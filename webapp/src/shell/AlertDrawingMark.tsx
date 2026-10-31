// The drawing an alert watches, on the alert's row (OLD feature/alerts AlertDrawingSketch.kt and
// AlertCenterScreen OrphanPill).
//
// Where the drawing is still on the chart: the line drawn small — a polyline through its stored
// anchors, time along x and price up y, each scaled to what the anchors span (a span of zero is
// centred, which is what a horizontal line is; one anchor is a level across the middle, not a dot).
// Not a render of the chart: it answers «which of my three trend lines is this».
//
// Where the drawing is gone: «ترسیمش نیست» in the refusal colour. The alert is armed and can never
// fire, because the line it measures against no longer exists; it is not the reader's decision, so
// it is not the muted grey of «متوقف».
import { useChartBus } from '../chart/chartBus';
import { anchorsOf, findDrawing, type Pt } from '../state/drawingLinks';
import { useL } from '../pages/kit';
import type { PriceAlert } from '../state/alerts';

const SIZE = 34;
const INSET = 6;
const STROKE = 2;

/** The sketch's polyline in a SIZE box (exported for the unit tests). */
export function sketchPath(points: Pt[], size = SIZE, inset = INSET, stroke = STROKE): string {
  const finite = points.filter((p) => Number.isFinite(p.p));
  const w = size - inset * 2, h = size - inset * 2;
  if (!finite.length) return '';
  if (finite.length === 1) return `M${inset + stroke} ${inset + h / 2}L${inset + w - stroke} ${inset + h / 2}`;
  const ts = finite.map((p) => p.t), ps = finite.map((p) => p.p);
  const t0 = Math.min(...ts), tSpan = Math.max(...ts) - t0;
  const p0 = Math.min(...ps), pSpan = Math.max(...ps) - p0;
  const uw = w - stroke * 2, uh = h - stroke * 2;
  const at = (p: Pt) => {
    const x = tSpan === 0 ? 0.5 : (p.t - t0) / tSpan;
    // Price up: the box's y grows downwards and a chart's does not.
    const y = pSpan === 0 ? 0.5 : 1 - (p.p - p0) / pSpan;
    return `${(inset + stroke + x * uw).toFixed(1)} ${(inset + stroke + y * uh).toFixed(1)}`;
  };
  return finite.map((p, i) => `${i ? 'L' : 'M'}${at(p)}`).join('');
}

/** The mark beside a drawing alert: its sketch, or the «drawing gone» pill. Nothing for other alerts. */
export function AlertDrawingMark({ a }: { a: Pick<PriceAlert, 'symbol' | 'drawingId'> }) {
  const L = useL();
  useChartBus((s) => s.drawingVersion);
  if (!a.drawingId) return null;
  const d = findDrawing(a.symbol, a.drawingId);
  if (d === null) return null;
  if (!d) return <span className="badge orphan fa" data-orphan>{L('ترسیمش نیست', 'Drawing gone')}</span>;
  const path = sketchPath(anchorsOf(d));
  if (!path) return null;
  return (
    <svg className="alert-sketch" width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} aria-hidden="true" data-sketch>
      <rect x="0.5" y="0.5" width={SIZE - 1} height={SIZE - 1} rx="5" />
      <path d={path} />
    </svg>
  );
}
