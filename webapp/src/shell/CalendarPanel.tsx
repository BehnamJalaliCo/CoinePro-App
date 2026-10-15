import { useEffect, useMemo, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { calendarWeek, type CalendarEvent } from '../data/intel';
import { instrumentOf } from '../data/symbols';
import { countdown } from '../data/sessionClock';
import { eventKey, readReminders, writeReminders, type Reminder } from '../data/reminders';
import { CurrencyFlag } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { flashElement, useNow } from '../ui/useFlash';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import './panels-x.css';

// The week's economic events, TradingView's way: a week strip with each day's count, Today to jump
// to it, impact (three independent toggles), currency and search filters, "only what moves this
// symbol", times in the reader's time zone, the next event highlighted with its countdown, actual
// against forecast coloured with an arrow (and a flash when it arrives), refreshed every five
// minutes. A click opens the pair the event moves; the bell sets a reminder (a browser
// notification at the event's time, kept in this browser).
const MOVES: Record<string, string> = { USD: 'XAUUSD', EUR: 'EURUSD', GBP: 'GBPUSD', JPY: 'USDJPY', AUD: 'AUDUSD', CHF: 'USDCHF', CAD: 'USDCAD', NZD: 'NZDUSD', CNY: 'AUDUSD' };
type Impact = 'high' | 'medium' | 'low';
const PREFS_KEY = 'pc.cal.panel.v1';
interface Prefs { impact: Impact[]; currency: string; related: boolean }
function readPrefs(): Prefs {
  try { const v = JSON.parse(localStorage.getItem(PREFS_KEY) ?? 'null') as Partial<Prefs> | null; if (v) return { impact: Array.isArray(v.impact) ? v.impact : ['high', 'medium', 'low'], currency: v.currency ?? 'all', related: Boolean(v.related) }; } catch { /* defaults */ }
  return { impact: ['high', 'medium', 'low'], currency: 'all', related: false };
}

/** The currencies that move a symbol: its base and quote, and the dollar for gold, crypto and US indices. */
export function currenciesOf(symbol: string): string[] {
  const i = instrumentOf(symbol);
  const out = new Set<string>();
  if (i.market === 'forex') { out.add(i.base); out.add(i.quote); }
  else if (i.market === 'crypto') out.add('USD');
  else if (i.market === 'indices') out.add(/DE40|GER/.test(symbol) ? 'EUR' : /UK/.test(symbol) ? 'GBP' : /JPN/.test(symbol) ? 'JPY' : 'USD');
  else { out.add('USD'); if (i.quote.length === 3) out.add(i.quote); }
  return [...out];
}

/** Whether the actual beat the forecast (or the previous): 1 better, −1 worse, 0 unknown. */
export function surprise(e: CalendarEvent): -1 | 0 | 1 {
  const n = (s: string) => { const m = /-?[\d.]+/.exec(s.replace(/,/g, '').replace('−', '-')); return m ? Number(m[0]) : NaN; };
  const a = n(e.actual), f = n(e.forecast || e.previous);
  if (!Number.isFinite(a) || !Number.isFinite(f) || a === f) return 0;
  // Unemployment and claims are better when lower.
  const lowerIsBetter = /unemploy|jobless|claims|بیکاری/i.test(e.title);
  return (a > f) !== lowerIsBetter ? 1 : -1;
}

export function CalendarPanel() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tz = usePrefs((s) => s.timezone);
  const symbol = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const [events, setEvents] = useState<CalendarEvent[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [prefs, setPrefs] = useState<Prefs>(readPrefs);
  const [q, setQ] = useState<string | null>(null);
  const [reminders, setReminders] = useState<Reminder[]>(readReminders);
  const body = useRef<HTMLDivElement>(null);
  const actuals = useRef<Map<string, string>>(new Map());
  const now = useNow(1000);

  useEffect(() => {
    let alive = true;
    const load = () => calendarWeek().then((e) => { if (alive) { setEvents(e); setFailed(false); } }).catch(() => { if (alive) { setFailed(true); setEvents((x) => x ?? []); } });
    void load();
    const id = setInterval(load, 5 * 60_000);
    return () => { alive = false; clearInterval(id); };
  }, []);
  useEffect(() => { try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch { /* ignore */ } }, [prefs]);
  useEffect(() => { const on = () => setReminders(readReminders()); window.addEventListener('pc:reminders', on); return () => window.removeEventListener('pc:reminders', on); }, []);
  // An actual that arrives flashes, up or down by the surprise.
  useEffect(() => {
    for (const e of events ?? []) {
      const k = eventKey(e);
      const before = actuals.current.get(k);
      if (before !== undefined && before !== e.actual && e.actual) flashElement(body.current?.querySelector(`[data-key="${CSS.escape(k)}"] .cal-actual`) ?? null, surprise(e) || 1, 1500);
      actuals.current.set(k, e.actual);
    }
  }, [events]);

  const dayOf = useMemo(() => { try { const f = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }); return (msv: number) => f.format(new Date(msv)); } catch { return (msv: number) => new Date(msv).toISOString().slice(0, 10); } }, [tz]);
  const clockTz = useMemo(() => { try { const f = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false }); return (msv: number) => f.format(new Date(msv)); } catch { return (msv: number) => new Date(msv).toISOString().slice(11, 16); } }, [tz]);
  const dayTitle = useMemo(() => { try { const f = new Intl.DateTimeFormat(fa ? 'fa-IR' : 'en-GB', { timeZone: tz, weekday: 'long', day: 'numeric', month: 'long' }); return (msv: number) => f.format(new Date(msv)); } catch { return (msv: number) => new Date(msv).toDateString(); } }, [tz, fa]);
  const dayShort = useMemo(() => { try { const w = new Intl.DateTimeFormat(fa ? 'fa-IR' : 'en-GB', { timeZone: tz, weekday: 'short' }); const d = new Intl.DateTimeFormat('en-GB', { timeZone: tz, day: 'numeric' }); return (msv: number) => ({ w: w.format(new Date(msv)), d: d.format(new Date(msv)) }); } catch { return (msv: number) => ({ w: '', d: String(new Date(msv).getDate()) }); } }, [tz, fa]);

  const currencies = useMemo(() => [...new Set((events ?? []).map((e) => e.currency).filter(Boolean))].sort(), [events]);
  const related = useMemo(() => currenciesOf(symbol), [symbol]);
  const list = useMemo(() => {
    const needle = (q ?? '').trim().toLowerCase();
    return (events ?? []).filter((e) => (e.impact === 'holiday' || prefs.impact.includes(e.impact as Impact))
      && (prefs.currency === 'all' || e.currency === prefs.currency)
      && (!prefs.related || related.includes(e.currency))
      && (!needle || e.title.toLowerCase().includes(needle) || e.currency.toLowerCase().includes(needle)));
  }, [events, prefs, related, q]);
  const days = useMemo(() => { const by = new Map<string, CalendarEvent[]>(); for (const e of list) { const k = dayOf(e.time); by.set(k, [...(by.get(k) ?? []), e]); } return [...by.entries()]; }, [list, dayOf]);
  const week = useMemo(() => { const by = new Map<string, { t: number; n: number; high: number }>(); for (const e of events ?? []) { const k = dayOf(e.time); const x = by.get(k) ?? { t: e.time, n: 0, high: 0 }; x.n++; if (e.impact === 'high') x.high++; by.set(k, x); } return [...by.entries()].sort((a, b) => a[1].t - b[1].t); }, [events, dayOf]);
  const today = dayOf(now);
  const next = list.find((e) => e.time > now);
  const jump = (k: string) => body.current?.querySelector(`[data-day="${k}"]`)?.scrollIntoView({ block: 'start', behavior: 'smooth' });
  const setImpact = (i: Impact) => setPrefs((p) => ({ ...p, impact: p.impact.includes(i) ? p.impact.filter((x) => x !== i) : [...p.impact, i] }));
  const remind = (e: CalendarEvent) => {
    const k = eventKey(e);
    const all = readReminders();
    const next0 = all.some((r) => r.key === k) ? all.filter((r) => r.key !== k) : [...all, { key: k, title: e.title, currency: e.currency, time: e.time }];
    writeReminders(next0); setReminders(next0);
    try { if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission(); } catch { /* ignore */ }
  };
  const num = (n: number) => (fa ? faNum(n) : String(n));

  return (
    <section className="panel-generic calendar">
      <header className="panel-head">
        <span className="fa">{t('calendar')}</span>
        <span className="ws-spacer" />
        <button className="btn btn-quiet sm cal-today" onClick={() => jump(today)} disabled={!week.some(([k]) => k === today)}><span className="fa">{L('امروز', 'Today')}</span></button>
        <button className={`ib${q != null ? ' on' : ''}`} title={L('جستجو', 'Search')} onClick={() => setQ(q == null ? '' : null)}><Icon name="ph-magnifying-glass" size="md" /></button>
      </header>
      {week.length ? (
        <div className="cal-week" role="tablist">
          {week.map(([k, d]) => { const s = dayShort(d.t); return (
            <button key={k} role="tab" className={`cal-wday${k === today ? ' today' : ''}`} onClick={() => jump(k)} title={dayTitle(d.t)}>
              <span className={fa ? 'fa' : ''}>{s.w}</span><b className="num">{s.d}</b><small className="num">{num(d.n)}{d.high ? <i className="hi" /> : null}</small>
            </button>
          ); })}
        </div>
      ) : null}
      <div className="cal-filters">
        <span className="cal-impacts" role="group" aria-label={L('اهمیت', 'Impact')}>
          {(['high', 'medium', 'low'] as Impact[]).map((i) => (
            <button key={i} className={`cal-imp${prefs.impact.includes(i) ? ' on' : ''}`} aria-pressed={prefs.impact.includes(i)} title={i === 'high' ? L('اهمیت زیاد', 'High impact') : i === 'medium' ? L('اهمیت متوسط', 'Medium impact') : L('اهمیت کم', 'Low impact')} onClick={() => setImpact(i)}>
              <span className={`impact ${i}`}><i /><i /><i /></span>
            </button>
          ))}
        </span>
        <select className="num" value={prefs.currency} onChange={(e) => setPrefs((p) => ({ ...p, currency: e.target.value }))} aria-label={L('ارز', 'Currency')}>
          <option value="all">{L('همه‌ی ارزها', 'All currencies')}</option>
          {currencies.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <label className="cal-related fa" title={related.join(', ')}><input type="checkbox" checked={prefs.related} onChange={(e) => setPrefs((p) => ({ ...p, related: e.target.checked }))} /><span>{L('مرتبط با', 'Moves')} <b className="sym">{symbol}</b></span></label>
      </div>
      {q != null ? <label className="wl-add-field alerts-search"><Icon name="search" size="sm" /><input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجوی رویداد', 'Search events')} onKeyDown={(e) => { if (e.key === 'Escape') setQ(null); }} /></label> : null}
      <div className="cal-cols" role="row">
        <span className="fa">{L('زمان', 'Time')}</span><span /><span className="fa">{L('رویداد', 'Event')}</span>
        <span className="fa r">{L('واقعی', 'Actual')}</span><span className="fa r">{L('پیش‌بینی', 'Forecast')}</span><span className="fa r">{L('قبلی', 'Prior')}</span><span />
      </div>
      <div className="panel-body" ref={body}>
        {events === null ? <div className="empty fa">{t('loading')}…</div> : null}
        {events !== null && !days.length ? <div className="empty fa">{failed ? t('networkError') : t('nothingFound')}</div> : null}
        {days.map(([key, items]) => (
          <div key={key} className="cal-day" data-day={key}>
            <div className={`cal-day-title${key === today ? ' today' : ''}`}><span className={fa ? 'fa' : ''}>{dayTitle(items[0]!.time)}</span>{key === today ? <b className="cal-badge fa">{L('امروز', 'Today')}</b> : null}<span className="ws-spacer" /><span className="muted num">{num(items.length)}</span></div>
            {items.map((e) => {
              const k = eventKey(e);
              const isNext = next && eventKey(next) === k;
              const s = e.actual ? surprise(e) : 0;
              const has = reminders.some((r) => r.key === k);
              const pair = MOVES[e.currency];
              return (
                <div key={k} data-key={k} className={`cal-row x${e.time < now ? ' past' : ''}${isNext ? ' next' : ''}`} role="button" tabIndex={0}
                  onClick={() => { if (pair) setSymbol(pair); }} onKeyDown={(ev) => { if (ev.key === 'Enter' && pair) setSymbol(pair); }} title={pair ? `${e.title} → ${pair}` : e.title}>
                  <span className={`cal-time num${isNext ? ' now' : ''}`}>{clockTz(e.time)}</span>
                  <span className="cal-flag"><CurrencyFlag code={e.currency} /><span className={`impact ${e.impact}`} title={e.impact}><i /><i /><i /></span></span>
                  <span className="cal-title" dir="auto" title={e.title}>{e.title}</span>
                  <small className="cal-cur2 num muted">{e.currency}</small>
                  <span className={`cal-actual num r ${s > 0 ? 'up' : s < 0 ? 'down' : ''}`}>
                    {e.actual ? <>{s ? (s > 0 ? '▲' : '▼') : ''}{e.actual}</> : isNext ? <span className="cal-soon">{countdown(e.time - now)}</span> : '—'}
                  </span>
                  <span className="cal-fc num r">{e.forecast || '—'}</span>
                  <span className="cal-prev num r muted">{e.previous || '—'}</span>
                  {e.time > now ? (
                    <button className={`cal-bell${has ? ' on' : ''}`} title={has ? L('برداشتن یادآور', 'Remove reminder') : L('یادآوری در زمان رویداد', 'Remind me')} aria-pressed={has} onClick={(ev) => { ev.stopPropagation(); remind(e); }}>
                      <Icon name={has ? 'ph-bell-ringing' : 'ph-bell'} size="sm" />
                    </button>
                  ) : <span className="cal-bell-gap" />}
                </div>
              );
            })}
          </div>
        ))}
      </div>
      {next ? <div className="cal-next fa"><span className="muted">{t('calNext')}</span> <b dir="auto">{next.currency} · {next.title}</b> <span className="num">{countdown(next.time - now)}</span></div> : null}
    </section>
  );
}
