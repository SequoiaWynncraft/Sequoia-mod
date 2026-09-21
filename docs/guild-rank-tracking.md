# Guild rank observations

Seqmod reports successful guild rank assignment system messages to the backend over the existing
authenticated WebSocket. See the backend's `docs/guild-rank-tracking.md` for the schema, delivery
and reconciliation behavior.

`GuildRankEventParser` normalizes line wrapping and extracts the actor, target, old rank and new
rank. Display names are not assumed to be usernames. It uses only unambiguous component
hover/insertion metadata (or explicit "You") for identity, retaining unresolved names as nicknames.
The same rule applies independently to the actor and target.

The server must advertise `guild_rank_tracking_supported` in its authentication response.
`GuildRankObservationQueue` assigns observation IDs, retries every five seconds until acknowledged,
expires reports after ten minutes and bounds the in-memory queue to 100 reports. Retries stay
scoped to the observing Minecraft account and the main Wynncraft server. A client process restart
clears pending reports; the API remains the fallback when no connected mod observed the action.

Tests cover the supplied multiline Strategist-to-Strategist example, actor/target component metadata,
unresolved names, ordinary chat rejection, demotions, duplicate packets and rapid rank reversals.
A live game check should verify that the current notification carries the expected hover/insertion
metadata, since a copied plain-text message cannot show those components.

## Diagnosing missing reports

A deployment being queued or building does not mean the live WebSocket has the new backend.
The authentication response must contain `"guild_rank_tracking_supported":true`. The client logs
`[GuildRank] Backend supports rank tracking` when this is present, or a warning explaining that
observations are held while support is absent. Reconnect after rollout if the existing socket has
not reauthenticated. Reports older than ten minutes expire; a later deployment cannot recover them.

The client logs recognized assignments at DEBUG, sends at INFO (`Sent observation id=...`), and
persistence acknowledgements at INFO (`Backend recorded observation id=...`). An acknowledgement
confirms database recording; Discord delivery is a separate backend queue. If recording succeeds
but no Discord message appears, check `DISCORD_MEMBERSHIP_CHANGE_LOG_CHANNEL_ID`, bot access to
that channel, and backend `Rank event ... delivery failed` warnings. Same-rank assignments are
recorded but do not generate a promotion/demotion message.

Regression coverage includes the decorated, wrapped Recruiter-to-Captain and Strategist-to-Captain
packets seen in the live Minecraft log. Component metadata is resolved independently for the actor and target.
