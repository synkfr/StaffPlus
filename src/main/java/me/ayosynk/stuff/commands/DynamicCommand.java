package me.ayosynk.stuff.commands;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DynamicCommand extends org.bukkit.command.Command {

    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    public DynamicCommand(@NotNull String name, @NotNull String description, @NotNull String usageMessage, @NotNull List<String> aliases, @NotNull CommandExecutor executor, @Nullable TabCompleter tabCompleter) {
        super(name, description, usageMessage, aliases);
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (tabCompleter != null) {
            List<String> list = tabCompleter.onTabComplete(sender, this, alias, args);
            if (list != null) {
                return list;
            }
        }
        return super.tabComplete(sender, alias, args);
    }
}
