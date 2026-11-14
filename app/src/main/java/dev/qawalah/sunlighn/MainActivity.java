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
public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1;

    private SharedPreferences prefs;
    private Ui.Palette p;
    private FrameLayout root;
    private MapView map;
    private LineOverlay lineOverlay;
    private ScaleBarOverlay scaleBar;
    private TextView credit;
    private int style;                          // index into STYLES
    private LinearLayout card;
    private Tutorial tutorial;
    private int insetTop, insetBottom;

    private GeoPoint you, subject;
    private GeoPoint backupYou, backupSubject; // for Undo after Clear
    private int mode;                           // 0 = sun/moon behind the subject, 1 = behind you

    private int generation;
    private Astro.Found foundSun, foundMoon;
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
        FrameLayout.LayoutParams cr = (FrameLayout.LayoutParams) credit.getLayoutParams();
        cr.topMargin = insetTop + Ui.dp(this, 20);
        credit.setLayoutParams(cr);
        scaleBar.setScaleBarOffset(Ui.dp(this, 14), insetTop + Ui.dp(this, 52));
        map.invalidate();
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), insetBottom + Ui.dp(this, 16));
        if (tutorial != null) tutorial.setInsets(insetTop, insetBottom);
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
     * Night look without a dark tile server: invert, turn the hues back so water stays blue and roads stay warm,
     * then dim and tint towards the app's navy. Looks far better than a plain inversion.
     */
    private static ColorMatrixColorFilter nightMapFilter() {
        ColorMatrix cm = new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0});
        cm.postConcat(new ColorMatrix(new float[]{      // hue rotate by 180 degrees
                -0.574f, 1.430f, 0.144f, 0, 0,
                0.426f, 0.430f, 0.144f, 0, 0,
                0.426f, 1.430f, -0.856f, 0, 0,
                0, 0, 0, 1, 0}));
        cm.postConcat(new ColorMatrix(new float[]{      // dim and tint
                0.81f, 0, 0, 0, 6,
                0, 0.87f, 0, 0, 10,
                0, 0, 1.0f, 0, 22,
                0, 0, 0, 1, 0}));
        return new ColorMatrixColorFilter(cm);
    }

    private void applyStyle() {
        MapStyle st = styles[style];
        map.setTileSource(st.source);
        TilesOverlay tiles = map.getOverlayManager().getTilesOverlay();
        tiles.setLoadingBackgroundColor(p.bg);   // no grey grid while tiles load
        tiles.setLoadingLineColor(p.line);
        if (p.night && style == 0) {
            tiles.setColorFilter(nightMapFilter());
        } else if (p.night) {
            // The terrain map is bright; just dim it.
            tiles.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    0.55f, 0, 0, 0, 0,
                    0, 0.55f, 0, 0, 0,
                    0, 0, 0.6f, 0, 6,
                    0, 0, 0, 1, 0})));
        } else {
            tiles.setColorFilter(null);
        }
        if (credit != null) credit.setText(st.credit);
        map.invalidate();
    }

    private void cycleStyle() {
        style = (style + 1) % styles.length;
        prefs.edit().putInt("style", style).apply();
        applyStyle();
        Toast.makeText(this, styles[style].name, Toast.LENGTH_SHORT).show();
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
        txt.setFakeBoldText(true);
        scaleBar.setTextPaint(txt);
        map.getOverlays().add(scaleBar);
        lineOverlay = new LineOverlay();
        map.getOverlays().add(lineOverlay);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));
    }

    private void onMapTap(GeoPoint pt) {
        if (tutorial != null) return;
        backupYou = backupSubject = null; // a new tap ends the chance to Undo
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
        saveState();
        refresh();
    }

    // ---- controls ----

    private LinearLayout topBar;

    private void buildControls() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.END);
        Ui.IconButton locate = new Ui.IconButton(this, p, Ui.Icon.LOCATE, "Go to my location");
        Ui.IconButton help = new Ui.IconButton(this, p, Ui.Icon.HELP, "How it works");
        Ui.IconButton layers = new Ui.IconButton(this, p, Ui.Icon.LAYERS, "Change map style");
        layers.setOnClickListener(v -> cycleStyle());
        locate.setOnClickListener(v -> locate(true));
        help.setOnClickListener(v -> showTutorial());
        topBar.addView(layers);
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
        ScrollView sv = new ScrollView(this);
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
        saveState();
        refresh();
    }

    private void setMode(int m) {
        if (m == mode) return;
        mode = m;
        prefs.edit().putInt("mode", mode).apply();
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
        working = true;
        renderResults();
        new Thread(() -> {
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
                    + "Try a line that points more east or west.", false);
            return;
        }
        addHit(f.next, "Next", moon);
        if (f.low != null && f.low.timeMs != f.next.timeMs) {
            addHit(f.low, moon ? "Low, bright moon" : "Low sun", moon);
        }
    }

    private void addHit(Astro.Hit h, String label, boolean moon) {
        addLine(when(h.timeMs), true);
        String alt = h.alt < 0.5 ? "on the horizon" : Math.round(h.alt) + "° above the horizon";
        String detail = label + " · " + alt + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : "");
        addLine(detail, false);
    }

    private void addLine(String s, boolean strong) {
        TextView t = Ui.text(this, s, strong ? 16 : 13, strong, strong ? p.text : p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(this, strong ? 8 : 2);
        resultsBox.addView(t, l);
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
        else super.onBackPressed();             // nothing to lose: the line is saved as you go
    }

    // ---- saving as you go ----

    private void loadState() {
        mode = prefs.getInt("mode", 0);
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

        LineOverlay() {
            labelText.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            labelText.setFakeBoldText(true);
            labelText.setTextAlign(Paint.Align.CENTER);
            labelEdge.setStyle(Paint.Style.STROKE);
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
                float dx = b.x - a.x, dy = b.y - a.y;
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);

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

                // The line itself: a soft outline so it reads on any map, then the green to blue stroke.
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8 * k);
                paint.setColor(Ui.withAlpha(p.night ? 0xFF000000 : 0xFFFFFFFF, 140));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setStrokeWidth(4 * k);
                paint.setShader(new LinearGradient(a.x, a.y, b.x, b.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setShader(null);
                dot(c, b, Ui.ROUTE_BLUE, k);
                label(c, "Subject", b.x, b.y - 20 * k, k);
            }
            dot(c, a, Ui.ROUTE_GREEN, k);
            label(c, "You", a.x, a.y - 20 * k, k);
        }

        private void dot(Canvas c, Point pt, int color, float k) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(color, 55));
            c.drawCircle(pt.x, pt.y, 20 * k, paint);   // soft halo
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(pt.x, pt.y, 11 * k, paint);
            paint.setColor(color);
            c.drawCircle(pt.x, pt.y, 8 * k, paint);
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
