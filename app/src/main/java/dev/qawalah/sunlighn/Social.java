package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * The phone's side of the SunWhere server (server 0.1, 2026-11-26; owner: "for the time being also allow offline use").
 * Offline first: nothing here is needed to use the app, nothing waits for the network on the screen. A shot is published
 * by putting it in the <b>outbox</b>; the outbox is sent whenever there is a network (on opening the app, when a network
 * comes back, after a press) and an entry that could not be sent stays until it can. Accounts have no email: a name,
 * a token kept here, a recovery code shown once. Private shots are never sent.
 */
final class Social {
    private Social() {}

    /** Where the server lives; the owner changes it in Settings (an address, no trailing slash). */
    static final String DEFAULT_BASE = "https://sunwhere.fly.dev";

    /** Publish levels (server 0.2). 3.36 phones stored "listed"; it reads as public. */
    static final String LISTED = "public", LINK = "link", FRIENDS = "friends";
    private static final int CARD_MAX = 3_000_000, PHOTO_MAX = 3_000_000;

    private static final Object LOCK = new Object();
    private static boolean running;
    /** The last thing the owner can act on, said in Settings and on Your shot; null when all is well. */
    static volatile String lastError;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE); }

    // ------------------------------------------------------------------ settings and account

    static String base(Context c) {
        String b = prefs(c).getString("social_base", DEFAULT_BASE);
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b.isEmpty() ? DEFAULT_BASE : b;
    }

    static void setBase(Context c, String b) {
        b = b == null ? "" : b.trim();
        if (!b.isEmpty() && !b.contains("://")) b = "https://" + b;
        prefs(c).edit().putString("social_base", b).apply();
    }

    static String name(Context c) { return prefs(c).getString("social_name", ""); }

    /** The signed-in handle as last seen from the server ("" until People has asked). */
    static String handle(Context c) { return prefs(c).getString("social_handle", ""); }

    static boolean signedIn(Context c) { return !prefs(c).getString("social_token", "").isEmpty(); }

    /** Forgets the account on this phone (the server keeps it; the recovery code brings it back). */
    static void signOut(Context c) {
        // the next account on this phone must not see this one's people (the cached lists, the handle)
        prefs(c).edit().remove("social_token").remove("social_name").remove("social_handle").remove("people_cache").apply();
    }

    interface Done {
        /** {@code error} is null on success; {@code extra} is the recovery code after a sign-up. */
        void done(String error, String extra);
    }

    static boolean validName(String n) {
        return n != null && n.trim().matches("^[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{1,29}$");
    }

    /** Makes an account (needs a network, once). The recovery code comes back to show once. */
    static void signUp(Context c, String name, Done cb) {
        Context app = c.getApplicationContext();
        async(() -> {
            String err = null, recovery = null;
            try {
                Reply r = call(app, "POST", "/api/accounts", null, new JSONObject().put("name", name.trim()).toString().getBytes(StandardCharsets.UTF_8), "application/json");
                if (r.status == 201) {
                    JSONObject o = new JSONObject(r.text());
                    prefs(app).edit().putString("social_token", o.getString("token")).putString("social_name", o.getString("name")).apply();
                    recovery = o.getString("recovery");
                } else err = r.error("The account could not be made.");
            } catch (IOException e) {
                err = "No network. Try again when you are online; the app works fully without an account.";
            } catch (JSONException e) {
                err = "The server's answer was not understood.";
            }
            cb.done(err, recovery);
        });
    }

    /** A new phone: the name and the recovery code give the account back. */
    static void recover(Context c, String name, String code, Done cb) {
        Context app = c.getApplicationContext();
        async(() -> {
            String err = null;
            try {
                Reply r = call(app, "POST", "/api/accounts/recover", null,
                        new JSONObject().put("name", name.trim()).put("recovery", code.trim()).toString().getBytes(StandardCharsets.UTF_8), "application/json");
                if (r.status == 200) {
                    JSONObject o = new JSONObject(r.text());
                    prefs(app).edit().putString("social_token", o.getString("token")).putString("social_name", o.getString("name")).apply();
                } else err = r.error("The name and the code do not match.");
            } catch (IOException e) {
                err = "No network. Try again when you are online.";
            } catch (JSONException e) {
                err = "The server's answer was not understood.";
            }
            cb.done(err, null);
        });
    }

    /** Deletes the account and everything it published (needs a network); then forgets it here. */
    static void deleteAccount(Context c, Done cb) {
        Context app = c.getApplicationContext();
        async(() -> {
            String err = null;
            try {
                Reply r = call(app, "DELETE", "/api/me", token(app), null, null);
                if (r.status == 204 || r.status == 401) {
                    signOut(app);
                    prefs(app).edit().remove("social_outbox").remove("social_done").apply();
                } else err = r.error("The account could not be deleted.");
            } catch (IOException e) {
                err = "No network. Deleting needs one.";
            }
            cb.done(err, null);
        });
    }

    private static String token(Context c) { return prefs(c).getString("social_token", ""); }

    // ------------------------------------------------------------------ the outbox

    /** What Your shot says about a shot: null when it was never published, else one line. */
    static String status(Context c, String shotId) {
        synchronized (LOCK) {
            JSONObject pending = find(outbox(c), shotId);
            if (pending != null) return pending.optString("action").equals("delete")
                    ? "Waiting to be taken down when there is a network." : "Waiting for a network to publish.";
            JSONObject d = done(c).optJSONObject(shotId);
            if (d == null) return null;
            String lv = level(d.optString("level"));
            return lv.equals(LISTED) ? "Published in the city's guides" : lv.equals(FRIENDS) ? "Published for friends only" : "Link only, not listed";
        }
    }

    /** A stored level, with 3.36's "listed" read as public. */
    static String level(String stored) { return "listed".equals(stored) ? LISTED : stored == null || stored.isEmpty() ? LINK : stored; }

    /** The guide's page for a published shot, or null. */
    static String url(Context c, String shotId) {
        synchronized (LOCK) {
            JSONObject d = done(c).optJSONObject(shotId);
            return d == null ? null : d.optString("url", null);
        }
    }

    static boolean published(Context c, String shotId) {
        synchronized (LOCK) {
            return done(c).has(shotId) && find(outbox(c), shotId) == null;
        }
    }

    /** Publish (or change the level of) a saved shot: it is in the outbox at once and sent when a network allows. */
    static void publish(Context c, Trip.Line shot, String level, Runnable changed) {
        queue(c, shot.id, "put", level);
        sync(c, changed);
    }

    /** Take a published shot down; waits for a network like publishing does. */
    static void unpublish(Context c, String shotId, Runnable changed) {
        queue(c, shotId, "delete", LINK);
        sync(c, changed);
    }

    static void queue(Context c, String shotId, String action, String level) {
        synchronized (LOCK) {
            JSONArray out = outbox(c);
            JSONArray keep = new JSONArray();
            for (int i = 0; i < out.length(); i++) if (!shotId.equals(out.optJSONObject(i).optString("id"))) keep.put(out.optJSONObject(i));
            try {
                keep.put(new JSONObject().put("id", shotId).put("action", action).put("level", level));
            } catch (JSONException ignored) {
            }
            prefs(c).edit().putString("social_outbox", keep.toString()).apply();
        }
    }

    /** Sends what is waiting, off the screen's thread. Safe to call as often as you like. */
    static void sync(Context c, Runnable changed) {
        Context app = c.getApplicationContext();
        async(() -> {
            if (syncNow(app) && changed != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(changed);
        });
    }

    /** One pass over the outbox, on this thread. True when something was sent or dropped. */
    static boolean syncNow(Context c) {
        synchronized (LOCK) {
            if (running) return false;
            running = true;
        }
        boolean moved = false;
        try {
            if (!signedIn(c)) return false;
            JSONArray out;
            synchronized (LOCK) { out = outbox(c); }
            for (int i = 0; i < out.length(); i++) {
                JSONObject e = out.optJSONObject(i);
                if (e == null) continue;
                int result;
                try {
                    result = e.optString("action").equals("delete") ? sendDelete(c, e) : sendPut(c, e);
                } catch (IOException offline) {
                    return moved;                                   // no network: all that is left waits
                }
                if (result == 401) {
                    lastError = "Your account was not accepted. Sign in again in Settings.";
                    return moved;
                }
                finish(c, e.optString("id"));
                moved = true;
            }
            if (moved) lastError = null;
        } finally {
            synchronized (LOCK) { running = false; }
        }
        return moved;
    }

    /** 0 sent, 4xx something wrong with this shot (said, dropped), 401 not signed in; IOException: offline. */
    private static int sendPut(Context c, JSONObject e) throws IOException {
        Trip.Line l = null;
        for (Trip.Line x : Trip.loadLines(c)) if (x.id.equals(e.optString("id"))) l = x;
        if (l == null) return 0;                                    // the shot was removed: nothing to publish
        String level = level(e.optString("level", LINK));
        String guideId = guideId(c, l.id);
        String tok = token(c);
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(l.timeMs > 0 ? l.timeMs : System.currentTimeMillis());
        JSONObject body = new JSONObject();
        try {
            body.put("title", l.name == null || l.name.isEmpty() ? "Shot" : l.name);
            body.put("city", l.city == null || l.city.isEmpty() ? "Other places" : l.city);
            body.put("month", cal.get(Calendar.MONTH) + 1);
            body.put("year", cal.get(Calendar.YEAR));
            body.put("summary", (l.moon ? "Moon " : "Sun ") + l.where() + (l.subjectName == null ? "" : " of " + l.subjectName) + ".");
            body.put("level", level);
            body.put("listed", LISTED.equals(level));              // a 0.1 server reads only this flag
            body.put("shots", new JSONArray().put(new JSONObject().put("name", l.name == null ? "Shot" : l.name).put("code", ShotLink.encode(l))));
        } catch (JSONException ex) {
            return 0;
        }
        Reply r = call(c, "PUT", "/api/guides/" + guideId, tok, body.toString().getBytes(StandardCharsets.UTF_8), "application/json");
        if (r.status == 401) return 401;
        if (r.status == 429 || r.status >= 500) throw new IOException("server busy: " + r.status);
        if (r.status != 200 && r.status != 201) {
            lastError = r.error("The server did not take this shot.");
            return 400;
        }
        // the picture the link shows, then the photos: a failure here is a network failure (the entry stays)
        byte[] card = card(c, l);
        if (card != null && card.length <= CARD_MAX) mediaPut(c, "/api/guides/" + guideId + "/card", tok, card, "image/png");
        List<File> photos = Photos.list(c, l.id);
        for (int i = 0; i < photos.size() && i < Photos.MAX; i++) {
            if (photos.get(i).length() > PHOTO_MAX) continue;
            mediaPut(c, "/api/guides/" + guideId + "/photos/" + (i + 1), tok, Files.readAllBytes(photos.get(i).toPath()), "image/jpeg");
        }
        try {
            String url = new JSONObject(r.text()).optString("url", base(c) + "/g/" + guideId);
            synchronized (LOCK) {
                JSONObject d = done(c);
                d.put(l.id, new JSONObject().put("guide", guideId).put("url", url).put("level", level));
                prefs(c).edit().putString("social_done", d.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
        return 0;
    }

    private static int sendDelete(Context c, JSONObject e) throws IOException {
        String shot = e.optString("id");
        JSONObject d;
        synchronized (LOCK) { d = done(c).optJSONObject(shot); }
        if (d == null) return 0;
        Reply r = call(c, "DELETE", "/api/guides/" + d.optString("guide"), token(c), null, null);
        if (r.status == 401) return 401;
        if (r.status == 429 || r.status >= 500) throw new IOException("server busy");
        synchronized (LOCK) {                                       // gone, or never there (404): either way it is down
            JSONObject all = done(c);
            all.remove(shot);
            prefs(c).edit().putString("social_done", all.toString()).apply();
        }
        return 0;
    }

    private static void mediaPut(Context c, String path, String tok, byte[] bytes, String type) throws IOException {
        Reply r = call(c, "PUT", path, tok, bytes, type);
        if (r.status == 429 || r.status >= 500) throw new IOException("server busy");
        // a 400 (too big, not the right kind) is left: the guide still stands without that picture
    }

    private static byte[] card(Context c, Trip.Line l) {
        try {
            ShareCard.Data d = new ShareCard.Data();
            d.title = l.name == null || l.name.isEmpty() ? "Shot" : l.name;
            d.city = l.city == null || l.city.isEmpty() ? null : l.city;
            d.moon = l.moon;
            d.moment = (l.moon ? "Moon " : "Sun ") + l.where() + (l.timeMs > 0 ? " · " + Fmt.when(c, l.timeMs, TimeZone.getDefault()) : "");
            d.detail = l.subjectName != null && !l.subjectName.equals(d.title) ? l.subjectName : null;
            d.hasLine = true;
            d.youLat = l.youLat; d.youLon = l.youLon; d.subLat = l.subLat; d.subLon = l.subLon;
            List<File> ph = Photos.list(c, l.id);
            if (!ph.isEmpty()) d.photo = Photos.decode(ph.get(0), 1600);
            android.graphics.Bitmap b = ShareCard.shot(c, d, false);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
            return o.toByteArray();
        } catch (Throwable t) {                                      // a card that cannot be drawn must not stop the guide
            CrashLog.caught(c, "social card", t);
            return null;
        }
    }

    /** The shot's guide id on the server: the same for ever, made once. */
    private static String guideId(Context c, String shotId) {
        synchronized (LOCK) {
            JSONObject d = done(c).optJSONObject(shotId);
            if (d != null && !d.optString("guide").isEmpty()) return d.optString("guide");
            SharedPreferences p = prefs(c);
            JSONObject ids;
            try {
                ids = new JSONObject(p.getString("social_ids", "{}"));
            } catch (JSONException e) {
                ids = new JSONObject();
            }
            String id = ids.optString(shotId, "");
            if (id.isEmpty()) {
                id = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                try {
                    ids.put(shotId, id);
                } catch (JSONException ignored) {
                }
                p.edit().putString("social_ids", ids.toString()).apply();
            }
            return id;
        }
    }

    private static void finish(Context c, String shotId) {
        synchronized (LOCK) {
            JSONArray out = outbox(c), keep = new JSONArray();
            for (int i = 0; i < out.length(); i++) if (!shotId.equals(out.optJSONObject(i).optString("id"))) keep.put(out.optJSONObject(i));
            prefs(c).edit().putString("social_outbox", keep.toString()).apply();
        }
    }

    private static JSONArray outbox(Context c) {
        try {
            return new JSONArray(prefs(c).getString("social_outbox", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static JSONObject done(Context c) {
        try {
            return new JSONObject(prefs(c).getString("social_done", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONObject find(JSONArray a, String id) {
        for (int i = 0; i < a.length(); i++) if (id.equals(a.optJSONObject(i).optString("id"))) return a.optJSONObject(i);
        return null;
    }

    // ------------------------------------------------------------------ people, likes, feed (server 0.2)

    /** An answer on the main thread: the server's words as {@code error} (null when all is well), the JSON (a JSONObject or a
     *  JSONArray; null for "no content" or when there is nothing to show), and whether it is the last good answer from the
     *  cache because there was no network. */
    interface Answer {
        void answer(String error, Object json, boolean cached);
    }

    static final String NEEDS_NETWORK = "Needs a network";

    static void profile(Context c, String handle, Answer cb) { get(c, "/api/people/" + enc(handle), cb); }

    static void me(Context c, Answer cb) { get(c, "/api/me", cb); }

    static void network(Context c, Answer cb) { get(c, "/api/me/network", cb); }

    static void feed(Context c, Answer cb) { get(c, "/api/feed", cb); }

    static void cities(Context c, Answer cb) { get(c, "/api/cities", cb); }

    static void cityGuides(Context c, String slug, Answer cb) { get(c, "/api/cities/" + enc(slug) + "/guides", cb); }

    static void search(Context c, String q, Answer cb) { get(c, "/api/people?q=" + urlEncode(q.trim()), cb); }

    static void saveProfile(Context c, String handle, String name, String bio, Answer cb) {
        JSONObject b = new JSONObject();
        try {
            if (handle != null) b.put("handle", handle);
            if (name != null) b.put("name", name);
            if (bio != null) b.put("bio", bio);
        } catch (JSONException ignored) {
        }
        act(c, "PUT", "/api/me/profile", b, cb);
    }

    static void follow(Context c, String handle, Answer cb) { act(c, "POST", "/api/people/" + enc(handle) + "/follow", null, cb); }

    static void unfollow(Context c, String handle, Answer cb) { act(c, "DELETE", "/api/people/" + enc(handle) + "/follow", null, cb); }

    static void befriend(Context c, String handle, Answer cb) { act(c, "POST", "/api/people/" + enc(handle) + "/friend", null, cb); }

    static void unfriend(Context c, String handle, Answer cb) { act(c, "DELETE", "/api/people/" + enc(handle) + "/friend", null, cb); }

    static void block(Context c, String handle, Answer cb) { act(c, "POST", "/api/people/" + enc(handle) + "/block", null, cb); }

    static void unblock(Context c, String handle, Answer cb) { act(c, "DELETE", "/api/people/" + enc(handle) + "/block", null, cb); }

    /** Like the guide (photo 0) or one of its photos (1 to 3). */
    static void like(Context c, String guide, int photo, Answer cb) { act(c, "POST", likePath(guide, photo), null, cb); }

    static void unlike(Context c, String guide, int photo, Answer cb) { act(c, "DELETE", likePath(guide, photo), null, cb); }

    private static String likePath(String guide, int photo) {
        return "/api/guides/" + enc(guide) + (photo > 0 ? "/photos/" + photo : "") + "/like";
    }

    /** A path part. The "UTF-8" String overload: the Charset one is Android 13 (minSdk is 24; ApiLevelTest guards it). */
    private static String enc(String s) { return urlEncode(s).replace("+", "%20"); }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);                    // UTF-8 is always there
        }
    }

    /** The last good answer for a path, or null: what People shows with "Not updated" when there is no network. */
    static Object cached(Context c, String path) {
        try {
            String raw = new JSONObject(prefs(c).getString("people_cache", "{}")).optString(path, "");
            if (raw.isEmpty()) return null;
            return raw.startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    private static void remember(Context c, String path, String raw) {
        synchronized (LOCK) {
            try {
                JSONObject all = new JSONObject(prefs(c).getString("people_cache", "{}"));
                if (all.length() > 60) all = new JSONObject();           // small: a handful of screens
                all.put(path, raw);
                prefs(c).edit().putString("people_cache", all.toString()).apply();
            } catch (JSONException ignored) {
            }
        }
    }

    /** A read: answers from the server and keeps it; with no network, the last good answer and "Needs a network". */
    private static void get(Context c, String path, Answer cb) {
        Context app = c.getApplicationContext();
        if (!signedIn(app)) {
            toMain(() -> cb.answer("Make an account in Settings, Sharing online.", cached(app, path), cached(app, path) != null));
            return;
        }
        async(() -> {
            String error = null;
            Object json = null;
            boolean cache = false;
            try {
                Reply r = call(app, "GET", path, token(app), null, null);
                if (r.status == 200) {
                    json = parse(r.text());
                    remember(app, path, r.text());
                    if (path.equals("/api/me") && json instanceof JSONObject)
                        prefs(app).edit().putString("social_handle", ((JSONObject) json).optString("handle")).apply();
                } else error = failure(r);
            } catch (IOException e) {
                error = NEEDS_NETWORK;
                json = cached(app, path);
                cache = json != null;
            }
            final String er = error;
            final Object js = json;
            final boolean ca = cache;
            toMain(() -> cb.answer(er, js, ca));
        });
    }

    /** A change: needs the network, never cached. */
    private static void act(Context c, String method, String path, JSONObject body, Answer cb) {
        Context app = c.getApplicationContext();
        if (!signedIn(app)) {
            toMain(() -> cb.answer("Make an account in Settings, Sharing online.", null, false));
            return;
        }
        async(() -> {
            String error = null;
            Object json = null;
            try {
                Reply r = call(app, method, path, token(app), body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8),
                        body == null ? null : "application/json");
                if (r.status >= 200 && r.status < 300) json = r.body.length == 0 ? null : parse(r.text());
                else error = failure(r);
            } catch (IOException e) {
                error = NEEDS_NETWORK;
            }
            final String er = error;
            final Object js = json;
            toMain(() -> cb.answer(er, js, false));
        });
    }

    private static String failure(Reply r) {
        return r.status == 401 ? "Your account was not accepted. Sign in again in Settings." : r.error("The server could not do that.");
    }

    private static Object parse(String text) {
        try {
            return text.trim().startsWith("[") ? new JSONArray(text) : new JSONObject(text);
        } catch (JSONException e) {
            return null;
        }
    }

    private static void toMain(Runnable r) { new android.os.Handler(android.os.Looper.getMainLooper()).post(r); }

    // ------------------------------------------------------------------ plumbing

    /** A server's answer. */
    static final class Reply {
        int status;
        byte[] body = new byte[0];

        String text() { return new String(body, StandardCharsets.UTF_8); }

        /** The server's own sentence when it sent one. */
        String error(String fallback) {
            try {
                return new JSONObject(text()).optString("error", fallback);
            } catch (JSONException e) {
                return fallback;
            }
        }
    }

    /** One request. Throws IOException when the server cannot be reached (offline, no route, timeout). */
    static Reply call(Context c, String method, String path, String token, byte[] body, String type) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(base(c) + path).openConnection();
        h.setRequestMethod(method);
        h.setConnectTimeout(8000);
        h.setReadTimeout(20000);
        h.setRequestProperty("User-Agent", Places.USER_AGENT);
        if (token != null && !token.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            h.setDoOutput(true);
            h.setFixedLengthStreamingMode(body.length);
            h.setRequestProperty("Content-Type", type);
            try (OutputStream o = h.getOutputStream()) {
                o.write(body);
            }
        }
        Reply r = new Reply();
        r.status = h.getResponseCode();
        InputStream in = r.status >= 400 ? h.getErrorStream() : h.getInputStream();
        if (in != null) {
            try (InputStream i = in) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                r.body = o.toByteArray();
            }
        }
        h.disconnect();
        return r;
    }

    private static void async(Runnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Throwable ignored) {                           // the network side never closes the app
            }
        }, "social");
        t.setDaemon(true);
        t.start();
    }
}
