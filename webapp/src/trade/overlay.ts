// Shared plumbing for what trading draws over the chart: where the main pane is, and one animation
// frame loop that places HTML labels on their prices while the chart scrolls, zooms and rescales
// (lightweight-charts has no event for a price-scale change, so the labels follow by frame).
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';

/** The chart's frame (`.ws-chart`), which the labels are positioned in. */
export function chartFrame(api: IChartApi | null): HTMLElement | null {
  try { return (api?.chartElement().closest('.ws-chart') as HTMLElement | null) ?? null; } catch { return null; }
}

export interface PaneBox { width: number; height: number; top: number; left: number }

/** The main pane's plotting area inside the frame (price scale excluded). */
export function paneBox(api: IChartApi, frame: HTMLElement): PaneBox | null {
  try {
    const pane = api.panes()[0];
    const el = pane?.getHTMLElement();
    const f = frame.getBoundingClientRect();
    const r = el?.getBoundingClientRect();
    const width = api.timeScale().width();
    if (!r || !width) return null;
    return { width, height: pane!.getHeight(), top: r.top - f.top, left: r.left - f.left };
  } catch { return null; }
}

export function yOf(series: ISeriesApi<SeriesType>, price: number): number | null {
  try { const y = series.priceToCoordinate(price); return y == null ? null : Number(y); } catch { return null; }
}

/** Runs `fn` every frame while the page is visible; returns the stop. */
export function frameLoop(fn: () => void): () => void {
  let id = 0;
  let alive = true;
  const run = () => { if (!alive) return; if (document.visibilityState !== 'hidden') fn(); id = requestAnimationFrame(run); };
  id = requestAnimationFrame(run);
  return () => { alive = false; cancelAnimationFrame(id); };
}

/** Snaps a price to the instrument's precision. */
export const snap = (p: number, digits: number) => Number(p.toFixed(digits));
