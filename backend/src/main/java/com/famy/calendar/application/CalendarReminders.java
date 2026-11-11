package com.famy.calendar.application;

import com.famy.calendar.application.CalendarViews.Occurrence;
import com.famy.calendar.domain.CalendarEvent;
import com.famy.calendar.infrastructure.CalendarEventRepository;
import com.famy.family.FamilyMembership;
import com.famy.notification.NotificationType;
import com.famy.notification.Reminder;
import com.famy.notification.ReminderSource;
import com.famy.shared.time.Spanish;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** RF-6.5: avisa a los participantes (o a toda la familia si no hay) los minutos indicados antes de cada ocurrencia. */
@Component
class CalendarReminders implements ReminderSource {

    private final CalendarEventRepository events;
    private final FamilyMembership membership;

    CalendarReminders(CalendarEventRepository events, FamilyMembership membership) {
        this.events = events;
        this.membership = membership;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reminder> remindersBetween(Instant from, Instant to) {
        List<Reminder> out = new ArrayList<>();
        Map<UUID, ZoneId> zones = new HashMap<>();
        Instant maxTo = to.plusSeconds(CalendarService.MAX_REMINDER_MINUTES * 60L);
        for (CalendarEvent e : events.findAllWithRemindersCandidates(from, maxTo)) {
            if (e.getReminderMinutes().isEmpty()) {
                continue;
            }
            ZoneId zone = zones.computeIfAbsent(e.getFamilyId(), membership::timezone);
            for (int minutes : e.getReminderMinutes()) {
                Instant windowFrom = from.plusSeconds(minutes * 60L);
                Instant windowTo = to.plusSeconds(minutes * 60L);
                // Ocurrencias que empiezan en (windowFrom, windowTo].
                for (Occurrence o : CalendarService.occurrences(e, zone, windowFrom.plusMillis(1),
                        windowTo.plusMillis(1), 50)) {
                    if (!o.start().isAfter(windowFrom) || o.start().isAfter(windowTo)) {
                        continue;
                    }
                    out.add(reminder(e, o, minutes, zone));
                }
            }
        }
        return out;
    }

    private Reminder reminder(CalendarEvent e, Occurrence o, int minutes, ZoneId zone) {
        Set<UUID> recipients = e.getParticipantIds().isEmpty()
                ? membership.activeMemberIds(e.getFamilyId())
                : Set.copyOf(e.getParticipantIds());
        Instant fireAt = o.start().minusSeconds(minutes * 60L);
        ZonedDateTime start = o.start().atZone(zone);
        LocalDate today = fireAt.atZone(zone).toLocalDate();
        String when = e.isAllDay()
                ? Spanish.capitalize(Spanish.day(start.toLocalDate(), today)) + ", todo el día"
                : Spanish.capitalize(Spanish.dayAndTime(start, today));
        String body = e.getLocation() == null ? when : when + " · " + e.getLocation();
        return new Reminder("event:%s:%d:%d".formatted(e.getId(), o.start().getEpochSecond(), minutes),
                e.getFamilyId(), recipients, NotificationType.EVENT, e.getTitle(), body,
                "/agenda?dia=" + start.toLocalDate(), fireAt);
    }
}
