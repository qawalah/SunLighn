package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Asks what the subject is and how tall, so it can be drawn to scale. Used by the viewfinder and by Plan a frame. */
final class HeightDialog {
    private HeightDialog() {}

    interface Done { void set(double metres, int kind); }

    /** {@code required}: the caller cannot do without a height, so there is no "No height" key. */
    static AlertDialog show(Context c, Ui.Palette p, double height, int startKind, boolean required, Done done) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int s = Ui.dp(c, 20);
        box.setPadding(s, Ui.dp(c, 8), s, 0);
        EditText metres = new EditText(c);
        metres.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        metres.setSingleLine(true);
        metres.setHint("Height in metres");
        if (height > 0) metres.setText(trim(height));
        final int[] kind = {startKind};
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout kinds = new LinearLayout(c);
        hs.addView(kinds);
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            kinds.removeAllViews();
            for (int i = 0; i < FrameMath.KINDS.length; i++) {
                final int idx = i;
                TextView chip = Ui.chip(c, p, FrameMath.KINDS[i], kind[0] == i);
                chip.setOnClickListener(v -> {
                    kind[0] = idx;
                    metres.setText(trim(FrameMath.KIND_HEIGHTS[idx]));   // a usual height, to correct if you know better
                    redraw[0].run();
                });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) l.setMarginStart(Ui.dp(c, 8));
                kinds.addView(chip, l);
            }
        };
        redraw[0].run();
        box.addView(hs);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = Ui.dp(c, 12);
        box.addView(metres, ml);
        AlertDialog.Builder b = new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Subject height")
                .setMessage("What it is and how tall, so it is drawn to scale. Pick a kind for a usual height, or type the real one.")
                .setView(box)
                .setPositiveButton("Set", (d, w) -> {
                    double h = 0;
                    try {
                        h = Double.parseDouble(metres.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    done.set(Math.max(0, Math.min(9000, h)), kind[0]);
                })
                .setNegativeButton("Cancel", null);
        if (height > 0 && !required) b.setNeutralButton("No height", (d, w) -> done.set(0, kind[0]));
        return b.show();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
