package com.famy.family.infrastructure;

import com.famy.family.domain.FamilyInvitation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FamilyInvitation> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FamilyInvitation> findByTokenHash(String tokenHash);

    boolean existsByCode(String code);

    Optional<FamilyInvitation> findByIdAndFamilyId(UUID id, UUID familyId);

    @Query("""
            select i from FamilyInvitation i where i.familyId = :familyId and i.revokedAt is null
            and i.expiresAt > :now order by i.createdAt desc""")
    List<FamilyInvitation> findActiveByFamily(UUID familyId, Instant now);
}
