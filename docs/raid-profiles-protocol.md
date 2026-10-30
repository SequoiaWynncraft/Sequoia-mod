# Raid-profile API

The [Sequoia backend](https://github.com/SequoiaWynncraft/sequoia-backend) owns the build catalog and saved profiles.
The mod parses responses with `RaidProfileStore.parseProfilesResponse` into `RaidProfilesResponse` and caches the last response locally.
`RaidProfilesBackendContractTest` checks the saved response fixture.

All endpoints require a bearer token, `X-Sequoia-Mod-Version`, and current Sequoia membership.
Unauthenticated requests return `401`. Non-members return `403 not_in_guild`, including website administrators without guild membership.
Membership follows the backend's Wynncraft roster, not the token's remaining lifetime. Request headers cannot supply another player's identity.
Fields use `snake_case` and timestamps use ISO-8601 instants.

## Backend selection

`BuildConfig.RAID_PROFILES_API_URL` follows `BuildConfig.API_URL` unless the build sets `raid_profiles_environment`.
Production uses `https://api.seqwawa.com/api`. Staging uses `https://staging.seqwawa.com/api`.
To use staging profiles while the other integrations stay on their selected backend, run:

```sh
mise run build -- -Praid_profiles_environment=staging
```

When the two backends differ, the mod authenticates separately for raid profiles and keeps that token in memory.
The account must be linked on that backend. If it is not linked, the mod provides the staging website's sign-in link.
The main session is unchanged. The mod ignores live `raid_profile_update` messages in this mode because the WebSocket belongs to the main backend.

## GET /raid-profiles

The response includes the catalog and saved profiles together.
The mod requests it when the panel opens, on Refresh, and after saving.

```json
{
  "schema_version": 1,
  "catalog": {
    "builds": [
      { "key": "ASCENDANCY", "label": "Ascendancy", "position": 1 }
    ],
    "raids": [
      {
        "key": "TNA",
        "short_name": "TNA",
        "api_name": "The Nameless Anomaly",
        "position": 1,
        "build_keys": ["ASCENDANCY"]
      }
    ]
  },
  "profiles": [
    {
      "minecraft": {
        "uuid": "10000000-0000-0000-0000-000000000001",
        "username": "ExamplePlayer"
      },
      "builds": ["ASCENDANCY"],
      "can_bring_auras": true,
      "region": "EU",
      "status": "Available for TNA",
      "updated_at": "2026-09-05T09:21:15Z"
    }
  ]
}
```

### Catalog builds

| Field | Meaning |
| --- | --- |
| `key` | Required uppercase identifier stored in profiles. Never reuse it for another build. |
| `label` | Display name. A missing or blank label falls back to a tidied key. |
| `position` | Display order. Ties sort by `key`. |

### Catalog raids

| Field | Meaning |
| --- | --- |
| `key` | Required uppercase identifier used by the raid filter. |
| `short_name` | Display abbreviation. Defaults to `key`. Keep it around four characters to fit. |
| `api_name` | Exact Wynncraft raid name used to read guild raid counts. |
| `position` | Display order. |
| `build_keys` | Meta builds for this raid. The client ignores keys absent from `catalog.builds`. |

`api_name` must match a key in `members.<rank>.<name>.globalData.currentGuildRaids.list`.
A mismatch displays a zero count. The supported names are:

| Key | API name |
| --- | --- |
| `TNA` | The Nameless Anomaly |
| `TCC` | The Canyon Colossus |
| `NOTG` | Nest of the Grootslangs |
| `NOL` | Orphion's Nexus of Light |
| `WTP` | The Wartorn Palace |

Use `currentGuildRaids`, not `raids`. The latter includes raids completed in previous guilds.

### Profiles

| Field | Meaning |
| --- | --- |
| `minecraft.uuid` | Required, non-null profile identity and player-head lookup key. |
| `minecraft.username` | Display name. It is not the profile identity. |
| `builds` | Catalog build keys. The client skips unknown keys. |
| `can_bring_auras` | Boolean, separate from builds. Defaults to `false`. |
| `region` | `EU`, `NA`, or `AS`. Missing and unknown values mean no region. |
| `status` | Free text. The client truncates it to 64 characters. |
| `updated_at` | Last save time. Missing values use the current time. |

Return only players who have saved a profile. A missing profile means the player has not shared one.
A profile with `"builds": []` means the player shared a profile with no builds selected.

## PUT /raid-profiles/me

Saving sends the caller's profile without a username. The bearer token identifies the caller.
The setup screen remains open until the request succeeds.

```json
{
  "builds": ["ASCENDANCY"],
  "can_bring_auras": true,
  "region": "EU",
  "status": "Available for TNA"
}
```

`builds` may be empty. The backend sets `updated_at` and ignores a client-supplied timestamp.
A successful `200` response is one `profiles[]` entry. The client applies that response, including normalized values, rather than its original request.

Errors contain `{"code": "...", "message": "..."}`. The client uses `code` for behavior and shows `message` under Save.

| Status | Code | Meaning |
| --- | --- | --- |
| `400` | `invalid_request` | Unknown build key or invalid region. |
| `409` | `identity_unknown` | The backend has no Minecraft identity for the caller. The client asks the player to rejoin and retry. |
| `401` | `token_invalid` or `token_expired` | Missing, invalid, or expired token. |
| `403` | `not_in_guild` | The caller is not a current guild member. |
| `426` | `mod_version_unsupported` | The client is below the configured minimum version. |

## DELETE /raid-profiles/me

This optional endpoint clears the caller's profile and returns `204`.
The mod does not call it.

## WebSocket raid_profile_update

The optional update message refreshes open panels after a save or removal.
Without it, the panel refreshes through GET when opened, refreshed, or saved.

```json
{
  "type": "raid_profile_update",
  "action": "updated",
  "profile": {
    "minecraft": {
      "uuid": "10000000-0000-0000-0000-000000000001",
      "username": "ExamplePlayer"
    },
    "builds": ["ASCENDANCY"],
    "can_bring_auras": true,
    "region": "EU",
    "status": "Available for TNA",
    "updated_at": "2026-09-05T09:21:15Z"
  }
}
```

`action` is `updated` or `removed`. Both require a non-null `minecraft.uuid`.
A removal retains the profile shape with its other fields set to `null`.
`RaidProfileStore.onLiveUpdate` removes the entry instead of treating it as an empty shared profile.
The backend controls version gating. `ConnectionManager.VERSION_REMINDER_INTERVALS` only throttles
client reminders after a version rejection; it does not gate `raid_profile_update` messages.

## Matching on UUID, not on username

Profiles use `minecraft.uuid` as their identity. A rename must update or remove the existing entry, not create a duplicate under a new username.
This applies to GET responses and both WebSocket actions.

`RaidProfilesResponse` also builds a lowercase-name index for offline friends known only by name.
Roster-backed profile matching does not use that index.

## Catalog updates

The backend reads builds, raids, and their relationships from `raid_profile_catalog_builds`, `raid_profile_catalog_raids`, and `raid_profile_catalog_raid_builds`.
Changes appear on the next request without a mod release.

Keep build keys stable. Retiring a key removes it from the client's display but does not delete saved references.
Restoring the key makes those references visible again.

## Client-only data

Friends, premade parties, and private notes stay in `config/sequoia/raid-profile.json` and are not uploaded.
The shared response cache at `config/sequoia/cache/raid-profiles.json` contains only the last GET response.
