package com.famy.calendar.domain;

import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "calendar_event")
public class CalendarEvent extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private String title;

    private String description;

    private String location;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean allDay;

    private String rrule;

    private String color;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "uuid[]")
    private List<UUID> participantIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "integer[]")
    private List<Integer> reminderMinutes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "timestamptz[]")
    private List<Instant> excludedStarts = new ArrayList<>();

    private Instant deletedAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CalendarEvent() {
    }

    /** Datos editables (reemplazo completo), ya validados. */
    public record EventData(String title, String description, String location, Instant startsAt, Instant endsAt,
            boolean allDay, String rrule, String color, List<UUID> participantIds, List<Integer> reminderMinutes) {
    }

    public CalendarEvent(UUID familyId, EventData data, UUID createdBy) {
        this.id = Ids.newId();
        this.familyId = familyId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        apply(data);
    }

    public void edit(EventData data) {
        apply(data);
    }

    private void apply(EventData d) {
        boolean scheduleChanged = !Objects.equals(rrule, d.rrule()) || !Objects.equals(startsAt, d.startsAt());
        this.title = d.title().strip();
        this.description = blankToNull(d.description());
        this.location = blankToNull(d.location());
        this.startsAt = d.startsAt();
        this.endsAt = d.endsAt();
        this.allDay = d.allDay();
        this.rrule = d.rrule();
        this.color = blankToNull(d.color());
        this.participantIds = new ArrayList<>(d.participantIds());
        this.reminderMinutes = new ArrayList<>(d.reminderMinutes());
        if (scheduleChanged) {
            // Las cancelaciones apuntan a ocurrencias de la regla anterior.
            this.excludedStarts = new ArrayList<>();
        }
        touch();
    }

    /** Cancela una ocurrencia de un evento que se repite. */
    public void exclude(Instant occurrenceStart) {
        if (!excludedStarts.contains(occurrenceStart)) {
            excludedStarts.add(occurrenceStart);
            touch();
        }
    }

    public void delete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public Duration duration() {
        return Duration.between(startsAt, endsAt);
    }

    public boolean isRecurring() {
        return rrule != null;
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

    public String getLocation() {
        return location;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getRrule() {
        return rrule;
    }

    public String getColor() {
        return color;
    }

    public List<UUID> getParticipantIds() {
        return List.copyOf(participantIds);
    }

    public List<Integer> getReminderMinutes() {
        return List.copyOf(reminderMinutes);
    }

    public List<Instant> getExcludedStarts() {
        return List.copyOf(excludedStarts);
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
