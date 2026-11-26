package dev.qawalah.sunlighn;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny HTTP server on a socket for tests (the Android classpath has no com.sun server). Answers by "METHOD /path?query". */
final class StubServer {
    private final ServerSocket socket;
    private final Map<String, String[]> routes = new HashMap<>();
    final List<String> seen = new ArrayList<>();
    final List<String> bodies = new ArrayList<>();

    StubServer() throws Exception {
        socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::loop, "stub");
        t.setDaemon(true);
        t.start();
    }

    String url() { return "http://127.0.0.1:" + socket.getLocalPort(); }

    /** The answer to "GET /api/me" and the like. A status of 204 sends no body. */
    StubServer on(String methodAndPath, int status, String body) {
        synchronized (routes) { routes.put(methodAndPath, new String[]{String.valueOf(status), body}); }
        return this;
    }

    boolean saw(String methodAndPath) {
        synchronized (seen) { return seen.contains(methodAndPath); }
    }

    void stop() {
        try {
            socket.close();
        } catch (java.io.IOException ignored) {
        }
    }

    private void loop() {
        while (!socket.isClosed()) {
            try (Socket s = socket.accept()) {
                InputStream in = s.getInputStream();
                ByteArrayOutputStream head = new ByteArrayOutputStream();
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
                String key = first[0] + " " + first[1];
                synchronized (seen) {
                    seen.add(key);
                    bodies.add(new String(body, "UTF-8"));
                }
                String[] r;
                synchronized (routes) { r = routes.get(key); }
                int status = r == null ? 404 : Integer.parseInt(r[0]);
                String out = r == null ? "{\"error\":\"Not found.\"}" : status == 204 ? "" : r[1];
                byte[] payload = out.getBytes("UTF-8");
                String reply = "HTTP/1.1 " + status + " X\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n\r\n";
                s.getOutputStream().write(reply.getBytes("UTF-8"));
                s.getOutputStream().write(payload);
                s.getOutputStream().flush();
            } catch (Exception e) {
                // closed while waiting: the test is over
            }
        }
    }
}
