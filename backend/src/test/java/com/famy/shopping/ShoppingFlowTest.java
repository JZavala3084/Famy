package com.famy.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import com.famy.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ShoppingFlowTest extends IntegrationTest {

    private String base(String familyId) {
        return "/api/v1/families/" + familyId + "/shopping";
    }

    private String createList(String token, String familyId, String name) throws Exception {
        MvcResult r = postJson(base(familyId) + "/lists", "{\"name\":\"%s\",\"icon\":\"cart\"}".formatted(name), token);
        assertThat(status(r)).as(body(r)).isEqualTo(201);
        return json(r, "$.id");
    }

    private String addItem(String token, String familyId, String listId, String json) throws Exception {
        MvcResult r = postJson(base(familyId) + "/lists/" + listId + "/items", json, token);
        assertThat(status(r)).as(body(r)).isEqualTo(201);
        return json(r, "$.id");
    }

    @Test
    void familyShopsTogetherAndCheckedItemsSinkToTheBottom() throws Exception {
        String owner = verifiedUser("Ana");
        String familyId = createFamilyAs(owner, "Familia Compras");
        String child = joinAs(owner, familyId, "Sofi", "CHILD");
        String listId = createList(owner, familyId, "Supermercado");

        String leche = addItem(owner, familyId, listId,
                "{\"name\":\"Leche\",\"quantity\":2,\"unit\":\"L\",\"category\":\"Lácteos\"}");
        String pan = addItem(child, familyId, listId, "{\"name\":\"Pan\"}");
        addItem(owner, familyId, listId, "{\"name\":\"Huevos\",\"quantity\":12}");

        MvcResult check = postJson(base(familyId) + "/lists/" + listId + "/items/" + leche + "/check", "", child);
        assertThat(status(check)).isEqualTo(200);
        assertThat((Boolean) json(check, "$.checked")).isTrue();
        assertThat((String) json(check, "$.checkedBy")).isEqualTo(meId(child));

        MvcResult items = getAuth(base(familyId) + "/lists/" + listId + "/items", owner);
        List<String> names = json(items, "$[*].name");
        assertThat(names).containsExactly("Pan", "Huevos", "Leche");
        assertThat((Double) json(items, "$[2].quantity")).isEqualTo(2.0);

        MvcResult lists = getAuth(base(familyId) + "/lists", child);
        assertThat((Integer) json(lists, "$[0].totalItems")).isEqualTo(3);
        assertThat((Integer) json(lists, "$[0].pendingItems")).isEqualTo(2);

        MvcResult cleared = postJson(base(familyId) + "/lists/" + listId + "/items/clear-checked", "", owner);
        assertThat((Integer) json(cleared, "$.removed")).isEqualTo(1);
        assertThat((List<?>) json(getAuth(base(familyId) + "/lists/" + listId + "/items", owner), "$"))
                .hasSize(2);

        // Editar con versión vieja → 409; con la actual → 200.
        MvcResult edited = putJsonAs(base(familyId) + "/lists/" + listId + "/items/" + pan,
                "{\"name\":\"Pan integral\",\"version\":0}", owner);
        assertThat(status(edited)).as(body(edited)).isEqualTo(200);
        assertThat(status(putJsonAs(base(familyId) + "/lists/" + listId + "/items/" + pan,
                "{\"name\":\"Pan blanco\",\"version\":0}", owner))).isEqualTo(409);

        // Un CHILD agrega y marca, pero no borra listas.
        assertThat(status(deleteAs(base(familyId) + "/lists/" + listId, child))).isEqualTo(403);
        assertThat(status(deleteAs(base(familyId) + "/lists/" + listId, owner))).isEqualTo(204);
        assertThat(status(getAuth(base(familyId) + "/lists/" + listId + "/items", owner))).isEqualTo(404);
    }

    @Test
    void suggestionsComeFromTheFamilyHistory() throws Exception {
        String owner = verifiedUser("Luis");
        String familyId = createFamilyAs(owner, "Familia Sugerencias");
        String super1 = createList(owner, familyId, "Super");
        for (int i = 0; i < 3; i++) {
            String id = addItem(owner, familyId, super1, "{\"name\":\"Leche\",\"unit\":\"L\"}");
            postJson(base(familyId) + "/lists/" + super1 + "/items/" + id + "/check", "", owner);
        }
        postJson(base(familyId) + "/lists/" + super1 + "/items/clear-checked", "", owner);
        addItem(owner, familyId, super1, "{\"name\":\"Tortillas\"}");
        addItem(owner, familyId, super1, "{\"name\":\"tortillas\"}");
        addItem(owner, familyId, super1, "{\"name\":\"Limones\"}");

        MvcResult all = getAuth(base(familyId) + "/suggestions", owner);
        assertThat((String) json(all, "$[0].name")).isEqualTo("Leche");
        assertThat((Integer) json(all, "$[0].uses")).isEqualTo(3);
        assertThat((String) json(all, "$[0].unit")).isEqualTo("L");
        assertThat((Integer) json(all, "$[1].uses")).isEqualTo(2);

        MvcResult filtered = getAuth(base(familyId) + "/suggestions?q=li", owner);
        assertThat((List<String>) json(filtered, "$[*].name")).containsExactly("Limones");

        // Lo que ya está pendiente en la lista no se sugiere.
        MvcResult forList = getAuth(base(familyId) + "/suggestions?listId=" + super1, owner);
        assertThat((List<String>) json(forList, "$[*].name")).containsExactly("Leche");
    }

    @Test
    void reorderAndMoveBetweenLists() throws Exception {
        String owner = verifiedUser("Mari");
        String familyId = createFamilyAs(owner, "Familia Orden");
        String super1 = createList(owner, familyId, "Super");
        String farmacia = createList(owner, familyId, "Farmacia");
        String a = addItem(owner, familyId, super1, "{\"name\":\"A\"}");
        String b = addItem(owner, familyId, super1, "{\"name\":\"B\"}");
        String c = addItem(owner, familyId, super1, "{\"name\":\"Paracetamol\"}");

        MvcResult reordered = postJson(base(familyId) + "/lists/" + super1 + "/items/reorder",
                "{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(c, a, b), owner);
        assertThat((List<String>) json(reordered, "$[*].name")).containsExactly("Paracetamol", "A", "B");
        assertThat(status(postJson(base(familyId) + "/lists/" + super1 + "/items/reorder",
                "{\"ids\":[\"%s\"]}".formatted(a), owner))).isEqualTo(400);

        MvcResult moved = putJsonAs(base(familyId) + "/lists/" + super1 + "/items/" + c,
                "{\"name\":\"Paracetamol\",\"listId\":\"%s\"}".formatted(farmacia), owner);
        assertThat((String) json(moved, "$.listId")).isEqualTo(farmacia);
        assertThat((List<String>) json(getAuth(base(familyId) + "/lists/" + farmacia + "/items", owner),
                "$[*].name")).containsExactly("Paracetamol");

        MvcResult archived = patchJsonAs(base(familyId) + "/lists/" + farmacia, "{\"archived\":true}", owner);
        assertThat((Boolean) json(archived, "$.archived")).isTrue();
        assertThat((List<String>) json(getAuth(base(familyId) + "/lists", owner), "$[*].name"))
                .containsExactly("Super");
    }

    @Test
    void outsidersCannotSeeOrTouchTheLists() throws Exception {
        String owner = verifiedUser("Dueño");
        String familyId = createFamilyAs(owner, "Familia Privada");
        String listId = createList(owner, familyId, "Super");
        String outsider = verifiedUser("Extraño");
        assertThat(status(getAuth(base(familyId) + "/lists", outsider))).isEqualTo(404);
        assertThat(status(postJson(base(familyId) + "/lists/" + listId + "/items", "{\"name\":\"X\"}", outsider)))
                .isEqualTo(404);
        // Ni siquiera desde su propia familia puede tocar listas ajenas por id.
        String otherFamily = createFamilyAs(outsider, "Otra");
        assertThat(status(getAuth(base(otherFamily) + "/lists/" + listId + "/items", outsider))).isEqualTo(404);
    }

    @Test
    void changesFeedReplaysWhatHappenedSinceACursor() throws Exception {
        String owner = verifiedUser("Rosa");
        String familyId = createFamilyAs(owner, "Familia Cambios");
        long cursor = ((Number) json(getAuth("/api/v1/families/" + familyId + "/changes/cursor", owner),
                "$.cursor")).longValue();

        String listId = createList(owner, familyId, "Super");
        String item = addItem(owner, familyId, listId, "{\"name\":\"Café\"}");
        deleteAs(base(familyId) + "/lists/" + listId + "/items/" + item, owner);

        MvcResult page = getAuth("/api/v1/families/" + familyId + "/changes?since=" + cursor, owner);
        assertThat(status(page)).as(body(page)).isEqualTo(200);
        List<String> types = json(page, "$.changes[*].type");
        assertThat(types).containsExactly("shopping.list.created", "shopping.item.created", "shopping.list.updated",
                "shopping.item.deleted", "shopping.list.updated");
        Map<String, Object> created = json(page, "$.changes[1]");
        assertThat(created.get("entityId")).isEqualTo(item);
        assertThat(created.get("actor")).isEqualTo(meId(owner));
        assertThat(((Map<?, ?>) created.get("payload")).get("name")).isEqualTo("Café");
        assertThat((Boolean) json(page, "$.hasMore")).isFalse();

        // Paginado: de dos en dos.
        MvcResult first = getAuth("/api/v1/families/" + familyId + "/changes?limit=2&since=" + cursor, owner);
        assertThat((Boolean) json(first, "$.hasMore")).isTrue();
        long next = ((Number) json(first, "$.cursor")).longValue();
        MvcResult second = getAuth("/api/v1/families/" + familyId + "/changes?limit=2&since=" + next, owner);
        assertThat((List<String>) json(second, "$.changes[*].type"))
                .containsExactly("shopping.list.updated", "shopping.item.deleted");

        String outsider = verifiedUser("Fisgón");
        assertThat(status(getAuth("/api/v1/families/" + familyId + "/changes", outsider))).isEqualTo(404);
    }
}
