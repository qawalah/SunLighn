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
        double panDeg = 0;            // the framing from Build the shot (3.3): degrees off the subject
        double pitch = Double.NaN;    // its tilt; NaN: the usual tilt (shots from the other tools, and before 3.3)

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
                l.panDeg = o.optDouble("pan", 0);
                l.pitch = o.has("pitch") ? o.optDouble("pitch", Double.NaN) : Double.NaN;
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
                if (l.panDeg != 0) o.put("pan", l.panDeg);
                if (!Double.isNaN(l.pitch)) o.put("pitch", l.pitch);
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
        l.panDeg = from.panDeg;      // the framing goes with the shot (3.3)
        l.pitch = from.pitch;
        all.add(l);
        saveLines(c, all);
        return l;
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

    private void compute() {
        final int g = ++generation;
        moments = null;
        final List<Line> snapshot = new ArrayList<>(lines);
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
            for (Line l : lines) body.addView(shotRow(l), rowParams());

            // No "Save the line on the map" here: Saved opens from Home, where that line is not in sight, and Save
            // on Your shot is the one way to save (audit 3.2.2: it made an "Any time" shot nobody expected).
            if (lines.isEmpty()) {
                note("Nothing saved yet. Every tool ends on Your shot: press Save there.");
            }

            return;
        }
        // The trip: the days you are away. Every moment on the saved lines in those days is listed below.
        caption("The days you are away. Every moment on your saved shots' lines in those days is listed below.");
        LinearLayout when = new LinearLayout(c);
        when.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = Ui.button(c, p, fmt(startDay, "EEEdMMM", TimeZone.getDefault()), false);
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
                    + "Add days, or open a line and use Where to stand to turn it towards the sun.");
        } else {
            long day = -1;
            for (Moment m : moments) {
                long d = Fmt.midnight(m.hit.timeMs, zone(m.line));
                if (d != day) {
                    day = d;
                    TextView dh = Ui.text(c, fmt(m.hit.timeMs, "EEEEdMMMM", zone(m.line)), 13, true, p.muted);
                    LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
                    dl.topMargin = Ui.dp(c, 14);
                    body.addView(dh, dl);
                }
                body.addView(momentRow(m), rowParams());
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
    private View shotRow(Line l) {
        Context c = getContext();
        LinearLayout row = row();
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(c, l.name, 16, true, p.text));
        String what = (l.timeMs > 0 ? (l.moon ? "Moon " : "Sun ") : "") + l.where();
        String when = l.timeMs > 0 ? Fmt.when(c, l.timeMs, zone(l)) : "Any time";
        TextView sub = Ui.text(c, when + " · " + what.substring(0, 1).toUpperCase(Locale.US) + what.substring(1), 13, false, p.muted);
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
                    (l.moon ? "Moon " : "Sun ") + l.where() + " at " + Fmt.time(c, l.timeMs, zone(l)), l.name), l, this::render));
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
                    if (n.isEmpty()) return;
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
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        String what = (m.moon ? "Moon " : "Sun ") + m.line.where();
        text.addView(Ui.text(c, time(m) + " · " + what, 16, true, p.text));
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
        return m.line.name + (Astro.isLow(m.hit, m.moon) ? (m.moon ? " · Low moon" : " · Low sun") : "") + " · " + alt
                + (m.moon ? " · " + Math.round(m.hit.lit * 100) + "% lit" : "");
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

    /** Shots still called "Shot 3" get a place name now, a few at a time (the name service asks for patience). */
    void nameUnnamed() {
        int n = 0;
        for (Line l : lines) {
            if (l.named || n >= 5) continue;
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
