import { useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { loadHistory } from '../data/candles';
import { rsi, ema, macd, bollinger, atr } from '../engine/indicators.js';
import { Tabs, downloadCsv, pct, useL } from './kit';
import { useWatchlist } from '../state/watchlist';
import type { PageProps } from './registry';
import { Icon } from '../ui/Icon';
import type { Candle, Market, Ticker } from '../data/types';

// The app's screener (feature/screener): field conditions on the live tickers, indicator
// conditions scanned over each symbol's bars (RSI, EMA cross, MACD cross, Bollinger, new high/low,
// ATR), presets, saved screens and CSV export.
type Field = 'changePct' | 'last' | 'turnover' | 'range';
type Op = '>' | '<';
interface Cond { field: Field; op: Op; value: number }
type Setup = 'none' | 'rsiLow' | 'rsiHigh' | 'emaCrossUp' | 'emaCrossDown' | 'macdUp' | 'macdDown' | 'bbBreakUp' | 'bbBreakDown' | 'newHigh' | 'newLow' | 'squeeze';
interface Saved { name: string; market: Market | 'all'; conds: Cond[]; setup: Setup; tf: string }
interface ScanRow { symbol: string; rsi: number | null; atrPct: number | null; hit: boolean; barsAgo: number | null }

const SAVED_KEY = 'pc.screens.v1';
const turnover = (x: Ticker) => (x.volume ?? 0) * (marketOf(x.symbol) === 'crypto' ? x.last : 1);
const fieldOf = (x: Ticker, f: Field) => (f === 'changePct' ? x.changePct : f === 'last' ? x.last : f === 'turnover' ? turnover(x) : x.high && x.low ? ((x.high - x.low) / x.low) * 100 : 0);

function detect(setup: Setup, bars: Candle[]): { hit: boolean; barsAgo: number | null; rsi: number | null; atrPct: number | null } {
  const c = bars.map((b) => b.c), h = bars.map((b) => b.h), l = bars.map((b) => b.l);
  const r = rsi(c, 14) as (number | null)[];
  const a = atr(h, l, c, 14) as (number | null)[];
  const n = c.length - 1;
  const last = r[n] ?? null;
  const atrPct = a[n] != null && c[n] ? ((a[n] as number) / c[n]!) * 100 : null;
  const within = (fn: (i: number) => boolean, look = 3): number | null => { for (let k = 0; k <= look; k++) if (n - k > 0 && fn(n - k)) return k; return null; };
  let ago: number | null = null;
  if (setup === 'rsiLow') ago = last != null && last < 30 ? 0 : null;
  if (setup === 'rsiHigh') ago = last != null && last > 70 ? 0 : null;
  if (setup === 'emaCrossUp' || setup === 'emaCrossDown') {
    const f = ema(c, 20) as (number | null)[], s = ema(c, 50) as (number | null)[];
    ago = within((i) => f[i] != null && s[i] != null && f[i - 1] != null && s[i - 1] != null && (setup === 'emaCrossUp' ? f[i - 1]! <= s[i - 1]! && f[i]! > s[i]! : f[i - 1]! >= s[i - 1]! && f[i]! < s[i]!));
  }
  if (setup === 'macdUp' || setup === 'macdDown') {
    const m = macd(c, 12, 26, 9) as { macd: (number | null)[]; signal: (number | null)[] };
    ago = within((i) => m.macd[i] != null && m.signal[i] != null && m.macd[i - 1] != null && m.signal[i - 1] != null && (setup === 'macdUp' ? m.macd[i - 1]! <= m.signal[i - 1]! && m.macd[i]! > m.signal[i]! : m.macd[i - 1]! >= m.signal[i - 1]! && m.macd[i]! < m.signal[i]!));
  }
  if (setup === 'bbBreakUp' || setup === 'bbBreakDown' || setup === 'squeeze') {
    const b = bollinger(c, 20, 2) as { upper: (number | null)[]; lower: (number | null)[]; basis: (number | null)[] };
    if (setup === 'squeeze') {
      const w = c.map((_, i) => (b.upper[i] != null && b.lower[i] != null && b.basis[i] ? (b.upper[i]! - b.lower[i]!) / b.basis[i]! : null)).filter((x): x is number => x != null);
      const now = w[w.length - 1];
      const sorted = [...w].sort((x, y) => x - y);
      ago = now != null && sorted.length > 20 && now <= sorted[Math.floor(sorted.length * 0.1)]! ? 0 : null;
    } else ago = within((i) => (setup === 'bbBreakUp' ? b.upper[i] != null && c[i]! > b.upper[i]! : b.lower[i] != null && c[i]! < b.lower[i]!));
  }
  if (setup === 'newHigh' || setup === 'newLow') {
    const look = Math.min(c.length - 1, 50);
    const prev = setup === 'newHigh' ? Math.max(...h.slice(n - look, n)) : Math.min(...l.slice(n - look, n));
    ago = setup === 'newHigh' ? (h[n]! > prev ? 0 : null) : (l[n]! < prev ? 0 : null);
  }
  return { hit: setup === 'none' || ago != null, barsAgo: ago, rsi: last, atrPct };
}

export default function ScreenerPage({ param = null }: Partial<PageProps> = {}) {
  const t = useT();
  const L = useL();
  // `wl:<id>` (the watchlist's "Open in screener"): only that list's symbols, no default condition.
  const listId = param?.startsWith('wl:') ? param.slice(3) : null;
  const list = useWatchlist((s) => (listId ? s.lists.find((l) => l.id === listId) ?? null : null));
  const [onlyList, setOnlyList] = useState(Boolean(listId));
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const setInterval = usePrefs((s) => s.setInterval);
  const close = usePage((s) => s.close);
  const [market, setMarket] = useState<Market | 'all'>(listId ? 'all' : 'crypto');
  const [conds, setConds] = useState<Cond[]>(listId ? [] : [{ field: 'turnover', op: '>', value: 1_000_000 }]);
  const [setup, setSetup] = useState<Setup>('none');
  const [tf, setTf] = useState('60');
  const [scan, setScan] = useState<Record<string, ScanRow>>({});
  const [scanning, setScanning] = useState<{ done: number; total: number } | null>(null);
  const [saved, setSaved] = useState<Saved[]>(() => { try { return JSON.parse(localStorage.getItem(SAVED_KEY) ?? '[]') as Saved[]; } catch { return []; } });
  const [name, setName] = useState('');

  const base = useMemo(() => Object.values(tickers)
    .filter((x) => hasLogo(x.symbol) && (market === 'all' || marketOf(x.symbol) === market) && (!onlyList || !list || list.symbols.includes(x.symbol)))
    .filter((x) => conds.every((c) => (c.op === '>' ? fieldOf(x, c.field) > c.value : fieldOf(x, c.field) < c.value)))
    .sort((a, b) => turnover(b) - turnover(a)), [tickers, market, conds, onlyList, list]);

  const rows = setup === 'none' ? base : base.filter((x) => scan[x.symbol]?.hit);

  const runScan = async () => {
    const universe = base.slice(0, 60).map((x) => x.symbol);
    const out: Record<string, ScanRow> = {};
    setScanning({ done: 0, total: universe.length });
    let i = 0;
    const worker = async () => {
      while (i < universe.length) {
        const s = universe[i++]!;
        try {
          const { bars } = await loadHistory(s, tf, undefined, 'bulk');
          const d = detect(setup, bars);
          out[s] = { symbol: s, ...d };
        } catch { /* skipped: a 429 or a symbol without bars */ }
        setScanning((p) => (p ? { ...p, done: p.done + 1 } : p));
      }
    };
    await Promise.all([worker(), worker(), worker(), worker()]);
    setScan(out);
    setScanning(null);
  };

  const save = () => {
    if (!name.trim()) return;
    const next = [{ name: name.trim(), market, conds, setup, tf }, ...saved.filter((s) => s.name !== name.trim())];
    setSaved(next); setName('');
    try { localStorage.setItem(SAVED_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  };
  const load = (s: Saved) => { setMarket(s.market); setConds(s.conds); setSetup(s.setup); setTf(s.tf); setScan({}); };
  const presets: { label: string; apply(): void }[] = [
    { label: t('scrOversold'), apply: () => { setSetup('rsiLow'); setScan({}); } },
    { label: t('scrOverbought'), apply: () => { setSetup('rsiHigh'); setScan({}); } },
    { label: t('scrGoldenCross'), apply: () => { setSetup('emaCrossUp'); setScan({}); } },
    { label: t('scrMacdUp'), apply: () => { setSetup('macdUp'); setScan({}); } },
    { label: t('scrBreakout'), apply: () => { setSetup('bbBreakUp'); setScan({}); } },
    { label: t('scrSqueeze'), apply: () => { setSetup('squeeze'); setScan({}); } },
    { label: t('scrTopGainers'), apply: () => { setSetup('none'); setConds([{ field: 'changePct', op: '>', value: 5 }]); } },
    { label: t('scrTopLosers'), apply: () => { setSetup('none'); setConds([{ field: 'changePct', op: '<', value: -5 }]); } },
  ];

  const exportCsv = () => downloadCsv('screener.csv', [
    ['Symbol', 'Last', 'Change %', 'Turnover', 'Day range %', 'RSI', 'ATR %', 'Bars ago'],
    ...rows.map((x) => [x.symbol, x.last, x.changePct.toFixed(2), Math.round(turnover(x)), fieldOf(x, 'range').toFixed(2), scan[x.symbol]?.rsi?.toFixed(1) ?? '', scan[x.symbol]?.atrPct?.toFixed(2) ?? '', scan[x.symbol]?.barsAgo ?? '']),
  ]);

  return (
    <div className="page-inner">
      <div className="row">
        <Tabs value={market} onChange={(m) => { setMarket(m); setScan({}); }} tabs={[{ id: 'all', label: t('all') }, { id: 'crypto', label: t('crypto') }, { id: 'forex', label: t('forex') }, { id: 'metals', label: t('metals') }, { id: 'indices', label: t('indices') }, { id: 'energy', label: t('energy') }]} />
        {list ? <button className={`chip wl-filter${onlyList ? ' on' : ''}`} aria-pressed={onlyList} onClick={() => setOnlyList(!onlyList)}><span className="fa">{L('فهرست', 'List')}: {list.name}</span></button> : null}
      </div>
      <div className="row presets">{presets.map((p) => <button key={p.label} className="chip" onClick={p.apply}><span className="fa">{p.label}</span></button>)}</div>
      <div className="card scr-conds">
        {conds.map((c, i) => (
          <div key={i} className="row">
            <select value={c.field} onChange={(e) => setConds(conds.map((x, k) => (k === i ? { ...x, field: e.target.value as Field } : x)))}>
              <option value="changePct">{t('changePct')}</option><option value="last">{t('last')}</option><option value="turnover">{t('mkTurnover')}</option><option value="range">{t('dayRange')} %</option>
            </select>
            <select value={c.op} onChange={(e) => setConds(conds.map((x, k) => (k === i ? { ...x, op: e.target.value as Op } : x)))}><option value=">">&gt;</option><option value="<">&lt;</option></select>
            <input type="number" className="num" value={c.value} onChange={(e) => setConds(conds.map((x, k) => (k === i ? { ...x, value: Number(e.target.value) } : x)))} />
            <button className="lt" onClick={() => setConds(conds.filter((_, k) => k !== i))} aria-label={t('remove')}><Icon name="ph-x" size="sm" /></button>
          </div>
        ))}
        <div className="row">
          <button className="btn btn-quiet sm" onClick={() => setConds([...conds, { field: 'changePct', op: '>', value: 0 }])}><Icon name="ph-plus" size="sm" /><span className="fa">{t('scrAddCond')}</span></button>
          <select value={setup} onChange={(e) => { setSetup(e.target.value as Setup); setScan({}); }}>
            <option value="none">{t('scrNoSetup')}</option>
            <option value="rsiLow">RSI(14) &lt; 30</option><option value="rsiHigh">RSI(14) &gt; 70</option>
            <option value="emaCrossUp">EMA20 ↗ EMA50</option><option value="emaCrossDown">EMA20 ↘ EMA50</option>
            <option value="macdUp">MACD ↗ signal</option><option value="macdDown">MACD ↘ signal</option>
            <option value="bbBreakUp">close &gt; BB upper</option><option value="bbBreakDown">close &lt; BB lower</option>
            <option value="newHigh">50-bar high</option><option value="newLow">50-bar low</option><option value="squeeze">BB squeeze</option>
          </select>
          <select value={tf} onChange={(e) => { setTf(e.target.value); setScan({}); }} className="num"><option value="15">15m</option><option value="60">1h</option><option value="240">4h</option><option value="1D">1D</option></select>
          {setup !== 'none' ? <button className="btn btn-primary sm" disabled={Boolean(scanning)} onClick={() => void runScan()}><span className="fa">{scanning ? `${scanning.done}/${scanning.total}` : t('scrScan')}</span></button> : null}
          <span className="ws-spacer" />
          <input className="scr-search" value={name} onChange={(e) => setName(e.target.value)} placeholder={t('layoutName')} />
          <button className="btn btn-quiet sm" onClick={save}><span className="fa">{t('save')}</span></button>
          <button className="btn btn-quiet sm" onClick={exportCsv}>CSV</button>
        </div>
        {saved.length ? <div className="row">{saved.map((s) => <button key={s.name} className="chip" onClick={() => load(s)}>{s.name}</button>)}</div> : null}
      </div>
      <table className="table">
        <thead><tr><th>{t('symbol')}</th><th className="r">{t('last')}</th><th className="r">{t('changePct')}</th><th className="r">{t('mkTurnover')}</th><th className="r">{t('dayRange')} %</th>{setup !== 'none' ? <><th className="r">RSI</th><th className="r">ATR %</th><th className="r">{t('scrBarsAgo')}</th></> : null}</tr></thead>
        <tbody>
          {rows.slice(0, 300).map((x) => {
            const sc = scan[x.symbol];
            return (
              <tr key={x.symbol} className="click" onClick={() => { setSymbol(x.symbol); setInterval(tf); close(); }}>
                <td><span className="row"><SymbolLogo symbol={x.symbol} size={18} /><b className="sym">{x.symbol}</b></span></td>
                <td className="r num">{formatPrice(x.last, instrumentOf(x.symbol, x.last).digits)}</td>
                <td className={`r num ${x.changePct >= 0 ? 'up' : 'down'}`}>{pct(x.changePct)}</td>
                <td className="r num">{formatVolume(turnover(x))}</td>
                <td className="r num">{fieldOf(x, 'range').toFixed(2)}%</td>
                {setup !== 'none' ? <><td className="r num">{sc?.rsi?.toFixed(1) ?? '—'}</td><td className="r num">{sc?.atrPct?.toFixed(2) ?? '—'}</td><td className="r num">{sc?.barsAgo ?? '—'}</td></> : null}
              </tr>
            );
          })}
        </tbody>
      </table>
      {setup !== 'none' && !Object.keys(scan).length && !scanning ? <div className="empty fa">{t('scrPressScan')}</div> : null}
    </div>
  );
}
