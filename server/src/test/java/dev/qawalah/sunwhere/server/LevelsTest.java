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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Guide levels: link, public, friends (people design, task 2). */
class LevelsTest {
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
    }

    @AfterEach
    void down() { app.stop(); }

    record R(int status, String body) {
        JsonNode j() throws Exception { return new ObjectMapper().readTree(body); }
    }

    R call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Fly-Client-IP", "10.3.0." + (++n % 250));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher p = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofString());
        return new R(r.statusCode(), r.body());
    }

    String account(String name) throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", name));
        assertEquals(201, r.status(), r.body());
        return r.j().get("token").asText();
    }

    Map<String, Object> guide(String level) {
        return Map.of("title", "T", "city", "Doha", "month", 11, "year", 2026, "level", level,
                "shots", List.of(Map.of("name", "n", "code", "AbC_123-xyz")));
    }

    @Test
    void friendsGuideIsPrivateToFriends() throws Exception {
        String owner = account("Owner"), stranger = account("Stranger");
        assertEquals(201, call("PUT", "/api/guides/guide-0001", owner, guide("friends")).status());
        assertEquals(200, call("GET", "/api/guides/guide-0001", owner, null).status());
        assertEquals(404, call("GET", "/api/guides/guide-0001", stranger, null).status());
        assertEquals(404, call("GET", "/api/guides/guide-0001", null, null).status());
        assertEquals(404, call("GET", "/g/guide-0001", null, null).status(), "no page, no preview");
        assertEquals(404, call("GET", "/c/doha", null, null).status());
        assertFalse(call("GET", "/sitemap.xml", null, null).body().contains("guide-0001"));
        assertEquals("friends", call("GET", "/api/guides/guide-0001", owner, null).j().get("level").asText());
    }

    @Test
    void publicIsListedAndLinkIsNot() throws Exception {
        String o = account("Lister");
        call("PUT", "/api/guides/guide-0002", o, guide("public"));
        call("PUT", "/api/guides/guide-0003", o, guide("link"));
        assertEquals(200, call("GET", "/g/guide-0003", null, null).status());
        String city = call("GET", "/c/doha", null, null).body();
        assertTrue(city.contains("guide-0002") && !city.contains("guide-0003"));
        assertEquals(400, call("PUT", "/api/guides/guide-0004", o, guide("secret")).status());
    }

    @Test
    void oldListedFlagStillWorks() throws Exception {
        String o = account("Old");
        Map<String, Object> g = new java.util.HashMap<>(guide("x"));
        g.remove("level");
        g.put("listed", true);
        call("PUT", "/api/guides/guide-0005", o, g);
        assertEquals("public", call("GET", "/api/guides/guide-0005", null, null).j().get("level").asText());
        g.put("listed", false);
        call("PUT", "/api/guides/guide-0005", o, g);
        assertEquals("link", call("GET", "/api/guides/guide-0005", null, null).j().get("level").asText());
    }

    @Test
    void migrationKeepsListedGuides() throws Exception {
        app.stop();
        Path old = dir.resolve("old");
        java.nio.file.Files.createDirectories(old);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old.resolve("sunwhere.db")); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE account(id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                    + " token_hash TEXT NOT NULL, recovery_hash TEXT NOT NULL, created INTEGER NOT NULL)");
            s.execute("INSERT INTO account VALUES('a1','Zed','t','r',1)");
            s.execute("CREATE TABLE guide(id TEXT PRIMARY KEY, owner TEXT NOT NULL, title TEXT NOT NULL, city TEXT NOT NULL,"
                    + " city_slug TEXT NOT NULL, country TEXT NOT NULL DEFAULT '', month INTEGER NOT NULL, year INTEGER NOT NULL,"
                    + " summary TEXT NOT NULL DEFAULT '', shots TEXT NOT NULL, listed INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0,"
                    + " created INTEGER NOT NULL, updated INTEGER NOT NULL)");
            s.execute("INSERT INTO guide VALUES('g1','a1','T','Doha','doha','',11,2026,'','[]',1,0,1,1)");
            s.execute("INSERT INTO guide VALUES('g2','a1','T','Doha','doha','',11,2026,'','[]',0,0,1,1)");
        }
        new Store(old);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old.resolve("sunwhere.db")); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT id, level FROM guide ORDER BY id")) {
            assertTrue(r.next());
            assertEquals("public", r.getString(2));
            assertTrue(r.next());
            assertEquals("link", r.getString(2));
        }
        app = new App(cfg).start();
    }
}
