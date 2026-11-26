package dev.qawalah.sunwhere.server;

import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The owner's numbers and two actions (people design, task 6): ban an account, and the older hide a guide. */
final class Admin {
    private final App app;
    private final Store store;
    private final Config cfg;

    private static final Object CLOCK = new Object();
    private static LocalDate day = LocalDate.now(java.time.ZoneOffset.UTC);
    private static long requests;

    Admin(App app, Config cfg) {
        this.app = app;
        this.store = app.store;
        this.cfg = cfg;
    }

    /** Counts every request for today's number; the count starts again at midnight UTC and at a restart. */
    static void countRequest() {
        synchronized (CLOCK) {
            LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
            if (!today.equals(day)) {
                day = today;
                requests = 0;
            }
            requests++;
        }
    }

    private static long requestsToday() {
        synchronized (CLOCK) {
            return LocalDate.now(java.time.ZoneOffset.UTC).equals(day) ? requests : 0;
        }
    }

    void routes(Javalin web) {
        web.get("/admin", ctx -> {
            // Names in the stats are user text (a city is whatever a publisher typed): the page builds cells with textContent
            // and, as a second lock, its CSP lets only its own script run (a nonce per response), no inline handlers.
            byte[] n = new byte[16];
            new java.security.SecureRandom().nextBytes(n);
            String nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(n);
            ctx.contentType("text/html; charset=utf-8").header("Cache-Control", "no-store")
                    .header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'unsafe-inline'; "
                            + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                    .header("X-Content-Type-Options", "nosniff").result(PAGE.replace("%NONCE%", nonce));
        });
        web.get("/api/admin/stats", ctx -> {
            app.requireAdmin(ctx);
            ctx.json(stats());
        });
        web.post("/api/admin/accounts/{handle}/ban", ctx -> ban(ctx, true));
        web.post("/api/admin/accounts/{handle}/unban", ctx -> ban(ctx, false));
    }

    private void ban(io.javalin.http.Context ctx, boolean on) {
        app.requireAdmin(ctx);
        if (!store.setBanned(ctx.pathParam("handle"), on)) throw ApiError.notFound();
        ctx.status(204);
    }

    Map<String, Object> stats() {
        long week = Store.now() - 7L * 86400L;
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> acc = new LinkedHashMap<>();
        acc.put("total", store.count("SELECT COUNT(*) FROM account"));
        acc.put("new_7d", store.count("SELECT COUNT(*) FROM account WHERE created>=?", week));
        acc.put("banned", store.count("SELECT COUNT(*) FROM account WHERE banned=1"));
        m.put("accounts", acc);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("total", store.count("SELECT COUNT(*) FROM guide"));
        g.put("public", store.count("SELECT COUNT(*) FROM guide WHERE level='public'"));
        g.put("friends", store.count("SELECT COUNT(*) FROM guide WHERE level='friends'"));
        g.put("link", store.count("SELECT COUNT(*) FROM guide WHERE level='link'"));
        g.put("hidden", store.count("SELECT COUNT(*) FROM guide WHERE hidden=1"));
        m.put("guides", g);
        long[] media = media();
        m.put("photos", media[0]);
        m.put("storage_bytes", media[1]);
        m.put("db_bytes", dbBytes());
        m.put("likes", store.count("SELECT COUNT(*) FROM like_"));
        m.put("follows", store.count("SELECT COUNT(*) FROM follow"));
        m.put("friendships", store.count("SELECT COUNT(*) FROM friend WHERE state='accepted'"));
        m.put("pending_requests", store.count("SELECT COUNT(*) FROM friend WHERE state='pending'"));
        m.put("blocks", store.count("SELECT COUNT(*) FROM block"));
        m.put("ratings", store.count("SELECT COUNT(*) FROM rating"));
        m.put("open_reports", store.count("SELECT COUNT(*) FROM report r JOIN guide g ON g.id=r.guide_id WHERE g.hidden=0"));
        List<Map<String, Object>> cities = new ArrayList<>();
        for (String[] c : store.topCities(10)) cities.add(Map.of("name", c[0], "guides", Integer.parseInt(c[1])));
        m.put("top_cities", cities);
        m.put("requests_today", requestsToday());
        return m;
    }

