package me.ayosynk.staff.bukkit.commands;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class StaffInfoHolder implements InventoryHolder {
    private final UUID targetUuid;
    private final String targetName;
    private final String targetIp;
    private final boolean online;
    private final java.util.Map<Integer, String> slotActions = new java.util.HashMap<>();
    private Inventory inventory;

    public StaffInfoHolder(UUID targetUuid, String targetName, String targetIp, boolean online) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetIp = targetIp;
        this.online = online;
    }

    public void registerAction(int slot, String actionKey) {
        slotActions.put(slot, actionKey);
    }

    public String getAction(int slot) {
        return slotActions.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public boolean isOnline() {
        return online;
    }
}
