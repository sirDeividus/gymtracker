import { db } from './db.js';
import EXERCISES from '../data/exercises.js';
import TEMPLATES from '../data/templates.js';
import { uid } from './util.js';

export const S = {
  settings: { unit: 'kg', restSec: 90, sound: true },
  custom: [],
  routines: [],
  sessions: [],
  weights: [],
  photos: [],
  render: () => {},
};

export const TPL = TEMPLATES.filter((t) => t.items.length > 0);
export const allExercises = () => [...EXERCISES, ...S.custom];
export const exerciseById = (id) => allExercises().find((e) => e.id === id);

export async function loadAll() {
  S.settings = { ...S.settings, ...(await db.getSetting('settings', {})) };
  [S.custom, S.routines, S.sessions, S.weights] = await Promise.all([
    db.getAll('customExercises'), db.getAll('routines'), db.getAll('sessions'), db.getAll('weights'),
  ]);
  S.photos = (await db.getAll('photos')).sort((a, b) => b.takenAt - a.takenAt);
}
export const saveSettings = () => db.setSetting('settings', S.settings);
export const activeSession = () => S.sessions.find((s) => s.status === 'active') || null;
export const finished = () => S.sessions.filter((s) => s.status === 'finished').sort((a, b) => b.startedAt - a.startedAt);

export async function saveSession(se) {
  const i = S.sessions.findIndex((x) => x.id === se.id);
  if (i >= 0) S.sessions[i] = se; else S.sessions.push(se);
  await db.put('sessions', se);
}
export async function removeSession(id) {
  S.sessions = S.sessions.filter((s) => s.id !== id);
  await db.del('sessions', id);
}
export async function saveRoutine(r) {
  const i = S.routines.findIndex((x) => x.id === r.id);
  if (i >= 0) S.routines[i] = r; else S.routines.push(r);
  await db.put('routines', r);
}
export async function removeRoutine(id) {
  S.routines = S.routines.filter((r) => r.id !== id);
  await db.del('routines', id);
}
export async function saveCustomExercise(e) {
  S.custom.push(e); await db.put('customExercises', e);
}
export function newRoutineItem(exId) {
  const ex = exerciseById(exId);
  const dur = ex?.tracking === 'DURATION';
  return { id: uid(), exerciseId: exId, sets: 3, repMin: dur ? 1 : 8, repMax: dur ? 1 : 12, restSec: S.settings.restSec, startWeightKg: null, durationSec: dur ? 30 : null };
}

export function go(hash) { location.hash = hash; }
