package kz.jondeu.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import kz.jondeu.support.Api;
import kz.jondeu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestPropertySource(properties = {"jondeu.demo.enabled=true", "jondeu.storage.local-dir=target/test-uploads-demo"})
class DemoSeedTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void demoEnvironmentIsPopulatedAndConsistent() {
        Api api = new Api(mvc, om);
        var asel = api.loginAs("+77010000001", "demo1234");
        JsonNode projects = asel.get("/api/v1/projects").body().get("content");
        assertThat(projects.size()).isEqualTo(2); // flagship + completed kitchen
        String flagship = null;
        for (JsonNode p : projects) if (p.path("name").asText().contains("64")) flagship = p.path("id").asText();
        assertThat(flagship).isNotNull();

        JsonNode ov = asel.get("/api/v1/projects/" + flagship + "/overview").body();
        assertThat(new BigDecimal(ov.at("/finance/approvedBudget").asText())).isEqualByComparingTo("12455000");
        assertThat(new BigDecimal(ov.at("/finance/paid").asText())).isEqualByComparingTo("8753000");
        assertThat(new BigDecimal(ov.at("/finance/remaining").asText())).isEqualByComparingTo("3702000");
        assertThat(new BigDecimal(ov.at("/progress/percent").asText())).isEqualByComparingTo("65.0");
        assertThat(ov.at("/project/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(ov.at("/project/demo").asBoolean()).isTrue();
        assertThat(ov.at("/quality/status").asText()).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(ov.at("/counts/photos").asInt()).isEqualTo(6);
        assertThat(asel.get("/api/v1/projects/" + flagship + "/materials").body().at("/items").size()).isEqualTo(7);
        assertThat(asel.get("/api/v1/projects/" + flagship + "/inspections").body().size()).isEqualTo(4);

        // directory is populated and filterable
        assertThat(asel.get("/api/v1/masters?category=ELECTRICAL&verified=true").body().at("/content").size()).isEqualTo(1);
        assertThat(asel.get("/api/v1/masters?size=50").body().at("/totalElements").asInt()).isGreaterThanOrEqualTo(7);
        assertThat(asel.get("/api/v1/masters?sort=price&size=50").body().at("/content/0/hourlyRate").asInt()).isEqualTo(1400);

        // the contractor sees the same numbers
        var company = api.loginAs("+77010000009", "demo1234");
        assertThat(company.get("/api/v1/projects/" + flagship + "/overview").body().at("/finance/paid").asText()).isEqualTo(ov.at("/finance/paid").asText());
        var admin = api.loginAs("+77010000099", "demo1234");
        assertThat(admin.get("/api/v1/admin/stats").status()).isEqualTo(200);
    }
}
