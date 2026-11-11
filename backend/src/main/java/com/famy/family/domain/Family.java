package com.famy.family.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family")
public class Family extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String avatarUrl;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private UUID ownerId;

    /** Si los miembros con rol MEMBER pueden invitar (por defecto no). */
    @Column(nullable = false)
    private boolean membersCanInvite;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Family() {
    }

    public Family(String name, String avatarUrl, String currency, String timezone, UUID ownerId) {
        this.id = Ids.newId();
        this.name = name.strip();
        this.avatarUrl = avatarUrl;
        this.currency = currency;
        this.timezone = timezone;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String name, String avatarUrl, String currency, String timezone, Boolean membersCanInvite) {
        if (name != null) {
            this.name = name.strip();
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl.isBlank() ? null : avatarUrl;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (membersCanInvite != null) {
            this.membersCanInvite = membersCanInvite;
        }
        this.updatedAt = Instant.now();
    }

    public void transferOwnership(UUID newOwnerId) {
        this.ownerId = newOwnerId;
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isMembersCanInvite() {
        return membersCanInvite;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
