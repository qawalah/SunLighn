package dev.qawalah.sunlighn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.TimeZone;

/** Dates never say "Sun" for Sunday: on a moon answer, "Sun, Nov 22" read as the sun (Ali, 2026-11-15). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DatesTest {
    private static final TimeZone LONDON = TimeZone.getTimeZone("Europe/London");
    private final Context c = RuntimeEnvironment.getApplication();

    /** Noon at the place, n days from today. */
    private static long inDays(int n, int hour) {
        Calendar k = Calendar.getInstance(LONDON);
        k.add(Calendar.DAY_OF_YEAR, n);
        k.set(Calendar.HOUR_OF_DAY, hour);
        k.set(Calendar.MINUTE, 0);
        return k.getTimeInMillis();
    }

    @Test public void dayIsNeverSun() {
        for (int n = 2; n < 16; n++) {
            long t = inDays(n, 12);
            for (String s : new String[]{Fmt.day(c, t, LONDON), Fmt.dayShort(c, t, LONDON), Fmt.days(c, t, inDays(n + 2, 12), LONDON)}) {
                assertFalse(s, s.matches("^(Sun|Mon|Tue|Wed|Thu|Fri|Sat)[ ,].*"));
            }
        }
    }

    @Test public void oneDayHasItsFullWeekday() {
        long t = inDays(4, 12);
        Calendar k = Calendar.getInstance(LONDON);
        k.setTimeInMillis(t);
        String weekday = k.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault());
        assertTrue(Fmt.day(c, t, LONDON), Fmt.day(c, t, LONDON).startsWith(weekday + ", "));
        assertEquals("Tomorrow", Fmt.day(c, inDays(1, 12), LONDON));
    }

    @Test public void shortDayHasNoWeekday() {
        long t = inDays(4, 12);
        Calendar k = Calendar.getInstance(LONDON);
        k.setTimeInMillis(t);
        String month = k.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault());
        assertEquals(month + " " + k.get(Calendar.DAY_OF_MONTH), Fmt.dayShort(c, t, LONDON));
        assertTrue(Fmt.whenShort(c, t, LONDON).startsWith(month + " " + k.get(Calendar.DAY_OF_MONTH) + " · "));
    }

    /** The 18th to the 20th of a month at least five weeks ahead (so the test never goes empty). */
    private static long[] aheadRange(int months, int from, int to) {
        Calendar a = Calendar.getInstance(LONDON);
        a.add(Calendar.MONTH, months);
        a.set(Calendar.DAY_OF_MONTH, from);
        a.set(Calendar.HOUR_OF_DAY, 12);
        Calendar b = (Calendar) a.clone();
        b.set(Calendar.DAY_OF_MONTH, to);
        return new long[]{a.getTimeInMillis(), b.getTimeInMillis()};
    }

    @Test public void rangesShareTheMonth() {
        long[] r = aheadRange(2, 18, 20);
        String month = Fmt.format(c, "MMM", LONDON).format(new java.util.Date(r[0]));
        assertEquals(month + " 18 to 20", Fmt.days(c, r[0], r[1], LONDON));
    }

    @Test public void rangesAcrossMonths() {
        long[] r = aheadRange(2, 28, 28);
        Calendar b = Calendar.getInstance(LONDON);
        b.setTimeInMillis(r[0]);
        b.add(Calendar.DAY_OF_YEAR, 6);
        String m1 = Fmt.format(c, "MMM", LONDON).format(new java.util.Date(r[0]));
        String m2 = Fmt.format(c, "MMM", LONDON).format(b.getTime());
        assertEquals(m1 + " 28 to " + m2 + " " + b.get(Calendar.DAY_OF_MONTH), Fmt.days(c, r[0], b.getTimeInMillis(), LONDON));
    }

    /** Over 300 days away the year is said, once, and the days stay readable. */
    @Test public void farRangesSayTheYear() {
        long[] r = aheadRange(14, 10, 12);
        Calendar a = Calendar.getInstance(LONDON);
        a.setTimeInMillis(r[0]);
        String s = Fmt.days(c, r[0], r[1], LONDON);
        String year = Integer.toString(a.get(Calendar.YEAR));
        assertTrue(s, s.contains("10") && s.contains("12") && s.contains(year) && s.contains(" to "));
        assertEquals(s, s.indexOf(year), s.lastIndexOf(year));
    }

    /** Where the day comes before the month (en-GB), the range reads "18 to 20 Nov", never "18 Nov to 20". */
    @Test public void dayFirstLocales() {
        java.util.Locale was = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.UK);
        try {
            long[] r = aheadRange(2, 18, 20);
            String s = Fmt.days(c, r[0], r[1], LONDON);
            assertTrue(s, s.matches("18 to 20 \\S+"));
        } finally {
            java.util.Locale.setDefault(was);
        }
    }

    @Test public void rangesFromTomorrow() {
        assertTrue(Fmt.days(c, inDays(1, 12), inDays(3, 12), LONDON).startsWith("Tomorrow to "));
    }
}
