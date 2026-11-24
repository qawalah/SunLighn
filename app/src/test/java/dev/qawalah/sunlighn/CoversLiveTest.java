package dev.qawalah.sunlighn;

import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** Asks Claude for real covers when COVER_KEY is set (an Anthropic key); saves them in build/screens/live-*.png. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CoversLiveTest {
    @Test public void realCovers() throws Exception {
        String key = System.getenv("COVER_KEY");
        assumeTrue(key != null && !key.isEmpty());
        String cities = System.getenv().getOrDefault("COVER_CITIES", "Riyadh:24.711:46.674;Dubai:25.197:55.274;Al Khobar:26.217:50.197");
        for (String c : cities.split(";")) {
            String[] f = c.split(":");
            Covers.Design d = Covers.claude(key, f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2]));
            if (d == null) { System.out.println(f[0] + ": " + Covers.lastError); continue; }
            StringBuilder names = new StringBuilder();
            for (Covers.Landmark l : d.landmarks) names.append(l.main ? "MAIN " : "minor ").append(l.name).append(" | ");
            System.out.println(f[0] + ": " + names + d.shapes.size() + " fillers");
            Bitmap b = Covers.drawn(d);
            File out = new File("build/screens/live-" + f[0].replace(' ', '_') + ".png");
            out.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        }
    }
}
