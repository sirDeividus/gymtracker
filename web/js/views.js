import { S, TPL, allExercises, exerciseById, saveRoutine, removeRoutine, saveCustomExercise, newRoutineItem, activeSession, finished, removeSession, saveSettings, go, loadAll } from './state.js';
import { h, uid, num, parseNum, toDisplay, fromDisplay, weightText, clock, dayText, fullDate, startOfWeek, toast, sheet, closeSheet, MUSCLES, EQUIPMENT, TRACKING } from './util.js';
import { sessionVolume, completedSets, bestOf, weekStreak, epley1RM, isWorking } from './domain.js';
import { startSession } from './workout.js';
import { db } from './db.js';

const header = (title, back) => h('div', { class: 'header' }, back ? h('button', { class: 'icon-btn', 'aria-label': 'Volver', onclick: () => (typeof back === 'function' ? back() : history.back()) }, '‹') : null, h('h2', null, title));
const empty = (icon, title, msg) => h('div', { class: 'empty' }, h('div', { class: 'big' }, icon), h('h3', null, title), h('p', null, msg));

// ================= Inicio =================
export function homeView() {
  const act = activeSession();
  const done = finished();
  const week0 = startOfWeek(Date.now());
  const thisWeek = done.filter((s) => s.startedAt >= week0);
  const dayNames = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
  const trained = new Set(thisWeek.map((s) => (new Date(s.startedAt).getDay() + 6) % 7));
  const last = done[0];
  const next = S.routines[0];
  return h('div', { class: 'screen has-nav' },
    h('h1', null, 'GymTrack'),
    h('p', { class: 'sub' }, done.length ? `${thisWeek.length} entrenamientos esta semana · racha ${weekStreak(S.sessions)} sem.` : 'Empieza tu primer entrenamiento'),
    h('div', { class: 'card' },
      h('div', { class: 'label', style: { margin: '0 0 8px' } }, 'Esta semana'),
      h('div', { class: 'dots' }, ...dayNames.map((_, i) => h('i', { class: trained.has(i) ? 'on' : '' }))),
      h('div', { class: 'row between small muted', style: { marginTop: '6px' } }, ...dayNames.map((d) => h('span', null, d))),
    ),
    act
      ? h('button', { class: 'btn primary block', onclick: () => go('#/workout') }, '▶  CONTINUAR ENTRENAMIENTO')
      : h('div', { class: 'stack' },
        next ? h('button', { class: 'btn primary block', onclick: () => startSession(next) }, `▶  INICIAR ${next.name.toUpperCase()}`) : null,
        h('button', { class: next ? 'btn block' : 'btn primary block', onclick: () => startSession(null) }, next ? 'Entrenamiento libre' : '▶  ENTRENAMIENTO LIBRE'),
      ),
    last ? [h('div', { class: 'label' }, 'Último entrenamiento'),
      h('div', { class: 'card tap', onclick: () => go('#/session/' + last.id) },
        h('h3', null, last.name), h('div', { class: 'sub' }, `${dayText(last.startedAt)} · ${completedSets(last)} series · ${weightText(sessionVolume(last), S.settings.unit).replace(/^— /, '0 ')}`))] : null,
    !S.routines.length ? h('div', { class: 'card' }, h('h3', null, 'Crea tu primera rutina'), h('p', { class: 'sub' }, 'Usa una plantilla (Push, Pull, Piernas…) o empieza desde cero.'), h('button', { class: 'btn', onclick: () => go('#/routines') }, 'Ir a rutinas')) : null,
  );
}

