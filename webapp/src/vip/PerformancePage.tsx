import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { ty, num, str, ms } from '../data/member';
import { trackRecord } from '../data/signals';
import { faNum } from '../i18n/time';
import { State, Tabs, useL, useLoad } from '../pages/kit';
import type { PageProps } from '../pages/registry';
import { MonthBars, OutcomeCurve, WinRateRing } from './charts';
import { monthRows, outcomeWalk, signalOutcome, winRate, type Graded, type MonthRow, type Outcome } from './perf';

// «عملکرد»: the user site's performance screen. «حساب من» is the reader's own copy-trading record as
// TradeYar keeps it (performance/stats + history): the win rate over graded trades only, the
// outcome walk, the months. «کارنامه‌ی سیگنال‌ها» is the public record of closed signals, for anyone.
type Tab = 'mine' | 'signals';

interface Stats {
  closed: number; active: number; wins: number; losses: number; breakeven: number; unknown: number;
  rate: number | null; denominator: number; long: number; short: number; staleGuards: number;
  months: MonthRow[]; outcomeKnown: boolean; error: boolean;
}

function readStats(r: Record<string, unknown>): Stats {
  const months = (Array.isArray(r.monthlyBreakdown) ? (r.monthlyBreakdown as Record<string, unknown>[]) : []).map((m) => {
    const wins = num(m.wins) ?? 0, losses = num(m.losses) ?? 0;
    return { month: str(m.month), wins, losses, graded: num(m.graded) ?? wins + losses, winRate: num(m.winRate), total: num(m.total) ?? 0 };
  }).sort((a, b) => a.month.localeCompare(b.month));
  const wins = num(r.winCount) ?? 0;
  return {
    closed: num(r.closedTrades) ?? 0, active: num(r.activeTrades) ?? 0, wins, losses: num(r.lossCount) ?? 0,
    breakeven: num(r.breakevenCount) ?? 0, unknown: num(r.unknownOutcomeCount) ?? 0,
    rate: num(r.winRate), denominator: num(r.winRateDenominator) ?? wins + (num(r.lossCount) ?? 0),
    long: num(r.longCount) ?? 0, short: num(r.shortCount) ?? 0, staleGuards: num(r.activeSignalClosed) ?? 0,
    months, outcomeKnown: typeof r.outcomeAvailable === 'boolean' ? r.outcomeAvailable : wins > 0, error: Boolean(r.error),
  };
}

export default function PerformancePage({ onSignIn }: PageProps) {
  const L = useL();
  const signed = useSession((s) => Boolean(s.sessions.tradeyar));
  const [tab, setTab] = useState<Tab>(signed ? 'mine' : 'signals');
  return (
    <div className="page-inner vip-page">
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'mine', label: L('حساب من (کپی‌ترید)', 'My account (copy trading)') }, { id: 'signals', label: L('کارنامه‌ی سیگنال‌ها', 'Signal track record') }]} />
      {tab === 'mine' ? (signed ? <Mine /> : <div className="empty fa">{L('کارنامه‌ی حساب برای اعضاست.', 'Your own record needs an account.')} <button className="link" onClick={onSignIn}>{L('ورود', 'Sign in')}</button></div>) : <SignalsRecord />}
    </div>
  );
}

