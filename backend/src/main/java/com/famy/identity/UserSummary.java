package com.famy.identity;

import java.time.LocalDate;
import java.util.UUID;

/** Datos públicos de un usuario que otros módulos pueden usar. */
public record UserSummary(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String color,
        boolean emailVerified,
        String currency,
        String timezone,
        LocalDate birthday) {
}
