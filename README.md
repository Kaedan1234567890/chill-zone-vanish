# Chill Zone Homes 0.4.0-alpha-shards-fix9-baltop

Minecraft 26.2 Fabric server-side homes + Shards update.

## Shards
- 1 Shard per full 5 minutes online (AFK time counts).
- Balance persists in `config/chill-zone-shards.json`.
- Clean per-player sidebar: player name + Shards.

## Home progression
- Homes 1-3 are free/default.
- Home 4 costs 100 Shards.
- Every next home costs 50 more, through Home 28 (1,300 Shards).
- Click the next locked barrier in `/home` to buy it permanently.
- Staff `/homes limit <player> <amount>` remains a free administrative override and now accepts 1-28.
- Existing home data is preserved.

## Fix 2 build cleanup
- Restores the MIT `LICENSE` file from the known-good Homes source.
- Explicitly excludes the unrelated `com/chillzone/vanish/**` package if an old copy is still sitting in the GitHub repository.
- This prevents stale Vanish source files from breaking the Homes build.

## Shard admin commands
Permission: `chillzonehomes.command.shardsadmin`

- `/shards give <player> <amount>`
- `/shards set <player> <amount>`
- `/shards take <player> <amount>`
- `/shards balance <player>`

These commands are intended for Owner/Admin testing and management. The target player must be online.

## Fix 4 — staff home-limit override
- `/homes limit <player> <amount>` now always overrides shard-purchased limits, even when the staff limit is lower.
- Example: if a player purchased 8 homes and staff sets `/homes limit Player 4`, the player is limited to 4 homes.
- `/homes limit <player> reset` removes the staff override, allowing the player's normal shard-purchased limit to apply again.
- No existing homes, shard balances, or purchased-home progress are deleted.


## Shard payments (fix6)
Players can now transfer their existing Shards directly to other online players:

- `/pay <player> <amount>`
- `/shardpay <player> <amount>`

Both commands do the same thing. Payments fail if the sender does not have enough Shards or tries to pay themselves. Both players' shard sidebars update immediately after a successful transfer.


## Fix 7 — pay command compile fix
- Corrected the command-registration closing parenthesis for `/pay`.
- `/pay <player> <amount>` and `/shardpay <player> <amount>` remain unchanged.
- Player arguments continue to use Minecraft player-name suggestions/autocomplete.


## Fix 8 — `/bal` Shard leaderboard
- `/bal` opens a 6x9 Shard-balance leaderboard.
- Up to 28 players appear per page, richest at the top-left and lowest balance at the bottom-right.
- Player entries use player-head icons; hovering shows the saved player name, Shard balance, and rank.
- A next-page arrow appears in the bottom-right only when more than 28 players exist.
- On page 2+, a previous-page arrow appears in the bottom-left.
- `/bal <playerName>` privately reports that player's saved Shard balance in chat.
- Online player names autocomplete. Offline names can still be typed manually after the mod has seen that player at least once.
- Existing `chill-zone-shards.json` data remains compatible; usernames are remembered alongside existing UUID records as players join.


## Balance commands (Fix 9)
- `/bal` — privately shows your own Shard balance in chat.
- `/bal <player>` — privately shows that player's Shard balance. Online player names autocomplete; saved offline names can still be typed manually.
- `/baltop` — opens the paginated Shard leaderboard GUI, richest to poorest, 28 players per page.


## Fix 10 - /baltop names + player skins
- `/baltop` now uses player heads tied to each player's actual profile instead of generic Steve heads.
- Online Java players use their live GameProfile, so their current Java skin is shown.
- Online Floodgate/Geyser players use the live server profile; when Geyser exposes a converted skin texture, that texture is used. Otherwise Minecraft falls back to the appropriate profile/default skin rather than forcing one Steve head for everyone.
- Offline player names are backfilled from the vanilla `usercache.json`, so older Shard balances can recover names without requiring every player to be online at the same time.
- Saved names continue to work after a player disconnects.

## Fix 11 — sidebar play time

- Keeps all Fix 10 `/bal`, `/baltop`, player-name, player-head, `/pay`, Homes, and Shard features unchanged.
- `/baltop` still shows 28 players per page, with a Next Page arrow when needed and a Previous Page arrow on page 2+.
- Adds **Playtime** directly under **Shards** on each player’s sidebar.
- Shard value is displayed in **light purple**.
- Play-time value is displayed in **yellow**.
- Uses Minecraft's existing `play_time` statistic, so existing player play time is retained rather than starting over with this update.
- Time formats as minutes, then hours, then days. Days never convert to months (for example `30d 5h`).
- Sidebar refreshes once per minute; Shard generation remains exactly 1 Shard every 5 minutes online.


## Fix 12 — Minecraft 26.2 BalanceMenu compile fix

- Fixes the `cannot find symbol: method getServer()` errors in `BalanceMenu.java`.
- Uses `viewer.level().getServer()` for Minecraft 26.2 server access.
- Keeps all Fix 11 features unchanged, including `/bal`, `/baltop`, player heads/skins, play time, Shard colours, payments, Homes, and 1 Shard every 5 minutes.


## Fix 13 — Baltop play time + play-time admin controls

- `/baltop` GUI title is now **Chill Zone SMP Shard Baltop**.
- Hovering a player head now shows: player name, Shards, Time Played, then Rank.
- Shard values in the hover text are light purple and play-time values are yellow.
- Players with both 0 Shards and 0 effective play time are omitted from `/baltop`. A player with either Shards or play time still appears.
- Ranking remains primarily richest-to-poorest by Shards; ties use play time, then name.
- Existing next/previous page arrows remain unchanged: 28 player entries per page, Next at bottom-right when needed, Previous at bottom-left on page 2+.
- Adds staff play-time controls under the existing `chillzonehomes.command.shardsadmin` permission:
  - `/playtime give <player> <amount> <minutes|hours|days>`
  - `/playtime set <player> <amount> <minutes|hours|days>`
  - `/playtime take <player> <amount> <minutes|hours|days>`
  - `/playtime balance <player>`
- Example to wipe a test player's displayed play time: `/playtime set PlayerName 0 minutes`. If that player also has 0 Shards, they disappear from `/baltop`.
- Play-time adjustments are stored as an offset alongside the existing shard data, so the player's vanilla Minecraft statistic is not destructively rewritten. Time continues accumulating normally after an adjustment.

## Fix 15 — Playtime label
- Renames the sidebar label from **Time** to **Playtime**.
- Keeps the yellow playtime value, purple Shard value, 5-minute Shard generation, and all other Fix 14 behaviour unchanged.
