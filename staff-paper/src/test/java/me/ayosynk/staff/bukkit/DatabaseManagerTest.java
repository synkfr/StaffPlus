package me.ayosynk.staff.bukkit;

import me.ayosynk.staff.StaffPlatform;
import me.ayosynk.staff.config.MessageConfig;
import me.ayosynk.staff.config.PluginConfig;
import me.ayosynk.staff.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testSqliteInitializationAndSave() {
        PluginConfig config = new PluginConfig();
        StaffPlatform platform = createPlatform(tempDir.toFile(), config);

        db = new DatabaseManager(platform);
        db.init();

        assertFalse(db.isRemote());

        UUID testUuid = UUID.randomUUID();
        db.savePlayer(testUuid, "TestPlayer", "127.0.0.1", 10).join();

        DatabaseManager.PlayerRecord record = db.getPlayerRecord(testUuid).join();
        assertNotNull(record);
        assertEquals("TestPlayer", record.name);
        assertEquals("127.0.0.1", record.ip);
        assertEquals(10, db.getPlayerWeight(testUuid).join());
    }

    @Test
    public void testRemoteDatabaseFallbackToSqlite() {
        PluginConfig config = new PluginConfig();
        try {
            var storageField = PluginConfig.class.getDeclaredField("storageType");
            storageField.setAccessible(true);
            storageField.set(config, "mysql");

            var hostField = PluginConfig.class.getDeclaredField("mysqlHost");
            hostField.setAccessible(true);
            hostField.set(config, "127.0.0.1");

            var portField = PluginConfig.class.getDeclaredField("mysqlPort");
            portField.setAccessible(true);
            portField.set(config, 65534);
        } catch (Exception e) {
            fail(e);
        }

        StaffPlatform platform = createPlatform(tempDir.toFile(), config);
        db = new DatabaseManager(platform);
        assertDoesNotThrow(() -> db.init());

        assertFalse(db.isRemote());
        File sqliteFile = new File(tempDir.toFile(), "database.db");
        assertTrue(sqliteFile.exists());
    }

    private StaffPlatform createPlatform(File dataDir, PluginConfig config) {
        return new StaffPlatform() {
            private final Logger logger = Logger.getLogger("TestLogger");

            @Override
            public Logger getLogger() { return logger; }

            @Override
            public File getDataFolder() { return dataDir; }

            @Override
            public PluginConfig getPluginConfig() { return config; }

            @Override
            public MessageConfig getMessageConfig() { return null; }

            @Override
            public DatabaseManager getDatabaseManager() { return db; }

            @Override
            public void dispatchConsoleCommand(String command) {}

            @Override
            public String getPluginVersion() { return "1.2.2"; }
        };
    }
}
