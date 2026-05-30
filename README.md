![Stuff](https://cdn.modrinth.com/data/cached_images/3d527e1c3549243fb7545c033e4c0106849f1849.png)


# Stuff+

Stuff+ is an enterprise-grade, high-performance moderation and utility plugin built for modern Minecraft networks. It ships as **two platform-specific JARs** — one for **Paper/Purpur/Folia** backend servers and one for **Velocity** proxies — sharing a unified core engine for consistent punishment management across your entire network.

**Paper Edition** runs on Paper/Purpur/Folia 1.20–1.21+ servers with full Folia multi-threaded region safety, and includes advanced staff tools like vanish, inventory inspection, and spectator monitoring.

**Velocity Edition** runs on Velocity 3.3.0+ proxies and enforces bans, IP-bans, and mutes at the network gateway before players even reach a backend server.

Both editions share the same database, configs, and migration system — deploy one or both depending on your setup.

---

## Platform Comparison

| Feature | Paper (Paper/Purpur/Folia) | Velocity (Proxy) |
| :--- | :---: | :---: |
| Ban / TempBan / Unban | ✅ | ✅ |
| IP-Ban / TempIP-Ban / UnIP-Ban | ✅ | ✅ |
| Mute / TempMute / Unmute | ✅ | ✅ |
| Warn / Warns / Clear | ✅ | ✅ |
| History / StaffHistory | ✅ | ✅ |
| Staff Rollback | ✅ | ✅ |
| IP-Ban Exemption (`/stuffallow`) | ✅ | ✅ |
| Punishment Migration (`/stuffimport`) | ✅ | ✅ |
| Discord Webhook Logging | ✅ | ✅ |
| Warning Escalation Ladder | ✅ | ✅ |
| Hierarchy Protection | ✅ | ✅ |
| Alt-Detection (IP Matching) | ✅ | ✅ |
| SQLite / MySQL Storage | ✅ | ✅ |
| Vanish System | ✅ | ❌ |
| Spectator Monitor/Follow | ✅ | ❌ |
| Inventory Inspector (`/invsee`) | ✅ | ❌ |
| Gamemode Shortcuts | ✅ | ❌ |
| Flight Toggle (`/fly`) | ✅ | ❌ |
| bStats Metrics | ✅ | ✅ |
| Folia Scheduler Safety | ✅ | N/A |
| Proxy-Level Ban Enforcement | ❌ | ✅ |

---

## Features

### Cross-Platform Core
* **Shared Database**: Both Paper and Velocity editions read and write to the same punishment database (SQLite or MySQL), enabling seamless network-wide moderation.
* **Shared Configs**: `config.yml` and `messages.yml` use identical formats on both platforms via the Okaeri configuration framework.
* **Unified Migration Engine**: Import punishments from 7+ plugins on either platform.

### Folia Safety (Paper Edition)
* **Asynchronous Lifecycles**: All commands, database transactions, and packet actions run on Folia-compliant regional or asynchronous schedulers.
* **Folia Teleportation Safety**: Standard teleports are replaced with safe, non-blocking `teleportAsync` futures, completely preventing thread crashes.
* **Isolated Packet Loops**: Visually toggling features (like Vanish hide/show packets) are scheduled on each recipient's specific regional thread, guaranteeing thread-safety.

### Proxy Gateway Enforcement (Velocity Edition)
* **Pre-Login Ban Check**: Intercepts login attempts at the proxy level and rejects banned players before they reach any backend server.
* **Network-Wide Mute Filter**: Blocks chat messages from muted players across all connected servers.
* **IP-Ban Gateway**: Enforces IP bans at the proxy gateway, preventing banned IPs from connecting to any server in the network.

### Vanish (Paper Only)
* **Total Invisibility**: Completely removes the player from the online count, player ping lists, tab completions, and visible lists.
* **Physical Interaction Bypass**: Vanished staff bypass physical pressure plates and tripwire triggers entirely.
* **Silent Containers**: Allows opening chest, furnace, and barrel interfaces silently without triggering container animations or audio.
* **Stealth Suppression**: Prevents mob targeting, item pickups, and suppresses join/quit messages entirely while active.
* **Visual Action Bar Indicator**: Real-time HUD showing vanished status via safe asynchronous repeating tasks.

### Moderation (Both Platforms)
* **Comprehensive Actions**: Integrated `/ban`, `/tempban`, `/ip-ban`, `/tempip-ban`, `/mute`, `/tempmute`, and `/warn`.
* **Warning Profiles**: Track individual warning logs via `/warns <player> [list/clear]`.
* **Dynamic Time Parsing**: Sophisticated time parser supporting complex units (e.g. `1d`, `12h`, `45m`) and infinite duration mappings (`perm`).
* **Relational Storage**: Backed by a high-performance database manager supporting both local SQLite databases and remote MySQL servers, managed via a shaded, thread-safe HikariCP connection pool.
* **Linked Account Alt-Detection**: On player login, automatically scans, logs, and broadcasts alternate accounts sharing the IP address, color-coding active (green) and banned (red) profiles.
* **Dynamic Command & History Parity**: Multi-threaded `/history <player>`, `/staffhistory <staff>`, and `/staffrollback <staff>` commands supported by dynamic database-backed name tab completions.
* **Warning Escalation Ladder**: Scale punishments dynamically (e.g. tempmutes scaling to permanent bans) based on active warning levels. Command dispatches execute safely on each platform's native scheduler.
* **Non-Blocking Discord Webhook Logs**: Real-time embeds logging all mute, ban, and warning dispatches using modern non-blocking Java 21 `HttpClient` interfaces with zero impact on region ticks.
* **Administrative Overwrite Hierarchy Protection**: Custom staff weight ranks (`stuff.hierarchy.weight.<number>`) blocking junior staff from overriding, deactivating, or removing punishments placed by senior administrators or the Console (`Integer.MAX_VALUE`).
* **IP-Ban Exemption Bypass**: Whitelist specific accounts (`/stuffallow`) to connect and bypass active IP-bans without lifting the IP ban itself for other accounts sharing the IP.

### Punishment Importer & Migration (Both Platforms)
* **7 Supported Platforms**: Seamlessly import mutes, bans, warnings, and IP bans from:
  * **Vanilla Minecraft**: Direct imports from `banned-players.json` and `banned-ips.json` in the server root.
  * **Essentials / EssentialsX**: Automated scans of mutes and bans in user profiles under `plugins/Essentials/userdata/*.yml`.
  * **LiteBans**: Migrates bans, mutes, and warnings from H2/SQLite/MySQL databases. Supports auto-detection of the `litebans.mv.db` H2 file or manual JDBC parameters.
  * **AdvancedBan**: Integrates and normalizes legacy SQL `Punishments` tables from SQLite or MySQL.
  * **MaxBans**: Migration of bans, IP bans, mutes, and warnings from SQLite or MySQL.
  * **BanManager**: Integrates historical player bans, mutes, and warnings with configurable table prefixes.
  * **BungeeAdminTools (BAT)**: Imports entries from database tables `bat_ban` and `bat_mute`.
* **Dynamic Auto-Scanning**: Scans your server's active files on command, extracts SQLite paths or remote SQL database credentials, and allows zero-configuration migrations.
* **Batch Operations**: Commits imported records inside a single transaction using batch inserts, migrating thousands of records instantly.
* **Non-Blocking Futures**: Runs connection setups, configuration decodes, and database commits fully asynchronously on non-blocking thread pools.

### Spectator Follow (Paper Only)
* **Cinematic Camera Tracking**: Teleports staff in spectator mode to follow the target seamlessly.
* **Velocity-Predicted Follow**: Computes target movements and velocity vectors (`target.getVelocity()`) to position the camera slightly ahead of the target, eliminating stutter and rubber-banding.
* **Throttled Teleports**: Auto-updates run at a throttled 4-tick (200ms) interval with a 400ms cooldown, giving client-side predicted movements and Paper's async teleportation futures time to fully resolve.
* **Configurable Boundary Tether**: Allows staff to orbit or move freely within a 10-block radius. Going beyond the boundary gently tethers the camera back into offset.

### Inventory Inspector (Paper Only)
* **54-Slot Live GUI**: View and edit target players' main inventories (0-35), armor slots (helmet, chestplate, leggings, boots), off-hand items, and stats.
* **Live Stats & Shortcuts**:
  * **Golden Apple**: Displays health, food levels, and experience dynamically.
  * **Potion Bottle**: Shows a live list of active potion effects and durations.
  * **Ender Chest**: Quick-access shortcut item to inspect their Ender Chest directly.
* **Unparalleled Folia Safety**: Completely avoids asynchronous block-state reads (which crash under Folia when querying `.getOpenInventory().getTopInventory().getHolder()` on block-based inventories in other regions). Instead, it implements a thread-safe custom session registry that cleanly tracks active inspect views and propagates updates regionally.

### Shortcuts (Paper Only)
* **Instant Gamemode Shifts**: Rapid commands including creative (`/gmc`), survival (`/gms`), spectator (`/gmsp`), and adventure (`/gma`).
* **Flight Mode**: Seamless command flight toggles via `/fly`.

---

## Commands & Permissions

### Network-Wide Commands (Paper & Velocity)

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
| `/history` | View complete punishment logs (active/inactive) for players. | `/history <player>` | `stuff.history` |
| `/staffhistory` | View all punishments issued by a staff member. | `/staffhistory <staff>` | `stuff.staffhistory` |
| `/staffrollback` | Rollback all active punishments placed by a staff member. | `/staffrollback <staff> [confirm]` | `stuff.staffrollback` |
| `/stuffallow` | Exempt a player/UUID from active IP bans. | `/stuffallow <player> [remove]` | `stuff.stuffallow` |
| `/stuffimport` | Import punishments from other plugins. | `/stuffimport <source> [params...]` | `stuff.import` |

### Paper-Only Commands

| Command | Description | Usage | Permission Node |
| :--- | :--- | :--- | :--- |
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

## Installation

### Paper Edition (Paper / Purpur / Folia)
1. Download `StuffPlus-Paper-1.0.0.jar` from [Releases](https://github.com/synkfr/StuffPlus/releases).
2. Place the JAR in your server's `plugins/` folder.
3. Restart your server.
4. Edit `plugins/Stuff/config.yml` and `plugins/Stuff/messages.yml` to your liking.

### Velocity Edition (Proxy)
1. Download `StuffPlus-Velocity-1.0.0.jar` from [Releases](https://github.com/synkfr/StuffPlus/releases).
2. Place the JAR in your Velocity proxy's `plugins/` folder.
3. Restart your proxy.
4. Edit `plugins/stuffplus/config.yml` and `plugins/stuffplus/messages.yml` to your liking.

### Shared Database (Network Mode)
To share punishments across your entire network, configure **both** editions to use the same MySQL database:

```yaml
# config.yml (same on both Paper and Velocity)
storage-type: "mysql"
mysql-host: "your-db-host"
mysql-port: 3306
mysql-database: "stuffplus"
mysql-username: "your-user"
mysql-password: "your-password"
```

---

## Configuration

The plugin uses Okaeri Config to generate, validate, and auto-update clean YAML configuration structures. Both platforms share identical configuration formats.

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

# Vanish settings (Paper only — ignored on Velocity)
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

# Vanish (Paper only)
vanish-enabled: "<color:#00E262>You are now vanished."
vanish-disabled: "<color:#00E262>You are no longer vanished."
vanish-action-bar: "<color:#00E262>★ YOU ARE VANISHED ★"
```

---

## Build & Compilation

Stuff+ is a **multi-module Gradle project** producing two independent shaded JARs.

### Requirements
* **Java 21** or higher
* **Gradle** (installed or via wrapper)

### Build Command
Compile and build both platform JARs:
```bash
./gradlew clean build
```

The compiled plugins will be located at:
```
stuff-paper/build/libs/StuffPlus-Paper-1.0.0.jar    # Paper/Purpur/Folia servers
stuff-velocity/build/libs/StuffPlus-Velocity-1.0.0.jar  # Velocity proxies
```

### Project Structure
```
StuffPlus/
├── stuff-core/          # Shared platform-agnostic library
│   └── src/main/java/   # Database, Punishment model, Configs, Migration, Utils
├── stuff-paper/         # Paper/Folia backend plugin
│   └── src/main/java/   # Paper commands, listeners, vanish, monitor, invsee
├── stuff-velocity/       # Velocity proxy plugin
│   └── src/main/java/   # Velocity commands, login/chat listeners
├── build.gradle          # Root configuration
└── settings.gradle       # Multi-module declaration
```

*Note: The Gradle shadow configuration automatically relocates `eu.okaeri`, `com.zaxxer.hikari`, `org.h2`, and `org.xerial` libraries to private namespaces (`me.ayosynk.stuff.libs.*`) to prevent conflicts with any other plugins on the classpath.*

---

## Continuous Integration

`Stuff+` implements a fully automated CI/CD pipeline via GitHub Actions to compile, validate, and distribute artifacts for **both platforms**.

### Dev Builds
* **Frequency**: Built automatically on every `push` to the `master` or `main` branches.
* **Release Target**: Published under the rolling **[dev-latest](https://github.com/synkfr/StuffPlus/releases/tag/dev-latest)** pre-release tag on GitHub.
* **Rolling Policy**: The pipeline automatically overwrites the previous `dev-latest` tag on every build, maintaining a clean releases page.
* **Artifacts**: Both `StuffPlus-Paper` and `StuffPlus-Velocity` JARs are published in every release.

### Stable Releases
* **Frequency**: Generated whenever a semantic Git version tag matching `v*` (e.g., `v1.0.0`) is pushed to the repository.
* **Release Target**: Published as a dedicated production release containing formal release notes and both platform-ready shaded JAR assets.

---

## bStats Metrics

Both editions of Stuff+ integrate with [bStats](https://bstats.org/) to collect anonymous usage statistics. This helps us understand how the plugin is being used and guides future development.

- **Paper Edition**: Uses `bstats-bukkit` for server-side metrics.
- **Velocity Edition**: Uses `bstats-velocity` (Plugin ID: `31693`) for proxy-side metrics.

If you wish to opt-out of anonymous metrics collection, you can disable it by navigating to the `plugins/bStats/config.yml` file on your server or proxy and setting `enabled: false`.

---

## Developer Notes

For developers looking at the source code of Stuff+, here is a summary of the architecture:

### Multi-Module Architecture
1. **stuff-core**: Platform-agnostic shared library containing the database layer, punishment model, config system, migration engine, and utility classes. Uses `ForkJoinPool.commonPool()` for async operations — no platform-specific scheduler dependencies.
2. **stuff-paper**: Paper/Folia plugin implementing `StuffPlatform` interface. Contains all Paper-specific commands, listeners, vanish, monitor, invsee, and gamemode shortcuts. Uses Folia-safe regional schedulers.
3. **stuff-velocity**: Velocity proxy plugin implementing `StuffPlatform` interface. Contains proxy-level commands and login/chat event listeners for network-wide enforcement.

### Folia Threading Rules (Paper)
1. **Async Teleportation**: Never call `Player#teleport()` on Folia. Always use `Player#teleportAsync()` and queue dependent actions using `.thenRun()` or `.thenAccept()`.
2. **Recipient Thread Safety**: Action updates (like `recipient.hidePlayer()` or `recipient.showPlayer()`) must execute on the **recipient's** regional thread context. Wrap these calls in `SchedulerUtils.runEntity(plugin, recipient, ...)` to guarantee packet delivery.
3. **Avoid cross-region block state reads**: Calling `open.getHolder()` on block-based inventories open in other regions throws threading exceptions. Instead, maintain an in-memory, thread-safe registry of `InvseeSession` objects.
4. **Cinematic Follow Damping**: Use interval checking (≥4 ticks), velocity extrapolation, and a task cooldown to maintain smooth follower views.
