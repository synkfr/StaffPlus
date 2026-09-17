package me.ayosynk.staff.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.utils.DiscordWebhookUtils;
import me.ayosynk.staff.utils.DurationUtils;
import me.ayosynk.staff.velocity.StaffVelocityPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Network-wide punishment command handler for Velocity proxy.
 * Supports: ban, tempban, unban, ip-ban, tempip-ban, unip-ban,
 *           mute, tempmute, unmute, warn, warns,
 *           history, staffhistory, staffrollback, staffallow, staffimport
 */
public class VelocityPunishCommand implements SimpleCommand {

    private final StaffVelocityPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public VelocityPunishCommand(StaffVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        String label = invocation.alias().toLowerCase();

        String permission;
        switch (label) {
            case "bans", "banhistory", "banlist" -> permission = "staff.bans";
            case "bansleaderboard", "banleaderboard", "staffleaderboard" -> permission = "staff.bansleaderboard";
            case "checkban", "bancheck" -> permission = "staff.checkban";
            case "staff", "staffplus", "staff+" -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                    permission = "staff.staff.reload";
                } else {
                    permission = "staff.staff";
                }
            }
            default -> permission = "staff." + label.replace("-", "");
        }

        if (source instanceof Player) {
            boolean hasPerm = source.hasPermission("staff.admin")
                    || source.hasPermission(permission)
                    || (permission.equals("staff.staff.reload") && source.hasPermission("staff.staff"));
            if (!hasPerm) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoPermission()));
                return;
            }
        }

        DatabaseManager db = plugin.getDatabaseManager();

        switch (label) {
            case "ban" -> handleBan(source, args, db, false);
            case "tempban" -> handleTempBan(source, args, db);
            case "unban" -> handleUnban(source, args, db);
            case "ip-ban", "ipban", "banip" -> handleIpBan(source, args, db, false);
            case "tempip-ban", "tempipban", "tempbanip" -> handleTempIpBan(source, args, db);
            case "unip-ban", "unipban", "unbanip" -> handleUnIpBan(source, args, db);
            case "mute" -> handleMute(source, args, db, false);
            case "tempmute" -> handleTempMute(source, args, db);
            case "unmute" -> handleUnmute(source, args, db);
            case "warn" -> handleWarn(source, args, db);
            case "warns" -> handleWarns(source, args, db);
            case "history" -> handleHistory(source, args, db);
            case "staffhistory" -> handleStaffHistory(source, args, db);
            case "staffrollback" -> handleStaffRollback(source, args, db);
            case "staffallow" -> handleStaffAllow(source, args, db);
            case "staffimport" -> handleStaffImport(source, args);
            case "bans", "banhistory", "banlist" -> handleBans(source, args, db);
            case "bansleaderboard", "banleaderboard", "staffleaderboard" -> handleBansLeaderboard(source, db);
            case "kick" -> handleKick(source, args);
            case "unwarn" -> handleUnwarn(source, args, db);
            case "checkban", "bancheck" -> handleCheckban(source, args, db);
            case "staff", "staffplus" -> handleStaff(source, args);
            default -> source.sendMessage(parse("<color:#E20000>Unknown command."));
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        String alias = invocation.alias().toLowerCase();
        if (alias.equals("staff") || alias.equals("staffplus")) {
            if (args.length <= 1) {
                return CompletableFuture.completedFuture(List.of("reload"));
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if ((alias.equals("bans") || alias.equals("banhistory") || alias.equals("banlist")) && args.length <= 1) {
            return CompletableFuture.completedFuture(List.of("1", "2", "3", "--chat"));
        }
        if (args.length <= 1) {
            String input = args.length == 1 ? args[0].toLowerCase() : "";
            return CompletableFuture.completedFuture(
                plugin.getRegisteredNames().stream()
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .collect(Collectors.toList())
            );
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ==========================================
    // COMMAND HANDLERS
    // ==========================================

    private void handleBan(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db, boolean isTemp) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /ban <player> [reason]"));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? joinArgs(args, 1) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }

            Punishment punishment = new Punishment(targetUuid, null, senderUuid, Punishment.Type.BAN, reason, new Timestamp(System.currentTimeMillis()), null, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerBanned().replace("{player}", targetName).replace("{time}", "Permanent").replace("{reason}", reason)));

                // Broadcast to all online players
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerBannedBroadcast().replace("{player}", targetName).replace("{sender}", senderName).replace("{time}", "Permanent").replace("{reason}", reason));

                // Kick from proxy if online
                kickPlayer(targetUuid, plugin.getMessageConfig().getBanKickMessage().replace("{reason}", reason).replace("{time}", "Permanent"));

                // Discord Webhook
                DiscordWebhookUtils.sendEmbed(plugin, "Player Banned", plugin.getPluginConfig().getDiscordWebhookColorBan(), targetName, senderName, "Permanent", reason);
            });
        });
    }

    private void handleTempBan(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 2) {
            source.sendMessage(parse("<color:#E20000>Usage: /tempban <player> <time> [reason]"));
            return;
        }
        String targetName = args[0];
        long durationMs = DurationUtils.parseDuration(args[1]);
        if (durationMs == -2) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
            return;
        }
        String reason = args.length > 2 ? joinArgs(args, 2) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        String timeStr = durationMs == -1 ? "Permanent" : DurationUtils.formatDuration(durationMs);

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }

            Timestamp endTime = durationMs == -1 ? null : new Timestamp(System.currentTimeMillis() + durationMs);
            Punishment punishment = new Punishment(targetUuid, null, senderUuid, Punishment.Type.BAN, reason, new Timestamp(System.currentTimeMillis()), endTime, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerBanned().replace("{player}", targetName).replace("{time}", timeStr).replace("{reason}", reason)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerBannedBroadcast().replace("{player}", targetName).replace("{sender}", senderName).replace("{time}", timeStr).replace("{reason}", reason));
                kickPlayer(targetUuid, plugin.getMessageConfig().getBanKickMessage().replace("{reason}", reason).replace("{time}", timeStr));
                DiscordWebhookUtils.sendEmbed(plugin, "Player Temp-Banned", plugin.getPluginConfig().getDiscordWebhookColorBan(), targetName, senderName, timeStr, reason);
            });
        });
    }

    private void handleUnban(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /unban <player>"));
            return;
        }
        String targetName = args[0];
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            db.deactivatePunishment(targetUuid, Punishment.Type.BAN).thenAccept(success -> {
                if (success) {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnbanned().replace("{player}", targetName)));
                    broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnbannedBroadcast().replace("{player}", targetName).replace("{sender}", senderName));
                } else {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>" + targetName + " is not banned."));
                }
            });
        });
    }

    private void handleIpBan(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db, boolean isTemp) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /ip-ban <player> [reason]"));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? joinArgs(args, 1) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenCompose(db::getPlayerRecord).thenAccept(record -> {
            if (record == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            Punishment punishment = new Punishment(record.uuid, record.ip, senderUuid, Punishment.Type.IP_BAN, reason, new Timestamp(System.currentTimeMillis()), null, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getIpBanned().replace("{player}", targetName).replace("{ip}", record.ip).replace("{time}", "Permanent").replace("{reason}", reason)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getIpBannedBroadcast().replace("{player}", targetName).replace("{ip}", record.ip).replace("{sender}", senderName).replace("{time}", "Permanent").replace("{reason}", reason));
                kickPlayer(record.uuid, plugin.getMessageConfig().getBanKickMessage().replace("{reason}", reason).replace("{time}", "Permanent"));
                DiscordWebhookUtils.sendEmbed(plugin, "Player IP-Banned", plugin.getPluginConfig().getDiscordWebhookColorBan(), targetName + " (" + record.ip + ")", senderName, "Permanent", reason);
            });
        });
    }

    private void handleTempIpBan(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 2) {
            source.sendMessage(parse("<color:#E20000>Usage: /tempip-ban <player> <time> [reason]"));
            return;
        }
        String targetName = args[0];
        long durationMs = DurationUtils.parseDuration(args[1]);
        if (durationMs == -2) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
            return;
        }
        String reason = args.length > 2 ? joinArgs(args, 2) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        String timeStr = durationMs == -1 ? "Permanent" : DurationUtils.formatDuration(durationMs);

        db.getPlayerUuidByName(targetName).thenCompose(db::getPlayerRecord).thenAccept(record -> {
            if (record == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            Timestamp endTime = durationMs == -1 ? null : new Timestamp(System.currentTimeMillis() + durationMs);
            Punishment punishment = new Punishment(record.uuid, record.ip, senderUuid, Punishment.Type.IP_BAN, reason, new Timestamp(System.currentTimeMillis()), endTime, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getIpBanned().replace("{player}", targetName).replace("{ip}", record.ip).replace("{time}", timeStr).replace("{reason}", reason)));
                kickPlayer(record.uuid, plugin.getMessageConfig().getBanKickMessage().replace("{reason}", reason).replace("{time}", timeStr));
            });
        });
    }

    private void handleUnIpBan(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /unip-ban <player/IP>"));
            return;
        }
        String target = args[0];
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        db.deactivateIpBan(target).thenAccept(success -> {
            if (success) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getIpUnbanned().replace("{ip}", target)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getIpUnbannedBroadcast().replace("{ip}", target).replace("{sender}", senderName));
            } else {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>" + target + " is not IP-banned."));
            }
        });
    }

    private void handleMute(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db, boolean isTemp) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /mute <player> [reason]"));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? joinArgs(args, 1) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            Punishment punishment = new Punishment(targetUuid, null, senderUuid, Punishment.Type.MUTE, reason, new Timestamp(System.currentTimeMillis()), null, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerMuted().replace("{player}", targetName).replace("{time}", "Permanent").replace("{reason}", reason)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerMutedBroadcast().replace("{player}", targetName).replace("{sender}", senderName).replace("{time}", "Permanent").replace("{reason}", reason));
                DiscordWebhookUtils.sendEmbed(plugin, "Player Muted", plugin.getPluginConfig().getDiscordWebhookColorMute(), targetName, senderName, "Permanent", reason);
            });
        });
    }

    private void handleTempMute(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 2) {
            source.sendMessage(parse("<color:#E20000>Usage: /tempmute <player> <time> [reason]"));
            return;
        }
        String targetName = args[0];
        long durationMs = DurationUtils.parseDuration(args[1]);
        if (durationMs == -2) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
            return;
        }
        String reason = args.length > 2 ? joinArgs(args, 2) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        String timeStr = durationMs == -1 ? "Permanent" : DurationUtils.formatDuration(durationMs);

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            Timestamp endTime = durationMs == -1 ? null : new Timestamp(System.currentTimeMillis() + durationMs);
            Punishment punishment = new Punishment(targetUuid, null, senderUuid, Punishment.Type.MUTE, reason, new Timestamp(System.currentTimeMillis()), endTime, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerMuted().replace("{player}", targetName).replace("{time}", timeStr).replace("{reason}", reason)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerMutedBroadcast().replace("{player}", targetName).replace("{sender}", senderName).replace("{time}", timeStr).replace("{reason}", reason));
                DiscordWebhookUtils.sendEmbed(plugin, "Player Temp-Muted", plugin.getPluginConfig().getDiscordWebhookColorMute(), targetName, senderName, timeStr, reason);
            });
        });
    }

    private void handleUnmute(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /unmute <player>"));
            return;
        }
        String targetName = args[0];
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            db.deactivatePunishment(targetUuid, Punishment.Type.MUTE).thenAccept(success -> {
                if (success) {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnmuted().replace("{player}", targetName)));
                    broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnmutedBroadcast().replace("{player}", targetName).replace("{sender}", senderName));
                } else {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>" + targetName + " is not muted."));
                }
            });
        });
    }

    private void handleWarn(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /warn <player> [reason]"));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? joinArgs(args, 1) : "No reason specified";
        UUID senderUuid = source instanceof Player p ? p.getUniqueId() : null;
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            Punishment punishment = new Punishment(targetUuid, null, senderUuid, Punishment.Type.WARN, reason, new Timestamp(System.currentTimeMillis()), null, true);
            db.addPunishment(punishment).thenRun(() -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerWarned().replace("{player}", targetName).replace("{reason}", reason)));
                broadcastToAll(plugin.getMessageConfig().getPrefix() +
                    plugin.getMessageConfig().getPlayerWarnedBroadcast().replace("{player}", targetName).replace("{sender}", senderName).replace("{reason}", reason));

                // Notify target if online
                Optional<Player> targetOpt = plugin.getServer().getPlayer(targetUuid);
                targetOpt.ifPresent(player -> player.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getYouAreWarned().replace("{reason}", reason))));

                DiscordWebhookUtils.sendEmbed(plugin, "Player Warned", plugin.getPluginConfig().getDiscordWebhookColorWarn(), targetName, senderName, "N/A", reason);
            });
        });
    }

    private void handleWarns(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /warns <player> [clear/list]"));
            return;
        }
        String targetName = args[0];
        boolean clear = args.length > 1 && args[1].equalsIgnoreCase("clear");

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            if (clear) {
                db.clearWarnings(targetUuid).thenAccept(success -> {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarnCleared().replace("{player}", targetName)));
                });
            } else {
                db.getWarnings(targetUuid).thenAccept(warns -> {
                    if (warns.isEmpty()) {
                        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoWarns().replace("{player}", targetName)));
                    } else {
                        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarnListHeader().replace("{player}", targetName)));
                        for (var w : warns) {
                            source.sendMessage(parse(plugin.getMessageConfig().getWarnListItem()
                                .replace("{reason}", w.getReason())
                                .replace("{sender}", w.getPunisherUuid() != null ? w.getPunisherUuid().toString() : "Console")
                                .replace("{date}", w.getStartTime().toString())));
                        }
                    }
                });
            }
        });
    }

    private void handleHistory(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /history <player>"));
            return;
        }
        db.getPlayerUuidByName(args[0]).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", args[0])));
                return;
            }
            db.getHistory(targetUuid).thenAccept(history -> {
                source.sendMessage(parse("<gradient:#00E262:#00FF7F>★ Punishment History for " + args[0] + " ★</gradient>"));
                if (history.isEmpty()) {
                    source.sendMessage(parse("<color:#A0A0A0>No punishments found."));
                } else {
                    for (var p : history) {
                        String active = p.isActive() ? "<color:#00E262>ACTIVE" : "<color:#E20000>EXPIRED";
                        source.sendMessage(parse("<color:#A0A0A0>[" + p.getType() + "] " + active + " <color:#A0A0A0>- " + p.getReason() + " (" + p.getStartTime() + ")"));
                    }
                }
            });
        });
    }

    private void handleStaffHistory(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /staffhistory <staff>"));
            return;
        }
        db.getPlayerUuidByName(args[0]).thenAccept(staffUuid -> {
            if (staffUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", args[0])));
                return;
            }
            db.getStaffHistory(staffUuid).thenAccept(history -> {
                source.sendMessage(parse("<gradient:#00E262:#00FF7F>★ Staff History for " + args[0] + " ★</gradient>"));
                if (history.isEmpty()) {
                    source.sendMessage(parse("<color:#A0A0A0>No punishments issued by this staff member."));
                } else {
                    for (var p : history) {
                        String active = p.isActive() ? "<color:#00E262>ACTIVE" : "<color:#E20000>EXPIRED";
                        source.sendMessage(parse("<color:#A0A0A0>[" + p.getType() + "] " + active + " <color:#A0A0A0>- " + p.getReason()));
                    }
                }
            });
        });
    }

    private void handleStaffRollback(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            source.sendMessage(parse("<color:#E20000>Usage: /staffrollback <staff> confirm"));
            return;
        }
        db.getPlayerUuidByName(args[0]).thenAccept(staffUuid -> {
            if (staffUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", args[0])));
                return;
            }
            db.rollbackStaff(staffUuid).thenAccept(count -> {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#00E262>Rolled back " + count + " punishments from " + args[0] + "."));
            });
        });
    }

    private void handleStaffAllow(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /staffallow <player> [remove]"));
            return;
        }
        String targetName = args[0];
        boolean remove = args.length > 1 && args[1].equalsIgnoreCase("remove");
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }
            if (remove) {
                db.removeAllow(targetUuid).thenAccept(success -> {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnallowed().replace("{player}", targetName)));
                });
            } else {
                db.addAllow(targetUuid).thenRun(() -> {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerAllowed().replace("{player}", targetName)));
                    broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerAllowedBroadcast().replace("{player}", targetName).replace("{sender}", senderName));
                });
            }
        });
    }

    private void handleStaffImport(com.velocitypowered.api.command.CommandSource source, String[] args) {
        var migrationManager = plugin.getMigrationManager();

        if (args.length == 0) {
            source.sendMessage(parse("<gradient:#00E262:#00FF7F>★ Staff+ Modern Importer & Migration System ★</gradient>"));
            for (var s : migrationManager.getSources()) {
                source.sendMessage(parse("<color:#A0A0A0> • </color><color:#00E262>/staffimport " + s.getName() + "</color> - <color:#707070>" + s.getDescription() + "</color>"));
            }
            return;
        }

        var sourceObj = migrationManager.getSource(args[0]);
        if (sourceObj == null) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Unknown migration source! Use /staffimport to view sources."));
            return;
        }

        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>Starting import from <color:#00E262>" + sourceObj.getName() + "<color:#A0A0A0>..."));
        sourceObj.migrate(plugin, msg -> source.sendMessage(parse(msg)), args).thenAccept(count -> {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<gradient:#00E262:#00FF7F>Successfully imported " + count + " punishments from " + sourceObj.getName() + "!</gradient>"));
        }).exceptionally(ex -> {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Migration failed: " + ex.getCause().getMessage()));
            return null;
        });
    }

    private void handleBans(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        final int targetPage = page;
        final int pageSize = 8;
        final java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");

        db.getTotalBanCount().thenAccept(totalBans -> {
            if (totalBans <= 0) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                return;
            }

            int totalPages = (int) Math.ceil((double) totalBans / pageSize);
            int validPage = Math.min(Math.max(1, targetPage), totalPages);
            int offset = (validPage - 1) * pageSize;

            db.getRecentBans(offset, pageSize).thenAccept(bans -> {
                if (bans.isEmpty()) {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                    return;
                }

                String header = plugin.getMessageConfig().getBansHeader()
                        .replace("{page}", String.valueOf(validPage))
                        .replace("{pages}", String.valueOf(totalPages))
                        .replace("{total}", String.valueOf(totalBans));
                source.sendMessage(parse(header));

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                List<String> renderedLines = Collections.synchronizedList(new ArrayList<>());

                for (Punishment p : bans) {
                    CompletableFuture<String> targetFuture = p.getUuid() != null
                            ? db.getPlayerNameByUuid(p.getUuid())
                            : CompletableFuture.completedFuture(p.getIpAddress() != null ? p.getIpAddress() : "Unknown");

                    CompletableFuture<String> punisherFuture = p.getPunisherUuid() != null
                            ? db.getPlayerNameByUuid(p.getPunisherUuid())
                            : CompletableFuture.completedFuture("Console");

                    CompletableFuture<Void> lineFuture = targetFuture.thenCombine(punisherFuture, (targetName, staffName) -> {
                        String finalTarget = targetName != null ? targetName : (p.getIpAddress() != null ? p.getIpAddress() : "Unknown");
                        String finalStaff = staffName != null ? staffName : "Console";
                        String dateStr = dateFormat.format(p.getStartTime());

                        String template = p.isActive() && !p.isExpired()
                                ? plugin.getMessageConfig().getBansItemActive()
                                : plugin.getMessageConfig().getBansItemExpired();

                        return template
                                .replace("{player}", finalTarget)
                                .replace("{staff}", finalStaff)
                                .replace("{reason}", p.getReason())
                                .replace("{date}", dateStr);
                    }).thenAccept(renderedLines::add);

                    futures.add(lineFuture);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                    for (String line : renderedLines) {
                        source.sendMessage(parse(line));
                    }

                    int prevPage = Math.max(1, validPage - 1);
                    int nextPage = Math.min(totalPages, validPage + 1);

                    String footer = plugin.getMessageConfig().getBansFooter()
                            .replace("{prev}", String.valueOf(prevPage))
                            .replace("{next}", String.valueOf(nextPage));
                    source.sendMessage(parse(footer));
                });
            });
        });
    }

    private void handleBansLeaderboard(com.velocitypowered.api.command.CommandSource source, DatabaseManager db) {
        if (!plugin.getPluginConfig().isBansLeaderboardEnabled()) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardDisabled()));
            return;
        }

        db.getStaffBanLeaderboard(10).thenAccept(entries -> {
            if (entries.isEmpty()) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardEmpty()));
                return;
            }

            source.sendMessage(parse(plugin.getMessageConfig().getBansLeaderboardHeader()));

            int rank = 1;
            for (DatabaseManager.StaffLeaderboardEntry entry : entries) {
                String line = plugin.getMessageConfig().getBansLeaderboardItem()
                        .replace("{rank}", String.valueOf(rank))
                        .replace("{staff}", entry.getStaffName())
                        .replace("{count}", String.valueOf(entry.getCount()));
                source.sendMessage(parse(line));
                rank++;
            }
        });
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private Component parse(String msg) {
        return miniMessage.deserialize(msg);
    }

    private void broadcastToAll(String msg) {
        Component component = parse(msg);
        for (Player player : plugin.getServer().getAllPlayers()) {
            player.sendMessage(component);
        }
    }

    private void kickPlayer(UUID uuid, String reason) {
        plugin.getServer().getPlayer(uuid).ifPresent(player ->
            player.disconnect(parse(reason))
        );
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void handleKick(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /kick <player> [reason]"));
            return;
        }
        String targetName = args[0];
        Optional<Player> targetOpt = plugin.getServer().getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
            return;
        }

        Player target = targetOpt.get();
        if (source instanceof Player senderPlayer) {
            if (target.hasPermission("staff.admin") && !senderPlayer.hasPermission("staff.admin")) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getCannotKickHigherRank()));
                return;
            }
        }

        String reason = args.length > 1 ? joinArgs(args, 1) : "Kicked by an operator.";
        String senderName = source instanceof Player p ? p.getUsername() : "Console";
        String kickMsg = plugin.getMessageConfig().getKickMessage().replace("{reason}", reason);
        target.disconnect(parse(kickMsg));

        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerKicked()
                .replace("{player}", target.getUsername())
                .replace("{reason}", reason)));

        broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerKickedBroadcast()
                .replace("{player}", target.getUsername())
                .replace("{staff}", senderName)
                .replace("{reason}", reason));

        DiscordWebhookUtils.sendEmbed(plugin, "Player Kicked", plugin.getPluginConfig().getDiscordWebhookColorKick(), target.getUsername(), senderName, "N/A", reason);
    }

    private void handleUnwarn(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /unwarn <player> [id]"));
            return;
        }
        String targetName = args[0];
        String senderName = source instanceof Player p ? p.getUsername() : "Console";

        db.getPlayerUuidByName(targetName).thenAccept(targetUuid -> {
            if (targetUuid == null) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return;
            }

            int explicitId = -1;
            if (args.length >= 2) {
                try {
                    explicitId = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }

            if (explicitId != -1) {
                final int warnId = explicitId;
                db.getPunishmentById(warnId).thenAccept(p -> {
                    if (p == null || p.getType() != Punishment.Type.WARN || !p.isActive()) {
                        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarningNotFound()));
                        return;
                    }
                    db.removeWarningById(warnId).thenAccept(success -> {
                        if (success) {
                            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnwarned().replace("{player}", targetName)));
                            broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnwarnedBroadcast()
                                    .replace("{player}", targetName)
                                    .replace("{staff}", senderName));
                        } else {
                            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarningNotFound()));
                        }
                    });
                });
            } else {
                db.getLatestActiveWarning(targetUuid).thenAccept(latest -> {
                    if (latest == null) {
                        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoActiveWarnings().replace("{player}", targetName)));
                        return;
                    }
                    db.removeWarningById(latest.getId()).thenAccept(success -> {
                        if (success) {
                            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnwarned().replace("{player}", targetName)));
                            broadcastToAll(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnwarnedBroadcast()
                                    .replace("{player}", targetName)
                                    .replace("{staff}", senderName));
                        } else {
                            source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoActiveWarnings().replace("{player}", targetName)));
                        }
                    });
                });
            }
        });
    }

    private void handleCheckban(com.velocitypowered.api.command.CommandSource source, String[] args, DatabaseManager db) {
        if (args.length < 1) {
            source.sendMessage(parse("<color:#E20000>Usage: /checkban <player/IP>"));
            return;
        }
        String input = args[0];
        boolean isIp = input.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

        if (isIp) {
            db.getActivePunishment(null, input, Punishment.Type.IP_BAN).thenAccept(p -> {
                if (p == null) {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getCheckbanNotBanned().replace("{target}", input)));
                    return;
                }
                displayCheckban(source, input, p, true, db);
            });
        } else {
            db.getPlayerUuidByName(input).thenAccept(uuid -> {
                if (uuid == null) {
                    source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", input)));
                    return;
                }
                db.getActivePunishment(uuid, null, Punishment.Type.BAN).thenAccept(p -> {
                    if (p != null) {
                        displayCheckban(source, input, p, false, db);
                    } else {
                        db.getActivePunishment(uuid, null, Punishment.Type.IP_BAN).thenAccept(ipBan -> {
                            if (ipBan != null) {
                                displayCheckban(source, input, ipBan, true, db);
                            } else {
                                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getCheckbanNotBanned().replace("{target}", input)));
                            }
                        });
                    }
                });
            });
        }
    }

    private void displayCheckban(com.velocitypowered.api.command.CommandSource source, String targetDisplay, Punishment p, boolean isIp, DatabaseManager db) {
        CompletableFuture<String> staffFut = p.getPunisherUuid() != null
                ? db.getPlayerNameByUuid(p.getPunisherUuid())
                : CompletableFuture.completedFuture("Console");

        staffFut.thenAccept(staffName -> {
            String finalStaff = staffName != null ? staffName : "Console";
            String duration = p.isPermanent() ? "Permanent" : DurationUtils.formatDuration(p.getDuration());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String date = sdf.format(p.getStartTime());

            source.sendMessage(parse(plugin.getMessageConfig().getCheckbanHeader().replace("{target}", targetDisplay)));
            String template = isIp ? plugin.getMessageConfig().getCheckbanActiveIp() : plugin.getMessageConfig().getCheckbanActive();
            String msg = template
                    .replace("{target}", targetDisplay)
                    .replace("{ip}", p.getIpAddress() != null ? p.getIpAddress() : targetDisplay)
                    .replace("{staff}", finalStaff)
                    .replace("{reason}", p.getReason())
                    .replace("{date}", date)
                    .replace("{duration}", duration);
            source.sendMessage(parse(msg));
        });
    }

    private void handleStaff(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (source instanceof Player && !source.hasPermission("staff.admin") && !source.hasPermission("staff.staff.reload") && !source.hasPermission("staff.staff")) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoPermission()));
                return;
            }
            try {
                plugin.getPluginConfig().load(true);
                plugin.getMessageConfig().load(true);
                plugin.getDatabaseManager().init();
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getConfigsReloaded()));
            } catch (Exception e) {
                source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Error reloading configurations: " + e.getMessage()));
                plugin.getLogger().severe("Error reloading configurations: " + e.getMessage());
            }
            return;
        }
        source.sendMessage(parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /staff reload"));
    }
}
