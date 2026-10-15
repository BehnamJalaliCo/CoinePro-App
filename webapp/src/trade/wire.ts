// The trade routes' answers in the app's shape (snake_case on the wire, as both platforms send
// it). Readers never invent a figure: a field the platform did not send stays null and the screen
// shows a dash.
import { HttpError } from '../data/http';

export type PosSide = 'long' | 'short';
export type OrderSide = 'buy' | 'sell';

export interface Account {
  currency: string;
  available: number | null;
  equity: number | null;
  marginUsed: number | null;
  unrealizedPnl: number | null;
  tradingEnabled: boolean;
}

export interface Position {
  symbol: string;
  side: PosSide;
  quantity: number;
  entry: number | null;
  mark: number | null;
  upnl: number | null;
  leverage: number | null;
  liquidation: number | null;
  /** Only when the platform reports them on the position itself. */
  stopLoss: number | null;
  takeProfit: number | null;
}

export interface Order {
  id: string;
  symbol: string;
  side: OrderSide;
  type: string;
  quantity: number | null;
  price: number | null;
  stopLoss: number | null;
  takeProfit: number | null;
  status: string;
  filled: number | null;
  avgPrice: number | null;
  reduceOnly: boolean;
  protection: string;
  error: string | null;
  canCancel: boolean;
  canAmend: boolean;
  createdAt: number | null;
}

export interface Fill {
  id: string;
  symbol: string;
  side: OrderSide;
  quantity: number | null;
  entry: number | null;
  exit: number | null;
  pnl: number | null;
  fee: number | null;
  openedAt: number | null;
  closedAt: number | null;
  currency: string;
}

const num = (v: unknown): number | null => {
  if (v == null || v === '' || typeof v === 'boolean') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};
const str = (v: unknown): string => (v == null ? '' : String(v));
const ms = (v: unknown): number | null => {
  if (v == null || v === '') return null;
  const t = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v));
  return Number.isFinite(t) ? t : null;
};
type Row = Record<string, unknown>;

export function readAccount(x: Row): Account {
  return {
    currency: str(x.currency) || 'USDT',
    available: num(x.available),
    equity: num(x.equity),
    marginUsed: num(x.margin_used),
    unrealizedPnl: num(x.unrealized_pnl),
    tradingEnabled: x.trading_enabled !== false,
  };
}

export function readPosition(x: Row): Position | null {
  const symbol = str(x.symbol).toUpperCase();
  const q = num(x.quantity ?? x.volume);
  const rawSide = str(x.side ?? x.direction).toLowerCase();
  const side: PosSide | null = rawSide === 'long' || rawSide === 'buy' ? 'long' : rawSide === 'short' || rawSide === 'sell' ? 'short' : null;
  if (!symbol || !q || !side) return null;
  return {
    symbol, side, quantity: Math.abs(q),
    entry: num(x.entry_price ?? x.entry),
    mark: num(x.mark_price ?? x.current_price),
    upnl: num(x.unrealized_pnl ?? x.profit),
    leverage: num(x.leverage),
    liquidation: num(x.liquidation_price),
    stopLoss: num(x.stop_loss ?? x.sl),
    takeProfit: num(x.take_profit ?? x.tp),
  };
}

export function readOrder(x: Row): Order | null {
  const symbol = str(x.symbol).toUpperCase();
  const side = str(x.side).toLowerCase();
  if (!symbol || (side !== 'buy' && side !== 'sell') || x.id == null) return null;
  const status = str(x.status).toLowerCase();
  return {
    id: str(x.id), symbol, side,
    type: str(x.type).toLowerCase() || 'market',
    quantity: num(x.quantity),
    price: num(x.price),
    stopLoss: num(x.stop_loss),
    takeProfit: num(x.take_profit),
    status,
    filled: num(x.filled_quantity),
    avgPrice: num(x.avg_price),
    reduceOnly: Boolean(x.reduce_only),
    protection: str(x.protection) || 'none',
    error: x.error_message ? str(x.error_message) : null,
    canCancel: x.can_cancel === undefined ? status === 'open' || status === 'partial' : Boolean(x.can_cancel),
    canAmend: x.can_amend === undefined ? status === 'open' && str(x.type) === 'limit' : Boolean(x.can_amend),
    createdAt: ms(x.created_at),
  };
}

/** A working order: one the exchange holds and may still fill. */
export const isWorking = (o: Order) => o.status === 'open' || o.status === 'partial' || o.status === 'submitted' || o.status === 'queued';

/** TradeYar portfolio/history {trades[]} or CoinePro-FX trade-history {items[]}. */
export function readFills(body: Row): Fill[] {
  const ty = Array.isArray(body.trades) ? (body.trades as Row[]) : null;
  if (ty) {
    return ty.map((t) => ({
      id: str(t.id), symbol: str(t.symbol).toUpperCase(), side: str(t.side).toLowerCase() === 'sell' ? 'sell' : 'buy',
      quantity: num(t.quantity), entry: num(t.entry), exit: num(t.exit), pnl: num(t.pnl), fee: num(t.fee),
      openedAt: ms(t.opened_at), closedAt: ms(t.closed_at), currency: str(t.currency) || 'USDT',
    }));
  }
  const fx = Array.isArray(body.items) ? (body.items as Row[]) : [];
  return fx.map((t) => {
    const fee = (num(t.commission) ?? 0) + (num(t.swap) ?? 0);
    return {
      id: str(t.id), symbol: str(t.symbol).toUpperCase(), side: str(t.direction).toLowerCase().startsWith('s') ? 'sell' : 'buy',
      quantity: num(t.volume), entry: num(t.entry_price), exit: num(t.exit_price), pnl: num(t.net_profit ?? t.gross_profit),
      fee: t.commission == null && t.swap == null ? null : fee,
      openedAt: ms(t.open_time), closedAt: ms(t.close_time), currency: 'USD',
    };
  });
}

/**
 * The platform's own sentence for a refusal, verbatim (`detail`, or `detail.message`). Both
 * platforms write it in Persian for the reader; an exchange's own words pass through as they came.
 */
export function backendText(e: unknown): string | null {
  if (!(e instanceof HttpError)) return null;
  const b = e.body as { detail?: unknown; message?: unknown } | string | null;
  if (typeof b === 'string') return null;
  const d = b?.detail;
  const text = typeof d === 'string' ? d : (d as { message?: unknown } | undefined)?.message ?? b?.message;
  if (typeof text !== 'string' || !text.trim() || text.length > 400) return null;
  return text.trim();
}

/** A readable reason for a failed trade call, in the reader's language when the platform gave none. */
export function tradeErrorText(e: unknown, L: (fa: string, en: string) => string): string {
  const own = backendText(e);
  if (own) return own;
  if (e instanceof HttpError) {
    if (e.status === -1) return L('ارتباط با سرور برقرار نشد. اتصال اینترنت را بررسی کنید.', 'Could not reach the server. Check the connection.');
    if (e.status === 401) return L('نشست شما تمام شده است؛ دوباره وارد شوید.', 'Your session has ended; sign in again.');
    if (e.status === 404 || e.status === 405) return L('این امکان روی این سرور هنوز فعال نیست.', 'This is not available on this server yet.');
    if (e.status === 429) return L('درخواست‌ها زیاد شد؛ کمی بعد دوباره امتحان کنید.', 'Too many requests; try again shortly.');
    if (e.status >= 500) return L('صرافی پاسخ نداد. وضعیت سفارش را در فهرست سفارش‌ها بررسی کنید.', 'The exchange did not answer. Check the order list for its status.');
  }
  return L('درخواست انجام نشد.', 'The request failed.');
}
