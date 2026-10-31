// «رصد» — the coach that reads a chart out loud (OLD chart/core RasadCoach.kt, ChartReading.kt and
// the support/resistance and swing studies of Structure.kt, ported formula for formula).
//
// Three sentences, always in this order: what the market is doing, where the prices that matter
// are, and what the chart's own studies make of it. Nothing here calls a model: every sentence is a
// template filled from the same arithmetic that draws the lines, so the same chart always produces
// the same words and none of them can name a level that is not there.
//
// Both languages live here beside their thresholds, as in the Kotlin: the boundary and the word
// belong together. Prices are market figures (Latin digits); counts in Persian prose are Persian.
import { create } from 'zustand';
import { adx, atr, ema } from '../engine/indicators.js';
import { faNum } from '../i18n/time';
import type { Candle } from '../data/types';

// ── ChartReading ─────────────────────────────────────────────────────────────────────────────────
/** Wilder's own threshold for «there is a trend here at all». */
export const TRENDING_FLOOR = 20;
const STRONG = 30;
const MID_VOL = 0.35;
const HIGH_VOL = 0.70;
/** How far the two averages must separate before the market is called: a tenth of a percent. */
const BIAS_EDGE = 0.001;
/** Bars needed before a reading means anything. Below it there is no reading, not a zero. */
const READING_BARS = 60;
const ATR_WINDOW = 60;
const FAST = 20;
const SLOW = 50;
/** ADX runs 0..100 in theory and 0..50 in practice: fifty is the top of the scale a reader sees. */
export const ADX_FULL_SCALE = 50;
/** Half a percent of price between the averages: a wide separation on any instrument. */
export const BIAS_FULL_SCALE = 0.005;

export interface ChartReading {
  /** Wilder's ADX on the last bar. */
  strength: number;
  /** Today's ATR as a percentile of its own recent range, 0..1. */
  volatility: number;
  /** Fast average minus slow, as a fraction of price. Positive is up. */
  bias: number;
}

export const strengthLabel = (r: ChartReading, en = false) =>
  r.strength >= STRONG ? (en ? 'Strong' : 'قوی') : r.strength >= TRENDING_FLOOR ? (en ? 'Moderate' : 'متوسط') : (en ? 'No trend' : 'بدون روند');
export const volatilityLabel = (r: ChartReading, en = false) =>
  r.volatility >= HIGH_VOL ? (en ? 'High' : 'زیاد') : r.volatility >= MID_VOL ? (en ? 'Medium' : 'متوسط') : (en ? 'Low' : 'کم');
export const biasLabel = (r: ChartReading, en = false) =>
  r.bias > BIAS_EDGE ? (en ? 'Bullish' : 'صعودی') : r.bias < -BIAS_EDGE ? (en ? 'Bearish' : 'نزولی') : (en ? 'Neutral' : 'خنثی');
export const isUp = (r: ChartReading) => r.bias > BIAS_EDGE;
export const isDown = (r: ChartReading) => r.bias < -BIAS_EDGE;

/** The three readings of a series, or null when there is not enough of it to tell. */
export function chartReading(bars: Candle[]): ChartReading | null {
  if (bars.length < READING_BARS) return null;
  const h = bars.map((b) => b.h), l = bars.map((b) => b.l), c = bars.map((b) => b.c);
  const last = c.length - 1;
  const strength = adx(h, l, c)[last];
  if (strength == null || !Number.isFinite(strength)) return null;
  const a = atr(h, l, c);
  const values: number[] = [];
  for (let i = Math.max(0, last - ATR_WINDOW + 1); i <= last; i++) { const v = a[i]; if (v != null) values.push(v); }
  if (!values.length) return null;
  const now = values[values.length - 1]!;
  const low = Math.min(...values), high = Math.max(...values);
  const span = high - low;
  const volatility = span <= 0 ? 0.5 : (now - low) / span;
  const fast = ema(c, FAST)[last], slow = ema(c, SLOW)[last];
  if (fast == null || slow == null) return null;
  const price = c[last]!;
  const bias = price === 0 ? 0 : (fast - slow) / Math.abs(price);
  return { strength, volatility, bias };
}

// ── Formatting ───────────────────────────────────────────────────────────────────────────────────
/** OLD ChartFormat.decimalsFor. */
export function decimalsFor(price: number): number {
  const m = Math.abs(price);
  return m >= 1000 ? 1 : m >= 1 ? 2 : m >= 0.01 ? 4 : 6;
}
/** Latin digits, always, at a fixed number of decimals, no grouping. */
export const fixed = (v: number, d: number) => v.toFixed(d);
const prose = (n: number, en: boolean) => (en ? String(n) : faNum(n));

