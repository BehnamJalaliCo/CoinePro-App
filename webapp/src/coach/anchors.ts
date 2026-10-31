// Where each tip's control is on the glass right now, and whether its moment exists (OLD chart/ui
// ChartCoachAnchors.kt and feature/chart ChartCoach.kt). A control laid out by the app carries
// `data-coach="<tip id>"`; the chart's own parts — its plot, its price column, the strip of event
// marks — are drawn by the chart library, so they are measured from its table.
//
// Every chart tip waits for its moment: the reading gestures only on a chart with bars and nothing
// armed or selected, the drawing cursor only once a drawing tool is armed on a touch screen, the
// handles only once a drawing is selected, the legend only with a study on, the event marks only
// with marks on the chart.
import { useDrawing } from '../state/drawing';
import { useChartBus } from '../chart/chartBus';
import { useStudies } from '../state/studies';
import { useAlerts } from '../state/alerts';
import { useChartSettings } from '../state/chartSettings';
import { usePrefs } from '../state/prefs';
import { useReplay } from '../state/replay';
import { CURSOR_TOOLS } from '../chart/tools';
import type { Box } from './tips';

const NOT_DRAWING = new Set([...CURSOR_TOOLS.map((t) => t.id), 'select', 'zoom', 'measure']);

function boxOf(el: Element | null | undefined): Box | null {
  if (!el || !(el as HTMLElement).isConnected) return null;
  const r = el.getBoundingClientRect();
  if (r.width <= 0 || r.height <= 0) return null;
  const cs = getComputedStyle(el);
  if (cs.visibility === 'hidden' || cs.display === 'none') return null;
  return { left: r.left, top: r.top, width: r.width, height: r.height };
}

function union(boxes: (Box | null)[]): Box | null {
  const b = boxes.filter((x): x is Box => Boolean(x));
  if (!b.length) return null;
  const left = Math.min(...b.map((x) => x.left)); const top = Math.min(...b.map((x) => x.top));
  const right = Math.max(...b.map((x) => x.left + x.width)); const bottom = Math.max(...b.map((x) => x.top + x.height));
  return { left, top, width: right - left, height: bottom - top };
}

/** Every visible element marked for [id], as one box. */
export function marked(id: string): Box | null {
  return union([...document.querySelectorAll(`[data-coach~="${id}"]`)].map(boxOf));
}

const first = (sel: string): Box | null => {
  for (const el of document.querySelectorAll(sel)) { const b = boxOf(el); if (b) return b; }
  return null;
};

/** The first chart on screen: its plot (the main pane) and its price column, in window pixels. */
function chartParts(): { plot: Box; gutter: Box | null } | null {
  for (const chart of document.querySelectorAll('.ws-chart')) {
    if (chart.querySelector('.chart-state')) continue;
    const row = chart.querySelector('.chart-host table tr');
    const cells = row ? [...row.children] : [];
    const plot = boxOf(cells[1]);
    if (!plot) continue;
    return { plot, gutter: boxOf(cells[2]) ?? boxOf(cells[0]) };
  }
  return null;
}

/** Every tip whose control is on screen and whose moment exists, with that control's box. */
export function collectRequests(touch: boolean): Map<string, Box> {
  const out = new Map<string, Box>();
  const put = (id: string, b: Box | null | undefined) => { if (b) out.set(id, b); };
  const tool = useDrawing.getState().tool;
  const armed = !NOT_DRAWING.has(tool);
  const selected = useChartBus.getState().selectedDrawing >= 0;
  const replay = useReplay.getState();
  const parts = chartParts();
  const reading = Boolean(parts) && !armed && !selected && !replay.selecting;
  const cs = useChartSettings.getState();
  const symbol = usePrefs.getState().symbol;

  put('draw_toolbar', marked('draw_toolbar'));
  put('draw_band', marked('draw_band'));
  put('select_bar', marked('select_bar') ?? first('.draw-style-bar'));
  put('replay_bar', marked('replay_bar') ?? first('.replay-bar.rp2'));
  if (parts) {
    if (touch && armed) put('draw_cursor', parts.plot);
    if (selected && !armed) put('draw_handle', parts.plot);
  }
  if (reading && parts) {
    put('chart_pinch', parts.plot);
    put('chart_axis', parts.gutter);
    put('chart_hold', parts.plot);
    if (cs.plusButton) put('gutter_plus', parts.gutter);
    if (cs.alertLines && useAlerts.getState().list.some((a) => a.active && a.symbol === symbol && (a.source ?? 'price') === 'price')) put('alert_line', parts.plot);
    put('axis_minis', marked('axis_minis'));
    if (useStudies.getState().list.length) put('legend', marked('legend') ?? first('.legend-study[data-uid]'));
    const marks = document.querySelector('canvas.event-marks');
    if (marks && Number(marks.getAttribute('data-marks') ?? 0) > 0) {
      const strip = Math.min(30, parts.plot.height);
      put('chart_events', { left: parts.plot.left, top: parts.plot.top + parts.plot.height - strip, width: parts.plot.width, height: strip });
    }
    put('realtime', marked('realtime') ?? first('.ws-chart .go-live'));
  }
  const rows = [...document.querySelectorAll('.ws-panel .wl-row[draggable="true"]')];
  if (rows.length >= 2) put('row_reorder', boxOf(rows[0]));
  return out;
}

/** The box of one tip's control now, for the spotlight to follow (null when it has gone). */
export function anchorOf(id: string, touch: boolean): Box | null {
  return collectRequests(touch).get(id) ?? null;
}

/** Something covers the app: a dialog, a page, a menu, the drawer, the first run. */
export function blockedNow(): boolean {
  return Boolean(document.querySelector('.scrim, [role=dialog], [role=alertdialog], [role=menu], .first-run'));
}

export const touchFirst = (): boolean => { try { return window.matchMedia('(pointer: coarse)').matches; } catch { return false; } };
export const reducedMotion = (): boolean => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch { return false; } };
