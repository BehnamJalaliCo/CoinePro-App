// The reader's watchlists, as TradingView keeps them: several named lists, one active; sections
// inside a list (a header, then its symbols, folded or open); seven colour flags on any symbol (each
// flag colour is a list of its own); a note per symbol. A guest's live in this browser; a
// signed-in reader's lists are the phone's document on TradeYar (data/watchlistSync.ts), list by
// list, with the sections, flags and notes in the document's extra fields.
//
// A list's `symbols` is always the whole list in display order — the symbols outside any section
// first, then each section's in turn — so the phone, which knows nothing of sections, shows the
// same order.
import { create } from 'zustand';
import { uuid } from '../data/http';

const KEY_V1 = 'pc.watchlist.v1';
const KEY = 'pc.watchlists.v2';
const VIEW_KEY = 'pc.watchview.v1';
export const DEFAULT_WATCHLIST = [
  'BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT',
  'EURUSD', 'GBPUSD', 'USDJPY', 'AUDUSD', 'USDCHF',
  'XAUUSD', 'XAGUSD',
];
/** TradingView's seven flag colours: red, blue, green, orange, purple, cyan, pink. */
export const FLAG_COLORS = ['#F23645', '#2962FF', '#089981', '#FF9800', '#9C27B0', '#00BCD4', '#E91E63'];
/** The phone's flag ids (core:datastore WatchlistFlag) for the colours they share; cyan and pink are the web's own. */
export const FLAG_IDS = ['red', 'blue', 'green', 'orange', 'purple', 'cyan', 'pink'];
export const FLAG_NAMES: { fa: string; en: string }[] = [
  { fa: 'قرمز', en: 'Red' }, { fa: 'آبی', en: 'Blue' }, { fa: 'سبز', en: 'Green' }, { fa: 'نارنجی', en: 'Orange' },
  { fa: 'بنفش', en: 'Purple' }, { fa: 'فیروزه‌ای', en: 'Cyan' }, { fa: 'صورتی', en: 'Pink' },
];

export interface WSection { id: string; name: string; symbols: string[]; collapsed?: boolean }
export interface WList { id: string; name: string; symbols: string[]; sections?: WSection[] }
interface Stored { lists: WList[]; active: string; flags: Record<string, number>; notes: Record<string, string> }

const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

/** The list with its invariants restored: no duplicates, every section symbol in the list and in one section only, `symbols` in display order. */
export function normalizeList(l: WList): WList {
  const all = [...new Set(strings(l.symbols))];
  const inList = new Set(all);
  const taken = new Set<string>();
  const sections = (l.sections ?? []).map((s) => {
    const own = [...new Set(strings(s.symbols))].filter((x) => inList.has(x) && !taken.has(x));
    own.forEach((x) => taken.add(x));
    return { id: String(s.id), name: String(s.name ?? ''), symbols: own, ...(s.collapsed ? { collapsed: true } : {}) };
  });
  const loose = all.filter((x) => !taken.has(x));
  const out: WList = { id: l.id, name: l.name, symbols: [...loose, ...sections.flatMap((s) => s.symbols)] };
  if (sections.length) out.sections = sections;
  return out;
}

function read(): Stored {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null;
    if (v && Array.isArray(v.lists) && v.lists.length) {
      const lists = v.lists.map((l) => normalizeList({ id: String(l.id), name: String(l.name ?? 'Watchlist'), symbols: strings(l.symbols), sections: Array.isArray(l.sections) ? l.sections : undefined }));
      return { lists, active: lists.some((l) => l.id === v.active) ? v.active : lists[0]!.id, flags: v.flags ?? {}, notes: v.notes ?? {} };
    }
  } catch { /* fall through */ }
  let first = DEFAULT_WATCHLIST;
  try { const old = strings(JSON.parse(localStorage.getItem(KEY_V1) ?? 'null')); if (old.length) first = old; } catch { /* ignore */ }
  return { lists: [{ id: 'default', name: 'Watchlist', symbols: first }], active: 'default', flags: {}, notes: {} };
}

