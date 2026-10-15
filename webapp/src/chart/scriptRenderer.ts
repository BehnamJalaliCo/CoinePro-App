// Puts a NamaScript run on the chart. The script is listed with the indicators (a pseudo-study,
// state/studies.ts SCRIPT), so the study renderer (chart/studies.ts) lays it out with them: plots
// become series — on the price when their values sit in the price's range, in a pane of their own
// when they do not (an RSI, a score) — horizontal lines become price lines, shapes and labels
// become markers with their text, `plotcandle`/`plotbar` become candle or bar series, and what is
// painted freely (backgrounds, fills, boxes, lines, bar colours, risk/reward zones) is drawn by
// ScriptPaint, a series primitive. Tables are an HTML overlay (shell/ScriptTables.tsx).
//
// `ScriptRenderer` is the chart view's first runner, kept for its constructor and calls; the chart
// view now always hands it nothing (useScript.applied stays false), and the study renderer draws.
import {
  LineSeries, LineStyle, createSeriesMarkers,
  type IChartApi, type IPriceLine, type ISeriesApi, type ISeriesMarkersPluginApi, type ISeriesPrimitive, type SeriesAttachedParameter, type SeriesMarker, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { ScriptResult } from '../engine/namascript.js';
import type { DrawingLayer } from '../engine/drawings.js';
import type { Candle } from '../data/types';

export interface PaintData {
  bgs?: { color?: string; bars?: number[] }[];
  fills?: { color?: string; a?: { time: number; value: number }[]; b?: { time: number; value: number }[] }[];
  boxes?: { left: number; right: number; top: number; bottom: number; color?: string; bg?: string }[];
  lines?: { x1: number; x2: number; y1: number; y2: number; color?: string; width?: number; style?: number }[];
  barcolors?: { color?: string; bars?: number[]; all?: boolean }[];
  zones?: { entry: number; sl: number; tps?: number[]; tp?: number; fromTime: number; toTime: number }[];
}

type Ctx = CanvasRenderingContext2D;

/** Free painting for a script: drawn under the series of the pane it is attached to. */
export class ScriptPaint implements ISeriesPrimitive<Time> {
  private chart: IChartApi | null = null;
  private series: ISeriesApi<SeriesType> | null = null;
  private request: (() => void) | null = null;
  private readonly views: { zOrder(): 'bottom'; renderer(): { draw(t: { useMediaCoordinateSpace(f: (s: { context: Ctx; mediaSize: { width: number; height: number } }) => void): void }): void } }[];

  constructor(private data: PaintData) {
    this.views = [{ zOrder: () => 'bottom', renderer: () => ({ draw: (t) => t.useMediaCoordinateSpace(({ context, mediaSize }) => this.paint(context, mediaSize.width, mediaSize.height)) }) }];
  }
  attached(p: SeriesAttachedParameter<Time>): void { this.chart = p.chart as IChartApi; this.series = p.series as ISeriesApi<SeriesType>; this.request = p.requestUpdate; }
  detached(): void { this.chart = null; this.series = null; this.request = null; }
  updateAllViews(): void { /* reads the coordinates at draw time */ }
  paneViews() { return this.views as never; }
  setData(d: PaintData): void { this.data = d; this.request?.(); }

  private x(t: number): number | null { const v = this.chart?.timeScale().timeToCoordinate(t as UTCTimestamp); return v == null ? null : v; }
  private y(p: number): number | null { const v = this.series?.priceToCoordinate(p); return v == null ? null : v; }

  private paint(ctx: Ctx, W: number, H: number): void {
    try {
      const d = this.data;
      const bw = Math.max(1, this.chart?.timeScale().options().barSpacing ?? 6);
      for (const g of d.bgs ?? []) {
        ctx.fillStyle = g.color || 'rgba(59,130,246,.10)';
        for (const t of g.bars ?? []) { const x = this.x(t); if (x != null) ctx.fillRect(x - bw / 2, 0, bw, H); }
      }
      for (const f of d.fills ?? []) {
        const A = f.a ?? []; const B = f.b ?? [];
        if (A.length < 2 || B.length < 2) continue;
        ctx.beginPath(); let started = false;
        for (const p of A) { const x = this.x(p.time); const y = this.y(p.value); if (x == null || y == null) continue; if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y); }
        for (let i = B.length - 1; i >= 0; i--) { const x = this.x(B[i]!.time); const y = this.y(B[i]!.value); if (x == null || y == null) continue; ctx.lineTo(x, y); }
        ctx.closePath(); ctx.fillStyle = f.color || 'rgba(59,130,246,.12)'; ctx.fill();
      }
      for (const b of d.boxes ?? []) {
        const x1 = this.x(b.left); const x2 = this.x(b.right); const y1 = this.y(b.top); const y2 = this.y(b.bottom);
        if (x1 == null || x2 == null || y1 == null || y2 == null) continue;
        ctx.fillStyle = b.bg || 'rgba(59,130,246,.08)'; ctx.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        ctx.strokeStyle = b.color || '#3b82f6'; ctx.lineWidth = 1; ctx.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
      }
      for (const l of d.lines ?? []) {
        const x1 = this.x(l.x1); const x2 = this.x(l.x2); const y1 = this.y(l.y1); const y2 = this.y(l.y2);
        if (x1 == null || x2 == null || y1 == null || y2 == null) continue;
        ctx.strokeStyle = l.color || '#3b82f6'; ctx.lineWidth = l.width || 1; ctx.setLineDash(l.style === 2 ? [5, 3] : []);
        ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke(); ctx.setLineDash([]);
      }
      for (const bc of d.barcolors ?? []) {
        if (bc.all) continue;
        ctx.fillStyle = bc.color || '#f59e0b';
        for (const t of bc.bars ?? []) { const x = this.x(t); if (x == null) continue; ctx.beginPath(); ctx.arc(x, H - 8, 2.5, 0, 7); ctx.fill(); }
      }
      // Risk/reward zones, as TradingView's long/short position: green to the targets, red to the stop,
      // drawn forward from the last bar.
      for (const z of d.zones ?? []) {
        const x0 = this.x(z.toTime); const ye = this.y(z.entry); const ys = this.y(z.sl);
        if (x0 == null || ye == null || ys == null) continue;
        const x1 = Math.min(W, x0 + bw * 30);
        const tps = z.tps?.length ? z.tps : z.tp != null ? [z.tp] : [];
        ctx.fillStyle = 'rgba(242,54,69,0.16)'; ctx.fillRect(x0, Math.min(ye, ys), x1 - x0, Math.abs(ys - ye));
        tps.forEach((tp, i) => {
          const yt = this.y(tp); if (yt == null) return;
          const from = i === 0 ? ye : this.y(tps[i - 1]!) ?? ye;
          ctx.fillStyle = `rgba(8,153,129,${0.16 - i * 0.03})`; ctx.fillRect(x0, Math.min(from, yt), x1 - x0, Math.abs(yt - from));
          ctx.strokeStyle = '#089981'; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(x0, yt); ctx.lineTo(x1, yt); ctx.stroke();
          ctx.fillStyle = '#089981'; ctx.font = '11px IRANYekanX, sans-serif'; ctx.fillText(`TP${i + 1}`, x0 + 4, yt - 3);
        });
        ctx.strokeStyle = '#787B86'; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(x0, ye); ctx.lineTo(x1, ye); ctx.stroke();
        ctx.strokeStyle = '#F23645'; ctx.beginPath(); ctx.moveTo(x0, ys); ctx.lineTo(x1, ys); ctx.stroke();
        ctx.fillStyle = '#F23645'; ctx.font = '11px IRANYekanX, sans-serif'; ctx.fillText('SL', x0 + 4, ys + 12);
      }
    } catch { /* a bad shape draws nothing, never breaks the chart */ }
  }
}

