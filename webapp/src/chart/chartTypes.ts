// TradingView's chart types, in its menu order, each with the glyph of the same name in the sprite.
// Time-based types map to a lightweight-charts series styled from the Settings → Symbol tab;
// Renko, Line break, Range, Kagi and Point & figure are built from the bars with TradingView's
// inputs (box size by ATR or fixed, reversal, line count) by the old Pro-Chart's builders.
import {
  AreaSeries, BarSeries, BaselineSeries, CandlestickSeries, HistogramSeries, LineSeries, LineType,
  type IChartApi, type ISeriesApi, type SeriesType, type UTCTimestamp,
} from 'lightweight-charts';
import { avgRange, kagi, lineBreak, rangeBars } from '../engine/chartbuilders.js';
import { hollowCandles } from '../engine/charttypes_ext.js';
import type { Candle } from '../data/types';
import type { ChartSettings } from '../state/chartSettings';
import type { PnfColumn } from './primitives';

export type ChartType =
  | 'bars' | 'candles' | 'hollow' | 'heikin' | 'line' | 'linemarkers' | 'step' | 'area' | 'baseline'
  | 'columns' | 'highlow' | 'volcandles' | 'hlcarea' | 'renko' | 'linebreak' | 'kagi' | 'pnf' | 'range';

export interface ChartTypeDef { id: ChartType; icon: string; fa: string; en: string; group: number }

export const CHART_TYPES: ChartTypeDef[] = [
  { id: 'bars', icon: 'chart_bars', fa: 'میله‌ای', en: 'Bars', group: 0 },
  { id: 'candles', icon: 'chart_candles', fa: 'شمعی', en: 'Candles', group: 0 },
  { id: 'hollow', icon: 'chart_hollow', fa: 'شمعی توخالی', en: 'Hollow candles', group: 0 },
  { id: 'volcandles', icon: 'chart_volcandles', fa: 'شمعی حجمی', en: 'Volume candles', group: 0 },
  { id: 'line', icon: 'chart_line', fa: 'خطی', en: 'Line', group: 1 },
  { id: 'linemarkers', icon: 'chart_lwm', fa: 'خطی با نشانگر', en: 'Line with markers', group: 1 },
  { id: 'step', icon: 'chart_step', fa: 'پله‌ای', en: 'Step line', group: 1 },
  { id: 'area', icon: 'chart_area', fa: 'ناحیه‌ای', en: 'Area', group: 2 },
  { id: 'baseline', icon: 'chart_baseline', fa: 'خط مبنا', en: 'Baseline', group: 2 },
  { id: 'hlcarea', icon: 'chart_hlcarea', fa: 'ناحیه‌ی سقف، کف و پایانی', en: 'HLC area', group: 2 },
  { id: 'columns', icon: 'chart_columns', fa: 'ستونی', en: 'Columns', group: 3 },
  { id: 'highlow', icon: 'chart_highlow', fa: 'سقف و کف', en: 'High-low', group: 3 },
  { id: 'heikin', icon: 'chart_heikin', fa: 'هیکن آشی', en: 'Heikin Ashi', group: 4 },
  { id: 'renko', icon: 'chart_renko', fa: 'رنکو', en: 'Renko', group: 4 },
  { id: 'linebreak', icon: 'chart_linebreak', fa: 'شکست خط', en: 'Line break', group: 4 },
  { id: 'kagi', icon: 'chart_kagi', fa: 'کاگی', en: 'Kagi', group: 4 },
  { id: 'pnf', icon: 'chart_pnf', fa: 'نقطه و رقم', en: 'Point & figure', group: 4 },
  { id: 'range', icon: 'chart_range', fa: 'رنج', en: 'Range', group: 4 },
];

export const chartTypeDef = (id: ChartType) => CHART_TYPES.find((c) => c.id === id) ?? CHART_TYPES[1]!;

export interface Palette { up: string; down: string; line: string; bg: string }

/** Everything about the price series' look that the settings decide. */
export type SeriesStyle = Palette & Pick<ChartSettings,
  'bodyVisible' | 'borderVisible' | 'borderUp' | 'borderDown' | 'wickVisible' | 'wickUp' | 'wickDown' | 'colorByPrevClose' |
  'thinBars' | 'hollow' | 'lineWidth' | 'areaTop' | 'areaBottom'>;

