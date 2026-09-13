package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import me.ayosynk.staff.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BansLeaderboardHolder implements InventoryHolder {

    private final StaffBukkitPlugin plugin;
    private Inventory inventory;

    public BansLeaderboardHolder(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static void open(StaffBukkitPlugin plugin, Player player) {
        DatabaseManager db = plugin.getDatabaseManager();
        db.getStaffBanLeaderboard(10).thenAccept(entries -> {
            if (entries.isEmpty()) {
                player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getBansLeaderboardEmpty()));
                return;
            }

            BansLeaderboardHolder holder = new BansLeaderboardHolder(plugin);
            Inventory inv = Bukkit.createInventory(holder, 54, MiniMessageUtils.parse(plugin.getMessageConfig().getBansLeaderboardGuiTitle()));
            holder.setInventory(inv);

            ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta bgMeta = bg.getItemMeta();
            if (bgMeta != null) {
                bgMeta.displayName(MiniMessageUtils.parse("<gray> </gray>"));
                bg.setItemMeta(bgMeta);
            }
            for (int i = 0; i < 54; i++) {
                inv.setItem(i, bg);
            }

            int[] podiumSlots = {13, 21, 23, 29, 30, 31, 32, 33, 38, 42};
            String[] rankColors = {"<gold>", "<white>", "<color:#CD7F32>", "<yellow>", "<yellow>", "<yellow>", "<yellow>", "<yellow>", "<yellow>", "<yellow>"};

            for (int i = 0; i < entries.size() && i < podiumSlots.length; i++) {
                DatabaseManager.StaffLeaderboardEntry entry = entries.get(i);
                int slot = podiumSlots[i];
                int rank = i + 1;
                String color = rankColors[i];

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    if (entry.getStaffUuid() != null) {
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getStaffUuid()));
                    }
                    meta.displayName(MiniMessageUtils.parse(color + "<bold>#" + rank + " " + entry.getStaffName() + "</bold>"));
                    meta.lore(Arrays.asList(
                            MiniMessageUtils.parse("<gray>Total Bans Issued: <white><bold>" + entry.getCount() + "</bold></white></gray>"),
                            MiniMessageUtils.parse("<gray>Rank: " + color + "#" + rank + "</gray>")
                    ));
                    head.setItemMeta(meta);
                }
                inv.setItem(slot, head);
            }

            ItemStack close = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = close.getItemMeta();
            if (closeMeta != null) {
                closeMeta.displayName(MiniMessageUtils.parse("<red>Close</red>"));
                close.setItemMeta(closeMeta);
            }
            inv.setItem(49, close);

            SchedulerUtils.runEntity(plugin, player, () -> player.openInventory(inv));
        });
    }
}
