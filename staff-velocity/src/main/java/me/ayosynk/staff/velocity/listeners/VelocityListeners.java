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
    private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    public VelocityListeners(StaffVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    private CompletableFuture<String> formatKickMessage(Punishment p, String prefix) {
        String timeRemaining = p.getEndTime() != null
            ? DurationUtils.formatDuration(p.getEndTime().getTime() - System.currentTimeMillis())
            : "Permanent";
        String dateStr = DATE_FORMAT.format(p.getStartTime().toInstant());
        return plugin.getDatabaseManager().getPlayerNameByUuid(p.getPunisherUuid()).thenApply(staffName -> {
            String staff = staffName != null ? staffName : "Console";
            return plugin.getMessageConfig().getBanKickMessage()
                .replace("{reason}", prefix + p.getReason())
                .replace("{time}", timeRemaining)
                .replace("{staff}", staff)
                .replace("{date}", dateStr);
        });
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
                        return formatKickMessage(ban, "").thenAccept(kickMsg -> {
                            event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(kickMsg)));
                        });
                    }

                    return plugin.getDatabaseManager().isAllowed(player.getUniqueId()).thenCompose(isAllowed -> {
                        if (isAllowed) {
                            return CompletableFuture.completedFuture(null);
                        }

                        return plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.IP_BAN)
                            .thenCompose(ipBan -> {
                                if (ipBan != null) {
                                    return formatKickMessage(ipBan, "IP Ban: ").thenAccept(kickMsg -> {
                                        event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(kickMsg)));
                                    });
                                }
                                return CompletableFuture.completedFuture(null);
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
                .thenCompose(ban -> {
                    if (ban != null) return CompletableFuture.completedFuture(ban);
                    return plugin.getDatabaseManager().isAllowed(player.getUniqueId()).thenCompose(allowed -> {
                        if (allowed) return CompletableFuture.completedFuture(null);
                        return plugin.getDatabaseManager().getActivePunishment(player.getUniqueId(), finalIp, Punishment.Type.IP_BAN);
                    });
                })
                .thenAccept(punishment -> {
                    if (punishment != null) {
                        String timeRemaining = punishment.getEndTime() != null
                            ? DurationUtils.formatDuration(punishment.getEndTime().getTime() - System.currentTimeMillis())
                            : "Permanent";
                        String kickMsg = plugin.getMessageConfig().getBanKickMessage()
                            .replace("{reason}", punishment.getReason())
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
