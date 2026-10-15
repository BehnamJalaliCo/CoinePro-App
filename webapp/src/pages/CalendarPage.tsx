import { useEffect, useMemo, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { calendarWeek } from '../data/intel';
import { clock, faDay, faNum } from '../i18n/time';
import { CurrencyFlag } from '../ui/SymbolLogo';
import { Tabs, useLoad, State } from './kit';

// The app's economic calendar: impact and currency filters, search, a countdown to the next
// event, actual/forecast/previous, and the chart the event moves (gold for USD, the pair otherwise).
const MOVES: Record<string, string> = { USD: 'XAUUSD', EUR: 'EURUSD', GBP: 'GBPUSD', JPY: 'USDJPY', AUD: 'AUDUSD', CHF: 'USDCHF', CAD: 'USDCAD', NZD: 'NZDUSD' };

export default function CalendarPage() {
  const t = useT();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const cal = useLoad(() => calendarWeek(), []);
  const [impact, setImpact] = useState<'all' | 'high' | 'medium'>('all');
  const [cur, setCur] = useState('all');
  const [q, setQ] = useState('');
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(id); }, []);
  const currencies = useMemo(() => [...new Set((cal.data ?? []).map((e) => e.currency).filter(Boolean))].sort(), [cal.data]);
  const list = useMemo(() => (cal.data ?? []).filter((e) => (impact === 'all' || (impact === 'high' ? e.impact === 'high' : e.impact === 'high' || e.impact === 'medium')) && (cur === 'all' || e.currency === cur) && (!q.trim() || e.title.toLowerCase().includes(q.trim().toLowerCase()))), [cal.data, impact, cur, q]);
  const next = list.find((e) => e.time > now);
  const left = next ? Math.max(0, Math.floor((next.time - now) / 1000)) : 0;
  const days = useMemo(() => { const by = new Map<string, typeof list>(); for (const e of list) { const k = new Date(e.time).toDateString(); by.set(k, [...(by.get(k) ?? []), e]); } return [...by.values()]; }, [list]);

  return (
    <div className="page-inner narrow">
      {next ? (
        <div className="card next-event">
          <span className="muted fa">{t('calNext')}</span>
          <b>{next.title}</b>
          <span className="num big">{String(Math.floor(left / 3600)).padStart(2, '0')}:{String(Math.floor((left % 3600) / 60)).padStart(2, '0')}:{String(left % 60).padStart(2, '0')}</span>
        </div>
      ) : null}
      <div className="row">
        <Tabs value={impact} onChange={setImpact} tabs={[{ id: 'all', label: t('all') }, { id: 'high', label: t('highImpact') }, { id: 'medium', label: t('calMediumUp') }]} />
        <select value={cur} onChange={(e) => setCur(e.target.value)} className="num"><option value="all">{t('all')}</option>{currencies.map((c) => <option key={c} value={c}>{c}</option>)}</select>
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} />
      </div>
      <State loading={cal.loading} error={cal.error} empty={!list.length} onRetry={cal.reload}>
        {days.map((d) => (
          <div key={d[0]!.time} className="cal-day">
            <div className="cal-day-title fa">{faDay(d[0]!.time)} <span className="muted">({faNum(d.length)})</span></div>
            {d.map((e, i) => (
              <div key={i} className={`cal-row wide${e.time < now ? ' past' : ''}`}>
                <span className="cal-time num">{clock(e.time)}</span>
                <span className={`impact ${e.impact}`}><i /><i /><i /></span>
                <CurrencyFlag code={e.currency} />
                <span className="cal-title">{e.title}</span>
                <span className="cal-vals num">
                  <span title="actual"><b>{e.actual || '—'}</b></span>
                  <span title="forecast">{e.forecast || '—'}</span>
                  <span className="muted" title="previous">{e.previous || '—'}</span>
                </span>
                {MOVES[e.currency] ? <button className="link-btn num" onClick={() => { setSymbol(MOVES[e.currency]!); close(); }}>{MOVES[e.currency]}</button> : <span />}
              </div>
            ))}
          </div>
        ))}
      </State>
    </div>
  );
}
