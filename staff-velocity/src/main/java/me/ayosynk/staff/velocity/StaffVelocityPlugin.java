package me.ayosynk.staff.velocity;

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

import me.ayosynk.staff.StaffPlatform;
import me.ayosynk.staff.config.MessageConfig;
import me.ayosynk.staff.config.PluginConfig;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.velocity.commands.VelocityPunishCommand;
import me.ayosynk.staff.velocity.listeners.VelocityListeners;

import org.bstats.velocity.Metrics;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Plugin(
    id = "staffplus",
    name = "Staff+",
    version = "1.2.2",
    description = "Network-wide moderation plugin for Velocity proxies",
    authors = {"me.ayosynk", "Antigravity"}
)
public class StaffVelocityPlugin implements StaffPlatform {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private PluginConfig pluginConfig;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;
    private me.ayosynk.staff.migration.MigrationManager migrationManager;
    private me.ayosynk.staff.utils.UpdateChecker updateChecker;

    private final Set<String> registeredNames = ConcurrentHashMap.newKeySet();

    @Inject
    public StaffVelocityPlugin(ProxyServer server, org.slf4j.Logger slf4jLogger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        // Bridge SLF4J to java.util.logging for StaffPlatform compatibility
        this.logger = java.util.logging.Logger.getLogger("Staff+");
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
            logger.severe("Could not initialize database: " + e.getMessage());
            e.printStackTrace();
        }

        // Load name cache
        databaseManager.getAllRegisteredNames().thenAccept(names -> {
            registeredNames.addAll(names);
            logger.info("Cached " + names.size() + " offline player names for tab completion.");
        });

        // Initialize Migration System
        this.migrationManager = new me.ayosynk.staff.migration.MigrationManager(this);
        this.migrationManager.init();

        // Register Commands
        registerCommands();

        // Register Listeners
        server.getEventManager().register(this, new VelocityListeners(this));

        // Initialize bStats Metrics (Plugin ID: 31693)
        metricsFactory.make(this, 31693);

        this.updateChecker = new me.ayosynk.staff.utils.UpdateChecker(this);
        this.updateChecker.checkForUpdates();

        logger.info("Staff+ Velocity Plugin has been successfully enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        logger.info("Staff+ Velocity Plugin has been disabled.");
    }

    private void registerCommands() {
        if (!pluginConfig.isVelocityProxyCommands()) {
            return;
        }

        CommandManager cm = server.getCommandManager();
        VelocityPunishCommand punishCommand = new VelocityPunishCommand(this);

        String[] punishCommands = {
                "ban", "tempban", "unban",
                "ip-ban", "ipban", "banip",
                "tempip-ban", "tempipban", "tempbanip",
                "unip-ban", "unipban", "unbanip",
                "mute", "tempmute", "unmute",
                "warn", "warns",
                "history", "punishhistory", "historylog",
                "staffhistory", "staffrollback", "rollbackstaff", "rollback",
                "staffallow", "allowip", "allow",
                "staffimport", "migrate", "staffmigrate",
                "bans", "banhistory", "banlist",
                "bansleaderboard", "banleaderboard", "staffleaderboard",
                "kick", "unwarn", "checkban", "bancheck",
                "staff", "staffplus", "staff+"
        };

        for (String cmd : punishCommands) {
            var meta = cm.metaBuilder(cmd).plugin(this).build();
            cm.register(meta, punishCommand);
        }
    }

    // ==========================================
    // StaffPlatform Implementation
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

    @Override
    public String getPluginVersion() {
        return server.getPluginManager().getPlugin("staffplus")
                .flatMap(p -> p.getDescription().getVersion())
                .orElse("1.2.2");
    }

    // ==========================================
    // Getters
    // ==========================================

    public ProxyServer getServer() { return server; }

    public me.ayosynk.staff.migration.MigrationManager getMigrationManager() { return migrationManager; }

    public Set<String> getRegisteredNames() { return registeredNames; }

    public void cacheName(String name) { registeredNames.add(name); }

    public me.ayosynk.staff.utils.UpdateChecker getUpdateChecker() { return updateChecker; }
}
