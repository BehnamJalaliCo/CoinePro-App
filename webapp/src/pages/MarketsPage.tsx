import { useEffect, useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { usePage } from '../state/page';
import { useWatchlist } from '../state/watchlist';
import { useSession } from '../data/session';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf, marketOf } from '../data/symbols';
import { cryptoNews, type NewsItem } from '../data/intel';
import { activeSignals, type Signal } from '../data/signals';
import { faAgo, faNum } from '../i18n/time';
import { Tabs, pct, useL } from './kit';
import { HubSwitch } from './MarketsHub';
import { Icon } from '../ui/Icon';
import { PreviewButton, usePreviewPress } from './MarketPreview';
import { Sheet } from './M2Sheet';
import {
  CHANGE_BANDS, NO_FILTER, PULSE_CELLS, STORY_LIMIT, TURNOVER_FLOORS, TYPE_NAMES, applyFilter, filterCount, filterIsEmpty, headlinesOf, hottest,
  pulseHasFigure, pulseOf, pulseValue, signalCounts, signalSummary, turnoverOf, typesOf, venuesOf, type MarketFilter, type PulseCell,
} from './marketsLib';
import type { Market, Ticker } from '../data/types';
import '../shell/markets2.css';

// The app's Markets hub (feature/search MarketsScreen): the day's pulse (market cap, 24h volume,
// Bitcoin's share, fear and greed — each a door to why it reads what it reads), the headlines,
// Explore's doors, pages Top · Popular · Movers · Volume · Forex · Metals, lenses (all, starred,
// «داغ», gainers, losers), the filter sheet (type, venue, change band, turnover floor), a preview
// on every row (held, right-clicked or its eye), the open signals, and Explore's top stories.
type PageKey = 'top' | 'popular' | 'movers' | 'volume' | 'forex' | 'metals';
type Lens = 'all' | 'favourites' | 'hot' | 'gainers' | 'losers';
const POPULAR = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'DOGEUSDT', 'ADAUSDT', 'TRXUSDT', 'TONUSDT', 'LTCUSDT', 'XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY'];
const LENSES: { id: Lens; fa: string; en: string }[] = [
  { id: 'all', fa: 'همه‌ی بازارها', en: 'All markets' },
  { id: 'favourites', fa: 'دنبال‌شده‌ها', en: 'Starred' },
  { id: 'hot', fa: 'داغ', en: 'Hot' },
  { id: 'gainers', fa: 'بیشترین رشد', en: 'Gainers' },
  { id: 'losers', fa: 'بیشترین افت', en: 'Losers' },
];

const turnover = (tk: Ticker) => turnoverOf(tk) ?? 0;

