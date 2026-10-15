// Which drawing tool is armed and the global drawing switches. The chart's DrawingLayer reads this;
// the rail writes it. Kept apart from the layer so the rail never needs the chart to exist.
// The reader's choices (cursor, magnet, stay in drawing mode, lock, hide, the rail, favourites, the
// default colour, per-tool defaults and templates) are kept in this browser.
import { create } from 'zustand';
import type { DrawingObject } from '../engine/drawings.js';

/** TradingView's cursors: Cross, Dot, Arrow, Demonstration and Eraser. */
export type CursorMode = 'cursor' | 'dot' | 'arrowcursor' | 'demonstration' | 'eraser';
export const CURSOR_MODES: CursorMode[] = ['cursor', 'dot', 'arrowcursor', 'demonstration', 'eraser'];
export const isCursorMode = (id: string): id is CursorMode => (CURSOR_MODES as string[]).includes(id);
export type MagnetMode = 'weak' | 'strong';
export interface StyleTemplate { name: string; style: Record<string, unknown> }
export interface Point { x: number; y: number }

interface Stored {
  cursor: CursorMode;
  lastInGroup: Record<string, string>;
  magnet: boolean;
  magnetMode: MagnetMode;
  /** Stay in drawing mode: the tool stays armed after a drawing is placed. */
  stay: boolean;
  locked: boolean;
  hidden: boolean;
  /** The whole rail hidden (desktop); the chart menu and the top bar's menus toggle it. */
  railHidden: boolean;
  favorites: string[];
  /** TradingView's floating favourites toolbar, and where it was dragged to. */
  favBar: boolean;
  favPos: Point | null;
  /** The colour new drawings take. */
  color: string;
  /** Per tool: the style saved with «Save as default». */
  defaults: Record<string, Record<string, unknown>>;
  /** Per tool: named style templates. */
  templates: Record<string, StyleTemplate[]>;
  /** Remove also locked drawings (TradingView's "Always remove locked drawings"). */
  removeLocked: boolean;
  /** Where the floating style bar was dragged to (relative to the chart), or null to follow the selection. */
  barPos: Point | null;
  /** The glyph the Icons tool places. */
  emoji: string;
  /** The studies the "hide indicators" switch hid, to show again. */
  hiddenStudies: string[];
}

const KEY = 'pc.drawing.v1';
const DEFAULTS: Stored = {
  cursor: 'cursor', lastInGroup: {}, magnet: false, magnetMode: 'strong', stay: false, locked: false, hidden: false,
  railHidden: false, favorites: [], favBar: false, favPos: null, color: '#2962FF', defaults: {}, templates: {},
  removeLocked: false, barPos: null, emoji: '🙂', hiddenStudies: [],
};

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Stored>;
    const out = { ...DEFAULTS, ...(v && typeof v === 'object' ? v : {}) };
    if (!isCursorMode(out.cursor)) out.cursor = 'cursor';
    if (out.magnetMode !== 'weak') out.magnetMode = 'strong';
    if (!Array.isArray(out.favorites)) out.favorites = [];
    if (!Array.isArray(out.hiddenStudies)) out.hiddenStudies = [];
    if (typeof out.color !== 'string' || !/^#[0-9a-f]{6}$/i.test(out.color)) out.color = DEFAULTS.color;
    return out;
  } catch { return { ...DEFAULTS }; }
}

