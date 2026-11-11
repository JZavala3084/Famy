package com.famy.calendar.application;

import com.famy.calendar.domain.CalendarEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representaciones que devuelve la API y que viajan como {@code payload} en los eventos en tiempo real. */
public final class CalendarViews {

    private CalendarViews() {
    }

    public record EventView(UUID id, String title, String description, String location, Instant startsAt,
            Instant endsAt, boolean allDay, String rrule, String color, List<UUID> participantIds,
            List<Integer> reminderMinutes, List<Instant> excludedStarts, UUID createdBy, Instant createdAt,
            Instant updatedAt, long version) {

        static EventView of(CalendarEvent e) {
            return new EventView(e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getStartsAt(),
                    e.getEndsAt(), e.isAllDay(), e.getRrule(), e.getColor(), e.getParticipantIds(),
                    e.getReminderMinutes(), e.getExcludedStarts(), e.getCreatedBy(), e.getCreatedAt(),
                    e.getUpdatedAt(), e.getVersion());
        }
    }

    /** Una ocurrencia concreta de un evento (los datos del evento van aparte, en {@code events}). */
    public record Occurrence(UUID eventId, Instant start, Instant end) {
    }

    /** Eventos que se cruzan con un rango y sus ocurrencias, ordenadas por inicio. */
    public record EventRange(Instant from, Instant to, List<EventView> events, List<Occurrence> occurrences) {
    }
}
