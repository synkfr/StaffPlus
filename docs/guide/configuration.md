# Configuration Reference

`Staff+` utilizes **Okaeri Config** to generate, validate, and dynamically update configurations. All configurations feature full hex color and gradient support via **Adventure MiniMessage** (e.g., `<gradient:#FF5F6D:#FFC371>Gradients</gradient>` or `<color:#00E262>Hex Colors</color>`).

Both the **Paper** and **Velocity** editions use the **same configuration format**. This makes it easy to maintain consistent settings across your network.

---

## Plugin Configuration (`config.yml`)

The primary configuration file manages database setups, vanish settings, warning escalation, and Discord webhooks:

```yaml
# Database Storage Type
# Options: sqlite, mysql, or mariadb
# - sqlite: Stores data locally in plugins/StaffPlus/database.db. Zero setup required.
# - mysql or mariadb: Connects to a remote database to share bans and mutes across multiple servers.
storage-type: "sqlite"

# MySQL / MariaDB Connection Settings
# These settings are only used when storage-type is set to 'mysql' or 'mariadb'.
# Staff+ will automatically create tables on first launch.
# If connection fails, Staff+ automatically falls back to local SQLite so your server never crashes.
mysql-host: "localhost"
mysql-port: 3306
mysql-database: "minecraft"
mysql-username: "root"
mysql-password: "password"
# Maximum number of concurrent database connections in the connection pool (default: 10)
mysql-pool-size: 10
# Enable SSL encrypted connections to your database server
mysql-use-ssl: false

# Vanish Settings (Paper / Purpur / Folia only)
# Controls stealth features for staff when using /vanish.
# Allows vanished staff to silently open chests and barrels without animations or sounds
vanish-silent-container-clicks: true
# Prevents vanished staff from triggering wooden, stone, or iron pressure plates
vanish-ignore-pressure-plates: true
# Prevents hostile mobs (like zombies and skeletons) from targeting or attacking vanished staff
vanish-disable-mob-targeting: true
# Prevents vanished staff from picking up items on the ground so they stay hidden
vanish-disable-item-pickup: true

# Warning Escalation Ladder
# Automatically applies progressive punishments when a player accumulates active warnings.
# Set to false to disable automated warning escalation.
warning-ladder-enabled: true

# Warning Ladder Actions
# Format: <warning_count>: "<command_to_run>"
# The {player} placeholder is replaced with the target player's name.
# Commands are executed safely via console when the player reaches that warning count.
warning-ladder-actions:
  1: "tempmute {player} 1h [Warning Ladder] First warning"
  2: "tempmute {player} 12h [Warning Ladder] Second warning"
  3: "tempban {player} 3d [Warning Ladder] Reached 3 warnings"
  4: "ban {player} [Warning Ladder] Reached 4 warnings"

# Discord Webhook Logging
# Sends real-time embed logs to your Discord channel whenever staff issues a ban, mute, warn, or kick.
# All webhook requests run asynchronously in the background with zero server lag.
discord-webhook-enabled: false
# The Webhook URL from your Discord channel settings (keep this private!)
discord-webhook-url: ""
# Display name shown as the author of the Discord webhook message
discord-webhook-username: "Staff+ Moderation"
# Avatar image URL shown on the Discord webhook profile
discord-webhook-avatar-url: "https://i.imgur.com/8Qp49X0.png"

# Hex color codes for Discord embed sidebar (without #, e.g. FF5555 for red)
discord-webhook-color-ban: "FF5555"
discord-webhook-color-mute: "FFAA00"
discord-webhook-color-warn: "FFFF55"
discord-webhook-color-kick: "FF8800"

# Update Checker
# Checks Modrinth asynchronously on startup and notifies administrators when a new update is available.
update-checker-enabled: true
# Whether to send an in-game notification to server admins and OPs when they join
update-checker-notify-admins: true

# Staff Ban Leaderboard
# Enables or disables the /bansleaderboard command for tracking staff moderation activity.
bans-leaderboard-enabled: true

# Velocity Proxy Settings (Velocity proxy edition only)
# These options configure how Staff+ behaves on a Velocity network gateway.
# Register moderation commands (/ban, /mute, etc.) directly on the Velocity proxy
velocity-proxy-commands: true
# If a staff member runs a command on the proxy but lacks proxy-level permissions,
# automatically forwards the command to their backend Paper server so LuckPerms and OP work seamlessly.
velocity-forward-to-backend: true
# When a player is kicked or banned on a backend server, disconnect them from the proxy completely
# instead of sending them to a fallback or limbo server. Normal restarts still send players to lobby.
velocity-disconnect-on-kick: true

# Freeze & Screenshare Settings (Paper / Purpur / Folia only)
# Allows staff to immobilize suspected rulebreakers for screensharing and investigation.
# Enable or disable the /freeze and /unfreeze system
freeze-enabled: true
# Message delivery mode for frozen players:
# - CHAT: Sends a clean chat notification box with voice chat links.
# - TITLE: Shows on-screen title and subtitle in the center of their screen.
# - ACTION_BAR: Shows a continuous warning above their hotbar.
# - ALL: Shows chat, title, and action bar all at once.
freeze-message-mode: "chat"
# Your community's TeamSpeak 3 server address (replaces {teamspeak} in messages)
teamspeak-server: "ts.example.com"
# Your community's Discord invite link (replaces {discord} in messages)
discord-invite: "discord.gg/example"
# How often (in seconds) to repeat the freeze message to the frozen player
freeze-reminder-interval-seconds: 5
# Whether to automatically run a punishment command if a player disconnects while frozen
freeze-logout-action-enabled: true
# Console command to run when a frozen player leaves the server (replaces {player}, {staff}, {reason})
freeze-logout-action: "ban {player} 30d Disconnecting while frozen [Auto-Ban]"
# List of commands that frozen players are allowed to use (e.g. messaging staff)
freeze-allowed-commands:
  - msg
  - r
  - reply
  - tell
```

