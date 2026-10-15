// Mounts the old Pro-Chart drawing engine (engine/drawings.js: 64 tools, handles, magnet, undo)
// over the chart. The engine draws in CSS pixels on `canvas.width × canvas.height`; it is handed a
// facade whose size is the CSS size while the real canvas is backed at the device pixel ratio, so
// every line is sharp on a retina screen without touching the engine.
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import { DrawingLayer, type DrawingObject } from '../engine/drawings.js';
import { useDrawing } from '../state/drawing';
import { useChartBus } from './chartBus';

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
  let symbol = '';

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
  layer.onToolReset = () => { useDrawing.getState().setTool('cursor', 'cursors'); };
  layer.setTimeFmt((t: number) => {
    const d = new Date(t * 1000);
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')} ${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`;
  });

  const repaint = () => layer.render();
  chart.timeScale().subscribeVisibleLogicalRangeChange(repaint);
  chart.subscribeCrosshairMove(repaint);
  const ro = new ResizeObserver(resize);
  if (canvas.parentElement) ro.observe(canvas.parentElement);
  resize();

  // The rail and the drawing switches drive the layer.
  const apply = (s: ReturnType<typeof useDrawing.getState>, prev?: ReturnType<typeof useDrawing.getState>) => {
    if (!prev || s.tool !== prev.tool) layer.setTool(s.tool === 'cursor' ? 'cursor' : s.tool);
    if (!prev || s.magnet !== prev.magnet) layer.setMagnet(s.magnet, 'strong');
    if (!prev || s.hidden !== prev.hidden) canvas.style.visibility = s.hidden ? 'hidden' : 'visible';
    if (prev && s.locked !== prev.locked) layer.lockAll(s.locked);
    if (prev && s.clearSeq !== prev.clearSeq) layer.clearAll();
  };
  apply(useDrawing.getState());
  const unsub = useDrawing.subscribe((s, prev) => apply(s, prev));

  // Delete removes the selected drawing; Ctrl/Cmd+Z and +Y (or Shift+Z) undo and redo, as on TradingView.
  const onKey = (e: KeyboardEvent) => {
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, [contenteditable]')) return;
    if ((e.key === 'Delete' || e.key === 'Backspace') && layer.selected >= 0) { e.preventDefault(); layer.removeAt(layer.selected); }
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z' && !e.shiftKey) { e.preventDefault(); layer.undo(); bump(); }
    else if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'y' || (e.key.toLowerCase() === 'z' && e.shiftKey))) { e.preventDefault(); layer.redo(); bump(); }
    else if (e.key === 'Escape' && layer.tool !== 'cursor') useDrawing.getState().setTool('cursor', 'cursors');
  };
  window.addEventListener('keydown', onKey);

  useChartBus.getState().setDrawingLayer(layer);

  return {
    layer,
    setSymbol(next) {
      if (next === symbol) return;
      symbol = next;
      layer.setDrawings(load(next));
      bump();
    },
    setSeries(series) { layer.setSeries(series); },
    resize,
    dispose() {
      unsub();
      ro.disconnect();
      window.removeEventListener('keydown', onKey);
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(repaint);
      chart.unsubscribeCrosshairMove(repaint);
      useChartBus.getState().setDrawingLayer(null);
    },
  };
}
