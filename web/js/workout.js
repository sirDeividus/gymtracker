import { S, exerciseById, saveSession, activeSession, finished, go } from './state.js';
import { h, uid, num, parseNum, toDisplay, fromDisplay, weightText, clock, beep, unlockAudio, toast, SET_TYPES, sheet, closeSheet, cueNumber } from './util.js';
import { sessionVolume, completedSets, lastPerformance, suggest, stepFor, warmupSets, isWorking } from './domain.js';
import { pickExercise } from './views.js';
import { openTabata } from './tabata.js';

/** Crea una sesión a partir de una rutina (o libre si routine es null). */
export async function startSession(routine) {
  const done = finished();
  const exercises = (routine?.items || []).map((it) => buildExercise(it.exerciseId, it, done));
  const se = {
    id: uid(), routineId: routine?.id || null, name: routine?.name || 'Entrenamiento libre',
    startedAt: Date.now(), endedAt: null, status: 'active', restEndsAt: null, restTotal: 0, exercises,
  };
  await saveSession(se);
  go('#/workout');
}

function buildExercise(exerciseId, it, done) {
  const def = exerciseById(exerciseId) || { id: exerciseId, name: 'Ejercicio', tracking: 'WEIGHT_REPS', muscle: 'FULL_BODY', equipment: 'OTHER' };
  const item = it || { sets: 3, repMin: 8, repMax: 12, restSec: S.settings.restSec, startWeightKg: null, durationSec: def.tracking === 'DURATION' ? 30 : null };
  const prev = lastPerformance(S.sessions, exerciseId);
  const prevWork = prev ? prev.sets.filter((s) => s.completed && isWorking(s)) : [];
  const sets = [];
  for (let i = 0; i < item.sets; i++) {
    const p = prevWork[i] || null;
    sets.push({ id: uid(), type: 'N', weightKg: null, reps: null, durationSec: null, completed: false, rpe: null, prev: p ? { weightKg: p.weightKg, reps: p.reps, durationSec: p.durationSec } : null });
  }
  return {
    id: uid(), exerciseId, name: def.name, tracking: def.tracking, muscle: def.muscle, equipment: def.equipment,
    restSec: item.restSec, repMin: item.repMin, repMax: item.repMax, durationSec: item.durationSec, cycles: item.cycles || 1, cycleRestSec: item.cycleRestSec ?? 30, notes: '', startWeightKg: item.startWeightKg,
    suggestion: suggest(prev, def, item.repMax, stepFor(def)), suggestionDismissed: false, sets,
  };
}

let timer = null, wakeLock = null;
let saveT = null;
const persist = (se) => { clearTimeout(saveT); saveT = setTimeout(() => saveSession(se), 250); };

export function stopWorkoutTimers() {
  clearInterval(timer); timer = null;
  document.querySelector('.lock')?.remove();
  try { wakeLock?.release(); } catch { /* ok */ }
  wakeLock = null;
}

