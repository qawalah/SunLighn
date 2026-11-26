package dev.qawalah.sunlighn;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * People (3.37): a place to manage who you are and who you know: Me (handle, name, bio, the numbers), Friends (requests,
 * friends, following, followers, blocked) and Find (search by handle, the guides of people you follow, cities and their
 * guides, with likes). It needs the server and says so; the last answer shows, marked, when there is no network.
 */
final class PeopleScreen extends FrameLayout {
    interface Host {
        void closePeople();
        void say(String words);
        /** Open a guide's shot in the app (its link code), offering to save it. */
        void openShot(String code);
    }

    private final Ui.Palette p;
    private final Host host;
    private final android.content.SharedPreferences prefs;
    private final ScrollView scroll;
    private final LinearLayout body;
    private int insetTop, insetBottom;
    private int tab;
    private int generation;          // an answer that comes after the tab changed is ignored
    private String query = "";       // Find: the last search
    private String cityOpen;         // Find: the city whose guides show, by slug
    private String cityName;

    PeopleScreen(Context c, Ui.Palette p, Host host) {
        super(c);
        this.p = p;
        this.host = host;
        prefs = c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
        setBackgroundColor(p.bg);
        setClickable(true);                         // the screen underneath must not get taps
        tab = Math.max(0, Math.min(2, prefs.getInt("people_tab", 0)));
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        addView(scroll, new LayoutParams(-1, -1));
        applyPadding();
        render();
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        applyPadding();
    }

    private void applyPadding() {
        int s = Ui.dp(getContext(), 16);
        body.setPadding(s, insetTop + Ui.dp(getContext(), 12), s, insetBottom + Ui.dp(getContext(), 24));
    }

    @Override protected void onDetachedFromWindow() {
        generation++;
        super.onDetachedFromWindow();
    }

    // ---- the frame: back, title, tabs ----

