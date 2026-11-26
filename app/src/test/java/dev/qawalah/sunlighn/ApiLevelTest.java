package dev.qawalah.sunlighn;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * minSdk is 24 and the tests run on a desktop JDK, so a call to a newer Android method passes every test and crashes the
 * phone (3.37 review: URLEncoder.encode(String, Charset) is Android 13). Keeps those calls out of the main sources.
 */
public class ApiLevelTest {
    @Test public void noUrlEncoderWithACharsetObject() throws Exception {
        Pattern bad = Pattern.compile("URLEncoder\\.(encode|decode)\\([^;]*StandardCharsets");
        StringBuilder found = new StringBuilder();
        scan(new File("src/main/java"), bad, found);
        assertEquals("These need the \"UTF-8\" String overload (the Charset one is Android 13):\n" + found, 0, found.length());
    }

    private void scan(File dir, Pattern bad, StringBuilder found) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) scan(f, bad, found);
            else if (f.getName().endsWith(".java")) {
                String text = new String(Files.readAllBytes(f.toPath()), "UTF-8");
                java.util.regex.Matcher m = bad.matcher(text);
                while (m.find()) found.append(f.getName()).append(": ").append(m.group().replace('\n', ' ')).append('\n');
            }
        }
    }
}
