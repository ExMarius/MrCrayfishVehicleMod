/**
 * Un personaj = un bot care joaca pe server ca un jucator real + creierul lui.
 * Actiunile permise sunt un set FIX de comenzi, verificate de cod.
 */
import mineflayer from 'mineflayer';
import pathfinderPkg from 'mineflayer-pathfinder';
import { Brain } from './brain.js';
import { Memory } from './memory.js';

const { pathfinder, Movements, goals } = pathfinderPkg;
const { GoalFollow } = goals;

const HOSTILE = ['zombie', 'skeleton', 'creeper', 'spider', 'enderman', 'witch',
  'drowned', 'husk', 'stray', 'pillager', 'slime', 'phantom', 'ravager'];

export class Character {
  constructor(definition, settings, memoryDir) {
    this.definition = definition;
    this.settings = settings;
    this.memory = new Memory(memoryDir, definition.id);
    this.brain = new Brain({ persona: definition, memory: this.memory, model: settings.model });
    this.bot = null;
    this.mode = 'follow';         // follow | stay | idle
    this.paused = false;          // regie: opreste AI-ul fara sa deconecteze
    this.lastSpoken = 0;
    this.lastDangerAt = 0;
    this.lastFollowAt = 0;
  }

  get name() {
    return this.definition.name;
  }

  start() {
    this.bot = mineflayer.createBot({
      host: this.settings.host,
      port: this.settings.port,
      username: this.definition.name,
      auth: this.settings.auth,
      version: this.settings.version,
    });

    this.bot.loadPlugin(pathfinder);
    this.bot.once('spawn', () => this.#onSpawn());
    this.bot.on('chat', (username, message) => this.#onChat(username, message));
    this.bot.on('kicked', (reason) => console.log(`[${this.name}] dat afara: ${reason}`));
    this.bot.on('error', (error) => console.log(`[${this.name}] eroare: ${error.message}`));
    this.bot.on('end', () => {
      console.log(`[${this.name}] deconectat.`);
      this.memory.save();
    });

    // bucla de comportament (1s): urmarire + frica de monstri
    this.timer = setInterval(() => this.#tick(), 1000);
  }

  #onSpawn() {
    const movements = new Movements(this.bot);
    movements.canDig = false;         // nu strica lumea prin tuneluri
    movements.allowSprinting = true;
    this.bot.pathfinder.setMovements(movements);

    console.log(`[${this.name}] conectat (${this.settings.host}:${this.settings.port})`);
    this.memory.event('Am intrat pe server.');
    this.say(this.definition.greetings[Math.floor(Math.random() * this.definition.greetings.length)]);
  }

  #tick() {
    if (!this.bot || !this.bot.entity) return;
    const owner = this.ownerEntity();

    // 1) monstri aproape -> replica + fuga spre jucator
    const danger = this.#nearestHostile(12);
    if (danger) {
      const now = Date.now();
      if (now - this.lastDangerAt > 25000) {
        this.lastDangerAt = now;
        this.say(this.definition.dangerLines[Math.floor(Math.random() * this.definition.dangerLines.length)]);
        this.memory.event('Am vazut monstri aproape si am fugit la jucator.');
      }
      if (owner) this.bot.pathfinder.setGoal(new GoalFollow(owner, 2), true);
      return;
    }

    // 2) urmarire normala
    if (this.mode === 'follow' && owner) {
      const distance = this.bot.entity.position.distanceTo(owner.position);
      if (distance > 6 && Date.now() - this.lastFollowAt > 1800) {
        this.lastFollowAt = Date.now();
        this.bot.pathfinder.setGoal(new GoalFollow(owner, 3), true);
      }
    }
  }

  async #onChat(username, message) {
    if (!this.bot || !this.bot.entity) return;
    const text = String(message || '').trim();
    if (!text) return;
    const lowered = text.toLowerCase();
    const ownerName = this.settings.owner.toLowerCase();
    const isOwner = username.toLowerCase() === ownerName;
    const calledByName = lowered.includes(this.definition.name.toLowerCase());

    if (!isOwner && !calledByName) return;
    this.memory.touchPlayer(username);

    // comenzile sunt cod; AI-ul nu le poate ocoli
    const command = lowered.replace(this.definition.name.toLowerCase(), '').trim();
    const handled = this.#command(command);
    if (handled) {
      this.say(handled);
      this.memory.event(`${username}: "${text}" -> ${handled}`);
      return;
    }

    if (this.paused) return; // regie: personajul tace, dar asculta
    const reply = await this.brain.reply(username, text);
    this.say(reply);
    this.memory.event(`${username} a zis "${text}"; eu am raspuns "${reply}"`);
  }

