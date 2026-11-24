package dev.qawalah.sunlighn;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shots as a file (3.28, owner: "a file that the app can use, for offline users"). A {@code .sunwhere} file is plain
 * text: a line saying what it is, then each shot's name and its {@code sunwhere://shot?d=CODE} link (the same code as
 * {@link ShotLink}). It travels by anything that carries a file (a chat, Bluetooth, Quick Share, a cable) and needs no
 * network or server. Opened, the app finds every code in it.
 */
final class ShotFile {
    private ShotFile() {}

    static final String EXT = ".sunwhere";
    static final String MIME = "application/octet-stream";
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_SHOTS = 200;
    private static final Pattern CODE = Pattern.compile("sunwhere://shot\\?d=([A-Za-z0-9_-]+)");

    /** The file's words for these shots. */
    static String text(List<Trip.Line> shots) {
        StringBuilder b = new StringBuilder();
        b.append(shots.size() == 1 ? "SunWhere shot" : "SunWhere shots (" + shots.size() + ")").append('\n');
        b.append("Open this file with SunWhere, or in SunWhere: Saved, Open a file.\n");
        for (Trip.Line l : shots) {
            b.append('\n');
            String name = l.name == null || l.name.isEmpty() ? "Shot" : l.name.replace('\n', ' ');
            b.append(name).append('\n');
            b.append(ShotLink.SCHEME).append("://shot?d=").append(ShotLink.encode(l)).append('\n');
        }
        return b.toString();
    }

    /** Every shot in a file's words (also a pasted link or the page's address with #CODE); empty when none. */
    static List<Trip.Line> parse(String text) {
        List<Trip.Line> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = CODE.matcher(text);
        while (m.find() && out.size() < MAX_SHOTS) {
            Trip.Line l = ShotLink.decode(m.group(1));
            if (l != null) out.add(l);
        }
        if (out.isEmpty()) {                                // the share page's address: .../s/#CODE
            Matcher h = Pattern.compile("/s/#(?:d=)?([A-Za-z0-9_-]+)").matcher(text);
            while (h.find() && out.size() < MAX_SHOTS) {
                Trip.Line l = ShotLink.decode(h.group(1));
                if (l != null) out.add(l);
            }
        }
        return out;
    }

    /** A file name for these shots: the shot's own name, or "SunWhere shots". */
    static String fileName(List<Trip.Line> shots) {
        String base = shots.size() == 1 && shots.get(0).name != null && !shots.get(0).name.isEmpty()
                ? shots.get(0).name : "SunWhere shots";
        base = base.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]+", " ").trim();
        if (base.length() > 60) base = base.substring(0, 60).trim();
        if (base.isEmpty()) base = "SunWhere shots";
        return base + EXT;
    }

    /** Writes the file and opens the phone's share menu with it. False when it could not be written or sent. */
    static boolean send(Activity a, List<Trip.Line> shots) {
        if (shots.isEmpty()) return false;
        File dir = new File(a.getCacheDir(), ShotFileProvider.DIR);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();         // only the latest is kept
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File f = new File(dir, fileName(shots));
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(text(shots).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
        Uri u = ShotFileProvider.uriFor(a, f.getName());
        Intent send = new Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, u)
                .putExtra(Intent.EXTRA_SUBJECT, f.getName())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(f.getName(), u));
        try {
            a.startActivity(Intent.createChooser(send, shots.size() == 1 ? "Send this shot as a file" : "Send the shots as a file"));
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        }
    }

    /** The words of a file another app handed us, or null when it cannot be read (or is far too big for a shot). */
    static String read(Context c, Uri u) {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) return null;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return null;
        }
    }
}