export default function MarketsPage() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const tickers = useMarket((s) => s.tickers);
  const open = usePage((s) => s.open);
  const watch = useWatchlist((s) => s.symbols);
  const signed = useSession((s) => Boolean(s.sessions.tradeyar || s.sessions.coineprofx));
  const [page, setPage] = useState<PageKey>('top');
  const [lens, setLens] = useState<Lens>('all');
  const [filter, setFilter] = useState<MarketFilter>(NO_FILTER);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [pulseCell, setPulseCell] = useState<PulseCell | null>(null);
  const [q, setQ] = useState('');
  const [news, setNews] = useState<{ items: NewsItem[]; loading: boolean; failed: boolean }>({ items: [], loading: true, failed: false });
  const [newsTry, setNewsTry] = useState(0);
  const [signals, setSignals] = useState<Signal[]>([]);

  // The newsroom the news page already reads; nothing here is polled.
  useEffect(() => {
    let alive = true;
    setNews((n) => ({ ...n, loading: true, failed: false }));
    cryptoNews(30).then((items) => { if (alive) setNews({ items, loading: false, failed: false }); }).catch(() => { if (alive) setNews({ items: [], loading: false, failed: true }); });
    return () => { alive = false; };
  }, [newsTry]);
  useEffect(() => {
    if (!signed) { setSignals([]); return; }
    let alive = true;
    activeSignals().then((s) => { if (alive) setSignals(s); }).catch(() => undefined);
    return () => { alive = false; };
  }, [signed]);

  const all = useMemo(() => Object.values(tickers).filter((x) => hasLogo(x.symbol)), [tickers]);
  // The pulse is the crypto table's (the venue's own book): the one the day's figures come from.
  const pulse = useMemo(() => pulseOf(all.filter((x) => marketOf(x.symbol) === 'crypto')), [all]);
  const headlines = useMemo(() => headlinesOf(news.items), [news.items]);
  const counts = useMemo(() => signalCounts(signals), [signals]);

  const visible = useMemo(() => {
    let list = all;
    if (page === 'popular') list = POPULAR.map((s) => tickers[s]).filter((x): x is Ticker => Boolean(x));
    if (page === 'forex') list = list.filter((x) => marketOf(x.symbol) === 'forex');
    if (page === 'metals') list = list.filter((x) => ['metals', 'energy', 'indices'].includes(marketOf(x.symbol)));
    return list;
  }, [all, tickers, page]);

  const rows = useMemo(() => {
    let list = visible;
    // The lens picks the rows, then the order; a row with no figure is dropped by a lens defined by one.
    if (lens === 'favourites') list = watch.map((s) => list.find((x) => x.symbol === s)).filter((x): x is Ticker => Boolean(x));
    if (lens === 'hot') list = hottest(list);
    if (lens === 'gainers') list = list.filter((x) => x.changePct > 0).sort((a, b) => b.changePct - a.changePct);
    if (lens === 'losers') list = list.filter((x) => x.changePct < 0).sort((a, b) => a.changePct - b.changePct);
    if (lens === 'all' && page !== 'popular') {
      const by = page === 'movers' ? (x: Ticker) => Math.abs(x.changePct) : turnover;
      list = [...list].sort((a, b) => by(b) - by(a));
    }
    list = applyFilter(list, filter);
    const needle = q.trim().toUpperCase();
    if (needle) list = list.filter((x) => x.symbol.includes(needle) || (instrumentOf(x.symbol).nameFa ?? '').includes(q.trim()));
    return list.slice(0, 300);
  }, [visible, lens, page, filter, q, watch]);

  const types = useMemo(() => typesOf(visible), [visible]);
  const venues = useMemo(() => venuesOf(visible), [visible]);
  const nFilters = filterCount(filter);

  return (
    <div className="page-inner">
      <HubSwitch current="markets" />
      {pulseHasFigure(pulse) ? (
        <div className="mk-pulse">
          {PULSE_CELLS.map((c) => {
            const v = pulseValue(c.id, pulse);
            return (
              <button key={c.id} type="button" className="mk-cell" data-cell={c.id} onClick={() => setPulseCell(c.id)}>
                <span className="lab"><span className="fa">{L(c.fa, c.en)}</span><Icon name="ph-info" size="sm" /></span>
                {/* A dash, never a zero: zero is a claim that the market is worth nothing. */}
                <b className={`num${v == null ? ' none' : ''}`}>{v ?? '—'}</b>
              </button>
            );
          })}
        </div>
      ) : null}
      {headlines.length ? (
        <div className="mk-headlines" role="list" aria-label={L('تیترها', 'Headlines')}>
          {headlines.map((h) => (
            <button key={h.id} type="button" role="listitem" className="mk-headline" onClick={() => open('news', h.id)}>
              {h.important ? <span className="imp fa">{L('مهم', 'Important')}</span> : null}
              <span dir="auto">{h.title}</span>
            </button>
          ))}
        </div>
      ) : null}
      <div className="mk-doors">
        <button className="link-btn fa" onClick={() => open('news')}>{L('اخبار', 'News')}</button>
        <button className="link-btn fa" onClick={() => open('calendar')}>{L('تقویم', 'Calendar')}</button>
        <button className="link-btn fa" onClick={() => open('heatmap')}>{L('نقشه حرارتی', 'Heat map')}</button>
      </div>
      <Tabs value={page} onChange={setPage} tabs={[
        { id: 'top', label: t('mkTop') }, { id: 'popular', label: t('mkPopular') }, { id: 'movers', label: t('mkMovers') },
        { id: 'volume', label: t('mkVolume') }, { id: 'forex', label: t('forex') }, { id: 'metals', label: t('mkMetalsMore') },
      ]} />
      <div className="row market-filters">
        <Tabs value={lens} onChange={setLens} tabs={LENSES.map((x) => ({ id: x.id, label: L(x.fa, x.en) }))} />
        <button type="button" className="mk-filter-btn" onClick={() => setFiltersOpen(true)} aria-label={L('فیلتر بازارها', 'Filter markets')}>
          <Icon name="ph-funnel" size="sm" /><span className="fa">{L('فیلتر', 'Filter')}</span>{nFilters ? <span className="badge num">{nFilters}</span> : null}
        </button>
        <input className="scr-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search')} dir="auto" />
      </div>
      {rows.length ? (
        <table className="table mk-table">
          <thead><tr><th>{t('symbol')}</th><th /><th className="r">{t('last')}</th><th className="r">{t('changePct')}</th><th className="r">{t('mkTurnover')}</th><th className="r">{t('dayRange')}</th><th /></tr></thead>
          <tbody>
            {rows.map((x) => <MarketRow key={x.symbol} tk={x} fa={fa} signals={counts[x.symbol] ?? 0} watched={watch.includes(x.symbol)} />)}
          </tbody>
        </table>
      ) : (
        <div className="empty fa">{!filterIsEmpty(filter) ? L('بازاری با این فیلتر پیدا نشد.', 'No market matches this filter.')
          : lens === 'favourites' ? L('هنوز بازاری به دیده‌بان اضافه نکرده‌اید.', 'You have not added a market to the watchlist yet.')
          : lens !== 'all' ? L('امروز هیچ بازاری در این فهرست نیست.', 'No market qualifies for this list today.') : t('nothingFound')}</div>
      )}
      {/* Only when there is something to say: «۰ سیگنال باز» is chrome reporting the absence of news. */}
      {signals.length ? (
        <button type="button" className="mk-strip" onClick={() => open('signals')}>
          <Icon name="ph-lightning" size="md" />
          <span className="grow"><b className="fa">{fa ? `${faNum(signals.length)} سیگنال باز` : `${signals.length} open signal${signals.length === 1 ? '' : 's'}`}</b><span className="muted">{signalSummary(signals, !fa)}</span></span>
          <Icon name={fa ? 'ph-caret-left' : 'ph-caret-right'} size="sm" />
        </button>
      ) : null}
      <section className="mk-stories">
        <div className="mk-stories-head">
          <h3 className="fa">{L('سرخط خبرها', 'Top stories')}</h3>
          <button className="link-btn fa" onClick={() => open('news')}>{L('همه‌ی اخبار', 'All news')}</button>
        </div>
        {news.items.length ? news.items.slice(0, STORY_LIMIT).map((n) => (
          <button key={n.id} type="button" className="mk-story" onClick={() => open('news', n.slug ?? n.id)}>
            <span className="muted"><span dir="ltr">{n.source}</span> · <span className={fa ? 'fa' : ''}>{fa ? faAgo(n.time) : new Date(n.time).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</span></span>
            <b dir="auto">{n.title}</b>
          </button>
        )) : news.loading ? <div className="empty fa">{t('loading')}…</div> : (
          <div className="empty fa">{L('هنوز خبری منتشر نشده است.', 'Nothing has been published yet.')} <button className="link" onClick={() => setNewsTry((n) => n + 1)}>{L('تلاش دوباره', 'Try again')}</button></div>
        )}
      </section>
      {filtersOpen ? <FilterSheet filter={filter} types={types} venues={venues} onChange={setFilter} onClose={() => setFiltersOpen(false)} /> : null}
      {pulseCell ? <PulseSheet cell={pulseCell} breadth={pulse.breadth} value={pulseValue(pulseCell, pulse)} onClose={() => setPulseCell(null)} /> : null}
    </div>
  );
}

