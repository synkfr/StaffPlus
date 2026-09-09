# Configuration Reference

`Staff+` utilizes **Okaeri Config** to generate, validate, and dynamically update configurations. All configurations feature full hex color and gradient support via **Adventure MiniMessage** (e.g., `<gradient:#FF5F6D:#FFC371>Gradients</gradient>` or `<color:#00E262>Hex Colors</color>`).

Both the **Paper** and **Velocity** editions use the **same configuration format**. This makes it easy to maintain consistent settings across your network.

---

## Plugin Configuration (`config.yml`)

The primary configuration file manages database setups, vanish settings, warning escalation, and Discord webhooks:

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
discord-webhook-username: "Staff+ Moderation"
discord-webhook-avatar-url: "https://i.imgur.com/8Qp49X0.png"

# Hex color codes for webhook embeds (without the #)
discord-webhook-color-ban: "FF5555"
discord-webhook-color-mute: "FFAA00"
discord-webhook-color-warn: "FFFF55"

# Update Checker settings (Modrinth)
update-checker-enabled: true
update-checker-notify-admins: true

# Staff Ban Leaderboard settings
bans-leaderboard-enabled: true
```

### Config Mappings Explained

#### Database Settings
* **`storage-type`**: Choose between local `sqlite` (zero configuration) or a centralized remote `mysql` cluster. Use `mysql` for network-wide punishment sharing between Paper and Velocity.
* **`mysql-*`**: Connection settings for your MySQL database. Powered by an isolated **HikariCP** connection pool for maximum performance and asynchronous safety.

::: tip Network Mode
To share punishments across Paper + Velocity, set `storage-type: "mysql"` and use identical `mysql-*` credentials in both `config.yml` files.
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

---

## Message Configuration (`messages.yml`)

This file allows you to customize all system messages with rich color styling. The format is identical on both Paper and Velocity.

```yaml
# Prefix for all plugin messages
prefix: "<color:#A0A0A0>[<color:#00E262>Staff+<color:#A0A0A0>] "

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

# Vanish (Paper only — these keys are unused on Velocity)
vanish-enabled: "<color:#00E262>You are now vanished."
vanish-disabled: "<color:#00E262>You are no longer vanished."
vanish-action-bar: "<color:#00E262>★ YOU ARE VANISHED ★"
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

---

## Config File Locations

| Platform | Config Directory |
| :--- | :--- |
| **Paper (Paper/Purpur/Folia)** | `plugins/Staff/config.yml` and `plugins/Staff/messages.yml` |
| **Velocity** | `plugins/staffplus/config.yml` and `plugins/staffplus/messages.yml` |
