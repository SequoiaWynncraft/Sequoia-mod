"""
Raid profiles service for the Sequoia mod.

Implements the contract in docs/raid-profiles-protocol.md:

    GET    /raid-profiles      the meta catalog plus every member's profile
    PUT    /raid-profiles/me   save the caller's profile
    DELETE /raid-profiles/me   clear the caller's profile

The catalog of meta builds lives in catalog.json next to this file. Editing that
file changes what the mod shows, with no redeploy and no client update: the mod
has no build list of its own.

Storage is SQLite in a single file, which needs no server to install and is
plenty for a guild-sized roster. Everything lives in this one module on purpose:
it is meant to be read start to finish, and ported into the real backend
endpoint by endpoint.

Run it with:

    uvicorn main:app --reload --port 8000

Then open http://127.0.0.1:8000/docs for a page that lets you call every
endpoint by hand, without writing a client.
"""

from __future__ import annotations

import json
import os
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Iterator, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, status
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel, Field, field_validator

# ── Configuration ────────────────────────────────────────────────────────────
# Everything tunable is an environment variable, so you never edit this file to
# deploy it. Defaults are the ones you want while developing on your own machine.

DATABASE_PATH = os.environ.get("RAID_PROFILES_DB", "raid-profiles.db")

# How the service decides who is calling. See the README for the trade-off.
#   "trust-header" reads the caller from request headers. Fine on your machine
#                  and inside the guild, and NOT safe on the open internet.
#   "disabled"     every request is rejected. Use it to be sure you have made a
#                  deliberate choice before exposing the service.
AUTH_MODE = os.environ.get("RAID_PROFILES_AUTH", "trust-header")

# The guild's meta: which builds exist, which raids exist, and which builds are
# meta for which raid. Edit this file to change the meta; nothing else needs to
# change, on the server or in the mod.
CATALOG_PATH = os.environ.get(
    "RAID_PROFILES_CATALOG", os.path.join(os.path.dirname(os.path.abspath(__file__)), "catalog.json")
)

KNOWN_REGIONS = {"EU", "NA", "AS"}

SCHEMA_VERSION = 1
MAX_STATUS_LENGTH = 64
MAX_BUILDS = 32

app = FastAPI(
    title="Sequoia raid profiles",
    version="1.0.0",
    description="The guild's raid meta, and what each member can bring to it.",
)


class ApiError(HTTPException):
    """
    An error the mod can branch on.

    The body is always {"code": ..., "message": ...}: `code` is for the client's
    logic, `message` is shown to the player under the Save button, so it is
    written to be read by a person.
    """

    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(status_code=status_code, detail={"code": code, "message": message})


@app.exception_handler(ApiError)
def api_error_handler(request: Request, error: ApiError) -> JSONResponse:
    return JSONResponse(status_code=error.status_code, content=error.detail)


@app.exception_handler(RequestValidationError)
def validation_error_handler(request: Request, error: RequestValidationError) -> JSONResponse:
    """
    Turns pydantic's validation report into the same two-field shape.

    Without this a rejected build key would arrive as FastAPI's nested `detail`
    array, which the mod would show to the player as raw JSON.
    """
    first = error.errors()[0] if error.errors() else {}
    message = str(first.get("msg", "That request was not valid."))
    message = message.removeprefix("Value error, ")
    return JSONResponse(status_code=400, content={"code": "invalid_request", "message": message})


# ── The catalog ──────────────────────────────────────────────────────────────


def load_catalog() -> dict:
    """
    Reads catalog.json.

    Read on every request rather than cached at start-up, because the file is
    tiny and because editing the meta should take effect immediately instead of
    after a restart someone has to remember to do.
    """
    try:
        with open(CATALOG_PATH, encoding="utf-8") as handle:
            catalog = json.load(handle)
    except FileNotFoundError as error:
        raise ApiError(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "catalog_unavailable",
            f"catalog.json is missing at {CATALOG_PATH}",
        ) from error
    except json.JSONDecodeError as error:
        raise ApiError(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "catalog_unavailable",
            f"catalog.json is not valid JSON: {error}",
        ) from error

    builds = catalog.get("builds") or []
    raids = catalog.get("raids") or []
    if not builds or not raids:
        raise ApiError(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "catalog_unavailable",
            "catalog.json needs at least one build and one raid.",
        )
    return {"builds": builds, "raids": raids}


def known_build_keys() -> set[str]:
    """The build keys a profile is allowed to name, straight from the catalog."""
    return {
        str(build.get("key", "")).strip().upper()
        for build in load_catalog()["builds"]
        if str(build.get("key", "")).strip()
    }


# ── Database ─────────────────────────────────────────────────────────────────


