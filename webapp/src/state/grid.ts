// Multi-chart layouts, TradingView's "Select layout": every arrangement of one to eight charts it
// offers up to eight (and the old Pro-Chart's ten), the sync switches under them, and the active
// chart the top bar drives. Cell 0 is the main chart (symbol, interval and type in prefs); the
// others keep their own symbol, interval and chart type here.
import { create } from 'zustand';
import type { ChartType } from '../chart/chartTypes';
import { usePrefs } from './prefs';

export interface Preset { id: string; cells: number; areas: string[] }

// Each arrangement as CSS grid areas; the letters a…h are the cells in order (a is the main chart).
export const PRESETS: Preset[] = [
  { id: '1', cells: 1, areas: ['a'] },
  { id: '2v', cells: 2, areas: ['a b'] },
  { id: '2h', cells: 2, areas: ['a', 'b'] },
  { id: '3c', cells: 3, areas: ['a b c'] },
  { id: '3r', cells: 3, areas: ['a', 'b', 'c'] },
  { id: '3', cells: 3, areas: ['a b', 'a c'] },
  { id: '3l', cells: 3, areas: ['a c', 'b c'] },
  { id: '3t', cells: 3, areas: ['a a', 'b c'] },
  { id: '3b', cells: 3, areas: ['a b', 'c c'] },
  { id: '4', cells: 4, areas: ['a b', 'c d'] },
  { id: '4r', cells: 4, areas: ['a', 'b', 'c', 'd'] },
  { id: '4c', cells: 4, areas: ['a b c d'] },
  { id: '4l', cells: 4, areas: ['a b', 'a c', 'a d'] },
  { id: '4rl', cells: 4, areas: ['a d', 'b d', 'c d'] },
  { id: '4t', cells: 4, areas: ['a a a', 'b c d'] },
  { id: '4b', cells: 4, areas: ['a b c', 'd d d'] },
  { id: '5t', cells: 5, areas: ['a a a a', 'b c d e'] },
  { id: '5c', cells: 5, areas: ['a b c d e'] },
  { id: '5r', cells: 5, areas: ['a', 'b', 'c', 'd', 'e'] },
  { id: '5l', cells: 5, areas: ['a b', 'a c', 'a d', 'a e'] },
  { id: '5g', cells: 5, areas: ['a a a b b b', 'c c d d e e'] },
  { id: '6', cells: 6, areas: ['a b c', 'd e f'] },
  { id: '6g', cells: 6, areas: ['a b', 'c d', 'e f'] },
  { id: '6c', cells: 6, areas: ['a b c d e f'] },
  { id: '6t', cells: 6, areas: ['a a a a a', 'b c d e f'] },
  { id: '7', cells: 7, areas: ['a a a a b b b b c c c c', 'd d d e e e f f f g g g'] },
  { id: '7l', cells: 7, areas: ['a b c', 'a d e', 'a f g'] },
  { id: '7c', cells: 7, areas: ['a b c d e f g'] },
  { id: '8', cells: 8, areas: ['a b c d', 'e f g h'] },
  { id: '8g', cells: 8, areas: ['a b', 'c d', 'e f', 'g h'] },
  { id: '8c', cells: 8, areas: ['a b c d e f g h'] },
];

export type GridId = string;
export const CELL_AREAS = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
export const MAX_CELLS = 8;

export const presetOf = (id: string): Preset => PRESETS.find((p) => p.id === id) ?? PRESETS[0]!;
export const cellCount = (g: GridId) => presetOf(g).cells;

