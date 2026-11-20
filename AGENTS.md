# StaffPlus agent instructions

StaffPlus is a modular moderation plugin suite for Paper, Folia, and Velocity.

## Repository structure

The project contains three Gradle modules.

- `staff-core`: Platform-independent library. Contains configuration models, database access, migrations, and shared utilities. No Bukkit or Velocity imports belong here.
- `staff-paper`: Backend implementation for Paper and Folia servers. Contains command registration, event listeners, chest GUIs, Bedrock forms, and screenshare controls.
- `staff-velocity`: Proxy implementation for Velocity networks. Handles network-wide punishment checks on player login and chat events.

## Build and test workflows

Always verify changes using Gradle.

```powershell
cmd.exe /c "set ""JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.1"" && gradlew.bat test"
cmd.exe /c "set ""JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.1"" && gradlew.bat build"
```

The build produces shaded artifacts in `staff-paper/build/libs/` and `staff-velocity/build/libs/`.

## Threading guidelines

1. Use `SchedulerUtils.runEntity` for operations affecting a player entity.
2. Use `SchedulerUtils.runGlobal` for console commands and server broadcasts.
3. Use `SchedulerUtils.runAsync` or `CompletableFuture` for database queries and HTTP requests.
4. Never call synchronous teleport methods on Folia. Always call `player.teleportAsync(location)`.
5. Never read foreign player inventory state directly across Folia regional threads. Read the target snapshot on the target scheduler, then apply it to the viewer on the viewer scheduler.

## Database rules

1. All database queries run asynchronously via `ForkJoinPool.commonPool()`.
2. Wrap batch inserts in single SQL transactions.
3. Use `DateTimeFormatter` instead of `SimpleDateFormat` to maintain thread safety.
4. When mapping database results to chat lines, preserve database order using indexed arrays before sending output.
