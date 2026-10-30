# Commands

Press `O` or run `/seq` to open the menu. The sidebar links to the same screens.

| Command | Action |
| --- | --- |
| `/seq p`, `/seq party` | Open party finder. |
| `/seq achievement` | Open achievements. |
| `/seq map` | Open the world map. |
| `/seq members`, `/seq member` | Open the guild members panel. |
| `/seq war` | Open the war planner, when authorized. |
| `/seq war available <minutes>` | Advertise availability for 1 to 1,440 minutes. |
| `/seq war unavailable` | Clear war availability. |
| `/seq connect` | Connect to the backend. |
| `/seq disconnect` | Disconnect. |
| `/seq status` | Show connection state. |
| `/seq connected` | List users connected to the backend. |
| `/seq logout` | Clear the backend session. |
| `/seq update` | Check for an update and show the prompt. |
| `/seq request aspects` | Request aspects from the guild reward queue. |
| `/seq request tome <reason>` | Request a tome from the guild reward queue. |
| `/allyraids [minutes]`, `/seq allyraids [minutes]` | Show raids shared with current allies in the last 20–120 minutes (default: 30). Requires Sequoia membership. |
| `/seq ignore <IGN>` | Hide Discord bridge messages from a Minecraft username. |
| `/seq unignore <IGN>` | Show that username's bridge messages again. |
| `/seqimages preview <url>` | Add a previewable image link to chat. |
| `/seqimages clear` | Clear cached previews and cancel pending ones. |

## Party finder

Use the screen for ordinary party management. These commands provide the same actions.

```text
/seq party list
/seq party status
/seq party create <activities>
/seq party update <activities>
/seq party join <listingId>
/seq party join <listingId> token <inviteToken>
/seq party join <listingId> <role>
/seq party join <listingId> <role> token <inviteToken>
/seq party deny <listingId>
/seq party leave
/seq party invite <username>
/seq party reserve <count>
/seq party open
/seq party close
/seq party disband
/seq party role <role>
/seq party kick <username>
/seq party promote <username>
/seq party scan
/seq party invite-all
```

## Map analysis

```text
/seq map params
/seq map eps <blocks>
/seq map minSamples <count>
/seq map reset
/seq map debug
```
