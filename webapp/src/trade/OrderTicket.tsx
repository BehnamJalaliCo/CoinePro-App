// The order ticket, TradingView's order dialog: Sell and Buy with the live bid and ask, market /
// limit / stop, the size in the coin or in USDT with 25–100 % of what is available, leverage,
// take profit and stop loss by price, distance or percent with the money at stake, reduce-only,
// and the order's value, margin, fee, liquidation and reward-to-risk. Live orders always pass a
// confirmation step and carry one `client_request_id` per order, kept across a retry; the
// platform's own refusal is shown word for word. Paper mode runs the same form on the paper book.
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { useMarket, digitsOf } from '../state/market';
import { usePaper, marginOf } from '../state/paper';
import { usePanels } from '../state/panels';
import { useQuote } from '../data/quote';
import { uuid } from '../data/http';
import { formatPrice } from '../chart/format';
import { instrumentOf } from '../data/symbols';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { parseNum, useL } from '../pages/kit';
import { placeOrder, setLeverage } from './api';
import { useTrading } from './store';
import { useTicket, type TicketRequest } from './ticket';
import { LiveNeeds } from './LiveNeeds';
import { notify } from './toast';
import { MAX_LEVERAGE, VENUE_LABEL, VENUE_TYPES, isSignalOnly, liveVenueOf } from './venue';
import { usePage } from '../state/page';
import { checkOrder, distanceUnit, entryPrice, FEE_RATE, levelFrom, pnlAt, qtyDecimals, qtyForPercent, summarize, valueFrom, type LevelMode, type Side, type TicketType } from './math';
import { tradeErrorText, type Order } from './wire';
import './trade.css';

const LEVERAGES = [1, 2, 3, 5, 10, 20, 25, 50, 100];
const QTY_KEY = 'pc.trade.qty.v1';
const readQty = (symbol: string): string => { try { return (JSON.parse(localStorage.getItem(QTY_KEY) ?? '{}') as Record<string, string>)[symbol] ?? ''; } catch { return ''; } };
const writeQty = (symbol: string, q: string) => { try { const all = JSON.parse(localStorage.getItem(QTY_KEY) ?? '{}') as Record<string, string>; all[symbol] = q; localStorage.setItem(QTY_KEY, JSON.stringify(all)); } catch { /* storage off */ } };

export const ORDER_STATUS: Record<string, [string, string]> = {
  filled: ['پر شد', 'Filled'], open: ['در انتظار اجرا', 'Working'], partial: ['بخشی پر شد', 'Partly filled'], submitted: ['فرستاده شد', 'Submitted'],
  queued: ['در صف', 'Queued'], rejected: ['رد شد', 'Rejected'], failed: ['ناموفق', 'Failed'], cancelled: ['لغو شد', 'Cancelled'], unknown: ['نامشخص', 'Unknown'],
};

/** Mounted once; shows the ticket the last `pc:order-ticket` asked for. */
export function OrderTicketHost({ onSignIn }: { onSignIn(): void }) {
  const req = useTicket((s) => s.req);
  if (!req) return null;
  return <OrderTicket key={req.at} req={req} onSignIn={onSignIn} />;
}

type Step = 'form' | 'confirm' | 'sending' | 'done' | 'error';

