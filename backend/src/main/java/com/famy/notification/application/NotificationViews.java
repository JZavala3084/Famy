package com.famy.notification.application;

import com.famy.notification.NotificationType;
import com.famy.notification.domain.Notification;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Representaciones de la API de notificaciones. */
public final class NotificationViews {

    private NotificationViews() {
    }

    public record NotificationView(UUID id, NotificationType type, String title, String body, String link,
            UUID familyId, boolean read, Instant createdAt) {

        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getFamilyId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record NotificationPage(List<NotificationView> items, long unread) {
    }

    /** Lo que llega por {@code /user/queue/notifications}: la notificación nueva y el total sin leer. */
    public record LiveNotification(NotificationView notification, long unread) {
    }

    /**
     * @param publicKey     clave VAPID para {@code pushManager.subscribe({applicationServerKey})}
     * @param subscriptions navegadores del usuario suscritos
     */
    public record PushConfig(boolean enabled, String publicKey, long subscriptions) {
    }

    public record Preferences(Set<NotificationType> mutedTypes, LocalTime quietFrom, LocalTime quietTo) {
    }
}
