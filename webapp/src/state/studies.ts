// The indicators on the chart, as TradingView keeps them: one list for the layout (not per symbol),
// each with its inputs, shown or hidden. Persisted in this browser.
import { create } from 'zustand';
import { REGISTRY } from '../engine/indicators.js';

export interface Study { uid: string; key: string; inputs: Record<string, number | string | boolean>; hidden?: boolean }

/** The volume histogram drawn under the price, as TradingView adds it to every new chart. */
export const VOLUME = '__volume';

const KEY = 'pc.studies.v1';
const FAV_KEY = 'pc.studies.fav.v1';

function read<T>(key: string, fallback: T): T {
  try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; return v ?? fallback; } catch { return fallback; }
}
function write(key: string, v: unknown) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* ignore */ } }

interface StudiesState {
  list: Study[];
  favourites: string[];
  add(key: string): void;
  remove(uid: string): void;
  toggle(uid: string): void;
  setInputs(uid: string, inputs: Study['inputs']): void;
  toggleFavourite(key: string): void;
  clear(): void;
  /** A copy of a study with the same inputs, added after it. */
  duplicate(uid: string): void;
  /** Moves a study one place up (−1) or down (+1) in the list, which is the order of the panes. */
  move(uid: string, dir: -1 | 1): void;
}

const initial = read<Study[]>(KEY, [{ uid: 'vol', key: VOLUME, inputs: {} }]).filter((s) => s.key === VOLUME || REGISTRY[s.key]);

export const useStudies = create<StudiesState>((set, get) => {
  const save = () => write(KEY, get().list);
  return {
    list: initial,
    favourites: read<string[]>(FAV_KEY, []),
    add: (key) => {
      const def = REGISTRY[key];
      if (!def) return;
      set({ list: [...get().list, { uid: `${key}-${Date.now().toString(36)}`, key, inputs: { ...def.inputs } }] });
      save();
    },
    remove: (uid) => { set({ list: get().list.filter((s) => s.uid !== uid) }); save(); },
    toggle: (uid) => { set({ list: get().list.map((s) => (s.uid === uid ? { ...s, hidden: !s.hidden } : s)) }); save(); },
    setInputs: (uid, inputs) => { set({ list: get().list.map((s) => (s.uid === uid ? { ...s, inputs } : s)) }); save(); },
    toggleFavourite: (key) => {
      const f = get().favourites;
      const next = f.includes(key) ? f.filter((k) => k !== key) : [...f, key];
      set({ favourites: next }); write(FAV_KEY, next);
    },
    clear: () => { set({ list: get().list.filter((s) => s.key === VOLUME) }); save(); },
    duplicate: (uid) => {
      const list = get().list;
      const i = list.findIndex((s) => s.uid === uid);
      if (i < 0 || list[i]!.key === VOLUME) return;
      const copy = { ...list[i]!, inputs: { ...list[i]!.inputs }, uid: `${list[i]!.key}-${Date.now().toString(36)}` };
      set({ list: [...list.slice(0, i + 1), copy, ...list.slice(i + 1)] }); save();
    },
    move: (uid, dir) => {
      const list = [...get().list];
      const i = list.findIndex((s) => s.uid === uid);
      // A study in its own pane trades places with the next study that has a pane of its own.
      const ownPane = (k: number) => list[k]!.key !== VOLUME && REGISTRY[list[k]!.key]?.pane !== 'main';
      let j = i + dir;
      if (i >= 0 && ownPane(i)) while (j >= 0 && j < list.length && !ownPane(j)) j += dir;
      if (i < 0 || j < 0 || j >= list.length) return;
      [list[i], list[j]] = [list[j]!, list[i]!];
      set({ list }); save();
    },
  };
});
