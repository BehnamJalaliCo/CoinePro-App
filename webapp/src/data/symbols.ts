// Which backend owns a symbol, and what kind of market it is. Crypto is TradeYar's (every *USDT);
// forex, metals, indices and energy are CoinePro-FX's fixed list (the alert route's 19, plus what
// academy/chart/symbols reports with data).
import type { Instrument, Market } from './types';
import { describe } from './names';

export const FX_SYMBOLS = [
  'XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY', 'USDCHF', 'AUDUSD', 'NZDUSD', 'USDCAD', 'EURGBP',
  'EURJPY', 'GBPJPY', 'AUDJPY', 'EURAUD', 'XTIUSD', 'XNGUSD', 'US30', 'US500', 'NAS100', 'DE40',
];

const INDICES = new Set(['US30', 'US500', 'NAS100', 'DE40', 'US100', 'UK100', 'GER40', 'JPN225']);
const ENERGY = new Set(['XTIUSD', 'XBRUSD', 'XNGUSD', 'USOIL', 'UKOIL']);

export type Backend = 'tradeyar' | 'coineprofx';

export function backendOf(symbol: string): Backend {
  return /USDT$|USDC$/.test(symbol) ? 'tradeyar' : 'coineprofx';
}

export function marketOf(symbol: string): Market {
  if (backendOf(symbol) === 'tradeyar') return 'crypto';
  if (INDICES.has(symbol)) return 'indices';
  if (ENERGY.has(symbol)) return 'energy';
  if (/^X(AU|AG|PT|PD)/.test(symbol)) return 'metals';
  return 'forex';
}

export function digitsFor(symbol: string, price?: number): number {
  const m = marketOf(symbol);
  if (m === 'forex') return symbol.includes('JPY') ? 3 : 5;
  if (symbol.startsWith('XAG')) return 3;
  if (m === 'metals' || m === 'indices' || m === 'energy') return 2;
  const p = Math.abs(price ?? 0);
  if (!p) return 2;
  if (p >= 1000) return 1;
  if (p >= 10) return 2;
  if (p >= 1) return 4;
  if (p >= 0.01) return 5;
  if (p >= 0.0001) return 7;
  return 8;
}

export function instrumentOf(symbol: string, price?: number): Instrument {
  const market = marketOf(symbol);
  const quote = market === 'crypto' ? (symbol.endsWith('USDC') ? 'USDC' : 'USDT') : symbol.length === 6 ? symbol.slice(3) : 'USD';
  const base = market === 'crypto' ? symbol.slice(0, -quote.length) : symbol.length === 6 ? symbol.slice(0, 3) : symbol;
  return {
    symbol, market, base, quote,
    nameFa: describe(symbol, market, base, quote, false), nameEn: describe(symbol, market, base, quote, true),
    digits: digitsFor(symbol, price),
  };
}

/** The interval ids a backend can draw, natively or folded from a finer timeframe. */
export function supportsInterval(symbol: string, interval: string): boolean {
  if (backendOf(symbol) === 'tradeyar') return true;
  return !['1', '3'].includes(interval);
}