interface DrawingState extends Stored {
  /** The armed tool: a drawing tool, 'select', 'zoom', or one of the cursor modes. */
  tool: string;
  /** Bumped to ask the chart to remove every drawing on the current symbol. */
  clearSeq: number;
  /** Drawings copied with Ctrl+C (or the menus), for Ctrl+V on any symbol. */
  clip: DrawingObject[] | null;
  /** How many zoom-ins the Zoom-out button can step back. */
  zoomDepth: number;
  /** The drawing whose settings dialog is open, and on which tab. */
  settings: { index: number; tab?: string } | null;
  setTool(id: string, group?: string): void;
  /** Back to the cursor the reader chose (after a drawing, on Esc). */
  resetTool(): void;
  setMagnet(on: boolean): void;
  setMagnetMode(mode: MagnetMode): void;
  setStay(on: boolean): void;
  setLocked(on: boolean): void;
  setHidden(on: boolean): void;
  setRailHidden(on: boolean): void;
  toggleFavorite(id: string): void;
  setFavBar(on: boolean): void;
  setFavPos(p: Point | null): void;
  setColor(color: string): void;
  setDefault(type: string, style: Record<string, unknown> | null): void;
  saveTemplate(type: string, name: string, style: Record<string, unknown>): void;
  deleteTemplate(type: string, name: string): void;
  setRemoveLocked(on: boolean): void;
  setBarPos(p: Point | null): void;
  setEmoji(e: string): void;
  setHiddenStudies(list: string[]): void;
  setClip(list: DrawingObject[] | null): void;
  setZoomDepth(n: number): void;
  openSettings(index: number, tab?: string): void;
  closeSettings(): void;
  clearAll(): void;
}

const initial = read();

export const useDrawing = create<DrawingState>((set, get) => ({
  ...initial,
  tool: initial.cursor,
  clearSeq: 0,
  clip: null,
  zoomDepth: 0,
  settings: null,
  setTool: (id, group) => set((s) => {
    const g = group ?? (isCursorMode(id) || id === 'select' ? 'cursors' : undefined);
    return {
      tool: id,
      ...(isCursorMode(id) ? { cursor: id } : {}),
      lastInGroup: g ? { ...s.lastInGroup, [g]: id } : s.lastInGroup,
    };
  }),
  resetTool: () => set({ tool: get().cursor }),
  setMagnet: (magnet) => set({ magnet }),
  setMagnetMode: (magnetMode) => set({ magnetMode, magnet: true }),
  setStay: (stay) => set({ stay }),
  setLocked: (locked) => set({ locked }),
  setHidden: (hidden) => set({ hidden }),
  setRailHidden: (railHidden) => set({ railHidden }),
  toggleFavorite: (id) => set((s) => ({ favorites: s.favorites.includes(id) ? s.favorites.filter((x) => x !== id) : [...s.favorites, id] })),
  setFavBar: (favBar) => set({ favBar }),
  setFavPos: (favPos) => set({ favPos }),
  setColor: (color) => set({ color }),
  setDefault: (type, style) => set((s) => {
    const next = { ...s.defaults };
    if (style) next[type] = style; else delete next[type];
    return { defaults: next };
  }),
  saveTemplate: (type, name, style) => set((s) => {
    const list = (s.templates[type] ?? []).filter((x) => x.name !== name);
    return { templates: { ...s.templates, [type]: [...list, { name, style }] } };
  }),
  deleteTemplate: (type, name) => set((s) => ({ templates: { ...s.templates, [type]: (s.templates[type] ?? []).filter((x) => x.name !== name) } })),
  setRemoveLocked: (removeLocked) => set({ removeLocked }),
  setBarPos: (barPos) => set({ barPos }),
  setEmoji: (emoji) => set({ emoji }),
  setHiddenStudies: (hiddenStudies) => set({ hiddenStudies }),
  setClip: (clip) => set({ clip }),
  setZoomDepth: (zoomDepth) => set({ zoomDepth }),
  openSettings: (index, tab) => set({ settings: { index, tab } }),
  closeSettings: () => set({ settings: null }),
  clearAll: () => set((s) => ({ clearSeq: s.clearSeq + 1 })),
}));

// Keep the reader's choices; storage may be blocked, and the app works without it.
const STORED_KEYS = Object.keys(DEFAULTS) as (keyof Stored)[];
let last = '';
useDrawing.subscribe((s) => {
  const out: Partial<Stored> = {};
  for (const k of STORED_KEYS) (out as Record<string, unknown>)[k] = s[k];
  const json = JSON.stringify(out);
  if (json === last) return;
  last = json;
  try { localStorage.setItem(KEY, json); } catch { /* storage blocked: keep going */ }
});
