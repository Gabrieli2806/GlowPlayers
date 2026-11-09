# GlowPlayers

Server-side Fabric mod: every player glows in a color of their own, and their Locator Bar dot
matches. Vanilla clients need nothing installed, the admin menu included.

## Requirements

- Minecraft 26.2 (branch `mc/26.2`; `main` targets 26.1.x)
- Fabric Loader 0.18.4+ and Fabric API
- Java 25

## How it works

The admin picks one of two color modes:

- **Fixed group** — every player owns a color for good. It stays reserved while they are offline,
  so a group of friends always sees the same colors. New players join the group on their first
  visit (or only when an admin adds them, if auto-add is off).
- **Join order** — the 1st player online gets palette color 1, the 2nd color 2, and so on. Leaving
  frees the color for the next player to join; players already online never change color.

The palette starts with the bright, easy to tell apart colors (green, red, blue, yellow, pink,
aqua, orange, white) and ends with the dark ones. Two players only share a color once all 16 are
taken. An admin can give anyone a specific color, and whoever had it swaps.

The glow color comes from a `gp_<color>` scoreboard team. If a player is already on a team the mod
does not own (a map, a minigame, another mod), that team is left alone: the glow takes its color,
the Locator Bar keeps the GlowPlayers color, and online admins get a chat warning. "Override" in
the menu moves them anyway.

The Locator Bar color is set on the waypoint icon, then the waypoint is untracked and retracked
(the same trick vanilla uses in `/waypoint modify`). That way the change reaches every player at
once instead of after a respawn, and it works with the glow switched off.

## Admin menu

`/glow` opens a chest menu where every option can be turned on or off:

- **Top row**: the mod, the glow outline, Locator Bar colors and colored names. Each has a green or
  red pane under it.
- **Middle**: color mode, auto-add, player color picking, other-team handling, conflict warnings,
  notifications.
- **Palette**: turn colors on or off and reorder them. The stack size shows the position.
- **Players**: online players, offline group members and excluded players. Each one gets a screen
  to pick a color, add them to or remove them from the group, or exclude them.
- **This dimension**, reapply interval, team cleanup, reload, and reset colors (shift-click).

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/glowplayers` or `/glow` | admin | Opens the admin menu |
| `/glow on` / `/glow off` | admin | Turns the whole mod on or off |
| `/glow mode fixed\|session` | admin | Switches the color mode |
| `/glow set <player> <color>` | admin | Gives someone a color (swaps with its owner) |
| `/glow exclude <player>` | admin | Skips a player entirely |
| `/glow refresh` / `/glow reload` | admin | Reapplies colors / re-reads the config |
| `/glow info` | everyone | Current settings and your own color |
| `/glow color <color>` | self pick | Picks your own color |
| `/glow auto` | self pick | Back to the automatic color |
| `/glow toggle` | self pick | Turns your own glow on or off |

"Admin" means permission level `adminPermissionLevel` (2 by default). "Self pick" commands need
`allowSelfColorPick`; without it they are admin only.

## Configuration

Settings live in `config/glowplayers.json`, editable from the menu or by hand (`/glow reload`).
Files from 2.x are upgraded automatically: remembered and forced colors become group members.

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch |
| `glowEffect` | `true` | Glowing outline |
| `locatorBarColors` | `true` | Colored Locator Bar dots |
| `colorPlayerNames` | `false` | Keep team colors on names when the glow is off |
| `mode` | `FIXED` | `FIXED` (group) or `SESSION` (join order) |
| `autoAddMembers` | `true` | Fixed mode: newcomers join the group by themselves |
| `overrideForeignTeams` | `false` | Move players off teams the mod does not own |
| `warnTeamConflicts` | `true` | Tell admins about those players in chat |
| `notifyMode` | `ACTION_BAR` | `OFF`, `ACTION_BAR`, `CHAT` |
| `reapplyIntervalTicks` | `100` | How often a lost glow comes back, `0` disables it |
| `allowSelfColorPick` | `false` | Lets everyone use the self pick commands |
| `cleanupTeamsOnStop` | `true` | Delete the `gp_` teams on shutdown |
| `seeFriendlyInvisibles` | `false` | Vanilla team flag |
| `adminPermissionLevel` | `2` | Permission level for the menu and admin commands |
| `palette` | all 16 | Colors handed out, in order |
| `disabledDimensions` | `[]` | Dimension ids where the mod does nothing |
| `members` | `{}` | Fixed group: UUID → `{name, color}` |
| `excludedPlayers` | `{}` | UUID → name of the skipped players |
