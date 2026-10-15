// Where an order for a symbol goes. Every *USDT symbol is a TradeYar order (LBank futures under the
// hood, /up/tradeyar/api/mobile/v1/trade/*); the fixed forex, metals, energy and index list belongs
// to CoinePro-FX, which on Pro Chart is signals only (owner, 2026-10-15: its MT5 executor is off),
// so those symbols trade on paper with a note saying so. Anything else has no venue at all.
// Pure, so the routing is tested without a browser.
import { FX_SYMBOLS } from '../data/symbols';

export type Venue = 'tradeyar' | 'coineprofx';

export const VENUES: Venue[] = ['tradeyar', 'coineprofx'];

/** The platforms that take live orders from the chart: TradeYar (crypto on LBank) only. */
export const LIVE_VENUES: Venue[] = ['tradeyar'];

/** The venue a live order for this symbol would go to, or null when it can only be paper. */
export function liveVenueOf(symbol: string): Venue | null {
  const v = venueOf(symbol);
  return v && LIVE_VENUES.includes(v) ? v : null;
}

/** True for the forex / metals / index list: signals only on Pro Chart. */
export const isSignalOnly = (symbol: string) => venueOf(symbol) === 'coineprofx';

export function venueOf(symbol: string): Venue | null {
  const s = (symbol ?? '').trim().toUpperCase();
  if (/^[A-Z0-9]{2,16}USDT$/.test(s)) return 'tradeyar';
  if (FX_SYMBOLS.includes(s)) return 'coineprofx';
  return null;
}

/** The member prefix of each platform; the trade, venue and execution routes hang off it. */
export const MEMBER_ROOT: Record<Venue, string> = {
  tradeyar: '/up/tradeyar/api/mobile/v1',
  coineprofx: '/up/coineprofx/api/user/mobile',
};

export const tradePath = (v: Venue, rest: string) => `${MEMBER_ROOT[v]}/trade/${rest.replace(/^\/+/, '')}`;

/** The platform's name and the market it reaches, in both languages. */
export const VENUE_LABEL: Record<Venue, { fa: string; en: string; broker: string }> = {
  tradeyar: { fa: 'تریدیار', en: 'TradeYar', broker: 'LBank' },
  coineprofx: { fa: 'کوین‌پرو اف‌ایکس', en: 'CoinePro-FX', broker: 'MT5' },
};

/** Order kinds each venue takes from the app: TradeYar sends market and limit to LBank only. */
export const VENUE_TYPES: Record<Venue, ('market' | 'limit' | 'stop')[]> = {
  tradeyar: ['market', 'limit'],
  coineprofx: ['market', 'limit', 'stop'],
};

/** The leverage the app may set (TradeYar: live_orders.MAX_LEVERAGE). */
export const MAX_LEVERAGE: Record<Venue, number> = { tradeyar: 50, coineprofx: 500 };

/** How far a limit, stop or target may sit from the live price before the venue refuses it. */
export const PRICE_BAND: Record<Venue, number | null> = { tradeyar: 0.25, coineprofx: null };
