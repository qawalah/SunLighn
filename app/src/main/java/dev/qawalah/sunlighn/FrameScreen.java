package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Frame: the viewfinder, on the whole screen. The scene on top, and under it the moment in words, a ruler of
 * time and the keys that jump to the moments that matter. Always dark, like the back of a camera.
 */
final class FrameScreen extends FrameLayout implements SensorEventListener {
    interface Host {
        void closeFrame();
        /** The time under the ruler changed: the map shows the sun and moon at that time. */
        void frameTime(long ms);
        void frameLens(double focalMm, boolean portrait);
        void subjectHeight(double metres, int kind);
        void say(String message);
        /** Is this moment a planned shoot? */
        boolean planned(long ms);
        /** Plan this moment as a shoot (with its reminder), or take it off the list. */
        void planShoot(long ms, Runnable changed);
        /** You moved (the Move map): the same point everywhere in the app. False if refused (too close). */
        boolean moveYou(double lat, double lon);
        /** Save this moment as a shot, in one press. */
        void saveShot(long ms);
        /** The map look the user chose, for the Move map. */
        void styleMap(org.osmdroid.views.MapView m);
        /** AR (3.5): may the camera be used? If not asked yet, ask, and call back with the answer. */
        void askCamera(java.util.function.Consumer<Boolean> answer);
    }

    /** Everything the screen needs to know about the line. */
    static final class Line {
        double youLat, youLon, subLat, subLon;
        int mode;                 // 0 = behind subject, 1 = behind you
        double height;            // metres, 0 = not known
        int kind;
        String name;              // the subject's name, or null
        TimeZone tz;
        long sunNext = -1, moonNext = -1;   // the next time each is on the line, -1 if never
        boolean moon;                       // the shot's body: the viewfinder shows only it (owner, 3.4)
        double panDeg = 0;                  // the framing from Build the shot (3.3)
        double pitch = Double.NaN;          // its tilt; NaN: aimed as usual
    }

    private static final int N = 97;                 // sixteen hours of path, a point every ten minutes
    private static final long TEN_MIN = 600000L;

    private final Ui.Palette p;
    private final Host host;
    private final Line line;
    private final FrameView view;
    private final TimeRuler ruler;
    private final LinearLayout panel, topBar, lensBar;
    private final TextView headline, lightTag, sunRead, moonRead, lensLabel, hint, dayLine;
    private TextView houseRead;
    private final Ui.GlyphView moonGlyph;
    private final Ui.IconButton liveKey, bell;
    private LinearLayout jumps;

    private long cursorMs;
    private double focal;
    private boolean portrait;
    private double manualPitch = Double.NaN;
    private long pathCenter = Long.MIN_VALUE, marksCenter = Long.MIN_VALUE;
    private final double[] sunAz = new double[N], sunAlt = new double[N], moonAz = new double[N], moonAlt = new double[N];
    private final String[] labels = new String[N];
    private int insetTop, insetBottom;

    // Live: the view follows the phone's compass and tilt.
    private boolean live;
    private SensorManager sensors;
    private double liveAz = Double.NaN, livePitch;
    private float declination;

