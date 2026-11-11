package com.famy.chore.infrastructure;

import com.famy.chore.domain.Chore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChoreRepository extends JpaRepository<Chore, UUID> {

    @Query("select c from Chore c where c.id = :id and c.familyId = :familyId and c.deletedAt is null")
    Optional<Chore> findActive(UUID familyId, UUID id);

    @Query("""
            select c from Chore c where c.familyId = :familyId and c.deletedAt is null
            and (:includeDone = true or c.completedAt is null)
            and (:assigneeId is null or c.currentAssigneeId = :assigneeId)
            order by case when c.dueAt is null then 1 else 0 end, c.dueAt, c.createdAt""")
    List<Chore> findByFamily(UUID familyId, boolean includeDone, UUID assigneeId);

    @Query("select count(c) from Chore c where c.familyId = :familyId and c.deletedAt is null")
    long countActive(UUID familyId);
}
