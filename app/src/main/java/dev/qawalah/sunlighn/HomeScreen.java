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
        head.addView(Ui.text(c, "SunLighn", pocket ? 20 : 26, 700, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        int onDisc = p.ink;                   // the keys sit on the disc, in the ink that goes on the accent
        Ui.IconButton saved = new Ui.IconButton(c, p, Ui.Icon.SAVED, "Saved").bare();
        if (pocket) saved.tint(onDisc);
        saved.setOnClickListener(v -> host.showSaved());
        head.addView(saved);
        Ui.IconButton gear = new Ui.IconButton(c, p, Ui.Icon.SETTINGS, "Settings").bare();
        if (pocket) gear.tint(onDisc);
        gear.setOnClickListener(v -> host.showSettings());
        head.addView(gear);
        content.addView(head, new LinearLayout.LayoutParams(-1, -2));

        String tagline = "Get the " + body() + " exactly where you want it in your photo.";
        if (pocket) {
            TextView cover = Ui.text(c, tagline, 30, 700, p.text);
            cover.setLetterSpacing(-0.025f);
            cover.setLineSpacing(0, 1.08f);
            content.addView(cover, top(56));
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
            col.addView(new BigBody(c, isMoon, on), new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
            TextView name = Ui.text(c, isMoon ? "Moon" : "Sun", 16, on ? 700 : 500, on ? p.text : p.muted);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = Ui.dp(c, 8);
            col.addView(name, nl);
            View bar = new View(c);
            bar.setBackground(on ? Ui.shape(c, p.accent, 0, 2, false) : null);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            bl.topMargin = Ui.dp(c, 10);
            col.addView(bar, bl);
            View.OnClickListener pick = v -> { if (!on) host.setMoon(isMoon); };
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
            float r = getWidth() / 2f * 0.86f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            int colour = isMoon ? p.violet : p.amber;
            paint.setColor(on ? colour : Ui.withAlpha(p.muted, 110));
            if (!isMoon) {
                cv.drawCircle(cx, cy, r, paint);
                return;
            }
            android.graphics.Path moonPath = new android.graphics.Path();
            moonPath.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            android.graphics.Path bite = new android.graphics.Path();
            bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, android.graphics.Path.Direction.CW);
            moonPath.op(bite, android.graphics.Path.Op.DIFFERENCE);
            cv.drawPath(moonPath, paint);
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
            paint.setColor(p.accent);
            cv.drawCircle(cx, cy, r, paint);
            if (moon) {                       // the crescent: the paper bites the disc's round edge
                paint.setColor(p.bg);
                cv.drawCircle(cx - r * 0.85f, cy + r * 0.85f, r * 0.55f, paint);
            }
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
