package me.ayosynk.staff.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE)
public class MessageConfig extends OkaeriConfig {

    @Comment("Prefix for all plugin messages")
    private String prefix = "<color:#A0A0A0>[<color:#00E262>Staff+<color:#A0A0A0>] ";

    // General messages
    private String noPermission = "<color:#E20000>You do not have permission to execute this command.";
    private String playerOnly = "<color:#E20000>This command can only be executed by a player.";
    private String cannotOverwritePunishment = "<color:#E20000>You cannot override a punishment placed by a higher-ranking staff member ({staff}).";
    private String playerAllowed = "<color:#00E262>You have allowed {player} to bypass IP bans.";
    private String playerAllowedBroadcast = "<color:#00E262>{player} has been exempted from IP bans by {sender}.";
    private String playerUnallowed = "<color:#00E262>You have removed IP ban exemption for {player}.";
    private String playerUnallowedBroadcast = "<color:#00E262>{player} is no longer exempted from IP bans.";
    private String playerNotFound = "<color:#E20000>Player '{player}' has not been found or registered.";
    private String invalidDuration = "<color:#E20000>Invalid duration format! Use e.g. 1d, 12h, 30m or perm.";

    // Mute messages
    private String playerMuted = "<color:#00E262>You have muted {player} for {time}. Reason: {reason}";
    private String playerMutedBroadcast = "<color:#E2B700>{player} has been muted by {sender} for {time}. Reason: {reason}";
    private String playerUnmuted = "<color:#00E262>You have unmuted {player}.";
    private String playerUnmutedBroadcast = "<color:#00E262>{player} has been unmuted by {sender}.";
    private String youAreMuted = "<color:#E20000>You are muted! Remaining time: {time}. Reason: {reason}";

    // Ban messages
    private String playerBanned = "<color:#00E262>You have banned {player} for {time}. Reason: {reason}";
    private String playerBannedBroadcast = "<color:#E20000>{player} has been banned by {sender} for {time}. Reason: {reason}";
    private String playerUnbanned = "<color:#00E262>You have unbanned {player}.";
    private String playerUnbannedBroadcast = "<color:#00E262>{player} has been unbanned by {sender}.";
    private String banKickMessage = "<color:#E20000>You have been banned from the server!\n\nReason: {reason}\nExpiry: {time}";

    // IP Ban messages
    private String ipBanned = "<color:#00E262>You have IP-banned {player} ({ip}) for {time}. Reason: {reason}";
    private String ipBannedBroadcast = "<color:#E20000>{player} ({ip}) has been IP-banned by {sender} for {time}. Reason: {reason}";
    private String ipUnbanned = "<color:#00E262>You have unbanned IP/Player '{ip}'.";
    private String ipUnbannedBroadcast = "<color:#00E262>IP/Player '{ip}' has been IP-unbanned by {sender}.";

    // Warn messages
    private String playerWarned = "<color:#00E262>You have warned {player} for: {reason}";
    private String playerWarnedBroadcast = "<color:#E2B700>{player} has been warned by {sender} for: {reason}";
    private String youAreWarned = "<color:#E2B700>You have been warned! Reason: {reason}";
    private String warnCleared = "<color:#00E262>Cleared warnings for {player}.";
    private String warnListHeader = "<color:#E2B700>Warnings for {player}:";
    private String warnListItem = "<color:#A0A0A0>- {reason} by {sender} ({date})";
    private String noWarns = "<color:#A0A0A0>No warnings found for {player}.";

    // Vanish messages (Bukkit only)
    private String vanishEnabled = "<color:#00E262>You are now vanished.";
    private String vanishDisabled = "<color:#00E262>You are no longer vanished.";
    private String vanishActionBar = "<color:#00E262>★ YOU ARE VANISHED ★";

    // Monitor messages (Bukkit only)
    private String monitorStarted = "<color:#00E262>Now monitoring {player} in spectator mode.";
    private String monitorLeft = "<color:#00E262>You left the monitor state and returned to your original location.";
    private String monitorNotActive = "<color:#E20000>You are not currently monitoring anyone.";
    private String monitorTargetOffline = "<color:#E20000>The monitored player has gone offline. Returning to original location.";

    // Invsee messages (Bukkit only)
    private String invseeOpened = "<color:#00E262>Opening live inventory of {player}...";

    private String updateAvailable = "<color:#E2B700>A new version of Staff+ is available: <color:#00E262>v{latest}</color> (Current: <color:#A0A0A0>v{current}</color>)\n<color:#A0A0A0>Download: <click:open_url:'{url}'><color:#00E262><underlined>{url}</underlined></color></click>";

