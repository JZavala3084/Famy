package com.famy.calendar.infrastructure;

import com.famy.calendar.domain.CalendarEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @Query("select e from CalendarEvent e where e.id = :id and e.familyId = :familyId and e.deletedAt is null")
    Optional<CalendarEvent> findActive(UUID familyId, UUID id);

    /** Candidatos para un rango: los únicos que se cruzan con él y todos los que se repiten y empezaron antes. */
    @Query("""
            select e from CalendarEvent e where e.familyId = :familyId and e.deletedAt is null
            and e.startsAt < :to and (e.rrule is not null or e.endsAt > :from)
            order by e.startsAt""")
    List<CalendarEvent> findCandidates(UUID familyId, Instant from, Instant to);

    /** Lo mismo para todas las familias (job de recordatorios; se filtran después los que no tienen avisos). */
    @Query("""
            select e from CalendarEvent e where e.deletedAt is null
            and e.startsAt < :to and (e.rrule is not null or e.endsAt > :from)""")
    List<CalendarEvent> findAllWithRemindersCandidates(Instant from, Instant to);

    @Query("select count(e) from CalendarEvent e where e.familyId = :familyId and e.deletedAt is null")
    long countActive(UUID familyId);
}
