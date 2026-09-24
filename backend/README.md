# Raid profiles backend

A small service that publishes the guild's raid meta and stores what each member
can bring to a raid. The mod has no copy of either: with this service down, the
members panel has no builds to show.

It is one Python file, [`main.py`](main.py), with an SQLite file for storage. No
database server to install, no framework to learn. It is meant to be read start
to finish, and it is small enough that you can port it into the real Sequoia
backend endpoint by endpoint if that is where it ends up living.

The endpoints match [`../docs/raid-profiles-protocol.md`](../docs/raid-profiles-protocol.md)
exactly, and `RaidProfilesBackendContractTest` in the mod parses a captured
response from this service, so the two are held together by a test.

## Who is allowed to use profiles

A token from `api.seqwawa.com` is opaque: only the service that issued it can say
who it belongs to. So this service does not try to read one. It signs in members
itself, the same way the mod expects, and issues its own tokens:

1. The mod asks `POST /auth/minecraft/challenge` and gets a `server_id` back.
2. The mod tells Mojang it joined a server with that id, which only the real owner
   of the account can do.
3. The mod calls `POST /auth/minecraft/complete`, and this service asks Mojang
   whether that join happened. Mojang answers with the uuid.
4. This service verifies that the UUID belongs to Sequoia using Wynncraft's guild
   roster, then returns a token signed with `RAID_PROFILES_SECRET`. The mod sends
   it as `Authorization: Bearer <token>` on every profile read and write.

That is the default, `RAID_PROFILES_AUTH=minecraft`, and it is the only mode the
mod can use. Set `RAID_PROFILES_SECRET` to any long random string and keep it
secret: whoever holds it can mint a token for any member.

