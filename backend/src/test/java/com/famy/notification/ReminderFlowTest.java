package com.famy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.famy.IntegrationTest;
import com.famy.TestDoubles.PushInbox;
import com.famy.notification.application.ReminderJob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

class ReminderFlowTest extends IntegrationTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");
    private static final String ENDPOINT = "https://push.example.com/send/abc";
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";

    @Autowired
    ReminderJob job;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PushInbox pushInbox;

    /** Corre el job desde {@code start} hasta {@code end} en pasos de 1 h (como si pasara el tiempo). */
    private int runBetween(Instant start, Instant end) {
        jdbc.sql("UPDATE reminder_checkpoint SET checked_up_to = :t WHERE id = 1")
                .param("t", Timestamp.from(start)).update();
        int sent = 0;
        for (Instant t = start.plus(Duration.ofHours(1)); !t.isAfter(end); t = t.plus(Duration.ofHours(1))) {
            sent += job.runUntil(t);
        }
        return sent;
    }

    @Test
    void sendsEventPaymentAndBirthdayRemindersOnceToTheRightPeople() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Avisos");
        String ana = joinAs(owner, familyId, "Ana", "MEMBER");
        String sofi = joinAs(owner, familyId, "Sofi", "CHILD");
        String sofiId = meId(sofi);
        String base = "/api/v1/families/" + familyId;

        // El dueño activa push en su navegador; una suscripción vieja se borra al responder 410.
        assertThat(status(postJson("/api/v1/notifications/push/subscriptions", """
                {"endpoint":"%s","keys":{"p256dh":"%s","auth":"BTBZMqHH6r4Tts7J_aSIgg"}}""".formatted(ENDPOINT,
                P256DH), owner))).isEqualTo(204);
        assertThat(status(postJson("/api/v1/notifications/push/subscriptions", """
                {"endpoint":"https://push.example.com/gone","keys":{"p256dh":"%s","auth":"BTBZMqHH6r4Tts7J_aSIgg"}}"""
                .formatted(P256DH), owner))).isEqualTo(204);
        assertThat(status(postJson("/api/v1/notifications/push/subscriptions", """
                {"endpoint":"http://inseguro","keys":{"p256dh":"%s","auth":"BTBZMqHH6r4Tts7J_aSIgg"}}"""
                .formatted(P256DH), owner))).isEqualTo(400);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        // Evento de Sofi dentro de 3 h con aviso 60 min antes.
        assertThat(status(postJson(base + "/events", """
                {"title":"Piano","startsAt":"%s","participantIds":["%s"],"reminderMinutes":[60]}"""
                .formatted(now.plus(Duration.ofHours(3)), sofiId), owner))).isEqualTo(201);
        // Evento de toda la familia con aviso, para el dueño (que tiene push).
        assertThat(status(postJson(base + "/events", """
                {"title":"Cena familiar","startsAt":"%s","reminderMinutes":[30]}"""
                .formatted(now.plus(Duration.ofHours(4))), owner))).isEqualTo(201);

        // El próximo aviso de las 9:00 de la familia después de ahora.
        ZonedDateTime nine = now.atZone(MX).toLocalDate().atTime(9, 0).atZone(MX);
        if (!nine.toInstant().isAfter(now)) {
            nine = nine.plusDays(1);
        }
        LocalDate sendDay = nine.toLocalDate();
        // Pago que vence 2 días después (aviso 2 días antes) a cargo de Ana.
        String anaId = meId(ana);
        assertThat(status(postJson(base + "/payments", """
                {"name":"Internet","amount":499,"firstDueOn":"%s","remindDaysBefore":2,"responsibleId":"%s"}"""
                .formatted(sendDay.plusDays(2), anaId), owner))).isEqualTo(201);
        // Cumpleaños de Sofi en 7 días: avisa a todos menos a ella.
        assertThat(status(patchJsonAs("/api/v1/me", "{\"birthday\":\"%s\"}".formatted(
                sendDay.plusDays(7).minusYears(10)), sofi))).isEqualTo(200);

        Instant end = nine.toInstant().plus(Duration.ofHours(1));
        if (end.isBefore(now.plus(Duration.ofHours(5)))) {
            end = now.plus(Duration.ofHours(5));
        }
        int sent = runBetween(now, end);
        // Otras pruebas pueden dejar eventos con aviso en el mismo periodo: se revisa por persona más abajo.
        assertThat(sent).isGreaterThanOrEqualTo(4);
        // Volver a revisar el mismo periodo no repite nada.
        assertThat(runBetween(now, end)).isZero();

        MvcResult sofiNotes = getAuth("/api/v1/notifications", sofi);
        assertThat((List<String>) json(sofiNotes, "$.items[*].title")).containsExactlyInAnyOrder("Piano",
                "Cena familiar");
        assertThat((Integer) json(sofiNotes, "$.unread")).isEqualTo(2);

        MvcResult anaNotes = getAuth("/api/v1/notifications", ana);
        assertThat((List<String>) json(anaNotes, "$.items[*].title")).containsExactlyInAnyOrder("Cena familiar",
                "Pago: Internet", "Cumpleaños de Sofi");
        assertThat((List<String>) json(anaNotes, "$.items[*].body")).anyMatch(b -> b.startsWith("Vence en 2 días"))
                .anyMatch(b -> b.startsWith("Cumple 10 en 7 días"));

        MvcResult ownerNotes = getAuth("/api/v1/notifications", owner);
        assertThat((List<String>) json(ownerNotes, "$.items[*].title")).containsExactlyInAnyOrder("Cena familiar",
                "Cumpleaños de Sofi");
        String firstId = json(ownerNotes, "$.items[0].id");
        assertThat(status(postJson("/api/v1/notifications/" + firstId + "/read", "", owner))).isEqualTo(200);
        assertThat(status(postJson("/api/v1/notifications/" + firstId + "/read", "", ana))).isEqualTo(404);
        assertThat((Integer) json(getAuth("/api/v1/notifications", owner), "$.unread")).isEqualTo(1);
        assertThat(status(postJson("/api/v1/notifications/read-all", "", owner))).isEqualTo(204);
        assertThat((Integer) json(getAuth("/api/v1/notifications", owner), "$.unread")).isZero();

        // Push: llegaron al navegador del dueño y la suscripción muerta se borró.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(pushInbox.to(ENDPOINT)).hasSize(2);
            assertThat((Integer) json(getAuth("/api/v1/notifications/push", owner), "$.subscriptions"))
                    .isEqualTo(1);
        });
        assertThat(pushInbox.to(ENDPOINT).getFirst().payload()).contains("\"link\":");

        // Preferencias: silenciar cumpleaños y probar.
        MvcResult prefs = putJsonAs("/api/v1/notifications/preferences",
                "{\"mutedTypes\":[\"CELEBRATION\"],\"quietFrom\":\"22:00\",\"quietTo\":\"07:00\"}", owner);
        assertThat(status(prefs)).as(body(prefs)).isEqualTo(200);
        assertThat((List<String>) json(prefs, "$.mutedTypes")).containsExactly("CELEBRATION");
        assertThat(status(postJson("/api/v1/notifications/push/test", "", owner))).isEqualTo(202);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(pushInbox.to(ENDPOINT)).hasSize(3));
    }
}
