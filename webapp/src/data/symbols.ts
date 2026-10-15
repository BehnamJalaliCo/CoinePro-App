// Which backend owns a symbol, and what kind of market it is. Crypto is TradeYar's (every *USDT);
// forex, metals, indices and energy are CoinePro-FX's fixed list (the alert route's 19, plus what
// academy/chart/symbols reports with data).
import type { Instrument, Market } from './types';
import { describe } from './names';
import { parseSymbolExpr } from '../engine/symbolExpr.js';

export const FX_SYMBOLS = [
  'XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY', 'USDCHF', 'AUDUSD', 'NZDUSD', 'USDCAD', 'EURGBP',
  'EURJPY', 'GBPJPY', 'AUDJPY', 'EURAUD', 'XTIUSD', 'XNGUSD', 'US30', 'US500', 'NAS100', 'DE40',
];

const INDICES = new Set(['US30', 'US500', 'NAS100', 'DE40', 'US100', 'UK100', 'GER40', 'JPN225']);
const ENERGY = new Set(['XTIUSD', 'XBRUSD', 'XNGUSD', 'USOIL', 'UKOIL']);

export type Backend = 'tradeyar' | 'coineprofx';

export function backendOf(symbol: string): Backend {
  const legs = exprLegs(symbol);
  const s = legs ? legs[0]! : symbol;
  return /USDT$|USDC$/.test(s) ? 'tradeyar' : 'coineprofx';
}

// ── Spreads and formulas (TradingView's BTCUSDT/ETHUSDT, XAUUSD*2, (EURUSD+GBPUSD)/2) ──────────
const EXPR_CHARS = /[-+*/() ]/;
const exprCache = new Map<string, string[] | null>();

/** The symbols an expression is built from, or null for a plain symbol (or nonsense). */
export function exprLegs(symbol: string): string[] | null {
  if (!symbol || !EXPR_CHARS.test(symbol)) return null;
  let legs = exprCache.get(symbol);
  if (legs === undefined) {
    const p = parseSymbolExpr(symbol);
    legs = p && p.isExpr && p.symbols.length && p.symbols.every((s) => /^[A-Z][A-Z0-9]{1,15}$/.test(s)) ? p.symbols : null;
    exprCache.set(symbol, legs);
  }
  return legs;
}

export function isExpression(symbol: string): boolean {
  return exprLegs(symbol) !== null;
}

/** An expression written the way the chart shows it: upper case, no spaces. */
export function normaliseExpression(raw: string): string | null {
  const s = raw.trim().toUpperCase().replace(/\s+/g, '');
  return exprLegs(s) ? s : null;
}

export function marketOf(symbol: string): Market {
  const legs = exprLegs(symbol);
  if (legs) return marketOf(legs[0]!);
  if (backendOf(symbol) === 'tradeyar') return 'crypto';
  if (INDICES.has(symbol)) return 'indices';
  if (ENERGY.has(symbol)) return 'energy';
  if (/^X(AU|AG|PT|PD)/.test(symbol)) return 'metals';
  return 'forex';
}

export function digitsFor(symbol: string, price?: number): number {
  if (isExpression(symbol)) {
    // A ratio or a sum has no instrument precision: as many decimals as its size needs.
    // Six significant figures, as TradingView shows a ratio.
    const p = Math.abs(price ?? 0);
    if (!p || !Number.isFinite(p)) return 5;
    return Math.max(2, Math.min(8, 5 - Math.floor(Math.log10(p))));
  }
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
  // A spread or formula is named by itself; it has no base, quote or catalogue name.
  if (isExpression(symbol)) return { symbol, market, base: symbol, quote: '', digits: digitsFor(symbol, price) };
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
  const legs = exprLegs(symbol);
  if (legs) return legs.every((s) => supportsInterval(s, interval));
  if (backendOf(symbol) === 'tradeyar') return true;
  return !['1', '2', '3'].includes(interval);
}
