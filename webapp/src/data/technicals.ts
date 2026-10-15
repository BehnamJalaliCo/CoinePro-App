// TradingView's "Technicals" table: every oscillator and moving average the rating counts, with its
// value and the action it votes for (buy, sell, neutral), by the same rules as engine/techRating.js.
// Also the outputs of any catalogue indicator, for alerts on an indicator.
import * as ind from '../engine/indicators.js';
import { REGISTRY, type IndicatorColumns, type IndicatorOutput } from '../engine/indicators.js';
import type { Candle } from './types';

export type Action = 1 | 0 | -1;
export interface TechRow { id: string; name: string; value: number | null; action: Action }

type Arr = (number | null)[];
// The vendored helpers the typings do not list.
const X = ind as unknown as {
  sma(s: number[], p: number): Arr; ema(s: number[], p: number): Arr; rsi(c: number[], p: number): Arr; hma(s: number[], p: number): Arr;
  stoch(h: number[], l: number[], c: number[], p: number, d: number): { k: Arr; d: Arr };
  stochRsi(c: number[], p: number, k: number, d: number): { k: Arr; d: Arr };
  cci(h: number[], l: number[], c: number[], p: number): Arr; williamsR(h: number[], l: number[], c: number[], p: number): Arr;
  ao(h: number[], l: number[]): Arr; vwma(c: number[], v: number[], p: number): Arr;
  macd(c: number[], f: number, s: number, g: number): { macd: Arr; signal: Arr };
  adxDI(h: number[], l: number[], c: number[], p: number): { adx: Arr; plusDI: Arr; minusDI: Arr } | null;
  elderRay(h: number[], l: number[], c: number[], p: number): { bull: Arr; bear: Arr };
};

const lastOf = (a: Arr | undefined | null, back = 0): number | null => {
  if (!a) return null;
  let seen = 0;
  for (let i = a.length - 1; i >= 0; i--) { const v = a[i]; if (v != null && Number.isFinite(v)) { if (seen === back) return v; seen++; } }
  return null;
};

export function columnsOf(bars: Candle[]): IndicatorColumns {
  return { open: bars.map((b) => b.o), high: bars.map((b) => b.h), low: bars.map((b) => b.l), close: bars.map((b) => b.c), volume: bars.map((b) => b.v), time: bars.map((b) => b.t) };
}

