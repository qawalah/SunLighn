package dev.qawalah.sunlighn;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Plan a frame: you make the picture first, and the app works out where to stand and when.
 * In the viewfinder you drag the sun or the moon to where you want it, pinch the subject to the size you want
 * it, pick the lens and say how tall the subject is. From that: the subject's size gives the distance, the
 * body's height gives the time of day, and its place beside the subject gives the side to stand on.
 */
final class ComposeScreen extends FrameLayout {
    interface Host {
        void closeCompose();
        /** Stand at this spot (the picture was planned for it) and open the viewfinder at this time. */
        void composed(double lat, double lon, long timeMs, double focalMm, boolean portrait, double height, int kind,
                      double panDeg, double pitch);
        void say(String message);
        /** Ground.LAND, WATER, BUILDING or UNKNOWN at a point; called off the main thread. */
        int ground(double lat, double lon);
        /** On foot / Drone was switched here: remember it (Where to stand shows the same). */
        void droneChanged(boolean drone);
    }

    /** One answer: a run of days that share a spot. */
    private static final class Run {
        long first, last;
        double bodyAz;        // where the body is then
        double lat, lon;      // where to stand
        double bearing;       // from the subject to there
        double dist;          // how far, with the ground's height taken into account (3.2)
        double rise = -FrameMath.EYE;   // the subject's foot above your eye there
        int ground;           // what is there
    }

    private static final String[] SUN_LIGHTS = {"Sunset", "Sunrise"}, MOON_LIGHTS = {"Moonset", "Moonrise"};
    private static final String[] LOOK_IN = {"Next 2 weeks", "Next 6 weeks", "Next year"};
    private static final int[] LOOK_DAYS = {14, 42, 365};

    private final Ui.Palette p;
    private final Host host;
    private final FrameView view;
    private final LinearLayout topBar, lensBar, panel, kinds, runsRow;
    private final TextView headline, sub, lensLabel, hint, stand;
    private final double subLat, subLon;
    private final String subjectName;
    private final TimeZone tz;
    private boolean drone;          // On foot / Drone: the same setting as in Where to stand, chosen here too
    private final long from;

    // The picture
    private final boolean moonBody;          // the tool's body: the sun or the moon, never both
    private int light;                       // 0 setting, 1 rising
    private int lookIn = 1;                  // into LOOK_IN
    private boolean[] possible;              // per degree of height: can the body be there (Sky.possible)
    private int skyGeneration;
    private final Runnable skyLater = this::workOutSky;
    private double bodyAlt = 4;              // degrees above the horizon
    private double bodyOff = 0;              // degrees to the right of the subject (negative: left)
    private double panDeg = 0;               // the framing: the camera's bearing less the subject's (3.3)
    private double framePitch = Double.NaN;  // the framing's tilt, degrees; NaN: the usual tilt
    private View centre;                     // the Centre key, shown once the picture is framed
    private double topAngle;                 // how high the subject's top stands in the picture, degrees
    private double height;
    private int kind;
    private double focal;
    private boolean portrait;

    private final List<Run> runs = new ArrayList<>();
    private int pick;
    private boolean solving;
    private int generation;
    private int insetTop, insetBottom;
    private final Runnable solveLater = this::solve;

    private static final int N = 49;         // four hours of path either side, a point every ten minutes
    private final double[] pAz = new double[N], pAlt = new double[N], hidAz = new double[N], hidAlt = new double[N];
    private final String[] labels = new String[N];

