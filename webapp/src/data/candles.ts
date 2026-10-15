// Bars for any symbol and interval, from whichever backend owns the symbol.
//
//   crypto, guest      GET /api/crypto/candles?symbol&tf&limit (≤500, latest only)
//   crypto, signed in  GET /up/tradeyar/api/mobile/v1/market/candles?symbol&tf&limit&before (≤1000, pages)
//   forex/metals/…     GET /up/coineprofx/api/academy/chart/{SYMBOL}?tf&limit&before (guest or academy token)
//
// Any interval is drawn: one a backend lacks is folded from the largest native timeframe that
// divides it, on clock and calendar boundaries as TradingView does (3m from 1m, 45m from 15m, 6h
// from 1h, 8h from 4h, 3D from days, a week from days on Monday, 3M from days on the quarter).
// Intraday folds go through the old Pro-Chart's resampler (engine/resample.js); a page that does
// not start on a bucket's first bar drops that partial bucket, so paging back never leaves a
// half-built bar on the chart.
import type { Candle } from './types';
import { intervalId, intervalSeconds, parseIntervalId, INTERVALS } from './intervals';
import { HttpError, request } from './http';
import { backendOf, exprLegs } from './symbols';
import { accessFor } from './auth';
import { academyToken, dropAcademyToken, dropGuestToken, guestToken, spendGuestBudget, type Urgency } from './forexAccess';
import { resampleCandles } from '../engine/resample.js';

export type Fold = 'none' | 'minutes' | 'days' | 'week' | 'month';
interface Plan { tf: string; fold: Fold; factor: number; base: string }

// Native timeframes per backend, keyed by interval id.
const CRYPTO_NATIVE: Record<string, string> = { '1': 'M1', '5': 'M5', '15': 'M15', '30': 'M30', '60': 'H1', '240': 'H4', '1D': 'D1', '1W': 'W1' };
const FX_NATIVE: Record<string, string> = { '5': 'M5', '15': 'M15', '60': 'H1', '240': 'H4', '1D': 'D1' };

export class UnsupportedInterval extends Error {}

const nativesOf = (symbol: string) => (backendOf(symbol) === 'tradeyar' ? CRYPTO_NATIVE : FX_NATIVE);

/** How a backend draws an interval: natively, or folded from which native timeframe. */
export function planFor(symbol: string, interval: string): Plan | null {
  const natives = nativesOf(symbol);
  const p = parseIntervalId(interval);
  if (!p) return null;
  const id = intervalId(p.n, p.unit);
  if (natives[id]) return { tf: natives[id]!, fold: 'none', factor: 1, base: id };
  if (p.unit === 'm') {
    // The largest native minute timeframe that divides the interval.
    const mins = Object.keys(natives).filter((k) => /^\d+$/.test(k)).map(Number).sort((a, b) => b - a);
    const base = mins.find((m) => p.n % m === 0);
    if (!base) return null;
    return { tf: natives[String(base)]!, fold: 'minutes', factor: p.n / base, base: String(base) };
  }
  if (p.unit === 'D') return natives['1D'] ? { tf: natives['1D']!, fold: 'days', factor: p.n, base: '1D' } : null;
  if (p.unit === 'W') {
    if (natives['1W']) return { tf: natives['1W']!, fold: 'week', factor: p.n, base: '1W' };
    return natives['1D'] ? { tf: natives['1D']!, fold: 'week', factor: p.n * 7, base: '1D' } : null;
  }
  return natives['1D'] ? { tf: natives['1D']!, fold: 'month', factor: p.n * 30, base: '1D' } : null;
}

const MONDAY = 4 * 86400; // 1970-01-05 00:00 UTC was a Monday

