import { useMemo } from 'react';
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
export function Watchlist() {
  const t = useT();
  const tickers = useMarket((s) => s.tickers);
  const instruments = useMarket((s) => s.instruments);
  const symbols = useWatchlist((s) => s.symbols);
  const current = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);

  const sections = useMemo(() => {
    const by = new Map<Market, string[]>();
    for (const s of symbols) {
      if (!hasLogo(s) && Object.keys(instruments).length) continue;
      const m = instruments[s]?.market ?? (s.endsWith('USDT') ? 'crypto' : /^X(AU|AG|PT|PD)/.test(s) ? 'metals' : 'forex');
      by.set(m, [...(by.get(m) ?? []), s]);
    }
    return (['crypto', 'forex', 'metals', 'indices', 'energy'] as Market[]).filter((m) => by.has(m)).map((m) => ({ m, list: by.get(m)! }));
  }, [symbols, instruments]);

  return (
    <section className="wl" aria-label={t('watchlist')}>
      <header className="wl-head">
        <button className="ib wl-title"><span className="fa">{t('watchlist')}</span><Icon name="ph-caret-down" size="sm" /></button>
        <span className="ws-spacer" />
        <button className="ib" title={t('compare')}><Icon name="ph-plus" size="md" /></button>
        <button className="ib"><Icon name="ph-dots-three" size="md" /></button>
      </header>
      <div className="wl-cols" role="row">
        <span className="c-sym fa">{t('symbol')}</span>
        <span className="c-num fa">{t('last')}</span>
        <span className="c-num fa">{t('change')}</span>
        <span className="c-num fa">{t('changePct')}</span>
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
                  <span className="c-sym"><SymbolLogo symbol={s} size={18} /><span className="sym">{s}</span></span>
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
