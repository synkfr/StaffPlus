package me.ayosynk.staff.bukkit.listeners;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import me.ayosynk.staff.bukkit.commands.InvseeCommand;
import me.ayosynk.staff.bukkit.commands.BansLeaderboardHolder;
import me.ayosynk.staff.bukkit.commands.BansMenuHolder;
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
import java.util.Map;
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
    private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

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
                    String dateStr = DATE_FORMAT.format(ipBan.getStartTime().toInstant());

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
                String dateStr = DATE_FORMAT.format(ban.getStartTime().toInstant());

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
                    List<String> altNames = java.util.Collections.synchronizedList(new ArrayList<>());
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
                        String dateStr = DATE_FORMAT.format(w.getStartTime().toInstant());
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

        if (plugin.getPluginConfig().isUpdateCheckerNotifyAdmins()) {
            if (player.hasPermission("staff.admin") || player.hasPermission("staff.update.notify") || player.isOp()) {
                if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
                    String updateMsg = plugin.getMessageConfig().getUpdateAvailable()
                            .replace("{latest}", plugin.getUpdateChecker().getLatestVersion())
                            .replace("{current}", plugin.getPluginVersion())
                            .replace("{url}", plugin.getUpdateChecker().getModrinthUrl());
                    player.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + updateMsg));
                }
            }
        }
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
        if (event.getInventory().getHolder() instanceof BansMenuHolder bansHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                int slot = event.getRawSlot();
                if (slot == 45 && bansHolder.getPage() > 1) {
                    BansMenuHolder.open(plugin, player, bansHolder.getPage() - 1);
                } else if (slot == 53 && bansHolder.getPage() < bansHolder.getTotalPages()) {
                    BansMenuHolder.open(plugin, player, bansHolder.getPage() + 1);
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof BansLeaderboardHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49) {
                event.getWhoClicked().closeInventory();
            }
            return;
        }

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

        StaffBukkitPlugin.InvseeSession clickSession = plugin.getInvseeSessions().get(staff.getUniqueId());
        if (clickSession != null) {
            clickSession.setLastEditMillis(System.currentTimeMillis());
        }

        // 1. Separator slots (36-43)
        if (slot >= 36 && slot <= 43) {
            event.setCancelled(true);
            return;
        }

        // 2. Strip all armor shortcut (slot 44)
        if (slot == 44) {
            event.setCancelled(true);
            List<ItemStack> removedItems = new ArrayList<>();
            for (int eqSlot = 45; eqSlot <= 49; eqSlot++) {
                ItemStack item = event.getInventory().getItem(eqSlot);
                if (item != null && !InvseeCommand.isPlaceholder(item)) {
                    removedItems.add(item.clone());
                    event.getInventory().setItem(eqSlot, InvseeCommand.createEquipmentPlaceholder(eqSlot));
                }
            }
            if (removedItems.isEmpty()) {
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#A0A0A0>Target has no armor or off-hand equipped."));
                return;
            }
            for (ItemStack removed : removedItems) {
                Map<Integer, ItemStack> leftover = staff.getInventory().addItem(removed);
                for (ItemStack drop : leftover.values()) {
                    staff.getWorld().dropItemNaturally(staff.getLocation(), drop);
                }
            }
            SchedulerUtils.runEntity(plugin, target, () -> {
                target.getInventory().setHelmet(null);
                target.getInventory().setChestplate(null);
                target.getInventory().setLeggings(null);
                target.getInventory().setBoots(null);
                target.getInventory().setItemInOffHand(null);
                target.updateInventory();
            });
            InvseeCommand.updateStatsOnly(event.getInventory(), target);
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#00E262>Removed armor and off-hand from <color:#FFFFFF>" + target.getName() + "</color>."));
            return;
        }

        // 3. Equipment slots (45-49)
        if (slot >= 45 && slot <= 49) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            boolean isPlaceholder = InvseeCommand.isPlaceholder(clicked);

            if (isPlaceholder) {
                if (cursor == null || cursor.getType().isAir()) {
                    return;
                }
                ItemStack toEquip = cursor.clone();
                toEquip.setAmount(1);
                ItemStack newCursor = cursor.clone();
                newCursor.setAmount(newCursor.getAmount() - 1);
                if (newCursor.getAmount() <= 0) {
                    newCursor = null;
                }
                event.getView().setCursor(newCursor);
                event.getInventory().setItem(slot, toEquip);

                SchedulerUtils.runEntity(plugin, target, () -> {
                    switch (slot) {
                        case 45 -> target.getInventory().setHelmet(toEquip);
                        case 46 -> target.getInventory().setChestplate(toEquip);
                        case 47 -> target.getInventory().setLeggings(toEquip);
                        case 48 -> target.getInventory().setBoots(toEquip);
                        case 49 -> target.getInventory().setItemInOffHand(toEquip);
                    }
                    target.updateInventory();
                });
            } else if (clicked != null && !clicked.getType().isAir()) {
                if (event.isShiftClick()) {
                    ItemStack removed = clicked.clone();
                    event.getInventory().setItem(slot, InvseeCommand.createEquipmentPlaceholder(slot));
                    Map<Integer, ItemStack> leftover = staff.getInventory().addItem(removed);
                    for (ItemStack drop : leftover.values()) {
                        staff.getWorld().dropItemNaturally(staff.getLocation(), drop);
                    }
                    SchedulerUtils.runEntity(plugin, target, () -> {
                        switch (slot) {
                            case 45 -> target.getInventory().setHelmet(null);
                            case 46 -> target.getInventory().setChestplate(null);
                            case 47 -> target.getInventory().setLeggings(null);
                            case 48 -> target.getInventory().setBoots(null);
                            case 49 -> target.getInventory().setItemInOffHand(null);
                        }
                        target.updateInventory();
                    });
                } else if (cursor == null || cursor.getType().isAir()) {
                    ItemStack removed = clicked.clone();
                    event.getView().setCursor(removed);
                    event.getInventory().setItem(slot, InvseeCommand.createEquipmentPlaceholder(slot));
                    SchedulerUtils.runEntity(plugin, target, () -> {
                        switch (slot) {
                            case 45 -> target.getInventory().setHelmet(null);
                            case 46 -> target.getInventory().setChestplate(null);
                            case 47 -> target.getInventory().setLeggings(null);
                            case 48 -> target.getInventory().setBoots(null);
                            case 49 -> target.getInventory().setItemInOffHand(null);
                        }
                        target.updateInventory();
                    });
                } else {
                    ItemStack oldEquip = clicked.clone();
                    ItemStack toEquip = cursor.clone();
                    toEquip.setAmount(1);
                    ItemStack newCursor = cursor.clone();
                    newCursor.setAmount(newCursor.getAmount() - 1);
                    if (newCursor.getAmount() <= 0) {
                        newCursor = oldEquip;
                    } else {
                        Map<Integer, ItemStack> leftover = staff.getInventory().addItem(oldEquip);
                        for (ItemStack drop : leftover.values()) {
                            staff.getWorld().dropItemNaturally(staff.getLocation(), drop);
                        }
                    }
                    event.getView().setCursor(newCursor);
                    event.getInventory().setItem(slot, toEquip);
                    SchedulerUtils.runEntity(plugin, target, () -> {
                        switch (slot) {
                            case 45 -> target.getInventory().setHelmet(toEquip);
                            case 46 -> target.getInventory().setChestplate(toEquip);
                            case 47 -> target.getInventory().setLeggings(toEquip);
                            case 48 -> target.getInventory().setBoots(toEquip);
                            case 49 -> target.getInventory().setItemInOffHand(toEquip);
                        }
                        target.updateInventory();
                    });
                }
            }

            InvseeCommand.updateStatsOnly(event.getInventory(), target);
            return;
        }

        // 4. Utility shortcuts (slots 50-53)
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

        if (slot == 53) {
            event.setCancelled(true);
            SchedulerUtils.runEntity(plugin, staff, staff::closeInventory);
            return;
        }

        // 5. Prevent shift-clicking from bottom inventory into top inventory
        if (event.isShiftClick() && event.getClickedInventory() == event.getView().getBottomInventory()) {
            event.setCancelled(true);
            return;
        }

        if (slot >= 54) {
            return;
        }

        // 6. Main storage (slots 0-26 -> target 9-35) and hotbar (slots 27-35 -> target 0-8)
        Inventory customInv = event.getInventory();
        SchedulerUtils.runEntityLater(plugin, staff, () -> {
            ItemStack[] targetItems = new ItemStack[36];
            for (int i = 0; i <= 26; i++) {
                ItemStack it = customInv.getItem(i);
                targetItems[i + 9] = it != null ? it.clone() : null;
            }
            for (int i = 27; i <= 35; i++) {
                ItemStack it = customInv.getItem(i);
                targetItems[i - 27] = it != null ? it.clone() : null;
            }

            SchedulerUtils.runEntity(plugin, target, () -> {
                for (int i = 0; i < 36; i++) {
                    target.getInventory().setItem(i, targetItems[i]);
                }
                target.updateInventory();
            });

            InvseeCommand.updateStatsOnly(customInv, target);
        }, null, 1L);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder)) {
            return;
        }

        InvseeHolder holder = (InvseeHolder) event.getInventory().getHolder();
        Player target = holder.getTarget();
        Player staff = (Player) event.getWhoClicked();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 36) {
                event.setCancelled(true);
                return;
            }
        }

        StaffBukkitPlugin.InvseeSession dragSession = plugin.getInvseeSessions().get(staff.getUniqueId());
        if (dragSession != null) {
            dragSession.setLastEditMillis(System.currentTimeMillis());
        }

        Inventory customInv = event.getInventory();
        SchedulerUtils.runEntityLater(plugin, staff, () -> {
            ItemStack[] targetItems = new ItemStack[36];
            for (int i = 0; i <= 26; i++) {
                ItemStack it = customInv.getItem(i);
                targetItems[i + 9] = it != null ? it.clone() : null;
            }
            for (int i = 27; i <= 35; i++) {
                ItemStack it = customInv.getItem(i);
                targetItems[i - 27] = it != null ? it.clone() : null;
            }

            SchedulerUtils.runEntity(plugin, target, () -> {
                for (int i = 0; i < 36; i++) {
                    target.getInventory().setItem(i, targetItems[i]);
                }
                target.updateInventory();
            });

            InvseeCommand.updateStatsOnly(customInv, target);
        }, null, 1L);
    }

    private void refreshTargetViewers(Player target) {
        long now = System.currentTimeMillis();
        InvseeCommand.InvSnapshot snapshot = null;
        for (StaffBukkitPlugin.InvseeSession session : plugin.getInvseeSessions().values()) {
            if (session.getTargetUuid().equals(target.getUniqueId())) {
                if (now - session.getLastEditMillis() < 500) {
                    continue;
                }
                if (snapshot == null) {
                    snapshot = InvseeCommand.takeSnapshot(target);
                }
                final InvseeCommand.InvSnapshot finalSnapshot = snapshot;
                Player staff = Bukkit.getPlayer(session.getStaffUuid());
                if (staff != null && staff.isOnline()) {
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        InvseeCommand.applySnapshot(session.getInventory(), finalSnapshot);
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
