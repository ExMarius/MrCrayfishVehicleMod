/**
 * Memoria unui personaj: fapte durabile + evenimente recente, salvate pe disc.
 * Fiecare personaj are propriul fisier (memorie/<id>.json), deci isi aminteste
 * separat de ceilalti si supravietuieste restartului.
 */
import fs from 'node:fs';
import path from 'node:path';

const MAX_EVENTS = 80;

export class Memory {
  constructor(directory, id) {
    this.file = path.join(directory, `${id}.json`);
    this.data = this.#load();
  }

  #load() {
    try {
      return JSON.parse(fs.readFileSync(this.file, 'utf8'));
    } catch {
      return { facts: [], events: [], players: {} };
    }
  }

  save() {
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2));
    } catch (error) {
      console.log('[memorie] nu pot salva:', error.message);
    }
  }

  /** Un eveniment = ceva ce s-a intamplat (se pastreaza ultimele MAX_EVENTS). */
  event(text) {
    this.data.events.push({ at: new Date().toISOString(), text });
    if (this.data.events.length > MAX_EVENTS) {
      this.data.events = this.data.events.slice(-MAX_EVENTS);
    }
    this.save();
  }

  /** Un fapt = ceva durabil ("jucatorul a construit o casa langa rau"). */
  fact(text) {
    if (!this.data.facts.includes(text)) {
      this.data.facts.push(text);
      if (this.data.facts.length > 40) this.data.facts.shift();
      this.save();
    }
  }

  touchPlayer(name) {
    const key = name.toLowerCase();
    this.data.players[key] = {
      ...(this.data.players[key] || {}),
      lastSeen: new Date().toISOString(),
    };
    this.save();
  }

  recentEvents(count = 8) {
    return this.data.events.slice(-count).map((event) => event.text);
  }

  summary(count = 5) {
    const events = this.recentEvents(count);
    const facts = this.data.facts.slice(-5);
    return { events, facts };
  }
}
