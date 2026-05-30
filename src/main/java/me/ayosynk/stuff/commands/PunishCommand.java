package me.ayosynk.stuff.commands;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.database.Punishment;
import me.ayosynk.stuff.utils.DurationUtils;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private final StuffPlugin plugin;
    private static final Pattern IP_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PunishCommand(StuffPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase();

        if (args.length == 0 && !name.equals("warns")) {
            return false;
        }

        // Run entire command flow asynchronously to prevent thread blocking (crucial for SQLite/MySQL & Folia compatibility)
        SchedulerUtils.runAsync(plugin, () -> handleCommandAsync(sender, name, args));
        return true;
    }

    private void handleCommandAsync(CommandSender sender, String label, String[] args) {
        UUID senderUuid = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
        String senderName = sender.getName();

        if (args.length == 0) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /" + label + " <player> ..."));
            return;
        }

        String targetInput = args[0];

        switch (label) {
            case "mute":
            case "tempmute": {
                boolean isTemp = label.equals("tempmute");
                if (isTemp && args.length < 2) {
                    sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /tempmute <player> <time> [reason]"));
                    return;
                }

                long duration = -1; // -1 represents Permanent
                int reasonStartIndex = 1;

                if (isTemp) {
                    duration = DurationUtils.parseDuration(args[1]);
                    if (duration == -2) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
                        return;
                    }
                    reasonStartIndex = 2;
                }

                final long finalDuration = duration;
                String reason = buildReason(args, reasonStartIndex);
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    Timestamp start = new Timestamp(System.currentTimeMillis());
                    Timestamp end = finalDuration > 0 ? new Timestamp(System.currentTimeMillis() + finalDuration) : null;

                    Punishment p = new Punishment(target.uuid, target.ip, senderUuid, Punishment.Type.MUTE, reason, start, end, true);
                    plugin.getDatabaseManager().addPunishment(p).thenRun(() -> {
                        String timeStr = finalDuration > 0 ? DurationUtils.formatDuration(finalDuration) : "Permanent";
                        // Send success feedback
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerMuted()
                                .replace("{player}", target.name)
                                .replace("{time}", timeStr)
                                .replace("{reason}", reason)));

                        // Global announcement
                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getPlayerMutedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{sender}", senderName)
                                    .replace("{time}", timeStr)
                                    .replace("{reason}", reason);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.mute");
                        });

                        // Notify online target if they are online
                        Player targetPlayer = Bukkit.getPlayer(target.uuid);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            SchedulerUtils.runEntity(plugin, targetPlayer, () -> {
                                targetPlayer.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getYouAreMuted()
                                        .replace("{time}", timeStr)
                                        .replace("{reason}", reason)));
                            });
                        }

                        // Trigger Discord Webhook
                        me.ayosynk.stuff.utils.DiscordWebhookUtils.sendEmbed(
                                plugin,
                                "Player Muted",
                                plugin.getPluginConfig().getDiscordWebhookColorMute(),
                                target.name,
                                senderName,
                                timeStr,
                                reason
                        );
                    });
                });
                break;
            }

            case "unmute": {
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    plugin.getDatabaseManager().deactivatePunishment(target.uuid, Punishment.Type.MUTE).thenAccept(success -> {
                        if (!success) {
                            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E2B700>This player is not currently muted."));
                            return;
                        }

                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnmuted().replace("{player}", target.name)));
                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getPlayerUnmutedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{sender}", senderName);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.mute");
                        });
                    });
                });
                break;
            }

            case "ban":
            case "tempban": {
                boolean isTemp = label.equals("tempban");
                if (isTemp && args.length < 2) {
                    sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /tempban <player> <time> [reason]"));
                    return;
                }

                long duration = -1;
                int reasonStartIndex = 1;

                if (isTemp) {
                    duration = DurationUtils.parseDuration(args[1]);
                    if (duration == -2) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
                        return;
                    }
                    reasonStartIndex = 2;
                }

                final long finalDuration = duration;
                String reason = buildReason(args, reasonStartIndex);
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    Timestamp start = new Timestamp(System.currentTimeMillis());
                    Timestamp end = finalDuration > 0 ? new Timestamp(System.currentTimeMillis() + finalDuration) : null;

                    Punishment p = new Punishment(target.uuid, target.ip, senderUuid, Punishment.Type.BAN, reason, start, end, true);
                    plugin.getDatabaseManager().addPunishment(p).thenRun(() -> {
                        String timeStr = finalDuration > 0 ? DurationUtils.formatDuration(finalDuration) : "Permanent";
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerBanned()
                                .replace("{player}", target.name)
                                .replace("{time}", timeStr)
                                .replace("{reason}", reason)));

                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getPlayerBannedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{sender}", senderName)
                                    .replace("{time}", timeStr)
                                    .replace("{reason}", reason);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.ban");
                        });

                        // Kick target player if online (Folia Regional Safe)
                        Player targetPlayer = Bukkit.getPlayer(target.uuid);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            SchedulerUtils.runEntity(plugin, targetPlayer, () -> {
                                targetPlayer.kick(MiniMessageUtils.parse(plugin.getMessageConfig().getBanKickMessage()
                                        .replace("{reason}", reason)
                                        .replace("{time}", timeStr)));
                            });
                        }

                        // Trigger Discord Webhook
                        me.ayosynk.stuff.utils.DiscordWebhookUtils.sendEmbed(
                                plugin,
                                "Player Banned",
                                plugin.getPluginConfig().getDiscordWebhookColorBan(),
                                target.name,
                                senderName,
                                timeStr,
                                reason
                        );
                    });
                });
                break;
            }

            case "unban": {
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    plugin.getDatabaseManager().deactivatePunishment(target.uuid, Punishment.Type.BAN).thenAccept(success -> {
                        if (!success) {
                            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E2B700>This player is not currently banned."));
                            return;
                        }

                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnbanned().replace("{player}", target.name)));
                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getPlayerUnbannedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{sender}", senderName);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.ban");
                        });
                    });
                });
                break;
            }

            case "ip-ban":
            case "tempip-ban": {
                boolean isTemp = label.equals("tempip-ban");
                if (isTemp && args.length < 2) {
                    sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /tempip-ban <player/IP> <time> [reason]"));
                    return;
                }

                long duration = -1;
                int reasonStartIndex = 1;

                if (isTemp) {
                    duration = DurationUtils.parseDuration(args[1]);
                    if (duration == -2) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvalidDuration()));
                        return;
                    }
                    reasonStartIndex = 2;
                }

                final long finalDuration = duration;
                String reason = buildReason(args, reasonStartIndex);
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    if (target.ip == null || target.ip.isEmpty()) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>No IP address could be resolved for target " + target.name + "."));
                        return;
                    }

                    Timestamp start = new Timestamp(System.currentTimeMillis());
                    Timestamp end = finalDuration > 0 ? new Timestamp(System.currentTimeMillis() + finalDuration) : null;

                    Punishment p = new Punishment(target.uuid, target.ip, senderUuid, Punishment.Type.IP_BAN, reason, start, end, true);
                    plugin.getDatabaseManager().addPunishment(p).thenRun(() -> {
                        String timeStr = finalDuration > 0 ? DurationUtils.formatDuration(finalDuration) : "Permanent";
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getIpBanned()
                                .replace("{player}", target.name)
                                .replace("{ip}", target.ip)
                                .replace("{time}", timeStr)
                                .replace("{reason}", reason)));

                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getIpBannedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{ip}", target.ip)
                                    .replace("{sender}", senderName)
                                    .replace("{time}", timeStr)
                                    .replace("{reason}", reason);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.ipban");
                        });

                        // Kick any player matching the IP
                        SchedulerUtils.runGlobal(plugin, () -> {
                            for (Player pOnline : Bukkit.getOnlinePlayers()) {
                                String onlineIp = pOnline.getAddress().getAddress().getHostAddress();
                                if (onlineIp.equals(target.ip)) {
                                    SchedulerUtils.runEntity(plugin, pOnline, () -> {
                                        pOnline.kick(MiniMessageUtils.parse(plugin.getMessageConfig().getBanKickMessage()
                                                .replace("{reason}", "IP Ban: " + reason)
                                                .replace("{time}", timeStr)));
                                    });
                                }
                            }
                        });

                        // Trigger Discord Webhook
                        me.ayosynk.stuff.utils.DiscordWebhookUtils.sendEmbed(
                                plugin,
                                "Player IP-Banned",
                                plugin.getPluginConfig().getDiscordWebhookColorBan(),
                                target.name + " (" + target.ip + ")",
                                senderName,
                                timeStr,
                                reason
                        );
                    });
                });
                break;
            }

            case "unip-ban": {
                plugin.getDatabaseManager().deactivateIpBan(targetInput).thenAccept(success -> {
                    if (!success) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E2B700>This IP or Player is not currently IP-banned."));
                        return;
                    }

                    sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getIpUnbanned().replace("{ip}", targetInput)));
                    SchedulerUtils.runGlobal(plugin, () -> {
                        String broadcast = plugin.getMessageConfig().getIpUnbannedBroadcast()
                                .replace("{ip}", targetInput)
                                .replace("{sender}", senderName);
                        Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.ipban");
                    });
                });
                break;
            }

            case "warn": {
                String reason = buildReason(args, 1);
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    Timestamp start = new Timestamp(System.currentTimeMillis());
                    Punishment p = new Punishment(target.uuid, target.ip, senderUuid, Punishment.Type.WARN, reason, start, null, true);
                    plugin.getDatabaseManager().addPunishment(p).thenRun(() -> {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerWarned()
                                .replace("{player}", target.name)
                                .replace("{reason}", reason)));

                        SchedulerUtils.runGlobal(plugin, () -> {
                            String broadcast = plugin.getMessageConfig().getPlayerWarnedBroadcast()
                                    .replace("{player}", target.name)
                                    .replace("{sender}", senderName)
                                    .replace("{reason}", reason);
                            Bukkit.broadcast(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + broadcast), "stuff.warn");
                        });

                        Player targetPlayer = Bukkit.getPlayer(target.uuid);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            SchedulerUtils.runEntity(plugin, targetPlayer, () -> {
                                targetPlayer.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getYouAreWarned()
                                        .replace("{reason}", reason)));
                            });
                        }

                        // Dynamic Warning Escalation Ladder
                        if (plugin.getPluginConfig().isWarningLadderEnabled()) {
                            plugin.getDatabaseManager().getWarnings(target.uuid).thenAccept(warns -> {
                                int activeWarnsCount = warns.size();
                                String actionTemplate = plugin.getPluginConfig().getWarningLadderActions().get(activeWarnsCount);
                                if (actionTemplate != null) {
                                    String finalCommand = actionTemplate.replace("{player}", target.name);
                                    SchedulerUtils.runGlobal(plugin, () -> {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                                    });
                                }
                            });
                        }

                        // Trigger Discord Webhook
                        me.ayosynk.stuff.utils.DiscordWebhookUtils.sendEmbed(
                                plugin,
                                "Player Warned",
                                plugin.getPluginConfig().getDiscordWebhookColorWarn(),
                                target.name,
                                senderName,
                                "N/A",
                                reason
                        );
                    });
                });
                break;
            }

            case "warns": {
                resolveTarget(targetInput).thenAccept(target -> {
                    if (target == null) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                        return;
                    }

                    String subAction = args.length > 1 ? args[1].toLowerCase() : "list";

                    if (subAction.equals("clear")) {
                        plugin.getDatabaseManager().clearWarnings(target.uuid).thenAccept(success -> {
                            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarnCleared().replace("{player}", target.name)));
                        });
                    } else {
                        plugin.getDatabaseManager().getWarnings(target.uuid).thenAccept(warns -> {
                            if (warns.isEmpty()) {
                                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoWarns().replace("{player}", target.name)));
                                return;
                            }

                            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarnListHeader().replace("{player}", target.name)));
                            for (Punishment warn : warns) {
                                plugin.getDatabaseManager().getPlayerNameByUuid(warn.getPunisherUuid() == null ? UUID.randomUUID() : warn.getPunisherUuid()).thenAccept(pName -> {
                                    String finalPName = warn.getPunisherUuid() == null ? "Console" : (pName != null ? pName : "Unknown");
                                    String item = plugin.getMessageConfig().getWarnListItem()
                                            .replace("{reason}", warn.getReason())
                                            .replace("{sender}", finalPName)
                                            .replace("{date}", DATE_FORMAT.format(warn.getStartTime()));
                                    sender.sendMessage(MiniMessageUtils.parse(item));
                                });
                            }
                        });
                    }
                });
                break;
            }
        }
    }

    private String buildReason(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "No reason provided.";
        }
        return Arrays.stream(args).skip(startIndex).collect(Collectors.joining(" "));
    }

    private CompletableFuture<ResolvedTarget> resolveTarget(String input) {
        CompletableFuture<ResolvedTarget> future = new CompletableFuture<>();

        // 1. Is it a raw IP?
        if (IP_PATTERN.matcher(input).matches()) {
            future.complete(new ResolvedTarget(null, input, input));
            return future;
        }

        // 2. Is target online?
        Player player = Bukkit.getPlayer(input);
        if (player != null && player.isOnline()) {
            future.complete(new ResolvedTarget(player.getUniqueId(), player.getName(), player.getAddress().getAddress().getHostAddress()));
            return future;
        }

        // 3. Resolve from Database
        plugin.getDatabaseManager().getPlayerUuidByName(input).thenAccept(uuid -> {
            if (uuid == null) {
                future.complete(null);
                return;
            }

            // Target exists in database, let's get last known username & IP
            plugin.getDatabaseManager().getPlayerRecord(uuid).thenAccept(record -> {
                if (record != null) {
                    future.complete(new ResolvedTarget(record.uuid, record.name, record.ip));
                } else {
                    future.complete(null);
                }
            }).exceptionally(ex -> {
                future.complete(null);
                return null;
            });
        }).exceptionally(ex -> {
            future.complete(null);
            return null;
        });

        return future;
    }

    private static class ResolvedTarget {
        final UUID uuid;
        final String name;
        final String ip;

        ResolvedTarget(UUID uuid, String name, String ip) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
        }
    }

    // Dynamic Tab Completion
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            // Include active online players
            for (Player p : plugin.getVisibleOnlinePlayers(sender)) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(p.getName());
                }
            }
            // Include registered offline players from the cache
            for (String regName : plugin.getRegisteredNames()) {
                if (regName.toLowerCase().startsWith(input) && !suggestions.contains(regName)) {
                    suggestions.add(regName);
                }
            }
            Collections.sort(suggestions);
            return suggestions;
        }

        if (args.length == 2) {
            if (name.equals("tempmute") || name.equals("tempban") || name.equals("tempip-ban")) {
                List<String> times = Arrays.asList("30m", "1h", "12h", "1d", "7d", "perm");
                String input = args[1].toLowerCase();
                return times.stream().filter(t -> t.startsWith(input)).collect(Collectors.toList());
            }
            if (name.equals("warns")) {
                List<String> sub = Arrays.asList("list", "clear");
                String input = args[1].toLowerCase();
                return sub.stream().filter(s -> s.startsWith(input)).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
