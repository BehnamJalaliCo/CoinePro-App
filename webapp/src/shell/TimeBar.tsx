import { goToTime } from '../chart/actions';
import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Dropdown } from '../ui/Menu';
import { usePrefs, useT } from '../state/prefs';
import { useChartBus, showSpan } from '../chart/chartBus';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { cellCount, useGrid } from '../state/grid';
import { canDraw } from '../data/candles';
import { intervalSeconds } from '../data/intervals';
import { marketOf } from '../data/symbols';
import { regularSession } from '../data/feed';
import { SESSIONS } from './SessionShading';
import './topbar.css';

// TradingView's bar under the chart: date ranges on the left (each picks an interval and shows
// that span; the one in force is marked), go to a date or a custom range, then the clock with the
// time zones (Exchange, UTC and the world by offset), the session where a symbol has one, the
// scale switches and the settings gear.
export const RANGES: { id: string; interval: string; span: number }[] = [
  { id: '1D', interval: '1', span: 86400 },
  { id: '5D', interval: '5', span: 5 * 86400 },
  { id: '1M', interval: '30', span: 30 * 86400 },
  { id: '3M', interval: '60', span: 91 * 86400 },
  { id: '6M', interval: '120', span: 182 * 86400 },
  { id: 'YTD', interval: '1D', span: 0 },
  { id: '1Y', interval: '1D', span: 365 * 86400 },
  { id: '5Y', interval: '1W', span: 5 * 365 * 86400 },
  { id: 'All', interval: '1M', span: Number.MAX_SAFE_INTEGER },
];

function ytdSpan(): number {
  const now = new Date();
  return Math.floor((now.getTime() - Date.UTC(now.getUTCFullYear(), 0, 1)) / 1000);
}

/** '(UTC+3:30)' for a zone, now (daylight saving included). */
export function offsetLabel(tz: string): string {
  try {
    const part = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
    return (part?.value ?? 'UTC').replace('GMT', 'UTC');
  } catch { return 'UTC'; }
}
function offsetMinutes(tz: string): number {
  const m = /UTC([+-])(\d{1,2})(?::(\d{2}))?/.exec(offsetLabel(tz));
  return m ? (m[1] === '-' ? -1 : 1) * (Number(m[2]) * 60 + Number(m[3] ?? 0)) : 0;
}