function Mine() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const open = usePage((s) => s.open);
  const stats = useLoad(() => ty<Record<string, unknown>>('api/user/v1/performance/stats').then(readStats), []);
  const history = useLoad(() => ty<{ trades?: Record<string, unknown>[] }>('api/user/v1/performance/history?limit=200&status=closed').then((r) => r.trades ?? []), []);
  const walk = useMemo(() => outcomeWalk((history.data ?? []).map((t): Graded => ({ outcome: (str(t.outcome).toUpperCase() || (t.isWin ? 'WIN' : 'UNKNOWN')) as Outcome, at: ms(t.closedAt ?? t.createdAt) ?? 0 }))), [history.data]);
  const s = stats.data;
  const n = (v: number) => (lang === 'fa' ? faNum(v) : String(v));
  return (
    <State loading={stats.loading} error={stats.error} onRetry={stats.reload}>
      {s ? (s.error ? <div className="empty fa">{L('کارنامه‌ی معاملات در دسترس نیست؛ هیچ عددی نمایش داده نمی‌شود.', 'The record is unavailable; no figure is shown.')}</div>
        : s.closed === 0 && s.active === 0 && !s.months.length ? (
          <section className="card stack">
            <h3 className="fa">{L('کارنامه‌ای برای نمایش نیست', 'Nothing to show yet')}</h3>
            <p className="fa">{L('هیچ معامله‌ی بسته یا بازی برای این حساب ثبت نشده است. نرخ برد روی صفر معامله تعریف نمی‌شود، نه صفر درصد.', 'No closed or open trade on this account. A win rate over zero trades is undefined, not zero.')}</p>
            <div className="row"><button className="btn btn-quiet sm" onClick={() => open('copytrade')}><span className="fa">{L('تنظیمات کپی‌ترید', 'Copy-trade settings')}</span></button><button className="btn btn-primary sm" onClick={() => open('vip-join')}><span className="fa">{L('عضویت رایگان VIP', 'Free VIP')}</span></button></div>
          </section>
        ) : (
          <div className="stack">
            <section className="card vip-perf-head">
              {s.outcomeKnown ? <WinRateRing rate={s.rate} denominator={s.denominator} window={L('از ابتدای فعالیت حساب', 'since the account started')} /> : <p className="fa">{L('نتیجه‌ی معاملات بسته‌شده ثبت نشده است، پس نرخ برد محاسبه نمی‌شود.', 'No outcome was recorded for closed trades, so no win rate is computed.')}</p>}
              <div className="vip-tiles">
                {s.outcomeKnown ? <><Tile label={L('برد', 'Wins')} value={s.wins} tone="up" /><Tile label={L('باخت', 'Losses')} value={s.losses} tone="down" /></> : <><Tile label={L('بسته‌شده', 'Closed')} value={s.closed} /><Tile label={L('باز', 'Open')} value={s.active} /></>}
                <Tile label={L('لانگ', 'Long')} value={s.long} /><Tile label={L('شورت', 'Short')} value={s.short} />
              </div>
            </section>
            {s.breakeven > 0 || s.unknown > 0 ? <p className="muted fa">{L(`${n(s.breakeven)} معامله‌ی سربه‌سر و ${n(s.unknown)} معامله‌ی بدون نتیجه از مخرج نرخ کنار گذاشته شده‌اند.`, `${s.breakeven} breakeven and ${s.unknown} ungraded trades are left out of the rate.`)}</p> : null}
            {s.staleGuards > 0 ? <p className="muted fa">{L(`${n(s.staleGuards)} پوزیشن در پنل باز مانده در حالی که سیگنال اصلی‌اش بسته شده است.`, `${s.staleGuards} positions are still open in the panel though their signal closed.`)}</p> : null}
            {s.outcomeKnown && walk.length >= 2 ? (
              <section className="card stack">
                <h3 className="fa">{L('مسیر برد و باخت', 'Win/loss path')}</h3>
                <p className="muted fa">{L('هر برد یک پله بالا، هر باخت یک پله پایین؛ این سود نیست، فقط جهت هر معامله است.', 'Each win one step up, each loss one down; direction only, not profit.')}</p>
                <OutcomeCurve walk={walk} label={L('مسیر برد و باخت', 'Win/loss path')} />
              </section>
            ) : null}
            <section className="card stack">
              <h3 className="fa">{L('تفکیک ماهانه', 'By month')}</h3>
              {s.months.length ? <MonthBars rows={s.months} /> : <div className="empty fa">{L('در شش ماه گذشته معامله‌ای بسته نشده است.', 'No closed trade in the last six months.')}</div>}
            </section>
          </div>
        )) : null}
    </State>
  );
}

function SignalsRecord() {
  const L = useL();
  const record = useLoad(() => trackRecord(300), []);
  const graded = useMemo(() => (record.data ?? []).map((s): Graded => ({ outcome: signalOutcome(s), at: s.closedAt ?? s.createdAt })), [record.data]);
  const wr = winRate(graded);
  const walk = outcomeWalk(graded);
  const months = monthRows(graded);
  return (
    <State loading={record.loading} error={record.error} onRetry={record.reload} empty={!graded.length}>
      <div className="stack">
        <section className="card vip-perf-head">
          <WinRateRing rate={wr.rate} denominator={wr.graded} window={L('سیگنال‌های بسته‌شده‌ی عمومی', 'public closed signals')} />
          <div className="vip-tiles"><Tile label={L('برد', 'Wins')} value={wr.wins} tone="up" /><Tile label={L('باخت', 'Losses')} value={wr.losses} tone="down" /><Tile label={L('همه', 'All')} value={graded.length} /></div>
        </section>
        {walk.length >= 2 ? <section className="card stack"><h3 className="fa">{L('مسیر برد و باخت', 'Win/loss path')}</h3><OutcomeCurve walk={walk} label={L('مسیر برد و باخت سیگنال‌ها', 'Signal win/loss path')} /></section> : null}
        <section className="card stack"><h3 className="fa">{L('تفکیک ماهانه', 'By month')}</h3><MonthBars rows={months} /></section>
      </div>
    </State>
  );
}

function Tile({ label, value, tone }: { label: string; value: number; tone?: 'up' | 'down' }) {
  return <div className="vip-tile"><span className="muted fa">{label}</span><b className={`num ${tone ?? ''}`}>{value.toLocaleString('en-US')}</b></div>;
}
