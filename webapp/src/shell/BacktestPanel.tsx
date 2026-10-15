import { useMemo, useState } from 'react';
import { useScript, scriptTitle } from '../state/script';
import { usePrefs } from '../state/prefs';
import { usePanels } from '../state/panels';
import { digitsOf } from '../state/market';
import { useReplay } from '../state/replay';
import { useChartBus } from '../chart/chartBus';
import { formatPrice } from '../chart/format';
import { runScript } from '../engine/namascript.js';
import { EXAMPLES } from '../engine/scriptlib.js';
import { fromAlerts, fromIndices, optimize, simulate, walkForward, type BtResult, type BtTrade, type OptRow, type Range, type WalkWindow } from '../pages/backtestEngine';
import { Properties } from './StudySettings';
import { dateTime, downloadCsv, useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { Dropdown } from '../ui/Menu';
import { Icon } from '../ui/Icon';
import type { Candle } from '../data/types';
import './studies.css';

// TradingView's Strategy Tester for the NamaScript strategy on the chart (or, without a strategy,
// its first two alert conditions as entry and exit): Overview (the headline figures, equity with
// buy-and-hold and drawdown), Performance (every figure, all / long / short), List of trades (entry
// and exit, price, time, size, profit, cumulative profit, run-up and drawdown; a click shows the
// trade on the chart), Properties (capital, order size, pyramiding, costs, fill, date range), an
// Optimizer (a grid of the script's inputs) and a Walk-forward test, and saved strategies.
type Tab = 'overview' | 'performance' | 'trades' | 'properties' | 'optimizer' | 'walk';

const money = (v: number | undefined) => (v == null || !Number.isFinite(v) ? '—' : `${v < 0 ? '−' : ''}${Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);
const pct = (v: number | undefined) => (v == null || !Number.isFinite(v) ? '—' : `${v < 0 ? '−' : ''}${Math.abs(v).toFixed(2)}%`);
const tone = (v: number | undefined) => (v == null ? '' : v > 0 ? 'up' : v < 0 ? 'down' : '');
const view = (): Candle[] => useReplay.getState().slice ?? useChartBus.getState().bars;

/** Runs the strategy script over bars with inputs and returns its report (for the optimizer). */
async function backtestRun(code: string, bars: Candle[], inputs: Record<string, unknown>): Promise<BtResult | null> {
  const r = await runScript(code, bars, inputs);
  if (!r.ok) return null;
  const props = useScript.getState().props;
  if (r.signals) return simulate(bars, fromIndices(bars.length, r.signals), props);
  const al = (r.alerts ?? []).filter((a) => !a.dynamic);
  return al.length >= 2 ? simulate(bars, fromAlerts(bars, al[0]!.bars, al[1]!.bars), props) : null;
}

/** Scrolls the chart to a trade (a click in the list of trades). */
function showTrade(t: BtTrade) {
  const api = useChartBus.getState().api; const bars = view();
  if (!api || !bars.length) return;
  const a = bars.findIndex((b) => b.t >= t.entryT); const b = bars.findIndex((x) => x.t >= t.exitT);
  const from = Math.max(0, (a < 0 ? bars.length - 1 : a) - 30); const to = (b < 0 ? bars.length - 1 : b) + 30;
  api.timeScale().setVisibleLogicalRange({ from, to });
}

export function BacktestPanel() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const s = useScript();
  const setBottom = usePanels((p) => p.setBottom);
  const [tab, setTab] = useState<Tab>('overview');
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const bt = s.on ? s.backtest : null;
  const digits = Math.min(digitsOf(symbol), 6);
  const fromAlertsOnly = Boolean(bt && s.result?.ok && !s.result.signals);
  const name = scriptTitle(s.runCode) ?? s.tabs.find((t) => t.id === s.active)?.name ?? 'NamaScript';

  if (!bt) {
    const examples = EXAMPLES.map((x, i) => ({ ...x, i })).filter((x) => x.kind === 'strategy').slice(0, 6);
    return (
      <div className={`bt-empty${fa ? '' : ' sa-ltr'}`}>
        <p className="fa">{s.on && s.result?.ok ? L('اسکریپت روی چارت استراتژی ندارد؛ با strategy.entry یا دو alertcondition (ورود و خروج) آن را آزمون کنید.', 'The script on the chart has no strategy: use strategy.entry, or two alert conditions (entry and exit).') : L('برای آزمون، یک استراتژی نمااسکریپت روی چارت بگذارید.', 'Put a NamaScript strategy on the chart to test it.')}</p>
        <div className="bt-examples">
          {examples.map((x) => <button key={x.i} className="chip" onClick={() => { useScript.getState().newTab(x.code, x.name.replace(/^[^\p{L}\p{N}]+/u, '').trim()); useScript.getState().apply(); }}><span className="fa">{x.name.replace(/^[^\p{L}\p{N}]+/u, '').trim()}</span></button>)}
        </div>
        <div className="row">
          <button className="btn btn-primary sm" onClick={() => setBottom('namascript')}><span className="fa">{L('نمااسکریپت', 'NamaScript')}</span></button>
          {s.strategies.length ? <button className="btn btn-quiet sm" onClick={(e) => setMenu(e.currentTarget)}><Icon name="ph-folder-open" size="sm" /><span className="fa">{L('استراتژی‌های ذخیره‌شده', 'Saved strategies')}</span></button> : null}
        </div>
        {menu ? <SavedMenu anchor={menu} onClose={() => setMenu(null)} /> : null}
      </div>
    );
  }

  const tabs: { id: Tab; fa: string; en: string }[] = [
    { id: 'overview', fa: 'نمای کلی', en: 'Overview' }, { id: 'performance', fa: 'عملکرد', en: 'Performance' }, { id: 'trades', fa: 'فهرست معامله‌ها', en: 'List of trades' },
    { id: 'properties', fa: 'ویژگی‌ها', en: 'Properties' }, { id: 'optimizer', fa: 'بهینه‌ساز', en: 'Optimizer' }, { id: 'walk', fa: 'آزمون پیش‌رونده', en: 'Walk-forward' },
  ];
  return (
    <div className={`bt bt2${fa ? '' : ' sa-ltr'}`} dir={fa ? 'rtl' : 'ltr'}>
      <div className="bt-head">
        <b className="bt-name" dir="auto">{name}</b>
        {fromAlertsOnly ? <span className="bt-badge fa">{L('از alertconditionها', 'from alert conditions')}</span> : null}
        <button className="ib" title={L('ویژگی‌های استراتژی', 'Strategy properties')} onClick={() => window.dispatchEvent(new CustomEvent('pc:study-settings', { detail: { uid: 'script' } }))}><Icon name="ph-gear" size="sm" /></button>
        <nav className="bt-tabs" role="tablist">
          {tabs.map((x) => <button key={x.id} role="tab" aria-selected={tab === x.id} className={`bt-tab${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}><span className="fa">{fa ? x.fa : x.en}</span></button>)}
        </nav>
        <span className="ws-spacer" />
        <button className="ib" title={L('استراتژی‌های ذخیره‌شده', 'Saved strategies')} onClick={(e) => setMenu(e.currentTarget)}><Icon name="ph-folder-open" size="sm" /></button>
        <button className="ib" title={L('خروجی CSV معامله‌ها', 'Export trades (CSV)')} onClick={() => downloadCsv(`${symbol}-trades.csv`, [['#', 'type', 'entry time', 'entry', 'exit time', 'exit', 'qty', 'profit', 'profit %', 'cum profit', 'run-up', 'drawdown', 'bars'], ...bt.trades.map((t, i) => [i + 1, t.dir === 1 ? 'long' : 'short', new Date(t.entryT * 1000).toISOString(), t.entry, new Date(t.exitT * 1000).toISOString(), t.exit, +(t.qty ?? 0).toPrecision(6), +t.pnl.toFixed(2), +t.pct.toFixed(2), +(t.cum ?? 0).toFixed(2), +(t.runUp ?? 0).toFixed(2), +(t.drawdown ?? 0).toFixed(2), t.bars ?? 0])])}><Icon name="ph-download-simple" size="sm" /></button>
      </div>
      <div className="bt-content">
        {tab === 'overview' ? <Overview bt={bt} digits={digits} /> : null}
        {tab === 'performance' ? <Performance bt={bt} /> : null}
        {tab === 'trades' ? <Trades bt={bt} digits={digits} /> : null}
        {tab === 'properties' ? <div className="bt-props st-dialog" dir={fa ? 'rtl' : 'ltr'}><div className="st-body"><Properties /></div></div> : null}
        {tab === 'optimizer' ? <Optimizer /> : null}
        {tab === 'walk' ? <Walk /> : null}
      </div>
      {menu ? <SavedMenu anchor={menu} onClose={() => setMenu(null)} /> : null}
    </div>
  );
}

