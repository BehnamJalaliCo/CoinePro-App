// The signal layer's arithmetic: what a study is SAYING, as opposed to what it draws, how often it
// has been right here, the chart's one Setup score, which marks go on the candles, which studies
// can rewrite their own past and what the picture is not showing. Pure — no React, no stores — so a
// unit test reaches every number. A port of the OLD Kotlin, rule for rule and word for word:
//   chart/core SignalSpec.kt (the four reading rules, the sixteen sentence shapes, the stop),
//   chart/core ConfidenceEngine.kt (the base rate, horizons 5/10/20, «low data» under 8, the Setup score),
//   chart/core SignalMarkers.kt + feature/chart ChartSignals.kt (six marks a study, labels/triangles/off, thinning),
//   feature/chart RepaintClaim.kt (the four claims) and ChartProvenance.kt (what the chart is not showing).
import type { Candle } from '../data/types';
import type { IndicatorOutput, Values } from '../engine/indicators.js';

// ── What a study says ───────────────────────────────────────────────────────────────────────────

export type MarketState = 'bull' | 'bear' | 'neutral';
export type Side = 'buy' | 'sell';

/** What the state is called, in the language the screen is in (MarketState.label). */
export function stateLabel(state: MarketState, english: boolean): string {
  if (state === 'bull') return english ? 'Bullish' : 'صعودی';
  if (state === 'bear') return english ? 'Bearish' : 'نزولی';
  return english ? 'Neutral' : 'خنثی';
}

/** The word under a mark on the candles (TradeSide.action). */
export const sideAction = (side: Side, english: boolean) => (side === 'buy' ? (english ? 'Buy' : 'خرید') : (english ? 'Sell' : 'فروش'));

/** One bar on which a study said something; `strength` is 0..1, how far past the line it went. */
export interface SignalEvent { bar: number; side: Side; strength: number }

/** The sixteen things a study can say. */
export type NoteShape =
  | 'crossedAbove' | 'crossedBelow' | 'sitsAbove' | 'sitsBelow' | 'leftFloor' | 'leftCeiling' | 'atFloor' | 'atCeiling'
  | 'crossedZeroUp' | 'crossedZeroDown' | 'turnedUp' | 'turnedDown' | 'brokeHigh' | 'brokeLow' | 'reads' | 'quiet';

export interface SignalNote { shape: NoteShape; values: string[] }

/** A sentence, rendered: the phrasing lives here, both languages side by side (NoteShape.render). */
export function noteText(note: SignalNote, english: boolean): string {
  const at = (i: number) => note.values[i] ?? '';
  switch (note.shape) {
    case 'crossedAbove': return english ? `${at(0)} crossed above ${at(1)}` : `${at(0)} از بالای ${at(1)} رد شد`;
    case 'crossedBelow': return english ? `${at(0)} crossed below ${at(1)}` : `${at(0)} از زیر ${at(1)} رد شد`;
    case 'sitsAbove': return english ? `${at(0)} is above ${at(1)}` : `${at(0)} بالای ${at(1)} است`;
    case 'sitsBelow': return english ? `${at(0)} is below ${at(1)}` : `${at(0)} زیر ${at(1)} است`;
    case 'leftFloor': return english ? `${at(0)} came back over ${at(1)}` : `${at(0)} از ${at(1)} برگشت`;
    case 'leftCeiling': return english ? `${at(0)} came back under ${at(1)}` : `${at(0)} از ${at(1)} پایین آمد`;
    case 'atFloor': return english ? `${at(0)} is under ${at(1)}` : `${at(0)} زیر ${at(1)} است`;
    case 'atCeiling': return english ? `${at(0)} is over ${at(1)}` : `${at(0)} بالای ${at(1)} است`;
    case 'crossedZeroUp': return english ? `${at(0)} crossed zero upward` : `${at(0)} از صفر به بالا رد شد`;
    case 'crossedZeroDown': return english ? `${at(0)} crossed zero downward` : `${at(0)} از صفر به پایین رد شد`;
    case 'turnedUp': return english ? `${at(0)} turned up` : `${at(0)} رو به بالا برگشت`;
    case 'turnedDown': return english ? `${at(0)} turned down` : `${at(0)} رو به پایین برگشت`;
    case 'brokeHigh': return english ? `Price broke the ${at(0)}-bar high` : `قیمت سقف ${at(0)} کندل را شکست`;
    case 'brokeLow': return english ? `Price broke the ${at(0)}-bar low` : `قیمت کف ${at(0)} کندل را شکست`;
    case 'reads': return english ? `${at(0)} reads ${at(1)}` : `${at(0)} برابر ${at(1)} است`;
    default: return english ? 'No signal on this bar' : 'روی این کندل سیگنالی نیست';
  }
}

/** Everything one study is saying about one series. `id` is the study's uid on this chart. */
export interface SignalRead { id: string; state: MarketState; events: SignalEvent[]; note: SignalNote; stop: number | null }

export const quietRead = (id: string): SignalRead => ({ id, state: 'neutral', events: [], note: { shape: 'quiet', values: [] }, stop: null });