function OrderTicket({ req, onSignIn }: { req: TicketRequest; onSignIn(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const close = useTicket((s) => s.close);
  const symbol = req.symbol;
  const venue = liveVenueOf(symbol);
  const signalOnly = isSignalOnly(symbol);
  const book = useTrading((s) => (venue ? s.books[venue] : null));
  const setMode = useTrading((s) => s.setMode);
  const knownLev = useTrading((s) => s.leverage[symbol] ?? null);
  const tk = useMarket((s) => s.tickers[symbol]);
  const quote = useQuote(symbol);
  const paper = usePaper();
  const [mode, setModeLocal] = useState<'live' | 'paper'>(signalOnly ? 'paper' : req.mode);
  const live = mode === 'live';
  const ready = live && book?.status === 'live';
  const digits = digitsOf(symbol);
  const inst = instrumentOf(symbol, tk?.last);
  const last = tk?.last ?? null;

  const [side, setSide] = useState<Side>(req.side);
  const [type, setType] = useState<TicketType>(req.type ?? (req.price != null && req.source && req.source !== 'legend' && req.source !== 'panel' ? 'limit' : 'market'));
  const [priceText, setPriceText] = useState(req.price != null && (req.type ?? 'limit') !== 'market' ? String(+req.price.toFixed(digits)) : '');
  const posLev = book?.positions.find((p) => p.symbol === symbol)?.leverage ?? null;
  const [lev, setLev] = useState<number>(live ? posLev ?? knownLev ?? 10 : paper.rules.leverage);
  const initialQty = req.quantity != null ? String(req.quantity) : live ? readQty(symbol) : (() => { const px = req.price ?? last; return px ? String(Number((1000 / px).toPrecision(2))) : ''; })();
  const [qtyText, setQtyText] = useState(initialQty);
  const [unit, setUnit] = useState<'base' | 'quote'>('base');
  const [tpOn, setTpOn] = useState(req.takeProfit != null);
  const [slOn, setSlOn] = useState(req.stopLoss != null);
  const [tpMode, setTpMode] = useState<LevelMode>('price');
  const [slMode, setSlMode] = useState<LevelMode>('price');
  const [tpText, setTpText] = useState(req.takeProfit != null ? String(req.takeProfit) : '');
  const [slText, setSlText] = useState(req.stopLoss != null ? String(req.stopLoss) : '');
  const [reduce, setReduce] = useState(false);
  const [step, setStep] = useState<Step>(req.source === 'panel' && req.quantity != null && req.mode === 'live' ? 'confirm' : 'form');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<Order | null>(null);
  const crid = useRef<{ key: string; id: string } | null>(null);
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);

  const types = live && venue ? VENUE_TYPES[venue] : (['market', 'limit', 'stop'] as TicketType[]);
  const price = parseNum(priceText);
  const entry = entryPrice(side, type, price, { bid: quote?.bid ?? null, ask: quote?.ask ?? null, last });
  const dUnit = distanceUnit(symbol, digits);
  const decimals = qtyDecimals(entry ?? last, venue);
  const qtyRaw = parseNum(qtyText);
  const qty = qtyRaw == null ? null : unit === 'base' ? qtyRaw : entry ? +(qtyRaw / entry).toFixed(Math.max(decimals, 2)) : null;
  const tp = tpOn ? levelFrom(tpMode, parseNum(tpText), side, 'tp', entry, dUnit) : null;
  const sl = slOn ? levelFrom(slMode, parseNum(slText), side, 'sl', entry, dUnit) : null;
  const leverage = live ? lev : paper.rules.leverage;
  const sum = summarize({ side, type, qty, entry, leverage, sl, tp, venue: live ? venue : null });
  const paperFree = paper.balance - paper.positions.reduce((n, p) => n + marginOf(p), 0);
  const available = live ? book?.account?.available ?? null : paperFree;
  const issues = checkOrder({ venue, live, side, type, qty, price, entry, ref: last, leverage, sl, tp, reduceOnly: live && reduce, available, margin: sum.margin });
  const blocked = issues.some((i) => i.block) || entry == null;
  const ccy = live && venue === 'coineprofx' ? 'USD' : inst.quote || 'USDT';
  const baseUnit = venue === 'coineprofx' ? L('لات', 'lots') : inst.base || symbol;
  const fmt = (v: number | null | undefined) => (v == null ? '—' : formatPrice(v, digits));
  const money = (v: number | null | undefined, signed = false) => (v == null || !Number.isFinite(v) ? '—' : `${signed && v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);
  const typeWord = (t: TicketType) => (t === 'market' ? L('بازار', 'Market') : t === 'limit' ? L('محدود', 'Limit') : L('استاپ', 'Stop'));
  const sideWord = side === 'buy' ? L('خرید', 'Buy') : L('فروش', 'Sell');
  const qtyShown = qty != null ? String(+qty.toFixed(Math.max(decimals, 2))) : '';

  // The order's identity: a new client_request_id the moment anything that would be sent changes.
  const body = useMemo(() => ({ symbol, side, type, quantity: qty ?? 0, price: type === 'market' ? null : price, reduce_only: live && reduce, stop_loss: sl, take_profit: tp }), [symbol, side, type, qty, price, live, reduce, sl, tp]);
  const bodyKey = JSON.stringify(body) + leverage;
  const requestId = () => { if (!crid.current || crid.current.key !== bodyKey) crid.current = { key: bodyKey, id: uuid() }; return crid.current.id; };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && step !== 'sending') { e.stopPropagation(); close(); } };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [step, close]);

  const switchMode = (m: 'live' | 'paper') => { setModeLocal(m); setMode(m); setStep('form'); setError(null); if (m === 'paper') setLev(paper.rules.leverage); };
  const switchLevelMode = (kind: 'tp' | 'sl', next: LevelMode) => {
    const level = kind === 'tp' ? tp : sl;
    const v = valueFrom(next, level, entry, dUnit);
    const text = v == null ? '' : next === 'price' ? String(+v.toFixed(digits)) : next === 'percent' ? v.toFixed(2) : String(+v.toFixed(next === 'distance' && dUnit < 1 ? 1 : digits));
    if (kind === 'tp') { setTpMode(next); setTpText(text); } else { setSlMode(next); setSlText(text); }
  };
  const setPercent = (pct: number) => {
    const fee = live && venue && FEE_RATE[venue] ? FEE_RATE[venue]!.taker : paper.rules.takerFeePercent / 100;
    const q = qtyForPercent(pct, available, leverage, entry, fee, decimals);
    if (q != null) { setUnit('base'); setQtyText(String(q)); }
  };
  const nudge = (k: -1 | 1) => { const base = price ?? entry ?? last; if (base) setPriceText(String(+(base + k * 10 ** -digits).toFixed(digits))); };

  const sendPaper = () => {
    if (qty == null || entry == null) return;
    const ps = side === 'buy' ? 'long' : 'short';
    const ref = last ?? entry;
    const st = usePaper.getState();
    const id = type === 'market'
      ? st.open(symbol, ps, qty, req.price ?? ref, sl ?? undefined, tp ?? undefined)
      : st.place({ symbol, side: ps, type, qty, limitPrice: type === 'limit' ? price ?? undefined : undefined, stopPrice: type === 'stop' ? price ?? undefined : undefined, sl: sl ?? undefined, tp: tp ?? undefined }, ref);
    if (!id) { setError(usePaper.getState().lastError === 'margin' ? L('موجودی حساب آزمایشی کافی نیست.', 'Not enough paper balance.') : L('سفارش آزمایشی پذیرفته نشد.', 'The paper order was not accepted.')); return; }
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
    notify(`${L('آزمایشی', 'Paper')} · ${sideWord} ${qtyShown} ${symbol} ${typeWord(type)}`);
    close();
  };

  const sendLive = async () => {
    if (!venue || qty == null) return;
    const id = requestId();
    setStep('sending'); setError(null);
    try {
      if (venue === 'tradeyar' && lev !== (posLev ?? knownLev)) {
        await setLeverage(venue, symbol, lev);
        useTrading.getState().rememberLeverage(symbol, lev);
      }
      const order = await placeOrder(venue, { client_request_id: id, ...body, quantity: qty });
      writeQty(symbol, qtyShown);
      if (order && (order.status === 'rejected' || order.status === 'failed')) {
        setResult(order); setError(order.error ?? L('صرافی سفارش را نپذیرفت.', 'The exchange refused the order.')); setStep('error');
      } else {
        if (type === 'market' && (sl != null || tp != null) && !reduce) useTrading.getState().rememberProtection(venue, symbol, side === 'buy' ? 'long' : 'short', { sl, tp });
        setResult(order); setStep('done');
        notify(`${sideWord} ${qtyShown} ${symbol} · ${L(...(ORDER_STATUS[order?.status ?? 'submitted'] ?? ORDER_STATUS.submitted!))}`);
      }
      void useTrading.getState().refresh(venue, 'all');
    } catch (e) {
      setError(tradeErrorText(e, L)); setStep('error');
    }
  };

  // Dragging by the header (desktop): the ticket stays where the reader puts it.
  const drag = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button, input, select') || window.innerWidth <= 760) return;
    const box = (e.currentTarget.parentElement as HTMLElement).getBoundingClientRect();
    const dx = e.clientX - box.left; const dy = e.clientY - box.top;
    const move = (ev: PointerEvent) => setPos({ x: Math.max(0, Math.min(window.innerWidth - box.width, ev.clientX - dx)), y: Math.max(0, Math.min(window.innerHeight - 60, ev.clientY - dy)) });
    const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); };
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
  };

  const levelRow = (kind: 'tp' | 'sl') => {
    const on = kind === 'tp' ? tpOn : slOn;
    const lvl = kind === 'tp' ? tp : sl;
    const m = kind === 'tp' ? tpMode : slMode;
    const text = kind === 'tp' ? tpText : slText;
    const pnl = lvl != null && entry != null && qty != null ? pnlAt(side, entry, lvl, qty) : null;
    const disabled = live && reduce;
    return (
      <div className={`ot-level ${kind}${on ? ' on' : ''}`}>
        <label className="ot-check">
          <input type="checkbox" checked={on} disabled={disabled} onChange={(e) => (kind === 'tp' ? setTpOn : setSlOn)(e.target.checked)} />
          <span className="fa">{kind === 'tp' ? L('حد سود', 'Take profit') : L('حد ضرر', 'Stop loss')}</span>
        </label>
        {on ? (
          <>
            <div className="seg ot-modes" role="radiogroup">
              {(['price', 'distance', 'percent'] as LevelMode[]).map((x) => (
                <button key={x} role="radio" aria-checked={m === x} className={m === x ? 'on' : ''} onClick={() => switchLevelMode(kind, x)}>
                  <span className="fa">{x === 'price' ? L('قیمت', 'Price') : x === 'distance' ? (dUnit < 0.001 || /^[A-Z]{6}$/.test(symbol) && !symbol.startsWith('X') ? L('پیپ', 'Pips') : L('فاصله', 'Distance')) : '%'}</span>
                </button>
              ))}
            </div>
            <div className="ot-input">
              <input className="num" dir="ltr" inputMode="decimal" value={text} aria-label={kind} onChange={(e) => (kind === 'tp' ? setTpText : setSlText)(e.target.value)} />
            </div>
            <div className="ot-level-out num" dir="ltr">
              {m !== 'price' ? <span>{fmt(lvl)}</span> : null}
              <b className={pnl == null ? '' : pnl >= 0 ? 'up' : 'down'}>{money(pnl, true)} {pnl != null ? ccy : ''}</b>
            </div>
          </>
        ) : null}
      </div>
    );
  };

  const info: [string, ReactNode][] = [
    [L('ارزش معامله', 'Trade value'), <>{money(sum.value)} <i>{ccy}</i></>],
    [L('مارجین', 'Margin'), <>{money(sum.margin)} <i>{ccy}</i></>],
    [L('کارمزد (تخمینی)', 'Fee (est.)'), sum.fee == null ? '—' : <>{money(sum.fee)} <i>{ccy}</i></>],
    [L('لیکوئید (تخمینی)', 'Liquidation (est.)'), fmt(sum.liquidation)],
    [L('سود به ریسک', 'Reward / risk'), sum.rr == null ? '—' : `1 : ${sum.rr.toFixed(2)}`],
    [L('در دسترس', 'Available'), <>{money(available)} <i>{ccy}</i></>],
  ];

  const style = pos ? { left: pos.x, top: pos.y, right: 'auto' } : undefined;
  const venueName = venue ? `${L(VENUE_LABEL[venue].fa, VENUE_LABEL[venue].en)} · ${VENUE_LABEL[venue].broker}` : L('بدون صرافی', 'No venue');

  return (
    <div className="ot" role="dialog" aria-modal="false" aria-label={L('سفارش', 'Order')} dir={lang === 'fa' ? 'rtl' : 'ltr'} style={style}>
      <header className="ot-head" onPointerDown={drag}>
        <SymbolLogo symbol={symbol} size={20} />
        <div className="ot-title">
          <b className="sym" dir="ltr">{symbol}</b>
          <span className="muted fa">{mode === 'paper' ? L('حساب آزمایشی', 'Paper account') : venueName}</span>
        </div>
        <div className="seg ot-mode" role="radiogroup" aria-label={L('حالت', 'Mode')}>
          <button role="radio" aria-checked={mode === 'paper'} className={mode === 'paper' ? 'on' : ''} onClick={() => switchMode('paper')}><span className="fa">{L('آزمایشی', 'Paper')}</span></button>
          <button role="radio" aria-checked={mode === 'live'} disabled={signalOnly} title={signalOnly ? L('فارکس در پرو چارت فقط سیگنال است', 'Forex on Pro Chart is signals only') : undefined} className={`${mode === 'live' ? 'on' : ''} live`} onClick={() => switchMode('live')}><i className={`ot-dot${ready ? ' on' : ''}`} /><span className="fa">{L('زنده', 'Live')}</span></button>
        </div>
        <button className="lt" onClick={close} aria-label={L('بستن', 'Close')}><Icon name="ph-x" size="sm" /></button>
      </header>
      {signalOnly ? <SignalOnlyNote onSignals={() => { close(); usePage.getState().open('signals'); }} /> : null}

      {live && !ready && book?.status !== 'checking' ? (
        <div className="ot-body"><LiveNeeds venue={venue} onSignIn={onSignIn} onPaper={() => switchMode('paper')} /></div>
      ) : step === 'confirm' || step === 'sending' ? (
        <div className="ot-body">
          <p className="ot-confirm-title fa">{L('این سفارش واقعی روی صرافی ثبت می‌شود:', 'This places a real order on the exchange:')}</p>
          <dl className="ot-review">
            <dt className="fa">{L('نماد', 'Symbol')}</dt><dd className="sym" dir="ltr">{symbol}</dd>
            <dt className="fa">{L('جهت', 'Side')}</dt><dd className={side === 'buy' ? 'buy' : 'down'}>{sideWord}</dd>
            <dt className="fa">{L('نوع', 'Type')}</dt><dd>{typeWord(type)}{type !== 'market' ? <> · <span className="num">{fmt(price)}</span></> : null}</dd>
            <dt className="fa">{L('مقدار', 'Quantity')}</dt><dd className="num" dir="ltr">{qtyShown} {baseUnit}</dd>
            <dt className="fa">{L('اهرم', 'Leverage')}</dt><dd className="num" dir="ltr">{lev}x</dd>
            {sl != null ? <><dt className="fa">{L('حد ضرر', 'Stop loss')}</dt><dd className="num down" dir="ltr">{fmt(sl)}</dd></> : null}
            {tp != null ? <><dt className="fa">{L('حد سود', 'Take profit')}</dt><dd className="num up" dir="ltr">{fmt(tp)}</dd></> : null}
            {reduce ? <><dt className="fa">{L('فقط کاهشی', 'Reduce only')}</dt><dd>✓</dd></> : null}
            <dt className="fa">{L('ارزش', 'Value')}</dt><dd className="num" dir="ltr">≈ {money(sum.value)} {ccy}</dd>
            <dt className="fa">{L('مارجین', 'Margin')}</dt><dd className="num" dir="ltr">≈ {money(sum.margin)} {ccy}</dd>
          </dl>
          <div className="ot-actions">
            <button className="btn btn-quiet sm" disabled={step === 'sending'} onClick={() => setStep('form')}>{L('بازگشت', 'Back')}</button>
            <button className={`btn ot-submit ${side}`} disabled={step === 'sending'} onClick={() => void sendLive()}>
              {step === 'sending' ? L('در حال ارسال…', 'Sending…') : L('تأیید و ارسال', 'Confirm and send')}
            </button>
          </div>
        </div>
      ) : step === 'done' ? (
        <div className="ot-body ot-result ok">
          <Icon name="ph-check" />
          <p className="fa">{L('سفارش فرستاده شد', 'Order sent')} · <b>{L(...(ORDER_STATUS[result?.status ?? 'submitted'] ?? ORDER_STATUS.submitted!))}</b></p>
          <p className="muted num" dir="ltr">{sideWord} {qtyShown} {symbol} {typeWord(type)}{result?.avgPrice ? ` @ ${fmt(result.avgPrice)}` : ''}</p>
          <div className="ot-actions">
            <button className="btn btn-quiet sm" onClick={() => { setStep('form'); crid.current = null; setResult(null); }}>{L('سفارش تازه', 'New order')}</button>
            <button className="btn btn-primary sm" onClick={close}>{L('بستن', 'Close')}</button>
          </div>
        </div>
      ) : step === 'error' ? (
        <div className="ot-body ot-result err">
          <p className="ot-error fa" dir="auto">{error}</p>
          <div className="ot-actions">
            <button className="btn btn-quiet sm" onClick={() => { setStep('form'); setError(null); }}>{L('ویرایش', 'Edit')}</button>
            <button className={`btn ot-submit ${side}`} onClick={() => void sendLive()}>{L('دوباره بفرست', 'Send again')}</button>
          </div>
          <p className="muted ot-foot fa">{L('ارسال دوباره همان شناسه‌ی درخواست را دارد؛ سفارش دوم ساخته نمی‌شود.', 'Sending again reuses the same request id; no second order is made.')}</p>
        </div>
      ) : (
        <div className="ot-body">
          {live && book?.status === 'checking' ? <p className="muted fa">{L('در حال بررسی حساب…', 'Checking the account…')}</p> : null}
          <div className="ot-sides">
            <button className={`ot-side sell${side === 'sell' ? ' on' : ''}`} onClick={() => setSide('sell')}>
              <span className="fa">{L('فروش', 'Sell')}</span><b className="num">{fmt(quote?.bid ?? last)}</b>
            </button>
            <span className="ot-spread num">{quote ? formatPrice(quote.spread, digits) : ''}</span>
            <button className={`ot-side buy${side === 'buy' ? ' on' : ''}`} onClick={() => setSide('buy')}>
              <span className="fa">{L('خرید', 'Buy')}</span><b className="num">{fmt(quote?.ask ?? last)}</b>
            </button>
          </div>
          <nav className="ot-types" role="tablist">
            {(['market', 'limit', 'stop'] as TicketType[]).map((x) => {
              const off = !types.includes(x);
              return <button key={x} role="tab" aria-selected={type === x} disabled={off} title={off ? L('این صرافی از برنامه سفارش استاپ نمی‌گیرد', 'This exchange takes no stop orders from the app') : undefined} className={type === x ? 'on' : ''} onClick={() => setType(x)}><span className="fa">{typeWord(x)}</span></button>;
            })}
          </nav>
          {type !== 'market' ? (
            <div className="ot-row">
              <span className="ot-label fa">{type === 'limit' ? L('قیمت محدود', 'Limit price') : L('قیمت استاپ', 'Stop price')}</span>
              <div className="ot-input stepper">
                <button onClick={() => nudge(-1)} aria-label="−">−</button>
                <input className="num" dir="ltr" inputMode="decimal" value={priceText} onChange={(e) => setPriceText(e.target.value)} placeholder={fmt(last)} aria-label="price" />
                <button onClick={() => nudge(1)} aria-label="+">+</button>
              </div>
            </div>
          ) : null}
          <div className="ot-row">
            <span className="ot-label fa">{L('مقدار', 'Quantity')}</span>
            <div className="ot-input with-unit">
              <input className="num" dir="ltr" inputMode="decimal" value={qtyText} onChange={(e) => setQtyText(e.target.value)} aria-label="quantity" />
              {venue !== 'coineprofx' ? (
                <button className="ot-unit" onClick={() => { const v = qty; setUnit(unit === 'base' ? 'quote' : 'base'); if (v != null && entry) setQtyText(unit === 'base' ? (v * entry).toFixed(2) : String(+v.toFixed(decimals))); }} title={L('تغییر واحد', 'Switch unit')}>
                  <span className="num">{unit === 'base' ? baseUnit : ccy}</span><Icon name="ph-arrows-down-up" size="sm" />
                </button>
              ) : <span className="ot-unit static fa">{baseUnit}</span>}
            </div>
            <div className="ot-hint num" dir="ltr">{unit === 'quote' ? (qty != null ? `≈ ${qtyShown} ${baseUnit}` : '') : sum.value != null ? `≈ ${money(sum.value)} ${ccy}` : ''}</div>
            <div className="ot-pcts">
              {[25, 50, 75, 100].map((p) => <button key={p} className="num" disabled={available == null || entry == null} onClick={() => setPercent(p)}>{p}%</button>)}
            </div>
          </div>
          {live ? (
            <div className="ot-row ot-lev">
              <span className="ot-label fa">{L('اهرم', 'Leverage')}</span>
              <div className="ot-levs">
                {LEVERAGES.filter((x) => venue && x <= MAX_LEVERAGE[venue]).map((x) => <button key={x} className={`num${lev === x ? ' on' : ''}`} onClick={() => setLev(x)}>{x}x</button>)}
              </div>
            </div>
          ) : (
            <div className="ot-row ot-lev"><span className="ot-label fa">{L('اهرم حساب آزمایشی', 'Paper leverage')}</span><b className="num" dir="ltr">{paper.rules.leverage}x</b></div>
          )}
          {levelRow('tp')}
          {levelRow('sl')}
          {live ? (
            <label className="ot-check"><input type="checkbox" checked={reduce} onChange={(e) => { setReduce(e.target.checked); if (e.target.checked) { setTpOn(false); setSlOn(false); } }} /><span className="fa">{L('فقط کاهش موقعیت', 'Reduce only')}</span></label>
          ) : null}
          <dl className="ot-info">
            {info.map(([k, v]) => <div key={k}><dt className="fa">{k}</dt><dd className="num" dir="ltr">{v}</dd></div>)}
          </dl>
          {issues.length ? (
            <ul className="ot-issues">{issues.map((i, n) => <li key={n} className={i.block ? 'err' : 'warn'}><span className="fa">{L(i.fa, i.en)}</span></li>)}</ul>
          ) : null}
          {error ? <p className="ot-error fa" dir="auto">{error}</p> : null}
          <button className={`btn ot-submit ${side}`} disabled={blocked || (live && !ready)} onClick={() => (live ? setStep('confirm') : sendPaper())}>
            <span className="fa">{sideWord}</span> <span className="num" dir="ltr">{qtyShown || '—'} {symbol}</span> <span className="fa">{type === 'market' ? L('بازار', 'MKT') : `@ ${fmt(price)}`}</span>
          </button>
          <p className="ot-foot muted fa">
            {live ? L('هر سفارش پیش از ارسال تأیید صریح می‌خواهد؛ نتیجه را فقط صرافی تعیین می‌کند.', 'Every order asks for an explicit confirmation; only the exchange decides the outcome.') : L('معامله‌ی آزمایشی فقط در همین مرورگر است و به هیچ صرافی نمی‌رود.', 'A paper trade stays in this browser and goes to no exchange.')}
          </p>
        </div>
      )}
    </div>
  );
}

/** Forex, metals and indices: no live orders from the chart, only CoinePro-FX's signals. */
export function SignalOnlyNote({ onSignals }: { onSignals(): void }) {
  const L = useL();
  return (
    <p className="ot-note fa">
      <Icon name="ph-info" size="sm" />
      <span>{L('فارکس، فلزات و شاخص‌ها در پرو چارت فقط سیگنال‌اند؛ معامله‌ی زنده‌ی آن‌ها از چارت انجام نمی‌شود و این سفارش آزمایشی است.', 'Forex, metals and indices are signals only on Pro Chart: they are not traded live from the chart, and this order is a paper one.')}{' '}
        <button className="link" onClick={onSignals}>{L('سیگنال‌های فارکس', 'Forex signals')}</button></span>
    </p>
  );
}