def connect() -> sqlite3.Connection:
    connection = sqlite3.connect(DATABASE_PATH)
    connection.row_factory = sqlite3.Row
    return connection


@contextmanager
def database() -> Iterator[sqlite3.Connection]:
    connection = connect()
    try:
        yield connection
        connection.commit()
    finally:
        connection.close()


def create_tables() -> None:
    """Creates the table if it is not there. Safe to run on every start."""
    with database() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS raid_profiles (
                username_key    TEXT PRIMARY KEY,
                username        TEXT NOT NULL,
                uuid            TEXT,
                builds          TEXT NOT NULL,
                can_bring_auras INTEGER NOT NULL DEFAULT 0,
                region          TEXT,
                status          TEXT,
                updated_at      TEXT NOT NULL
            )
            """
        )


create_tables()


# ── Who is calling ───────────────────────────────────────────────────────────


class Caller(BaseModel):
    """The member a request is acting as. The uuid is never absent."""

    username: str
    uuid: str


def current_caller(
    x_minecraft_username: Optional[str] = Header(default=None),
    x_minecraft_uuid: Optional[str] = Header(default=None),
) -> Caller:
    """
    Resolves the caller from the request.

    In "trust-header" mode this believes what the client says it is. That is
    enough for local development and for a guild-internal deployment, and it is
    not enough on the open internet: anyone who can reach the service could
    write to anyone's profile. The README explains what to replace this with.
    """
    if AUTH_MODE == "disabled":
        raise ApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            "auth_not_configured",
            "Authentication is not configured. Set RAID_PROFILES_AUTH.",
        )

    username = (x_minecraft_username or "").strip()
    if not username:
        raise ApiError(status.HTTP_401_UNAUTHORIZED, "token_invalid", "Missing X-Minecraft-Username header.")
    if not username.replace("_", "").isalnum() or not 3 <= len(username) <= 16:
        raise ApiError(status.HTTP_400_BAD_REQUEST, "invalid_request", "That is not a Minecraft username.")

    # Every stored profile must carry a uuid, because the mod keys on it: a member
    # who renames keeps the uuid, and matching by name would strand their old row.
    uuid = (x_minecraft_uuid or "").strip()
    if not uuid:
        raise ApiError(
            status.HTTP_409_CONFLICT,
            "identity_unknown",
            "The backend does not know your Minecraft account yet. Rejoin the game and try again.",
        )

    return Caller(username=username, uuid=uuid)


# ── Request and response shapes ──────────────────────────────────────────────
# These mirror RaidProfilesResponse on the client. Field names are what goes on
# the wire, so they stay snake_case.


class MinecraftIdentity(BaseModel):
    """Always carries a uuid: the mod keys profiles on it, never on the name."""

    uuid: str
    username: str


class ProfileOut(BaseModel):
    minecraft: MinecraftIdentity
    builds: list[str]
    can_bring_auras: bool
    region: Optional[str] = None
    status: Optional[str] = None
    updated_at: str


class CatalogBuild(BaseModel):
    key: str
    label: str
    position: int = 0


class CatalogRaid(BaseModel):
    key: str
    short_name: str
    api_name: str
    position: int = 0
    build_keys: list[str]


class CatalogOut(BaseModel):
    builds: list[CatalogBuild]
    raids: list[CatalogRaid]


class ProfilesResponse(BaseModel):
    schema_version: int
    catalog: CatalogOut
    profiles: list[ProfileOut]


class ProfileIn(BaseModel):
    """What a member sends when they save the setup screen."""

    builds: list[str] = Field(default_factory=list, max_length=MAX_BUILDS)
    can_bring_auras: bool = False
    region: Optional[str] = None
    status: Optional[str] = None

    @field_validator("builds")
    @classmethod
    def check_builds(cls, value: list[str]) -> list[str]:
        # Uppercase and de-duplicate, then reject anything the catalog does not
        # list, so a typo fails loudly instead of being stored.
        known = known_build_keys()
        cleaned: list[str] = []
        for raw in value:
            key = (raw or "").strip().upper()
            if not key:
                continue
            if key not in known:
                raise ValueError(
                    f"Unknown build {key!r}. Known builds: {', '.join(sorted(known))}"
                )
            if key not in cleaned:
                cleaned.append(key)
        return cleaned

    @field_validator("region")
    @classmethod
    def check_region(cls, value: Optional[str]) -> Optional[str]:
        if value is None or not value.strip():
            return None
        region = value.strip().upper()
        if region not in KNOWN_REGIONS:
            raise ValueError(f"Region must be one of {', '.join(sorted(KNOWN_REGIONS))}")
        return region

    @field_validator("status")
    @classmethod
    def check_status(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        trimmed = value.strip()
        if not trimmed:
            return None
        return trimmed[:MAX_STATUS_LENGTH]


# ── Conversion ───────────────────────────────────────────────────────────────


def row_to_profile(row: sqlite3.Row, build_order: Optional[list[str]] = None) -> ProfileOut:
    stored = json.loads(row["builds"])
    if build_order:
        # Catalog order, so two members who ticked the same builds read back the
        # same way and the mod never has to sort.
        stored = [key for key in build_order if key in stored]
    return ProfileOut(
        minecraft=MinecraftIdentity(uuid=row["uuid"], username=row["username"]),
        builds=stored,
        can_bring_auras=bool(row["can_bring_auras"]),
        region=row["region"],
        status=row["status"],
        updated_at=row["updated_at"],
    )


def now_iso() -> str:
    """ISO-8601 in UTC, which is what every timestamp in this protocol uses."""
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


# ── Endpoints ────────────────────────────────────────────────────────────────


@app.get("/raid-profiles", response_model=ProfilesResponse)
def list_profiles(response: Response) -> ProfilesResponse:
    """
    The meta catalog and every saved profile, in one response.

    They come together because the client always needs both: a profile's build
    keys mean nothing without the catalog that names them, and shipping them
    apart would only create a window where the two disagree.

    Only members who actually saved a profile appear. The client draws "has not
    shared a profile" differently from "shared one with no builds ticked", so a
    member who deliberately ticked nothing is here with an empty `builds` list.
    """
    response.headers["Cache-Control"] = "no-store"
    catalog = load_catalog()
    build_order = [str(build["key"]).strip().upper() for build in catalog["builds"]]
    with database() as connection:
        rows = connection.execute(
            "SELECT * FROM raid_profiles WHERE uuid IS NOT NULL ORDER BY username_key"
        ).fetchall()

    return ProfilesResponse(
        schema_version=SCHEMA_VERSION,
        catalog=CatalogOut(**catalog),
        profiles=[row_to_profile(row, build_order) for row in rows],
    )


@app.put("/raid-profiles/me", response_model=ProfileOut)
def save_my_profile(
    profile: ProfileIn, caller: Caller = Depends(current_caller)
) -> ProfileOut:
    """
    Saves the caller's profile, replacing whatever was there.

    `updated_at` is set here rather than taken from the request: a client clock
    that is wrong, or a client that lies, must not be able to make its profile
    look newer than everyone else's.
    """
    stored_at = now_iso()
    with database() as connection:
        # Rows are keyed by name, so a rename would otherwise leave the old row behind
        # under the same uuid, and the client (which keys on uuid) could pick it.
        connection.execute(
            "DELETE FROM raid_profiles WHERE uuid = ? AND username_key != ?",
            (caller.uuid, caller.username.lower()),
        )
        connection.execute(
            """
            INSERT INTO raid_profiles
                (username_key, username, uuid, builds, can_bring_auras, region, status, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(username_key) DO UPDATE SET
                username        = excluded.username,
                uuid            = COALESCE(excluded.uuid, raid_profiles.uuid),
                builds          = excluded.builds,
                can_bring_auras = excluded.can_bring_auras,
                region          = excluded.region,
                status          = excluded.status,
                updated_at      = excluded.updated_at
            """,
            (
                caller.username.lower(),
                caller.username,
                caller.uuid,
                json.dumps(profile.builds),
                1 if profile.can_bring_auras else 0,
                profile.region,
                profile.status,
                stored_at,
            ),
        )
        row = connection.execute(
            "SELECT * FROM raid_profiles WHERE username_key = ?",
            (caller.username.lower(),),
        ).fetchone()

    build_order = [str(build["key"]).strip().upper() for build in load_catalog()["builds"]]
    return row_to_profile(row, build_order)


@app.delete("/raid-profiles/me", status_code=status.HTTP_204_NO_CONTENT)
def delete_my_profile(caller: Caller = Depends(current_caller)) -> Response:
    """Removes the caller's profile, so they read as "has not shared one" again."""
    with database() as connection:
        connection.execute(
            "DELETE FROM raid_profiles WHERE username_key = ? OR uuid = ?",
            (caller.username.lower(), caller.uuid),
        )
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/health")
def health() -> dict[str, object]:
    """A cheap endpoint to point a monitor at, and to check the service is up."""
    with database() as connection:
        count = connection.execute("SELECT COUNT(*) AS n FROM raid_profiles").fetchone()["n"]
    catalog = load_catalog()
    return {
        "ok": True,
        "profiles": count,
        "builds": len(catalog["builds"]),
        "raids": len(catalog["raids"]),
        "auth_mode": AUTH_MODE,
    }
