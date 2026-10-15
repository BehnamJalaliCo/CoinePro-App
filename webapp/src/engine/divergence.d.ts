export interface Detection { t: number; dir: 'bull' | 'bear' | 'neutral'; label: string; points: { t: number; price: number }[] }
export function detectDivergences(cs: { t: number; o: number; h: number; l: number; c: number }[], opts?: { period?: number; left?: number; right?: number }): Detection[];
