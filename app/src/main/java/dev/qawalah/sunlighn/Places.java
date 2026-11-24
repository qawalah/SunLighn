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
import java.util.Locale;
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
        int kind = -1;        // what it is, into FrameMath.KINDS, from the map's own type; -1 not known (3.25, B-05)
        double height;        // metres, from the map's height tag; 0 not known
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

    static void search(String query, Callback cb) { search(query, null, cb); }

    /** Searches by name, places near {lat, lon} first (the map's middle): "London Eye" in London before Albania. */
    static void search(String query, double[] near, Callback cb) {
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
                String box = near == null ? "" : String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                        near[1] - 0.6, near[0] + 0.4, near[1] + 0.6, near[0] - 0.4);   // a bias, not a limit
                URL u = new URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q="
                        + URLEncoder.encode(query, "UTF-8") + box + "&extratags=1");
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
            Place pl = new Place(name, detail, o.getDouble("lat"), o.getDouble("lon"));
            JSONObject x = o.optJSONObject("extratags");
            pl.height = x == null ? 0 : metres(x.optString("height", ""));
            pl.kind = kindOf(o.optString("category", o.optString("class", "")), o.optString("type", ""), x, pl.height);
            out.add(pl);
        }
        return out;
    }

    /** "330", "330 m", "324.5": metres; 0 when it is not a plain height (feet, ranges, words). */
    static double metres(String v) {
        Matcher m = Pattern.compile("^\\s*(\\d{1,4}(?:\\.\\d+)?)\\s*(m)?\\s*$").matcher(v);
        if (!m.matches()) return 0;
        double h = Double.parseDouble(m.group(1));
        return h >= 1 && h <= 2000 ? h : 0;
    }

    /**
     * What the place is, as one of the subject kinds (3.25, Ali and phase 3 B-05: every search pick was "a building
     * 30 m", the Eiffel Tower too): from the map's type, else from its height; -1 when neither says.
     */
    static int kindOf(String cls, String type, JSONObject extra, double height) {
        String manMade = extra == null ? "" : extra.optString("man_made", "");
        String t = type + "|" + manMade;
        if (t.matches(".*\\b(tower|mast|communications_tower|lighthouse|chimney|spire|minaret|obelisk|bell_tower|clock_tower)\\b.*")) return 4;
        if (cls.equals("natural") && type.matches("peak|volcano|hill|mountain_range|ridge|cliff|rock")) return 5;
        if (cls.equals("natural") && type.equals("tree")) return 1;
        if (type.matches("house|detached|bungalow|hut|cabin")) return 2;
        if (height > 0) {                               // the kind whose usual height is nearest, on a log scale
            int best = 0;
            for (int i = 0; i < FrameMath.KIND_HEIGHTS.length; i++)
                if (Math.abs(Math.log(height / FrameMath.KIND_HEIGHTS[i])) < Math.abs(Math.log(height / FrameMath.KIND_HEIGHTS[best]))) best = i;
            return best;
        }
        if (cls.equals("building") || cls.equals("amenity") || cls.equals("tourism") || cls.equals("historic")) return 3;
        return -1;
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

    /** "Jeddah": the city (or town, village, county) alone, for grouping Saved by city (3.29); null if none. */
    static String cityName(String json) {
        try {
            org.json.JSONObject a = new org.json.JSONObject(json).optJSONObject("address");
            if (a == null) return null;
            return cleanCity(first(a, null, "city", "town", "village", "municipality", "county", "state"));
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** "Riyadh Governorate" reads "Riyadh" (3.33: a cover's name should fit). */
    static String cleanCity(String c) {
        if (c == null) return null;
        String s = c.replaceAll("(?i)\\s+(governorate|province|region|municipality|district|emirate)$", "")
                .replaceAll("^(محافظة|منطقة|أمانة)\\s+", "").trim();
        return s.isEmpty() ? c : s;
    }

    /** The first of these the address has, other than {@code not}. */
    private static String first(org.json.JSONObject o, String not, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty() && !v.equals(not)) return v;
        }
        return null;
    }

    /** The place's name and its city; both null on any failure, the city null when the answer names none. */
    interface Named { void name(String nameOrNull, String cityOrNull); }

    /** Names a point by its place: one request, answered on the main thread (null on any failure). */
    static final String USER_AGENT = "SunWhere/3.15 (Android; dev.qawalah.sunlighn)";

    static void reverse(double lat, double lon, Named cb) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String name = null, city = null;
            boolean answered = false;
            try {
                java.net.URL url = new java.net.URL(String.format(java.util.Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&accept-language=en&lat=%.6f&lon=%.6f", lat, lon));
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                if (con.getResponseCode() == 200) {
                    String json = read(con.getInputStream());
                    name = placeName(json);
                    city = cityName(json);
                    answered = true;
                }
                con.disconnect();
            } catch (java.io.IOException ignored) {
            }
            final String n = name, ci = answered && city == null ? "" : city;   // "": asked, and it names no city
            main.post(() -> cb.name(n, ci));
        }).start();
    }
}
