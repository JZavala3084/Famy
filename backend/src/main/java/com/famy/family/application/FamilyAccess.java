package com.famy.family.application;

import com.famy.family.domain.FamilyMember;
import com.famy.family.FamilyRole;
import com.famy.family.infrastructure.FamilyMemberRepository;
import com.famy.shared.error.ApiException;
import com.famy.shared.security.CurrentUser;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorización por familia: cada operación sobre {@code /api/v1/families/{id}/**} comprueba que el usuario sea
 * miembro activo y tenga el rol necesario. Si no es miembro se responde 404 (no se revela que la familia existe).
 * Disponible como bean {@code familyAccess} para {@code @PreAuthorize} en los módulos de dominio.
 */
@Component("familyAccess")
public class FamilyAccess {

    private final FamilyMemberRepository members;

    public FamilyAccess(FamilyMemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public FamilyMember requireMember(UUID familyId, UUID userId) {
        return members.findActive(familyId, userId)
                .orElseThrow(() -> ApiException.notFound("family_not_found", "Familia no encontrada."));
    }

    @Transactional(readOnly = true)
    public FamilyMember requireRole(UUID familyId, UUID userId, FamilyRole first, FamilyRole... rest) {
        FamilyMember member = requireMember(familyId, userId);
        if (!EnumSet.of(first, rest).contains(member.getRole())) {
            throw ApiException.forbidden("forbidden", "No tienes permiso para esta acción.");
        }
        return member;
    }

    /** Para {@code @PreAuthorize("@familyAccess.isMember(#familyId)")}. */
    @Transactional(readOnly = true)
    public boolean isMember(UUID familyId) {
        UUID userId = CurrentUser.idOrNull();
        return userId != null && members.findActive(familyId, userId).isPresent();
    }
}
