package dev.qawalah.sunlighn;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Home: what the app opens on. The sun or the moon (each with its icon; the accent follows it), then the three
 * tools, simplest first, each a card with its icon in the middle and its words beneath, and the one the user left
 * unfinished. Each tool is a short series of steps; Back from its first step comes back here.
 */
final class HomeScreen extends FrameLayout {
    interface Host {
        void startTool(int tool);
        void continueTool();
        void setMoon(boolean moon);
        void showSaved();
        void showSettings();
        void showHelp();
    }

    private final Ui.Palette p;
    private final Host host;
    private final LinearLayout content;
    private final CoverDisc disc;
    private int insetTop, insetBottom;
    private boolean moon;
    private String continueText;

    HomeScreen(Context c, Ui.Palette p, Host host) {
        super(c);
        this.p = p;
        this.host = host;
        setBackgroundColor(p.bg);
        setClickable(true);
        // Pocket map (3.7): Home is a map booklet's cover, the sun as a quarter disc in its corner (the moon a crescent).
        disc = new CoverDisc(c);
        if (p.design == Ui.POCKET) addView(disc, new LayoutParams(Ui.dp(c, 150), Ui.dp(c, 150), Gravity.TOP | Gravity.END));
        ScrollView scroll = new ScrollView(c);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        addView(scroll, new LayoutParams(-1, -1));
    }

    void setInsets(int top, int bottom) {
        if (top == insetTop && bottom == insetBottom && content.getChildCount() > 0) return;
        insetTop = top;
        insetBottom = bottom;
        build();
    }

    /** The body chosen, and the words of the Continue card (null: nothing to continue). */
    void refresh(boolean moon, String continueText) {
        this.moon = moon;
        this.continueText = continueText;
        build();
    }

