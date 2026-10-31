import { useEffect, useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { usePaper } from '../state/paper';
import { request } from '../data/http';
import { backendOf, instrumentOf } from '../data/symbols';
import { pageVisible } from '../data/http';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash } from '../ui/useFlash';
import { useL } from '../pages/kit';
import { submitOrder } from './orderTicket';
import './panels-x.css';
import { aggregated, aggregationDecimals, aggregationSteps, bookOf, depthCurve, imbalance, ladderBar, ladderFigure, ladderRows, quantityDecimalsFor, STACKED_ORDERS_THRESHOLD, type Book } from './depthLadder';
import { AlertAtPrice, BidShare, DepthControls, DepthCurveView, DepthNotes, useLadderPref } from './DepthExtras';

// TradingView's DOM: the order book as one price ladder — asks above, bids below, the last price
// between — with each level's size and a depth bar. A click on a bid places a paper buy limit at
// that price, on an ask a paper sell limit; the reader's paper orders are marked on their levels.
// Crypto only (TradeYar's public book, every 2 s); forex has no depth feed. The OLD ladder's tools
// (DepthOfMarketScreen.kt): price aggregation, size or cumulative figures, the bid share near the
// touch, the depth curve, and a bell on every level for «هشدار در این قیمت».
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
    // Fetched wide (100 a side): aggregation and the bid share read past the rows on screen.
    const load = () => { if (pageVisible()) void request<{ bids?: unknown[][]; asks?: unknown[][] }>(`/up/tradeyar/api/v1/public/market/depth?symbol=${encodeURIComponent(symbol)}&depth=100`).then((r) => { if (alive) setBook(bookOf(r)); }).catch(() => { if (alive) setFailed(true); }); };
    load();
    const id = setInterval(load, 2000);
    return () => { alive = false; clearInterval(id); };
  }, [symbol, crypto]);

  const [step, figure, choose] = useLadderPref(symbol);
  const steps = useMemo(() => (book ? aggregationSteps(book, step) : []), [book, step]);
  const shown = useMemo(() => (book ? aggregated(book, step) : null), [book, step]);
  const ladder = useMemo(() => (shown ? ladderRows(shown, depth) : null), [shown, depth]);
  const curve = useMemo(() => (shown ? depthCurve(shown) : null), [shown]);
  const sizeDigits = ladder ? quantityDecimalsFor(Math.max(0, ...[...ladder.asks, ...ladder.bids].map((r) => ladderFigure(r, figure)))) : 0;
  // A grouped ladder's prices carry the step's decimals (OLD aggregationDecimals).
  const priceDigits = step != null ? aggregationDecimals(step) : digits;
  const fmtP = (v: number) => formatPrice(v, priceDigits);

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
  const spread = book?.asks[0] && book?.bids[0] ? book.asks[0].price - book.bids[0].price : null;
  const sizeCell = (r: NonNullable<typeof ladder>['bids'][number]) => (
    <>
      {figure === 'amount' ? <i className="dom-curve-fill" style={{ width: `${r.curveFraction * 100}%` }} /> : null}
      <i style={{ width: `${ladderBar(r, figure) * 100}%` }} />
      {r.orders != null && r.orders >= STACKED_ORDERS_THRESHOLD ? <b className="dom-orders-mark num">{r.orders}</b> : null}
      <span>{ladderFigure(r, figure).toFixed(sizeDigits)}</span>
    </>
  );

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
          {book ? <BidShare share={imbalance(book)} /> : null}
          {book ? <DepthControls steps={steps} step={step} figure={figure} onStep={(x) => choose(x, figure)} onFigure={(f) => choose(step, f)} /> : null}
          <div className="dom-head"><span className="fa">{figure === 'amount' ? L('حجم خرید', 'Bid size') : L('خرید تجمعی', 'Bid total')}</span><span className="fa">{L('قیمت', 'Price')}</span><span className="fa">{figure === 'amount' ? L('حجم فروش', 'Ask size') : L('فروش تجمعی', 'Ask total')}</span></div>
          <div className="panel-body dom-ladder">
            {!ladder ? <div className="empty fa">{failed ? L('دفتر سفارش در دسترس نیست.', 'The order book is unavailable.') : L('در حال بارگیری…', 'Loading…')}</div> : (
              <>
                {ladder.asks.map((r) => (
                  <div key={`a${r.price}`} className="dom-lvl ask">
                    <span className="dom-cell" />
                    <span className="dom-price num"><AlertAtPrice symbol={symbol} price={r.price} label={fmtP(r.price)} />{fmtP(r.price)}{mine(r.price, 'short').length ? <i className="dom-mine" title={L('سفارش شما', 'Your order')} /> : null}</span>
                    <button className="dom-cell size num" title={L('فروش محدود در این قیمت', 'Sell limit at this price')} onClick={() => limit('short', r.price)}>{sizeCell(r)}</button>
                  </div>
                ))}
                <div className="dom-mid">
                  <span ref={ref} className={`num ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tk ? formatPrice(tk.last, digits) : '—'}</span>
                  {spread != null ? <span className="muted num">{L('اسپرد', 'Spread')} {formatPrice(spread, digits)}</span> : null}
                </div>
                {ladder.bids.map((r) => (
                  <div key={`b${r.price}`} className="dom-lvl bid">
                    <button className="dom-cell size num" title={L('خرید محدود در این قیمت', 'Buy limit at this price')} onClick={() => limit('long', r.price)}>{sizeCell(r)}</button>
                    <span className="dom-price num"><AlertAtPrice symbol={symbol} price={r.price} label={fmtP(r.price)} />{fmtP(r.price)}{mine(r.price, 'long').length ? <i className="dom-mine" title={L('سفارش شما', 'Your order')} /> : null}</span>
                    <span className="dom-cell" />
                  </div>
                ))}
              </>
            )}
          </div>
          {curve ? <DepthCurveView curve={curve} fmt={fmtP} /> : null}
          {book ? <DepthNotes showStepNote={steps.length > 0} showOrdersNote={[...(ladder?.asks ?? []), ...(ladder?.bids ?? [])].some((r) => r.orders != null)} /> : null}
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
