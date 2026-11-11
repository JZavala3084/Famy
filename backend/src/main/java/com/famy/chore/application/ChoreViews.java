package com.famy.chore.application;

import com.famy.chore.domain.Chore;
import com.famy.chore.domain.ChoreCompletion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Representaciones que devuelve la API y que viajan como {@code payload} en los eventos en tiempo real. */
public final class ChoreViews {

    private ChoreViews() {
    }

    public record ChoreView(UUID id, String title, String description, Instant dueAt, String rrule, int points,
            UUID assigneeId, boolean rotation, List<UUID> rotationOrder, boolean done, Instant completedAt,
            UUID createdBy, Instant createdAt, Instant updatedAt, long version) {

        static ChoreView of(Chore c) {
            return new ChoreView(c.getId(), c.getTitle(), c.getDescription(), c.getDueAt(), c.getRrule(),
                    c.getPoints(), c.getCurrentAssigneeId(), c.isRotation(), c.getRotationOrder(), c.isDone(),
                    c.getCompletedAt(), c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
        }
    }

    public record CompletionView(UUID id, UUID choreId, UUID completedBy, UUID recordedBy, Instant completedAt,
            LocalDate occurrenceDate, int points) {

        static CompletionView of(ChoreCompletion c) {
            return new CompletionView(c.getId(), c.getChoreId(), c.getCompletedBy(), c.getRecordedBy(),
                    c.getCompletedAt(), c.getOccurrenceDate(), c.getPoints());
        }
    }

    public record CompleteResult(ChoreView chore, CompletionView completion) {
    }

    public record MemberScore(UUID userId, String displayName, int points, int completions) {
    }

    public record Scoreboard(LocalDate weekStart, LocalDate weekEnd, List<MemberScore> members) {
    }
}
