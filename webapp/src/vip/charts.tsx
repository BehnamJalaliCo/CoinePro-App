// The record's three figures, drawn in SVG with the theme's own colours: the win-rate ring (rate,
// denominator and window as one unit), the outcome walk (+1 a win, −1 a loss — direction only, never
// money), and wins/losses per month.
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import { monthLabel, type MonthRow } from './perf';

export function WinRateRing({ rate, denominator, window }: { rate: number | null; denominator: number; window: string }) {
  const L = useL();
  const ok = rate != null && Number.isFinite(rate) && denominator > 0;
  const r = 52, c = 2 * Math.PI * r;
  const frac = ok ? Math.max(0, Math.min(1, rate! / 100)) : 0;
  return (
    <div className="vip-ring" role="img" aria-label={ok ? `${rate}% ${L('نرخ برد', 'win rate')}` : L('نرخ برد ثبت نشده', 'No win rate')}>
      <svg viewBox="0 0 132 132" width="132" height="132">
        <circle cx="66" cy="66" r={r} className="vip-ring-track" />
        {ok ? <circle cx="66" cy="66" r={r} className="vip-ring-arc" strokeDasharray={`${(c * frac).toFixed(1)} ${c.toFixed(1)}`} transform="rotate(-90 66 66)" /> : null}
        <text x="66" y="62" textAnchor="middle" className="vip-ring-value">{ok ? `${rate!.toFixed(rate! % 1 ? 1 : 0)}%` : '—'}</text>
        <text x="66" y="84" textAnchor="middle" className="vip-ring-sub">{L('نرخ برد', 'win rate')}</text>
      </svg>
      <div className="vip-ring-meta">
        <span className="fa">{ok ? L(`روی ${faNum(denominator)} معامله‌ی ارزیابی‌شده`, `over ${denominator} graded trades`) : L('هنوز معامله‌ی ارزیابی‌شده‌ای نیست؛ نرخ روی صفر معامله تعریف نمی‌شود.', 'No graded trades yet; a rate over zero trades is undefined.')}</span>
        <span className="muted fa">{window}</span>
      </div>
    </div>
  );
}

export function OutcomeCurve({ walk, label }: { walk: number[]; label: string }) {
  if (walk.length < 2) return null;
  const W = 640, H = 160, P = 8;
  const min = Math.min(0, ...walk), max = Math.max(0, ...walk);
  const span = Math.max(1, max - min);
  const x = (i: number) => P + (i / (walk.length - 1)) * (W - 2 * P);
  const y = (v: number) => P + (1 - (v - min) / span) * (H - 2 * P);
  const d = walk.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const last = walk[walk.length - 1]!;
  return (
    <svg className={`vip-walk ${last >= 0 ? 'up' : 'down'}`} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label={label}>
      <line x1={P} x2={W - P} y1={y(0)} y2={y(0)} className="vip-walk-zero" />
      <path d={`${d} L${x(walk.length - 1).toFixed(1)},${y(0).toFixed(1)} L${x(0).toFixed(1)},${y(0).toFixed(1)} Z`} className="vip-walk-fill" />
      <path d={d} className="vip-walk-line" />
    </svg>
  );
}

export function MonthBars({ rows }: { rows: MonthRow[] }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const top = Math.max(1, ...rows.map((r) => Math.max(r.wins, r.losses)));
  return (
    <div className="vip-months" role="table" aria-label={L('تفکیک ماهانه', 'By month')}>
      {rows.map((r) => (
        <div key={r.month} className="vip-month" role="row">
          <span className="vip-month-label fa" role="cell">{monthLabel(r.month, lang)}</span>
          <span className="vip-month-bars" role="cell" dir="ltr">
            <i className="w" style={{ width: `${(r.wins / top) * 100}%` }} title={`${r.wins}`} />
            <i className="l" style={{ width: `${(r.losses / top) * 100}%` }} title={`${r.losses}`} />
          </span>
          <span className="vip-month-num num" role="cell"><span className="up">{r.wins}</span> / <span className="down">{r.losses}</span></span>
          <span className="vip-month-rate num" role="cell">{r.winRate == null ? '—' : `${r.winRate}%`}</span>
        </div>
      ))}
    </div>
  );
}
