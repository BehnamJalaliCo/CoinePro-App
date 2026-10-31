// The screener's growth-scan alerts (OLD ScreenerController.toggleWatch, feature/screener ScanWatch,
// app/alerts ScanWatchWorker, 5.17.0): «tell me when a market enters this scan».
//
// A watch keeps the scan's settings, the markets it runs over (fixed when it is made: the most
// liquid MAX_SYMBOLS the screen was scanning) and the markets that matched last time. Every half
// hour while a tab is open each watch is run again with the same scan and the same rule, on closed
// bars only, and a market that matches now and did not last time is announced — once, not on every
// pass while it stays in. The first notification is about a market that *started* to qualify:
// what matched when the watch was made is recorded as already known.
//
// Announcements go through the market notifications' gates (state/pulse): never in the night or the
// reader's quiet hours, and counted against the six a day. A pass that cannot announce keeps the old
// record, so its entrants are announced on a later pass rather than lost. Under ?e2e nothing runs
// unless a test opts in and drives `runWatches` itself.
import { create } from 'zustand';
import { pacedHistory } from '../data/pacer';
import { intervalSeconds } from '../data/intervals';
import { entrants, matches, scanOf, MIN_BARS } from '../pages/growthScan';
import { allowed, canSpend, isE2E, post, usePulse } from './pulse';
import { usePrefs } from './prefs';
import { usePage } from './page';
import { faNum } from '../i18n/time';
import type { Candle } from '../data/types';

export interface ScanWatch { id: string; scanIds: string[]; within: number; minGrowth: number | null; tf: string; symbols: string[]; known: string[] }
/** How many markets a watch scans (OLD ScanWatch.MAX_SYMBOLS). */
export const MAX_SYMBOLS = 150;
export const PERIOD_MS = 30 * 60 * 1000;
const SHOWN = 5;
const CONCURRENCY = 4;
const KEY = 'pc.scanwatch.v1';

const sameScan = (w: ScanWatch, ids: string[], within: number, minGrowth: number | null, tf: string) =>
  [...w.scanIds].sort().join(',') === [...ids].sort().join(',') && w.within === within && w.minGrowth === minGrowth && w.tf === tf;

interface State {
  watches: ScanWatch[];
  /** The watch matching a scan on screen, if the reader has one. */
  find(ids: string[], within: number, minGrowth: number | null, tf: string): ScanWatch | null;
  /** Makes a watch for the scan on screen, or removes the one there is. */
  toggle(scan: { ids: string[]; within: number; minGrowth: number | null; tf: string; symbols: string[]; matched: string[] }): void;
  record(id: string, known: string[]): void;
  remove(id: string): void;
}

const load = (): ScanWatch[] => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as ScanWatch[]; return Array.isArray(v) ? v : []; } catch { return []; } };

export const useScanWatch = create<State>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().watches)); } catch { /* ignore */ } };
  return {
    watches: load(),
    find: (ids, within, minGrowth, tf) => get().watches.find((w) => sameScan(w, ids, within, minGrowth, tf)) ?? null,
    toggle: (s) => {
      const existing = get().find(s.ids, s.within, s.minGrowth, s.tf);
      if (existing) { set({ watches: get().watches.filter((w) => w.id !== existing.id) }); save(); return; }
      const watch: ScanWatch = { id: `watch_${Date.now()}`, scanIds: [...s.ids], within: s.within, minGrowth: s.minGrowth, tf: s.tf, symbols: s.symbols.slice(0, MAX_SYMBOLS), known: [...s.matched] };
      set({ watches: [...get().watches, watch] }); save();
    },
    record: (id, known) => { set({ watches: get().watches.map((w) => (w.id === id ? { ...w, known } : w)) }); save(); },
    remove: (id) => { set({ watches: get().watches.filter((w) => w.id !== id) }); save(); },
  };
});

/** Bars whose interval has ended: a setup on the forming bar can un-happen before the reader looks. */
export function closedOnly(bars: Candle[], tf: string, nowSec = Date.now() / 1000): Candle[] {
  const len = intervalSeconds(tf);
  return bars.filter((b) => b.t + len <= nowSec);
}

/** The body: tickers are Latin and read as a list; the count is prose (OLD scan_watch_body_more). */
export function watchBody(list: string[], en: boolean): string {
  const shown = list.slice(0, SHOWN).join('، ');
  const more = list.length - SHOWN;
  return more > 0 ? (en ? `${shown} and ${more} more` : `${shown} و ${faNum(more)} نماد دیگر`) : shown;
}

/** Runs one watch: the markets it matches now, and those it could not read (kept as they were). */
export async function scanWatch(w: ScanWatch, bars: (symbol: string) => Promise<Candle[] | null> = defaultBars(w.tf)): Promise<string[]> {
  const queue = w.symbols.slice(0, MAX_SYMBOLS);
  const out: string[] = [];
  const known = new Set(w.known);
  let i = 0;
  const worker = async () => {
    while (i < queue.length) {
      const symbol = queue[i++]!;
      const got = await bars(symbol).catch(() => null);
      // Not read (a 429, a failure): its standing is unknown, so it keeps last pass's.
      if (!got) { if (known.has(symbol)) out.push(symbol); continue; }
      const closed = closedOnly(got, w.tf);
      if (closed.length < MIN_BARS) continue;
      const scan = scanOf(closed);
      if (scan && matches(scan, w.scanIds, w.within, w.minGrowth)) out.push(symbol);
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  return out.sort();
}

const defaultBars = (tf: string) => async (symbol: string) => (await pacedHistory(symbol, tf)).bars;

/** One pass over every watch (OLD ScanWatchWorker.doWork). */
export async function runWatches(now = Date.now(), bars?: (symbol: string, tf: string) => Promise<Candle[] | null>): Promise<void> {
  if (!allowed() || !usePulse.getState().scan) return;
  const en = usePrefs.getState().lang === 'en';
  for (const w of useScanWatch.getState().watches) {
    const matched = await scanWatch(w, bars ? (s) => bars(s, w.tf) : undefined);
    const fresh = entrants(matched, w.known);
    if (fresh.length) {
      if (!canSpend(now)) continue;
      const first = fresh[0]!;
      const ok = post('scan_watch', en ? 'New markets entered your growth scan' : 'نمادهای تازه وارد اسکن رشد شما شدند', watchBody(fresh, en), {
        tag: `scan-watch:${w.id}`, now,
        onClick: () => { usePrefs.getState().setSymbol(first); usePrefs.getState().setInterval(w.tf); usePage.getState().close(); },
      });
      if (!ok) continue;
    }
    useScanWatch.getState().record(w.id, matched);
  }
}

let started = false;
/** Every half hour while a tab is open and there is a watch. */
export function startScanWatch(): void {
  if (started || isE2E()) return;
  started = true;
  window.setInterval(() => { if (useScanWatch.getState().watches.length) void runWatches(); }, PERIOD_MS);
}
