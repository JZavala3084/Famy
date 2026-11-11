package com.famy.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.famy.IntegrationTest;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class FamilyFlowTest extends IntegrationTest {

    private String createFamily(String token, String name) throws Exception {
        MvcResult r = postJson("/api/v1/families", "{\"name\":\"%s\",\"timezone\":\"America/Mexico_City\"}"
                .formatted(name), token);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r, "$.id");
    }

    private MvcResult invite(String token, String familyId, String body) throws Exception {
        return postJson("/api/v1/families/" + familyId + "/invitations", body, token);
    }

    private String userId(String token) throws Exception {
        return json(getAuth("/api/v1/me", token), "$.id");
    }

    private MvcResult patchJson(String url, String body, String token) throws Exception {
        return perform(patch(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private MvcResult deleteAuth(String url, String token) throws Exception {
        return perform(delete(url).header("Authorization", "Bearer " + token));
    }

    @Test
    void createFamilyRequiresVerifiedEmail() throws Exception {
        String email = uniqueEmail("sinverificar");
        register(email, "Sin");
        String token = accessTokenFor(email);
        MvcResult r = postJson("/api/v1/families", "{\"name\":\"Familia\"}", token);
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) json(r, "$.code")).isEqualTo("email_not_verified");
    }

    @Test
    void ownerCreatesFamilyAndTokenCarriesFamilyClaims() throws Exception {
        String email = uniqueEmail("jose");
        register(email, "Jose");
        verify(email);
        MvcResult login = login(email, PASSWORD);
        String familyId = createFamily(json(login, "$.accessToken"), "Familia Zavala");

        MvcResult refreshed = postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted((String) json(login, "$.refreshToken")));
        var claims = SignedJWT.parse((String) json(refreshed, "$.accessToken")).getJWTClaimsSet();
        assertThat(claims.getStringClaim("fid")).isEqualTo(familyId);
        assertThat(claims.getStringClaim("role")).isEqualTo("OWNER");
        assertThat(SignedJWT.parse((String) json(refreshed, "$.accessToken")).getHeader().getAlgorithm().getName())
                .isEqualTo("ES256");

        MvcResult mine = getAuth("/api/v1/families/me", json(refreshed, "$.accessToken"));
        assertThat((String) json(mine, "$[0].name")).isEqualTo("Familia Zavala");
        assertThat((String) json(mine, "$[0].currency")).isEqualTo("USD");
        assertThat((String) json(mine, "$[0].myRole")).isEqualTo("OWNER");

        // MVP: una sola familia activa por usuario.
        assertThat(postJson("/api/v1/families", "{\"name\":\"Otra\"}", json(refreshed, "$.accessToken"))
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void joinWithCodeRequiresApprovalAndOwnerApproves() throws Exception {
        String owner = verifiedUser("Owner");
        String familyId = createFamily(owner, "Los Pérez");
        MvcResult inv = invite(owner, familyId, "{}");
        assertThat(inv.getResponse().getStatus()).isEqualTo(201);
        String code = json(inv, "$.code");
        assertThat(code).matches("FAM-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        assertThat((Boolean) json(inv, "$.requiresApproval")).isTrue();
        assertThat((String) json(inv, "$.link")).startsWith("http://localhost:4200/join/");

        String ana = verifiedUser("Ana");
        MvcResult preview = getAuth("/api/v1/invitations/" + code.toLowerCase(), ana);
        assertThat(preview.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(preview, "$.familyName")).isEqualTo("Los Pérez");

        MvcResult accept = postJson("/api/v1/invitations/" + code + "/accept", "", ana);
        assertThat(accept.getResponse().getStatus()).isEqualTo(202);
        assertThat((String) json(accept, "$.status")).isEqualTo("PENDING_APPROVAL");
        assertThat(getAuth("/api/v1/families/" + familyId, ana).getResponse().getStatus()).isEqualTo(404);

        MvcResult pending = getAuth("/api/v1/families/" + familyId + "/join-requests", owner);
        List<String> ids = json(pending, "$[*].id");
        assertThat(ids).containsExactly((String) json(accept, "$.requestId"));
        assertThat(postJson("/api/v1/families/" + familyId + "/join-requests/" + ids.get(0) + "/approve", "", owner)
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult family = getAuth("/api/v1/families/" + familyId, ana);
        assertThat(family.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(family, "$.myRole")).isEqualTo("MEMBER");
        assertThat((List<?>) json(family, "$.members")).hasSize(2);
    }

    @Test
    void emailInvitationJoinsDirectlyButOnlyForThatEmail() throws Exception {
        String owner = verifiedUser("Owner");
        String familyId = createFamily(owner, "Familia Correo");
        String luisEmail = uniqueEmail("luis");
        register(luisEmail, "Luis");
        verify(luisEmail);
        MvcResult inv = invite(owner, familyId, "{\"email\":\"%s\",\"role\":\"CHILD\"}".formatted(luisEmail));
        assertThat((Boolean) json(inv, "$.requiresApproval")).isFalse();
        String link = json(inv, "$.link");
        String token = link.substring(link.lastIndexOf('/') + 1);

        String intruder = verifiedUser("Intruso");
        assertThat(postJson("/api/v1/invitations/" + token + "/accept", "", intruder).getResponse().getStatus())
                .isEqualTo(404);

        MvcResult accept = postJson("/api/v1/invitations/" + token + "/accept", "", accessTokenFor(luisEmail));
        assertThat(accept.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(accept, "$.status")).isEqualTo("JOINED");
        MvcResult family = getAuth("/api/v1/families/" + familyId, accessTokenFor(luisEmail));
        assertThat((String) json(family, "$.myRole")).isEqualTo("CHILD");
    }

    @Test
    void rolesAndPermissionsFollowTheMatrix() throws Exception {
        String owner = verifiedUser("Owner");
        String familyId = createFamily(owner, "Familia Roles");
        String admin = joinDirectly(owner, familyId, "Admin", "ADMIN");
        String member = joinDirectly(owner, familyId, "Member", "MEMBER");
        String child = joinDirectly(owner, familyId, "Child", "CHILD");
        String ownerId = userId(owner);
        String adminId = userId(admin);
        String memberId = userId(member);
        String childId = userId(child);
        String base = "/api/v1/families/" + familyId;

        // MEMBER y CHILD no invitan (salvo que la familia lo permita a MEMBER).
        assertThat(invite(member, familyId, "{}").getResponse().getStatus()).isEqualTo(403);
        assertThat(invite(child, familyId, "{}").getResponse().getStatus()).isEqualTo(403);
        // ADMIN no puede invitar administradores.
        assertThat(invite(admin, familyId, "{\"role\":\"ADMIN\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(patchJson(base, "{\"membersCanInvite\":true}", owner).getResponse().getStatus()).isEqualTo(200);
        assertThat(invite(member, familyId, "{}").getResponse().getStatus()).isEqualTo(201);

        // ADMIN cambia MEMBER <-> CHILD, pero no asciende a ADMIN ni toca al OWNER.
        assertThat(patchJson(base + "/members/" + childId, "{\"role\":\"MEMBER\"}", admin).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(patchJson(base + "/members/" + memberId, "{\"role\":\"ADMIN\"}", admin).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(patchJson(base + "/members/" + ownerId, "{\"role\":\"MEMBER\"}", admin).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(patchJson(base + "/members/" + adminId, "{\"role\":\"OWNER\"}", owner).getResponse().getStatus())
                .isEqualTo(400);
        // ADMIN solo puede cambiar la moneda de la familia.
        assertThat(patchJson(base, "{\"name\":\"Otro\"}", admin).getResponse().getStatus()).isEqualTo(403);
        assertThat((String) json(patchJson(base, "{\"currency\":\"MXN\"}", admin), "$.currency")).isEqualTo("MXN");

        // MEMBER no expulsa; ADMIN sí a un MEMBER.
        assertThat(deleteAuth(base + "/members/" + childId, member).getResponse().getStatus()).isEqualTo(403);
        assertThat(deleteAuth(base + "/members/" + childId, admin).getResponse().getStatus()).isEqualTo(204);
        assertThat(getAuth(base, child).getResponse().getStatus()).isEqualTo(404);

        // El OWNER no puede salir sin transferir; tras transferir queda como ADMIN y puede salir.
        assertThat(deleteAuth(base + "/members/" + ownerId, owner).getResponse().getStatus()).isEqualTo(409);
        MvcResult transfer = postJson(base + "/transfer-ownership", "{\"newOwnerId\":\"%s\"}".formatted(adminId), owner);
        assertThat(transfer.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(transfer, "$.ownerId")).isEqualTo(adminId);
        assertThat((String) json(transfer, "$.myRole")).isEqualTo("ADMIN");
        assertThat(deleteAuth(base + "/members/" + ownerId, owner).getResponse().getStatus()).isEqualTo(204);

        // Quien no es miembro no ve la familia (404, sin revelar que existe).
        assertThat(getAuth(base, verifiedUser("Extra")).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void revokedOrGuessedCodesAreRejectedAndRateLimited() throws Exception {
        String owner = verifiedUser("Owner");
        String familyId = createFamily(owner, "Familia Códigos");
        MvcResult inv = invite(owner, familyId, "{}");
        String code = json(inv, "$.code");
        assertThat(deleteAuth("/api/v1/families/" + familyId + "/invitations/" + json(inv, "$.id"), owner)
                .getResponse().getStatus()).isEqualTo(204);

        String guesser = verifiedUser("Adivina");
        assertThat(getAuth("/api/v1/invitations/" + code, guesser).getResponse().getStatus()).isEqualTo(404);
        for (int i = 0; i < 4; i++) {
            assertThat(getAuth("/api/v1/invitations/FAM-AAAA-AAA" + (i + 2), guesser).getResponse().getStatus())
                    .isEqualTo(404);
        }
        assertThat(getAuth("/api/v1/invitations/FAM-BBBB-BBBB", guesser).getResponse().getStatus()).isEqualTo(429);
    }

    /** Crea un usuario verificado que entra con una invitación por correo con el rol dado. */
    private String joinDirectly(String ownerToken, String familyId, String name, String role) throws Exception {
        String email = uniqueEmail(name.toLowerCase());
        register(email, name);
        verify(email);
        MvcResult inv = invite(ownerToken, familyId, "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role));
        String code = json(inv, "$.code");
        String token = accessTokenFor(email);
        MvcResult accept = postJson("/api/v1/invitations/" + code + "/accept", "", token);
        assertThat(accept.getResponse().getStatus()).as(accept.getResponse().getContentAsString()).isEqualTo(200);
        return token;
    }
}
