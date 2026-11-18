package dev.qawalah.sunlighn;

import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A shot as a link (3.18, owner: "make the shots shareable by a link that the other person can get and save"). The
 * shot's own words (line, moment, name, subject, framing) are packed into one short code and sent as a web address
 * whose page opens the app ({@code docs/s/index.html}), or straight as {@code sunwhere://shot?d=CODE}. No server and no
 * account: everything the other phone needs is in the code.
 */
final class ShotLink {
    private ShotLink() {}

    /** The page that hands the code to the app (GitHub Pages of this repository); the code is after the #. */
    static final String PAGE = "https://qawalah.github.io/SunLighn/s/";
    static final String SCHEME = "sunwhere";
    private static final String VERSION = "1";

    static String url(Trip.Line l) { return PAGE + "#" + encode(l); }

    /** The code for a shot: URL-safe, deflated. */
    static String encode(Trip.Line l) {
        String text = String.join("|", VERSION, enc(l.name), num(l.youLat), num(l.youLon), num(l.subLat), num(l.subLon),
                String.valueOf(l.mode), num(l.height), String.valueOf(l.kind), String.valueOf(l.timeMs), l.moon ? "1" : "0",
                enc(l.subjectName), num(l.panDeg), Double.isNaN(l.pitch) ? "" : num(l.pitch));
        byte[] in = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Deflater d = new Deflater(9, true);
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    /** The shot a code holds, with a new id, or null when it is not one of ours. */
    static Trip.Line decode(String code) {
        if (code == null || code.isEmpty()) return null;
        try {
            byte[] raw = Base64.decode(code, Base64.URL_SAFE);
            Inflater inf = new Inflater(true);
            inf.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 4096) return null;            // not a shot
            }
            inf.end();
            String[] f = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length < 14 || !f[0].equals(VERSION)) return null;
            String name = dec(f[1]);
            Trip.Line l = new Trip.Line(UUID.randomUUID().toString(), name, Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                    Double.parseDouble(f[4]), Double.parseDouble(f[5]), Integer.parseInt(f[6]) == 1 ? 1 : 0);
            if (Math.abs(l.youLat) > 90 || Math.abs(l.subLat) > 90 || Math.abs(l.youLon) > 180 || Math.abs(l.subLon) > 180) return null;
            l.height = Math.max(0, Math.min(9000, Double.parseDouble(f[7])));
            l.kind = Math.max(0, Math.min(FrameMath.KINDS.length - 1, Integer.parseInt(f[8])));
            l.timeMs = Math.max(0, Long.parseLong(f[9]));
            l.moon = f[10].equals("1");
            String subj = dec(f[11]);
            l.subjectName = subj.isEmpty() ? null : subj;
            l.panDeg = Double.parseDouble(f[12]);
            l.pitch = f[13].isEmpty() ? Double.NaN : Double.parseDouble(f[13]);
            l.named = !name.isEmpty();
            return l;
        } catch (IllegalArgumentException | DataFormatException | java.io.UnsupportedEncodingException e) {
            return null;                                    // includes NumberFormatException
        }
    }

    /** The code in a link we were opened with: {@code sunwhere://shot?d=CODE} or the page's address with #CODE. */
    static String codeOf(Uri u) {
        if (u == null) return null;
        String d = u.getQueryParameter("d");
        if (d != null && !d.isEmpty()) return d;
        String f = u.getFragment();
        if (f != null && f.startsWith("d=")) f = f.substring(2);
        return f == null || f.isEmpty() ? null : f;
    }

    private static String num(double v) { return String.format(Locale.US, "%.7f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }

    private static String enc(String s) {
        try {
            return s == null ? "" : URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String dec(String s) throws java.io.UnsupportedEncodingException {
        return URLDecoder.decode(s, "UTF-8");
    }
}
