package me.ayosynk.staff.bukkit;

import me.ayosynk.staff.bukkit.commands.InvseeCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InvseeTest {

    @Test
    public void testGuiAndTargetSlotMapping() {
        // Main inventory mapping (GUI slots 0..26 <-> Target slots 9..35)
        for (int guiSlot = 0; guiSlot <= 26; guiSlot++) {
            int targetSlot = InvseeCommand.guiSlotToTargetSlot(guiSlot);
            assertEquals(guiSlot + 9, targetSlot, "GUI slot " + guiSlot + " should map to target slot " + (guiSlot + 9));
            assertEquals(guiSlot, InvseeCommand.targetSlotToGuiSlot(targetSlot), "Target slot " + targetSlot + " should map back to GUI slot " + guiSlot);
        }

        // Hotbar mapping (GUI slots 27..35 <-> Target slots 0..8)
        for (int guiSlot = 27; guiSlot <= 35; guiSlot++) {
            int targetSlot = InvseeCommand.guiSlotToTargetSlot(guiSlot);
            assertEquals(guiSlot - 27, targetSlot, "GUI slot " + guiSlot + " should map to target hotbar slot " + (guiSlot - 27));
            assertEquals(guiSlot, InvseeCommand.targetSlotToGuiSlot(targetSlot), "Target hotbar slot " + targetSlot + " should map back to GUI slot " + guiSlot);
        }

        // Out-of-bounds checks
        assertEquals(-1, InvseeCommand.guiSlotToTargetSlot(-1));
        assertEquals(-1, InvseeCommand.guiSlotToTargetSlot(36));
        assertEquals(-1, InvseeCommand.guiSlotToTargetSlot(45));
        assertEquals(-1, InvseeCommand.targetSlotToGuiSlot(-1));
        assertEquals(-1, InvseeCommand.targetSlotToGuiSlot(36));
    }

    @Test
    public void testInvSnapshotCapacity() {
        InvseeCommand.InvSnapshot snapshot = new InvseeCommand.InvSnapshot();
        assertNotNull(snapshot.items);
        assertEquals(36, snapshot.items.length);
        assertNull(snapshot.helmet);
        assertNull(snapshot.chestplate);
        assertNull(snapshot.leggings);
        assertNull(snapshot.boots);
        assertNull(snapshot.offhand);
    }

    @Test
    public void testResolveMaxHealthNullSafe() {
        // Null target should gracefully return default 20.0
        assertEquals(20.0, InvseeCommand.resolveMaxHealth(null));
    }
}
