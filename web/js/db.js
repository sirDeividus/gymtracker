// Almacenamiento local (IndexedDB). Todo queda en el dispositivo; no hay cuenta ni servidor.
const NAME = 'gymtrack';
const VERSION = 1;
const STORES = ['routines', 'sessions', 'weights', 'photos', 'customExercises', 'kv'];
let dbp;

function open() {
  if (dbp) return dbp;
  dbp = new Promise((resolve, reject) => {
    const req = indexedDB.open(NAME, VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      for (const s of STORES) if (!db.objectStoreNames.contains(s)) db.createObjectStore(s, { keyPath: s === 'kv' ? 'key' : 'id' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbp;
}

async function tx(store, mode, fn) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, mode);
    const out = fn(t.objectStore(store));
    t.oncomplete = () => resolve(out && 'result' in out ? out.result : undefined);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}

export const db = {
  getAll: (s) => tx(s, 'readonly', (st) => st.getAll()),
  get: (s, id) => tx(s, 'readonly', (st) => st.get(id)),
  put: (s, v) => tx(s, 'readwrite', (st) => st.put(v)),
  del: (s, id) => tx(s, 'readwrite', (st) => st.delete(id)),
  clear: (s) => tx(s, 'readwrite', (st) => st.clear()),
  async getSetting(key, fallback) {
    const r = await this.get('kv', key);
    return r ? r.value : fallback;
  },
  setSetting: (key, value) => tx('kv', 'readwrite', (st) => st.put({ key, value })),
};

export async function askPersistentStorage() {
  try { if (navigator.storage?.persist) return await navigator.storage.persist(); } catch { /* sin soporte */ }
  return false;
}
