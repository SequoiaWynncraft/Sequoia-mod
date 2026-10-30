# Achievement announcements

`GuildRaidProgressService` refreshes on connection, six seconds after a local raid, and every five minutes.
Eligible Sequoia players POST to the progress endpoint and receive progress and authorized `announcements` in one response.
Other players use GET. A POST that returns 404 or 405 falls back to GET.

`ChatManager` sends messages through `/g`, spaced three seconds apart.
The guild-to-Discord bridge relays them to the chatbridge channel.
The backend posts earned achievements to Campfire independently after committing their state.

Raid messages use `Baptiste reached Diamond (1,000 raids) in TNA!`. Awarded and role-derived badges use messages such as `Baptiste earned the Gold Insignia badge!`. Baselines and deduplication live on the backend, including across reconnects and simultaneous clients.

Chat settings include **Announce new Sequoia badges tiers**, enabled by default. Disabling it switches this client's refreshes to read-only GET requests and suppresses queued guild announcements. It does not retract Campfire posts already triggered by the backend or disable announcements from another client. Raid progress remains available; re-enabling resumes achievement reporting on the next refresh.

Before sending, the mod checks the request's session generation, active account, player connection, main Wynncraft server, and Sequoia membership.
A lost response or disconnect before the `/g` message can miss the guild announcement. Campfire delivery is independent.
