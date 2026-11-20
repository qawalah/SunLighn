package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * One day of sun and moon: how high each is, with a daylight strip underneath, a tick wherever one sits on your
 * line, and a cursor you drag to move through the day. The map follows the cursor.
 */
final class DayChart extends View {
    interface Listener { void onCursor(long ms); }

    private final Ui.Palette p;
    private final float k;       // 1 dp in px
    private final float sp;      // 1 sp in px
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private long start, end, cursor;
    private double[] sunAlt, moonAlt;
    private boolean moon;                   // the body the tool is about: only its curve is drawn

    /** Draw only the moon (true) or only the sun. The daylight strip stays: it is the light either way. */
    void setBody(boolean moon) {
        this.moon = moon;
        invalidate();
    }
    private List<Astro.Hit> sunHits, moonHits;
    private SimpleDateFormat timeFormat;
    private Listener listener;

    // Layout, in px, worked out in onDraw.
    private float left, right, plotTop, plotBottom, bandTop, bandBottom, yMax, yMin;

    DayChart(Context c, Ui.Palette p) {
        super(c);
        this.p = p;
        this.k = Ui.dp(c, 1);
        this.sp = c.getResources().getDisplayMetrics().scaledDensity;
        setContentDescription("Day timeline. Drag along it to see where the sun and moon are at that time.");
    }

    void setData(long start, long end, double[] sunAlt, double[] moonAlt, List<Astro.Hit> sunHits,
                 List<Astro.Hit> moonHits, SimpleDateFormat timeFormat) {
        this.start = start;
        this.end = end;
        this.sunAlt = sunAlt;
        this.moonAlt = moonAlt;
        this.sunHits = sunHits;
        this.moonHits = moonHits;
        this.timeFormat = timeFormat;
        invalidate();
    }

    void setCursor(long ms) {
        cursor = Math.max(start, Math.min(end, ms));
        invalidate();
    }

