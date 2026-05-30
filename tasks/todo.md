# Task List: Modern Stuff Minecraft Plugin

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
  - [x] Update `SpectatorState` class in `StuffPlugin.java` to support dynamic tracking
  - [x] Implement seamless follow logic in `MonitorCommand.java` on the staff's entity scheduler
  - [x] Verify `/gmc`, `/gms`, `/gmsp`, `/gma` and `/fly` are functioning perfectly
  - [x] Compile and verify shaded JAR builds cleanly

- [x] **Phase 7: Ultimate Vanish System Improvements**
  - [x] Implement visibility and bypass check helpers in `StuffPlugin.java`
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
  - [x] Refactor `SpectatorState` in `StuffPlugin.java` to support a `teleporting` state tracker
  - [x] Refactor `MonitorCommand.java` follow scheduler to implement asynchronous rate-limiting (teleporting flag) and a 0.2 block movement threshold
  - [x] Rebuild and verify clean compilation of shadow JAR

- [x] **Phase 10: Lag-Free Tethered Spectator Follow Optimization**
  - [x] Design the tether and delta-follow combination logic
  - [x] Refactor `MonitorCommand.java` follow loop with tether boundaries and delta tracking
  - [x] Rebuild and verify clean compilation of shadow JAR

- [x] **Phase 11: Folia Thread-Safe /invsee Session Refresh Bugfix**
  - [x] Diagnose `IllegalStateException` on `PlayerInteractEvent` due to cross-region block state reads via `.getOpenInventory().getTopInventory().getHolder()`
  - [x] Define `InvseeSession` static class and thread-safe registry in `StuffPlugin.java`
  - [x] Register active inspect sessions on GUI open in `InvseeCommand.java`
  - [x] Listen to `InventoryCloseEvent` and cleanup sessions on target logout in `PlayerListener.java`
  - [x] Refactor `refreshTargetViewers` in `PlayerListener.java` to use session lookup, eliminating asynchronous world state reads
  - [x] Rebuild and verify compilation success
  - [x] Document the Folia block-state read safety pattern in `tasks/lessons.md`

- [x] **Phase 12: Premium Moderation Feature Parity & Dynamic Command Registry**
  - [x] Perform exhaustive gap analysis between Stuff+ and advanced moderation suites
  - [x] Create comprehensive implementation plan mapping premium feature gaps
  - [x] Import missing collections and concurrency structures to `PlayerListener.java`
  - [x] Register `/history`, `/staffhistory`, and `/staffrollback` commands dynamically in `StuffPlugin.java`
  - [x] Compile and package a clean 456KB shaded shadow JAR containing HikariCP, Okaeri, and bStats dependencies

- [x] **Phase 13: Customizable Warning Escalation Ladder**
  - [x] Add warning ladder enable toggle and action threshold map configuration to `PluginConfig.java`
  - [x] Integrate warning count query and command dispatch triggers in `/warn` command processing under `PunishCommand.java`
  - [x] Implement Folia-safe console dispatching utilizing `SchedulerUtils.runGlobal`
  - [x] Compile and package the updated shaded shadow JAR verifying zero syntax errors


