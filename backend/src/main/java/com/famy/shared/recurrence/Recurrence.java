package com.famy.shared.recurrence;

import com.famy.shared.error.ApiException;
import java.time.Instant;
import java.time.ZoneId;
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
