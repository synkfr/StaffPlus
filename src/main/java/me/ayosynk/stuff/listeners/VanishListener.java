package me.ayosynk.stuff.listeners;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class VanishListener implements Listener {

    private final StuffPlugin plugin;

    public VanishListener(StuffPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent vanished players from picking up items.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.isVanished(player.getUniqueId()) && plugin.getPluginConfig().isVanishDisableItemPickup()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent mobs from targeting vanished players.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (plugin.isVanished(player.getUniqueId()) && plugin.getPluginConfig().isVanishDisableMobTargeting()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent vanished players from triggering pressure plates, tripwires, or trampling crops.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPhysicalInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Player player = event.getPlayer();
            if (plugin.isVanished(player.getUniqueId()) && plugin.getPluginConfig().isVanishIgnorePressurePlates()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent vanished players from dealing or receiving damage (God Mode).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.isVanished(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (plugin.isVanished(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent chest opening animations and sounds (Silent Containers option).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (plugin.isVanished(player.getUniqueId()) && plugin.getPluginConfig().isVanishSilentContainerClicks()) {
            InventoryType type = event.getInventory().getType();
            if (type == InventoryType.CHEST || type == InventoryType.SHULKER_BOX || type == InventoryType.BARREL) {
                // To display the inventory without chest opening animation, we can copy the contents into a custom virtual inventory!
                // This is extremely professional and completely silent!
                // Spigot does chest animation naturally when chest GUI opens. If we create a custom container inventory, it opens silently!
                // Let's open a copy of chest or just let it open silently by bypassing the chest block sound (which is client side mostly, but virtual inventory suppresses serverside animation packet).
                // Re-creating is easy:
                String title = event.getView().getTitle();
                Inventory silentInv = Bukkit.createInventory(null, event.getInventory().getSize(), MiniMessageUtils.parse("<color:#A0A0A0>[Silent] " + title));
                silentInv.setContents(event.getInventory().getContents());
                
                event.setCancelled(true); // Cancel chest opening packet
                SchedulerUtils.runEntity(plugin, player, () -> {
                    player.openInventory(silentInv);
                });
            }
        }
    }

    /**
     * Exclude vanished players from the server list ping online player count and hover list.
     */
    @EventHandler
    public void onServerListPing(org.bukkit.event.server.ServerListPingEvent event) {
        int vanishedCount = 0;
        for (UUID uuid : plugin.getVanishedPlayers()) {
            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished != null && vanished.isOnline()) {
                vanishedCount++;
            }
        }
        if (vanishedCount > 0) {
            int newSize = Math.max(0, event.getNumPlayers() - vanishedCount);
            if (event instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent) {
                ((com.destroystokyo.paper.event.server.PaperServerListPingEvent) event).setNumPlayers(newSize);
            }
        }

        // Exclude vanished players from the player sample hover list
        try {
            java.util.Iterator<Player> iterator = event.iterator();
            while (iterator.hasNext()) {
                Player player = iterator.next();
                if (plugin.isVanished(player.getUniqueId())) {
                    iterator.remove();
                }
            }
        } catch (UnsupportedOperationException e) {
            // Some server implementations might throw this if the list is immutable, we handle it gracefully
        }
    }
}