/** The open time of the bar `t` falls in, on clock and calendar boundaries. */
function bucketOf(t: number, interval: string, fold: Fold): number {
  const p = parseIntervalId(interval) ?? { n: 1, unit: 'm' as const };
  if (fold === 'week') {
    const span = 604800 * (p.unit === 'W' ? p.n : 1);
    return t - ((((t - MONDAY) % span) + span) % span);
  }
  if (fold === 'month') {
    const d = new Date(t * 1000);
    const n = p.unit === 'M' ? p.n : 1;
    const k = d.getUTCFullYear() * 12 + d.getUTCMonth();
    const start = k - (k % n);
    return Date.UTC(Math.floor(start / 12), start % 12, 1) / 1000;
  }
  if (fold === 'days') {
    const span = 86400 * (p.unit === 'D' ? p.n : 1);
    return Math.floor(t / span) * span;
  }
  const s = intervalSeconds(interval);
  // An interval that does not divide a day starts over at each midnight (UTC), as TradingView's
  // sessions do; one that does is simply aligned to the clock.
  if (86400 % s === 0) return Math.floor(t / s) * s;
  const day = Math.floor(t / 86400) * 86400;
  return day + Math.floor((t - day) / s) * s;
}

/** Folds bars into `interval`'s buckets. */
export function foldBars(bars: Candle[], interval: string, fold: Fold): Candle[] {
  if (fold === 'none') return bars;
  // Intraday intervals that divide a day: the resampler, which aligns to the clock.
  if (fold === 'minutes' && bars.length > 1) {
    const s = intervalSeconds(interval);
    const base = minStep(bars);
    if (86400 % s === 0 && base > 0 && s % base === 0) {
      const out = resampleCandles(bars, s / base) as Candle[];
      return out.map((b) => ({ t: b.t, o: b.o, h: b.h, l: b.l, c: b.c, v: b.v }));
    }
  }
  const out: Candle[] = [];
  let cur: Candle | null = null;
  for (const b of bars) {
    const k = bucketOf(b.t, interval, fold);
    if (!cur || cur.t !== k) {
      if (cur) out.push(cur);
      cur = { t: k, o: b.o, h: b.h, l: b.l, c: b.c, v: b.v };
    } else {
      cur.h = Math.max(cur.h, b.h); cur.l = Math.min(cur.l, b.l); cur.c = b.c; cur.v += b.v;
    }
  }
  if (cur) out.push(cur);
  return out;
}

function minStep(bars: Candle[]): number {
  let m = Infinity;
  for (let i = 1; i < bars.length; i++) { const d = bars[i]!.t - bars[i - 1]!.t; if (d > 0 && d < m) m = d; }
  return Number.isFinite(m) ? m : 0;
}

const clean = (raw: unknown): Candle[] => {
  const list = Array.isArray(raw) ? raw : [];
  const out: Candle[] = [];
  for (const r of list as Record<string, unknown>[]) {
    const c = { t: Number(r.t), o: Number(r.o), h: Number(r.h), l: Number(r.l), c: Number(r.c), v: Number(r.v ?? 0) };
    if ([c.t, c.o, c.h, c.l, c.c].every(Number.isFinite) && c.t > 0) out.push(c);
  }
  out.sort((a, b) => a.t - b.t);
  // One bar per time: a page boundary or a live merge must never duplicate a bar.
  return out.filter((b, i) => i === 0 || b.t !== out[i - 1]!.t);
};

export interface HistoryPage { bars: Candle[]; more: boolean }

async function cryptoPage(symbol: string, plan: Plan, before: number | undefined, limit: number): Promise<HistoryPage> {
  const token = await accessFor('tradeyar');
  if (token) {
    try {
      const q = new URLSearchParams({ symbol, tf: plan.tf, limit: String(Math.min(limit, 1000)) });
      if (before) q.set('before', String(before));
      const r = await request<{ candles?: unknown; has_more?: boolean }>(`/up/tradeyar/api/mobile/v1/market/candles?${q}`, { token });
      return { bars: clean(r.candles), more: r.has_more ?? false };
    } catch (e) {
      if (!(e instanceof HttpError) || (e.status !== 401 && e.status !== 403)) throw e;
    }
  }
  if (before) return { bars: [], more: false }; // the public route has no paging
  const q = new URLSearchParams({ symbol, tf: plan.tf, limit: String(Math.min(limit, 500)) });
  const r = await request<{ candles?: unknown }>(`/api/crypto/candles?${q}`);
  return { bars: clean(r.candles), more: false };
}

