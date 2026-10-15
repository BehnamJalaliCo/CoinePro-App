// What the symbol details card reads beyond the live ticker: the daily history (performance over
// 1D…1Y, the 52-week range, the previous close and the day's open, high and low, average volume,
// ATR), a day of intraday closes for the sparkline, and bars per timeframe for the technical rating.
// Each is fetched once a minute at most, at background urgency so the chart's own loads come first.
import { useEffect, useState } from 'react';
import { loadHistory } from './candles';
import { atr } from '../engine/indicators.js';
import type { Candle } from './types';

const cache = new Map<string, { at: number; bars: Promise<Candle[]> }>();
export function barsFor(symbol: string, interval: string, ttlMs = 60_000): Promise<Candle[]> {
  const key = `${symbol}|${interval}`;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttlMs) return hit.bars;
  const bars = loadHistory(symbol, interval, undefined, 'background').then((r) => r.bars).catch(() => { cache.delete(key); return [] as Candle[]; });
  cache.set(key, { at: Date.now(), bars });
  return bars;
}

export interface DailyStats {
  perf: { id: string; pct: number | null }[];
  lo52: number | null; hi52: number | null;
  prevClose: number | null; open: number | null; avgVolume30: number | null;
  /** Today's bar with the live price folded in. */
  dayHigh: number | null; dayLow: number | null; dayVolume: number | null;
  /** ATR(14) on daily bars, and as a share of the price. */
  atr: number | null; atrPct: number | null;
}

const D = 86400;

/** Whether the newest daily bar is today's (UTC), i.e. still forming. */
function lastIsToday(daily: Candle[], now: number): boolean {
  const last = daily[daily.length - 1];
  return Boolean(last) && Math.floor(last!.t / D) === Math.floor(now / 1000 / D);
}

/** Performance and ranges from daily bars, by calendar time as TradingView counts them. */
export function dailyStats(daily: Candle[], last: number | null, now = Date.now()): DailyStats {
  const empty: DailyStats = { perf: [], lo52: null, hi52: null, prevClose: null, open: null, avgVolume30: null, dayHigh: null, dayLow: null, dayVolume: null, atr: null, atrPct: null };
  if (!daily.length) return empty;
  const price = last ?? daily[daily.length - 1]!.c;
  const at = (secondsAgo: number) => { const t = now / 1000 - secondsAgo; let c: number | null = null; for (const b of daily) { if (b.t <= t) c = b.c; else break; } return c; };
  const pct = (ref: number | null) => (ref ? ((price - ref) / ref) * 100 : null);
  const yearStart = Date.UTC(new Date(now).getUTCFullYear(), 0, 1) / 1000;
  const ytdRef = (() => { let c: number | null = null; for (const b of daily) { if (b.t < yearStart) c = b.c; else break; } return c ?? daily.find((b) => b.t >= yearStart)?.o ?? null; })();
  const year = daily.filter((b) => b.t >= now / 1000 - 365 * D);
  const vols = daily.slice(-30).map((b) => b.v).filter((v) => v > 0);
  const today = lastIsToday(daily, now) ? daily[daily.length - 1]! : null;
  // The previous session's close: the bar before today's, or the newest bar when today has none yet.
  const prevClose = today ? (daily.length > 1 ? daily[daily.length - 2]!.c : null) : daily[daily.length - 1]!.c;
  const a = atr(daily.map((b) => b.h), daily.map((b) => b.l), daily.map((b) => b.c), 14) as (number | null)[];
  const atrNow = a[a.length - 1] ?? null;
  return {
    perf: [
      { id: '1D', pct: pct(prevClose) },
      { id: '1W', pct: pct(at(7 * D)) }, { id: '1M', pct: pct(at(30 * D)) }, { id: '3M', pct: pct(at(91 * D)) },
      { id: '6M', pct: pct(at(182 * D)) }, { id: 'YTD', pct: pct(ytdRef) }, { id: '1Y', pct: pct(at(365 * D)) },
    ],
    lo52: year.length ? Math.min(...year.map((b) => b.l), price) : null,
    hi52: year.length ? Math.max(...year.map((b) => b.h), price) : null,
    prevClose,
    open: today ? today.o : null,
    avgVolume30: vols.length ? vols.reduce((n, v) => n + v, 0) / vols.length : null,
    dayHigh: today ? Math.max(today.h, price) : null,
    dayLow: today ? Math.min(today.l, price) : null,
    dayVolume: today ? today.v : null,
    atr: atrNow, atrPct: atrNow != null && price ? (atrNow / price) * 100 : null,
  };
}

// ── The previous daily close of many symbols (the watchlist's "change from the close") ──────────
const closes = new Map<string, number | null>();
const listeners = new Set<() => void>();
const pending = new Set<string>();
/** At most this many symbols are fetched for the close-based change; the rest keep the 24-hour one. */
export const PREV_CLOSE_LIMIT = 50;

function fetchClose(symbol: string): void {
  if (closes.has(symbol) || pending.has(symbol)) return;
  pending.add(symbol);
  void barsFor(symbol, '1D', 10 * 60_000).then((daily) => {
    closes.set(symbol, daily.length ? dailyStats(daily, null).prevClose : null);
  }).finally(() => { pending.delete(symbol); listeners.forEach((l) => l()); });
}

/** Previous daily closes for the first PREV_CLOSE_LIMIT symbols, while `on`. */
export function usePrevCloses(symbols: string[], on: boolean): Record<string, number> {
  const [, bump] = useState(0);
  useEffect(() => {
    if (!on) return;
    const l = () => bump((n) => n + 1);
    listeners.add(l);
    symbols.slice(0, PREV_CLOSE_LIMIT).forEach(fetchClose);
    return () => { listeners.delete(l); };
  }, [on, symbols]);
  if (!on) return {};
  const out: Record<string, number> = {};
  for (const s of symbols.slice(0, PREV_CLOSE_LIMIT)) { const c = closes.get(s); if (c != null) out[s] = c; }
  return out;
}
