package com.famy.celebration.infrastructure;

import com.famy.celebration.domain.Celebration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CelebrationRepository extends JpaRepository<Celebration, UUID> {

    @Query("select c from Celebration c where c.id = :id and c.familyId = :familyId and c.deletedAt is null")
    Optional<Celebration> findActive(UUID familyId, UUID id);

    @Query("select c from Celebration c where c.familyId = :familyId and c.deletedAt is null")
    List<Celebration> findByFamily(UUID familyId);

    @Query("""
            select c from Celebration c where c.familyId = :familyId and c.userId = :userId
            and c.deletedAt is null""")
    Optional<Celebration> findByMember(UUID familyId, UUID userId);

    @Query("select c from Celebration c where c.deletedAt is null")
    List<Celebration> findAllActive();

    @Query("select count(c) from Celebration c where c.familyId = :familyId and c.deletedAt is null")
    long countActive(UUID familyId);
}
