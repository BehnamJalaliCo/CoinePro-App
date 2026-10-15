import { useEffect, useMemo, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs, useT } from '../state/prefs';
import { SymbolLogo } from '../ui/SymbolLogo';
import { formatPrice, formatVolume } from '../chart/format';
import { instrumentOf } from '../data/symbols';
import { marketStatus } from '../data/sessions';
import { useQuote } from '../data/quote';
import { barsFor, dailyStats } from '../data/symbolStats';
import { technicalRating, RATING_FA, type TechnicalRating, type RatingKey } from '../engine/techRating.js';
import { intervalLabel } from '../data/intervals';
import { intervalsFor } from '../data/candles';
import type { Candle, Market } from '../data/types';

// Under the watchlist, as TradingView and the old Pro-Chart show it: the name, the price large with
// the day's change and a day's sparkline, the live quote (bid, ask, spread), key facts, the
// technical rating per timeframe, the day's and the year's range, performance from a week to a
// year, and the key stats.
const RATING_EN: Record<RatingKey, string> = { strongBuy: 'Strong buy', buy: 'Buy', neutral: 'Neutral', sell: 'Sell', strongSell: 'Strong sell' };

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

function Gauge({ r, fa }: { r: TechnicalRating; fa: boolean }) {
  // −1 … 1 on a half circle, five segments from strong sell to strong buy.
  const angle = Math.PI * (1 - (r.overall + 1) / 2);
  const cx = 80; const cy = 74; const R = 60;
  const seg = (i: number) => {
    const a0 = Math.PI * (1 - i / 5) - 0.03; const a1 = Math.PI * (1 - (i + 1) / 5) + 0.03;
    return `M${cx + R * Math.cos(a0)},${cy - R * Math.sin(a0)} A${R},${R} 0 0 1 ${cx + R * Math.cos(a1)},${cy - R * Math.sin(a1)}`;
  };
  const colors = ['#F23645', '#F0616D', '#787B86', '#22C55E', '#089981'];
  return (
    <div className="gauge">
      <svg viewBox="0 0 160 84" aria-hidden>
        {colors.map((c, i) => <path key={c} d={seg(i)} stroke={c} strokeWidth="8" fill="none" strokeLinecap="round" />)}
        <line x1={cx} y1={cy} x2={cx + (R - 14) * Math.cos(angle)} y2={cy - (R - 14) * Math.sin(angle)} stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
        <circle cx={cx} cy={cy} r="4" fill="currentColor" />
      </svg>
      <b className={fa ? 'fa' : ''} style={{ color: RATING_FA[r.label].color }}>{fa ? RATING_FA[r.label].label : RATING_EN[r.label]}</b>
      <div className="gauge-counts num">
        <span className="down">{r.sell}</span><span className="muted">{r.neutral}</span><span className="up">{r.buy}</span>
      </div>
      <div className="gauge-parts">
        <span className={fa ? 'fa' : ''}>{fa ? 'میانگین‌ها' : 'Moving averages'}: <b style={{ color: RATING_FA[r.maLabel].color }}>{fa ? RATING_FA[r.maLabel].label : RATING_EN[r.maLabel]}</b></span>
        <span className={fa ? 'fa' : ''}>{fa ? 'نوسان‌گرها' : 'Oscillators'}: <b style={{ color: RATING_FA[r.oscLabel].color }}>{fa ? RATING_FA[r.oscLabel].label : RATING_EN[r.oscLabel]}</b></span>
      </div>
    </div>
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

export function SymbolDetails() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const symbol = usePrefs((s) => s.symbol);
  const chartInterval = usePrefs((s) => s.interval);
  const tk = useMarket((s) => s.tickers[symbol]);
  const inst = useMarket((s) => s.instruments[symbol]) ?? instrumentOf(symbol, tk?.last);
  const digits = inst.digits;
  const name = (fa ? inst.nameFa : inst.nameEn) ?? symbol;
  const tone = tk ? (tk.change >= 0 ? 'up' : 'down') : '';
  const open = marketStatus(inst.market);
  const quote = useQuote(symbol);
  const L = (f: string, e: string) => (fa ? f : e);

  const [daily, setDaily] = useState<Candle[]>([]);
  const [intraday, setIntraday] = useState<Candle[]>([]);
  const ratingTfs = useMemo(() => ['15', '60', '240', '1D'].filter((iv) => intervalsFor(symbol).includes(iv)), [symbol]);
  const [ratingTf, setRatingTf] = useState<string>('60');
  const [rating, setRating] = useState<TechnicalRating | null>(null);

  useEffect(() => {
    let alive = true;
    setDaily([]); setIntraday([]);
    void barsFor(symbol, '1D').then((b) => { if (alive) setDaily(b); });
    void barsFor(symbol, intervalsFor(symbol).includes('15') ? '15' : '60').then((b) => { if (alive) setIntraday(b.slice(-96)); });
    return () => { alive = false; };
  }, [symbol]);
  useEffect(() => { if (ratingTfs.includes(chartInterval)) setRatingTf(chartInterval); }, [chartInterval, ratingTfs]);
  useEffect(() => {
    let alive = true;
    setRating(null);
    const tf = ratingTfs.includes(ratingTf) ? ratingTf : ratingTfs[0];
    if (!tf) return;
    void barsFor(symbol, tf).then((b) => { if (alive) setRating(technicalRating(b)); });
    return () => { alive = false; };
  }, [symbol, ratingTf, ratingTfs]);

  const stats = useMemo(() => dailyStats(daily, tk?.last ?? null), [daily, tk?.last]);
  const prevClose = tk?.prevClose ?? stats.prevClose;
  const dayLo = tk?.low != null ? Math.min(tk.low, tk.last) : null;
  const dayHi = tk?.high != null ? Math.max(tk.high, tk.last) : null;

  return (
    <section className="details" aria-label={symbol}>
      <div className="details-head">
        <SymbolLogo symbol={symbol} size={28} />
        <div className="details-names">
          <span className="sym details-sym">{symbol}</span>
          <span className={`details-name${fa ? ' fa' : ''}`}>{name}</span>
        </div>
      </div>
      <div className="details-price">
        <span className="num details-last">{tk ? formatPrice(tk.last, digits) : '—'}</span>
        <span className="details-quote num">{inst.quote}</span>
      </div>
      {tk ? (
        <div className={`details-change num ${tone}`}>
          {tk.change >= 0 ? '+' : '−'}{formatPrice(Math.abs(tk.change), digits)}{'  '}
          {tk.changePct >= 0 ? '+' : '−'}{Math.abs(tk.changePct).toFixed(2)}%
        </div>
      ) : null}
      <div className={`details-status ${open ? 'open' : 'closed'}`}>
        <span className="dot" /><span className="fa">{t(open ? 'marketOpen' : 'marketClosed')}</span>
      </div>
      <Sparkline bars={intraday} up={(tk?.change ?? 0) >= 0} />

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

      <div className="details-facts">
        <h3 className="details-title fa">{L('حقایق کلیدی', 'Key facts')}</h3>
        <p className={fa ? 'fa' : ''}>{factsFor(inst.market, name, fa)}</p>
      </div>

      <h3 className="details-title fa">{L('امتیاز تکنیکال', 'Technical rating')}</h3>
      <div className="chips flat rating-tfs">
        {ratingTfs.map((iv) => <button key={iv} className={`chip${ratingTf === iv ? ' on' : ''}`} onClick={() => setRatingTf(iv)}><span className="num">{intervalLabel(iv)}</span></button>)}
      </div>
      {rating ? <Gauge r={rating} fa={fa} /> : <div className="muted small fa">{t('loading')}…</div>}

      {dayLo != null && dayHi != null && tk ? <RangeBar title={t('dayRange')} lo={dayLo} hi={dayHi} at={tk.last} digits={digits} /> : null}
      {stats.lo52 != null && stats.hi52 != null && tk ? <RangeBar title={L('بازه‌ی ۵۲ هفته', '52-week range')} lo={stats.lo52} hi={stats.hi52} at={tk.last} digits={digits} /> : null}

      {stats.perf.length ? (
        <>
          <h3 className="details-title fa">{L('عملکرد', 'Performance')}</h3>
          <div className="perf-grid">
            {stats.perf.map((p) => (
              <div key={p.id} className={`perf ${p.pct == null ? '' : p.pct >= 0 ? 'up' : 'down'}`}>
                <b className="num">{p.pct == null ? '—' : `${p.pct >= 0 ? '+' : '−'}${Math.abs(p.pct).toFixed(2)}%`}</b><span className="num">{p.id}</span>
              </div>
            ))}
          </div>
        </>
      ) : null}

      <h3 className="details-title fa">{t('keyStats')}</h3>
      <dl className="details-stats">
        {prevClose != null ? (<><dt className="fa">{t('prevClose')}</dt><dd className="num">{formatPrice(prevClose, digits)}</dd></>) : null}
        {stats.open != null ? (<><dt className="fa">{L('باز شدن روز', 'Open')}</dt><dd className="num">{formatPrice(stats.open, digits)}</dd></>) : null}
        {tk?.volume != null ? (<><dt className="fa">{t('volume24h')}</dt><dd className="num">{formatVolume(tk.volume)}</dd></>) : null}
        {stats.avgVolume30 != null ? (<><dt className="fa">{L('میانگین حجم (۳۰ روز)', 'Average volume (30D)')}</dt><dd className="num">{formatVolume(stats.avgVolume30)}</dd></>) : null}
      </dl>
    </section>
  );
}
