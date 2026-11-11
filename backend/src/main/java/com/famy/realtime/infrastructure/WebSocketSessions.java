package com.famy.realtime.infrastructure;

import com.famy.family.MemberLeft;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Sesiones WebSocket abiertas. Permite cerrar las de alguien que salió de la familia (código 4403) y las que tienen
 * el access token vencido (código 4401); el cliente reconecta con un token nuevo.
 */
@Component
public class WebSocketSessions {

    public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "token_expired");
    public static final CloseStatus MEMBERSHIP_REVOKED = new CloseStatus(4403, "membership_revoked");

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessions.class);

    private static final class Entry {
        final WebSocketSession session;
        volatile UUID userId;
        volatile Instant expiresAt;

        Entry(WebSocketSession session) {
            this.session = session;
        }
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    void opened(WebSocketSession session) {
        sessions.put(session.getId(), new Entry(session));
    }

    void closed(String sessionId) {
        sessions.remove(sessionId);
    }

    void authenticated(String sessionId, UUID userId, Instant expiresAt) {
        Entry e = sessions.get(sessionId);
        if (e != null) {
            e.userId = userId;
            e.expiresAt = expiresAt;
        }
    }

    public int count() {
        return sessions.size();
    }

    @TransactionalEventListener
    void onMemberLeft(MemberLeft event) {
        sessions.values().stream()
                .filter(e -> event.userId().equals(e.userId))
                .forEach(e -> close(e, MEMBERSHIP_REVOKED));
    }

    @Scheduled(fixedDelay = 30_000)
    void closeExpired() {
        Instant now = Instant.now();
        sessions.values().stream()
                .filter(e -> e.expiresAt != null && e.expiresAt.isBefore(now))
                .forEach(e -> close(e, TOKEN_EXPIRED));
    }

    private void close(Entry e, CloseStatus status) {
        sessions.remove(e.session.getId());
        try {
            e.session.close(status);
        } catch (IOException ex) {
            log.debug("Error al cerrar la sesión WebSocket {}", e.session.getId(), ex);
        }
    }
}
