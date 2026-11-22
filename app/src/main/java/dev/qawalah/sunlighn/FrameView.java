package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.Random;

/**
 * The viewfinder: the scene a camera pointed along your line would see. The sky takes its colour from the sun's
 * height and glows on the sun's side; the sun and the moon are where they really are, as big as they really are,
 * on the paths they travel; the subject stands on the horizon to scale. The bright corners mark what the lens
 * takes in; the dimmed part around it is what is just outside the picture.
 *
 * Touch: pinch changes the lens, a sideways drag moves through time, up and down tilts, a double tap aims again.
 */
final class FrameView extends View {
    interface Listener {
        void onScrubPx(float dxPx);
        void onTilt(double pitch);
        void onFocal(double focalMm, boolean settled);
        void onAimReset();
        /** Plan a frame: the body was dragged to this place in the sky. */
        default void onBodyAt(double az, double alt, boolean settled) {}
        /** Plan a frame: a pinch made the subject this many times bigger. */
        default void onSubjectScale(double factor, boolean settled) {}
        /** Build the shot: the picture was dragged (off the body): swing by dAz, tilt by dPitch, degrees (3.3). */
        default void onFrame(double dAzDeg, double dPitchDeg, boolean settled) {}
    }

    private final float k, sp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF frame = new RectF();
    private final RectF tmp = new RectF();
    private Listener listener;

    // The camera
    private double azCenter, pitch, focal = 35;
    private boolean portrait, live;
    // The sky now
    private double sunAz, sunAlt, moonAz, moonAlt, moonLit;
    private boolean moonWaxing;
    // The paths: one sample every ten minutes; a label on the samples that fall on a full hour
    private double[] pSunAz, pSunAlt, pMoonAz, pMoonAlt;
    private String[] pLabels;
    // The subject and the line
    private double subjectAz, subjectDist, subjectHeight;
    private double subjectRise = -FrameMath.EYE;   // the subject's foot above your eye, metres
    private int subjectKind;
    private double lineAz;
    private boolean lineIsBehind;      // the sun is wanted behind you: the line points away from the subject
    private int topInset, bottomInset;
    private float headroomDp = 88;              // room kept over the picture (the compass strip, the bar)
    // Plan a frame: the body is put where you want it (drag) and the subject made as big as you want it (pinch).
    private boolean compose, composeMoon, composeSolved;
    private boolean still;     // a small picture to look at (How you want it, Your shot): no touch, no labels (3.3)
    private int only = -1;     // the shot's body only: 0 the sun, 1 the moon; -1 both (owner, 3.4)
    private boolean ar;        // AR (3.5): the camera is behind; no drawn sky, land or dimmed edges
    private boolean cross;     // a cross in the middle, to point at a landmark while calibrating
    private boolean dragged;           // the body has been dragged once: "Drag the moon" has done its job

    // Stars: fixed points in the sky, faint, only to say "night".
    private static final int STARS = 260;
    private final float[] starAz = new float[STARS], starAlt = new float[STARS], starMag = new float[STARS];

    private final GestureDetector taps;
    private final ScaleGestureDetector pinch;
    private boolean pinching;
    private int axis;                  // 0 undecided, 1 sideways (time), 2 up/down (tilt)
    private float downX, downY, lastX, lastY;
    private float pxPerUnit, cx, cy;
    private boolean wasOnLine;

