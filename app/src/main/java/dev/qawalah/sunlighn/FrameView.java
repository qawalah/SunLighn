package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * A viewfinder: the sky as the camera would see it, pointed along your line, with the sun and the moon where
 * they really are and as big as they really are (about half a degree). The sky colour follows the sun's height.
 * Drag sideways to change the time, up or down to tilt the camera.
 */
final class FrameView extends View {
    interface Listener {
        void onScrub(long deltaMs);
        void onTilt(double pitch);
    }

    private final Ui.Palette p;
    private final float k;
    private final float sp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF frame = new RectF();

    private double azCenter, pitch, focal = 35;
    private boolean portrait;
    private double sunAz, sunAlt, moonAz, moonAlt, moonLit;
    private boolean moonWaxing;
    private Listener listener;

    // Touch
    private float downX, downY, lastX, lastY;
    private int axis;   // 0 undecided, 1 sideways (time), 2 up/down (tilt)

    FrameView(Context c, Ui.Palette p) {
        super(c);
        this.p = p;
        this.k = Ui.dp(c, 1);
        this.sp = c.getResources().getDisplayMetrics().scaledDensity;
        setContentDescription("Viewfinder preview. Drag sideways to change the time, up or down to tilt.");
    }

    void setListener(Listener l) { listener = l; }

    void setLens(double focalMm, boolean portrait) {
        this.focal = focalMm;
        this.portrait = portrait;
        invalidate();
    }

    void setAim(double azCenter, double pitch) {
        this.azCenter = azCenter;
        this.pitch = pitch;
        invalidate();
    }