    ComposeScreen(Context c, Host host, double subLat, double subLon, String subjectName, double height, int kind,
                  double focal, boolean portrait, TimeZone tz, boolean drone, long from, boolean moon) {
        super(c);
        this.moonBody = moon;
        this.p = new Ui.Palette(c, true);
        this.host = host;
        this.subLat = subLat;
        this.subLon = subLon;
        this.subjectName = subjectName;
        this.kind = kind;
        this.height = height > 0 ? height : FrameMath.KIND_HEIGHTS[kind];   // a usual height until you say
        this.focal = focal;
        this.portrait = portrait;
        this.tz = tz;
        this.drone = drone;
        this.from = from;
        this.topAngle = FrameMath.fovV(focal, portrait) * 0.2;              // the subject a fifth of the frame, to start
        this.bodyAlt = topAngle + 1;                                          // the sun just over its top, to start
        android.content.SharedPreferences fp = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        try {                                                                 // framed as it was left
            panDeg = Double.parseDouble(fp.getString("frame_pan", "0"));
            String fpp = fp.getString("frame_pitch", "");
            framePitch = fpp.isEmpty() ? Double.NaN : Double.parseDouble(fpp);
        } catch (NumberFormatException e) {
            panDeg = 0;
            framePitch = Double.NaN;
        }
        setBackgroundColor(0xFF05070D);
        setClickable(true);
        for (int i = 0; i < N; i++) hidAlt[i] = -90;

        view = new FrameView(c);
        view.setListener(new FrameView.Listener() {
            @Override public void onScrubPx(float dx) {}
            @Override public void onTilt(double pitch) {}
            @Override public void onFocal(double f, boolean settled) {}
            @Override public void onAimReset() {}
            @Override public void onBodyAt(double az, double alt, boolean settled) {
                // The body stops at the picture's edge, and never so high the subject must leave it (Ali, 3.3)
                double halfW = halfWidthDeg() * 0.85, fovV = FrameMath.fovV(ComposeScreen.this.focal, ComposeScreen.this.portrait);
                bodyOff = Math.max(panDeg - halfW, Math.min(panDeg + halfW, FrameMath.diff(az, aim())));
                double top = Double.isNaN(framePitch) ? fovV * 0.8 : framePitch + fovV / 2 * 0.8;
                bodyAlt = Math.max(0.3, Math.min(Math.min(60, top), alt));
                if (settled) keepPossible();
                changed(settled);
            }
            @Override public void onFrame(double dAz, double dPitch, boolean settled) {
                if (!settled) {
                    panDeg += dAz;
                    framePitch = (Double.isNaN(framePitch) ? autoPitch() : framePitch) + dPitch;
                    clampFrame();
                } else {
                    keepFrame();
                }
                draw();
            }
            @Override public void onSubjectScale(double factor, boolean settled) {
                double max = FrameMath.fovV(ComposeScreen.this.focal, ComposeScreen.this.portrait) * 0.9;
                topAngle = Math.max(0.2, Math.min(max, topAngle * factor));
                changed(settled);
                if (settled) skyChanged();
            }
        });
        addView(view, new LayoutParams(-1, -1));

        topBar = new LinearLayout(c);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        key(Ui.Icon.BACK, "Back", "Back: one step back").setOnClickListener(v -> host.closeCompose());
        topBar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        key(Ui.Icon.ROTATE, "Turn", "Turn the camera: landscape or portrait").setOnClickListener(v -> {
            this.portrait = !this.portrait;
            changed(true);
        });
        key(Ui.Icon.HEIGHT, "Height", "Subject height").setOnClickListener(v -> askHeight());
        Ui.IconButton c0 = key(Ui.Icon.LOCATE, "Centre", "Centre: put the subject back in the middle");
        centre = (View) c0.getParent();
        c0.setOnClickListener(v -> {
            panDeg = 0;
            framePitch = Double.NaN;
            keepFrame();
            draw();
        });
        addView(topBar, new LayoutParams(-1, -2, Gravity.TOP));

        lensBar = new LinearLayout(c);
        lensBar.setGravity(Gravity.CENTER_VERTICAL);
        lensBar.setBackground(Ui.shape(c, 0xA60B1220, 0x33FFFFFF, 24, false));
        Ui.IconButton wider = new Ui.IconButton(c, p, Ui.Icon.MINUS, "Wider lens").bare();
        Ui.IconButton longer = new Ui.IconButton(c, p, Ui.Icon.PLUS, "Longer lens").bare();
        lensLabel = Ui.text(c, "", 13.5f, 700, 0xFFFFFFFF);
        lensLabel.setGravity(Gravity.CENTER);
        lensLabel.setMinWidth(Ui.dp(c, 140));
        wider.setOnClickListener(v -> lens(-1));
        longer.setOnClickListener(v -> lens(1));
        lensBar.addView(wider);
        lensBar.addView(lensLabel);
        lensBar.addView(longer);
        addView(lensBar, new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        // The sheet stands apart from the scene: a lighter edge and a shadow (3.1.1).
        panel.setBackground(Ui.shape(c, p.panel, 0x40FFFFFF, 22, true));
        panel.setElevation(Ui.dp(c, 12));
        panel.setClickable(true);
        headline = Ui.text(c, "", 20, 700, p.text);
        panel.addView(headline, pad(0));
        sub = Ui.text(c, "", 14, 400, p.muted);
        panel.addView(sub, pad(4));

        kinds = new LinearLayout(c);
        kinds.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        LinearLayout ks = kinds;
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-1, -2);
        kl.topMargin = Ui.dp(c, 12);
        panel.addView(ks, kl);

        // On foot / Drone here as well: Build the shot said "Fly" with no way to change it (audit 3.2.2)
        footRow = new LinearLayout(c);
        footRow.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = Ui.dp(c, 10);
        panel.addView(footRow, fl);
        buildFoot();

        runsRow = new LinearLayout(c);
        runsRow.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        LinearLayout rs = runsRow;
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(c, 10);
        panel.addView(rs, rl);

        stand = Ui.button(c, p, drone ? "Fly here" : "Stand here", true);
        stand.setOnClickListener(v -> go());
        panel.addView(stand, pad(12));
        hint = Ui.text(c, "Drag the sun to place it. Drag the picture to frame it. Pinch to make the subject bigger or smaller. "
                + "The app finds the spot and the days that give you this picture.", 12.5f, 400, p.muted);
        panel.addView(hint, pad(10));
        addView(panel, new LayoutParams(-1, -2, Gravity.BOTTOM));
        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());

