package me.ayosynk.staff.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE)
public class PluginConfig extends OkaeriConfig {

    @Comment({
        "Database Storage Type",
        "Options: sqlite, mysql, or mariadb",
        "- sqlite: Stores data locally in plugins/StaffPlus/database.db. Zero setup required.",
        "- mysql or mariadb: Connects to a remote database to share bans and mutes across multiple servers."
    })
    private String storageType = "sqlite";

    @Comment({
        "MySQL / MariaDB Connection Settings",
        "These settings are only used when storage-type is set to 'mysql' or 'mariadb'.",
        "Staff+ will automatically create tables on first launch.",
        "If connection fails, Staff+ automatically falls back to local SQLite so your server never crashes."
    })
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "minecraft";
    private String mysqlUsername = "root";
    private String mysqlPassword = "password";
    @Comment("Maximum number of concurrent database connections in the connection pool (default: 10)")
    private int mysqlPoolSize = 10;
    @Comment("Enable SSL encrypted connections to your database server")
    private boolean mysqlUseSsl = false;

    @Comment({
        "Vanish Settings (Paper / Purpur / Folia only)",
        "Controls stealth features for staff when using /vanish."
    })
    @Comment("Allows vanished staff to silently open chests and barrels without animations or sounds")
    private boolean vanishSilentContainerClicks = true;
    @Comment("Prevents vanished staff from triggering wooden, stone, or iron pressure plates")
    private boolean vanishIgnorePressurePlates = true;
    @Comment("Prevents hostile mobs (like zombies and skeletons) from targeting or attacking vanished staff")
    private boolean vanishDisableMobTargeting = true;
    @Comment("Prevents vanished staff from picking up items on the ground so they stay hidden")
    private boolean vanishDisableItemPickup = true;

    @Comment({
        "Warning Escalation Ladder",
        "Automatically applies progressive punishments when a player accumulates active warnings.",
        "Set to false to disable automated warning escalation."
    })
    private boolean warningLadderEnabled = true;

    @Comment({
        "Warning Ladder Actions",
        "Format: <warning_count>: \"<command_to_run>\"",
        "The {player} placeholder is replaced with the target player's name.",
        "Commands are executed safely via console when the player reaches that warning count."
    })
    private java.util.Map<Integer, String> warningLadderActions = java.util.Map.of(
        1, "tempmute {player} 1h [Warning Ladder] First warning",
        2, "tempmute {player} 12h [Warning Ladder] Second warning",
        3, "tempban {player} 3d [Warning Ladder] Reached 3 warnings",
        4, "ban {player} [Warning Ladder] Reached 4 warnings"
    );

    @Comment({
        "Discord Webhook Logging",
        "Sends real-time embed logs to your Discord channel whenever staff issues a ban, mute, warn, or kick.",
        "All webhook requests run asynchronously in the background with zero server lag."
    })
    private boolean discordWebhookEnabled = false;
    @Comment("The Webhook URL from your Discord channel settings (keep this private!)")
    private String discordWebhookUrl = "";
    @Comment("Display name shown as the author of the Discord webhook message")
    private String discordWebhookUsername = "Staff+ Moderation";
    @Comment("Avatar image URL shown on the Discord webhook profile")
    private String discordWebhookAvatarUrl = "https://i.imgur.com/8Qp49X0.png";

    @Comment("Hex color code for Discord embed sidebar (without #, e.g. FF5555 for red)")
    private String discordWebhookColorBan = "FF5555";
    private String discordWebhookColorMute = "FFAA00";
    private String discordWebhookColorWarn = "FFFF55";
    private String discordWebhookColorKick = "FF8800";

    @Comment({
        "Update Checker",
        "Checks Modrinth asynchronously on startup and notifies administrators when a new update is available."
    })
    private boolean updateCheckerEnabled = true;
    @Comment("Whether to send an in-game notification to server admins and OPs when they join")
    private boolean updateCheckerNotifyAdmins = true;

    @Comment({
        "Staff Ban Leaderboard",
        "Enables or disables the /bansleaderboard command for tracking staff moderation activity."
    })
    private boolean bansLeaderboardEnabled = true;

    @Comment({
        "Velocity Proxy Settings (Velocity proxy edition only)",
        "These options configure how Staff+ behaves on a Velocity network gateway."
    })
    @Comment("Register moderation commands (/ban, /mute, etc.) directly on the Velocity proxy")
    private boolean velocityProxyCommands = true;
    @Comment({
        "If a staff member runs a command on the proxy but lacks proxy-level permissions,",
        "automatically forwards the command to their backend Paper server so LuckPerms and OP work seamlessly."
    })
    private boolean velocityForwardToBackend = true;
    @Comment({
        "When a player is kicked or banned on a backend server, disconnect them from the proxy completely",
        "instead of sending them to a fallback or limbo server. Normal restarts still send players to lobby."
    })
    private boolean velocityDisconnectOnKick = true;