function SavedMenu({ anchor, onClose }: { anchor: HTMLElement; onClose(): void }) {
  const L = useL();
  const s = useScript();
  return (
    <Dropdown anchor={anchor} onClose={onClose} align="right" className="ns-menu">
      {s.on ? <button className="menu-item" onClick={() => { const n = window.prompt(L('نام این استراتژی', 'Name of this strategy'), scriptTitle(s.runCode) ?? 'Strategy'); if (n) s.saveStrategy(n); onClose(); }}><Icon name="ph-floppy-disk" size="sm" /><span className="fa">{L('ذخیره‌ی این استراتژی با ویژگی‌ها و ورودی‌ها', 'Save this strategy with its properties and inputs')}</span></button> : null}
      <div className="menu-title fa">{L('استراتژی‌های ذخیره‌شده', 'Saved strategies')}</div>
      {!s.strategies.length ? <div className="menu-item muted fa">{L('هنوز چیزی ذخیره نشده.', 'Nothing saved yet.')}</div> : null}
      {s.strategies.map((x) => (
        <div key={x.id} className="menu-item ns-lib">
          <button className="link-btn" onClick={() => { s.loadStrategy(x.id); onClose(); }}><span dir="auto">{x.name}</span></button>
          <span className="muted num">{dateTime(x.at)}</span>
          <button className="lt" title={L('حذف', 'Delete')} onClick={() => s.deleteStrategy(x.id)}><Icon name="ph-trash" size="sm" /></button>
        </div>
      ))}
    </Dropdown>
  );
}

