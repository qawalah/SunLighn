package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/** The phone's calls for people, likes and the feed, and the cache that answers offline (people design, task 7). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SocialPeopleTest {
    private java.net.ServerSocket server;
    private final List<String> seen = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private String reply = "{}";
    private int replyStatus = 200;
    private Context c;

    @Before public void setUp() throws Exception {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit()
                .putString("social_token", "tok-tok-tok-tok-tok-tok").putString("social_name", "Sara").commit();
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) { Social.setBase(c, "http://127.0.0.1:" + s.getLocalPort()); }
        Social.lastError = null;
    }

    @After public void tearDown() { stop(); }

    private void stop() {
        try {
            if (server != null) server.close();
        } catch (java.io.IOException ignored) {
        }
        server = null;
    }

    private void serverUp() throws Exception {
        server = new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"));
        final java.net.ServerSocket mine = server;
        Thread t = new Thread(() -> {
            while (!mine.isClosed()) {
                try (java.net.Socket s = mine.accept()) {
                    java.io.InputStream in = s.getInputStream();
                    java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream();
                    int state = 0;
                    while (state < 4) {
                        int ch = in.read();
                        if (ch < 0) break;
                        head.write(ch);
                        state = (ch == '\r' && (state == 0 || state == 2)) || (ch == '\n' && (state == 1 || state == 3)) ? state + 1 : (ch == '\r' ? 1 : 0);
                    }
                    String h = head.toString("UTF-8");
                    String[] first = h.split("\r\n")[0].split(" ");
                    int len = 0;
                    for (String line : h.split("\r\n")) if (line.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
                    byte[] body = new byte[len];
                    int got = 0;
                    while (got < len) {
                        int n = in.read(body, got, len - got);
                        if (n < 0) break;
                        got += n;
                    }
                    synchronized (seen) {
                        seen.add(first[0] + " " + first[1]);
                        bodies.add(new String(body, "UTF-8"));
                    }
                    String out = replyStatus == 204 ? "" : reply;
                    String r = "HTTP/1.1 " + replyStatus + " X\r\nContent-Length: " + out.length() + "\r\nConnection: close\r\n\r\n" + out;
                    s.getOutputStream().write(r.getBytes("UTF-8"));
                    s.getOutputStream().flush();
                } catch (Exception e) {
                    // closed: the test is over
                }
            }
        });
        t.setDaemon(true);
        t.start();
        Social.setBase(c, "http://127.0.0.1:" + server.getLocalPort());
    }

    /** What an answer came to, waited for on the main looper. */
    private static final class Got {
        volatile boolean done;
        volatile String error;
        volatile Object json;
        volatile boolean cached;
    }

    private Got waitFor(Got g) throws Exception {
        for (int i = 0; i < 200 && !g.done; i++) {
            Thread.sleep(25);
            ShadowLooper.idleMainLooper();
        }
        assertTrue("no answer", g.done);
        return g;
    }

    private Social.Answer into(Got g) {
        return (err, json, cached) -> {
            g.error = err;
            g.json = json;
            g.cached = cached;
            g.done = true;
        };
    }

    @Test public void offlineAnswersFromCache() throws Exception {
        serverUp();
        reply = "{\"handle\":\"aya\",\"name\":\"Aya\",\"stats\":{\"guides\":2}}";
        Got first = new Got();
        Social.profile(c, "aya", into(first));
        waitFor(first);
        assertNull(first.error);
        assertFalse(first.cached);
        assertEquals("aya", ((JSONObject) first.json).getString("handle"));
        stop();
        Social.setBase(c, "http://127.0.0.1:9");
        Got second = new Got();
        Social.profile(c, "aya", into(second));
        waitFor(second);
        assertEquals("Needs a network", second.error);
        assertTrue(second.cached);
        assertEquals(2, ((JSONObject) second.json).getJSONObject("stats").getInt("guides"));
        Got none = new Got();
        Social.profile(c, "carl", into(none));                          // never asked before: nothing to show
        waitFor(none);
        assertEquals("Needs a network", none.error);
        assertNull(none.json);
    }

    @Test public void levelIsSent() throws Exception {
        serverUp();
        replyStatus = 201;
        reply = "{\"id\":\"x\",\"url\":\"https://guides.test/g/x\"}";
        Trip.Line d = new Trip.Line("s1", "Water tower", 26.30, 50.20, 26.31, 50.21, 0);
        d.timeMs = 1_795_000_000_000L;
        Trip.Line l = Trip.addShot(c, d);
        Social.queue(c, l.id, "put", Social.FRIENDS);
        Social.syncNow(c);
        JSONObject body = new JSONObject(bodies.get(0));
        assertEquals("friends", body.getString("level"));
        assertEquals("Published for friends only", Social.status(c, l.id));
    }

    @Test public void followCallsTheRightRoutes() throws Exception {
        serverUp();
        replyStatus = 204;
        String[][] calls = {{"follow", "POST /api/people/aya/follow"}, {"unfollow", "DELETE /api/people/aya/follow"},
                {"befriend", "POST /api/people/aya/friend"}, {"unfriend", "DELETE /api/people/aya/friend"},
                {"block", "POST /api/people/aya/block"}, {"unblock", "DELETE /api/people/aya/block"}};
        for (String[] call : calls) {
            Got g = new Got();
            switch (call[0]) {
                case "follow": Social.follow(c, "aya", into(g)); break;
                case "unfollow": Social.unfollow(c, "aya", into(g)); break;
                case "befriend": Social.befriend(c, "aya", into(g)); break;
                case "unfriend": Social.unfriend(c, "aya", into(g)); break;
                case "block": Social.block(c, "aya", into(g)); break;
                default: Social.unblock(c, "aya", into(g));
            }
            waitFor(g);
            assertNull(call[0], g.error);
            assertEquals(call[1], seen.get(seen.size() - 1));
        }
        Got like = new Got();
        replyStatus = 200;
        reply = "{\"likes\":3,\"liked\":true}";
        Social.like(c, "guide-0001", 2, into(like));
        waitFor(like);
        assertEquals("POST /api/guides/guide-0001/photos/2/like", seen.get(seen.size() - 1));
        assertEquals(3, ((JSONObject) like.json).getInt("likes"));
        Got unlike = new Got();
        Social.unlike(c, "guide-0001", 0, into(unlike));
        waitFor(unlike);
        assertEquals("DELETE /api/guides/guide-0001/like", seen.get(seen.size() - 1));
    }

    @Test public void listsComeAsArraysAndErrorsAreTheServersWords() throws Exception {
        serverUp();
        reply = "[{\"handle\":\"aya\",\"name\":\"Aya\"}]";
        Got s = new Got();
        Social.search(c, "ay a", into(s));
        waitFor(s);
        assertEquals("GET /api/people?q=ay+a", seen.get(seen.size() - 1));
        assertEquals(1, ((JSONArray) s.json).length());
        replyStatus = 404;
        reply = "{\"error\":\"Not found.\"}";
        Got p = new Got();
        Social.profile(c, "nobody", into(p));
        waitFor(p);
        assertEquals("Not found.", p.error);
        assertFalse(p.cached);
    }

    @Test public void signOutForgetsThePeopleData() throws Exception {
        serverUp();
        reply = "{\"followers\":[{\"handle\":\"x\",\"name\":\"X\"}]}";
        Got g = new Got();
        Social.network(c, into(g));
        waitFor(g);
        assertNotNull(Social.cached(c, "/api/me/network"));
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putString("social_handle", "sara").commit();
        Social.signOut(c);
        assertNull("the next account must not see this one's network", Social.cached(c, "/api/me/network"));
        assertEquals("", Social.handle(c));
    }

    @Test public void signedOutSaysSo() throws Exception {
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().remove("social_token").commit();
        Got g = new Got();
        Social.network(c, into(g));
        waitFor(g);
        assertEquals("Make an account in Settings, Sharing online.", g.error);
    }
}
