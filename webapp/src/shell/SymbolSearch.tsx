import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { searchText } from '../data/names';
import type { Instrument, Market } from '../data/types';
import type { StringKey } from '../i18n/strings';
import { highlightPositions } from '../engine/fuzzy.js';
import { exprLegs, instrumentOf, isExpression, normaliseExpression } from '../data/symbols';
import { activeChart, applySymbol } from '../state/grid';
import { useCompare, COMPARE_COLORS } from '../state/compare';
import { useL } from '../pages/kit';
import './topbar.css';

// TradingView's symbol search: a centred dialog, the query field on top, market tabs under it,
// one row per instrument (logo, ticker with the matched letters marked, name, type, source).
// Arrow keys move, Enter opens, Esc closes. Recent searches lead an empty query; a formula such
// as BTCUSDT/ETHUSDT opens as a spread. In compare mode rows are added and removed in place.
const TABS: { id: Market | 'all'; label: StringKey }[] = [
  { id: 'all', label: 'all' }, { id: 'crypto', label: 'crypto' }, { id: 'forex', label: 'forex' },
  { id: 'metals', label: 'metals' }, { id: 'indices', label: 'indices' }, { id: 'energy', label: 'energy' },
];
const MAX_ROWS = 80;
const RECENT_KEY = 'pc.symsearch.recent.v1';

