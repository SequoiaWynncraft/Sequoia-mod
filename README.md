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
- Interactive world map with gathering nodes analysis and active world events
- In-game WynnBuilder builder and crafter, including reading and generating WynnBuilder links
- Spell damage, spell costs and a worst-piece verdict for the gear you are wearing, beside your inventory
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
- `/seq wb`: open the WynnBuilder tools (builder and crafter)
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
- `/seq wb`
- `/seq wb build`
- `/seq wb build <link>`
- `/seq wb craft`
- `/seq wb craft <link>`
- `/seq wynnbuilder`
- `/seq craft`
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

## WynnBuilder

Open **WynnBuilder** from the main Sequoia screen, or run `/seq wb`, for an in-game version of
[WynnBuilder](https://wynnbuilder.github.io). It has two tools:

- **Builder** (`/seq wb build`) — fill the nine equipment slots, apply powders, set the build level,
  and read the aggregated statistics: health, effective health, elemental defences, skill point
  requirements and every identification. **Ability tree** opens the class tree for the equipped
  weapon.

  The right panel has three tabs — **Build**, **Items** and **Damage** — and every section folds. **Build** shows the totals; **Items** is a
  plain list of what is equipped, and clicking a piece unfolds its breakdown, listing each
  identification as the range a real drop can roll rather than a single value. Crafted pieces unfold
  to their recipe, materials and resulting ranges, plus an **Open in crafter** button that loads that
  exact craft into the crafter — the ingredient grid itself is left to the crafter. **Damage** lists
  every source the build has: the melee hit and its damage per second, the critical chance, and each
  spell broken down part by part, along with the buffs from the selected abilities that you can
  switch on and off.
- **Crafter** (`/seq wb craft` or `/seq craft`) — pick a recipe, material tiers and up to six
  ingredients, and see the resulting item. Ingredient effectiveness is shown per grid position,
  since where an ingredient sits changes what it contributes.

### The gear you are wearing

Open your inventory and a panel appears beside it, running the same calculator over the build you
have on: what each spell costs in mana, what it deals per second, and your effective health.

**Find my weakest piece** answers the question the numbers are usually asked for — what to replace
first. It is a measurement, not an opinion. Every equipped piece is put through the whole pipeline
twice, once as it is and once as it would be at its ceiling, and the difference in the build's own
damage is the verdict. A perfectly rolled ring of stats the build does not scale on therefore ranks
below a mediocre one carrying the right damage type, and a major identification that makes health
drive damage is accounted for without anything having to know about it.

A piece's ceiling depends on where it came from. A dropped item's is its best possible roll — a
drawback printed on a mythic is part of the item and no amount of rerolling removes it. A crafted
item's ceiling also drops the identifications that actively hurt, because those came from an
ingredient and are a recipe decision rather than luck. That is the difference between "reroll this"
and "recraft this", and the panel says which.

Powder tiers never have to be guessed. Wynncraft does not print them, but it does print the damage,
health and defences that result from them, so those printed numbers are what the panel reads.

#### What has to be opened

Gear is read the moment you open your inventory. The rest of a character lives in menus Wynncraft
only fills in when you look at them, so they are read while you look:

| What | Where it comes from |
| --- | --- |
| Equipment, powders, rolls | Always available |
| Ability tree | Read while you browse your own tree. It scrolls, so a partial read is reported as a percentage and climbs as you scroll |
| Mastery tomes | Read when you open the tome menu; a single page, so one look is enough |
| Skill points | Only printed in the character sheet |

Nothing is requested on your behalf. The panel lists what it has not counted rather than quietly
guessing, and a **Scan** button asks Wynntils to read the character sheet and the full ability tree
when you would rather not do it yourself — that one closes your inventory and opens those menus in
the background, which is why it only ever happens on a click.

### Links

Builds and crafts are exchanged as ordinary WynnBuilder links, so they work with the website and
with anything else that reads them.

- **Copy link** (or `Ctrl+C`) puts a `wynnbuilder.github.io` URL on the clipboard.
- **Paste link** (or `Ctrl+V`) reads one back. Both the current format and the older link formats
  are understood, so links shared years ago still open. A link written against an older data version
  opens too: only that version's encoding constants are fetched, about a kilobyte, because item IDs
  are stable between versions.
- `/seq wb build <link>` and `/seq wb craft <link>` import a link straight from chat.

### Item data

Item, ingredient, recipe, tome, aspect and ability tree data is downloaded from
`wynnbuilder.github.io` the first time the section is opened and cached under
`config/sequoia/wynnbuilder/`. Nothing is bundled in the mod: WynnBuilder is GPL-3 licensed and
Sequoia is MIT, so its data files are used at runtime rather than redistributed. Opening a link
built on an older data version fetches that version, because item IDs are version specific.

WynnBuilder is a separate project; Sequoia is not affiliated with it and only reads its published
data and link format.

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
