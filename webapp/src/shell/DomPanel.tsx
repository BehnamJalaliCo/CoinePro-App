import { useEffect, useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { usePaper } from '../state/paper';
import { orderBook, type Book } from '../data/quote';
import { backendOf, instrumentOf } from '../data/symbols';
import { pageVisible } from '../data/http';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash } from '../ui/useFlash';
import { useL } from '../pages/kit';
import { submitOrder } from './orderTicket';
import './panels-x.css';

// TradingView's DOM: the order book as one price ladder — asks above, bids below, the last price
// between — with each level's size and a depth bar. A click on a bid places a paper buy limit at
// that price, on an ask a paper sell limit; the reader's paper orders are marked on their levels.
// Crypto only (TradeYar's public book, every 2 s); forex has no depth feed.
export function DomPanel() {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const orders = usePaper((s) => s.orders);
  const cancel = usePaper((s) => s.cancel);
  const lastError = usePaper((s) => s.lastError);
  const [book, setBook] = useState<Book | null>(null);
  const [failed, setFailed] = useState(false);
  const [depth, setDepth] = useState(15);
  const [qty, setQty] = useState('0.01');
  const [note, setNote] = useState<string | null>(null);
  const digits = instrumentOf(symbol, tk?.last).digits;
  const crypto = backendOf(symbol) === 'tradeyar';
  const { ref, tick } = useFlash<HTMLSpanElement>(tk?.last);

  useEffect(() => {
    if (!crypto) return;
    let alive = true;
    setBook(null); setFailed(false);
    const load = () => { if (pageVisible()) void orderBook(symbol, depth).then((b) => { if (alive) setBook(b); }).catch(() => { if (alive) setFailed(true); }); };
    load();
    const id = setInterval(load, 2000);
    return () => { alive = false; clearInterval(id); };
  }, [symbol, depth, crypto]);

  const ladder = useMemo(() => {
    if (!book) return null;
    const cum = (side: [number, number][]) => { let c = 0; return side.map(([p, v]) => { c += v; return { p, v, c }; }); };
    const asks = cum(book.asks.slice(0, depth));
    const bids = cum(book.bids.slice(0, depth));
    const max = Math.max(asks[asks.length - 1]?.c ?? 0, bids[bids.length - 1]?.c ?? 0) || 1;
    return { asks: [...asks].reverse(), bids, max };
  }, [book, depth]);

  const q = Number(qty);
  const limit = (side: 'long' | 'short', price: number) => {
    if (!(q > 0)) { setNote(L('مقدار را وارد کنید.', 'Enter a quantity.')); return; }
    const r = submitOrder({ symbol, side, type: 'limit', qty: q, limitPrice: price, last: tk?.last ?? price });
    if (r.live) { setNote(L('به معامله‌ی زنده سپرده شد.', 'Handed to live trading.')); return; }
    setNote(r.id ? L(`سفارش محدود ${side === 'long' ? 'خرید' : 'فروش'} در ${formatPrice(price, digits)} ثبت شد (آزمایشی).`, `Paper ${side === 'long' ? 'buy' : 'sell'} limit at ${formatPrice(price, digits)} placed.`) : L('ثبت نشد (موجودی یا قیمت).', 'Not placed (margin or price).'));
  };
  const market = (side: 'long' | 'short') => {
    if (!tk || !(q > 0)) return;
    const r = submitOrder({ symbol, side, type: 'market', qty: q, last: tk.last });
    setNote(r.live ? L('به معامله‌ی زنده سپرده شد.', 'Handed to live trading.') : r.id ? L('پر شد (آزمایشی).', 'Filled (paper).') : L('ثبت نشد (موجودی).', 'Not placed (margin).'));
  };
  const mine = (price: number, side: 'long' | 'short') => orders.filter((o) => o.symbol === symbol && o.side === side && o.type === 'limit' && Math.abs((o.limitPrice ?? 0) - price) < 10 ** -digits / 2);
  const spread = book?.asks[0] && book?.bids[0] ? book.asks[0][0] - book.bids[0][0] : null;

  return (
    <section className="panel-generic dom-panel">
      <header className="panel-head">
        <SymbolLogo symbol={symbol} size={18} /><span className="sym dom-sym">{symbol}</span>
        <span className="ws-spacer" />
        {crypto ? <select className="num" value={depth} onChange={(e) => setDepth(Number(e.target.value))} aria-label={L('عمق', 'Depth')}>{[10, 15, 20, 30].map((x) => <option key={x} value={x}>{x}</option>)}</select> : null}
      </header>
      {!crypto ? (
        <div className="empty fa">{L('دفتر سفارش فقط برای رمزارزها در دسترس است؛ برای فارکس داده‌ی عمق بازار نیست.', 'The order book is available for crypto only; there is no depth feed for forex.')}</div>
      ) : (
        <>
          <div className="dom-trade">
            <button className="btn sell sm" disabled={!tk || !(q > 0)} onClick={() => tk && market('short')}><span className="fa">{L('فروش بازار', 'Sell MKT')}</span></button>
            <input className="num" value={qty} onChange={(e) => setQty(e.target.value)} inputMode="decimal" aria-label={L('مقدار', 'Quantity')} />
            <button className="btn buy sm" disabled={!tk || !(q > 0)} onClick={() => tk && market('long')}><span className="fa">{L('خرید بازار', 'Buy MKT')}</span></button>
          </div>
          <div className="dom-head"><span className="fa">{L('خرید', 'Bid')}</span><span className="fa">{L('قیمت', 'Price')}</span><span className="fa">{L('فروش', 'Ask')}</span></div>
          <div className="panel-body dom-ladder">
            {!ladder ? <div className="empty fa">{failed ? L('دفتر سفارش در دسترس نیست.', 'The order book is unavailable.') : L('در حال بارگیری…', 'Loading…')}</div> : (
              <>
                {ladder.asks.map((r) => (
                  <div key={`a${r.p}`} className="dom-lvl ask">
                    <span className="dom-cell" />
                    <span className="dom-price num">{formatPrice(r.p, digits)}{mine(r.p, 'short').length ? <i className="dom-mine" title={L('سفارش شما', 'Your order')} /> : null}</span>
                    <button className="dom-cell size num" title={L('فروش محدود در این قیمت', 'Sell limit at this price')} onClick={() => limit('short', r.p)}><i style={{ width: `${(r.c / ladder.max) * 100}%` }} /><span>{+r.v.toPrecision(5)}</span></button>
                  </div>
                ))}
                <div className="dom-mid">
                  <span ref={ref} className={`num ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tk ? formatPrice(tk.last, digits) : '—'}</span>
                  {spread != null ? <span className="muted num">{L('اسپرد', 'Spread')} {formatPrice(spread, digits)}</span> : null}
                </div>
                {ladder.bids.map((r) => (
                  <div key={`b${r.p}`} className="dom-lvl bid">
                    <button className="dom-cell size num" title={L('خرید محدود در این قیمت', 'Buy limit at this price')} onClick={() => limit('long', r.p)}><i style={{ width: `${(r.c / ladder.max) * 100}%` }} /><span>{+r.v.toPrecision(5)}</span></button>
                    <span className="dom-price num">{formatPrice(r.p, digits)}{mine(r.p, 'long').length ? <i className="dom-mine" title={L('سفارش شما', 'Your order')} /> : null}</span>
                    <span className="dom-cell" />
                  </div>
                ))}
              </>
            )}
          </div>
          {note ? <div className={`dom-note fa${lastError ? ' down' : ''}`}>{note}</div> : null}
          {orders.some((o) => o.symbol === symbol) ? (
            <div className="dom-orders">
              {orders.filter((o) => o.symbol === symbol).map((o) => (
                <div key={o.id} className="dom-order num">
                  <span className={o.side === 'long' ? 'up' : 'down'}>{o.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</span>
                  <span>{o.qty}</span><span className="muted">@</span><span>{formatPrice(o.limitPrice ?? o.stopPrice ?? 0, digits)}</span><span className="muted">{o.type}</span>
                  <span className="ws-spacer" />
                  <button className="lt" title={L('لغو', 'Cancel')} onClick={() => cancel(o.id)}><Icon name="ph-x" size="sm" /></button>
                </div>
              ))}
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
