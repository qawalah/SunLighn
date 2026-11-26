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

/** Follow, friends, block, network, people search (people design, task 3). */
class PeopleTest {
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Fly-Client-IP", "10.4.0." + (++n % 250));
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

    Map<String, Object> guide(String level) {
        return Map.of("title", "T", "city", "Doha", "month", 11, "year", 2026, "level", level,
                "shots", List.of(Map.of("name", "n", "code", "AbC_123-xyz")));
    }

    @Test
    void followIsInstantAndCounts() throws Exception {
        String a = account("aya"), b = account("bader");
        assertEquals(204, call("POST", "/api/people/bader/follow", a, null).status());
        JsonNode p = call("GET", "/api/people/bader", a, null).j();
        assertTrue(p.get("relation").get("following").asBoolean());
        assertEquals(1, p.get("stats").get("followers").asInt());
        assertEquals(1, call("GET", "/api/people/aya", b, null).j().get("stats").get("following").asInt());
        assertEquals(204, call("DELETE", "/api/people/bader/follow", a, null).status());
        assertEquals(0, call("GET", "/api/people/bader", a, null).j().get("stats").get("followers").asInt());
    }

    @Test
    void cannotFollowSelfOrAnUnknownHandle() throws Exception {
        String a = account("aya");
        assertEquals(400, call("POST", "/api/people/aya/follow", a, null).status());
        assertEquals(404, call("POST", "/api/people/nobody/follow", a, null).status());
        assertEquals(401, call("POST", "/api/people/aya/follow", null, null).status());
    }

    @Test
    void friendRequestAcceptDeclineCancelUnfriend() throws Exception {
        String a = account("aya"), b = account("bader"), c = account("carl");
        assertEquals("pending", call("POST", "/api/people/bader/friend", a, null).j().get("state").asText());
        JsonNode bp = call("GET", "/api/people/aya", b, null).j();
        assertTrue(bp.get("relation").get("pending_in").asBoolean());
        assertEquals("friends", call("POST", "/api/people/aya/friend", b, null).j().get("state").asText());
        assertTrue(call("GET", "/api/people/bader", a, null).j().get("relation").get("friend").asBoolean());
        assertEquals(1, call("GET", "/api/people/bader", a, null).j().get("stats").get("friends").asInt());
        assertEquals(204, call("DELETE", "/api/people/bader/friend", a, null).status());          // unfriend
        assertFalse(call("GET", "/api/people/bader", a, null).j().get("relation").get("friend").asBoolean());
        // cancel
        call("POST", "/api/people/carl/friend", a, null);
        assertTrue(call("GET", "/api/people/carl", a, null).j().get("relation").get("pending_out").asBoolean());
        assertEquals(204, call("DELETE", "/api/people/carl/friend", a, null).status());
        assertFalse(call("GET", "/api/people/carl", a, null).j().get("relation").get("pending_out").asBoolean());
        // decline
        call("POST", "/api/people/carl/friend", a, null);
        assertEquals(204, call("DELETE", "/api/people/aya/friend", c, null).status());
        assertFalse(call("GET", "/api/people/carl", a, null).j().get("relation").get("pending_out").asBoolean());
        // both ask each other: they are friends at once
        call("POST", "/api/people/carl/friend", a, null);
        assertEquals("friends", call("POST", "/api/people/aya/friend", c, null).j().get("state").asText());
        assertEquals(400, call("POST", "/api/people/aya/friend", a, null).status());
    }

    @Test
    void friendsSeeFriendsGuides() throws Exception {
        String a = account("aya"), b = account("bader"), s = account("stella");
        call("PUT", "/api/guides/guide-0001", a, guide("friends"));
        call("POST", "/api/people/aya/friend", b, null);
        assertEquals(404, call("GET", "/api/guides/guide-0001", b, null).status(), "a request is not a friendship");
        call("POST", "/api/people/bader/friend", a, null);
        assertEquals(200, call("GET", "/api/guides/guide-0001", b, null).status());
        assertEquals(404, call("GET", "/api/guides/guide-0001", s, null).status());
        call("POST", "/api/people/aya/follow", s, null);
        assertEquals(404, call("GET", "/api/guides/guide-0001", s, null).status(), "a follower is not a friend");
    }

