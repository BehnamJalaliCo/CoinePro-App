import { useMemo, useState } from 'react';
import { usePaper, pnlOf, marginOf, paperStats, type OrderType, type Side } from '../state/paper';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Field, Tabs, dateTime, downloadCsv, money, parseNum, useL } from './kit';
import { EquityCurve, PaperStatsGrid, RiskSizer } from '../shell/PaperParts';

// The full practice account (feature/papertrade): an order ticket with every order type, the
// account's rules, open positions with their protection, working orders, and the history with
// its statistics (the equity curve, the longest winning and losing runs). Positions close in part
// (25/50/75%), the size can come from the risk, and the bottom dock's trading panel shows the same
// account. The book runs in state/paper.ts against the live prices.
export default function PaperPage() {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const tickers = useMarket((s) => s.tickers);
  const book = usePaper();
  const tk = tickers[symbol];
  const digits = instrumentOf(symbol, tk?.last).digits;
  const [tab, setTab] = useState<'positions' | 'orders' | 'history' | 'stats' | 'rules'>('positions');
  const [side, setSide] = useState<Side>('long');
  const [type, setType] = useState<OrderType>('market');
  const [f, setF] = useState({ qty: '', limit: '', stop: '', trail: '1', sl: '', tp: '' });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  const unreal = book.positions.reduce((n, p) => n + (tickers[p.symbol] ? pnlOf(p, tickers[p.symbol]!.last) : 0), 0);
  const used = book.positions.reduce((n, p) => n + marginOf(p), 0);
  const equity = book.balance + unreal;

  const submit = () => {
    if (!tk) return;
    const qty = parseNum(f.qty) ?? 0;
    book.place({
      symbol, side, type, qty,
      limitPrice: parseNum(f.limit) ?? undefined, stopPrice: parseNum(f.stop) ?? undefined, trailPercent: parseNum(f.trail) ?? undefined,
      sl: parseNum(f.sl) ?? undefined, tp: parseNum(f.tp) ?? undefined,
    }, tk.last);
  };

  const stats = useMemo(() => paperStats(book.history, book.rules.startingBalance), [book.history, book.rules.startingBalance]);
  const entryNow = type === 'limit' || type === 'stop_limit' ? parseNum(f.limit) : type === 'stop' ? parseNum(f.stop) : tk?.last ?? null;

  const err = book.lastError === 'margin' ? L('مارجین کافی نیست.', 'Not enough margin.') : book.lastError ? L('قیمت یا مقدار سفارش درست نیست.', 'The order price or quantity is not valid.') : null;
  const go = (s: string) => { setSymbol(s); close(); };

  return (
    <div className="page-inner paper-page">
      <div className="pulse">
        <div className="card"><span className="muted fa">{L('موجودی', 'Balance')}</span><b className="num">{money(book.balance)} USDT</b></div>
        <div className="card"><span className="muted fa">{L('سود و زیان باز', 'Unrealized P&L')}</span><b className={`num ${unreal >= 0 ? 'up' : 'down'}`}>{money(unreal)}</b></div>
        <div className="card"><span className="muted fa">{L('ارزش حساب', 'Equity')}</span><b className="num">{money(equity)}</b></div>
        <div className="card"><span className="muted fa">{L('مارجین در استفاده', 'Margin used')}</span><b className="num">{money(used)} · x{book.rules.leverage}</b></div>
      </div>
      <div className="split">
        <form className="card form ticket" onSubmit={(e) => { e.preventDefault(); submit(); }}>
          <div className="row"><SymbolLogo symbol={symbol} size={20} /><b className="sym">{symbol}</b><span className="num muted">{tk ? formatPrice(tk.last, digits) : '—'}</span></div>
          <Tabs value={side} onChange={setSide} tabs={[{ id: 'long', label: L('خرید', 'Buy') }, { id: 'short', label: L('فروش', 'Sell') }]} />
          <Field label={L('نوع سفارش', 'Order type')}>
            <select value={type} onChange={(e) => setType(e.target.value as OrderType)}>
              <option value="market">{L('بازار', 'Market')}</option><option value="limit">{L('محدود', 'Limit')}</option><option value="stop">{L('استاپ', 'Stop')}</option>
              <option value="stop_limit">{L('استاپ‌محدود', 'Stop limit')}</option><option value="trailing">{L('حد ضرر متحرک', 'Trailing stop')}</option>
            </select>
          </Field>
          <Field label={L('مقدار', 'Quantity')}><input className="num" dir="ltr" inputMode="decimal" value={f.qty} onChange={set('qty')} placeholder={tk ? String(Number((1000 / tk.last).toPrecision(2))) : ''} /></Field>
          <RiskSizer entry={entryNow} sl={parseNum(f.sl)} onSize={(q) => setF({ ...f, qty: String(q) })} />
          {type === 'limit' || type === 'stop_limit' ? <Field label={L('قیمت محدود', 'Limit price')}><input className="num" dir="ltr" inputMode="decimal" value={f.limit} onChange={set('limit')} /></Field> : null}
          {type === 'stop' || type === 'stop_limit' ? <Field label={L('قیمت فعال‌سازی', 'Stop price')}><input className="num" dir="ltr" inputMode="decimal" value={f.stop} onChange={set('stop')} /></Field> : null}
          {type === 'trailing' ? <Field label={L('فاصله‌ی دنباله (درصد)', 'Trail %')}><input className="num" dir="ltr" inputMode="decimal" value={f.trail} onChange={set('trail')} /></Field> : null}
          <div className="row two">
            <Field label={L('حد ضرر', 'Stop loss')}><input className="num" dir="ltr" inputMode="decimal" value={f.sl} onChange={set('sl')} /></Field>
            <Field label={L('حد سود', 'Take profit')}><input className="num" dir="ltr" inputMode="decimal" value={f.tp} onChange={set('tp')} /></Field>
          </div>
          {err ? <p className="down fa small">{err}</p> : null}
          <button className={`btn ${side === 'long' ? 'buy' : 'sell'}`} disabled={!tk}>{side === 'long' ? L('ثبت خرید', 'Place buy') : L('ثبت فروش', 'Place sell')}</button>
          <p className="muted small fa">{L(`کارمزد ${book.rules.takerFeePercent}% · لغزش ${book.rules.slippagePercent}% · اسپرد ${book.rules.spreadPercent}%`, `Fee ${book.rules.takerFeePercent}% · slippage ${book.rules.slippagePercent}% · spread ${book.rules.spreadPercent}%`)}</p>
        </form>
        <div className="grow">
          <Tabs value={tab} onChange={setTab} tabs={[
            { id: 'positions', label: `${L('موقعیت‌ها', 'Positions')} (${book.positions.length})` }, { id: 'orders', label: `${L('سفارش‌های باز', 'Working orders')} (${book.orders.length})` },
            { id: 'history', label: L('تاریخچه', 'History') }, { id: 'stats', label: L('آمار', 'Statistics') }, { id: 'rules', label: L('قوانین حساب', 'Account rules') },
          ]} />
          {tab === 'positions' ? (
            <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">SL / TP</th><th className="r">P&amp;L</th><th /></tr></thead>
              <tbody>{book.positions.map((p) => {
                const px = tickers[p.symbol]?.last; const pnl = px ? pnlOf(p, px) : 0; const d = instrumentOf(p.symbol, px).digits;
                return (
                  <tr key={p.id}>
                    <td><button className="link-btn sym" onClick={() => go(p.symbol)}>{p.symbol}</button>{book.orders.some((o) => o.symbol === p.symbol) ? <span className="pp-badge pending fa" title={L('سفارش در انتظار روی این نماد', 'Working orders on this symbol')}>{L('سفارش', 'order')}</span> : null}</td>
                    <td className={p.side === 'long' ? 'up' : 'down'}>{p.side === 'long' ? 'Long' : 'Short'}</td>
                    <td className="r num">{p.qty}</td><td className="r num">{formatPrice(p.entry, d)}</td>
                    <td className="r num">
                      <input className="mini num" dir="ltr" defaultValue={p.sl ?? ''} placeholder="SL" onBlur={(e) => book.protect(p.id, parseNum(e.target.value) ?? undefined, p.tp)} aria-label="stop loss" />
                      <input className="mini num" dir="ltr" defaultValue={p.tp ?? ''} placeholder="TP" onBlur={(e) => book.protect(p.id, p.sl, parseNum(e.target.value) ?? undefined)} aria-label="take profit" />
                    </td>
                    <td className={`r num ${pnl >= 0 ? 'up' : 'down'}`}>{money(pnl)}</td>
                    <td className="r nowrap">
                      {[0.25, 0.5, 0.75].map((x) => <button key={x} className="pp-part num" title={L('بستن بخشی از حجم', 'Close part of the position')} disabled={!px} onClick={() => px && book.closePart(p.id, x, px)}>{x * 100}%</button>)}
                      <button className="btn btn-quiet sm" disabled={!px} onClick={() => px && book.close(p.id, px)}>{L('بستن', 'Close')}</button>
                    </td>
                  </tr>
                );
              })}</tbody></table>
          ) : null}
          {tab === 'orders' ? (
            <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('نوع', 'Type')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('قیمت', 'Price')}</th><th /></tr></thead>
              <tbody>{book.orders.map((o) => (
                <tr key={o.id}><td className="sym">{o.symbol}</td><td>{o.type}{o.triggered ? ' ✓' : ''}</td><td className={o.side === 'long' ? 'up' : 'down'}>{o.side === 'long' ? 'Buy' : 'Sell'}</td>
                  <td className="r num">{o.qty}</td><td className="r num">{o.limitPrice ?? o.stopPrice ?? (o.trailPercent ? `${o.trailPercent}%` : '—')}</td>
                  <td className="r"><button className="btn btn-quiet sm" onClick={() => book.cancel(o.id)}>{L('لغو', 'Cancel')}</button></td></tr>
              ))}</tbody></table>
          ) : null}
          {tab === 'history' ? (
            <>
              <div className="row"><button className="btn btn-quiet sm" onClick={() => downloadCsv('paper-trades.csv', [['symbol', 'side', 'qty', 'entry', 'exit', 'pnl', 'fees', 'reason', 'opened', 'closed'], ...book.history.map((h) => [h.symbol, h.side, h.qty, h.entry, h.exit, +h.pnl.toFixed(2), +(h.fees ?? 0).toFixed(2), h.reason ?? 'manual', new Date(h.openedAt).toISOString(), new Date(h.closedAt).toISOString()])])}>CSV</button></div>
              <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th><th>{L('دلیل', 'Reason')}</th><th className="r">P&amp;L</th><th className="r">{L('زمان', 'Closed')}</th></tr></thead>
                <tbody>{book.history.map((h) => { const d = instrumentOf(h.symbol, h.exit).digits; return (
                  <tr key={h.id + h.closedAt}><td className="sym">{h.symbol}</td><td className={h.side === 'long' ? 'up' : 'down'}>{h.side === 'long' ? 'Long' : 'Short'}</td><td className="r num">{formatPrice(h.entry, d)}</td><td className="r num">{formatPrice(h.exit, d)}</td><td>{(h.reason ?? 'manual').toUpperCase()}</td><td className={`r num ${h.pnl >= 0 ? 'up' : 'down'}`}>{money(h.pnl)}</td><td className="r num">{dateTime(h.closedAt)}</td></tr>
                ); })}</tbody></table>
            </>
          ) : null}
          {tab === 'stats' ? (
            <div className="pp-page-stats">
              <EquityCurve curve={stats.curve} start={book.rules.startingBalance} height={160} />
              <PaperStatsGrid stats={stats} money={(v) => money(v)} />
            </div>
          ) : null}
          {tab === 'rules' ? <Rules /> : null}
        </div>
      </div>
    </div>
  );
}

