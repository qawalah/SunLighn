package dev.qawalah.sunlighn;

import android.content.Context;
import android.view.Gravity;
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
    private int insetTop, insetBottom;
    private boolean moon;
    private String continueText;

    HomeScreen(Context c, Ui.Palette p, Host host) {
        super(c);
        this.p = p;
        this.host = host;
        setBackgroundColor(p.bg);
        setClickable(true);
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
        int pad = Ui.dp(c, 16);
        content.setPadding(pad, insetTop + Ui.dp(c, 12), pad, insetBottom + Ui.dp(c, 24));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(c, "SunLighn", 26, 700, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.IconButton saved = new Ui.IconButton(c, p, Ui.Icon.SAVED, "Saved").bare();
        saved.setOnClickListener(v -> host.showSaved());
        head.addView(saved);
        Ui.IconButton gear = new Ui.IconButton(c, p, Ui.Icon.SETTINGS, "Settings").bare();
        gear.setOnClickListener(v -> host.showSettings());
        head.addView(gear);
        content.addView(head, new LinearLayout.LayoutParams(-1, -2));

        content.addView(Ui.text(c, "Get the " + body() + " exactly where you want it in your photo.", 15, 400, p.muted),
                top(4));
        content.addView(Ui.segmented(c, p, new String[]{"Sun", "Moon"}, new Ui.Icon[]{Ui.Icon.SUN, Ui.Icon.MOON},
                moon ? 1 : 0, i -> host.setMoon(i == 1)), top(16));

        if (continueText != null) {
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

    /** A row card (Continue): the words, and a chevron to go on. */
    private LinearLayout card(String number, String title, String words) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
        card.setBackground(Ui.shape(c, p.panel, p.line, 16, false));
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
