package dev.qawalah.sunlighn;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;

/** "Sun" never stands for Sunday: no date pattern shortens the weekday to three letters (3.3, review). */
public class NoShortWeekdayTest {
    @Test public void noThreeLetterWeekdays() throws Exception {
        Pattern eee = Pattern.compile("\"[^\"]*(?<!E)EEE(?!E)[^\"]*\"");
        File dir = new File("src/main/java/dev/qawalah/sunlighn");
        for (File f : dir.listFiles((d, n) -> n.endsWith(".java"))) {
            String s = new String(Files.readAllBytes(f.toPath()), "UTF-8");
            assertFalse(f.getName() + " shortens a weekday", eee.matcher(s).find());
        }
    }
}
