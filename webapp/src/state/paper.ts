// Paper trading: a practice account in this browser, filled at the live price. Positions are marked
// to the live tickers; closing books the result into the history. Nothing is sent anywhere.
import { create } from 'zustand';
import { uuid } from '../data/http';

export interface PaperPosition { id: string; symbol: string; side: 'long' | 'short'; qty: number; entry: number; openedAt: number; sl?: number; tp?: number }
export interface PaperTrade extends PaperPosition { exit: number; closedAt: number; pnl: number }

const KEY = 'pc.paper.v1';
const START = 10_000;
interface Stored { balance: number; positions: PaperPosition[]; history: PaperTrade[] }
const read = (): Stored => {
  try { const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null; if (v && typeof v.balance === 'number') return v; } catch { /* fall through */ }
  return { balance: START, positions: [], history: [] };
};

interface PaperState extends Stored {
  open(symbol: string, side: 'long' | 'short', qty: number, price: number, sl?: number, tp?: number): void;
  close(id: string, price: number): void;
  reset(): void;
}

export const pnlOf = (p: PaperPosition, price: number) => (p.side === 'long' ? price - p.entry : p.entry - price) * p.qty;

export const usePaper = create<PaperState>((set, get) => {
  const save = () => { const { balance, positions, history } = get(); try { localStorage.setItem(KEY, JSON.stringify({ balance, positions, history })); } catch { /* ignore */ } };
  return {
    ...read(),
    open: (symbol, side, qty, price, sl, tp) => {
      if (!(qty > 0) || !(price > 0)) return;
      set({ positions: [{ id: uuid(), symbol, side, qty, entry: price, openedAt: Date.now(), sl, tp }, ...get().positions] }); save();
    },
    close: (id, price) => {
      const p = get().positions.find((x) => x.id === id);
      if (!p) return;
      const pnl = pnlOf(p, price);
      set({ positions: get().positions.filter((x) => x.id !== id), history: [{ ...p, exit: price, closedAt: Date.now(), pnl }, ...get().history].slice(0, 200), balance: get().balance + pnl });
      save();
    },
    reset: () => { set({ balance: START, positions: [], history: [] }); save(); },
  };
});
