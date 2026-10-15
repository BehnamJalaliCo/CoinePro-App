// Types for candlePatterns.js: the old detector (about 19 patterns, Persian labels) and the single
// TradingView patterns the Patterns tab adds.
export function detectCandlePatterns(cs: { t: number; o: number; h: number; l: number; c: number }[]): { t: number; dir: 'bull' | 'bear' | 'neutral'; label: string }[];
export const PATTERN_COUNT: number;
export const PATTERN_EN: Record<string, string>;
export function patternName(label: string, lang: 'fa' | 'en'): string;
export interface TvPattern { id: string; en: string; fa: string; abbr: string; dir: 'bull' | 'bear' | 'neutral'; bars: number }
export const TV_PATTERNS: TvPattern[];
export function detectPattern(id: string, cs: { t: number; o: number; h: number; l: number; c: number }[], trend?: string): number[];
