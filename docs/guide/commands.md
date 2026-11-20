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
| `/bans` | Views recent server bans (GUI / Geyser Form / Chat). | `/bans [page] [--chat]` | `staff.bans` |
| `/bansleaderboard` | Views staff leaderboard by bans issued. | `/bansleaderboard [--chat]` | `staff.bansleaderboard` |
| `/kick` | Kicks an online player from the server/proxy. | `/kick <player> [reason]` | `staff.kick` |
| `/unwarn` | Removes latest or specific warning profile. | `/unwarn <player> [id]` | `staff.unwarn` |
| `/checkban` | Checks active ban / IP-ban status for player or IP. | `/checkban <player/IP>` | `staff.checkban` |
| `/staff reload` | Reloads configurations and messages live. | `/staff reload` | `staff.staff.reload` |

---

## Paper-Only Commands

These commands are available **only** on Paper/Purpur/Folia servers. They require server-side features (inventories, gamemode, spectator mode) that are not available on a proxy.

| Command | Description | Default Usage | Permission Node |
| :--- | :--- | :--- | :--- |
| `/vanish` | Toggles complete invisibility. | `/vanish` | `staff.vanish` |
| `/invsee` | Inspects player inventories live with hotbar, armor removal, and off-hand management. | `/invsee <player>` | `staff.invsee` |
| `/freeze` | Freezes a player for screensharing. | `/freeze <player> [reason] [--chat\|--title\|--actionbar\|--all]` | `staff.freeze` |
| `/unfreeze` | Unfreezes a frozen player. | `/unfreeze <player>` | `staff.unfreeze` |
| `/monitor` | Smoothly spectates and follows players. | `/monitor <player/leave>` | `staff.monitor` |
| `/fly` | Toggles flight mode for players. | `/fly [player]` | `staff.fly` |
| `/gmc` | Sets creative gamemode shortcut. | `/gmc [player]` | `staff.gmc` |
| `/gms` | Sets survival gamemode shortcut. | `/gms [player]` | `staff.gms` |
| `/gmsp` | Sets spectator gamemode shortcut. | `/gmsp [player]` | `staff.gmsp` |
| `/gma` | Sets adventure gamemode shortcut. | `/gma [player]` | `staff.gma` |

---

## Special Wildcard & Bypass Nodes

* **`staff.admin`**: Grants access to all administrative capabilities and commands of `Staff+` by default. On Paper, server operators (`isOp()`) automatically receive full access to all commands without needing explicit configuration.
* **Velocity Proxy Permissions**: Since Velocity does not have an `/op` command, proxy permissions must be assigned via a proxy permissions plugin such as LuckPerms on Velocity (`/lpv user <username> permission set staff.admin true`).
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
| `/freeze` | `/ss`, `/screenshare` |
| `/fly` | `/flight` |
| `/history` | `/punishhistory`, `/historylog` |
| `/staffrollback` | `/rollbackstaff`, `/rollback` |
| `/staffallow` | `/allowip`, `/allow` |
| `/staffimport` | `/migrate`, `/staffmigrate` |
| `/bans` | `/banhistory`, `/banlist` |
| `/bansleaderboard` | `/banleaderboard`, `/staffleaderboard` |
| `/checkban` | `/bancheck` |
| `/staff` | `/staffplus`, `/staff+` |

---

## Interactive GUIs & Geyser Forms

* **Bedrock Edition (Geyser / Floodgate)**:
  * When executed by Bedrock players, `/bans` and `/bansleaderboard` automatically open native **Geyser Forms** (`SimpleForm`) with interactive buttons, detail modals, and back navigation.
* **Java Edition**:
  * When executed by Java players on Paper, `/bans` and `/bansleaderboard` open 54-slot chest inventories featuring player heads, active status badges, podium ranking layouts, and page buttons.
* **Console / Chat Fallback**:
  * Running with `--chat` (or `-c`), or executing from Console / Velocity, outputs paginated interactive chat text.

---

## Live Inventory Inspector (/invsee)

`/invsee <player>` provides real-time bidirectional synchronization for inspecting and modifying online player inventories:

* **Interface Layout**:
  * **Rows 0–2 (Slots 0–26)**: Target main inventory storage (slots 9–35).
  * **Row 3 (Slots 27–35)**: Target hotbar (slots 0–8), placed directly beneath storage to mirror standard inventory ordering.
  * **Row 4 (Slots 36–44)**: Section separator with visual divider and the **Strip All Armor** button in slot 44 (shears icon).
  * **Row 5 (Slots 45–53)**: Equipped gear (Helmet 45, Chestplate 46, Leggings 47, Boots 48, Off-Hand 49), Ender Chest shortcut (50), Potion effects (51), Health & Food stats (52), and Close (53).
* **Armor Manipulation**:
  * Click equipped armor to take it to the cursor (left/right-click) or shift-click to move it directly into your inventory.
  * Equip new gear by clicking empty placeholders (PDC-tagged `staff:invsee_placeholder`) or swap existing gear directly.
* **Synchronization & Safety**:
  * Requires the target player to be online; shows a clear notice if the target is offline.
  * Captures target item drops, pickups, inventory clicks, and container closes in real time.
  * Multi-viewer sync updates all inspecting admins when changes occur.
  * Protects items on staff cursors from being overwritten by concurrent target updates.
  * Automatically closes sessions on target death to eliminate item duplication exploits.
