import { useEffect, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { useMarket } from '../state/market';
import { marginOf, pnlOf, usePaper, type OrderType, type Side } from '../state/paper';
import { useQuote } from '../data/quote';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash } from '../ui/useFlash';
import { usePage } from '../state/page';
import { useL } from '../pages/kit';
import { submitOrder } from './orderTicket';
import './panels-x.css';

// The order ticket on the right (TradingView's Order panel, the old Pro-Chart's OrderTicket), on
// the paper account (state/paper.ts): buy or sell; market, limit, stop, stop-limit or trailing;
// time in force; the quantity with a stepper or as 25/50/75/100% of what the balance can carry;
// leverage; a bracket (take profit and stop loss, one cancels the other) or reduce-only; each
// level's distance in pips and per cent; the cost, margin, R:R, risk and reward, the fee and the
// estimated liquidation; what is wrong before it is sent; and a button that says what it will do.
const TYPES: OrderType[] = ['market', 'limit', 'stop', 'stop_limit', 'trailing'];
const TIFS = ['GTC', 'IOC', 'FOK'] as const;
const PCTS = [25, 50, 75, 100];
const LEVS = [1, 5, 10, 20, 50, 100];
const n = (s: string) => { const v = Number(String(s).replace(/,/g, '')); return Number.isFinite(v) ? v : 0; };

