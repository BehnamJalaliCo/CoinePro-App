import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Dropdown } from '../ui/Menu';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { ALL_COLS, FLAG_COLORS, FLAG_NAMES, exportList, parseList, useWatchView, useWatchlist, type WCol, type WSortKey } from '../state/watchlist';
import { useCompare } from '../state/compare';
import { usePanels } from '../state/panels';
import { usePage } from '../state/page';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume, guessDigits } from '../chart/format';
import { Icon } from '../ui/Icon';
import { useFlash } from '../ui/useFlash';
import { usePrevCloses, PREV_CLOSE_LIMIT } from '../data/symbolStats';
import { marketStatus } from '../data/sessions';
import { backendOf, marketOf } from '../data/symbols';
import { searchText } from '../data/names';
import { downloadCsv, useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import type { Instrument, Market, Ticker } from '../data/types';
import type { StringKey } from '../i18n/strings';
import './panels-x.css';

const SECTION_LABEL: Record<Market, StringKey> = { crypto: 'crypto', forex: 'forex', metals: 'metals', indices: 'indices', energy: 'energy' };
const MARKETS: Market[] = ['crypto', 'forex', 'metals', 'indices', 'energy'];

// TradingView's watchlist: Symbol · Last · Chg · Chg% (and any of Volume, High, Low, Range%), 30px
// rows, the reader's own sections (or sections by market), the open symbol outlined; several lists
// under the title, seven colour flags (each a list of its own), a note per symbol, import and
// export as text or CSV, drag to reorder, Shift/Ctrl to select several, ↑/↓ to walk the chart
// through the list. Left to right in both languages, like the chart.

export interface RowValues { last: number | null; change: number | null; changePct: number | null; volume: number | null; high: number | null; low: number | null; range: number | null }

/** One symbol's figures; the change from the previous daily close when one is given. */
export function rowValues(s: string, tk: Ticker | undefined, prevClose?: number): RowValues {
  if (!tk) return { last: null, change: null, changePct: null, volume: null, high: null, low: null, range: null };
  const change = prevClose ? tk.last - prevClose : tk.change;
  const changePct = prevClose ? ((tk.last - prevClose) / prevClose) * 100 : tk.changePct;
  const volume = tk.volume != null ? tk.volume * (marketOf(s) === 'crypto' ? tk.last : 1) : null;
  const high = tk.high != null ? Math.max(tk.high, tk.last) : null;
  const low = tk.low != null ? Math.min(tk.low, tk.last) : null;
  return { last: tk.last, change, changePct, volume, high, low, range: high != null && low ? ((high - low) / low) * 100 : null };
}

const toneOf = (v: number | null) => (v == null || v === 0 ? '' : v > 0 ? 'up' : 'down');
const signed = (v: number, text: string) => `${v < 0 ? '−' : v > 0 ? '+' : ''}${text}`;

function copyText(text: string): void {
  try { void navigator.clipboard?.writeText(text); } catch { /* clipboard unavailable */ }
}

export function openAlertDialog(detail: Record<string, unknown>): void {
  window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail }));
}

interface Group { key: string; title: string | null; sectionId?: string; collapsed: boolean; list: string[]; total: number }

