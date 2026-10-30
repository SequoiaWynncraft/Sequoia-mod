# Getting started

## Install and connect

1. Install Fabric for Minecraft 1.21.11 and Java 21.
2. Install Fabric Loader 0.18.4 or newer and Fabric API 0.141.2+1.21.11.
3. Download the Sequoia JAR from [Modrinth](https://modrinth.com/project/sequoia) or [GitHub releases](https://github.com/SequoiaWynncraft/Sequoia-mod/releases). Put it in `mods`.
4. Start the game and press `O` or run `/seq`.
5. Connect from the Connection screen. Automatic connection is enabled by default.
6. If the backend reports no linked account, run `/link` in Sequoia's Discord and reconnect.
7. Run `/seq status` to check the connection.

Wynntils is optional. Version 4.2.9 or newer supports item-encoding-v3 previews in Discord and legacy completed-war and queue reporting.
The main UI, live war telemetry, and active-class detection work without it.

Sequoia members can use guild integrations. Allied members and other linked players with access to the Party Finder Discord channel can use party finder.
This includes Veteran, Honoured Veteran, and Community access. A non-guild session receives one notice when Sequoia-only integrations are disabled.

## Update in game

With **Update checks on startup** enabled, the title screen checks GitHub for a release.
Run `/seq update` to open the prompt manually.

- **Update** downloads and verifies the release, then installs it. Linux and macOS install immediately. Windows installs when Minecraft closes.
- **Update + Exit** requests the update and closes Minecraft after a successful install or staging for Windows.
- **Cancel** stops the download.
- **Retry** repeats a failed attempt. The prompt keeps the error visible.

The updater checks the file's size, SHA-256, mod version, and compatibility before installing it.
Turning off startup checks stops automatic network requests. The next launch still checks the outcome of an earlier local install.

## Install an update manually

1. Download `sequoia-VERSION.jar` and its `.sha256` file from [GitHub releases](https://github.com/SequoiaWynncraft/Sequoia-mod/releases).
2. Verify the download. On Linux, run `sha256sum -c sequoia-VERSION.jar.sha256`. On macOS, run `shasum -a 256 -c sequoia-VERSION.jar.sha256`. On Windows, compare `Get-FileHash sequoia-VERSION.jar` with the checksum file.
3. Close Minecraft. Remove the old Sequoia JAR from `mods`, add the new JAR, and restart.

The previous JAR remains in `mods` with a `.previous` suffix until the updated version runs.
If an interrupted install leaves Sequoia missing, rename that backup to its original `.jar` name or install the new version manually.
Keep only one loadable Sequoia JAR in `mods`.

The game directory's `updates/sequoia-update.properties` records the last attempt.
On Windows, `updates/update-helper.log` contains the installer's output.

[Commands](commands.md) and [settings](settings.md) cover the rest of the mod.