function Overview({ bt, digits }: { bt: BtResult; digits: number }) {
  const L = useL();
  const [bh, setBh] = useState(true);
  const stats: [string, string, string, string?][] = [
    [L('سود خالص', 'Net profit'), money(bt.net), pct(bt.netPct), tone(bt.net)],
    [L('کل معامله‌های بسته', 'Total closed trades'), String(bt.trades.length), ''],
    [L('درصد سودده', 'Percent profitable'), pct(bt.win), `${bt.wins ?? 0}/${bt.trades.length}`],
    [L('ضریب سود', 'Profit factor'), Number.isFinite(bt.pf) ? bt.pf.toFixed(3) : '∞', ''],
    [L('بیشترین افت سرمایه', 'Max drawdown'), money(bt.dd), pct(bt.ddPct), 'down'],
    [L('میانگین معامله', 'Avg trade'), money(bt.avg), '', tone(bt.avg)],
    [L('میانگین کندل در معامله', 'Avg # bars in trades'), (bt.avgBars ?? 0).toFixed(0), ''],
  ];
  return (
    <div className="bt-over">
      <div className="bt-stats">
        {stats.map(([k, v, sub, tn]) => <div key={k} className="bt-stat"><span className="bt-k fa">{k}</span><span className={`bt-v num ${tn ?? ''}`}>{v}</span>{sub ? <span className={`bt-sub num ${tn ?? ''}`}>{sub}</span> : null}</div>)}
      </div>
      <EquityChart bt={bt} buyHold={bh} digits={digits} />
      <label className="bt-legend fa"><input type="checkbox" checked={bh} onChange={(e) => setBh(e.target.checked)} />{L('خرید و نگه‌داری', 'Buy & hold')} <span className="num">{pct(bt.buyHold)}</span></label>
    </div>
  );
}