// ================= Rutinas =================
export function routinesView() {
  const root = h('div', { class: 'screen has-nav' },
    h('div', { class: 'row between' }, h('h1', null, 'Rutinas'), h('button', { class: 'btn small primary', onclick: newRoutine }, '+ Nueva')),
    ...(S.routines.length ? S.routines.map(routineCard) : [empty('📋', 'Sin rutinas', 'Crea una o usa una plantilla.')]),
    h('div', { class: 'label' }, 'Plantillas'),
    ...TPL.map((t) => h('div', { class: 'card tap', onclick: async () => {
      const r = { id: uid(), name: t.name, days: [], items: t.items.map((it) => ({ ...newRoutineItem(it.exerciseId), sets: it.sets, repMin: it.repMin, repMax: it.repMax, restSec: it.restSec })) };
      await saveRoutine(r); toast('Rutina creada'); go('#/routine/' + r.id);
    } }, h('h3', null, t.name), h('div', { class: 'sub' }, `${t.description} · ${t.items.length} ejercicios`))),
  );
  return root;
}
function routineCard(r) {
  const muscles = [...new Set(r.items.map((i) => MUSCLES[exerciseById(i.exerciseId)?.muscle]).filter(Boolean))].slice(0, 3).join(' · ');
  return h('div', { class: 'card' },
    h('div', { class: 'row' },
      h('div', { class: 'grow tap', onclick: () => go('#/routine/' + r.id) }, h('h3', null, r.name || 'Sin nombre'), h('div', { class: 'sub' }, `${r.items.length} ejercicios${muscles ? ' · ' + muscles : ''}`)),
      h('button', { class: 'btn primary small', 'aria-label': 'Iniciar', onclick: () => r.items.length ? startSession(r) : toast('Añade ejercicios primero') }, '▶')));
}
async function newRoutine() {
  const r = { id: uid(), name: 'Nueva rutina', days: [], items: [] };
  await saveRoutine(r); go('#/routine/' + r.id);
}

