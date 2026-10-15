import { useEffect, useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { usePanels } from '../state/panels';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { useFlash, useNow } from '../ui/useFlash';
import { formatPrice, formatVolume } from '../chart/format';
import { backendOf, instrumentOf } from '../data/symbols';
import { marketStatus } from '../data/sessions';
import { countdown, contractSize, nextSessionChange, sessionText } from '../data/sessionClock';
import { useQuote } from '../data/quote';
import { barsFor, dailyStats } from '../data/symbolStats';
import { technicals, type TechRow } from '../data/technicals';
import { technicalRating, RATING_FA, type TechnicalRating, type RatingKey } from '../engine/techRating.js';
import { intervalLabel } from '../data/intervals';
import { intervalsFor } from '../data/candles';
import type { Candle, Market } from '../data/types';
import { cryptoNews, forexNews, type NewsItem } from '../data/intel';
import { community, listOf, readPost, type Post } from '../data/community';
import { usePage } from '../state/page';
import { faAgo, faNum } from '../i18n/time';
import { useL } from '../pages/kit';
import './panels-x.css';

// The latest stories, fetched once a visit for the details card.
let newsCache: Partial<Record<'crypto' | 'forex', Promise<NewsItem[]>>> = {};
const newsFor = (kind: 'crypto' | 'forex') => (newsCache[kind] ??= (kind === 'crypto' ? cryptoNews(50) : forexNews()).catch(() => { newsCache = {}; return []; }));

// Under the watchlist, as TradingView and the old Pro-Chart show it: the name with the instrument
// type and venue, the price large (flashing on each tick) with the day's change, the market's
// session and its countdown, the last update, a day's sparkline, the live quote, the news, key
// facts and an About card, performance from a day to a year, TradingView's Technicals (the gauge
// per timeframe, the counts per group and every indicator's value and vote), the day's and the
// year's range, the key stats with ATR, and the community's posts on the symbol.
const RATING_EN: Record<RatingKey, string> = { strongBuy: 'Strong buy', buy: 'Buy', neutral: 'Neutral', sell: 'Sell', strongSell: 'Strong sell' };
const TYPE: Record<Market, { fa: string; en: string }> = {
  crypto: { fa: 'رمزارز', en: 'Crypto' }, forex: { fa: 'فارکس', en: 'Forex' }, metals: { fa: 'فلزات', en: 'Commodity' },
  indices: { fa: 'شاخص', en: 'Index' }, energy: { fa: 'انرژی', en: 'Commodity' },
};

function factsFor(market: Market, name: string, fa: boolean): string {
  if (!fa) {
    switch (market) {
      case 'crypto': return `${name} is a digital asset traded around the clock, seven days a week, on global crypto markets; it moves a lot and trades deep.`;
      case 'metals': return `${name} is a precious metal and a safe-haven asset; its price moves closely with dollar rates and global risk.`;
      case 'energy': return `${name} is one of the most traded energy contracts; supply and demand, weekly inventories and producers' decisions drive it.`;
      case 'indices': return `${name} tracks the largest companies of its market and stands for that market's overall direction.`;
      default: return `${name} is one of the most traded currency pairs and trades around the clock from Sydney to New York.`;
    }
  }
  switch (market) {
    case 'crypto': return `${name} یک دارایی دیجیتال است که به‌صورت ۲۴ساعته و ۷ روز هفته در بازارهای جهانی رمزارز دادوستد می‌شود؛ نوسان آن بالا و نقدشوندگی‌اش زیاد است.`;
    case 'metals': return `${name} از فلزات گران‌بها و دارایی‌های امن به شمار می‌آید و قیمتش با نرخ بهره‌ی دلار و ریسک جهانی رابطه‌ی نزدیک دارد.`;
    case 'energy': return `${name} از قراردادهای پرمعامله‌ی انرژی است و قیمتش تابع عرضه و تقاضای جهانی، ذخایر هفتگی و تصمیم تولیدکنندگان است.`;
    case 'indices': return `${name} شاخصی از بزرگ‌ترین شرکت‌های بازار مربوطه است و نمایانگر روند کلی آن بازار محسوب می‌شود.`;
    default: return `${name} از پرمعامله‌ترین جفت‌ارزهای بازار فارکس است و به‌صورت ۲۴ساعته از سیدنی تا نیویورک دادوستد می‌شود.`;
  }
}

/** A spread as a share of the price: per cent, or basis points when it is tiny. */
const spreadPct = (r: number) => (r * 100 >= 0.01 ? `${(r * 100).toFixed(3)}%` : `${(r * 10_000).toFixed(2)} bp`);

function Sparkline({ bars, up }: { bars: Candle[]; up: boolean }) {
  if (bars.length < 2) return <div className="spark" />;
  const W = 260; const H = 44;
  const cs = bars.map((b) => b.c);
  const min = Math.min(...cs); const max = Math.max(...cs);
  const pts = cs.map((c, i) => `${(i / (cs.length - 1)) * W},${H - 2 - ((c - min) / (max - min || 1)) * (H - 4)}`).join(' ');
  return (
    <svg className={`spark ${up ? 'up' : 'down'}`} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-hidden>
      <polyline points={`0,${H} ${pts} ${W},${H}`} className="spark-fill" />
      <polyline points={pts} className="spark-line" />
    </svg>
  );
}

const ZONES = ['#F23645', '#F0616D', '#787B86', '#22C55E', '#089981'];

function Gauge({ r, fa }: { r: TechnicalRating; fa: boolean }) {
  // −1 … 1 on a half circle, five zones from strong sell to strong buy, notches between them.
  const angle = Math.PI * (1 - (r.overall + 1) / 2);
  const cx = 90; const cy = 80; const R = 62;
  const seg = (i: number) => {
    const a0 = Math.PI * (1 - i / 5) - 0.035; const a1 = Math.PI * (1 - (i + 1) / 5) + 0.035;
    return `M${cx + R * Math.cos(a0)},${cy - R * Math.sin(a0)} A${R},${R} 0 0 1 ${cx + R * Math.cos(a1)},${cy - R * Math.sin(a1)}`;
  };
  const label = (k: RatingKey) => (fa ? RATING_FA[k].label : RATING_EN[k]);
  return (
    <div className="gauge">
      <svg viewBox="0 0 180 96" aria-hidden>
        {ZONES.map((c, i) => <path key={c} d={seg(i)} stroke={c} strokeWidth="7" fill="none" strokeLinecap="butt" />)}
        {[1, 2, 3, 4].map((i) => { const a = Math.PI * (1 - i / 5); return <line key={i} x1={cx + (R - 12) * Math.cos(a)} y1={cy - (R - 12) * Math.sin(a)} x2={cx + (R - 7) * Math.cos(a)} y2={cy - (R - 7) * Math.sin(a)} stroke="currentColor" strokeOpacity="0.35" strokeWidth="1.2" />; })}
        <line x1={cx} y1={cy} x2={cx + (R - 16) * Math.cos(angle)} y2={cy - (R - 16) * Math.sin(angle)} stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
        <circle cx={cx} cy={cy} r="4.5" fill="currentColor" />
      </svg>
      <div className="gauge-ends"><span className={fa ? 'fa' : ''}>{label('strongSell')}</span><span className={fa ? 'fa' : ''}>{label('strongBuy')}</span></div>
      <b className={fa ? 'fa' : ''} style={{ color: RATING_FA[r.label].color }}>{label(r.label)}</b>
      <div className="gauge-counts">
        <span className="down"><small className="fa">{fa ? 'فروش' : 'Sell'}</small><b className="num">{r.sell}</b></span>
        <span className="muted"><small className="fa">{fa ? 'خنثی' : 'Neutral'}</small><b className="num">{r.neutral}</b></span>
        <span className="up"><small className="fa">{fa ? 'خرید' : 'Buy'}</small><b className="num">{r.buy}</b></span>
      </div>
    </div>
  );
}

function GroupCard({ title, label, sell, neutral, buy, fa }: { title: string; label: RatingKey; sell: number; neutral: number; buy: number; fa: boolean }) {
  return (
    <div className="tech-group">
      <div className="tech-group-head"><span className={fa ? 'fa' : ''}>{title}</span><b className={fa ? 'fa' : ''} style={{ color: RATING_FA[label].color }}>{fa ? RATING_FA[label].label : RATING_EN[label]}</b></div>
      <div className="tech-group-counts">
        <span className="down"><small className="fa">{fa ? 'فروش' : 'Sell'}</small><b className="num">{sell}</b></span>
        <span className="muted"><small className="fa">{fa ? 'خنثی' : 'Neutral'}</small><b className="num">{neutral}</b></span>
        <span className="up"><small className="fa">{fa ? 'خرید' : 'Buy'}</small><b className="num">{buy}</b></span>
      </div>
    </div>
  );
}

function TechTable({ title, rows, fa }: { title: string; rows: TechRow[]; fa: boolean }) {
  const act = (a: number) => (a > 0 ? (fa ? 'خرید' : 'Buy') : a < 0 ? (fa ? 'فروش' : 'Sell') : (fa ? 'خنثی' : 'Neutral'));
  const fmt = (v: number | null) => (v == null ? '—' : Math.abs(v) >= 1000 ? v.toLocaleString('en-US', { maximumFractionDigits: 2 }) : Math.abs(v) >= 1 ? v.toFixed(2) : v.toPrecision(4));
  return (
    <table className="tech-table">
      <thead><tr><th className={fa ? 'fa' : ''}>{title}</th><th className={fa ? 'fa' : ''}>{fa ? 'مقدار' : 'Value'}</th><th className={fa ? 'fa' : ''}>{fa ? 'اقدام' : 'Action'}</th></tr></thead>
      <tbody>{rows.map((r) => (
        <tr key={r.id}><td>{r.name}</td><td className="num">{fmt(r.value)}</td><td className={`${r.action > 0 ? 'up' : r.action < 0 ? 'down' : 'muted'}${fa ? ' fa' : ''}`}>{act(r.action)}</td></tr>
      ))}</tbody>
    </table>
  );
}

function RangeBar({ title, lo, hi, at, digits }: { title: string; lo: number; hi: number; at: number; digits: number }) {
  const p = hi > lo ? Math.max(0, Math.min(1, (at - lo) / (hi - lo))) : 0.5;
  return (
    <div className="details-range">
      <div className="row"><span className="fa">{title}</span></div>
      <div className="bar grad"><span className="fill" style={{ left: `${p * 100}%` }} /></div>
      <div className="row num"><span>{formatPrice(lo, digits)}</span><span>{formatPrice(hi, digits)}</span></div>
    </div>
  );
}

const fmtTime = (msv: number, tz: string) => {
  try { return new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(msv)); } catch { return new Date(msv).toISOString().slice(11, 19); }
};

