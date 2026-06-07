package me.ayosynk.staff.bukkit.commands;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private final StaffBukkitPlugin plugin;
    private static final Pattern IP_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public StaffCommand(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("staff.staff.reload") && !sender.hasPermission("staff.staff") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getNoPermission()));
                return true;
            }
            try {
                plugin.getPluginConfig().load(true);
                plugin.getMessageConfig().load(true);
                plugin.getMenuManager().load();
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#00E262>Configurations and menus reloaded successfully."));
            } catch (Exception e) {
                sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Error reloading configurations: " + e.getMessage()));
                plugin.getLogger().severe("Error reloading configurations: " + e.getMessage());
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerOnly()));
            return true;
        }

        Player staff = (Player) sender;

        if (args.length < 2 || !args[0].equalsIgnoreCase("info")) {
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /staff info <player> or /staff reload"));
            return true;
        }

        String targetInput = args[1];

        // Run async lookup to prevent thread locking under Folia
        SchedulerUtils.runAsync(plugin, () -> handleInfoAsync(staff, targetInput));
        return true;
    }

    private void handleInfoAsync(Player staff, String targetInput) {
        resolveTarget(targetInput).thenAccept(target -> {
            if (target == null) {
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", targetInput)));
                return;
            }

            // Fetch all database records in parallel
            CompletableFuture<List<Punishment>> warningsFuture = plugin.getDatabaseManager().getWarnings(target.uuid);
            CompletableFuture<List<me.ayosynk.staff.database.DatabaseManager.PlayerRecord>> altsFuture = 
                    (target.ip != null && !target.ip.isEmpty()) 
                    ? plugin.getDatabaseManager().getAltsByIp(target.ip) 
                    : CompletableFuture.completedFuture(Collections.emptyList());
            CompletableFuture<Boolean> allowedFuture = plugin.getDatabaseManager().isAllowed(target.uuid);
            CompletableFuture<Integer> weightFuture = plugin.getDatabaseManager().getPlayerWeight(target.uuid);

            CompletableFuture.allOf(warningsFuture, altsFuture, allowedFuture, weightFuture).thenAccept(v -> {
                List<Punishment> warnings = warningsFuture.join();
                List<me.ayosynk.staff.database.DatabaseManager.PlayerRecord> alts = altsFuture.join();
                boolean isAllowed = allowedFuture.join();
                int weight = weightFuture.join();

                // Open the inventory GUI on the staff player's regional entity thread
                SchedulerUtils.runEntity(plugin, staff, () -> {
                    openStaffInfoGui(staff, target, warnings, alts, isAllowed, weight);
                });
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Error loading staff info for " + target.name + ": " + ex.getMessage());
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Database error loading profile details."));
                return null;
            });
        });
    }

    private void openStaffInfoGui(Player staff, ResolvedTarget target, List<Punishment> warnings, List<me.ayosynk.staff.database.DatabaseManager.PlayerRecord> alts, boolean isAllowed, int weight) {
        Player targetPlayer = Bukkit.getPlayer(target.uuid);
        boolean isOnline = targetPlayer != null && targetPlayer.isOnline() && plugin.canSee(staff, targetPlayer);

        me.ayosynk.staff.bukkit.config.StaffInfoMenuConfig config = plugin.getMenuManager().getStaffInfoMenuConfig();

        StaffInfoHolder holder = new StaffInfoHolder(target.uuid, target.name, target.ip, isOnline);
        String guiTitle = replacePlaceholders(config.name, target, isOnline, targetPlayer, weight, warnings, alts, isAllowed);
        Inventory inv = Bukkit.createInventory(holder, config.size, MiniMessageUtils.parse(guiTitle));
        holder.setInventory(inv);

        Set<Integer> populatedSlots = new java.util.HashSet<>();

        for (Map.Entry<String, me.ayosynk.staff.bukkit.config.StaffInfoMenuConfig.MenuItem> entry : config.items.entrySet()) {
            String actionKey = entry.getKey();
            me.ayosynk.staff.bukkit.config.StaffInfoMenuConfig.MenuItem itemConfig = entry.getValue();

            List<Integer> slots = new ArrayList<>();
            if (itemConfig.slots != null && !itemConfig.slots.isEmpty()) {
                slots.addAll(itemConfig.slots);
            } else if (itemConfig.slot >= 0) {
                slots.add(itemConfig.slot);
            }

            if (slots.isEmpty()) continue;

            boolean isOnlineOnly = actionKey.equals("teleport_target") || 
                                   actionKey.equals("inspect_inventory") || 
                                   actionKey.equals("kick_player");

            ItemStack item;
            if (isOnlineOnly && !isOnline) {
                item = makeDisabledPane(itemConfig.item.name);
            } else {
                Material mat = Material.matchMaterial(itemConfig.item.material);
                if (mat == null) {
                    mat = Material.STONE;
                }

                if (actionKey.equals("exemption_status") && !isAllowed && mat == Material.BEACON) {
                    mat = Material.ANVIL;
                }

                item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                        ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(target.uuid));
                    }

                    String displayName = replacePlaceholders(itemConfig.item.name, target, isOnline, targetPlayer, weight, warnings, alts, isAllowed);
                    meta.displayName(MiniMessageUtils.parse(displayName));

                    List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
                    for (String line : itemConfig.item.lore) {
                        if (line.contains("{warnings_list}")) {
                            if (warnings.isEmpty()) {
                                loreComponents.add(MiniMessageUtils.parse("<color:#A0A0A0>No active warnings logged."));
                            } else {
                                for (Punishment w : warnings) {
                                    loreComponents.add(MiniMessageUtils.parse("<color:#A0A0A0>- " + w.getReason() + " (" + DATE_FORMAT.format(w.getStartTime()) + ")"));
                                }
                            }
                        } else {
                            loreComponents.add(MiniMessageUtils.parse(replacePlaceholders(line, target, isOnline, targetPlayer, weight, warnings, alts, isAllowed)));
                        }
                    }
                    meta.lore(loreComponents);
                    item.setItemMeta(meta);
                }
            }

            for (int s : slots) {
                if (s >= 0 && s < config.size) {
                    inv.setItem(s, item);
                    holder.registerAction(s, actionKey);
                    populatedSlots.add(s);
                }
            }
        }

        if (config.fillItem != null) {
            Material fillMat = Material.matchMaterial(config.fillItem.material);
            if (fillMat != null) {
                ItemStack fillStack = new ItemStack(fillMat);
                ItemMeta fillMeta = fillStack.getItemMeta();
                if (fillMeta != null) {
                    fillMeta.displayName(MiniMessageUtils.parse(config.fillItem.name));
                    List<net.kyori.adventure.text.Component> fillLore = new ArrayList<>();
                    for (String line : config.fillItem.lore) {
                        fillLore.add(MiniMessageUtils.parse(line));
                    }
                    fillMeta.lore(fillLore);
                    fillStack.setItemMeta(fillMeta);
                }

                for (int i = 0; i < config.size; i++) {
                    if (!populatedSlots.contains(i)) {
                        inv.setItem(i, fillStack);
                    }
                }
            }
        }

        staff.openInventory(inv);
    }

    private ItemStack makeDisabledPane(String name) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessageUtils.parse("<color:#E20000>" + cleanName(name) + " <color:#A0A0A0>(Offline)"));
            meta.lore(Collections.singletonList(MiniMessageUtils.parse("<color:#A0A0A0>Action unavailable while target is offline.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("<[^>]*>", "");
    }

    private String replacePlaceholders(String text, ResolvedTarget target, boolean isOnline, Player targetPlayer, int weight, List<Punishment> warnings, List<me.ayosynk.staff.database.DatabaseManager.PlayerRecord> alts, boolean isAllowed) {
        if (text == null) return "";
        return text.replace("{player}", target.name)
                .replace("{uuid}", target.uuid != null ? target.uuid.toString() : "N/A")
                .replace("{ip}", target.ip != null && !target.ip.isEmpty() ? target.ip : "N/A")
                .replace("{weight}", String.valueOf(weight))
                .replace("{status}", isOnline ? "<color:#00E262>Online" : "<color:#E20000>Offline")
                .replace("{gamemode}", isOnline && targetPlayer != null ? targetPlayer.getGameMode().name() : "N/A")
                .replace("{flying}", isOnline && targetPlayer != null ? (targetPlayer.isFlying() ? "Yes" : "No") : "N/A")
                .replace("{vanished}", isOnline && targetPlayer != null ? (plugin.isVanished(target.uuid) ? "Yes" : "No") : "N/A")
                .replace("{ping}", isOnline && targetPlayer != null ? String.valueOf(targetPlayer.getPing()) : "N/A")
                .replace("{world}", isOnline && targetPlayer != null ? targetPlayer.getLocation().getWorld().getName() : "N/A")
                .replace("{x}", isOnline && targetPlayer != null ? String.format("%.1f", targetPlayer.getLocation().getX()) : "0.0")
                .replace("{y}", isOnline && targetPlayer != null ? String.format("%.1f", targetPlayer.getLocation().getY()) : "0.0")
                .replace("{z}", isOnline && targetPlayer != null ? String.format("%.1f", targetPlayer.getLocation().getZ()) : "0.0")
                .replace("{warnings}", String.valueOf(warnings.size()))
                .replace("{alts}", String.valueOf(alts.size() > 1 ? alts.size() - 1 : 0))
                .replace("{allowed}", isAllowed ? "<color:#00E262>Yes" : "<color:#E20000>No")
                .replace("{exemption_action}", isAllowed ? "<color:#E20000>Click to REMOVE whitelist bypass." : "<color:#00E262>Click to ENABLE whitelist bypass.");
    }

    private CompletableFuture<ResolvedTarget> resolveTarget(String input) {
        CompletableFuture<ResolvedTarget> future = new CompletableFuture<>();

        if (IP_PATTERN.matcher(input).matches()) {
            future.complete(new ResolvedTarget(null, input, input));
            return future;
        }

        Player player = Bukkit.getPlayer(input);
        if (player != null && player.isOnline()) {
            future.complete(new ResolvedTarget(player.getUniqueId(), player.getName(), player.getAddress().getAddress().getHostAddress()));
            return future;
        }

        plugin.getDatabaseManager().getPlayerUuidByName(input).thenAccept(uuid -> {
            if (uuid == null) {
                future.complete(null);
                return;
            }

            plugin.getDatabaseManager().getPlayerRecord(uuid).thenAccept(record -> {
                if (record != null) {
                    future.complete(new ResolvedTarget(record.uuid, record.name, record.ip));
                } else {
                    future.complete(null);
                }
            }).exceptionally(ex -> {
                future.complete(null);
                return null;
            });
        }).exceptionally(ex -> {
            future.complete(null);
            return null;
        });

        return future;
    }

    public static class ResolvedTarget {
        public final UUID uuid;
        public final String name;
        public final String ip;

        ResolvedTarget(UUID uuid, String name, String ip) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
        }
    }

    // Tab Completion for /staff
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Arrays.asList("info", "reload").stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (Player p : plugin.getVisibleOnlinePlayers(sender)) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(p.getName());
                }
            }
            for (String regName : plugin.getRegisteredNames()) {
                if (regName.toLowerCase().startsWith(input) && !suggestions.contains(regName)) {
                    suggestions.add(regName);
                }
            }
            Collections.sort(suggestions);
            return suggestions;
        }

        return Collections.emptyList();
    }
}
