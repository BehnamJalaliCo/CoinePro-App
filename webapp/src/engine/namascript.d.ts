// Types for the vendored NamaScript engine (namascript.js): the reader's code runs in a sandboxed
// Web Worker over the bars and returns everything it plotted.
export interface ScriptPoint { time: number; value: number }
export interface ScriptResult {
  ok: boolean;
  error?: string;
  plots?: { name: string; color: string; width: number; style: number; data: ScriptPoint[] }[];
  shapes?: { name: string; color: string; shape: string; points: { time: number; price: number; shape: string }[] }[];
  hlines?: { price: number; title: string; color: string }[];
  bgs?: unknown[]; fills?: unknown[]; lines?: unknown[]; boxes?: unknown[]; zones?: unknown[]; labels?: { time: number; price: number; text: string; color: string }[];
  tables?: unknown[]; barcolors?: unknown[]; candleplots?: unknown[];
  alerts?: { msg: string; bars: number[] }[];
  inputs?: { key: string; type: string; def: unknown; title?: string; min?: number; max?: number; options?: string[] }[];
  strategy?: StrategyResult | null;
}
export interface StrategyResult {
  trades: number; net: number; win: number; pf: number; dd: number;
  equity: { t: number; e: number }[];
  list: { t: number; r: number; dir: 1 | -1 }[];
  avgWin: number; avgLoss: number; maxWin: number; maxLoss: number; longN: number; shortN: number;
  winStreak: number; lossStreak: number; avgTrade: number; expectancy: number; sharpe: number;
  grossProfit: number; grossLoss: number; wins: number; losses: number;
}
export function runScript(source: string, candles: unknown[], inputs?: Record<string, unknown>, timeoutMs?: number): Promise<ScriptResult>;
