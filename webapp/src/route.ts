// The address follows the open chart: <base>SYMBOL/INTERVAL (pro-chart.com/next/BTCUSDT/4h), and a
// link in that form, or ?symbol=&tf= as the current terminal writes, opens straight onto the chart.
import { useEffect } from 'react';
import { usePrefs } from './state/prefs';
import { INTERVALS, intervalLabel } from './data/intervals';
import { usePage } from './state/page';
import { pageById, pageFromPath } from './pages/registry';

const BASE = import.meta.env.BASE_URL;
const SYMBOL_RE = /^[A-Z0-9]{2,12}$/;

/** '4h', '240', 'H4', '15m', 'M15', '1d', 'D', '1W', 'W1', '1M' (month) → an interval id. */
export function parseInterval(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const s = raw.trim();
  if (INTERVALS.some((i) => i.id === s)) return s;
  const byLabel = INTERVALS.find((i) => i.label.toLowerCase() === s.toLowerCase() && !(s === '1m' && i.id === '1M'));
  if (s === '1M') return '1M';
  if (byLabel) return byLabel.id;
  const m = /^([MHDW])(\d{1,3})$/i.exec(s);
  if (m) {
    const n = Number(m[2]);
    const unit = m[1]!.toUpperCase();
    const id = unit === 'M' ? String(n) : unit === 'H' ? String(n * 60) : unit === 'D' ? '1D' : '1W';
    return INTERVALS.some((i) => i.id === id) ? id : null;
  }
  if (/^d$/i.test(s)) return '1D';
  if (/^w$/i.test(s)) return '1W';
  return null;
}

export function readAddress(loc: Location = window.location): { symbol: string | null; interval: string | null } {
  const rest = loc.pathname.startsWith(BASE) ? loc.pathname.slice(BASE.length) : '';
  const [seg1, seg2] = rest.split('/').filter(Boolean);
  const q = new URLSearchParams(loc.search);
  const symbol = (q.get('symbol') ?? seg1 ?? '').toUpperCase().replace('/', '');
  return {
    symbol: SYMBOL_RE.test(symbol) ? symbol : null,
    interval: parseInterval(q.get('tf') ?? q.get('interval') ?? seg2 ?? null),
  };
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
      : `${BASE}${symbol}/${intervalLabel(interval)}`;
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