// TradingView's list of zones, with the cities written in Persian too.
const ZONES: { id: string; en: string; fa: string }[] = [
  { id: 'Pacific/Honolulu', en: 'Honolulu', fa: 'هونولولو' }, { id: 'America/Anchorage', en: 'Anchorage', fa: 'انکوریج' },
  { id: 'America/Los_Angeles', en: 'Los Angeles', fa: 'لس‌آنجلس' }, { id: 'America/Phoenix', en: 'Phoenix', fa: 'فینیکس' },
  { id: 'America/Vancouver', en: 'Vancouver', fa: 'ونکوور' }, { id: 'America/Denver', en: 'Denver', fa: 'دنور' },
  { id: 'America/Mexico_City', en: 'Mexico City', fa: 'مکزیکوسیتی' }, { id: 'America/El_Salvador', en: 'San Salvador', fa: 'سان‌سالوادور' },
  { id: 'America/Bogota', en: 'Bogota', fa: 'بوگوتا' }, { id: 'America/Chicago', en: 'Chicago', fa: 'شیکاگو' },
  { id: 'America/Lima', en: 'Lima', fa: 'لیما' }, { id: 'America/Caracas', en: 'Caracas', fa: 'کاراکاس' },
  { id: 'America/New_York', en: 'New York', fa: 'نیویورک' }, { id: 'America/Toronto', en: 'Toronto', fa: 'تورنتو' },
  { id: 'America/Argentina/Buenos_Aires', en: 'Buenos Aires', fa: 'بوئنوس‌آیرس' }, { id: 'America/Halifax', en: 'Halifax', fa: 'هلیفکس' },
  { id: 'America/Santiago', en: 'Santiago', fa: 'سانتیاگو' }, { id: 'America/Sao_Paulo', en: 'Sao Paulo', fa: 'سائوپائولو' },
  { id: 'Atlantic/Azores', en: 'Azores', fa: 'آزور' }, { id: 'Atlantic/Reykjavik', en: 'Reykjavik', fa: 'ریکیاویک' },
  { id: 'Africa/Casablanca', en: 'Casablanca', fa: 'کازابلانکا' }, { id: 'Europe/Dublin', en: 'Dublin', fa: 'دوبلین' },
  { id: 'Africa/Lagos', en: 'Lagos', fa: 'لاگوس' }, { id: 'Europe/Lisbon', en: 'Lisbon', fa: 'لیسبون' },
  { id: 'Europe/London', en: 'London', fa: 'لندن' }, { id: 'Europe/Amsterdam', en: 'Amsterdam', fa: 'آمستردام' },
  { id: 'Europe/Berlin', en: 'Berlin', fa: 'برلین' }, { id: 'Europe/Brussels', en: 'Brussels', fa: 'بروکسل' },
  { id: 'Africa/Cairo', en: 'Cairo', fa: 'قاهره' }, { id: 'Africa/Johannesburg', en: 'Johannesburg', fa: 'ژوهانسبورگ' },
  { id: 'Europe/Madrid', en: 'Madrid', fa: 'مادرید' }, { id: 'Europe/Paris', en: 'Paris', fa: 'پاریس' },
  { id: 'Europe/Rome', en: 'Rome', fa: 'رم' }, { id: 'Europe/Stockholm', en: 'Stockholm', fa: 'استکهلم' },
  { id: 'Europe/Zurich', en: 'Zurich', fa: 'زوریخ' }, { id: 'Europe/Athens', en: 'Athens', fa: 'آتن' },
  { id: 'Europe/Istanbul', en: 'Istanbul', fa: 'استانبول' }, { id: 'Europe/Moscow', en: 'Moscow', fa: 'مسکو' },
  { id: 'Asia/Riyadh', en: 'Riyadh', fa: 'ریاض' }, { id: 'Asia/Tehran', en: 'Tehran', fa: 'تهران' },
  { id: 'Asia/Dubai', en: 'Dubai', fa: 'دبی' }, { id: 'Asia/Muscat', en: 'Muscat', fa: 'مسقط' },
  { id: 'Asia/Kabul', en: 'Kabul', fa: 'کابل' }, { id: 'Asia/Karachi', en: 'Karachi', fa: 'کراچی' },
  { id: 'Asia/Kolkata', en: 'Kolkata', fa: 'کلکته' }, { id: 'Asia/Kathmandu', en: 'Kathmandu', fa: 'کاتماندو' },
  { id: 'Asia/Dhaka', en: 'Dhaka', fa: 'داکا' }, { id: 'Asia/Bangkok', en: 'Bangkok', fa: 'بانکوک' },
  { id: 'Asia/Jakarta', en: 'Jakarta', fa: 'جاکارتا' }, { id: 'Asia/Hong_Kong', en: 'Hong Kong', fa: 'هنگ‌کنگ' },
  { id: 'Asia/Shanghai', en: 'Shanghai', fa: 'شانگهای' }, { id: 'Asia/Singapore', en: 'Singapore', fa: 'سنگاپور' },
  { id: 'Asia/Taipei', en: 'Taipei', fa: 'تایپه' }, { id: 'Australia/Perth', en: 'Perth', fa: 'پرت' },
  { id: 'Asia/Seoul', en: 'Seoul', fa: 'سئول' }, { id: 'Asia/Tokyo', en: 'Tokyo', fa: 'توکیو' },
  { id: 'Australia/Brisbane', en: 'Brisbane', fa: 'بریزبن' }, { id: 'Australia/Sydney', en: 'Sydney', fa: 'سیدنی' },
  { id: 'Pacific/Auckland', en: 'Auckland', fa: 'اوکلند' },
];

/** The time zone of the place a symbol trades: TradingView's "Exchange". */
export function exchangeZone(symbol: string): string {
  const s = regularSession(symbol);
  if (s) return s.tz;
  const m = marketOf(symbol);
  // Crypto venues keep UTC; forex, metals and energy roll their trading day at 17:00 New York.
  return m === 'crypto' ? 'UTC' : 'America/New_York';
}

