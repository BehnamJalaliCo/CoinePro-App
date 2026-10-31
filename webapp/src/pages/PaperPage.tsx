import { useMemo, useState } from 'react';
import { usePaper, pnlOf, marginOf, paperStats, taking, resting, feeFor, type OrderType, type Side, TIME_IN_FORCE, type TimeInForce, type PaperRules, type PaperFill } from '../state/paper';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Field, Tabs, dateTime, downloadCsv, money, parseNum, useL } from './kit';
import { faNum } from '../i18n/time';
import { JALALI_MONTHS, JALALI_MONTHS_EN, paperAsClosed, paperRecord, type PaperRecord } from './records';
import { ExportCard } from './PortfolioPage';
import './records.css';
import { EquityCurve, PaperStatsGrid, RiskSizer } from '../shell/PaperParts';

// The full practice account (feature/papertrade): an order ticket with every order type and the
// expected fill before it is placed (PaperTicket.FillPreview), the account's rules, open positions with their protection, working orders, and the history with
// its statistics (the equity curve, the longest winning and losing runs), the fill log that audits
// every price (PaperHistory), and the record by symbol and by month (PaperRecordTab). Positions close in part
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
  const [tab, setTab] = useState<'positions' | 'orders' | 'history' | 'record' | 'stats' | 'rules'>('positions');
  const [side, setSide] = useState<Side>('long');
  const [type, setType] = useState<OrderType>('market');
  const [tif, setTif] = useState<TimeInForce>('gtc');
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
      sl: parseNum(f.sl) ?? undefined, tp: parseNum(f.tp) ?? undefined, tif: type === 'limit' ? tif : undefined,
    }, tk.last);
  };

  const stats = useMemo(() => paperStats(book.history, book.rules.startingBalance), [book.history, book.rules.startingBalance]);
  const entryNow = type === 'limit' || type === 'stop_limit' ? parseNum(f.limit) : type === 'stop' ? parseNum(f.stop) : tk?.last ?? null;

  const err = book.lastError === 'margin' ? L('مارجین کافی نیست.', 'Not enough margin.') : book.lastError === 'immediate' ? L('بی‌درنگ پر نشد و لغو شد', 'Not filled at once, so cancelled') : book.lastError ? L('قیمت یا مقدار سفارش درست نیست.', 'The order price or quantity is not valid.') : null;
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
          {type === 'limit' ? <div className="field pp-tif"><span className="fa">{L('مدت اعتبار', 'Time in force')}</span><Tabs value={tif} onChange={setTif} tabs={TIME_IN_FORCE.map((x) => ({ id: x, label: x.toUpperCase() }))} /><small className="muted fa">{L('GTC: تا لغو · IOC: فوری یا لغو · FOK: کامل یا لغو', 'GTC: good till cancelled · IOC: immediate or cancel · FOK: fill or kill')}</small></div> : null}
          {type === 'stop' || type === 'stop_limit' ? <Field label={L('قیمت فعال‌سازی', 'Stop price')}><input className="num" dir="ltr" inputMode="decimal" value={f.stop} onChange={set('stop')} /></Field> : null}
          {type === 'trailing' ? <Field label={L('فاصله‌ی دنباله (درصد)', 'Trail %')}><input className="num" dir="ltr" inputMode="decimal" value={f.trail} onChange={set('trail')} /></Field> : null}
          <div className="row two">
            <Field label={L('حد ضرر', 'Stop loss')}><input className="num" dir="ltr" inputMode="decimal" value={f.sl} onChange={set('sl')} /></Field>
            <Field label={L('حد سود', 'Take profit')}><input className="num" dir="ltr" inputMode="decimal" value={f.tp} onChange={set('tp')} /></Field>
          </div>
          {tk ? <FillPreview rules={book.rules} last={tk.last} side={side} type={type} qty={parseNum(f.qty)} limit={parseNum(f.limit)} digits={digits} /> : null}
          {err ? <p className="down fa small">{err}</p> : null}
          <button className={`btn ${side === 'long' ? 'buy' : 'sell'}`} disabled={!tk}>{side === 'long' ? L('ثبت خرید', 'Place buy') : L('ثبت فروش', 'Place sell')}</button>
          <p className="muted small fa">{L(`کارمزد ${book.rules.takerFeePercent}% · لغزش ${book.rules.slippagePercent}% · اسپرد ${book.rules.spreadPercent}%`, `Fee ${book.rules.takerFeePercent}% · slippage ${book.rules.slippagePercent}% · spread ${book.rules.spreadPercent}%`)}</p>
        </form>
        <div className="grow">
          <Tabs value={tab} onChange={setTab} tabs={[
            { id: 'positions', label: `${L('موقعیت‌ها', 'Positions')} (${book.positions.length})` }, { id: 'orders', label: `${L('سفارش‌های باز', 'Working orders')} (${book.orders.length})` },
            { id: 'history', label: L('تاریخچه', 'History') }, { id: 'record', label: L('کارنامه', 'Record') }, { id: 'stats', label: L('آمار', 'Statistics') }, { id: 'rules', label: L('قوانین حساب', 'Account rules') },
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
                <tr key={o.id}><td className="sym">{o.symbol}</td><td>{o.type}{o.type === 'limit' ? ` · ${(o.tif ?? 'gtc').toUpperCase()}` : ''}{o.triggered ? ' ✓' : ''}</td><td className={o.side === 'long' ? 'up' : 'down'}>{o.side === 'long' ? 'Buy' : 'Sell'}</td>
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
              {book.fills.length ? <FillLog fills={book.fills} /> : null}
              <ExportCard trades={book.history.map(paperAsClosed)} name={(ext) => `coinepro-paper-trades.${ext}`} />
            </>
          ) : null}
          {tab === 'record' ? <Record /> : null}
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
  const wipe = usePaper((s) => s.wipe);
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
      <p className="muted small fa">{L('این‌ها فرض‌اند، نه واقعیت. کارمزد صرافی و بروکر شما فرق دارد؛ عدد خودتان را بگذارید.', 'These are assumptions, not facts. Your exchange or broker charges something else — put your own numbers in.')}</p>
      <div className="pr-danger">
        <button type="button" className="btn btn-quiet sm" data-act="reset" onClick={() => { if (window.confirm(`${L('شروع دوباره؟', 'Start again?')}\n${L('موجودی به عدد شروع برمی‌گردد. موقعیت‌های باز و سفارش‌های در انتظار حذف می‌شوند — بسته نمی‌شوند. تاریخچه‌ی معامله‌ها سر جایش می‌ماند.', 'The balance goes back to its starting number. Open positions and working orders are dropped — not closed. The trade history stays.')}`)) reset(); }}>{L('شروع دوباره', 'Start again')}</button>
        <button type="button" className="btn btn-quiet sm down" data-act="wipe" onClick={() => { if (window.confirm(`${L('حساب و تاریخچه پاک شود؟', 'Erase the account and its history?')}\n${L('هم حساب و هم تاریخچه‌ی معامله‌ها پاک می‌شود. برگشتی ندارد.', 'Both the account and every closed trade go. There is no undo.')}`)) wipe(); }}>{L('پاک کردن همه‌چیز', 'Erase everything')}</button>
      </div>
      <RulesCard rules={rules} />
    </form>
  );
}

