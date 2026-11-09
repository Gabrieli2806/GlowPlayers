// End-to-end test: boots the dev server, joins Mineflayer bots and checks what they see.
// Usage: npm test            (add --log to echo the server console)
const { prepareRunDir, startServer, Rcon, createBot, sleep } = require('./lib')

const LOG = process.argv.includes('--log')
const bots = new Map()
const results = []
let observer // the admin bot, whose view of the others is what gets checked

function check (name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
}

async function waitFor (condition, timeout = 5000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    if (condition()) return true
    await sleep(100)
  }
  return condition()
}

async function join (name) {
  const bot = await createBot(name)
  bots.set(name, bot)
  await sleep(600)
  return bot
}

async function leave (name) {
  const bot = bots.get(name)
  bots.delete(name)
  bot.quit()
  await sleep(800)
}

/** Team the observer sees this player on, e.g. "gp_red". */
const teamOf = name => observer.teamMap[name]?.team
/** Glowing entity flag (bit 0x40 of metadata 0) as the observer sees it. */
const glows = name => {
  const entity = observer.players[name]?.entity
  return !!entity && ((entity.metadata?.[0] ?? 0) & 0x40) !== 0
}

async function expectTeams (label, expected) {
  const ok = await waitFor(() => Object.entries(expected).every(([name, team]) => teamOf(name) === team))
  const seen = Object.fromEntries(Object.keys(expected).map(name => [name, teamOf(name) ?? 'none']))
  check(label, ok, ok ? '' : 'saw ' + JSON.stringify(seen))
}

async function expectGlow (label, names, glowing, timeout = 5000) {
  const ok = await waitFor(() => names.every(name => glows(name) === glowing), timeout)
  check(label, ok, ok ? '' : names.map(name => `${name}=${glows(name)}`).join(' '))
}

async function main () {
  prepareRunDir({ freshConfig: true })
  console.log('Starting the dev server (gradlew runServer)…')
  const server = startServer({ log: LOG })
  await server.ready
  const rcon = new Rcon()
  await rcon.connect()
  const run = command => rcon.send(command)

  try {
    // ---------------------------------------------------------------- fixed group
    observer = await join('GP_Admin')
    await run('op GP_Admin')
    for (const name of ['Bot1', 'Bot2', 'Bot3', 'Bot4']) await join(name)

    await expectTeams('fixed: colors follow the palette in join order', {
      GP_Admin: 'gp_green', Bot1: 'gp_red', Bot2: 'gp_blue', Bot3: 'gp_yellow', Bot4: 'gp_light_purple'
    })
    await expectGlow('fixed: every other player glows', ['Bot1', 'Bot2', 'Bot3', 'Bot4'], true)

    await leave('Bot2')
    await join('Bot5')
    await expectTeams('fixed: offline member keeps blue reserved, newcomer gets aqua', { Bot5: 'gp_aqua' })
    await join('Bot2')
    await expectTeams('fixed: member gets the same color back after rejoining', { Bot2: 'gp_blue' })

    await run('glowplayers set Bot3 red')
    await expectTeams('fixed: forcing a taken color swaps it', { Bot3: 'gp_red', Bot1: 'gp_yellow' })

    // ---------------------------------------------------------------- team conflicts
    observer.chatLog.length = 0
    await run('team add foreign')
    await run('team join foreign Bot4')
    await expectTeams('conflict: a foreign team is left alone', { Bot4: 'foreign' })
    const warned = await waitFor(() => observer.chatLog.some(line => line.includes('Bot4') && line.includes('foreign')), 4000)
    check('conflict: admins get a chat warning', warned, warned ? '' : JSON.stringify(observer.chatLog.slice(-5)))
    await expectGlow('conflict: player still glows', ['Bot4'], true)
    await run('team leave Bot4')
    await expectTeams('conflict: back to our team once the foreign one lets go', { Bot4: 'gp_light_purple' })
    await run('team remove foreign')

    // ---------------------------------------------------------------- join order
    await run('glowplayers mode session')
    // Player list order is the join order: GP_Admin, Bot1, Bot3, Bot4, Bot5, Bot2.
    await expectTeams('session: colors follow the join order', {
      GP_Admin: 'gp_green', Bot1: 'gp_red', Bot3: 'gp_blue', Bot4: 'gp_yellow', Bot5: 'gp_light_purple', Bot2: 'gp_aqua'
    })
    await leave('Bot1')
    await join('Bot6')
    await expectTeams('session: a newcomer takes the lowest free color, nobody else moves', {
      Bot6: 'gp_red', Bot3: 'gp_blue', Bot2: 'gp_aqua'
    })

    // ---------------------------------------------------------------- chest GUI
    const window = await new Promise(resolve => {
      observer.once('windowOpen', resolve)
      observer.chat('/glowplayers')
    })
    const slotName = slot => window.slots[slot]?.name
    check('gui: main menu opens with the master switch', slotName(10) === 'beacon', `slot 10=${slotName(10)}`)
    check('gui: master switch shows enabled', slotName(19) === 'lime_stained_glass_pane', `slot 19=${slotName(19)}`)
    await observer.clickWindow(19, 0, 0)
    await waitFor(() => slotName(19) === 'red_stained_glass_pane', 3000)
    check('gui: clicking the switch disables the mod', slotName(19) === 'red_stained_glass_pane', `slot 19=${slotName(19)}`)
    await expectGlow('gui: disabling removes every glow', ['Bot2', 'Bot3', 'Bot6'], false)
    await expectTeams('gui: disabling removes every team', { Bot2: undefined, Bot3: undefined })
    await observer.clickWindow(19, 0, 0)
    await waitFor(() => slotName(19) === 'lime_stained_glass_pane', 3000)
    check('gui: clicking again enables it', slotName(19) === 'lime_stained_glass_pane')
    await expectGlow('gui: glow comes back', ['Bot2', 'Bot3', 'Bot6'], true)
    const inventoryBefore = observer.inventory.items().length
    await observer.clickWindow(10, 0, 0)
    await sleep(300)
    check('gui: nothing can be taken out of the menu', observer.inventory.items().length === inventoryBefore)
    observer.closeWindow(window)

    // ---------------------------------------------------------------- commands
    await run('glowplayers off')
    await expectGlow('command: /glowplayers off removes the glow', ['Bot2', 'Bot3'], false)
    await run('glowplayers on')
    await expectGlow('command: /glowplayers on brings it back', ['Bot2', 'Bot3'], true)
    await run('effect clear Bot2')
    await expectGlow('reapply: a cleared glow comes back', ['Bot2'], true, 8000)
  } finally {
    for (const bot of bots.values()) bot.quit()
    await sleep(500)
    await run('stop').catch(() => {})
    rcon.close()
    await new Promise(resolve => server.child.once('exit', resolve)).catch(() => {})
  }

  const failed = results.filter(result => !result.ok)
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
  process.exit(failed.length === 0 ? 0 : 1)
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