function EquityChart({ bt, buyHold, digits }: { bt: BtResult; buyHold: boolean; digits: number }) {
  const [hover, setHover] = useState<number | null>(null);
  void digits;
  const eq = bt.equity; const bh = buyHold ? bt.buyHoldCurve ?? [] : []; const dd = bt.drawdown ?? [];
  if (eq.length < 2) return <div className="bt-chart empty" />;
  const W = 800, H = 150, D = 46;
  const vals = [...eq.map((p) => p.e), ...bh.map((p) => p.e), bt.capital ?? 0];
  const min = Math.min(...vals), max = Math.max(...vals);
  const x = (i: number) => (i / (eq.length - 1)) * W;
  const y = (v: number) => H - ((v - min) / (max - min || 1)) * (H - 8) - 4;
  const ddMin = Math.min(-0.0001, ...dd.map((p) => p.dd));
  const yd = (v: number) => H + 4 + (v / ddMin) * D;
  const path = (pts: { e: number }[]) => pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.e).toFixed(1)}`).join(' ');
  const ddPath = `M0,${H + 4} ${dd.map((p, i) => `L${x(i).toFixed(1)},${yd(p.dd).toFixed(1)}`).join(' ')} L${W},${H + 4} Z`;
  const base = y(bt.capital ?? 0);
  const h = hover != null ? eq[hover] : null;
  return (
    <div className="bt-chart" onMouseLeave={() => setHover(null)} onMouseMove={(e) => { const r = e.currentTarget.getBoundingClientRect(); const fx = (e.clientX - r.left) / r.width; setHover(Math.max(0, Math.min(eq.length - 1, Math.round(fx * (eq.length - 1))))); }}>
      <svg viewBox={`0 0 ${W} ${H + D + 8}`} preserveAspectRatio="none" aria-label="equity">
        <line x1="0" x2={W} y1={base} y2={base} className="bt-zero" />
        <path d={ddPath} className="bt-dd" />
        {bh.length ? <path d={path(bh)} className="bt-bh" /> : null}
        <path d={path(eq)} className={`bt-eq ${bt.net >= 0 ? 'up' : 'down'}`} />
        {hover != null ? <line x1={x(hover)} x2={x(hover)} y1="0" y2={H + D + 8} className="bt-cross" /> : null}
      </svg>
      {h ? <div className="bt-tip num" style={{ left: `${(hover! / (eq.length - 1)) * 100}%` }}>{dateTime(h.t * 1000)} · {money(h.e)}{dd[hover!] ? ` · DD ${pct(dd[hover!]!.dd)}` : ''}</div> : null}
    </div>
  );
}

function sideRow(list: BtTrade[]) {
  const w = list.filter((t) => t.pnl > 0); const l = list.filter((t) => t.pnl <= 0);
  const gp = w.reduce((n, t) => n + t.pnl, 0); const gl = l.reduce((n, t) => n + t.pnl, 0);
  const bars = (x: BtTrade[]) => (x.length ? x.reduce((n, t) => n + (t.bars ?? 0), 0) / x.length : 0);
  return {
    net: gp + gl, gp, gl: -gl, pf: gl ? gp / -gl : gp ? Infinity : 0, n: list.length, wins: w.length, losses: l.length, win: list.length ? (w.length / list.length) * 100 : 0,
    avg: list.length ? (gp + gl) / list.length : 0, avgW: w.length ? gp / w.length : 0, avgL: l.length ? gl / l.length : 0,
    ratio: l.length && w.length ? (gp / w.length) / Math.abs(gl / l.length) : 0, big: w.reduce((m, t) => Math.max(m, t.pnl), 0), small: l.reduce((m, t) => Math.min(m, t.pnl), 0),
    fees: list.reduce((n, t) => n + (t.fee ?? 0), 0), bars: bars(list), barsW: bars(w), barsL: bars(l),
  };
}

function Performance({ bt }: { bt: BtResult }) {
  const L = useL();
  const all = sideRow(bt.trades); const lo = sideRow(bt.trades.filter((t) => t.dir === 1)); const sh = sideRow(bt.trades.filter((t) => t.dir === -1));
  const row = (label: string, f: (x: typeof all) => string, only?: string) => [label, f(all), only ?? f(lo), only ?? f(sh)] as const;
  const rows = [
    row(L('سود خالص', 'Net profit'), (x) => money(x.net)),
    row(L('سود ناخالص', 'Gross profit'), (x) => money(x.gp)),
    row(L('زیان ناخالص', 'Gross loss'), (x) => money(x.gl)),
    [L('بیشترین رشد سرمایه', 'Max equity run-up'), `${money(bt.maxRunUp)} (${pct(bt.maxRunUpPct)})`, '', ''],
    [L('بیشترین افت سرمایه', 'Max equity drawdown'), `${money(bt.dd)} (${pct(bt.ddPct)})`, '', ''],
    [L('بازده خرید و نگه‌داری', 'Buy & hold return'), pct(bt.buyHold), '', ''],
    [L('نسبت شارپ', 'Sharpe ratio'), bt.sharpe.toFixed(3), '', ''],
    [L('نسبت سورتینو', 'Sortino ratio'), (bt.sortino ?? 0).toFixed(3), '', ''],
    row(L('ضریب سود', 'Profit factor'), (x) => (Number.isFinite(x.pf) ? x.pf.toFixed(3) : '∞')),
    [L('سود و زیان باز', 'Open P&L'), money(bt.openPnl), '', ''],
    row(L('کارمزد پرداختی', 'Commission paid'), (x) => money(x.fees)),
    row(L('کل معامله‌های بسته', 'Total closed trades'), (x) => String(x.n)),
    row(L('معامله‌های سودده', 'Number winning trades'), (x) => String(x.wins)),
    row(L('معامله‌های زیان‌ده', 'Number losing trades'), (x) => String(x.losses)),
    row(L('درصد سودده', 'Percent profitable'), (x) => pct(x.win)),
    row(L('میانگین معامله', 'Avg trade'), (x) => money(x.avg)),
    row(L('میانگین معامله‌ی سودده', 'Avg winning trade'), (x) => money(x.avgW)),
    row(L('میانگین معامله‌ی زیان‌ده', 'Avg losing trade'), (x) => money(x.avgL)),
    row(L('نسبت میانگین سود به زیان', 'Ratio avg win / avg loss'), (x) => x.ratio.toFixed(3)),
    row(L('بزرگ‌ترین معامله‌ی سودده', 'Largest winning trade'), (x) => money(x.big)),
    row(L('بزرگ‌ترین معامله‌ی زیان‌ده', 'Largest losing trade'), (x) => money(x.small)),
    [L('امید ریاضی هر معامله', 'Expectancy'), money(bt.expectancy), '', ''],
    [L('بیشترین بردهای پیاپی', 'Max consecutive wins'), String(bt.winStreak ?? 0), '', ''],
    [L('بیشترین باخت‌های پیاپی', 'Max consecutive losses'), String(bt.lossStreak ?? 0), '', ''],
    row(L('میانگین کندل در معامله', 'Avg # bars in trades'), (x) => x.bars.toFixed(0)),
    row(L('میانگین کندل در سودده‌ها', 'Avg # bars in winning trades'), (x) => x.barsW.toFixed(0)),
    row(L('میانگین کندل در زیان‌ده‌ها', 'Avg # bars in losing trades'), (x) => x.barsL.toFixed(0)),
    [L('زمان در بازار', 'Exposure'), pct(bt.exposure), '', ''],
  ];
  return (
    <div className="bt-table-wrap">
      <table className="bt-table">
        <thead><tr><th /><th className="fa">{L('همه', 'All')}</th><th className="fa">{L('خرید', 'Long')}</th><th className="fa">{L('فروش', 'Short')}</th></tr></thead>
        <tbody>{rows.map(([k, a, b, c]) => <tr key={k}><td className="fa">{k}</td><td className={`num ${/^−/.test(a) ? 'down' : ''}`}>{a}</td><td className="num">{b}</td><td className="num">{c}</td></tr>)}</tbody>
      </table>
    </div>
  );
}

function Trades({ bt, digits }: { bt: BtResult; digits: number }) {
  const L = useL();
  const [sel, setSel] = useState<number | null>(null);
  const [desc, setDesc] = useState(true);
  const list = desc ? [...bt.trades].reverse() : bt.trades;
  const n = bt.trades.length;
  const time = (t: number) => dateTime(t * 1000);
  return (
    <div className="bt-table-wrap">
      <table className="bt-table trades">
        <thead><tr>
          <th><button className="link-btn num" onClick={() => setDesc(!desc)}>#{desc ? '↓' : '↑'}</button></th><th className="fa">{L('نوع', 'Type')}</th><th className="fa">{L('سیگنال', 'Signal')}</th><th className="fa">{L('زمان', 'Date/Time')}</th><th className="fa">{L('قیمت', 'Price')}</th>
          <th className="fa">{L('مقدار', 'Contracts')}</th><th className="fa">{L('سود', 'Profit')}</th><th className="fa">{L('سود تجمعی', 'Cum. profit')}</th><th className="fa">{L('بیشترین رشد', 'Run-up')}</th><th className="fa">{L('بیشترین افت', 'Drawdown')}</th>
        </tr></thead>
        <tbody>
          {list.map((t, k) => {
            const i = desc ? n - k : k + 1;
            const long = t.dir === 1;
            return (
              <tr key={i} className={`bt-trade${sel === i ? ' on' : ''}`} onClick={() => { setSel(i); showTrade(t); }} title={L('نمایش روی چارت', 'Show on the chart')}>
                <td className="num">{i}</td>
                <td><div className={long ? 'up' : 'down'}>{L(long ? 'خروج خرید' : 'خروج فروش', long ? 'Exit long' : 'Exit short')}</div><div className={long ? 'up' : 'down'}>{L(long ? 'ورود خرید' : 'ورود فروش', long ? 'Entry long' : 'Entry short')}</div></td>
                <td className="num"><div>{t.reason === 'stop' ? 'SL' : t.reason === 'target' ? 'TP' : t.reason === 'end' ? 'Open' : 'Close'}</div><div>{long ? 'Long' : 'Short'}</div></td>
                <td className="num"><div>{time(t.exitT)}</div><div>{time(t.entryT)}</div></td>
                <td className="num"><div>{formatPrice(t.exit, digits)}</div><div>{formatPrice(t.entry, digits)}</div></td>
                <td className="num">{(t.qty ?? 0).toPrecision(4)}</td>
                <td className={`num ${tone(t.pnl)}`}><div>{money(t.pnl)}</div><div>{pct(t.pct)}</div></td>
                <td className={`num ${tone(t.cum)}`}>{money(t.cum)}</td>
                <td className="num up">{money(t.runUp)}</td>
                <td className="num down">{money(t.drawdown)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** The script's numeric inputs, each with a range to try. */
function useRanges(): [Range[], (r: Range[]) => void, { key: string; def: number }[]] {
  const result = useScript((s) => s.result);
  const inputs = useScript((s) => s.inputs);
  const numeric = useMemo(() => (result?.ok ? result.inputs ?? [] : []).filter((d) => d.type === 'int' || d.type === 'float').map((d) => ({ key: d.key, def: Number(inputs[d.key] ?? d.def) })), [result, inputs]);
  const [ranges, setRanges] = useState<Range[]>(() => numeric.slice(0, 2).map((d) => ({ key: d.key, from: Math.max(1, Math.round(d.def * 0.5)), to: Math.round(d.def * 1.5) || 10, step: Math.max(1, Math.round(d.def * 0.25)) })));
  return [ranges, setRanges, numeric];
}

function RangeEditor({ ranges, setRanges, numeric }: { ranges: Range[]; setRanges(r: Range[]): void; numeric: { key: string; def: number }[] }) {
  const L = useL();
  if (!numeric.length) return <p className="muted fa">{L('این اسکریپت ورودی عددی ندارد؛ با input.int یا input.float ورودی بسازید.', 'This script has no numeric inputs: add some with input.int or input.float.')}</p>;
  return (
    <div className="bt-ranges">
      {numeric.map((d) => {
        const r = ranges.find((x) => x.key === d.key);
        const set = (p: Partial<Range>) => setRanges(ranges.map((x) => (x.key === d.key ? { ...x, ...p } : x)));
        return (
          <div key={d.key} className={`bt-range${r ? '' : ' off'}`}>
            <label className="st-check"><input type="checkbox" checked={Boolean(r)} onChange={(e) => setRanges(e.target.checked ? [...ranges, { key: d.key, from: Math.max(1, Math.round(d.def * 0.5)), to: Math.round(d.def * 1.5) || 10, step: Math.max(1, Math.round(d.def * 0.25)) }].slice(-3) : ranges.filter((x) => x.key !== d.key))} /><span dir="auto">{d.key}</span></label>
            {r ? <span className="num bt-range-nums">
              <input type="number" value={r.from} onChange={(e) => set({ from: Number(e.target.value) })} aria-label="from" /> –
              <input type="number" value={r.to} onChange={(e) => set({ to: Number(e.target.value) })} aria-label="to" /> ×
              <input type="number" value={r.step} onChange={(e) => set({ step: Number(e.target.value) })} aria-label="step" />
            </span> : null}
          </div>
        );
      })}
    </div>
  );
}

function Optimizer() {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const [ranges, setRanges, numeric] = useRanges();
  const [rows, setRows] = useState<OptRow[] | null>(null);
  const [progress, setProgress] = useState<[number, number] | null>(null);
  const run = async () => {
    const st = useScript.getState(); const bars = view();
    setRows(null);
    const out = await optimize(ranges, (v) => backtestRun(st.runCode, bars, { ...st.inputs, ...v }), (d, t) => setProgress([d, t]));
    setRows(out); setProgress(null);
  };
  return (
    <div className="bt-opt">
      <div className="bt-opt-side">
        <RangeEditor ranges={ranges} setRanges={setRanges} numeric={numeric} />
        <button className="btn btn-primary sm" disabled={!ranges.length || Boolean(progress)} onClick={() => void run()}><Icon name="ph-play" size="sm" /><span className="fa">{progress ? L(`${faNum(progress[0])} از ${faNum(progress[1])}`, `${progress[0]} of ${progress[1]}`) : L('اجرای بهینه‌ساز', 'Run the optimizer')}</span></button>
        <p className="muted small fa">{L('هر ترکیب روی همین کندل‌ها با همین ویژگی‌ها آزموده می‌شود. نتیجه‌ی بهترین را با احتیاط بخوانید: بیش‌برازش.', 'Each combination is tested on these bars with these properties. Read the best with care: overfitting.')}</p>
      </div>
      <div className="bt-table-wrap">
        {rows ? (
          <table className="bt-table">
            <thead><tr>{ranges.map((r) => <th key={r.key} dir="auto">{r.key}</th>)}<th className="fa">{L('سود خالص', 'Net profit')}</th><th>%</th><th className="fa">{L('ضریب سود', 'PF')}</th><th className="fa">{L('برد', 'Win')}</th><th className="fa">{L('افت', 'DD')}</th><th className="fa">{L('معامله', 'Trades')}</th><th /></tr></thead>
            <tbody>{rows.slice(0, 100).map((r, i) => (
              <tr key={i}>
                {ranges.map((x) => <td key={x.key} className="num">{r.values[x.key]}</td>)}
                <td className={`num ${tone(r.net)}`}>{money(r.net)}</td><td className={`num ${tone(r.netPct)}`}>{pct(r.netPct)}</td><td className="num">{Number.isFinite(r.pf) ? r.pf.toFixed(2) : '∞'}</td><td className="num">{pct(r.win)}</td><td className="num down">{pct(r.dd)}</td><td className="num">{r.trades}</td>
                <td><button className="link-btn fa" onClick={() => useScript.getState().setInputs({ ...useScript.getState().inputs, ...r.values })}>{L('اعمال', 'Apply')}</button></td>
              </tr>
            ))}</tbody>
          </table>
        ) : <p className={`muted ${fa ? 'fa' : ''}`}>{L('بازه‌ها را بگذارید و اجرا کنید.', 'Set the ranges and run.')}</p>}
      </div>
    </div>
  );
}

function Walk() {
  const L = useL();
  const [ranges, setRanges, numeric] = useRanges();
  const [windows, setWindows] = useState(4);
  const [share, setShare] = useState(70);
  const [out, setOut] = useState<WalkWindow[] | null>(null);
  const [busy, setBusy] = useState(false);
  const run = async () => {
    const st = useScript.getState(); setBusy(true); setOut(null);
    const res = await walkForward(view(), windows, share / 100, ranges, (b, v) => backtestRun(st.runCode, b, { ...st.inputs, ...v }));
    setOut(res); setBusy(false);
  };
  const oos = out ? out.reduce((n, w) => n + (w.outSample?.netPct ?? 0), 0) : 0;
  const ins = out ? out.reduce((n, w) => n + (w.inSample?.netPct ?? 0), 0) : 0;
  return (
    <div className="bt-opt">
      <div className="bt-opt-side">
        <RangeEditor ranges={ranges} setRanges={setRanges} numeric={numeric} />
        <div className="bt-range"><span className="fa">{L('تعداد پنجره', 'Windows')}</span><input type="number" min={2} max={12} value={windows} onChange={(e) => setWindows(Math.max(2, Math.min(12, Number(e.target.value))))} /></div>
        <div className="bt-range"><span className="fa">{L('سهم آموزش (درصد)', 'In-sample share (%)')}</span><input type="number" min={40} max={90} value={share} onChange={(e) => setShare(Math.max(40, Math.min(90, Number(e.target.value))))} /></div>
        <button className="btn btn-primary sm" disabled={!ranges.length || busy} onClick={() => void run()}><Icon name="ph-play" size="sm" /><span className="fa">{busy ? L('در حال اجرا…', 'Running…') : L('اجرای آزمون پیش‌رونده', 'Run walk-forward')}</span></button>
      </div>
      <div className="bt-table-wrap">
        {out ? (
          <>
            <p className="bt-wf-sum fa">{L(`بازده بیرون از نمونه ${pct(oos)} در برابر ${pct(ins)} درون نمونه`, `Out-of-sample ${pct(oos)} against ${pct(ins)} in sample`)}{ins ? ` · ${L('کارایی', 'efficiency')} ${pct((oos / ins) * 100)}` : ''}</p>
            <table className="bt-table">
              <thead><tr><th className="fa">{L('پنجره', 'Window')}</th><th className="fa">{L('بازه', 'Period')}</th><th className="fa">{L('بهترین ورودی‌ها', 'Best inputs')}</th><th className="fa">{L('درون نمونه', 'In sample')}</th><th className="fa">{L('بیرون از نمونه', 'Out of sample')}</th><th className="fa">{L('معامله', 'Trades')}</th></tr></thead>
              <tbody>{out.map((w, i) => (
                <tr key={i}><td className="num">{i + 1}</td><td className="num">{dateTime(w.from * 1000).slice(0, 10)} → {dateTime(w.to * 1000).slice(0, 10)}</td>
                  <td className="num">{w.best ? Object.entries(w.best).map(([k, v]) => `${k}=${v}`).join(', ') : '—'}</td>
                  <td className={`num ${tone(w.inSample?.netPct)}`}>{pct(w.inSample?.netPct)}</td><td className={`num ${tone(w.outSample?.netPct)}`}>{pct(w.outSample?.netPct)}</td><td className="num">{w.outSample?.trades ?? 0}</td></tr>
              ))}</tbody>
            </table>
          </>
        ) : null}
      </div>
    </div>
  );
}