export function routineEditView(id) {
  const r = S.routines.find((x) => x.id === id);
  if (!r) return h('div', { class: 'screen' }, header('Rutina', () => go('#/routines')), empty('🔍', 'Rutina no encontrada', 'Puede que se haya eliminado.'));
  const unit = S.settings.unit;
  const save = () => saveRoutine(r);
  const root = h('div', { class: 'screen' });
  function render() {
    root.replaceChildren(
      header('Editar rutina', () => go('#/routines')),
      h('input', { type: 'text', value: r.name, placeholder: 'Nombre de la rutina', oninput: (e) => { r.name = e.target.value; save(); } }),
      h('div', { class: 'label' }, `${r.items.length} ejercicios`),
      ...r.items.map((it, i) => {
        const ex = exerciseById(it.exerciseId);
        const sum = ex?.tracking === 'DURATION' ? `${it.sets} × ${it.durationSec || 30} s` : `${it.sets} × ${it.repMin === it.repMax ? it.repMin : it.repMin + '–' + it.repMax}`;
        return h('div', { class: 'card' }, h('div', { class: 'row' },
          h('div', { class: 'grow tap', onclick: () => editItem(it, ex) }, h('h3', null, ex?.name || 'Ejercicio'), h('div', { class: 'sub' }, `${sum} · ${it.restSec ? clock(it.restSec) : 'sin'} descanso`)),
          h('button', { class: 'icon-btn', 'aria-label': 'Subir', onclick: () => { if (i > 0) { [r.items[i - 1], r.items[i]] = [r.items[i], r.items[i - 1]]; save(); render(); } } }, '↑'),
          h('button', { class: 'icon-btn', 'aria-label': 'Bajar', onclick: () => { if (i < r.items.length - 1) { [r.items[i + 1], r.items[i]] = [r.items[i], r.items[i + 1]]; save(); render(); } } }, '↓'),
          h('button', { class: 'icon-btn', 'aria-label': 'Quitar', onclick: () => { r.items.splice(i, 1); save(); render(); } }, '✕')));
      }),
      h('div', { class: 'stack', style: { marginTop: '12px' } },
        h('button', { class: 'btn block', onclick: async () => { const exId = await pickExercise(); if (exId) { r.items.push(newRoutineItem(exId)); await save(); render(); } } }, '+  Agregar ejercicio'),
        h('button', { class: 'btn primary block', onclick: () => r.items.length ? startSession(r) : toast('Añade ejercicios primero') }, '▶  INICIAR'),
        h('button', { class: 'btn block danger', onclick: () => sheet('¿Eliminar rutina?', [['Eliminar', async () => { await removeRoutine(r.id); go('#/routines'); }, true]]) }, 'Eliminar rutina')),
    );
  }
  function editItem(it, ex) {
    const dur = ex?.tracking === 'DURATION';
    const stepper = (label, get, set, min, max) => {
      const val = h('b', { style: { width: '44px', textAlign: 'center' } }, String(get()));
      return h('div', { class: 'row between' }, h('span', null, label), h('div', { class: 'row' },
        h('button', { class: 'btn small', onclick: () => { set(Math.max(min, get() - 1)); val.textContent = get(); } }, '−'), val,
        h('button', { class: 'btn small', onclick: () => { set(Math.min(max, get() + 1)); val.textContent = get(); } }, '+')));
    };
    const w = h('input', { type: 'text', inputmode: 'decimal', value: it.startWeightKg != null ? num(toDisplay(it.startWeightKg, unit), 2) : '', placeholder: `Peso inicial opcional (${unit})`, oninput: (e) => { const p = parseNum(e.target.value); it.startWeightKg = p == null ? null : fromDisplay(p, unit); } });
    const restSel = h('select', { onchange: (e) => { it.restSec = Number(e.target.value); } }, ...[0, 30, 45, 60, 90, 120, 180, 240, 300].map((s) => h('option', { value: s, selected: s === it.restSec }, s ? clock(s) : 'Sin descanso')));
    sheet(ex?.name || 'Ejercicio', [], h('div', { class: 'stack' },
      stepper(dur ? 'Rondas' : 'Series', () => it.sets, (v) => { it.sets = v; }, 1, 20),
      ...(dur ? [stepper('Trabajo (s)', () => it.durationSec || 30, (v) => { it.durationSec = v; }, 5, 600), stepper('Ciclos', () => it.cycles || 1, (v) => { it.cycles = v; }, 1, 30), stepper('Descanso entre ciclos (s)', () => it.cycleRestSec ?? 30, (v) => { it.cycleRestSec = v; }, 0, 300)]
        : [stepper('Reps mínimas', () => it.repMin, (v) => { it.repMin = v; if (it.repMax < v) it.repMax = v; }, 1, 100), stepper('Reps máximas', () => it.repMax, (v) => { it.repMax = v; if (it.repMin > v) it.repMin = v; }, 1, 100)]),
      ex?.tracking === 'WEIGHT_REPS' ? w : null,
      h('div', null, h('div', { class: 'small muted' }, 'Descanso entre series'), restSel),
      h('button', { class: 'btn primary block', onclick: async () => { await save(); closeSheet(); render(); } }, 'Guardar')));
  }
  render();
  return root;
}

