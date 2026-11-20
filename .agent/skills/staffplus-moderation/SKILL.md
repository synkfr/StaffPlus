---
name: staffplus-moderation
description: Guidelines and workflows for punishment types, hierarchy permissions, database schema, and Folia thread boundaries in StaffPlus.
---

# StaffPlus moderation reference

Reference for punishment models, permission hierarchy, database schema, and thread boundaries.

## Architecture

StaffPlus splits functionality across three modules:
1. `staff-core`: Platform-agnostic database managers, models (`Punishment`), config holders (`PluginConfig`, `MessageConfig`), migrations, and utilities (`DiscordWebhookUtils`, `DurationUtils`).
2. `staff-paper`: Paper and Folia backend plugin with command executors, event listeners, chest GUIs, Bedrock forms, and screenshare freeze handlers.
3. `staff-velocity`: Velocity proxy plugin managing network-wide punishments, login gate checks, and chat mutes.

## Punishment types
- `BAN`: Bans target UUID from server connections.
- `IP_BAN`: Bans both IP address and associated UUIDs. Supports exemption bypass via `/staffallow <player>`.
- `MUTE`: Blocks chat dispatch.
- `WARN`: Increments warning count and triggers configurable warning ladder escalations.
- `KICK`: Disconnects an online player with Folia entity-thread execution.

## Hierarchy rules
1. Staff weight is determined by permission nodes: `staff.hierarchy.weight.<number>`.
2. Console execution has weight `Integer.MAX_VALUE`.
3. A staff member cannot override or remove a punishment placed by another staff member with higher weight.
4. If weight is equal or higher, override succeeds.

## Database tables
- `staff_players`: UUID, username, ip_address, last_seen, weight.
- `staff_punishments`: id, uuid, ip_address, punisher_uuid, type, reason, start_time, end_time, active.
- `staff_allows`: uuid, active (IP-ban exemption whitelist).
