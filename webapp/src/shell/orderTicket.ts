// Every order this area's tickets send goes through here (the order panel, the DOM ladder, the
// quick buy / sell under the alerts). It first offers the order to live trading — the area that
// trades on TradeYar and CoinePro-FX listens for `pc:order-ticket` and calls preventDefault() when
// it takes the order — and only an order nobody took goes to the paper account.
import { usePaper, type OrderType, type Side } from '../state/paper';

export interface TicketDetail {
  symbol: string;
  side: 'buy' | 'sell';
  price?: number;
  type?: 'market' | 'limit' | 'stop';
  quantity?: number;
  stopLoss?: number;
  takeProfit?: number;
  source: 'panel';
}

/** Offers the order to live trading; true when it was taken there. */
export function offerLive(detail: TicketDetail): boolean {
  const ev = new CustomEvent('pc:order-ticket', { cancelable: true, detail });
  window.dispatchEvent(ev);
  return ev.defaultPrevented;
}

export interface PaperOrderInput {
  symbol: string; side: Side; type: OrderType; qty: number; last: number;
  limitPrice?: number; stopPrice?: number; trailPercent?: number; sl?: number; tp?: number;
}

/** The kinds live trading understands; stop-limit and trailing stay on paper. */
const LIVE_TYPE: Partial<Record<OrderType, TicketDetail['type']>> = { market: 'market', limit: 'limit', stop: 'stop' };

/** Sends an order: live when the live area takes it, else to the paper book. */
export function submitOrder(o: PaperOrderInput): { live: true } | { live: false; id: string | null; error: string | null } {
  const type = LIVE_TYPE[o.type];
  if (type) {
    const price = o.type === 'limit' ? o.limitPrice : o.type === 'stop' ? o.stopPrice : o.last;
    if (offerLive({ symbol: o.symbol, side: o.side === 'long' ? 'buy' : 'sell', type, price, quantity: o.qty, stopLoss: o.sl, takeProfit: o.tp, source: 'panel' })) return { live: true };
  }
  const paper = usePaper.getState();
  const id = o.type === 'market'
    ? paper.open(o.symbol, o.side, o.qty, o.last, o.sl, o.tp)
    : paper.place({ symbol: o.symbol, side: o.side, type: o.type, qty: o.qty, limitPrice: o.limitPrice, stopPrice: o.stopPrice, trailPercent: o.trailPercent, sl: o.sl, tp: o.tp }, o.last);
  return { live: false, id, error: id ? null : usePaper.getState().lastError };
}
