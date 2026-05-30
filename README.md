# Stuff+ — Advanced Modern Moderation & Utility Plugin

Stuff+ is an enterprise-grade, high-performance moderation and utility plugin built for modern Minecraft networks. It is designed from the ground up to support Folia's multi-threaded region architecture as well as standard Paper / Spigot (1.20 - 1.21+) servers.

By utilizing regional entity schedulers, thread-safe packet delivery pipelines, and robust data isolation, Stuff+ delivers advanced administrative utilities without ever risking main-thread locks or cross-thread race conditions.

---

## Key Features

### 1. Native Folia & Region-Threading Compatibility
* **Asynchronous Lifecycles**: All commands, database transactions, and packet actions run on Folia-compliant regional or asynchronous schedulers.
* **Folia Teleportation Safety**: Standard Bukkit teleports are replaced with safe, non-blocking `teleportAsync` futures, completely preventing thread crashes.
* **Isolated Packet Loops**: Visually toggling features (like Vanish hide/show packets) are scheduled on each recipient's specific regional thread, guaranteeing thread-safety.

### 2. Ultimate Vanish System (/vanish)
* **Total Invisibility**: Completely removes the player from the online count, player ping lists, tab completions, and visible lists.
* **Physical Interaction Bypass**: Vanished staff bypass physical pressure plates and tripwire triggers entirely.
* **Silent Containers**: Allows opening chest, furnace, and barrel interfaces silently without triggering container animations or audio.
* **Stealth Suppression**: Prevents mob targeting, item pickups, and suppresses join/quit messages entirely while active.
* **Visual Action Bar Indicator**: Real-time HUD showing vanished status via safe asynchronous repeating tasks.

### 3. Robust Moderation Suite
* **Comprehensive Actions**: Integrated `/ban`, `/tempban`, `/ip-ban`, `/tempip-ban`, `/mute`, `/tempmute`, and `/warn`.
* **Warning Profiles**: Track individual warning logs via `/warns <player> [list/clear]`.
* **Dynamic Time Parsing**: Sophisticated time parser supporting complex units (e.g. `1d`, `12h`, `45m`) and infinite duration mappings (`perm`).
* **Relational Storage**: Backed by a high-performance database manager supporting both local SQLite databases and remote MySQL servers, managed via a shaded, thread-safe HikariCP connection pool.

### 4. Smooth Spectator Auto-Follow (/monitor)
* **Cinematic Camera Tracking**: Teleports staff in spectator mode to follow the target seamlessly.
* **Velocity-Predicted Follow**: Computes target movements and velocity vectors (`target.getVelocity()`) to position the camera slightly ahead of the target, eliminating stutter and rubber-banding.
* **Throttled Teleports**: Auto-updates run at a throttled 4-tick (200ms) interval with a 400ms cooldown, giving client-side predicted movements and Paper's async teleportation futures time to fully resolve.
* **Configurable Boundary Tether**: Allows staff to orbit or move freely within a 10-block radius. Going beyond the boundary gently tethers the camera back into offset.

### 5. Real-Time Inventory Inspector (/invsee)
* **54-Slot Live GUI**: View and edit target players' main inventories (0-35), armor slots (helmet, chestplate, leggings, boots), off-hand items, and stats.
* **Live Stats & Shortcuts**:
  * **Golden Apple**: Displays health, food levels, and experience dynamically.
  * **Potion Bottle**: Shows a live list of active potion effects and durations.
  * **Ender Chest**: Quick-access shortcut item to inspect their Ender Chest directly.
* **Unparalleled Folia Safety**: Completely avoids asynchronous block-state reads (which crash under Folia when querying `.getOpenInventory().getTopInventory().getHolder()` on block-based inventories in other regions). Instead, it implements a thread-safe custom session registry that cleanly tracks active inspect views and propagates updates regionally.

### 6. Admin Shortcuts
* **Instant Gamemode Shifts**: Rapid commands including creative (`/gmc`), survival (`/gms`), spectator (`/gmsp`), and adventure (`/gma`).
* **Flight Mode**: Seamless command flight toggles via `/fly`.

---

## Command & Permission Reference

| Command | Description | Usage | Permission Node |
| :--- | :--- | :--- | :--- |
| `/ban` | Bans a player from the server. | `/ban <player> [reason]` | `stuff.ban` |
| `/tempban` | Temporarily bans a player. | `/tempban <player> <duration> [reason]` | `stuff.tempban` |
| `/unban` | Unbans a player. | `/unban <player>` | `stuff.unban` |
| `/ip-ban` | IP-bans a player. | `/ip-ban <player> [reason]` | `stuff.ipban` |
| `/tempip-ban` | Temporarily IP-bans a player. | `/tempip-ban <player> <duration> [reason]` | `stuff.tempipban` |
| `/unip-ban` | Unbans an IP address or player. | `/unip-ban <player/IP>` | `stuff.unipban` |
| `/mute` | Mutes a player in chat. | `/mute <player> [reason]` | `stuff.mute` |
| `/tempmute` | Temporarily mutes a player. | `/tempmute <player> <duration> [reason]` | `stuff.tempmute` |
| `/unmute` | Unmutes a player. | `/unmute <player>` | `stuff.unmute` |
| `/warn` | Warns a player. | `/warn <player> [reason]` | `stuff.warn` |
| `/warns` | View or clear warning history. | `/warns <player> [list/clear]` | `stuff.warns` |
| `/vanish` | Toggle complete invisibility. | `/vanish` | `stuff.vanish` |
| `/invsee` | Inspect a player's inventory live. | `/invsee <player>` | `stuff.invsee` |
| `/monitor` | Spectate and smoothly follow a player. | `/monitor <player/leave>` | `stuff.monitor` |
| `/fly` | Toggle player flight. | `/fly [player]` | `stuff.fly` |
| `/gmc` | Switch to creative mode. | `/gmc [player]` | `stuff.gmc` |
| `/gms` | Switch to survival mode. | `/gms [player]` | `stuff.gms` |
| `/gmsp` | Switch to spectator mode. | `/gmsp [player]` | `stuff.gmsp` |
| `/gma` | Switch to adventure mode. | `/gma [player]` | `stuff.gma` |

