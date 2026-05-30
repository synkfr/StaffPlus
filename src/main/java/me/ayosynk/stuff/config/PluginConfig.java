package me.ayosynk.stuff.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE)
public class PluginConfig extends OkaeriConfig {

    @Comment("Storage type: SQLITE or MYSQL")
    private String storageType = "sqlite";

    @Comment("MySQL Connection Details (only used if storage-type is MYSQL)")
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "minecraft";
    private String mysqlUsername = "root";
    private String mysqlPassword = "password";
    private int mysqlPoolSize = 10;
    private boolean mysqlUseSsl = false;

    @Comment("Vanish settings")
    private boolean vanishSilentContainerClicks = true;
    private boolean vanishIgnorePressurePlates = true;
    private boolean vanishDisableMobTargeting = true;
    private boolean vanishDisableItemPickup = true;

    @Comment("Warning escalation ladder settings")
    private boolean warningLadderEnabled = true;

    @Comment("Actions to run when a player reaches a specific number of active warnings. Placeholders: {player}")
    private java.util.Map<Integer, String> warningLadderActions = java.util.Map.of(
        1, "tempmute {player} 1h [Warning Ladder] First warning",
        2, "tempmute {player} 12h [Warning Ladder] Second warning",
        3, "tempban {player} 3d [Warning Ladder] Reached 3 warnings",
        4, "ban {player} [Warning Ladder] Reached 4 warnings"
    );

    @Comment("Discord Webhook logging settings")
    private boolean discordWebhookEnabled = false;
    private String discordWebhookUrl = "";
    private String discordWebhookUsername = "Stuff+ Moderation";
    private String discordWebhookAvatarUrl = "https://i.imgur.com/8Qp49X0.png";

    @Comment("Hex color codes for webhook embeds (without the #)")
    private String discordWebhookColorBan = "FF5555";
    private String discordWebhookColorMute = "FFAA00";
    private String discordWebhookColorWarn = "FFFF55";

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
}
