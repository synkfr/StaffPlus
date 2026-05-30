package me.ayosynk.stuff.commands;

import me.ayosynk.stuff.StuffPlugin;
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FlyCommand implements CommandExecutor, TabCompleter {

    private final StuffPlugin plugin;

    public FlyCommand(StuffPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerOnly()));
                return true;
            }

            Player player = (Player) sender;
            SchedulerUtils.runEntity(plugin, player, () -> toggleFly(player, player));
        } else {
            if (!sender.hasPermission("stuff.fly.others")) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoPermission()));
                return true;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null || !target.isOnline() || !plugin.canSee(sender, target)) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetName)));
                return true;
            }

            SchedulerUtils.runEntity(plugin, target, () -> toggleFly(sender, target));
        }
        return true;
    }

    private void toggleFly(CommandSender sender, Player target) {
        boolean fly = !target.getAllowFlight();
        target.setAllowFlight(fly);
        target.setFlying(fly);

        String state = fly ? "<color:#00E262>enabled" : "<color:#E20000>disabled";
        
        target.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>Flight mode has been " + state + "<color:#A0A0A0>."));
        
        if (!(sender instanceof Player) || !((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>Flight mode " + state + "<color:#A0A0A0> for <color:#00E262>" + target.getName() + "<color:#A0A0A0>."));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("stuff.fly.others")) {
            String input = args[0].toLowerCase();
            return plugin.getVisibleOnlinePlayers(sender).stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
