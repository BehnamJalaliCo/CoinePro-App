// The one live chart, reachable from the frame around it (the time bar's ranges and scale switches,
// the snapshot button, the data window) without threading refs through every component.
import { create } from 'zustand';
import type { IChartApi } from 'lightweight-charts';
import type { Candle } from '../data/types';
import type { DrawingLayer } from '../engine/drawings.js';

export type ScaleMode = 'normal' | 'log' | 'percent' | 'indexed';

interface ChartBusState {
  api: IChartApi | null;
  bars: Candle[];
  /** Bar under the crosshair, or null for the last bar. */
  hover: Candle | null;
  scale: ScaleMode;
  autoScale: boolean;
  /** The price scale upside down (TradingView's Alt+I). */
  invert: boolean;
  /** The drawing the reader has selected, or −1. */
  selectedDrawing: number;
  /** Seconds of history to show once the next load finishes (a time-bar range button). */
  pendingSpan: number | null;
  drawingLayer: DrawingLayer | null;
  canUndo: boolean;
  canRedo: boolean;
  drawingCount: number;
  /** Bumped on every drawing change, for lists that show the drawings. */
  drawingVersion: number;
  /** The text drawing being written or edited (its index), or null. */
  textEdit: { index: number; fresh: boolean } | null;
  setTextEdit(e: { index: number; fresh: boolean } | null): void;
  setDrawingLayer(layer: DrawingLayer | null): void;
  bumpDrawings(canUndo: boolean, canRedo: boolean, count: number): void;
  setApi(api: IChartApi | null): void;
  setBars(bars: Candle[]): void;
  setHover(bar: Candle | null): void;
  setScale(mode: ScaleMode): void;
  setAutoScale(on: boolean): void;
  setInvert(on: boolean): void;
  setSelectedDrawing(i: number): void;
  setPendingSpan(seconds: number | null): void;
}

export const useChartBus = create<ChartBusState>((set) => ({
  api: null,
  bars: [],
  hover: null,
  scale: 'normal',
  autoScale: true,
  invert: false,
  selectedDrawing: -1,
  pendingSpan: null,
  drawingLayer: null,
  canUndo: false,
  canRedo: false,
  drawingCount: 0,
  drawingVersion: 0,
  textEdit: null,
  setTextEdit: (textEdit) => set({ textEdit }),
  setDrawingLayer: (drawingLayer) => set({ drawingLayer }),
  bumpDrawings: (canUndo, canRedo, drawingCount) => set((s) => ({ canUndo, canRedo, drawingCount, drawingVersion: s.drawingVersion + 1 })),
  setApi: (api) => set({ api }),
  setBars: (bars) => set({ bars }),
  setHover: (hover) => set({ hover }),
  setScale: (scale) => set({ scale }),
  setAutoScale: (autoScale) => set({ autoScale }),
  setInvert: (invert) => set({ invert }),
  setSelectedDrawing: (selectedDrawing) => set({ selectedDrawing }),
  setPendingSpan: (pendingSpan) => set({ pendingSpan }),
}));

/** Shows the last `seconds` of the loaded bars, keeping TradingView's right margin. */
export function showSpan(api: IChartApi, bars: Candle[], seconds: number): void {
  const last = bars[bars.length - 1];
  if (!last) return;
  const from = last.t - seconds;
  const first = bars.findIndex((b) => b.t >= from);
  if (first < 0) return;
  api.timeScale().setVisibleLogicalRange({ from: first, to: bars.length - 1 + 10 });
}
