# Folia threading rules

Folia splits Minecraft worlds into independent regional tick loops. Code running on one regional thread cannot safely inspect or modify entities, blocks, or inventories located on another regional thread.

## Teleportation
Always use `Entity#teleportAsync(Location)` when moving players. Do not use synchronous `Entity#teleport(Location)`, which throws `UnsupportedOperationException` on Folia.

## Player visibility packets
Execute `player.hidePlayer(plugin, target)` and `player.showPlayer(plugin, target)` on the recipient player's entity thread using `SchedulerUtils.runEntity(plugin, player, task)`.

## Spectator camera follow tasks
1. Run follow schedulers on interval ticks (at least 4 ticks, or 200 ms) instead of every tick. Teleporting every tick floods position packets and causes rubber-banding.
2. Only issue teleports when the target moves 1.5 blocks or more from the last position.
3. Use a cooldown between teleports to allow async completions to finish.

## World and inventory reads
Do not call `player.getOpenInventory().getTopInventory().getHolder()` on external players inside arbitrary event handlers. This triggers cross-thread block reads.
When reading player state to update a viewer's custom inventory:
1. Snapshot item stacks and stats on the target player's entity thread.
2. Apply the snapshot to the viewer's custom inventory on the viewer's entity thread.
