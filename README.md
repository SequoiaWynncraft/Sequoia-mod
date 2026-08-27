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
- Seq-only war planner with timed availability, shared five-player territory queues, exclusive 1–5 player parties, a shared Lead + three Eco board, and collaborative territory zones
- Raid tracking and announcements
- Per-player raid gambit counts parsed natively from the raid-start roster
- Sequoia achievements: guild-raid completion counts ranked from Bronze to Mythril
- A playful Princess-mode guild-raid count, compact leaderboard, and numbered raid celebration
- Interactive world map with gathering nodes analysis and active world events
- Guild-specific settings and status screens

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.2+1.21.11`
- Java `21`
- Wynntils (optional; adds legacy completed-war and queue lifecycle reporting, while live war
  telemetry and active-class detection also work through bounded vanilla fallbacks)

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
- `/seq war`: open the Seq-only war planner after the backend authorizes the current member
- `/seq war available <minutes>`: advertise war availability for 1–1440 minutes
- `/seq war unavailable`: clear your war availability
- `/seq connect`: connect to the backend
- `/seq status`: show connection state
- `/seq logout`: clear the current backend session
- `/seq request aspects`: request aspects from the guild reward queue
- `/seq request tome <reason>`: request a tome from the guild reward queue
- `/allyraids [minutes]`: show recent shared raids with current allies (Sequoia members only)
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
- `/allyraids [minutes]`
- `/seq allyraids [minutes]`
- `/seq ignore <IGN>`
- `/seq unignore <IGN>`
- `/seq map`
- `/seq map params`
- `/seq map eps <blocks>`
- `/seq map minSamples <count>`
- `/seq map reset`
- `/seq map debug`
- `/seq war`
- `/seq war available <minutes>`
- `/seq war unavailable`
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
- `/seq party scan`
- `/seq party game create`
- `/seq party game invite <username>`
- `/seq party game invite-all`

</details>

## Party finder

`/seq party scan` imports the leader's current Wynn party into the active Sequoia listing. Scanned usernames that
match verified linked players with Party Finder access become regular listing members, which enables role, kick, and
leadership actions. Unlinked, ineligible, conflicting, or recently kicked players remain visible as observed
username-only occupancy without receiving Sequoia member privileges.

## War planner

The planner uses backend schema v3: party membership is separate from the
shared Lead/Eco support slots, so support players may also join a party. The
zone map provides palette colors, multi-party assignment, territory routes,
responsive full-map zone previews, and production-based resource coloring. Stored
resources do not affect color. Ordinary 9k emerald income is treated as the
baseline; territories with 18k base emerald production are highlighted as
emerald generators.

The War Planner entry and `/seq war` command only appear after a compact protected backend access check confirms
that the current account is a Sequoia member. The full planner snapshot is fetched only when the player opens or
explicitly refreshes the screen. Members can advertise timed availability and see their own team immediately;
authorized managers can atomically create or edit parties of one to five people, while one shared Lead and three Eco
slots remain independent of party membership. Managers choose `HQ Team`, `VLow Munch`, or `FFA` from the team editor;
the backend keeps HQ unique and assigns the numeric VLow/FFA suffixes. The Teams view adapts from full-width cards
to a one-column rail and then a balanced two-column grid, while keeping shared support, presence, composition roles,
actions, dragging, and scrolling on the same visible layout. The Zones view assigns named, colored groups of
territories to teams and previews each zone against the complete territory map.
Composition capabilities, eligibility, team exclusivity, versions, and all mutations remain server-authoritative.


## World map

Run `/seq map` to open the world map. Use the Gathering / Events control to switch between gathering analysis with guild territories and API-visible world events.

Navigation and map mode controls remain pinned in the left sidebar. Its map, analysis, filter, display, and tracking groups can be folded independently, while selection details and map insights are available from the collapsible right sidebar.

Gathering analysis supports three scopes: all bundled gathering nodes, nodes inside any guild territory, or nodes inside the selected territory. Resource, profession, cluster, and score controls continue to refine the active scope.

The Events view shows runs currently visible through the Wynncraft API. Choose All or Tracked to filter the markers, click a marker for event details, and use Track Event or the searchable tracking dropdown to manage persistent tracking. The management list can be limited to Tracked Only for quick removal. Tracked-event detection messages can be enabled in the World Events settings category.

## Achievements

Open **Achievements** from the main Sequoia screen (`/seq`, or `O`) or from any Sequoia sidebar.

The screen shows one line per guild raid with the authenticated Minecraft account's completions,
current tier, and progress toward the next tier, plus a combined line for all guild raids. Tiers run
from Bronze to Mythril, each with its own theme colour; combined tiers require twice as many runs as
a single-raid tier.

Counts and current tiers come from the Sequoia backend. Progress follows the same Minecraft account
across machines; linked alternate accounts retain separate totals. The backend counts recorded raid
announcements from 14 August 2026 at 18:12:02 UTC onward. The mod keeps the thresholds needed to show
the next target, polls periodically, and refreshes shortly after a locally detected completion. If
there is no cached result and the backend is unavailable, the screen reports that state rather than
presenting missing data as zero completions.

Tier colours use the `achievement.bronze` through `achievement.mythril` theme keys. See
[`docs/theme-template.theme.yml`](docs/theme-template.theme.yml) for the complete palette schema.

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
- War queue HUD text size, maximum rows, and an only-my-queues filter shared with the war planner map
- Optional queue-miss blame messages, disabled by default

War tracking, planner display, and queue controls share one **Wars** category. Settings that depend on another
control remain visible underneath it and are indented and disabled while their parent is off.

Sequoia includes Default and High Contrast themes. Open **Theme editor** from the Settings
screen to copy a theme, edit its full RGBA palette, preview changes live, and save a personal
theme without restarting. Personal themes are stored as `*.theme.yml` files under
`config/sequoia/themes`. Files added manually are discovered when the client starts.
The complete supported schema is available in [`docs/theme-template.theme.yml`](docs/theme-template.theme.yml).

## Installation

1. Install Fabric for Minecraft `1.21.11`.
2. Put the Sequoia mod jar in your Minecraft `mods` folder.
3. Install Fabric API.
4. Optionally install [Wynntils](https://wynntils.com) for legacy completed-war and queue lifecycle
   reporting. Sequoia's core UI, live war telemetry, and active-class detection do not require it.
5. Start the game and press `O`, or run `/seq`.

## License

MIT: `LICENSE.txt`.