const cleanSymbol = (raw: string) => raw.trim().replace(/^[A-Z0-9_]+:/i, '').replace(/[/\-_.!]/g, '').toUpperCase();

/** Symbols out of a pasted or imported text: "BINANCE:BTCUSDT, OANDA:XAUUSD\nETH/USDT" → upper case, no prefixes. */
export function parseSymbols(text: string): string[] {
  return parseList(text).symbols;
}

/**
 * A TradingView list export or any pasted text: symbols separated by commas, semicolons or new
 * lines (or spaces when there is none of those), `###Name` opening a section.
 */
export function parseList(text: string): { symbols: string[]; sections: { name: string; symbols: string[] }[] } {
  const symbols: string[] = [];
  const sections: { name: string; symbols: string[] }[] = [];
  const tokens = /[,;\n]/.test(text) ? text.split(/[,;\n\r]+/) : text.split(/\s+/);
  for (const raw0 of tokens) {
    const raw = raw0.trim();
    if (!raw) continue;
    if (raw.startsWith('###')) { const name = raw.slice(3).trim(); if (name) sections.push({ name, symbols: [] }); continue; }
    for (const part of raw.split(/\s+/)) {
      const s = cleanSymbol(part);
      if (!/^[A-Z0-9]{2,15}$/.test(s) || symbols.includes(s)) continue;
      symbols.push(s);
      sections[sections.length - 1]?.symbols.push(s);
    }
  }
  return { symbols, sections };
}

/** The list as TradingView exports it: one line, sections as `###Name`. */
export function exportList(l: WList): string {
  const n = normalizeList(l);
  const inSection = new Set((n.sections ?? []).flatMap((s) => s.symbols));
  const parts = n.symbols.filter((s) => !inSection.has(s));
  for (const s of n.sections ?? []) parts.push(`###${s.name}`, ...s.symbols);
  return parts.join(',');
}

interface WatchlistState extends Stored {
  /** The active list's symbols (what most of the app reads). */
  symbols: string[];
  set(symbols: string[]): void;
  add(symbol: string, listId?: string, sectionId?: string): void;
  remove(symbol: string): void;
  removeMany(symbols: string[]): void;
  /** Moves symbols before `before` (or to the end of `sectionId`, or of the loose symbols when null). */
  move(symbols: string[], before: string | null, sectionId: string | null): void;
  setLists(lists: WList[]): void;
  setActive(id: string): void;
  createList(name: string, symbols?: string[], sections?: { name: string; symbols: string[] }[]): string;
  renameList(id: string, name: string): void;
  deleteList(id: string): void;
  addSection(name: string, symbols?: string[]): string;
  renameSection(id: string, name: string): void;
  deleteSection(id: string): void;
  toggleSection(id: string): void;
  moveSection(id: string, before: string | null): void;
  flag(symbol: string, color: number | null): void;
  flagMany(symbols: string[], color: number | null): void;
  unflagAll(): void;
  note(symbol: string, text: string): void;
  /** Replaces the flags and notes (the sync's pull). */
  setMarks(flags: Record<string, number>, notes: Record<string, string>): void;
}

