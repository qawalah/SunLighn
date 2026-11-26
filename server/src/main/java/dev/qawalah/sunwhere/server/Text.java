package dev.qawalah.sunwhere.server;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** Small text helpers: slugs, escaping, random codes, hashes. */
final class Text {
    private Text() {}

    private static final SecureRandom RND = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no 0 O 1 I

    static String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "place" : n.length() > 60 ? n.substring(0, 60) : n;
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }

    static String token() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** A recovery code like K7QM-2XPD-9TRW: 12 letters and digits, 60 bits. */
    static String recoveryCode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(CODE_CHARS.charAt(RND.nextInt(CODE_CHARS.length())));
        }
        return b.toString();
    }

    static String normaliseRecovery(String s) { return s == null ? "" : s.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT); }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String id() {
        byte[] b = new byte[9];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{1,29}$");
    static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{8,40}$");
    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,3000}$");

    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};
}
