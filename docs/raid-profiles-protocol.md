# Raid profiles: backend contract

> **Aligned with the shipped backend, 5 September 2026.** Four things changed from
> the first draft of this document, and the mod now matches all four: the Wartorn
> Palace is keyed `WTP`, `409 identity_unknown` exists, every profile carries a
> non-null `minecraft.uuid`, and the panel keys on that uuid rather than on the
> username. See [Matching](#matching-on-uuid-not-on-username).

Everything the backend has to provide for the members panel. The mod holds no
copy of the guild's meta and no copy of anyone's profile: both come from here, so
until these endpoints exist the panel's builds column stays empty.

A working implementation of all of it lives in [`../backend/`](../backend), in one
readable Python file. If you are porting rather than deploying that, it is the
reference to port from.

`RaidProfilesResponse` is the client's Gson model and
`RaidProfileStore.parseProfilesResponse` is the only parser, so a payload that
matches this document needs no client work. `RaidProfilesBackendContractTest`
parses a real captured response from the reference service, which is what keeps
this document honest.

Conventions are the ones the rest of the protocol already uses: REST, `snake_case`
fields, ISO-8601 instants, and the `Authorization: Bearer <token>` plus
`X-Sequoia-Mod-Version` headers `ApiClient` sends on every call.

Every profile endpoint requires authenticated current Sequoia membership.
Unauthenticated calls receive `401`; non-members receive `403 not_in_guild`.
The Java service checks its existing Wynncraft membership roster for REST and
live updates, without granting this feature to non-member website administrators.
The Python service checks UUID membership before issuing a token and on every
profile request; an unavailable roster returns `503 guild_roster_unavailable`.
Header-supplied identities are not supported. Membership freshness follows the
respective Wynncraft roster source, not the lifetime of an authentication token.

Base URL is `BuildConfig.API_URL`, which is `https://api.seqwawa.com/api` in
production and `https://staging.seqwawa.com/api` in staging.

---

## 1. `GET /raid-profiles`

The single call the panel makes. Returns the meta catalog **and** every member's
profile together, because they are always read together: a profile's build keys
mean nothing without the catalog that names them, and shipping them apart only
creates a window where the two disagree.

Called when the panel opens, when the player presses Refresh, and after a save.

### Response `200`

```json
{
  "schema_version": 1,
  "catalog": {
    "builds": [
      { "key": "ASCENDANCY", "label": "Ascendancy", "position": 1 },
      { "key": "CSPRING",    "label": "Cspring",    "position": 2 },
      { "key": "RESONANCE",  "label": "Resonance",  "position": 3 },
      { "key": "HALCYON",    "label": "Halcyon",    "position": 4 },
      { "key": "CATACLYSM",  "label": "Cataclysm",  "position": 5 },
      { "key": "TCRACK",     "label": "Tcrack",     "position": 6 },
      { "key": "HERO",       "label": "Hero",       "position": 7 },
      { "key": "HADAL",      "label": "Hadal",      "position": 8 }
    ],
    "raids": [
      {
        "key": "TNA", "short_name": "TNA", "api_name": "The Nameless Anomaly", "position": 1,
        "build_keys": ["ASCENDANCY", "CSPRING", "RESONANCE"]
      },
      {
        "key": "TCC", "short_name": "TCC", "api_name": "The Canyon Colossus", "position": 2,
        "build_keys": ["CSPRING", "ASCENDANCY", "HALCYON"]
      },
      {
        "key": "NOTG", "short_name": "NOTG", "api_name": "Nest of the Grootslangs", "position": 3,
        "build_keys": ["CATACLYSM", "TCRACK", "HERO", "HADAL"]
      },
      {
        "key": "NOL", "short_name": "NOL", "api_name": "Orphion's Nexus of Light", "position": 4,
        "build_keys": ["ASCENDANCY", "CATACLYSM", "HALCYON"]
      },
      {
        "key": "WTP", "short_name": "WTP", "api_name": "The Wartorn Palace", "position": 5,
        "build_keys": ["CSPRING", "ASCENDANCY", "RESONANCE"]
      }
    ]
  },
  "profiles": [
    {
      "minecraft": { "uuid": "66efb975-31b4-499e-9b46-a34980edd8ee", "username": "ArcLeRetour" },
      "builds": ["ASCENDANCY", "CSPRING", "RESONANCE"],
      "can_bring_auras": true,
      "region": "EU",
      "status": "I am the goat",
      "updated_at": "2026-09-05T09:21:15Z"
    },
    {
      "minecraft": { "uuid": "10000000-0000-0000-0000-000000000002", "username": "Blousy" },
      "builds": ["HADAL", "HERO"],
      "can_bring_auras": false,
      "region": null,
      "status": null,
      "updated_at": "2026-09-05T09:21:15Z"
    }
  ]
}
```

### `catalog.builds[]`

| Field | Type | Notes |
| --- | --- | --- |
| `key` | string | **Required.** Uppercase identifier. This is what profiles store, so it must never change once members have saved with it. |
| `label` | string | What players see. Safe to rename at any time; saved profiles are unaffected. Omitted or blank falls back to a tidied key (`CSPRING` becomes `Cspring`). |
| `position` | int | Sort order in the setup screen and the chips. Ties break on `key`. |

### `catalog.raids[]`

| Field | Type | Notes |
| --- | --- | --- |
| `key` | string | **Required.** Uppercase identifier, used by the filter. |
| `short_name` | string | The abbreviation the guild says out loud, shown on filter chips and column headings. Keep it to about four characters or it will be clipped. Defaults to `key`. |
| `api_name` | string | **The exact string Wynncraft uses**, see below. |
| `position` | int | Order of the filter chips and of the rows in a member's profile card. |
| `build_keys` | string[] | Which builds are meta for this raid. Every key must also appear in `catalog.builds`, or the client ignores it. |

**`api_name` matters more than it looks.** The mod reads each member's clear count
straight off the Wynncraft guild roster, under
`members.<rank>.<name>.globalData.currentGuildRaids.list`, where the keys are the
raids' full names. `api_name` is what maps one to the other, so a typo silently
turns every count into `0`. The five correct values are in the sample above, and
`RaidProfilesBackendContractTest` asserts them.

`currentGuildRaids` is deliberate: it counts raids run while in the guild, where
`raids` would fold in everything a member did in a previous guild.

### `profiles[]`

| Field | Type | Notes |
| --- | --- | --- |
| `minecraft.uuid` | string | **Required, never null.** This is what the client keys profiles on, and what it fetches player heads with. |
| `minecraft.username` | string | Display text. The client shows it but never matches on it, because it changes on a rename. |
| `builds` | string[] | Keys from `catalog.builds`. Anything the catalog does not list is skipped, so an in-flight meta edit never breaks a client. |
| `can_bring_auras` | bool | Separate from builds on purpose: bringing auras is not a build. Defaults to `false`. |
| `region` | string | `EU`, `NA` or `AS`. Omit when the member set none; an unknown value reads as none. |
| `status` | string | Free text, max 64 characters. The client truncates beyond that. |
| `updated_at` | ISO-8601 | When the member last saved. Absent is treated as "now". |

Include only members who actually saved a profile. Absent means "has not shared
one", which the panel draws differently from "shared one with nothing ticked". A
member who deliberately ticked nothing belongs here with `"builds": []`.

---

## 2. `PUT /raid-profiles/me`

Called when a member saves the setup screen. The client will not leave that screen
until this succeeds, so a failure has to come back as a real error status.

### Request

```json
{
  "builds": ["ASCENDANCY", "CSPRING"],
  "can_bring_auras": true,
  "region": "EU",
  "status": "down for tna all evening"
}
```

The caller is whoever the bearer token belongs to, so there is no username in the
body. `updated_at` is set server-side; ignore a client-supplied one, or a wrong
clock on someone's PC makes their profile look newest.

`builds` may be empty. That is a member saying "I own none of the meta yet", which
is different from having no profile, and both need to be storable.

### Response `200`

One `profiles[]` entry, exactly as in `GET`. The client applies what you return
rather than what it sent, so any normalising you do (uppercasing keys, dropping
duplicates, truncating the status) shows up immediately and correctly.

### Errors

| Status | When |
| --- | --- |
Every error body is `{"code": "...", "message": "..."}`. `code` is what the client
branches on; `message` is shown to the player under the Save button, so write it
for a person to read.

| Status | `code` | When |
| --- | --- | --- |
| `400` / `422` | `invalid_request` | A build key outside the catalog, or a region that is not `EU`/`NA`/`AS`. |
| `409` | `identity_unknown` | The backend holds no Minecraft identity for the caller, so a stored profile could never be listed. Recoverable: the client tells the player to rejoin and retry. |
| `401` | `token_invalid` / `token_expired` | No or bad token. |
| `403` | `token_invalid` | Authenticated but not a guild member. |
| `426` | `mod_version_unsupported` | Client below the configured minimum. |

---

## 3. `DELETE /raid-profiles/me`

Optional. Clears the caller's profile so they read as "has not shared one" again.
Respond `204`. Nothing in the mod calls this yet; it exists so a member can undo a
mistake without a database edit.

---

## 4. WebSocket `raid_profile_update`

Optional, and worth doing last. Pushed to every connected guild member when
someone saves, so a panel left open goes stale in seconds rather than minutes.
Same envelope as `party_finder_update`: a `type` plus the changed object.

```json
{
  "type": "raid_profile_update",
  "action": "updated",
  "profile": {
    "minecraft": { "uuid": "66efb975-31b4-499e-9b46-a34980edd8ee", "username": "ArcLeRetour" },
    "builds": ["ASCENDANCY", "CSPRING", "RESONANCE"],
    "can_bring_auras": true,
    "region": "EU",
    "status": "I am the goat",
    "updated_at": "2026-09-05T09:21:15Z"
  }
}
```

`action` is `updated` or `removed`. Both carry a non-null `minecraft.uuid`, which
is what the client matches on.

On `removed` the payload keeps a profile's shape with its fields set to `null`
rather than omitted. **The client must not apply that as a profile**, or the
member reads as having shared an empty one instead of none;
`RaidProfileStore.onLiveUpdate` branches on the action before touching anything.

Without it the panel still refreshes when it opens, when Refresh is pressed, and
after a save. `GET /raid-profiles` alone makes the feature work.

If you gate capabilities by client version, add the type to
`VERSION_REMINDER_INTERVALS` in `ConnectionManager` alongside
`guild_raid_announcement`. `10` is a sensible interval: an outdated client gets
told, but not on every save.

---

## Matching on uuid, not on username

The one design point the client has to get right, and the reason removal events
carry the uuid.

A token is valid for days and the backend's username follows in-game renames, so
a panel left open can hold an entry under a name that no longer exists:

1. A member saves. The panel stores their profile.
2. They rename in game. The backend now knows them under the new name.
3. They save again, or clear. The event names them as they are now.

Matched by name, step 3 misses the entry from step 1: an update adds a duplicate
row, and a removal leaves the original on screen until the next refresh. No
backend change fixes that, because the event has to name the member as they are
now.

The uuid is stable across renames and is present on every profile the panel ever
receives, from `GET` and from both WebSocket actions. The client keys on
`minecraft.uuid` and treats `username` as display text. `RaidProfilesResponse`
also builds a lowercase-name index, used only by the friends list where a member
may be offline and a name is all there is; that index is the one place a stale
name can linger, and nothing with a roster row goes through it.

---

## Changing the meta later

Adding a build, retiring one, or changing which builds are meta for a raid is a
change to `GET /raid-profiles`'s catalog and nothing else. No mod release, no
client update, no coordination.

Two rules keep old data valid:

- **Never reuse a key for a different build.** Saved profiles reference keys.
- **Retiring a key is safe.** Profiles that still name it keep it in the database;
  the client drops it from display because the catalog no longer lists it. Put the
  key back and those profiles light up again.

In the reference backend this is one edit to `backend/catalog.json`, live on the
next request.

---

## What the mod still keeps to itself

For completeness, so nobody wonders where it went. These never touch the backend:

- **Friends**, **premade parties** and **private notes**, in
  `config/sequoia/raid-profile.json`. They are the player's own, and a private note
  about somebody is not something to upload.
- **A cache of the last `GET /raid-profiles`**, in
  `config/sequoia/cache/raid-profiles.json`, so the panel is not blank while the
  backend is unreachable. It is only ever a copy of what you served.
