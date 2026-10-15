// Bar replay, as TradingView's: pick a starting bar on the chart, then play the bars after it one
// at a time (or step), at a chosen speed. The engine is the old Pro-Chart ReplayController.
import { create } from 'zustand';
import { ReplayController, SPEED_LADDER, type ReplayState } from '../engine/ReplayController.js';
import type { Candle } from '../data/types';

interface ReplayStore {
  /** Waiting for the reader to click the starting bar. */
  selecting: boolean;
  state: ReplayState | null;
  /** The bars on screen while replay is on (null when off). */
  slice: Candle[] | null;
  controller: ReplayController;
  select(): void;
  start(bars: Candle[], startTime: number): void;
  exit(): void;
}

export const useReplay = create<ReplayStore>((set, get) => {
  const controller = new ReplayController({
    onSlice: (slice) => set({ slice: slice as Candle[] }),
    onStep: (_bar, slice) => set({ slice: slice as Candle[] }),
    onChange: (state) => set({ state }),
    onEnd: () => undefined,
  });
  return {
    selecting: false,
    state: null,
    slice: null,
    controller,
    select: () => { if (get().state?.on) get().exit(); set({ selecting: true }); },
    start: (bars, startTime) => {
      set({ selecting: false });
      controller.enter(bars, { startTime });
    },
    exit: () => { controller.exit(); set({ selecting: false, state: null, slice: null }); },
  };
});

export { SPEED_LADDER };
