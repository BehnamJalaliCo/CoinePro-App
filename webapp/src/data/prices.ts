// Live prices for the whole screen, polled through the relay's cached reads and written to the
// market store. One poller per market, paused while the tab is hidden, silenced by a 429 for as
// long as the backend asks.
//
//   crypto  GET /api/crypto/prices                         every 1 s with a crypto chart open, else 3 s
//   forex   GET /up/coineprofx/api/user/mobile/market/tickers  every max(cache_ttl, 5 s)
//           GET /up/coineprofx/api/ws/snapshot?symbols=S     every 1.5 s with a forex chart open
import { HttpError, pageVisible, request } from './http';
import { isOffScope } from './cryptoScope';
import { backendOf, instrumentOf } from './symbols';
import { useMarket } from '../state/market';
import type { Ticker } from './types';
import { venueName } from './venue';
import { readSnapshot, saveSnapshot, useSaved } from './saved';

type PriceListener = (price: number, tsMs: number) => void;
const listeners = new Map<string, Set<PriceListener>>();
let focus: string | null = null;

/** The symbol on the chart; its market polls faster. */
export function setFocus(symbol: string | null): void { focus = symbol; }

/** Every new price for `symbol`; returns the unsubscribe. */
export function onPrice(symbol: string, cb: PriceListener): () => void {
  let set = listeners.get(symbol);
  if (!set) listeners.set(symbol, (set = new Set()));
  set.add(cb);
  return () => { set!.delete(cb); };
}

function emit(symbol: string, price: number, ts: number) {
  listeners.get(symbol)?.forEach((cb) => cb(price, ts));
}

// ── Saved tickers ───────────────────────────────────────────────────────────────────────────────
// Each market's last good list is kept (at most every 30 s) and put on screen at start before the
// first poll answers, marked as saved so the watchlist says how old it is; the first live answer
// replaces it and clears the mark. Saved prices never reach the price listeners (the forming bar).
export const SAVED_TICKERS = { crypto: 'tickers:crypto', fx: 'tickers:fx' } as const;
type SavedMarket = keyof typeof SAVED_TICKERS;
const SAVE_EVERY = 30_000;
const savedAtMs: Record<SavedMarket, number> = { crypto: 0, fx: 0 };

function arrived(market: SavedMarket, rows: Ticker[]): void {
  const now = Date.now();
  useMarket.getState().markLive(now);
  useSaved.getState().mark(SAVED_TICKERS[market], null);
  if (rows.length && now - savedAtMs[market] >= SAVE_EVERY) { savedAtMs[market] = now; saveSnapshot(SAVED_TICKERS[market], rows, now); }
}

/** Puts each market's saved list on screen, for the moments (or the offline hours) before a poll answers. */
export function restoreSavedTickers(): void {
  for (const market of Object.keys(SAVED_TICKERS) as SavedMarket[]) {
    const snap = readSnapshot<Ticker[]>(SAVED_TICKERS[market]);
    const rows = (snap?.data ?? []).filter((x) => x && typeof x.symbol === 'string' && Number.isFinite(x.last));
    if (!snap || !rows.length) continue;
    const have = useMarket.getState().tickers;
    const missing = rows.filter((x) => !have[x.symbol]);
    if (!missing.length) continue;
    useMarket.getState().put(missing);
    useMarket.getState().putInstruments(missing.filter((x) => !useMarket.getState().instruments[x.symbol]).map((x) => instrumentOf(x.symbol, x.last)));
    useSaved.getState().mark(SAVED_TICKERS[market], snap.at);
  }
}

const last = new Map<string, number>();
function publish(tickers: Ticker[]) {
  const changed: Ticker[] = [];
  const known = useMarket.getState().tickers;
  for (const raw of tickers) {
    // A feed that does not name its venue keeps the one another feed reported for the symbol.
    const source = raw.source || known[raw.symbol]?.source;
    const t = source ? { ...raw, source } : raw;
    if (last.get(t.symbol) !== t.last || !known[t.symbol] || known[t.symbol]!.source !== t.source) changed.push(t);
    if (last.get(t.symbol) !== t.last) emit(t.symbol, t.last, t.ts);
    last.set(t.symbol, t.last);
  }
  if (changed.length) useMarket.getState().put(changed);
}

// ── Crypto ──────────────────────────────────────────────────────────────────────────────────────
interface CryptoRow { s: string; p: number; t?: number; c?: number; h?: number; l?: number; v?: number }
let cryptoCatalogued = false;

