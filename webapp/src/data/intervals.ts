// Interval ids follow TradingView's own notation: minutes as a number, then D, W, M.
export interface IntervalDef { id: string; seconds: number; label: string }

export const INTERVALS: IntervalDef[] = [
  { id: '1', seconds: 60, label: '1m' },
  { id: '3', seconds: 180, label: '3m' },
  { id: '5', seconds: 300, label: '5m' },
  { id: '15', seconds: 900, label: '15m' },
  { id: '30', seconds: 1800, label: '30m' },
  { id: '60', seconds: 3600, label: '1h' },
  { id: '120', seconds: 7200, label: '2h' },
  { id: '240', seconds: 14400, label: '4h' },
  { id: '1D', seconds: 86400, label: '1D' },
  { id: '1W', seconds: 604800, label: '1W' },
  { id: '1M', seconds: 2592000, label: '1M' },
];

export function intervalLabel(id: string): string {
  return INTERVALS.find((i) => i.id === id)?.label ?? id;
}

export function intervalSeconds(id: string): number {
  return INTERVALS.find((i) => i.id === id)?.seconds ?? 3600;
}
