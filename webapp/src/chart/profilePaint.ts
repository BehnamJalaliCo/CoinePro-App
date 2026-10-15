// Volume profiles on the chart, as TradingView draws them: horizontal rows of up (blue) and down
// (yellow) volume per price, the value area brighter, the point of control as a red line, and the
// value area's high and low. A profile either hangs off the pane's right edge (visible range) or
// sits inside the time range it covers (session, periodic, fixed range, anchored).
import type { IChartApi, ISeriesApi, ISeriesPrimitive, SeriesAttachedParameter, SeriesType, Time, UTCTimestamp } from 'lightweight-charts';
import type { ProfileSegment } from '../engine/indicators.js';

type Ctx = CanvasRenderingContext2D;
export interface ProfileColors { up: string; down: string; upVa: string; downVa: string; poc: string; edge: string }
export const PROFILE_COLORS: ProfileColors = {
  up: 'rgba(33, 150, 243, 0.3)', down: 'rgba(251, 192, 45, 0.3)', upVa: 'rgba(33, 150, 243, 0.7)', downVa: 'rgba(251, 192, 45, 0.7)', poc: '#F23645', edge: 'rgba(33, 150, 243, 0.5)',
};

export class ProfilePaint implements ISeriesPrimitive<Time> {
  private chart: IChartApi | null = null;
  private series: ISeriesApi<SeriesType> | null = null;
  private request: (() => void) | null = null;
  private readonly views: unknown[];
  constructor(private segments: ProfileSegment[], private place: 'right' | 'inside', private colors: ProfileColors = PROFILE_COLORS, private widthShare = 0.3) {
    this.views = [{ zOrder: () => 'bottom', renderer: () => ({ draw: (t: { useMediaCoordinateSpace(f: (s: { context: Ctx; mediaSize: { width: number; height: number } }) => void): void }) => t.useMediaCoordinateSpace(({ context, mediaSize }) => this.paint(context, mediaSize.width)) }) }];
  }
  attached(p: SeriesAttachedParameter<Time>): void { this.chart = p.chart as IChartApi; this.series = p.series as ISeriesApi<SeriesType>; this.request = p.requestUpdate; }
  detached(): void { this.chart = null; this.series = null; this.request = null; }
  updateAllViews(): void { /* coordinates are read at draw time */ }
  paneViews() { return this.views as never; }
  setData(segments: ProfileSegment[]): void { this.segments = segments; this.request?.(); }

  private paint(ctx: Ctx, W: number): void {
    const ts = this.chart?.timeScale(); const s = this.series;
    if (!ts || !s) return;
    try {
      for (const seg of this.segments) {
        const max = Math.max(...seg.rows.map((r) => r.up + r.down)) || 1;
        let x0: number; let width: number; let dir = 1;
        if (this.place === 'right') { width = W * this.widthShare; x0 = W; dir = -1; }
        else {
          const a = ts.timeToCoordinate(seg.from as UTCTimestamp); const b = ts.timeToCoordinate(seg.to as UTCTimestamp);
          if (a == null || b == null || b < 0 || a > W) continue;
          x0 = a; width = Math.max(4, (b - a) * 0.9);
        }
        for (const r of seg.rows) {
          const y1 = s.priceToCoordinate(r.hi); const y2 = s.priceToCoordinate(r.lo);
          if (y1 == null || y2 == null) continue;
          const h = Math.max(1, Math.abs(y2 - y1) - 1); const y = Math.min(y1, y2) + 0.5;
          const wu = (r.up / max) * width; const wd = (r.down / max) * width;
          ctx.fillStyle = r.va ? this.colors.upVa : this.colors.up;
          ctx.fillRect(dir > 0 ? x0 : x0 - wu, y, wu, h);
          ctx.fillStyle = r.va ? this.colors.downVa : this.colors.down;
          ctx.fillRect(dir > 0 ? x0 + wu : x0 - wu - wd, y, wd, h);
        }
        const line = (price: number, color: string, w: number, dash: number[]) => {
          const y = s.priceToCoordinate(price); if (y == null) return;
          ctx.strokeStyle = color; ctx.lineWidth = w; ctx.setLineDash(dash);
          ctx.beginPath();
          if (this.place === 'right') { ctx.moveTo(W - width, y); ctx.lineTo(W, y); }
          else { const b = ts.timeToCoordinate(seg.to as UTCTimestamp) ?? x0 + width; ctx.moveTo(x0, y); ctx.lineTo(b, y); }
          ctx.stroke(); ctx.setLineDash([]);
        };
        line(seg.poc, this.colors.poc, 1.5, []);
        line(seg.vah, this.colors.edge, 1, [3, 3]);
        line(seg.val, this.colors.edge, 1, [3, 3]);
      }
    } catch { /* never break the chart */ }
  }
}