/** The inputs of the types built from the bars. */
export type TypeParams = Pick<ChartSettings,
  'renkoMethod' | 'renkoAtr' | 'renkoBox' | 'renkoWicks' | 'rangeMethod' | 'rangeSize' | 'lineBreakCount' |
  'kagiMethod' | 'kagiAtr' | 'kagiReversal' | 'pnfMethod' | 'pnfAtr' | 'pnfBox' | 'pnfReversal' | 'pnfSource'>;

/** Types whose bars are not the time series itself: live updates rebuild them from the bars. */
export const DERIVED: ChartType[] = ['heikin', 'renko', 'linebreak', 'kagi', 'pnf', 'range'];
/** Types without time: their bricks are evenly spaced, so nothing else may add time points. */
export const TIMELESS: ChartType[] = ['renko', 'linebreak', 'kagi', 'pnf', 'range'];
/** Timeless types whose bricks are bars (open, high, low, close): indicators and volume make sense on them. */
export const BRICKS: ChartType[] = ['renko', 'linebreak', 'range'];
/** Types with inputs of their own (the gear beside the chart-type button). */
export const PARAM_TYPES: ChartType[] = ['renko', 'linebreak', 'kagi', 'pnf', 'range', 'baseline'];

const CLEAR = 'rgba(0,0,0,0)';

export function createPriceSeries(chart: IChartApi, type: ChartType, p: SeriesStyle, fmt: { precision: number; minMove: number }): ISeriesApi<SeriesType> {
  const priceFormat = { type: 'price' as const, ...fmt };
  const common = { priceFormat, priceLineStyle: 3 as const };
  const candle = {
    upColor: p.bodyVisible ? p.up : CLEAR, downColor: p.bodyVisible ? p.down : CLEAR,
    borderVisible: p.borderVisible, borderUpColor: p.borderUp, borderDownColor: p.borderDown,
    wickVisible: p.wickVisible, wickUpColor: p.wickUp, wickDownColor: p.wickDown,
  };
  switch (type) {
    case 'bars':
      return chart.addSeries(BarSeries, { ...common, upColor: p.up, downColor: p.down, thinBars: p.thinBars });
    case 'hollow':
      // TradingView's hollow candles: colour by the close against the previous close, body hollow when it closes above its open.
      return chart.addSeries(CandlestickSeries, { ...common, ...candle, borderVisible: true, upColor: CLEAR });
    case 'line':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: p.lineWidth as 1 | 2 | 3 | 4 });
    case 'kagi': case 'pnf':
      // A host for the price scale, the label and the crosshair; the drawing is the chart's primitive.
      return chart.addSeries(LineSeries, { ...common, color: p.up, lineVisible: false, crosshairMarkerVisible: false, lineWidth: 1 });
    case 'hlcarea':
      // The close as a line; the high–close and close–low bands are drawn by the chart (BandFill).
      return chart.addSeries(LineSeries, { ...common, color: '#787B86', lineWidth: 2 });
    case 'linemarkers':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: p.lineWidth as 1 | 2 | 3 | 4, pointMarkersVisible: true, pointMarkersRadius: 3 });
    case 'step':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: p.lineWidth as 1 | 2 | 3 | 4, lineType: LineType.WithSteps });
    case 'area':
      return chart.addSeries(AreaSeries, { ...common, lineColor: p.line, lineWidth: p.lineWidth as 1 | 2 | 3 | 4, topColor: p.areaTop, bottomColor: p.areaBottom });
    case 'baseline':
      return chart.addSeries(BaselineSeries, { ...common, topLineColor: p.up, bottomLineColor: p.down, topFillColor1: tint(p.up, 0.28), topFillColor2: tint(p.up, 0.05), bottomFillColor1: tint(p.down, 0.05), bottomFillColor2: tint(p.down, 0.28), lineWidth: p.lineWidth as 1 | 2 | 3 | 4 });
    case 'columns':
      return chart.addSeries(HistogramSeries, { ...common, color: p.up });
    case 'highlow':
      // A body from the low to the high, no close tick, coloured by the close against the previous close.
      return chart.addSeries(CandlestickSeries, { ...common, upColor: p.up, downColor: p.down, borderVisible: false, wickVisible: false });
    default:
      return chart.addSeries(CandlestickSeries, { ...common, ...candle, upColor: p.bodyVisible ? (p.hollow ? CLEAR : p.up) : CLEAR, borderVisible: p.borderVisible || p.hollow });
  }
}