// ================= Selector de ejercicios (pantalla completa) =================
export function pickExercise() {
  return new Promise((resolve) => {
    let q = '', muscle = null, equip = null;
    const body = h('div', { class: 'body' });
    const ov = h('div', { class: 'overlay' },
      h('div', { class: 'header', style: { padding: '0 8px' } }, h('button', { class: 'icon-btn', 'aria-label': 'Cerrar', onclick: () => close(null) }, '✕'), h('h2', null, 'Agregar ejercicio'), h('button', { class: 'btn small', onclick: createCustom }, 'Crear')),
      h('div', { style: { padding: '0 16px 8px' } }, h('input', { type: 'search', placeholder: 'Buscar', oninput: (e) => { q = e.target.value; list(); } })),
      body);
    function close(v) { ov.remove(); resolve(v); }
    function chips(map, cur, set) {
      return h('div', { class: 'chips' }, h('button', { class: 'chip' + (cur() == null ? ' on' : ''), onclick: () => { set(null); draw(); } }, 'Todos'), ...Object.entries(map).map(([k, v]) => h('button', { class: 'chip' + (cur() === k ? ' on' : ''), onclick: () => { set(cur() === k ? null : k); draw(); } }, v)));
    }
    const listHost = h('div');
    function list() {
      const norm = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
      const items = allExercises().filter((e) => (!muscle || e.muscle === muscle || e.secondary?.includes(muscle)) && (!equip || e.equipment === equip) && (!q || norm(e.name).includes(norm(q))));
      listHost.replaceChildren(h('div', { class: 'small muted', style: { margin: '6px 0' } }, `${items.length} ejercicios`),
        ...items.map((e) => h('div', { class: 'card tap', onclick: () => close(e.id) }, h('h3', null, e.name), h('div', { class: 'sub' }, `${MUSCLES[e.muscle]} · ${EQUIPMENT[e.equipment]}${e.custom ? ' · Personalizado' : ''}`))));
    }
    function draw() { body.replaceChildren(chips(MUSCLES, () => muscle, (v) => { muscle = v; }), chips(EQUIPMENT, () => equip, (v) => { equip = v; }), listHost); list(); }
    function createCustom() {
      const name = h('input', { type: 'text', placeholder: 'Nombre' });
      const mus = h('select', null, ...Object.entries(MUSCLES).map(([k, v]) => h('option', { value: k }, v)));
      const eq = h('select', null, ...Object.entries(EQUIPMENT).map(([k, v]) => h('option', { value: k }, v)));
      const tr = h('select', null, ...Object.entries(TRACKING).map(([k, v]) => h('option', { value: k }, v)));
      sheet('Nuevo ejercicio', [], h('div', { class: 'stack' }, name, mus, eq, tr, h('button', { class: 'btn primary block', onclick: async () => {
        if (!name.value.trim()) { toast('Escribe un nombre'); return; }
        const ex = { id: 'custom_' + uid(), name: name.value.trim(), muscle: mus.value, secondary: [], equipment: eq.value, movement: 'ISOLATION', tracking: tr.value, unilateral: false, description: '', cues: '', custom: true };
        await saveCustomExercise(ex); closeSheet(); close(ex.id);
      } }, 'Guardar')));
    }
    document.body.append(ov); draw();
  });
}

// ================= Historial =================
export function historyView() {
  const list = finished();
  return h('div', { class: 'screen has-nav' }, h('h1', null, 'Historial'),
    list.length ? list.map((s) => h('div', { class: 'card tap', onclick: () => go('#/session/' + s.id) },
      h('div', { class: 'row between' }, h('h3', null, s.name), h('span', { class: 'sub' }, dayText(s.startedAt))),
      h('div', { class: 'sub' }, `${clock((s.endedAt - s.startedAt) / 1000)} · ${completedSets(s)} series · ${weightText(sessionVolume(s), S.settings.unit).replace(/^— /, '0 ')}`)))
      : empty('📅', 'Aún no hay entrenamientos', 'Cuando finalices uno, aparecerá aquí.'));
}
export function sessionView(id) {
  const s = S.sessions.find((x) => x.id === id);
  const unit = S.settings.unit;
  if (!s) return h('div', { class: 'screen' }, header('Sesión', () => go('#/history')), empty('🔍', 'No encontrada', ''));
  return h('div', { class: 'screen' }, header(s.name, () => go('#/history')),
    h('p', { class: 'sub' }, `${fullDate(s.startedAt)} · ${clock((s.endedAt - s.startedAt) / 1000)} · ${weightText(sessionVolume(s), unit).replace(/^— /, '0 ')}`),
    s.notes ? h('div', { class: 'card' }, s.notes) : null,
    ...s.exercises.map((e) => h('div', { class: 'card' }, h('h3', null, e.name),
      ...e.sets.map((x, i) => h('div', { class: 'kv' }, h('span', { style: { color: x.type === 'W' ? 'var(--warn)' : '' } }, x.type === 'N' ? `Serie ${e.sets.filter((y) => y.type === 'N').indexOf(x) + 1}` : x.type === 'W' ? 'Calentamiento' : x.type === 'D' ? 'Drop set' : 'Al fallo'),
        h('span', null, e.tracking === 'DURATION' ? `${x.durationSec} s` : e.tracking === 'DISTANCE' ? `${num((x.distanceM || 0) / 1000, 2)} km · ${clock(x.durationSec || 0)}` : e.tracking === 'BODYWEIGHT' ? `${x.reps} reps` : `${num(toDisplay(x.weightKg, unit))} ${unit} × ${x.reps}`))))),
    h('button', { class: 'btn block danger', onclick: () => sheet('¿Eliminar sesión?', [['Eliminar', async () => { await removeSession(s.id); go('#/history'); }, true]]) }, 'Eliminar sesión'));
}

