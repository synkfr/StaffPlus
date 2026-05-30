package me.ayosynk.stuff;

import me.ayosynk.stuff.config.MessageConfig;
import me.ayosynk.stuff.config.PluginConfig;
import me.ayosynk.stuff.database.DatabaseManager;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class StuffPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private MessageConfig messageConfig;
    private DatabaseManager databaseManager;

    // Cache of all players ever seen (for tab completion)
    private final Set<String> registeredNames = ConcurrentHashMap.newKeySet();

    // Vanish State (Persistent by UUID, optional database backup can be done, but in-memory with reload support is standard. Let's make it robust)
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    // Monitor State: Staff UUID -> SpectatorState (stores original gamemode, location, and inventory)
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

        getLogger().info("Stuff+ Plugin has been successfully enabled on Folia/Paper!");
    }

    @Override
    public void onDisable() {
        // Restore all monitored players before shutdown to prevent them from getting stuck!
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

        getLogger().info("Stuff+ Plugin has been disabled.");
    }

    private void registerListeners() {
        // We will write and register PlayerListener and VanishListener later
        getServer().getPluginManager().registerEvents(new me.ayosynk.stuff.listeners.PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new me.ayosynk.stuff.listeners.VanishListener(this), this);
    }

    private void registerCommands() {
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();
        
        me.ayosynk.stuff.commands.PunishCommand punishCommand = new me.ayosynk.stuff.commands.PunishCommand(this);
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

        me.ayosynk.stuff.commands.VanishCommand vanishCommand = new me.ayosynk.stuff.commands.VanishCommand(this);
        registerDynamic(commandMap, "vanish", "Toggle vanish mode.", "/vanish", Arrays.asList("v", "vmode"), vanishCommand, vanishCommand);

        me.ayosynk.stuff.commands.MonitorCommand monitorCommand = new me.ayosynk.stuff.commands.MonitorCommand(this);
        registerDynamic(commandMap, "monitor", "Spectate and follow a player.", "/monitor <player/leave>", Arrays.asList("spectate", "mon"), monitorCommand, monitorCommand);

        me.ayosynk.stuff.commands.InvseeCommand invseeCommand = new me.ayosynk.stuff.commands.InvseeCommand(this);
        registerDynamic(commandMap, "invsee", "Inspect a player's inventory live.", "/invsee <player>", Arrays.asList("inspect", "inv"), invseeCommand, invseeCommand);

        // Gamemode shortcuts and Fly commands
        me.ayosynk.stuff.commands.GamemodeCommand gmCommand = new me.ayosynk.stuff.commands.GamemodeCommand(this);
        registerDynamic(commandMap, "gmc", "Switch to creative mode.", "/gmc [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gms", "Switch to survival mode.", "/gms [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gmsp", "Switch to spectator mode.", "/gmsp [player]", Collections.emptyList(), gmCommand, gmCommand);
        registerDynamic(commandMap, "gma", "Switch to adventure mode.", "/gma [player]", Collections.emptyList(), gmCommand, gmCommand);

        me.ayosynk.stuff.commands.FlyCommand flyCommand = new me.ayosynk.stuff.commands.FlyCommand(this);
        registerDynamic(commandMap, "fly", "Toggle player fly mode.", "/fly [player]", Collections.singletonList("flight"), flyCommand, flyCommand);
    }

    private void registerDynamic(org.bukkit.command.CommandMap commandMap, String name, String description, String usage, List<String> aliases, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter tabCompleter) {
        me.ayosynk.stuff.commands.DynamicCommand command = new me.ayosynk.stuff.commands.DynamicCommand(name, description, usage, aliases, executor, tabCompleter);
        command.setPermission("stuff." + name.replace("-", ""));
        command.setPermissionMessage(messageConfig.getNoPermission());
        commandMap.register("stuff", command);
    }

    private void startVanishActionBarTask() {
        SchedulerUtils.runAsyncRepeating(this, () -> {
            for (UUID uuid : vanishedPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    // Send vanish action bar message
                    player.sendActionBar(MiniMessageUtils.parse(messageConfig.getVanishActionBar()));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // Config Getters
    public PluginConfig getPluginConfig() { return pluginConfig; }
    public MessageConfig getMessageConfig() { return messageConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }

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
        boolean canSeeVanished = sender.hasPermission("stuff.vanish.see");
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
            return sender.hasPermission("stuff.vanish.see");
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
        private int followCooldown = 0; // Cycles to wait between follow teleports

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

        /** Decrement the follow cooldown by 1 each task cycle (called every 4 ticks). */
        public void tickFollowCooldown() { if (followCooldown > 0) followCooldown--; }
        /** Reset the follow cooldown after issuing a follow teleport. 2 cycles × 4 ticks = 400ms gap. */
        public void resetFollowCooldown() { this.followCooldown = 2; }
        /** True when enough time has elapsed since the last follow teleport. */
        public boolean isFollowReady() { return followCooldown <= 0; }
    }

    public void restoreSpectatorState(Player staff, boolean asyncSafe) {
        SpectatorState state = monitorStates.remove(staff.getUniqueId());
        
        // Remove and cancel active spectating task if present
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
