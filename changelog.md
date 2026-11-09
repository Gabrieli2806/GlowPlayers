## [3.0.0] - 2026-10-27
### Added
- Fixed group mode: every player owns a color, reserved while they are offline
- Join order mode: colors follow join order, freed on leave, never reshuffled
- Picking a taken color swaps it with its owner
- Foreign scoreboard teams are respected and reported to admins in chat (optional override)
- `/glow on`, `/glow off` and `/glow mode fixed|session`
- Players menu lists offline group members and excluded players
- Mineflayer bot test suite under `testing/`
- GitHub Actions build
### Changed
- Redesigned chest menu: framed layout, switches with on/off panes, glint on active options
- Palette ordered like player colors (bright colors first); stack size shows the position
- Join order, alphabetical, name hash and random modes replaced by the two modes above
- Config upgraded to version 3; 2.x files are migrated automatically
### Fixed
- Newly joined players did not glow until the next refresh
- The mod's glow is removed on logout and shutdown, so it never stays in player data
- Glow effects from other sources are no longer removed