// The clock at the right of the time bar, in the chosen time zone; a click lists the zones.
function Clock() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tz = usePrefs((s) => s.timezone);
  const tzExchange = usePrefs((s) => s.tzExchange);
  const symbol = usePrefs((s) => s.symbol);
  const setTz = usePrefs((s) => s.setTimezone);
  const setTzExchange = usePrefs((s) => s.setTzExchange);
  const [now, setNow] = useState(() => new Date());
  const [open, setOpen] = useState(false);
  const btn = useRef<HTMLButtonElement>(null);
  useEffect(() => { const id = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(id); }, []);
  // "Exchange" follows the symbol.
  useEffect(() => { if (tzExchange) { const z = exchangeZone(symbol); if (z !== usePrefs.getState().timezone) setTz(z); } }, [tzExchange, symbol, setTz]);
  const zones = useMemo(() => [...ZONES].sort((a, b) => offsetMinutes(a.id) - offsetMinutes(b.id) || a.en.localeCompare(b.en)), [open]);
  let time = '';
  try { time = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(now); } catch { time = now.toISOString().slice(11, 19); }
  const pick = (id: string | 'exchange') => {
    if (id === 'exchange') { setTzExchange(true); setTz(exchangeZone(symbol)); } else { setTzExchange(false); setTz(id); }
    setOpen(false);
  };
  const row = (id: string, label: string, on: boolean, off?: string) => (
    <button key={id} role="menuitemradio" aria-checked={on} className={`menu-item tz-row${on ? ' on' : ''}`} onClick={() => pick(id)}>
      <span className="tz-check" aria-hidden>{on ? <Icon name="ph-check" size="sm" /> : null}</span>
      {off ? <span className="num tz-off">({off})</span> : null}
      <span className={fa ? 'fa' : ''}>{label}</span>
    </button>
  );
  return (
    <>
      <button ref={btn} className={`ib timebar-clock num${open ? ' open' : ''}`} title={t('timezone')} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        {time} ({tzExchange ? L('بورس', 'Exchange') : offsetLabel(tz)})
      </button>
      {open ? (
        <Dropdown anchor={btn.current} onClose={() => setOpen(false)} align="right" className="tz-menu tvm">
          <div dir={fa ? 'rtl' : 'ltr'}>
            {row('UTC', 'UTC', !tzExchange && tz === 'UTC')}
            {row('exchange', `${L('بورس', 'Exchange')} · ${exchangeZone(symbol) === 'UTC' ? 'UTC' : offsetLabel(exchangeZone(symbol))}`, tzExchange)}
            <div className="menu-sep" />
            {zones.map((z) => row(z.id, fa ? z.fa : z.en, !tzExchange && z.id === tz, offsetLabel(z.id)))}
          </div>
        </Dropdown>
      ) : null}
    </>
  );
}

/** Regular / extended trading hours, shown only for a symbol that has both (cash indices). */
function SessionMenu() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const session = usePrefs((s) => s.session);
  const setSession = usePrefs((s) => s.setSession);
  const [open, setOpen] = useState(false);
  const btn = useRef<HTMLButtonElement>(null);
  if (!regularSession(symbol) || intervalSeconds(interval) >= 86400) return null;
  return (
    <>
      <button ref={btn} className={`ib session-btn${open ? ' open' : ''}`} title={L('ساعت‌های معامله', 'Trading session')} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="label">{session === 'regular' ? 'RTH' : 'ETH'}</span>
      </button>
      {open ? (
        <Dropdown anchor={btn.current} onClose={() => setOpen(false)} align="right" className="session-menu tvm">
          <div dir={fa ? 'rtl' : 'ltr'}>
            {(['regular', 'extended'] as const).map((s) => (
              <button key={s} role="menuitemradio" aria-checked={session === s} className={`menu-item tz-row${session === s ? ' on' : ''}`} onClick={() => { setSession(s); setOpen(false); }}>
                <span className="tz-check" aria-hidden>{session === s ? <Icon name="ph-check" size="sm" /> : null}</span>
                <span className={fa ? 'fa' : ''}>{s === 'regular' ? L('ساعت‌های رسمی بازار', 'Regular trading hours') : L('ساعت‌های گسترده', 'Extended trading hours')}</span>
              </button>
            ))}
          </div>
        </Dropdown>
      ) : null}
    </>
  );
}

