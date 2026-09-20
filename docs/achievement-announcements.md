# Achievement announcements

`GuildRaidProgressService` uses its existing refresh schedule (on connection, six seconds after a local raid, and every five minutes). Eligible Sequoia players POST to the existing progress endpoint, receiving progress and authorized `announcements` in one response. Other players use GET; older backends returning 404/405 fall back to GET.

`ChatManager` sends each message through `/g`, spaced three seconds apart. The existing guild-to-Discord bridge posts it to Campfire. No independent polling manager or Discord delivery path is added.

Raid messages use `Baptiste reached Diamond (1,000 raids) in TNA!`. Awarded and role-derived badges use messages such as `Baptiste earned the Gold Insignia badge!`. Baselines and deduplication live on the backend, including across reconnects and simultaneous clients.

Before dispatch, the mod checks the progress request's session generation, active account, player connection, main Wynncraft server and Sequoia membership. A lost response or a disconnect before the `/g` message is sent can miss the announcement in both destinations. Deploy the matching backend first.