// ================= Progreso =================
export function progressView() {
  const done = finished();
  const root = h('div', { class: 'screen has-nav' }, h('h1', null, 'Progreso'));
  root.append(h('div', { class: 'card tap', onclick: () => go('#/body') }, h('div', { class: 'row' }, h('span', { style: { fontSize: '24px' } }, '📸'), h('div', { class: 'grow' }, h('h3', null, 'FOTOS Y PESO CORPORAL'), h('div', { class: 'sub' }, 'Compara tus cambios · solo en tu teléfono')))));
  if (!done.length) { root.append(empty('📈', 'Aún no hay datos', 'Finaliza tu primer entrenamiento para ver la evolución de tus cargas y tus récords.')); return root; }
  const unit = S.settings.unit;
  const used = new Map();
  done.forEach((s) => s.exercises.forEach((e) => { if (e.sets.some((x) => x.completed && isWorking(x))) used.set(e.exerciseId, e.name); }));
  let cur = [...used.keys()][0], metric = 'e1rm';
  const chartHost = h('div'), prHost = h('div');
  const sel = h('select', { onchange: (e) => { cur = e.target.value; draw(); } }, ...[...used].map(([id, n]) => h('option', { value: id }, n)));
  const metricSel = h('select', { onchange: (e) => { metric = e.target.value; draw(); } }, h('option', { value: 'e1rm' }, '1RM estimado'), h('option', { value: 'weight' }, 'Peso máximo'), h('option', { value: 'reps' }, 'Repeticiones máx.'), h('option', { value: 'volume' }, 'Volumen del ejercicio'));
  function draw() {
    const pts = done.slice().reverse().map((s) => { const e = s.exercises.find((x) => x.exerciseId === cur); if (!e) return null; const b = bestOf(e); const v = metric === 'volume' ? e.sets.filter((x) => x.completed && isWorking(x)).reduce((a, x) => a + (x.weightKg || 0) * (x.reps || 0), 0) : b[metric]; return v > 0 ? { t: s.startedAt, v: metric === 'reps' ? v : toDisplay(v, unit) } : null; }).filter(Boolean);
    chartHost.replaceChildren(pts.length >= 2 ? lineChart(pts, metric === 'reps' ? '' : unit) : h('p', { class: 'sub' }, 'Necesitas al menos 2 sesiones con este ejercicio para ver la gráfica.'));
    let bw = 0, b1 = 0, br = 0;
    done.forEach((s) => s.exercises.filter((x) => x.exerciseId === cur).forEach((e) => { const b = bestOf(e); bw = Math.max(bw, b.weight); b1 = Math.max(b1, b.e1rm); br = Math.max(br, b.reps); }));
    prHost.replaceChildren(h('div', { class: 'card' }, h('h3', null, 'Récords'), h('div', { class: 'kv' }, h('span', null, 'Peso máximo'), h('b', null, weightText(bw, unit))), h('div', { class: 'kv' }, h('span', null, '1RM estimado'), h('b', null, weightText(b1, unit))), h('div', { class: 'kv' }, h('span', null, 'Repeticiones máx.'), h('b', null, br))));
  }
  // sesiones por semana (8 semanas)
  const weeks = []; const w0 = startOfWeek(Date.now());
  for (let i = 7; i >= 0; i--) { const a = w0 - i * 7 * 864e5; weeks.push({ a, n: done.filter((s) => s.startedAt >= a && s.startedAt < a + 7 * 864e5).length }); }
  root.append(h('div', { class: 'label' }, 'Evolución por ejercicio'), h('div', { class: 'card stack' }, sel, metricSel, chartHost), prHost,
    h('div', { class: 'label' }, 'Sesiones por semana'), h('div', { class: 'card' }, barChart(weeks.map((w) => ({ label: new Date(w.a).getDate(), v: w.n })))));
  draw();
  return root;
}
function sizeCanvas(c, w, hgt) { const dpr = window.devicePixelRatio || 1; c.width = w * dpr; c.height = hgt * dpr; c.style.height = hgt + 'px'; const g = c.getContext('2d'); g.scale(dpr, dpr); return g; }
export function lineChart(pts, unit) {
  const c = h('canvas'); const W = 340, H = 170;
  requestAnimationFrame(() => {
    const g = sizeCanvas(c, W, H);
    const vs = pts.map((p) => p.v), min = Math.min(...vs), max = Math.max(...vs), span = max - min || 1, pad = 28;
    const X = (i) => pad + (i * (W - pad * 2)) / Math.max(1, pts.length - 1), Y = (v) => H - 26 - ((v - min) / span) * (H - 54);
    g.strokeStyle = '#ff1e46'; g.lineWidth = 2.5; g.lineJoin = 'round'; g.beginPath(); pts.forEach((p, i) => (i ? g.lineTo(X(i), Y(p.v)) : g.moveTo(X(i), Y(p.v)))); g.stroke();
    const grad = g.createLinearGradient(0, 0, 0, H); grad.addColorStop(0, 'rgba(255,30,70,.25)'); grad.addColorStop(1, 'rgba(255,30,70,0)');
    g.lineTo(X(pts.length - 1), H - 26); g.lineTo(X(0), H - 26); g.closePath(); g.fillStyle = grad; g.fill();
    g.fillStyle = '#ff1e46'; pts.forEach((p, i) => { g.beginPath(); g.arc(X(i), Y(p.v), 3.5, 0, 7); g.fill(); });
    g.fillStyle = '#a1a1aa'; g.font = '11px sans-serif'; g.textAlign = 'left'; g.fillText(`${num(min)} ${unit}`, 4, H - 8); g.textAlign = 'right'; g.fillText(`${num(max)} ${unit}`, W - 4, 12);
    g.textAlign = 'left'; g.fillText(dayText(pts[0].t), 4, H - 8 - 0 + 0); g.fillStyle = '#71717a';
  });
  return c;
}
function barChart(data) {
  const c = h('canvas'); const W = 340, H = 120;
  requestAnimationFrame(() => {
    const g = sizeCanvas(c, W, H); const max = Math.max(1, ...data.map((d) => d.v)); const bw = (W - 20) / data.length;
    data.forEach((d, i) => { const bh = (d.v / max) * (H - 34); g.fillStyle = i === data.length - 1 ? '#ff1e46' : '#3a3a44'; g.beginPath(); g.roundRect(10 + i * bw + 5, H - 20 - bh, bw - 10, Math.max(bh, 2), 4); g.fill(); g.fillStyle = '#a1a1aa'; g.font = '11px sans-serif'; g.textAlign = 'center'; g.fillText(String(d.label), 10 + i * bw + bw / 2, H - 4); if (d.v) g.fillText(String(d.v), 10 + i * bw + bw / 2, H - 24 - bh); });
  });
  return c;
}

