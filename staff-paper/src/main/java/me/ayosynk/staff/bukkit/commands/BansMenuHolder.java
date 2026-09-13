package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import me.ayosynk.staff.database.DatabaseManager;
import me.ayosynk.staff.database.Punishment;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BansMenuHolder implements InventoryHolder {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 45;

    private final StaffBukkitPlugin plugin;
    private final int page;
    private final int totalPages;
    private final int totalBans;
    private Inventory inventory;

    public BansMenuHolder(StaffBukkitPlugin plugin, int page, int totalPages, int totalBans) {
        this.plugin = plugin;
        this.page = page;
        this.totalPages = totalPages;
        this.totalBans = totalBans;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getTotalBans() {
        return totalBans;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static void open(StaffBukkitPlugin plugin, Player player, int page) {
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
                String title = plugin.getMessageConfig().getBansGuiTitle()
                        .replace("{page}", String.valueOf(validPage))
                        .replace("{pages}", String.valueOf(totalPages));

                BansMenuHolder holder = new BansMenuHolder(plugin, validPage, totalPages, totalBans);
                Inventory inv = Bukkit.createInventory(holder, 54, MiniMessageUtils.parse(title));
                holder.setInventory(inv);

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                class SlotData {
                    int slot;
                    ItemStack item;
                }
                List<SlotData> slotDataList = Collections.synchronizedList(new ArrayList<>());

                int slot = 0;
                for (Punishment p : bans) {
                    final int currentSlot = slot++;
                    CompletableFuture<String> targetFut = p.getUuid() != null
                            ? db.getPlayerNameByUuid(p.getUuid())
                            : CompletableFuture.completedFuture(p.getIpAddress() != null ? p.getIpAddress() : "Unknown");
                    CompletableFuture<String> staffFut = p.getPunisherUuid() != null
                            ? db.getPlayerNameByUuid(p.getPunisherUuid())
                            : CompletableFuture.completedFuture("Console");

                    futures.add(targetFut.thenCombine(staffFut, (target, staff) -> {
                        String finalTarget = target != null ? target : (p.getIpAddress() != null ? p.getIpAddress() : "Unknown");
                        String finalStaff = staff != null ? staff : "Console";
                        boolean active = p.isActive() && !p.isExpired();

                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        if (meta != null) {
                            if (p.getUuid() != null) {
                                meta.setOwningPlayer(Bukkit.getOfflinePlayer(p.getUuid()));
                            }
                            String displayName = active
                                    ? "<red><bold>" + finalTarget + "</bold></red>"
                                    : "<gray>" + finalTarget + " (Expired)</gray>";
                            meta.displayName(MiniMessageUtils.parse(displayName));

                            List<Component> lore = Arrays.asList(
                                    MiniMessageUtils.parse("<gray>Staff: <white>" + finalStaff + "</white></gray>"),
                                    MiniMessageUtils.parse("<gray>Reason: <yellow>" + p.getReason() + "</yellow></gray>"),
                                    MiniMessageUtils.parse("<gray>Date: <white>" + DATE_FORMAT.format(p.getStartTime()) + "</white></gray>"),
                                    MiniMessageUtils.parse("<gray>Status: " + (active ? "<red>Active</red>" : "<gray>Expired</gray>"))
                            );
                            meta.lore(lore);
                            head.setItemMeta(meta);
                        }

                        SlotData sd = new SlotData();
                        sd.slot = currentSlot;
                        sd.item = head;
                        slotDataList.add(sd);
                        return null;
                    }));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                    for (SlotData sd : slotDataList) {
                        inv.setItem(sd.slot, sd.item);
                    }

                    ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                    ItemMeta fillerMeta = filler.getItemMeta();
                    if (fillerMeta != null) {
                        fillerMeta.displayName(MiniMessageUtils.parse("<gray> </gray>"));
                        filler.setItemMeta(fillerMeta);
                    }

                    for (int s = 45; s < 54; s++) {
                        inv.setItem(s, filler);
                    }

                    if (validPage > 1) {
                        ItemStack prev = new ItemStack(Material.ARROW);
                        ItemMeta prevMeta = prev.getItemMeta();
                        if (prevMeta != null) {
                            prevMeta.displayName(MiniMessageUtils.parse("<color:#00E262>« Previous Page</color>"));
                            prev.setItemMeta(prevMeta);
                        }
                        inv.setItem(45, prev);
                    }

                    ItemStack book = new ItemStack(Material.BOOK);
                    ItemMeta bookMeta = book.getItemMeta();
                    if (bookMeta != null) {
                        bookMeta.displayName(MiniMessageUtils.parse("<color:#E2B700>Page " + validPage + " / " + totalPages + "</color>"));
                        bookMeta.lore(Collections.singletonList(MiniMessageUtils.parse("<gray>Total Bans: <yellow>" + totalBans + "</yellow></gray>")));
                        book.setItemMeta(bookMeta);
                    }
                    inv.setItem(49, book);

                    if (validPage < totalPages) {
                        ItemStack next = new ItemStack(Material.ARROW);
                        ItemMeta nextMeta = next.getItemMeta();
                        if (nextMeta != null) {
                            nextMeta.displayName(MiniMessageUtils.parse("<color:#00E262>Next Page »</color>"));
                            next.setItemMeta(nextMeta);
                        }
                        inv.setItem(53, next);
                    }

                    SchedulerUtils.runEntity(plugin, player, () -> player.openInventory(inv));
                });
            });
        });
    }
}
