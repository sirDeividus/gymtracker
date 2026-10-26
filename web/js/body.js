import { S } from './state.js';
import { db } from './db.js';
import { h, uid, num, parseNum, toDisplay, fromDisplay, weightText, dayText, fullDate, toast, sheet, closeSheet } from './util.js';
import { lineChart } from './views.js';
import { go } from './state.js';

const POSES = { FRONT: 'Frente', SIDE: 'Perfil', BACK: 'Espalda', OTHER: 'Otra' };
const urls = new Map();
const urlOf = (p) => { if (!urls.has(p.id)) urls.set(p.id, URL.createObjectURL(p.blob)); return urls.get(p.id); };

/** Reduce la foto a ≤1600 px (respeta la orientación EXIF) y la devuelve como Blob JPEG. */
async function shrink(file) {
  const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const k = Math.min(1, 1600 / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas'); c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('No se pudo procesar la imagen'))), 'image/jpeg', 0.85));
}

export function bodyView() {
  const unit = S.settings.unit;
  const root = h('div', { class: 'screen' });
  let comparing = false, picked = [];
  function render() {
    const ws = S.weights.slice().sort((a, b) => a.measuredAt - b.measuredAt);
    const first = ws[0], last = ws[ws.length - 1];
    root.replaceChildren(
      h('div', { class: 'header' }, h('button', { class: 'icon-btn', 'aria-label': 'Volver', onclick: () => go('#/progress') }, '‹'), h('h2', null, 'Fotos y peso')),
      h('p', { class: 'sub' }, 'Guardado solo en tu teléfono'),
      h('div', { class: 'card stack' }, h('div', { class: 'label', style: { margin: 0 } }, 'Peso corporal'),
        last ? [h('div', { style: { fontSize: '28px', fontWeight: 700 } }, weightText(last.weightKg, unit)), ws.length > 1 ? h('div', { class: 'sub' }, `${last.weightKg - first.weightKg > 0 ? '+' : ''}${num(toDisplay(last.weightKg - first.weightKg, unit))} ${unit} desde el primer registro`) : null,
          ws.length > 1 ? lineChart(ws.map((w) => ({ t: w.measuredAt, v: toDisplay(w.weightKg, unit) })), unit) : null] : h('p', { class: 'sub' }, 'Registra tu peso para ver la tendencia.'),
        h('button', { class: 'btn', onclick: logWeight }, '⚖︎  REGISTRAR PESO')),
      h('div', { class: 'label' }, 'Fotos de progreso'),
      h('div', { class: 'row' }, h('button', { class: 'btn primary block', onclick: addPhoto }, '📷  AÑADIR FOTO'), S.photos.length >= 2 ? h('button', { class: 'btn block', onclick: () => { comparing = !comparing; picked = []; render(); } }, comparing ? 'CANCELAR' : 'COMPARAR') : null),
      comparing ? h('p', { class: 'small muted' }, `Elige dos fotos (${picked.length}/2)`) : null,
      S.photos.length ? h('div', { class: 'grid3', style: { marginTop: '10px' } }, ...S.photos.map((p) => h('div', { class: 'photo' + (picked.includes(p.id) ? ' sel' : ''), style: { backgroundImage: `url(${urlOf(p)})` }, onclick: () => {
        if (comparing) { picked = picked.includes(p.id) ? picked.filter((x) => x !== p.id) : picked.length < 2 ? [...picked, p.id] : picked; if (picked.length === 2) return compare(); render(); } else detail(p);
      } }, h('small', null, `${dayText(p.takenAt)} · ${POSES[p.pose]}`))))
        : h('div', { class: 'empty' }, h('div', { class: 'big' }, '🖼️'), h('p', null, 'Haz una foto cada pocas semanas con la misma luz y pose. Se guardan privadas en tu teléfono.')),
    );
  }
  function logWeight() {
    const i = h('input', { type: 'text', inputmode: 'decimal', placeholder: unit });
    sheet('Registrar peso', [], h('div', { class: 'stack' }, i, h('button', { class: 'btn primary block', onclick: async () => {
      const p = parseNum(i.value); const kg = p == null ? null : fromDisplay(p, unit);
      if (kg == null || kg < 20 || kg > 500) { toast('El peso debe estar entre 20 y 500 kg'); return; }
      const w = { id: uid(), measuredAt: Date.now(), weightKg: kg }; S.weights.push(w); await db.put('weights', w); closeSheet(); render();
    } }, 'Guardar')));
  }
  function addPhoto() {
    let pose = 'FRONT'; let file = null;
    const chips = h('div', { class: 'chips' });
    const drawChips = () => chips.replaceChildren(...Object.entries(POSES).map(([k, v]) => h('button', { class: 'chip' + (pose === k ? ' on' : ''), onclick: () => { pose = k; drawChips(); } }, v)));
    drawChips();
    const wt = h('input', { type: 'text', inputmode: 'decimal', placeholder: `Peso (${unit}, opcional)` });
    const note = h('input', { type: 'text', placeholder: 'Nota (opcional)' });
    const status = h('p', { class: 'small muted' }, 'Ninguna imagen elegida');
    const inp = h('input', { type: 'file', accept: 'image/*', style: { display: 'none' }, onchange: (e) => { file = e.target.files[0] || null; status.textContent = file ? 'Imagen seleccionada ✓' : 'Ninguna imagen elegida'; } });
    sheet('Nueva foto', [], h('div', { class: 'stack' }, chips, wt, note, h('button', { class: 'btn block', onclick: () => inp.click() }, '📷  Cámara o galería'), inp, status,
      h('button', { class: 'btn primary block', onclick: async () => {
        if (!file) { toast('Elige una imagen'); return; }
        const p = parseNum(wt.value); const kg = p == null ? null : fromDisplay(p, unit);
        if (wt.value && (kg == null || kg < 20 || kg > 500)) { toast('Peso no válido'); return; }
        try { const blob = await shrink(file); const rec = { id: uid(), takenAt: Date.now(), pose, weightKg: kg, note: note.value.trim(), blob }; await db.put('photos', rec); S.photos.unshift(rec); closeSheet(); render(); }
        catch (e) { toast('No se pudo guardar la foto: ' + e.message); }
      } }, 'Guardar')));
  }
  function detail(p) {
    sheet(`${POSES[p.pose]} · ${fullDate(p.takenAt)}`, [], h('div', { class: 'stack' }, h('img', { src: urlOf(p), style: { width: '100%', borderRadius: '12px' }, alt: 'Foto de progreso' }), p.weightKg ? h('p', { class: 'sub' }, weightText(p.weightKg, unit)) : null, p.note ? h('p', { class: 'sub' }, p.note) : null,
      h('button', { class: 'btn block danger', onclick: async () => { await db.del('photos', p.id); S.photos = S.photos.filter((x) => x.id !== p.id); closeSheet(); render(); } }, 'Eliminar foto')));
  }
  function compare() {
    const [a, b] = picked.map((id) => S.photos.find((p) => p.id === id)).sort((x, y) => x.takenAt - y.takenAt);
    const days = Math.round((b.takenAt - a.takenAt) / 864e5);
    const dw = a.weightKg != null && b.weightKg != null ? b.weightKg - a.weightKg : null;
    sheet('Antes y después', [], h('div', { class: 'stack' }, h('div', { class: 'row' }, ...[[a, 'ANTES'], [b, 'DESPUÉS']].map(([p, l]) => h('div', { class: 'grow' }, h('img', { src: urlOf(p), style: { width: '100%', borderRadius: '12px', aspectRatio: '3/4', objectFit: 'cover' }, alt: l }), h('div', { class: 'small muted' }, l), h('div', { class: 'small' }, fullDate(p.takenAt))))),
      h('p', { class: 'sub' }, `${days} días de diferencia${dw != null ? ` · ${dw > 0 ? '+' : ''}${num(toDisplay(dw, unit))} ${unit}` : ''}`)));
    comparing = false; picked = []; render();
  }
  render();
  return root;
}
