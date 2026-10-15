import { useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import type { Market, Ticker } from '../data/types';
import type { StringKey } from '../i18n/strings';

type SortKey = 'symbol' | 'last' | 'changePct' | 'volume' | 'range';
const MARKETS: { id: Market | 'all'; label: StringKey }[] = [
  { id: 'all', label: 'all' }, { id: 'crypto', label: 'crypto' }, { id: 'forex', label: 'forex' },
  { id: 'metals', label: 'metals' }, { id: 'indices', label: 'indices' }, { id: 'energy', label: 'energy' },
];

// TradingView's screener, on the live tickers both platforms already publish: every market, sorted
// by any column, a click opens the chart. Volume in quote currency where the feed gives it.
export function Screener({ compact = false }: { compact?: boolean }) {
  const t = useT();
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const current = usePrefs((s) => s.symbol);
  const [market, setMarket] = useState<Market | 'all'>('all');
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'volume', desc: true });
  const [q, setQ] = useState('');

  const rows = useMemo(() => {
    const needle = q.trim().toUpperCase();
    const list = Object.values(tickers).filter((tk) => hasLogo(tk.symbol) && (market === 'all' || marketOf(tk.symbol) === market) && (!needle || tk.symbol.includes(needle)));
    const val = (tk: Ticker): number | string => {
      switch (sort.key) {
        case 'symbol': return tk.symbol;
        case 'last': return tk.last;
        case 'changePct': return tk.changePct;
        case 'volume': return (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);
        case 'range': return tk.high && tk.low ? (tk.high - tk.low) / tk.low : 0;
      }
    };
    list.sort((a, b) => {
      const x = val(a), y = val(b);
      const c = typeof x === 'string' ? x.localeCompare(y as string) : (x as number) - (y as number);
      return sort.desc ? -c : c;
    });
    return list.slice(0, compact ? 150 : 400);
  }, [tickers, market, sort, q, compact]);

  const head = (key: SortKey, label: string) => (
    <button className={`scr-h${sort.key === key ? ' on' : ''}`} onClick={() => setSort({ key, desc: sort.key === key ? !sort.desc : key !== 'symbol' })}>
      {label}{sort.key === key ? (sort.desc ? ' ▼' : ' ▲') : ''}
    </button>
  );

  return (
    <section className={`screener${compact ? ' compact' : ''}`}>
      <div className="scr-bar">
        <nav className="chips">
          {MARKETS.map((m) => (
            <button key={m.id} className={`chip${market === m.id ? ' on' : ''}`} onClick={() => setMarket(m.id)}><span className="fa">{t(m.label)}</span></button>
          ))}
        </nav>
        <input className="scr-search num" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} spellCheck={false} />
      </div>
      <div className="scr-table" role="table">
        <div className="scr-row scr-head" role="row">
          {head('symbol', 'Symbol')}{head('last', 'Price')}{head('changePct', 'Chg %')}
          {!compact ? head('volume', 'Volume') : null}{!compact ? head('range', 'Day range %') : null}
        </div>
        {rows.map((tk) => {
          const digits = instrumentOf(tk.symbol, tk.last).digits;
          const vol = (tk.volume ?? 0) * (marketOf(tk.symbol) === 'crypto' ? tk.last : 1);
          return (
            <button key={tk.symbol} role="row" className={`scr-row${tk.symbol === current ? ' on' : ''}`} onClick={() => setSymbol(tk.symbol)}>
              <span className="scr-sym"><SymbolLogo symbol={tk.symbol} size={18} /><span className="sym">{tk.symbol}</span></span>
              <span className="num">{formatPrice(tk.last, digits)}</span>
              <span className={`num ${tk.changePct >= 0 ? 'up' : 'down'}`}>{tk.changePct >= 0 ? '+' : '−'}{Math.abs(tk.changePct).toFixed(2)}%</span>
              {!compact ? <span className="num">{vol ? formatVolume(vol) : '—'}</span> : null}
              {!compact ? <span className="num">{tk.high && tk.low ? `${(((tk.high - tk.low) / tk.low) * 100).toFixed(2)}%` : '—'}</span> : null}
            </button>
          );
        })}
      </div>
    </section>
  );
}