// ── Structure (support/resistance, swings) ───────────────────────────────────────────────────────
/** Support and resistance: swing prices within a tolerance of each other, clustered; clusters of one dropped. */
export function supportResistance(bars: Candle[], lookback = 15, tolerancePercent = 0.1): { price: number; count: number }[] {
  const tolerance = tolerancePercent / 100;
  const prices: number[] = [];
  for (let i = lookback; i < bars.length - lookback; i++) {
    let isHigh = true, isLow = true;
    for (let s = 1; s <= lookback; s++) {
      if (bars[i - s]!.h >= bars[i]!.h || bars[i + s]!.h >= bars[i]!.h) isHigh = false;
      if (bars[i - s]!.l <= bars[i]!.l || bars[i + s]!.l <= bars[i]!.l) isLow = false;
    }
    if (isHigh) prices.push(bars[i]!.h);
    if (isLow) prices.push(bars[i]!.l);
  }
  prices.sort((x, y) => x - y);
  const clusters: { total: number; count: number }[] = [];
  for (const p of prices) {
    const last = clusters[clusters.length - 1];
    if (last && Math.abs(p - last.total / last.count) <= (last.total / last.count) * tolerance) { last.total += p; last.count++; }
    else clusters.push({ total: p, count: 1 });
  }
  return clusters.filter((c) => c.count >= 2).map((c) => ({ price: c.total / c.count, count: c.count }));
}

/** Swing highs and lows: a bar whose high (low) beats `left` bars behind and `right` ahead. */
export function swings(bars: Candle[], left = 5, right = 5): number[] {
  const out: number[] = [];
  for (let i = left; i < bars.length - right; i++) {
    let isHigh = true, isLow = true;
    for (let s = 1; s <= left; s++) { if (bars[i - s]!.h >= bars[i]!.h) isHigh = false; if (bars[i - s]!.l <= bars[i]!.l) isLow = false; }
    for (let s = 1; s <= right; s++) { if (bars[i + s]!.h >= bars[i]!.h) isHigh = false; if (bars[i + s]!.l <= bars[i]!.l) isLow = false; }
    if (isHigh) out.push(bars[i]!.h);
    if (isLow) out.push(bars[i]!.l);
  }
  return out;
}

// ── What the studies say (published by the signal layer) ─────────────────────────────────────────
export type ReadState = 'bull' | 'bear' | 'neutral';
export interface RasadRead { state: ReadState }

/**
 * The studies on the chart, as the signal layer read them: `null` until something publishes them.
 * Null is «not known», not «none»: the third sentence is left out rather than claiming that
 * nothing on the chart has an opinion. The signal layer sets `{ reads, side }` here.
 */
export const useRasadReads = create<{ reads: RasadRead[] | null; side: ReadState | null; set(reads: RasadRead[] | null, side?: ReadState | null): void }>((set) => ({
  reads: null,
  side: null,
  set: (reads, side = null) => set({ reads, side }),
}));

const stateLabel = (s: ReadState, en: boolean) => (s === 'bull' ? (en ? 'Bullish' : 'صعودی') : s === 'bear' ? (en ? 'Bearish' : 'نزولی') : (en ? 'Neutral' : 'خنثی'));

// ── The coach ────────────────────────────────────────────────────────────────────────────────────
/** Below this the arithmetic is noise and so is anything said about it. */
export const MINIMUM_BARS = 30;
const NEAR_FLOOR = 0.002;
const NEAR_CEILING = 0.06;

/**
 * The trend sentence's template matrix. A strong reading with no direction says exactly that; the
 * neutral word never reaches the trending branch because `direction` is null rather than «خنثی».
 */
export function trendLine(trending: boolean, direction: string | null, strength: string, swing: string, en: boolean): string {
  const sw = en ? swing.toLowerCase() : swing;
  const st = en ? strength.toLowerCase() : strength;
  const dir = en ? direction?.toLowerCase() ?? null : direction;
  if (!trending) return en ? `There is no trend here − the market is turning inside a range, with ${sw} swing.` : `اینجا روندی نیست − بازار داخل یک محدوده می‌چرخد، با نوسان ${sw}.`;
  if (dir == null) return en ? `The trend reads ${st}, but which way it is going is not clear yet, with ${sw} swing.` : `روند ${st} خوانده می‌شود، اما هنوز روشن نیست به کدام سمت، با نوسان ${sw}.`;
  return en ? `The market is ${dir} and the trend reads ${st}, with ${sw} swing.` : `بازار ${dir} است و روند ${st} خوانده می‌شود، با نوسان ${sw}.`;
}

