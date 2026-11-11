package com.famy.chore.infrastructure;

import com.famy.chore.domain.ChoreCompletion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChoreCompletionRepository extends JpaRepository<ChoreCompletion, UUID> {

    @Query("select c from ChoreCompletion c where c.choreId = :choreId order by c.completedAt desc, c.id desc")
    List<ChoreCompletion> findByChore(UUID choreId, Limit limit);

    default Optional<ChoreCompletion> findLatest(UUID choreId) {
        return findByChore(choreId, Limit.of(1)).stream().findFirst();
    }

    @Query("""
            select c from ChoreCompletion c where c.familyId = :familyId
            and c.completedAt >= :from and c.completedAt < :to order by c.completedAt desc""")
    List<ChoreCompletion> findByFamilyBetween(UUID familyId, Instant from, Instant to);
}
