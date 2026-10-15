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
  /** Practice trades inside the replay: opened and closed at the replayed bar's close. */
  trades: ReplayTrade[];
  trade(side: 'long' | 'short'): void;
  flatten(): void;
}

export interface ReplayTrade { side: 'long' | 'short'; entry: number; at: number; exit?: number; exitAt?: number }

/** A replayed trade's result in percent of its entry, at `price` while it is open. */
export const replayPnl = (t: ReplayTrade, price: number) => ((t.exit ?? price) - t.entry) / t.entry * 100 * (t.side === 'long' ? 1 : -1);

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
    exit: () => { controller.exit(); set({ selecting: false, state: null, slice: null, trades: [] }); },
    trades: [],
    trade: (side) => {
      const last = get().slice?.[get().slice!.length - 1];
      if (!last) return;
      // An opposite trade closes the open one first, as a one-position practice account does.
      const open = get().trades.find((t) => t.exit == null);
      if (open && open.side !== side) { get().flatten(); return; }
      set({ trades: [...get().trades, { side, entry: last.c, at: last.t }] });
    },
    flatten: () => {
      const last = get().slice?.[get().slice!.length - 1];
      if (!last) return;
      set({ trades: get().trades.map((t) => (t.exit == null ? { ...t, exit: last.c, exitAt: last.t } : t)) });
    },
  };
});

export { SPEED_LADDER };
