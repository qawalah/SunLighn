package dev.qawalah.sunlighn;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Search: find a place by name, or type coordinates, and set it as the Subject or as You.
 * One search per press of the search key (the map service asks for that), never while typing.
 */
final class SearchScreen extends FrameLayout {
    interface Host {
        void closeSearch();
        void placePicked(Places.Place place, boolean asYou);
        /** The middle of the map: results near it come first and say how far they are. Null if unknown. */
        double[] searchNear();
    }

    private final Ui.Palette p;
    private final Host host;
    private final EditText field;
    private final LinearLayout content, list, head;
    private boolean asYou;
    private final boolean farEnd;     // Line up a street: the point is the street's far end, not a subject (3.25, B-10)
    private int generation;
    private int insetTop, insetBottom;

    SearchScreen(Context c, Ui.Palette p, Host host, boolean asYou) { this(c, p, host, asYou, false); }

    SearchScreen(Context c, Ui.Palette p, Host host, boolean asYou, boolean farEnd) {
        super(c);
        this.p = p;
        this.host = host;
        this.asYou = asYou;
        this.farEnd = farEnd;
        setBackgroundColor(p.bg);
        setClickable(true);

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.shape(c, p.panel, p.line, 16, false));
        Ui.IconButton back = new Ui.IconButton(c, p, Ui.Icon.BACK, "Back to the map").bare();
        back.setOnClickListener(v -> host.closeSearch());
        bar.addView(back);
        field = new EditText(c);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setHint("Search for a place");
        field.setHintTextColor(p.muted);
        field.setTextColor(p.text);
        field.setTextSize(16);
        field.setTypeface(Ui.font(c, 500));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setContentDescription("Search for a place");
        field.setOnEditorActionListener((v, action, e) -> {
            search();
            return true;
        });
        bar.addView(field, new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f));
        Ui.IconButton go = new Ui.IconButton(c, p, Ui.Icon.SEARCH, "Search").bare();
        go.setOnClickListener(v -> search());
        bar.addView(go);
        content.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        head = new LinearLayout(c);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(c, 12);
        content.addView(head, hl);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildHead();
        note("Type the name of a place and press search. Coordinates work too, like 51.5007, -0.1246.");
        applyPadding();
        field.requestFocus();
        field.post(() -> {
            InputMethodManager im = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyPadding();
    }

    private void applyPadding() {
        int s = Ui.dp(getContext(), 12);
        content.setPadding(s, insetTop + s, s, insetBottom);
    }

    private void buildHead() {
        head.removeAllViews();
        // The search sets the point the step asks for; say which, so nobody has to choose.
        head.addView(Ui.text(getContext(), asYou ? "The place you pick is where you stand."
                : farEnd ? "The place you pick is the far end of the street." : "The place you pick is your subject.", 14, 500, p.muted), new LinearLayout.LayoutParams(-1, -2));
    }

    private void note(String s) {
        list.removeAllViews();
        TextView t = Ui.text(getContext(), s, 15, 400, p.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), 20);
        l.setMarginStart(Ui.dp(getContext(), 4));
        list.addView(t, l);
    }

    void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void search() {
        String q = field.getText().toString().trim();
        if (q.length() < 2) {
            note("Type at least two letters.");
            return;
        }
        hideKeyboard();
        final int g = ++generation;
        note("Searching…");
        Places.search(q, host.searchNear(), places -> {
            if (g != generation || getParent() == null) return;
            show(places);
        });
    }

    /** For tests and for the host: the results as they came. */
    void show(List<Places.Place> places) {
        Context c = getContext();
        if (places == null) {
            note("No answer from the map search. Check the connection and press search again, "
                    + "or type coordinates like 51.5007, -0.1246.");
            return;
        }
        if (places.isEmpty()) {
            note("Nothing found by that name. Check the spelling, add the city, or type coordinates like 51.5007, -0.1246.");
            return;
        }
        list.removeAllViews();
        for (Places.Place pl : places) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            row.setBackground(Ui.shape(c, p.panel, p.line, 14, false));
            row.addView(Ui.text(c, pl.name, 16, 700, p.text));
            double[] near = host.searchNear();
            // How far from the map: seven places called "London Eye" are told apart at a glance (Ali, 2026-11-15)
            String away = near == null || pl.detail.equals("Coordinates") ? ""
                    : Fmt.distance(Astro.distance(near[0], near[1], pl.lat, pl.lon)) + " away";
            String detail = away.isEmpty() ? pl.detail : pl.detail.isEmpty() ? away : away + " · " + pl.detail;
            if (!detail.isEmpty()) {
                TextView d = Ui.text(c, detail, 13, 400, p.muted);
                d.setMaxLines(2);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
                dl.topMargin = Ui.dp(c, 3);
                row.addView(d, dl);
            }
            row.setClickable(true);
            Ui.pressable(row);
            row.setContentDescription(pl.name + ", set as " + (asYou ? "You" : farEnd ? "Far end" : "Subject"));
            row.setOnClickListener(v -> host.placePicked(pl, asYou));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.topMargin = Ui.dp(c, 8);
            list.addView(row, l);
        }
        TextView credit = Ui.text(c, "Search by OpenStreetMap", 11.5f, 400, p.muted);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = Ui.dp(c, 14);
        cl.bottomMargin = Ui.dp(c, 20);
        cl.setMarginStart(Ui.dp(c, 4));
        list.addView(credit, cl);
    }
}
