package com.famy.chore;

import static org.assertj.core.api.Assertions.assertThat;

import com.famy.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ChoreFlowTest extends IntegrationTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    private String base(String familyId) {
        return "/api/v1/families/" + familyId + "/chores";
    }

    @Test
    void recurringChoreRotatesBetweenMembersAndKeepsHistory() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Tareas");
        String ana = joinAs(owner, familyId, "Ana", "MEMBER");
        String sofi = joinAs(owner, familyId, "Sofi", "CHILD");
        String anaId = meId(ana);
        String sofiId = meId(sofi);

        // Ayer a las 20:00 (atrasada), todos los días, rotando Sofi → Ana.
        Instant due = LocalDate.now(MX).minusDays(1).atTime(20, 0).atZone(MX).toInstant();
        MvcResult created = postJson(base(familyId), """
                {"title":"Lavar platos","dueAt":"%s","rrule":"FREQ=DAILY","points":5,
                 "rotation":true,"rotationOrder":["%s","%s"]}""".formatted(due, sofiId, anaId), owner);
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        String choreId = json(created, "$.id");
        assertThat((String) json(created, "$.assigneeId")).isEqualTo(sofiId);

        // Sofi (CHILD) marca la suya.
        MvcResult done = postJson(base(familyId) + "/" + choreId + "/complete", "", sofi);
        assertThat(status(done)).as(body(done)).isEqualTo(200);
        assertThat((String) json(done, "$.chore.assigneeId")).isEqualTo(anaId);
        Instant nextDue = Instant.parse(json(done, "$.chore.dueAt"));
        // Se completó tarde: la siguiente es la próxima ocurrencia después de ahora, a las 20:00.
        assertThat(nextDue).isAfter(Instant.now());
        assertThat(nextDue.atZone(MX).getHour()).isEqualTo(20);
        assertThat(ChronoUnit.HOURS.between(Instant.now(), nextDue)).isLessThan(25);
        assertThat((Integer) json(done, "$.completion.points")).isEqualTo(5);
        assertThat((String) json(done, "$.completion.occurrenceDate"))
                .isEqualTo(LocalDate.now(MX).minusDays(1).toString());

        // Ahora le toca a Ana: Sofi ya no puede marcarla.
        assertThat(status(postJson(base(familyId) + "/" + choreId + "/complete", "", sofi))).isEqualTo(403);

        // El dueño registra que la hizo Ana.
        MvcResult byOwner = postJson(base(familyId) + "/" + choreId + "/complete",
                "{\"memberId\":\"%s\"}".formatted(anaId), owner);
        assertThat(status(byOwner)).as(body(byOwner)).isEqualTo(200);
        assertThat((String) json(byOwner, "$.completion.completedBy")).isEqualTo(anaId);
        assertThat((String) json(byOwner, "$.chore.assigneeId")).isEqualTo(sofiId);

        MvcResult history = getAuth(base(familyId) + "/" + choreId + "/completions", ana);
        assertThat((List<String>) json(history, "$[*].completedBy")).containsExactly(anaId, sofiId);

        MvcResult score = getAuth(base(familyId) + "/scoreboard", sofi);
        assertThat((List<?>) json(score, "$.members")).hasSize(3);
        assertThat((Integer) json(score, "$.members[0].points")).isEqualTo(5);
        assertThat((Integer) json(score, "$.members[2].points")).isEqualTo(0);

        // Deshacer la última marca: vuelve a Ana y a la fecha anterior.
        String lastId = json(byOwner, "$.completion.id");
        String firstId = json(done, "$.completion.id");
        assertThat(status(deleteAs(base(familyId) + "/" + choreId + "/completions/" + firstId, owner)))
                .isEqualTo(409);
        MvcResult undone = deleteAs(base(familyId) + "/" + choreId + "/completions/" + lastId, ana);
        assertThat(status(undone)).as(body(undone)).isEqualTo(200);
        assertThat((String) json(undone, "$.assigneeId")).isEqualTo(anaId);
        assertThat(Instant.parse(json(undone, "$.dueAt"))).isEqualTo(nextDue);
    }

    @Test
    void oneOffChoreIsDoneOnceAndChildrenCannotManageChores() throws Exception {
        String owner = verifiedUser("Mamá");
        String familyId = createFamilyAs(owner, "Familia Única");
        String child = joinAs(owner, familyId, "Leo", "CHILD");

        assertThat(status(postJson(base(familyId), "{\"title\":\"Tender la cama\"}", child))).isEqualTo(403);
        MvcResult created = postJson(base(familyId),
                "{\"title\":\"Tender la cama\",\"assigneeId\":\"%s\",\"points\":2}".formatted(meId(child)), owner);
        String choreId = json(created, "$.id");
        assertThat(status(putJsonAs(base(familyId) + "/" + choreId, "{\"title\":\"X\"}", child))).isEqualTo(403);
        assertThat(status(deleteAs(base(familyId) + "/" + choreId, child))).isEqualTo(403);

        MvcResult done = postJson(base(familyId) + "/" + choreId + "/complete", "", child);
        assertThat(status(done)).as(body(done)).isEqualTo(200);
        assertThat((Boolean) json(done, "$.chore.done")).isTrue();
        assertThat(status(postJson(base(familyId) + "/" + choreId + "/complete", "", owner))).isEqualTo(409);

        assertThat((List<?>) json(getAuth(base(familyId), owner), "$")).isEmpty();
        assertThat((List<?>) json(getAuth(base(familyId) + "?includeDone=true", owner), "$")).hasSize(1);
    }

    @Test
    void validatesRecurrenceAndMembers() throws Exception {
        String owner = verifiedUser("Papá");
        String familyId = createFamilyAs(owner, "Familia Validación");
        String outsiderId = meId(verifiedUser("Vecino"));

        MvcResult noDue = postJson(base(familyId), "{\"title\":\"Regar\",\"rrule\":\"FREQ=WEEKLY\"}", owner);
        assertThat(status(noDue)).isEqualTo(400);
        assertThat((String) json(noDue, "$.code")).isEqualTo("due_required");

        MvcResult badRule = postJson(base(familyId),
                "{\"title\":\"Regar\",\"dueAt\":\"2026-12-01T10:00:00Z\",\"rrule\":\"FREQ=MINUTELY\"}", owner);
        assertThat((String) json(badRule, "$.code")).isEqualTo("invalid_rrule");

        MvcResult stranger = postJson(base(familyId),
                "{\"title\":\"Regar\",\"assigneeId\":\"%s\"}".formatted(outsiderId), owner);
        assertThat((String) json(stranger, "$.code")).isEqualTo("invalid_member");

        MvcResult shortRotation = postJson(base(familyId),
                "{\"title\":\"Regar\",\"rotation\":true,\"rotationOrder\":[\"%s\"]}".formatted(meId(owner)), owner);
        assertThat((String) json(shortRotation, "$.code")).isEqualTo("invalid_rotation");
    }
}