/** The CSS grid template for a preset: areas, and equal rows and columns. */
export function gridTemplate(id: string): { gridTemplateAreas: string; gridTemplateRows: string; gridTemplateColumns: string } {
  const p = presetOf(id);
  const cols = p.areas[0]!.split(' ').length;
  return {
    gridTemplateAreas: p.areas.map((r) => `'${r}'`).join(' '),
    gridTemplateRows: `repeat(${p.areas.length}, minmax(0, 1fr))`,
    gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`,
  };
}

export interface Cell { symbol: string; interval: string; chartType?: ChartType }
export interface Sync { symbol: boolean; interval: boolean; crosshair: boolean; time: boolean; dateRange: boolean }
export interface Mirror { studies: boolean; hlines: boolean }

const KEY = 'pc.grid.v1';
interface Stored { grid: GridId; cells: Cell[]; sync: Sync; mirror: Mirror }
const DEFAULT_CELLS: Cell[] = [
  { symbol: 'ETHUSDT', interval: '60' }, { symbol: 'XAUUSD', interval: '60' }, { symbol: 'EURUSD', interval: '60' },
  { symbol: 'SOLUSDT', interval: '60' }, { symbol: 'GBPUSD', interval: '60' }, { symbol: 'BNBUSDT', interval: '60' }, { symbol: 'XAGUSD', interval: '60' },
];
// TradingView's defaults: the crosshair follows across charts; nothing else is linked.
export const DEFAULT_SYNC: Sync = { symbol: false, interval: false, crosshair: true, time: false, dateRange: false };
export const DEFAULT_MIRROR: Mirror = { studies: false, hlines: true };

const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v && v.grid) {
      const cells = Array.isArray(v.cells) ? v.cells.filter((c) => c && typeof c.symbol === 'string' && typeof c.interval === 'string') : [];
      return {
        grid: PRESETS.some((p) => p.id === v.grid) ? v.grid : '1',
        cells: [...cells, ...DEFAULT_CELLS.slice(cells.length)].slice(0, MAX_CELLS - 1),
        sync: { ...DEFAULT_SYNC, ...(v.sync ?? {}) },
        mirror: { ...DEFAULT_MIRROR, ...(v.mirror ?? {}) },
      };
    }
  } catch { /* fall through */ }
  return { grid: '1', cells: DEFAULT_CELLS, sync: DEFAULT_SYNC, mirror: DEFAULT_MIRROR };
};

/** A crosshair or a visible time range shared between the charts of the layout. */
export interface SharedHover { t: number; src: number }
export interface SharedRange { from: number; to: number; src: number }
/** A time-bar date range for some charts of the layout (1…7: the extra charts). */
export interface RangeRequest { seq: number; span: number | null; interval: string; targets: number[] }

interface GridState extends Stored {
  /** The chart the top bar drives: 0 the main chart, n the n-th extra cell. */
  active: number;
  /** Alt+Enter: the active chart alone, the others kept alive behind it. */
  maximized: boolean;
  hover: SharedHover | null;
  range: SharedRange | null;
  rangeReq: RangeRequest | null;
  requestRange(span: number | null, interval: string, targets: number[]): void;
  setGrid(g: GridId): void;
  setCell(i: number, c: Partial<Cell>): void;
  setCells(cells: Cell[]): void;
  setActive(i: number): void;
  setMaximized(on: boolean): void;
  setSync(p: Partial<Sync>): void;
  setMirror(p: Partial<Mirror>): void;
  setHover(h: SharedHover | null): void;
  setRange(r: SharedRange | null): void;
}

export const useGrid = create<GridState>((set, get) => {
  const save = () => { const { grid, cells, sync, mirror } = get(); try { localStorage.setItem(KEY, JSON.stringify({ grid, cells, sync, mirror })); } catch { /* ignore */ } };
  return {
    ...read(),
    active: 0,
    maximized: false,
    hover: null,
    range: null,
    rangeReq: null,
    requestRange: (span, interval, targets) => set({ rangeReq: { seq: (get().rangeReq?.seq ?? 0) + 1, span, interval, targets } }),
    setGrid: (grid) => {
      const n = cellCount(grid);
      set({ grid, active: get().active < n ? get().active : 0, maximized: n > 1 && get().maximized });
      save();
    },
    setCell: (i, c) => { set({ cells: get().cells.map((x, k) => (k === i ? { ...x, ...c } : x)) }); save(); },
    setCells: (cells) => { set({ cells: [...cells, ...DEFAULT_CELLS.slice(cells.length)].slice(0, MAX_CELLS - 1) }); save(); },
    setActive: (active) => set({ active: active < cellCount(get().grid) ? active : 0 }),
    setMaximized: (maximized) => set({ maximized: maximized && cellCount(get().grid) > 1 }),
    setSync: (p) => { set({ sync: { ...get().sync, ...p } }); save(); },
    setMirror: (p) => { set({ mirror: { ...get().mirror, ...p } }); save(); },
    setHover: (hover) => set({ hover }),
    setRange: (range) => set({ range }),
  };
});

// ── The top bar drives the active chart ─────────────────────────────────────────────────────────

/** The symbol, interval and chart type of the active chart. */
export function activeChart(): { symbol: string; interval: string; chartType: ChartType } {
  const g = useGrid.getState();
  const p = usePrefs.getState();
  if (g.active > 0) {
    const c = g.cells[g.active - 1];
    if (c) return { symbol: c.symbol, interval: c.interval, chartType: c.chartType ?? p.chartType };
  }
  return { symbol: p.symbol, interval: p.interval, chartType: p.chartType };
}

const visibleCells = () => cellCount(useGrid.getState().grid) - 1;

/** A symbol for the active chart; with symbol sync on, for every chart of the layout. */
export function applySymbol(symbol: string): void {
  const g = useGrid.getState();
  const p = usePrefs.getState();
  if (g.sync.symbol && cellCount(g.grid) > 1) {
    p.setSymbol(symbol);
    for (let i = 0; i < visibleCells(); i++) g.setCell(i, { symbol });
    return;
  }
  if (g.active > 0) g.setCell(g.active - 1, { symbol });
  else p.setSymbol(symbol);
}

/** An interval for the active chart; with interval sync on, for every chart of the layout. */
export function applyInterval(interval: string): void {
  const g = useGrid.getState();
  const p = usePrefs.getState();
  if (g.sync.interval && cellCount(g.grid) > 1) {
    p.setInterval(interval);
    for (let i = 0; i < visibleCells(); i++) g.setCell(i, { interval });
    return;
  }
  if (g.active > 0) g.setCell(g.active - 1, { interval });
  else p.setInterval(interval);
}

/** A chart type for the active chart only (each chart keeps its own). */
export function applyChartType(chartType: ChartType): void {
  const g = useGrid.getState();
  if (g.active > 0) g.setCell(g.active - 1, { chartType });
  else usePrefs.getState().setChartType(chartType);
}