/** A colour with the bar's volume as its strength: a quiet bar faint, a heavy one solid. */
function volumeTint(hex: string, v: number, avg: number): string {
  const k = avg > 0 ? Math.max(0.25, Math.min(1, v / (avg * 1.5))) : 1;
  return tint(hex, Number(k.toFixed(2)));
}

function volCandle(b: Candle, avg: number, p: Palette) {
  const c = volumeTint(b.c >= b.o ? p.up : p.down, b.v, avg);
  return { time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c, color: c, borderColor: c, wickColor: c };
}

const avgVolume = (bars: Candle[], end: number) => {
  let n = 0; let sum = 0;
  for (let i = Math.max(0, end - 20); i < end; i++) { sum += bars[i]!.v; n++; }
  return n ? sum / n : 0;
};

export function heikinAshi(bars: Candle[]): Candle[] {
  const out: Candle[] = [];
  for (let i = 0; i < bars.length; i++) {
    const b = bars[i]!;
    const c = (b.o + b.h + b.l + b.c) / 4;
    const prev = out[i - 1];
    const o = prev ? (prev.o + prev.c) / 2 : (b.o + b.c) / 2;
    out.push({ t: b.t, o, h: Math.max(b.h, o, c), l: Math.min(b.l, o, c), c, v: b.v });
  }
  return out;
}

/** Gives every brick a share of the volume of the bar that made it (the last bar at or before the
 *  brick's time; the builders nudge each brick a second past the one before). */
export function shareVolume(bricks: Candle[], bars: Candle[]): Candle[] {
  const src: number[] = [];
  let j = 0;
  for (const b of bricks) { while (j + 1 < bars.length && bars[j + 1]!.t <= b.t) j++; src.push(j); }
  const count = new Map<number, number>();
  for (const s of src) count.set(s, (count.get(s) ?? 0) + 1);
  return bricks.map((b, i) => ({ ...b, v: (bars[src[i]!]?.v ?? 0) / (count.get(src[i]!) ?? 1) }));
}

/** Renko as TradingView builds it: a brick each time the close moves a box beyond the last brick,
 *  a reversal only after two boxes; wicks (when asked) reach the extremes traded since the last brick. */
export function renkoBuild(bars: Candle[], box: number, wicks: boolean): Candle[] {
  if (!bars.length || !(box > 0)) return [];
  const out: Candle[] = [];
  let top = bars[0]!.c; let bot = bars[0]!.c;
  let lo = Infinity; let hi = -Infinity; let last = 0;
  const t = (x: number) => { last = Math.max(x, last + 1); return last; };
  for (const b of bars) {
    lo = Math.min(lo, b.l); hi = Math.max(hi, b.h);
    while (b.c >= top + box) {
      const o = top; const c = top + box;
      out.push({ t: t(b.t), o, h: c, l: wicks ? Math.min(o, lo) : o, c, v: 0 });
      top = c; bot = top - box; lo = Infinity; hi = -Infinity;
    }
    while (b.c <= bot - box) {
      const o = bot; const c = bot - box;
      out.push({ t: t(b.t), o, h: wicks ? Math.max(o, hi) : o, l: c, c, v: 0 });
      bot = c; top = bot + box; lo = Infinity; hi = -Infinity;
    }
  }
  return out;
}

/** Point & figure as TradingView builds it: a column of X while price rises a box at a time, a
 *  column of O while it falls, a new column only on a reversal of `rev` boxes; each column dated
 *  by the bar that began it. Source: the close, or the high and the low. */
