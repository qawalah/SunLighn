package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buildings that hide a low sun or moon (3.14, owner: a sunset went behind a block). OpenStreetMap's buildings as
 * OpenFreeMap's vector map tiles (free, no key, served like map tiles from a CDN; 3.14.1: Overpass refused the
 * phone). Zoom 14 tiles, each about 2 km square, carry every building's outline and a height (the mapped height,
 * else the floors, else a stand-in of 5 m that says "not mapped": then about three floors is guessed). The tiles round
 * you and along the line are read: every building within {@link #AROUND} m of you, those in a corridor along the way the
 * body must be out to {@link #NEAR} m, and the tall ones out to {@link #FAR} m. They stand on your ground: hills are the
 * land's horizon's business. Tiles are kept in the cache folder for a month. Optional: Settings, Buildings.
 */
final class Buildings {
    private Buildings() {}

    /** Off in the screen tests, so they do not depend on the network. */
    static boolean enabled = true;

    static final double AROUND = 300, NEAR = 1500, FAR = 5000, WIDTH = 40;
    /** A building this close to the subject point is the subject itself (or its own block): it never hides it. */
    static final double SUBJECT_WITHIN = 15;
    /** A building whose height is not mapped: about three floors. */
    static final double GUESS = 9;
    static final int ZOOM = 14;

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

    /** The buildings round one spot (the origin), and along one direction. */
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

        /** How many of its buildings have a guessed height, of how many. */
        int guessed() {
            int g = 0;
            for (B b : list) if (b.guessed) g++;
            return g;
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

    /** A building between You and the subject (3.18): how far, how tall, and how much of the subject it hides. */
    static final class Cover {
        final double dist, height, hides;     // metres: to the building, its height, the subject's height hidden from its foot up
        final boolean guessed, whole;         // its height is a guess; the whole subject is hidden
        Cover(double dist, double height, double hides, boolean guessed, boolean whole) {
            this.dist = dist;
            this.height = height;
            this.hides = hides;
            this.guessed = guessed;
            this.whole = whole;
        }
    }

    /**
     * The building that hides the most of the subject from where you stand (owner, 2026-11-17: the sun clears its roof
     * but the subject cannot be seen), or null: the ray from your eye to the subject crosses it, and its top stands higher
     * than that ray would be over it. Flat ground; the building you stand in and the subject's own are left out.
     * {@code subjectHeight} 0 means not known: nothing counts as whole then.
     */
    static Cover cover(Area a, double lat, double lon, double subLat, double subLon, double subjectHeight) {
        if (a == null) return null;
        double pe = a.east(lon), pn = a.north(lat), se = a.east(subLon), sn = a.north(subLat);
        double dist = Math.hypot(se - pe, sn - pn);
        if (dist < 20) return null;
        double de = (se - pe) / dist, dn = (sn - pn) / dist;
        Cover best = null;
        for (B b : a.list) {
            if (b.height <= FrameMath.EYE) continue;
            if (inside(b, pe, pn) || inside(b, se, sn) || distanceTo(b, se, sn) < SUBJECT_WITHIN) continue;
            double d = rayHit(b, pe, pn, de, dn);
            if (Double.isNaN(d) || d >= dist - SUBJECT_WITHIN) continue;
            // the line from your eye over the building's top, at the subject: what lies under it is hidden
            double hides = FrameMath.EYE + (b.height - FrameMath.EYE) * dist / d;
            if (best == null || hides > best.hides) best = new Cover(d, b.height, hides, b.guessed, subjectHeight > 0 && hides >= subjectHeight);
        }
        return best;
    }

    /** What a cover says, in words, for the sheets and the viewfinder (""  when there is none). */
    static String coverWords(Cover c) {
        if (c == null) return "";
        String tall = (c.guessed ? "about " : "") + Fmt.height(c.height);
        return "A building " + Fmt.distance(c.dist) + " from you (" + tall + " tall) hides "
                + (c.whole ? "the subject from here" : "the subject's lower " + Fmt.height(c.hides)) + (c.guessed ? " (its height is a guess)." : ".");
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

    // ---- the tiles: Mapbox vector tiles (protobuf), read by hand, no library ----

    /** A building as the tile gives it: its outline in degrees and its height. */
    static final class Raw {
        final double[] lat, lon;
        final double height;
        final boolean guessed;
        Raw(double[] lat, double[] lon, double height, boolean guessed) {
            this.lat = lat;
            this.lon = lon;
            this.height = height;
            this.guessed = guessed;
        }
    }

    /**
     * The height a tile gives, made honest: OpenMapTiles writes 5 m for a building with no height and no floors mapped
     * (and for low ones); that is taken as "not mapped" and about three floors are guessed. {height, guessed (1/0)}.
     */
    static double[] height(double renderHeight, double renderMin) {
        if (Double.isNaN(renderHeight) || renderHeight <= 0 || (renderHeight == 5 && (Double.isNaN(renderMin) || renderMin == 0))) {
            return new double[]{GUESS, 1};
        }
        return new double[]{renderHeight, 0};
    }

    /** The buildings of tile (x, y) at {@link #ZOOM}: each outer ring is one building. */
    static List<Raw> decode(byte[] data, int x, int y) {
        List<Raw> out = new ArrayList<>();
        Pb tile = new Pb(data, 0, data.length);
        while (tile.more()) {
            int key = tile.tag();
            if ((key >>> 3) == 3 && (key & 7) == 2) {
                Pb layer = tile.sub();
                readLayer(layer, x, y, out);
            } else {
                tile.skip(key & 7);
            }
        }
        return out;
    }

    private static void readLayer(Pb layer, int tx, int ty, List<Raw> out) {
        String name = null;
        int extent = 4096;
        List<String> keys = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<int[]> features = new ArrayList<>();        // {start, end} of each feature, read once keys and values are known
        while (layer.more()) {
            int key = layer.tag(), field = key >>> 3, wire = key & 7;
            if (field == 1 && wire == 2) name = layer.string();
            else if (field == 2 && wire == 2) {
                int len = (int) layer.varint();
                features.add(new int[]{layer.p, layer.p + len});
                layer.p += len;
            } else if (field == 3 && wire == 2) keys.add(layer.string());
            else if (field == 4 && wire == 2) values.add(value(layer.sub()));
            else if (field == 5 && wire == 0) extent = (int) layer.varint();
            else layer.skip(wire);
        }
        if (!"building".equals(name)) return;
        int hKey = keys.indexOf("render_height"), mKey = keys.indexOf("render_min_height");
        double n = Math.pow(2, ZOOM);
        for (int[] fr : features) {
            Pb f = new Pb(layer.b, fr[0], fr[1]);
            double rh = Double.NaN, rm = Double.NaN;
            int type = 0;
            List<int[]> rings = null;
            while (f.more()) {
                int key = f.tag(), field = key >>> 3, wire = key & 7;
                if (field == 2 && wire == 2) {
                    Pb tags = f.sub();
                    while (tags.more()) {
                        int k = (int) tags.varint(), v = (int) tags.varint();
                        if (v < 0 || v >= values.size()) continue;
                        if (k == hKey) rh = values.get(v);
                        else if (k == mKey) rm = values.get(v);
                    }
                } else if (field == 3 && wire == 0) type = (int) f.varint();
                else if (field == 4 && wire == 2) rings = geometry(f.sub());
                else f.skip(wire);
            }
            if (type != 3 || rings == null) continue;               // polygons only
            double[] h = height(rh, rm);
            for (int[] ring : rings) {
                int k = ring.length / 2;
                if (k < 3 || area(ring) <= 0) continue;              // holes (inner rings) wind the other way
                double[] la = new double[k], lo = new double[k];
                for (int i = 0; i < k; i++) {
                    double px = (tx + ring[2 * i] / (double) extent) / n, py = (ty + ring[2 * i + 1] / (double) extent) / n;
                    lo[i] = px * 360 - 180;
                    la[i] = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * py))));
                }
                out.add(new Raw(la, lo, h[0], h[1] != 0));
            }
        }
    }

    /** The surveyor's area of a ring in tile coordinates (y down): positive for an outer ring. */
    private static double area(int[] r) {
        double s = 0;
        int k = r.length / 2;
        for (int i = 0, j = k - 1; i < k; j = i++) s += (double) r[2 * j] * r[2 * i + 1] - (double) r[2 * i] * r[2 * j + 1];
        return s;
    }

    /** The rings of a polygon's geometry: MoveTo, LineTo, ClosePath with zigzag steps. Each ring as {x0, y0, x1, y1, ...}. */
    private static List<int[]> geometry(Pb g) {
        List<int[]> rings = new ArrayList<>();
        int x = 0, y = 0;
        int[] ring = new int[64];
        int len = 0;
        while (g.more()) {
            int cmd = (int) g.varint(), id = cmd & 7, count = cmd >>> 3;
            if (id == 7) {                                  // ClosePath
                if (len >= 6) rings.add(java.util.Arrays.copyOf(ring, len));
                len = 0;
                continue;
            }
            for (int i = 0; i < count; i++) {
                x += zigzag(g.varint());
                y += zigzag(g.varint());
                if (id == 1) len = 0;                       // MoveTo: a new ring
                if (len + 2 > ring.length) ring = java.util.Arrays.copyOf(ring, ring.length * 2);
                ring[len++] = x;
                ring[len++] = y;
            }
        }
        return rings;
    }

    private static int zigzag(long v) { return (int) ((v >>> 1) ^ -(v & 1)); }

    /** A tile's value as a number (strings and booleans are not needed): NaN otherwise. */
    private static double value(Pb v) {
        double out = Double.NaN;
        while (v.more()) {
            int key = v.tag(), field = key >>> 3, wire = key & 7;
            if (field == 2 && wire == 5) out = Float.intBitsToFloat(v.fixed32());
            else if (field == 3 && wire == 1) out = Double.longBitsToDouble(v.fixed64());
            else if ((field == 4 || field == 5) && wire == 0) out = v.varint();
            else if (field == 6 && wire == 0) out = zigzag(v.varint());
            else v.skip(wire);
        }
        return out;
    }

    /** A protobuf reader over part of an array. */
    private static final class Pb {
        final byte[] b;
        int p;
        final int end;
        Pb(byte[] b, int p, int end) {
            this.b = b;
            this.p = p;
            this.end = end;
        }
        boolean more() { return p < end; }
        long varint() {
            long r = 0;
            for (int s = 0; s < 64; s += 7) {
                int c = b[p++] & 0xFF;
                r |= (long) (c & 0x7F) << s;
                if (c < 0x80) return r;
            }
            return r;
        }
        int tag() { return (int) varint(); }
        Pb sub() {
            int len = (int) varint();
            Pb q = new Pb(b, p, p + len);
            p += len;
            return q;
        }
        String string() {
            int len = (int) varint();
            String s = new String(b, p, len, java.nio.charset.StandardCharsets.UTF_8);
            p += len;
            return s;
        }
        int fixed32() {
            int r = (b[p] & 0xFF) | (b[p + 1] & 0xFF) << 8 | (b[p + 2] & 0xFF) << 16 | (b[p + 3] & 0xFF) << 24;
            p += 4;
            return r;
        }
        long fixed64() {
            long lo = fixed32() & 0xFFFFFFFFL, hi = fixed32() & 0xFFFFFFFFL;
            return lo | hi << 32;
        }
        void skip(int wire) {
            switch (wire) {
                case 0: varint(); break;
                case 1: p += 8; break;
                case 2: p += (int) varint(); break;
                case 5: p += 4; break;
                default: p = end;                           // a group or worse: nothing more can be read safely
            }
        }
    }

    // ---- which tiles, and which of their buildings ----

    static int tileX(double lon) { return (int) Math.floor((lon + 180) / 360 * Math.pow(2, ZOOM)); }

    static int tileY(double lat) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (int) Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * Math.pow(2, ZOOM));
    }

    /** The tiles round you and along the way {@code bearing} looks, out to {@link #FAR}, as {x, y}. */
    static List<int[]> tiles(double lat, double lon, double bearing) {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> out = new ArrayList<>();
        double r = AROUND + 50;
        double[][] round = {{0, 0}, {r, 0}, {r, 90}, {r, 180}, {r, 270}, {r * 1.42, 45}, {r * 1.42, 135}, {r * 1.42, 225}, {r * 1.42, 315}};
        for (double[] q : round) addTile(seen, out, Astro.destination(lat, lon, q[1], q[0]));
        for (double d = 0; d <= FAR; d += 200) {
            double[] c = Astro.destination(lat, lon, bearing, d);
            for (int side = -1; side <= 1; side += 2) addTile(seen, out, Astro.destination(c[0], c[1], bearing + 90, side * WIDTH * 1.5));
        }
        return out;
    }

    private static void addTile(Set<Long> seen, List<int[]> out, double[] p) {
        int x = tileX(p[1]), y = tileY(p[0]);
        if (seen.add((long) x << 32 | y)) out.add(new int[]{x, y});
    }

    /**
     * The buildings of these tiles that matter from (lat, lon) looking {@code bearing}: within AROUND (and a margin) of
     * you, in the corridor to NEAR, or tall ones in it to FAR.
     */
    static Area select(List<Raw> raws, double lat, double lon, double bearing) {
        List<B> list = new ArrayList<>();
        Area a = new Area(lat, lon, bearing, list);
        double de = Math.sin(Math.toRadians(bearing)), dn = Math.cos(Math.toRadians(bearing));
        for (Raw r : raws) {
            int k = r.lat.length;
            double[] e = new double[k], n = new double[k];
            double least = Double.MAX_VALUE, ce = 0, cn = 0;
            for (int i = 0; i < k; i++) {
                e[i] = a.east(r.lon[i]);
                n[i] = a.north(r.lat[i]);
                least = Math.min(least, Math.hypot(e[i], n[i]));
                ce += e[i] / k;
                cn += n[i] / k;
            }
            double along = ce * de + cn * dn, side = Math.abs(ce * dn - cn * de);
            boolean round = least <= AROUND + 100;
            boolean corridor = along > 0 && side <= WIDTH * 1.5 && (along <= NEAR || (along <= FAR && r.height >= 40));
            if (round || corridor) list.add(new B(e, n, r.height, r.guessed));
        }
        return a;
    }

    // ---- asking, and remembering ----

    private static final Map<String, Area> MEMORY = new LinkedHashMap<String, Area>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Area> e) { return size() > 6; }
    };
    private static final Map<String, List<Raw>> TILES = new LinkedHashMap<String, List<Raw>>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Raw>> e) { return size() > 12; }
    };
    private static final Set<String> ASKING = new HashSet<>();
    private static final Map<String, Long> FAILED = new HashMap<>();

    private static String key(double lat, double lon, double bearing) {
        return "b_" + Math.round(lat * 1e4) + "_" + Math.round(lon * 1e4) + "_" + Math.round(Astro.norm360(bearing)) % 360;
    }

    /** The buildings that answer for this spot and direction, if they have been read; else null. */
    static Area get(Context c, double lat, double lon, double bearing) {
        synchronized (MEMORY) {
            for (Area a : MEMORY.values()) if (a.covers(lat, lon, bearing)) return UserBuildings.merge(a);   // 3.18: the owner's too
        }
        return null;
    }

    /** Any buildings read round here, to draw (the viewfinder while you walk): within {@code metres} of the spot. */
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
        return UserBuildings.merge(best);
    }

    /** The highest building top this way, if the buildings are on and known here; else null. */
    static Hit hides(Context c, double lat, double lon, double bearing, double subLat, double subLon) {
        if (!on(c)) return null;
        return horizon(get(c, lat, lon, bearing), lat, lon, bearing, subLat, subLon);
    }

    static final int OFF = 0, ASKED = 1, FAILED_ = 2, READY = 3;

    /** Where the buildings stand for this spot and direction, so the screens can say it (3.14.1: never silent). */
    static int state(Context c, double lat, double lon, double bearing) {
        if (!on(c)) return OFF;
        if (get(c, lat, lon, bearing) != null) return READY;
        String k = key(lat, lon, bearing);
        synchronized (ASKING) {
            if (ASKING.contains(k)) return ASKED;
            Long f = FAILED.get(k);
            if (f != null && System.currentTimeMillis() - f < 60000) return FAILED_;
        }
        return ASKED;   // about to be asked
    }

    /** Asks for the buildings in the background if they are not known; {@code done} runs on the main thread after, either way. */
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
            Area a = null;
            try {
                List<Raw> raws = new ArrayList<>();
                int got = 0;
                List<int[]> want = tiles(lat, lon, bearing);
                for (int[] t : want) {
                    List<Raw> r = tile(app, t[0], t[1]);
                    if (r != null) {
                        raws.addAll(r);
                        got++;
                    }
                }
                if (got == want.size()) {
                    a = select(raws, lat, lon, bearing);
                    keep(a);
                }
            } catch (Exception ignored) {
                // nothing hidden; said as "could not be fetched"
            }
            synchronized (ASKING) {
                ASKING.remove(k);
                if (a == null) FAILED.put(k, System.currentTimeMillis());
            }
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    /** One tile's buildings: from memory, the cache folder (a month), or OpenFreeMap. Null when it cannot be had. */
    private static List<Raw> tile(Context c, int x, int y) {
        String k = x + "_" + y;
        synchronized (TILES) {
            List<Raw> r = TILES.get(k);
            if (r != null) return r;
        }
        File f = new File(dir(c), "z" + ZOOM + "_" + k + ".pbf");
        byte[] data = null;
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 86400000L) data = readFile(f);
        if (data == null) {
            data = download(c, x, y);
            if (data == null) return null;
            try (OutputStream o = new FileOutputStream(f)) {
                o.write(data);
            } catch (Exception ignored) {
                // kept in memory only
            }
        }
        List<Raw> r = decode(data, x, y);
        synchronized (TILES) { TILES.put(k, r); }
        return r;
    }

    /** The tile's address: OpenFreeMap names its weekly build in the address, given by its TileJSON (asked every 3 days). */
    private static String template(Context c, boolean fresh) {
        SharedPreferences p = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        String t = p.getString("ofm_tiles", null);
        if (!fresh && t != null && System.currentTimeMillis() - p.getLong("ofm_tiles_at", 0) < 3 * 86400000L) return t;
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://tiles.openfreemap.org/planet").openConnection();
            h.setRequestProperty("User-Agent", Places.USER_AGENT);
            h.setConnectTimeout(10000);
            h.setReadTimeout(15000);
            try (InputStream in = h.getInputStream()) {
                String s = new JSONObject(Places.read(in)).getJSONArray("tiles").getString(0);
                p.edit().putString("ofm_tiles", s).putLong("ofm_tiles_at", System.currentTimeMillis()).apply();
                return s;
            }
        } catch (Exception e) {
            return t;                                   // the last one known, if any
        }
    }

    private static byte[] download(Context c, int x, int y) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String t = template(c, attempt > 0);
            if (t == null) return null;
            try {
                String u = t.replace("{z}", "" + ZOOM).replace("{x}", "" + x).replace("{y}", "" + y);
                HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
                h.setRequestProperty("User-Agent", Places.USER_AGENT);
                h.setConnectTimeout(10000);
                h.setReadTimeout(20000);
                int code = h.getResponseCode();
                if (code == 404 || code == 410) continue;          // last week's build is gone: ask for the address again
                if (code != 200) return null;
                try (InputStream in = h.getInputStream()) {
                    byte[] b = readAll(in);
                    if (b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) {
                        b = readAll(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(b)));
                    }
                    return b;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        return o.toByteArray();
    }

    private static byte[] readFile(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** For the screen tests and the fetch: buildings known for this spot and direction; null forgets them all. */
    static void keep(Area a) {
        synchronized (MEMORY) {
            if (a == null) MEMORY.clear();
            else MEMORY.put(key(a.lat, a.lon, a.bearing), a);
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "buildings");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }
}