/** Every left-to-right run (a figure, a percentage) isolated inside the prose (BidiText.isolateNumbers). */
export function isolateNumbers(text: string): string {
  return text.replace(/[-+−]?\d[\d,.]*%?/g, (m) => `⁦${m}⁩`);
}

// ── The series the rules read ───────────────────────────────────────────────────────────────────

/** Bars as columns, with the average range every rule shares (Wilder's ATR, 14). */
export interface Series { bars: Candle[]; close: number[]; high: number[]; low: number[]; atr: (number | null)[] }

export function makeSeries(bars: Candle[]): Series {
  const close = bars.map((b) => b.c); const high = bars.map((b) => b.h); const low = bars.map((b) => b.l);
  return { bars, close, high, low, atr: wilderAtr(high, low, close, 14) };
}

/** Indicators.atr: Wilder's smoothing of the true range, seeded by its first `period` mean. */
export function wilderAtr(high: number[], low: number[], close: number[], period = 14): (number | null)[] {
  const n = close.length;
  const out: (number | null)[] = new Array(n).fill(null);
  const tr = close.map((_, i) => (i === 0 ? high[i]! - low[i]! : Math.max(high[i]! - low[i]!, Math.abs(high[i]! - close[i - 1]!), Math.abs(low[i]! - close[i - 1]!))));
  let prev = 0;
  for (let i = period - 1; i < n; i++) {
    if (i === period - 1) { let s = 0; for (let k = 0; k < period; k++) s += tr[i - k]!; prev = s / period; }
    else prev = (prev * (period - 1) + tr[i]!) / period;
    out[i] = prev;
  }
  return out;
}

const val = (line: Values | null | undefined, i: number): number | null => {
  const v = line?.[i];
  return v == null || !Number.isFinite(v) ? null : v;
};

// ── The reading rules (SignalSpec) ──────────────────────────────────────────────────────────────

/** Below this there is not enough history for any of it to mean anything. */
export const MIN_BARS = 20;
/** How far back a stop looks for the swing it sits behind, and how much ATR air it keeps. */
export const STOP_LOOKBACK = 10;
export const STOP_AIR = 0.25;
/** A cross this far past the line, as a fraction of price, is a strength of one. */
const CROSS_FULL = 0.01;
/** The dead band around a bounded oscillator's midpoint, as a fraction of its span, on each side. */
export const NEUTRAL_BAND = 0.25;
/** How near a reference line the close must be to count as sitting ON it, in average ranges. */
export const TOUCHING_ATR = 0.15;

/** The bounded oscillators and the floor and ceiling each is read against (web ids). */
export const OSCILLATOR_BOUNDS: Record<string, [number, number]> = {
  rsi: [30, 70], stoch: [20, 80], stochrsi: [20, 80], cci: [-100, 100], woodiesCci: [-100, 100], willr: [-80, -20],
  mfi: [20, 80], uo: [30, 70],
  // The web draws Connors RSI's guides at 20 and 80; the sentence reads the line the reader sees.
  crsi: [20, 80],
  smi: [-40, 40], bbpercent: [0, 1], fisher: [-2, 2], aroonOsc: [-50, 50], mtfRsi: [30, 70], stc: [25, 75], rviVol: [20, 80],
};

/** The studies whose reading has no direction in it. They report and never fire. */
export const MEASURES = new Set([
  'atr', 'adx', 'choppiness', 'stddev', 'hv', 'chaikinVol', 'bbw', 'bbWidth', 'massIndex', 'correlation', 'relVolAtTime',
  'adr', 'ulcer', 'pvi', 'nvi', 'volumeOsc', 'vol24h',
]);

/** The studies that flip sides — their line is a trailing stop. */
export const FLIPPING = new Set(['supertrend', 'psar', 'volatilityStop', 'chandeKroll', 'chandelier']);

const priceWord = (en: boolean) => (en ? 'Price' : 'قیمت');
const signalWord = (en: boolean) => (en ? 'its signal line' : 'خط سیگنالش');
const channelWord = (en: boolean) => (en ? 'the channel' : 'کانال');

export function figure(v: number): string {
  const a = Math.abs(v);
  return a >= 1000 ? v.toFixed(0) : v.toFixed(a >= 10 ? 1 : 2);
}

/** A bounded oscillator's state, with the dead band round its middle (SignalSpec.oscillatorState). */
export function oscillatorState(level: number | null, [floor, ceiling]: [number, number]): MarketState {
  if (level == null) return 'neutral';
  const middle = (floor + ceiling) / 2;
  const band = (ceiling - floor) * NEUTRAL_BAND;
  if (level > middle + band) return 'bull';
  if (level < middle - band) return 'bear';
  return 'neutral';
}

/** A close against a reference line: within 0.15 ATR is sitting on it (SignalSpec.referenceState). */
export function referenceState(close: number, level: number | null, range: number): MarketState {
  if (level == null) return 'neutral';
  const touching = Number.isFinite(range) && range > 0 ? range * TOUCHING_ATR : 0;
  if (close > level + touching) return 'bull';
  if (close < level - touching) return 'bear';
  return 'neutral';
}

