// The order ticket's arithmetic, kept apart from the screen so it is tested on its own: the price an
// order enters at, stop and target from a price, a distance or a percentage, the size from a share
// of what is available, the cost, margin, fee and reward-to-risk summary, and the checks a venue
// would refuse an order for (live_orders.py on TradeYar) — caught here, before anything is sent.
import { MAX_LEVERAGE, PRICE_BAND, VENUE_TYPES, type Venue } from './venue';
import { faNum } from '../i18n/time';

export type Side = 'buy' | 'sell';
export type TicketType = 'market' | 'limit' | 'stop';
export type LevelMode = 'price' | 'distance' | 'percent';
export type LevelKind = 'sl' | 'tp';

/** Estimated fees per side (LBank USDT-M futures' standard taker and maker). Unknown elsewhere. */
export const FEE_RATE: Record<Venue, { taker: number; maker: number } | null> = {
  tradeyar: { taker: 0.0006, maker: 0.0002 },
  coineprofx: null,
};

const finite = (v: number | null | undefined): v is number => typeof v === 'number' && Number.isFinite(v);

/** The price the order should enter at: the ask for a market buy, the bid for a market sell. */
export function entryPrice(side: Side, type: TicketType, price: number | null, quote: { bid?: number | null; ask?: number | null; last?: number | null }): number | null {
  if (type !== 'market') return finite(price) && price > 0 ? price : null;
  const p = side === 'buy' ? quote.ask ?? quote.last : quote.bid ?? quote.last;
  return finite(p) && p > 0 ? p : null;
}

/** Stop or target as a price, from what the reader typed in the chosen mode. */
export function levelFrom(mode: LevelMode, value: number | null, side: Side, kind: LevelKind, entry: number | null, unit = 1): number | null {
  if (!finite(value) || value <= 0) return null;
  if (mode === 'price') return value;
  if (!finite(entry) || entry <= 0) return null;
  const d = mode === 'distance' ? value * unit : (entry * value) / 100;
  const up = (side === 'buy') === (kind === 'tp');
  const level = up ? entry + d : entry - d;
  return level > 0 ? level : null;
}

/** The inverse: what the field shows in a mode for a level (always a positive amount). */
export function valueFrom(mode: LevelMode, level: number | null, entry: number | null, unit = 1): number | null {
  if (!finite(level) || level <= 0) return null;
  if (mode === 'price') return level;
  if (!finite(entry) || entry <= 0) return null;
  const d = Math.abs(level - entry);
  return mode === 'distance' ? d / unit : (d / entry) * 100;
}

/** Profit (negative: loss) of `qty` from `entry` to `exit` on this side, in the quote currency. */
export function pnlAt(side: Side, entry: number, exit: number, qty: number): number {
  return (exit - entry) * qty * (side === 'buy' ? 1 : -1);
}

/** Reward over risk, or null when either leg is missing or on the wrong side. */
export function rewardRisk(side: Side, entry: number | null, sl: number | null, tp: number | null): number | null {
  if (!finite(entry) || !finite(sl) || !finite(tp)) return null;
  const risk = side === 'buy' ? entry - sl : sl - entry;
  const reward = side === 'buy' ? tp - entry : entry - tp;
  if (risk <= 0 || reward <= 0) return null;
  return reward / risk;
}

/** Decimal places a quantity is typed with, by price: about one-tenth of a dollar per step. */
export function qtyDecimals(price: number | null, venue: Venue | null): number {
  if (venue === 'coineprofx') return 2; // lots
  if (!finite(price) || price <= 0) return 4;
  return Math.min(6, Math.max(0, Math.ceil(Math.log10(price)) - 1));
}

export function roundDown(v: number, decimals: number): number {
  const k = 10 ** decimals;
  return Math.floor(v * k + 1e-9) / k;
}

/** The size that spends `pct` % of what is available at this leverage, fee included. */
export function qtyForPercent(pct: number, available: number | null, leverage: number, price: number | null, feeRate: number, decimals: number): number | null {
  if (!finite(available) || available <= 0 || !finite(price) || price <= 0 || !(pct > 0)) return null;
  const lev = Math.max(1, leverage);
  // Margin plus the fee on the whole value must fit: qty·p/lev + qty·p·fee ≤ available·pct.
  const qty = (available * (pct / 100)) / (price * (1 / lev + feeRate));
  const q = roundDown(qty, decimals);
  return q > 0 ? q : null;
}

/** The isolated-margin liquidation price, roughly (maintenance margin left out): a guide only. */
export function liquidationEstimate(side: Side, entry: number | null, leverage: number): number | null {
  if (!finite(entry) || leverage <= 1) return null;
  return side === 'buy' ? entry * (1 - 1 / leverage) : entry * (1 + 1 / leverage);
}

export interface Summary {
  value: number | null;
  margin: number | null;
  fee: number | null;
  risk: number | null;
  reward: number | null;
  rr: number | null;
  liquidation: number | null;
}

