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
- Clickable world names in chat, so a called-out world is one click away
- Guild invite and removal audit relay for staff utilities
- Party finder commands and UI
- Raid tracking and announcements
- Per-player raid gambit counts parsed natively from the raid-start roster
- Sequoia achievements: your graid counts, ranked from Bronze to Mythril
- Interactive world map with gathering nodes analysis and active world events
- Guild-specific settings and status screens

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.2+1.21.11`
- Java `21`
- Wynntils (Optional)

## First-time setup

1. **Have Party Finder access** - link an account that is in Sequoia/an allied guild or can view the Party Finder
   Discord channel (including Veteran, Honoured Veteran, and Community members).
2. **Install the mod** using the steps below.
3. **Connect** - the mod auto-connects on startup if enabled, or by using a button in the Connection section.
4. **Link if prompted** - if the backend reports no linked account, run `/link` in Discord and reconnect.
5. **Check status** - run `/seq status` to make sure you're connected.
6. **Configure** - press `O` and open settings to toggle Discord chat, raid announcements, and related behavior.

Linked players need Sequoia/allied guild membership or access to the Party Finder Discord channel to view, join,
create, or manage Party Finder listings. When a non-guild session connects, the mod shows one notice and disables
Sequoia-only integrations for that session; later membership rejections stay silent.

## Common commands

- `/seq`: open the main Sequoia screen
- `/seq p`: open the Sequoia party finder UI
- `/seq map`: open the Sequoia world map
- `/seq connect`: connect to the backend
- `/seq status`: show connection state
- `/seq logout`: clear the current backend session
- `/seq request aspects`: request aspects from the guild reward queue
- `/seq request tome <reason>`: request a tome from the guild reward queue
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
- `/seq request aspects`
- `/seq request tome <reason>`
- `/seq ignore <IGN>`
- `/seq unignore <IGN>`
- `/seq map`
- `/seq map params`
- `/seq map eps <blocks>`
- `/seq map minSamples <count>`
- `/seq map reset`
- `/seq map debug`
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

## World map

Run `/seq map` to open the world map. Use the Gathering / Events control to switch between gathering analysis with guild territories and API-visible world events.

Navigation and map mode controls remain pinned in the left sidebar. Its map, analysis, filter, display, and tracking groups can be folded independently, while selection details and map insights are available from the collapsible right sidebar.

Gathering analysis supports three scopes: all bundled gathering nodes, nodes inside any guild territory, or nodes inside the selected territory. Resource, profession, cluster, and score controls continue to refine the active scope.

The Events view shows runs currently visible through the Wynncraft API. Choose All or Tracked to filter the markers, click a marker for event details, and use Track Event or the searchable tracking dropdown to manage persistent tracking. The management list can be limited to Tracked Only for quick removal. Tracked-event detection messages can be enabled in the World Events settings category.

## Achievements

Open **Achievements** from the main Sequoia screen (`/seq`, or `O`) or from any Sequoia sidebar.

One line per guild raid with your completions, the tier you are on and how far you are from the next
one, plus a combined line for every graid together. Tiers are named after the metal you earn, from
Bronze up to Mythril, and each has its own colour. The combined line asks for twice as many runs as a
single raid.

Counts and tiers both come from the Sequoia backend, which tallies the raid announcements the mod
already sends, so your numbers follow you across accounts and machines. The mod only keeps the
thresholds it needs to show how far the next tier is. It loads everything when the game starts and
refreshes a few seconds after every raid you finish, so the screen is filled in before you open it.
When the backend cannot be reached it says so instead of showing zeroes.

The metals are theme colours (`achievement.bronze` through `achievement.mythril`), so a personal theme
can restyle all of them. See [`docs/theme-template.theme.yml`](docs/theme-template.theme.yml).

## Settings

The settings screen includes controls for:

- Auto connect to the Sequoia backend
- Discord chat display
- World name links in chat, and whether clicking one switches immediately or types `/switch` into the chat box
- Raid auto-announce
- Tracked world-event notifications
- Global Sequoia UI size
- UI theme selection
- Update checks on startup

Sequoia includes Default and High Contrast themes. Open **Theme editor** from the Settings
screen to copy a theme, edit its full RGBA palette, preview changes live, and save a personal
theme without restarting. Personal themes are stored as `*.theme.yml` files under
`config/sequoia/themes`. Files added manually are discovered when the client starts.
The complete supported schema is available in [`docs/theme-template.theme.yml`](docs/theme-template.theme.yml).

## Installation

1. Install Fabric for Minecraft `1.21.11`.
2. Put the Sequoia mod jar in your Minecraft `mods` folder.
3. Install Fabric API.
4. Install [Wynntils](https://wynntils.com) for improved class detection.
5. Start the game and press `O`, or run `/seq`.

## License

MIT: `LICENSE.txt`.