/** The old Pro-Chart's session shading: Sydney, Tokyo, London, New York, their overlaps; all or none. */
function ShadingMenu() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const interval = usePrefs((s) => s.interval);
  const ids = usePrefs((s) => s.shadedSessions);
  const overlaps = usePrefs((s) => s.sessionOverlaps);
  const setIds = usePrefs((s) => s.setShadedSessions);
  const setOverlaps = usePrefs((s) => s.setSessionOverlaps);
  const [open, setOpen] = useState(false);
  const btn = useRef<HTMLButtonElement>(null);
  if (intervalSeconds(interval) >= 86400) return null;
  const check = (on: boolean, label: string, onClick: () => void, swatch?: string) => (
    <button key={label} className="menu-item mcheck" role="menuitemcheckbox" aria-checked={on} onClick={onClick}>
      <span className={`tick${on ? ' on' : ''}`} aria-hidden>{on ? <Icon name="ph-check" size="sm" /> : null}</span>
      {swatch ? <i className="shade-swatch" style={{ background: swatch }} aria-hidden /> : null}
      <span className={fa ? 'fa' : ''}>{label}</span>
    </button>
  );
  return (
    <>
      <button ref={btn} className={`ib shading-btn${ids.length ? ' on' : ''}${open ? ' open' : ''}`} title={L('سایه‌ی جلسه‌های معاملاتی', 'Trading sessions')} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        <Icon name="ph-clock" size="sm" />
      </button>
      {open ? (
        <Dropdown anchor={btn.current} onClose={() => setOpen(false)} align="right" className="shading-menu tvm">
          <div dir={fa ? 'rtl' : 'ltr'}>
            <div className={`menu-title${fa ? ' fa' : ''}`}>{L('جلسه‌های معاملاتی', 'Trading sessions')}</div>
            {SESSIONS.map((s) => check(ids.includes(s.id), fa ? s.fa : s.en, () => setIds(ids.includes(s.id) ? ids.filter((x) => x !== s.id) : [...ids, s.id]), s.edge))}
            {check(overlaps, L('هم‌پوشانی‌ها', 'Overlaps'), () => setOverlaps(!overlaps), 'rgba(239,68,68,0.35)')}
            <div className="menu-sep" />
            <div className="menu-row shade-all">
              <button className="btn btn-quiet sm" onClick={() => setIds(SESSIONS.map((s) => s.id))}><span className={fa ? 'fa' : ''}>{L('همه', 'All')}</span></button>
              <button className="btn btn-quiet sm" onClick={() => setIds([])}><span className={fa ? 'fa' : ''}>{L('هیچ‌کدام', 'None')}</span></button>
            </div>
          </div>
        </Dropdown>
      ) : null}
    </>
  );
}

