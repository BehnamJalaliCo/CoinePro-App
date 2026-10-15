// The live-trade calls, one function per route, on the reader's own session for that platform
// (data/auth.ts `authed`: the bearer handle, one refresh-and-retry on 401). Writes always carry a
// `client_request_id` so a retry after a dropped answer never places a second order.
import { authed } from '../data/auth';
import { MEMBER_ROOT, tradePath, type Venue } from './venue';
import { readAccount, readFills, readOrder, readPosition, type Account, type Fill, type Order, type Position, type PosSide, type OrderSide } from './wire';

type Row = Record<string, unknown>;

export async function getAccount(v: Venue): Promise<Account> {
  return readAccount(await authed<Row>(v, tradePath(v, 'account')));
}

export async function getPositions(v: Venue, symbol?: string): Promise<Position[]> {
  const q = symbol ? `?symbol=${encodeURIComponent(symbol)}` : '';
  const r = await authed<{ items?: Row[] }>(v, tradePath(v, `positions${q}`));
  return (r.items ?? []).map(readPosition).filter((x): x is Position => x != null);
}

export async function getOrders(v: Venue, limit = 50): Promise<Order[]> {
  const r = await authed<{ items?: Row[] }>(v, tradePath(v, `orders?limit=${limit}`));
  return (r.items ?? []).map(readOrder).filter((x): x is Order => x != null);
}

export interface PlaceBody {
  client_request_id: string;
  symbol: string;
  side: OrderSide;
  type: 'market' | 'limit' | 'stop';
  quantity: number;
  price: number | null;
  reduce_only: boolean;
  stop_loss: number | null;
  take_profit: number | null;
}

export async function placeOrder(v: Venue, body: PlaceBody): Promise<Order | null> {
  const r = await authed<{ order?: Row }>(v, tradePath(v, 'orders'), { body });
  return r.order ? readOrder(r.order) : null;
}

export async function amendOrder(v: Venue, id: string, price: number): Promise<Order | null> {
  const r = await authed<{ order?: Row }>(v, tradePath(v, `orders/${encodeURIComponent(id)}`), { method: 'PATCH', body: { price } });
  return r.order ? readOrder(r.order) : null;
}

export async function cancelOrder(v: Venue, id: string): Promise<Order | null> {
  const r = await authed<{ order?: Row }>(v, tradePath(v, `orders/${encodeURIComponent(id)}`), { method: 'DELETE' });
  return r.order ? readOrder(r.order) : null;
}

export async function protectPosition(v: Venue, body: { symbol: string; side: PosSide; stop_loss: number | null; take_profit: number | null }): Promise<void> {
  await authed(v, tradePath(v, 'positions/protection'), { body });
}

export async function closePosition(v: Venue, body: { client_request_id: string; symbol: string; side: PosSide; quantity: number | null }): Promise<Order | null> {
  const r = await authed<{ order?: Row }>(v, tradePath(v, 'positions/close'), { body });
  return r.order ? readOrder(r.order) : null;
}

export async function setLeverage(v: Venue, symbol: string, leverage: number): Promise<void> {
  await authed(v, tradePath(v, 'leverage'), { body: { symbol, leverage } });
}

/** Closed trades: TradeYar's portfolio history (last 30 days), CoinePro-FX's trade history. */
export async function getHistory(v: Venue): Promise<Fill[]> {
  if (v === 'tradeyar') {
    const to = Math.floor(Date.now() / 1000);
    const r = await authed<Row>(v, `${MEMBER_ROOT.tradeyar}/portfolio/history?from=${to - 30 * 86400}&to=${to}&page=1&per_page=100`);
    return readFills(r);
  }
  const r = await authed<Row>(v, '/up/coineprofx/api/user/trade-history?page=1&per_page=100');
  return readFills(r);
}

export interface VenueLink { configured: boolean; connected: boolean; permission: string; status: string }

/** TradeYar's LBank key link (venues/lbank); only the server's word that it is live counts. */
export async function getVenueLink(): Promise<VenueLink> {
  const x = await authed<Row>('tradeyar', `${MEMBER_ROOT.tradeyar}/venues/lbank`);
  return { configured: Boolean(x.configured), connected: Boolean(x.connected), permission: String(x.permission ?? ''), status: String(x.status ?? '') };
}

export interface Kyc { level: number; status: string }

export async function getKyc(v: Venue): Promise<Kyc> {
  const x = await authed<Row>(v, `${MEMBER_ROOT[v]}/kyc`);
  return { level: Number(x.level ?? 0) || 0, status: String(x.status ?? 'not_started') };
}
