package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Trip plan: pick the days you are away and the lines you want to shoot, and see every sun and moon moment
 * on those lines in those days, in order, each with a bell for a reminder. Saved as you go.
 */
final class Trip extends FrameLayout {
    static final int MAX_DAYS = 14;

    /** A line kept in the trip. Its id ties reminders to it; the name is only for people. */
    static final class Line {
        final String id;
        String name;
        final double youLat, youLon, subLat, subLon;
        final int mode; // 0 = behind subject, 1 = behind you
        double height;  // the subject's height in metres, 0 = not known
        int kind = 3;   // what the subject is, into FrameMath.KINDS
        long timeMs;    // a shot: the moment it is for; 0 = any time (a line saved without a moment)
        boolean moon;   // the shot is of the moon
        boolean named = true; // false while it is still "Shot 3", waiting for a place name
        String subjectName;           // the subject's own name ("Big Ben"), apart from the shot's name (3.3)
        double panDeg = 0;            // the framing from Build the shot (3.3): degrees off the subject
        double pitch = Double.NaN;    // its tilt; NaN: the usual tilt (shots from the other tools, and before 3.3)
        int tool = Flow.LINE;         // the tool it was made in (3.25, phase 3 B-14): it reopens there
        String city;                  // the subject's city (3.29): null = not asked yet, "" = asked, none named

        Line(String id, String name, double youLat, double youLon, double subLat, double subLon, int mode) {
            this.id = id;
            this.name = name;
            this.youLat = youLat;
            this.youLon = youLon;
            this.subLat = subLat;
            this.subLon = subLon;
            this.mode = mode;
        }

        double bearing() {
            double b = Astro.bearing(youLat, youLon, subLat, subLon);
            return mode == 0 ? b : Astro.norm360(b + 180);
        }

        String where() {
            return mode == 0 ? "behind subject" : "behind you";
        }

        /**
         * Where the body is at the shot's moment (3.25, phase 3 B-15): on the line, or how far off it (a time moved in the
         * viewfinder). Build the shot frames the body off the line on purpose, so its shots keep the plain words.
         */
        String whereAt() {
            if (timeMs > 0 && tool != Flow.BUILD) {
                double[] pos = moon ? Astro.moon(timeMs, youLat, youLon) : Astro.sun(timeMs, youLat, youLon);
                double off = Math.abs(FrameMath.diff(pos[0], bearing()));
                if (off >= 0.6) return Math.round(off) + "° off the line";
            }
            return where();
        }
    }

    /** What the trip needs from the map screen. */
    interface Host {
        /** The line on the map right now, or null. Its id is the trip line's id if it came from the trip. */
        Line mapLine();
        void showOnMap(Line l);
        /** Show the line on the map and open the Frame at this moment. */
        void showMoment(Line l, long timeMs, boolean moon);
        /** The line on the map was just added to the trip as l: from now on it shares l's id. */
        void mapLineIs(Line l);
        /** {@code line}: where the moment is shot, kept with it as a planned shoot; null when taking one off. */
        void toggleReminder(Reminders.Item item, Line line, Runnable changed);
        /** Open a shot: its line, and Your shot at its moment (or the times, for a shot with no moment). */
        void openShot(Line shot);
        /** Ask for a place name for a shot still called "Shot 3" (one request, if there is a network). */
        void nameShot(Line shot);
        void closeTrip();
        /** Saved, Open a file (3.28): a .sunwhere file picked on the phone. */
        void openFile();
        /** Saved, Send as file (3.28): these shots in one .sunwhere file, through the share menu. */
        void sendShots(List<Line> shots);
    }

    private final Ui.Palette p;
    private final Host host;
    private final SharedPreferences prefs;
    private final List<Line> lines = new ArrayList<>();
    private int tab;   // 0 = Shots, 1 = Trip
    private long startDay; // local midnight of the first day
    private int days;

    private final LinearLayout body;
    private int insetTop, insetBottom;
    private final ScrollView scroll;

    private int generation;
    private List<Moment> moments; // null while working
    private Line removed;
    private int removedAt;
    private List<Reminders.Item> removedReminders;

    private static final class Moment {
        final Line line;
        final boolean moon;
        final Astro.Hit hit;
        Moment(Line line, boolean moon, Astro.Hit hit) {
            this.line = line;
            this.moon = moon;
            this.hit = hit;
        }
    }

    Trip(Context c, Ui.Palette p, Host host) {
        super(c);
        this.p = p;
        this.host = host;
        prefs = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        setBackgroundColor(p.bg);
        setClickable(true); // the map underneath must not get taps
        load();
        // Open where there is something to see: the planned shoots if any, else the lines.
        tab = Math.max(0, Math.min(1, prefs.getInt("saved_tab3", 0)));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        addView(scroll, new LayoutParams(-1, -1));
        applyPadding();
        render();
        compute();
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyPadding();
    }

    private void applyPadding() {
        int s = Ui.dp(getContext(), 16);
        body.setPadding(s, insetTop + Ui.dp(getContext(), 12), s, insetBottom + Ui.dp(getContext(), 24));
    }

