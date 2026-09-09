# Changelog

All notable changes to the Staff+ moderation suite will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
