// How far a newly placed drawing travels (OLD core/datastore DrawingSyncStore.kt, feature/chart
// ChartStudioScreen.kt DrawingSyncRow): «موقت» stays on this chart and does not travel with a saved
// layout; «همین چیدمان» belongs to this layout and is saved with it; «همه‌ی چیدمان‌ها» is seen on
// every layout of the symbol, including ones made later. Three and not a switch, because «keep my
// panes in step» and «keep my levels everywhere» are different intentions. The setting is stamped
// onto what is drawn *next*: silently widening a year of marks is the one move that cannot be undone.
// A drawing with no stamp (everything drawn before this) behaves as it always did: with the layout.
import { create } from 'zustand';
import type { DrawingObject } from '../engine/drawings.js';

export type DrawingScope = 'none' | 'layout' | 'global';
export const SCOPES: DrawingScope[] = ['none', 'layout', 'global'];

const KEY = 'pc.drawings.scope.v1';
const GLOBAL = (symbol: string) => `pc.drawings.global.v1.${symbol}`;

/** A stored id read back; anything unrecognised is «this layout», the mode that changes nothing. */
export const scopeFromId = (id: unknown): DrawingScope => (id === 'none' || id === 'global' || id === 'layout' ? id : 'layout');
export const scopeOf = (d: DrawingObject): DrawingScope => scopeFromId((d as { scope?: unknown }).scope);

export const useDrawingScope = create<{ mode: DrawingScope; set(mode: DrawingScope): void }>((set) => ({
  mode: (() => { try { return scopeFromId(localStorage.getItem(KEY)); } catch { return 'layout' as const; } })(),
  set: (mode) => { set({ mode }); try { localStorage.setItem(KEY, mode); } catch { /* blocked */ } },
}));

/** Stamps a drawing just placed with the current reach. */
export function stampScope(d: DrawingObject): void {
  (d as { scope?: DrawingScope }).scope = useDrawingScope.getState().mode;
}

/** What a saved layout keeps of a symbol's drawings: not the scratch ones, not the everywhere ones. */
export const forLayout = (list: DrawingObject[]): DrawingObject[] => list.filter((d) => scopeOf(d) === 'layout');

/** A layout's drawings for a symbol, with that symbol's everywhere-drawings joined in (no doubles). */
export function withGlobal(layoutList: DrawingObject[], global: DrawingObject[]): DrawingObject[] {
  const ids = new Set<unknown>(global.map((d) => (d as { id?: unknown }).id).filter((x) => x != null));
  return [...layoutList.filter((d) => scopeOf(d) === 'layout' && !ids.has((d as { id?: unknown }).id)), ...global];
}

export function readGlobal(symbol: string): DrawingObject[] {
  try { const v = JSON.parse(localStorage.getItem(GLOBAL(symbol)) ?? '[]') as unknown; return Array.isArray(v) ? (v as DrawingObject[]) : []; } catch { return []; }
}

/** Keeps the symbol's everywhere-drawings in their own store, as the chart's list now has them. */
export function noteGlobal(symbol: string, list: DrawingObject[]): void {
  const g = list.filter((d) => scopeOf(d) === 'global');
  try {
    if (g.length) localStorage.setItem(GLOBAL(symbol), JSON.stringify(g));
    else localStorage.removeItem(GLOBAL(symbol));
  } catch { /* full or blocked */ }
}
