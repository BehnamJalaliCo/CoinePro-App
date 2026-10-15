// Puts a NamaScript run on the chart. Plots become line series — on the price when their values
// sit in the price's range, in a pane of their own when they do not (an RSI, a score) — as
// TradingView decides from `overlay`. Horizontal lines become price lines, shapes become markers,
// and everything drawn freely (backgrounds, fills, boxes, lines, labels, tables) is painted by the
// drawing layer, which already knows how (engine/drawings.js _paintScript).
import {
  LineSeries, LineStyle, createSeriesMarkers,
  type IChartApi, type IPriceLine, type ISeriesApi, type ISeriesMarkersPluginApi, type SeriesMarker, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { ScriptResult } from '../engine/namascript.js';
import type { DrawingLayer } from '../engine/drawings.js';
import type { Candle } from '../data/types';

export class ScriptRenderer {
  private series: ISeriesApi<SeriesType>[] = [];
  private lines: { s: ISeriesApi<SeriesType>; l: IPriceLine }[] = [];
  private markers: ISeriesMarkersPluginApi<Time> | null = null;
  overlay = true;

  constructor(private chart: IChartApi, private price: () => ISeriesApi<SeriesType> | null, private layer: () => DrawingLayer | null, private firstFreePane: () => number) {}

  draw(r: ScriptResult | null, bars: Candle[]): void {
    this.clear();
    if (!r || !r.ok) return;
    const plots = r.plots ?? [];
    // Overlay when the plots live in the price's range.
    let lo = Infinity, hi = -Infinity;
    for (const b of bars) { if (b.l < lo) lo = b.l; if (b.h > hi) hi = b.h; }
    const values = plots.flatMap((p) => p.data.map((d) => d.value)).filter(Number.isFinite).sort((a, b) => a - b);
    const median = values.length ? values[Math.floor(values.length / 2)]! : (lo + hi) / 2;
    const span = hi - lo || Math.abs(hi) || 1;
    this.overlay = !values.length || (median >= lo - span && median <= hi + span);
    const pane = this.overlay ? 0 : this.firstFreePane();

    for (const p of plots) {
      const s = this.chart.addSeries(LineSeries, {
        color: p.color, lineWidth: Math.max(1, Math.min(4, p.width || 1)) as 1 | 2 | 3 | 4, priceLineVisible: false,
        lastValueVisible: true, crosshairMarkerVisible: false, lineStyle: p.style === 2 ? LineStyle.Dashed : LineStyle.Solid,
      }, pane);
      s.setData(p.data.map((d) => ({ time: d.time as UTCTimestamp, value: d.value })));
      this.series.push(s);
    }
    const host = this.overlay ? this.price() : this.series[0] ?? null;
    if (host) {
      for (const h of r.hlines ?? []) {
        this.lines.push({ s: host, l: host.createPriceLine({ price: h.price, color: h.color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: h.title }) });
      }
    }
    // Shapes and the strategy's trades as markers on the price.
    const price = this.price();
    if (price) {
      const ms: SeriesMarker<Time>[] = [];
      for (const sh of r.shapes ?? []) {
        for (const pt of sh.points) {
          const down = pt.shape === 'down';
          ms.push({ time: pt.time as UTCTimestamp, position: down ? 'aboveBar' : pt.shape === 'circle' ? 'inBar' : 'belowBar', shape: down ? 'arrowDown' : pt.shape === 'circle' ? 'circle' : 'arrowUp', color: sh.color });
        }
      }
      for (const l of r.labels ?? []) ms.push({ time: l.time as UTCTimestamp, position: 'aboveBar', shape: 'square', color: l.color, text: l.text });
      ms.sort((a, b) => (a.time as number) - (b.time as number));
      this.markers = createSeriesMarkers(price, ms);
    }
    if (this.overlay) this.layer()?.setScriptPaint?.(r);
  }

  clear(): void {
    for (const { s, l } of this.lines) { try { s.removePriceLine(l); } catch { /* gone */ } }
    for (const s of this.series) { try { this.chart.removeSeries(s); } catch { /* gone */ } }
    this.markers?.setMarkers([]);
    this.markers?.detach?.();
    this.markers = null;
    this.series = [];
    this.lines = [];
    this.layer()?.setScriptPaint?.(null);
  }
}