export function SymbolDetails({ onCompare, onSearch }: { onCompare?(): void; onSearch?(): void } = {}) {
  const t = useT();
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const tz = usePrefs((s) => s.timezone);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const chartInterval = usePrefs((s) => s.interval);
  const folded = usePanels((s) => s.detailsCollapsed);
  const setFolded = usePanels((s) => s.setDetailsCollapsed);
  const tk = useMarket((s) => s.tickers[symbol]);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol, tk?.last);
  const digits = inst.digits;
  const name = (fa ? inst.nameFa : inst.nameEn) ?? symbol;
  const tone = tk ? (tk.change > 0 ? 'up' : tk.change < 0 ? 'down' : '') : '';
  const now = useNow(1000);
  const open = marketStatus(inst.market, new Date(now));
  const next = nextSessionChange(inst.market, now);
  const quote = useQuote(symbol);
  const { ref: priceRef, tick } = useFlash<HTMLSpanElement>(tk?.last);
  // The market the prices come from, as symbol search names it: LBank for crypto, MetaTrader 5 for the rest.
  const venue = backendOf(symbol) === 'tradeyar' ? 'LBank' : 'MetaTrader 5';
  const openPage = usePage((s) => s.open);
  const [copied, setCopied] = useState(false);

  const [daily, setDaily] = useState<Candle[] | null>(null);
  const [intraday, setIntraday] = useState<Candle[]>([]);
  const ratingTfs = useMemo(() => ['5', '15', '60', '240', '1D', '1W'].filter((iv) => intervalsFor(symbol).includes(iv)), [symbol]);
  const [ratingTf, setRatingTf] = useState<string>('60');
  const [rating, setRating] = useState<TechnicalRating | null>(null);
  const [table, setTable] = useState<ReturnType<typeof technicals>>(null);
  const [showTable, setShowTable] = useState(() => { try { return localStorage.getItem('pc.details.tech.v1') === '1'; } catch { return false; } });
  const [posts, setPosts] = useState<Post[]>([]);

  useEffect(() => {
    let alive = true;
    setDaily(null); setIntraday([]);
    void barsFor(symbol, '1D').then((b) => { if (alive) setDaily(b); });
    void barsFor(symbol, intervalsFor(symbol).includes('15') ? '15' : '60').then((b) => { if (alive) setIntraday(b.slice(-96)); });
    return () => { alive = false; };
  }, [symbol]);
  useEffect(() => { if (ratingTfs.includes(chartInterval)) setRatingTf(chartInterval); }, [chartInterval, ratingTfs]);
  useEffect(() => {
    let alive = true;
    setRating(null); setTable(null);
    const tf = ratingTfs.includes(ratingTf) ? ratingTf : ratingTfs[0];
    if (!tf || folded) return;
    void barsFor(symbol, tf).then((b) => { if (alive) { setRating(technicalRating(b)); setTable(technicals(b)); } });
    return () => { alive = false; };
  }, [symbol, ratingTf, ratingTfs, folded]);
  useEffect(() => { try { localStorage.setItem('pc.details.tech.v1', showTable ? '1' : '0'); } catch { /* ignore */ } }, [showTable]);

  const stats = useMemo(() => dailyStats(daily ?? [], tk?.last ?? null), [daily, tk?.last]);
  // The newest story that names this symbol: its code, its base asset or its Persian name.
  const [story, setStory] = useState<NewsItem | null>(null);
  useEffect(() => {
    let alive = true;
    setStory(null);
    const base = symbol.replace(/USDT$|USD$/, '');
    const keys = [symbol, base, inst.nameFa?.split('/')[0]?.trim(), inst.nameEn?.split('/')[0]?.trim()].filter((k): k is string => Boolean(k && k.length >= 3));
    void newsFor(inst.market === 'crypto' ? 'crypto' : 'forex').then((list) => {
      const hit = list.find((n) => keys.some((k) => n.title.includes(k) || n.summary.includes(k)));
      if (alive) setStory(hit ?? null);
    });
    return () => { alive = false; };
  }, [symbol, inst.market, inst.nameFa, inst.nameEn]);
  // The community's posts that name the symbol (its base asset).
  useEffect(() => {
    let alive = true;
    setPosts([]);
    const q = inst.market === 'crypto' ? inst.base : symbol;
    void community<Record<string, unknown>>(`posts/search?q=${encodeURIComponent(q)}`).then((r) => { if (alive) setPosts(listOf(r).map(readPost).filter((p) => p.content).slice(0, 3)); }).catch(() => undefined);
    return () => { alive = false; };
  }, [symbol, inst.market, inst.base]);

  // The day's figures from the daily bar (TradingView's), the 24-hour ticker until it arrives.
  const prevClose = stats.prevClose ?? tk?.prevClose ?? null;
  const dayLo = stats.dayLow ?? (tk?.low != null ? Math.min(tk.low, tk.last) : null);
  const dayHi = stats.dayHigh ?? (tk?.high != null ? Math.max(tk.high, tk.last) : null);
  const chg = tk && prevClose ? tk.last - prevClose : tk?.change ?? null;
  const chgPct = tk && prevClose ? ((tk.last - prevClose) / prevClose) * 100 : tk?.changePct ?? null;
  const copyPrice = () => { if (!tk) return; try { void navigator.clipboard?.writeText(formatPrice(tk.last, digits).replace(/,/g, '')); } catch { /* ignore */ } setCopied(true); setTimeout(() => setCopied(false), 1200); };
  const type = TYPE[inst.market];
  const kind = inst.market === 'crypto' ? L('اسپات', 'Spot') : inst.market === 'forex' ? L('جفت‌ارز', 'Currency pair') : 'CFD';

  const head = (
    <div className="details-head">
      <SymbolLogo symbol={symbol} size={24} />
      <span className="sym details-sym">{symbol}</span>
      <span className="ws-spacer" />
      {onSearch ? <button className="lt" title={L('جستجوی نماد', 'Symbol search')} onClick={onSearch}><Icon name="ph-magnifying-glass" size="sm" /></button> : null}
      {onCompare ? <button className="lt" title={t('compare')} onClick={onCompare}><Icon name="ph-plus" size="sm" /></button> : null}
      <button className="lt" title={L('تنظیمات نماد در چارت', 'Symbol settings')} onClick={() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'symbol' } }))}><Icon name="ph-gear" size="sm" /></button>
      <button className={`lt${copied ? ' on' : ''}`} title={copied ? L('کپی شد', 'Copied') : L('کپی قیمت', 'Copy price')} onClick={copyPrice} disabled={!tk}><Icon name={copied ? 'ph-check' : 'ph-copy'} size="sm" /></button>
      <button className="lt" title={folded ? L('باز کردن جزئیات', 'Show details') : L('بستن جزئیات', 'Hide details')} aria-expanded={!folded} onClick={() => setFolded(!folded)}><Icon name={folded ? 'ph-caret-up' : 'ph-caret-down'} size="sm" /></button>
    </div>
  );
  if (folded) return <section className="details folded" aria-label={symbol}>{head}</section>;

  return (
    <section className="details" aria-label={symbol}>
      {daily === null ? <div className="details-loading" role="progressbar" aria-label={t('loading')} /> : null}
      {head}
      <div className="details-meta">
        <span className={`details-name${fa ? ' fa' : ''}`}>{name}</span>
        <span className="dot-sep">·</span><span className="details-venue">{venue.toUpperCase()}</span>
      </div>
      <div className="details-type"><span className="fa">{kind}</span><span className="dot-sep">·</span><span className="fa">{fa ? type.fa : type.en}</span></div>
      <div className="details-price">
        <span ref={priceRef} className={`num details-last${tick > 0 ? ' tick-up' : tick < 0 ? ' tick-down' : ''}`}>{tk ? formatPrice(tk.last, digits) : '—'}</span>
        <span className="details-quote num">{inst.quote}</span>
      </div>
      {tk && chg != null && chgPct != null ? (
        <div className={`details-change num ${tone}`}>
          {chg > 0 ? '+' : chg < 0 ? '−' : ''}{formatPrice(Math.abs(chg), digits)}{'  '}
          {chgPct > 0 ? '+' : chgPct < 0 ? '−' : ''}{Math.abs(chgPct).toFixed(2)}%
        </div>
      ) : null}
      {!tk ? <div className="muted small fa">{L('هنوز قیمت زنده‌ای نرسیده است.', 'No live price yet.')}</div> : null}
      <div className={`details-status ${open ? 'open' : 'closed'}`}>
        <span className="dot" /><span className="fa">{t(open ? 'marketOpen' : 'marketClosed')}</span>
        {next ? <span className="muted fa details-count">{next.open ? L('بسته می‌شود تا', 'closes in') : L('باز می‌شود تا', 'opens in')} <span className="num">{countdown(next.at - now)}</span></span> : null}
      </div>
      {tk ? <div className="details-updated muted fa">{L('آخرین به‌روزرسانی', 'Last update')} <span className="num">{fmtTime(tk.ts || now, tz)}</span></div> : null}
      <Sparkline bars={intraday} up={(tk?.change ?? 0) >= 0} />
      {tk?.volume != null || stats.avgVolume30 != null ? (
        <div className="details-vol">
          <div className="row"><span className="fa">{L('حجم ۲۴ ساعت', 'Volume')}</span><span className="fa">{L('میانگین حجم (۳۰ روز)', 'Avg. volume (30D)')}</span></div>
          <div className="row num"><b>{tk?.volume != null ? formatVolume(tk.volume) : '—'}</b><b>{stats.avgVolume30 != null ? formatVolume(stats.avgVolume30) : '—'}</b></div>
          {tk?.volume != null && stats.avgVolume30 ? <div className="vol-bar"><i style={{ width: `${Math.min(100, (tk.volume / (tk.volume + stats.avgVolume30)) * 100)}%` }} /></div> : null}
        </div>
      ) : null}

      {quote ? (
        <>
          <h3 className="details-title fa">{L('مظنه', 'Quote')}</h3>
          <dl className="details-stats">
            <dt className="fa">{L('خرید (Bid)', 'Bid')}</dt><dd className="num down">{formatPrice(quote.bid, digits)}</dd>
            <dt className="fa">{L('فروش (Ask)', 'Ask')}</dt><dd className="num up">{formatPrice(quote.ask, digits)}</dd>
            <dt className="fa">{L('اسپرد', 'Spread')}</dt><dd className="num">{formatPrice(quote.spread, digits)} <span className="muted">({spreadPct(quote.spread / quote.ask)})</span></dd>
          </dl>
        </>
      ) : null}

      {story ? (
        <button className="details-news" onClick={() => openPage('news', story.slug ?? null)}>
          <span className="muted small"><b className="fa">{L('اخبار', 'News')}</b> · {fa ? faAgo(story.time) : new Date(story.time).toISOString().slice(0, 16).replace('T', ' ')}</span>
          <b className="fa" dir="auto">{story.title}</b>
          <span className="muted small fa">{L('رویدادهای بیشتر ›', 'More events ›')}</span>
        </button>
      ) : null}

      {stats.perf.length ? (
        <>
          <h3 className="details-title fa">{L('عملکرد', 'Performance')}</h3>
          <div className="perf-grid perf-4">
            {stats.perf.map((p) => (
              <div key={p.id} className={`perf ${p.pct == null || p.pct === 0 ? '' : p.pct > 0 ? 'up' : 'down'}`}>
                <b className="num">{p.pct == null ? '—' : `${p.pct > 0 ? '+' : p.pct < 0 ? '−' : ''}${Math.abs(p.pct).toFixed(2)}%`}</b><span className="num">{p.id}</span>
              </div>
            ))}
          </div>
        </>
      ) : null}

      <h3 className="details-title fa">{L('تحلیل تکنیکال', 'Technicals')}</h3>
      <div className="chips flat rating-tfs">
        {ratingTfs.map((iv) => <button key={iv} className={`chip${ratingTf === iv ? ' on' : ''}`} onClick={() => setRatingTf(iv)}><span className="num">{intervalLabel(iv)}</span></button>)}
      </div>
      {rating ? (
        <>
          <Gauge r={rating} fa={fa} />
          <div className="tech-groups">
            <GroupCard title={L('نوسان‌گرها', 'Oscillators')} label={rating.oscLabel} sell={rating.oscSell} neutral={rating.oscNeutral} buy={rating.oscBuy} fa={fa} />
            <GroupCard title={L('میانگین‌های متحرک', 'Moving averages')} label={rating.maLabel} sell={rating.maSell} neutral={rating.maNeutral} buy={rating.maBuy} fa={fa} />
          </div>
          {table ? <button className="details-more fa" onClick={() => setShowTable(!showTable)}>{showTable ? L('پنهان کردن اندیکاتورها', 'Hide indicators') : L('نمایش همه‌ی اندیکاتورها', 'Show every indicator')} <Icon name={showTable ? 'ph-caret-up' : 'ph-caret-down'} size="sm" /></button> : null}
          {table && showTable ? (
            <div className="tech-tables">
              <TechTable title={L('نوسان‌گر', 'Oscillator')} rows={table.oscillators} fa={fa} />
              <TechTable title={L('میانگین متحرک', 'Moving average')} rows={table.averages} fa={fa} />
            </div>
          ) : null}
        </>
      ) : <div className="muted small fa">{t('loading')}…</div>}

      {dayLo != null && dayHi != null && tk ? <RangeBar title={t('dayRange')} lo={dayLo} hi={dayHi} at={tk.last} digits={digits} /> : null}
      {stats.lo52 != null && stats.hi52 != null && tk ? <RangeBar title={L('بازه‌ی ۵۲ هفته', '52-week range')} lo={stats.lo52} hi={stats.hi52} at={tk.last} digits={digits} /> : null}

      <h3 className="details-title fa">{t('keyStats')}</h3>
      <dl className="details-stats">
        {prevClose != null ? (<><dt className="fa">{stats.prevClose != null ? L('بسته شدن قبلی', 'Previous close') : t('prevClose')}</dt><dd className="num">{formatPrice(prevClose, digits)}</dd></>) : null}
        {stats.open != null ? (<><dt className="fa">{L('باز شدن روز', 'Open')}</dt><dd className="num">{formatPrice(stats.open, digits)}</dd></>) : null}
        {stats.dayVolume != null && stats.dayVolume > 0 ? (<><dt className="fa">{L('حجم امروز', 'Volume (today)')}</dt><dd className="num">{formatVolume(stats.dayVolume)}</dd></>) : null}
        {tk?.volume != null ? (<><dt className="fa">{t('volume24h')}</dt><dd className="num">{formatVolume(tk.volume)}</dd></>) : null}
        {stats.avgVolume30 != null ? (<><dt className="fa">{L('میانگین حجم (۳۰ روز)', 'Average volume (30D)')}</dt><dd className="num">{formatVolume(stats.avgVolume30)}</dd></>) : null}
        {stats.atr != null ? (<><dt className="fa">{L('ATR (۱۴ روز)', 'ATR (14D)')}</dt><dd className="num">{formatPrice(stats.atr, digits)} <span className="muted">({stats.atrPct?.toFixed(2)}%)</span></dd></>) : null}
      </dl>

      <div className="details-facts">
        <h3 className="details-title fa">{L('حقایق کلیدی', 'Key facts')}</h3>
        <p className={fa ? 'fa' : ''}>{factsFor(inst.market, name, fa)}</p>
      </div>

      <h3 className="details-title fa">{L('درباره‌ی نماد', 'About')}</h3>
      <dl className="details-stats about">
        <dt className="fa">{L('نماد', 'Symbol')}</dt><dd className="sym">{symbol}</dd>
        <dt className="fa">{L('نوع', 'Type')}</dt><dd className="fa">{kind} · {fa ? type.fa : type.en}</dd>
        <dt className="fa">{L('دارایی پایه', 'Base')}</dt><dd className="num">{inst.base}</dd>
        <dt className="fa">{L('ارز مظنه', 'Quote currency')}</dt><dd className="num">{inst.quote}</dd>
        <dt className="fa">{L('اندازه‌ی قرارداد', 'Contract size')}</dt><dd className="num">{contractSize(symbol, inst.market, fa)}</dd>
        <dt className="fa">{L('کمترین تیک', 'Minimum tick')}</dt><dd className="num">{(1 / 10 ** digits).toFixed(digits)}</dd>
        <dt className="fa">{L('ساعت معامله', 'Session')}</dt><dd className="fa">{sessionText(inst.market, fa)}</dd>
        <dt className="fa">{L('منبع داده', 'Data source')}</dt><dd>{venue}</dd>
      </dl>

      {posts.length ? (
        <>
          <h3 className="details-title fa">{L('در انجمن', 'Community')}</h3>
          {posts.map((p) => (
            <button key={p.id} className="details-post" onClick={() => openPage('thread', p.id)}>
              <span className="fa clamp2" dir="auto">{p.content}</span>
              <span className="muted small fa">{p.author}{p.createdAt ? ` · ${fa ? faAgo(p.createdAt) : new Date(p.createdAt).toISOString().slice(0, 10)}` : ''}{p.replies ? ` · ${fa ? faNum(p.replies) : p.replies} ${L('پاسخ', 'replies')}` : ''}</span>
            </button>
          ))}
        </>
      ) : null}
      <button className="details-browse fa" onClick={() => openPage('community')}><Icon name="ph-chat-circle" size="sm" />{L('مرور پست‌های انجمن ›', 'Browse community posts ›')}</button>
    </section>
  );
}