export function TimeBar({ supported }: { supported: (interval: string) => boolean }) {
  const t = useT();
  const L = useL();
  const setInterval = usePrefs((s) => s.setInterval);
  const interval = usePrefs((s) => s.interval);
  const symbol = usePrefs((s) => s.symbol);
  const { api, bars, scale, autoScale, setScale, setAutoScale, setPendingSpan } = useChartBus();
  const [active, setActive] = useState<string | null>(null);
  const expected = useRef<{ symbol: string; interval: string } | null>(null);

  // The marked range holds until the reader moves the chart or picks another symbol or interval.
  useEffect(() => {
    const e = expected.current;
    if (e && e.symbol === symbol && e.interval === interval) return;
    expected.current = null;
    setActive(null);
  }, [symbol, interval]);
  useEffect(() => {
    if (!active) return;
    const clear = (ev: Event) => {
      const el = ev.target as HTMLElement | null;
      if (el?.closest?.('.cell-main .chart-host') && (ev.type === 'wheel' || (ev as PointerEvent).buttons)) setActive(null);
    };
    window.addEventListener('wheel', clear, { capture: true, passive: true });
    window.addEventListener('pointermove', clear, { capture: true, passive: true });
    return () => { window.removeEventListener('wheel', clear, { capture: true }); window.removeEventListener('pointermove', clear, { capture: true }); };
  }, [active]);

  const pick = (r: (typeof RANGES)[number]) => {
    const span = r.id === 'YTD' ? ytdSpan() : r.span;
    const all = span === Number.MAX_SAFE_INTEGER;
    const g = useGrid.getState();
    const n = cellCount(g.grid);
    // The active chart, or every chart with date-range sync on.
    const targets = g.sync.dateRange && n > 1 ? Array.from({ length: n - 1 }, (_, i) => i + 1) : g.active > 0 ? [g.active] : [];
    const main = g.active === 0 || (g.sync.dateRange && n > 1);
    if (targets.length) {
      const cellIv = (sym: string) => (canDraw(sym, r.interval) ? r.interval : ['5', '15', '60', '1D'].find((x) => canDraw(sym, x)) ?? r.interval);
      const sym = g.cells[(targets[0] ?? 1) - 1]?.symbol ?? symbol;
      g.requestRange(all ? null : span, cellIv(sym), targets);
    }
    setActive(r.id);
    if (!main) return;
    let iv = r.interval;
    if (!supported(iv)) iv = ['5', '15', '60', '1D'].find(supported) ?? iv;
    if (iv === interval && api) {
      if (all) api.timeScale().fitContent(); else showSpan(api, bars, span);
    } else {
      expected.current = { symbol, interval: iv };
      setPendingSpan(all ? null : span);
      setInterval(iv);
      if (all) setTimeout(() => useChartBus.getState().api?.timeScale().fitContent(), 1500);
    }
  };

  return (
    <div className="ws-timebar" role="toolbar">
      {RANGES.map((r) => (
        <button key={r.id} className={`ib range num${active === r.id ? ' on' : ''}`} aria-pressed={active === r.id} onClick={() => pick(r)}>{r.id}</button>
      ))}
      <span className="sep" />
      <GoToDate />
      <span className="ws-spacer" />
      <Clock />
      <SessionMenu />
      <ShadingMenu />
      <span className="sep" />
      <button className={`ib scale${scale === 'percent' ? ' on' : ''}`} aria-pressed={scale === 'percent'} title="Alt+P"
        onClick={() => setScale(scale === 'percent' ? 'normal' : 'percent')}>%</button>
      <button className={`ib scale${scale === 'log' ? ' on' : ''}`} aria-pressed={scale === 'log'} title="Alt+L"
        onClick={() => setScale(scale === 'log' ? 'normal' : 'log')}>log</button>
      <button className={`ib scale${autoScale ? ' on' : ''}`} aria-pressed={autoScale}
        onClick={() => setAutoScale(!autoScale)}>auto</button>
      <button className="ib timebar-gear" title={`${t('settings')} · ${L('مقیاس‌ها', 'Scales')}`}
        onClick={() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'scales' } }))}>
        <Icon name="ph-gear" size="sm" />
      </button>
    </div>
  );
}

