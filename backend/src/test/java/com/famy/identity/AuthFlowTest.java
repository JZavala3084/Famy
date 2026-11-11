package com.famy.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.famy.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void registerVerifyLoginAndReadProfile() throws Exception {
        String email = uniqueEmail("ana");
        register(email, "Ana");
        assertThat(outbox.to(email)).hasSize(1);

        // Se puede iniciar sesión antes de verificar, pero el perfil indica que falta.
        String token = json(login(email, PASSWORD), "$.accessToken");
        MvcResult me = getAuth("/api/v1/me", token);
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat((Boolean) json(me, "$.emailVerified")).isFalse();
        assertThat((String) json(me, "$.currency")).isEqualTo("USD");
        assertThat((String) json(me, "$.timezone")).isEqualTo("America/Mexico_City");

        verify(email);
        assertThat((Boolean) json(getAuth("/api/v1/me", token), "$.emailVerified")).isTrue();

        // El token del enlace es de un solo uso.
        String used = outbox.lastToken(email, "/verify-email");
        assertThat(postJson("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(used)).getResponse()
                .getStatus()).isEqualTo(400);
    }

    @Test
    void passwordsAreStoredWithArgon2id() throws Exception {
        String email = uniqueEmail("hash");
        register(email, "Hash");
        String hash = jdbc.sql("select password_hash from app_user where email = :e").param("e", email)
                .query(String.class).single();
        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=65536,t=3,p=1$");
    }

    @Test
    void registerDoesNotRevealExistingAccounts() throws Exception {
        String email = uniqueEmail("dup");
        register(email, "Dup");
        verify(email);
        MvcResult again = postJson("/api/v1/auth/register", """
                {"email":"%s","password":"otra-clave-larga-1","displayName":"Otro"}""".formatted(email));
        assertThat(again.getResponse().getStatus()).isEqualTo(202);
        assertThat(outbox.to(email)).last().extracting(m -> m.subject()).isEqualTo("Ya tienes una cuenta en Famy");
    }

    @Test
    void rejectsWeakPasswordsAndInvalidInput() throws Exception {
        MvcResult weak = postJson("/api/v1/auth/register", """
                {"email":"%s","password":"password123","displayName":"X"}""".formatted(uniqueEmail("weak")));
        assertThat(weak.getResponse().getStatus()).isEqualTo(400);
        assertThat((String) json(weak, "$.code")).isEqualTo("weak_password");
        assertThat(weak.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        MvcResult invalid = postJson("/api/v1/auth/register", """
                {"email":"no-es-correo","password":"x","displayName":""}""");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat((String) json(invalid, "$.code")).isEqualTo("validation_error");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = uniqueEmail("luis");
        register(email, "Luis");
        MvcResult wrong = login(email, "no-es-la-clave");
        MvcResult unknown = login(uniqueEmail("nadie"), "no-es-la-clave");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void locksLoginAfterFiveFailures() throws Exception {
        String email = uniqueEmail("lock");
        register(email, "Lock");
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "mala-clave-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesTheWholeChain() throws Exception {
        String email = uniqueEmail("rot");
        register(email, "Rot");
        String first = json(login(email, PASSWORD), "$.refreshToken");

        MvcResult r1 = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(first));
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        String second = json(r1, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);

        // Reutilizar el token ya rotado: 401 y se revoca la cadena (el token nuevo también deja de servir).
        assertThat(postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(first)).getResponse()
                .getStatus()).isEqualTo(401);
        assertThat(postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(second)).getResponse()
                .getStatus()).isEqualTo(401);

        List<String> actions = jdbc.sql("""
                select a.action from audit_log a join app_user u on u.id = a.user_id where u.email = :e""")
                .param("e", email).query(String.class).list();
        assertThat(actions).contains("auth.register", "auth.login", "auth.refresh_reuse_detected");
    }

    @Test
    void webClientsGetRefreshTokenOnlyInHttpOnlyCookie() throws Exception {
        String email = uniqueEmail("web");
        register(email, "Web");
        MvcResult login = perform(post("/api/v1/auth/login").header("X-Famy-Client", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getContentAsString()).doesNotContain("refreshToken");
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("famy_rt=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        Cookie cookie = login.getResponse().getCookie("famy_rt");

        // Sin la cabecera personalizada la cookie no se acepta (defensa CSRF).
        assertThat(perform(post("/api/v1/auth/refresh").cookie(cookie)).getResponse().getStatus()).isEqualTo(401);

        MvcResult refreshed = perform(post("/api/v1/auth/refresh").cookie(cookie).header("X-Famy-Client", "web"));
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(refreshed, "$.accessToken")).isNotBlank();
    }

    @Test
    void logoutAllRevokesEverySession() throws Exception {
        String email = uniqueEmail("out");
        register(email, "Out");
        MvcResult a = login(email, PASSWORD);
        MvcResult b = login(email, PASSWORD);
        assertThat(perform(post("/api/v1/auth/logout-all")
                .header("Authorization", "Bearer " + json(a, "$.accessToken"))).getResponse().getStatus())
                .isEqualTo(204);
        assertThat(postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}"
                .formatted((String) json(b, "$.refreshToken"))).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordResetChangesPasswordAndClosesSessions() throws Exception {
        String email = uniqueEmail("reset");
        register(email, "Reset");
        String oldRefresh = json(login(email, PASSWORD), "$.refreshToken");

        assertThat(postJson("/api/v1/auth/password/forgot", "{\"email\":\"%s\"}".formatted(email)).getResponse()
                .getStatus()).isEqualTo(202);
        assertThat(postJson("/api/v1/auth/password/forgot", "{\"email\":\"%s\"}".formatted(uniqueEmail("x")))
                .getResponse().getStatus()).isEqualTo(202);
        String token = outbox.lastToken(email, "/reset-password");

        assertThat(postJson("/api/v1/auth/password/reset", """
                {"token":"%s","newPassword":"nueva-clave-segura"}""".formatted(token)).getResponse().getStatus())
                .isEqualTo(204);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, "nueva-clave-segura").getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(oldRefresh))
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void updatesProfileAndCurrency() throws Exception {
        String token = verifiedUser("Perfil");
        MvcResult r = perform(patch("/api/v1/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"mxn\",\"color\":\"#1E6B5E\",\"birthday\":\"1990-05-04\"}"));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(r, "$.currency")).isEqualTo("MXN");
        assertThat((String) json(r, "$.color")).isEqualTo("#1E6B5E");

        MvcResult bad = perform(patch("/api/v1/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"XYZ\"}"));
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void googleLoginCreatesAccountAndLinksExistingOne() throws Exception {
        String email = uniqueEmail("goo");
        MvcResult created = postJson("/api/v1/auth/google",
                "{\"idToken\":\"google|sub-%s|%s|true|Goo\"}".formatted(email.hashCode(), email));
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        MvcResult me = getAuth("/api/v1/me", json(created, "$.accessToken"));
        assertThat((Boolean) json(me, "$.emailVerified")).isTrue();
        assertThat((Boolean) json(me, "$.hasPassword")).isFalse();
        assertThat((List<String>) json(me, "$.linkedProviders")).containsExactly("GOOGLE");

        // Cuenta con correo NO verificado: Google la toma, se borra la contraseña (pre-account hijacking).
        String victim = uniqueEmail("vic");
        register(victim, "Intruso");
        MvcResult linked = postJson("/api/v1/auth/google",
                "{\"idToken\":\"google|sub-v-%s|%s|true|Victima\"}".formatted(victim.hashCode(), victim));
        assertThat(linked.getResponse().getStatus()).isEqualTo(200);
        assertThat(login(victim, PASSWORD).getResponse().getStatus()).isEqualTo(401);

        assertThat(postJson("/api/v1/auth/google", "{\"idToken\":\"falso\"}").getResponse().getStatus())
                .isEqualTo(401);
        assertThat(postJson("/api/v1/auth/google",
                "{\"idToken\":\"google|s|%s|false|N\"}".formatted(uniqueEmail("nv"))).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        assertThat(perform(get("/api/v1/me")).getResponse().getStatus()).isEqualTo(401);
        assertThat(perform(get("/api/v1/me").header("Authorization", "Bearer abc.def.ghi")).getResponse()
                .getStatus()).isEqualTo(401);
        MvcResult health = perform(get("/actuator/health"));
        assertThat(health.getResponse().getStatus()).isEqualTo(200);
        assertThat(health.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(health.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(perform(get("/v3/api-docs")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void rateLimitsLoginPerIp() throws Exception {
        int last = 0;
        for (int i = 0; i < 11; i++) {
            last = mvc.perform(post("/api/v1/auth/login").with(r -> {
                r.setRemoteAddr("192.0.2.77");
                return r;
            }).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"x\"}".formatted(uniqueEmail("ip"))))
                    .andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
    }
}
