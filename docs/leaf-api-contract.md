# Leaf API contract

`src/test/resources/contracts/leaf-api-v1.json` comes from
[`sequoia-leaf`](https://github.com/SequoiaWynncraft/sequoia-leaf)'s `contracts/leaf-api-v1.json`.
Leaf owns it: its contract test calls Leaf's real routes and fails whenever their answers
differ from the file, so the file is what Leaf sends, down to the order of each list.

`LeafContractTest` reads the mod's copy through the production code: the account, quests and
reroll answers, the Recent list, every event type's row and popup, Leaf's refusal codes, and
the reroll request the mod builds. Test the mod's reading of Leaf against this file, not
against answers written by hand: hand-written answers only repeat what the mod expects. The
Recent list once showed day-old events because its tests listed events newest first, while
Leaf sends them oldest first.

When Leaf changes what a route sends, copy its file here and run `mise run test`. See Leaf's
README, "The contract clients test against", for the Leaf side.
