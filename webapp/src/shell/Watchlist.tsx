import { useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Dropdown } from '../ui/Menu';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { FLAG_COLORS, parseSymbols, useWatchlist } from '../state/watchlist';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, guessDigits } from '../chart/format';
import { Icon } from '../ui/Icon';
import type { Market } from '../data/types';
import type { StringKey } from '../i18n/strings';

const SECTION_LABEL: Record<Market, StringKey> = { crypto: 'crypto', forex: 'forex', metals: 'metals', indices: 'indices', energy: 'energy' };

// TradingView's watchlist: Symbol · Last · Chg · Chg%, 30px rows, sections by market, the open
// symbol outlined; several lists under the title, seven colour flags (each a list of its own), a
// note per symbol, import and export as text. Left to right in both languages, like the chart.
type Sort = { key: 'symbol' | 'last' | 'change' | 'changePct'; desc: boolean } | null;

export function Watchlist({ onAdd }: { onAdd(): void }) {
  const t = useT();
  const tickers = useMarket((s) => s.tickers);
  const instruments = useMarket((s) => s.instruments);
  const wl = useWatchlist();
  const [flagView, setFlagView] = useState<number | null>(null);
  const symbols = flagView == null ? wl.symbols : Object.entries(wl.flags).filter(([, c]) => c === flagView).map(([s]) => s);
  const [menu, setMenu] = useState<'lists' | 'more' | null>(null);
  const [rowMenu, setRowMenu] = useState<{ symbol: string; x: number; y: number } | null>(null);
  const titleRef = useRef<HTMLButtonElement>(null);
  const moreRef = useRef<HTMLButtonElement>(null);
  const activeName = flagView != null ? `Flagged` : wl.lists.find((l) => l.id === wl.active)?.name ?? t('watchlist');
  const doImport = () => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = '.txt,.csv,text/plain';
    input.onchange = () => { const f = input.files?.[0]; if (f) void f.text().then((txt) => { const list = parseSymbols(txt); if (list.length) wl.createList(f.name.replace(/\.[^.]+$/, ''), list); }); };
    input.click();
  };
  const doExport = () => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([wl.symbols.join(',')], { type: 'text/plain' }));
    a.download = `${activeName}.txt`; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  };
  const current = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const remove = wl.remove;
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
        <button ref={titleRef} className="ib wl-title" aria-haspopup="menu" aria-expanded={menu === 'lists'} onClick={() => setMenu(menu === 'lists' ? null : 'lists')}>
          {flagView != null ? <i className="wl-flag" style={{ background: FLAG_COLORS[flagView] }} /> : null}<span className="fa">{activeName}</span><Icon name="ph-caret-down" size="sm" />
        </button>
        <span className="ws-spacer" />
        <button className="ib" title={t('compare')} onClick={onAdd}><Icon name="ph-plus" size="md" /></button>
        <button ref={moreRef} className="ib" aria-haspopup="menu" onClick={() => setMenu(menu === 'more' ? null : 'more')}><Icon name="ph-dots-three" size="md" /></button>
      </header>
      {menu === 'lists' ? (
        <Dropdown anchor={titleRef.current} onClose={() => setMenu(null)} className="wl-lists">
          {wl.lists.map((l) => (
            <button key={l.id} className={`menu-item${flagView == null && l.id === wl.active ? ' on' : ''}`} onClick={() => { wl.setActive(l.id); setFlagView(null); setMenu(null); }}>
              <span>{l.name}</span><span className="num muted">{l.symbols.length}</span>
            </button>
          ))}
          <div className="menu-sep" />
          {FLAG_COLORS.map((c, i) => {
            const n = Object.values(wl.flags).filter((x) => x === i).length;
            return n ? <button key={c} className={`menu-item${flagView === i ? ' on' : ''}`} onClick={() => { setFlagView(i); setMenu(null); }}><i className="wl-flag" style={{ background: c }} /><span>Flagged</span><span className="num muted">{n}</span></button> : null;
          })}
          <div className="menu-sep" />
          <button className="menu-item" onClick={() => { const n = window.prompt('New list name', 'Watchlist'); if (n) { wl.createList(n); setFlagView(null); } setMenu(null); }}><Icon name="ph-plus" size="sm" /><span>Create new list…</span></button>
        </Dropdown>
      ) : null}
      {menu === 'more' ? (
        <Dropdown anchor={moreRef.current} onClose={() => setMenu(null)} align="right">
          <button className="menu-item" onClick={() => { setMenu(null); const n = window.prompt('Rename list', activeName); if (n) wl.renameList(wl.active, n); }}><span>Rename list…</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); wl.createList(`${activeName} copy`, [...wl.symbols]); }}><span>Make a copy</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); doImport(); }}><Icon name="ph-upload-simple" size="sm" /><span>Import list…</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); doExport(); }}><Icon name="ph-download-simple" size="sm" /><span>Export list</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); const txt = window.prompt('Symbols (comma or space separated)'); if (txt) parseSymbols(txt).forEach((x) => wl.add(x)); }}><span>Add symbols…</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); if (wl.symbols.length && window.confirm('Clear this list?')) wl.set([]); }}><span>Clear list</span></button>
          {wl.lists.length > 1 ? <button className="menu-item" onClick={() => { setMenu(null); if (window.confirm(`Delete "${activeName}"?`)) wl.deleteList(wl.active); }}><Icon name="ph-trash" size="sm" /><span>Delete list</span></button> : null}
        </Dropdown>
      ) : null}
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
                <button key={s} role="listitem" className={`wl-row${s === current ? ' on' : ''}`} onClick={() => setSymbol(s)} title={wl.notes[s] ?? undefined}
                  onContextMenu={(e) => { e.preventDefault(); setRowMenu({ symbol: s, x: e.clientX, y: e.clientY }); }}>
                  <span className="c-sym">
                    <span className="wl-flag-btn" role="button" aria-label="flag" style={wl.flags[s] != null ? { background: FLAG_COLORS[wl.flags[s]!] } : undefined}
                      onClick={(e) => { e.stopPropagation(); const c = wl.flags[s]; wl.flag(s, c == null ? 0 : c + 1 < FLAG_COLORS.length ? c + 1 : null); }} />
                    <SymbolLogo symbol={s} size={18} /><span className="sym">{s}</span>{wl.notes[s] ? <Icon name="ph-note" size="sm" /> : null}
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
      {rowMenu ? <RowMenu at={rowMenu} onClose={() => setRowMenu(null)} /> : null}
    </section>
  );
}