export function pnfBuild(bars: Candle[], box: number, rev: number, source: 'close' | 'hl'): PnfColumn[] {
  if (!bars.length || !(box > 0)) return [];
  const up = (p: number) => Math.floor(p / box + 1e-9) * box;
  const dn = (p: number) => Math.ceil(p / box - 1e-9) * box;
  const cols: PnfColumn[] = [];
  const first = bars[0]!;
  let cur: { time: number; dir: 1 | -1 | 0; lo: number; hi: number } = { time: first.t, dir: 0, lo: Math.round(first.c / box) * box, hi: Math.round(first.c / box) * box };
  for (let i = 1; i < bars.length; i++) {
    const b = bars[i]!;
    const hiP = source === 'hl' ? b.h : b.c; const loP = source === 'hl' ? b.l : b.c;
    if (cur.dir === 0) {
      if (up(hiP) >= cur.hi + box) { cur.dir = 1; cur.hi = up(hiP); }
      else if (dn(loP) <= cur.lo - box) { cur.dir = -1; cur.lo = dn(loP); }
      continue;
    }
    if (cur.dir === 1) {
      if (up(hiP) >= cur.hi + box) cur.hi = up(hiP);
      else if (loP <= cur.hi - rev * box) { cols.push({ ...cur, dir: 1 }); cur = { time: b.t, dir: -1, hi: cur.hi - box, lo: dn(loP) }; }
    } else {
      if (dn(loP) <= cur.lo - box) cur.lo = dn(loP);
      else if (hiP >= cur.lo + rev * box) { cols.push({ ...cur, dir: -1 }); cur = { time: b.t, dir: 1, lo: cur.lo + box, hi: up(hiP) }; }
    }
  }
  cols.push({ ...cur, dir: cur.dir === -1 ? -1 : 1 });
  return cols;
}

export interface Built {
  /** The bars as the series shows them (bricks for the timeless types, Heikin Ashi's own bars). */
  candles: Candle[];
  /** Series data in lightweight-charts' shape. */
  data: unknown[];
  kagi?: { time: number; value: number }[];
  pnf?: { cols: PnfColumn[]; box: number };
}

