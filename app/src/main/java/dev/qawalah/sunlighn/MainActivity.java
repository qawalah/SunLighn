package dev.qawalah.sunlighn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SunLighn: tap where you stand, tap your subject, and read when the sun and the moon
 * sit right on that line. Two taps to the answer; the last line is remembered.
 */
public class MainActivity extends Activity implements Trip.Host {
    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFY = 2;
    static final String MAP_LINE = "map"; // reminder id of a line that is not in the trip

    private SharedPreferences prefs;
    private Ui.Palette p;
    private FrameLayout root;
    private MapView map;
    private LineOverlay lineOverlay;
    private LinearLayout card;
    private Tutorial tutorial;
    private Trip trip;
    private int insetTop, insetBottom;

    private GeoPoint you, subject;
    private GeoPoint backupYou, backupSubject; // for Undo after Clear
    private int mode;                           // 0 = sun/moon behind the subject, 1 = behind you
    private String mapLineId = MAP_LINE;        // the trip line's id while the map shows one, so reminders match
    private GeoPoint movedFrom;                 // for Undo after "Move here"

    private Reminders.Item pendingReminder;     // waits for the notification permission
    private Runnable pendingChanged;

    private int generation;
    private Astro.Found foundSun, foundMoon;
    private long[] standTimes;                  // {evening, morning} low sun for Where to stand, -1 if none
    private boolean working;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sunlighn", MODE_PRIVATE);
        p = new Ui.Palette(this);

        // Tiles are cached in the app's own cache folder, so no storage permission is needed.
        org.osmdroid.config.IConfigurationProvider cfg = org.osmdroid.config.Configuration.getInstance();
        cfg.load(getApplicationContext(), getSharedPreferences("osmdroid", MODE_PRIVATE));
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osmdroid");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));

