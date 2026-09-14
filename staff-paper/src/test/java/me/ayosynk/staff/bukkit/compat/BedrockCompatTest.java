package me.ayosynk.staff.bukkit.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BedrockCompatTest {

    @Test
    public void testBedrockNotSupportedWhenNoPluginsInstalled() {
        assertFalse(BedrockFormManager.isBedrockSupported());
        assertFalse(BedrockFormManager.isBedrock(null));
    }
}
