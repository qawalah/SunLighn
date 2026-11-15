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
import android.widget.SeekBar;
import android.widget.TextView;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
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
    static final String MAP_LINE = "map"; // reminder id of a line that is not saved

    private SharedPreferences prefs;
    private Ui.Palette p;
    private FrameLayout root;
    private MapView map;
    private LineOverlay lineOverlay;
    private android.animation.ValueAnimator dropAnim;
    private float dropT = 1f;                   // 0 = pin high above the point, 1 = on it
    private ScaleBarOverlay scaleBar;
    private TextView credit;
    private int style;                          // index into styles
    private LinearLayout topBar, sideKeys;
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
    private String mapLineId = MAP_LINE;        // the saved line's id while the map shows one, so reminders match
    private int armed;                          // which point a tap places next: 0 = Subject, 1 = You
    private GeoPoint held;                      // where the map was pressed and held, waiting for "Subject" or "You"

    private Reminders.Item pendingReminder;     // waits for the notification permission
    private Runnable pendingChanged;
    private Trip.Line pendingLine;

    private int generation;
    private Astro.Found foundSun, foundMoon;
    private boolean working;
    private TimeZone tz = TimeZone.getDefault(); // the place's time zone

    private int tab;                            // 0 = Times, 1 = Day
    private long searchFrom;                    // look for moments from this day on; 0 = from now
    private int dayOffset;                      // days from today shown on the Day tab
    private long cursorMs = System.currentTimeMillis();
    private long shotMs;                         // the moment of Your shot
    private String savedShotId;                  // Your shot as saved (its id in Saved), or null
    private GeoPoint asideYou;                   // where You was before Where to stand / Build the shot took it off
    private boolean retimeShot;                  // You moved in the viewfinder: Your shot takes the new soonest moment
    private ComposeScreen keptCompose;           // Build it, kept as it was left while Your shot shows
    private String keptComposeKey;
    private boolean pendingSetYou;
    // Where to stand: the light you want, the lights that come, and which one is picked.
    private int planKind;                       // 0 evening sun, 1 morning sun, 2 moonrise, 3 moonset
    private int planDist = 300;                 // metres from the subject
    private int planLight;                      // 0 setting, 1 rising (the body is the tool's)
    private int standPlace = 2;                 // where the body sits on the subject: 0 horizon, 1 middle, 2 on top
    private int moonFull = 1;                   // 0 any, 1 half or more, 2 nearly full
    private List<Astro.Hit> planSpots;
    private List<int[]> planGroups;            // {first, last} into planSpots: a run of days that share one spot
    private int[] planGround;                   // per run: what is on the ground at its spot (Ground.*), once known
    private int[] planGroupDist;                // per run: metres to stand at when planDist is no place to stand; 0 = planDist
    private boolean drone;                      // a drone can be anywhere: nothing is ruled out
    private int groundGen;
    private Astro.Near nearSun, nearMoon;       // the closest each comes to a line it never crosses
    private int planPick;
    private boolean planWorking;
    private TextView planSub;
    private final List<TextView> planDetails = new ArrayList<>();

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
    private final Runnable hideSnack = () -> { if (snack != null) snack.setVisibility(View.GONE); held = null; if (map != null) map.invalidate(); };

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

        Trip.migrate(this);                       // 2.1 lines and planned shoots become shots, once
        buildMap();
        buildChrome();
        loadState();
        refresh();
        // The app opens on Home; Continue takes the user back. A restart of the screen itself (dark mode switching
        // at sunset, a theme change) goes straight back to the step the user was on.
        if (savedInstanceState != null && savedInstanceState.getBoolean("in_tool")) continueTool();
        else showHome();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            applyInsets();
            return insets;
        });

        if (savedInstanceState != null && savedInstanceState.getBoolean("settings")) showSettings();
        else if (!prefs.getBoolean("tutorial_done", false)) showTutorial();
        Reveal.resume(root);                      // a theme or accent change: the new colours open from the finger
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
        FrameLayout.LayoutParams cr = (FrameLayout.LayoutParams) credit.getLayoutParams();
        if (cr.topMargin != below + Ui.dp(this, 10)) {
            cr.topMargin = below + Ui.dp(this, 10);
            credit.setLayoutParams(cr);
        }
        scaleBar.setScaleBarOffset(Ui.dp(this, 16), below + Ui.dp(this, 44));
        if (home != null) home.setInsets(insetTop, insetBottom);
        map.invalidate();
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
                tapMap(pt);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint pt) {
                onMapHold(pt);
                return true;
            }
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
        Point t = map.getProjection().toPixels(pt, null);
        int st = flow.current();
        if (st == Flow.YOU) {
            place(1, pt);
        } else if (st == Flow.SUBJECT) {
            place(0, pt);
        } else if (st == Flow.SPOTS) {
            int spot = lineOverlay.spotAt(t);            // one of the spots to stand: pick it
            if (spot >= 0) pickSpot(spot);
            else say("Tap one of the green spots, or a row below.");
        } else if (you != null && subject != null) {
            if (lineOverlay.onSunBadge(t)) {             // the sun at the end of the line: tap to send it to the other end
                setMode(1 - mode);
                return;
            }
            say("Press and hold the map to move a point, or drag one.");
        }
    }

    /** Press and hold: place the missing point, or, with a line drawn, ask which point moves here. */
    private void onMapHold(GeoPoint pt) {
        if (covered()) return;
        map.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (you != null && subject != null && flow.current() != Flow.YOU && flow.current() != Flow.SUBJECT) {
            held = pt;
            map.invalidate();
            say("Move which point here?", "Subject", () -> place(0, pt), "You", () -> place(1, pt));
            return;
        }
        tapMap(pt);
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
        layersKey.setOnClickListener(v -> {
            setStyle((style + 1) % styles.length);
            say("Map look: " + styles[style].name);
        });
        sideKeys.addView(layersKey);
        Ui.IconButton locate = new Ui.IconButton(this, p, Ui.Icon.LOCATE, "Go to my location");
        locate.setOnClickListener(v -> locate(true));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = Ui.dp(this, 8);
        sideKeys.addView(locate, ll);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.setMarginEnd(Ui.dp(this, 12));
        root.addView(sideKeys, sp);

        // Map credit, required by the map data licences. Plain small text: it is not a key.
        credit = Ui.text(this, styles[style].credit, 10.5f, 500, p.muted);
        credit.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        credit.setBackground(Ui.shape(this, Ui.withAlpha(p.bg, 190), 0, 6, false));
        FrameLayout.LayoutParams crp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        crp.setMarginStart(Ui.dp(this, 12));
        root.addView(credit, crp);

        sheet = new Sheet(this, p);
        sheet.setListener(this::placeSnack);
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
        working = false;
        planSpots = null;
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
                    () -> { if (!isFinishing() && home == null && you != null && subject != null) buildSheet(); });
        } else if (you == null && subject != null) computePlan();
        buildSheet();
    }

    private void buildSheet() {
        sheet.head.removeAllViews();
        sheet.body.removeAllViews();
        body = null;
        planSub = null;
        planDetails.clear();
        sunRead = moonRead = null;
        renderStep();
        sheet.headChanged();
        map.invalidate();
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
        planSub = s;
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
            m.getMenu().add(0, 2, 1, "Clear");
            m.setOnMenuItemClickListener(i -> {
                if (i.getItemId() == 1) { if (flow.current() == Flow.SHOT) saveShot(true); else saveLine(); }
                else clearLine();
                return true;
            });
            m.show();
        });
        return more;
    }

    // ---- Where to stand, starting from the subject ----


    private String planAsked;   // what the spots were found for: the same again keeps the row picked

    private void computePlan() {
        planSpots = null;
        planWorking = true;
        final int g = generation;
        final double lat = subject.getLatitude(), lon = subject.getLongitude();
        planKind = flow.moon ? (planLight == 0 ? 3 : 2) : (planLight == 0 ? 0 : 1);
        // Back from Your shot finds the same spots again: keep the day that was picked (Ali, 2026-11-15)
        String asked = lat + "," + lon + "," + planKind + "," + planDist + "," + standPlace + "," + heightNow() + ","
                + moonFull + "," + mode + "," + drone + "," + searchFrom;
        if (!asked.equals(planAsked)) planPick = 0;
        planAsked = asked;
        final boolean moon = flow.moon, setting = planLight == 0;
        final long from = searchFrom > 0 ? searchFrom : System.currentTimeMillis();
        // Level ground first (your eye above the subject's foot); then the real ground under each spot, below.
        final int place = standPlace, dist = planDist;
        final double height = heightNow();
        final double alt = FrameMath.targetAlt(place, height, dist, -FrameMath.EYE);
        final double minLit = !moon ? 0 : MOON_FULL[moonFull];
        new Thread(() -> {
            List<Astro.Hit> level = new ArrayList<>();
            for (Astro.Hit h : Astro.upcomingPasses(moon, lat, lon, alt, setting, from, 45)) if (h.lit >= minLit) level.add(h);
            List<Astro.Hit> hits = onTheGround(level, moon, setting, lat, lon, dist, place, height, alt, minLit);
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                planSpots = hits;
                planWorking = false;
                if (you == null && subject != null) {
                    groupPlan();
                    buildSheet();
                    fitPlan();
                    checkGround();
                }
            });
        }).start();
    }

    /**
     * The ground under each spot (3.2): asks for the heights of the subject and of every spot in one request, then
     * works each day out again with the real difference of level. A spot lower than the subject sees it taller, so
     * the sun must be higher to sit on its top, which is a little earlier at sunset. A day the body never gets that
     * high is dropped. Without a network the level answers stay. Runs on the background thread.
     */
    private List<Astro.Hit> onTheGround(List<Astro.Hit> hits, boolean moon, boolean setting, double lat, double lon,
                                        int dist, int place, double height, double levelAlt, double minLit) {
        int n = hits.size();
        if (n == 0) return hits;
        double[] la = new double[n + 1], lo = new double[n + 1];
        for (int i = 0; i < n; i++) {
            GeoPoint q = spotAt(hits.get(i), dist);
            la[i] = q.getLatitude();
            lo[i] = q.getLongitude();
        }
        la[n] = lat;
        lo[n] = lon;
        Heights.fetchNow(this, la, lo);
        if (!Heights.known(this, lat, lon)) return hits;
        List<Astro.Hit> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Astro.Hit h = hits.get(i);
            double a = FrameMath.targetAlt(place, height, dist, Heights.rise(this, la[i], lo[i], lat, lon));
            if (Math.abs(a - levelAlt) > 0.02) {
                long t = Astro.bodyPasses(moon, lat, lon, a, setting, h.timeMs - 6 * 3600000L);
                if (t < 0 || Math.abs(t - h.timeMs) > 8 * 3600000L) continue;   // not that high on this day
                h = new Astro.Hit(t, a, moon ? Astro.moonLit(t) : 1);
                if (h.lit < minLit) continue;
            }
            out.add(h);
        }
        return out;
    }

    /** "18 m lower than the subject's ground", or "" when the grounds are not known or nearly level. */
    private String groundWords(double youLat, double youLon) {
        double d = Heights.difference(this, youLat, youLon, subject.getLatitude(), subject.getLongitude());
        if (Double.isNaN(d) || Math.abs(d) < 3) return "";
        return Fmt.height(Math.abs(d)) + (d > 0 ? " lower" : " higher") + " than the subject's ground";
    }

    private static final int MAX_GROUPS = 8;
    private static final double[] MOON_FULL = {0, 0.5, 0.9};
    private static final String[] PLACES = {"On the horizon", "Behind the middle", "Just on top"};

    /** The subject's height: typed, or the usual height of its kind. */
    private double heightNow() {
        return subjectHeight > 0 ? subjectHeight : FrameMath.KIND_HEIGHTS[subjectKind];
    }

    /**
     * Days that follow each other and share one spot (within a degree) become one row with a range of dates:
     * the sun sets in nearly the same place for a week, so seven rows saying the same thing say nothing.
     */
    private void groupPlan() {
        planGroups = new ArrayList<>();
        planGround = null;
        planGroupDist = null;
        if (planSpots == null) return;
        double first = 0;
        for (int i = 0; i < planSpots.size(); i++) {
            double b = bodyAz(planSpots.get(i));
            if (planGroups.isEmpty() || Math.abs(FrameMath.diff(b, first)) > 1.0) {
                if (planGroups.size() == MAX_GROUPS) break;
                planGroups.add(new int[]{i, i});
                first = b;
            } else {
                planGroups.get(planGroups.size() - 1)[1] = i;
            }
        }
        planPick = Math.min(planPick, Math.max(0, planGroups.size() - 1));
    }

    private double bodyAz(Astro.Hit h) {
        double lat = subject.getLatitude(), lon = subject.getLongitude();
        return (planKind >= 2 ? Astro.moon(h.timeMs, lat, lon) : Astro.sun(h.timeMs, lat, lon))[0];
    }

    /** The first day of a run. */
    private Astro.Hit groupHit(int g) {
        return planSpots.get(planGroups.get(g)[0]);
    }

    private Astro.Hit groupLast(int g) {
        return planSpots.get(planGroups.get(g)[1]);
    }

    /** How far from the subject a run's spot is: the distance asked for, or the nearest land to it. */
    private int groupDist(int g) {
        return planGroupDist != null && g < planGroupDist.length && planGroupDist[g] > 0 ? planGroupDist[g] : planDist;
    }

    /** On foot, a spot on water or inside a building with no land near it on the same line is no place to stand. */
    private boolean blocked(int g) {
        return !drone && planGround != null && g < planGround.length
                && (planGround[g] == Ground.WATER || planGround[g] == Ground.BUILDING) && planGroupDist[g] == 0;
    }

    private GeoPoint spotFor(int g) {
        return spotAt(groupHit(g), groupDist(g));
    }

    /** Where to stand for one of the lights: metres from the subject, on the side that lines the body up. */
    private GeoPoint spotAt(Astro.Hit h, double metres) {
        double[] at = Astro.destination(subject.getLatitude(), subject.getLongitude(), standBearing(bodyAz(h)), metres);
        return new GeoPoint(at[0], at[1]);
    }

    /**
     * Looks at the ground under every suggested spot (off the main thread, from the map's own picture). A spot on
     * water or inside a building is moved along its line to the nearest land, nearer or farther; if there is none,
     * the row says so. A drone skips all of this.
     */
    private void checkGround() {
        final int gen = ++groundGen;
        planGround = null;
        planGroupDist = null;
        if (drone || !planning()) return;
        final int n = planGroups.size(), dist = planDist;
        final Astro.Hit[] hits = new Astro.Hit[n];
        for (int i = 0; i < n; i++) hits[i] = groupHit(i);
        final GeoPoint sub = subject;
        final String agent = getPackageName();
        new Thread(() -> {
            int[] kinds = new int[n], dists = new int[n];
            double[] tries = {0.8, 1.25, 0.65, 1.5, 0.5, 2, 3};
            for (int i = 0; i < n; i++) {
                if (gen != groundGen) return;
                GeoPoint q = spotAt(hits[i], dist);
                kinds[i] = Ground.at(q.getLatitude(), q.getLongitude(), agent);
                if (kinds[i] != Ground.WATER && kinds[i] != Ground.BUILDING) continue;
                for (double f : tries) {
                    int d = (int) Math.max(50, Math.min(5000, Math.round(dist * f / 10) * 10));
                    GeoPoint alt = spotAt(hits[i], d);
                    if (Ground.at(alt.getLatitude(), alt.getLongitude(), agent) == Ground.LAND) {
                        dists[i] = d;
                        break;
                    }
                }
            }
            runOnUiThread(() -> {
                if (gen != groundGen || isFinishing() || subject != sub || !planning() || planGroups.size() != n) return;
                planGround = kinds;
                planGroupDist = dists;
                buildSheet();
            });
        }).start();
    }

    /** The compass direction from the subject to where you stand. */
    private double standBearing(double bodyAz) {
        return mode == 0 ? Astro.norm360(bodyAz + 180) : bodyAz;   // behind the subject: the far side from the sun
    }

    private boolean planning() {
        return you == null && subject != null && planSpots != null && planGroups != null && !planGroups.isEmpty();
    }

    private String planLine(int g) {
        Astro.Hit h = groupHit(g);
        String why = planGround == null || g >= planGround.length || drone ? ""
                : planGround[g] == Ground.WATER ? "on water" : planGround[g] == Ground.BUILDING ? "inside a building" : "";
        GeoPoint spot = spotFor(g);
        String ground = drone ? "" : groundWords(spot.getLatitude(), spot.getLongitude());
        String out = (drone ? "Fly " : "Stand ") + Fmt.distance(groupDist(g)) + " " + Astro.compass(standBearing(bodyAz(h)))
                + " of the subject" + (ground.isEmpty() ? "" : ", " + ground)
                + (planKind >= 2 ? " · " + Math.round(h.lit * 100) + "% lit" : "");
        if (!why.isEmpty()) {
            out += blocked(g) ? ". That is " + why + ", with no land near on this line"
                    : " (" + Fmt.distance(planDist) + " is " + why + ")";
        }
        return out + lowerSunWords(g);
    }

    /** "16:24 to 16:26 · Evening sun · stand 350 m ENE of the subject": the line under the dates in the head. */
    private String planHead(int g) {
        return Fmt.times(this, groupHit(g).timeMs, groupLast(g).timeMs, tz) + " · " + lightWords(groupHit(g)) + " · "
                + (blocked(g) ? "no place to stand" : Fmt.distance(groupDist(g)) + " "
                + Astro.compass(standBearing(bodyAz(groupHit(g)))) + " of the subject");
    }

    /** How high the body is then, and for the sun the light it gives: "Daylight, 10° up", "Golden hour, 3° up". */
    private String lightWords(Astro.Hit h) {
        String up = Math.round(h.alt) + "° up";
        return planKind >= 2 ? up : Light.name(Light.of(h.alt)) + ", " + up;
    }

    /** The sun still high (above golden hour) there: how far to stand for a low sun on the same part of the subject. */
    private String lowerSunWords(int g) {
        Astro.Hit h = groupHit(g);
        if (planKind >= 2 || h.alt <= Light.GOLDEN_TOP || blocked(g)) return "";
        double far = planDist * Math.tan(Math.toRadians(h.alt)) / Math.tan(Math.toRadians(4));   // the distance that height is for
        return ". For golden-hour light, stand farther: about " + Fmt.distance(far);
    }

    private void pickSpot(int i) {
        planPick = i;
        map.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        buildSheet();
        fitPlan();
    }

    /** Where to stand, step 2: how you want it. Every choice is a dropdown, a value or an either/or. */
    private void renderCriteria() {
        sheet.setPullable(true);
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        boolean moon = flow.moon;
        String b = moon ? "moon" : "sun";
        String who = subjectName == null ? "your subject" : subjectName;
        sheet.head.addView(Ui.text(this, "How do you want the " + b + "?", 20, 700, p.text));
        sheet.head.addView(Ui.text(this, "Say where it sits on " + who + " and how far away you stand. Then SunLighn "
                + "finds the spots and the days.", 14, 400, p.muted), top(4));
        sheet.head.addView(criteriaPicture(), new LinearLayout.LayoutParams(-1, Ui.dp(this, 120)) {{ topMargin = Ui.dp(MainActivity.this, 10); }});
        TextView find = Ui.button(this, p, "Find spots", true);
        find.setOnClickListener(v -> nextStep());
        sheet.head.addView(find, top(12));

        LinearLayout body = sheet.body;
        body.addView(Ui.dropdown(this, p, "Light", moon ? new String[]{"Moonset", "Moonrise"} : new String[]{"Sunset", "Sunrise"},
                planLight, i -> {
                    planLight = i;
                    prefs.edit().putInt("plan_light", i).apply();
                    replan();
                }), top(4));

        String[] kinds = new String[FrameMath.KINDS.length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = FrameMath.KINDS[i];   // the height is beside it, never two numbers
        body.addView(Ui.dropdown(this, p, "Subject", kinds, subjectKind, i -> {
            subjectKind = i;
            subjectHeight = 0;                  // the usual height of the new kind, until one is typed
            saveState();
            replan();
        }), top(10));
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        hr.addView(Ui.text(this, "Height in metres", 14, 500, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.EditText hEdit = new android.widget.EditText(this);
        hEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hEdit.setSingleLine(true);
        hEdit.setTextColor(p.text);
        hEdit.setHintTextColor(p.muted);
        hEdit.setTypeface(Ui.font(this, 600));
        hEdit.setMinWidth(Ui.dp(this, 90));
        hEdit.setGravity(Gravity.END);
        hEdit.setContentDescription("Subject height in metres");
        hEdit.setHint(trimNum(FrameMath.KIND_HEIGHTS[subjectKind]) + " (usual)");
        if (subjectHeight > 0) hEdit.setText(trimNum(subjectHeight));
        hr.addView(hEdit);
        body.addView(hr, top(6));

        body.addView(Ui.dropdown(this, p, "Where the " + b + " sits", PLACES, standPlace, i -> {
            standPlace = i;
            prefs.edit().putInt("stand_place", i).apply();
            replan();
        }), top(10));

        // How far from the subject: a slider, 50 m to 5 km, fine near and coarse far.
        LinearLayout dr = new LinearLayout(this);
        dr.setGravity(Gravity.CENTER_VERTICAL);
        dr.addView(Ui.text(this, "Distance from the subject", 14, 500, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView dv = Ui.text(this, Fmt.distance(planDist), 14, 700, p.text);
        dr.addView(dv);
        body.addView(dr, top(16));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress((int) Math.round(Math.log(planDist / 50.0) / Math.log(100) * 100));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(p.accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(p.accent));
        bar.setContentDescription("Distance from the subject");
        bar.setMinimumHeight(Ui.dp(this, 48));
        TextView lens = Ui.text(this, lensWords(), 13, 400, p.muted);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                if (!fromUser) return;
                double m = 50 * Math.pow(100, v / 100.0);
                planDist = (int) (m < 1000 ? Math.round(m / 10) * 10 : Math.round(m / 100) * 100);
                groundGen++;
                planGround = null;
                planGroupDist = null;
                dv.setText(Fmt.distance(planDist));
                lens.setText(lensWords());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt("plan_dist", planDist).apply();
                generation++;
                computePlan();           // how high the sun must be follows the distance
            }
        });
        body.addView(bar, top(2));
        body.addView(lens, top(0));
        hEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence t, int a, int c, int d) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                double h = 0;
                try {
                    h = Double.parseDouble(e.toString().trim());
                } catch (NumberFormatException ignored) {
                }
                subjectHeight = Math.max(0, Math.min(9000, h));
                saveState();
                lens.setText(lensWords());
                map.removeCallbacks(replanLater);
                map.postDelayed(replanLater, 700);   // work it out again once typing stops
            }
        });

        if (moon) body.addView(Ui.dropdown(this, p, "How full", new String[]{"Any", "Half or more", "Nearly full"},
                moonFull, i -> {
                    moonFull = i;
                    prefs.edit().putInt("moon_full", i).apply();
                    replan();
                }), top(10));
        // On foot, spots are kept to land you can stand on; a drone can be anywhere.
        body.addView(Ui.segmented(this, p, new String[]{"On foot", "Drone"}, drone ? 1 : 0, i -> {
            drone = i == 1;
            prefs.edit().putBoolean("drone", drone).apply();
            buildSheet();
        }), top(14));
        body.addView(Ui.segmented(this, p, new String[]{"Behind subject", "Behind you"}, mode, this::setMode), top(8));
        body.addView(Ui.text(this, "Behind subject: the " + b + " stands behind " + who + " as you look at it. Behind "
                + "you: it lights " + who + " from your side.", 12.5f, 400, p.muted), top(8));
    }

    private final Runnable replanLater = () -> { if (flow.current() == Flow.CRITERIA && subject != null) replan(); };

    private void replan() {
        generation++;
        computePlan();
        buildSheet();
    }

    /** The lens that makes the subject a third of the picture's height, from here. */
    private String lensWords() {
        double f = FrameMath.lensForThird(heightNow(), planDist, portrait);
        return "About " + Math.round(f) + " mm to fill a third of the picture"
                + (f >= FrameMath.FOCAL_MAX ? " (or come closer)" : f <= FrameMath.FOCAL_MIN ? " (or stand further back)" : "") + ".";
    }

    private static String trimNum(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Where to stand, step 3: the days and the spot for each; Stand here goes to Your shot. */
    private void renderSpots() {
        sheet.setPullable(true);
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        boolean moon = flow.moon, any = planning();
        int pg = any ? Math.min(planPick, planGroups.size() - 1) : -1;
        Astro.Hit pick = any ? groupHit(pg) : null;
        String headline = pick != null ? Fmt.days(this, pick.timeMs, groupLast(pg).timeMs, tz) : planWorking || planSpots == null
                ? "Working it out…" : "Not in the coming weeks";
        String sub = pick != null ? planHead(pg) : planWorking || planSpots == null
                ? (subjectName == null ? "Where to stand" : "Where to stand for " + subjectName)
                : "The " + (moon ? "moon" : "sun") + " is not there in the next 45 days. Go back and pick another "
                + "Where the " + (moon ? "moon" : "sun") + " sits, a longer distance, or Drone.";
        sheet.head.addView(headRow(badge(moon, true, pick == null ? 1 : pick.lit, pick == null ? 0 : pick.timeMs),
                headline, sub, moreKey()));
        TextView stand = Ui.button(this, p, drone ? "Fly here" : "Stand here", true);
        stand.setEnabled(any);
        stand.setAlpha(any ? 1f : 0.5f);
        stand.setOnClickListener(v -> standHere());
        sheet.head.addView(stand, top(12));
        sheet.head.addView(Ui.text(this, "Puts you on this spot and shows your shot.", 12.5f, 400, p.muted), top(4));

        LinearLayout b = sheet.body;
        if (searchFrom > 0) b.addView(fromRow(), top(8));
        if (any) {
            for (int i = 0; i < planGroups.size(); i++) b.addView(planRow(i), top(8));
            b.addView(Ui.text(this, "Days that share a spot are one row. Each spot is on the map: tap one there or "
                    + "here. " + (drone ? "" : "Spots on water or inside a building are moved to the nearest land on "
                    + "the same line. ") + zoneNote(), 12.5f, 400, p.muted), top(12));
        }
    }

    /** The slider moved: the same rows, new words. */
    private void updatePlanWords() {
        if (!planning()) return;
        if (planSub != null) planSub.setText(planHead(Math.min(planPick, planGroups.size() - 1)));
        for (int i = 0; i < planDetails.size() && i < planGroups.size(); i++) planDetails.get(i).setText(planLine(i));
    }

    /** One light you could shoot in: the sun or moon low in the sky, when, and where you would stand. */
    private View planRow(int i) {
        Astro.Hit h = groupHit(i), last = groupLast(i);
        boolean moon = planKind >= 2, picked = i == planPick;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.setBackground(Ui.shape(this, picked ? Ui.withAlpha(p.accent, 30) : p.panel, picked ? p.accent : p.line, 14, false));
        row.setClickable(true);
        Ui.pressable(row);
        row.addView(badge(moon, true, h.lit, h.timeMs));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, Fmt.days(this, h.timeMs, last.timeMs, tz), 16, 700, p.text));
        col.addView(Ui.text(this, Fmt.times(this, h.timeMs, last.timeMs, tz) + " · " + lightWords(h), 13.5f, 600, p.text), top(2));
        TextView d = Ui.text(this, planLine(i), 13, 400, blocked(i) ? p.bad : p.muted);
        planDetails.add(d);
        col.addView(d, top(3));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.setMarginStart(Ui.dp(this, 12));
        row.addView(col, cl);
        row.setOnClickListener(v -> pickSpot(i));
        return row;
    }

    /** Shows the subject and the picked spot together, in the part of the map the sheet does not cover. */
    private void fitPlan() {
        if (!planning()) return;
        fit(spotFor(Math.min(planPick, planGroups.size() - 1)), subject);
    }

    private void fit(GeoPoint a, GeoPoint b) {
        // zoomToBoundingBox never returns on a map that has no size yet, so wait for the first layout.
        if (!map.isLayoutOccurred() || map.getWidth() == 0 || map.getHeight() == 0) {
            map.addOnFirstLayoutListener((v, l, t, r, bt) -> fit(a, b));
            return;
        }
        double minLat = Math.min(a.getLatitude(), b.getLatitude()), maxLat = Math.max(a.getLatitude(), b.getLatitude());
        double minLon = Math.min(a.getLongitude(), b.getLongitude()), maxLon = Math.max(a.getLongitude(), b.getLongitude());
        double span = Math.max(Math.max(maxLat - minLat, (maxLon - minLon) * Math.cos(Math.toRadians(minLat))), 0.0008);
        // Extra room to the south pushes the two points up, clear of the sheet; a little to the north clears the bar.
        BoundingBox box = new BoundingBox(maxLat + span * 0.5, maxLon + span * 0.25, minLat - span * 1.7, minLon - span * 0.25);
        map.zoomToBoundingBox(box, true, Ui.dp(this, 40));
    }

    /** Stand at the picked spot: You moves there and the Frame opens at that moment. */
    private void standHere() {
        if (!planning()) return;
        int pg = Math.min(planPick, planGroups.size() - 1);
        if (blocked(pg)) {
            say("That spot is no place to stand. Pick another row, change the distance, or switch to Drone.");
            return;
        }
        Astro.Hit h = groupHit(pg);
        backupYou = backupSubject = null;
        you = spotFor(pg);
        mapLineId = MAP_LINE;
        armed = 0;
        saveState();
        tab = 0;
        goShot(h.timeMs);
        dropPin();
        fit(you, subject);
    }

    /** From a drawn line: keep the subject and go to the Where to stand tool, at its criteria. */
    private void whereToStand() {
        flow.start(Flow.STAND);
        flow.go(Flow.CRITERIA);
        enterStep();
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
        working = true;
        new Thread(() -> {
            Astro.Found s = Astro.search(false, lat, lon, b, from);
            Astro.Near ns = s.next == null ? Astro.nearest(false, lat, lon, b, from, 366) : null;
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundSun = s;
                nearSun = ns;
                if (you != null && subject != null) buildSheet();
            });
            Astro.Found m = Astro.search(true, lat, lon, b, from);
            Astro.Near nm = m.next == null ? Astro.nearest(true, lat, lon, b, from, 60) : null;
            runOnUiThread(() -> {
                if (g != generation || isFinishing()) return;
                foundMoon = m;
                nearMoon = nm;
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

    private static long next(Astro.Found f) {
        return f == null || f.next == null ? -1 : f.next.timeMs;
    }

    private String where() {
        return mode == 0 ? "behind " + (subjectName == null ? "subject" : subjectName) : "behind you";
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
        Astro.Hit first = found != null ? found.next : null;
        long now = System.currentTimeMillis();
        String headline, sub;
        View badge;
        String bodyName = firstMoon ? "moon" : "sun";
        if (first != null) {
            headline = Fmt.when(this, first.timeMs, tz);
            String until = Fmt.until(first.timeMs, now);
            sub = (firstMoon ? "Moon " : "Sun ") + where() + " · " + up(first) + (until.isEmpty() ? "" : " · " + until);
            badge = badge(firstMoon, Astro.isLow(first, firstMoon), first.lit, first.timeMs);
        } else if (found == null) {
            headline = "Working it out…";
            sub = lineWords;
            badge = badge(firstMoon, false, 1, 0);
        } else {
            headline = "Never lines up here";
            sub = "The " + bodyName + " never crosses this line. The closest it comes is in the list below.";
            badge = badge(firstMoon, false, 1, 0);
        }
        sheet.head.addView(headRow(badge, headline, sub, moreKey()));
        Astro.Near near = firstMoon ? nearMoon : nearSun;
        if (first == null && found != null && near != null) {
            // "Never" comes with the way on as the main key, not a greyed "Use the soonest" (audit 3.2.2)
            TextView fix = Ui.button(this, p, "Line it up", true);
            fix.setContentDescription("Line it up: move You so the " + bodyName + " is on the line at its closest");
            fix.setOnClickListener(v -> lineUp(near, bodyName));
            sheet.head.addView(fix, top(12));
            sheet.head.addView(Ui.text(this, "Moves you round the subject, the same distance away, so the " + bodyName
                    + " lines up with it.", 12.5f, 400, p.muted), top(4));
        } else {
            TextView use = Ui.button(this, p, "Use the soonest", true);
            use.setContentDescription("Use the soonest: your shot at that moment");
            use.setEnabled(first != null);
            use.setAlpha(first != null ? 1f : 0.5f);
            final long at = first != null ? first.timeMs : now;
            use.setOnClickListener(v -> goShot(at));
            sheet.head.addView(use, top(12));
        }

        LinearLayout sb = sheet.body;
        sb.addView(Ui.tabs(this, p, new String[]{"Times", "Day"}, tab, t -> {
            tab = t;
            buildSheet();
        }), wrapTop(0));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sb.addView(body, top(0));
        if (tab == 0) renderTimes(lineWords);
        else renderDay();
    }

    private void renderTimes(String lineWords) {
        body.addView(Ui.segmented(this, p, new String[]{"Behind subject", "Behind you"}, mode, this::setMode), top(12));
        body.addView(fromRow(), top(10));
        if (flow.moon) addSection("Moon", foundMoon, true);
        else addSection("Sun", foundSun, false);

        // Another light: keep the subject, and be told where to stand.
        LinearLayout other = new LinearLayout(this);
        other.setGravity(Gravity.CENTER_VERTICAL);
        other.addView(Ui.text(this, "Want sunrise, sunset or the moon behind it instead? Keep the subject and move.",
                13, 400, p.muted), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView stand = Ui.smallButton(this, p, "Where to stand");
        stand.setOnClickListener(v -> whereToStand());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMarginStart(Ui.dp(this, 12));
        other.addView(stand, sl);
        body.addView(other, top(22));

        body.addView(Ui.text(this, "Soonest = the next time it lines up. Low sun or low moon = it lines up while low in "
                + "the sky (8° or less; the moon also at least 60% lit), which can be months away. The bell plans a shoot.",
                12.5f, 400, p.muted), top(16));
        body.addView(Ui.text(this, "Line: " + lineWords + (subjectHeight > 0 ? " · subject " + Fmt.height(subjectHeight) : "")
                + ". " + zoneNote() + " To move a point, drag it or press and hold the map.", 12.5f, 400, p.muted), top(6));
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
        if (f.next == null) {
            addNever(name, moon ? nearMoon : nearSun, moon);
            return;
        }
        String lowName = moon ? "Low moon" : "Low sun";
        addHit(f.next, "Soonest", moon, Astro.isLow(f.next, moon), lowName);
        if (f.low != null && f.low.timeMs != f.next.timeMs) addHit(f.low, lowName, moon, true, lowName);
    }

    /**
     * One answer. The time leads; under it what kind of answer it is (Next, Low) and the light it comes in, as
     * small labels, then the details. Tap the row to see it in the Frame; the bell sets a reminder.
     */
    private void addHit(Astro.Hit h, String label, boolean moon, boolean low, String lowName) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10));
        row.setBackground(Ui.shape(this, p.panel, p.line, 14, false));
        row.addView(badge(moon, low, h.lit, h.timeMs));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, Fmt.when(this, h.timeMs, tz), 17, 700, p.text));
        LinearLayout tags = new LinearLayout(this);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        tags.addView(Ui.tag(this, low && !label.equals(lowName) ? label + " · " + lowName : label, low ? p.accent : p.muted));
        Light.Kind light = Light.of(Astro.sun(h.timeMs, you.getLatitude(), you.getLongitude())[1]);
        if (light != Light.Kind.DAY && (light != Light.Kind.NIGHT || moon)) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.setMarginStart(Ui.dp(this, 6));
            tags.addView(Ui.tag(this, Light.name(light), light == Light.Kind.GOLDEN ? p.amber : p.violet), tl);
        }
        col.addView(tags, wrapTop(5));
        String until = Fmt.until(h.timeMs, System.currentTimeMillis());
        String detail = up(h) + (moon ? " · " + Math.round(h.lit * 100) + "% lit" : "") + (until.isEmpty() ? "" : " · " + until);
        col.addView(Ui.text(this, detail, 13, 400, p.muted), top(5));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.setMarginStart(Ui.dp(this, 12));
        row.addView(col, cl);

        // No bell here: a row opens Your shot, where Remind me is (a reminder belongs to a saved shot).
        row.setClickable(true);
        Ui.pressable(row);
        row.setContentDescription((moon ? "Moon, " : "Sun, ") + label + ", " + Fmt.when(this, h.timeMs, tz) + ". Your shot at this moment");
        row.setOnClickListener(v -> goShot(h.timeMs));
        body.addView(row, top(8));
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
        col.addView(Ui.text(this, Fmt.when(this, near.timeMs, tz), 17, 700, p.text));
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
        double[] at = Astro.destination(subject.getLatitude(), subject.getLongitude(),
                mode == 0 ? Astro.norm360(near.az + 180) : near.az, d);
        you = new GeoPoint(at[0], at[1]);
        mapLineId = MAP_LINE;
        dropPin();
        saveState();
        refresh();
        fit(you, subject);
        say("You moved, so the " + body + " lines up. The soonest is at the top.", "Undo", () -> {
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
        body.addView(Ui.text(this, s, 13.5f, 400, p.muted), top(6));
    }

    // ---- the Day tab: where the sun and the moon go ----

    private void buildDay() {
        double lat = you.getLatitude(), lon = you.getLongitude(), bearing = lineBearing();
        dayStart = Fmt.midnight(cursorMs, tz);
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
        buildDay();
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
        body.addView(nav, top(12));

        DayChart chart = new DayChart(this, p);
        chart.setData(dayStart, dayEnd, sunAlt, moonAlt, sunHits, moonHits, Fmt.timeFormat(this, tz));
        chart.setBody(flow.moon);
        chart.setCursor(cursorMs);
        chart.setListener(ms -> {
            cursorMs = ms;
            updateReadouts();
            map.invalidate();
        });
        body.addView(chart, top(4));

        sunRead = Ui.text(this, "", 14, 400, p.text);
        moonRead = Ui.text(this, "", 14, 400, p.text);
        moonReadGlyph = new Ui.GlyphView(this, Ui.GlyphView.Kind.MOON, p.violet, 24);
        if (flow.moon) body.addView(readRow(moonReadGlyph, moonRead));
        else body.addView(readRow(new Ui.GlyphView(this, Ui.GlyphView.Kind.SUN, p.amber, 24), sunRead));
        body.addView(Ui.text(this, (flow.moon ? "Purple is the moon" : "Orange is the sun") + ": how high it is through "
                + "the day; the strip under it is the light. Drag along the chart to pick a time (the marker); the map "
                + "shows it at that time, and its path.", 12.5f, 400, p.muted), top(10));
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
                setReminder(item, pendingLine, changed); // the bell does its job once allowed, no second press
            } else {
                say("Without notifications SunLighn cannot remind you. "
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

    @Override public double[] searchNear() {
        org.osmdroid.api.IGeoPoint c = map.getMapCenter();
        return c == null ? null : new double[]{c.getLatitude(), c.getLongitude()};
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
            map.getController().setZoom(16.0);
            map.getController().setCenter(g);
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
        // Opened on Your shot: "Sun lined up" goes back to that moment, not to the soonest one (Ali, 2026-11-15)
        if (flow.current() == Flow.SHOT && ms > 0) { if (flow.moon) l.moonNext = ms; else l.sunNext = ms; }
        cursorMs = ms;
        frame = new FrameScreen(this, this, l, ms, focal, portrait);
        frame.setInsets(insetTop, insetBottom);
        root.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        hideSnack.run();
    }

    @Override public void closeFrame() {
        if (frame == null) return;
        cursorMs = frame.time();
        setShotFraming(frame.panDeg(), frame.framePitch());
        root.removeView(frame);
        frame = null;
        if (you != null && subject != null && tab == 1) buildSheet();
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

    @Override public void composed(double lat, double lon, long timeMs, double focalMm, boolean port, double height, int kind) {
        removeCompose();
        backupYou = backupSubject = null;
        you = new GeoPoint(lat, lon);
        subjectHeight = height;
        subjectKind = kind;
        mapLineId = MAP_LINE;
        armed = 0;
        tab = 0;
        frameLens(focalMm, port);
        try {                                      // the picture as framed in Build the shot
            String fp = prefs.getString("frame_pitch", "");
            setShotFraming(Double.parseDouble(prefs.getString("frame_pan", "0")), fp.isEmpty() ? Double.NaN : Double.parseDouble(fp));
        } catch (NumberFormatException e) {
            setShotFraming(0, Double.NaN);
        }
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
        if (savedShot() != null) say("Saved as " + savedShot().name);
        else saveShot(true);
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
            @Override public void onAccent(int accent) {
                if (accent == Ui.accentIndex(MainActivity.this)) return;
                prefs.edit().putInt("accent2", accent).apply();
                Reveal.keep(root, touchX, touchY);
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
        prefs.edit().putBoolean("tutorial_done", true).apply();
        // Location is asked when the first map opens (a tool), not on Home where nothing needs it yet (audit 3.2.2)
    }

    /**
     * Back closes the top thing first, then goes one step back in the tool, then to Home; only Home leaves the
     * app (all is saved as you go). A plain Activity has no AndroidX dispatcher; with targetSdk 35 and no
     * enableOnBackInvokedCallback in the manifest, onBackPressed is still what Back calls.
     */
    @Override
    public void onBackPressed() {
        if (PickSheet.closeTop(this)) return;
        if (tutorial != null) closeTutorial();
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
        flow.start(tool);
        tab = 0;
        enterStep();
        GeoPoint g = tool == Flow.LINE ? you : subject;
        if (tool == Flow.LINE && you != null && subject != null) fit(you, subject);
        else if (g != null) map.getController().setCenter(g);
        else if (!prefs.getBoolean("asked_location", false)) {
            prefs.edit().putBoolean("asked_location", true).apply();   // once: the first map opens where you are
            locate(true);
        }
    }

    @Override public void continueTool() {
        Flow last = Flow.decode(prefs.getString("flow", null));
        if (last.current() == Flow.SHOT && last.moon != flow.moon) last.back();   // the body changed on Home
        last.moon = flow.moon;
        flow = last;
        enterStep();
        if (you != null && subject != null) fit(you, subject);
        else if (subject != null) map.getController().setCenter(subject);
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

    /** Does this step have what it needs? (The points it works on.) */
    private boolean ready(int step) {
        switch (step) {
            case Flow.SUBJECT: return flow.tool != Flow.LINE || you != null;
            case Flow.WHEN: case Flow.SHOT: return you != null && subject != null;
            case Flow.CRITERIA: case Flow.SPOTS: case Flow.COMPOSE: return subject != null;
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
        // Where to stand and Build the shot exist to find where you stand: You is placed by their last step.
        if ((flow.tool == Flow.STAND || flow.tool == Flow.BUILD) && flow.current() != Flow.SHOT && you != null) {
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
        if (!ready(flow.steps(flow.tool).length > flow.step + 1 ? Flow.steps(flow.tool)[flow.step + 1] : -1)) return;
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

    private void goShot(long ms) {
        if (flow.tool != Flow.BUILD) setShotFraming(0, Double.NaN);   // the other tools give a centred picture
        asideYou = null;                           // You has a new spot now
        if (ms != shotMs) setSavedShot(null);
        shotMs = ms;
        cursorMs = ms;
        prefs.edit().putLong("shot_ms", ms).apply();
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

    /** How you want it: the subject to scale, the sun or moon where "Where the sun sits" puts it, at that distance. */
    private FrameView criteriaPicture() {
        FrameView v = stillPicture("How it will look");
        double height = heightNow(), alt = FrameMath.targetAlt(standPlace, height, planDist, -FrameMath.EYE);
        double f = FrameMath.lensForThird(height, planDist, false), aim = 180;
        boolean behind = mode == 1;                // behind you: the body is at your back, out of the picture
        v.setLens(f, false);
        v.setSubject(aim, planDist, height, subjectKind, aim, behind, -FrameMath.EYE);
        v.setCompose(flow.moon, false);
        double bodyAz = behind ? aim + 180 : aim;
        if (flow.moon) v.setBodies(aim + 180, -10, bodyAz, alt, 0.8, true);
        else v.setBodies(bodyAz, alt, 0, -90, 1, true);
        v.setAim(aim, FrameMath.aimPitch(FrameMath.fovV(f, false),
                Math.max(behind ? -1 : alt, FrameMath.topAngle(height, planDist, -FrameMath.EYE))), false);
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
        double body = mode == 1 ? -1 : flow.moon ? m[1] : s[1];
        double pitch = !Double.isNaN(shotPitch) ? shotPitch : FrameMath.aimPitch(FrameMath.fovV(focal, portrait),
                Math.max(body, FrameMath.topAngle(height, dist, rise)));
        v.setAim(az + shotPan, pitch, false);
        return v;
    }

    private void renderShot() {
        sheet.setPullable(false);
        sheet.head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), insetBottom + Ui.dp(this, 8));
        boolean moon = flow.moon;
        long ms = shotMs > 0 ? shotMs : System.currentTimeMillis();
        double[] pos = moon ? Astro.moon(ms, you.getLatitude(), you.getLongitude()) : Astro.sun(ms, you.getLatitude(), you.getLongitude());
        double lit = moon ? Astro.moonLit(ms) : 1;
        Astro.Hit h = new Astro.Hit(ms, pos[1], lit);
        String sub = (moon ? "Moon " : "Sun ") + where() + " · " + up(h) + (moon ? " · " + Math.round(lit * 100) + "% lit" : "");
        sheet.head.addView(headRow(badge(moon, Astro.isLow(h, moon), lit, ms), Fmt.when(this, ms, tz), sub, moreKey()));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 120));
        pl.topMargin = Ui.dp(this, 10);
        sheet.head.addView(shotPicture(ms), pl);      // see it, not only read it (3.3); a tap opens the viewfinder
        double back = Astro.bearing(subject.getLatitude(), subject.getLongitude(), you.getLatitude(), you.getLongitude());
        double m = Astro.distance(you.getLatitude(), you.getLongitude(), subject.getLatitude(), subject.getLongitude());
        String ground = groundWords(you.getLatitude(), you.getLongitude());
        boolean flying = flow.tool == Flow.STAND && drone;   // the same word as the row it came from
        sheet.head.addView(Ui.text(this, (flying ? "Fly " : "Stand ") + Fmt.distance(m) + " " + Astro.compass(back) + " of the subject"
                + (ground.isEmpty() ? "" : ", " + ground) + ". " + zoneNote(), 14, 400, p.muted), top(8));
        TextView open = Ui.button(this, p, "Open the viewfinder", true);
        open.setContentDescription("Viewfinder: see the shot as the camera will");
        open.setOnClickListener(v -> showFrame(ms));
        sheet.head.addView(open, top(12));

        // What comes next: keep it, be reminded, get there.
        Trip.Line saved = savedShot();
        boolean reminds = saved != null && Reminders.has(this, Reminders.key(saved.id, moon, ms));
        LinearLayout next = new LinearLayout(this);
        TextView save = Ui.smallButton(this, p, saved != null ? "Saved" : "Save");
        save.setContentDescription(saved != null ? "Saved as " + saved.name : "Save this shot");
        save.setOnClickListener(v -> { if (savedShot() == null) saveShot(true); });
        next.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean future = ms > System.currentTimeMillis();
        TextView remind = Ui.smallButton(this, p, reminds ? "Reminder set" : "Remind me");
        remind.setContentDescription(reminds ? "Reminder set. Press to take it off" : "Remind me 30 minutes before");
        remind.setEnabled(future);
        remind.setAlpha(future ? 1f : 0.5f);
        remind.setOnClickListener(v -> remind(ms, this::buildSheet));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -2, 1f);
        rl.setMarginStart(Ui.dp(this, 8));
        next.addView(remind, rl);
        TextView go = Ui.smallButton(this, p, "Directions");
        go.setContentDescription("Directions to your spot");
        go.setOnClickListener(v -> directions());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(0, -2, 1f);
        gl.setMarginStart(Ui.dp(this, 8));
        next.addView(go, gl);
        sheet.head.addView(next, top(8));
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
        Trip.Line have = savedShot();
        if (have != null) return have;
        Trip.Line onMap = mapLine();
        Trip.Line draft = new Trip.Line(onMap.id, subjectName, onMap.youLat, onMap.youLon, onMap.subLat, onMap.subLon, mode);
        draft.height = subjectHeight;
        draft.kind = subjectKind;
        draft.timeMs = shotMs;
        draft.moon = flow.moon;
        draft.named = false;
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
        map.post(() -> fit(you, subject));
        if (l.timeMs > 0) {
            flow.moon = l.moon;
            prefs.edit().putBoolean("moon", l.moon).apply();
            p.setMoon(l.moon);
            shotMs = l.timeMs;
            setSavedShot(l.id);
            goShot(l.timeMs);
            setShotFraming(l.panDeg, l.pitch);   // framed as it was saved
            fromSaved = true;      // Back goes back to Saved, where the shot was opened (audit 3.2.2)
        }
    }

    private boolean fromSaved;   // Your shot was opened from Saved

    private void renderStep() {
        switch (flow.current()) {
            case Flow.YOU: renderPlace(true); break;
            case Flow.SUBJECT: renderPlace(false); break;
            case Flow.WHEN: if (you != null && subject != null) renderLine(); break;
            case Flow.SHOT: if (you != null && subject != null) renderShot(); break;
            case Flow.CRITERIA: if (subject != null) renderCriteria(); break;
            case Flow.SPOTS: if (subject != null) renderSpots(); break;
            case Flow.COMPOSE: renderPlace(false); break;
            default: break;
        }
    }

    /** The steps that place a point: one question, how to answer it, and Next once it is answered. */
    private void renderPlace(boolean youStep) {
        sheet.setPullable(false);
        GeoPoint pt = youStep ? you : subject;
        boolean line = flow.tool == Flow.LINE;
        // Once the point is set the step says so, and what Next does (the three questions, 3.3)
        String b = flow.moon ? "moon" : "sun";
        String title = pt == null ? (youStep ? "Where will you stand?" : line ? "What is at the other end?"
                : "What do you want to photograph?")
                : youStep ? "You are on the map" : "Your subject: " + (subjectName != null ? subjectName : "on the map");
        sheet.head.addView(Ui.text(this, title, 22, 700, p.text));
        String how = youStep ? "Tap the map where you will stand: one end of the street. Or use your location, or search above."
                : line ? "Tap the other end of the line: the end of the street, a building, a peak. Or search above."
                : "Tap it on the map or search above: a building, a peak, a tree. That is your subject.";
        if (pt != null) how = "Tap again or drag it to move it. " + (youStep ? "Next: your subject."
                : line ? "Next shows when the " + b + " lines up with it." : flow.tool == Flow.BUILD
                ? "Next: place the " + b + " in the picture." : "Next: how you want the " + b + ".");
        sheet.head.addView(Ui.text(this, how, 15, 400, p.muted), top(6));
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
        next.setEnabled(pt != null);
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
        if (frame != null) setShotFraming(frame.panDeg(), frame.framePitch());   // a double tap there re-centres
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
        map.post(() -> fit(you, subject));
    }

    /** A moment in the Saved list was tapped: its line goes on the map and the Frame opens at that time. */
    @Override public void showMoment(Trip.Line l, long ms, boolean moon) {
        flow.moon = moon;
        prefs.edit().putBoolean("moon", moon).apply();
        p.setMoon(moon);
        openLine(l);
        closeTrip();
        map.post(() -> fit(you, subject));
        goShot(ms);
        showFrame(ms);
    }

    private void openLine(Trip.Line l) {
        you = new GeoPoint(l.youLat, l.youLon);
        subject = new GeoPoint(l.subLat, l.subLon);
        mode = l.mode;
        mapLineId = l.id;
        subjectName = null;
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
        String when = item.timeMs - Reminders.LEAD_MS < System.currentTimeMillis()
                ? "It is less than 30 minutes away, so the reminder comes in a moment."
                : "Reminder at " + Fmt.time(this, at, tz) + ", 30 minutes before.";
        if (trip != null) say("Planned. " + when);
        else say("Reminder set. " + when);
        if (changed != null) changed.run();
    }

    // ---- saving as you go ----

    private void loadState() {
        mode = prefs.getInt("mode", 0);
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
        planDist = prefs.getInt("plan_dist", 300);
        planLight = prefs.getInt("plan_light", 0);
        standPlace = prefs.getInt("stand_place", 2);
        moonFull = prefs.getInt("moon_full", 1);
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
        private float badgeX = Float.NaN, badgeY;          // the sun at the end of the line, for taps
        private final List<Point> spots = new ArrayList<>(); // the spots to stand, for taps

        LineOverlay() {
            labelText.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            labelText.setTypeface(Ui.font(MainActivity.this, 700));
            labelText.setTextAlign(Paint.Align.CENTER);
            labelEdge.setStyle(Paint.Style.STROKE);
        }

        boolean onSunBadge(Point t) {
            return !Float.isNaN(badgeX) && Math.hypot(t.x - badgeX, t.y - badgeY) < Ui.dp(MainActivity.this, 28);
        }

        int spotAt(Point t) {
            for (int i = 0; i < spots.size(); i++) {
                if (Math.hypot(t.x - spots.get(i).x, t.y - spots.get(i).y) < Ui.dp(MainActivity.this, 26)) return i;
            }
            return -1;
        }

        // Grab a point and drag it. Dragging does not pan the map; dropping it works out the answer again.
        @Override
        public boolean onTouchEvent(android.view.MotionEvent e, MapView mv) {
            if (covered() || (you == null && subject == null)) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    Projection pr = mv.getProjection();
                    float reach = Ui.dp(MainActivity.this, 30);
                    double da = Double.MAX_VALUE;
                    if (you != null) {
                        Point a = pr.toPixels(you, null);
                        float bodyUp = 9 * 2f * Ui.dp(MainActivity.this, 1);   // the pin's round head sits above its tip
                        da = Math.min(Math.hypot(e.getX() - a.x, e.getY() - a.y),
                                Math.hypot(e.getX() - a.x, e.getY() - (a.y - bodyUp)));
                    }
                    double db = Double.MAX_VALUE;
                    if (subject != null) {
                        Point b = pr.toPixels(subject, null);
                        db = Math.hypot(e.getX() - b.x, e.getY() - b.y);
                    }
                    if (Math.min(da, db) > reach) return false;
                    drag = db <= da ? 2 : 1;
                    backupYou = backupSubject = null;
                    mv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mv.invalidate();
                    return true;
                }
                case android.view.MotionEvent.ACTION_MOVE:
                    if (drag == 0) return false;
                    org.osmdroid.api.IGeoPoint g = mv.getProjection().fromPixels((int) e.getX(),
                            (int) (e.getY() - Ui.dp(MainActivity.this, 36)));   // the point rides above the finger, in sight
                    GeoPoint moved = new GeoPoint(g.getLatitude(), g.getLongitude());
                    if (drag == 1) you = moved; else { subject = moved; subjectName = null; }
                    mv.invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (drag == 0) return false;
                    boolean wasYou = drag == 1;
                    drag = 0;
                    if (wasYou) dropPin();
                    mapLineId = MAP_LINE;
                    retimeShot = flow.current() == Flow.SHOT;   // dragged on Your shot: a new moment
                    mv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    saveState();
                    refresh();
                    return true;
                default:
                    return drag != 0;
            }
        }

        @Override
        public void draw(Canvas c, MapView mv, boolean shadow) {
            if (shadow) return;
            float k = Ui.dp(MainActivity.this, 1);
            Projection pr = mv.getProjection();
            badgeX = Float.NaN;
            spots.clear();

            if (held != null) {                         // where the map is being held, waiting for an answer
                Point h = pr.toPixels(held, null);
                paint.setShader(null);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * k);
                paint.setColor(p.accent);
                c.drawCircle(h.x, h.y, 11 * k, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(h.x, h.y, 3 * k, paint);
            }

            if (you == null) {
                if (subject == null) return;
                Point s = pr.toPixels(subject, null);
                if (planning()) drawSpots(c, pr, s, k);
                dot(c, s, Ui.ROUTE_BLUE, k, drag == 2);
                label(c, subjectName == null ? "Subject" : subjectName, s.x, s.y - 20 * k, k);
                return;
            }
            Point a = pr.toPixels(you, null);
            Point b = subject == null ? null : pr.toPixels(subject, null);
            if (b != null) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                if (tab == 1 && sunHits != null) drawDay(c, a, k);
                else drawSunEnd(c, a, b, k);

                // The line itself: a soft outline so it reads on any map, then the green to blue stroke.
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8 * k);
                paint.setColor(Ui.withAlpha(p.night ? 0xFF000000 : 0xFFFFFFFF, 140));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setStrokeWidth(4 * k);
                paint.setShader(new LinearGradient(a.x, a.y, b.x, b.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setShader(null);
                dot(c, b, Ui.ROUTE_BLUE, k, drag == 2);
                label(c, subjectName == null ? "Subject" : subjectName, b.x, b.y - 20 * k, k);
            }
            drawYou(c, a, k, false);
        }

        /**
         * Where the sun or moon will stand: a dotted continuation of the line ending in a badge, with the time
         * of the next moment beside it. Tapping the badge sends it to the other end of the line.
         */
        private void drawSunEnd(Canvas c, Point a, Point b, float k) {
            float dx = b.x - a.x, dy = b.y - a.y;
            float len = (float) Math.max(1, Math.hypot(dx, dy));
            float reach = Math.max(64 * k, Math.min(150 * k, len * 0.9f));
            float ux = dx / len, uy = dy / len;
            float sx = mode == 0 ? b.x : a.x, sy = mode == 0 ? b.y : a.y;
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
            Astro.Hit h = moonFirst ? foundMoon.next : foundSun != null ? foundSun.next : null;
            String s = (moonFirst ? "Moon" : "Sun") + (h == null ? "" : " " + Fmt.time(MainActivity.this, h.timeMs, tz));
            label(c, s, ex, ey - 22 * k, k);
        }

        /** Every spot to stand for the light that is picked, as a fan round the subject; the chosen one is a pin. */
        private void drawSpots(Canvas c, Projection pr, Point s, float k) {
            int pick = Math.min(planPick, planGroups.size() - 1);
            for (int i = 0; i < planGroups.size(); i++) {
                Point q = pr.toPixels(spotFor(i), null);
                spots.add(q);
                if (i == pick) continue;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(q.x, q.y, 7 * k, paint);
                paint.setColor(Ui.ROUTE_GREEN);
                c.drawCircle(q.x, q.y, 4.5f * k, paint);
            }
            Point q = spots.get(pick);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(3 * k);
            paint.setShader(new LinearGradient(q.x, q.y, s.x, s.y, Ui.ROUTE_GREEN, Ui.ROUTE_BLUE, Shader.TileMode.CLAMP));
            paint.setPathEffect(new DashPathEffect(new float[]{7 * k, 7 * k}, 0));
            c.drawLine(q.x, q.y, s.x, s.y, paint);
            paint.setPathEffect(null);
            paint.setShader(null);
            drawYou(c, q, k, true);
        }

        /** The day's paths round you, and the sun and moon where the cursor is on the Day tab. */
        private void drawDay(Canvas c, Point a, float k) {
            if (sunHits == null) return;
            float rSun = 104 * k, rMoon = 78 * k;
            double lat = you.getLatitude(), lon = you.getLongitude();
            if (flow.moon) {        // only the body the tool is about
                trailOf(c, a, moonAz, moonAlt, rSun, p.violet, k);
                body(c, a, Astro.moon(cursorMs, lat, lon), rSun, p.violet, (float) Astro.moonLit(cursorMs), true, k);
            } else {
                trailOf(c, a, sunAz, sunAlt, rSun, p.amber, k);
                body(c, a, Astro.sun(cursorMs, lat, lon), rSun, p.amber, 1f, false, k);
            }
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
        private void drawYou(Canvas c, Point a, float k, boolean ghost) {
            float u = 2f * k;
            if (ghost) {                               // a spot to stand, not yet chosen
                Ui.drawPinHalo(c, a.x, a.y, u, Ui.ROUTE_GREEN, 1f);
                Ui.drawPin(c, a.x, a.y, u, 0f, 0.85f);
                label(c, "Stand here", a.x, a.y - Ui.PIN_HEIGHT * u - 5 * k, k);
                return;
            }
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