/** Where this reading is wrong, in price: the swing it came out of, a quarter-ATR beyond it. */
export function defaultStop(s: Series, event: SignalEvent, at: number = s.bars.length - 1): number | null {
  const n = s.bars.length;
  if (!n) return null;
  const from = Math.max(0, event.bar - STOP_LOOKBACK);
  const until = Math.min(n, event.bar + 1);
  if (from >= until) return null;
  let low = Infinity; let high = -Infinity;
  for (let i = from; i < until; i++) { low = Math.min(low, s.low[i]!); high = Math.max(high, s.high[i]!); }
  const range = s.atr[Math.min(at, n - 1)] ?? 0;
  const air = range * STOP_AIR;
  return event.side === 'buy' ? low - air : high + air;
}

const crossStrength = (close: number, level: number) => (level === 0 ? 1 : Math.max(0, Math.min(1, Math.abs(close - level) / Math.abs(level) / CROSS_FULL)));
function zoneStrength(before: number, floor: number, ceiling: number): number {
  const width = ceiling - floor;
  if (width <= 0) return 1;
  const depth = before <= floor ? floor - before : before - ceiling;
  return Math.max(0, Math.min(1, depth / width));
}
const firesLast = (events: SignalEvent[], last: number) => events[events.length - 1]?.bar === last;

/** A reference line on the price: the close crossing it is the event. */
export function reference(id: string, name: string, line: Values, s: Series, en: boolean): SignalRead {
  const events: SignalEvent[] = [];
  for (let bar = 1; bar < s.bars.length; bar++) {
    const now = val(line, bar); const before = val(line, bar - 1);
    if (now == null || before == null) continue;
    const was = s.close[bar - 1]!; const nowClose = s.close[bar]!;
    if (was <= before && nowClose > now) events.push({ bar, side: 'buy', strength: crossStrength(nowClose, now) });
    else if (was >= before && nowClose < now) events.push({ bar, side: 'sell', strength: crossStrength(nowClose, now) });
  }
  const last = s.bars.length - 1;
  const level = val(line, last);
  const state = referenceState(s.close[last]!, level, s.atr[last] ?? 0);
  const fired = firesLast(events, last);
  let note: SignalNote;
  if (fired && state === 'bull') note = { shape: 'crossedAbove', values: [priceWord(en), name] };
  else if (fired && state === 'bear') note = { shape: 'crossedBelow', values: [priceWord(en), name] };
  else if (state === 'bull') note = { shape: 'sitsAbove', values: [priceWord(en), name] };
  else if (state === 'bear') note = { shape: 'sitsBelow', values: [priceWord(en), name] };
  else if (level != null) note = { shape: 'reads', values: [name, figure(level)] };
  else note = { shape: 'quiet', values: [] };
  return { id, state, events, note, stop: level };
}

/** A bounded oscillator: leaving the floor or the ceiling is the event. */
export function bounded(id: string, name: string, line: Values, bounds: [number, number], s: Series): SignalRead {
  const [floor, ceiling] = bounds;
  const events: SignalEvent[] = [];
  for (let bar = 1; bar < s.bars.length; bar++) {
    const now = val(line, bar); const before = val(line, bar - 1);
    if (now == null || before == null) continue;
    if (before <= floor && now > floor) events.push({ bar, side: 'buy', strength: zoneStrength(before, floor, ceiling) });
    if (before >= ceiling && now < ceiling) events.push({ bar, side: 'sell', strength: zoneStrength(before, floor, ceiling) });
  }
  const last = s.bars.length - 1;
  const level = val(line, last);
  const state = oscillatorState(level, bounds);
  const lastEvent = events[events.length - 1];
  let note: SignalNote;
  if (level == null) note = { shape: 'quiet', values: [] };
  else if (lastEvent?.bar === last && lastEvent.side === 'buy') note = { shape: 'leftFloor', values: [name, figure(floor)] };
  else if (lastEvent?.bar === last) note = { shape: 'leftCeiling', values: [name, figure(ceiling)] };
  else if (level <= floor) note = { shape: 'atFloor', values: [name, figure(floor)] };
  else if (level >= ceiling) note = { shape: 'atCeiling', values: [name, figure(ceiling)] };
  else note = { shape: 'reads', values: [name, figure(level)] };
  return { id, state, events, note, stop: null };
}

