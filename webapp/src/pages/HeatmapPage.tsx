import { useEffect, useMemo, useRef, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { hasLogo } from '../ui/SymbolLogo';
import { marketOf } from '../data/symbols';
import { formatVolume } from '../chart/format';
import { Tabs, pct, useL } from './kit';
import { useWatchlist } from '../state/watchlist';
import type { PageProps } from './registry';
import type { Ticker } from '../data/types';

// The app's heat map (feature/heatmap): tiles sized by turnover (or equal), coloured by the day's
// change or range, grouped by market; colour-blind and monochrome palettes. Squarified layout.
type ColourBy = 'change' | 'range';
type SizeBy = 'turnover' | 'equal';
type Palette = 'classic' | 'blind' | 'mono';
interface Rect { x: number; y: number; w: number; h: number; tk: Ticker }

function squarify(items: { v: number; tk: Ticker }[], x: number, y: number, w: number, h: number): Rect[] {
  const out: Rect[] = [];
  let rest = items.filter((i) => i.v > 0);
  while (rest.length) {
    const total = rest.reduce((n, i) => n + i.v, 0);
    const horizontal = w >= h;
    const side = horizontal ? h : w;
    let row: typeof rest = [];
    let worst = Infinity;
    for (const it of rest) {
      const next = [...row, it];
      const s = next.reduce((n, i) => n + i.v, 0);
      const len = (s / total) * (horizontal ? w : h);
      const ratios = next.map((i) => { const other = (i.v / s) * side; return Math.max(len / other, other / len); });
      const r = Math.max(...ratios);
      if (r > worst) break;
      worst = r; row = next;
    }
    const s = row.reduce((n, i) => n + i.v, 0);
    const len = (s / total) * (horizontal ? w : h);
    let off = 0;
    for (const it of row) {
      const size = (it.v / s) * side;
      out.push(horizontal ? { x, y: y + off, w: len, h: size, tk: it.tk } : { x: x + off, y, w: size, h: len, tk: it.tk });
      off += size;
    }
    if (horizontal) { x += len; w -= len; } else { y += len; h -= len; }
    rest = rest.slice(row.length);
  }
  return out;
}

function colour(v: number, palette: Palette, span: number): string {
  const k = Math.max(-1, Math.min(1, v / span));
  if (palette === 'mono') { const l = 50 - k * 22; return `hsl(220 8% ${l}%)`; }
  const [up, down] = palette === 'blind' ? [[33, 150, 243], [255, 152, 0]] : [[8, 153, 129], [242, 54, 69]];
  const c = k >= 0 ? up : down;
  const a = 0.25 + Math.abs(k) * 0.75;
  return `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${a})`;
}

export default function HeatmapPage({ param = null }: Partial<PageProps> = {}) {
  const t = useT();
  const L = useL();
  // `wl:<id>` (the watchlist's "Open in heat map"): only that list's symbols, every market.
  const listId = param?.startsWith('wl:') ? param.slice(3) : null;
  const list = useWatchlist((s) => (listId ? s.lists.find((l) => l.id === listId) ?? null : null));
  const [onlyList, setOnlyList] = useState(Boolean(listId));
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const [group, setGroup] = useState<'crypto' | 'fx'>('crypto');
  const [colourBy, setColourBy] = useState<ColourBy>('change');
  const [sizeBy, setSizeBy] = useState<SizeBy>('turnover');
  const [palette, setPalette] = useState<Palette>('classic');
  const [count, setCount] = useState(100);
  const box = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 1000, h: 600 });
  const [hover, setHover] = useState<Ticker | null>(null);

  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const rects = useMemo(() => {
    const mine = onlyList && list ? new Set(list.symbols) : null;
    const items0 = Object.values(tickers).filter((x) => hasLogo(x.symbol) && (mine ? mine.has(x.symbol) : group === 'crypto' ? marketOf(x.symbol) === 'crypto' : marketOf(x.symbol) !== 'crypto'));
    const val = (x: Ticker) => (sizeBy === 'equal' ? 1 : Math.max(1, (x.volume ?? 0) * (marketOf(x.symbol) === 'crypto' ? x.last : 1)));
    const items = items0.map((tk) => ({ v: val(tk), tk })).sort((a, b) => b.v - a.v).slice(0, count);
    return squarify(items, 0, 0, size.w, size.h);
  }, [tickers, group, sizeBy, count, size, onlyList, list]);

  const metric = (x: Ticker) => (colourBy === 'change' ? x.changePct : x.high && x.low ? ((x.high - x.low) / x.low) * 100 : 0);
  const span = colourBy === 'change' ? (group === 'crypto' ? 6 : 1) : group === 'crypto' ? 12 : 2;

  return (
    <div className="page-inner heat">
      <div className="row">
        <Tabs value={group} onChange={(g) => { setGroup(g); setOnlyList(false); }} tabs={[{ id: 'crypto', label: t('crypto') }, { id: 'fx', label: t('forex') }]} />
        {list ? <button className={`chip wl-filter${onlyList ? ' on' : ''}`} aria-pressed={onlyList} onClick={() => setOnlyList(!onlyList)}><span className="fa">{L('فهرست', 'List')}: {list.name}</span></button> : null}
        <select value={colourBy} onChange={(e) => setColourBy(e.target.value as ColourBy)}><option value="change">{t('changePct')}</option><option value="range">{t('dayRange')}</option></select>
        <select value={sizeBy} onChange={(e) => setSizeBy(e.target.value as SizeBy)}><option value="turnover">{t('mkTurnover')}</option><option value="equal">{t('hmEqual')}</option></select>
        <select value={palette} onChange={(e) => setPalette(e.target.value as Palette)}><option value="classic">{t('hmClassic')}</option><option value="blind">{t('hmBlind')}</option><option value="mono">{t('hmMono')}</option></select>
        <select value={count} onChange={(e) => setCount(Number(e.target.value))} className="num"><option value={50}>50</option><option value={100}>100</option><option value={200}>200</option></select>
        {hover ? <span className="num muted">{hover.symbol} · {pct(hover.changePct)} · {formatVolume((hover.volume ?? 0) * hover.last)}</span> : null}
      </div>
      <div className="heat-box" ref={box}>
        {rects.map((r) => {
          const v = metric(r.tk);
          const big = r.w > 60 && r.h > 34;
          return (
            <button key={r.tk.symbol} className="tile" style={{ left: r.x, top: r.y, width: r.w, height: r.h, background: colour(colourBy === 'change' ? v : v - span / 2, palette, span) }}
              onMouseEnter={() => setHover(r.tk)} onClick={() => { setSymbol(r.tk.symbol); close(); }} title={`${r.tk.symbol} ${pct(r.tk.changePct)}`}>
              {big ? <><b className="sym">{r.tk.symbol.replace(/USDT$/, '')}</b><span className="num">{colourBy === 'change' ? pct(v) : `${v.toFixed(1)}%`}</span></> : null}
            </button>
          );
        })}
      </div>
    </div>
  );
}
