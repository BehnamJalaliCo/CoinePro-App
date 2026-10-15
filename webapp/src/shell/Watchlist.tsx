import { useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { useWatchlist } from '../state/watchlist';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, guessDigits } from '../chart/format';
import { Icon } from '../ui/Icon';
import type { Market } from '../data/types';
import type { StringKey } from '../i18n/strings';

const SECTION_LABEL: Record<Market, StringKey> = { crypto: 'crypto', forex: 'forex', metals: 'metals', indices: 'indices', energy: 'energy' };

// TradingView's watchlist: Symbol · Last · Chg · Chg%, 30px rows, sections by market, the open
// symbol outlined. Left to right in both languages, like the chart beside it.
type Sort = { key: 'symbol' | 'last' | 'change' | 'changePct'; desc: boolean } | null;

export function Watchlist({ onAdd }: { onAdd(): void }) {
  const t = useT();
  const tickers = useMarket((s) => s.tickers);
  const instruments = useMarket((s) => s.instruments);
  const symbols = useWatchlist((s) => s.symbols);
  const current = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const remove = useWatchlist((s) => s.remove);
  const [sort, setSort] = useState<Sort>(null);

  const sections = useMemo(() => {
    const by = new Map<Market, string[]>();
    for (const s of symbols) {
      if (!hasLogo(s) && Object.keys(instruments).length) continue;
      const m = instruments[s]?.market ?? (s.endsWith('USDT') ? 'crypto' : /^X(AU|AG|PT|PD)/.test(s) ? 'metals' : 'forex');
      by.set(m, [...(by.get(m) ?? []), s]);
    }
    const ordered = (list: string[]) => {
      if (!sort) return list;
      const v = (s: string): number | string => (sort.key === 'symbol' ? s : tickers[s]?.[sort.key] ?? -Infinity);
      return [...list].sort((a, b) => { const x = v(a), y = v(b); const c = typeof x === 'string' ? x.localeCompare(y as string) : (x as number) - (y as number); return sort.desc ? -c : c; });
    };
    return (['crypto', 'forex', 'metals', 'indices', 'energy'] as Market[]).filter((m) => by.has(m)).map((m) => ({ m, list: ordered(by.get(m)!) }));
  }, [symbols, instruments, sort, tickers]);

  // A column title sorts by it: descending, ascending, then the list's own order, as on TradingView.
  const sortBy = (key: NonNullable<Sort>['key']) => setSort((s) => (s?.key !== key ? { key, desc: key !== 'symbol' } : s.desc === (key !== 'symbol') ? { key, desc: !s.desc } : null));
  const mark = (key: NonNullable<Sort>['key']) => (sort?.key === key ? (sort.desc ? ' ▼' : ' ▲') : '');

  return (
    <section className="wl" aria-label={t('watchlist')}>
      <header className="wl-head">
        <button className="ib wl-title"><span className="fa">{t('watchlist')}</span><Icon name="ph-caret-down" size="sm" /></button>
        <span className="ws-spacer" />
        <button className="ib" title={t('compare')} onClick={onAdd}><Icon name="ph-plus" size="md" /></button>
        <button className="ib"><Icon name="ph-dots-three" size="md" /></button>
      </header>
      <div className="wl-cols" role="row">
        <button className="c-sym fa" onClick={() => sortBy('symbol')}>{t('symbol')}{mark('symbol')}</button>
        <button className="c-num fa" onClick={() => sortBy('last')}>{t('last')}{mark('last')}</button>
        <button className="c-num fa" onClick={() => sortBy('change')}>{t('change')}{mark('change')}</button>
        <button className="c-num fa" onClick={() => sortBy('changePct')}>{t('changePct')}{mark('changePct')}</button>
      </div>
      <div className="wl-body" role="list">
        {sections.map(({ m, list }) => (
          <div key={m} className="wl-section">
            <div className="wl-section-title"><Icon name="ph-caret-down" size="sm" /><span className="fa">{t(SECTION_LABEL[m])}</span></div>
            {list.map((s) => {
              const tk = tickers[s];
              const digits = instruments[s]?.digits ?? guessDigits(s, tk?.last);
              const tone = tk ? (tk.change >= 0 ? 'up' : 'down') : '';
              return (
                <button key={s} role="listitem" className={`wl-row${s === current ? ' on' : ''}`} onClick={() => setSymbol(s)}>
                  <span className="c-sym"><SymbolLogo symbol={s} size={18} /><span className="sym">{s}</span>
                    <span className="wl-x" role="button" aria-label={t('remove')} onClick={(e) => { e.stopPropagation(); remove(s); }}><Icon name="ph-x" size="sm" /></span>
                  </span>
                  <span className="c-num num">{tk ? formatPrice(tk.last, digits) : ''}</span>
                  <span className={`c-num num ${tone}`}>{tk ? `${tk.change >= 0 ? '' : '−'}${formatPrice(Math.abs(tk.change), digits)}` : ''}</span>
                  <span className={`c-num num ${tone}`}>{tk ? `${tk.changePct >= 0 ? '' : '−'}${Math.abs(tk.changePct).toFixed(2)}%` : ''}</span>
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </section>
  );
}
