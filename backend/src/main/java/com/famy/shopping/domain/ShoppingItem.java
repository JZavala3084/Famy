package com.famy.shopping.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_item")
public class ShoppingItem extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID listId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String name;

    private BigDecimal quantity;

    private String unit;

    private String category;

    private String note;

    private Instant checkedAt;

    private UUID checkedBy;

    @Column(nullable = false)
    private int position;

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ShoppingItem() {
    }

    public ShoppingItem(UUID familyId, UUID listId, ItemData data, int position, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.listId = listId;
        this.position = position;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        apply(data);
    }

    /** Datos editables de un ítem (reemplazo completo). */
    public record ItemData(String name, BigDecimal quantity, String unit, String category, String note) {
    }

    public void edit(ItemData data) {
        apply(data);
    }

    private void apply(ItemData data) {
        this.name = data.name().strip();
        this.quantity = data.quantity() == null ? null : data.quantity().stripTrailingZeros();
        this.unit = ShoppingList.blankToNull(data.unit());
        this.category = ShoppingList.blankToNull(data.category());
        this.note = ShoppingList.blankToNull(data.note());
        touch();
    }

    public void moveToList(UUID listId, int position) {
        this.listId = listId;
        this.position = position;
        touch();
    }

    public void check(UUID userId) {
        if (checkedAt == null) {
            this.checkedAt = Instant.now();
            this.checkedBy = userId;
            touch();
        }
    }

    public void uncheck() {
        if (checkedAt != null) {
            this.checkedAt = null;
            this.checkedBy = null;
            touch();
        }
    }

    public void moveTo(int position) {
        this.position = position;
        touch();
    }

    public void delete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getListId() {
        return listId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public UUID getCheckedBy() {
        return checkedBy;
    }

    public boolean isChecked() {
        return checkedAt != null;
    }

    public int getPosition() {
        return position;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
