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

/** Likes on guides and photos, and the feed (people design, task 4). */
class LikesTest {
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Fly-Client-IP", "10.5.0." + (++n % 250));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher p = body instanceof byte[] bytes ? HttpRequest.BodyPublishers.ofByteArray(bytes)
                : body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null && !(body instanceof byte[])) b.header("Content-Type", "application/json");
        HttpResponse<String> r = http.send(b.method(method, p).build(), HttpResponse.BodyHandlers.ofString());
        return new R(r.statusCode(), r.body());
    }

    String account(String handle) throws Exception {
        R r = call("POST", "/api/accounts", null, Map.of("name", "N " + handle, "handle", handle));
        assertEquals(201, r.status(), r.body());
        return r.j().get("token").asText();
    }

    Map<String, Object> guide(String level) {
        return Map.of("title", "T", "city", "Doha", "month", 11, "year", 2026, "level", level,
                "shots", List.of(Map.of("name", "n", "code", "AbC_123-xyz")));
    }

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Test
    void likeRules() throws Exception {
        String o = account("owner"), a = account("aya"), b = account("bader");
        call("PUT", "/api/guides/guide-0001", o, guide("public"));
        call("PUT", "/api/guides/guide-0001/photos/1", o, JPEG);
        assertEquals(403, call("POST", "/api/guides/guide-0001/like", o, null).status(), "not your own");
        assertEquals(401, call("POST", "/api/guides/guide-0001/like", null, null).status());
        assertEquals(1, call("POST", "/api/guides/guide-0001/like", a, null).j().get("likes").asInt());
        assertEquals(1, call("POST", "/api/guides/guide-0001/like", a, null).j().get("likes").asInt(), "twice stays one");
        assertEquals(2, call("POST", "/api/guides/guide-0001/like", b, null).j().get("likes").asInt());
        JsonNode g = call("GET", "/api/guides/guide-0001", a, null).j();
        assertTrue(g.get("liked").asBoolean());
        assertFalse(call("GET", "/api/guides/guide-0001", null, null).j().get("liked").asBoolean());
        assertEquals(1, call("DELETE", "/api/guides/guide-0001/like", b, null).j().get("likes").asInt());
        assertEquals(404, call("POST", "/api/guides/guide-0001/photos/2/like", a, null).status(), "no such photo");
        assertEquals(1, call("POST", "/api/guides/guide-0001/photos/1/like", a, null).j().get("likes").asInt());
        JsonNode after = call("GET", "/api/guides/guide-0001", null, null).j();
        assertEquals(1, after.get("photoLikes").get(0).asInt());
        assertEquals(1, after.get("likes").asInt());
        assertEquals(2, call("GET", "/api/people/owner", null, null).j().get("stats").get("likes").asInt(), "guide and photo both count");
    }

    @Test
    void unseenOrBlockedMeansNotFound() throws Exception {
        String o = account("owner"), a = account("aya");
        call("PUT", "/api/guides/guide-0002", o, guide("friends"));
        assertEquals(404, call("POST", "/api/guides/guide-0002/like", a, null).status());
        call("PUT", "/api/guides/guide-0003", o, guide("public"));
        call("POST", "/api/people/aya/block", o, null);
        assertEquals(404, call("POST", "/api/guides/guide-0003/like", a, null).status());
    }

    @Test
    void likesGoWhenTheGuideDoes() throws Exception {
        String o = account("owner"), a = account("aya");
        call("PUT", "/api/guides/guide-0004", o, guide("public"));
        call("POST", "/api/guides/guide-0004/like", a, null);
        call("DELETE", "/api/guides/guide-0004", o, null);
        assertEquals(0, call("GET", "/api/people/owner", null, null).j().get("stats").get("likes").asInt());
    }

    @Test
    void feedHasFollowedPublicAndFriendsGuides() throws Exception {
        String me = account("me_a"), f1 = account("followed"), f2 = account("friend"), x = account("other");
        call("PUT", "/api/guides/guide-0010", f1, guide("public"));
        call("PUT", "/api/guides/guide-0011", f1, guide("friends"));      // a follower is not a friend
        call("PUT", "/api/guides/guide-0012", f1, guide("link"));
        call("PUT", "/api/guides/guide-0013", f2, guide("friends"));
        call("PUT", "/api/guides/guide-0014", f2, guide("public"));
        call("PUT", "/api/guides/guide-0015", x, guide("public"));        // not followed
        call("POST", "/api/people/followed/follow", me, null);
        call("POST", "/api/people/friend/friend", me, null);
        call("POST", "/api/people/me_a/friend", f2, null);
        JsonNode feed = call("GET", "/api/feed", me, null).j();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode g : feed) ids.add(g.get("id").asText());
        assertEquals(java.util.Set.of("guide-0010", "guide-0013", "guide-0014"), ids);
        assertEquals(401, call("GET", "/api/feed", null, null).status());
    }

    @Test
    void feedIsNewestFirstAndAtMostThirty() throws Exception {
        String me = account("me_a"), f = account("followed");
        call("POST", "/api/people/followed/follow", me, null);
        for (int i = 0; i < 33; i++) call("PUT", "/api/guides/guide-1" + String.format("%03d", i), f, guide("public"));
        JsonNode feed = call("GET", "/api/feed", me, null).j();
        assertEquals(30, feed.size());
        long prev = Long.MAX_VALUE;
        for (JsonNode g : feed) {
            assertTrue(g.get("updated").asLong() <= prev);
            prev = g.get("updated").asLong();
        }
    }
}
