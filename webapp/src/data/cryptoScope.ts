// What TradeYar actually charts. Its price list (/api/crypto/prices) carries every LBank USDT
// perpetual, but the candle and depth routes answer only the platform's crypto scope: anything else
// is `422 TYR-021` («مربوط به بازار فارکس و فلزات است» / «در این پلتفرم پشتیبانی نمی‌شود»).
// A row the reader cannot open is not shown — the same rule as a symbol without artwork.
//
// Three sources, in order: TradeYar's own non-crypto rule (app/api/mobile/scope.py
// `is_forex_or_metal`, mirrored here word for word), the symbols measured as refused on
// 2026-10-31, and whatever this browser has since been refused (remembered, one-way).
import { useMarket } from '../state/market';

const FX_CODES = new Set(`
  USD EUR GBP JPY CHF CAD AUD NZD CNY CNH HKD SGD SEK NOK DKK PLN CZK HUF
  TRY ZAR MXN BRL RUB INR KRW THB IDR MYR PHP ILS AED SAR QAR KWD BHD OMR
  IRR IQD EGP NGN CLP COP ARS PEN TWD VND UAH RON BGN HRK ISK
  XAU XAG XPT XPD`.split(/\s+/).filter(Boolean));

const NON_CRYPTO_UNDERLYINGS = new Set(`
  GOLD SILVER PLATINUM PALLADIUM XAU XAG XPT XPD WTI BRENT OIL NATGAS
  SPX SPX500 NDX NAS100 DJI US30 DAX FTSE NIKKEI HSI
  TSLA NVDA AAPL MSFT META AMZN GOOG GOOGL NFLX AMD INTC BABA COIN MSTR
  SPCX SKHYNIX SMCI PLTR ASML TSM ORCL CRM UBER ABNB`.split(/\s+/).filter(Boolean));

const QUOTES = ['USDT', 'USDC', 'USD', 'PERP'];

/** Measured: `422 TYR-021` from /api/crypto/candles on 2026-10-31. */
const MEASURED = new Set([
  'GOLDUSDT', 'SILVERUSDT', 'NAS100USDT', 'SPX500USDT', 'US30USDT', 'EURUSDT', 'GBPUSDT', 'JPYUSDT',
  'KODEX200USDT', 'KR200USDT', 'HK1810USDT', 'HK0992USDT', 'HK0700USDT', 'CSOPSKHYNIX2LUSDT',
  'CSOPSAMSUNG2LUSDT', 'FOXAUSDT', 'USD1USDT',
]);

/** Exchange-listed shares and share baskets wearing a USDT quote (HK0700USDT, CSOP…2LUSDT). */
const EQUITY_LIKE = /^(HK\d{4}|CSOP[A-Z0-9]+|KODEX\d+|KR\d{3})USDT$/;

function underlying(s: string): string {
  for (const q of QUOTES) if (s.endsWith(q) && s.length > q.length) return s.slice(0, -q.length);
  return s;
}

/** TradeYar's `is_forex_or_metal`: a six-letter ISO pair, or a metal / index / share underlying. */
export function isForexOrMetal(symbol: string): boolean {
  const s = symbol.trim().toUpperCase().replace(/[/_-]/g, '');
  if (!s) return false;
  const m = /^([A-Z]{3})([A-Z]{3})$/.exec(s);
  if (m && FX_CODES.has(m[1]!) && FX_CODES.has(m[2]!)) return true;
  return NON_CRYPTO_UNDERLYINGS.has(underlying(s));
}

const KEY = 'pc.offscope.v1';

const learned: Set<string> = (() => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown;
    return new Set(Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);
  } catch { return new Set(); }
})();

/** Whether TradeYar will refuse to chart this symbol, so no list should offer it. */
export function isOffScope(symbol: string): boolean {
  return MEASURED.has(symbol) || learned.has(symbol) || EQUITY_LIKE.test(symbol) || isForexOrMetal(symbol);
}

/** A `TYR-021` answer: remembered, and the row leaves every list now. */
export function learnOffScope(symbol: string): void {
  if (!learned.has(symbol)) {
    learned.add(symbol);
    try { localStorage.setItem(KEY, JSON.stringify([...learned])); } catch { /* for this visit */ }
  }
  useMarket.getState().drop(symbol);
}

/** Whether an error body is TradeYar's scope gate (`422` with `TYR-021`). */
export function isScopeRefusal(status: number, body: unknown): boolean {
  if (status !== 422) return false;
  const text = typeof body === 'string' ? body : JSON.stringify(body ?? '');
  return text.includes('TYR-021') || text.includes('unsupported_symbol');
}
