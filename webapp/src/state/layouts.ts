// Saved chart layouts and indicator templates, as TradingView's layout menu and "Indicator
// templates": a layout keeps the symbol, interval and chart type, the indicators, the compared
// symbols, the multi-chart grid with each chart's symbol, interval and type and its sync switches,
// the drawings of every symbol on screen, the chart settings and the theme. Save (Ctrl+S), save as,
// rename, make a copy, new, open, recently used, autosave. Kept in this browser.
import { create } from 'zustand';
import { usePrefs, type Theme } from './prefs';
import { useStudies, type Study } from './studies';
import { useCompare } from './compare';
import { useGrid, cellCount, DEFAULT_MIRROR, DEFAULT_SYNC, type Cell, type Mirror, type Sync } from './grid';
import { useChartSettings, type ChartSettings } from './chartSettings';
import { useChartBus } from '../chart/chartBus';
import type { ChartType } from '../chart/chartTypes';
import type { DrawingObject } from '../engine/drawings.js';

export interface Layout {
  name: string;
  symbol: string;
  interval: string;
  chartType: ChartType;
  studies: Study[];
  compare: string[];
  savedAt: number;
  /** When it was last opened or saved, for "Recently used". */
  openedAt?: number;
  grid?: string;
  cells?: Cell[];
  sync?: Sync;
  mirror?: Mirror;
  /** The drawings of every symbol the layout shows, by symbol. */
  drawings?: Record<string, DrawingObject[]>;
  settings?: Partial<ChartSettings>;
  theme?: Theme;
}
export interface Template { name: string; studies: Study[] }

const KEY = 'pc.layouts.v1';
const DRAWINGS = (symbol: string) => `pc.drawings.v1.${symbol}`;
interface Stored { layouts: Layout[]; templates: Template[]; current: string | null; autosave: boolean }
const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v && Array.isArray(v.layouts)) return { layouts: v.layouts, templates: v.templates ?? [], current: v.current ?? null, autosave: v.autosave ?? false };
  } catch { /* fall through */ }
  return { layouts: [], templates: [], current: null, autosave: false };
};

interface LayoutState extends Stored {
  /** Changes since the layout was last saved. */
  dirty: boolean;
  /** Saves under `name`, or the open layout's name; false when there is none yet (ask for one). */
  save(name?: string): boolean;
  load(name: string): void;
  remove(name: string): void;
  rename(from: string, to: string): void;
  /** A copy of the open layout under a new name, which becomes the open one. */
  copy(name: string): void;
  /** A fresh, unnamed layout: one chart, volume, no comparisons; the symbol stays. */
  newLayout(): void;
  setAutosave(on: boolean): void;
  markDirty(): void;
  saveTemplate(name: string): void;
  applyTemplate(name: string): void;
  removeTemplate(name: string): void;
}

const fresh = (list: Study[]) => list.map((s) => ({ ...s, uid: `${s.key}-${Math.random().toString(36).slice(2, 8)}` }));

function readDrawings(symbol: string): DrawingObject[] {
  try { const v = JSON.parse(localStorage.getItem(DRAWINGS(symbol)) ?? '[]') as unknown; return Array.isArray(v) ? (v as DrawingObject[]) : []; } catch { return []; }
}

/** Everything a layout keeps, read from the screen now. */
export function captureLayout(): Omit<Layout, 'name' | 'savedAt' | 'openedAt'> {
  const p = usePrefs.getState();
  const g = useGrid.getState();
  const cells = g.cells.slice(0, cellCount(g.grid) - 1);
  const symbols = [...new Set([p.symbol, ...cells.map((c) => c.symbol)])];
  const layer = useChartBus.getState().drawingLayer;
  const drawings: Record<string, DrawingObject[]> = {};
  for (const s of symbols) {
    const list = s === p.symbol && layer ? layer.getDrawings() : readDrawings(s);
    if (list.length) drawings[s] = JSON.parse(JSON.stringify(list)) as DrawingObject[];
  }
  const { set: _set, reset: _reset, ...settings } = useChartSettings.getState();
  void _set; void _reset;
  return {
    symbol: p.symbol, interval: p.interval, chartType: p.chartType, theme: p.theme,
    studies: useStudies.getState().list, compare: useCompare.getState().symbols,
    grid: g.grid, cells: g.cells, sync: g.sync, mirror: g.mirror, drawings, settings,
  };
}

/** Puts a layout on the screen. */
export function applyLayout(l: Layout): void {
  applying = true;
  try { applyLayoutNow(l); } finally { applying = false; }
}

function applyLayoutNow(l: Layout): void {
  const p = usePrefs.getState();
  // Drawings first, so the chart that loads a symbol finds its own.
  if (l.drawings) {
    for (const [sym, list] of Object.entries(l.drawings)) {
      try { if (list.length) localStorage.setItem(DRAWINGS(sym), JSON.stringify(list)); else localStorage.removeItem(DRAWINGS(sym)); } catch { /* storage full */ }
    }
  }
  const sameSymbol = p.symbol === l.symbol;
  p.setSymbol(l.symbol); p.setInterval(l.interval); p.setChartType(l.chartType);
  if (l.theme) p.setTheme(l.theme);
  useStudies.setState({ list: fresh(l.studies) });
  try { localStorage.setItem('pc.studies.v1', JSON.stringify(useStudies.getState().list)); } catch { /* ignore */ }
  useCompare.setState({ symbols: l.compare ?? [] });
  const g = useGrid.getState();
  if (l.cells) g.setCells(l.cells);
  g.setSync(l.sync ?? DEFAULT_SYNC);
  g.setMirror(l.mirror ?? DEFAULT_MIRROR);
  g.setGrid(l.grid ?? '1');
  g.setActive(0);
  if (l.settings) useChartSettings.getState().set(l.settings);
  // The main chart keeps its symbol: its drawings change in place.
  const layer = useChartBus.getState().drawingLayer;
  if (sameSymbol && layer && l.drawings) {
    layer.setDrawings(JSON.parse(JSON.stringify(l.drawings[l.symbol] ?? [])) as DrawingObject[]);
    useChartBus.getState().bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length);
  }
}

