package me.ayosynk.stuff.commands;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.migration.MigrationManager;
import me.ayosynk.stuff.migration.MigrationSource;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ImportCommand implements CommandExecutor, TabCompleter {

    private final StuffPlugin plugin;
    private final MigrationManager migrationManager;
    private final AtomicBoolean migrating = new AtomicBoolean(false);

    public ImportCommand(StuffPlugin plugin, MigrationManager migrationManager) {
        this.plugin = plugin;
        this.migrationManager = migrationManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("stuff.import")) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoPermission()));
            return true;
        }

        if (args.length == 0) {
            sendOverview(sender);
            return true;
        }

        String sourceName = args[0].toLowerCase();
        MigrationSource source = migrationManager.getSource(sourceName);

        if (source == null) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Unknown migration source! Use /stuffimport to view active sources."));
            return true;
        }

        if (migrating.getAndSet(true)) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>A migration is already in progress! Please wait until it completes."));
            return true;
        }

        sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>Starting asynchronous import from <color:#00E262>" + source.getName() + "<color:#A0A0A0>... Please wait..."));

        source.migrate(plugin, sender, args).thenAccept(count -> {
            migrating.set(false);
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<gradient:#00E262:#00FF7F>Successfully imported " + count + " punishments from " + source.getName() + "!</gradient>"));
        }).exceptionally(ex -> {
            migrating.set(false);
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Migration failed: " + ex.getCause().getMessage()));
            return null;
        });

        return true;
    }

    private void sendOverview(CommandSender sender) {
        sender.sendMessage(MiniMessageUtils.parse("<gradient:#00E262:#00FF7F>★ Stuff+ Modern Importer & Migration System ★</gradient>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#808080>--------------------------------------------------</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#A0A0A0>Scanning local configurations...</color>"));

        Map<String, String> scans = migrationManager.scanDetectedSources();
        for (String source : scans.keySet()) {
            sender.sendMessage(MiniMessageUtils.parse("<color:#A0A0A0> » </color>" + scans.get(source)));
        }

        sender.sendMessage(MiniMessageUtils.parse("<color:#808080>--------------------------------------------------</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#A0A0A0>Available Imports:</color>"));
        for (MigrationSource source : migrationManager.getSources()) {
            sender.sendMessage(MiniMessageUtils.parse("<color:#A0A0A0> • </color><color:#00E262>/stuffimport " + source.getName() + "</color> - <color:#707070>" + source.getDescription() + "</color>"));
        }
        sender.sendMessage(MiniMessageUtils.parse("<color:#808080>--------------------------------------------------</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#707070>To connect to a remote SQL source explicitly, use:</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#00E262>/stuffimport <litebans/advancedban/maxbans/banmanager/bat> <jdbcUrl> <user> <pass> [prefix]</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#707070>Example: /stuffimport litebans jdbc:mysql://localhost:3306/bans root pass litebans_</color>"));
        sender.sendMessage(MiniMessageUtils.parse("<color:#808080>--------------------------------------------------</color>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("stuff.import")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return migrationManager.getSources().stream()
                    .map(MigrationSource::getName)
                    .filter(name -> name.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