  #command(command) {
    const owner = this.ownerEntity();
    if (command === 'vino' || command === 'vin-o') {
      if (!owner) return 'Nu te vad. Unde esti?';
      this.mode = 'follow';
      this.bot.pathfinder.setGoal(new GoalFollow(owner, 2), true);
      return 'Vin!';
    }
    if (command === 'urmeaza-ma' || command === 'urmeaza') {
      if (!owner) return 'Nu te vad.';
      this.mode = 'follow';
      this.bot.pathfinder.setGoal(new GoalFollow(owner, 3), true);
      return 'Te urmez.';
    }
    if (command === 'stai') {
      this.mode = 'stay';
      this.bot.pathfinder.setGoal(null);
      return 'Stau aici.';
    }
    if (command === 'opreste') {
      this.mode = 'idle';
      this.bot.pathfinder.setGoal(null);
      return 'Ma opresc.';
    }
    if (command === 'unde' || command === 'unde esti') {
      const position = this.bot.entity.position;
      return `Sunt la ${position.x.toFixed(0)}, ${position.y.toFixed(0)}, ${position.z.toFixed(0)}.`;
    }
    if (command === 'memorie') {
      const { events } = this.memory.summary(4);
      return events.length ? `Imi amintesc: ${events.join(' / ')}` : 'Nu-mi amintesc nimic deocamdata.';
    }
    return null;
  }

  #nearestHostile(radius) {
    for (const entity of Object.values(this.bot.entities)) {
      if (!entity || !entity.position || entity === this.bot.entity) continue;
      const name = String(entity.name || '').toLowerCase();
      if (!HOSTILE.some((hostile) => name.includes(hostile))) continue;
      if (entity.position.distanceTo(this.bot.entity.position) < radius) return entity;
    }
    return null;
  }

  ownerEntity() {
    const player = this.bot?.players?.[this.settings.owner.toLowerCase()];
    return player && player.entity ? player.entity : null;
  }

  say(text) {
    if (!text || !this.bot) return;
    const now = Date.now();
    if (now - this.lastSpoken < 1200) return;
    this.lastSpoken = now;
    this.bot.chat(String(text).slice(0, 240));
  }

  /** Regie: opreste/reporneste AI-ul fara deconectare. */
  setPaused(value) {
    this.paused = value;
    if (value) this.bot?.pathfinder?.setGoal(null);
  }

  /** Regie: cheama personajul la jucator. */
  call() {
    const owner = this.ownerEntity();
    if (owner) {
      this.mode = 'follow';
      this.bot.pathfinder.setGoal(new GoalFollow(owner, 2), true);
    }
  }

  describe() {
    const position = this.bot?.entity?.position;
    return `${this.name}${position ? ` @ ${position.x.toFixed(0)},${position.y.toFixed(0)},${position.z.toFixed(0)}` : ''}` +
      ` | mod=${this.mode} | AI=${this.paused ? 'pauza' : 'activ'} | creier=${this.brain.online ? 'online' : 'local'}`;
  }

  stop() {
    clearInterval(this.timer);
    this.bot?.quit();
  }
}
