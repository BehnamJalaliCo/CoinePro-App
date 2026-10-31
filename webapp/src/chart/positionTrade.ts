// «باز کردن به‌صورت معامله‌ی آزمایشی» from a drawn long/short position (OLD feature/chart
// SetupSheet.kt): the same entry, both lines the reader drew and the size the position's own risk
// worked out, offered to the order ticket — so live/paper routing and its rules hold — and only an
// order nobody took goes straight to the paper book.
import { offerOrderTicket } from './actions';
import { usePaper } from '../state/paper';
import { usePanels } from '../state/panels';
import { useMarket } from '../state/market';
import type { PositionStats } from '../engine/drawings.js';

export interface PositionOrder { side: 'buy' | 'sell'; price: number; quantity: number; stopLoss: number; takeProfit: number }

/** The order a drawn position stands for, or null where its stop and target are on the wrong sides or there is no size. */
export function positionOrder(st: PositionStats): PositionOrder | null {
  const { entry, stop, target, long } = st;
  if (![entry, stop, target].every((v) => Number.isFinite(v) && v > 0)) return null;
  const valid = long ? stop < entry && target > entry : stop > entry && target < entry;
  const quantity = st.qty * st.lot;
  if (!valid || !(quantity > 0) || !Number.isFinite(quantity)) return null;
  return { side: long ? 'buy' : 'sell', price: entry, quantity: Number(quantity.toPrecision(6)), stopLoss: stop, takeProfit: target };
}

export function openPositionAsPaper(symbol: string, o: PositionOrder): void {
  const last = useMarket.getState().tickers[symbol]?.last ?? o.price;
  if (offerOrderTicket({ symbol, side: o.side, price: o.price, source: 'position', quantity: o.quantity, stopLoss: o.stopLoss, takeProfit: o.takeProfit, ...(o.price !== last ? { type: 'limit' as const } : {}) })) return;
  const side = o.side === 'buy' ? 'long' : 'short';
  const paper = usePaper.getState();
  if (o.price === last) paper.open(symbol, side, o.quantity, last, o.stopLoss, o.takeProfit);
  else {
    const type = (side === 'long') === (o.price < last) ? 'limit' : 'stop';
    paper.place({ symbol, side, type, qty: o.quantity, ...(type === 'limit' ? { limitPrice: o.price } : { stopPrice: o.price }), sl: o.stopLoss, tp: o.takeProfit }, last);
  }
  if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
}
