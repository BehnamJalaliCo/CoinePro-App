import { useEffect, useMemo, useState } from 'react';
import { usePaper, sizeForRisk, type PaperStats } from '../state/paper';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';

// Pieces the trading panel under the chart and the paper trading page share: the equity curve, the
// account's statistics (with the longest winning and losing runs), and the size-from-risk helper.

const RISK_KEY = 'pc.paperRisk.v1';
const readRisk = () => { try { const v = Number(localStorage.getItem(RISK_KEY)); return v > 0 ? v : 1; } catch { return 1; } };

/** Balance after each closed trade, as a line over its starting level. */
export function EquityCurve({ curve, start, height = 120 }: { curve: Array<{ t: number; v: number }>; start: number; height?: number }) {
  const L = useL();
  const W = 600;
  const pts = useMemo(() => {
    if (curve.length < 2) return null;
    const vs = curve.map((p) => p.v);
    const lo = Math.min(start, ...vs); const hi = Math.max(start, ...vs);
    const span = hi - lo || 1;
    const x = (i: number) => (i / (curve.length - 1)) * W;
    const y = (v: number) => 6 + (1 - (v - lo) / span) * (height - 12);
    const line = curve.map((p, i) => `${x(i).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ');
    return { line, base: y(start), area: `0,${y(start).toFixed(1)} ${line} ${W},${y(start).toFixed(1)}` };
  }, [curve, start, height]);
  if (!pts) return <div className="pp-curve empty muted fa">{L('پس از اولین معامله‌ی بسته، منحنی سرمایه اینجا رسم می‌شود.', 'The equity curve appears after the first closed trade.')}</div>;
  const up = curve[curve.length - 1]!.v >= start;
  return (
    <svg className={`pp-curve ${up ? 'up' : 'down'}`} viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" role="img" aria-label={L('منحنی سرمایه', 'Equity curve')} style={{ height }}>
      <line x1="0" x2={W} y1={pts.base} y2={pts.base} className="pp-base" vectorEffect="non-scaling-stroke" />
      <polygon points={pts.area} className="pp-area" />
      <polyline points={pts.line} className="pp-line" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

/** The record: trades, win rate, profit factor, averages, extremes, drawdown, runs, fees. */
export function PaperStatsGrid({ stats, money }: { stats: PaperStats; money(v: number): string }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const count = (n: number) => (fa ? faNum(n) : String(n));
  const rows: Array<[string, string, string?]> = [
    [L('تعداد معامله', 'Trades'), count(stats.n)],
    [L('نرخ برد', 'Win rate'), `${stats.win.toFixed(1)}%`],
    [L('سود خالص', 'Net profit'), money(stats.net), stats.net >= 0 ? 'up' : 'down'],
    [L('ضریب سود', 'Profit factor'), Number.isFinite(stats.pf) ? stats.pf.toFixed(2) : '∞'],
    [L('میانگین برد', 'Average win'), money(stats.avgWin), 'up'],
    [L('میانگین باخت', 'Average loss'), money(stats.avgLoss), 'down'],
    [L('بهترین معامله', 'Best trade'), money(stats.best)],
    [L('بدترین معامله', 'Worst trade'), money(stats.worst)],
    [L('بیشترین افت', 'Max drawdown'), `${money(stats.dd)} (${stats.ddPct.toFixed(1)}%)`],
    [L('بیشترین برد پیاپی', 'Max consecutive wins'), count(stats.winStreak), 'up'],
    [L('بیشترین باخت پیاپی', 'Max consecutive losses'), count(stats.lossStreak), 'down'],
    [L('کارمزد پرداختی', 'Fees paid'), money(stats.fees)],
  ];
  return (
    <dl className="kv cards-kv pp-stats">
      {rows.map(([k, v, c]) => <div key={k} className="kv-row"><dt className="fa">{k}</dt><dd className={`${/^[\d۰-۹]+$/.test(v) && fa ? 'fa' : 'num'}${c ? ` ${c}` : ''}`}>{v}</dd></div>)}
    </dl>
  );
}

/** Risk a share of the balance: fills the quantity from the entry and the stop. */
export function RiskSizer({ entry, sl, onSize, compact = false }: { entry: number | null; sl: number | null; onSize(qty: number): void; compact?: boolean }) {
  const L = useL();
  const balance = usePaper((s) => s.balance);
  const [risk, setRisk] = useState(readRisk);
  useEffect(() => { try { localStorage.setItem(RISK_KEY, String(risk)); } catch { /* blocked */ } }, [risk]);
  const qty = entry && sl ? sizeForRisk(balance, risk, entry, sl) : 0;
  const tip = !sl ? L('برای محاسبه‌ی حجم، حد ضرر را وارد کنید.', 'Enter a stop loss to size the trade from the risk.') : L(`حجمی که اگر حد ضرر بخورد ${risk}% موجودی را از دست می‌دهد`, `The size that loses ${risk}% of the balance at the stop`);
  return (
    <span className={`pp-risk${compact ? ' compact' : ''}`} title={tip}>
      <span className="fa">{L('ریسک', 'Risk')}</span>
      <input className="num" type="number" min="0.01" step="any" value={risk} onChange={(e) => setRisk(Math.max(0.01, Number(e.target.value) || 1))} dir="ltr" aria-label={L('درصد ریسک', 'Risk percent')} />
      <span className="num">%</span>
      <button type="button" className="btn btn-quiet sm" disabled={!qty} onClick={() => onSize(qty)}>
        <span className="fa">{L('حجم', 'Size')}</span>{qty ? <span className="num"> {qty}</span> : null}
      </button>
    </span>
  );
}
