package com.famy.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI: otro módulo (family) aporta la familia activa y el rol del usuario para los claims
 * {@code fid} y {@code role} del access token. Así identity no depende de family.
 */
public interface AccessTokenClaimsProvider {

    record FamilyClaims(UUID familyId, String role) {
    }

    Optional<FamilyClaims> activeFamily(UUID userId);
}
