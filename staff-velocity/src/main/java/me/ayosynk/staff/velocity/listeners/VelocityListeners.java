package me.ayosynk.staff.velocity.listeners;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.utils.DurationUtils;
import me.ayosynk.staff.velocity.StaffVelocityPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.InetSocketAddress;

/**
 * Velocity event listeners for proxy-level moderation enforcement.
 * - Pre-Login Ban Check: Intercepts login attempts and rejects banned players at the proxy gateway.
 * - Chat Mute Filter: Blocks messages from actively muted players network-wide.
 */
public class VelocityListeners {

    private final StaffVelocityPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public VelocityListeners(StaffVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts player login events asynchronously.
     * Checks database for active player bans and IP bans, rejecting rulebreakers
     * at the proxy gateway before they reach any backend servers.
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String ip = "";
        InetSocketAddress address = player.getRemoteAddress();
        if (address != null) {
            ip = address.getAddress().getHostAddress();
        }

        String finalIp = ip;

        // Check player ban
        plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.BAN)
            .thenAccept(ban -> {
                if (ban != null) {
                    String timeRemaining = ban.getEndTime() != null
                        ? DurationUtils.formatDuration(ban.getEndTime().getTime() - System.currentTimeMillis())
                        : "Permanent";
                    String kickMsg = plugin.getMessageConfig().getBanKickMessage()
                        .replace("{reason}", ban.getReason())
                        .replace("{time}", timeRemaining);
                    event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(kickMsg)));
                    return;
                }

                // Check IP ban (only if not already rejected by player ban)
                plugin.getDatabaseManager().isAllowed(player.getUniqueId()).thenAccept(isAllowed -> {
                    if (isAllowed) return; // Player is exempt from IP bans

                    plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.IP_BAN)
                        .thenAccept(ipBan -> {
                            if (ipBan != null) {
                                String timeRemaining = ipBan.getEndTime() != null
                                    ? DurationUtils.formatDuration(ipBan.getEndTime().getTime() - System.currentTimeMillis())
                                    : "Permanent";
                                String kickMsg = plugin.getMessageConfig().getBanKickMessage()
                                    .replace("{reason}", ipBan.getReason())
                                    .replace("{time}", timeRemaining);
                                event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(kickMsg)));
                            }
                        });
                });
            });

        // Register player in database
        String playerIp = finalIp;
        plugin.getDatabaseManager().savePlayer(player.getUniqueId(), player.getUsername(), playerIp, 0);
        plugin.cacheName(player.getUsername());
    }

    /**
     * Intercepts player chat events.
     * Blocks messages from actively muted players network-wide.
     */
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), null, Punishment.Type.MUTE)
            .thenAccept(mute -> {
                if (mute != null) {
                    String timeRemaining = mute.getEndTime() != null
                        ? DurationUtils.formatDuration(mute.getEndTime().getTime() - System.currentTimeMillis())
                        : "Permanent";
                    player.sendMessage(miniMessage.deserialize(
                        plugin.getMessageConfig().getPrefix() +
                        plugin.getMessageConfig().getYouAreMuted()
                            .replace("{time}", timeRemaining)
                            .replace("{reason}", mute.getReason())
                    ));
                    event.setResult(PlayerChatEvent.ChatResult.denied());
                }
            });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPluginConfig().isUpdateCheckerNotifyAdmins()) {
            if (player.hasPermission("staff.admin") || player.hasPermission("staff.update.notify")) {
                if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
                    String updateMsg = plugin.getMessageConfig().getUpdateAvailable()
                            .replace("{latest}", plugin.getUpdateChecker().getLatestVersion())
                            .replace("{current}", plugin.getPluginVersion())
                            .replace("{url}", plugin.getUpdateChecker().getModrinthUrl());
                    player.sendMessage(miniMessage.deserialize(plugin.getMessageConfig().getPrefix() + updateMsg));
                }
            }
        }
    }
}
