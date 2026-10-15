// The reader's watchlist. A guest's lives in this browser; a signed-in reader's is synced with the
// server by the data layer (the same list the phone shows).
import { create } from 'zustand';

const KEY = 'pc.watchlist.v1';
export const DEFAULT_WATCHLIST = [
  'BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT',
  'EURUSD', 'GBPUSD', 'USDJPY', 'AUDUSD', 'USDCHF',
  'XAUUSD', 'XAGUSD',
];

function read(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as unknown;
    return Array.isArray(v) && v.every((x) => typeof x === 'string') && v.length ? (v as string[]) : DEFAULT_WATCHLIST;
  } catch { return DEFAULT_WATCHLIST; }
}

interface WatchlistState {
  symbols: string[];
  set(symbols: string[]): void;
  add(symbol: string): void;
  remove(symbol: string): void;
}

export const useWatchlist = create<WatchlistState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().symbols)); } catch { /* ignore */ } };
  return {
    symbols: read(),
    set: (symbols) => { set({ symbols }); save(); },
    add: (s) => { if (!get().symbols.includes(s)) { set({ symbols: [...get().symbols, s] }); save(); } },
    remove: (s) => { set({ symbols: get().symbols.filter((x) => x !== s) }); save(); },
  };
});
