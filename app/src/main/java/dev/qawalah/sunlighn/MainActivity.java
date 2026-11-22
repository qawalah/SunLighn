package dev.qawalah.sunlighn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SunLighn: pick your subject, and read where to stand and when the sun and the moon sit right on that line.
 * The map is home. A sheet rises from the bottom with the answer; the Frame shows it as the camera will see it.
 * Everything is saved as you go.
 */
public class MainActivity extends Activity implements Trip.Host, FrameScreen.Host, SearchScreen.Host, ComposeScreen.Host,
        HomeScreen.Host {
    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFY = 2;
    static final int REQ_CAMERA = 3;            // AR (3.5): the camera behind the viewfinder
    private java.util.function.Consumer<Boolean> pendingCamera;
    static final String MAP_LINE = "map"; // reminder id of a line that is not saved

    private SharedPreferences prefs;
    private Ui.Palette p;
    private FrameLayout root;
    private GeoMap map;                         // MapLibre (3D) or osmdroid (flat): GeoMap (3.15)
    private LineLayer lineOverlay;
    private android.animation.ValueAnimator dropAnim;
    private float dropT = 1f;                   // 0 = pin high above the point, 1 = on it
    private TextView credit;
    private int style;                          // index into styles
    private LinearLayout topBar, sideKeys;
    private Ui.IconButton tiltKey;               // the 3D map's key (3.15)
    private Ui.IconButton northKey;              // north up (3.20): its needle points to north as the map turns
    private Ui.IconButton layersKey;
    private Sheet sheet;
    private Tutorial tutorial;
    private SettingsSheet settings;
    private Trip trip;
    private FrameScreen frame;
    private SearchScreen search;
    private ComposeScreen compose;
    private int insetTop, insetBottom;
    private HomeScreen home;
    private Flow flow = new Flow();              // where the user is: Home, or a tool and its step
    private LinearLayout stepRow, searchRow;
    private TextView stepText, bodyText;
    private android.widget.ImageView bodyIcon;
    private float touchX, touchY;               // where the last touch began: a change of colours opens from there

    private GeoPoint you, subject;
    private String subjectName;                 // from the search, until the subject is moved
    private double subjectHeight;               // metres; 0 = not known
    private int subjectKind = 3;                // into FrameMath.KINDS
    private GeoPoint backupYou, backupSubject;  // for Undo
    private int mode;                           // 0 = sun/moon behind the subject, 1 = behind you
    private int lineSits;                       // 3.12: where the body sits on the line: 0 Any, 1 Horizon, 2 Middle, 3 On top, 4 an angle
    private float lineLo, lineHi;               // 3.18: the heights picked on the range slider (lineSits 4), degrees up
    private String mapLineId = MAP_LINE;        // the saved line's id while the map shows one, so reminders match
    private int armed;                          // which point a tap places next: 0 = Subject, 1 = You
    private GeoPoint held;                      // where the map was pressed and held, waiting for "Subject" or "You"

    private Reminders.Item pendingReminder;     // waits for the notification permission
    private Runnable pendingChanged;
    private Trip.Line pendingLine;

    private int generation;
    private Astro.Found foundSun, foundMoon;
    // 3.22 (Ali, 2026-11-20): when the chosen side never lines up, the soonest on the other side (0: none), so the
    // answer can offer it instead of a dead end
    private long otherSideMs;
    private boolean working;
    private TimeZone tz = TimeZone.getDefault(); // the place's time zone

    private int tab;                            // 0 = Times, 1 = Day
    private long searchFrom;                    // look for moments from this day on; 0 = from now
    private int dayOffset;                      // days from today shown on the Day tab
    private long cursorMs = System.currentTimeMillis();
    private DayChart dayChart;      // the Day chart shown (3.20: dragging the sun on the map moves its cursor)
    private long pathMs;            // 3.19.4: on All times, the moment whose day's path is drawn on the map (0: none)
    private long shotMs;                         // the moment of Your shot
    private String savedShotId;                  // Your shot as saved (its id in Saved), or null
    private GeoPoint asideYou;                   // where You was before Where to stand / Build the shot took it off
    private boolean retimeShot;                  // You moved in the viewfinder: Your shot takes the new soonest moment
    private ComposeScreen keptCompose;           // Build it, kept as it was left while Your shot shows
    private String keptComposeKey;
    private boolean pendingSetYou;
    private boolean drone;                      // a drone can be anywhere: nothing is ruled out
    private Astro.Near nearSun, nearMoon;       // the closest each comes to a line it never crosses

    // One day of sun and moon, for the Day tab and for drawing it on the map.
    private static final int DAY_N = 145;       // every 10 minutes
    private long dayStart, dayEnd;
    private final double[] sunAlt = new double[DAY_N], sunAz = new double[DAY_N];
    private final double[] moonAlt = new double[DAY_N], moonAz = new double[DAY_N];
    private List<Astro.Hit> sunHits, moonHits;
    private LinearLayout body;                  // what the sheet shows under the tabs
    private double focal = 35;
    private boolean portrait;
    private TextView sunRead, moonRead;
    private Ui.GlyphView moonReadGlyph;

    // The bar that says what just happened, with a way back.
    private LinearLayout snack;
    private final Runnable hideSnack = () -> { if (snack != null) snack.setVisibility(View.GONE); held = null; if (map != null) map.redraw(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);                   // 3.19.1: the next crash is written down and shown at the next start
        prefs = getSharedPreferences("sunlighn", MODE_PRIVATE);
        UserBuildings.load(this);
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

        Trip.migrate(this);                       // 2.1 lines and planned shoots become shots, once
        buildMap();
        buildChrome();
        loadState();
        refresh();
        // The app opens on Home; Continue takes the user back. A restart of the screen itself (dark mode switching
        // at sunset, a theme change) goes straight back to the step the user was on.
        if (savedInstanceState != null && savedInstanceState.getBoolean("in_tool")) {
            continueTool();
            // 3.23 (sweep S8, S9): a restart keeps where Back goes from a shot opened in Saved, and the viewfinder open
            fromSaved = savedInstanceState.getBoolean("from_saved") && flow.current() == Flow.SHOT;
            if (savedInstanceState.getBoolean("frame")) {
                final long at = savedInstanceState.getLong("frame_ms", 0);
                root.post(() -> { if (flow.current() == Flow.SHOT) showFrame(at > 0 ? at : shotMs); });
            }
        } else showHome();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            applyInsets();
            return insets;
        });

        if (GeoMap.fellBack(this)) {
            say("The 3D map could not start on this phone: the flat map is back. Settings, 3D map, to try again.");
        }
        if (savedInstanceState != null && savedInstanceState.getBoolean("settings")) showSettings();
        else if (!prefs.getBoolean("tutorial_done", false)) showTutorial();
        Reveal.resume(root);                      // a theme or accent change: the new colours open from the finger
        if (savedInstanceState == null) root.post(() -> handleLink(getIntent()));   // opened by a shared shot's link
        root.post(() -> CrashLog.show(this));     // what stopped the app last time, if anything
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLink(intent);
    }

    // ---- sharing a shot by a link (3.18) ----

    /** A link to a shot opened the app: say what it holds and offer to save it. Anything else is ignored. */
    private void handleLink(android.content.Intent intent) {
        if (intent == null || !android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        android.net.Uri u = intent.getData();
        intent.setData(null);                                   // once: a restart does not ask again
        Trip.Line l = ShotLink.decode(ShotLink.codeOf(u));
        if (l == null) {
            say("That link does not hold a shot SunWhere can open.");
            return;
        }
        String who = l.name == null || l.name.isEmpty() ? "A shared shot" : l.name;
        java.util.TimeZone zone = Zones.of(this, l.subLat, l.subLon);
        String when = l.timeMs > 0 ? Fmt.when(this, l.timeMs, zone) : "any time it lines up";
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(who)
                .setMessage((l.moon ? "Moon " : "Sun ") + l.where() + "\n" + when + "\n\nSave it to your shots and open it?")
                .setPositiveButton("Save and open", (d, w) -> {
                    Trip.Line saved = Trip.addShot(this, l);
                    if (trip != null) trip.reload();
                    openShot(saved);
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    /** Share on Your shot: the moment in words, and the link that opens it on the other phone. */
    private void shareShot() {
        Trip.Line onMap = mapLine();
        if (onMap == null) return;
        Trip.Line have = savedShot();
        Trip.Line d = new Trip.Line("share", have != null ? have.name : subjectName == null ? "" : subjectName,
                onMap.youLat, onMap.youLon, onMap.subLat, onMap.subLon, mode);
        d.height = subjectHeight;
        d.kind = subjectKind;
        d.timeMs = shotMs;
        d.moon = flow.moon;
        d.subjectName = subjectName;
        d.panDeg = shotPan;
        d.pitch = shotPitch;
        String words = (flow.moon ? "Moon " : "Sun ") + where() + (subjectName == null ? "" : " (" + subjectName + ")") + "\n"
                + Fmt.when(this, shotMs, tz) + " (" + Fmt.zoneName(tz) + ")\nOpen it in SunWhere: " + ShotLink.url(d);
        try {
            startActivity(android.content.Intent.createChooser(new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, words), "Share this shot"));
        } catch (android.content.ActivityNotFoundException e) {
            say("No app on this phone can share text.");
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            touchX = ev.getX();
            touchY = ev.getY();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("settings", settings != null);   // a theme change restarts the screen; keep the sheet open
        out.putBoolean("in_tool", home == null && flow.tool != Flow.HOME);
        out.putBoolean("from_saved", fromSaved);
        out.putBoolean("frame", frame != null);
        out.putLong("frame_ms", frame != null ? frame.time() : 0);
    }

    // ---- window ----

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!p.night && frame == null) {   // the viewfinder is always dark: light icons there (3.23, B-19)
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void applyInsets() {
        int s = Ui.dp(this, 12);
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        tp.topMargin = insetTop + s;
        topBar.setLayoutParams(tp);
        int below = insetTop + s + (topBar.getHeight() > 0 ? topBar.getHeight() : Ui.dp(this, 105));
        FrameLayout.LayoutParams sk = (FrameLayout.LayoutParams) sideKeys.getLayoutParams();
        if (sk.topMargin != below + Ui.dp(this, 12)) {
            sk.topMargin = below + Ui.dp(this, 12);
            sideKeys.setLayoutParams(sk);
        }
        // 3.9: the credit and the scale sit at the foot of the map, above the sheet (they crowded the top, "messy").
        map.setScaleOffset(Ui.dp(this, 16), Ui.dp(this, 52));
        if (home != null) home.setInsets(insetTop, insetBottom);
        map.redraw();
        sheet.setInsets(insetTop, insetBottom);
        if (tutorial != null) tutorial.setInsets(insetTop, insetBottom);
        if (settings != null) settings.setInsets(insetBottom);
        if (trip != null) trip.setInsets(insetTop, insetBottom);
        if (frame != null) frame.setInsets(insetTop, insetBottom);
        if (search != null) search.setInsets(insetTop, insetBottom);
        if (compose != null) compose.setInsets(insetTop, insetBottom);
    }

    // ---- map ----

    /** The map looks. The first is chosen by the phone's theme; the others are one tap away on the layers button. */
    private static final class MapStyle {
        final String name, credit;
        final ITileSource source;          // the flat map's (osmdroid)
        final String[] urls;               // the 3D map's (MapLibre), the same tiles
        final int maxZoom;
        MapStyle(String name, String credit, ITileSource source, int maxZoom, String... urls) {
            this.name = name;
            this.credit = credit;
            this.source = source;
            this.maxZoom = maxZoom;
            this.urls = urls;
        }
    }

    private MapStyle[] styles;

    private void buildStyles() {
        styles = new MapStyle[]{
                new MapStyle("Map", "© OpenStreetMap contributors", TileSourceFactory.MAPNIK, 19,
                        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
                new MapStyle("Terrain", "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
                        new XYTileSource("opentopomap", 0, 17, 256, ".png",
                                new String[]{"https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/",
                                        "https://c.tile.opentopomap.org/"}), 17,
                        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"),
                // 3.8: a third look, clear streets in soft colours; keyless, with the credit its host asks for.
                new MapStyle("Clear streets", "© OpenStreetMap contributors · Tiles HOT, OSM France",
                        new XYTileSource("osmhot", 0, 19, 256, ".png",
                                new String[]{"https://a.tile.openstreetmap.fr/hot/", "https://b.tile.openstreetmap.fr/hot/",
                                        "https://c.tile.openstreetmap.fr/hot/"}), 19,
                        "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", "https://b.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
                        "https://c.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"),
        };
    }

    /**
     * The map is graded so it stays quiet and the line and pin lead. Day: softened, almost grey, a little cool.
     * Night: no dark tile server needs a key, so invert, turn the hues back (water stays blue, roads stay warm),
     * soften and tint towards the app's navy.
     */
    /** The map's colours (3.8, owner: "I also want to change map style and colors"); Soft is the design's own. */
    static final String[] MAP_COLOURS = {"Soft", "Natural", "Paper", "Grey", "Dark"};

    int mapColours() { return Math.max(0, Math.min(MAP_COLOURS.length - 1, prefs.getInt("map_colours", 0))); }

    private ColorMatrixColorFilter mapFilter(boolean terrain) {
        ColorMatrix cm = new ColorMatrix();
        switch (mapColours()) {
            case 1:                                       // Natural: the tiles as drawn (inverted at night)
                return p.night ? softFilter(terrain, true) : null;
            case 2:                                       // Paper: sepia, like an old printed map
                if (p.night) cm.set(invertKeepHues());
                cm.postConcat(saturation(0.2f));
                cm.postConcat(new ColorMatrix(p.night ? new float[]{
                        0.80f, 0, 0, 0, 16, 0, 0.74f, 0, 0, 10, 0, 0, 0.62f, 0, 4, 0, 0, 0, 1, 0} : new float[]{
                        1.0f, 0, 0, 0, 14, 0, 0.96f, 0, 0, 8, 0, 0, 0.84f, 0, 0, 0, 0, 0, 1, 0}));
                return new ColorMatrixColorFilter(cm);
            case 3:                                       // Grey: no colour, so only the line has one
                if (p.night) cm.set(invertKeepHues());
                cm.postConcat(saturation(0f));
                cm.postConcat(new ColorMatrix(p.night ? new float[]{
                        0.78f, 0, 0, 0, 8, 0, 0.78f, 0, 0, 8, 0, 0, 0.80f, 0, 10, 0, 0, 0, 1, 0} : new float[]{
                        0.96f, 0, 0, 0, 8, 0, 0.96f, 0, 0, 8, 0, 0, 0.96f, 0, 8, 0, 0, 0, 1, 0}));
                return new ColorMatrixColorFilter(cm);
            case 4:                                       // Dark: the night map, by day too
                return softFilter(false, true);
            default:
                return softFilter(terrain, p.night);
        }
    }

    private static ColorMatrix saturation(float f) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(f);
        return m;
    }

    /** Inverted, with the hues turned back (water stays blue, roads stay warm). */
    private static ColorMatrix invertKeepHues() {
        ColorMatrix cm = new ColorMatrix(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0});
        cm.postConcat(new ColorMatrix(new float[]{
                -0.574f, 1.430f, 0.144f, 0, 0, 0.426f, 0.430f, 0.144f, 0, 0, 0.426f, 1.430f, -0.856f, 0, 0, 0, 0, 0, 1, 0}));
        return cm;
    }

    private ColorMatrixColorFilter softFilter(boolean terrain, boolean night) {
        ColorMatrix cm = new ColorMatrix();
        if (night) {
            if (terrain) {
                // Not inverted (that would turn the hill shading inside out), only dimmed. At 55% the dark lines and
                // names drowned in the grey (roadmap): a lighter dim keeps them readable, a little navy keeps it calm.
                ColorMatrix soft = new ColorMatrix();
                soft.setSaturation(0.8f);
                cm.postConcat(soft);
                cm.postConcat(new ColorMatrix(new float[]{0.72f, 0, 0, 0, 0, 0, 0.74f, 0, 0, 2, 0, 0, 0.80f, 0, 10, 0, 0, 0, 1, 0}));
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
            if (p.readoutLike()) {
                cm.postConcat(new ColorMatrix(new float[]{  // dim, neutral: the black case
                        0.80f, 0, 0, 0, 2,
                        0, 0.80f, 0, 0, 2,
                        0, 0, 0.82f, 0, 4,
                        0, 0, 0, 1, 0}));
            } else {
                cm.postConcat(new ColorMatrix(new float[]{  // dim and tint
                        0.80f, 0, 0, 0, 6,
                        0, 0.86f, 0, 0, 10,
                        0, 0, 1.0f, 0, 22,
                        0, 0, 0, 1, 0}));
            }
        } else if (p.design == Ui.POCKET) {
            // A printed city map: some colour kept (parks green, water blue), warmed towards the paper.
            cm.setSaturation(terrain ? 0.7f : 0.6f);
            cm.postConcat(new ColorMatrix(new float[]{
                    0.95f, 0, 0, 0, 10,
                    0, 0.94f, 0, 0, 9,
                    0, 0, 0.91f, 0, 6,
                    0, 0, 0, 1, 0}));
        } else if (p.readoutLike()) {
            cm.setSaturation(terrain ? 0.45f : 0.3f);       // grey, so the line and the keys lead
            cm.postConcat(new ColorMatrix(new float[]{
                    0.95f, 0, 0, 0, 9,
                    0, 0.95f, 0, 0, 9,
                    0, 0, 0.95f, 0, 8,
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
        map.setLook(new GeoMap.Look(st.source, st.urls, st.maxZoom, mapFilter(style == 1), p.bg, p.line));
        showCredit();
        map.redraw();
    }

    /** The credit the tiles' licences ask for; with the buildings standing up (3D), theirs too. */
    private void showCredit() {
        if (credit == null) return;
        credit.setText(styles[style].credit + (map.tilted() ? " · Buildings © OpenMapTiles, OpenFreeMap" : ""));
    }

    private MapLookSheet mapLook;

    private void showMapLook() {
        if (mapLook != null) return;
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) names[i] = styles[i].name;
        mapLook = new MapLookSheet(this, p, names, style, mapColours(), new MapLookSheet.Listener() {
            @Override public void onStyle(int s) { setStyle(s); }
            @Override public void onColours(int k) { setMapColours(k); }
            @Override public void onClose() { closeMapLook(); }
        });
        mapLook.setInsets(insetBottom);
        root.addView(mapLook, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeMapLook() {
        if (mapLook == null) return;
        root.removeView(mapLook);
        mapLook = null;
    }

    private void setMapColours(int k) {
        prefs.edit().putInt("map_colours", k).apply();
        applyStyle();
        if (settings != null) settings.setMapColours(k);
    }

    private void setStyle(int i) {
        style = i;
        prefs.edit().putInt("style", style).apply();
        applyStyle();
        if (settings != null) settings.setMapStyle(style);
    }

    private void buildMap() {
        style = Math.min(prefs.getInt("style", 0), 2);
        buildStyles();
        lineOverlay = new LineLayer();
        map = GeoMap.create(this, p, lineOverlay, new GeoMap.Listener() {
            @Override public void onTap(GeoPoint g) { tapMap(g); }
            @Override public void onHold(GeoPoint g) { onMapHold(g); }
            @Override public void onTilted(boolean tilted) {
                showCredit();
                if (tiltKey != null) tiltKey.setContentDescription(tilted ? "Flat map" : "3D map");
            }
            @Override public void onTurned(double bearing) {
                if (northKey != null) northKey.setIconRotation((float) -bearing);
            }
        });
        map.view().setBackgroundColor(p.bg);
        applyStyle();
        root.addView(map.view(), new FrameLayout.LayoutParams(-1, -1));
    }

    /** The pin drops onto the point with a small bounce (it settles at once if the phone has animations off). */
    private void dropPin() {
        if (dropAnim != null) dropAnim.cancel();
        dropAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        dropAnim.setDuration(520);
        dropAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        dropAnim.addUpdateListener(a -> {
            dropT = (float) a.getAnimatedValue();
            map.redraw();
        });
        dropAnim.start();
    }

    private boolean covered() {
        return tutorial != null || settings != null || trip != null || frame != null || search != null || compose != null;
    }

    private boolean tooClose(GeoPoint a, GeoPoint b) {
        return a != null && b != null
                && Astro.distance(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()) < 10;
    }

    /**
     * A tap places the point the step asks for (Where you stand, Your subject), and on that step only. On the
     * other steps a tap moves nothing: points are moved on purpose, by dragging them or pressing and holding.
     */
    void tapMap(GeoPoint pt) {
        if (covered()) return;
        Point t = map.toPixels(pt);
        int st = flow.current();
        if (st == Flow.YOU) {
            place(1, pt);
        } else if (st == Flow.SUBJECT) {
            place(0, pt);
        } else if (you != null && subject != null) {
            if (t != null && lineOverlay.onSunBadge(t)) {             // the sun at the end of the line: tap to send it to the other end
                setMode(1 - mode);
                return;
            }
            say("Press and hold the map to move a point, or drag one.");
        }
    }

    /** Press and hold: place the missing point, or, with a line drawn, ask which point moves here. */
    private void onMapHold(GeoPoint pt) {
        if (covered()) return;
        map.view().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (you != null && subject != null && flow.current() != Flow.YOU && flow.current() != Flow.SUBJECT) {
            held = pt;
            map.redraw();
            holdMenu(pt);
            return;
        }
        tapMap(pt);
    }

    /**
     * Press and hold: which point moves here, or (3.18) a building the map lacks is added here, or the one added here
     * is removed. A list, because the bar holds two keys.
     */
    private void holdMenu(GeoPoint pt) {
        UserBuildings.Mine mine = UserBuildings.at(pt.getLatitude(), pt.getLongitude(), 3);
        String[] items = {"Move the subject here", "Move You here", mine == null ? "Add a building here" : "Remove the building added here"};
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setItems(items, (d, which) -> {
                    held = null;
                    if (which == 0) place(0, pt);
                    else if (which == 1) place(1, pt);
                    else if (mine != null) {
                        UserBuildings.remove(this, mine);
                        buildingsChanged();
                        say("Building removed.", "Undo", () -> {
                            UserBuildings.add(this, mine);
                            buildingsChanged();
                        });
                    } else {
                        BuildingDialog.show(this, lastBuildingHeight, (h, w, dep, turn) -> {
                            lastBuildingHeight = h;
                            UserBuildings.Mine m = new UserBuildings.Mine(pt.getLatitude(), pt.getLongitude(), w, dep, turn, h);
                            UserBuildings.add(this, m);
                            buildingsChanged();
                            say("Building added: " + Fmt.height(h) + " tall. It counts in the times, the viewfinder and the shadows.",
                                    "Undo", () -> {
                                        UserBuildings.remove(this, m);
                                        buildingsChanged();
                                    });
                        });
                    }
                })
                .setOnCancelListener(d -> {
                    held = null;
                    map.redraw();
                })
                .show();
    }

    private double lastBuildingHeight = 12;

    /** The buildings changed (one added or removed): the times and the words are worked out again. */
    private void buildingsChanged() {
        map.redraw();
        if (you != null && subject != null) {
            generation++;
            compute();
        }
        buildSheet();
    }

    /** Puts the Subject (0) or You (1) at a point. */
    private void place(int which, GeoPoint pt) {
        held = null;
        if (tooClose(which == 0 ? you : subject, pt)) {
            say("That is on top of the other point. Pick a spot at least 10 m away.");
            return;
        }
        backupYou = backupSubject = null;
        if (which == 0) {
            subject = pt;
            subjectName = null;
            armed = 1;
        } else {
            you = pt;
            armed = 0;
            dropPin();
        }
        mapLineId = MAP_LINE; // a changed line is a new line, not the saved one any more
        setSavedShot(null);
        retimeShot = flow.current() == Flow.SHOT;   // the old moment is not lined up any more (Ali, 2026-11-15)
        saveState();
        refresh();
    }

    // ---- what sits over the map: the search bar, the map keys, the credit, the sheet ----

    private void buildChrome() {
        // The search bar, with the two ways out of the map at its end: Saved and Settings.
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackground(Ui.shape(this, p.panel, p.line, 16, false));
        // The step bar: Back, where you are in the tool, and the sun or the moon.
        stepRow = new LinearLayout(this);
        stepRow.setGravity(Gravity.CENTER_VERTICAL);
        stepRow.setMinimumHeight(Ui.dp(this, 52));
        Ui.IconButton back = new Ui.IconButton(this, p, Ui.Icon.BACK, "Back").bare();
        back.setOnClickListener(v -> onBackPressed());
        stepRow.addView(back);
        stepText = Ui.text(this, "", 15, 700, p.text);
        stepText.setMaxLines(1);
        stepText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stepRow.addView(stepText, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout bodyKey = new LinearLayout(this);
        bodyKey.setGravity(Gravity.CENTER_VERTICAL);
        bodyKey.setMinimumHeight(Ui.dp(this, 40));
        bodyKey.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        bodyKey.setBackground(Ui.shape(this, p.raised, p.line, 12, false));
        bodyIcon = new android.widget.ImageView(this);
        LinearLayout.LayoutParams bil = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        bil.setMarginEnd(Ui.dp(this, 6));
        bodyKey.addView(bodyIcon, bil);
        bodyText = Ui.text(this, "Sun", 14, 600, p.text);
        bodyKey.addView(bodyText);
        android.widget.ImageView chev = new android.widget.ImageView(this);
        chev.setImageDrawable(Ui.icon(this, Ui.Icon.CHEV_DOWN, p.muted));
        LinearLayout.LayoutParams cvl = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        cvl.setMarginStart(Ui.dp(this, 4));
        bodyKey.addView(chev, cvl);
        bodyKey.setClickable(true);
        Ui.pressable(bodyKey);
        bodyKey.setOnClickListener(v -> PickSheet.open(this, p, "Sun or moon", new String[]{"Sun", "Moon"}, null,
                flow.moon ? 1 : 0, i -> setMoon(i == 1)));
        LinearLayout.LayoutParams bkl = new LinearLayout.LayoutParams(-2, -2);
        bkl.setMarginEnd(Ui.dp(this, 8));
        stepRow.addView(bodyKey, bkl);
        topBar.addView(stepRow, new LinearLayout.LayoutParams(-1, -2));
        searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout find = new LinearLayout(this);
        find.setGravity(Gravity.CENTER_VERTICAL);
        find.setMinimumHeight(Ui.dp(this, 52));
        find.setPadding(Ui.dp(this, 14), 0, 0, 0);
        android.widget.ImageView glass = new android.widget.ImageView(this);
        glass.setImageDrawable(Ui.icon(this, Ui.Icon.SEARCH, p.muted));
        find.addView(glass, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView findText = Ui.text(this, "Search a place", 16, 500, p.muted);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, -2, 1f);
        fl.setMarginStart(Ui.dp(this, 10));
        find.addView(findText, fl);
        find.setClickable(true);
        find.setContentDescription("Search a place");
        find.setOnClickListener(v -> showSearch());
        searchRow.addView(find, new LinearLayout.LayoutParams(0, -2, 1f));
        View rule = new View(this);
        rule.setBackgroundColor(p.line);
        topBar.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));
        topBar.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
        // Never change the layout from inside a layout pass: wait for it to end.
        topBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (b - t != ob - ot) topBar.post(this::applyInsets); });
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        tp.setMargins(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(topBar, tp);

        // Map keys down the right edge: the map look, and where I am.
        sideKeys = new LinearLayout(this);
        sideKeys.setOrientation(LinearLayout.VERTICAL);
        layersKey = new Ui.IconButton(this, p, Ui.Icon.STACK, "Map look");
        layersKey.setOnClickListener(v -> showMapLook());   // 3.8: style and colours, seen on the map as they change
        sideKeys.addView(layersKey);
        Ui.IconButton locate = new Ui.IconButton(this, p, Ui.Icon.LOCATE, "Go to my location");
        locate.setOnClickListener(v -> askLocation());   // 3.23 (phase 3): the reason first, then Android asks
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = Ui.dp(this, 8);
        sideKeys.addView(locate, ll);
        if (map.tilts()) {
            // 3.15 (owner: "tilt to see the map in 3D, as an option"): the key tilts the map and stands the buildings
            // up; again, it lies flat with north up. Two fingers do the same.
            tiltKey = new Ui.IconButton(this, p, Ui.Icon.CUBE, "3D map");
            tiltKey.setOnClickListener(v -> map.setTilted(!map.tilted()));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = Ui.dp(this, 8);
            sideKeys.addView(tiltKey, tl);
            // 3.20 (owner: "add north button"): the needle shows where north is; a tap turns the map north up,
            // the tilt kept. Only where the map turns (the flat map is always north up).
            northKey = new Ui.IconButton(this, p, Ui.Icon.NORTH, "North up");
            northKey.setOnClickListener(v -> map.northUp());
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = Ui.dp(this, 8);
            sideKeys.addView(northKey, nl);
        }
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.setMarginEnd(Ui.dp(this, 12));
        root.addView(sideKeys, sp);

        // Map credit, required by the map data licences. Plain small text: it is not a key.
        credit = Ui.text(this, styles[style].credit, 10, 500, p.muted);
        credit.setSingleLine(true);
        credit.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        credit.setBackground(Ui.shape(this, Ui.withAlpha(p.bg, 190), 0, 6, false));
        FrameLayout.LayoutParams crp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        crp.setMarginStart(Ui.dp(this, 12));
        root.addView(credit, crp);

        sheet = new Sheet(this, p);
        sheet.setListener(new Sheet.Listener() {
            @Override public void onSheetMoved() { placeSnack(); }
            @Override public void onSheetSettled() { refitMap(); }
        });
        // The map is squeezed into the room above the sheet (owner, 3.8): what the sheet changes stays in sight.
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (b - t != ob - ot) squeezeMap(); });
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        snack = new LinearLayout(this);
        snack.setGravity(Gravity.CENTER_VERTICAL);
        snack.setBackground(Ui.shape(this, p.night ? 0xFFE8EEFC : 0xFF0F172A, 0, 14, false));
        snack.setVisibility(View.GONE);
        snack.setClickable(true);
        FrameLayout.LayoutParams sn = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sn.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        root.addView(snack, sn);
    }

    // ---- saying what happened, with a way back ----

    @Override public void say(String message) {
        say(message, null, null, null, null);
    }

    private void say(String message, String action, Runnable run) {
        say(message, action, run, null, null);
    }

    /** A bar above the sheet: what happened, and up to two keys. It goes by itself after a few seconds. */
    private void say(String message, String a1, Runnable r1, String a2, Runnable r2) {
        int ink = p.night ? 0xFF0F172A : 0xFFF3F5FA, key = p.accentOnInverse();   // the bar is in the other theme
        snack.removeAllViews();
        snack.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        TextView t = Ui.text(this, message, 14, 500, ink);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        snack.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        String[] names = {a1, a2};
        Runnable[] runs = {r1, r2};
        for (int i = 0; i < 2; i++) {
            if (names[i] == null) continue;
            final Runnable run = runs[i];
            TextView b = Ui.text(this, names[i], 14, 700, key);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(Ui.dp(this, 44));
            b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            b.setClickable(true);
            b.setOnClickListener(v -> {
                hideSnack.run();
                run.run();
            });
            snack.addView(b);
        }
        snack.setVisibility(View.VISIBLE);
        snack.bringToFront();
        for (View over : new View[]{trip, settings, search, compose, frame, tutorial}) if (over != null) over.bringToFront();
        if (frame != null || trip != null || search != null || compose != null) snack.bringToFront();   // said from those screens
        placeSnack();
        snack.removeCallbacks(hideSnack);
        snack.postDelayed(hideSnack, names[0] == null ? 4000 : 7000);
    }

    private void placeSnack() {
        if (snack == null || snack.getVisibility() != View.VISIBLE) return;
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) snack.getLayoutParams();
        boolean overMap = frame == null && trip == null && search == null && compose == null;
        int want = (overMap ? sheet.getHeight() : insetBottom) + Ui.dp(this, 12);
        if (l.bottomMargin != want) {
            l.bottomMargin = want;
            snack.setLayoutParams(l);
        }
    }

    // ---- the sheet: the first steps, where to stand, or the answer ----

    private void refresh() {
        generation++; // anything still being worked out is now out of date
        foundSun = foundMoon = null;
        nearSun = nearMoon = null;
        otherSideMs = 0;
        working = false;
        GeoPoint anchor = subject != null ? subject : you;
        if (anchor != null) {
            tz = Zones.of(this, anchor.getLatitude(), anchor.getLongitude());
            Zones.fetch(this, anchor.getLatitude(), anchor.getLongitude(), () -> { if (!isFinishing()) refresh(); });
        }
        if (you != null && subject != null) {
            compute();
            // The two grounds, for Your shot and the viewfinder; the words appear when they come.
            Heights.fetch(this, new double[]{you.getLatitude(), subject.getLatitude()},
                    new double[]{you.getLongitude(), subject.getLongitude()},
                    () -> {
                        if (isFinishing() || home != null || you == null || subject == null) return;
                        if (mode == 0 && hiddenBelow() > 0) {   // the subject's ground hides some times: find them again
                            generation++;
                            compute();
                        }
                        buildSheet();
                    });
            // 3.14 (owner: a sunset went behind a block): the buildings this way, when Settings counts them
            Buildings.fetch(this, you.getLatitude(), you.getLongitude(), lineBearing(), () -> {
                if (isFinishing() || home != null || you == null || subject == null) return;
                if (buildingsUp() > -1) {
                    generation++;
                    compute();
                }
                buildSheet();
            });
            // 3.12 (owner): the land this way, out to 25 km, so a sun "on the horizon" is not one behind a hill
            double[][] land = landPoints();
            Heights.fetch(this, land[0], land[1], () -> {
                if (isFinishing() || home != null || you == null || subject == null) return;
                if (landHorizon() > -1) {          // the land stands up this way: find the times again
                    generation++;
                    compute();
                }
                buildSheet();
            });
        }
        buildSheet();
    }

    private void buildSheet() {
        sheet.head.removeAllViews();
        sheet.body.removeAllViews();
        body = null;
        sunRead = moonRead = null;
        renderStep();
        sheet.headChanged();
        map.redraw();
        updateShadows();
    }

    private String shadowKey = "";

    /**
     * 3.17: the shadows on the 3D map. 3.17.1: they follow the time: on the Day chart the body is where the cursor is
     * (every drag moves them); otherwise it is the moment the map shows (the soonest of the body chosen, on the line).
     * From the buildings read here and the subject; only while tilted.
     */
    private void updateShadows() {
        try {
            updateShadowsNow();
        } catch (Throwable t) {             // shadows are a nicety: they must never end the app
            CrashLog.caught(this, "updateShadows", t);
            shadowKey = "";
        }
    }

    private void updateShadowsNow() {
        if (map == null || !map.tilts() || home != null || you == null || subject == null) return;
        double az = Double.NaN, alt = Double.NaN;
        long when = 0;
        if (tab == 1 && sunHits != null) {
            double[] pos = flow.moon ? Astro.moon(cursorMs, you.getLatitude(), you.getLongitude())
                    : Astro.sun(cursorMs, you.getLatitude(), you.getLongitude());
            az = pos[0];
            alt = pos[1];
            when = cursorMs / 30000;        // half a minute: the sun moves about 0.1 degree
        } else {
            Astro.Hit h = flow.moon ? (foundMoon == null ? null : foundMoon.next) : (foundSun == null ? null : foundSun.next);
            if (h != null) {
                az = lineBearing();
                alt = h.alt;
                when = h.timeMs;
            }
        }
        Buildings.Area a = Double.isNaN(alt) || !Buildings.on(this) ? null : Buildings.get(this, you.getLatitude(), you.getLongitude(), lineBearing());
        double alpha = Double.isNaN(alt) || !Shadows.on(this) ? 0 : 0.45 * Shadows.strength(alt, flow.moon);
        String key = alpha <= 0 ? "" : when + "|" + Math.round(az * 10) + "|" + System.identityHashCode(a) + "|" + subject.getLatitude()
                + "|" + subject.getLongitude() + "|" + you.getLatitude() + "|" + you.getLongitude() + "|" + heightNow()
                + "|" + subjectKind + "|" + mode + "|" + Math.round(alpha * 100);
        if (key.equals(shadowKey)) return;
        shadowKey = key;
        if (key.isEmpty()) {
            map.setShadows(null, 0);
            return;
        }
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        int n = 0;
        if (a != null) {
            double pe = a.east(subject.getLongitude()), pn = a.north(subject.getLatitude());
            for (Buildings.B b : a.list) {
                if (Buildings.inside(b, pe, pn) || Buildings.distanceTo(b, pe, pn) < Buildings.SUBJECT_WITHIN) continue;   // the subject's own
                double[] v = Shadows.vector(az, alt, b.height);
                if (v == null) continue;
                double[][] hull = Shadows.hull(b.e, b.n, v[0], v[1]);
                n = shadowPolygon(sb, n, a.lat, a.lon, hull);
            }
        }
        double[] v = Shadows.vector(az, alt, heightNow());
        if (v != null) {
            double w = heightNow() * FrameMath.KIND_WIDTHS[subjectKind];
            double viewAz = Astro.bearing(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
            double[][] q = Shadows.flat(0, 0, viewAz, w, v[0], v[1]);
            n = shadowPolygon(sb, n, subject.getLatitude(), subject.getLongitude(), q);
        }
        sb.append("]}");
        map.setShadows(sb.toString(), alpha);
    }

    /** One polygon feature, its corners in metres east and north of (lat0, lon0); returns the count so far. */
    private static int shadowPolygon(StringBuilder sb, int n, double lat0, double lon0, double[][] ring) {
        double mLat = Math.toRadians(1) * FrameMath.EARTH_R, mLon = mLat * Math.cos(Math.toRadians(lat0));
        if (n > 0) sb.append(',');
        sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
        int m = ring[0].length;
        for (int i = 0; i <= m; i++) {
            int j = i % m;
            if (i > 0) sb.append(',');
            sb.append('[').append(String.format(java.util.Locale.US, "%.7f,%.7f", lon0 + ring[0][j] / mLon, lat0 + ring[1][j] / mLat)).append(']');
        }
        sb.append("]]}}");
        return n + 1;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(this, dp);
        return l;
    }

    private LinearLayout.LayoutParams wrapTop(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.topMargin = Ui.dp(this, dp);
        return l;
    }

    private FrameLayout badge(boolean moon, boolean horizon, double lit, long ms) {
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.shape(this, p.raised, p.line, 24, false));
        Ui.GlyphView.Kind kind = moon ? (horizon ? Ui.GlyphView.Kind.MOON_HORIZON : Ui.GlyphView.Kind.MOON)
                : (horizon ? Ui.GlyphView.Kind.SUN_HORIZON : Ui.GlyphView.Kind.SUN);
        Ui.GlyphView glyph = new Ui.GlyphView(this, kind, moon ? p.violet : p.amber, 26);
        if (moon) glyph.setMoon((float) lit, Astro.moonWaxing(ms));
        badge.addView(glyph, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return badge;
    }

    /** The head of the sheet: a badge, the answer in large words, one line under it, and the keys at the end. */
    private LinearLayout headRow(View badge, String headline, String sub, View... keys) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (badge != null) row.addView(badge);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView h = Ui.text(this, headline, 20, 700, p.text);
        h.setMaxLines(1);
        h.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(h);
        TextView s = Ui.text(this, sub, 13.5f, 400, p.muted);
        s.setMaxLines(2);
        col.addView(s, top(2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        if (badge != null) cl.setMarginStart(Ui.dp(this, 12));
        row.addView(col, cl);
        for (View key : keys) {
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
            kl.setMarginStart(Ui.dp(this, 8));
            row.addView(key, kl);
        }
        return row;
    }

    /** The key with three dots: what is done to the line as a whole. The quiet place for Clear. */
    private Ui.IconButton moreKey() {
        Ui.IconButton more = new Ui.IconButton(this, p, Ui.Icon.MORE, "More").small();
        more.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            if (you != null && subject != null) {
                String name = flow.current() == Flow.SHOT ? (savedShot() == null ? null : savedShot().name) : lineName();
                m.getMenu().add(0, 1, 0, name == null ? "Save" : "Saved as " + name).setEnabled(name == null);
            }
            if (you != null && subject != null && flow.current() != Flow.SHOT) m.getMenu().add(0, 3, 1, "Swap You and the subject");
            m.getMenu().add(0, 2, 2, "Clear");
            m.getMenu().add(0, 4, 3, "How " + Flow.toolName(flow.tool) + " works");   // 3.20: its guide again
            m.setOnMenuItemClickListener(i -> {
                if (i.getItemId() == 1) { if (flow.current() == Flow.SHOT) saveShot(true); else saveLine(); }
                else if (i.getItemId() == 3) swapPoints();
                else if (i.getItemId() == 4) showToolGuide();
                else clearLine();
                return true;
            });
            m.show();
        });
        return more;
    }

    /** "18 m lower than the subject's ground", or "" when the grounds are not known or nearly level. */
    private String groundWords(double youLat, double youLon) {
        double d = Heights.difference(this, youLat, youLon, subject.getLatitude(), subject.getLongitude());
        if (Double.isNaN(d) || Math.abs(d) < 3) return "";
        return Fmt.height(Math.abs(d)) + (d > 0 ? " lower" : " higher") + " than the subject's ground";
    }

    /** The subject's height: typed, or the usual height of its kind. */
    private double heightNow() {
        return subjectHeight > 0 ? subjectHeight : FrameMath.KIND_HEIGHTS[subjectKind];
    }

    /** The map ends where the sheet begins (a little under its rounded top), so nothing it shows is hidden. */
    private void squeezeMap() {
        if (map == null || sheet == null) return;
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) map.view().getLayoutParams();
        int h = sheet.getLayoutParams() != null && sheet.getLayoutParams().height > 0 ? sheet.getLayoutParams().height : sheet.getHeight();
        int want = sheet.getVisibility() == View.VISIBLE ? Math.max(0, h - Ui.dp(this, 22)) : 0;
        if (l == null || l.bottomMargin == want) return;
        l.bottomMargin = want;
        map.view().setLayoutParams(l);
        // The credit follows the foot of the map; the map keys step aside when the map is too short for them (3.9).
        if (credit != null) {
            FrameLayout.LayoutParams cr = (FrameLayout.LayoutParams) credit.getLayoutParams();
            cr.bottomMargin = want + Ui.dp(this, 28);
            credit.setLayoutParams(cr);
        }
        if (sideKeys != null) {
            int room = root.getHeight() - want - ((FrameLayout.LayoutParams) sideKeys.getLayoutParams()).topMargin;
            sideKeys.setVisibility(room > Ui.dp(this, 150) || root.getHeight() == 0 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** With the sheet at rest, the line is fitted again into the map's room (the steps after the points). */
    private void refitMap() {
        if (home != null || you == null || subject == null) return;
        int st = flow.current();
        if (st == Flow.WHEN && tab == 1) map.view().post(() -> map.animateTo(you, Double.NaN));   // the day's path is round you
        else if (st == Flow.WHEN || st == Flow.SHOT) map.view().post(() -> fit(you, subject));
    }

    private void fit(GeoPoint a, GeoPoint b) {
        if (Math.abs(a.getLongitude() - b.getLongitude()) > 180) {
            // 3.23 (sweep S3): across the date line the box went round the world the other way; centre and zoom instead
            double bLon = b.getLongitude() + (b.getLongitude() < a.getLongitude() ? 360 : -360);
            double mid = (a.getLongitude() + bLon) / 2, midLat = (a.getLatitude() + b.getLatitude()) / 2;
            if (mid > 180) mid -= 360;
            if (mid < -180) mid += 360;
            double spanDeg = Math.max(Math.max(Math.abs(a.getLatitude() - b.getLatitude()),
                    Math.abs(a.getLongitude() - bLon) * Math.cos(Math.toRadians(midLat))), 0.0008) * 1.8;
            View v = map.view();
            double wdp = v != null && v.getWidth() > 0 ? v.getWidth() / getResources().getDisplayMetrics().density : 360;
            double z = Math.max(2, Math.min(18, Math.log(360 * wdp / (256 * spanDeg)) / Math.log(2)));
            map.setCenter(new GeoPoint(midLat, mid), z);
            return;
        }
        double minLat = Math.min(a.getLatitude(), b.getLatitude()), maxLat = Math.max(a.getLatitude(), b.getLatitude());
        double minLon = Math.min(a.getLongitude(), b.getLongitude()), maxLon = Math.max(a.getLongitude(), b.getLongitude());
        double span = Math.max(Math.max(maxLat - minLat, (maxLon - minLon) * Math.cos(Math.toRadians(minLat))), 0.0008);
        // The map ends above the sheet (3.8), so only a little room to the south; more to the north clears the bar.
        map.fit(maxLat + span * 0.55, maxLon + span * 0.25, minLat - span * 0.3, minLon - span * 0.25, Ui.dp(this, 40));
    }

    /** From a drawn line: keep the subject and build the shot from it (Where to stand lives there since 3.6). */
    private void buildFromLine() {
        flow.start(Flow.BUILD);
        flow.go(Flow.COMPOSE);
        enterStep();
    }

    /**
     * 3.18 (owner's sketch: an arrow between the two pins, "Swap"): You stands where the subject was and the subject
     * where You stood. The subject's kind and height stay (they belong to the thing, not the spot); its name goes (it
     * named the old spot). Undo swaps back.
     */
    private void swapPoints() {
        if (you == null || subject == null) return;
        GeoPoint t = you;
        you = subject;
        subject = t;
        final String name = subjectName;
        subjectName = null;
        mapLineId = MAP_LINE;
        saveState();
        dropPin();
        enterStep();
        say("Swapped: you stand where the subject was.", "Undo", () -> {
            GeoPoint u = you;
            you = subject;
            subject = u;
            subjectName = name;
            mapLineId = MAP_LINE;
            saveState();
            dropPin();
            enterStep();
        });
    }

    private void clearLine() {
        backupYou = you;
        backupSubject = subject;
        final String name = subjectName, id = mapLineId;
        final int step = flow.step;
        you = subject = null;
        subjectName = null;
        armed = 0;
        mapLineId = MAP_LINE;
        saveState();
        flow.step = 0;
        enterStep();
        say("Cleared.", "Undo", () -> {
            you = backupYou;
            subject = backupSubject;
            subjectName = name;
            mapLineId = id;
            backupYou = backupSubject = null;
            if (you != null) dropPin();
            saveState();
            flow.step = step;
            enterStep();
        });
    }

    private void setMode(int m) {
        if (m == mode) return;
        mode = m;
        mapLineId = MAP_LINE; // the other end is a different line for the saved list
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
        final long from = searchFrom > 0 ? searchFrom : System.currentTimeMillis();
        // Behind the subject, a subject on higher ground hides a low sun: those times are left out (3.6)
        final double hidden = hiddenAll();          // 3.12: the land as well as the subject's ground
        final boolean wantMoon = flow.moon;
        working = true;
        new Thread(() -> {
            Astro.Found s = Astro.search(false, lat, lon, b, from, hidden);
            Astro.Near ns = s.next == null ? Astro.nearest(false, lat, lon, b, from, 366, hidden) : null;
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundSun = s;
                nearSun = ns;
                if (you != null && subject != null) buildSheet();
            });
            Astro.Found m = Astro.search(true, lat, lon, b, from, hidden);
            Astro.Near nm = m.next == null ? Astro.nearest(true, lat, lon, b, from, 60, hidden) : null;
            // The other side, only when the chosen body never lines up on this one (the land that way is not known yet)
            Astro.Found mine = wantMoon ? m : s;
            Astro.Found other = mine.next == null ? Astro.search(wantMoon, lat, lon, Astro.norm360(b + 180), from) : null;
            final long otherAt = other != null && other.next != null ? other.next.timeMs : 0;
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundMoon = m;
                nearMoon = nm;
                otherSideMs = otherAt;
                working = false;
                if (retimeShot) {          // a point moved on Your shot or in the viewfinder: the new soonest moment
                    retimeShot = false;
                    long n = next(flow.moon ? foundMoon : foundSun);
                    String body = flow.moon ? "moon" : "sun";
                    if (n > 0) {
                        shotMs = n;
                        prefs.edit().putLong("shot_ms", n).apply();
                        say("You moved: the " + body + " lines up here " + Fmt.when(this, n, tz) + ".");
                        if (frame != null) frame.jumpTo(n);   // the scene shows the new moment, not a stale one
                    } else {
                        say("You moved: the " + body + " never lines up from here. Back to pick again.");
                    }
                }
                if (you != null && subject != null) buildSheet();
                if (frame != null) frame.setNext(next(foundSun), next(foundMoon));
            });
        }).start();
    }

    /** How high the ground under the subject stands from your eye, degrees (below the horizon: nothing hidden). */
    private double hiddenBelow() {
        double yLat = you.getLatitude(), yLon = you.getLongitude(), sLat = subject.getLatitude(), sLon = subject.getLongitude();
        if (!Heights.known(this, yLat, yLon) || !Heights.known(this, sLat, sLon)) return -90;
        return FrameMath.baseAngle(Heights.rise(this, yLat, yLon, sLat, sLon), Astro.distance(yLat, yLon, sLat, sLon));
    }

    /** The points the land's horizon is read at: along the way the body must be, from you, out to 25 km (3.12). */
    private double[][] landPoints() {
        double b = lineBearing();
        double[] d = FrameMath.horizonDistances();
        double[] la = new double[d.length], lo = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            double[] q = Astro.destination(you.getLatitude(), you.getLongitude(), b, d[i]);
            la[i] = q[0];
            lo[i] = q[1];
        }
        return new double[][]{la, lo};
    }

    /**
     * How high the land stands this way, degrees from your eye (hills, rising ground, the earth's curve): -90 until
     * most of it is known, or without a network. Ground only: buildings are not in the terrain model.
     */
    private double landHorizon() {
        if (you == null || subject == null || !Heights.known(this, you.getLatitude(), you.getLongitude())) return -90;
        double[][] pts = landPoints();
        double[] d = FrameMath.horizonDistances(), h = new double[d.length];
        int known = 0;
        for (int i = 0; i < d.length; i++) {
            h[i] = Heights.of(this, pts[0][i], pts[1][i]);
            if (!Double.isNaN(h[i])) known++;
        }
        if (known < d.length / 2) return -90;
        return FrameMath.landHorizon(Heights.of(this, you.getLatitude(), you.getLongitude()), d, h);
    }

    /**
     * 3.18 (owner: the subject stood behind a building and the app said nothing): a building between You and the
     * subject, in words, or "". It only says so: the times are about the sun, which may clear the roof.
     */
    private String coverWords() {
        if (you == null || subject == null || !Buildings.on(this)) return "";
        Buildings.Area a = Buildings.get(this, you.getLatitude(), you.getLongitude(), lineBearing());
        return Buildings.coverWords(Buildings.cover(a, you.getLatitude(), you.getLongitude(),
                subject.getLatitude(), subject.getLongitude(), heightNow()));
    }

    /** What hides a low body: the subject's ground (behind the subject), the land or a building, whichever stands higher. */
    private double hiddenAll() {
        return Math.max(Math.max(mode == 0 ? hiddenBelow() : -90, landHorizon()), buildingsUp());
    }

    /** The highest building this way (3.14), or null: none in the way, not fetched, or left out in Settings. */
    private Buildings.Hit building() {
        if (you == null || subject == null) return null;
        return Buildings.hides(this, you.getLatitude(), you.getLongitude(), lineBearing(),
                subject.getLatitude(), subject.getLongitude());
    }

    /** How high the buildings this way stand from your eye, degrees; -90 when none hides anything. */
    private double buildingsUp() {
        Buildings.Hit b = building();
        return b == null ? -90 : b.alt;
    }

    /**
     * What the buildings are doing this way, when they are counted and do not hide the most (3.14.1: the owner saw no
     * buildings and no reason). "" with buildings left out in Settings.
     */
    private String buildingsWords(String bn) {
        if (you == null || subject == null) return "";
        int state = Buildings.state(this, you.getLatitude(), you.getLongitude(), lineBearing());
        if (state == Buildings.OFF) return "";
        if (state == Buildings.FAILED_) return "Buildings could not be fetched (no network?): the times are not checked against them.";
        if (state != Buildings.READY) return "Getting the buildings this way…";
        Buildings.Area a = Buildings.get(this, you.getLatitude(), you.getLongitude(), lineBearing());
        Buildings.Hit b = building();
        String guess = a.guessed() > a.list.size() / 2 ? " Most heights here are not mapped: about "
                + Fmt.height(Buildings.GUESS) + " is guessed." : "";
        if (b == null || b.alt <= 0) return "No building this way stands above the horizon." + guess;
        return "The highest building this way stands " + String.format(Locale.US, "%.1f°", b.alt) + " up, "
                + Fmt.distance(b.dist) + " off." + guess;
    }

    /** What hides the body below how many degrees, in words, or "" when nothing does (3.14: buildings too). */
    private String hidesWords(String bn) {
        double landUp = landHorizon(), groundUp = mode == 0 ? hiddenBelow() : -90, houses = buildingsUp();
        double hides = Math.max(Math.max(landUp, groundUp), houses);
        if (hides <= 0.05) return "";
        String deg = String.format(Locale.US, "%.1f°", hides);
        if (houses >= landUp && houses >= groundUp) {
            Buildings.Hit b = building();
            return "A building " + Fmt.distance(b.dist) + " this way, " + (b.guessed ? "about " : "") + Fmt.height(b.height)
                    + " tall, hides the " + bn + " below " + deg + (b.guessed ? " (its height is a guess)" : "")
                    + ": those times are left out.";
        }
        return (landUp >= groundUp ? "Hills this way hide the " + bn + " below " : "The subject's ground hides the " + bn
                + " below ") + deg + ": those times are left out.";
    }

    /** Does this moment have the body where the photographer wants it on the line (3.12, Where the sun sits)? */
    private boolean sitsRight(Astro.Hit h) {
        if (lineSits == 0 || mode != 0) return true;
        double[] w = sitsWindow(lineSits);
        return h.alt >= w[0] && h.alt <= w[1];
    }

    /** How far either side of the picked angle a time still counts (3.13): the sun moves about that much a day. */
    static final double ANGLE_SLACK = 0.75;

    /** The heights, low and high (degrees up), a choice of Where the sun sits stands for, from here. */
    private double[] sitsWindow(int which) {
        double d = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        double rise = Heights.rise(this, you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        double foot = FrameMath.baseAngle(rise, d), top = FrameMath.topAngle(heightNow(), d, rise);
        switch (which) {
            case 1: {                                   // on the horizon: just clear of what hides it
                double floor = Math.max(0, hiddenAll());
                return new double[]{-90, floor + 2.0};
            }
            case 2: {                                   // behind the middle of the subject
                double mid = (foot + top) / 2, half = Math.max(0.3, (top - foot) * 0.25);
                return new double[]{mid - half, mid + half};
            }
            case 3:                                     // just on top: its lower edge on the subject's top
                return new double[]{top, top + 1.0};
            case 4:                                     // the angle picked on the slider (3.13, owner)
                double mid = (lineLo + lineHi) / 2, half = Math.max((lineHi - lineLo) / 2, ANGLE_SLACK);
                return new double[]{mid - half, mid + half};
            default:
                return new double[]{-90, 90};
        }
    }

    /** The middle of a choice's heights, where the slider stands for it. */
    private double sitsCentre(int which) {
        double[] w = sitsWindow(which);
        if (which == 1) return Math.max(0, hiddenAll()) + 1.0;
        if (which == 0) return sitsWindow(3)[0];        // Any: the slider waits at the subject's top
        return (w[0] + w[1]) / 2;
    }

    private java.util.List<Astro.Hit> shown(Astro.Found f) {
        java.util.List<Astro.Hit> out = new java.util.ArrayList<>();
        if (f == null) return out;
        for (Astro.Hit h : f.all) if (sitsRight(h)) out.add(h);
        return out;
    }

    static final String[] LINE_SITS = {"Any", "Horizon", "Middle", "On top"};
    private static final String[] LINE_SITS_WORDS = {"anywhere", "on the horizon", "behind the middle", "just on top",
            "at that height"};

    private static long next(Astro.Found f) {
        return f == null || f.next == null ? -1 : f.next.timeMs;
    }

    private String where() {
        return mode == 0 ? "behind " + (subjectName == null ? "subject" : subjectName) : "behind you";
    }

    /** Is the body on the line at this moment (as the readout counts it: within 0.6°, not below the horizon)? */
    private boolean onLineAt(long ms, boolean moon) {
        if (you == null || subject == null) return false;
        double[] pos = moon ? Astro.moon(ms, you.getLatitude(), you.getLongitude()) : Astro.sun(ms, you.getLatitude(), you.getLongitude());
        return pos[1] >= -1 && Math.abs(FrameMath.diff(pos[0], lineBearing())) < 0.6;
    }

    /** Your shot's words for where the body is: on the line, or (a time saved off it, 3.23 B-03) how far off. */
    private String shotWhere(long ms, boolean moon) {
        if (flow.tool == Flow.BUILD || onLineAt(ms, moon)) return where();
        double[] pos = moon ? Astro.moon(ms, you.getLatitude(), you.getLongitude()) : Astro.sun(ms, you.getLatitude(), you.getLongitude());
        return Math.round(Math.abs(FrameMath.diff(pos[0], lineBearing()))) + "° off the line";
    }

    private static String up(Astro.Hit h) {
        return h.alt < 0.5 ? "on the horizon" : Math.round(h.alt) + "° up";
    }

    private String zoneNote() {
        boolean same = tz.getOffset(System.currentTimeMillis()) == TimeZone.getDefault().getOffset(System.currentTimeMillis());
        return same ? "Times are your phone's time." : "Times are " + Fmt.zoneName(tz) + ", not your phone's.";
    }

    /** The line is drawn: the soonest moment in large words, then Times or Day. */
    private void renderLine() {
        sheet.setPullable(true);
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        double b = lineBearing();
        double m = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        String lineWords = String.format(Locale.US, "%d° %s · %s", Math.round(b) % 360, Astro.compass(b), Fmt.distance(m));

        // The soonest moment of the chosen body leads: sun and moon are never mixed in one list.
        boolean firstMoon = flow.moon;
        Astro.Found found = firstMoon ? foundMoon : foundSun;
        java.util.List<Astro.Hit> fits = shown(found);
        // 3.12: the soonest where the body sits as chosen (Any: the soonest of all)
        Astro.Hit first = found == null || found.next == null ? null : fits.isEmpty() ? null : fits.get(0);
        boolean noneThatWay = found != null && found.next != null && fits.isEmpty();
        long now = System.currentTimeMillis();
        String headline, sub;
        View badge;
        String bodyName = firstMoon ? "moon" : "sun";
        if (first != null) {
            headline = Fmt.whenShort(this, first.timeMs, tz);
            String until = Fmt.until(first.timeMs, now), wd = Fmt.weekday(this, first.timeMs, tz);
            sub = (wd.isEmpty() ? "" : wd + " · ") + (firstMoon ? "Moon " : "Sun ") + where() + " · " + up(first)
                    + (until.isEmpty() ? "" : " · " + until);
            badge = badge(firstMoon, Astro.isLow(first, firstMoon), first.lit, first.timeMs);
        } else if (found == null) {
            headline = "Working it out…";
            sub = lineWords;
            badge = badge(firstMoon, false, 1, 0);
        } else if (noneThatWay) {
            headline = "Not " + LINE_SITS_WORDS[lineSits] + " this year";
            sub = "From here the " + bodyName + " never sits " + LINE_SITS_WORDS[lineSits] + " in the coming year. Choose Any "
                    + "below, or move.";
            badge = badge(firstMoon, false, 1, 0);
        } else if (found.hidden) {
            // 3.23 (phase 3, B-05): it does line up here, but only behind a building, the land or the subject's ground.
            // Name that, and do not send the photographer to the other side, which may be just as blocked.
            headline = "Hidden from here";
            String why = hidesWords(bodyName).replace(": those times are left out.", ".");
            sub = (why.isEmpty() ? "The " + bodyName + " lines up here only while something hides it." : why)
                    + " Move along the street, or away from what hides it.";
            badge = badge(firstMoon, false, 1, 0);
        } else {
            headline = "Never lines up here";
            sub = otherSideMs > 0
                    ? "The " + bodyName + " is never " + (mode == 0 ? "behind the subject" : "behind you") + " from here. It is "
                    + (mode == 0 ? "behind you" : "behind the subject") + " " + Fmt.when(this, otherSideMs, tz) + "."
                    : "The " + bodyName + " never crosses this line. The closest it comes is in the list below.";
            badge = badge(firstMoon, false, 1, 0);
        }
        boolean chart = tab == 1 && first != null;
        // 3.19.4 (owner: the Day chart leaves the map too small, the card is too tall): the map keeps more of the screen
        sheet.setHalfShare(tab == 1 ? 0.55f : 0.4f);
        // 3.19.4 (owner): the day's path of the body round You shows on the map as soon as there is a soonest moment,
        // on All times too (it showed only on the Day chart), for the day of that moment.
        pathMs = 0;
        if (tab == 0 && first != null && you != null) {
            pathMs = first.timeMs;
            buildDay(pathMs);
        }
        if (map != null) map.redraw();
        final boolean compactCard = first != null && p.design != Ui.CLASSIC && !p.readoutLike();
        if (chart) {
            // 3.9 (owner: "still no map view of the timeline"): with the Day chart the head is one line, so the chart
            // fits under a map that stays in sight; the soonest is still one tap away.
            sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 2));   // 3.19.5: the chart gets the room
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(new Ui.GlyphView(this, firstMoon ? Ui.GlyphView.Kind.MOON : Ui.GlyphView.Kind.SUN, firstMoon ? p.violet : p.amber, 20));
            TextView t = Ui.text(this, "Soonest " + Fmt.whenShort(this, first.timeMs, tz), 15, 700, p.text);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.setMarginStart(Ui.dp(this, 8));
            line.addView(t, tl);
            TextView see = Ui.smallButton(this, p, "See it");
            see.setContentDescription("See the soonest in the viewfinder");
            final long at = first.timeMs;
            see.setOnClickListener(v -> openMoment(at));
            line.addView(see);
            sheet.head.addView(line);
        } else if (p.design == Ui.CLASSIC || first == null) {
            sheet.head.addView(headRow(badge, headline, sub, moreKey()));
        } else {
            sheet.head.addView(answerCard(first, firstMoon, b));
        }
        Astro.Near near = firstMoon ? nearMoon : nearSun;
        if (chart) {
            // the head is the one line above
        } else if (noneThatWay) {
            // nothing to see: the list below offers the other choices
        } else if (first == null && found != null && near != null) {
            // "Never" comes with the way on as the main key, not a greyed "Use the soonest" (audit 3.2.2)
            TextView fix = Ui.button(this, p, "Line it up", true);
            fix.setContentDescription("Line it up: move You so the " + bodyName + " is on the line at its closest");
            fix.setOnClickListener(v -> lineUp(near, bodyName));
            sheet.head.addView(fix, top(12));
            sheet.head.addView(note(Ui.Icon.MOVE, "Moves you round the subject, the same distance away, so the " + bodyName
                    + " lines up with it."), top(6));
            if (otherSideMs > 0 && !found.hidden) {   // 3.22 (Ali): the other side has times: say so with a key, not only in the tabs below
                final int otherMode = 1 - mode;
                TextView swap = Ui.button(this, p, otherMode == 1 ? "Show Behind you" : "Show Behind subject", false);
                swap.setContentDescription("Show the times with the " + bodyName + (otherMode == 1 ? " behind you" : " behind the subject"));
                swap.setOnClickListener(v -> setMode(otherMode));
                sheet.head.addView(swap, top(10));
            }
        } else if (compactCard) {
            // 3.19.4: the card has its own small "See it" key beside the label (the big key made the card too tall)
        } else if (first != null) {   // no moment: no greyed key with no reason (3.23, B-05)
            // 3.8: the moment is seen, not only read: the main key opens it in the viewfinder (owner).
            TextView use = Ui.button(this, p, "See it in the viewfinder", true);
            use.setContentDescription("See the soonest in the viewfinder");
            use.setEnabled(first != null);
            use.setAlpha(first != null ? 1f : 0.5f);
            final long at = first != null ? first.timeMs : now;
            use.setOnClickListener(v -> openMoment(at));
            sheet.head.addView(use, top(12));
        }

        LinearLayout sb = sheet.body;
        // Two clear tabs, each with its icon (owner, 3.8): the other times as a list, or the day as a chart.
        sb.addView(Ui.bigTabs(this, p, new String[]{"All times", "Day chart"}, new Ui.Icon[]{Ui.Icon.LIST, Ui.Icon.CHART},
                tab, t -> {
                    tab = t;
                    buildSheet();
                    if (t == 1) {                       // the map shows the path round you, above the sheet
                        if (sheet.stop() != Sheet.HALF) sheet.setStop(Sheet.HALF, true);
                        else refitMap();
                    }
                }), tab == 1 ? top(4) : top(0));
        sheet.setMoreWords(tab == 0 ? "Pull up for the other times" : "Pull up for the day chart");
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sb.addView(body, top(0));
        if (tab == 0) renderTimes(lineWords);
        else renderDay();
    }

    /**
     * The soonest moment as a card, as in the owner's rendering (3.8): the time large on the left, the bearing on
     * the right past a hairline, where the body stands under both. Readout sets it on its graphite panel.
     */
    private View answerCard(Astro.Hit first, boolean moon, double bearing) {
        long now = System.currentTimeMillis();
        String wd = Fmt.weekday(this, first.timeMs, tz), until = Fmt.until(first.timeMs, now);
        String dir = Math.round(bearing) % 360 + "° " + Astro.compass(bearing);
        String light = moon ? Math.round(first.lit * 100) + "% lit"
                : Light.name(Light.of(Astro.sun(first.timeMs, you.getLatitude(), you.getLongitude())[1])).toLowerCase(Locale.ROOT);
        if (p.readoutLike()) {
            LinearLayout r = Ui.readout(this, p);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(Ui.readoutLabel(this, p, "Soonest" + (wd.isEmpty() ? "" : " · " + wd) + " · " + (moon ? "Moon " : "Sun ")
                    + where()), new LinearLayout.LayoutParams(0, -2, 1f));
            Ui.IconButton more = moreKey();
            more.tint(p.readoutText);
            top.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
            r.addView(top);
            r.addView(Ui.readoutBig(this, p, Fmt.whenShort(this, first.timeMs, tz), 30));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.topMargin = Ui.dp(this, 12);
            r.addView(Ui.cells(this, p, new String[][]{{dir, "bearing"}, {up(first), light},
                    {until.isEmpty() ? "now" : until.replace("in ", ""), "from now"}}), cl);
            r.setContentDescription("Soonest " + Fmt.when(this, first.timeMs, tz));
            return r;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // The label and the three dots on top, so the time has the row to itself ("PM" was cut, 3.8).
        LinearLayout label = new LinearLayout(this);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.addView(new Ui.GlyphView(this, moon ? Ui.GlyphView.Kind.MOON : Ui.GlyphView.Kind.SUN, moon ? p.violet : p.amber, 18));
        TextView lt = Ui.text(this, "Soonest · " + Fmt.day(this, first.timeMs, tz), 13, 600, p.muted);
        lt.setSingleLine(true);
        lt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ltl = new LinearLayout.LayoutParams(0, -2, 1f);
        ltl.setMarginStart(Ui.dp(this, 8));
        label.addView(lt, ltl);
        TextView see = Ui.smallButton(this, p, "See it");
        see.setContentDescription("See the soonest in the viewfinder");
        final long at = first.timeMs;
        see.setOnClickListener(v -> openMoment(at));
        label.addView(see);
        label.addView(moreKey());
        card.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // The day is in the label; the time alone is large, so it never breaks (3.9: "PM" fell to a second line).
        TextView big = Ui.text(this, Fmt.time(this, first.timeMs, tz), 30, 700, p.text);
        big.setLetterSpacing(-0.02f);
        big.setFontFeatureSettings("tnum");
        big.setSingleLine(true);
        row.addView(big, new LinearLayout.LayoutParams(0, -2, 1f));
        View hair = new View(this);
        hair.setBackgroundColor(p.line);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 40));
        hl.setMarginStart(Ui.dp(this, 10));
        hl.setMarginEnd(Ui.dp(this, 12));
        row.addView(hair, hl);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(Ui.text(this, "Bearing", 12.5f, 500, p.muted));
        right.addView(Ui.text(this, dir, 17, 600, p.text), top(2));
        row.addView(right);
        card.addView(row);
        card.addView(Ui.text(this, (moon ? "Moon " : "Sun ") + where() + " · " + up(first) + " · " + light
                + (until.isEmpty() ? "" : " · " + until), 13.5f, 400, p.muted), top(6));
        card.setContentDescription("Soonest " + Fmt.when(this, first.timeMs, tz));
        return card;
    }

    private void renderTimes(String lineWords) {
        body.addView(Ui.segmented(this, p, new String[]{"Behind subject", "Behind you"}, mode, this::setMode), top(12));
        String bn = flow.moon ? "moon" : "sun";
        double hides = hiddenAll();
        if (mode == 0) {
            // 3.12 (owner: "options to the aligning of the sun"); 3.12.2 ("this is not clear"): pictures, and one plain
            // sentence saying what the choice means from here, in degrees
            body.addView(Ui.text(this, "Where the " + bn + " sits behind the subject", 15, 700, p.text), top(16));
            body.addView(Ui.sitsChoice(this, p, LINE_SITS, new int[]{0, 1, 2, 3}, lineSits == 4 ? -1 : lineSits, i -> {
                lineSits = i;
                prefs.edit().putInt("line_sits", i).apply();
                buildSheet();
            }), top(8));
            double d = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
            double rise = Heights.rise(this, you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
            double foot = FrameMath.baseAngle(rise, d), topA = FrameMath.topAngle(heightNow(), d, rise);
            String deg = "%.1f°";
            String[] means = {
                    "Every time the " + bn + " lines up, at any height.",
                    "Low, just clearing the horizon: up to " + String.format(Locale.US, deg, Math.max(0, hides) + 2) + " up.",
                    "Behind the subject's middle: about " + String.format(Locale.US, deg, (foot + topA) / 2) + " up.",
                    "Just above the subject's top: " + String.format(Locale.US, deg, topA) + " to "
                            + String.format(Locale.US, deg, topA + 1) + " up.",
                    "Between " + String.format(Locale.US, deg, sitsWindow(4)[0]) + " and "
                            + String.format(Locale.US, deg, sitsWindow(4)[1]) + " up: every time it lines up in that range."};
            body.addView(Ui.text(this, means[Math.min(lineSits, 4)], 13.5f, 500, p.text), top(8));

            // 3.13 (owner: "change angle instead of subject height"): the sun's height itself, on a slider; the pictures
            // set it, a drag picks any height (none of the pictures lit then). The subject's top is said for reference.
            // 3.19.4 (owner: "show me in the slider where the sun is visible, I don't want to guess"): one tick for each
            // height the body really is on this line in the coming year, red below what hides it. Nothing estimated.
            Astro.Found fnd = flow.moon ? foundMoon : foundSun;
            double hideTo = Math.max(0, hiddenAll());
            java.util.List<Double> real = new java.util.ArrayList<>();
            double lowest = Double.MAX_VALUE, highest = -Double.MAX_VALUE;
            if (fnd != null) {
                for (Astro.Hit h : fnd.all) {
                    if (h.alt < 0) continue;
                    real.add(h.alt);
                    lowest = Math.min(lowest, h.alt);
                    highest = Math.max(highest, h.alt);
                }
            }
            // 3.23 (phase 3, B-12): the slider reaches the heights the body really has here (it stopped at 20°)
            double sliderTop = highest > 19 ? Math.min(90, Math.ceil(highest + 1)) : 20;
            LinearLayout aHead = new LinearLayout(this);
            aHead.setGravity(Gravity.CENTER_VERTICAL);
            aHead.addView(Ui.text(this, (flow.moon ? "Moon" : "Sun") + " height", 14, 600, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
            double[] shown = sitsWindow(lineSits);
            // the range the choice stands for, on the slider (Any: the whole slider; Horizon: from the horizon up)
            double showLo = lineSits == 0 ? 0 : Math.max(0, shown[0]), showHi = lineSits == 0 ? sliderTop : Math.min(sliderTop, shown[1]);
            TextView aValue = Ui.text(this, lineSits == 0 ? "Any height" : String.format(Locale.US, "%.1f° to %.1f° up", showLo, showHi),
                    14, 700, p.text);
            aHead.addView(aValue);
            body.addView(aHead, top(12));
            RangeBar bar = new RangeBar(this, p, 0, sliderTop);       // from the horizon, a tenth of a degree at a time
            bar.set(showLo, showHi);
            bar.setContentDescription((flow.moon ? "Moon" : "Sun") + " height range");
            bar.setListener((lo, hi, done) -> {
                aValue.setText(String.format(Locale.US, "%.1f° to %.1f° up", lo, hi));
                if (!done) return;
                lineLo = (float) lo;
                lineHi = (float) hi;
                lineSits = 4;
                prefs.edit().putInt("line_sits", 4).putFloat("line_lo", lineLo).putFloat("line_hi", lineHi).apply();
                // 3.19.3: not inside the touch. This runs on the finger's lift, from the slider's own onTouchEvent, and
                // buildSheet() removes that slider; a button's click is posted by Android, this one was not, and the
                // next layout pass met a null child (NullPointerException in dispatchCollectViewAttributes).
                getWindow().getDecorView().post(() -> {
                    try {
                        buildSheet();
                    } catch (RuntimeException ex) {   // 3.19.1: written down (shown at the next start), not a crash
                        CrashLog.caught(this, "sun height range", ex);
                        say("That range could not be shown. Pick another.");
                    }
                });
            });
            double[] ticks = new double[real.size()];
            for (int i = 0; i < ticks.length; i++) ticks[i] = real.get(i);
            bar.setAvailability(ticks, hideTo > 0.05 ? hideTo : -90);
            body.addView(bar, new LinearLayout.LayoutParams(-1, -2));
            // 3.20 (owner: "make these bullet points with icons"): one idea per point, each with its icon
            if (real.isEmpty()) {
                body.addView(note(Ui.Icon.CHART, "No time in the coming year puts the " + bn + " on this line above the horizon.")
                        .color(p.text).size(12.5f, 500), top(4));
            } else {
                body.addView(note(Ui.Icon.CHART, "Shaded: the heights the " + bn + " really is on this line in the coming year, "
                        + String.format(Locale.US, "%.1f° to %.1f°", lowest, highest)
                        + ".").color(p.text).size(12.5f, 500), top(4));
            }
            if (hideTo > 0.05) body.addView(note(Ui.Icon.WARNING, "Red: hidden up to " + String.format(Locale.US, "%.1f°", hideTo) + ".")
                    .color(p.bad), top(6));
            body.addView(note(Ui.Icon.HEIGHT, "From here the subject's top is " + String.format(Locale.US, deg, topA) + " up and its foot "
                    + String.format(Locale.US, deg, foot) + "."), top(6));
        }
        String hidden = hidesWords(bn);
        if (!hidden.isEmpty()) body.addView(note(hidesIcon(hidden), hidden), top(6));
        String houses = buildingsWords(bn);
        if (!houses.isEmpty() && !hidden.startsWith("A building")) body.addView(note(Ui.Icon.CUBE, houses), top(6));
        String cover = coverWords();
        if (!cover.isEmpty()) body.addView(note(Ui.Icon.WARNING, cover).color(p.bad).size(13, 600), top(6));
        body.addView(fromRow(), top(10));
        if (flow.moon) addSection("Moon", foundMoon, true);
        else addSection("Sun", foundSun, false);
        // (3.8: "Build the shot" moved to Your shot, the last page, shown as the other tool.)

        body.addView(note(Ui.Icon.VIEWFINDER, "Tap a time to see it in the viewfinder."), top(16));
        body.addView(note(flow.moon ? Ui.Icon.MOON : Ui.Icon.SUN, "Low sun or low moon = it lines up 8° or less above the "
                + "horizon (the moon also at least 60% lit), which can be months away."), top(6));
        body.addView(note(Ui.Icon.LAYERS, "Line: " + lineWords + (subjectHeight > 0 ? " · subject " + Fmt.height(subjectHeight) : "") + "."), top(6));
        body.addView(note(Ui.Icon.CLOCK, zoneNote()), top(6));
        body.addView(note(Ui.Icon.MOVE, "To move a point, drag it or press and hold the map."), top(6));
    }

    /** One point of notes (3.20): an icon, then the words, muted by default. */
    private Ui.Note note(Ui.Icon icon, CharSequence s) {
        return Ui.note(this, p, icon, s);
    }

    /** The icon for what hides a low body: a building, or the land (the subject's ground or the hills). */
    private static Ui.Icon hidesIcon(String words) {
        return words.startsWith("A building") ? Ui.Icon.CUBE : Ui.Icon.HILL;
    }

    /** Which day the search starts from: now, or a day you pick ("when does it line up in June?"). */
    private View fromRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, searchFrom > 0 ? "From " + Fmt.day(this, searchFrom, tz) : "From now", 14, 500, p.text),
                new LinearLayout.LayoutParams(0, -2, 1f));
        if (searchFrom > 0) {
            TextView now = Ui.smallButton(this, p, "From now");
            now.setOnClickListener(v -> setSearchFrom(0));
            row.addView(now);
        }
        TextView pick = Ui.smallButton(this, p, "Pick a date");
        pick.setOnClickListener(v -> pickDate(searchFrom > 0 ? searchFrom : System.currentTimeMillis(), this::setSearchFrom));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.setMarginStart(Ui.dp(this, 8));
        row.addView(pick, pl);
        return row;
    }

    private void setSearchFrom(long ms) {
        searchFrom = ms > 0 && ms <= System.currentTimeMillis() ? 0 : ms;
        refresh();
    }

    private android.app.Dialog dialog;

    private void pickDate(long shown, java.util.function.LongConsumer picked) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(shown);
        DatePickerDialog d = new DatePickerDialog(this, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT, (v, y, mo, dd) -> {
            Calendar n = Calendar.getInstance(tz);
            n.clear();
            n.set(y, mo, dd, 0, 0, 0);
            picked.accept(n.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog = d;
        d.show();
    }

    private void addSection(String name, Astro.Found f, boolean moon) {
        body.addView(Ui.text(this, name, 16, 700, p.text), top(20));
        if (f == null) {
            addLine(working ? "Working it out…" : "");
            return;
        }
        String bn = moon ? "moon" : "sun";
        if (f.next == null) {
            if (f.hidden) addLine("It lines up only while hidden. " + hidesWords(bn)
                    + (buildingsUp() > -90 ? " To ignore buildings: Settings, Buildings." : ""));
            else addNever(name, moon ? nearMoon : nearSun, moon);
            return;
        }
        String lowName = moon ? "Low moon" : "Low sun";
        // 3.9 (owner): the times of the year, by month; days in a row are one run ("2 to 8 Apr · 05:33 to 05:50") that
        // opens to its days when tapped. The soonest is the card above; its run is marked.
        body.getChildAt(body.getChildCount() - 1).setVisibility(View.GONE);   // the "Sun" heading: the months say it
        java.util.List<java.util.List<Astro.Hit>> runs = new java.util.ArrayList<>();
        Calendar cal = Calendar.getInstance(tz);
        int lastMonth = -1;
        java.util.List<Astro.Hit> fits = shown(f);
        Astro.Hit soon = fits.isEmpty() ? null : fits.get(0);
        for (Astro.Hit h : fits) {
            cal.setTimeInMillis(h.timeMs);
            int month = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
            java.util.List<Astro.Hit> run = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            boolean next = run != null && month == lastMonth && h.timeMs - run.get(run.size() - 1).timeMs < 30 * 3600000L;
            if (!next) {
                run = new java.util.ArrayList<>();
                runs.add(run);
            }
            run.add(h);
            lastMonth = month;
        }
        if (runs.isEmpty()) body.addView(note(lineSits == 0 ? Ui.Icon.INFO : Ui.Icon.WARNING, lineSits == 0 ? "No other time in the coming year."
                : "Never " + LINE_SITS_WORDS[lineSits] + " in the coming year: choose Any above to see every time.")
                .color(p.text).size(15, 600), top(18));
        int shownMonth = -1;
        java.text.SimpleDateFormat monthName = new java.text.SimpleDateFormat("LLLL yyyy", Locale.getDefault());
        monthName.setTimeZone(tz);
        for (java.util.List<Astro.Hit> run : runs) {
            cal.setTimeInMillis(run.get(0).timeMs);
            int month = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
            if (month != shownMonth) {
                shownMonth = month;
                body.addView(Ui.text(this, monthName.format(new java.util.Date(run.get(0).timeMs)), 15, 700, p.text), top(18));
                View rule = new View(this);
                rule.setBackgroundColor(p.design == Ui.POCKET ? p.text : p.line);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, p.design == Ui.POCKET ? 1.5f : 1)));
                rl.topMargin = Ui.dp(this, 6);
                body.addView(rule, rl);
            }
            if (run.size() == 1) {
                Astro.Hit h = run.get(0);
                boolean low = Astro.isLow(h, moon);
                addHit(body, h, soon != null && h.timeMs == soon.timeMs ? "Soonest" : low ? lowName : "", moon, low, lowName);
            } else {
                addRun(run, f, moon, lowName);
            }
        }
        // (what hides the low times is said once, above the list: hidesWords)
    }

    /**
     * One answer. The time leads; under it what kind of answer it is (Next, Low) and the light it comes in, as
     * small labels, then the details. Tap the row to see it in the Frame; the bell sets a reminder.
     */
    /**
     * Days in a row as one run (3.9): the days, the times and the heights from first to last; a tap opens its days
     * under it (each opens the viewfinder), another tap folds them away.
     */
    private void addRun(java.util.List<Astro.Hit> run, Astro.Found f, boolean moon, String lowName) {
        Astro.Hit first = run.get(0), last = run.get(run.size() - 1);
        double lo = 90, hi = -90;
        boolean anyLow = false, allLow = true, soonest = false;
        java.util.List<Astro.Hit> fits = shown(f);
        final Astro.Hit soon = fits.isEmpty() ? null : fits.get(0);     // the soonest where it sits as chosen (3.12)
        for (Astro.Hit h : run) {
            lo = Math.min(lo, h.alt);
            hi = Math.max(hi, h.alt);
            boolean l = Astro.isLow(h, moon);
            anyLow |= l;
            allLow &= l;
            soonest |= soon != null && h.timeMs == soon.timeMs;
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, Fmt.days(this, first.timeMs, last.timeMs, tz), 17, 700, p.text));
        long loR = Math.round(Math.max(0, lo)), hiR = Math.round(hi);
        String heights = hi < 0.5 ? "on the horizon" : loR == hiR ? hiR + "° up" : loR + "° to " + hiR + "° up";
        col.addView(Ui.text(this, Fmt.times(this, first.timeMs, last.timeMs, tz) + " · " + heights + " · " + run.size()
                + " days", 13, 400, p.muted), top(2));
        LinearLayout tags = new LinearLayout(this);
        if (soonest) tags.addView(Ui.tag(this, "Soonest", p.accent));
        if (anyLow) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            if (tags.getChildCount() > 0) tl.setMarginStart(Ui.dp(this, 6));
            tags.addView(Ui.tag(this, allLow ? lowName : lowName + " some days", p.accent), tl);
        }
        if (tags.getChildCount() > 0) col.addView(tags, wrapTop(5));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView chev = new android.widget.ImageView(this);
        chev.setImageDrawable(Ui.icon(this, Ui.Icon.CHEV_DOWN, p.accent));
        row.addView(chev, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.VERTICAL);
        days.setPadding(Ui.dp(this, 14), 0, 0, 0);
        days.setVisibility(View.GONE);
        row.setClickable(true);
        Ui.pressable(row);
        String what = Fmt.days(this, first.timeMs, last.timeMs, tz) + ", " + run.size() + " days";
        row.setContentDescription(what + ". Show the days");
        row.setOnClickListener(v -> {
            boolean open = days.getVisibility() != View.VISIBLE;
            if (open && days.getChildCount() == 0) {
                for (Astro.Hit h : run) {
                    boolean low = Astro.isLow(h, moon);
                    addHit(days, h, soon != null && h.timeMs == soon.timeMs ? "Soonest" : "", moon, low, lowName);
                }
            }
            days.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.setRotation(open ? 180 : 0);
            row.setContentDescription(what + (open ? ". Hide the days" : ". Show the days"));
        });
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        body.addView(days, new LinearLayout.LayoutParams(-1, -2));
        View hair = new View(this);
        hair.setBackgroundColor(p.line);
        body.addView(hair, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));
    }

    private void addHit(LinearLayout into, Astro.Hit h, String label, boolean moon, boolean low, String lowName) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 3.8: ruled rows, no cards and no badge each (owner: "a lot of wasted space"); a tap opens the viewfinder.
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        addWhen(col, h.timeMs);
        LinearLayout tags = new LinearLayout(this);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        if (!label.isEmpty()) tags.addView(Ui.tag(this, label, low ? p.accent : p.muted));
        Light.Kind light = Light.of(Astro.sun(h.timeMs, you.getLatitude(), you.getLongitude())[1]);
        if (light != Light.Kind.DAY && (light != Light.Kind.NIGHT || moon)) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            if (tags.getChildCount() > 0) tl.setMarginStart(Ui.dp(this, 6));
            tags.addView(Ui.tag(this, Light.name(light), light == Light.Kind.GOLDEN ? p.amber : p.violet), tl);
        }
        String until = Fmt.until(h.timeMs, System.currentTimeMillis());
        String detail = up(h) + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : "") + (until.isEmpty() ? "" : " · " + until);
        col.addView(Ui.text(this, detail, 13, 400, p.muted), top(3));
        if (tags.getChildCount() > 0) col.addView(tags, wrapTop(5));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView see = new android.widget.ImageView(this);
        see.setImageDrawable(Ui.icon(this, Ui.Icon.VIEWFINDER, p.accent));
        row.addView(see, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));

        row.setClickable(true);
        Ui.pressable(row);
        row.setContentDescription((moon ? "Moon, " : "Sun, ") + (label.isEmpty() ? "" : label + ", ") + Fmt.when(this, h.timeMs, tz)
                + ". See it in the viewfinder");
        row.setOnClickListener(v -> openMoment(h.timeMs));
        into.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View hair = new View(this);
        hair.setBackgroundColor(p.line);
        into.addView(hair, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));
    }

    /** A row's moment: "Nov 26 · 3:28 PM" on one line, the weekday under it when it has one (never cut, 3.3). */
    private void addWhen(LinearLayout col, long ms) {
        col.addView(Ui.text(this, Fmt.whenShort(this, ms, tz), 17, 700, p.text));
        String wd = Fmt.weekday(this, ms, tz);
        if (!wd.isEmpty()) col.addView(Ui.text(this, wd, 13, 500, p.muted), top(1));
    }

    /**
     * The body never crosses the line: say so plainly, show the closest it comes and by how much it misses, and
     * offer to turn the line that much (You moves, the subject and the distance stay).
     */
    private void addNever(String name, Astro.Near near, boolean moon) {
        String lower = name.toLowerCase(Locale.US);
        addLine("The " + lower + " never crosses this line" + (moon ? " in the coming months" : " at any time of year")
                + ": it points too far north or south for the " + lower + " at this place.");
        if (near == null) return;
        long deg = Math.max(1, Math.round(Math.abs(near.off)));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        row.setBackground(Ui.shape(this, p.panel, p.line, 14, false));
        row.addView(badge(moon, near.alt < Astro.LOW_ALT, Astro.moonLit(near.timeMs), near.timeMs));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        addWhen(col, near.timeMs);
        col.addView(Ui.tag(this, "Closest", p.muted), wrapTop(5));
        col.addView(Ui.text(this, "Misses by " + deg + "° to the " + (near.off > 0 ? "right" : "left") + " · "
                + (near.alt < 0.5 ? "on the horizon" : Math.round(near.alt) + "° up"), 13, 400, p.muted), top(5));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.setMarginStart(Ui.dp(this, 12));
        row.addView(col, cl);
        body.addView(row, top(8));   // Line it up is the main key above (one key, not two)
    }

    /** Moves You round the subject, at the same distance, so the line points where the body will be at its closest. */
    private void lineUp(Astro.Near near, String body) {
        final GeoPoint was = you;
        final String id = mapLineId;
        double d = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        double aim = Astro.lineUpBearing(near);   // a little inside the body's reach, so the line crosses it
        double[] at = Astro.destination(subject.getLatitude(), subject.getLongitude(),
                mode == 0 ? Astro.norm360(aim + 180) : aim, d);
        you = new GeoPoint(at[0], at[1]);
        mapLineId = MAP_LINE;
        dropPin();
        saveState();
        refresh();
        fit(you, subject);
        // Says what was done, not the answer (Ali, 2026-11-20: "lines up" over a "Never" card): the land and buildings
        // this new way come later and can still hide it, and the card always shows the answer as it stands.
        say("You moved round the subject, the same distance away.", "Undo", () -> {
            you = was;
            mapLineId = id;
            dropPin();
            saveState();
            refresh();
        });
    }

    /** The saved name of the line on the map, or null if it is not saved. */
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

    private void addLine(String s) {
        body.addView(note(Ui.Icon.INFO, s).size(13.5f, 400), top(6));
    }

    // ---- the Day tab: where the sun and the moon go ----

    private void buildDay(long ref) {
        double lat = you.getLatitude(), lon = you.getLongitude(), bearing = lineBearing();
        dayStart = Fmt.midnight(ref, tz);
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(dayStart);
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
    }

    private void renderDay() {
        buildDay(cursorMs);
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton prev = new Ui.IconButton(this, p, Ui.Icon.CHEV_LEFT, "Previous day");
        Ui.IconButton next = new Ui.IconButton(this, p, Ui.Icon.CHEV_RIGHT, "Next day");
        prev.setOnClickListener(v -> moveDay(-1));
        next.setOnClickListener(v -> moveDay(1));
        TextView date = Ui.smallButton(this, p, Fmt.day(this, dayStart + 3600000L, tz));
        date.setContentDescription("Pick a day");
        date.setOnClickListener(v -> pickDate(cursorMs, ms -> {
            cursorMs = ms + 12 * 3600000L;      // midday of the picked day
            buildSheet();
        }));
        nav.addView(prev);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, -2, 1f);
        dl.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        nav.addView(date, dl);
        nav.addView(next);
        body.addView(nav, top(4));

        sunRead = Ui.text(this, "", 14, 400, p.text);
        moonRead = Ui.text(this, "", 14, 400, p.text);
        moonReadGlyph = new Ui.GlyphView(this, Ui.GlyphView.Kind.MOON, p.violet, 24);
        // 3.9: the time picked is read right above the chart, in sight with the map
        if (flow.moon) body.addView(readRow(moonReadGlyph, moonRead), top(2));
        else body.addView(readRow(new Ui.GlyphView(this, Ui.GlyphView.Kind.SUN, p.amber, 24), sunRead), top(2));

        DayChart chart = new DayChart(this, p);
        dayChart = chart;
        chart.setData(dayStart, dayEnd, sunAlt, moonAlt, sunHits, moonHits, Fmt.timeFormat(this, tz));
        chart.setBody(flow.moon);
        chart.setCursor(cursorMs);
        chart.setListener(ms -> {
            cursorMs = ms;
            updateReadouts();
            updateShadows();
            map.redraw();
        });
        body.addView(chart, top(4));
        body.addView(note(flow.moon ? Ui.Icon.MOON : Ui.Icon.SUN, (flow.moon ? "Purple is the moon" : "Orange is the sun")
                + ": how high it is through the day."), top(10));
        body.addView(note(Ui.Icon.STACK, "The strip under it is the light."), top(6));
        body.addView(note(Ui.Icon.MOVE, "Drag along the chart, or the " + (flow.moon ? "moon" : "sun")
                + " on the map, to pick a time; the map shows it then, and its path."), top(6));
        updateReadouts();
    }

    private LinearLayout readRow(View glyph, TextView text) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(glyph);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(this, 10));
        row.addView(text, tl);
        row.setLayoutParams(top(8));
        return row;
    }

    private void moveDay(int delta) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(cursorMs);
        c.add(Calendar.DAY_OF_YEAR, delta);   // keep the same time of day
        cursorMs = c.getTimeInMillis();
        buildSheet();
    }

    /** Where the sun and moon are at the cursor, in words, with the light. */
    private void updateReadouts() {
        if (sunRead == null || moonRead == null || you == null) return;
        double lat = you.getLatitude(), lon = you.getLongitude(), bearing = lineBearing();
        double[] s = Astro.sun(cursorMs, lat, lon), m = Astro.moon(cursorMs, lat, lon);
        sunRead.setText(Fmt.time(this, cursorMs, tz) + " · " + readout(s, bearing, Light.name(Light.of(s[1]))));
        float lit = (float) Astro.moonLit(cursorMs);
        moonReadGlyph.setMoon(lit, Astro.moonWaxing(cursorMs));
        moonRead.setText("Moon · " + readout(m, bearing, Math.round(lit * 100) + "% lit"));
    }

    private String readout(double[] pos, double bearing, String extra) {
        // Just under 0 is still sunset or moonrise to the eye (the air lifts it): "on the horizon", never
        // "below the horizon · lined up" together (Ali's test, 2026-11-15).
        String out = pos[1] >= 0.5 ? Math.round(pos[1]) + "° up, " + Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0])
                : pos[1] >= -1 ? "on the horizon, " + Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0])
                : "below the horizon";
        if (extra != null) out += " · " + extra;
        if (Math.abs(FrameMath.diff(pos[0], bearing)) < 0.6 && pos[1] >= -1) out += " · lined up";
        return out;
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
            say("Turn on location in your phone settings, then press this again.");
            return;
        }
        say("Finding you…");
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
        map.animateTo(g, 15.0);
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
        if (code == REQ_CAMERA) {
            java.util.function.Consumer<Boolean> answer = pendingCamera;
            pendingCamera = null;
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (!ok) say("Without the camera, Compass follows the phone over the drawn sky.");
            if (answer != null) answer.accept(ok);
            return;
        }
        if (code == REQ_NOTIFY) {
            Reminders.Item item = pendingReminder;
            Runnable changed = pendingChanged;
            pendingReminder = null;
            pendingChanged = null;
            if (item == null) return;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                setReminder(item, pendingLine, changed); // the bell does its job once allowed, no second press
            } else {
                say("Without notifications SunWhere cannot remind you. "
                        + "You can allow them in the phone's settings.");
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
        say("Without location, move the map by hand to where you are.");
    }

    // ---- the screens over the map ----

    private void showSearch() {
        if (search != null) return;
        // The search sets the point that is still missing; with a line drawn, the subject.
        boolean asYou = flow.current() == Flow.YOU;
        search = new SearchScreen(this, p, this, asYou);
        search.setInsets(insetTop, insetBottom);
        root.addView(search, new FrameLayout.LayoutParams(-1, -1));
    }

    /** AR (3.5): the camera, asked for once at the first Compass press; refused, the drawn sky stays. */
    @Override public void askCamera(java.util.function.Consumer<Boolean> answer) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            answer.accept(true);
            return;
        }
        if (prefs.getBoolean("camera_refused", false)) {
            answer.accept(false);
            return;
        }
        pendingCamera = ok -> {
            if (!ok) prefs.edit().putBoolean("camera_refused", true).apply();
            answer.accept(ok);
        };
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    @Override public double[] searchNear() {
        // Near what the map shows, once it shows a town: zoomed out over the world its middle means nothing (Ali, 3.3)
        GeoPoint c = map.center();
        if (c == null || map.zoom() < 8) return null;
        return new double[]{c.getLatitude(), c.getLongitude()};
    }

    @Override public void closeSearch() {
        if (search == null) return;
        search.hideKeyboard();
        root.removeView(search);
        search = null;
    }

    @Override public void placePicked(Places.Place place, boolean asYou) {
        closeSearch();
        GeoPoint g = new GeoPoint(place.lat, place.lon);
        if (tooClose(asYou ? subject : you, g)) {
            say("That is on top of the other point. Pick a place at least 10 m away.");
            return;
        }
        backupYou = backupSubject = null;
        if (asYou) {
            you = g;
            armed = 0;
            dropPin();
        } else {
            subject = g;
            subjectName = place.name;
            armed = 1;
        }
        mapLineId = MAP_LINE;
        saveState();
        if (you != null && subject != null) fit(you, subject);
        else {
            map.setCenter(g, 16.0);
        }
        refresh();
    }

    private void showFrame(long ms) {
        if (frame != null || you == null || subject == null) return;
        FrameScreen.Line l = new FrameScreen.Line();
        l.youLat = you.getLatitude();
        l.youLon = you.getLongitude();
        l.subLat = subject.getLatitude();
        l.subLon = subject.getLongitude();
        l.mode = mode;
        l.height = subjectHeight;
        l.kind = subjectKind;
        l.name = subjectName != null ? subjectName : lineName();
        l.tz = tz;
        l.sunNext = next(foundSun);
        l.moonNext = next(foundMoon);
        l.panDeg = shotPan;
        l.pitch = shotPitch;
        l.moon = flow.moon;
        // Opened on Your shot: "Sun lined up" goes back to that moment, not to the soonest one (Ali, 2026-11-15)
        // 3.23 (phase 3, B-03): only a moment that is lined up is called so. A time saved from the viewfinder after
        // the scale was dragged is not; Build the shot's planned moment is (the body sits where the picture put it).
        if (flow.current() == Flow.SHOT && ms > 0 && (flow.tool == Flow.BUILD || onLineAt(ms, flow.moon))) {
            if (flow.moon) l.moonNext = ms; else l.sunNext = ms;
        }
        cursorMs = ms;
        frame = new FrameScreen(this, this, l, ms, focal, portrait);
        frame.setInsets(insetTop, insetBottom);
        root.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        setupWindow();
        hideSnack.run();
        guideOnce(Tutorial.Kind.FRAME);   // 3.20: how the viewfinder works, the first time it opens
    }

    @Override public void closeFrame() {
        if (frame == null) return;
        cursorMs = frame.time();
        setShotFraming(frame.panDeg(), frame.framePitch());
        boolean onShot = flow.current() == Flow.SHOT;
        root.removeView(frame);
        frame = null;
        setupWindow();
        if (you != null && subject != null && (tab == 1 || onShot)) buildSheet();   // Your shot's picture as left there
    }

    @Override public void frameTime(long ms) { cursorMs = ms; }

    @Override public void frameLens(double focalMm, boolean portrait) {
        this.focal = focalMm;
        this.portrait = portrait;
        prefs.edit().putFloat("focal_mm", (float) focalMm).putBoolean("portrait", portrait).apply();
    }

    @Override public void subjectHeight(double metres, int kind) {
        subjectHeight = metres;
        subjectKind = kind;
        saveState();
        if (trip == null && you != null && subject != null) buildSheet();
    }

    // ---- Plan a frame: make the picture first, be told where to stand ----

    private void showCompose() {
        if (compose != null || subject == null) return;
        String key = subject.getLatitude() + "," + subject.getLongitude() + "," + flow.moon;
        if (keptCompose != null && key.equals(keptComposeKey)) {     // the picture as it was left
            compose = keptCompose;
            keptCompose = null;
            compose.setInsets(insetTop, insetBottom);
            root.addView(compose, new FrameLayout.LayoutParams(-1, -1));
            hideSnack.run();
            return;
        }
        keptCompose = null;
        keptComposeKey = key;
        compose = new ComposeScreen(this, this, subject.getLatitude(), subject.getLongitude(), subjectName,
                subjectHeight, subjectKind, focal, portrait, tz, drone, searchFrom > 0 ? searchFrom : System.currentTimeMillis(),
                flow.moon);
        compose.setInsets(insetTop, insetBottom);
        root.addView(compose, new FrameLayout.LayoutParams(-1, -1));
        hideSnack.run();
    }

    /** The compose view's own Back key: the same as the phone's Back, one step back. */
    @Override public void droneChanged(boolean d) {
        drone = d;
        prefs.edit().putBoolean("drone", drone).apply();
    }

    @Override public void closeCompose() {
        if (flow.current() == Flow.COMPOSE) {
            flow.back();
            enterStep();
        } else {
            removeCompose();
        }
    }

    /** Takes Build it off the screen, and keeps it: going back to it shows the same picture. */
    private void removeCompose() {
        if (compose == null) return;
        root.removeView(compose);
        keptCompose = compose;
        compose = null;
    }

    @Override public void composed(double lat, double lon, long timeMs, double focalMm, boolean port, double height, int kind,
                                   double panDeg, double pitch) {
        removeCompose();
        backupYou = backupSubject = null;
        you = new GeoPoint(lat, lon);
        // 3.23 (phase 3, B-04): Build the shot always puts the body behind the subject; Line up's Behind you never leaks in
        mode = 0;
        prefs.edit().putInt("mode", 0).apply();
        subjectHeight = height;
        subjectKind = kind;
        mapLineId = MAP_LINE;
        armed = 0;
        tab = 0;
        frameLens(focalMm, port);
        setShotFraming(panDeg, pitch);             // the picture as it is framed on screen (after a lens change too)
        saveState();
        goShot(timeMs);
        dropPin();
        fit(you, subject);
    }

    @Override public int ground(double lat, double lon) {
        return Ground.at(lat, lon, getPackageName());
    }

    // ---- planned shoots, from the viewfinder ----

    /** The key a moment at this time has as a shoot: the moon's if the moon is the one on the line then. */
    private String shootKey(long ms) {
        double b = lineBearing();
        double[] s = Astro.sun(ms, you.getLatitude(), you.getLongitude()), m = Astro.moon(ms, you.getLatitude(), you.getLongitude());
        boolean moon = m[1] > -1 && Math.abs(FrameMath.diff(m[0], b)) < Math.abs(FrameMath.diff(s[0], b));
        return Reminders.key(mapLineId, moon, ms);
    }

    @Override public boolean planned(long ms) {
        Trip.Line l = savedShot();
        return l != null && Reminders.has(this, Reminders.key(l.id, flow.moon, ms));
    }

    /** The bell in the viewfinder: the same as Remind me on Your shot. */
    @Override public void planShoot(long ms, Runnable changed) {
        if (you == null || subject == null) return;
        if (ms != shotMs) {
            shotMs = ms;
            prefs.edit().putLong("shot_ms", ms).apply();
            setSavedShot(null);
        }
        remind(ms, changed);
    }

    /** You moved in the viewfinder: the same You everywhere; the answers are worked out again. */
    @Override public boolean moveYou(double lat, double lon) {
        if (subject != null && tooClose(subject, new GeoPoint(lat, lon))) {
            say("That is on top of the subject. Stand at least 10 m away.");
            return false;
        }
        retimeShot = flow.current() == Flow.SHOT;
        you = new GeoPoint(lat, lon);
        mapLineId = MAP_LINE;
        setSavedShot(null);
        saveState();
        refresh();
        return true;
    }

    @Override public void saveShot(long ms) {
        if (you == null || subject == null) return;
        if (ms != shotMs) {
            shotMs = ms;
            prefs.edit().putLong("shot_ms", ms).apply();
            setSavedShot(null);
        }
        Trip.Line before = savedShot();
        Trip.Line l = saveShot(before == null);   // already saved: its framing is brought up to date (review)
        // 3.23 (phase 3, B-10): a second tap keeps the first bar (its Undo and Rename); later it says it is saved already
        if (before != null && l != null && snack.getVisibility() != View.VISIBLE) say("Already saved as " + l.name);
    }

    @Override public void styleMap(MapView m) {
        m.setTileSource(styles[style].source);
        m.setBackgroundColor(p.bg);
        TilesOverlay tiles = m.getOverlayManager().getTilesOverlay();
        tiles.setLoadingBackgroundColor(p.bg);
        tiles.setLoadingLineColor(p.line);
        tiles.setColorFilter(mapFilter(style == 1));
    }

    @Override public void showSettings() {
        if (settings != null) return;
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) names[i] = styles[i].name;
        settings = new SettingsSheet(this, p, names, style, new SettingsSheet.Listener() {
            @Override public void onTheme(int theme) {
                if (theme == Ui.theme(MainActivity.this)) return;
                prefs.edit().putInt("theme", theme).apply();
                Reveal.keep(root, touchX, touchY);
                recreate();                       // the whole screen reads the new theme; everything is saved already
            }
            @Override public void onDesign(int design) {
                if (design == Ui.design(MainActivity.this)) return;
                prefs.edit().putInt("design", design).apply();
                Reveal.keep(root, touchX, touchY);
                recreate();                       // like the theme: everything reads the new design
            }
            @Override public void onAccent(int accent) {
                if (accent == Ui.accentIndex(MainActivity.this)) return;
                prefs.edit().putInt("accent2", accent).apply();
                Reveal.keep(root, touchX, touchY);
                recreate();
            }
            @Override public void onMapStyle(int s) { setStyle(s); }
            @Override public void onMapColours(int k) { setMapColours(k); }
            // 3.23 (sweep S10): a guide opens over Settings, so closing it comes back to Settings
            @Override public void onHelp() { showTutorial(); }
            @Override public void onGuide(Tutorial.Kind kind) { showTutorial(kind); }
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
        showTutorial(Tutorial.Kind.INTRO);
    }

    /** A guide (3.20): How it works, a tool's, or the viewfinder's. One at a time, over everything. */
    private void showTutorial(Tutorial.Kind kind) {
        if (tutorial != null) return;
        tutorial = new Tutorial(this, p, kind, this::closeTutorial);
        tutorial.setInsets(insetTop, insetBottom);
        root.addView(tutorial, new FrameLayout.LayoutParams(-1, -1));
    }

    /** A tool's or the viewfinder's guide, the first time only (3.20). Posted: the screen under it is built first. */
    private void guideOnce(Tutorial.Kind kind) {
        if (prefs.getBoolean(Tutorial.seenKey(kind), false)) return;
        root.post(() -> { if (tutorial == null) showTutorial(kind); });
    }

    private void closeTutorial() {
        if (tutorial == null) return;
        Tutorial.Kind kind = tutorial.kind();
        root.removeView(tutorial);
        tutorial = null;
        prefs.edit().putBoolean(Tutorial.seenKey(kind), true).apply();
        // Location is asked when the first map opens (a tool), not on Home where nothing needs it yet (audit 3.2.2)
        if (locationAskPending) {
            locationAskPending = false;
            offerLocation();
        }
    }

    private boolean locationAskPending;   // a tool's first map waits for its guide to close (3.22)

    /** The first map: where you are, with your yes. Already allowed: it just goes there. */
    private void offerLocation() {
        prefs.edit().putBoolean("asked_location", true).apply();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locate(false);
            return;
        }
        say("Open the map where you are? Your location centres the map and can place You.", "Use my location",
                () -> locate(true), null, null);
        snack.removeCallbacks(hideSnack);
        snack.postDelayed(hideSnack, 15000);   // long enough to read it (Ali, 2026-11-22: it went in 7 s)
    }

    /** The map's location key: straight there when allowed; otherwise say what it is for before Android asks. */
    private void askLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locate(true);
            return;
        }
        say("Your location centres the map where you are. Android asks next.", "Allow", () -> locate(true), null, null);
    }

    /**
     * Back closes the top thing first, then goes one step back in the tool, then to Home; only Home leaves the
     * app (all is saved as you go). A plain Activity has no AndroidX dispatcher; with targetSdk 35 and no
     * enableOnBackInvokedCallback in the manifest, onBackPressed is still what Back calls.
     */
    @Override
    public void onBackPressed() {
        if (PickSheet.closeTop(this)) return;
        if (mapLook != null) closeMapLook();
        else if (tutorial != null) closeTutorial();
        else if (settings != null) closeSettings();
        else if (frame != null) { if (!frame.closeDialog()) closeFrame(); }
        else if (compose != null && compose.closeDialog()) { /* the dialog closed */ }
        else if (search != null) closeSearch();
        else if (trip != null) closeTrip();
        else if (home != null) super.onBackPressed();
        else if (sheet.stop() == Sheet.FULL && sheet.isPullable()) sheet.setStop(Sheet.HALF, true);
        else if (fromSaved && flow.current() == Flow.SHOT) {
            fromSaved = false;
            flow.tool = Flow.HOME;
            enterStep();
            showTrip();
        }
        else {
            flow.back();
            enterStep();
        }
    }

    // ---- Home and the steps of each tool ----

    private void showHome() {
        flow.tool = Flow.HOME;
        removeCompose();
        keptCompose = null;
        if (you == null && asideYou != null) {     // the spot set aside by Where to stand or Build the shot
            you = asideYou;
            asideYou = null;
            saveState();
            refresh();
        }
        if (home == null) {
            home = new HomeScreen(this, p, this);
            home.setInsets(insetTop, insetBottom);
            root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        }
        home.refresh(flow.moon, continueText());
        hideSnack.run();
    }

    private void hideHome() {
        if (home == null) return;
        root.removeView(home);
        home = null;
    }

    /** What the Continue card says, or null when there is nothing to go back to. */
    private String continueText() {
        Flow last = Flow.decode(prefs.getString("flow", null));
        if (last.tool == Flow.HOME || (subject == null && you == null)) return null;
        // Where it goes on, by the step's own name: "your subject" opened on Where you stand (audit 3.2.2)
        String what = subjectName != null ? subjectName + " · " : "";
        return Flow.toolName(last.tool) + ", " + what + last.stepTitle();
    }

    @Override public void startTool(int tool) {
        // A tool started from Home starts clean: no old pin where a point is to be picked (owner, 3.4). The bar
        // offering the last points went in 3.8 (owner: "so annoying"); Continue keeps everything as it was.
        if (tool == Flow.LINE) you = null;
        subject = null;
        subjectName = null;
        mapLineId = MAP_LINE;
        setSavedShot(null);
        saveState();
        flow.start(tool);
        tab = 0;
        enterStep();
        Tutorial.Kind guide = tool == Flow.LINE ? Tutorial.Kind.LINE : Tutorial.Kind.BUILD;
        guideOnce(guide);   // 3.20: its guide, the first time
        if (!prefs.getBoolean("asked_location", false)) {
            // Once: the first map opens where you are. 3.22 (Ali, 2026-11-20: Android asked over the guide, before the
            // app said why): after the guide, and the app says what it is for before Android asks.
            if (prefs.getBoolean(Tutorial.seenKey(guide), false)) offerLocation();
            else locationAskPending = true;
        }
    }

    @Override public void continueTool() {
        Flow last = Flow.decode(prefs.getString("flow", null));
        if (last.current() == Flow.SHOT && last.moon != flow.moon) last.back();   // the body changed on Home
        last.moon = flow.moon;
        flow = last;
        enterStep();
        if (you != null && subject != null) fit(you, subject);
        else if (subject != null) map.setCenter(subject, Double.NaN);
    }

    /**
     * Sun or moon. The accent follows the body (amber, violet) unless one is fixed in Settings, and the new
     * colours open in a circle from the finger.
     */
    @Override public void setMoon(boolean moon) {
        if (flow.moon == moon) return;
        Reveal.play(root, touchX, touchY, () -> {
            flow.moon = moon;
            prefs.edit().putBoolean("moon", moon).apply();
            p.setMoon(moon);
            if (home != null) home.refresh(moon, continueText());
            else if (flow.current() == Flow.SHOT) {
                flow.back();                       // Your shot was the other body's moment: choose again for this one
                enterStep();
            } else {
                prefs.edit().putString("flow", flow.encode()).apply();
                updateStepBar();
                refresh();
            }
        });
    }

    /** The colours in use (the tests read the accent). */
    Ui.Palette palette() { return p; }

    @Override public void showSaved() { showTrip(); }

    @Override public void showHelp() { showTutorial(); }

    /** The guide of the tool open (3.20): from the three dots, or Build the shot's Help. */
    @Override public void showToolGuide() {
        showTutorial(flow.tool == Flow.BUILD ? Tutorial.Kind.BUILD : Tutorial.Kind.LINE);
    }

    /** The viewfinder's guide (3.20): its Help key. */
    @Override public void showFrameGuide() { showTutorial(Tutorial.Kind.FRAME); }

    /** Does this step have what it needs? (The points it works on.) */
    private boolean ready(int step) {
        switch (step) {
            case Flow.YOU: return flow.tool != Flow.LINE || subject != null;   // 3.8: the subject comes first
            case Flow.WHEN: case Flow.SHOT: return you != null && subject != null;
            case Flow.COMPOSE: return subject != null;
            default: return true;
        }
    }

    /** Shows the step the flow is on: the step bar, the sheet, and the full-screen view a step needs. */
    private void enterStep() {
        if (flow.tool == Flow.HOME) {
            showHome();
            return;
        }
        while (flow.step > 0 && !ready(flow.current())) flow.step--;
        if (flow.current() != Flow.SHOT) fromSaved = false;
        // Build the shot exists to find where you stand: You is placed by its last step.
        if (flow.tool == Flow.BUILD && flow.current() != Flow.SHOT && you != null) {
            if (asideYou == null) asideYou = you;   // back on Home it comes back: starting a tool clears nothing
            you = null;
            mapLineId = MAP_LINE;
            saveState();
        }
        prefs.edit().putString("flow", flow.encode()).apply();
        hideHome();
        if (flow.current() == Flow.COMPOSE) showCompose();
        else removeCompose();
        updateStepBar();
        refresh();
    }

    private void updateStepBar() {
        if (flow.tool == Flow.HOME) return;
        stepText.setText(flow.bar());
        stepText.setContentDescription("Step " + flow.bar());
        bodyText.setText(flow.moon ? "Moon" : "Sun");
        bodyIcon.setImageDrawable(Ui.icon(this, flow.moon ? Ui.Icon.MOON : Ui.Icon.SUN, p.accent));
        ((View) bodyText.getParent()).setContentDescription("Sun or moon: " + (flow.moon ? "Moon" : "Sun"));
        int st = flow.current();
        searchRow.setVisibility(st == Flow.YOU || st == Flow.SUBJECT ? View.VISIBLE : View.GONE);
        ((View) searchRow.getParent()).requestLayout();
    }

    private void nextStep() {
        if (!ready(flow.steps(flow.tool).length > flow.step + 1 ? Flow.steps(flow.tool)[flow.step + 1] : -1)) {
            // 3.22 (Ali): a Next that cannot go on says why, never nothing
            say(subject == null ? "Put your subject on the map first." : "Put where you stand on the map first.");
            return;
        }
        flow.next();
        enterStep();
    }

    /** Your shot at this moment: the last step of every tool. */
    // The framing of Your shot (3.3): set in Build the shot, or kept with a saved shot; centred otherwise.
    private double shotPan = 0, shotPitch = Double.NaN;

    private void setShotFraming(double pan, double pitch) {
        shotPan = pan;
        shotPitch = pitch;
        prefs.edit().putString("shot_pan", Double.toString(pan))
                .putString("shot_pitch", Double.isNaN(pitch) ? "" : Double.toString(pitch)).apply();
    }

    private void goShot(long ms) { goShot(ms, flow.tool == Flow.BUILD); }

    /** A moment picked on When opens straight in the viewfinder (owner, 3.8); Back from it shows Your shot. */
    private void openMoment(long ms) {
        goShot(ms);
        showFrame(ms);
    }

    private void goShot(long ms, boolean keepFraming) {
        if (!keepFraming) setShotFraming(0, Double.NaN);   // the other tools give a centred picture
        asideYou = null;                           // You has a new spot now
        if (ms != shotMs) setSavedShot(null);
        shotMs = ms;
        cursorMs = ms;
        prefs.edit().putLong("shot_ms", ms).apply();
        sheet.setStop(Sheet.HALF, false);          // Your shot opens with the map in sight (3.9)
        flow.go(Flow.SHOT);
        enterStep();
    }

    /** Your shot: the moment in large words, where you stand, and what you can do next. */
    // ---- the shot as a small picture (3.3): what the settings mean, and what the moment looks like ----

    private FrameView stillPicture(String description) {
        FrameView v = new FrameView(this);
        v.setStill(true);
        v.setContentDescription(description);
        v.setBackground(Ui.shape(this, 0xFF0B1220, 0, 14, false));
        v.setClipToOutline(true);                  // rounded like the sheet's other surfaces
        return v;
    }

    /** Your shot: the real sun and moon at the moment, from where you stand, framed as in Build the shot. */
    private FrameView shotPicture(long ms) {
        FrameView v = stillPicture("Your shot as a picture");
        v.setClickable(true);
        v.setOnClickListener(x -> showFrame(ms));
        double yLat = you.getLatitude(), yLon = you.getLongitude(), sLat = subject.getLatitude(), sLon = subject.getLongitude();
        double[] s = Astro.sun(ms, yLat, yLon), m = Astro.moon(ms, yLat, yLon);
        double az = Astro.bearing(yLat, yLon, sLat, sLon), dist = Astro.distance(yLat, yLon, sLat, sLon);
        double rise = Heights.rise(this, yLat, yLon, sLat, sLon), height = heightNow();
        v.setLens(focal, portrait);
        v.setSubject(az, dist, height, subjectKind, lineBearing(), mode == 1, rise);
        v.setBodies(s[0], s[1], m[0], m[1], Astro.moonLit(ms), Astro.moonWaxing(ms));
        if (Buildings.on(this)) v.setBuildings(Buildings.get(this, yLat, yLon, lineBearing()), yLat, yLon, sLat, sLon);
        double body = mode == 1 ? -1 : flow.moon ? m[1] : s[1];
        double pitch = !Double.isNaN(shotPitch) ? shotPitch : FrameMath.aimPitch(FrameMath.fovV(focal, portrait),
                Math.max(body, FrameMath.topAngle(height, dist, rise)));
        v.setAim(az + shotPan, pitch, false);
        return v;
    }

    private void renderShot() {
        // 3.9 (owner: "I can't see the map and it's messy"): a sheet that pulls, the answer and its keys in its head,
        // the picture and the rest under it, so the map stays in sight above.
        sheet.setPullable(true);
        sheet.setHalfShare(0.5f);
        sheet.setMoreWords("Pull up for Save and Remind me");   // the picture is in sight already (3.23, B-17)
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout under = sheet.body;
        boolean moon = flow.moon;
        long ms = shotMs > 0 ? shotMs : System.currentTimeMillis();
        double[] pos = moon ? Astro.moon(ms, you.getLatitude(), you.getLongitude()) : Astro.sun(ms, you.getLatitude(), you.getLongitude());
        double lit = moon ? Astro.moonLit(ms) : 1;
        Astro.Hit h = new Astro.Hit(ms, pos[1], lit);
        String sub = (moon ? "Moon " : "Sun ") + shotWhere(ms, moon) + " · " + up(h) + (moon ? " · " + Math.round(lit * 100) + "% lit" : "");
        String wd = Fmt.weekday(this, ms, tz);    // the time is the answer: never cut for a long weekday (Ali, 3.3)
        double back = Astro.bearing(subject.getLatitude(), subject.getLongitude(), you.getLatitude(), you.getLongitude());
        double m = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        String ground = groundWords(you.getLatitude(), you.getLongitude());
        boolean flying = flow.tool == Flow.BUILD && drone;   // the same word as the spot it came from
        if (p.readoutLike()) {
            // Readout (3.7): the moment in large figures on the graphite panel, the picture, three numbers under it.
            LinearLayout r = Ui.readout(this, p);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            String label = (wd.isEmpty() ? "" : wd + " · ") + (moon ? "Moon " : "Sun ") + shotWhere(ms, moon);
            top.addView(Ui.readoutLabel(this, p, label), new LinearLayout.LayoutParams(0, -2, 1f));
            Ui.IconButton more = moreKey();
            more.tint(p.readoutText);
            top.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
            r.addView(top);
            r.addView(Ui.readoutBig(this, p, Fmt.whenShort(this, ms, tz), 30));
            String until = Fmt.until(ms, System.currentTimeMillis());
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.topMargin = Ui.dp(this, 12);
            r.addView(Ui.cells(this, p, new String[][]{
                    {Fmt.distance(m), "distance, " + Astro.compass(back)},
                    {up(h), moon ? Math.round(lit * 100) + "% lit" : Light.name(Light.of(h.alt)).toLowerCase(Locale.ROOT)},
                    {until.isEmpty() ? Math.round(focal) + " mm" : until.replace("in ", ""), until.isEmpty() ? "lens" : "from now"}}), cl);
            sheet.head.addView(r);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 120));
            pl.topMargin = Ui.dp(this, 12);
            under.addView(shotPicture(ms), pl);
            if (!ground.isEmpty()) under.addView(note(Ui.Icon.HILL, ground.substring(0, 1).toUpperCase(Locale.ROOT)
                    + ground.substring(1) + ".").size(13, 400), top(8));
            under.addView(note(Ui.Icon.CLOCK, zoneNote()).size(13, 400), top(6));
        } else {
            sheet.head.addView(headRow(badge(moon, Astro.isLow(h, moon), lit, ms), Fmt.whenShort(this, ms, tz),
                    wd.isEmpty() ? sub : wd + " · " + sub, moreKey()));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 120));
            pl.topMargin = Ui.dp(this, 12);
            under.addView(shotPicture(ms), pl);      // see it, not only read it (3.3); a tap opens the viewfinder
            if (p.design == Ui.POCKET) {
                // Pocket map (3.7): where you stand and the body, as the map key names them.
                LinearLayout key = Ui.mapKey(this, p);
                Ui.keyRow(this, p, key, Ui.Swatch.YOU, "Distance", Fmt.distance(m) + " " + Astro.compass(back)
                        + " of the subject" + (ground.isEmpty() ? "" : ", " + ground));
                Ui.keyRow(this, p, key, Ui.Swatch.BODY, moon ? "Moon" : "Sun", up(h)
                        + (moon ? " · " + Math.round(lit * 100) + "% lit" : " · " + Light.name(Light.of(h.alt))));
                under.addView(key, top(12));
                under.addView(note(Ui.Icon.CLOCK, zoneNote()), top(6));
            } else {
                under.addView(note(Ui.Icon.LOCATE, "Distance " + Fmt.distance(m) + " " + Astro.compass(back) + " of the subject.")
                        .size(14, 400), top(8));
                if (!ground.isEmpty()) under.addView(note(Ui.Icon.HILL, ground.substring(0, 1).toUpperCase(Locale.ROOT)
                        + ground.substring(1) + ".").size(14, 400), top(6));
                under.addView(note(Ui.Icon.CLOCK, zoneNote()).size(14, 400), top(6));
            }
        }
        String cover = coverWords();
        if (!cover.isEmpty()) under.addView(note(Ui.Icon.WARNING, cover).color(p.bad).size(13, 600), top(8));
        TextView open = Ui.button(this, p, "Open the viewfinder", true);
        open.setContentDescription("Viewfinder: see the shot as the camera will");
        open.setOnClickListener(v -> showFrame(ms));
        // Close (owner, 3.9: "once done there is no closure"): the tool is done, back to Home; all is saved as it is.
        TextView close = Ui.button(this, p, "Close", false);
        close.setContentDescription("Close: back to Home");
        close.setOnClickListener(v -> closeTool());
        LinearLayout ends = new LinearLayout(this);
        ends.addView(open, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-2, -2);
        xl.setMarginStart(Ui.dp(this, 8));
        ends.addView(close, xl);
        Ui.IconButton share = new Ui.IconButton(this, p, Ui.Icon.SHARE, "Share").small();   // 3.18: a link to this shot
        share.setContentDescription("Share this shot as a link");
        share.setOnClickListener(v -> shareShot());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        sl.setMarginStart(Ui.dp(this, 8));
        ends.setGravity(Gravity.CENTER_VERTICAL);
        ends.addView(share, sl);
        sheet.head.addView(ends, top(12));

        // What comes next: keep it, be reminded, get there.
        Trip.Line saved = savedShot();
        boolean reminds = saved != null && Reminders.has(this, Reminders.key(saved.id, moon, ms));
        if (p.readoutLike()) {          // round keys, each with its name under it
            LinearLayout keys = new LinearLayout(this);
            LinearLayout save = Ui.namedKey(this, p, Ui.Icon.SAVED, saved != null ? "Saved" : "Save", saved != null);
            save.setContentDescription(saved != null ? "Saved as " + saved.name : "Save this shot");
            save.setOnClickListener(v -> { if (savedShot() == null) saveShot(true); });
            boolean future = ms > System.currentTimeMillis();
            LinearLayout remind = Ui.namedKey(this, p, reminds ? Ui.Icon.BELL_ON : Ui.Icon.BELL,
                    reminds ? "Reminder set" : "Remind me", reminds);
            remind.setContentDescription(reminds ? "Reminder set. Press to take it off" : "Remind me 30 minutes before");
            remind.setEnabled(future);
            remind.setAlpha(future ? 1f : 0.5f);
            remind.setOnClickListener(v -> remind(ms, this::buildSheet));
            LinearLayout go = Ui.namedKey(this, p, Ui.Icon.DIRECTIONS, "Directions", false);
            go.setContentDescription("Directions to your spot");
            go.setOnClickListener(v -> directions());
            for (View k : new View[]{save, remind, go}) keys.addView(k, new LinearLayout.LayoutParams(0, -2, 1f));
            under.addView(keys, top(14));
            addOtherTool(under);
            return;
        }
        LinearLayout next = new LinearLayout(this);
        TextView save = Ui.smallButton(this, p, saved != null ? "Saved" : "Save");
        save.setContentDescription(saved != null ? "Saved as " + saved.name : "Save this shot");
        save.setOnClickListener(v -> { if (savedShot() == null) saveShot(true); });
        next.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean future = ms > System.currentTimeMillis();
        TextView remind = Ui.smallButton(this, p, reminds ? "Reminder set" : "Remind me");
        for (TextView k : new TextView[]{save, remind}) {   // one line each: "Reminder set" broke in two (Ali, 3.3)
            k.setSingleLine(true);
            k.setMinHeight(Ui.dp(this, 40));                  // setSingleLine resets the height to one line
            k.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        }
        remind.setContentDescription(reminds ? "Reminder set. Press to take it off" : "Remind me 30 minutes before");
        remind.setEnabled(future);
        remind.setAlpha(future ? 1f : 0.5f);
        remind.setOnClickListener(v -> remind(ms, this::buildSheet));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -2, 1f);
        rl.setMarginStart(Ui.dp(this, 8));
        next.addView(remind, rl);
        TextView go = Ui.smallButton(this, p, "Directions");
        go.setSingleLine(true);
        go.setMinHeight(Ui.dp(this, 40));
        go.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        go.setContentDescription("Directions to your spot");
        go.setOnClickListener(v -> directions());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(0, -2, 1f);
        gl.setMarginStart(Ui.dp(this, 8));
        next.addView(go, gl);
        under.addView(next, top(14));
        addOtherTool(under);
    }

    /**
     * The last page offers the other tool (owner, 3.8: "indicate it's the other mode, use the icon, put it on the last
     * page"): from Line up a street, Build the shot keeps the subject and finds where to stand for another light.
     */
    /** Close on Your shot: the tool is done; Home, with Continue to come back (3.9). */
    private void closeTool() {
        flow.tool = Flow.HOME;
        enterStep();
    }

    private void addOtherTool(LinearLayout into) {
        if (flow.tool != Flow.LINE) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12));
        row.setBackground(p.design == Ui.APPLIANCE ? Ui.face(this, p, 22, false)
                : Ui.shape(this, p.raised, p.line, p.readoutLike() ? 22 : 14, false));
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageDrawable(Ui.icon(this, Ui.Icon.TOOL_BUILD, p.text));
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, "The other tool", 12, 500, p.muted));
        col.addView(Ui.text(this, Flow.toolName(Flow.BUILD), 16, 700, p.text), top(1));
        col.addView(Ui.text(this, "Keep the subject: place the " + (flow.moon ? "moon" : "sun")
                + " in the picture, get where to stand.", 12.5f, 400, p.muted), top(2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.setMarginStart(Ui.dp(this, 12));
        cl.setMarginEnd(Ui.dp(this, 8));
        row.addView(col, cl);
        android.widget.ImageView chev = new android.widget.ImageView(this);
        chev.setImageDrawable(Ui.icon(this, Ui.Icon.CHEV_RIGHT, p.muted));
        row.addView(chev, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        row.setClickable(true);
        Ui.pressable(row);
        row.setContentDescription("Build the shot: the other tool, with this subject");
        row.setOnClickListener(v -> buildFromLine());
        into.addView(row, top(16));
    }

    // ---- shots: saved in one press, named by their place ----

    private void setSavedShot(String id) {
        savedShotId = id;
        if (id == null) prefs.edit().remove("saved_shot").apply();
        else prefs.edit().putString("saved_shot", id).apply();
    }

    /** Your shot as it is in Saved, or null if it is not saved (or was removed there). */
    private Trip.Line savedShot() {
        if (savedShotId == null || you == null || subject == null) return null;
        for (Trip.Line l : Trip.loadLines(this)) {
            if (!l.id.equals(savedShotId)) continue;
            // Only while it is the same shot: the same two points, the same moment, the same body.
            boolean same = Math.abs(l.youLat - you.getLatitude()) < 1e-7 && Math.abs(l.youLon - you.getLongitude()) < 1e-7
                    && Math.abs(l.subLat - subject.getLatitude()) < 1e-7 && Math.abs(l.subLon - subject.getLongitude()) < 1e-7
                    && l.timeMs == shotMs && l.moon == flow.moon;
            return same ? l : null;
        }
        return null;
    }

    /** Saves Your shot at once: "Shot 3" (or the subject's name) until the place name comes. */
    private Trip.Line saveShot(boolean tell) {
        if (frame != null) setShotFraming(frame.panDeg(), frame.framePitch());   // as framed on screen now (a double tap re-centres)
        Trip.Line have = savedShot();
        if (have != null) {
            boolean same = have.panDeg == shotPan && (have.pitch == shotPitch || (Double.isNaN(have.pitch) && Double.isNaN(shotPitch)));
            if (!same) {                          // the same shot, framed again: keep the framing on screen (review)
                Trip.setFraming(this, have.id, shotPan, shotPitch);
                have = savedShot();
            }
            return have;
        }
        Trip.Line onMap = mapLine();
        Trip.Line draft = new Trip.Line(onMap.id, subjectName, onMap.youLat, onMap.youLon, onMap.subLat, onMap.subLon, mode);
        draft.height = subjectHeight;
        draft.kind = subjectKind;
        draft.timeMs = shotMs;
        draft.moon = flow.moon;
        draft.named = false;
        draft.subjectName = subjectName;
        draft.panDeg = onMap.panDeg;             // as framed (3.3)
        draft.pitch = onMap.pitch;
        Trip.Line l = Trip.addShot(this, draft);
        mapLineIs(l);
        setSavedShot(l.id);
        nameShot(l);
        if (trip != null) trip.reload();
        if (tell) say("Saved as " + l.name, "Undo", () -> {
            Trip.removeShot(this, l.id);
            setSavedShot(null);
            mapLineId = MAP_LINE;
            saveState();
            buildSheet();
        }, "Rename", () -> askRename(l));
        buildSheet();
        return l;
    }

    private boolean online() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /** One request for the place's name; the shot keeps its number name if there is no answer. */
    @Override public void nameShot(Trip.Line l) {
        if (l.named || !online()) return;
        final String id = l.id;
        Places.reverse(l.subLat, l.subLon, place -> {
            if (place == null || isFinishing()) return;
            Trip.Line now = null;                     // as it is now: renamed by hand while we waited?
            for (Trip.Line x : Trip.loadLines(this)) if (x.id.equals(id)) now = x;
            if (now == null || now.named) return;
            Trip.rename(this, id, Trip.uniqueName(Trip.loadLines(this), id, Trip.shotName(now.name, place)), true);
            if (trip != null) trip.reload();
            if (id.equals(savedShotId) && flow.current() == Flow.SHOT) buildSheet();
        });
    }

    private void askRename(Trip.Line l) {
        android.widget.EditText name = new android.widget.EditText(this);
        name.setSingleLine(true);
        name.setText(l.name);
        name.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        int s = Ui.dp(this, 20);
        wrap.setPadding(s, Ui.dp(this, 4), s, 0);
        wrap.addView(name);
        dialog = new android.app.AlertDialog.Builder(this, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Rename this shot")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    Trip.rename(this, l.id, n, true);
                    if (trip != null) trip.reload();
                    buildSheet();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The bell: saves the shot too (a reminder belongs to a shot), then sets or takes off its reminder. */
    private void remind(long ms, Runnable changed) {
        Trip.Line l = saveShot(false);
        boolean moon = flow.moon;
        String key = Reminders.key(l.id, moon, ms);
        toggleReminder(new Reminders.Item(key, ms, (moon ? "Moon " : "Sun ") + where() + " at " + Fmt.time(this, ms, tz),
                l.name), l, changed);
    }

    /** The phone's maps app, at the spot to stand. No service of ours. */
    private void directions() {
        String at = String.format(Locale.US, "%.6f,%.6f", you.getLatitude(), you.getLongitude());
        android.content.Intent go = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("geo:" + at + "?q=" + at + "(Your spot)"));
        try {
            startActivity(go);
        } catch (android.content.ActivityNotFoundException e) {
            say("No maps app on this phone.");
        }
    }

    @Override public void openShot(Trip.Line l) {
        closeTrip();
        openLine(l);
        map.view().post(() -> fit(you, subject));
        if (l.timeMs > 0) {
            flow.moon = l.moon;
            prefs.edit().putBoolean("moon", l.moon).apply();
            p.setMoon(l.moon);
            shotMs = l.timeMs;
            setSavedShot(l.id);
            setShotFraming(l.panDeg, l.pitch);   // framed as it was saved, before the sheet is drawn
            goShot(l.timeMs, true);
            fromSaved = true;      // Back goes back to Saved, where the shot was opened (audit 3.2.2)
        } else {
            sheet.setStop(Sheet.HALF, false);      // When opens with the map in sight (3.23, sweep S2: it was 66 px)
        }
    }

    private boolean fromSaved;   // Your shot was opened from Saved

    private void renderStep() {
        switch (flow.current()) {
            case Flow.YOU: renderPlace(true); break;
            case Flow.SUBJECT: renderPlace(false); break;
            case Flow.WHEN: if (you != null && subject != null) renderLine(); break;
            case Flow.SHOT: if (you != null && subject != null) renderShot(); break;
            case Flow.COMPOSE: renderPlace(false); break;
            default: break;
        }
    }

    /** The steps that place a point: one question, how to answer it, and Next once it is answered. */
    private void renderPlace(boolean youStep) {
        sheet.setPullable(false);
        sheet.setHalfShare(0.47f);
        GeoPoint pt = youStep ? you : subject;
        boolean line = flow.tool == Flow.LINE;
        // Once the point is set the step says so, and what Next does (the three questions, 3.3)
        String b = flow.moon ? "moon" : "sun";
        String title = pt == null ? (youStep ? "Where will you stand?" : "What do you want to photograph?")
                : youStep ? "You are on the map" : subjectName != null ? "Your subject: " + subjectName : "Your subject is on the map";
        boolean both = you != null && subject != null;
        double lineM = both ? Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude()) : 0;
        String lineDir = both ? Math.round(lineBearing()) % 360 + "° " + Astro.compass(lineBearing()) : "";
        if (p.readoutLike() && pt != null) {
            // Readout (3.7): the point named large on the graphite panel; the line's numbers under it.
            LinearLayout r = Ui.readout(this, p);
            r.addView(Ui.readoutLabel(this, p, youStep ? "Where you stand" : "Your subject"));
            TextView big = Ui.text(this, youStep ? "You are on the map" : subjectName != null ? subjectName : "On the map",
                    24, 600, p.readoutText);
            big.setLetterSpacing(-0.02f);
            big.setMaxLines(2);
            big.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(this, 4);
            r.addView(big, bl);
            if (both) {
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
                cl.topMargin = Ui.dp(this, 12);
                double hgt = heightNow();
                r.addView(Ui.cells(this, p, new String[][]{{Fmt.distance(lineM), "the line"}, {lineDir, "bearing"},
                        {hgt > 0 ? Fmt.height(hgt) : "Not set", "subject's height"}}), cl);
            }
            r.setContentDescription(title);
            sheet.head.addView(r);
        } else {
            sheet.head.addView(Ui.text(this, title, 22, 700, p.text));
        }
        // 3.8: the subject first in both tools, then where you stand (owner: "inconsistent").
        String how = youStep ? "Use your location, or tap the map where you will stand: the other end of the street from your subject."
                : line ? "Tap it on the map or search above: the building, peak or tower at the end of a street. That is your subject."
                : "Tap it on the map or search above: a building, a peak, a tree. That is your subject.";
        if (pt != null) how = "Tap again or drag it to move it. " + (youStep ? "Next shows when the " + b + " lines up."
                : line ? "Next: where you stand." : flow.tool == Flow.BUILD
                ? "Next: place the " + b + " in the picture." : "Next: how you want the " + b + ".");
        sheet.head.addView(Ui.text(this, how, 15, 400, p.muted), top(6));
        if (p.design == Ui.POCKET && (you != null || subject != null)) {
            // Pocket map (3.7): the map key names what is on the map.
            LinearLayout key = Ui.mapKey(this, p);
            if (you != null) Ui.keyRow(this, p, key, Ui.Swatch.YOU, "You", "on the map");
            if (subject != null) Ui.keyRow(this, p, key, Ui.Swatch.SUBJECT, "Subject",
                    (subjectName != null ? subjectName + ", " : "") + FrameMath.KINDS[subjectKind].toLowerCase(Locale.ROOT)
                            + " " + Fmt.height(heightNow()));
            if (both) Ui.keyRow(this, p, key, Ui.Swatch.LINE, "Line", Fmt.distance(lineM) + " · " + lineDir);
            sheet.head.addView(key, top(12));
        }
        if (!youStep && subject != null) {
            // 3.10 (owner: "ask what target"): what the subject is gives its height; a person is not a tower.
            String[] names = new String[FrameMath.KINDS.length];
            for (int i = 0; i < names.length; i++) names[i] = FrameMath.KINDS[i] + " · about " + Fmt.height(FrameMath.KIND_HEIGHTS[i]);
            sheet.head.addView(Ui.dropdown(this, p, "What is it?", names, subjectKind, i -> {
                subjectKind = i;
                subjectHeight = FrameMath.KIND_HEIGHTS[i];
                saveState();
                buildSheet();
            }), top(12));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (youStep) {
            TextView here = Ui.button(this, p, "Use my location", false);
            here.setOnClickListener(v -> {
                pendingSetYou = true;
                locate(true);
            });
            actions.addView(here, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        TextView next = Ui.button(this, p, "Next", true);
        // 3.23 (phase 3, B-07): dimmed but never disabled, so a tap says what is missing (a disabled key hears nothing)
        next.setAlpha(pt != null ? 1f : 0.5f);
        next.setOnClickListener(v -> nextStep());
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        if (youStep) nl.setMarginStart(Ui.dp(this, 8));
        actions.addView(next, nl);
        sheet.head.addView(actions, top(14));
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), insetBottom + Ui.dp(this, 8));
    }

    // ---- saved lines and the trip ----

    private void showTrip() {
        if (trip != null) return;
        trip = new Trip(this, p, this);
        trip.setInsets(insetTop, insetBottom);
        root.addView(trip, new FrameLayout.LayoutParams(-1, -1));
        if (online()) trip.nameUnnamed();
    }

    /** "Save line" on the sheet: the Saved screen opens and asks for the name at once. */
    private void saveLine() {
        showTrip();
        trip.saveMapLine();
    }

    @Override public void closeTrip() {
        if (trip == null) return;
        root.removeView(trip);
        trip = null;
        refresh(); // reminders or the line's name may have changed
    }

    @Override public Trip.Line mapLine() {
        if (you == null || subject == null) return null;
        Trip.Line l = new Trip.Line(mapLineId, subjectName == null ? "" : subjectName, you.getLatitude(), you.getLongitude(),
                subject.getLatitude(), subject.getLongitude(), mode);
        l.height = subjectHeight;
        l.kind = subjectKind;
        l.panDeg = flow.current() == Flow.SHOT ? shotPan : 0;
        l.pitch = flow.current() == Flow.SHOT ? shotPitch : Double.NaN;
        return l;
    }

    @Override public void mapLineIs(Trip.Line l) {
        // Reminders already set for this line's moments move with it to the saved line.
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
            Shoots.rekey(this, i.key, l.id + rest);
        }
        mapLineId = l.id;
        saveState();
    }

    @Override public void showOnMap(Trip.Line l) {
        openLine(l);
        closeTrip();
        map.view().post(() -> fit(you, subject));
    }

    /** A moment in the Saved list was tapped: its line goes on the map and the Frame opens at that time. */
    @Override public void showMoment(Trip.Line l, long ms, boolean moon) {
        flow.moon = moon;
        prefs.edit().putBoolean("moon", moon).apply();
        p.setMoon(moon);
        openLine(l);
        closeTrip();
        map.view().post(() -> fit(you, subject));
        goShot(ms);
        fromSaved = true;                          // Back from it returns to Saved (Ali, 3.3)
        showFrame(ms);
    }

    private void openLine(Trip.Line l) {
        you = new GeoPoint(l.youLat, l.youLon);
        subject = new GeoPoint(l.subLat, l.subLon);
        mode = l.mode;
        mapLineId = l.id;
        subjectName = l.subjectName;              // as it was when saved (Ali, 3.3)
        subjectHeight = l.height;
        subjectKind = l.kind;
        backupYou = backupSubject = null;
        tab = 0;
        prefs.edit().putInt("mode", mode).apply();
        saveState();
        flow.start(Flow.LINE);
        flow.go(Flow.WHEN);
        enterStep();
    }

    // ---- reminders ----

    @Override public void toggleReminder(Reminders.Item item, Trip.Line line, Runnable changed) {
        if (Reminders.has(this, item.key)) {
            Reminders.remove(this, item.key);
            changed.run();
            say("Taken off your shoots.", "Undo", () -> {
                if (item.timeMs > System.currentTimeMillis()) Reminders.add(this, item);
                if (trip != null) trip.refresh();
                else if (frame == null) buildSheet();
            });
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = item;
            pendingChanged = changed;
            pendingLine = line;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            return;
        }
        setReminder(item, line, changed);
    }

    /** Plans the shoot: the reminder, and with it where and how it is shot, so it opens by itself from Shoots. */
    private void setReminder(Reminders.Item item, Trip.Line line, Runnable changed) {
        if (item.timeMs <= System.currentTimeMillis()) {
            say("That moment has passed.");
            return;
        }
        Reminders.add(this, item);
        Shoots.put(this, item.key, item.timeMs, line, focal, portrait);
        long at = Reminders.fireAt(item.timeMs);
        // 3.23 (phase 3, B-25): one "reminder", and the phone's own time when the place keeps another
        String t = Fmt.time(this, at, tz);
        if (tz.getOffset(at) != TimeZone.getDefault().getOffset(at)) t += " there (" + Fmt.time(this, at, TimeZone.getDefault()) + " your time)";
        String when = item.timeMs - Reminders.LEAD_MS < System.currentTimeMillis()
                ? "It is less than 30 minutes away, so the reminder comes in a moment."
                : "I'll remind you at " + t + ", 30 minutes before.";
        say((trip != null ? "Planned. " : "") + when);
        if (changed != null) changed.run();
    }

    // ---- saving as you go ----

    private void loadState() {
        mode = prefs.getInt("mode", 0);
        lineSits = prefs.getInt("line_sits", 0);
        float old = prefs.getFloat("line_angle", 4f);       // 3.13's single angle becomes a range of the same width
        lineLo = prefs.getFloat("line_lo", old - (float) ANGLE_SLACK);
        lineHi = prefs.getFloat("line_hi", old + (float) ANGLE_SLACK);
        shotMs = prefs.getLong("shot_ms", 0);
        try {
            shotPan = Double.parseDouble(prefs.getString("shot_pan", "0"));
            String sp = prefs.getString("shot_pitch", "");
            shotPitch = sp.isEmpty() ? Double.NaN : Double.parseDouble(sp);
        } catch (NumberFormatException e) {
            shotPan = 0;
            shotPitch = Double.NaN;
        }
        savedShotId = prefs.getString("saved_shot", null);
        flow.moon = prefs.getBoolean("moon", Flow.decode(prefs.getString("flow", null)).moon);
        p.setMoon(flow.moon);
        drone = prefs.getBoolean("drone", false);
        focal = prefs.contains("focal_mm") ? prefs.getFloat("focal_mm", 35)
                : FrameMath.FOCALS[Math.max(0, Math.min(FrameMath.FOCALS.length - 1, prefs.getInt("focal", 2)))];
        portrait = prefs.getBoolean("portrait", false);
        mapLineId = prefs.getString("map_line_id", MAP_LINE);
        you = load("you");
        subject = load("subject");
        asideYou = load("aside");
        subjectName = prefs.getString("subject_name", null);
        subjectHeight = prefs.getFloat("subject_h", 0);
        subjectKind = Math.max(0, Math.min(FrameMath.KINDS.length - 1, prefs.getInt("subject_kind", 3)));
        armed = subject != null ? 1 : 0;
        if (prefs.contains("cam_lat")) {
            map.setCenter(new GeoPoint(Double.parseDouble(prefs.getString("cam_lat", "0")),
                    Double.parseDouble(prefs.getString("cam_lon", "0"))), Double.parseDouble(prefs.getString("cam_zoom", "3")));
        } else if (you != null) {
            map.setCenter(you, 14.0);
        } else {
            map.setCenter(new GeoPoint(20.0, 10.0), 3.0);
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
        put(e, "aside", asideYou);
        e.putString("map_line_id", mapLineId);
        if (subjectName == null) e.remove("subject_name"); else e.putString("subject_name", subjectName);
        e.putFloat("subject_h", (float) subjectHeight).putInt("subject_kind", subjectKind);
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

    @Override protected void onStart() {
        super.onStart();
        map.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
        GeoPoint c = map.center();
        prefs.edit().putString("cam_lat", Double.toString(c.getLatitude()))
                .putString("cam_lon", Double.toString(c.getLongitude()))
                .putString("cam_zoom", Double.toString(map.zoom())).apply();
    }

    @Override protected void onStop() {
        super.onStop();
        map.onStop();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        map.onLowMemory();
    }

    @Override protected void onDestroy() {
        generation++;
        super.onDestroy();
        map.onDestroy();
    }

    // ---- the line on the map ----

    /**
     * The time of the drawn day (3.20) when the body stands at this bearing from you: where its path, sampled every
     * ten minutes, passes the bearing, read between the samples. Of the times it does, the one nearest {@code near}
     * wins, a time with the body below the horizon only when none is above it.
     */
    private long timeAtAzimuth(double az, long near) {
        double[] azs = flow.moon ? moonAz : sunAz, alts = flow.moon ? moonAlt : sunAlt;
        long best = near;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i + 1 < DAY_N; i++) {
            double step = norm180(azs[i + 1] - azs[i]);
            if (Math.abs(step) < 1e-6) continue;
            double f = norm180(az - azs[i]) / step;
            if (f < 0 || f > 1) continue;
            long t0 = dayStart + (dayEnd - dayStart) * i / (DAY_N - 1), t1 = dayStart + (dayEnd - dayStart) * (i + 1) / (DAY_N - 1);
            long t = t0 + Math.round((t1 - t0) * f);
            double alt = alts[i] + (alts[i + 1] - alts[i]) * f;
            double score = Math.abs(t - near) + (alt < 0 ? 24 * 3600000.0 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private static double norm180(double x) {
        double r = ((x % 360) + 540) % 360 - 180;
        return r;
    }

    /** The Day chart, opened at this time (3.20: after dragging the sun on All times). */
    private void openDayAt(long ms) {
        cursorMs = ms;
        tab = 1;
        buildSheet();
        if (sheet.stop() != Sheet.HALF) sheet.setStop(Sheet.HALF, true);
        else refitMap();
        say("The day at " + Fmt.time(this, ms, tz) + ". Drag the " + (flow.moon ? "moon" : "sun") + " or the chart for another time.");
    }

    private final class LineLayer implements GeoMap.Layer {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path trail = new android.graphics.Path();
        private int drag;   // 0 none, 1 you, 2 subject, 3 the sun or moon on its day path (3.20)
        private float badgeX = Float.NaN, badgeY;          // the sun at the end of the line, for taps
        private float dayX = Float.NaN, dayY;              // the body on its day path, for dragging (3.20)
        private long dragMs;                               // the time the dragged body stands at

        LineLayer() {
            labelText.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            labelText.setTypeface(Ui.font(MainActivity.this, 700));
            labelText.setTextAlign(Paint.Align.CENTER);
            labelEdge.setStyle(Paint.Style.STROKE);
        }

        boolean onSunBadge(Point t) {
            return !Float.isNaN(badgeX) && Math.hypot(t.x - badgeX, t.y - badgeY) < Ui.dp(MainActivity.this, 28);
        }


        // Grab a point and drag it. Dragging does not pan the map; dropping it works out the answer again.
        @Override
        public boolean onTouch(android.view.MotionEvent e, GeoMap mv) {
            if (covered() || (you == null && subject == null) || !mv.ready()) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    GeoMap pr = mv;
                    float reach = Ui.dp(MainActivity.this, 30);
                    double da = Double.MAX_VALUE;
                    if (you != null) {
                        Point a = pr.toPixels(you);
                        float bodyUp = 9 * 2f * Ui.dp(MainActivity.this, 1);   // the pin's round head sits above its tip
                        da = Math.min(Math.hypot(e.getX() - a.x, e.getY() - a.y),
                                Math.hypot(e.getX() - a.x, e.getY() - (a.y - bodyUp)));
                    }
                    double db = Double.MAX_VALUE;
                    if (subject != null) {
                        Point b = pr.toPixels(subject);
                        db = Math.hypot(e.getX() - b.x, e.getY() - b.y);
                    }
                    if (Math.min(da, db) > reach) {
                        // 3.20 (owner: "allow me to move the day by dragging the sun"): the body on its day path
                        // follows the finger round you; the time is the one its path passes there.
                        if (Float.isNaN(dayX) || Math.hypot(e.getX() - dayX, e.getY() - dayY) > reach) return false;
                        drag = 3;
                        dragMs = tab == 1 ? cursorMs : pathMs;
                        mv.view().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        mv.redraw();
                        return true;
                    }
                    drag = db <= da ? 2 : 1;
                    backupYou = backupSubject = null;
                    mv.view().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mv.redraw();
                    return true;
                }
                case android.view.MotionEvent.ACTION_MOVE:
                    if (drag == 0) return false;
                    if (drag == 3) {
                        Point a = mv.toPixels(you);
                        double az = Math.toDegrees(Math.atan2(e.getX() - a.x, -(e.getY() - a.y))) + map.bearing();
                        long t = timeAtAzimuth(Astro.norm360(az), dragMs);
                        if (t / 60000 != dragMs / 60000) {
                            dragMs = t;
                            if (tab == 1) {
                                cursorMs = t;
                                if (dayChart != null) dayChart.setCursor(t);
                                updateReadouts();
                                updateShadows();
                            }
                        }
                        mv.redraw();
                        return true;
                    }
                    // the point rides above the finger, in sight
                    GeoPoint moved = mv.fromPixels(e.getX(), e.getY() - Ui.dp(MainActivity.this, 36));
                    if (drag == 1) you = moved; else { subject = moved; subjectName = null; }
                    mv.redraw();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (drag == 0) return false;
                    if (drag == 3) {
                        drag = 0;
                        mv.view().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        if (tab != 1) openDayAt(dragMs);    // All times: the Day chart opens at the time picked
                        else mv.redraw();
                        return true;
                    }
                    boolean wasYou = drag == 1;
                    drag = 0;
                    if (wasYou) dropPin();
                    mapLineId = MAP_LINE;
                    retimeShot = flow.current() == Flow.SHOT;   // dragged on Your shot: a new moment
                    mv.view().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    saveState();
                    refresh();
                    return true;
                default:
                    return drag != 0;
            }
        }

        @Override
        public void draw(Canvas c, GeoMap mv) {
            if (!mv.ready()) return;
            float k = Ui.dp(MainActivity.this, 1);
            GeoMap pr = mv;
            badgeX = Float.NaN;
            dayX = Float.NaN;

            if (held != null) {                         // where the map is being held, waiting for an answer
                Point h = pr.toPixels(held);
                paint.setShader(null);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * k);
                paint.setColor(p.accent);
                c.drawCircle(h.x, h.y, 11 * k, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(h.x, h.y, 3 * k, paint);
            }

            drawMine(c, pr, k);
            if (you == null) {
                if (subject == null) return;
                Point s = pr.toPixels(subject);
                dot(c, s, subjectColor(), k, drag == 2);
                label(c, subjectName == null ? "Subject" : subjectName, s.x, s.y - 20 * k, k);
                return;
            }
            Point a = pr.toPixels(you);
            Point b = subject == null ? null : pr.toPixels(subject);
            if (b != null) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                if (tab == 1 && sunHits != null) drawDay(c, a, k, drag == 3 ? dragMs : cursorMs);
                else {
                    if (tab == 0 && pathMs > 0 && sunHits != null) drawDay(c, a, k, drag == 3 ? dragMs : pathMs);   // 3.19.4: the curve at once
                    drawSunEnd(c, a, b, k, mv);
                }

                // The line itself: a soft outline so it reads on any map, then the green to blue stroke.
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8 * k);
                paint.setColor(Ui.withAlpha(p.night ? 0xFF000000 : 0xFFFFFFFF, 140));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setStrokeWidth(4 * k);
                // Pocket map and Readout (3.7): the line in the body's colour, like the route on a folded map.
                if (p.design == Ui.CLASSIC) paint.setShader(new LinearGradient(a.x, a.y, b.x, b.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
                else paint.setColor(p.accent);
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setShader(null);
                dot(c, b, subjectColor(), k, drag == 2);
                label(c, subjectName == null ? "Subject" : subjectName, b.x, b.y - 20 * k, k);
            }
            drawYou(c, a, k, false);
        }

        /**
         * Where the sun or moon will stand: a dotted continuation of the line ending in a badge, with the time
         * of the next moment beside it. Tapping the badge sends it to the other end of the line.
         */
        private void drawSunEnd(Canvas c, Point a, Point b, float k, GeoMap mv) {
            float dx = b.x - a.x, dy = b.y - a.y;
            float len = (float) Math.max(1, Math.hypot(dx, dy));
            float ux = dx / len, uy = dy / len;
            float sx = mode == 0 ? b.x : a.x, sy = mode == 0 ? b.y : a.y;
            // 3.9 (owner): the sun stands at the edge of the map along the line, far beyond the subject, not at the
            // line's own length: find where the line leaves the map's open part (under the bar, above the sheet).
            float vx = mode == 0 ? ux : -ux, vy = mode == 0 ? uy : -uy;
            float edge = 34 * k;
            float left = edge, right = mv.view().getWidth() - edge, topY = (topBar != null ? topBar.getBottom() : 0) + edge + 20 * k;
            float bottom = mv.view().getHeight() - 22 * k - edge;
            float reach = Float.MAX_VALUE;
            if (vx > 1e-3f) reach = Math.min(reach, (right - sx) / vx);
            if (vx < -1e-3f) reach = Math.min(reach, (left - sx) / vx);
            if (vy > 1e-3f) reach = Math.min(reach, (bottom - sy) / vy);
            if (vy < -1e-3f) reach = Math.min(reach, (topY - sy) / vy);
            if (reach == Float.MAX_VALUE || reach < 56 * k) reach = 56 * k;   // the end of the line is at the edge already
            float ex = sx + (mode == 0 ? ux : -ux) * reach, ey = sy + (mode == 0 ? uy : -uy) * reach;
            boolean moonFirst = flow.moon;
            int color = moonFirst ? p.violet : p.amber;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5 * k);
            paint.setColor(Ui.withAlpha(p.bg, 150));
            paint.setPathEffect(new DashPathEffect(new float[]{0.1f, 9 * k}, 0));
            c.drawLine(sx, sy, ex, ey, paint);
            paint.setStrokeWidth(3 * k);
            paint.setColor(color);
            c.drawLine(sx, sy, ex, ey, paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(color, 70));
            c.drawCircle(ex, ey, 19 * k, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(ex, ey, 13 * k, paint);
            paint.setColor(color);
            c.drawCircle(ex, ey, 10.5f * k, paint);
            badgeX = ex;
            badgeY = ey;
            // 3.23 (phase 3, B-01/B-02): the moon's answer is null until worked out, as the sun's is; the map draws first
            Astro.Found f = moonFirst ? foundMoon : foundSun;
            Astro.Hit h = f == null ? null : f.next;
            String s = (moonFirst ? "Moon" : "Sun") + (h == null ? "" : " " + Fmt.time(MainActivity.this, h.timeMs, tz));
            label(c, s, ex, ey - 22 * k, k);
        }


        /** The buildings the owner added (3.18): their outlines, in the body's colour, with their height. */
        private void drawMine(Canvas c, GeoMap pr, float k) {
            try {
                drawMineNow(c, pr, k);
            } catch (RuntimeException e) {
                CrashLog.caught(MainActivity.this, "drawMine", e);
            }
        }

        private void drawMineNow(Canvas c, GeoMap pr, float k) {
            if (UserBuildings.all().isEmpty()) return;
            paint.setShader(null);
            paint.setPathEffect(null);
            for (UserBuildings.Mine m : UserBuildings.all()) {
                double[][] o = UserBuildings.outline(m);
                Path path = new Path();
                for (int i = 0; i < 4; i++) {
                    Point q = pr.toPixels(new GeoPoint(o[i][0], o[i][1]));
                    if (q == null) return;
                    if (i == 0) path.moveTo(q.x, q.y); else path.lineTo(q.x, q.y);
                }
                path.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Ui.withAlpha(p.amber, 70));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setColor(p.amber);
                c.drawPath(path, paint);
            }
        }

        /** The day's paths round you, and the sun and moon where the cursor is on the Day tab. */
        private void drawDay(Canvas c, Point a, float k, long ms) {
            if (sunHits == null) return;
            float rSun = 104 * k, rMoon = 78 * k;
            double lat = you.getLatitude(), lon = you.getLongitude();
            if (flow.moon) {        // only the body the tool is about
                trailOf(c, a, moonAz, moonAlt, rSun, p.violet, k);
                body(c, a, Astro.moon(ms, lat, lon), rSun, p.violet, (float) Astro.moonLit(ms), true, k);
            } else {
                trailOf(c, a, sunAz, sunAlt, rSun, p.amber, k);
                body(c, a, Astro.sun(ms, lat, lon), rSun, p.amber, 1f, false, k);
            }
        }

        private void trailOf(Canvas c, Point a, double[] az, double[] alt, float r, int color, float k) {
            trail.reset();
            double turn = map.bearing();            // the 3D map can be turned: north is not always up
            boolean pen = false;
            for (int i = 0; i < az.length; i++) {
                if (alt[i] < 0) { pen = false; continue; }
                float x = a.x + (float) Math.sin(Math.toRadians(az[i] - turn)) * r;
                float y = a.y - (float) Math.cos(Math.toRadians(az[i] - turn)) * r;
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
            float x = a.x + (float) Math.sin(Math.toRadians(pos[0] - map.bearing())) * r;
            float y = a.y - (float) Math.cos(Math.toRadians(pos[0] - map.bearing())) * r;
            boolean up = pos[1] >= 0;
            dayX = x;
            dayY = y;
            if (drag == 3) label(c, Fmt.time(MainActivity.this, dragMs, tz), x, y - 24 * k, k);   // the time, while dragging
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
        private void drawYou(Canvas c, Point a, float k, boolean ghost) {
            float u = 2f * k;
            if (ghost) {                               // a spot to stand, not yet chosen
                Ui.drawPinHalo(c, a.x, a.y, u, youColor(), 1f);
                Ui.drawPin(c, a.x, a.y, u, 0f, 0.85f);
                label(c, "Stand here", a.x, a.y - Ui.PIN_HEIGHT * u - 5 * k, k);
                return;
            }
            float back = dropT - 1f;
            float eased = 1f + 2.7f * back * back * back + 1.7f * back * back;   // overshoots a little, then settles
            float rise = (eased - 1f) * 26f * u;
            if (drag == 1) rise = -6 * k;
            float fade = Math.min(1f, dropT * 3f);
            Ui.drawPinHalo(c, a.x, a.y, u, youColor(), drag == 1 ? 1.15f : Math.min(1f, dropT * 2f));
            Ui.drawPin(c, a.x, a.y, u, rise, fade);
            label(c, "You", a.x, a.y + rise - Ui.PIN_HEIGHT * u - 5 * k, k);
        }

        /** The subject's mark: blue in Classic, ink (the printed map's pin) in the Braun designs. */
        private int subjectColor() { return p.design == Ui.CLASSIC ? Ui.ROUTE_BLUE : p.text; }
        /** The halo under You: green in Classic, blue (You) in the Braun designs. */
        private int youColor() { return p.design == Ui.CLASSIC ? Ui.ROUTE_GREEN : p.you; }

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
            // Kept inside the map: the sun's label at the map's edge was cut in half (3.9)
            if (map != null && map.view().getWidth() > 0) x = Math.max(w / 2 + 4 * k, Math.min(map.view().getWidth() - w / 2 - 4 * k, x));
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
