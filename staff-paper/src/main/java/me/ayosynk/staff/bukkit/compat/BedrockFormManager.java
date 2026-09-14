package me.ayosynk.staff.bukkit.compat;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BedrockFormManager {

    public static boolean isBedrockSupported() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return false;
        }
        return Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
                || Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public static boolean isBedrock(Player player) {
        if (player == null || !isBedrockSupported()) {
            return false;
        }
        try {
            return BedrockFormHandler.isBedrock(player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    public static void openBansForm(StaffBukkitPlugin plugin, Player player, int page) {
        if (!isBedrockSupported()) {
            return;
        }
        try {
            BedrockFormHandler.openBansForm(plugin, player, page);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to open Bedrock bans form: " + t.getMessage());
        }
    }

    public static void openLeaderboardForm(StaffBukkitPlugin plugin, Player player) {
        if (!isBedrockSupported()) {
            return;
        }
        try {
            BedrockFormHandler.openLeaderboardForm(plugin, player);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to open Bedrock leaderboard form: " + t.getMessage());
        }
    }
}
