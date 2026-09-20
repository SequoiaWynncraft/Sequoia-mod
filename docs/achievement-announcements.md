# Achievement announcements

The mod checks `/api/achievements/announcements/claim` every 30 seconds while connected to the main Wynncraft server, authenticated as the active Minecraft account and confirmed by Wynntils to be in Sequoia. Only this player's backend-confirmed changes are requested. Existing achievements are silently baselined by the backend on the first check.

When authorized, the mod sends `/g Baptiste reached Diamond (1,000 raids) in TNA!`. Awarded badges and Discord-derived Insignia/War Honour badges use messages such as `/g Baptiste earned the Community Helper badge!`. Multiple announcements are spaced at least three seconds apart. Campfire posting is handled independently by the backend; no commands are broadcast to other mods.

Responses from a previous account or connection are discarded. The mod rechecks connection and guild membership on the Minecraft thread before sending. It never guesses a tier, trusts a client-supplied count, or replays a claimed guild message after reconnecting. As a result, a disconnect or lost HTTP response immediately after claiming may miss a guild announcement. The backend still retains its Campfire delivery. No announcements can be discovered while the recipient is not using the mod.

Deploy the matching backend feature before releasing this mod. An older backend's missing endpoint results in a quiet failed check, retried after 30 seconds.
