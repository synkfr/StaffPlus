package me.ayosynk.stuff;

import me.ayosynk.stuff.config.PluginConfig;
import me.ayosynk.stuff.config.MessageConfig;
import me.ayosynk.stuff.database.DatabaseManager;

import java.io.File;
import java.util.logging.Logger;

/**
 * Platform abstraction layer enabling stuff-core to run on both Bukkit and Velocity.
 * Each platform module implements this interface to provide runtime-specific services.
 */
public interface StuffPlatform {

    /**
     * Gets the platform-specific logger.
     */
    Logger getLogger();

    /**
     * Gets the platform's data folder (where configs and databases are stored).
     */
    File getDataFolder();

    /**
     * Gets the plugin configuration.
     */
    PluginConfig getPluginConfig();

    /**
     * Gets the message configuration.
     */
    MessageConfig getMessageConfig();

    /**
     * Gets the shared database manager.
     */
    DatabaseManager getDatabaseManager();

    /**
     * Dispatches a console command on the platform (used for warning escalation ladder).
     */
    void dispatchConsoleCommand(String command);
}
