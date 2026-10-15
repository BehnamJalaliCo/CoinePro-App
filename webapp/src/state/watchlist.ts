// The reader's watchlists, as TradingView keeps them: several named lists, one active; seven colour
// flags on any symbol (each flag colour is a list of its own); a note per symbol; sections. A
// guest's live in this browser; a signed-in reader's lists are the phone's document on TradeYar
// (data/watchlistSync.ts), list by list.
import { create } from 'zustand';
import { uuid } from '../data/http';

const KEY_V1 = 'pc.watchlist.v1';
const KEY = 'pc.watchlists.v2';
export const DEFAULT_WATCHLIST = [
  'BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT',
  'EURUSD', 'GBPUSD', 'USDJPY', 'AUDUSD', 'USDCHF',
  'XAUUSD', 'XAGUSD',
];
export const FLAG_COLORS = ['#F23645', '#2962FF', '#089981', '#FF9800', '#9C27B0', '#00BCD4', '#E91E63'];

export interface WList { id: string; name: string; symbols: string[] }
interface Stored { lists: WList[]; active: string; flags: Record<string, number>; notes: Record<string, string> }

const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null;
    if (v && Array.isArray(v.lists) && v.lists.length) {
      const lists = v.lists.map((l) => ({ id: String(l.id), name: String(l.name ?? 'Watchlist'), symbols: strings(l.symbols) }));
      return { lists, active: lists.some((l) => l.id === v.active) ? v.active : lists[0]!.id, flags: v.flags ?? {}, notes: v.notes ?? {} };
    }
  } catch { /* fall through */ }
  let first = DEFAULT_WATCHLIST;
  try { const old = strings(JSON.parse(localStorage.getItem(KEY_V1) ?? 'null')); if (old.length) first = old; } catch { /* ignore */ }
  return { lists: [{ id: 'default', name: 'Watchlist', symbols: first }], active: 'default', flags: {}, notes: {} };
}

/** Symbols out of a pasted or imported text: "BINANCE:BTCUSDT, OANDA:XAUUSD\nETH/USDT" → upper case, no prefixes. */
export function parseSymbols(text: string): string[] {
  const out: string[] = [];
  for (const raw of text.split(/[\s,;]+/)) {
    const s = raw.replace(/^###.*$/, '').replace(/^[A-Z0-9_]+:/i, '').replace(/[/\-_.]/g, '').toUpperCase();
    if (/^[A-Z0-9]{2,15}$/.test(s) && !out.includes(s)) out.push(s);
  }
  return out;
}

interface WatchlistState extends Stored {
  /** The active list's symbols (what most of the app reads). */
  symbols: string[];
  set(symbols: string[]): void;
  add(symbol: string, listId?: string): void;
  remove(symbol: string): void;
  setLists(lists: WList[]): void;
  setActive(id: string): void;
  createList(name: string, symbols?: string[]): string;
  renameList(id: string, name: string): void;
  deleteList(id: string): void;
  flag(symbol: string, color: number | null): void;
  note(symbol: string, text: string): void;
}

export const useWatchlist = create<WatchlistState>((set, get) => {
  const init = read();
  const activeSymbols = (s: Stored) => s.lists.find((l) => l.id === s.active)?.symbols ?? [];
  const commit = (patch: Partial<Stored>) => {
    const next = { ...get(), ...patch };
    const symbols = activeSymbols(next);
    set({ ...patch, symbols });
    const { lists, active, flags, notes } = get();
    try { localStorage.setItem(KEY, JSON.stringify({ lists, active, flags, notes })); localStorage.setItem(KEY_V1, JSON.stringify(symbols)); } catch { /* ignore */ }
  };
  const mapActive = (fn: (l: WList) => WList) => get().lists.map((l) => (l.id === get().active ? fn(l) : l));
  return {
    ...init,
    symbols: activeSymbols(init),
    set: (symbols) => commit({ lists: mapActive((l) => ({ ...l, symbols })) }),
    add: (s, listId) => {
      const id = listId ?? get().active;
      commit({ lists: get().lists.map((l) => (l.id === id && !l.symbols.includes(s) ? { ...l, symbols: [...l.symbols, s] } : l)) });
    },
    remove: (s) => commit({ lists: mapActive((l) => ({ ...l, symbols: l.symbols.filter((x) => x !== s) })) }),
    setLists: (lists) => {
      if (!lists.length) return;
      commit({ lists, active: lists.some((l) => l.id === get().active) ? get().active : lists[0]!.id });
    },
    setActive: (active) => { if (get().lists.some((l) => l.id === active)) commit({ active }); },
    createList: (name, symbols = []) => {
      const id = `web-${uuid().slice(0, 8)}`;
      commit({ lists: [...get().lists, { id, name: name.trim() || 'Watchlist', symbols }], active: id });
      return id;
    },
    renameList: (id, name) => commit({ lists: get().lists.map((l) => (l.id === id ? { ...l, name: name.trim() || l.name } : l)) }),
    deleteList: (id) => {
      if (get().lists.length <= 1) return;
      const lists = get().lists.filter((l) => l.id !== id);
      commit({ lists, active: get().active === id ? lists[0]!.id : get().active });
    },
    flag: (symbol, color) => {
      const flags = { ...get().flags };
      if (color == null) delete flags[symbol]; else flags[symbol] = color;
      commit({ flags });
    },
    note: (symbol, text) => {
      const notes = { ...get().notes };
      if (text.trim()) notes[symbol] = text.trim(); else delete notes[symbol];
      commit({ notes });
    },
  };
});
