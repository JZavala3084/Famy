package com.famy.chore.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chore")
public class Chore extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String title;

    private String description;

    private Instant dueAt;

    private String rrule;

    private Instant recurrenceStart;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private boolean rotation;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "uuid[]")
    private List<UUID> rotationOrder = new ArrayList<>();

    private UUID currentAssigneeId;

    private Instant completedAt;

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Chore() {
    }

    /** Datos editables (reemplazo completo). {@code rrule} ya validada y {@code rotationOrder} ya verificada. */
    public record ChoreData(String title, String description, Instant dueAt, String rrule, int points,
            UUID assigneeId, boolean rotation, List<UUID> rotationOrder) {
    }

    public Chore(UUID familyId, ChoreData data, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        apply(data);
    }

    public void edit(ChoreData data) {
        apply(data);
    }

    private void apply(ChoreData d) {
        boolean scheduleChanged = !Objects.equals(rrule, d.rrule()) || !Objects.equals(dueAt, d.dueAt());
        this.title = d.title().strip();
        this.description = d.description() == null || d.description().isBlank() ? null : d.description().strip();
        this.dueAt = d.dueAt();
        this.rrule = d.rrule();
        if (rrule == null) {
            this.recurrenceStart = null;
        } else if (scheduleChanged || recurrenceStart == null) {
            this.recurrenceStart = dueAt;
        }
        this.points = d.points();
        this.rotation = d.rotation();
        this.rotationOrder = new ArrayList<>(d.rotation() ? d.rotationOrder() : List.of());
        this.currentAssigneeId = d.assigneeId();
        if (scheduleChanged && completedAt != null) {
            // Darle una fecha nueva a una tarea hecha la vuelve a abrir.
            this.completedAt = null;
        }
        touch();
    }

    /** Avanza a la siguiente ocurrencia ({@code nextDue}) o, si no hay más, la marca como hecha. */
    public void advance(Instant nextDue, UUID nextAssignee, Instant now) {
        if (nextDue != null) {
            this.dueAt = nextDue;
        } else {
            this.completedAt = now;
        }
        this.currentAssigneeId = nextAssignee;
        touch();
    }

    /** Deshace la última marca: restaura la fecha y el responsable anteriores. */
    public void restore(Instant previousDue, UUID previousAssignee) {
        this.dueAt = previousDue;
        this.currentAssigneeId = previousAssignee;
        this.completedAt = null;
        touch();
    }

    public void delete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isDone() {
        return completedAt != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getRrule() {
        return rrule;
    }

    public Instant getRecurrenceStart() {
        return recurrenceStart;
    }

    public int getPoints() {
        return points;
    }

    public boolean isRotation() {
        return rotation;
    }

    public List<UUID> getRotationOrder() {
        return List.copyOf(rotationOrder);
    }

    public UUID getCurrentAssigneeId() {
        return currentAssigneeId;
    }

    public Instant getCompletedAt() {
        return completedAt;
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
