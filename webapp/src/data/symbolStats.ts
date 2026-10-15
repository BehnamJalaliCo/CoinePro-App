// What the symbol details card reads beyond the live ticker: the daily history (performance over
// 1W…1Y, the 52-week range, the previous close and the day's open, average volume), a day of
// intraday closes for the sparkline, and bars per timeframe for the technical rating. Each is
// fetched once a minute at most, at background urgency so the chart's own loads come first.
import { loadHistory } from './candles';
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
}

/** Performance and ranges from daily bars, by calendar time as TradingView counts them. */
export function dailyStats(daily: Candle[], last: number | null, now = Date.now()): DailyStats {
  const empty: DailyStats = { perf: [], lo52: null, hi52: null, prevClose: null, open: null, avgVolume30: null };
  if (!daily.length) return empty;
  const price = last ?? daily[daily.length - 1]!.c;
  const at = (secondsAgo: number) => { const t = now / 1000 - secondsAgo; let c: number | null = null; for (const b of daily) { if (b.t <= t) c = b.c; else break; } return c; };
  const pct = (ref: number | null) => (ref ? ((price - ref) / ref) * 100 : null);
  const yearStart = Date.UTC(new Date(now).getUTCFullYear(), 0, 1) / 1000;
  const ytdRef = (() => { let c: number | null = null; for (const b of daily) { if (b.t < yearStart) c = b.c; else break; } return c ?? daily.find((b) => b.t >= yearStart)?.o ?? null; })();
  const D = 86400;
  const year = daily.filter((b) => b.t >= now / 1000 - 365 * D);
  const vols = daily.slice(-30).map((b) => b.v).filter((v) => v > 0);
  return {
    perf: [
      { id: '1W', pct: pct(at(7 * D)) }, { id: '1M', pct: pct(at(30 * D)) }, { id: '3M', pct: pct(at(91 * D)) },
      { id: '6M', pct: pct(at(182 * D)) }, { id: 'YTD', pct: pct(ytdRef) }, { id: '1Y', pct: pct(at(365 * D)) },
    ],
    lo52: year.length ? Math.min(...year.map((b) => b.l), price) : null,
    hi52: year.length ? Math.max(...year.map((b) => b.h), price) : null,
    prevClose: daily.length > 1 ? daily[daily.length - 2]!.c : null,
    open: daily[daily.length - 1]!.o,
    avgVolume30: vols.length ? vols.reduce((n, v) => n + v, 0) / vols.length : null,
  };
}
