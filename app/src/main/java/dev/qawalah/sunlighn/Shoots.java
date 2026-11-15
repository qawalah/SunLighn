package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Planned shoots: the moments you decided to shoot. A shoot is a reminder (the bell) plus everything needed to
 * open it again by itself: where you stand, the subject, which end of the line, the lens. It is tied to its
 * reminder by the reminder's key, so taking the bell off takes the shoot off the list, and the other way round.
 */
final class Shoots {
    private Shoots() {}

    static final class Shoot {
        String key, title, text;
        long timeMs;
        boolean moon, portrait, hasPlace;
        double youLat, youLon, subLat, subLon, height, focal = 35;
        int mode, kind = 3;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    private static JSONObject load(Context c) {
        try {
            return new JSONObject(prefs(c).getString("shoots", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** Keeps where and how a reminder's moment is shot. */
    static void put(Context c, String key, long timeMs, Trip.Line line, double focal, boolean portrait) {
        if (line == null) return;
        JSONObject all = load(c);
        try {
            all.put(key, new JSONObject().put("t", timeMs).put("yLat", line.youLat).put("yLon", line.youLon)
                    .put("sLat", line.subLat).put("sLon", line.subLon).put("mode", line.mode).put("h", line.height)
                    .put("kind", line.kind).put("focal", focal).put("portrait", portrait));
            // Forget what is long past, so the store does not grow for ever.
            long old = System.currentTimeMillis() - 2 * 86400000L;
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                String k = it.next();
                if (all.getJSONObject(k).optLong("t", 0) < old) it.remove();
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("shoots", all.toString()).apply();
    }

    /** A reminder got a new key (its line was saved under a name): its place moves with it. */
    static void rekey(Context c, String from, String to) {
        JSONObject all = load(c);
        Object o = all.remove(from);
        if (o == null) return;
        try {
            all.put(to, o);
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("shoots", all.toString()).apply();
    }

    /** Every planned shoot that is still to come, soonest first. */
    static List<Shoot> planned(Context c) {
        JSONObject all = load(c);
        List<Shoot> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Reminders.Item i : Reminders.all(c)) {
            if (i.timeMs < now) continue;
            Shoot s = new Shoot();
            s.key = i.key;
            s.timeMs = i.timeMs;
            s.title = i.title;
            s.text = i.text;
            s.moon = i.key.contains(":moon:");
            JSONObject o = all.optJSONObject(i.key);
            if (o != null) {
                s.hasPlace = true;
                s.youLat = o.optDouble("yLat");
                s.youLon = o.optDouble("yLon");
                s.subLat = o.optDouble("sLat");
                s.subLon = o.optDouble("sLon");
                s.mode = o.optInt("mode");
                s.height = o.optDouble("h", 0);
                s.kind = Math.max(0, Math.min(FrameMath.KINDS.length - 1, o.optInt("kind", 3)));
                s.focal = o.optDouble("focal", 35);
                s.portrait = o.optBoolean("portrait");
            }
            out.add(s);
        }
        out.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return out;
    }
}
