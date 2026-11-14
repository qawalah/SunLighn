package dev.qawalah.sunlighn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
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
    private android.animation.ValueAnimator dropAnim;
    private float dropT = 1f;                   // 0 = pin high above the point, 1 = on it
    private ScaleBarOverlay scaleBar;
    private TextView credit;
    private int style;                          // index into STYLES
    private LinearLayout card;
    private Tutorial tutorial;
    private SettingsSheet settings;
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

    private int tab;                            // 0 = Times, 1 = Day timeline
    private int dayOffset;                      // days from today shown on the Day tab
    private long cursorMs = System.currentTimeMillis();
    private boolean pendingSetYou;              // "Use my location" waiting for a fix

    // One day of sun and moon, for the Day tab and for drawing it on the map.
    private static final int DAY_N = 145;       // every 10 minutes
    private long dayStart, dayEnd;
    private final double[] sunAlt = new double[DAY_N], sunAz = new double[DAY_N];
    private final double[] moonAlt = new double[DAY_N], moonAz = new double[DAY_N];
    private java.util.List<Astro.Hit> sunHits, moonHits;
    private LinearLayout body;                  // what the card shows under the tabs
    private int focalIndex = 2;                 // into FrameMath.FOCALS
    private boolean portrait;
    private double manualPitch = Double.NaN;    // NaN = aim automatically
    private boolean cursorTouched;              // the user chose a time, so do not jump it for them
    private FrameView frameView;
    private TextView timeLabel;
    private TextView sunRead, moonRead, dayLabel;
    private Ui.GlyphView moonReadGlyph;

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

        if (savedInstanceState != null && savedInstanceState.getBoolean("settings")) showSettings();
        else if (!prefs.getBoolean("tutorial_done", false)) showTutorial();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("settings", settings != null);   // a theme change restarts the screen; keep the sheet open
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
        FrameLayout.LayoutParams cr = (FrameLayout.LayoutParams) credit.getLayoutParams();
        cr.topMargin = insetTop + Ui.dp(this, 20);
        credit.setLayoutParams(cr);
        scaleBar.setScaleBarOffset(Ui.dp(this, 14), insetTop + Ui.dp(this, 52));
        map.invalidate();
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), insetBottom + Ui.dp(this, 16));
        if (tutorial != null) tutorial.setInsets(insetTop, insetBottom);
        if (settings != null) settings.setInsets(insetBottom);
        if (trip != null) trip.setInsets(insetTop, insetBottom);
    }

    // ---- map ----

    /** The map looks. The first is chosen by the phone's theme; the others are one tap away on the layers button. */
    private static final class MapStyle {
        final String name, credit;
        final ITileSource source;
        MapStyle(String name, String credit, ITileSource source) {
            this.name = name;
            this.credit = credit;
            this.source = source;
        }
    }

    private MapStyle[] styles;

    private void buildStyles() {
        styles = new MapStyle[]{
                new MapStyle("Map", "© OpenStreetMap contributors", TileSourceFactory.MAPNIK),
                new MapStyle("Terrain", "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
                        new XYTileSource("opentopomap", 0, 17, 256, ".png",
                                new String[]{"https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/",
                                        "https://c.tile.opentopomap.org/"})),
        };
    }

    /**
     * The map is graded so it stays quiet and the line and pin lead. Day: softened, almost grey, a little cool.
     * Night: no dark tile server needs a key, so invert, turn the hues back (water stays blue, roads stay warm),
     * soften and tint towards the app's navy.
     */
    private ColorMatrixColorFilter mapFilter(boolean terrain) {
        ColorMatrix cm = new ColorMatrix();
        if (p.night) {
            if (terrain) {
                cm.postConcat(new ColorMatrix(new float[]{0.55f, 0, 0, 0, 0, 0, 0.55f, 0, 0, 0, 0, 0, 0.6f, 0, 6, 0, 0, 0, 1, 0}));
                return new ColorMatrixColorFilter(cm);
            }
            cm.postConcat(new ColorMatrix(new float[]{
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0}));
            cm.postConcat(new ColorMatrix(new float[]{      // hue rotate by 180 degrees
                    -0.574f, 1.430f, 0.144f, 0, 0,
                    0.426f, 0.430f, 0.144f, 0, 0,
                    0.426f, 1.430f, -0.856f, 0, 0,
                    0, 0, 0, 1, 0}));
            ColorMatrix soft = new ColorMatrix();
            soft.setSaturation(0.7f);
            cm.postConcat(soft);
            cm.postConcat(new ColorMatrix(new float[]{      // dim and tint
                    0.80f, 0, 0, 0, 6,
                    0, 0.86f, 0, 0, 10,
                    0, 0, 1.0f, 0, 22,
                    0, 0, 0, 1, 0}));
        } else {
            cm.setSaturation(terrain ? 0.6f : 0.42f);
            cm.postConcat(new ColorMatrix(new float[]{      // lighten and cool a little
                    0.94f, 0, 0, 0, 12,
                    0, 0.96f, 0, 0, 13,
                    0, 0, 1.0f, 0, 12,
                    0, 0, 0, 1, 0}));
        }
        return new ColorMatrixColorFilter(cm);
    }

    private void applyStyle() {
        MapStyle st = styles[style];
        map.setTileSource(st.source);
        TilesOverlay tiles = map.getOverlayManager().getTilesOverlay();
        tiles.setLoadingBackgroundColor(p.bg);   // no grey grid while tiles load
        tiles.setLoadingLineColor(p.line);
        tiles.setColorFilter(mapFilter(style == 1));
        if (credit != null) credit.setText(st.credit);
        map.invalidate();
    }

    private void setStyle(int i) {
        style = i;
        prefs.edit().putInt("style", style).apply();
        applyStyle();
        if (settings != null) settings.setMapStyle(style);
    }

    private void buildMap() {
        style = Math.min(prefs.getInt("style", 0), 1);
        buildStyles();
        map = new MapView(this);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true); // street names stay readable on a phone screen
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(2.0);
        map.setBackgroundColor(p.bg);
        applyStyle();
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint pt) {
                onMapTap(pt);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint pt) { return false; }
        }));
        scaleBar = new ScaleBarOverlay(map);
        scaleBar.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric);
        scaleBar.setAlignBottom(false);
        scaleBar.setLineWidth(2f);
        scaleBar.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(p.text);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        scaleBar.setBarPaint(bar);
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(p.text);
        txt.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        txt.setTypeface(Ui.font(this, 600));
        scaleBar.setTextPaint(txt);
        map.getOverlays().add(scaleBar);
        lineOverlay = new LineOverlay();
        map.getOverlays().add(lineOverlay);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));
    }

    /** The pin drops onto the point with a small bounce (it settles at once if the phone has animations off). */
    private void dropPin() {
        if (dropAnim != null) dropAnim.cancel();
        dropAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        dropAnim.setDuration(520);
        dropAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        dropAnim.addUpdateListener(a -> {
            dropT = (float) a.getAnimatedValue();
            map.invalidate();
        });
        dropAnim.start();
    }

    private void onMapTap(GeoPoint pt) {
        if (tutorial != null || settings != null || trip != null) return;
        backupYou = backupSubject = null; // a new tap ends the chance to Undo
        movedFrom = null;
        if (you == null) {
            you = pt;
            dropPin();
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
            if (da <= db) { you = pt; dropPin(); } else subject = pt;
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
        Ui.IconButton gear = new Ui.IconButton(this, p, Ui.Icon.SETTINGS, "Settings");
        locate.setOnClickListener(v -> locate(true));
        gear.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.setMarginStart(Ui.dp(this, 8));
        topBar.addView(locate, gl);
        topBar.addView(gear, gl);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.setMarginEnd(Ui.dp(this, 12));
        tp.topMargin = Ui.dp(this, 12);
        root.addView(topBar, tp);

        // Map credit, required by the map data licences. Small, top left, out of the way.
        credit = Ui.text(this, styles[style].credit, 11, false, p.muted);
        credit.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        credit.setBackground(Ui.shape(this, Ui.withAlpha(p.panel, 220), 0, 99, false));
        FrameLayout.LayoutParams crp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        crp.setMarginStart(Ui.dp(this, 12));
        crp.topMargin = Ui.dp(this, 12);
        root.addView(credit, crp);

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
        generation++; // anything still being worked out is now out of date
        foundSun = foundMoon = null;
        working = false;
        cursorTouched = false;
        manualPitch = Double.NaN;
        map.invalidate();
        if (you != null && subject != null) compute();
        buildCard();
    }

    private void buildCard() {
        card.removeAllViews();
        body = null;
        if (you == null || subject == null) {
            renderSteps();
            map.invalidate();
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

        // Which end of the line, and which view.
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

        LinearLayout tabs = new LinearLayout(this);
        TextView t0 = Ui.chip(this, p, "Times", tab == 0);
        TextView t1 = Ui.chip(this, p, "Day", tab == 1);
        TextView t2 = Ui.chip(this, p, "Frame", tab == 2);
        t0.setOnClickListener(v -> setTab(0));
        t1.setOnClickListener(v -> setTab(1));
        t2.setOnClickListener(v -> setTab(2));
        tabs.addView(t0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-2, -2);
        l2.setMarginStart(Ui.dp(this, 8));
        tabs.addView(t1, l2);
        tabs.addView(t2, l2);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(this, 8);
        card.addView(tabs, tl);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        Ui.CappedScrollView sv = new Ui.CappedScrollView(this, 0.42f);
        sv.addView(body);
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, -2);
        svl.topMargin = Ui.dp(this, 4);
        card.addView(sv, svl);

        TextView note = Ui.text(this, "Drag a point on the map to move it · times in " + TimeZone.getDefault().getID(),
                12.5f, false, p.muted);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 10);
        card.addView(note, nl);

        renderBody();
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        buildCard();
        map.invalidate();
    }

    private void renderBody() {
        body.removeAllViews();
        if (tab == 0) renderResults();
        else if (tab == 1) renderDay();
        else renderFrame();
        map.invalidate();
    }

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
        LinearLayout actions = new LinearLayout(this);
        if (you == null) {
            TextView here = Ui.button(this, p, "Use my location", false);
            here.setOnClickListener(v -> {
                pendingSetYou = true;
                locate(true);
            });
            actions.addView(here);
        }
        if (backupYou != null) {
            TextView undo = Ui.button(this, p, "Undo clear", false);
            undo.setOnClickListener(v -> {
                you = backupYou;
                subject = backupSubject;
                backupYou = backupSubject = null;
                dropPin();
                saveState();
                refresh();
            });
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-2, -2);
            if (actions.getChildCount() > 0) ul.setMarginStart(Ui.dp(this, 8));
            actions.addView(undo, ul);
        }
        if (actions.getChildCount() > 0) {
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.topMargin = Ui.dp(this, 12);
            card.addView(actions, al);
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
                if (body != null && (tab == 0 || tab == 2)) renderBody();
            });
            Astro.Found m = Astro.search(true, lat, lon, b, now);
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundMoon = m;
                working = false;
                if (body != null && (tab == 0 || tab == 2)) renderBody();
            });
        }).start();
    }

    private void renderResults() {
        addSection("Sun", foundSun, false);
        addSection("Moon", foundMoon, true);
        addWhereToStand();
    }

    private void addSection(String name, Astro.Found f, boolean moon) {
        TextView t = Ui.text(this, name, 16, true, p.text);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, body.getChildCount() == 0 ? 8 : 18);
        body.addView(t, hl);

        if (f == null) {
            addLine(working ? "Working it out…" : "");
            return;
        }
        if (f.next == null) {
            addLine("The " + name.toLowerCase(Locale.US) + " never lines up with this line this year. "
                    + (moon ? "Try a line that points more east or west."
                    : "Try Where to stand below, or a line that points more east or west."));
            return;
        }
        addHit(f.next, "Next", moon, false);
        if (f.low != null && f.low.timeMs != f.next.timeMs) {
            addHit(f.low, moon ? "Low, bright moon" : "Low sun", moon, true);
        }
    }

    /** One answer: a round badge with the sun or moon (on the horizon for the low ones), the time and the details. */
    private void addHit(Astro.Hit h, String label, boolean moon, boolean low) {
        int color = moon ? p.violet : p.amber;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.shape(this, p.raised, p.line, 24, false));
        Ui.GlyphView.Kind kind = moon ? (low ? Ui.GlyphView.Kind.MOON_HORIZON : Ui.GlyphView.Kind.MOON)
                : (low ? Ui.GlyphView.Kind.SUN_HORIZON : Ui.GlyphView.Kind.SUN);
        Ui.GlyphView glyph = new Ui.GlyphView(this, kind, color, 26);
        if (moon) glyph.setMoon((float) h.lit, Astro.moonWaxing(h.timeMs));
        badge.addView(glyph, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, when(h.timeMs), 16, true, p.text));
        String alt = h.alt < 0.5 ? "on the horizon" : Math.round(h.alt) + "° above the horizon";
        TextView detail = Ui.text(this, label + " · " + alt + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : ""),
                13, false, p.muted);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = Ui.dp(this, 3);
        col.addView(detail, dl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.setMarginStart(Ui.dp(this, 12));
        row.addView(col, cl);

        // The bell: a notification 30 minutes before this moment.
        String key = Reminders.key(mapLineId, moon, h.timeMs);
        boolean on = Reminders.has(this, key);
        Ui.IconButton bell = new Ui.IconButton(this, p, on ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                on ? "Remove the reminder" : "Remind me 30 minutes before").small();
        String what = (moon ? "Moon " : "Sun ") + (mode == 0 ? "behind subject" : "behind you");
        String lineName = lineName();
        String detailText = label + " · " + alt + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : "");
        bell.setOnClickListener(v -> toggleReminder(new Reminders.Item(key, h.timeMs, what + " at " + time(h.timeMs),
                (lineName == null ? "" : lineName + " · ") + detailText), this::renderBody));
        row.addView(bell);

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 10);
        body.addView(row, rl);
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
        body.addView(head, hl);
        addLine("Keep the subject, move you so the low sun sits " + (mode == 0 ? "behind the subject." : "behind you."));
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
            body.addView(undo, ul);
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
            body.addView(row, rl);
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

    private void addLine(String s) {
        TextView t = Ui.text(this, s, 13, false, p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(this, 6);
        body.addView(t, l);
    }

    // ---- the Day tab: where the sun and the moon go ----

    private void buildDay() {
        double lat = you.getLatitude(), lon = you.getLongitude(), bearing = lineBearing();
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        dayStart = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, 1);
        dayEnd = c.getTimeInMillis();
        for (int i = 0; i < DAY_N; i++) {
            long t = dayStart + (dayEnd - dayStart) * i / (DAY_N - 1);
            double[] s = Astro.sun(t, lat, lon), m = Astro.moon(t, lat, lon);
            sunAz[i] = s[0];
            sunAlt[i] = s[1];
            moonAz[i] = m[0];
            moonAlt[i] = m[1];
        }
        sunHits = Astro.hitsBetween(false, lat, lon, bearing, dayStart, dayEnd);
        moonHits = Astro.hitsBetween(true, lat, lon, bearing, dayStart, dayEnd);
        if (cursorMs < dayStart || cursorMs >= dayEnd) cursorMs = dayStart + (dayEnd - dayStart) / 2; // midday
    }

    private SimpleDateFormat timeFormat() {
        return new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(this) ? "Hm" : "jm"), Locale.getDefault());
    }

    private long midnight(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void renderDay() {
        dayOffset = (int) Math.round((midnight(cursorMs) - midnight(System.currentTimeMillis())) / 86400000.0);
        buildDay();

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton prev = new Ui.IconButton(this, p, Ui.Icon.CHEV_LEFT, "Previous day");
        Ui.IconButton next = new Ui.IconButton(this, p, Ui.Icon.CHEV_RIGHT, "Next day");
        prev.setOnClickListener(v -> moveDay(-1));
        next.setOnClickListener(v -> moveDay(1));
        dayLabel = Ui.text(this, dayName(), 16, true, p.text);
        dayLabel.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(dayLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 6);
        body.addView(nav, nl);

        DayChart chart = new DayChart(this, p);
        chart.setData(dayStart, dayEnd, sunAlt, moonAlt, sunHits, moonHits, timeFormat());
        chart.setCursor(cursorMs);
        chart.setListener(ms -> {
            cursorMs = ms;
            cursorTouched = true;
            updateReadouts();
            map.invalidate();
        });
        body.addView(chart, new LinearLayout.LayoutParams(-1, -2));

        addReadouts();
        TextView hint = Ui.text(this, "Drag along the day. The map shows the sun and moon at that time.",
                12.5f, false, p.muted);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 10);
        body.addView(hint, hl);
        updateReadouts();
    }

    /** Two lines: where the sun and the moon are at the chosen time. Shared by the Day and Frame tabs. */
    private void addReadouts() {
        sunRead = Ui.text(this, "", 14, false, p.text);
        moonRead = Ui.text(this, "", 14, false, p.text);
        body.addView(readRow(new Ui.GlyphView(this, Ui.GlyphView.Kind.SUN, p.amber, 24), sunRead));
        moonReadGlyph = new Ui.GlyphView(this, Ui.GlyphView.Kind.MOON, p.violet, 24);
        body.addView(readRow(moonReadGlyph, moonRead));
    }

    // ---- the Frame tab: how the sun and moon look in your camera ----

    private void renderFrame() {
        if (!cursorTouched && foundSun != null && foundSun.next != null) cursorMs = foundSun.next.timeMs;

        frameView = new FrameView(this, p);
        frameView.setListener(new FrameView.Listener() {
            @Override public void onScrub(long deltaMs) { setCursor(cursorMs + deltaMs); }
            @Override public void onTilt(double pitch) {
                manualPitch = Math.max(-30, Math.min(89, pitch));
                updateFrame();
            }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = Ui.dp(this, 8);
        body.addView(frameView, fl);

        // Lens and orientation.
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout lens = new LinearLayout(this);
        TextView orient = Ui.chip(this, p, portrait ? "Portrait" : "Landscape", false);
        orient.setOnClickListener(v -> {
            portrait = !portrait;
            manualPitch = Double.NaN;
            prefs.edit().putBoolean("portrait", portrait).apply();
            renderBody();
        });
        lens.addView(orient);
        for (int i = 0; i < FrameMath.FOCALS.length; i++) {
            final int fi = i;
            TextView chip = Ui.chip(this, p, FrameMath.FOCALS[i] + " mm", focalIndex == i);
            chip.setOnClickListener(v -> {
                focalIndex = fi;
                manualPitch = Double.NaN;
                prefs.edit().putInt("focal", fi).apply();
                renderBody();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            l.setMarginStart(Ui.dp(this, 8));
            lens.addView(chip, l);
        }
        hs.addView(lens);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 10);
        body.addView(hs, hl);

        // The time, and a jump to the moment each one lines up.
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeLabel = Ui.text(this, when(cursorMs), 16, true, p.text);
        timeRow.addView(timeLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        if (foundSun != null && foundSun.next != null) {
            final long t = foundSun.next.timeMs;
            TextView j = Ui.chip(this, p, "Sun on the line", false);
            j.setOnClickListener(v -> setCursor(t));
            timeRow.addView(j);
        }
        if (foundMoon != null && foundMoon.next != null) {
            final long t = foundMoon.next.timeMs;
            TextView j = Ui.chip(this, p, "Moon on the line", false);
            j.setOnClickListener(v -> setCursor(t));
            LinearLayout.LayoutParams jl = new LinearLayout.LayoutParams(-2, -2);
            jl.setMarginStart(Ui.dp(this, 8));
            timeRow.addView(j, jl);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.dp(this, 10);
        body.addView(timeRow, tl);

        addReadouts();
        TextView hint = Ui.text(this, "Drag sideways to change the time, up or down to tilt. The wedge on the map is what the lens sees.",
                12.5f, false, p.muted);
        LinearLayout.LayoutParams ht = new LinearLayout.LayoutParams(-1, -2);
        ht.topMargin = Ui.dp(this, 10);
        body.addView(hint, ht);
        updateFrame();
        updateReadouts();
    }

    private void setCursor(long ms) {
        cursorMs = ms;
        cursorTouched = true;
        if (timeLabel != null) timeLabel.setText(when(cursorMs));
        updateFrame();
        updateReadouts();
        map.invalidate();
    }

    private void updateFrame() {
        if (frameView == null || you == null || subject == null) return;
        double lat = you.getLatitude(), lon = you.getLongitude();
        double[] s = Astro.sun(cursorMs, lat, lon), m = Astro.moon(cursorMs, lat, lon);
        double focal = FrameMath.FOCALS[focalIndex];
        double aim = Astro.bearing(lat, lon, subject.getLatitude(), subject.getLongitude());
        double pitch = Double.isNaN(manualPitch)
                ? FrameMath.autoPitch(FrameMath.fovV(focal, portrait), s[1] >= 0 ? s[1] : m[1]) : manualPitch;
        frameView.setLens(focal, portrait);
        frameView.setAim(aim, pitch);
        frameView.setBodies(s[0], s[1], m[0], m[1], Astro.moonLit(cursorMs), Astro.moonWaxing(cursorMs));
    }

    private LinearLayout readRow(View glyph, TextView text) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(glyph);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(this, 10));
        row.addView(text, tl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 8);
        row.setLayoutParams(rl);
        return row;
    }

    private void moveDay(int delta) {
        dayOffset += delta;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(cursorMs);
        c.add(Calendar.DAY_OF_YEAR, delta);   // keep the same time of day
        cursorMs = c.getTimeInMillis();
        renderBody();
    }

    private String dayName() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart + 3600000);
        Calendar now = Calendar.getInstance();
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String date = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"),
                Locale.getDefault()).format(c.getTime());
        if (sameDay(c, now)) return "Today · " + date;
        if (sameDay(c, tomorrow)) return "Tomorrow · " + date;
        if (sameDay(c, yesterday)) return "Yesterday · " + date;
        return date;
    }

    /** Where the sun and moon are at the cursor, in words. */
    private void updateReadouts() {
        if (sunRead == null || moonRead == null) return;
        double lat = you.getLatitude(), lon = you.getLongitude(), bearing = lineBearing();
        double[] s = Astro.sun(cursorMs, lat, lon), m = Astro.moon(cursorMs, lat, lon);
        sunRead.setText(readout(s, bearing, null));
        float lit = (float) Astro.moonLit(cursorMs);
        moonReadGlyph.setMoon(lit, Astro.moonWaxing(cursorMs));
        moonRead.setText(readout(m, bearing, Math.round(lit * 100) + "% lit"));
    }

    private String readout(double[] pos, double bearing, String extra) {
        String where = Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0]);
        String height = pos[1] >= 0 ? Math.round(pos[1]) + "° up" : "below the horizon";
        double off = Math.abs(((pos[0] - bearing + 540) % 360) - 180);
        String out = where + " · " + height;
        if (extra != null) out += " · " + extra;
        if (off < 0.6 && pos[1] >= -1) out += " · on your line";
        return out;
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
            pendingSetYou = false;
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
        GeoPoint g = new GeoPoint(l.getLatitude(), l.getLongitude());
        map.getController().setZoom(15.0);
        map.getController().animateTo(g);
        if (pendingSetYou) {          // "Use my location": that is where you stand
            pendingSetYou = false;
            you = g;
            dropPin();
            saveState();
            refresh();
        }
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
        pendingSetYou = false;
        Toast.makeText(this, "Without location, move the map by hand to where you are.", Toast.LENGTH_LONG).show();
    }

    // ---- tutorial ----

    private void showSettings() {
        if (settings != null) return;
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) names[i] = styles[i].name;
        settings = new SettingsSheet(this, p, names, style, new SettingsSheet.Listener() {
            @Override public void onTheme(int theme) {
                prefs.edit().putInt("theme", theme).apply();
                recreate();                       // the whole screen reads the new theme; everything is saved already
            }
            @Override public void onAccent(int accent) {
                prefs.edit().putInt("accent", accent).apply();
                recreate();
            }
            @Override public void onMapStyle(int s) { setStyle(s); }
            @Override public void onHelp() {
                closeSettings();
                showTutorial();
            }
            @Override public void onClose() { closeSettings(); }
        });
        settings.setInsets(insetBottom);
        root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeSettings() {
        if (settings == null) return;
        root.removeView(settings);
        settings = null;
    }

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
        if (tutorial != null) closeTutorial();       // the top thing closes first
        else if (settings != null) closeSettings();
        else if (trip != null) closeTrip();           // the trip is saved as you go
        else super.onBackPressed();                    // nothing to lose: the line is saved as you go
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
        focalIndex = Math.max(0, Math.min(FrameMath.FOCALS.length - 1, prefs.getInt("focal", 2)));
        portrait = prefs.getBoolean("portrait", false);
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
        private final Paint labelEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path trail = new android.graphics.Path();
        private int drag;   // 0 none, 1 you, 2 subject

        LineOverlay() {
            labelText.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            labelText.setTypeface(Ui.font(MainActivity.this, 700));
            labelText.setTextAlign(Paint.Align.CENTER);
            labelEdge.setStyle(Paint.Style.STROKE);
        }

        // Grab a point and drag it. Dragging does not pan the map; dropping it works out the answer again.
        @Override
        public boolean onTouchEvent(android.view.MotionEvent e, MapView mv) {
            if (tutorial != null || settings != null || you == null) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    Projection pr = mv.getProjection();
                    float reach = Ui.dp(MainActivity.this, 30);
                    Point a = pr.toPixels(you, null);
                    float bodyUp = 9 * 2f * Ui.dp(MainActivity.this, 1);   // the pin's round head sits above its tip
                    double da = Math.min(Math.hypot(e.getX() - a.x, e.getY() - a.y),
                            Math.hypot(e.getX() - a.x, e.getY() - (a.y - bodyUp)));
                    double db = Double.MAX_VALUE;
                    if (subject != null) {
                        Point b = pr.toPixels(subject, null);
                        db = Math.hypot(e.getX() - b.x, e.getY() - b.y);
                    }
                    if (Math.min(da, db) > reach) return false;
                    drag = db <= da ? 2 : 1;
                    backupYou = backupSubject = null;
                    mv.invalidate();
                    return true;
                }
                case android.view.MotionEvent.ACTION_MOVE:
                    if (drag == 0) return false;
                    org.osmdroid.api.IGeoPoint g = mv.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                    GeoPoint moved = new GeoPoint(g.getLatitude(), g.getLongitude());
                    if (drag == 1) you = moved; else subject = moved;
                    mv.invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (drag == 0) return false;
                    boolean wasYou = drag == 1;
                    drag = 0;
                    if (wasYou) dropPin();
                    saveState();
                    refresh();
                    return true;
                default:
                    return drag != 0;
            }
        }

        @Override
        public void draw(Canvas c, MapView mv, boolean shadow) {
            if (shadow || you == null) return;
            float k = Ui.dp(MainActivity.this, 1);
            Projection pr = mv.getProjection();
            Point a = pr.toPixels(you, null);
            Point b = subject == null ? null : pr.toPixels(subject, null);
            if (b != null) {
                float dx = b.x - a.x, dy = b.y - a.y;
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);

                if (tab == 1) {
                    drawDay(c, a, k);
                } else if (tab == 2) {
                    drawLens(c, a, k);
                } else {
                    // Where the sun or moon will stand: a dotted amber continuation ending in a small sun.
                    float ex, ey;
                    if (mode == 0) { ex = b.x + dx * 1.2f; ey = b.y + dy * 1.2f; }
                    else { ex = a.x - dx * 1.2f; ey = a.y - dy * 1.2f; }
                    float sx = mode == 0 ? b.x : a.x, sy = mode == 0 ? b.y : a.y;
                    paint.setStrokeWidth(5 * k);
                    paint.setColor(Ui.withAlpha(p.bg, 150));
                    paint.setPathEffect(new DashPathEffect(new float[]{0.1f, 9 * k}, 0));
                    c.drawLine(sx, sy, ex, ey, paint);
                    paint.setStrokeWidth(3 * k);
                    paint.setColor(p.amber);
                    c.drawLine(sx, sy, ex, ey, paint);
                    paint.setPathEffect(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Ui.withAlpha(p.amber, 70));
                    c.drawCircle(ex, ey, 18 * k, paint);
                    paint.setColor(0xFFFFFFFF);
                    c.drawCircle(ex, ey, 12 * k, paint);
                    paint.setColor(p.amber);
                    c.drawCircle(ex, ey, 9.5f * k, paint);
                }

                // The line itself: a soft outline so it reads on any map, then the green to blue stroke.
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8 * k);
                paint.setColor(Ui.withAlpha(p.night ? 0xFF000000 : 0xFFFFFFFF, 140));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setStrokeWidth(4 * k);
                paint.setShader(new LinearGradient(a.x, a.y, b.x, b.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setShader(null);
                dot(c, b, Ui.ROUTE_BLUE, k, drag == 2);
                label(c, "Subject", b.x, b.y - 20 * k, k);
            }
            drawYou(c, a, k);
        }

        /** The Frame tab on the map: the wedge the lens sees, and the sun and moon at the chosen time. */
        private void drawLens(Canvas c, Point a, float k) {
            double lat = you.getLatitude(), lon = you.getLongitude();
            double aim = Astro.bearing(lat, lon, subject.getLatitude(), subject.getLongitude());
            double half = FrameMath.fovH(FrameMath.FOCALS[focalIndex], portrait) / 2;
            float r = 128 * k;
            trail.reset();
            trail.moveTo(a.x, a.y);
            int steps = 24;
            for (int i = 0; i <= steps; i++) {
                double az = Math.toRadians(aim - half + 2 * half * i / steps);
                trail.lineTo(a.x + (float) Math.sin(az) * r, a.y - (float) Math.cos(az) * r);
            }
            trail.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(p.accent, 48));
            c.drawPath(trail, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * k);
            paint.setColor(Ui.withAlpha(p.accent, 190));
            c.drawPath(trail, paint);
            body(c, a, Astro.sun(cursorMs, lat, lon), 104 * k, p.amber, 1f, false, k);
            body(c, a, Astro.moon(cursorMs, lat, lon), 78 * k, p.violet, (float) Astro.moonLit(cursorMs), true, k);
        }

        /** The day's paths round you, and the sun and moon where the cursor is on the Day tab. */
        private void drawDay(Canvas c, Point a, float k) {
            if (sunHits == null) return;
            float rSun = 104 * k, rMoon = 78 * k;
            trailOf(c, a, sunAz, sunAlt, rSun, p.amber, k);
            trailOf(c, a, moonAz, moonAlt, rMoon, p.violet, k);
            double lat = you.getLatitude(), lon = you.getLongitude();
            body(c, a, Astro.sun(cursorMs, lat, lon), rSun, p.amber, 1f, false, k);
            body(c, a, Astro.moon(cursorMs, lat, lon), rMoon, p.violet, (float) Astro.moonLit(cursorMs), true, k);
        }

        private void trailOf(Canvas c, Point a, double[] az, double[] alt, float r, int color, float k) {
            trail.reset();
            boolean pen = false;
            for (int i = 0; i < az.length; i++) {
                if (alt[i] < 0) { pen = false; continue; }
                float x = a.x + (float) Math.sin(Math.toRadians(az[i])) * r;
                float y = a.y - (float) Math.cos(Math.toRadians(az[i])) * r;
                if (!pen) { trail.moveTo(x, y); pen = true; } else trail.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(5 * k);
            paint.setColor(Ui.withAlpha(p.bg, 120));
            c.drawPath(trail, paint);
            paint.setStrokeWidth(2.5f * k);
            paint.setColor(Ui.withAlpha(color, 200));
            c.drawPath(trail, paint);
        }

        private void body(Canvas c, Point a, double[] pos, float r, int color, float lit, boolean moon, float k) {
            float x = a.x + (float) Math.sin(Math.toRadians(pos[0])) * r;
            float y = a.y - (float) Math.cos(Math.toRadians(pos[0])) * r;
            boolean up = pos[1] >= 0;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * k);
            paint.setColor(Ui.withAlpha(color, up ? 150 : 70));
            paint.setPathEffect(new DashPathEffect(new float[]{3 * k, 5 * k}, 0));
            c.drawLine(a.x, a.y, x, y, paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            if (!up) {                       // below the horizon: only a hollow ring, so it is clear it cannot be seen
                paint.setColor(p.panel);
                c.drawCircle(x, y, 9 * k, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setColor(Ui.withAlpha(color, 160));
                c.drawCircle(x, y, 9 * k, paint);
                return;
            }
            paint.setColor(Ui.withAlpha(color, 70));
            c.drawCircle(x, y, 19 * k, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, 13 * k, paint);
            paint.setColor(color);
            c.drawCircle(x, y, 10.5f * k, paint);
        }

        /** You: the CrossPaths pin, tip on your point, a green halo, lifted a little while you hold it. */
        private void drawYou(Canvas c, Point a, float k) {
            float u = 2f * k;
            float back = dropT - 1f;
            float eased = 1f + 2.7f * back * back * back + 1.7f * back * back;   // overshoots a little, then settles
            float rise = (eased - 1f) * 26f * u;
            if (drag == 1) rise = -6 * k;
            float fade = Math.min(1f, dropT * 3f);
            Ui.drawPinHalo(c, a.x, a.y, u, Ui.ROUTE_GREEN, drag == 1 ? 1.15f : Math.min(1f, dropT * 2f));
            Ui.drawPin(c, a.x, a.y, u, rise, fade);
            label(c, "You", a.x, a.y + rise - Ui.PIN_HEIGHT * u - 5 * k, k);
        }

        private void dot(Canvas c, Point pt, int color, float k, boolean grabbed) {
            float s = grabbed ? 1.3f : 1f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(color, grabbed ? 90 : 55));
            c.drawCircle(pt.x, pt.y, 20 * k * s, paint);   // soft halo
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(pt.x, pt.y, 11 * k * s, paint);
            paint.setColor(color);
            c.drawCircle(pt.x, pt.y, 8 * k * s, paint);
        }

        private void label(Canvas c, String s, float x, float y, float k) {
            float w = labelText.measureText(s) + 18 * k, h = 24 * k;
            RectF r = new RectF(x - w / 2, y - h, x + w / 2, y);
            labelBg.setColor(p.panel);
            c.drawRoundRect(r, h / 2, h / 2, labelBg);
            labelEdge.setColor(p.line);
            labelEdge.setStrokeWidth(Math.max(1, k));
            c.drawRoundRect(r, h / 2, h / 2, labelEdge);
            labelText.setColor(p.text);
            c.drawText(s, x, y - h / 2 + labelText.getTextSize() * 0.35f, labelText);
        }
    }
}