function Rules() {
  const L = useL();
  const rules = usePaper((s) => s.rules);
  const setRules = usePaper((s) => s.setRules);
  const reset = usePaper((s) => s.reset);
  const num = (k: keyof typeof rules, label: string) => (
    <Field label={label}><input className="num" dir="ltr" inputMode="decimal" defaultValue={rules[k]} onBlur={(e) => { const v = parseNum(e.target.value); if (v != null) setRules({ [k]: v }); }} /></Field>
  );
  return (
    <form className="form" onSubmit={(e) => e.preventDefault()}>
      {num('startingBalance', L('موجودی شروع (USDT)', 'Starting balance (USDT)'))}
      {num('leverage', L('اهرم (۱ تا ۵۰۰)', 'Leverage (1–500)'))}
      {num('takerFeePercent', L('کارمزد تیکر (درصد)', 'Taker fee %'))}
      {num('makerFeePercent', L('کارمزد میکر (درصد)', 'Maker fee %'))}
      {num('slippagePercent', L('لغزش (درصد)', 'Slippage %'))}
      {num('spreadPercent', L('اسپرد فرضی (درصد)', 'Assumed spread %'))}
      {num('stopOutPercent', L('حد استاپ‌اوت (درصد مارجین)', 'Stop-out (% of margin)'))}
      <button type="button" className="btn btn-quiet sm" onClick={() => { if (window.confirm(L('حساب آزمایشی از نو شروع شود؟', 'Start the paper account over?'))) reset(); }}>{L('شروع دوباره‌ی حساب', 'Reset the account')}</button>
    </form>
  );
}