export const useLayouts = create<LayoutState>((set, get) => {
  const persist = () => { const { layouts, templates, current, autosave } = get(); try { localStorage.setItem(KEY, JSON.stringify({ layouts, templates, current, autosave })); } catch { /* ignore */ } };
  const put = (l: Layout) => set({ layouts: [l, ...get().layouts.filter((x) => x.name !== l.name)] });
  return {
    ...read(),
    dirty: false,
    save: (name) => {
      const n = (name ?? get().current ?? '').trim();
      if (!n) {
        // No name yet (Ctrl+S on a new layout): the top bar asks for one, as TradingView does.
        if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('pc:layout-dialog', { detail: { mode: 'save' } }));
        return false;
      }
      const now = Date.now();
      put({ ...captureLayout(), name: n, savedAt: now, openedAt: now });
      set({ current: n, dirty: false }); persist();
      return true;
    },
    load: (name) => {
      const l = get().layouts.find((x) => x.name === name);
      if (!l) return;
      suspendAutosave();
      applyLayout(l);
      set({ current: name, dirty: false, layouts: get().layouts.map((x) => (x.name === name ? { ...x, openedAt: Date.now() } : x)) }); persist();
    },
    remove: (name) => { set({ layouts: get().layouts.filter((x) => x.name !== name), current: get().current === name ? null : get().current }); persist(); },
    rename: (from, to) => {
      const n = to.trim();
      if (!n || n === from) return;
      const l = get().layouts.find((x) => x.name === from);
      if (!l) return;
      set({ layouts: [{ ...l, name: n }, ...get().layouts.filter((x) => x.name !== from && x.name !== n)], current: get().current === from ? n : get().current }); persist();
    },
    copy: (name) => {
      const n = name.trim();
      if (!n) return;
      const now = Date.now();
      put({ ...captureLayout(), name: n, savedAt: now, openedAt: now });
      set({ current: n, dirty: false }); persist();
    },
    newLayout: () => {
      suspendAutosave();
      const p = usePrefs.getState();
      applyLayout({ name: '', symbol: p.symbol, interval: p.interval, chartType: 'candles', studies: [{ uid: 'vol', key: '__volume', inputs: {} }], compare: [], savedAt: 0, grid: '1', sync: DEFAULT_SYNC, mirror: DEFAULT_MIRROR });
      set({ current: null, dirty: false }); persist();
    },
    setAutosave: (autosave) => { set({ autosave }); persist(); if (autosave && get().current && get().dirty) get().save(); },
    markDirty: () => { if (!get().dirty) set({ dirty: true }); },
    saveTemplate: (name) => {
      const n = name.trim();
      if (!n) return;
      set({ templates: [{ name: n, studies: useStudies.getState().list }, ...get().templates.filter((x) => x.name !== n)] }); persist();
    },
    applyTemplate: (name) => {
      const tpl = get().templates.find((x) => x.name === name);
      if (!tpl) return;
      useStudies.setState({ list: fresh(tpl.studies) });
      try { localStorage.setItem('pc.studies.v1', JSON.stringify(useStudies.getState().list)); } catch { /* ignore */ }
    },
    removeTemplate: (name) => { set({ templates: get().templates.filter((x) => x.name !== name) }); persist(); },
  };
});

/** "Recently used": the saved layouts by when they were last opened, the open one left out. */
export function recentLayouts(limit = 5): Layout[] {
  const { layouts, current } = useLayouts.getState();
  return layouts.filter((l) => l.name !== current).sort((a, b) => (b.openedAt ?? b.savedAt) - (a.openedAt ?? a.savedAt)).slice(0, limit);
}

// ── Autosave: what the layout holds changed → saved a moment later (or marked unsaved) ───────────
// Putting a layout on screen is not a change; nor is the chart re-reading the drawings it brought
// (a moment later, once the bars are in).
let applying = false;
let drawingsQuietUntil = 0;
const suspendAutosave = () => { drawingsQuietUntil = Date.now() + 2500; };
let started = false;
let timer: ReturnType<typeof setTimeout> | null = null;

export function startLayoutWatch(): () => void {
  if (started) return () => undefined;
  started = true;
  const changed = () => {
    if (applying) return;
    const st = useLayouts.getState();
    st.markDirty();
    if (!st.autosave || !st.current) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => { timer = null; const s = useLayouts.getState(); if (s.autosave && s.current) s.save(); }, 1500);
  };
  const offs = [
    usePrefs.subscribe((s, p) => { if (s.symbol !== p.symbol || s.interval !== p.interval || s.chartType !== p.chartType || s.theme !== p.theme) changed(); }),
    useStudies.subscribe((s, p) => { if (s.list !== p.list) changed(); }),
    useCompare.subscribe((s, p) => { if (s.symbols !== p.symbols) changed(); }),
    useGrid.subscribe((s, p) => { if (s.grid !== p.grid || s.cells !== p.cells || s.sync !== p.sync || s.mirror !== p.mirror) changed(); }),
    useChartSettings.subscribe(() => changed()),
    useChartBus.subscribe((s, p) => { if (s.drawingVersion !== p.drawingVersion && Date.now() > drawingsQuietUntil) changed(); }),
  ];
  return () => { started = false; offs.forEach((f) => f()); if (timer) clearTimeout(timer); };
}
