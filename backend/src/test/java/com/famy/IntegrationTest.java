package com.famy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Base de pruebas de integración: API completa contra PostgreSQL real (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestDoubles.class})
public abstract class IntegrationTest {

    public static final String PASSWORD = "caballo-bateria-grapa";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestDoubles.Outbox outbox;

    /** Cada petición sale de una IP distinta para no chocar con el rate limiting por IP entre pruebas. */
    protected static RequestPostProcessor randomIp() {
        return req -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            req.setRemoteAddr("10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255));
            return req;
        };
    }

    protected static String uniqueEmail(String name) {
        return name + "+" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected MvcResult perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(randomIp())).andReturn();
    }

    protected MvcResult postJson(String url, String json) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected MvcResult postJson(String url, String json, String accessToken) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)
                .header("Authorization", "Bearer " + accessToken));
    }

    protected MvcResult getAuth(String url, String accessToken) throws Exception {
        return perform(get(url).header("Authorization", "Bearer " + accessToken));
    }

    protected static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    protected void register(String email, String name) throws Exception {
        MvcResult r = postJson("/api/v1/auth/register", """
                {"email":"%s","password":"%s","displayName":"%s","timezone":"America/Mexico_City"}
                """.formatted(email, PASSWORD, name));
        if (r.getResponse().getStatus() != 202) {
            throw new AssertionError("registro falló: " + r.getResponse().getContentAsString());
        }
    }

    protected void verify(String email) throws Exception {
        String token = outbox.lastToken(email, "/verify-email");
        MvcResult r = postJson("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(token));
        if (r.getResponse().getStatus() != 204) {
            throw new AssertionError("verificación falló: " + r.getResponse().getContentAsString());
        }
    }

    protected MvcResult login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email":"%s","password":"%s","deviceName":"test"}""".formatted(email, password));
    }

    /** Registra, verifica e inicia sesión; devuelve el access token. */
    protected String verifiedUser(String name) throws Exception {
        String email = uniqueEmail(name.toLowerCase());
        register(email, name);
        verify(email);
        return json(login(email, PASSWORD), "$.accessToken");
    }

    protected String accessTokenFor(String email) throws Exception {
        return json(login(email, PASSWORD), "$.accessToken");
    }
}