        applyInsets();
        buildKinds();
        draw();
        solve();
        skyChanged();
    }

    private Ui.IconButton key(Ui.Icon icon, String label, String description) {
        Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.IconButton b = new Ui.IconButton(c, p, icon, description).onScene();
        col.addView(b);
        TextView t = Ui.text(c, label, 11, 600, 0xFFFFFFFF);
        t.setShadowLayer(Ui.dp(c, 2), 0, Ui.dp(c, 1), 0xB3000000);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(c, 3);
        col.addView(t, tl);
        topBar.addView(col, new LinearLayout.LayoutParams(Ui.dp(c, 60), -2));
        return b;
    }

    private LinearLayout.LayoutParams pad(int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), topDp);
        l.setMarginStart(Ui.dp(getContext(), 16));
        l.setMarginEnd(Ui.dp(getContext(), 16));
        return l;
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyInsets();
    }

    private void applyInsets() {
        int s = Ui.dp(getContext(), 12);
        topBar.setPadding(s, insetTop + s, s, 0);
        panel.setPadding(0, Ui.dp(getContext(), 16), 0, insetBottom + Ui.dp(getContext(), 14));
        place();
    }

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

    private boolean moon() { return moonBody; }
    private boolean setting() { return light == 0; }

    private LinearLayout footRow;

    private void buildFoot() {
        footRow.removeAllViews();
        footRow.addView(Ui.segmented(getContext(), p, new String[]{"On foot", "Drone"}, drone ? 1 : 0, i -> {
            drone = i == 1;
            host.droneChanged(drone);
            stand.setText(drone ? "Fly here" : "Stand here");
            buildFoot();
            changed(true);
            skyChanged();
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildKinds() {
        Context c = getContext();
        kinds.removeAllViews();
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1f);
        kinds.addView(Ui.dropdown(c, p, "Light", moonBody ? MOON_LIGHTS : SUN_LIGHTS, light, k -> {
            light = k;
            buildKinds();
            changed(true);
            skyChanged();
        }), half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1f);
        half2.setMarginStart(Ui.dp(c, 8));
        kinds.addView(Ui.dropdown(c, p, "Look in", LOOK_IN, lookIn, k -> {
            lookIn = k;
            buildKinds();
            changed(true);
            skyChanged();
        }), half2);
    }

    // ---- where the body can never be: shaded in the picture ----

    /** Something that moves the spots changed (light, days, size, lens, height): work the sky out again, soon. */
    private void skyChanged() {
        removeCallbacks(skyLater);
        postDelayed(skyLater, 250);
    }

    private void workOutSky() {
        final int g = ++skyGeneration;
        final boolean moon = moon(), setting = setting();
        final double dist = distance(), off = bodyOff;
        final int days = LOOK_DAYS[lookIn];
        new Thread(() -> {
            boolean[] ok = Sky.possible(moon, setting, subLat, subLon, from, days, dist, off, drone,
                    (lat, lon) -> host.ground(lat, lon));
            post(() -> {
                if (g != skyGeneration || getParent() == null) return;
                possible = ok;
                view.setImpossible(ok, why(ok));
                words();
            });
        }).start();
    }

    /** One line on why the shaded heights cannot be: too high for these days, or no ground to stand on. */
    private String why(boolean[] ok) {
        int top = -1;
        for (int a = 0; a < ok.length; a++) if (ok[a]) top = a;
        boolean blockedBelow = false;
        for (int a = 0; a < top; a++) blockedBelow |= !ok[a];
        String body = moon() ? "moon" : "sun", light = setting() ? (moon() ? "moonset" : "sunset") : (moon() ? "moonrise" : "sunrise");
        if (top < 0) return drone ? "The " + body + " is not there at " + light + " in these days"
                : "Every spot for this is on water or in a building";
        if (blockedBelow) return "Never there, or every spot is on water or in a building";
        if (top < ok.length - 1) return "The " + body + " is never that high at " + light + " in these days";
        return null;
    }

    /** Dragged into a shaded height: let go at its edge, and say why. */
    private void keepPossible() {
        if (possible == null) return;
        int a = (int) Math.round(bodyAlt);
        if (a >= 0 && a < possible.length && possible[a]) return;
        int best = -1;
        for (int i = 0; i < possible.length; i++) {
            if (possible[i] && (best < 0 || Math.abs(i - bodyAlt) < Math.abs(best - bodyAlt))) best = i;
        }
        String why = why(possible);
        if (best < 0) {
            if (why != null) host.say(why + ".");
            return;
        }
        bodyAlt = Math.max(0.3, best);
        if (why != null) host.say(why + ".");
    }

    private void lens(int dir) {
        double before = FrameMath.fovV(focal, portrait);
        focal = FrameMath.stepFocal(focal, dir);
        // The picture stays the same picture: what filled a third of the old frame fills a third of the new one.
        double f = FrameMath.fovV(focal, portrait) / before;
        topAngle *= f;
        bodyAlt = Math.max(0.3, bodyAlt * f);
        bodyOff *= f;
        panDeg *= f;                                   // the framing scales with the picture too
        if (!Double.isNaN(framePitch)) framePitch *= f;
        changed(true);
        skyChanged();
    }

    /** The picture changed: draw it at once, and work out the spot again (a moment later, while fingers move). */
    private void changed(boolean settled) {
        draw();
        removeCallbacks(solveLater);
        if (settled) solve();
        else postDelayed(solveLater, 160);
    }

    /** How far to stand for this picture: the chosen day's spot once solved (its ground known), else on level ground. */
    private double distance() {
        // From the picture as it is now (a lens change or a pinch counts at once), with the chosen spot's ground.
        return clampDist(FrameMath.distanceFor(height, topAngle, rise()));
    }

    private double levelDistance() {
        return clampDist(FrameMath.distanceFor(height, topAngle, -FrameMath.EYE));
    }

    private static double clampDist(double d) {
        return Math.max(15, Math.min(30000, d));
    }

    private double rise() {
        return runs.isEmpty() ? -FrameMath.EYE : runs.get(Math.min(pick, runs.size() - 1)).rise;
    }

    /** Which way the camera looks: at the subject. Known once a day is found; until then, any way will do. */
    // ---- the framing (3.3): where the camera points, apart from where the subject and the sun are ----

    double panDeg() { return panDeg; }

    double framePitch() { return framePitch; }

    /** The body's place on the subject: {degrees right of it, degrees up}. */
    double[] bodyPlace() { return new double[]{bodyOff, bodyAlt}; }

    double halfWidthDeg() { return FrameMath.fovH(focal, portrait) / 2; }

    private double autoPitch() {
        return FrameMath.aimPitch(FrameMath.fovV(focal, portrait), Math.max(bodyAlt, topAngle));
    }

    /**
     * The shot stays in the picture: the subject and the sun (or moon) both inside, with a little room at the edges.
     * Framing moves the picture, never takes what you placed out of it.
     */
    private void clampFrame() {
        // Room at the edges in proportion to the picture: towards the corners a wide lens bends things outward, and
        // at a long lens a fixed margin would swallow the whole picture (review, 2026-11-15).
        double halfW = halfWidthDeg() * 0.85, halfV = FrameMath.fovV(focal, portrait) / 2 * 0.8;
        double lo = Math.max(-halfW, bodyOff - halfW), hi = Math.min(halfW, bodyOff + halfW);
        panDeg = lo <= hi ? Math.max(lo, Math.min(hi, panDeg)) : bodyOff / 2;
        if (Double.isNaN(framePitch)) return;
        double pLo = Math.max(-halfV, bodyAlt - halfV), pHi = Math.min(topAngle + halfV, bodyAlt + halfV);
        framePitch = pLo <= pHi ? Math.max(pLo, Math.min(pHi, framePitch)) : (bodyAlt + topAngle) / 2;
    }

    /** Remembered, so the picture is framed the same when you come back (and carried to Your shot). */
    private void keepFrame() {
        getContext().getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit()
                .putString("frame_pan", Double.toString(panDeg))
                .putString("frame_pitch", Double.isNaN(framePitch) ? "" : Double.toString(framePitch)).apply();
    }

    private double aim() {
        return runs.isEmpty() ? 180 : Astro.norm360(runs.get(Math.min(pick, runs.size() - 1)).bodyAz - bodyOff);
    }

    private void draw() {
        // Whatever changed (lens, Turn, pinch, a framing remembered from another time), the shot stays in the picture
        double wasPan = panDeg, wasPitch = framePitch;
        clampFrame();
        if (wasPan != panDeg || !(wasPitch == framePitch || (Double.isNaN(wasPitch) && Double.isNaN(framePitch)))) keepFrame();
        double fovV = FrameMath.fovV(focal, portrait), aim = aim();
        boolean solved = !runs.isEmpty();
        view.setLens(focal, portrait);
        view.setAim(aim + panDeg, Double.isNaN(framePitch) ? FrameMath.aimPitch(fovV, Math.max(bodyAlt, topAngle)) : framePitch, false);
        if (centre != null) centre.setVisibility(panDeg != 0 || !Double.isNaN(framePitch) ? VISIBLE : INVISIBLE);
        view.setSubject(aim, distance(), height, kind, aim + bodyOff, false, rise());
        view.setCompose(moon(), solved);
        long t = solved ? runs.get(Math.min(pick, runs.size() - 1)).first : from;
        double lit = Astro.moonLit(t);
        // The sky takes its colour from the sun: where it really is when the moon is placed, where you put it otherwise.
        double sunAlt = moon() ? Astro.sun(t, subLat, subLon)[1] : bodyAlt;
        if (moon()) view.setBodies(aim + 180, sunAlt, aim + bodyOff, bodyAlt, lit, Astro.moonWaxing(t));   // the sun behind you
        else view.setBodies(aim + bodyOff, bodyAlt, 0, -90, lit, true);
        lensLabel.setText(Math.round(focal) + " mm lens · " + (focal < 30 ? "wide" : focal < 70 ? "normal" : "long"));
        words();
    }

    private void words() {
        Context c = getContext();
        String who = subjectName == null ? "the subject" : subjectName;
        String body = moon() ? "moon" : "sun";
        if (runs.isEmpty()) {
            headline.setText(solving ? "Working it out…" : "The " + body + " never gets there here");
            sub.setText(solving ? "Finding where to stand for this picture." : "Put the " + body
                    + " lower in the picture, or try another light.");
            stand.setEnabled(false);
            stand.setAlpha(0.5f);
            runsRow.removeAllViews();
            return;
        }
        Run r = runs.get(Math.min(pick, runs.size() - 1));
        double bearing = Astro.bearing(subLat, subLon, r.lat, r.lon);
        headline.setText((drone ? "Fly " : "Stand ") + Fmt.distance(distance()) + " " + Astro.compass(bearing) + " of " + who);
        double dif = Heights.difference(c, r.lat, r.lon, subLat, subLon);
        String level = drone || Double.isNaN(dif) || Math.abs(dif) < 3 ? ""
                : " Your spot is " + Fmt.height(Math.abs(dif)) + (dif > 0 ? " lower" : " higher") + " than the subject's ground.";
        String ground = drone || r.ground == Ground.LAND || r.ground == Ground.UNKNOWN ? ""
                : r.ground == Ground.WATER ? " That spot is on water: pinch the subject bigger to stand nearer, or smaller to stand farther."
                : " That spot is inside a building: pinch the subject bigger to stand nearer, or smaller to stand farther.";
        // The light and the height then, not only "Sunset": a sun 9° up at 2:45 PM is daylight (Ali, 2026-11-15)
        String up = Math.round(bodyAlt) + "° up";
        String light = moon() ? up : Light.name(Light.of(bodyAlt)) + ", " + up;
        sub.setText(Fmt.days(c, r.first, r.last, tz) + " · " + Fmt.times(c, r.first, r.last, tz) + " · " + light + " · subject "
                + Fmt.height(height) + " tall." + level + ground);
        sub.setTextColor(ground.isEmpty() ? p.muted : p.bad);
        stand.setEnabled(true);
        stand.setAlpha(1f);
        String why = possible == null ? null : why(possible);
        hint.setText((why != null ? "Shaded red: " + Character.toLowerCase(why.charAt(0)) + why.substring(1) + ". " : "")
                + "Drag the " + body + " to place it. Drag the picture to frame it. Pinch to make the subject bigger or "
                + "smaller. The dotted curve is how the " + body + " moves through this picture.");
        runsRow.removeAllViews();
        if (runs.size() > 1) {
            String[] names = new String[runs.size()];
            for (int i = 0; i < names.length; i++) names[i] = Fmt.days(c, runs.get(i).first, runs.get(i).last, tz);
            runsRow.addView(Ui.dropdown(c, p, "Days", names, Math.min(pick, runs.size() - 1), idx -> {
                pick = idx;
                paths();
                draw();
                words();
            }), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /**
     * From the picture to the place. The body's height in the picture is its height in the sky, so the times are
     * the times it passes that height (going down for a sunset, up for a sunrise). At each, it stands at some
     * compass bearing; the camera must look bodyOff degrees to the left of that to see the subject, so you stand
     * on the opposite side of the subject. Days that give the same spot are one run.
     */
    private void solve() {
        removeCallbacks(solveLater);
        final int g = ++generation;
        final boolean moon = moon(), setting = setting();
        final double alt = bodyAlt, off = bodyOff, dist = levelDistance(), top = topAngle, tall = height;
        final Context app = getContext().getApplicationContext();
        solving = true;
        new Thread(() -> {
            List<Astro.Hit> hits = Astro.upcomingPasses(moon, subLat, subLon, alt, setting, from, LOOK_DAYS[lookIn]);
            List<Run> out = new ArrayList<>();
            double firstBearing = 0;
            for (Astro.Hit h : hits) {
                double az = (moon ? Astro.moon(h.timeMs, subLat, subLon) : Astro.sun(h.timeMs, subLat, subLon))[0];
                double bearing = Astro.norm360(az - off + 180);
                if (out.isEmpty() || Math.abs(FrameMath.diff(bearing, firstBearing)) > 1.0) {
                    if (out.size() == 6) break;
                    Run r = new Run();
                    r.first = r.last = h.timeMs;
                    r.bodyAz = az;
                    double[] at = Astro.destination(subLat, subLon, bearing, dist);
                    r.lat = at[0];
                    r.lon = at[1];
                    r.bearing = bearing;
                    r.dist = dist;
                    out.add(r);
                    firstBearing = bearing;
                } else {
                    out.get(out.size() - 1).last = h.timeMs;
                }
            }
            // The ground (3.2): a subject on higher ground looks taller, so for the same picture you stand farther
            // away. The spot moves, so its ground is asked again once; two rounds settle it.
            for (int round = 0; round < 2 && !out.isEmpty(); round++) {
                if (g != generation) return;
                double[] la = new double[out.size() + 1], lo = new double[out.size() + 1];
                for (int i = 0; i < out.size(); i++) {
                    la[i] = out.get(i).lat;
                    lo[i] = out.get(i).lon;
                }
                la[out.size()] = subLat;
                lo[out.size()] = subLon;
                Heights.fetchNow(app, la, lo);
                if (!Heights.known(app, subLat, subLon)) break;
                for (Run r : out) {
                    if (!Heights.known(app, r.lat, r.lon)) continue;
                    r.rise = Heights.rise(app, r.lat, r.lon, subLat, subLon);
                    r.dist = clampDist(FrameMath.distanceFor(tall, top, r.rise));
                    double[] at = Astro.destination(subLat, subLon, r.bearing, r.dist);
                    r.lat = at[0];
                    r.lon = at[1];
                }
            }
            if (!drone) for (Run r : out) {
                if (g != generation) return;
                r.ground = host.ground(r.lat, r.lon);
            }
            post(() -> {
                if (g != generation || getParent() == null) return;
                solving = false;
                runs.clear();
                runs.addAll(out);
                pick = Math.min(pick, Math.max(0, runs.size() - 1));
                paths();
                draw();
            });
        }).start();
    }

    /** How the body moves through the picture on the chosen day. */
    private void paths() {
        if (runs.isEmpty()) {
            view.setPaths(null, null, null, null, null);
            return;
        }
        long t0 = runs.get(Math.min(pick, runs.size() - 1)).first / 600000L * 600000L;
        java.text.SimpleDateFormat hf = Fmt.timeFormat(getContext(), tz);
        java.util.Calendar cal = java.util.Calendar.getInstance(tz);
        for (int i = 0; i < N; i++) {
            long t = t0 + (i - N / 2) * 600000L;
            double[] q = moon() ? Astro.moon(t, subLat, subLon) : Astro.sun(t, subLat, subLon);
            pAz[i] = q[0];
            pAlt[i] = q[1];
            cal.setTimeInMillis(t);
            labels[i] = cal.get(java.util.Calendar.MINUTE) == 0 ? hf.format(new java.util.Date(t)) : null;
        }
        if (moon()) view.setPaths(hidAz, hidAlt, pAz, pAlt, labels);
        else view.setPaths(pAz, pAlt, hidAz, hidAlt, labels);
    }

    private void go() {
        if (runs.isEmpty()) return;
        Run r = runs.get(Math.min(pick, runs.size() - 1));
        if (!drone && (r.ground == Ground.WATER || r.ground == Ground.BUILDING)) {
            host.say("That spot is no place to stand. Make the subject bigger or smaller to move it along the line.");
            return;
        }
        keepFrame();
        host.composed(r.lat, r.lon, r.first, focal, portrait, height, kind, panDeg, framePitch);
    }

    // ---- the subject's height: this picture cannot be planned without it ----

    private android.app.AlertDialog dialog;

    boolean closeDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null;
            return true;
        }
        return false;
    }

    private void askHeight() {
        dialog = HeightDialog.show(getContext(), p, height, kind, true, (h, k) -> {
            if (h > 0) height = h;
            kind = k;
            changed(true);
            skyChanged();
        });
    }
}
