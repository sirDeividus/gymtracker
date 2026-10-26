import { loadAll, S, activeSession, go } from './state.js';
import { askPersistentStorage } from './db.js';
import { h } from './util.js';
import { homeView, routinesView, routineEditView, historyView, sessionView, progressView, settingsView } from './views.js';
import { workoutView, stopWorkoutTimers } from './workout.js';
import { bodyView } from './body.js';

const app = document.getElementById('app');
const TABS = [['#/', 'Inicio', '🏠'], ['#/routines', 'Rutinas', '📋'], ['#/history', 'Historial', '📅'], ['#/progress', 'Progreso', '📈'], ['#/settings', 'Ajustes', '⚙️']];

function route() {
  stopWorkoutTimers();
  const hash = location.hash || '#/';
  const [, a, b] = hash.split('/');
  let view, tab = hash;
  try {
    if (hash === '#/' || hash === '#') view = homeView();
    else if (a === 'routines') view = routinesView();
    else if (a === 'routine') { view = routineEditView(b); tab = '#/routines'; }
    else if (a === 'history') view = historyView();
    else if (a === 'session') { view = sessionView(b); tab = '#/history'; }
    else if (a === 'progress') view = progressView();
    else if (a === 'body') { view = bodyView(); tab = '#/progress'; }
    else if (a === 'settings') view = settingsView();
    else if (a === 'workout') { view = workoutView(); tab = null; }
    else view = homeView();
  } catch (e) { console.error(e); view = h('div', { class: 'screen' }, h('h2', null, 'Algo salió mal'), h('p', { class: 'sub' }, String(e.message))); }
  const bar = tab && a !== 'workout' ? h('nav', { class: 'nav' }, ...TABS.map(([hs, label, ic]) => h('button', { class: hs === tab ? 'on' : '', onclick: () => go(hs) }, h('span', { class: 'ic' }, ic), label))) : null;
  app.replaceChildren(...[view, bar].filter(Boolean));
  if (!['workout'].includes(a)) view.scrollTop = 0;
}

window.addEventListener('hashchange', route);
(async () => {
  await loadAll();
  askPersistentStorage();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  if (!location.hash && activeSession()) location.hash = '#/workout';
  route();
})();
