# Party finder

Run `/seq p` or select **Partyfinder** in the sidebar.
You need a linked account and either Sequoia or allied guild membership, or access to the Party Finder Discord channel.

Create a listing, choose its activities, and invite players or let them join an open slot.
Expanded cards show a class icon beside each member. Collapsed cards show a row of icons.
The mod updates your detected class when you switch classes. Other members' icons use the listing data.

## Sync a Wynncraft party

`/seq party scan` imports the leader's current Wynncraft party into the active listing.
Players with verified account links and party-finder access become listing members.
The leader can assign their roles, remove them, or transfer leadership.

Unlinked, ineligible, conflicting, or recently kicked players count as observed occupancy.
They remain visible but do not receive listing-member permissions. A later sync can promote an eligible player after their account is linked.
Scanning does not create or verify account links.

Inviting from [Player List](members.md) enables and saves **Sync with Wynn party**.
If the mod has not observed the current party, the leader requests a roster scan.
Accepted invites and subsequent party changes then follow the automatic sync setting.

See [commands](commands.md#party-finder) for command-line party management.