### Config Mappings Explained

#### Database Settings
* **`storage-type`**: Choose between local `sqlite` (zero configuration), remote `mysql`, or `mariadb`. Official shaded drivers for MySQL Connector/J and MariaDB Java Client are bundled directly into both Paper and Velocity editions.
* **`mysql-*`**: Connection parameters for your remote database. Powered by an isolated **HikariCP** pool with automated table generation and `createDatabaseIfNotExist=true` support.
* **Resilient Fallback**: If remote MySQL/MariaDB fails to connect, Staff+ logs clear diagnostic details and automatically falls back to local SQLite (`database.db`) so the plugin never disables itself and commands remain operational.
* **Live Reload**: Updating database credentials and running `/staff reload` dynamically reconnects the database pool without server restarts.

::: tip Network Mode
To share punishments across Paper + Velocity, set `storage-type: "mysql"` (or `"mariadb"`) and use identical credentials in both `config.yml` files.
:::

#### Vanish Settings (Paper Only)
These settings are **only used on the Paper edition**. They are safely ignored if present in the Velocity config.

* **`vanish-silent-container-clicks`**: Vanished staff can open chests, barrels, and furnaces silently. Container animations are suppressed.
* **`vanish-ignore-pressure-plates`**: Staff will not trigger physical stone, wood, gold, or iron plate interactions.
* **`vanish-disable-mob-targeting`**: Prevents hostile mobs from targeting vanished staff.
* **`vanish-disable-item-pickup`**: Blocks vanished staff from picking up floor items to prevent visual reveals.

#### Warning Escalation
* **`warning-ladder-enabled`**: Toggles warning escalation. If active, punishments scale automatically with player warning thresholds.
* **`warning-ladder-actions`**: Custom commands mapping. When active warning count matches the key, the mapped command is executed safely. The `{player}` placeholder is replaced with the target's name.

#### Discord Webhooks
* **`discord-webhook-enabled`**: Enable or disable non-blocking logs to your Discord webhook channel.
* **`discord-webhook-url`**: Your channel's secure Discord Webhook link.
* **`discord-webhook-username` / `avatar-url`**: Customized profiles displayed in Discord embeds.
* **`discord-webhook-color-*`**: Custom embed border hex colors for ban, mute, and warning events.

#### Update Checker (Modrinth)
* **`update-checker-enabled`**: Toggle automatic asynchronous version checks against Modrinth releases.
* **`update-checker-notify-admins`**: If enabled, notifies server admins and OPs on join/login when a newer version is published.

#### Staff Ban Leaderboard
* **`bans-leaderboard-enabled`**: Toggle access to the `/bansleaderboard` command for staff activity metrics.

#### Velocity Proxy Settings (Velocity only)
* **`velocity-proxy-commands`**: Toggle whether Velocity registers punishment and moderation commands directly on the proxy (default: `true`). Set to `false` if you want backend Paper servers to handle all commands exclusively while Velocity focuses purely on gateway enforcement.
* **`velocity-forward-to-backend`**: When enabled (default: `true`), if a player runs a command on Velocity without proxy permissions (e.g. staff permissions and OPs are managed on Paper backend servers), Velocity transparently forwards the command to their connected Paper server via `spoofChatInput` instead of blocking them with "no permission".
* **`velocity-disconnect-on-kick`**: When enabled (default: `true`), moderation kicks and active ban kicks disconnect players from the proxy network entirely rather than routing them to fallback/limbo servers.

#### Freeze & Screenshare Settings (Paper Only)
* **`freeze-enabled`**: Enable or disable the screenshare `/freeze` and `/unfreeze` system.
* **`freeze-message-mode`**: Select how screenshare instructions are delivered to frozen players. Options: `CHAT` (clean chat notification banner), `TITLE` (on-screen title and subtitle), `ACTION_BAR` (persistent action bar text), or `ALL` (shows all three). Can also be overridden per-freeze using `--chat`, `--title`, `--actionbar`, or `--all` flags.
* **`teamspeak-server`**: Your community's TeamSpeak 3 address (injected into `{teamspeak}` placeholders).
* **`discord-invite`**: Your community's Discord invite link (injected into `{discord}` placeholders).
* **`freeze-reminder-interval-seconds`**: Interval in seconds between periodic title, chat, and action bar screenshare reminders sent to frozen players.
* **`freeze-logout-action-enabled`**: Whether to automatically execute an action if a frozen player disconnects while under screenshare investigation.
* **`freeze-logout-action`**: Command executed via console if a player leaves while frozen (supports `{player}` placeholder).
* **`freeze-allowed-commands`**: Whitelisted commands that frozen players are permitted to run (e.g., messaging commands such as `/msg`, `/r`).

