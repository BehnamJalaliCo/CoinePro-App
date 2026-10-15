import { useEffect, useMemo, useRef, useState } from 'react';
import { useT, usePrefs } from '../state/prefs';
import {
  CHANNEL, CONDITION_NAMES, FREQ_NAMES, MOVES, describeAlert, lineLevel, useAlerts,
  type AlertCondition, type AlertFreq, type NewAlert, type PriceAlert, type PriceField, type TrendLine,
} from '../state/alerts';
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
import './panels-x.css';

/** What opens the dialog prefilled (the `pc:alert-dialog` event's detail, contract 1, plus a list). */
export interface AlertSeed {
  symbol?: string; price?: number; line?: TrendLine; study?: { uid: string; key: string; plot?: number };
  /** A watchlist id: the alert goes on every symbol of the list (or of `symbols`). */
  list?: string; symbols?: string[];
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
            {st.list.length && !shown.length ? <div className="empty fa">{t('noResults')}</div> : null}
            {shown.map((a) => <AlertRow key={a.id} a={a} onEdit={() => setEdit(a)} />)}
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
    </section>
  );
}

function AlertRow({ a, onEdit }: { a: PriceAlert; onEdit(): void }) {
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
      </button>
      <span className="alert-sub fa">
        <span className={`badge st ${a.active ? 'on' : a.expired ? 'exp' : 'off'}`}>{status}</span>
        <span className="badge">{FREQ_NAMES[a.freq ?? 'once'][fa ? 'fa' : 'en']}</span>
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
const PRICE_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below', 'enter_channel', 'exit_channel', 'inside_channel', 'outside_channel', 'move_up_pct', 'move_down_pct', 'move_up', 'move_down'];
const LINE_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below'];
const IND_CONDS: AlertCondition[] = ['cross', 'cross_up', 'cross_down', 'above', 'below', 'enter_channel', 'exit_channel', 'inside_channel', 'outside_channel'];
const LIST_CONDS: AlertCondition[] = ['move_up_pct', 'move_down_pct'];
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
  const initialSource: 'price' | 'line' | 'indicator' = edit?.source ?? (seed?.study ? 'indicator' : seed?.line ? 'line' : 'price');
  const [source, setSource] = useState<'price' | 'line' | 'indicator'>(initialSource);
  const [field, setField] = useState<PriceField>(edit?.field ?? 'last');
  const [cond, setCond] = useState<AlertCondition>(edit?.condition ?? (listId ? 'move_up_pct' : (initialPrice ?? seed?.price) != null && tk && (initialPrice ?? seed?.price)! < tk.last ? 'below' : 'cross'));
  const [value, setValue] = useState(edit ? String(edit.value) : listId ? '3' : start ? fmt(start) : '');
  const [high, setHigh] = useState(edit?.high != null ? String(edit.high) : start ? fmt(start * 1.01) : '');
  const [bars, setBars] = useState(String(edit?.bars ?? 4));
  const [interval, setIv] = useState(edit?.interval ?? (ALERT_TFS.includes(chartInterval) ? chartInterval : '60'));
  const [line] = useState<TrendLine | undefined>(edit?.line ?? seed?.line);
  const [indKey, setIndKey] = useState(edit?.indicator?.key ?? study?.key ?? seed?.study?.key ?? 'rsi');
  const [indInputs] = useState(edit?.indicator?.inputs ?? study?.inputs);
  const [outputs, setOutputs] = useState<{ id: string; label: string; last: number | null }[]>([]);
  const [indOut, setIndOut] = useState(edit?.indicator?.output ?? '');
  const [andOn, setAndOn] = useState(Boolean(edit?.and));
  const [andCond, setAndCond] = useState<AlertCondition>(edit?.and?.condition ?? 'below');
  const [andValue, setAndValue] = useState(edit?.and ? String(edit.and.value) : '');
  const [freq, setFreq] = useState<AlertFreq>(edit?.freq ?? 'once');
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

  const conds = scope === 'list' ? LIST_CONDS : source === 'line' ? LINE_CONDS : source === 'indicator' ? IND_CONDS : PRICE_CONDS;
  useEffect(() => { if (!conds.includes(cond)) setCond(conds[0]!); }, [conds, cond]);
  const isChannel = CHANNEL.includes(cond);
  const isMove = MOVES.includes(cond);
  const v = Number(value);
  const lineNow = line ? lineLevel(line, Date.now() / 1000) : null;
  const target = source === 'line' ? lineNow : isMove ? null : v;
  const pip = symbol.length === 6 && !/^X(AU|AG)/.test(symbol) ? (symbol.includes('JPY') ? 0.01 : 0.0001) : null;
  const dist = source !== 'indicator' && target && tk ? { d: target - tk.last, pct: ((target - tk.last) / tk.last) * 100 } : null;
  const indName = (key: string) => { const e = entryOf(key); return e ? (fa ? e.fa : e.en) : key; };
  const condName = (c: AlertCondition) => CONDITION_NAMES[c][fa ? 'fa' : 'en'];
  const draft = (): NewAlert | null => {
    if (source === 'line' && !line) return null;
    const base: NewAlert = {
      symbol, condition: cond, value: source === 'line' ? (lineNow ?? 0) : v, source, interval, freq,
      field: source === 'price' ? field : undefined,
      high: isChannel ? Number(high) : undefined, bars: isMove ? Math.max(1, Math.round(Number(bars))) : undefined,
      line: source === 'line' ? line : undefined,
      indicator: source === 'indicator' ? { key: indKey, output: indOut, inputs: indInputs, label: `${entryOf(indKey)?.abbr ?? indKey}${outputs.length > 1 ? ` ${outputs.find((o) => o.id === indOut)?.label ?? ''}` : ''}` } : undefined,
      and: andOn && andValue !== '' && !isChannel ? { condition: andCond, value: Number(andValue) } : undefined,
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
    if (source !== 'line' && (!Number.isFinite(d.value) || (d.value <= 0 && source === 'price' && !isMove))) { setErr(L('مقدار را درست وارد کنید.', 'Enter a valid value.')); setTab('settings'); return; }
    if (isChannel && !(Number.isFinite(d.high!) && d.high !== d.value)) { setErr(L('کف و سقف کانال را وارد کنید.', 'Enter the channel low and high.')); setTab('settings'); return; }
    if (source === 'indicator' && !indOut) { setErr(L('خروجی اندیکاتور را انتخاب کنید.', 'Pick the indicator output.')); return; }
    if (d.expiresAt && d.expiresAt < Date.now()) { setErr(L('زمان انقضا گذشته است.', 'The expiry is in the past.')); return; }
    if (edit) st.update(edit.id, { ...d, active: true, expired: undefined, triggeredAt: undefined });
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
  const onServer = Boolean(sessions[backendOf(symbol)]) && (backendOf(symbol) === 'tradeyar' || FX_SYMBOLS.includes(symbol)) && source === 'price' && ['above', 'below', 'cross', 'cross_up', 'cross_down'].includes(cond) && !andOn && field === 'last';

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
            <button className="menu-item" onClick={() => { setPresetMenu(false); const n = window.prompt(L('نام پیش‌تنظیم', 'Preset name'), autoName()); const d = draft(); if (n && d) { const { symbol: _s, line: _l, indicator: _i, value, ...rest } = d; st.savePreset(n, { ...rest, value: isMove ? value : undefined }); } }}><Icon name="ph-floppy-disk" size="sm" /><span className="grow">{L('ذخیره به‌عنوان پیش‌تنظیم…', 'Save as preset…')}</span></button>
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
              {scope === 'symbol' ? (
                <div className="al-row">
                  <span className="al-label fa">{L('منبع', 'Condition')}</span>
                  <div className="al-field two">
                    <select value={source} onChange={(e) => setSource(e.target.value as typeof source)} aria-label={L('منبع', 'Source')}>
                      <option value="price">{L(`قیمت ${symbol}`, `${symbol} price`)}</option>
                      {line ? <option value="line">{L('خط روند', 'Trend line')}</option> : null}
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
              <div className="al-row">
                <span className="al-label" />
                <div className="al-field">
                  <select value={cond} onChange={(e) => setCond(e.target.value as AlertCondition)} aria-label={L('شرط', 'Operator')}>
                    {conds.map((c) => <option key={c} value={c}>{condName(c)}</option>)}
                  </select>
                </div>
              </div>
              <div className="al-row">
                <span className="al-label fa">{source === 'line' ? L('سطح اکنون', 'Level now') : isMove ? L('اندازه', 'Amount') : isChannel ? L('کانال', 'Channel') : L('مقدار', 'Value')}</span>
                <div className={`al-field${isChannel || isMove ? ' three' : ''}`}>
                  {source === 'line' ? <span className="num al-static">{lineNow != null ? formatPrice(lineNow, digits) : '—'} <span className="muted fa">{L('(با زمان جابه‌جا می‌شود)', '(slides with time)')}</span></span>
                    : <input type="number" className="num" step={isMove && cond.endsWith('pct') ? 0.1 : source === 'indicator' ? 0.01 : 1 / 10 ** digits} value={value} onChange={(e) => setValue(e.target.value)} autoFocus aria-label={L('مقدار', 'Value')} placeholder={isChannel ? L('کف', 'Low') : undefined} />}
                  {isChannel ? <input type="number" className="num" step={source === 'indicator' ? 0.01 : 1 / 10 ** digits} value={high} onChange={(e) => setHigh(e.target.value)} aria-label={L('سقف', 'High')} placeholder={L('سقف', 'High')} /> : null}
                  {isMove ? <label className="al-inline fa"><input type="number" className="num" min={1} value={bars} onChange={(e) => setBars(e.target.value)} aria-label={L('تعداد کندل', 'Bars')} /><span>{L('کندل', 'bars')}</span></label> : null}
                </div>
              </div>
              {dist && scope === 'symbol' ? (
                <div className="al-row"><span className="al-label" /><div className={`al-dist num ${dist.d >= 0 ? 'up' : 'down'}`}>
                  {dist.d >= 0 ? '+' : '−'}{formatPrice(Math.abs(dist.d), digits)} · {dist.pct >= 0 ? '+' : '−'}{Math.abs(dist.pct).toFixed(2)}%{pip ? ` · ${(Math.abs(dist.d) / pip).toFixed(1)} pips` : ''}
                  <span className="muted fa"> {L('تا قیمت فعلی', 'from the price now')}</span>
                  {source === 'price' && (field === 'bid' || field === 'ask') && quote ? <span className="muted"> · {field} {formatPrice(field === 'bid' ? quote.bid : quote.ask, digits)}</span> : null}
                </div></div>
              ) : null}
              {!isChannel && scope === 'symbol' ? (
                <div className="al-row">
                  <span className="al-label" />
                  <div className="al-field and">
                    <label className="al-inline fa"><input type="checkbox" checked={andOn} onChange={(e) => setAndOn(e.target.checked)} /><span>{L('و شرط دوم', 'AND a second condition')}</span></label>
                    {andOn ? (
                      <>
                        <select value={andCond} onChange={(e) => setAndCond(e.target.value as AlertCondition)} aria-label={L('شرط دوم', 'Second condition')}>
                          {(['above', 'below', 'cross', 'cross_up', 'cross_down'] as AlertCondition[]).map((c) => <option key={c} value={c}>{condName(c)}</option>)}
                        </select>
                        <input type="number" className="num" value={andValue} onChange={(e) => setAndValue(e.target.value)} aria-label={L('مقدار دوم', 'Second value')} />
                      </>
                    ) : null}
                  </div>
                </div>
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
      <button className="toast-main" onClick={() => { setSymbol(fired.symbol); dismiss(); }}>
        <b className={fired.name ? 'fa' : 'sym'} dir="auto">{fired.name || fired.symbol}</b>
        <span className="muted" dir="auto">{describeAlert(fired, lang === 'fa', (v) => formatPrice(v, digitsOf(fired.symbol)))}</span>
      </button>
      <span className="num">{formatPrice(useMarket.getState().tickers[fired.symbol]?.last ?? fired.value, digitsOf(fired.symbol))}</span>
      <button className="lt" onClick={dismiss} aria-label={L('بستن', 'close')}><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