/** An unbounded oscillator: zero, or its own signal line where it has one. */
export function zeroCross(id: string, name: string, line: Values, s: Series, signal: Values | null, en: boolean): SignalRead {
  const events: SignalEvent[] = [];
  for (let bar = 1; bar < s.bars.length; bar++) {
    const now = val(line, bar); const before = val(line, bar - 1);
    if (now == null || before == null) continue;
    const nowRef = signal ? val(signal, bar) ?? 0 : 0;
    const beforeRef = signal ? val(signal, bar - 1) ?? 0 : 0;
    if (before <= beforeRef && now > nowRef) events.push({ bar, side: 'buy', strength: 1 });
    if (before >= beforeRef && now < nowRef) events.push({ bar, side: 'sell', strength: 1 });
  }
  const last = s.bars.length - 1;
  const level = val(line, last);
  const ref = signal ? val(signal, last) ?? 0 : 0;
  const state: MarketState = level == null ? 'neutral' : level > ref ? 'bull' : level < ref ? 'bear' : 'neutral';
  const fired = firesLast(events, last);
  let note: SignalNote;
  if (level == null) note = { shape: 'quiet', values: [] };
  else if (fired && state === 'bull' && !signal) note = { shape: 'crossedZeroUp', values: [name] };
  else if (fired && !signal) note = { shape: 'crossedZeroDown', values: [name] };
  else if (fired && state === 'bull') note = { shape: 'crossedAbove', values: [name, signalWord(en)] };
  else if (fired) note = { shape: 'crossedBelow', values: [name, signalWord(en)] };
  else if (state === 'bull' && !signal) note = { shape: 'atCeiling', values: [name, '0'] };
  else if (state === 'bear' && !signal) note = { shape: 'atFloor', values: [name, '0'] };
  else if (state === 'bull') note = { shape: 'sitsAbove', values: [name, signalWord(en)] };
  else note = { shape: 'sitsBelow', values: [name, signalWord(en)] };
  return { id, state, events, note, stop: null };
}

/** A study that flips sides — SuperTrend, parabolic SAR, a volatility stop. The stop IS the study. */
export function flipping(id: string, name: string, line: Values, s: Series, en: boolean): SignalRead {
  const events: SignalEvent[] = [];
  for (let bar = 1; bar < s.bars.length; bar++) {
    const now = val(line, bar); const before = val(line, bar - 1);
    if (now == null || before == null) continue;
    const wasBelow = s.close[bar - 1]! < before;
    const isBelow = s.close[bar]! < now;
    if (wasBelow && !isBelow) events.push({ bar, side: 'buy', strength: 1 });
    if (!wasBelow && isBelow) events.push({ bar, side: 'sell', strength: 1 });
  }
  const last = s.bars.length - 1;
  const level = val(line, last);
  const state: MarketState = level == null ? 'neutral' : s.close[last]! > level ? 'bull' : 'bear';
  const fired = firesLast(events, last);
  const note: SignalNote = fired && state === 'bull' ? { shape: 'turnedUp', values: [name] }
    : fired ? { shape: 'turnedDown', values: [name] }
      : state === 'bull' ? { shape: 'sitsAbove', values: [priceWord(en), name] }
        : { shape: 'sitsBelow', values: [priceWord(en), name] };
  return { id, state, events, note, stop: level };
}

/** Donchian: the channel is a breakout, not a mean to revert to. */
export function breakout(id: string, s: Series, period: number, en: boolean): SignalRead {
  const n = s.bars.length;
  const upper: (number | null)[] = new Array(n).fill(null);
  const lower: (number | null)[] = new Array(n).fill(null);
  for (let i = period - 1; i < n; i++) {
    let hi = -Infinity; let lo = Infinity;
    for (let k = i - period + 1; k <= i; k++) { hi = Math.max(hi, s.high[k]!); lo = Math.min(lo, s.low[k]!); }
    upper[i] = hi; lower[i] = lo;
  }
  const events: SignalEvent[] = [];
  for (let bar = 1; bar < n; bar++) {
    const roof = upper[bar - 1]; const floor = lower[bar - 1];
    if (roof == null || floor == null) continue;
    if (s.close[bar]! > roof) events.push({ bar, side: 'buy', strength: 1 });
    if (s.close[bar]! < floor) events.push({ bar, side: 'sell', strength: 1 });
  }
  const last = n - 1;
  const basis = upper[last] != null && lower[last] != null ? (upper[last]! + lower[last]!) / 2 : null;
  const close = s.close[last]!;
  const state: MarketState = basis == null ? 'neutral' : close > basis ? 'bull' : close < basis ? 'bear' : 'neutral';
  const lastEvent = events[events.length - 1];
  let note: SignalNote;
  if (lastEvent?.bar === last && lastEvent.side === 'buy') note = { shape: 'brokeHigh', values: [String(period)] };
  else if (lastEvent?.bar === last) note = { shape: 'brokeLow', values: [String(period)] };
  else if (state === 'bull') note = { shape: 'sitsAbove', values: [priceWord(en), channelWord(en)] };
  else if (state === 'bear') note = { shape: 'sitsBelow', values: [priceWord(en), channelWord(en)] };
  else if (basis != null) note = { shape: 'reads', values: [channelWord(en), figure(basis)] };
  else note = { shape: 'quiet', values: [] };
  const stop = lastEvent?.side === 'buy' ? lower[last] : upper[last];
  return { id, state, events, note, stop: stop ?? null };
}

/** A measurement: it reports, it never fires. Only ATR, ADX and choppiness have a reading. */
function measure(id: string, key: string, name: string, line: Values | null, s: Series): SignalRead {
  const level = ['atr', 'adx', 'choppiness'].includes(key) ? val(line, s.bars.length - 1) : null;
  return { id, state: 'neutral', events: [], note: level == null ? { shape: 'quiet', values: [] } : { shape: 'reads', values: [name, figure(level)] }, stop: null };
}

