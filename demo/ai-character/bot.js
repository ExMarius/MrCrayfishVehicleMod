/**
 * Personaj AI pentru Minecraft — demo minimal, dar functional.
 *
 * Ideea: personajul NU este un NPC din mod, ci un BOT care se conecteaza la
 * server ca un jucator real (skin, nume, chat, mers natural, poate sapa/construi).
 * Asa arata identic cu un jucator in inregistrare, fara sa scriem vreun mod.
 *
 * Ce stie sa faca acum:
 *  - vorbeste in chat, cu personalitate constanta (si optional prin AI online);
 *  - asculta comenzi: <nume> vino / stai / urmeaza-ma / opreste;
 *  - te urmareste singur pe distanta;
 *  - daca vede monstri aproape, comenteaza si fuge spre tine;
 *  - isi aminteste (memorie in fisier) cine esti si ce s-a intamplat.
 *
 * Rulare:  npm install  &&  cp .env.example .env  (completezi daca vrei AI)  &&  npm start
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mineflayer from 'mineflayer';
import pathfinderPkg from 'mineflayer-pathfinder';

const { pathfinder, Movements, goals } = pathfinderPkg;
const { GoalFollow, GoalBlock } = goals;

const here = path.dirname(fileURLToPath(import.meta.url));
const MEMORY_FILE = path.join(here, 'memory.json');

/* ============================ CONFIGURARE ============================ */

const config = {
  host: process.env.MC_HOST || '127.0.0.1',
  port: Number(process.env.MC_PORT || 25565),
  version: process.env.MC_VERSION || '1.21.1',
  username: process.env.BOT_NAME || 'Uke',
  // in offline-mode poti intra cu orice nume; pe server cu auth iti trebuie cont
  auth: process.env.MC_AUTH || 'offline',
  owner: (process.env.OWNER || 'ExMarius').toLowerCase(),
  respondToAllChat: process.env.RESPOND_ALL === '1',
  llmModel: process.env.LLM_MODEL || 'llama-3.3-70b-versatile',
};

const persona = {
  name: config.username,
  // Personalitatea sta AICI, intr-un singur loc, si se schimba usor pe personaje.
  description:
    'Esti Uke, un tovaras de aventura. Esti curios, glumet si loial, dar nu vorbesti prea mult. ' +
    'Ai curaj, dar nu esti temerar: preferi sa te gandesti inainte. ' +
    'Vorbesti romana, natural, ca un prieten, nu ca un asistent.',
  rules: [
    'Raspunde scurt: 1-2 propozitii, ca in chat, nu eseuri.',
    'Fara liste, fara markdown, fara emoji, fara descrieri de tip *zambeste*.',
    'Nu inventa fapte despre lume; daca nu stii, spune sincer.',
    'Nu porunci jucatorului; propune si intreaba.',
  ],
};

/* ============================ MEMORIE ============================ */

const memory = loadMemory();

function loadMemory() {
  try {
    return JSON.parse(fs.readFileSync(MEMORY_FILE, 'utf8'));
  } catch {
    return { facts: [], events: [], players: {} };
  }
}

function saveMemory() {
  try {
    fs.writeFileSync(MEMORY_FILE, JSON.stringify(memory, null, 2));
  } catch (error) {
    console.log('[memorie] nu pot salva:', error.message);
  }
}

function rememberEvent(text) {
  memory.events.push({ at: new Date().toISOString(), text });
  memory.events = memory.events.slice(-60); // pastram ultimele 60
  saveMemory();
}

/* ============================ BOTUL ============================ */

const bot = mineflayer.createBot({
  host: config.host,
  port: config.port,
  username: config.username,
  auth: config.auth,
  version: config.version,
});

bot.loadPlugin(pathfinder);

let movement;
let mode = 'follow';           // follow | stay | idle
let lastSpoken = 0;
let lastDangerLine = 0;

bot.once('spawn', () => {
  movement = new Movements(bot);
  movement.canDig = false;     // nu strica lumea prin tuneluri accidentale
  movement.allowSprinting = true;
  bot.pathfinder.setMovements(movement);

  console.log(`[${persona.name}] conectat pe ${config.host}:${config.port}`);
  say('Salut! Am ajuns.');
  rememberEvent('Am intrat pe server.');

  // urmarire continua, fara comenzi repetate
  setInterval(() => {
    const owner = ownerEntity();
    if (!owner || mode !== 'follow') return;
    const distance = bot.entity.position.distanceTo(owner.position);
    if (distance > 6) {
      bot.pathfinder.setGoal(new GoalFollow(owner, 3), true);
    }
  }, 2000);

  // frica de monstri: reactioneaza singur
  setInterval(() => {
    const hostiles = Object.values(bot.entities).filter((entity) => {
      if (!entity || !entity.position || entity === bot.entity) return false;
      const name = (entity.name || '').toLowerCase();
      const isMonster = ['zombie', 'skeleton', 'creeper', 'spider', 'enderman',
        'witch', 'drowned', 'husk', 'stray', 'pillager', 'slime'].some((m) => name.includes(m));
      return isMonster && entity.position.distanceTo(bot.entity.position) < 10;
    });
    if (hostiles.length === 0) return;

    const now = Date.now();
    if (now - lastDangerLine > 20000) {
      lastDangerLine = now;
      say(pickLine([
        'Stai, aud ceva... sunt monstri aproape!',
        'Nu-mi place ce vad. Vin spre tine!',
        'Atenție, se apropie ceva. Stai aproape de mine.',
      ]));
      rememberEvent('Am dat peste monstri si am fugit la jucator.');
    }
    const owner = ownerEntity();
    if (owner) {
      bot.pathfinder.setGoal(new GoalFollow(owner, 2), true);
    }
  }, 1500);
});

