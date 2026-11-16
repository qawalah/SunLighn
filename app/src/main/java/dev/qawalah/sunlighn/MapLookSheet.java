package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The map's look (3.8, owner: "I also want to change map style and colors"), opened from the layers key: the style
 * and the colours, each change seen at once on the map above the sheet. Tapping outside, the close key or Back
 * closes it. The same two choices are in Settings.
 */
final class MapLookSheet extends FrameLayout {
    interface Listener {
        void onStyle(int style);
        void onColours(int colours);
        void onClose();
    }

    /** A swatch per colour choice, drawn as two halves: the land and the water it gives. */
    private static final int[][] SWATCH = {
            {0xFFEDEBE4, 0xFFBCD0DE},   // Soft
            {0xFFF2EFE9, 0xFFAAD3DF},   // Natural
            {0xFFEFE3CC, 0xFFC9C2A8},   // Paper
            {0xFFE6E6E6, 0xFFBDBDBD},   // Grey
            {0xFF1E2636, 0xFF14304A},   // Dark
    };

    private final Ui.Palette p;
    private final Listener listener;
    private final String[] styles;
    private int style, colours;
    private final LinearLayout content;
    private int insetBottom;

    MapLookSheet(Context c, Ui.Palette p, String[] styles, int style, int colours, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        this.styles = styles;
        this.style = style;
        this.colours = colours;
        setBackgroundColor(0x26000000);              // light: the map is to be seen through
        setClickable(true);
        setOnClickListener(v -> listener.onClose());
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(Ui.shape(c, p.panel, p.line, 22, true));
        content.setClickable(true);
        content.setElevation(Ui.dp(c, 8));
        addView(content, new LayoutParams(-1, -2, Gravity.BOTTOM));
        build();
    }

    void setInsets(int bottom) {
        insetBottom = bottom;
        build();
    }

    private void build() {
        Context c = getContext();
        content.removeAllViews();
        int d = Ui.dp(c, 20);
        content.setPadding(d, Ui.dp(c, 16), d, insetBottom + d);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(c, "Map look", 22, 700, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.IconButton close = new Ui.IconButton(c, p, Ui.Icon.CLOSE, "Close map look");
        close.setOnClickListener(v -> listener.onClose());
        head.addView(close);
        content.addView(head);

        content.addView(Ui.text(c, "Style", 13, 600, p.muted), top(14));
        content.addView(Ui.segmented(c, p, styles, style, i -> {
            style = i;
            listener.onStyle(i);
            build();
        }), top(6));

        content.addView(Ui.text(c, "Colours", 13, 600, p.muted), top(18));
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < MainActivity.MAP_COLOURS.length; i++) {
            final int idx = i;
            boolean on = i == colours;
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            GradientDrawable bg = Ui.shape(c, on ? Ui.withAlpha(p.accent, 26) : 0, on ? p.accent : 0, 14, false);
            if (on) bg.setStroke(Math.max(1, Ui.dp(c, 2)), p.accent);
            tile.setBackground(bg);
            tile.addView(new Swatch(c, SWATCH[i], p.night), new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
            TextView t = Ui.text(c, MainActivity.MAP_COLOURS[i], 12, on ? 700 : 500, on ? p.text : p.muted);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = Ui.dp(c, 6);
            tile.addView(t, tl);
            tile.setClickable(true);
            tile.setContentDescription("Map colours: " + MainActivity.MAP_COLOURS[i] + (on ? ", chosen" : ""));
            tile.setOnClickListener(v -> {
                colours = idx;
                listener.onColours(idx);
                build();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) l.setMarginStart(Ui.dp(c, 6));
            row.addView(tile, l);
        }
        content.addView(row, top(6));
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), dp);
        return l;
    }

    /** A round swatch, its lower right the water colour. */
    private static final class Swatch extends View {
        private final int[] colours;
        private final boolean night;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Swatch(Context c, int[] colours, boolean night) {
            super(c);
            this.colours = colours;
            this.night = night;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            float r = getWidth() / 2f;
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(r, r, r, android.graphics.Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            paint.setColor(colours[0]);
            cv.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(colours[1]);
            android.graphics.Path water = new android.graphics.Path();
            water.moveTo(getWidth(), getHeight() * 0.35f);
            water.lineTo(getWidth(), getHeight());
            water.lineTo(getWidth() * 0.3f, getHeight());
            water.close();
            cv.drawPath(water, paint);
            cv.restore();
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, getResources().getDisplayMetrics().density));
            paint.setColor(night ? 0x73FFFFFF : 0x33000000);   // the Dark swatch stays seen at night
            cv.drawCircle(r, r, r - paint.getStrokeWidth() / 2, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
        }
    }
}