/** The line a study is read by on the price: a band's basis, else its first drawn line. */
export function priceLineOf(out: IndicatorOutput): Values | null {
  if (out.basis) return out.basis;
  const lines = out.lines ?? [];
  if (out.cloud && lines.length === 3) return lines[1]!.data;
  return out.line ?? lines[0]?.data ?? null;
}

/** A pane study's first line, and its second (MACD's signal, Stochastic's %D) where it has one. */
export function paneLinesOf(out: IndicatorOutput): { first: Values | null; second: Values | null } {
  if (out.line) return { first: out.line, second: out.signal ?? null };
  if (out.hist) return { first: out.hist, second: null };
  if (out.hists?.length) return { first: out.hists[0]!.data, second: null };
  const lines = out.lines ?? [];
  return { first: lines[0]?.data ?? null, second: lines[1]?.data ?? null };
}

/** SuperTrend draws two half-lines; reading it needs them back as one series. */
export function trailingOf(out: IndicatorOutput, n: number): Values | null {
  const lines = out.lines ?? [];
  if (!lines.length) return out.line ?? null;
  if (lines.length === 1) return lines[0]!.data;
  return Array.from({ length: n }, (_, i) => { for (const l of lines) { const v = val(l.data, i); if (v != null) return v; } return null; });
}

export interface ReadInput {
  /** The study's uid on this chart. */
  id: string;
  /** Its registry key (rsi, macd…). */
  key: string;
  /** What a pill calls it — RSI, MACD, BB: the same in both languages. */
  name: string;
  /** Over the price or in a pane of its own. */
  overlay: boolean;
  /** A study that draws structure rather than a value per bar (levels, patterns, profiles). */
  structure?: boolean;
  /** Its lookback, for Donchian's «N-bar high». */
  period?: number;
}

/** What one study is saying about the series, by the rule its family implies (SignalSpec.read). */
export function readStudy(input: ReadInput, out: IndicatorOutput | null, s: Series, en: boolean): SignalRead {
  const { id, key, name } = input;
  if (!out || s.bars.length < MIN_BARS || input.structure) return quietRead(id);
  const last = s.bars.length - 1;
  let read: SignalRead;
  if (MEASURES.has(key)) read = measure(id, key, name, out.line ?? null, s);
  else if (OSCILLATOR_BOUNDS[key]) {
    const line = out.line ?? paneLinesOf(out).first;
    if (!line) return quietRead(id);
    read = bounded(id, name, line, OSCILLATOR_BOUNDS[key]!, s);
  } else if (FLIPPING.has(key)) {
    const line = trailingOf(out, s.bars.length);
    if (!line) return quietRead(id);
    read = flipping(id, name, line, s, en);
  } else if (key === 'donchian') read = breakout(id, s, Math.max(2, Math.round(input.period ?? 20)), en);
  else if (input.overlay) {
    const line = priceLineOf(out);
    if (!line) return quietRead(id);
    read = reference(id, name, line, s, en);
  } else {
    const { first, second } = paneLinesOf(out);
    if (!first) return quietRead(id);
    read = zeroCross(id, name, first, s, second, en);
  }
  // The stop is the same question for every study — where is this reading wrong.
  if (read.stop != null || !read.events.length) return read;
  return { ...read, stop: defaultStop(s, read.events[read.events.length - 1]!, last) };
}

/** A line nothing in the catalogue owns — a reader's script plot — read by the same rules (readLine). */
export function readLine(id: string, name: string, line: Values, s: Series, onPrice: boolean, en: boolean): SignalRead {
  if (s.bars.length < MIN_BARS) return quietRead(id);
  const read = onPrice ? reference(id, name, line, s, en) : zeroCross(id, name, line, s, null, en);
  if (read.stop != null || !read.events.length) return read;
  return { ...read, stop: defaultStop(s, read.events[read.events.length - 1]!) };
}

/** A script's own long/short signals as one read: the newest verdict is what it is saying (fromVerdicts). */
export function readVerdicts(id: string, name: string, long: number[], short: number[], s: Series): SignalRead {
  const events: SignalEvent[] = [
    ...long.map((bar) => ({ bar, side: 'buy' as const, strength: 1 })),
    ...short.map((bar) => ({ bar, side: 'sell' as const, strength: 1 })),
  ].filter((e) => e.bar >= 0 && e.bar < s.bars.length).sort((a, b) => a.bar - b.bar);
  const newest = events[events.length - 1];
  if (!newest) return quietRead(id);
  return {
    id, state: newest.side === 'buy' ? 'bull' : 'bear', events,
    note: { shape: newest.side === 'buy' ? 'turnedUp' : 'turnedDown', values: [name] },
    stop: defaultStop(s, newest),
  };
}

/** Whether a read says anything at all (a quiet one keeps its legend row and stays out of the strip). */
export const isSilent = (r: SignalRead) => r.state === 'neutral' && !r.events.length && r.note.shape === 'quiet';

// ── How it has done here (ConfidenceEngine) ─────────────────────────────────────────────────────