    private String bansHeader = "<color:#E2B700>------------- [ Ban History (Page {page}/{pages}) ] -------------";
    private String bansItemActive = "<color:#E20000>[ACTIVE]</color> <color:#00E262>{player}</color> banned by <color:#00E262>{staff}</color>: {reason} <color:#A0A0A0>({date})</color>";
    private String bansItemExpired = "<color:#A0A0A0>[EXPIRED] <color:#00E262>{player}</color> banned by <color:#00E262>{staff}</color>: {reason} ({date})</color>";
    private String bansFooter = "<click:run_command:'/bans {prev}'><color:#00E262>[« Previous]</color></click> <color:#A0A0A0>| <click:run_command:'/bans {next}'><color:#00E262>[Next »]</color></click>";
    private String bansEmpty = "<color:#A0A0A0>No bans found in database.";

    private String bansLeaderboardDisabled = "<color:#E20000>Bans leaderboard is currently disabled.";
    private String bansLeaderboardHeader = "<color:#E2B700>------------- [ Staff Ban Leaderboard ] -------------";
    private String bansLeaderboardItem = "<color:#E2B700>#{rank}</color> <color:#00E262>{staff}</color> <color:#A0A0A0>- <color:#00E262>{count}</color> bans</color>";
    private String bansLeaderboardEmpty = "<color:#A0A0A0>No staff punishments recorded on this server.";

    public String getPrefix() { return prefix; }
    public String getNoPermission() { return noPermission; }
    public String getCannotOverwritePunishment() { return cannotOverwritePunishment; }
    public String getPlayerAllowed() { return playerAllowed; }
    public String getPlayerAllowedBroadcast() { return playerAllowedBroadcast; }
    public String getPlayerUnallowed() { return playerUnallowed; }
    public String getPlayerUnallowedBroadcast() { return playerUnallowedBroadcast; }
    public String getPlayerOnly() { return playerOnly; }
    public String getPlayerNotFound() { return playerNotFound; }
    public String getInvalidDuration() { return invalidDuration; }
    public String getPlayerMuted() { return playerMuted; }
    public String getPlayerMutedBroadcast() { return playerMutedBroadcast; }
    public String getPlayerUnmuted() { return playerUnmuted; }
    public String getPlayerUnmutedBroadcast() { return playerUnmutedBroadcast; }
    public String getYouAreMuted() { return youAreMuted; }
    public String getPlayerBanned() { return playerBanned; }
    public String getPlayerBannedBroadcast() { return playerBannedBroadcast; }
    public String getPlayerUnbanned() { return playerUnbanned; }
    public String getPlayerUnbannedBroadcast() { return playerUnbannedBroadcast; }
    public String getBanKickMessage() { return banKickMessage; }
    public String getIpBanned() { return ipBanned; }
    public String getIpBannedBroadcast() { return ipBannedBroadcast; }
    public String getIpUnbanned() { return ipUnbanned; }
    public String getIpUnbannedBroadcast() { return ipUnbannedBroadcast; }
    public String getPlayerWarned() { return playerWarned; }
    public String getPlayerWarnedBroadcast() { return playerWarnedBroadcast; }
    public String getYouAreWarned() { return youAreWarned; }
    public String getWarnCleared() { return warnCleared; }
    public String getWarnListHeader() { return warnListHeader; }
    public String getWarnListItem() { return warnListItem; }
    public String getNoWarns() { return noWarns; }
    public String getVanishEnabled() { return vanishEnabled; }
    public String getVanishDisabled() { return vanishDisabled; }
    public String getVanishActionBar() { return vanishActionBar; }
    public String getMonitorStarted() { return monitorStarted; }
    public String getMonitorLeft() { return monitorLeft; }
    public String getMonitorNotActive() { return monitorNotActive; }
    public String getMonitorTargetOffline() { return monitorTargetOffline; }
    public String getInvseeOpened() { return invseeOpened; }
    public String getUpdateAvailable() { return updateAvailable; }
    public String getBansHeader() { return bansHeader; }
    public String getBansItemActive() { return bansItemActive; }
    public String getBansItemExpired() { return bansItemExpired; }
    public String getBansFooter() { return bansFooter; }
    public String getBansEmpty() { return bansEmpty; }
    public String getBansLeaderboardDisabled() { return bansLeaderboardDisabled; }
    public String getBansLeaderboardHeader() { return bansLeaderboardHeader; }
    public String getBansLeaderboardItem() { return bansLeaderboardItem; }
    public String getBansLeaderboardEmpty() { return bansLeaderboardEmpty; }
}