    void setListener(Listener l) { listener = l; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(150 * k));
    }

    private float x(long t) { return left + (right - left) * (t - start) / (float) (end - start); }

    private float y(double alt) {
        double a = Math.max(yMin, Math.min(yMax, alt));
        return plotTop + (float) ((yMax - a) / (yMax - yMin)) * (plotBottom - plotTop);
    }

    private double altAt(double[] a, long t) {
        double f = (t - start) / (double) (end - start) * (a.length - 1);
        int i = (int) Math.floor(f);
        if (i >= a.length - 1) return a[a.length - 1];
        return a[i] + (a[i + 1] - a[i]) * (f - i);
    }

    @Override protected void onDraw(Canvas c) {
        if (sunAlt == null) return;
        left = 6 * k;
        right = getWidth() - 6 * k;
        plotTop = 34 * k;
        plotBottom = plotTop + 112 * k;
        bandTop = plotBottom + 6 * k;
        bandBottom = bandTop + 10 * k;

        double top = 40;
        for (double a : moon ? moonAlt : sunAlt) top = Math.max(top, a + 8);
        yMax = (float) (Math.ceil(top / 10) * 10);
        yMin = -16;
        int n = sunAlt.length;

        // Below the horizon: a quiet block, so "up" and "down" read at once.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(p.raised);
        c.drawRect(left, y(0), right, plotBottom, paint);

        // Height lines and the horizon.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, k));
        paint.setTextSize(10.5f * sp);
        paint.setTypeface(Ui.font(getContext(), 400));
        paint.setTextAlign(Paint.Align.LEFT);
        for (int a = 30; a < yMax; a += 30) {
            paint.setColor(Ui.withAlpha(p.line, 255));
            paint.setStyle(Paint.Style.STROKE);
            c.drawLine(left, y(a), right, y(a), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.muted);
            c.drawText(a + "°", left + 4 * k, y(a) - 3 * k, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * k);
        paint.setColor(p.muted);
        c.drawLine(left, y(0), right, y(0), paint);

        // Sun: the light you can use is the area above the horizon.
        path.reset();
        path.moveTo(left, y(0));
        for (int i = 0; i < n; i++) path.lineTo(left + (right - left) * i / (n - 1), y(Math.max(sunAlt[i], 0)));
        path.lineTo(right, y(0));
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.withAlpha(p.amber, moon ? 22 : 50));
        c.drawPath(path, paint);

        if (moon) curve(c, moonAlt, p.violet, 2.6f * k);
        else curve(c, sunAlt, p.amber, 2.6f * k);

        // Where it sits on your line.
        if (moon) tick(c, moonHits, moonAlt, p.violet);
        else tick(c, sunHits, sunAlt, p.amber);

        // The daylight strip: night, twilight, golden light, day.
        float r = 5 * k;
        c.save();
        path.reset();
        path.addRoundRect(new RectF(left, bandTop, right, bandBottom), r, r, Path.Direction.CW);
        c.clipPath(path);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n - 1; i++) {
            paint.setColor(bandColor(sunAlt[i]));
            c.drawRect(left + (right - left) * i / (n - 1), bandTop, left + (right - left) * (i + 1) / (n - 1) + 1, bandBottom, paint);
        }
        c.restore();

        // Hours.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(p.muted);
        paint.setTextSize(11 * sp);
        int[] hours = {0, 6, 12, 18, 24};
        for (int h : hours) {
            long t = Math.min(end, start + h * 3600000L);
            paint.setTextAlign(h == 0 ? Paint.Align.LEFT : h == 24 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            c.drawText(String.format("%02d", h), h == 0 ? left : h == 24 ? right : x(t), bandBottom + 16 * k, paint);
        }

        drawCursor(c);
    }

    private void curve(Canvas c, double[] a, int color, float width) {
        path.reset();
        int n = a.length;
        for (int i = 0; i < n; i++) {
            float px = left + (right - left) * i / (n - 1);
            if (i == 0) path.moveTo(px, y(a[i])); else path.lineTo(px, y(a[i]));
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(width);
        // Below the horizon it is still there, just faint.
        c.save();
        c.clipRect(left, y(0), right, plotBottom);
        paint.setColor(Ui.withAlpha(color, 90));
        c.drawPath(path, paint);
        c.restore();
        c.save();
        c.clipRect(left, plotTop - 4 * k, right, y(0));
        paint.setColor(color);
        c.drawPath(path, paint);
        c.restore();
    }

    private void tick(Canvas c, List<Astro.Hit> hits, double[] alt, int color) {
        if (hits == null) return;
        for (Astro.Hit h : hits) {
            float hx = x(h.timeMs);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * k);
            paint.setColor(Ui.withAlpha(color, 170));
            paint.setPathEffect(new DashPathEffect(new float[]{3 * k, 4 * k}, 0));
            c.drawLine(hx, plotTop, hx, plotBottom, paint);
            paint.setPathEffect(null);
            float hy = y(altAt(alt, h.timeMs));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            c.drawCircle(hx, hy, 7 * k, paint);
            paint.setColor(color);
            c.drawCircle(hx, hy, 5 * k, paint);
        }
    }

    private int bandColor(double alt) {
        if (alt < -6) return p.night ? 0xFF1E293B : 0xFF475569;
        if (alt < 0) return p.violet;
        if (alt < 6) return p.amber;
        return p.night ? 0xFFFFE9A8 : 0xFFFFD866;
    }

    private void drawCursor(Canvas c) {
        float cx = x(cursor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * k);
        paint.setColor(p.text);
        c.drawLine(cx, plotTop - 6 * k, cx, bandBottom + 3 * k, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = moon ? 1 : 0; i < (moon ? 2 : 1); i++) {
            double[] a = i == 0 ? sunAlt : moonAlt;
            int col = i == 0 ? p.amber : p.violet;
            float cy = y(altAt(a, cursor));
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, 7.5f * k, paint);
            paint.setColor(col);
            c.drawCircle(cx, cy, 5.5f * k, paint);
        }
        // The time, in a pill on top of the cursor.
        String label = timeFormat == null ? "" : timeFormat.format(new Date(cursor));
        paint.setTextSize(12.5f * sp);
        paint.setTypeface(Ui.font(getContext(), 700));
        paint.setTextAlign(Paint.Align.CENTER);
        float w = paint.measureText(label) + 20 * k, h = 24 * k;
        float px = Math.max(w / 2, Math.min(getWidth() - w / 2, cx));
        RectF pill = new RectF(px - w / 2, 2 * k, px + w / 2, 2 * k + h);
        paint.setColor(p.accent);
        c.drawRoundRect(pill, h / 2, h / 2, paint);
        paint.setColor(p.ink);
        c.drawText(label, px, pill.centerY() + paint.getTextSize() * 0.35f, paint);
        paint.setTypeface(Ui.font(getContext(), 400));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (sunAlt == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true); // dragging along the day must not scroll the card
                // fall through
            case MotionEvent.ACTION_MOVE: {
                float ex = Math.max(left, Math.min(right, e.getX()));
                long t = start + (long) ((ex - left) / (right - left) * (end - start));
                t = snap(t, ex);
                cursor = t;
                invalidate();
                if (listener != null) listener.onCursor(t);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Near a tick, land exactly on it: the moment the sun or moon is on your line. */
    private long snap(long t, float ex) {
        long best = t;
        float bestDist = 12 * k;
        for (List<Astro.Hit> hits : java.util.Collections.singletonList(moon ? moonHits : sunHits)) {
            if (hits == null) continue;
            for (Astro.Hit h : hits) {
                float d = Math.abs(x(h.timeMs) - ex);
                if (d < bestDist) {
                    bestDist = d;
                    best = h.timeMs;
                }
            }
        }
        return best;
    }
}