> **Admin Wildcard**: The permission node `stuff.admin` grants access to all capabilities of the plugin by default.
> **Bypass Node**: Players with the permission `stuff.vanish.see` can see vanished players in-game, in tab lists, and in commands.

---

## Configuration Files

The plugin uses Okaeri Config to generate, validate, and auto-update clean YAML configuration structures.

### `config.yml`
```yaml
# Storage type: SQLITE or MYSQL
storage-type: "sqlite"

# MySQL Connection Details (only used if storage-type is MYSQL)
mysql-host: "localhost"
mysql-port: 3306
mysql-database: "minecraft"
mysql-username: "root"
mysql-password: "password"
mysql-pool-size: 10
mysql-use-ssl: false

# Vanish settings
vanish-silent-container-clicks: true
vanish-ignore-pressure-plates: true
vanish-disable-mob-targeting: true
vanish-disable-item-pickup: true
```

### `messages.yml`
Features full Adventure MiniMessage compatibility, enabling gorgeous native text styling, hex colors, and gradients (e.g. `<gradient:#FF5F6D:#FFC371>Text</gradient>`).
```yaml
# Prefix for all plugin messages
prefix: "<color:#A0A0A0>[<color:#00E262>Stuff+<color:#A0A0A0>] "

no-permission: "<color:#E20000>You do not have permission to execute this command."
player-only: "<color:#E20000>This command can only be executed by a player."
player-not-found: "<color:#E20000>Player '{player}' has not been found or registered."
invalid-duration: "<color:#E20000>Invalid duration format! Use e.g. 1d, 12h, 30m or perm."

# Mutes
player-muted: "<color:#00E262>You have muted {player} for {time}. Reason: {reason}"
player-muted-broadcast: "<color:#E2B700>{player} has been muted by {sender} for {time}. Reason: {reason}"
you-are-muted: "<color:#E20000>You are muted! Remaining time: {time}. Reason: {reason}"

# Bans
player-banned: "<color:#00E262>You have banned {player} for {time}. Reason: {reason}"
player-banned-broadcast: "<color:#E20000>{player} has been banned by {sender} for {time}. Reason: {reason}"
ban-kick-message: "<color:#E20000>You have been banned from the server!\n\nReason: {reason}\nExpiry: {time}"

# Vanish
vanish-enabled: "<color:#00E262>You are now vanished."
vanish-disabled: "<color:#00E262>You are no longer vanished."
vanish-action-bar: "<color:#00E262>★ YOU ARE VANISHED ★"
```

---

## Build & Compilation

To build the plugin and create a fully shaded/relocated JAR ready for production:

### Requirements
* **Java 21** or higher
* **Gradle** (installed or via wrapper)

### Build Command
Compile and build the shadow JAR:
```bash
gradle shadowJar
```

The compiled plugin will be located at:
```
build/libs/Stuff-1.0.0.jar
```

*Note: The Gradle shadow configuration automatically relocates `eu.okaeri` and `com.zaxxer.hikari` libraries to private namespaces (`me.ayosynk.stuff.libs.*`) to prevent conflicts with any other plugins on the classpath.*

---

## Folia Best Practices & Technical Notes

For developers looking at the source code of Stuff+, here is a summary of region-threading safety rules integrated into this repository:

1. **Async Teleportation**: Never call `Player#teleport()` on Folia. Always use `Player#teleportAsync()` and queue dependent actions using `.thenRun()` or `.thenAccept()`.
2. **Recipient Thread Safety**: Action updates (like `recipient.hidePlayer()` or `recipient.showPlayer()`) must execute on the **recipient's** regional thread context. Wrap these calls in `SchedulerUtils.runEntity(plugin, recipient, ...)` to guarantee packet delivery.
3. **Avoid cross-region block state reads**: Calling `open.getHolder()` on block-based inventories (e.g., Chests, Furnaces) open in other regions throws threading exceptions. Instead, maintain an in-memory, thread-safe registry of `InvseeSession` objects to update live inventories without world queries.
4. **Cinematic Follow Damping**: Issuing `teleportAsync` to follow players every single tick saturates client-side movement buffers. Use interval checking (≥4 ticks), velocity extrapolation, and a task cooldown to maintain smooth follower views.
