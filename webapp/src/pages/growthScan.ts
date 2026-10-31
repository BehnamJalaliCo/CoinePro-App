// The screener's «which market is starting to move up» reading (OLD chart/core GrowthScan.kt,
// 5.17.0): eleven named setups, each with a mechanical definition, read off the same arithmetic the
// chart draws. For each kind, how many bars ago it last fired within LOOKBACK (0 = the newest bar);
// a 0–100 growth score (a weighted checklist, not a forecast — the weights are in growthOf); and the
// trend-start signal's hit rate on this market's own history (OLD ConfidenceEngine over the
// SuperTrend flips), null below MIN_SAMPLES completed signals.
import * as ind from '../engine/indicators.js';
import { technicalRating } from '../engine/techRating.js';
import { detectDivergences } from '../engine/divergence.js';
import { detectHarmonics } from '../engine/harmonic.js';
import type { Candle } from '../data/types';

type Line = (number | null)[];
const E = ind as unknown as {
  ema(src: number[], p: number): Line;
  sma(src: number[], p: number): Line;
  rsi(c: number[], p?: number): Line;
  atr(h: number[], l: number[], c: number[], p?: number): Line;
  macd(c: number[], f?: number, s?: number, g?: number): { macd: Line; signal: Line; hist: Line };
  bollinger(c: number[], p?: number, m?: number): { upper: Line; basis: Line; lower: Line };
  keltner(h: number[], l: number[], c: number[], p?: number, m?: number): { upper: Line; basis: Line; lower: Line };
  supertrend(h: number[], l: number[], c: number[], p?: number, m?: number): { trend: Line; line: Line };
  adxDI(h: number[], l: number[], c: number[], p?: number): { adx: Line; plusDI: Line; minusDI: Line } | null;
};

export type KindId = 'scan_trend' | 'scan_breakout' | 'scan_momentum' | 'scan_golden' | 'scan_squeeze' | 'scan_volume' | 'scan_divergence' | 'scan_pullback' | 'scan_structure' | 'scan_harmonic' | 'scan_trend_end';
export interface Kind { id: KindId; fa: string; en: string; bullish: boolean; /** The OLD catalogue ids of the studies that draw it. */ studies: string[] }

/** The setups, in the OLD order (Kind.entries). */
export const KINDS: Kind[] = [
  { id: 'scan_trend', fa: 'شروع روند صعودی', en: 'Uptrend starting', bullish: true, studies: ['supertrend', 'ema', 'adx'] },
  { id: 'scan_breakout', fa: 'شکست سقف با حجم', en: 'Breakout on volume', bullish: true, studies: ['donchian'] },
  { id: 'scan_momentum', fa: 'برگشت شتاب به بالا', en: 'Momentum turning up', bullish: true, studies: ['macd', 'rsi'] },
  { id: 'scan_golden', fa: 'تقاطع طلایی', en: 'Golden cross', bullish: true, studies: ['sma'] },
  { id: 'scan_squeeze', fa: 'آزاد شدن فشردگی', en: 'Squeeze release', bullish: true, studies: ['bollinger', 'keltner'] },
  { id: 'scan_volume', fa: 'جهش حجم خرید', en: 'Buying volume surge', bullish: true, studies: ['volumeosc'] },
  { id: 'scan_divergence', fa: 'واگرایی مثبت', en: 'Bullish divergence', bullish: true, studies: ['rsi', 'divergence'] },
  { id: 'scan_pullback', fa: 'پایان اصلاح در روند صعودی', en: 'Pullback ending in an uptrend', bullish: true, studies: ['ema', 'rsi'] },
  { id: 'scan_structure', fa: 'شکست ساختار', en: 'Break of structure', bullish: true, studies: ['zigzag', 'sr'] },
  { id: 'scan_harmonic', fa: 'الگوی هارمونیک صعودی', en: 'Bullish harmonic', bullish: true, studies: ['harmonics'] },
  { id: 'scan_trend_end', fa: 'پایان روند صعودی', en: 'Uptrend ending', bullish: false, studies: ['supertrend'] },
];
export const BULLISH = KINDS.filter((k) => k.bullish);
export const kindOf = (id: string): Kind | undefined => KINDS.find((k) => k.id === id);

