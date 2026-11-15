package dev.qawalah.sunlighn;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Words for times, days and distances. Times are always said in the time zone of the place, not the phone. */
final class Fmt {
    private Fmt() {}

    static SimpleDateFormat format(Context c, String skeleton, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton),
                Locale.getDefault());
        f.setTimeZone(tz);
        return f;
    }

    static SimpleDateFormat timeFormat(Context c, TimeZone tz) {
        return format(c, DateFormat.is24HourFormat(c) ? "Hm" : "jm", tz);
    }

    static String time(Context c, long ms, TimeZone tz) {
        return timeFormat(c, tz).format(new Date(ms));
    }

    /** "Today", "Tomorrow" or "Sat, Nov 14", as the day is called at the place. */
    static String day(Context c, long ms, TimeZone tz) {
        Calendar now = Calendar.getInstance(tz), t = Calendar.getInstance(tz);
        t.setTimeInMillis(ms);
        if (sameDay(t, now)) return "Today";
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(t, now)) return "Tomorrow";
        now.add(Calendar.DAY_OF_YEAR, -2);
        if (sameDay(t, now)) return "Yesterday";
        // The year only when the day is far off (over 300 days): "Thu, Feb 25" stays short enough for one line.
        boolean far = Math.abs(ms - System.currentTimeMillis()) > 300L * 24 * 3600 * 1000;
        return format(c, far ? "EEEdMMMy" : "EEEdMMM", tz).format(new Date(ms));
    }

    static String when(Context c, long ms, TimeZone tz) {
        return day(c, ms, tz) + " · " + time(c, ms, tz);
    }

    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static long midnight(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "in 40 min", "in 5 h", "in 3 days"; empty for the past. */
    static String until(long ms, long now) {
        if (ms < now) return "";
        long min = (ms - now) / 60000;
        if (min < 1) return "now";
        if (min < 90) return "in " + min + " min";
        if (min < 36 * 60) return "in " + Math.round(min / 60.0) + " h";
        return "in " + Math.round(min / 1440.0) + " days";
    }

    static String distance(double metres) {
        if (metres < 1000) return (metres < 500 ? Math.round(metres) : Math.round(metres / 10) * 10) + " m";
        return String.format(Locale.US, metres < 10000 ? "%.1f km" : "%.0f km", metres / 1000);
    }

    /** "London time" from Europe/London; the phone's own zone needs no name. */
    static String zoneName(TimeZone tz) {
        String id = tz.getID();
        int i = id.lastIndexOf('/');
        String city = (i >= 0 ? id.substring(i + 1) : id).replace('_', ' ');
        return city + " time";
    }

    static String height(double m) {
        return m >= 10 ? Math.round(m) + " m" : String.format(Locale.US, "%.1f m", m);
    }

    /** "Today to Sat 21 Nov" for a run of days, or just the day when it is one. */
    static String days(Context c, long first, long last, TimeZone tz) {
        String a = day(c, first, tz), b = day(c, last, tz);
        return a.equals(b) ? a : a + " to " + b;
    }

    /** "16:24 to 16:26" for a run of days whose time drifts, or the one time. */
    static String times(Context c, long first, long last, TimeZone tz) {
        long[] o = byClock(first, last, tz);
        String a = time(c, o[0], tz), b = time(c, o[1], tz);
        return a.equals(b) ? a : a + " to " + b;
    }

    /** The two moments ordered by the time of day they fall at, earliest first (the days do not matter). */
    static long[] byClock(long a, long b, TimeZone tz) {
        long ma = (a + tz.getOffset(a)) % 86400000L, mb = (b + tz.getOffset(b)) % 86400000L;
        return ma <= mb ? new long[]{a, b} : new long[]{b, a};
    }
}
