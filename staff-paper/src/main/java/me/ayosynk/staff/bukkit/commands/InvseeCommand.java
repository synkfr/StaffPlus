package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InvseeCommand implements CommandExecutor, TabCompleter {

    private final StaffBukkitPlugin plugin;

    public InvseeCommand(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerOnly()));
            return true;
        }

        Player staff = (Player) sender;

        if (args.length < 1) {
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /invsee <player>"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline() || !plugin.canSee(staff, target)) {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline.hasPlayedBefore()) {
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>" + targetName + "</color> is currently offline. <color:#A0A0A0>/invsee provides real-time live inspection and requires the player to be online.</color>"));
            } else {
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
            }
            return true;
        }

        if (target.getUniqueId().equals(staff.getUniqueId())) {
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>You cannot inspect your own inventory."));
            return true;
        }

        // Run UI opening on entity scheduler to be thread safe under Folia
        SchedulerUtils.runEntity(plugin, staff, () -> openInvsee(staff, target));
        return true;
    }

    public void openInvsee(Player staff, Player target) {
        InvseeHolder holder = new InvseeHolder(target);
        Inventory inv = Bukkit.createInventory(holder, 54, MiniMessageUtils.parse("<color:#A0A0A0>Invsee: <color:#00E262>" + target.getName()));
        holder.setInventory(inv);

        updateInvseeContents(inv, target);

        plugin.getInvseeSessions().put(staff.getUniqueId(), new StaffBukkitPlugin.InvseeSession(staff.getUniqueId(), target.getUniqueId(), inv));

        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvseeOpened().replace("{player}", target.getName())));
        staff.openInventory(inv);
    }

    /**
     * Refreshes the custom invsee inventory layout and syncing with the target player's stats/items.
     */
    public static void updateInvseeContents(Inventory inv, Player target) {
        applySnapshot(inv, takeSnapshot(target));
    }

    public static class InvSnapshot {
        public final ItemStack[] items = new ItemStack[36];
        public ItemStack helmet;
        public ItemStack chestplate;
        public ItemStack leggings;
        public ItemStack boots;
        public ItemStack offhand;
        public ItemStack potionItem;
        public ItemStack appleItem;
    }

    public static InvSnapshot takeSnapshot(Player target) {
        InvSnapshot s = new InvSnapshot();
        for (int i = 0; i < 36; i++) {
            ItemStack it = target.getInventory().getItem(i);
            s.items[i] = it != null ? it.clone() : null;
        }
        ItemStack h = target.getInventory().getHelmet();
        s.helmet = h != null ? h.clone() : null;
        ItemStack c = target.getInventory().getChestplate();
        s.chestplate = c != null ? c.clone() : null;
        ItemStack l = target.getInventory().getLeggings();
        s.leggings = l != null ? l.clone() : null;
        ItemStack b = target.getInventory().getBoots();
        s.boots = b != null ? b.clone() : null;
        ItemStack off = target.getInventory().getItemInOffHand();
        s.offhand = off != null ? off.clone() : null;

        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta potMeta = potion.getItemMeta();
        if (potMeta != null) {
            potMeta.displayName(MiniMessageUtils.parse("<color:#E2B700>Potion Effects"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (target.getActivePotionEffects().isEmpty()) {
                lore.add(MiniMessageUtils.parse("<color:#A0A0A0>No active potion effects."));
            } else {
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    String effName = effect.getType().getName().replace("_", " ").toLowerCase();
                    int amp = effect.getAmplifier() + 1;
                    long durationSec = effect.getDuration() / 20;
                    lore.add(MiniMessageUtils.parse("<color:#A0A0A0>- " + effName + " " + amp + " (" + durationSec + "s)"));
                }
            }
            potMeta.lore(lore);
            potion.setItemMeta(potMeta);
        }
        s.potionItem = potion;

        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta appMeta = apple.getItemMeta();
        if (appMeta != null) {
            appMeta.displayName(MiniMessageUtils.parse("<color:#E20000>Target Health & Hunger"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            double maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null 
                    ? target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() 
                    : target.getMaxHealth();
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Health: <color:#E20000>" + String.format("%.1f", target.getHealth()) + " / " + maxHealth));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Food Level: <color:#E2B700>" + target.getFoodLevel() + " / 20"));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>XP Level: <color:#00E262>" + target.getLevel()));
            appMeta.lore(lore);
            apple.setItemMeta(appMeta);
        }
        s.appleItem = apple;

        return s;
    }

    public static void applySnapshot(Inventory inv, InvSnapshot s) {
        // 1. Copy main inventory rows 0-2 (target slots 9-35 -> GUI 0-26)
        for (int i = 0; i <= 26; i++) {
            inv.setItem(i, s.items[i + 9]);
        }

        // 2. Copy hotbar row 3 (target slots 0-8 -> GUI 27-35)
        for (int i = 27; i <= 35; i++) {
            inv.setItem(i, s.items[i - 27]);
        }

        // 3. Separator row (slots 36-44)
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, createSeparatorItem(i));
        }

        // 4. Equipment row (slots 45-49)
        inv.setItem(45, s.helmet != null ? s.helmet : createEquipmentPlaceholder(45));
        inv.setItem(46, s.chestplate != null ? s.chestplate : createEquipmentPlaceholder(46));
        inv.setItem(47, s.leggings != null ? s.leggings : createEquipmentPlaceholder(47));
        inv.setItem(48, s.boots != null ? s.boots : createEquipmentPlaceholder(48));
        inv.setItem(49, s.offhand != null ? s.offhand : createEquipmentPlaceholder(49));

        // 5. Utility shortcuts (slots 50-53)
        inv.setItem(50, createEnderChestItem());
        inv.setItem(51, s.potionItem);
        inv.setItem(52, s.appleItem);
        inv.setItem(53, createCloseItem());
    }

    public static ItemStack createSeparatorItem(int slot) {
        if (slot == 44) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            ItemMeta meta = shears.getItemMeta();
            if (meta != null) {
                meta.displayName(MiniMessageUtils.parse("<color:#E20000>Strip All Armor</color>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Click to remove all armor and off-hand.</color>"));
                lore.add(MiniMessageUtils.parse("<color:#707070>Items will be added to your inventory.</color>"));
                meta.lore(lore);
                shears.setItemMeta(meta);
            }
            return shears;
        }

        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            if (slot == 40) {
                meta.displayName(MiniMessageUtils.parse("<color:#A0A0A0>▲ Hotbar | Equipment ▼</color>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(MiniMessageUtils.parse("<color:#707070>Hotbar above, equipment and stats below.</color>"));
                meta.lore(lore);
            } else {
                meta.displayName(MiniMessageUtils.parse("<color:#555555>─────────</color>"));
            }
            separator.setItemMeta(meta);
        }
        return separator;
    }

    public static final NamespacedKey PLACEHOLDER_KEY = new NamespacedKey("staff", "invsee_placeholder");

    public static ItemStack createEquipmentPlaceholder(int slot) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
            switch (slot) {
                case 45 -> {
                    meta.displayName(MiniMessageUtils.parse("<color:#E2B700>Helmet Slot</color> <color:#707070>(Empty)</color>"));
                    meta.lore(List.of(
                            MiniMessageUtils.parse("<color:#A0A0A0>Target has no helmet equipped.</color>"),
                            MiniMessageUtils.parse("<color:#707070>Click with armor to equip.</color>")
                    ));
                }
                case 46 -> {
                    meta.displayName(MiniMessageUtils.parse("<color:#E2B700>Chestplate Slot</color> <color:#707070>(Empty)</color>"));
                    meta.lore(List.of(
                            MiniMessageUtils.parse("<color:#A0A0A0>Target has no chestplate equipped.</color>"),
                            MiniMessageUtils.parse("<color:#707070>Click with armor to equip.</color>")
                    ));
                }
                case 47 -> {
                    meta.displayName(MiniMessageUtils.parse("<color:#E2B700>Leggings Slot</color> <color:#707070>(Empty)</color>"));
                    meta.lore(List.of(
                            MiniMessageUtils.parse("<color:#A0A0A0>Target has no leggings equipped.</color>"),
                            MiniMessageUtils.parse("<color:#707070>Click with armor to equip.</color>")
                    ));
                }
                case 48 -> {
                    meta.displayName(MiniMessageUtils.parse("<color:#E2B700>Boots Slot</color> <color:#707070>(Empty)</color>"));
                    meta.lore(List.of(
                            MiniMessageUtils.parse("<color:#A0A0A0>Target has no boots equipped.</color>"),
                            MiniMessageUtils.parse("<color:#707070>Click with armor to equip.</color>")
                    ));
                }
                case 49 -> {
                    meta.displayName(MiniMessageUtils.parse("<color:#00E262>Off-Hand Slot</color> <color:#707070>(Empty)</color>"));
                    meta.lore(List.of(
                            MiniMessageUtils.parse("<color:#A0A0A0>Target has no off-hand item equipped.</color>"),
                            MiniMessageUtils.parse("<color:#707070>Click with an item to equip.</color>")
                    ));
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(PLACEHOLDER_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createEnderChestItem() {
        ItemStack echest = new ItemStack(Material.ENDER_CHEST);
        ItemMeta ecMeta = echest.getItemMeta();
        if (ecMeta != null) {
            ecMeta.displayName(MiniMessageUtils.parse("<color:#00E262>Ender Chest"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Click to inspect target's Ender Chest."));
            ecMeta.lore(lore);
            echest.setItemMeta(ecMeta);
        }
        return echest;
    }

    public static ItemStack createCloseItem() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barMeta = barrier.getItemMeta();
        if (barMeta != null) {
            barMeta.displayName(MiniMessageUtils.parse("<color:#E20000>Close Viewer"));
            barrier.setItemMeta(barMeta);
        }
        return barrier;
    }

    public static int guiSlotToTargetSlot(int guiSlot) {
        if (guiSlot >= 0 && guiSlot <= 26) {
            return guiSlot + 9;
        }
        if (guiSlot >= 27 && guiSlot <= 35) {
            return guiSlot - 27;
        }
        return -1;
    }

    public static int targetSlotToGuiSlot(int targetSlot) {
        if (targetSlot >= 9 && targetSlot <= 35) {
            return targetSlot - 9;
        }
        if (targetSlot >= 0 && targetSlot <= 8) {
            return targetSlot + 27;
        }
        return -1;
    }

    public static void updateStatsOnly(Inventory inv, Player target) {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta potMeta = potion.getItemMeta();
        if (potMeta != null) {
            potMeta.displayName(MiniMessageUtils.parse("<color:#E2B700>Potion Effects"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (target.getActivePotionEffects().isEmpty()) {
                lore.add(MiniMessageUtils.parse("<color:#A0A0A0>No active potion effects."));
            } else {
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    String effName = effect.getType().getName().replace("_", " ").toLowerCase();
                    int amp = effect.getAmplifier() + 1;
                    long durationSec = effect.getDuration() / 20;
                    lore.add(MiniMessageUtils.parse("<color:#A0A0A0>- " + effName + " " + amp + " (" + durationSec + "s)"));
                }
            }
            potMeta.lore(lore);
            potion.setItemMeta(potMeta);
        }
        inv.setItem(51, potion);

        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta appMeta = apple.getItemMeta();
        if (appMeta != null) {
            appMeta.displayName(MiniMessageUtils.parse("<color:#E20000>Target Health & Hunger"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            org.bukkit.attribute.AttributeInstance maxHealthAttr = target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Health: <color:#E20000>" + String.format("%.1f", target.getHealth()) + " / " + maxHealth));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Food Level: <color:#E2B700>" + target.getFoodLevel() + " / 20"));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>XP Level: <color:#00E262>" + target.getLevel()));
            appMeta.lore(lore);
            apple.setItemMeta(appMeta);
        }
        inv.setItem(52, apple);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return plugin.getVisibleOnlinePlayers(sender).stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