    @Test
    void blockCutsBothWays() throws Exception {
        String a = account("aya"), b = account("bader");
        call("POST", "/api/people/bader/follow", a, null);
        call("POST", "/api/people/aya/follow", b, null);
        call("POST", "/api/people/bader/friend", a, null);
        call("POST", "/api/people/aya/friend", b, null);
        call("PUT", "/api/guides/guide-0001", b, guide("public"));
        assertEquals(204, call("POST", "/api/people/bader/block", a, null).status());
        assertEquals(404, call("GET", "/api/people/bader", a, null).status());
        assertEquals(404, call("GET", "/api/people/aya", b, null).status());
        assertEquals(404, call("GET", "/api/guides/guide-0001", a, null).status());
        assertEquals(200, call("GET", "/api/guides/guide-0001", null, null).status(), "strangers still see a public guide");
        assertEquals(404, call("POST", "/api/people/aya/follow", b, null).status());
        assertEquals(404, call("POST", "/api/people/aya/friend", b, null).status());
        JsonNode net = call("GET", "/api/me/network", a, null).j();
        assertEquals(0, net.get("following").size() + net.get("followers").size() + net.get("friends").size());
        assertEquals(1, net.get("blocked").size());
        assertEquals(204, call("DELETE", "/api/people/bader/block", a, null).status());
        assertEquals(200, call("GET", "/api/people/bader", a, null).status());
        assertFalse(call("GET", "/api/people/bader", a, null).j().get("relation").get("following").asBoolean(), "unblocking does not bring the follow back");
    }

    @Test
    void searchByHandlePrefix() throws Exception {
        String a = account("aya");
        for (int i = 0; i < 25; i++) account("sun_" + (char) ('a' + i % 26) + i);
        account("moon_x");
        JsonNode r = call("GET", "/api/people?q=sun_", a, null).j();
        assertEquals(20, r.size());
        assertTrue(r.get(0).has("handle") && r.get(0).has("name"));
        assertEquals(1, call("GET", "/api/people?q=moon", a, null).j().size());
        assertEquals(0, call("GET", "/api/people?q=ay", a, null).j().size(), "never yourself");
        assertEquals(400, call("GET", "/api/people?q=a", a, null).status(), "at least 2 characters");
    }

    @Test
    void networkListsEverything() throws Exception {
        String a = account("aya"), b = account("bader"), c = account("carl"), d = account("dina");
        call("POST", "/api/people/bader/follow", a, null);
        call("POST", "/api/people/aya/follow", c, null);
        call("POST", "/api/people/carl/friend", a, null);
        call("POST", "/api/people/carl/friend", d, null);
        call("POST", "/api/people/aya/friend", c, null);                   // a and c are friends
        call("POST", "/api/people/aya/friend", d, null);                   // d asks a
        JsonNode n = call("GET", "/api/me/network", a, null).j();
        assertEquals("bader", n.get("following").get(0).get("handle").asText());
        assertEquals("carl", n.get("followers").get(0).get("handle").asText());
        assertEquals("carl", n.get("friends").get(0).get("handle").asText());
        assertEquals("dina", n.get("requests_in").get(0).get("handle").asText());
        assertNotNull(b);
    }

    @Test
    void limitsPerDay() throws Exception {
        String a = account("aya");
        String b = account("bader");
        int refused = 0;
        for (int i = 0; i < 105; i++) {
            int s = call(i % 2 == 0 ? "POST" : "DELETE", "/api/people/bader/follow", a, null).status();
            if (s == 429) refused++;
        }
        assertTrue(refused >= 5, "refused " + refused);
        assertNotNull(b);
    }
}
