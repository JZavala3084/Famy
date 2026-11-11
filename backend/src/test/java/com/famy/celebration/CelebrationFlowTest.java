package com.famy.celebration;

import static org.assertj.core.api.Assertions.assertThat;

import com.famy.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class CelebrationFlowTest extends IntegrationTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    private String base(String familyId) {
        return "/api/v1/families/" + familyId + "/celebrations";
    }

    @Test
    void memberBirthdaysComeFromProfileAndGiftIdeasAreASurprise() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Cumples");
        String sofi = joinAs(owner, familyId, "Sofi", "CHILD");
        String sofiId = meId(sofi);
        LocalDate today = LocalDate.now(MX);
        LocalDate birthday = today.plusDays(12).minusYears(9);
        assertThat(status(patchJsonAs("/api/v1/me", "{\"birthday\":\"%s\"}".formatted(birthday), sofi)))
                .isEqualTo(200);

        // Sale sola en la lista, sin registro todavía.
        MvcResult list = getAuth(base(familyId), owner);
        assertThat(status(list)).as(body(list)).isEqualTo(200);
        assertThat((List<String>) json(list, "$[*].personName")).containsExactly("Sofi");
        assertThat((Integer) json(list, "$[0].daysUntil")).isEqualTo(12);
        assertThat((Integer) json(list, "$[0].turning")).isEqualTo(9);
        assertThat((Object) json(list, "$[0].id")).isNull();

        // El papá anota ideas de regalo.
        MvcResult gifts = postJson(base(familyId), """
                {"userId":"%s","giftIdeas":[{"text":"Bicicleta","done":false},{"text":"Libro","done":true}]}"""
                .formatted(sofiId), owner);
        assertThat(status(gifts)).as(body(gifts)).isEqualTo(201);
        String id = json(gifts, "$.id");
        assertThat((List<String>) json(gifts, "$.giftIdeas[*].text")).containsExactly("Bicicleta", "Libro");
        assertThat(status(postJson(base(familyId), "{\"userId\":\"%s\"}".formatted(sofiId), owner)))
                .isEqualTo(409);

        // Sofi ve su cumpleaños pero no los regalos, y no puede borrarlos sin querer.
        MvcResult hers = getAuth(base(familyId) + "/" + id, sofi);
        assertThat((Boolean) json(hers, "$.giftIdeasHidden")).isTrue();
        assertThat((Object) json(hers, "$.giftIdeas")).isNull();
        MvcResult edit = putJsonAs(base(familyId) + "/" + id, "{\"notes\":\"Pastel de chocolate\",\"giftIdeas\":[]}",
                sofi);
        assertThat(status(edit)).as(body(edit)).isEqualTo(200);
        MvcResult again = getAuth(base(familyId) + "/" + id, owner);
        assertThat((List<?>) json(again, "$.giftIdeas")).hasSize(2);
        assertThat((String) json(again, "$.notes")).isEqualTo("Pastel de chocolate");

        // Persona externa y aniversario, ordenados por cercanía.
        LocalDate grandma = today.plusDays(3);
        MvcResult ext = postJson(base(familyId), """
                {"personName":"Abuela Rosa","month":%d,"day":%d,"year":1956}"""
                .formatted(grandma.getMonthValue(), grandma.getDayOfMonth()), sofi);
        assertThat(status(ext)).as(body(ext)).isEqualTo(201);
        assertThat((Integer) json(ext, "$.turning")).isEqualTo(grandma.getYear() - 1956);
        assertThat(status(postJson(base(familyId), """
                {"type":"ANNIVERSARY","personName":"Papá y Mamá","month":2,"day":30}""", owner))).isEqualTo(400);
        MvcResult ordered = getAuth(base(familyId), owner);
        assertThat((List<String>) json(ordered, "$[*].personName")).containsExactly("Abuela Rosa", "Sofi");
        List<Map<String, Object>> sofiView = json(getAuth(base(familyId), sofi), "$[?(@.personName == 'Sofi')]");
        assertThat(sofiView.getFirst().get("giftIdeas")).isNull();

        assertThat(status(deleteAs(base(familyId) + "/" + json(ext, "$.id"), owner))).isEqualTo(204);
        assertThat((List<?>) json(getAuth(base(familyId), owner), "$")).hasSize(1);
    }
}
