# Sequoia

[![Release](https://img.shields.io/github/v/release/SequoiaWynncraft/Sequoia-mod?display_name=tag&style=flat-square)](https://github.com/SequoiaWynncraft/Sequoia-mod/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-3C8527?style=flat-square)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
[![License: MIT](https://img.shields.io/badge/License-MIT-2ea44f?style=flat-square)](LICENSE.txt)


**[sequoia-mod](https://modrinth.com/project/sequoia)** is a client-side Fabric mod for members of **[Sequoia](https://discord.gg/seq)**. Party finder, Discord chat bridge, account linking, and general utilities!

> This mod is built for Sequoia guild members. Backend features like the party finder, Discord bridge, and raid tracking require a linked account and won't work otherwise.


## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.141.2+1.21.11`
- Java `21`
- Wynntils

Wynntils is optional but is necessary for many features.

## Setup for new users

1. **Join the guild** - you need to be a member of [Sequoia](https://discord.gg/seq) on Wynncraft for backend features to work.
2. **Install the mod** using the steps below.
3. **Link your account** - run `/seq link` in-game on first launch to authenticate with the backend.
4. **Connect** - the mod auto-connects on startup if enabled, or run `/seq connect`.
5. **Check status** - run `/seq status` to make sure you're connected.
6. **Configure** - press `O` and open settings to toggle Discord chat, raid announcements, etc.


## Common commands

- `/seq`: open the main Sequoia screen
- `/seq connect`: connect to the backend
- `/seq link`: link or refresh backend authentication
- `/seq status`: show connection state and token status
- `/seq logout`: clear the saved token
- `/seq p list`: load current party listings
- `/seq p create <activities>`: create a party listing
- `/seq p join <listingId> [role]`: join a listing

## Settings

The settings screen has toggles for:

- Auto connect to the Sequoia backend
- Discord chat display
- Raid auto announce
- Update checks on startup

## Installation

1. Install Fabric for Minecraft `1.21.11`.
2. Put the Sequoia mod jar in your Minecraft `mods` folder.
3. Install Fabric API.
4. Install [Wynntils](https://wynntils.com) for improved class detection.
5. Start the game and press `O`, or run `/seq`.

## License

MIT:  `LICENSE.txt`.
