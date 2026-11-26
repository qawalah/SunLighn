package dev.qawalah.sunwhere.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** The SunWhere server: accounts without email, guides (a shot or a trip shared), their photos and card, ratings,
 *  reports, and the web pages that open in the app. One process, one SQLite file, one folder of media. */
public final class App {
    private final Config cfg;
    private final Store store;
    private final Pages pages;
    private final ObjectMapper json = new ObjectMapper();
    private final RateLimit limits = new RateLimit();
    private final Javalin web;
    private final ScheduledExecutorService chores = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chores");
        t.setDaemon(true);
        return t;
    });

    private static final long HOUR = 3_600_000L, DAY = 86_400_000L;
    static final int PHOTO_MAX = 3, PHOTO_BYTES = 3_000_000, CARD_BYTES = 3_000_000;

    public App(Config cfg) throws SQLException, IOException {
        this.cfg = cfg;
        this.store = new Store(cfg.dataDir);
        this.pages = new Pages(cfg);
        Files.createDirectories(cfg.dataDir.resolve("media"));
        web = Javalin.create(c -> {
            c.showJavalinBanner = false;
            c.http.maxRequestSize = 4_000_000L;
        });
        routes();
    }

    public App start() {
        web.start(cfg.port);
        chores.scheduleAtFixedRate(limits::sweep, 1, 1, TimeUnit.HOURS);
        if (cfg.backups) chores.scheduleAtFixedRate(this::backup, 1, 24, TimeUnit.HOURS);
        return this;
    }

    public int port() { return web.port(); }

    public void stop() {
        chores.shutdownNow();
        web.stop();
    }

    private void backup() {
        try {
            Path dir = cfg.dataDir.resolve("backup");
            store.backupTo(dir.resolve("sunwhere-" + LocalDate.now() + ".db"));
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> all = new ArrayList<>(s.filter(p -> p.getFileName().toString().startsWith("sunwhere-")).sorted().toList());
                while (all.size() > 7) Files.deleteIfExists(all.remove(0));
            }
        } catch (Exception e) {
            System.err.println("backup failed: " + e);
        }
    }

    // ------------------------------------------------------------------ routes

    private void routes() {
        web.exception(ApiError.class, (e, ctx) -> {
            ctx.status(e.status);
            if (ctx.path().startsWith("/api/")) ctx.json(Map.of("error", e.getMessage()));
            else ctx.contentType("text/html; charset=utf-8").result(e.status == 404 ? pages.notFound() : e.getMessage());
        });
        web.exception(io.javalin.http.HttpResponseException.class, (e, ctx) -> {
            ctx.status(e.getStatus());
            if (ctx.path().startsWith("/api/")) ctx.json(Map.of("error", e.getMessage()));
        });
        web.exception(Exception.class, (e, ctx) -> {
            System.err.println("error on " + ctx.method() + " " + ctx.path() + ": " + e);
            ctx.status(500).json(Map.of("error", "Something went wrong on the server."));
        });

        web.get("/healthz", ctx -> ctx.result("ok"));
        web.get("/robots.txt", ctx -> ctx.contentType("text/plain").result("User-agent: *\nAllow: /\nSitemap: " + cfg.publicUrl + "/sitemap.xml\n"));
        web.get("/sitemap.xml", this::sitemap);

        web.get("/", ctx -> html(ctx, pages.home(store.cities(holdBefore()))));
        web.get("/c/{slug}", ctx -> {
            String slug = ctx.pathParam("slug");
            List<Store.Guide> gs = store.listed(slug, holdBefore(), 300);
            if (gs.isEmpty()) throw ApiError.notFound();
            html(ctx, pages.city(slug, gs.get(0).city, gs));
        });
        web.get("/g/{id}", ctx -> {
            Store.Guide g = visible(ctx.pathParam("id"));
            html(ctx, pages.guide(g, Files.isRegularFile(media(g.id, "card.png")), photoCount(g.id)));
        });
        web.get("/g/{id}/card.png", ctx -> serve(ctx, visible(ctx.pathParam("id")).id, "card.png", "image/png"));
        web.get("/g/{id}/photo/{file}", ctx -> {
            String f = ctx.pathParam("file");
            if (!f.matches("[1-3]\\.jpg")) throw ApiError.notFound();
            serve(ctx, visible(ctx.pathParam("id")).id, "photo-" + f.charAt(0) + ".jpg", "image/jpeg");
        });

        web.post("/api/accounts", this::createAccount);
        web.post("/api/accounts/recover", this::recover);
        web.get("/api/me", ctx -> {
            Store.Account a = account(ctx);
            ctx.json(Map.of("id", a.id, "name", a.name));
        });
        web.delete("/api/me", ctx -> {
            Store.Account a = account(ctx);
            for (String gid : store.deleteAccount(a.id)) deleteMedia(gid);
            ctx.status(204);
        });

        web.get("/api/cities", ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (String[] c : store.cities(holdBefore())) out.add(Map.of("slug", c[0], "name", c[1], "guides", Integer.parseInt(c[2])));
            ctx.json(out);
        });
        web.get("/api/cities/{slug}/guides", ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Store.Guide g : store.listed(ctx.pathParam("slug"), holdBefore(), 300)) out.add(describe(g));
            ctx.json(out);
        });
        web.get("/api/guides/{id}", ctx -> ctx.json(describe(visible(ctx.pathParam("id")))));
        web.put("/api/guides/{id}", this::putGuide);
        web.delete("/api/guides/{id}", ctx -> {
            Store.Account a = account(ctx);
            owned(ctx, a);
            store.deleteGuide(ctx.pathParam("id"));
            deleteMedia(ctx.pathParam("id"));
            ctx.status(204);
        });
        web.put("/api/guides/{id}/card", ctx -> {
            Store.Account a = account(ctx);
            Store.Guide g = owned(ctx, a);
            writeLimit(a);
            byte[] b = ctx.bodyAsBytes();
            if (b.length > CARD_BYTES || !isPng(b)) throw ApiError.bad("The card must be a PNG of at most 3 MB.");
            write(media(g.id, "card.png"), b);
            touch(g);
            ctx.status(204);
        });
        web.put("/api/guides/{id}/photos/{n}", ctx -> {
            Store.Account a = account(ctx);
            Store.Guide g = owned(ctx, a);
            writeLimit(a);
            int n = photoNo(ctx);
            byte[] b = ctx.bodyAsBytes();
            if (b.length > PHOTO_BYTES || !isJpeg(b)) throw ApiError.bad("A photo must be a JPEG of at most 3 MB.");
            write(media(g.id, "photo-" + n + ".jpg"), b);
            touch(g);
            ctx.status(204);
        });
        web.delete("/api/guides/{id}/photos/{n}", ctx -> {
            Store.Account a = account(ctx);
            Store.Guide g = owned(ctx, a);
            Files.deleteIfExists(media(g.id, "photo-" + photoNo(ctx) + ".jpg"));
            touch(g);
            ctx.status(204);
        });

        web.post("/api/guides/{id}/rating", ctx -> {
            Store.Account a = account(ctx);
            Store.Guide g = visible(ctx.pathParam("id"));
            if (g.owner.equals(a.id)) throw ApiError.forbidden("You cannot rate your own guide.");
            if (!limits.allow("vote:" + a.id, 200, DAY)) throw ApiError.tooMany();
            int stars = body(ctx).path("stars").asInt(0);
            if (stars < 1 || stars > 5) throw ApiError.bad("stars is 1 to 5.");
            store.rate(g.id, a.id, stars);
            ctx.json(describe(store.guide(g.id)));
        });
        web.post("/api/guides/{id}/report", ctx -> {
            Store.Account a = account(ctx);
            Store.Guide g = visible(ctx.pathParam("id"));
            if (!limits.allow("vote:" + a.id, 200, DAY)) throw ApiError.tooMany();
            String reason = clip(body(ctx).path("reason").asText(""), 300);
            if (reason.isBlank()) throw ApiError.bad("Say what is wrong in a few words.");
            if (store.report(g.id, a.id, reason) >= cfg.reportsToHide) store.setHidden(g.id, true);
            ctx.status(204);
        });

        web.post("/api/admin/guides/{id}/hide", ctx -> admin(ctx, true));
        web.post("/api/admin/guides/{id}/unhide", ctx -> admin(ctx, false));
    }

    // ------------------------------------------------------------------ accounts

    private void createAccount(Context ctx) {
        if (!limits.allow("new:" + ip(ctx), 5, HOUR)) throw ApiError.tooMany();
        String name = body(ctx).path("name").asText("").trim();
        if (!Text.NAME.matcher(name).matches())
            throw ApiError.bad("A name is 2 to 30 letters, digits, spaces, dots, dashes or underscores.");
        String token = Text.token(), recovery = Text.recoveryCode();
        Store.Account a = store.createAccount(name, token, Text.normaliseRecovery(recovery));
        if (a == null) throw new ApiError(409, "That name is taken. Choose another.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.id);
        out.put("name", a.name);
        out.put("token", token);
        out.put("recovery", recovery);
        ctx.status(201).json(out);
    }

    private void recover(Context ctx) {
        if (!limits.allow("rec:" + ip(ctx), 10, HOUR)) throw ApiError.tooMany();
        JsonNode b = body(ctx);
        String token = Text.token();
        Store.Account a = store.recover(b.path("name").asText("").trim(), Text.normaliseRecovery(b.path("recovery").asText("")), token);
        if (a == null) throw new ApiError(403, "The name and the recovery code do not match.");
        ctx.json(Map.of("id", a.id, "name", a.name, "token", token));
    }

    private Store.Account account(Context ctx) {
        String h = ctx.header("Authorization");
        if (h == null || !h.startsWith("Bearer ") || h.length() < 20) throw ApiError.unauthorized();
        Store.Account a = store.byToken(h.substring(7).trim());
        if (a == null) throw ApiError.unauthorized();
        return a;
    }

    // ------------------------------------------------------------------ guides

    private void putGuide(Context ctx) {
        Store.Account a = account(ctx);
        String id = ctx.pathParam("id");
        if (!Text.ID.matcher(id).matches()) throw ApiError.bad("The guide's id is 8 to 40 letters, digits, - or _.");
        writeLimit(a);
        Store.Guide old = store.guide(id);
        if (old != null && !old.owner.equals(a.id)) throw ApiError.forbidden("This guide belongs to someone else.");
        JsonNode b = body(ctx);
        Store.Guide g = new Store.Guide();
        g.id = id;
        g.owner = a.id;
        g.title = need(b, "title", 100);
        g.city = need(b, "city", 80);
        g.citySlug = Text.slug(g.city);
        g.country = clip(b.path("country").asText(""), 60).trim();
        g.summary = clip(b.path("summary").asText(""), 600).trim();
        g.month = b.path("month").asInt(0);
        g.year = b.path("year").asInt(0);
        if (g.month < 1 || g.month > 12) throw ApiError.bad("month is 1 to 12.");
        if (g.year < 2000 || g.year > 2100) throw ApiError.bad("year is not right.");
        g.listed = b.path("listed").asBoolean(false);
        JsonNode shots = b.path("shots");
        if (!shots.isArray() || shots.isEmpty() || shots.size() > 20) throw ApiError.bad("A guide has 1 to 20 shots.");
        ArrayNode clean = json.createArrayNode();
        for (JsonNode s : shots) {
            String code = s.path("code").asText("");
            if (!Text.CODE.matcher(code).matches()) throw ApiError.bad("A shot's code is not right.");
            clean.addObject().put("name", clip(s.path("name").asText("Shot"), 100)).put("code", code);
        }
        g.shotsJson = clean.toString();
        g.created = old != null ? old.created : Store.now();
        g.updated = Store.now();
        store.saveGuide(g);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", g.id);
        out.put("url", cfg.publicUrl + "/g/" + g.id);
        ctx.status(old == null ? 201 : 200).json(out);
    }

    private Store.Guide owned(Context ctx, Store.Account a) {
        Store.Guide g = store.guide(ctx.pathParam("id"));
        if (g == null) throw ApiError.notFound();
        if (!g.owner.equals(a.id)) throw ApiError.forbidden("This guide belongs to someone else.");
        return g;
    }

    private Store.Guide visible(String id) {
        Store.Guide g = Text.ID.matcher(id).matches() ? store.guide(id) : null;
        if (g == null || g.hidden) throw ApiError.notFound();
        return g;
    }

    private void touch(Store.Guide g) {
        g.updated = Store.now();
        store.saveGuide(g);
    }

    private Map<String, Object> describe(Store.Guide g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.id);
        m.put("title", g.title);
        m.put("city", g.city);
        m.put("citySlug", g.citySlug);
        m.put("country", g.country);
        m.put("month", g.month);
        m.put("year", g.year);
        m.put("summary", g.summary);
        m.put("author", g.ownerName);
        m.put("listed", g.listed);
        m.put("rating", Math.round(g.avg * 10) / 10.0);
        m.put("votes", g.votes);
        m.put("photos", photoCount(g.id));
        m.put("hasCard", Files.isRegularFile(media(g.id, "card.png")));
        m.put("updated", g.updated);
        m.put("url", cfg.publicUrl + "/g/" + g.id);
        try {
            m.put("shots", json.readTree(g.shotsJson));
        } catch (IOException e) {
            m.put("shots", List.of());
        }
        return m;
    }

    private void admin(Context ctx, boolean hide) {
        String t = ctx.header("X-Admin-Token");
        if (cfg.adminToken.isEmpty() || t == null || !java.security.MessageDigest.isEqual(t.getBytes(), cfg.adminToken.getBytes()))
            throw ApiError.forbidden("Not allowed.");
        if (store.setHidden(ctx.pathParam("id"), hide) == 0) throw ApiError.notFound();
        ctx.status(204);
    }

    // ------------------------------------------------------------------ pages and media

    private void sitemap(Context ctx) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        b.append("<url><loc>").append(cfg.publicUrl).append("/</loc></url>");
        for (String[] c : store.cities(holdBefore()))
            b.append("<url><loc>").append(Text.esc(cfg.publicUrl + "/c/" + Text.url(c[0]))).append("</loc></url>");
        for (Store.Guide g : store.listed(null, holdBefore(), 5000))
            b.append("<url><loc>").append(cfg.publicUrl).append("/g/").append(g.id).append("</loc></url>");
        ctx.contentType("application/xml").result(b.append("</urlset>").toString());
    }

    private static void html(Context ctx, String page) {
        ctx.contentType("text/html; charset=utf-8").header("Cache-Control", "public, max-age=60").result(page);
    }

    private void serve(Context ctx, String guide, String name, String type) throws IOException {
        Path p = media(guide, name);
        if (!Files.isRegularFile(p)) throw ApiError.notFound();
        ctx.contentType(type).header("Cache-Control", "public, max-age=86400").result(Files.readAllBytes(p));
    }

    private Path media(String guide, String name) { return cfg.dataDir.resolve("media").resolve(guide).resolve(name); }

    private int photoCount(String guide) {
        int n = 0;
        for (int i = 1; i <= PHOTO_MAX; i++) if (Files.isRegularFile(media(guide, "photo-" + i + ".jpg"))) n++;
        return n;
    }

    private void deleteMedia(String guide) throws IOException {
        Path d = cfg.dataDir.resolve("media").resolve(guide);
        if (!Files.isDirectory(d)) return;
        try (Stream<Path> s = Files.list(d)) {
            for (Path p : s.toList()) Files.deleteIfExists(p);
        }
        Files.deleteIfExists(d);
    }

    private static void write(Path p, byte[] b) throws IOException {
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.write(tmp, b);
        Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    // ------------------------------------------------------------------ small things

    private long holdBefore() { return Store.now() - cfg.holdSeconds; }

    private void writeLimit(Store.Account a) {
        if (!limits.allow("write:" + a.id, 300, DAY)) throw ApiError.tooMany();
    }

    private String ip(Context ctx) {
        if (cfg.trustProxy) {
            String f = ctx.header("Fly-Client-IP");
            if (f != null && !f.isBlank()) return f.trim();
            String x = ctx.header("X-Forwarded-For");
            if (x != null && !x.isBlank()) return x.split(",")[0].trim();
        }
        return ctx.ip();
    }

    private JsonNode body(Context ctx) {
        try {
            JsonNode n = json.readTree(ctx.bodyAsBytes());
            if (n == null || !n.isObject()) throw ApiError.bad("Send a JSON object.");
            return n;
        } catch (IOException e) {
            throw ApiError.bad("The body is not JSON.");
        }
    }

    private static String need(JsonNode b, String key, int max) {
        String s = b.path(key).asText("").trim();
        if (s.isEmpty()) throw ApiError.bad(key + " is needed.");
        return clip(s, max);
    }

    private static String clip(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }

    private static int photoNo(Context ctx) {
        try {
            int n = Integer.parseInt(ctx.pathParam("n"));
            if (n >= 1 && n <= PHOTO_MAX) return n;
        } catch (NumberFormatException e) {
            // falls to the error
        }
        throw ApiError.bad("A photo's number is 1 to 3.");
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
