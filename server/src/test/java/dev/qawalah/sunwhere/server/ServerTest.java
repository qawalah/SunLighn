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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The real server on a random port, a temporary data folder, and Java's own HTTP client. */
class ServerTest {
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
        cfg.adminToken = "admin-secret";
        cfg.backups = false;
        start();
    }

    void start() throws Exception {
        app = new App(cfg).start();
        base = "http://localhost:" + app.port();
        cfg.publicUrl = base;
    }

    @AfterEach
    void down() { app.stop(); }

    // ---- helpers

    record R(int status, String body, java.net.http.HttpHeaders headers, byte[] bytes) {
        JsonNode j() throws Exception { return new ObjectMapper().readTree(body); }
    }

    R call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher p = HttpRequest.BodyPublishers.noBody();
        if (body instanceof byte[] bytes) p = HttpRequest.BodyPublishers.ofByteArray(bytes);
        else if (body != null) {
            p = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            b.header("Content-Type", "application/json");
        }
        HttpResponse<byte[]> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofByteArray());
        return new R(r.statusCode(), new String(r.body(), java.nio.charset.StandardCharsets.UTF_8), r.headers(), r.body());
    }

    String signUp(String name) throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", name));
        assertEquals(201, r.status(), r.body());
        return r.j().get("token").asText();
    }

    Map<String, Object> guide(String title, String city, boolean listed) {
        return Map.of("title", title, "city", city, "month", 11, "year", 2026, "summary", "Golden hour at the Corniche.",
                "listed", listed, "shots", java.util.List.of(Map.of("name", "Water tower", "code", "AbC_123-xyz")));
    }

    static byte[] png() { return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0}; }

    static byte[] jpeg() { return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'}; }

    // ---- accounts

    @Test
    void accountGetsATokenAndARecoveryCodeAndNamesAreUnique() throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", "Sara"));
        assertEquals(201, r.status());
        assertTrue(r.j().get("token").asText().length() >= 40);
        assertTrue(r.j().get("recovery").asText().matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));
        assertEquals(409, call("POST", "/api/accounts", null, Map.of("name", "sara")).status());
        assertEquals(400, call("POST", "/api/accounts", null, Map.of("name", "<script>")).status());
        assertEquals("Sara", call("GET", "/api/me", r.j().get("token").asText(), null).j().get("name").asText());
        assertEquals(401, call("GET", "/api/me", "wrong-token-wrong-token", null).status());
    }

    @Test
    void recoveryGivesANewTokenAndKillsTheOld() throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", "Omar"));
        String old = r.j().get("token").asText();
        String code = r.j().get("recovery").asText();
        assertEquals(403, call("POST", "/api/accounts/recover", null, Map.of("name", "Omar", "recovery", "AAAA-BBBB-CCCC")).status());
        R ok = call("POST", "/api/accounts/recover", null, Map.of("name", "omar", "recovery", code.toLowerCase()));
        assertEquals(200, ok.status(), ok.body());
        assertEquals(200, call("GET", "/api/me", ok.j().get("token").asText(), null).status());
        assertEquals(401, call("GET", "/api/me", old, null).status());
    }

    @Test
    void newAccountsAreLimitedPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) signUp("User" + i);
        assertEquals(429, call("POST", "/api/accounts", null, Map.of("name", "User9")).status());
    }

    // ---- guides

    @Test
    void publishPageCityAndSitemap() throws Exception {
        String t = signUp("Layla");
        R put = call("PUT", "/api/guides/guide-0001", t, guide("Khobar at sunset", "Al Khobar", true));
        assertEquals(201, put.status(), put.body());
        assertEquals(base + "/g/guide-0001", put.j().get("url").asText());
        assertEquals(204, call("PUT", "/api/guides/guide-0001/card", t, png()).status());
        assertEquals(204, call("PUT", "/api/guides/guide-0001/photos/1", t, jpeg()).status());

        R page = call("GET", "/g/guide-0001", null, null);
        assertEquals(200, page.status());
        assertTrue(page.body().contains("<meta property=\"og:title\" content=\"Khobar at sunset · Al Khobar · SunWhere\">"), page.body());
        assertTrue(page.body().contains("twitter:card\" content=\"summary_large_image\""));
        assertTrue(page.body().contains(base + "/g/guide-0001/card.png?v="));
        assertTrue(page.body().contains("/g/guide-0001/photo/1.jpg"));
        assertTrue(page.body().contains("#AbC_123-xyz"), "the key that opens the app carries the shot's code");
        R card = call("GET", "/g/guide-0001/card.png", null, null);
        assertArrayEquals(png(), card.bytes());
        assertEquals("image/png", card.headers().firstValue("content-type").orElse(""));

        R city = call("GET", "/c/al-khobar", null, null);
        assertEquals(200, city.status());
        assertTrue(city.body().contains("November 2026") && city.body().contains("Khobar at sunset"));
        assertTrue(call("GET", "/", null, null).body().contains("Al Khobar"));
        String map = call("GET", "/sitemap.xml", null, null).body();
        assertTrue(map.contains("/g/guide-0001") && map.contains("/c/al-khobar"));
        assertEquals(404, call("GET", "/c/nowhere", null, null).status());
    }

    @Test
    void unlistedGuideOpensByLinkButIsNotInTheCity() throws Exception {
        String t = signUp("Noor");
        call("PUT", "/api/guides/guide-0002", t, guide("Private-ish", "Riyadh", false));
        assertEquals(200, call("GET", "/g/guide-0002", null, null).status());
        assertEquals(404, call("GET", "/c/riyadh", null, null).status());
        assertFalse(call("GET", "/sitemap.xml", null, null).body().contains("guide-0002"));
    }

    @Test
    void newAccountsWaitInTheirHold() throws Exception {
        app.stop();
        cfg.holdSeconds = 3600;
        start();
        String t = signUp("Fresh");
        call("PUT", "/api/guides/guide-0003", t, guide("Waiting", "Dubai", true));
        assertEquals(404, call("GET", "/c/dubai", null, null).status(), "listed pages wait for the hold");
        assertEquals(200, call("GET", "/g/guide-0003", null, null).status(), "the link works at once");
    }

    @Test
    void onlyTheOwnerChangesAGuide() throws Exception {
        String a = signUp("Aya"), b = signUp("Bader");
        assertEquals(201, call("PUT", "/api/guides/guide-0004", a, guide("Mine", "Doha", true)).status());
        assertEquals(403, call("PUT", "/api/guides/guide-0004", b, guide("Stolen", "Doha", true)).status());
        assertEquals(403, call("DELETE", "/api/guides/guide-0004", b, null).status());
        assertEquals(401, call("PUT", "/api/guides/guide-0004", null, guide("None", "Doha", true)).status());
        assertEquals(200, call("PUT", "/api/guides/guide-0004", a, guide("Mine, edited", "Doha", true)).status());
        assertEquals("Mine, edited", call("GET", "/api/guides/guide-0004", null, null).j().get("title").asText());
        assertEquals(204, call("DELETE", "/api/guides/guide-0004", a, null).status());
        assertEquals(404, call("GET", "/g/guide-0004", null, null).status());
    }

    @Test
    void badGuidesAreRefusedWithAReason() throws Exception {
        String t = signUp("Careful");
        Map<String, Object> bad = new java.util.HashMap<>(guide("x", "Doha", true));
        bad.put("month", 13);
        assertEquals(400, call("PUT", "/api/guides/guide-0005", t, bad).status());
        bad = new java.util.HashMap<>(guide("x", "Doha", true));
        bad.put("shots", java.util.List.of(Map.of("name", "n", "code", "<bad>")));
        assertEquals(400, call("PUT", "/api/guides/guide-0005", t, bad).status());
        bad.put("shots", java.util.List.of());
        assertEquals(400, call("PUT", "/api/guides/guide-0005", t, bad).status());
        assertEquals(400, call("PUT", "/api/guides/short", t, guide("x", "Doha", true)).status());
        assertEquals(404, call("GET", "/g/guide-0005", null, null).status());
    }

    @Test
    void titlesAreEscapedOnThePage() throws Exception {
        String t = signUp("Hacker");
        call("PUT", "/api/guides/guide-0006", t, guide("<img src=x onerror=alert(1)>", "Doha", true));
        String body = call("GET", "/g/guide-0006", null, null).body();
        assertFalse(body.contains("<img src=x"), "the title must not become a tag");
        assertTrue(body.contains("&lt;img src=x"));
    }

    // ---- media

    @Test
    void mediaMustBeWhatItSaysAndFitTheLimits() throws Exception {
        String t = signUp("Media");
        call("PUT", "/api/guides/guide-0007", t, guide("M", "Doha", true));
        assertEquals(400, call("PUT", "/api/guides/guide-0007/card", t, jpeg()).status());
        assertEquals(400, call("PUT", "/api/guides/guide-0007/photos/1", t, png()).status());
        assertEquals(400, call("PUT", "/api/guides/guide-0007/photos/4", t, jpeg()).status());
        byte[] big = new byte[3_100_000];
        System.arraycopy(jpeg(), 0, big, 0, jpeg().length);
        assertEquals(400, call("PUT", "/api/guides/guide-0007/photos/2", t, big).status());
        assertEquals(204, call("PUT", "/api/guides/guide-0007/photos/2", t, jpeg()).status());
        assertEquals(1, call("GET", "/api/guides/guide-0007", null, null).j().get("photos").asInt());
        assertEquals(204, call("DELETE", "/api/guides/guide-0007/photos/2", t, null).status());
        assertEquals(0, call("GET", "/api/guides/guide-0007", null, null).j().get("photos").asInt());
        assertEquals(404, call("GET", "/g/guide-0007/photo/2.jpg", null, null).status());
        assertEquals(404, call("GET", "/g/guide-0007/photo/..%2f1.jpg", null, null).status());
    }

    // ---- ratings, reports, admin

    @Test
    void ratingsAverageAndYouCannotRateYourOwn() throws Exception {
        String a = signUp("Owner1"), b = signUp("Fan1"), c = signUp("Fan2");
        call("PUT", "/api/guides/guide-0008", a, guide("Rated", "Doha", true));
        assertEquals(403, call("POST", "/api/guides/guide-0008/rating", a, Map.of("stars", 5)).status());
        assertEquals(400, call("POST", "/api/guides/guide-0008/rating", b, Map.of("stars", 6)).status());
        call("POST", "/api/guides/guide-0008/rating", b, Map.of("stars", 5));
        call("POST", "/api/guides/guide-0008/rating", c, Map.of("stars", 4));
        R r = call("POST", "/api/guides/guide-0008/rating", c, Map.of("stars", 3));      // c changes their mind
        assertEquals(2, r.j().get("votes").asInt());
        assertEquals(4.0, r.j().get("rating").asDouble(), 0.01);
        assertTrue(call("GET", "/c/doha", null, null).body().contains("4.0 of 5 (2)"));
    }

    @Test
    void threeReportsHideAGuideAndAdminCanBringItBack() throws Exception {
        // five new accounts an hour is the limit, so the reporters come from different "addresses"
        app.stop();
        cfg.trustProxy = true;
        start();
        String o = signUpFrom("Owner2", "10.0.0.1");
        call("PUT", "/api/guides/guide-0009", o, guide("Spam", "Doha", true));
        for (int i = 0; i < 3; i++) {
            String t = signUpFrom("Reporter" + i, "10.0.1." + i);
            assertEquals(400, call("POST", "/api/guides/guide-0009/report", t, Map.of("reason", " ")).status());
            assertEquals(204, call("POST", "/api/guides/guide-0009/report", t, Map.of("reason", "spam")).status());
        }
        assertEquals(404, call("GET", "/g/guide-0009", null, null).status());
        assertEquals(404, call("GET", "/c/doha", null, null).status());
        assertEquals(403, call("POST", "/api/admin/guides/guide-0009/unhide", null, null).status());
        HttpRequest unhide = HttpRequest.newBuilder(URI.create(base + "/api/admin/guides/guide-0009/unhide"))
                .header("X-Admin-Token", "admin-secret").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(204, http.send(unhide, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(200, call("GET", "/g/guide-0009", null, null).status());
    }

    String signUpFrom(String name, String ip) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/accounts")).header("Fly-Client-IP", ip)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("name", name)))).build();
        HttpResponse<String> s = http.send(r, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, s.statusCode(), s.body());
        return json.readTree(s.body()).get("token").asText();
    }

    @Test
    void deletingTheAccountRemovesItsGuidesAndMedia() throws Exception {
        String t = signUp("Leaver");
        call("PUT", "/api/guides/guide-0010", t, guide("Gone", "Doha", true));
        call("PUT", "/api/guides/guide-0010/card", t, png());
        assertTrue(Files.isDirectory(dir.resolve("media/guide-0010")));
        assertEquals(204, call("DELETE", "/api/me", t, null).status());
        assertEquals(404, call("GET", "/g/guide-0010", null, null).status());
        assertFalse(Files.exists(dir.resolve("media/guide-0010")));
        assertEquals(401, call("GET", "/api/me", t, null).status());
        signUp("Leaver");                       // the name is free again
    }

    @Test
    void healthAndBackup() throws Exception {
        assertEquals("ok", call("GET", "/healthz", null, null).body());
        signUp("Backup");
        Path target = dir.resolve("backup/copy.db");
        new Store(dir).backupTo(target);
        assertTrue(Files.size(target) > 0);
    }
}
