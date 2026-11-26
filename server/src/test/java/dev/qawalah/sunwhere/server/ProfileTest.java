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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Handles and profiles (people design, task 1). */
class ProfileTest {
    @TempDir Path dir;
    App app;
    Config cfg;
    String base;
    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void up() throws Exception {
        cfg = new Config();
        cfg.port = 0;
        cfg.dataDir = dir;
        cfg.holdSeconds = 0;
        cfg.backups = false;
        app = new App(cfg).start();
        base = "http://localhost:" + app.port();
    }

    @AfterEach
    void down() { app.stop(); }

    record R(int status, JsonNode body) {
        JsonNode j() { return body; }
    }

    R call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.header("Fly-Client-IP", "10.9." + (Math.abs((token == null ? "x" : token).hashCode()) % 200) + ".1");
        HttpRequest.BodyPublisher p = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofString());
        return new R(r.statusCode(), r.body().isEmpty() ? null : json.readTree(r.body()));
    }

    String account(String name, String handle) throws Exception {
        cfg.trustProxy = true;
        Map<String, Object> m = handle == null ? Map.of("name", name) : Map.of("name", name, "handle", handle);
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/accounts")).header("Content-Type", "application/json")
                .header("Fly-Client-IP", "10.1." + Math.abs(name.hashCode() % 250) + ".1")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(m))).build();
        HttpResponse<String> s = http.send(r, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, s.statusCode(), s.body());
        return json.readTree(s.body()).get("token").asText();
    }

    @Test
    void anAccountGetsAHandleFromItsNameOrTheOneAsked() throws Exception {
        String t = account("Sara Ahmed", null);
        assertEquals("sara_ahmed", call("GET", "/api/me", t, null).j().get("handle").asText());
        String u = account("Omar", "omar_k");
        assertEquals("omar_k", call("GET", "/api/me", u, null).j().get("handle").asText());
    }

    @Test
    void handleRules() throws Exception {
        String a = account("Aya", "aya_1"), b = account("Bader", "bader");
        assertEquals(409, call("PUT", "/api/me/profile", b, Map.of("handle", "aya_1")).status());
        assertEquals(400, call("PUT", "/api/me/profile", b, Map.of("handle", "admin")).status());
        assertEquals(400, call("PUT", "/api/me/profile", b, Map.of("handle", "Ab")).status());
        assertEquals(400, call("PUT", "/api/me/profile", b, Map.of("handle", "a-b")).status());
        assertEquals(400, call("PUT", "/api/me/profile", b, Map.of("handle", "x".repeat(21))).status());
        assertEquals(400, call("PUT", "/api/me/profile", b, Map.of("bio", "y".repeat(161))).status());
        assertEquals(200, call("PUT", "/api/me/profile", b, Map.of("handle", "bader_2", "bio", "Sunsets in the Gulf")).status());
        R me = call("GET", "/api/me", b, null);
        assertEquals("bader_2", me.j().get("handle").asText());
        assertEquals("Sunsets in the Gulf", me.j().get("bio").asText());
        assertEquals(429, call("PUT", "/api/me/profile", b, Map.of("handle", "bader_3")).status(), "once in 30 days");
        assertEquals(200, call("PUT", "/api/me/profile", b, Map.of("handle", "bader_2", "bio", "Changed bio")).status(), "the same handle is no change");
        assertNotNull(a);
    }

    @Test
    void recoveryAlsoTakesTheHandle() throws Exception {
        cfg.trustProxy = true;
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/accounts")).header("Content-Type", "application/json")
                .header("Fly-Client-IP", "10.2.2.2")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Noor\",\"handle\":\"noor_q\"}")).build();
        JsonNode created = json.readTree(http.send(r, HttpResponse.BodyHandlers.ofString()).body());
        R rec = call("POST", "/api/accounts/recover", null, Map.of("name", "noor_q", "recovery", created.get("recovery").asText()));
        assertEquals(200, rec.status());
        assertEquals("noor_q", rec.j().get("handle").asText());
    }

    @Test
    void migrationMakesUniqueHandles() throws Exception {
        app.stop();
        Path old = dir.resolve("old");
        java.nio.file.Files.createDirectories(old);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old.resolve("sunwhere.db")); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE account(id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                    + " token_hash TEXT NOT NULL, recovery_hash TEXT NOT NULL, created INTEGER NOT NULL)");
            String[] names = {"Sara", "sara!", "sara2", "سارة", "محمد", "Al Khobar Fan"};
            for (int i = 0; i < names.length; i++)
                s.execute("INSERT INTO account VALUES('id" + i + "','" + names[i] + "','t" + i + "','r" + i + "'," + (100 + i) + ")");
        }
        new Store(old);
        Set<String> seen = new HashSet<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + old.resolve("sunwhere.db")); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT handle FROM account ORDER BY created")) {
            while (r.next()) {
                String h = r.getString(1);
                assertTrue(Text.validHandle(h), h);
                assertTrue(seen.add(h), "duplicate " + h);
            }
        }
        assertEquals(6, seen.size());
        assertTrue(seen.contains("sara") && seen.contains("al_khobar_fan"));
        app = new App(cfg).start();            // so tearDown has something to stop
    }
}
