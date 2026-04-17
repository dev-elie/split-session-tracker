# Auto Split Manager

A RuneLite plugin for automatically managing and tracking splits in group activities.

## Features

- **Automatic Value Detection**: Detect drop values from chat channels (Clan/Friends Chat) and queue them
    - **Detects PvM drops**: Detects drop values from PvM drop messages
    - **Detects PvP loot**: Detects loot values from PvP loot messages
    - **Detects player !add**: Detects values from player `!add` commands in Clan/Friends chat
    - [ ] TODO -> **Detects through item drop name**: Detects values from through item drop names
- **Player Management**: Keep track of current participating players with support for alt accounts
- **Split Calculation**: Automatically calculates fair splits based on session participation
- **Settlement Guidance**: View metrics to settle payments between players
- **Popout Dashboard**: Open the plugin in a wider standalone window with the normal controls on the left and live graphs on the right
    - **GP/hr over time**: Tracks cumulative GP/hr and refreshes the live rate once per minute while the popout is open
    - **Highest earnings**: Shows a bar chart of player loot totals
    - **Settlement balance**: Shows who should receive and who owes
    - **Show sleeping players**: Toggle inactive/sleeping players in graph modes from the graph controls
- **Synced Panels**: Changes made in the sidebar or popout refresh both views
- **Fast Form Entry**: Press Enter in form fields and dropdowns to submit the related safe action
- **Stable Dropdowns**: Player dropdowns keep the selected player after refreshes unless that player is no longer valid

## How It Works

### Step 1: Start a session
- Using the plugin panel, start a new session

### Step 2: Add players to your known player list
- **Option A**: Go to your Chat/Friends/Clan channel, right-click a player and select `Add to session`
- **Option B**: In the plugin panel's `Known player info` section, type a player's name in the text box

### Step 3: Add players to your current session
- **Option A**: If you used Step 2 Option A, the player is already added to the session
- **Option B**: In the `Add players to session` section, select the player from the dropdown and click `Add to session`

### Step 4: Add splits to the session
- **Manual method**: In the `Add split to session` section, select a player, enter an amount (e.g. 300k, 3.4m, 1.23b), and click `Add`
- You can press Enter in the amount field or supported player dropdowns to submit the same action as the button
- **Semi-automatic method**:
    1. In settings under `Chat Detection Settings`, enable `Chat detection` and disable `Auto-apply when in session`
    2. When drops are announced in Chat, they'll appear in the `Detected values` section
    3. Select the item and click `Add` (or `Del` to remove)
- **Fully automatic method**:
    1. In settings under `Chat Detection Settings`, enable both `Chat detection` and `Auto-apply when in session`
    2. Drops will be automatically added for players in the session

### Step 5: Manage players in the session
- **Option A**: Right-click a player in Chat/Friends/Clan channel and select `Remove from session`
- **Option B**: In the plugin panel's `Settlement` section, click the `X` next to a player's name

### Step 6: View settlements
- Settlement calculations appear automatically in the plugin panel under the `Settlement` section
- Click `Pop Out` to open a standalone dashboard. The left side contains the normal plugin controls and the right side contains graph modes.
- In the popout dashboard, choose between `GP/hr over time`, `Highest earnings`, and `Settlement balance`.
- Use `Show sleeping players` on the graph side to include or hide inactive players in the graph.
- The sidebar and popout stay synced after adding splits, editing detected values, changing recent splits, or changing session players.

### Step 7: Stop the session
- Click the Stop button in the plugin panel when finished

## Configuration Options

### General Settings

- **Enable tour**: Enables the tour/explanation of the plugin
- **Popout**: The plugin panel includes a `Pop Out` button that opens the dashboard window
- **Default value**: The plugin allows the user to select the default value they want to use when inputting splits, meaning that if "m, aka a million" is selected 1 is calculated as 1,000,000

### Settlement Settings

- **Copy for Discord**: Format Markdown tables with proper spacing for Discord
- **Direct payments**: Toggle between direct player payments or using a middleman/bank
- **Flip settlement sign**: Change perspective of settlement values (+ means bank pays player vs. player pays bank)

### Chat Detection Settings

- **Enable chat detection**: Master toggle for detecting values from chat
- **Detect in Clan/Friends Chat**: Choose which chat channels to monitor
- **Detect PvM/PvP values**: Toggle automatic detection of drop and loot messages
- **Detect player !add**: Allow players to queue values via chat commands
- **Auto-apply when in session**: Automatically apply detected values for players already in session

## Alt Account Management

The plugin supports linking alt accounts to main accounts, ensuring that values are properly attributed regardless of
which character is active. All alts are automatically resolved to their main accounts when calculating splits.

## Contribute

This plugin is under active development. Contributions, bug reports, and feature requests are welcome.
