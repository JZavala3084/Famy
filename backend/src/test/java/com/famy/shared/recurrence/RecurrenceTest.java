package com.famy.shared.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.famy.shared.error.ApiException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RecurrenceTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");
    private static final ZoneId NY = ZoneId.of("America/New_York");

    private static Instant at(ZoneId zone, String local) {
        return LocalDateTime.parse(local).atZone(zone).toInstant();
    }

    @Test
    void weeklyNextOccurrenceAfterAGivenInstant() {
        Instant start = at(MX, "2026-11-02T20:00"); // lunes
        String rule = "FREQ=WEEKLY;BYDAY=MO,TH";
        assertThat(Recurrence.nextAfter(rule, start, MX, start)).contains(at(MX, "2026-11-05T20:00"));
        assertThat(Recurrence.nextAfter(rule, start, MX, at(MX, "2026-11-20T09:00")))
                .contains(at(MX, "2026-11-23T20:00"));
    }

    @Test
    void keepsLocalTimeAcrossDaylightSavingChange() {
        // En 2026 Nueva York pasa a horario de invierno el 1 de noviembre.
        Instant start = at(NY, "2026-10-26T19:00");
        Instant next = Recurrence.nextAfter("FREQ=WEEKLY", start, NY, start).orElseThrow();
        ZonedDateTime local = next.atZone(NY);
        assertThat(local.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-11-02T19:00"));
    }

    @Test
    void finiteRulesEnd() {
        Instant start = at(MX, "2026-11-01T08:00");
        String rule = "FREQ=DAILY;COUNT=3";
        assertThat(Recurrence.nextAfter(rule, start, MX, at(MX, "2026-11-02T08:00")))
                .contains(at(MX, "2026-11-03T08:00"));
        assertThat(Recurrence.nextAfter(rule, start, MX, at(MX, "2026-11-03T08:00"))).isEmpty();
    }

    @Test
    void validatesRules() {
        assertThat(Recurrence.validate("RRULE:FREQ=MONTHLY;BYMONTHDAY=1")).contains("FREQ=MONTHLY");
        assertThatThrownBy(() -> Recurrence.validate("FREQ=HOURLY")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Recurrence.validate("cada lunes")).isInstanceOf(ApiException.class);
    }
}
