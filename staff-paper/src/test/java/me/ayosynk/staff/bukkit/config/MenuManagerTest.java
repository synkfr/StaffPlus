package me.ayosynk.staff.bukkit.config;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.contains;

public class MenuManagerTest {

    @TempDir
    File tempDir;

    private StaffBukkitPlugin mockPlugin;
    private Logger mockLogger;

    @BeforeEach
    public void setUp() {
        mockPlugin = mock(StaffBukkitPlugin.class);
        mockLogger = mock(Logger.class);
        when(mockPlugin.getDataFolder()).thenReturn(tempDir);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);
    }

    @Test
    public void testGracefulFallbackOnInvalidYaml() throws IOException {
        File menusDir = new File(tempDir, "menus");
        menusDir.mkdirs();
        File yamlFile = new File(menusDir, "staff_info.yml");
        
        // Write invalid YAML syntax (broken indentations / colon errors)
        try (FileWriter writer = new FileWriter(yamlFile)) {
            writer.write("name: \"Staff Info\"\n");
            writer.write("size: 54\n");
            writer.write("  items:\n"); // Bad indentation
            writer.write("  border_item:\n");
            writer.write("  - missingcolon\n");
        }

        MenuManager menuManager = new MenuManager(mockPlugin);
        
        // Load configurations
        assertDoesNotThrow(menuManager::load);

        // Verify fallback defaults are used
        StaffInfoMenuConfig config = menuManager.getStaffInfoMenuConfig();
        assertNotNull(config);
        assertEquals("<color:#A0A0A0>Staff Info: <color:#00E262>{player}", config.title);
        assertEquals(54, config.size);
        assertTrue(config.items.containsKey("border_item"));
        
        // Verify logger warning was called
        verify(mockLogger, atLeastOnce()).warning(contains("YAML SYNTAX ERROR"));
    }

    @Test
    public void testValidationWarningsForInvalidValues() throws IOException {
        File menusDir = new File(tempDir, "menus");
        menusDir.mkdirs();
        File yamlFile = new File(menusDir, "staff_info.yml");

        // Write configuration with valid YAML syntax but invalid logical values
        try (FileWriter writer = new FileWriter(yamlFile)) {
            writer.write("name: \"Custom Info\"\n");
            writer.write("size: 50\n"); // Invalid size (not multiple of 9)
            writer.write("items:\n");
            writer.write("  close_gui:\n");
            writer.write("    slot: 99\n"); // Out of bounds slot
            writer.write("    item:\n");
            writer.write("      material: INVALID_MATERIAL_XYZ\n"); // Invalid material
        }

        MenuManager menuManager = new MenuManager(mockPlugin);

        assertDoesNotThrow(menuManager::load);

        StaffInfoMenuConfig config = menuManager.getStaffInfoMenuConfig();
        assertNotNull(config);
        
        // Should fall back to default size (54)
        assertEquals(54, config.size);

        // Verify invalid material fell back to default (BARRIER)
        StaffInfoMenuConfig.MenuItem closeGui = config.items.get("close_gui");
        assertNotNull(closeGui);
        assertEquals("BARRIER", closeGui.material);

        // Verify out of bounds slot fell back to default (49)
        assertEquals(49, closeGui.slot);

        // Verify logger was warned
        verify(mockLogger, atLeastOnce()).warning(contains("Invalid 'size'"));
        verify(mockLogger, atLeastOnce()).warning(contains("invalid material"));
        verify(mockLogger, atLeastOnce()).warning(contains("out of bounds slot"));
    }
}
