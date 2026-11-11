package com.famy.notification.application;

import com.famy.notification.NotificationType;
import com.famy.notification.application.NotificationViews.LiveNotification;
import com.famy.notification.application.NotificationViews.NotificationPage;
import com.famy.notification.application.NotificationViews.NotificationView;
import com.famy.notification.domain.Notification;
import com.famy.notification.infrastructure.NotificationRepository;
import com.famy.shared.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Centro de notificaciones (RF-11.3). Al crear una, tras el commit se envía en vivo a la cola personal del usuario
 * ({@code /user/queue/notifications}) y por push web a sus navegadores (según sus preferencias).
 */
@Service
public class NotificationService {

    public static final String QUEUE = "/queue/notifications";
    static final Duration RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final SimpMessagingTemplate broker;
    private final PushService push;

    public NotificationService(NotificationRepository notifications, SimpMessagingTemplate broker, PushService push) {
        this.notifications = notifications;
        this.broker = broker;
        this.push = push;
    }

    /** Crea una notificación para cada usuario. Debe llamarse dentro de una transacción. */
    @Transactional
    public List<NotificationView> notify(Collection<UUID> userIds, UUID familyId, NotificationType type,
            String title, String body, String link) {
        List<NotificationView> created = new ArrayList<>();
        for (UUID userId : userIds) {
            created.add(NotificationView.of(
                    notifications.save(new Notification(userId, familyId, type, title, body, link))));
        }
        notifications.flush();
        List<UUID> users = List.copyOf(userIds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (int i = 0; i < users.size(); i++) {
                    deliver(users.get(i), created.get(i));
                }
            }
        });
        return created;
    }

    private void deliver(UUID userId, NotificationView n) {
        try {
            broker.convertAndSendToUser(userId.toString(), QUEUE,
                    new LiveNotification(n, notifications.countUnread(userId)));
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar la notificación en vivo a {}: {}", userId, e.getMessage());
        }
        push.dispatch(userId, n, n.type() == NotificationType.SYSTEM);
    }

    @Transactional(readOnly = true)
    public NotificationPage list(UUID userId, int limit) {
        List<NotificationView> items = notifications.findByUser(userId, Limit.of(Math.clamp(limit, 1, 100))).stream()
                .map(NotificationView::of).toList();
        return new NotificationPage(items, notifications.countUnread(userId));
    }

    @Transactional
    public NotificationView markRead(UUID userId, UUID id) {
        Notification n = notifications.findOwn(userId, id)
                .orElseThrow(() -> ApiException.notFound("notification_not_found", "Notificación no encontrada."));
        n.markRead(Instant.now());
        return NotificationView.of(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, Instant.now());
    }

    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void purge() {
        int n = notifications.deleteOlderThan(Instant.now().minus(RETENTION));
        if (n > 0) {
            log.info("{} notificaciones viejas borradas", n);
        }
    }
}
