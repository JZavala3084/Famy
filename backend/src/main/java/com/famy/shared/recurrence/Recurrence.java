package com.famy.shared.recurrence;

import com.famy.shared.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;

/**
 * Recurrencias RFC 5545 (RRULE) expandidas en la zona horaria de la familia, para que «cada lunes a las 20:00» siga
 * siendo a las 20:00 después de un cambio de horario. Lo usan tareas y, más adelante, calendario y pagos.
 */
public final class Recurrence {

    private static final Set<Freq> ALLOWED = Set.of(Freq.DAILY, Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY);
    private static final int MAX_ITERATIONS = 100_000;

    private Recurrence() {
    }

    /** Valida y normaliza una RRULE (sin el prefijo {@code RRULE:}); solo frecuencias diaria o mayores. */
    public static String validate(String rrule) {
        RecurrenceRule rule = parse(rrule);
        if (!ALLOWED.contains(rule.getFreq())) {
            throw ApiException.badRequest("invalid_rrule", "La repetición debe ser diaria, semanal, mensual o anual.");
        }
        return rule.toString();
    }

    /**
     * Primera ocurrencia estrictamente posterior a {@code after}, contando desde {@code start} (el DTSTART), o vacío
     * si la regla ya terminó (COUNT / UNTIL).
     */
    public static Optional<Instant> nextAfter(String rrule, Instant start, ZoneId zone, Instant after) {
        RecurrenceRule rule = parse(rrule);
        DateTime dtStart = new DateTime(TimeZone.getTimeZone(zone), start.toEpochMilli());
        RecurrenceRuleIterator it = rule.iterator(dtStart);
        if (after.isAfter(start)) {
            it.fastForward(after.toEpochMilli());
        }
        for (int i = 0; i < MAX_ITERATIONS && it.hasNext(); i++) {
            long next = it.nextMillis();
            if (next > after.toEpochMilli()) {
                return Optional.of(Instant.ofEpochMilli(next));
            }
        }
        return Optional.empty();
    }

    /**
     * Ocurrencias que empiezan en {@code [from, to)}, contando desde {@code start} (el DTSTART, que es la primera
     * ocurrencia), como mucho {@code max}.
     */
    public static List<Instant> between(String rrule, Instant start, ZoneId zone, Instant from, Instant to, int max) {
        List<Instant> out = new ArrayList<>();
        if (!to.isAfter(from)) {
            return out;
        }
        RecurrenceRule rule = parse(rrule);
        DateTime dtStart = new DateTime(TimeZone.getTimeZone(zone), start.toEpochMilli());
        RecurrenceRuleIterator it = rule.iterator(dtStart);
        if (from.isAfter(start)) {
            it.fastForward(from.toEpochMilli());
        } else if (start.isBefore(to)) {
            // RFC 5545: el DTSTART siempre es la primera ocurrencia, aunque no cumpla la regla.
            out.add(start);
        }
        for (int i = 0; i < MAX_ITERATIONS && it.hasNext() && out.size() < max; i++) {
            long next = it.nextMillis();
            if (next >= to.toEpochMilli()) {
                break;
            }
            if (next >= from.toEpochMilli() && next != start.toEpochMilli()) {
                out.add(Instant.ofEpochMilli(next));
            }
        }
        return out;
    }

    /** Igual que {@link #between} pero con fechas sin hora (pagos, eventos de todo el día): {@code [from, to]}. */
    public static List<LocalDate> datesBetween(String rrule, LocalDate start, LocalDate from, LocalDate to, int max) {
        return between(rrule, midnightUtc(start), ZoneOffset.UTC, midnightUtc(from), midnightUtc(to.plusDays(1)), max)
                .stream().map(i -> LocalDate.ofInstant(i, ZoneOffset.UTC)).toList();
    }

    /** Primera fecha estrictamente posterior a {@code after}, o vacío si la regla terminó. */
    public static Optional<LocalDate> nextDateAfter(String rrule, LocalDate start, LocalDate after) {
        return nextAfter(rrule, midnightUtc(start), ZoneOffset.UTC, midnightUtc(after))
                .map(i -> LocalDate.ofInstant(i, ZoneOffset.UTC));
    }

    private static Instant midnightUtc(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static RecurrenceRule parse(String rrule) {
        String value = rrule.strip();
        if (value.regionMatches(true, 0, "RRULE:", 0, 6)) {
            value = value.substring(6);
        }
        if (value.length() > 255) {
            throw ApiException.badRequest("invalid_rrule", "La regla de repetición es demasiado larga.");
        }
        try {
            return new RecurrenceRule(value, RecurrenceRule.RfcMode.RFC5545_STRICT);
        } catch (InvalidRecurrenceRuleException | IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_rrule", "La regla de repetición (RRULE) no es válida.");
        }
    }
}
