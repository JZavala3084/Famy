package com.famy.shopping.infrastructure;

import com.famy.shopping.domain.ShoppingList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    @Query("select l from ShoppingList l where l.id = :id and l.familyId = :familyId and l.deletedAt is null")
    Optional<ShoppingList> findActive(UUID familyId, UUID id);

    @Query("""
            select l from ShoppingList l where l.familyId = :familyId and l.deletedAt is null
            and (:includeArchived = true or l.archivedAt is null) order by l.position, l.createdAt""")
    List<ShoppingList> findByFamily(UUID familyId, boolean includeArchived);

    @Query("select coalesce(max(l.position), -1) from ShoppingList l where l.familyId = :familyId and l.deletedAt is null")
    int maxPosition(UUID familyId);
}
