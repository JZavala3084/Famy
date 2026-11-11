package com.famy.family.domain;

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

/**
 * Invitación con código corto (8 caracteres) y enlace con token (solo se guarda su hash). Si tiene
 * {@code email}, solo esa persona puede usarla.
 */
@Entity
@Table(name = "family_invitation")
public class FamilyInvitation extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FamilyRole role;

    private String email;

    private Integer maxUses;

    @Column(nullable = false)
    private int uses;

    @Column(nullable = false)
    private boolean requiresApproval;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected FamilyInvitation() {
    }

    public FamilyInvitation(UUID familyId, String code, String tokenHash, FamilyRole role, String email,
            Integer maxUses, boolean requiresApproval, Instant expiresAt, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.code = code;
        this.tokenHash = tokenHash;
        this.role = role;
        this.email = email;
        this.maxUses = maxUses;
        this.requiresApproval = requiresApproval;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && (maxUses == null || uses < maxUses);
    }

    public void registerUse() {
        uses++;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getCode() {
        return code;
    }

    public FamilyRole getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUses() {
        return uses;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
