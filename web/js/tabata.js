import { h, uid, clock, beep, unlockAudio, sheet, closeSheet, toast } from './util.js';
import { intervalSnapshot, planTotalSec } from './domain.js';

const LABEL = { PREP: 'PREPÁRATE', WORK: '¡TRABAJA!', REST: 'DESCANSA', CYCLE_REST: 'DESCANSO ENTRE CICLOS', DONE: '¡LISTO!' };
const COLOR = { PREP: 'var(--warn)', WORK: 'var(--primary)', REST: 'var(--text2)', CYCLE_REST: 'var(--warn)', DONE: 'var(--primary)' };

function stepper(label, get, set, min, max, step = 1) {
  const val = h('b', { style: { minWidth: '44px', textAlign: 'center' } }, String(get()));
  const upd = (v) => { set(Math.min(max, Math.max(min, v))); val.textContent = get(); };
  return h('div', { class: 'row between' }, h('span', null, label), h('div', { class: 'row' },
    h('button', { class: 'btn small', onclick: () => { upd(get() - step); cfgChanged(); } }, '−'), val,
    h('button', { class: 'btn small', onclick: () => { upd(get() + step); cfgChanged(); } }, '+')));
}
let cfgChanged = () => {};

/** Configuración del modo intervalos para un ejercicio; al comenzar abre la pantalla de cuenta atrás. */
export function openTabata(se, ex, save, onChange) {
  const pending = ex.sets.filter((s) => !s.completed).length;
  const cfg = { workSec: ex.durationSec || 20, restSec: 10, rounds: Math.max(1, Math.min(60, pending || 8)), cycles: 1, cycleRestSec: 30, prepSec: 5 };
  const summary = h('p', { class: 'small muted' });
  cfgChanged = () => {
    const p = { ...cfg, cycleRestSec: cfg.cycles > 1 ? cfg.cycleRestSec : 0 };
    summary.textContent = `${p.rounds * p.cycles} rondas de trabajo · duración ${clock(planTotalSec(p))}` + (p.prepSec ? ` (incluye ${p.prepSec} s de inicio)` : '');
  };
  const presets = h('div', { class: 'chips' }, ...[['Tabata 20/10×8', 20, 10, 8], ['30/30', 30, 30, null], ['40/20', 40, 20, null]].map(([l, w, r, n]) => h('button', { class: 'chip', onclick: () => { cfg.workSec = w; cfg.restSec = r; if (n) cfg.rounds = n; closeSheet(); openTabata2(); } }, l)));
  let cycleRow;
  function openTabata2() {
    cycleRow = stepper('Descanso entre ciclos (s)', () => cfg.cycleRestSec, (v) => { cfg.cycleRestSec = v; }, 0, 300, 5);
    sheet('Modo intervalos', [], h('div', { class: 'stack' },
      h('p', { class: 'sub' }, `${ex.name}. Cada ronda de trabajo completa una serie (si faltan series, se añaden).`),
      presets,
      stepper('Trabajo (s)', () => cfg.workSec, (v) => { cfg.workSec = v; }, 5, 300, 5),
      stepper('Descanso entre rondas (s)', () => cfg.restSec, (v) => { cfg.restSec = v; }, 0, 120, 5),
      stepper('Rondas por ciclo', () => cfg.rounds, (v) => { cfg.rounds = v; }, 1, 60),
      stepper('Ciclos', () => cfg.cycles, (v) => { cfg.cycles = v; }, 1, 30),
      cycleRow,
      stepper('Cuenta atrás de inicio (s)', () => cfg.prepSec, (v) => { cfg.prepSec = v; }, 0, 30),
      summary,
      h('button', { class: 'btn primary block', onclick: () => { closeSheet(); begin(); } }, 'Comenzar')));
    cfgChanged();
  }
  function begin() {
    unlockAudio();
    const plan = { ...cfg, cycleRestSec: cfg.cycles > 1 ? cfg.cycleRestSec : 0 };
    const total = plan.rounds * plan.cycles;
    // Asegura una serie pendiente por ronda.
    let pend = ex.sets.filter((s) => !s.completed);
    while (pend.length < total) { const s = { id: uid(), type: 'N', weightKg: null, reps: null, durationSec: null, completed: false, rpe: null, prev: null }; ex.sets.push(s); pend.push(s); }
    pend = pend.slice(0, total);
    runIntervals(plan, pend, ex, async () => { await save(); onChange(); });
  }
  openTabata2();
}

