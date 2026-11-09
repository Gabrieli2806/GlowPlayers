// Fills the dev server with bots so the mod can be checked by hand from a real client.
// Usage: npm run bots -- [count]        then join localhost from Minecraft 26.2
// Type "<bot> <message>" to make a bot chat (e.g. "Bot1 /glowplayers color red"),
// or "<message>" alone to make every bot say it. Ctrl+C to quit.
const readline = require('readline')
const { createBot, sleep } = require('./lib')

async function main () {
  const count = Number(process.argv[2] ?? 5)
  const bots = new Map()
  for (let i = 1; i <= count; i++) {
    const name = 'Bot' + i
    const bot = await createBot(name)
    bots.set(name, bot)
    bot.on('messagestr', message => console.log(`[${name}] ${message}`))
    // Wander around a little so the glow and the Locator Bar are easy to see.
    const wander = setInterval(() => {
      bot.look(Math.random() * Math.PI * 2, 0)
      bot.setControlState('forward', Math.random() < 0.5)
      bot.setControlState('jump', Math.random() < 0.2)
    }, 1500)
    bot.once('end', () => clearInterval(wander))
    console.log(`${name} joined`)
    await sleep(500)
  }

  const input = readline.createInterface({ input: process.stdin })
  input.on('line', line => {
    const [first, ...rest] = line.trim().split(' ')
    if (bots.has(first)) bots.get(first).chat(rest.join(' '))
    else if (line.trim()) bots.forEach(bot => bot.chat(line.trim()))
  })
  process.on('SIGINT', () => { bots.forEach(bot => bot.quit()); setTimeout(() => process.exit(0), 300) })
}

main().catch(error => { console.error(error); process.exit(1) })