/**
 * What this order is expected to cost before it is placed (OLD PaperTicket.FillPreview), computed
 * with the same functions the fill uses — if the two could disagree this would be an advertisement.
 */
function FillPreview({ rules, last, side, type, qty, limit, digits }: { rules: PaperRules; last: number; side: Side; type: OrderType; qty: number | null; limit: number | null; digits: number }) {
  const L = useL();
  if (!qty || !(qty > 0)) return null;
  const half = (last * rules.spreadPercent) / 100 / 2;
  // A limit already through the far side of the assumed spread takes the market (OLD PaperFills.marketable).
  const marketable = type === 'market' || (type === 'limit' && limit != null && (side === 'long' ? limit >= last + half : limit <= last - half));
  const row = (k: string, v: string) => <div className="rp-metric" key={k}><span className="muted fa">{k}</span><b className="num" dir="ltr">{v}</b></div>;
  let body;
  if (marketable) {
    const p = taking(side, last, rules, type === 'limit' ? limit ?? undefined : undefined);
    body = <>{row(L('قیمت پر شدن', 'Fill price'), formatPrice(p.price, digits))}{row(L('هزینه‌ی اسپرد', 'Spread cost'), money(p.spreadPerUnit * qty))}{row(L('لغزش', 'Slippage'), money(p.slippagePerUnit * qty))}{row(L('کارمزد', 'Fee'), money(feeFor(p, qty, rules)))}{row(L('مارجین لازم', 'Margin needed'), money((p.price * qty) / Math.max(1, rules.leverage)))}</>;
  } else {
    const rest = type === 'limit' || type === 'stop_limit' ? limit : null;
    const stopOnly = type === 'stop' || type === 'trailing';
    body = <>
      <p className="small fa">{stopOnly ? L('استاپ یک ماشه است، نه یک قیمت. وقتی فعال شود در قیمت بازار پر می‌شود، نه در عددی که نوشته‌اید — و همین است که بیشترین هزینه را به تازه‌کارها می‌زند.', 'A stop is a trigger, not a price. When it goes it fills wherever the market is, not at the number you wrote — and that is what costs beginners the most.') : L('این سفارش منتظر می‌ماند و در قیمت خودش پر می‌شود، با کارمزد میکر.', 'This order waits and fills at its own price, at the maker fee.')}</p>
      {rest != null && rest > 0 ? <>{row(L('قیمت پر شدن', 'Fill price'), formatPrice(rest, digits))}{row(L('کارمزد', 'Fee'), money(feeFor(resting(rest), qty, rules)))}{row(L('مارجین لازم', 'Margin needed'), money((rest * qty) / Math.max(1, rules.leverage)))}</> : null}
    </>;
  }
  return (
    <div className="pr-preview" data-part="fill-preview">
      <div className="row"><b className="fa">{L('برآورد پر شدن', 'Expected fill')}</b><span className="ws-spacer" /><span className="pp-badge fa">{L('آزمایشی', 'Paper')}</span></div>
      {body}
      <small className="warn fa">{L('اسپرد فرضی بود', 'The spread was assumed')}</small>
    </div>
  );
}