function runIntervals(plan, setsToFill, ex, done) {
  let start = Date.now(), pausedAt = null, pausedTotal = 0, filled = 0, lastKey = null, finished = false;
  const label = h('div', { style: { fontSize: '28px', fontWeight: 800, textAlign: 'center' } });
  const big = h('div', { style: { fontSize: '160px', fontWeight: 800, textAlign: 'center', fontFamily: 'ui-monospace,Menlo,monospace', lineHeight: 1.05 } });
  const bar = h('i', { style: { display: 'block', height: '100%', transition: 'width .2s linear' } });
  const info = h('div', { style: { fontSize: '20px', fontWeight: 600, textAlign: 'center' } });
  const left = h('div', { class: 'sub', style: { textAlign: 'center' } });
  const btns = h('div', { class: 'row', style: { width: '100%' } });
  const ov = h('div', { class: 'overlay', style: { justifyContent: 'center', alignItems: 'center', padding: '24px', gap: '14px' } },
    h('div', { class: 'sub', style: { fontSize: '20px', textAlign: 'center' } }, ex.name), label, big,
    h('div', { class: 'progress', style: { width: '80%', margin: 0 } }, bar), info, left, btns);
  document.body.append(ov);
  let wake = null; try { navigator.wakeLock?.request('screen').then((w) => { wake = w; }).catch(() => {}); } catch { /* sin soporte */ }

  const elapsed = () => (pausedAt ?? Date.now()) - start - pausedTotal;
  function renderBtns() {
    btns.replaceChildren(
      finished ? h('button', { class: 'btn primary block', onclick: close }, 'Cerrar') : [
        h('button', { class: 'btn block', onclick: () => { if (pausedAt) { pausedTotal += Date.now() - pausedAt; pausedAt = null; } else pausedAt = Date.now(); renderBtns(); } }, pausedAt ? 'Reanudar' : 'Pausar'),
        h('button', { class: 'btn block danger', onclick: close }, 'Terminar')]);
  }
  function tick() {
    const snap = intervalSnapshot(plan, elapsed());
    while (filled < snap.completed && filled < setsToFill.length) { const s = setsToFill[filled]; s.durationSec = plan.workSec; s.completed = true; filled++; }
    const key = snap.phase + snap.cycle + ':' + snap.round;
    if (key !== lastKey) { if (lastKey !== null) beep(snap.phase === 'DONE' ? 3 : 1); lastKey = key; }
    label.textContent = LABEL[snap.phase]; label.style.color = COLOR[snap.phase]; big.style.color = COLOR[snap.phase];
    if (snap.phase === 'DONE') {
      finished = true; big.textContent = '✓'; bar.style.width = '0%';
      info.textContent = `${plan.rounds * plan.cycles} rondas completadas`; left.textContent = 'Tus series ya están registradas en el entrenamiento.'; renderBtns(); clearInterval(iv); return;
    }
    big.textContent = String(Math.ceil(snap.remainingMs / 1000));
    bar.style.background = COLOR[snap.phase]; bar.style.width = snap.totalMs ? `${(snap.remainingMs / snap.totalMs) * 100}%` : '0%';
    info.textContent = snap.round > 0 ? (plan.cycles > 1 ? `Ciclo ${snap.cycle} de ${plan.cycles} · ` : '') + `Ronda ${snap.round} de ${plan.rounds}` : '';
    left.textContent = `Quedan ${clock(Math.ceil(snap.totalLeftMs / 1000))}`;
  }
  async function close() { clearInterval(iv); try { wake?.release(); } catch { /* ok */ } ov.remove(); toast(filled ? `${filled} series registradas` : 'Intervalos cancelados'); await done(); }
  const iv = setInterval(tick, 200);
  renderBtns(); tick();
}
