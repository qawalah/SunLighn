package dev.qawalah.sunwhere.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;

import static dev.qawalah.sunwhere.server.Text.esc;

/** The web pages: plain HTML in the app's Pocket-map look (paper, an orange quarter disc), nothing to load but the page. */
final class Pages {
    private final Config cfg;
    private final ObjectMapper json = new ObjectMapper();

    Pages(Config cfg) { this.cfg = cfg; }

    private static final String CSS = """
            :root{--paper:#ECEBE7;--ink:#1D1D1B;--mute:#6B6A66;--rule:#CFCEC8;--sun:#EE5A1E;--card:#F7F6F2}
            @media(prefers-color-scheme:dark){:root{--paper:#14181F;--ink:#ECEBE7;--mute:#9A9890;--rule:#2B313B;--card:#1B2029}}
            *{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:16px/1.5 system-ui,"IBM Plex Sans Arabic",sans-serif}
            main{max-width:760px;margin:0 auto;padding:28px 18px 60px;position:relative}
            .disc{position:absolute;top:0;right:0;width:120px;height:120px;border-bottom-left-radius:120px;background:var(--sun);opacity:.95;z-index:0}
            main>*{position:relative;z-index:1}
            h1{font-size:30px;line-height:1.15;margin:44px 0 6px;max-width:80%}h2{font-size:18px;margin:30px 0 8px;border-bottom:1px solid var(--rule);padding-bottom:6px}
            a{color:inherit}.mute{color:var(--mute)}.brand{font-weight:700;letter-spacing:.02em}
            .row{display:block;text-decoration:none;padding:12px 0;border-bottom:1px solid var(--rule)}.row b{display:block}
            .btn{display:inline-block;background:var(--sun);color:#fff;text-decoration:none;padding:10px 18px;border-radius:24px;font-weight:600;margin:6px 8px 6px 0}
            .btn.alt{background:transparent;color:var(--ink);border:1px solid var(--rule)}
            .photos{display:flex;gap:8px;flex-wrap:wrap;margin:14px 0}.photos img{width:calc(50% - 4px);border-radius:10px}
            .card{width:100%;border-radius:12px;margin:14px 0}
            .shot{background:var(--card);border:1px solid var(--rule);border-radius:12px;padding:12px 14px;margin:10px 0}
            footer{margin-top:44px;color:var(--mute);font-size:13px}
            """;

    private String shell(String title, String desc, String canonical, String image, String body) {
        StringBuilder b = new StringBuilder(2048);
        b.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        b.append("<title>").append(esc(title)).append("</title>");
        b.append("<meta name=\"description\" content=\"").append(esc(desc)).append("\">");
        b.append("<link rel=\"canonical\" href=\"").append(esc(canonical)).append("\">");
        b.append("<meta property=\"og:site_name\" content=\"SunWhere\"><meta property=\"og:type\" content=\"article\">");
        b.append("<meta property=\"og:title\" content=\"").append(esc(title)).append("\">");
        b.append("<meta property=\"og:description\" content=\"").append(esc(desc)).append("\">");
        b.append("<meta property=\"og:url\" content=\"").append(esc(canonical)).append("\">");
        if (image != null) {
            b.append("<meta property=\"og:image\" content=\"").append(esc(image)).append("\">");
            b.append("<meta name=\"twitter:card\" content=\"summary_large_image\"><meta name=\"twitter:image\" content=\"").append(esc(image)).append("\">");
        } else {
            b.append("<meta name=\"twitter:card\" content=\"summary\">");
        }
        b.append("<meta name=\"twitter:title\" content=\"").append(esc(title)).append("\"><meta name=\"twitter:description\" content=\"").append(esc(desc)).append("\">");
        b.append("<style>").append(CSS).append("</style></head><body><main><div class=\"disc\"></div>");
        b.append("<div class=\"brand\"><a href=\"/\" style=\"text-decoration:none\">SunWhere</a></div>");
        b.append(body);
        b.append("<footer>SunWhere puts the sun or the moon exactly where you want it in a photo. Guides are shared by people; ")
                .append("a guide you find wrong can be reported in the app.</footer></main></body></html>");
        return b.toString();
    }

