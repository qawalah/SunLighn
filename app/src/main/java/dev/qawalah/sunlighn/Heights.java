package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The ground's height above the sea, where you stand and where the subject stands. Asked of Open-Meteo's elevation
 * service (free, no key, a 90 m terrain model: the ground, not buildings), up to 100 points in one request, and
 * remembered per spot of about 11 m. Without a network, or until the answer comes, the ground is taken as level.
 */
final class Heights {
    private Heights() {}

    /** Off in the screen tests, so they do not depend on the network. */
    static boolean enabled = true;

    private static final Set<String> ASKING = new HashSet<>();

    private static String key(double lat, double lon) {
        return "el_" + Math.round(lat * 1e4) + "_" + Math.round(lon * 1e4);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    /** The ground's height at a point in metres, or NaN when it is not known (yet). */
    static double of(Context c, double lat, double lon) {
        String k = key(lat, lon);
        SharedPreferences p = prefs(c);
        return p.contains(k) ? p.getFloat(k, 0) : Double.NaN;
    }

    static boolean known(Context c, double lat, double lon) {
        return prefs(c).contains(key(lat, lon));
    }

    /**
     * How much higher the subject's foot is than your eye, in metres: the two grounds, less your eye height. With
     * either ground unknown both are taken as level, so it is minus the eye height.
     */
    static double rise(Context c, double youLat, double youLon, double subLat, double subLon) {
        double you = of(c, youLat, youLon), sub = of(c, subLat, subLon);
        if (Double.isNaN(you) || Double.isNaN(sub)) return -FrameMath.EYE;
        return FrameMath.rise(sub, you);
    }

    /** The two grounds' difference (subject minus you), or NaN when not known. For the words on Your shot. */
    static double difference(Context c, double youLat, double youLon, double subLat, double subLon) {
        return of(c, subLat, subLon) - of(c, youLat, youLon);
    }

    /** Asks for the points not known yet, in the background; {@code done} runs on the main thread if any came. */
    static void fetch(Context c, double[] lats, double[] lons, Runnable done) {
        if (!enabled) return;
        final Context app = c.getApplicationContext();
        List<double[]> want = unknown(app, lats, lons);
        if (want.isEmpty()) return;
        synchronized (ASKING) {
            for (int i = want.size() - 1; i >= 0; i--) {
                if (!ASKING.add(key(want.get(i)[0], want.get(i)[1]))) want.remove(i);
            }
        }
        if (want.isEmpty()) return;
        new Thread(() -> {
            boolean got = ask(app, want);
            synchronized (ASKING) { for (double[] q : want) ASKING.remove(key(q[0], q[1])); }
            if (got && done != null) new Handler(Looper.getMainLooper()).post(done);
        }).start();
    }

    /** The same, waiting for the answer: for work that already runs on a background thread. */
    static void fetchNow(Context c, double[] lats, double[] lons) {
        if (!enabled) return;
        List<double[]> want = unknown(c.getApplicationContext(), lats, lons);
        if (!want.isEmpty()) ask(c.getApplicationContext(), want);
    }

    private static List<double[]> unknown(Context c, double[] lats, double[] lons) {
        List<double[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lats.length; i++) {
            String k = key(lats[i], lons[i]);
            if (!known(c, lats[i], lons[i]) && seen.add(k)) out.add(new double[]{lats[i], lons[i]});
        }
        return out;
    }

    /** One request per 100 points. True if any height came back. */
    private static boolean ask(Context c, List<double[]> points) {
        boolean got = false;
        for (int from = 0; from < points.size(); from += 100) {
            List<double[]> part = points.subList(from, Math.min(points.size(), from + 100));
            StringBuilder la = new StringBuilder(), lo = new StringBuilder();
            for (double[] q : part) {
                if (la.length() > 0) { la.append(','); lo.append(','); }
                la.append(String.format(Locale.US, "%.5f", q[0]));
                lo.append(String.format(Locale.US, "%.5f", q[1]));
            }
            try {
                HttpURLConnection h = (HttpURLConnection) new URL("https://api.open-meteo.com/v1/elevation?latitude="
                        + la + "&longitude=" + lo).openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(8000);
                JSONArray e;
                try (InputStream in = h.getInputStream()) {
                    e = new JSONObject(Places.read(in)).optJSONArray("elevation");
                }
                if (e == null || e.length() != part.size()) continue;
                SharedPreferences.Editor ed = prefs(c).edit();
                for (int i = 0; i < part.size(); i++) {
                    double m = e.optDouble(i, Double.NaN);
                    if (Double.isNaN(m) || m < -500 || m > 9000) continue;   // the sea answers 0; nonsense is skipped
                    ed.putFloat(key(part.get(i)[0], part.get(i)[1]), (float) m);
                    got = true;
                }
                ed.apply();
            } catch (Exception ignored) {
                // No network: the ground stays level, and Your shot does not claim otherwise.
            }
        }
        return got;
    }
}
