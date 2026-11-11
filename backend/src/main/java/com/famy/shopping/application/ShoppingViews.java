package com.famy.shopping.application;

import com.famy.shopping.domain.ShoppingItem;
import com.famy.shopping.domain.ShoppingList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representaciones que devuelve la API y que viajan como {@code payload} en los eventos en tiempo real. */
public final class ShoppingViews {

    private ShoppingViews() {
    }

    public record ListView(UUID id, String name, String icon, int position, boolean archived, Instant archivedAt,
            long totalItems, long pendingItems, UUID createdBy, Instant createdAt, Instant updatedAt, long version) {

        static ListView of(ShoppingList l, long total, long pending) {
            return new ListView(l.getId(), l.getName(), l.getIcon(), l.getPosition(), l.getArchivedAt() != null,
                    l.getArchivedAt(), total, pending, l.getCreatedBy(), l.getCreatedAt(), l.getUpdatedAt(),
                    l.getVersion());
        }
    }

    public record ItemView(UUID id, UUID listId, String name, BigDecimal quantity, String unit, String category,
            String note, boolean checked, Instant checkedAt, UUID checkedBy, int position, UUID createdBy,
            Instant createdAt, Instant updatedAt, long version) {

        static ItemView of(ShoppingItem i) {
            return new ItemView(i.getId(), i.getListId(), i.getName(), i.getQuantity(), i.getUnit(), i.getCategory(),
                    i.getNote(), i.isChecked(), i.getCheckedAt(), i.getCheckedBy(), i.getPosition(),
                    i.getCreatedBy(), i.getCreatedAt(), i.getUpdatedAt(), i.getVersion());
        }
    }

    public record Suggestion(String name, String unit, String category, long uses) {
    }
}