/* ============================ COMENZI ============================ */

const COMMANDS = {
  vino: () => {
    const owner = ownerEntity();
    if (!owner) return 'Nu te vad. Unde esti?';
    mode = 'follow';
    bot.pathfinder.setGoal(new GoalFollow(owner, 2), true);
    return 'Vin!';
  },
  'urmeaza-ma': () => {
    const owner = ownerEntity();
    if (!owner) return 'Nu te vad.';
    mode = 'follow';
    bot.pathfinder.setGoal(new GoalFollow(owner, 3), true);
    return 'Te urmez.';
  },
  stai: () => {
    mode = 'stay';
    bot.pathfinder.setGoal(null);
    return 'Stau aici.';
  },
  opreste: () => {
    mode = 'idle';
    bot.pathfinder.setGoal(null);
    return 'Ma opresc.';
  },
  unde: () => {
    const position = bot.entity.position;
    return `Sunt la ${position.x.toFixed(0)}, ${position.y.toFixed(0)}, ${position.z.toFixed(0)}.`;
  },
  memorie: () => {
    const last = memory.events.slice(-5).map((event) => event.text);
    if (last.length === 0) return 'Nu-mi amintesc nimic deocamdata.';
    return 'Imi amintesc: ' + last.join(' / ');
  },
};

bot.on('chat', async (username, message) => {
  if (!bot.entity) return;
  const fromOwner = username.toLowerCase() === config.owner;
  const text = message.trim();
  const lowered = text.toLowerCase();
  const calledByName = lowered.includes(persona.name.toLowerCase());

  if (!fromOwner && !calledByName && !config.respondToAllChat) return;

  const remembered = memory.players[username.toLowerCase()] || {};
  memory.players[username.toLowerCase()] = {
    ...remembered,
    lastSeen: new Date().toISOString(),
  };
  saveMemory();

  // 1) comenzi simple — executate de cod, nu de AI (deci sunt sigure)
  const command = lowered.replace(persona.name.toLowerCase(), '').trim();
  for (const [name, handler] of Object.entries(COMMANDS)) {
    if (command === name || command.startsWith(name + ' ')) {
      const answer = handler();
      say(answer);
      rememberEvent(`${username}: ${text} -> ${answer}`);
      return;
    }
  }

  // 2) conversatie — decide AI-ul (daca e configurat), altfel raspuns local
  const reply = await think(username, text);
  say(reply);
  rememberEvent(`${username}: ${text} | eu: ${reply}`);
});

/* ============================ CREIERUL ============================ */

async function think(username, text) {
  if (!process.env.GROQ_API_KEY) {
    return localReply(text);
  }
  try {
    const reply = await askGroq([
      { role: 'system', content: systemPrompt(username) },
      { role: 'user', content: text },
    ]);
    return reply;
  } catch (error) {
    console.log('[ai] eroare, raspund local:', error.message);
    return localReply(text);
  }
}

function systemPrompt(username) {
  const recent = memory.events.slice(-8).map((event) => '- ' + event.text).join('\n');
  return [
    persona.description,
    'Reguli:',
    ...persona.rules.map((rule) => '- ' + rule),
    `Jucatorul cu care vorbesti: ${username}.`,
    recent ? `Ce s-a intamplat recent:\n${recent}` : 'Nu s-a intamplat nimic notabil deocamdata.',
    'Raspunde doar cu replica ta, fara numele tau in fata.',
  ].join('\n');
}

async function askGroq(messages) {
  const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
    },
    body: JSON.stringify({
      model: config.llmModel,
      messages,
      temperature: 0.8,
      max_tokens: 120,
    }),
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const data = await response.json();
  return (data.choices?.[0]?.message?.content || '').trim().slice(0, 300);
}

function localReply(text) {
  const lowered = text.toLowerCase();
  if (lowered.includes('salut') || lowered.includes('buna')) return 'Salut! Ce facem azi?';
  if (lowered.includes('unde')) return COMMANDS.unde();
  if (lowered.includes('ajutor') || lowered.includes('ajuta')) return 'Spune-mi ce ai nevoie si ma apuc.';
  return pickLine([
    'Interesant. Spune-mi mai mult.',
    'Bine, hai sa vedem.',
    'Am inteles. Ce facem acum?',
  ]);
}

/* ============================ UTILITARE ============================ */

function ownerEntity() {
  const player = bot.players[config.owner];
  return player && player.entity ? player.entity : null;
}

function say(text) {
  if (!text) return;
  const now = Date.now();
  if (now - lastSpoken < 1200) return; // anti-spam
  lastSpoken = now;
  bot.chat(String(text).slice(0, 240));
}

function pickLine(lines) {
  return lines[Math.floor(Math.random() * lines.length)];
}

bot.on('kicked', (reason) => console.log('[kicked]', reason));
bot.on('error', (error) => console.log('[error]', error.message));
bot.on('end', () => {
  console.log('[bot] deconectat.');
  saveMemory();
});
