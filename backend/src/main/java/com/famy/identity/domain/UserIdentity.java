package com.famy.identity.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Identidad externa vinculada a un usuario (por ahora solo Google). */
@Entity
@Table(name = "user_identity")
public class UserIdentity extends BaseEntity<UUID> {

    public static final String GOOGLE = "GOOGLE";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerSubject;

    private String email;

    @Column(nullable = false)
    private Instant linkedAt;

    protected UserIdentity() {
    }

    public UserIdentity(UUID userId, String provider, String providerSubject, String email) {
        this.id = Ids.newId();
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.linkedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }
}
