import { create } from 'zustand';

export type PanelId = 'watchlist' | 'alerts' | 'objects' | 'data' | 'news' | 'calendar' | 'screener';
export type BottomTab = 'screener' | 'namascript' | 'backtest' | 'paper';

const KEY = 'pc.panels.v1';
function read(): { open: PanelId | null; bottom: BottomTab | null } {
  try { return JSON.parse(localStorage.getItem(KEY) ?? '') as { open: PanelId | null; bottom: BottomTab | null }; }
  catch { return { open: 'watchlist', bottom: null }; }
}

interface PanelsState {
  open: PanelId | null;
  bottom: BottomTab | null;
  toggle(id: PanelId): void;
  setBottom(tab: BottomTab | null): void;
}

const initial = read();

export const usePanels = create<PanelsState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify({ open: get().open, bottom: get().bottom })); } catch { /* ignore */ } };
  return {
    open: initial.open === undefined ? 'watchlist' : initial.open,
    bottom: initial.bottom ?? null,
    toggle: (id) => { set((s) => ({ open: s.open === id ? null : id })); save(); },
    setBottom: (tab) => { set((s) => ({ bottom: s.bottom === tab ? null : tab })); save(); },
  };
});
