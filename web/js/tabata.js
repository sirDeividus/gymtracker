import { h, uid, clock, beep, unlockAudio, sheet, closeSheet, toast, speak, cueNumber } from './util.js';
import { S } from './state.js';
import { intervalSnapshot, planTotalSec, intervalSchedule, nextPhase } from './domain.js';

const COLORS = { PREP: '#f4f4f5', WORK: '#ff1e46', REST: '#38bdf8', CYCLE_REST: '#f5a524', DONE: '#34c759' };
const TITLE = { PREP: 'PREPÁRATE', WORK: 'ENTRENAR', REST: 'DESCANSA', CYCLE_REST: 'DESCANSO DE CICLO', DONE: '¡LISTO!' };
const NEXT = { PREP: 'PREPÁRATE', WORK: 'ENTRENAR', REST: 'DESCANSAR', CYCLE_REST: 'DESCANSO DE CICLO', DONE: 'FIN' };
const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
const KEY = 'gymtrack.tabata';
const DEFAULT = { workSec: 20, restSec: 10, rounds: 8, cycles: 1, cycleRestSec: 30, prepSec: 10 };
const loadCfg = () => { try { return { ...DEFAULT, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; } catch { return { ...DEFAULT }; } };
const saveCfg = (c) => { try { localStorage.setItem(KEY, JSON.stringify(c)); } catch { /* sin almacenamiento */ } };

/** Temporizador de intervalos a pantalla completa: se abre listo (en pausa); el botón verde empieza. */
export function openTabata(se, ex, save, onChange) {
  let plan = { workSec: ex.durationSec || ex.sets[0]?.durationSec || 20, restSec: ex.restSec ?? 10, rounds: Math.min(60, Math.max(1, ex.sets.length)), cycles: ex.cycles || 1, cycleRestSec: ex.cycleRestSec ?? 30, prepSec: ex.prepSec ?? 10 };
  let lastCount = null; let started = false, pausedAt = null, pausedTotal = 0, startMs = 0, filled = 0, lastKey = null, muted = false, sets = [];
  let iv = null, wake = null;

  const timeTotal = h('div', { class: 'small muted' });
  const nameEl = h('div', { style: { fontWeight: 600, fontSize: '16px' } }, ex.name);
  const dot = h('i', { style: { width: '8px', height: '8px', borderRadius: '50%', display: 'inline-block' } });
  const title = h('span', { style: { fontSize: '14px', fontWeight: 600, letterSpacing: '3px' } });
  const big = h('div', { style: { fontSize: 'min(26vw,128px)', fontWeight: 300, textAlign: 'center', color: 'var(--text)', lineHeight: 1.05, fontFamily: 'ui-monospace,Menlo,monospace', fontVariantNumeric: 'tabular-nums' } });
  const bar = h('i', { style: { display: 'block', height: '100%', borderRadius: '4px', transition: 'width .2s linear' } });
  const roundInfo = h('div', { style: { fontSize: '15px', color: 'var(--text2)', marginTop: '14px', textAlign: 'center' } });
  const main = h('div', { style: { flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' } },
    h('div', { style: { display: 'flex', alignItems: 'center', gap: '8px' } }, dot, title), big,
    h('div', { style: { width: '70%', height: '4px', borderRadius: '4px', background: 'var(--surface2)' } }, bar), roundInfo);
  const pill = (txt, fn) => h('button', { style: { background: 'transparent', border: '1px solid var(--outline)', color: 'var(--text)', borderRadius: '999px', padding: '8px 14px', fontSize: '13px', fontFamily: 'inherit' }, onclick: fn }, txt);
  const soundBtn = pill('', () => { muted = !muted; draw(); });
  const pills = h('div', { style: { display: 'flex', gap: '8px', padding: '14px 0 0' } }, pill('⚙︎ Editar', edit), soundBtn, pill('Intervalos', showList));
  const nextDot = h('i', { style: { width: '7px', height: '7px', borderRadius: '50%', display: 'inline-block' } });
  const nextLabel = h('b', { style: { fontSize: '14px' } });
  const nextBox = h('div', { style: { textAlign: 'center', padding: '0 0 14px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px' } }, h('span', { style: { fontSize: '12px', letterSpacing: '2px', color: 'var(--muted)' } }, 'SIGUIENTE'), nextDot, nextLabel);
  const roundsN = h('div', { style: { fontSize: '36px', fontWeight: 300, fontFamily: 'ui-monospace,Menlo,monospace' } });
  const cyclesN = h('div', { style: { fontSize: '36px', fontWeight: 300, fontFamily: 'ui-monospace,Menlo,monospace' } });
  const play = h('button', { style: { width: '88px', height: '88px', borderRadius: '50%', border: 0, background: 'linear-gradient(135deg,var(--primary),var(--primary2))', color: '#fff', fontSize: '34px', boxShadow: '0 8px 28px rgba(255,30,70,.3)' }, 'aria-label': 'Empezar o pausar', onclick: playPause });
  const counter = (label, n) => h('div', { style: { flex: 1, textAlign: 'center' } }, n, h('div', { style: { color: 'var(--muted)', fontSize: '11px', letterSpacing: '2px' } }, label));
  const bottom = h('div', { style: { display: 'flex', alignItems: 'center', padding: '0 0 calc(var(--safe-b) + 20px)' } }, counter('RONDAS', roundsN), play, counter('CICLOS', cyclesN));
  const mainBox = h('div', { style: { flex: 1, display: 'flex', flexDirection: 'column', padding: '0 20px', transition: 'background .3s' } }, pills, main, nextBox, bottom);
  const ov = h('div', { class: 'overlay', style: { padding: 0 } },
    h('div', { style: { display: 'flex', alignItems: 'center', gap: '12px', padding: '8px 20px 0' } },
      h('button', { style: { width: '44px', height: '44px', borderRadius: '50%', border: '1px solid var(--outline)', background: 'transparent', color: 'var(--text)', fontSize: '20px' }, 'aria-label': 'Cerrar el temporizador', onclick: close }, '✕'),
      h('div', null, nameEl, timeTotal)),
    mainBox);
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
      const voice = S.settings.voice !== false, sound = S.settings.sound;
      const changed = key !== lastKey;
      if (changed) {
        if (lastKey !== null && !muted && snap.phase !== 'DONE') beep(1);
        if (snap.phase === 'DONE' && !muted) beep(3);
        lastKey = key;
        // Al empezar cada tramo, qué toca hacer.
        if (!muted) speak({ WORK: 'Go', REST: 'Descanso', CYCLE_REST: 'Descanso', PREP: 'Prepárate', DONE: 'Terminado' }[snap.phase], voice);
      } else if (!muted && pausedAt == null && snap.phase !== 'DONE') {
        const left = Math.ceil(snap.remainingMs / 1000), total = Math.round(snap.totalMs / 1000);
        if (left >= 1 && left <= 3 && left < total) {
          const ck = key + ':' + left;
          if (ck !== lastCount) {
            lastCount = ck;
            // En los descansos, a 3 s: «Prepárate»; después 2, 1. En trabajo y preparación: 3, 2, 1.
            if ((snap.phase === 'REST' || snap.phase === 'CYCLE_REST') && left === 3) { if (!speak('Prepárate', voice) && sound) beep(1); }
            else cueNumber(left, voice, sound);
          }
        }
      }
    }
    const nx = nextPhase(p, e);
    const paused = !started || pausedAt != null;
    const sec = snap.phase === 'DONE' ? 0 : Math.ceil(snap.remainingMs / 1000);
    const rLeft = snap.phase === 'PREP' || snap.phase === 'CYCLE_REST' ? p.rounds : snap.phase === 'WORK' ? p.rounds - snap.round + 1 : snap.phase === 'REST' ? p.rounds - snap.round : 0;
    const cLeft = snap.phase === 'PREP' ? p.cycles : snap.phase === 'WORK' || snap.phase === 'REST' ? p.cycles - snap.cycle + 1 : snap.phase === 'CYCLE_REST' ? p.cycles - snap.cycle : 0;
    const col = COLORS[snap.phase];
    ov.style.background = `linear-gradient(180deg, ${col}2e 0%, var(--bg) 55%)`;
    dot.style.background = col; title.style.color = col; bar.style.background = col;
    bar.style.width = snap.totalMs ? `${(snap.remainingMs / snap.totalMs) * 100}%` : '0%';
    timeTotal.textContent = `Quedan ${mmss(Math.ceil(snap.totalLeftMs / 1000))}`;
    title.textContent = TITLE[snap.phase]; big.textContent = snap.phase === 'DONE' ? '✓' : mmss(sec);
    roundInfo.textContent = snap.round > 0 && snap.phase !== 'DONE' ? (p.cycles > 1 ? `Ciclo ${snap.cycle}/${p.cycles}  ·  ` : '') + `Ronda ${snap.round}/${p.rounds}` : '';
    nextDot.style.background = COLORS[nx] || '#999'; nextLabel.textContent = NEXT[nx]; roundsN.textContent = rLeft; cyclesN.textContent = cLeft;
    play.textContent = snap.phase === 'DONE' ? '✓' : paused ? '▶' : '❚❚'; soundBtn.textContent = muted ? '🔇 Sin sonido' : '🔊 Sonido';
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
        ex.cycles = plan.cycles; ex.cycleRestSec = plan.cycleRestSec; ex.prepSec = plan.prepSec; started = false; pausedAt = null; pausedTotal = 0; filled = 0; lastKey = null; sets = []; lastCount = null; closeSheet(); draw();
      } }, 'Guardar')));
  }
  function showList() {
    const p = cfgPlan(), snap = intervalSnapshot(p, elapsed());
    const rows = intervalSchedule(p).map((st) => {
      const cur = st.phase === snap.phase && st.cycle === snap.cycle && st.round === snap.round;
      const name = st.phase === 'PREP' ? 'Preparación' : st.phase === 'WORK' ? `${p.cycles > 1 ? 'C' + st.cycle + ' · ' : ''}Entrenar ${st.round}` : st.phase === 'REST' ? 'Descansar' : 'Descanso de ciclo';
      return h('div', { class: 'row', style: { padding: '8px 6px', background: cur ? COLORS[st.phase] + '33' : 'transparent' } }, h('i', { style: { width: '14px', height: '14px', background: COLORS[st.phase], display: 'inline-block' } }), h('span', { class: 'grow' }, name), h('b', null, mmss(st.seconds)));
    });
    sheet('Intervalos', [], h('div', { style: { maxHeight: '55vh', overflowY: 'auto' } }, ...rows));
  }

  iv = setInterval(draw, 200); draw();
}
