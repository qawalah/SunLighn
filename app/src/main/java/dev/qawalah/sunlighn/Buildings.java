package dev.qawalah.sunlighn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Buildings that hide a low sun or moon (3.14, owner: a sunset went behind a block). OpenStreetMap's building outlines
 * through Overpass (free, no key, the map's own data): every building within {@link #AROUND} m of you, those in a
 * corridor along the way the body must be out to {@link #NEAR} m, and the tall ones out to {@link #FAR} m. Heights are
 * the mapped height, else the floors times 3 m, else a guess by kind (about three floors). They stand on your ground:
 * hills are the land's horizon's business. Kept in memory and in the cache folder, per spot of ~11 m and whole degree.
 * Optional: Settings, Buildings. Without a network nothing is hidden.
 */
final class Buildings {
    private Buildings() {}

    /** Off in the screen tests, so they do not depend on the network. */
    static boolean enabled = true;

    static final double AROUND = 300, NEAR = 1500, FAR = 5000, WIDTH = 40;
    /** A building this close to the subject point is the subject itself (or its own block): it never hides it. */
    static final double SUBJECT_WITHIN = 15;
    static final double FLOOR = 3.0;

    /** The setting (Settings, Buildings): count them (the default) or leave them out. */
    static boolean on(Context c) {
        return enabled && c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("buildings", true);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putBoolean("buildings", on).apply();
    }

    /** One building: its outline in metres east and north of its area's origin, its height, and whether guessed. */
    static final class B {
        final double[] e, n;
        final double height;
        final boolean guessed;
        B(double[] e, double[] n, double height, boolean guessed) {
            this.e = e;
            this.n = n;
            this.height = height;
            this.guessed = guessed;
        }
    }

    /** The buildings asked for from one spot (the origin) in one direction. */
    static final class Area {
        final double lat, lon, bearing;
        final List<B> list;
        private final double mLat, mLon;     // metres per degree here
        Area(double lat, double lon, double bearing, List<B> list) {
            this.lat = lat;
            this.lon = lon;
            this.bearing = bearing;
            this.list = list;
            mLat = Math.toRadians(1) * FrameMath.EARTH_R;
            mLon = mLat * Math.cos(Math.toRadians(lat));
        }
        double east(double lon) { return (lon - this.lon) * mLon; }
        double north(double lat) { return (lat - this.lat) * mLat; }

        /** Can it answer for this spot and direction? Its corridor is WIDTH wide, so the spot must be near its origin. */
        boolean covers(double lat, double lon, double bearing) {
            return Astro.distance(lat, lon, this.lat, this.lon) <= 20 && Math.abs(FrameMath.diff(bearing, this.bearing)) <= 1.5;
        }
    }

    /** The building that stands highest this way: how high its top is from your eye, how far, how tall. */
    static final class Hit {
        final double alt, dist, height;
        final boolean guessed;
        Hit(double alt, double dist, double height, boolean guessed) {
            this.alt = alt;
            this.dist = dist;
            this.height = height;
            this.guessed = guessed;
        }
    }

    // ---- the sums (plain Java, tested) ----

    /**
     * The highest building top the way {@code bearing} looks, from your eye at (lat, lon), or null when none is in the
     * way. The building you stand in and the subject's own (it holds the subject point, or comes within
     * {@link #SUBJECT_WITHIN} m of it; NaN for none) are left out.
     */
    static Hit horizon(Area a, double lat, double lon, double bearing, double subLat, double subLon) {
        if (a == null) return null;
        double pe = a.east(lon), pn = a.north(lat);
        double de = Math.sin(Math.toRadians(bearing)), dn = Math.cos(Math.toRadians(bearing));
        boolean skip = !Double.isNaN(subLat);
        double se = skip ? a.east(subLon) : 0, sn = skip ? a.north(subLat) : 0;
        Hit best = null;
        for (B b : a.list) {
            if (inside(b, pe, pn)) continue;                                  // you stand in it
            if (skip && (inside(b, se, sn) || distanceTo(b, se, sn) < SUBJECT_WITHIN)) continue;   // the subject
            double d = rayHit(b, pe, pn, de, dn);
            if (Double.isNaN(d)) continue;
            double alt = topAlt(b.height, d);
            if (best == null || alt > best.alt) best = new Hit(alt, d, b.height, b.guessed);
        }
        return best;
    }

    /** How high a top height metres tall stands from your eye at d metres, degrees (the earth's curve taken off). */
    static double topAlt(double height, double d) {
        double drop = d * d * (1 - FrameMath.REFRACTION_K) / (2 * FrameMath.EARTH_R);
        return Math.toDegrees(Math.atan2(height - FrameMath.EYE - drop, d));
    }

    /** How high a foot stands from your eye at d metres, degrees. */
    static double footAlt(double d) { return topAlt(0, d); }

    /** The nearest distance along the ray from (pe, pn) in direction (de, dn) that crosses the outline, or NaN. */
    static double rayHit(B b, double pe, double pn, double de, double dn) {
        double best = Double.NaN;
        int k = b.e.length;
        for (int i = 0; i < k; i++) {
            int j = (i + 1) % k;
            double ae = b.e[i], an = b.n[i], ee = b.e[j] - ae, en = b.n[j] - an;
            double den = de * en - dn * ee;
            if (Math.abs(den) < 1e-12) continue;
            double qe = ae - pe, qn = an - pn;
            double t = (qe * en - qn * ee) / den, u = (qe * dn - qn * de) / den;
            if (t > 0 && u >= 0 && u <= 1 && (Double.isNaN(best) || t < best)) best = t;
        }
        return best;
    }

    /** Is the point inside the outline (even-odd)? */
    static boolean inside(B b, double pe, double pn) {
        boolean in = false;
        int k = b.e.length;
        for (int i = 0, j = k - 1; i < k; j = i++) {
            if ((b.n[i] > pn) != (b.n[j] > pn)
                    && pe < (b.e[j] - b.e[i]) * (pn - b.n[i]) / (b.n[j] - b.n[i]) + b.e[i]) in = !in;
        }
        return in;
    }

    /** How far the point is from the outline's edge, metres. */
    static double distanceTo(B b, double pe, double pn) {
        double best = Double.MAX_VALUE;
        int k = b.e.length;
        for (int i = 0; i < k; i++) {
            int j = (i + 1) % k;
            double ee = b.e[j] - b.e[i], en = b.n[j] - b.n[i], l2 = ee * ee + en * en;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((pe - b.e[i]) * ee + (pn - b.n[i]) * en) / l2));
            double x = b.e[i] + t * ee - pe, y = b.n[i] + t * en - pn;
            best = Math.min(best, Math.sqrt(x * x + y * y));
        }
        return best;
    }

    /** A building's height from its tags: the mapped height, else floors x 3 m, else a guess by kind. {height, guessed}. */
    static double[] height(JSONObject tags) {
        double h = metres(tags.optString("height", ""));
        if (!Double.isNaN(h) && h > 0) return new double[]{h, 0};
        double floors = number(tags.optString("building:levels", ""));
        if (!Double.isNaN(floors) && floors > 0) {
            double roof = number(tags.optString("roof:levels", ""));
            return new double[]{(floors + (Double.isNaN(roof) ? 0 : roof * 0.5)) * FLOOR, 0};
        }
        switch (tags.optString("building", "yes")) {
            case "garage": case "garages": case "shed": case "carport": case "roof": case "hut": case "kiosk":
            case "service": case "toilets": case "cabin":
                return new double[]{3, 1};
            case "house": case "detached": case "semidetached_house": case "terrace": case "bungalow": case "farm":
                return new double[]{7, 1};
            default:
                return new double[]{3 * FLOOR, 1};                 // about three floors
        }
    }

    /** "25", "25 m", "25.5m", "82'" or "82 ft": metres, or NaN. */
    static double metres(String s) {
        s = s.trim().toLowerCase(Locale.US);
        if (s.isEmpty()) return Double.NaN;
        boolean feet = s.endsWith("'") || s.endsWith("ft") || s.endsWith("feet");
        double v = number(s);
        return Double.isNaN(v) ? v : feet ? v * 0.3048 : v;
    }

    private static double number(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        if (!m.find()) return Double.NaN;
        try {
            return Double.parseDouble(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Overpass's answer as buildings round the origin. Ways and the outer rings of multipolygon relations. */
    static Area parse(String json, double lat, double lon, double bearing) throws org.json.JSONException {
        List<B> out = new ArrayList<>();
        Area a = new Area(lat, lon, bearing, out);
        JSONArray els = new JSONObject(json).optJSONArray("elements");
        Set<String> seen = new HashSet<>();
        if (els != null) {
            for (int i = 0; i < els.length(); i++) {
                JSONObject el = els.getJSONObject(i);
                if (!seen.add(el.optString("type") + el.optLong("id"))) continue;     // a way in two parts of the query
                JSONObject tags = el.optJSONObject("tags");
                if (tags == null) tags = new JSONObject();
                double[] h = height(tags);
                if ("way".equals(el.optString("type"))) {
                    add(out, a, el.optJSONArray("geometry"), h);
                } else if ("relation".equals(el.optString("type"))) {
                    JSONArray ms = el.optJSONArray("members");
                    if (ms == null) continue;
                    for (int m = 0; m < ms.length(); m++) {
                        JSONObject mem = ms.getJSONObject(m);
                        if ("outer".equals(mem.optString("role"))) add(out, a, mem.optJSONArray("geometry"), h);
                    }
                }
            }
        }
        return a;
    }

    private static void add(List<B> out, Area a, JSONArray g, double[] h) throws org.json.JSONException {
        if (g == null || g.length() < 3) return;
        int k = g.length();
        JSONObject first = g.getJSONObject(0), last = g.getJSONObject(k - 1);
        if (first.optDouble("lat") == last.optDouble("lat") && first.optDouble("lon") == last.optDouble("lon")) k--;   // closed
        if (k < 3) return;
        double[] e = new double[k], n = new double[k];
        for (int i = 0; i < k; i++) {
            JSONObject q = g.getJSONObject(i);
            e[i] = a.east(q.optDouble("lon"));
            n[i] = a.north(q.optDouble("lat"));
        }
        out.add(new B(e, n, h[0], h[1] != 0));
    }

    /** The Overpass query: round you, the corridor this way, and the tall ones farther. */
    static String query(double lat, double lon, double bearing) {
        String near = corridor(lat, lon, bearing, 0, NEAR, WIDTH), far = corridor(lat, lon, bearing, NEAR, FAR, WIDTH * 1.5);
        return String.format(Locale.US, "[out:json][timeout:25];("
                + "way[\"building\"](around:%.0f,%.6f,%.6f);relation[\"building\"](around:%.0f,%.6f,%.6f);"
                + "way[\"building\"](poly:\"%s\");relation[\"building\"](poly:\"%s\");"
                + "way[\"building\"](poly:\"%s\")(if:number(t[\"height\"])>=40||number(t[\"building:levels\"])>=12);"
                + ");out tags geom;", AROUND, lat, lon, AROUND, lat, lon, near, near, far);
    }

    private static String corridor(double lat, double lon, double bearing, double from, double to, double half) {
        double[] a = Astro.destination(lat, lon, bearing, from), b = Astro.destination(lat, lon, bearing, to);
        double[][] q = {Astro.destination(a[0], a[1], bearing - 90, half), Astro.destination(b[0], b[1], bearing - 90, half),
                Astro.destination(b[0], b[1], bearing + 90, half), Astro.destination(a[0], a[1], bearing + 90, half)};
        StringBuilder s = new StringBuilder();
        for (double[] p : q) {
            if (s.length() > 0) s.append(' ');
            s.append(String.format(Locale.US, "%.6f %.6f", p[0], p[1]));
        }
        return s.toString();
    }

    // ---- asking, and remembering ----

    private static final Map<String, Area> MEMORY = new LinkedHashMap<String, Area>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Area> e) { return size() > 6; }
    };
    private static final Set<String> ASKING = new HashSet<>();
    private static final Map<String, Long> FAILED = new HashMap<>();

    private static String key(double lat, double lon, double bearing) {
        return "b_" + Math.round(lat * 1e4) + "_" + Math.round(lon * 1e4) + "_" + Math.round(Astro.norm360(bearing)) % 360;
    }

    /** The buildings that answer for this spot and direction, if they have been fetched; else null. */
    static Area get(Context c, double lat, double lon, double bearing) {
        synchronized (MEMORY) {
            for (Area a : MEMORY.values()) if (a.covers(lat, lon, bearing)) return a;
        }
        return fromDisk(c, lat, lon, bearing);
    }

    /** Any buildings fetched round here, to draw (the viewfinder while you walk): within {@code metres} of the spot. */
    static Area around(double lat, double lon, double metres) {
        Area best = null;
        double bestD = metres;
        synchronized (MEMORY) {
            for (Area a : MEMORY.values()) {
                double d = Astro.distance(lat, lon, a.lat, a.lon);
                if (d <= bestD) {
                    best = a;
                    bestD = d;
                }
            }
        }
        return best;
    }

    /** The highest building top this way, if the buildings are on and known here; else null. */
    static Hit hides(Context c, double lat, double lon, double bearing, double subLat, double subLon) {
        if (!on(c)) return null;
        return horizon(get(c, lat, lon, bearing), lat, lon, bearing, subLat, subLon);
    }

    /** Asks for the buildings in the background if they are not known; {@code done} runs on the main thread when they come. */
    static void fetch(Context c, double lat, double lon, double bearing, Runnable done) {
        if (!on(c) || get(c, lat, lon, bearing) != null) return;
        final Context app = c.getApplicationContext();
        final String k = key(lat, lon, bearing);
        synchronized (ASKING) {
            Long f = FAILED.get(k);
            if (f != null && System.currentTimeMillis() - f < 60000) return;   // not again for a minute
            if (!ASKING.add(k)) return;
        }
        new Thread(() -> {
            Area a = ask(app, lat, lon, bearing);
            synchronized (ASKING) {
                ASKING.remove(k);
                if (a == null) FAILED.put(k, System.currentTimeMillis());
            }
            if (a != null && done != null) new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    private static final String[] SERVERS = {"https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"};

    private static Area ask(Context c, double lat, double lon, double bearing) {
        String q = query(lat, lon, bearing);
        for (String server : SERVERS) {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(server).openConnection();
                h.setRequestProperty("User-Agent", Places.USER_AGENT);
                h.setConnectTimeout(10000);
                h.setReadTimeout(30000);
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = h.getOutputStream()) {
                    o.write(("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8));
                }
                if (h.getResponseCode() != 200) continue;
                String json;
                try (InputStream in = h.getInputStream()) {
                    json = Places.read(in);
                }
                Area a = parse(json, lat, lon, bearing);
                remember(key(lat, lon, bearing), a);
                toDisk(c, key(lat, lon, bearing), json);
                return a;
            } catch (Exception ignored) {
                // the next server, or nothing hidden
            }
        }
        return null;
    }

    /** For the screen tests: buildings known here without the network; null forgets them all. */
    static void keep(Area a) {
        if (a == null) synchronized (MEMORY) { MEMORY.clear(); }
        else remember(key(a.lat, a.lon, a.bearing), a);
    }

    private static void remember(String k, Area a) {
        synchronized (MEMORY) { MEMORY.put(k, a); }
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "buildings");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static void toDisk(Context c, String k, String json) {
        try (OutputStream o = new FileOutputStream(new File(dir(c), k + ".json"))) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // kept in memory only
        }
    }

    private static Area fromDisk(Context c, double lat, double lon, double bearing) {
        String k = key(lat, lon, bearing);
        File f = new File(dir(c), k + ".json");
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            Area a = parse(Places.read(in), lat, lon, bearing);
            remember(k, a);
            return a;
        } catch (Exception e) {
            return null;
        }
    }
}
