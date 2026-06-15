# Sequoia

[![Release](https://img.shields.io/github/v/release/SequoiaWynncraft/Sequoia-mod?display_name=tag&style=flat-square)](https://github.com/SequoiaWynncraft/Sequoia-mod/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-3C8527?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
[![License: MIT](https://img.shields.io/badge/License-MIT-2ea44f?style=flat-square)](LICENSE.txt)

**[Sequoia](https://modrinth.com/project/sequoia)** is a client-side Fabric mod for **[Sequoia](https://discord.gg/seq)** guild members on Wynncraft.

It provides the in-game client for Sequoia's guild systems: backend authentication, Discord bridge integration, party finder tools, raid tracking, and a small set of guild-specific utilities. The goal is straightforward: put the guild workflows members actually use into the game client instead of splitting them across chat, Discord, and manual commands.

This is not a general-purpose Wynncraft mod. Most online features require a linked Sequoia Discord/Wynn account, and several of them only make sense if you are already part of the guild's Discord and in-game processes.

If you are a Sequoia or allied guild member, the expected setup is simple: link through Discord, install the mod, connect to the backend, and use the guild integrations from there.

## Features

- Automatic backend authentication
- Automatic connection to Sequoia services
- In-game Discord chat bridge
- Party finder commands and UI
- Raid tracking and announcements
- Guild-specific settings and status screens

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.2+1.21.11`
- Java `21`
- Wynntils (Optional)

## First-time setup

1. **Join Sequoia or an allied guild** - party finder access is available to Sequoia and allied guild members.
2. **Install the mod** using the steps below.
3. **Connect** - the mod auto-connects on startup if enabled, or by using a button in the Connection section.
4. **Link if prompted** - if the backend reports no linked account, run `/link` in Discord and reconnect.
5. **Check status** - run `/seq status` to make sure you're connected.
6. **Configure** - press `O` and open settings to toggle Discord chat, raid announcements, and related behavior.

## Common commands

- `/seq`: open the main Sequoia screen
- `/seq p`: open the Sequoia party finder UI
- `/seq connect`: connect to the backend
- `/seq status`: show connection state and token status
- `/seq logout`: clear the saved token
- `/seq ignore <IGN>`: hide incoming Discord bridge messages from a Minecraft username
- `/seq unignore <IGN>`: show incoming Discord bridge messages from that username again

<details>
<summary>Full command list</summary>

- `/seq`
- `/seq connect`
- `/seq disconnect`
- `/seq connected`
- `/seq status`
- `/seq logout`
- `/seq ignore <IGN>`
- `/seq unignore <IGN>`
- `/seq party`
- `/seq p`
- `/seq party list`
- `/seq party status`
- `/seq party create <activities>`
- `/seq party update <activities>`
- `/seq party join <listingId>`
- `/seq party join <listingId> token <inviteToken>`
- `/seq party join <listingId> <role>`
- `/seq party join <listingId> <role> token <inviteToken>`
- `/seq party deny <listingId>`
- `/seq party leave`
- `/seq party invite <username>`
- `/seq party reserve <count>`
- `/seq party open`
- `/seq party close`
- `/seq party disband`
- `/seq party role <role>`
- `/seq party kick <username>`
- `/seq party promote <username>`
- `/seq party game create`
- `/seq party game invite <username>`
- `/seq party game invite-all`

</details>

## Settings

The settings screen has toggles for:

- Auto connect to the Sequoia backend
- Discord chat display
- Raid auto-announce
- Update checks on startup

## Installation

1. Install Fabric for Minecraft `1.21.11`.
2. Put the Sequoia mod jar in your Minecraft `mods` folder.
3. Install Fabric API.
4. Install [Wynntils](https://wynntils.com) for improved class detection.
5. Start the game and press `O`, or run `/seq`.

## License

MIT: `LICENSE.txt`.
