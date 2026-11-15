package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * A slider with two thumbs for a range of values, snapping to fixed stops (3.4.3: the lens, as a zoom range).
 * Drag either thumb; the nearer one moves. The thumbs never cross.
 */
final class RangeBar extends View {
    interface Listener { void onRange(int lo, int hi, boolean settled); }

    private final int[] stops;
    private final Ui.Palette p;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float k;
    private int lo, hi;
    private int dragging = -1;            // 0 the low thumb, 1 the high one
    private Listener listener;

    RangeBar(Context c, Ui.Palette p, int[] stops, int lo, int hi) {
        super(c);
        this.p = p;
        this.stops = stops;
        this.lo = Math.max(0, Math.min(stops.length - 1, lo));
        this.hi = Math.max(this.lo, Math.min(stops.length - 1, hi));
        k = c.getResources().getDisplayMetrics().density;
        setMinimumHeight(Math.round(48 * k));
        setClickable(true);
    }

    void setListener(Listener l) { listener = l; }

    int lo() { return lo; }

    int hi() { return hi; }

    /** As a finger would set it (for tests): moves the thumbs and tells the listener, settled. */
    void userSet(int newLo, int newHi) {
        lo = Math.max(0, Math.min(stops.length - 1, newLo));
        hi = Math.max(lo, Math.min(stops.length - 1, newHi));
        invalidate();
        if (listener != null) listener.onRange(lo, hi, true);
    }

    private float pad() { return 14 * k; }

    private float xOf(int i) {
        return pad() + (getWidth() - 2 * pad()) * i / (float) (stops.length - 1);
    }

    private int indexAt(float x) {
        float t = (x - pad()) / Math.max(1, getWidth() - 2 * pad());
        return Math.max(0, Math.min(stops.length - 1, Math.round(t * (stops.length - 1))));
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Math.round(48 * k));
    }

    @Override protected void onDraw(Canvas c) {
        float y = getHeight() / 2f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4 * k);
        paint.setColor(p.line);
        c.drawLine(pad(), y, getWidth() - pad(), y, paint);
        paint.setColor(p.accent);
        c.drawLine(xOf(lo), y, xOf(hi), y, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i : new int[]{lo, hi}) c.drawCircle(xOf(i), y, 10 * k, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int i = indexAt(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = Math.abs(e.getX() - xOf(lo)) <= Math.abs(e.getX() - xOf(hi)) ? 0 : 1;
                if (lo == hi) dragging = i < lo ? 0 : 1;
                // fall through
            case MotionEvent.ACTION_MOVE:
                if (dragging == 0) lo = Math.min(i, hi);
                else hi = Math.max(i, lo);
                invalidate();
                if (listener != null) listener.onRange(lo, hi, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                if (listener != null) listener.onRange(lo, hi, true);
                return true;
        }
        return true;
    }
}
