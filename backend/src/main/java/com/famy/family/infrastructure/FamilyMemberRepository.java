package com.famy.family.infrastructure;

import com.famy.family.domain.FamilyMember;
import com.famy.family.domain.FamilyMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, FamilyMemberId> {

    @Query("select m from FamilyMember m where m.id.familyId = :familyId and m.id.userId = :userId and m.leftAt is null")
    Optional<FamilyMember> findActive(UUID familyId, UUID userId);

    @Query("select m from FamilyMember m where m.id.familyId = :familyId and m.leftAt is null order by m.joinedAt")
    List<FamilyMember> findActiveByFamily(UUID familyId);

    @Query("select m from FamilyMember m where m.id.userId = :userId and m.leftAt is null order by m.joinedAt")
    List<FamilyMember> findActiveByUser(UUID userId);

    @Query("select count(m) from FamilyMember m where m.id.familyId = :familyId and m.leftAt is null")
    long countActive(UUID familyId);
}