Every profile request rechecks the authenticated UUID against Sequoia's roster.
An existing token stops granting access when the roster no longer contains its
owner. Membership is never inferred from a username or supplied identity header.
The service requests the [Wynncraft guild endpoint](https://docs.wynncraft.com/modules/guild/get-guild-by-name)
with `identifier=uuid`; Wynncraft's own cache can delay membership changes.
The service adds no membership cache or stale-success fallback.

Unauthenticated profile calls return `401 token_invalid`; authenticated
non-members return `403 not_in_guild`. If the roster cannot be fetched or parsed,
access fails closed with `503 guild_roster_unavailable` until it can be verified.

`RAID_PROFILES_AUTH=disabled` refuses profile reads, writes and new sign-ins.
The old `trust-header` mode is no longer accepted: anyone reaching that mode
could impersonate a member. Authentication configuration other than `minecraft`
or `disabled` stops the service at startup. `/health` remains public for monitors.

### Pointing the mod at it

The mod refuses to sign in over plain HTTP, so it can only reach a deployment
served over HTTPS. Once yours is, build the mod against it:

```powershell
./gradlew build -Praid_profiles_environment=https://raid-profiles.example.com
```

Give the address the service answers on, with no `/api` on the end: `main.py`
serves `/raid-profiles` and `/auth/minecraft/*` at its root, and the mod adds those
paths to whatever you pass. Anything other than `production`, `staging` or an
`https://` address stops the build, so a typo cannot quietly point it at staging.

Everything else in the mod stays on the Sequoia backend. Moving these endpoints
into `api.seqwawa.com` later makes that flag unnecessary, and this file is then
the spec for whoever maintains them.

## Running it on your machine

You need Python 3.12 or newer. Check with `python --version`.

From this folder, in PowerShell:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m uvicorn main:app --reload --port 8000
```

That last command stays running and prints the requests it receives. Stop it
with Ctrl+C. `--reload` restarts it whenever you edit `main.py`.

Now open **http://127.0.0.1:8000/docs**. That page lists every endpoint with a
"Try it out" button, so you can save and read profiles by hand without writing a
single line of client code. It is the fastest way to see what the service does.

A quick check that it is alive:

```powershell
curl http://127.0.0.1:8000/health
```

## The meta lives in catalog.json

[`catalog.json`](catalog.json) is the guild's meta: which builds exist, which
raids exist, and which builds are meta for which raid. The mod reads it from this
service and has no list of its own, so editing that file is how the meta changes.
No redeploy, no mod update: the file is read on every request.

Adding a build is one entry in `builds` plus its key in whichever raids it serves:

```json
{ "key": "NEWBUILD", "label": "Newbuild", "position": 9 }
```

Only `key` is required on a build or a raid. A missing `label` becomes the tidied
key (`CSPRING` reads `Cspring`), a missing `short_name` becomes the key. The file is
checked on every read, so a mistake such as a build with no key comes back as
`500 catalog_unavailable` naming the entry, never as an unexplained error.

Two rules keep old profiles valid. **Never reuse a key for a different build**,
because saved profiles reference keys. **Retiring a key is safe**: profiles that
still name it keep it in the database and the mod just stops showing it, so
putting the key back brings them all straight back.

`api_name` on each raid is the exact string Wynncraft uses for that raid, and the
mod reads clear counts with it. A typo there silently turns every count into `0`.
The five correct values ship in the file.

The Wartorn Palace is keyed `WTP` here, matching the guild's backend. Note that
the **party finder** subsystem keys the same raid `TWP`; the two are separate
services and the mod keeps them apart, but it is worth knowing before someone
tries to make them agree.

## What each endpoint does

| Method | Path | What it does |
| --- | --- | --- |
| `GET` | `/raid-profiles` | The meta catalog and every saved profile. This is the only call the panel makes. |
| `PUT` | `/raid-profiles/me` | Saves the caller's profile, replacing what was there. |
| `DELETE` | `/raid-profiles/me` | Clears the caller's profile. |
| `GET` | `/health` | Says the service is up and how many profiles it holds. |

Every error comes back as `{"code": ..., "message": ...}`. `code` is what the mod
branches on, and `message` is what the player reads under the Save button.
Manual API clients must complete the same Minecraft authentication flow and
supply its bearer token; identity headers do not authenticate a request.

The service normalises what it stores, so the client never has to: build keys are
uppercased, duplicates dropped, unknown ones rejected with a message naming the
valid keys. `updated_at` is set by the server rather than taken from the request,
so a wrong clock on someone's PC cannot make their profile look newest.

## Settings

Set them as environment variables before starting the service. Only
`RAID_PROFILES_SECRET` is needed in the default mode; without it, sign-in and every
profile request answers `503 auth_not_configured`.

| Variable | Default | What it does |
| --- | --- | --- |
| `RAID_PROFILES_AUTH` | `minecraft` | `minecraft` or `disabled`. See above. Any other value stops the service at start-up rather than guessing. |
| `RAID_PROFILES_SECRET` | none | Signs the tokens issued in `minecraft` mode. Required there; any long random string, kept secret. |
| `RAID_PROFILES_TOKEN_TTL` | `604800` (a week) | How long a token lasts, in seconds. |
| `RAID_PROFILES_DB` | `raid-profiles.db` | Where the SQLite file lives. |
| `RAID_PROFILES_CATALOG` | `catalog.json` next to `main.py` | Where the meta lives. |

## Putting it somewhere the guild can reach

Any host that runs Python works. The command to run in production drops
`--reload` and listens on all interfaces:

```
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Two things worth doing before you share the URL:

- Put HTTPS in front of it. A reverse proxy such as Caddy does this in two lines
  and gets a certificate on its own.
- Back up the `.db` file. It is one file; copying it is a complete backup.

The database is created automatically on first start, so there is no setup step.

Point the mod at it by building with `backend_environment=staging` in
`gradle.properties` and setting the staging URL, or by deploying under the
production API. The mod calls `GET /raid-profiles` and `PUT /raid-profiles/me`
relative to `BuildConfig.API_URL`.

## The files

- `main.py`: the whole service.
- `catalog.json`: the guild's meta. This is the file you will actually edit.
- `requirements.txt`: what to install. These are lower bounds rather than exact
  pins on purpose: pinning an old `pydantic` makes pip try to compile it from
  Rust source on a recent Python, which fails with a wall of errors.
- `.gitignore`: keeps the virtualenv and the database out of git.

## Authorization tests

From this folder, install the test dependencies and run the endpoint tests:

```powershell
python -m pip install -r requirements-test.txt
python -m unittest discover -s tests -v
```

The tests use temporary databases and stub Wynncraft/Mojang responses. They cover
reads and mutations, spoofed identities, non-members, departure with a valid token,
upstream failures, disabled authentication and token issuance.
