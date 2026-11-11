package com.famy.notification.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Suscripción Web Push de un navegador. */
@Entity
@Table(name = "push_subscription")
public class PushSubscription extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastSuccessAt;

    protected PushSubscription() {
    }

    public PushSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        this.id = Ids.newId();
        this.createdAt = Instant.now();
        update(userId, p256dh, auth, userAgent);
        this.endpoint = endpoint;
    }

    /** El mismo navegador puede volver a suscribirse (o cambiar de usuario): se reemplazan las claves. */
    public void update(UUID userId, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length()));
    }

    public void delivered(Instant when) {
        this.lastSuccessAt = when;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
