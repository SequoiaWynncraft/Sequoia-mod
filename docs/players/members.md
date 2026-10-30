# Guild members

Run `/seq members` or select **Player List** from the menu or sidebar.
The panel lists members Wynncraft reports as online.

## Share your raid profile

On first use, choose the meta builds you own, whether you can bring auras, your region, and a short status.
The backend supplies the build catalog and calculates which raids each build covers.
Saving keeps the screen open if the backend returns an error.

Choose **Not now** to skip setup. **My profile** reopens it later.
Players without a shared profile show `no profile yet`, rather than an empty list of builds.

## Find and invite members

The **All** tab shows each player's world, online time, status, and declared builds.
Auras appear separately beside the name. The current world is highlighted.
A `?` means Wynncraft has not supplied that information, including when a player hides their online status.

Click **MEMBER**, **WORLD**, or **ONLINE** to sort. Click again to reverse the order.
Unknown values remain at the bottom. Filter by raid, **Auras**, **Free**, or name.
With a raid selected, the builds column shows builds for that raid.
For members without a profile, the raid filter uses their clear count instead.

The status column shows these states.

- **Free** means the member has no current busy signal or listing.
- **Busy** counts down for eight minutes after a guild raid completion. One completion marks the whole reported party.
- `PF TNA 2/4` identifies a party-finder listing. It takes precedence over Busy and turns red when full.
- Your own listing uses the theme's accent color and shows **Your party** on hover.

An open listing with space can be joined by clicking its status chip, unless it is your row or you already belong to a listing.
Joining this way uses the default DPS role. Invite-only listings cannot be joined through the chip.
Members running Sequoia have a `MOD` marker.

Click a row to open the member card. It contains rank, join date, world, online time, playtime, levels, wars, guild XP, raid statistics, and profile details.
You can add the member as a friend or keep a private note on their card.

**Invite** creates a Wynncraft party if needed and invites the member.
It also creates a party-finder listing if you have none. A selected raid becomes the listing's activity.
Without a raid filter, the listing covers all raids.
The region comes from your current world, then your profile, then NA.
Inviting leaves an existing listing unchanged. Accepted invites sync into the listing through **Sync with Wynn party**.

## Friends

Add friends from member cards. The **Friends** tab keeps them listed while offline.

- **Raid ?** sends `/msg <name> raid ?`.
- **Invite** invites the friend, creating a party if needed.
- **Remove** removes the friend from your saved list.

Friends show their last login, not their last logout. Wynncraft's privacy settings can hide that time.
Busy friends show a `BUSY` tag. Offline friends have disabled invitation and whisper buttons.

## Premade parties

Use **New party** in **Premades** to name a group and add up to four players, including yourself.
**Add my party** imports your current Wynncraft party.

Each row reports who is online and busy. Click it for individual seats.
The invite button sends invites only to free members, with a confirmation showing who was skipped.
The mod spaces out the commands. Use **Edit** to change or delete a saved party.

## Saved data

Wynncraft supplies online status, worlds, playtime, raid counts, and login times.
Sequoia's backend supplies the build catalog and shared profiles through `GET /raid-profiles`.
The panel refreshes at most once a minute. **Refresh** requests both sources at the next allowed time.

Friends, premade parties, and private notes stay in `config/sequoia/raid-profile.json` on your client.
The last shared profile response is cached in `config/sequoia/cache/raid-profiles.json` for use when the backend is unavailable.
See the [raid-profile contract](../raid-profiles-protocol.md) for the API.
