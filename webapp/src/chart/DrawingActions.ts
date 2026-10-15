// What the keyboard, the rail, the floating style bar, the right-click menu and the object tree all
// do to drawings: copy and paste, remove the selection, an alert from a drawing, templates and
// defaults, hiding the indicators, clearing the chart. One place, so every door does the same.
import { useChartBus } from './chartBus';
import { useDrawing } from '../state/drawing';
import { usePrefs } from '../state/prefs';
import { useStudies, VOLUME } from '../state/studies';
import { DrawingLayer, STYLE_KEYS, type DrawingObject } from '../engine/drawings.js';
import { toolName } from './tools';
import { cleanFa } from './indicatorCatalog';

const layer = () => useChartBus.getState().drawingLayer;

/** The name a drawing shows in the reader's language. */
export function drawingName(type: string, lang: 'fa' | 'en'): string {
  const n = toolName(type, lang);
  return n !== type ? n : (lang === 'fa' ? cleanFa(DrawingLayer.label(type)) : type);
}

/** Whether the keyboard is inside a dialog (the drawing keys leave it alone then). */
export function inDialog(): boolean {
  const el = document.activeElement as HTMLElement | null;
  return Boolean(el?.closest?.('[role="dialog"], .dctx, .dfly'));
}

/** Tells the lists that show drawings (object tree, style bar) that something changed. */
export function bumpDrawings(): void {
  const l = layer();
  if (l) useChartBus.getState().bumpDrawings(l.canUndo(), l.canRedo(), l.getDrawings().length);
}

/** Ctrl+C: the selected drawings to the drawing clipboard. False when nothing is selected. */
export function copySelection(): boolean {
  const l = layer();
  if (!l) return false;
  const list = l.copySelected();
  if (!list.length) return false;
  useDrawing.getState().setClip(list);
  return true;
}

/** Ctrl+V: the clipboard's drawings on this chart, a little offset, selected. */
export function pasteClip(): boolean {
  const l = layer();
  const clip = useDrawing.getState().clip;
  if (!l || !clip?.length) return false;
  l.pasteMany(clip);
  return true;
}

/** Delete: every selected drawing. */
export function removeSelection(): boolean {
  const l = layer();
  if (!l || !l.selectedIndices().length) return false;
  l.removeSelected();
  return true;
}

/** A drawing's look (colour, widths, text style, levels…), without where it is. */
export function styleOf(d: DrawingObject): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const k of STYLE_KEYS) if (d[k] !== undefined) out[k] = JSON.parse(JSON.stringify(d[k]));
  return out;
}

/** «Save as default»: new drawings of this type take this one's look. */
export function saveAsDefault(d: DrawingObject): void {
  useDrawing.getState().setDefault(d.type, styleOf(d));
}

/** Applies a style (a template, the factory look) to the drawing at `index`. */
export function applyStyle(index: number, style: Record<string, unknown>): void {
  const l = layer();
  if (!l) return;
  l.setStyle(index, style);
  l.render();
}

type Pt = { t: number; p: number };
const isPt = (v: unknown): v is Pt => !!v && typeof (v as Pt).t === 'number' && typeof (v as Pt).p === 'number';

/** Lines an alert can follow as a sloped level (TradingView: trend line, ray, extended line). */
export const LINE_ALERT_TYPES = ['trend', 'ray', 'extline', 'infoline', 'angle'];
/** Horizontal drawings an alert reads as one price. */
export const PRICE_ALERT_TYPES = ['hline', 'hray', 'pricelabel', 'crossline', 'pricenote'];

/**
 * The alert a drawing stands for (contract 1): a horizontal line → its price; a trend line, ray or
 * extended line → the line itself; anything else with a price → the price of its last point.
 */
export function alertDetail(d: DrawingObject): { symbol: string; price?: number; line?: { t1: number; p1: number; t2: number; p2: number } } | null {
  const symbol = usePrefs.getState().symbol;
  if (d.type === 'hline' && typeof d.p === 'number') return { symbol, price: d.p };
  if (LINE_ALERT_TYPES.includes(d.type) && isPt(d.p0) && isPt(d.p1)) return { symbol, line: { t1: d.p0.t, p1: d.p0.p, t2: d.p1.t, p2: d.p1.p } };
  if (isPt(d.p0) && PRICE_ALERT_TYPES.includes(d.type)) return { symbol, price: d.p0.p };
  if ((d.type === 'longshort' || d.type === 'short') && isPt(d.p0)) return { symbol, price: d.p0.p };
  const pts = Array.isArray(d.pts) ? (d.pts as unknown[]).filter(isPt) : [];
  const last = pts[pts.length - 1] ?? (isPt(d.p1) ? d.p1 : isPt(d.p0) ? d.p0 : null);
  if (last) return { symbol, price: last.p };
  if (typeof d.p === 'number') return { symbol, price: d.p };
  return null;
}

/** Opens the alert dialog prefilled from a drawing. False when the drawing has no price. */
export function addAlertFor(d: DrawingObject): boolean {
  const detail = alertDetail(d);
  if (!detail) return false;
  window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail }));
  return true;
}

/** Hides every indicator (or shows again the ones this switch hid); the volume study included. */
export function setIndicatorsHidden(on: boolean): void {
  const st = useStudies.getState();
  const dr = useDrawing.getState();
  if (on) {
    const hide = st.list.filter((s) => !s.hidden).map((s) => s.uid);
    hide.forEach((uid) => st.toggle(uid));
    dr.setHiddenStudies([...new Set([...dr.hiddenStudies, ...hide])]);
  } else {
    const back = new Set(dr.hiddenStudies);
    useStudies.getState().list.filter((s) => s.hidden && back.has(s.uid)).forEach((s) => useStudies.getState().toggle(s.uid));
    dr.setHiddenStudies([]);
  }
}
/** Whether the "hide indicators" switch is on: some indicator it hid is still hidden. */
export function indicatorsHidden(list = useStudies.getState().list, hidden = useDrawing.getState().hiddenStudies): boolean {
  return hidden.length > 0 && list.some((s) => s.hidden && hidden.includes(s.uid));
}

/** The indicators the Remove menu counts (the volume under the price is part of the chart). */
export const removableStudies = (list = useStudies.getState().list) => list.filter((s) => s.key !== VOLUME);

/** Ctrl+Alt+R: TradingView's clear chart — the drawings and the indicators go. */
export function clearChart(): void {
  useDrawing.getState().clearAll();
  useStudies.getState().clear();
}

/** The drawings the Remove button would take: all, or the unlocked ones unless asked otherwise. */
export function removableDrawings(list: DrawingObject[], removeLocked = useDrawing.getState().removeLocked): DrawingObject[] {
  return removeLocked ? list : list.filter((d) => !d.locked);
}
