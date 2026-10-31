// Seconds and tick bars from CoinePro-FX's public trade history (OLD core/marketdata TickHistory.kt):
//   GET /up/coineprofx/api/public/market/seconds?symbol&seconds&limit&before   → {candles:[{t,o,h,l,c,v,closed}]}  (before: s)
//   GET /up/coineprofx/api/public/market/ticks?symbol&limit&before            → {ticks:[{t(ms),p,v,s}]}        (before: ms)
// Crypto from the exchange's own trades and 1-second bars, forex and metals from the broker feed's
// ticks the server keeps. A tick bar is every N trades whatever the clock says; its time is its first
// trade's second, nudged forward when a busy second holds several bars so the series stays in order.
import type { Candle } from './types';
import { request } from './http';
import { parseIntervalId } from './intervals';
import type { HistoryPage } from './candles';

const BASE = '/up/coineprofx/api/public/market';
const MAX_TICKS = 5000;
const MAX_BARS = 1000;

export interface Tick { tMs: number; price: number; volume: number }

/** Trades folded into bars of `count`; also how many trades the last bar holds. */
export function foldTicks(ticks: Tick[], count: number): { bars: Candle[]; filled: number } {
  const bars: Candle[] = [];
  let filled = 0;
  if (count <= 0) return { bars, filled };
  for (const k of ticks) {
    if (filled === 0) {
      const prev = bars[bars.length - 1];
      const t = Math.max(Math.floor(k.tMs / 1000), prev ? prev.t + 1 : 0);
      bars.push({ t, o: k.price, h: k.price, l: k.price, c: k.price, v: k.volume });
    } else {
      const b = bars[bars.length - 1]!;
      b.h = Math.max(b.h, k.price); b.l = Math.min(b.l, k.price); b.c = k.price; b.v += k.volume;
    }
    filled++;
    if (filled === count) filled = 0;
  }
  return { bars, filled };
}

/** How many trades the newest tick bar of a symbol already holds (the live edge continues it). */
export const tickFill = new Map<string, number>();

async function secondsPage(symbol: string, seconds: number, before?: number): Promise<HistoryPage> {
  const q = new URLSearchParams({ symbol, seconds: String(seconds), limit: '500' });
  if (before) q.set('before', String(before));
  const r = await request<{ candles?: Record<string, unknown>[] }>(`${BASE}/seconds?${q}`, { timeoutMs: 15_000 });
  const bars = (r.candles ?? [])
    .map((x) => ({ t: Number(x.t), o: Number(x.o), h: Number(x.h), l: Number(x.l), c: Number(x.c), v: Number(x.v ?? 0) }))
    .filter((b) => b.t > 0 && [b.o, b.h, b.l, b.c].every(Number.isFinite) && b.c > 0 && (!before || b.t < before))
    .sort((a, b) => a.t - b.t)
    .filter((b, i, a) => i === 0 || b.t !== a[i - 1]!.t);
  return { bars, more: bars.length >= 450 && bars.length <= MAX_BARS };
}

async function ticksPage(symbol: string, count: number, before?: number): Promise<HistoryPage> {
  const limit = Math.min(MAX_TICKS, Math.max(500, count * 300));
  const q = new URLSearchParams({ symbol, limit: String(limit) });
  if (before) q.set('before', String(before * 1000));
  const r = await request<{ ticks?: Record<string, unknown>[] }>(`${BASE}/ticks?${q}`, { timeoutMs: 15_000 });
  const ticks = (r.ticks ?? [])
    .map((x) => ({ tMs: Number(x.t), price: Number(x.p), volume: Number(x.v ?? 0) }))
    .filter((k) => k.tMs > 0 && Number.isFinite(k.price) && k.price > 0)
    .sort((a, b) => a.tMs - b.tMs);
  const { bars, filled } = foldTicks(ticks, count);
  if (!before) tickFill.set(`${symbol}|${count}`, filled);
  // An older page's bars must end before the chart's first bar.
  const out = before ? bars.filter((b) => b.t < before) : bars;
  return { bars: out, more: ticks.length >= limit * 0.9 };
}

/** Bars for a seconds or tick interval, ending now or before `before` (a bar time, seconds). */
export function subMinuteHistory(symbol: string, interval: string, before?: number): Promise<HistoryPage> {
  const p = parseIntervalId(interval);
  if (p?.unit === 'S') return secondsPage(symbol, p.n, before);
  if (p?.unit === 'T') return ticksPage(symbol, p.n, before);
  return Promise.resolve({ bars: [], more: false });
}
