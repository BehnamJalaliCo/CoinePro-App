// The address follows the open chart: <base>SYMBOL/INTERVAL (pro-chart.com/next/BTCUSDT/4h), and a
// link in that form, or ?symbol=&tf= as the current terminal writes, opens straight onto the chart.
// A spread or formula travels encoded in the same place: /next/BTCUSDT%2FETHUSDT/1h.
import { useEffect } from 'react';
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

/** Applies the address once at start, then keeps it in step with the chart or the open page. */
export function useRoute(): void {
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const page = usePage((s) => s.page);
  const param = usePage((s) => s.param);
  useEffect(() => {
    const path = page
      ? `${BASE}${pageById(page).path}${param ? `/${encodeURIComponent(param)}` : ''}`
      : chartPath(symbol, interval);
    // The reset page keeps its token in the query until the page has read it.
    const keepQuery = page === 'reset';
    if (window.location.pathname !== path || (window.location.search && !keepQuery)) {
      window.history.replaceState(null, '', path + (keepQuery ? window.location.search : '') + window.location.hash);
    }
  }, [symbol, interval, page, param]);
}

export function applyAddress(): void {
  const rest = window.location.pathname.startsWith(BASE) ? window.location.pathname.slice(BASE.length) : '';
  const page = pageFromPath(rest);
  if (page) { usePage.getState().open(page.id, page.param); return; }
  const { symbol, interval } = readAddress();
  const p = usePrefs.getState();
  if (symbol) p.setSymbol(symbol);
  if (interval) p.setInterval(interval);
}
