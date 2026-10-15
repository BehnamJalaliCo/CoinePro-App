import { useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { useWatchlist } from '../state/watchlist';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash } from '../ui/useFlash';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import type { Market, Ticker } from '../data/types';
import './panels-x.css';

// TradingView's Hotlists: the day's top gainers, top losers, most active (by turnover) and most
// volatile (by the day's range), per market, from the live tickers. A click opens the chart; the
// star watches the symbol.
type List = 'gainers' | 'losers' | 'active' | 'volatile';
const LISTS: { id: List; fa: string; en: string }[] = [
  { id: 'gainers', fa: 'بیشترین رشد', en: 'Top gainers' }, { id: 'losers', fa: 'بیشترین افت', en: 'Top losers' },
  { id: 'active', fa: 'پرمعامله‌ترین', en: 'Most active' }, { id: 'volatile', fa: 'پرنوسان‌ترین', en: 'Most volatile' },
];
const turnover = (tk: Ticker) => (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);
const range = (tk: Ticker) => (tk.high && tk.low ? ((tk.high - tk.low) / tk.low) * 100 : 0);
const KEY = 'pc.hotlists.v1';

export function hotlist(tickers: Ticker[], list: List, n = 25): Ticker[] {
  const by = { gainers: (t: Ticker) => t.changePct, losers: (t: Ticker) => -t.changePct, active: turnover, volatile: range }[list];
  return [...tickers].filter((t) => (list === 'gainers' ? t.changePct > 0 : list === 'losers' ? t.changePct < 0 : true)).sort((a, b) => by(b) - by(a)).slice(0, n);
}

export function Hotlists() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const current = usePrefs((s) => s.symbol);
  const wl = useWatchlist();
  const saved = (() => { try { return JSON.parse(localStorage.getItem(KEY) ?? '{}') as { list?: List; market?: Market | 'all' }; } catch { return {}; } })();
  const [list, setList] = useState<List>(saved.list ?? 'gainers');
  const [market, setMarket] = useState<Market | 'all'>(saved.market ?? 'crypto');
  const remember = (patch: { list?: List; market?: Market | 'all' }) => { try { localStorage.setItem(KEY, JSON.stringify({ list, market, ...patch })); } catch { /* ignore */ } };
  const rows = useMemo(() => hotlist(Object.values(tickers).filter((tk) => hasLogo(tk.symbol) && (market === 'all' || marketOf(tk.symbol) === market || (market === 'forex' && marketOf(tk.symbol) !== 'crypto'))), list), [tickers, list, market]);
  const figure = (tk: Ticker) => (list === 'active' ? formatVolume(turnover(tk)) : list === 'volatile' ? `${range(tk).toFixed(2)}%` : null);

  return (
    <section className="panel-generic hotlists">
      <header className="panel-head"><span className="fa">{L('پرتحرک‌ها', 'Hotlists')}</span></header>
      <div className="hot-bar">
        <select value={list} onChange={(e) => { setList(e.target.value as List); remember({ list: e.target.value as List }); }} aria-label={L('فهرست', 'List')}>
          {LISTS.map((x) => <option key={x.id} value={x.id}>{fa ? x.fa : x.en}</option>)}
        </select>
        <nav className="seg sm">
          {(['crypto', 'forex', 'all'] as const).map((m) => <button key={m} className={market === m ? 'on' : ''} onClick={() => { setMarket(m); remember({ market: m }); }}><span className="fa">{m === 'forex' ? L('فارکس و کالا', 'FX & CFDs') : t(m)}</span></button>)}
        </nav>
      </div>
      <div className="hot-cols"><span>#</span><span className="fa">{L('نماد', 'Symbol')}</span><span className="fa r">{t('last')}</span><span className="fa r">{list === 'active' ? L('ارزش معاملات', 'Turnover') : list === 'volatile' ? L('دامنه', 'Range') : t('changePct')}</span><span /></div>
      <div className="panel-body">
        {!rows.length ? <div className="empty fa">{t('noResults')}</div> : null}
        {rows.map((tk, i) => <HotRow key={tk.symbol} tk={tk} rank={i + 1} fa={fa} on={tk.symbol === current} figure={figure(tk)} watched={wl.symbols.includes(tk.symbol)}
          onOpen={() => setSymbol(tk.symbol)} onStar={() => (wl.symbols.includes(tk.symbol) ? wl.remove(tk.symbol) : wl.add(tk.symbol))} />)}
      </div>
    </section>
  );
}

function HotRow({ tk, rank, fa, on, figure, watched, onOpen, onStar }: { tk: Ticker; rank: number; fa: boolean; on: boolean; figure: string | null; watched: boolean; onOpen(): void; onStar(): void }) {
  const { ref, tick } = useFlash<HTMLSpanElement>(tk.last);
  const d = instrumentOf(tk.symbol, tk.last).digits;
  const tone = tk.changePct > 0 ? 'up' : tk.changePct < 0 ? 'down' : '';
  return (
    <div className={`hot-row${on ? ' on' : ''}`} role="button" tabIndex={0} onClick={onOpen} onKeyDown={(e) => { if (e.key === 'Enter') onOpen(); }}>
      <span className="num muted">{fa ? faNum(rank) : rank}</span>
      <span className="hot-sym"><SymbolLogo symbol={tk.symbol} size={18} /><span className="sym">{tk.symbol}</span></span>
      <span ref={ref} className={`num r ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{formatPrice(tk.last, d)}</span>
      <span className={`num r ${figure ? '' : tone}`}>{figure ?? `${tk.changePct > 0 ? '+' : tk.changePct < 0 ? '−' : ''}${Math.abs(tk.changePct).toFixed(2)}%`}</span>
      <button className={`scr-star${watched ? ' on' : ''}`} aria-pressed={watched} onClick={(e) => { e.stopPropagation(); onStar(); }}><Icon name={watched ? 'ph-star-fill' : 'ph-star'} size="sm" /></button>
    </div>
  );
}
