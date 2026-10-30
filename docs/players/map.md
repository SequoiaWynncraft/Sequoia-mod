# World map

Run `/seq map`. The dropdown near the top-left selects **Gathering**, **Events**, or **Ingredients**.
**Close Map** returns to the main menu, including when opened directly with the command.

The left sidebar contains map, analysis, filter, display, and tracking controls.
Each group folds independently. Selection details and insights appear in the collapsible right sidebar.

## Gathering

Analyze all nodes, nodes in any guild territory, or nodes in the selected territory.
Resource, profession, cluster, and score filters refine the selected scope.

Choose **Static file** or **Wynn API** with **Node source** in the **Map & Territory** panel.
The same setting appears as **Gathering node source** under **World Map** in Settings.
The selection persists across restarts.

- **Static file** is the default. It uses bundled nodes without requesting node data online.
- **Wynn API** loads nodes in the background, refreshes hourly, and retries failed requests after one minute while Gathering is open.

Each source keeps separate data and status. API failures retain the last successful API data.
Loading or failure status appears on an empty map. Insights show the selected source's status.

## Events

The map shows runs visible through the Wynncraft API.
Choose **All** or **Tracked** to filter markers. Click a marker for details and **Track Event** to save it.
The searchable tracking dropdown also manages tracked events. **Tracked Only** narrows that management list.

Enable tracked-event detection messages under **World Events** in Settings.
The default-enabled **Collect world event rewards** setting shares observed end-chest rewards.
See [reward collection](../world-event-loot.md) for captured fields and timing.
