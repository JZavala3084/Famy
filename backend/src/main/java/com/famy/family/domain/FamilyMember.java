package com.famy.family.domain;

import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Membresía. Al salir o ser expulsado se marca {@code leftAt}; los datos que creó se quedan en la familia. */
@Entity
@Table(name = "family_member")
public class FamilyMember extends BaseEntity<FamilyMemberId> {

    @EmbeddedId
    private FamilyMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FamilyRole role;

    private String nickname;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    @Version
    private long version;

    protected FamilyMember() {
    }

    public FamilyMember(UUID familyId, UUID userId, FamilyRole role) {
        this.id = new FamilyMemberId(familyId, userId);
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void rejoin(FamilyRole role) {
        this.role = role;
        this.joinedAt = Instant.now();
        this.leftAt = null;
    }

    public void leave() {
        this.leftAt = Instant.now();
    }

    public void changeRole(FamilyRole role) {
        this.role = role;
    }

    @Override
    public FamilyMemberId getId() {
        return id;
    }

    public UUID getFamilyId() {
        return id.familyId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public FamilyRole getRole() {
        return role;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
