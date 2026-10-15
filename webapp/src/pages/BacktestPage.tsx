import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { usePanels } from '../state/panels';
import { useScript } from '../state/script';
import { loadHistory, intervalsFor } from '../data/candles';
import { INTERVALS, intervalLabel } from '../data/intervals';
import { SymbolSearch } from '../shell/SymbolSearch';
import { SymbolLogo } from '../ui/SymbolLogo';
import { DEFAULT_OPTIONS, DEFAULT_PARAMS, runBacktest, signals, type BacktestOptions, type BtResult, type StrategyId, type StrategyParams } from './backtestEngine';
import { Field, dateTime, downloadCsv, errorText, money, parseNum, useL } from './kit';
import { useT } from '../state/prefs';

// The strategy tester as a page (the app's backtest screen): pick a symbol, an interval and a
// built-in strategy or the NamaScript on the chart, set the stop, target, fees and sizing, and
// read the report — overview, equity curve against buy-and-hold, every trade, CSV.
const STRATEGIES: { id: StrategyId; fa: string; en: string }[] = [
  { id: 'ma_cross', fa: 'تقاطع میانگین متحرک', en: 'Moving-average cross' },
  { id: 'rsi', fa: 'بازگشت RSI', en: 'RSI reversal' },
  { id: 'macd', fa: 'تقاطع MACD', en: 'MACD cross' },
  { id: 'bollinger', fa: 'بازگشت به باند بولینگر', en: 'Bollinger reversion' },
  { id: 'breakout', fa: 'شکست کانال دانچیان', en: 'Donchian breakout' },
  { id: 'supertrend', fa: 'سوپرترند', en: 'Supertrend' },
];

