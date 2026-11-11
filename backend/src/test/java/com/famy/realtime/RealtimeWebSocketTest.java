package com.famy.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.famy.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** WebSocket real (puerto aleatorio) + API por MockMvc en el mismo contexto. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeWebSocketTest extends IntegrationTest {

    @LocalServerPort
    int port;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void stop() {
        client.stop();
    }

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (accessToken != null) {
            connect.add("Authorization", "Bearer " + accessToken);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> subscribe(StompSession session, String familyId) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/families/" + familyId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return received;
    }

    @Test
    void membersReceiveChangesLive() throws Exception {
        String owner = verifiedUser("Ana");
        String familyId = createFamilyAs(owner, "Familia En Vivo");
        String luis = joinAs(owner, familyId, "Luis", "MEMBER");

        StompSession session = connect(luis);
        BlockingQueue<String> events = subscribe(session, familyId);
        Thread.sleep(300); // que el SUBSCRIBE llegue antes del cambio

        String listId = json(postJson("/api/v1/families/" + familyId + "/shopping/lists", "{\"name\":\"Super\"}",
                owner), "$.id");
        String event = events.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("shopping.list.created");
        assertThat((String) JsonPath.read(event, "$.entityId")).isEqualTo(listId);
        assertThat((String) JsonPath.read(event, "$.payload.name")).isEqualTo("Super");
        assertThat((String) JsonPath.read(event, "$.actor")).isEqualTo(meId(owner));
        assertThat(((Number) JsonPath.read(event, "$.id")).longValue()).isPositive();
        assertThat((String) JsonPath.read(event, "$.occurredAt")).endsWith("Z");
    }

    @Test
    void connectWithoutValidTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("no-es-un-jwt")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void cannotSubscribeToAnotherFamily() throws Exception {
        String owner = verifiedUser("Dueña");
        String familyId = createFamilyAs(owner, "Familia Ajena");
        String outsider = verifiedUser("Curioso");

        StompSession session = connect(outsider);
        BlockingQueue<String> events = subscribe(session, familyId);
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
        postJson("/api/v1/families/" + familyId + "/shopping/lists", "{\"name\":\"Secreta\"}", owner);
        assertThat(events.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void removedMemberIsDisconnected() throws Exception {
        String owner = verifiedUser("Jefa");
        String familyId = createFamilyAs(owner, "Familia Expulsión");
        String member = joinAs(owner, familyId, "Primo", "MEMBER");

        StompSession session = connect(member);
        subscribe(session, familyId);
        Thread.sleep(300);
        assertThat(session.isConnected()).isTrue();

        assertThat(status(deleteAs("/api/v1/families/" + familyId + "/members/" + meId(member), owner)))
                .isEqualTo(204);
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
    }
}
