// «معامله» under the chart, TradingView's Account Manager for the reader's own exchange account:
// equity, available, margin and open profit across the top; then positions (close, stop and target),
// working orders (move, cancel) and the closed-trade history. Live trading is TradeYar's (LBank
// futures) only: forex on Pro Chart is signals only.
import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePanels, type BottomTab } from '../state/panels';
import { useMarket, digitsOf } from '../state/market';
import { formatPrice } from '../chart/format';
import { uuid } from '../data/http';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { dateTime, parseNum, useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { amendOrder, cancelOrder, closePosition, getHistory, protectPosition } from './api';
import { protectionOf, useTrading, workingOrders } from './store';
import { LiveNeeds } from './LiveNeeds';
import { ORDER_STATUS } from './OrderTicket';
import { pnlAt } from './math';
import { tradeErrorText, type Fill, type Order, type Position } from './wire';
import { notify } from './toast';
import { LIVE_VENUES, VENUE_LABEL, type Venue } from './venue';
import './trade.css';

export const ACCOUNT_TAB = 'account' as BottomTab;

/** The bottom bar's tab button (its label is ours, not the shared string table's). */
export function AccountTabButton() {
  const L = useL();
  const bottom = usePanels((s) => s.bottom);
  const setBottom = usePanels((s) => s.setBottom);
  const live = useTrading((s) => LIVE_VENUES.some((v) => s.books[v].status === 'live'));
  return (
    <button role="tab" aria-selected={bottom === ACCOUNT_TAB} className={`tab${bottom === ACCOUNT_TAB ? ' on' : ''}`} onClick={() => setBottom(ACCOUNT_TAB)}>
      <span className="fa">{L('معامله', 'Trading')}</span>{live ? <i className="ot-dot on" aria-hidden /> : null}
    </button>
  );
}

const money = (v: number | null | undefined, signed = false) => (v == null || !Number.isFinite(v) ? '—' : `${signed && v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);

export function AccountPanel({ onSignIn }: { onSignIn(): void }) {
  const L = useL();
  const venue: Venue = LIVE_VENUES[0]!;
  const book = useTrading((s) => s.books[venue]);
  const refresh = useTrading((s) => s.refresh);
  const [tab, setTab] = useState<'positions' | 'orders' | 'history'>('positions');
  useEffect(() => { void refresh(venue, 'all'); }, [venue, refresh]);
  const a = book.account;
  const working = workingOrders(book);
  const n = (x: number) => (usePrefs.getState().lang === 'fa' ? faNum(x) : String(x));

  return (
    <div className="acct" dir={usePrefs.getState().lang === 'fa' ? 'rtl' : 'ltr'}>
      <div className="acct-strip">
        <b className="acct-venue fa">{L(VENUE_LABEL[venue].fa, VENUE_LABEL[venue].en)} <span className="muted num">· {VENUE_LABEL[venue].broker}</span></b>
        <span className={`acct-state ${book.status}`}><i className={`ot-dot${book.status === 'live' ? ' on' : ''}`} /><span className="fa">{book.status === 'live' ? L('متصل', 'Connected') : book.status === 'checking' ? L('در حال بررسی', 'Checking') : L('معامله‌ی زنده فعال نیست', 'Live trading is off')}</span></span>
        {([[L('ارزش حساب', 'Equity'), a?.equity], [L('در دسترس', 'Available'), a?.available], [L('مارجین', 'Margin'), a?.marginUsed]] as [string, number | null | undefined][]).map(([k, v]) => (
          <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className="bt-v num" dir="ltr">{money(v)}</span></div>
        ))}
        <div className="bt-stat"><span className="bt-k fa">{L('سود و زیان باز', 'Unrealized P&L')}</span><span className={`bt-v num ${(a?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'}`} dir="ltr">{money(a?.unrealizedPnl, true)}</span></div>
        <span className="ws-spacer" />
        <button className="lt" title={L('تازه‌سازی', 'Refresh')} onClick={() => void refresh(venue, 'all')}><Icon name="ph-arrow-clockwise" size="sm" /></button>
      </div>
      {book.status !== 'live' && book.status !== 'checking' ? (
        <div className="acct-needs"><LiveNeeds venue={venue} onSignIn={onSignIn} compact /></div>
      ) : (
        <>
          <nav className="acct-tabs" role="tablist">
            <button role="tab" aria-selected={tab === 'positions'} className={tab === 'positions' ? 'on' : ''} onClick={() => setTab('positions')}><span className="fa">{L('موقعیت‌ها', 'Positions')}</span> <b className="num">{n(book.positions.length)}</b></button>
            <button role="tab" aria-selected={tab === 'orders'} className={tab === 'orders' ? 'on' : ''} onClick={() => setTab('orders')}><span className="fa">{L('سفارش‌ها', 'Orders')}</span> <b className="num">{n(working.length)}</b></button>
            <button role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}><span className="fa">{L('تاریخچه', 'History')}</span></button>
          </nav>
          <div className="acct-body">
            {tab === 'positions' ? <Positions venue={venue} /> : tab === 'orders' ? <Orders venue={venue} orders={book.orders} /> : <History venue={venue} />}
          </div>
        </>
      )}
    </div>
  );
}

function Positions({ venue }: { venue: Venue }) {
  const L = useL();
  const book = useTrading((s) => s.books[venue]);
  const memory = useTrading((s) => s.protections);
  const tickers = useMarket((s) => s.tickers);
  if (!book.positions.length) return <div className="empty fa">{L('موقعیت بازی ندارید.', 'No open positions.')}</div>;
  return (
    <table className="table acct-table">
      <thead><tr>
        <th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('آخرین', 'Last')}</th>
        <th className="r">P&amp;L</th><th className="r">{L('لیکوئید', 'Liq.')}</th><th className="r">SL</th><th className="r">TP</th><th />
      </tr></thead>
      <tbody>{book.positions.map((p) => <PositionRow key={p.symbol + p.side} venue={venue} p={p} last={tickers[p.symbol]?.last ?? p.mark} prot={protectionOf(venue, p, book.orders, memory)} />)}</tbody>
    </table>
  );
}

