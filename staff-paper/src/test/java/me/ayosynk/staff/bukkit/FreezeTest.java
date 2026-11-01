package me.ayosynk.staff.bukkit;

import me.ayosynk.staff.bukkit.freeze.FreezeSession;
import me.ayosynk.staff.config.MessageConfig;
import me.ayosynk.staff.config.PluginConfig;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class FreezeTest {

    @Test
    public void testFreezeSessionProperties() {
        UUID targetUuid = UUID.randomUUID();
        UUID staffUuid = UUID.randomUUID();
        String staffName = "Admin";
        String reason = "Suspected Killaura";
        Location loc = new Location(null, 100.5, 64.0, -200.5, 90.0f, 0.0f);

        FreezeSession session = new FreezeSession(targetUuid, staffUuid, staffName, reason, loc);

        assertEquals(targetUuid, session.getTargetUuid());
        assertEquals(staffUuid, session.getStaffUuid());
        assertEquals("Admin", session.getStaffName());
        assertEquals("Suspected Killaura", session.getReason());
        assertEquals(loc, session.getFreezeLocation());
        assertTrue(session.getFrozenAt() <= System.currentTimeMillis());
    }

    @Test
    public void testFreezeConfigDefaults() {
        PluginConfig config = new PluginConfig();

        assertTrue(config.isFreezeEnabled());
        assertEquals("ts.example.com", config.getTeamspeakServer());
        assertEquals("discord.gg/example", config.getDiscordInvite());
        assertEquals(5, config.getFreezeReminderIntervalSeconds());
        assertTrue(config.isFreezeLogoutActionEnabled());
        assertTrue(config.getFreezeLogoutAction().contains("ban {player}"));
        assertNotNull(config.getFreezeAllowedCommands());
        assertTrue(config.getFreezeAllowedCommands().contains("msg"));
    }

    @Test
    public void testFreezeMessagesConfigDefaults() {
        MessageConfig msg = new MessageConfig();

        assertNotNull(msg.getPlayerFrozen());
        assertNotNull(msg.getPlayerUnfrozen());
        assertNotNull(msg.getPlayerFrozenBroadcast());
        assertNotNull(msg.getPlayerUnfrozenBroadcast());
        assertNotNull(msg.getFrozenChatReminder());
        assertTrue(msg.getFrozenChatReminder().contains("{teamspeak}"));
        assertTrue(msg.getFrozenChatReminder().contains("{discord}"));
        assertTrue(msg.getFrozenTitle().contains("FROZEN"));
        assertTrue(msg.getFrozenSubtitle().contains("{teamspeak}"));
        assertTrue(msg.getFrozenActionBar().contains("{teamspeak}"));
        assertNotNull(msg.getFrozenLogoutBroadcast());
        assertNotNull(msg.getFrozenCannotExecuteCommand());
        assertNotNull(msg.getFreezeAlreadyFrozen());
        assertNotNull(msg.getFreezeNotFrozen());
        assertNotNull(msg.getCannotFreezeStaff());
    }

    @Test
    public void testInvseeSessionDebounceTimestamp() {
        UUID staff = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        StaffBukkitPlugin.InvseeSession session = new StaffBukkitPlugin.InvseeSession(staff, target, null);

        assertEquals(0L, session.getLastEditMillis());
        long now = System.currentTimeMillis();
        session.setLastEditMillis(now);
        assertEquals(now, session.getLastEditMillis());
    }
}
