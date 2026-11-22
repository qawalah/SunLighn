package dev.qawalah.sunlighn;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Keeps the last crash (3.19.1, owner: "the app keeps crashing", and no log to read): the stack is written to a file as
 * the app dies, and the next start shows it with Copy and Share. Failures the app survives (a shadow that could not be
 * drawn) are written the same way, marked "caught", so they can be read too.
 */
final class CrashLog {
    private CrashLog() {}

    private static final int KEEP = 6000;
    private static String lastWhere = "";
    private static long lastAt;

    private static File file(Context c) { return new File(c.getFilesDir(), "last-crash.txt"); }

    /** Call first in onCreate: from here an uncaught exception is written down before the app ends. */
    /** Ours, so a screen restart sees it is already there and adds no second layer (sweep C1). */
    private static final class Handler implements Thread.UncaughtExceptionHandler {
        final Context app;
        final Thread.UncaughtExceptionHandler old;
        Handler(Context app, Thread.UncaughtExceptionHandler old) { this.app = app; this.old = old; }
        @Override public void uncaughtException(Thread thread, Throwable e) {
            write(app, "CRASH", thread.getName(), e);
            if (old != null) old.uncaughtException(thread, e);
        }
    }

    static synchronized void install(Context c) {
        Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        if (old instanceof Handler) return;
        Thread.setDefaultUncaughtExceptionHandler(new Handler(c.getApplicationContext(), old));
    }

    /** A failure the app got over: written down, not shown as a crash. */
    static void caught(Context c, String where, Throwable e) {
        long now = System.currentTimeMillis();
        if (where.equals(lastWhere) && now - lastAt < 10000) return;      // drawn every frame: written once
        lastWhere = where;
        lastAt = now;
        write(c.getApplicationContext(), "caught", where, e);
    }

    private static void write(Context c, String kind, String where, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String version = "?";
            try {
                version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            String text = kind + " in " + where + " · SunWhere " + version + " · " + new Date() + " · Android "
                    + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MODEL + "\n" + sw;
            if (text.length() > KEEP) text = text.substring(0, KEEP);
            try (FileOutputStream out = new FileOutputStream(file(c))) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // writing a crash must never be the crash
        }
    }

    /** The text of the last crash, or null. */
    static String pending(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) Math.min(f.length(), KEEP * 4L)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = in.read(b);
                return n <= 0 ? null : new String(b, 0, n, StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static void clear(Context c) { file(c).delete(); }

    /** Shows what stopped the app last time, once; Copy and Share carry it away. */
    static void show(Context c) {
        final String text = pending(c);
        if (text == null) return;
        clear(c);
        String shown = text.length() > 1500 ? text.substring(0, 1500) + "…" : text;
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(text.startsWith("CRASH") ? "SunWhere stopped last time" : "Something went wrong (the app went on)")
                .setMessage(shown)
                .setPositiveButton("Share", (d, w) -> {
                    try {
                        c.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text), "Share the crash"));
                    } catch (Exception ignored) {
                    }
                })
                .setNeutralButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("SunWhere crash", text));
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
