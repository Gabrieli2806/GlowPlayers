# GlowPlayers

GlowPlayers gives online players glow colors by join order and lets each player turn the effect on or off with a command.

## Requirements

| Item | Value |
| --- | --- |
| Minecraft | 26.1 |
| Loader | Fabric Loader 0.18.4 |
| Fabric API | 0.144.3+26.1 |
| Java | 25 |

## How It Works

Players who have glow enabled are assigned a color in join order, starting at white and cycling through the palette as more players connect. The `/glow` command lets each player enable, disable, toggle, or check their own glow state at any time. Preferences are saved to the Fabric config folder so reconnecting does not re-enable glow for players who turned it off.

## Commands

| Command | Permission Level | Description |
| --- | --- | --- |
| `/glow [on|off|toggle|status]` | All players | Enables, disables, toggles, or shows your personal glow setting. |

## Configuration

| File | What It Contains |
| --- | --- |
| `config/glowplayers/preferences.json` | The default glow setting and saved per-player glow preferences. |