const p2 = (n: number) => String(n).padStart(2, '0');
const localInput = (ms: number) => { const d = new Date(ms); return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`; };

/** TradingView's «Go to» (Alt+G): a date and time, or a custom range; older history loads as needed. */
function GoToDate() {
  const t = useT();
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<'date' | 'range'>('date');
  const [value, setValue] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [busy, setBusy] = useState(false);
  const [miss, setMiss] = useState(false);
  useEffect(() => { const on = () => { setOpen(true); setTab('date'); }; window.addEventListener('pc:goto', on); return () => window.removeEventListener('pc:goto', on); }, []);
  useEffect(() => {
    if (!open) return;
    setMiss(false);
    const now = Date.now();
    setTo(localInput(now));
    setFrom(localInput(now - 7 * 86400e3));
  }, [open]);
  const go = async () => {
    const ms = Date.parse(value);
    if (!Number.isFinite(ms)) return;
    setBusy(true); setMiss(false);
    const ok = await goToTime(Math.floor(ms / 1000));
    setBusy(false);
    if (ok) setOpen(false); else setMiss(true);
  };
  const goRange = async () => {
    const a = Date.parse(from); const b = Date.parse(to);
    if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) return;
    setBusy(true); setMiss(false);
    const ok = await goToTime(Math.floor(a / 1000));
    const { api, bars } = useChartBus.getState();
    if (api && bars.length) {
      const i0 = Math.max(0, bars.findIndex((x) => x.t >= a / 1000));
      let i1 = bars.findIndex((x) => x.t > b / 1000);
      if (i1 < 0) i1 = bars.length;
      const want = { from: i0, to: Math.max(i0 + 2, i1 - 1) };
      // A load or a live bar landing at the same moment can move the view; hold the range there.
      for (let k = 0; k < 4; k++) {
        api.timeScale().setVisibleLogicalRange(want);
        await new Promise((r) => setTimeout(r, 120));
        const now = api.timeScale().getVisibleLogicalRange();
        if (now && Math.abs(now.from - want.from) < 1 && Math.abs(now.to - want.to) < 1) break;
      }
    }
    setBusy(false);
    if (ok) setOpen(false); else setMiss(true);
  };
  return (
    <span className="goto">
      <button className={`ib${open ? ' on' : ''}`} title={`${t('goToDate')} (Alt+G)`} aria-expanded={open} onClick={() => setOpen(!open)}><Icon name="ph-calendar-blank" size="sm" /></button>
      {open ? createPortal(
        <div className="scrim center" onMouseDown={(e) => { if (e.target === e.currentTarget) setOpen(false); }}>
          <div className="dialog tv-small goto-dialog" role="dialog" aria-modal="true" aria-label={t('goToDate')} dir={fa ? 'rtl' : 'ltr'} onKeyDown={(e) => { if (e.key === 'Escape') { e.stopPropagation(); setOpen(false); } }}>
            <header className="dialog-head"><h2 className={fa ? 'fa' : ''}>{L('رفتن به', 'Go to')}</h2>
              <button type="button" className="ib" onClick={() => setOpen(false)} aria-label="close"><Icon name="ph-x" size="md" /></button></header>
            <nav className="goto-tabs" role="tablist">
              <button role="tab" aria-selected={tab === 'date'} className={tab === 'date' ? 'on' : ''} onClick={() => setTab('date')}><span className={fa ? 'fa' : ''}>{L('تاریخ', 'Date')}</span></button>
              <button role="tab" aria-selected={tab === 'range'} className={tab === 'range' ? 'on' : ''} onClick={() => setTab('range')}><span className={fa ? 'fa' : ''}>{L('بازه‌ی دلخواه', 'Custom range')}</span></button>
            </nav>
            {tab === 'date' ? (
              <form className="goto-pop" onSubmit={(e) => { e.preventDefault(); void go(); }}>
                <input type="datetime-local" value={value} onChange={(e) => setValue(e.target.value)} autoFocus aria-label={t('goToDate')} />
                <button className="btn btn-primary sm" disabled={busy || !value}>{busy ? '…' : L('برو', 'Go to')}</button>
                {miss ? <span className="muted small fa">{t('goToDateMiss')}</span> : null}
              </form>
            ) : (
              <form className="goto-range" onSubmit={(e) => { e.preventDefault(); void goRange(); }}>
                <label className="tv-field"><span className={fa ? 'fa' : ''}>{L('از', 'From')}</span><input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
                <label className="tv-field"><span className={fa ? 'fa' : ''}>{L('تا', 'To')}</span><input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} /></label>
                <div className="goto-range-foot">
                  {miss ? <span className="muted small fa">{t('goToDateMiss')}</span> : <span />}
                  <button className="btn btn-primary sm" disabled={busy || !from || !to}>{busy ? '…' : L('برو', 'Go to')}</button>
                </div>
              </form>
            )}
          </div>
        </div>,
        document.body,
      ) : null}
    </span>
  );
}