/** The three horizons a reader can ask for, in bars, and the default. */
export const HORIZONS = [5, 10, 20] as const;
export const DEFAULT_HORIZON = 10;
/** How much history is walked. */
export const WINDOW = 500;
/** Below eight samples the app prints «low data» instead of a percentage. */
export const THIN = 8;
/** How many of the newest outcomes a card shows as dots. */
export const RECENT_DOTS = 5;
/** The sample count at which a record carries its full weight in the Setup score. */
export const FULL_WEIGHT_SAMPLES = 30;
/** The ceiling for a chart on which no contributor beats an even coin. */
export const CONFIDENCE_CAP = 85;
const EVEN_COIN = 0.5;

export interface ConfidenceReport {
  id: string;
  /** How many completed signals were measured. */
  samples: number;
  /** The share that reached the horizon in profit, 0..1. */
  winRate: number;
  /** The average result in units of risk. */
  averageR: number;
  /** The newest five outcomes, newest last. */
  recent: boolean[];
  horizon: number;
}

export const trustworthy = (r: ConfidenceReport) => r.samples >= THIN;
/** The percentage as a whole number for a pill; zero when there is too little to print one. */
export const percentOf = (r: ConfidenceReport) => (trustworthy(r) ? Math.round(r.winRate * 100) : 0);
export const emptyReport = (id: string, horizon = DEFAULT_HORIZON): ConfidenceReport => ({ id, samples: 0, winRate: 0, averageR: 0, recent: [], horizon });

/** The nearest of the three horizons to any number of bars. */
export const snapHorizon = (bars: number) => HORIZONS.reduce((best, h) => (Math.abs(h - bars) < Math.abs(best - bars) ? h : best), HORIZONS[0] as number);

/**
 * The base rate for one study's signals: each completed one measured the way a trade is — the
 * study's own stop checked bar by bar before the horizon (a loss of one R), else the move at the
 * horizon in units of that R. A signal whose horizon has not passed is not an outcome.
 */
export function measureRead(read: SignalRead, s: Series, horizon: number = DEFAULT_HORIZON, stopFor: (e: SignalEvent) => number | null = (e) => defaultStop(s, e)): ConfidenceReport {
  const n = s.bars.length;
  if (!n || !read.events.length) return emptyReport(read.id, horizon);
  const first = Math.max(0, n - WINDOW);
  const outcomes: number[] = []; const wins: boolean[] = [];
  for (const event of read.events) {
    if (event.bar < first) continue;
    const settle = event.bar + horizon;
    if (settle >= n) continue;
    const entry = s.close[event.bar]!;
    const stop = stopFor(event);
    const risk = stop == null ? null : Math.abs(entry - stop) > 0 ? Math.abs(entry - stop) : null;
    const long = event.side === 'buy';
    let result: number | null = null;
    if (risk != null && stop != null) {
      for (let bar = event.bar + 1; bar <= settle; bar++) {
        const hit = long ? s.low[bar]! <= stop : s.high[bar]! >= stop;
        if (hit) { result = -1; break; }
      }
    }
    if (result == null) {
      const move = (s.close[settle]! - entry) * (long ? 1 : -1);
      result = risk != null ? move / risk : move / entry;
    }
    outcomes.push(result); wins.push(result > 0);
  }
  if (!outcomes.length) return emptyReport(read.id, horizon);
  return {
    id: read.id, samples: outcomes.length, winRate: wins.filter(Boolean).length / outcomes.length,
    averageR: outcomes.reduce((a, b) => a + b, 0) / outcomes.length, recent: wins.slice(-RECENT_DOTS), horizon,
  };
}

/** The header's one number: confidence, not consensus. */
export interface SetupScore {
  score: number;
  side: MarketState;
  signals: number;
  /** How many studies had an opinion at all. */
  studies: number;
  /** The mean win rate of the studies that had a record, or null where none did. */
  winRate: number | null;
}

/**
 * `score = |Σ(direction × winRate × w)| / Σw` as a percentage, `w = min(1, samples / 30)`: a study
 * cannot score above its own record, a thin record counts for less, no record no vote, and a chart
 * of losing studies is held under 85 (ConfidenceEngine.setupScore).
 */
export function setupScore(reads: SignalRead[], reports: Record<string, ConfidenceReport>): SetupScore {
  const voting = reads.filter((r) => r.state !== 'neutral');
  const signals = reads.reduce((a, r) => a + r.events.length, 0);
  if (!voting.length) return { score: 0, side: 'neutral', signals, studies: 0, winRate: null };
  let sum = 0; let weights = 0; let rates = 0; let measured = 0;
  for (const read of voting) {
    const report = reports[read.id];
    const samples = report?.samples ?? 0;
    const weight = Math.min(1, samples / FULL_WEIGHT_SAMPLES);
    if (weight <= 0 || !report) continue;
    const direction = read.state === 'bull' ? 1 : -1;
    sum += direction * report.winRate * weight;
    weights += weight; rates += report.winRate; measured++;
  }
  if (weights <= 0) {
    const bulls = voting.filter((r) => r.state === 'bull').length;
    const side: MarketState = bulls * 2 > voting.length ? 'bull' : bulls * 2 < voting.length ? 'bear' : 'neutral';
    return { score: 0, side, signals, studies: voting.length, winRate: null };
  }
  const side: MarketState = sum > 0 ? 'bull' : sum < 0 ? 'bear' : 'neutral';
  const average = rates / measured;
  const raw = Math.round(Math.max(0, Math.min(1, Math.abs(sum) / weights)) * 100);
  const allLosing = measured > 0 && voting.every((r) => { const rep = reports[r.id]; return rep ? rep.samples === 0 || rep.winRate < EVEN_COIN : true; });
  return { score: allLosing ? Math.min(raw, CONFIDENCE_CAP) : raw, side, signals, studies: voting.length, winRate: average };
}

