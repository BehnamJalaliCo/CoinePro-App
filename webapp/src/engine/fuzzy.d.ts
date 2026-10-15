// Types for the old Pro-Chart's fuzzy matcher (fuzzy.js).
export interface FuzzyMeta { symbol: string; desc?: string; base?: string; popular?: boolean; cat?: string }
export function matchSymbol(meta: FuzzyMeta, q: string): number;
export function highlightPositions(text: string, q: string): number[];
export function searchSymbols<T extends FuzzyMeta>(metaList: T[], q: string, cat?: string): T[];
