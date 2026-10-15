import { useEffect, useMemo, useState } from 'react';
import { useT } from '../state/prefs';
import { calendarWeek, type CalendarEvent } from '../data/intel';
import { clock, faDay } from '../i18n/time';
import { CurrencyFlag } from '../ui/SymbolLogo';

// The week's economic events, grouped by day, impact as TradingView marks it (three bars), the
// currency's flag, then actual / forecast / previous in Latin figures.

export function CalendarPanel() {
  const t = useT();
  const [events, setEvents] = useState<CalendarEvent[] | null>(null);
  const [onlyHigh, setOnlyHigh] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    calendarWeek().then((e) => { if (alive) setEvents(e); }).catch(() => { if (alive) { setFailed(true); setEvents([]); } });
    return () => { alive = false; };
  }, []);

  const days = useMemo(() => {
    const by = new Map<string, CalendarEvent[]>();
    for (const e of events ?? []) {
      if (onlyHigh && e.impact !== 'high') continue;
      const key = new Date(e.time).toDateString();
      by.set(key, [...(by.get(key) ?? []), e]);
    }
    return [...by.entries()];
  }, [events, onlyHigh]);

  const now = Date.now();
  return (
    <section className="panel-generic calendar">
      <header className="panel-head"><span className="fa">{t('calendar')}</span></header>
      <nav className="chips panel-chips">
        <button className={`chip${!onlyHigh ? ' on' : ''}`} onClick={() => setOnlyHigh(false)}><span className="fa">{t('all')}</span></button>
        <button className={`chip${onlyHigh ? ' on' : ''}`} onClick={() => setOnlyHigh(true)}><span className="fa">{t('highImpact')}</span></button>
      </nav>
      <div className="panel-body">
        {events === null ? <div className="empty fa">{t('loading')}…</div> : null}
        {events !== null && !days.length ? <div className="empty fa">{failed ? t('networkError') : t('noResults')}</div> : null}
        {days.map(([key, list]) => (
          <div key={key} className="cal-day">
            <div className="cal-day-title fa">{faDay(list[0]!.time)}</div>
            {list.map((e, i) => (
              <div key={i} className={`cal-row${e.time < now ? ' past' : ''}`}>
                <span className="cal-time num">{clock(e.time)}</span>
                <span className={`impact ${e.impact}`} title={e.impact}><i /><i /><i /></span>
                <CurrencyFlag code={e.currency} />
                <span className="cal-title">{e.title}</span>
                <span className="cal-vals num">
                  {e.actual ? <b>{e.actual}</b> : null}
                  {e.forecast ? <span title="forecast">{e.forecast}</span> : null}
                  {e.previous ? <span className="muted" title="previous">{e.previous}</span> : null}
                </span>
              </div>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}
