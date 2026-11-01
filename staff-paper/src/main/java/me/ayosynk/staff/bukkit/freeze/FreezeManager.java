package me.ayosynk.staff.bukkit.freeze;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FreezeManager {

    private final StaffBukkitPlugin plugin;
    private final Map<UUID, FreezeSession> frozenPlayers = new ConcurrentHashMap<>();

    public FreezeManager(@NotNull StaffBukkitPlugin plugin) {
        this.plugin = plugin;
        startReminderTask();
        startActionBarTask();
    }

    public boolean isFrozen(@NotNull UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    public FreezeSession getSession(@NotNull UUID uuid) {
        return frozenPlayers.get(uuid);
    }

    public Map<UUID, FreezeSession> getFrozenPlayers() {
        return frozenPlayers;
    }

    public void freeze(@NotNull Player target, @NotNull CommandSender staff, @NotNull String reason) {
        UUID staffUuid = (staff instanceof Player) ? ((Player) staff).getUniqueId() : null;
        String staffName = (staff instanceof Player) ? staff.getName() : "Console";

        FreezeSession session = new FreezeSession(target.getUniqueId(), staffUuid, staffName, reason, target.getLocation());
        frozenPlayers.put(target.getUniqueId(), session);

        String freezeStaffMsg = plugin.getMessageConfig().getPlayerFrozen()
                .replace("{player}", target.getName())
                .replace("{staff}", staffName)
                .replace("{reason}", reason);
        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + freezeStaffMsg));

        String broadcastMsg = plugin.getMessageConfig().getPlayerFrozenBroadcast()
                .replace("{player}", target.getName())
                .replace("{staff}", staffName)
                .replace("{reason}", reason);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("staff.freeze") || p.hasPermission("staff.admin") || p.isOp()) {
                p.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcastMsg));
            }
        }

        sendReminder(target, session);
    }

    public void unfreeze(@NotNull Player target, @NotNull CommandSender staff) {
        FreezeSession session = frozenPlayers.remove(target.getUniqueId());
        if (session == null) {
            return;
        }

        String staffName = (staff instanceof Player) ? staff.getName() : "Console";
        target.clearTitle();

        String unfrozenTargetMsg = plugin.getMessageConfig().getPlayerUnfrozen()
                .replace("{player}", target.getName())
                .replace("{staff}", staffName);
        target.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + unfrozenTargetMsg));

        String staffMsg = plugin.getMessageConfig().getPlayerUnfrozen()
                .replace("{player}", target.getName())
                .replace("{staff}", staffName);
        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + staffMsg));

        String broadcastMsg = plugin.getMessageConfig().getPlayerUnfrozenBroadcast()
                .replace("{player}", target.getName())
                .replace("{staff}", staffName);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("staff.freeze") || p.hasPermission("staff.admin") || p.isOp()) {
                p.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcastMsg));
            }
        }
    }

    public void handleLogout(@NotNull Player player) {
        FreezeSession session = frozenPlayers.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        String alert = plugin.getMessageConfig().getFrozenLogoutBroadcast()
                .replace("{player}", player.getName())
                .replace("{staff}", session.getStaffName())
                .replace("{reason}", session.getReason());

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("staff.freeze") || p.hasPermission("staff.admin") || p.isOp()) {
                p.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + alert));
            }
        }
        Bukkit.getConsoleSender().sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + alert));

        if (plugin.getPluginConfig().isFreezeLogoutActionEnabled()) {
            String action = plugin.getPluginConfig().getFreezeLogoutAction()
                    .replace("{player}", player.getName())
                    .replace("{staff}", session.getStaffName())
                    .replace("{reason}", session.getReason());
            plugin.dispatchConsoleCommand(action);
        }
    }

    public void cleanup() {
        frozenPlayers.clear();
    }

    private void startReminderTask() {
        int interval = Math.max(1, plugin.getPluginConfig().getFreezeReminderIntervalSeconds());
        SchedulerUtils.runAsyncRepeating(plugin, () -> {
            for (UUID uuid : frozenPlayers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    FreezeSession session = frozenPlayers.get(uuid);
                    if (session != null) {
                        sendReminder(player, session);
                    }
                }
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void startActionBarTask() {
        SchedulerUtils.runAsyncRepeating(plugin, () -> {
            for (UUID uuid : frozenPlayers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    String ab = plugin.getMessageConfig().getFrozenActionBar()
                            .replace("{teamspeak}", plugin.getPluginConfig().getTeamspeakServer())
                            .replace("{discord}", plugin.getPluginConfig().getDiscordInvite())
                            .replace("{player}", player.getName());
                    player.sendActionBar(MiniMessageUtils.parse(ab));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void sendReminder(Player player, FreezeSession session) {
        String chatMsg = plugin.getMessageConfig().getFrozenChatReminder()
                .replace("{teamspeak}", plugin.getPluginConfig().getTeamspeakServer())
                .replace("{discord}", plugin.getPluginConfig().getDiscordInvite())
                .replace("{staff}", session.getStaffName())
                .replace("{reason}", session.getReason())
                .replace("{player}", player.getName());
        player.sendMessage(MiniMessageUtils.parse(chatMsg));

        String titleStr = plugin.getMessageConfig().getFrozenTitle()
                .replace("{teamspeak}", plugin.getPluginConfig().getTeamspeakServer())
                .replace("{discord}", plugin.getPluginConfig().getDiscordInvite())
                .replace("{staff}", session.getStaffName())
                .replace("{reason}", session.getReason())
                .replace("{player}", player.getName());
        String subStr = plugin.getMessageConfig().getFrozenSubtitle()
                .replace("{teamspeak}", plugin.getPluginConfig().getTeamspeakServer())
                .replace("{discord}", plugin.getPluginConfig().getDiscordInvite())
                .replace("{staff}", session.getStaffName())
                .replace("{reason}", session.getReason())
                .replace("{player}", player.getName());

        player.showTitle(Title.title(MiniMessageUtils.parse(titleStr), MiniMessageUtils.parse(subStr)));
    }
}
