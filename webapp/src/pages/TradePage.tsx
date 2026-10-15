import { useEffect, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useMarket } from '../state/market';
import { uuid } from '../data/http';
import { ty, pub, num, str } from '../data/member';
import { useSession } from '../data/session';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolSearch } from '../shell/SymbolSearch';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Field, State, Tabs, errorText, money, parseNum, useL, useLoad } from './kit';

// Live trading on the connected exchange (core/execution LiveTrade.kt, TradeYar only): the account,
// an order ticket that always asks for an explicit confirmation, open positions with protection,
// close and leverage, working orders with amend and cancel, and the order book (core/orderbook:
// the member depth first, the public depth when that is refused).
const P = 'api/mobile/v1/trade';
interface Pos { symbol: string; side: 'long' | 'short'; quantity: number; entry: number | null; mark: number | null; upnl: number | null; leverage: number | null; liq: number | null }
interface Ord { id: number; symbol: string; side: string; type: string; quantity: number | null; price: number | null; status: string; filled: string; canCancel: boolean; canAmend: boolean; error: string; protection: string }

const readPos = (x: Record<string, unknown>): Pos => ({ symbol: str(x.symbol), side: str(x.side) === 'short' ? 'short' : 'long', quantity: num(x.quantity) ?? 0, entry: num(x.entry_price), mark: num(x.mark_price), upnl: num(x.unrealized_pnl), leverage: num(x.leverage), liq: num(x.liquidation_price) });
const readOrd = (x: Record<string, unknown>): Ord => ({ id: Number(x.id), symbol: str(x.symbol), side: str(x.side), type: str(x.type), quantity: num(x.quantity), price: num(x.price), status: str(x.status), filled: str(x.filled_quantity), canCancel: Boolean(x.can_cancel), canAmend: Boolean(x.can_amend), error: str(x.error_message), protection: str(x.protection) });