/** Average R as a signed figure, two decimals (ExplainSheet.formatR). */
export function formatR(v: number): string {
  const rounded = Math.round(v * 100) / 100;
  return `${rounded > 0 ? '+' : ''}${rounded.toFixed(2)}`;
}

// ── Marks on the candles (ChartSignalEngine.markersFor, SignalMarkers) ──────────────────────────

export type MarkerStyle = 'labels' | 'triangles' | 'off';
export const MARKER_STYLES: MarkerStyle[] = ['labels', 'triangles', 'off'];
/** The newest marks only: a study that fired forty times would put forty triangles on the chart. */
export const MARKERS_PER_STUDY = 6;
/** At or above this many px a bar a mark carries its word; below the lower one the marks thin out. */
export const LABEL_SPACING = 12;
export const TRIANGLE_SPACING = 6;
/** How many bars a thinned mark speaks for. */
export const THIN_WINDOW = 10;
/** The glyph's size for a weak, an ordinary and a strong reading, and the two cuts. */
export const SIZE_WEAK = 6; export const SIZE_MEDIUM = 8; export const SIZE_STRONG = 10;
export const WEAK = 0.34; export const STRONG = 0.67;

export interface SignalMark { id: string; time: number; bar: number; price: number; above: boolean; colour: string; side: Side; label: string | null; strength: number }

export function markersFor(reads: SignalRead[], bars: Candle[], opts: { hidden?: Set<string>; styles?: Record<string, MarkerStyle>; colours?: Record<string, string>; english: boolean }): SignalMark[] {
  const out: SignalMark[] = [];
  for (const read of reads) {
    if (opts.hidden?.has(read.id)) continue;
    const style = opts.styles?.[read.id] ?? 'labels';
    if (style === 'off') continue;
    for (const e of read.events.slice(-MARKERS_PER_STUDY)) {
      const b = bars[e.bar];
      if (!b) continue;
      const buy = e.side === 'buy';
      out.push({ id: read.id, time: b.t, bar: e.bar, price: buy ? b.l : b.h, above: !buy, colour: opts.colours?.[read.id] ?? SCRIPT_MARK_COLOUR, side: e.side, label: style === 'labels' ? sideAction(e.side, opts.english) : null, strength: e.strength });
    }
  }
  return out;
}
/** Gold, for a script's marks: a script is the reader's own, like their drawings. */
export const SCRIPT_MARK_COLOUR = '#D8A848';

export type MarkerDetail = 'label' | 'triangle' | 'thinned' | 'hidden';
export function detailFor(barSpacing: number, style: MarkerStyle = 'labels'): MarkerDetail {
  if (style === 'off') return 'hidden';
  if (barSpacing >= LABEL_SPACING && style === 'labels') return 'label';
  if (barSpacing >= TRIANGLE_SPACING) return 'triangle';
  return 'thinned';
}
export function sizeFor(strength: number): number {
  if (!Number.isFinite(strength)) return SIZE_MEDIUM;
  return strength < WEAK ? SIZE_WEAK : strength < STRONG ? SIZE_MEDIUM : SIZE_STRONG;
}
/** One mark per window of bars and direction — the strongest, ties to the newer. */
export function thinMarks(marks: SignalMark[], window: number = THIN_WINDOW): SignalMark[] {
  if (marks.length < 2 || window < 2) return marks;
  const strongest = new Map<number, SignalMark>();
  for (const m of marks) {
    const key = Math.floor(m.bar / window) * 2 + (m.above ? 1 : 0);
    const held = strongest.get(key);
    if (!held || m.strength >= held.strength) strongest.set(key, m);
  }
  return [...strongest.values()];
}

// ── Whether a thing drawn can change after it is drawn (RepaintClaim) ───────────────────────────

export type RepaintClaim = 'settled' | 'late' | 'liveBar' | 'repaints';

/** Every claim this build is prepared to make, by web study key; no entry draws no mark. */
export const REPAINT_CLAIMS: Record<string, RepaintClaim> = {
  pivots: 'settled', fractals: 'late', pivotHL: 'late', srLevels: 'late', supplyDemand: 'late',
  zigzag: 'repaints', autoFib: 'repaints', chopZone: 'liveBar',
};
/** The chart's own auto tools, as the same claims (swings, harmonics, divergence, gaps). */
export const AUTO_CLAIMS: Record<'divergence' | 'harmonic' | 'gaps', RepaintClaim> = { divergence: 'late', harmonic: 'repaints', gaps: 'settled' };

