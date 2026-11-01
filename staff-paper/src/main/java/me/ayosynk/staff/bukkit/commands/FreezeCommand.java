package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FreezeCommand implements CommandExecutor, TabCompleter {

    private final StaffBukkitPlugin plugin;

    public FreezeCommand(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!plugin.getPluginConfig().isFreezeEnabled()) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Freeze system is currently disabled."));
            return true;
        }

        boolean isUnfreeze = label.equalsIgnoreCase("unfreeze");

        if (args.length < 1) {
            String usage = isUnfreeze ? "<color:#E20000>Usage: /unfreeze <player>" : "<color:#E20000>Usage: /freeze <player> [reason]";
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + usage));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline() || (sender instanceof Player && !plugin.canSee((Player) sender, target))) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
            return true;
        }

        if (isUnfreeze) {
            if (!plugin.getFreezeManager().isFrozen(target.getUniqueId())) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getFreezeNotFrozen().replace("{player}", target.getName())));
                return true;
            }
            plugin.getFreezeManager().unfreeze(target, sender);
            return true;
        }

        if (sender instanceof Player staff && target.getUniqueId().equals(staff.getUniqueId())) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>You cannot freeze yourself."));
            return true;
        }

        if (plugin.getFreezeManager().isFrozen(target.getUniqueId())) {
            plugin.getFreezeManager().unfreeze(target, sender);
            return true;
        }

        if (sender instanceof Player staff) {
            int staffWeight = plugin.getHierarchyWeight(staff);
            int targetWeight = plugin.getHierarchyWeight(target);
            if (targetWeight >= staffWeight) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getCannotFreezeStaff()));
                return true;
            }
        }

        String reason = "Screenshare";
        if (args.length > 1) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        plugin.getFreezeManager().freeze(target, sender, reason);
        return true;
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
        if (args.length == 2 && !alias.equalsIgnoreCase("unfreeze")) {
            String input = args[1].toLowerCase();
            return List.of("Screenshare", "Suspected Cheating", "Hacking Investigation").stream()
                    .filter(r -> r.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
