// Live prices for every symbol the screen shows, keyed by symbol. The data layer writes; the
// watchlist, the details panel and the tab title read.
import { create } from 'zustand';
import type { Instrument, Ticker } from '../data/types';
import { instrumentOf } from '../data/symbols';

interface MarketState {
  tickers: Record<string, Ticker>;
  instruments: Record<string, Instrument>;
  /** When live prices last arrived from a backend (ms), 0 before the first; saved tickers do not count. */
  liveAt: number;
  put(tickers: Ticker[]): void;
  markLive(at: number): void;
  putInstruments(list: Instrument[]): void;
}

export const useMarket = create<MarketState>((set) => ({
  tickers: {},
  instruments: {},
  liveAt: 0,
  markLive: (at) => set({ liveAt: at }),
  put: (list) => set((s) => {
    const next = { ...s.tickers };
    for (const t of list) next[t.symbol] = t;
    return { tickers: next };
  }),
  putInstruments: (list) => set((s) => {
    const next = { ...s.instruments };
    for (const i of list) next[i.symbol] = i;
    return { instruments: next };
  }),
}));

/** Price precision for a symbol: the catalogue's, else derived from its live price. */
export function digitsOf(symbol: string): number {
  const s = useMarket.getState();
  return s.instruments[symbol]?.digits ?? instrumentOf(symbol, s.tickers[symbol]?.last).digits;
}
