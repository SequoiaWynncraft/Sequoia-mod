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
- Guild members panel: who is online, on which world, which raid builds they own, and who is mid-raid
- Friend list with a one-click "raid ?" whisper, and saved premade parties that show who can come
- Guild invite and removal audit relay for staff utilities
- Party finder commands and UI
- Raid tracking and announcements
- Per-player raid gambit counts parsed natively from the raid-start roster
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
- `/seq members`: open the guild members panel
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
- `/seq members`
- `/seq member`
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

## Guild members

Run `/seq members`, or press `O` and choose **Members**, to see every guild member Wynncraft
reports as online.

### Your raid profile

The first time you open the panel it asks you to fill in a raid profile: which of
the guild's meta builds you own, whether you can bring auras, your region, and a
one-line status. It takes about a minute and replaces "who has an ascendancy for
TNA" in guild chat.

The list of meta builds comes from the backend, not from the mod, so when the
guild's meta changes everyone sees it on their next refresh instead of their next
update. Saving sends your profile to the backend; the setup screen stays open and
shows the reason if that fails, rather than telling you your builds are shared
when they are not.

You tick builds, not raids: the same build serves several raids, so raid coverage
is worked out from what you ticked. **Not now** skips setup and is remembered;
**My profile** in the header reopens it whenever you finish a new set.

### Members

The **Members** tab lists everyone online, sorted by name until you sort it otherwise, with their world in its own
column, so a name stays where you expect it instead of jumping around every time somebody
switches server. Your own world is highlighted. A member whose world Wynncraft will not
report, which happens when they have turned off their online status, shows a `?` there.

Each row leads with the player's head so you can spot who it is at a glance, with a dot on
its corner for their status: green when they are free, red while they are busy.

The rest of the row is how long they have been online, their guild raid count, their war
count, and the builds they said they own. The raid number follows the filter: with no raid
selected it is every guild raid they have run, and with one selected it narrows to that
raid. Counts are **guild raids only**, the ones run while in Sequoia. A lifetime total
would fold in every raid they did in a previous guild, which is not what you are asking
when you are picking a group.

Click a column heading to order the list by it: **MEMBER**, **WORLD**, **ONLINE**,
**GRAIDS** or **WARS**. The heading in force carries an arrow, and clicking it again turns
the order round. Each column starts the way it is usually read, so wars and guild raids
start with the highest and names start at A. Members Wynncraft says nothing about stay at
the bottom either way round, rather than floating to the top as a row of question marks.

Auras are shown separately from builds, as an `AURAS` tag beside the name, because being
able to bring auras is not a build.

Filter with the raid chips (**All**, then each raid), **Auras**, **Free**, and the name
search. With a raid selected, the builds column narrows to the builds that are meta for
that raid, and a member who has not shared a profile is matched on their clear count
instead, which is weaker evidence but needs nobody to opt in.

Click any row for the full card. Everything Wynncraft publishes about them is there, and
none of it costs a request of its own since it rides along in the roster the panel already
fetches: rank, when they joined the guild, world, how long they have been online, playtime,
total level, wars, guild XP contributed and where that places them, and how their raids have
gone (damage, healing, deaths, gambits). Then what they told us: region, auras, declared
builds and guild raids per raid. A **private note** you can keep on that member sits at the
bottom and never leaves your client. The same card has **Add friend**.

Row actions: **Join** switches you to that member's world, and **Invite** invites them to
your Wynncraft party, creating the party first when you are not in one yet. With a raid
filter active, the confirmation names the build they bring to it.

A member sitting in a party finder listing shows a chip such as `PF TNA 2/4` where the busy
chip would be. When the listing is open and has room, the chip is a button: click it to join
as DPS, the party finder's default. It stays grey when the listing is full or invite only,
on your own row, and while you are already in a listing yourself.

### Friends

The **Friends** tab is the people you like raiding with. Add someone from their profile in
the Members tab; they stay on the list whether or not they are online.

- **Raid ?** sends them `/msg <name> raid ?`.
- **Invite** pulls them into your party, creating it if you are not in one.
- **Remove** takes them off the list.

Every friend shows when they last logged in, such as `NA6, logged in 2h ago` for someone
online or `offline, logged in 3d ago` for someone who is not. That is Wynncraft's `lastJoin`,
the time they connected, not the time they left, which is why it does not say "seen". A
member who hides their online status in Wynncraft's privacy settings has no login time.

Offline friends stay listed with a grey dot and a faded head, and their two action buttons
are disabled.

### Premade parties

The **Premades** tab holds groups you run with often, so a regular team is one click rather
than four invites typed out every time.

**New party** opens the editor: give it a name, then add members by username, or press
**Add my party** to pull in whoever is in your Wynncraft party right now. A party holds up
to ten, which is Wynncraft's largest raid group.

Each row says who can come, such as `3/4 online, 1 busy`, in green when the whole group is
free. The invite button only invites the free members: it reads **Invite all** when that is
everyone and **Invite 2** when it is not, and the confirmation says who was left out. It
creates the party when you are not in one, and spaces the invites out so Wynncraft does not
drop the commands. **Edit** reopens the editor, where **Delete** also lives.

Click a row to see every seat: free and on which world, busy with its countdown, or offline
with their last login.

### Busy status

A member shows a **Busy** chip with a countdown for eight minutes after finishing a raid. The
signal is the same raid completion report the mod relays to Discord: Wynncraft announces every
guild raid in guild chat, so one completion marks its whole party at once, with no server round
trip. Members running Sequoia are tagged `SEQ`.

### Where the data comes from

| What | From |
| --- | --- |
| Who is online, their world, playtime, guild raid counts, last login | Wynncraft's public API |
| Who is in a party finder listing | The party finder's own listings |
| The meta builds and raids, and everyone's profile | The Sequoia backend, `GET /raid-profiles` |
| Your friends, premade parties and private notes | This client only, `config/sequoia/raid-profile.json` |

The last row never leaves your machine. A private note about somebody is not
something to upload.

The last backend response is cached to `config/sequoia/cache/raid-profiles.json`
so the panel is not blank while the backend is unreachable. The column header
says how many profiles are in play, `N SHARED` or `ONLY YOUR PROFILE`, so an empty
builds column reads as "nobody has shared one" rather than "nobody owns anything".

Raid profiles can be pointed at a different backend than the rest of the mod, to try
the feature on staging while sign-in, chat and the party finder stay on production:

```
./gradlew build -Praid_profiles_environment=staging
```

It follows `backend_environment` when not set, so an ordinary release build is
unaffected.

Each backend signs its tokens with its own secret, so staging refuses a production
token. When the two differ, the mod signs in to the raid-profiles backend a second
time and keeps that token in memory only; the main session is left alone. That
second sign-in needs your account linked on that backend. If it is not, the mod
posts a link in chat to its website sign-in
(`https://staging.seqwawa.com/auth/web/start?return_to=https://staging.seqwawa.com/`),
which links through Discord and Wynncraft the same way `/link` does. Live
`raid_profile_update` pushes are ignored in this mode, because the WebSocket still
belongs to the main backend.

[`docs/raid-profiles-protocol.md`](docs/raid-profiles-protocol.md) is the contract
the backend implements, and [`backend/`](backend) is a working implementation of
it in one Python file.

The roster refreshes at most once a minute, matching the two-minute cache
Wynncraft serves the guild endpoint with; **Refresh** in the header forces the
next allowed fetch of both the roster and the profiles.

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