export function workoutView() {
  const se = activeSession();
  if (!se) {
    return h('div', { class: 'screen' }, h('div', { class: 'empty' }, h('div', { class: 'big' }, '🏋️'), h('p', null, 'No hay un entrenamiento en curso.'), h('button', { class: 'btn primary', onclick: () => go('#/') }, 'Volver')));
  }
  const unit = S.settings.unit;
  let unlocked = false;
  const openTables = new Set(); // ejercicios por tiempo con las series desplegadas
  let lastRestKey = null;
  let lastCount = null;
  const root = h('div', { class: 'screen', style: { paddingBottom: '140px' } });
  const timeEl = h('span', { class: 'wk-time' });
  const prog = h('i');
  const stDur = h('b'), stVol = h('b'), stSets = h('b');
  const restHost = h('div');

  async function acquireWake() {
    try { if ('wakeLock' in navigator && !wakeLock) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => { wakeLock = null; }); } } catch { /* sin soporte */ }
  }
  acquireWake();
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') acquireWake(); });

  const total = () => se.exercises.reduce((a, e) => a + e.sets.length, 0);
  function updateStats() {
    stVol.textContent = weightText(sessionVolume(se), unit).replace('— ', '0 ');
    const vol = toDisplay(sessionVolume(se), unit);
    stVol.textContent = `${num(vol, 0) || 0} ${unit}`;
    stSets.textContent = completedSets(se);
    const done = se.exercises.reduce((a, e) => a + e.sets.filter((s) => s.completed).length, 0);
    prog.style.width = total() ? `${(done / total()) * 100}%` : '0%';
  }

  function render() {
    const y = root.scrollTop;
    root.replaceChildren(
      h('div', { class: 'wk-top' },
        h('button', { class: 'icon-btn', 'aria-label': 'Minimizar', onclick: () => go('#/') }, '⌄'),
        timeEl,
        h('button', { class: 'icon-btn', 'aria-label': 'Descanso', onclick: () => startRest(S.settings.restSec) }, '⏰'),
        h('button', { class: 'btn primary small', onclick: finishFlow }, 'Terminar'),
      ),
      h('div', { class: 'progress' }, prog),
      h('div', { class: 'stats' },
        h('div', { class: 'acc' }, stDur, h('span', null, 'Duración')),
        h('div', null, stVol, h('span', null, 'Volumen')),
        h('div', null, stSets, h('span', null, 'Series')),
      ),
      ...se.exercises.map(exerciseBlock),
      h('div', { class: 'stack', style: { marginTop: '22px' } },
        h('button', { class: 'btn primary block', onclick: async () => { const id = await pickExercise(); if (id) { se.exercises.push(buildExercise(id, null)); await saveSession(se); render(); } } }, '+  AGREGAR EJERCICIO'),
        h('div', { class: 'row' },
          h('button', { class: 'btn block', onclick: () => go('#/settings') }, 'Configuración'),
          h('button', { class: 'btn block danger', onclick: discard }, 'Descartar Entreno'),
        ),
      ),
      restHost,
    );
    root.scrollTop = y;
    updateStats(); tick();
  }

  function timerButton(ex, done) {
    const work = ex.durationSec || ex.sets[0]?.durationSec || 20;
    const summary = (ex.cycles > 1 ? `${ex.cycles} ciclos × ` : '') + `${ex.sets.length} × ${work} s` + (ex.restSec > 0 ? ` · descanso ${ex.restSec} s` : '');
    return h('button', { style: { display: 'flex', alignItems: 'center', gap: '12px', width: '100%', margin: '10px 0 0', padding: '14px 16px', border: 0, borderRadius: '18px', color: '#fff', textAlign: 'left', fontFamily: 'inherit', background: done ? 'var(--ok)' : 'var(--primary)' },
      onclick: () => openTabata(se, ex, () => saveSession(se), render), 'aria-label': 'Abrir el temporizador' },
      h('span', { style: { fontSize: '24px' } }, '⏱'),
      h('span', { style: { flex: 1 } }, h('div', { style: { fontWeight: 700, fontSize: '17px' } }, done ? 'Completado' : 'Iniciar temporizador'), h('div', { style: { fontSize: '13px', opacity: .85 } }, summary)),
      h('span', { style: { width: '34px', height: '34px', borderRadius: '50%', background: done ? '#fff' : 'rgba(255,255,255,.25)', color: done ? 'var(--ok)' : 'rgba(255,255,255,.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900 } }, '✓'));
  }

  function exerciseBlock(ex) {
    const def = exerciseById(ex.exerciseId);
    const done = ex.sets.length > 0 && ex.sets.every((s) => s.completed);
    const fields = ex.tracking === 'DURATION' ? ['SEG'] : ex.tracking === 'DISTANCE' ? ['KM', 'MIN'] : ex.tracking === 'BODYWEIGHT' ? ['REPS'] : [unit.toUpperCase(), 'REPS'];
    const compact = ex.tracking === 'DURATION';
    const showTable = !compact || openTables.has(ex.id);
    const sug = ex.suggestion && !ex.suggestionDismissed ? ex.suggestion : null;
    return h('section', { class: 'ex' + (done ? ' done' : '') },
      h('div', { class: 'ex-head' },
        h('div', { class: 'avatar' }, ex.name.charAt(0)),
        h('h3', null, ex.name),
        h('button', { class: 'icon-btn', 'aria-label': 'Más', onclick: () => exerciseMenu(ex) }, '⋮'),
      ),
      compact ? null : h('div', { class: 'row', style: { margin: '10px 0' } },
        h('button', { class: 'pill', onclick: () => restPicker(ex) }, `⏱ Descanso: ${ex.restSec ? clock(ex.restSec) : '--'}`),
        h('button', { class: 'pill', onclick: () => noteEditor(ex) }, ex.notes ? '📝 Nota ✓' : '📝 Nota'),
      ),
      compact ? timerButton(ex, done) : null,
      compact ? h('button', { class: 'btn small ghost', style: { margin: '8px 0' }, onclick: () => { if (openTables.has(ex.id)) openTables.delete(ex.id); else openTables.add(ex.id); render(); } }, showTable ? 'Ocultar series' : `Ver series (${ex.sets.filter((x) => x.completed).length}/${ex.sets.length})`) : null,
      !showTable ? null : sug && sug.kind !== 'hold' ? h('div', { class: 'banner' },
        h('b', { style: { color: 'var(--primary)', fontSize: '12px', letterSpacing: '.6px' } }, sug.kind === 'first' ? 'PRIMERA VEZ' : 'SUGERENCIA'),
        h('div', null, sug.text),
        sug.kind === 'up' ? h('div', { class: 'row', style: { marginTop: '6px' } },
          h('button', { class: 'btn small', onclick: () => { ex.sets.forEach((s) => { if (!s.completed) s.weightKg = sug.weightKg; }); ex.suggestionDismissed = true; persist(se); render(); } }, 'Aplicar a series pendientes'),
          h('button', { class: 'btn small ghost', onclick: () => { ex.suggestionDismissed = true; persist(se); render(); } }, 'Ignorar')) :
          h('button', { class: 'btn small ghost', onclick: () => { ex.suggestionDismissed = true; persist(se); render(); } }, 'Entendido'),
      ) : null,
      !showTable ? null : h('div', { class: 'tbl-h' }, h('span', null, 'SERIE'), h('span', null, 'ANTERIOR'), ...fields.map((f) => h('span', null, f)), ...(fields.length === 1 ? [h('span')] : []), h('span', null, '✓')),
      ...(showTable ? ex.sets.map((s, i) => setRow(ex, s, i)) : []),
      !showTable ? null : h('button', { class: 'btn block', style: { marginTop: '6px' }, onclick: () => { const last = ex.sets[ex.sets.length - 1]; ex.sets.push({ id: uid(), type: 'N', weightKg: null, reps: null, durationSec: null, completed: false, rpe: null, prev: null }); void last; persist(se); render(); } }, '+  Agregar Serie'),
    );
  }

  function normalNumber(ex, s) {
    if (s.type !== 'N') return SET_TYPES[s.type] ? s.type : '·';
    return ex.sets.filter((x) => x.type === 'N').indexOf(s) + 1;
  }

  function setRow(ex, s, i) {
    const t = ex.tracking;
    const prevTxt = !s.prev ? '—' : t === 'DURATION' ? `${s.prev.durationSec ?? '—'}s` : t === 'BODYWEIGHT' ? `${s.prev.reps ?? '—'} reps` : `${num(toDisplay(s.prev.weightKg, unit))} × ${s.prev.reps ?? '—'}`;
    const inp = (val, ph, on, dec = true) => h('input', { type: 'text', inputmode: dec ? 'decimal' : 'numeric', value: val ?? '', placeholder: ph, 'aria-label': 'valor', oninput: (e) => { on(e.target.value); persist(se); updateStats(); } });
    const wIn = inp(s.weightKg != null ? num(toDisplay(s.weightKg, unit), 2) : '', s.prev?.weightKg != null ? num(toDisplay(s.prev.weightKg, unit)) : '—', (v) => { const p = parseNum(v); s.weightKg = p == null ? null : fromDisplay(p, unit); });
    const rIn = inp(s.reps ?? '', `${ex.repMin}${ex.repMax !== ex.repMin ? '–' + ex.repMax : ''}`, (v) => { const p = parseNum(v); s.reps = p == null ? null : Math.round(p); }, false);
    const dIn = inp(s.durationSec ?? '', String(ex.durationSec || 30), (v) => { const p = parseNum(v); s.durationSec = p == null ? null : Math.round(p); }, false);
    const kmIn = inp(s.distanceM != null ? num(s.distanceM / 1000, 3) : '', '0', (v) => { const p = parseNum(v); s.distanceM = p == null ? null : p * 1000; });
    const minIn = inp(s.durationSec != null ? num(s.durationSec / 60, 2) : '', '0', (v) => { const p = parseNum(v); s.durationSec = p == null ? null : Math.round(p * 60); });
    const cells = t === 'DURATION' ? [dIn, h('span')] : t === 'DISTANCE' ? [kmIn, minIn] : t === 'BODYWEIGHT' ? [rIn, h('span')] : [wIn, rIn];
    return h('div', { class: 'set' + (s.completed ? ' ok' : '') },
      h('button', { class: 'n ' + s.type, style: { border: 0, color: 'inherit', font: 'inherit', fontWeight: 700 }, 'aria-label': 'Opciones de serie', onclick: () => setMenu(ex, s, i) }, normalNumber(ex, s)),
      h('button', { class: 'prev', onclick: () => copyPrev(ex, s) }, prevTxt),
      ...cells,
      h('button', { class: 'ck', 'aria-label': s.completed ? 'Deshacer serie' : 'Completar serie', onclick: () => toggle(ex, s) }, '✓'),
    );
  }

  function copyPrev(ex, s) {
    if (!s.prev) return;
    if (s.prev.weightKg != null) s.weightKg = s.prev.weightKg;
    if (s.prev.reps != null) s.reps = s.prev.reps;
    if (s.prev.durationSec != null && ex.tracking === 'DURATION') s.durationSec = s.prev.durationSec;
    persist(se); render();
  }

  function toggle(ex, s) {
    unlockAudio();
    if (!s.completed) {
      const need = ex.tracking === 'WEIGHT_REPS' ? (s.reps == null ? 'las repeticiones' : null)
        : ex.tracking === 'BODYWEIGHT' ? (s.reps == null ? 'las repeticiones' : null)
        : ex.tracking === 'DURATION' ? (s.durationSec == null ? 'los segundos' : null)
        : (s.distanceM == null && s.durationSec == null ? 'distancia o tiempo' : null);
      if (need) { toast(`Escribe ${need} antes de completar`); return; }
      // Si dejó el peso vacío, usa el de la sesión anterior (registro en 2 toques).
      if (ex.tracking === 'WEIGHT_REPS' && s.weightKg == null) s.weightKg = s.prev?.weightKg ?? 0;
      s.completed = true;
      // Tras la última serie del ejercicio se usa el descanso «al cambiar de ejercicio» (Ajustes);
      // si era el último ejercicio pendiente, no hace falta descansar.
      const exDone = ex.sets.every((x) => x.completed);
      const othersPending = se.exercises.some((e) => e !== ex && e.sets.some((x) => !x.completed));
      const restSec = !exDone ? ex.restSec : othersPending ? (S.settings.changeRestSec ?? 120) : 0;
      if (restSec > 0) startRest(restSec, false);
    } else s.completed = false;
    persist(se); render();
  }

  function setMenu(ex, s, i) {
    const opts = [
      ['Tipo de serie…', () => typeSheet(ex, s)],
      ex.tracking === 'WEIGHT_REPS' && s.weightKg > 0 ? ['Calculadora de discos', () => plates(s)] : null,
      i > 0 ? ['Repetir serie anterior', () => { const p = ex.sets[i - 1]; s.weightKg = p.weightKg; s.reps = p.reps; s.durationSec = p.durationSec; persist(se); render(); }] : null,
      ['Esfuerzo (RPE)…', () => rpeSheet(s)],
      ['Eliminar serie', () => { ex.sets.splice(i, 1); persist(se); render(); }, true],
    ].filter(Boolean);
    sheet(`Serie`, opts);
  }
  function typeSheet(ex, s) {
    sheet('Tipo de serie', Object.entries(SET_TYPES).map(([k, v]) => [v + (k === 'W' ? ' (no cuenta en volumen ni récords)' : ''), () => { s.type = k; persist(se); render(); }]));
  }
  function rpeSheet(s) {
    sheet('Esfuerzo percibido (RPE)', [[ 'Sin dato', () => { s.rpe = null; persist(se); }], ...[6, 7, 8, 9, 10].map((v) => [`RPE ${v}`, () => { s.rpe = v; persist(se); }])]);
  }
  function plates(s) {
    const unitPlates = unit === 'lb' ? [45, 35, 25, 10, 5, 2.5] : [25, 20, 15, 10, 5, 2.5, 1.25];
    const bar = unit === 'lb' ? 45 : 20;
    let rem = (toDisplay(s.weightKg, unit) - bar) / 2;
    const out = [];
    if (rem > 0) for (const p of unitPlates) { const n = Math.floor(rem / p + 1e-9); if (n > 0) { out.push(`${num(p, 2)} × ${n}`); rem -= n * p; } }
    sheet(`Discos para ${num(toDisplay(s.weightKg, unit))} ${unit}`, [], h('div', null, h('p', { class: 'sub' }, `Barra ${bar} ${unit}. Por lado:`), h('p', { style: { fontSize: '20px', fontWeight: 700 } }, out.length ? out.join('  ·  ') : 'Solo la barra'), rem > 0.01 ? h('p', { class: 'small', style: { color: 'var(--warn)' } }, `Faltan ${num(rem * 2, 2)} ${unit} con estos discos.`) : null));
  }
  function exerciseMenu(ex) {
    sheet(ex.name, [
      ex.tracking === 'WEIGHT_REPS' ? ['Añadir calentamiento', () => { const base = ex.sets.find((x) => x.type === 'N')?.weightKg || ex.sets[0]?.prev?.weightKg; const w = warmupSets(base); if (!w.length) { toast('Escribe primero el peso de trabajo (≥ 20 kg)'); return; } ex.sets.unshift(...w.map((x) => ({ id: uid(), type: 'W', weightKg: x.weightKg, reps: x.reps, durationSec: null, completed: false, rpe: null, prev: null }))); persist(se); render(); }] : null,
      ex.tracking === 'DURATION' ? ['Modo intervalos (Tabata)', () => openTabata(se, ex, () => saveSession(se), render)] : null,
      ['Quitar de la sesión', () => { se.exercises = se.exercises.filter((e) => e !== ex); persist(se); render(); }, true],
    ].filter(Boolean));
  }
  function restPicker(ex) {
    sheet('Descanso entre series', [0, 30, 45, 60, 90, 120, 180, 240, 300].map((s) => [s === 0 ? 'Sin descanso' : clock(s), () => { ex.restSec = s; persist(se); render(); }]));
  }
  function noteEditor(ex) {
    const ta = h('textarea', { rows: 4, placeholder: 'Notas del ejercicio' }, ex.notes);
    sheet('Nota', [], h('div', { class: 'stack' }, ta, h('button', { class: 'btn primary block', onclick: () => { ex.notes = ta.value; persist(se); closeSheet(); render(); } }, 'Guardar')));
  }

  // ---- Descanso con bloqueo (bolsillo)
  function startRest(sec, manual = true) {
    se.restEndsAt = Date.now() + sec * 1000; se.restTotal = sec; unlocked = false;
    persist(se); if (manual) toast(`Descanso de ${clock(sec)}`); tick();
  }
  function tick() {
    const now = Date.now();
    timeEl.textContent = clock((now - se.startedAt) / 1000);
    stDur.textContent = clock((now - se.startedAt) / 1000);
    const left = se.restEndsAt ? Math.ceil((se.restEndsAt - now) / 1000) : 0;
    // 3, 2, 1 en los últimos segundos del descanso (si dura más de tres).
    if (se.restEndsAt && left >= 1 && left <= 3 && se.restTotal > 3) {
      const k = se.restEndsAt + ':' + left;
      if (k !== lastCount) { lastCount = k; cueNumber(left, S.settings.voice !== false, S.settings.sound); }
    }
    if (se.restEndsAt && left <= 0) {
      se.restEndsAt = null; persist(se); unlocked = false;
      if (S.settings.sound) beep(); toast('¡Descanso terminado!');
    }
    const active = !!se.restEndsAt;
    const key = active ? (unlocked ? 'u' : 'l') : 'n';
    if (key !== lastRestKey) { lastRestKey = key; renderRestPanel(active); document.body.querySelector('.lock')?.remove(); if (active && !unlocked) document.body.append(h('div', { class: 'lock', 'aria-hidden': 'true' })); }
    const t = restHost.querySelector('.t'); if (t) t.textContent = clock(left);
  }
  function renderRestPanel(active) {
    if (!active) { restHost.replaceChildren(); return; }
    let pressT;
    const lockBtn = h('button', { class: 'btn', 'aria-label': 'Mantén pulsado para desbloquear',
      ontouchstart: () => { pressT = setTimeout(() => { unlocked = true; tick(); }, 700); },
      ontouchend: () => clearTimeout(pressT), ontouchcancel: () => clearTimeout(pressT),
      onmousedown: () => { pressT = setTimeout(() => { unlocked = true; tick(); }, 700); }, onmouseup: () => clearTimeout(pressT),
    }, '🔒 Mantén para desbloquear');
    restHost.replaceChildren(h('div', { class: 'rest' },
      h('div', { class: 't' }, ''),
      ...(unlocked ? [
        h('button', { class: 'btn small', onclick: () => { se.restEndsAt += -15000; tick(); } }, '−15'),
        h('button', { class: 'btn small', onclick: () => { se.restEndsAt += 15000; tick(); } }, '+15'),
        h('button', { class: 'btn small', onclick: () => { se.restEndsAt = null; persist(se); tick(); } }, 'Saltar'),
      ] : [lockBtn]),
    ));
  }

  async function finishFlow() {
    const n = completedSets(se);
    if (n === 0) { sheet('Aún no hay series completadas', [['Descartar entrenamiento', discard, true]], h('p', { class: 'sub' }, 'Completa al menos una serie para guardar la sesión.')); return; }
    const notes = h('textarea', { rows: 3, placeholder: 'Notas generales (opcional)' });
    sheet('Finalizar entrenamiento', [], h('div', { class: 'stack' },
      h('p', null, `Duración ${clock((Date.now() - se.startedAt) / 1000)} · ${n} series · ${num(toDisplay(sessionVolume(se), unit), 0)} ${unit}`),
      notes,
      h('button', { class: 'btn primary block', onclick: async () => {
        se.exercises.forEach((e) => { e.sets = e.sets.filter((s) => s.completed); });
        se.exercises = se.exercises.filter((e) => e.sets.length);
        se.notes = notes.value; se.status = 'finished'; se.endedAt = Date.now(); se.restEndsAt = null;
        await saveSession(se); closeSheet(); document.querySelector('.lock')?.remove(); stopWorkoutTimers(); go('#/session/' + se.id);
      } }, 'Finalizar y guardar'),
    ));
  }
  async function discard() {
    sheet('¿Descartar entrenamiento?', [], h('div', { class: 'stack' }, h('p', { class: 'sub' }, 'Se perderán las series registradas en esta sesión.'),
      h('button', { class: 'btn block danger', onclick: async () => { const { removeSession } = await import('./state.js'); await removeSession(se.id); closeSheet(); document.querySelector('.lock')?.remove(); stopWorkoutTimers(); go('#/'); } }, 'Descartar'),
      h('button', { class: 'btn block', onclick: closeSheet }, 'Seguir entrenando')));
  }

  render();
  clearInterval(timer); timer = setInterval(tick, 250);
  return root;
}

