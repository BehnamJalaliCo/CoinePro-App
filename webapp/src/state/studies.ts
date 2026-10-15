// The indicators on the chart, as TradingView keeps them: one list for the layout (not per symbol),
// each with its inputs, its style (per-plot colour, width, line style, plot type, levels, fill,
// precision, scale), the timeframes it shows on, an optional timeframe of its own, shown or
// hidden. The list order is the pane order. Persisted in this browser, as are the reader's saved
// defaults per indicator and the recently added ones.
import { create } from 'zustand';
import { STUDIES as REGISTRY } from '../engine/indicators_ext_c.js';

export type PlotType = 'line' | 'step' | 'histogram' | 'area' | 'columns' | 'circles' | 'cross';
export type LineStyleName = 'solid' | 'dashed' | 'dotted';
export interface PlotStyle { color?: string; visible?: boolean; width?: number; lineStyle?: LineStyleName; opacity?: number; type?: PlotType }
export interface LevelStyle { value: number; color: string; lineStyle: LineStyleName; visible: boolean; width?: number }
export interface FillStyle { color?: string; opacity?: number; visible?: boolean; /** The second colour of a two-coloured cloud (Ichimoku falling). */ color2?: string }
export interface StudyStyle {
  /** Per output, in the order the renderer flattens the engine's output. */
  plots?: PlotStyle[];
  /** The band fill (Bollinger, Keltner, …) or the cloud (Ichimoku). */
  fill?: FillStyle;
  /** Horizontal levels; absent means the engine's guides as they come. */
  levels?: LevelStyle[];
  /** The shaded background between the outer levels (RSI 30–70 and the like). */
  levelFill?: FillStyle;
  /** Decimal places of the values, or absent for the instrument's own. */
  precision?: number;
  /** Which price scale the study sits on: TradingView's "Pin to scale". */
  scale?: 'right' | 'left' | 'none';
  /** The last value as a label on the price scale. */
  priceLabels?: boolean;
  /** The values in the legend (status line). */
  statusValues?: boolean;
}
export type VisClass = 'seconds' | 'minutes' | 'hours' | 'days' | 'weeks' | 'months';
export type Visibility = Partial<Record<VisClass, { on: boolean; from: number; to: number }>>;
export const VIS_RANGES: Record<VisClass, [number, number]> = { seconds: [1, 59], minutes: [1, 59], hours: [1, 24], days: [1, 366], weeks: [1, 52], months: [1, 12] };

export interface Study {
  uid: string;
  key: string;
  inputs: Record<string, number | string | boolean>;
  hidden?: boolean;
  style?: StudyStyle;
  visibility?: Visibility;
  /** A timeframe of its own (an interval id higher than the chart's), or absent for the chart's. */
  timeframe?: string;
  /** With a timeframe of its own: only closed bars of that timeframe count (no repainting). */
  waitClose?: boolean;
}

/** The volume histogram drawn under the price, as TradingView adds it to every new chart. */
export const VOLUME = '__volume';
/** The NamaScript on the chart, listed with the indicators so it has a legend row and a gear. */
export const SCRIPT = '__script';

const KEY = 'pc.studies.v1';
const FAV_KEY = 'pc.studies.fav.v1';
const DEFAULTS_KEY = 'pc.studies.defaults.v1';
const RECENT_KEY = 'pc.studies.recent.v1';

function read<T>(key: string, fallback: T): T {
  try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; return v ?? fallback; } catch { return fallback; }
}
function write(key: string, v: unknown) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* ignore */ } }

/** What the reader saved with "Save as default" for an indicator: inputs and style. */
export interface SavedDefault { inputs?: Study['inputs']; style?: StudyStyle; visibility?: Visibility }

interface StudiesState {
  list: Study[];
  favourites: string[];
  /** The keys added most recently, newest first (TradingView's "Recently used"). */
  recent: string[];
  defaults: Record<string, SavedDefault>;
  /** Adds an indicator with the reader's saved defaults; returns its uid. */
  add(key: string): string | null;
  remove(uid: string): void;
  toggle(uid: string): void;
  setInputs(uid: string, inputs: Study['inputs']): void;
  /** Replaces any of a study's fields but its uid and key (the settings dialog's live preview). */
  patch(uid: string, p: Partial<Omit<Study, 'uid' | 'key'>>): void;
  /** Puts a study back exactly as it was (Cancel in the settings dialog). */
  restore(s: Study): void;
  /** A copy right after the original, with its own uid. */
  duplicate(uid: string): string | null;
  /** One place up (−1) or down (+1) in the list, which is the pane order. */
  move(uid: string, dir: -1 | 1): void;
  saveDefault(uid: string): void;
  resetDefault(key: string): void;
  toggleFavourite(key: string): void;
  clear(): void;
  /** The volume histogram at the foot of the price pane: put back (or shown) and taken off. */
  setVolume(on: boolean): void;
  /** The NamaScript pseudo-study: added when a script goes on the chart, removed with it. */
  setScript(on: boolean): void;
  /** Asks the chart to draw the studies again (a new script result). */
  touch(uid: string): void;
}

const known = (s: Study) => s.key === VOLUME || s.key === SCRIPT || Boolean(REGISTRY[s.key]);
const initial = read<Study[]>(KEY, [{ uid: 'vol', key: VOLUME, inputs: {} }]).filter(known);
const newUid = (key: string) => `${key}-${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`;
const clone = <T>(v: T): T => (v == null ? v : (JSON.parse(JSON.stringify(v)) as T));

