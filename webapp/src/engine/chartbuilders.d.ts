export const NONSTANDARD: string[];
export function buildNonStandard(type: string, candles: unknown[], params?: Record<string, unknown>): unknown;
export interface BuiltBar { t: number; o: number; h: number; l: number; c: number }
export function avgRange(candles: unknown[], period?: number): number;
export function rangeBars(candles: unknown[], range?: number): BuiltBar[];
export function lineBreak(candles: unknown[], lines?: number): BuiltBar[];
export function kagi(candles: unknown[], reversal?: number, atrLen?: number): { t: number; value: number }[];
