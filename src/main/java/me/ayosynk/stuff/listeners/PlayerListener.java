package me.ayosynk.stuff.listeners;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.commands.InvseeCommand;
import me.ayosynk.stuff.commands.InvseeHolder;
import me.ayosynk.stuff.database.Punishment;
import me.ayosynk.stuff.utils.DurationUtils;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
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
import me.ayosynk.stuff.database.DatabaseManager;

public class PlayerListener implements Listener {

    private final StuffPlugin plugin;

    public PlayerListener(StuffPlugin plugin) {
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

        // 1. Cache name and save/update record in Database
        plugin.cacheName(name);
        plugin.getDatabaseManager().savePlayer(uuid, name, ip).thenRun(() -> {
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
                                if (staff.hasPermission("stuff.admin")) {
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
            if (!player.hasPermission("stuff.vanish.see")) {
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
                    if (!other.hasPermission("stuff.vanish.see")) {
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
        for (StuffPlugin.InvseeSession session : plugin.getInvseeSessions().values()) {
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
            StuffPlugin.SpectatorState state = plugin.getMonitorStates().get(staffUuid);
            if (state != null && state.getTargetUuid().equals(uuid)) {
                Player staff = Bukkit.getPlayer(staffUuid);
                if (staff != null && staff.isOnline()) {
                    plugin.restoreSpectatorState(staff, true);
                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getMonitorTargetOffline()));
                }
            }
        }

        // 4. If target of invsee quits, close open GUIs for all viewing staff
        for (StuffPlugin.InvseeSession session : plugin.getInvseeSessions().values()) {
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
}
