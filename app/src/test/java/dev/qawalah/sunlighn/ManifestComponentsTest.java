package dev.qawalah.sunlighn;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Android makes a manifest component with Class.newInstance from another package, so the class and its
 * no-argument constructor must be public. A package-private receiver crashes the app at the first broadcast
 * (3.19.0, 3.19.1: Reminders), and Robolectric does not enforce it.
 */
public class ManifestComponentsTest {
    @Test
    public void remindersReceiverIsPublic() throws Exception {
        assertTrue("Reminders must be public", Modifier.isPublic(Reminders.class.getModifiers()));
        Constructor<Reminders> c = Reminders.class.getDeclaredConstructor();
        assertTrue("Reminders() must be public", Modifier.isPublic(c.getModifiers()));
    }
}
