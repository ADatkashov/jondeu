package kz.jondeu.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.support.Api;
import kz.jondeu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthAndAccessTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private UUID project(Api.Actor owner) {
        return owner.post("/api/v1/projects", Map.of("name", "Ванная", "propertyType", "APARTMENT", "areaM2", 6, "address", "Алматы", "category", "BATHROOM", "description", "x", "budgetMax", 1000000)).id();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        Api api = new Api(mvc, om);
        assertThat(api.rawGet("/api/v1/projects").status()).isEqualTo(401);
        assertThat(api.rawGet("/api/v1/auth/me").status()).isEqualTo(401);
        assertThat(api.rawGet("/api/v1/admin/stats").status()).isEqualTo(401);
        assertThat(api.rawGet("/actuator/health").status()).isEqualTo(200);
        assertThat(api.rawGet("/v3/api-docs").status()).isEqualTo(200);
    }

    @Test
    void privilegedRolesCannotBeSelfAssigned() {
        Api api = new Api(mvc, om);
        for (String role : new String[]{"PLATFORM_ADMIN", "PLATFORM_SUPER_ADMIN", "INSPECTOR", "PROJECT_MANAGER"}) {
            assertThat(api.rawPost("/api/v1/auth/register", Map.of("phone", Api.randomPhone(), "password", "Passw0rd!x", "fullName", "Hacker", "role", role)).status()).isEqualTo(400);
        }
        var u = api.register("CUSTOMER", "Обычный");
        assertThat(u.get("/api/v1/admin/stats").status()).isEqualTo(403);
        assertThat(u.get("/api/v1/admin/audit").status()).isEqualTo(403);
    }

    @Test
    void registrationValidatesPhoneAndPassword() {
        Api api = new Api(mvc, om);
        assertThat(api.rawPost("/api/v1/auth/register", Map.of("phone", "123", "password", "Passw0rd!x", "fullName", "A", "role", "CUSTOMER")).status()).isEqualTo(400);
        assertThat(api.rawPost("/api/v1/auth/register", Map.of("phone", Api.randomPhone(), "password", "short", "fullName", "A", "role", "CUSTOMER")).status()).isEqualTo(400);
        var u = api.register("CUSTOMER", "Дубль");
        assertThat(api.rawPost("/api/v1/auth/register", Map.of("phone", u.phone, "password", "Passw0rd!x", "fullName", "A", "role", "CUSTOMER")).status()).isEqualTo(409);
    }

    @Test
    void phoneNumbersAreNormalized() {
        Api api = new Api(mvc, om);
        String local = "8 (701) 555-" + String.format("%04d", (int) (Math.random() * 10000));
        var r = api.rawPost("/api/v1/auth/register", Map.of("phone", local, "password", "Passw0rd!x", "fullName", "Нормализация", "role", "CUSTOMER"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.at("/user/phone").asText()).startsWith("+7701555");
        assertThat(api.rawPost("/api/v1/auth/login", Map.of("phone", r.at("/user/phone").asText(), "password", "Passw0rd!x")).status()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() {
        Api api = new Api(mvc, om);
        var u = api.register("CUSTOMER", "Логин");
        var wrong = api.rawPost("/api/v1/auth/login", Map.of("phone", u.phone, "password", "wrong-password"));
        var unknown = api.rawPost("/api/v1/auth/login", Map.of("phone", Api.randomPhone(), "password", "wrong-password"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.str("code")).isEqualTo(unknown.str("code"));
    }

    @Test
    void refreshTokensRotateAndReuseRevokesTheFamily() {
        Api api = new Api(mvc, om);
        var u = api.register("CUSTOMER", "Токены");
        String first = u.refresh;
        var second = api.rawPost("/api/v1/auth/refresh", Map.of("refreshToken", first));
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.str("refreshToken")).isNotEqualTo(first);
        // replaying the rotated token is a theft signal: it fails AND kills the newer token too
        assertThat(api.rawPost("/api/v1/auth/refresh", Map.of("refreshToken", first)).status()).isEqualTo(401);
        assertThat(api.rawPost("/api/v1/auth/refresh", Map.of("refreshToken", second.str("refreshToken"))).status()).isEqualTo(401);
    }

    @Test
    void logoutInvalidatesRefreshToken() {
        Api api = new Api(mvc, om);
        var u = api.register("CUSTOMER", "Выход");
        assertThat(api.rawPost("/api/v1/auth/logout", Map.of("refreshToken", u.refresh)).status()).isEqualTo(200);
        assertThat(api.rawPost("/api/v1/auth/refresh", Map.of("refreshToken", u.refresh)).status()).isEqualTo(401);
    }

    @Test
    void otpLoginWorksAndCodeIsSingleUse() {
        Api api = new Api(mvc, om);
        var u = api.register("CUSTOMER", "ОТП");
        var req = api.rawPost("/api/v1/auth/otp/request", Map.of("phone", u.phone));
        String code = req.str("devCode");
        assertThat(code).hasSize(6);
        assertThat(api.rawPost("/api/v1/auth/otp/login", Map.of("phone", u.phone, "code", "000000".equals(code) ? "111111" : "000000")).status()).isEqualTo(400);
        assertThat(api.rawPost("/api/v1/auth/otp/login", Map.of("phone", u.phone, "code", code)).status()).isEqualTo(200);
        assertThat(api.rawPost("/api/v1/auth/otp/login", Map.of("phone", u.phone, "code", code)).status()).isEqualTo(400);
    }

    @Test
    void strangersCannotSeeOrTouchSomeoneElsesProject() {
        Api api = new Api(mvc, om);
        var owner = api.register("CUSTOMER", "Владелец");
        var stranger = api.register("CUSTOMER", "Чужой");
        var pro = api.register("MASTER", "Мастер без доступа");
        UUID pid = project(owner);
        String[] gets = {"", "/overview", "/stages", "/estimates", "/payments", "/materials", "/media", "/inspections", "/members", "/activity", "/events", "/conversation", "/proposals"};
        for (String suffix : gets) {
            assertThat(stranger.get("/api/v1/projects/" + pid + suffix).status()).as("stranger GET %s", suffix).isEqualTo(404);
            assertThat(pro.get("/api/v1/projects/" + pid + suffix).status()).as("pro GET %s", suffix).isEqualTo(404);
        }
        assertThat(stranger.patch("/api/v1/projects/" + pid, Map.of("name", "pwn")).status()).isEqualTo(404);
        assertThat(stranger.post("/api/v1/projects/" + pid + "/submit").status()).isEqualTo(404);
        assertThat(stranger.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "ADVANCE", "method", "CASH", "amount", 1), "Idempotency-Key", UUID.randomUUID().toString()).status()).isEqualTo(404);
        assertThat(stranger.get("/api/v1/projects").body().path("totalElements").asInt()).isZero();
        assertThat(owner.get("/api/v1/projects/" + pid).status()).isEqualTo(200);
    }

    @Test
    void customersCannotBrowseOrBidOnRequests() {
        Api api = new Api(mvc, om);
        var customer = api.register("CUSTOMER", "Заказчик");
        var other = api.register("CUSTOMER", "Другой заказчик");
        UUID pid = project(customer);
        customer.post("/api/v1/projects/" + pid + "/submit");
        assertThat(other.get("/api/v1/requests/open").status()).isEqualTo(403);
        assertThat(other.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 1, "durationDays", 1)).status()).isEqualTo(403);
    }

    @Test
    void incompleteRequestCannotBeSubmitted() {
        Api api = new Api(mvc, om);
        var c = api.register("CUSTOMER", "Неполная");
        UUID pid = c.post("/api/v1/projects", Map.of("name", "Без деталей", "propertyType", "HOUSE", "areaM2", 100)).id();
        var r = c.post("/api/v1/projects/" + pid + "/submit");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.str("code")).isEqualTo("INCOMPLETE_REQUEST");
    }

    @Test
    void payingBeforeAnEstimateIsApprovedIsRejected() {
        Api api = new Api(mvc, om);
        var c = api.register("CUSTOMER", "Рано платит");
        UUID pid = project(c);
        var r = c.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "ADVANCE", "method", "CASH", "amount", 1000), "Idempotency-Key", UUID.randomUUID().toString());
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("code")).isEqualTo("NO_APPROVED_ESTIMATE");
    }

    @Test
    void kaspiIsNotFakedWhenNotConnected() {
        Api api = new Api(mvc, om);
        var c = api.register("CUSTOMER", "Каспи");
        var pro = api.register("CONTRACTOR", "Подрядчик");
        UUID pid = project(c);
        c.post("/api/v1/projects/" + pid + "/submit");
        var prop = pro.post("/api/v1/projects/" + pid + "/proposals", Map.of("price", 900000, "durationDays", 30));
        c.post("/api/v1/proposals/" + prop.id() + "/accept");
        var est = pro.post("/api/v1/projects/" + pid + "/estimates", Map.of("items", java.util.List.of(Map.of("title", "Работы", "quantity", 1, "unit", "компл", "unitPrice", 900000, "kind", "LABOR"))));
        pro.post("/api/v1/estimates/" + est.id() + "/submit");
        c.post("/api/v1/estimates/" + est.id() + "/approve");
        // Kaspi has no sandbox here: the sandbox provider handles CARD only when asked; KASPI goes to the sandbox too (supports), so check CARD vs unsupported method path
        var r = c.postWithHeader("/api/v1/projects/" + pid + "/payments", Map.of("type", "ADVANCE", "method", "KASPI", "amount", 100000), "Idempotency-Key", UUID.randomUUID().toString());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.str("status")).isEqualTo("PENDING");
        assertThat(r.body().path("sandbox").asBoolean()).isTrue(); // clearly labelled as sandbox, never reported as paid
    }
}
