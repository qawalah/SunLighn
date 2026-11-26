package dev.qawalah.sunwhere.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Admin stats and ban (people design, task 6). */
class AdminTest {
    @TempDir Path dir;
    App app;
    Config cfg;
    String base;
    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    int n;

    @BeforeEach
    void up() throws Exception {
        cfg = new Config();
        cfg.port = 0;
        cfg.dataDir = dir;
        cfg.holdSeconds = 0;
        cfg.backups = false;
        cfg.trustProxy = true;
        cfg.adminToken = "admin-secret";
        app = new App(cfg).start();
        base = "http://localhost:" + app.port();
    }

    @AfterEach
    void down() { app.stop(); }

    record R(int status, String body) {
        JsonNode j() throws Exception { return new ObjectMapper().readTree(body); }
    }

    R call(String method, String path, String token, String admin, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Fly-Client-IP", "10.7.0." + (++n % 250));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (admin != null) b.header("X-Admin-Token", admin);
        HttpRequest.BodyPublisher p = body instanceof byte[] bytes ? HttpRequest.BodyPublishers.ofByteArray(bytes)
                : body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null && !(body instanceof byte[])) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofString());
        return new R(r.statusCode(), r.body());
    }

    String account(String handle) throws Exception {
        R r = call("POST", "/api/accounts", null, null, Map.of("name", "N " + handle, "handle", handle));
        assertEquals(201, r.status(), r.body());
        return r.j().get("token").asText();
    }

    Map<String, Object> guide(String level, String city) {
        return Map.of("title", "T", "city", city, "month", 11, "year", 2026, "level", level,
                "shots", List.of(Map.of("name", "n", "code", "AbC_123-xyz")));
    }

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Test
    void statsNeedTheToken() throws Exception {
        assertEquals(403, call("GET", "/api/admin/stats", null, null, null).status());
        assertEquals(403, call("GET", "/api/admin/stats", null, "wrong", null).status());
        assertEquals(200, call("GET", "/api/admin/stats", null, "admin-secret", null).status());
        assertEquals(200, call("GET", "/admin", null, null, null).status(), "the page itself asks for the token");
    }

    @Test
    void statsCountEverything() throws Exception {
        String a = account("aya"), b = account("bader"), c = account("carl");
        call("PUT", "/api/guides/guide-0001", a, null, guide("public", "Doha"));
        call("PUT", "/api/guides/guide-0002", a, null, guide("public", "Doha"));
        call("PUT", "/api/guides/guide-0003", a, null, guide("friends", "Dubai"));
        call("PUT", "/api/guides/guide-0004", b, null, guide("link", "Dubai"));
        call("PUT", "/api/guides/guide-0001/photos/1", a, null, JPEG);
        call("POST", "/api/people/aya/follow", b, null, null);
        call("POST", "/api/people/aya/friend", b, null, null);
        call("POST", "/api/people/bader/friend", a, null, null);          // friends
        call("POST", "/api/people/carl/friend", a, null, null);           // pending
        call("POST", "/api/people/carl/block", b, null, null);
        call("POST", "/api/guides/guide-0001/like", b, null, null);
        call("POST", "/api/guides/guide-0001/photos/1/like", b, null, null);
        call("POST", "/api/guides/guide-0001/rating", b, null, Map.of("stars", 5));
        call("POST", "/api/guides/guide-0002/report", c, null, Map.of("reason", "spam"));
        JsonNode s = call("GET", "/api/admin/stats", null, "admin-secret", null).j();
        assertEquals(3, s.get("accounts").get("total").asInt());
        assertEquals(3, s.get("accounts").get("new_7d").asInt());
        assertEquals(0, s.get("accounts").get("banned").asInt());
        assertEquals(4, s.get("guides").get("total").asInt());
        assertEquals(2, s.get("guides").get("public").asInt());
        assertEquals(1, s.get("guides").get("friends").asInt());
        assertEquals(1, s.get("guides").get("link").asInt());
        assertEquals(1, s.get("photos").asInt());
        assertEquals(2, s.get("likes").asInt());
        assertEquals(1, s.get("follows").asInt());
        assertEquals(1, s.get("friendships").asInt());
        assertEquals(1, s.get("pending_requests").asInt());
        assertEquals(1, s.get("blocks").asInt());
        assertEquals(1, s.get("ratings").asInt());
        assertEquals(1, s.get("open_reports").asInt());
        assertEquals("Doha", s.get("top_cities").get(0).get("name").asText());
        assertEquals(2, s.get("top_cities").get(0).get("guides").asInt());
        assertTrue(s.get("db_bytes").asLong() > 0);
        assertTrue(s.get("storage_bytes").asLong() > 0);
        assertTrue(s.get("requests_today").asLong() >= 10);
    }

    @Test
    void banAndUnban() throws Exception {
        String a = account("aya"), b = account("bader");
        call("PUT", "/api/guides/guide-0001", a, null, guide("public", "Doha"));
        assertEquals(403, call("POST", "/api/admin/accounts/aya/ban", null, null, null).status());
        assertEquals(404, call("POST", "/api/admin/accounts/nobody/ban", null, "admin-secret", null).status());
        assertEquals(204, call("POST", "/api/admin/accounts/aya/ban", null, "admin-secret", null).status());
        assertEquals(401, call("GET", "/api/me", a, null, null).status(), "the token stops working");
        assertEquals(404, call("GET", "/g/guide-0001", null, null, null).status());
        assertEquals(404, call("GET", "/c/doha", null, null, null).status());
        assertEquals(404, call("GET", "/u/aya", null, null, null).status());
        assertEquals(404, call("GET", "/api/people/aya", b, null, null).status());
        assertEquals(1, call("GET", "/api/admin/stats", null, "admin-secret", null).j().get("accounts").get("banned").asInt());
        assertEquals(204, call("POST", "/api/admin/accounts/aya/unban", null, "admin-secret", null).status());
        assertEquals(200, call("GET", "/api/me", a, null, null).status());
        assertEquals(200, call("GET", "/g/guide-0001", null, null, null).status());
    }
}
