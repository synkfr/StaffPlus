# Task List: Modern Staff Minecraft Plugin

- [x] **Phase 1: Project Setup**
  - [x] Create `settings.gradle` and `build.gradle` (with shadow and Okaeri Config dependencies)
  - [x] Create `src/main/resources/paper-plugin.yml` with Folia support enabled
- [x] **Phase 2: Core Architecture**
  - [x] Implement `SchedulerUtils` for Paper/Folia compatibility
  - [x] Implement `DurationUtils` for parsing durations (e.g. `1d`, `12h`, `30m`)
  - [x] Create `PluginConfig` and `MessageConfig` using Okaeri Config
  - [x] Create `DatabaseManager` with SQLite and MySQL support using HikariCP
- [x] **Phase 3: Moderation System**
  - [x] Create `Punishment` data structure and database tables
  - [x] Implement `PunishCommand` representing `/mute`, `/tempmute`, `/ban`, `/tempban`, `/ip-ban`, `/tempip-ban`, `/warn`
  - [x] Add `/unmute`, `/unban`, `/unipban`, `/warns` (clear/check) commands
  - [x] Dynamic tab completion for offline/online players from the database
- [x] **Phase 4: Utility Commands**
  - [x] Implement `/invsee` with a 54-slot custom inventory syncing armor, offhand, stats, and real-time updates
  - [x] Implement `/vanish` with persistence, mob target prevention, pressure plate bypass, and join listeners
  - [x] Implement `/monitor` and `/monitor leave` using spectate target tracking and safety location backups
- [x] **Phase 5: Listeners & Polish**
  - [x] Implement join check listener for active bans/ip-bans
  - [x] Implement chat check listener for active mutes
  - [x] Implement vanish listener to prevent picking up items, mob targeting, physical plate triggers
  - [x] Build the plugin and verify success

- [x] **Phase 6: Seamless Spectator Monitor & Shortcuts Refactor**
  - [x] Create implementation plan and design auto-follow logic
  - [x] Update `SpectatorState` class in `StaffPlugin.java` to support dynamic tracking
  - [x] Implement seamless follow logic in `MonitorCommand.java` on the staff's entity scheduler
  - [x] Verify `/gmc`, `/gms`, `/gmsp`, `/gma` and `/fly` are functioning perfectly
  - [x] Compile and verify shaded JAR builds cleanly

- [x] **Phase 7: Ultimate Vanish System Improvements**
  - [x] Implement visibility and bypass check helpers in `StaffPlugin.java`
  - [x] Add `ServerListPingEvent` in `VanishListener.java` to deduct vanished players from online count
  - [x] Hide join/quit broadcast messages for vanished players in `PlayerListener.java`
  - [x] Filter vanished players out of all custom command tab completions for regular players
  - [x] Filter vanished players out of target command routing (report not found) for regular players
  - [x] Rebuild and verify compilation succeeds

- [x] **Phase 8: Thread-Safe Vanish Packet Delivery under Folia**
  - [x] Identify cross-region hidePlayer and showPlayer threading exception vectors
  - [x] Refactor dynamic toggle `/vanish` packet loops in `VanishCommand.java` to execute on target recipient schedulers
  - [x] Refactor persistent on-join vanish packet loops in `PlayerListener.java` to execute on target recipient schedulers
  - [x] Rebuild and verify clean Folia-compatible shaded JAR compilation

- [x] **Phase 9: Spectator Follow Optimization & Throttle**
  - [x] Identify cause of follow lag (concurrent uncompleted async teleports overloading server/client)
  - [x] Refactor `SpectatorState` in `StaffPlugin.java` to support a `teleporting` state tracker
  - [x] Refactor `MonitorCommand.java` follow scheduler to implement asynchronous rate-limiting (teleporting flag) and a 0.2 block movement threshold
  - [x] Rebuild and verify clean compilation of shadow JAR

