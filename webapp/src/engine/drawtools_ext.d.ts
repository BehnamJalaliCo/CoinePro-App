// Types for the drawing tools' registry (drawtools_ext.js): only what the app reads.
export interface Level { v: number; on: boolean; color: string }
export const LEVEL_DEFAULTS: Record<string, { v: number; on: boolean; color?: string }[]>;
export const LEVEL_COLORS: string[];
export function normLevels(d: Record<string, unknown>, type?: string): Level[];
export function levelValues(d: Record<string, unknown>, type?: string): number[];
export const EXT_REGISTRY: Record<string, { label: string; points: number; freehand?: boolean; poly?: boolean; screen?: boolean }>;
export const EXT_LABELS: Record<string, string>;
export const EXT_ONE: string[];
export const EXT_POLY: string[];
export function vwapPath(candles: { t: number; o: number; h: number; l: number; c: number; v?: number }[], t0: number): { t: number; v: number; sd: number }[];
export function volumeRows(candles: { t: number; o: number; h: number; l: number; c: number; v?: number }[], t0: number, t1: number, rows?: number, va?: number): {
  rows: { lo: number; hi: number; up: number; down: number; poc: boolean; va: boolean }[]; poc: number; vah: number; val: number; lo: number; hi: number; bars: number;
} | null;
export function dashOf(d: Record<string, unknown>): number[];
