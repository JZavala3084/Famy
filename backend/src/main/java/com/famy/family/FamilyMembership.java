package com.famy.family;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Consultas de membresía para otros módulos (la autorización siempre se valida contra la base). */
public interface FamilyMembership {

    /** Rol activo del usuario en la familia, o vacío si no es miembro. */
    Optional<FamilyRole> roleOf(UUID familyId, UUID userId);

    /** Rol activo del usuario; si no es miembro lanza 404 (no se revela que la familia existe). */
    FamilyRole requireMember(UUID familyId, UUID userId);

    /** Ids de los miembros activos. */
    Set<UUID> activeMemberIds(UUID familyId);

    /** Familias con al menos un miembro activo (jobs que recorren todas las familias). */
    List<UUID> activeFamilyIds();

    /** Miembros activos con su rol. */
    Map<UUID, FamilyRole> activeMembers(UUID familyId);

    /** Ids de los miembros activos que no son CHILD. */
    default Set<UUID> activeAdultIds(UUID familyId) {
        return activeMembers(familyId).entrySet().stream().filter(e -> e.getValue() != FamilyRole.CHILD)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /** Moneda del presupuesto familiar (ISO 4217). */
    String currency(UUID familyId);

    /** Zona horaria de la familia (para fechas límite, semanas y recurrencias). */
    ZoneId timezone(UUID familyId);
}
