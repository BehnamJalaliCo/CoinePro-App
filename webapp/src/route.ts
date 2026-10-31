// The address follows the open chart: <base>SYMBOL/INTERVAL (pro-chart.com/next/BTCUSDT/4h), and a
// link in that form, or ?symbol=&tf= as the current terminal writes, opens straight onto the chart.
// A spread or formula travels encoded in the same place: /next/BTCUSDT%2FETHUSDT/1h.
import { useEffect, useRef } from 'react';
import { usePrefs } from './state/prefs';
import { intervalLabel, parseIntervalInput } from './data/intervals';
import { usePage } from './state/page';
import { pageById, pageFromPath } from './pages/registry';
import { normaliseExpression } from './data/symbols';

const BASE = import.meta.env.BASE_URL;
const SYMBOL_RE = /^[A-Z0-9]{2,12}$/;

/** '4h', '240', 'H4', '15m', 'M15', '1d', 'D', '1W', 'W1', '1M' (month), '45m', '3M' → an interval id. */
export function parseInterval(raw: string | null | undefined): string | null {
  return parseIntervalInput(raw);
}

const decode = (s: string) => { try { return decodeURIComponent(s); } catch { return s; } };

/** A symbol from the address: a plain ticker, or a spread / formula of plain tickers. */
export function readSymbol(raw: string): string | null {
  const s = decode(raw).toUpperCase().replace(/\s+/g, '');
  if (SYMBOL_RE.test(s.replace('/', '')) && !/[-+*()]/.test(s) && s.split('/').length <= 2) {
    const plain = s.replace('/', '');
    // 'BTC/USDT' and 'EUR/USD' are one instrument written with a slash, not a ratio.
    if (s.includes('/') && s.split('/').every((x) => x.length >= 2 && x.length <= 5)) return plain;
    if (!s.includes('/')) return plain;
  }
  return normaliseExpression(s);
}

/** The address segment for a symbol: a spread is encoded so its slash stays one segment. */
export function symbolSegment(symbol: string): string {
  return SYMBOL_RE.test(symbol) ? symbol : encodeURIComponent(symbol);
}

export function readAddress(loc: Location = window.location): { symbol: string | null; interval: string | null } {
  const rest = loc.pathname.startsWith(BASE) ? loc.pathname.slice(BASE.length) : '';
  const [seg1, seg2] = rest.split('/').filter(Boolean);
  const q = new URLSearchParams(loc.search);
  const raw = q.get('symbol') ?? seg1 ?? '';
  return {
    symbol: raw ? readSymbol(raw) : null,
    interval: parseInterval(q.get('tf') ?? q.get('interval') ?? (seg2 ? decode(seg2) : null)),
  };
}

/** The address of a chart, for a link that opens it. */
export function chartPath(symbol: string, interval: string): string {
  return `${BASE}${symbolSegment(symbol)}/${intervalLabel(interval)}`;
}

/** The pages that are faces of one destination (the markets hub): switching between them replaces. */
const HUB = new Set(['markets', 'heatmap', 'screener']);

/** What the history entry of an open page carries: how many page entries sit above the chart's. */
interface PageEntry { pcDepth: number }
const depthOf = (state: unknown): number => {
  const d = (state as Partial<PageEntry> | null)?.pcDepth;
  return typeof d === 'number' && d > 0 ? d : 0;
};

/**
 * How the address changes when the screen does: a page opened (or another page opened over one) is
 * a new history entry, so the browser's Back closes it; a page closed from inside the app walks
 * back over its own entries; anything else (the symbol, the interval, a hub face, a page's own
 * segment) rewrites the current entry. Pure, for the tests.
 */
export function addressStep(prev: { page: string | null } | null, next: { page: string | null }, state: unknown): 'push' | 'replace' | 'back' {
  if (!prev) return 'replace';
  if (next.page && next.page !== prev.page && !(prev.page && HUB.has(prev.page) && HUB.has(next.page))) return 'push';
  if (!next.page && prev.page && depthOf(state) > 0) return 'back';
  return 'replace';
}

/** Set while the app walks back over its own page entries, so that popstate is not read as the reader's. */
let walkingBack = false;

/** Applies the address once at start, then keeps it in step with the chart or the open page. */
export function useRoute(): void {
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const page = usePage((s) => s.page);
  const param = usePage((s) => s.param);
  const prev = useRef<{ page: string | null } | null>(null);
  useEffect(() => {
    const path = page
      ? `${BASE}${pageById(page).path}${param ? `/${encodeURIComponent(param)}` : ''}`
      : chartPath(symbol, interval);
    // The reset page keeps its token in the query until the page has read it.
    const keepQuery = page === 'reset';
    const before = prev.current;
    prev.current = { page };
    // Already there: the browser moved (Back / Forward) and the screen followed it.
    if (window.location.pathname === path && (!window.location.search || keepQuery)) return;
    const url = path + (keepQuery ? window.location.search : '') + window.location.hash;
    const step = addressStep(before, { page }, window.history.state);
    if (step === 'push') window.history.pushState({ pcDepth: depthOf(window.history.state) + 1 } satisfies PageEntry, '', url);
    else if (step === 'back') {
      walkingBack = true;
      window.history.go(-depthOf(window.history.state));
      // A browser that does not move (the entries are gone) still ends on the chart's address.
      setTimeout(() => { if (walkingBack) { walkingBack = false; window.history.replaceState(null, '', url); } }, 600);
    }
    else window.history.replaceState(window.history.state, '', url);
  }, [symbol, interval, page, param]);

  // Back and Forward: the address is read again and the screen follows it.
  useEffect(() => {
    const onPop = () => {
      if (walkingBack) {
        // Back over our own entries after a page closed in the app: the chart stays as it is now.
        walkingBack = false;
        const p = usePrefs.getState();
        window.history.replaceState(null, '', chartPath(p.symbol, p.interval) + window.location.hash);
        return;
      }
      const rest = window.location.pathname.startsWith(BASE) ? window.location.pathname.slice(BASE.length) : '';
      const target = pageFromPath(rest);
      const pg = usePage.getState();
      if (target) {
        prev.current = { page: target.id };
        if (pg.page !== target.id || pg.param !== target.param) pg.open(target.id, target.param);
        return;
      }
      prev.current = { page: null };
      if (pg.page) pg.close();
      applyChart();
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);
}

function applyChart(): void {
  const { symbol, interval } = readAddress();
  const p = usePrefs.getState();
  if (symbol && symbol !== p.symbol) p.setSymbol(symbol);
  if (interval && interval !== p.interval) p.setInterval(interval);
}

export function applyAddress(): void {
  const rest = window.location.pathname.startsWith(BASE) ? window.location.pathname.slice(BASE.length) : '';
  const page = pageFromPath(rest);
  if (page) { usePage.getState().open(page.id, page.param); return; }
  applyChart();
}
