package dev.qawalah.sunlighn;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * The list a dropdown opens: a short sheet from the bottom, one row per choice, a tick on the current one.
 * A tap picks and closes; a tap outside or Back closes with nothing changed.
 */
final class PickSheet extends FrameLayout {
    private PickSheet(Context c) { super(c); }

    static PickSheet open(Activity a, Ui.Palette p, String title, String[] options, int[] swatches, int selected,
                          IntConsumer onPick) {
        PickSheet s = new PickSheet(a);
        s.setTag("pick-sheet");
        s.setBackgroundColor(0x99000000);
        s.setClickable(true);
        s.setOnClickListener(v -> s.close());
        Context c = a;
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.shape(c, p.panel, p.line, 22, true));
        panel.setClickable(true);
        int pad = Ui.dp(c, 16);
        int bottom = 0;
        View content = a.findViewById(android.R.id.content);
        if (content != null && content.getRootWindowInsets() != null) {
            bottom = content.getRootWindowInsets().getSystemWindowInsetBottom();
        }
        panel.setPadding(pad, pad, pad, pad + bottom);
        panel.addView(Ui.text(c, title, 17, 700, p.text));
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 48));
            row.setClickable(true);
            Ui.pressable(row);
            row.setContentDescription(options[i] + (i == selected ? ", chosen" : ""));
            if (swatches != null) {
                View dot = new View(c);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(swatches[i]);
                dot.setBackground(g);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
                dl.setMarginEnd(Ui.dp(c, 12));
                row.addView(dot, dl);
            }
            row.addView(Ui.text(c, options[i], 15.5f, i == selected ? 700 : 500, p.text),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            if (i == selected) {
                ImageView tick = new ImageView(c);
                android.graphics.drawable.Drawable d = c.getDrawable(R.drawable.ic_check).mutate();
                d.setTint(p.accent);
                tick.setImageDrawable(d);
                row.addView(tick, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            }
            row.setOnClickListener(v -> {
                s.close();
                if (idx != selected) onPick.accept(idx);
            });
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = Ui.dp(c, i == 0 ? 8 : 0);
            panel.addView(row, rl);
        }
        s.addView(panel, new LayoutParams(-1, -2, Gravity.BOTTOM));
        ((ViewGroup) a.findViewById(android.R.id.content)).addView(s, new LayoutParams(-1, -1));
        return s;
    }

    void close() {
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
    }

    /** Closes the list on top, if one is open. True if it closed one (Back stops there). */
    static boolean closeTop(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return false;
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View v = content.getChildAt(i);
            if (v instanceof PickSheet) {
                ((PickSheet) v).close();
                return true;
            }
        }
        return false;
    }

    static boolean isOpen(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return false;
        for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i) instanceof PickSheet) return true;
        return false;
    }
}
