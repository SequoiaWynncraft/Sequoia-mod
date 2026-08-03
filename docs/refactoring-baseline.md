# Refactoring Baseline

This document defines the behavior and compatibility surfaces that incremental
refactors must preserve. A behavior change discovered while refactoring should
be split into a separately reviewed fix or feature.

## Supported compatibility surfaces

- Fabric entrypoints remain `com.seqwawa.seq.Seq` and
  `com.seqwawa.seq.client.SeqClient`.
- Required and optional mixins remain declared by `seq.mixins.json` and
  `seq.wynntils.mixins.json`. Their injection requirements must not become
  stricter during a refactor.
- The Brigadier command tree rooted at `/seq`, including the standalone `/e`
  alias, is captured by `snapshots/seq-command-tree.txt`.
- Configuration keys, categories, defaults, ranges, and persisted JSON
  representations remain stable. Legacy configuration migration remains
  readable until it is removed in a dedicated migration.
- WebSocket message type names, field names, authentication rules, throttling,
  and server-scope rules remain stable. Representative wire payloads live in
  `src/test/resources/protocol`.
- Backend JSON models, theme YAML files, bundled resource identifiers, keybind
  identifiers, and updater metadata remain stable.
- No external Java API is supported. Public implementation types may be
  removed only after confirming they are absent from Java call sites, Fabric
  metadata, mixin metadata, resources, documentation, and reflective lookup.

## Client lifecycle baseline

Initialization currently proceeds in this order:

1. Create and subscribe the event bus.
2. Create managers and register Treasury OUT callbacks.
3. Load the base configuration and retire the legacy token file.
4. Initialize themes, optional integrations, commands, render features, key
   bindings, and the client tick callback.
5. After Minecraft finishes loading, initialize the UI renderer and assets,
   create and register feature settings, reload persisted values, and
   auto-connect only on a confirmed main Wynncraft server.

`MinecraftUiRenderer.shutdown()` remains registered on client shutdown.
Changing initialization order, shutdown ownership, or thread affinity requires
new lifecycle tests before the change.

## Server and connection lifecycle baseline

| Server scope | Current behavior |
| --- | --- |
| `MAIN` | Feature ticks run, outbound messages flush, and eligible automatic connection recovery may run. |
| `UNKNOWN` | Server-transfer state is retained, raid handoff safety is advanced, rendering probes reset, and outbound messages remain queued. |
| `BLOCKED` | The connection is closed without automatic reconnect; party sync, raids, guild wars, and guild storage enter their existing reset or safety paths. |

- Manual disconnect suppresses automatic reconnect until a manual connect.
- Remote closes and non-clean local closes may schedule capped exponential
  reconnect attempts; a local close with code `1000` does not.
- Normal authenticated sessions use bearer authentication. The dedicated
  Treasury OUT account uses Minecraft session proof instead.
- Capability-scoped authorization failures must not disable unrelated session
  features. Session-wide membership rejection retains its existing behavior.
- Account changes clear account-scoped state unless the existing operator
  session preservation rule applies.

## UI parity baseline

Refactors must preserve these interaction states before any renderer migration:

- Settings and Theme Editor: category selection, search, scrolling, choice and
  color editing, live preview, save/cancel, and UI scales from 75% through 150%.
- Party Finder: list/create/manage modes, invite and stale-warning actions,
  member actions, dropdowns, text input, scrolling, and modal dismissal.
- World Map: Gathering and Events modes, both sidebars independently open and
  closed, filters and dropdowns, map pan/zoom, markers and tooltips, coordinate
  copy, ingredient waypoints, and gathering-totem selection.
- Ingredient Guide: search, filters, sorting, pagination/scrolling, farm-spot
  previews, and navigation to the map.

Reference checks should cover window sizes 1280x720 and 1920x1080 at 75%,
100%, and 150% UI scale. Visual changes require before/after screenshots and
must not be folded into structural refactors.

## Client smoke test

Run this checklist on a development client after a pass that changes bootstrap,
rendering, commands, configuration, or lifecycle code:

1. Start with `./gradlew runClient` on Java 21 and reach the title screen
   without mixin, resource, or initialization errors.
2. Join a supported Wynncraft server and confirm `/seq`, `/seq status`, `/e`,
   the `O` keybind, Settings, Party Finder, World Map, and Ingredient Guide open.
3. Move through a server transfer and confirm the client does not connect while
   scope is `UNKNOWN` or `BLOCKED`.
4. Disconnect manually, wait through the reconnect interval, and confirm no
   automatic reconnect occurs; reconnect manually afterward.
5. Exit normally and confirm renderer, executor, and updater shutdown paths do
   not report errors.

When a graphical environment or Wynncraft session is unavailable, record the
unexecuted steps in the pass validation report; automated tests and build
success do not replace this runtime check.

## Changed-source hygiene

`./gradlew check` applies whitespace and newline checks plus focused Checkstyle
analysis to changed sources. Local runs compare tracked Java files with `HEAD`
and include untracked Java files. Pull-request CI derives the comparison from
`GITHUB_BASE_REF`. To reproduce that comparison locally, run:

```bash
./gradlew check -PsourceHygieneBaseRef=origin/main
```

The initial rules deliberately avoid a repository-wide formatter rewrite.
Broader formatting or analysis rules should be introduced in separate tooling
commits after the existing source tree has been normalized.