export function Watchlist({ onAdd }: { onAdd(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tickers = useMarket((s) => s.tickers);
  const instruments = useMarket((s) => s.instruments);
  const wl = useWatchlist();
  const view = useWatchView();
  const [flagView, setFlagView] = useState<number | null>(null);
  const [menu, setMenu] = useState<'lists' | 'more' | null>(null);
  const [rowMenu, setRowMenu] = useState<{ symbol: string; x: number; y: number } | null>(null);
  const [secMenu, setSecMenu] = useState<{ id: string; x: number; y: number } | null>(null);
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [anchor, setAnchor] = useState<string | null>(null);
  const [drop, setDrop] = useState<string | null>(null);
  const [listQ, setListQ] = useState('');
  const titleRef = useRef<HTMLButtonElement>(null);
  const moreRef = useRef<HTMLButtonElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const current = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const activeList = wl.lists.find((l) => l.id === wl.active);
  // The first list is created as «Watchlist»; it reads in the reader's language until renamed.
  const listName = (n: string) => (n === 'Watchlist' ? t('watchlist') : n);
  const activeName = flagView != null ? `${L('پرچم', 'Flagged')} · ${fa ? FLAG_NAMES[flagView]!.fa : FLAG_NAMES[flagView]!.en}` : (activeList ? listName(activeList.name) : t('watchlist'));
  const symbols = useMemo(() => (flagView == null ? wl.symbols : Object.entries(wl.flags).filter(([, c]) => c === flagView).map(([s]) => s)), [flagView, wl.symbols, wl.flags]);
  const loaded = Object.keys(instruments).length > 0;
  const visible = useMemo(() => symbols.filter((s) => !loaded || hasLogo(s)), [symbols, loaded]);
  const prev = usePrevCloses(visible, view.fromClose);
  const values = (s: string) => rowValues(s, tickers[s], prev[s]);
  const groupBy = flagView != null ? 'none' : view.groupBy;
  const canDrag = !view.sort && flagView == null && groupBy !== 'market';

  // Sections: the reader's own (loose symbols first, no header), by market, or none.
  const groups = useMemo<Group[]>(() => {
    const order = (list: string[]) => {
      const sort = view.sort;
      if (!sort) return list;
      const v = (s: string): number | string => (sort.key === 'symbol' ? s : (rowValues(s, tickers[s], prev[s])[sort.key] ?? -Infinity));
      return [...list].sort((a, b) => { const x = v(a), y = v(b); const c = typeof x === 'string' ? x.localeCompare(y as string) : (x as number) - (y as number); return sort.desc ? -c : c; });
    };
    const shown = new Set(visible);
    if (groupBy === 'sections') {
      const secs = activeList?.sections ?? [];
      const inSec = new Set(secs.flatMap((s) => s.symbols));
      const out: Group[] = [{ key: 'loose', title: null, collapsed: false, list: order(visible.filter((s) => !inSec.has(s))), total: 0 }];
      for (const s of secs) { const list = s.symbols.filter((x) => shown.has(x)); out.push({ key: s.id, sectionId: s.id, title: s.name, collapsed: Boolean(s.collapsed), list: order(list), total: list.length }); }
      return out.filter((g) => g.title != null || g.list.length);
    }
    if (groupBy === 'market') {
      const by = new Map<Market, string[]>();
      for (const s of visible) { const m = instruments[s]?.market ?? marketOf(s); by.set(m, [...(by.get(m) ?? []), s]); }
      return MARKETS.filter((m) => by.has(m)).map((m) => ({ key: m, title: t(SECTION_LABEL[m]), collapsed: (view.collapsed ?? []).includes(m), list: order(by.get(m)!), total: by.get(m)!.length }));
    }
    return [{ key: 'all', title: null, collapsed: false, list: order(visible), total: visible.length }];
  }, [visible, groupBy, activeList?.sections, instruments, view.sort, view.collapsed, tickers, prev, t]);

  const rows = useMemo(() => groups.filter((g) => !g.collapsed).flatMap((g) => g.list), [groups]);
  useEffect(() => { setSelected((sel) => (sel.every((s) => symbols.includes(s)) ? sel : sel.filter((s) => symbols.includes(s)))); }, [symbols]);

  // A column title sorts by it: descending, ascending, then the list's own order, as on TradingView.
  const sortBy = (key: WSortKey) => {
    const s = view.sort;
    view.setView({ sort: s?.key !== key ? { key, desc: key !== 'symbol' } : s.desc === (key !== 'symbol') ? { key, desc: !s.desc } : null });
  };
  const mark = (key: WSortKey) => (view.sort?.key === key ? (view.sort.desc ? ' ▼' : ' ▲') : '');
  const toggleGroup = (g: Group) => {
    if (g.sectionId) wl.toggleSection(g.sectionId);
    else { const c = view.collapsed ?? []; view.setView({ collapsed: c.includes(g.key) ? c.filter((x) => x !== g.key) : [...c, g.key] }); }
  };

  const ask = (fa0: string, en: string, def = '') => window.prompt(L(fa0, en), def);
  const doImport = () => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = '.txt,.csv,text/plain';
    input.onchange = () => { const f = input.files?.[0]; if (f) void f.text().then((txt) => { const p = parseList(txt); if (p.symbols.length) { wl.createList(f.name.replace(/\.[^.]+$/, ''), p.symbols, p.sections); if (p.sections.length) view.setView({ groupBy: 'sections' }); } }); };
    input.click();
  };
  const doExport = () => {
    if (!activeList) return;
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([exportList(activeList)], { type: 'text/plain' }));
    a.download = `${activeName}.txt`; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  };
  const doCsv = () => downloadCsv(`${activeName}.csv`, [
    ['Symbol', 'Description', 'Last', 'Chg', 'Chg%', 'Volume', 'High', 'Low', 'Range%', 'Flag', 'Note'],
    ...rows.map((s) => {
      const v = values(s); const d = instruments[s]?.digits ?? guessDigits(s, v.last ?? undefined);
      const f = wl.flags[s];
      return [s, instruments[s]?.nameEn ?? '', v.last != null ? v.last.toFixed(d) : '', v.change != null ? v.change.toFixed(d) : '', v.changePct != null ? v.changePct.toFixed(2) : '', v.volume != null ? Math.round(v.volume) : '', v.high != null ? v.high.toFixed(d) : '', v.low != null ? v.low.toFixed(d) : '', v.range != null ? v.range.toFixed(2) : '', f != null ? FLAG_NAMES[f]!.en : '', wl.notes[s] ?? ''];
    }),
  ]);
  const newSection = (syms: string[] = []) => { const n = ask('نام بخش تازه', 'Section name', L('بخش تازه', 'NEW SECTION')); if (n) { wl.addSection(n, syms); view.setView({ groupBy: 'sections', sort: null }); } };

  // Click: open; Ctrl/Cmd-click: add to the selection; Shift-click: select the range.
  const onRowClick = (s: string, e: React.MouseEvent) => {
    if (e.shiftKey && anchor) {
      const a = rows.indexOf(anchor), b = rows.indexOf(s);
      if (a >= 0 && b >= 0) { setSelected(rows.slice(Math.min(a, b), Math.max(a, b) + 1)); return; }
    }
    if (e.ctrlKey || e.metaKey) { setSelected((sel) => (sel.includes(s) ? sel.filter((x) => x !== s) : [...sel, s])); setAnchor(s); return; }
    setSelected([]); setAnchor(s); setSymbol(s);
  };
  const onKey = (e: React.KeyboardEvent) => {
    if (e.target !== e.currentTarget && (e.target as HTMLElement).closest('input, textarea')) return;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const i = rows.indexOf(current);
      const next = rows[e.key === 'ArrowDown' ? Math.min(rows.length - 1, i + 1) : Math.max(0, i < 0 ? 0 : i - 1)];
      if (next) { setSymbol(next); setAnchor(next); setSelected([]); requestAnimationFrame(() => bodyRef.current?.querySelector(`[data-symbol="${next}"]`)?.scrollIntoView({ block: 'nearest' })); }
    } else if ((e.key === 'Delete' || e.key === 'Backspace') && flagView == null) {
      e.preventDefault();
      wl.removeMany(selected.length ? selected : rows.includes(current) ? [current] : []);
      setSelected([]);
    } else if (e.key === 'Escape') setSelected([]);
    else if (e.key === 'Enter' && e.altKey) { const c = wl.flags[current]; wl.flag(current, c == null ? 0 : null); }
  };

  // Drag a row (or the selection) onto a row or a section title; drag a section title onto another.
  const dragData = (e: React.DragEvent, kind: 'rows' | 'section', payload: string[] | string) => { e.dataTransfer.setData(`application/x-pc-${kind}`, JSON.stringify(payload)); e.dataTransfer.effectAllowed = 'move'; };
  const onDropAt = (e: React.DragEvent, before: string | null, sectionId: string | null) => {
    e.preventDefault(); setDrop(null);
    const rowsRaw = e.dataTransfer.getData('application/x-pc-rows');
    const secRaw = e.dataTransfer.getData('application/x-pc-section');
    if (rowsRaw) wl.move(JSON.parse(rowsRaw) as string[], before, sectionId);
    else if (secRaw && sectionId) wl.moveSection(JSON.parse(secRaw) as string, sectionId);
  };

  const cols = view.columns;
  const wide = view.table && cols.length > 3;
  const grid = { gridTemplateColumns: `minmax(${wide ? 112 : 0}px, 1.75fr) ${cols.map((c) => `minmax(${wide ? 62 : 0}px, ${c === 'last' ? 1.05 : c === 'changePct' ? 0.85 : 0.95}fr)`).join(' ')}` };
  const colLabel = (c: WCol) => (c === 'last' ? t('last') : c === 'change' ? t('change') : c === 'changePct' ? t('changePct') : c === 'volume' ? L('حجم', 'Vol') : c === 'high' ? L('سقف', 'High') : c === 'low' ? L('کف', 'Low') : L('دامنه٪', 'Range%'));
  const count = fa ? faNum(visible.length) : String(visible.length);

  return (
    <section className={`wl${view.table ? ' table' : ' listview'}${wide ? ' wide' : ''}`} aria-label={t('watchlist')}>
      <header className="wl-head">
        <button ref={titleRef} className="ib wl-title" aria-haspopup="menu" aria-expanded={menu === 'lists'} onClick={() => setMenu(menu === 'lists' ? null : 'lists')}>
          {flagView != null ? <i className="wl-flag" style={{ background: FLAG_COLORS[flagView] }} /> : null}<span className="fa">{activeName}</span><Icon name="ph-caret-down" size="sm" />
        </button>
        <span className="wl-count num" title={L('تعداد نمادها', 'Symbols')}>{count}</span>
        <span className="ws-spacer" />
        <button className={`ib${adding ? ' on' : ''}`} title={L('افزودن نماد', 'Add symbol')} aria-pressed={adding} onClick={() => setAdding((a) => !a)}><Icon name="ph-plus" size="md" /></button>
        <button className={`ib${view.table ? ' on' : ''}`} title={L('نمای جدول', 'Table view')} aria-pressed={view.table} onClick={() => view.setView({ table: !view.table })}><Icon name="ph-table" size="md" /></button>
        <button ref={moreRef} className="ib" aria-haspopup="menu" title={L('تنظیمات فهرست', 'Watchlist settings')} onClick={() => setMenu(menu === 'more' ? null : 'more')}><Icon name="ph-dots-three" size="md" /></button>
      </header>
      {adding ? <AddSymbol onClose={() => setAdding(false)} onMore={onAdd} /> : null}
      {menu === 'lists' ? (
        <Dropdown anchor={titleRef.current} onClose={() => setMenu(null)} className="wl-lists"><div dir={fa ? 'rtl' : 'ltr'}>
          <button className="menu-item" onClick={() => { setMenu(null); openAlertDialog({ list: wl.active }); }}><Icon name="ph-bell" size="sm" /><span>{L('هشدار روی کل فهرست…', 'Add alert on the list…')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); wl.createList(`${activeName} ${L('(کپی)', 'copy')}`, [...wl.symbols], activeList?.sections?.map((s) => ({ name: s.name, symbols: s.symbols }))); }}><Icon name="ph-copy" size="sm" /><span>{L('ساخت کپی…', 'Make a copy…')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); const n = ask('نام تازه‌ی فهرست', 'Rename list', activeName); if (n) wl.renameList(wl.active, n); }}><Icon name="ph-pencil-simple" size="sm" /><span>{L('تغییر نام', 'Rename')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); newSection(); }}><Icon name="ph-rows" size="sm" /><span>{L('افزودن بخش', 'Add section')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); if (wl.symbols.length && window.confirm(L('همه‌ی نمادهای این فهرست پاک شوند؟', 'Clear this list?'))) wl.set([]); }}><Icon name="ph-trash" size="sm" /><span>{L('پاک کردن فهرست', 'Clear list')}</span></button>
          <div className="menu-sep" />
          <button className="menu-item" onClick={() => { setMenu(null); const n = ask('نام فهرست تازه', 'New list name', L('دیده‌بان', 'Watchlist')); if (n) { wl.createList(n); setFlagView(null); } }}><Icon name="ph-list-plus" size="sm" /><span>{L('فهرست تازه…', 'Create new list…')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); doImport(); }}><Icon name="ph-upload-simple" size="sm" /><span>{L('بارگذاری فهرست…', 'Upload list…')}</span></button>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('فهرست‌ها', 'Lists')}</div>
          {wl.lists.length > 5 ? <div className="menu-form"><input autoFocus value={listQ} onChange={(e) => setListQ(e.target.value)} placeholder={L('جستجوی فهرست', 'Search lists')} dir="auto" /></div> : null}
          {wl.lists.filter((l) => !listQ.trim() || l.name.toLowerCase().includes(listQ.trim().toLowerCase())).map((l) => (
            <button key={l.id} className={`menu-item${flagView == null && l.id === wl.active ? ' on' : ''}`} onClick={() => { wl.setActive(l.id); setFlagView(null); setMenu(null); }}>
              <span className="grow">{listName(l.name)}</span><span className="num muted">{fa ? faNum(l.symbols.length) : l.symbols.length}</span>
            </button>
          ))}
          {FLAG_COLORS.some((_, i) => Object.values(wl.flags).includes(i)) ? <div className="menu-title fa">{L('فهرست‌های پرچم‌دار', 'Flagged lists')}</div> : null}
          {FLAG_COLORS.map((c, i) => {
            const n = Object.values(wl.flags).filter((x) => x === i).length;
            return n ? <button key={c} className={`menu-item${flagView === i ? ' on' : ''}`} onClick={() => { setFlagView(i); setMenu(null); }}><i className="wl-flag" style={{ background: c }} /><span className="grow">{L('پرچم‌دار', 'Flagged')} · {fa ? FLAG_NAMES[i]!.fa : FLAG_NAMES[i]!.en}</span><span className="num muted">{fa ? faNum(n) : n}</span></button> : null;
          })}
        </div></Dropdown>
      ) : null}
      {menu === 'more' ? (
        <Dropdown anchor={moreRef.current} onClose={() => setMenu(null)} align="right" className="wl-more"><div dir={fa ? 'rtl' : 'ltr'}>
          <label className="menu-item toggle"><span className="grow">{L('نمای جدول', 'Table view')}</span><input type="checkbox" className="switch" checked={view.table} onChange={() => view.setView({ table: !view.table })} /></label>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('ستون‌ها', 'Customize columns')}</div>
          {ALL_COLS.map((c) => (
            <label key={c} className="menu-item check"><input type="checkbox" checked={cols.includes(c)} onChange={() => view.toggleColumn(c)} /><span>{c === 'last' ? L('آخرین قیمت', 'Last') : c === 'change' ? L('تغییر', 'Change') : c === 'changePct' ? L('درصد تغییر', 'Change %') : c === 'volume' ? L('حجم', 'Volume') : c === 'high' ? L('سقف روز', 'High') : c === 'low' ? L('کف روز', 'Low') : L('دامنه‌ی روز ٪', 'Range %')}</span></label>
          ))}
          <label className="menu-item check" title={L(`برای ${faNum(PREV_CLOSE_LIMIT)} نماد اول`, `For the first ${PREV_CLOSE_LIMIT} symbols`)}><input type="checkbox" checked={view.fromClose} onChange={() => view.setView({ fromClose: !view.fromClose })} /><span>{L('تغییر نسبت به بسته شدن دیروز', 'Change from the previous close')}</span></label>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('نمایش نماد', 'Symbol display')}</div>
          <label className="menu-item check"><input type="checkbox" checked={view.logos} onChange={() => view.setView({ logos: !view.logos })} /><span>{L('لوگو', 'Logo')}</span></label>
          <label className="menu-item check"><input type="radio" name="wl-name" checked={!view.showName} onChange={() => view.setView({ showName: false })} /><span>{L('نماد', 'Symbol')}</span></label>
          <label className="menu-item check"><input type="radio" name="wl-name" checked={view.showName} onChange={() => view.setView({ showName: true })} /><span>{L('نام', 'Name')}</span></label>
          <label className="menu-item check"><input type="checkbox" checked={view.description} onChange={() => view.setView({ description: !view.description })} /><span>{L('توضیح زیر نماد', 'Description line')}</span></label>
          <label className="menu-item check"><input type="checkbox" checked={view.dot} onChange={() => view.setView({ dot: !view.dot })} /><span>{L('وضعیت بازار (باز/بسته)', 'Market status dot')}</span></label>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('گروه‌بندی', 'Group by')}</div>
          {(['sections', 'market', 'none'] as const).map((g) => (
            <label key={g} className="menu-item check"><input type="radio" name="wl-group" checked={view.groupBy === g} onChange={() => view.setView({ groupBy: g })} /><span>{g === 'sections' ? L('بخش‌های من', 'My sections') : g === 'market' ? L('نوع بازار', 'Market') : L('بدون گروه', 'None')}</span></label>
          ))}
          <div className="menu-sep" />
          <div className="menu-title fa">{L('مرتب‌سازی', 'Sort')}</div>
          {([null, 'symbol', 'last', 'change', 'changePct', 'volume'] as (WSortKey | null)[]).map((k) => (
            <label key={k ?? 'manual'} className="menu-item check"><input type="radio" name="wl-sort" checked={(view.sort?.key ?? null) === k} onChange={() => view.setView({ sort: k ? { key: k, desc: k !== 'symbol' } : null })} /><span>{k == null ? L('ترتیب خودم', 'Manual') : k === 'symbol' ? L('نماد', 'Symbol') : colLabel(k)}</span></label>
          ))}
          <div className="menu-sep" />
          <button className="menu-item" onClick={() => { setMenu(null); usePage.getState().open('screener', `wl:${wl.active}`); }}><Icon name="ph-funnel" size="sm" /><span>{L('باز کردن در اسکرینر', 'Open in screener')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); usePage.getState().open('heatmap', `wl:${wl.active}`); }}><Icon name="ph-squares-four" size="sm" /><span>{L('باز کردن در هیت‌مپ', 'Open in heat map')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); const txt = ask('نمادها (با ویرگول یا فاصله؛ ###نام برای بخش)', 'Symbols (comma or space separated; ###Name for a section)'); if (txt) { const p = parseList(txt); p.symbols.forEach((x) => wl.add(x)); p.sections.forEach((s) => wl.addSection(s.name, s.symbols)); } }}><Icon name="ph-plus" size="sm" /><span>{L('افزودن چند نماد…', 'Add symbols…')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); doExport(); }}><Icon name="ph-download-simple" size="sm" /><span>{L('خروجی فهرست (.txt)', 'Export list (.txt)')}</span></button>
          <button className="menu-item" onClick={() => { setMenu(null); doCsv(); }}><Icon name="ph-download-simple" size="sm" /><span>{L('خروجی با قیمت‌ها (.csv)', 'Export with prices (.csv)')}</span></button>
          {Object.keys(wl.flags).length ? <button className="menu-item" onClick={() => { setMenu(null); wl.unflagAll(); setFlagView(null); }}><Icon name="ph-flag" size="sm" /><span>{L('برداشتن همه‌ی پرچم‌ها', 'Unflag all symbols')}</span></button> : null}
          {wl.lists.length > 1 ? <button className="menu-item" onClick={() => { setMenu(null); if (window.confirm(L(`فهرست «${activeName}» حذف شود؟`, `Delete "${activeName}"?`))) wl.deleteList(wl.active); }}><Icon name="ph-trash" size="sm" /><span>{L('حذف فهرست', 'Delete list')}</span></button> : null}
        </div></Dropdown>
      ) : null}
      <div className="wl-scroll">
        {view.table ? (
          <div className="wl-cols" role="row" style={grid}>
            <button className="c-sym fa" onClick={() => sortBy('symbol')}>{t('symbol')}{mark('symbol')}</button>
            {cols.map((c) => <button key={c} className="c-num fa" onClick={() => sortBy(c)}>{colLabel(c)}{mark(c)}</button>)}
          </div>
        ) : null}
        <div className="wl-body" role="list" tabIndex={0} ref={bodyRef} onKeyDown={onKey} aria-label={activeName}>
          {!visible.length ? (
            <div className="empty fa wl-empty">
              {flagView != null ? L('نمادی با این پرچم نیست.', 'No symbol has this flag.') : L('این فهرست خالی است. با + نماد بیفزایید.', 'This list is empty. Add symbols with +.')}
              {flagView == null ? <button className="link" onClick={() => setAdding(true)}>{L('افزودن نماد', 'Add symbol')}</button> : null}
            </div>
          ) : null}
          {groups.map((g) => (
            <div key={g.key} className={`wl-section${drop === `sec:${g.key}` ? ' drop' : ''}`}>
              {g.title != null ? (
                <div className={`wl-section-title${g.collapsed ? ' closed' : ''}`} role="button" tabIndex={-1} onClick={() => toggleGroup(g)}
                  draggable={Boolean(g.sectionId) && canDrag} onDragStart={(e) => g.sectionId && dragData(e, 'section', g.sectionId)}
                  onDragOver={(e) => { if (canDrag && g.sectionId) { e.preventDefault(); setDrop(`sec:${g.key}`); } }} onDragLeave={() => setDrop(null)}
                  onDrop={(e) => g.sectionId && onDropAt(e, g.list[0] ?? null, g.sectionId)}
                  onContextMenu={(e) => { if (g.sectionId) { e.preventDefault(); setSecMenu({ id: g.sectionId, x: e.clientX, y: e.clientY }); } }}>
                  <Icon name="ph-caret-down" size="sm" /><span className="fa">{g.title}</span>
                  <span className="wl-sec-count num">{g.collapsed ? (fa ? faNum(g.total) : g.total) : ''}</span>
                </div>
              ) : null}
              {g.collapsed ? null : g.list.map((s) => (
                <WatchRow key={s} symbol={s} inst={instruments[s]} values={values(s)} cols={cols} grid={view.table ? grid : undefined}
                  on={s === current} sel={selected.includes(s)} flag={wl.flags[s]} note={wl.notes[s]} drop={drop === s} draggable={canDrag}
                  logos={view.logos} desc={view.description} showName={view.showName} dot={view.dot} fa={fa}
                  onClick={(e) => onRowClick(s, e)}
                  onMenu={(e) => { e.preventDefault(); if (!selected.includes(s)) setSelected([]); setRowMenu({ symbol: s, x: e.clientX, y: e.clientY }); }}
                  onFlag={() => { const c = wl.flags[s]; wl.flag(s, c == null ? 0 : c + 1 < FLAG_COLORS.length ? c + 1 : null); }}
                  onRemove={flagView == null ? () => wl.remove(s) : undefined}
                  onDragStart={(e) => dragData(e, 'rows', selected.includes(s) ? rows.filter((x) => selected.includes(x)) : [s])}
                  onDragOver={(e) => { if (canDrag) { e.preventDefault(); setDrop(s); } }} onDragLeave={() => setDrop((d) => (d === s ? null : d))}
                  onDrop={(e) => onDropAt(e, s, g.sectionId ?? null)} />
              ))}
              {canDrag && g.key === 'loose' && groups.length > 1 ? (
                <div className={`wl-drop-end${drop === 'end:loose' ? ' drop' : ''}`} onDragOver={(e) => { e.preventDefault(); setDrop('end:loose'); }} onDragLeave={() => setDrop(null)} onDrop={(e) => onDropAt(e, null, null)} />
              ) : null}
            </div>
          ))}
        </div>
      </div>
      {selected.length > 1 ? (
        <div className="wl-selbar">
          <span className="fa">{L(`${faNum(selected.length)} نماد انتخاب شده`, `${selected.length} selected`)}</span>
          <span className="ws-spacer" />
          <button className="lt" title={L('پرچم', 'Flag')} onClick={(e) => setRowMenu({ symbol: selected[0]!, x: e.clientX, y: e.clientY })}><Icon name="ph-dots-three" size="sm" /></button>
          <button className="lt" title={L('حذف', 'Remove')} disabled={flagView != null} onClick={() => { wl.removeMany(selected); setSelected([]); }}><Icon name="ph-trash" size="sm" /></button>
          <button className="lt" title={L('لغو انتخاب', 'Clear selection')} onClick={() => setSelected([])}><Icon name="ph-x" size="sm" /></button>
        </div>
      ) : null}
      {rowMenu ? <RowMenu at={rowMenu} many={selected.length > 1 && selected.includes(rowMenu.symbol) ? selected : null} flagged={flagView != null} onNewSection={newSection} onClose={() => setRowMenu(null)} onDone={() => setSelected([])} /> : null}
      {secMenu ? <SectionMenu at={secMenu} onClose={() => setSecMenu(null)} /> : null}
    </section>
  );
}

