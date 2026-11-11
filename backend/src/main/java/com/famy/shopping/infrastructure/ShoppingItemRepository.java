package com.famy.shopping.infrastructure;

import com.famy.shopping.domain.ShoppingItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {

    /** Pendientes primero (por posición) y los comprados al final (el más reciente arriba). */
    @Query("""
            select i from ShoppingItem i where i.listId = :listId and i.deletedAt is null
            order by case when i.checkedAt is null then 0 else 1 end, i.position, i.checkedAt desc, i.createdAt""")
    List<ShoppingItem> findByList(UUID listId);

    @Query("""
            select i from ShoppingItem i where i.id = :id and i.listId = :listId and i.familyId = :familyId
            and i.deletedAt is null""")
    Optional<ShoppingItem> findActive(UUID familyId, UUID listId, UUID id);

    @Query("""
            select i from ShoppingItem i where i.listId = :listId and i.deletedAt is null
            and i.checkedAt is not null""")
    List<ShoppingItem> findChecked(UUID listId);

    @Query("""
            select coalesce(max(i.position), -1) from ShoppingItem i where i.listId = :listId
            and i.deletedAt is null""")
    int maxPosition(UUID listId);

    interface ListCount {
        UUID getListId();

        long getTotal();

        long getPending();
    }

    @Query("""
            select i.listId as listId, count(i) as total,
            sum(case when i.checkedAt is null then 1 else 0 end) as pending
            from ShoppingItem i where i.familyId = :familyId and i.deletedAt is null group by i.listId""")
    List<ListCount> countByList(UUID familyId);
}
