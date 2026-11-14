package dev.qawalah.sunlighn;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Settings as a bottom sheet over the map (CrossPaths style): theme, accent colour, map look, and the tutorial.
 * Tapping outside, the close button or Back closes it.
 */
final class SettingsSheet extends FrameLayout {
    interface Listener {
        void onTheme(int theme);
        void onAccent(int accent);
        void onMapStyle(int style);
        void onHelp();
        void onClose();
    }

    private final Ui.Palette p;
    private final Listener listener;
    private final String[] mapStyles;
    private int mapStyle;
    private final LinearLayout content;
    private final LinearLayout sheet;
    private int insetBottom;

    SettingsSheet(Context c, Ui.Palette p, String[] mapStyles, int mapStyle, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        this.mapStyles = mapStyles;
        this.mapStyle = mapStyle;
        setBackgroundColor(0x99000000);
        setClickable(true);
        setOnClickListener(v -> listener.onClose());

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Ui.shape(c, p.panel, p.line, 22, true));
        sheet.setClickable(true); // taps on the sheet must not close it
        LayoutParams sp = new LayoutParams(-1, -2, Gravity.BOTTOM);
        addView(sheet, sp);

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        Ui.CappedScrollView scroll = new Ui.CappedScrollView(c, 0.8f);
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        applyPadding();
        build();
    }

    void setInsets(int bottom) {
        insetBottom = bottom;
        applyPadding();
    }

    private void applyPadding() {
        int d = Ui.dp(getContext(), 20);
        content.setPadding(d, d, d, insetBottom + d);
    }

    void setMapStyle(int style) {
        mapStyle = style;
        build();
    }

    private void build() {
        Context c = getContext();
        content.removeAllViews();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(c, "Settings", 24, 700, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.IconButton close = new Ui.IconButton(c, p, Ui.Icon.CLOSE, "Close settings");
        close.setOnClickListener(v -> listener.onClose());
        head.addView(close);
        content.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // Theme
        section("Theme");
        int theme = Ui.theme(c);
        String[] themes = {"System", "Light", "Dark"};
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < 3; i++) {
            final int t = i;
            TextView chip = Ui.chip(c, p, themes[i], theme == i);
            chip.setOnClickListener(v -> listener.onTheme(t));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.setMarginStart(Ui.dp(c, 8));
            row.addView(chip, l);
        }
        content.addView(row, below(10));

        // Accent: one colour, used for the main action, the selection and what is happening now.
        section("Accent");
        int accent = Ui.accentIndex(c);
        LinearLayout swatches = new LinearLayout(c);
        for (int i = 0; i < Ui.ACCENT_NAMES.length; i++) {
            final int a = i;
            FrameLayout sw = new FrameLayout(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Ui.accentColor(i, p.night));
            if (accent == i) g.setStroke(Ui.dp(c, 3), p.text);
            sw.setBackground(g);
            sw.setContentDescription(Ui.ACCENT_NAMES[i]);
            sw.setClickable(true);
            Ui.pressable(sw);
            sw.setOnClickListener(v -> listener.onAccent(a));
            if (accent == i) {
                android.widget.ImageView check = new android.widget.ImageView(c);
                android.graphics.drawable.Drawable d = c.getDrawable(R.drawable.ic_check).mutate();
                d.setTint(p.ink);
                check.setImageDrawable(d);
                sw.addView(check, new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
            }
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
            if (i > 0) l.setMarginStart(Ui.dp(c, 12));
            swatches.addView(sw, l);
        }
        content.addView(swatches, below(10));

        // Map look
        section("Map");
        LinearLayout maps = new LinearLayout(c);
        for (int i = 0; i < mapStyles.length; i++) {
            final int m = i;
            TextView chip = Ui.chip(c, p, mapStyles[i], mapStyle == i);
            chip.setOnClickListener(v -> listener.onMapStyle(m));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.setMarginStart(Ui.dp(c, 8));
            maps.addView(chip, l);
        }
        content.addView(maps, below(10));

        TextView help = Ui.button(c, p, "How it works", false);
        help.setOnClickListener(v -> listener.onHelp());
        LinearLayout.LayoutParams hl = below(24);
        content.addView(help, hl);
    }

    private void section(String title) {
        content.addView(Ui.text(getContext(), title, 16, 700, p.text), below(20));
    }

    private LinearLayout.LayoutParams below(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.topMargin = Ui.dp(getContext(), dp);
        return l;
    }
}
