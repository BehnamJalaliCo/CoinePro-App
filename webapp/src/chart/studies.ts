// Draws the engine's indicators (engine/indicators.js REGISTRY, 108 studies) with lightweight-charts:
// overlays on the price pane, oscillators each in its own pane below, as TradingView stacks them.
// Every output shape the registry produces is mapped: line/signal/signal2, histograms (by sign,
// by palette, MACD's four shades), multi-line bands with their fill, Ichimoku's two-coloured
// cloud, guide levels with the shaded zone between them, a fixed range, markers and levels.
import {
  HistogramSeries, LineSeries, LineStyle, createSeriesMarkers,
  type IChartApi, type IPriceLine, type ISeriesApi, type ISeriesMarkersPluginApi, type SeriesMarker, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import { REGISTRY, type IndicatorColumns, type IndicatorOutput, type Values } from '../engine/indicators.js';
import { BandFillPrimitive } from '../engine/bandFill.js';
import type { Candle } from '../data/types';
import type { Study } from '../state/studies';

// TradingView's default colours for the studies people add first; the rest keep the engine's.
const TV_COLOR: Record<string, string> = {
  ma: '#2962FF', ema: '#2962FF', wma: '#2962FF', hma: '#2962FF', vwap: '#2962FF', rsi: '#7E57C2', stoch: '#2962FF',
  stochrsi: '#2962FF', macd: '#2962FF', atr: '#B71C1C', cci: '#2962FF', willr: '#7E57C2', mfi: '#7E57C2', adx: '#FF5252',
  obv: '#2962FF', cmf: '#43A047', aroon: '#FB8C00', mom: '#2962FF', roc: '#2962FF', supertrend: '#089981',
};
const SIGNAL = '#FF6D00';
const GUIDE = '#787B86';
const UP = '#089981';
const DOWN = '#F23645';

export interface StudyLegendRow { uid: string; key: string; label: string; colors: string[]; values: (number | null)[]; pane: number; hidden: boolean }

interface Rendered {
  study: Study;
  pane: number;
  series: ISeriesApi<SeriesType>[];
  colors: string[];
  outputs: Values[];
  priceLines: { series: ISeriesApi<SeriesType>; line: IPriceLine }[];
  bands: { series: ISeriesApi<SeriesType>; band: BandFillPrimitive }[];
  markers: SeriesMarker<Time>[];
}

const columns = (bars: Candle[]): IndicatorColumns => ({
  open: bars.map((b) => b.o), high: bars.map((b) => b.h), low: bars.map((b) => b.l),
  close: bars.map((b) => b.c), volume: bars.map((b) => b.v), time: bars.map((b) => b.t),
});

const lineData = (bars: Candle[], v: Values) => {
  const out: { time: UTCTimestamp; value?: number }[] = [];
  for (let i = 0; i < bars.length; i++) {
    const x = v[i];
    out.push(x == null || !Number.isFinite(x) ? { time: bars[i]!.t as UTCTimestamp } : { time: bars[i]!.t as UTCTimestamp, value: x });
  }
  return out;
};

export class StudyRenderer {
  private rendered: Rendered[] = [];
  private markersApi: ISeriesMarkersPluginApi<Time> | null = null;
  private bars: Candle[] = [];
  /** The first pane index no study uses (a NamaScript oscillator goes there). */
  freePane = 1;

  constructor(private chart: IChartApi, private price: () => ISeriesApi<SeriesType> | null, private digits: () => number) {}

  /** Rebuilds every study's series from scratch (on add, remove, input change, symbol change). */
  rebuild(studies: Study[], bars: Candle[]): void {
    this.bars = bars;
    this.dispose();
    let nextPane = 1;
    for (const st of studies) {
      const def = REGISTRY[st.key];
      if (!def) continue;
      const pane = def.pane === 'main' ? 0 : nextPane++;
      const r: Rendered = { study: st, pane, series: [], colors: [], outputs: [], priceLines: [], bands: [], markers: [] };
      this.rendered.push(r);
      if (st.hidden) continue;
      let out: IndicatorOutput;
      try { out = def.calc(columns(bars), { ...def.inputs, ...st.inputs }); } catch { continue; }
      this.draw(r, out, TV_COLOR[st.key] ?? def.color);
    }
    this.freePane = nextPane;
    // Panes left over from studies that were removed go with them.
    const panes = this.chart.panes();
    for (let i = panes.length - 1; i >= nextPane; i--) {
      if (i > 0) { try { this.chart.removePane(i); } catch { /* pane still holds a series */ } }
    }
    this.applyMarkers();
  }

  /** Recomputes values for new or changed bars without recreating series. */
  refresh(bars: Candle[]): void {
    this.bars = bars;
    for (const r of this.rendered) {
      if (r.study.hidden || !r.series.length) continue;
      const def = REGISTRY[r.study.key];
      if (!def) continue;
      let out: IndicatorOutput;
      try { out = def.calc(columns(bars), { ...def.inputs, ...r.study.inputs }); } catch { continue; }
      const values = this.flatten(out);
      if (values.length !== r.series.length) continue;
      values.forEach((v, i) => {
        const s = r.series[i]!;
        if (s.seriesType() === 'Histogram') s.setData(this.histData(bars, v.values, v.colorFor));
        else s.setData(lineData(bars, v.values));
      });
      r.outputs = values.map((v) => v.values);
    }
  }

  legend(index: number | null): StudyLegendRow[] {
    return this.rendered.map((r) => {
      const def = REGISTRY[r.study.key]!;
      const i = index ?? this.bars.length - 1;
      const inputs = Object.values({ ...def.inputs, ...r.study.inputs }).filter((v) => typeof v === 'number');
      const label = `${shortName(r.study.key, def.label)}${inputs.length ? ` ${inputs.join(' ')}` : ''}`;
      return { uid: r.study.uid, key: r.study.key, label, colors: r.colors, values: r.outputs.map((o) => o[i] ?? null), pane: r.pane, hidden: Boolean(r.study.hidden) };
    });
  }

  dispose(): void {
    for (const r of this.rendered) {
      for (const p of r.priceLines) { try { p.series.removePriceLine(p.line); } catch { /* gone */ } }
      for (const b of r.bands) { try { b.series.detachPrimitive(b.band as never); } catch { /* gone */ } }
      for (const s of r.series) { try { this.chart.removeSeries(s); } catch { /* gone */ } }
    }
    this.rendered = [];
    this.markersApi?.setMarkers([]);
  }

  // ── Drawing one study ────────────────────────────────────────────────────────────────────────
  private flatten(out: IndicatorOutput): { values: Values; color: string; hist?: boolean; dashed?: boolean; colorFor?: (v: number | null, k: number) => string }[] {
    const list: { values: Values; color: string; hist?: boolean; dashed?: boolean; colorFor?: (v: number | null, k: number) => string }[] = [];
    if (out.hist) {
      const h = out.hist;
      let colorFor: (v: number | null, k: number) => string;
      if (out.palette) colorFor = out.palette;
      else if (out.macd) colorFor = (v, k) => { const prev = k > 0 ? h[k - 1] : null; const rising = prev == null || (v ?? 0) >= prev; return (v ?? 0) >= 0 ? (rising ? '#26A69A' : '#B2DFDB') : (rising ? '#FFCDD2' : '#FF5252'); };
      else colorFor = (v) => ((v ?? 0) >= 0 ? UP : DOWN);
      list.push({ values: h, color: UP, hist: true, colorFor });
    }
    for (const x of out.hists ?? []) list.push({ values: x.data, color: x.color, hist: true, colorFor: () => x.color });
    if (out.upper && out.lower) {
      list.push({ values: out.upper, color: '#F23645' });
      if (out.basis) list.push({ values: out.basis, color: '#2962FF' });
      list.push({ values: out.lower, color: '#089981' });
    }
    if (out.line) list.push({ values: out.line, color: '' });
    if (out.signal) list.push({ values: out.signal, color: SIGNAL });
    if (out.signal2) list.push({ values: out.signal2, color: out.signal2Color ?? '#787B86' });
    for (const l of out.lines ?? []) list.push({ values: l.data, color: l.color, dashed: l.dashed });
    return list;
  }

  private histData(bars: Candle[], v: Values, colorFor?: (v: number | null, k: number) => string) {
    return bars.map((b, i) => {
      const x = v[i];
      return x == null || !Number.isFinite(x)
        ? { time: b.t as UTCTimestamp }
        : { time: b.t as UTCTimestamp, value: x, color: colorFor ? colorFor(x, i) : UP };
    });
  }

  private draw(r: Rendered, out: IndicatorOutput, mainColor: string): void {
    const bars = this.bars;
    const overlay = r.pane === 0;
    const fmt = overlay ? { type: 'price' as const, precision: this.digits(), minMove: 1 / 10 ** this.digits() } : { type: 'price' as const, precision: 2, minMove: 0.01 };
    const autoscale = out.range
      ? () => ({ priceRange: { minValue: out.range![0], maxValue: out.range![1] } })
      : undefined;

    for (const item of this.flatten(out)) {
      const color = item.color || mainColor;
      let s: ISeriesApi<SeriesType>;
      if (item.hist) {
        s = this.chart.addSeries(HistogramSeries, {
          priceFormat: fmt, lastValueVisible: false, priceLineVisible: false, base: 0,
          ...(autoscale ? { autoscaleInfoProvider: autoscale } : {}),
        }, r.pane);
        s.setData(this.histData(bars, item.values, item.colorFor));
      } else {
        s = this.chart.addSeries(LineSeries, {
          color, lineWidth: 1, lineStyle: item.dashed ? LineStyle.Dashed : LineStyle.Solid, priceFormat: fmt,
          lastValueVisible: true, priceLineVisible: false, crosshairMarkerVisible: false,
          ...(autoscale ? { autoscaleInfoProvider: autoscale } : {}),
        }, r.pane);
        s.setData(lineData(bars, item.values));
      }
      r.series.push(s);
      r.colors.push(color);
      r.outputs.push(item.values);
    }

    const first = r.series[0];
    // Bands: Bollinger/Keltner/… (upper, lower) and the registry's `cloud` pairs (Ichimoku).
    if (first && out.upper && out.lower) {
      const data = bars.map((b, i) => ({ time: b.t, upper: out.upper![i] ?? null, lower: out.lower![i] ?? null }));
      const band = new BandFillPrimitive(data, 'rgba(33, 150, 243, 0.08)');
      first.attachPrimitive(band as never);
      r.bands.push({ series: first, band });
    }
    if (first && out.cloud && out.lines) {
      const [a, b] = out.cloud;
      const la = out.lines[a!]?.data;
      const lb = out.lines[b!]?.data;
      if (la && lb) {
        const ichimoku = r.study.key === 'ichimoku';
        const data = bars.map((bar, i) => ({ time: bar.t, upper: la[i] ?? null, lower: lb[i] ?? null }));
        const band = ichimoku
          ? new BandFillPrimitive(data, 'rgba(67, 160, 71, 0.18)', 'rgba(244, 67, 54, 0.18)')
          : new BandFillPrimitive(data, out.cloudColors?.[0] ?? 'rgba(33, 150, 243, 0.08)');
        first.attachPrimitive(band as never);
        r.bands.push({ series: first, band });
      }
    }
    // Guides (RSI 70/30, CCI ±100, …) and the shaded zone between two of them.
    if (first) {
      for (const g of out.guides ?? []) {
        const line = first.createPriceLine({ price: g, color: GUIDE, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: false, title: '' });
        r.priceLines.push({ series: first, line });
      }
      if (out.zone) {
        const [lo, hi] = out.zone;
        const band = new BandFillPrimitive(bars.map((b) => ({ time: b.t, upper: hi, lower: lo })), 'rgba(126, 87, 194, 0.1)');
        first.attachPrimitive(band as never);
        r.bands.push({ series: first, band });
      }
    }
    // Levels (auto Fibonacci, support/resistance, supply/demand) on the price series.
    const price = this.price();
    if (price) {
      for (const l of out.levels ?? []) {
        const line = price.createPriceLine({ price: l.price, color: l.color, lineWidth: 1, lineStyle: LineStyle.Solid, axisLabelVisible: true, title: l.label ?? '' });
        r.priceLines.push({ series: price, line });
      }
    }
    if (out.markers?.length) r.markers = out.markers.map((m) => ({ ...m, time: m.time as UTCTimestamp }));
  }

  private applyMarkers(): void {
    const price = this.price();
    if (!price) return;
    const all = this.rendered.flatMap((r) => (r.study.hidden ? [] : r.markers)).sort((a, b) => (a.time as number) - (b.time as number));
    if (!this.markersApi) this.markersApi = createSeriesMarkers(price, all);
    else this.markersApi.setMarkers(all);
  }

  /** The price series was replaced (a chart-type change): markers and levels follow it. */
  resetPriceSeries(): void { this.markersApi = null; }
}

/** TradingView-style short name for the legend: the Latin abbreviation in the label, else the label. */
export function shortName(key: string, label: string): string {
  const m = /\(([A-Za-z0-9%± .\-]+)\)/.exec(label);
  if (m) return m[1]!.trim();
  if (/^[A-Za-z0-9 %]+$/.test(label)) return label;
  const known: Record<string, string> = { bb: 'BB', ichimoku: 'Ichimoku', supertrend: 'Supertrend', stoch: 'Stoch', willr: '%R', donchian: 'DC', keltner: 'KC', psar: 'SAR', alligator: 'Alligator', pivots: 'Pivots' };
  return known[key] ?? key.toUpperCase();
}
