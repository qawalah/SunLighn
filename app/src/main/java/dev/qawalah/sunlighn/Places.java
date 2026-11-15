package dev.qawalah.sunlighn;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finding a place by name: OpenStreetMap's Nominatim (free, no key). Its rules: one search per press, never while
 * typing, and say who is asking. Coordinates typed as "51.5, -0.12" are understood without the network.
 */
final class Places {
    private Places() {}

    static final class Place {
        final String name, detail;
        final double lat, lon;
        Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail;
            this.lat = lat;
            this.lon = lon;
        }
    }

    interface Callback {
        /** {@code places} is null when the search could not be made (no network). */
        void done(List<Place> places);
    }

    private static final Pattern COORDS = Pattern.compile("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,;\\s]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$");

    /** "51.5, -0.12" as a place, or null if the text is not a pair of coordinates. */
    static Place coordinates(String text) {
        Matcher m = COORDS.matcher(text);
        if (!m.matches()) return null;
        double lat = Double.parseDouble(m.group(1)), lon = Double.parseDouble(m.group(2));
        if (Math.abs(lat) > 85 || Math.abs(lon) > 180) return null;
        return new Place(m.group(1) + ", " + m.group(2), "Coordinates", lat, lon);
    }

    static void search(String query, Callback cb) {
        Place typed = coordinates(query);
        if (typed != null) {
            List<Place> one = new ArrayList<>();
            one.add(typed);
            cb.done(one);
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Place> out = null;
            try {
                URL u = new URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q="
                        + URLEncoder.encode(query, "UTF-8"));
                HttpURLConnection h = (HttpURLConnection) u.openConnection();
                h.setRequestProperty("User-Agent", USER_AGENT);
                h.setConnectTimeout(8000);
                h.setReadTimeout(8000);
                try (InputStream in = h.getInputStream()) {
                    out = parse(read(in));
                }
            } catch (Exception ignored) {
            }
            final List<Place> result = out;
            main.post(() -> cb.done(result));
        }).start();
    }

    static List<Place> parse(String json) throws org.json.JSONException {
        List<Place> out = new ArrayList<>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String full = o.optString("display_name", "");
            String name = o.optString("name", "");
            if (name.isEmpty()) name = full.contains(",") ? full.substring(0, full.indexOf(',')) : full;
            String detail = full.startsWith(name + ", ") ? full.substring(name.length() + 2) : full;
            out.add(new Place(name, detail, o.getDouble("lat"), o.getDouble("lon")));
        }
        return out;
    }

    static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    /** "Al Shati, Jeddah": the neighbourhood and the city from a Nominatim reverse answer; null if it has neither. */
    static String placeName(String json) {
        try {
            org.json.JSONObject a = new org.json.JSONObject(json).optJSONObject("address");
            if (a == null) return null;
            String near = first(a, null, "suburb", "neighbourhood", "quarter", "village", "town", "hamlet");
            String city = first(a, near, "city", "town", "municipality", "county", "state");
            if (near == null) return city;
            return city == null ? near : near + ", " + city;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** The first of these the address has, other than {@code not}. */
    private static String first(org.json.JSONObject o, String not, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty() && !v.equals(not)) return v;
        }
        return null;
    }

    interface Named { void name(String nameOrNull); }

    /** Names a point by its place: one request, answered on the main thread (null on any failure). */
    static final String USER_AGENT = "SunLighn/3.0 (Android; dev.qawalah.sunlighn)";

    static void reverse(double lat, double lon, Named cb) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String name = null;
            try {
                java.net.URL url = new java.net.URL(String.format(java.util.Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&lat=%.6f&lon=%.6f", lat, lon));
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                if (con.getResponseCode() == 200) name = placeName(read(con.getInputStream()));
                con.disconnect();
            } catch (java.io.IOException ignored) {
            }
            final String n = name;
            main.post(() -> cb.name(n));
        }).start();
    }
}
