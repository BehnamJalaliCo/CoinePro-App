import { useEffect, useMemo, useRef, useState } from 'react';
import { usePaper, pnlOf, marginOf, paperStats, type OrderType, type PaperTrade, type Side } from '../state/paper';
import { useMarket, digitsOf } from '../state/market';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useJournal } from '../state/journal';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { dateTime, downloadCsv, parseNum, useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { EquityCurve, PaperStatsGrid, RiskSizer } from './PaperParts';
import './studies.css';

// TradingView's trading panel under the chart, for the paper account: the account's figures, an
// order ticket (every order type, stop and target, size from the risk), and the account manager's
// tabs — positions (with 25/50/75% partial closes and their stop and target), working orders,
// history, statistics with the equity curve, and the journal. Positions, stops, targets and orders
// of the open symbol are also drawn on the chart (shell/PaperLines.tsx), where they can be dragged.
type Tab = 'positions' | 'orders' | 'history' | 'stats' | 'journal';

const money = (v: number) => `${v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;

export function PaperPanel() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const tickers = useMarket((s) => s.tickers);
  const book = usePaper();
  const tk = tickers[symbol];
  const digits = instrumentOf(symbol, tk?.last).digits;
  const [tab, setTab] = useState<Tab>('positions');
  const [type, setType] = useState<OrderType>('market');
  const [f, setF] = useState({ qty: '', limit: '', stop: '', trail: '1', sl: '', tp: '' });
  // A fresh ticket for each symbol, sized at about 1,000 USDT until the reader types a quantity.
  const seeded = useRef<string | null>(null);
  useEffect(() => {
    if (!tk || seeded.current === symbol) return;
    seeded.current = symbol;
    setF((x) => ({ ...x, qty: String(Number((1000 / tk.last).toPrecision(2))), limit: '', stop: '', sl: '', tp: '' }));
  }, [tk, symbol]);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  const unreal = book.positions.reduce((n, p) => n + (tickers[p.symbol] ? pnlOf(p, tickers[p.symbol]!.last) : 0), 0);
  const used = book.positions.reduce((n, p) => n + marginOf(p), 0);
  const equity = book.balance + unreal;
  const realized = book.history.reduce((n, h) => n + h.pnl, 0);
  const stats = useMemo(() => paperStats(book.history, book.rules.startingBalance), [book.history, book.rules.startingBalance]);
  const entryNow = type === 'limit' || type === 'stop_limit' ? parseNum(f.limit) : type === 'stop' ? parseNum(f.stop) : tk?.last ?? null;

  const place = (side: Side) => {
    if (!tk) return;
    const id = book.place({
      symbol, side, type, qty: parseNum(f.qty) ?? 0,
      limitPrice: parseNum(f.limit) ?? undefined, stopPrice: parseNum(f.stop) ?? undefined, trailPercent: parseNum(f.trail) ?? undefined,
      sl: parseNum(f.sl) ?? undefined, tp: parseNum(f.tp) ?? undefined,
    }, tk.last);
    if (id) setTab(type === 'market' ? 'positions' : 'orders');
  };
  const err = book.lastError === 'margin' ? L('مارجین کافی نیست.', 'Not enough margin.') : book.lastError ? L('قیمت یا مقدار سفارش درست نیست.', 'The order price or quantity is not valid.') : null;
  const count = (n: number) => (fa ? faNum(n) : String(n));
  const ordersFor = (s: string) => book.orders.filter((o) => o.symbol === s).length;
  const journal = useJournal((s) => s.entries);
  const logged = (h: PaperTrade) => journal.some((e) => e.symbol === h.symbol && e.entry === h.entry && e.exit === h.exit && e.size === h.qty);
  const log = (h: PaperTrade) => useJournal.getState().upsert({ symbol: h.symbol, buy: h.side === 'long', entry: h.entry, exit: h.exit, size: h.qty, pnl: h.pnl, emotion: '', note: '', lesson: '', tags: [] });
  const SIDE = (s: Side) => (s === 'long' ? L('خرید', 'Long') : L('فروش', 'Short'));
  const TYPE: Record<OrderType, string> = { market: L('بازار', 'Market'), limit: L('لیمیت', 'Limit'), stop: L('استاپ', 'Stop'), stop_limit: L('استاپ‌لیمیت', 'Stop limit'), trailing: L('حد ضرر متحرک', 'Trailing stop') };
  const REASON = { manual: L('دستی', 'Manual'), sl: L('حد ضرر', 'Stop loss'), tp: L('حد سود', 'Take profit'), liq: L('استاپ‌اوت', 'Stop-out') };

  return (
    <div className="paper pp2">
      <div className="paper-account">
        <div className="bt-stat"><span className="bt-k fa">{L('موجودی', 'Balance')}</span><span className="bt-v num">{money(book.balance)} USDT</span></div>
        <div className="bt-stat"><span className="bt-k fa">{L('ارزش حساب', 'Equity')}</span><span className="bt-v num">{money(equity)}</span></div>
        <div className="bt-stat"><span className="bt-k fa">{L('سود و زیان محقق', 'Realized P&L')}</span><span className={`bt-v num ${realized >= 0 ? 'up' : 'down'}`}>{money(realized)}</span></div>
        <div className="bt-stat"><span className="bt-k fa">{L('سود و زیان باز', 'Unrealized P&L')}</span><span className={`bt-v num ${unreal >= 0 ? 'up' : 'down'}`}>{money(unreal)}</span></div>
        <div className="bt-stat"><span className="bt-k fa">{L('مارجین در استفاده', 'Account margin')}</span><span className="bt-v num">{money(used)}</span></div>
        <div className="bt-stat"><span className="bt-k fa">{L('قابل استفاده', 'Available funds')}</span><span className="bt-v num">{money(equity - used)}</span></div>
      </div>
      <div className="pp-main">
        <form className="paper-ticket pp-ticket" onSubmit={(e) => e.preventDefault()} dir={fa ? 'rtl' : 'ltr'}>
          <div className="pp-sym"><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span><span className="num muted">{tk ? formatPrice(tk.last, digits) : '—'}</span></div>
          <select value={type} onChange={(e) => setType(e.target.value as OrderType)} aria-label={L('نوع سفارش', 'Order type')}>
            {(Object.keys(TYPE) as OrderType[]).map((k) => <option key={k} value={k}>{TYPE[k]}</option>)}
          </select>
          <label className="pp-f"><span className="fa">{L('مقدار', 'Quantity')}</span><input className="num" type="number" min="0" step="any" value={f.qty} onChange={set('qty')} aria-label="quantity" dir="ltr" /></label>
          {type === 'limit' || type === 'stop_limit' ? <label className="pp-f"><span className="fa">{L('قیمت لیمیت', 'Limit price')}</span><input className="num" inputMode="decimal" value={f.limit} onChange={set('limit')} placeholder={tk ? formatPrice(tk.last, digits).replace(/,/g, '') : ''} dir="ltr" /></label> : null}
          {type === 'stop' || type === 'stop_limit' ? <label className="pp-f"><span className="fa">{L('قیمت استاپ', 'Stop price')}</span><input className="num" inputMode="decimal" value={f.stop} onChange={set('stop')} placeholder={tk ? formatPrice(tk.last, digits).replace(/,/g, '') : ''} dir="ltr" /></label> : null}
          {type === 'trailing' ? <label className="pp-f"><span className="fa">{L('فاصله (درصد)', 'Trail %')}</span><input className="num" inputMode="decimal" value={f.trail} onChange={set('trail')} dir="ltr" /></label> : null}
          <div className="pp-two">
            <label className="pp-f"><span className="fa">{L('حد ضرر', 'Stop loss')}</span><input className="num" inputMode="decimal" value={f.sl} onChange={set('sl')} dir="ltr" /></label>
            <label className="pp-f"><span className="fa">{L('حد سود', 'Take profit')}</span><input className="num" inputMode="decimal" value={f.tp} onChange={set('tp')} dir="ltr" /></label>
          </div>
          <RiskSizer compact entry={entryNow} sl={parseNum(f.sl)} onSize={(q) => setF({ ...f, qty: String(q) })} />
          {err ? <p className="down fa small pp-err">{err}</p> : null}
          <div className="pp-two">
            <button type="button" className="btn buy sm" disabled={!tk} onClick={() => place('long')}>Buy</button>
            <button type="button" className="btn sell sm" disabled={!tk} onClick={() => place('short')}>Sell</button>
          </div>
        </form>
        <div className="pp-right">
          <div className="pp-tabs" role="tablist" dir={fa ? 'rtl' : 'ltr'}>
            {([['positions', L('موقعیت‌ها', 'Positions'), book.positions.length], ['orders', L('سفارش‌ها', 'Orders'), book.orders.length], ['history', L('تاریخچه', 'History'), 0], ['stats', L('آمار', 'Statistics'), 0], ['journal', L('ژورنال', 'Journal'), 0]] as Array<[Tab, string, number]>).map(([id, label, n]) => (
              <button key={id} role="tab" aria-selected={tab === id} className={`pp-tab${tab === id ? ' on' : ''}`} onClick={() => setTab(id)}>
                <span className="fa">{label}</span>{n ? <span className={`pp-badge${id === 'orders' ? ' pending' : ''}`}>{count(n)}</span> : null}
              </button>
            ))}
            <span className="ws-spacer" />
            {tab === 'history' && book.history.length ? <button className="link-btn" onClick={() => downloadCsv('paper-trades.csv', [['symbol', 'side', 'qty', 'entry', 'exit', 'pnl', 'fees', 'reason', 'opened', 'closed'], ...book.history.map((h) => [h.symbol, h.side, h.qty, h.entry, h.exit, +h.pnl.toFixed(2), +(h.fees ?? 0).toFixed(2), h.reason ?? 'manual', new Date(h.openedAt).toISOString(), new Date(h.closedAt).toISOString()])])}>CSV</button> : null}
            <button className="lt" title={L('شروع دوباره‌ی حساب', 'Reset the account')} onClick={() => { if (window.confirm(L('حساب آزمایشی از نو شروع شود؟', 'Start the paper account over?'))) book.reset(); }}><Icon name="ph-arrow-counter-clockwise" size="sm" /></button>
            <button className="link-btn" onClick={() => usePage.getState().open('paper')}><span className="fa">{L('صفحه‌ی کامل', 'Full page')}</span></button>
          </div>
          <div className="paper-lists" dir={fa ? 'rtl' : 'ltr'}>
            {tab === 'positions' ? (
              <div className="bt-trades">
                <div className="bt-row bt-head pp-pos"><span className="fa">{L('نماد', 'Symbol')}</span><span className="fa">{L('سمت', 'Side')}</span><span className="fa">{L('مقدار', 'Qty')}</span><span className="fa">{L('ورود', 'Entry')}</span><span className="fa">{L('آخرین', 'Last')}</span><span>SL</span><span>TP</span><span className="fa">{L('سود و زیان', 'P&L')}</span><span /></div>
                {!book.positions.length ? <div className="empty fa">{L('موقعیت بازی نیست.', 'No open positions.')}</div> : null}
                {book.positions.map((p) => {
                  const px = tickers[p.symbol]?.last;
                  const pnl = px ? pnlOf(p, px) : 0;
                  const d = instrumentOf(p.symbol, px).digits;
                  const n = ordersFor(p.symbol);
                  return (
                    <div key={p.id} className="bt-row pp-pos">
                      <span className="sym"><button className="link-btn sym" onClick={() => setSymbol(p.symbol)}>{p.symbol}</button>{n ? <span className="pp-badge pending" title={L('سفارش در انتظار روی این نماد', 'Working orders on this symbol')}>{count(n)}</span> : null}</span>
                      <span className={`fa ${p.side === 'long' ? 'up' : 'down'}`}>{SIDE(p.side)}</span>
                      <span className="num">{p.qty}</span>
                      <span className="num">{formatPrice(p.entry, d)}</span>
                      <span className="num">{px ? formatPrice(px, d) : '—'}</span>
                      <span><input key={`${p.id}:sl:${p.sl}`} className="mini num" dir="ltr" defaultValue={p.sl != null ? Number(p.sl.toFixed(d)) : ''} placeholder="—" aria-label="stop loss" onBlur={(e) => { const v = parseNum(e.target.value) ?? undefined; if (v !== (p.sl != null ? Number(p.sl.toFixed(d)) : undefined)) book.protect(p.id, v, p.tp); }} onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }} /></span>
                      <span><input key={`${p.id}:tp:${p.tp}`} className="mini num" dir="ltr" defaultValue={p.tp != null ? Number(p.tp.toFixed(d)) : ''} placeholder="—" aria-label="take profit" onBlur={(e) => { const v = parseNum(e.target.value) ?? undefined; if (v !== (p.tp != null ? Number(p.tp.toFixed(d)) : undefined)) book.protect(p.id, p.sl, v); }} onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }} /></span>
                      <span className={`num ${pnl >= 0 ? 'up' : 'down'}`}>{money(pnl)} <small>{px ? `${((pnl / (p.qty * p.entry)) * 100 * Math.max(1, p.leverage ?? 1)).toFixed(2)}%` : ''}</small></span>
                      <span className="pp-acts">
                        <button className="lt" title={L('بستن', 'Close')} disabled={!px} onClick={() => px && book.close(p.id, px)}><Icon name="ph-x" size="sm" /></button>
                        {[0.25, 0.5, 0.75].map((x) => <button key={x} className="pp-part num" title={L(`بستن ${x * 100}% حجم`, `Close ${x * 100}%`)} disabled={!px} onClick={() => px && book.closePart(p.id, x, px)}>{x * 100}%</button>)}
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : null}
            {tab === 'orders' ? (
              <div className="bt-trades">
                <div className="bt-row bt-head pp-ord"><span className="fa">{L('نماد', 'Symbol')}</span><span className="fa">{L('سمت', 'Side')}</span><span className="fa">{L('نوع', 'Type')}</span><span className="fa">{L('مقدار', 'Qty')}</span><span className="fa">{L('قیمت', 'Price')}</span><span>SL</span><span>TP</span><span className="fa">{L('وضعیت', 'Status')}</span><span /></div>
                {!book.orders.length ? <div className="empty fa">{L('سفارش بازی نیست.', 'No working orders.')}</div> : null}
                {book.orders.map((o) => {
                  const d = digitsOf(o.symbol);
                  const price = o.type === 'trailing' ? `${o.trailPercent}%` : [o.stopPrice, o.limitPrice].filter((x): x is number => x != null).map((x) => formatPrice(x, d)).join(' → ');
                  return (
                    <div key={o.id} className="bt-row pp-ord">
                      <span className="sym">{o.symbol}</span>
                      <span className={`fa ${o.side === 'long' ? 'up' : 'down'}`}>{o.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</span>
                      <span className="fa">{TYPE[o.type]}</span>
                      <span className="num">{o.qty}</span>
                      <span className="num">{price || '—'}</span>
                      <span className="num">{o.sl != null ? formatPrice(o.sl, d) : '—'}</span>
                      <span className="num">{o.tp != null ? formatPrice(o.tp, d) : '—'}</span>
                      <span><span className="pp-badge pending fa">{o.triggered ? L('فعال شد', 'Triggered') : L('در انتظار', 'Pending')}</span></span>
                      <span className="pp-acts"><button className="lt" title={L('لغو سفارش', 'Cancel order')} onClick={() => book.cancel(o.id)}><Icon name="ph-x" size="sm" /></button></span>
                    </div>
                  );
                })}
              </div>
            ) : null}
            {tab === 'history' ? (
              <div className="bt-trades">
                <div className="bt-row bt-head pp-hist"><span className="fa">{L('نماد', 'Symbol')}</span><span className="fa">{L('سمت', 'Side')}</span><span className="fa">{L('مقدار', 'Qty')}</span><span className="fa">{L('ورود', 'Entry')}</span><span className="fa">{L('خروج', 'Exit')}</span><span className="fa">{L('دلیل', 'Reason')}</span><span className="fa">{L('سود و زیان', 'P&L')}</span><span className="fa">{L('زمان', 'Closed')}</span><span /></div>
                {!book.history.length ? <div className="empty fa">{L('هنوز معامله‌ای بسته نشده است.', 'No closed trades yet.')}</div> : null}
                {book.history.map((h) => {
                  const d = instrumentOf(h.symbol, h.exit).digits;
                  const done = logged(h);
                  return (
                    <div key={h.id + h.closedAt} className="bt-row pp-hist">
                      <span className="sym">{h.symbol}</span>
                      <span className={`fa ${h.side === 'long' ? 'up' : 'down'}`}>{SIDE(h.side)}</span>
                      <span className="num">{h.qty}</span>
                      <span className="num">{formatPrice(h.entry, d)}</span>
                      <span className="num">{formatPrice(h.exit, d)}</span>
                      <span className="fa">{REASON[h.reason ?? 'manual']}</span>
                      <span className={`num ${h.pnl >= 0 ? 'up' : 'down'}`}>{money(h.pnl)}</span>
                      <span className="num muted">{dateTime(h.closedAt)}</span>
                      <span className="pp-acts"><button className={`pp-part${done ? ' done' : ''}`} disabled={done} title={done ? L('در ژورنال ثبت شده', 'In the journal') : L('ثبت در ژورنال', 'Log in the journal')} onClick={() => log(h)}><Icon name={done ? 'ph-check' : 'ph-note'} size="sm" /></button></span>
                    </div>
                  );
                })}
              </div>
            ) : null}
            {tab === 'stats' ? (
              <div className="pp-statsbox">
                <EquityCurve curve={stats.curve} start={book.rules.startingBalance} height={110} />
                <PaperStatsGrid stats={stats} money={money} />
              </div>
            ) : null}
            {tab === 'journal' ? (
              <div className="bt-trades">
                <div className="bt-row bt-head pp-jr"><span className="fa">{L('زمان', 'Date')}</span><span className="fa">{L('نماد', 'Symbol')}</span><span className="fa">{L('سمت', 'Side')}</span><span className="fa">{L('ورود', 'Entry')}</span><span className="fa">{L('خروج', 'Exit')}</span><span className="fa">{L('سود و زیان', 'P&L')}</span><span className="fa">{L('یادداشت', 'Note')}</span></div>
                {!journal.length ? <div className="empty fa">{L('ژورنال خالی است؛ معامله‌های بسته را از تاریخچه ثبت کنید.', 'The journal is empty; log closed trades from the history.')}</div> : null}
                {journal.slice(0, 100).map((e) => (
                  <div key={e.id} className="bt-row pp-jr">
                    <span className="num muted">{dateTime(e.createdAt)}</span>
                    <span className="sym">{e.symbol}</span>
                    <span className={`fa ${e.buy ? 'up' : 'down'}`}>{e.buy ? L('خرید', 'Long') : L('فروش', 'Short')}</span>
                    <span className="num">{e.entry}</span>
                    <span className="num">{e.exit ?? '—'}</span>
                    <span className={`num ${(e.pnl ?? 0) >= 0 ? 'up' : 'down'}`}>{e.pnl != null ? money(e.pnl) : '—'}</span>
                    <span className="fa muted">{[e.emotion, ...e.tags].filter(Boolean).join(' · ') || e.note || '—'}</span>
                  </div>
                ))}
                <div className="pp-jr-foot"><button className="link-btn" onClick={() => usePage.getState().open('journal')}><span className="fa">{L('باز کردن ژورنال', 'Open the journal')}</span></button></div>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
