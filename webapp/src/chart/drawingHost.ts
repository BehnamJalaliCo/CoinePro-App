// Mounts the old Pro-Chart drawing engine (engine/drawings.js: 76 tools, handles, magnet, undo)
// over the chart. The engine draws in CSS pixels on `canvas.width × canvas.height`; it is handed a
// facade whose size is the CSS size while the real canvas is backed at the device pixel ratio, so
// every line is sharp on a retina screen without touching the engine.
//
// The host wires the reader's drawing state to the layer: the armed tool and cursor, the default
// colour and per-tool defaults, magnet (which also snaps the chart's crosshair), stay-in-drawing,
// lock and hide (kept across symbols), the timeframe-visibility class, the time zone of the date
// labels, the zoom box, the right-click on a drawing, and drawing with a finger.
import { CrosshairMode, type IChartApi, type ISeriesApi, type SeriesType } from 'lightweight-charts';
import { DrawingLayer, TEXT_TYPES, type DrawingObject } from '../engine/drawings.js';
import { isCursorMode, useDrawing } from '../state/drawing';
import { usePrefs } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { intervalSeconds } from '../data/intervals';
import { useChartBus } from './chartBus';
import { formatPrice } from './format';
import { attachCursor, ERASER_CURSOR } from './DrawingCursor';
import { clearZoom, zoomToBox } from './DrawingZoom';
import { removableDrawings } from './DrawingActions';

const KEY = (symbol: string) => `pc.drawings.v1.${symbol}`;

function load(symbol: string): DrawingObject[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY(symbol)) ?? '[]') as unknown;
    return Array.isArray(v) ? (v as DrawingObject[]) : [];
  } catch { return []; }
}

function save(symbol: string, list: DrawingObject[]): void {
  try {
    if (list.length) localStorage.setItem(KEY(symbol), JSON.stringify(list));
    else localStorage.removeItem(KEY(symbol));
  } catch { /* storage full or blocked: drawings stay for this visit */ }
}

/** The interval's class for «visibility on timeframes», and its size in that class's units. */
export function tfClassOf(interval: string): { cls: string; n: number } {
  const s = intervalSeconds(interval);
  if (s < 60) return { cls: 'seconds', n: s };
  if (s < 3600) return { cls: 'minutes', n: Math.round(s / 60) };
  if (s < 86400) return { cls: 'hours', n: Math.round(s / 3600) };
  if (s < 604800) return { cls: 'days', n: Math.round(s / 86400) };
  if (s < 2592000) return { cls: 'weeks', n: Math.round(s / 604800) };
  return { cls: 'months', n: Math.max(1, Math.round(s / 2592000)) };
}

/**
 * The date of a vertical line or an anchor on the time axis, as the axis writes it: in the reader's
 * time zone with Latin digits ("Tue 13 Oct '26  17:00"); daily and longer bars keep their UTC date.
 */
export function timeLabel(timezone: string, interval: string): (t: number) => string {
  const daily = ['1D', '1W', '1M'].includes(interval);
  const tz = daily ? 'UTC' : timezone;
  let date: Intl.DateTimeFormat, time: Intl.DateTimeFormat;
  try {
    date = new Intl.DateTimeFormat('en-GB', { timeZone: tz, weekday: 'short', day: '2-digit', month: 'short', year: '2-digit' });
    time = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false });
  } catch {
    date = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', weekday: 'short', day: '2-digit', month: 'short', year: '2-digit' });
    time = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit', hour12: false });
  }
  return (t: number) => {
    const d = new Date(t * 1000);
    const day = date.format(d).replace(/,/g, '').replace(/ (\d{2})$/, " '$1");
    return daily ? day : `${day}  ${time.format(d)}`;
  };
}

/** The engine's tool for a store tool: the cursor modes are the plain cursor, the eraser its own. */
const engineTool = (tool: string) => (tool === 'eraser' ? 'eraser' : isCursorMode(tool) ? 'cursor' : tool);

