import { useEffect, useMemo, useRef, useState } from 'react';
import { useT, usePrefs } from '../state/prefs';
import {
  CHANNEL, CHANGE24, CONDITION_NAMES, DEFAULT_REPEAT_MINUTES, FREQ_NAMES, MAX_CONDITIONS, MOVES, UNTIL_SEEN_MINUTES, describeAlert, extraConditions, lineLevel, uncheckedIds, useAlerts,
  type AlertCondition, type AlertFreq, type NewAlert, type PriceAlert, type PriceField, type TrendLine,
} from '../state/alerts';
import { useWebhooks } from '../state/webhooks';
import { WebhookSheet } from './WebhookSheet';
import { AlertAuditDialog } from './AlertAudit';
import './alerts2.css';
import { SOUNDS, playSound, type SoundId } from '../state/notify';
import { useMarket, digitsOf } from '../state/market';
import { useStudies } from '../state/studies';
import { useWatchlist } from '../state/watchlist';
import { useSession } from '../data/session';
import { backendOf, FX_SYMBOLS, instrumentOf, type Backend } from '../data/symbols';
import { ty, fx, num, ms, str } from '../data/member';
import { barsFor } from '../data/symbolStats';
import { indicatorSeries } from '../data/technicals';
import { intervalLabel, INTERVALS } from '../data/intervals';
import { useQuote } from '../data/quote';
import { CATALOG, entryOf } from '../chart/indicatorCatalog';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash, useNow } from '../ui/useFlash';
import { Dropdown } from '../ui/Menu';
import { SymbolSearch } from './SymbolSearch';
import { faAgo, faNum } from '../i18n/time';
import { dateTime, useL } from '../pages/kit';
import { submitOrder } from './orderTicket';
import { BAND_ALERT_TYPES, boundsAt, findDrawing } from '../state/drawingLinks';
import { toolName } from '../chart/tools';
import { AlertDrawingMark } from './AlertDrawingMark';
import './panels-x.css';

/** What opens the dialog prefilled (the `pc:alert-dialog` event's detail, contract 1, plus a list). */
export interface AlertSeed {
  symbol?: string; price?: number; line?: TrendLine; study?: { uid: string; key: string; plot?: number };
  /** A rectangle's or a channel's two boundaries (a drawing alert on a shape). */
  band?: { low: number; high: number };
  /** The drawing the alert follows (its stable id) and its tool. */
  drawingId?: string; drawingType?: string;
  /** A watchlist id: the alert goes on every symbol of the list (or of `symbols`). */
  list?: string; symbols?: string[];
  /** A NamaScript `alertcondition` (the script travels with the alert). */
  script?: { source: string; condition: string; name: string; inputs?: Record<string, unknown>; interval?: string };
}

// ── Account alerts (the platforms'), merged into the panel ───────────────────────────────────────
interface ServerAlert { id: string; symbol: string; condition: string; value: number | null; trigger: string; active: boolean; createdAt: number | null; lastAt: number | null; expiresAt: number | null; backend: Backend }
const call = (b: Backend) => (b === 'tradeyar' ? (p: string, o = {}) => ty<unknown>(`api/mobile/v1/${p}`, o) : (p: string, o = {}) => fx<unknown>(`user/mobile/${p}`, o));
function readServer(x: Record<string, unknown>, backend: Backend): ServerAlert {
  return { id: str(x.id), symbol: str(x.symbol).toUpperCase(), condition: str(x.condition), value: num(x.value), trigger: str(x.trigger || 'once'), active: Boolean(x.active), createdAt: ms(x.created_at_ms ?? x.created_at), lastAt: ms(x.last_triggered_at_ms), expiresAt: ms(x.expires_at_ms ?? x.expires_at), backend };
}
function useServerAlerts(): { list: ServerAlert[]; reload(): void } {
  const sessions = useSession((s) => s.sessions);
  const [list, setList] = useState<ServerAlert[]>([]);
  const [n, setN] = useState(0);
  useEffect(() => {
    let alive = true;
    const load = async () => {
      const out: ServerAlert[] = [];
      for (const b of ['tradeyar', 'coineprofx'] as Backend[]) {
        if (!sessions[b]) continue;
        const r = await call(b)('alerts').catch(() => null) as { items?: Record<string, unknown>[] } | null;
        out.push(...(r?.items ?? []).map((x) => readServer(x, b)));
      }
      if (alive) setList(out);
    };
    void load();
    const id = setInterval(() => void load(), 30_000);
    return () => { alive = false; clearInterval(id); };
  }, [sessions.tradeyar, sessions.coineprofx, n]);
  return { list, reload: () => setN((x) => x + 1) };
}

type Sort = 'created' | 'name' | 'symbol' | 'status';
type Filter = 'all' | 'active' | 'inactive' | 'fired';

