package com.famy.chore.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "chore_completion")
public class ChoreCompletion extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID choreId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private UUID completedBy;

    @Column(nullable = false)
    private UUID recordedBy;

    @Column(nullable = false)
    private Instant completedAt;

    @Column(nullable = false)
    private LocalDate occurrenceDate;

    @Column(nullable = false)
    private int points;

    private Instant previousDueAt;

    private UUID previousAssigneeId;

    protected ChoreCompletion() {
    }

    public ChoreCompletion(Chore chore, UUID completedBy, UUID recordedBy, Instant completedAt,
            LocalDate occurrenceDate) {
        this.id = Ids.newId();
        this.choreId = chore.getId();
        this.familyId = chore.getFamilyId();
        this.completedBy = completedBy;
        this.recordedBy = recordedBy;
        this.completedAt = completedAt;
        this.occurrenceDate = occurrenceDate;
        this.points = chore.getPoints();
        this.previousDueAt = chore.getDueAt();
        this.previousAssigneeId = chore.getCurrentAssigneeId();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getChoreId() {
        return choreId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public int getPoints() {
        return points;
    }

    public Instant getPreviousDueAt() {
        return previousDueAt;
    }

    public UUID getPreviousAssigneeId() {
        return previousAssigneeId;
    }
}
