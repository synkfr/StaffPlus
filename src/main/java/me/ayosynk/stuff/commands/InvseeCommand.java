package me.ayosynk.stuff.commands;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InvseeCommand implements CommandExecutor, TabCompleter {

    private final StuffPlugin plugin;

    public InvseeCommand(StuffPlugin plugin) {
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
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
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

        plugin.getInvseeSessions().put(staff.getUniqueId(), new StuffPlugin.InvseeSession(staff.getUniqueId(), target.getUniqueId(), inv));

        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getInvseeOpened().replace("{player}", target.getName())));
        staff.openInventory(inv);
    }

    /**
     * Refreshes the custom invsee inventory layout and syncing with the target player's stats/items.
     */
    public static void updateInvseeContents(Inventory inv, Player target) {
        // 1. Copy main inventory slots (0 - 35)
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, target.getInventory().getItem(i));
        }

        // 2. Add visual separators (slots 36 - 44)
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            sepMeta.displayName(MiniMessageUtils.parse(" "));
            separator.setItemMeta(sepMeta);
        }
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, separator);
        }

        // 3. Add armor slots (slots 45 - 48)
        inv.setItem(45, target.getInventory().getHelmet());
        inv.setItem(46, target.getInventory().getChestplate());
        inv.setItem(47, target.getInventory().getLeggings());
        inv.setItem(48, target.getInventory().getBoots());

        // 4. Add off-hand slot (slot 49)
        inv.setItem(49, target.getInventory().getItemInOffHand());

        // 5. Ender Chest shortcut (slot 50)
        ItemStack echest = new ItemStack(Material.ENDER_CHEST);
        ItemMeta ecMeta = echest.getItemMeta();
        if (ecMeta != null) {
            ecMeta.displayName(MiniMessageUtils.parse("<color:#00E262>Ender Chest"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Click to inspect target's Ender Chest."));
            ecMeta.lore(lore);
            echest.setItemMeta(ecMeta);
        }
        inv.setItem(50, echest);

        // 6. Active Potion Effects (slot 51)
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

        // 7. Health & Food stats (slot 52)
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta appMeta = apple.getItemMeta();
        if (appMeta != null) {
            appMeta.displayName(MiniMessageUtils.parse("<color:#E20000>Target Health & Hunger"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Health: <color:#E20000>" + String.format("%.1f", target.getHealth()) + " / " + target.getMaxHealth()));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>Food Level: <color:#E2B700>" + target.getFoodLevel() + " / 20"));
            lore.add(MiniMessageUtils.parse("<color:#A0A0A0>XP Level: <color:#00E262>" + target.getLevel()));
            appMeta.lore(lore);
            apple.setItemMeta(appMeta);
        }
        inv.setItem(52, apple);

        // 8. Close status slot (slot 53)
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barMeta = barrier.getItemMeta();
        if (barMeta != null) {
            barMeta.displayName(MiniMessageUtils.parse("<color:#E20000>Close Viewer"));
            barrier.setItemMeta(barMeta);
        }
        inv.setItem(53, barrier);
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