export function summarize(o: { side: Side; type: TicketType; qty: number | null; entry: number | null; leverage: number; sl: number | null; tp: number | null; venue: Venue | null }): Summary {
  const { side, qty, entry, sl, tp } = o;
  const value = finite(qty) && finite(entry) ? qty * entry : null;
  const fees = o.venue ? FEE_RATE[o.venue] : null;
  const rate = fees ? (o.type === 'limit' ? fees.maker : fees.taker) : null;
  return {
    value,
    margin: value != null ? value / Math.max(1, o.leverage) : null,
    fee: value != null && rate != null ? value * rate : null,
    risk: finite(qty) && finite(entry) && finite(sl) ? pnlAt(side, entry, sl, qty) : null,
    reward: finite(qty) && finite(entry) && finite(tp) ? pnlAt(side, entry, tp, qty) : null,
    rr: rewardRisk(side, entry, sl, tp),
    liquidation: liquidationEstimate(side, entry, o.leverage),
  };
}

export interface Issue { field: 'qty' | 'price' | 'sl' | 'tp' | 'leverage' | 'type' | 'margin'; fa: string; en: string; block: boolean }

/** Everything wrong with the order before it is sent; `block` issues keep the button off. */
export function checkOrder(o: {
  venue: Venue | null; live: boolean; side: Side; type: TicketType; qty: number | null; price: number | null;
  entry: number | null; ref: number | null; leverage: number; sl: number | null; tp: number | null; reduceOnly: boolean;
  available: number | null; margin: number | null;
}): Issue[] {
  const out: Issue[] = [];
  const add = (field: Issue['field'], fa: string, en: string, block = true) => out.push({ field, fa, en, block });
  if (o.live && o.venue && !VENUE_TYPES[o.venue].includes(o.type)) {
    add('type', 'این صرافی از برنامه فقط سفارش بازار و محدود می‌گیرد.', 'This exchange takes only market and limit orders from the app.');
  }
  if (!finite(o.qty) || o.qty <= 0) add('qty', 'مقدار را وارد کنید.', 'Enter the quantity.');
  if (o.type !== 'market' && (!finite(o.price) || o.price <= 0)) add('price', o.type === 'limit' ? 'قیمت محدود را وارد کنید.' : 'قیمت استاپ را وارد کنید.', o.type === 'limit' ? 'Enter the limit price.' : 'Enter the stop price.');
  if (o.live && o.venue) {
    const max = MAX_LEVERAGE[o.venue];
    if (!Number.isInteger(o.leverage) || o.leverage < 1 || o.leverage > max) add('leverage', `اهرم باید بین ${faNum(1)} و ${faNum(max)} باشد.`, `Leverage must be between 1 and ${max}.`);
  }
  if (o.reduceOnly && (o.sl != null || o.tp != null)) add('sl', 'سفارش فقط‌کاهشی حد ضرر و سود جدا نمی‌گیرد.', 'A reduce-only order takes no stop loss or take profit.');
  const e = o.entry;
  if (finite(e)) {
    const buy = o.side === 'buy';
    if (finite(o.sl) && (buy ? o.sl >= e : o.sl <= e)) add('sl', buy ? 'حد ضرر خرید باید زیر قیمت ورود باشد.' : 'حد ضرر فروش باید بالای قیمت ورود باشد.', buy ? 'A buy’s stop loss must be below the entry.' : 'A sell’s stop loss must be above the entry.');
    if (finite(o.tp) && (buy ? o.tp <= e : o.tp >= e)) add('tp', buy ? 'حد سود خرید باید بالای قیمت ورود باشد.' : 'حد سود فروش باید زیر قیمت ورود باشد.', buy ? 'A buy’s take profit must be above the entry.' : 'A sell’s take profit must be below the entry.');
  }
  const band = o.live && o.venue ? PRICE_BAND[o.venue] : null;
  if (band && finite(o.ref) && o.ref > 0) {
    const far = (p: number | null) => finite(p) && Math.abs(p - o.ref!) / o.ref! > band;
    if (o.type !== 'market' && far(o.price)) add('price', 'قیمت سفارش بیش از ۲۵٪ با قیمت فعلی فاصله دارد.', 'The order price is more than 25% away from the market.');
    if (far(o.sl) || far(o.tp)) add(far(o.sl) ? 'sl' : 'tp', 'حد ضرر یا سود بیش از ۲۵٪ با قیمت فعلی فاصله دارد.', 'The stop or target is more than 25% away from the market.');
  }
  if (finite(o.margin) && finite(o.available) && o.margin > o.available && !o.reduceOnly) {
    add('margin', 'مارجین این سفارش از موجودی در دسترس بیشتر است.', 'This order needs more margin than is available.', false);
  }
  return out;
}

/** The distance unit of a symbol: a pip for forex (0.0001, 0.01 for yen), one tick elsewhere. */
export function distanceUnit(symbol: string, digits: number): number {
  if (/^[A-Z]{6}$/.test(symbol) && !/^X/.test(symbol)) return symbol.includes('JPY') ? 0.01 : 0.0001;
  return 10 ** -digits;
}
