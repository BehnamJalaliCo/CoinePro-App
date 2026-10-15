// Bars for any symbol and interval, from whichever backend owns the symbol.
//
//   crypto, guest      GET /api/crypto/candles?symbol&tf&limit (≤500, latest only)
//   crypto, signed in  GET /up/tradeyar/api/mobile/v1/market/candles?symbol&tf&limit&before (≤1000, pages)
//   forex/metals/…     GET /up/coineprofx/api/academy/chart/{SYMBOL}?tf&limit&before (guest or academy token)
//
// Intervals a backend lacks are folded from a finer one on calendar boundaries, as TradingView
// does: 3m from 1m, 2h from 1h, a week from days (Monday open), a month from days.
import type { Candle } from './types';
import { intervalSeconds } from './intervals';
import { HttpError, request } from './http';
import { backendOf } from './symbols';
import { accessFor } from './auth';
import { academyToken, dropAcademyToken, dropGuestToken, guestToken, spendGuestBudget, type Urgency } from './forexAccess';

interface Plan { tf: string; fold: 'none' | 'minutes' | 'week' | 'month'; factor: number }

const CRYPTO_PLAN: Record<string, Plan> = {
  '1': { tf: 'M1', fold: 'none', factor: 1 },
  '3': { tf: 'M1', fold: 'minutes', factor: 3 },
  '5': { tf: 'M5', fold: 'none', factor: 1 },
  '15': { tf: 'M15', fold: 'none', factor: 1 },
  '30': { tf: 'M30', fold: 'none', factor: 1 },
  '60': { tf: 'H1', fold: 'none', factor: 1 },
  '120': { tf: 'H1', fold: 'minutes', factor: 2 },
  '240': { tf: 'H4', fold: 'none', factor: 1 },
  '1D': { tf: 'D1', fold: 'none', factor: 1 },
  '1W': { tf: 'W1', fold: 'none', factor: 1 },
  '1M': { tf: 'D1', fold: 'month', factor: 1 },
};

const FX_PLAN: Record<string, Plan> = {
  '5': { tf: 'M5', fold: 'none', factor: 1 },
  '15': { tf: 'M15', fold: 'none', factor: 1 },
  '30': { tf: 'M15', fold: 'minutes', factor: 2 },
  '60': { tf: 'H1', fold: 'none', factor: 1 },
  '120': { tf: 'H1', fold: 'minutes', factor: 2 },
  '240': { tf: 'H4', fold: 'none', factor: 1 },
  '1D': { tf: 'D1', fold: 'none', factor: 1 },
  '1W': { tf: 'D1', fold: 'week', factor: 1 },
  '1M': { tf: 'D1', fold: 'month', factor: 1 },
};

export class UnsupportedInterval extends Error {}

function bucketOf(t: number, interval: string, fold: Plan['fold']): number {
  if (fold === 'week') return t - ((t - 4 * 86400) % 604800 + 604800) % 604800; // Monday 00:00 UTC
  if (fold === 'month') { const d = new Date(t * 1000); return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) / 1000; }
  const s = intervalSeconds(interval);
  return Math.floor(t / s) * s;
}

export function foldBars(bars: Candle[], interval: string, fold: Plan['fold']): Candle[] {
  if (fold === 'none') return bars;
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
  const plan = (crypto ? CRYPTO_PLAN : FX_PLAN)[interval];
  if (!plan) throw new UnsupportedInterval(interval);
  const want = crypto ? 500 : 1500;
  const limit = Math.min(want * plan.factor, crypto ? 1000 : 3000);
  const page = crypto ? await cryptoPage(symbol, plan, before, limit) : await forexPage(symbol, plan, before, limit, urgency);
  return { bars: foldBars(page.bars, interval, plan.fold), more: page.more };
}

/** The last few native bars, folded: the live edge's correction of what the price ticks built. */
export async function loadEdge(symbol: string, interval: string): Promise<Candle[]> {
  const crypto = backendOf(symbol) === 'tradeyar';
  const plan = (crypto ? CRYPTO_PLAN : FX_PLAN)[interval];
  if (!plan) return [];
  const n = plan.fold === 'none' ? 3 : plan.fold === 'minutes' ? plan.factor * 2 : 40;
  const page = crypto ? await cryptoPage(symbol, plan, undefined, n) : await forexPage(symbol, plan, undefined, n, 'background');
  return foldBars(page.bars.slice(-n), interval, plan.fold);
}

export function intervalsFor(symbol: string): string[] {
  return Object.keys(backendOf(symbol) === 'tradeyar' ? CRYPTO_PLAN : FX_PLAN);
}

export { bucketOf };
