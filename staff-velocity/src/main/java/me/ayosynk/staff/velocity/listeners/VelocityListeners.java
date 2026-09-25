package me.ayosynk.staff.velocity.listeners;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.utils.DurationUtils;
import me.ayosynk.staff.velocity.StaffVelocityPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public class VelocityListeners {

    private final StaffVelocityPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public VelocityListeners(StaffVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.withContinuation(continuation -> {
            Player player = event.getPlayer();
            String ip = "";
            InetSocketAddress address = player.getRemoteAddress();
            if (address != null) {
                ip = address.getAddress().getHostAddress();
            }

            String finalIp = ip;

            plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.BAN)
                .thenCompose(ban -> {
                    if (ban != null) {
                        String timeRemaining = ban.getEndTime() != null
                            ? DurationUtils.formatDuration(ban.getEndTime().getTime() - System.currentTimeMillis())
                            : "Permanent";
                        String kickMsg = plugin.getMessageConfig().getBanKickMessage()
                            .replace("{reason}", ban.getReason())
                            .replace("{time}", timeRemaining);
                        event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(kickMsg)));
                        return CompletableFuture.completedFuture(null);
                    }

                    return plugin.getDatabaseManager().isAllowed(player.getUniqueId()).thenCompose(isAllowed -> {
                        if (isAllowed) {
                            return CompletableFuture.completedFuture(null);
                        }

                        return plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.IP_BAN)
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
                })
                .thenCompose(v -> {
                    if (!event.getResult().isAllowed()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    plugin.cacheName(player.getUsername());
                    return plugin.getDatabaseManager().savePlayer(player.getUniqueId(), player.getUsername(), finalIp, 0);
                })
                .whenComplete((v, ex) -> continuation.resume());
        });
    }

    @Subscribe
    public EventTask onPlayerChat(PlayerChatEvent event) {
        return EventTask.withContinuation(continuation -> {
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
                })
                .whenComplete((v, ex) -> continuation.resume());
        });
    }

    @Subscribe
    public EventTask onKickedFromServer(KickedFromServerEvent event) {
        if (!plugin.getPluginConfig().isVelocityDisconnectOnKick()) {
            return null;
        }

        return EventTask.withContinuation(continuation -> {
            Player player = event.getPlayer();
            String ip = "";
            InetSocketAddress address = player.getRemoteAddress();
            if (address != null) {
                ip = address.getAddress().getHostAddress();
            }
            String finalIp = ip;

            plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.BAN)
                .thenAccept(ban -> {
                    if (ban != null) {
                        String timeRemaining = ban.getEndTime() != null
                            ? DurationUtils.formatDuration(ban.getEndTime().getTime() - System.currentTimeMillis())
                            : "Permanent";
                        String kickMsg = plugin.getMessageConfig().getBanKickMessage()
                            .replace("{reason}", ban.getReason())
                            .replace("{time}", timeRemaining);
                        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(miniMessage.deserialize(kickMsg)));
                        return;
                    }

                    if (!event.kickedDuringServerConnect() && event.getServerKickReason().isPresent()) {
                        Component kickReason = event.getServerKickReason().get();
                        String serialized = miniMessage.serialize(kickReason).toLowerCase();
                        boolean isServerShutdown = serialized.contains("server closed")
                                || serialized.contains("server restarting")
                                || serialized.contains("server going down");
                        if (!isServerShutdown) {
                            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(kickReason));
                        }
                    }
                })
                .whenComplete((v, ex) -> continuation.resume());
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