        setupWindow();
        root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);
        setContentView(root);

        buildMap();
        buildControls();
        loadState();
        refresh();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            applyInsets();
            return insets;
        });

        if (!prefs.getBoolean("tutorial_done", false)) showTutorial();
    }

    // ---- window ----

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!p.night) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void applyInsets() {
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        tp.topMargin = insetTop + Ui.dp(this, 12);
        topBar.setLayoutParams(tp);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), insetBottom + Ui.dp(this, 16));
        if (tutorial != null) tutorial.setInsets(insetTop, insetBottom);
        if (trip != null) trip.setInsets(insetTop, insetBottom);
    }

    // ---- map ----

    private void buildMap() {
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(2.0);
        if (p.night) map.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint pt) {
                onMapTap(pt);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint pt) { return false; }
        }));
        lineOverlay = new LineOverlay();
        map.getOverlays().add(lineOverlay);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));
    }

    private void onMapTap(GeoPoint pt) {
        if (tutorial != null || trip != null) return;
        backupYou = backupSubject = null; // a new tap ends the chance to Undo
        movedFrom = null;
        if (you == null) {
            you = pt;
        } else if (subject == null) {
            if (Astro.distance(you.getLatitude(), you.getLongitude(), pt.getLatitude(), pt.getLongitude()) < 10) {
                Toast.makeText(this, "That is too close to you. Tap a subject farther away.", Toast.LENGTH_SHORT).show();
                return;
            }
            subject = pt;
        } else {
            // Move whichever point is nearer to the tap.
            Projection pr = map.getProjection();
            Point t = pr.toPixels(pt, null), a = pr.toPixels(you, null), b = pr.toPixels(subject, null);
            double da = Math.hypot(t.x - a.x, t.y - a.y), db = Math.hypot(t.x - b.x, t.y - b.y);
            GeoPoint other = da <= db ? subject : you;
            if (Astro.distance(other.getLatitude(), other.getLongitude(), pt.getLatitude(), pt.getLongitude()) < 10) {
                Toast.makeText(this, "That is too close to the other point.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (da <= db) you = pt; else subject = pt;
        }
        mapLineId = MAP_LINE; // a changed line is a new line, not the trip line any more
        saveState();
        refresh();
    }

    // ---- controls ----

    private LinearLayout topBar;

    private void buildControls() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.END);
        Ui.IconButton tripButton = new Ui.IconButton(this, p, Ui.Icon.TRIP, "Trip plan");
        tripButton.setOnClickListener(v -> showTrip());
        topBar.addView(tripButton);
        Ui.IconButton locate = new Ui.IconButton(this, p, Ui.Icon.LOCATE, "Go to my location");
        Ui.IconButton help = new Ui.IconButton(this, p, Ui.Icon.HELP, "How it works");
        locate.setOnClickListener(v -> locate(true));
        help.setOnClickListener(v -> showTutorial());
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.setMarginStart(Ui.dp(this, 8));
        topBar.addView(locate, ll);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.setMarginStart(Ui.dp(this, 8));
        topBar.addView(help, hl);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.setMarginEnd(Ui.dp(this, 12));
        tp.topMargin = Ui.dp(this, 12);
        root.addView(topBar, tp);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.shape(this, p.panel, p.line, 22, true));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setClickable(true); // taps on the card must not reach the map
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(card, cp);
    }

    // ---- the card: steps first, then the answer ----

    private void refresh() {
        card.removeAllViews();
        lineOverlay.update();
        map.invalidate();
        generation++; // anything still being worked out is now out of date
        foundSun = foundMoon = null;
        working = false;

        if (you == null || subject == null) {
            renderSteps();
            return;
        }
        double b = lineBearing();
        double km = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, String.format(Locale.US, "%d° %s · %s",
                Math.round(b) % 360, Astro.compass(b), km < 1000 ? Math.round(km) + " m" : String.format(Locale.US, "%.1f km", km / 1000)),
                24, true, p.text);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = Ui.button(this, p, "Clear", false);
        clear.setOnClickListener(v -> clearLine());
        head.addView(clear);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chips = new LinearLayout(this);
        TextView c0 = Ui.chip(this, p, "Behind subject", mode == 0);
        TextView c1 = Ui.chip(this, p, "Behind you", mode == 1);
        c0.setOnClickListener(v -> setMode(0));
        c1.setOnClickListener(v -> setMode(1));
        chips.addView(c0);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(-2, -2);
        l1.setMarginStart(Ui.dp(this, 8));
        chips.addView(c1, l1);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Ui.dp(this, 10);
        card.addView(chips, cl);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                // The card never covers more than about half the map.
                int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.45f);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        sv.addView(resultsBox);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, -2);
        svl.topMargin = Ui.dp(this, 6);
        card.addView(sv, svl);

        TextView note = Ui.text(this, "Tap the map to move the nearest point · times in " + TimeZone.getDefault().getID(),
                12.5f, false, p.muted);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 10);
        card.addView(note, nl);

        compute();
    }

    private LinearLayout resultsBox;

    private void renderSteps() {
        String s = you == null ? "Tap the map where you will stand." : "Now tap your subject.";
        TextView t = Ui.text(this, s, 24, true, p.text);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = Ui.text(this, you == null
                ? "Then tap the building, peak or tree you want the sun or moon to line up with."
                : "SunLighn will tell you when the sun and moon line up with it.", 15, false, p.muted);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = Ui.dp(this, 8);
        card.addView(sub, sl);
        if (backupYou != null) {
            TextView undo = Ui.button(this, p, "Undo clear", false);
            undo.setOnClickListener(v -> {
                you = backupYou;
                subject = backupSubject;
                backupYou = backupSubject = null;
                saveState();
                refresh();
            });
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-2, -2);
            ul.topMargin = Ui.dp(this, 12);
            card.addView(undo, ul);
        }
    }

    private void clearLine() {
        backupYou = you;
        backupSubject = subject;
        you = subject = null;
        movedFrom = null;
        mapLineId = MAP_LINE;
        saveState();
        refresh();
    }

    private void setMode(int m) {
        if (m == mode) return;
        mode = m;
        movedFrom = null;
        mapLineId = MAP_LINE; // the other end is a different line for the trip
        prefs.edit().putInt("mode", mode).putString("map_line_id", mapLineId).apply();
        refresh();
    }

    /** The direction the sun or moon must be in, as a compass bearing. */
    private double lineBearing() {
        double b = Astro.bearing(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        return mode == 0 ? b : Astro.norm360(b + 180);
    }

    // ---- working out the answer, off the main thread ----

    private void compute() {
        final int g = generation;
        final double lat = you.getLatitude(), lon = you.getLongitude(), b = lineBearing();
        final long now = System.currentTimeMillis();
        final double sLat = subject.getLatitude(), sLon = subject.getLongitude();
        standTimes = null;
        working = true;
        renderResults();
        new Thread(() -> {
            long[] st = {Astro.sunPasses(sLat, sLon, Astro.SUGGEST_ALT, true, now),
                    Astro.sunPasses(sLat, sLon, Astro.SUGGEST_ALT, false, now)};
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                standTimes = st;
                renderResults();
            });
            Astro.Found s = Astro.search(false, lat, lon, b, now);
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundSun = s;
                renderResults();
            });
            Astro.Found m = Astro.search(true, lat, lon, b, now);
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundMoon = m;
                working = false;
                renderResults();
            });
        }).start();
    }

    private void renderResults() {
        resultsBox.removeAllViews();
        addSection("Sun", p.amber, foundSun, false);
        addSection("Moon", p.violet, foundMoon, true);
        addWhereToStand();
    }

    private void addSection(String name, int dot, Astro.Found f, boolean moon) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View d = new View(this);
        d.setBackground(Ui.shape(this, dot, 0, 99, false));
        head.addView(d, new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10)));
        TextView t = Ui.text(this, name, 16, true, p.text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.dp(this, 8));
        head.addView(t, tl);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 12);
        resultsBox.addView(head, hl);

        if (f == null) {
            addLine(working ? "Working it out…" : "", false);
            return;
        }
        if (f.next == null) {
            addLine("The " + name.toLowerCase(Locale.US) + " never lines up with this line this year. "
                    + (moon ? "Try a line that points more east or west."
                    : "Try Where to stand below, or a line that points more east or west."), false);
            return;
        }
        addHit(f.next, "Next", moon);
        if (f.low != null && f.low.timeMs != f.next.timeMs) {
            addHit(f.low, moon ? "Low, bright moon" : "Low sun", moon);
        }
    }

    private void addHit(Astro.Hit h, String label, boolean moon) {
        String alt = h.alt < 0.5 ? "on the horizon" : Math.round(h.alt) + "° above the horizon";
        String detail = label + " · " + alt + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : "");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this, when(h.timeMs), 16, true, p.text));
        TextView d = Ui.text(this, detail, 13, false, p.muted);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = Ui.dp(this, 2);
        text.addView(d, dl);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        String key = Reminders.key(mapLineId, moon, h.timeMs);
        boolean on = Reminders.has(this, key);
        Ui.IconButton bell = new Ui.IconButton(this, p, on ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                on ? "Remove the reminder" : "Remind me 30 minutes before").small();
        String what = (moon ? "Moon " : "Sun ") + (mode == 0 ? "behind subject" : "behind you");
        String lineName = lineName();
        bell.setOnClickListener(v -> toggleReminder(new Reminders.Item(key, h.timeMs, what + " at " + time(h.timeMs),
                (lineName == null ? "" : lineName + " · ") + detail), this::renderResults));
        row.addView(bell);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 6);
        resultsBox.addView(row, rl);
    }

    /** The trip's name for the line on the map, or null if it is not a trip line. */
    private String lineName() {
        if (MAP_LINE.equals(mapLineId)) return null;
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("trip_lines", "[]"));
            for (int i = 0; i < a.length(); i++) {
                if (mapLineId.equals(a.getJSONObject(i).getString("id"))) return a.getJSONObject(i).getString("name");
            }
        } catch (org.json.JSONException ignored) {
        }
        return null;
    }

    // ---- where to stand: move You so the low sun sits on the line ----

    private void addWhereToStand() {
        if (standTimes == null || standTimes[0] < 0 && standTimes[1] < 0) return;
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, "Where to stand", 16, true, p.text));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 16);
        resultsBox.addView(head, hl);
        addLine("Keep the subject, move you so the low sun sits " + (mode == 0 ? "behind the subject." : "behind you."), false);
        if (movedFrom != null) {
            TextView undo = Ui.button(this, p, "Undo move", false);
            undo.setOnClickListener(v -> {
                you = movedFrom;
                movedFrom = null;
                saveState();
                refresh();
            });
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-2, -2);
            ul.topMargin = Ui.dp(this, 8);
            resultsBox.addView(undo, ul);
            return;
        }
        for (int i = 0; i < 2; i++) {
            long t = standTimes[i];
            if (t < 0) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.addView(Ui.text(this, when(t), 16, true, p.text));
            TextView d = Ui.text(this, i == 0 ? "Evening low sun" : "Morning low sun", 13, false, p.muted);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = Ui.dp(this, 2);
            text.addView(d, dl);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView move = Ui.button(this, p, "Move here", false);
            move.setOnClickListener(v -> moveForSun(t));
            row.addView(move);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = Ui.dp(this, 8);
            resultsBox.addView(row, rl);
        }
    }

    /** Puts You at the same distance from the subject, on the side that puts the sun at time t on the line. */
    private void moveForSun(long t) {
        double sLat = subject.getLatitude(), sLon = subject.getLongitude();
        double az = Astro.sun(t, sLat, sLon)[0];
        double dist = Astro.distance(you.getLatitude(), you.getLongitude(), sLat, sLon);
        // Behind subject: you look along the sun's direction, so you stand on the far side from the sun.
        double[] at = Astro.destination(sLat, sLon, mode == 0 ? Astro.norm360(az + 180) : az, dist);
        movedFrom = you;
        backupYou = backupSubject = null;
        you = new GeoPoint(at[0], at[1]);
        mapLineId = MAP_LINE;
        saveState();
        refresh();
        if (!map.getBoundingBox().contains(you)) map.getController().animateTo(you);
    }

    private void addLine(String s, boolean strong) {
        TextView t = Ui.text(this, s, strong ? 16 : 13, strong, strong ? p.text : p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(this, strong ? 8 : 2);
        resultsBox.addView(t, l);
    }

    private String time(long ms) {
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(this) ? "Hm" : "jm"), Locale.getDefault()).format(ms);
    }

    private String when(long ms) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String time = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(this) ? "Hm" : "jm"), Locale.getDefault()).format(c.getTime());
        String day;
        if (sameDay(c, now)) day = "Today";
        else if (sameDay(c, tomorrow)) day = "Tomorrow";
        else day = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"),
                Locale.getDefault()).format(c.getTime());
        return day + " · " + time;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---- location ----

    @SuppressLint("MissingPermission")
    private void locate(boolean ask) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ask) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        for (String prov : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(prov);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null) {
            goTo(best);
            return;
        }
        String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (prov == null) {
            Toast.makeText(this, "Turn on location in your phone settings, then press this again.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Finding you…", Toast.LENGTH_SHORT).show();
        lm.requestSingleUpdate(prov, new LocationListener() {
            @Override public void onLocationChanged(Location l) { goTo(l); }
            @Override public void onStatusChanged(String s, int i, Bundle b) {}
            @Override public void onProviderEnabled(String s) {}
            @Override public void onProviderDisabled(String s) {}
        }, Looper.getMainLooper());
    }

    private void goTo(Location l) {
        if (isFinishing()) return;
        map.getController().setZoom(15.0);
        map.getController().animateTo(new GeoPoint(l.getLatitude(), l.getLongitude()));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_NOTIFY) {
            Reminders.Item item = pendingReminder;
            Runnable changed = pendingChanged;
            pendingReminder = null;
            pendingChanged = null;
            if (item == null) return;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                setReminder(item, changed); // the bell does its job once allowed, no second press
            } else {
                Toast.makeText(this, "Without notifications SunLighn cannot remind you. "
                        + "You can allow them in the phone's settings.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (code != REQ_LOCATION) return;
        for (int r : results) {
            if (r == PackageManager.PERMISSION_GRANTED) {
                locate(false);
                return;
            }
        }
        Toast.makeText(this, "Without location, move the map by hand to where you are.", Toast.LENGTH_LONG).show();
    }

    // ---- tutorial ----

    private void showTutorial() {
        if (tutorial != null) return;
        tutorial = new Tutorial(this, p, this::closeTutorial);
        tutorial.setInsets(insetTop, insetBottom);
        root.addView(tutorial, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeTutorial() {
        if (tutorial == null) return;
        root.removeView(tutorial);
        tutorial = null;
        boolean first = !prefs.getBoolean("tutorial_done", false);
        prefs.edit().putBoolean("tutorial_done", true).apply();
        if (first && !prefs.contains("cam_lat")) locate(true); // first start: take the user to where they are
    }

    @Override
    public void onBackPressed() {
        if (tutorial != null) closeTutorial(); // the top thing closes first
        else if (trip != null) closeTrip();     // the trip is saved as you go
        else super.onBackPressed();             // nothing to lose: the line is saved as you go
    }

    // ---- trip plan ----

    private void showTrip() {
        if (trip != null) return;
        trip = new Trip(this, p, this);
        trip.setInsets(insetTop, insetBottom);
        root.addView(trip, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override public void closeTrip() {
        if (trip == null) return;
        root.removeView(trip);
        trip = null;
        refresh(); // reminders or the line's name may have changed
    }

    @Override public Trip.Line mapLine() {
        if (you == null || subject == null) return null;
        return new Trip.Line(mapLineId, "", you.getLatitude(), you.getLongitude(),
                subject.getLatitude(), subject.getLongitude(), mode);
    }

    @Override public void mapLineIs(Trip.Line l) {
        // Reminders already set for this line's moments move with it to the trip line.
        long now = System.currentTimeMillis();
        java.util.Set<String> mine = new java.util.HashSet<>();
        for (int k = 0; k < 2; k++) {
            Astro.Found f = k == 0 ? foundSun : foundMoon;
            if (f == null) continue;
            for (Astro.Hit h : new Astro.Hit[]{f.next, f.low}) {
                if (h != null) mine.add(Reminders.key(MAP_LINE, k == 1, h.timeMs));
            }
        }
        for (Reminders.Item i : Reminders.all(this)) {
            if (!MAP_LINE.equals(mapLineId) || !mine.contains(i.key)) continue;
            Reminders.remove(this, i.key);
            if (i.timeMs <= now) continue;
            String rest = i.key.substring(i.key.indexOf(':'));
            Reminders.add(this, new Reminders.Item(l.id + rest, i.timeMs, i.title, l.name + " · " + i.text));
        }
        mapLineId = l.id;
        saveState();
    }

    @Override public void showOnMap(Trip.Line l) {
        you = new GeoPoint(l.youLat, l.youLon);
        subject = new GeoPoint(l.subLat, l.subLon);
        mode = l.mode;
        mapLineId = l.id;
        backupYou = backupSubject = movedFrom = null;
        prefs.edit().putInt("mode", mode).apply();
        saveState();
        closeTrip();
        map.post(() -> {
            org.osmdroid.util.BoundingBox box = org.osmdroid.util.BoundingBox.fromGeoPoints(
                    java.util.Arrays.asList(you, subject));
            map.zoomToBoundingBox(box.increaseByScale(2.2f), false);
        });
    }

    // ---- reminders ----

    @Override public void toggleReminder(Reminders.Item item, Runnable changed) {
        if (Reminders.has(this, item.key)) {
            Reminders.remove(this, item.key);
            Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show();
            changed.run();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = item;
            pendingChanged = changed;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            return;
        }
        setReminder(item, changed);
    }

    private void setReminder(Reminders.Item item, Runnable changed) {
        if (item.timeMs <= System.currentTimeMillis()) return;
        Reminders.add(this, item);
        long at = Reminders.fireAt(item.timeMs);
        Toast.makeText(this, item.timeMs - Reminders.LEAD_MS < System.currentTimeMillis()
                ? "Reminder set: it is less than 30 minutes away, so it comes in a moment"
                : "Reminder set for " + time(at) + ", 30 minutes before", Toast.LENGTH_LONG).show();
        if (changed != null) changed.run();
    }

    // ---- saving as you go ----

    private void loadState() {
        mode = prefs.getInt("mode", 0);
        mapLineId = prefs.getString("map_line_id", MAP_LINE);
        you = load("you");
        subject = load("subject");
        if (prefs.contains("cam_lat")) {
            map.getController().setZoom(Double.parseDouble(prefs.getString("cam_zoom", "3")));
            map.getController().setCenter(new GeoPoint(Double.parseDouble(prefs.getString("cam_lat", "0")),
                    Double.parseDouble(prefs.getString("cam_lon", "0"))));
        } else if (you != null) {
            map.getController().setZoom(14.0);
            map.getController().setCenter(you);
        } else {
            map.getController().setZoom(3.0);
            map.getController().setCenter(new GeoPoint(20.0, 10.0));
        }
    }

    private GeoPoint load(String key) {
        if (!prefs.contains(key + "_lat")) return null;
        return new GeoPoint(Double.parseDouble(prefs.getString(key + "_lat", "0")),
                Double.parseDouble(prefs.getString(key + "_lon", "0")));
    }

    private void saveState() {
        SharedPreferences.Editor e = prefs.edit();
        put(e, "you", you);
        put(e, "subject", subject);
        e.putString("map_line_id", mapLineId);
        e.apply();
    }

    private static void put(SharedPreferences.Editor e, String key, GeoPoint g) {
        if (g == null) {
            e.remove(key + "_lat").remove(key + "_lon");
        } else {
            e.putString(key + "_lat", Double.toString(g.getLatitude()));
            e.putString(key + "_lon", Double.toString(g.getLongitude()));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
        GeoPoint c = (GeoPoint) map.getMapCenter();
        prefs.edit().putString("cam_lat", Double.toString(c.getLatitude()))
                .putString("cam_lon", Double.toString(c.getLongitude()))
                .putString("cam_zoom", Double.toString(map.getZoomLevelDouble())).apply();
    }

    @Override protected void onDestroy() {
        generation++;
        super.onDestroy();
        map.onDetach();
    }

    // ---- the line on the map ----

    private final class LineOverlay extends Overlay {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineOverlay() {
            labelText.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            labelText.setFakeBoldText(true);
            labelText.setTextAlign(Paint.Align.CENTER);
        }

        void update() {}

        @Override
        public void draw(Canvas c, MapView mv, boolean shadow) {
            if (shadow || you == null) return;
            float k = Ui.dp(MainActivity.this, 1);
            Projection pr = mv.getProjection();
            Point a = pr.toPixels(you, null);
            Point b = subject == null ? null : pr.toPixels(subject, null);
            if (b != null) {
                // Where the sun or moon will stand, drawn as a faint dashed continuation of the line.
                float dx = b.x - a.x, dy = b.y - a.y;
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(3 * k);
                paint.setColor(Ui.withAlpha(p.amber, 190));
                paint.setPathEffect(new DashPathEffect(new float[]{2 * k, 8 * k}, 0));
                if (mode == 0) c.drawLine(b.x, b.y, b.x + dx * 1.2f, b.y + dy * 1.2f, paint);
                else c.drawLine(a.x, a.y, a.x - dx * 1.2f, a.y - dy * 1.2f, paint);
                paint.setPathEffect(null);

                paint.setStrokeWidth(4 * k);
                paint.setShader(new LinearGradient(a.x, a.y, b.x, b.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setShader(null);
                dot(c, b, Ui.ROUTE_BLUE, k);
                label(c, "Subject", b.x, b.y - 18 * k, k);
            }
            dot(c, a, Ui.ROUTE_GREEN, k);
            label(c, "You", a.x, a.y - 18 * k, k);
        }

        private void dot(Canvas c, Point pt, int color, float k) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(pt.x, pt.y, 11 * k, paint);
            paint.setColor(color);
            c.drawCircle(pt.x, pt.y, 8 * k, paint);
        }

        private void label(Canvas c, String s, float x, float y, float k) {
            float w = labelText.measureText(s) + 16 * k, h = 22 * k;
            labelBg.setColor(p.panel);
            RectF r = new RectF(x - w / 2, y - h, x + w / 2, y);
            c.drawRoundRect(r, h / 2, h / 2, labelBg);
            labelText.setColor(p.text);
            c.drawText(s, x, y - h / 2 + labelText.getTextSize() * 0.35f, labelText);
        }
    }
}
