![image](https://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/auto-split-manager)
# Auto Split Manager

Auto Split Manager is a RuneLite plugin for tracking OSRS group splits without relying on a separate spreadsheet during the raid, trip, or PK session.

It keeps a session roster, records loot manually or from chat, resolves alts to mains, tracks roster changes across the session, and shows who owes or should receive at the end.

## What It Does

- Start and stop split sessions from the sidebar
- Add or remove players from the active roster
- Link alt accounts to mains so loot is attributed correctly
- Add values manually with OSRS-style amounts like `300k`, `12.5m`, or `1b`
- Detect values from clan chat or friends chat
- Queue detected values for review or auto-apply them when the player is already in session
- Recalculate fair splits when the roster changes mid-session
- Show settlement totals and suggested payouts
- Reopen finished sessions from history
- Open a popout dashboard with live graphs and session stats

## Screenshot Placeholders

> Image placeholder: main sidebar with session controls, detected values, and settlement table
>
> Suggested file: `docs/images/sidebar-overview.png`

> Image placeholder: detected values flow showing queued chat drops and manual apply/remove actions
>
> Suggested file: `docs/images/detected-values.png`

> Image placeholder: popout dashboard with session graph and summary stats
>
> Suggested file: `docs/images/popout-dashboard.png`

> Image placeholder: history picker with a finished session loaded
>
> Suggested file: `docs/images/history-mode.png`

## Typical Flow

### 1. Start a session

Start a new session from the plugin panel before the trip begins.

### 2. Build the player list

Add known players once, then add them to the active session when they are part of the split. You can also add players from the in-game chat right-click menu.

If a player uses multiple accounts, link those alts to their main. Drops, pending values, and split math resolve through the main account.

### 3. Record loot

You can record loot in three ways:

- Manual entry from the `Add split to session` section
- Chat-detected PvM or PvP loot messages
- Chat `!add` commands such as `!add 100`, `!add 1.2m`, or `!add 100, 200m 300k`

When auto-apply is off, detected values land in the `Detected values` queue first so you can review them. When auto-apply is on, values are applied immediately if the player is already in the active roster.

### 4. Let the plugin handle roster changes

If people join late or leave early, update the session roster. The plugin keeps the split thread intact and recalculates the settlement based on who was active for each part of the session.

### 5. Review settlement

The settlement section shows totals, split values, and payout guidance. Negative split values mean that player owes. Positive split values mean that player should receive.

You can also copy settlement output in Markdown for sharing.

### 6. Review history or use the popout

Finished sessions can be loaded from the `View history` section. This is useful for checking old runs, reviewing totals, or opening those sessions in the graph dashboard.

The popout window gives you a wider view with the normal controls on the left and graphs on the right.

## Main Features

### Chat Detection

The plugin can listen to clan chat and friends chat for:

- PvM drops: `received a drop: ... (N coins)`
- PvP loot: `has defeated ... and received (N coins) worth of loot!`
- Player commands: `!add ...`

Detected amounts are stored as raw coin values, even if you enter them with `k`, `m`, or `b`.

### Session-Aware Split Tracking

The plugin does not treat the whole trip as one flat roster. When the team changes after loot has already been recorded, it tracks the session in segments so the final split stays fair.

### Alt-To-Main Resolution

Known alts can be linked to a main account. This prevents split totals from being fragmented across different character names.

### Popout Dashboard

The popout dashboard adds a wider working view and session graph modes:

- `GP/hr over time`
- `Highest earnings`
- `Settlement balance`

It also shows quick session stats such as total loot, GP/hr, and top earner.

### Session History

Stopped sessions are kept in history and can be reopened later. This makes it possible to review past sessions without keeping separate notes outside the plugin.

## Configuration Highlights

### General

- Guided tour toggle
- Default value multiplier for shorthand manual entry
- Custom time and date formats

### Settlement

- Discord-friendly Markdown copy
- Settlement display options

### Chat Detection

- Master enable/disable toggle
- Separate clan chat and friends chat toggles
- PvM, PvP, and `!add` detection toggles
- Auto-apply for players already in session
- Regex overrides for advanced chat parsing setups

## Notes

- This plugin is aimed at the user running the split and keeping the active session accurate.
- It is most useful when the roster is updated as people join or leave instead of fixing everything after the trip.
