export const uid = () => (crypto.randomUUID ? crypto.randomUUID() : 'id' + Math.random().toString(36).slice(2) + Date.now().toString(36));

/** Crea elementos DOM: h('div', {class:'x', onclick}, hijos...) */
export function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  const add = (c) => {
    if (c == null || c === false) return;
    if (Array.isArray(c)) c.forEach(add);
    else el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  };
  kids.forEach(add);
  return el;
}

export const KG_PER_LB = 0.45359237;
export const toDisplay = (kg, unit) => (kg == null ? null : unit === 'lb' ? kg / KG_PER_LB : kg);
export const fromDisplay = (v, unit) => (v == null ? null : unit === 'lb' ? v * KG_PER_LB : v);
export function num(v, max = 1) {
  if (v == null || Number.isNaN(v)) return '';
  const f = Math.pow(10, max);
  return String(Math.round(v * f) / f).replace(/\.0+$/, '');
}
export const weightText = (kg, unit) => (kg == null ? '—' : `${num(toDisplay(kg, unit))} ${unit}`);
export function parseNum(s) {
  if (s == null) return null;
  const v = parseFloat(String(s).replace(',', '.'));
  return Number.isFinite(v) ? v : null;
}
export function clock(sec) {
  sec = Math.max(0, Math.floor(sec));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}
const dfDay = new Intl.DateTimeFormat('es', { weekday: 'short', day: 'numeric', month: 'short' });
const dfFull = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short', year: 'numeric' });
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
export const dayText = (ms) => cap(dfDay.format(ms));
export const fullDate = (ms) => dfFull.format(ms);
export function startOfWeek(ms) {
  const d = new Date(ms); d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.getTime();
}
export const MUSCLES = { CHEST: 'Pecho', BACK: 'Espalda', SHOULDERS: 'Hombros', BICEPS: 'Bíceps', TRICEPS: 'Tríceps', FOREARMS: 'Antebrazos', QUADS: 'Cuádriceps', HAMSTRINGS: 'Isquiotibiales', GLUTES: 'Glúteos', CALVES: 'Gemelos', CORE: 'Abdominales y core', FULL_BODY: 'Cuerpo completo' };
export const EQUIPMENT = { BARBELL: 'Barra', DUMBBELL: 'Mancuernas', MACHINE: 'Máquina', CABLE: 'Polea', BODYWEIGHT: 'Peso corporal', BANDS: 'Bandas', OTHER: 'Otros' };
export const TRACKING = { WEIGHT_REPS: 'Peso y repeticiones', BODYWEIGHT: 'Peso corporal', DURATION: 'Duración', DISTANCE: 'Distancia y tiempo' };
export const SET_TYPES = { N: 'Normal', W: 'Calentamiento', D: 'Drop set', F: 'Al fallo' };

export function toast(msg) {
  const t = h('div', { class: 'toast' }, msg);
  document.body.append(t);
  setTimeout(() => t.remove(), 2600);
}

let audioCtx;
export function beep(times = 3) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    for (let i = 0; i < times; i++) {
      const o = audioCtx.createOscillator(), g = audioCtx.createGain();
      o.frequency.value = 880; o.connect(g); g.connect(audioCtx.destination);
      const t0 = audioCtx.currentTime + i * 0.25;
      g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(0.4, t0 + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.18);
      o.start(t0); o.stop(t0 + 0.2);
    }
  } catch { /* sin audio */ }
  try { navigator.vibrate?.([200, 100, 200]); } catch { /* iOS no vibra */ }
}
/** iOS exige un gesto del usuario para activar el audio: se llama al completar una serie. */
export function unlockAudio() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume();
  } catch { /* sin audio */ }
  // iOS solo deja hablar tras un gesto del usuario: una frase vacía "desbloquea" la voz.
  try { if ('speechSynthesis' in window) window.speechSynthesis.speak(new SpeechSynthesisUtterance('')); } catch { /* sin voz */ }
}

/** Pitido corto para la cuenta atrás. */
export function tick() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const o = audioCtx.createOscillator(), g = audioCtx.createGain();
    o.frequency.value = 660; o.connect(g); g.connect(audioCtx.destination);
    const t0 = audioCtx.currentTime;
    g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(0.35, t0 + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.12);
    o.start(t0); o.stop(t0 + 0.14);
  } catch { /* sin audio */ }
}

/** Dice una frase en español (si la voz está activada en Ajustes). Devuelve true si habló. */
export function speak(text, enabled = true) {
  if (!enabled || !('speechSynthesis' in window)) return false;
  try {
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'es-ES'; u.rate = 1.05;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(u);
    return true;
  } catch { return false; }
}
const NUM = { 3: 'tres', 2: 'dos', 1: 'uno' };
/** Cuenta atrás: voz con el número o, si no hay voz, un pitido corto. */
export function cueNumber(n, voice, sound) {
  if (voice && speak(NUM[n])) return;
  if (sound) tick();
}

// ---- Hoja inferior genérica
let sheetEl = null;
export function closeSheet() { sheetEl?.remove(); sheetEl = null; }
export function sheet(title, options, extra) {
  closeSheet();
  sheetEl = h('div', { class: 'sheet-bg', onclick: (e) => { if (e.target === sheetEl) closeSheet(); } },
    h('div', { class: 'sheet' }, h('h2', { style: { marginBottom: '10px' } }, title),
      ...options.map(([label, fn, danger]) => h('button', { class: 'btn block' + (danger ? ' danger' : ''), style: { marginBottom: '8px', textAlign: 'left' }, onclick: () => { closeSheet(); fn(); } }, label)),
      extra));
  document.body.append(sheetEl);
}

/** Opciones típicas de descanso con el valor actual incluido (p. ej. 75 s), ordenadas. */
export const restOptions = (base, current) => [...new Set([...base, current])].filter((v) => v != null).sort((a, b) => a - b);

/** Descanso a medida: minutos y segundos escritos directamente. */
export function customRest(title, initialSec, onSave, minSec = 0, maxSec = 1800) {
  const m = h('input', { type: 'text', inputmode: 'numeric', value: String(Math.floor(initialSec / 60)), onfocus: (e) => e.target.select() });
  const s = h('input', { type: 'text', inputmode: 'numeric', value: String(initialSec % 60), onfocus: (e) => e.target.select() });
  sheet(title, [], h('div', { class: 'stack' },
    h('div', { class: 'row' }, h('label', { class: 'small muted', style: { flex: 1 } }, 'Minutos', m), h('label', { class: 'small muted', style: { flex: 1 } }, 'Segundos', s)),
    h('button', { class: 'btn primary block', onclick: () => {
      const total = (parseInt(m.value, 10) || 0) * 60 + (parseInt(s.value, 10) || 0);
      if (total < minSec || total > maxSec) { toast(`Debe estar entre ${minSec} s y ${Math.floor(maxSec / 60)} min`); return; }
      closeSheet(); onSave(total);
    } }, 'Guardar')));
}