    void setBodies(double sunAz, double sunAlt, double moonAz, double moonAlt, double moonLit, boolean waxing) {
        this.sunAz = sunAz;
        this.sunAlt = sunAlt;
        this.moonAz = moonAz;
        this.moonAlt = moonAlt;
        this.moonLit = moonLit;
        this.moonWaxing = waxing;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(250 * k));
    }

    // ---- sky colours by the sun's height: night, twilight, golden light, day ----

    private static final double[] KEY_ALT = {-18, -12, -6, -1, 3, 10, 30};
    private static final int[] KEY_HORIZON = {0xFF0C1226, 0xFF1B2450, 0xFF8A4B6E, 0xFFF29A5C, 0xFFFFC27A, 0xFFFFE2B0, 0xFFCFE6FF};
    private static final int[] KEY_ZENITH = {0xFF03050C, 0xFF060A1C, 0xFF0F1A40, 0xFF22407A, 0xFF3F74C2, 0xFF4C8BE0, 0xFF3E86E8};

    private static int mix(int a, int b, double t) {
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
        int horizon = keyed(KEY_HORIZON, sunAlt), zenith = keyed(KEY_ZENITH, sunAlt);
        double t = Math.max(0, Math.min(1, elevation / 50.0));
        return mix(horizon, zenith, Math.sqrt(t));
    }

    @Override protected void onDraw(Canvas c) {
        float aspect = portrait ? 2f / 3f : 3f / 2f;
        float fw = Math.min(getWidth(), getHeight() * aspect);
        float fh = fw / aspect;
        frame.set((getWidth() - fw) / 2f, (getHeight() - fh) / 2f, (getWidth() + fw) / 2f, (getHeight() + fh) / 2f);
        float radius = 16 * k;
        double halfW = FrameMath.sensorWidth(portrait) / 2 / focal;        // in focal lengths
        float pxPerUnit = (float) (fw / 2 / halfW);
        float cx = frame.centerX(), cy = frame.centerY();
        double fovV = FrameMath.fovV(focal, portrait);

        c.save();
        path.reset();
        path.addRoundRect(frame, radius, radius, Path.Direction.CW);
        c.clipPath(path);

        // Sky: from the colour at the top edge of the frame to the colour at the bottom edge.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, frame.top, 0, frame.bottom,
                skyAt(pitch + fovV / 2), skyAt(pitch - fovV / 2), Shader.TileMode.CLAMP));
        c.drawRect(frame, paint);
        paint.setShader(null);

        double[] hz = FrameMath.project(azCenter, 0, azCenter, pitch);
        float yH = hz == null ? frame.bottom + 1000 : cy + (float) hz[1] * pxPerUnit;

        // Sun, then moon, then the ground over them so what is below the horizon is hidden.
        float sunX = 0, sunY = 0, sunR = 0;
        double[] sp1 = FrameMath.project(sunAz, sunAlt, azCenter, pitch);
        if (sp1 != null) {
            sunX = cx + (float) sp1[0] * pxPerUnit;
            sunY = cy + (float) sp1[1] * pxPerUnit;
            sunR = Math.max(3 * k, (float) (Math.tan(Math.toRadians(0.265)) * pxPerUnit));
            drawSun(c, sunX, sunY, sunR);
        }
        double[] mp = FrameMath.project(moonAz, moonAlt, azCenter, pitch);
        float moonX = 0, moonY = 0, moonR = 0;
        if (mp != null) {
            moonX = cx + (float) mp[0] * pxPerUnit;
            moonY = cy + (float) mp[1] * pxPerUnit;
            moonR = Math.max(3 * k, (float) (Math.tan(Math.toRadians(0.26)) * pxPerUnit));
            drawMoon(c, moonX, moonY, moonR);
        }

        // Ground: a dark silhouette shaded from the horizon colour.
        if (yH < frame.bottom) {
            int horizon = keyed(KEY_HORIZON, sunAlt);
            paint.setShader(new LinearGradient(0, Math.max(yH, frame.top), 0, frame.bottom,
                    mix(horizon, 0xFF000000, 0.80), mix(horizon, 0xFF000000, 0.93), Shader.TileMode.CLAMP));
            c.drawRect(frame.left, Math.max(yH, frame.top), frame.right, frame.bottom, paint);
            paint.setShader(null);
        }

        // Rule-of-thirds grid, as in a viewfinder.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        paint.setColor(0x40FFFFFF);
        for (int i = 1; i < 3; i++) {
            c.drawLine(frame.left + fw * i / 3f, frame.top, frame.left + fw * i / 3f, frame.bottom, paint);
            c.drawLine(frame.left, frame.top + fh * i / 3f, frame.right, frame.top + fh * i / 3f, paint);
        }

        // The subject: straight ahead, on the horizon.
        if (yH > frame.top && yH < frame.bottom + 8 * k) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(Ui.ROUTE_BLUE, 60));
            c.drawCircle(cx, yH, 14 * k, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(cx, yH, 7.5f * k, paint);
            paint.setColor(Ui.ROUTE_BLUE);
            c.drawCircle(cx, yH, 5.5f * k, paint);
            pill(c, "Subject", cx, yH - 18 * k, 0xFF0B1220, 0xFFFFFFFF, true);
        }

        // Names on the bodies, and arrows when they are out of the frame.
        label(c, "Sun", sunAlt, sunX, sunY, sunR, sp1 != null, sunAz, 0xFFFFD166);
        label(c, "Moon", moonAlt, moonX, moonY, moonR, mp != null, moonAz, 0xFFD9DEF2);
        c.restore();

        // Frame edge and the lens, in the app's own surfaces.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        paint.setColor(p.line);
        c.drawRoundRect(frame, radius, radius, paint);
        String lens = Math.round(focal) + " mm · " + Math.round(FrameMath.fovH(focal, portrait)) + "° × "
                + Math.round(fovV) + "°";
        pill(c, lens, frame.left + 12 * k, frame.top + 12 * k + 11 * k, 0xCC0B1220, 0xFFE8EEFC, false);
    }

    private void drawSun(Canvas c, float x, float y, float r) {
        if (sunAlt < -8) return;
        float glow = Math.max(r * 12, 36 * k);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x, y, glow, new int[]{0x99FFD166, 0x33FF9F40, 0x00FF9F40},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glow, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFF3C4);
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, r * 0.25f));
        paint.setColor(0xFFFFD166);
        c.drawCircle(x, y, r, paint);
    }

    /** The moon with its real phase: a faint whole disc and the lit part bright. */
    private void drawMoon(Canvas c, float x, float y, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x, y, Math.max(r * 5, 14 * k), new int[]{0x33D9DEF2, 0x00D9DEF2},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, Math.max(r * 5, 14 * k), paint);
        paint.setShader(null);
        paint.setColor(0x40D9DEF2);
        c.drawCircle(x, y, r, paint);
        paint.setColor(0xFFF2F4FB);
        float f = (float) moonLit;
        if (f > 0.98f) {
            c.drawCircle(x, y, r, paint);
        } else if (f > 0.02f) {
            Path m = new Path();
            RectF circle = new RectF(x - r, y - r, x + r, y + r);
            float rx = r * Math.abs(1 - 2 * f);
            RectF term = new RectF(x - rx, y - r, x + rx, y + r);
            m.arcTo(circle, -90, 180);
            m.arcTo(term, 90, f > 0.5f ? 180 : -180);
            m.close();
            c.save();
            if (!moonWaxing) c.scale(-1, 1, x, y);
            c.drawPath(m, paint);
            c.restore();
        }
    }

    private void label(Canvas c, String name, double alt, float x, float y, float r, boolean projected, double az,
                       int color) {
        boolean inside = projected && x > frame.left && x < frame.right && y > frame.top && y < frame.bottom;
        if (alt < -1) return;       // below the horizon: not there to name
        if (inside) {
            if (r < 14 * k) pill(c, name, x, y - Math.max(r * 2.2f, 26 * k), 0xCC0B1220, color, true);
            return;
        }
        // Out of the frame: say which way and how far.
        double off = FrameMath.diff(az, azCenter);
        String text = name + " " + Math.round(Math.abs(off)) + "° " + (off < 0 ? "left" : "right");
        paint.setTextSize(12 * sp);
        paint.setTypeface(Ui.font(getContext(), 600));
        float w = paint.measureText(text) + 18 * k;
        float px = off < 0 ? frame.left + 8 * k + w / 2 : frame.right - 8 * k - w / 2;
        float ey = frame.top + frame.height() * (name.equals("Sun") ? 0.30f : 0.42f);
        pill(c, text, px, ey, 0xCC0B1220, color, true);
    }

    private void pill(Canvas c, String text, float x, float y, int bg, int fg, boolean centered) {
        paint.setTextSize(12 * sp);
        paint.setTypeface(Ui.font(getContext(), 600));
        float w = paint.measureText(text) + 18 * k, h = 22 * k;
        float left = centered ? x - w / 2 : x;
        RectF r = new RectF(left, y - h / 2, left + w, y + h / 2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        c.drawRoundRect(r, h / 2, h / 2, paint);
        paint.setColor(fg);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, r.centerX(), r.centerY() + paint.getTextSize() * 0.35f, paint);
    }

    // ---- touch: sideways moves through the day, up and down tilts ----

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                axis = 0;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (axis == 0) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (Math.max(dx, dy) > 10 * k) axis = dx >= dy ? 1 : 2;
                    else return true;
                }
                if (listener != null) {
                    if (axis == 1) {
                        // 1 dp is 2 minutes: a full swipe across the frame moves about half a day.
                        listener.onScrub((long) ((e.getX() - lastX) / k * 2 * 60000L));
                    } else {
                        double degPerPx = FrameMath.fovV(focal, portrait) / Math.max(1f, frame.height());
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
        return super.onTouchEvent(e);
    }
}
