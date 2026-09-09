---
layout: home

hero:
  name: "Staff+"
  text: "Modern Moderation & Administrative Utilities"
  tagline: "A powerful, cross-platform staff toolkit for Paper, Folia, and Velocity networks"
  image:
    src: https://raw.githubusercontent.com/synkfr/StaffPlus/master/docs/public/logo.png
    alt: Staff+ Logo
  actions:
    - theme: brand
      text: Getting Started
      link: /guide/getting-started
    - theme: alt
      text: Commands List
      link: /guide/commands
    - theme: alt
      text: View on GitHub
      link: https://github.com/synkfr/StaffPlus

features:
  - icon: 🌐
    title: Cross-Platform Architecture
    details: Ships as two JARs — one for Paper/Purpur/Folia servers and one for Velocity proxies. Share the same database for network-wide moderation.
  - icon: 🧵
    title: Native Folia Support
    details: Built from the ground up for Folia's multi-threaded region architecture. Every operation uses Folia-safe schedulers.
  - icon: 🛡️
    title: Full Moderation Suite
    details: Ban, mute, IP-ban, and warn rulebreakers permanently or temporarily. All punishments are enforced on both Paper and Velocity.
  - icon: 🔗
    title: Velocity Proxy Enforcement
    details: Block banned players at the proxy gateway before they reach any backend server. Network-wide mute enforcement across all servers.
  - icon: 👥
    title: Complete Vanish (Paper)
    details: Hide completely from players, tab lists, and server listings. Prevents item pickups, mob attacks, and container sounds.
  - icon: 🎒
    title: Live Inventory Inspector (Paper)
    details: View and change any player's inventory, armor, and active effects in real-time through an interactive menu.
  - icon: 👁️
    title: Smooth Spectate Follow (Paper)
    details: Watch and automatically follow players smoothly in spectator mode without visual jitter.
  - icon: ⚡
    title: Handy Staff Commands (Paper)
    details: Super fast shortcuts to toggle flight (/fly) or change your gamemode (/gmc, /gms, /gmsp, /gma).
  - icon: ⚖️
    title: Warning Escalation Ladder
    details: Automate moderation actions (mutes, bans) dynamically as players accumulate active warning thresholds.
  - icon: 👑
    title: Overwrite Hierarchy Protection
    details: Restrict junior staff from bypassing or revoking punishments placed by senior admins or the Console.
  - icon: 💬
    title: Discord Webhook Logs
    details: Native, thread-safe asynchronous logging to Discord channels using modern HttpClient integrations.
  - icon: 🔄
    title: Multi-Source Importer
    details: Zero-config auto-scanning and batch imports from Vanilla, Essentials, LiteBans, AdvancedBan, MaxBans, BanManager, and BungeeAdminTools.
  - icon: 📜
    title: Global Ban History & Leaderboard
    details: Browse active and expired server bans with interactive page buttons (/bans), or track staff moderation metrics (/bansleaderboard).
  - icon: 🔔
    title: Modrinth Version Notifier
    details: Automatic asynchronous update checking against Modrinth releases, notifying server admins with direct download links.
---
