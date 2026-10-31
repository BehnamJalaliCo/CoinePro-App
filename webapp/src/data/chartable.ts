// The markets that can be opened as a chart (OLD core/auth PlatformCapabilities.chartableSymbols,
// 5.19.3). TradeYar's price feed carries every market on its venue and its candle routes serve about
// half of them — the rest answer the chart with «در این پلتفرم پشتیبانی نمی‌شود» — so the screener
// and the heat map list only what can become a chart:
//
//   crypto   GET /up/tradeyar/api/mobile/v1/auth/methods → symbols: [{symbol}]  (the candle scope)
//   forex    GET /up/coineprofx/api/academy/chart/symbols?with_data=true → {symbols: [...]}  (symbols with candles)
//
// A null or empty answer, or a failed one, filters nothing: a server that has not said which
// markets it serves has not said any is missing. Read once per page load.
import { request } from './http';
import { academyToken, guestToken } from './forexAccess';
import { accessFor } from './auth';
import { backendOf } from './symbols';
import type { Ticker } from './types';

let crypto: Promise<Set<string> | null> | null = null;
let forex: Promise<Set<string> | null> | null = null;

const setOf = (raw: unknown): Set<string> | null => {
  if (!Array.isArray(raw)) return null;
  const out = new Set<string>();
  for (const x of raw) {
    const s = typeof x === 'string' ? x : x && typeof x === 'object' ? (x as { symbol?: unknown }).symbol : null;
    if (typeof s === 'string' && s.trim()) out.add(s.trim().toUpperCase());
  }
  return out.size ? out : null;
};

function cryptoScope(): Promise<Set<string> | null> {
  if (!crypto) crypto = request<{ symbols?: unknown }>('/up/tradeyar/api/mobile/v1/auth/methods').then((r) => setOf(r.symbols)).catch(() => { crypto = null; return null; });
  return crypto;
}

function forexScope(): Promise<Set<string> | null> {
  if (!forex) {
    forex = (async () => {
      const token = (await academyToken(await accessFor('coineprofx'))) ?? (await guestToken());
      const r = await request<{ symbols?: unknown }>('/up/coineprofx/api/academy/chart/symbols?with_data=true', { token });
      return setOf(r.symbols);
    })().catch(() => { forex = null; return null; });
  }
  return forex;
}

export interface Chartable { crypto: Set<string> | null; forex: Set<string> | null }

/** Both platforms' answers, independently: one being unreachable says nothing about the other. */
export async function chartable(): Promise<Chartable> {
  const [c, f] = await Promise.all([cryptoScope(), forexScope()]);
  return { crypto: c, forex: f };
}

/** Whether a symbol may be listed, by the answers in hand (null = the platform did not say). */
export function isChartable(symbol: string, scope: Chartable | null): boolean {
  if (!scope) return true;
  const set = backendOf(symbol) === 'tradeyar' ? scope.crypto : scope.forex;
  return !set || set.has(symbol.toUpperCase());
}

/** A heat-map tile with no change and no volume is a market that did not trade (or a feed that did not say): not drawn. */
export const deadTile = (x: Pick<Ticker, 'changePct' | 'volume'>): boolean => !x.changePct && !(x.volume != null && x.volume > 0);