    @Comment({
        "Freeze & Screenshare Settings (Paper / Purpur / Folia only)",
        "Allows staff to immobilize suspected rulebreakers for screensharing and investigation."
    })
    @Comment("Enable or disable the /freeze and /unfreeze system")
    private boolean freezeEnabled = true;
    @Comment({
        "Message delivery mode for frozen players:",
        "- CHAT: Sends a clean chat notification box with voice chat links.",
        "- TITLE: Shows on-screen title and subtitle in the center of their screen.",
        "- ACTION_BAR: Shows a continuous warning above their hotbar.",
        "- ALL: Shows chat, title, and action bar all at once."
    })
    private String freezeMessageMode = "CHAT";
    @Comment("Your community's TeamSpeak 3 server address (replaces {teamspeak} in messages)")
    private String teamspeakServer = "ts.example.com";
    @Comment("Your community's Discord invite link (replaces {discord} in messages)")
    private String discordInvite = "discord.gg/example";
    @Comment("How often (in seconds) to repeat the freeze message to the frozen player")
    private int freezeReminderIntervalSeconds = 5;
    @Comment("Whether to automatically run a punishment command if a player disconnects while frozen")
    private boolean freezeLogoutActionEnabled = true;
    @Comment("Console command to run when a frozen player leaves the server (replaces {player}, {staff}, {reason})")
    private String freezeLogoutAction = "ban {player} 30d Disconnecting while frozen [Auto-Ban]";
    @Comment("List of commands that frozen players are allowed to use (e.g. messaging staff)")
    private java.util.List<String> freezeAllowedCommands = java.util.List.of("msg", "r", "reply", "tell");

    public String getStorageType() {
        return storageType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    public boolean isMysqlUseSsl() {
        return mysqlUseSsl;
    }

    public boolean isVanishSilentContainerClicks() {
        return vanishSilentContainerClicks;
    }

    public boolean isVanishIgnorePressurePlates() {
        return vanishIgnorePressurePlates;
    }

    public boolean isVanishDisableMobTargeting() {
        return vanishDisableMobTargeting;
    }

    public boolean isVanishDisableItemPickup() {
        return vanishDisableItemPickup;
    }

    public boolean isWarningLadderEnabled() {
        return warningLadderEnabled;
    }

    public java.util.Map<Integer, String> getWarningLadderActions() {
        return warningLadderActions;
    }

    public boolean isDiscordWebhookEnabled() {
        return discordWebhookEnabled;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public String getDiscordWebhookUsername() {
        return discordWebhookUsername;
    }

    public String getDiscordWebhookAvatarUrl() {
        return discordWebhookAvatarUrl;
    }

    public String getDiscordWebhookColorBan() {
        return discordWebhookColorBan;
    }

    public String getDiscordWebhookColorMute() {
        return discordWebhookColorMute;
    }

    public String getDiscordWebhookColorWarn() {
        return discordWebhookColorWarn;
    }

    public String getDiscordWebhookColorKick() {
        return discordWebhookColorKick;
    }

    public boolean isUpdateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public boolean isUpdateCheckerNotifyAdmins() {
        return updateCheckerNotifyAdmins;
    }

    public boolean isBansLeaderboardEnabled() {
        return bansLeaderboardEnabled;
    }

    public boolean isVelocityProxyCommands() {
        return velocityProxyCommands;
    }

    public boolean isVelocityForwardToBackend() {
        return velocityForwardToBackend;
    }

    public boolean isVelocityDisconnectOnKick() {
        return velocityDisconnectOnKick;
    }

    public boolean isFreezeEnabled() {
        return freezeEnabled;
    }

    public String getTeamspeakServer() {
        return teamspeakServer;
    }

    public String getDiscordInvite() {
        return discordInvite;
    }

    public int getFreezeReminderIntervalSeconds() {
        return freezeReminderIntervalSeconds;
    }

    public boolean isFreezeLogoutActionEnabled() {
        return freezeLogoutActionEnabled;
    }

    public String getFreezeLogoutAction() {
        return freezeLogoutAction;
    }

    public java.util.List<String> getFreezeAllowedCommands() {
        return freezeAllowedCommands;
    }

    public String getFreezeMessageMode() {
        return freezeMessageMode;
    }
}
