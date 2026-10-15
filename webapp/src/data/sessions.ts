// Whether a market is trading now. Crypto never closes; forex, metals, energy and the CFD indices
// trade from Sunday 22:00 to Friday 22:00 UTC, with the daily 21:00–22:00 UTC break for metals,
// energy and indices (the broker's rollover hour).
import type { Market } from './types';

export function marketStatus(market: Market, now: Date = new Date()): boolean {
  if (market === 'crypto') return true;
  const day = now.getUTCDay();
  const h = now.getUTCHours();
  if (day === 6) return false;
  if (day === 0 && h < 22) return false;
  if (day === 5 && h >= 22) return false;
  if (market !== 'forex' && h === 21) return false;
  return true;
}