    /** {photo files, bytes of everything under media}. */
    private long[] media() {
        long[] out = new long[2];
        Path root = cfg.dataDir.resolve("media");
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                out[1] += Files.size(p);
                if (p.getFileName().toString().startsWith("photo-")) out[0]++;
            }
        } catch (IOException e) {
            // a number that cannot be read is left as it was counted so far
        }
        return out;
    }

    private long dbBytes() {
        long n = 0;
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            try {
                Path p = store.file.resolveSibling(store.file.getFileName() + suffix);
                if (Files.isRegularFile(p)) n += Files.size(p);
            } catch (IOException e) {
                // as above
            }
        }
        return n;
    }

    private static final String PAGE = """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>SunWhere admin</title><meta name="robots" content="noindex">
            <style>body{font:15px/1.5 system-ui,sans-serif;max-width:720px;margin:0 auto;padding:20px;background:#ECEBE7;color:#1D1D1B}
            @media(prefers-color-scheme:dark){body{background:#14181F;color:#ECEBE7}input,button{background:#1B2029;color:#ECEBE7}}
            h1{font-size:24px}table{border-collapse:collapse;width:100%;margin:8px 0 20px}td{border-bottom:1px solid #8884;padding:6px 4px}
            td:last-child{text-align:right;font-variant-numeric:tabular-nums}input,button{font:inherit;padding:8px 10px;border:1px solid #8886;border-radius:8px}
            button{background:#EE5A1E;color:#fff;border:0;font-weight:600}.row{display:flex;gap:8px;margin:8px 0}.row input{flex:1}#msg{color:#c33}</style></head>
            <body><h1>SunWhere admin</h1>
            <div class="row"><input id="tok" type="password" placeholder="Admin token" autocomplete="off"><button id="go">Show</button></div>
            <p id="msg"></p><div id="out"></div>
            <h2>Actions</h2>
            <div class="row"><input id="who" placeholder="@handle"><button data-do="ban">Ban</button><button data-do="unban">Unban</button></div>
            <div class="row"><input id="gid" placeholder="Guide id"><button data-do="hide">Hide</button><button data-do="unhide">Unhide</button></div>
            <script nonce="%NONCE%">
            const $=id=>document.getElementById(id);
            const tok=()=>sessionStorage.getItem('t')||'';
            function addRows(tb,o,p){for(const [k,v] of Object.entries(o)){
              const name=(p?p+' ':'')+k.replace(/_/g,' ');
              if(Array.isArray(v)){row(tb,name,v.map(x=>x.name+' ('+x.guides+')').join(', '))}
              else if(v&&typeof v==='object'){addRows(tb,v,name)}
              else row(tb,name,/bytes/.test(k)?(v/1048576).toFixed(2)+' MB':String(v))}}
            function row(tb,a,b){const tr=document.createElement('tr'),x=document.createElement('td'),y=document.createElement('td');
              x.textContent=a;y.textContent=b;tr.append(x,y);tb.append(tr)}
            async function load(){const r=await fetch('/api/admin/stats',{headers:{'X-Admin-Token':tok()}});
              if(!r.ok){$('msg').textContent='Not allowed.';$('out').replaceChildren();return}
              $('msg').textContent='';const t=document.createElement('table'),tb=document.createElement('tbody');
              addRows(tb,await r.json(),'');t.append(tb);$('out').replaceChildren(t)}
            $('go').onclick=()=>{sessionStorage.setItem('t',$('tok').value);load()};
            document.querySelectorAll('[data-do]').forEach(b=>b.onclick=async()=>{
              const d=b.dataset.do,path=d==='ban'||d==='unban'?'/api/admin/accounts/'+encodeURIComponent($('who').value.replace('@',''))+'/'+d
                :'/api/admin/guides/'+encodeURIComponent($('gid').value)+'/'+d;
              const r=await fetch(path,{method:'POST',headers:{'X-Admin-Token':tok()}});
              $('msg').textContent=r.ok?'Done.':'Failed ('+r.status+').';if(r.ok)load()});
            if(tok())load();
            </script></body></html>
            """;
}
