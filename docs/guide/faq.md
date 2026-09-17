# Frequently Asked Questions (FAQ)

This FAQ answers common operational and architectural questions about Staff+'s capabilities, including the interactive GUI, Folia region safety, database synchronization, and LuckPerms-independent hierarchy protection.

---

## 🛡️ Hierarchy Protection

### Q: How does the Hierarchy Protection work? Does it require hooking into LuckPerms?
**No. Staff+ does not require any direct API hooks to LuckPerms.**

Instead, it reads permission nodes natively via Spigot/Paper's standard `getEffectivePermissions()` API.
* **How to assign weight**: Assign staff members the permission node `staff.hierarchy.weight.<number>` (e.g. `staff.hierarchy.weight.50` for moderators or `staff.hierarchy.weight.100` for administrators).
* **Authority checks**: When a punishment is placed, the weight of the punisher is recorded. If another staff member attempts to lift, override, or clear that punishment, Staff+ checks if their weight is equal to or greater than the original punisher.
* **Decoupled design**: Because it relies on standard Bukkit permission checks, this system works out-of-the-box with **any** permissions plugin (LuckPerms, UltraPermissions, GroupManager, etc.).

---

## 📊 Staff Info GUI

### Q: What is `/staff info <player>`?
It is a dynamic, interactive chest GUI that presents comprehensive profile, connection, and moderation statistics for a specific player. This allows staff members to inspect details in busy lobbies without chat flood.

### Q: Does `/staff info` work for offline players?
**Yes.** When a player is offline, Staff+ queries their last known database record to display:
* Last known IP address
* Hierarchy weight
* Total active warnings
* Alt accounts registered under their IP
* Whitelist IP-ban exemption status

Actions that require the player to be online (such as **Teleport**, **Kick**, and **Inspect Inventory**) are visually marked as disabled (using red stained glass panes) and clicking them is blocked. Moderation commands (ban, mute, warn, allow, history logs) remain fully active.

---

## 🧵 Folia & Regional Safety

### Q: What makes Staff+ fully Folia-compatible?
Traditional plugins fail under Folia due to region-threading constraints. Staff+ enforces complete region-safety:
1. **Asynchronous Database & Webhooks**: All database and Discord webhook transactions are run asynchronously via `ForkJoinPool.commonPool()`, preventing any tick lag.
2. **Asynchronous Teleports**: All teleports (such as spectator camera follows) use modern non-blocking `Entity#teleportAsync()` futures rather than standard synchronous teleports.
3. **Recipient-Threaded Packets**: Player visibility toggles (hide/show packet loops in Vanish) are run on each recipient's specific entity scheduler to prevent cross-region threading exceptions.
4. **Isolating block-state reads**: Traditional `/invsee` plugins query `.getHolder()` on block inventories in other regions, which triggers Folia crashes. Staff+ uses a thread-safe `InvseeSession` registry to bypass this entirely.

---

## 🌐 Network Sync & Database

### Q: How do we sync mutes and bans across our server network?
To enable network-wide moderation, configure **both** the Velocity proxy plugin and the Paper backend plugins to point to the same database (MySQL/MariaDB).
* **Mutes**: Actively muted players are blocked from typing in chat network-wide at the Velocity proxy level.
* **Bans**: Banned players are rejected at the Velocity proxy pre-login event, saving backend resources.
* **IP-Ban Exemptions**: Whitelisted players bypass active IP bans at the gateway without removing the IP ban itself.

### Q: Why did the database fail to connect or say it "can't find it"?
Starting in v1.2.2:
1. **Bundled Drivers**: Staff+ includes relocated MySQL (`com.mysql.cj.jdbc.Driver`) and MariaDB (`org.mariadb.jdbc.Driver`) JDBC drivers directly inside both the Paper and Velocity JARs. You do not need to install external driver JARs.
2. **Auto-creation**: Connection pools configure `createDatabaseIfNotExist=true` and `allowPublicKeyRetrieval=true` automatically. If the target database schema does not exist on your MySQL server, it will be automatically provisioned.
3. **Graceful SQLite Fallback**: If remote database credentials are invalid or your SQL server is offline, Staff+ falls back to a local SQLite database (`database.db`) instead of crashing or disabling the plugin, ensuring your server remains protected.
4. **Dynamic Reconnect**: Running `/staff reload` automatically re-establishes the database connection pool without requiring a full server reboot.

---

## 🔑 Permissions & Velocity Proxy

### Q: Why do I get "no permission" for `/bans`, `/banhistory`, or `/staff reload` even if I have OP?
* **On Paper/Folia backend servers**: All Staff+ commands default to Server Operators (`default: op`). Additionally, assigning `staff.admin` grants immediate full access to all commands and bypasses all restrictions.
* **On Velocity proxy servers**: The Velocity proxy has no native `/op` command. To use proxy commands (`/ban`, `/mute`, `/bans`, `/banhistory`, `/staff reload`), you must assign permissions via a proxy permission plugin such as LuckPerms Velocity:
  ```bash
  /lpv user <your-username> permission set staff.admin true
  ```
  This single permission grants access to all proxy moderation actions and administrative commands.

---

## 📱 Bedrock & Geyser Compatibility

### Q: Do I need GeyserMC on both the proxy and the backend servers?
Staff+ features multi-classloader Geyser detection. If Geyser is present on the server where the command is executed (whether Velocity or Paper), Bedrock players receive an interactive GUI form. If Geyser is only on the proxy and you run backend commands (or vice versa), Staff+ safely falls back to standard text/chat pagination with zero console errors.