/** How far back a setup counts as having fired at all. */
export const LOOKBACK = 30;
/** «Fresh»: the horizon the growth score and the row's tags use. */
export const FRESH = 5;
/** Enough for EMA 200 to have warmed and a few bars besides. */
export const MIN_BARS = 60;
export const MIN_SAMPLES = 5;
const BREAKOUT_WINDOW = 20;
const PIVOT_SIDE = 5;
const DIVERGENCE_CONFIRM = 5;
/** The screen's choices (OLD ScreenerScreen WITHIN_CHOICES / GROWTH_CHOICES) and the score's tints. */
export const WITHIN_CHOICES = [1, 3, 5, 10, 20];
export const GROWTH_CHOICES = [50, 65, 80];
export const STRONG_GROWTH = 65;
export const WEAK_GROWTH = 35;

export interface Scan {
  /** Bars since each kind last fired, within LOOKBACK. A kind that did not fire is absent. */
  barsAgo: Partial<Record<KindId, number>>;
  /** 0–100. See growthOf. */
  growth: number;
  /** The trend-start signal's hit rate here, 0–100, or null under MIN_SAMPLES completed signals. */
  reliability: number | null;
  samples: number;
}

const at = (line: Line, i: number): number | null => {
  if (i < 0 || i >= line.length) return null;
  const v = line[i];
  return v != null && Number.isFinite(v) ? v : null;
};

interface Ctx {
  close: number[]; open: number[]; high: number[]; low: number[]; volume: number[]; hasVolume: boolean; last: number;
  ema20: Line; ema50: Line; ema200: Line; sma50: Line; sma200: Line; rsi: Line; hist: Line;
  adx: Line; plusDi: Line; minusDi: Line; atr: Line; trend: Line;
  bbUpper: Line; bbLower: Line; bbBasis: Line; kUpper: Line; kLower: Line; volumeAverage: Line;
  swingHighs: number[]; rating: number | null;
}

function context(bars: Candle[]): Ctx {
  const close = bars.map((b) => b.c), open = bars.map((b) => b.o), high = bars.map((b) => b.h), low = bars.map((b) => b.l), volume = bars.map((b) => b.v || 0);
  const n = bars.length;
  const dmi = E.adxDI(high, low, close, 14);
  const empty = new Array<number | null>(n).fill(null);
  const bands = E.bollinger(close, 20, 2);
  const kelt = E.keltner(high, low, close, 20, 1.5);
  const swingHighs: number[] = [];
  for (let k = PIVOT_SIDE; k < n - PIVOT_SIDE; k++) {
    let peak = true;
    for (let j = k - PIVOT_SIDE; j <= k + PIVOT_SIDE; j++) if (j !== k && high[j]! >= high[k]!) { peak = false; break; }
    if (peak) swingHighs.push(k);
  }
  const r = technicalRating(bars);
  return {
    close, open, high, low, volume, hasVolume: volume.some((v) => v > 0), last: n - 1,
    ema20: E.ema(close, 20), ema50: E.ema(close, 50), ema200: E.ema(close, 200), sma50: E.sma(close, 50), sma200: E.sma(close, 200),
    rsi: E.rsi(close, 14), hist: E.macd(close, 12, 26, 9).hist,
    adx: dmi?.adx ?? empty, plusDi: dmi?.plusDI ?? empty, minusDi: dmi?.minusDI ?? empty,
    atr: E.atr(high, low, close, 14), trend: E.supertrend(high, low, close, 10, 3).trend,
    bbUpper: bands.upper, bbLower: bands.lower, bbBasis: bands.basis, kUpper: kelt.upper, kLower: kelt.lower,
    volumeAverage: E.sma(volume, 20), swingHighs, rating: r && Number.isFinite(r.overall) ? r.overall : null,
  };
}

const crossedAbove = (fast: Line, slow: Line, i: number) => {
  const a = at(fast, i), b = at(slow, i), a0 = at(fast, i - 1), b0 = at(slow, i - 1);
  return a != null && b != null && a0 != null && b0 != null && a > b && a0 <= b0;
};

