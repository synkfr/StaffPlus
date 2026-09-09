package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.database.Punishment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BansCommand implements CommandExecutor, TabCompleter {

    private final StaffBukkitPlugin plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 8;

    public BansCommand(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase();

        if (name.equals("bansleaderboard") || name.equals("banleaderboard") || name.equals("staffleaderboard")) {
            SchedulerUtils.runAsync(plugin, () -> handleLeaderboard(sender));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        final int targetPage = page;
        SchedulerUtils.runAsync(plugin, () -> handleBansList(sender, targetPage));
        return true;
    }

    private void handleBansList(CommandSender sender, int page) {
        DatabaseManager db = plugin.getDatabaseManager();

        db.getTotalBanCount().thenAccept(totalBans -> {
            if (totalBans <= 0) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                return;
            }

            int totalPages = (int) Math.ceil((double) totalBans / PAGE_SIZE);
            int validPage = Math.min(Math.max(1, page), totalPages);
            int offset = (validPage - 1) * PAGE_SIZE;

            db.getRecentBans(offset, PAGE_SIZE).thenAccept(bans -> {
                if (bans.isEmpty()) {
                    sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                    return;
                }

                String header = plugin.getMessageConfig().getBansHeader()
                        .replace("{page}", String.valueOf(validPage))
                        .replace("{pages}", String.valueOf(totalPages))
                        .replace("{total}", String.valueOf(totalBans));
                sender.sendMessage(MiniMessageUtils.parse(header));

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
                        String dateStr = DATE_FORMAT.format(p.getStartTime());

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
                        sender.sendMessage(MiniMessageUtils.parse(line));
                    }

                    int prevPage = Math.max(1, validPage - 1);
                    int nextPage = Math.min(totalPages, validPage + 1);

                    String footer = plugin.getMessageConfig().getBansFooter()
                            .replace("{prev}", String.valueOf(prevPage))
                            .replace("{next}", String.valueOf(nextPage));
                    sender.sendMessage(MiniMessageUtils.parse(footer));
                });
            });
        });
    }

    private void handleLeaderboard(CommandSender sender) {
        if (!plugin.getPluginConfig().isBansLeaderboardEnabled()) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardDisabled()));
            return;
        }

        plugin.getDatabaseManager().getStaffBanLeaderboard(10).thenAccept(entries -> {
            if (entries.isEmpty()) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardEmpty()));
                return;
            }

            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getBansLeaderboardHeader()));

            int rank = 1;
            for (DatabaseManager.StaffLeaderboardEntry entry : entries) {
                String line = plugin.getMessageConfig().getBansLeaderboardItem()
                        .replace("{rank}", String.valueOf(rank))
                        .replace("{staff}", entry.getStaffName())
                        .replace("{count}", String.valueOf(entry.getCount()));
                sender.sendMessage(MiniMessageUtils.parse(line));
                rank++;
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase();
        if ((name.equals("bans") || name.equals("banhistory") || name.equals("banlist")) && args.length == 1) {
            List<String> pages = new ArrayList<>();
            pages.add("1");
            pages.add("2");
            pages.add("3");
            return pages;
        }
        return Collections.emptyList();
    }
}
