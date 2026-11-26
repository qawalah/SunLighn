package dev.qawalah.sunwhere.server;

import java.nio.file.Path;

/** Everything the server reads from its environment. One place, so the same image runs on Fly.io or any machine. */
public final class Config {
    public int port = 8080;
    public Path dataDir = Path.of("data");
    /** Where the pages live on the web, without a trailing slash (used in links and og:image). */
    public String publicUrl = "http://localhost:8080";
    /** The page that opens a shot in the app (the existing share page). */
    public String appLink = "https://qawalah.github.io/SunLighn/s/";
    public String adminToken = "";
    /** Behind Fly's proxy (or any): take the client's address from Fly-Client-IP / X-Forwarded-For. */
    public boolean trustProxy = false;
    /** A new account's listed guides show in the city pages only after this many seconds (a hold against spam). */
    public long holdSeconds = 600;
    public int reportsToHide = 3;
    public boolean backups = true;

    public static Config fromEnv() {
        Config c = new Config();
        c.port = intEnv("PORT", c.port);
        c.dataDir = Path.of(env("DATA_DIR", "data"));
        c.publicUrl = trimSlash(env("PUBLIC_URL", c.publicUrl));
        c.appLink = env("APP_LINK", c.appLink);
        c.adminToken = env("ADMIN_TOKEN", "");
        c.trustProxy = "true".equalsIgnoreCase(env("TRUST_PROXY", "false"));
        c.holdSeconds = intEnv("HOLD_SECONDS", (int) c.holdSeconds);
        return c;
    }

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v.trim();
    }

    private static int intEnv(String k, int d) {
        try {
            return Integer.parseInt(env(k, String.valueOf(d)));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
