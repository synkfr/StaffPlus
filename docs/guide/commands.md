# Commands & Permissions

`Staff+` provides a complete command and permission system mapping administrative actions and utilities. Commands are split into **network-wide** (available on both Paper and Velocity) and **Paper-only** (server-side features).

---

## Network-Wide Commands (Paper & Velocity)

These commands are available on **both** Paper/Purpur/Folia servers and Velocity proxies. When using a shared MySQL database, punishments issued on either platform are enforced network-wide.

| Command | Description | Default Usage | Permission Node |
| :--- | :--- | :--- | :--- |
| `/ban` | Permanently bans a player. | `/ban <player> [reason]` | `staff.ban` |
| `/tempban` | Temporarily bans a player. | `/tempban <player> <duration> [reason]` | `staff.tempban` |
| `/unban` | Revokes an active ban profile. | `/unban <player>` | `staff.unban` |
| `/ip-ban` | Permanently IP-bans a target. | `/ip-ban <player> [reason]` | `staff.ipban` |
| `/tempip-ban` | Temporarily IP-bans a player/IP. | `/tempip-ban <player> <duration> [reason]` | `staff.tempipban` |
| `/unip-ban` | Revokes an active IP ban profile. | `/unip-ban <player/IP>` | `staff.unipban` |
| `/mute` | Permanently mutes a player. | `/mute <player> [reason]` | `staff.mute` |
| `/tempmute` | Temporarily mutes a player. | `/tempmute <player> <duration> [reason]` | `staff.tempmute` |
| `/unmute` | Restores a player's chat access. | `/unmute <player>` | `staff.unmute` |
| `/warn` | Issues a warning profile to a player. | `/warn <player> [reason]` | `staff.warn` |
| `/warns` | View or clear warning logs. | `/warns <player> [list/clear]` | `staff.warns` |
| `/history` | Views complete historical punishment logs. | `/history <player>` | `staff.history` |
| `/staffhistory` | Audits punishments issued by a staff member. | `/staffhistory <staff>` | `staff.staffhistory` |
| `/staffrollback` | Revokes all active punishments placed by a staff member. | `/staffrollback <staff> [confirm]` | `staff.staffrollback` |
| `/staffallow` | Exempts a player/UUID from active IP bans. | `/staffallow <player> [remove]` | `staff.staffallow` |
| `/staffimport` | Imports punishments from other moderation plugins. | `/staffimport <source> [params...]` | `staff.import` |
| `/bans` | Views paginated recent server bans. | `/bans [page]` | `staff.bans` |
| `/bansleaderboard` | Views staff leaderboard by bans issued. | `/bansleaderboard` | `staff.bansleaderboard` |

---

## Paper-Only Commands

These commands are available **only** on Paper/Purpur/Folia servers. They require server-side features (inventories, gamemode, spectator mode) that are not available on a proxy.

| Command | Description | Default Usage | Permission Node |
| :--- | :--- | :--- | :--- |
| `/vanish` | Toggles complete invisibility. | `/vanish` | `staff.vanish` |
| `/invsee` | Live inspects player inventories. | `/invsee <player>` | `staff.invsee` |
| `/monitor` | Smoothly spectates and follows players. | `/monitor <player/leave>` | `staff.monitor` |
| `/fly` | Toggles flight mode for players. | `/fly [player]` | `staff.fly` |
| `/gmc` | Sets creative gamemode shortcut. | `/gmc [player]` | `staff.gmc` |
| `/gms` | Sets survival gamemode shortcut. | `/gms [player]` | `staff.gms` |
| `/gmsp` | Sets spectator gamemode shortcut. | `/gmsp [player]` | `staff.gmsp` |
| `/gma` | Sets adventure gamemode shortcut. | `/gma [player]` | `staff.gma` |

---

## Special Wildcard & Bypass Nodes

* **`staff.admin`**: Grants access to all administrative capabilities and commands of `Staff+` by default. Typically assigned to OPs and high-tier Administrators.
* **`staff.vanish.see`**: Allows staff to see other vanished players in the game, in tab lists, and list them in command autocompletions. *(Paper only)*

---

## Overwrite Hierarchy Weights

`Staff+` implements a robust Administrative Overwrite Hierarchy. Lower-ranking staff members cannot bypass, overwrite, or remove punishments issued by senior administrators.

Weights are assigned using custom permission nodes:
* **`staff.hierarchy.weight.<number>`**: Defines the hierarchy weight of the player (e.g. `staff.hierarchy.weight.100` for Owners, `staff.hierarchy.weight.50` for Helpers).
* **Console Authority**: The Server Console holds infinite authority (`Integer.MAX_VALUE`). Console-issued bans, mutes, or warnings can never be overwritten in-game by any player, regardless of rank.
* **Database Cache**: Dynamic weights of staff members are automatically resolved and cached in the database on join, enabling offline hierarchy protection for senior staff.

::: tip Velocity Hierarchy
On Velocity, hierarchy weights are also enforced through the same database-backed weight system. Permission nodes work with Velocity's permission providers (e.g., LuckPerms Velocity).
:::

---

## Command Aliases

Many commands have shorthand aliases for convenience:

| Command | Aliases |
| :--- | :--- |
| `/mute` | `/silence` |
| `/ip-ban` | `/ipban`, `/banip` |
| `/tempip-ban` | `/tempipban`, `/tempbanip` |
| `/unip-ban` | `/unipban`, `/unbanip` |
| `/vanish` | `/v`, `/vmode` |
| `/monitor` | `/spectate`, `/mon` |
| `/invsee` | `/inspect`, `/inv` |
| `/fly` | `/flight` |
| `/history` | `/punishhistory`, `/historylog` |
| `/staffrollback` | `/rollbackstaff`, `/rollback` |
| `/staffallow` | `/allowip`, `/allow` |
| `/staffimport` | `/migrate`, `/staffmigrate` |
| `/bans` | `/banhistory`, `/banlist` |
| `/bansleaderboard` | `/banleaderboard`, `/staffleaderboard` |
