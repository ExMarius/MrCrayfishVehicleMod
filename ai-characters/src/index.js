/**
 * Intrarea: incarca personajele din characters/*.json, le conecteaza pe server
 * si porneste consola de regie (pentru tine, cand filmezi).
 *
 *   npm start
 *
 * Consola de regie:
 *   list                - ce personaje sunt si ce fac
 *   pause <nume> / play <nume>
 *   call <nume>         - cheama personajul la tine
 *   say <nume> <text>   - il pune sa spuna ceva (util cand o scena se blocheaza)
 *   memory <nume>       - ce isi aminteste
 *   quit
 */
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';
import { Character } from './character.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');

/* Citim .env fara dependente (functioneaza pe orice Node 20+). */
try {
  const envFile = path.join(root, '.env');
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const index = trimmed.indexOf('=');
      if (index === -1) continue;
      const key = trimmed.slice(0, index).trim();
      const value = trimmed.slice(index + 1).trim().replace(/^["']|["']$/g, '');
      if (key && process.env[key] === undefined) process.env[key] = value;
    }
  }
} catch (error) {
  console.log('[env] nu pot citi .env:', error.message);
}

const settings = {
  host: process.env.MC_HOST || '127.0.0.1',
  port: Number(process.env.MC_PORT || 25565),
  version: process.env.MC_VERSION || '1.21.1',
  auth: process.env.MC_AUTH || 'offline',
  owner: process.env.OWNER || 'ExMarius',
  model: process.env.LLM_MODEL || 'llama-3.3-70b-versatile',
};

const charactersDir = path.join(root, 'characters');
const definitions = fs.readdirSync(charactersDir)
  .filter((file) => file.endsWith('.json'))
  .map((file) => JSON.parse(fs.readFileSync(path.join(charactersDir, file), 'utf8')));

if (definitions.length === 0) {
  console.log('Nu am gasit personaje in characters/.');
  process.exit(1);
}

console.log('=== Personaje AI ===');
console.log(`Server: ${settings.host}:${settings.port} (${settings.version}), owner: ${settings.owner}`);
console.log(`Creier: ${process.env.GROQ_API_KEY ? 'online (Groq gratuit)' : 'local (fara cheie AI)'}`);
console.log(`Personaje: ${definitions.map((definition) => definition.name).join(', ')}`);
console.log('');

const characters = definitions.map((definition) => {
  const character = new Character(definition, settings, path.join(root, 'memory'));
  character.start();
  return character;
});

/* ============================ REGIE ============================ */

const cli = readline.createInterface({ input: process.stdin, output: process.stdout });
cli.on('line', (line) => {
  const [command, who, ...rest] = line.trim().split(/\s+/);
  const target = characters.find((character) =>
    character.name.toLowerCase() === String(who || '').toLowerCase());

  switch ((command || '').toLowerCase()) {
    case 'list':
      characters.forEach((character) => console.log(' - ' + character.describe()));
      break;
    case 'pause':
      if (target) { target.setPaused(true); console.log(`${target.name}: AI pe pauza.`); }
      else console.log('Personaj necunoscut.');
      break;
    case 'play':
      if (target) { target.setPaused(false); console.log(`${target.name}: AI pornit.`); }
      else console.log('Personaj necunoscut.');
      break;
    case 'call':
      if (target) { target.call(); console.log(`${target.name}: chemat.`); }
      else console.log('Personaj necunoscut.');
      break;
    case 'say':
      if (target) { target.say(rest.join(' ')); console.log(`${target.name}: a spus.`); }
      else console.log('Personaj necunoscut.');
      break;
    case 'memory': {
      if (!target) { console.log('Personaj necunoscut.'); break; }
      const { events, facts } = target.memory.summary(6);
      console.log(`Evenimente (${target.name}):`);
      events.forEach((event) => console.log('  - ' + event));
      if (facts.length) {
        console.log('Fapte:');
        facts.forEach((fact) => console.log('  - ' + fact));
      }
      break;
    }
    case 'quit':
    case 'exit':
      characters.forEach((character) => character.stop());
      cli.close();
      process.exit(0);
      break;
    case 'help':
    default:
      console.log('Comenzi: list | pause <nume> | play <nume> | call <nume> | say <nume> <text> | memory <nume> | quit');
  }
});

process.on('SIGINT', () => {
  characters.forEach((character) => character.stop());
  process.exit(0);
});
