package com.famy.shopping.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_list")
public class ShoppingList extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(nullable = false)
    private int position;

    private Instant archivedAt;

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ShoppingList() {
    }

    public ShoppingList(UUID familyId, String name, String icon, int position, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.name = name.strip();
        this.icon = blankToNull(icon);
        this.position = position;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void rename(String name) {
        this.name = name.strip();
        touch();
    }

    public void changeIcon(String icon) {
        this.icon = blankToNull(icon);
        touch();
    }

    public void archive(boolean archived) {
        this.archivedAt = archived ? (archivedAt == null ? Instant.now() : archivedAt) : null;
        touch();
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

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public int getPosition() {
        return position;
    }

    public Instant getArchivedAt() {
        return archivedAt;
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
