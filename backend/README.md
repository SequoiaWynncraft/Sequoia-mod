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

## Who is allowed to write

A token from `api.seqwawa.com` is opaque: only the service that issued it can say
who it belongs to. So this service does not try to read one. It signs in members
itself, the same way the mod expects, and issues its own tokens:

1. The mod asks `POST /auth/minecraft/challenge` and gets a `server_id` back.
2. The mod tells Mojang it joined a server with that id, which only the real owner
   of the account can do.
3. The mod calls `POST /auth/minecraft/complete`, and this service asks Mojang
   whether that join happened. Mojang answers with the uuid.
4. This service hands back a token it signed with `RAID_PROFILES_SECRET`, and the
   mod sends it as `Authorization: Bearer <token>` on every write.

That is the default, `RAID_PROFILES_AUTH=minecraft`, and it is the only mode the
mod can use. Set `RAID_PROFILES_SECRET` to any long random string and keep it
secret: whoever holds it can mint a token for any member.

Two other modes exist:

- `RAID_PROFILES_AUTH=trust-header` reads the caller from `X-Minecraft-Username`
  and `X-Minecraft-Uuid`, which is how you call the service by hand with curl. The
  mod never sends those headers, and anyone who reaches the service could overwrite
  anybody's profile, so this is for your own machine only.
- `RAID_PROFILES_AUTH=disabled` refuses every write, so the service cannot be
  exposed by accident before you have decided.

### Pointing the mod at it

The mod refuses to sign in over plain HTTP, so it can only reach a deployment
served over HTTPS. Once yours is, build the mod against it:

```powershell
./gradlew build -Praid_profiles_environment=https://raid-profiles.example.com/api
```

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
branches on, `message` is what the player reads under the Save button. The one
worth knowing about is `409 identity_unknown`: the caller sent no
`X-Minecraft-Uuid`, and a profile stored without one could never be matched to a
roster row, so it is refused rather than silently lost.

Saving looks like this:

```powershell
curl -X PUT http://127.0.0.1:8000/raid-profiles/me `
  -H "Content-Type: application/json" `
  -H "X-Minecraft-Username: ArcLeRetour" `
  -d '{\"builds\":[\"ASCENDANCY\",\"CSPRING\"],\"can_bring_auras\":true,\"region\":\"EU\",\"status\":\"down for tna\"}'
```

The service normalises what it stores, so the client never has to: build keys are
uppercased, duplicates dropped, unknown ones rejected with a message naming the
valid keys. `updated_at` is set by the server rather than taken from the request,
so a wrong clock on someone's PC cannot make their profile look newest.

## Settings

All optional. Set them as environment variables before starting the service.

| Variable | Default | What it does |
| --- | --- | --- |
| `RAID_PROFILES_DB` | `raid-profiles.db` | Where the SQLite file lives. |
| `RAID_PROFILES_AUTH` | `trust-header` | `trust-header` or `disabled`. See above. |
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