export const useWatchlist = create<WatchlistState>((set, get) => {
  const init = read();
  const activeSymbols = (s: Stored) => s.lists.find((l) => l.id === s.active)?.symbols ?? [];
  const commit = (patch: Partial<Stored>) => {
    if (patch.lists) patch = { ...patch, lists: patch.lists.map(normalizeList) };
    const next = { ...get(), ...patch };
    const symbols = activeSymbols(next);
    set({ ...patch, symbols });
    const { lists, active, flags, notes } = get();
    try { localStorage.setItem(KEY, JSON.stringify({ lists, active, flags, notes })); localStorage.setItem(KEY_V1, JSON.stringify(symbols)); } catch { /* ignore */ }
  };
  const mapActive = (fn: (l: WList) => WList) => get().lists.map((l) => (l.id === get().active ? fn(l) : l));
  const newId = (p: string) => `${p}-${uuid().slice(0, 8)}`;
  return {
    ...init,
    symbols: activeSymbols(init),
    set: (symbols) => commit({ lists: mapActive((l) => ({ ...l, symbols })) }),
    add: (s, listId, sectionId) => {
      const id = listId ?? get().active;
      commit({
        lists: get().lists.map((l) => {
          if (l.id !== id) return l;
          const symbols = l.symbols.includes(s) ? l.symbols : [...l.symbols, s];
          const sections = sectionId ? (l.sections ?? []).map((x) => ({ ...x, symbols: x.id === sectionId ? [...x.symbols.filter((y) => y !== s), s] : x.symbols.filter((y) => y !== s) })) : l.sections;
          return { ...l, symbols, sections };
        }),
      });
    },
    remove: (s) => get().removeMany([s]),
    removeMany: (gone) => {
      const out = new Set(gone);
      commit({ lists: mapActive((l) => ({ ...l, symbols: l.symbols.filter((x) => !out.has(x)) })) });
    },
    move: (moving, before, sectionId) => {
      const set0 = new Set(moving);
      commit({
        lists: mapActive((l0) => {
          const l = normalizeList(l0);
          // Where `before` lives decides the section, unless it is one of the moving symbols.
          const target = before && !set0.has(before) ? before : null;
          const secOf = (s: string) => (l.sections ?? []).find((x) => x.symbols.includes(s))?.id ?? null;
          const dest = target ? secOf(target) : sectionId;
          const strip = (arr: string[]) => arr.filter((x) => !set0.has(x));
          const insert = (arr: string[]) => {
            const base = strip(arr);
            const at = target ? base.indexOf(target) : -1;
            return at >= 0 ? [...base.slice(0, at), ...moving, ...base.slice(at)] : [...base, ...moving];
          };
          const loose = l.symbols.filter((s) => !secOf(s));
          const nextLoose = dest == null ? insert(loose) : strip(loose);
          const sections = (l.sections ?? []).map((x) => ({ ...x, symbols: x.id === dest ? insert(x.symbols) : strip(x.symbols) }));
          return { ...l, symbols: [...nextLoose, ...sections.flatMap((x) => x.symbols)], sections };
        }),
      });
    },
    setLists: (lists) => {
      if (!lists.length) return;
      commit({ lists, active: lists.some((l) => l.id === get().active) ? get().active : lists[0]!.id });
    },
    setActive: (active) => { if (get().lists.some((l) => l.id === active)) commit({ active }); },
    createList: (name, symbols = [], sections) => {
      const id = `web-${uuid().slice(0, 8)}`;
      const secs = sections?.length ? sections.map((s) => ({ id: newId('sec'), name: s.name, symbols: s.symbols })) : undefined;
      commit({ lists: [...get().lists, { id, name: name.trim() || 'Watchlist', symbols, sections: secs }], active: id });
      return id;
    },
    renameList: (id, name) => commit({ lists: get().lists.map((l) => (l.id === id ? { ...l, name: name.trim() || l.name } : l)) }),
    deleteList: (id) => {
      if (get().lists.length <= 1) return;
      const lists = get().lists.filter((l) => l.id !== id);
      commit({ lists, active: get().active === id ? lists[0]!.id : get().active });
    },
    addSection: (name, symbols = []) => {
      const id = newId('sec');
      const take = new Set(symbols);
      commit({
        lists: mapActive((l) => ({
          ...l,
          symbols: [...l.symbols, ...symbols.filter((s) => !l.symbols.includes(s))],
          sections: [...(l.sections ?? []).map((x) => ({ ...x, symbols: x.symbols.filter((s) => !take.has(s)) })), { id, name: name.trim() || 'Section', symbols }],
        })),
      });
      return id;
    },
    renameSection: (id, name) => commit({ lists: mapActive((l) => ({ ...l, sections: (l.sections ?? []).map((x) => (x.id === id ? { ...x, name: name.trim() || x.name } : x)) })) }),
    // The header goes; its symbols join the section above (or the loose symbols), as on TradingView.
    deleteSection: (id) => commit({
      lists: mapActive((l) => {
        const secs = l.sections ?? [];
        const i = secs.findIndex((x) => x.id === id);
        if (i < 0) return l;
        const moved = secs[i]!.symbols;
        const rest = secs.filter((x) => x.id !== id).map((x, k) => (k === i - 1 ? { ...x, symbols: [...x.symbols, ...moved] } : x));
        return { ...l, sections: rest };
      }),
    }),
    toggleSection: (id) => commit({ lists: mapActive((l) => ({ ...l, sections: (l.sections ?? []).map((x) => (x.id === id ? { ...x, collapsed: !x.collapsed } : x)) })) }),
    moveSection: (id, before) => commit({
      lists: mapActive((l) => {
        const secs = l.sections ?? [];
        const s = secs.find((x) => x.id === id);
        if (!s || id === before) return l;
        const rest = secs.filter((x) => x.id !== id);
        const at = before ? rest.findIndex((x) => x.id === before) : -1;
        return { ...l, sections: at >= 0 ? [...rest.slice(0, at), s, ...rest.slice(at)] : [...rest, s] };
      }),
    }),
    flag: (symbol, color) => get().flagMany([symbol], color),
    flagMany: (symbols, color) => {
      const flags = { ...get().flags };
      for (const s of symbols) { if (color == null) delete flags[s]; else flags[s] = color; }
      commit({ flags });
    },
    unflagAll: () => commit({ flags: {} }),
    note: (symbol, text) => {
      const notes = { ...get().notes };
      if (text.trim()) notes[symbol] = text.trim(); else delete notes[symbol];
      commit({ notes });
    },
    setMarks: (flags, notes) => commit({ flags, notes }),
  };
});

