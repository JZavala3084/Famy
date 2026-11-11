package com.famy.calendar.application;

import com.famy.calendar.application.CalendarViews.EventRange;
import com.famy.calendar.application.CalendarViews.EventView;
import com.famy.calendar.application.CalendarViews.Occurrence;
import com.famy.calendar.domain.CalendarEvent;
import com.famy.calendar.domain.CalendarEvent.EventData;
import com.famy.calendar.infrastructure.CalendarEventRepository;
import com.famy.family.FamilyMembership;
import com.famy.family.FamilyRole;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.shared.recurrence.Recurrence;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del calendario. Todos los miembros crean eventos; un CHILD solo edita o borra los que creó (ver
 * docs/06-familias.md#roles-y-permisos).
 */
@Service
public class CalendarService {

    static final String EVENT = "calendar.event";
    static final int MAX_EVENTS = 2_000;
    static final int MAX_OCCURRENCES = 2_000;
    static final Duration MAX_RANGE = Duration.ofDays(400);
    static final Duration MAX_EVENT_LENGTH = Duration.ofDays(60);
    static final int MAX_REMINDER_MINUTES = 7 * 24 * 60;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final CalendarEventRepository events;
    private final FamilyMembership membership;
    private final ChangePublisher changes;

    public CalendarService(CalendarEventRepository events, FamilyMembership membership, ChangePublisher changes) {
        this.events = events;
        this.membership = membership;
        this.changes = changes;
    }

    /** Datos tal como llegan de la API (sin validar contra la familia). */
    public record EventInput(String title, String description, String location, Instant startsAt, Instant endsAt,
            boolean allDay, String rrule, String color, List<UUID> participantIds, List<Integer> reminderMinutes) {
    }

    /**
     * Eventos y ocurrencias que se cruzan con {@code [from, to)}.
     *
     * @param participantId si se indica, solo los eventos de esa persona (o de toda la familia: sin participantes)
     */
    @Transactional(readOnly = true)
    public EventRange range(UUID familyId, UUID userId, Instant from, Instant to, UUID participantId) {
        membership.requireMember(familyId, userId);
        if (from == null || to == null || !to.isAfter(from)) {
            throw ApiException.badRequest("invalid_range", "El rango de fechas no es válido.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw ApiException.badRequest("invalid_range", "El rango no puede pasar de 400 días.");
        }
        ZoneId zone = membership.timezone(familyId);
        List<EventView> views = new ArrayList<>();
        List<Occurrence> occurrences = new ArrayList<>();
        for (CalendarEvent e : events.findCandidates(familyId, from, to)) {
            if (participantId != null && !e.getParticipantIds().isEmpty()
                    && !e.getParticipantIds().contains(participantId)) {
                continue;
            }
            List<Occurrence> own = occurrences(e, zone, from, to, MAX_OCCURRENCES - occurrences.size());
            if (!own.isEmpty()) {
                views.add(EventView.of(e));
                occurrences.addAll(own);
            }
            if (occurrences.size() >= MAX_OCCURRENCES) {
                break;
            }
        }
        occurrences.sort(Comparator.comparing(Occurrence::start).thenComparing(Occurrence::eventId));
        return new EventRange(from, to, views, occurrences);
    }

    /** Ocurrencias de un evento que se cruzan con {@code [from, to)}, sin las canceladas. */
    static List<Occurrence> occurrences(CalendarEvent e, ZoneId zone, Instant from, Instant to, int max) {
        Duration length = e.duration();
        if (!e.isRecurring()) {
            return e.getStartsAt().isBefore(to) && (e.getEndsAt().isAfter(from) || e.getStartsAt().equals(from))
                    ? List.of(new Occurrence(e.getId(), e.getStartsAt(), e.getEndsAt()))
                    : List.of();
        }
        // Una ocurrencia que empezó antes de `from` puede seguir en curso.
        Instant searchFrom = from.minus(length);
        Set<Instant> excluded = new HashSet<>(e.getExcludedStarts());
        List<Occurrence> out = new ArrayList<>();
        for (Instant start : Recurrence.between(e.getRrule(), e.getStartsAt(), zone, searchFrom, to,
                max + excluded.size() + 1)) {
            Instant end = start.plus(length);
            if (excluded.contains(start) || !(end.isAfter(from) || start.equals(from))) {
                continue;
            }
            out.add(new Occurrence(e.getId(), start, end));
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public EventView get(UUID familyId, UUID userId, UUID eventId) {
        membership.requireMember(familyId, userId);
        return EventView.of(require(familyId, eventId));
    }

    @Transactional
    public EventView create(UUID familyId, UUID userId, EventInput input) {
        FamilyRole role = membership.requireMember(familyId, userId);
        if (events.countActive(familyId) >= MAX_EVENTS) {
            throw ApiException.conflict("too_many_events", "La familia ya tiene el máximo de eventos.");
        }
        EventData data = validate(familyId, input);
        if (role == FamilyRole.CHILD && !data.participantIds().isEmpty() && !data.participantIds().equals(List.of(userId))) {
            throw ApiException.forbidden("forbidden", "Solo puedes crear eventos propios.");
        }
        CalendarEvent event = events.saveAndFlush(new CalendarEvent(familyId, data, userId));
        EventView view = EventView.of(event);
        changes.created(familyId, EVENT, event.getId(), event.getVersion(), view);
        return view;
    }

    @Transactional
    public EventView update(UUID familyId, UUID userId, UUID eventId, EventInput input, Long version) {
        CalendarEvent event = requireEditable(familyId, userId, eventId);
        if (version != null && version != event.getVersion()) {
            throw versionConflict();
        }
        event.edit(validate(familyId, input));
        return publish(events.saveAndFlush(event));
    }

    @Transactional
    public void delete(UUID familyId, UUID userId, UUID eventId) {
        CalendarEvent event = requireEditable(familyId, userId, eventId);
        event.delete();
        events.saveAndFlush(event);
        changes.deleted(familyId, EVENT, eventId, event.getVersion());
    }

    /** Cancela una sola ocurrencia («borrar solo este día»); en un evento único equivale a borrarlo. */
    @Transactional
    public EventView cancelOccurrence(UUID familyId, UUID userId, UUID eventId, Instant start) {
        CalendarEvent event = requireEditable(familyId, userId, eventId);
        if (!event.isRecurring()) {
            if (!event.getStartsAt().equals(start)) {
                throw ApiException.notFound("occurrence_not_found", "Esa ocurrencia no existe.");
            }
            delete(familyId, userId, eventId);
            return null;
        }
        ZoneId zone = membership.timezone(familyId);
        boolean exists = Recurrence.between(event.getRrule(), event.getStartsAt(), zone, start,
                start.plusMillis(1), 1).contains(start);
        if (!exists) {
            throw ApiException.notFound("occurrence_not_found", "Esa ocurrencia no existe.");
        }
        event.exclude(start);
        return publish(events.saveAndFlush(event));
    }

    // ---------------------------------------------------------------- auxiliares

    private EventData validate(UUID familyId, EventInput in) {
        if (in.startsAt() == null) {
            throw ApiException.badRequest("start_required", "Indica cuándo empieza el evento.");
        }
        ZoneId zone = membership.timezone(familyId);
        Instant start = in.startsAt();
        Instant end = in.endsAt() == null ? start.plus(Duration.ofHours(1)) : in.endsAt();
        if (in.allDay()) {
            // Todo el día: de la medianoche del primer día a la del día siguiente al último (zona de la familia).
            start = start.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
            var lastDay = (in.endsAt() == null ? in.startsAt() : in.endsAt().minusMillis(1)).atZone(zone).toLocalDate();
            end = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
            if (!end.isAfter(start)) {
                end = start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
            }
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("invalid_end", "El evento no puede terminar antes de empezar.");
        }
        if (Duration.between(start, end).compareTo(MAX_EVENT_LENGTH) > 0) {
            throw ApiException.badRequest("invalid_end", "Un evento no puede durar más de 60 días.");
        }
        String rrule = in.rrule() == null || in.rrule().isBlank() ? null : Recurrence.validate(in.rrule());
        if (in.color() != null && !in.color().isBlank() && !COLOR.matcher(in.color()).matches()) {
            throw ApiException.badRequest("invalid_color", "El color debe tener el formato #RRGGBB.");
        }
        List<UUID> participants = List.copyOf(new LinkedHashSet<>(
                in.participantIds() == null ? List.of() : in.participantIds()));
        if (!membership.activeMemberIds(familyId).containsAll(participants)) {
            throw ApiException.badRequest("invalid_member", "Algún participante no es miembro de la familia.");
        }
        List<Integer> reminders = (in.reminderMinutes() == null ? List.<Integer>of() : in.reminderMinutes())
                .stream().distinct().sorted().toList();
        if (reminders.size() > 5 || reminders.stream().anyMatch(m -> m == null || m < 0 || m > MAX_REMINDER_MINUTES)) {
            throw ApiException.badRequest("invalid_reminders",
                    "Hasta 5 recordatorios, entre 0 minutos y 7 días antes.");
        }
        return new EventData(in.title(), in.description(), in.location(), start, end, in.allDay(), rrule,
                in.color(), participants, reminders);
    }

    private CalendarEvent requireEditable(UUID familyId, UUID userId, UUID eventId) {
        FamilyRole role = membership.requireMember(familyId, userId);
        CalendarEvent event = require(familyId, eventId);
        if (role == FamilyRole.CHILD && !userId.equals(event.getCreatedBy())) {
            throw ApiException.forbidden("forbidden", "Solo puedes cambiar los eventos que creaste.");
        }
        return event;
    }

    private CalendarEvent require(UUID familyId, UUID eventId) {
        return events.findActive(familyId, eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Evento no encontrado."));
    }

    private EventView publish(CalendarEvent event) {
        EventView view = EventView.of(event);
        changes.updated(event.getFamilyId(), EVENT, event.getId(), event.getVersion(), view);
        return view;
    }

    private static ApiException versionConflict() {
        return ApiException.conflict("version_conflict",
                "Otro miembro modificó este dato al mismo tiempo. Vuelve a intentarlo.");
    }
}
