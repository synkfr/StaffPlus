# Changelog

All notable changes to the Staff+ moderation suite will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.3] - 2026-09-25

### Added
* **Velocity Backend Command Forwarding**:
  * Implemented automatic transparent command forwarding (`velocity-forward-to-backend: true`). If a staff member executes a moderation command (`/tempban`, `/ban`, `/mute`, etc.) on Velocity and lacks proxy-level permissions (e.g. LuckPerms and OPs are configured exclusively on backend Paper servers), Velocity automatically forwards the command down to their connected Paper server via `player.spoofChatInput(...)` instead of dropping it with a "no permission" error.
  * Added `velocity-proxy-commands` (default: `true`) in `config.yml`, allowing server administrators to optionally disable proxy command registration so backend Paper servers handle all moderation commands directly while Velocity focuses purely on gateway enforcement.
* **Limbo Kick Prevention via `KickedFromServerEvent`**:
  * Subscribed to Velocity's `KickedFromServerEvent`. When a player is explicitly kicked (`/kick`) or banned by staff on a backend server, Velocity intercepts the event and executes `DisconnectPlayer.create(reason)`.
  * Completely eliminates the issue where kicked or banned players bounced into fallback or Limbo servers instead of being disconnected from the proxy network.
  * Preserved normal server restart and shutdown routing ("Server closed"), which continues sending players to the lobby.
  * Added `velocity-disconnect-on-kick` (default: `true`) in `config.yml`.
* **Complete Alias Registration on Velocity**:
  * Registered all moderation command aliases with Velocity's `CommandManager`: `/ipban`, `/banip`, `/tempipban`, `/tempbanip`, `/unipban`, `/unbanip`, `/banleaderboard`, `/staffleaderboard`, `/bancheck`, `/staff+`, `/punishhistory`, and `/historylog`.

### Fixed
* **Velocity Asynchronous Event Race Condition**:
  * Refactored `onLogin` and `onPlayerChat` listeners in `VelocityListeners` to return `EventTask` with continuations. Velocity now properly pauses event dispatch until database ban and mute queries complete, ensuring rulebreakers are reliably rejected at the gateway and muted players are blocked from chatting.
* **Database Staff Hierarchy Weight Preservation**:
  * Updated `DatabaseManager.savePlayer` SQL upsert logic to preserve existing hierarchy weights when incoming weight is 0 (`weight = CASE WHEN VALUES(weight) > 0 THEN VALUES(weight) ELSE weight END`).
  * Fixes an issue where staff logging in through the Velocity proxy had their hierarchy weight reset to 0 in the shared database.
* **Instant Online Target Resolution on Velocity**:
  * Added `resolveTargetUuid` across all punishment handlers in `VelocityPunishCommand`, immediately resolving online players via `plugin.getServer().getPlayer(...)` before falling back to database lookups.
* **Unit Testing**:
  * Added unit test cases for hierarchy weight preservation and Velocity configuration defaults in `DatabaseManagerTest`.

### Changed
* Bumped project version to `1.2.3` across all subprojects, build scripts, plugin descriptors, and documentation.

---

## [1.2.2] - 2026-09-17

### Fixed
* **Database Driver Bundling & Connection Hardening**:
  * Shaded and relocated official `com.mysql:mysql-connector-j:8.3.0` and `org.mariadb.jdbc:mariadb-java-client:3.3.3` across Paper and Velocity editions, fixing `ClassNotFoundException` / `No suitable driver found` on proxies and bare server environments.
  * Added native support for `storage-type: "mariadb"` alongside `"mysql"` and `"sqlite"`.
  * Added essential JDBC properties to connection pools: `createDatabaseIfNotExist=true` (prevents `Unknown database` errors), `allowPublicKeyRetrieval=true` (fixes MySQL 8+ authentication), `serverTimezone=UTC`, and `characterEncoding=utf8`.
  * Implemented automated graceful fallback to local SQLite (`database.db`) with clear console warnings if a remote MySQL/MariaDB database cannot be reached, ensuring the plugin never disables itself and commands remain fully functional.
  * Dynamic database reconnection: `/staff reload` now automatically re-initializes and reconnects the database pool on both Paper and Velocity.
* **Command Permissions & Operator Bypass**:
  * Added operator (`isOp()`) and `staff.admin` bypass to `DynamicCommand` and `StaffCommand` on Paper, guaranteeing OP players have access to `/bans`, `/banhistory`, `/staff reload`, and all moderation utilities.
  * Added top-level permission declarations with `default: op` in `paper-plugin.yml` and created `plugin.yml` for complete compatibility with LuckPerms and Spigot-based permission managers.
  * Fixed command permission mapping on Velocity: alias commands (`/banhistory`, `/banlist`, `/bancheck`) now resolve to their canonical permission nodes (`staff.bans`, `staff.checkban`), and `staff.admin` grants global access to all proxy moderation commands.
