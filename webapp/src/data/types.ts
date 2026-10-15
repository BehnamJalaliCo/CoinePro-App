/** One bar. `t` is the bar's open time in Unix seconds, as lightweight-charts and the old engine expect. */
export interface Candle { t: number; o: number; h: number; l: number; c: number; v: number }

export type Market = 'crypto' | 'forex' | 'metals' | 'indices' | 'energy';

export interface Instrument {
  symbol: string;        // BTCUSDT, EURUSD, XAUUSD
  market: Market;
  base: string;          // BTC, EUR, XAU
  quote: string;         // USDT, USD
  nameFa?: string;
  nameEn?: string;
  digits: number;        // price precision on screen
}

export interface Ticker {
  symbol: string;
  last: number;
  change: number;        // absolute, against the price 24h ago
  changePct: number;
  high?: number;
  low?: number;
  volume?: number;
  prevClose?: number;
  ts: number;
}