// ── How the list is shown ─────────────────────────────────────────────────────────────────────────
export type WCol = 'last' | 'change' | 'changePct' | 'volume' | 'high' | 'low' | 'range';
export const ALL_COLS: WCol[] = ['last', 'change', 'changePct', 'volume', 'high', 'low', 'range'];
export type WSortKey = 'symbol' | WCol;
export interface WatchView {
  columns: WCol[];
  /** Table view: one wide row per symbol with every chosen column; list view: TradingView's compact four. */
  table: boolean;
  logos: boolean;
  /** The instrument's name under the ticker. */
  description: boolean;
  /** Show the name instead of the ticker (TradingView's Symbol / Name choice). */
  showName: boolean;
  /** The green / grey market-open dot. */
  dot: boolean;
  /** Change against the previous daily close instead of the price 24 hours ago. */
  fromClose: boolean;
  groupBy: 'sections' | 'market' | 'none';
  sort: { key: WSortKey; desc: boolean } | null;
  /** Market groups folded shut (the reader's own sections keep their state in the list). */
  collapsed?: string[];
}
const VIEW_DEFAULT: WatchView = { columns: ['last', 'change', 'changePct'], table: true, logos: true, description: false, showName: false, dot: false, fromClose: false, groupBy: 'market', sort: null };

function readView(): WatchView {
  try {
    const v = JSON.parse(localStorage.getItem(VIEW_KEY) ?? 'null') as Partial<WatchView> | null;
    if (v && typeof v === 'object') return { ...VIEW_DEFAULT, ...v, columns: Array.isArray(v.columns) ? v.columns.filter((c): c is WCol => ALL_COLS.includes(c as WCol)) : VIEW_DEFAULT.columns };
  } catch { /* defaults */ }
  return VIEW_DEFAULT;
}

interface ViewState extends WatchView { setView(patch: Partial<WatchView>): void; toggleColumn(c: WCol): void }
export const useWatchView = create<ViewState>((set, get) => {
  const save = () => { const { setView: _a, toggleColumn: _b, ...v } = get(); try { localStorage.setItem(VIEW_KEY, JSON.stringify(v)); } catch { /* ignore */ } };
  return {
    ...readView(),
    setView: (patch) => { set(patch); save(); },
    toggleColumn: (c) => {
      const cols = get().columns.includes(c) ? get().columns.filter((x) => x !== c) : ALL_COLS.filter((x) => x === c || get().columns.includes(x));
      set({ columns: cols }); save();
    },
  };
});