export default function TradePage() {
  const L = useL();
  const t = useT();
  const chart = usePrefs((s) => s.symbol);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const [symbol, setSymbol] = useState(chart.endsWith('USDT') ? chart : 'BTCUSDT');
  const [tab, setTab] = useState<'positions' | 'orders' | 'book'>('positions');
  const [pick, setPick] = useState(false);
  const [n, setN] = useState(0);
  const refresh = () => setN((x) => x + 1);
  const account = useLoad(() => ty<Record<string, unknown>>(`${P}/account`), [n]);
  const positions = useLoad(() => ty<{ items?: Record<string, unknown>[] }>(`${P}/positions`).then((r) => (r.items ?? []).map(readPos)), [n]);
  const orders = useLoad(() => ty<{ items?: Record<string, unknown>[] }>(`${P}/orders?limit=50`).then((r) => (r.items ?? []).map(readOrd)), [n]);
  useEffect(() => { const id = setInterval(refresh, 15_000); return () => clearInterval(id); }, []);

  if (!signed) return <div className="empty fa">{L('معامله‌ی زنده برای رمزارز و با حساب تریدیار است؛ وارد شوید.', 'Live trading is for crypto on a TradeYar account; sign in.')}</div>;
  const a = account.data;
  const enabled = a ? a.trading_enabled !== false : false;

  return (
    <div className="page-inner trade-page">
      {pick ? <SymbolSearch onClose={() => setPick(false)} onPick={(s) => { if (s.endsWith('USDT')) setSymbol(s); setPick(false); }} /> : null}
      <State loading={account.loading && !a} error={account.error} onRetry={account.reload}>
        <div className="pulse">
          <div className="card"><span className="muted fa">{L('در دسترس', 'Available')}</span><b className="num">{money(num(a?.available))}</b></div>
          <div className="card"><span className="muted fa">{L('ارزش حساب', 'Equity')}</span><b className="num">{money(num(a?.equity))}</b></div>
          <div className="card"><span className="muted fa">{L('مارجین', 'Margin used')}</span><b className="num">{money(num(a?.margin_used))}</b></div>
          <div className="card"><span className="muted fa">{L('سود و زیان باز', 'Unrealized P&L')}</span><b className={`num ${(num(a?.unrealized_pnl) ?? 0) >= 0 ? 'up' : 'down'}`}>{money(num(a?.unrealized_pnl))}</b></div>
        </div>
        {!enabled ? <p className="card warn fa">{L('معامله روی این حساب فعال نیست. اتصال صرافی را در «اتصال حساب» بررسی کنید.', 'Trading is not enabled on this account. Check the exchange in Connections.')}</p> : null}
      </State>
      <div className="split">
        <Ticket symbol={symbol} onPick={() => setPick(true)} enabled={enabled} onDone={refresh} />
        <div className="grow">
          <Tabs value={tab} onChange={setTab} tabs={[{ id: 'positions', label: L('موقعیت‌ها', 'Positions') }, { id: 'orders', label: L('سفارش‌ها', 'Orders') }, { id: 'book', label: L('دفتر سفارش', 'Order book') }]} />
          {tab === 'positions' ? (
            <State loading={positions.loading && !positions.data} error={positions.error} onRetry={positions.reload} empty={!positions.data?.length}>
              <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('مارک', 'Mark')}</th><th className="r">{L('لیکوئید', 'Liq.')}</th><th className="r">P&amp;L</th><th /></tr></thead>
                <tbody>{(positions.data ?? []).map((p) => <PositionRow key={p.symbol + p.side} p={p} onDone={refresh} />)}</tbody></table>
            </State>
          ) : null}
          {tab === 'orders' ? (
            <State loading={orders.loading && !orders.data} error={orders.error} onRetry={orders.reload} empty={!orders.data?.length}>
              <table className="table"><thead><tr><th>{L('نماد', 'Symbol')}</th><th>{L('نوع', 'Type')}</th><th>{L('جهت', 'Side')}</th><th className="r">{L('مقدار', 'Qty')}</th><th className="r">{L('قیمت', 'Price')}</th><th>{L('وضعیت', 'Status')}</th><th /></tr></thead>
                <tbody>{(orders.data ?? []).map((o) => (
                  <tr key={o.id}><td className="sym">{o.symbol}</td><td>{o.type}</td><td className={o.side === 'buy' ? 'up' : 'down'}>{o.side}</td><td className="r num">{o.quantity ?? '—'}{o.filled ? ` (${o.filled})` : ''}</td><td className="r num">{o.price ?? L('بازار', 'market')}</td>
                    <td title={o.error}>{o.status}{o.protection && o.protection !== 'none' ? ` · SL/TP ${o.protection}` : ''}</td>
                    <td className="r">
                      {o.canAmend ? <button className="btn btn-quiet sm" onClick={async () => { const v = parseNum(window.prompt(L('قیمت تازه', 'New price'), String(o.price ?? '')) ?? ''); if (v) { await ty(`${P}/orders/${o.id}`, { method: 'PATCH', body: { price: v } }).catch((e) => window.alert(errorText(e, t))); refresh(); } }}>{L('ویرایش', 'Amend')}</button> : null}
                      {o.canCancel ? <button className="btn btn-quiet sm" onClick={async () => { await ty(`${P}/orders/${o.id}`, { method: 'DELETE' }).catch((e) => window.alert(errorText(e, t))); refresh(); }}>{L('لغو', 'Cancel')}</button> : null}
                    </td></tr>
                ))}</tbody></table>
            </State>
          ) : null}
          {tab === 'book' ? <OrderBook symbol={symbol} /> : null}
        </div>
      </div>
    </div>
  );
}