export const useStudies = create<StudiesState>((set, get) => {
  const save = () => write(KEY, get().list);
  const map = (uid: string, f: (s: Study) => Study) => { set({ list: get().list.map((s) => (s.uid === uid ? f(s) : s)) }); save(); };
  return {
    list: initial,
    favourites: read<string[]>(FAV_KEY, []),
    recent: read<string[]>(RECENT_KEY, []),
    defaults: read<Record<string, SavedDefault>>(DEFAULTS_KEY, {}),
    add: (key) => {
      const def = REGISTRY[key];
      if (!def) return null;
      const saved = get().defaults[key];
      const uid = newUid(key);
      const study: Study = { uid, key, inputs: { ...def.inputs, ...(saved?.inputs ?? {}) } };
      if (saved?.style) study.style = clone(saved.style);
      if (saved?.visibility) study.visibility = clone(saved.visibility);
      const recent = [key, ...get().recent.filter((k) => k !== key)].slice(0, 12);
      set({ list: [...get().list, study], recent });
      save(); write(RECENT_KEY, recent);
      return uid;
    },
    remove: (uid) => { set({ list: get().list.filter((s) => s.uid !== uid) }); save(); },
    toggle: (uid) => map(uid, (s) => ({ ...s, hidden: !s.hidden })),
    setInputs: (uid, inputs) => map(uid, (s) => ({ ...s, inputs })),
    patch: (uid, p) => map(uid, (s) => ({ ...s, ...p })),
    restore: (st) => map(st.uid, () => clone(st)),
    duplicate: (uid) => {
      const list = get().list;
      const i = list.findIndex((s) => s.uid === uid);
      if (i < 0 || list[i]!.key === VOLUME || list[i]!.key === SCRIPT) return null;
      const copy = { ...clone(list[i]!), uid: newUid(list[i]!.key) };
      set({ list: [...list.slice(0, i + 1), copy, ...list.slice(i + 1)] });
      save();
      return copy.uid;
    },
    move: (uid, dir) => {
      const list = [...get().list];
      const i = list.findIndex((s) => s.uid === uid);
      // A study in its own pane trades places with the next study that has a pane of its own,
      // stepping over the overlays between them: the pane order is what the reader sees move.
      const ownPane = (k: number) => list[k]!.key !== VOLUME && list[k]!.key !== SCRIPT && REGISTRY[list[k]!.key]?.pane !== 'main';
      let j = i + dir;
      if (i >= 0 && ownPane(i)) while (j >= 0 && j < list.length && !ownPane(j)) j += dir;
      if (i < 0 || j < 0 || j >= list.length) return;
      [list[i], list[j]] = [list[j]!, list[i]!];
      set({ list });
      save();
    },
    saveDefault: (uid) => {
      const s = get().list.find((x) => x.uid === uid);
      if (!s || !REGISTRY[s.key]) return;
      const defaults = { ...get().defaults, [s.key]: { inputs: { ...s.inputs }, style: clone(s.style), visibility: clone(s.visibility) } };
      set({ defaults }); write(DEFAULTS_KEY, defaults);
    },
    resetDefault: (key) => {
      const defaults = { ...get().defaults };
      delete defaults[key];
      set({ defaults }); write(DEFAULTS_KEY, defaults);
    },
    toggleFavourite: (key) => {
      const f = get().favourites;
      const next = f.includes(key) ? f.filter((k) => k !== key) : [...f, key];
      set({ favourites: next }); write(FAV_KEY, next);
    },
    clear: () => { set({ list: get().list.filter((s) => s.key === VOLUME) }); save(); },
    setVolume: (on) => {
      const list = get().list;
      const v = list.find((s) => s.key === VOLUME);
      if (on) set({ list: v ? list.map((s) => (s.key === VOLUME ? { ...s, hidden: false } : s)) : [{ uid: 'vol', key: VOLUME, inputs: {} }, ...list] });
      else set({ list: list.filter((s) => s.key !== VOLUME) });
      save();
    },
    setScript: (on) => {
      const has = get().list.some((s) => s.key === SCRIPT);
      if (on && !has) { set({ list: [...get().list, { uid: 'script', key: SCRIPT, inputs: {} }] }); save(); }
      if (!on && has) { set({ list: get().list.filter((s) => s.key !== SCRIPT) }); save(); }
    },
    touch: (uid) => { set({ list: get().list.map((s) => (s.uid === uid ? { ...s } : s)) }); },
  };
});

/** The interval's class and size for the Visibility tab: '15' → minutes 15, '240' → hours 4. */
export function intervalClass(id: string): { cls: VisClass; n: number } {
  const m = /^(\d+)([SDWM]?)$/.exec(id);
  if (!m) return { cls: 'hours', n: 1 };
  const n = Number(m[1]);
  if (m[2] === 'S') return { cls: 'seconds', n };
  if (m[2] === 'D') return { cls: 'days', n };
  if (m[2] === 'W') return { cls: 'weeks', n };
  if (m[2] === 'M') return { cls: 'months', n };
  return n < 60 ? { cls: 'minutes', n } : { cls: 'hours', n: n / 60 };
}

/** Whether a study shows on this interval, by its Visibility settings. */
export function visibleOn(v: Visibility | undefined, interval: string): boolean {
  if (!v) return true;
  const { cls, n } = intervalClass(interval);
  const c = v[cls];
  if (!c) return true;
  return c.on && n >= c.from && n <= c.to;
}
