package me.ayosynk.staff.bukkit;

import me.ayosynk.staff.utils.UpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    public void testIsNewerVersion() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.1"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.1.0"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "2.0.0"));
        assertTrue(UpdateChecker.isNewer("v1.0.0", "v1.0.1"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "v1.0.1"));
        assertTrue(UpdateChecker.isNewer("v1.0.0", "1.0.1"));
        assertTrue(UpdateChecker.isNewer("1.0.0-SNAPSHOT", "1.0.1"));

        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("2.0.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewer("", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", ""));
        assertFalse(UpdateChecker.isNewer(null, "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", null));
    }
}
