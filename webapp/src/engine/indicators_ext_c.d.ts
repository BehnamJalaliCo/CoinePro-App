// Types for indicators_ext_c.js: the indicators added for TradingView parity and the whole catalogue.
import type { IndicatorColumns, IndicatorDef, ProfileRow } from './indicators.js';
export const EXT_REGISTRY_C: Record<string, IndicatorDef>;
export const STUDIES: Record<string, IndicatorDef>;
export function profileRows(c: IndicatorColumns, a: number, b: number, rows?: number, vaPct?: number): { rows: ProfileRow[]; poc: number; vah: number; val: number } | null;
export function periodKey(t: number, anchor: string): number;
export function swings(h: number[], l: number[], len: number): { i: number; price: number; high: boolean }[];
export interface Swing { i: number; price: number; high: boolean }
/** Pattern kinds of Auto Chart Patterns: [Persian name, English name, direction (1 up, −1 down, 0 either)]. */
export const CHART_PATTERNS: Record<string, [string, string, number]>;
export function findChartPatterns(z: Swing[], tol: number, max?: number): { kind: string; pts: Swing[]; lines: Array<[{ i: number; price: number }, { i: number; price: number }]>; from: number; to: number }[];