async function pollCrypto(): Promise<number> {
  const r = await request<{ data?: CryptoRow[] }>('/api/crypto/prices', { timeoutMs: 10_000 });
  // Only what TradeYar will also chart: its price list carries forex, metals and shares too.
  const rows = (r.data ?? []).filter((x) => typeof x.s === 'string' && Number.isFinite(x.p) && !isOffScope(x.s));
  const now = Date.now();
  const list: Ticker[] = rows.map((x) => {
    const pct = Number(x.c ?? 0);
    const prev = pct > -100 ? x.p / (1 + pct / 100) : x.p;
    return { symbol: x.s, last: x.p, change: x.p - prev, changePct: pct, high: x.h, low: x.l, volume: x.v, prevClose: prev, ts: x.t ? x.t * 1000 : now, source: 'LBank' };
  });
  publish(list);
  arrived('crypto', list);
  if (!cryptoCatalogued && rows.length) {
    cryptoCatalogued = true;
    useMarket.getState().putInstruments(rows.map((x) => instrumentOf(x.s, x.p)));
  }
  return focus && backendOf(focus) === 'tradeyar' ? 1000 : 3000;
}

// ── Forex ───────────────────────────────────────────────────────────────────────────────────────
interface FxTicker { symbol: string; last?: number; price?: number; open_24h?: number; high_24h?: number; low_24h?: number; change_percent_24h?: number; volume_24h?: number; ts?: number }
let fxCatalogued = false;

async function pollFxTickers(): Promise<number> {
  const r = await request<{ tickers?: FxTicker[]; cache_ttl_ms?: number }>('/up/coineprofx/api/user/mobile/market/tickers', { timeoutMs: 10_000 });
  const rows = (r.tickers ?? []).filter((x) => typeof x.symbol === 'string' && Number.isFinite(x.last ?? x.price));
  const list: Ticker[] = rows.map((x) => {
    const lastPx = (x.last ?? x.price)!;
    const open = x.open_24h ?? lastPx;
    return { symbol: x.symbol, last: lastPx, change: lastPx - open, changePct: x.change_percent_24h ?? (open ? ((lastPx - open) / open) * 100 : 0), high: x.high_24h, low: x.low_24h, volume: x.volume_24h, prevClose: open, ts: x.ts ?? Date.now() };
  });
  publish(list);
  arrived('fx', list);
  if (!fxCatalogued && rows.length) {
    fxCatalogued = true;
    useMarket.getState().putInstruments(rows.map((x) => instrumentOf(x.symbol, x.last ?? x.price)));
  }
  return Math.max(r.cache_ttl_ms ?? 0, 5000);
}

async function pollFxFocus(): Promise<number> {
  const sym = focus;
  if (!sym || backendOf(sym) !== 'coineprofx') return 1500;
  const r = await request<{ prices?: Record<string, { price?: number; ts?: number; source?: string }> }>(`/up/coineprofx/api/ws/snapshot?symbols=${encodeURIComponent(sym)}`, { timeoutMs: 8000 });
  const q = r.prices?.[sym];
  if (q && Number.isFinite(q.price)) {
    const prior = useMarket.getState().tickers[sym];
    const source = venueName(q.source) || undefined;
    if (prior && (prior.last !== q.price || (source && prior.source !== source))) {
      const open = prior.prevClose ?? prior.last;
      publish([{ ...prior, last: q.price!, change: q.price! - open, changePct: open ? ((q.price! - open) / open) * 100 : 0, ts: q.ts ?? Date.now(), ...(source ? { source } : {}) }]);
    } else if (!prior) emit(sym, q.price!, q.ts ?? Date.now());
  }
  return 1500;
}

// ── Loop ────────────────────────────────────────────────────────────────────────────────────────
function loop(name: string, step: () => Promise<number>) {
  const run = async () => {
    if (!pageVisible()) { document.addEventListener('visibilitychange', run, { once: true }); return; }
    let next = 5000;
    try { next = await step(); } catch (e) { next = e instanceof HttpError && e.retryAfterMs ? e.retryAfterMs : 10_000; }
    setTimeout(run, next);
  };
  void run();
  return name;
}

let started = false;
export function startPrices(): void {
  if (started) return;
  started = true;
  restoreSavedTickers();
  loop('crypto', pollCrypto);
  loop('fx', pollFxTickers);
  loop('fx-focus', pollFxFocus);
}