function Ticket({ symbol, onPick, enabled, onDone }: { symbol: string; onPick(): void; enabled: boolean; onDone(): void }) {
  const L = useL();
  const t = useT();
  const price = useMarket((s) => s.tickers[symbol]?.last ?? null);
  const [side, setSide] = useState<'buy' | 'sell'>('buy');
  const [type, setType] = useState<'market' | 'limit'>('market');
  const [f, setF] = useState({ qty: '', price: '', sl: '', tp: '', lev: '' });
  const [reduce, setReduce] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const digits = instrumentOf(symbol, price ?? undefined).digits;

  const place = async () => {
    const qty = parseNum(f.qty);
    if (!qty) { setMsg(L('مقدار را وارد کنید.', 'Enter the quantity.')); return; }
    const px = type === 'limit' ? parseNum(f.price) : null;
    if (type === 'limit' && !px) { setMsg(L('قیمت محدود را وارد کنید.', 'Enter the limit price.')); return; }
    const sl = parseNum(f.sl); const tp = parseNum(f.tp);
    const summary = `${side.toUpperCase()} ${qty} ${symbol} @ ${px ?? 'MARKET'}${sl ? ` · SL ${sl}` : ''}${tp ? ` · TP ${tp}` : ''}${reduce ? ' · reduce-only' : ''}`;
    if (!window.confirm(`${L('این سفارش واقعی روی صرافی ثبت می‌شود:', 'This places a real order on the exchange:')}\n${summary}`)) return;
    setBusy(true); setMsg(null);
    try {
      const lev = parseNum(f.lev);
      if (lev) await ty(`${P}/leverage`, { body: { symbol, leverage: Math.round(lev) } });
      const r = await ty<{ order?: Record<string, unknown> }>(`${P}/orders`, { body: { client_request_id: uuid(), symbol, side, type, quantity: qty, price: px, reduce_only: reduce, stop_loss: sl, take_profit: tp } });
      setMsg(`${L('ثبت شد', 'Placed')}: ${str(r.order?.status) || 'ok'}`);
      onDone();
    } catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <form className="card form ticket" onSubmit={(e) => { e.preventDefault(); void place(); }}>
      <div className="row"><button type="button" className="btn btn-quiet sm" onClick={onPick}><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span></button><span className="num muted">{price != null ? formatPrice(price, digits) : '—'}</span></div>
      <Tabs value={side} onChange={setSide} tabs={[{ id: 'buy', label: L('خرید', 'Buy') }, { id: 'sell', label: L('فروش', 'Sell') }]} />
      <Tabs value={type} onChange={setType} tabs={[{ id: 'market', label: L('بازار', 'Market') }, { id: 'limit', label: L('محدود', 'Limit') }]} />
      <Field label={L('مقدار', 'Quantity')}><input className="num" dir="ltr" inputMode="decimal" value={f.qty} onChange={set('qty')} /></Field>
      {type === 'limit' ? <Field label={L('قیمت', 'Price')}><input className="num" dir="ltr" inputMode="decimal" value={f.price} onChange={set('price')} /></Field> : null}
      <div className="row two"><Field label={L('حد ضرر', 'Stop loss')}><input className="num" dir="ltr" inputMode="decimal" value={f.sl} onChange={set('sl')} /></Field><Field label={L('حد سود', 'Take profit')}><input className="num" dir="ltr" inputMode="decimal" value={f.tp} onChange={set('tp')} /></Field></div>
      <Field label={L('اهرم (اختیاری)', 'Leverage (optional)')}><input className="num" dir="ltr" inputMode="numeric" value={f.lev} onChange={set('lev')} /></Field>
      <label className="check"><input type="checkbox" checked={reduce} onChange={(e) => setReduce(e.target.checked)} /> <span className="fa">{L('فقط کاهش موقعیت', 'Reduce only')}</span></label>
      {msg ? <p className="fa small">{msg}</p> : null}
      <button className={`btn ${side === 'buy' ? 'buy' : 'sell'}`} disabled={busy || !enabled}>{side === 'buy' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</button>
      <p className="muted small fa">{L('هر سفارش پیش از ارسال تأیید صریح می‌خواهد. نتیجه را فقط صرافی تعیین می‌کند.', 'Every order asks for an explicit confirmation. Only the exchange decides the outcome.')}</p>
    </form>
  );
}

function PositionRow({ p, onDone }: { p: Pos; onDone(): void }) {
  const L = useL();
  const t = useT();
  const d = instrumentOf(p.symbol, p.entry ?? undefined).digits;
  const fmt = (v: number | null) => (v == null ? '—' : formatPrice(v, d));
  const protect = async () => {
    const sl = parseNum(window.prompt(L('حد ضرر (خالی = بدون تغییر)', 'Stop loss (empty = none)')) ?? '');
    const tp = parseNum(window.prompt(L('حد سود (خالی = بدون تغییر)', 'Take profit (empty = none)')) ?? '');
    if (sl == null && tp == null) return;
    await ty(`api/mobile/v1/trade/positions/protection`, { body: { symbol: p.symbol, side: p.side, stop_loss: sl, take_profit: tp } }).catch((e) => window.alert(errorText(e, t)));
    onDone();
  };
  const closePos = async () => {
    const q = window.prompt(L('مقدار بستن (خالی = کل موقعیت)', 'Quantity to close (empty = all)'), '');
    if (q === null) return;
    if (!window.confirm(L('این موقعیت روی صرافی بسته می‌شود.', 'This closes the position on the exchange.'))) return;
    await ty(`api/mobile/v1/trade/positions/close`, { body: { client_request_id: uuid(), symbol: p.symbol, side: p.side, quantity: parseNum(q) } }).catch((e) => window.alert(errorText(e, t)));
    onDone();
  };
  return (
    <tr>
      <td className="sym">{p.symbol}{p.leverage ? <span className="muted num"> x{p.leverage}</span> : null}</td>
      <td className={p.side === 'long' ? 'up' : 'down'}>{p.side === 'long' ? 'Long' : 'Short'}</td>
      <td className="r num">{p.quantity}</td><td className="r num">{fmt(p.entry)}</td><td className="r num">{fmt(p.mark)}</td><td className="r num">{fmt(p.liq)}</td>
      <td className={`r num ${(p.upnl ?? 0) >= 0 ? 'up' : 'down'}`}>{money(p.upnl)}</td>
      <td className="r"><button className="btn btn-quiet sm" onClick={() => void protect()}>SL/TP</button> <button className="btn btn-quiet sm" onClick={() => void closePos()}>{L('بستن', 'Close')}</button></td>
    </tr>
  );
}

/** The order book: member depth, else the public one; bids and asks with cumulative bars. */
export function OrderBook({ symbol }: { symbol: string }) {
  const L = useL();
  const [depth, setDepth] = useState(20);
  const book = useLoad(async () => {
    const q = `symbol=${encodeURIComponent(symbol)}&depth=${depth}`;
    const r = await ty<{ bids?: number[][]; asks?: number[][] }>(`api/mobile/v1/market/depth?${q}`)
      .catch(() => pub<{ bids?: number[][]; asks?: number[][] }>(`/up/tradeyar/api/v1/public/market/depth?${q}`));
    return { bids: (r.bids ?? []).slice(0, depth), asks: (r.asks ?? []).slice(0, depth) };
  }, [symbol, depth]);
  useEffect(() => { const id = setInterval(book.reload, 3000); return () => clearInterval(id); }, [book.reload]);
  const d = instrumentOf(symbol).digits;
  const rows = (list: number[][], side: 'bid' | 'ask') => {
    let cum = 0;
    const withCum = list.map(([p, v]) => { cum += v ?? 0; return { p: p!, v: v ?? 0, cum }; });
    const max = withCum[withCum.length - 1]?.cum || 1;
    return withCum.map((r) => (
      <div key={side + r.p} className={`dom-row ${side}`}><i style={{ width: `${(r.cum / max) * 100}%` }} /><span className="num">{formatPrice(r.p, d)}</span><span className="num">{+r.v.toPrecision(6)}</span><span className="num muted">{+r.cum.toPrecision(6)}</span></div>
    ));
  };
  const best = book.data ? { bid: book.data.bids[0]?.[0], ask: book.data.asks[0]?.[0] } : null;
  return (
    <div className="dom">
      <div className="row"><b className="sym">{symbol}</b><select value={depth} onChange={(e) => setDepth(Number(e.target.value))}>{[10, 20, 50, 100].map((x) => <option key={x} value={x}>{x}</option>)}</select>
        {best?.bid && best.ask ? <span className="muted num">{L('اسپرد', 'Spread')} {formatPrice(best.ask - best.bid, d)} ({(((best.ask - best.bid) / best.ask) * 100).toFixed(3)}%)</span> : null}</div>
      <State loading={book.loading && !book.data} error={book.error} onRetry={book.reload}>
        {book.data ? <div className="dom-cols"><div>{rows([...book.data.asks].reverse().slice(-depth), 'ask')}</div><div>{rows(book.data.bids, 'bid')}</div></div> : null}
      </State>
    </div>
  );
}
