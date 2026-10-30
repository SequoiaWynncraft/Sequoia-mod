# Shared HQ, trade style, and cheapest success

Sequoia learns your actual HQ from the server's `… set the guild headquarters to …` notice or a
validated `<territory> (HQ)` entry in your guild's Territories / Select Territories menu. It reads
server menu slots only, including individual slot updates. Trade styles come from the existing
`… changed the style of <territory> to cheapest/fastest` server notices, even when eco chat filters
hide them.

Current Sequoia members automatically share these observations through the backend. The lightweight
guild-state feed refreshes every 15 seconds, so a joining client can use evidence already seen by
another member. Collection works independently of war availability, full planner screens, Wynntils,
and the flash/Easter-eggs settings. The map's shared HQ now comes from in-game observations;
right-clicking the map no longer assigns an HQ manually.

HQ and trade style expire independently six hours after observation.
The mod needs a menu or notice observation before it knows the HQ or style.
It does not infer trade style from queue duration, defense rating, or an unverified menu field.

## Cheapest success effect

After you click Attack and the server confirms the same territory's war within 15 seconds,
Sequoia shows one white flash fading over 0.9 seconds and a "CHEAPEST SUCCESS" message for 2.4
seconds if **your own HQ** is known to use cheapest. Any territory can be attacked. No sound is
played, input is not blocked, and the queue is not changed.

The effect defaults to enabled. Disable **Guild wars → Cheapest success flash** or the global
**Easter eggs** setting to hide it. Minecraft's **Screen Effects** intensity scales the entire
effect; zero hides it. Turning off visuals does not stop sharing observations.

A subsequent attack replaces the pending attempt. HQ moves, HQ style changes, loadout notices,
disconnects, world/account changes, or loss of shared-state authorization invalidate the relevant
evidence. Local observations can still establish a local HQ/style when sharing is unavailable.
Loadout notices invalidate styles without inventing a replacement style.

Reports preserve their capture time against a fixed, backend-calibrated session clock.
Requests are bounded, short-lived, and dropped across resets; offline history is not replayed.
Backend-derived data is never reported back as a new local observation. Shared and local evidence
is merged by observation time even after uploads fail. Expiry is checked independently of pending
network work, including at the point a queue could trigger the effect.

See [war planner](players/wars.md) for team, queue, and display controls.
