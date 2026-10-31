// TradingView's phone placement (OLD chart/ui DrawCursor.kt, CoineProChart.kt drawCursor): a
// cursor that is not the finger.
//
// A tap that places a point under the fingertip places it where the reader cannot see — the finger
// is over it. So on a phone (≤760 px, a finger) an armed tool shows its own pointer: a blue dot and
// two dashed rules in the middle of the plot, moved by dragging anywhere on the chart the way a
// trackpad moves a mouse, and a tap puts the point where the dot is. Between points the shape is
// stretched from the last anchor to the dot, so the reader sees the line before committing it, and
// a banner over the plot counts the steps («۱ از ۴ · نشانگر را روی نقطه‌ی شروع ببرید») with a bin
// that drops the drawing. A mouse needs none of this and keeps drawing directly.
import type { IChartApi } from 'lightweight-charts';
import type { DrawingLayer } from '../engine/drawings.js';
import { EXT_NEED, EXT_ONE, EXT_POLY } from '../engine/drawtools_ext.js';
import { isCursorMode, useDrawing } from '../state/drawing';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { FREEHAND_TOOLS } from './tools';

export const PHONE_QUERY = '(max-width: 760px)';
const SLOP = 8;
const DOUBLE_TAP_MS = 320;

/** How many points the tool takes: 1, 2, 3…, or 0 for a path that ends when the reader says. */
export function pointsOf(tool: string): number {
  if (EXT_POLY.includes(tool)) return 0;
  if (EXT_ONE.includes(tool) || ['hline', 'vline', 'text'].includes(tool)) return 1;
  const need = ({ channel: 3, pitchfork: 3, ...EXT_NEED } as Record<string, number>)[tool];
  return need ?? 2;
}

/** Whether the armed tool places points (the placement cursor serves it). */
export function placesPoints(tool: string): boolean {
  return !isCursorMode(tool) && !['select', 'zoom', 'eraser'].includes(tool) && !FREEHAND_TOOLS.includes(tool);
}

export type StepText = 'moveStart' | 'moveNext' | 'tapFirst' | 'tapNext' | 'tapFinish';
export interface Step { index: number; total: number; text: StepText }

/**
 * Which line of the banner applies and where in the count it is (OLD DrawCursor.step): two steps
 * a point, «move the cursor to the point» then «tap to set it». `moved`: the cursor has travelled
 * since the last point went down. A path has no last point, so no «of N».
 */
export function drawStep(points: number, placed: number, moved: boolean): Step {
  const variable = points <= 0;
  const text: StepText = !moved && placed === 0 ? 'moveStart'
    : !moved ? 'moveNext'
      : placed === 0 && (points > 1 || variable) ? 'tapFirst'
        : !variable && placed >= points - 1 ? 'tapFinish'
          : 'tapNext';
  if (variable) return { index: 0, total: 0, text };
  return { index: Math.min(2 * placed + (moved ? 2 : 1), 2 * points), total: 2 * points, text };
}

export const STEP_TEXT: Record<StepText | 'of' | 'cancel', [string, string]> = {
  moveStart: ['نشانگر را روی نقطه‌ی شروع ببرید', 'Move cursor to start point'],
  moveNext: ['نشانگر را روی نقطه‌ی بعد ببرید', 'Move cursor to next point'],
  tapFirst: ['بزنید تا نقطه‌ی اول ثبت شود', 'Tap to set first point'],
  tapNext: ['بزنید تا این نقطه ثبت شود', 'Tap to set this point'],
  tapFinish: ['بزنید تا ترسیم تمام شود', 'Tap to finish up'],
  of: ['%1 از %2', '%1 of %2'],
  cancel: ['لغو ترسیم', 'Cancel drawing'],
};

const BIN = '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M9 7V4.5h6V7M6 7l1 13h10l1-13M10 11v5.5M14 11v5.5"/></svg>';

export interface Placement { dispose(): void; sync(): void }

