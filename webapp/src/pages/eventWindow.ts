// «بازه‌ی خبر پرتأثیر» on an active signal (the phone's highImpactWarningsFor and SignalDetailScreen's
// warning card): the high-impact calendar events that move this market, from an hour ago to six
// hours ahead, soonest first. A risk warning, not a prediction.
import type { CalendarEvent } from '../data/intel';

export type Relevance = 'gold' | 'silver' | 'crypto';

/** The market a symbol belongs to for the calendar, or null where the calendar says nothing about it. */
export function relevanceForSymbol(symbol: string): Relevance | null {
  const s = symbol.toUpperCase();
  if (s === 'XAUUSD') return 'gold';
  if (s === 'XAGUSD') return 'silver';
  return s.endsWith('USDT') ? 'crypto' : null;
}

export const RECENT_MS = 60 * 60 * 1000;
export const HORIZON_MS = 6 * 60 * 60 * 1000;

export function highImpactWarningsFor(events: CalendarEvent[], symbol: string, now: number, horizonMs = HORIZON_MS): CalendarEvent[] {
  const rel = relevanceForSymbol(symbol);
  if (!rel) return [];
  return events
    .filter((e) => e.impact === 'high')
    // A saved copy from before the tag existed came from the week's file: both metals.
    .filter((e) => (e.relevance ?? ['gold', 'silver']).includes(rel))
    .filter((e) => e.time >= now - RECENT_MS && e.time <= now + horizonMs)
    .sort((a, b) => a.time - b.time);
}
