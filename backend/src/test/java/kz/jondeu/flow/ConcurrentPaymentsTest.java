package kz.jondeu.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import kz.jondeu.support.Api;
import kz.jondeu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The budget must never be oversold, even when many payments race. */
@IntegrationTest
class ConcurrentPaymentsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void racingPaymentsNeverExceedTheApprovedBudget() throws Exception {
        Api api = new Api(mvc, om);
        var c = api.register("CUSTOMER", "Гонка");
        var pro = api.register("CONTRACTOR", "Подрядчик");
        UUID pid = c.post("/api/v1/projects", Map.of("name", "Гонка платежей", "propertyType", "APARTMENT", "areaM2", 40, "address", "Алматы", "category", "KITCHEN", "description", "x", "budgetMax", 1000000)).id();
        c.post("/api/v1/projects/" + pid + "/submit");
        var prop = pro.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 1000000, "durationDays", 30));
        c.post("/api/v1/proposals/" + prop.id() + "/accept");
        var est = pro.post("/api/v1/projects/" + pid + "/estimates", Map.of("items", List.of(Map.of("title", "Работы", "quantity", 1, "unit", "компл", "unitPrice", 1000000, "kind", "LABOR"))));
        pro.post("/api/v1/estimates/" + est.id() + "/submit");
        c.post("/api/v1/estimates/" + est.id() + "/approve");

        int n = 8; // 8 x 300 000 = 2 400 000 requested against 1 000 000 budget -> exactly 3 may succeed
        ExecutorService ex = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String key = "race-" + UUID.randomUUID();
            fs.add(ex.submit(() -> { go.await(); return c.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "STAGE", "method", "CASH", "amount", 300000), "Idempotency-Key", key).status(); }));
        }
        go.countDown();
        int ok = 0, rejected = 0;
        for (var f : fs) { int s = f.get(30, TimeUnit.SECONDS); if (s == 200) ok++; else if (s == 422) rejected++; }
        ex.shutdown();
        assertThat(ok).isEqualTo(3);
        assertThat(rejected).isEqualTo(n - 3);
        var fin = c.get("/api/v1/projects/" + pid + "/overview").body().at("/finance");
        assertThat(new BigDecimal(fin.path("reserved").asText())).isEqualByComparingTo("900000");

        // identical retries with the SAME key do not multiply
        String same = "same-" + UUID.randomUUID();
        Set<String> ids = ConcurrentHashMap.newKeySet();
        ExecutorService ex2 = Executors.newFixedThreadPool(5);
        List<Future<?>> f2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) f2.add(ex2.submit(() -> ids.add(c.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "STAGE", "method", "CASH", "amount", 100000), "Idempotency-Key", same).str("id"))));
        for (var f : f2) f.get(30, TimeUnit.SECONDS);
        ex2.shutdown();
        assertThat(ids).hasSize(1);
        assertThat(new BigDecimal(c.get("/api/v1/projects/" + pid + "/overview").body().at("/finance/reserved").asText())).isEqualByComparingTo("1000000");
    }
}