* **Unit Testing**:
  * Added `DatabaseManagerTest` unit test suite covering SQLite initialization, player records, and remote database fallback.

### Changed
* Bumped project version to `1.2.2` across all subprojects, build scripts, plugin descriptors, and documentation.

---

## [1.2.1] - 2026-09-14

### Fixed
* **Bedrock / Geyser Classloader Isolation**:
  * Decoupled `BedrockFormManager` into a zero-dependency facade, eliminating compile-only Cumulus and Geyser/Floodgate imports from the class verification path.
  * Isolated Cumulus form creation and API calls into internal `BedrockFormHandler`, `GeyserInvoker`, and `FloodgateInvoker` classes, classloaded strictly on-demand only when `Geyser-Spigot` or `floodgate` is enabled on the Paper server.
  * Resolved `NoClassDefFoundError: org/geysermc/cumulus/form/Form` that prevented `/bans`, `/banhistory`, and `/bansleaderboard` from opening on backend Paper servers operating without local Geyser or Floodgate plugins (e.g., proxy-only Geyser network architectures).
  * Backend Paper servers without Floodgate now gracefully fall back to native 54-slot Java chest inventory GUIs with zero console errors or stack traces.
  * Added `BedrockCompatTest` unit test verifying that Bedrock detection cleanly returns false without throwing classloading exceptions when dependencies are absent.

### Changed
* Bumped project version to `1.2.1` across all subprojects, build scripts, plugin descriptors, and documentation.

---

## [1.2.0] - 2026-09-13

### Added
* **Native Bedrock Geyser Forms**:
  * Implemented `BedrockFormManager` with dual detection for `GeyserApi` (`Geyser-Spigot`) and `FloodgateApi` (`floodgate`).
  * Bedrock players running `/bans` receive a native Bedrock `SimpleForm` with paginated ban list buttons, detailed ban modal inspections, and back navigation.
  * Bedrock players running `/bansleaderboard` receive a native Bedrock `SimpleForm` displaying top staff member statistics and ban counts.
  * Runtime soft-dependency isolation guaranteeing zero `NoClassDefFoundError` or crashes on servers without Geyser or Floodgate.
* **Java Chest Inventory GUIs**:
  * Implemented `BansMenuHolder` 54-slot chest GUI on Paper for Java players with player heads, active/expired status badges, and clickable page navigation buttons.
  * Implemented `BansLeaderboardHolder` 54-slot chest GUI on Paper displaying top 10 staff with gold, silver, and bronze podium styling and player heads.
  * Full support for `--chat` / `-c` flag and automatic fallback to paginated chat text for Console and Velocity proxy execution.
* **New Moderation Commands**:
  * `/kick <player> [reason]`: Online player kick enforcing administrative hierarchy checks, Folia entity-thread safety, and Discord webhook logging.
  * `/unwarn <player> [id]`: Deactivates a player's latest active warning (or a specific warning ID) with hierarchy weight validation against the issuing staff member.
  * `/checkban <player/IP>` (alias `/bancheck`): Status audit command displaying active ban or IP-ban status, expiry, issuing staff, and reason.
* **Dynamic Configuration Reloading**:
  * Added `/staff reload` (and `/staffplus reload`) across both Paper and Velocity to dynamically reload `config.yml` and `messages.yml` without server restarts.
* **Core Database Enhancements**:
  * Added `getLatestActiveWarning(UUID)` to `DatabaseManager` for quick warning resolution.
  * Added `removeWarningById(int)` and `removeLatestWarning(UUID)` for warning revocation.
  * Added `getPunishmentById(int)` for targeted punishment inspection.
  * Added `isPermanent()` and `getDuration()` helpers to `Punishment`.

### Changed
* Bumped project version to `1.2.0` across all subprojects, build scripts, plugin descriptors, and documentation.
* Added `staff.kick`, `staff.unwarn`, `staff.checkban`, and `staff.staff.reload` permission nodes to `paper-plugin.yml`.

---

## [1.1.0] - 2026-09-09