/** Every fill and how it got its price (OLD PaperHistory.FillCard). */
function FillLog({ fills }: { fills: PaperFill[] }) {
  const L = useL();
  return (
    <section className="card" data-part="fills">
      <h3 className="fa">{L('گزارش پر شدن‌ها', 'Fill log')}</h3>
      {fills.slice(0, 100).map((f) => {
        const d = instrumentOf(f.symbol, f.price).digits;
        return (
          <div key={f.id} className="pr-fill">
            <div className="row"><span className="num" dir="ltr">{f.symbol} · {f.side === 'long' ? 'Buy' : 'Sell'} · {f.qty}</span><span className="ws-spacer" /><b className="num">{formatPrice(f.price, d)}</b></div>
            <small className="muted fa">{L(`قیمت آخر در آن لحظه ${formatPrice(f.reference, d)}`, `Last was ${formatPrice(f.reference, d)} at the time`)}</small>
            <div className="rp-metric"><span className="muted fa">{L('هزینه‌ی اسپرد', 'Spread cost')}</span><span className="num">{money(f.spreadCost)}</span></div>
            <div className="rp-metric"><span className="muted fa">{L('لغزش', 'Slippage')}</span><span className="num">{money(f.slippage)}</span></div>
            <div className="rp-metric"><span className="muted fa">{L('کارمزد', 'Fee')}</span><span className="num">{money(f.fee)}</span></div>
            <small className="fa" data-basis={f.basis}>{f.basis === 'taken' ? L('از بازار گرفته شد', 'Took the market') : L('در قیمت خودش پر شد', 'Filled at its own price')}</small>
            {!f.watched ? <small className="warn fa" data-unwatched="1">{L('گذر قیمت از این سطح دیده نشد', 'Nothing watched the price cross this level')}</small> : null}
            {f.assumedSpread ? <small className="warn fa">{L('اسپرد فرضی بود', 'The spread was assumed')}</small> : null}
            <small className="muted num">{dateTime(f.at)}</small>
          </div>
        );
      })}
    </section>
  );
}

