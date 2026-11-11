package com.famy.family;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Consultas de membresía para otros módulos (la autorización siempre se valida contra la base). */
public interface FamilyMembership {

    /** Rol activo del usuario en la familia, o vacío si no es miembro. */
    Optional<FamilyRole> roleOf(UUID familyId, UUID userId);

    /** Rol activo del usuario; si no es miembro lanza 404 (no se revela que la familia existe). */
    FamilyRole requireMember(UUID familyId, UUID userId);

    /** Ids de los miembros activos. */
    Set<UUID> activeMemberIds(UUID familyId);

    /** Zona horaria de la familia (para fechas límite, semanas y recurrencias). */
    ZoneId timezone(UUID familyId);
}