- [x] **Phase 10: Lag-Free Tethered Spectator Follow Optimization**
  - [x] Design the tether and delta-follow combination logic
  - [x] Refactor `MonitorCommand.java` follow loop with tether boundaries and delta tracking
  - [x] Rebuild and verify clean compilation of shadow JAR

- [x] **Phase 11: Folia Thread-Safe /invsee Session Refresh Bugfix**
  - [x] Diagnose `IllegalStateException` on `PlayerInteractEvent` due to cross-region block state reads via `.getOpenInventory().getTopInventory().getHolder()`
  - [x] Define `InvseeSession` static class and thread-safe registry in `StaffPlugin.java`
  - [x] Register active inspect sessions on GUI open in `InvseeCommand.java`
  - [x] Listen to `InventoryCloseEvent` and cleanup sessions on target logout in `PlayerListener.java`
  - [x] Refactor `refreshTargetViewers` in `PlayerListener.java` to use session lookup, eliminating asynchronous world state reads
  - [x] Rebuild and verify compilation success
  - [x] Document the Folia block-state read safety pattern in `tasks/lessons.md`

- [x] **Phase 12: Premium Moderation Feature Parity & Dynamic Command Registry**
  - [x] Perform exhaustive gap analysis between Staff+ and advanced moderation suites
  - [x] Create comprehensive implementation plan mapping premium feature gaps
  - [x] Import missing collections and concurrency structures to `PlayerListener.java`
  - [x] Register `/history`, `/staffhistory`, and `/staffrollback` commands dynamically in `StaffPlugin.java`
  - [x] Compile and package a clean 456KB shaded shadow JAR containing HikariCP, Okaeri, and bStats dependencies

- [x] **Phase 13: Customizable Warning Escalation Ladder**
  - [x] Add warning ladder enable toggle and action threshold map configuration to `PluginConfig.java`
  - [x] Integrate warning count query and command dispatch triggers in `/warn` command processing under `PunishCommand.java`
  - [x] Implement Folia-safe console dispatching utilizing `SchedulerUtils.runGlobal`
  - [x] Compile and package the updated shaded shadow JAR verifying zero syntax errors

- [x] **Phase 14: Discord Webhook Logging**
  - [x] Add Discord Webhook parameters (`enabled`, `url`, `username`, `avatar_url`, hex colors) to `PluginConfig.java`
  - [x] Implement asynchronous DiscordWebhookUtils utilizing native Java 21 `HttpClient` for safe non-blocking requests
  - [x] Hook webhook dispatches into `/mute`, `/ban`, `/ip-ban`, and `/warn` inside `PunishCommand.java`
  - [x] Compile and verify clean shaded shadow JAR compilation without errors

- [x] **Phase 15: Punishment Overwrite Hierarchy**
  - [x] Add cannot-overwrite-punishment error message to `MessageConfig.java`
  - [x] Add non-destructive `weight` column database migration and update `savePlayer` in `DatabaseManager.java`
  - [x] Query and cache dynamic permission weights on `PlayerJoinEvent` in `PlayerListener.java`
  - [x] Implement `getHierarchyWeight` and asynchronous `checkHierarchy` in `PunishCommand.java`
  - [x] Weave overwrite checks inside `/mute`, `/tempmute`, `/unmute`, `/ban`, `/tempban`, `/unban`, `/ip-ban`, `/tempip-ban`, `/unip-ban`, and `/warns clear`
  - [x] Compile and package updated shadow JAR with full administrative protection

- [x] **Phase 16: IP-Ban Allow Exemption Bypass**
  - [x] Add dynamic bypass allow feedback messages and getters to `MessageConfig.java`
  - [x] Create `staff_allows` tracking table and query methods (`addAllow`, `removeAllow`, `isAllowed`) in `DatabaseManager.java`
  - [x] Wire pre-login allow exemption check to completely bypass active IP bans in `PlayerListener.java`
  - [x] Register `/staffallow` command (aliases `/allow`, `/allowip`) dynamically in `StaffPlugin.java`
  - [x] Implement `/staffallow` case, weight protection check, and tab completion suggestions in `PunishCommand.java`
  - [x] Compile and verify clean shaded shadow JAR compilation without errors

