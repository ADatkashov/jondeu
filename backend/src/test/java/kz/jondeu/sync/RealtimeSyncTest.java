package kz.jondeu.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kz.jondeu.support.Api;
import kz.jondeu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Verifies the core promise: a change by one participant reaches every other connected client without polling. */
@IntegrationTest
class RealtimeSyncTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @LocalServerPort int port;

    static class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        final ObjectMapper om;
        WebSocketSession session;
        Client(ObjectMapper om) { this.om = om; }
        @Override protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception { inbox.add(om.readTree(m.getPayload())); }

        JsonNode next(String kind, long seconds) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < end) {
                JsonNode n = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (n != null && kind.equals(n.path("kind").asText())) return n;
            }
            throw new AssertionError("No '" + kind + "' message within " + seconds + "s");
        }

        JsonNode nextEvent(String type, long seconds) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < end) {
                JsonNode n = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (n != null && "project.event".equals(n.path("kind").asText()) && type.equals(n.path("type").asText())) return n;
            }
            throw new AssertionError("No event " + type + " within " + seconds + "s");
        }
    }

    private Client connect(String token) throws Exception {
        Client c = new Client(om);
        c.session = new StandardWebSocketClient().execute(c, null, URI.create("ws://localhost:" + port + "/ws?token=" + token)).get(10, TimeUnit.SECONDS);
        return c;
    }

    private void subscribe(Client c, UUID pid) throws Exception {
        c.session.sendMessage(new TextMessage(om.writeValueAsString(Map.of("action", "subscribe", "projectId", pid.toString()))));
    }

    @Test
    void handshakeWithoutValidTokenIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> connect("not-a-token"));
    }

    @Test
    void changesPropagateToAllParticipantsAndOnlyToThem() throws Exception {
        Api api = new Api(mvc, om);
        var customer = api.register("CUSTOMER", "Заказчик WS");
        var pro = api.register("CONTRACTOR", "Подрядчик WS");
        var stranger = api.register("CUSTOMER", "Посторонний");
        UUID pid = customer.post("/api/v1/projects", Map.of("name", "Realtime", "propertyType", "APARTMENT", "areaM2", 64, "address", "Алматы", "category", "APARTMENT_RENOVATION", "description", "x", "budgetMax", 12000000)).id();
        customer.post("/api/v1/projects/" + pid + "/submit");
        var prop = pro.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 12455000, "durationDays", 90));
        customer.post("/api/v1/proposals/" + prop.id() + "/accept");

        Client web = connect(customer.access);      // the web dashboard
        Client mobilePro = connect(pro.access);     // the contractor's mobile app
        Client intruder = connect(stranger.access);
        for (Client c : List.of(web, mobilePro, intruder)) c.next("hello", 5);
        subscribe(web, pid); subscribe(mobilePro, pid); subscribe(intruder, pid);
        web.next("subscribed", 5); mobilePro.next("subscribed", 5);
        assertThat(intruder.next("error", 5).path("code").asText()).isEqualTo("FORBIDDEN");

        // contractor creates + submits an estimate in the app -> the web dashboard must see it immediately
        var est = pro.post("/api/v1/projects/" + pid + "/estimates", Map.of("items", List.of(Map.of("title", "Всё", "quantity", 1, "unit", "компл", "unitPrice", 12455000, "kind", "LABOR"))));
        assertThat(web.nextEvent("ESTIMATE_CREATED", 5).path("entityId").asText()).isEqualTo(est.id().toString());
        pro.post("/api/v1/estimates/" + est.id() + "/submit");
        assertThat(web.nextEvent("ESTIMATE_SUBMITTED", 5).path("projectId").asText()).isEqualTo(pid.toString());
        assertThat(web.next("notification", 5).path("type").asText()).isEqualTo("ESTIMATE_SUBMITTED"); // push-style notification to the customer

        // customer approves on the web -> the contractor's app learns about it, stages appear
        customer.post("/api/v1/estimates/" + est.id() + "/approve");
        assertThat(mobilePro.nextEvent("ESTIMATE_APPROVED", 5).path("message").asText()).contains("12455000");
        mobilePro.nextEvent("STAGES_CREATED", 5);

        // contractor moves a stage, customer pays -> each side is told
        UUID stage = UUID.fromString(customer.get("/api/v1/projects/" + pid + "/stages").body().get(0).path("id").asText());
        pro.patch("/api/v1/stages/" + stage, Map.of("progress", 40));
        assertThat(web.nextEvent("STAGE_UPDATED", 5).path("entityId").asText()).isEqualTo(stage.toString());
        var pay = customer.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "ADVANCE", "method", "CARD", "amount", 1000000), "Idempotency-Key", UUID.randomUUID().toString());
        mobilePro.nextEvent("PAYMENT_CREATED", 5);
        customer.post("/api/v1/payments/" + pay.id() + "/sandbox-resolve", Map.of("success", true));
        var received = mobilePro.nextEvent("PAYMENT_RECEIVED", 5);
        assertThat(received.path("seq").asLong()).isPositive();

        // chat messages are delivered in real time and tracked as read
        var conv = customer.get("/api/v1/projects/" + pid + "/conversation");
        pro.get("/api/v1/projects/" + pid + "/conversation");
        assertThat(customer.post("/api/v1/conversations/" + conv.id() + "/messages", Map.of("body", "Здравствуйте! Начинаем завтра в 10:00")).status()).isEqualTo(200);
        var chat = mobilePro.next("chat.message", 5);
        assertThat(chat.at("/message/body").asText()).contains("Начинаем завтра");
        assertThat(chat.at("/message/status").asText()).isEqualTo("SENT");
        assertThat(pro.get("/api/v1/conversations").body().get(0).path("unread").asInt()).isEqualTo(1);
        pro.post("/api/v1/conversations/" + conv.id() + "/read");
        assertThat(web.next("chat.read", 5).path("userId").asText()).isEqualTo(pro.id.toString());
        assertThat(pro.get("/api/v1/conversations").body().get(0).path("unread").asInt()).isZero();
        assertThat(customer.get("/api/v1/conversations/" + conv.id() + "/messages").body().get(0).path("status").asText()).isEqualTo("READ");
        assertThat(stranger.get("/api/v1/conversations/" + conv.id() + "/messages").status()).isEqualTo(404);

        // the stranger saw nothing of the above
        assertThat(intruder.inbox.stream().filter(n -> "project.event".equals(n.path("kind").asText()) || "chat.message".equals(n.path("kind").asText()))).isEmpty();

        // offline catch-up: events after the last seq a client saw
        long last = received.path("seq").asLong();
        pro.patch("/api/v1/stages/" + stage, Map.of("progress", 60));
        JsonNode missed = customer.get("/api/v1/projects/" + pid + "/events?after=" + last).body();
        assertThat(missed.size()).isGreaterThanOrEqualTo(1);
        assertThat(missed.get(missed.size() - 1).path("type").asText()).isEqualTo("STAGE_UPDATED");

        for (Client c : List.of(web, mobilePro, intruder)) c.session.close(CloseStatus.NORMAL);
    }
}
