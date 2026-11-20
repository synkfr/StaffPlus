package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HistoryCommand implements CommandExecutor, TabCompleter {

    private final StaffBukkitPlugin plugin;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public HistoryCommand(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase();

        if (args.length < 1) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /" + name + " <player>"));
            return true;
        }

        SchedulerUtils.runAsync(plugin, () -> handleCommandAsync(sender, name, args[0]));
        return true;
    }

    private void handleCommandAsync(CommandSender sender, String label, String targetInput) {
        resolveTarget(targetInput).thenAccept(target -> {
            if (target == null) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                return;
            }

            if (label.equals("history")) {
                plugin.getDatabaseManager().getHistory(target.uuid).thenAccept(punishments -> {
                    if (punishments.isEmpty()) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>No punishments found for " + target.name + "."));
                        return;
                    }

                    sender.sendMessage(MiniMessageUtils.parse("<color:#E2B700>------------- [ History: <color:#00E262>" + target.name + " <color:#E2B700>] -------------"));
                    renderPunishments(sender, punishments, true);
                });
            } else {
                // staffhistory
                plugin.getDatabaseManager().getStaffHistory(target.uuid).thenAccept(punishments -> {
                    if (punishments.isEmpty()) {
                        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>No punishments issued by staff " + target.name + "."));
                        return;
                    }

                    sender.sendMessage(MiniMessageUtils.parse("<color:#E2B700>------------- [ Staff History: <color:#00E262>" + target.name + " <color:#E2B700>] -------------"));
                    renderPunishments(sender, punishments, false);
                });
            }
        });
    }

    private void renderPunishments(CommandSender sender, List<Punishment> punishments, boolean showTarget) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        String[] lines = new String[punishments.size()];
        for (int i = 0; i < punishments.size(); i++) {
            final int idx = i;
            Punishment p = punishments.get(i);
            String activeStr = p.isActive() && !p.isExpired() ? "<color:#E20000>[ACTIVE]</color> " : "<color:#A0A0A0>[INACTIVE]</color> ";
            String dateStr = DATE_FORMAT.format(p.getStartTime().toInstant());
            String typeStr = p.getType().name();

            CompletableFuture<String> subjectFuture = showTarget ? 
                    plugin.getDatabaseManager().getPlayerNameByUuid(p.getPunisherUuid()) :
                    plugin.getDatabaseManager().getPlayerNameByUuid(p.getUuid());

            futures.add(subjectFuture.thenAccept(name -> {
                String subject = name != null ? name : "Console";
                String actionWord = showTarget ? "by" : "on";

                String mainMsg = activeStr + "<color:#00E262>" + typeStr + "</color> " + actionWord + " <color:#00E262>" + subject + "</color>: " + p.getReason() + " <color:#A0A0A0>(" + dateStr + ")</color>";
                if (p.isActive() && !p.isExpired() && p.getEndTime() != null) {
                    mainMsg += " <color:#E2B700>[Expires: " + DATE_FORMAT.format(p.getEndTime().toInstant()) + "]</color>";
                }
                lines[idx] = mainMsg;
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            for (String line : lines) {
                if (line != null) sender.sendMessage(MiniMessageUtils.parse(line));
            }
        });
    }

    private CompletableFuture<ResolvedTarget> resolveTarget(String input) {
        CompletableFuture<ResolvedTarget> future = new CompletableFuture<>();
        Player online = Bukkit.getPlayer(input);

        if (online != null && online.isOnline()) {
            future.complete(new ResolvedTarget(online.getUniqueId(), online.getName(), online.getAddress().getAddress().getHostAddress()));
            return future;
        }

        plugin.getDatabaseManager().getPlayerUuidByName(input).thenAccept(uuid -> {
            if (uuid == null) {
                future.complete(null);
                return;
            }

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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (Player p : plugin.getVisibleOnlinePlayers(sender)) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(p.getName());
                }
            }
            for (String regName : plugin.getRegisteredNames()) {
                if (regName.toLowerCase().startsWith(input) && !suggestions.contains(regName)) {
                    suggestions.add(regName);
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