- [x] **Phase 17: Multi-Source Importer & Migration System**
  - [x] Create modern `ImportedPunishment` normalized payload schema and `MigrationSource` interface
  - [x] Program multi-threaded batch insertion transactions `importBatch` inside `DatabaseManager.java`
  - [x] Implement local Vanilla JSON files parsing (`banned-players.json`, `banned-ips.json`) using Gson
  - [x] Implement Essentials userdata YAML files parsing
  - [x] Build automated database autodetectors for **LiteBans**, **AdvancedBan**, **MaxBans**, **BanManager**, and **BungeeAdminTools**
  - [x] Design premium interactive status overview command `/staffimport` (with `/migrate` and `/staffmigrate` aliases)
  - [x] Dynamically register `/staffimport` executor and tab suggestions, and declare the permission node in `paper-plugin.yml`
  - [x] Rebuild and verify shadow JAR builds cleanly

- [x] **Phase 18: Git Commits Partitioning**
  - [x] Stage and commit multi-module Gradle project structure
  - [x] Stage and commit StaffPlatform interface
  - [x] Stage and commit migrated core models and utilities
  - [x] Stage and commit migrated core configurations
  - [x] Stage and commit migrated DatabaseManager
  - [x] Stage and commit migrated migration framework
  - [x] Stage and commit staff-paper module commands, listeners, and resources
  - [x] Stage and commit staff-velocity module core classes
  - [x] Stage and commit bStats integration for staff-velocity
  - [x] Stage and commit GitHub Actions workflow update
  - [x] Stage and commit README documentation update
  - [x] Stage and commit VitePress pages updates
  - [x] Verify git log has 10+ clean and distinct commits

- [x] **Phase 19: Rename Staff to Staff**
  - [x] Update documentation and configurations replacing Staff+ with Staff+
  - [x] Rename Gradle modules/subprojects (`staff-*` to `staff-*`)
  - [x] Refactor Java packages (`me.ayosynk.staff` to `me.ayosynk.staff`)
  - [x] Rename commands and permissions (`/staffallow` -> `/staffallow`, etc.)
  - [x] Update GitHub Actions workflow and properties
  - [x] Verify clean build and correct JAR output names

- [x] **Phase 20: Staff Info command and GUI**
  - [x] Implement `StaffInfoHolder` class to manage inventory GUI data
  - [x] Create `StaffCommand` with `/staff info <player>` subcommand
  - [x] Register `/staff` command dynamically in `StaffBukkitPlugin`
  - [x] Add `InventoryClickEvent` handling for `StaffInfoHolder` in `PlayerListener`
  - [x] Add `staff.info` permission description in `paper-plugin.yml`
  - [x] Verify build compiles and tests cleanly

- [x] **Phase 21: Customizable Staff Info GUI via YAML & Reload Command**
  - [x] Disable default italics in all parsed MiniMessage components globally (GUI display names & lore)
  - [x] Create customizable `StaffInfoMenuConfig` data container supporting zmenu-like layout schema
  - [x] Implement error-resilient YAML loader `MenuManager` checking for size, slot, and material validation
  - [x] Register default `staff_info.yml` menu layout in plugin jar resources
  - [x] Integrate `MenuManager` initialization & getter in `StaffBukkitPlugin`
  - [x] Add `/staff reload` subcommand supporting console/player configuration & menu reloads
  - [x] Refactor `StaffCommand` GUI building to dynamically draw items, slots, titles, and placeholders from configuration
  - [x] Refactor `PlayerListener` click handling using dynamic slot-to-action registry from `StaffInfoHolder`
  - [x] Verify build compiles and shaded JAR builds cleanly
  - [x] Write localized YAML parser test to prove error resilience and graceful fallback