/** Crosses at a series' points (TradingView's "Cross" plot style). */
export class CrossPaint implements ISeriesPrimitive<Time> {
  private chart: IChartApi | null = null;
  private series: ISeriesApi<SeriesType> | null = null;
  private request: (() => void) | null = null;
  private readonly views: unknown[];
  constructor(private points: { time: number; value: number }[], private color: string, private size: number) {
    this.views = [{ zOrder: () => 'normal', renderer: () => ({ draw: (t: { useMediaCoordinateSpace(f: (s: { context: Ctx }) => void): void }) => t.useMediaCoordinateSpace(({ context }) => this.paint(context)) }) }];
  }
  attached(p: SeriesAttachedParameter<Time>): void { this.chart = p.chart as IChartApi; this.series = p.series as ISeriesApi<SeriesType>; this.request = p.requestUpdate; }
  detached(): void { this.chart = null; this.series = null; }
  updateAllViews(): void { /* coordinates are read at draw time */ }
  paneViews() { return this.views as never; }
  setData(points: { time: number; value: number }[]): void { this.points = points; this.request?.(); }
  private paint(ctx: Ctx): void {
    const ts = this.chart?.timeScale(); const s = this.series;
    if (!ts || !s) return;
    const r = 2 + this.size;
    ctx.strokeStyle = this.color; ctx.lineWidth = Math.max(1, this.size / 2);
    ctx.beginPath();
    for (const p of this.points) {
      const x = ts.timeToCoordinate(p.time as UTCTimestamp); const y = s.priceToCoordinate(p.value);
      if (x == null || y == null) continue;
      ctx.moveTo(x - r, y); ctx.lineTo(x + r, y); ctx.moveTo(x, y - r); ctx.lineTo(x, y + r);
    }
    ctx.stroke();
  }
}

