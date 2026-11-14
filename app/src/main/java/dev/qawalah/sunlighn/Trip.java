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
        void showMoment(Line l, long timeMs);
        /** The line on the map was just added to the trip as l: from now on it shares l's id. */
        void mapLineIs(Line l);
        void toggleReminder(Reminders.Item item, Runnable changed);
        void closeTrip();
    }

    private final Ui.Palette p;
    private final Host host;
    private final SharedPreferences prefs;
    private final List<Line> lines = new ArrayList<>();
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
        try {
            JSONArray a = new JSONArray(prefs.getString("trip_lines", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Line l = new Line(o.getString("id"), o.getString("name"), o.getDouble("yLat"), o.getDouble("yLon"),
                        o.getDouble("sLat"), o.getDouble("sLon"), o.getInt("mode"));
                l.height = o.optDouble("h", 0);
                l.kind = Math.max(0, Math.min(FrameMath.KINDS.length - 1, o.optInt("kind", 3)));
                lines.add(l);
            }
        } catch (JSONException ignored) {
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (Line l : lines) {
                a.put(new JSONObject().put("id", l.id).put("name", l.name).put("yLat", l.youLat).put("yLon", l.youLon)
                        .put("sLat", l.subLat).put("sLon", l.subLon).put("mode", l.mode).put("h", l.height).put("kind", l.kind));
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putLong("trip_start", startDay).putInt("trip_days", days).putString("trip_lines", a.toString()).apply();
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
                render();
            });
        }).start();
    }

    // ---- the screen ----

    private void render() {
        Context c = getContext();
        body.removeAllViews();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton back = new Ui.IconButton(c, p, Ui.Icon.BACK, "Back to the map");
        back.setOnClickListener(v -> host.closeTrip());
        head.addView(back);
        TextView title = Ui.text(c, "Saved", 24, true, p.text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        head.addView(title, tl);
        body.addView(head);

        // The lines in this trip.
        section("Lines");
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
        for (Line l : lines) body.addView(lineRow(l), rowParams());

        Line onMap = host.mapLine();
        boolean inTrip = onMap != null && find(onMap.id) != null;
        if (onMap != null && !inTrip) {
            TextView add = Ui.button(c, p, "Save the line on the map", true);
            add.setOnClickListener(v -> askName(onMap));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
            al.topMargin = Ui.dp(c, 10);
            body.addView(add, al);
        } else if (lines.isEmpty()) {
            note("Nothing saved yet. Draw a line on the map (your subject, then where you stand); "
                    + "it can then be saved from here or from the three dots on its sheet.");
        }

        // The trip: the days you are away. Every moment on the saved lines in those days is listed below.
        section("Trip");
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
            note("Moments show here once a line is saved.");
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
            note("Times are each place's own time. Tap a moment to see it in the Frame; the bell reminds you 30 minutes before.");
        }
    }

    private View lineRow(Line l) {
        Context c = getContext();
        LinearLayout row = row();
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(c, l.name, 16, true, p.text));
        double km = Astro.distance(l.youLat, l.youLon, l.subLat, l.subLon);
        double b = Astro.bearing(l.youLat, l.youLon, l.subLat, l.subLon);
        TextView sub = Ui.text(c, String.format(Locale.US, "%d° %s · %s · %s", Math.round(b) % 360, Astro.compass(b),
                Fmt.distance(km), l.mode == 0 ? "Behind subject" : "Behind you"), 13, false, p.muted);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.dp(c, 3);
        text.addView(sub, sl);
        text.setClickable(true);
        text.setContentDescription("Show " + l.name + " on the map");
        text.setOnClickListener(v -> host.showOnMap(l));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.IconButton remove = new Ui.IconButton(c, p, Ui.Icon.TRASH, "Remove " + l.name).small();
        remove.setOnClickListener(v -> remove(l));
        row.addView(remove);
        return row;
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
        text.setContentDescription(what + " at " + time(m) + ". Opens the Frame");
        text.setOnClickListener(v -> host.showMoment(m.line, m.hit.timeMs));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        String key = Reminders.key(m.line.id, m.moon, m.hit.timeMs);
        boolean on = Reminders.has(c, key);
        Ui.IconButton bell = new Ui.IconButton(c, p, on ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                on ? "Remove the reminder" : "Remind me 30 minutes before").small();
        bell.setOnClickListener(v -> host.toggleReminder(new Reminders.Item(key, m.hit.timeMs,
                what.substring(0, 1).toUpperCase(Locale.US) + what.substring(1) + " at " + time(m),
                detail(m)), this::render));
        row.addView(bell);
        return row;
    }

    private String detail(Moment m) {
        String alt = m.hit.alt < 0.5 ? "on the horizon" : Math.round(m.hit.alt) + "° above the horizon";
        return m.line.name + (Astro.isLow(m.hit, m.moon) ? " · Low" : "") + " · " + alt
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

    /** "Save line" on the map's sheet: ask for the name straight away. */
    void saveMapLine() {
        Line onMap = host.mapLine();
        if (onMap != null && find(onMap.id) == null) askName(onMap);
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

    private void askName(Line onMap) {
        Context c = getContext();
        EditText name = new EditText(c);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine(true);
        String suggested = onMap.name != null && !onMap.name.isEmpty() ? onMap.name : nextName();
        name.setHint(suggested);
        FrameLayout wrap = new FrameLayout(c);
        int s = Ui.dp(c, 20);
        wrap.setPadding(s, Ui.dp(c, 4), s, 0);
        wrap.addView(name);
        new AlertDialog.Builder(c, p.night ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Name this line")
                .setMessage("For example the place or the subject. It is kept in Saved, and its moments show in your trip.")
                .setView(wrap)
                .setPositiveButton("Save", (dlg, w) -> {
                    String n = name.getText().toString().trim();
                    Line l = new Line(UUID.randomUUID().toString(), n.isEmpty() ? suggested : n,
                            onMap.youLat, onMap.youLon, onMap.subLat, onMap.subLon, onMap.mode);
                    l.height = onMap.height;
                    l.kind = onMap.kind;
                    lines.add(l);
                    host.mapLineIs(l);
                    changed();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String nextName() {
        for (int n = lines.size() + 1; ; n++) {
            String s = "Line " + n;
            boolean used = false;
            for (Line l : lines) used |= l.name.equalsIgnoreCase(s);
            if (!used) return s;
        }
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
