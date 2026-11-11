package com.famy.notification.application;

import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.notification.NotificationType;
import com.famy.notification.application.NotificationViews.NotificationView;
import com.famy.notification.application.NotificationViews.Preferences;
import com.famy.notification.application.NotificationViews.PushConfig;
import com.famy.notification.domain.NotificationPreference;
import com.famy.notification.domain.PushSubscription;
import com.famy.notification.infrastructure.NotificationPreferenceRepository;
import com.famy.notification.infrastructure.PushGateway;
import com.famy.notification.infrastructure.PushGateway.Result;
import com.famy.notification.infrastructure.PushSubscriptionRepository;
import com.famy.notification.infrastructure.VapidKeys;
import com.famy.notification.infrastructure.WebPushCrypto;
import com.famy.shared.config.FamyProperties;
import com.famy.shared.error.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Suscripciones Web Push, preferencias (RF-11.2) y envío a los navegadores de un usuario. */
@Service
public class PushService {

    static final int MAX_SUBSCRIPTIONS = 10;
    static final int TTL_SECONDS = 24 * 60 * 60;

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final NotificationPreferenceRepository preferences;
    private final PushGateway gateway;
    private final VapidKeys vapid;
    private final UserDirectory users;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PushService(PushSubscriptionRepository subscriptions, NotificationPreferenceRepository preferences,
            PushGateway gateway, VapidKeys vapid, UserDirectory users, ObjectMapper mapper, TransactionTemplate tx,
            FamyProperties props) {
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.gateway = gateway;
        this.vapid = vapid;
        this.users = users;
        this.mapper = mapper;
        this.tx = tx;
        this.enabled = props.push().enabled();
    }

    @Transactional(readOnly = true)
    public PushConfig config(UUID userId) {
        return new PushConfig(enabled, vapid.publicKey(), subscriptions.countByUserId(userId));
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_subscription", "La suscripción no es válida.");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || endpoint.length() > 1000) {
            throw ApiException.badRequest("invalid_subscription", "La suscripción no es válida.");
        }
        try {
            if (WebPushCrypto.unb64(p256dh).length != 65 || WebPushCrypto.unb64(auth).length != 16) {
                throw ApiException.badRequest("invalid_subscription", "Las claves de la suscripción no son válidas.");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_subscription", "Las claves de la suscripción no son válidas.");
        }
        PushSubscription existing = subscriptions.findByEndpoint(endpoint).orElse(null);
        if (existing != null) {
            existing.update(userId, p256dh, auth, userAgent);
            return;
        }
        List<PushSubscription> own = subscriptions.findByUserId(userId);
        if (own.size() >= MAX_SUBSCRIPTIONS) {
            // Se descarta la más vieja: probablemente un navegador que ya no se usa.
            own.stream().min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())).ifPresent(subscriptions::delete);
        }
        subscriptions.save(new PushSubscription(userId, endpoint, p256dh, auth, userAgent));
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptions.findByEndpoint(endpoint).filter(s -> s.getUserId().equals(userId))
                .ifPresent(subscriptions::delete);
    }

    @Transactional(readOnly = true)
    public Preferences preferences(UUID userId) {
        NotificationPreference p = preferences.findById(userId).orElse(null);
        return p == null ? new Preferences(Set.of(), null, null)
                : new Preferences(p.getMutedTypes(), p.getQuietFrom(), p.getQuietTo());
    }

    @Transactional
    public Preferences updatePreferences(UUID userId, Preferences update) {
        NotificationPreference p = preferences.findById(userId).orElseGet(() -> new NotificationPreference(userId));
        Set<NotificationType> muted = update.mutedTypes() == null || update.mutedTypes().isEmpty()
                ? EnumSet.noneOf(NotificationType.class) : EnumSet.copyOf(update.mutedTypes());
        muted.remove(NotificationType.SYSTEM);
        if ((update.quietFrom() == null) != (update.quietTo() == null)) {
            throw ApiException.badRequest("invalid_quiet_hours", "Indica el inicio y el fin del horario de silencio.");
        }
        p.update(muted, update.quietFrom(), update.quietTo());
        preferences.save(p);
        return new Preferences(p.getMutedTypes(), p.getQuietFrom(), p.getQuietTo());
    }

    /**
     * Envía la notificación por push a los navegadores del usuario, en segundo plano. Respeta los tipos silenciados
     * y el horario de silencio, salvo {@code force} (prueba).
     */
    public void dispatch(UUID userId, NotificationView n, boolean force) {
        if (!enabled) {
            return;
        }
        executor.submit(() -> {
            try {
                send(userId, n, force);
            } catch (RuntimeException e) {
                log.warn("Fallo al enviar push a {}: {}", userId, e.getMessage());
            }
        });
    }

    private void send(UUID userId, NotificationView n, boolean force) {
        List<PushSubscription> targets = tx.execute(s -> {
            if (!force && muted(userId, n.type())) {
                return List.<PushSubscription>of();
            }
            return subscriptions.findByUserId(userId);
        });
        if (targets == null || targets.isEmpty()) {
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", n.id().toString());
        message.put("type", n.type().name());
        message.put("title", n.title());
        message.put("body", n.body() == null ? "" : n.body());
        message.put("link", n.link() == null ? "/" : n.link());
        byte[] payload = mapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        for (PushSubscription sub : targets) {
            Result r = gateway.send(new PushGateway.Target(sub.getEndpoint(), sub.getP256dh(), sub.getAuth()),
                    payload, TTL_SECONDS, null);
            tx.executeWithoutResult(s -> subscriptions.findById(sub.getId()).ifPresent(current -> {
                if (r == Result.GONE) {
                    subscriptions.delete(current);
                } else if (r == Result.DELIVERED) {
                    current.delivered(Instant.now());
                }
            }));
        }
    }

    private boolean muted(UUID userId, NotificationType type) {
        NotificationPreference p = preferences.findById(userId).orElse(null);
        if (p == null) {
            return false;
        }
        if (p.getMutedTypes().contains(type)) {
            return true;
        }
        ZoneId zone = users.find(userId).map(UserSummary::timezone).map(PushService::zone).orElse(ZoneOffset.UTC);
        return p.isQuietAt(LocalTime.now(zone));
    }

    private static ZoneId zone(String tz) {
        try {
            return tz == null ? ZoneOffset.UTC : ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
