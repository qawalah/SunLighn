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
        /** A tool's or the viewfinder's guide again (3.20). */
        void onGuide(Tutorial.Kind kind);
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

        // Design (3.7): the two redesigns and the 3.6 look, to try each on the phone.
        content.addView(Ui.dropdown(c, p, "Design", Ui.DESIGN_NAMES, Ui.design(c), listener::onDesign), full(16));

        // The viewfinder (3.16): two layouts, seen the next time it opens.
        content.addView(Ui.dropdown(c, p, "Viewfinder", Ui.VIEWFINDER_NAMES, Ui.viewfinder(c), i -> {
            c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).edit().putInt("viewfinder", i).apply();
            build();
        }), full(12));

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
            content.addView(Ui.note(c, p, Ui.Icon.CUBE, "On: the map tilts with two fingers or the 3D key, turns, and "
                    + "stands the buildings up."), full(8));
            content.addView(Ui.note(c, p, Ui.Icon.LAYERS, "Off: the flat map of before."), full(6));
        }

        // Shadows (3.17, owner): the sun's shadows in the viewfinder and on the 3D map
        content.addView(Ui.text(c, "Shadows", 14, 600, p.text), full(20));
        content.addView(Ui.segmented(c, p, new String[]{"On", "Off"}, Shadows.on(c) ? 0 : 1, i -> {
            Shadows.set(c, i == 0);
            build();
        }), full(8));
        content.addView(Ui.note(c, p, Ui.Icon.SUN, "On: the subject and the buildings cast the sun's shadow on the ground "
                + "in the viewfinder and on the tilted 3D map (the moon's, faintly)."), full(8));
        content.addView(Ui.note(c, p, Ui.Icon.CUBE, "Shadows follow the buildings counted below."), full(6));

        // Buildings (3.14, owner: a sunset went behind a block): optional, an either/or setting
        content.addView(Ui.text(c, "Buildings", 14, 600, p.text), full(20));
        content.addView(Ui.segmented(c, p, new String[]{"Count them", "Leave out"},
                c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("buildings", true) ? 0 : 1,
                i -> {
                    Buildings.set(c, i == 0);
                    build();
                }), full(8));
        content.addView(Ui.note(c, p, Ui.Icon.CUBE, "Counted: buildings in the way leave out the times the sun or moon is "
                + "behind them, and the viewfinder draws them."), full(8));
        content.addView(Ui.note(c, p, Ui.Icon.HEIGHT, "Where a height is not mapped, about three floors is guessed."), full(6));
        content.addView(Ui.note(c, p, Ui.Icon.INFO, "Buildings from OpenFreeMap, © OpenMapTiles, © OpenStreetMap contributors."), full(6));

        addCovers(c);
        addGuides(c);
    }

    /**
     * City covers (3.30, owner: "connect to an api to generate city covers"): a banner over each city in Saved. Photo is
     * free; Claude and GPT use the owner's own key, kept on this phone, and spend a little of that account's credit for
     * each new city (a cover is made once and kept).
     */
    private void addCovers(Context c) {
        content.addView(Ui.text(c, "City covers", 14, 600, p.text), full(20));
        int src = Covers.source(c);
        content.addView(Ui.dropdown(c, p, "Covers from", Covers.NAMES, src, i -> {
            Covers.setSource(c, i);
            build();
            if (Covers.needsKey(i) && Covers.key(c, i).isEmpty()) askKey(c, i);
        }), full(8));
        if (src == Covers.OFF) {
            content.addView(Ui.note(c, p, Ui.Icon.SAVED, "Off: Saved shows each city's name as a heading."), full(8));
            return;
        }
        if (src == Covers.PHOTO) {
            content.addView(Ui.note(c, p, Ui.Icon.SAVED, "The city's picture from its Wikipedia article. Free, no key."), full(8));
        } else {
            boolean has = !Covers.key(c, src).isEmpty();
            String who = src == Covers.CLAUDE ? "Claude" : "GPT";
            content.addView(Ui.note(c, p, Ui.Icon.SAVED, src == Covers.CLAUDE
                    ? "Claude designs a poster of the city's skyline at golden hour, and the app draws it."
                    : "GPT paints a poster of the city's skyline at golden hour."), full(8));
            content.addView(Ui.note(c, p, Ui.Icon.INFO, "Uses your own " + (src == Covers.CLAUDE ? "Anthropic" : "OpenAI")
                    + " API key and spends a little of its credit for each new city (about 2 US cents; your chat "
                    + "subscription is not used). Each cover is made once and kept on this phone."), full(6));
            TextView key = Ui.button(c, p, has ? "Change the " + who + " key" : "Enter the " + who + " key", !has);
            key.setOnClickListener(v -> askKey(c, src));
            content.addView(key, full(10));
            if (has) {
                TextView clear = Ui.button(c, p, "Remove the key", false);
                clear.setOnClickListener(v -> {
                    Covers.setKey(c, src, "");
                    build();
                });
                content.addView(clear, full(8));
            }
        }
        if (Covers.lastError != null)
            content.addView(Ui.note(c, p, Ui.Icon.WARNING, Covers.lastError), full(8));
    }

    private void askKey(Context c, int src) {
        android.widget.EditText field = new android.widget.EditText(c);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint(src == Covers.CLAUDE ? "sk-ant-…" : "sk-…");
        FrameLayout wrap = new FrameLayout(c);
        int s = Ui.dp(c, 20);
        wrap.setPadding(s, Ui.dp(c, 4), s, 0);
        wrap.addView(field);
        new android.app.AlertDialog.Builder(c, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle(src == Covers.CLAUDE ? "Your Anthropic API key" : "Your OpenAI API key")
                .setMessage("Kept only on this phone and sent only to " + (src == Covers.CLAUDE ? "Anthropic" : "OpenAI")
                        + " to make covers.")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String k = field.getText().toString().trim();
                    if (!k.isEmpty()) Covers.setKey(c, src, k);
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * The guides, at the foot (owner, 3.26: "make the guides at the bottom"): the settings come first, the guides are
     * there to see again. How it works heads the list, the way the first start opens with it.
     */
    private void addGuides(Context c) {
        content.addView(Ui.text(c, "Guides", 14, 600, p.text), full(24));
        LinearLayout guides = new LinearLayout(c);
        guides.setOrientation(LinearLayout.VERTICAL);
        // 3.24.2: one guide per thing, each said once
        Tutorial.Kind[] kinds = {Tutorial.Kind.INTRO, Tutorial.Kind.CHOOSE, Tutorial.Kind.LINE, Tutorial.Kind.FIND,
                Tutorial.Kind.BUILD, Tutorial.Kind.NOW, Tutorial.Kind.MAP, Tutorial.Kind.SHOT, Tutorial.Kind.FRAME};
        Ui.Icon[] icons = {Ui.Icon.INFO, Ui.Icon.HELP, Ui.Icon.TOOL_LINE, Ui.Icon.TOOL_FIND, Ui.Icon.TOOL_BUILD, Ui.Icon.NOW,
                Ui.Icon.LAYERS, Ui.Icon.SAVED, Ui.Icon.VIEWFINDER};
        for (int i = 0; i < kinds.length; i++) {
            final Tutorial.Kind kind = kinds[i];
            String title = kind == Tutorial.Kind.INTRO ? "How it works" : Tutorial.title(kind);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            android.widget.ImageView ic = new android.widget.ImageView(c);
            ic.setImageDrawable(Ui.icon(c, icons[i], p.accent));
            row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            TextView name = Ui.text(c, title, 15, 500, p.text);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.setMarginStart(Ui.dp(c, 12));
            row.addView(name, nl);
            android.widget.ImageView chev = new android.widget.ImageView(c);
            chev.setImageDrawable(Ui.icon(c, Ui.Icon.CHEV_RIGHT, p.muted));
            row.addView(chev, new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            row.setClickable(true);
            row.setContentDescription(kind == Tutorial.Kind.INTRO ? "How it works" : "Guide: " + title);
            if (kind == Tutorial.Kind.INTRO) row.setOnClickListener(v -> listener.onHelp());
            else row.setOnClickListener(v -> listener.onGuide(kind));
            guides.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        content.addView(guides, full(4));
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
