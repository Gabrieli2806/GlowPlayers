// Shared helpers for the GlowPlayers test bots: dev server control, RCON and bot spawning.
const fs = require('fs')
const net = require('net')
const path = require('path')
const { spawn } = require('child_process')
const mineflayer = require('mineflayer')

const ROOT = path.resolve(__dirname, '..')
const RUN = path.join(ROOT, 'run')
const HOST = 'localhost'
const PORT = 25565
const RCON_PORT = 25575
const RCON_PASSWORD = 'glowplayers-test'
// Mineflayer stops at 26.1; ViaBackwards on the dev server lets it join 26.2.
const BOT_VERSION = '26.1'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

/** Offline mode, RCON on, and optionally a fresh GlowPlayers config. */
function prepareRunDir ({ freshConfig }) {
  fs.mkdirSync(RUN, { recursive: true })
  fs.writeFileSync(path.join(RUN, 'eula.txt'), 'eula=true\n')
  const propsFile = path.join(RUN, 'server.properties')
  const props = new Map()
  if (fs.existsSync(propsFile)) {
    for (const line of fs.readFileSync(propsFile, 'utf8').split(/\r?\n/)) {
      const at = line.indexOf('=')
      if (at > 0 && !line.startsWith('#')) props.set(line.slice(0, at), line.slice(at + 1))
    }
  }
  const wanted = {
    'online-mode': 'false',
    'enable-rcon': 'true',
    'rcon.port': String(RCON_PORT),
    'rcon.password': RCON_PASSWORD,
    'server-port': String(PORT),
    'spawn-protection': '0',
    'level-type': 'minecraft\\:flat',
    'generate-structures': 'false',
    'difficulty': 'peaceful'
  }
  for (const [key, value] of Object.entries(wanted)) props.set(key, value)
  fs.writeFileSync(propsFile, [...props].map(([k, v]) => `${k}=${v}`).join('\n') + '\n')
  if (freshConfig) fs.rmSync(path.join(RUN, 'config', 'glowplayers.json'), { force: true })
}

/** Starts `gradlew runServer` and resolves once the server is done loading. */
function startServer ({ log = false } = {}) {
  const gradlew = path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')
  const child = spawn(process.platform === 'win32' ? `"${gradlew}"` : gradlew, ['runServer', '--console=plain'], {
    cwd: ROOT, shell: process.platform === 'win32' ? true : false, stdio: ['pipe', 'pipe', 'pipe'] // an open stdin: EOF on the console stops the server
  })
  const lines = []
  const onData = chunk => {
    for (const line of chunk.toString().split(/\r?\n/)) {
      if (!line) continue
      lines.push(line)
      if (log) console.log('[server] ' + line)
    }
  }
  child.stdout.on('data', onData)
  child.stderr.on('data', onData)
  const ready = new Promise((resolve, reject) => {
    const timer = setInterval(() => {
      if (lines.some(line => /Done \(\d/.test(line))) { clearInterval(timer); resolve() }
    }, 250)
    child.on('exit', code => { clearInterval(timer); reject(new Error('server exited with ' + code + '\n' + lines.slice(-30).join('\n'))) })
  })
  return { child, lines, ready }
}

/** Minimal RCON client: enough to send commands and read their reply. */
class Rcon {
  async connect () {
    this.socket = net.connect(RCON_PORT, HOST)
    this.pending = new Map()
    this.nextId = 1
    let buffer = Buffer.alloc(0)
    this.socket.on('data', data => {
      buffer = Buffer.concat([buffer, data])
      while (buffer.length >= 4) {
        const length = buffer.readInt32LE(0)
        if (buffer.length < length + 4) break
        const id = buffer.readInt32LE(4)
        const body = buffer.toString('utf8', 12, length + 2)
        buffer = buffer.subarray(length + 4)
        const resolve = this.pending.get(id)
        if (resolve) { this.pending.delete(id); resolve(body) }
      }
    })
    await new Promise((resolve, reject) => { this.socket.once('connect', resolve); this.socket.once('error', reject) })
    const auth = await this.send(RCON_PASSWORD, 3)
    return auth
  }

  send (body, type = 2) {
    const id = this.nextId++
    const payload = Buffer.from(body, 'utf8')
    const packet = Buffer.alloc(14 + payload.length)
    packet.writeInt32LE(10 + payload.length, 0)
    packet.writeInt32LE(id, 4)
    packet.writeInt32LE(type, 8)
    payload.copy(packet, 12)
    return new Promise(resolve => { this.pending.set(id, resolve); this.socket.write(packet) })
  }

  close () { this.socket?.destroy() }
}

/** Joins the dev server as an offline-mode bot and resolves once it spawned. */
function createBot (username) {
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: BOT_VERSION, auth: 'offline' })
  bot.chatLog = []
  bot.on('messagestr', message => bot.chatLog.push(message))
  bot.on('error', error => console.error(`[${username}] ${error.message}`))
  return new Promise((resolve, reject) => {
    bot.once('spawn', () => resolve(bot))
    bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)))
    bot.once('end', reason => reject(new Error(`${username} disconnected: ${reason}`)))
  })
}

module.exports = { prepareRunDir, startServer, Rcon, createBot, sleep, RUN }
