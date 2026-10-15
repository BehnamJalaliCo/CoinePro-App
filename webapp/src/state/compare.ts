// Symbols compared on the chart (TradingView's «Compare or add symbol»): a line each, on its own
// hidden scale unless the price axis is in percent (or the settings ask for percent), each with a
// colour the reader may change and an eye to hide it. Kept across reloads.
import { create } from 'zustand';

export const COMPARE_COLORS = ['#FF9800', '#2962FF', '#E91E63', '#00BCD4', '#9C27B0', '#4CAF50'];

interface CompareState {
  symbols: string[];
  hidden: Record<string, boolean>;
  colors: Record<string, string>;
  add(s: string): void;
  remove(s: string): void;
  clear(): void;
  toggle(s: string): void;
  setColor(s: string, color: string): void;
}

const KEY = 'pc.compare.v1';
interface Stored { symbols: string[]; hidden: Record<string, boolean>; colors: Record<string, string> }
const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v && Array.isArray(v.symbols)) {
      return { symbols: v.symbols.filter((x) => typeof x === 'string').slice(0, COMPARE_COLORS.length), hidden: v.hidden ?? {}, colors: v.colors ?? {} };
    }
  } catch { /* fall through */ }
  return { symbols: [], hidden: {}, colors: {} };
};

/** A compared symbol's colour: the reader's choice, else the next of TradingView's palette. */
export function compareColor(s: Pick<CompareState, 'symbols' | 'colors'>, symbol: string): string {
  const i = s.symbols.indexOf(symbol);
  return s.colors[symbol] ?? COMPARE_COLORS[(i < 0 ? 0 : i) % COMPARE_COLORS.length]!;
}

export const useCompare = create<CompareState>((set, get) => ({
  ...read(),
  add: (s) => { if (!get().symbols.includes(s) && get().symbols.length < COMPARE_COLORS.length) set({ symbols: [...get().symbols, s] }); },
  remove: (s) => {
    const { [s]: _h, ...hidden } = get().hidden; void _h;
    const { [s]: _c, ...colors } = get().colors; void _c;
    set({ symbols: get().symbols.filter((x) => x !== s), hidden, colors });
  },
  clear: () => set({ symbols: [], hidden: {}, colors: {} }),
  toggle: (s) => set({ hidden: { ...get().hidden, [s]: !get().hidden[s] } }),
  setColor: (s, color) => set({ colors: { ...get().colors, [s]: color } }),
}));

// Every change is written, whoever makes it (a saved layout sets the list directly).
useCompare.subscribe((s) => {
  try { localStorage.setItem(KEY, JSON.stringify({ symbols: s.symbols, hidden: s.hidden, colors: s.colors })); } catch { /* storage blocked */ }
});
