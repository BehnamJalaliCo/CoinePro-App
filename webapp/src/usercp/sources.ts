// What the panel reads from the two platforms themselves (the wire contract): entitlements, VIP
// membership, positions, orders, closed trades, alerts, signal executions and the exchange link.
// Each answer is made into one shape for both; a platform the reader is not signed in to, or a route
// it does not have, is `null` — the panel shows a dash, never a guess.
import { authed } from '../data/auth';
import { ROOT, num, ms, str } from '../data/member';
import { useSession } from '../data/session';
import type { Backend } from '../data/symbols';

export const BACKENDS: Backend[] = ['tradeyar', 'coineprofx'];
export const signedOn = (b: Backend) => Boolean(useSession.getState().sessions[b]);
const call = <T,>(b: Backend, path: string) => authed<T>(b, `${ROOT[b]}/${path}`);
const settle = async <T,>(b: Backend, job: () => Promise<T>): Promise<T | null> => {
  if (!signedOn(b)) return null;
  try { return await job(); } catch { return null; }
};

export interface Entitlement { backend: Backend; premium: boolean; plan: string; endsAt: number | null; all: boolean }
export const entitlement = (b: Backend) => settle(b, async (): Promise<Entitlement> => {
  const r = await call<Record<string, unknown>>(b, 'entitlements');
  return { backend: b, premium: Boolean(r.premium), plan: str(r.plan), endsAt: ms(r.ends_at), all: Boolean(r.all) };
});

export interface Membership { status: string; message: string; nextStep: string; exchange: string; uid: string }
export const membership = () => settle('tradeyar', async (): Promise<Membership> => {
  const r = await call<Record<string, unknown>>('tradeyar', 'membership/status');
  return { status: str(r.status), message: str(r.message_fa), nextStep: str(r.next_step), exchange: str(r.exchange), uid: str(r.uid) };
});

export interface Position { backend: Backend; symbol: string; side: string; quantity: number | null; entry: number | null; mark: number | null; pnl: number | null; leverage: number | null; liquidation: number | null }
export const positions = (b: Backend) => settle(b, async (): Promise<Position[]> => {
  const r = await call<{ items?: Record<string, unknown>[] }>(b, 'trade/positions');
  return (r.items ?? []).map((x) => ({
    backend: b, symbol: str(x.symbol), side: str(x.side ?? x.direction).toLowerCase(), quantity: num(x.quantity ?? x.volume),
    entry: num(x.entry_price ?? x.entry), mark: num(x.mark_price ?? x.current_price), pnl: num(x.unrealized_pnl ?? x.pnl ?? x.profit),
    leverage: num(x.leverage), liquidation: num(x.liquidation_price),
  }));
});

export interface Order { backend: Backend; id: string; symbol: string; side: string; type: string; quantity: number | null; price: number | null; status: string; at: number | null }
export const orders = (b: Backend) => settle(b, async (): Promise<Order[]> => {
  const r = await call<{ items?: Record<string, unknown>[] }>(b, 'trade/orders?limit=50');
  return (r.items ?? []).map((x) => ({
    backend: b, id: str(x.id), symbol: str(x.symbol), side: str(x.side).toLowerCase(), type: str(x.type), quantity: num(x.quantity),
    price: num(x.price), status: str(x.status), at: ms(x.created_at ?? x.updated_at),
  }));
});

export interface TradeAccount { available: number | null; equity: number | null; margin: number | null; unrealized: number | null; enabled: boolean }
export const tradeAccount = (b: Backend) => settle(b, async (): Promise<TradeAccount> => {
  const r = await call<Record<string, unknown>>(b, 'trade/account');
  return { available: num(r.available), equity: num(r.equity), margin: num(r.margin_used), unrealized: num(r.unrealized_pnl), enabled: Boolean(r.trading_enabled) };
});

export interface Closed { backend: Backend; id: string; symbol: string; side: string; opened: number | null; closed: number | null; entry: number | null; exit: number | null; qty: number | null; pnl: number; fee: number; currency: string }

