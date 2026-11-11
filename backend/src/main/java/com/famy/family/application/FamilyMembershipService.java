package com.famy.family.application;

import com.famy.family.FamilyMembership;
import com.famy.family.FamilyRole;
import com.famy.family.domain.FamilyMember;
import com.famy.family.infrastructure.FamilyMemberRepository;
import com.famy.family.infrastructure.FamilyRepository;
import com.famy.shared.error.ApiException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FamilyMembershipService implements FamilyMembership {

    private final FamilyMemberRepository members;
    private final FamilyRepository families;

    FamilyMembershipService(FamilyMemberRepository members, FamilyRepository families) {
        this.members = members;
        this.families = families;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FamilyRole> roleOf(UUID familyId, UUID userId) {
        return members.findActive(familyId, userId).map(FamilyMember::getRole);
    }

    @Override
    @Transactional(readOnly = true)
    public FamilyRole requireMember(UUID familyId, UUID userId) {
        return roleOf(familyId, userId)
                .orElseThrow(() -> ApiException.notFound("family_not_found", "Familia no encontrada."));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> activeMemberIds(UUID familyId) {
        return members.findActiveByFamily(familyId).stream().map(FamilyMember::getUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeFamilyIds() {
        return members.findActiveFamilyIds();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, FamilyRole> activeMembers(UUID familyId) {
        return members.findActiveByFamily(familyId).stream()
                .collect(Collectors.toUnmodifiableMap(FamilyMember::getUserId, FamilyMember::getRole));
    }

    @Override
    @Transactional(readOnly = true)
    public String currency(UUID familyId) {
        return families.findById(familyId)
                .orElseThrow(() -> ApiException.notFound("family_not_found", "Familia no encontrada."))
                .getCurrency();
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId timezone(UUID familyId) {
        String tz = families.findById(familyId)
                .orElseThrow(() -> ApiException.notFound("family_not_found", "Familia no encontrada."))
                .getTimezone();
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
