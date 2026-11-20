---
name: staffplus-build
description: Build, compile, test, and package StaffPlus Paper and Velocity JARs using Gradle.
---

# StaffPlus build guide

Builds the StaffPlus multi-module project and packages shaded JARs for Paper and Velocity.

## Prerequisites
Java 21 or newer is required to compile and run tests.

## Build commands

Run unit tests:
```powershell
cmd.exe /c "set ""JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.1"" && gradlew.bat test"
```

Compile all modules without running tests:
```powershell
cmd.exe /c "set ""JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.1"" && gradlew.bat compileJava"
```

Build shaded release JARs:
```powershell
cmd.exe /c "set ""JAVA_HOME=C:\Program Files\Java\jdk-25.0.5.1"" && gradlew.bat build"
```

## Output artifacts
- Paper plugin: `staff-paper/build/libs/StaffPlus-Paper-<version>.jar`
- Velocity proxy plugin: `staff-velocity/build/libs/StaffPlus-Velocity-<version>.jar`
- Core library: `staff-core/build/libs/staff-core-<version>.jar`

## Verification checklist
1. Verify `shadowJar` shaded dependencies are relocated under `me.ayosynk.staff.libs.*`.
2. Confirm SLF4J is excluded from shaded archives to avoid collisions with server loggers.
3. Ensure JUnit 5 tests execute with zero failures.
