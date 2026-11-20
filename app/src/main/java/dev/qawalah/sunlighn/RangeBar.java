package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * A slider with two thumbs (3.18, owner: "give a range where the sun can be aligned"): a low and a high value in
 * tenths of a unit between {@code min} and {@code max}. The thumb nearest the finger moves; they never cross.
 */
final class RangeBar extends View {
    interface Listener {
        void onRange(double lo, double hi, boolean done);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float k;
    private final int accent, track, bad;
    private final double min, max;
    // 3.19.4 (owner: "show me in the slider where the sun is visible, I don't want to guess"): the heights the body is
    // really on the line in the coming year (one tick each), and the height up to which something hides it (red).
    private final java.util.ArrayList<double[]> bands = new java.util.ArrayList<>();   // heights it lines up at, merged
    private double blockedTo = -90;
    private double lo, hi;
    private int drag;               // 0 none, 1 low thumb, 2 high thumb
    private Listener listener;

    RangeBar(Context c, Ui.Palette p, double min, double max) {
        super(c);
        this.min = min;
        this.max = max;
        k = c.getResources().getDisplayMetrics().density;
        accent = p.accent;
        track = p.line;
        bad = p.bad;
        lo = min;
        hi = max;
        setMinimumHeight(Math.round(56 * k));
    }

    void setListener(Listener l) { listener = l; }

    /** The heights it really lines up at (a tick each) and the height below which it is hidden (red; below min: none). */
    void setAvailability(double[] heights, double blockedUpTo) {
        bands.clear();
        if (heights != null && heights.length > 0) {
            double[] s = heights.clone();
            java.util.Arrays.sort(s);
            double start = s[0], prev = s[0];
            for (double v : s) {
                if (v - prev > 0.4) {                 // a gap of more than 0.4 degree: nothing lines up in between
                    bands.add(new double[]{start, prev});
                    start = v;
                }
                prev = v;
            }
            bands.add(new double[]{start, prev});
        }
        blockedTo = blockedUpTo;
        invalidate();
    }

    void set(double lo, double hi) {
        this.lo = Math.max(min, Math.min(max, Math.min(lo, hi)));
        this.hi = Math.max(min, Math.min(max, Math.max(lo, hi)));
        invalidate();
    }

    /** As if a finger set the range and let go (the screen tests). */
    void commit(double lo, double hi) {
        set(lo, hi);
        if (listener != null) listener.onRange(this.lo, this.hi, true);
    }

    double low() { return lo; }
    double high() { return hi; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(56 * k));
    }

    private float x(double v) {
        float pad = 14 * k;
        return pad + (float) ((v - min) / (max - min)) * (getWidth() - 2 * pad);
    }

    private double value(float px) {
        float pad = 14 * k;
        double v = min + (px - pad) / (getWidth() - 2 * pad) * (max - min);
        return Math.round(Math.max(min, Math.min(max, v)) * 10) / 10.0;
    }

    @Override protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f, r = 11 * k;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4 * k);
        paint.setColor(track);
        c.drawLine(x(min), cy, x(max), cy, paint);
        if (blockedTo > min) {                                   // hidden by a building, hills or the subject's ground
            paint.setColor((bad & 0x00FFFFFF) | 0xB0000000);
            c.drawLine(x(min), cy, x(Math.min(max, blockedTo)), cy, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);                       // the heights it really lines up at: a shaded band
        paint.setStrokeWidth(18 * k);
        paint.setColor((accent & 0x00FFFFFF) | 0x55000000);
        for (double[] b : bands) {
            if (b[1] < min || b[0] > max) continue;
            float x0 = x(Math.max(min, b[0])), x1 = x(Math.min(max, b[1]));
            if (x1 - x0 < 4 * k) { x0 -= 2 * k; x1 += 2 * k; }
            c.drawLine(x0, cy, x1, cy, paint);
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4 * k);
        paint.setColor(accent);
        c.drawLine(x(lo), cy, x(hi), cy, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        c.drawCircle(x(lo), cy, r, paint);
        c.drawCircle(x(hi), cy, r, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(x(lo), cy, 3.5f * k, paint);
        c.drawCircle(x(hi), cy, 3.5f * k, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // the nearer thumb; when both stand together, the one the finger is on the side of
                float dl = Math.abs(e.getX() - x(lo)), dh = Math.abs(e.getX() - x(hi));
                drag = dl < dh || (dl == dh && e.getX() < x(lo)) ? 1 : 2;
                // fall through
            case MotionEvent.ACTION_MOVE: {
                if (drag == 0) return false;
                double v = value(e.getX());
                if (drag == 1) lo = Math.min(v, hi);
                else hi = Math.max(v, lo);
                invalidate();
                if (listener != null) listener.onRange(lo, hi, false);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag != 0 && listener != null) listener.onRange(lo, hi, true);
                drag = 0;
                return true;
            default:
                return true;
        }
    }
}
