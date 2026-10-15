// Series primitives the chart draws itself, inside lightweight-charts' own canvases (so a snapshot
// carries them): Kagi's thick and thin right-angle line, Point & figure's X and O columns, and the
// countdown under the last price on the axis, as TradingView draws all three.
import type {
  IChartApiBase, IPrimitivePaneRenderer, IPrimitivePaneView, ISeriesApi, ISeriesPrimitive, ISeriesPrimitiveAxisView,
  SeriesAttachedParameter, SeriesType, Time,
} from 'lightweight-charts';
import type { CanvasRenderingTarget2D } from 'fancy-canvas';

type Chart = IChartApiBase<Time>;
type Series = ISeriesApi<SeriesType, Time>;

abstract class Base implements ISeriesPrimitive<Time> {
  protected chart: Chart | null = null;
  protected series: Series | null = null;
  private request: (() => void) | null = null;
  attached(p: SeriesAttachedParameter<Time>): void { this.chart = p.chart; this.series = p.series as Series; this.request = p.requestUpdate; }
  detached(): void { this.chart = null; this.series = null; this.request = null; }
  protected update(): void { try { this.request?.(); } catch { /* detached */ } }
}

class View implements IPrimitivePaneView {
  constructor(private readonly draw: (target: CanvasRenderingTarget2D) => void, private readonly z: 'bottom' | 'normal' | 'top' = 'normal') {}
  zOrder() { return this.z; }
  renderer(): IPrimitivePaneRenderer { return { draw: (t) => { try { this.draw(t); } catch { /* never break the chart */ } } }; }
}

// ── Kagi ──────────────────────────────────────────────────────────────────────────────────────────
export interface KagiPoint { time: number; value: number }
export interface KagiStyle { yang: string; yin: string; yangWidth: number; yinWidth: number }

/** The Kagi line as runs of one thickness: yang (thick) once price passes the last shoulder,
 *  yin (thin) once it falls through the last waist; the change happens at that very price. */
export function kagiRuns(points: KagiPoint[]): { thick: boolean; path: { i: number; v: number; vertical: boolean }[] }[] {
  const runs: { thick: boolean; path: { i: number; v: number; vertical: boolean }[] }[] = [];
  if (points.length < 2) return runs;
  let thick = points[1]!.value > points[0]!.value;
  let shoulder = -Infinity; let waist = Infinity;
  let cur = { thick, path: [{ i: 0, v: points[0]!.value, vertical: false }] };
  const flip = (at: number, i: number) => {
    cur.path.push({ i, v: at, vertical: true });
    runs.push(cur);
    thick = !thick;
    cur = { thick, path: [{ i, v: at, vertical: false }] };
  };
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]!.value; const b = points[i]!.value;
    // The shoulder or waist the line walks along to the next column.
    cur.path.push({ i, v: a, vertical: false });
    if (b > a) {
      if (!thick && Number.isFinite(shoulder) && b > shoulder) flip(shoulder, i);
      waist = a;
    } else if (b < a) {
      if (thick && Number.isFinite(waist) && b < waist) flip(waist, i);
      shoulder = a;
    }
    cur.path.push({ i, v: b, vertical: true });
  }
  runs.push(cur);
  return runs;
}

export class KagiPrimitive extends Base {
  private points: KagiPoint[] = [];
  private runs: ReturnType<typeof kagiRuns> = [];
  constructor(private style: KagiStyle) { super(); }
  setData(points: KagiPoint[], style?: KagiStyle): void { this.points = points; this.runs = kagiRuns(points); if (style) this.style = style; this.update(); }
  private readonly views = [new View((target) => this.paint(target))];
  paneViews() { return this.views; }
  private paint(target: CanvasRenderingTarget2D): void {
    const chart = this.chart; const series = this.series;
    if (!chart || !series || this.points.length < 2) return;
    const ts = chart.timeScale();
    const xs = this.points.map((p) => ts.timeToCoordinate(p.time as Time));
    target.useMediaCoordinateSpace(({ context: ctx }) => {
      ctx.lineJoin = 'miter'; ctx.lineCap = 'square';
      for (const run of this.runs) {
        ctx.beginPath();
        let started = false;
        for (const p of run.path) {
          const x = xs[p.i]; const y = series.priceToCoordinate(p.v);
          if (x == null || y == null) { started = false; continue; }
          if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y);
        }
        ctx.strokeStyle = run.thick ? this.style.yang : this.style.yin;
        ctx.lineWidth = run.thick ? this.style.yangWidth : this.style.yinWidth;
        ctx.stroke();
      }
    });
  }
}

// ── Point & figure ────────────────────────────────────────────────────────────────────────────────
export interface PnfColumn { time: number; dir: 1 | -1; lo: number; hi: number }