/** What the account has actually done (OLD PaperRecordTab): the record, by symbol, by month. */
function Record() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const history = usePaper((s) => s.history);
  const r: PaperRecord = useMemo(() => paperRecord(history), [history]);
  if (!r.trades) return <div className="empty fa">{L('تا معامله‌ای بسته نشود کارنامه‌ای نیست. سود موقعیت باز آماری نمی‌سازد.', "There is no record until a trade closes. An open position's profit is not a statistic.")}</div>;
  const ratio = (v: number | null) => (v == null ? '—' : v.toFixed(2));
  const signed = (v: number | null) => (v == null ? '—' : `${v > 0 ? '+' : ''}${money(v)}`);
  const tone = (v: number | null) => (v == null || v === 0 ? '' : v > 0 ? 'up' : 'down');
  const row = (k: string, v: string, t = '') => <div className="rp-metric" key={k}><span className="muted fa">{k}</span><b className={`num ${t}`} dir="ltr">{v}</b></div>;
  const widest = Math.max(...r.byMonth.map((m) => Math.abs(m.net)), 0) || 1;
  const months = fa ? JALALI_MONTHS : JALALI_MONTHS_EN;
  return (
    <div className="pr-record">
      <section className="card" data-part="record">
        <div className="row"><b className="fa">{L(`${faNum(r.trades)} معامله‌ی بسته‌شده`, `${r.trades} closed trades`)}</b><span className="ws-spacer" /><span className="pp-badge fa">{L('آزمایشی', 'Paper')}</span></div>
        {row(L('سود خالص', 'Net'), signed(r.net), tone(r.net))}
        {row(L('نرخ برد', 'Win rate'), r.winRate == null ? '—' : `${r.winRate.toFixed(2)}%`)}
        {row(L('ضریب سود', 'Profit factor'), ratio(r.profitFactor))}
        {row(L('امید ریاضی', 'Expectancy'), signed(r.expectancy), tone(r.expectancy))}
        {row(L('برد به باخت', 'Win to loss'), ratio(r.payoff))}
        {row(L('میانگین برد', 'Average win'), r.averageWin == null ? '—' : money(r.averageWin))}
        {row(L('میانگین باخت', 'Average loss'), r.averageLoss == null ? '—' : money(r.averageLoss))}
        {row(L('بهترین', 'Best'), signed(r.best))}
        {row(L('بدترین', 'Worst'), signed(r.worst))}
        {row(L('بیشترین افت', 'Max drawdown'), `${money(r.maxDrawdown)} · ${ratio(r.maxDrawdownPercent)}`)}
        {row(L('هزینه‌ها', 'Costs'), money(r.costs))}
      </section>
      {r.bySymbol.length ? (
        <section className="card" data-part="by-symbol"><h3 className="fa">{L('به تفکیک نماد', 'By symbol')}</h3>
          {r.bySymbol.map((s) => (
            <div key={s.symbol} className="rp-metric">
              <span><b className="sym" dir="ltr">{s.symbol}</b> <small className="muted fa">{fa ? `${faNum(s.trades)} معامله · نرخ برد ${(s.winRate ?? 0).toFixed(2)}%` : `${s.trades} trades · ${(s.winRate ?? 0).toFixed(2)}% win rate`}</small></span>
              <b className={`num ${tone(s.net)}`} dir="ltr">{signed(s.net)}</b>
            </div>
          ))}
        </section>
      ) : null}
      {r.byMonth.length ? (
        <section className="card" data-part="by-month"><h3 className="fa">{L('به تفکیک ماه', 'By month')}</h3>
          {r.byMonth.map((m) => (
            <div key={`${m.year}-${m.month}`} className="pr-month">
              <span className="fa small">{months[m.month - 1]} <span className="num muted">{m.year}</span></span>
              <span><span className={`bar ${tone(m.net) || 'flat'}`} style={{ display: 'block', width: `${Math.min(100, Math.max(2, (Math.abs(m.net) / widest) * 100))}%` }} /></span>
              <span className={`num small ${tone(m.net)}`} dir="ltr">{signed(m.net)}</span>
            </div>
          ))}
        </section>
      ) : null}
    </div>
  );
}

