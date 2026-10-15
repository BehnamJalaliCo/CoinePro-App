// Symbols compared on the chart (TradingView's "Compare or add symbol"): drawn as lines, with the
// price scale switched to percent so every series starts from the same zero.
import { create } from 'zustand';

interface CompareState { symbols: string[]; add(s: string): void; remove(s: string): void; clear(): void }

export const COMPARE_COLORS = ['#FF9800', '#2962FF', '#E91E63', '#00BCD4', '#9C27B0', '#4CAF50'];

export const useCompare = create<CompareState>((set, get) => ({
  symbols: [],
  add: (s) => { if (!get().symbols.includes(s) && get().symbols.length < COMPARE_COLORS.length) set({ symbols: [...get().symbols, s] }); },
  remove: (s) => set({ symbols: get().symbols.filter((x) => x !== s) }),
  clear: () => set({ symbols: [] }),
}));