function fires(c: Ctx, kind: KindId, i: number): boolean {
  const trendTurned = (up: boolean) => {
    const now = at(c.trend, i), before = at(c.trend, i - 1);
    if (now == null || before == null) return false;
    return up ? now > 0 && before < 0 : now < 0 && before > 0;
  };
  const highest = (from: number, to: number) => { let top = -Infinity; for (let k = Math.max(0, from); k <= to; k++) top = Math.max(top, c.high[k]!); return top; };
  const inside = (k: number) => {
    const bu = at(c.bbUpper, k), bl = at(c.bbLower, k), ku = at(c.kUpper, k), kl = at(c.kLower, k);
    return bu != null && bl != null && ku != null && kl != null && bu < ku && bl > kl;
  };
  switch (kind) {
    case 'scan_trend': {
      if (trendTurned(true)) return true;
      if (!crossedAbove(c.ema20, c.ema50, i)) return false;
      const adx = at(c.adx, i), plus = at(c.plusDi, i), minus = at(c.minusDi, i);
      return adx != null && plus != null && minus != null && adx >= 18 && plus > minus;
    }
    case 'scan_trend_end': return trendTurned(false);
    case 'scan_breakout': {
      if (i < BREAKOUT_WINDOW + 1) return false;
      const ceiling = highest(i - BREAKOUT_WINDOW, i - 1);
      const ceilingBefore = highest(i - BREAKOUT_WINDOW - 1, i - 2);
      if (c.close[i]! <= ceiling || c.close[i - 1]! > ceilingBefore) return false;
      if (c.hasVolume) { const avg = at(c.volumeAverage, i - 1); return avg != null && avg > 0 && c.volume[i]! >= avg * 1.5; }
      const range = at(c.atr, i - 1);
      return range != null && c.high[i]! - c.low[i]! >= range;
    }
    case 'scan_momentum': {
      const now = at(c.hist, i), before = at(c.hist, i - 1), strength = at(c.rsi, i);
      return now != null && before != null && strength != null && before <= 0 && now > 0 && strength >= 45 && strength <= 70;
    }
    case 'scan_golden': return crossedAbove(c.sma50, c.sma200, i);
    case 'scan_squeeze': {
      if (i < 7) return false;
      let squeezed = 0;
      for (let k = i - 6; k < i; k++) if (inside(k)) squeezed++;
      if (squeezed < 5 || inside(i)) return false;
      const upper = at(c.bbUpper, i), ku = at(c.kUpper, i), basis = at(c.bbBasis, i);
      return upper != null && ku != null && basis != null && upper > ku && c.close[i]! > basis;
    }
    case 'scan_volume': {
      if (!c.hasVolume) return false;
      const avg = at(c.volumeAverage, i - 1);
      return avg != null && avg > 0 && c.volume[i]! >= avg * 2.5 && c.close[i]! > c.open[i]! && c.close[i]! > c.close[i - 1]!;
    }
    case 'scan_pullback': {
      const fast = at(c.ema50, i), slow = at(c.ema200, i), now = at(c.rsi, i), before = at(c.rsi, i - 1);
      return fast != null && slow != null && now != null && before != null && fast > slow && c.close[i]! > slow && before < 40 && now >= 40;
    }
    case 'scan_structure': {
      // The newest swing high confirmed before bar i: five bars either side, all before i.
      let pivot = -1;
      for (const k of c.swingHighs) if (k + PIVOT_SIDE < i) pivot = k;
      if (pivot < 0) return false;
      const level = c.high[pivot]!;
      return c.close[i]! > level && c.close[i - 1]! <= level;
    }
    // Read once for the whole series in scanOf.
    default: return false;
  }
}

/**
 * The composite on the newest bar. Trend 30 (close > EMA20 6, EMA20 > EMA50 8, EMA50 > EMA200 8,
 * close > EMA200 8); momentum 20 (RSI 50–70 8, 45–50 4, MACD histogram > 0 6 and rising 6);
 * strength 15 (ADX ≥ 20 with +DI over −DI 10, ADX over three bars ago 5); participation 10 (the
 * up-bar share of twenty bars' volume, 50 % → 0 … 70 % → 10; 5 with no volume); fresh setups 15
 * (5 each within FRESH bars, at most three); rating 10 (the technical rating when positive × 10);
 * penalties: the trend ending within FRESH bars −15, RSI over 80 −5.
 */