/** Closed trades of the last [days]: TradeYar's portfolio history, CoinePro-FX's trade history. */
export const closedTrades = (b: Backend, days: number) => settle(b, async (): Promise<Closed[]> => {
  const out: Closed[] = [];
  const since = Date.now() - days * 86_400_000;
  if (b === 'coineprofx') {
    for (let page = 1; page <= 3; page++) {
      const r = await authed<{ items?: Record<string, unknown>[]; total_pages?: number }>(b, `/up/coineprofx/api/user/trade-history?page=${page}&per_page=100`);
      for (const x of r.items ?? []) {
        const closed = ms(x.close_time);
        if (closed && closed < since) continue;
        out.push({ backend: b, id: str(x.id), symbol: str(x.symbol), side: str(x.direction).toLowerCase(), opened: ms(x.open_time), closed, entry: num(x.entry_price), exit: num(x.exit_price), qty: num(x.volume), pnl: num(x.net_profit) ?? 0, fee: (num(x.commission) ?? 0) + (num(x.swap) ?? 0), currency: 'USD' });
      }
      if (!r.total_pages || page >= r.total_pages) break;
    }
    return out;
  }
  const to = Math.floor(Date.now() / 1000); const from = Math.floor(since / 1000);
  const r = await authed<{ trades?: Record<string, unknown>[] }>(b, `/up/tradeyar/api/mobile/v1/portfolio/history?from=${from}&to=${to}&page=1&per_page=100`);
  for (const x of r.trades ?? []) out.push({ backend: b, id: str(x.id), symbol: str(x.symbol), side: str(x.side).toLowerCase(), opened: ms(x.opened_at), closed: ms(x.closed_at), entry: num(x.entry), exit: num(x.exit), qty: num(x.quantity), pnl: num(x.pnl) ?? 0, fee: num(x.fee) ?? 0, currency: str(x.currency) || 'USDT' });
  return out;
});

/** The start of today in this browser's time zone. */
export function startOfToday(now = new Date()): number {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
}

/** Today's realised P&L from closed trades, per currency. */
export function todayPnl(trades: Closed[], now = new Date()): Record<string, number> {
  const from = startOfToday(now);
  const out: Record<string, number> = {};
  for (const t of trades) if ((t.closed ?? 0) >= from) out[t.currency] = (out[t.currency] ?? 0) + t.pnl;
  return out;
}

export const alertsCount = (b: Backend) => settle(b, async () => {
  const r = await call<{ items?: Record<string, unknown>[] }>(b, 'alerts');
  return (r.items ?? []).filter((x) => x.active !== false).length;
});

export interface Execution { backend: Backend; id: string; signalId: string; symbol: string; direction: string; timeframe: string; venue: string; status: string; quantity: string; at: number | null; error: string }
export const executions = (b: Backend) => settle(b, async (): Promise<Execution[]> => {
  const r = await call<{ items?: Record<string, unknown>[] }>(b, 'executions?limit=100');
  return (r.items ?? []).map((x) => {
    const sig = (x.signal ?? {}) as Record<string, unknown>;
    return { backend: b, id: str(x.id), signalId: str(x.signal_id), symbol: str(sig.symbol ?? x.symbol), direction: str(sig.direction ?? x.side), timeframe: str(sig.timeframe), venue: [str(x.venue), str(x.product)].filter(Boolean).join(' '), status: str(x.status), quantity: str(x.quantity), at: ms(x.created_at), error: str(x.error_message) };
  });
});
export const closeExecution = (b: Backend, id: string) => call(b, `executions/${encodeURIComponent(id)}/close`);

export interface Venue { backend: Backend; configured: boolean; connected: boolean; status: string; permission: string; keyHint: string }
export const venue = (b: Backend) => settle(b, async (): Promise<Venue> => {
  const x = await call<Record<string, unknown>>(b, 'venues/lbank');
  return { backend: b, configured: Boolean(x.configured), connected: Boolean(x.connected), status: str(x.status), permission: str(x.permission), keyHint: str(x.key_hint) };
});

export const sumBy = <T,>(list: T[], f: (x: T) => number) => list.reduce((n, x) => n + f(x), 0);
export const flat = <T,>(lists: (T[] | null)[]): T[] => lists.flatMap((x) => x ?? []);