---

## Message Configuration (`messages.yml`)

This file allows you to customize all system messages with rich color styling. The format is identical on both Paper and Velocity.

```yaml
# Prefix for all plugin chat messages
prefix: "<color:#A0A0A0>[<color:#00E262>Staff+<color:#A0A0A0>] "

# General Plugin Messages (Placeholders: {player}, {sender}, {staff})
no-permission: "<color:#E20000>You do not have permission to execute this command."
player-only: "<color:#E20000>This command can only be executed by a player."
cannot-overwrite-punishment: "<color:#E20000>You cannot override a punishment placed by a higher-ranking staff member ({staff})."
player-allowed: "<color:#00E262>You have allowed {player} to bypass IP bans."
player-allowed-broadcast: "<color:#00E262>{player} has been exempted from IP bans by {sender}."
player-unallowed: "<color:#00E262>You have removed IP ban exemption for {player}."
player-unallowed-broadcast: "<color:#00E262>{player} is no longer exempted from IP bans."
player-not-found: "<color:#E20000>Player '{player}' has not been found or registered."
invalid-duration: "<color:#E20000>Invalid duration format! Use e.g. 1d, 12h, 30m or perm."

# Mute Messages (Placeholders: {player}, {sender}, {time}, {reason})
player-muted: "<color:#00E262>You have muted {player} for {time}. Reason: {reason}"
player-muted-broadcast: "<color:#E2B700>{player} has been muted by {sender} for {time}. Reason: {reason}"
you-are-muted: "<color:#E20000>You are muted! Remaining time: {time}. Reason: {reason}"

# Ban Messages (Placeholders: {player}, {sender}, {time}, {reason})
player-banned: "<color:#00E262>You have banned {player} for {time}. Reason: {reason}"
player-banned-broadcast: "<color:#E20000>{player} has been banned by {sender} for {time}. Reason: {reason}"
ban-kick-message: "<color:#E20000>You have been banned from the server!\n\nReason: {reason}\nExpiry: {time}"

# Vanish Messages (Paper only — these keys are unused on Velocity)
vanish-enabled: "<color:#00E262>You are now vanished."
vanish-disabled: "<color:#00E262>You are no longer vanished."
vanish-action-bar: "<color:#00E262>★ YOU ARE VANISHED ★"

# Freeze & Screenshare Messages (Paper only — Placeholders: {player}, {staff}, {reason}, {teamspeak}, {discord})
player-frozen: "<color:#00E262>You have frozen <color:#FFFFFF>{player}</color>."
player-unfrozen: "<color:#00E262>You have unfrozen <color:#FFFFFF>{player}</color>."
frozen-chat-reminder: "<color:#E20000><b>------- [ YOU ARE FROZEN ] -------</b></color>\n<color:#FFFFFF>You have been frozen by a staff member!</color>\n<color:#A0A0A0>Join our TeamSpeak: <color:#00E262>{teamspeak}</color> or Discord: <color:#00E262>{discord}</color></color>\n<color:#E20000><b>DO NOT LOG OUT</b></color> <color:#A0A0A0>or you will be automatically banned!</color>\n<color:#E20000><b>----------------------------</b></color>"
frozen-title: "<color:#E20000><b>YOU ARE FROZEN</b></color>"
frozen-subtitle: "<color:#A0A0A0>Join TeamSpeak: <color:#00E262>{teamspeak}</color>"
frozen-action-bar: "<color:#E20000>⚠ YOU ARE FROZEN - JOIN {teamspeak} ⚠</color>"
```

### Available Placeholders

| Placeholder | Description | Used In |
| :--- | :--- | :--- |
| `{player}` | Target player name | All punishment messages |
| `{sender}` | Staff member name | Broadcast messages |
| `{time}` | Duration / expiry string | Bans, mutes, kick messages |
| `{reason}` | Punishment reason | All punishment messages |
| `{staff}` | Senior staff who placed original punishment | Hierarchy overwrite denial |
| `{ip}` | IP address | IP ban messages |
| `{date}` | Timestamp date string | Warning list items |
| `{teamspeak}` | Configured TeamSpeak server address | Freeze messages, titles, and reminders |
| `{discord}` | Configured Discord invite link | Freeze messages and reminders |

---

## Config File Locations

| Platform | Config Directory |
| :--- | :--- |
| **Paper (Paper/Purpur/Folia)** | `plugins/Staff/config.yml` and `plugins/Staff/messages.yml` |
| **Velocity** | `plugins/staffplus/config.yml` and `plugins/staffplus/messages.yml` |
