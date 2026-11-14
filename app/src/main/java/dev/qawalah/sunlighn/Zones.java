package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The time zone of a place. Looked up once per place on Open-Meteo (free, no key) and remembered; until the answer
 * comes, or without a network, the phone's own zone is used.
 */
final class Zones {
    private Zones() {}

    private static String key(double lat, double lon) {
        return "tz_" + Math.round(lat * 5) + "_" + Math.round(lon * 5);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    static boolean known(Context c, double lat, double lon) {
        return prefs(c).contains(key(lat, lon));
    }

    static TimeZone of(Context c, double lat, double lon) {
        String id = prefs(c).getString(key(lat, lon), null);
        return id == null ? TimeZone.getDefault() : TimeZone.getTimeZone(id);
    }

    /** Asks for the zone if it is not known yet; {@code done} runs on the main thread only when a new answer came. */
    static void fetch(Context c, double lat, double lon, Runnable done) {
        if (known(c, lat, lon)) return;
        final Context app = c.getApplicationContext();
        final String k = key(lat, lon);
        new Thread(() -> {
            try {
                URL u = new URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=is_day&timezone=auto", lat, lon));
                HttpURLConnection h = (HttpURLConnection) u.openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(8000);
                String id;
                try (InputStream in = h.getInputStream()) {
                    id = new JSONObject(Places.read(in)).optString("timezone", "");
                }
                if (id.isEmpty() || "GMT".equals(TimeZone.getTimeZone(id).getID()) && !id.equals("GMT")) return;
                prefs(app).edit().putString(k, id).apply();
                new Handler(Looper.getMainLooper()).post(done);
            } catch (Exception ignored) {
                // No network: the phone's zone stays, and the card says so.
            }
        }).start();
    }
}
