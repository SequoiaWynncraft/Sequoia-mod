# Achievement announcements

`GuildRaidProgressService` uses its existing refresh schedule (on connection, six seconds after a local raid, and every five minutes). Eligible Sequoia players POST to the existing progress endpoint, receiving progress and authorized `announcements` in one response. Other players use GET; older backends returning 404/405 fall back to GET.

`ChatManager` sends each message through `/g`, spaced three seconds apart. The existing guild-to-Discord bridge relays it to the chatbridge channel. The backend independently posts earned achievements to Campfire after committing their state; no extra mod polling is added.

Raid messages use `Baptiste reached Diamond (1,000 raids) in TNA!`. Awarded and role-derived badges use messages such as `Baptiste earned the Gold Insignia badge!`. Baselines and deduplication live on the backend, including across reconnects and simultaneous clients.

Chat settings include **Announce new Sequoia badges tiers**, enabled by default. Disabling it switches this client's refreshes to read-only GET requests and suppresses queued guild announcements. It does not retract Campfire posts already triggered by the backend or disable announcements from another client. Raid progress remains available; re-enabling resumes achievement reporting on the next refresh.

Before dispatch, the mod checks the progress request's session generation, active account, player connection, main Wynncraft server and Sequoia membership. A lost response or a disconnect before the `/g` message is sent can miss the guild announcement; the backend Campfire post is independent. Deploy the matching backend first.