/** The oscillators and the moving averages, each with its value and vote. */
export function technicals(bars: Candle[]): { oscillators: TechRow[]; averages: TechRow[] } | null {
  if (bars.length < 60) return null;
  try {
    const c = bars.map((b) => b.c), h = bars.map((b) => b.h), l = bars.map((b) => b.l), v = bars.map((b) => b.v ?? 0);
    const price = c[c.length - 1]!;
    const osc: TechRow[] = [];
    const push = (id: string, name: string, value: number | null, action: Action) => { if (value != null) osc.push({ id, name, value, action }); };
    const r = lastOf(X.rsi(c, 14));
    push('rsi', 'Relative Strength Index (14)', r, r == null ? 0 : r < 30 ? 1 : r > 70 ? -1 : 0);
    const st = X.stoch(h, l, c, 14, 3); const k = lastOf(st.k);
    push('stoch', 'Stochastic %K (14, 3, 3)', k, k == null ? 0 : k < 20 ? 1 : k > 80 ? -1 : 0);
    const cc = lastOf(X.cci(h, l, c, 20));
    push('cci', 'Commodity Channel Index (20)', cc, cc == null ? 0 : cc < -100 ? 1 : cc > 100 ? -1 : 0);
    const ax = X.adxDI(h, l, c, 14);
    if (ax) {
      const a = lastOf(ax.adx), p0 = lastOf(ax.plusDI), m0 = lastOf(ax.minusDI), p1 = lastOf(ax.plusDI, 1), m1 = lastOf(ax.minusDI, 1);
      const act: Action = a != null && p0 != null && m0 != null && p1 != null && m1 != null && a > 20 ? (p1 < m1 && p0 > m0 ? 1 : p1 > m1 && p0 < m0 ? -1 : 0) : 0;
      push('adx', 'Average Directional Index (14)', a, act);
    }
    const aoArr = X.ao(h, l); const ao0 = lastOf(aoArr), ao1 = lastOf(aoArr, 1);
    push('ao', 'Awesome Oscillator', ao0, ao0 == null ? 0 : ao0 > 0 && ao1 != null && ao0 > ao1 ? 1 : ao0 < 0 && ao1 != null && ao0 < ao1 ? -1 : 0);
    if (c.length > 12) {
      const n = c.length; const mom0 = c[n - 1]! - c[n - 11]!; const mom1 = c[n - 2]! - c[n - 12]!;
      push('mom', 'Momentum (10)', mom0, mom0 > mom1 ? 1 : mom0 < mom1 ? -1 : 0);
    }
    const m = X.macd(c, 12, 26, 9); const ml = lastOf(m.macd), ms = lastOf(m.signal);
    push('macd', 'MACD Level (12, 26)', ml, ml == null || ms == null ? 0 : ml > ms ? 1 : ml < ms ? -1 : 0);
    const sr = lastOf(X.stochRsi(c, 14, 3, 3).k);
    push('stochrsi', 'Stochastic RSI Fast (3, 3, 14, 14)', sr, sr == null ? 0 : sr < 20 ? 1 : sr > 80 ? -1 : 0);
    const wr = lastOf(X.williamsR(h, l, c, 14));
    push('wr', 'Williams Percent Range (14)', wr, wr == null ? 0 : wr < -80 ? 1 : wr > -20 ? -1 : 0);
    const er = X.elderRay(h, l, c, 13);
    const b0 = lastOf(er.bull), b1 = lastOf(er.bull, 1), e0 = lastOf(er.bear), e1 = lastOf(er.bear, 1);
    if (b0 != null && e0 != null) {
      const act: Action = b1 != null && e1 != null ? (e0 < 0 && e0 > e1 && b0 > b1 ? 1 : b0 > 0 && b0 < b1 && e0 < e1 ? -1 : 0) : 0;
      push('bbp', 'Bull Bear Power', b0 + e0, act);
    }
    const uo = REGISTRY.uo ? lastOf(REGISTRY.uo.calc(columnsOf(bars), { short: 7, mid: 14, long: 28 }).line) : null;
    push('uo', 'Ultimate Oscillator (7, 14, 28)', uo, uo == null ? 0 : uo > 70 ? 1 : uo < 30 ? -1 : 0);

    const avg: TechRow[] = [];
    const rate = (id: string, name: string, value: number | null) => { if (value != null) avg.push({ id, name, value, action: price > value ? 1 : price < value ? -1 : 0 }); };
    for (const p of [10, 20, 30, 50, 100, 200]) {
      if (c.length <= p + 2) continue;
      rate(`ema${p}`, `Exponential Moving Average (${p})`, lastOf(X.ema(c, p)));
      rate(`sma${p}`, `Simple Moving Average (${p})`, lastOf(X.sma(c, p)));
    }
    if (c.length >= 26) {
      const hh = Math.max(...h.slice(-26)), ll = Math.min(...l.slice(-26));
      rate('ichimoku', 'Ichimoku Base Line (9, 26, 52, 26)', (hh + ll) / 2);
    }
    if (v.some((x) => x > 0) && c.length > 22) rate('vwma', 'Volume Weighted Moving Average (20)', lastOf(X.vwma(c, v, 20)));
    rate('hma', 'Hull Moving Average (9)', lastOf(X.hma(c, 9)));
    return { oscillators: osc, averages: avg };
  } catch {
    return null;
  }
}

/** The plots of an indicator's output an alert can watch: the main line, its signal, the bands… */
export function outputsOf(out: IndicatorOutput): { id: string; label: string; values: (number | null)[] }[] {
  const list: { id: string; label: string; values: (number | null)[] }[] = [];
  const add = (id: string, label: string, values: (number | null)[] | undefined) => { if (Array.isArray(values) && values.length) list.push({ id, label, values }); };
  add('line', out.macd ? 'MACD' : 'Value', out.line);
  add('signal', 'Signal', out.signal);
  add('signal2', 'Signal 2', out.signal2);
  add('hist', 'Histogram', out.hist);
  add('upper', 'Upper', out.upper);
  add('basis', 'Basis', out.basis);
  add('lower', 'Lower', out.lower);
  (out.lines ?? []).forEach((ln, i) => add(`lines.${i}`, ln.name ?? `Plot ${i + 1}`, ln.data));
  return list;
}

/** One indicator's outputs over the bars, with its inputs (the catalogue's defaults when none). */
export function indicatorSeries(key: string, bars: Candle[], inputs?: Record<string, unknown>): { id: string; label: string; values: (number | null)[] }[] {
  const def = REGISTRY[key];
  if (!def || !bars.length) return [];
  try { return outputsOf(def.calc(columnsOf(bars), { ...def.inputs, ...(inputs ?? {}) })); } catch { return []; }
}