export interface DrawingHost {
  layer: DrawingLayer;
  setSymbol(symbol: string): void;
  setSeries(series: ISeriesApi<SeriesType>): void;
  resize(): void;
  dispose(): void;
}

export function attachDrawings(canvas: HTMLCanvasElement, chart: IChartApi): DrawingHost {
  const ctx = canvas.getContext('2d')!;
  let cssW = 0;
  let cssH = 0;
  const facade = {
    get width() { return cssW; },
    set width(_v: number) { /* sized by resize() */ },
    get height() { return cssH; },
    set height(_v: number) { /* sized by resize() */ },
    style: canvas.style,
    getContext: () => ctx,
    getBoundingClientRect: () => canvas.getBoundingClientRect(),
    addEventListener: canvas.addEventListener.bind(canvas),
    removeEventListener: canvas.removeEventListener.bind(canvas),
  };
  const layer = new DrawingLayer(facade, chart);
  layer.eraserCursor = ERASER_CURSOR;
  let symbol = '';
  const host = canvas.parentElement;

  const resize = () => {
    const r = canvas.parentElement?.getBoundingClientRect();
    if (!r) return;
    const dpr = window.devicePixelRatio || 1;
    cssW = Math.round(r.width);
    cssH = Math.round(r.height);
    canvas.style.width = `${cssW}px`;
    canvas.style.height = `${cssH}px`;
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    layer.render();
  };

  const bump = () => useChartBus.getState().bumpDrawings(layer.canUndo(), layer.canRedo(), layer.getDrawings().length);
  layer.onChange = (list) => { if (symbol) save(symbol, list); bump(); };
  layer.onSelect = (i) => useChartBus.getState().setSelectedDrawing(i);
  layer.onToolReset = () => { useDrawing.getState().resetTool(); };
  // A drawing just placed: locked like the others when everything is locked; a text-bearing one
  // opens the app's text editor at once (TradingView asks for the text the same way).
  layer.onCreated = (index) => {
    const d = layer.getDrawings()[index];
    if (!d) return;
    if (useDrawing.getState().locked) { d.locked = true; layer.onChange?.(layer.getDrawings()); }
    if (TEXT_TYPES.includes(d.type)) useChartBus.getState().setTextEdit({ index, fresh: true });
  };
  // A double click edits: the text of a text-bearing drawing, the settings of any other.
  layer.onDblEdit = (index) => {
    const d = layer.getDrawings()[index];
    if (!d) return;
    if (TEXT_TYPES.includes(d.type)) useChartBus.getState().setTextEdit({ index, fresh: false });
    else useDrawing.getState().openSettings(index);
  };
  layer.onZoomBox = (box) => { zoomToBox(box); };
  layer.priceFmt = (v: number) => formatPrice(v, layer.digits);

  // Dates on the canvas follow the reader's time zone, as the axis does; labels the language.
  const applyPrefs = () => {
    const p = usePrefs.getState();
    layer.setTimeFmt(timeLabel(p.timezone, p.interval));
    layer.lang = p.lang;
    const tf = tfClassOf(p.interval);
    layer.setTfClass(tf.cls, tf.n);
    layer.render();
  };
  applyPrefs();

  const repaint = () => layer.render();
  chart.timeScale().subscribeVisibleLogicalRangeChange(repaint);
  chart.subscribeCrosshairMove(repaint);
  const ro = new ResizeObserver(resize);
  if (canvas.parentElement) ro.observe(canvas.parentElement);
  resize();

  // The cursor over the chart (Cross, Dot, Arrow, Demonstration, Eraser).
  const cursor = host ? attachCursor(host) : null;

  // The chart's crosshair: hidden for the plain arrow, the laser and the eraser; snapped to the
  // bars' open/high/low/close while the magnet is on (TradingView); otherwise the chart settings'.
  // Re-applied a moment after any change, so it wins over the chart's own settings effect.
  let crossTimer: ReturnType<typeof setTimeout> | null = null;
  const crossMode = () => {
    const d = useDrawing.getState();
    if (d.tool === 'arrowcursor' || d.tool === 'demonstration' || d.tool === 'eraser') return CrosshairMode.Hidden;
    if (d.magnet) return CrosshairMode.MagnetOHLC;
    return useChartSettings.getState().crosshair === 'magnet' ? CrosshairMode.Magnet : CrosshairMode.Normal;
  };
  const applyCross = () => { try { chart.applyOptions({ crosshair: { mode: crossMode() } }); } catch { /* chart gone */ } };
  const scheduleCross = () => { applyCross(); if (crossTimer) clearTimeout(crossTimer); crossTimer = setTimeout(() => { crossTimer = null; applyCross(); }, 40); };
  scheduleCross();

  // The rail and the drawing switches drive the layer.
  const apply = (s: ReturnType<typeof useDrawing.getState>, prev?: ReturnType<typeof useDrawing.getState>) => {
    if (!prev || s.color !== prev.color) layer.color = s.color;
    if (!prev || s.tool !== prev.tool) {
      layer.setTool(engineTool(s.tool), s.color);
      cursor?.set(isCursorMode(s.tool) ? s.tool : 'draw');
      // Drawing while everything is hidden shows the drawings again, as TradingView does.
      if (prev && s.hidden && !isCursorMode(s.tool) && s.tool !== 'zoom' && s.tool !== 'select') useDrawing.getState().setHidden(false);
    }
    if (!prev || s.magnet !== prev.magnet || s.magnetMode !== prev.magnetMode) layer.setMagnet(s.magnet, s.magnetMode);
    if (!prev || s.stay !== prev.stay) layer.setStayInMode(s.stay);
    if (!prev || s.hidden !== prev.hidden) canvas.style.visibility = s.hidden ? 'hidden' : 'visible';
    if (prev && s.locked !== prev.locked) layer.lockAll(s.locked);
    if (!prev || s.defaults !== prev.defaults) layer.setToolDefaults(s.defaults);
    if (!prev || s.emoji !== prev.emoji) layer.emoji = s.emoji;
    if (prev && s.clearSeq !== prev.clearSeq) {
      const list = layer.getDrawings();
      const gone = removableDrawings(list, s.removeLocked);
      if (gone.length === list.length) layer.clearAll();
      else if (gone.length) { const keep = list.filter((d) => !gone.includes(d)); layer.setDrawings(keep, true); layer.onChange?.(keep); }
    }
    if (!prev || s.tool !== prev.tool || s.magnet !== prev.magnet) scheduleCross();
  };
  apply(useDrawing.getState());
  const unsub = useDrawing.subscribe((s, prev) => apply(s, prev));
  const unsubSettings = useChartSettings.subscribe((s, prev) => { if (s.crosshair !== prev.crosshair) scheduleCross(); });
  const unsubPrefs = usePrefs.subscribe((s, prev) => {
    if (s.timezone !== prev.timezone || s.interval !== prev.interval || s.lang !== prev.lang) applyPrefs();
    if (s.theme !== prev.theme) scheduleCross();
    if (s.interval !== prev.interval) clearZoom();
  });

  // Delete removes every selected drawing; Ctrl/Cmd+Z and +Y (or Shift+Z) undo and redo, as on TradingView.
  const onKey = (e: KeyboardEvent) => {
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable], [role="dialog"], .dctx')) return;
    if (e.defaultPrevented) return;
    if ((e.key === 'Delete' || e.key === 'Backspace') && !e.ctrlKey && !e.metaKey && !e.altKey && layer.selectedIndices().length) { e.preventDefault(); layer.removeSelected(); }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey && !e.altKey) { e.preventDefault(); layer.undo(); bump(); }
    else if ((e.ctrlKey || e.metaKey) && !e.altKey && (e.key.toLowerCase() === 'y' || (e.key.toLowerCase() === 'z' && e.shiftKey))) { e.preventDefault(); layer.redo(); bump(); }
  };
  window.addEventListener('keydown', onKey);

  // A right click on a drawing opens the drawing's own menu (contract 2: the chart's menu listens
  // for the same event when the click reaches it first).
  const onContext = (e: MouseEvent) => {
    const r = canvas.getBoundingClientRect();
    const i = layer.hitTest(e.clientX - r.left, e.clientY - r.top);
    if (i < 0) return;
    e.preventDefault(); e.stopPropagation();
    window.dispatchEvent(new CustomEvent('pc:drawing-menu', { detail: { clientX: e.clientX, clientY: e.clientY, index: i } }));
  };
  canvas.addEventListener('contextmenu', onContext);

  // A finger on a drawing in cursor mode: the layer takes the press (select, drag, handles) and the
  // chart does not scroll under it. Elsewhere the finger pans the chart as usual.
  let fingerOwned = false;
  const onPointerDownCapture = (e: PointerEvent) => {
    fingerOwned = false;
    if (e.pointerType === 'mouse' || e.target === canvas) return;
    if (layer.tool !== 'cursor' && layer.tool !== 'eraser' && layer.tool !== 'select') return;
    const r = canvas.getBoundingClientRect();
    const x = e.clientX - r.left, y = e.clientY - r.top;
    const sel = layer.selected >= 0 ? layer.getDrawings()[layer.selected] : null;
    const onSelected = sel && !sel.locked && (layer as unknown as { _hitHandle(d: DrawingObject, x: number, y: number): number })._hitHandle(sel, x, y) >= 0;
    if (!onSelected && layer.hitTest(x, y) < 0) return;
    fingerOwned = true;
    e.stopPropagation();
    layer._down?.(e);
  };
  const onTouchCapture = (e: TouchEvent) => { if (fingerOwned) { e.stopPropagation(); if (e.cancelable) e.preventDefault(); } if (e.type === 'touchend' || e.type === 'touchcancel') fingerOwned = false; };
  host?.addEventListener('pointerdown', onPointerDownCapture, true);
  for (const ev of ['touchstart', 'touchmove', 'touchend', 'touchcancel'] as const) host?.addEventListener(ev, onTouchCapture, { capture: true, passive: false });

  useChartBus.getState().setDrawingLayer(layer);

  return {
    layer,
    setSymbol(next) {
      if (next === symbol) return;
      symbol = next;
      layer.setDrawings(load(next));
      // A new symbol, a new history: undo never brings another symbol's drawings here.
      layer.resetHistory();
      clearZoom();
      if (useDrawing.getState().locked && layer.getDrawings().some((d) => !d.locked)) layer.lockAll(true, false);
      layer.selectAt(-1);
      useChartBus.getState().setSelectedDrawing(-1);
      useChartBus.getState().setTextEdit(null);
      useDrawing.getState().closeSettings();
      bump();
    },
    setSeries(series) { layer.setSeries(series); },
    resize,
    dispose() {
      unsub(); unsubSettings(); unsubPrefs();
      if (crossTimer) clearTimeout(crossTimer);
      ro.disconnect();
      cursor?.dispose();
      window.removeEventListener('keydown', onKey);
      canvas.removeEventListener('contextmenu', onContext);
      host?.removeEventListener('pointerdown', onPointerDownCapture, true);
      for (const ev of ['touchstart', 'touchmove', 'touchend', 'touchcancel'] as const) host?.removeEventListener(ev, onTouchCapture, { capture: true });
      layer.destroy();
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(repaint);
      chart.unsubscribeCrosshairMove(repaint);
      useChartBus.getState().setDrawingLayer(null);
      useChartBus.getState().setSelectedDrawing(-1);
    },
  };
}