function MarketRow({ tk: x, fa, signals, watched }: { tk: Ticker; fa: boolean; signals: number; watched: boolean }) {
  const L = useL();
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const press = usePreviewPress(x.symbol);
  const inst = instrumentOf(x.symbol, x.last);
  const range = x.high != null && x.low != null && x.high > x.low ? (x.last - x.low) / (x.high - x.low) : null;
  const vol = turnover(x);
  const toggle = () => { const w = useWatchlist.getState(); if (watched) w.remove(x.symbol); else w.add(x.symbol); };
  return (
    <tr className="click" data-symbol={x.symbol} onClick={() => { setSymbol(x.symbol); close(); }} {...press}>
      <td><span className="row"><SymbolLogo symbol={x.symbol} size={20} /><b className="sym">{x.symbol}</b>
        {signals ? <span className="mk-sig num" title={fa ? `${faNum(signals)} سیگنال باز` : `${signals} open signals`}><Icon name="ph-lightning" size="sm" />{signals}</span> : null}</span></td>
      <td className={`muted ${fa ? 'fa' : ''}`}>{fa ? inst.nameFa : inst.nameEn}</td>
      <td className="r num">{formatPrice(x.last, inst.digits)}</td>
      <td className={`r num ${x.changePct >= 0 ? 'up' : 'down'}`}>{pct(x.changePct)}</td>
      <td className="r num">{vol ? formatVolume(vol) : '—'}</td>
      <td className="r">{range != null ? <span className="range-bar"><i style={{ left: `${range * 100}%` }} /></span> : null}</td>
      <td className="r nowrap">
        <PreviewButton symbol={x.symbol} />
        <button className="lt" title={watched ? L('حذف از دیده‌بان', 'Remove from watchlist') : L('افزودن به دیده‌بان', 'Add to watchlist')} aria-pressed={watched} onClick={(e) => { e.stopPropagation(); toggle(); }}><Icon name={watched ? 'ph-star-fill' : 'ph-star'} size="sm" /></button>
      </td>
    </tr>
  );
}