    String home(List<String[]> cities) {
        StringBuilder b = new StringBuilder();
        b.append("<h1>Where the sun will be, city by city</h1><p class=\"mute\">Guides shared by photographers: where to stand and when, for a skyline, a mosque, a bridge.</p>");
        if (cities.isEmpty()) b.append("<p class=\"mute\">No guides yet. Share the first one from the app.</p>");
        else {
            b.append("<h2>Cities</h2>");
            for (String[] c : cities)
                b.append("<a class=\"row\" href=\"/c/").append(Text.url(c[0])).append("\"><b>").append(esc(c[1])).append("</b><span class=\"mute\">")
                        .append(c[2]).append(c[2].equals("1") ? " guide" : " guides").append("</span></a>");
        }
        return shell("SunWhere guides", "Guides for the sun and the moon, city by city.", cfg.publicUrl + "/", null, b.toString());
    }

    String city(String slug, String name, List<Store.Guide> guides) {
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(name)).append("</h1><p class=\"mute\">").append(guides.size()).append(guides.size() == 1 ? " guide" : " guides").append("</p>");
        String last = "";
        for (Store.Guide g : guides) {
            String head = Text.MONTHS[g.month - 1] + " " + g.year;
            if (!head.equals(last)) {
                b.append("<h2>").append(head).append("</h2>");
                last = head;
            }
            b.append("<a class=\"row\" href=\"/g/").append(esc(g.id)).append("\"><b>").append(esc(g.title)).append("</b><span class=\"mute\">by ")
                    .append(esc(g.ownerName)).append(stars(g)).append("</span></a>");
        }
        return shell(name + " · SunWhere", "Guides for the sun and the moon in " + name + ".", cfg.publicUrl + "/c/" + Text.url(slug), null, b.toString());
    }

    private static String stars(Store.Guide g) {
        if (g.votes == 0) return "";
        return String.format(Locale.ROOT, " · %.1f of 5 (%d)", g.avg, g.votes);
    }

    /** The page behind a shared link: the card as the preview picture, the words, the photos, a key to open in the app. */
    String guide(Store.Guide g, boolean hasCard, int photos) {
        String url = cfg.publicUrl + "/g/" + g.id;
        String image = hasCard ? cfg.publicUrl + "/g/" + g.id + "/card.png?v=" + g.updated : null;
        String desc = g.summary.isEmpty() ? g.title + ", " + g.city + ". Where to stand and when, with SunWhere." : g.summary;
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(g.title)).append("</h1><p class=\"mute\"><a href=\"/c/").append(Text.url(g.citySlug)).append("\">")
                .append(esc(g.city)).append("</a> · ").append(Text.MONTHS[g.month - 1]).append(' ').append(g.year).append(" · by ")
                .append(esc(g.ownerName)).append(stars(g)).append("</p>");
        if (hasCard) b.append("<img class=\"card\" alt=\"\" src=\"").append(esc(image)).append("\">");
        if (!g.summary.isEmpty()) b.append("<p>").append(esc(g.summary)).append("</p>");
        if (photos > 0) {
            b.append("<div class=\"photos\">");
            for (int i = 1; i <= photos; i++)
                b.append("<img alt=\"Result photo ").append(i).append("\" src=\"/g/").append(esc(g.id)).append("/photo/").append(i).append(".jpg?v=").append(g.updated).append("\">");
            b.append("</div>");
        }
        try {
            JsonNode shots = json.readTree(g.shotsJson);
            b.append("<h2>").append(shots.size() == 1 ? "The shot" : "The shots").append("</h2>");
            for (JsonNode s : shots) {
                String code = s.path("code").asText("");
                b.append("<div class=\"shot\"><b>").append(esc(s.path("name").asText("Shot"))).append("</b><br>")
                        .append("<a class=\"btn\" href=\"").append(esc(cfg.appLink + "#" + code)).append("\">Open in SunWhere</a>")
                        .append("<a class=\"btn alt\" href=\"sunwhere://shot?d=").append(esc(code)).append("\">Open the app directly</a></div>");
            }
        } catch (java.io.IOException e) {
            // the shots were checked when they were saved; show the page without them
        }
        return shell(g.title + " · " + g.city + " · SunWhere", desc, url, image, b.toString());
    }

    String notFound() {
        return shell("Not found · SunWhere", "This page is not here.", cfg.publicUrl + "/", null, "<h1>Not found</h1><p class=\"mute\">This guide is gone or was never here.</p>");
    }
}
