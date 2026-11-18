package dev.qawalah.sunlighn;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Buildings the owner added (3.18: "a missing 3D building"): OpenStreetMap is thin in places (in Al Khobar none has a
 * height). Each is a box: a place, a width and a depth, a turn, a height. They are kept on this phone only, count in
 * the sums like the mapped ones (they are merged into every {@link Buildings.Area} handed out), and are drawn on the
 * map and in the viewfinder. Plain geometry here; the rest is in Buildings and MainActivity.
 */
final class UserBuildings {
    private UserBuildings() {}

    static final class Mine {
        final double lat, lon, width, depth, turn, height;   // metres, turn clockwise from north, degrees
        Mine(double lat, double lon, double width, double depth, double turn, double height) {
            this.lat = lat;
            this.lon = lon;
            this.width = width;
            this.depth = depth;
            this.turn = turn;
            this.height = height;
        }
    }

    private static final List<Mine> LIST = new ArrayList<>();
    private static int version;
    /** Far ones are left out of a merge: the areas reach 5 km. */
    static final double REACH = 5000;

    static synchronized List<Mine> all() { return new ArrayList<>(LIST); }

    static synchronized int version() { return version; }

    static synchronized void load(Context c) {
        LIST.clear();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getString("my_buildings", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LIST.add(new Mine(o.getDouble("lat"), o.getDouble("lon"), o.getDouble("w"), o.getDouble("d"),
                        o.getDouble("t"), o.getDouble("h")));
            }
        } catch (JSONException ignored) {
        }
        version++;
    }

    private static void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Mine m : LIST) {
                a.put(new JSONObject().put("lat", m.lat).put("lon", m.lon).put("w", m.width).put("d", m.depth)
                        .put("t", m.turn).put("h", m.height));
            }
        } catch (JSONException ignored) {
        }
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putString("my_buildings", a.toString()).apply();
    }

    static synchronized void add(Context c, Mine m) {
        LIST.add(m);
        version++;
        save(c);
    }

    static synchronized void remove(Context c, Mine m) {
        if (LIST.remove(m)) {
            version++;
            save(c);
        }
    }

    /** The corners as metres east and north of the centre, turned clockwise by {@code turn}. */
    static double[][] corners(Mine m) {
        double t = Math.toRadians(m.turn), cos = Math.cos(t), sin = Math.sin(t);
        double[] xs = {-m.width / 2, m.width / 2, m.width / 2, -m.width / 2}, ys = {-m.depth / 2, -m.depth / 2, m.depth / 2, m.depth / 2};
        double[] e = new double[4], n = new double[4];
        for (int i = 0; i < 4; i++) {
            e[i] = xs[i] * cos + ys[i] * sin;
            n[i] = -xs[i] * sin + ys[i] * cos;
        }
        return new double[][]{e, n};
    }

    /** The corners as {lat, lon} pairs, for drawing. */
    static double[][] outline(Mine m) {
        double[][] c = corners(m);
        double mLat = Math.toRadians(1) * FrameMath.EARTH_R, mLon = mLat * Math.cos(Math.toRadians(m.lat));
        double[][] out = new double[4][];
        for (int i = 0; i < 4; i++) out[i] = new double[]{m.lat + c[1][i] / mLat, m.lon + c[0][i] / mLon};
        return out;
    }

    /** The building the owner added that holds this point (within {@code slack} metres of it), or null. */
    static Mine at(double lat, double lon, double slack) {
        double mLat = Math.toRadians(1) * FrameMath.EARTH_R;
        for (Mine m : all()) {
            double mLon = mLat * Math.cos(Math.toRadians(m.lat));
            double e = (lon - m.lon) * mLon, n = (lat - m.lat) * mLat, t = Math.toRadians(m.turn);
            // into the box's own frame (undo the turn)
            double x = e * Math.cos(t) - n * Math.sin(t), y = e * Math.sin(t) + n * Math.cos(t);
            if (Math.abs(x) <= m.width / 2 + slack && Math.abs(y) <= m.depth / 2 + slack) return m;
        }
        return null;
    }

    private static Buildings.Area lastBase, lastMerged;
    private static int lastVersion = -1;

    /** The area with the buildings added here in it (the same object while nothing changed, so callers can compare). */
    static synchronized Buildings.Area merge(Buildings.Area a) {
        if (a == null || LIST.isEmpty()) return a;
        if (a == lastBase && lastVersion == version) return lastMerged;
        List<Buildings.B> l = new ArrayList<>(a.list);
        for (Mine m : LIST) {
            if (Astro.distance(a.lat, a.lon, m.lat, m.lon) > REACH) continue;
            double[][] c = corners(m);
            double ce = a.east(m.lon), cn = a.north(m.lat);
            double[] e = new double[4], n = new double[4];
            for (int i = 0; i < 4; i++) {
                e[i] = ce + c[0][i];
                n[i] = cn + c[1][i];
            }
            l.add(new Buildings.B(e, n, m.height, false));
        }
        lastBase = a;
        lastVersion = version;
        lastMerged = new Buildings.Area(a.lat, a.lon, a.bearing, l);
        return lastMerged;
    }
}
