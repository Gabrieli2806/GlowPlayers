# GlowPlayers

GlowPlayers gives online players colored glow effects in join order and lets each player turn their own glow on or off.

## Requirements

| Item | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| Server | Paper or Spigot 1.21.1 |
| Java | 21 |

## How It Works

Players with glow enabled are placed into color teams in the order they joined the server, starting with white for the first player and cycling through the palette after that. The `/glow` command lets any player enable, disable, toggle, or check their own glow state. Player preferences are saved so the setting survives reconnects and restarts.

## Commands

| Command | Permission Level | Description |
| --- | --- | --- |
| `/glow [on|off|toggle|status]` | All players | Enables, disables, toggles, or shows your personal glow setting. |

## Configuration

| File | What It Contains |
| --- | --- |
| `plugins/GlowPlayers/config.yml` | The plugin language and the default glow state for new players. |
| `plugins/GlowPlayers/player-data.yml` | Saved per-player glow preferences. |