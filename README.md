![Stuff](https://cdn.modrinth.com/data/cached_images/3d527e1c3549243fb7545c033e4c0106849f1849.png)


# Stuff+

Stuff+ is an enterprise-grade, high-performance moderation and utility plugin built for modern Minecraft networks. It is designed from the ground up to support Folia's multi-threaded region architecture as well as standard Paper / Spigot (1.20 - 1.21+) servers.

By utilizing regional entity schedulers, thread-safe packet delivery pipelines, and robust data isolation, Stuff+ delivers advanced administrative utilities without ever risking main-thread locks or cross-thread race conditions.

---

## Features

### Folia Safety
* **Asynchronous Lifecycles**: All commands, database transactions, and packet actions run on Folia-compliant regional or asynchronous schedulers.
* **Folia Teleportation Safety**: Standard Bukkit teleports are replaced with safe, non-blocking `teleportAsync` futures, completely preventing thread crashes.
* **Isolated Packet Loops**: Visually toggling features (like Vanish hide/show packets) are scheduled on each recipient's specific regional thread, guaranteeing thread-safety.

### Vanish
* **Total Invisibility**: Completely removes the player from the online count, player ping lists, tab completions, and visible lists.
* **Physical Interaction Bypass**: Vanished staff bypass physical pressure plates and tripwire triggers entirely.
* **Silent Containers**: Allows opening chest, furnace, and barrel interfaces silently without triggering container animations or audio.
* **Stealth Suppression**: Prevents mob targeting, item pickups, and suppresses join/quit messages entirely while active.
* **Visual Action Bar Indicator**: Real-time HUD showing vanished status via safe asynchronous repeating tasks.

### Moderation
* **Comprehensive Actions**: Integrated `/ban`, `/tempban`, `/ip-ban`, `/tempip-ban`, `/mute`, `/tempmute`, and `/warn`.
* **Warning Profiles**: Track individual warning logs via `/warns <player> [list/clear]`.
* **Dynamic Time Parsing**: Sophisticated time parser supporting complex units (e.g. `1d`, `12h`, `45m`) and infinite duration mappings (`perm`).
* **Relational Storage**: Backed by a high-performance database manager supporting both local SQLite databases and remote MySQL servers, managed via a shaded, thread-safe HikariCP connection pool.
* **Linked Account Alt-Detection**: On player login, automatically scans, logs, and broadcasts alternate accounts sharing the IP address, color-coding active (green) and banned (red) profiles.
* **Dynamic Command & History Parity**: Multi-threaded `/history <player>`, `/staffhistory <staff>`, and `/staffrollback <staff>` commands supported by dynamic database-backed name tab completions.
* **Warning Escalation Ladder**: Scale punishments dynamically (e.g. tempmutes scaling to permanent bans) based on active warning levels. Command dispatches execute safely on Folia's global scheduler.
* **Non-Blocking Discord Webhook Logs**: Real-time embeds logging all mute, ban, and warning dispatches using modern non-blocking Java 21 `HttpClient` interfaces with zero impact on region ticks.
* **Administrative Overwrite Hierarchy Protection**: Custom staff weight ranks (`stuff.hierarchy.weight.<number>`) blocking junior staff from overriding, deactivating, or removing punishments placed by senior administrators or the Console (`Integer.MAX_VALUE`).
* **IP-Ban Exemption Bypass**: Whitelist specific accounts (`/stuffallow`) to connect and bypass active IP-bans without lifting the IP ban itself for other accounts sharing the IP.

### Punishment Importer & Migration
* **7 Supported Platforms**: Seamlessly import mutes, bans, warnings, and IP bans from:
  * **Vanilla Minecraft**: Direct imports from `banned-players.json` and `banned-ips.json` in the server root.
  * **Essentials / EssentialsX**: Automated scans of mutes and bans in user profiles under `plugins/Essentials/userdata/*.yml`.
  * **LiteBans**: Migrates bans, mutes, and warnings from SQL/SQLite connection structures.
  * **AdvancedBan**: Integrates and normalizes legacy SQL `Punishments` tables.
  * **MaxBans**: Dynamic name-to-UUID resolving and migration of bans, IP bans, mutes, and warnings.
  * **BanManager**: Integrates historical player bans, mutes, and warnings.
  * **BungeeAdminTools**: Imports entries from database tables `bat_ban` and `bat_mute`.
* **Dynamic Auto-Scanning**: Scans your server's active files on command, extracts SQLite paths or remote SQL database credentials, and allows zero-configuration migrations.
* **Batch Operations**: Commits imported records inside a single transaction using batch inserts, migrating thousands of records instantly.
* **Non-Blocking Futures**: Runs connection setups, configuration decodes, and database commits fully asynchronously on non-blocking thread pools.

### Spectator Follow
* **Cinematic Camera Tracking**: Teleports staff in spectator mode to follow the target seamlessly.
* **Velocity-Predicted Follow**: Computes target movements and velocity vectors (`target.getVelocity()`) to position the camera slightly ahead of the target, eliminating stutter and rubber-banding.
* **Throttled Teleports**: Auto-updates run at a throttled 4-tick (200ms) interval with a 400ms cooldown, giving client-side predicted movements and Paper's async teleportation futures time to fully resolve.
* **Configurable Boundary Tether**: Allows staff to orbit or move freely within a 10-block radius. Going beyond the boundary gently tethers the camera back into offset.

### Inventory Inspector
* **54-Slot Live GUI**: View and edit target players' main inventories (0-35), armor slots (helmet, chestplate, leggings, boots), off-hand items, and stats.
* **Live Stats & Shortcuts**:
  * **Golden Apple**: Displays health, food levels, and experience dynamically.
  * **Potion Bottle**: Shows a live list of active potion effects and durations.
  * **Ender Chest**: Quick-access shortcut item to inspect their Ender Chest directly.
* **Unparalleled Folia Safety**: Completely avoids asynchronous block-state reads (which crash under Folia when querying `.getOpenInventory().getTopInventory().getHolder()` on block-based inventories in other regions). Instead, it implements a thread-safe custom session registry that cleanly tracks active inspect views and propagates updates regionally.

### Shortcuts
* **Instant Gamemode Shifts**: Rapid commands including creative (`/gmc`), survival (`/gms`), spectator (`/gmsp`), and adventure (`/gma`).
* **Flight Mode**: Seamless command flight toggles via `/fly`.

---

## Commands & Permissions

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
| `/history` | View complete punishment logs (active/inactive) for players. | `/history <player>` | `stuff.history` |
| `/staffhistory` | View all punishments issued by a staff member. | `/staffhistory <staff>` | `stuff.staffhistory` |
| `/staffrollback` | Rollback all active punishments placed by a staff member. | `/staffrollback <staff> [confirm]` | `stuff.staffrollback` |
| `/stuffallow` | Exempt a player/UUID from active IP bans. | `/stuffallow <player> [remove]` | `stuff.stuffallow` |
| `/stuffimport` | Import punishments from other plugins (Vanilla, Essentials, LiteBans, etc). | `/stuffimport <source> [params...]` | `stuff.import` |

> **Admin Wildcard**: The permission node `stuff.admin` grants access to all capabilities of the plugin by default.
> **Bypass Node**: Players with the permission `stuff.vanish.see` can see vanished players in-game, in tab lists, and in commands.

---

## Configuration

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

# Warning escalation ladder settings
warning-ladder-enabled: true

# Actions to run when a player reaches a specific number of active warnings
warning-ladder-actions:
  1: "tempmute {player} 1h [Warning Ladder] First warning"
  2: "tempmute {player} 12h [Warning Ladder] Second warning"
  3: "tempban {player} 3d [Warning Ladder] Reached 3 warnings"
  4: "ban {player} [Warning Ladder] Reached 4 warnings"

# Discord Webhook logging settings
discord-webhook-enabled: false
discord-webhook-url: ""
discord-webhook-username: "Stuff+ Moderation"
discord-webhook-avatar-url: "https://i.imgur.com/8Qp49X0.png"

# Hex color codes for webhook embeds (without the #)
discord-webhook-color-ban: "FF5555"
discord-webhook-color-mute: "FFAA00"
discord-webhook-color-warn: "FFFF55"
```

### `messages.yml`
Features full Adventure MiniMessage compatibility, enabling gorgeous native text styling, hex colors, and gradients (e.g. `<gradient:#FF5F6D:#FFC371>Text</gradient>`).
```yaml
# Prefix for all plugin messages
prefix: "<color:#A0A0A0>[<color:#00E262>Stuff+<color:#A0A0A0>] "

no-permission: "<color:#E20000>You do not have permission to execute this command."
player-only: "<color:#E20000>This command can only be executed by a player."
cannot-overwrite-punishment: "<color:#E20000>You cannot override a punishment placed by a higher-ranking staff member ({staff})."
player-allowed: "<color:#00E262>You have allowed {player} to bypass IP bans."
player-allowed-broadcast: "<color:#00E262>{player} has been exempted from IP bans by {sender}."
player-unallowed: "<color:#00E262>You have removed IP ban exemption for {player}."
player-unallowed-broadcast: "<color:#00E262>{player} is no longer exempted from IP bans."
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

## Continuous Integration

`Stuff+` implements a fully automated CI/CD pipeline via GitHub Actions to compile, validate, and distribute artifacts:

### Dev Builds
* **Frequency**: Built automatically on every `push` to the `master` or `main` branches.
* **Release Target**: Published under the rolling **[dev-latest](https://github.com/synkfr/StuffPlus/releases/tag/dev-latest)** pre-release tag on GitHub.
* **Rolling Policy**: The pipeline automatically overwrites the previous `dev-latest` tag on every build, maintaining a clean releases page. Administrators can always access the absolute latest development features and hotfixes at this stable release target.

### Stable Releases
* **Frequency**: Generated whenever a semantic Git version tag matching `v*` (e.g., `v1.0.0`) is pushed to the repository.
* **Release Target**: Published as a dedicated production release containing formal release notes and the production-ready shaded JAR asset.

---

## bStats Metrics

Stuff+ integrates with [bStats](https://bstats.org/) to collect anonymous usage statistics. This helps us understand how the plugin is being used and guides future development.

If you wish to opt-out of anonymous metrics collection, you can disable it by navigating to your server's `plugins/bStats/config.yml` file and setting `enabled: false`.

---

## Developer Notes

For developers looking at the source code of Stuff+, here is a summary of region-threading safety rules integrated into this repository:

1. **Async Teleportation**: Never call `Player#teleport()` on Folia. Always use `Player#teleportAsync()` and queue dependent actions using `.thenRun()` or `.thenAccept()`.
2. **Recipient Thread Safety**: Action updates (like `recipient.hidePlayer()` or `recipient.showPlayer()`) must execute on the **recipient's** regional thread context. Wrap these calls in `SchedulerUtils.runEntity(plugin, recipient, ...)` to guarantee packet delivery.
3. **Avoid cross-region block state reads**: Calling `open.getHolder()` on block-based inventories (e.g., Chests, Furnaces) open in other regions throws threading exceptions. Instead, maintain an in-memory, thread-safe registry of `InvseeSession` objects to update live inventories without world queries.
4. **Cinematic Follow Damping**: Issuing `teleportAsync` to follow players every single tick saturates client-side movement buffers. Use interval checking (≥4 ticks), velocity extrapolation, and a task cooldown to maintain smooth follower views.
