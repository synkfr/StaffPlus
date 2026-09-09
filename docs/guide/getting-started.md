# Getting Started with Staff+

`Staff+` is an enterprise-grade, high-performance moderation and utility plugin built for modern Minecraft networks. It ships as **two platform-specific JARs**:

- **StaffPlus-Paper** — For Paper/Purpur/Folia 1.20–1.21+ backend servers with full Folia multi-threaded safety.
- **StaffPlus-Velocity** — For Velocity 3.3.0+ proxies enforcing bans and mutes at the network gateway.

Both editions share the same database, config format, and migration system.

---

## Requirements

### Paper Edition
* **Java Runtime**: Java 21+ (OpenJDK / Eclipse Temurin recommended).
* **Server Platform**: Paper or Folia (1.20.x, 1.21.x, or higher).

### Velocity Edition
* **Java Runtime**: Java 21+.
* **Proxy Platform**: Velocity 3.3.0+.

### Build (Developers)
* **Build System**: Gradle 8+ and JDK 21+ for compiling from source.

---

## Installation

### Paper Edition (Paper / Folia)

1. Download `StaffPlus-Paper-1.1.0.jar` from [Modrinth](https://modrinth.com/plugin/staff%2B/versions) or [GitHub Releases](https://github.com/synkfr/StaffPlus/releases).
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default configuration files.
4. Customize `plugins/Staff/config.yml` and `plugins/Staff/messages.yml`, then restart.

### Velocity Edition (Proxy)

1. Download `StaffPlus-Velocity-1.1.0.jar` from [Modrinth](https://modrinth.com/plugin/staff%2B/versions) or [GitHub Releases](https://github.com/synkfr/StaffPlus/releases).
2. Place the JAR in your Velocity proxy's `plugins/` directory.
3. Start the proxy to generate the default configuration files.
4. Customize `plugins/staffplus/config.yml` and `plugins/staffplus/messages.yml`, then restart.

### Network Mode (Shared Database)

To share punishments across your entire Paper + Velocity network, configure **both** editions to point at the same MySQL database:

```yaml
# config.yml (identical on both platforms)
storage-type: "mysql"
mysql-host: "your-db-host"
mysql-port: 3306
mysql-database: "staffplus"
mysql-username: "your-user"
mysql-password: "your-password"
mysql-pool-size: 10
mysql-use-ssl: false
```

::: tip Network Architecture
With the shared database, a ban issued from the Paper `/ban` command will immediately be enforced by the Velocity proxy on the next login attempt — and vice versa.
:::

---

## Compilation from Source

If you want to modify the source code or build the latest development snapshot, clone the repository and use Gradle:

```bash
# Clone the repository
git clone https://github.com/synkfr/StaffPlus.git
cd StaffPlus

# Compile and build both platform JARs
./gradlew clean build
```

The compiled shaded plugins will be located at:
```
staff-paper/build/libs/StaffPlus-Paper-1.1.0.jar     # Paper/Purpur/Folia servers
staff-velocity/build/libs/StaffPlus-Velocity-1.1.0.jar  # Velocity proxies
```

### Project Structure
```
StaffPlus/
├── staff-core/          # Shared platform-agnostic core library
│   └── Database, Punishment model, Configs, Migration, Utils
├── staff-paper/         # Paper/Purpur/Folia backend plugin
│   └── Commands, Listeners, Vanish, Monitor, Invsee
├── staff-velocity/       # Velocity proxy plugin
│   └── Proxy commands, Login/Chat enforcement listeners
├── build.gradle          # Root Gradle configuration
└── settings.gradle       # Multi-module declaration
```

---

## Migrating from Other Plugins

`Staff+` includes a powerful multi-source migration and import tool. You can migrate mutes, bans, warnings, and IP bans from **7 popular moderation systems** on either platform:

| Source Plugin | Data Imported | Storage Supported |
| :--- | :--- | :--- |
| **Vanilla Minecraft** | Bans, IP Bans | `banned-players.json`, `banned-ips.json` |
| **Essentials / EssentialsX** | Bans, Mutes | `plugins/Essentials/userdata/*.yml` |
| **LiteBans** | Bans, Mutes, Warnings | H2 (`.mv.db`), SQLite, MySQL |
| **AdvancedBan** | Bans, IP Bans, Mutes, Warnings | SQLite, MySQL |
| **MaxBans** | Bans, IP Bans, Mutes, Warnings | SQLite, MySQL |
| **BanManager** | Bans, Mutes, Warnings | SQLite, MySQL |
| **BungeeAdminTools (BAT)** | Bans, Mutes | SQLite, MySQL |

Run the `/staffimport` command in-game to view auto-detected local setups, or see the [Commands Guide](/guide/commands) for usage details.

::: info Auto-Detection
The importer automatically scans for local plugin config files and databases. For most setups, you can run `/staffimport <source>` with zero manual configuration.
:::

::: info Relocated Dependencies
The build system automatically relocates and packages all required libraries inside the final JAR files, ensuring they run out of the box with zero conflicts against any other plugins on your server or proxy.
:::
