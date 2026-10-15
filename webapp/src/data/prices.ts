// Live prices for the whole screen, polled through the relay's cached reads and written to the
// market store. One poller per market, paused while the tab is hidden, silenced by a 429 for as
// long as the backend asks.
//
//   crypto  GET /api/crypto/prices                         every 1 s with a crypto chart open, else 3 s
//   forex   GET /up/coineprofx/api/user/mobile/market/tickers  every max(cache_ttl, 5 s)
//           GET /up/coineprofx/api/ws/snapshot?symbols=S     every 1.5 s with a forex chart open
import { HttpError, pageVisible, request } from './http';
import { backendOf, instrumentOf } from './symbols';
import { useMarket } from '../state/market';
import type { Ticker } from './types';

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

const last = new Map<string, number>();
function publish(tickers: Ticker[]) {
  const changed: Ticker[] = [];
  for (const t of tickers) {
    if (last.get(t.symbol) !== t.last || !useMarket.getState().tickers[t.symbol]) changed.push(t);
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
  const rows = (r.data ?? []).filter((x) => typeof x.s === 'string' && Number.isFinite(x.p));
  const now = Date.now();
  publish(rows.map((x) => {
    const pct = Number(x.c ?? 0);
    const prev = pct > -100 ? x.p / (1 + pct / 100) : x.p;
    return { symbol: x.s, last: x.p, change: x.p - prev, changePct: pct, high: x.h, low: x.l, volume: x.v, prevClose: prev, ts: x.t ? x.t * 1000 : now };
  }));
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
  publish(rows.map((x) => {
    const lastPx = (x.last ?? x.price)!;
    const open = x.open_24h ?? lastPx;
    return { symbol: x.symbol, last: lastPx, change: lastPx - open, changePct: x.change_percent_24h ?? (open ? ((lastPx - open) / open) * 100 : 0), high: x.high_24h, low: x.low_24h, volume: x.volume_24h, prevClose: open, ts: x.ts ?? Date.now() };
  }));
  if (!fxCatalogued && rows.length) {
    fxCatalogued = true;
    useMarket.getState().putInstruments(rows.map((x) => instrumentOf(x.symbol, x.last ?? x.price)));
  }
  return Math.max(r.cache_ttl_ms ?? 0, 5000);
}

async function pollFxFocus(): Promise<number> {
  const sym = focus;
  if (!sym || backendOf(sym) !== 'coineprofx') return 1500;
  const r = await request<{ prices?: Record<string, { price?: number; ts?: number }> }>(`/up/coineprofx/api/ws/snapshot?symbols=${encodeURIComponent(sym)}`, { timeoutMs: 8000 });
  const q = r.prices?.[sym];
  if (q && Number.isFinite(q.price)) {
    const prior = useMarket.getState().tickers[sym];
    if (prior && prior.last !== q.price) {
      const open = prior.prevClose ?? prior.last;
      publish([{ ...prior, last: q.price!, change: q.price! - open, changePct: open ? ((q.price! - open) / open) * 100 : 0, ts: q.ts ?? Date.now() }]);
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
  loop('crypto', pollCrypto);
  loop('fx', pollFxTickers);
  loop('fx-focus', pollFxFocus);
}
