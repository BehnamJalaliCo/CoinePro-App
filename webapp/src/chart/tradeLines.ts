// Trading lines on the chart, as TradingView draws them for positions, orders and their stop loss
// and take profit: a line across the price pane with its price on the axis, and a tag beside the
// scale (what it is, a figure such as the open P&L, and ×). A line with `onMove` can be dragged;
// the new price is handed over on release. Replay practice and paper trading each keep their own
// group; TradeLinesHost (shell/TradeLines.tsx) draws them all on the chart bus's main series.
import { create } from 'zustand';

export interface TradeLine {
  id: string;
  price: number;
  color: string;
  /** The tag's first chip: "Long 0.25", "SL", "Buy limit". */
  label: string;
  /** A second chip, e.g. the open P&L, coloured by `valueColor`. */
  value?: string;
  valueColor?: string;
  dashed?: boolean;
  /** Draggable when set: called with the dropped price. */
  onMove?(price: number): void;
  /** The tag's ×: close the position, cancel the order, remove the stop. */
  onClose?(): void;
  closeTitle?: string;
}

interface TradeLinesState {
  groups: Record<string, TradeLine[]>;
  set(group: string, lines: TradeLine[]): void;
}

export const useTradeLines = create<TradeLinesState>((set) => ({
  groups: {},
  set: (group, lines) => set((s) => {
    const was = s.groups[group];
    if (!lines.length && !was?.length) return s;
    return { groups: { ...s.groups, [group]: lines } };
  }),
}));

/** Rounds a dragged price to the series' own step. */
export function roundTo(price: number, precision: number): number {
  const p = Math.max(0, Math.min(10, Math.round(precision)));
  return Number(price.toFixed(p));
}

/** The line within `slop` pixels of `y` that can be dragged, nearest first; -1 when none. */
export function hitLine(ys: Array<{ y: number | null; drag: boolean }>, y: number, slop = 5): number {
  let best = -1;
  let gap = slop + 1;
  ys.forEach((l, i) => {
    if (!l.drag || l.y == null) return;
    const d = Math.abs(l.y - y);
    if (d <= slop && d < gap) { gap = d; best = i; }
  });
  return best;
}
