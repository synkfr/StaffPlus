package me.ayosynk.staff.bukkit.listeners;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.commands.InvseeCommand;
import me.ayosynk.staff.bukkit.commands.InvseeHolder;
import me.ayosynk.staff.bukkit.commands.StaffInfoHolder;
import me.ayosynk.staff.database.Punishment;
import me.ayosynk.staff.utils.DurationUtils;
import me.ayosynk.staff.bukkit.utils.MiniMessageUtils;
import me.ayosynk.staff.bukkit.utils.SchedulerUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import me.ayosynk.staff.database.DatabaseManager;

public class PlayerListener implements Listener {

    private final StaffBukkitPlugin plugin;

    public PlayerListener(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enforce active bans and IP-bans asynchronously during pre-login.
     */
    private static final java.text.SimpleDateFormat DATE_FORMAT = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        // 0. Check IP-ban allow exemption
        boolean isExempt = false;
        try {
            isExempt = plugin.getDatabaseManager().isAllowed(uuid).join();
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking IP-ban allow exemption for " + uuid + ": " + e.getMessage());
        }

        if (!isExempt) {
            // 1. Check IP Ban
            try {
                Punishment ipBan = plugin.getDatabaseManager().getActivePunishment(uuid, ip, Punishment.Type.IP_BAN).join();
                if (ipBan != null) {
                    String timeStr = ipBan.getEndTime() != null ? DurationUtils.formatDuration(ipBan.getEndTime().getTime() - System.currentTimeMillis()) : "Permanent";
                    
                    String staffName = "Console";
                    if (ipBan.getPunisherUuid() != null) {
                        String queryName = plugin.getDatabaseManager().getPlayerNameByUuid(ipBan.getPunisherUuid()).join();
                        if (queryName != null) {
                            staffName = queryName;
                        }
                    }
                    String dateStr = DATE_FORMAT.format(ipBan.getStartTime());

                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, MiniMessageUtils.parse(plugin.getMessageConfig().getBanKickMessage()
                            .replace("{reason}", "IP Ban: " + ipBan.getReason())
                            .replace("{time}", timeStr)
                            .replace("{staff}", staffName)
                            .replace("{date}", dateStr)));
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error checking IP ban for " + uuid + ": " + e.getMessage());
            }
        }

        // 2. Check Player Ban
        try {
            Punishment ban = plugin.getDatabaseManager().getActivePunishment(uuid, ip, Punishment.Type.BAN).join();
            if (ban != null) {
                String timeStr = ban.getEndTime() != null ? DurationUtils.formatDuration(ban.getEndTime().getTime() - System.currentTimeMillis()) : "Permanent";
                
                String staffName = "Console";
                if (ban.getPunisherUuid() != null) {
                    String queryName = plugin.getDatabaseManager().getPlayerNameByUuid(ban.getPunisherUuid()).join();
                    if (queryName != null) {
                        staffName = queryName;
                    }
                }
                String dateStr = DATE_FORMAT.format(ban.getStartTime());

                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, MiniMessageUtils.parse(plugin.getMessageConfig().getBanKickMessage()
                        .replace("{reason}", ban.getReason())
                        .replace("{time}", timeStr)
                        .replace("{staff}", staffName)
                        .replace("{date}", dateStr)));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking ban for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Cache name on join and hide vanished players.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();

        // Hide join message if vanished
        if (plugin.isVanished(uuid)) {
            event.joinMessage(null);
        }

        // Determine player weight from permissions node: staff.hierarchy.weight.<number>
        int weight = 0;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase();
            if (perm.startsWith("staff.hierarchy.weight.")) {
                try {
                    int w = Integer.parseInt(perm.substring("staff.hierarchy.weight.".length()));
                    if (w > weight) {
                        weight = w;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 1. Cache name and save/update record in Database
        plugin.cacheName(name);
        plugin.getDatabaseManager().savePlayer(uuid, name, ip, weight).thenRun(() -> {
            // Asynchronously scan for alt accounts registered on this IP
            plugin.getDatabaseManager().getAltsByIp(ip).thenAccept(alts -> {
                if (alts.size() > 1) {
                    List<String> altNames = new ArrayList<>();
                    List<CompletableFuture<Void>> futures = new ArrayList<>();

                    for (DatabaseManager.PlayerRecord alt : alts) {
                        if (alt.uuid.equals(uuid)) continue; // Skip joining player

                        CompletableFuture<Void> future = plugin.getDatabaseManager()
                                .getActivePunishment(alt.uuid, alt.ip, Punishment.Type.BAN)
                                .thenAccept(ban -> {
                                    if (ban != null) {
                                        altNames.add("<color:#E20000>" + alt.name + " (Banned)</color>");
                                    } else {
                                        altNames.add("<color:#00E262>" + alt.name + "</color>");
                                    }
                                });
                        futures.add(future);
                    }

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                        if (!altNames.isEmpty()) {
                            String altListStr = String.join("<color:#A0A0A0>, </color>", altNames);
                            String alertMsg = "<color:#E2B700>[Alt Alert]</color> <color:#00E262>" + name + "</color> has registered accounts on this IP: " + altListStr;

                            // Broadcast to online staff members
                            for (Player staff : Bukkit.getOnlinePlayers()) {
                                if (staff.hasPermission("staff.admin")) {
                                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + alertMsg));
                                }
                            }
                        }
                    });
                }
            });

            // Asynchronously query and display unread warnings on join
            plugin.getDatabaseManager().getWarnings(uuid).thenAccept(warns -> {
                if (!warns.isEmpty()) {
                    player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E2B700>Welcome back! You have active warnings:</color>"));
                    for (Punishment w : warns) {
                        String dateStr = DATE_FORMAT.format(w.getStartTime());
                        plugin.getDatabaseManager().getPlayerNameByUuid(w.getPunisherUuid()).thenAccept(staffName -> {
                            String punisher = staffName != null ? staffName : "Console";
                            player.sendMessage(MiniMessageUtils.parse("<color:#A0A0A0>- " + w.getReason() + " by " + punisher + " (" + dateStr + ")</color>"));
                        });
                    }
                }
            });
        });

        // 2. Handle Vanish hiding
        SchedulerUtils.runEntity(plugin, player, () -> {
            // Hide existing vanished players from the new joiner if they don't have bypass permission
            if (!player.hasPermission("staff.vanish.see")) {
                for (UUID vanishedUuid : plugin.getVanishedPlayers()) {
                    Player vanished = Bukkit.getPlayer(vanishedUuid);
                    if (vanished != null && vanished.isOnline()) {
                        player.hidePlayer(plugin, vanished);
                    }
                }
            }

            // If the joiner is vanished (persistent or reload), hide them from others
            if (plugin.isVanished(uuid)) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.getUniqueId().equals(uuid)) continue;
                    if (!other.hasPermission("staff.vanish.see")) {
                        SchedulerUtils.runEntity(plugin, other, () -> other.hidePlayer(plugin, player));
                    }
                }
                player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getVanishEnabled()));
            }
        });
    }

    /**
     * Enforce mutes asynchronously on chat events.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress().getAddress().getHostAddress();

        try {
            Punishment mute = plugin.getDatabaseManager().getActivePunishment(uuid, ip, Punishment.Type.MUTE).join();
            if (mute != null) {
                event.setCancelled(true);
                String timeStr = mute.getEndTime() != null ? DurationUtils.formatDuration(mute.getEndTime().getTime() - System.currentTimeMillis()) : "Permanent";
                // Send chat warning back to the player on their entity thread
                SchedulerUtils.runEntity(plugin, player, () -> {
                    player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getYouAreMuted()
                            .replace("{time}", timeStr)
                            .replace("{reason}", mute.getReason())));
                });
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking mute for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle invsee GUI clicks and sync with target.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof StaffInfoHolder) {
            handleStaffInfoClick(event);
            return;
        }

        if (!(event.getInventory().getHolder() instanceof InvseeHolder)) {
            return;
        }

        InvseeHolder holder = (InvseeHolder) event.getInventory().getHolder();
        Player target = holder.getTarget();
        Player staff = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // 1. Read-only and button slots
        if (slot >= 36 && slot <= 44) {
            event.setCancelled(true);
            return;
        }

        if (slot == 50) { // Ender Chest shortcut
            event.setCancelled(true);
            SchedulerUtils.runEntity(plugin, staff, () -> {
                staff.closeInventory();
                staff.openInventory(target.getEnderChest());
            });
            return;
        }

        if (slot >= 51 && slot <= 52) { // Read-only Stats
            event.setCancelled(true);
            return;
        }

        if (slot == 53) { // Close button
            event.setCancelled(true);
            SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
            return;
        }

        // 2. Allow item movement inside normal inventory and armor, then sync to target on the next tick
        SchedulerUtils.runEntity(plugin, target, () -> {
            // Copy contents from custom inventory to target player
            Inventory customInv = event.getInventory();

            // Sync main inventory (slots 0 - 35)
            for (int i = 0; i < 36; i++) {
                target.getInventory().setItem(i, customInv.getItem(i));
            }

            // Sync armor (slots 45 - 48)
            target.getInventory().setHelmet(customInv.getItem(45));
            target.getInventory().setChestplate(customInv.getItem(46));
            target.getInventory().setLeggings(customInv.getItem(47));
            target.getInventory().setBoots(customInv.getItem(48));

            // Sync off-hand (slot 49)
            target.getInventory().setItemInOffHand(customInv.getItem(49));
        });

        // Trigger GUI refresh to update read-only stats
        SchedulerUtils.runEntity(plugin, staff, () -> {
            InvseeCommand.updateInvseeContents(event.getInventory(), target);
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder)) {
            return;
        }

        InvseeHolder holder = (InvseeHolder) event.getInventory().getHolder();
        Player target = holder.getTarget();
        Player staff = (Player) event.getWhoClicked();

        // If drag impacts any visual separators or stat slots, cancel it
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 36 && rawSlot <= 44 || rawSlot >= 50 && rawSlot <= 53) {
                event.setCancelled(true);
                return;
            }
        }

        // Sync main inventory/armor on next tick
        SchedulerUtils.runEntity(plugin, target, () -> {
            Inventory customInv = event.getInventory();
            for (int i = 0; i < 36; i++) {
                target.getInventory().setItem(i, customInv.getItem(i));
            }
            target.getInventory().setHelmet(customInv.getItem(45));
            target.getInventory().setChestplate(customInv.getItem(46));
            target.getInventory().setLeggings(customInv.getItem(47));
            target.getInventory().setBoots(customInv.getItem(48));
            target.getInventory().setItemInOffHand(customInv.getItem(49));
        });

        SchedulerUtils.runEntity(plugin, staff, () -> {
            InvseeCommand.updateInvseeContents(event.getInventory(), target);
        });
    }

    /**
     * Real-time sync: when target updates inventory, update all open invsee GUIs.
     * Thread-safe under Folia: iterates over registered sessions instead of accessing other players' open inventories directly.
     */
    private void refreshTargetViewers(Player target) {
        for (StaffBukkitPlugin.InvseeSession session : plugin.getInvseeSessions().values()) {
            if (session.getTargetUuid().equals(target.getUniqueId())) {
                Player staff = Bukkit.getPlayer(session.getStaffUuid());
                if (staff != null && staff.isOnline()) {
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        InvseeCommand.updateInvseeContents(session.getInventory(), target);
                    });
                }
            }
        }
    }

    /**
     * Clean up invsee session mapping when a staff member closes the invsee GUI.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player staff = (Player) event.getPlayer();
        
        // Remove active invsee session when staff closes the inventory
        plugin.getInvseeSessions().remove(staff.getUniqueId());
    }

    @EventHandler
    public void onTargetPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            refreshTargetViewers((Player) event.getEntity());
        }
    }

    @EventHandler
    public void onTargetDrop(PlayerDropItemEvent event) {
        refreshTargetViewers(event.getPlayer());
    }

    @EventHandler
    public void onTargetHeldItemChange(PlayerItemHeldEvent event) {
        refreshTargetViewers(event.getPlayer());
    }

    @EventHandler
    public void onTargetSwapHand(PlayerSwapHandItemsEvent event) {
        refreshTargetViewers(event.getPlayer());
    }

    @EventHandler
    public void onTargetInteract(PlayerInteractEvent event) {
        refreshTargetViewers(event.getPlayer());
    }

    /**
     * Restore monitored players and clean up invsee sessions when they or the target player quits.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Hide quit message if vanished
        if (plugin.isVanished(uuid)) {
            event.quitMessage(null);
        }

        // 1. If staff quit while monitoring, restore their location and state before they leave!
        if (plugin.getMonitorStates().containsKey(uuid)) {
            plugin.restoreSpectatorState(player, false);
        }

        // 2. Clean up invsee session if staff quits
        plugin.getInvseeSessions().remove(uuid);

        // 3. If a monitored target quits, restore any staff monitoring them
        for (UUID staffUuid : plugin.getMonitorStates().keySet()) {
            StaffBukkitPlugin.SpectatorState state = plugin.getMonitorStates().get(staffUuid);
            if (state != null && state.getTargetUuid().equals(uuid)) {
                Player staff = Bukkit.getPlayer(staffUuid);
                if (staff != null && staff.isOnline()) {
                    plugin.restoreSpectatorState(staff, true);
                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getMonitorTargetOffline()));
                }
            }
        }

        // 4. If target of invsee quits, close open GUIs for all viewing staff
        for (StaffBukkitPlugin.InvseeSession session : plugin.getInvseeSessions().values()) {
            if (session.getTargetUuid().equals(uuid)) {
                Player staff = Bukkit.getPlayer(session.getStaffUuid());
                if (staff != null && staff.isOnline()) {
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        staff.closeInventory();
                        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Target player has logged out. Closing view."));
                    });
                }
            }
        }
    }

    private int getHierarchyWeight(Player player) {
        int maxWeight = 0;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase();
            if (perm.startsWith("staff.hierarchy.weight.")) {
                try {
                    int weight = Integer.parseInt(perm.substring("staff.hierarchy.weight.".length()));
                    if (weight > maxWeight) {
                        maxWeight = weight;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxWeight;
    }

    private void handleStaffInfoClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        StaffInfoHolder holder = (StaffInfoHolder) event.getInventory().getHolder();
        UUID targetUuid = holder.getTargetUuid();
        String targetName = holder.getTargetName();
        boolean isOnline = holder.isOnline();

        String action = holder.getAction(slot);
        if (action == null) return;

        switch (action) {
            case "close_gui":
                SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
                break;

            case "teleport_target":
                if (!isOnline) return;
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null && target.isOnline()) {
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        staff.closeInventory();
                        staff.teleportAsync(target.getLocation());
                        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#00E262>Teleported to " + target.getName()));
                    });
                }
                break;

            case "inspect_inventory":
                if (!isOnline) return;
                Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        staff.closeInventory();
                        new me.ayosynk.staff.bukkit.commands.InvseeCommand(plugin).openInvsee(staff, targetPlayer);
                    });
                }
                break;

            case "warnings_log":
                SchedulerUtils.runAsync(plugin, () -> {
                    plugin.getDatabaseManager().getWarnings(targetUuid).thenAccept(warns -> {
                        if (warns.isEmpty()) return;

                        List<CompletableFuture<Integer>> futures = new ArrayList<>();
                        for (Punishment w : warns) {
                            futures.add(plugin.getDatabaseManager().getPlayerWeight(w.getPunisherUuid()));
                        }

                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                            int executorWeight = getHierarchyWeight(staff);
                            boolean blocked = false;
                            UUID higherStaffUuid = null;
                            for (int i = 0; i < warns.size(); i++) {
                                int punisherWeight = futures.get(i).join();
                                if (executorWeight < punisherWeight) {
                                    blocked = true;
                                    higherStaffUuid = warns.get(i).getPunisherUuid();
                                    break;
                                }
                            }

                            if (blocked) {
                                final UUID finalStaffUuid = higherStaffUuid;
                                if (finalStaffUuid != null) {
                                    plugin.getDatabaseManager().getPlayerNameByUuid(finalStaffUuid).thenAccept(name -> {
                                        String finalName = name != null ? name : "Console";
                                        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + 
                                                plugin.getMessageConfig().getCannotOverwritePunishment().replace("{staff}", finalName)));
                                    });
                                } else {
                                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + 
                                            plugin.getMessageConfig().getCannotOverwritePunishment().replace("{staff}", "Console")));
                                }
                                return;
                            }

                            plugin.getDatabaseManager().clearWarnings(targetUuid).thenAccept(success -> {
                                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getWarnCleared().replace("{player}", targetName)));
                                SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
                            });
                        });
                    });
                });
                break;

            case "exemption_status":
                SchedulerUtils.runAsync(plugin, () -> {
                    plugin.getDatabaseManager().isAllowed(targetUuid).thenAccept(allowed -> {
                        if (allowed) {
                            plugin.getDatabaseManager().removeAllow(targetUuid).thenAccept(success -> {
                                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerUnallowed().replace("{player}", targetName)));
                                SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
                            });
                        } else {
                            plugin.getDatabaseManager().addAllow(targetUuid).thenRun(() -> {
                                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerAllowed().replace("{player}", targetName)));
                                SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
                            });
                        }
                    });
                });
                break;

            case "view_history":
                SchedulerUtils.runEntity(plugin, staff, () -> {
                    staff.closeInventory();
                    staff.performCommand("history " + targetName);
                });
                break;

            case "kick_player":
                if (!isOnline) return;
                Player kickTarget = Bukkit.getPlayer(targetUuid);
                if (kickTarget != null && kickTarget.isOnline()) {
                    SchedulerUtils.runEntity(plugin, kickTarget, () -> {
                        kickTarget.kick(MiniMessageUtils.parse("<color:#E20000>Kicked by staff member: " + staff.getName()));
                        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#00E262>Kicked player " + kickTarget.getName()));
                    });
                    SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
                }
                break;

            case "warn_player":
                SchedulerUtils.runEntity(plugin, staff, () -> {
                    staff.closeInventory();
                    staff.performCommand("warn " + targetName + " GUI Warning");
                });
                break;

            case "mute_player":
                SchedulerUtils.runEntity(plugin, staff, () -> {
                    staff.closeInventory();
                    staff.performCommand("tempmute " + targetName + " 1h GUI Mute");
                });
                break;

            case "ban_player":
                SchedulerUtils.runEntity(plugin, staff, () -> {
                    staff.closeInventory();
                    staff.performCommand("ban " + targetName + " GUI Ban");
                });
                break;
        }
    }
}
