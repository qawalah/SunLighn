package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
    }

    private static final int N = 97;                 // sixteen hours of path, a point every ten minutes
    private static final long TEN_MIN = 600000L;

    private final Ui.Palette p;
    private final Host host;
    private final Line line;
    private final FrameView view;
    private final TimeRuler ruler;
    private final LinearLayout panel, topBar, lensBar;
    private final TextView headline, lightTag, sunRead, moonRead, lensLabel, hint;
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
            @Override public void onAimReset() {
                manualPitch = Double.NaN;
                if (live) setLive(false);
                update();
            }
        });
        addView(view, new LayoutParams(-1, -1));

        // The keys over the scene.
        topBar = new LinearLayout(c);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton close = key(Ui.Icon.BACK, "Back", "Back");
        close.setOnClickListener(v -> host.closeFrame());
        topBar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        liveKey = key(Ui.Icon.LIVE, "Compass", "Compass: follow the way the phone points");
        liveKey.setOnClickListener(v -> setLive(!live));
        Ui.IconButton turn = key(Ui.Icon.ROTATE, "Turn", "Turn the camera: landscape or portrait");
        turn.setOnClickListener(v -> {
            this.portrait = !this.portrait;
            manualPitch = Double.NaN;
            host.frameLens(this.focal, this.portrait);
            update();
        });
        Ui.IconButton tall = key(Ui.Icon.HEIGHT, "Height", "Subject height");
        tall.setOnClickListener(v -> askHeight());
        Ui.IconButton move = key(Ui.Icon.MOVE, "Move", "Move: drag where you stand");
        move.setOnClickListener(v -> { if (moveMap == null) openMove(); else closeMove(); });
        Ui.IconButton share = key(Ui.Icon.SHARE, "Share", "Share this moment");
        share.setOnClickListener(this::shareMenu);
        Ui.IconButton save = key(Ui.Icon.SAVED, "Save", "Save this shot");
        save.setOnClickListener(v -> host.saveShot(cursorMs));
        addView(topBar, new LayoutParams(-1, -2, Gravity.TOP));

        // The lens: pinch, or step through the usual ones.
        lensBar = new LinearLayout(c);
        lensBar.setGravity(Gravity.CENTER_VERTICAL);
        lensBar.setBackground(Ui.shape(c, 0xA60B1220, 0x33FFFFFF, 24, false));
        Ui.IconButton wider = new Ui.IconButton(c, p, Ui.Icon.MINUS, "Wider lens").bare();
        Ui.IconButton longer = new Ui.IconButton(c, p, Ui.Icon.PLUS, "Longer lens").bare();
        lensLabel = Ui.text(c, "", 13.5f, 700, 0xFFFFFFFF);
        lensLabel.setGravity(Gravity.CENTER);
        lensLabel.setMinWidth(Ui.dp(c, 140));
        wider.setOnClickListener(v -> stepLens(-1));
        longer.setOnClickListener(v -> stepLens(1));
        lensBar.addView(wider);
        lensBar.addView(lensLabel);
        lensBar.addView(longer);
        addView(lensBar, new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // The moment in words, the ruler of time and the jumps.
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        // The sheet stands apart from the scene: a lighter edge and a shadow (3.1.1).
        panel.setBackground(Ui.shape(c, p.panel, 0x40FFFFFF, 22, true));
        panel.setElevation(Ui.dp(c, 12));
        panel.setClickable(true);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        headline = Ui.text(c, "", 22, 700, p.text);
        head.addView(headline, new LinearLayout.LayoutParams(0, -2, 1f));
        lightTag = Ui.tag(c, "", p.amber);
        head.addView(lightTag);
        bell = new Ui.IconButton(c, p, Ui.Icon.BELL, "Remind me: keep this shot and remind me 30 minutes before").bare();
        bell.setOnClickListener(v -> host.planShoot(cursorMs, this::update));
        LinearLayout bellCol = new LinearLayout(c);           // every key over the viewfinder has its name under it
        bellCol.setOrientation(LinearLayout.VERTICAL);
        bellCol.setGravity(Gravity.CENTER_HORIZONTAL);
        bellCol.addView(bell);
        TextView bellName = Ui.text(c, "Remind me", 11, 600, p.text);
        bellName.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bellCol.addView(bellName);
        head.addView(bellCol);
        panel.addView(head, pad(0));

        sunRead = Ui.text(c, "", 14, 400, p.text);
        moonRead = Ui.text(c, "", 14, 400, p.text);
        moonGlyph = new Ui.GlyphView(c, Ui.GlyphView.Kind.MOON, p.violet, 22);
        panel.addView(readRow(new Ui.GlyphView(c, Ui.GlyphView.Kind.SUN, p.amber, 22), sunRead), pad(10));
        panel.addView(readRow(moonGlyph, moonRead), pad(6));

        ruler = new TimeRuler(c, p);
        ruler.setPlace(line.youLat, line.youLon, line.tz);
        ruler.setListener(ms -> {
            cursorMs = ms;
            update();
            host.frameTime(ms);
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(c, 12);
        panel.addView(ruler, rl);

        jumps = new LinearLayout(c);
        buildJumps();
        panel.addView(jumps, pad(6));

        hint = Ui.text(c, "Pinch to change the lens. Drag the sky sideways for time, up or down to tilt.", 12.5f, 400, p.muted);
        panel.addView(hint, pad(10));
        addView(panel, new LayoutParams(-1, -2, Gravity.BOTTOM));

        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        applyInsets();
        update();
        askGround();
    }

    /** A key over the scene with its name under it: an icon alone is a riddle the first time. */
    private Ui.IconButton key(Ui.Icon icon, String label, String description) {
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
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(c, 48), -2);   // seven keys fit a phone
        topBar.addView(col, l);
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
        topBar.setPadding(Ui.dp(getContext(), 4), insetTop + s, Ui.dp(getContext(), 4), 0);
        panel.setPadding(0, Ui.dp(getContext(), 16), 0, insetBottom + Ui.dp(getContext(), 14));
        place();
    }

    /** The scene ends where the panel begins; the lens bar floats just above the panel. */
    private void place() {
        int ph = panel.getHeight();
        LayoutParams l = (LayoutParams) lensBar.getLayoutParams();
        int want = ph + Ui.dp(getContext(), 12);
        if (l.bottomMargin != want) {
            l.bottomMargin = want;
            lensBar.setLayoutParams(l);
        }
        view.setInsets(insetTop, ph + Ui.dp(getContext(), 64));
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
            final long t = times[i];
            TextView b = Ui.smallButton(c, p, names[i]);
            b.setOnClickListener(v -> jump(t == 0 ? System.currentTimeMillis() : t));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (jumps.getChildCount() > 0) l.setMarginStart(Ui.dp(c, 8));
            jumps.addView(b, l);
        }
    }

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
            az = subjectAz();
            double body = line.mode == 0 ? (s[1] >= -1 ? s[1] : m[1]) : -1;   // behind you: nothing to aim up at
            double top = FrameMath.topAngle(line.height, distance(), rise());
            pitch = Double.isNaN(manualPitch) ? FrameMath.aimPitch(fovV, Math.max(body, top)) : manualPitch;
        }
        view.setLens(focal, portrait);
        view.setAim(az, pitch, live);
        view.setSubject(subjectAz(), distance(), line.height, line.kind, lineAz(), line.mode == 1, rise());
        view.setBodies(s[0], s[1], m[0], m[1], lit, Astro.moonWaxing(cursorMs));
        paths();
        marks();
        ruler.setTime(cursorMs);

        headline.setText(Fmt.when(c, cursorMs, line.tz));
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
        hint.setText(live ? "Compass is on: turn and tilt the phone to look around. Double tap the sky to aim at the subject again."
                : line.mode == 1 ? "The sun or moon is wanted behind you here, so it lights the subject from the front."
                : line.height <= 0 ? "Drag the ruler to move through time. Press Height (top) to draw the subject to scale."
                : "Drag the ruler or the sky to move through time. Pinch to change the lens. The bell plans this shoot.");
    }

    private String where(double[] pos, String extra) {
        String out = pos[1] >= 0 ? Math.round(pos[1]) + "° up, " + Math.round(pos[0]) % 360 + "° " + Astro.compass(pos[0])
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
        for (Astro.Hit h : Astro.hitsBetween(false, lat, lon, lineAz(), from, to)) {
            marks.add(new TimeRuler.Mark(h.timeMs, "Sun lined up", p.amber));
        }
        for (Astro.Hit h : Astro.hitsBetween(true, lat, lon, lineAz(), from, to)) {
            marks.add(new TimeRuler.Mark(h.timeMs, "Moon lined up", p.violet));
        }
        for (int k = 0; k < 2; k++) {
            long t = from;
            for (int guard = 0; guard < 8; guard++) {
                t = Astro.bodyPasses(false, lat, lon, -0.833, k == 1, t);
                if (t < 0 || t > to) break;
                marks.add(new TimeRuler.Mark(t, k == 1 ? "Sunset" : "Sunrise", p.muted));
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
            liveAz = Double.NaN;
            sensors.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
        } else if (sensors != null) {
            sensors.unregisterListener(this);
        }
        live = on;
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
        if (Double.isNaN(liveAz)) {
            liveAz = az;
            livePitch = alt;
        } else {                                    // smooth the needle so the picture does not shake
            liveAz = Astro.norm360(liveAz + FrameMath.diff(az, liveAz) * 0.18);
            livePitch += (alt - livePitch) * 0.18;
        }
        update();
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensors != null) sensors.unregisterListener(this);
    }

    // ---- the subject's height ----

    private AlertDialog dialog;

    /** True if a dialog was open and is now closed (Back closes the top thing first). */
    boolean closeDialog() {
        if (moveMap != null) {
            closeMove();
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

    private FrameLayout movePanel;
    private org.osmdroid.views.MapView moveMap;

    private void openMove() {
        Context c = getContext();
        movePanel = new FrameLayout(c);
        movePanel.setBackground(Ui.shape(c, p.panel, p.line, 22, true));
        movePanel.setClickable(true);
        moveMap = new org.osmdroid.views.MapView(c);
        moveMap.setMultiTouchControls(true);
        moveMap.setTilesScaledToDpi(true);
        moveMap.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        host.styleMap(moveMap);
        moveMap.setContentDescription("Map: drag You, or tap where you will stand");
        moveMap.getOverlays().add(new MoveOverlay());
        moveMap.getController().setZoom(16.0);
        moveMap.getController().setCenter(new org.osmdroid.util.GeoPoint((line.youLat + line.subLat) / 2,
                (line.youLon + line.subLon) / 2));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-1, -1);
        ml.topMargin = Ui.dp(c, 8);
        movePanel.addView(moveMap, ml);
        TextView tip = Ui.text(c, "Drag You (the pin) to where you will stand, or tap there", 13, 600, p.text);
        tip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        tip.setBackground(Ui.shape(c, Ui.withAlpha(p.panel, 230), p.line, 10, false));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tl.topMargin = Ui.dp(c, 16);
        movePanel.addView(tip, tl);
        TextView done = Ui.button(c, p, "Done", true);
        done.setOnClickListener(v -> closeMove());
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int m = Ui.dp(c, 16);
        dl.setMargins(m, 0, m, insetBottom + Ui.dp(c, 12));
        movePanel.addView(done, dl);
        int h = Math.max(Ui.dp(c, 260), getHeight() * 2 / 5);
        addView(movePanel, new FrameLayout.LayoutParams(-1, h > 0 ? h : Ui.dp(c, 300), Gravity.BOTTOM));
    }

    private void closeMove() {
        if (movePanel == null) return;
        moveMap.onDetach();
        removeView(movePanel);
        movePanel = null;
        moveMap = null;
    }

    /** How much higher the subject's foot is than your eye (the grounds, when known; else level). */
    private double rise() {
        return Heights.rise(getContext(), line.youLat, line.youLon, line.subLat, line.subLon);
    }

    /** Asks for the two grounds if they are not known; the scene redraws when they come. */
    private void askGround() {
        Heights.fetch(getContext(), new double[]{line.youLat, line.subLat}, new double[]{line.youLon, line.subLon},
                () -> { if (getParent() != null) update(); });
    }

    /** You stands here now: the scene, the paths and the answers follow. */
    void moveTo(double lat, double lon) {
        if (!host.moveYou(lat, lon)) {          // refused: the viewfinder and the map keep the same You
            if (moveMap != null) moveMap.invalidate();
            return;
        }
        line.youLat = lat;
        line.youLon = lon;
        askGround();
        pathCenter = marksCenter = Long.MIN_VALUE;
        ruler.setPlace(lat, lon, line.tz);
        manualPitch = Double.NaN;
        update();
        if (moveMap != null) moveMap.invalidate();
    }

    /** The line on the Move map: the subject, You (the pin) and the line between; You can be dragged. */
    private final class MoveOverlay extends org.osmdroid.views.overlay.Overlay {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private boolean dragging;
        private double dragLat, dragLon;

        @Override public void draw(android.graphics.Canvas c, org.osmdroid.views.MapView m, boolean shadow) {
            if (shadow) return;
            org.osmdroid.views.Projection pr = m.getProjection();
            android.graphics.Point s = pr.toPixels(new org.osmdroid.util.GeoPoint(line.subLat, line.subLon), null);
            android.graphics.Point y = pr.toPixels(new org.osmdroid.util.GeoPoint(dragging ? dragLat : line.youLat,
                    dragging ? dragLon : line.youLon), null);
            float k = getResources().getDisplayMetrics().density;
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(3 * k);
            paint.setColor(0xFF22C55E);
            c.drawLine(y.x, y.y, s.x, s.y, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(s.x, s.y, 9 * k, paint);
            paint.setColor(0xFF1D8CF8);
            c.drawCircle(s.x, s.y, 6.5f * k, paint);
            Ui.drawPin(c, y.x, y.y, 2f * k, dragging ? 10 * k : 0, 1f);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e, org.osmdroid.views.MapView m) {
            org.osmdroid.views.Projection pr = m.getProjection();
            android.graphics.Point y = pr.toPixels(new org.osmdroid.util.GeoPoint(line.youLat, line.youLon), null);
            float k = getResources().getDisplayMetrics().density;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // The pin's head sits above its point; a press near either picks it up.
                    if (Math.hypot(e.getX() - y.x, e.getY() - (y.y - 20 * k)) < 36 * k) {
                        dragging = true;
                        dragLat = line.youLat;
                        dragLon = line.youLon;
                        return true;
                    }
                    return false;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging) return false;
                    org.osmdroid.api.IGeoPoint g = pr.fromPixels((int) e.getX(), (int) (e.getY() + 20 * k));
                    dragLat = g.getLatitude();
                    dragLon = g.getLongitude();
                    m.invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!dragging) return false;
                    dragging = false;
                    moveTo(dragLat, dragLon);
                    return true;
            }
            return false;
        }

        @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e, org.osmdroid.views.MapView m) {
            org.osmdroid.api.IGeoPoint g = m.getProjection().fromPixels((int) e.getX(), (int) e.getY());
            moveTo(g.getLatitude(), g.getLongitude());
            return true;
        }
    }
}
