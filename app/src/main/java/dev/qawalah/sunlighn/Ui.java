package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

    enum Icon { LOCATE, HELP, CLEAR, LAYERS }

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
                case CLEAR:
                    cv.drawLine(6 * k, 6 * k, 18 * k, 18 * k, paint);
                    cv.drawLine(18 * k, 6 * k, 6 * k, 18 * k, paint);
                    break;
            }
        }
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