    FrameView(Context c) {
        super(c);
        k = Ui.dp(c, 1);
        sp = c.getResources().getDisplayMetrics().scaledDensity;
        text.setTypeface(Ui.font(c, 600));
        Random r = new Random(7);
        for (int i = 0; i < STARS; i++) {
            starAz[i] = r.nextFloat() * 360;
            starAlt[i] = (float) Math.toDegrees(Math.asin(r.nextFloat()));   // even over the dome
            starMag[i] = 0.25f + r.nextFloat() * r.nextFloat() * 0.75f;
        }
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) listener.onAimReset();
                return true;
            }
        });
        pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                pinching = true;
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (compose) {
                    if (listener != null) listener.onSubjectScale(d.getScaleFactor(), false);
                    return true;
                }
                double f = Math.max(FrameMath.FOCAL_MIN, Math.min(FrameMath.FOCAL_MAX, focal * d.getScaleFactor()));
                if (listener != null) listener.onFocal(f, false);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (compose) {
                    if (listener != null) listener.onSubjectScale(1, true);
                    return;
                }
                double snapped = FrameMath.snapFocal(focal);
                if (snapped != focal) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) listener.onFocal(snapped, true);
            }
        });
        setContentDescription("Viewfinder. Pinch to change the lens, drag sideways to change the time, "
                + "up or down to tilt, double tap to aim at the subject again.");
    }

    void setListener(Listener l) { listener = l; }

    // ---- buildings (3.14): walls standing on the land, from OpenStreetMap; optional (Settings, Buildings) ----

    private Buildings.Area houses;
    private double housesLat = Double.NaN, housesLon = Double.NaN;
    private double[][] hAz, hTop, hFoot;      // per building, per corner: bearing from you, top and foot heights (degrees)
    private double[] hNear;                   // per building: its nearest corner, metres
    private double[][] hE, hN;                // per building, per corner: metres east and north of you (shadows, 3.17)
    private double[] hH;                      // per building: its height, metres
    private final Path hPath = new Path(), hRim = new Path();

    /**
     * The buildings to draw from where you stand, null for none. The building you stand in and the subject's own are
     * left out (the subject is drawn as itself). Far ones first, so near ones stand in front.
     */
    void setBuildings(Buildings.Area a, double youLat, double youLon, double subLat, double subLon) {
        if (a == houses && youLat == housesLat && youLon == housesLon) return;
        houses = a;
        housesLat = youLat;
        housesLon = youLon;
        hAz = hTop = hFoot = null;
        hNear = null;
        hE = hN = null;
        hH = null;
        if (a != null) {
            double pe = a.east(youLon), pn = a.north(youLat), se = a.east(subLon), sn = a.north(subLat);
            java.util.List<Integer> keep = new java.util.ArrayList<>();
            java.util.List<double[]> az = new java.util.ArrayList<>(), top = new java.util.ArrayList<>(), foot = new java.util.ArrayList<>();
            java.util.List<Double> near = new java.util.ArrayList<>();
            java.util.List<double[]> ee = new java.util.ArrayList<>(), nn = new java.util.ArrayList<>();
            java.util.List<Double> hh = new java.util.ArrayList<>();
            for (Buildings.B b : a.list) {
                if (Buildings.inside(b, pe, pn) || Buildings.inside(b, se, sn)
                        || Buildings.distanceTo(b, se, sn) < Buildings.SUBJECT_WITHIN) continue;
                int corners = b.e.length;
                double[] z = new double[corners], t = new double[corners], f = new double[corners];
                double[] ce = new double[corners], cn = new double[corners];
                double least = Double.MAX_VALUE;
                for (int i = 0; i < corners; i++) {
                    double de = b.e[i] - pe, dn = b.n[i] - pn, d = Math.max(1, Math.hypot(de, dn));
                    ce[i] = de;
                    cn[i] = dn;
                    z[i] = Astro.norm360(Math.toDegrees(Math.atan2(de, dn)));
                    t[i] = Buildings.topAlt(b.height, d);
                    f[i] = Buildings.footAlt(d);
                    least = Math.min(least, d);
                }
                if (least > Buildings.NEAR && b.height < 40) continue;
                az.add(z);
                top.add(t);
                foot.add(f);
                near.add(least);
                ee.add(ce);
                nn.add(cn);
                hh.add(b.height);
                keep.add(keep.size());
            }
            Integer[] order = keep.toArray(new Integer[0]);
            java.util.Arrays.sort(order, (x, y) -> Double.compare(near.get(y), near.get(x)));
            int n = order.length;
            hAz = new double[n][];
            hTop = new double[n][];
            hFoot = new double[n][];
            hNear = new double[n];
            hE = new double[n][];
            hN = new double[n][];
            hH = new double[n];
            for (int i = 0; i < n; i++) {
                hE[i] = ee.get(order[i]);
                hN[i] = nn.get(order[i]);
                hH[i] = hh.get(order[i]);
                hAz[i] = az.get(order[i]);
                hTop[i] = top.get(order[i]);
                hFoot[i] = foot.get(order[i]);
                hNear[i] = near.get(order[i]);
            }
        }
        invalidate();
    }

    /** The buildings, far ({@code far}: beyond the subject) or near: one flat silhouette each with a faint lit top. */
    private void drawBuildings(Canvas c, boolean far) {
        if (hAz == null || ar || compose) return;
        double half = Math.toDegrees(Math.atan(getWidth() / 2f / pxPerUnit)) + 4;
        int sky = horizonColor();
        for (int b = 0; b < hAz.length; b++) {
            if (far != (hNear[b] > subjectDist)) continue;
            double[] az = hAz[b], top = hTop[b], foot = hFoot[b];
            int corners = az.length;
            boolean left = true, right = true;
            for (double z : az) {
                double d = FrameMath.diff(z, azCenter);
                if (d > -half) left = false;
                if (d < half) right = false;
            }
            if (left || right) continue;                      // wholly outside the picture
            hPath.reset();
            hRim.reset();
            for (int i = 0; i < corners; i++) {
                int j = (i + 1) % corners;
                float[] b1 = at(az[i], foot[i]), t1 = at(az[i], top[i]), t2 = at(az[j], top[j]), b2 = at(az[j], foot[j]);
                if (b1 == null || t1 == null || t2 == null || b2 == null) continue;
                // one wall; all turned the same way, so walls that overlap fill together (no holes)
                double area = (t1[0] - b1[0]) * (t1[1] + b1[1]) + (t2[0] - t1[0]) * (t2[1] + t1[1])
                        + (b2[0] - t2[0]) * (b2[1] + t2[1]) + (b1[0] - b2[0]) * (b1[1] + b2[1]);
                if (area >= 0) {
                    hPath.moveTo(b1[0], b1[1]); hPath.lineTo(t1[0], t1[1]); hPath.lineTo(t2[0], t2[1]); hPath.lineTo(b2[0], b2[1]);
                } else {
                    hPath.moveTo(b2[0], b2[1]); hPath.lineTo(t2[0], t2[1]); hPath.lineTo(t1[0], t1[1]); hPath.lineTo(b1[0], b1[1]);
                }
                hPath.close();
                hRim.moveTo(t1[0], t1[1]);
                hRim.lineTo(t2[0], t2[1]);
            }
            if (hPath.isEmpty()) continue;
            // far ones take some of the sky's haze; near ones are nearly the land's darkest
            float haze = (float) Math.min(0.55, hNear[b] / 3000.0);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mix(mix(groundFar(), 0xFF000000, 0.35), sky, haze));
            c.drawPath(hPath, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, 0.8f * k));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Ui.withAlpha(mix(sky, 0xFFFFFFFF, 0.4), 70));
            c.drawPath(hRim, paint);
        }
    }

    /**
     * Shadows (3.17): the shot's body (the sun unless the shot is the moon's) throws the subject's and the buildings'
     * shadows away from it on the ground, in one soft dark shape (overlaps do not double). Not in AR (the camera has
     * its own) or in Build the shot's planning sky; the strength fades as the body comes down.
     */
    private void drawShadows(Canvas c) {
        try {
            drawShadowsNow(c);
        } catch (RuntimeException e) {          // shadows are a nicety: never the reason the viewfinder ends
            CrashLog.caught(getContext(), "drawShadows", e);
            sPath.reset();
        }
    }

    private void drawShadowsNow(Canvas c) {
        if (ar || compose || !Shadows.on(getContext())) return;
        boolean byMoon = only == 1;
        double az = byMoon ? moonAz : sunAz, alt = byMoon ? moonAlt : sunAlt;
        double s = Shadows.strength(alt, byMoon);
        if (s <= 0) return;
        sPath.reset();
        sPath.setFillType(Path.FillType.WINDING);
        if (hE != null) {
            for (int b = 0; b < hE.length; b++) {
                double[] v = Shadows.vector(az, alt, hH[b]);
                if (v == null) continue;
                double[][] hull = Shadows.hull(hE[b], hN[b], v[0], v[1]);
                addGround(hull[0], hull[1], Double.NaN);
            }
        }
        if (subjectHeight > 0 && subjectDist > 0) {
            double[] v = Shadows.vector(az, alt, subjectHeight);
            if (v != null) {
                double ra = Math.toRadians(subjectAz);
                double[][] q = Shadows.flat(Math.sin(ra) * subjectDist, Math.cos(ra) * subjectDist, subjectAz,
                        subjectHeight * FrameMath.KIND_WIDTHS[subjectKind], v[0], v[1]);
                addGround(q[0], q[1], subjectRise);
            }
        }
        if (sPath.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (112 * s), 0, 0, 0));
        c.drawPath(sPath, paint);
    }

    private final Path sPath = new Path();

    /** One shadow outline on the ground; {@code rise} is the subject's ground (NaN: yours, level with you). */
    private void addGround(double[] es, double[] ns, double rise) {
        int m = es.length;
        float[] xs = new float[m], ys = new float[m];
        for (int i = 0; i < m; i++) {
            double d = Math.max(1, Math.hypot(es[i], ns[i]));
            double a = Astro.norm360(Math.toDegrees(Math.atan2(es[i], ns[i])));
            float[] q = at(a, Double.isNaN(rise) ? Buildings.footAlt(d) : FrameMath.baseAngle(rise, d));
            if (q == null) return;                       // behind the camera: left out
            xs[i] = q[0];
            ys[i] = q[1];
        }
        boolean out = true;
        for (int i = 0; i < m && out; i++) if (xs[i] > -getWidth() && xs[i] < 2f * getWidth()) out = false;
        if (out) return;
        double area = 0;
        for (int i = 0; i < m; i++) { int j = (i + 1) % m; area += xs[i] * ys[j] - xs[j] * ys[i]; }
        for (int k2 = 0; k2 < m; k2++) {
            int i = area >= 0 ? k2 : m - 1 - k2;
            if (k2 == 0) sPath.moveTo(xs[i], ys[i]); else sPath.lineTo(xs[i], ys[i]);
        }
        sPath.close();
    }

    private boolean[] impossible;     // per whole degree of height: false where the body can never be
    private String impossibleWhy;

    /** Build the shot: shade the heights where the body can never be, and say why once. Null clears it. */
    void setImpossible(boolean[] byDegree, String why) {
        impossible = byDegree;
        impossibleWhy = why;
        boolean any = false;
        if (byDegree != null) for (boolean b : byDegree) any |= !b;
        setContentDescription(any && why != null ? "Impossible: " + why : "Build the shot. Drag the "
                + (composeMoon ? "moon" : "sun") + " to where you want it. Pinch to make the subject bigger or smaller.");
        invalidate();
    }

    /** Plan a frame: only the chosen body shows, it can be dragged, and a pinch sizes the subject. */
    /** Only the shot's body: nothing about the other (owner, 3.4: "I do not care where the moon is"). */
    void setOnly(boolean moon) {
        only = moon ? 1 : 0;
        invalidate();
    }

    /** AR: the live camera shows behind, so the scene draws only what the camera cannot (the frame fills the view). */
    void setAr(boolean on) {
        ar = on;
        invalidate();
    }

    boolean isAr() { return ar; }

    void setCross(boolean on) {
        cross = on;
        invalidate();
    }

    boolean showsCross() { return cross; }

    boolean showsSun() { return only != 1; }

    boolean showsMoon() { return only != 0; }

    /** A still picture: the frame fills the view, nothing to grab, no compass, scale or labels. */
    private boolean free;      // Now (3.24): no subject and no line, only the sky, the land, the buildings and the body

    void setFree(boolean on) {
        free = on;
        invalidate();
    }

    void setStill(boolean on) {
        still = on;
        setClickable(false);
        invalidate();
    }

    /** The height of the body being shown (the sun, or the moon when it is the moon's picture), degrees. */
    double bodyAlt() { return compose && composeMoon ? moonAlt : sunAlt; }

    void setCompose(boolean moon, boolean solved) {
        compose = true;
        composeMoon = moon;
        composeSolved = solved;
        if (impossible == null && !still) setContentDescription("Build the shot. Drag the " + (moon ? "moon" : "sun")
                + " to where you want it. Pinch to make the subject bigger or smaller.");
        invalidate();
    }

    /** Room kept over the picture inside the top inset (3.16: the Answer band has none; the 3.8 screen 88 dp). */
    void setHeadroom(float dp) {
        headroomDp = dp;
        invalidate();
    }

    void setInsets(int top, int bottom) {
        topInset = top;
        bottomInset = bottom;
        invalidate();
    }

    void setLens(double focalMm, boolean portrait) {
        this.focal = focalMm;
        this.portrait = portrait;
        invalidate();
    }

    void setAim(double azCenter, double pitch, boolean live) {
        this.azCenter = azCenter;
        this.pitch = pitch;
        this.live = live;
        invalidate();
    }

    double focal() { return focal; }

    void setBodies(double sunAz, double sunAlt, double moonAz, double moonAlt, double moonLit, boolean waxing) {
        this.sunAz = sunAz;
        this.sunAlt = sunAlt;
        this.moonAz = moonAz;
        this.moonAlt = moonAlt;
        this.moonLit = moonLit;
        this.moonWaxing = waxing;
        boolean on = !compose && ((showsSun() && onLine(sunAz, sunAlt)) || (showsMoon() && onLine(moonAz, moonAlt)));
        if (on && !wasOnLine) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        wasOnLine = on;
        invalidate();
    }

    void setPaths(double[] sunAz, double[] sunAlt, double[] moonAz, double[] moonAlt, String[] labels) {
        pSunAz = sunAz;
        pSunAlt = sunAlt;
        pMoonAz = moonAz;
        pMoonAlt = moonAlt;
        pLabels = labels;
        invalidate();
    }

    void setSubject(double az, double metres, double height, int kind, double lineAz, boolean lineIsBehind) {
        setSubject(az, metres, height, kind, lineAz, lineIsBehind, -FrameMath.EYE);
    }

    /** The same, with the subject's foot {@code rise} metres above your eye (negative: you stand higher). */
    void setSubject(double az, double metres, double height, int kind, double lineAz, boolean lineIsBehind, double rise) {
        subjectRise = rise;
        subjectAz = az;
        subjectDist = metres;
        subjectHeight = height;
        subjectKind = kind;
        this.lineAz = lineAz;
        this.lineIsBehind = lineIsBehind;
        invalidate();
    }

    private boolean onLine(double az, double alt) {
        return !compose && !free && alt >= -1 && Math.abs(FrameMath.diff(az, lineAz)) < 0.3;
    }

    // ---- sky colours by the sun's height: night, twilight, golden light, day ----

    private static final double[] KEY_ALT = {-18, -12, -6, -1, 3, 10, 30};
    // Night is a deep blue with a glow at the horizon, never black: the land and the subject must read against it
    // (3.1.1, the owner found the all-black scene unreadable).
    private static final int[] KEY_HORIZON = {0xFF1E2A50, 0xFF2A3A6E, 0xFF8A4B6E, 0xFFF29A5C, 0xFFFFC27A, 0xFFFFE2B0, 0xFFCFE6FF};
    private static final int[] KEY_ZENITH = {0xFF0A1330, 0xFF0F1B42, 0xFF13224F, 0xFF22407A, 0xFF3F74C2, 0xFF4C8BE0, 0xFF3E86E8};
    // Build the shot plans, it does not judge exposure: a calm blue-grey sky at any hour, the same every time.
    private static final int PLAN_HORIZON = 0xFF7E90B8, PLAN_ZENITH = 0xFF33466E;
    private static final int PLAN_GROUND_FAR = 0xFF27324C, PLAN_GROUND_NEAR = 0xFF161D2F;
    // The bodies' own colours, for their paths, rings and grips.
    static final int SUN_INK = 0xFFFFD166, MOON_INK = 0xFFC4B5FD;

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int keyed(int[] colors, double alt) {
        if (alt <= KEY_ALT[0]) return colors[0];
        for (int i = 1; i < KEY_ALT.length; i++) {
            if (alt <= KEY_ALT[i]) return mix(colors[i - 1], colors[i], (alt - KEY_ALT[i - 1]) / (KEY_ALT[i] - KEY_ALT[i - 1]));
        }
        return colors[colors.length - 1];
    }

    private int skyAt(double elevation) {
        double t = Math.max(0, Math.min(1, elevation / 55.0));
        return mix(horizonColor(), compose ? PLAN_ZENITH : keyed(KEY_ZENITH, sunAlt), Math.sqrt(t));
    }

    private int horizonColor() { return compose ? PLAN_HORIZON : keyed(KEY_HORIZON, sunAlt); }
    private int groundFar() { return compose ? PLAN_GROUND_FAR : mix(horizonColor(), 0xFF05070D, 0.62); }
    private int groundNear() { return compose ? PLAN_GROUND_NEAR : mix(horizonColor(), 0xFF020308, 0.92); }

    /** The height above the horizon that a row of the picture looks at, in degrees (at the middle of the view). */
    private double elevationAt(float y) {
        return pitch + Math.toDegrees(Math.atan((cy - y) / pxPerUnit));
    }

    private float[] at(double az, double alt) {
        double[] q = FrameMath.project(az, alt, azCenter, pitch);
        if (q == null) return null;
        return new float[]{cx + (float) q[0] * pxPerUnit, cy + (float) q[1] * pxPerUnit};
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // The picture: as large as fits between the bars at the top and the panel at the bottom.
        float aspect = portrait ? 2f / 3f : 3f / 2f;
        float availTop = still || ar ? 0 : topInset + headroomDp * k, availBottom = still ? h : ar ? h : h - bottomInset - 12 * k;
        float fw = ar ? w : still ? Math.min(w, (availBottom - availTop) * aspect) : Math.min(w - 24 * k, (availBottom - availTop) * aspect);
        float fh = fw / aspect;
        cx = w / 2f;
        cy = availTop + (availBottom - availTop) / 2f;
        frame.set(cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2);
        pxPerUnit = (float) (fw / (FrameMath.sensorWidth(portrait) / focal));

        if (!ar) drawSky(c, w, h);
        float[] hz = at(azCenter, 0);
        float yH = hz == null ? (pitch > 0 ? h + 1000 : -1000) : hz[1];
        if (!ar) drawStars(c);
        if (compose) drawImpossible(c, w, h);
        if (showsMoon()) drawPath(c, pMoonAz, pMoonAlt, MOON_INK, !showsSun());
        if (showsSun()) drawPath(c, pSunAz, pSunAlt, SUN_INK, true);
        if (!compose && !still && !free) drawLine(c, yH);

        float[] sun = at(sunAz, sunAlt), moon = at(moonAz, moonAlt);
        float least = (compose ? 7 : 5) * k;     // a speck at a wide lens: big enough to see and to grab
        float sunR = Math.max(least, (float) (Math.tan(Math.toRadians(0.265)) * pxPerUnit));
        float moonR = Math.max(least, (float) (Math.tan(Math.toRadians(0.26)) * pxPerUnit));
        if (showsMoon() && moon != null && moonAlt > -2) drawMoon(c, moon[0], moon[1], moonR);
        if (showsSun() && sun != null && sunAlt > -2) drawSun(c, sun[0], sun[1], sunR);

        if (!ar) {
            drawGround(c, w, h, yH, sun);
            drawShadows(c);                       // on the ground, under everything that stands on it
            drawBuildings(c, true);               // beyond the subject: behind it
            if (!free) drawSubject(c, yH);
            drawBuildings(c, false);              // between you and the subject: in front of it
        } else if (!free) {
            drawSubjectMarker(c);                 // the camera shows the real subject: only where it is
        }

        if (showsSun() && sun != null && onLine(sunAz, sunAlt)) ring(c, sun[0], sun[1], sunR, SUN_INK);
        if (showsMoon() && moon != null && onLine(moonAz, moonAlt)) ring(c, moon[0], moon[1], moonR, MOON_INK);
        if (still) {
            // nothing to grab, nothing to read: the picture only
        } else if (compose) {
            // A grip round the body says it can be moved.
            float[] q = composeMoon ? moon : sun;
            if (q != null) {
                float r = (composeMoon ? moonR : sunR) + 12 * k;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setColor(composeMoon ? MOON_INK : SUN_INK);
                paint.setPathEffect(new DashPathEffect(new float[]{4 * k, 5 * k}, 0));
                c.drawCircle(q[0], q[1], r, paint);
                paint.setPathEffect(null);
                if (!dragged) label(c, composeMoon ? "Drag the moon" : "Drag the sun", q[0], q[1] - r - 10 * k, 0xFFFFFFFF, 12);
            }
        } else {
            if (showsSun()) tag(c, "Sun", sunAz, sunAlt, sun, sunR, SUN_INK, 0.19f);
            if (showsMoon()) tag(c, "Moon", moonAz, moonAlt, moon, moonR, 0xFFD9DEF2, 0.31f);
        }

        if (!ar) drawOutside(c, w, h);
        if (cross) drawCross(c);
        if (still) return;
        if (!compose || composeSolved) drawCompass(c, w);
        drawHeights(c, yH);
        if (compose) drawImpossibleWhy(c, composeMoon ? moon : sun, composeMoon ? moonR : sunR);
    }

    /** AR: a small ring where the subject's foot is, named. */
    private void drawSubjectMarker(Canvas c) {
        if (Math.abs(FrameMath.diff(subjectAz, azCenter)) > 80) return;
        float[] q = at(subjectAz, FrameMath.baseAngle(subjectRise, subjectDist));
        if (q == null) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * k);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(q[0], q[1], 9 * k, paint);
        label(c, "Subject", q[0], q[1] + 24 * k, 0xFFFFFFFF, 12);
    }

    /** The cross to point at a landmark while calibrating the compass. */
    private void drawCross(Canvas c) {
        float x = getWidth() / 2f, y = frame.centerY(), r = 22 * k;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f * k);
        paint.setColor(0x99000000);
        c.drawLine(x - r, y, x + r, y, paint);
        c.drawLine(x, y - r, x, y + r, paint);
        paint.setStrokeWidth(2 * k);
        paint.setColor(0xFFFFFFFF);
        c.drawLine(x - r, y, x + r, y, paint);
        c.drawLine(x, y - r, x, y + r, paint);
    }

    /** The heights the body can never reach in the picture: red lines across them, the sky still seen through. */
    private void drawImpossible(Canvas c, int w, int h) {
        if (impossible == null) return;
        Path bands = new Path();
        for (int a = 0; a < impossible.length; a++) {
            if (impossible[a]) continue;
            float[] lo = at(azCenter, Math.max(-1, a - 0.5)), hi = at(azCenter, a + 0.5);
            if (lo == null || hi == null) continue;
            float top = a == impossible.length - 1 ? 0 : hi[1];    // the last degree shades everything above it
            bands.addRect(0, Math.max(0, top), w, Math.min(h, lo[1]), Path.Direction.CW);
        }
        if (bands.isEmpty()) return;
        c.save();
        c.clipPath(bands);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x38E5484D);          // red, so "shaded red" under the picture is what you see
        c.drawRect(0, 0, w, h, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * k);
        paint.setColor(0x99F87171);
        float gap = 10 * k;
        for (float x = -h; x < w; x += gap) c.drawLine(x, h, x + h, 0, paint);
        c.restore();
        // The reason is drawn last, over the picture (drawImpossibleWhy).
    }

    /**
     * 3.22: why the sky is shaded, on the shading (it was a red line under every answer): near the top of the picture,
     * drawn last so the subject and the sun never cover it, and moved off the sun if it sits there.
     */
    private void drawImpossibleWhy(Canvas c, float[] body, float bodyR) {
        if (impossible == null || impossibleWhy == null) return;
        // The shaded band that shows most in the picture (a band can sit between heights that are possible: the
        // highest ones count as possible when no sunset passes them, 3.22.2 on the phone)
        float bestTop = 0, bestBottom = 0;
        boolean above = false;   // 3.23 (phase 3, B-08): the strip just above the picture is shaded (it often is)
        for (int a = 0; a < impossible.length; a++) {
            if (impossible[a]) continue;
            int e = a;
            while (e + 1 < impossible.length && !impossible[e + 1]) e++;
            float[] lo = at(azCenter, Math.max(-1, a - 0.5)), hi = at(azCenter, e + 0.5);
            float lowY = lo == null ? frame.bottom : lo[1];
            float upY = e == impossible.length - 1 || hi == null ? -1e6f : hi[1];   // the last degree shades all above
            if (upY <= frame.top - 30 * k && lowY >= frame.top - 2 * k) above = true;
            float top = Math.max(frame.top, upY), bottom = Math.min(frame.bottom, lowY);
            if (bottom - top > bestBottom - bestTop) {
                bestTop = top;
                bestBottom = bottom;
            }
            a = e;
        }
        // Under the compass numbers when the band starts at the picture's top, never past the band's lower edge
        float y = bestTop + (bestTop <= frame.top + 1 ? 42 * k : 16 * k), bottom = bestBottom - 12 * k;
        if (body != null && Math.abs(body[1] - y) < bodyR + 40 * k) y = body[1] + bodyR + 40 * k;
        if (bestBottom - bestTop < 28 * k || y > bottom) {
            // No room inside the picture: in the shading just above it, under the keys
            y = frame.top - 16 * k;
            if (!above || y - 12 * k < topInset + headroomDp * k - 4 * k) return;
        }
        text.setTextSize(12 * sp);
        // Clear of the heights scale on the right: the short words when the long ones would reach it
        String s = text.measureText(impossibleWhy) + 20 * k < frame.width() - 2 * 48 * k ? impossibleWhy
                : "The " + (composeMoon ? "moon" : "sun") + " can't be here";
        pill(c, s, frame.centerX(), y, 0xD92A0E10, 0xFFFECACA);
    }

    private void drawSky(Canvas c, int w, int h) {
        int n = 7;
        int[] colors = new int[n];
        float[] stops = new float[n];
        for (int i = 0; i < n; i++) {
            stops[i] = i / (float) (n - 1);
            colors[i] = skyAt(Math.max(0, elevationAt(h * stops[i])));
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, colors, stops, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // The sky is brighter and warmer on the sun's side, most of all when the sun is low.
        if (sunAlt > -10 && !compose) {
            float[] s = at(sunAz, Math.max(sunAlt, -2));
            if (s != null) {
                double low = Math.max(0, Math.min(1, (25 - sunAlt) / 25));
                double up = Math.max(0, Math.min(1, (sunAlt + 10) / 10));
                int a = (int) (150 * up * (0.45 + 0.55 * low));
                int warm = mix(0xFFFFF1D0, 0xFFFF9A4D, low);
                float r = (float) Math.max(w * 0.9, pxPerUnit * Math.tan(Math.toRadians(14)));
                paint.setShader(new RadialGradient(s[0], s[1], r,
                        new int[]{Ui.withAlpha(warm, a), Ui.withAlpha(warm, a / 3), Ui.withAlpha(warm, 0)},
                        new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            }
        }
    }

    private void drawStars(Canvas c) {
        if (sunAlt > -5 || compose) return;
        float dark = (float) Math.min(1, (-5 - sunAlt) / 9);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < STARS; i++) {
            if (Math.abs(FrameMath.diff(starAz[i], azCenter)) > 100) continue;
            float[] q = at(starAz[i], starAlt[i]);
            if (q == null || q[0] < 0 || q[0] > getWidth() || q[1] < 0 || q[1] > getHeight()) continue;
            float low = Math.min(1, starAlt[i] / 12f);          // haze near the horizon hides them
            paint.setColor(Color.argb((int) (230 * dark * starMag[i] * low), 255, 255, 255));
            c.drawCircle(q[0], q[1], (0.6f + starMag[i]) * k, paint);
        }
    }

    /** Where a body travels: a fine dashed curve with a dot and the time on every full hour. */
    private void drawPath(Canvas c, double[] az, double[] alt, int color, boolean labels) {
        if (az == null) return;
        path.reset();
        boolean pen = false;
        for (int i = 0; i < az.length; i++) {
            float[] q = alt[i] < -1.5 || Math.abs(FrameMath.diff(az[i], azCenter)) > 85 ? null : at(az[i], alt[i]);
            if (q == null) { pen = false; continue; }
            if (!pen) { path.moveTo(q[0], q[1]); pen = true; } else path.lineTo(q[0], q[1]);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2 * k);
        paint.setColor(Ui.withAlpha(color, 215));
        paint.setPathEffect(new DashPathEffect(new float[]{1.5f * k, 5 * k}, 0));
        c.drawPath(path, paint);
        paint.setPathEffect(null);
        text.setTextSize(11 * sp);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < az.length; i++) {
            if (pLabels == null || pLabels[i] == null || alt[i] < 0) continue;
            if (Math.abs(FrameMath.diff(az[i], azCenter)) > 85) continue;
            float[] q = at(az[i], alt[i]);
            if (q == null) continue;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(color, 230));
            c.drawCircle(q[0], q[1], 2.6f * k, paint);
            float[] now = at(sunAz, sunAlt);     // no hour beside the sun itself: its own label goes there
            // No hour under the keys or over the compass along the picture's top (they ran into "Back", audit 3.2.2)
            boolean clear = q[1] - 8 * k > Math.max(topInset + headroomDp * k, frame.top + 30 * k)    // below the compass strip
                    && !(q[0] > frame.right - 64 * k && q[0] < frame.right + 40 * k);           // and off the height scale
            if (labels && clear && (now == null || Math.hypot(q[0] - now[0], q[1] - now[1]) > 56 * k)) {
                label(c, pLabels[i], q[0], q[1] - 8 * k, Ui.withAlpha(color, 240), 11);
            }
        }
    }

    /** Your line, standing up from the horizon: where the sun or moon has to be. */
    private void drawLine(Canvas c, float yH) {
        if (Math.abs(FrameMath.diff(lineAz, azCenter)) > 80) return;
        float[] base = at(lineAz, 0), top = at(lineAz, 60);
        if (base == null) return;
        float ty = top == null ? 0 : top[1];
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * k);
        paint.setColor(0x80FFFFFF);
        paint.setPathEffect(new DashPathEffect(new float[]{5 * k, 6 * k}, 0));
        c.drawLine(base[0], base[1], top == null ? base[0] : top[0], ty, paint);
        paint.setPathEffect(null);
    }

    private void drawSun(Canvas c, float x, float y, float r) {
        float glow = Math.max(r * 7, 30 * k);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x, y, glow, new int[]{0xCCFFE9A8, 0x55FFB45A, 0x00FF9F40},
                new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glow, paint);
        paint.setShader(null);
        // Low in the sky the sun is deep orange; high up it is near white.
        paint.setColor(mix(0xFFFF8A3D, 0xFFFFFBEA, Math.max(0, Math.min(1, sunAlt / 12))));
        c.drawCircle(x, y, r, paint);
    }

    /** The moon with its real phase: a faint whole disc and the lit part bright. */
    private void drawMoon(Canvas c, float x, float y, float r) {
        paint.setStyle(Paint.Style.FILL);
        // A soft glow, so the moon is the brightest thing in the picture even when it is a thin crescent.
        float glow = Math.max(r * 5, 24 * k);
        paint.setShader(new RadialGradient(x, y, glow, new int[]{(int) (0x70 * (0.45 + 0.55 * moonLit)) << 24 | 0xF4F1E6, 0x00F4F1E6},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glow, paint);
        paint.setShader(null);
        paint.setColor(0x52F4F1E6);
        c.drawCircle(x, y, r, paint);
        paint.setColor(mix(0xFFF6C89A, 0xFFF4F1E6, Math.max(0, Math.min(1, moonAlt / 10))));
        float f = (float) moonLit;
        if (f > 0.98f) {
            c.drawCircle(x, y, r, paint);
        } else if (f > 0.02f) {
            path.reset();
            tmp.set(x - r, y - r, x + r, y + r);
            path.arcTo(tmp, -90, 180);
            float rx = r * Math.abs(1 - 2 * f);
            tmp.set(x - rx, y - r, x + rx, y + r);
            path.arcTo(tmp, 90, f > 0.5f ? 180 : -180);
            path.close();
            c.save();
            if (!moonWaxing) c.scale(-1, 1, x, y);
            c.drawPath(path, paint);
            c.restore();
        }
    }

    /** The land: haze at the horizon that takes the sky's colour, darker towards your feet. */
    private void drawGround(Canvas c, int w, int h, float yH, float[] sun) {
        if (yH >= h) return;
        float top = Math.max(yH, 0);
        int far = groundFar(), near = groundNear();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, Math.max(top + 1, Math.min(h, top + 0.5f * pxPerUnit)),
                far, near, Shader.TileMode.CLAMP));
        c.drawRect(0, top, w, h, paint);
        paint.setShader(null);
        // The horizon, a fine pale line: where the land ends and the sky begins.
        if (yH > 0) {
            paint.setColor(0x66FFFFFF);
            c.drawRect(0, yH - 0.5f * k, w, yH + 0.5f * k, paint);
        }
        // A thin bright rim where the low sun meets the land.
        if (sun != null && sunAlt > -3 && sunAlt < 12 && yH > 0 && !compose) {
            float r = (float) (pxPerUnit * Math.tan(Math.toRadians(9)));
            paint.setShader(new RadialGradient(sun[0], yH, r, new int[]{0x99FFD9A0, 0x00FFD9A0}, null, Shader.TileMode.CLAMP));
            c.drawRect(sun[0] - r, yH - 1.5f * k, sun[0] + r, yH + 1.5f * k, paint);
            paint.setShader(null);
        }
    }

    /** The subject, standing on the horizon in the direction of the line, drawn to scale when its height is known. */
    private void drawSubject(Canvas c, float yH) {
        if (Math.abs(FrameMath.diff(subjectAz, azCenter)) > 80) return;
        double baseAlt = FrameMath.baseAngle(subjectRise, subjectDist);
        float[] base = at(subjectAz, baseAlt);
        if (base == null) return;
        float x = base[0], y = base[1];
        if (y < yH - 1.5f * k) drawHill(c, x, y, yH);   // higher ground: the subject stands on a rise of the land
        // 3.22 (owner: "the subject must stand out from the background"): the subject in its own blue, the colour that
        // marks it on the maps, with a pale lit edge and a thin light outline; the buildings stay dark grey.
        int fill = mix(Ui.ROUTE_BLUE, 0xFF000000, 0.35), rim = mix(Ui.ROUTE_BLUE, 0xFFFFFFFF, 0.65);
        if (subjectHeight > 0) {
            float[] t = at(subjectAz, FrameMath.topAngle(subjectHeight, subjectDist, subjectRise));
            float hp = t == null ? 0 : y - t[1];
            if (hp >= 2 * k) {
                float wp = (float) (hp * FrameMath.KIND_WIDTHS[subjectKind]);
                outline(x, y + 1.5f * k, wp, hp);
                paint.setStyle(Paint.Style.FILL);
                boolean byMoon = only == 1 || (compose && composeMoon);
                double lightAz = byMoon ? moonAz : sunAz, lightAlt = byMoon ? moonAlt : sunAlt;
                if (lightAlt > -6) {
                    // 3.16: a silhouette lit on the light's side, as the camera sees it: the shape in the rim's colour,
                    // then itself again moved away from the light, so only a thin edge on that side stays lit.
                    double side = FrameMath.diff(lightAz, subjectAz);
                    float d = Math.max(1, 1.3f * k);
                    float dx = Math.abs(side) < 2 ? 0 : side > 0 ? -d : d;
                    c.save();
                    c.clipPath(path);
                    paint.setColor(rim);
                    c.drawPath(path, paint);
                    c.translate(dx, d * (dx == 0 ? 1f : 0.6f));
                    paint.setColor(fill);
                    c.drawPath(path, paint);
                    c.restore();
                } else {
                    paint.setColor(fill);
                    c.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, k));
                paint.setColor(0x8CFFFFFF);
                c.drawPath(path, paint);
            }
        }
        // A small marker so the subject is found even when it is a speck, or has no height yet.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 4.5f * k, paint);
        paint.setColor(Ui.ROUTE_BLUE);
        c.drawCircle(x, y, 3 * k, paint);
        // Its name sits under its feet, out of the sky where the sun and moon are.
        String label = subjectHeight > 0 ? "Subject · " + Fmt.height(subjectHeight) + " tall" : "Subject";
        label(c, label, x, Math.min(getHeight() - bottomInset + 40 * k, y + 24 * k), 0xF2FFFFFF, 12);
    }

    /** The land rising from the horizon to the subject's foot: a soft hill as wide as it is gentle. */
    private void drawHill(Canvas c, float x, float top, float yH) {
        float up = yH - top, half = Math.max(70 * k, up * 7);
        path.reset();
        path.moveTo(x - half, yH);
        path.cubicTo(x - half * 0.45f, yH, x - half * 0.3f, top, x, top);
        path.cubicTo(x + half * 0.3f, top, x + half * 0.45f, yH, x + half, yH);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(groundFar());
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        paint.setColor(0x66FFFFFF);
        c.drawPath(path, paint);
    }

    /**
     * The subject by kind, feet at (x, y), wp wide and hp tall (3.16, owner chose the pictograms): built like Otl
     * Aicher's Ulm pictograms, of circles and straight edges, solid, at the proportions the app works with.
     */
    private void outline(float x, float y, float wp, float hp) {
        path.reset();
        path.setFillType(Path.FillType.WINDING);
        float l = x - wp / 2, r = x + wp / 2, t = y - hp, thin = Math.max(0.6f * k, 0.04f * wp);
        switch (subjectKind) {
            case 0: {   // person: head, shoulders and body, two legs
                float head = Math.min(wp * 0.32f, hp * 0.1f);
                path.addCircle(x, t + head, head, Path.Direction.CW);
                tmp.set(l, t + head * 2 + hp * 0.03f, r, t + hp * 0.6f);
                path.addRoundRect(tmp, wp * 0.3f, wp * 0.3f, Path.Direction.CW);
                path.addRect(l + wp * 0.08f, t + hp * 0.56f, x - wp * 0.04f, y, Path.Direction.CW);
                path.addRect(x + wp * 0.04f, t + hp * 0.56f, r - wp * 0.08f, y, Path.Direction.CW);
                break;
            }
            case 1: {   // tree: a round crown on a straight trunk
                float crown = Math.min(wp / 2, hp * 0.36f);
                path.addCircle(x, t + crown, crown, Path.Direction.CW);
                path.addRect(x - Math.max(thin, wp * 0.08f), t + crown, x + Math.max(thin, wp * 0.08f), y, Path.Direction.CW);
                break;
            }
            case 2: {   // house: a roof at 45 degrees over the walls, a door
                float drop = Math.min(wp / 2, hp * 0.55f), eaves = t + drop;
                path.moveTo(l, eaves);
                path.lineTo(x, t);
                path.lineTo(r, eaves);
                path.close();
                path.addRect(l + wp * 0.08f, eaves - 0.5f, r - wp * 0.08f, y, Path.Direction.CW);
                path.addRect(x - wp * 0.07f, y - hp * 0.3f, x + wp * 0.07f, y, Path.Direction.CCW);   // the door, open
                break;
            }
            case 3:     // building: a block
                path.addRect(l, t, r, y, Path.Direction.CW);
                break;
            case 4: {   // tower: a tapering shaft, a round pod, a mast
                float podY = t + hp * 0.3f, pod = Math.min(wp * 0.45f, hp * 0.06f);
                path.moveTo(l, y);
                path.lineTo(x - wp * 0.14f, podY);
                path.lineTo(x + wp * 0.14f, podY);
                path.lineTo(r, y);
                path.close();
                path.addCircle(x, podY, pod, Path.Direction.CW);
                path.addRect(x - thin / 2, t, x + thin / 2, podY, Path.Direction.CW);
                break;
            }
            default: {  // peak: a summit and a lower shoulder beside it
                path.moveTo(l, y);
                path.lineTo(x, t);
                path.lineTo(x + wp * 0.16f, t + hp * 0.42f);
                path.lineTo(x + wp * 0.24f, t + hp * 0.3f);
                path.lineTo(r, y);
                path.close();
            }
        }
    }

    private void ring(Canvas c, float x, float y, float r, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * k);
        paint.setColor(color);
        c.drawCircle(x, y, r + 7 * k, paint);
        pill(c, "Lined up", x, y - r - 24 * k, color, 0xFF1A1203);
    }

    /** The name beside a body when it is small, or which way to turn when it is out of sight. */
    private void tag(Canvas c, String name, double az, double alt, float[] q, float r, int color, float edgeY) {
        if (alt < -1) return;
        boolean seen = q != null && q[0] > 0 && q[0] < getWidth() && q[1] > topInset && q[1] < getHeight();
        if (seen) {
            if (onLine(az, alt)) return;                       // the ring already names it
            if (r < 12 * k) {
                text.setTextSize(12 * sp);
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(0x80000000);
                c.drawText(name, q[0] + r + 7 * k + k, q[1] + 4 * k + k, text);
                text.setColor(color);
                c.drawText(name, q[0] + r + 7 * k, q[1] + 4 * k, text);
            }
            return;
        }
        double off = FrameMath.diff(az, azCenter);
        String s = Math.abs(off) > 150 ? name + " is behind you"
                : name + " is " + Math.round(Math.abs(off)) + "° to the " + (off < 0 ? "left" : "right");
        text.setTextSize(12 * sp);
        float half = (text.measureText(s) + 20 * k) / 2;
        pill(c, s, off < 0 ? frame.left + 10 * k + half : frame.right - 10 * k - half,
                frame.top + frame.height() * edgeY, 0xB30B1220, color);
    }

    /** What is outside the picture is dimmed; bright corners mark the frame. */
    private void drawOutside(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x8C050810);           // the picture is the brightest part of the screen
        c.drawRect(0, 0, w, frame.top, paint);
        c.drawRect(0, frame.bottom, w, h, paint);
        c.drawRect(0, frame.top, frame.left, frame.bottom, paint);
        c.drawRect(frame.right, frame.top, w, frame.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * k);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(0xE6FFFFFF);
        float a = 16 * k;
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? frame.left : frame.right, y = i < 2 ? frame.top : frame.bottom;
            c.drawLine(x, y, x + (i % 2 == 0 ? a : -a), y, paint);
            c.drawLine(x, y, x, y + (i < 2 ? a : -a), paint);
        }
    }

    /** A compass along the top of the picture: which way the camera looks. */
    private void drawCompass(Canvas c, int w) {
        float y = frame.top;
        double half = Math.toDegrees(Math.atan(frame.width() / 2 / pxPerUnit));
        int step = half > 30 ? 15 : half > 12 ? 5 : half > 4 ? 2 : 1;
        text.setTextSize(11 * sp);
        text.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        int first = (int) Math.floor((azCenter - half) / step) * step;
        for (int a = first; a <= azCenter + half + step; a += step) {
            float x = cx + (float) Math.tan(Math.toRadians(a - azCenter)) * pxPerUnit;
            if (x < frame.left + 4 * k || x > frame.right - 4 * k) continue;
            boolean room = x > frame.left + 18 * k && x < frame.right - 44 * k;   // clear of the heights on the right
            int n = ((a % 360) + 360) % 360;
            boolean major = n % (step * 3) == 0 || n % 45 == 0;
            paint.setColor(major ? 0xF2FFFFFF : 0x99FFFFFF);
            c.drawLine(x, y, x, y + (major ? 9 : 5) * k, paint);
            if (major && room) {
                String s = n % 45 == 0 ? Astro.compass(n) : n + "°";
                label(c, s, x, y + 22 * k, n % 45 == 0 ? 0xFFFFFFFF : 0xE6FFFFFF, 10.5f);
            }
        }
    }

    /** Heights above the horizon along the right edge of the picture. */
    private void drawHeights(Canvas c, float yH) {
        double span = FrameMath.fovV(focal, portrait);
        int step = span > 50 ? 10 : span > 20 ? 5 : span > 8 ? 2 : 1;
        text.setTextSize(10.5f * sp);
        text.setTextAlign(Paint.Align.RIGHT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        for (int a = 0; a <= 90; a += step) {
            float[] q = at(azCenter, a);
            if (q == null || q[1] < frame.top + 30 * k) break;
            if (q[1] > frame.bottom - 6 * k) continue;
            paint.setColor(a == 0 ? 0xF2FFFFFF : 0x99FFFFFF);
            c.drawLine(frame.right - (a == 0 ? 12 : 6) * k, q[1], frame.right, q[1], paint);
            text.setShadowLayer(3 * k, 0, k, 0xB3000000);
            text.setColor(0xE6FFFFFF);
            c.drawText(a + "°", frame.right - 15 * k, q[1] + 4 * k, text);
            text.clearShadowLayer();
        }
    }

    /** Words straight on the scene, with a soft shadow so they read on any sky. */
    private void label(Canvas c, String s, float x, float y, int color, float size) {
        text.setTextSize(size * sp);
        text.setTextAlign(Paint.Align.CENTER);
        float w = text.measureText(s);
        x = Math.max(w / 2 + 6 * k, Math.min(getWidth() - w / 2 - 6 * k, x));
        text.setShadowLayer(4 * k, 0, k, 0xCC000000);
        text.setColor(color);
        c.drawText(s, x, y + text.getTextSize() * 0.35f, text);
        text.clearShadowLayer();
    }

    private void pill(Canvas c, String s, float x, float y, int bg, int fg) {
        text.setTextSize(12 * sp);
        text.setTextAlign(Paint.Align.CENTER);
        float w = text.measureText(s) + 20 * k, h = 24 * k;
        x = Math.max(w / 2 + 4 * k, Math.min(getWidth() - w / 2 - 4 * k, x));
        tmp.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        c.drawRoundRect(tmp, h / 2, h / 2, paint);
        text.setColor(fg);
        c.drawText(s, x, y + text.getTextSize() * 0.35f, text);
    }

    // ---- touch ----

    private boolean grabBody;   // Build the shot: this drag began on the sun or moon (else it frames the picture)

    /** Where the subject's foot is drawn, and the moon (for tests and checks of what is in the picture). */
    float[] subjectOnScreen() { return at(subjectAz, FrameMath.baseAngle(subjectRise, subjectDist)); }

    float[] moonOnScreen() { return moonAlt > -2 ? at(moonAz, moonAlt) : null; }

    float[] subjectTopOnScreen() {
        return at(subjectAz, subjectHeight > 0 ? FrameMath.topAngle(subjectHeight, subjectDist, subjectRise)
                : FrameMath.baseAngle(subjectRise, subjectDist));
    }

    double aimAz() { return azCenter; }

    double aimPitch() { return pitch; }

    /** The picture the lens takes in, on screen. */
    RectF frameRect() { return new RectF(frame); }

    /** Where the body being placed is drawn (Build the shot), or null. */
    float[] bodyOnScreen() {
        if (!compose) return null;
        return composeMoon ? at(moonAz, moonAlt) : at(sunAz, sunAlt);
    }

    /** Is this point on the body, generously: its ring and a finger's width round it (at least 48 dp across). */
    boolean onBody(float x, float y) {
        float[] q = bodyOnScreen();
        if (q == null) return false;
        float least = 7 * k;
        float r = Math.max(least, (float) (Math.tan(Math.toRadians(0.265)) * pxPerUnit)) + 12 * k;
        return Math.hypot(x - q[0], y - q[1]) <= Math.max(r + 24 * k, 24 * k);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (still) return super.onTouchEvent(e);    // a tap may open the viewfinder; nothing moves
        pinch.onTouchEvent(e);
        taps.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                axis = 0;
                pinching = false;
                grabBody = compose && onBody(e.getX(), e.getY());
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (pinching || e.getPointerCount() > 1) {
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                }
                if (axis == 0) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (Math.max(dx, dy) > 10 * k) axis = dx >= dy ? 1 : 2;
                    else return true;
                }
                if (compose && !grabBody) {             // off the body: the picture follows the finger
                    if (listener != null) {
                        double degPerPx = Math.toDegrees(Math.atan(1 / pxPerUnit));
                        listener.onFrame(-(e.getX() - lastX) * degPerPx, (e.getY() - lastY) * degPerPx, false);
                    }
                } else if (compose) {
                    dragged = true;
                    if (listener != null) {
                        double[] at = FrameMath.unproject((e.getX() - cx) / pxPerUnit, (e.getY() - 28 * k - cy) / pxPerUnit,
                                azCenter, pitch);                    // the body rides above the finger, in sight
                        listener.onBodyAt(at[0], at[1], false);
                    }
                } else if (listener != null) {
                    if (axis == 1) {
                        listener.onScrubPx(e.getX() - lastX);
                    } else if (!live) {
                        double degPerPx = Math.toDegrees(Math.atan(1 / pxPerUnit));
                        listener.onTilt(pitch + (e.getY() - lastY) * degPerPx);   // drag down: look higher
                    }
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (compose && !grabBody && listener != null && axis != 0 && !pinching) {
                    listener.onFrame(0, 0, true);
                } else if (compose && listener != null && axis != 0 && !pinching) {
                    double[] at = FrameMath.unproject((e.getX() - cx) / pxPerUnit, (e.getY() - 28 * k - cy) / pxPerUnit,
                            azCenter, pitch);
                    listener.onBodyAt(at[0], at[1], true);
                }
                return true;
        }
        return true;
    }
}
