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
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours, shapes and small views in the DevStore design language (see CrossPaths DESIGN.md). */
final class Ui {
    /**
     * A slider moved only by its thumb (3.25, Ali and phase 3 B-13): a press elsewhere on the track does nothing (the
     * stock bar jumped there, so a small touch went from 4° to 16°), and the thumb follows the finger from where it is.
     */
    static android.widget.SeekBar slider(Context c) { return new ThumbSeek(c); }

    static class ThumbSeek extends android.widget.SeekBar {
        private final float reach;
        private float shift;
        private boolean held;

        ThumbSeek(Context c) {
            super(c);
            reach = dp(c, 28);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                int w = getWidth() - getPaddingLeft() - getPaddingRight();
                float cx = getPaddingLeft() + w * (float) getProgress() / Math.max(1, getMax());
                held = Math.abs(e.getX() - cx) <= reach;
                shift = cx - e.getX();
            }
            if (!held) return false;
            e.offsetLocation(shift, 0);
            boolean r = super.onTouchEvent(e);
            e.offsetLocation(-shift, 0);
            return r;
        }
    }

    private Ui() {}

    /** The five accents (CrossPaths): night value, day value, and the ink that goes on top of each. */
    static final String[] ACCENT_NAMES = {"Teal", "Sky blue", "Violet", "Amber", "Rose"};
    private static final int[] ACCENT_NIGHT = {0xFF2DD4BF, 0xFF60A5FA, 0xFFA78BFA, 0xFFFBBF24, 0xFFFB7185};
    private static final int[] ACCENT_DAY = {0xFF0D9488, 0xFF2563EB, 0xFF7C3AED, 0xFFB45309, 0xFFE11D48};
    private static final int[] INK_NIGHT = {0xFF04222A, 0xFF0B1B33, 0xFF1E1147, 0xFF2A1B02, 0xFF3A0A18};

    /** The accent setting that follows the body: amber for the sun, violet for the moon (the default). */
    static final int ACCENT_FOLLOW = -1;
    static final int ACCENT_SUN = 3, ACCENT_MOON = 2;

    /**
     * Settings the user can change: theme 0 = follow the phone, 1 = light, 2 = dark; accent
     * {@link #ACCENT_FOLLOW} or 0..4. 3.1 moved the accent to a new key so everyone starts on Sun and moon.
     */
    static int theme(Context c) { return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("theme", 0); }
    static int accentIndex(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("accent2", ACCENT_FOLLOW);
    }
    static int accentColor(int index, boolean night) { return night ? ACCENT_NIGHT[index] : ACCENT_DAY[index]; }

    /**
     * The design (3.7, owner asked to try both redesigns on the phone): 0 Classic (the 3.6 look), 1 Pocket map (a
     * printed city map: paper, map greys, the line in the body's colour, a map key under the answer), 2 Readout (a
     * handheld navigator: grey case, a graphite readout with large figures, round keys). Pocket map is the default.
     * 3 Appliance (3.19, owner: "a new theme inspired by" a family of Braun-style objects): off-white rounded device
     * faces that stand off the case, an LCD readout (pale by day, black by night), orange round keys, dot grilles.
     * It shares Readout's blocks ({@link Palette#readoutLike()}) and draws them as its own.
     */
    static final String[] DESIGN_NAMES = {"Classic", "Pocket map", "Readout", "Appliance"};
    static final int CLASSIC = 0, POCKET = 1, READOUT = 2, APPLIANCE = 3;
    static int design(Context c) {
        return Math.max(0, Math.min(3, c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("design", POCKET)));
    }
    /**
     * The viewfinder's layout (3.16, owner: "both, in the settings switchable"): 0 Answer band (the moment in a band of
     * the body's colour, the picture under it, a tuning scale for time), 1 Camera (the picture fills the screen, the time
     * over the sky, one orange Lined up key where a shutter would be).
     */
    static final String[] VIEWFINDER_NAMES = {"Answer band", "Camera"};
    static final int VF_BAND = 0, VF_CAMERA = 1;
    static int viewfinder(Context c) {
        return Math.max(0, Math.min(1, c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getInt("viewfinder", VF_BAND)));
    }
    // The body's two colours in the Braun designs: an orange sun and a violet moon, day and night.
    private static final int BRAUN_SUN_DAY = 0xFFD9640A, BRAUN_SUN_NIGHT = 0xFFF5A524;
    private static final int BRAUN_MOON_DAY = 0xFF5747CC, BRAUN_MOON_NIGHT = 0xFFAE9FFF;
    // Appliance's sun: the signal orange of the keys in the owner's picture.
    private static final int APPLIANCE_SUN_DAY = 0xFFEE5A1E, APPLIANCE_SUN_NIGHT = 0xFFFF7A3D;
    /** The accent for this setting, body, theme and design (the Braun designs have their own sun and moon). */
    static int accentColor(int setting, boolean moon, boolean night, int design) {
        if (setting == ACCENT_FOLLOW && design == APPLIANCE && !moon) return night ? APPLIANCE_SUN_NIGHT : APPLIANCE_SUN_DAY;
        if (setting == ACCENT_FOLLOW && design != CLASSIC) {
            return moon ? (night ? BRAUN_MOON_NIGHT : BRAUN_MOON_DAY) : (night ? BRAUN_SUN_NIGHT : BRAUN_SUN_DAY);
        }
        return accentColor(resolveAccent(setting, moon), night);
    }
    /** The accent in use for this setting and body. */
    static int resolveAccent(int setting, boolean moon) {
        return setting == ACCENT_FOLLOW ? (moon ? ACCENT_MOON : ACCENT_SUN) : Math.max(0, Math.min(4, setting));
    }

    // Palette, night and day twins. Always read through Palette so the theme is never frozen. The accent is
    // rewritten in place when the body changes (CrossPaths "Read-The-Theme": one shared object, never captured).
    static final class Palette {
        final boolean night;
        final int design;
        final int bg, panel, raised, line, text, muted, amber, violet, bad;
        /** You on the map (blue) in the Braun designs; the readout panel of Readout and its words. */
        final int you, readout, readoutText, readoutMuted, readoutLine;
        int accent, ink;
        private final int accentSetting;
        private int accentNow;
        Palette(Context c) { this(c, false); }

        /** {@code forceNight}: the night twin whatever the theme (the viewfinder is a dark instrument). */
        Palette(Context c, boolean forceNight) {
            int theme = theme(c);
            boolean system = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            night = forceNight || theme == 2 || (theme == 0 && system);
            accentSetting = accentIndex(c);
            boolean moon = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE).getBoolean("moon", false);
            design = design(c);
            if (design == POCKET) {             // paper by day, the navy night edition by night
                bg = night ? 0xFF121722 : 0xFFECEBE7;
                panel = night ? 0xFF1A2030 : 0xFFF8F7F4;
                raised = night ? 0xFF222A3C : 0xFFE3E1DC;
                line = night ? 0xFF2D3549 : 0xFFD3D0C9;
                text = night ? 0xFFECEAE4 : 0xFF1C1B19;
                muted = night ? 0xFF9AA1AF : 0xFF5E5B55;
            } else if (design == READOUT) {     // grey casework; black by night
                bg = night ? 0xFF0E0E0E : 0xFFE2E1DD;
                panel = night ? 0xFF191918 : 0xFFF7F7F5;
                raised = night ? 0xFF242423 : 0xFFECEBE8;
                line = night ? 0xFF2F2E2C : 0xFFD3D1CC;
                text = night ? 0xFFEDEBE6 : 0xFF161616;
                muted = night ? 0xFF8F8D88 : 0xFF67655F;
            } else if (design == APPLIANCE) {  // off-white casework, faces a shade lighter; the black edition by night
                bg = night ? 0xFF161615 : 0xFFE6E5E1;
                panel = night ? 0xFF222221 : 0xFFF5F4F1;
                raised = night ? 0xFF2B2B29 : 0xFFDEDDD8;
                line = night ? 0xFF363533 : 0xFFCFCDC7;
                text = night ? 0xFFEDEDE8 : 0xFF1A1A19;
                muted = night ? 0xFF94928C : 0xFF64625D;
            } else {
                bg = night ? 0xFF0B1220 : 0xFFF3F5FA;
                panel = night ? 0xFF121A2B : 0xFFFFFFFF;
                raised = night ? 0xFF18233A : 0xFFEEF1F7;
                line = night ? 0xFF25324D : 0xFFDBE1EC;
                text = night ? 0xFFE8EEFC : 0xFF0F172A;
                muted = night ? 0xFF8A97B4 : 0xFF5B6578;
            }
            if (design == APPLIANCE && !night) {   // a pale LCD with dark figures, like the alarm clock's
                readout = 0xFFD3D6CA;
                readoutText = 0xFF1B1E17;
                readoutMuted = 0xFF5A6052;
                readoutLine = 0xFFB4B8A9;
            } else if (design == APPLIANCE) {      // the black screen of the music player
                readout = 0xFF0B0C0B;
                readoutText = 0xFFEDEDE7;
                readoutMuted = 0xFF8E9188;
                readoutLine = 0xFF2C2E2A;
            } else {
                readout = night ? 0xFF222221 : 0xFF1C1C1B;
                readoutText = 0xFFF2F0EA;
                readoutMuted = 0xFFA3A09A;
                readoutLine = 0xFF3A3936;
            }
            you = design == CLASSIC ? ROUTE_BLUE : night ? 0xFF5B95FF : 0xFF1F5FD1;
            setMoon(moon);
            if (design == CLASSIC) {
                amber = night ? 0xFFFBBF24 : 0xFFB45309;     // the sun
                violet = night ? 0xFFA78BFA : 0xFF7C3AED;    // the moon
            } else if (design == APPLIANCE) {
                amber = night ? APPLIANCE_SUN_NIGHT : APPLIANCE_SUN_DAY;
                violet = night ? BRAUN_MOON_NIGHT : BRAUN_MOON_DAY;
            } else {
                amber = night ? BRAUN_SUN_NIGHT : BRAUN_SUN_DAY;
                violet = night ? BRAUN_MOON_NIGHT : BRAUN_MOON_DAY;
            }
            bad = night ? 0xFFF87171 : 0xFFDC2626;
        }

        /** The body chosen. With the accent on Sun and moon, the accent turns amber or violet. */
        void setMoon(boolean moon) {
            this.moonNow = moon;
            accentNow = resolveAccent(accentSetting, moon);
            accent = accentColor(accentSetting, moon, night, design);
            if (design != CLASSIC && accentSetting == ACCENT_FOLLOW) {
                ink = moon && !night ? 0xFFFFFFFF : night && moon ? 0xFF14121F : 0xFF1B1A18;   // dark on the orange sun
            } else {
                ink = night ? INK_NIGHT[accentNow] : 0xFFFFFFFF;
            }
        }
        private boolean moonNow;

        /** Is the moon the body chosen? */
        boolean moon() { return moonNow; }

        /** The accent of the other theme, for the inverted bar that says what happened. */
        int accentOnInverse() { return accentColor(accentSetting, moonNow, !night, design); }

        /** The corner of buttons and fields: round keys in Readout and Appliance. */
        float keyRadius() { return readoutLike() ? 99 : 12; }

        /** Readout and Appliance: the answer on a readout panel, round keys with their names under them. */
        boolean readoutLike() { return design == READOUT || design == APPLIANCE; }
    }

    // ---- type: IBM Plex Sans Arabic, one family, weight carries the hierarchy (400, 500, 600, 700) ----

    private static final Typeface[] FONTS = new Typeface[4];

    static Typeface font(Context c, int weight) {
        int i = weight >= 700 ? 3 : weight >= 600 ? 2 : weight >= 500 ? 1 : 0;
        if (FONTS[i] == null) {
            String[] names = {"Regular", "Medium", "SemiBold", "Bold"};
            try {
                FONTS[i] = Typeface.createFromAsset(c.getAssets(), "fonts/IBMPlexSansArabic-" + names[i] + ".ttf");
            } catch (RuntimeException e) {
                FONTS[i] = Typeface.create("sans-serif", i == 3 ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
        return FONTS[i];
    }

    /** Presses scale a control to 0.97 (CrossPaths); it never blocks the click. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
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
        return text(c, s, sp, bold ? 700 : 400, color);
    }

    static TextView text(Context c, String s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font(c, weight));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    /**
     * One point of the notes under an answer or a setting (3.20, owner: "make these bullet points with icons, app
     * wide"): a small line icon that says what kind of note it is, then the words. Never a paragraph of several ideas:
     * one idea per point. An empty text hides the point.
     */
    static Note note(Context c, Palette p, Icon icon, CharSequence s) {
        return new Note(c, p, icon, s);
    }

    static final class Note extends LinearLayout {
        final TextView text;
        private final android.widget.ImageView glyph;
        private final Context c;
        private Icon icon;

        Note(Context c, Palette p, Icon icon, CharSequence s) {
            super(c);
            this.c = c;
            this.icon = icon;
            setOrientation(HORIZONTAL);
            setGravity(android.view.Gravity.TOP);
            glyph = new android.widget.ImageView(c);
            text = Ui.text(c, "", 12.5f, 400, p.muted);
            addView(glyph);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.setMarginStart(dp(c, 9));
            addView(text, tl);
            size(12.5f, 400);
            color(p.muted);
            setText(s);
        }

        /** The words' size and weight; the icon follows the size, centred on the first line. */
        Note size(float sp, int weight) {
            text.setTextSize(sp);
            text.setTypeface(font(c, weight));
            int side = dp(c, sp + 3.5f);
            float line = sp * 1.25f * c.getResources().getDisplayMetrics().scaledDensity;
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(side, side);
            il.topMargin = Math.max(0, Math.round((line - side) / 2f));
            glyph.setLayoutParams(il);
            return this;
        }

        /** The words' colour, and the icon's with them. */
        Note color(int color) {
            text.setTextColor(color);
            glyph.setImageDrawable(Ui.icon(c, icon, color));
            return this;
        }

        Note icon(Icon i, int color) {
            icon = i;
            glyph.setImageDrawable(Ui.icon(c, i, color));
            return this;
        }

        Note setText(CharSequence s) {
            text.setText(s == null ? "" : s);
            setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
            return this;
        }
    }

    /** A button: primary (accent fill, accent ink) or ghost (raised fill with border). */
    static TextView button(Context c, Palette p, String label, boolean primary) {
        TextView b = text(c, label, 15, 700, primary ? p.ink : p.text);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackground(p.design == APPLIANCE && !primary ? face(c, p, 99, false)
                : shape(c, primary ? p.accent : p.raised, primary ? 0 : p.line, p.keyRadius(), false));
        if (p.design == APPLIANCE && primary) b.setElevation(dp(c, 2));
        if (p.readoutLike()) b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setClickable(true);
        pressable(b);
        return b;
    }

    /** A smaller ghost button for a quick action inside a row. */
    static TextView smallButton(Context c, Palette p, String label) {
        TextView b = text(c, label, 13, 600, p.text);
        b.setGravity(android.view.Gravity.CENTER);
        b.setMinHeight(dp(c, 40));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setBackground(p.design == APPLIANCE ? face(c, p, 99, false)
                : shape(c, p.design == POCKET ? p.panel : p.raised, p.line, p.keyRadius(), false));
        b.setClickable(true);
        pressable(b);
        return b;
    }

    /**
     * An either/or switch: two or three parts in one track, the chosen one raised. For a setting with a few fixed
     * values (which end of the line, which point a tap places). Not a tab and not a filter.
     */
    static android.widget.LinearLayout segmented(Context c, Palette p, String[] labels, int selected,
                                                 java.util.function.IntConsumer pick) {
        return segmented(c, p, labels, null, selected, pick);
    }

    /** The same switch with a line icon before each label (Sun / Moon); the chosen part's icon takes the accent. */
    static android.widget.LinearLayout segmented(Context c, Palette p, String[] labels, Icon[] icons, int selected,
                                                 java.util.function.IntConsumer pick) {
        android.widget.LinearLayout track = new android.widget.LinearLayout(c);
        boolean ro = p.readoutLike(), lcd = p.design == READOUT;    // Appliance: a raised face in a sunk track
        track.setBackground(shape(c, p.raised, ro ? 0 : p.line, ro ? 99 : 12, false));
        int pad = dp(c, 3);
        track.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            int onText = lcd ? p.readoutText : p.text;
            TextView t = text(c, labels[i], 13.5f, on ? 700 : 500, on ? onText : p.muted);
            t.setGravity(android.view.Gravity.CENTER);
            t.setMinHeight(dp(c, 38));
            t.setPadding(dp(c, 12), 0, dp(c, 12), 0);
            if (on) t.setBackground(segOn(c, p));
            View.OnClickListener click = v -> { if (idx != selected) pick.accept(idx); };
            t.setOnClickListener(click);
            t.setSelected(on);
            if (icons == null) {
                track.addView(t, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
                continue;
            }
            // With an icon: icon and word sit together in the middle of their part.
            android.graphics.drawable.Drawable d = icon(c, icons[i], on ? p.accent : p.muted);
            int s = dp(c, 20);
            d.setBounds(0, 0, s, s);
            t.setCompoundDrawables(d, null, null, null);
            t.setCompoundDrawablePadding(dp(c, 8));
            t.setBackground(null);
            t.setPadding(0, 0, 0, 0);
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            android.widget.FrameLayout part = new android.widget.FrameLayout(c);
            part.setMinimumHeight(dp(c, 42));
            if (on) part.setBackground(segOn(c, p));
            part.setClickable(true);
            part.setSelected(on);
            part.setContentDescription(labels[i]);
            part.setOnClickListener(click);
            part.addView(t, new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
            track.addView(part, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        }
        return track;
    }

    /** The raised part of a segmented switch: paper, or the graphite of the readout in Readout. */
    private static android.graphics.drawable.Drawable segOn(Context c, Palette p) {
        if (p.design == READOUT) return shape(c, p.night ? p.readoutLine : p.readout, 0, 99, false);
        if (p.design == APPLIANCE) return face(c, p, 99, false);
        return shape(c, p.panel, p.line, 9, false);
    }

    /** Tabs: plain words with a line under the one that is open. They switch what is shown below, nothing else. */
    static android.widget.LinearLayout tabs(Context c, Palette p, String[] labels, int selected,
                                            java.util.function.IntConsumer pick) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView tab = text(c, labels[i], 15, on ? 700 : 500, on ? p.text : p.muted);
            tab.setGravity(android.view.Gravity.CENTER);
            tab.setMinHeight(dp(c, 46));
            tab.setPadding(dp(c, 2), 0, dp(c, 2), 0);
            if (on) {   // the line under the open tab, as wide as its word
                android.graphics.drawable.LayerDrawable under = new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{shape(c, p.accent, 0, 2, false)});
                under.setLayerGravity(0, android.view.Gravity.BOTTOM | android.view.Gravity.FILL_HORIZONTAL);
                under.setLayerHeight(0, dp(c, 3));
                tab.setBackground(under);
            }
            tab.setClickable(true);
            tab.setContentDescription(labels[i] + (on ? ", open" : ""));
            tab.setOnClickListener(v -> { if (idx != selected) pick.accept(idx); });
            android.widget.LinearLayout.LayoutParams l = new android.widget.LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.setMarginStart(dp(c, 20));
            row.addView(tab, l);
        }
        return row;
    }

    /**
     * Big tabs (3.8, owner: "Times, Day are extremely not clear; they need to be separate tabs"): equal parts across
     * the width, an icon over each name, the open one in the accent with a bar under it and a hairline under all.
     */
    static android.widget.LinearLayout bigTabs(Context c, Palette p, String[] labels, Icon[] icons, int selected,
                                               java.util.function.IntConsumer pick) {
        android.widget.LinearLayout wrap = new android.widget.LinearLayout(c);
        wrap.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            android.widget.LinearLayout tab = new android.widget.LinearLayout(c);
            tab.setOrientation(android.widget.LinearLayout.VERTICAL);
            tab.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tab.setPadding(0, dp(c, 8), 0, 0);
            android.widget.ImageView ic = new android.widget.ImageView(c);
            ic.setImageDrawable(icon(c, icons[i], on ? p.accent : p.muted));
            tab.addView(ic, new android.widget.LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
            TextView t = text(c, labels[i], 14.5f, on ? 700 : 500, on ? p.text : p.muted);
            android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = dp(c, 4);
            tab.addView(t, tl);
            View bar = new View(c);
            bar.setBackground(on ? shape(c, p.accent, 0, 2, false) : null);
            android.widget.LinearLayout.LayoutParams bl = new android.widget.LinearLayout.LayoutParams(-1, dp(c, 3));
            bl.topMargin = dp(c, 8);
            tab.addView(bar, bl);
            tab.setClickable(true);
            tab.setMinimumHeight(dp(c, 48));
            tab.setContentDescription(labels[i] + (on ? ", open" : ""));
            tab.setOnClickListener(v -> { if (idx != selected) pick.accept(idx); });
            row.addView(tab, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        }
        wrap.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));
        View hair = new View(c);
        hair.setBackgroundColor(p.line);
        wrap.addView(hair, new android.widget.LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1))));
        return wrap;
    }

    /**
     * Where the sun sits, as pictures (3.12.2, owner: "this is not clear"): a tile per choice with the subject, the
     * horizon and the body drawn where the choice puts it, its name under it; the chosen tile outlined in the accent.
     * {@code which[i]}: 0 any (the body faint at all three), 1 horizon, 2 middle, 3 on top.
     */
    static android.widget.LinearLayout sitsChoice(Context c, Palette p, String[] labels, int[] which, int selected,
                                                  java.util.function.IntConsumer pick) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            android.widget.LinearLayout tile = new android.widget.LinearLayout(c);
            tile.setOrientation(android.widget.LinearLayout.VERTICAL);
            tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 8));
            GradientDrawable bg = shape(c, on ? withAlpha(p.accent, 24) : p.raised, on ? p.accent : p.line, 14, false);
            if (on) bg.setStroke(Math.max(1, dp(c, 2)), p.accent);
            tile.setBackground(bg);
            tile.addView(new SitsPicture(c, p, which[i], on), new android.widget.LinearLayout.LayoutParams(dp(c, 64), dp(c, 48)));
            TextView t = text(c, labels[i], 13, on ? 700 : 500, on ? p.text : p.muted);
            android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = dp(c, 6);
            tile.addView(t, tl);
            tile.setClickable(true);
            tile.setContentDescription(labels[i] + (on ? ", chosen" : ""));
            View.OnClickListener go = v -> { if (idx != selected) pick.accept(idx); };
            tile.setOnClickListener(go);
            t.setOnClickListener(go);
            android.widget.LinearLayout.LayoutParams l = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) l.setMarginStart(dp(c, 6));
            row.addView(tile, l);
        }
        return row;
    }

    /** The little picture of a sits tile: the horizon, the subject as a block, the body where the choice puts it. */
    static final class SitsPicture extends View {
        private final Palette p;
        private final int which;
        private final boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SitsPicture(Context c, Palette p, int which, boolean on) {
            super(c);
            this.p = p;
            this.which = which;
            this.on = on;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), k = dp(getContext(), 1);
            float horizon = h * 0.8f, bw = w * 0.12f, bh = h * 0.46f, bx = w / 2 - bw / 2, top = horizon - bh;
            float r = w * 0.17f;                                  // wider than the subject, so it shows round it
            int sun = on ? p.accent : withAlpha(p.accent, 170);
            float[] ys = {horizon, top + bh / 2, top - r};      // horizon, middle, on top
            paint.setStyle(Paint.Style.FILL);
            if (which == 0) {
                paint.setColor(withAlpha(sun, 90));
                for (float y : ys) cv.drawCircle(w / 2, y, r * 0.8f, paint);
            } else {
                paint.setColor(sun);
                cv.drawCircle(w / 2, ys[which - 1], r, paint);
            }
            paint.setColor(p.text);                              // the subject, in front of the body
            cv.drawRect(bx, top, bx + bw, horizon, paint);
            paint.setColor(p.muted);
            paint.setStrokeWidth(Math.max(1, 1.5f * k));
            cv.drawLine(0, horizon, w, horizon, paint);
            paint.setColor(p.night ? 0x33000000 : 0x14000000);
            cv.drawRect(0, horizon, w, h, paint);
        }
    }

    /** A small label that names a quality (Low, Golden hour): tinted text on a faint fill of the same colour. */
    static TextView tag(Context c, String label, int color) {
        TextView t = text(c, label, 11.5f, 700, color);
        t.setPadding(dp(c, 7), dp(c, 3), dp(c, 7), dp(c, 3));
        t.setBackground(shape(c, withAlpha(color, 34), 0, 6, false));
        return t;
    }

    /**
     * A dropdown: a bordered field with its label small above, the value, and a chevron. A tap opens the list
     * from the bottom ({@link PickSheet}). For picking one of a few named things; never a pill.
     */
    static android.widget.LinearLayout dropdown(Context c, Palette p, String label, String[] options, int selected,
                                                java.util.function.IntConsumer pick) {
        return dropdown(c, p, label, options, null, selected, pick);
    }

    /** A dropdown whose rows show a colour (the accent). */
    static android.widget.LinearLayout dropdown(Context c, Palette p, String label, String[] options, int[] swatches,
                                                int selected, java.util.function.IntConsumer pick) {
        android.widget.LinearLayout field = new android.widget.LinearLayout(c);
        field.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        field.setGravity(android.view.Gravity.CENTER_VERTICAL);
        field.setBackground(p.design == APPLIANCE ? face(c, p, 18, false)
                : shape(c, p.design == POCKET ? p.panel : p.raised, p.line, p.design == READOUT ? 18 : 12, false));
        field.setPadding(dp(c, p.readoutLike() ? 18 : 14), dp(c, 8), dp(c, 12), dp(c, 8));
        field.setMinimumHeight(dp(c, 52));
        android.widget.LinearLayout col = new android.widget.LinearLayout(c);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.addView(text(c, label, 12, 500, p.muted));
        int sel = Math.max(0, Math.min(selected, options.length - 1));
        android.widget.LinearLayout valueRow = new android.widget.LinearLayout(c);
        valueRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (swatches != null) {
            View dot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(swatches[sel]);
            dot.setBackground(g);
            android.widget.LinearLayout.LayoutParams dl = new android.widget.LinearLayout.LayoutParams(dp(c, 14), dp(c, 14));
            dl.setMarginEnd(dp(c, 8));
            valueRow.addView(dot, dl);
        }
        valueRow.addView(text(c, options[sel], 15, 600, p.text));
        col.addView(valueRow);
        field.addView(col, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView chev = new android.widget.ImageView(c);
        chev.setImageDrawable(icon(c, Icon.CHEV_DOWN, p.muted));
        field.addView(chev, new android.widget.LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
        field.setContentDescription(label + ": " + options[sel]);
        field.setClickable(true);
        pressable(field);
        field.setOnClickListener(v -> {
            android.app.Activity a = activity(c);
            if (a != null) PickSheet.open(a, p, label, options, swatches, sel, pick);
        });
        return field;
    }

    /**
     * A dropdown whose value always stays on one line: the words get smaller (to 11.5 sp), and only then end in an
     * ellipsis. "Today to / Tomorrow" on two lines read as two choices (roadmap, 3.6).
     */
    static android.widget.LinearLayout oneLine(android.widget.LinearLayout dropdown) {
        android.widget.LinearLayout col = (android.widget.LinearLayout) dropdown.getChildAt(0);
        android.widget.LinearLayout valueRow = (android.widget.LinearLayout) col.getChildAt(1);
        TextView t = (TextView) valueRow.getChildAt(valueRow.getChildCount() - 1);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final float min = 11.5f * t.getResources().getDisplayMetrics().scaledDensity;
        t.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
            android.text.Layout lay = t.getLayout();
            if (lay != null && lay.getLineCount() > 0 && lay.getEllipsisCount(0) > 0 && t.getTextSize() > min) {
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Math.max(min, t.getTextSize() - dp(t.getContext(), 1)));
            }
        });
        return dropdown;
    }

    static android.app.Activity activity(Context c) {
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) return (android.app.Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    enum Icon {
        LOCATE(R.drawable.ic_locate), HELP(R.drawable.ic_help), CLEAR(R.drawable.ic_close), LAYERS(R.drawable.ic_map),
        CHEV_LEFT(R.drawable.ic_prev), CHEV_RIGHT(R.drawable.ic_next), SETTINGS(R.drawable.ic_settings),
        CLOSE(R.drawable.ic_close), TRIP(R.drawable.ic_trip), BACK(R.drawable.ic_back), BELL(R.drawable.ic_bell),
        BELL_ON(R.drawable.ic_bell_on), SEARCH(R.drawable.ic_search), SAVED(R.drawable.ic_saved),
        SHARE(R.drawable.ic_share), MORE(R.drawable.ic_more), ROTATE(R.drawable.ic_rotate), HEIGHT(R.drawable.ic_height), PLUS(R.drawable.ic_plus), MINUS(R.drawable.ic_minus), LIVE(R.drawable.ic_live), STACK(R.drawable.ic_layers),
        TRASH(R.drawable.ic_trash), CHEV_DOWN(R.drawable.ic_chevron_down), MOVE(R.drawable.ic_move),
        SUN(R.drawable.ic_sun), MOON(R.drawable.ic_moon), TOOL_LINE(R.drawable.ic_tool_line),
        TOOL_BUILD(R.drawable.ic_tool_build), TOOL_FIND(R.drawable.ic_tool_find), NOW(R.drawable.ic_now), DIRECTIONS(R.drawable.ic_directions), VIEWFINDER(R.drawable.ic_viewfinder),
        LIST(R.drawable.ic_list), CHART(R.drawable.ic_chart), CUBE(R.drawable.ic_cube),
        WARNING(R.drawable.ic_warning), INFO(R.drawable.ic_info), CLOCK(R.drawable.ic_clock), HILL(R.drawable.ic_hill), CHECK(R.drawable.ic_check), NORTH(R.drawable.ic_north), PEOPLE(R.drawable.ic_people), HEART(R.drawable.ic_heart);
        final int res;
        Icon(int res) { this.res = res; }
    }

    /** A line icon from the CrossPaths set (24 unit grid, 1.8 stroke, round caps), tinted to the theme. */
    static android.graphics.drawable.Drawable icon(Context c, Icon icon, int color) {
        android.graphics.drawable.Drawable d = c.getDrawable(icon.res).mutate();
        d.setTint(color);
        return d;
    }

    /** Round button with a CrossPaths line icon. */
    static final class IconButton extends View {
        private Icon icon;
        private int sizeDp = 48;
        private final Palette p;
        private android.graphics.drawable.Drawable drawable;

        IconButton(Context c, Palette p, Icon icon, String description) {
            super(c);
            this.p = p;
            this.icon = icon;
            this.drawable = tinted();
            setContentDescription(description);
            setClickable(true);
            setBackground(p.design == APPLIANCE ? face(c, p, 99, false)
                    : shape(c, p.panel, p.line, p.design == POCKET ? 14 : 24, false));
            pressable(this);
        }

        /** A smaller, borderless button for rows in a list (the icon stays 24dp). */
        IconButton small() {
            sizeDp = 40;
            setBackground(null);
            return this;
        }

        private boolean lit;
        private int tint;

        /** Over the viewfinder: a dark glass disc with a white icon. */
        IconButton onScene() {
            tint = 0xFFFFFFFF;
            if (p.readoutLike()) {               // round keys: a ring, no glass
                GradientDrawable g = shape(getContext(), 0x40000000, 0x66FFFFFF, 99, false);
                g.setStroke(Math.max(1, dp(getContext(), 1.5f)), 0x66FFFFFF);
                setBackground(g);
            } else if (p.design == POCKET) {
                setBackground(shape(getContext(), 0x99121722, 0x33FFFFFF, 12, false));
            } else {
                setBackground(shape(getContext(), 0xA60B1220, 0x33FFFFFF, 24, false));
            }
            drawable = tinted();
            return this;
        }

        /** A different size of disc (the icon stays 24dp). */
        IconButton size(int dp) {
            sizeDp = dp;
            return this;
        }

        /** The icon in this colour (a key on the cover's disc). */
        IconButton tint(int color) {
            tint = color;
            drawable = tinted();
            invalidate();
            return this;
        }

        /** No disc, for a key that sits inside a bar. */
        IconButton bare() {
            setBackground(null);
            return this;
        }

        /** A key that is switched on takes the accent. */
        void setLit(boolean on) {
            lit = on;
            drawable = tinted();
            invalidate();
        }

        private android.graphics.drawable.Drawable tinted() {
            // The lit bell, and any key that is on, take the accent.
            return Ui.icon(getContext(), icon, icon == Icon.BELL_ON || lit ? p.accent : tint != 0 ? tint : p.text);
        }

        void setIcon(Icon i) {
            icon = i;
            drawable = tinted();
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            int s = dp(getContext(), sizeDp);
            setMeasuredDimension(s, s);
        }

        private float turn;

        /** The icon turned (degrees clockwise), the disc not: the North key points to north (3.20). */
        void setIconRotation(float degrees) {
            if (Math.abs(degrees - turn) < 0.5f) return;
            turn = degrees;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            int half = dp(getContext(), 12);
            drawable.setBounds(getWidth() / 2 - half, getHeight() / 2 - half, getWidth() / 2 + half, getHeight() / 2 + half);
            drawable.setAlpha(isEnabled() ? 255 : 128);   // disabled is 50%
            int saved = cv.save();
            cv.rotate(turn, getWidth() / 2f, getHeight() / 2f);
            drawable.draw(cv);
            cv.restoreToCount(saved);
        }
    }


    // ---- Pocket map (3.7): the map key, a ruled block naming each mark on the map with its swatch ----

    enum Swatch { YOU, SUBJECT, LINE, BODY }

    /** The map key: a ruled block (a firm rule on top, like a printed map's legend). Rows come from {@link #keyRow}. */
    static android.widget.LinearLayout mapKey(Context c, Palette p) {
        android.widget.LinearLayout key = new android.widget.LinearLayout(c);
        key.setOrientation(android.widget.LinearLayout.VERTICAL);
        View rule = new View(c);
        rule.setBackgroundColor(p.text);
        key.addView(rule, new android.widget.LinearLayout.LayoutParams(-1, Math.max(2, dp(c, 1.5f))));
        return key;
    }

    /** A row of the map key: the swatch, the name, and its value on the right. */
    static void keyRow(Context c, Palette p, android.widget.LinearLayout key, Swatch swatch, String name, String value) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 9), 0, 0);
        row.addView(new SwatchView(c, p, swatch), new android.widget.LinearLayout.LayoutParams(dp(c, 24), dp(c, 16)));
        TextView n = text(c, name, 14, 600, p.text);
        android.widget.LinearLayout.LayoutParams nl = new android.widget.LinearLayout.LayoutParams(-2, -2);
        nl.setMarginStart(dp(c, 10));
        nl.setMarginEnd(dp(c, 12));
        row.addView(n, nl);
        TextView v = text(c, value, 13.5f, 400, p.muted);
        v.setGravity(android.view.Gravity.END);
        row.addView(v, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        row.setContentDescription(name + ": " + value);
        key.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));
    }

    /** The marks of the map, small: You (a blue dot), the subject (an ink pin), the line, the sun or moon. */
    static final class SwatchView extends View {
        private final Palette p;
        private final Swatch kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SwatchView(Context c, Palette p, Swatch kind) {
            super(c);
            this.p = p;
            this.kind = kind;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas cv) {
            float k = dp(getContext(), 1), cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            switch (kind) {
                case YOU:
                    paint.setColor(p.you);
                    cv.drawCircle(cx, cy, 7 * k, paint);
                    paint.setColor(p.panel);
                    cv.drawCircle(cx, cy, 5 * k, paint);
                    paint.setColor(p.you);
                    cv.drawCircle(cx, cy, 3.6f * k, paint);
                    break;
                case SUBJECT:
                    paint.setColor(p.text);
                    Path pin = new Path();
                    pin.moveTo(cx, cy + 7 * k);
                    pin.cubicTo(cx - 2 * k, cy + 3 * k, cx - 5.5f * k, cy + 0.5f * k, cx - 5.5f * k, cy - 2.5f * k);
                    pin.arcTo(new RectF(cx - 5.5f * k, cy - 8 * k, cx + 5.5f * k, cy + 3 * k), 180, 180, false);
                    pin.cubicTo(cx + 5.5f * k, cy + 0.5f * k, cx + 2 * k, cy + 3 * k, cx, cy + 7 * k);
                    cv.drawPath(pin, paint);
                    paint.setColor(p.panel);
                    cv.drawCircle(cx, cy - 2.5f * k, 2 * k, paint);
                    break;
                case LINE:
                    paint.setColor(p.accent);
                    cv.drawRoundRect(new RectF(0, cy - 1.75f * k, getWidth(), cy + 1.75f * k), 2 * k, 2 * k, paint);
                    break;
                case BODY:
                    paint.setColor(p.accent);
                    cv.drawCircle(cx, cy, 6.5f * k, paint);
                    break;
            }
        }
    }

    // ---- Readout (3.7): a graphite panel, the answer in large figures, three numbers under it ----

    /** The readout panel; it stays graphite by day and by night so the figures read the same. */
    static android.widget.LinearLayout readout(Context c, Palette p) {
        android.widget.LinearLayout r = new android.widget.LinearLayout(c);
        r.setOrientation(android.widget.LinearLayout.VERTICAL);
        if (p.design == APPLIANCE) {
            r.setBackground(new LcdDrawable(c, p));
            r.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15));
            return r;
        }
        r.setBackground(shape(c, p.readout, p.night ? p.readoutLine : 0, 22, false));
        r.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return r;
    }

    /** The small line on top of a readout, with a dot in the body's colour. */
    static android.widget.LinearLayout readoutLabel(Context c, Palette p, String words) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(shape(c, p.accent, 0, 99, false));
        row.addView(dot, new android.widget.LinearLayout.LayoutParams(dp(c, 7), dp(c, 7)));
        TextView t = text(c, words, 12.5f, 500, p.readoutMuted);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(dp(c, 7));
        row.addView(t, tl);
        return row;
    }

    /** Three numbers in a strip, split by hairlines: {value, what it is} each. */
    static android.widget.LinearLayout cells(Context c, Palette p, String[][] cells) {
        android.widget.LinearLayout strip = new android.widget.LinearLayout(c);
        strip.setOrientation(android.widget.LinearLayout.VERTICAL);
        View rule = new View(c);
        rule.setBackgroundColor(p.readoutLine);
        strip.addView(rule, new android.widget.LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1))));
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setPadding(0, dp(c, 10), 0, 0);
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                View hair = new View(c);
                hair.setBackgroundColor(p.readoutLine);
                android.widget.LinearLayout.LayoutParams hl = new android.widget.LinearLayout.LayoutParams(Math.max(1, dp(c, 1)), -1);
                hl.setMarginStart(dp(c, 10));
                hl.setMarginEnd(dp(c, 10));
                row.addView(hair, hl);
            }
            android.widget.LinearLayout col = new android.widget.LinearLayout(c);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            TextView v = text(c, cells[i][0], 16, 600, p.readoutText);
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setFontFeatureSettings("tnum");
            col.addView(v);
            TextView n = text(c, cells[i][1], 11.5f, 400, p.readoutMuted);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.LinearLayout.LayoutParams nl = new android.widget.LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = dp(c, 2);
            col.addView(n, nl);
            col.setContentDescription(cells[i][0] + " " + cells[i][1]);
            row.addView(col, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        }
        strip.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));
        return strip;
    }

    /** The large figures of a readout ("4:54 PM"); the part after a space is set smaller. */
    static TextView readoutBig(Context c, Palette p, String figures, float sp) {
        TextView t = text(c, figures, sp, 600, p.readoutText);
        t.setLetterSpacing(-0.02f);
        t.setFontFeatureSettings("tnum");
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    /** A round key in the body's colour with an icon: the one thing to do next. */
    static android.widget.FrameLayout roundKey(Context c, Palette p, Icon icon, String description, int sizeDp) {
        android.widget.FrameLayout k = new android.widget.FrameLayout(c);
        k.setBackground(shape(c, p.accent, 0, 99, false));
        k.setElevation(dp(c, 3));
        android.widget.ImageView i = new android.widget.ImageView(c);
        i.setImageDrawable(icon(c, icon, p.ink));
        k.addView(i, new android.widget.FrameLayout.LayoutParams(dp(c, 24), dp(c, 24), android.view.Gravity.CENTER));
        k.setContentDescription(description);
        k.setClickable(true);
        pressable(k);
        k.setMinimumWidth(dp(c, sizeDp));
        k.setMinimumHeight(dp(c, sizeDp));
        return k;
    }

    /** A round outlined key with its name under it (Save, Remind me, Directions in Readout). */
    static android.widget.LinearLayout namedKey(Context c, Palette p, Icon icon, String name, boolean lit) {
        android.widget.LinearLayout col = new android.widget.LinearLayout(c);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        android.widget.FrameLayout disc = new android.widget.FrameLayout(c);
        if (p.design == APPLIANCE) {          // a raised round key; lit, it is the orange one
            disc.setBackground(lit ? shape(c, p.accent, 0, 99, false) : face(c, p, 99, false));
            if (lit) disc.setElevation(dp(c, 2));
        } else {
            GradientDrawable g = shape(c, p.panel, lit ? p.accent : p.line, 99, false);
            g.setStroke(Math.max(1, dp(c, 1.5f)), lit ? p.accent : p.line);
            disc.setBackground(g);
        }
        android.widget.ImageView i = new android.widget.ImageView(c);
        i.setImageDrawable(icon(c, icon, lit ? (p.design == APPLIANCE ? p.ink : p.accent) : p.text));
        disc.addView(i, new android.widget.FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), android.view.Gravity.CENTER));
        col.addView(disc, new android.widget.LinearLayout.LayoutParams(dp(c, 50), dp(c, 50)));
        TextView t = text(c, name, 12, 500, p.muted);
        t.setSingleLine(true);
        android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(c, 6);
        col.addView(t, tl);
        col.setContentDescription(name);
        col.setClickable(true);
        pressable(col);
        col.setMinimumHeight(dp(c, 48));
        return col;
    }

    // ---- Appliance (3.19): device faces, the LCD and the dot grille, after the owner's picture of small appliances ----

    /** A device face: lighter than the case, standing off it on a soft shadow; {@code sideKey} adds the orange side key. */
    static android.graphics.drawable.Drawable face(Context c, Palette p, float radiusDp, boolean sideKey) {
        return new FaceDrawable(c, p, radiusDp, sideKey);
    }

    static final class FaceDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette p;
        private final float r, k;
        private final boolean sideKey;
        FaceDrawable(Context c, Palette p, float radiusDp, boolean sideKey) {
            this.p = p;
            this.k = c.getResources().getDisplayMetrics().density;
            this.r = radiusDp * k;
            this.sideKey = sideKey;
        }
        @Override public void draw(Canvas cv) {
            android.graphics.Rect b = getBounds();
            float side = sideKey ? 3 * k : 0;
            RectF f = new RectF(b.left + side, b.top, b.right, b.bottom - 2 * k);
            float rr = Math.min(r, Math.min(f.width(), f.height()) / 2);
            if (sideKey) {                    // the small orange key on the left edge, as on the radios
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(p.accent);
                float h = Math.min(22 * k, f.height() * 0.3f), top = f.top + Math.max(rr, f.height() * 0.3f);
                cv.drawRoundRect(new RectF(b.left, top, b.left + 8 * k, top + h), 2 * k, 2 * k, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            int shade = p.night ? 0x66000000 : 0x1F3A3026;      // a soft shadow under the face, two steps
            paint.setColor(shade);
            cv.drawRoundRect(new RectF(f.left + k, f.top + 2 * k, f.right - k, f.bottom + 2 * k), rr, rr, paint);
            paint.setColor(withAlpha(shade, Color.alpha(shade) / 2));
            cv.drawRoundRect(new RectF(f.left, f.top + 1 * k, f.right, f.bottom + 1 * k), rr, rr, paint);
            paint.setColor(p.panel);
            cv.drawRoundRect(f, rr, rr, paint);
            paint.setStyle(Paint.Style.STROKE);   // a hairline, lighter on top, where the light falls
            paint.setStrokeWidth(Math.max(1, k));
            paint.setColor(p.night ? 0x14FFFFFF : 0x99FFFFFF);
            RectF in = new RectF(f.left + k / 2, f.top + k / 2, f.right - k / 2, f.bottom - k / 2);
            cv.drawRoundRect(in, rr, rr, paint);
            paint.setColor(withAlpha(p.line, 150));
            cv.drawRoundRect(in, rr, rr, paint);
        }
        @Override public boolean getPadding(android.graphics.Rect padding) {
            padding.set(sideKey ? Math.round(3 * k) : 0, 0, 0, Math.round(2 * k));
            return true;
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** The LCD of a readout in Appliance: a screen sunk into the face, its glass a shade darker at the top. */
    static final class LcdDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette p;
        private final float k, radius;
        LcdDrawable(Context c, Palette p) { this(c, p, 14); }
        LcdDrawable(Context c, Palette p, float radiusDp) {
            this.p = p;
            this.k = c.getResources().getDisplayMetrics().density;
            this.radius = radiusDp * k;
        }
        @Override public void draw(Canvas cv) {
            android.graphics.Rect b = getBounds();
            RectF f = new RectF(b);
            float r = radius;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.night ? 0xFF000000 : 0xFFBFC2B6);     // the bezel
            cv.drawRoundRect(f, r, r, paint);
            RectF glass = new RectF(f.left + 2 * k, f.top + 2 * k, f.right - 2 * k, f.bottom - 2 * k);
            paint.setShader(new android.graphics.LinearGradient(0, glass.top, 0, glass.bottom,
                    mixShade(p.readout, p.night ? 0.06f : -0.05f), p.readout, android.graphics.Shader.TileMode.CLAMP));
            cv.drawRoundRect(glass, r - 2 * k, r - 2 * k, paint);
            paint.setShader(null);
        }
        private static int mixShade(int c, float by) {   // lighter (by > 0) or darker (by < 0)
            int t = by > 0 ? 255 : 0;
            float a = Math.abs(by);
            return Color.rgb(Math.round(Color.red(c) + (t - Color.red(c)) * a),
                    Math.round(Color.green(c) + (t - Color.green(c)) * a), Math.round(Color.blue(c) + (t - Color.blue(c)) * a));
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A speaker grille: a field of small round holes, drawn only for its look (Appliance). */
    static final class Grille extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette p;
        Grille(Context c, Palette p) {
            super(c);
            this.p = p;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas cv) {
            float k = dp(getContext(), 1), step = 5.5f * k, rad = 1.25f * k;
            int cols = (int) ((getWidth() - 2 * rad) / step) + 1, rows = (int) ((getHeight() - 2 * rad) / step) + 1;
            float x0 = (getWidth() - (cols - 1) * step) / 2f, y0 = (getHeight() - (rows - 1) * step) / 2f;
            paint.setColor(p.night ? 0xFF0C0C0B : withAlpha(p.muted, 120));
            for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) cv.drawCircle(x0 + j * step, y0 + i * step, rad, paint);
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
