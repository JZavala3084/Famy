package com.famy.identity.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token opaco (solo se guarda su hash). Todos los tokens rotados desde el mismo login comparten
 * {@code familyChainId}; si se reutiliza uno ya rotado, se revoca toda la cadena.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String tokenHash;

    private String deviceName;

    @Column(nullable = false)
    private UUID familyChainId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
    private UUID replacedBy;
    private String createdIp;

    @Column(nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, String deviceName, UUID familyChainId, Instant expiresAt,
            String createdIp) {
        this.id = Ids.newId();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deviceName = deviceName;
        this.familyChainId = familyChainId;
        this.expiresAt = expiresAt;
        this.createdIp = createdIp;
        this.createdAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean wasRotated() {
        return replacedBy != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public void rotateTo(UUID newTokenId) {
        revoke();
        this.replacedBy = newTokenId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyChainId() {
        return familyChainId;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
