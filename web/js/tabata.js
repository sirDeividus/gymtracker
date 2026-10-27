import { h, uid, clock, beep, unlockAudio, sheet, closeSheet, toast } from './util.js';
import { intervalSnapshot, planTotalSec, intervalSchedule, nextPhase } from './domain.js';

const COLORS = { PREP: '#fffc3f', WORK: '#9dff3f', REST: '#3db8ff', CYCLE_REST: '#ffb020', DONE: '#fffc3f' };
const TITLE = { PREP: 'PREPÁRATE', WORK: 'ENTRENAR', REST: 'DESCANSA', CYCLE_REST: 'DESCANSO DE CICLO', DONE: '¡LISTO!' };
const NEXT = { PREP: 'PREPÁRATE', WORK: 'ENTRENAR', REST: 'DESCANSAR', CYCLE_REST: 'DESCANSO DE CICLO', DONE: 'FIN' };
const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
const KEY = 'gymtrack.tabata';
const DEFAULT = { workSec: 20, restSec: 10, rounds: 8, cycles: 1, cycleRestSec: 30, prepSec: 10 };
const loadCfg = () => { try { return { ...DEFAULT, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; } catch { return { ...DEFAULT }; } };
const saveCfg = (c) => { try { localStorage.setItem(KEY, JSON.stringify(c)); } catch { /* sin almacenamiento */ } };

/** Temporizador de intervalos a pantalla completa: se abre listo (en pausa); el botón verde empieza. */
export function openTabata(se, ex, save, onChange) {
  let plan = loadCfg();
  let started = false, pausedAt = null, pausedTotal = 0, startMs = 0, filled = 0, lastKey = null, muted = false, sets = [];
  let iv = null, wake = null;

  const el = (css, ...kids) => h('div', { style: css }, ...kids);
  const timeTotal = h('div', { style: { color: '#fff', fontSize: '38px', fontWeight: 900, textAlign: 'right' } });
  const nameEl = h('div', { style: { color: '#fff', fontSize: '15px', fontWeight: 700, textAlign: 'right' } }, ex.name);
  const title = h('div', { style: { fontSize: '40px', fontWeight: 900, textAlign: 'center', color: '#0a0a0a' } });
  const big = h('div', { style: { fontSize: 'min(30vw,150px)', fontWeight: 900, textAlign: 'center', color: '#0a0a0a', lineHeight: 1, fontVariantNumeric: 'tabular-nums' } });
  const roundInfo = h('div', { style: { fontSize: '17px', fontWeight: 700, textAlign: 'center', color: '#0a0a0a' } });
  const main = h('div', { style: { flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '6px', padding: '0 12px' } }, title, big, roundInfo);
  const soundBtn = h('button', { style: btn(), onclick: () => { muted = !muted; draw(); } });
  const topRow = h('div', { style: { display: 'flex', justifyContent: 'space-between', padding: '14px 18px' } },
    h('button', { style: btn(), onclick: edit }, '⚙︎ EDITAR TEMPORIZADOR'), soundBtn);
  const listBtn = h('button', { style: { ...btn(), padding: '16px', textAlign: 'center', width: '100%' }, onclick: showList }, 'VER INTERVALOS  ››');
  const nextLabel = h('div', { style: { fontSize: '32px', fontWeight: 900, color: '#0a0a0a' } });
  const nextBox = h('div', { style: { padding: '10px', textAlign: 'center' } }, h('div', { style: { fontSize: '13px', fontWeight: 700, color: 'rgba(10,10,10,.7)' } }, 'SIGUIENTE'), nextLabel);
  const roundsN = h('div', { style: { fontSize: '56px', fontWeight: 900, color: '#3db8ff', lineHeight: 1.05 } });
  const cyclesN = h('div', { style: { fontSize: '56px', fontWeight: 900, color: '#ffb020', lineHeight: 1.05 } });
  const play = h('button', { style: { width: '100px', height: '100px', background: '#9dff3f', border: 0, fontSize: '56px', color: '#0a0a0a' }, 'aria-label': 'Empezar o pausar', onclick: playPause });
  const counter = (label, n) => h('div', { style: { flex: 1, textAlign: 'center' } }, h('div', { style: { color: '#aaa', fontSize: '13px', fontWeight: 700, whiteSpace: 'pre-line' } }, label), n);
  const bottom = h('div', { style: { display: 'flex', alignItems: 'center', padding: '8px 8px calc(var(--safe-b) + 8px)', background: '#000' } }, counter('RONDAS\nRESTANTES', roundsN), play, counter('CICLOS\nRESTANTES', cyclesN));
  const mainBox = h('div', { style: { flex: 1, display: 'flex', flexDirection: 'column' } }, topRow, main, listBtn);
  const ov = h('div', { class: 'overlay', style: { background: '#000', padding: 0 } },
    h('div', { style: { display: 'flex', alignItems: 'stretch', paddingTop: 'var(--safe-t)', background: '#000' } },
      h('button', { style: { width: '84px', background: '#f44336', border: 0, color: '#fff', fontSize: '44px' }, 'aria-label': 'Cerrar el temporizador', onclick: close }, '✕'),
      h('div', { style: { flex: 1, padding: '10px 18px' } }, timeTotal, nameEl)),
    mainBox, nextBox, bottom);
  function btn() { return { background: 'transparent', border: 0, color: '#0a0a0a', fontWeight: 800, fontSize: '15px', padding: '6px 0', fontFamily: 'inherit', textAlign: 'left' }; }
  document.body.append(ov);
  try { navigator.wakeLock?.request('screen').then((w) => { wake = w; }).catch(() => {}); } catch { /* sin soporte */ }

  const elapsed = () => (!started ? 0 : (pausedAt ?? Date.now()) - startMs - pausedTotal);

  function playPause() {
    unlockAudio();
    const now = Date.now();
    if (!started) {
      started = true; startMs = now; pausedAt = null; pausedTotal = 0;
      // Asegura una serie pendiente por ronda.
      const total = plan.rounds * plan.cycles;
      let pend = ex.sets.filter((s) => !s.completed);
      while (pend.length < total) { const s = { id: uid(), type: 'N', weightKg: null, reps: null, durationSec: null, completed: false, rpe: null, prev: null }; ex.sets.push(s); pend.push(s); }
      sets = pend.slice(0, total);
    } else if (pausedAt == null) pausedAt = now;
    else { pausedTotal += now - pausedAt; pausedAt = null; }
    draw();
  }
  function cfgPlan() { return { ...plan, cycleRestSec: plan.cycles > 1 ? plan.cycleRestSec : 0 }; }
  function draw() {
    const p = cfgPlan(), e = elapsed();
    const snap = intervalSnapshot(p, e);
    if (started) {
      while (filled < snap.completed && filled < sets.length) { sets[filled].durationSec = p.workSec; sets[filled].completed = true; filled++; }
      const key = snap.phase + snap.cycle + ':' + snap.round;
      if (key !== lastKey) { if (lastKey !== null && !muted && snap.phase !== 'DONE') beep(1); if (snap.phase === 'DONE' && !muted) beep(3); lastKey = key; }
    }
    const nx = nextPhase(p, e);
    const paused = !started || pausedAt != null;
    const sec = snap.phase === 'DONE' ? 0 : Math.ceil(snap.remainingMs / 1000);
    const rLeft = snap.phase === 'PREP' || snap.phase === 'CYCLE_REST' ? p.rounds : snap.phase === 'WORK' ? p.rounds - snap.round + 1 : snap.phase === 'REST' ? p.rounds - snap.round : 0;
    const cLeft = snap.phase === 'PREP' ? p.cycles : snap.phase === 'WORK' || snap.phase === 'REST' ? p.cycles - snap.cycle + 1 : snap.phase === 'CYCLE_REST' ? p.cycles - snap.cycle : 0;
    mainBox.style.background = COLORS[snap.phase]; nextBox.style.background = COLORS[nx] || '#eee';
    timeTotal.textContent = mmss(Math.ceil(snap.totalLeftMs / 1000));
    title.textContent = TITLE[snap.phase]; big.textContent = snap.phase === 'DONE' ? '✓' : mmss(sec);
    roundInfo.textContent = snap.round > 0 && snap.phase !== 'DONE' ? (p.cycles > 1 ? `Ciclo ${snap.cycle} de ${p.cycles} · ` : '') + `Ronda ${snap.round} de ${p.rounds}` : '';
    nextLabel.textContent = NEXT[nx]; roundsN.textContent = rLeft; cyclesN.textContent = cLeft;
    play.textContent = paused ? '▶' : '❚❚'; soundBtn.textContent = muted ? '🔇 SIN SONIDO' : '🔊 SONIDO';
    if (snap.phase === 'DONE') { play.textContent = '✓'; }
  }
  function close() { clearInterval(iv); try { wake?.release(); } catch { /* ok */ } ov.remove(); toast(filled ? `${filled} series registradas` : 'Temporizador cerrado'); save().then(onChange); }

  function edit() {
    if (started && pausedAt == null) { pausedAt = Date.now(); draw(); }
    const f = {};
    const field = (label, key) => { const i = h('input', { type: 'text', inputmode: 'numeric', value: String(plan[key]), onfocus: (e) => e.target.select() }); f[key] = i; return h('label', { class: 'small muted', style: { flex: 1 } }, label, i); };
    const preset = (w, r, n) => { f.workSec.value = w; f.restSec.value = r; f.rounds.value = n; };
    sheet('Editar temporizador', [], h('div', { class: 'stack' },
      h('div', { class: 'chips' }, ...[['Tabata', 20, 10, 8], ['30/30', 30, 30, 10], ['40/20', 40, 20, 10]].map(([l, w, r, n]) => h('button', { class: 'chip', onclick: () => preset(w, r, n) }, l))),
      h('div', { class: 'row' }, field('Trabajo (s)', 'workSec'), field('Descanso (s)', 'restSec')),
      h('div', { class: 'row' }, field('Rondas', 'rounds'), field('Ciclos', 'cycles')),
      h('div', { class: 'row' }, field('Descanso entre ciclos (s)', 'cycleRestSec'), field('Cuenta atrás inicial (s)', 'prepSec')),
      h('p', { class: 'small muted' }, 'Cada ronda de trabajo completa una serie del ejercicio; si faltan series, se añaden.'),
      h('button', { class: 'btn primary block', onclick: () => {
        const n = (k, min, max) => { const v = Number(String(f[k].value).replace(',', '.')); return Math.min(max, Math.max(min, Number.isFinite(v) && String(f[k].value).trim() !== '' ? Math.round(v) : plan[k])); };
        plan = { workSec: n('workSec', 1, 3600), restSec: n('restSec', 0, 3600), rounds: n('rounds', 1, 60), cycles: n('cycles', 1, 30), cycleRestSec: n('cycleRestSec', 0, 3600), prepSec: n('prepSec', 0, 60) };
        saveCfg(plan); started = false; pausedAt = null; pausedTotal = 0; filled = 0; lastKey = null; sets = []; closeSheet(); draw();
      } }, 'Guardar')));
  }
  function showList() {
    const p = cfgPlan(), snap = intervalSnapshot(p, elapsed());
    const rows = intervalSchedule(p).map((st) => {
      const cur = st.phase === snap.phase && st.cycle === snap.cycle && st.round === snap.round;
      const name = st.phase === 'PREP' ? 'Preparación' : st.phase === 'WORK' ? `${p.cycles > 1 ? 'C' + st.cycle + ' · ' : ''}Entrenar ${st.round}` : st.phase === 'REST' ? 'Descansar' : 'Descanso de ciclo';
      return h('div', { class: 'row', style: { padding: '8px 6px', background: cur ? COLORS[st.phase] + '40' : 'transparent' } }, h('i', { style: { width: '14px', height: '14px', background: COLORS[st.phase], display: 'inline-block' } }), h('span', { class: 'grow' }, name), h('b', null, mmss(st.seconds)));
    });
    sheet('Intervalos', [], h('div', { style: { maxHeight: '55vh', overflowY: 'auto' } }, ...rows));
  }

  iv = setInterval(draw, 200); draw();
}
