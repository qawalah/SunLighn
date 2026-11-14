package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Colours, shapes and small views in the DevStore design language (see CrossPaths DESIGN.md). */
final class Ui {
    private Ui() {}

    // Palette, night and day twins. Always read through Palette so the theme is never frozen.
    static final class Palette {
        final boolean night;
        final int bg, panel, raised, line, text, muted, accent, ink, amber, violet, bad;
        Palette(Context c) {
            night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            bg = night ? 0xFF0B1220 : 0xFFF3F5FA;
            panel = night ? 0xFF121A2B : 0xFFFFFFFF;
            raised = night ? 0xFF18233A : 0xFFEEF1F7;
            line = night ? 0xFF25324D : 0xFFDBE1EC;
            text = night ? 0xFFE8EEFC : 0xFF0F172A;
            muted = night ? 0xFF8A97B4 : 0xFF5B6578;
            accent = night ? 0xFF2DD4BF : 0xFF0D9488;
            ink = night ? 0xFF04222A : 0xFFFFFFFF;
            amber = night ? 0xFFFBBF24 : 0xFFB45309;
            violet = night ? 0xFFA78BFA : 0xFF7C3AED;
            bad = night ? 0xFFF87171 : 0xFFDC2626;
        }
    }

    static final int ROUTE_BLUE = 0xFF0A84FF;
    static final int ROUTE_GREEN = 0xFF34C759;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Flat rounded surface with a 1dp border. Radii in dp, one per corner is not needed: top-only sheets use topOnly. */
    static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp, boolean topOnly) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        float r = dp(c, radiusDp);
        if (topOnly) g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        else g.setCornerRadius(r);
        return g;
    }

    static TextView text(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    /** A button: primary (accent fill) or ghost (raised fill with border). */
    static TextView button(Context c, Palette p, String label, boolean primary) {
        TextView b = text(c, label, 15, true, primary ? p.ink : p.text);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackground(shape(c, primary ? p.accent : p.raised, primary ? 0 : p.line, 12, false));
        b.setClickable(true);
        return b;
    }

    /** A pill chip, selected or not. */
    static TextView chip(Context c, Palette p, String label, boolean selected) {
        TextView t = text(c, label, 12.5f, true, selected ? p.ink : p.text);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7));
        t.setBackground(shape(c, selected ? p.accent : p.raised, selected ? 0 : p.line, 99, false));
        t.setClickable(true);
        return t;
    }

    enum Icon { LOCATE, HELP, CLEAR, LAYERS, CHEV_LEFT, CHEV_RIGHT }

    /** Round button with a drawn line icon (24 unit grid, 1.8 stroke, round caps). */
    static final class IconButton extends View {
        private final Icon icon;
        private final Palette p;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconButton(Context c, Palette p, Icon icon, String description) {
            super(c);
            this.p = p;
            this.icon = icon;
            setContentDescription(description);
            setClickable(true);
            setBackground(shape(c, p.panel, p.line, 24, false));
        }

        @Override protected void onMeasure(int w, int h) {
            int s = dp(getContext(), 48);
            setMeasuredDimension(s, s);
        }

        @Override protected void onDraw(Canvas cv) {
            float u = getWidth() / 48f;           // 48dp button, icon 24 units drawn at 1dp per unit
            float k = dp(getContext(), 1);
            cv.translate(getWidth() / 2f - 12 * k, getHeight() / 2f - 12 * k);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * k);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(isEnabled() ? p.text : p.muted);
            switch (icon) {
                case LOCATE:
                    cv.drawCircle(12 * k, 12 * k, 6 * k, paint);
                    cv.drawLine(12 * k, 2 * k, 12 * k, 6 * k, paint);
                    cv.drawLine(12 * k, 18 * k, 12 * k, 22 * k, paint);
                    cv.drawLine(2 * k, 12 * k, 6 * k, 12 * k, paint);
                    cv.drawLine(18 * k, 12 * k, 22 * k, 12 * k, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12 * k, 12 * k, 1.6f * k, paint);
                    break;
                case HELP:
                    cv.drawCircle(12 * k, 12 * k, 9 * k, paint);
                    Path q = new Path();
                    q.moveTo(9.5f * k, 9.5f * k);
                    q.cubicTo(9.5f * k, 6.5f * k, 14.5f * k, 6.5f * k, 14.5f * k, 9.5f * k);
                    q.cubicTo(14.5f * k, 11.5f * k, 12 * k, 11.8f * k, 12 * k, 14 * k);
                    cv.drawPath(q, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12 * k, 17.2f * k, 1.1f * k, paint);
                    break;
                case LAYERS: {
                    Path top = new Path();
                    top.moveTo(12 * k, 3 * k);
                    top.lineTo(21 * k, 8 * k);
                    top.lineTo(12 * k, 13 * k);
                    top.lineTo(3 * k, 8 * k);
                    top.close();
                    cv.drawPath(top, paint);
                    Path mid = new Path();
                    mid.moveTo(3 * k, 12 * k);
                    mid.lineTo(12 * k, 17 * k);
                    mid.lineTo(21 * k, 12 * k);
                    cv.drawPath(mid, paint);
                    Path low = new Path();
                    low.moveTo(3 * k, 16 * k);
                    low.lineTo(12 * k, 21 * k);
                    low.lineTo(21 * k, 16 * k);
                    cv.drawPath(low, paint);
                    break;
                }
                case CHEV_LEFT:
                case CHEV_RIGHT: {
                    float dir = icon == Icon.CHEV_LEFT ? 1 : -1;      // 1 points left
                    Path ch = new Path();
                    ch.moveTo((12 + 3.5f * dir) * k, 5 * k);
                    ch.lineTo((12 - 3.5f * dir) * k, 12 * k);
                    ch.lineTo((12 + 3.5f * dir) * k, 19 * k);
                    cv.drawPath(ch, paint);
                    break;
                }
                case CLEAR:
                    cv.drawLine(6 * k, 6 * k, 18 * k, 18 * k, paint);
                    cv.drawLine(18 * k, 6 * k, 6 * k, 18 * k, paint);
                    break;
            }
        }
    }

    // ---- the pin (CrossPaths): a white pin with a dark outline, tip on the point, soft halo and ground shadow ----

    private static final Path PIN = new Path();
    static {
        // The app icon's pin on a 24 unit grid (CrossPaths icons.js), tip at (12, 21.2).
        PIN.moveTo(12, 2.8f);
        PIN.cubicTo(8.4f, 2.8f, 5.6f, 5.6f, 5.6f, 9.2f);
        PIN.cubicTo(5.6f, 14f, 12, 21.2f, 12, 21.2f);
        PIN.cubicTo(12, 21.2f, 18.4f, 14f, 18.4f, 9.2f);
        PIN.cubicTo(18.4f, 5.6f, 15.6f, 2.8f, 12, 2.8f);
        PIN.close();
    }
    /** Height of the pin above its tip, in grid units. */
    static final float PIN_HEIGHT = 18.4f;

    /** The pale halo round the point the pin marks. {@code grow} runs 0..1. */
    static void drawPinHalo(Canvas cv, float tipX, float tipY, float u, int color, float grow) {
        if (grow <= 0) return;
        Paint ph = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = 8.8f * u * grow;
        ph.setStyle(Paint.Style.FILL);
        ph.setColor(withAlpha(color, 41));
        cv.drawCircle(tipX, tipY, r, ph);
        ph.setStyle(Paint.Style.STROKE);
        ph.setStrokeWidth(Math.max(1f, 0.45f * u));
        ph.setColor(withAlpha(color, 115));
        cv.drawCircle(tipX, tipY, r, ph);
    }

    /**
     * The pin with its tip on (tipX, tipY). {@code u} is pixels per grid unit, {@code rise} moves it up (negative)
     * or down, {@code fade} 0..1 fades it and its shadow in. The shadow stays on the ground.
     */
    static void drawPin(Canvas cv, float tipX, float tipY, float u, float rise, float fade) {
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float shadowW = 7.6f * u * (0.3f + 0.7f * Math.max(0, Math.min(1, fade)));
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(Color.argb((int) (89 * fade), 3, 10, 25));
        cv.drawOval(new RectF(tipX - shadowW / 2, tipY - 0.9f * u, tipX + shadowW / 2, tipY + 1.5f * u), pp);

        cv.save();
        cv.translate(tipX - 12 * u, tipY - 21.2f * u + rise);
        cv.scale(u, u);
        pp.setStrokeJoin(Paint.Join.ROUND);
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(Color.argb((int) (255 * fade), 255, 255, 255));
        cv.drawPath(PIN, pp);
        cv.drawCircle(12, 9.2f, 2.4f, pp);
        pp.setStyle(Paint.Style.STROKE);
        pp.setStrokeWidth(1.75f);
        pp.setColor(Color.argb((int) (255 * fade), 0x1b, 0x25, 0x37));
        cv.drawPath(PIN, pp);
        cv.drawCircle(12, 9.2f, 2.4f, pp);
        cv.restore();
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** A scroll area that never grows past a share of the screen height, so a tall card cannot cover the map. */
    static final class CappedScrollView extends android.widget.ScrollView {
        private final float share;
        CappedScrollView(Context c, float share) {
            super(c);
            this.share = share;
            setVerticalScrollBarEnabled(false);
        }
        @Override protected void onMeasure(int w, int h) {
            int cap = (int) (getResources().getDisplayMetrics().heightPixels * share);
            int given = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? cap : Math.min(cap, MeasureSpec.getSize(h));
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(given, MeasureSpec.AT_MOST));
        }
    }

    /** Sun and moon pictures for the results, in the same line style as the other icons (24 unit grid, 1.8 stroke). */
    static final class GlyphView extends View {
        enum Kind { SUN, MOON, SUN_HORIZON, MOON_HORIZON }

        private final Kind kind;
        private final int color;
        private final int px;
        private float lit = 1f;
        private boolean waxing = true;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlyphView(Context c, Kind kind, int color, int sizeDp) {
            super(c);
            this.kind = kind;
            this.color = color;
            this.px = dp(c, sizeDp);
        }

        /** How much of the moon is lit (0..1) and which side. */
        void setMoon(float lit, boolean waxing) {
            this.lit = lit;
            this.waxing = waxing;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(px, px);
        }

        @Override protected void onDraw(Canvas cv) {
            float s = getWidth() / 24f;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(1.8f * s);
            paint.setColor(color);
            switch (kind) {
                case SUN: sun(cv, 12 * s, 12 * s, 4.2f * s, 7.4f * s, 9.9f * s, 0, 360, 8); break;
                case SUN_HORIZON:
                    // Half a sun rising over a horizon line.
                    paint.setStyle(Paint.Style.STROKE);
                    RectF half = new RectF(7 * s, 13 * s, 17 * s, 23 * s);
                    cv.drawArc(half, 180, 180, false, paint);
                    for (int i = 0; i < 5; i++) {
                        double a = Math.toRadians(180 + i * 45);
                        cv.drawLine(12 * s + (float) Math.cos(a) * 8.2f * s, 18 * s + (float) Math.sin(a) * 8.2f * s,
                                12 * s + (float) Math.cos(a) * 10.8f * s, 18 * s + (float) Math.sin(a) * 10.8f * s, paint);
                    }
                    cv.drawLine(2.5f * s, 18 * s, 21.5f * s, 18 * s, paint);
                    cv.drawLine(7 * s, 21.5f * s, 17 * s, 21.5f * s, paint);
                    break;
                case MOON: moon(cv, 12 * s, 12 * s, 8 * s, s); break;
                case MOON_HORIZON:
                    moon(cv, 12 * s, 10.5f * s, 5.6f * s, s);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(color);
                    cv.drawLine(2.5f * s, 18.5f * s, 21.5f * s, 18.5f * s, paint);
                    cv.drawLine(7 * s, 21.5f * s, 17 * s, 21.5f * s, paint);
                    break;
            }
        }

        private void sun(Canvas cv, float cx, float cy, float r, float r1, float r2, float from, float to, int rays) {
            paint.setStyle(Paint.Style.STROKE);
            cv.drawCircle(cx, cy, r, paint);
            for (int i = 0; i < rays; i++) {
                double a = Math.toRadians(from + (to - from) * i / rays);
                cv.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                        cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, paint);
            }
        }

        /** The moon with its real lit share: a faint disc, an outline and the lit part filled. */
        private void moon(Canvas cv, float cx, float cy, float r, float s) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(color, 40));
            cv.drawCircle(cx, cy, r, paint);
            paint.setColor(color);
            if (lit > 0.98f) {
                cv.drawCircle(cx, cy, r, paint);
            } else if (lit > 0.02f) {
                Path path = new Path();
                RectF circle = new RectF(cx - r, cy - r, cx + r, cy + r);
                float rx = r * Math.abs(1 - 2 * lit);
                RectF term = new RectF(cx - rx, cy - r, cx + rx, cy + r);
                path.arcTo(circle, -90, 180);                    // the right half, top to bottom
                path.arcTo(term, 90, lit > 0.5f ? 180 : -180);   // back up along the shadow edge
                path.close();
                cv.save();
                if (!waxing) cv.scale(-1, 1, cx, cy);
                cv.drawPath(path, paint);
                cv.restore();
            }
            paint.setStyle(Paint.Style.STROKE);
            cv.drawCircle(cx, cy, r, paint);
        }
    }
}
