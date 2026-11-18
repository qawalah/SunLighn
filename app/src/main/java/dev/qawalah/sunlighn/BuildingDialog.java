package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Asks for a building the map lacks (3.18): its height, width, depth and turn. */
final class BuildingDialog {
    private BuildingDialog() {}

    interface Done { void add(double height, double width, double depth, double turn); }

    static AlertDialog show(Context c, double lastHeight, Done done) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int s = Ui.dp(c, 20);
        box.setPadding(s, Ui.dp(c, 8), s, 0);
        EditText h = field(c, "Height in metres", lastHeight > 0 ? lastHeight : 12);
        EditText w = field(c, "Width in metres (across)", 20);
        EditText d = field(c, "Depth in metres (along)", 20);
        EditText t = field(c, "Turn in degrees, clockwise from north", 0);
        for (EditText e : new EditText[]{h, w, d, t}) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.topMargin = Ui.dp(c, 8);
            box.addView(e, l);
        }
        return new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Add a building here")
                .setMessage("A box where you held the map. It counts like a mapped building (the times, the viewfinder, the "
                        + "shadows) and stays on this phone.")
                .setView(box)
                .setPositiveButton("Add", (dlg, which) -> done.add(num(h, 12, 2, 800), num(w, 20, 3, 500), num(d, 20, 3, 500),
                        num(t, 0, -360, 360)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static EditText field(Context c, String hint, double value) {
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value));
        return e;
    }

    private static double num(EditText e, double fallback, double lo, double hi) {
        try {
            return Math.max(lo, Math.min(hi, Double.parseDouble(e.getText().toString().trim())));
        } catch (NumberFormatException x) {
            return fallback;
        }
    }
}