export default function BacktestPage() {
  const L = useL();
  const t = useT();
  const chartSymbol = usePrefs((s) => s.symbol);
  const chartInterval = usePrefs((s) => s.interval);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const setInterval = usePrefs((s) => s.setInterval);
  const close = usePage((s) => s.close);
  const setBottom = usePanels((s) => s.setBottom);
  const scriptApplied = useScript((s) => s.applied);
  const [symbol, setSym] = useState(chartSymbol);
  const [interval, setIv] = useState(chartInterval);
  const [strategy, setStrategy] = useState<StrategyId>('ma_cross');
  const [p, setP] = useState<StrategyParams>(DEFAULT_PARAMS);
  const [o, setO] = useState<BacktestOptions>(DEFAULT_OPTIONS);
  const [pick, setPick] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [res, setRes] = useState<(BtResult & { bars: number; from: number; to: number }) | null>(null);

  const run = async () => {
    setBusy(true); setErr(null);
    try {
      let { bars, more } = await loadHistory(symbol, interval, undefined, 'reader');
      // Up to three more pages where the platform pages its history.
      for (let i = 0; i < 3 && more && bars[0]; i++) {
        const older = await loadHistory(symbol, interval, bars[0].t, 'reader');
        if (!older.bars.length) break;
        bars = [...older.bars, ...bars]; more = older.more;
      }
      if (bars.length < 50) throw new Error('few');
      const r = runBacktest(bars, signals(bars, strategy, p), o);
      setRes({ ...r, bars: bars.length, from: bars[0]!.t, to: bars[bars.length - 1]!.t });
    } catch (e) {
      setErr(e instanceof Error && e.message === 'few' ? L('داده‌ی کافی برای این بازه نیست.', 'Not enough history for this interval.') : errorText(e, t));
    } finally { setBusy(false); }
  };

  const numIn = (label: string, v: number, on: (n: number) => void) => (
    <Field label={label}><input className="num" dir="ltr" inputMode="decimal" defaultValue={v} onBlur={(e) => { const n = parseNum(e.target.value); if (n != null) on(n); }} /></Field>
  );
  const W = 900; const H = 180;
  const curve = (() => {
    if (!res?.equity.length) return null;
    const eq = res.equity; const min = Math.min(...eq.map((x) => x.e)); const max = Math.max(...eq.map((x) => x.e));
    const y = (v: number) => H - ((v - min) / (max - min || 1)) * H;
    return { d: eq.map((pt, i) => `${i ? 'L' : 'M'}${(i / Math.max(1, eq.length - 1)) * W},${y(pt.e)}`).join(' '), base: y(o.capital) };
  })();

  return (
    <div className="page-inner backtest-page">
      {pick ? <SymbolSearch onClose={() => setPick(false)} onPick={(s) => { setSym(s); setPick(false); if (!intervalsFor(s).includes(interval)) setIv('60'); }} /> : null}
      <div className="split">
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void run(); }}>
          <div className="row">
            <button type="button" className="btn btn-quiet sm" onClick={() => setPick(true)}><SymbolLogo symbol={symbol} size={18} /><span className="sym">{symbol}</span></button>
            <select value={interval} onChange={(e) => setIv(e.target.value)} aria-label="interval">
              {INTERVALS.filter((i) => intervalsFor(symbol).includes(i.id)).map((i) => <option key={i.id} value={i.id}>{intervalLabel(i.id)}</option>)}
            </select>
          </div>
          <Field label={L('استراتژی', 'Strategy')}>
            <select value={strategy} onChange={(e) => setStrategy(e.target.value as StrategyId)}>{STRATEGIES.map((s) => <option key={s.id} value={s.id}>{L(s.fa, s.en)}</option>)}</select>
          </Field>
          {strategy === 'ma_cross' ? <div className="row three">{numIn(L('سریع', 'Fast'), p.fast, (n) => setP({ ...p, fast: n }))}{numIn(L('کند', 'Slow'), p.slow, (n) => setP({ ...p, slow: n }))}
            <Field label={L('نوع', 'Type')}><select value={p.maType} onChange={(e) => setP({ ...p, maType: e.target.value as 'sma' | 'ema' })}><option value="ema">EMA</option><option value="sma">SMA</option></select></Field></div> : null}
          {strategy === 'rsi' ? <div className="row three">{numIn(L('دوره', 'Length'), p.rsiLen, (n) => setP({ ...p, rsiLen: n }))}{numIn(L('اشباع فروش', 'Oversold'), p.rsiLow, (n) => setP({ ...p, rsiLow: n }))}{numIn(L('اشباع خرید', 'Overbought'), p.rsiHigh, (n) => setP({ ...p, rsiHigh: n }))}</div> : null}
          {strategy === 'bollinger' ? <div className="row two">{numIn(L('دوره', 'Length'), p.bbLen, (n) => setP({ ...p, bbLen: n }))}{numIn(L('ضریب', 'Multiplier'), p.bbMult, (n) => setP({ ...p, bbMult: n }))}</div> : null}
          {strategy === 'breakout' ? numIn(L('دوره‌ی کانال', 'Channel length'), p.donchian, (n) => setP({ ...p, donchian: Math.max(2, Math.round(n)) })) : null}
          {strategy === 'supertrend' ? <div className="row two">{numIn('ATR', p.stLen, (n) => setP({ ...p, stLen: n }))}{numIn(L('ضریب', 'Multiplier'), p.stMult, (n) => setP({ ...p, stMult: n }))}</div> : null}
          <Field label={L('جهت', 'Direction')}><select value={o.direction} onChange={(e) => setO({ ...o, direction: e.target.value as BacktestOptions['direction'] })}><option value="both">{L('خرید و فروش', 'Long and short')}</option><option value="long">{L('فقط خرید', 'Long only')}</option><option value="short">{L('فقط فروش', 'Short only')}</option></select></Field>
          <div className="row two">{numIn(L('حد ضرر (درصد، ۰ = ندارد)', 'Stop % (0 = none)'), o.stopPct, (n) => setO({ ...o, stopPct: Math.max(0, n) }))}{numIn(L('حد سود (درصد، ۰ = ندارد)', 'Target % (0 = none)'), o.targetPct, (n) => setO({ ...o, targetPct: Math.max(0, n) }))}</div>
          <div className="row three">{numIn(L('کارمزد هر طرف (درصد)', 'Fee per side %'), o.feePct, (n) => setO({ ...o, feePct: Math.max(0, n) }))}{numIn(L('سرمایه', 'Capital'), o.capital, (n) => setO({ ...o, capital: Math.max(1, n) }))}{numIn(L('سهم هر معامله (درصد)', 'Share per trade %'), o.riskShare, (n) => setO({ ...o, riskShare: Math.min(100, Math.max(1, n)) }))}</div>
          <button className="btn btn-primary" disabled={busy}>{busy ? `${t('loading')}…` : L('اجرای بک‌تست', 'Run the backtest')}</button>
          <button type="button" className="btn btn-quiet sm" onClick={() => { setSymbol(symbol); setInterval(interval); setBottom('backtest'); close(); }}>{scriptApplied ? L('گزارش استراتژی نمااسکریپت روی چارت', 'The NamaScript strategy report on the chart') : L('نوشتن استراتژی با نمااسکریپت', 'Write a strategy in NamaScript')}</button>
          {err ? <p className="down fa">{err}</p> : null}
        </form>
        <div className="grow">
          {res ? (
            <>
              <div className="bt-stats wide">
                {([
                  [L('سود خالص', 'Net profit'), `${money(res.net)} (${res.netPct.toFixed(2)}%)`, res.net >= 0 ? 'up' : 'down'],
                  [L('خرید و نگهداری', 'Buy and hold'), `${res.buyHold.toFixed(2)}%`, res.buyHold >= 0 ? 'up' : 'down'],
                  [L('تعداد معامله', 'Trades'), String(res.trades.length)], [L('نرخ برد', 'Win rate'), `${res.win.toFixed(1)}%`],
                  [L('ضریب سود', 'Profit factor'), Number.isFinite(res.pf) ? res.pf.toFixed(2) : '∞'], [L('بیشترین افت', 'Max drawdown'), `${money(res.dd)} (${res.ddPct.toFixed(2)}%)`, 'down'],
                  [L('میانگین معامله', 'Average trade'), money(res.avg), res.avg >= 0 ? 'up' : 'down'], ['Sharpe', res.sharpe.toFixed(2)],
                  [L('زمان در بازار', 'Time in market'), `${res.exposure.toFixed(0)}%`], [L('بازه‌ی داده', 'Data'), `${res.bars} bars`],
                ] as [string, string, string?][]).map(([k, v, tone]) => <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className={`bt-v num ${tone ?? ''}`}>{v}</span></div>)}
              </div>
              <p className="muted small num">{dateTime(res.from * 1000)} → {dateTime(res.to * 1000)}</p>
              {curve ? <svg className="bt-equity big" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-label="equity"><line x1="0" x2={W} y1={curve.base} y2={curve.base} className="bt-zero" /><path d={curve.d} className={res.net >= 0 ? 'up' : 'down'} /></svg> : null}
              <div className="row"><button className="btn btn-quiet sm" onClick={() => downloadCsv(`backtest-${symbol}-${intervalLabel(interval)}.csv`, [['#', 'side', 'entry_time', 'entry', 'exit_time', 'exit', 'pnl', 'pct', 'reason'], ...res.trades.map((x, i) => [i + 1, x.dir === 1 ? 'long' : 'short', new Date(x.entryT * 1000).toISOString(), x.entry, new Date(x.exitT * 1000).toISOString(), x.exit, +x.pnl.toFixed(2), +x.pct.toFixed(2), x.reason])])}>CSV</button></div>
              <table className="table">
                <thead><tr><th>#</th><th>{L('جهت', 'Side')}</th><th className="r">{L('ورود', 'Entry')}</th><th className="r">{L('خروج', 'Exit')}</th><th>{L('دلیل', 'Reason')}</th><th className="r">P&amp;L</th><th className="r">%</th></tr></thead>
                <tbody>{res.trades.slice().reverse().map((x, i) => (
                  <tr key={i}><td className="num">{res.trades.length - i}</td><td className={x.dir === 1 ? 'up' : 'down'}>{x.dir === 1 ? 'Long' : 'Short'}</td>
                    <td className="r num">{dateTime(x.entryT * 1000)} · {+x.entry.toPrecision(8)}</td><td className="r num">{dateTime(x.exitT * 1000)} · {+x.exit.toPrecision(8)}</td>
                    <td>{x.reason}</td><td className={`r num ${x.pnl >= 0 ? 'up' : 'down'}`}>{money(x.pnl)}</td><td className={`r num ${x.pct >= 0 ? 'up' : 'down'}`}>{x.pct.toFixed(2)}%</td></tr>
                ))}</tbody>
              </table>
            </>
          ) : <div className="empty fa">{L('استراتژی را انتخاب کنید و بک‌تست را اجرا کنید.', 'Choose a strategy and run the backtest.')}</div>}
        </div>
      </div>
    </div>
  );
}
