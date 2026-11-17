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
        void onDesign(int design);
        void onAccent(int accent);
        void onMapStyle(int style);
        void onMapColours(int colours);
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

    void setMapColours(int k) { build(); }

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

        // Help first: it is the only place that says what the app does.
        TextView help = Ui.button(c, p, "How it works", false);
        help.setOnClickListener(v -> listener.onHelp());
        LinearLayout.LayoutParams hl = below(16);
        content.addView(help, hl);

        // Design (3.7): the two redesigns and the 3.6 look, to try each on the phone.
        content.addView(Ui.dropdown(c, p, "Design", Ui.DESIGN_NAMES, Ui.design(c), listener::onDesign), full(20));

        // Three picks, each a dropdown: the field says what is chosen, the list opens from the bottom.
        int theme = Ui.theme(c);
        content.addView(Ui.dropdown(c, p, "Theme", new String[]{"System", "Light", "Dark"}, theme,
                listener::onTheme), full(12));

        // Accent: one colour, used for the main action, the selection and what is happening now. "Sun and moon"
        // (the default) turns it amber for the sun and violet for the moon.
        boolean moon = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("moon", false);
        String[] names = new String[Ui.ACCENT_NAMES.length + 1];
        int[] colours = new int[names.length];
        names[0] = "Sun and moon";
        colours[0] = Ui.accentColor(Ui.resolveAccent(Ui.ACCENT_FOLLOW, moon), p.night);
        for (int i = 0; i < Ui.ACCENT_NAMES.length; i++) {
            names[i + 1] = Ui.ACCENT_NAMES[i];
            colours[i + 1] = Ui.accentColor(i, p.night);
        }
        content.addView(Ui.dropdown(c, p, "Accent", names, colours, Ui.accentIndex(c) + 1,
                i -> listener.onAccent(i - 1)), full(12));

        content.addView(Ui.dropdown(c, p, "Map look", mapStyles, mapStyle, listener::onMapStyle), full(12));
        int mapColours = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("map_colours", 0);
        content.addView(Ui.dropdown(c, p, "Map colours", MainActivity.MAP_COLOURS, mapColours, listener::onMapColours), full(12));

        // The 3D map (3.15, owner: "tilt to see the map in 3D, as an option"): on by default where the phone can run it;
        // Off is the flat map of before. The change restarts the screen, like Theme.
        if (GeoMap.canRun(c) && GeoMap.libreAllowed) {
            content.addView(Ui.text(c, "3D map", 14, 600, p.text), full(20));
            content.addView(Ui.segmented(c, p, new String[]{"On", "Off"}, GeoMap.wanted(c) ? 0 : 1, i -> {
                c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putBoolean("map3d", i == 0).apply();
                if (c instanceof android.app.Activity) ((android.app.Activity) c).recreate();
            }), full(8));
            content.addView(Ui.text(c, "On, the map tilts with two fingers or the 3D key, turns, and stands the buildings "
                    + "up. Off, it is the flat map of before.", 12.5f, 400, p.muted), full(6));
        }

        // Buildings (3.14, owner: a sunset went behind a block): optional, an either/or setting
        content.addView(Ui.text(c, "Buildings", 14, 600, p.text), full(20));
        content.addView(Ui.segmented(c, p, new String[]{"Count them", "Leave out"},
                c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("buildings", true) ? 0 : 1,
                i -> {
                    Buildings.set(c, i == 0);
                    build();
                }), full(8));
        content.addView(Ui.text(c, "Counted, buildings in the way leave out the times the sun or moon is behind them, "
                + "and the viewfinder draws them. Where a height is not mapped, about three floors is guessed. "
                + "Buildings from OpenFreeMap, © OpenMapTiles, © OpenStreetMap contributors.",
                12.5f, 400, p.muted), full(6));
    }

    private LinearLayout.LayoutParams full(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), dp);
        return l;
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
