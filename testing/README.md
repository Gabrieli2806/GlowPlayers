# Test bots

Mineflayer bots that join the dev server (`gradlew runServer`) to test GlowPlayers in game.
Mineflayer speaks up to 26.1, so the dev server also loads ViaFabric, ViaVersion and ViaBackwards
(dev runtime only, never in the built jar).

Setup once: `npm install` in this folder (Node 18+). `JAVA_HOME` must point to a JDK 25.

## Automatic test

`npm test` (add `-- --log` to see the server console). It resets `run/config/glowplayers.json`,
turns on offline mode and RCON in `run/server.properties`, starts the server, then joins
`GP_Admin` and `Bot1`… to check:

- fixed group colors, reservation of offline members, swaps
- foreign team conflicts and the admin warning
- join order mode and freed slots
- the chest menu (opening, toggling the mod off and on, nothing can be taken out)
- `/glow on|off` and the glow coming back after `/effect clear`

It exits with 0 when every check passes.

## Manual testing

1. `gradlew runServer` (or `runClient` and open the world to LAN on port 25565).
2. `npm run bots -- 5` joins Bot1…Bot5, which wander around.
3. Join `localhost` from a 26.2 client and `/op` yourself to use `/glow`.

In the bots terminal, `Bot1 /glow color red` makes one bot chat; a line without a bot name is
sent by all of them.
