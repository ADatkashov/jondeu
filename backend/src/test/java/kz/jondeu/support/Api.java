package kz.jondeu.support;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tiny test DSL over MockMvc: each Actor is an authenticated user. */
public class Api {
    public record Result(int status, JsonNode body, String raw) {
        public String str(String field) { return body.path(field).asText(null); }
        public UUID id() { return UUID.fromString(body.path("id").asText()); }
        public JsonNode at(String ptr) { return body.at(ptr); }
    }

    private final MockMvc mvc;
    private final ObjectMapper om;

    public Api(MockMvc mvc, ObjectMapper om) { this.mvc = mvc; this.om = om; }

    public class Actor {
        public final String phone;
        public final UUID id;
        public String access;
        public String refresh;

        Actor(String phone, UUID id, String access, String refresh) { this.phone = phone; this.id = id; this.access = access; this.refresh = refresh; }

        public Result get(String url) { return exec(auth(MockMvcRequestBuilders.get(url))); }
        public Result post(String url, Object body) { return exec(withBody(MockMvcRequestBuilders.post(url), body)); }
        public Result post(String url) { return exec(auth(MockMvcRequestBuilders.post(url))); }
        public Result put(String url, Object body) { return exec(withBody(MockMvcRequestBuilders.put(url), body)); }
        public Result patch(String url, Object body) { return exec(withBody(MockMvcRequestBuilders.patch(url), body)); }
        public Result delete(String url) { return exec(auth(MockMvcRequestBuilders.delete(url))); }
        public Result postWithHeader(String url, Object body, String h, String v) { return exec(withBody(MockMvcRequestBuilders.post(url), body).header(h, v)); }

        public Result upload(String url, String filename, byte[] data, String contentType, Map<String, String> params) {
            var b = MockMvcRequestBuilders.multipart(url).file(new MockMultipartFile("file", filename, contentType, data));
            params.forEach(b::param);
            return exec(auth(b));
        }

        private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) { return access == null ? b : b.header("Authorization", "Bearer " + access); }
        private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder b, Object body) {
            try { return auth(b).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public Result exec(MockHttpServletRequestBuilder b) {
        try {
            MvcResult r = mvc.perform(b).andReturn();
            String raw = r.getResponse().getContentAsString();
            JsonNode n = raw.isBlank() || !r.getResponse().getContentType().contains("json") ? om.createObjectNode() : om.readTree(raw);
            return new Result(r.getResponse().getStatus(), n, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Result rawPost(String url, Object body) {
        try { return exec(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public Result rawGet(String url) { return exec(MockMvcRequestBuilders.get(url)); }

    public static String randomPhone() { return "+7701" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000)); }

    public Actor register(String role, String name) {
        String phone = randomPhone();
        Result r = rawPost("/api/v1/auth/register", Map.of("phone", phone, "password", "Passw0rd!x", "fullName", name, "role", role));
        if (r.status() != 200) throw new IllegalStateException("register failed: " + r.raw());
        return new Actor(phone, UUID.fromString(r.at("/user/id").asText()), r.str("accessToken"), r.str("refreshToken"));
    }

    public Actor loginAs(String phone, String password) {
        Result r = rawPost("/api/v1/auth/login", Map.of("phone", phone, "password", password));
        return new Actor(phone, UUID.fromString(r.at("/user/id").asText()), r.str("accessToken"), r.str("refreshToken"));
    }
}
