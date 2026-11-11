package com.famy.family.infrastructure;

import com.famy.identity.AccessTokenClaimsProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Aporta la familia activa y el rol a los claims {@code fid} / {@code role} del access token. */
@Component
class FamilyTokenClaims implements AccessTokenClaimsProvider {

    private final FamilyMemberRepository members;

    FamilyTokenClaims(FamilyMemberRepository members) {
        this.members = members;
    }

    @Override
    public Optional<FamilyClaims> activeFamily(UUID userId) {
        return members.findActiveByUser(userId).stream().findFirst()
                .map(m -> new FamilyClaims(m.getFamilyId(), m.getRole().name()));
    }
}