/** Whether a script's plots belong on the price (their median sits in the bars' range). */
export function scriptIsOverlay(r: ScriptResult, bars: Candle[]): boolean {
  const plots = r.plots ?? [];
  let lo = Infinity, hi = -Infinity;
  for (const b of bars) { if (b.l < lo) lo = b.l; if (b.h > hi) hi = b.h; }
  const values = plots.flatMap((p) => p.data.map((d) => d.value)).filter(Number.isFinite).sort((a, b) => a - b);
  if (!values.length) return true;
  const median = values[Math.floor(values.length / 2)]!;
  const span = hi - lo || Math.abs(hi) || 1;
  return median >= lo - span && median <= hi + span;
}

/** A script's shapes and labels as series markers, each with its text. */
export function scriptMarkers(r: ScriptResult): SeriesMarker<Time>[] {
  const ms: SeriesMarker<Time>[] = [];
  for (const sh of r.shapes ?? []) {
    for (const pt of sh.points) {
      const down = pt.shape === 'down';
      const circle = pt.shape === 'circle' || pt.shape === 'cross' || pt.shape === 'flag';
      ms.push({ time: pt.time as UTCTimestamp, position: down ? 'aboveBar' : circle ? 'inBar' : 'belowBar', shape: down ? 'arrowDown' : circle ? 'circle' : 'arrowUp', color: sh.color, text: sh.name && !/^shape$/.test(sh.name) ? sh.name : undefined });
    }
  }
  for (const l of r.labels ?? []) ms.push({ time: l.time as UTCTimestamp, position: 'aboveBar', shape: 'square', color: l.color, text: l.text });
  return ms;
}

export class ScriptRenderer {
  private series: ISeriesApi<SeriesType>[] = [];
  private lines: { s: ISeriesApi<SeriesType>; l: IPriceLine }[] = [];
  private markers: ISeriesMarkersPluginApi<Time> | null = null;
  overlay = true;

  constructor(private chart: IChartApi, private price: () => ISeriesApi<SeriesType> | null, private layer: () => DrawingLayer | null, private firstFreePane: () => number) {}

  draw(r: ScriptResult | null, bars: Candle[]): void {
    this.clear();
    if (!r || !r.ok) return;
    this.overlay = scriptIsOverlay(r, bars);
    const pane = this.overlay ? 0 : this.firstFreePane();
    for (const p of r.plots ?? []) {
      const s = this.chart.addSeries(LineSeries, {
        color: p.color, lineWidth: Math.max(1, Math.min(4, p.width || 1)) as 1 | 2 | 3 | 4, priceLineVisible: false,
        lastValueVisible: true, crosshairMarkerVisible: false, lineStyle: p.style === 2 ? LineStyle.Dashed : LineStyle.Solid,
      }, pane);
      s.setData(p.data.map((d) => ({ time: d.time as UTCTimestamp, value: d.value })));
      this.series.push(s);
    }
    const host = this.overlay ? this.price() : this.series[0] ?? null;
    if (host) for (const h of r.hlines ?? []) this.lines.push({ s: host, l: host.createPriceLine({ price: h.price, color: h.color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: h.title }) });
    const price = this.price();
    if (price) this.markers = createSeriesMarkers(price, scriptMarkers(r).sort((a, b) => (a.time as number) - (b.time as number)));
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
