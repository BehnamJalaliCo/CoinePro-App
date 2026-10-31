import { useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { usePanels } from '../state/panels';
import { useScript } from '../state/script';
import { loadHistory, intervalsFor, planFor } from '../data/candles';
import { INTERVALS, intervalLabel, intervalSeconds } from '../data/intervals';
import { SymbolSearch } from '../shell/SymbolSearch';
import { SymbolLogo } from '../ui/SymbolLogo';
import { DEFAULT_OPTIONS, DEFAULT_PARAMS, barMagnifier, runBacktest, strategySet, type BacktestOptions, type BtResult, type StrategyId, type StrategyParams } from './backtestEngine';
import { Field, dateTime, errorText, money, parseNum, useL } from './kit';
import { faNum } from '../i18n/time';
import './records.css';
import { CONFIDENT_TRADES, XLSX_MIME, backtestCsv, backtestFileName, backtestXlsx, downloadFile, type BtRun } from './records';
import { useT } from '../state/prefs';

// The strategy tester as a page (the app's backtest screen): pick a symbol, an interval and a
// built-in strategy or the NamaScript on the chart, set the stop, target, fees and sizing, and
// read the report — overview, equity curve against buy-and-hold, every trade — with the small-sample
// sentence under thirty trades and the whole run as CSV or a real Excel workbook (OLD BacktestSheet).
const STRATEGIES: { id: StrategyId; fa: string; en: string }[] = [
  { id: 'ma_cross', fa: 'تقاطع میانگین متحرک', en: 'Moving-average cross' },
  { id: 'rsi', fa: 'بازگشت RSI', en: 'RSI reversal' },
  { id: 'macd', fa: 'تقاطع MACD', en: 'MACD cross' },
  { id: 'bollinger', fa: 'بازگشت به باند بولینگر', en: 'Bollinger reversion' },
  { id: 'breakout', fa: 'شکست کانال دانچیان', en: 'Donchian breakout' },
  { id: 'supertrend', fa: 'سوپرترند', en: 'Supertrend' },
  { id: 'growth_scan', fa: 'الگوهای رشد', en: 'Growth setups' },
];

/** The finer native length under `interval` that divides it, for the bar magnifier (OLD ChartController.magnifierBars). */
export function finerInterval(symbol: string, interval: string): string | null {
  const len = intervalSeconds(interval);
  const natives = INTERVALS.map((i) => i.id).filter((id) => planFor(symbol, id)?.fold === 'none');
  const finer = natives.filter((id) => { const s = intervalSeconds(id); return s < len && len % s === 0; }).sort((a, b) => intervalSeconds(b) - intervalSeconds(a));
  return finer[0] ?? null;
}
/** How many pages of finer bars the magnifier may fetch (OLD MAGNIFIER_PAGES). */
const MAGNIFIER_PAGES = 12;

export default function BacktestPage() {
  const L = useL();
  const t = useT();
  const chartSymbol = usePrefs((s) => s.symbol);
  const chartInterval = usePrefs((s) => s.interval);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const setInterval = usePrefs((s) => s.setInterval);
  const close = usePage((s) => s.close);
  const setBottom = usePanels((s) => s.setBottom);
  const scriptApplied = useScript((s) => s.on);
  const [symbol, setSym] = useState(chartSymbol);
  const [interval, setIv] = useState(chartInterval);
  const [strategy, setStrategy] = useState<StrategyId>('ma_cross');
  const [p, setP] = useState<StrategyParams>(DEFAULT_PARAMS);
  const [o, setO] = useState<BacktestOptions>(DEFAULT_OPTIONS);
  const [pick, setPick] = useState(false);
  const [magnify, setMagnify] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [res, setRes] = useState<(BtResult & { bars: number; from: number; to: number; run: BtRun }) | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

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
      let magnifier: ((i: number) => typeof bars) | null = null;
      const finer = magnify ? finerInterval(symbol, interval) : null;
      if (finer && (o.stopPct > 0 || o.targetPct > 0)) {
        // Paged back from the live edge to the first bar, or MAGNIFIER_PAGES pages.
        let lower: typeof bars = [];
        let before: number | undefined;
        for (let i = 0; i < MAGNIFIER_PAGES; i++) {
          const page = await loadHistory(symbol, finer, before, 'reader').catch(() => null);
          const got = page?.bars.filter((b) => before == null || b.t < before) ?? [];
          if (!got.length) break;
          lower = [...got, ...lower];
          before = got[0]!.t;
          if (before <= bars[0]!.t || !page?.more) break;
        }
        if (lower.length) magnifier = barMagnifier(bars, lower.filter((b) => b.t >= bars[0]!.t));
      }
      // The growth setups are a rule about growth: long only, as in the app.
      const opts = { ...o, magnifier, ...(strategy === 'growth_scan' ? { direction: 'long' as const } : {}) };
      const r = runBacktest(bars, strategySet(bars, strategy, p), opts);
      const from = bars[0]!.t; const to = bars[bars.length - 1]!.t;
      const name = STRATEGIES.find((x) => x.id === strategy)?.fa ?? strategy;
      setRes({ ...r, bars: bars.length, from, to, run: { symbol, strategy: name, direction: opts.direction, feePct: o.feePct, capital: o.capital, bars: bars.length, from, to, result: r } });
      setSaved(null);
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
          {strategy === 'growth_scan' ? <>{numIn(L('دوره‌ی کانال', 'Channel length'), p.donchian, (n) => setP({ ...p, donchian: Math.max(2, Math.round(n)) }))}<p className="muted small fa">{L('ورود با برگشت سوپرترند به بالا یا شکست سقف کانال با ADX بالای ۲۰؛ خروج با برگشت سوپرترند به پایین. فقط خرید.', 'In on a SuperTrend turn up or a channel breakout with ADX over 20; out when the SuperTrend turns down. Long only.')}</p></> : null}
          {strategy === 'supertrend' ? <div className="row two">{numIn('ATR', p.stLen, (n) => setP({ ...p, stLen: n }))}{numIn(L('ضریب', 'Multiplier'), p.stMult, (n) => setP({ ...p, stMult: n }))}</div> : null}
          <Field label={L('جهت', 'Direction')}><select value={o.direction} onChange={(e) => setO({ ...o, direction: e.target.value as BacktestOptions['direction'] })}><option value="both">{L('خرید و فروش', 'Long and short')}</option><option value="long">{L('فقط خرید', 'Long only')}</option><option value="short">{L('فقط فروش', 'Short only')}</option></select></Field>
          <div className="row two">{numIn(L('حد ضرر (درصد، ۰ = ندارد)', 'Stop % (0 = none)'), o.stopPct, (n) => setO({ ...o, stopPct: Math.max(0, n) }))}{numIn(L('حد سود (درصد، ۰ = ندارد)', 'Target % (0 = none)'), o.targetPct, (n) => setO({ ...o, targetPct: Math.max(0, n) }))}</div>
          <label className="check row-between bt-magnifier"><span className="fa">{L('ذره‌بین کندل', 'Bar magnifier')}</span><input type="checkbox" role="switch" checked={magnify} disabled={!finerInterval(symbol, interval)} onChange={() => setMagnify(!magnify)} /></label>
          {magnify ? <p className="muted small fa">{finerInterval(symbol, interval) ? L(`حد ضرر و حد سودی که در یک کندل لمس شوند با کندل‌های ${intervalLabel(finerInterval(symbol, interval)!)} به ترتیب واقعی‌شان خوانده می‌شوند.`, `A stop and a target touched in the same bar are read in their real order from ${intervalLabel(finerInterval(symbol, interval)!)} bars.`) : null}</p> : null}
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
              {res.trades.length < CONFIDENT_TRADES ? <p className="small fa rec-warn" data-part="thin-sample">{L(`فقط ${faNum(res.trades.length)} معامله بسته شد. زیر ${faNum(CONFIDENT_TRADES)} معامله، این عددها حکایت‌اند نه نتیجه.`, `Only ${res.trades.length} trades closed. Under ${CONFIDENT_TRADES} trades these numbers are anecdotes, not a result.`)}</p> : null}
              <p className="muted small num">{dateTime(res.from * 1000)} → {dateTime(res.to * 1000)}</p>
              {curve ? <svg className="bt-equity big" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-label="equity"><line x1="0" x2={W} y1={curve.base} y2={curve.base} className="bt-zero" /><path d={curve.d} className={res.net >= 0 ? 'up' : 'down'} /></svg> : null}
              <div className="row" data-part="bt-export">
                <button className="btn btn-quiet sm" data-export="csv" onClick={() => { downloadFile(backtestFileName(res.run.symbol, 'csv'), backtestCsv(res.run), 'text/csv;charset=utf-8'); setSaved(L('فایل ذخیره شد.', 'The file was saved.')); }}>{L('خروجی CSV', 'Export a CSV')}</button>
                <button className="btn btn-quiet sm" data-export="xlsx" onClick={() => { downloadFile(backtestFileName(res.run.symbol, 'xlsx'), backtestXlsx(res.run), XLSX_MIME); setSaved(L('فایل ذخیره شد.', 'The file was saved.')); }}>{L('خروجی Excel', 'Export for Excel')}</button>
              </div>
              <p className="muted small fa">{L('همه‌ی معاملات، همه‌ی سنجه‌ها و بازه‌ی اجرا در یک فایل. رایگان — تریدینگ‌ویو برای همین خروجی اشتراک می‌گیرد.', "Every trade, every measure and the run's span in one file. Free — TradingView charges a subscription for this export.")}{saved ? ` ${saved}` : ''}</p>
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
