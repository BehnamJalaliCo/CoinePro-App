import { create } from 'zustand';

export type PanelId =
  | 'watchlist' | 'alerts' | 'signals' | 'ai' | 'objects' | 'data' | 'news' | 'calendar' | 'screener'
  | 'hotlists' | 'dom' | 'notes' | 'log' | 'order';
export type BottomTab = 'screener' | 'namascript' | 'backtest' | 'paper' | 'notes';

const KEY = 'pc.panels.v1';
interface Stored { open: PanelId | null; bottom: BottomTab | null; split?: number; detailsCollapsed?: boolean }
function read(): Stored {
  try { return JSON.parse(localStorage.getItem(KEY) ?? '') as Stored; }
  catch { return { open: 'watchlist', bottom: null }; }
}

interface PanelsState {
  open: PanelId | null;
  bottom: BottomTab | null;
  /** The watchlist's share of the panel's height, above the symbol's details (TradingView's draggable line). */
  split: number;
  /** The details folded to their header, the watchlist taking the panel. */
  detailsCollapsed: boolean;
  toggle(id: PanelId): void;
  /** Opens a panel, or keeps it open (unlike toggle). */
  show(id: PanelId): void;
  setBottom(tab: BottomTab | null): void;
  setSplit(share: number): void;
  setDetailsCollapsed(on: boolean): void;
}

const initial = read();
// A phone opens on the chart, not on a panel covering it.
if (typeof window !== 'undefined' && window.innerWidth <= 760) initial.open = null;

const clampSplit = (v: number) => Math.min(0.85, Math.max(0.15, Number.isFinite(v) ? v : 0.58));

export const usePanels = create<PanelsState>((set, get) => {
  const save = () => {
    const { open, bottom, split, detailsCollapsed } = get();
    try { localStorage.setItem(KEY, JSON.stringify({ open, bottom, split, detailsCollapsed })); } catch { /* ignore */ }
  };
  return {
    open: initial.open === undefined ? 'watchlist' : initial.open,
    bottom: initial.bottom ?? null,
    split: clampSplit(initial.split ?? 0.58),
    detailsCollapsed: Boolean(initial.detailsCollapsed),
    toggle: (id) => { set((s) => ({ open: s.open === id ? null : id })); save(); },
    show: (id) => { set({ open: id }); save(); },
    setBottom: (tab) => { set((s) => ({ bottom: s.bottom === tab ? null : tab })); save(); },
    setSplit: (share) => { set({ split: clampSplit(share) }); save(); },
    setDetailsCollapsed: (on) => { set({ detailsCollapsed: on }); save(); },
  };
});