export function attachPlacement(host: HTMLElement, canvas: HTMLCanvasElement, layer: DrawingLayer, chart: IChartApi): Placement {
  let lastPointer: string | null = null;
  let at: { x: number; y: number } | null = null;
  let moved = false;
  let gesture: { id: number; x: number; y: number; dragging: boolean } | null = null;
  let lastTap = 0;

  const phone = () => { try { return window.matchMedia(PHONE_QUERY).matches; } catch { return false; } };
  const active = () => phone() && lastPointer !== 'mouse' && placesPoints(useDrawing.getState().tool);

  // The overlay: two dashed rules and the dot, and the banner.
  const overlay = document.createElement('div');
  overlay.className = 'place-cursor';
  overlay.innerHTML = '<span class="pc-v"></span><span class="pc-h"></span><span class="pc-dot"></span>';
  overlay.hidden = true;
  const banner = document.createElement('div');
  banner.className = 'draw-banner';
  banner.setAttribute('role', 'status');
  banner.hidden = true;
  const chip = document.createElement('span'); chip.className = 'db-count num';
  const text = document.createElement('span'); text.className = 'db-text';
  const bin = document.createElement('button'); bin.type = 'button'; bin.className = 'db-cancel'; bin.innerHTML = BIN;
  banner.append(chip, text, bin);
  host.append(overlay, banner);

  const plot = () => {
    const w = canvas.clientWidth || host.clientWidth, h = canvas.clientHeight || host.clientHeight;
    let axis = 0, time = 0;
    try { axis = chart.priceScale('right').width(); } catch { /* no scale */ }
    try { time = chart.timeScale().height(); } catch { /* no scale */ }
    return { w: Math.max(1, w - axis), h: Math.max(1, h - time) };
  };
  const placed = () => {
    const l = layer as unknown as { twoClick?: boolean; tmp?: unknown; pending?: { pts: unknown[] } | null };
    if (l.pending?.pts?.length) return l.pending.pts.length;
    return l.twoClick && l.tmp ? 1 : 0;
  };
  const feed = () => {
    if (!at) return;
    const r = canvas.getBoundingClientRect();
    layer._move?.({ clientX: r.left + at.x, clientY: r.top + at.y });
  };

  const paint = () => {
    const on = active();
    if (!on) { overlay.hidden = true; banner.hidden = true; at = null; return; }
    const p = plot();
    if (!at) { at = { x: p.w / 2, y: p.h / 2 }; moved = false; }
    at = { x: Math.max(0, Math.min(p.w, at.x)), y: Math.max(0, Math.min(p.h, at.y)) };
    overlay.hidden = false;
    overlay.style.setProperty('--px', `${at.x}px`);
    overlay.style.setProperty('--py', `${at.y}px`);
    overlay.style.setProperty('--pw', `${p.w}px`);
    overlay.style.setProperty('--ph', `${p.h}px`);
    const fa = usePrefs.getState().lang === 'fa';
    const k = fa ? 0 : 1;
    const step = drawStep(pointsOf(useDrawing.getState().tool), placed(), moved);
    banner.hidden = false;
    banner.dir = fa ? 'rtl' : 'ltr';
    chip.hidden = step.total === 0;
    const n = (v: number) => (fa ? faNum(v) : String(v));
    chip.textContent = STEP_TEXT.of[k].replace('%1', n(step.index)).replace('%2', n(step.total));
    text.textContent = STEP_TEXT[step.text][k];
    text.className = `db-text${fa ? ' fa' : ''}`;
    banner.dataset.step = step.text;
    bin.title = STEP_TEXT.cancel[k];
    bin.setAttribute('aria-label', STEP_TEXT.cancel[k]);
  };

  // A tap places the point at the dot: the layer hears a click there, as from a mouse (so the
  // finger's own double-tap logic stays out of it).
  const tapAtCursor = () => {
    if (!at) return;
    const r = canvas.getBoundingClientRect();
    const tool = useDrawing.getState().tool;
    const now = Date.now();
    // A path ends with a double tap on the same spot.
    if (pointsOf(tool) === 0 && now - lastTap < DOUBLE_TAP_MS && placed() > 0) { lastTap = 0; layer._finishPoly?.(); moved = false; paint(); return; }
    lastTap = now;
    const ev = { clientX: r.left + at.x, clientY: r.top + at.y, pointerType: 'mouse', button: 0, shiftKey: false, ctrlKey: false, metaKey: false, altKey: false } as unknown as PointerEvent;
    layer._down?.(ev);
    layer._up?.();
    moved = false;
    paint();
  };

  const onDown = (e: PointerEvent) => {
    // The banner's own bin is a button, whatever presses it.
    if ((e.target as HTMLElement | null)?.closest?.('.draw-banner')) return;
    const was = lastPointer;
    lastPointer = e.pointerType || 'mouse';
    if (was !== lastPointer) paint();
    if (e.pointerType === 'mouse' || !active()) return;
    e.stopPropagation();
    if (e.cancelable) e.preventDefault();
    gesture = { id: e.pointerId, x: e.clientX, y: e.clientY, dragging: false };
    if (!at) paint();
  };
  const onMove = (e: PointerEvent) => {
    if (e.pointerType === 'mouse') return;
    if (!gesture || (e.pointerId !== gesture.id && e.pointerId !== 0 && gesture.id !== 0)) return;
    e.stopImmediatePropagation();
    const dx = e.clientX - gesture.x, dy = e.clientY - gesture.y;
    if (!gesture.dragging && Math.hypot(dx, dy) > SLOP) gesture.dragging = true;
    if (!gesture.dragging) return;
    gesture.x = e.clientX; gesture.y = e.clientY;
    const p = plot();
    const from = at ?? { x: p.w / 2, y: p.h / 2 };
    at = { x: Math.max(0, Math.min(p.w, from.x + dx)), y: Math.max(0, Math.min(p.h, from.y + dy)) };
    moved = true;
    feed();
    paint();
  };
  const onUp = (e: PointerEvent) => {
    if (!gesture || e.pointerType === 'mouse') return;
    e.stopImmediatePropagation();
    const g = gesture; gesture = null;
    if (e.type === 'pointercancel') return;
    if (!g.dragging) tapAtCursor();
  };
  // A finger on the chart does not also reach it as a scroll while placing.
  // A mouse over the chart (a narrow desktop window) draws directly, as on a desktop.
  const onHover = (e: PointerEvent) => { if (e.pointerType === 'mouse' && lastPointer !== 'mouse' && !gesture && !(e.target as HTMLElement | null)?.closest?.('.draw-banner')) { lastPointer = 'mouse'; paint(); } };
  const onTouch = (e: TouchEvent) => { if (active() && !(e.target as HTMLElement | null)?.closest?.('.draw-banner')) { e.stopPropagation(); if (e.cancelable) e.preventDefault(); } };

  bin.addEventListener('click', (e) => {
    e.stopPropagation();
    const had = placed() > 0;
    layer.cancelInteraction();
    if (!had) useDrawing.getState().resetTool();
    moved = false;
    paint();
  });
  host.addEventListener('pointerdown', onDown, true);
  host.addEventListener('pointermove', onHover);
  window.addEventListener('pointermove', onMove, true);
  window.addEventListener('pointerup', onUp, true);
  window.addEventListener('pointercancel', onUp, true);
  for (const ev of ['touchstart', 'touchmove'] as const) host.addEventListener(ev, onTouch, { capture: true, passive: false });
  const unsubDraw = useDrawing.subscribe((s, p) => { if (s.tool !== p.tool) { at = null; moved = false; } paint(); });
  const unsubPrefs = usePrefs.subscribe((s, p) => { if (s.lang !== p.lang) paint(); });
  let mq: MediaQueryList | null = null;
  try { mq = window.matchMedia(PHONE_QUERY); mq.addEventListener?.('change', paint); } catch { /* no media queries */ }
  paint();

  return {
    sync: paint,
    dispose() {
      host.removeEventListener('pointerdown', onDown, true);
      host.removeEventListener('pointermove', onHover);
      window.removeEventListener('pointermove', onMove, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
      for (const ev of ['touchstart', 'touchmove'] as const) host.removeEventListener(ev, onTouch, { capture: true });
      unsubDraw(); unsubPrefs();
      mq?.removeEventListener?.('change', paint);
      overlay.remove(); banner.remove();
    },
  };
}
