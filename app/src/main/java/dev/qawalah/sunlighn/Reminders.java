package dev.qawalah.sunlighn;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reminders: a phone notification LEAD_MS before a sun or moon moment.
 * Each reminder is stored as "key|timeMs|title|text" in the app's preferences, so it survives a restart
 * (BOOT_COMPLETED sets the alarms again). The key ties it to its line by id: "<lineId>:<sun|moon>:<timeMs>".
 */
final class Reminders extends BroadcastReceiver {
    static final long LEAD_MS = 30 * 60 * 1000L;
    private static final String PREF = "reminders";
    private static final String CHANNEL = "moments";

    /** One reminder as stored. */
    static final class Item {
        final String key, title, text;
        final long timeMs;
        Item(String key, long timeMs, String title, String text) {
            this.key = key;
            this.timeMs = timeMs;
            this.title = title;
            this.text = text;
        }
        String line() { return key.substring(0, key.indexOf(':')); }
    }

    static String key(String lineId, boolean moon, long timeMs) {
        return lineId + ":" + (moon ? "moon" : "sun") + ":" + timeMs;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sunlighn", Context.MODE_PRIVATE);
    }

    static List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        for (String s : prefs(c).getStringSet(PREF, new HashSet<>())) {
            String[] f = s.split("\\|", 4);
            if (f.length == 4) {
                try {
                    out.add(new Item(f[0], Long.parseLong(f[1]), f[2], f[3]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    static boolean has(Context c, String key) {
        for (Item i : all(c)) if (i.key.equals(key)) return true;
        return false;
    }

    static void add(Context c, Item item) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(PREF, new HashSet<>()));
        set.add(item.key + "|" + item.timeMs + "|" + clean(item.title) + "|" + clean(item.text));
        prefs(c).edit().putStringSet(PREF, set).apply();
        schedule(c, item);
    }

    static void remove(Context c, String key) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(PREF, new HashSet<>()));
        set.removeIf(s -> s.startsWith(key + "|"));
        prefs(c).edit().putStringSet(PREF, set).apply();
        alarms(c).cancel(pending(c, key, null));
    }

    /** Removes every reminder of one line, and gives them back so Undo can put them back. */
    static List<Item> removeLine(Context c, String lineId) {
        List<Item> gone = new ArrayList<>();
        for (Item i : all(c)) {
            if (i.line().equals(lineId)) {
                gone.add(i);
                remove(c, i.key);
            }
        }
        return gone;
    }

    /** When the notification goes off: LEAD_MS before, or in a few seconds if that is already past. */
    static long fireAt(long timeMs) {
        return Math.max(System.currentTimeMillis() + 5000, timeMs - LEAD_MS);
    }

    private static String clean(String s) {
        return s.replace('|', '/');
    }

    private static AlarmManager alarms(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent pending(Context c, String key, Item item) {
        Intent i = new Intent(c, Reminders.class).setAction("dev.qawalah.sunlighn.REMIND." + key);
        if (item != null) i.putExtra("key", item.key).putExtra("title", item.title).putExtra("text", item.text);
        return PendingIntent.getBroadcast(c, key.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, Item item) {
        AlarmManager am = alarms(c);
        PendingIntent pi = pending(c, item.key, item);
        long at = fireAt(item.timeMs);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); // may be a few minutes late
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            // Alarms do not survive a restart or an update: set them again, and drop the ones long gone.
            for (Item i : all(c)) {
                if (i.timeMs < System.currentTimeMillis()) remove(c, i.key);
                else schedule(c, i);
            }
            return;
        }
        String key = intent.getStringExtra("key");
        if (key == null) return;
        show(c, key.hashCode(), intent.getStringExtra("title"), intent.getStringExtra("text"));
        remove(c, key);
    }

    @SuppressWarnings("deprecation")
    private static void show(Context c, int id, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders",
                    NotificationManager.IMPORTANCE_HIGH));
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL);
        }
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true);
        nm.notify(id, b.build());
    }
}