async function forexPage(symbol: string, plan: Plan, before: number | undefined, limit: number, urgency: Urgency): Promise<HistoryPage> {
  const academy = await academyToken(await accessFor('coineprofx'));
  const token = academy ?? await guestToken();
  if (!academy && !spendGuestBudget(urgency)) throw new HttpError(429, null, 30_000);
  const q = new URLSearchParams({ tf: plan.tf, limit: String(Math.min(limit, 3000)) });
  if (before) q.set('before', String(before));
  try {
    const r = await request<{ candles?: unknown }>(`/up/coineprofx/api/academy/chart/${encodeURIComponent(symbol)}?${q}`, { token, timeoutMs: 15_000 });
    let bars = clean(r.candles);
    if (before) bars = bars.filter((b) => b.t < before); // the route's `before` is inclusive
    return { bars, more: bars.length >= Math.min(limit, 3000) * 0.9 };
  } catch (e) {
    if (e instanceof HttpError && e.status === 401) { if (academy) dropAcademyToken(); else dropGuestToken(); }
    throw e;
  }
}

/** Bars for the interval ending now, or before `before` (a bar time, seconds), oldest first. */
export async function loadHistory(symbol: string, interval: string, before?: number, urgency: Urgency = 'reader'): Promise<HistoryPage> {
  const crypto = backendOf(symbol) === 'tradeyar';
  const plan = planFor(symbol, interval);
  if (!plan) throw new UnsupportedInterval(interval);
  const want = crypto ? 500 : 1500;
  const limit = Math.min(Math.ceil(want * plan.factor), crypto ? 1000 : 3000);
  const page = crypto ? await cryptoPage(symbol, plan, before, limit) : await forexPage(symbol, plan, before, limit, urgency);
  let bars = foldBars(page.bars, interval, plan.fold);
  // The oldest bucket may have been cut by the page boundary; the next page back rebuilds it whole.
  if (plan.fold !== 'none' && page.more && bars.length > 1) bars = bars.slice(1);
  return { bars, more: page.more };
}

/** The last few native bars, folded: the live edge's correction of what the price ticks built. */
export async function loadEdge(symbol: string, interval: string): Promise<Candle[]> {
  const crypto = backendOf(symbol) === 'tradeyar';
  const plan = planFor(symbol, interval);
  if (!plan) return [];
  // Enough native bars to rebuild the forming bucket whole, and the one before it.
  const n = plan.fold === 'none' ? 3 : Math.min(Math.ceil(plan.factor * 2) + 2, crypto ? 1000 : 3000);
  const page = crypto ? await cryptoPage(symbol, plan, undefined, n) : await forexPage(symbol, plan, undefined, n, 'background');
  const folded = foldBars(page.bars.slice(-n), interval, plan.fold);
  // Only whole buckets: the first one may start before the bars fetched.
  return plan.fold === 'none' || folded.length < 2 ? folded : folded.slice(1);
}

/** The menu's intervals a symbol's backend can draw, natively or folded. */
export function intervalsFor(symbol: string): string[] {
  return INTERVALS.map((i) => i.id).filter((id) => canDraw(symbol, id));
}

/** Whether any interval id (a custom one too) can be drawn for the symbol, or every leg of a spread. */
export function canDraw(symbol: string, interval: string): boolean {
  const legs = exprLegs(symbol);
  if (legs) return legs.every((s) => planFor(s, interval) !== null);
  return planFor(symbol, interval) !== null;
}

export { bucketOf };
