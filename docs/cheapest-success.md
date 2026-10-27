# Cheapest success

After you click Attack and the server confirms that the same territory's war has been queued,
Sequoia shows one white flash fading over 0.9 seconds and a “CHEAPEST SUCCESS” message for 2.4
seconds if **your own HQ** is known to use cheapest. The attacked territory can be any territory.
It does not play a sound, block input, or alter the queue.

The effect defaults to enabled. Disable **Guild wars → Cheapest success flash** or the global
**Easter eggs** setting to turn it off. Minecraft's **Screen Effects** intensity scales the
entire effect; zero hides it. It works without Wynntils, war telemetry, or a backend connection.

## What the client can establish

The mod learns your HQ from the server's `… set the guild headquarters to …` notice or a verified
`<territory> (HQ)` entry in the guild's Territories / Select Territories menu. It only examines
server menu slots, excluding your inventory. Trade styles come from the server's existing
`… changed the style of <territory> to cheapest/fastest` notices, even when chat filters hide them.

**Initial style is unknown.** If HQ was already set to cheapest before joining, the effect does
not activate until its style is observed changing in the current world session. Opening the
territory menu establishes HQ identity but does not establish trade style. To establish both,
open the guild territory menu and observe an HQ style-change notice (or observe both an HQ move
and its style change). The mod never guesses style from queue duration or defense rating.

Only your own local Attack click arms the effect. A matching server success must arrive within
15 seconds, and the HQ must still have the same known-cheapest state. The effect fires once.
Another attack replaces the pending attempt. HQ moves, HQ style updates, loadout notices,
disconnects, world changes, or disabling the feature invalidate pending evidence. A loadout
notice clears known styles because a loadout may change style without a separate style notice.

The visual and packet hooks still require an in-game check. Automated coverage exercises the
known server notice formats, HQ discovery, style transitions, queue confirmation, timeout,
duplicates, reset, and flash intensity/fade behavior.
