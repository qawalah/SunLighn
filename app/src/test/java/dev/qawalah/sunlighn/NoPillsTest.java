package dev.qawalah.sunlighn;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/** Pills are gone for good (owner, 2026-11-15): a pick from a list is a dropdown. */
public class NoPillsTest {
    @Test public void noChipCalls() throws Exception {
        File dir = new File("src/main/java/dev/qawalah/sunlighn");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".java"));
        for (File f : files) {
            String s = new String(Files.readAllBytes(f.toPath()), "UTF-8");
            assertFalse(f.getName() + " still makes a pill", s.contains("Ui.chip(") || s.contains(" chip(Context"));
        }
    }
}
