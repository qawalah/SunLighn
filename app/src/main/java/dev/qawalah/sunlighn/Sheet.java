package dev.qawalah.sunlighn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The sheet that rises from the bottom of the map. It has a grab bar, a head that always shows (the answer in
 * one line) and a body that scrolls. It rests at three heights: peek (the head only, the map is free), half and
 * full. Drag the head up or down, fling it, or tap the grab bar to step between heights.
 * A sheet with little to say (the first steps) is not pulled: it is as tall as its words.
 */
final class Sheet extends LinearLayout {
    static final int PEEK = 0, HALF = 1, FULL = 2;

    interface Listener { void onSheetMoved(); }

    private final Ui.Palette p;
    private final View grab;
    final LinearLayout head, body;
    private final ScrollView scroll;
    private final float k;
    private final int slop;
    private Listener listener;

    private boolean pullable;
    private int stop = HALF;
    private int insetTop, insetBottom;
    private ValueAnimator anim;

    private float downY, startH;
    private boolean dragging;
    private VelocityTracker velocity;

    Sheet(Context c, Ui.Palette p) {
        super(c);
        this.p = p;
        k = Ui.dp(c, 1);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setBackground(Ui.shape(c, p.panel, p.line, 22, true));
        setClickable(true); // taps on the sheet must not reach the map

        FrameLayout grabZone = new FrameLayout(c);
        grab = new View(c);
        grab.setBackground(Ui.shape(c, p.line, 0, 3, false));
        grab.setVisibility(INVISIBLE);   // shown once the sheet can be pulled
        grabZone.addView(grab, new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5), Gravity.CENTER));
        grabZone.setContentDescription("Pull the sheet up or down");
        grabZone.setOnClickListener(v -> {
            if (pullable) setStop(stop == PEEK ? HALF : stop == HALF ? FULL : PEEK, true);
        });
        addView(grabZone, new LayoutParams(-1, Ui.dp(c, 24)));

        head = new LinearLayout(c);
        head.setOrientation(VERTICAL);
        head.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 12));
        addView(head, new LayoutParams(-1, -2));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        scroll.addView(body);
        addView(scroll, new LayoutParams(-1, 0, 1f));
        applyPadding();
    }

    void setListener(Listener l) { listener = l; }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyPadding();
        if (pullable) post(() -> setStop(stop, false));
    }

    private void applyPadding() {
        body.setPadding(Ui.dp(getContext(), 16), 0, Ui.dp(getContext(), 16), insetBottom + Ui.dp(getContext(), 16));
    }

    /** A sheet that can be pulled has the three heights; one that cannot is as tall as its words. */
    void setPullable(boolean on) {
        if (pullable == on) return;
        pullable = on;
        grab.setVisibility(on ? VISIBLE : INVISIBLE);
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) getLayoutParams();
        LayoutParams sl = (LayoutParams) scroll.getLayoutParams();
        if (on) {
            sl.height = 0;
            sl.weight = 1f;
            scroll.setLayoutParams(sl);
            post(() -> setStop(stop, false));
        } else {
            if (anim != null) anim.cancel();
            sl.height = -2;
            sl.weight = 0;
            scroll.setLayoutParams(sl);
            l.height = -2;
            setLayoutParams(l);
        }
    }

    int stop() { return stop; }

    private int screen() {
        View parent = (View) getParent();
        return parent == null || parent.getHeight() == 0 ? getResources().getDisplayMetrics().heightPixels : parent.getHeight();
    }

    private int heightOf(int s) {
        int peek = Math.round(24 * k) + head.getHeight() + insetBottom;
        if (head.getHeight() == 0) peek = Math.round(120 * k) + insetBottom;
        if (s == PEEK) return peek;
        if (s == HALF) return Math.max(peek, Math.round(screen() * 0.47f));
        return Math.max(peek, screen() - insetTop - Math.round(84 * k));
    }

    void setStop(int s, boolean animate) {
        stop = s;
        if (!pullable || getLayoutParams() == null) return;
        int from = getLayoutParams().height > 0 ? getLayoutParams().height : getHeight(), to = heightOf(s);
        if (anim != null) anim.cancel();
        if (!animate || from <= 0 || !ValueAnimator.areAnimatorsEnabled()) {
            setH(to);
            return;
        }
        anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(260);
        anim.setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f));   // quick out, soft landing
        anim.addUpdateListener(a -> setH((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setH(int h) {
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) getLayoutParams();
        if (l.height == h) return;
        l.height = h;
        setLayoutParams(l);
        if (listener != null) listener.onSheetMoved();
    }

    /** After the head's words changed: the peek height is the head's height, so settle again. */
    void headChanged() {
        if (pullable) post(() -> setStop(stop, false));
    }

    void scrollToTop() { scroll.scrollTo(0, 0); }

    // ---- pulling: a drag that starts on the grab bar or the head moves the sheet ----

    private boolean inHead(MotionEvent e) {
        return e.getY() < head.getBottom();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!pullable) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                startH = getHeight();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // From the head, or from the body when it is at its top and the pull is downwards.
                float dy = e.getRawY() - downY;
                boolean fromBody = !inHead(e) && scroll.getScrollY() == 0 && dy > slop && stop != PEEK;
                if ((inHead(e) && Math.abs(dy) > slop) || fromBody) {
                    dragging = true;
                    if (anim != null) anim.cancel();
                    downY = e.getRawY();
                    startH = getHeight();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!pullable) return super.onTouchEvent(e);
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getRawY() - downY) > slop) {
                    dragging = true;
                    downY = e.getRawY();
                    startH = getHeight();
                }
                if (dragging) {
                    int h = Math.round(startH - (e.getRawY() - downY));
                    setH(Math.max(heightOf(PEEK), Math.min(heightOf(FULL), h)));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000);
                float v = velocity.getYVelocity();
                velocity.recycle();
                velocity = null;
                if (dragging) {
                    dragging = false;
                    int h = getHeight(), best = PEEK;
                    if (Math.abs(v) > 900 * k) {                       // a fling goes one stop in its direction
                        best = v < 0 ? (h > heightOf(HALF) ? FULL : HALF) : (h < heightOf(HALF) ? PEEK : HALF);
                    } else {
                        for (int s = PEEK; s <= FULL; s++) {
                            if (Math.abs(heightOf(s) - h) < Math.abs(heightOf(best) - h)) best = s;
                        }
                    }
                    setStop(best, true);
                }
                return true;
        }
        return true;
    }
}
