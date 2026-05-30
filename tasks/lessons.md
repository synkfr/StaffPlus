# Lessons Learned & Technical Guidelines

## 1. Paper/Folia Command Registration Patterns
*   **The Issue**: When building Paper/Folia plugins, declaring a `paper-plugin.yml` file blocks the traditional Bukkit `JavaPlugin#getCommand` API. Attempting to use it throws an `UnsupportedOperationException` because modern Paper plugins require Brigadier command structures or dynamic lifecycles.
*   **The Resolution**: We have two excellent pathways:
    1.  **Traditional Way**: Use Spigot's legacy `plugin.yml` with `folia-supported: true`. This allows traditional `JavaPlugin#getCommand` to operate normally.
    2.  **Modern Programmatic Way (Preferred for paper-plugin.yml)**: Omit commands block from `paper-plugin.yml` entirely, and register traditional command executors dynamically into the server's public `CommandMap` using `Bukkit.getCommandMap().register("namespace", commandInstance)` in `onEnable()`. To do this seamlessly without changing existing `CommandExecutor`/`TabCompleter` implementations, wrap them in a simple custom `org.bukkit.command.Command` subclass.
*   **Self-Corrective Action Rule**:
    *   *Rule*: When using `paper-plugin.yml`, never declare commands inside the YAML file. Register them programmatically via `Bukkit.getCommandMap()` using a custom `Command` class wrapper in `onEnable()`. This preserves clean separation and compatibility while retaining pure modern Paper bootstrap features.

## 2. Minecraft 1.21+ breaking change: InventoryView Interface
*   **The Issue**: Starting in Minecraft 1.21, the Bukkit API transitioned `InventoryView` from an abstract class to an interface. If a plugin compiles against an older API (e.g. 1.20.4) where `InventoryView` is a class, but runs on a newer server (e.g. 1.21.11), the JVM throws an `IncompatibleClassChangeError` because the compiled bytecode expects a class reference (INVOKEVIRTUAL) but encounters an interface.
*   **The Resolution**: Always match the `compileOnly` target API in `build.gradle` to the target server's runtime major version. Compiling against `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT` generates correct interface-invocation bytecode (INVOKEINTERFACE), preventing any runtime ClassChange exceptions.
*   **Self-Corrective Action Rule**:
    *   *Rule*: When a project targets modern 1.21+ Paper/Folia servers, ensure `build.gradle` compiles against a 1.21+ API version. This is critical when interacting with modern UI/Inventory methods.

## 3. Folia Threading Restrictions: Asynchronous Teleportation
*   **The Issue**: Under Folia's multi-threaded region architecture, attempting to use the standard Bukkit synchronous `Entity#teleport(Location)` method from a regional or entity task generates an `UnsupportedOperationException: Must use teleportAsync while in region threading` exception.
*   **The Resolution**: Always use the modern Paper asynchronous teleportation API `Entity#teleportAsync(Location)` when running under Folia. This ensures that the entity transitions safely and cleanly between regional threads without blocking the current ticks or throwing threading exceptions.
*   **Self-Corrective Action Rule**:
    *   *Rule*: Never use synchronous `Entity#teleport(Location)` in plugins targeting Folia. Always use `Entity#teleportAsync(Location)` to ensure compatibility with region threading.

## 4. Folia Threading Restrictions: hidePlayer and showPlayer recipient thread safety
*   **The Issue**: Under Folia's multi-threaded region architecture, player entity state adjustments (like calling `recipient.hidePlayer(plugin, vanished)` or `recipient.showPlayer(plugin, vanished)`) are strictly thread-confined to the **recipient's** regional thread. Running this logic synchronously from the vanished player's thread or join thread will either fail silently or throw cross-region threading exceptions.
*   **The Resolution**: Always execute `hidePlayer` and `showPlayer` inside the recipient player's entity thread using `SchedulerUtils.runEntity(plugin, recipient, () -> recipient.hidePlayer(plugin, vanished))`.
*   **Self-Corrective Action Rule**:
    *   *Rule*: In Folia-compatible plugins, never call `recipient.hidePlayer` or `recipient.showPlayer` synchronously across regional threads. Always wrap it inside a regional task on the recipient player's scheduler to ensure perfect packet and state delivery.

## 5. Folia Smooth Follow: Avoid Per-Tick teleportAsync
*   **The Issue**: Using `Entity#teleportAsync()` every single tick (50ms) for a "smooth follow" spectator camera creates severe visual stutter/rubber-banding. Each async teleport sends a position correction packet to the client, and at 20 packets/sec the client can never reconcile its position smoothly — it perpetually rubber-bands between predicted and corrected positions.
*   **The Resolution**: Reduce teleport frequency drastically:
    1.  Run the follow task every **4 ticks** (200ms) instead of every tick.
    2.  Only issue follow teleports when the target moves **> 1.5 blocks** from the last reference position.
    3.  Use **velocity prediction** (`target.getVelocity()`) to teleport slightly *ahead* of the target, eliminating the "chasing behind" effect.
    4.  Enforce a **cooldown** (min 2 cycles = 400ms) between consecutive follow teleports to ensure `teleportAsync` fully resolves.
*   **Self-Corrective Action Rule**:
    *   *Rule*: Never issue `teleportAsync` on every server tick for smooth camera following. Use interval-based tasks (≥4 ticks), distance thresholds (≥1.5 blocks), and velocity prediction instead. The fewer teleports, the smoother the client-side experience.

## 6. Folia Threading Restrictions: Unsafe Block/World Reads (e.g., InventoryHolder/BlockState access)
*   **The Issue**: Under Folia's multi-threaded region architecture, attempting to query another player's open inventory (`player.getOpenInventory().getTopInventory()`) or calling `.getHolder()` on it from an arbitrary regional thread/event listener can trigger a synchronous read of block or entity states (e.g., if the open inventory is a Furnace block entity located in another region). Since this block exists in a different regional thread, this triggers a `java.lang.IllegalStateException: Thread failed main thread check: Cannot read world asynchronously` exception.
*   **The Resolution**: Avoid calling `.getOpenInventory()`, `.getTopInventory()`, or `.getHolder()` on other players from arbitrary threads. Instead, maintain a thread-safe registry (`ConcurrentHashMap`) mapping active custom inventory sessions (e.g., `InvseeSession` mapping staff UUID to the target UUID and custom Inventory instance). Clean these sessions up on `InventoryCloseEvent` and `PlayerQuitEvent`. This allows event handlers to update custom GUIs thread-safely by looking up registered sessions and dispatching updates to the target player's region thread.
*   **Self-Corrective Action Rule**:
    *   *Rule*: Never call `.getHolder()` on arbitrary inventories or query `.getOpenInventory()` on other players synchronously from event listeners under Folia. Use a thread-safe registry of custom inventory sessions to isolate and safely query open GUIs.
