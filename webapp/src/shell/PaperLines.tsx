import { useEffect } from 'react';
import { usePaper, pnlOf } from '../state/paper';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { useReplay } from '../state/replay';
import { useTradeLines, type TradeLine } from '../chart/tradeLines';

// The paper account on the chart, as TradingView draws a broker's: each position of the open symbol
// (its size, the open P&L and ×), its stop loss and take profit (dragged to move, × to remove) and the
// working orders (dragged to a new price, × to cancel). Hidden while replaying: the replay's past
// prices and the account's live ones do not mix.
const money = (v: number) => `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}`;

export function PaperLines() {
  const symbol = usePrefs((s) => s.symbol);
  const fa = usePrefs((s) => s.lang) === 'fa';
  const positions = usePaper((s) => s.positions);
  const orders = usePaper((s) => s.orders);
  const last = useMarket((s) => s.tickers[symbol]?.last);
  const replaying = useReplay((r) => Boolean(r.state?.on));
  useEffect(() => {
    const set = useTradeLines.getState().set;
    if (replaying) { set('paper', []); return; }
    const book = usePaper.getState();
    const L = (a: string, b: string) => (fa ? a : b);
    const out: TradeLine[] = [];
    for (const p of positions) {
      if (p.symbol !== symbol) continue;
      const long = p.side === 'long';
      const pnl = last ? pnlOf(p, last) : 0;
      const at = (x: number) => pnlOf(p, x);
      out.push({ id: `p:${p.id}`, price: p.entry, color: '#2962FF', label: `${long ? L('خرید', 'Long') : L('فروش', 'Short')} ${p.qty}`, value: last ? money(pnl) : undefined, valueColor: pnl >= 0 ? 'var(--up)' : 'var(--down)', onClose: last ? () => usePaper.getState().close(p.id, last) : undefined, closeTitle: L('بستن موقعیت', 'Close position') });
      if (p.sl != null) out.push({ id: `p:${p.id}:sl`, price: p.sl, color: '#F23645', label: 'SL', value: money(at(p.sl)), valueColor: 'var(--down)', onMove: (x) => usePaper.getState().protect(p.id, x, usePaper.getState().positions.find((q) => q.id === p.id)?.tp), onClose: () => book.protect(p.id, undefined, p.tp), closeTitle: L('حذف حد ضرر', 'Remove stop loss') });
      if (p.tp != null) out.push({ id: `p:${p.id}:tp`, price: p.tp, color: '#089981', label: 'TP', value: money(at(p.tp)), valueColor: 'var(--up)', onMove: (x) => usePaper.getState().protect(p.id, usePaper.getState().positions.find((q) => q.id === p.id)?.sl, x), onClose: () => book.protect(p.id, p.sl, undefined), closeTitle: L('حذف حد سود', 'Remove take profit') });
    }
    for (const o of orders) {
      if (o.symbol !== symbol || o.type === 'market') continue;
      const buy = o.side === 'long';
      const color = buy ? '#2962FF' : '#F23645';
      const kind = { limit: L('لیمیت', 'limit'), stop: L('استاپ', 'stop'), stop_limit: L('استاپ‌لیمیت', 'stop limit'), trailing: L('متحرک', 'trailing'), market: '' }[o.type];
      const name = `${buy ? L('خرید', 'Buy') : L('فروش', 'Sell')} ${kind} ${o.qty}`;
      const cancel = () => usePaper.getState().cancel(o.id);
      if (o.stopPrice != null && !(o.type === 'stop_limit' && o.triggered)) out.push({ id: `o:${o.id}:stop`, price: o.stopPrice, color, dashed: true, label: name, onMove: (x) => usePaper.getState().modifyOrder(o.id, { stopPrice: x }), onClose: cancel, closeTitle: L('لغو سفارش', 'Cancel order') });
      if (o.limitPrice != null) out.push({ id: `o:${o.id}:limit`, price: o.limitPrice, color, dashed: true, label: o.type === 'stop_limit' ? `${name} · ${L('لیمیت', 'limit')}` : name, onMove: (x) => usePaper.getState().modifyOrder(o.id, { limitPrice: x }), onClose: cancel, closeTitle: L('لغو سفارش', 'Cancel order') });
      if (o.sl != null) out.push({ id: `o:${o.id}:sl`, price: o.sl, color: '#F23645', dashed: true, label: 'SL', onMove: (x) => usePaper.getState().modifyOrder(o.id, { sl: x }) });
      if (o.tp != null) out.push({ id: `o:${o.id}:tp`, price: o.tp, color: '#089981', dashed: true, label: 'TP', onMove: (x) => usePaper.getState().modifyOrder(o.id, { tp: x }) });
    }
    set('paper', out);
  }, [symbol, positions, orders, last, replaying, fa]);
  useEffect(() => () => useTradeLines.getState().set('paper', []), []);
  return null;
}