## Review & Verification Results

### 1. Compiles and Shading Integrity
- Successfully compiled the plugin with `./gradlew clean build`.
- Generated shaded shadow JAR size: ~456 KB.
- Shaded package names relocated under `me.ayosynk.staff.libs.*` to prevent classpath pollution:
  - `eu.okaeri.configs` relocated successfully.
  - `com.zaxxer.hikari` relocated successfully.

### 2. Phase 16: IP-Ban Allow Exemption Bypass
- Added `staff_allows` tracking table in SQLITE/MYSQL.
- Programmed native `/staffallow <player> [remove]` command (with aliases `/allow`, `/allowip`).
- Wired pre-login connection verification. Whitelisted UUIDs completely bypass IP-ban blocks, allowing safe connection of players sharing a banned IP while keeping the IP ban fully active for others.
- Wired hierarchical weight checking. Lower-ranking staff members are blocked from adding or removing IP ban exemptions for accounts banned by senior administrators.

### 3. Phase 17: Multi-Source Importer & Migration System
- Programmed batch transaction imports in a single transaction, supporting MySQL/SQLite backends.
- Formulated automatic database scanners mapping local SQLite files and remote SQL databases automatically.
- Extensively supports imports from: Vanilla bans, Essentials mutes/bans, LiteBans, AdvancedBan, MaxBans, BanManager, and BungeeAdminTools.
- Built interactive command display `/staffimport` showing active autodetected configuration directories.

### 4. General Framework Stability
- Fully compatible with Folia's multi-threaded region-threading context.
- Zero main-thread blocking operations. All DB transactions, UUID resolves, and external logging actions execute on asynchronous execution pools or regional task loops.

### 5. Phase 18: Git Commits Partitioning
- Organized the multi-module project changes into 12 granular, distinct, and logical Git commits following Conventional Commits.
- Verified commit progression with `git log` and ensured the workspace builds successfully.

### 6. Phase 19: Rename Stuff to Staff
- Renamed all directories and build configurations from stuff to staff.
- Refactored all Java package declarations and imports to use `me.ayosynk.staff`.
- Updated all plugin commands, permissions, and database tables to use "staff".
- Renamed display names, logs, metrics, workflows, and documentation pages from Stuff+ to Staff+.
- Verified clean build producing `StaffPlus-Paper-1.0.0.jar` and `StaffPlus-Velocity-1.0.0.jar`.

### 7. Phase 20: Staff Info GUI & command
- Implemented `/staff info <player>` command opening an interactive chest inventory GUI.
- Shows dynamic connections, warnings, alts, and exemption statuses.
- Provides clickable punish-shortcuts (kick, warn, mute, ban, clear history, and toggle whitelist bypass) utilizing hierarchy weight permission checks.
- Safely handles offline players by turning active buttons to red glass panes.
- Compiles successfully without warnings/errors under Paper/Folia.

### 8. Phase 21: Customizable Staff Info GUI via YAML & Reload Command
- Removed default italics globally across item names/lore by altering the MiniMessage parsing decorator.
- Extracted all `/staff info` layout characteristics (title, size, materials, names, slots list, fill item, and placeholders) to `menus/staff_info.yml` following the zmenu YAML schema format.
- Implemented a robust `MenuManager` parser catching parsing or validation errors (invalid YAML syntax, invalid materials, bad sizes, out of bound slots) and logging warning reports while dynamically falling back to standard configuration settings.
- Wired `/staff reload` console and player command executing real-time reloads of config, messages, and GUI files.
- Completed full unit test validation (`MenuManagerTest.java`) confirming graceful error handling and correct default settings fallback under invalid YAML/logical configurations.
- Verified compilation builds cleanly and generated shaded JAR.



