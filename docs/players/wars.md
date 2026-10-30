# War planner

Open `/seq war` or select **War Planner** after the backend authorizes your Sequoia membership.
The planner loads its full snapshot when opened or explicitly refreshed.

## Availability and teams

Advertise availability with `/seq war available <minutes>` and clear it with `/seq war unavailable`.
Members can see their own team. Authorized managers can create and edit exclusive parties of one to five players.

The shared support board has one Lead and three Eco slots. These slots are separate from party membership, so a support player can also join a party.
Managers choose **HQ Team**, **VLow Munch**, or **FFA** in the editor.
The backend keeps HQ unique and assigns the numeric VLow and FFA suffixes.
Eligibility, composition rules, and edits are checked by the backend.

## Territory zones and queues

Assign named, colored territory groups to teams in **Zones**. Each zone has a full-map preview.
The map supports routes, resource coloring, and shared five-player territory queues.
Resource colors use base production, not stored resources. Ordinary 9k emerald income is the baseline, and 18k base production marks an emerald generator.

HQ and trade-style markers use [in-game observations](../cheapest-success.md) shared by members.
Right-clicking the map does not manually assign the HQ.

## Display settings

The **Wars** settings category controls planner display, tracking, and queues.
Use **HUD layout** to move the queue HUD, then set its text size, row limit, and only-my-queues filter.
The filter is shared with the planner map. Queue-miss blame messages are off by default.

The planner's opacity control changes panel backgrounds. Map imagery stays at full opacity.
Use the [theme editor](settings.md) to change individual RGBA colors.
