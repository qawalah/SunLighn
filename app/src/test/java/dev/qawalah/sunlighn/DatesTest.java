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

    @Test public void rangesShareTheMonth() {
        Calendar a = Calendar.getInstance(LONDON);
        a.set(2026, Calendar.NOVEMBER, 18, 12, 0, 0);
        Calendar b = Calendar.getInstance(LONDON);
        b.set(2026, Calendar.NOVEMBER, 20, 12, 0, 0);
        if (a.getTimeInMillis() - System.currentTimeMillis() < 2 * 86400000L) return;   // only while these days are ahead
        assertEquals("Nov 18 to 20", Fmt.days(c, a.getTimeInMillis(), b.getTimeInMillis(), LONDON));
    }

    @Test public void rangesAcrossMonths() {
        Calendar a = Calendar.getInstance(LONDON);
        a.set(2026, Calendar.NOVEMBER, 30, 12, 0, 0);
        Calendar b = Calendar.getInstance(LONDON);
        b.set(2026, Calendar.DECEMBER, 2, 12, 0, 0);
        if (a.getTimeInMillis() - System.currentTimeMillis() < 2 * 86400000L) return;
        assertEquals("Nov 30 to Dec 2", Fmt.days(c, a.getTimeInMillis(), b.getTimeInMillis(), LONDON));
    }

    @Test public void rangesFromTomorrow() {
        assertTrue(Fmt.days(c, inDays(1, 12), inDays(3, 12), LONDON).startsWith("Tomorrow to "));
    }
}
