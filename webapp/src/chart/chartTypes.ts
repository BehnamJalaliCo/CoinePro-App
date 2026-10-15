// TradingView's chart types, in its menu order, each with the glyph of the same name in the sprite.
// Time-based types map to a lightweight-charts series; Renko, Line break, Kagi, Point & figure and
// Range come from the old Pro-Chart builders (engine/chartbuilders.js).
import {
  AreaSeries, BarSeries, BaselineSeries, CandlestickSeries, HistogramSeries, LineSeries, LineType,
  type IChartApi, type ISeriesApi, type SeriesType, type UTCTimestamp,
} from 'lightweight-charts';
import { buildNonStandard } from '../engine/chartbuilders.js';
import type { Candle } from '../data/types';

export type ChartType =
  | 'bars' | 'candles' | 'hollow' | 'heikin' | 'line' | 'linemarkers' | 'step' | 'area' | 'baseline'
  | 'columns' | 'highlow' | 'volcandles' | 'renko' | 'linebreak' | 'kagi' | 'pnf' | 'range';

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

/** Types whose bars are not the time series itself: live updates rebuild them from the bars. */
export const DERIVED: ChartType[] = ['heikin', 'renko', 'linebreak', 'kagi', 'pnf', 'range'];

export function createPriceSeries(chart: IChartApi, type: ChartType, p: Palette, fmt: { precision: number; minMove: number }): ISeriesApi<SeriesType> {
  const priceFormat = { type: 'price' as const, ...fmt };
  const common = { priceFormat, priceLineStyle: 3 as const };
  switch (type) {
    case 'bars':
      return chart.addSeries(BarSeries, { ...common, upColor: p.up, downColor: p.down, thinBars: false });
    case 'hollow':
      // Hollow: rising bars outlined only (TradingView's "Hollow candles").
      return chart.addSeries(CandlestickSeries, { ...common, upColor: 'rgba(0,0,0,0)', downColor: p.down, borderUpColor: p.up, borderDownColor: p.down, wickUpColor: p.up, wickDownColor: p.down });
    case 'line': case 'kagi': case 'pnf':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: 2 });
    case 'linemarkers':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: 2, pointMarkersVisible: true, pointMarkersRadius: 3 });
    case 'step':
      return chart.addSeries(LineSeries, { ...common, color: p.line, lineWidth: 2, lineType: LineType.WithSteps });
    case 'area':
      return chart.addSeries(AreaSeries, { ...common, lineColor: p.line, lineWidth: 2, topColor: 'rgba(41, 98, 255, 0.28)', bottomColor: 'rgba(41, 98, 255, 0.02)' });
    case 'baseline':
      return chart.addSeries(BaselineSeries, { ...common, topLineColor: p.up, bottomLineColor: p.down, topFillColor1: 'rgba(8,153,129,0.28)', topFillColor2: 'rgba(8,153,129,0.05)', bottomFillColor1: 'rgba(242,54,69,0.05)', bottomFillColor2: 'rgba(242,54,69,0.28)', lineWidth: 2 });
    case 'columns':
      return chart.addSeries(HistogramSeries, { ...common, color: p.up });
    case 'highlow':
      return chart.addSeries(BarSeries, { ...common, upColor: p.line, downColor: p.line, openVisible: false, thinBars: false });
    default:
      return chart.addSeries(CandlestickSeries, { ...common, upColor: p.up, downColor: p.down, borderUpColor: p.up, borderDownColor: p.down, wickUpColor: p.up, wickDownColor: p.down });
  }
}

/** A colour with the bar's volume as its strength: a quiet bar faint, a heavy one solid. */
function volumeTint(hex: string, v: number, avg: number): string {
  const k = avg > 0 ? Math.max(0.25, Math.min(1, v / (avg * 1.5))) : 1;
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const n = parseInt(m[1]!, 16);
  return `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, ${k.toFixed(2)})`;
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

function heikinAshi(bars: Candle[]): Candle[] {
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

/** The series data for a type, from the plain bars. */
export function seriesData(type: ChartType, bars: Candle[], p: Palette): unknown[] {
  const ohlc = (list: Candle[]) => list.map((b) => ({ time: b.t as UTCTimestamp, open: b.o, high: b.h, low: b.l, close: b.c }));
  switch (type) {
    case 'line': case 'linemarkers': case 'step': case 'area': case 'baseline':
      return bars.map((b) => ({ time: b.t as UTCTimestamp, value: b.c }));
    case 'columns':
      return bars.map((b, i) => ({ time: b.t as UTCTimestamp, value: b.c, color: b.c >= (bars[i - 1]?.c ?? b.o) ? p.up : p.down }));
    case 'heikin':
      return ohlc(heikinAshi(bars));
    case 'volcandles':
      return bars.map((b, i) => volCandle(b, avgVolume(bars, i), p));
    case 'renko': case 'linebreak': case 'range': case 'kagi': case 'pnf': {
      const built = buildNonStandard(type, bars) as { kind: 'candle' | 'line'; data: { t: number; o?: number; h?: number; l?: number; c?: number; value?: number }[] } | null;
      if (!built) return ohlc(bars);
      return built.kind === 'line'
        ? built.data.map((d) => ({ time: d.t as UTCTimestamp, value: d.value! }))
        : built.data.map((d) => ({ time: d.t as UTCTimestamp, open: d.o!, high: d.h!, low: d.l!, close: d.c! }));
    }
    default:
      return ohlc(bars);
  }
}

/** One live point for a time-based type, or null when the type must be rebuilt instead. */
export function livePoint(type: ChartType, bar: Candle, prev: Candle | undefined, p: Palette): unknown | null {
  if (DERIVED.includes(type)) return null;
  switch (type) {
    case 'line': case 'linemarkers': case 'step': case 'area': case 'baseline':
      return { time: bar.t as UTCTimestamp, value: bar.c };
    case 'columns':
      return { time: bar.t as UTCTimestamp, value: bar.c, color: bar.c >= (prev?.c ?? bar.o) ? p.up : p.down };
    case 'volcandles':
      return volCandle(bar, prev?.v ?? bar.v, p);
    default:
      return { time: bar.t as UTCTimestamp, open: bar.o, high: bar.h, low: bar.l, close: bar.c };
  }
}
