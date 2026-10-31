package kz.jondeu.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import kz.jondeu.support.Api;
import kz.jondeu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end scenario from the product brief: customer registers -> ... -> review. All through the real HTTP layer and PostgreSQL. */
@IntegrationTest
class RenovationLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // 1x1 transparent PNG
    static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static BigDecimal bd(JsonNode n) { return new BigDecimal(n.asText()); }

    @Test
    void fullRenovationLifecycle() {
        Api api = new Api(mvc, om);

        // 1. customer & master register
        var customer = api.register("CUSTOMER", "Асель Нурланова");
        var master = api.register("CONTRACTOR", "Руслан Бригадир");
        assertThat(master.put("/api/v1/masters/me", Map.of("specialization", "Ремонт под ключ", "category", "APARTMENT_RENOVATION", "experienceYears", 9, "hourlyRate", 4500)).status()).isEqualTo(200);

        // 2. customer creates a project (64 m2 apartment) – validation first
        assertThat(customer.post("/api/v1/projects", Map.of("name", "", "areaM2", 64)).status()).isEqualTo(400);
        assertThat(customer.post("/api/v1/projects", Map.of("name", "X", "areaM2", -5, "propertyType", "APARTMENT")).status()).isEqualTo(400);
        var created = customer.post("/api/v1/projects", Map.of("name", "Квартира, Алматы 64 м²", "propertyType", "APARTMENT", "rooms", 3, "areaM2", 64, "address", "Алматы, ул. Абая 150",
            "category", "APARTMENT_RENOVATION", "description", "Капитальный ремонт 3-комнатной квартиры", "budgetMin", 10000000, "budgetMax", 14000000));
        assertThat(created.status()).isEqualTo(200);
        UUID pid = created.id();
        assertThat(created.str("status")).isEqualTo("DRAFT");

        // 3. submit request
        assertThat(customer.post("/api/v1/projects/" + pid + "/submit").str("status")).isEqualTo("SUBMITTED");

        // 4. contractor sees the open request, 5. submits a proposal
        assertThat(master.get("/api/v1/requests/open").body().at("/content").toString()).contains(pid.toString());
        assertThat(master.get("/api/v1/projects/" + pid).status()).isEqualTo(404); // not a member yet
        var proposal = master.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 12000000, "durationDays", 90, "message", "Сделаем за 3 месяца"));
        assertThat(proposal.status()).isEqualTo(200);
        assertThat(master.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 11000000, "durationDays", 80)).status()).isEqualTo(409); // duplicate
        assertThat(customer.get("/api/v1/projects/" + pid).str("status")).isEqualTo("PROPOSALS_RECEIVED");

        // 6. customer compares & accepts
        assertThat(customer.get("/api/v1/projects/" + pid + "/proposals").body().size()).isEqualTo(1);
        assertThat(master.post("/api/v1/proposals/" + proposal.id() + "/accept").status()).isEqualTo(404); // a bidder has no access to the project, existence is not leaked
        assertThat(customer.post("/api/v1/proposals/" + proposal.id() + "/accept").str("status")).isEqualTo("ACCEPTED");
        assertThat(master.get("/api/v1/projects/" + pid).str("status")).isEqualTo("CONTRACTOR_SELECTED");

        // 7. contractor builds the estimate: server computes the total 12 455 000
        List<Map<String, Object>> items = List.of(
            item("LABOR", "Демонтажные работы", 1, "компл", 800000), item("LABOR", "Электромонтаж", 1, "компл", 2500000),
            item("LABOR", "Сантехника", 1, "компл", 1800000), item("LABOR", "Штукатурка", 64, "м²", 25000),
            item("LABOR", "Покраска", 64, "м²", 18000), item("MATERIAL", "Материалы", 1, "компл", 3500000));
        Map<String, Object> body = new HashMap<>(Map.of("items", items, "additionalCosts", 1103000, "discount", 0, "taxRate", 0));
        var est = master.post("/api/v1/projects/" + pid + "/estimates", body);
        assertThat(est.status()).isEqualTo(200);
        assertThat(bd(est.body().path("total"))).isEqualByComparingTo("12455000.00");
        assertThat(bd(est.body().path("subtotal"))).isEqualByComparingTo("11352000.00");
        assertThat(est.str("status")).isEqualTo("DRAFT");
        UUID eid = est.id();
        // client-sent totals are ignored: the backend recomputes
        var tampered = master.put("/api/v1/estimates/" + eid, new HashMap<>(Map.of("items", items, "additionalCosts", 1103000, "total", 1)));
        assertThat(bd(tampered.body().path("total"))).isEqualByComparingTo("12455000.00");

        // 8. approval flow
        assertThat(customer.post("/api/v1/estimates/" + eid + "/approve").status()).isEqualTo(409); // not submitted yet
        assertThat(master.post("/api/v1/estimates/" + eid + "/submit").str("status")).isEqualTo("SUBMITTED");
        assertThat(master.put("/api/v1/estimates/" + eid, body).status()).isEqualTo(409); // locked while under review
        assertThat(master.post("/api/v1/estimates/" + eid + "/approve").status()).isEqualTo(403); // only the customer approves
        assertThat(customer.post("/api/v1/estimates/" + eid + "/approve").str("status")).isEqualTo("APPROVED");

        // approved estimate is immutable; revision creates a new version
        assertThat(master.put("/api/v1/estimates/" + eid, body).status()).isEqualTo(409);
        var rev = master.post("/api/v1/estimates/" + eid + "/revise");
        assertThat(rev.status()).isEqualTo(200);
        assertThat(rev.body().path("version").asInt()).isEqualTo(2);
        assertThat(rev.str("status")).isEqualTo("DRAFT");
        assertThat(master.post("/api/v1/estimates/" + eid + "/revise").status()).isEqualTo(409); // one open revision at a time
        assertThat(customer.get("/api/v1/estimates/" + eid).str("status")).isEqualTo("APPROVED");

        // 9. stages were created from the approved estimate; budgets sum exactly to the approved total
        var overview = customer.get("/api/v1/projects/" + pid + "/overview");
        assertThat(bd(overview.body().at("/finance/approvedBudget"))).isEqualByComparingTo("12455000.00");
        assertThat(overview.str("status")).isNull();
        assertThat(overview.at("/project/status").asText()).isEqualTo("IN_PROGRESS");
        JsonNode stages = customer.get("/api/v1/projects/" + pid + "/stages").body();
        assertThat(stages.size()).isEqualTo(11);
        BigDecimal budgetSum = BigDecimal.ZERO;
        for (JsonNode s : stages) budgetSum = budgetSum.add(bd(s.path("budget")));
        assertThat(budgetSum).isEqualByComparingTo("12455000.00");

        // 10. contractor updates stage progress; dependencies are enforced; only the customer accepts completion
        UUID s1 = UUID.fromString(stages.get(0).path("id").asText());
        UUID s2 = UUID.fromString(stages.get(1).path("id").asText());
        assertThat(master.patch("/api/v1/stages/" + s2, Map.of("progress", 10)).status()).isEqualTo(409); // blocked by stage 1
        assertThat(master.patch("/api/v1/stages/" + s1, Map.of("progress", 50)).str("status")).isEqualTo("IN_PROGRESS");
        assertThat(master.patch("/api/v1/stages/" + s1, Map.of("progress", 100)).str("status")).isEqualTo("UNDER_REVIEW");
        assertThat(master.patch("/api/v1/stages/" + s1, Map.of("status", "COMPLETED")).status()).isEqualTo(403);
        assertThat(customer.patch("/api/v1/stages/" + s1, Map.of("title", "hack")).status()).isEqualTo(403);
        assertThat(customer.patch("/api/v1/stages/" + s1, Map.of("status", "COMPLETED")).str("status")).isEqualTo("COMPLETED");
        // weighted progress: stage 1 has weight 3 of 100
        assertThat(bd(customer.get("/api/v1/projects/" + pid + "/overview").body().at("/progress/percent"))).isEqualByComparingTo("3.0");

        // 11. photos
        var photo = master.upload("/api/v1/projects/" + pid + "/media", "before.png", PNG, "image/png", Map.of("category", "BEFORE", "stageId", s1.toString(), "caption", "До начала"));
        assertThat(photo.status()).isEqualTo(200);
        assertThat(master.upload("/api/v1/projects/" + pid + "/media", "evil.png", "<script>alert(1)</script>".getBytes(), "image/png", Map.of("category", "DURING")).status()).isEqualTo(415);
        JsonNode photos = customer.get("/api/v1/projects/" + pid + "/media?category=BEFORE").body();
        assertThat(photos.size()).isEqualTo(1);
        assertThat(api.rawGet(photos.get(0).path("url").asText()).status()).isEqualTo(200); // signed URL works without a header
        assertThat(api.rawGet(photos.get(0).path("url").asText().replaceAll("sig=.{40}", "sig=" + "0".repeat(40))).status()).isEqualTo(403);

        // 12. payments: idempotency, reservation, verified callback, overpayment protection
        String key = UUID.randomUUID().toString();
        Map<String, Object> pay = Map.of("type", "ADVANCE", "method", "CARD", "amount", 3000000);
        var p1 = customer.postWithHeader("/api/v1/projects/" + pid + "/payments", pay, "Idempotency-Key", key);
        assertThat(p1.status()).isEqualTo(200);
        assertThat(p1.str("status")).isEqualTo("PENDING"); // never auto-succeeds
        assertThat(p1.body().path("sandbox").asBoolean()).isTrue();
        var replay = customer.postWithHeader("/api/v1/projects/" + pid + "/payments", pay, "Idempotency-Key", key);
        assertThat(replay.id()).isEqualTo(p1.id()); // same key -> same payment, no double charge
        assertThat(customer.post("/api/v1/projects/" + pid + "/payments", pay).status()).isEqualTo(400); // key is mandatory
        assertThat(master.postWithHeader("/api/v1/projects/" + pid + "/payments", pay, "Idempotency-Key", "k-" + UUID.randomUUID()).status()).isEqualTo(403); // contractor cannot pay
        var fin = customer.get("/api/v1/projects/" + pid + "/overview").body().at("/finance");
        assertThat(bd(fin.path("paid"))).isEqualByComparingTo("0");
        assertThat(bd(fin.path("reserved"))).isEqualByComparingTo("3000000");
        // forged gateway callback is rejected
        var forged = api.exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payments/callbacks/sandbox")
            .contentType("application/json").header("X-Sandbox-Signature", "deadbeef").content("{\"providerRef\":\"" + p1.str("providerRef") + "\",\"status\":\"SUCCESS\"}"));
        assertThat(forged.status()).isEqualTo(401);
        assertThat(customer.get("/api/v1/projects/" + pid + "/payments").body().get(0).path("status").asText()).isEqualTo("PENDING");
        // genuine (signed) confirmation
        assertThat(customer.post("/api/v1/payments/" + p1.id() + "/sandbox-resolve", Map.of("success", true)).str("status")).isEqualTo("PAID");
        assertThat(customer.post("/api/v1/payments/" + p1.id() + "/sandbox-resolve", Map.of("success", true)).str("status")).isEqualTo("PAID"); // replay-safe
        fin = customer.get("/api/v1/projects/" + pid + "/overview").body().at("/finance");
        assertThat(bd(fin.path("paid"))).isEqualByComparingTo("3000000");
        assertThat(bd(fin.path("remaining"))).isEqualByComparingTo("9455000");
        // cannot exceed outstanding amount
        assertThat(customer.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "STAGE", "method", "CASH", "amount", 9455000.01), "Idempotency-Key", "over-" + UUID.randomUUID()).status()).isEqualTo(422);

        // payment request by the contractor -> customer pays in cash -> contractor confirms receipt
        var req = master.post("/api/v1/projects/" + pid + "/payment-requests", Map.of("type", "STAGE", "amount", 1000000, "note", "Этап: демонтаж"));
        assertThat(req.str("status")).isEqualTo("REQUESTED");
        var paidReq = customer.post("/api/v1/payments/" + req.id() + "/pay", Map.of("method", "CASH"));
        assertThat(paidReq.str("status")).isEqualTo("PENDING");
        assertThat(customer.post("/api/v1/payments/" + req.id() + "/confirm").status()).isEqualTo(403); // payer cannot confirm own cash payment
        assertThat(master.post("/api/v1/payments/" + req.id() + "/confirm").str("status")).isEqualTo("PAID");

        // 13. contractor completes all stages (customer accepts each); project cannot be closed with unpaid balance
        assertThat(customer.post("/api/v1/projects/" + pid + "/complete").status()).isEqualTo(409);
        for (JsonNode s : customer.get("/api/v1/projects/" + pid + "/stages").body()) {
            String sid = s.path("id").asText();
            if (s.path("status").asText().equals("COMPLETED")) continue;
            assertThat(master.patch("/api/v1/stages/" + sid, Map.of("progress", 100)).status()).isEqualTo(200);
            assertThat(customer.patch("/api/v1/stages/" + sid, Map.of("status", "COMPLETED")).str("status")).isEqualTo("COMPLETED");
        }
        assertThat(customer.post("/api/v1/projects/" + pid + "/complete").status()).isEqualTo(409); // outstanding balance
        var rest = customer.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "FINAL", "method", "BANK_TRANSFER", "amount", 8455000), "Idempotency-Key", "final-" + UUID.randomUUID());
        assertThat(rest.status()).isEqualTo(200);
        assertThat(master.post("/api/v1/payments/" + rest.id() + "/confirm").str("status")).isEqualTo("PAID");
        fin = customer.get("/api/v1/projects/" + pid + "/overview").body().at("/finance");
        assertThat(bd(fin.path("remaining"))).isEqualByComparingTo("0");
        assertThat(bd(fin.path("utilizationPercent"))).isEqualByComparingTo("100.0");

        // 14-15. final handover by the customer
        assertThat(master.post("/api/v1/projects/" + pid + "/complete").status()).isEqualTo(403);
        assertThat(customer.post("/api/v1/projects/" + pid + "/complete").str("status")).isEqualTo("COMPLETED");
        assertThat(bd(customer.get("/api/v1/projects/" + pid + "/overview").body().at("/progress/percent"))).isEqualByComparingTo("100.0");

        // 16. review: once only, only for the completed project's owner
        assertThat(master.post("/api/v1/projects/" + pid + "/reviews", Map.of("rating", 5)).status()).isEqualTo(404);
        assertThat(customer.post("/api/v1/projects/" + pid + "/reviews", Map.of("rating", 6)).status()).isEqualTo(400);
        assertThat(customer.post("/api/v1/projects/" + pid + "/reviews", Map.of("rating", 5, "body", "Отличная работа")).status()).isEqualTo(200);
        assertThat(customer.post("/api/v1/projects/" + pid + "/reviews", Map.of("rating", 4)).status()).isEqualTo(409);
        var profile = customer.get("/api/v1/masters/" + master.id);
        assertThat(profile.status()).isEqualTo(200);
        assertThat(bd(profile.body().path("rating"))).isEqualByComparingTo("5.00");
        assertThat(profile.body().path("completedProjects").asInt()).isEqualTo(1);

        // PDF + sync log
        var pdf = customer.get("/api/v1/estimates/" + eid + "/pdf");
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(pdf.raw()).startsWith("%PDF");
        JsonNode log = customer.get("/api/v1/projects/" + pid + "/events?after=0").body();
        assertThat(log.size()).isGreaterThan(20);
        long prev = 0;
        Set<String> types = new HashSet<>();
        for (JsonNode e : log) { assertThat(e.path("seq").asLong()).isGreaterThan(prev); prev = e.path("seq").asLong(); types.add(e.path("type").asText()); }
        assertThat(types).contains("ESTIMATE_APPROVED", "PAYMENT_RECEIVED", "STAGE_COMPLETED", "PROJECT_COMPLETED", "MEDIA_UPLOADED");
        assertThat(customer.get("/api/v1/projects/" + pid + "/events?after=" + prev).body().size()).isZero();
        // notifications reached the other party
        assertThat(master.get("/api/v1/notifications").body().at("/content").size()).isGreaterThan(0);
    }

    static Map<String, Object> item(String kind, String title, int qty, String unit, long price) {
        return Map.of("kind", kind, "title", title, "quantity", qty, "unit", unit, "unitPrice", price);
    }
}
