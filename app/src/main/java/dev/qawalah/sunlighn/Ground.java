package dev.qawalah.sunlighn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What is on the ground at a point: land you can stand on, water, or a building. Read from the colour of the
 * standard OpenStreetMap picture of that place (the same map the app shows), so it needs no other service and
 * knows the sea, rivers and lakes alike. It is a good guess, not a survey: without a network the answer is
 * UNKNOWN and nothing is ruled out.
 */
final class Ground {
    private Ground() {}

    static final int UNKNOWN = 0, LAND = 1, WATER = 2, BUILDING = 3;
    static final int OVERLAY = 4;   // a line or a name drawn over the ground: says nothing about it

    /** What is on the ground at a point (one of the above). Called off the main thread. */
    interface Fn { int at(double lat, double lon); }
    private static final int ZOOM = 15;          // about 4 m a pixel: one picture covers a square kilometre

    private static final Map<String, Bitmap> TILES = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 24; }
    };

    /** The colours the standard map paints water and buildings in. */
    static int classify(int argb) {
        if (near(argb, 0xAAD3DF, 14)) return WATER;
        if (near(argb, 0xD9D0C9, 9) || near(argb, 0xC4B9AE, 9)) return BUILDING;
        // Over a river the map draws ferry routes, piers' names and their soft edges: violet lines (OVERLAY, left out
        // of the count) and pale blues blended with the water. No land is painted blue, so a blue tint is water.
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (b - r >= 20 && b >= g) return b - g >= 30 ? OVERLAY : WATER;
        return LAND;
    }

    private static boolean near(int c, int rgb, int tol) {
        return Math.abs(((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF)) <= tol && Math.abs(((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF)) <= tol
                && Math.abs((c & 0xFF) - (rgb & 0xFF)) <= tol;
    }

    /** From a small patch of the map round the point: water or building if most of it is, else land. */
    static int verdict(int water, int building, int total) {
        if (total == 0) return UNKNOWN;
        if (water * 10 >= total * 4) return WATER;
        if (building * 2 >= total) return BUILDING;
        return LAND;
    }

    /** Asks the map. Blocks on the network the first time a square is needed: call it off the main thread. */
    static int at(double lat, double lon, String userAgent) {
        double n = Math.pow(2, ZOOM);
        double fx = (lon + 180) / 360 * n;
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        double fy = (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n;
        int tx = (int) Math.floor(fx), ty = (int) Math.floor(fy);
        Bitmap b = tile(tx, ty, userAgent);
        if (b == null) return UNKNOWN;
        int px = (int) ((fx - tx) * b.getWidth()), py = (int) ((fy - ty) * b.getHeight());
        int water = 0, building = 0, total = 0;
        for (int dx = -3; dx <= 3; dx++) {          // about 25 m square round the point
            for (int dy = -3; dy <= 3; dy++) {
                int x = px + dx, y = py + dy;
                if (x < 0 || y < 0 || x >= b.getWidth() || y >= b.getHeight()) continue;
                int k = classify(b.getPixel(x, y));
                if (k == OVERLAY) continue;
                total++;
                if (k == WATER) water++;
                else if (k == BUILDING) building++;
            }
        }
        return verdict(water, building, total);
    }

    private static Bitmap tile(int x, int y, String userAgent) {
        String key = x + "/" + y;
        synchronized (TILES) {
            Bitmap b = TILES.get(key);
            if (b != null) return b;
        }
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + ZOOM + "/" + key + ".png")
                    .openConnection();
            h.setRequestProperty("User-Agent", userAgent);
            h.setConnectTimeout(6000);
            h.setReadTimeout(6000);
            try (InputStream in = h.getInputStream()) {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null) synchronized (TILES) { TILES.put(key, b); }
                return b;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
