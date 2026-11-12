/**
 * "Creierul": transforma o replica a jucatorului in raspunsul personajului.
 * Foloseste un model online GRATUIT (Groq, API compatibil OpenAI) daca exista
 * cheie; altfel raspunde local, ca sa functioneze si fara internet/cont.
 *
 * Regula de aur: creierul doar VORBESTE. Actiunile sunt executate de cod
 * (vezi character.js), deci un model nu poate strica lumea.
 */
const ENDPOINT = 'https://api.groq.com/openai/v1/chat/completions';

export class Brain {
  constructor({ persona, memory, model }) {
    this.persona = persona;
    this.memory = memory;
    this.model = model || 'llama-3.3-70b-versatile';
    this.history = [];
  }

  get online() {
    return Boolean(process.env.GROQ_API_KEY);
  }

  async reply(playerName, message) {
    if (!this.online) return this.#local(message);

    const messages = [
      { role: 'system', content: this.#systemPrompt(playerName) },
      ...this.history.slice(-8),
      { role: 'user', content: message },
    ];

    try {
      const text = await this.#ask(messages);
      this.history.push({ role: 'user', content: message }, { role: 'assistant', content: text });
      if (this.history.length > 40) this.history = this.history.slice(-40);
      return text;
    } catch (error) {
      console.log(`[${this.persona.name}] AI indisponibil (${error.message}); raspund local.`);
      return this.#local(message);
    }
  }

  async #ask(messages) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(ENDPOINT, {
        method: 'POST',
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
        },
        body: JSON.stringify({
          model: this.model,
          messages,
          temperature: 0.85,
          max_tokens: 110,
        }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      const text = (data.choices?.[0]?.message?.content || '').trim();
      if (!text) throw new Error('raspuns gol');
      return text.replace(/\s+/g, ' ').slice(0, 280);
    } finally {
      clearTimeout(timeout);
    }
  }

  #systemPrompt(playerName) {
    const { events, facts } = this.memory.summary();
    return [
      this.persona.description,
      '',
      'Reguli de comportament:',
      ...this.persona.rules.map((rule) => `- ${rule}`),
      '',
      `Vorbesti cu jucatorul ${playerName}.`,
      facts.length ? `Lucruri pe care le stii deja:\n${facts.map((f) => `- ${f}`).join('\n')}` : '',
      events.length ? `Ce s-a intamplat recent:\n${events.map((e) => `- ${e}`).join('\n')}` : '',
      '',
      'Raspunde DOAR cu replica ta, fara numele tau in fata, fara descrieri de actiuni.',
    ].filter(Boolean).join('\n');
  }

  /** Fara cheie AI: replici simple, dar personalitatea ramane vizibila. */
  #local(message) {
    const text = message.toLowerCase();
    if (/(salut|buna|hei|neata)/.test(text)) return this.persona.greetings[0];
    if (/(ajutor|ajuta|vin-o|vino)/.test(text)) return 'Vin, spune-mi unde esti.';
    if (/(unde esti|unde ești|unde)/.test(text)) return 'Sunt aproape. Te vad.';
    if (/(ce faci|ce facem)/.test(text)) return 'Astept sa-mi spui ce facem. Tu ce vrei?';
    if (/(multumesc|mersi)/.test(text)) return 'Cu placere.';
    return this.persona.filler[Math.floor(Math.random() * this.persona.filler.length)];
  }
}
