// Lógica de entrenamiento (puerto de la parte Kotlin `domain/`).
export const isWorking = (s) => s.type !== 'W';

export const epley1RM = (w, r) => (w > 0 && r > 0 ? (r === 1 ? w : w * (1 + r / 30)) : 0);

export function exerciseVolume(ex) {
  return ex.sets.filter((s) => s.completed && isWorking(s)).reduce((a, s) => a + Math.max(0, s.weightKg || 0) * (s.reps || 0), 0);
}
export const sessionVolume = (se) => se.exercises.reduce((a, e) => a + exerciseVolume(e), 0);
export const completedSets = (se) => se.exercises.reduce((a, e) => a + e.sets.filter((s) => s.completed && isWorking(s)).length, 0);

export function bestOf(ex) {
  let weight = 0, e1rm = 0, reps = 0;
  for (const s of ex.sets) {
    if (!s.completed || !isWorking(s)) continue;
    weight = Math.max(weight, s.weightKg || 0);
    e1rm = Math.max(e1rm, epley1RM(s.weightKg || 0, s.reps || 0));
    reps = Math.max(reps, s.reps || 0);
  }
  return { weight, e1rm, reps };
}

/** Última sesión finalizada (más reciente primero) que contiene el ejercicio. */
export function lastPerformance(sessions, exerciseId, beforeId) {
  const list = sessions.filter((s) => s.status === 'finished' && s.id !== beforeId).sort((a, b) => b.startedAt - a.startedAt);
  for (const se of list) {
    const ex = se.exercises.find((e) => e.exerciseId === exerciseId);
    if (ex && ex.sets.some((s) => s.completed && isWorking(s))) return ex;
  }
  return null;
}

const STEP_KG = { DUMBBELL: 2, MACHINE: 2.5, CABLE: 2.5, BARBELL: 2.5, BODYWEIGHT: 2.5, OTHER: 2.5 };
const LOWER = new Set(['QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES']);
export function stepFor(ex) {
  const base = STEP_KG[ex.equipment] ?? 2.5;
  return ex.equipment === 'BARBELL' && LOWER.has(ex.muscle) ? 5 : base;
}

/** Sugerencia de progresión: nunca automática; solo se muestra y el usuario decide. */
export function suggest(prev, exDef, repMax, unitStep) {
  if (!prev) return { kind: 'first', text: 'Primera vez · introduce una carga inicial' };
  const work = prev.sets.filter((s) => s.completed && isWorking(s));
  if (!work.length) return null;
  const top = Math.max(...work.map((s) => s.weightKg || 0));
  if (exDef.tracking !== 'WEIGHT_REPS' || top <= 0) return null;
  const atTop = work.filter((s) => (s.weightKg || 0) === top);
  const allHit = atTop.length === work.length && atTop.every((s) => (s.reps || 0) >= repMax);
  const hardRpe = work.some((s) => s.rpe && s.rpe >= 9);
  if (allHit && !hardRpe) return { kind: 'up', weightKg: top + unitStep, text: `Completaste ${work.length}×${repMax} con ${top} kg. Si el esfuerzo fue adecuado, prueba ${top + unitStep} kg.` };
  return { kind: 'hold', text: `Mantén ${top} kg y busca llegar a ${repMax} repeticiones en todas las series.` };
}

export function weekStreak(sessions, now = Date.now()) {
  const weeks = new Set(sessions.filter((s) => s.status === 'finished').map((s) => weekStart(s.startedAt)));
  let streak = 0, w = weekStart(now);
  if (!weeks.has(w)) w -= 7 * 864e5;
  while (weeks.has(w)) { streak++; w -= 7 * 864e5; }
  return streak;
}
function weekStart(ms) {
  const d = new Date(ms); d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.getTime();
}

export function warmupSets(workKg) {
  if (!workKg || workKg < 20) return [];
  const mk = (pct, reps) => ({ weightKg: Math.round((workKg * pct) / 2.5) * 2.5, reps });
  return [mk(0.4, 8), mk(0.6, 5), mk(0.8, 3)];
}

// ---- Intervalos / Tabata (puerto de IntervalTimer.kt): trabajo/descanso × rondas × ciclos
export function planTotalSec(p) {
  return p.prepSec + p.rounds * p.cycles * p.workSec + (p.rounds - 1) * p.restSec * p.cycles + (p.cycles - 1) * p.cycleRestSec;
}
function timeline(p) {
  const out = []; let done = 0;
  for (let c = 1; c <= p.cycles; c++) {
    for (let r = 1; r <= p.rounds; r++) {
      out.push({ phase: 'WORK', cycle: c, round: r, ms: p.workSec * 1000, done }); done++;
      const last = r === p.rounds;
      if (!last && p.restSec > 0) out.push({ phase: 'REST', cycle: c, round: r, ms: p.restSec * 1000, done });
      if (last && c < p.cycles && p.cycleRestSec > 0) out.push({ phase: 'CYCLE_REST', cycle: c, round: r, ms: p.cycleRestSec * 1000, done });
    }
  }
  return out;
}
export function intervalSnapshot(p, elapsedMs) {
  const e = Math.max(0, elapsedMs), prep = p.prepSec * 1000;
  const totalMs = planTotalSec(p) * 1000;
  if (e < prep) return { phase: 'PREP', cycle: 0, round: 0, remainingMs: prep - e, totalMs: prep, completed: 0, totalLeftMs: totalMs - e };
  let t = e - prep;
  for (const s of timeline(p)) {
    if (t < s.ms) return { phase: s.phase, cycle: s.cycle, round: s.round, remainingMs: s.ms - t, totalMs: s.ms, completed: s.done, totalLeftMs: Math.max(0, totalMs - e) };
    t -= s.ms;
  }
  return { phase: 'DONE', cycle: p.cycles, round: p.rounds, remainingMs: 0, totalMs: 0, completed: p.rounds * p.cycles, totalLeftMs: 0 };
}

/** Todos los tramos en orden, empezando por la preparación (si la hay). */
export function intervalSchedule(p) {
  const out = p.prepSec > 0 ? [{ phase: 'PREP', cycle: 0, round: 0, seconds: p.prepSec }] : [];
  return out.concat(timeline(p).map((s) => ({ phase: s.phase, cycle: s.cycle, round: s.round, seconds: s.ms / 1000 })));
}
/** Fase del tramo siguiente al actual ('DONE' si es el último). */
export function nextPhase(p, elapsedMs) {
  const e = Math.max(0, elapsedMs), prep = p.prepSec * 1000, steps = timeline(p);
  if (e < prep) return steps[0].phase;
  let t = e - prep;
  for (let i = 0; i < steps.length; i++) { if (t < steps[i].ms) return steps[i + 1]?.phase ?? 'DONE'; t -= steps[i].ms; }
  return 'DONE';
}