    /** The map line may have changed while the trip was closed; call when it opens. */
    void refresh() {
        render();
    }

    // ---- saving as you go ----

    private void load() {
        Calendar today = midnight(System.currentTimeMillis());
        startDay = prefs.getLong("trip_start", today.getTimeInMillis());
        if (startDay < today.getTimeInMillis()) startDay = today.getTimeInMillis(); // a trip in the past moves to today
        days = Math.max(1, Math.min(MAX_DAYS, prefs.getInt("trip_days", 3)));
        lines.clear();
        lines.addAll(loadLines(getContext()));
    }

    private void save() {
        saveLines(getContext(), lines);
        prefs.edit().putLong("trip_start", startDay).putInt("trip_days", days).apply();
    }

    // ---- shots: saved lines with a moment, kept as you go ----

    static List<Line> loadLines(Context c) {
        List<Line> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getString("trip_lines", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Line l = new Line(o.getString("id"), o.getString("name"), o.getDouble("yLat"), o.getDouble("yLon"),
                        o.getDouble("sLat"), o.getDouble("sLon"), o.getInt("mode"));
                l.height = o.optDouble("h", 0);
                l.kind = Math.max(0, Math.min(FrameMath.KINDS.length - 1, o.optInt("kind", 3)));
                l.timeMs = o.optLong("t", 0);
                l.moon = o.optBoolean("moon", false);
                l.named = o.optBoolean("named", true);
                l.subjectName = o.has("subj") ? o.optString("subj") : null;
                l.panDeg = o.optDouble("pan", 0);
                l.pitch = o.has("pitch") ? o.optDouble("pitch", Double.NaN) : Double.NaN;
                l.tool = o.optInt("tool", Flow.LINE);
                l.city = o.has("city") ? o.optString("city", "") : null;
                out.add(l);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static void saveLines(Context c, List<Line> lines) {
        JSONArray a = new JSONArray();
        try {
            for (Line l : lines) {
                JSONObject o = new JSONObject().put("id", l.id).put("name", l.name).put("yLat", l.youLat).put("yLon", l.youLon)
                        .put("sLat", l.subLat).put("sLon", l.subLon).put("mode", l.mode).put("h", l.height).put("kind", l.kind)
                        .put("t", l.timeMs).put("moon", l.moon).put("named", l.named);
                if (l.subjectName != null) o.put("subj", l.subjectName);
                if (l.panDeg != 0 && !Double.isNaN(l.panDeg)) o.put("pan", l.panDeg);   // a NaN would drop every shot after it
                if (!Double.isNaN(l.pitch)) o.put("pitch", l.pitch);
                if (l.tool != Flow.LINE) o.put("tool", l.tool);
                if (l.city != null) o.put("city", l.city);
                a.put(o);
            }
        } catch (JSONException ignored) {
        }
        c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putString("trip_lines", a.toString()).apply();
    }

    /** The name a shot gets once its place is known: its own subject's name first, if it has one. */
    static String shotName(String current, String place) {
        if (current == null || current.isEmpty() || current.matches("Shot \\d+") || place.startsWith(current)) return place;
        return current + ", " + place;
    }

    /**
     * A name no other shot has: "Millbank, City of Westminster 2" for the second shot there. Two saved shots with
     * one name could not be told apart in Saved or in the Trip (Ali, 2026-11-15).
     */
    static String uniqueName(List<Line> lines, String id, String name) {
        for (int n = 1; ; n++) {
            String s = n == 1 ? name : name + " " + n;
            boolean used = false;
            for (Line l : lines) used |= !l.id.equals(id) && s.equalsIgnoreCase(l.name);
            if (!used) return s;
        }
    }

    /** "Shot 3": the first number no shot uses yet. */
    static String nextShotName(List<Line> lines) {
        for (int n = 1; ; n++) {
            String s = "Shot " + n;
            boolean used = false;
            for (Line l : lines) used |= s.equalsIgnoreCase(l.name);
            if (!used) return s;
        }
    }

    /** Saves a shot at once, under its own name if it has one, else "Shot N" until a place name comes. */
    static Line addShot(Context c, Line from) {
        List<Line> all = loadLines(c);
        boolean has = from.name != null && !from.name.isEmpty();
        Line l = new Line(UUID.randomUUID().toString(), has ? from.name : nextShotName(all),
                from.youLat, from.youLon, from.subLat, from.subLon, from.mode);
        l.height = from.height;
        l.kind = from.kind;
        l.timeMs = from.timeMs;
        l.moon = from.moon;
        l.named = has && from.named;
        l.subjectName = from.subjectName;
        l.panDeg = from.panDeg;      // the framing goes with the shot (3.3)
        l.pitch = from.pitch;
        all.add(l);
        saveLines(c, all);
        return l;
    }

    /** The framing of a saved shot, as it is on screen now (3.4). */
    static void setFraming(Context c, String id, double panDeg, double pitch) {
        List<Line> all = loadLines(c);
        for (Line l : all) {
            if (!l.id.equals(id)) continue;
            l.panDeg = panDeg;
            l.pitch = pitch;
        }
        saveLines(c, all);
    }

    static void setCity(Context c, String id, String city) {
        List<Line> all = loadLines(c);
        for (Line l : all) if (l.id.equals(id)) l.city = city;
        saveLines(c, all);
    }

    /** The heading a shot sits under in Saved (3.29): its city, or why there is none. */
    static String cityOf(Line l) {
        if (l.city == null) return "Place not known yet";
        return l.city.isEmpty() ? "Other places" : l.city;
    }

    /**
     * The shots by city (3.29, owner: "the trips to be grouped by city"): the cities in the order their first shot was
     * saved, each with its shots in saved order; the ones not known yet last.
     */
    static java.util.LinkedHashMap<String, List<Line>> byCity(List<Line> lines) {
        java.util.LinkedHashMap<String, List<Line>> out = new java.util.LinkedHashMap<>();
        for (int pass = 0; pass < 3; pass++) {
            for (Line l : lines) {
                int kind = l.city == null ? 2 : l.city.isEmpty() ? 1 : 0;
                if (kind != pass) continue;
                String k = cityOf(l);
                List<Line> g = out.get(k);
                if (g == null) out.put(k, g = new ArrayList<>());
                g.add(l);
            }
        }
        return out;
    }

    static void rename(Context c, String id, String name, boolean named) {
        List<Line> all = loadLines(c);
        for (Line l : all) {
            if (!l.id.equals(id)) continue;
            l.name = name;
            l.named = named;
        }
        saveLines(c, all);
    }

    static void removeShot(Context c, String id) {
        List<Line> all = loadLines(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        saveLines(c, all);
        Reminders.removeLine(c, id);
    }

    /**
     * Once, on the first start of 3.0: planned shoots (2.1) that are not on a saved line become shots, and their
     * reminders move to the new shot's id so the alarms stay. Saved lines stay as they are: shots with no time.
     */
    static void migrate(Context c) {
        SharedPreferences prefs = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        if (prefs.getBoolean("shots_v3", false)) return;
        List<Line> all = loadLines(c);
        for (Shoots.Shoot s : Shoots.planned(c)) {
            if (!s.hasPlace) continue;   // every planned shoot becomes a timed shot, on a saved line or not
            Line l = new Line(UUID.randomUUID().toString(), nextShotName(all), s.youLat, s.youLon, s.subLat, s.subLon, s.mode);
            l.height = s.height;
            l.kind = s.kind;
            l.timeMs = s.timeMs;
            l.moon = s.moon;
            l.named = false;
            all.add(l);
            String to = Reminders.key(l.id, s.moon, s.timeMs);
            Reminders.remove(c, s.key);
            Reminders.add(c, new Reminders.Item(to, s.timeMs, s.title, s.text));
            Shoots.rekey(c, s.key, to);
        }
        saveLines(c, all);
        prefs.edit().putBoolean("shots_v3", true).apply();
    }

    private static Calendar midnight(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private long endMs() {
        Calendar c = midnight(startDay);
        c.add(Calendar.DAY_OF_YEAR, days); // calendar days, so a clock change does not cut a day short
        return c.getTimeInMillis();
    }

    // ---- working out the moments, off the main thread ----

    /**
     * The lines once each: shots saved at different moments on the same line (same two points, within a metre, and
     * the same side) share their moments, which the trip listed twice (roadmap, 3.6). The first saved one names them.
     */
    static List<Line> distinctLines(List<Line> all) {
        List<Line> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Line l : all) {
            String key = Math.round(l.youLat * 1e5) + "," + Math.round(l.youLon * 1e5) + ","
                    + Math.round(l.subLat * 1e5) + "," + Math.round(l.subLon * 1e5) + "," + l.mode;
            if (seen.add(key)) out.add(l);
        }
        return out;
    }

    private void compute() {
        final int g = ++generation;
        moments = null;
        final List<Line> snapshot = distinctLines(lines);   // two shots on one line: its moments once (3.6)
        final long from = Math.max(startDay, System.currentTimeMillis()), to = endMs();
        new Thread(() -> {
            List<Moment> out = new ArrayList<>();
            for (Line l : snapshot) {
                for (int b = 0; b < 2; b++) {
                    boolean moon = b == 1;
                    for (Astro.Hit h : Astro.searchAll(moon, l.youLat, l.youLon, l.bearing(), from, to)) {
                        out.add(new Moment(l, moon, h));
                    }
                }
            }
            out.sort((x, y) -> Long.compare(x.hit.timeMs, y.hit.timeMs));
            post(() -> {
                if (g != generation) return;
                moments = out;
                // Only the Trip tab shows them. Rebuilding Shots threw away the row under a finger holding it to
                // rename, so press and hold "did nothing" (Ali's test, 2026-11-15).
                if (tab == 1) renderSoon();
            });
        }).start();
    }

    // ---- the screen ----

    private void render() {
        Context c = getContext();
        body.removeAllViews();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton back = new Ui.IconButton(c, p, Ui.Icon.BACK, "Back");
        back.setOnClickListener(v -> host.closeTrip());
        head.addView(back);
        TextView title = Ui.text(c, "Saved", 24, true, p.text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        head.addView(title, tl);
        body.addView(head);

        body.addView(Ui.tabs(c, p, new String[]{"Shots", "Trip"}, tab, t -> {
            tab = t;
            prefs.edit().putInt("saved_tab3", tab).apply();
            render();
        }), new LinearLayout.LayoutParams(-2, -2));

        if (tab == 0) {
            caption("Your saved shots: where you stand, the subject and the moment. Tap one to open it; press and "
                    + "hold to rename it. The bell reminds you 30 minutes before.");
            // The lines in this trip.
            if (removed != null) {
                int n = removedReminders.size();
                LinearLayout row = row();
                row.addView(Ui.text(c, "Removed " + removed.name + (n == 0 ? "" : n == 1 ? " and its reminder"
                        : " and its " + n + " reminders"), 14, false, p.muted), new LinearLayout.LayoutParams(0, -2, 1f));
                TextView undo = Ui.button(c, p, "Undo", false);
                undo.setOnClickListener(v -> undoRemove());
                row.addView(undo);
                body.addView(row, rowParams());
            }
            // 3.31 (owner: "I don't see the option"): covers are offered where they show, not only in Settings
            if (!lines.isEmpty() && !Covers.on(c)) {
                LinearLayout row = row();
                row.addView(Ui.text(c, "Add a cover over each city, drawn by Claude.", 14, false, p.muted),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                TextView add = Ui.button(c, p, "City covers", false);
                add.setOnClickListener(v -> Covers.askKey(c, p, this::render));
                row.addView(add);
                body.addView(row, rowParams());
            } else if (Covers.on(c)) {
                // 3.32 (owner: "generate for old ones already created"): every city without a cover, at one press
                java.util.LinkedHashMap<String, List<Line>> all = byCity(lines);
                int real = 0, drawn = 0;
                for (List<Line> g : all.values()) {
                    Line l = g.get(0);
                    if (l.city == null || l.city.isEmpty()) continue;
                    real++;
                    if (Covers.cached(c, cityOf(l)) != null) drawn++;
                }
                int unknown = 0;
                for (Line l : lines) if (l.city == null) unknown++;
                if (!Covers.working.isEmpty()) {
                    note("Claude is drawing the cover of " + String.join(", ", Covers.working) + " (about a minute each).");
                } else if (drawn < real || unknown > 0) {
                    LinearLayout row = row();
                    row.addView(Ui.text(c, drawn + " of " + real + " cities have a cover"
                            + (unknown > 0 ? "; " + unknown + " shots wait for their city" : "") + ".", 14, false, p.muted),
                            new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView make = Ui.button(c, p, "Make covers", true);
                    make.setOnClickListener(v -> makeCovers());
                    row.addView(make);
                    body.addView(row, rowParams());
                }
                if (Covers.lastError != null && Covers.working.isEmpty()) note(Covers.lastError);
            }
            java.util.LinkedHashMap<String, List<Line>> cities = byCity(lines);
            for (java.util.Map.Entry<String, List<Line>> e : cities.entrySet()) {
                cityHeading(e.getKey(), e.getValue().size() == 1 ? "1 shot" : e.getValue().size() + " shots", e.getValue().get(0));
                for (Line l : e.getValue()) body.addView(shotRow(l), rowParams());
            }

            // No "Save the line on the map" here: Saved opens from Home, where that line is not in sight, and Save
            // on Your shot is the one way to save (audit 3.2.2: it made an "Any time" shot nobody expected).
            if (lines.isEmpty()) {
                note("Nothing saved yet. Every tool ends on Your shot: press Save there.");
            }
            body.addView(fileRow(true), rowParams());

            return;
        }
        // The trip: the days you are away. Every moment on the saved lines in those days is listed below.
        caption("The days you are away. Every moment on your saved shots' lines in those days is listed below.");
        if (!lines.isEmpty()) body.addView(fileRow(false), rowParams());
        LinearLayout when = new LinearLayout(c);
        when.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = Ui.button(c, p, Fmt.dayShort(c, startDay, TimeZone.getDefault()), false);
        date.setContentDescription("First day of the trip");
        date.setOnClickListener(v -> pickDate());
        when.addView(date);
        TextView minus = Ui.button(c, p, "−", false);
        minus.setContentDescription("One day less");
        minus.setEnabled(days > 1);
        minus.setAlpha(days > 1 ? 1f : 0.4f);
        minus.setOnClickListener(v -> setDays(days - 1));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.setMarginStart(Ui.dp(c, 12));
        when.addView(minus, ml);
        TextView count = Ui.text(c, days == 1 ? "1 day" : days + " days", 15, true, p.text);
        count.setGravity(Gravity.CENTER);
        count.setMinWidth(Ui.dp(c, 64));
        when.addView(count);
        TextView plus = Ui.button(c, p, "+", false);
        plus.setContentDescription("One day more");
        plus.setEnabled(days < MAX_DAYS);
        plus.setAlpha(days < MAX_DAYS ? 1f : 0.4f);
        plus.setOnClickListener(v -> setDays(days + 1));
        when.addView(plus);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = Ui.dp(c, 10);
        body.addView(when, wl);

        // Every moment in those days.
        section("Moments");
        if (lines.isEmpty()) {
            note("Moments show here once a shot is saved.");
        } else if (moments == null) {
            note("Working it out…");
        } else if (moments.isEmpty()) {
            note("The sun and moon do not line up with these lines on these days. "
                    + "Add days, or open a shot and use Build the shot to turn its line towards the sun.");
        } else {
            // 3.25 (phase 3 B-03): one heading per date. Each moment is on its own place's date, and the days are said
            // against the phone's calendar, so Paris and London on the same date no longer read Today, Tomorrow, Today.
            java.util.Map<Moment, Integer> keys = new java.util.HashMap<>();
            for (Moment m : moments) keys.put(m, dateKey(m.hit.timeMs, zone(m.line)));
            // 3.29: under each city (the order of Saved), then one heading per date in it.
            java.util.LinkedHashMap<String, List<Moment>> byCityMoments = new java.util.LinkedHashMap<>();
            for (String city : byCity(lines).keySet()) byCityMoments.put(city, new ArrayList<>());
            for (Moment m : moments) {
                List<Moment> g = byCityMoments.get(cityOf(m.line));
                if (g == null) byCityMoments.put(cityOf(m.line), g = new ArrayList<>());
                g.add(m);
            }
            for (java.util.Map.Entry<String, List<Moment>> e : byCityMoments.entrySet()) {
                List<Moment> byDay = e.getValue();
                if (byDay.isEmpty()) continue;
                cityHeading(e.getKey(), byDay.size() == 1 ? "1 moment" : byDay.size() + " moments", byDay.get(0).line);
                byDay.sort((x, y) -> {
                    int k = Integer.compare(keys.get(x), keys.get(y));
                    return k != 0 ? k : Long.compare(x.hit.timeMs, y.hit.timeMs);
                });
                int day = -1;
                for (Moment m : byDay) {
                    int d = keys.get(m);
                    if (d != day) {
                        day = d;
                        TextView dh = Ui.text(c, Fmt.day(c, phoneNoon(d), TimeZone.getDefault()), 13, true, p.muted);
                        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
                        dl.topMargin = Ui.dp(c, 10);
                        body.addView(dh, dl);
                    }
                    body.addView(momentRow(m), rowParams());
                }
            }
            note("Times are each place's own time. Tap a moment to see it in the viewfinder. The bell plans it as a shoot and reminds you 30 minutes before.");
        }
    }

    private void caption(String s) {
        TextView t = Ui.text(getContext(), s, 13.5f, false, p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), 12);
        body.addView(t, l);
    }

    /** Opens on a tab: 0 Shots, 1 Trip. */
    void showTab(int t) {
        tab = t;
        render();
    }

    /** One shot: its name, when (or any time), the bell, and remove. */
    /**
     * Sun and moon told apart at a glance (owner, 3.10: "color code"): the body's drawn glyph in its colour at the row's
     * start, and "Sun" / "Moon" in the words in that colour.
     */
    private void bodyMark(LinearLayout row, Boolean moon) {
        Context c = getContext();
        View g = moon == null ? new View(c) : new Ui.GlyphView(c, moon ? Ui.GlyphView.Kind.MOON : Ui.GlyphView.Kind.SUN,
                moon ? p.violet : p.amber, 26);
        if (moon != null && moon) ((Ui.GlyphView) g).setMoon(0.75f, true);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26));
        gl.setMarginEnd(Ui.dp(c, 12));
        row.addView(g, gl);
    }

    private CharSequence tinted(String words, Boolean moon) {
        if (moon == null) return words;
        String w = moon ? "Moon" : "Sun";
        int at = words.indexOf(w);
        if (at < 0) return words;
        android.text.SpannableString sp = new android.text.SpannableString(words);
        sp.setSpan(new android.text.style.ForegroundColorSpan(moon ? p.violet : p.amber), at, at + w.length(), 0);
        sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + w.length(), 0);
        return sp;
    }

    private View shotRow(Line l) {
        Context c = getContext();
        LinearLayout row = row();
        bodyMark(row, l.timeMs > 0 ? l.moon : null);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(c, l.name, 16, true, p.text));
        String what = (l.timeMs > 0 ? (l.moon ? "Moon " : "Sun ") : "") + l.whereAt();
        String when = l.timeMs > 0 ? Fmt.when(c, l.timeMs, zone(l)) : "Any time";
        TextView sub = Ui.text(c, "", 13, false, p.muted);
        sub.setText(tinted(when + " · " + what.substring(0, 1).toUpperCase(Locale.US) + what.substring(1), l.timeMs > 0 ? l.moon : null));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.dp(c, 3);
        text.addView(sub, sl);
        text.setClickable(true);
        Ui.pressable(text);
        text.setContentDescription("Open " + l.name);
        text.setOnClickListener(v -> host.openShot(l));
        text.setOnLongClickListener(v -> {
            askRename(l);
            return true;
        });
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        if (l.timeMs > System.currentTimeMillis()) {
            String key = Reminders.key(l.id, l.moon, l.timeMs);
            boolean on = Reminders.has(c, key);
            Ui.IconButton bell = new Ui.IconButton(c, p, on ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                    on ? "Reminder set. Press to take it off" : "Remind me 30 minutes before").small();
            bell.setOnClickListener(v -> host.toggleReminder(new Reminders.Item(key, l.timeMs,
                    (l.moon ? "Moon " : "Sun ") + l.whereAt() + " at " + Fmt.time(c, l.timeMs, zone(l)), l.name), l, this::render));
            row.addView(bell);
        }
        Ui.IconButton remove = new Ui.IconButton(c, p, Ui.Icon.TRASH, "Remove " + l.name).small();
        remove.setOnClickListener(v -> remove(l));
        row.addView(remove);
        return row;
    }

    private void askRename(Line l) {
        Context c = getContext();
        EditText name = new EditText(c);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine(true);
        name.setText(l.name);
        name.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(c);
        int s = Ui.dp(c, 20);
        wrap.setPadding(s, Ui.dp(c, 4), s, 0);
        wrap.addView(name);
        new AlertDialog.Builder(c, p.night ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Rename this shot")
                .setView(wrap)
                .setPositiveButton("Save", (dlg, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {   // 3.23 (phase 3, B-24): said, not silent
                        android.widget.Toast.makeText(c, "The name stays " + l.name + ": a name cannot be empty.", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    l.name = n;
                    l.named = true;
                    save();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View momentRow(Moment m) {
        Context c = getContext();
        LinearLayout row = row();
        bodyMark(row, m.moon);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        String what = (m.moon ? "Moon " : "Sun ") + m.line.where();
        TextView head = Ui.text(c, "", 16, true, p.text);
        head.setText(tinted(time(m) + " · " + what, m.moon));
        text.addView(head);
        TextView sub = Ui.text(c, detail(m), 13, false, p.muted);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.dp(c, 3);
        text.addView(sub, sl);
        text.setClickable(true);
        text.setContentDescription(what + " at " + time(m) + ". Opens the viewfinder");
        text.setOnClickListener(v -> host.showMoment(m.line, m.hit.timeMs, m.moon));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        String key = Reminders.key(m.line.id, m.moon, m.hit.timeMs);
        boolean on = Reminders.has(c, key);
        Ui.IconButton bell = new Ui.IconButton(c, p, on ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                on ? "Planned. Press to take it off your shoots" : "Plan this shoot: remind me 30 minutes before").small();
        bell.setOnClickListener(v -> host.toggleReminder(new Reminders.Item(key, m.hit.timeMs,
                what.substring(0, 1).toUpperCase(Locale.US) + what.substring(1) + " at " + time(m),
                detail(m)), m.line, this::render));
        row.addView(bell);
        return row;
    }

    private String detail(Moment m) {
        String alt = m.hit.alt < 0.5 ? "on the horizon" : Math.round(m.hit.alt) + "° above the horizon";
        // 3.23 (phase 3, B-09): a name in Arabic turned the line right to left and moved the numbers ("°8 above")
        return "\u200E" + android.text.BidiFormatter.getInstance(false).unicodeWrap(m.line.name) + (Astro.isLow(m.hit, m.moon) ? (m.moon ? " · Low moon" : " · Low sun") : "") + " · " + alt
                + (m.moon ? " · " + Math.round(m.hit.lit * 100) + "% lit" : "");
    }

    /** Send as file and Open a file (3.28): shots between phones with no network, as a .sunwhere file. */
    private LinearLayout fileRow(boolean open) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (!lines.isEmpty()) {
            TextView send = Ui.button(c, p, tab == 1 ? "Send the trip as a file" : "Send as file", false);
            send.setOnClickListener(v -> host.sendShots(new ArrayList<>(lines)));
            row.addView(send, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (open) {
            TextView pick = Ui.button(c, p, "Open a file", false);
            pick.setOnClickListener(v -> host.openFile());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (!lines.isEmpty()) lp.setMarginStart(Ui.dp(c, 8));
            row.addView(pick, lp);
        }
        return row;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        int s = Ui.dp(getContext(), 12);
        row.setPadding(s, Ui.dp(getContext(), 10), Ui.dp(getContext(), 6), Ui.dp(getContext(), 10));
        row.setBackground(Ui.shape(getContext(), p.panel, p.line, 14, false));
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), 8);
        return l;
    }

    /** Press and hold a city's heading (3.33, owner: "the names should be editable"): rename it for all its shots. */
    private void askCityName(String city) {
        Context c = getContext();
        android.widget.EditText field = new android.widget.EditText(c);
        field.setSingleLine(true);
        field.setText(city);
        field.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(c);
        int pad = Ui.dp(c, 20);
        wrap.setPadding(pad, Ui.dp(c, 4), pad, 0);
        wrap.addView(field);
        new android.app.AlertDialog.Builder(c, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Rename this city")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String n = field.getText().toString().trim();
                    if (n.isEmpty() || n.equals(city)) return;
                    List<Line> all = loadLines(c);
                    for (Line l : all) if (city.equals(l.city)) l.city = n;
                    saveLines(c, all);
                    Covers.renameCity(c, city, n);           // the cover keeps, under the new name
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Every city without a cover is drawn; shots with no city yet ask for theirs first (3.32). */
    private void makeCovers() {
        Context c = getContext();
        if (!Covers.online(c)) {
            Covers.lastError = "Covers need a network.";
            render();
            return;
        }
        Covers.lastError = null;
        nameUnnamed();                                     // old shots learn their city; their covers follow
        for (List<Line> g : byCity(lines).values()) {
            Line l = g.get(0);
            if (l.city == null || l.city.isEmpty() || Covers.cached(c, l.city) != null) continue;
            Covers.retry(c, l.city);
            Covers.make(c, l.city, l.subLat, l.subLon, true, b -> renderSoon());
        }
        render();
    }

    /**
     * A city's heading in Saved (3.29): the name, and how many under it. With City covers on (3.30) the heading is a
     * banner: the city's cover with its name over it; the cover is made once, the first time the city shows online.
     */
    private void cityHeading(String city, String count, Line any) {
        Context c = getContext();
        boolean real = any.city != null && !any.city.isEmpty();
        android.graphics.Bitmap cover = real ? Covers.cached(c, city) : null;
        if (real && cover == null)
            Covers.make(c, city, any.subLat, any.subLon, Covers.online(c), b -> renderSoon());
        if (cover != null) {
            FrameLayout banner = new FrameLayout(c);
            android.widget.ImageView img = new android.widget.ImageView(c);
            img.setImageBitmap(cover);
            img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            img.setContentDescription("Cover of " + city);
            banner.addView(img, new FrameLayout.LayoutParams(-1, -1));
            // A drawn cover (Claude) is a paper booklet: dark words on it, the app's name at the foot (owner's picture,
            // 3.30). A photo or a painting gets a shade and white words.
            boolean paper = Covers.source(c) == Covers.CLAUDE;
            if (!paper) {
                View shade = new View(c);
                shade.setBackground(new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x99000000, 0x00000000}));
                banner.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            }
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(c, 14);
            words.setPadding(Ui.dp(c, 18), pad, pad, pad);
            words.setGravity(paper ? Gravity.TOP : Gravity.BOTTOM);
            TextView name = Ui.text(c, city, paper ? 24 : 22, true, paper ? 0xFF22211F : 0xFFFFFFFF);
            name.setMaxLines(2);                           // 3.33: a long name shrinks to fit, never cut short
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (android.os.Build.VERSION.SDK_INT >= 26)
                name.setAutoSizeTextTypeUniformWithConfiguration(13, paper ? 24 : 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            TextView n = Ui.text(c, count, paper ? 15 : 13, false, paper ? 0xFF3C3B38 : 0xE6FFFFFF);
            if (!paper) {
                name.setShadowLayer(6, 0, 1, 0xAA000000);
                n.setShadowLayer(4, 0, 1, 0xAA000000);
            }
            words.addView(name);
            words.addView(n);
            int bw = getWidth() > 0 ? getWidth() - Ui.dp(c, 32) : getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 32);
            banner.addView(words, new FrameLayout.LayoutParams(paper ? (int) (bw * 0.42f) : -1, -1));   // clear of the skyline
            if (paper) {
                TextView mark = Ui.text(c, "SunWhere", 13, true, 0xFF22211F);
                FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                ml.setMargins(Ui.dp(c, 18), 0, 0, Ui.dp(c, 8));
                banner.addView(mark, ml);
                banner.setElevation(Ui.dp(c, 3));
            }
            // 3.32: a tap offers to draw it again
            banner.setOnClickListener(v -> new android.app.AlertDialog.Builder(c, p.night
                    ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle("Redraw the cover of " + city + "?")
                    .setMessage("Claude designs it again, about a minute and about 2 US cents of the key's credit.")
                    .setPositiveButton("Redraw", (d, w) -> {
                        Covers.lastError = null;
                        Covers.redo(c, city, any.subLat, any.subLon, Covers.online(c), b -> renderSoon());
                        render();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            banner.setOnLongClickListener(v -> { askCityName(city); return true; });
            banner.setClipToOutline(true);
            android.graphics.drawable.GradientDrawable round = new android.graphics.drawable.GradientDrawable();
            round.setCornerRadius(Ui.dp(c, 14));
            round.setStroke(Math.max(1, Ui.dp(c, 1)), p.night ? 0x33FFFFFF : 0x22000000);   // the cover stands off the page
            banner.setBackground(round);
            int width = getWidth() > 0 ? getWidth() - Ui.dp(c, 32) : getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 32);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Math.max(Ui.dp(c, 96), width / 3));
            bl.topMargin = Ui.dp(c, 20);
            body.addView(banner, bl);
            return;
        }
        LinearLayout h = new LinearLayout(c);
        h.setGravity(Gravity.BOTTOM);
        if (real) h.setOnLongClickListener(v -> { askCityName(city); return true; });   // 3.33: hold to rename
        TextView name = Ui.text(c, city, 17, true, p.text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        h.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView n = Ui.text(c, count, 13, false, p.muted);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.setMarginStart(Ui.dp(c, 8));
        h.addView(n, nl);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(c, 20);
        body.addView(h, l);
    }

    private void section(String s) {
        TextView t = Ui.text(getContext(), s, 16, true, p.text);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), 24);
        body.addView(t, l);
    }

    private void note(String s) {
        TextView t = Ui.text(getContext(), s, 13, false, p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), 10);
        body.addView(t, l);
    }

    private String fmt(long ms, String skeleton, TimeZone tz) {
        return Fmt.format(getContext(), skeleton, tz).format(new Date(ms));
    }

    /** A line's times are said in the time of the place it is in. */
    /** 20261123: the date of this moment in its place's own time. */
    private static int dateKey(long ms, TimeZone tz) {
        java.util.Calendar k = java.util.Calendar.getInstance(tz);
        k.setTimeInMillis(ms);
        return k.get(java.util.Calendar.YEAR) * 10000 + (k.get(java.util.Calendar.MONTH) + 1) * 100 + k.get(java.util.Calendar.DAY_OF_MONTH);
    }

    /** Noon of that date on the phone's calendar: what the day heading is said from. */
    private static long phoneNoon(int key) {
        java.util.Calendar k = java.util.Calendar.getInstance(TimeZone.getDefault());
        k.clear();
        k.set(key / 10000, key / 100 % 100 - 1, key % 100, 12, 0, 0);
        return k.getTimeInMillis();
    }

    private TimeZone zone(Line l) {
        return Zones.of(getContext(), l.subLat, l.subLon);
    }

    private String time(Moment m) {
        return Fmt.time(getContext(), m.hit.timeMs, zone(m.line));
    }

    /** Saves the line on the map as a shot at once; it gets a place name by itself. */
    void saveMapLine() {
        Line onMap = host.mapLine();
        tab = 0;
        if (onMap != null && find(onMap.id) == null) {
            Line copy = new Line(onMap.id, null, onMap.youLat, onMap.youLon, onMap.subLat, onMap.subLon, onMap.mode);
            copy.height = onMap.height;
            copy.kind = onMap.kind;
            Line l = addShot(getContext(), copy);
            lines.clear();
            lines.addAll(loadLines(getContext()));
            host.mapLineIs(l);
            host.nameShot(l);
            removed = null;
            compute();
        }
        render();
    }

    /** Shots still called "Shot 3" get a place name, and shots with no city their city, a second apart (Nominatim's rule). */
    void nameUnnamed() {
        int n = 0;
        for (Line l : lines) {
            if ((l.named && l.city != null) || n >= 20) continue;   // 3.29: named shots still ask for their city
            final Line x = l;
            postDelayed(() -> host.nameShot(x), 1100L * n++);
        }
    }

    /** Someone else (the map screen) changed the saved shots: show them again. */
    void reload() {
        lines.clear();
        lines.addAll(loadLines(getContext()));
        compute();
        renderSoon();
    }

    // Answers that arrive by themselves (moments worked out, a place name) wait while a finger is on the screen:
    // rebuilding the rows threw away the one being held, so press and hold to rename did nothing (Ali, 2026-11-15).
    private boolean fingerDown, renderLater;

    private void renderSoon() {
        if (fingerDown) renderLater = true;
        else render();
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        int a = e.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) fingerDown = true;
        else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            fingerDown = false;
            if (renderLater) {
                renderLater = false;
                post(this::render);
            }
        }
        return super.dispatchTouchEvent(e);
    }

    // ---- changes ----

    private void pickDate() {
        Calendar c = midnight(startDay);
        DatePickerDialog d = new DatePickerDialog(getContext(), (v, y, mo, dd) -> {
            Calendar n = Calendar.getInstance();
            n.set(y, mo, dd, 0, 0, 0);
            n.set(Calendar.MILLISECOND, 0);
            startDay = n.getTimeInMillis();
            changed();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(midnight(System.currentTimeMillis()).getTimeInMillis());
        d.show();
    }

    private void setDays(int n) {
        days = Math.max(1, Math.min(MAX_DAYS, n));
        changed();
    }

    private void changed() {
        removed = null; // a new change ends the chance to Undo
        save();
        compute();
        render();
    }

    private Line find(String id) {
        for (Line l : lines) if (l.id.equals(id)) return l;
        return null;
    }

    private void remove(Line l) {
        removedAt = lines.indexOf(l);
        lines.remove(l);
        removedReminders = Reminders.removeLine(getContext(), l.id); // what the trip made for it goes too
        save();
        compute();
        removed = l;
        render();
    }

    private void undoRemove() {
        if (removed == null) return;
        lines.add(Math.min(removedAt, lines.size()), removed);
        long now = System.currentTimeMillis();
        for (Reminders.Item i : removedReminders) if (i.timeMs > now) Reminders.add(getContext(), i);
        removed = null;
        save();
        compute();
        render();
    }
}