function growthOf(c: Ctx, found: Partial<Record<KindId, number>>): number {
  const i = c.last;
  let score = 0;
  const close = c.close[i]!;
  const e20 = at(c.ema20, i), e50 = at(c.ema50, i), e200 = at(c.ema200, i);
  if (e20 != null && close > e20) score += 6;
  if (e20 != null && e50 != null && e20 > e50) score += 8;
  if (e50 != null && e200 != null && e50 > e200) score += 8;
  if (e200 != null && close > e200) score += 8;
  const rsi = at(c.rsi, i);
  if (rsi != null) {
    if (rsi >= 50 && rsi <= 70) score += 8; else if (rsi >= 45 && rsi < 50) score += 4;
    if (rsi > 80) score -= 5;
  }
  const hist = at(c.hist, i), histBefore = at(c.hist, i - 1);
  if (hist != null && hist > 0) score += 6;
  if (hist != null && histBefore != null && hist > histBefore) score += 6;
  const adx = at(c.adx, i), plus = at(c.plusDi, i), minus = at(c.minusDi, i);
  if (adx != null && plus != null && minus != null && adx >= 20 && plus > minus) score += 10;
  const adxBefore = at(c.adx, i - 3);
  if (adx != null && adxBefore != null && adx > adxBefore) score += 5;
  if (c.hasVolume) {
    let up = 0, total = 0;
    for (let k = Math.max(1, i - 19); k <= i; k++) { total += c.volume[k]!; if (c.close[k]! > c.close[k - 1]!) up += c.volume[k]!; }
    score += total <= 0 ? 5 : Math.min(10, Math.max(0, ((up / total - 0.5) / 0.2) * 10));
  } else score += 5;
  const fresh = (Object.entries(found) as [KindId, number][]).filter(([k, ago]) => kindOf(k)!.bullish && ago <= FRESH).length;
  score += 5 * Math.min(3, fresh);
  if (c.rating != null && c.rating > 0) score += c.rating * 10;
  if ((found.scan_trend_end ?? Infinity) <= FRESH) score -= 15;
  return Math.min(100, Math.max(0, score));
}

/** The SuperTrend flips as signals, and how often each was right ten bars on (OLD ConfidenceEngine.measure). */
export function reliabilityOf(bars: Candle[], horizon = 10): { samples: number; winRate: number } {
  const n = bars.length;
  if (n < 20) return { samples: 0, winRate: 0 };
  const h = bars.map((b) => b.h), l = bars.map((b) => b.l), c = bars.map((b) => b.c);
  const line = E.supertrend(h, l, c, 10, 3).line;
  const events: { bar: number; long: boolean }[] = [];
  for (let bar = 1; bar < n; bar++) {
    const now = line[bar], before = line[bar - 1];
    if (now == null || before == null) continue;
    const wasBelow = c[bar - 1]! < before, isBelow = c[bar]! < now;
    if (wasBelow && !isBelow) events.push({ bar, long: true });
    if (!wasBelow && isBelow) events.push({ bar, long: false });
  }
  if (!events.length) return { samples: 0, winRate: 0 };
  // OLD SignalSpec.defaultStop: the extreme of the ten bars to the signal, a quarter-ATR beyond it.
  const range = at(E.atr(h, l, c, 14), n - 1) ?? 0;
  const first = Math.max(0, n - 500);
  let samples = 0, wins = 0;
  for (const ev of events) {
    if (ev.bar < first) continue;
    const settle = ev.bar + horizon;
    if (settle >= n) continue;
    const entry = c[ev.bar]!;
    let lo = Infinity, hi = -Infinity;
    for (let k = Math.max(0, ev.bar - 10); k <= ev.bar; k++) { lo = Math.min(lo, l[k]!); hi = Math.max(hi, h[k]!); }
    const stop = ev.long ? lo - range * 0.25 : hi + range * 0.25;
    const risk = Math.abs(entry - stop) > 0 ? Math.abs(entry - stop) : null;
    let result: number | null = null;
    if (risk != null) for (let k = ev.bar + 1; k <= settle; k++) if (ev.long ? l[k]! <= stop : h[k]! >= stop) { result = -1; break; }
    if (result == null) { const move = (c[settle]! - entry) * (ev.long ? 1 : -1); result = risk != null ? move / risk : move / entry; }
    samples++;
    if (result > 0) wins++;
  }
  return { samples, winRate: samples ? wins / samples : 0 };
}

