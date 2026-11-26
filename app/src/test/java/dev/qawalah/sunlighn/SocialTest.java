package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.content.Context;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** The phone's side of the server: nothing needs a network, the outbox waits and sends when one comes (server 0.1). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SocialTest {
    private java.net.ServerSocket server;
    private Thread acceptor;
    private final List<String> seen = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private int deadPort;
    private Context c;

    @Before public void setUp() throws Exception {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().clear().commit();
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) { deadPort = s.getLocalPort(); }   // nobody listens here
        Social.setBase(c, "http://127.0.0.1:" + deadPort);
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

    /** A tiny HTTP server on a socket (the Android classpath has no com.sun server). */
    private void serverUp() throws Exception {
        server = new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"));
        final java.net.ServerSocket mine = server;
        acceptor = new Thread(() -> {
            while (!mine.isClosed()) {
                try (java.net.Socket s = mine.accept()) {
                    java.io.InputStream in = s.getInputStream();
                    java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream();
                    int state = 0;
                    while (state < 4) {                                  // up to the blank line
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
                    String method = first[0], path = first[1];
                    synchronized (seen) {
                        seen.add(method + " " + path);
                        bodies.add(path.endsWith("card") || path.contains("/photos/") ? "[" + len + " bytes]" : new String(body, "UTF-8"));
                    }
                    String out;
                    int status;
                    if (path.equals("/api/accounts")) {
                        status = 201;
                        out = "{\"id\":\"a1\",\"name\":\"Sara\",\"token\":\"tok-tok-tok-tok-tok-tok\",\"recovery\":\"K7QM-2XPD-9TRW\"}";
                    } else if (method.equals("PUT") && path.startsWith("/api/guides/") && path.split("/").length == 4) {
                        status = 201;
                        out = "{\"id\":\"x\",\"url\":\"https://guides.test/g/" + path.substring(12) + "\"}";
                    } else {
                        status = 204;
                        out = "";
                    }
                    String reply = "HTTP/1.1 " + status + " X\r\nContent-Length: " + out.length() + "\r\nConnection: close\r\n\r\n" + out;
                    s.getOutputStream().write(reply.getBytes("UTF-8"));
                    s.getOutputStream().flush();
                } catch (Exception e) {
                    // closed while waiting: the test is over
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        Social.setBase(c, "http://127.0.0.1:" + server.getLocalPort());
    }

    private Trip.Line shot() {
        Trip.Line d = new Trip.Line("s1", "Water tower", 26.30, 50.20, 26.31, 50.21, 0);
        d.timeMs = 1_795_000_000_000L;
        d.city = "Al Khobar";
        d.subjectName = "Water tower";
        Trip.Line l = Trip.addShot(c, d);
        Trip.setCity(c, l.id, "Al Khobar");
        for (Trip.Line x : Trip.loadLines(c)) if (x.id.equals(l.id)) return x;
        return l;
    }

    private void signedIn() { c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putString("social_token", "tok-tok-tok-tok-tok-tok").putString("social_name", "Sara").commit(); }

    @Test public void offlineThePublishWaitsAndNothingBreaks() {
        signedIn();
        Trip.Line l = shot();
        Social.queue(c, l.id, "put", Social.LISTED);
        assertFalse(Social.syncNow(c));                              // no server: no crash, no loss
        assertEquals("Waiting for a network to publish.", Social.status(c, l.id));
        assertFalse(Social.published(c, l.id));
    }

    @Test public void whenANetworkComesTheOutboxGoesUp() throws Exception {
        signedIn();
        Trip.Line l = shot();
        Social.queue(c, l.id, "put", Social.LISTED);
        assertFalse(Social.syncNow(c));
        serverUp();
        assertTrue(Social.syncNow(c));
        assertTrue(Social.published(c, l.id));
        assertEquals("Published in the city's guides", Social.status(c, l.id));
        assertTrue(Social.url(c, l.id).startsWith("https://guides.test/g/"));
        assertTrue(seen.get(0).startsWith("PUT /api/guides/"));
        JSONObject body = new JSONObject(bodies.get(0));
        assertEquals("Water tower", body.getString("title"));
        assertEquals("Al Khobar", body.getString("city"));
        assertEquals("public", body.getString("level"));
        assertEquals(ShotLink.encode(l), body.getJSONArray("shots").getJSONObject(0).getString("code"));
        assertTrue("the picture follows", seen.get(1).endsWith("/card"));
        assertFalse("nothing waits any more", Social.syncNow(c));
    }

    @Test public void takingItDownWaitsToo() throws Exception {
        signedIn();
        Trip.Line l = shot();
        serverUp();
        Social.queue(c, l.id, "put", Social.LINK);
        Social.syncNow(c);
        assertEquals("Link only, not listed", Social.status(c, l.id));
        stop();
        Social.queue(c, l.id, "delete", Social.LINK);
        assertFalse(Social.syncNow(c));
        assertEquals("Waiting to be taken down when there is a network.", Social.status(c, l.id));
        seen.clear();
        serverUp();
        assertTrue(Social.syncNow(c));
        assertTrue(seen.get(0).startsWith("DELETE /api/guides/"));
        assertNull(Social.status(c, l.id));
    }

    @Test public void signedOutNothingIsSent() throws Exception {
        Trip.Line l = shot();
        serverUp();
        Social.queue(c, l.id, "put", Social.LISTED);
        assertFalse(Social.syncNow(c));
        assertTrue(seen.isEmpty());
    }

    @Test public void aRemovedShotIsDroppedNotSent() throws Exception {
        signedIn();
        Trip.Line l = shot();
        Social.queue(c, l.id, "put", Social.LISTED);
        Trip.removeShot(c, l.id);
        serverUp();
        Social.syncNow(c);
        assertTrue(seen.isEmpty());
        assertNull(Social.status(c, l.id));
    }

    @Test public void signUpKeepsTheTokenAndHandsBackTheCode() throws Exception {
        serverUp();
        String[] got = new String[2];
        Thread t = new Thread(() -> Social.signUp(c, "Sara", (err, extra) -> { got[0] = err; got[1] = extra; }));
        t.start();
        t.join();
        for (int i = 0; i < 100 && got[1] == null && got[0] == null; i++) Thread.sleep(50);
        assertNull(got[0]);
        assertEquals("K7QM-2XPD-9TRW", got[1]);
        assertTrue(Social.signedIn(c));
        assertEquals("Sara", Social.name(c));
    }

    @Test public void signUpOfflineSaysSo() throws Exception {
        String[] got = new String[1];
        Social.signUp(c, "Sara", (err, extra) -> got[0] = err == null ? "ok" : err);
        for (int i = 0; i < 100 && got[0] == null; i++) Thread.sleep(50);
        assertNotNull(got[0]);
        assertTrue(got[0], got[0].startsWith("No network"));
        assertFalse(Social.signedIn(c));
    }

    @Test public void namesAreChecked() {
        assertTrue(Social.validName("Sara"));
        assertTrue(Social.validName("سارة"));
        assertFalse(Social.validName("x"));
        assertFalse(Social.validName("<b>"));
    }
}
