import { useEffect, useMemo, useRef, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { useWatchlist } from '../state/watchlist';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { Dropdown } from '../ui/Menu';
import { useFlash } from '../ui/useFlash';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { barsFor } from '../data/symbolStats';
import { technicalRating, RATING_FA, type RatingKey } from '../engine/techRating.js';
import { downloadCsv, useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import type { Market, Ticker } from '../data/types';
import type { StringKey } from '../i18n/strings';
import './panels-x.css';

type Col = 'last' | 'change' | 'changePct' | 'volume' | 'high' | 'low' | 'range' | 'rating';
type SortKey = 'symbol' | Col;
const COLS: Col[] = ['last', 'change', 'changePct', 'volume', 'high', 'low', 'range', 'rating'];
const MARKETS: { id: Market | 'all'; label: StringKey }[] = [
  { id: 'all', label: 'all' }, { id: 'crypto', label: 'crypto' }, { id: 'forex', label: 'forex' },
  { id: 'metals', label: 'metals' }, { id: 'indices', label: 'indices' }, { id: 'energy', label: 'energy' },
];
const RATING_EN: Record<RatingKey, string> = { strongBuy: 'Strong buy', buy: 'Buy', neutral: 'Neutral', sell: 'Sell', strongSell: 'Strong sell' };
const RATING_ORDER: Record<RatingKey, number> = { strongSell: -2, sell: -1, neutral: 0, buy: 1, strongBuy: 2 };

interface Prefs { market: Market | 'all'; sort: { key: SortKey; desc: boolean }; cols: Col[]; dir: 'all' | 'up' | 'down'; minMove: number }
const DEFAULTS = (compact: boolean): Prefs => ({ market: 'all', sort: { key: 'volume', desc: true }, cols: compact ? ['last', 'changePct'] : ['last', 'change', 'changePct', 'volume', 'range'], dir: 'all', minMove: 0 });
function readPrefs(compact: boolean): Prefs {
  try { const v = JSON.parse(localStorage.getItem(`pc.scr.${compact ? 'panel' : 'dock'}.v1`) ?? 'null') as Partial<Prefs> | null; if (v) return { ...DEFAULTS(compact), ...v, cols: Array.isArray(v.cols) ? v.cols.filter((c) => COLS.includes(c)) : DEFAULTS(compact).cols }; } catch { /* defaults */ }
  return DEFAULTS(compact);
}

// The technical rating (1-hour bars) of the rows on screen, fetched a few at a time and kept 5 min.
const ratings = new Map<string, { at: number; key: RatingKey | null }>();
const waiting = new Set<string>();
function useRatings(symbols: string[], on: boolean): Record<string, RatingKey | null> {
  const [, bump] = useState(0);
  useEffect(() => {
    if (!on) return;
    let alive = true;
    const todo = symbols.slice(0, 40).filter((s) => !waiting.has(s) && !(ratings.get(s) && Date.now() - ratings.get(s)!.at < 300_000));
    (async () => {
      for (const s of todo) {
        if (!alive) return;
        waiting.add(s);
        const bars = await barsFor(s, '60', 300_000).catch(() => []);
        const r = technicalRating(bars);
        ratings.set(s, { at: Date.now(), key: r ? r.label : null });
        waiting.delete(s);
        if (alive) bump((n) => n + 1);
      }
    })();
    return () => { alive = false; };
  }, [symbols, on]);
  const out: Record<string, RatingKey | null> = {};
  for (const s of symbols) { const r = ratings.get(s); if (r) out[s] = r.key; }
  return out;
}

const turnover = (tk: Ticker) => (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);
const rangeOf = (tk: Ticker) => (tk.high && tk.low ? ((tk.high - tk.low) / tk.low) * 100 : null);

// TradingView's screener, on the live tickers both platforms already publish: every market, sorted
// by any column, columns of the reader's choice (with the technical rating), gainers / losers and
// a minimum move, CSV, a star to watch a symbol (or every row shown); a click opens the chart.
// Volume in quote currency where the feed gives it. The choices are kept.
export function Screener({ compact = false }: { compact?: boolean }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const current = usePrefs((s) => s.symbol);
  const wl = useWatchlist();
  const [p, setP] = useState<Prefs>(() => readPrefs(compact));
  const [q, setQ] = useState('');
  const [menu, setMenu] = useState(false);
  const colsRef = useRef<HTMLButtonElement>(null);
  useEffect(() => { try { localStorage.setItem(`pc.scr.${compact ? 'panel' : 'dock'}.v1`, JSON.stringify(p)); } catch { /* ignore */ } }, [p, compact]);
  const set = (patch: Partial<Prefs>) => setP((x) => ({ ...x, ...patch }));

  const base = useMemo(() => {
    const needle = q.trim().toUpperCase();
    return Object.values(tickers).filter((tk) => hasLogo(tk.symbol) && (p.market === 'all' || marketOf(tk.symbol) === p.market) && (!needle || tk.symbol.includes(needle))
      && (p.dir === 'all' || (p.dir === 'up' ? tk.changePct > 0 : tk.changePct < 0)) && Math.abs(tk.changePct) >= p.minMove);
  }, [tickers, p.market, p.dir, p.minMove, q]);
  const ratingOn = p.cols.includes('rating');
  const visibleSyms = useMemo(() => [...base].sort((a, b) => turnover(b) - turnover(a)).map((x) => x.symbol), [base]);
  const rating = useRatings(visibleSyms, ratingOn);
  const rows = useMemo(() => {
    const val = (tk: Ticker): number | string => {
      switch (p.sort.key) {
        case 'symbol': return tk.symbol;
        case 'last': return tk.last;
        case 'change': return tk.change;
        case 'changePct': return tk.changePct;
        case 'volume': return turnover(tk);
        case 'high': return tk.high ?? 0;
        case 'low': return tk.low ?? 0;
        case 'range': return rangeOf(tk) ?? 0;
        case 'rating': { const r = rating[tk.symbol]; return r ? RATING_ORDER[r] : -9; }
      }
    };
    const list = [...base];
    list.sort((a, b) => { const x = val(a), y = val(b); const c = typeof x === 'string' ? x.localeCompare(y as string) : (x as number) - (y as number); return p.sort.desc ? -c : c; });
    return list.slice(0, compact ? 150 : 400);
  }, [base, p.sort, compact, rating]);

  const head = (key: SortKey, label: string) => (
    <button key={key} className={`scr-h${p.sort.key === key ? ' on' : ''}`} onClick={() => set({ sort: { key, desc: p.sort.key === key ? !p.sort.desc : key !== 'symbol' } })}>
      {label}{p.sort.key === key ? (p.sort.desc ? ' ▼' : ' ▲') : ''}
    </button>
  );
  const colName = (c: Col) => (c === 'last' ? L('قیمت', 'Price') : c === 'change' ? L('تغییر', 'Chg') : c === 'changePct' ? L('تغییر٪', 'Chg %') : c === 'volume' ? L('حجم', 'Volume') : c === 'high' ? L('سقف', 'High') : c === 'low' ? L('کف', 'Low') : c === 'range' ? L('دامنه٪', 'Range %') : L('امتیاز فنی', 'Rating'));
  const grid = { gridTemplateColumns: `20px minmax(${compact ? 96 : 140}px, 1.6fr) ${p.cols.map((c) => (c === 'rating' ? 'minmax(70px, 1fr)' : 'minmax(0, 1fr)')).join(' ')}` };
  const exportCsv = () => downloadCsv('screener.csv', [
    ['Symbol', 'Last', 'Chg', 'Chg %', 'Volume', 'High', 'Low', 'Range %', 'Rating'],
    ...rows.map((x) => { const d = instrumentOf(x.symbol, x.last).digits; return [x.symbol, x.last.toFixed(d), x.change.toFixed(d), x.changePct.toFixed(2), Math.round(turnover(x)), x.high?.toFixed(d) ?? '', x.low?.toFixed(d) ?? '', rangeOf(x)?.toFixed(2) ?? '', rating[x.symbol] ? RATING_EN[rating[x.symbol]!] : '']; }),
  ]);
  const missing = rows.filter((x) => !wl.symbols.includes(x.symbol));
  const count = (n: number) => (fa ? faNum(n) : String(n));

  return (
    <section className={`screener${compact ? ' compact' : ''}`}>
      <div className="scr-bar">
        <nav className="chips">
          {MARKETS.map((m) => (
            <button key={m.id} className={`chip${p.market === m.id ? ' on' : ''}`} onClick={() => set({ market: m.id })}><span className="fa">{t(m.label)}</span></button>
          ))}
        </nav>
        <input className="scr-search num" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} spellCheck={false} />
      </div>
      <div className="scr-bar scr-filters">
        <span className="seg sm" role="group">
          {(['all', 'up', 'down'] as const).map((d) => <button key={d} className={p.dir === d ? 'on' : ''} onClick={() => set({ dir: d })}><span className="fa">{d === 'all' ? L('همه', 'All') : d === 'up' ? L('رشد', 'Gainers') : L('افت', 'Losers')}</span></button>)}
        </span>
        <select className="num" value={p.minMove} onChange={(e) => set({ minMove: Number(e.target.value) })} aria-label={L('کمترین تغییر', 'Minimum move')} title={L('کمترین تغییر روزانه', 'Minimum daily move')}>
          {[0, 0.5, 1, 2, 5, 10].map((m) => <option key={m} value={m}>{m ? `|Δ| ≥ ${m}%` : L('هر حرکتی', 'Any move')}</option>)}
        </select>
        <span className="ws-spacer" />
        <button className="lt" title={L('افزودن همه‌ی ردیف‌های نمایش‌داده به دیده‌بان', 'Add every row shown to the watchlist')} disabled={!missing.length} onClick={() => missing.slice(0, 100).forEach((x) => wl.add(x.symbol))}><Icon name="ph-list-plus" size="sm" /></button>
        <button className="lt" title={L('خروجی CSV', 'Export CSV')} onClick={exportCsv}><Icon name="ph-download-simple" size="sm" /></button>
        <button ref={colsRef} className="lt" title={L('ستون‌ها', 'Columns')} onClick={() => setMenu((m) => !m)}><Icon name="ph-columns" size="sm" /></button>
      </div>
      {menu ? (
        <Dropdown anchor={colsRef.current} onClose={() => setMenu(false)} align="right" className="wl-more"><div dir={fa ? 'rtl' : 'ltr'}>
          <div className="menu-title fa">{L('ستون‌ها', 'Columns')}</div>
          {COLS.map((c) => <label key={c} className="menu-item check"><input type="checkbox" checked={p.cols.includes(c)} onChange={() => set({ cols: p.cols.includes(c) ? p.cols.filter((x) => x !== c) : COLS.filter((x) => x === c || p.cols.includes(x)) })} /><span>{colName(c)}</span></label>)}
        </div></Dropdown>
      ) : null}
      <div className="scr-table" role="table">
        <div className="scr-row scr-head" role="row" style={grid}>
          <span />
          {head('symbol', L('نماد', 'Symbol'))}
          {p.cols.map((c) => head(c, colName(c)))}
        </div>
        {rows.map((tk) => <ScrRow key={tk.symbol} tk={tk} cols={p.cols} grid={grid} on={tk.symbol === current} watched={wl.symbols.includes(tk.symbol)} rating={rating[tk.symbol]} fa={fa}
          onOpen={() => setSymbol(tk.symbol)} onStar={() => (wl.symbols.includes(tk.symbol) ? wl.remove(tk.symbol) : wl.add(tk.symbol))} />)}
      </div>
      <footer className="scr-foot fa">
        <span>{L(`${count(rows.length)} نماد`, `${rows.length} symbols`)}{base.length > rows.length ? <span className="muted"> {L(`از ${count(base.length)}`, `of ${base.length}`)}</span> : null}</span>
        {ratingOn ? <span className="muted">{L('امتیاز فنی روی کندل یک‌ساعته', 'Rating on 1-hour bars')}</span> : null}
      </footer>
    </section>
  );
}

function ScrRow({ tk, cols, grid, on, watched, rating, fa, onOpen, onStar }: { tk: Ticker; cols: Col[]; grid: React.CSSProperties; on: boolean; watched: boolean; rating: RatingKey | null | undefined; fa: boolean; onOpen(): void; onStar(): void }) {
  const digits = instrumentOf(tk.symbol, tk.last).digits;
  const { ref, tick } = useFlash<HTMLSpanElement>(tk.last);
  const tone = (v: number) => (v > 0 ? 'up' : v < 0 ? 'down' : '');
  const sign = (v: number) => (v > 0 ? '+' : v < 0 ? '−' : '');
  const vol = turnover(tk);
  const range = rangeOf(tk);
  const cell = (c: Col) => {
    switch (c) {
      case 'last': return <span key={c} ref={ref} className={`num scr-last ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{tick ? <i className="scr-tick">{tick > 0 ? '▲' : '▼'}</i> : null}{formatPrice(tk.last, digits)}</span>;
      case 'change': return <span key={c} className={`num scr-abs ${tone(tk.change)}`}><b>{sign(tk.change)}{formatPrice(Math.abs(tk.change), digits)}</b></span>;
      case 'changePct': return <span key={c} className={`num ${tone(tk.changePct)}`}>{sign(tk.changePct)}{Math.abs(tk.changePct).toFixed(2)}%</span>;
      case 'volume': return <span key={c} className="num">{vol ? formatVolume(vol) : '—'}</span>;
      case 'high': return <span key={c} className="num">{tk.high ? formatPrice(tk.high, digits) : '—'}</span>;
      case 'low': return <span key={c} className="num">{tk.low ? formatPrice(tk.low, digits) : '—'}</span>;
      case 'range': return <span key={c} className="num">{range != null ? `${range.toFixed(2)}%` : '—'}</span>;
      case 'rating': return <span key={c} className={`scr-rating${fa ? ' fa' : ''}`} style={rating ? { color: RATING_FA[rating].color } : undefined}>{rating ? (fa ? RATING_FA[rating].label : RATING_EN[rating]) : rating === null ? '—' : '…'}</span>;
    }
  };
  return (
    <div role="row" tabIndex={0} className={`scr-row${on ? ' on' : ''}`} style={grid} onClick={onOpen} onKeyDown={(e) => { if (e.key === 'Enter') onOpen(); }}>
      <button className={`scr-star${watched ? ' on' : ''}`} aria-pressed={watched} title={watched ? (fa ? 'برداشتن از دیده‌بان' : 'Remove from watchlist') : (fa ? 'افزودن به دیده‌بان' : 'Add to watchlist')} onClick={(e) => { e.stopPropagation(); onStar(); }}>
        <Icon name={watched ? 'ph-star-fill' : 'ph-star'} size="sm" />
      </button>
      <span className="scr-sym"><SymbolLogo symbol={tk.symbol} size={18} /><span className="sym">{tk.symbol}</span></span>
      {cols.map(cell)}
    </div>
  );
}
