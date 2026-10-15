// Types for the vendored indicator registry (indicators.js + indicators_ext_a/b.js): 108 studies,
// each a pure function of the bars. Only the surface the app reads is described.
export interface IndicatorColumns { open: number[]; high: number[]; low: number[]; close: number[]; volume: number[]; time: number[] }
export type Values = (number | null)[];
export interface IndicatorOutput {
  line?: Values; signal?: Values; signal2?: Values; signal2Color?: string;
  hist?: Values; histMode?: 'sign'; palette?: (v: number | null, k: number) => string; macd?: boolean;
  hists?: { data: Values; color: string }[];
  lines?: { data: Values; color: string; dashed?: boolean; gaps?: boolean; connectNulls?: boolean; name?: string }[];
  upper?: Values; basis?: Values; lower?: Values; multi?: boolean;
  cloud?: number[]; cloudColors?: string[];
  guides?: number[]; range?: [number, number]; zone?: [number, number];
  markers?: { time: number; position: 'aboveBar' | 'belowBar' | 'inBar'; shape: 'circle' | 'square' | 'arrowUp' | 'arrowDown'; color: string; text?: string }[];
  levels?: { price: number; color: string; label?: string; extendRight?: boolean }[];
  /** Re-coloured bars (Bollinger Bars). */
  candles?: { time: number; open: number; high: number; low: number; close: number; color: string }[];
  /** A small table over the chart (Performance, Seasonality). */
  table?: { title: string; rows: [string, string, (string | null)?][] };
  /** Volume profiles: one segment per range; 'right' hangs off the pane's right edge, 'inside' sits in its range. */
  profile?: { segments: ProfileSegment[]; place: 'right' | 'inside' };
}
export interface ProfileRow { lo: number; hi: number; up: number; down: number; va: boolean; poc: boolean }
export interface ProfileSegment { from: number; to: number; rows: ProfileRow[]; poc: number; vah: number; val: number }
export interface IndicatorDef {
  label: string;
  pane: 'main' | 'sub';
  inputs: Record<string, number | string | boolean>;
  color: string;
  calc(c: IndicatorColumns, inputs: Record<string, unknown>): IndicatorOutput;
  /** Reads the bars on screen (inputs __from/__to): recomputed as the chart scrolls. */
  visibleRange?: boolean;
  /** Reads a second symbol (input `symbol`; inputs __other are its closes on these bars). */
  other?: boolean;
}
export const REGISTRY: Record<string, IndicatorDef>;
export const SOURCE_OPTS: string[];
export function resolveSrc(c: IndicatorColumns, name: string): number[];
export function sma(src: number[], p: number): (number | null)[];
export function ema(src: number[], p: number): (number | null)[];
export function rsi(closes: number[], p?: number): (number | null)[];
export function atr(h: number[], l: number[], c: number[], p?: number): (number | null)[];
export function macd(c: number[], fast?: number, slow?: number, sig?: number): { macd: (number | null)[]; signal: (number | null)[]; hist: (number | null)[] };
export function bollinger(c: number[], p?: number, mult?: number): { upper: (number | null)[]; basis: (number | null)[]; lower: (number | null)[] };
