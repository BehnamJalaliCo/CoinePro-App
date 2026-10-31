// The market preview's arithmetic (OLD feature/search MarketPreviewSheet.kt, MarketPreviewChart.kt,
// chart/core ChartReading.kt): the six spans and the bar length behind each, the spans' cache, the
// three plain-language readings, and a bar's moment at the resolution the bar has.
import * as ind from '../engine/indicators.js';
import { loadHistory } from '../data/candles';
import type { Candle } from '../data/types';

type Line = (number | null)[];
const E = ind as unknown as {
  ema(src: number[], p: number): Line;
  atr(h: number[], l: number[], c: number[], p?: number): Line;
  adx(h: number[], l: number[], c: number[], p?: number): Line;
};

/** One span and the bar length it is drawn at: each comes out as roughly one to four hundred points. */
export interface PreviewRange { id: 'day' | 'week' | 'month' | 'quarter' | 'year' | 'all'; fa: string; en: string; interval: string; bars: number }
export const PREVIEW_RANGES: PreviewRange[] = [
  { id: 'day', fa: '1D', en: '1D', interval: '15', bars: 96 },
  { id: 'week', fa: '1W', en: '1W', interval: '60', bars: 168 },
  { id: 'month', fa: '1M', en: '1M', interval: '240', bars: 180 },
  { id: 'quarter', fa: '3M', en: '3M', interval: '1D', bars: 90 },
  { id: 'year', fa: '1Y', en: '1Y', interval: '1D', bars: 365 },
  // As far back as the venue keeps, at weekly bars: more than there is, drawn as whatever comes back.
  { id: 'all', fa: 'همه', en: 'All', interval: '1W', bars: 540 },
];

/** The three sizes a milestone comes in: three is the smallest move that is not noise. */
export const MILESTONES = [3, 5, 10];

// The spans, asked for once each and held for the session. A failure is not cached: tapping the
// chip again is the retry, and a stored empty would make one bad moment a span empty for good.
const held = new Map<string, Candle[]>();
const key = (symbol: string, r: PreviewRange) => `${symbol.toUpperCase()}@${r.id}`;
export const heldSpan = (symbol: string, r: PreviewRange): Candle[] | null => held.get(key(symbol, r)) ?? null;
export async function loadSpan(symbol: string, r: PreviewRange, load: typeof loadHistory = loadHistory): Promise<Candle[]> {
  const k = key(symbol, r);
  const have = held.get(k);
  if (have) return have;
  let bars: Candle[] = [];
  try { bars = (await load(symbol, r.interval, undefined, 'reader')).bars.slice(-r.bars); } catch { bars = []; }
  if (bars.length >= 2) held.set(k, bars);
  return bars;
}
export function forgetSpans(): void { held.clear(); }

// ── ChartReading ─────────────────────────────────────────────────────────────────────────────────
export interface ChartReading { strength: number; volatility: number; bias: number }
const TRENDING = 20, STRONG = 30, MID_VOL = 0.35, HIGH_VOL = 0.7, BIAS_EDGE = 0.001, MINIMUM_BARS = 60, ATR_WINDOW = 60, FAST = 20, SLOW = 50;
/** ADX, ATR's place in its own recent range, and the fast-slow EMA gap — or null under 60 bars. */
export function readingOf(bars: Candle[]): ChartReading | null {
  if (bars.length < MINIMUM_BARS) return null;
  const h = bars.map((b) => b.h), l = bars.map((b) => b.l), c = bars.map((b) => b.c);
  const last = c.length - 1;
  const adx = E.adx(h, l, c, 14)[last];
  if (adx == null || !Number.isFinite(adx)) return null;
  const atr = E.atr(h, l, c, 14);
  const values: number[] = [];
  for (let i = Math.max(0, last - ATR_WINDOW + 1); i <= last; i++) { const v = atr[i]; if (v != null && Number.isFinite(v)) values.push(v); }
  if (!values.length) return null;
  const now = values[values.length - 1]!, lo = Math.min(...values), hi = Math.max(...values);
  const volatility = hi - lo <= 0 ? 0.5 : (now - lo) / (hi - lo);
  const fast = E.ema(c, FAST)[last], slow = E.ema(c, SLOW)[last];
  if (fast == null || slow == null) return null;
  const price = c[last]!;
  return { strength: adx, volatility, bias: price === 0 ? 0 : (fast - slow) / Math.abs(price) };
}
export const strengthLabel = (r: ChartReading, en: boolean) => (r.strength >= STRONG ? (en ? 'Strong' : 'قوی') : r.strength >= TRENDING ? (en ? 'Moderate' : 'متوسط') : (en ? 'No trend' : 'بدون روند'));
export const volatilityLabel = (r: ChartReading, en: boolean) => (r.volatility >= HIGH_VOL ? (en ? 'High' : 'زیاد') : r.volatility >= MID_VOL ? (en ? 'Medium' : 'متوسط') : (en ? 'Low' : 'کم'));
export const biasLabel = (r: ChartReading, en: boolean) => (r.bias > BIAS_EDGE ? (en ? 'Bullish' : 'صعودی') : r.bias < -BIAS_EDGE ? (en ? 'Bearish' : 'نزولی') : (en ? 'Neutral' : 'خنثی'));
/** «صعودی · قدرت قوی · نوسان کم» */
export const readingText = (r: ChartReading, en: boolean) => (en ? `${biasLabel(r, en)} · strength ${strengthLabel(r, en)} · swing ${volatilityLabel(r, en)}` : `${biasLabel(r, en)} · قدرت ${strengthLabel(r, en)} · نوسان ${volatilityLabel(r, en)}`);

/**
 * A bar's own moment at the resolution the bar has: from a day upward the clock is dropped (a daily
 * bar was not struck at midnight), and a year of days — or weekly bars — says which year.
 */
export function momentOf(tSec: number, range: PreviewRange | null, en: boolean): string {
  const d = new Date(tSec * 1000);
  const loc = en ? 'en-GB' : 'fa-IR-u-ca-persian';
  const day = (withYear: boolean) => new Intl.DateTimeFormat(loc, withYear ? { day: 'numeric', month: 'long', year: 'numeric' } : { day: 'numeric', month: 'long' }).format(d);
  if (range?.interval === '1W') return day(true);
  if (range?.interval === '1D') return day(range.id === 'year');
  return `${day(false)} · ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** The plot's points in a W×H box: the series scaled to its own extremes, inset 4 px top and bottom. */
export function linePoints(values: number[], w: number, h: number, inset = 4): { x: number; y: number }[] {
  if (values.length < 2) return [];
  const hi = Math.max(...values), lo = Math.min(...values);
  const span = hi - lo > 0 ? hi - lo : 1;
  const step = w / (values.length - 1);
  return values.map((v, i) => ({ x: step * i, y: h - inset - ((v - lo) / span) * (h - inset * 2) }));
}