// ================= Ajustes / copia de seguridad =================
export function settingsView() {
  const s = S.settings;
  const upd = async () => { await saveSettings(); };
  const fileIn = h('input', { type: 'file', accept: 'application/json,.json', style: { display: 'none' }, onchange: importBackup });
  return h('div', { class: 'screen has-nav' }, header('Ajustes', null), h('h1', { style: { display: 'none' } }),
    h('div', { class: 'label' }, 'Unidades'),
    h('div', { class: 'chips' }, ...['kg', 'lb'].map((u) => h('button', { class: 'chip' + (s.unit === u ? ' on' : ''), onclick: async () => { s.unit = u; await upd(); go('#/settings'); location.reload(); } }, u))),
    h('div', { class: 'label' }, 'Descanso por defecto'),
    h('select', { onchange: async (e) => { s.restSec = Number(e.target.value); await upd(); } }, ...[30, 45, 60, 90, 120, 180, 240].map((v) => h('option', { value: v, selected: v === s.restSec }, clock(v)))),
    h('div', { class: 'label' }, 'Descanso al cambiar de ejercicio'),
    h('select', { onchange: async (e) => { s.changeRestSec = Number(e.target.value); await upd(); } }, ...[0, 60, 90, 120, 180, 240, 300].map((v) => h('option', { value: v, selected: v === (s.changeRestSec ?? 120) }, v ? clock(v) : 'Sin descanso'))),
    h('p', { class: 'small muted' }, 'Se usa al completar la última serie de un ejercicio y pasar al siguiente.'),
    h('div', { class: 'label' }, 'Aviso de fin de descanso'),
    h('div', { class: 'chips' }, h('button', { class: 'chip' + (s.sound ? ' on' : ''), onclick: async () => { s.sound = !s.sound; await upd(); location.reload(); } }, s.sound ? 'Sonido activado' : 'Sonido desactivado')),
    h('p', { class: 'small muted' }, 'En iPhone la web no puede vibrar ni avisar con la pantalla bloqueada: mantén la app abierta durante el descanso.'),
    h('div', { class: 'label' }, 'Copia de seguridad'),
    h('div', { class: 'stack' },
      h('button', { class: 'btn block', onclick: exportBackup }, 'Exportar copia (JSON)'),
      h('button', { class: 'btn block', onclick: () => fileIn.click() }, 'Importar copia'), fileIn,
      h('p', { class: 'small muted' }, 'La copia incluye rutinas, historial, ejercicios propios y peso corporal. Las fotos NO se incluyen.')),
    h('div', { class: 'label' }, 'Almacenamiento'),
    h('p', { class: 'small muted' }, 'Todo se guarda solo en este dispositivo. Si borras los datos del sitio o desinstalas la app, se pierde: exporta copias con frecuencia.'),
  );
}
async function exportBackup() {
  const data = { app: 'gymtrack-web', version: 1, exportedAt: Date.now(), settings: S.settings, routines: S.routines, sessions: S.sessions, customExercises: S.custom, weights: S.weights };
  const blob = new Blob([JSON.stringify(data)], { type: 'application/json' });
  const file = new File([blob], `gymtrack-${new Date().toISOString().slice(0, 10)}.json`, { type: 'application/json' });
  try { if (navigator.canShare?.({ files: [file] })) { await navigator.share({ files: [file], title: 'Copia GymTrack' }); return; } } catch (e) { if (e.name === 'AbortError') return; }
  const a = h('a', { href: URL.createObjectURL(blob), download: file.name }); document.body.append(a); a.click(); a.remove();
}
async function importBackup(e) {
  const f = e.target.files[0]; if (!f) return;
  try {
    const d = JSON.parse(await f.text());
    if (d.app !== 'gymtrack-web' || !Array.isArray(d.sessions) || !Array.isArray(d.routines)) throw new Error('Archivo no válido');
    sheet('¿Reemplazar los datos actuales?', [], h('div', { class: 'stack' }, h('p', { class: 'sub' }, `La copia tiene ${d.routines.length} rutinas y ${d.sessions.length} sesiones. Se sustituirán los datos actuales.`),
      h('button', { class: 'btn primary block', onclick: async () => {
        for (const st of ['routines', 'sessions', 'customExercises', 'weights']) await db.clear(st);
        for (const r of d.routines) await db.put('routines', r);
        for (const s of d.sessions) await db.put('sessions', s);
        for (const x of d.customExercises || []) await db.put('customExercises', x);
        for (const w of d.weights || []) await db.put('weights', w);
        await db.setSetting('settings', d.settings || {}); await loadAll(); closeSheet(); toast('Copia importada'); go('#/');
      } }, 'Importar'), h('button', { class: 'btn block', onclick: closeSheet }, 'Cancelar')));
  } catch (err) { toast('No se pudo leer la copia: ' + err.message); }
  e.target.value = '';
}
