// Where the prices on screen come from, named — never invented (OLD CandleGateway.sourceName,
// QuoteSource.displayName, ChartController.noteQuoteVenue, ChartScreen's source caption).
//   candles  crypto: LBank (TradeYar relays LBank's candles unchanged); forex: MetaTrader 5 (the
//            CoinePro-FX master account's own feed). A spread names its legs' venue when they share one.
//   quote    the venue the live price feed reports in its `source` field (the forex snapshot sends
//            "finnhub"); crypto prices are LBank's. A source the app does not recognise has no name.
import { useMarket } from '../state/market';
import { backendOf, exprLegs } from './symbols';

/** A feed's `source` field as the venue's own spelling, or '' when it is not one we know. */
export function venueName(raw: unknown): string {
  const s = typeof raw === 'string' ? raw.toLowerCase() : '';
  if (s.includes('finnhub')) return 'Finnhub';
  if (s.includes('lbank')) return 'LBank';
  return '';
}

/** The venue a symbol's candles come from, or '' when it cannot be named. */
export function candleSource(symbol: string): string {
  const legs = exprLegs(symbol);
  if (legs) {
    const all = new Set(legs.map((s) => candleSource(s)));
    return all.size === 1 ? [...all][0]! : '';
  }
  if (!symbol) return '';
  return backendOf(symbol) === 'tradeyar' ? 'LBank' : 'MetaTrader 5';
}

/** The venue of the symbol's live price, as the feed last reported it, or ''. */
export function useQuoteSource(symbol: string): string {
  return useMarket((s) => s.tickers[symbol]?.source ?? '');
}

/**
 * «منبع کندل‌ها: X · آخرین قیمت از Y» — the price's half only when it names a different venue, and
 * nothing at all when the candles' venue is unknown.
 */
export function sourceCaption(candles: string, quote: string, fa: boolean): string {
  if (!candles) return '';
  const head = fa ? `منبع کندل‌ها: ⁦${candles}⁩` : `Candles from ${candles}`;
  if (!quote || quote === candles) return head;
  return `${head}  ·  ${fa ? `آخرین قیمت از ⁦${quote}⁩` : `last price from ${quote}`}`;
}

/** The caption for a symbol, live. */
export function useSourceCaption(symbol: string, fa: boolean): string {
  return sourceCaption(candleSource(symbol), useQuoteSource(symbol), fa);
}