    FrameScreen(Context c, Host host, Line line, long startMs, double focal, boolean portrait) {
        super(c);
        this.p = new Ui.Palette(c, true);
        this.host = host;
        this.line = line;
        this.cursorMs = startMs;
        this.focal = focal;
        this.portrait = portrait;
        setBackgroundColor(0xFF05070D);
        setClickable(true);

        view = new FrameView(c);
        view.setOnly(line.moon);
        view.setListener(new FrameView.Listener() {
            @Override public void onScrubPx(float dx) { ruler.dragBy(dx); }
            @Override public void onTilt(double pitch) {
                manualPitch = Math.max(-30, Math.min(89, pitch));
                update();
            }
            @Override public void onFocal(double f, boolean settled) {
                FrameScreen.this.focal = f;
                if (settled) host.frameLens(f, FrameScreen.this.portrait);
                update();
            }
            @Override public void onAimReset() { aimAgain(); }
        });
        addView(view, new LayoutParams(-1, -1));

        // 3.8 (owner: "the viewfinder was not improved at all", "more like the rendering"): the scene fills the
        // screen; Back and the shot's keys sit on top; the moment lies over the scene at the bottom: the lens, the
        // time large, where the body is, the ruler, and the keys that change the view, each with its name.
        topBar = new LinearLayout(c);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        Ui.IconButton close = key(topBar, Ui.Icon.BACK, "Back", "Back");
        close.setOnClickListener(v -> host.closeFrame());
        topBar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        bell = key(topBar, Ui.Icon.BELL, "Remind me", "Remind me: keep this shot and remind me 30 minutes before");
        bell.setOnClickListener(v -> host.planShoot(cursorMs, this::update));
        Ui.IconButton share = key(topBar, Ui.Icon.SHARE, "Share", "Share this moment");
        share.setOnClickListener(this::shareMenu);
        Ui.IconButton save = key(topBar, Ui.Icon.SAVED, "Save", "Save this shot");
        save.setOnClickListener(v -> host.saveShot(cursorMs));
        addView(topBar, new LayoutParams(-1, -2, Gravity.TOP));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000, 0xEB000000, 0xF5000000}));
        panel.setClickable(true);

        // The lens: pinch, or step through the usual ones.
        lensBar = new LinearLayout(c);
        lensBar.setGravity(Gravity.CENTER_VERTICAL);
        lensBar.setBackground(Ui.shape(c, 0x59000000, 0x40FFFFFF, 99, false));
        Ui.IconButton wider = new Ui.IconButton(c, p, Ui.Icon.MINUS, "Wider lens").bare().size(40);
        Ui.IconButton longer = new Ui.IconButton(c, p, Ui.Icon.PLUS, "Longer lens").bare().size(40);
        lensLabel = Ui.text(c, "", 13, 600, 0xFFFFFFFF);
        lensLabel.setGravity(Gravity.CENTER);
        lensLabel.setMinWidth(Ui.dp(c, 120));
        wider.setOnClickListener(v -> stepLens(-1));
        longer.setOnClickListener(v -> stepLens(1));
        lensBar.addView(wider);
        lensBar.addView(lensLabel);
        lensBar.addView(longer);
        panel.addView(lensBar, new LinearLayout.LayoutParams(-2, -2));

        headline = Ui.text(c, "", 42, 700, 0xFFFFFFFF);
        headline.setLetterSpacing(-0.02f);
        headline.setFontFeatureSettings("tnum");
        headline.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0x99000000);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = Ui.dp(c, 10);
        panel.addView(headline, hl);
        LinearLayout dayRow = new LinearLayout(c);
        dayRow.setGravity(Gravity.CENTER_VERTICAL);
        dayLine = Ui.text(c, "", 14, 500, 0xFFE4E1DA);
        dayRow.addView(dayLine);
        lightTag = Ui.tag(c, "", p.amber);
        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(-2, -2);
        lt.setMarginStart(Ui.dp(c, 8));
        dayRow.addView(lightTag, lt);
        panel.addView(dayRow, new LinearLayout.LayoutParams(-2, -2));

        sunRead = Ui.text(c, "", 13.5f, 400, 0xFFC9C6BF);
        moonRead = Ui.text(c, "", 13.5f, 400, 0xFFC9C6BF);
        moonGlyph = new Ui.GlyphView(c, Ui.GlyphView.Kind.MOON, p.violet, 22);
        TextView read = line.moon ? moonRead : sunRead;   // only the shot's body (owner, 3.4)
        read.setGravity(Gravity.CENTER);
        panel.addView(read, pad(4));
        // 3.14.1 (owner saw no buildings and no reason): what the buildings are doing, in one quiet line
        houseRead = Ui.text(c, "", 12.5f, 400, 0xFF9B988F);
        houseRead.setGravity(Gravity.CENTER);
        houseRead.setVisibility(GONE);
        panel.addView(houseRead, pad(2));

        ruler = new TimeRuler(c, p);
        ruler.setPlace(line.youLat, line.youLon, line.tz);
        ruler.setListener(ms -> {
            cursorMs = ms;
            update();
            host.frameTime(ms);
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(c, 8);
        panel.addView(ruler, rl);

        jumps = new LinearLayout(c);
        jumps.setGravity(Gravity.CENTER);
        buildJumps();
        panel.addView(jumps, pad(6));

        calibrate = Ui.smallButton(c, p, "Calibrate the compass");
        calibrate.setContentDescription("Calibrate the compass");
        calibrate.setOnClickListener(v -> openCalibrate());
        calibrate.setVisibility(GONE);                // only while Compass is on
        panel.addView(calibrate, pad(8));
        hint = Ui.text(c, "Pinch to change the lens. Drag the sky sideways for time, up or down to tilt.", 12, 400, 0xFFA3A09A);
        hint.setGravity(Gravity.CENTER);
        hint.setVisibility(GONE);                     // 3.8: the picture gets the room; the hint shows with Compass on
        panel.addView(hint, pad(6));

        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.CENTER_HORIZONTAL);
        liveKey = key(keys, Ui.Icon.LIVE, "Compass", "Compass: follow the way the phone points");
        liveKey.setOnClickListener(v -> setLive(!live));
        Ui.IconButton turn = key(keys, Ui.Icon.ROTATE, "Turn", "Turn the camera: landscape or portrait");
        turn.setOnClickListener(v -> {
            this.portrait = !this.portrait;
            manualPitch = Double.NaN;
            host.frameLens(this.focal, this.portrait);
            update();
        });
        Ui.IconButton tall = key(keys, Ui.Icon.HEIGHT, "Height", "Subject height");
        tall.setOnClickListener(v -> askHeight());
        Ui.IconButton move = key(keys, Ui.Icon.MOVE, "Move", "Move: walk where you stand");
        move.setOnClickListener(v -> { if (walkPanel == null) openWalk(); else closeWalk(); });
        panel.addView(keys, pad(10));
        addView(panel, new LayoutParams(-1, -2, Gravity.BOTTOM));

        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        applyInsets();
        update();
        askGround();
    }

    /** A key over the scene with its name under it: an icon alone is a riddle the first time. */
    private Ui.IconButton key(LinearLayout parent, Ui.Icon icon, String label, String description) {
        Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.IconButton b = new Ui.IconButton(c, p, icon, description).onScene().size(42);
        col.addView(b);
        TextView t = Ui.text(c, label, 11, 600, 0xFFFFFFFF);
        t.setShadowLayer(Ui.dp(c, 2), 0, Ui.dp(c, 1), 0xB3000000);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(c, 3);
        col.addView(t, tl);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(c, parent == topBar ? 60 : 72), -2);
        parent.addView(col, l);
        return b;
    }

    private LinearLayout.LayoutParams pad(int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), topDp);
        l.setMarginStart(Ui.dp(getContext(), 16));
        l.setMarginEnd(Ui.dp(getContext(), 16));
        return l;
    }

    private LinearLayout readRow(View glyph, TextView text) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(glyph);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(getContext(), 10));
        row.addView(text, tl);
        return row;
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyInsets();
    }

    private void applyInsets() {
        int s = Ui.dp(getContext(), 12);
        topBar.setPadding(Ui.dp(getContext(), 4), insetTop + s, Ui.dp(getContext(), 4), Ui.dp(getContext(), 20));
        panel.setPadding(0, Ui.dp(getContext(), 40), 0, insetBottom + Ui.dp(getContext(), 12));
        place();
    }

    /** The scene fills the screen; the frame is placed above the dense part of the panel (3.8). */
    private void place() {
        if (walkPanel != null) return;               // walking: the map and the stick hold the lower part
        int ph = panel.getHeight();
        view.setInsets(insetTop + Ui.dp(getContext(), 56), Math.max(0, ph - Ui.dp(getContext(), 40)));
    }

    long time() { return cursorMs; }

    /** The next time each body is on the line became known (the search runs in the background). */
    void setNext(long sunNext, long moonNext) {
        line.sunNext = sunNext;
        line.moonNext = moonNext;
        buildJumps();
    }

    private void buildJumps() {
        Context c = getContext();
        jumps.removeAllViews();
        String[] names = {"Sun lined up", "Moon lined up", "Now"};
        long[] times = {line.sunNext, line.moonNext, 0};
        for (int i = 0; i < 3; i++) {
            if (i < 2 && times[i] <= 0) continue;
            if (i < 2 && (i == 1) != line.moon) continue;      // the shot's body only (owner, 3.4)
            final long t = times[i];
            TextView b = Ui.smallButton(c, p, names[i]);
            b.setOnClickListener(v -> jump(t == 0 ? System.currentTimeMillis() : t));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (jumps.getChildCount() > 0) l.setMarginStart(Ui.dp(c, 8));
            jumps.addView(b, l);
        }
    }

    /** Double tap: aimed at the subject again, centred, at the usual tilt (the framing goes). */
    void aimAgain() {
        manualPitch = Double.NaN;
        line.panDeg = 0;
        line.pitch = Double.NaN;
        if (live) setLive(false);
        update();
    }

    /** The framing now (for saving the shot). */
    double panDeg() { return line.panDeg; }

    double framePitch() { return line.pitch; }

    /** The host found a new moment (You moved): show it. */
    void jumpTo(long ms) { jump(ms); }

    private void jump(long ms) {
        cursorMs = ms;
        manualPitch = Double.NaN;
        update();
        host.frameTime(ms);
    }

    private void stepLens(int dir) {
        focal = FrameMath.stepFocal(focal, dir);
        manualPitch = Double.NaN;
        host.frameLens(focal, portrait);
        update();
    }

    private double subjectAz() {
        return Astro.bearing(line.youLat, line.youLon, line.subLat, line.subLon);
    }

    private double lineAz() {
        return line.mode == 0 ? subjectAz() : Astro.norm360(subjectAz() + 180);
    }

    /** Works out the scene for the chosen time and puts the words under it. */
    private void update() {
        Context c = getContext();
        double lat = line.youLat, lon = line.youLon;
        double[] s = Astro.sun(cursorMs, lat, lon), m = Astro.moon(cursorMs, lat, lon);
        double lit = Astro.moonLit(cursorMs);
        double fovV = FrameMath.fovV(focal, portrait);

        // Aim: the phone's own direction when live, else along the line, tilted to show the body that is up.
        double az, pitch;
        if (live && !Double.isNaN(liveAz)) {
            az = liveAz;
            pitch = livePitch;
        } else {
            az = subjectAz() + line.panDeg;                 // framed in Build the shot: the same picture (3.3)
            double body = line.mode == 0 ? (line.moon ? m[1] : s[1]) : -1;   // the shot's body; behind you: nothing to aim at
            double top = FrameMath.topAngle(line.height, distance(), rise());
            pitch = !Double.isNaN(manualPitch) ? manualPitch : !Double.isNaN(line.pitch) ? line.pitch
                    : FrameMath.aimPitch(fovV, Math.max(body, top));
        }
        view.setLens(focal, portrait);
        view.setAim(az, pitch, live);
        view.setSubject(subjectAz(), distance(), line.height, line.kind, lineAz(), line.mode == 1, rise());
        view.setBodies(s[0], s[1], m[0], m[1], lit, Astro.moonWaxing(cursorMs));
        houses();
        paths();
        marks();
        ruler.setTime(cursorMs);

        headline.setText(Fmt.time(c, cursorMs, line.tz));       // the time large; the day under it (3.8)
        dayLine.setText(Fmt.day(c, cursorMs, line.tz));
        Light.Kind kind = Light.of(s[1]);
        lightTag.setText(Light.name(kind));
        int tagColor = kind == Light.Kind.GOLDEN ? p.amber : kind == Light.Kind.DAY ? 0xFF7CC4F8
                : kind == Light.Kind.BLUE ? 0xFF8FA8FF : p.muted;
        lightTag.setTextColor(tagColor);
        lightTag.setBackground(Ui.shape(c, Ui.withAlpha(tagColor, 34), 0, 6, false));
        sunRead.setText("Sun · " + where(s, null));
        moonGlyph.setMoon((float) lit, Astro.moonWaxing(cursorMs));
        moonRead.setText("Moon · " + where(m, Math.round(lit * 100) + "% lit"));
        lensLabel.setText(Math.round(focal) + " mm lens · " + (focal < 30 ? "wide" : focal < 70 ? "normal" : "long"));
        boolean on = host.planned(cursorMs);
        bell.setIcon(on ? Ui.Icon.BELL_ON : Ui.Icon.BELL);
        bell.setContentDescription(on ? "Planned. Press to take it off your shoots"
                : "Plan this shoot: keep it and remind me 30 minutes before");
        lensLabel.setContentDescription(Math.round(focal) + " millimetre lens, " + (portrait ? "portrait" : "landscape"));
        hint.setVisibility(live ? VISIBLE : GONE);
        hint.setText(live ? "Compass is on: turn and tilt the phone to look around. Double tap the sky to aim at the subject again."
                : line.mode == 1 ? "The sun or moon is wanted behind you here, so it lights the subject from the front."
                : line.height <= 0 ? "Drag the ruler to move through time. Press Height (top) to draw the subject to scale."
                : "Drag the ruler or the sky to move through time. Pinch to change the lens. The bell plans this shoot.");
    }

    private String where(double[] pos, String extra) {
        // Just under 0 is still sunset or moonrise to the eye (the air lifts it): "on the horizon", never
        // "below the horizon · lined up" together (Ali's test, 2026-11-15).
        String out = pos[1] >= 0.5 ? Math.round(pos[1]) + "° up, " + Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0])
                : pos[1] >= -1 ? "on the horizon, " + Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0])
                : "below the horizon";
        if (extra != null) out += " · " + extra;
        if (pos[1] >= -1 && Math.abs(FrameMath.diff(pos[0], lineAz())) < 0.3) out += " · lined up";
        return out;
    }

    private double distance() {
        return Astro.distance(line.youLat, line.youLon, line.subLat, line.subLon);
    }

    /** The paths the sun and moon travel, eight hours either side; worked out again when the time has moved far. */
    private void paths() {
        if (pathCenter != Long.MIN_VALUE && Math.abs(cursorMs - pathCenter) < 3 * 3600000L) return;
        pathCenter = cursorMs / TEN_MIN * TEN_MIN;
        java.text.SimpleDateFormat hf = Fmt.timeFormat(getContext(), line.tz);
        java.util.Calendar cal = java.util.Calendar.getInstance(line.tz);
        for (int i = 0; i < N; i++) {
            long t = pathCenter + (i - N / 2) * TEN_MIN;
            double[] s = Astro.sun(t, line.youLat, line.youLon), m = Astro.moon(t, line.youLat, line.youLon);
            sunAz[i] = s[0];
            sunAlt[i] = s[1];
            moonAz[i] = m[0];
            moonAlt[i] = m[1];
            cal.setTimeInMillis(t);
            labels[i] = cal.get(java.util.Calendar.MINUTE) == 0 ? hf.format(new Date(t)) : null;
        }
        view.setPaths(sunAz, sunAlt, moonAz, moonAlt, labels);
    }

    /** The moments the ruler stops at, two days either side. */
    private void marks() {
        if (marksCenter != Long.MIN_VALUE && Math.abs(cursorMs - marksCenter) < 86400000L) return;
        marksCenter = cursorMs;
        long from = cursorMs - 2 * 86400000L, to = cursorMs + 2 * 86400000L;
        double lat = line.youLat, lon = line.youLon;
        List<TimeRuler.Mark> marks = new ArrayList<>();
        if (!line.moon) for (Astro.Hit h : Astro.hitsBetween(false, lat, lon, lineAz(), from, to)) {
            marks.add(new TimeRuler.Mark(h.timeMs, "Lined up", p.amber));
        }
        if (line.moon) for (Astro.Hit h : Astro.hitsBetween(true, lat, lon, lineAz(), from, to)) {
            marks.add(new TimeRuler.Mark(h.timeMs, "Lined up", p.violet));
        }
        for (int k = 0; k < 2; k++) {
            long t = from;
            for (int guard = 0; guard < 8; guard++) {
                t = Astro.bodyPasses(false, lat, lon, -0.833, k == 1, t);
                if (t < 0 || t > to) break;
                marks.add(new TimeRuler.Mark(t, k == 1 ? "Sunset" : "Sunrise", 0xFFD8D5CE));
                t += 3600000L;
            }
        }
        marks.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        ruler.setMarks(marks);
    }

    // ---- Live: the phone's compass and tilt aim the view ----

    private void setLive(boolean on) {
        if (on == live) return;
        if (on) {
            sensors = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            Sensor rot = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rot == null) {
                host.say("This phone has no compass to follow.");
                return;
            }
            declination = new GeomagneticField((float) line.youLat, (float) line.youLon, 0, System.currentTimeMillis())
                    .getDeclination();
            liveAz = rawAz = Double.NaN;
            compassOffset = loadOffset();
            sensors.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
        } else {
            if (sensors != null) sensors.unregisterListener(this);
            stopCamera();
            closeCalibrate();
        }
        live = on;
        calibrate.setVisibility(on ? VISIBLE : GONE);
        if (on) host.askCamera(ok -> { if (ok && live) startCamera(); });   // AR: the camera behind, when allowed
        liveKey.setLit(on);
        liveKey.setContentDescription(on ? "Compass is on. Press to aim at the subject again" : "Compass: follow the way the phone points");
        update();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float[] r = new float[9];
        SensorManager.getRotationMatrixFromVector(r, e.values);
        // Where the back camera points, in east, north, up.
        double east = -r[2], north = -r[5], up = -r[8];
        double az = Astro.norm360(Math.toDegrees(Math.atan2(east, north)) + declination);
        double alt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, up))));
        if (Double.isNaN(rawAz)) {
            rawAz = az;
            livePitch = alt;
        } else {                                    // smooth the needle so the picture does not shake
            rawAz = Astro.norm360(rawAz + FrameMath.diff(az, rawAz) * 0.18);
            livePitch += (alt - livePitch) * 0.18;
        }
        liveAz = Astro.norm360(rawAz + compassOffset);   // set from a landmark (3.5)
        update();
    }

    /** A compass reading, as the sensor would give it (tests; the smoothing is skipped). */
    void feedRaw(double az, double alt) {
        rawAz = az;
        livePitch = alt;
        liveAz = Astro.norm360(rawAz + compassOffset);
        update();
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

    // ---- AR (3.5): the camera behind the scene, and the compass set from a landmark ----

    private double rawAz = Double.NaN;          // the compass as read, before the correction
    private double compassOffset;               // degrees to add to the compass (from a landmark), kept in prefs
    private CameraFeed camera;
    private double focalBeforeAr = Double.NaN;
    private boolean portraitBeforeAr;
    private TextView calibrate;

    private double loadOffset() {
        try {
            return Double.parseDouble(getContext().getSharedPreferences("sunlighn", Context.MODE_PRIVATE)
                    .getString("compass_offset", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void startCamera() {
        if (camera != null) return;
        focalBeforeAr = focal;
        portraitBeforeAr = portrait;
        camera = new CameraFeed(getContext(), f -> {
            focal = Math.max(FrameMath.FOCAL_MIN, Math.min(FrameMath.FOCAL_MAX, f));
            update();
        });
        addView(camera, 0, new LayoutParams(-1, -1));   // behind everything
        portrait = true;                                // the phone held upright
        view.setAr(true);
        lensBar.setVisibility(GONE);                    // the camera's lens is the lens; and the cross stays in sight
        update();
    }

    private void stopCamera() {
        if (camera == null) return;
        camera.close();
        removeView(camera);
        camera = null;
        view.setAr(false);
        if (walkPanel == null) lensBar.setVisibility(VISIBLE);
        if (!Double.isNaN(focalBeforeAr)) {
            focal = focalBeforeAr;
            portrait = portraitBeforeAr;
        }
        update();
    }

    private FrameLayout calPanel;
    private org.osmdroid.views.MapView calMap;
    private double lmLat = Double.NaN, lmLon;
    private TextView calSet;

    /** The landmark chosen on the small map (the map's tap, and tests). */
    void pickLandmark(double lat, double lon) {
        lmLat = lat;
        lmLon = lon;
        if (calSet != null) {
            calSet.setEnabled(true);
            calSet.setAlpha(1f);
        }
        if (calMap != null) calMap.invalidate();
    }

    private void openCalibrate() {
        if (calPanel != null) return;
        Context c = getContext();
        calPanel = new FrameLayout(c);
        calPanel.setElevation(Ui.dp(c, 16));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), insetBottom + Ui.dp(c, 12));
        col.setBackground(Ui.shape(c, p.panel, 0x40FFFFFF, 22, true));
        col.addView(Ui.text(c, "1. Tap a landmark you can see: a tower, a mast, a hilltop. 2. Point the cross at it. "
                + "3. Set.", 13, 500, p.text));
        calMap = new org.osmdroid.views.MapView(c);
        calMap.setTilesScaledToDpi(true);
        calMap.setMultiTouchControls(true);
        calMap.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        host.styleMap(calMap);
        calMap.setContentDescription("Map: tap a landmark you can see");
        calMap.getController().setZoom(15.5);
        calMap.getController().setCenter(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon));
        calMap.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(new org.osmdroid.events.MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(org.osmdroid.util.GeoPoint g) {
                pickLandmark(g.getLatitude(), g.getLongitude());
                return true;
            }
            @Override public boolean longPressHelper(org.osmdroid.util.GeoPoint g) { return false; }
        }));
        calMap.getOverlays().add(new CalOverlay());
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, Ui.dp(c, 200));
        ml.topMargin = Ui.dp(c, 8);
        col.addView(calMap, ml);
        LinearLayout row = new LinearLayout(c);
        TextView reset = Ui.smallButton(c, p, "Reset");
        reset.setOnClickListener(v -> setOffset(0, "Compass as the phone reads it."));
        row.addView(reset, new LinearLayout.LayoutParams(0, -2, 1f));
        calSet = Ui.button(c, p, "Set", true);
        calSet.setOnClickListener(v -> setFromLandmark());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 2f);
        sl.setMarginStart(Ui.dp(c, 8));
        row.addView(calSet, sl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(c, 10);
        col.addView(row, rl);
        calPanel.addView(col, new LayoutParams(-1, -2));
        addView(calPanel, new LayoutParams(-1, -2, Gravity.BOTTOM));
        // The subject is offered first: often the easiest thing to point at, but any landmark will do (owner, 3.5)
        pickLandmark(line.subLat, line.subLon);
        calMap.addOnFirstLayoutListener((v, l, t, r, b) -> {      // you and the landmark both in sight
            if (calMap == null || Double.isNaN(lmLat)) return;
            double n = Math.max(line.youLat, lmLat), s0 = Math.min(line.youLat, lmLat);
            double e = Math.max(line.youLon, lmLon), w = Math.min(line.youLon, lmLon);
            double pad = Math.max(n - s0, (e - w) * Math.cos(Math.toRadians(n))) * 0.3 + 0.001;
            calMap.zoomToBoundingBox(new org.osmdroid.util.BoundingBox(n + pad, e + pad, s0 - pad, w - pad), false, Ui.dp(c, 16));
        });
        view.setCross(true);
    }

    private void closeCalibrate() {
        if (calPanel == null) return;
        if (calMap != null) calMap.onDetach();
        removeView(calPanel);
        calPanel = null;
        calMap = null;
        calSet = null;
        view.setCross(false);
    }

    /** Set: the cross is on the landmark, so the compass should read its true bearing from you. */
    private void setFromLandmark() {
        if (Double.isNaN(lmLat) || Double.isNaN(rawAz)) {
            host.say("Point the phone at the landmark first.");
            return;
        }
        double truth = Astro.bearing(line.youLat, line.youLon, lmLat, lmLon);
        double off = FrameMath.headingOffset(truth, rawAz);
        setOffset(off, "Compass set: " + Math.round(Math.abs(off)) + "° to the " + (off >= 0 ? "right" : "left") + ".");
        closeCalibrate();
    }

    private void setOffset(double off, String said) {
        compassOffset = off;
        getContext().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit()
                .putString("compass_offset", Double.toString(off)).apply();
        if (!Double.isNaN(rawAz)) liveAz = Astro.norm360(rawAz + compassOffset);
        update();
        host.say(said);
    }

    /** On the calibration map: you, and the landmark picked, with the line to it. */
    private final class CalOverlay extends org.osmdroid.views.overlay.Overlay {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c, org.osmdroid.views.MapView m, boolean shadow) {
            if (shadow) return;
            float k = getResources().getDisplayMetrics().density;
            android.graphics.Point a = m.getProjection().toPixels(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon), null);
            paint.setStyle(Paint.Style.FILL);
            if (!Double.isNaN(lmLat)) {
                android.graphics.Point b = m.getProjection().toPixels(new org.osmdroid.util.GeoPoint(lmLat, lmLon), null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * k);
                paint.setColor(p.accent);
                c.drawLine(a.x, a.y, b.x, b.y, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(b.x, b.y, 7 * k, paint);
            }
            paint.setColor(Ui.ROUTE_BLUE);
            c.drawCircle(a.x, a.y, 6 * k, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensors != null) sensors.unregisterListener(this);
    }

    // ---- the subject's height ----

    private AlertDialog dialog;

    /** True if a dialog was open and is now closed (Back closes the top thing first). */
    boolean closeDialog() {
        if (calPanel != null) {
            closeCalibrate();
            return true;
        }
        if (walkPanel != null) {
            closeWalk();
            return true;
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null;
            return true;
        }
        return false;
    }

    private void askHeight() {
        dialog = HeightDialog.show(getContext(), p, line.height, line.kind, false, this::setHeight);
    }

    private void setHeight(double h, int kind) {
        line.height = h;
        line.kind = kind;
        manualPitch = Double.NaN;
        host.subjectHeight(h, kind);
        update();
    }

    // ---- sharing the moment ----

    private void shareMenu(View anchor) {
        PopupMenu m = new PopupMenu(getContext(), anchor);
        m.getMenu().add(0, 1, 0, "Share as text");
        m.getMenu().add(0, 2, 1, "Add to calendar");
        m.setOnMenuItemClickListener(i -> {
            try {
                if (i.getItemId() == 1) {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shareText());
                    getContext().startActivity(Intent.createChooser(send, "Share this moment"));
                } else {
                    getContext().startActivity(new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, title())
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cursorMs - 30 * 60000L)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cursorMs + 15 * 60000L)
                            .putExtra(CalendarContract.Events.EVENT_LOCATION,
                                    String.format(Locale.US, "%.5f, %.5f", line.youLat, line.youLon))
                            .putExtra(CalendarContract.Events.DESCRIPTION, shareText()));
                }
            } catch (android.content.ActivityNotFoundException e) {
                host.say(i.getItemId() == 1 ? "No app on this phone can share text." : "No calendar app on this phone.");
            }
            return true;
        });
        m.show();
    }

    private String title() {
        double[] s = Astro.sun(cursorMs, line.youLat, line.youLon), m = Astro.moon(cursorMs, line.youLat, line.youLon);
        boolean moon = Math.abs(FrameMath.diff(m[0], lineAz())) < Math.abs(FrameMath.diff(s[0], lineAz())) && m[1] > -1;
        String subject = line.name == null ? "subject" : line.name;
        return (moon ? "Moon " : "Sun ") + (line.mode == 0 ? "behind " + subject : "behind you, on " + subject);
    }

    String shareText() {
        Context c = getContext();
        return title() + "\n" + Fmt.when(c, cursorMs, line.tz) + " (" + Fmt.zoneName(line.tz) + ")\n"
                + String.format(Locale.US, "Stand here: https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=17/%.5f/%.5f\n",
                line.youLat, line.youLon, line.youLat, line.youLon)
                + String.format(Locale.US, "Subject: %.5f, %.5f · %s away · %d mm lens",
                line.subLat, line.subLon, Fmt.distance(distance()), Math.round(focal));
    }

    // ---- Move: a small map over the lower part, to drag where you stand ----

    /** How much higher the subject's foot is than your eye (the grounds, when known; else level). */
    private double rise() {
        return Heights.rise(getContext(), line.youLat, line.youLon, line.subLat, line.subLon);
    }

    /**
     * The buildings round where you stand (3.14, optional: Settings, Buildings). While you walk the last ones fetched
     * are drawn; they are asked for again where you let go.
     */
    private void houses() {
        Context c = getContext();
        if (!Buildings.on(c)) {
            view.setBuildings(null, line.youLat, line.youLon, line.subLat, line.subLon);
            if (houseRead != null) houseRead.setVisibility(GONE);
            return;
        }
        Buildings.Area a = Buildings.get(c, line.youLat, line.youLon, lineAz());
        if (a == null) {
            a = Buildings.around(line.youLat, line.youLon, 250);
            if (walkPanel == null) {
                Buildings.fetch(c, line.youLat, line.youLon, lineAz(), () -> { if (getParent() != null) update(); });
            }
        }
        view.setBuildings(a, line.youLat, line.youLon, line.subLat, line.subLon);
        if (houseRead == null) return;
        int state = Buildings.state(c, line.youLat, line.youLon, lineAz());
        String words;
        if (a != null && (state == Buildings.READY || walkPanel != null)) {
            int n = a.list.size(), g = a.guessed();
            words = n == 0 ? "No buildings mapped here" : "Buildings: " + n + " near" + (g == 0 ? ""
                    : g == n ? " · heights guessed (about " + Fmt.height(Buildings.GUESS) + ")" : " · " + g + " heights guessed");
        } else if (state == Buildings.FAILED_) {
            words = "Buildings could not be fetched (no network?)";
        } else {
            words = "Getting the buildings…";
        }
        houseRead.setText(words);
        houseRead.setVisibility(VISIBLE);
    }

    /** Asks for the two grounds if they are not known; the scene redraws when they come. */
    private void askGround() {
        Heights.fetch(getContext(), new double[]{line.youLat, line.subLat}, new double[]{line.youLon, line.subLon},
                () -> { if (getParent() != null) update(); });
    }

    /** You stands here now: the scene, the paths and the answers follow. */
    void moveTo(double lat, double lon) {
        if (!host.moveYou(lat, lon)) {          // refused: the viewfinder and the map keep the same You
            return;
        }
        line.youLat = lat;
        line.youLon = lon;
        askGround();
        pathCenter = marksCenter = Long.MIN_VALUE;
        ruler.setPlace(lat, lon, line.tz);
        manualPitch = Double.NaN;
        update();
    }

    // ---- Move (3.4): a joystick to walk where you stand while you look; the scene follows as you go ----

    private FrameLayout walkPanel;
    private TextView walkWords;
    private org.osmdroid.views.MapView walkMap;   // where you are as you walk: you, the subject and the line (3.4.2)

    /** Where the small map shows you (for tests). */
    /**
     * The small map turns so the subject is straight up, as the stick's up walks towards it (owner, 3.10: "orientation
     * when moving should point to the subject").
     */
    private void headUp() {
        if (walkMap != null) walkMap.setMapOrientation((float) -subjectAz());
    }

    double[] walkMapYou() { return new double[]{line.youLat, line.youLon}; }

    private void openWalk() {
        Context c = getContext();
        walkPanel = new FrameLayout(c);
        walkPanel.setElevation(Ui.dp(c, 16));      // over the sheet's shadow, or it is drawn and touched beneath it
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
        col.setBackground(Ui.shape(c, 0x660B1220, 0x26FFFFFF, 20, false));   // light: the scene shows through
        Joystick stick = new Joystick(c);
        col.addView(stick, new LinearLayout.LayoutParams(Ui.dp(c, 128), Ui.dp(c, 128)));
        walkWords = Ui.text(c, "", 12, 600, 0xFFFFFFFF);
        walkWords.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.dp(c, 140), -2);
        wl.topMargin = Ui.dp(c, 6);
        col.addView(walkWords, wl);
        // A small map beside the stick: where you are now, the subject and the line, following you (owner, 3.4.2)
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.BOTTOM);
        walkMap = new org.osmdroid.views.MapView(c);
        walkMap.setTilesScaledToDpi(true);
        walkMap.setMultiTouchControls(false);
        walkMap.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        walkMap.setOnTouchListener((v, e) -> true);      // to look at, not to move: the stick moves you
        host.styleMap(walkMap);
        walkMap.setContentDescription("Map: where you stand now");
        walkMap.getOverlays().add(new WalkOverlay());
        walkMap.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        walkMap.setBackground(Ui.shape(c, p.panel, 0x33FFFFFF, 16, false));
        walkMap.setClipToOutline(true);
        double span = Math.max(150, distance() * 1.3) / 111320.0;   // you in the middle, the subject in sight
        walkMap.getController().setZoom(16.0);
        headUp();
        walkMap.getController().setCenter(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon));
        walkMap.addOnFirstLayoutListener((v, l, t, r, b) -> {
            if (walkMap == null) return;
            double lonSpan = span / Math.cos(Math.toRadians(line.youLat));
            walkMap.zoomToBoundingBox(new org.osmdroid.util.BoundingBox(line.youLat + span, line.youLon + lonSpan,
                    line.youLat - span, line.youLon - lonSpan), false, 0);
            walkMap.getController().setCenter(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon));
            walkMap.post(() -> { if (walkMap != null) walkMap.getController().setCenter(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon)); });
        });
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -1, 1f);
        ml.rightMargin = Ui.dp(c, 8);
        row.addView(walkMap, ml);
        row.addView(col, new LinearLayout.LayoutParams(-2, -1));
        col.setGravity(Gravity.CENTER);
        walkPanel.addView(row, new LayoutParams(-1, -1));
        // The lower part of the screen is the map and the stick while you walk (owner, 3.4.3): the sheet and the lens
        // bar step aside, and the picture sits above, whole
        int walkH = Ui.dp(c, 280);
        LayoutParams lp = new LayoutParams(-1, walkH, Gravity.BOTTOM);
        lp.leftMargin = lp.rightMargin = Ui.dp(c, 12);
        lp.bottomMargin = insetBottom + Ui.dp(c, 12);
        lensBar.setVisibility(GONE);
        panel.setVisibility(GONE);
        view.setInsets(insetTop, walkH + insetBottom + Ui.dp(c, 24));
        addView(walkPanel, lp);
        sayWhere();
    }

    private void closeWalk() {
        if (walkPanel == null) return;
        if (walkMap != null) walkMap.onDetach();
        walkMap = null;
        lensBar.setVisibility(VISIBLE);
        panel.setVisibility(VISIBLE);
        removeView(walkPanel);
        place();
        walkPanel = null;
        walkWords = null;
    }

    private void sayWhere() {
        if (walkWords == null) return;
        double back = Astro.bearing(line.subLat, line.subLon, line.youLat, line.youLon);
        walkWords.setText(Fmt.distance(distance()) + " " + Astro.compass(back) + " of the subject");
    }

    /** One step of walking: forward (towards the subject) and to the right, in metres. The scene follows at once. */
    private void walk(double forward, double right) {
        double az = subjectAz();
        double[] a = Astro.destination(line.youLat, line.youLon, az, forward);
        double[] b = Astro.destination(a[0], a[1], az + 90, right);
        if (Astro.distance(b[0], b[1], line.subLat, line.subLon) < 15) return;   // never onto the subject
        line.youLat = b[0];
        line.youLon = b[1];
        pathCenter = marksCenter = Long.MIN_VALUE;
        ruler.setPlace(line.youLat, line.youLon, line.tz);
        update();
        sayWhere();
        if (walkMap != null) {                      // the small map follows you
            headUp();
            walkMap.getController().setCenter(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon));
            walkMap.invalidate();
        }
    }

    /** On the small map: the line from you to the subject, the subject, and you (the pin's colour). */
    private final class WalkOverlay extends org.osmdroid.views.overlay.Overlay {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c, org.osmdroid.views.MapView m, boolean shadow) {
            if (shadow) return;
            float k = getResources().getDisplayMetrics().density;
            android.graphics.Point a = m.getProjection().toPixels(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon), null);
            android.graphics.Point b = m.getProjection().toPixels(new org.osmdroid.util.GeoPoint(line.subLat, line.subLon), null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * k);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Ui.ROUTE_GREEN);
            c.drawLine(a.x, a.y, b.x, b.y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.ROUTE_BLUE);
            c.drawCircle(b.x, b.y, 5 * k, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(a.x, a.y, 7 * k, paint);
            paint.setColor(p.accent);
            c.drawCircle(a.x, a.y, 5 * k, paint);
        }
    }

    /** The finger let go: You stands there everywhere in the app, and the moment is worked out again. */
    private void walked() {
        moveTo(line.youLat, line.youLon);
    }

    /**
     * A joystick: push it to walk, up is towards the subject, sideways is sideways. The further it is pushed, the
     * faster you go (up to a fifth of the distance a second, at least 10 m/s), so near and far both move well.
     */
    private final class Joystick extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dx, dy;             // where the knob is, -1..1
        private boolean down;
        private final Runnable step = new Runnable() {
            @Override public void run() {
                if (!down) return;
                double speed = Math.max(10, Math.min(400, distance() * 0.2));   // metres a second at full push
                walk(-dy * speed * 0.05, dx * speed * 0.05);
                postDelayed(this, 50);
            }
        };

        Joystick(Context c) {
            super(c);
            setContentDescription("Joystick: push to walk");
            setClickable(true);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f, k = getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x26FFFFFF);
            c.drawCircle(w / 2, h / 2, r - k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * k);
            paint.setColor(0x66FFFFFF);
            c.drawCircle(w / 2, h / 2, r - k, paint);
            // the four ways, faint
            paint.setStrokeWidth(2 * k);
            float a = r * 0.72f, t = 6 * k;
            c.drawLine(w / 2 - t, h / 2 - a + t, w / 2, h / 2 - a, paint);
            c.drawLine(w / 2 + t, h / 2 - a + t, w / 2, h / 2 - a, paint);
            c.drawLine(w / 2 - t, h / 2 + a - t, w / 2, h / 2 + a, paint);
            c.drawLine(w / 2 + t, h / 2 + a - t, w / 2, h / 2 + a, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(down ? p.accent : 0xE6FFFFFF);
            c.drawCircle(w / 2 + dx * r * 0.55f, h / 2 + dy * r * 0.55f, r * 0.34f, paint);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    down = true;
                    removeCallbacks(step);
                    postDelayed(step, 50);
                    // fall through: the knob goes where the finger is
                case android.view.MotionEvent.ACTION_MOVE: {
                    float x = (e.getX() - getWidth() / 2f) / (r * 0.55f), y = (e.getY() - getHeight() / 2f) / (r * 0.55f);
                    float len = (float) Math.hypot(x, y);
                    if (len > 1) { x /= len; y /= len; }
                    dx = x;
                    dy = y;
                    invalidate();
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    down = false;
                    removeCallbacks(step);
                    dx = dy = 0;
                    invalidate();
                    walked();
                    return true;
            }
            return true;
        }
    }
}