    private void render() {
        Context c = getContext();
        generation++;
        body.removeAllViews();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Ui.IconButton back = new Ui.IconButton(c, p, Ui.Icon.BACK, "Back");
        back.setOnClickListener(v -> host.closePeople());
        head.addView(back);
        TextView title = Ui.text(c, "People", 24, true, p.text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        head.addView(title, tl);
        body.addView(head);
        body.addView(Ui.tabs(c, p, new String[]{"Me", "Friends", "Find"}, tab, t -> {
            tab = t;
            prefs.edit().putInt("people_tab", tab).apply();
            render();
        }), new LinearLayout.LayoutParams(-2, -2));
        if (!Social.signedIn(c)) {
            add(Ui.note(c, p, Ui.Icon.INFO, "Make an account in Settings, Sharing online."), 16);
            add(Ui.note(c, p, Ui.Icon.INFO, "People needs the server. The rest of the app never does."), 8);
            return;
        }
        if (tab == 0) renderMe();
        else if (tab == 1) renderFriends();
        else renderFind();
    }

    private void add(View v, int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = Ui.dp(getContext(), topDp);
        body.addView(v, l);
    }

    private void heading(String words) { add(Ui.text(getContext(), words, 16, 700, p.text), 22); }

    private void loading() { add(Ui.text(getContext(), "Loading…", 14, false, p.muted), 16); }

    /** Says why an answer is old or missing; true when there is something to show. */
    private boolean status(String error, Object json, boolean cached) {
        Context c = getContext();
        if (error != null && json == null) {
            add(Ui.note(c, p, Ui.Icon.WARNING, Social.NEEDS_NETWORK.equals(error) ? "Needs a network." : error), 16);
            return false;
        }
        if (cached) add(Ui.note(c, p, Ui.Icon.CLOCK, "Not updated: needs a network."), 12);
        else if (error != null) add(Ui.note(c, p, Ui.Icon.WARNING, error), 12);
        return json != null;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView key(String label, String description, Runnable go) {
        TextView k = Ui.smallButton(getContext(), p, label);
        k.setSingleLine(true);
        k.setMinHeight(Ui.dp(getContext(), 40));
        k.setContentDescription(description);
        k.setOnClickListener(v -> go.run());
        return k;
    }

    private LinearLayout.LayoutParams keyParams() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.setMarginStart(Ui.dp(getContext(), 8));
        return l;
    }

    /** What a change answered: the words on the bar, then the tab again from the server. */
    private Social.Answer then(String done) {
        return (error, json, cached) -> {
            if (error != null) host.say(Social.NEEDS_NETWORK.equals(error) ? "Needs a network." : error);
            else if (done != null) host.say(done);
            render();
        };
    }

    // ---- Me ----

    private void renderMe() {
        Context c = getContext();
        loading();
        final int gen = generation;
        Social.me(c, (error, json, cached) -> {
            if (gen != generation) return;
            body.removeViewAt(body.getChildCount() - 1);
            if (!status(error, json, cached)) return;
            JSONObject me = (JSONObject) json;
            String handle = me.optString("handle");
            add(Ui.text(c, me.optString("name"), 22, true, p.text), 16);
            add(Ui.text(c, "@" + handle, 15, 500, p.muted), 2);
            String bio = me.optString("bio");
            add(Ui.text(c, bio.isEmpty() ? "No bio yet." : bio, 15, false, bio.isEmpty() ? p.muted : p.text), 10);
            LinearLayout keys = row();
            keys.addView(key("Edit profile", "Edit your profile", () -> editProfile(me)));
            add(keys, 12);
            final int gen2 = generation;
            Social.profile(c, handle, (e2, j2, c2) -> {
                if (gen2 != generation || j2 == null) return;
                JSONObject st = ((JSONObject) j2).optJSONObject("stats");
                if (st == null) return;
                heading("Your numbers");
                add(Ui.text(c, plural(st.optInt("guides"), "guide") + " · " + (st.optInt("cities") == 1 ? "1 city" : st.optInt("cities") + " cities")
                        + " · " + plural(st.optInt("likes"), "like"), 15, false, p.text), 8);
                add(Ui.text(c, plural(st.optInt("followers"), "follower") + " · " + st.optInt("following") + " following · "
                        + plural(st.optInt("friends"), "friend"), 15, false, p.text), 4);
                add(Ui.note(c, p, Ui.Icon.INFO, "Only public guides count in your numbers. Friends-only and link-only guides do not."), 14);
            });
        });
    }

    private static String plural(int n, String one) { return n + " " + (n == 1 ? one : one + "s"); }

    private void editProfile(JSONObject me) {
        Context c = getContext();
        LinearLayout w = new LinearLayout(c);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), 0);
        EditText handle = field(c, "Handle, like sara_k", me.optString("handle"));
        EditText name = field(c, "Name", me.optString("name"));
        EditText bio = field(c, "A line about you (160 characters)", me.optString("bio"));
        bio.setSingleLine(false);
        w.addView(handle);
        w.addView(name);
        w.addView(bio);
        new android.app.AlertDialog.Builder(c, p.night ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("Your profile")
                .setMessage("A handle is 3 to 20 lowercase letters, digits or underscores. It can change once in 30 days.")
                .setView(w)
                .setPositiveButton("Save", (d, i) -> {
                    String h = handle.getText().toString().trim().toLowerCase(Locale.ROOT);
                    Social.saveProfile(c, h.equals(me.optString("handle")) ? null : h, name.getText().toString().trim(),
                            bio.getText().toString().trim(), then("Profile saved."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText field(Context c, String hint, String value) {
        EditText f = new EditText(c);
        f.setSingleLine(true);
        f.setHint(hint);
        f.setText(value);
        return f;
    }

    // ---- Friends ----

    private void renderFriends() {
        Context c = getContext();
        loading();
        final int gen = generation;
        Social.network(c, (error, json, cached) -> {
            if (gen != generation) return;
            body.removeViewAt(body.getChildCount() - 1);
            if (!status(error, json, cached)) return;
            JSONObject n = (JSONObject) json;
            people("Friend requests", n.optJSONArray("requests_in"), "Nobody has asked you.", h -> {
                person(h, "Accept", "Accept @" + h.optString("handle"), () -> Social.befriend(c, h.optString("handle"), then("You are friends with @" + h.optString("handle") + ".")),
                        "Decline", "Decline @" + h.optString("handle"), () -> Social.unfriend(c, h.optString("handle"), then(null)));
            });
            people("Friends", n.optJSONArray("friends"), "No friends yet. Find people in Find.", h -> {
                person(h, "Unfriend", "Unfriend @" + h.optString("handle"), () -> Social.unfriend(c, h.optString("handle"), then(null)), null, null, null);
            });
            people("Asked, waiting", n.optJSONArray("requests_out"), null, h -> {
                person(h, "Cancel", "Cancel the request to @" + h.optString("handle"), () -> Social.unfriend(c, h.optString("handle"), then(null)), null, null, null);
            });
            people("Following", n.optJSONArray("following"), "You follow nobody. Find people in Find.", h -> {
                person(h, "Unfollow", "Unfollow @" + h.optString("handle"), () -> Social.unfollow(c, h.optString("handle"), then(null)), null, null, null);
            });
            people("Followers", n.optJSONArray("followers"), "Nobody follows you yet.", h -> {
                person(h, "Block", "Block @" + h.optString("handle"), () -> Social.block(c, h.optString("handle"), then("Blocked @" + h.optString("handle") + ".")), null, null, null);
            });
            people("Blocked", n.optJSONArray("blocked"), null, h -> {
                person(h, "Unblock", "Unblock @" + h.optString("handle"), () -> Social.unblock(c, h.optString("handle"), then(null)), null, null, null);
            });
        });
    }

    private interface PerRow { void each(JSONObject person); }

    private void people(String title, JSONArray list, String empty, PerRow each) {
        int n = list == null ? 0 : list.length();
        if (n == 0 && empty == null) return;
        heading(title + (n > 0 ? " · " + n : ""));
        if (n == 0) {
            add(Ui.text(getContext(), empty, 14, false, p.muted), 6);
            return;
        }
        for (int i = 0; i < n; i++) each.each(list.optJSONObject(i));
    }

    /** One person: name and @handle, and up to two keys. */
    private void person(JSONObject who, String k1, String d1, Runnable r1, String k2, String d2, Runnable r2) {
        Context c = getContext();
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(c, who.optString("name"), 15, 600, p.text));
        words.addView(Ui.text(c, "@" + who.optString("handle"), 13, false, p.muted));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        if (k1 != null) r.addView(key(k1, d1, r1), keyParams());
        if (k2 != null) r.addView(key(k2, d2, r2), keyParams());
        add(r, 2);
    }

    // ---- Find ----

    private void renderFind() {
        Context c = getContext();
        LinearLayout search = row();
        EditText f = new EditText(c);
        f.setSingleLine(true);
        f.setHint("Search by handle");
        f.setText(query);
        search.addView(f, new LinearLayout.LayoutParams(0, -2, 1f));
        search.addView(key("Search", "Search people", () -> {
            query = f.getText().toString().trim();
            render();
        }), keyParams());
        add(search, 14);
        if (!query.isEmpty()) {
            heading("Results for " + query);
            loading();
            final int gen = generation;
            Social.search(c, query, (error, json, cached) -> {
                if (gen != generation) return;
                body.removeViewAt(body.getChildCount() - 1);
                if (!status(error, json, cached)) return;
                JSONArray list = (JSONArray) json;
                if (list.length() == 0) add(Ui.text(c, "Nobody with that handle.", 14, false, p.muted), 6);
                for (int i = 0; i < list.length(); i++) {
                    JSONObject h = list.optJSONObject(i);
                    String handle = h.optString("handle");
                    person(h, "Follow", "Follow @" + handle, () -> Social.follow(c, handle, then("Following @" + handle + ".")),
                            "Friend", "Ask @" + handle + " to be friends", () -> Social.befriend(c, handle, then("Asked @" + handle + ".")));
                }
            });
            return;
        }
        if (cityOpen != null) {
            LinearLayout up = row();
            up.addView(key("All cities", "Back to all cities", () -> {
                cityOpen = null;
                render();
            }));
            add(up, 14);
            heading(cityName);
            guides(Social::cityGuides, cityOpen, "No public guides here yet.");
            return;
        }
        heading("From people you follow");
        final int gen = generation;
        loading();
        Social.feed(c, (error, json, cached) -> {
            if (gen != generation) return;
            body.removeViewAt(body.getChildCount() - 1);
            if (!status(error, json, cached)) return;
            showGuides((JSONArray) json, "Nothing yet. Follow people to see their public guides, and friends' guides.");
            cities();
        });
    }

    private interface GuideSource { void get(Context c, String key, Social.Answer cb); }

    private void guides(GuideSource src, String key, String empty) {
        final int gen = generation;
        loading();
        src.get(getContext(), key, (error, json, cached) -> {
            if (gen != generation) return;
            body.removeViewAt(body.getChildCount() - 1);
            if (!status(error, json, cached)) return;
            showGuides((JSONArray) json, empty);
        });
    }

    private void showGuides(JSONArray list, String empty) {
        if (list.length() == 0) add(Ui.text(getContext(), empty, 14, false, p.muted), 6);
        for (int i = 0; i < list.length(); i++) guide(list.optJSONObject(i));
    }

    private void cities() {
        Context c = getContext();
        heading("Cities");
        final int gen = generation;
        loading();
        Social.cities(c, (error, json, cached) -> {
            if (gen != generation) return;
            body.removeViewAt(body.getChildCount() - 1);
            if (!status(error, json, cached)) return;
            JSONArray list = (JSONArray) json;
            if (list.length() == 0) add(Ui.text(c, "No public guides yet. Be the first: Publish a shot.", 14, false, p.muted), 6);
            for (int i = 0; i < list.length(); i++) {
                JSONObject ct = list.optJSONObject(i);
                LinearLayout r = row();
                r.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
                r.setClickable(true);
                r.setContentDescription("Guides in " + ct.optString("name"));
                r.addView(Ui.text(c, ct.optString("name"), 15, 600, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(Ui.text(c, plural(ct.optInt("guides"), "guide"), 13, false, p.muted));
                r.setOnClickListener(v -> {
                    cityOpen = ct.optString("slug");
                    cityName = ct.optString("name");
                    render();
                });
                add(r, 0);
            }
        });
    }

    /** One guide: title, where and who, its likes (a key) and a tap that opens the page. */
    private void guide(JSONObject g) {
        Context c = getContext();
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setClickable(true);
        words.setContentDescription("Open the guide " + g.optString("title"));
        words.addView(Ui.text(c, g.optString("title"), 15, 600, p.text));
        String by = g.optString("authorHandle").isEmpty() ? g.optString("author") : "@" + g.optString("authorHandle");
        words.addView(Ui.text(c, g.optString("city") + " · by " + by, 13, false, p.muted));
        words.setOnClickListener(v -> {
            if ("friends".equals(g.optString("level"))) {          // no sign-in on the web: a friends-only guide opens here
                JSONArray shots = g.optJSONArray("shots");
                String code = shots == null || shots.length() == 0 ? "" : shots.optJSONObject(0).optString("code");
                if (code.isEmpty()) host.say("This guide holds no shot.");
                else host.openShot(code);
                return;
            }
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(g.optString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (android.content.ActivityNotFoundException e) {
                host.say("No app on this phone can open a web page.");
            }
        });
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        int likes = g.optInt("likes");
        boolean mine = g.optString("authorHandle").equals(Social.handle(c));
        TextView like = Ui.smallButton(c, p, (g.optBoolean("liked") ? "Liked " : "Like ") + likes);
        like.setSingleLine(true);
        like.setMinHeight(Ui.dp(c, 40));
        like.setContentDescription((g.optBoolean("liked") ? "Unlike " : "Like ") + "the guide " + g.optString("title"));
        like.setAlpha(mine ? 0.5f : 1f);
        like.setOnClickListener(v -> {
            if (mine) {
                host.say("You cannot like your own guide.");
                return;
            }
            boolean wasLiked = g.optBoolean("liked");                // as it is now, not as the row was built
            Social.Answer done = (error, json, cached) -> {
                if (error != null) host.say(Social.NEEDS_NETWORK.equals(error) ? "Needs a network." : error);
                else if (json instanceof JSONObject) {
                    try {
                        g.put("likes", ((JSONObject) json).optInt("likes"));
                        g.put("liked", ((JSONObject) json).optBoolean("liked"));
                    } catch (org.json.JSONException ignored) {
                    }
                    like.setText((g.optBoolean("liked") ? "Liked " : "Like ") + g.optInt("likes"));
                    like.setContentDescription((g.optBoolean("liked") ? "Unlike " : "Like ") + "the guide " + g.optString("title"));
                }
            };
            if (wasLiked) Social.unlike(c, g.optString("id"), 0, done);
            else Social.like(c, g.optString("id"), 0, done);
        });
        r.addView(like, keyParams());
        add(r, 2);
    }
}
