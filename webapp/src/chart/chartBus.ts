// The one live chart, reachable from the frame around it (the time bar's ranges and scale switches,
// the snapshot button, the data window) without threading refs through every component.
import { create } from 'zustand';
import type { IChartApi, ISeriesApi, SeriesType } from 'lightweight-charts';
import type { Candle } from '../data/types';
import type { DrawingLayer } from '../engine/drawings.js';
import type { StudyLegendRow } from './studies';

export type ScaleMode = 'normal' | 'log' | 'percent' | 'indexed';

/** A compared symbol's line, as the legend and the data window show it. */
export interface CompareRow { symbol: string; color: string; value: number | null; change: number | null; hidden: boolean }

// The price scale's mode, auto-scale and inversion are kept across reloads, as TradingView keeps them.
const SCALE_KEY = 'pc.chartscale.v1';
const readScale = (): { scale: ScaleMode; autoScale: boolean; invert: boolean } => {
  const d = { scale: 'normal' as ScaleMode, autoScale: true, invert: false };
  try {
    const v = JSON.parse(localStorage.getItem(SCALE_KEY) ?? '{}') as Partial<typeof d>;
    return {
      scale: ['normal', 'log', 'percent', 'indexed'].includes(String(v.scale)) ? v.scale! : d.scale,
      autoScale: typeof v.autoScale === 'boolean' ? v.autoScale : d.autoScale,
      invert: typeof v.invert === 'boolean' ? v.invert : d.invert,
    };
  } catch { return d; }
};

interface ChartBusState {
  api: IChartApi | null;
  /** The main price series (changes when the chart type changes), for markers and price lines. */
  series: ISeriesApi<SeriesType> | null;
  bars: Candle[];
  /** Bar under the crosshair, or null for the last bar. */
  hover: Candle | null;
  /** The bars the price series draws: the loaded bars, or the bricks of Renko, Range, Line break. */
  shown: Candle[];
  /** The indicators' values under the crosshair (the legend's rows), for the data window. */
  studyRows: StudyLegendRow[];
  /** The compared symbols' values under the crosshair. */
  compareRows: CompareRow[];
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
  setSeries(series: ISeriesApi<SeriesType> | null): void;
  setBars(bars: Candle[]): void;
  setHover(bar: Candle | null): void;
  setShown(bars: Candle[]): void;
  setStudyRows(rows: StudyLegendRow[]): void;
  setCompareRows(rows: CompareRow[]): void;
  setScale(mode: ScaleMode): void;
  setAutoScale(on: boolean): void;
  setInvert(on: boolean): void;
  setSelectedDrawing(i: number): void;
  setPendingSpan(seconds: number | null): void;
}

const scale0 = readScale();
const saveScale = (s: Pick<ChartBusState, 'scale' | 'autoScale' | 'invert'>) => {
  try { localStorage.setItem(SCALE_KEY, JSON.stringify({ scale: s.scale, autoScale: s.autoScale, invert: s.invert })); } catch { /* storage blocked */ }
};

export const useChartBus = create<ChartBusState>((set, get) => ({
  api: null,
  series: null,
  bars: [],
  hover: null,
  shown: [],
  studyRows: [],
  compareRows: [],
  scale: scale0.scale,
  autoScale: scale0.autoScale,
  invert: scale0.invert,
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
  setSeries: (series) => set({ series }),
  setBars: (bars) => set({ bars }),
  setHover: (hover) => set({ hover }),
  setShown: (shown) => set({ shown }),
  setStudyRows: (studyRows) => set({ studyRows }),
  setCompareRows: (compareRows) => set({ compareRows }),
  setScale: (scale) => { set({ scale }); saveScale(get()); },
  setAutoScale: (autoScale) => { set({ autoScale }); saveScale(get()); },
  setInvert: (invert) => { set({ invert }); saveScale(get()); },
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