function PositionRow({ venue, p, last, prot }: { venue: Venue; p: Position; last: number | null; prot: { sl: number | null; tp: number | null } }) {
  const L = useL();
  const d = digitsOf(p.symbol);
  const [edit, setEdit] = useState(false);
  const [sl, setSl] = useState(prot.sl != null ? String(prot.sl) : '');
  const [tp, setTp] = useState(prot.tp != null ? String(prot.tp) : '');
  const [ask, setAsk] = useState(false);
  const [busy, setBusy] = useState(false);
  const side = p.side === 'long' ? 'buy' : 'sell';
  const pnl = last != null && p.entry != null ? pnlAt(side, p.entry, last, p.quantity) : p.upnl;
  const pct = pnl != null && p.entry ? (pnl / (p.entry * p.quantity)) * 100 * (p.leverage ?? 1) : null;
  const f = (v: number | null | undefined) => (v == null ? '—' : formatPrice(v, d));
  const act = async (job: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try { await job(); notify(ok); } catch (e) { notify(tradeErrorText(e, L), 'error'); } finally { setBusy(false); setAsk(false); setEdit(false); void useTrading.getState().refresh(venue, 'book'); }
  };
  const save = () => {
    const s = parseNum(sl); const t = parseNum(tp);
    void act(async () => {
      await protectPosition(venue, { symbol: p.symbol, side: p.side, stop_loss: s, take_profit: t });
      useTrading.getState().rememberProtection(venue, p.symbol, p.side, { sl: s, tp: t });
    }, L('حد ضرر و سود ثبت شد', 'Protection set'));
  };
  return (
    <>
      <tr className="click" onClick={() => usePrefs.getState().setSymbol(p.symbol)}>
        <td><span className="acct-sym"><SymbolLogo symbol={p.symbol} size={16} /><b className="sym">{p.symbol}</b>{p.leverage ? <span className="muted num">{p.leverage}x</span> : null}</span></td>
        <td className={p.side === 'long' ? 'buy-t' : 'down'}>{p.side === 'long' ? L('خرید', 'Long') : L('فروش', 'Short')}</td>
        <td className="r num">{p.quantity}</td><td className="r num">{f(p.entry)}</td><td className="r num">{f(last)}</td>
        <td className={`r num ${(pnl ?? 0) >= 0 ? 'up' : 'down'}`} dir="ltr">{money(pnl, true)}{pct != null ? <small> ({pct >= 0 ? '+' : '−'}{Math.abs(pct).toFixed(2)}%)</small> : null}</td>
        <td className="r num">{f(p.liquidation)}</td><td className="r num down">{f(prot.sl)}</td><td className="r num up">{f(prot.tp)}</td>
        <td className="r acct-acts" onClick={(e) => e.stopPropagation()}>
          <button className="btn btn-quiet sm" disabled={busy} onClick={() => setEdit(!edit)}>SL/TP</button>
          {ask ? (
            <span className="vip-ask"><span className="fa">{L('بسته شود؟', 'Close it?')}</span>
              <button className="btn sell sm" disabled={busy} onClick={() => void act(() => closePosition(venue, { client_request_id: uuid(), symbol: p.symbol, side: p.side, quantity: null }), L(`دستور بستن ${p.symbol} فرستاده شد`, `${p.symbol} close sent`))}>{L('بستن', 'Close')}</button>
              <button className="lt" onClick={() => setAsk(false)} aria-label="×"><Icon name="ph-x" size="sm" /></button></span>
          ) : <button className="btn btn-quiet sm" disabled={busy} onClick={() => setAsk(true)}>{L('بستن', 'Close')}</button>}
        </td>
      </tr>
      {edit ? (
        <tr className="acct-edit"><td colSpan={10}>
          <label><span>SL</span><input className="num" dir="ltr" inputMode="decimal" value={sl} onChange={(e) => setSl(e.target.value)} /></label>
          <label><span>TP</span><input className="num" dir="ltr" inputMode="decimal" value={tp} onChange={(e) => setTp(e.target.value)} /></label>
          <button className="btn btn-primary sm" disabled={busy || (parseNum(sl) == null && parseNum(tp) == null)} onClick={save}>{L('ثبت روی صرافی', 'Set on the exchange')}</button>
          <button className="btn btn-quiet sm" onClick={() => setEdit(false)}>{L('انصراف', 'Cancel')}</button>
          <span className="muted fa">{L('روی چارت هم می‌توانید خط SL و TP را بکشید.', 'You can also drag the SL and TP lines on the chart.')}</span>
        </td></tr>
      ) : null}
    </>
  );
}

