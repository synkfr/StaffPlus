package me.ayosynk.staff.bukkit.compat;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.database.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class BedrockFormHandler {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 10;

    static boolean isBedrock(UUID uuid) {
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                if (GeyserInvoker.isBedrock(uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                if (FloodgateInvoker.isBedrock(uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    static boolean sendForm(UUID uuid, Form form) {
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                if (GeyserInvoker.sendForm(uuid, form)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                if (FloodgateInvoker.sendForm(uuid, form)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    static void openBansForm(StaffBukkitPlugin plugin, Player player, int page) {
        DatabaseManager db = plugin.getDatabaseManager();
        db.getTotalBanCount().thenAccept(totalBans -> {
            if (totalBans <= 0) {
                player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                return;
            }

            int totalPages = (int) Math.ceil((double) totalBans / PAGE_SIZE);
            int validPage = Math.min(Math.max(1, page), totalPages);
            int offset = (validPage - 1) * PAGE_SIZE;

            db.getRecentBans(offset, PAGE_SIZE).thenAccept(bans -> {
                if (bans.isEmpty()) {
                    player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansEmpty()));
                    return;
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                List<BanDisplayEntry> entries = Collections.synchronizedList(new ArrayList<>());

                for (Punishment p : bans) {
                    CompletableFuture<String> targetFut = p.getUuid() != null
                            ? db.getPlayerNameByUuid(p.getUuid())
                            : CompletableFuture.completedFuture(p.getIpAddress() != null ? p.getIpAddress() : "Unknown");
                    CompletableFuture<String> staffFut = p.getPunisherUuid() != null
                            ? db.getPlayerNameByUuid(p.getPunisherUuid())
                            : CompletableFuture.completedFuture("Console");

                    futures.add(targetFut.thenCombine(staffFut, (target, staff) -> {
                        BanDisplayEntry e = new BanDisplayEntry();
                        e.target = target != null ? target : (p.getIpAddress() != null ? p.getIpAddress() : "Unknown");
                        e.staff = staff != null ? staff : "Console";
                        e.date = DATE_FORMAT.format(p.getStartTime());
                        e.active = p.isActive() && !p.isExpired();
                        e.punishment = p;
                        entries.add(e);
                        return null;
                    }));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                    String title = plugin.getMessageConfig().getBansFormTitle()
                            .replace("{page}", String.valueOf(validPage))
                            .replace("{pages}", String.valueOf(totalPages));
                    String content = plugin.getMessageConfig().getBansFormContent()
                            .replace("{total}", String.valueOf(totalBans));

                    SimpleForm.Builder builder = SimpleForm.builder()
                            .title(title)
                            .content(content);

                    for (BanDisplayEntry entry : entries) {
                        String statusPrefix = entry.active ? "[Active] " : "[Expired] ";
                        builder.button(statusPrefix + entry.target + " - " + entry.punishment.getReason());
                    }

                    int prevIndex = -1;
                    if (validPage > 1) {
                        prevIndex = entries.size();
                        builder.button("« Previous Page");
                    }

                    int nextIndex = -1;
                    if (validPage < totalPages) {
                        nextIndex = prevIndex != -1 ? prevIndex + 1 : entries.size();
                        builder.button("Next Page »");
                    }

                    builder.button("Close");

                    final int finalPrev = prevIndex;
                    final int finalNext = nextIndex;

                    builder.validResultHandler(response -> {
                        int id = response.clickedButtonId();
                        if (id < entries.size()) {
                            openBanDetailForm(plugin, player, entries.get(id), validPage);
                        } else if (id == finalPrev) {
                            openBansForm(plugin, player, validPage - 1);
                        } else if (id == finalNext) {
                            openBansForm(plugin, player, validPage + 1);
                        }
                    });

                    sendForm(player.getUniqueId(), builder.build());
                });
            });
        });
    }

    private static void openBanDetailForm(StaffBukkitPlugin plugin, Player player, BanDisplayEntry entry, int returnPage) {
        String durationStr = entry.punishment.isPermanent() ? "Permanent" : (entry.punishment.getDuration() + "ms");
        String details = "Target: " + entry.target + "\n"
                + "Staff: " + entry.staff + "\n"
                + "Reason: " + entry.punishment.getReason() + "\n"
                + "Date: " + entry.date + "\n"
                + "Duration: " + durationStr + "\n"
                + "Status: " + (entry.active ? "Active" : "Expired / Inactive");

        SimpleForm form = SimpleForm.builder()
                .title("Ban Details: " + entry.target)
                .content(details)
                .button("« Back to Bans")
                .button("Close")
                .validResultHandler(response -> {
                    if (response.clickedButtonId() == 0) {
                        openBansForm(plugin, player, returnPage);
                    }
                })
                .build();

        sendForm(player.getUniqueId(), form);
    }

    static void openLeaderboardForm(StaffBukkitPlugin plugin, Player player) {
        DatabaseManager db = plugin.getDatabaseManager();
        db.getStaffBanLeaderboard(10).thenAccept(entries -> {
            if (entries.isEmpty()) {
                player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardEmpty()));
                return;
            }

            String title = plugin.getMessageConfig().getBansLeaderboardFormTitle();
            String content = plugin.getMessageConfig().getBansLeaderboardFormContent();

            SimpleForm.Builder builder = SimpleForm.builder()
                    .title(title)
                    .content(content);

            int rank = 1;
            for (DatabaseManager.StaffLeaderboardEntry e : entries) {
                builder.button("#" + rank + " " + e.getStaffName() + " (" + e.getCount() + " bans)");
                rank++;
            }
            builder.button("Close");

            sendForm(player.getUniqueId(), builder.build());
        });
    }

    static class BanDisplayEntry {
        String target;
        String staff;
        String date;
        boolean active;
        Punishment punishment;
    }

    private static class GeyserInvoker {
        static boolean isBedrock(UUID uuid) {
            return GeyserApi.api().isBedrockPlayer(uuid);
        }

        static boolean sendForm(UUID uuid, Form form) {
            return GeyserApi.api().sendForm(uuid, form);
        }
    }

    private static class FloodgateInvoker {
        static boolean isBedrock(UUID uuid) {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        }

        static boolean sendForm(UUID uuid, Form form) {
            return FloodgateApi.getInstance().sendForm(uuid, form);
        }
    }
}