### Added
* **Modrinth Version Notifier**:
  * Asynchronous update checker querying the Modrinth v2 API (`https://api.modrinth.com/v2/project/staff+/version`) using native Java 21 `HttpClient` and Gson.
  * Compares semantic versioning between running and published releases.
  * Automatically logs update availability to the server console upon enable.
  * In-game notifications for server administrators and OPs (`staff.admin`, `staff.update.notify`, or OP) upon joining, featuring interactive clickable links to [Modrinth Releases](https://modrinth.com/plugin/staff%2B/versions).
  * Configuration options `update-checker-enabled` and `update-checker-notify-admins` in `config.yml`.
* **Global Ban History Command (`/bans`)**:
  * Cross-platform `/bans [page]` command (aliases: `/banhistory`, `/banlist`) available on both Paper/Purpur/Folia servers and Velocity proxies.
  * Displays paginated ban logs with `[ACTIVE]` and `[EXPIRED]` badges, target name/IP, staff member, reason, and timestamps.
  * Interactive pagination buttons (`[« Previous]` and `[Next »]`) enabling one-click page navigation.
  * Permission node: `staff.bans` (granted by default under `staff.admin`).
* **Staff Moderation Leaderboard (`/bansleaderboard`)**:
  * Cross-platform `/bansleaderboard` command (aliases: `/banleaderboard`, `/staffleaderboard`) available on Paper and Velocity.
  * Displays the Top 10 staff members ranked by total bans and IP bans issued.
  * Configurable toggle `bans-leaderboard-enabled` in `config.yml` allowing server owners to enable or disable staff leaderboards.
  * Permission node: `staff.bansleaderboard` (granted by default under `staff.admin`).
* **Core Database Enhancements**:
  * Added `getRecentBans(offset, limit)` to `DatabaseManager` for paginated ban retrieval.
  * Added `getTotalBanCount()` to `DatabaseManager` for dynamic page calculation.
  * Added `getStaffBanLeaderboard(limit)` and `StaffLeaderboardEntry` for staff ranking aggregation.
  * Statements tested and fully compliant with SQLite and MySQL `ONLY_FULL_GROUP_BY` strict modes.
* **Testing & Platform**:
  * Added `UpdateCheckerTest` unit test suite covering semantic version parsing, release prefixes, and edge cases.
  * Added `StaffPlatform#getPluginVersion()` method for platform-agnostic version resolution.

### Changed
* Bumped project version to `1.1.0` across root `build.gradle`, `paper-plugin.yml`, `StaffVelocityPlugin`, `package.json`, and documentation.
* Configured JUnit test execution in `staff-paper/build.gradle` with ByteBuddy experimental property and dynamic agent loading for modern Java compatibility.
* Updated documentation files (`README.md`, `commands.md`, `configuration.md`, `getting-started.md`) with the new commands, options, and download sources.

---

## [1.0.0] - 2026-05-31

### Added
* **Multi-Platform Architecture**:
  * Dedicated Paper edition supporting Paper, Purpur, and Folia 1.20–1.21+.
  * Dedicated Velocity edition supporting Velocity 3.3.0+ proxies.
  * Unified `staff-core` library with shared database models and configuration format.
* **Punishment Management**:
  * Commands: `/ban`, `/tempban`, `/unban`, `/ip-ban`, `/tempip-ban`, `/unip-ban`, `/mute`, `/tempmute`, `/unmute`, `/warn`, `/warns`.
  * Historical audits via `/history <player>`, `/staffhistory <staff>`, and `/staffrollback <staff>`.
  * Dynamic IP-ban exemption whitelist via `/staffallow`.
* **Vanish System (Paper)**:
  * Invisibility removing staff from online counts, tab completions, and player ping lists.
  * Silent chest/container interaction without animation or audio.
  * Suppression of mob targeting, item pickups, and pressure plate triggers.
  * Real-time HUD action bar indicator.
* **Spectator Monitoring (Paper)**:
  * Throttled spectator camera tracking with velocity vector prediction.
  * Smooth boundary tethering within a 10-block orbit radius.
* **Inventory Inspector (Paper)**:
  * 54-slot live GUI inspecting inventories, armor, off-hand items, and effects.
  * Thread-safe session registry preventing Folia cross-region block state read exceptions.
* **Warning Escalation Ladder**:
  * Configurable automatic punishment ladder triggered by active warning count thresholds.
* **Administrative Overwrite Hierarchy**:
  * Weight-based rank hierarchy preventing lower-tier staff from overriding senior staff actions.
* **Multi-Source Importer**:
  * Automated scans and batch imports from Vanilla, Essentials, LiteBans, AdvancedBan, MaxBans, BanManager, and BungeeAdminTools.
* **Integrations**:
  * Asynchronous Discord webhook logging via native `HttpClient`.
  * bStats metrics integration.
  * Okaeri Config framework with MiniMessage hex color and gradient support.