export function AlertsPanel({ onNew }: { onNew(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const st = useAlerts();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const { ref: priceRef, tick } = useFlash<HTMLSpanElement>(tk?.last);
  const server = useServerAlerts();
  const [tab, setTab] = useState<'alerts' | 'log'>('alerts');
  const [edit, setEdit] = useState<PriceAlert | null>(null);
  const [q, setQ] = useState<string | null>(null);
  const [sort, setSort] = useState<Sort>(() => { try { return (localStorage.getItem('pc.alerts.sort.v1') as Sort) || 'created'; } catch { return 'created'; } });
  const [filter, setFilter] = useState<Filter>('all');
  const [menu, setMenu] = useState<'sort' | 'more' | null>(null);
  const [hooks, setHooks] = useState(false);
  const [history, setHistory] = useState<PriceAlert | null>(null);
  const sortRef = useRef<HTMLButtonElement>(null);
  const moreRef = useRef<HTMLButtonElement>(null);
  useNow(30_000);
  useEffect(() => { if (tab === 'log') st.markSeen(); }, [tab, st.log.length]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { try { localStorage.setItem('pc.alerts.sort.v1', sort); } catch { /* ignore */ } }, [sort]);

  const mine = new Set(st.list.map((a) => a.serverId).filter(Boolean));
  const remote = server.list.filter((a) => !mine.has(a.id));
  const shown = useMemo(() => {
    const needle = (q ?? '').trim().toLowerCase();
    const list = st.list.filter((a) => (filter === 'all' || (filter === 'active' ? a.active : filter === 'inactive' ? !a.active : Boolean(a.triggeredAt)))
      && (!needle || `${a.symbol} ${a.name ?? ''} ${describeAlert(a, fa)}`.toLowerCase().includes(needle)));
    const key = (a: PriceAlert): string | number => (sort === 'name' ? (a.name || describeAlert(a, fa)).toLowerCase() : sort === 'symbol' ? a.symbol : sort === 'status' ? (a.active ? 0 : a.triggeredAt ? 1 : 2) : -a.createdAt);
    return [...list].sort((x, y) => { const a = key(x), b = key(y); return typeof a === 'string' ? a.localeCompare(b as string) : (a as number) - (b as number); });
  }, [st.list, q, filter, sort, fa]);

  const toggleServer = async (a: ServerAlert) => { await call(a.backend)(`alerts/${encodeURIComponent(a.id)}`, { method: 'PATCH', body: { active: !a.active } }).catch(() => null); server.reload(); };
  const deleteServer = async (a: ServerAlert) => { await call(a.backend)(`alerts/${encodeURIComponent(a.id)}`, { method: 'DELETE' }).catch(() => null); server.reload(); };
  const activeN = st.list.filter((a) => a.active).length;
  const inactiveN = st.list.length - activeN;

  return (
    <section className="panel-generic alerts">
      <header className="panel-head alerts-head">
        <div className="seg" role="tablist">
          <button role="tab" aria-selected={tab === 'alerts'} className={tab === 'alerts' ? 'on' : ''} onClick={() => setTab('alerts')}><span className="fa">{t('alerts')}</span>{st.list.length ? <span className="num muted"> {fa ? faNum(st.list.length) : st.list.length}</span> : null}</button>
          <button role="tab" aria-selected={tab === 'log'} className={tab === 'log' ? 'on' : ''} onClick={() => setTab('log')}><span className="fa">{L('گزارش', 'Log')}</span>{st.unseen ? <span className="dot-badge" /> : null}</button>
        </div>
      </header>
      <div className="alerts-tools">
        <button className="ib" title={t('alertAdd')} onClick={onNew}><Icon name="ph-plus" size="md" /></button>
        <span className="alerts-price">
          <SymbolLogo symbol={symbol} size={16} /><span className="sym">{symbol}</span>
          <span ref={priceRef} className={`num ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tk ? formatPrice(tk.last, digitsOf(symbol)) : '—'}</span>
        </span>
        <span className="ws-spacer" />
        {tab === 'alerts' ? (
          <>
            <button className={`ib${q != null ? ' on' : ''}`} title={L('جستجو', 'Search')} onClick={() => setQ(q == null ? '' : null)}><Icon name="ph-magnifying-glass" size="md" /></button>
            <button ref={sortRef} className="ib" title={L('مرتب‌سازی و پالایش', 'Sort and filter')} onClick={() => setMenu(menu === 'sort' ? null : 'sort')}><Icon name="ph-sort-descending" size="md" /></button>
          </>
        ) : null}
        <button ref={moreRef} className="ib" title={L('بیشتر', 'More')} onClick={() => setMenu(menu === 'more' ? null : 'more')}><Icon name="ph-dots-three" size="md" /></button>
      </div>
      {menu === 'sort' ? (
        <Dropdown anchor={sortRef.current} onClose={() => setMenu(null)} align="right" className="wl-more"><div dir={fa ? 'rtl' : 'ltr'}>
          <div className="menu-title fa">{L('مرتب‌سازی', 'Sort by')}</div>
          {(['created', 'name', 'symbol', 'status'] as Sort[]).map((k) => (
            <label key={k} className="menu-item check"><input type="radio" name="al-sort" checked={sort === k} onChange={() => setSort(k)} /><span>{k === 'created' ? L('تازه‌ترین', 'Newest') : k === 'name' ? L('نام', 'Name') : k === 'symbol' ? L('نماد', 'Symbol') : L('وضعیت', 'Status')}</span></label>
          ))}
          <div className="menu-sep" />
          <div className="menu-title fa">{L('نمایش', 'Show')}</div>
          {(['all', 'active', 'inactive', 'fired'] as Filter[]).map((k) => (
            <label key={k} className="menu-item check"><input type="radio" name="al-filter" checked={filter === k} onChange={() => setFilter(k)} /><span>{k === 'all' ? L('همه', 'All') : k === 'active' ? L('فعال', 'Active') : k === 'inactive' ? L('متوقف', 'Stopped') : L('رخ‌داده', 'Triggered')}</span></label>
          ))}
        </div></Dropdown>
      ) : null}
      {menu === 'more' ? (
        <Dropdown anchor={moreRef.current} onClose={() => setMenu(null)} align="right" className="wl-more"><div dir={fa ? 'rtl' : 'ltr'}>
          <button className="menu-item" disabled={!activeN} onClick={() => { setMenu(null); st.stopAll(); }}><Icon name="ph-pause" size="sm" /><span className="grow">{L('توقف همه', 'Stop all')}</span></button>
          <button className="menu-item" disabled={!inactiveN} onClick={() => { setMenu(null); st.restartInactive(); }}><Icon name="ph-play" size="sm" /><span className="grow">{L('راه‌اندازی دوباره‌ی همه‌ی متوقف‌ها', 'Restart all inactive')}</span></button>
          <button className="menu-item danger" disabled={!inactiveN} onClick={() => { setMenu(null); if (window.confirm(L('همه‌ی هشدارهای متوقف حذف شوند؟', 'Delete all inactive alerts?'))) st.deleteInactive(); }}><Icon name="ph-trash" size="sm" /><span className="grow">{L('حذف همه‌ی متوقف‌ها', 'Delete all inactive')}</span></button>
          <div className="menu-sep" />
          <button className="menu-item" disabled={!st.log.length} onClick={() => { setMenu(null); st.clearLog(); }}><Icon name="ph-trash" size="sm" /><span className="grow">{L('پاک کردن گزارش', 'Clear log')}</span></button>
          <button className="menu-item" data-act="webhooks" onClick={() => { setMenu(null); setHooks(true); }}><Icon name="ph-plugs-connected" size="sm" /><span className="grow">{L('وب‌هوک', 'Webhooks')}</span></button>
          {'Notification' in window && Notification.permission !== 'granted' ? <button className="menu-item" onClick={() => { setMenu(null); void Notification.requestPermission(); }}><Icon name="ph-bell-ringing" size="sm" /><span className="grow">{L('اجازه‌ی اعلان مرورگر', 'Allow browser notifications')}</span></button> : null}
        </div></Dropdown>
      ) : null}
      {q != null && tab === 'alerts' ? (
        <label className="wl-add-field alerts-search"><Icon name="search" size="sm" /><input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجوی هشدار', 'Search alerts')} onKeyDown={(e) => { if (e.key === 'Escape') setQ(null); }} /></label>
      ) : null}
      <div className="panel-body">
        {tab === 'log' ? (
          !st.log.length ? <div className="empty fa">{L('هنوز هشداری رخ نداده است.', 'No alert has fired yet.')}</div> : st.log.map((e) => (
            <button key={e.id} className="alert-log" onClick={() => setSymbol(e.symbol)} title={new Date(e.at).toLocaleString(fa ? 'fa-IR' : 'en-GB')}>
              <Icon name="ph-bell-ringing" size="sm" />
              <span className="alert-log-main">
                <b className={fa ? 'fa' : ''} dir="auto">{e.name}</b>
                <span className="muted" dir="auto">{e.message}</span>
              </span>
              <span className={`muted ${fa ? 'fa' : 'num'}`}>{fa ? faAgo(e.at) : dateTime(e.at).slice(5)}</span>
            </button>
          ))
        ) : (
          <>
            {!st.list.length && !remote.length ? (
              <div className="alerts-empty">
                <Icon name="ph-alarm" className="big" />
                <p className="fa">{L('هشدارها همین که شرط شما برقرار شود خبرتان می‌کنند. برای شروع یکی بسازید.', 'Alerts notify you instantly when your conditions are met. Create one to get started.')}</p>
                <button className="btn btn-primary sm" onClick={onNew}><span className="fa">{L('ساخت هشدار', 'Create alert')}</span></button>
              </div>
            ) : null}
            {st.list.length && !shown.length ? <div className="empty fa">{t('nothingFound')}</div> : null}
            {shown.map((a) => <AlertRow key={a.id} a={a} onEdit={() => setEdit(a)} onHistory={() => setHistory(a)} />)}
            {remote.length ? <div className="alerts-sub fa">{L('هشدارهای حساب (گوشی و تلگرام)', 'On your account (phone and Telegram)')}</div> : null}
            {remote.map((a) => (
              <div key={a.backend + a.id} className={`alert-row${a.active ? '' : ' off'}`}>
                <button className="alert-main" onClick={() => setSymbol(a.symbol)}>
                  <SymbolLogo symbol={a.symbol} size={18} />
                  <span className="alert-text">
                    <b className="sym">{a.symbol}</b>
                    <span className="alert-cond num">{({ above: '≥', below: '≤', cross_up: '↗', cross_down: '↘', cross: '⇄' } as Record<string, string>)[a.condition] ?? a.condition} {a.value ?? ''}</span>
                  </span>
                </button>
                <span className="alert-sub fa">
                  <span className="badge acct">{L('حساب', 'Account')}</span>
                  <span className="badge">{a.trigger === 'recurring' ? L('هر بار', 'Every time') : L('یک بار', 'Once')}</span>
                  {a.lastAt ? <span className="badge fired">{L('رخ داد', 'Triggered')}</span> : null}
                  {a.expiresAt && a.expiresAt < Date.now() ? <span className="badge">{L('منقضی', 'Expired')}</span> : null}
                </span>
                <span className="alert-tools">
                  <button className="lt" onClick={() => void toggleServer(a)} title={a.active ? L('توقف', 'Stop') : L('ادامه', 'Restart')}><Icon name={a.active ? 'ph-pause' : 'ph-play'} size="sm" /></button>
                  <button className="lt" onClick={() => { if (window.confirm(L('این هشدار حذف شود؟', 'Delete this alert?'))) void deleteServer(a); }} title={t('remove')}><Icon name="ph-trash" size="sm" /></button>
                </span>
              </div>
            ))}
          </>
        )}
      </div>
      <QuickTrade />
      {edit ? <AlertDialog edit={edit} onClose={() => setEdit(null)} /> : null}
      {hooks ? <WebhookSheet onClose={() => setHooks(false)} /> : null}
      {history ? <AlertAuditDialog alert={history} onClose={() => setHistory(null)} /> : null}
    </section>
  );
}

function AlertRow({ a, onEdit, onHistory }: { a: PriceAlert; onEdit(): void; onHistory(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const st = useAlerts();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const d = digitsOf(a.symbol);
  const recent = a.triggeredAt && Date.now() - a.triggeredAt < 86_400_000;
  const status = a.expired ? L('منقضی', 'Expired') : a.active ? L('فعال', 'Active') : a.triggeredAt ? L('رخ داد', 'Triggered') : L('متوقف', 'Stopped');
  return (
    <div className={`alert-row${a.active ? '' : ' off'}`} onDoubleClick={onEdit}>
      <button className="alert-main" onClick={() => setSymbol(a.symbol)}>
        <SymbolLogo symbol={a.symbol} size={18} />
        <span className="alert-text">
          <b className={a.name ? (fa ? 'fa' : '') : 'sym'} dir="auto">{a.name || a.symbol}</b>
          <span className="alert-cond" dir="auto">{describeAlert(a, fa, (v) => formatPrice(v, d))}</span>
        </span>
        <AlertDrawingMark a={a} />
      </button>
      <span className="alert-sub fa">
        <span className={`badge st ${a.active ? 'on' : a.expired ? 'exp' : 'off'}`}>{status}</span>
        {uncheckedIds([a], st.checkedAt).has(a.id) ? <span className="badge unchecked" title={L('هنوز هیچ قیمتی با این هشدار سنجیده نشده است', 'No price has been checked against this alert yet')}>{L('هنوز بررسی نشده', 'Not checked yet')}</span> : null}
        <span className="badge">{FREQ_NAMES[a.freq ?? 'once'][fa ? 'fa' : 'en']}</span>
        {a.untilSeen ? <span className="badge until-seen">{L('تا وقتی ببینمش', 'Until I see it')}</span> : null}
        {extraConditions(a).length ? <span className="badge num" title={L('همه‌ی شرط‌ها باید برقرار باشند', 'All conditions must hold')}>{L(`${faNum(extraConditions(a).length + 1)} شرط`, `${extraConditions(a).length + 1} conditions`)}</span> : null}
        {a.interval ? <span className="badge num">{intervalLabel(a.interval)}</span> : null}
        {a.serverId ? <span className="badge acct">{L('حساب', 'Account')}</span> : null}
        {a.group ? <span className="badge">{L('فهرست', 'List')}</span> : null}
        {recent ? <span className="badge fired">{L('رخ داد', 'Triggered')} · {fa ? faAgo(a.triggeredAt!) : dateTime(a.triggeredAt!).slice(11)}</span> : null}
        {a.fires && a.fires > 1 ? <span className="badge num">×{a.fires}</span> : null}
        {a.expiresAt && !a.expired ? <span className="badge num" title={L('انقضا', 'Expires')}>⏱ {dateTime(a.expiresAt).slice(5)}</span> : null}
      </span>
      <span className="alert-tools">
        <button className="lt" onClick={onEdit} title={L('ویرایش', 'Edit')}><Icon name="ph-pencil-simple" size="sm" /></button>
        <button className="lt" onClick={() => st.toggle(a.id)} title={a.active ? L('توقف', 'Stop') : L('ادامه', 'Restart')}><Icon name={a.active ? 'ph-pause' : 'ph-play'} size="sm" /></button>
        <button className="lt" onClick={() => st.clone(a.id)} title={L('همسان‌سازی', 'Clone')}><Icon name="ph-copy" size="sm" /></button>
        <button className="lt" onClick={() => st.remove(a.id)} title={t('remove')}><Icon name="ph-trash" size="sm" /></button>
        {a.untilSeen && a.active && a.lastFiredAt && !a.seenAt ? <button className="lt seen" data-act="seen" onClick={() => st.acknowledge(a.id)} title={L('دیدم', 'Seen')}><span className="fa">{L('دیدم', 'Seen')}</span></button> : null}
        <button className="lt" data-act="history" onClick={onHistory} title={L('کارنامه‌ی هشدار', 'What this alert did')}><Icon name="ph-clock-counter-clockwise" size="sm" /></button>
      </span>
    </div>
  );
}

/** Buy and sell under the alerts, a paper order at the market (the old Pro-Chart's quick trade). */
function QuickTrade() {
  const L = useL();
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const [qty, setQty] = useState('1');
  const [msg, setMsg] = useState<string | null>(null);
  const q = Number(qty);
  const go = (side: 'long' | 'short') => {
    if (!tk || !(q > 0)) return;
    const r = submitOrder({ symbol, side, type: 'market', qty: q, last: tk.last });
    setMsg(r.live ? L('به معامله‌ی زنده سپرده شد.', 'Handed to live trading.') : r.id ? L('پر شد (آزمایشی).', 'Filled (paper).') : L('ثبت نشد.', 'Not placed.'));
    setTimeout(() => setMsg(null), 2500);
  };
  return (
    <div className="quick-trade">
      <button className="btn buy sm" disabled={!tk || !(q > 0)} onClick={() => go('long')}><span className="fa">{L('خرید', 'Buy')}</span></button>
      <input className="num" value={qty} onChange={(e) => setQty(e.target.value)} inputMode="decimal" aria-label={L('مقدار', 'Quantity')} />
      <button className="btn sell sm" disabled={!tk || !(q > 0)} onClick={() => go('short')}><span className="fa">{L('فروش', 'Sell')}</span></button>
      {msg ? <span className="quick-msg fa">{msg}</span> : null}
    </div>
  );
}

// ── The dialog ─────────────────────────────────────────────────────────────────────────────────
const PRICE_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below', 'enter_channel', 'exit_channel', 'inside_channel', 'outside_channel', 'move_up_pct', 'move_down_pct', 'move_up', 'move_down', 'change24_over', 'change24_under'];
/** What each condition after the first may be: a level against the same price. */
const EXTRA_CONDS: AlertCondition[] = ['above', 'below', 'cross', 'cross_up', 'cross_down'];
const LINE_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below'];
/** A shape (rectangle, channel): entering and leaving it, and crossing either boundary. */
const BAND_CONDS: AlertCondition[] = ['enter_channel', 'exit_channel', 'inside_channel', 'outside_channel', 'cross', 'cross_up', 'cross_down'];
const IND_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below', 'enter_channel', 'exit_channel', 'inside_channel', 'outside_channel'];
const LIST_CONDS: AlertCondition[] = ['move_up_pct', 'move_down_pct', 'change24_over', 'change24_under'];
const SCRIPT_CONDS: AlertCondition[] = ['above'];
const FIELDS: { id: PriceField; fa: string; en: string }[] = [
  { id: 'last', fa: 'آخرین (میانه)', en: 'Last (mid)' }, { id: 'bid', fa: 'خرید (Bid)', en: 'Bid' }, { id: 'ask', fa: 'فروش (Ask)', en: 'Ask' },
  { id: 'close', fa: 'بسته شدن', en: 'Close' }, { id: 'high', fa: 'سقف کندل', en: 'High' }, { id: 'low', fa: 'کف کندل', en: 'Low' },
];
const PLACEHOLDERS = ['{{ticker}}', '{{close}}', '{{open}}', '{{high}}', '{{low}}', '{{volume}}', '{{interval}}', '{{time}}', '{{timenow}}', '{{plot_0}}'];
const ALERT_TFS = ['1', '5', '15', '30', '60', '240', '1D', '1W'];

const toLocalInput = (msv: number) => { const d = new Date(msv - new Date().getTimezoneOffset() * 60_000); return d.toISOString().slice(0, 16); };

export function AlertDialog({ onClose, price: initialPrice, seed, edit }: { onClose(): void; price?: number; seed?: AlertSeed; edit?: PriceAlert }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const chartSymbol = usePrefs((s) => s.symbol);
  const chartInterval = usePrefs((s) => s.interval);
  const studies = useStudies((s) => s.list);
  const wl = useWatchlist();
  const st = useAlerts();
  const sessions = useSession((s) => s.sessions);

  const listId = edit ? null : seed?.list ?? null;
  const listSymbols = listId ? (seed?.symbols ?? wl.lists.find((l) => l.id === listId)?.symbols ?? []) : [];
  const [symbol, setSym] = useState(edit?.symbol ?? seed?.symbol ?? chartSymbol);
  const [scope, setScope] = useState<'symbol' | 'list'>(listId ? 'list' : 'symbol');
  const tk = useMarket((s) => s.tickers[symbol]);
  const digits = instrumentOf(symbol, tk?.last).digits;
  const quote = useQuote(symbol);
  const { ref: headRef, tick } = useFlash<HTMLSpanElement>(tk?.last);
  const study = seed?.study ? studies.find((s) => s.uid === seed.study!.uid) : undefined;
  const fmt = (v: number) => (Number.isFinite(v) ? v.toFixed(digits) : '');

  const start = edit?.value ?? seed?.price ?? initialPrice ?? tk?.last ?? 0;
  // A drawing alert: the drawing as it is now (the alert follows it when it is edited).
  const drawingId = edit?.drawingId ?? seed?.drawingId;
  const drawingType = edit?.drawingType ?? seed?.drawingType;
  const isBand = Boolean(drawingType && BAND_ALERT_TYPES.includes(drawingType));
  const drawing = drawingId ? findDrawing(symbol, drawingId) : null;
  const nowBounds = drawing ? boundsAt(drawing, Date.now() / 1000) : [];
  const bandNow = isBand ? (nowBounds.length === 2 ? { low: nowBounds[0]!, high: nowBounds[1]! } : seed?.band ?? (edit?.high != null ? { low: edit.value, high: edit.high } : undefined)) : undefined;
  const drawingLabel = drawingType ? toolName(drawingType, lang) : L('خط روند', 'Trend line');
  const script = edit?.script ?? (seed?.script ? { source: seed.script.source, condition: seed.script.condition, name: seed.script.name, inputs: seed.script.inputs } : undefined);
  const initialSource: 'price' | 'line' | 'indicator' | 'script' = edit?.source ?? (script ? 'script' : seed?.study ? 'indicator' : seed?.line || seed?.band ? 'line' : 'price');
  const [source, setSource] = useState<'price' | 'line' | 'indicator' | 'script'>(initialSource);
  const [field, setField] = useState<PriceField>(edit?.field ?? 'last');
  const [cond, setCond] = useState<AlertCondition>(edit?.condition ?? (script ? 'above' : listId ? 'move_up_pct' : seed?.band ? 'enter_channel' : (initialPrice ?? seed?.price) != null && tk && (initialPrice ?? seed?.price)! < tk.last ? 'below' : 'cross'));
  const [value, setValue] = useState(edit ? String(edit.value) : listId ? '3' : start ? fmt(start) : '');
  const [high, setHigh] = useState(edit?.high != null ? String(edit.high) : start ? fmt(start * 1.01) : '');
  const [bars, setBars] = useState(String(edit?.bars ?? 4));
  const [interval, setIv] = useState(edit?.interval ?? seed?.script?.interval ?? (ALERT_TFS.includes(chartInterval) ? chartInterval : '60'));
  const [line] = useState<TrendLine | undefined>(edit?.line ?? seed?.line);
  const [indKey, setIndKey] = useState(edit?.indicator?.key ?? study?.key ?? seed?.study?.key ?? 'rsi');
  const [indInputs] = useState(edit?.indicator?.inputs ?? study?.inputs);
  const [outputs, setOutputs] = useState<{ id: string; label: string; last: number | null }[]>([]);
  const [indOut, setIndOut] = useState(edit?.indicator?.output ?? '');
  // The conditions after the first (all must hold), up to MAX_CONDITIONS in all.
  const [extras, setExtras] = useState<{ condition: AlertCondition; value: string }[]>(() => (edit ? extraConditions(edit) : []).map((o) => ({ condition: o.condition, value: String(o.value) })));
  const [freq, setFreq] = useState<AlertFreq>(edit?.freq ?? (script ? 'per_bar' : 'once'));
  const [untilSeen, setUntilSeen] = useState(Boolean(edit?.untilSeen));
  const [repeatMinutes, setRepeatMinutes] = useState(edit?.repeatMinutes ?? DEFAULT_REPEAT_MINUTES);
  const hookTargets = useWebhooks((h) => h.targets);
  const [hookSel, setHookSel] = useState<string[] | null>(edit?.webhooks ?? null);
  const [hookSheet, setHookSheet] = useState(false);
  const [cooldown, setCooldown] = useState(String(Math.round((edit?.cooldownMs ?? 60_000) / 60_000)));
  const [expires, setExpires] = useState(edit?.expiresAt ? toLocalInput(edit.expiresAt) : '');
  const [name, setName] = useState(edit?.name ?? '');
  const [message, setMessage] = useState(edit?.message ?? '');
  const [sound, setSound] = useState<SoundId>(edit?.sound ?? 'chime');
  const [soundSecs, setSoundSecs] = useState(edit?.soundSecs ?? 0);
  const [popup, setPopup] = useState(edit?.popup ?? true);
  const [tab, setTab] = useState<'settings' | 'message' | 'notify'>('settings');
  const [pick, setPick] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [presetMenu, setPresetMenu] = useState(false);
  const presetRef = useRef<HTMLButtonElement>(null);
  const msgRef = useRef<HTMLTextAreaElement>(null);

  // The indicator's outputs and their last values on this timeframe.
  useEffect(() => {
    if (source !== 'indicator') return;
    let alive = true;
    void barsFor(symbol, interval).then((b) => {
      if (!alive) return;
      const outs = indicatorSeries(indKey, b, indInputs).map((o) => ({ id: o.id, label: o.label, last: [...o.values].reverse().find((v) => v != null && Number.isFinite(v)) ?? null }));
      setOutputs(outs);
      const want = edit?.indicator?.output ?? (seed?.study?.plot != null ? outs[seed.study.plot]?.id : undefined);
      setIndOut((cur) => (outs.some((o) => o.id === cur) ? cur : want && outs.some((o) => o.id === want) ? want : outs[0]?.id ?? ''));
      if (!edit && !seed?.price && outs[0]?.last != null) setValue((v) => (v && source === 'indicator' && v !== fmt(start) ? v : outs[0]!.last!.toFixed(2)));
    });
    return () => { alive = false; };
  }, [source, symbol, interval, indKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const conds = source === 'script' ? SCRIPT_CONDS : scope === 'list' ? LIST_CONDS : source === 'line' ? (isBand ? BAND_CONDS : LINE_CONDS) : source === 'indicator' ? IND_CONDS : PRICE_CONDS;
  useEffect(() => { if (!conds.includes(cond)) setCond(conds[0]!); }, [conds, cond]);
  const isChannel = CHANNEL.includes(cond);
  const isMove = MOVES.includes(cond);
  const isChange = CHANGE24.includes(cond);
  const v = Number(value);
  const lineNow = isBand ? null : line ? lineLevel(line, Date.now() / 1000) : null;
  // A horizontal drawing: its price now, which the alert keeps following.
  const levelNow = drawingId && !isBand && !line && nowBounds.length === 1 ? nowBounds[0]! : null;
  const linkedPrice = source === 'price' && drawingId != null && !isMove && levelNow != null;
  const target = source === 'line' ? lineNow : isMove || isChange || source === 'script' ? null : linkedPrice ? levelNow : v;
  const pip = symbol.length === 6 && !/^X(AU|AG)/.test(symbol) ? (symbol.includes('JPY') ? 0.01 : 0.0001) : null;
  const dist = source !== 'indicator' && target && tk ? { d: target - tk.last, pct: ((target - tk.last) / tk.last) * 100 } : null;
  const indName = (key: string) => { const e = entryOf(key); return e ? (fa ? e.fa : e.en) : key; };
  const condName = (c: AlertCondition) => CONDITION_NAMES[c][fa ? 'fa' : 'en'];
  const draft = (): NewAlert | null => {
    if (source === 'line' && !line && !bandNow) return null;
    const bandLine = source === 'line' && bandNow;
    const base: NewAlert = {
      symbol, condition: cond, value: bandLine ? bandNow.low : source === 'line' ? (lineNow ?? 0) : linkedPrice ? levelNow! : v, source, interval, freq,
      field: source === 'price' ? field : undefined,
      high: bandLine ? bandNow.high : isChannel ? Number(high) : undefined, bars: isMove ? Math.max(1, Math.round(Number(bars))) : undefined,
      line: source === 'line' && !bandLine ? line : undefined,
      drawingId: drawingId && source !== 'indicator' && (source === 'line' || linkedPrice) ? drawingId : undefined,
      drawingType: drawingId && source !== 'indicator' && (source === 'line' || linkedPrice) ? drawingType : undefined,
      indicator: source === 'indicator' ? { key: indKey, output: indOut, inputs: indInputs, label: `${entryOf(indKey)?.abbr ?? indKey}${outputs.length > 1 ? ` ${outputs.find((o) => o.id === indOut)?.label ?? ''}` : ''}` } : undefined,
      script: source === 'script' ? script : undefined,
      ands: !isChannel && source !== 'script' && scope === 'symbol' && extras.some((x) => x.value !== '') ? extras.filter((x) => x.value !== '' && Number.isFinite(Number(x.value))).map((x) => ({ condition: x.condition, value: Number(x.value) })) : undefined,
      and: undefined,
      untilSeen: untilSeen || undefined,
      repeatMinutes: untilSeen ? repeatMinutes : undefined,
      webhooks: hookSel ?? undefined,
      cooldownMs: freq === 'every' ? Math.max(0, Number(cooldown) || 0) * 60_000 : undefined,
      expiresAt: expires ? Date.parse(expires) : undefined,
      name: name.trim() || undefined, message: message.trim() || undefined, sound, soundSecs, popup,
    };
    return base;
  };
  const autoName = () => { const d = draft(); return d ? describeAlert(d, fa, (x) => formatPrice(x, digits)) : symbol; };

  const create = () => {
    setErr(null);
    const d = draft();
    if (!d) return;
    if (source !== 'line' && source !== 'script' && (!Number.isFinite(d.value) || (d.value <= 0 && source === 'price' && !isMove))) { setErr(L('مقدار را درست وارد کنید.', 'Enter a valid value.')); setTab('settings'); return; }
    if (isChannel && !(Number.isFinite(d.high!) && d.high !== d.value)) { setErr(L('کف و سقف کانال را وارد کنید.', 'Enter the channel low and high.')); setTab('settings'); return; }
    if (source === 'indicator' && !indOut) { setErr(L('خروجی اندیکاتور را انتخاب کنید.', 'Pick the indicator output.')); return; }
    if (d.expiresAt && d.expiresAt < Date.now()) { setErr(L('زمان انقضا گذشته است.', 'The expiry is in the past.')); return; }
    if (edit) st.update(edit.id, { ...d, active: true, expired: undefined, triggeredAt: undefined, seenAt: undefined });
    else if (scope === 'list') {
      const group = `wl-${Date.now().toString(36)}`;
      for (const s of listSymbols) st.create({ ...d, symbol: s, group, name: d.name ? `${d.name} · ${s}` : undefined });
    } else st.create(d);
    try { if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission(); } catch { /* ignore */ }
    onClose();
  };
  const applyPreset = (p: Partial<PriceAlert>) => {
    if (p.condition && conds.includes(p.condition)) setCond(p.condition);
    if (p.freq) setFreq(p.freq);
    if (p.interval) setIv(p.interval);
    if (p.field) setField(p.field);
    if (p.bars) setBars(String(p.bars));
    if (p.sound) setSound(p.sound);
    if (p.soundSecs != null) setSoundSecs(p.soundSecs);
    if (p.message != null) setMessage(p.message);
    if (p.cooldownMs != null) setCooldown(String(Math.round(p.cooldownMs / 60_000)));
    if (p.condition && MOVES.includes(p.condition) && p.value != null) setValue(String(p.value));
  };
  const insert = (ph: string) => {
    const el = msgRef.current;
    if (!el) { setMessage((m) => m + ph); return; }
    const a = el.selectionStart ?? message.length, b = el.selectionEnd ?? message.length;
    setMessage(message.slice(0, a) + ph + message.slice(b));
    requestAnimationFrame(() => { el.focus(); el.setSelectionRange(a + ph.length, a + ph.length); });
  };
  const onServer = Boolean(sessions[backendOf(symbol)]) && (backendOf(symbol) === 'tradeyar' || FX_SYMBOLS.includes(symbol)) && source === 'price' && ['above', 'below', 'cross', 'cross_up', 'cross_down'].includes(cond) && !extras.some((x) => x.value !== '') && field === 'last' && !untilSeen && freq !== 'daily';

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog settings alert-dlg" role="dialog" aria-modal="true" aria-label={t('alertAdd')} dir={fa ? 'rtl' : 'ltr'}
        onKeyDown={(e) => { e.stopPropagation(); if (e.key === 'Escape') onClose(); if (e.key === 'Enter' && !(e.target as HTMLElement).closest('textarea, select, button')) create(); }}>
        <header className="dialog-head">
          <h2 className={`alert-dlg-title${fa ? ' fa' : ''}`}>
            <span>{edit ? L('ویرایش هشدار', 'Edit alert on') : L('هشدار روی', 'Create alert on')}</span>
            {scope === 'list' ? <b className="fa">{L('فهرست', 'list')} «{wl.lists.find((l) => l.id === listId)?.name}» <span className="num muted">({fa ? faNum(listSymbols.length) : listSymbols.length})</span></b> : <b className="sym">{symbol}</b>}
          </h2>
          <span className="alert-dlg-quote">
            <span className="num muted">{intervalLabel(interval)}</span>
            {scope === 'symbol' ? <span ref={headRef} className={`num ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tk ? formatPrice(tk.last, digits) : '—'}</span> : null}
          </span>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <nav className="alert-tabs" role="tablist">
          {(['settings', 'message', 'notify'] as const).map((k) => (
            <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>
              <span className="fa">{k === 'settings' ? L('تنظیمات', 'Settings') : k === 'message' ? L('پیام', 'Message') : L('اعلان‌ها', 'Notifications')}</span>
            </button>
          ))}
          <span className="ws-spacer" />
          <button ref={presetRef} className="link-btn fa" onClick={() => setPresetMenu((m) => !m)}>{L('پیش‌تنظیم‌ها', 'Presets')}</button>
        </nav>
        {presetMenu ? (
          <Dropdown anchor={presetRef.current} onClose={() => setPresetMenu(false)} align="right" className="wl-more"><div dir={fa ? 'rtl' : 'ltr'}>
            <button className="menu-item" onClick={() => { setPresetMenu(false); const n = window.prompt(L('نام پیش‌تنظیم', 'Preset name'), autoName()); const d = draft(); if (n && d) { const { symbol: _s, line: _l, indicator: _i, script: _sc, value, ...rest } = d; st.savePreset(n, { ...rest, value: isMove ? value : undefined }); } }}><Icon name="ph-floppy-disk" size="sm" /><span className="grow">{L('ذخیره به‌عنوان پیش‌تنظیم…', 'Save as preset…')}</span></button>
            <div className="menu-sep" />
            <button className="menu-item" onClick={() => { setPresetMenu(false); applyPreset({ condition: 'move_up_pct', value: 2, bars: 1, freq: 'per_bar' }); }}><span className="grow">{L('رشد ۲٪ در یک کندل', 'Up 2% in one bar')}</span></button>
            <button className="menu-item" onClick={() => { setPresetMenu(false); applyPreset({ condition: 'move_down_pct', value: 2, bars: 1, freq: 'per_bar' }); }}><span className="grow">{L('افت ۲٪ در یک کندل', 'Down 2% in one bar')}</span></button>
            <button className="menu-item" onClick={() => { setPresetMenu(false); applyPreset({ condition: 'cross', freq: 'per_bar_close' }); }}><span className="grow">{L('عبور، روی بسته شدن کندل', 'Crossing, on bar close')}</span></button>
            {st.presets.length ? <div className="menu-sep" /> : null}
            {st.presets.map((p) => (
              <div key={p.name} className="menu-item">
                <button className="grow" onClick={() => { setPresetMenu(false); applyPreset(p.settings); }}>{p.name}</button>
                <button className="lt" aria-label={t('remove')} onClick={() => st.deletePreset(p.name)}><Icon name="ph-x" size="sm" /></button>
              </div>
            ))}
          </div></Dropdown>
        ) : null}
        <div className="settings-body alert-body">
          {tab === 'settings' ? (
            <>
              <div className="al-row">
                <span className="al-label fa">{L('نماد', 'Symbols')}</span>
                <div className="al-field">
                  {listId ? (
                    <select value={scope} onChange={(e) => setScope(e.target.value as 'symbol' | 'list')}>
                      <option value="list">{L('همه‌ی نمادهای فهرست', 'Every symbol of the list')} ({fa ? faNum(listSymbols.length) : listSymbols.length})</option>
                      <option value="symbol">{symbol}</option>
                    </select>
                  ) : (
                    <button type="button" className="al-sym" onClick={() => setPick(true)} disabled={Boolean(edit)}><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span><Icon name="ph-caret-down" size="sm" /></button>
                  )}
                </div>
              </div>
              {source === 'script' && script ? (
                <div className="al-row" data-script-cond>
                  <span className="al-label fa">{L('اسکریپت', 'Script')}</span>
                  <div className="al-field"><span className="al-static"><b dir="auto">{script.name}</b> · <span dir="auto">{script.condition}</span> <span className="muted fa">{L('(روی کندل آخر)', '(on the last bar)')}</span></span></div>
                </div>
              ) : scope === 'symbol' ? (
                <div className="al-row">
                  <span className="al-label fa">{L('منبع', 'Condition')}</span>
                  <div className="al-field two">
                    <select value={source} onChange={(e) => setSource(e.target.value as typeof source)} aria-label={L('منبع', 'Source')}>
                      <option value="price">{L(`قیمت ${symbol}`, `${symbol} price`)}</option>
                      {line || bandNow ? <option value="line">{drawingLabel}</option> : null}
                      <option value="indicator">{L('اندیکاتور', 'Indicator')}</option>
                    </select>
                    {source === 'price' ? (
                      <select value={field} onChange={(e) => setField(e.target.value as PriceField)} aria-label={L('کدام قیمت', 'Price')}>
                        {FIELDS.filter((f) => (f.id !== 'bid' && f.id !== 'ask') || backendOf(symbol) === 'tradeyar').map((f) => <option key={f.id} value={f.id}>{fa ? f.fa : f.en}</option>)}
                      </select>
                    ) : null}
                  </div>
                </div>
              ) : null}
              {source === 'indicator' && scope === 'symbol' ? (
                <div className="al-row">
                  <span className="al-label fa">{L('اندیکاتور', 'Indicator')}</span>
                  <div className="al-field two">
                    <select value={indKey} onChange={(e) => setIndKey(e.target.value)} aria-label={L('اندیکاتور', 'Indicator')}>
                      {studies.filter((s) => entryOf(s.key)).length ? <optgroup label={L('روی چارت', 'On the chart')}>{[...new Set(studies.map((s) => s.key))].filter((k) => entryOf(k)).map((k) => <option key={`c${k}`} value={k}>{indName(k)}</option>)}</optgroup> : null}
                      <optgroup label={L('همه', 'All')}>{CATALOG.map((c) => <option key={c.key} value={c.key}>{fa ? c.fa : c.en}</option>)}</optgroup>
                    </select>
                    <select value={indOut} onChange={(e) => setIndOut(e.target.value)} aria-label={L('خروجی', 'Output')}>
                      {outputs.map((o) => <option key={o.id} value={o.id}>{o.label}{o.last != null ? ` (${o.last.toFixed(2)})` : ''}</option>)}
                    </select>
                  </div>
                </div>
              ) : null}
              {source !== 'script' ? (<>
              <div className="al-row">
                <span className="al-label" />
                <div className="al-field">
                  <select value={cond} onChange={(e) => setCond(e.target.value as AlertCondition)} aria-label={L('شرط', 'Operator')}>
                    {conds.map((c) => <option key={c} value={c}>{condName(c)}</option>)}
                  </select>
                </div>
              </div>
              <div className="al-row">
                <span className="al-label fa">{source === 'line' || linkedPrice ? L('سطح اکنون', 'Level now') : isMove ? L('اندازه', 'Amount') : isChange ? L('درصد', 'Percent') : isChannel ? L('کانال', 'Channel') : L('مقدار', 'Value')}</span>
                <div className={`al-field${(isChannel || isMove) && source !== 'line' && !linkedPrice ? ' three' : ''}`}>
                  {source === 'line' && bandNow ? <span className="num al-static" data-band>{formatPrice(bandNow.low, digits)} – {formatPrice(bandNow.high, digits)} <span className="muted fa">{L('(با ترسیم جابه‌جا می‌شود)', '(follows the drawing)')}</span></span>
                    : source === 'line' ? <span className="num al-static">{lineNow != null ? formatPrice(lineNow, digits) : '—'} <span className="muted fa">{drawingId ? L('(با ترسیم و با زمان جابه‌جا می‌شود)', '(follows the drawing and slides with time)') : L('(با زمان جابه‌جا می‌شود)', '(slides with time)')}</span></span>
                    : linkedPrice ? <span className="num al-static">{formatPrice(levelNow!, digits)} <span className="muted fa">{L('(با ترسیم جابه‌جا می‌شود)', '(follows the drawing)')}</span></span>
                    : <input type="number" className="num" step={(isMove && cond.endsWith('pct')) || isChange ? 0.1 : source === 'indicator' ? 0.01 : 1 / 10 ** digits} value={value} onChange={(e) => setValue(e.target.value)} autoFocus aria-label={L('مقدار', 'Value')} placeholder={isChannel ? L('کف', 'Low') : undefined} />}
                  {isChannel && source !== 'line' && !linkedPrice ? <input type="number" className="num" step={source === 'indicator' ? 0.01 : 1 / 10 ** digits} value={high} onChange={(e) => setHigh(e.target.value)} aria-label={L('سقف', 'High')} placeholder={L('سقف', 'High')} /> : null}
                  {isMove ? <label className="al-inline fa"><input type="number" className="num" min={1} value={bars} onChange={(e) => setBars(e.target.value)} aria-label={L('تعداد کندل', 'Bars')} /><span>{L('کندل', 'bars')}</span></label> : null}
                  {isChange ? <span className="muted num">%</span> : null}
                </div>
              </div>
              </>) : null}
              {dist && scope === 'symbol' ? (
                <div className="al-row"><span className="al-label" /><div className={`al-dist num ${dist.d >= 0 ? 'up' : 'down'}`}>
                  {dist.d >= 0 ? '+' : '−'}{formatPrice(Math.abs(dist.d), digits)} · {dist.pct >= 0 ? '+' : '−'}{Math.abs(dist.pct).toFixed(2)}%{pip ? ` · ${(Math.abs(dist.d) / pip).toFixed(1)} pips` : ''}
                  <span className="muted fa"> {L('تا قیمت فعلی', 'from the price now')}</span>
                  {source === 'price' && (field === 'bid' || field === 'ask') && quote ? <span className="muted"> · {field} {formatPrice(field === 'bid' ? quote.bid : quote.ask, digits)}</span> : null}
                </div></div>
              ) : null}
              {!isChannel && scope === 'symbol' && source !== 'script' ? (
                <>
                  {extras.map((x, i) => (
                    <div key={i} className="al-row al-extra" data-extra={i}>
                      <span className="al-label fa">{L('و', 'AND')}</span>
                      <div className="al-field and">
                        <select value={x.condition} onChange={(e) => setExtras(extras.map((y, j) => (j === i ? { ...y, condition: e.target.value as AlertCondition } : y)))} aria-label={L(`شرط ${faNum(i + 2)}`, `Condition ${i + 2}`)}>
                          {EXTRA_CONDS.map((c) => <option key={c} value={c}>{condName(c)}</option>)}
                        </select>
                        <input type="number" className="num" value={x.value} step={1 / 10 ** digits} onChange={(e) => setExtras(extras.map((y, j) => (j === i ? { ...y, value: e.target.value } : y)))} aria-label={L(`مقدار شرط ${faNum(i + 2)}`, `Value of condition ${i + 2}`)} />
                        <button type="button" className="lt" data-act="remove-cond" title={L('برداشتن این شرط', 'Remove this condition')} aria-label={L('برداشتن این شرط', 'Remove this condition')} onClick={() => setExtras(extras.filter((_, j) => j !== i))}><Icon name="ph-x" size="sm" /></button>
                      </div>
                    </div>
                  ))}
                  <div className="al-row">
                    <span className="al-label" />
                    <div className="al-field al-addcond">
                      {extras.length + 1 < MAX_CONDITIONS ? <button type="button" className="chip" data-act="add-cond" onClick={() => setExtras([...extras, { condition: 'below', value: tk ? fmt(tk.last) : '' }])}><span className="fa">{L('+ شرط', '+ Condition')}</span></button> : null}
                      <span className="muted small fa" data-cap>{L(`حداکثر ${faNum(MAX_CONDITIONS)} شرط، و همه با هم باید برقرار باشند`, `At most ${MAX_CONDITIONS} conditions, and all of them must hold`)}</span>
                    </div>
                  </div>
                </>
              ) : null}
              <div className="al-row">
                <span className="al-label fa">{L('بازه‌ی زمانی', 'Timeframe')}</span>
                <div className="al-field">
                  <select value={interval} onChange={(e) => setIv(e.target.value)} aria-label={L('بازه‌ی زمانی', 'Timeframe')}>
                    {INTERVALS.filter((i) => ALERT_TFS.includes(i.id)).map((i) => <option key={i.id} value={i.id}>{i.label}</option>)}
                  </select>
                </div>
              </div>
              <div className="al-row">
                <span className="al-label fa">{L('تکرار', 'Trigger')}</span>
                <div className="al-field chips-row">
                  {(Object.keys(FREQ_NAMES) as AlertFreq[]).map((f) => (
                    <button key={f} type="button" className={`chip${freq === f ? ' on' : ''}`} onClick={() => setFreq(f)}><span className="fa">{FREQ_NAMES[f][fa ? 'fa' : 'en']}</span></button>
                  ))}
                  {freq === 'every' ? <label className="al-inline fa"><span>{L('فاصله', 'Cool-down')}</span><input type="number" className="num" min={0} value={cooldown} onChange={(e) => setCooldown(e.target.value)} aria-label={L('فاصله (دقیقه)', 'Cool-down (minutes)')} /><span>{L('دقیقه', 'min')}</span></label> : null}
                </div>
              </div>
              <div className="al-row al-until">
                <span className="al-label fa">{L('تا کِی خبر بدهد', 'Keep telling me')}</span>
                <div className="al-field chips-row" data-until>
                  <button type="button" className={`chip${!untilSeen ? ' on' : ''}`} onClick={() => setUntilSeen(false)}><span className="fa">{L('یک بار کافی است', 'Once is enough')}</span></button>
                  <button type="button" className={`chip${untilSeen ? ' on' : ''}`} data-act="until-seen" onClick={() => setUntilSeen(true)}><span className="fa">{L('تا وقتی ببینمش', 'Until I see it')}</span></button>
                </div>
              </div>
              {untilSeen ? (
                <div className="al-row">
                  <span className="al-label" />
                  <div className="al-field chips-row" data-minutes>
                    {UNTIL_SEEN_MINUTES.map((m) => <button key={m} type="button" className={`chip${repeatMinutes === m ? ' on' : ''}`} onClick={() => setRepeatMinutes(m)}><span className="fa">{L(`${faNum(m)} دقیقه`, `${m} min`)}</span></button>)}
                    <p className="muted small fa al-note" data-until-note>{L(`هر ${faNum(repeatMinutes)} دقیقه برمی‌گردد و به‌محض باز کردنش یا زدن «دیدم» می‌ایستد.`, `It comes back every ${repeatMinutes} minutes and stops the moment you open it or press Seen.`)}</p>
                  </div>
                </div>
              ) : null}
              <div className="al-row">
                <span className="al-label fa">{L('انقضا', 'Expiration')}</span>
                <div className="al-field two">
                  <input type="datetime-local" value={expires} onChange={(e) => setExpires(e.target.value)} aria-label={L('انقضا', 'Expiration')} />
                  <select value="" onChange={(e) => { const h = Number(e.target.value); if (h) setExpires(toLocalInput(Date.now() + h * 3_600_000)); else if (e.target.value === '0') setExpires(''); }} aria-label={L('انقضای سریع', 'Quick expiry')}>
                    <option value="">{expires ? L('تغییر…', 'Change…') : L('بدون انقضا', 'Open-ended')}</option>
                    <option value="0">{L('بدون انقضا', 'Open-ended')}</option>
                    <option value="1">{L('یک ساعت', '1 hour')}</option><option value="24">{L('یک روز', '1 day')}</option>
                    <option value="168">{L('یک هفته', '1 week')}</option><option value="720">{L('یک ماه', '1 month')}</option>
                  </select>
                </div>
              </div>
              {onServer && scope === 'symbol' ? <p className="muted small fa al-note">{L('این هشدار در حساب شما هم ثبت می‌شود و روی گوشی و تلگرام می‌رسد.', 'This alert is also kept on your account and reaches the phone and Telegram.')}</p> : null}
            </>
          ) : null}
          {tab === 'message' ? (
            <>
              <label className="al-col">
                <span className="fa">{L('نام هشدار', 'Alert name')}</span>
                <input value={name} onChange={(e) => setName(e.target.value)} placeholder={autoName()} dir="auto" />
              </label>
              <label className="al-col">
                <span className="fa">{L('پیام', 'Message')}</span>
                <textarea ref={msgRef} rows={4} value={message} onChange={(e) => setMessage(e.target.value)} placeholder={`{{ticker}} ${autoName()} — {{close}}`} dir="auto" />
              </label>
              <div className="al-placeholders">
                <span className="muted fa">{L('افزودن متغیر', 'Add placeholder')}</span>
                {PLACEHOLDERS.map((ph) => <button key={ph} type="button" className="chip num" onClick={() => insert(ph)}>{ph}</button>)}
              </div>
            </>
          ) : null}
          {tab === 'notify' ? (
            <>
              <label className="al-check fa"><input type="checkbox" checked={popup} onChange={(e) => setPopup(e.target.checked)} /><span>{L('نمایش پنجره‌ی کوچک (پاپ‌آپ)', 'Show pop-up')}</span></label>
              <div className="al-check fa">
                <input type="checkbox" checked={sound !== 'none'} onChange={(e) => setSound(e.target.checked ? 'chime' : 'none')} aria-label={L('پخش صدا', 'Play sound')} />
                <span>{L('پخش صدا', 'Play sound')}</span>
                <select value={sound} onChange={(e) => setSound(e.target.value as SoundId)} aria-label={L('صدا', 'Sound')} disabled={sound === 'none'}>
                  {SOUNDS.filter((x) => x.id !== 'none').map((x) => <option key={x.id} value={x.id}>{fa ? x.fa : x.en}</option>)}
                </select>
                <select value={soundSecs} onChange={(e) => setSoundSecs(Number(e.target.value))} aria-label={L('مدت', 'Duration')} disabled={sound === 'none'}>
                  <option value={0}>{L('یک بار', 'Once')}</option><option value={3}>{L('۳ ثانیه', '3 seconds')}</option><option value={5}>{L('۵ ثانیه', '5 seconds')}</option><option value={10}>{L('۱۰ ثانیه', '10 seconds')}</option>
                </select>
                <button type="button" className="lt" title={L('پخش نمونه', 'Preview')} onClick={() => playSound(sound, Math.min(3, soundSecs))} disabled={sound === 'none'}><Icon name="ph-play" size="sm" /></button>
              </div>
              <div className="al-check fa">
                <Icon name="ph-bell-ringing" size="sm" />
                <span>{L('اعلان مرورگر', 'Browser notification')}</span>
                <span className="muted">{'Notification' in window ? (Notification.permission === 'granted' ? L('روشن', 'on') : <button type="button" className="link-btn" onClick={() => void Notification.requestPermission()}>{L('اجازه دهید', 'Allow')}</button>) : L('در دسترس نیست', 'unavailable')}</span>
              </div>
              <div className="al-hooks" data-hooks>
                <div className="al-check fa"><Icon name="ph-plugs-connected" size="sm" /><span>{L('وب‌هوک', 'Webhooks')}</span>
                  <button type="button" className="link-btn" onClick={() => setHookSheet(true)}>{hookTargets.length ? L('مدیریت', 'Manage') : L('وب‌هوک تازه', 'New webhook')}</button>
                </div>
                {hookTargets.length ? (
                  <>
                    <label className="al-check fa"><input type="checkbox" checked={hookSel == null} onChange={(e) => setHookSel(e.target.checked ? null : hookTargets.filter((t) => t.enabled).map((t) => t.id))} /><span>{L('همه‌ی وب‌هوک‌های روشن', 'Every webhook that is on')}</span></label>
                    {hookSel != null ? hookTargets.map((t) => (
                      <label key={t.id} className="al-check fa al-hook"><input type="checkbox" checked={hookSel.includes(t.id)} disabled={!t.enabled} onChange={(e) => setHookSel(e.target.checked ? [...hookSel, t.id] : hookSel.filter((x) => x !== t.id))} /><span dir="auto">{t.name}</span>{!t.enabled ? <span className="muted">{L('خاموش', 'Off')}</span> : null}</label>
                    )) : null}
                  </>
                ) : <p className="muted small fa">{L('هر هشداری که اجرا شود، به این نشانی‌ها هم فرستاده می‌شود.', 'Every alert that fires is also posted to these addresses.')}</p>}
              </div>
              <p className="muted small fa">{onServer ? L('روی گوشی (پوش) و تلگرام هم می‌رسد، چون در حساب شما ثبت می‌شود.', 'Also reaches the phone (push) and Telegram, as it is kept on your account.') : L('این هشدار در همین مرورگر پایش می‌شود، تا وقتی صفحه باز است.', 'Watched in this browser while the page is open.')}</p>
            </>
          ) : null}
          {err ? <p className="error fa">{err}</p> : null}
        </div>
        <footer className="dialog-foot">
          <span className="ws-spacer" />
          <button className="btn btn-quiet" onClick={onClose}><span className="fa">{L('انصراف', 'Cancel')}</span></button>
          <button className="btn btn-primary" onClick={create}><span className="fa">{edit ? L('ذخیره', 'Save') : scope === 'list' ? L(`ساخت ${faNum(listSymbols.length)} هشدار`, `Create ${listSymbols.length} alerts`) : t('alertCreate')}</span></button>
        </footer>
      </div>
      {hookSheet ? <WebhookSheet onClose={() => setHookSheet(false)} /> : null}
      {pick ? <SymbolSearch onClose={() => setPick(false)} onPick={(s) => { setSym(s); const p = useMarket.getState().tickers[s]?.last; if (p && source === 'price' && !isMove) setValue(p.toFixed(instrumentOf(s, p).digits)); }} /> : null}
    </div>
  );
}

/** Listens for `pc:alert-dialog` (contract 1) and opens the dialog with what it carries. */
export function AlertHost() {
  const [seed, setSeed] = useState<AlertSeed | null>(null);
  useEffect(() => {
    const on = (e: Event) => setSeed({ ...((e as CustomEvent<AlertSeed>).detail ?? {}) });
    window.addEventListener('pc:alert-dialog', on);
    return () => window.removeEventListener('pc:alert-dialog', on);
  }, []);
  return seed ? <AlertDialog seed={seed} onClose={() => setSeed(null)} /> : null;
}

/** The toast when an alert fires, top centre, until dismissed. */
export function AlertToast() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fired = useAlerts((s) => s.fired);
  const dismiss = useAlerts((s) => s.dismiss);
  const setSymbol = usePrefs((s) => s.setSymbol);
  if (!fired || fired.popup === false) return null;
  return (
    <div className="toast" role="alert">
      <Icon name="ph-bell-ringing" size="md" />
      <button className="toast-main" onClick={() => { setSymbol(fired.symbol); if (fired.untilSeen) useAlerts.getState().acknowledge(fired.id); dismiss(); }}>
        <b className={fired.name ? 'fa' : 'sym'} dir="auto">{fired.name || fired.symbol}</b>
        <span className="muted" dir="auto">{describeAlert(fired, lang === 'fa', (v) => formatPrice(v, digitsOf(fired.symbol)))}</span>
      </button>
      <span className="num">{formatPrice(useMarket.getState().tickers[fired.symbol]?.last ?? fired.value, digitsOf(fired.symbol))}</span>
      {fired.untilSeen ? <button className="btn btn-quiet sm" data-act="seen" onClick={() => { useAlerts.getState().acknowledge(fired.id); dismiss(); }}><span className="fa">{L('دیدم', 'Seen')}</span></button> : null}
      <button className="lt" onClick={dismiss} aria-label={L('بستن', 'close')}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
