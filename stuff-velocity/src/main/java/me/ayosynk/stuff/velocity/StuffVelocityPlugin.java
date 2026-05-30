package me.ayosynk.stuff.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;

import me.ayosynk.stuff.StuffPlatform;
import me.ayosynk.stuff.config.MessageConfig;
import me.ayosynk.stuff.config.PluginConfig;
import me.ayosynk.stuff.database.DatabaseManager;
import me.ayosynk.stuff.velocity.commands.VelocityPunishCommand;
import me.ayosynk.stuff.velocity.listeners.VelocityListeners;

import org.bstats.velocity.Metrics;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Plugin(
    id = "stuffplus",
    name = "Stuff+",
    version = "1.0.0",
    description = "Network-wide moderation plugin for Velocity proxies",
    authors = {"me.ayosynk", "Antigravity"}
)
public class StuffVelocityPlugin implements StuffPlatform {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private PluginConfig pluginConfig;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;
    private me.ayosynk.stuff.migration.MigrationManager migrationManager;

    private final Set<String> registeredNames = ConcurrentHashMap.newKeySet();

    @Inject
    public StuffVelocityPlugin(ProxyServer server, org.slf4j.Logger slf4jLogger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        // Bridge SLF4J to java.util.logging for StuffPlatform compatibility
        this.logger = java.util.logging.Logger.getLogger("Stuff+");
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize Data Folder
        File dataFolder = dataDirectory.toFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Initialize Configs
        try {
            this.pluginConfig = ConfigManager.create(PluginConfig.class, (it) -> {
                it.withConfigurer(new YamlSnakeYamlConfigurer());
                it.withBindFile(new File(dataFolder, "config.yml"));
                it.saveDefaults();
                it.load(true);
            });

            this.messageConfig = ConfigManager.create(MessageConfig.class, (it) -> {
                it.withConfigurer(new YamlSnakeYamlConfigurer());
                it.withBindFile(new File(dataFolder, "messages.yml"));
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            logger.severe("Could not load configurations! Plugin will not function.");
            e.printStackTrace();
            return;
        }

        // Initialize Database
        try {
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.init();
        } catch (Exception e) {
            logger.severe("Could not initialize database! Plugin will not function.");
            e.printStackTrace();
            return;
        }

        // Load name cache
        databaseManager.getAllRegisteredNames().thenAccept(names -> {
            registeredNames.addAll(names);
            logger.info("Cached " + names.size() + " offline player names for tab completion.");
        });

        // Initialize Migration System
        this.migrationManager = new me.ayosynk.stuff.migration.MigrationManager(this);
        this.migrationManager.init();

        // Register Commands
        registerCommands();

        // Register Listeners
        server.getEventManager().register(this, new VelocityListeners(this));

        // Initialize bStats Metrics (Plugin ID: 31693)
        metricsFactory.make(this, 31693);

        logger.info("Stuff+ Velocity Plugin has been successfully enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        logger.info("Stuff+ Velocity Plugin has been disabled.");
    }

    private void registerCommands() {
        CommandManager cm = server.getCommandManager();
        VelocityPunishCommand punishCommand = new VelocityPunishCommand(this);

        // Register all punishment commands
        String[] punishCommands = {"ban", "tempban", "unban", "ip-ban", "tempip-ban", "unip-ban",
                "mute", "tempmute", "unmute", "warn", "warns",
                "history", "staffhistory", "staffrollback", "stuffallow", "stuffimport"};

        for (String cmd : punishCommands) {
            var meta = cm.metaBuilder(cmd).plugin(this).build();
            cm.register(meta, punishCommand);
        }
    }

    // ==========================================
    // StuffPlatform Implementation
    // ==========================================

    @Override
    public Logger getLogger() { return logger; }

    @Override
    public File getDataFolder() { return dataDirectory.toFile(); }

    @Override
    public PluginConfig getPluginConfig() { return pluginConfig; }

    @Override
    public MessageConfig getMessageConfig() { return messageConfig; }

    @Override
    public DatabaseManager getDatabaseManager() { return databaseManager; }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    // ==========================================
    // Getters
    // ==========================================

    public ProxyServer getServer() { return server; }

    public me.ayosynk.stuff.migration.MigrationManager getMigrationManager() { return migrationManager; }

    public Set<String> getRegisteredNames() { return registeredNames; }

    public void cacheName(String name) { registeredNames.add(name); }
}
