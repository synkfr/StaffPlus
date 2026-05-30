package me.ayosynk.stuff.commands;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.utils.MiniMessageUtils;
import me.ayosynk.stuff.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MonitorCommand implements CommandExecutor, TabCompleter {

    private final StuffPlugin plugin;

    public MonitorCommand(StuffPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerOnly()));
            return true;
        }

        Player staff = (Player) sender;

        if (args.length < 1) {
            staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>Usage: /monitor <player> or /monitor leave"));
            return true;
        }

        String targetInput = args[0].toLowerCase();

        // Safe entity run on staff player regional thread (under Folia)
        SchedulerUtils.runEntity(plugin, staff, () -> {
            if (targetInput.equals("leave")) {
                if (!plugin.getMonitorStates().containsKey(staff.getUniqueId())) {
                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getMonitorNotActive()));
                    return;
                }

                plugin.restoreSpectatorState(staff, false);
                staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getMonitorLeft()));
            } else {
                Player target = Bukkit.getPlayer(args[0]);

                if (target == null || !target.isOnline() || !plugin.canSee(staff, target)) {
                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getPlayerNotFound().replace("{player}", args[0])));
                    return;
                }

                if (target.getUniqueId().equals(staff.getUniqueId())) {
                    staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + "<color:#E20000>You cannot monitor yourself."));
                    return;
                }

                // If already monitoring, let's restore first so we don't overwrite original state!
                if (plugin.getMonitorStates().containsKey(staff.getUniqueId())) {
                    plugin.restoreSpectatorState(staff, false);
                }

                // Store state
                ItemStack[] inv = staff.getInventory().getContents().clone();
                ItemStack[] armor = staff.getInventory().getArmorContents().clone();
                StuffPlugin.SpectatorState state = new StuffPlugin.SpectatorState(
                        staff.getLocation(),
                        staff.getGameMode(),
                        inv,
                        armor,
                        target.getUniqueId()
                );

                // Initialize relative follow offset (2 blocks behind target's looking direction, 1.5 blocks up)
                org.bukkit.Location targetLoc = target.getLocation();
                Vector direction = targetLoc.getDirection().normalize();
                Vector relativeOffset = direction.multiply(-2.0);
                relativeOffset.setY(1.5);

                org.bukkit.Location initialStaffLoc = targetLoc.clone().add(relativeOffset);
                initialStaffLoc.setYaw(targetLoc.getYaw());
                initialStaffLoc.setPitch(targetLoc.getPitch());

                state.setRelativeOffset(relativeOffset);
                state.setLastStaffLocation(initialStaffLoc);
                state.setLastTargetLocation(targetLoc.clone());

                plugin.getMonitorStates().put(staff.getUniqueId(), state);

                // Set spectator
                staff.setGameMode(GameMode.SPECTATOR);
                // Clear inventory to avoid any visual clutter
                staff.getInventory().clear();
                
                // Teleport to target player with dynamic offset and spectate them automatically
                staff.teleportAsync(initialStaffLoc).thenRun(() -> {
                    // Running on entity thread of staff to start the follow task
                    SchedulerUtils.runEntity(plugin, staff, () -> {
                        // Smooth auto-follow task. Runs every 4 ticks (200ms) instead of every tick
                        // to avoid flooding the client with position correction packets which cause rubber-banding.
                        // Uses distance thresholds and velocity prediction for smooth movement.
                        io.papermc.paper.threadedregions.scheduler.ScheduledTask followTask = staff.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
                            if (!staff.isOnline() || !target.isOnline() || !plugin.getMonitorStates().containsKey(staff.getUniqueId())) {
                                scheduledTask.cancel();
                                plugin.getMonitorTasks().remove(staff.getUniqueId());
                                if (staff.isOnline()) {
                                    plugin.restoreSpectatorState(staff, true);
                                }
                                return;
                            }

                            StuffPlugin.SpectatorState currentState = plugin.getMonitorStates().get(staff.getUniqueId());
                            if (currentState == null) {
                                scheduledTask.cancel();
                                plugin.getMonitorTasks().remove(staff.getUniqueId());
                                return;
                            }

                             // Decrement follow cooldown each cycle
                             currentState.tickFollowCooldown();

                             org.bukkit.Location currentTargetLoc = target.getLocation();
                             org.bukkit.Location currentStaffLoc = staff.getLocation();

                             // 1. Cross-world handling (always immediate)
                             if (!currentTargetLoc.getWorld().equals(currentStaffLoc.getWorld())) {
                                 if (currentState.isTeleporting()) return;

                                 Vector dir = currentTargetLoc.getDirection().normalize();
                                 Vector behind = dir.multiply(-2.0);
                                 behind.setY(1.5);

                                 org.bukkit.Location resetLoc = currentTargetLoc.clone().add(behind);
                                 resetLoc.setYaw(currentTargetLoc.getYaw());
                                 resetLoc.setPitch(currentTargetLoc.getPitch());

                                 currentState.setRelativeOffset(behind);
                                 currentState.setLastStaffLocation(resetLoc);
                                 currentState.setLastTargetLocation(currentTargetLoc.clone());

                                 currentState.setTeleporting(true);
                                 currentState.resetFollowCooldown();
                                 staff.teleportAsync(resetLoc).thenAccept(success -> {
                                     currentState.setTeleporting(false);
                                 });
                                 return;
                             }

                             // 2. Manual movement detection (uses larger threshold for 4-tick intervals)
                             org.bukkit.Location lastStaffLoc = currentState.getLastStaffLocation();
                             boolean staffMovingManually = false;
                             if (lastStaffLoc != null && lastStaffLoc.getWorld().equals(currentStaffLoc.getWorld())) {
                                 double staffDistSq = currentStaffLoc.distanceSquared(lastStaffLoc);
                                 // 0.04 = 0.2 blocks squared — filters spectator micro-drift at 4-tick resolution
                                 if (staffDistSq > 0.04) {
                                     staffMovingManually = true;
                                     Vector newOffset = currentStaffLoc.toVector().subtract(currentTargetLoc.toVector());
                                     currentState.setRelativeOffset(newOffset);
                                 }
                             }

                             // 3. Tether enforcement (Max 10 blocks — matches user spec)
                             double currentDist = currentStaffLoc.distance(currentTargetLoc);
                             if (currentDist > 10.0 && !currentState.isTeleporting()) {
                                 Vector tetherDir = currentStaffLoc.toVector().subtract(currentTargetLoc.toVector());
                                 if (tetherDir.lengthSquared() > 0) {
                                     tetherDir.normalize();
                                 } else {
                                     tetherDir = new Vector(0, 1.5, -2).normalize();
                                 }

                                 org.bukkit.Location newStaffLoc = currentTargetLoc.clone().add(tetherDir.multiply(7.0));
                                 newStaffLoc.setYaw(currentStaffLoc.getYaw());
                                 newStaffLoc.setPitch(currentStaffLoc.getPitch());

                                 currentState.setTeleporting(true);
                                 currentState.resetFollowCooldown();
                                 staff.teleportAsync(newStaffLoc).thenAccept(success -> {
                                     currentState.setTeleporting(false);
                                 });

                                 currentState.setLastStaffLocation(newStaffLoc);
                                 currentState.setLastTargetLocation(currentTargetLoc.clone());
                                 return;
                             }

                             // 4. Smooth auto-follow when staff is standing still
                             //    Only teleport when:
                             //    - Target moved > 1.5 blocks from last follow reference (avoids micro-jitter)
                             //    - Not already mid-teleport
                             //    - Follow cooldown has elapsed (min 2 cycles = 8 ticks = 400ms between follows)
                             if (!staffMovingManually) {
                                 org.bukkit.Location lastTargetLoc = currentState.getLastTargetLocation();
                                 double targetMoveDist = 0;
                                 boolean targetMoved = false;

                                 if (lastTargetLoc == null || !lastTargetLoc.getWorld().equals(currentTargetLoc.getWorld())) {
                                     targetMoved = true;
                                     targetMoveDist = 10.0; // Force follow on world change
                                 } else {
                                     targetMoveDist = lastTargetLoc.distance(currentTargetLoc);
                                     targetMoved = targetMoveDist > 1.5; // 1.5 block threshold
                                 }

                                 if (targetMoved && !currentState.isTeleporting() && currentState.isFollowReady()) {
                                     // Predict where target will be in ~200ms using current velocity
                                     // This eliminates the "chasing behind" stutter by teleporting slightly ahead
                                     Vector targetVel = target.getVelocity();
                                     // 4 ticks ahead prediction, clamped to avoid overshoot
                                     Vector prediction = targetVel.clone().multiply(4.0);
                                     if (prediction.lengthSquared() > 4.0) { // Max 2 blocks prediction
                                         prediction.normalize().multiply(2.0);
                                     }

                                     org.bukkit.Location predictedTargetLoc = currentTargetLoc.clone().add(prediction);
                                     org.bukkit.Location newStaffLoc = predictedTargetLoc.add(currentState.getRelativeOffset());
                                     newStaffLoc.setYaw(currentStaffLoc.getYaw());
                                     newStaffLoc.setPitch(currentStaffLoc.getPitch());

                                     currentState.setTeleporting(true);
                                     currentState.resetFollowCooldown();
                                     staff.teleportAsync(newStaffLoc).thenAccept(success -> {
                                         currentState.setTeleporting(false);
                                     });

                                     currentState.setLastStaffLocation(newStaffLoc);
                                     currentState.setLastTargetLocation(currentTargetLoc.clone());
                                 } else {
                                     if (!currentState.isTeleporting()) {
                                         currentState.setLastStaffLocation(currentStaffLoc);
                                     }
                                 }
                             } else {
                                 // Staff is moving manually — just track position
                                 if (!currentState.isTeleporting()) {
                                     currentState.setLastStaffLocation(currentStaffLoc);
                                     currentState.setLastTargetLocation(currentTargetLoc.clone());
                                 }
                             }
                        }, null, 1, 4);

                        plugin.getMonitorTasks().put(staff.getUniqueId(), followTask);
                        staff.sendMessage(MiniMessageUtils.parse(plugin.getMessageConfig().getPrefix() + plugin.getMessageConfig().getMonitorStarted().replace("{player}", target.getName())));
                    });
                });
            }
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("leave".startsWith(input)) {
                suggestions.add("leave");
            }
            for (Player p : plugin.getVisibleOnlinePlayers(sender)) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(p.getName());
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