export class PnfPrimitive extends Base {
  private cols: PnfColumn[] = [];
  private box = 1;
  constructor(private up: string, private down: string) { super(); }
  setData(cols: PnfColumn[], box: number, up?: string, down?: string): void { this.cols = cols; this.box = box || 1; if (up) this.up = up; if (down) this.down = down; this.update(); }
  /** The visible columns' full extent, for the host series' auto-scale. */
  range(from: number, to: number): { minValue: number; maxValue: number } | null {
    let lo = Infinity; let hi = -Infinity;
    for (let i = Math.max(0, Math.floor(from)); i <= Math.min(this.cols.length - 1, Math.ceil(to)); i++) {
      const c = this.cols[i]!; lo = Math.min(lo, c.lo - this.box / 2); hi = Math.max(hi, c.hi + this.box / 2);
    }
    return Number.isFinite(lo) ? { minValue: lo, maxValue: hi } : null;
  }
  private readonly views = [new View((target) => this.paint(target))];
  paneViews() { return this.views; }
  private paint(target: CanvasRenderingTarget2D): void {
    const chart = this.chart; const series = this.series;
    if (!chart || !series || !this.cols.length) return;
    const ts = chart.timeScale();
    const spacing = ts.options().barSpacing;
    target.useMediaCoordinateSpace(({ context: ctx }) => {
      const y0 = series.priceToCoordinate(this.cols[0]!.lo); const y1 = series.priceToCoordinate(this.cols[0]!.lo + this.box);
      const boxPx = y0 != null && y1 != null ? Math.abs(y1 - y0) : spacing;
      const size = Math.max(2, Math.min(spacing * 0.8, boxPx * 0.85));
      ctx.lineWidth = Math.max(1, Math.min(2, size / 6));
      for (const c of this.cols) {
        const x = ts.timeToCoordinate(c.time as Time);
        if (x == null) continue;
        ctx.strokeStyle = c.dir > 0 ? this.up : this.down;
        ctx.beginPath();
        for (let lvl = c.lo; lvl <= c.hi + this.box * 1e-6; lvl += this.box) {
          const y = series.priceToCoordinate(lvl);
          if (y == null) continue;
          const h = size / 2;
          if (c.dir > 0) { ctx.moveTo(x - h, y - h); ctx.lineTo(x + h, y + h); ctx.moveTo(x + h, y - h); ctx.lineTo(x - h, y + h); }
          else { ctx.moveTo(x + h, y); ctx.ellipse(x, y, h, h, 0, 0, Math.PI * 2); }
        }
        ctx.stroke();
      }
    });
  }
}

// ── The last price's label and the countdown under it ─────────────────────────────────────────────
/** The countdown under the last price on the axis (and, for a series whose own label would be
 *  invisible — hollow bodies, hidden bodies — the last price label itself). */
export class CountdownPrimitive extends Base {
  private text = '';
  private label = '';
  private price: number | null = null;
  private back = '#089981';
  private labelBack = '#089981';
  private fore = '#FFFFFF';
  private fontSize = 12;
  private readonly countdown: ISeriesPrimitiveAxisView = {
    coordinate: () => -1000,
    fixedCoordinate: () => {
      const series = this.series;
      if (!series || this.price == null) return undefined;
      const y = series.priceToCoordinate(this.price);
      // One label's height lower: lightweight-charts' label is the font size plus 2.5/12 of it above and below.
      return y == null ? undefined : y + this.fontSize * (1 + 5 / 12);
    },
    text: () => this.text,
    textColor: () => this.fore,
    backColor: () => this.back,
    visible: () => Boolean(this.text) && this.price != null,
    tickVisible: () => false,
  };
  private readonly last: ISeriesPrimitiveAxisView = {
    coordinate: () => -1000,
    fixedCoordinate: () => {
      const series = this.series;
      if (!series || this.price == null) return undefined;
      const y = series.priceToCoordinate(this.price);
      return y == null ? undefined : y;
    },
    text: () => this.label,
    textColor: () => this.fore,
    backColor: () => this.labelBack,
    visible: () => Boolean(this.label) && this.price != null,
  };
  private readonly views = [this.last, this.countdown];
  set(o: { text: string; price: number | null; back: string; fore?: string; fontSize?: number; label?: string; labelBack?: string }): void {
    this.text = o.text; this.price = o.price; this.back = o.back; this.fore = o.fore ?? '#FFFFFF'; this.fontSize = o.fontSize ?? 12;
    this.label = o.label ?? ''; this.labelBack = o.labelBack ?? o.back;
    this.update();
  }
  priceAxisViews() { return this.views; }
}