function Orders({ venue, orders }: { venue: Venue; orders: Order[] }) {
  const L = useL();
  const [moving, setMoving] = useState<string | null>(null);
  const [price, setPrice] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  if (!orders.length) return <div className="empty fa">{L('سفارشی ندارید.', 'No orders.')}</div>;
  const act = async (id: string, job: () => Promise<unknown>, ok: string) => {
    setBusy(id);
    try { await job(); notify(ok); } catch (e) { notify(tradeErrorText(e, L), 'error'); } finally { setBusy(null); setMoving(null); void useTrading.getState().refresh(venue, 'book'); }
  };
  return (
    <table className="table acct-table">
      <thead><tr>
        <th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th>{L('نوع', 'Type')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('قیمت', 'Price')}</th>
        <th className="r">SL</th><th className="r">TP</th><th>{L('وضعیت', 'Status')}</th><th>{L('زمان', 'Time')}</th><th />
      </tr></thead>
      <tbody>{orders.map((o) => {
        const d = digitsOf(o.symbol);
        const f = (v: number | null) => (v == null ? '—' : formatPrice(v, d));
        return (
          <tr key={o.id} className="click" onClick={() => usePrefs.getState().setSymbol(o.symbol)}>
            <td><span className="acct-sym"><SymbolLogo symbol={o.symbol} size={16} /><b className="sym">{o.symbol}</b></span></td>
            <td className={o.side === 'buy' ? 'buy-t' : 'down'}>{o.side === 'buy' ? L('خرید', 'Buy') : L('فروش', 'Sell')}{o.reduceOnly ? <small className="muted"> RO</small> : null}</td>
            <td>{o.type === 'limit' ? L('محدود', 'Limit') : o.type === 'stop' ? L('استاپ', 'Stop') : L('بازار', 'Market')}</td>
            <td className="r num">{o.quantity ?? '—'}{o.filled ? <small className="muted"> ({o.filled})</small> : null}</td>
            <td className="r num">{o.price != null ? f(o.price) : o.avgPrice != null ? f(o.avgPrice) : L('بازار', 'market')}</td>
            <td className="r num down">{f(o.stopLoss)}</td><td className="r num up">{f(o.takeProfit)}</td>
            <td title={o.error ?? ''}><span className={`acct-status ${o.status}`}>{L(...(ORDER_STATUS[o.status] ?? [o.status, o.status]))}</span>{o.error ? <small className="down fa" dir="auto"> {o.error}</small> : null}</td>
            <td className="num muted">{dateTime(o.createdAt)}</td>
            <td className="r acct-acts" onClick={(e) => e.stopPropagation()}>
              {moving === o.id ? (
                <span className="vip-ask"><input className="num" dir="ltr" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} aria-label="price" />
                  <button className="btn btn-primary sm" disabled={busy === o.id || parseNum(price) == null} onClick={() => void act(o.id, () => amendOrder(venue, o.id, parseNum(price)!), L('قیمت سفارش عوض شد', 'Order price changed'))}>{L('ثبت', 'Set')}</button>
                  <button className="lt" onClick={() => setMoving(null)} aria-label="×"><Icon name="ph-x" size="sm" /></button></span>
              ) : (
                <>
                  {o.canAmend ? <button className="btn btn-quiet sm" disabled={busy === o.id} onClick={() => { setMoving(o.id); setPrice(o.price != null ? String(o.price) : ''); }}>{L('ویرایش', 'Modify')}</button> : null}
                  {o.canCancel ? <button className="btn btn-quiet sm" disabled={busy === o.id} onClick={() => void act(o.id, () => cancelOrder(venue, o.id), L('سفارش لغو شد', 'Order cancelled'))}>{L('لغو', 'Cancel')}</button> : null}
                </>
              )}
            </td>
          </tr>
        );
      })}</tbody>
    </table>
  );
}

