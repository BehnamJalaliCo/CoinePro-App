import { useEffect, useMemo, useRef, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { searchText } from '../data/names';
import type { Market } from '../data/types';
import type { StringKey } from '../i18n/strings';

// TradingView's symbol search: a centred dialog, the query field on top, market tabs under it,
// one 44px row per instrument (logo, ticker, name, market). Arrow keys move, Enter opens.
const TABS: { id: Market | 'all'; label: StringKey }[] = [
  { id: 'all', label: 'all' }, { id: 'crypto', label: 'crypto' }, { id: 'forex', label: 'forex' },
  { id: 'metals', label: 'metals' }, { id: 'indices', label: 'indices' }, { id: 'energy', label: 'energy' },
];
const MAX_ROWS = 80;

export function SymbolSearch({ onClose, onPick, initial = '' }: { onClose(): void; onPick?(symbol: string): void; initial?: string }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const instruments = useMarket((s) => s.instruments);
  const [q, setQ] = useState(initial);
  const [tab, setTab] = useState<Market | 'all'>('all');
  const [cursor, setCursor] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);

  useEffect(() => { input.current?.focus(); }, []);

  const index = useMemo(() => Object.values(instruments)
    .filter((i) => hasLogo(i.symbol))
    .map((i) => ({ i, text: searchText(i.symbol, i.market, i.base, i.quote) })), [instruments]);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase().replace(/[\s/]/g, '');
    const hits = index.filter(({ i, text }) => (tab === 'all' || i.market === tab) && (!needle || text.replace(/[\s/]/g, '').includes(needle) || i.symbol.toLowerCase().includes(needle)));
    const rank = (s: string) => {
      const l = s.toLowerCase();
      if (!needle) return 0;
      if (l === needle || l === `${needle}usdt`) return 0;
      if (l.startsWith(needle)) return 1;
      return 2;
    };
    const order: Record<Market, number> = { forex: 1, metals: 0, crypto: 2, indices: 3, energy: 4 };
    hits.sort((a, b) => rank(a.i.symbol) - rank(b.i.symbol) || order[a.i.market] - order[b.i.market] || a.i.symbol.length - b.i.symbol.length || a.i.symbol.localeCompare(b.i.symbol));
    return hits.slice(0, MAX_ROWS).map((h) => h.i);
  }, [index, q, tab]);

  useEffect(() => { setCursor(0); }, [q, tab]);
  useEffect(() => { list.current?.querySelector('.on')?.scrollIntoView({ block: 'nearest' }); }, [cursor]);

  const choose = (s: string) => { if (onPick) onPick(s); else setSymbol(s); onClose(); };

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
    else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(rows.length - 1, c + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
    else if (e.key === 'Enter' && rows[cursor]) choose(rows[cursor]!.symbol);
  };

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog search" role="dialog" aria-modal="true" aria-label={t('symbolSearch')} onKeyDown={onKey}>
        <header className="dialog-head">
          <h2 className="fa">{t('symbolSearch')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <label className="search-field">
          <Icon name="search" size="md" />
          <input ref={input} value={q} onChange={(e) => setQ(e.target.value)}
            placeholder={t('symbolSearch')} spellCheck={false} autoComplete="off" dir="auto" />
          {q ? <button className="ib" onClick={() => setQ('')} aria-label="clear"><Icon name="ph-x" size="sm" /></button> : null}
        </label>
        <nav className="chips" role="tablist">
          {TABS.map((x) => (
            <button key={x.id} role="tab" aria-selected={tab === x.id} className={`chip${tab === x.id ? ' on' : ''}`} onClick={() => setTab(x.id)}>
              <span className="fa">{t(x.label)}</span>
            </button>
          ))}
        </nav>
        <div className="search-list" ref={list} role="listbox">
          {rows.length === 0 ? <div className="empty fa">{t('noResults')}</div> : rows.map((i, n) => {
            const name = lang === 'fa' ? i.nameFa : i.nameEn;
            return (
              <button key={i.symbol} role="option" aria-selected={n === cursor} className={`search-row${n === cursor ? ' on' : ''}`}
                onMouseEnter={() => setCursor(n)} onClick={() => choose(i.symbol)}>
                <SymbolLogo symbol={i.symbol} size={24} />
                <span className="sym">{i.symbol}</span>
                <span className={`name${lang === 'fa' ? ' fa' : ''}`}>{name ?? ''}</span>
                <span className="kind fa">{t(i.market)}</span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