/** A symbol's right-click menu: flag colours, note, copy to another list, remove. */
function RowMenu({ at, onClose }: { at: { symbol: string; x: number; y: number }; onClose(): void }) {
  const wl = useWatchlist();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const s = at.symbol;
  const run = (fn: () => void) => () => { fn(); onClose(); };
  return createPortal(
    <div className="scrim clear" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }} onContextMenu={(e) => { e.preventDefault(); onClose(); }}>
      <div className="menu dropdown" role="menu" style={{ position: 'fixed', left: Math.min(at.x, window.innerWidth - 240), top: Math.min(at.y, window.innerHeight - 340) }}>
        <div className="menu-row flags">
          {FLAG_COLORS.map((c, i) => <button key={c} className={`wl-flag big${wl.flags[s] === i ? ' on' : ''}`} style={{ background: c }} aria-label={`flag ${i + 1}`} onClick={run(() => wl.flag(s, wl.flags[s] === i ? null : i))} />)}
        </div>
        <button className="menu-item" onClick={run(() => setSymbol(s))}><span>Open chart</span><span className="sym muted">{s}</span></button>
        <button className="menu-item" onClick={run(() => { const n = window.prompt(`Note for ${s}`, wl.notes[s] ?? ''); if (n != null) wl.note(s, n); })}><Icon name="ph-note" size="sm" /><span>{wl.notes[s] ? 'Edit note…' : 'Add note…'}</span></button>
        {wl.lists.filter((l) => l.id !== wl.active).map((l) => (
          <button key={l.id} className="menu-item" onClick={run(() => wl.add(s, l.id))}><span>Add to {l.name}</span></button>
        ))}
        <div className="menu-sep" />
        <button className="menu-item" onClick={run(() => wl.remove(s))}><Icon name="ph-trash" size="sm" /><span>Remove from list</span></button>
      </div>
    </div>,
    document.body,
  );
}
