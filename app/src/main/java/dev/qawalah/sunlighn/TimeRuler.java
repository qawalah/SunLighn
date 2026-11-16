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
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(104 * k));
    }

    private float x(long t) { return getWidth() / 2f + (t - timeMs) * pxPerMs; }

    /** The light's short names for the strip (3.13.1: the colours were not named). */
    private static String shortName(Light.Kind kind) {
        switch (kind) {
            case DAY: return "Day";
            case GOLDEN: return "Golden";
            case BLUE: return "Blue";
            case TWILIGHT: return "Twilight";
            default: return "Night";
        }
    }

    /**
     * 3.13.1 (owner: "this is confusing"): every moment worth stopping at is a pin with its name and time on one of two
     * lanes, so none hides another; the strip names its lights; the time shown is a white playhead, not the colour of
     * any mark.
     */
    @Override protected void onDraw(Canvas c) {
        if (hourFormat == null) return;
        int w = getWidth();
        float lane1 = 30 * k, lane2 = 14 * k, stripTop = 42 * k, stripBottom = 60 * k, mid = w / 2f;
        long from = timeMs - (long) (mid / pxPerMs), to = timeMs + (long) (mid / pxPerMs);

        // The light, in columns narrow enough to look continuous, and each stretch named where it has room.
        paint.setStyle(Paint.Style.FILL);
        float col = 4 * k;
        Light.Kind runKind = null;
        float runStart = 0;
        text.setTextSize(10.5f * sp);
        text.setTextAlign(Paint.Align.CENTER);
        for (float px = 0; px <= w; px += col) {
            Light.Kind kind = px >= w ? null : Light.of(Astro.sun(timeMs + (long) ((px + col / 2 - mid) / pxPerMs), lat, lon)[1]);
            if (kind != null) {
                paint.setColor(Light.color(kind));
                c.drawRect(px, stripTop, Math.min(w, px + col), stripBottom, paint);
            }
            if (kind != runKind) {
                if (runKind != null) nameRun(c, runKind, runStart, Math.min(w, px), stripTop, stripBottom);
                runKind = kind;
                runStart = px;
            }
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

        // The moments worth stopping at: a pin down to the strip, its name and time on a free lane.
        text.setTextSize(11.5f * sp);
        float end1 = -1e9f, end2 = -1e9f;
        java.util.List<Mark> sorted = new java.util.ArrayList<>(marks);
        java.util.Collections.sort(sorted, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        for (Mark m : sorted) {
            if (m.timeMs < from || m.timeMs > to) continue;
            float px = x(m.timeMs);
            String label = m.label + " " + Fmt.time(getContext(), m.timeMs, tz);
            float half = text.measureText(label) / 2;
            float lx = Math.max(half + 2 * k, Math.min(w - half - 2 * k, px));
            float lane;
            if (lx - half > end1 + 8 * k) { lane = lane1; end1 = lx + half; }
            else if (lx - half > end2 + 8 * k) { lane = lane2; end2 = lx + half; }
            else lane = Float.NaN;                                // a third at the same place: the pin alone
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * k);
            paint.setColor(m.color);
            c.drawLine(px, Float.isNaN(lane) ? stripTop - 8 * k : lane + 4 * k, px, stripTop, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(px, stripTop, 3 * k, paint);
            if (!Float.isNaN(lane)) {
                text.setColor(m.color);
                c.drawText(label, lx, lane, text);
            }
        }

        // The time shown: a white playhead across the strip, a small handle under it.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        c.drawRect(mid - 1.5f * k, stripTop - 4 * k, mid + 1.5f * k, stripBottom + 4 * k, paint);
        path.reset();
        path.moveTo(mid, stripBottom + 2 * k);
        path.lineTo(mid - 6 * k, stripBottom + 10 * k);
        path.lineTo(mid + 6 * k, stripBottom + 10 * k);
        path.close();
        c.drawPath(path, paint);
    }

    private void nameRun(Canvas c, Light.Kind kind, float a, float b, float top, float bottom) {
        String name = shortName(kind);
        if (b - a < text.measureText(name) + 10 * k) return;
        boolean lightGround = kind == Light.Kind.DAY || kind == Light.Kind.GOLDEN;
        text.setColor(lightGround ? 0xFF1B1A18 : 0xFFF2F0EA);
        float cx = Math.max(a + text.measureText(name) / 2 + 4 * k, Math.min(b - text.measureText(name) / 2 - 4 * k, (a + b) / 2));
        c.drawText(name, cx, (top + bottom) / 2 + text.getTextSize() * 0.36f, text);
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
