package com.famy.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.famy.IntegrationTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class CalendarFlowTest extends IntegrationTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    private static String at(String local) {
        return LocalDateTime.parse(local).atZone(MX).toInstant().toString();
    }

    private String base(String familyId) {
        return "/api/v1/families/" + familyId + "/events";
    }

    @Test
    void recurringEventExpandsInRangeAndCanCancelOneOccurrence() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Agenda");
        String diego = joinAs(owner, familyId, "Diego", "CHILD");
        String diegoId = meId(diego);

        MvcResult created = postJson(base(familyId), """
                {"title":"Fútbol","location":"Cancha municipal","startsAt":"%s","endsAt":"%s",
                 "rrule":"FREQ=WEEKLY;BYDAY=TU,TH","participantIds":["%s"],"reminderMinutes":[60],
                 "color":"#2F5DA8"}""".formatted(at("2026-11-10T16:00"), at("2026-11-10T17:30"), diegoId), owner);
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        String eventId = json(created, "$.id");

        MvcResult range = getAuth(base(familyId) + "?from=%s&to=%s".formatted(at("2026-11-09T00:00"),
                at("2026-11-23T00:00")), diego);
        assertThat(status(range)).as(body(range)).isEqualTo(200);
        assertThat((List<String>) json(range, "$.occurrences[*].start")).containsExactly(at("2026-11-10T16:00"),
                at("2026-11-12T16:00"), at("2026-11-17T16:00"), at("2026-11-19T16:00"));
        assertThat((List<String>) json(range, "$.occurrences[*].end")).first().isEqualTo(at("2026-11-10T17:30"));
        assertThat((List<?>) json(range, "$.events")).hasSize(1);

        // Un evento en curso al inicio del rango también aparece.
        MvcResult during = getAuth(base(familyId) + "?from=%s&to=%s".formatted(at("2026-11-12T17:00"),
                at("2026-11-12T18:00")), owner);
        assertThat((List<String>) json(during, "$.occurrences[*].start")).containsExactly(at("2026-11-12T16:00"));

        // Diego (CHILD) no puede editar un evento que no creó.
        assertThat(status(deleteAs(base(familyId) + "/" + eventId, diego))).isEqualTo(403);

        // Cancelar solo el jueves 12.
        MvcResult cancel = deleteAs(base(familyId) + "/" + eventId + "/occurrences/" + at("2026-11-12T16:00"),
                owner);
        assertThat(status(cancel)).as(body(cancel)).isEqualTo(200);
        assertThat(status(deleteAs(base(familyId) + "/" + eventId + "/occurrences/" + at("2026-11-13T16:00"),
                owner))).isEqualTo(404);
        MvcResult after = getAuth(base(familyId) + "?from=%s&to=%s".formatted(at("2026-11-09T00:00"),
                at("2026-11-23T00:00")), owner);
        assertThat((List<String>) json(after, "$.occurrences[*].start")).containsExactly(at("2026-11-10T16:00"),
                at("2026-11-17T16:00"), at("2026-11-19T16:00"));

        // Filtro por miembro: el evento de Diego no sale para el dueño, uno de toda la familia sí.
        String ownerId = meId(owner);
        MvcResult family = postJson(base(familyId), """
                {"title":"Cena con abuelos","startsAt":"%s","allDay":true}""".formatted(at("2026-11-15T13:00")),
                owner);
        assertThat(status(family)).as(body(family)).isEqualTo(201);
        assertThat((String) json(family, "$.startsAt")).isEqualTo(at("2026-11-15T00:00"));
        assertThat((String) json(family, "$.endsAt")).isEqualTo(at("2026-11-16T00:00"));
        MvcResult mine = getAuth(base(familyId) + "?from=%s&to=%s&participantId=%s".formatted(at("2026-11-09T00:00"),
                at("2026-11-23T00:00"), ownerId), owner);
        assertThat((List<String>) json(mine, "$.events[*].title")).containsExactly("Cena con abuelos");

        // Un CHILD crea eventos propios, no para otros.
        assertThat(status(postJson(base(familyId), """
                {"title":"Piano","startsAt":"%s","participantIds":["%s"]}""".formatted(at("2026-11-11T17:30"),
                ownerId), diego))).isEqualTo(403);
        MvcResult own = postJson(base(familyId), """
                {"title":"Piano","startsAt":"%s","participantIds":["%s"]}""".formatted(at("2026-11-11T17:30"),
                diegoId), diego);
        assertThat(status(own)).as(body(own)).isEqualTo(201);
        assertThat(status(deleteAs(base(familyId) + "/" + json(own, "$.id"), diego))).isEqualTo(204);

        // Validaciones.
        assertThat(status(postJson(base(familyId), """
                {"title":"Mal","startsAt":"%s","endsAt":"%s"}""".formatted(at("2026-11-11T17:30"),
                at("2026-11-11T16:00")), owner))).isEqualTo(400);
        assertThat(status(postJson(base(familyId), """
                {"title":"Mal","startsAt":"%s","reminderMinutes":[99999]}""".formatted(at("2026-11-11T17:30")),
                owner))).isEqualTo(400);
        assertThat(status(getAuth(base(familyId) + "?from=%s&to=%s".formatted(at("2026-01-01T00:00"),
                at("2027-12-01T00:00")), owner))).isEqualTo(400);

        // Otra familia no ve nada.
        String stranger = verifiedUser("Extra");
        assertThat(status(getAuth(base(familyId) + "/" + eventId, stranger))).isEqualTo(404);
    }
}
