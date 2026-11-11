package com.famy.family.domain;

import com.famy.family.FamilyRole;
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

/** Solicitud para unirse con una invitación que requiere aprobación de un OWNER o ADMIN. */
@Entity
@Table(name = "join_request")
public class JoinRequest extends BaseEntity<UUID> {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private UUID userId;

    private UUID invitationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FamilyRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private UUID decidedBy;
    private Instant decidedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected JoinRequest() {
    }

    public JoinRequest(UUID familyId, UUID userId, UUID invitationId, FamilyRole role) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.userId = userId;
        this.invitationId = invitationId;
        this.role = role;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void decide(Status status, UUID decidedBy) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public FamilyRole getRole() {
        return role;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