function trendSentence(bars: Candle[], en: boolean): string | null {
  const r = chartReading(bars);
  if (!r) return null;
  return trendLine(r.strength >= TRENDING_FLOOR, isUp(r) || isDown(r) ? biasLabel(r, en) : null, strengthLabel(r, en), volatilityLabel(r, en), en);
}

function levelSentence(bars: Candle[], en: boolean): string | null {
  const last = bars[bars.length - 1]?.c;
  if (last == null) return null;
  const d = decimalsFor(last);
  const levels = supportResistance(bars);
  const aboveL = levels.filter((x) => x.price > last).sort((a, b) => a.price - b.price)[0]?.price ?? null;
  const belowL = levels.filter((x) => x.price < last).sort((a, b) => b.price - a.price)[0]?.price ?? null;
  const price = fixed(last, d);
  if (aboveL != null && belowL != null) return en ? `It is at ${price}, between support at ${fixed(belowL, d)} and resistance at ${fixed(aboveL, d)}.` : `روی ${price} است، بین حمایت ${fixed(belowL, d)} و مقاومت ${fixed(aboveL, d)}.`;
  if (aboveL != null) return en ? `It is at ${price}, with the nearest level above it at ${fixed(aboveL, d)}.` : `روی ${price} است و نزدیک‌ترین سطح بالای آن ${fixed(aboveL, d)} است.`;
  if (belowL != null) return en ? `It is at ${price}, with the nearest level below it at ${fixed(belowL, d)}.` : `روی ${price} است و نزدیک‌ترین سطح زیر آن ${fixed(belowL, d)} است.`;
  return en ? `It is at ${price}, with no level it has turned at more than once nearby.` : `روی ${price} است و نزدیکش سطحی نیست که بیش از یک بار از آن برگشته باشد.`;
}

function studySentence(reads: RasadRead[], side: ReadState | null, en: boolean): string {
  const speaking = reads.filter((r) => r.state !== 'neutral');
  if (!speaking.length) return en ? 'Nothing on this chart has an opinion about direction right now.' : 'الان هیچ‌کدام از ابزارهای روی این چارت نظری درباره‌ی جهت ندارند.';
  const bullsN = speaking.filter((r) => r.state === 'bull').length;
  const bearsN = speaking.filter((r) => r.state === 'bear').length;
  const bulls = prose(bullsN, en), bears = prose(bearsN, en), agreeing = prose(speaking.length, en), total = prose(reads.length, en);
  if (bullsN && bearsN) return en ? `Of ${total} studies on the chart, ${bulls} read up and ${bears} read down − they disagree.` : `از ${total} ابزار روی چارت، ${bulls} صعودی و ${bears} نزولی می‌خوانند − با هم موافق نیستند.`;
  const s = stateLabel(side ?? speaking[0]!.state, en);
  return en ? `Of ${total} studies on the chart, ${agreeing} agree and all of them read ${s}.` : `از ${total} ابزار روی چارت، ${agreeing} هم‌نظرند و همه ${s} می‌خوانند.`;
}

/**
 * «این چارت را بخوان»: the trend, the levels and (where the studies' reads are known) what they say.
 * Empty under thirty bars rather than padded with guesses.
 */
export function readChart(bars: Candle[], reads: RasadRead[] | null = null, side: ReadState | null = null, en = false): string[] {
  if (bars.length < MINIMUM_BARS) return [];
  return [trendSentence(bars, en), levelSentence(bars, en), reads ? studySentence(reads, side, en) : null].filter((x): x is string => x != null);
}

export interface AlertSuggestion { price: number; above: boolean; why: string }

/** «یک هشدار پیشنهاد بده»: the nearest respected level 0.2–6% away, a repeated one first, else a swing. */
export function suggestAlert(bars: Candle[], en = false): AlertSuggestion | null {
  if (bars.length < MINIMUM_BARS) return null;
  const last = bars[bars.length - 1]?.c;
  if (last == null || !(last > 0)) return null;
  const d = decimalsFor(last);
  const inRange = (p: number) => { const x = Math.abs(p - last) / last; return x >= NEAR_FLOOR && x <= NEAR_CEILING; };
  const nearest = (xs: number[]) => xs.filter(inRange).sort((a, b) => Math.abs(a - last) - Math.abs(b - last))[0] ?? null;
  const price = nearest(supportResistance(bars).map((x) => x.price)) ?? nearest(swings(bars));
  if (price == null) return null;
  const above = price > last;
  const p = fixed(price, d);
  const why = above
    ? (en ? `The market has turned back from ${p} before. Getting through it is the news.` : `بازار پیش از این از ${p} برگشته. رد شدن از آن، خبر است.`)
    : (en ? `${p} has held the market up before. Losing it is the news.` : `${p} پیش از این بازار را نگه داشته. از دست دادنش، خبر است.`);
  return { price, above, why };
}

