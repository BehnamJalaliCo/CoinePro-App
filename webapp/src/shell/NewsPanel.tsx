import { useEffect, useMemo, useRef, useState } from 'react';
import { usePrefs, useT } from '../state/prefs';
import { useMarket } from '../state/market';
import { articleBody, cryptoNews, forexNews, type NewsItem } from '../data/intel';
import { backendOf, instrumentOf } from '../data/symbols';
import { hasLogo, SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { flashElement, useNow } from '../ui/useFlash';
import { faAgo, faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import type { Instrument } from '../data/types';
import './panels-x.css';

// TradingView's news panel with the old Pro-Chart's extras: the open symbol's stories, its market's
// or everything; category chips with counts; the tickers a story names (a click opens the chart);
// a "new" badge and a flash for what arrived since the panel opened; the important stories marked
// (forex ones too, by their words); a two-line summary; the full date on hover; refreshed every
// minute. A story opens in place with its full Persian text, Esc / ↑ / ↓ inside.
type Cat = 'crypto' | 'forex' | 'metals' | 'indices' | 'energy' | 'macro';
const CATS: { id: Cat; fa: string; en: string; words: RegExp }[] = [
  { id: 'crypto', fa: 'رمزارز', en: 'Crypto', words: /bitcoin|btc|ethereum|crypto|blockchain|stablecoin|altcoin|بیت‌?کوین|اتریوم|رمزارز|ارز دیجیتال|کریپتو|استیبل/i },
  { id: 'forex', fa: 'فارکس', en: 'Forex', words: /forex|dollar|euro|pound|sterling|yen|franc|currency|eur\/?usd|usd\/?jpy|gbp|دلار|یورو|پوند|ین ژاپن|فرانک|فارکس|جفت ارز/i },
  { id: 'metals', fa: 'فلزات', en: 'Metals', words: /gold|silver|platinum|metal|xau|xag|طلا|نقره|پلاتین|فلز/i },
  { id: 'indices', fa: 'شاخص‌ها', en: 'Indices', words: /s&p|nasdaq|dow jones|stocks|equit|index|indices|شاخص|نزدک|داوجونز|سهام|بورس/i },
  { id: 'energy', fa: 'انرژی', en: 'Energy', words: /\boil\b|crude|brent|opec|natural gas|energy|نفت|برنت|اوپک|گاز طبیعی|انرژی/i },
  { id: 'macro', fa: 'کلان و بانک مرکزی', en: 'Macro and central banks', words: /\bfed\b|fomc|ecb|boe|boj|central bank|interest rate|rate (cut|hike)|inflation|cpi|payroll|nfp|gdp|recession|powell|lagarde|فدرال|بانک مرکزی|نرخ بهره|تورم|اشتغال|نان‌?فارم|تولید ناخالص|رکود|پاول|لاگارد/i },
];
const HOT = /\bfed\b|fomc|ecb|boe|boj|rate decision|interest rate|rate (cut|hike)|cpi|inflation|nfp|non-?farm|payrolls|gdp|recession|war\b|sanction|default|crash|surge|plunge|record|halving|etf approv|فدرال|نرخ بهره|تورم|نان‌?فارم|رکود|جنگ|تحریم|سقوط|جهش|رکورد|هاوینگ/i;

/** A story's importance 0–10: the feed's, or the weight of its words (forex stories carry none). */
export function importanceOf(n: NewsItem): number {
  const text = `${n.title} ${n.summary}`;
  const words = (text.match(new RegExp(HOT.source, 'gi')) ?? []).length;
  return Math.max(n.importance, Math.min(10, words ? 5 + words * 2 : 0));
}

export function categoriesOf(n: NewsItem): Cat[] {
  const text = `${n.title} ${n.summary}`;
  const out = CATS.filter((c) => c.words.test(text)).map((c) => c.id);
  if (n.market === 'crypto' && !out.includes('crypto')) out.unshift('crypto');
  return out;
}

const ESC = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const ALIASES: Record<string, string[]> = { XAUUSD: ['طلا', 'gold'], XAGUSD: ['نقره', 'silver'], XTIUSD: ['نفت', 'crude', 'WTI'], US500: ['S&P'], NAS100: ['Nasdaq', 'نزدک'], US30: ['Dow', 'داوجونز'] };

/** The instruments a story names: tickers, base assets, the phone's names, a few aliases. */
export function tickersIn(n: NewsItem, instruments: Instrument[], max = 4): string[] {
  const text = `${n.title} ${n.summary}`;
  const hits: string[] = [];
  for (const i of instruments) {
    if (hits.length >= max) break;
    const keys = [i.symbol, i.market === 'crypto' && i.base.length >= 3 ? i.base : null, i.nameFa?.split('/')[0]?.trim(), i.nameEn?.split('/')[0]?.trim(), ...(ALIASES[i.symbol] ?? [])].filter((k): k is string => Boolean(k && k.length >= 3));
    if (keys.some((k) => (/^[A-Za-z0-9&]+$/.test(k) ? new RegExp(`(^|[^A-Za-z0-9])${ESC(k)}([^A-Za-z0-9]|$)`, i.market === 'crypto' ? '' : 'i').test(text) : text.includes(k)))) hits.push(i.symbol);
  }
  return hits;
}

export function NewsPanel() {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const instrumentsMap = useMarket((s) => s.instruments);
  const tickers = useMarket((s) => s.tickers);
  const [items, setItems] = useState<NewsItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [scope, setScope] = useState<'symbol' | 'market' | 'all'>('market');
  const [cat, setCat] = useState<Cat | null>(null);
  const [source, setSource] = useState('');
  const [open, setOpen] = useState<NewsItem | null>(null);
  const [body, setBody] = useState<string | null>(null);
  const [fresh, setFresh] = useState<Set<string>>(new Set());
  const seen = useRef<Set<string> | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const now = useNow(30_000);
  const market = backendOf(symbol) === 'tradeyar' ? 'crypto' : 'forex';
  const inst = instrumentsMap[symbol] ?? instrumentOf(symbol);

  useEffect(() => {
    let alive = true;
    const load = () => Promise.allSettled([cryptoNews(50), forexNews()]).then((rs) => {
      if (!alive) return;
      const all = rs.flatMap((r) => (r.status === 'fulfilled' ? r.value : [])).sort((a, b) => b.time - a.time);
      setFailed(rs.every((r) => r.status === 'rejected'));
      // What came since the first load is new.
      if (seen.current) { const add = all.filter((n) => !seen.current!.has(n.id)).map((n) => n.id); if (add.length) setFresh((f) => new Set([...f, ...add])); }
      seen.current = new Set(all.map((n) => n.id));
      setItems(all);
    });
    void load();
    const id = setInterval(load, 60_000);
    return () => { alive = false; clearInterval(id); };
  }, []);
  useEffect(() => { for (const id of fresh) flashElement(listRef.current?.querySelector(`[data-id="${CSS.escape(id)}"]`) ?? null, 0, 1200); }, [fresh]);

  useEffect(() => {
    setBody(null);
    if (open) void articleBody(open).then(setBody).catch(() => setBody(null));
  }, [open]);

  // The symbol's own words, for "this symbol" (as the details card finds its story).
  const keys = useMemo(() => [symbol, inst.market === 'crypto' ? inst.base : null, inst.nameFa?.split('/')[0]?.trim(), inst.nameEn?.split('/')[0]?.trim(), ...(ALIASES[symbol] ?? [])].filter((k): k is string => Boolean(k && k.length >= 3)), [symbol, inst]);
  const scoped = useMemo(() => (items ?? []).filter((n) => scope === 'all' || (scope === 'market' ? n.market === market : keys.some((k) => n.title.includes(k) || n.summary.includes(k)))), [items, scope, market, keys]);
  const counts = useMemo(() => { const c: Partial<Record<Cat, number>> = {}; for (const n of scoped) for (const x of categoriesOf(n)) c[x] = (c[x] ?? 0) + 1; return c; }, [scoped]);
  const sources = useMemo(() => [...new Set((items ?? []).map((n) => n.source).filter(Boolean))].sort(), [items]);
  const shown = useMemo(() => scoped.filter((n) => (!cat || categoriesOf(n).includes(cat)) && (!source || n.source === source)), [scoped, cat, source]);
  // Instruments a chip can open: the ones with artwork, the most traded first.
  const universe = useMemo(() => Object.values(instrumentsMap).filter((i) => hasLogo(i.symbol))
    .sort((a, b) => ((tickers[b.symbol]?.volume ?? 0) * (tickers[b.symbol]?.last ?? 0)) - ((tickers[a.symbol]?.volume ?? 0) * (tickers[a.symbol]?.last ?? 0))).slice(0, 160), [instrumentsMap, tickers]);
  const absTime = (msv: number) => new Date(msv).toLocaleString(fa ? 'fa-IR' : 'en-GB', { dateStyle: 'full', timeStyle: 'short' });
  const ago = (msv: number) => (fa ? faAgo(msv, now) : agoEn(msv, now));
  const num = (n: number) => (fa ? faNum(n) : String(n));

  if (open) {
    const i = shown.findIndex((n) => n.id === open.id);
    const go = (d: number) => { const n = shown[i + d]; if (n) setOpen(n); };
    const chips = tickersIn(open, universe, 6);
    return (
      <section className="panel-generic news" tabIndex={-1} ref={(el) => el?.focus()} onKeyDown={(e) => { if (e.key === 'Escape') setOpen(null); if (e.key === 'ArrowDown') { e.preventDefault(); go(1); } if (e.key === 'ArrowUp') { e.preventDefault(); go(-1); } }}>
        <header className="panel-head">
          <button className="ib" onClick={() => setOpen(null)} aria-label={t('back')}><Icon name={fa ? 'ph-caret-right' : 'ph-caret-left'} size="sm" /></button>
          <span className="fa">{t('news')}</span>
          <span className="ws-spacer" />
          <button className="ib" disabled={i <= 0} onClick={() => go(-1)} title={L('خبر قبلی (↑)', 'Previous story (↑)')}><Icon name="ph-caret-up" size="sm" /></button>
          <button className="ib" disabled={i < 0 || i >= shown.length - 1} onClick={() => go(1)} title={L('خبر بعدی (↓)', 'Next story (↓)')}><Icon name="ph-caret-down" size="sm" /></button>
        </header>
        <article className="panel-body article" dir="rtl">
          {open.image ? <img src={open.image} alt="" className="article-img" /> : null}
          <h3>{open.title}</h3>
          <div className="news-meta"><span>{open.source}</span> · <span title={absTime(open.time)}>{ago(open.time)}</span></div>
          {chips.length ? <div className="news-tickers" dir="ltr">{chips.map((s) => <button key={s} className="news-ticker" onClick={() => setSymbol(s)}><SymbolLogo symbol={s} size={14} /><span className="sym">{s}</span></button>)}</div> : null}
          <p className="article-text">{body ?? open.summary}</p>
          {open.url ? <a className="link" href={open.url} target="_blank" rel="noopener noreferrer">{open.source}</a> : null}
        </article>
      </section>
    );
  }

  return (
    <section className="panel-generic news">
      <header className="panel-head">
        <span className="fa">{t('news')}</span><span className="ws-spacer" />
        {sources.length > 1 ? <select className="news-source" value={source} onChange={(e) => setSource(e.target.value)} aria-label={L('منبع', 'Provider')}><option value="">{L('همه‌ی منابع', 'All providers')}</option>{sources.map((x) => <option key={x} value={x}>{x}</option>)}</select> : null}
        {items ? <span className="muted num">{num(shown.length)}</span> : null}
      </header>
      <nav className="chips panel-chips">
        <button className={`chip${scope === 'symbol' ? ' on' : ''}`} onClick={() => setScope('symbol')}><span className="sym">{symbol}</span></button>
        <button className={`chip${scope === 'market' ? ' on' : ''}`} onClick={() => setScope('market')}><span className="fa">{t(market)}</span></button>
        <button className={`chip${scope === 'all' ? ' on' : ''}`} onClick={() => setScope('all')}><span className="fa">{t('all')}</span></button>
      </nav>
      <nav className="chips panel-chips cats">
        {CATS.filter((c) => counts[c.id]).map((c) => (
          <button key={c.id} className={`chip sm${cat === c.id ? ' on' : ''}`} onClick={() => setCat(cat === c.id ? null : c.id)}>
            <span className="fa">{fa ? c.fa : c.en}</span><span className="num count">{num(counts[c.id]!)}</span>
          </button>
        ))}
      </nav>
      <div className="panel-body" ref={listRef}>
        {items === null ? <div className="empty fa">{t('loading')}…</div> : null}
        {items !== null && !shown.length ? <div className="empty fa">{failed ? t('networkError') : t('nothingFound')}</div> : null}
        {shown.map((n) => {
          const hot = importanceOf(n) >= 7;
          const chips = tickersIn(n, universe, 3);
          return (
            <div key={n.id} data-id={n.id} className={`news-row${hot ? ' hot' : ''}${fresh.has(n.id) ? ' fresh' : ''}`} role="button" tabIndex={0}
              onClick={() => { setOpen(n); setFresh((f) => { const x = new Set(f); x.delete(n.id); return x; }); }} onKeyDown={(e) => { if (e.key === 'Enter') setOpen(n); }} dir="rtl">
              <span className="news-title">{fresh.has(n.id) ? <b className="news-new fa">{L('تازه', 'New')}</b> : null}{hot ? <i className="news-hot" title={L('مهم', 'Important')} /> : null}{n.title}</span>
              {n.summary ? <span className="news-sum">{n.summary}</span> : null}
              <span className="news-meta">
                <span>{n.source}</span> · <span title={absTime(n.time)}>{ago(n.time)}</span>
                {chips.length ? <span className="news-tickers" dir="ltr">{chips.map((s) => <button key={s} className="news-ticker" onClick={(e) => { e.stopPropagation(); setSymbol(s); }}><SymbolLogo symbol={s} size={14} /><span className="sym">{s}</span></button>)}</span> : null}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function agoEn(msv: number, now: number): string {
  const s = Math.max(0, Math.round((now - msv) / 1000));
  if (s < 60) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}
