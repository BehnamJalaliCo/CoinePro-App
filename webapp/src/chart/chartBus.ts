// The one live chart, reachable from the frame around it (the time bar's ranges and scale switches,
// the snapshot button, the data window) without threading refs through every component.
import { create } from 'zustand';
import type { IChartApi } from 'lightweight-charts';
import type { Candle } from '../data/types';

export type ScaleMode = 'normal' | 'log' | 'percent';

interface ChartBusState {
  api: IChartApi | null;
  bars: Candle[];
  /** Bar under the crosshair, or null for the last bar. */
  hover: Candle | null;
  scale: ScaleMode;
  autoScale: boolean;
  /** Seconds of history to show once the next load finishes (a time-bar range button). */
  pendingSpan: number | null;
  setApi(api: IChartApi | null): void;
  setBars(bars: Candle[]): void;
  setHover(bar: Candle | null): void;
  setScale(mode: ScaleMode): void;
  setAutoScale(on: boolean): void;
  setPendingSpan(seconds: number | null): void;
}

export const useChartBus = create<ChartBusState>((set) => ({
  api: null,
  bars: [],
  hover: null,
  scale: 'normal',
  autoScale: true,
  pendingSpan: null,
  setApi: (api) => set({ api }),
  setBars: (bars) => set({ bars }),
  setHover: (hover) => set({ hover }),
  setScale: (scale) => set({ scale }),
  setAutoScale: (autoScale) => set({ autoScale }),
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
