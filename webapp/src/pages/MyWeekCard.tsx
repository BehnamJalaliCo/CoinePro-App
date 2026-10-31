import { useMemo, useState } from 'react';
import { useJournal } from '../state/journal';
import { usePaper } from '../state/paper';
import { useAlerts } from '../state/alerts';
import { usePrefs, useT } from '../state/prefs';
import { replaySessionTimes } from '../state/replay';
import { MINIMUM_TRADES, myWeek, weekTrades, type WeekSummary } from '../state/week';
import { faDay, faNum } from '../i18n/time';
import { renderShareCard, sharePicture } from './shareCard';
import { useL } from './kit';
import './extras.css';

// «هفته‌ی من» at the top of the journal (OLD feature/journal MyWeekCard.kt, WeekShareCard.kt): the
// reader's own week over the whole journal, the paper account, the alerts and the replay — not the
// filtered view. An untouched week is a sentence, not four zeros; under five closed trades the win
// row shows the count and no percentage anywhere.
export function MyWeekCard() {
  const L = useL();
  const t = useT();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const entries = useJournal((s) => s.entries);
  const history = usePaper((s) => s.history);
  const log = useAlerts((s) => s.log);
  const alerts = useAlerts((s) => s.list);
  const [busy, setBusy] = useState(false);
  const week = useMemo<WeekSummary>(() => myWeek({
    now: Date.now(),
    trades: weekTrades(entries, history),
    alertFiredAt: log.map((x) => x.at),
    alertsArmed: alerts.filter((a) => a.active).length,
    practiceFinishedAt: replaySessionTimes(),
  }), [entries, history, log, alerts]);
  const n = (v: number) => (fa ? faNum(v) : String(v));
  const day = fa ? faDay(week.from) : new Date(week.from).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
  const since = L(`از ${day}`, `From ${day}`);
  const count = L(`${n(week.won)} از ${n(week.trades)}`, `${week.won} of ${week.trades}`);
  const floor = L(`نرخ برد دست‌کم به ${n(MINIMUM_TRADES)} معامله پشتش نیاز دارد. کمتر از آن، یک نتیجه رقم را آن‌قدر جابه‌جا می‌کند که دیگر معنایی ندارد؛ پس به‌جایش شمارش نشان داده می‌شود.`, `A win rate needs at least ${MINIMUM_TRADES} trades behind it. Below that, one outcome moves the figure too far for it to mean anything, so the count is shown instead.`);
  const rate = week.winPercent;
  const share = async () => {
    setBusy(true);
    try {
      const lines: string[] = [];
      if (!week.empty) {
        lines.push(L(`${n(week.trades)} معامله‌ی بسته‌شده`, `${week.trades} trades closed`));
        if (rate == null && week.trades > 0) lines.push(floor);
        if (week.alertsFired > 0) lines.push(L(`${n(week.alertsFired)} هشدار به صدا درآمد`, `${week.alertsFired} alerts went off`));
      }
      const cv = await renderShareCard({
        title: L('هفته‌ی من', 'My week'), subtitle: since,
        headline: week.empty ? L('هفته‌ی آرام', 'A quiet week') : rate != null ? `${rate.toFixed(0)}%` : count,
        tone: week.empty || rate == null ? 'neutral' : rate >= 50 ? 'up' : 'down',
        lines, footer: t('appName'), rtl: fa,
      });
      await sharePicture(cv, 'week', L('هفته‌ی من', 'My week'));
    } finally { setBusy(false); }
  };
  return (
    <section className="card my-week" aria-label={L('هفته‌ی من', 'My week')}>
      <h3 className="fa">{L('هفته‌ی من', 'My week')}</h3>
      <p className="muted small fa mw-since">{since}</p>
      {week.empty ? <p className="fa mw-empty">{L('این هفته هنوز چیزی ثبت نشده. هر معامله‌ای که بنویسید، بعداً می‌توانید بخوانیدش — کل کار این صفحه همین است.', 'Nothing logged this week yet. Every trade you write down is one you can read back — that is the whole of what this page is for.')}</p> : (
        <dl className="mw-rows">
          <div className="mw-row" data-row="trades"><dt className="fa">{L('معامله‌ی بسته‌شده', 'Trades closed')}</dt><dd className={fa ? 'fa' : 'num'}>{n(week.trades)}</dd></div>
          {week.trades > 0 ? <>
            <div className="mw-row" data-row="wins"><dt className="fa">{L('از این‌ها، برنده', 'Of those, won')}</dt>
              <dd className={rate == null ? (fa ? 'fa' : 'num') : `num ${rate >= 50 ? 'up' : 'down'}`}>{rate != null ? `${rate.toFixed(0)}%` : count}</dd></div>
            {rate == null ? <p className="muted small fa mw-floor">{floor}</p> : null}
          </> : null}
          <div className="mw-row" data-row="alerts"><dt className="fa">{L('هشدارِ به‌صدا‌درآمده · در انتظار', 'Alerts fired, still waiting')}</dt><dd className={fa ? 'fa' : 'num'}>{n(week.alertsFired)} · {n(week.alertsArmed)}</dd></div>
          {week.practiceSessions > 0 ? <div className="mw-row" data-row="practice"><dt className="fa">{L('جلسه‌ی بازپخش', 'Replay sessions')}</dt><dd className={fa ? 'fa' : 'num'}>{n(week.practiceSessions)}</dd></div> : null}
        </dl>
      )}
      <button className="btn btn-quiet sm mw-share" disabled={busy} onClick={() => void share()}>{L('هم‌رسانی این هفته', 'Share this week')}</button>
    </section>
  );
}
