package com.famy.family.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record FamilyMemberId(@Column(name = "family_id") UUID familyId, @Column(name = "user_id") UUID userId)
        implements Serializable {
}
