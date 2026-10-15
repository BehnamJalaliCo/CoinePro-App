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
}
export interface IndicatorDef {
  label: string;
  pane: 'main' | 'sub';
  inputs: Record<string, number | string | boolean>;
  color: string;
  calc(c: IndicatorColumns, inputs: Record<string, unknown>): IndicatorOutput;
}
export const REGISTRY: Record<string, IndicatorDef>;
export const SOURCE_OPTS: string[];
export function resolveSrc(c: IndicatorColumns, name: string): number[];