/** The series data for a type, from the plain bars, with the type's own inputs and the settings' colours. */
export function buildSeries(type: ChartType, bars: Candle[], p: SeriesStyle, q: TypeParams): Built {
  const ohlc = (list: Candle[]) => list.map((b) => ({ time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c }));
  const byPrev = (list: Candle[]) => list.map((b, i) => {
    const prev = list[i - 1]?.c ?? b.o;
    const rising = b.c >= prev;
    return {
      time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c,
      color: p.bodyVisible ? (rising ? (p.hollow && b.c >= b.o ? CLEAR : p.up) : p.down) : CLEAR,
      borderColor: rising ? p.borderUp : p.borderDown, wickColor: rising ? p.wickUp : p.wickDown,
    };
  });
  switch (type) {
    case 'line': case 'linemarkers': case 'step': case 'area': case 'baseline': case 'hlcarea':
      return { candles: bars, data: bars.map((b) => ({ time: b.t as UTCTimestamp, value: b.c })) };
    case 'columns':
      return { candles: bars, data: bars.map((b, i) => ({ time: b.t as UTCTimestamp, value: b.c, color: b.c >= (bars[i - 1]?.c ?? b.o) ? p.up : p.down })) };
    case 'bars':
      return { candles: bars, data: p.colorByPrevClose ? bars.map((b, i) => ({ time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c, color: b.c >= (bars[i - 1]?.c ?? b.o) ? p.up : p.down })) : ohlc(bars) };
    case 'hollow':
      return { candles: bars, data: hollowCandles(bars, { up: p.up, down: p.down }).map((d) => ({ ...d, time: d.time as UTCTimestamp })) };
    case 'highlow': {
      let prev = bars[0]?.c ?? 0;
      const data = bars.map((b) => { const c = b.c >= prev ? p.up : p.down; prev = b.c; return { time: b.t as UTCTimestamp, open: b.l, high: b.h, low: b.l, close: b.h, color: c, borderColor: c, wickColor: CLEAR }; });
      return { candles: bars, data };
    }
    case 'heikin': {
      const ha = heikinAshi(bars);
      return { candles: ha, data: p.colorByPrevClose ? byPrev(ha) : ohlc(ha) };
    }
    case 'volcandles':
      return { candles: bars, data: bars.map((b, i) => volCandle(b, avgVolume(bars, i), p)) };
    case 'renko': case 'range': case 'linebreak': {
      const raw = type === 'renko'
        ? renkoBuild(bars, q.renkoMethod === 'traditional' && q.renkoBox > 0 ? q.renkoBox : avgRange(bars, q.renkoAtr), q.renkoWicks)
        : (type === 'range'
          ? rangeBars(bars, q.rangeMethod === 'traditional' && q.rangeSize > 0 ? q.rangeSize : avgRange(bars, 14))
          : lineBreak(bars, Math.max(1, Math.round(q.lineBreakCount))));
      const bricks = shareVolume(raw.map((b) => ({ t: b.t, o: b.o, h: b.h, l: b.l, c: b.c, v: 0 })), bars);
      const data = bricks.map((b) => {
        const col = b.c >= b.o ? p.up : p.down;
        return { time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c, color: p.bodyVisible ? col : CLEAR, borderColor: b.c >= b.o ? p.borderUp : p.borderDown, wickColor: b.c >= b.o ? p.wickUp : p.wickDown };
      });
      return { candles: bricks, data };
    }
    case 'kagi': {
      const reversal = q.kagiMethod === 'traditional' && q.kagiReversal > 0 ? q.kagiReversal : avgRange(bars, q.kagiAtr);
      const pts = kagi(bars, reversal, q.kagiAtr).map((x) => ({ time: x.t, value: x.value }));
      let prev = pts[0]?.value ?? 0;
      const data = pts.map((x) => { const c = x.value >= prev ? p.up : p.down; prev = x.value; return { time: x.time as UTCTimestamp, value: x.value, color: c }; });
      return { candles: pts.map((x) => ({ t: x.time, o: x.value, h: x.value, l: x.value, c: x.value, v: 0 })), data, kagi: pts };
    }
    case 'pnf': {
      const box = q.pnfMethod === 'traditional' && q.pnfBox > 0 ? q.pnfBox : avgRange(bars, q.pnfAtr);
      const cols = pnfBuild(bars, box, Math.max(1, Math.round(q.pnfReversal)), q.pnfSource);
      const data = cols.map((c) => ({ time: c.time as UTCTimestamp, value: c.dir > 0 ? c.hi : c.lo, color: c.dir > 0 ? p.up : p.down }));
      return { candles: cols.map((c) => ({ t: c.time, o: c.dir > 0 ? c.lo : c.hi, h: c.hi, l: c.lo, c: c.dir > 0 ? c.hi : c.lo, v: 0 })), data, pnf: { cols, box } };
    }
    default:
      return { candles: bars, data: p.colorByPrevClose ? byPrev(bars) : ohlc(bars) };
  }
}

/** One live point for a time-based type, or null when the type must be rebuilt instead. */
export function livePoint(type: ChartType, bar: Candle, prev: Candle | undefined, p: SeriesStyle): unknown | null {
  // HLC area's bands, hollow and high-low colour by the previous close: those repaint as a whole.
  if (DERIVED.includes(type) || type === 'hlcarea' || type === 'hollow' || type === 'highlow') return null;
  switch (type) {
    case 'line': case 'linemarkers': case 'step': case 'area': case 'baseline':
      return { time: bar.t as UTCTimestamp, value: bar.c };
    case 'columns':
      return { time: bar.t as UTCTimestamp, value: bar.c, color: bar.c >= (prev?.c ?? bar.o) ? p.up : p.down };
    case 'volcandles':
      return volCandle(bar, prev?.v ?? bar.v, p);
    case 'bars':
      return p.colorByPrevClose
        ? { time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c, color: bar.c >= (prev?.c ?? bar.o) ? p.up : p.down }
        : { time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c };
    default: {
      if (!p.colorByPrevClose) return { time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c };
      const rising = bar.c >= (prev?.c ?? bar.o);
      return {
        time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c,
        color: p.bodyVisible ? (rising ? (p.hollow && bar.c >= bar.o ? CLEAR : p.up) : p.down) : CLEAR,
        borderColor: rising ? p.borderUp : p.borderDown, wickColor: rising ? p.wickUp : p.wickDown,
      };
    }
  }
}

/** A #rrggbb colour at an opacity (other colour forms are returned as they are). */
export function tint(hex: string, a: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const n = parseInt(m[1]!, 16);
  return `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}
