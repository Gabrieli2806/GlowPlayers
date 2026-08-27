# GlowPlayers

Server-side Fabric mod. Every player gets a color of their own: a glowing outline you can see
through walls, and a matching **Locator Bar** waypoint at the top of the screen. Nothing has to be
installed on the client — vanilla players see everything, including the admin menu, because it is
built out of a plain chest GUI.

## Features

- Unique glow color per player, out of the 16 Minecraft colors.
- Locator Bar waypoints painted with the same color, and **kept in sync** — colors update the moment
  they change instead of waiting for someone to die (see [issue #1](https://github.com/Gabrieli2806/GlowPlayers/issues/1)).
- The Locator Bar colors work on their own: turn the glow outline off and the bar stays colored.
- In-game admin menu (`/glowplayers`) built from chest GUIs — no config file editing needed.
- Four ways to hand out colors: join order, alphabetical, name hash or random.
- Colors can be remembered between sessions, forced per player, or left automatic.
- Editable palette: switch colors off or reorder them.
- Per-player exclusions and per-dimension exclusions.
- Everything is stored in `config/glowplayers.json`, which can also be edited by hand.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/glowplayers` or `/glow` | admin | Opens the admin menu |
| `/glowplayers info` | everyone | Current settings and your own color |
| `/glowplayers color <color>` | see below | Forces your own color |
| `/glowplayers auto` | see below | Back to the automatic color |
| `/glowplayers toggle` | see below | Turns your own glow on or off |
| `/glowplayers set <player> <color>` | admin | Forces someone's color |
| `/glowplayers exclude <player>` | admin | Skips a player entirely |
| `/glowplayers refresh` | admin | Recomputes every color |
| `/glowplayers reload` | admin | Re-reads the config file |

The three player commands need `allowSelfColorPick` to be on; otherwise they are admin only.
"Admin" means permission level `adminPermissionLevel` (2 by default).

## The admin menu

`/glowplayers` opens a chest with every setting on it. Left click toggles or cycles a setting, and
the reapply interval also takes a right click to go down.

- **Palette** — pick which of the 16 colors get handed out, right click a color to move it first.
- **Players** — a head per online player: force a color, hand it back to the automatic mode, or
  exclude them.
- **This dimension** — switch the mod off in the dimension you are standing in.
- **Reset colors** — forget every remembered and forced color.

## Configuration

`config/glowplayers.json`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch |
| `glowEffect` | `true` | Glowing outline |
| `locatorBarColors` | `true` | Colored Locator Bar waypoints |
| `colorPlayerNames` | `false` | Keep team colors on names when the glow is off |
| `assignMode` | `JOIN_ORDER` | `JOIN_ORDER`, `ALPHABETICAL`, `NAME_HASH`, `RANDOM` |
| `notifyMode` | `ACTION_BAR` | `OFF`, `ACTION_BAR`, `CHAT` |
| `reapplyIntervalTicks` | `100` | How often a lost glow is restored, `0` disables it |
| `persistColors` | `true` | Players keep their color between sessions |
| `allowSelfColorPick` | `false` | Lets everyone use the player commands |
| `cleanupTeamsOnStop` | `true` | Delete the mod's scoreboard teams on shutdown |
| `seeFriendlyInvisibles` | `false` | Vanilla team flag |
| `adminPermissionLevel` | `2` | Permission level for the menu and admin commands |
| `palette` | all 16 | Colors that get handed out, in order |
| `disabledDimensions` | `[]` | Dimension ids where the mod does nothing |
| `playerColors` | `{}` | UUID → forced color |
| `excludedPlayers` | `[]` | UUIDs the mod skips |
| `assignedColors` | `{}` | UUID → remembered color |

## How the colors reach the client

The glow outline takes its color from a scoreboard team, so the mod puts each player on a
`gp_<color>` team. The Locator Bar takes its color from the waypoint icon, which the server
snapshots when a waypoint connection is created — which is why a plain team change never showed up
until a connection happened to be rebuilt, usually after a death. GlowPlayers sets the icon color
directly and then untracks and retracks the waypoint, the same trick vanilla uses in
`/waypoint modify`, so the new color reaches everyone immediately.

Because the color lives on the icon rather than on the team, the Locator Bar keeps working with the
glow outline switched off.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Requires JDK 25.

## Versions

| Branch | Minecraft |
| --- | --- |
| `main` | 26.1.x |
| `mc/26.2` | 26.2 |

## License

MIT — see [LICENSE](LICENSE).
