package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Shots as a .sunwhere file (3.28): what goes in comes out, several at once, and anything else holds none. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShotFileTest {
    private static Trip.Line shot(String name, boolean moon) {
        Trip.Line l = new Trip.Line("id-" + name, name, 26.28, 50.21, 26.29, 50.22, 0);
        l.height = 30;
        l.kind = 3;
        l.timeMs = 1763400000000L;
        l.moon = moon;
        return l;
    }

    @Test public void severalShotsComeOutAsTheyWentIn() {
        List<Trip.Line> in = Arrays.asList(shot("Water tower", false), shot("Corniche | north", true));
        String text = ShotFile.text(in);
        assertTrue(text.startsWith("SunWhere shots (2)"));
        List<Trip.Line> out = ShotFile.parse(text);
        assertEquals(2, out.size());
        assertEquals("Water tower", out.get(0).name);
        assertEquals("Corniche | north", out.get(1).name);
        assertTrue(out.get(1).moon);
        assertEquals(26.29, out.get(0).subLat, 1e-7);
    }

    @Test public void aPastedLinkOrThePageAddressIsReadToo() {
        String code = ShotLink.encode(shot("A", false));
        assertEquals(1, ShotFile.parse("look: sunwhere://shot?d=" + code + " nice").size());
        assertEquals(1, ShotFile.parse("https://qawalah.github.io/SunLighn/s/#" + code).size());
    }

    @Test public void otherFilesHoldNoShot() {
        assertTrue(ShotFile.parse("hello").isEmpty());
        assertTrue(ShotFile.parse(null).isEmpty());
        assertTrue(ShotFile.parse("sunwhere://shot?d=notacode").isEmpty());
    }

    @Test public void theFileNameIsSafe() {
        assertEquals("Big Ben Westminster.sunwhere",
                ShotFile.fileName(Collections.singletonList(shot("Big Ben/Westminster", false))));
        assertEquals("SunWhere shots.sunwhere", ShotFile.fileName(Arrays.asList(shot("a", false), shot("b", false))));
    }

    @Test public void aFileOnThePhoneIsRead() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        File f = new File(c.getCacheDir(), "t.sunwhere");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(ShotFile.text(Collections.singletonList(shot("Tower", false))).getBytes(StandardCharsets.UTF_8));
        }
        List<Trip.Line> out = ShotFile.parse(ShotFile.read(c, Uri.fromFile(f)));
        assertEquals(1, out.size());
        assertEquals("Tower", out.get(0).name);
    }
}