export function OrderPanel() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const quote = useQuote(symbol);
  const paper = usePaper();
  const inst = instrumentOf(symbol, tk?.last);
  const digits = inst.digits;
  const { ref, tick } = useFlash<HTMLSpanElement>(tk?.last);
  const [side, setSide] = useState<Side>('long');
  const [type, setType] = useState<OrderType>('market');
  const [tif, setTif] = useState<(typeof TIFS)[number]>('GTC');
  const [qty, setQty] = useState('');
  const [pct, setPct] = useState(0);
  const [limitPx, setLimitPx] = useState('');
  const [stopPx, setStopPx] = useState('');
  const [trail, setTrail] = useState('0.5');
  const [bracket, setBracket] = useState(true);
  const [reduce, setReduce] = useState(false);
  const [tp, setTp] = useState('');
  const [sl, setSl] = useState('');
  const [done, setDone] = useState<{ ok: boolean; text: string } | null>(null);
  const lev = paper.rules.leverage;

  // A new symbol starts the prices from its own market.
  useEffect(() => { setLimitPx(''); setStopPx(''); setTp(''); setSl(''); setQty(''); setPct(0); setDone(null); }, [symbol]);
  useEffect(() => {
    const p = tk?.last;
    if (!p) return;
    if ((type === 'limit' || type === 'stop_limit') && !limitPx) setLimitPx(p.toFixed(digits));
    if ((type === 'stop' || type === 'stop_limit') && !stopPx) setStopPx((side === 'long' ? p * 1.002 : p * 0.998).toFixed(digits));
  }, [type]); // eslint-disable-line react-hooks/exhaustive-deps

  const mkt = side === 'long' ? quote?.ask ?? tk?.last ?? 0 : quote?.bid ?? tk?.last ?? 0;
  const isLimit = type === 'limit' || type === 'stop_limit';
  const isStop = type === 'stop' || type === 'stop_limit';
  const px = type === 'limit' ? n(limitPx) || mkt : type === 'stop' ? n(stopPx) || mkt : type === 'stop_limit' ? n(limitPx) || n(stopPx) || mkt : mkt;
  const used = paper.positions.reduce((s, p) => s + marginOf(p), 0);
  const avail = Math.max(0, paper.balance - used);
  const amount = n(qty);
  const cost = amount * px;
  const margin = cost / Math.max(1, lev);
  const maker = isLimit;
  const fee = cost * ((maker ? paper.rules.makerFeePercent : paper.rules.takerFeePercent) / 100);
  const tpV = n(tp), slV = n(sl);
  const risk = slV ? Math.abs(px - slV) * amount : 0;
  const reward = tpV ? Math.abs(tpV - px) * amount : 0;
  const rr = risk && reward ? reward / risk : null;
  const liq = px && lev > 1 ? (side === 'long' ? px * (1 - 1 / lev) : px * (1 + 1 / lev)) : null;
  const pip = inst.market === 'forex' ? (symbol.includes('JPY') ? 0.01 : 0.0001) : null;
  const dist = (v: number) => { if (!(v > 0) || !(px > 0)) return ''; const d = Math.abs(v - px); return `${pip ? `${(d / pip).toFixed(1)} pips · ` : ''}${((d / px) * 100).toFixed(2)}%`; };
  const opposite = paper.positions.filter((p) => p.symbol === symbol && p.side !== side);
  const oppositeQty = opposite.reduce((s, p) => s + p.qty, 0);
  const base = inst.market === 'crypto' ? inst.base : symbol;
  const qtyDigits = inst.market === 'crypto' ? (px >= 1000 ? 4 : px >= 1 ? 2 : 0) : 2;

  const errs: string[] = [];
  const warns: string[] = [];
  if (!(amount > 0)) errs.push(L('مقدار را وارد کنید.', 'Enter the quantity.'));
  if (isLimit && !(n(limitPx) > 0)) errs.push(L('قیمت محدود لازم است.', 'A limit price is needed.'));
  if (isStop && !(n(stopPx) > 0)) errs.push(L('قیمت ماشه لازم است.', 'A trigger price is needed.'));
  if (type === 'trailing' && !(n(trail) > 0)) errs.push(L('فاصله‌ی دنباله لازم است.', 'A trail distance is needed.'));
  if (!reduce && amount > 0 && margin + fee > avail) errs.push(L('مارجین لازم از موجودی بیشتر است.', 'The margin exceeds the available balance.'));
  if (reduce && !oppositeQty) errs.push(L('موقعیت مخالفی برای کاهش نیست.', 'There is no opposite position to reduce.'));
  if (reduce && type !== 'market') errs.push(L('فقط‌کاهش با سفارش بازار.', 'Reduce-only works with market orders.'));
  if (isStop && n(stopPx) > 0 && mkt > 0) {
    if (side === 'long' && n(stopPx) < mkt) warns.push(L('ماشه‌ی خرید معمولاً بالای قیمت است.', 'A buy stop is usually above the price.'));
    if (side === 'short' && n(stopPx) > mkt) warns.push(L('ماشه‌ی فروش معمولاً زیر قیمت است.', 'A sell stop is usually below the price.'));
  }
  if (bracket && slV > 0 && (side === 'long' ? slV >= px : slV <= px)) errs.push(side === 'long' ? L('حد ضرر خرید باید زیر ورود باشد.', 'For a buy the stop loss must be below the entry.') : L('حد ضرر فروش باید بالای ورود باشد.', 'For a sell the stop loss must be above the entry.'));
  if (bracket && tpV > 0 && (side === 'long' ? tpV <= px : tpV >= px)) errs.push(side === 'long' ? L('حد سود خرید باید بالای ورود باشد.', 'For a buy the take profit must be above the entry.') : L('حد سود فروش باید زیر ورود باشد.', 'For a sell the take profit must be below the entry.'));
  if (type === 'limit' && (tif === 'IOC' || tif === 'FOK') && n(limitPx) > 0 && mkt > 0 && (side === 'long' ? n(limitPx) < mkt : n(limitPx) > mkt)) warns.push(L('این سفارش IOC/FOK الان پر نمی‌شود و لغو خواهد شد.', 'This IOC/FOK order would not fill now and will be cancelled.'));

  const typeName = (x: OrderType) => ({ market: L('بازار', 'Market'), limit: L('محدود', 'Limit'), stop: L('توقف', 'Stop'), stop_limit: L('توقف-محدود', 'Stop limit'), trailing: L('دنباله‌رو', 'Trailing') }[x]);
  const label = `${side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')}${amount > 0 ? ` ${amount}` : ''} ${base} @ ${type === 'market' ? typeName('market') : (isLimit ? n(limitPx) : n(stopPx)) > 0 ? `${formatPrice(isLimit ? n(limitPx) : n(stopPx), digits)} ${typeName(type)}` : typeName(type)}`;

  const applyPct = (p: number) => {
    setPct(p);
    const can = reduce ? oppositeQty * (p / 100) : ((avail * lev) / (px || 1)) * (p / 100) / (1 + (paper.rules.takerFeePercent / 100) * lev);
    setQty(can > 0 ? (Math.floor(can * 10 ** qtyDigits) / 10 ** qtyDigits).toString() : '');
  };
  const step = (dir: 1 | -1) => {
    const cur = amount;
    const s = cur >= 100 ? 1 : cur >= 10 ? 0.1 : cur >= 1 ? 0.01 : inst.market === 'crypto' ? 0.001 : 0.01;
    setQty(String(+Math.max(0, cur + dir * s).toFixed(4))); setPct(0);
  };

  const submit = () => {
    setDone(null);
    if (errs.length || !tk) return;
    const o = { symbol, side, type, qty: amount, sl: bracket && slV > 0 ? slV : undefined, tp: bracket && tpV > 0 ? tpV : undefined };
    if (reduce) {
      // Reduce-only: close the opposite paper positions, oldest first, as far as the quantity goes.
      let left = amount; let closed = 0;
      for (const p of [...opposite].reverse()) { if (left + 1e-12 < p.qty) break; paper.close(p.id, tk.last); left -= p.qty; closed++; }
      setDone(closed ? { ok: true, text: L(`${closed} موقعیت بسته شد.`, `${closed} position(s) closed.`) } : { ok: false, text: L('مقدار کمتر از اندازه‌ی موقعیت است؛ بستن کامل لازم است.', 'The quantity is below the position size; a paper position closes whole.') });
      return;
    }
    if ((tif === 'IOC' || tif === 'FOK') && type === 'limit' && (side === 'long' ? n(limitPx) < tk.last : n(limitPx) > tk.last)) { setDone({ ok: false, text: L('IOC/FOK: پر نشد و لغو شد.', 'IOC/FOK: not filled, cancelled.') }); return; }
    const r = submitOrder({ ...o, last: tk.last, limitPrice: isLimit ? n(limitPx) : undefined, stopPrice: isStop ? n(stopPx) : undefined, trailPercent: type === 'trailing' ? n(trail) : undefined });
    if (r.live) { setDone({ ok: true, text: L('سفارش به معامله‌ی زنده سپرده شد.', 'Handed to live trading.') }); return; }
    if (r.id && (tif === 'IOC' || tif === 'FOK') && type === 'limit') paper.mark(symbol, tk.last);
    setDone(r.id ? { ok: true, text: type === 'market' ? L('سفارش پر شد (آزمایشی).', 'Filled (paper).') : L('سفارش ثبت شد (آزمایشی).', 'Order placed (paper).') } : { ok: false, text: r.error === 'margin' ? L('موجودی کافی نیست.', 'Not enough balance.') : L('سفارش ثبت نشد.', 'The order was not placed.') });
  };

  const mine = paper.positions.filter((p) => p.symbol === symbol);
  const pending = paper.orders.filter((o) => o.symbol === symbol);
  const money = (v: number) => `${v.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;

  return (
    <section className="panel-generic order-panel">
      <header className="panel-head">
        <SymbolLogo symbol={symbol} size={18} /><span className="sym op-sym">{symbol}</span>
        <span ref={ref} className={`num op-last ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tk ? formatPrice(tk.last, digits) : '—'}</span>
        <span className="ws-spacer" />
        <span className="badge fa">{L('حساب آزمایشی', 'Paper')}</span>
        <button className="lt" title={L('صفحه‌ی معامله‌ی آزمایشی', 'Paper trading page')} onClick={() => usePage.getState().open('paper')}><Icon name="ph-arrow-square-out" size="sm" /></button>
      </header>
      <div className="panel-body op-body">
        <div className="op-sides">
          <button className={`op-side sell${side === 'short' ? ' on' : ''}`} onClick={() => setSide('short')}><span className="fa">{L('فروش', 'Sell')}</span><b className="num">{quote ? formatPrice(quote.bid, digits) : tk ? formatPrice(tk.last, digits) : '—'}</b></button>
          <span className="op-spread num">{quote ? formatPrice(quote.spread, digits) : ''}</span>
          <button className={`op-side buy${side === 'long' ? ' on' : ''}`} onClick={() => setSide('long')}><span className="fa">{L('خرید', 'Buy')}</span><b className="num">{quote ? formatPrice(quote.ask, digits) : tk ? formatPrice(tk.last, digits) : '—'}</b></button>
        </div>
        <div className="op-types" role="tablist">
          {TYPES.map((x) => <button key={x} role="tab" aria-selected={type === x} className={type === x ? 'on' : ''} onClick={() => setType(x)}><span className="fa">{typeName(x)}</span></button>)}
        </div>
        {isStop ? <Field label={L('قیمت ماشه', 'Trigger price')}><input className="num" inputMode="decimal" value={stopPx} onChange={(e) => setStopPx(e.target.value)} /></Field> : null}
        {isLimit ? <Field label={L('قیمت محدود', 'Limit price')}><input className="num" inputMode="decimal" value={limitPx} onChange={(e) => setLimitPx(e.target.value)} /></Field> : null}
        {type === 'trailing' ? (
          <Field label={L('فاصله‌ی دنباله', 'Trail distance')} hint={tk && n(trail) > 0 ? `${L('ماشه', 'trigger')} ≈ ${formatPrice(side === 'long' ? tk.last * (1 + n(trail) / 100) : tk.last * (1 - n(trail) / 100), digits)}` : undefined}>
            <input className="num" inputMode="decimal" value={trail} onChange={(e) => setTrail(e.target.value)} /><span className="op-unit">%</span>
          </Field>
        ) : null}
        {isLimit ? (
          <div className="op-row"><span className="op-label fa">{L('اعتبار', 'Time in force')}</span><span className="seg sm">{TIFS.map((f) => <button key={f} className={tif === f ? 'on' : ''} onClick={() => setTif(f)}><span className="num">{f}</span></button>)}</span></div>
        ) : null}
        <Field label={L('مقدار', 'Quantity')} hint={`≈ ${money(cost)} ${inst.quote}`}>
          <button className="op-step" onClick={() => step(-1)} aria-label={L('کم', 'Less')}>−</button>
          <input className="num c" inputMode="decimal" value={qty} onChange={(e) => { setQty(e.target.value); setPct(0); }} placeholder="0" />
          <span className="op-unit">{base}</span>
          <button className="op-step" onClick={() => step(1)} aria-label={L('زیاد', 'More')}>+</button>
        </Field>
        <div className="op-pcts">{PCTS.map((p) => <button key={p} className={pct === p ? 'on' : ''} onClick={() => applyPct(p)}><span className="num">{p}%</span></button>)}</div>
        <div className="op-lev">
          <span className="fa">{L('اهرم', 'Leverage')}</span>
          <input type="range" min={1} max={125} value={lev} onChange={(e) => paper.setRules({ leverage: Number(e.target.value) })} aria-label={L('اهرم', 'Leverage')} />
          <b className="num">{lev}×</b>
        </div>
        <div className="op-levs">{LEVS.map((x) => <button key={x} className={lev === x ? 'on' : ''} onClick={() => paper.setRules({ leverage: x })}><span className="num">{x}×</span></button>)}</div>
        <div className="op-row">
          <label className="op-check fa"><input type="checkbox" checked={bracket} onChange={(e) => setBracket(e.target.checked)} /><span>{L('براکت (OCO): حد سود و ضرر', 'Bracket (OCO): TP and SL')}</span></label>
          <label className="op-check fa"><input type="checkbox" checked={reduce} onChange={(e) => setReduce(e.target.checked)} /><span>{L('فقط کاهش', 'Reduce-only')}</span></label>
        </div>
        {bracket ? (
          <div className="op-bracket">
            <Field label={L('حد سود', 'Take profit')} tone="up" hint={dist(tpV) || undefined}><input className="num" inputMode="decimal" value={tp} onChange={(e) => setTp(e.target.value)} placeholder={px ? formatPrice(side === 'long' ? px * 1.02 : px * 0.98, digits).replace(/,/g, '') : ''} /></Field>
            <Field label={L('حد ضرر', 'Stop loss')} tone="down" hint={dist(slV) || undefined}><input className="num" inputMode="decimal" value={sl} onChange={(e) => setSl(e.target.value)} placeholder={px ? formatPrice(side === 'long' ? px * 0.99 : px * 1.01, digits).replace(/,/g, '') : ''} /></Field>
          </div>
        ) : null}
        <dl className="op-sum">
          <dt className="fa">{L('قیمت ورود', 'Entry')}</dt><dd className="num">{px ? formatPrice(px, digits) : '—'}</dd>
          {isStop ? <><dt className="fa">{L('ماشه', 'Trigger')}</dt><dd className="num">{n(stopPx) ? formatPrice(n(stopPx), digits) : '—'}</dd></> : null}
          <dt className="fa">{L('ارزش سفارش', 'Order value')}</dt><dd className="num">{money(cost)} {inst.quote}</dd>
          <dt className="fa">{L('مارجین', 'Margin')}</dt><dd className="num">{money(margin)}</dd>
          <dt className="fa">R:R</dt><dd className={`num ${rr != null ? (rr >= 1 ? 'up' : 'warn') : ''}`}>{rr != null ? rr.toFixed(2) : '—'}</dd>
          {risk || reward ? <><dt className="fa">{L('ریسک / سود', 'Risk / reward')}</dt><dd className="num"><span className="down">−{money(risk)}</span> / <span className="up">+{money(reward)}</span></dd></> : null}
          <dt className="fa">{L('کارمزد', 'Fee')} <span className="muted">({maker ? 'maker' : 'taker'})</span></dt><dd className="num">{money(fee)}</dd>
          {liq ? <><dt className="fa">{L('لیکویید تخمینی', 'Est. liquidation')}</dt><dd className="num warn">{formatPrice(liq, digits)}</dd></> : null}
          <dt className="fa">{L('در دسترس', 'Available')}</dt><dd className="num">{money(avail)}</dd>
        </dl>
        {amount > 0 && (errs.length || warns.length) ? (
          <div className={`op-msgs${errs.length ? ' err' : ''}`}>
            {errs.map((m) => <div key={m} className="down fa"><Icon name="ph-warning" size="sm" />{m}</div>)}
            {warns.map((m) => <div key={m} className="warn fa"><Icon name="ph-warning" size="sm" />{m}</div>)}
          </div>
        ) : null}
        <button className={`btn op-go ${side === 'long' ? 'buy' : 'sell'}`} disabled={Boolean(errs.length) || !tk} onClick={submit}><span className="fa">{label}</span></button>
        {done ? <div className={`op-done fa ${done.ok ? 'up' : 'down'}`}>{done.text}</div> : null}
        {mine.length || pending.length ? (
          <div className="op-book">
            {mine.map((p) => (
              <div key={p.id} className="op-pos num">
                <span className={p.side === 'long' ? 'up' : 'down'}>{p.side === 'long' ? L('خرید', 'Long') : L('فروش', 'Short')}</span><span>{p.qty}</span><span className="muted">@</span><span>{formatPrice(p.entry, digits)}</span>
                {tk ? <span className={pnlOf(p, tk.last) >= 0 ? 'up' : 'down'}>{pnlOf(p, tk.last) >= 0 ? '+' : '−'}{money(Math.abs(pnlOf(p, tk.last)))}</span> : null}
                <span className="ws-spacer" />
                <button className="lt" title={L('بستن', 'Close')} onClick={() => tk && paper.close(p.id, tk.last)}><Icon name="ph-x" size="sm" /></button>
              </div>
            ))}
            {pending.map((o) => (
              <div key={o.id} className="op-pos num">
                <span className={o.side === 'long' ? 'up' : 'down'}>{o.side === 'long' ? L('خرید', 'Buy') : L('فروش', 'Sell')}</span><span>{o.qty}</span><span className="muted">{o.type}</span><span>{formatPrice(o.limitPrice ?? o.stopPrice ?? 0, digits)}</span>
                <span className="ws-spacer" />
                <button className="lt" title={L('لغو', 'Cancel')} onClick={() => paper.cancel(o.id)}><Icon name="ph-x" size="sm" /></button>
              </div>
            ))}
          </div>
        ) : null}
        <p className="muted small fa op-foot">{fa ? 'سفارش‌ها روی حساب آزمایشی همین مرورگر اجرا می‌شوند؛ پولی جابه‌جا نمی‌شود.' : 'Orders run on this browser’s paper account; no money moves.'}</p>
      </div>
    </section>
  );
}

function Field({ label, hint, tone, children }: { label: string; hint?: string; tone?: 'up' | 'down'; children: React.ReactNode }) {
  return (
    <label className="op-field">
      <span className="op-label"><span className={`fa ${tone ?? ''}`}>{label}</span>{hint ? <span className={`num muted ${tone ?? ''}`}>{hint}</span> : null}</span>
      <span className="op-box">{children}</span>
    </label>
  );
}