export function readRecent(): string[] {
  try { const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as unknown; return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string').slice(0, 12) : []; } catch { return []; }
}
export function pushRecent(symbol: string): void {
  try { localStorage.setItem(RECENT_KEY, JSON.stringify([symbol, ...readRecent().filter((s) => s !== symbol)].slice(0, 12))); } catch { /* storage blocked */ }
}

/** TradingView's type column: what the instrument is, in the reader's language. */
function typeLabel(market: Market, fa: boolean): string {
  if (fa) return { crypto: 'کریپتو اسپات', forex: 'فارکس', metals: 'فلز CFD', indices: 'شاخص CFD', energy: 'انرژی CFD' }[market];
  return { crypto: 'spot crypto', forex: 'forex', metals: 'commodity cfd', indices: 'index cfd', energy: 'commodity cfd' }[market];
}
/** Where the prices come from, as the phone names its candle sources (CandleGateway.sourceName). */
const sourceOf = (market: Market) => (market === 'crypto' ? 'LBank' : 'MetaTrader 5');

/** The ticker with the letters the query matched marked, as TradingView marks them. */
function Marked({ text, q }: { text: string; q: string }): ReactNode {
  // Only a run of letters is marked, as TradingView does; a match found in the name marks nothing.
  if (!q || !text.toLowerCase().includes(q.toLowerCase())) return text;
  const pos = new Set(highlightPositions(text, q));
  if (!pos.size) return text;
  const out: ReactNode[] = [];
  let run = '';
  let on = false;
  const flush = (k: number) => { if (run) out.push(on ? <b key={k} className="hl">{run}</b> : run); run = ''; };
  [...text].forEach((ch, i) => {
    const m = pos.has(i);
    if (m !== on) { flush(i); on = m; }
    run += ch;
  });
  flush(text.length);
  return out;
}

export function SymbolSearch({ onClose, onPick, initial = '', allowExpr = false, mode = 'pick', current }: {
  onClose(): void;
  onPick?(symbol: string): void;
  initial?: string;
  /** Spreads and formulas may be opened (charts only; lists and backends take plain symbols). */
  allowExpr?: boolean;
  /** 'compare': TradingView's "Compare symbol", rows added and removed without closing. */
  mode?: 'pick' | 'compare';
  /** The symbol the field starts with, selected, when the dialog opens from a chart's button. */
  current?: string;
}) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const instruments = useMarket((s) => s.instruments);
  const compared = useCompare((s) => s.symbols);
  const start = initial || (mode === 'pick' && !onPick ? activeChart().symbol : current ?? '');
  const [q, setQ] = useState(start);
  const [tab, setTab] = useState<Market | 'all'>('all');
  const [cursor, setCursor] = useState(0);
  const [recent] = useState(readRecent);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);
  // Opened from the symbol button the current symbol is selected, so typing replaces it.
  const typed = useRef(Boolean(initial));

  useEffect(() => {
    const el = input.current;
    if (!el) return;
    el.focus();
    if (typed.current) el.setSelectionRange(el.value.length, el.value.length); else el.select();
  }, []);

  const index = useMemo(() => Object.values(instruments)
    .filter((i) => hasLogo(i.symbol) && !isExpression(i.symbol))
    .map((i) => ({ i, text: searchText(i.symbol, i.market, i.base, i.quote) })), [instruments]);

  const query = typed.current || q !== start ? q : '';
  const needle = query.trim().toLowerCase().replace(/[\s/]/g, '');
  const expr = allowExpr && mode === 'pick' ? normaliseExpression(query) : null;
  const exprKnown = expr ? exprLegs(expr)!.every((s) => instruments[s] || Object.keys(instruments).length === 0) : false;

  const rows = useMemo(() => {
    if (!needle) {
      // An empty query: the recent symbols first (TradingView's history), then the catalogue.
      const recentRows = tab === 'all' ? recent.map((s) => instruments[s]).filter((i): i is Instrument => Boolean(i) && hasLogo(i!.symbol)) : [];
      const order: Record<Market, number> = { crypto: 0, forex: 1, metals: 2, indices: 3, energy: 4 };
      const rest = index.map((x) => x.i).filter((i) => (tab === 'all' || i.market === tab) && !recentRows.includes(i))
        .sort((a, b) => order[a.market] - order[b.market]);
      return { recent: recentRows, list: rest.slice(0, MAX_ROWS) };
    }
    const hits = index.filter(({ i, text }) => (tab === 'all' || i.market === tab) && (text.replace(/[\s/]/g, '').includes(needle) || i.symbol.toLowerCase().includes(needle)));
    const rank = (s: string) => {
      const l = s.toLowerCase();
      if (l === needle || l === `${needle}usdt`) return 0;
      if (l.startsWith(needle)) return 1;
      if (l.includes(needle)) return 2;
      return 3;
    };
    const order: Record<Market, number> = { forex: 1, metals: 0, crypto: 2, indices: 3, energy: 4 };
    hits.sort((a, b) => rank(a.i.symbol) - rank(b.i.symbol) || order[a.i.market] - order[b.i.market] || a.i.symbol.length - b.i.symbol.length || a.i.symbol.localeCompare(b.i.symbol));
    return { recent: [] as Instrument[], list: hits.slice(0, MAX_ROWS).map((h) => h.i) };
  }, [index, needle, tab, recent, instruments]);

  // The rows the cursor walks: the spread (if any), the recent ones, then the list.
  const flat: string[] = [...(expr ? [expr] : []), ...rows.recent.map((i) => i.symbol), ...rows.list.map((i) => i.symbol)];

  useEffect(() => { setCursor(0); }, [needle, tab]);
  useEffect(() => { list.current?.querySelector('.search-row.on')?.scrollIntoView({ block: 'nearest' }); }, [cursor]);

  const choose = (s: string) => {
    if (mode === 'compare') {
      const c = useCompare.getState();
      if (c.symbols.includes(s)) c.remove(s); else if (onPick) onPick(s); else c.add(s);
      return;
    }
    pushRecent(s);
    if (onPick) onPick(s); else applySymbol(s);
    onClose();
  };

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') { e.stopPropagation(); onClose(); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(flat.length - 1, c + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
    else if (e.key === 'Enter' && flat[cursor]) { e.preventDefault(); choose(flat[cursor]!); }
  };

  const row = (i: Instrument, n: number) => {
    // The names are read now: the catalogue may have been filed before the name maps arrived.
    const named = i.nameFa || i.nameEn ? i : instrumentOf(i.symbol);
    const name = fa ? named.nameFa : named.nameEn;
    const added = mode === 'compare' && compared.includes(i.symbol);
    return (
      <button key={i.symbol} role="option" aria-selected={n === cursor} className={`search-row${n === cursor ? ' on' : ''}${added ? ' added' : ''}`}
        onMouseEnter={() => setCursor(n)} onClick={() => choose(i.symbol)}>
        <SymbolLogo symbol={i.symbol} size={24} />
        <span className="sym"><Marked text={i.symbol} q={needle} /></span>
        <span className={`name${fa ? ' fa' : ''}`}>{name ?? ''}</span>
        <span className="src">
          <span className={`kind${fa ? ' fa' : ''}`}>{typeLabel(i.market, fa)}</span>
          <span className="venue">{sourceOf(i.market)}</span>
        </span>
        {mode === 'compare' ? (
          <span className={`add${added ? ' on' : ''}`} style={added ? { color: COMPARE_COLORS[compared.indexOf(i.symbol) % COMPARE_COLORS.length] } : undefined}>
            <Icon name={added ? 'ph-check' : 'ph-plus'} size="sm" />
          </span>
        ) : null}
      </button>
    );
  };

  let n = expr ? 1 : 0;
  const title = mode === 'compare' ? L('مقایسه‌ی نماد', 'Compare symbol') : t('symbolSearch');
  return (
    <div className="scrim search-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`dialog search ss${mode === 'compare' ? ' compare' : ''}`} role="dialog" aria-modal="true" aria-label={title} onKeyDown={onKey} dir={fa ? 'rtl' : 'ltr'}>
        <header className="dialog-head">
          <h2 className={fa ? 'fa' : ''}>{title}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <label className="ss-field">
          <Icon name="search" size="md" />
          <input ref={input} value={q} onChange={(e) => { typed.current = true; setQ(e.target.value); }}
            placeholder={t('symbolSearch')} spellCheck={false} autoComplete="off" dir="ltr" aria-label={t('symbolSearch')} />
          {q ? <button className="ss-clear" onClick={() => { typed.current = true; setQ(''); input.current?.focus(); }} aria-label="clear"><Icon name="ph-x" size="sm" /></button> : null}
        </label>
        <nav className="chips ss-tabs" role="tablist">
          {TABS.map((x) => (
            <button key={x.id} role="tab" aria-selected={tab === x.id} className={`chip${tab === x.id ? ' on' : ''}`} onClick={() => { setTab(x.id); input.current?.focus(); }}>
              <span className={fa ? 'fa' : ''}>{t(x.label)}</span>
            </button>
          ))}
        </nav>
        <div className="search-list" ref={list} role="listbox">
          {expr ? (
            <button role="option" aria-selected={cursor === 0} className={`search-row expr-row${cursor === 0 ? ' on' : ''}`} onMouseEnter={() => setCursor(0)} onClick={() => choose(expr)}>
              <span className="logo logo-expr" aria-hidden><Icon name="ph-function" size="sm" /></span>
              <span className="sym">{expr}</span>
              <span className={`name${fa ? ' fa' : ''}`}>{exprKnown ? L('اسپرد / فرمول از نمادهای آن', 'Spread built from its symbols') : L('یکی از نمادها در فهرست نیست', 'A symbol in it is not listed')}</span>
              <span className="src"><span className={`kind${fa ? ' fa' : ''}`}>{L('اسپرد', 'spread')}</span></span>
            </button>
          ) : null}
          {rows.recent.length ? <div className={`ss-section${fa ? ' fa' : ''}`}>{L('جستجوهای اخیر', 'Recent searches')}</div> : null}
          {rows.recent.map((i) => row(i, n++))}
          {rows.recent.length && rows.list.length ? <div className={`ss-section${fa ? ' fa' : ''}`}>{L('همه‌ی نمادها', 'All symbols')}</div> : null}
          {rows.list.map((i) => row(i, n++))}
          {!flat.length ? <div className="empty fa">{t('noResults')}</div> : null}
        </div>
        {allowExpr && mode === 'pick' ? (
          <footer className={`ss-foot${fa ? ' fa' : ''}`}>
            {L('اسپرد یا فرمول هم می‌شود: ', 'Spreads and formulas work too: ')}<span className="num">BTCUSDT/ETHUSDT · XAUUSD*2 · (EURUSD+GBPUSD)/2</span>
          </footer>
        ) : null}
      </div>
    </div>
  );
}