    private void build() {
        Context c = getContext();
        content.removeAllViews();
        int pad = Ui.dp(c, p.design == Ui.POCKET ? 20 : 16);
        content.setPadding(pad, insetTop + Ui.dp(c, 12), pad, insetBottom + Ui.dp(c, 24));
        disc.invalidate();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        boolean pocket = p.design == Ui.POCKET;
        head.addView(Ui.text(c, "SunWhere", pocket ? 20 : 26, 700, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        // On the cover the keys have their own paper backing: over the disc or the crescent's bite alike they read (3.10)
        Ui.IconButton saved = new Ui.IconButton(c, p, Ui.Icon.SAVED, "Saved");
        if (!pocket) saved.bare();
        saved.setOnClickListener(v -> host.showSaved());
        head.addView(saved);
        Ui.IconButton gear = new Ui.IconButton(c, p, Ui.Icon.SETTINGS, "Settings");
        if (!pocket) gear.bare();
        else {
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.setMarginStart(Ui.dp(c, 8));
            gear.setLayoutParams(gl);
        }
        gear.setOnClickListener(v -> host.showSettings());
        head.addView(gear);
        content.addView(head, new LinearLayout.LayoutParams(-1, -2));

        String tagline = "Get the " + body() + " exactly where you want it in your photo.";
        if (pocket) {
            // 3.13.2 (owner): "sun" (or "moon") and "where" bold and in colour, the rest smaller, on two lines only
            TextView cover = Ui.text(c, "", 19, 500, p.text);
            cover.setText(coverWords(c));
            cover.setLetterSpacing(-0.02f);
            cover.setLineSpacing(0, 1.05f);
            cover.setMaxLines(2);
            content.addView(cover, top(76));                // clear of the corner disc
        } else {
            content.addView(Ui.text(c, tagline, 15, 400, p.muted), top(4));
        }
        if (p.design == Ui.CLASSIC) {
            content.addView(Ui.segmented(c, p, new String[]{"Sun", "Moon"}, new Ui.Icon[]{Ui.Icon.SUN, Ui.Icon.MOON},
                    moon ? 1 : 0, i -> host.setMoon(i == 1)), top(16));
        } else {
            content.addView(bodyPicker(), top(pocket ? 16 : 12));
        }

        if (continueText != null && p.design == Ui.READOUT) {
            content.addView(continueReadout(), top(16));
        } else if (continueText != null && pocket) {
            LinearLayout cont = card(null, "Continue", continueText);
            cont.setContentDescription("Continue: " + continueText);
            View dot = new View(c);
            dot.setBackground(Ui.shape(c, p.accent, 0, 99, false));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
            dl.setMarginEnd(Ui.dp(c, 12));
            cont.addView(dot, 0, dl);
            cont.setOnClickListener(v -> host.continueTool());
            content.addView(cont, top(16));
        } else if (continueText != null) {
            LinearLayout cont = card(null, "Continue", continueText);
            cont.setContentDescription("Continue: " + continueText);
            cont.setBackground(Ui.shape(c, Ui.withAlpha(p.accent, 30), p.accent, 16, false));
            cont.setOnClickListener(v -> host.continueTool());
            content.addView(cont, top(16));
        }

        String b = body();
        addTool(Flow.LINE, Ui.Icon.TOOL_LINE, "You and your subject: when does the " + b + " sit exactly on that line?",
                continueText == null ? 16 : 20);
        // Two tools since 3.6: Where to stand is part of Build the shot (owner, 2026-11-16)
        addTool(Flow.BUILD, Ui.Icon.TOOL_BUILD, "Your subject: place the " + b + " in the picture, or say where it sits. "
                + "Get where to stand and when.", 12);

        TextView help = Ui.text(c, "How it works", 15, 600, p.accent);
        help.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        help.setClickable(true);
        help.setOnClickListener(v -> host.showHelp());
        content.addView(help, top(12));
    }

    private String body() { return moon ? "moon" : "sun"; }

    /** "Get the sun exactly where / you want it in your photo.", the two words that matter large, bold, coloured. */
    private CharSequence coverWords(Context c) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Get the ");
        key(b, body(), c);
        b.append(" exactly ");
        key(b, "where", c);
        b.append("\nyou want it in your photo.");
        return b;
    }

    private void key(android.text.SpannableStringBuilder b, String word, Context c) {
        int at = b.length();
        b.append(word);
        int end = b.length();
        b.setSpan(new android.text.style.AbsoluteSizeSpan(28, true), at, end, 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(moon ? p.violet : p.amber), at, end, 0);
        b.setSpan(new FontSpan(Ui.font(c, 700)), at, end, 0);
    }

    /** A typeface for part of the words (TypefaceSpan(Typeface) needs Android 9; this works from 7). */
    private static final class FontSpan extends android.text.style.MetricAffectingSpan {
        private final android.graphics.Typeface tf;
        FontSpan(android.graphics.Typeface tf) { this.tf = tf; }
        @Override public void updateDrawState(android.text.TextPaint tp) { tp.setTypeface(tf); }
        @Override public void updateMeasureState(android.text.TextPaint tp) { tp.setTypeface(tf); }
    }

    // ---- the change between sun and moon (3.13.2, owner: "a cool transition between the two, with intermediate
    // transition in between"): the chosen body goes out (the sun eclipsed, the moon dimming), the other comes in (the
    // moon glowing up, the sun's rays bursting), the underline sweeps across, the cover's disc turns its colour and
    // gains or loses its craters; then the colours change everywhere as before (Reveal).

    private BigBody sunGlyph, moonGlyph;
    private View sunBar, moonBar;
    private float morph;                   // 0..1 through the change
    private boolean changing;

    private void switchTo(boolean toMoon) {
        if (changing) return;
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            host.setMoon(toMoon);
            return;
        }
        changing = true;
        View from = toMoon ? sunBar : moonBar, to = toMoon ? moonBar : sunBar;
        to.setBackground(Ui.shape(getContext(), toMoon ? p.violet : p.amber, 0, 2, false));
        from.setPivotX(toMoon ? from.getWidth() : 0);        // the bar leaves towards the other name
        to.setPivotX(toMoon ? 0 : to.getWidth());            // and arrives from it
        to.setScaleX(0f);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(620);
        a.setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f));
        a.addUpdateListener(v -> {
            morph = (float) v.getAnimatedValue();
            from.setScaleX(Math.max(0f, 1f - morph * 2f));
            to.setScaleX(Math.max(0f, Math.min(1f, morph * 2f - 1f)));
            if (sunGlyph != null) sunGlyph.invalidate();
            if (moonGlyph != null) moonGlyph.invalidate();
            disc.invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator x) {
                changing = false;
                morph = 0f;
                host.setMoon(toMoon);
            }
        });
        a.start();
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return android.graphics.Color.argb(
                Math.round(android.graphics.Color.alpha(a) + (android.graphics.Color.alpha(b) - android.graphics.Color.alpha(a)) * t),
                Math.round(android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * t),
                Math.round(android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * t),
                Math.round(android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * t));
    }

    /**
     * A tool as a row: its name and what it does on the left, its drawn icon on the right, so the tools fit one
     * screen with Continue (owner's sketch, 2026-11-15; was the icon in the middle with the words beneath, 3.1).
     */
    private void addTool(int tool, Ui.Icon icon, String words, int gap) {
        if (p.design != Ui.CLASSIC) {
            addRuledTool(tool, icon, words, gap);
            return;
        }
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        card.setBackground(Ui.shape(c, p.panel, p.line, 16, false));
        card.setClickable(true);
        Ui.pressable(card);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(c, Flow.toolName(tool), 18, 700, p.text));
        TextView body = Ui.text(c, words, 13.5f, 400, p.muted);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(c, 3);
        text.addView(body, bl);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginEnd(Ui.dp(c, 12));
        card.addView(text, tl);
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Ui.shape(c, Ui.withAlpha(p.accent, 28), 0, 99, false));
        android.widget.ImageView glyph = new android.widget.ImageView(c);
        glyph.setImageDrawable(Ui.icon(c, icon, p.accent));
        disc.addView(glyph, new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.CENTER));
        card.addView(disc, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        card.setContentDescription(Flow.toolName(tool));
        card.setOnClickListener(v -> host.startTool(tool));
        content.addView(card, top(gap));
    }

    /**
     * The Braun designs (3.7): a tool is a ruled row, like a booklet's contents, its line icon on the right. The
     * first row's rule is firm. Readout puts the icon in a round outlined key.
     */
    private boolean firstRule = true;
    private void addRuledTool(int tool, Ui.Icon icon, String words, int gap) {
        Context c = getContext();
        if (tool == Flow.LINE) firstRule = true;
        if (firstRule) {                  // the rows share the rule between them; the first has its own, firm in Pocket map
            View rule = new View(c);
            boolean firm = p.design == Ui.POCKET;
            rule.setBackgroundColor(firm ? p.text : p.line);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1,
                    firm ? Math.max(2, Ui.dp(c, 1.5f)) : Math.max(1, Ui.dp(c, 1)));
            rl.topMargin = Ui.dp(c, gap + 4);
            content.addView(rule, rl);
        }
        firstRule = false;
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 16));
        row.setClickable(true);
        Ui.pressable(row);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(c, Flow.toolName(tool), 19, 600, p.text);
        name.setLetterSpacing(-0.01f);
        text.addView(name);
        TextView body = Ui.text(c, words, 13.5f, 400, p.muted);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(c, 3);
        text.addView(body, bl);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginEnd(Ui.dp(c, 14));
        row.addView(text, tl);
        android.widget.ImageView glyph = new android.widget.ImageView(c);
        glyph.setImageDrawable(Ui.icon(c, icon, p.text));
        if (p.design == Ui.READOUT) {
            FrameLayout key = new FrameLayout(c);
            android.graphics.drawable.GradientDrawable g = Ui.shape(c, p.panel, p.line, 99, false);
            g.setStroke(Math.max(1, Ui.dp(c, 1.5f)), p.line);
            key.setBackground(g);
            key.addView(glyph, new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.CENTER));
            row.addView(key, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        } else {
            row.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        }
        row.setContentDescription(Flow.toolName(tool));
        row.setOnClickListener(v -> host.startTool(tool));
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View under = new View(c);
        under.setBackgroundColor(p.line);
        content.addView(under, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 1))));
    }

    /**
     * Sun or moon as in the owner's rendering (3.8): two large drawn bodies side by side, a hairline between, the
     * chosen one in its colour with a bar under its name; the other quiet.
     */
    private View bodyPicker() {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 2; i++) {
            final boolean isMoon = i == 1;
            boolean on = isMoon == moon;
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, Ui.dp(c, 8), 0, 0);
            BigBody glyph = new BigBody(c, isMoon, on);
            if (isMoon) moonGlyph = glyph; else sunGlyph = glyph;
            col.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(c, 66), Ui.dp(c, 66)));   // room for the glow
            TextView name = Ui.text(c, isMoon ? "Moon" : "Sun", 16, on ? 700 : 500, on ? p.text : p.muted);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = Ui.dp(c, 8);
            col.addView(name, nl);
            View bar = new View(c);
            bar.setBackground(on ? Ui.shape(c, p.accent, 0, 2, false) : null);
            if (isMoon) moonBar = bar; else sunBar = bar;
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            bl.topMargin = Ui.dp(c, 10);
            col.addView(bar, bl);
            View.OnClickListener pick = v -> { if (!on) switchTo(isMoon); };
            col.setClickable(true);
            col.setOnClickListener(pick);
            name.setOnClickListener(pick);
            col.setContentDescription(isMoon ? "Moon" : "Sun");
            col.setSelected(on);
            name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            if (i == 0) {
                View hair = new View(c);
                hair.setBackgroundColor(p.line);
                row.addView(hair, new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 1)), Ui.dp(c, 64)));
            }
        }
        return row;
    }

    /** The sun as a full disc, the moon as a crescent, in the body's colour when chosen and quiet when not. */
    private final class BigBody extends View {
        private final boolean isMoon, on;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        BigBody(Context c, boolean isMoon, boolean on) {
            super(c);
            this.isMoon = isMoon;
            this.on = on;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            float r = getWidth() / 2f * 0.68f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            int colour = isMoon ? p.violet : p.amber, quiet = mix(p.bg, p.muted, 0.55f);
            float lit = on ? 1f - morph : morph;               // how chosen it is, through the change
            float wave = (float) Math.sin(Math.PI * morph);    // 0 at both ends, 1 half way
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(mix(quiet, colour, lit));
            boolean coming = !on && morph > 0;
            float scale = 1f + (coming ? 0.1f : -0.08f) * wave;
            cv.save();
            cv.scale(scale, scale, cx, cy);
            if (coming) {                                       // arriving: a glow, and for the sun its rays
                android.graphics.Paint glow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                glow.setColor(Ui.withAlpha(colour, Math.round(80 * wave)));
                cv.drawCircle(cx, cy, r * (1f + 0.25f * wave), glow);
                if (!isMoon) {
                    glow.setColor(Ui.withAlpha(colour, Math.round(255 * wave)));
                    glow.setStrokeWidth(r * 0.12f);
                    glow.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * 2 * i / 8;
                        float r1 = r * (1.05f + 0.05f * wave), r2 = r * (1.15f + 0.15f * wave);
                        cv.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                                cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, glow);
                    }
                }
            }
            if (!isMoon) {
                cv.drawCircle(cx, cy, r, paint);
                if (on && morph > 0) {                          // leaving: the moon's shadow passes over the sun
                    android.graphics.Paint shade = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    shade.setColor(Ui.withAlpha(p.bg, 235));
                    cv.drawCircle(cx - 2.2f * r + 3.2f * r * morph, cy - r * 0.15f, r * 0.95f, shade);
                }
                cv.restore();
                return;
            }
            android.graphics.Path moonPath = new android.graphics.Path();
            moonPath.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            android.graphics.Path bite = new android.graphics.Path();
            bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, android.graphics.Path.Direction.CW);
            moonPath.op(bite, android.graphics.Path.Op.DIFFERENCE);
            cv.drawPath(moonPath, paint);
            cv.restore();
        }
    }

    /** Readout (3.7): Continue as a graphite readout, the subject and step large, a round key to go on. */
    private View continueReadout() {
        Context c = getContext();
        LinearLayout r = Ui.readout(c, p);
        int comma = continueText.indexOf(", ");
        String tool = comma > 0 ? continueText.substring(0, comma) : continueText;
        String rest = comma > 0 ? continueText.substring(comma + 2) : "";
        r.addView(Ui.readoutLabel(c, p, "Continue · " + tool));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = Ui.text(c, rest.isEmpty() ? tool : rest, 22, 600, p.readoutText);
        big.setLetterSpacing(-0.02f);
        big.setMaxLines(2);
        big.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -2, 1f);
        bl.setMarginEnd(Ui.dp(c, 12));
        row.addView(big, bl);
        FrameLayout go = Ui.roundKey(c, p, Ui.Icon.CHEV_RIGHT, "Continue", 52);
        go.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        go.setOnClickListener(v -> host.continueTool());
        row.addView(go, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
        LinearLayout.LayoutParams rowl = new LinearLayout.LayoutParams(-1, -2);
        rowl.topMargin = Ui.dp(c, 8);
        r.addView(row, rowl);
        r.setClickable(true);
        Ui.pressable(r);
        r.setContentDescription("Continue: " + continueText);
        r.setOnClickListener(v -> host.continueTool());
        return r;
    }

    /** The cover's disc: the sun as a quarter disc in the top corner; the moon as a crescent. */
    private final class CoverDisc extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        CoverDisc(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            float r = getWidth(), cx = getWidth(), cy = 0;
            if (morph <= 0) {
                drawBody(cv, cx, cy, r, moon, p.accent);
                return;
            }
            // 3.13.3 (owner: "the big sun moves fully, to be replaced with the moon"): the body leaving slides off past
            // the screen's edge on a slight arc, and the other one slides down into the corner in its place.
            float e = morph * morph * (3 - 2 * morph);           // smooth start and stop
            drawBody(cv, cx + 1.15f * r * e, cy + 0.25f * r * (float) Math.sin(Math.PI * e), r, moon, moon ? p.violet : p.amber);
            drawBody(cv, cx, cy - 1.15f * r * (1 - e), r, !moon, moon ? p.amber : p.violet);
        }

        /** The sun as a full disc; the moon the same with its soft craters. */
        private void drawBody(android.graphics.Canvas cv, float cx, float cy, float r, boolean asMoon, int colour) {
            paint.setColor(colour);
            cv.drawCircle(cx, cy, r, paint);
            if (!asMoon) return;
            // 3.13.1 (owner: "complete the moon circle and put some moon face minimal details"): a few soft craters
            float[][] craters = {{0.55f, 0.58f, 0.13f}, {0.82f, 0.28f, 0.08f}, {0.36f, 0.34f, 0.07f},
                    {0.80f, 0.74f, 0.06f}, {0.30f, 0.62f, 0.05f}};
            float dx = cx - getWidth(), dy = cy;                 // the craters ride with the disc
            android.graphics.Path disc = new android.graphics.Path();
            disc.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            cv.save();
            cv.clipPath(disc);
            paint.setColor(Ui.withAlpha(0xFF000000, p.night ? 46 : 34));
            for (float[] q : craters) cv.drawCircle(q[0] * r + dx, q[1] * r + dy, q[2] * r, paint);
            paint.setColor(Ui.withAlpha(0xFFFFFFFF, p.night ? 28 : 40));   // a faint lit rim, upper left
            for (float[] q : craters) cv.drawCircle(q[0] * r - q[2] * r * 0.25f + dx, q[1] * r - q[2] * r * 0.25f + dy, q[2] * r * 0.55f, paint);
            cv.restore();
        }
    }

    /** A row card (Continue): the words, and a chevron to go on. */
    private LinearLayout card(String number, String title, String words) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
        card.setBackground(Ui.shape(c, p.panel, p.line, p.design == Ui.POCKET ? 14 : 16, false));
        card.setClickable(true);
        Ui.pressable(card);
        if (number != null) {
            TextView n = Ui.text(c, number, 17, 700, p.accent);
            n.setGravity(Gravity.CENTER);
            n.setBackground(Ui.shape(c, Ui.withAlpha(p.accent, 28), 0, 99, false));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
            nl.setMarginEnd(Ui.dp(c, 14));
            card.addView(n, nl);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(c, title, 18, 700, p.text));
        col.addView(Ui.text(c, words, 14, 400, p.muted), top(3));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageView chev = new android.widget.ImageView(c);
        chev.setImageDrawable(Ui.icon(c, Ui.Icon.CHEV_RIGHT, p.muted));
        card.addView(chev, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        return card;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), dp);
        return l;
    }
}