/**
 * The filter sheet (F2): what kind of market, who quotes it, how far it moved, how much changed
 * hands. A block with one value in it is not offered (a chip that cannot change anything); a second
 * tap on a chosen band or floor clears it; «پاک کردن همه» appears only once there is something to clear.
 */
function FilterSheet({ filter, types, venues, onChange, onClose }: { filter: MarketFilter; types: Market[]; venues: string[]; onChange(f: MarketFilter): void; onClose(): void }) {
  const L = useL();
  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  return (
    <Sheet title={L('فیلتر بازارها', 'Filter markets')} onClose={onClose} className="mk-filter-sheet">
      {types.length > 1 ? (
        <div className="m2-section"><span className="label fa">{L('نوع بازار', 'Market type')}</span>
          <div className="m2-chips">{types.map((m) => <button key={m} type="button" data-type={m} className={`chip${filter.types.includes(m) ? ' on' : ''}`} aria-pressed={filter.types.includes(m)} onClick={() => onChange({ ...filter, types: toggle(filter.types, m) })}><span className="fa">{L(TYPE_NAMES[m].fa, TYPE_NAMES[m].en)}</span></button>)}</div></div>
      ) : null}
      {venues.length > 1 ? (
        <div className="m2-section"><span className="label fa">{L('محل معامله', 'Traded on')}</span>
          <div className="m2-chips">{venues.map((v) => <button key={v} type="button" data-venue={v} className={`chip${filter.venues.includes(v) ? ' on' : ''}`} aria-pressed={filter.venues.includes(v)} onClick={() => onChange({ ...filter, venues: toggle(filter.venues, v) })}><span dir="ltr">{v}</span></button>)}</div></div>
      ) : null}
      <div className="m2-section"><span className="label fa">{L('تغییر ۲۴ ساعت', '24h change')}</span>
        <div className="m2-chips">{CHANGE_BANDS.map((b) => <button key={b.id} type="button" data-band={b.id} className={`chip${filter.change === b.id ? ' on' : ''}`} aria-pressed={filter.change === b.id} onClick={() => onChange({ ...filter, change: filter.change === b.id ? null : b.id })}><span className="fa">{L(b.fa, b.en)}</span></button>)}</div></div>
      <div className="m2-section"><span className="label fa">{L('ارزش معاملات', 'Turnover')}</span>
        <div className="m2-chips">{TURNOVER_FLOORS.map((f) => <button key={f.id} type="button" data-floor={f.id} className={`chip${filter.turnover === f.id ? ' on' : ''}`} aria-pressed={filter.turnover === f.id} onClick={() => onChange({ ...filter, turnover: filter.turnover === f.id ? null : f.id })}><span className="fa">{L(f.fa, f.en)}</span></button>)}</div></div>
      {!filterIsEmpty(filter) ? <div className="m2-section"><div className="m2-chips"><button type="button" className="chip mk-clear" onClick={() => onChange(NO_FILTER)}><span className="fa">{L('پاک کردن همه', 'Clear all')}</span></button></div></div> : null}
    </Sheet>
  );
}

/** Why a pulse cell reads what it reads — and under the mood, what the app can measure of it. */
function PulseSheet({ cell, breadth, value, onClose }: { cell: PulseCell; breadth: number | null; value: string | null; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const c = PULSE_CELLS.find((x) => x.id === cell)!;
  return (
    <Sheet title={L(c.fa, c.en)} onClose={onClose} className="mk-pulse-sheet">
      <div className="m2-section">
        {value == null ? <b className="fa">{L('هنوز در دسترس نیست', 'Not available yet')}</b> : null}
        <p className="m2-note fa" style={{ fontSize: 14, color: 'var(--text-2)' }}>{L(c.whyFa, c.whyEn)}</p>
        {cell === 'mood' && breadth != null ? <b className="mk-breadth">{fa ? <><span className="num">{breadth}%</span> <span className="fa">بازار صعودی است</span></> : `${breadth}% of the board is up`}</b> : null}
      </div>
    </Sheet>
  );
}