/** How a paper order fills, with the reader's own numbers in it (OLD PaperAssumptions.PaperRulesCard). */
function RulesCard({ rules }: { rules: PaperRules }) {
  const L = useL();
  const p = (v: number) => `${v.toFixed(2)}%`;
  return (
    <section className="card pr-rules" data-part="fill-rules">
      <h3 className="fa">{L('قانون‌های پر شدن', 'How a paper order fills')}</h3>
      <p className="fa">{L('اینجا هیچ سفارشی در قیمت میانه پر نمی‌شود. هر عددی که می‌بینید از یکی از این قانون‌ها می‌آید.', 'Nothing here fills at the mid price. Every number you see comes from one of these rules.')}</p>
      <p className="muted small fa">{L(`خرید در قیمت فروشنده پر می‌شود و فروش در قیمت خریدار. اگر فید مظنه ندهد، ${p(rules.spreadPercent)} اسپرد فرضی دو طرف قیمت آخر باز می‌شود و روی همان پر شدن نوشته می‌شود که فرضی بوده.`, `A buy pays the ask and a sell hits the bid. Where the feed sends no book, an assumed spread of ${p(rules.spreadPercent)} is widened around last and the fill says it was assumed.`)}</p>
      <p className="muted small fa">{L(`سفارش بازار و استاپِ فعال‌شده ${p(rules.slippagePercent)} لغزش هم می‌دهند. سفارشی که منتظر مانده هرگز لغزش نمی‌دهد.`, `Market orders and triggered stops also pay ${p(rules.slippagePercent)} slippage. An order that rested never slips.`)}</p>
      <p className="muted small fa">{L('سفارش منتظر در قیمت خودش پر می‌شود و کارمزد میکر می‌دهد؛ سفارشی که از بازار می‌گیرد کارمزد تیکر.', 'A resting order fills at its own price and pays the maker fee; one that takes the market pays the taker fee.')}</p>
      <p className="muted small fa">{L('استاپ یک ماشه است، نه یک قیمت. وقتی فعال شود در قیمت بازار پر می‌شود، نه در عددی که نوشته‌اید — و همین است که بیشترین هزینه را به تازه‌کارها می‌زند.', 'A stop is a trigger, not a price. When it goes it fills wherever the market is, not at the number you wrote — and that is what costs beginners the most.')}</p>
      <p className="muted small fa">{L('وقتی برنامه بسته بوده، هیچ‌کس گذر قیمت را ندیده. آن‌وقت بدترین قیمت ممکن حساب می‌شود: سفارش حد در قیمت خودش و استاپ در قیمت بازار. روی آن پر شدن هم نوشته می‌شود که دیده نشده.', 'While the app is shut nobody watches the price cross. Then the worse of the two candidate prices is used: a limit gets its own limit, a stop gets the market — and the fill says it was unwatched.')}</p>
      <p className="warn small fa">{L('اینجا سفارش منتظر با یک بار لمس قیمت پر می‌شود. در بازار واقعی صف دارد و ممکن است قیمت بیاید و برود بی‌آنکه شما پر شوید. این خوش‌بینانه است.', 'Here a resting order fills on touch. A real one joins a queue and may watch the price arrive and leave without it. This is optimistic.')}</p>
      <p className="warn small fa">{L('برنامه قیمت آخر را می‌بیند، نه کندل را. استاپی که فقط با یک سایه لمس می‌شد، بین دو قیمت دیده‌شده نمی‌خورد.', 'The app sees a last price, not a candle. A stop that only a wick reached is not hit between two observed prices.')}</p>
    </section>
  );
}
