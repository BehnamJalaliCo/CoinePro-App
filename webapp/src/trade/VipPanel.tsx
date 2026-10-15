// «کانال VIP تریدیار» beside the chart: the channel's signals — active, recent and closed with
// their results and the win rate — filtered by symbol; a click opens the symbol on the signal's
// interval with its levels drawn; copy-trade sends one to LBank after a confirmation, and the copy
// trades list their exchange status with a close button. A reader outside the channel sees why,
// the join terms (the LBank referral link and the minimum deposit) and the way in.
import { useEffect, useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { usePanels, type PanelId } from '../state/panels';
import { useMarket, digitsOf } from '../state/market';
import { useSession } from '../data/session';
import { intervalOfTimeframe, type Signal } from '../data/signals';
import { formatPrice } from '../chart/format';
import { faAgo, faNum } from '../i18n/time';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { parseNum, useL } from '../pages/kit';
import { PAGES, type PageId } from '../pages/registry';
import { uuid } from '../data/http';
import { getVenueLink, type VenueLink } from './api';
import { closeExecution, copySignal, isOpen, livePct, outcomeOf, pollVip, statusLabel, useVip, winRate, type Execution, type VipList } from './vip';
import { usePremium } from './entitlements';
import { tradeErrorText } from './wire';
import { notify } from './toast';
import { timeAgoEn } from './VipOverlay';
import './trade.css';

export const VIP_PANEL = 'vip' as PanelId;

type Tab = VipList | 'copies';

/** The join page: area H's `vip-join` once it is registered, the exchange membership page before. */
export const joinPage = (): PageId => (PAGES.some((p) => (p.id as string) === 'vip-join') ? ('vip-join' as PageId) : 'membership');

export function VipPanel({ onSignIn }: { onSignIn(): void }) {
  const L = useL();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const member = useVip((s) => s.member);
  const markSeen = useVip((s) => s.markSeen);
  const onChart = useVip((s) => s.onChart);
  const setOnChart = useVip((s) => s.setOnChart);
  const [notifyOn, setNotifyOn] = useState(() => typeof Notification !== 'undefined' && Notification.permission === 'granted');

  useEffect(() => { markSeen(); }, [markSeen]);
  useEffect(() => { if (signed && member === null) pollVip(); }, [signed, member]);

  const askNotify = async () => {
    if (typeof Notification === 'undefined') return;
    try { setNotifyOn((await Notification.requestPermission()) === 'granted'); } catch { /* blocked */ }
  };

  return (
    <section className="panel-generic vip-panel">
      <header className="panel-head">
        <Icon name="ph-crown-simple" size="sm" className="vip-crown" />
        <span className="fa">{L('کانال VIP تریدیار', 'TradeYar VIP channel')}</span>
        <span className="ws-spacer" />
        {member ? (
          <>
            <button className={`lt${onChart ? ' on' : ''}`} title={L('نمایش روی چارت', 'Show on the chart')} aria-pressed={onChart} onClick={() => setOnChart(!onChart)}><Icon name="ph-chart-line-up" size="sm" /></button>
            {typeof Notification !== 'undefined' ? <button className={`lt${notifyOn ? ' on' : ''}`} title={notifyOn ? L('اعلان مرورگر روشن است', 'Browser notifications are on') : L('اعلان مرورگر برای سیگنال تازه', 'Browser notifications for new signals')} onClick={() => void askNotify()}><Icon name="ph-bell-ringing" size="sm" /></button> : null}
          </>
        ) : null}
      </header>
      {!signed ? <Pitch onSignIn={onSignIn} signed={false} /> : member === false ? <Pitch onSignIn={onSignIn} signed /> : member === null ? <div className="empty fa">{L('در حال بارگذاری…', 'Loading…')}</div> : <MemberView />}
    </section>
  );
}

function Pitch({ onSignIn, signed }: { onSignIn(): void; signed: boolean }) {
  const L = useL();
  const open = usePage((s) => s.open);
  const message = useVip((s) => s.message);
  const status = useVip((s) => s.status);
  const terms = useVip((s) => s.terms);
  const rules = useVip((s) => s.rules);
  const minDeposit = rules?.minDeposit ?? terms?.minDeposit ?? null;
  const loadMembership = useVip((s) => s.loadMembership);
  useEffect(() => { void loadMembership(); }, [loadMembership, signed]);
  return (
    <div className="panel-body vip-pitch">
      <div className="vip-hero">
        <Icon name="ph-crown-simple" />
        <h3 className="fa">{L('سیگنال‌های کانال VIP، روی چارت شما', 'The VIP channel’s signals, on your chart')}</h3>
        <p className="fa">{L('هر سیگنال کانال با ناحیه‌ی ورود، حد ضرر و هدف‌ها روی چارت همان نماد می‌نشیند و با یک کلیک روی LBank کپی می‌شود. عضو کانال، همه‌ی امکانات پرو چارت را رایگان دارد.', 'Every channel signal lands on its symbol’s chart with the entry zone, stop and targets, and copies to LBank in one click. A channel member has all of Pro Chart free.')}</p>
      </div>
      {message ? <p className="vip-note fa" dir="rtl">{message}</p> : null}
      {status?.messageFa ? <p className="vip-note fa" dir="rtl">{status.messageFa}</p> : null}
      <ol className="tr-steps">
        <li className={terms?.lbankUrl ? 'todo' : 'wait'}>
          <span className="tr-step-mark"><span className="num">1</span></span>
          <span className="tr-step-text"><span className="fa">{L('ثبت‌نام در LBank با لینک تریدیار', 'Sign up on LBank with TradeYar’s link')}</span>{terms?.noticeFa ? <small className="fa" dir="rtl">{terms.noticeFa}</small> : null}</span>
          {terms?.lbankUrl ? <a className="btn btn-quiet sm" href={terms.lbankUrl} target="_blank" rel="noreferrer">LBank <Icon name="ph-arrow-square-out" size="sm" /></a> : null}
        </li>
        <li className="todo">
          <span className="tr-step-mark"><span className="num">2</span></span>
          <span className="tr-step-text"><span className="fa">{L('واریز به حساب صرافی', 'Fund the exchange account')}</span>{minDeposit ? <small className="fa">{L('حداقل', 'At least')} <b className="num" dir="ltr">{minDeposit} USDT</b></small> : null}</span>
        </li>
        <li className={status?.status === 'approved' ? 'done' : 'todo'}>
          <span className="tr-step-mark">{status?.status === 'approved' ? <Icon name="ph-check" size="sm" /> : <span className="num">3</span>}</span>
          <span className="tr-step-text"><span className="fa">{L('ثبت UID صرافی برای بررسی', 'Send your exchange UID for review')}</span>{status?.uid ? <small className="num" dir="ltr">UID {status.uid}</small> : null}</span>
        </li>
      </ol>
      {rules && (rules.warnBelow != null || rules.removeAt != null || rules.linesFa.length) ? (
        <div className="vip-note">
          <b className="fa">{L('قوانین ماندن در کانال', 'Staying in the channel')}</b>
          {rules.warnBelow != null ? <p className="fa">{L('موجودی زیر', 'Balance under')} <b className="num" dir="ltr">{rules.warnBelow} USDT</b>{L(': هشدار', ': a warning')}</p> : null}
          {rules.removeAt != null ? <p className="fa">{L('موجودی', 'Balance at')} <b className="num" dir="ltr">{rules.removeAt} USDT</b>{L(': خروج از کانال', ': removal from the channel')}</p> : null}
          {rules.linesFa.map((x, i) => <p key={i} className="fa" dir="rtl">{x}</p>)}
        </div>
      ) : null}
      {signed ? (
        <button className="btn vip-join" onClick={() => open(joinPage())}><Icon name="ph-crown-simple" size="sm" /><span className="fa">{L('عضویت در کانال VIP', 'Join the VIP channel')}</span></button>
      ) : (
        <div className="vip-actions">
          <button className="btn vip-join" onClick={onSignIn}><span className="fa">{L('ورود به تریدیار', 'Sign in to TradeYar')}</span></button>
          <button className="btn btn-quiet sm" onClick={() => open(joinPage())}><span className="fa">{L('شرایط عضویت', 'How to join')}</span></button>
        </div>
      )}
    </div>
  );
}

function MemberView() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const chartSymbol = usePrefs((s) => s.symbol);
  const lists = useVip((s) => s.lists);
  const errors = useVip((s) => s.errors);
  const load = useVip((s) => s.load);
  const focus = useVip((s) => s.focus);
  const setFocus = useVip((s) => s.setFocus);
  const premium = usePremium();
  const tickers = useMarket((s) => s.tickers);
  const [tab, setTab] = useState<Tab>('active');
  const [q, setQ] = useState('');
  const [onlyHere, setOnlyHere] = useState(false);
  const [copying, setCopying] = useState<Signal | null>(null);

  useEffect(() => { if (tab !== 'copies') void load(tab).catch(() => undefined); }, [tab, load]);
  const list = tab === 'copies' ? null : lists[tab];
  const rows = useMemo(() => (list ?? []).filter((s) => (!onlyHere || s.symbol === chartSymbol) && (!q || s.symbol.includes(q.trim().toUpperCase()))), [list, onlyHere, chartSymbol, q]);
  const rate = useMemo(() => winRate((tab === 'active' ? [] : rows).filter((s) => !isOpen(s))), [rows, tab]);

  const show = (s: Signal) => {
    const p = usePrefs.getState();
    p.setSymbol(s.symbol);
    const iv = intervalOfTimeframe(s.timeframe);
    if (iv) p.setInterval(iv);
    setFocus(s);
    if (window.innerWidth <= 760 && usePanels.getState().open === VIP_PANEL) usePanels.getState().toggle(VIP_PANEL);
  };

  return (
    <>
      {premium.vip ? <p className="vip-free fa"><Icon name="ph-seal-check" size="sm" />{L('عضو کانال هستید: همه‌ی امکانات برایتان رایگان است.', 'You are in the channel: everything is free for you.')}</p> : null}
      <nav className="chips panel-chips">
        {([['active', L('فعال', 'Active')], ['recent', L('اخیر', 'Recent')], ['closed', L('بسته‌شده', 'Closed')], ['copies', L('کپی‌ترید', 'Copy trades')]] as [Tab, string][]).map(([id, label]) => (
          <button key={id} className={`chip${tab === id ? ' on' : ''}`} onClick={() => setTab(id)}><span className="fa">{label}</span>{id === 'active' && lists.active?.length ? <b className="vip-count num">{lang === 'fa' ? faNum(lists.active.length) : lists.active.length}</b> : null}</button>
        ))}
      </nav>
      {tab !== 'copies' ? (
        <div className="vip-filter">
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('نماد', 'Symbol')} dir="ltr" aria-label={L('فیلتر نماد', 'Symbol filter')} />
          <button className={`chip${onlyHere ? ' on' : ''}`} onClick={() => setOnlyHere(!onlyHere)}><span className="num">{chartSymbol}</span></button>
          {rate.rate != null ? <span className="vip-rate fa" title={L('نرخ برد سیگنال‌های بسته‌شده‌ی این فهرست', 'Win rate of the closed signals in this list')}>
            {lang === 'fa' ? `${faNum(rate.wins)} برد · ${faNum(rate.losses)} باخت` : `${rate.wins} won · ${rate.losses} lost`} · <b className="num">{rate.rate.toFixed(0)}%</b>
          </span> : null}
        </div>
      ) : null}
      <div className="panel-body">
        {tab === 'copies' ? <Copies /> : list === null ? (
          errors[tab] ? <div className="empty fa">{L('دریافت نشد.', 'Could not load.')} <button className="link" onClick={() => void load(tab).catch(() => undefined)}>{L('دوباره', 'Retry')}</button></div> : <div className="empty fa">{L('در حال بارگذاری…', 'Loading…')}</div>
        ) : !rows.length ? <div className="empty fa">{tab === 'active' ? L('الان سیگنال فعالی در کانال نیست.', 'No active signal in the channel right now.') : L('چیزی پیدا نشد.', 'Nothing found.')}</div> : rows.map((s) => {
          const d = digitsOf(s.symbol);
          const open = isOpen(s);
          const pct = open ? livePct(s, tickers[s.symbol]?.last ?? s.current) : s.pctGain;
          const outcome = outcomeOf(s);
          const f = (v: number | null | undefined) => (v == null ? '—' : formatPrice(v, d));
          return (
            <div key={s.id} className={`vip-row${focus?.id === s.id ? ' on' : ''}`}>
              <button className="vip-main" onClick={() => show(s)} title={L('روی چارت', 'On the chart')}>
                <span className="sig-top">
                  <SymbolLogo symbol={s.symbol} size={18} />
                  <span className="sym">{s.symbol}</span>
                  <span className={`sig-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</span>
                  <span className="num muted">{s.timeframe}</span>
                  <span className="ws-spacer" />
                  {pct != null ? <b className={`num ${pct >= 0 ? 'up' : 'down'}`}>{pct >= 0 ? '+' : '−'}{Math.abs(pct).toFixed(2)}%</b> : null}
                </span>
                <span className="sig-levels num" dir="ltr">
                  <span>{s.entryLow != null && s.entryHigh != null && s.entryLow !== s.entryHigh ? `E ${f(s.entryLow)}–${f(s.entryHigh)}` : `E ${f(s.entry)}`}</span>
                  <span className="down">SL {f(s.sl)}</span>
                  {s.targets.slice(0, 3).map((tp, i) => <span key={i} className={`up${s.hits?.[i] ? ' hit' : ''}`}>TP{i + 1} {f(tp)}{s.hits?.[i] ? ' ✓' : ''}</span>)}
                </span>
                <span className="vip-meta">
                  <span className={`vip-state ${outcome ?? ''} fa`}>{L(...statusLabel(s))}</span>
                  {!open && s.pnlUsd != null ? <span className={`num ${s.pnlUsd >= 0 ? 'up' : 'down'}`} dir="ltr">{s.pnlUsd >= 0 ? '+' : '−'}{Math.abs(s.pnlUsd).toFixed(2)}$</span> : null}
                  {s.confidence != null ? <span className="muted num">{Math.round(s.confidence)}%</span> : null}
                  <span className="ws-spacer" />
                  <span className="muted fa">{lang === 'fa' ? faAgo(s.closedAt ?? s.createdAt) : timeAgoEn(s.closedAt ?? s.createdAt)}</span>
                </span>
              </button>
              {open ? <button className="btn btn-quiet sm vip-copy" onClick={() => setCopying(s)}><Icon name="ph-copy-simple" size="sm" /><span className="fa">{L('کپی‌ترید', 'Copy trade')}</span></button> : null}
            </div>
          );
        })}
      </div>
      {copying ? <CopyDialog signal={copying} onClose={() => setCopying(null)} onDone={() => { setCopying(null); setTab('copies'); }} /> : null}
    </>
  );
}

const EXEC_STATUS: Record<string, [string, string]> = {
  queued: ['در صف', 'Queued'], submitted: ['فرستاده شد', 'Submitted'], open: ['باز روی صرافی', 'Open on the exchange'], close_requested: ['درخواست بستن', 'Closing'],
  closed: ['بسته شد', 'Closed'], rejected: ['رد شد', 'Rejected'], failed: ['ناموفق', 'Failed'], unknown: ['نامشخص', 'Unknown'],
};

function Copies() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const executions = useVip((s) => s.executions);
  const loadExecutions = useVip((s) => s.loadExecutions);
  const lists = useVip((s) => s.lists);
  const [ask, setAsk] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => { void loadExecutions().catch(() => setFailed(true)); const id = setInterval(() => void loadExecutions().catch(() => undefined), 10_000); return () => clearInterval(id); }, [loadExecutions]);
  const symbolOf = (x: Execution) => x.symbol ?? [...(lists.active ?? []), ...(lists.recent ?? []), ...(lists.closed ?? [])].find((s) => s.id === x.signalId)?.symbol ?? `#${x.signalId}`;
  const close = async (x: Execution) => {
    setBusy(x.id);
    try { await closeExecution(x.id); notify(L('درخواست بستن فرستاده شد', 'Close sent')); } catch (e) { notify(tradeErrorText(e, L), 'error'); } finally { setBusy(null); setAsk(null); void loadExecutions().catch(() => undefined); }
  };
  if (executions === null) return <div className="empty fa">{failed ? L('دریافت نشد.', 'Could not load.') : L('در حال بارگذاری…', 'Loading…')}</div>;
  if (!executions.length) return <div className="empty fa">{L('هنوز سیگنالی کپی نکرده‌اید.', 'You have not copied a signal yet.')}</div>;
  return (
    <>
      {executions.map((x) => (
        <div key={x.id} className="vip-exec">
          <span className="sig-top">
            {symbolOf(x).startsWith('#') ? null : <SymbolLogo symbol={symbolOf(x)} size={16} />}
            <span className="sym">{symbolOf(x)}</span>
            <span className={`sig-dir ${x.side === 'buy' ? 'up' : 'down'}`}>{x.side === 'buy' ? 'BUY' : 'SELL'}</span>
            {x.quantity ? <span className="num">{x.quantity}</span> : null}
            <span className="ws-spacer" />
            <span className={`vip-state ${x.status} fa`}>{L(...(EXEC_STATUS[x.status] ?? [x.status, x.status]))}</span>
          </span>
          {x.error ? <small className="down fa" dir="auto">{x.error}</small> : null}
          <span className="vip-meta">
            <span className="muted num">LBank · {x.product || 'futures'}</span>
            <span className="ws-spacer" />
            <span className="muted fa">{x.createdAt ? (lang === 'fa' ? faAgo(x.createdAt) : timeAgoEn(x.createdAt)) : ''}</span>
            {x.canClose ? (ask === x.id ? (
              <span className="vip-ask"><span className="fa">{L('بسته شود؟', 'Close it?')}</span><button className="btn btn-quiet sm" disabled={busy === x.id} onClick={() => void close(x)}>{L('بله', 'Yes')}</button><button className="lt" onClick={() => setAsk(null)}><Icon name="ph-x" size="sm" /></button></span>
            ) : <button className="btn btn-quiet sm" onClick={() => setAsk(x.id)}>{L('بستن', 'Close')}</button>) : null}
          </span>
        </div>
      ))}
    </>
  );
}

function CopyDialog({ signal, onClose, onDone }: { signal: Signal; onClose(): void; onDone(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const last = useMarket((s) => s.tickers[signal.symbol]?.last ?? signal.current ?? signal.entry ?? null);
  const [link, setLink] = useState<VenueLink | null>(null);
  const [linkFailed, setLinkFailed] = useState(false);
  const [unit, setUnit] = useState<'base' | 'usdt'>('usdt');
  const [text, setText] = useState('100');
  const [step, setStep] = useState<'form' | 'confirm' | 'sending'>('form');
  const [error, setError] = useState<string | null>(null);
  const [rid] = useState(() => uuid());
  const d = digitsOf(signal.symbol);
  const base = signal.symbol.replace(/USDT$/, '');
  useEffect(() => { void getVenueLink().then(setLink).catch(() => setLinkFailed(true)); }, []);
  const raw = parseNum(text);
  const qty = raw == null || raw <= 0 ? null : unit === 'base' ? raw : last ? +(raw / last).toPrecision(4) : null;
  const connected = Boolean(link?.connected && link.permission !== 'spot');

  const send = async () => {
    if (qty == null) return;
    setStep('sending'); setError(null);
    try {
      const x = await copySignal(signal.id, qty, rid);
      notify(`${L('کپی‌ترید', 'Copy trade')} ${signal.symbol} · ${L(...(EXEC_STATUS[x?.status ?? 'queued'] ?? EXEC_STATUS.queued!))}`);
      onDone();
    } catch (e) { setError(tradeErrorText(e, L)); setStep('confirm'); }
  };

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget && step !== 'sending') onClose(); }}>
      <div className="dialog vip-dialog" role="dialog" aria-modal="true" aria-label={L('کپی‌ترید', 'Copy trade')} dir={lang === 'fa' ? 'rtl' : 'ltr'} onKeyDown={(e) => { if (e.key === 'Escape' && step !== 'sending') onClose(); }}>
        <header className="dialog-head">
          <h2 className="fa">{L('کپی‌ترید روی LBank', 'Copy trade on LBank')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="vip-dialog-body">
          <div className="sig-top">
            <SymbolLogo symbol={signal.symbol} size={22} /><b className="sym">{signal.symbol}</b>
            <span className={`sig-dir ${signal.direction === 'long' ? 'up' : 'down'}`}>{signal.direction === 'long' ? 'LONG' : 'SHORT'}</span>
            <span className="num muted">{signal.timeframe}</span>
          </div>
          <dl className="ot-review">
            <dt className="fa">{L('ورود', 'Entry')}</dt><dd className="num" dir="ltr">{signal.entry != null ? formatPrice(signal.entry, d) : '—'}</dd>
            <dt className="fa">{L('حد ضرر', 'Stop loss')}</dt><dd className="num down" dir="ltr">{signal.sl != null ? formatPrice(signal.sl, d) : '—'}</dd>
            <dt className="fa">{L('هدف‌ها', 'Targets')}</dt><dd className="num up" dir="ltr">{signal.targets.slice(0, 3).map((t) => formatPrice(t, d)).join(' · ') || '—'}</dd>
          </dl>
          {!link && !linkFailed ? <p className="muted fa">{L('بررسی اتصال LBank…', 'Checking the LBank link…')}</p> : !connected ? (
            <div className="tr-warn fa">
              {L('برای کپی‌ترید، کلید API فیوچرز LBank باید متصل و تأییدشده باشد.', 'Copy trading needs verified LBank futures API keys.')}
              <button className="btn btn-quiet sm" onClick={() => { onClose(); open('connections'); }}>{L('اتصال صرافی', 'Connections')}</button>
            </div>
          ) : null}
          {step === 'form' ? (
            <>
              <label className="field">
                <span className="fa">{L('اندازه', 'Size')}</span>
                <div className="ot-input with-unit">
                  <input className="num" dir="ltr" inputMode="decimal" value={text} onChange={(e) => setText(e.target.value)} aria-label="quantity" />
                  <button type="button" className="ot-unit" onClick={() => { setUnit(unit === 'usdt' ? 'base' : 'usdt'); if (qty != null && last) setText(unit === 'usdt' ? String(qty) : (qty * last).toFixed(2)); }}>
                    <span className="num">{unit === 'usdt' ? 'USDT' : base}</span><Icon name="ph-arrows-down-up" size="sm" />
                  </button>
                </div>
                <small className="num muted" dir="ltr">{qty != null ? `= ${qty} ${base}${last ? ` ≈ ${(qty * last).toFixed(2)} USDT` : ''}` : ''}</small>
              </label>
              <p className="muted fa">{L('نماد، جهت، ورود، حد ضرر و هدف‌ها را سرور از خود سیگنال برمی‌دارد؛ فقط اندازه از شماست.', 'The server takes symbol, side, entry, stop and targets from the signal itself; only the size is yours.')}</p>
              <div className="ot-actions">
                <button className="btn btn-quiet sm" onClick={onClose}>{L('انصراف', 'Cancel')}</button>
                <button className={`btn ot-submit ${signal.direction === 'long' ? 'buy' : 'sell'}`} disabled={qty == null || !connected} onClick={() => setStep('confirm')}>{L('ادامه', 'Continue')}</button>
              </div>
            </>
          ) : (
            <>
              <p className="ot-confirm-title fa">{L('این سفارش واقعی روی LBank ثبت می‌شود:', 'This places a real order on LBank:')}</p>
              <p className="vip-confirm num" dir="ltr">{signal.direction === 'long' ? 'BUY' : 'SELL'} {qty} {signal.symbol}{last ? ` ≈ ${(qty! * last).toFixed(2)} USDT` : ''}</p>
              {error ? <p className="ot-error fa" dir="auto">{error}</p> : null}
              <div className="ot-actions">
                <button className="btn btn-quiet sm" disabled={step === 'sending'} onClick={() => setStep('form')}>{L('بازگشت', 'Back')}</button>
                <button className={`btn ot-submit ${signal.direction === 'long' ? 'buy' : 'sell'}`} disabled={step === 'sending'} onClick={() => void send()}>{step === 'sending' ? L('در حال ارسال…', 'Sending…') : L('تأیید و کپی', 'Confirm and copy')}</button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

/** The widget-bar button: a crown with the count of new signals not yet looked at. */
export function VipWidgetButton() {
  const L = useL();
  const open = usePanels((s) => s.open);
  const toggle = usePanels((s) => s.toggle);
  const fresh = useVip((s) => s.fresh.length);
  const lang = usePrefs((s) => s.lang);
  const count = fresh > 9 ? '9+' : String(fresh);
  return (
    <button className={`wbar-btn vip-wbtn${open === VIP_PANEL ? ' on' : ''}`} title={L('کانال VIP', 'VIP channel')} aria-pressed={open === VIP_PANEL} data-panel="vip" onClick={() => toggle(VIP_PANEL)}>
      <Icon name="ph-crown-simple" size="md" />
      {fresh ? <b className="wbar-badge num">{lang === 'fa' ? faNum(count) : count}</b> : null}
    </button>
  );
}