function History({ venue }: { venue: Venue }) {
  const L = useL();
  const [rows, setRows] = useState<Fill[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [n, setN] = useState(0);
  useEffect(() => {
    let alive = true;
    setRows(null); setError(null);
    getHistory(venue).then((r) => { if (alive) setRows(r); }).catch((e) => { if (alive) setError(tradeErrorText(e, L)); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [venue, n]);
  if (error) return <div className="empty fa">{error} <button className="link" onClick={() => setN(n + 1)}>{L('دوباره', 'Retry')}</button></div>;
  if (!rows) return <div className="empty fa">{L('در حال بارگذاری…', 'Loading…')}</div>;
  if (!rows.length) return <div className="empty fa">{L('معامله‌ی بسته‌شده‌ای در این بازه نیست.', 'No closed trades in this period.')}</div>;
  const total = rows.reduce((s, r) => s + (r.pnl ?? 0), 0);
  return (
    <table className="table acct-table">
      <thead><tr>
        <th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th>
        <th className="r">P&amp;L</th><th className="r">{L('کارمزد', 'Fee')}</th><th>{L('باز شد', 'Opened')}</th><th>{L('بسته شد', 'Closed')}</th>
      </tr></thead>
      <tbody>{rows.map((r) => {
        const d = digitsOf(r.symbol);
        const f = (v: number | null) => (v == null ? '—' : formatPrice(v, d));
        return (
          <tr key={r.id}>
            <td><span className="acct-sym"><SymbolLogo symbol={r.symbol} size={16} /><b className="sym">{r.symbol}</b></span></td>
            <td className={r.side === 'buy' ? 'buy-t' : 'down'}>{r.side === 'buy' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</td>
            <td className="r num">{r.quantity ?? '—'}</td><td className="r num">{f(r.entry)}</td><td className="r num">{f(r.exit)}</td>
            <td className={`r num ${(r.pnl ?? 0) >= 0 ? 'up' : 'down'}`} dir="ltr">{money(r.pnl, true)}</td><td className="r num muted">{money(r.fee)}</td>
            <td className="num muted">{dateTime(r.openedAt)}</td><td className="num muted">{dateTime(r.closedAt)}</td>
          </tr>
        );
      })}</tbody>
      <tfoot><tr><td colSpan={5} className="fa muted">{L('جمع', 'Total')}</td><td className={`r num ${total >= 0 ? 'up' : 'down'}`} dir="ltr">{money(total, true)}</td><td colSpan={3} /></tr></tfoot>
    </table>
  );
}
