// Types for the chart-type builders the app calls (charttypes_ext.js, from the old Pro-Chart).
export interface ExtColours { up?: string; down?: string }
export interface ExtCandle { time: number; open: number; high: number; low: number; close: number; color: string; borderColor: string; wickColor: string }
export function hollowCandles(candles: unknown[], opts?: ExtColours): ExtCandle[];
export function highLowData(candles: unknown[], opts?: ExtColours): ExtCandle[];
export function pnfColumns(candles: unknown[], opts?: { box?: number; reversal?: number; atrLen?: number; source?: 'close' | 'hl'; up?: string; down?: string }): {
  columns: { dir: number; from: number; to: number }[];
  box: number;
};
