import { useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useWatchlist } from '../state/watchlist';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { Tabs, pct } from './kit';
import { Icon } from '../ui/Icon';
import type { Ticker } from '../data/types';

// The app's Markets hub (feature/search MarketsScreen): pages Top · Popular · Movers · Volume ·
// Forex · Metals, lenses, a change/turnover filter, the market's pulse on top, every row opening
// its chart. All from the live tickers both platforms publish.
type PageKey = 'top' | 'popular' | 'movers' | 'volume' | 'forex' | 'metals';
type Lens = 'all' | 'favourites' | 'gainers' | 'losers';
const POPULAR = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'DOGEUSDT', 'ADAUSDT', 'TRXUSDT', 'TONUSDT', 'LTCUSDT', 'XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY'];

const turnover = (tk: Ticker) => (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);

export default function MarketsPage() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const open = usePage((s) => s.open);
  const watch = useWatchlist((s) => s.symbols);
  const add = useWatchlist((s) => s.add);
  const [page, setPage] = useState<PageKey>('top');
  const [lens, setLens] = useState<Lens>('all');
  const [minMove, setMinMove] = useState(0);
  const [minTurnover, setMinTurnover] = useState(0);
  const [q, setQ] = useState('');

  const all = useMemo(() => Object.values(tickers).filter((x) => hasLogo(x.symbol)), [tickers]);
  const pulse = useMemo(() => {
    const crypto = all.filter((x) => marketOf(x.symbol) === 'crypto');
    const up = crypto.filter((x) => x.changePct > 0).length;
    const sorted = crypto.map((x) => x.changePct).sort((a, b) => a - b);
    const median = sorted.length ? sorted[Math.floor(sorted.length / 2)]! : 0;
    const total = crypto.reduce((n, x) => n + turnover(x), 0);
    const btc = tickers.BTCUSDT ? turnover(tickers.BTCUSDT) / (total || 1) : 0;
    return { up, down: crypto.length - up, median, total, btc };
  }, [all, tickers]);

  const rows = useMemo(() => {
    let list = all;
    if (page === 'popular') list = POPULAR.map((s) => tickers[s]).filter((x): x is Ticker => Boolean(x));
    if (page === 'forex') list = list.filter((x) => marketOf(x.symbol) === 'forex');
    if (page === 'metals') list = list.filter((x) => ['metals', 'energy', 'indices'].includes(marketOf(x.symbol)));
    if (lens === 'favourites') list = list.filter((x) => watch.includes(x.symbol));
    if (lens === 'gainers') list = list.filter((x) => x.changePct > 0);
    if (lens === 'losers') list = list.filter((x) => x.changePct < 0);
    if (minMove) list = list.filter((x) => Math.abs(x.changePct) >= minMove);
    if (minTurnover) list = list.filter((x) => turnover(x) >= minTurnover);
    const needle = q.trim().toUpperCase();
    if (needle) list = list.filter((x) => x.symbol.includes(needle) || (instrumentOf(x.symbol).nameFa ?? '').includes(q.trim()));
    const by = page === 'movers' ? (x: Ticker) => Math.abs(x.changePct) : lens === 'gainers' ? (x: Ticker) => x.changePct : lens === 'losers' ? (x: Ticker) => -x.changePct : turnover;
    if (page !== 'popular') list = [...list].sort((a, b) => by(b) - by(a));
    return list.slice(0, 300);
  }, [all, tickers, page, lens, minMove, minTurnover, q, watch]);

  const openChart = (s: string) => { setSymbol(s); close(); };

  return (
    <div className="page-inner">
      <div className="pulse">
        <div className="card"><span className="muted fa">{t('mkBreadth')}</span><b className="num"><span className="up">{pulse.up}</span> / <span className="down">{pulse.down}</span></b></div>
        <div className="card"><span className="muted fa">{t('mkMedian')}</span><b className={`num ${pulse.median >= 0 ? 'up' : 'down'}`}>{pct(pulse.median)}</b></div>
        <div className="card"><span className="muted fa">{t('mkTurnover')}</span><b className="num">{formatVolume(pulse.total)} USDT</b></div>
        <div className="card"><span className="muted fa">{t('mkBtcShare')}</span><b className="num">{(pulse.btc * 100).toFixed(1)}%</b></div>
        <div className="card doors">
          <button className="link-btn" onClick={() => open('heatmap')}>{t('heatmap')}</button>
          <button className="link-btn" onClick={() => open('news')}>{t('news')}</button>
          <button className="link-btn" onClick={() => open('calendar')}>{t('calendar')}</button>
          <button className="link-btn" onClick={() => open('screener')}>{t('screener')}</button>
        </div>
      </div>
      <Tabs value={page} onChange={setPage} tabs={[
        { id: 'top', label: t('mkTop') }, { id: 'popular', label: t('mkPopular') }, { id: 'movers', label: t('mkMovers') },
        { id: 'volume', label: t('mkVolume') }, { id: 'forex', label: t('forex') }, { id: 'metals', label: t('mkMetalsMore') },
      ]} />
      <div className="row market-filters">
        <Tabs value={lens} onChange={setLens} tabs={[
          { id: 'all', label: t('all') }, { id: 'favourites', label: t('watchlist') }, { id: 'gainers', label: t('mkGainers') }, { id: 'losers', label: t('mkLosers') },
        ]} />
        <select value={minMove} onChange={(e) => setMinMove(Number(e.target.value))} aria-label="move">
          <option value={0}>{t('mkAnyMove')}</option><option value={2}>±2%</option><option value={5}>±5%</option><option value={10}>±10%</option>
        </select>
        <select value={minTurnover} onChange={(e) => setMinTurnover(Number(e.target.value))} aria-label="turnover">
          <option value={0}>{t('mkAnyTurnover')}</option><option value={1e6}>≥ 1M</option><option value={1e7}>≥ 10M</option><option value={1e8}>≥ 100M</option>
        </select>
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} dir="auto" />
      </div>
      <table className="table">
        <thead><tr><th>{t('symbol')}</th><th /><th className="r">{t('last')}</th><th className="r">{t('changePct')}</th><th className="r">{t('mkTurnover')}</th><th className="r">{t('dayRange')}</th><th /></tr></thead>
        <tbody>
          {rows.map((x) => {
            const inst = instrumentOf(x.symbol, x.last);
            const range = x.high != null && x.low != null && x.high > x.low ? (x.last - x.low) / (x.high - x.low) : null;
            return (
              <tr key={x.symbol} className="click" onClick={() => openChart(x.symbol)}>
                <td><span className="row"><SymbolLogo symbol={x.symbol} size={20} /><b className="sym">{x.symbol}</b></span></td>
                <td className={`muted ${lang === 'fa' ? 'fa' : ''}`}>{lang === 'fa' ? inst.nameFa : inst.nameEn}</td>
                <td className="r num">{formatPrice(x.last, inst.digits)}</td>
                <td className={`r num ${x.changePct >= 0 ? 'up' : 'down'}`}>{pct(x.changePct)}</td>
                <td className="r num">{turnover(x) ? formatVolume(turnover(x)) : '—'}</td>
                <td className="r">{range != null ? <span className="range-bar"><i style={{ left: `${range * 100}%` }} /></span> : null}</td>
                <td className="r"><button className="lt" title={t('watchlist')} onClick={(e) => { e.stopPropagation(); add(x.symbol); }}><Icon name={watch.includes(x.symbol) ? 'ph-star-fill' : 'ph-star'} size="sm" /></button></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
