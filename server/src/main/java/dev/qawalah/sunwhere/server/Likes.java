package dev.qawalah.sunwhere.server;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Likes on a guide and on its photos, and the feed (people design, task 4). */
final class Likes {
    private final App app;
    private final Store store;

    Likes(App app) {
        this.app = app;
        this.store = app.store;
    }

    void routes(Javalin web) {
        web.post("/api/guides/{id}/like", ctx -> like(ctx, 0, true));
        web.delete("/api/guides/{id}/like", ctx -> like(ctx, 0, false));
        web.post("/api/guides/{id}/photos/{n}/like", ctx -> like(ctx, photo(ctx), true));
        web.delete("/api/guides/{id}/photos/{n}/like", ctx -> like(ctx, photo(ctx), false));
        web.get("/api/feed", ctx -> {
            Store.Account me = app.account(ctx);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Store.Guide g : store.feed(me.id, 30)) out.add(app.describe(g, me.id));
            ctx.json(out);
        });
    }

    private static int photo(Context ctx) {
        try {
            int n = Integer.parseInt(ctx.pathParam("n"));
            if (n >= 1 && n <= 3) return n;
        } catch (NumberFormatException e) {
            // falls to the error
        }
        throw ApiError.bad("A photo's number is 1 to 3.");
    }

    private void like(Context ctx, int photo, boolean on) {
        Store.Account me = app.account(ctx);
        Store.Guide g = app.visible(ctx.pathParam("id"), me.id);
        if (g.owner.equals(me.id)) throw ApiError.forbidden("You cannot like your own guide.");
        if (photo > 0 && !app.hasPhoto(g.id, photo)) throw ApiError.notFound();
        if (!app.limits.allow("like:" + me.id, 300, App.DAY)) throw ApiError.tooMany();
        store.like(me.id, g.id, photo, on);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("likes", store.likeCounts(g.id)[photo]);
        out.put("liked", on);
        ctx.json(out);
    }
}