/** Scans the bars, or null when they are too short for the two-hundred-bar average to exist. */
export function scanOf(bars: Candle[]): Scan | null {
  if (bars.length < MIN_BARS) return null;
  const c = context(bars);
  const found: Partial<Record<KindId, number>> = {};
  const last = bars.length - 1;
  const from = Math.max(1, bars.length - LOOKBACK);
  for (let i = last; i >= from; i--) for (const k of KINDS) if (found[k.id] == null && fires(c, k.id, i)) found[k.id] = last - i;
  const index = new Map(bars.map((b, i) => [b.t, i]));
  // A confirmed regular bullish RSI divergence: the event is the confirmation, `right` bars on.
  let div = -1;
  for (const d of detectDivergences(bars)) if (d.dir === 'bull' && !/مخفی|hidden/i.test(d.label)) div = Math.max(div, index.get(d.t) ?? -1);
  if (div >= 0) { const confirmed = Math.min(last, div + DIVERGENCE_CONFIRM); if (last - confirmed <= LOOKBACK) found.scan_divergence = last - confirmed; }
  let harm = -1;
  for (const m of detectHarmonics(bars)) if (m.dir === 'bull') harm = Math.max(harm, index.get(m.points[m.points.length - 1]?.t ?? m.t) ?? -1);
  if (harm >= 0 && last - harm <= LOOKBACK) found.scan_harmonic = last - harm;
  let rel: { samples: number; winRate: number } | null = null;
  try { rel = reliabilityOf(bars); } catch { rel = null; }
  const samples = rel?.samples ?? 0;
  return { barsAgo: found, growth: growthOf(c, found), reliability: rel && rel.samples >= MIN_SAMPLES ? rel.winRate * 100 : null, samples };
}

/** Whether a scan passes the screen's (and a watch's) rule: score at least `minGrowth`, and any chosen setup within `within` bars. */
export function matches(scan: Scan, ids: string[], within: number, minGrowth: number | null): boolean {
  if (scan.growth < (minGrowth ?? 0)) return false;
  if (!ids.length) return true;
  return ids.some((id) => { const ago = scan.barsAgo[id as KindId]; return ago != null && ago <= within; });
}

/** The setups on a row within `within` bars, freshest first (OLD ScreenerScanTags, the warning included). */
export function tagsOf(scan: Scan, within: number): { kind: Kind; ago: number }[] {
  return KINDS.flatMap((kind) => { const ago = scan.barsAgo[kind.id]; return ago != null && ago <= within ? [{ kind, ago }] : []; }).sort((a, b) => a.ago - b.ago);
}

/** The studies that draw a row's setups, or the trend's where it has none fresh (OLD setupStudiesOf). */
export function setupStudiesOf(tags: { kind: Kind }[]): string[] {
  const out = [...new Set(tags.flatMap((t) => t.kind.studies))];
  return out.length ? out : KINDS[0]!.studies;
}

/** OLD catalogue ids → this chart's study keys; the two structure readings are the chart's auto overlays. */
const STUDY_KEYS: Record<string, string> = { supertrend: 'supertrend', ema: 'ema', adx: 'adx', donchian: 'donchian', macd: 'macd', rsi: 'rsi', sma: 'ma', bollinger: 'bb', keltner: 'keltner', volumeosc: 'volumeOsc', zigzag: 'zigzag', sr: 'srLevels' };
export function chartStudiesOf(ids: string[]): { keys: string[]; divergence: boolean; harmonic: boolean } {
  return { keys: [...new Set(ids.map((id) => STUDY_KEYS[id]).filter((k): k is string => Boolean(k)))], divergence: ids.includes('divergence'), harmonic: ids.includes('harmonics') };
}

/** The markets in `matched` that were not in the scan last time, sorted (OLD ScanWatch.entrants). */
export function entrants(matched: string[], known: string[]): string[] {
  const k = new Set(known);
  return matched.filter((s) => !k.has(s)).sort();
}
