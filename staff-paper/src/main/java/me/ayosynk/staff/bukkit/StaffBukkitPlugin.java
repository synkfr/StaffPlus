package me.ayosynk.staff.bukkit;

import me.ayosynk.staff.StaffPlatform;
import me.ayosynk.staff.config.MessageConfig;
import me.ayosynk.staff.config.PluginConfig;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class StaffBukkitPlugin extends JavaPlugin implements StaffPlatform {

    private PluginConfig pluginConfig;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;
    private me.ayosynk.staff.migration.MigrationManager migrationManager;
    private me.ayosynk.staff.bukkit.config.MenuManager menuManager;

    // Cache of all players ever seen (for tab completion)
    private final Set<String> registeredNames = ConcurrentHashMap.newKeySet();

    // Vanish State
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    // Monitor State: Staff UUID -> SpectatorState
    private final Map<UUID, SpectatorState> monitorStates = new ConcurrentHashMap<>();

    // Monitor Follow Tasks under Folia
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> monitorTasks = new ConcurrentHashMap<>();

    // Invsee Sessions: Staff UUID -> InvseeSession
    private final Map<UUID, InvseeSession> invseeSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Initialize Data Folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize Configs
        try {
            this.pluginConfig = ConfigManager.create(PluginConfig.class, (it) -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(new File(getDataFolder(), "config.yml"));
                it.saveDefaults();
                it.load(true);
            });

            this.messageConfig = ConfigManager.create(MessageConfig.class, (it) -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(new File(getDataFolder(), "messages.yml"));
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            getLogger().severe("Could not load configurations! Disabling plugin.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Menu Manager
        try {
            this.menuManager = new me.ayosynk.staff.bukkit.config.MenuManager(this);
            this.menuManager.load();
        } catch (Exception e) {
            getLogger().warning("Could not initialize menus: " + e.getMessage());
        }

        // Initialize Database
        try {
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Could not initialize database! Disabling plugin.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load name cache from database asynchronously
        databaseManager.getAllRegisteredNames().thenAccept(names -> {
            registeredNames.addAll(names);
            getLogger().info("Cached " + names.size() + " offline player names for tab completion.");
        });

        // Initialize Migration System
        this.migrationManager = new me.ayosynk.staff.migration.MigrationManager(this);
        this.migrationManager.init();

        // Register Listeners
        registerListeners();

        // Register Commands
        registerCommands();

        // Start Vanish Action Bar Task (Folia compatible)
        startVanishActionBarTask();

        // Initialize bStats Metrics (Shaded & Relocated)
        try {
            new Metrics(this, 31675);
            getLogger().info("bStats metrics enabled successfully (ID: 31675).");
        } catch (Exception e) {
            getLogger().warning("Could not initialize bStats metrics: " + e.getMessage());
        }

        getLogger().info("Staff+ Bukkit Plugin has been successfully enabled on Folia/Paper!");
    }

    @Override
    public void onDisable() {
        // Restore all monitored players before shutdown
        for (UUID uuid : monitorStates.keySet()) {
            Player staff = Bukkit.getPlayer(uuid);
            if (staff != null) {
                restoreSpectatorState(staff, false);
            }
        }
        monitorStates.clear();

        // Cancel all monitor follow tasks
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : monitorTasks.values()) {
            task.cancel();
        }
        monitorTasks.clear();

        // Shutdown database
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("Staff+ Bukkit Plugin has been disabled.");
    }

    // ==========================================
    // StaffPlatform Implementation
    // ==========================================

    @Override
    public PluginConfig getPluginConfig() { return pluginConfig; }

    @Override
    public MessageConfig getMessageConfig() { return messageConfig; }

    @Override
    public DatabaseManager getDatabaseManager() { return databaseManager; }

    @Override
    public void dispatchConsoleCommand(String command) {
        SchedulerUtils.runGlobal(this, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );
    }

    // ==========================================
    // Registration
    // ==========================================

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new me.ayosynk.staff.bukkit.listeners.PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new me.ayosynk.staff.bukkit.listeners.VanishListener(this), this);
    }

    private void registerCommands() {
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();

        me.ayosynk.staff.bukkit.commands.PunishCommand punishCommand = new me.ayosynk.staff.bukkit.commands.PunishCommand(this);
        registerDynamic(commandMap, "mute", "Mutes a player.", "/mute <player> [reason]", Collections.singletonList("silence"), punishCommand, punishCommand);
        registerDynamic(commandMap, "tempmute", "Temporarily mutes a player.", "/tempmute <player> <time> [reason]", Collections.emptyList(), punishCommand, punishCommand);
        registerDynamic(commandMap, "unmute", "Unmutes a player.", "/unmute <player>", Collections.emptyList(), punishCommand, punishCommand);

        registerDynamic(commandMap, "ban", "Bans a player.", "/ban <player> [reason]", Collections.emptyList(), punishCommand, punishCommand);
        registerDynamic(commandMap, "tempban", "Temporarily bans a player.", "/tempban <player> <time> [reason]", Collections.emptyList(), punishCommand, punishCommand);
        registerDynamic(commandMap, "unban", "Unbans a player.", "/unban <player>", Collections.emptyList(), punishCommand, punishCommand);

        registerDynamic(commandMap, "ip-ban", "IP-bans a player.", "/ip-ban <player> [reason]", Arrays.asList("ipban", "banip"), punishCommand, punishCommand);
        registerDynamic(commandMap, "tempip-ban", "Temporarily IP-bans a player.", "/tempip-ban <player> <time> [reason]", Arrays.asList("tempipban", "tempbanip"), punishCommand, punishCommand);
        registerDynamic(commandMap, "unip-ban", "Unbans an IP address.", "/unip-ban <player/IP>", Arrays.asList("unipban", "unbanip"), punishCommand, punishCommand);

        registerDynamic(commandMap, "warn", "Warns a player.", "/warn <player> [reason]", Collections.emptyList(), punishCommand, punishCommand);
        registerDynamic(commandMap, "warns", "View or clear warnings.", "/warns <player> [clear/list]", Collections.emptyList(), punishCommand, punishCommand);

        me.ayosynk.staff.bukkit.commands.VanishCommand vanishCommand = new me.ayosynk.staff.bukkit.commands.VanishCommand(this);
        registerDynamic(commandMap, "vanish", "Toggle vanish mode.", "/vanish", Arrays.asList("v", "vmode"), vanishCommand, vanishCommand);

        me.ayosynk.staff.bukkit.commands.MonitorCommand monitorCommand = new me.ayosynk.staff.bukkit.commands.MonitorCommand(this);
        registerDynamic(commandMap, "monitor", "Spectate and follow a player.", "/monitor <player/leave>", Arrays.asList("spectate", "mon"), monitorCommand, monitorCommand);

        me.ayosynk.staff.bukkit.commands.InvseeCommand invseeCommand = new me.ayosynk.staff.bukkit.commands.InvseeCommand(this);
        registerDynamic(commandMap, "invsee", "Inspect a player's inventory live.", "/invsee <player>", Arrays.asList("inspect", "inv"), invseeCommand, invseeCommand);

        // Gamemode shortcuts and Fly commands
        me.ayosynk.staff.bukkit.commands.GamemodeCommand gmCommand = new me.ayosynk.staff.bukkit.commands.GamemodeCommand(this);
        registerDynamic(commandMap, "gmc", "Switch to creative mode.", "/gmc [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gms", "Switch to survival mode.", "/gms [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gmsp", "Switch to spectator mode.", "/gmsp [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gma", "Switch to adventure mode.", "/gma [player]", Collections.emptyList(), gmCommand, gmCommand);

        me.ayosynk.staff.bukkit.commands.FlyCommand flyCommand = new me.ayosynk.staff.bukkit.commands.FlyCommand(this);
        registerDynamic(commandMap, "fly", "Toggle player fly mode.", "/fly [player]", Collections.singletonList("flight"), flyCommand, flyCommand);

        me.ayosynk.staff.bukkit.commands.HistoryCommand historyCommand = new me.ayosynk.staff.bukkit.commands.HistoryCommand(this);
        registerDynamic(commandMap, "history", "View punishment history for players.", "/history <player>", Arrays.asList("punishhistory", "historylog"), historyCommand, historyCommand);
        registerDynamic(commandMap, "staffhistory", "View punishments issued by staff members.", "/staffhistory <staff>", Collections.emptyList(), historyCommand, historyCommand);

        me.ayosynk.staff.bukkit.commands.StaffRollbackCommand rollbackCommand = new me.ayosynk.staff.bukkit.commands.StaffRollbackCommand(this);
        registerDynamic(commandMap, "staffrollback", "Rollback all punishments issued by staff.", "/staffrollback <staff> [confirm]", Arrays.asList("rollbackstaff", "rollback"), rollbackCommand, rollbackCommand);

        registerDynamic(commandMap, "staffallow", "Exempt a player from IP bans.", "/staffallow <player> [remove]", Arrays.asList("allowip", "allow"), punishCommand, punishCommand);

        me.ayosynk.staff.bukkit.commands.ImportCommand importCommand = new me.ayosynk.staff.bukkit.commands.ImportCommand(this, this.migrationManager);
        registerDynamic(commandMap, "staffimport", "Import punishments from other plugins.", "/staffimport <source> [params...]", Arrays.asList("migrate", "staffmigrate"), importCommand, importCommand);

        me.ayosynk.staff.bukkit.commands.StaffCommand staffCommand = new me.ayosynk.staff.bukkit.commands.StaffCommand(this);
        registerDynamic(commandMap, "staff", "Access staff utility subcommands.", "/staff <subcommand>", Collections.emptyList(), staffCommand, staffCommand);
    }

    private void registerDynamic(org.bukkit.command.CommandMap commandMap, String name, String description, String usage, List<String> aliases, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        me.ayosynk.staff.bukkit.commands.DynamicCommand command = new me.ayosynk.staff.bukkit.commands.DynamicCommand(name, description, usage, aliases, executor, tabCompleter);
        command.setPermission("staff." + name.replace("-", ""));
        command.setPermissionMessage(messageConfig.getNoPermission());
        commandMap.register("staff", command);
    }

    private void startVanishActionBarTask() {
        SchedulerUtils.runAsyncRepeating(this, () -> {
            for (UUID uuid : vanishedPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendActionBar(MiniMessageUtils.parse(messageConfig.getVanishActionBar()));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // Getters
    public me.ayosynk.staff.migration.MigrationManager getMigrationManager() { return migrationManager; }
    public me.ayosynk.staff.bukkit.config.MenuManager getMenuManager() { return menuManager; }

    // Cache operations
    public Set<String> getRegisteredNames() { return registeredNames; }
    public void cacheName(String name) { registeredNames.add(name); }

    // Vanish state
    public Set<UUID> getVanishedPlayers() { return vanishedPlayers; }
    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }
    public void setVanished(UUID uuid, boolean vanish) {
        if (vanish) vanishedPlayers.add(uuid);
        else vanishedPlayers.remove(uuid);
    }

    public List<Player> getVisibleOnlinePlayers(org.bukkit.command.CommandSender sender) {
        boolean canSeeVanished = sender.hasPermission("staff.vanish.see");
        List<Player> list = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isVanished(p.getUniqueId()) && !canSeeVanished) {
                continue;
            }
            list.add(p);
        }
        return list;
    }

    public boolean canSee(org.bukkit.command.CommandSender sender, Player target) {
        if (target == null) return false;
        if (isVanished(target.getUniqueId())) {
            return sender.hasPermission("staff.vanish.see");
        }
        return true;
    }

    // Monitor State structures
    public Map<UUID, SpectatorState> getMonitorStates() { return monitorStates; }
    public Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> getMonitorTasks() { return monitorTasks; }
    public Map<UUID, InvseeSession> getInvseeSessions() { return invseeSessions; }

    public static class InvseeSession {
        private final UUID staffUuid;
        private final UUID targetUuid;
        private final org.bukkit.inventory.Inventory inventory;

        public InvseeSession(UUID staffUuid, UUID targetUuid, org.bukkit.inventory.Inventory inventory) {
            this.staffUuid = staffUuid;
            this.targetUuid = targetUuid;
            this.inventory = inventory;
        }

        public UUID getStaffUuid() { return staffUuid; }
        public UUID getTargetUuid() { return targetUuid; }
        public org.bukkit.inventory.Inventory getInventory() { return inventory; }
    }

    public static class SpectatorState {
        private final Location originalLocation;
        private final GameMode originalGameMode;
        private final ItemStack[] inventoryContents;
        private final ItemStack[] armorContents;
        private final UUID targetUuid;
        private Vector relativeOffset;
        private Location lastStaffLocation;
        private Location lastTargetLocation;
        private boolean teleporting = false;
        private int followCooldown = 0;

        public SpectatorState(Location originalLocation, GameMode originalGameMode, ItemStack[] inventoryContents, ItemStack[] armorContents, UUID targetUuid) {
            this.originalLocation = originalLocation;
            this.originalGameMode = originalGameMode;
            this.inventoryContents = inventoryContents;
            this.armorContents = armorContents;
            this.targetUuid = targetUuid;
        }

        public Location getOriginalLocation() { return originalLocation; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
        public ItemStack[] getInventoryContents() { return inventoryContents; }
        public ItemStack[] getArmorContents() { return armorContents; }
        public UUID getTargetUuid() { return targetUuid; }
        public Vector getRelativeOffset() { return relativeOffset; }
        public void setRelativeOffset(Vector relativeOffset) { this.relativeOffset = relativeOffset; }
        public Location getLastStaffLocation() { return lastStaffLocation; }
        public void setLastStaffLocation(Location lastStaffLocation) { this.lastStaffLocation = lastStaffLocation; }
        public Location getLastTargetLocation() { return lastTargetLocation; }
        public void setLastTargetLocation(Location lastTargetLocation) { this.lastTargetLocation = lastTargetLocation; }
        public boolean isTeleporting() { return teleporting; }
        public void setTeleporting(boolean teleporting) { this.teleporting = teleporting; }
        public void tickFollowCooldown() { if (followCooldown > 0) followCooldown--; }
        public void resetFollowCooldown() { this.followCooldown = 2; }
        public boolean isFollowReady() { return followCooldown <= 0; }
    }

    public void restoreSpectatorState(Player staff, boolean asyncSafe) {
        SpectatorState state = monitorStates.remove(staff.getUniqueId());

        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = monitorTasks.remove(staff.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        if (state == null) return;

        Runnable restoreAction = () -> {
            staff.setSpectatorTarget(null);
            staff.setGameMode(state.getOriginalGameMode());
            staff.getInventory().setContents(state.getInventoryContents());
            staff.getInventory().setArmorContents(state.getArmorContents());
            staff.teleportAsync(state.getOriginalLocation());
        };

        if (asyncSafe) {
            SchedulerUtils.runEntity(this, staff, restoreAction);
        } else {
            restoreAction.run();
        }
    }
}
