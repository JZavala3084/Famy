package com.famy.family.application;

import com.famy.family.FamilyRole;
import com.famy.family.domain.FamilyMember;
import com.famy.shared.events.ChangePublisher;
import java.util.UUID;

/** Eventos en tiempo real de membresía ({@code family.member.*}); el cliente recarga el detalle de la familia. */
final class MemberChanges {

    static final String ENTITY = "family.member";

    record MemberPayload(UUID userId, FamilyRole role, boolean active) {
    }

    private MemberChanges() {
    }

    static void joined(ChangePublisher changes, FamilyMember m) {
        changes.created(m.getFamilyId(), ENTITY, m.getUserId(), m.getVersion(), payload(m));
    }

    static void updated(ChangePublisher changes, FamilyMember m) {
        changes.updated(m.getFamilyId(), ENTITY, m.getUserId(), m.getVersion(), payload(m));
    }

    private static MemberPayload payload(FamilyMember m) {
        return new MemberPayload(m.getUserId(), m.getRole(), m.isActive());
    }
}
