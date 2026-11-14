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
    private int subjectKind;
    private double lineAz;
    private boolean lineIsBehind;      // the sun is wanted behind you: the line points away from the subject
    private int topInset, bottomInset;

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
                double f = Math.max(FrameMath.FOCAL_MIN, Math.min(FrameMath.FOCAL_MAX, focal * d.getScaleFactor()));
                if (listener != null) listener.onFocal(f, false);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                double snapped = FrameMath.snapFocal(focal);
                if (snapped != focal) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) listener.onFocal(snapped, true);
            }
        });
        setContentDescription("Viewfinder. Pinch to change the lens, drag sideways to change the time, "
                + "up or down to tilt, double tap to aim at the subject again.");
    }

    void setListener(Listener l) { listener = l; }

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
        boolean on = onLine(sunAz, sunAlt) || onLine(moonAz, moonAlt);
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
        subjectAz = az;
        subjectDist = metres;
        subjectHeight = height;
        subjectKind = kind;
        this.lineAz = lineAz;
        this.lineIsBehind = lineIsBehind;
        invalidate();
    }

    private boolean onLine(double az, double alt) {
        return alt >= -1 && Math.abs(FrameMath.diff(az, lineAz)) < 0.3;
    }

    // ---- sky colours by the sun's height: night, twilight, golden light, day ----

    private static final double[] KEY_ALT = {-18, -12, -6, -1, 3, 10, 30};
    private static final int[] KEY_HORIZON = {0xFF0C1226, 0xFF1B2450, 0xFF8A4B6E, 0xFFF29A5C, 0xFFFFC27A, 0xFFFFE2B0, 0xFFCFE6FF};
    private static final int[] KEY_ZENITH = {0xFF03050C, 0xFF060A1C, 0xFF0F1A40, 0xFF22407A, 0xFF3F74C2, 0xFF4C8BE0, 0xFF3E86E8};

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
        return mix(keyed(KEY_HORIZON, sunAlt), keyed(KEY_ZENITH, sunAlt), Math.sqrt(t));
    }

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
        float availTop = topInset + 64 * k, availBottom = h - bottomInset - 12 * k;
        float fw = Math.min(w - 24 * k, (availBottom - availTop) * aspect);
        float fh = fw / aspect;
        cx = w / 2f;
        cy = availTop + (availBottom - availTop) / 2f;
        frame.set(cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2);
        pxPerUnit = (float) (fw / (FrameMath.sensorWidth(portrait) / focal));

        drawSky(c, w, h);
        float[] hz = at(azCenter, 0);
        float yH = hz == null ? (pitch > 0 ? h + 1000 : -1000) : hz[1];
        drawStars(c);
        drawPath(c, pMoonAz, pMoonAlt, 0xFFC4B5FD, false);
        drawPath(c, pSunAz, pSunAlt, 0xFFFFD166, true);
        drawLine(c, yH);

        float[] sun = at(sunAz, sunAlt), moon = at(moonAz, moonAlt);
        float sunR = Math.max(5 * k, (float) (Math.tan(Math.toRadians(0.265)) * pxPerUnit));
        float moonR = Math.max(5 * k, (float) (Math.tan(Math.toRadians(0.26)) * pxPerUnit));
        if (moon != null && moonAlt > -2) drawMoon(c, moon[0], moon[1], moonR);
        if (sun != null && sunAlt > -2) drawSun(c, sun[0], sun[1], sunR);

        drawGround(c, w, h, yH, sun);
        drawSubject(c, yH);

        if (sun != null && onLine(sunAz, sunAlt)) ring(c, sun[0], sun[1], sunR, 0xFFFFD166);
        if (moon != null && onLine(moonAz, moonAlt)) ring(c, moon[0], moon[1], moonR, 0xFFC4B5FD);
        tag(c, "Sun", sunAz, sunAlt, sun, sunR, 0xFFFFD166, 0.36f);
        tag(c, "Moon", moonAz, moonAlt, moon, moonR, 0xFFD9DEF2, 0.46f);

        drawOutside(c, w, h);
        drawCompass(c, w);
        drawHeights(c, yH);
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
        if (sunAlt > -10) {
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
        if (sunAlt > -5) return;
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
        paint.setStrokeWidth(1.6f * k);
        paint.setColor(Ui.withAlpha(color, 150));
        paint.setPathEffect(new DashPathEffect(new float[]{1 * k, 6 * k}, 0));
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
            if (labels && (now == null || Math.hypot(q[0] - now[0], q[1] - now[1]) > 56 * k)) {
                text.setColor(0x66000000);
                c.drawText(pLabels[i], q[0] + k, q[1] - 8 * k + k, text);
                text.setColor(Ui.withAlpha(color, 235));
                c.drawText(pLabels[i], q[0], q[1] - 8 * k, text);
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
        float glow = Math.max(r * 4, 16 * k);
        paint.setShader(new RadialGradient(x, y, glow, new int[]{(int) (0x44 * (0.3 + 0.7 * moonLit)) << 24 | 0xD9DEF2, 0x00D9DEF2},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glow, paint);
        paint.setShader(null);
        paint.setColor(0x38D9DEF2);
        c.drawCircle(x, y, r, paint);
        paint.setColor(mix(0xFFF6C89A, 0xFFF4F6FC, Math.max(0, Math.min(1, moonAlt / 10))));
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
        int horizon = keyed(KEY_HORIZON, sunAlt);
        int far = mix(horizon, 0xFF05070D, 0.62), near = mix(horizon, 0xFF020308, 0.92);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, Math.max(top + 1, Math.min(h, top + 0.5f * pxPerUnit)),
                far, near, Shader.TileMode.CLAMP));
        c.drawRect(0, top, w, h, paint);
        paint.setShader(null);
        // A thin bright rim where the low sun meets the land.
        if (sun != null && sunAlt > -3 && sunAlt < 12 && yH > 0) {
            float r = (float) (pxPerUnit * Math.tan(Math.toRadians(9)));
            paint.setShader(new RadialGradient(sun[0], yH, r, new int[]{0x99FFD9A0, 0x00FFD9A0}, null, Shader.TileMode.CLAMP));
            c.drawRect(sun[0] - r, yH - 1.5f * k, sun[0] + r, yH + 1.5f * k, paint);
            paint.setShader(null);
        }
    }

    /** The subject, standing on the horizon in the direction of the line, drawn to scale when its height is known. */
    private void drawSubject(Canvas c, float yH) {
        if (Math.abs(FrameMath.diff(subjectAz, azCenter)) > 80) return;
        float[] base = at(subjectAz, 0);
        if (base == null) return;
        float x = base[0], y = base[1];
        int horizon = keyed(KEY_HORIZON, sunAlt);
        int fill = mix(horizon, 0xFF020308, 0.9);
        if (subjectHeight > 0) {
            float[] t = at(subjectAz, FrameMath.topAngle(subjectHeight, subjectDist));
            float hp = t == null ? 0 : y - t[1];
            if (hp >= 2 * k) {
                float wp = (float) (hp * FrameMath.KIND_WIDTHS[subjectKind]);
                outline(x, y + 1.5f * k, wp, hp);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fill);
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, k));
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(0x47FFFFFF);
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
        String label = subjectHeight > 0 ? "Subject · " + Fmt.height(subjectHeight) : "Subject";
        pill(c, label, x, Math.min(getHeight() - bottomInset + 40 * k, y + 22 * k), 0xB30B1220, 0xFFFFFFFF);
    }

    /** The outline of the subject by kind, feet at (x, y), wp wide and hp tall. Plain shapes: a measure, not a drawing. */
    private void outline(float x, float y, float wp, float hp) {
        path.reset();
        float l = x - wp / 2, r = x + wp / 2, t = y - hp;
        switch (subjectKind) {
            case 0: {   // person: body and head
                float head = hp * 0.075f;
                tmp.set(l, t + head * 2.2f, r, y + wp / 2);
                path.addRoundRect(tmp, wp / 2, wp / 2, Path.Direction.CW);
                path.addCircle(x, t + head, head, Path.Direction.CW);
                break;
            }
            case 1: {   // tree: trunk and crown
                path.addRect(x - wp * 0.07f, t + hp * 0.5f, x + wp * 0.07f, y, Path.Direction.CW);
                tmp.set(l, t, r, t + hp * 0.72f);
                path.addOval(tmp, Path.Direction.CW);
                break;
            }
            case 2: {   // house: walls and a pitched roof
                path.moveTo(l, y);
                path.lineTo(l, t + hp * 0.45f);
                path.lineTo(x, t);
                path.lineTo(r, t + hp * 0.45f);
                path.lineTo(r, y);
                path.close();
                break;
            }
            case 3:     // building: a block
                path.addRect(l, t, r, y, Path.Direction.CW);
                break;
            case 4: {   // tower: tapering, with a mast
                path.moveTo(l, y);
                path.lineTo(x - wp * 0.22f, t + hp * 0.16f);
                path.lineTo(x - wp * 0.05f, t + hp * 0.16f);
                path.lineTo(x, t);
                path.lineTo(x + wp * 0.05f, t + hp * 0.16f);
                path.lineTo(x + wp * 0.22f, t + hp * 0.16f);
                path.lineTo(r, y);
                path.close();
                break;
            }
            default: {  // peak: a ridge with a shoulder
                path.moveTo(l, y);
                path.lineTo(x - wp * 0.2f, t + hp * 0.42f);
                path.lineTo(x - wp * 0.1f, t + hp * 0.5f);
                path.lineTo(x, t);
                path.lineTo(x + wp * 0.16f, t + hp * 0.38f);
                path.lineTo(x + wp * 0.26f, t + hp * 0.34f);
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
        pill(c, "On the line", x, y - r - 24 * k, color, 0xFF1A1203);
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
                : name + " " + Math.round(Math.abs(off)) + "° " + (off < 0 ? "left" : "right");
        text.setTextSize(12 * sp);
        float half = (text.measureText(s) + 20 * k) / 2;
        pill(c, s, off < 0 ? frame.left + 10 * k + half : frame.right - 10 * k - half,
                frame.top + frame.height() * edgeY, 0xB30B1220, color);
    }

    /** What is outside the picture is dimmed; bright corners mark the frame. */
    private void drawOutside(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x73050810);
        c.drawRect(0, 0, w, frame.top, paint);
        c.drawRect(0, frame.bottom, w, h, paint);
        c.drawRect(0, frame.top, frame.left, frame.bottom, paint);
        c.drawRect(frame.right, frame.top, w, frame.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        paint.setColor(0x59FFFFFF);
        c.drawRect(frame, paint);
        paint.setStrokeWidth(2.5f * k);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(0xFFFFFFFF);
        float a = 18 * k;
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
            paint.setColor(major ? 0xE6FFFFFF : 0x80FFFFFF);
            c.drawLine(x, y, x, y + (major ? 9 : 5) * k, paint);
            if (major && room) {
                String s = n % 45 == 0 ? Astro.compass(n) : n + "°";
                text.setColor(0x80000000);
                c.drawText(s, x + k, y + 22 * k + k, text);
                text.setColor(n % 90 == 0 ? 0xFFFFFFFF : 0xD9FFFFFF);
                c.drawText(s, x, y + 22 * k, text);
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
            paint.setColor(a == 0 ? 0xCCFFFFFF : 0x80FFFFFF);
            c.drawLine(frame.right - (a == 0 ? 12 : 6) * k, q[1], frame.right, q[1], paint);
            text.setColor(0xCCFFFFFF);
            c.drawText(a + "°", frame.right - 15 * k, q[1] + 4 * k, text);
        }
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

    @Override public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        taps.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                axis = 0;
                pinching = false;
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
                if (listener != null) {
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
                return true;
        }
        return true;
    }
}
