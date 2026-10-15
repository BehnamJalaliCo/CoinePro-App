// TradingView's Zoom in (drag a rectangle: the chart shows that span of bars and that band of
// prices) and Zoom out (one step back per zoom in), and Alt+R's reset of the chart view.
import { useChartBus } from './chartBus';
import { useDrawing } from '../state/drawing';

interface View { from: number; to: number; price: { from: number; to: number } | null; auto: boolean }
const stack: View[] = [];

const setDepth = () => useDrawing.getState().setZoomDepth(stack.length);

/** Zooms the chart to a rectangle drawn on it (pixels relative to the chart). */
export function zoomToBox(box: { x0: number; y0: number; x1: number; y1: number }): boolean {
  const { api, series } = useChartBus.getState();
  if (!api || !series) return false;
  const ts = api.timeScale();
  const ps = api.priceScale('right');
  const lr = ts.getVisibleLogicalRange();
  if (!lr) return false;
  const a = ts.coordinateToLogical(box.x0), b = ts.coordinateToLogical(box.x1);
  const pa = series.coordinateToPrice(box.y0), pb = series.coordinateToPrice(box.y1);
  if (a == null || b == null || pa == null || pb == null || b - a < 1) return false;
  stack.push({ from: lr.from, to: lr.to, price: ps.getVisibleRange(), auto: ps.options().autoScale !== false });
  ts.setVisibleLogicalRange({ from: a, to: b });
  ps.setVisibleRange({ from: Math.min(pa, pb), to: Math.max(pa, pb) });
  useChartBus.getState().setAutoScale(false);
  setDepth();
  return true;
}

/** One zoom in undone. False when there is nothing to undo. */
export function zoomOut(): boolean {
  const v = stack.pop();
  setDepth();
  const { api } = useChartBus.getState();
  if (!v || !api) return false;
  api.timeScale().setVisibleLogicalRange({ from: v.from, to: v.to });
  const ps = api.priceScale('right');
  if (v.auto || !v.price) { ps.setAutoScale(true); useChartBus.getState().setAutoScale(true); }
  else ps.setVisibleRange(v.price);
  return true;
}

/** Alt+R: TradingView's reset chart — the default bar spacing at the last bar, the price scale on auto. */
export function resetChartView(): void {
  stack.length = 0;
  setDepth();
  const { api } = useChartBus.getState();
  if (!api) return;
  api.timeScale().resetTimeScale();
  api.priceScale('right').setAutoScale(true);
  api.priceScale('right').applyOptions({ autoScale: true });
  useChartBus.getState().setAutoScale(true);
}

/** The zoom history belongs to one symbol and interval. */
export function clearZoom(): void { stack.length = 0; setDepth(); }