/**
 * The claim for one study as this chart computes it. A study on a higher timeframe of its own that
 * does not wait for that timeframe's bar to close writes the forming bar's value over the bars it
 * already drew — that is a rewrite, whatever the study is.
 */
export function repaintOf(key: string, opts: { ownTimeframe?: boolean; waitClose?: boolean } = {}): RepaintClaim | null {
  if (opts.ownTimeframe && opts.waitClose === false) return 'repaints';
  return REPAINT_CLAIMS[key] ?? null;
}

export function repaintLabel(c: RepaintClaim, english: boolean): string {
  switch (c) {
    case 'settled': return english ? 'Does not repaint' : 'repaint نمی‌کند';
    case 'late': return english ? 'Settles a few bars later' : 'با تأخیر قطعی می‌شود';
    case 'liveBar': return english ? 'Moves until the candle closes' : 'تا بسته‌شدن کندل تغییر می‌کند';
    default: return english ? 'Rewrites what is behind it' : 'عقب‌تر دوباره نوشته می‌شود';
  }
}
export function repaintNote(c: RepaintClaim, english: boolean): string {
  switch (c) {
    case 'settled': return english ? 'Worked out from closed candles only. What is drawn does not move.' : 'فقط از کندل‌های بسته حساب می‌شود. آنچه رسم شده، جابه‌جا نمی‌شود.';
    case 'late': return english ? 'Each mark settles a few candles later. The newest one may be taken away before then.' : 'هر نشانه چند کندل بعد قطعی می‌شود. تازه‌ترین نشانه تا آن موقع ممکن است برداشته شود.';
    case 'liveBar': return english ? 'The last candle\'s value moves until it closes. Everything before it is fixed.' : 'مقدارِ آخرین کندل تا بسته‌شدنش حرکت می‌کند. مقدارهای قبلی ثابت‌اند.';
    default: return english ? 'As new candles arrive, part of what was already drawn is written again. Do not rely on it for a backtest.' : 'با آمدن کندل‌های تازه، بخشی از آنچه قبلاً رسم شده دوباره نوشته می‌شود. برای بک‌تست به آن تکیه نکنید.';
  }
}
export const isTrustworthyClaim = (c: RepaintClaim) => c === 'settled' || c === 'late';

// ── What this chart is not showing (ChartProvenance) ────────────────────────────────────────────

export type Exclusion =
  | { kind: 'folded'; source: string }
  | { kind: 'noVolume' }
  | { kind: 'replay' }
  | { kind: 'repainting'; names: string[] };

export interface ProvenanceInput {
  /** The native timeframe the bars are built from on this device, or null when the venue sends them as drawn. */
  foldedFrom: string | null;
  /** The bars on the chart (no volume column when every bar's volume is zero). */
  bars: Candle[];
  replaying: boolean;
  /** The names of what is switched on that rewrites its own past. */
  repainting: string[];
}

/** Every way the picture legitimately differs from the venue's own, most noticeable first. */
export function chartExclusions(p: ProvenanceInput): Exclusion[] {
  const out: Exclusion[] = [];
  if (p.foldedFrom) out.push({ kind: 'folded', source: p.foldedFrom });
  if (p.bars.length && !p.bars.some((b) => b.v > 0)) out.push({ kind: 'noVolume' });
  if (p.replaying) out.push({ kind: 'replay' });
  if (p.repainting.length) out.push({ kind: 'repainting', names: p.repainting });
  return out;
}

export function exclusionText(x: Exclusion, english: boolean): string {
  switch (x.kind) {
    // The web folds its buckets on UTC boundaries (data/candles.ts bucketOf), and the sentence says so.
    case 'folded': return english
      ? `Candles at this bar length are built on this device from ${x.source} candles, counted from midnight UTC.`
      : `کندل‌های این بازه روی همین دستگاه از کندل‌های ${x.source} ساخته می‌شوند و از نیمه‌شب UTC شمرده می‌شوند.`;
    case 'noVolume': return english
      ? 'This feed sends no volume. Volume indicators and tools are not drawn on this symbol.'
      : 'این فید حجم نمی‌فرستد. اندیکاتورها و ابزارهای حجمی روی این نماد نمایش داده نمی‌شوند.';
    case 'replay': return english
      ? 'Replay is on. Candles after the replay point are deliberately not shown.'
      : 'بازپخش روشن است. کندل‌های بعد از نقطه‌ی بازپخش عمداً نشان داده نمی‌شوند.';
    default: return `${x.names.join(english ? ', ' : '، ')}: ${repaintNote('repaints', english)}`;
  }
}

/** The exclusions as one line under the chart, or '' when there is nothing to say. */
export function exclusionsLine(list: Exclusion[], english: boolean): string {
  if (!list.length) return '';
  const body = list.map((x) => exclusionText(x, english)).join(' ');
  return english ? `What is not in this picture — ${body}` : `آنچه در این تصویر نیست — ${body}`;
}
