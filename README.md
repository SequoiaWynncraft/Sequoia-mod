# Sequoia

[![Release](https://img.shields.io/github/v/release/SequoiaWynncraft/Sequoia-mod?display_name=tag&style=flat-square)](https://github.com/SequoiaWynncraft/Sequoia-mod/releases)

Sequoia is a client-side Fabric mod for the [Sequoia guild](https://discord.gg/seq) on Wynncraft.
It connects guild chat, party finding, raid profiles, war planning, and the world map to Sequoia's services.
Guild services require a linked Minecraft and Discord account. Access depends on guild membership and Discord permissions.

## Install

Use Minecraft **1.21.11**, Java **21**, Fabric Loader **0.18.4 or newer**, and Fabric API **0.141.2+1.21.11**.

1. Install Fabric and Fabric API.
2. Download Sequoia from [Modrinth](https://modrinth.com/project/sequoia) or [GitHub releases](https://github.com/SequoiaWynncraft/Sequoia-mod/releases).
3. Put the JAR in your Minecraft `mods` folder and start the game.
4. Press `O` or run `/seq`. If prompted, use `/link` in Discord, then reconnect.

[Wynntils](https://wynntils.com) is optional. Version 4.2.9 or newer adds item-link previews in Discord and legacy war reporting.

## Documentation

- [Getting started and updating](docs/players/getting-started.md)
- [Commands](docs/players/commands.md)
- [Player guides and technical references](docs/README.md)
- [Development with mise](docs/development.md)
- [Image decoder builds](native/README.md)

## License

Sequoia uses the [MIT license](LICENSE.txt).
Bundled decoder components have separate notices under `licenses/` in the JAR.
Each release includes `sequoia-images-native-source.zip` with their corresponding source.
