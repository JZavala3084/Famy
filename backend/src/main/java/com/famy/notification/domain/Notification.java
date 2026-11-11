package com.famy.notification.domain;

import com.famy.notification.NotificationType;
import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Entrada del centro de notificaciones de un usuario. */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID familyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    private String body;

    private String link;

    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID userId, UUID familyId, NotificationType type, String title, String body, String link) {
        this.id = Ids.newId();
        this.userId = userId;
        this.familyId = familyId;
        this.type = type;
        this.title = truncate(title, 160);
        this.body = body == null ? null : truncate(body, 500);
        this.link = link == null ? null : truncate(link, 200);
        this.createdAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