/** What a finished trade was, reduced to the facts a review is about (OLD TradeFacts). */
export interface TradeFacts {
  entry: number;
  exit: number;
  stop?: number | null;
  /** Whether there was a stop at all; null where the record does not say. */
  hadStop?: boolean | null;
  closedByStop?: boolean;
  closedEarly?: boolean;
  /** Closed for the reader by the account's stop-out: the plan sentence is left out. */
  forced?: boolean;
  rMultiple?: number | null;
}

/** «معامله‌ی آخرم را مرور کن»: what was risked, what happened to the plan, then the result. */
export function reviewTrade(t: TradeFacts, en = false): string[] {
  const d = decimalsFor(t.entry);
  const hadStop = t.hadStop !== undefined ? t.hadStop : t.stop != null ? true : null;
  let risk: string;
  if (t.stop == null && hadStop == null) {
    risk = en ? `You entered at ${fixed(t.entry, d)} and it closed at ${fixed(t.exit, d)}.` : `روی ${fixed(t.entry, d)} وارد شدید و روی ${fixed(t.exit, d)} بسته شد.`;
  } else if (t.stop == null) {
    risk = en ? 'This one went on without a stop, so there was no price at which the loss was decided.' : 'این یکی بدون حد ضرر باز شد، پس قیمتی نبود که ضرر را در آن تمام کند.';
  } else {
    const r = Math.abs(t.entry - t.stop);
    risk = en ? `You entered at ${fixed(t.entry, d)} risking ${fixed(r, d)} to the stop.` : `روی ${fixed(t.entry, d)} وارد شدید و تا حد ضرر ${fixed(r, d)} ریسک کردید.`;
  }
  let plan: string | null;
  if (hadStop === false) plan = en ? 'Deciding where to be wrong before entering is the one habit that makes the rest measurable.' : 'تصمیم‌گرفتن درباره‌ی جای اشتباه‌بودن، پیش از ورود، تنها عادتی است که بقیه را قابل اندازه‌گیری می‌کند.';
  else if (t.closedByStop) plan = en ? 'The stop did its job and closed it − that is the plan working, not the plan failing.' : 'حد ضرر کارش را کرد و معامله را بست − این یعنی نقشه کار کرد، نه اینکه شکست خورد.';
  else if (t.closedEarly) plan = en ? 'You closed it by hand before either line was reached.' : 'پیش از رسیدن به هر کدام از دو خط، خودتان معامله را بستید.';
  else if (t.forced) plan = null;
  else plan = en ? 'It reached the target you set.' : 'به هدفی که گذاشته بودید رسید.';
  const result = t.rMultiple != null && Number.isFinite(t.rMultiple)
    ? (en ? `The result was ${fixed(t.rMultiple, 1)} times what you risked.` : `نتیجه ${fixed(t.rMultiple, 1)} برابر چیزی بود که ریسک کردید.`)
    : null;
  return [risk, plan, result].filter((x): x is string => x != null);
}

/** The paper book's closed trade as review facts (the web book keeps the stop on every trade). */
export function factsOfPaper(t: { side: 'long' | 'short'; entry: number; exit: number; sl?: number; reason?: string }): TradeFacts {
  const stop = t.sl != null && Number.isFinite(t.sl) ? t.sl : null;
  const risk = stop != null ? Math.abs(t.entry - stop) : 0;
  return {
    entry: t.entry, exit: t.exit, stop, hadStop: stop != null,
    closedByStop: t.reason === 'sl',
    closedEarly: t.reason === 'manual' || t.reason === 'rev' || t.reason === 'reverse',
    forced: t.reason === 'liq',
    rMultiple: risk > 0 ? ((t.exit - t.entry) * (t.side === 'long' ? 1 : -1)) / risk : null,
  };
}

/** The rule that says a sentence disagrees with itself (OLD RasadContradiction). */
export const FLAT = ['خنثی', 'روندی نیست', 'بدون روند', 'no trend', 'No trend', 'Neutral', 'neutral'];
export const TRENDING = ['روند قوی', 'روند متوسط', 'trend reads Strong', 'trend reads Moderate', 'trend reads strong', 'trend reads moderate'];
export function contradiction(sentence: string): [string, string] | null {
  const flat = FLAT.find((x) => sentence.includes(x));
  if (!flat) return null;
  const trend = TRENDING.find((x) => sentence.includes(x));
  return trend ? [flat, trend] : null;
}