interface RowProps {
  symbol: string; inst: Instrument | undefined; values: RowValues; cols: WCol[]; grid?: React.CSSProperties;
  on: boolean; sel: boolean; flag: number | undefined; note: string | undefined; drop: boolean; draggable: boolean;
  logos: boolean; desc: boolean; showName: boolean; dot: boolean; fa: boolean;
  onClick(e: React.MouseEvent): void; onMenu(e: React.MouseEvent): void; onFlag(): void; onRemove?(): void;
  onDragStart(e: React.DragEvent): void; onDragOver(e: React.DragEvent): void; onDragLeave(): void; onDrop(e: React.DragEvent): void;
}

function WatchRow(p: RowProps) {
  const L = useL();
  const { symbol: s, values: v } = p;
  const digits = p.inst?.digits ?? guessDigits(s, v.last ?? undefined);
  const { ref, tick } = useFlash<HTMLSpanElement>(v.last);
  const name = (p.fa ? p.inst?.nameFa : p.inst?.nameEn) ?? '';
  const tone = toneOf(v.change);
  const open = marketStatus(p.inst?.market ?? marketOf(s));
  const venue = backendOf(s) === 'tradeyar' ? 'TradeYar' : 'CoinePro FX';
  const tip = [`${s}${name ? ` — ${name}` : ''}`, `${p.inst?.market ? p.inst.market.toUpperCase() : marketOf(s).toUpperCase()} · ${venue}`, p.note].filter(Boolean).join('\n');
  const cell = (c: WCol) => {
    switch (c) {
      case 'last': return <span key={c} ref={ref} className={`c-num num last ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{v.last != null ? formatPrice(v.last, digits) : ''}</span>;
      case 'change': return <span key={c} className={`c-num num ${tone}`}>{v.change != null ? signed(v.change, formatPrice(Math.abs(v.change), digits)) : ''}</span>;
      case 'changePct': return <span key={c} className={`c-num num pct ${toneOf(v.changePct)}`}><b>{v.changePct != null ? signed(v.changePct, `${Math.abs(v.changePct).toFixed(2)}%`) : ''}</b></span>;
      case 'volume': return <span key={c} className="c-num num">{v.volume != null ? formatVolume(v.volume) : '—'}</span>;
      case 'high': return <span key={c} className="c-num num">{v.high != null ? formatPrice(v.high, digits) : '—'}</span>;
      case 'low': return <span key={c} className="c-num num">{v.low != null ? formatPrice(v.low, digits) : '—'}</span>;
      case 'range': return <span key={c} className="c-num num">{v.range != null ? `${v.range.toFixed(2)}%` : '—'}</span>;
    }
  };
  const symCell = (
    <span className="c-sym">
      <span className="wl-flag-btn" role="button" aria-label={L('پرچم', 'flag')} style={p.flag != null ? { background: FLAG_COLORS[p.flag] } : undefined} onClick={(e) => { e.stopPropagation(); p.onFlag(); }} />
      {p.dot ? <i className={`wl-dot${open ? ' open' : ''}`} title={open ? L('بازار باز است', 'Market open') : L('بازار بسته است', 'Market closed')} /> : null}
      {p.logos ? <SymbolLogo symbol={s} size={18} /> : null}
      <span className="wl-names">
        <span className={p.showName && name ? `sym name${p.fa ? ' fa' : ''}` : 'sym'}>{p.showName && name ? name : s}</span>
        {p.desc && name && !p.showName ? <span className={`wl-desc${p.fa ? ' fa' : ''}`}>{name}</span> : null}
      </span>
      {p.note ? <Icon name="ph-note" size="sm" className="wl-note" /> : null}
      {p.onRemove ? <span className="wl-x" role="button" aria-label={L('حذف', 'Remove')} onClick={(e) => { e.stopPropagation(); p.onRemove!(); }}><Icon name="ph-x" size="sm" /></span> : null}
    </span>
  );
  return (
    <button role="listitem" data-symbol={s} className={`wl-row${p.on ? ' on' : ''}${p.sel ? ' sel' : ''}${p.drop ? ' drop' : ''}${p.desc ? ' two' : ''}`} style={p.grid} title={tip}
      onClick={p.onClick} onContextMenu={p.onMenu} draggable={p.draggable} onDragStart={p.onDragStart} onDragOver={p.onDragOver} onDragLeave={p.onDragLeave} onDrop={p.onDrop}>
      {symCell}
      {p.grid ? p.cols.map(cell) : (
        <span className="wl-list-figs">
          <span ref={ref} className={`num last ${tick > 0 ? 'up' : tick < 0 ? 'down' : ''}`}>{v.last != null ? formatPrice(v.last, digits) : ''}</span>
          <span className={`num small ${tone}`}>{v.change != null ? signed(v.change, formatPrice(Math.abs(v.change), digits)) : ''} <b className={`pct ${toneOf(v.changePct)}`}>{v.changePct != null ? signed(v.changePct, `${Math.abs(v.changePct).toFixed(2)}%`) : ''}</b></span>
        </span>
      )}
    </button>
  );
}

/** The in-panel symbol search under the header (the old Pro-Chart's): type, pick, keep adding. */
function AddSymbol({ onClose, onMore }: { onClose(): void; onMore(): void }) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const instruments = useMarket((s) => s.instruments);
  const wl = useWatchlist();
  const [q, setQ] = useState('');
  const [cursor, setCursor] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.focus(); }, []);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase().replace(/[\s/]/g, '');
    if (!needle) return [];
    const hits = Object.values(instruments).filter((i) => hasLogo(i.symbol) && (i.symbol.toLowerCase().includes(needle) || searchText(i.symbol, i.market, i.base, i.quote).replace(/[\s/]/g, '').includes(needle)));
    const rank = (s: string) => { const l = s.toLowerCase(); return l === needle || l === `${needle}usdt` ? 0 : l.startsWith(needle) ? 1 : 2; };
    return hits.sort((a, b) => rank(a.symbol) - rank(b.symbol) || a.symbol.length - b.symbol.length || a.symbol.localeCompare(b.symbol)).slice(0, 30);
  }, [q, instruments]);
  useEffect(() => setCursor(0), [q]);
  const pick = (s: string) => { wl.add(s); setQ(''); input.current?.focus(); };
  return (
    <div className="wl-add">
      <label className="wl-add-field">
        <Icon name="search" size="sm" />
        <input ref={input} value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('افزودن نماد…', 'Add symbol…')} spellCheck={false} autoComplete="off" dir="auto"
          onKeyDown={(e) => {
            if (e.key === 'Escape') onClose();
            else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(rows.length - 1, c + 1)); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(0, c - 1)); }
            else if (e.key === 'Enter' && rows[cursor]) pick(rows[cursor]!.symbol);
          }} />
        <button className="lt" onClick={onClose} aria-label={t('back')}><Icon name="ph-x" size="sm" /></button>
      </label>
      {q.trim() ? (
        <div className="wl-add-list" role="listbox">
          {!rows.length ? <div className="empty fa">{t('noResults')} <button className="link" onClick={onMore}>{L('جستجوی کامل', 'Full search')}</button></div> : rows.map((i, n) => {
            const have = wl.symbols.includes(i.symbol);
            return (
              <button key={i.symbol} role="option" aria-selected={n === cursor} className={`wl-add-row${n === cursor ? ' on' : ''}`} onMouseEnter={() => setCursor(n)} onClick={() => pick(i.symbol)}>
                <SymbolLogo symbol={i.symbol} size={20} />
                <span className="sym">{i.symbol}</span>
                <span className={`name${lang === 'fa' ? ' fa' : ''}`}>{(lang === 'fa' ? i.nameFa : i.nameEn) ?? ''}</span>
                <span className="kind fa">{t(i.market)}</span>
                <Icon name={have ? 'ph-check' : 'ph-plus'} size="sm" />
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

function Menu({ at, onClose, children, width = 260 }: { at: { x: number; y: number }; onClose(): void; children: React.ReactNode; width?: number }) {
  const lang = usePrefs((s) => s.lang);
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ left: at.x, top: at.y });
  useEffect(() => {
    const r = ref.current?.getBoundingClientRect();
    if (r) setPos({ left: Math.max(4, Math.min(at.x, window.innerWidth - r.width - 4)), top: Math.max(4, Math.min(at.y, window.innerHeight - r.height - 4)) });
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', esc);
    return () => document.removeEventListener('keydown', esc);
  }, [at.x, at.y, onClose]);
  return createPortal(
    <div className="scrim clear" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }} onContextMenu={(e) => { e.preventDefault(); onClose(); }}>
      <div ref={ref} className="menu dropdown wl-ctx" role="menu" dir={lang === 'fa' ? 'rtl' : 'ltr'} style={{ position: 'fixed', ...pos, minWidth: width }}>{children}</div>
    </div>,
    document.body,
  );
}

/** A symbol's right-click menu (TradingView's and the old Pro-Chart's, together). */
function RowMenu({ at, many, flagged, onNewSection, onClose, onDone }: { at: { symbol: string; x: number; y: number }; many: string[] | null; flagged: boolean; onNewSection(syms: string[]): void; onClose(): void; onDone(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const wl = useWatchlist();
  const inst = useMarket((s) => s.instruments[at.symbol]);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const s = at.symbol;
  const targets = many ?? [s];
  const run = (fn: () => void) => () => { fn(); onClose(); };
  const sections = wl.lists.find((l) => l.id === wl.active)?.sections ?? [];
  const inSection = sections.find((x) => x.symbols.includes(s))?.id ?? null;
  const name = (lang === 'fa' ? inst?.nameFa : inst?.nameEn) ?? '';
  const flagNow = many ? null : wl.flags[s];
  return (
    <Menu at={at} onClose={onClose}>
      <div className="wl-ctx-head">
        {many ? <span className="fa">{L(`${faNum(many.length)} نماد`, `${many.length} symbols`)}</span> : <><SymbolLogo symbol={s} size={20} /><b className="sym">{s}</b><span className={`muted${lang === 'fa' ? ' fa' : ''}`}>{name}</span></>}
      </div>
      <div className="menu-row flags">
        {FLAG_COLORS.map((c, i) => <button key={c} className={`wl-flag big${flagNow === i ? ' on' : ''}`} style={{ background: c }} title={lang === 'fa' ? FLAG_NAMES[i]!.fa : FLAG_NAMES[i]!.en} aria-label={`flag ${i + 1}`} onClick={run(() => { wl.flagMany(targets, !many && flagNow === i ? null : i); onDone(); })} />)}
        <button className="wl-flag big none" title={L('بدون پرچم', 'No flag')} aria-label={L('بدون پرچم', 'No flag')} onClick={run(() => { wl.flagMany(targets, null); onDone(); })}><Icon name="ph-x" size="sm" /></button>
      </div>
      {!many ? (
        <>
          <button className="menu-item" onClick={run(() => setSymbol(s))}><Icon name="ph-chart-line-up" size="sm" /><span className="grow">{L('باز کردن چارت', 'Open chart')}</span></button>
          <button className="menu-item" onClick={run(() => useCompare.getState().add(s))}><Icon name="ph-plus" size="sm" /><span className="grow">{L(`افزودن ${s} به مقایسه`, `Add ${s} to compare`)}</span></button>
          <button className="menu-item" onClick={run(() => openAlertDialog({ symbol: s, price: useMarket.getState().tickers[s]?.last }))}><Icon name="ph-bell" size="sm" /><span className="grow">{L(`هشدار روی ${s}…`, `Add alert on ${s}…`)}</span></button>
          <button className="menu-item" onClick={run(() => { setSymbol(s); usePanels.getState().show('order'); })}><Icon name="ph-shopping-cart" size="sm" /><span className="grow">{L(`معامله‌ی ${s}…`, `Trade ${s}…`)}</span></button>
          <button className="menu-item" onClick={run(() => { const n = window.prompt(L(`یادداشت برای ${s}`, `Note for ${s}`), wl.notes[s] ?? ''); if (n != null) wl.note(s, n); })}><Icon name="ph-note" size="sm" /><span className="grow">{wl.notes[s] ? L('ویرایش یادداشت…', 'Edit note…') : L(`افزودن یادداشت برای ${s}…`, `Add note for ${s}…`)}</span></button>
        </>
      ) : null}
      {!flagged ? (
        <>
          <div className="menu-sep" />
          {sections.length ? <div className="menu-title fa">{L('انتقال به بخش', 'Move to section')}</div> : null}
          {sections.length ? (
            <button className="menu-item" disabled={!many && inSection == null} onClick={run(() => { wl.move(targets, null, null); onDone(); })}><span className="grow">{L('بیرون از بخش‌ها', 'No section')}</span></button>
          ) : null}
          {sections.map((x) => (
            <button key={x.id} className="menu-item" disabled={!many && inSection === x.id} onClick={run(() => { wl.move(targets, null, x.id); onDone(); })}><Icon name="ph-rows" size="sm" /><span className="grow">{x.name}</span></button>
          ))}
          <button className="menu-item" onClick={run(() => { onNewSection(targets); onDone(); })}><Icon name="ph-list-plus" size="sm" /><span className="grow">{L('افزودن به بخش تازه…', 'Add to new section…')}</span></button>
        </>
      ) : null}
      {wl.lists.length > 1 ? (
        <>
          <div className="menu-sep" />
          <div className="menu-title fa">{L('افزودن به فهرست', 'Add to watchlist')}</div>
          {wl.lists.filter((l) => l.id !== wl.active).map((l) => (
            <button key={l.id} className="menu-item" onClick={run(() => targets.forEach((x) => wl.add(x, l.id)))}><span className="grow">{l.name}</span></button>
          ))}
        </>
      ) : null}
      <div className="menu-sep" />
      {!many ? <button className="menu-item" onClick={run(() => copyText(s))}><Icon name="ph-copy" size="sm" /><span className="grow">{L('کپی نماد', 'Copy symbol')}</span></button> : null}
      {!flagged ? <button className="menu-item danger" onClick={run(() => { wl.removeMany(targets); onDone(); })}><Icon name="ph-trash" size="sm" /><span className="grow">{many ? L(`حذف ${faNum(many.length)} نماد`, `Remove ${many.length} symbols`) : L('حذف از فهرست', 'Remove from watchlist')}</span><kbd>Del</kbd></button> : null}
    </Menu>
  );
}

function SectionMenu({ at, onClose }: { at: { id: string; x: number; y: number }; onClose(): void }) {
  const L = useL();
  const wl = useWatchlist();
  const sec = wl.lists.find((l) => l.id === wl.active)?.sections?.find((x) => x.id === at.id);
  if (!sec) return null;
  const run = (fn: () => void) => () => { fn(); onClose(); };
  return (
    <Menu at={at} onClose={onClose} width={220}>
      <button className="menu-item" onClick={run(() => { const n = window.prompt(L('نام بخش', 'Section name'), sec.name); if (n) wl.renameSection(sec.id, n); })}><Icon name="ph-pencil-simple" size="sm" /><span className="grow">{L('تغییر نام بخش', 'Rename section')}</span></button>
      <button className="menu-item" onClick={run(() => wl.toggleSection(sec.id))}><Icon name="ph-caret-down" size="sm" /><span className="grow">{sec.collapsed ? L('باز کردن', 'Expand') : L('بستن', 'Collapse')}</span></button>
      <button className="menu-item" onClick={run(() => openAlertDialog({ list: wl.active, symbols: sec.symbols }))}><Icon name="ph-bell" size="sm" /><span className="grow">{L('هشدار روی این بخش…', 'Add alert on the section…')}</span></button>
      <div className="menu-sep" />
      <button className="menu-item danger" onClick={run(() => wl.deleteSection(sec.id))}><Icon name="ph-trash" size="sm" /><span className="grow">{L('حذف بخش (نمادها می‌مانند)', 'Remove section (keep symbols)')}</span></button>
    </Menu>
  );
}
