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

/** The profile page and the people on the guide page (people design, task 5). */
class PagesTest {
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
        app = new App(cfg).start();
        base = "http://localhost:" + app.port();
        cfg.publicUrl = base;
    }

    @AfterEach
    void down() { app.stop(); }

    record R(int status, String body) {
        JsonNode j() throws Exception { return new ObjectMapper().readTree(body); }
    }

    R call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Fly-Client-IP", "10.6.0." + (++n % 250));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher p = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofString());
        return new R(r.statusCode(), r.body());
    }

    String account(String handle) throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", "N " + handle, "handle", handle));
        assertEquals(201, r.status(), r.body());
        return r.j().get("token").asText();
    }

    Map<String, Object> guide(String title, String level) {
        return Map.of("title", title, "city", "Doha", "month", 11, "year", 2026, "level", level,
                "shots", List.of(Map.of("name", "n", "code", "AbC_123-xyz")));
    }

    @Test
    void profilePageShowsStatsAndPublicGuides() throws Exception {
        String o = account("layla"), f = account("fan");
        call("PUT", "/api/me/profile", o, Map.of("bio", "Chasing <b>sunsets</b> in the Gulf"));
        call("PUT", "/api/guides/guide-0001", o, guide("Public one", "public"));
        call("PUT", "/api/guides/guide-0002", o, guide("Friends one", "friends"));
        call("PUT", "/api/guides/guide-0003", o, guide("Link one", "link"));
        call("POST", "/api/people/layla/follow", f, null);
        call("POST", "/api/guides/guide-0001/like", f, null);
        R page = call("GET", "/u/layla", null, null);
        assertEquals(200, page.status());
        assertTrue(page.body().contains("@layla"));
        assertTrue(page.body().contains("Public one"));
        assertFalse(page.body().contains("Friends one"));
        assertFalse(page.body().contains("Link one"));
        assertTrue(page.body().contains("&lt;b&gt;sunsets&lt;/b&gt;"), "the bio is escaped");
        assertTrue(page.body().contains("1 follower") && page.body().contains("1 like"), page.body());
        assertTrue(page.body().contains("og:title"));
    }

    @Test
    void missingProfileIs404() throws Exception {
        assertEquals(404, call("GET", "/u/nobody", null, null).status());
        assertEquals(404, call("GET", "/u/AB!", null, null).status());
    }

    @Test
    void guidePageShowsTheAuthorAndLikes() throws Exception {
        String o = account("layla"), f = account("fan");
        call("PUT", "/api/guides/guide-0001", o, guide("Public one", "public"));
        call("POST", "/api/guides/guide-0001/like", f, null);
        String body = call("GET", "/g/guide-0001", null, null).body();
        assertTrue(body.contains("href=\"/u/layla\""), "the author links to the profile");
        assertTrue(body.contains("1 like"));
        assertTrue(body.contains("Like it in the SunWhere app"));
    }
}
