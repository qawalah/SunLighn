package dev.qawalah.sunlighn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * The guides (3.20, owner: "revisit How it works, make a tutorial for each mode, and one for the viewfinder: how to
 * navigate it and its main elements, what swiping does and what pinch does"). Swipeable cards, each with a small
 * animated picture, a title, a line of words and the points that matter, each with its icon (`Ui.note`).
 * <ul>
 * <li>{@link Kind#INTRO} How it works: what the app is for, Sun or moon, the two tools, Your shot, the map, Saved.
 *     Shown at the first start; Home and Settings replay it.</li>
 * <li>{@link Kind#LINE}, {@link Kind#BUILD}: one card per step of the tool, shown the first time the tool starts;
 *     the three dots (Line up a street) and Help (Build the shot) replay them.</li>
 * <li>{@link Kind#FRAME}: the viewfinder, shown the first time it opens; its Help key replays it. Its words follow
 *     the layout chosen in Settings (Answer band or Camera).</li>
 * </ul>
 * Skip is always there; Back closes a guide like Skip. Each guide is remembered as seen once closed.
 */
final class Tutorial extends FrameLayout {
    interface Listener { void onClosed(); }

    enum Kind { INTRO, LINE, FIND, BUILD, FRAME }

    /** The preference that remembers a guide as seen. */
    static String seenKey(Kind k) {
        switch (k) {
            case LINE: return "guide_line";
            case BUILD: return "guide_build";
            case FIND: return "guide_find";
            case FRAME: return "guide_frame";
            default: return "tutorial_done";
        }
    }

    // The pictures
    static final int A_LINE = 0, A_BUILD = 2, A_SWITCH = 3, A_TAP = 4, A_SUN_DRAG = 5, A_SHOT = 6, A_KEYS = 7,
            A_SCENE = 8, A_SWIPE = 9, A_PINCH = 10, A_TIME = 11, A_SITS = 12, A_SPOTS = 13, A_TAP_END = 14;

    /** One card. */
    static final class Page {
        final String title, body;
        final Ui.Icon tool;            // the tool's icon in a disc under the picture, as on Home (or null)
        final int art;
        final Ui.Icon[] noteIcons;
        final String[] notes;
        Ui.Icon[] keys;                // the keys a picture shows (A_SHOT, A_KEYS)
        String[] keyNames;

        Page(String title, Ui.Icon tool, int art, String body, Object... notes) {
            this.title = title;
            this.tool = tool;
            this.art = art;
            this.body = body;
            this.noteIcons = new Ui.Icon[notes.length / 2];
            this.notes = new String[notes.length / 2];
            for (int i = 0; i < this.notes.length; i++) {
                noteIcons[i] = (Ui.Icon) notes[2 * i];
                this.notes[i] = (String) notes[2 * i + 1];
            }
        }

        Page keys(Ui.Icon[] icons, String[] names) {
            keys = icons;
            keyNames = names;
            return this;
        }
    }

    /** The cards of a guide. */
    static Page[] pages(Context c, Kind kind) {
        switch (kind) {
            case LINE: return line();
            case BUILD: return build();
            case FIND: return find();
            case FRAME: return frame(Ui.viewfinder(c));
            default: return intro(c);
        }
    }

    private static Page[] intro(Context c) {
        // What the app is for comes first: the first-time tester read the whole card and still did not know (audit 3.2.2)
        String colours = Ui.accentIndex(c) == Ui.ACCENT_FOLLOW
                ? " The app takes its colour: amber for the sun, violet for the moon." : "";
        return new Page[]{
                new Page("Sun or moon", null, A_SWITCH,
                        "SunWhere tells you where to stand and when the sun or the moon sits exactly behind what you "
                                + "photograph. Choose the sun or the moon at the top of Home." + colours),
                new Page("Line up a street", Ui.Icon.TOOL_LINE, A_LINE,
                        "Tap the far end of a street, then where you stand on it. SunWhere says when the sun or the "
                                + "moon sits exactly on that line.",
                        Ui.Icon.CHART, "Sun height: drag the slider for how high it should be; Any height for every time.",
                        Ui.Icon.CUBE, "Hills and buildings that hide it are counted, and you are told where along the "
                                + "street it clears them."),
                new Page("Find the spot", Ui.Icon.TOOL_FIND, A_SPOTS,
                        "Pick your subject and where the sun or the moon should sit on it. SunWhere shows every spot "
                                + "to stand, and every day, on the map.",
                        Ui.Icon.LIST, "The days come in runs, under Sunset and Sunrise, month by month.",
                        Ui.Icon.VIEWFINDER, "Each spot comes with the picture from there."),
                new Page("Build the shot", Ui.Icon.TOOL_BUILD, A_BUILD,
                        "Pick your subject, then place the sun or the moon in the picture exactly. SunWhere finds "
                                + "where to stand and when.",
                        Ui.Icon.WARNING, "Red shading: heights it can never reach in the days looked at."),
                new Page("Your shot", null, A_SHOT,
                        "Every tool ends here: the moment, and the picture as the camera will see it.",
                        Ui.Icon.VIEWFINDER, "Open the viewfinder: change the time and the lens, walk, point the phone.",
                        Ui.Icon.BELL, "Remind me: a notification 30 minutes before. Save keeps it in Saved.",
                        Ui.Icon.SHARE, "Share sends a link; the other person opens the same shot.")
                        .keys(new Ui.Icon[]{Ui.Icon.VIEWFINDER, Ui.Icon.SAVED, Ui.Icon.BELL, Ui.Icon.SHARE},
                                new String[]{"Viewfinder", "Save", "Remind me", "Share"}),
                new Page("On the map", null, A_SUN_DRAG,
                        "The map answers to your fingers.",
                        Ui.Icon.LOCATE, "Tap to place the point the step asks for. Drag a pin to move it.",
                        Ui.Icon.SUN, "Drag the sun round you to move through the day.",
                        Ui.Icon.CUBE, "Two fingers or the cube tilt it in 3D; the needle key turns it north up."),
                new Page("Now", Ui.Icon.NOW, A_SUN_DRAG,
                        "Now, on Home, is the light where you are: no subject, no line.",
                        Ui.Icon.CHART, "Drag the day chart, or the sun on the map, to move through the day.",
                        Ui.Icon.CUBE, "Tilt the map to see the buildings' shadows at that time.",
                        Ui.Icon.LIVE, "Look through the camera: the sun's path over what you see."),
                new Page("Saved, and more help", null, A_KEYS,
                        "Saved and Settings are on Home.",
                        Ui.Icon.SAVED, "Saved: your shots, and Trip, every moment of the days you are away.",
                        Ui.Icon.HELP, "Each tool and the viewfinder show a short guide the first time. Replay any "
                                + "from Settings.")
                        .keys(new Ui.Icon[]{Ui.Icon.SAVED, Ui.Icon.TRIP, Ui.Icon.BELL, Ui.Icon.SETTINGS},
                                new String[]{"Shots", "Trip", "Reminder", "Settings"}),
        };
    }

    private static Page subjectPage() {
        return new Page("1 · Your subject", null, A_TAP,
                "Tap what you want to photograph on the map: a building, a tower, a tree.",
                Ui.Icon.SEARCH, "Or search above, by name or by coordinates.",
                Ui.Icon.HEIGHT, "What is it? sets its kind and usual height; the height can be typed.",
                Ui.Icon.MOVE, "Drag the pin to set it right.");
    }

    private static Page[] line() {
        return new Page[]{
                new Page("1 · The far end", null, A_TAP_END,
                        "Tap the far end of the street on the map: the end you want the sun or the moon in line with.",
                        Ui.Icon.SEARCH, "Or search above, by name or by coordinates.",
                        Ui.Icon.MOVE, "Drag the pin to set it right."),
                new Page("2 · Where you stand", Ui.Icon.TOOL_LINE, A_LINE,
                        "Tap where you will stand on the street. The line runs from you to the far end.",
                        Ui.Icon.LOCATE, "Use my location, or the round key, to stand where you are now.",
                        Ui.Icon.CHEV_RIGHT, "Next shows when the sun lines up."),
                new Page("3 · When", null, A_SUN_DRAG,
                        "The soonest time it lines up comes first, with See it in the viewfinder.",
                        Ui.Icon.CHART, "Sun height: drag the slider; only the times at those heights stay. Any height: all.",
                        Ui.Icon.CUBE, "Along the street: where the sun clears the buildings at the far end, and where it does not.",
                        Ui.Icon.LIST, "All times: the year, by month. Day chart: the whole day, hour by hour.",
                        Ui.Icon.MORE, "The three dots: save, swap the two ends, clear, this guide."),
                new Page("4 · Your shot", null, A_SHOT,
                        "The moment, and the picture as the camera will see it.",
                        Ui.Icon.VIEWFINDER, "Open the viewfinder, or tap the picture.",
                        Ui.Icon.BELL, "Save, Remind me, Share, Directions.",
                        Ui.Icon.CLOSE, "Close ends the tool; the shot stays as it is.")
                        .keys(new Ui.Icon[]{Ui.Icon.VIEWFINDER, Ui.Icon.SAVED, Ui.Icon.BELL, Ui.Icon.DIRECTIONS},
                                new String[]{"Viewfinder", "Save", "Remind me", "Directions"}),
        };
    }

    private static Page[] find() {
        return new Page[]{
                subjectPage(),
                new Page("2 · Where to stand", Ui.Icon.TOOL_FIND, A_SPOTS,
                        "A dot on the map for every day: stand there and the sun sits where you asked.",
                        Ui.Icon.CHART, "Horizon, Middle or On top on the subject, or drag Sun height for any height.",
                        Ui.Icon.LOCATE, "Distance from the subject: how far away the dots are.",
                        Ui.Icon.LIST, "Runs of days under Sunset and Sunrise, by month. Tap one, or a dot on the map.",
                        Ui.Icon.SETTINGS, "Settings: Light, Behind subject or Behind you, How full, Lens, On foot or Drone."),
                new Page("3 · Your shot", null, A_SHOT,
                        "Stand here takes the next day of the run; the picture opens the viewfinder at once.",
                        Ui.Icon.VIEWFINDER, "Open the viewfinder, or tap the picture.",
                        Ui.Icon.TOOL_BUILD, "Build the shot frames it exactly, with the same subject.")
                        .keys(new Ui.Icon[]{Ui.Icon.VIEWFINDER, Ui.Icon.SAVED, Ui.Icon.BELL, Ui.Icon.DIRECTIONS},
                                new String[]{"Viewfinder", "Save", "Remind me", "Directions"}),
        };
    }

    private static Page[] build() {
        return new Page[]{
                subjectPage(),
                new Page("2 · Place the sun", Ui.Icon.TOOL_BUILD, A_BUILD,
                        "The picture as the camera will see it. Put the sun or the moon where you want it.",
                        Ui.Icon.SUN, "Drag the sun by its ring.",
                        Ui.Icon.MOVE, "Drag anywhere else to frame the picture; Centre puts it back.",
                        Ui.Icon.PLUS, "Pinch to make the subject bigger or smaller: that is how close you stand."),
                new Page("3 · Where it sits", null, A_SITS,
                        "Horizon, Middle and On top put the sun there and keep it there while you change the rest.",
                        Ui.Icon.WARNING, "Red shading: heights it never reaches in the days looked at.",
                        Ui.Icon.SETTINGS, "Pull up the card for Light, Look in, Distance, On foot or Drone."),
                new Page("4 · When, and Stand here", null, A_SHOT,
                        "When lists the days found, each with its own spot.",
                        Ui.Icon.LOCATE, "Stand here puts you at that spot and opens Your shot.",
                        Ui.Icon.VIEWFINDER, "The framing goes with the shot into the viewfinder.")
                        .keys(new Ui.Icon[]{Ui.Icon.LIST, Ui.Icon.LOCATE, Ui.Icon.VIEWFINDER},
                                new String[]{"When", "Stand here", "Viewfinder"}),
        };
    }

    private static Page[] frame(int layout) {
        boolean camera = layout == Ui.VF_CAMERA;
        return new Page[]{
                new Page("The viewfinder", null, A_SCENE,
                        "Your shot as the camera will see it, at the time shown.",
                        Ui.Icon.SUN, "The dotted curve is the sun's path, the hours marked on it.",
                        Ui.Icon.VIEWFINDER, "The bright corners are the edges of the picture: what the lens takes in.",
                        Ui.Icon.LOCATE, "The compass runs along the top, the height above the horizon down the side."),
                new Page("Swipe", null, A_SWIPE,
                        "Drag on the sky.",
                        Ui.Icon.CLOCK, "Sideways moves the time: the sun walks along its path.",
                        Ui.Icon.HEIGHT, "Up or down tilts the camera.",
                        Ui.Icon.LOCATE, "Double tap aims at the subject again."),
                new Page("Pinch", null, A_PINCH,
                        "Two fingers change the lens, from 14 to 400 mm.",
                        Ui.Icon.PLUS, "Spread them for a longer lens: the subject and the sun grow.",
                        Ui.Icon.MINUS, "Pinch them together for a wider lens: more fits in.",
                        Ui.Icon.CHECK, camera ? "Or tap a length on the lens scale." : "Or use the − and + keys."),
                new Page("The time", null, A_TIME,
                        camera ? "The time is over the sky; the slider under the picture moves it."
                                : "The band at the top names the moment the sun lines up.",
                        Ui.Icon.SUN, camera ? "The orange Lined up key takes you to the moment: filled when you are there."
                                : "Tap the band to go to that moment.",
                        Ui.Icon.CLOCK, camera ? "Drag the slider to move through the day."
                                : "Drag the tuning scale to move the time; it settles on lined up, sunrise and sunset.",
                        Ui.Icon.SETTINGS, "Answer band or Camera: choose the layout in Settings, Viewfinder."),
                new Page("The keys", null, A_KEYS,
                        "Each key has its name under it.",
                        Ui.Icon.LIVE, "Compass follows the phone; with the camera allowed, the real sky is behind.",
                        Ui.Icon.MOVE, "Move walks where you stand with a joystick; the moment is worked out again.",
                        Ui.Icon.HEIGHT, "Height sets the subject's height. Turn: portrait or landscape.",
                        Ui.Icon.BACK, "Back returns to Your shot. Help shows this guide again.")
                        .keys(new Ui.Icon[]{Ui.Icon.LIVE, Ui.Icon.ROTATE, Ui.Icon.HEIGHT, Ui.Icon.MOVE},
                                new String[]{"Compass", "Turn", "Height", "Move"}),
        };
    }

    private final Ui.Palette p;
    private final Listener listener;
    private final Kind kind;
    private final Page[] pages;
    private final Pager pager;
    private final Art[] arts;
    private final View[] dots;
    private final TextView next, skip;
    private int page = -1;

    /** How it works (the first-start guide). */
    Tutorial(Context c, Ui.Palette p, Listener listener) {
        this(c, p, Kind.INTRO, listener);
    }

    Tutorial(Context c, Ui.Palette p, Kind kind, Listener listener) {
        super(c);
        this.p = p;
        this.listener = listener;
        this.kind = kind;
        this.pages = pages(c, kind);
        int n = pages.length;
        arts = new Art[n];
        dots = new View[n];
        setBackgroundColor(p.bg);
        setClickable(true); // swallow taps so the map underneath is not touched
        setContentDescription(kind == Kind.INTRO ? "How it works" : "Guide");

        pager = new Pager(c);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(OVER_SCROLL_NEVER);
        PageRow row = new PageRow(c);
        pager.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        int artH = Math.max(Ui.dp(c, 140), Math.min(Ui.dp(c, 290),
                Math.round(c.getResources().getDisplayMetrics().heightPixels * 0.34f)));
        for (int i = 0; i < n; i++) {
            Page pg = pages[i];
            ScrollView scroll = new ScrollView(c);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Ui.dp(c, 28);
            card.setPadding(pad, Ui.dp(c, 40), pad, Ui.dp(c, 150));
            arts[i] = new Art(c, pg);
            final Art art = arts[i];
            art.setOnClickListener(v -> art.play());   // a tap plays the picture again
            card.addView(arts[i], new LinearLayout.LayoutParams(-1, artH));
            if (pg.tool != null) {   // the tool's icon, as on its Home card
                FrameLayout disc = new FrameLayout(c);
                disc.setBackground(Ui.shape(c, Ui.withAlpha(p.accent, 28), 0, 99, false));
                android.widget.ImageView glyph = new android.widget.ImageView(c);
                glyph.setImageDrawable(Ui.icon(c, pg.tool, p.accent));
                disc.addView(glyph, new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48));
                dl.topMargin = Ui.dp(c, 14);
                card.addView(disc, dl);
            }
            TextView title = Ui.text(c, pg.title, kind == Kind.INTRO ? 28 : 26, true, p.text);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(c, 12);
            card.addView(title, tl);
            TextView body = Ui.text(c, pg.body, 15, false, p.muted);
            body.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 8);
            card.addView(body, bl);
            for (int k = 0; k < pg.notes.length; k++) {   // the points, each with its icon in the accent
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
                nl.topMargin = Ui.dp(c, k == 0 ? 16 : 9);
                card.addView(Ui.note(c, p, pg.noteIcons[k], pg.notes[k]).size(14, 400).color(p.text)
                        .icon(pg.noteIcons[k], p.accent), nl);
            }
            scroll.addView(card, new ScrollView.LayoutParams(-1, -2));
            row.addView(scroll, new LinearLayout.LayoutParams(0, -1)); // as wide as the pager: see PageRow
        }
        addView(pager, new LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 24));
        bottom.setBackgroundColor(p.bg);    // the card's words scroll under it, not through it
        LinearLayout dotRow = new LinearLayout(c);
        for (int i = 0; i < n; i++) {
            View d = new View(c);
            dots[i] = d;
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8));
            dl.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            dotRow.addView(d, dl);
        }
        bottom.addView(dotRow, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout buttons = new LinearLayout(c);
        skip = Ui.button(c, p, "Skip", false);
        next = Ui.button(c, p, "Next", true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
        sl.rightMargin = Ui.dp(c, 8);
        buttons.addView(skip, sl);
        buttons.addView(next, new LinearLayout.LayoutParams(0, -2, 2f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(c, 16);
        bottom.addView(buttons, bl);
        LayoutParams bp = new LayoutParams(-1, -2);
        bp.gravity = Gravity.BOTTOM;
        addView(bottom, bp);

        skip.setOnClickListener(v -> listener.onClosed());
        next.setOnClickListener(v -> {
            if (page >= pages.length - 1) listener.onClosed();
            else pager.goTo(page + 1);
        });
        pager.onPage = this::showPage;
        showPage(0);
    }

    Kind kind() { return kind; }

    /** Keep the buttons clear of the system bars. */
    void setInsets(int top, int bottomInset) {
        setPadding(0, top, 0, bottomInset);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        pager.post(() -> pager.scrollTo(Math.max(page, 0) * pager.getWidth(), 0));   // stay on the card after a turn
    }

    /**
     * The row of cards. Each card is as wide as the pager, set while measuring: sizing them from onSizeChanged
     * changed the layout inside a layout pass and the phone showed empty pages (3.1.0).
     */
    private static final class PageRow extends LinearLayout {
        PageRow(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
        }

        int pageWidth;   // given by the pager before it measures this row

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int page = pageWidth > 0 ? pageWidth : getResources().getDisplayMetrics().widthPixels;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).getLayoutParams().width = page;
            super.onMeasure(MeasureSpec.makeMeasureSpec(page * getChildCount(), MeasureSpec.EXACTLY), hSpec);
        }
    }

    private void showPage(int i) {
        if (i == page) return;
        page = i;
        for (int k = 0; k < pages.length; k++) {
            dots[k].setBackground(Ui.shape(getContext(), k == i ? p.accent : p.line, 0, 99, false));
        }
        next.setText(i >= pages.length - 1 ? "Got it" : "Next");
        skip.setVisibility(i >= pages.length - 1 ? INVISIBLE : VISIBLE);
        arts[i].play(); // the picture restarts each time its card becomes current
    }

    /** Horizontal pager: snaps to a card, a fling moves one card. */
    private final class Pager extends HorizontalScrollView {
        Runnable1 onPage;
        private int fling = 0;
        private final GestureDetector detector;

        Pager(Context c) {
            super(c);
            detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener());
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            if (getChildCount() > 0 && getChildAt(0) instanceof PageRow) {
                ((PageRow) getChildAt(0)).pageWidth = MeasureSpec.getSize(wSpec);
            }
            super.onMeasure(wSpec, hSpec);
        }

        int pageNow() {
            int w = Math.max(getWidth(), 1);
            return Math.max(0, Math.min(pages.length - 1, Math.round(getScrollX() / (float) w)));
        }

        void goTo(int i) {
            i = Math.max(0, Math.min(pages.length - 1, i));
            smoothScrollTo(i * getWidth(), 0);
            if (onPage != null) onPage.run(i);
        }

        @Override public void fling(int velocityX) {
            fling = velocityX > 0 ? 1 : -1; // handled when the finger lifts
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            detector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_DOWN) fling = 0;
            boolean r = super.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                int target = fling != 0 ? Math.round(getScrollX() / (float) Math.max(getWidth(), 1) + fling * 0.5f)
                        : pageNow();
                goTo(target);
            }
            return r;
        }
    }

    private interface Runnable1 { void run(int page); }

    /** The small animated picture on each card. t runs 0..1 over a few seconds. */
    private final class Art extends View {
        private final Page pg;
        private final int kind;
        private float t = 1f;
        private ValueAnimator anim;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Art(Context c, Page pg) {
            super(c);
            this.pg = pg;
            this.kind = pg.art;
            setContentDescription("Picture: " + pg.title + ". Tap to play it again.");
        }

        void play() {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setInterpolator(null);    // linear: the parts below ease themselves
            anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
            // 3.11: a slow whole lets each part be seen; the gestures take longer, they have two halves
            anim.setDuration(kind == A_SWITCH ? 2600 : kind == A_SWIPE || kind == A_TIME || kind == A_SITS ? 3600 : 2800);
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        private float lerp(float a, float b, float f) { return a + (b - a) * f; }
        private float part(float from, float to) { return Math.max(0f, Math.min(1f, (t - from) / (to - from))); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float k = Ui.dp(getContext(), 1);
            float cx = w / 2f, cy = h / 2f;
            float s = Math.min(w, h * 1.1f);
            float half = Math.min(w / 2f, s / 2f);
            paint.setShader(null);
            paint.setPathEffect(null);
            paint.setStrokeCap(Paint.Cap.ROUND);
            switch (kind) {
                case A_SWITCH: drawSwitch(cv); return;
                case A_BUILD: drawViewfinder(cv, cx, cy, half, k); return;
                case A_TAP: drawTap(cv, box(cx, cy, half), k, true); return;
                case A_TAP_END: drawTap(cv, box(cx, cy, half), k, false); return;
                case A_SUN_DRAG: drawSunDrag(cv, box(cx, cy, half), k); return;
                case A_SHOT: drawShot(cv, box(cx, cy, half), k); return;
                case A_KEYS: drawKeys(cv, box(cx, cy, half), k, true); return;
                case A_SCENE: drawSceneCard(cv, box(cx, cy, half), k); return;
                case A_SWIPE: drawSwipe(cv, box(cx, cy, half), k); return;
                case A_PINCH: drawPinch(cv, box(cx, cy, half), k); return;
                case A_TIME: drawTime(cv, box(cx, cy, half), k); return;
                case A_SITS: drawSits(cv, box(cx, cy, half), k); return;
                case A_SPOTS: drawSpotsArt(cv, box(cx, cy, half), k); return;
                default: break;
            }
            // Line up a street (3.11, owner: the picture was "empty"): a street map; the subject is tapped, you drop at
            // the other end, the line draws, and the sun comes to stand where the line leaves the map.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            cv.drawRoundRect(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f, 16 * k, 16 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * k);
            paint.setColor(p.line);
            cv.drawRoundRect(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f, 16 * k, 16 * k, paint);
            for (int i = 1; i < 4; i++) {
                float gx = cx - half + 2 * half * i / 4f;
                cv.drawLine(gx, cy - half * 0.8f + 8 * k, gx, cy + half * 0.8f - 8 * k, paint);
                float gy = cy - half * 0.8f + 1.6f * half * i / 4f;
                cv.drawLine(cx - half + 8 * k, gy, cx + half - 8 * k, gy, paint);
            }
            float ax = cx + half * 0.55f, ay = cy + half * 0.45f;   // you, at one end of the street
            float bx = cx - half * 0.05f, by = cy - half * 0.05f;   // the subject, at the other
            paint.setStrokeCap(Paint.Cap.ROUND);
            float u = 2.2f * k;
            // the line, then its dotted way on to the sun at the map's edge
            float draw = part(0.42f, 0.62f);
            if (draw > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4 * k);
                paint.setColor(p.accent);
                cv.drawLine(ax, ay, lerp(ax, bx, draw), lerp(ay, by, draw), paint);
            }
            float dx = bx - ax, dy = by - ay, len = (float) Math.hypot(dx, dy), ux = dx / len, uy = dy / len;
            float reach = Math.min((bx - (cx - half + 22 * k)) / -ux, (by - (cy - half * 0.8f + 22 * k)) / -uy);
            float on = part(0.62f, 0.92f);
            if (on > 0) {
                float ex = bx + ux * reach * on, ey = by + uy * reach * on;
                paint.setStrokeWidth(3 * k);
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{0.1f, 8 * k}, 0));
                paint.setColor(p.amber);
                cv.drawLine(bx, by, ex, ey, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Ui.withAlpha(p.amber, 70));
                cv.drawCircle(ex, ey, 18 * k, paint);
                paint.setColor(p.amber);
                cv.drawCircle(ex, ey, 11 * k, paint);
            }
            // the subject: tapped first, an ink mark with a white ring
            float pop = ease(part(0f, 0.2f));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(bx, by, 11 * k * pop, paint);
            paint.setColor(p.text);
            cv.drawCircle(bx, by, 8 * k * pop, paint);
            // you: the pin drops onto its spot
            float drop = part(0.18f, 0.45f);
            if (drop > 0) {
                float back = drop - 1f;
                float eased = 1f + 2.7f * back * back * back + 1.7f * back * back;   // overshoots, settles
                Ui.drawPinHalo(cv, ax, ay, u, p.you, drop);
                Ui.drawPin(cv, ax, ay, u, (eased - 1f) * 26f * u, Math.min(1f, drop * 3f));
            }
        }

        private RectF box(float cx, float cy, float half) {
            return new RectF(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f);
        }

        // ---- shared pieces ----

        /** A finger on the glass: a soft disc with a ring; {@code a} fades it. */
        private void finger(Canvas cv, float x, float y, float a, float k) {
            if (a <= 0) return;
            paint.setShader(null);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(0xFFFFFFFF, Math.round(110 * a)));
            cv.drawCircle(x, y, 17 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * k);
            paint.setColor(Ui.withAlpha(0xFF1A1F2B, Math.round(150 * a)));
            cv.drawCircle(x, y, 17 * k, paint);
        }

        /** A paper map with streets, in the card's colours. */
        private void mapBox(Canvas cv, RectF b, float k) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            cv.drawRoundRect(b, 16 * k, 16 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * k);
            paint.setColor(p.line);
            cv.drawRoundRect(b, 16 * k, 16 * k, paint);
            for (int i = 1; i < 4; i++) {
                float gx = b.left + b.width() * i / 4f;
                cv.drawLine(gx, b.top + 8 * k, gx, b.bottom - 8 * k, paint);
                float gy = b.top + b.height() * i / 4f;
                cv.drawLine(b.left + 8 * k, gy, b.right - 8 * k, gy, paint);
            }
        }

        /** The subject on the map: an ink dot with a white ring. */
        private void subjectDot(Canvas cv, float x, float y, float pop, float k) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(x, y, 11 * k * pop, paint);
            paint.setColor(p.text);
            cv.drawCircle(x, y, 8 * k * pop, paint);
        }

        /** A word over a picture, centred, with a soft pale backing. */
        private void tag(Canvas cv, String s, float x, float y, float k, boolean onDark) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Ui.font(getContext(), 700));
            paint.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
            float tw = paint.measureText(s), th = paint.getTextSize();
            RectF r = new RectF(x - tw / 2 - 8 * k, y - th / 2 - 6 * k, x + tw / 2 + 8 * k, y + th / 2 + 6 * k);
            paint.setColor(onDark ? 0xCC0B1220 : Ui.withAlpha(p.bg, 235));
            cv.drawRoundRect(r, 9 * k, 9 * k, paint);
            paint.setColor(onDark ? 0xFFFFFFFF : p.text);
            cv.drawText(s, x - tw / 2, y + th * 0.35f, paint);
        }

        /** A time of day for a fraction between two times, "1:05 PM". */
        private String clock(int fromMin, int toMin, float f) {
            int m = Math.round(lerp(fromMin, toMin, f));
            int hh = (m / 60) % 24, mm = m % 60;
            return String.format(Locale.US, "%d:%02d %s", hh % 12 == 0 ? 12 : hh % 12, mm, hh < 12 ? "AM" : "PM");
        }

        /**
         * The viewfinder's scene in a box: the sky by the sun's height, the land, a tower on the horizon, the sun's
         * dotted path with hours, the sun at {@code f} along it, and the frame's corners. {@code tilt} moves the
         * horizon (pixels, up positive), {@code zoom} scales the scene round the tower's top.
         */
        private void scene(Canvas cv, RectF b, float f, float tilt, float zoom, boolean hours, float k) {
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(b, 16 * k, 16 * k, android.graphics.Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            float horizon = b.top + b.height() * 0.70f + tilt;
            float cx = b.centerX();
            float tw = b.width() * 0.07f, th = b.height() * 0.30f, ttop = horizon - th;
            cv.save();
            cv.scale(zoom, zoom, cx, ttop);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, b.top - b.height(), 0, horizon, 0xFF26304A, 0xFFD39364, Shader.TileMode.CLAMP));
            cv.drawRect(b.left - b.width(), b.top - b.height(), b.right + b.width(), horizon, paint);
            paint.setShader(null);
            paint.setColor(0xFF141820);
            cv.drawRect(b.left - b.width(), horizon, b.right + b.width(), b.bottom + b.height(), paint);
            paint.setColor(0x66FFE2BF);
            paint.setStrokeWidth(1 * k);
            cv.drawLine(b.left - b.width(), horizon, b.right + b.width(), horizon, paint);
            // the sun's path: an arc from upper left down through the tower's top to the horizon at right
            android.graphics.Path path = new android.graphics.Path();
            float x0 = b.left + b.width() * 0.05f, y0 = b.top + b.height() * 0.22f + tilt;
            float x1 = b.right - b.width() * 0.08f, y1 = horizon + b.height() * 0.06f;
            float qx = cx + b.width() * 0.18f, qy = b.top + b.height() * 0.18f + tilt;
            path.moveTo(x0, y0);
            path.quadTo(qx, qy, x1, y1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * k);
            paint.setColor(Ui.withAlpha(p.amber, 200));
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{2 * k, 6 * k}, 0));
            cv.drawPath(path, paint);
            paint.setPathEffect(null);
            android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
            float[] pos = new float[2];
            if (hours) {
                String[] hs = {"2", "3", "4", "5"};
                paint.setTypeface(Ui.font(getContext(), 600));
                paint.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
                for (int i = 0; i < hs.length; i++) {
                    pm.getPosTan(pm.getLength() * (0.15f + 0.24f * i), pos, null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xCCFFFFFF);
                    cv.drawCircle(pos[0], pos[1], 2.2f * k, paint);
                    cv.drawText(hs[i], pos[0] - 3 * k, pos[1] - 7 * k, paint);
                }
            }
            // the tower, a silhouette with a lit edge
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0C0F15);
            cv.drawRect(cx - tw / 2, ttop, cx + tw / 2, horizon + 2 * k, paint);
            cv.drawRect(cx - tw * 0.18f, ttop - th * 0.25f, cx + tw * 0.18f, ttop, paint);
            paint.setColor(0x55FFD9A6);
            cv.drawRect(cx + tw / 2 - 1.5f * k, ttop, cx + tw / 2, horizon, paint);
            // the sun
            pm.getPosTan(pm.getLength() * Math.max(0f, Math.min(1f, f)), pos, null);
            float sr = 10 * k;
            paint.setColor(0x55FFC067);
            cv.drawCircle(pos[0], pos[1], sr * 2.2f, paint);
            paint.setColor(p.amber);
            cv.drawCircle(pos[0], pos[1], sr, paint);
            sunX = (pos[0] - cx) * zoom + cx;
            sunY = (pos[1] - ttop) * zoom + ttop;
            cv.restore();
            cv.restore();
            corners(cv, b, k);
        }

        private float sunX, sunY;   // where scene() drew the sun, for the finger

        private void corners(Canvas cv, RectF b, float k) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * k);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setColor(0xFFFFFFFF);
            float in = 14 * k, c = 22 * k;
            float fl = b.left + in, fr = b.right - in, ft = b.top + in, fb = b.bottom - in;
            cv.drawLine(fl, ft, fl + c, ft, paint); cv.drawLine(fl, ft, fl, ft + c, paint);
            cv.drawLine(fr, ft, fr - c, ft, paint); cv.drawLine(fr, ft, fr, ft + c, paint);
            cv.drawLine(fl, fb, fl + c, fb, paint); cv.drawLine(fl, fb, fl, fb - c, paint);
            cv.drawLine(fr, fb, fr - c, fb, paint); cv.drawLine(fr, fb, fr, fb - c, paint);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        /** A round key with its icon, its name under it; lit keys take the accent. */
        private float slot = Float.MAX_VALUE;   // the width each key of a row has, for its name

        private void key(Canvas cv, float x, float y, float r, Ui.Icon icon, String name, float lit, float k) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.raised);
            cv.drawCircle(x, y, r, paint);
            if (lit > 0) {
                paint.setColor(Ui.withAlpha(p.accent, Math.round(60 * lit)));
                cv.drawCircle(x, y, r + 5 * k * lit, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * k);
            paint.setColor(lit > 0.5f ? p.accent : p.line);
            cv.drawCircle(x, y, r, paint);
            android.graphics.drawable.Drawable d = Ui.icon(getContext(), icon, lit > 0.5f ? p.accent : p.text);
            int ih = Math.round(r * 0.95f);
            d.setBounds(Math.round(x - ih / 2f), Math.round(y - ih / 2f), Math.round(x + ih / 2f), Math.round(y + ih / 2f));
            d.draw(cv);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Ui.font(getContext(), lit > 0.5f ? 700 : 500));
            paint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
            float room = slot - 4 * k;   // the name fits its key's share of the row
            if (paint.measureText(name) > room) paint.setTextSize(paint.getTextSize() * room / paint.measureText(name));
            paint.setColor(lit > 0.5f ? p.text : p.muted);
            float tw = paint.measureText(name);
            cv.drawText(name, x - tw / 2, y + r + 16 * k, paint);
        }

        // ---- the pictures ----

        /** Your subject: a finger taps the map, the subject's dot pops up, What is it? offers kinds. */
        private void drawTap(Canvas cv, RectF b, float k, boolean askKind) {
            mapBox(cv, b, k);
            float sx = b.centerX() - b.width() * 0.12f, sy = b.centerY() - b.height() * 0.05f;
            float come = (float) ease(part(0f, 0.28f));
            float fx = lerp(b.right - 30 * k, sx, come), fy = lerp(b.bottom - 20 * k, sy, come);
            float pop = ease(part(0.32f, 0.45f));
            if (pop > 0) subjectDot(cv, sx, sy, pop, k);
            float ripple = part(0.28f, 0.5f);
            if (ripple > 0 && ripple < 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setColor(Ui.withAlpha(p.accent, Math.round(200 * (1 - ripple))));
                cv.drawCircle(sx, sy, (12 + 26 * ripple) * k, paint);
            }
            finger(cv, fx + 4 * k, fy + 14 * k, 1f - part(0.45f, 0.55f), k);
            if (!askKind) {                // 3.24: a street's far end is a point: no kind to pick
                if (pop > 0.9f) tag(cv, "Far end", sx, sy - 32 * k, k, false);
                return;
            }
            // What is it? A kind is picked and its usual height said
            float ask = ease(part(0.55f, 0.7f));
            if (ask > 0) {
                String[] kinds = {"Tree", "Building", "Tower"};
                float y = b.bottom - 34 * k;
                paint.setTypeface(Ui.font(getContext(), 600));
                paint.setTextSize(12.5f * getResources().getDisplayMetrics().scaledDensity);
                float gap = 8 * k, total = 0;
                float[] ws = new float[kinds.length];
                for (int i = 0; i < kinds.length; i++) { ws[i] = paint.measureText(kinds[i]) + 24 * k; total += ws[i]; }
                total += gap * (kinds.length - 1);
                float x = b.centerX() - total / 2;
                boolean pick = t > 0.85f;
                for (int i = 0; i < kinds.length; i++) {
                    boolean on = pick && i == 1;
                    RectF r = new RectF(x, y - 15 * k + (1 - ask) * 12 * k, x + ws[i], y + 15 * k + (1 - ask) * 12 * k);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(on ? p.accent : p.raised);
                    paint.setAlpha(Math.round(255 * ask));
                    cv.drawRoundRect(r, 10 * k, 10 * k, paint);
                    paint.setColor(on ? p.ink : p.text);
                    paint.setAlpha(Math.round(255 * ask));
                    cv.drawText(kinds[i], x + 12 * k, r.centerY() + paint.getTextSize() * 0.35f, paint);
                    x += ws[i] + gap;
                }
                paint.setAlpha(255);
                tag(cv, pick ? "Building · 30 m" : "What is it?", sx, sy - 32 * k, k, false);
            }
        }

        /**
         * Find the spot (3.24): the subject on a map; a dot comes up for every day round it, one is tapped, it grows,
         * a dashed line runs to it from the subject and it says Stand here.
         */
        private void drawSpotsArt(Canvas cv, RectF b, float k) {
            mapBox(cv, b, k);
            float sx = b.centerX(), sy = b.centerY() + b.height() * 0.08f;
            float r = Math.min(b.width(), b.height()) * 0.36f;
            int n = 13;
            float shown = part(0.05f, 0.5f) * n;
            int pick = 9;
            float picked = ease(part(0.62f, 0.78f));
            float px = 0, py = 0;
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(150 + 150.0 * i / (n - 1));        // a ring west to east, over the north
                float x = sx + (float) Math.sin(a) * r, y = sy - (float) Math.cos(a) * r * 0.8f;
                if (i == pick) { px = x; py = y; }
                float on = Math.max(0, Math.min(1, shown - i));
                if (on <= 0) continue;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                paint.setAlpha(Math.round(220 * on));
                float big = i == pick ? 1 + picked * 0.7f : 1;
                cv.drawCircle(x, y, 5.5f * k * big, paint);
                paint.setColor(i == pick && picked > 0 ? p.accent : p.amber);
                paint.setAlpha(Math.round(255 * on));
                cv.drawCircle(x, y, 4f * k * big, paint);
                paint.setAlpha(255);
            }
            if (picked > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * k);
                paint.setColor(p.accent);
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{6 * k, 6 * k}, 0));
                cv.drawLine(sx, sy, lerp(sx, px, picked), lerp(sy, py, picked), paint);
                paint.setPathEffect(null);
            }
            subjectDot(cv, sx, sy, 1f, k);
            float come = (float) ease(part(0.5f, 0.62f));
            if (come > 0 && t < 0.78f) finger(cv, lerp(b.right - 20 * k, px, come) + 4 * k, lerp(b.bottom - 10 * k, py, come) + 14 * k,
                    1f - part(0.7f, 0.78f), k);
            if (picked >= 1) tag(cv, "Stand here · Tomorrow", px, py - 18 * k, k, false);
            else if (shown >= n) tag(cv, "A spot for every day", sx, b.top + 22 * k, k, false);
        }

        /** On the map: You, the subject and the line; a finger takes the sun round you and the time follows. */
        private void drawSunDrag(Canvas cv, RectF b, float k) {
            mapBox(cv, b, k);
            float ax = b.centerX() - b.width() * 0.05f, ay = b.top + b.height() * 0.38f;    // you
            float bx = b.right - b.width() * 0.16f, by = b.top + b.height() * 0.30f;        // the subject
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * k);
            paint.setColor(p.accent);
            cv.drawLine(ax, ay, bx, by, paint);
            subjectDot(cv, bx, by, 1f, k);
            float r = Math.min(b.width() * 0.34f, b.height() * 0.48f);
            RectF arc = new RectF(ax - r, ay - r, ax + r, ay + r);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * k);
            paint.setColor(Ui.withAlpha(p.amber, 200));
            cv.drawArc(arc, 10, 160, false, paint);   // the day's path, the south half round you
            float f = ease(part(0.2f, 0.85f));
            double ang = Math.toRadians(lerp(150, 40, f));
            float sx = ax + (float) Math.cos(ang) * r, sy = ay + (float) Math.sin(ang) * r;
            paint.setStrokeWidth(1.5f * k);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{3 * k, 5 * k}, 0));
            cv.drawLine(ax, ay, sx, sy, paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.withAlpha(p.amber, 70));
            cv.drawCircle(sx, sy, 19 * k, paint);
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(sx, sy, 13 * k, paint);
            paint.setColor(p.amber);
            cv.drawCircle(sx, sy, 10.5f * k, paint);
            Ui.drawPinHalo(cv, ax, ay, 2.2f * k, p.you, 1f);
            Ui.drawPin(cv, ax, ay, 2.2f * k, 0f, 1f);
            float press = part(0.1f, 0.2f) * (1 - part(0.88f, 0.98f));
            finger(cv, sx + 6 * k, sy + 12 * k, press, k);
            if (t > 0.12f) tag(cv, clock(10 * 60, 16 * 60 + 40, f), sx, sy - 30 * k, k, false);
        }

        /** Your shot: the still picture, and its keys lighting one after the other. */
        private void drawShot(Canvas cv, RectF b, float k) {
            RectF pic = new RectF(b.left, b.top, b.right, b.top + b.height() * 0.56f);
            scene(cv, pic, 0.62f, 0, 1, false, k);
            drawKeys(cv, new RectF(b.left, pic.bottom + 10 * k, b.right, b.bottom), k, false);
        }

        /** A row of keys, each lit in turn. */
        private void drawKeys(Canvas cv, RectF b, float k, boolean panel) {
            if (pg.keys == null) return;
            int n = pg.keys.length;
            float r = Math.min(24 * k, b.width() / n / 2.6f);
            float y = b.centerY() - 8 * k;
            slot = b.width() / n;
            if (panel) {   // a band round the keys and their names, not the whole picture
                RectF band = new RectF(b.left, y - r - 18 * k, b.right, y + r + 30 * k);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(p.panel);
                cv.drawRoundRect(band, 16 * k, 16 * k, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1 * k);
                paint.setColor(p.line);
                cv.drawRoundRect(band, 16 * k, 16 * k, paint);
            }
            for (int i = 0; i < n; i++) {
                float x = b.left + b.width() * (i + 0.5f) / n;
                float from = 0.1f + 0.8f * i / n, to = from + 0.8f / n;
                float lit = t >= from && t < to ? (float) Math.sin(Math.PI * (t - from) / (to - from)) : 0;
                if (i == n - 1 && t >= 1f) lit = 0;
                key(cv, x, y, r, pg.keys[i], pg.keyNames[i], lit, k);
                if (lit > 0.3f) finger(cv, x + 8 * k, y + 10 * k, lit, k);
            }
        }

        /** The viewfinder's scene: the sun's path draws itself with its hours, then the compass and the heights. */
        private void drawSceneCard(Canvas cv, RectF b, float k) {
            scene(cv, b, 0.48f * ease(part(0f, 0.5f)) + 0.1f, 0, 1, part(0.3f, 0.5f) > 0, k);
            float a = part(0.5f, 0.7f);
            if (a > 0) {   // the compass along the top
                paint.setTypeface(Ui.font(getContext(), 600));
                paint.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
                paint.setStyle(Paint.Style.FILL);
                String[] dirs = {"S", "210", "SW", "240", "W"};
                for (int i = 0; i < dirs.length; i++) {
                    float x = b.left + b.width() * (0.2f + 0.6f * i / (dirs.length - 1));   // clear of the corners
                    paint.setColor(Ui.withAlpha(0xFFFFFFFF, Math.round(230 * a)));
                    cv.drawText(dirs[i], x - paint.measureText(dirs[i]) / 2, b.top + 20 * k, paint);
                }
            }
            float h = part(0.7f, 0.9f);
            if (h > 0) {   // the heights down the side
                paint.setTypeface(Ui.font(getContext(), 600));
                paint.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
                paint.setStyle(Paint.Style.FILL);
                float horizon = b.top + b.height() * 0.70f;
                for (int i = 1; i <= 2; i++) {
                    float y = horizon - (horizon - b.top) * 0.36f * i;
                    paint.setColor(Ui.withAlpha(0xFFFFFFFF, Math.round(230 * h)));
                    cv.drawText(i * 10 + "°", b.left + 22 * k, y + 14 * k, paint);
                }
            }
        }

        /** Swipe: sideways the sun walks along its path and the time follows; then up and down tilts. */
        private void drawSwipe(Canvas cv, RectF b, float k) {
            float side = ease(part(0.08f, 0.5f));
            float tiltF = part(0.58f, 0.95f);
            float tilt = (float) Math.sin(Math.PI * tiltF) * b.height() * 0.16f;
            scene(cv, b, lerp(0.2f, 0.75f, side), tilt, 1, true, k);
            if (t < 0.55f) {
                float fx = lerp(b.left + b.width() * 0.3f, b.right - b.width() * 0.3f, side), fy = b.top + b.height() * 0.45f;
                finger(cv, fx, fy, part(0.02f, 0.08f) * (1 - part(0.5f, 0.55f)), k);
                arrow(cv, b.centerX(), b.bottom - 26 * k, true, k);
                tag(cv, clock(14 * 60 + 10, 16 * 60 + 50, side), b.centerX(), b.top + 30 * k, k, true);
            } else {
                float fy = b.top + b.height() * 0.45f - (float) Math.sin(Math.PI * tiltF) * b.height() * 0.16f;
                finger(cv, b.centerX() - b.width() * 0.25f, fy, part(0.55f, 0.6f) * (1 - part(0.95f, 1f)), k);
                arrow(cv, b.right - 28 * k, b.centerY(), false, k);
                tag(cv, "Tilt", b.centerX(), b.top + 30 * k, k, true);
            }
        }

        /** A double-headed arrow, sideways or up and down. */
        private void arrow(Canvas cv, float x, float y, boolean sideways, float k) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * k);
            paint.setColor(0xE6FFFFFF);
            float l = 22 * k, a = 6 * k;
            if (sideways) {
                cv.drawLine(x - l, y, x + l, y, paint);
                cv.drawLine(x - l, y, x - l + a, y - a, paint); cv.drawLine(x - l, y, x - l + a, y + a, paint);
                cv.drawLine(x + l, y, x + l - a, y - a, paint); cv.drawLine(x + l, y, x + l - a, y + a, paint);
            } else {
                cv.drawLine(x, y - l, x, y + l, paint);
                cv.drawLine(x, y - l, x - a, y - l + a, paint); cv.drawLine(x, y - l, x + a, y - l + a, paint);
                cv.drawLine(x, y + l, x - a, y + l - a, paint); cv.drawLine(x, y + l, x + a, y + l - a, paint);
            }
        }

        /** Pinch: two fingers spread, the lens gets longer, the subject and the sun grow in the same corners. */
        private void drawPinch(Canvas cv, RectF b, float k) {
            float f = ease(part(0.12f, 0.75f));
            float zoom = lerp(1f, 2.3f, f);
            scene(cv, b, 0.5f, 0, zoom, false, k);
            float spread = lerp(18 * k, b.width() * 0.26f, f);
            float a = part(0.04f, 0.12f) * (1 - part(0.8f, 0.9f));
            float cx = b.centerX(), cy = b.centerY();
            finger(cv, cx - spread * 0.8f, cy + spread * 0.5f, a, k);
            finger(cv, cx + spread * 0.8f, cy - spread * 0.5f, a, k);
            int mm = Math.round(lerp(24, 85, f) / 5f) * 5;
            if (f >= 0.99f) mm = 85;
            tag(cv, mm + " mm", cx, b.bottom - 30 * k, k, true);
        }

        /**
         * The time: the band (or the Camera layout's Lined up key) over a small scene, and the tuning scale under it;
         * a finger drags the scale until the playhead meets the Lined up pin and the band lights.
         */
        private void drawTime(Canvas cv, RectF b, float k) {
            boolean camera = Ui.viewfinder(getContext()) == Ui.VF_CAMERA;
            float f = ease(part(0.1f, 0.7f));
            RectF pic = new RectF(b.left, b.top + 40 * k, b.right, b.bottom - 56 * k);
            scene(cv, pic, lerp(0.3f, 0.6f, f), 0, 1, false, k);
            boolean at = f > 0.97f;
            // the band (Answer band) or the status line (Camera)
            RectF band = new RectF(b.left, b.top, b.right, b.top + 34 * k);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(camera ? Ui.withAlpha(0xFF0B1220, 220) : at ? p.amber : Ui.withAlpha(p.amber, 150));
            cv.drawRoundRect(band, 12 * k, 12 * k, paint);
            paint.setTypeface(Ui.font(getContext(), 700));
            paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(camera ? 0xFFFFFFFF : 0xFF1A1206);
            String words = camera ? (at ? "● Lined up · 4:12 PM" : clock(15 * 60 + 20, 16 * 60 + 12, f) + " · lined up at 4:12 PM")
                    : "Lined up · Today 4:12 PM" + (at ? "" : " · tap");
            cv.drawText(words, band.left + 14 * k, band.centerY() + paint.getTextSize() * 0.35f, paint);
            // the scale: ticks, a Lined up pin, the playhead
            float sy = b.bottom - 26 * k;
            float x0 = b.left + 16 * k, x1 = b.right - 16 * k;
            float pinX = lerp(x0, x1, 0.72f);
            float shift = lerp(0, pinX - b.centerX(), f);   // the scale moves under a fixed playhead
            paint.setStrokeWidth(1.2f * k);
            paint.setColor(p.muted);
            for (int i = -10; i <= 30; i++) {
                float x = x0 + (x1 - x0) * i / 20f - shift;
                if (x < b.left + 6 * k || x > b.right - 6 * k) continue;
                float hh = i % 5 == 0 ? 12 * k : 6 * k;
                cv.drawLine(x, sy - hh / 2, x, sy + hh / 2, paint);
            }
            float px = pinX - shift;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.amber);
            cv.drawCircle(px, sy - 14 * k, 5 * k, paint);
            paint.setStrokeWidth(2 * k);
            cv.drawLine(px, sy - 10 * k, px, sy + 8 * k, paint);
            paint.setColor(p.text);
            paint.setStrokeWidth(2.5f * k);
            cv.drawLine(b.centerX(), sy - 16 * k, b.centerX(), sy + 14 * k, paint);   // the playhead
            if (camera) {   // the orange key between the keys, filled at the moment
                float kx = b.right - 30 * k, ky = pic.bottom - 26 * k;
                paint.setStyle(at ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(3 * k);
                paint.setColor(p.amber);
                cv.drawCircle(kx, ky, 15 * k, paint);
            }
            finger(cv, b.centerX() + 30 * k - shift * 0.1f, sy + 4 * k, part(0.05f, 0.1f) * (1 - part(0.7f, 0.78f)), k);
            if (at) {   // then a tap on the band
                float tap = part(0.82f, 0.95f);
                finger(cv, band.right - 40 * k, band.centerY(), (float) Math.sin(Math.PI * tap), k);
            }
        }

        /** Where it sits: the sun goes to the horizon, behind the middle, just on top, the key lit for each. */
        private void drawSits(Canvas cv, RectF b, float k) {
            RectF pic = new RectF(b.left, b.top, b.right, b.bottom - 50 * k);
            float horizon = pic.top + pic.height() * 0.70f;
            float th = pic.height() * 0.30f, ttop = horizon - th;
            float[] ys = {horizon - 4 * k, horizon - th / 2, ttop - th * 0.25f - 12 * k};
            int stage = t < 0.36f ? 0 : t < 0.7f ? 1 : 2;
            float from = stage == 0 ? pic.top + 20 * k : ys[stage - 1];
            float local = ease(part(stage == 0 ? 0.04f : stage == 1 ? 0.36f : 0.7f, stage == 0 ? 0.28f : stage == 1 ? 0.6f : 0.94f));
            float sy = lerp(from, ys[stage], local);
            // the planning sky and the tower, as Build the shot draws them
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(pic, 16 * k, 16 * k, android.graphics.Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, pic.top, 0, horizon, 0xFF4A5A78, 0xFF8DA0BC, Shader.TileMode.CLAMP));
            cv.drawRect(pic.left, pic.top, pic.right, horizon, paint);
            paint.setShader(null);
            paint.setColor(0xFF1A2130);
            cv.drawRect(pic.left, horizon, pic.right, pic.bottom, paint);
            float cx = pic.centerX(), tw = pic.width() * 0.07f;
            float sr = 10 * k;
            boolean behind = stage == 1;
            if (behind) {
                paint.setColor(p.amber);
                cv.drawCircle(cx, sy, sr, paint);
            }
            paint.setColor(0xFF0C0F15);
            cv.drawRect(cx - tw / 2, ttop, cx + tw / 2, horizon + 2 * k, paint);
            cv.drawRect(cx - tw * 0.18f, ttop - th * 0.25f, cx + tw * 0.18f, ttop, paint);
            if (!behind) {
                paint.setColor(0x55FFC067);
                cv.drawCircle(cx, sy, sr * 2.2f, paint);
                paint.setColor(p.amber);
                cv.drawCircle(cx, sy, sr, paint);
            } else {   // behind the subject: the ring shows where it is
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setColor(p.amber);
                cv.drawCircle(cx, sy, sr * 1.8f, paint);
            }
            cv.restore();
            corners(cv, pic, k);
            // the three keys in one track, the chosen one raised
            String[] names = {"Horizon", "Middle", "On top"};
            RectF track = new RectF(b.left + 10 * k, b.bottom - 40 * k, b.right - 10 * k, b.bottom - 4 * k);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.raised);
            cv.drawRoundRect(track, 12 * k, 12 * k, paint);
            float w3 = track.width() / 3f;
            RectF thumb = new RectF(track.left + w3 * stage + 3 * k, track.top + 3 * k, track.left + w3 * (stage + 1) - 3 * k, track.bottom - 3 * k);
            paint.setColor(p.panel);
            cv.drawRoundRect(thumb, 9 * k, 9 * k, paint);
            paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            for (int i = 0; i < 3; i++) {
                paint.setTypeface(Ui.font(getContext(), i == stage ? 700 : 500));
                paint.setColor(i == stage ? p.text : p.muted);
                float tw2 = paint.measureText(names[i]);
                cv.drawText(names[i], track.left + w3 * (i + 0.5f) - tw2 / 2, track.centerY() + paint.getTextSize() * 0.35f, paint);
            }
            float tap = local < 0.25f ? (float) Math.sin(Math.PI * local / 0.25f) : 0;
            finger(cv, track.left + w3 * (stage + 0.5f) + 10 * k, track.centerY() + 6 * k, tap, k);
        }

        /**
         * Build the shot (3.11, owner: "viewfinder"): the picture as the camera sees it; the sun comes down and sits just
         * on top of the subject, and its ring shows it is placed.
         */
        private void drawViewfinder(Canvas cv, float cx, float cy, float half, float k) {
            float l = cx - half, r = cx + half, t0 = cy - half * 0.8f, b = cy + half * 0.8f;
            android.graphics.RectF box = new android.graphics.RectF(l, t0, r, b);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(box, 16 * k, 16 * k, android.graphics.Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            float horizon = t0 + (b - t0) * 0.72f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, t0, 0, horizon, 0xFF2B3550, 0xFFC98A5A, Shader.TileMode.CLAMP));
            cv.drawRect(l, t0, r, horizon, paint);
            paint.setShader(null);
            paint.setColor(0xFF141820);
            cv.drawRect(l, horizon, r, b, paint);
            // the subject: a tower standing on the horizon, a lit edge
            float tw = (r - l) * 0.11f, th = (b - t0) * 0.40f, tx = cx - tw / 2, ttop = horizon - th;
            paint.setColor(0xFF0C0F15);
            cv.drawRect(tx, ttop, tx + tw, horizon + 2 * k, paint);
            paint.setColor(0x55FFD9A6);
            cv.drawRect(tx + tw - 1.5f * k, ttop, tx + tw, horizon, paint);
            // the sun comes down from the top left to just on top of the subject
            float f = ease(part(0.1f, 0.75f));
            float sr = 11 * k;
            float sx = lerp(l + (r - l) * 0.2f, cx, f), sy = lerp(t0 + (b - t0) * 0.18f, ttop - sr, f);
            paint.setColor(0x55FFC067);
            cv.drawCircle(sx, sy, sr * 2.2f, paint);
            paint.setColor(p.amber);
            cv.drawCircle(sx, sy, sr, paint);
            // 3.20: a finger takes the sun there (it is dragged by its ring)
            finger(cv, sx + 7 * k, sy + 12 * k, part(0.04f, 0.12f) * (1f - part(0.72f, 0.8f)), k);
            float ring = part(0.75f, 0.95f);
            if (ring > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * k);
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * k, 4 * k}, 0));
                paint.setColor(Ui.withAlpha(p.amber, Math.round(255 * ring)));
                cv.drawCircle(sx, sy, sr * 1.8f, paint);
                paint.setPathEffect(null);
            }
            cv.restore();
            // the frame's corners: what the lens takes in
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * k);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setColor(0xFFFFFFFF);
            float in = 14 * k, c = 22 * k;
            float fl = l + in, fr = r - in, ft = t0 + in, fb = b - in;
            cv.drawLine(fl, ft, fl + c, ft, paint); cv.drawLine(fl, ft, fl, ft + c, paint);
            cv.drawLine(fr, ft, fr - c, ft, paint); cv.drawLine(fr, ft, fr, ft + c, paint);
            cv.drawLine(fl, fb, fl + c, fb, paint); cv.drawLine(fl, fb, fl, fb - c, paint);
            cv.drawLine(fr, fb, fr - c, fb, paint); cv.drawLine(fr, fb, fr, fb - c, paint);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        private float ease(float f) {   // CrossPaths arrival: fast start, long soft landing
            float u = 1f - f;
            return 1f - u * u * u;
        }

        /**
         * The sun-or-moon card: the Home switch, the body's icon and a main key. A finger presses Moon and the
         * moon's colours open in a circle from it, the way the app changes.
         */
        private void drawSwitch(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float k = Ui.dp(getContext(), 1);
            float cx = w / 2f, cy = h / 2f;
            float half = Math.min(w / 2f, Math.min(w, h * 1.1f) / 2f);
            android.graphics.RectF box = new android.graphics.RectF(cx - half, cy - half * 0.8f, cx + half, cy + half * 0.8f);
            float trackY = box.top + 18 * k, trackH = 40 * k;
            android.graphics.RectF track = new android.graphics.RectF(box.left + 18 * k, trackY, box.right - 18 * k, trackY + trackH);
            float tapX = track.centerX() + track.width() / 4f, tapY = track.centerY();
            float open = ease(part(0.42f, 0.86f));
            float far = (float) Math.hypot(Math.max(tapX - box.left, box.right - tapX), box.bottom - tapY);

            drawState(cv, box, track, false, k);
            if (open > 0f) {
                cv.save();
                android.graphics.Path hole = new android.graphics.Path();
                hole.addCircle(tapX, tapY, far * open, android.graphics.Path.Direction.CW);
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(box, 16 * k, 16 * k, android.graphics.Path.Direction.CW);
                hole.op(clip, android.graphics.Path.Op.INTERSECT);
                cv.clipPath(hole);
                drawState(cv, box, track, true, k);
                cv.restore();
            }
            // the finger: a soft dot that presses and lets go
            float press = part(0.26f, 0.42f), lift = part(0.48f, 0.62f);
            if (press > 0f && lift < 1f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Ui.withAlpha(p.text, (int) (60 * (1f - lift))));
                cv.drawCircle(tapX, tapY, (14 + 8 * press) * k, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * k);
                paint.setColor(Ui.withAlpha(p.text, (int) (120 * (1f - lift))));
                cv.drawCircle(tapX, tapY, (14 + 16 * lift) * k, paint);
            }
        }

        /** One state of the card, the sun's or the moon's, in that body's accent. */
        private void drawState(Canvas cv, android.graphics.RectF box, android.graphics.RectF track, boolean moon, float k) {
            int accent = Ui.accentColor(Ui.resolveAccent(Ui.accentIndex(getContext()), moon), p.night);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.panel);
            cv.drawRoundRect(box, 16 * k, 16 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * k);
            paint.setColor(p.line);
            cv.drawRoundRect(box, 16 * k, 16 * k, paint);
            // the switch: a raised track, the chosen half on a lighter thumb, an icon and a word in each
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.raised);
            cv.drawRoundRect(track, 12 * k, 12 * k, paint);
            float mid = track.centerX(), in = 3 * k;
            android.graphics.RectF thumb = moon
                    ? new android.graphics.RectF(mid, track.top + in, track.right - in, track.bottom - in)
                    : new android.graphics.RectF(track.left + in, track.top + in, mid, track.bottom - in);
            paint.setColor(p.panel);
            cv.drawRoundRect(thumb, 9 * k, 9 * k, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(p.line);
            cv.drawRoundRect(thumb, 9 * k, 9 * k, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(13.5f * getResources().getDisplayMetrics().scaledDensity);
            for (int i = 0; i < 2; i++) {
                boolean on = (i == 1) == moon;
                String word = i == 0 ? "Sun" : "Moon";
                paint.setTypeface(Ui.font(getContext(), on ? 700 : 500));
                float tw = paint.measureText(word), icon = 18 * k, gap = 7 * k;
                float x0 = (i == 0 ? (track.left + mid) / 2f : (mid + track.right) / 2f) - (icon + gap + tw) / 2f;
                android.graphics.drawable.Drawable d = Ui.icon(getContext(), i == 0 ? Ui.Icon.SUN : Ui.Icon.MOON,
                        on ? accent : p.muted);
                d.setBounds((int) x0, (int) (track.centerY() - icon / 2f), (int) (x0 + icon), (int) (track.centerY() + icon / 2f));
                d.draw(cv);
                paint.setColor(on ? p.text : p.muted);
                cv.drawText(word, x0 + icon + gap, track.centerY() + paint.getTextSize() * 0.35f, paint);
            }
            // the body, large, on a soft disc of its colour
            float gy = (track.bottom + box.bottom - 58 * k) / 2f, r = Math.min(46 * k, (box.bottom - 58 * k - track.bottom) / 2.4f);
            paint.setColor(Ui.withAlpha(accent, 34));
            cv.drawCircle(box.centerX(), gy, r * 1.25f, paint);
            android.graphics.drawable.Drawable big = Ui.icon(getContext(), moon ? Ui.Icon.MOON : Ui.Icon.SUN, accent);
            big.setBounds((int) (box.centerX() - r * 0.8f), (int) (gy - r * 0.8f), (int) (box.centerX() + r * 0.8f), (int) (gy + r * 0.8f));
            big.draw(cv);
            // the main key, in the accent with its ink
            android.graphics.RectF key = new android.graphics.RectF(box.left + 18 * k, box.bottom - 58 * k, box.right - 18 * k, box.bottom - 18 * k);
            paint.setColor(accent);
            cv.drawRoundRect(key, 12 * k, 12 * k, paint);
            int ink = p.night ? (moon ? 0xFF1E1147 : 0xFF2A1B02) : 0xFFFFFFFF;
            if (Ui.accentIndex(getContext()) != Ui.ACCENT_FOLLOW) ink = p.ink;
            paint.setColor(ink);
            paint.setTypeface(Ui.font(getContext(), 700));
            String label = "Next";
            cv.drawText(label, key.centerX() - paint.measureText(label) / 2f, key.centerY() + paint.getTextSize() * 0.35f, paint);
        }
    }
}
