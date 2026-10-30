# Settings and themes

Press `O` and open **Settings**, or select **Settings** in the sidebar.
Click category headings to expand them. Search reveals matching controls without changing the saved expansion choices for that screen.
Dependent controls stay visible but disabled while their parent setting is off.

Settings cover these features.

- Automatic backend connection and startup update checks.
- Discord chat, image previews, world-name links, and raid announcements.
- World map node sources and tracked-event notifications.
- Sequoia rank badges on member nametags, off by default.
- Global UI size and theme selection.
- War tracking, planner display, and queue controls under **Wars**.
- Draggable queue and TNA beam countdown positions under **HUD layout**.
- Queue HUD text size, maximum rows, and the only-my-queues filter.
- Optional queue-miss blame messages, off by default.

## Edit a theme

Sequoia includes Default and High Contrast themes.
Open **Theme editor** to copy a theme, edit its RGBA palette, preview changes, and save without restarting.
Personal themes are `*.theme.yml` files under `config/sequoia/themes`.
The client discovers manually added files on startup.

The [theme template](../theme-template.theme.yml) lists supported keys.
The war planner's opacity slider scales panel backgrounds, while its map imagery stays at full opacity.
Use RGBA controls to change individual colors and transparency.

For UI development, see [rendering and themes](../rendering.md).
