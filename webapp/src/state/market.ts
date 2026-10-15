// Live prices for every symbol the screen shows, keyed by symbol. The data layer writes; the
// watchlist, the details panel and the tab title read.
import { create } from 'zustand';
import type { Instrument, Ticker } from '../data/types';

interface MarketState {
  tickers: Record<string, Ticker>;
  instruments: Record<string, Instrument>;
  put(tickers: Ticker[]): void;
  putInstruments(list: Instrument[]): void;
}

export const useMarket = create<MarketState>((set) => ({
  tickers: {},
  instruments: {},
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
