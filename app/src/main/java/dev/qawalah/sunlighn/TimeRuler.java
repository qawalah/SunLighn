package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * A ruler of time. The chosen moment stays under the mark in the middle and the ruler slides beneath it: drag or
 * fling. A strip shows the light (night, twilight, blue hour, golden hour, day) and small marks show the moments
 * worth stopping at (the sun or moon on your line, sunrise, sunset); the ruler settles on a mark that is near.
 */
final class TimeRuler extends View {
    interface Listener { void onTime(long ms); }

    /** A moment the ruler stops at. */
    static final class Mark {
        final long timeMs;
        final String label;
        final int color;
        Mark(long timeMs, String label, int color) {
            this.timeMs = timeMs;
            this.label = label;
            this.color = color;
        }
    }

    private final Ui.Palette p;
    private final float k, sp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final OverScroller scroller;
    private VelocityTracker velocity;
    private final float pxPerMs;
    private Listener listener;

    private long timeMs;
    private double lat, lon;
    private TimeZone tz = TimeZone.getDefault();
    private SimpleDateFormat hourFormat, dayFormat;
    private java.util.List<Mark> marks = new java.util.ArrayList<>();
    private float lastX;
    private boolean dragging;
    private long flingBase;
    private Mark under;

    TimeRuler(Context c, Ui.Palette p) {
        super(c);
        this.p = p;
        k = Ui.dp(c, 1);
        sp = c.getResources().getDisplayMetrics().scaledDensity;
        pxPerMs = 64 * k / 3600000f;                // an hour is 64 dp
        scroller = new OverScroller(c);
        text.setTypeface(Ui.font(c, 600));
        setContentDescription("Time ruler. Drag sideways to change the time.");
    }

    void setListener(Listener l) { listener = l; }

    void setPlace(double lat, double lon, TimeZone tz) {
        this.lat = lat;
        this.lon = lon;
        this.tz = tz;
        hourFormat = Fmt.format(getContext(), android.text.format.DateFormat.is24HourFormat(getContext()) ? "H" : "j", tz);
        dayFormat = Fmt.format(getContext(), "MMMd", tz);   // never "Sun" for Sunday (3.3)
        invalidate();
    }

    void setMarks(java.util.List<Mark> marks) {
        this.marks = marks;
        invalidate();
    }

    void setTime(long ms) {
        timeMs = ms;
        invalidate();
    }

    float pxPerMs() { return pxPerMs; }

    /** Moves the time as if the ruler had been dragged dx pixels (the viewfinder's sideways drag). */
    void dragBy(float dx) {
        scroller.forceFinished(true);
        move(timeMs - (long) (dx / pxPerMs));
    }

    /** Lets go after a drag that came from elsewhere: settle on a mark if one is near. */
    void settle() {
        Mark m = near(14 * k);
        if (m != null) move(m.timeMs);
    }

    private void move(long ms) {
        timeMs = ms;
        Mark m = near(3 * k);
        if (m != null && m != under) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        under = m;
        invalidate();
        if (listener != null) listener.onTime(timeMs);
    }

    private Mark near(float px) {
        Mark best = null;
        long reach = (long) (px / pxPerMs), bestD = Long.MAX_VALUE;
        for (Mark m : marks) {
            long d = Math.abs(m.timeMs - timeMs);
            if (d <= reach && d < bestD) { best = m; bestD = d; }
        }
        return best;
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(76 * k));
    }

    private float x(long t) { return getWidth() / 2f + (t - timeMs) * pxPerMs; }

    @Override protected void onDraw(Canvas c) {
        if (hourFormat == null) return;
        int w = getWidth();
        float stripTop = 26 * k, stripBottom = 36 * k, mid = w / 2f;
        long from = timeMs - (long) (mid / pxPerMs), to = timeMs + (long) (mid / pxPerMs);

        // The light, in columns narrow enough to look continuous.
        paint.setStyle(Paint.Style.FILL);
        float col = 4 * k;
        for (float px = 0; px < w; px += col) {
            long t = timeMs + (long) ((px + col / 2 - mid) / pxPerMs);
            paint.setColor(Light.color(Light.of(Astro.sun(t, lat, lon)[1])));
            c.drawRect(px, stripTop, Math.min(w, px + col), stripBottom, paint);
        }

        // Hours, and the day where it changes.
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(from);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        text.setTextSize(11.5f * sp);
        text.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(Math.max(1, k));
        for (long t = cal.getTimeInMillis(); t <= to + 1800000L; t += 1800000L) {
            float px = x(t);
            cal.setTimeInMillis(t);
            boolean hour = cal.get(Calendar.MINUTE) == 0;
            boolean midnight = hour && cal.get(Calendar.HOUR_OF_DAY) == 0;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(midnight ? p.text : hour ? p.muted : p.line);
            c.drawLine(px, stripBottom + 3 * k, px, stripBottom + (midnight ? 14 : hour ? 9 : 5) * k, paint);
            if (hour) {
                text.setColor(midnight ? p.text : p.muted);
                c.drawText(midnight ? dayFormat.format(new Date(t)) : hourFormat.format(new Date(t)),
                        px, stripBottom + 28 * k, text);
            }
        }

        // The moments worth stopping at.
        text.setTextSize(11 * sp);
        float lastLabelEnd = -1e9f;
        for (Mark m : marks) {
            if (m.timeMs < from || m.timeMs > to) continue;
            float px = x(m.timeMs);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(m.color);
            path.reset();
            path.moveTo(px, stripTop - 2 * k);
            path.lineTo(px - 4.5f * k, stripTop - 9 * k);
            path.lineTo(px + 4.5f * k, stripTop - 9 * k);
            path.close();
            c.drawPath(path, paint);
            float half = text.measureText(m.label) / 2;
            if (px - half > lastLabelEnd + 6 * k) {
                text.setColor(m.color);
                c.drawText(m.label, Math.max(half + 2 * k, Math.min(w - half - 2 * k, px)), stripTop - 13 * k, text);
                lastLabelEnd = px + half;
            }
        }

        // The mark in the middle: the chosen moment.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * k);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(p.accent);
        c.drawLine(mid, stripTop - 3 * k, mid, stripBottom + 12 * k, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                lastX = e.getX();
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                move(timeMs - (long) ((e.getX() - lastX) / pxPerMs));
                lastX = e.getX();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                velocity.computeCurrentVelocity(1000);
                float v = velocity.getXVelocity();
                velocity.recycle();
                velocity = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (Math.abs(v) > ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity() * 4) {
                    flingBase = timeMs;
                    scroller.fling(0, 0, (int) -v, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
                    postInvalidateOnAnimation();
                } else {
                    settle();
                }
                return true;
            }
        }
        return true;
    }

    @Override public void computeScroll() {
        if (dragging || scroller.isFinished()) return;
        if (scroller.computeScrollOffset()) {
            move(flingBase + (long) (scroller.getCurrX() / pxPerMs));
            if (scroller.isFinished()) settle();
            else postInvalidateOnAnimation();
        }
    }
}
