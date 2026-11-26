package dev.qawalah.sunwhere.server;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** People: profiles, search, follow, friends, block, the network (people design, task 3). */
final class People {
    private final App app;
    private final Store store;
    private static final int ACTIONS_PER_DAY = 100;

    People(App app) {
        this.app = app;
        this.store = app.store;
    }

    void routes(Javalin web) {
        web.get("/api/people", this::search);
        web.get("/api/people/{handle}", ctx -> ctx.json(profile(target(ctx, app.viewer(ctx), false), app.viewer(ctx))));
        web.post("/api/people/{handle}/follow", ctx -> act(ctx, (me, t) -> store.follow(me.id, t.id, true)));
        web.delete("/api/people/{handle}/follow", ctx -> act(ctx, (me, t) -> store.follow(me.id, t.id, false)));
        web.post("/api/people/{handle}/friend", ctx -> {
            Store.Account me = app.account(ctx);
            Store.Account t = target(ctx, me.id, true);
            limit(me);
            ctx.json(Map.of("state", store.friendRequest(me.id, t.id)));
        });
        web.delete("/api/people/{handle}/friend", ctx -> act(ctx, (me, t) -> store.friendRemove(me.id, t.id)));
        web.post("/api/people/{handle}/block", ctx -> {
            Store.Account me = app.account(ctx);
            Store.Account t = store.byHandle(ctx.pathParam("handle"));
            if (t == null || t.banned) throw ApiError.notFound();
            if (t.id.equals(me.id)) throw ApiError.bad("You cannot do that to yourself.");
            limit(me);
            store.block(me.id, t.id, true);
            ctx.status(204);
        });
        web.delete("/api/people/{handle}/block", ctx -> {
            Store.Account me = app.account(ctx);
            Store.Account t = store.byHandle(ctx.pathParam("handle"));
            if (t == null) throw ApiError.notFound();
            store.block(me.id, t.id, false);
            ctx.status(204);
        });
        web.get("/api/me/network", ctx -> ctx.json(network(app.account(ctx))));
    }

    private interface Action { void run(Store.Account me, Store.Account target); }

    private void act(Context ctx, Action a) {
        Store.Account me = app.account(ctx);
        Store.Account t = target(ctx, me.id, true);
        limit(me);
        a.run(me, t);
        ctx.status(204);
    }

    private void limit(Store.Account me) {
        if (!app.limits.allow("people:" + me.id, ACTIONS_PER_DAY, App.DAY)) throw ApiError.tooMany();
    }

    /** The person behind {handle}: 404 when unknown, banned, or blocked either way; 400 for yourself when {@code notSelf}. */
    private Store.Account target(Context ctx, String viewer, boolean notSelf) {
        Store.Account t = store.byHandle(ctx.pathParam("handle"));
        if (t == null || t.banned) throw ApiError.notFound();
        if (viewer != null && store.blocked(viewer, t.id)) throw ApiError.notFound();
        if (notSelf && t.id.equals(viewer)) throw ApiError.bad("You cannot do that to yourself.");
        return t;
    }

    Map<String, Object> profile(Store.Account t, String viewer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handle", t.handle);
        m.put("name", t.name);
        m.put("bio", t.bio);
        m.put("joined", t.created);
        m.put("stats", store.stats(t.id));
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("self", t.id.equals(viewer));
        rel.put("following", viewer != null && store.following(viewer, t.id));
        rel.put("friend", viewer != null && store.areFriends(viewer, t.id));
        rel.put("pending_in", viewer != null && store.pending(t.id, viewer));
        rel.put("pending_out", viewer != null && store.pending(viewer, t.id));
        m.put("relation", rel);
        return m;
    }

    private void search(Context ctx) {
        String q = ctx.queryParam("q") == null ? "" : ctx.queryParam("q").trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) throw ApiError.bad("Type at least 2 characters of a handle.");
        q = q.replace("\\", "").replace("%", "").replace("_", "\\_");
        String viewer = app.viewer(ctx);
        List<Map<String, Object>> out = new ArrayList<>();
        String v = viewer == null ? "" : viewer;
        for (String[] p : store.people("SELECT handle, name FROM account WHERE handle LIKE ? ESCAPE '\\' AND banned=0 AND id<>?"
                + " AND id NOT IN (SELECT blocked FROM block WHERE blocker=?) AND id NOT IN (SELECT blocker FROM block WHERE blocked=?)"
                + " ORDER BY handle LIMIT 20", q + "%", v, v, v))
            out.add(Map.of("handle", p[0], "name", p[1]));
        ctx.json(out);
    }

    private Map<String, Object> network(Store.Account me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("followers", refs("SELECT a.handle,a.name FROM follow f JOIN account a ON a.id=f.follower WHERE f.followee=? AND a.banned=0 ORDER BY a.handle", me.id));
        m.put("following", refs("SELECT a.handle,a.name FROM follow f JOIN account a ON a.id=f.followee WHERE f.follower=? AND a.banned=0 ORDER BY a.handle", me.id));
        m.put("friends", refs("SELECT a.handle,a.name FROM friend f JOIN account a ON a.id=CASE WHEN f.a=? THEN f.b ELSE f.a END"
                + " WHERE f.state='accepted' AND (f.a=? OR f.b=?) AND a.banned=0 ORDER BY a.handle", me.id, me.id, me.id));
        m.put("requests_in", refs("SELECT a.handle,a.name FROM friend f JOIN account a ON a.id=f.a WHERE f.b=? AND f.state='pending' AND a.banned=0 ORDER BY f.created", me.id));
        m.put("requests_out", refs("SELECT a.handle,a.name FROM friend f JOIN account a ON a.id=f.b WHERE f.a=? AND f.state='pending' AND a.banned=0 ORDER BY f.created", me.id));
        m.put("blocked", refs("SELECT a.handle,a.name FROM block b JOIN account a ON a.id=b.blocked WHERE b.blocker=? ORDER BY a.handle", me.id));
        return m;
    }

    private List<Map<String, Object>> refs(String sql, Object... args) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] p : store.people(sql, args)) out.add(Map.of("handle", p[0], "name", p[1]));
        return out;
    }
}
