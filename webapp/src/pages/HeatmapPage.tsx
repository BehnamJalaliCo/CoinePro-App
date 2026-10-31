import { useEffect, useMemo, useRef, useState } from 'react';
import { useMarket } from '../state/market';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { hasLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { formatVolume } from '../chart/format';
import { pct, useL } from './kit';
import { HubSwitch } from './MarketsHub';
import { Sheet } from './M2Sheet';
import { useHold } from './MarketPreview';
import { useWatchlist } from '../state/watchlist';
import { pacedBars } from '../data/pacer';
import { instrumentOf } from '../data/symbols';
import { faNum } from '../i18n/time';
import type { PageProps } from './registry';
import type { Candle, Ticker } from '../data/types';
import { chartable, deadTile, isChartable, type Chartable } from '../data/chartable';
import {
  DEFAULT_OPTIONS, UNKNOWN_FILL, anyValueFor, anyWeightFor, assetOf, bucketKey, colourFor, coverageText, fmtAmount, fmtPercent, fmtPrice, hex,
  labelInkFor, labelOf, plan, prettyOf, rankOf, selectAssets, tileFigure, valueOf, scaleFor,
  type Bucket, type HeatmapAsset, type HeatmapColour, type HeatmapOptions, type HeatmapPeriod,
} from './heatmapLib';
import '../shell/markets2.css';

// The app's heat map (feature/heatmap, 5.26): tiles sized by the liquidity ranking, volume, turnover
// or equally; coloured by the day's change, a 7/30/90-day performance, volatility against the
// market's own normal, the place in the day's range or the opening gap, on one diverging ramp
// (classic, colour-blind or monochrome); blocks by class or quote currency, a block's name strip
// focusing it; a density cap; the scale and a coverage line saying how much of the map is real. A
// market nobody has read is hatched — never the colour of one that did not move. A tap opens the
// chart; a hold (or right click) opens the tile's figures. The choices are kept per browser.
const OPTIONS_KEY = 'pc.heatmap.options.v2';
const readOptions = (): HeatmapOptions => { try { return { ...DEFAULT_OPTIONS, ...(JSON.parse(localStorage.getItem(OPTIONS_KEY) ?? '{}') as Partial<HeatmapOptions>) }; } catch { return DEFAULT_OPTIONS; } };
const GROUP_HEADER = 32;
/** Daily bars per market (the OLD HeatmapBarSource LIMIT), and how many markets are read at all. */
const BAR_LIMIT = 120;
const RESOLUTION_BUDGET = 220;
const CONCURRENCY = 4;

// The daily bars, read once per session per market through the shared bulk pacer.
const dailyBars = new Map<string, Candle[]>();
const asked = new Set<string>();

export default function HeatmapPage({ param = null }: Partial<PageProps> = {}) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  // `wl:<id>` (the watchlist's "Open in heat map"): only that list's symbols, every market.
  const listId = param?.startsWith('wl:') ? param.slice(3) : null;
  const list = useWatchlist((s) => (listId ? s.lists.find((l) => l.id === listId) ?? null : null));
  const [onlyList, setOnlyList] = useState(Boolean(listId));
  const tickers = useMarket((s) => s.tickers);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const close = usePage((s) => s.close);
  const [options, setOptionsState] = useState<HeatmapOptions>(readOptions);
  const [focus, setFocus] = useState<Bucket | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [detail, setDetail] = useState<string | null>(null);
  const box = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 1000, h: 600 });
  const [hover, setHover] = useState<Ticker | null>(null);
  const [scope, setScope] = useState<Chartable | null>(null);
  const [version, setVersion] = useState(0);
  const [resolving, setResolving] = useState(false);
  const setOptions = (o: HeatmapOptions) => {
    // A cut changed under a drill-down would leave the map focused on a block that no longer exists.
    if (o.grouping !== options.grouping) setFocus(null);
    setOptionsState(o);
    try { localStorage.setItem(OPTIONS_KEY, JSON.stringify(o)); } catch { /* not kept */ }
  };
  // Only markets that can become a chart (OLD PlatformCapabilities.chartableSymbols).
  useEffect(() => { let on = true; void chartable().then((c) => { if (on) setScope(c); }); return () => { on = false; }; }, []);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const universe = useMemo(() => {
    const mine = onlyList && list ? new Set(list.symbols) : null;
    return Object.values(tickers).filter((x) => hasLogo(x.symbol) && isChartable(x.symbol, scope) && !deadTile(x) && (!mine || mine.has(x.symbol)));
  }, [tickers, onlyList, list, scope]);

  // Over the universe ranked by liquidity, not over the tiles already drawn; republished per market,
  // so the map fills in rather than appearing all at once after a minute.
  const universeKey = universe.map((x) => x.symbol).sort().join(',');
  useEffect(() => {
    const pending = universe.map((x) => x.symbol).sort((a, b) => rankOf(a) - rankOf(b)).slice(0, RESOLUTION_BUDGET).filter((s) => !asked.has(s));
    if (!pending.length) return;
    let alive = true;
    pending.forEach((s) => asked.add(s));
    setResolving(true);
    let i = 0;
    const worker = async () => {
      while (alive && i < pending.length) {
        const s = pending[i++]!;
        const bars = await pacedBars(s, '1D');
        dailyBars.set(s, bars.slice(-BAR_LIMIT));
        if (alive) setVersion((v) => v + 1);
      }
    };
    void Promise.all(Array.from({ length: CONCURRENCY }, worker)).then(() => { if (alive) setResolving(false); });
    // Leaving stops the pass; what was not read is asked again next time.
    return () => { alive = false; for (const s of pending.slice(i)) asked.delete(s); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [universeKey]);

  const assets = useMemo(() => universe.map((tk) => assetOf(tk.symbol, tk, dailyBars.get(tk.symbol) ?? [], options.period, dailyBars.has(tk.symbol))).filter((a): a is HeatmapAsset => a != null),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [universe, options.period, version]);
  const drawn = useMemo(() => selectAssets(assets, options, focus), [assets, options, focus]);
  const scale = useMemo(() => scaleFor(drawn.map((a) => valueOf(a, options.colour)).filter((v): v is number => v != null), options.colour), [drawn, options.colour]);
  const known = drawn.filter((a) => valueOf(a, options.colour) != null).length;
  const map = useMemo(() => plan(assets, options, size.w, size.h, { mirrored: fa, headerHeight: GROUP_HEADER, focus }), [assets, options, size, fa, focus]);
  const coverage = drawn.length ? coverageText(known, drawn.length, resolving, true, fa, faNum) : null;
  const bucketName = (b: Bucket) => (b.kind === 'class' ? L(CLASS_NAMES[b.market].fa, CLASS_NAMES[b.market].en) : b.kind === 'quote' ? b.currency : L('سایر', 'Other'));
  const detailAsset = detail ? assets.find((a) => a.symbol === detail) ?? null : null;
  const openChart = (s: string) => { setSymbol(s); close(); };

  return (
    <div className="page-inner heat">
      <HubSwitch current="heatmap" />
      <div className="hm-top">
        <div className="grow">
          <h2 className="fa">{focus ? bucketName(focus) : L('نقشه حرارتی بازار', 'Market heatmap')}</h2>
          {/* Both counts in Persian digits: prose about how many things there are. */}
          <span className="muted fa hm-subtitle">{fa ? `${faNum(drawn.length)} از ${faNum(assets.length)} بازار` : `${drawn.length} of ${assets.length} markets`}</span>
        </div>
        {list ? <button className={`chip wl-filter${onlyList ? ' on' : ''}`} aria-pressed={onlyList} onClick={() => setOnlyList(!onlyList)}><span className="fa">{L('فهرست', 'List')}: {list.name}</span></button> : null}
        {hover ? <span className="num muted">{hover.symbol} · {pct(hover.changePct)} · {formatVolume((hover.volume ?? 0) * hover.last)}</span> : null}
        {focus ? <button className="ib hm-unfocus" title={L('بازگشت به کل نقشه', 'Back to the whole map')} aria-label={L('بازگشت به کل نقشه', 'Back to the whole map')} onClick={() => setFocus(null)}><Icon name="ph-x" size="md" /></button> : null}
        <button className="ib hm-settings" title={L('تنظیمات نقشه', 'Map settings')} aria-label={L('تنظیمات نقشه', 'Map settings')} onClick={() => setSheetOpen(true)}><Icon name="ph-gear" size="md" /></button>
      </div>
      {coverage ? <p className="hm-coverage fa">{coverage}</p> : null}
      <div className="heat-box" ref={box} aria-label={L('نقشه‌ی بازارها. روی هر خانه بزنید تا چارتش باز شود؛ نگه دارید تا جزئیاتش را ببینید.', 'Market map. Tap a tile for its chart; press and hold for its detail.')}>
        {!drawn.length ? <div className="empty fa">{L('بازاری برای نمایش روی نقشه نیست.', 'There are no markets to map.')} <span className="muted">{L('پس از دریافت قیمت‌ها، هر بازار یک خانه می‌گیرد.', 'Every market gets a tile once prices arrive.')}</span></div> : null}
        {map.groups.map((g) => g.header && g.bucket ? (
          <button key={`h${bucketKey(g.bucket)}`} type="button" className="hm-group fa" data-bucket={bucketKey(g.bucket)} style={{ left: g.header.x, top: g.header.y, width: g.header.w, height: g.header.h }}
            title={L('روی نوار نام هر گروه بزنید تا فقط همان گروه را ببینید.', 'Tap a group’s name strip to see that group on its own.')} onClick={() => setFocus(g.bucket)}>
            {bucketName(g.bucket)}
          </button>
        ) : null)}
        {map.groups.flatMap((g) => g.tiles).map((tile) => (
          <Tile key={tile.asset.symbol} tile={tile} colour={options.colour} onOpen={() => openChart(tile.asset.symbol)} onDetail={() => setDetail(tile.asset.symbol)} onHover={() => setHover(tickers[tile.asset.symbol] ?? null)} />
        ))}
      </div>
      {drawn.length ? <Legend scale={scale} colour={options.colour} palette={options.palette} /> : null}
      {sheetOpen ? <SettingsSheet options={options} scale={scale} assets={drawn} onOptions={setOptions} onClose={() => setSheetOpen(false)} /> : null}
      {detailAsset ? <DetailSheet asset={detailAsset} period={options.period} onOpenChart={() => { setDetail(null); openChart(detailAsset.symbol); }} onClose={() => setDetail(null)} /> : null}
    </div>
  );
}

const CLASS_NAMES = {
  forex: { fa: 'فارکس', en: 'Forex' }, crypto: { fa: 'کریپتو', en: 'Crypto' }, metals: { fa: 'فلزات', en: 'Metals' },
  indices: { fa: 'شاخص‌ها', en: 'Indices' }, energy: { fa: 'انرژی', en: 'Energy' },
} as const;
const COLOUR_NAMES: Record<HeatmapColour, { fa: string; en: string }> = {
  change: { fa: 'تغییر روز', en: 'Day change' }, performance: { fa: 'بازدهی دوره', en: 'Performance' }, volatility: { fa: 'نوسان', en: 'Volatility' },
  range: { fa: 'جای قیمت در دامنه', en: 'Place in range' }, gap: { fa: 'شکاف بازگشایی', en: 'Opening gap' },
};
const PERIOD_NAMES: Record<HeatmapPeriod, { fa: string; en: string }> = { week: { fa: '۷ روز', en: '7 days' }, month: { fa: '۳۰ روز', en: '30 days' }, quarter: { fa: '۹۰ روز', en: '90 days' } };

/** One tile: the fill, a hatch where there is nothing behind it, and the label only where it fits whole. */
function Tile({ tile, colour, onOpen, onDetail, onHover }: { tile: ReturnType<typeof plan>['groups'][number]['tiles'][number]; colour: HeatmapColour; onOpen(): void; onDetail(): void; onHover(): void }) {
  const hold = useHold(onDetail);
  const { rect: r, asset, value, fill } = tile;
  if (r.w <= 0 || r.h <= 0) return null;
  const label = labelOf(asset.symbol);
  const figure = tileFigure(value, colour);
  const boxW = r.w - 6, boxH = r.h - 6;
  // A clipped label is worse than none: half a ticker reads as a different ticker.
  const showLabel = boxW >= Math.max(26, label.length * 7.2) && boxH >= 14;
  const showFigure = showLabel && boxW >= figure.length * 6.6 && boxH >= 28;
  const bg = fill == null ? UNKNOWN_FILL : hex(fill);
  const ink = fill == null ? '#f0f1f2' : labelInkFor(fill);
  return (
    <button type="button" className={`tile${fill == null ? ' unknown' : ''}`} data-symbol={asset.symbol} data-value={value ?? ''} style={{ left: r.x, top: r.y, width: r.w, height: r.h, background: bg, color: ink }}
      onMouseEnter={onHover} onClick={onOpen} title={`${asset.symbol} ${figure}`} {...hold}>
      {showLabel ? <b className="sym" dir="ltr">{label}</b> : null}
      {showFigure ? <span className="num" dir="ltr">{figure}</span> : null}
    </button>
  );
}

/** The ramp itself, the hatch labelled «بدون داده», and the ends written the way a tile at that end is. */
function Legend({ scale, colour, palette }: { scale: number; colour: HeatmapColour; palette: HeatmapOptions['palette'] }) {
  const L = useL();
  const steps = Array.from({ length: 24 }, (_, i) => colourFor(((i + 0.5) / 24) * 2 - 1, 1, palette));
  return (
    <div className="hm-legend">
      <div className="hm-legend-row"><span className="fa">{L(COLOUR_NAMES[colour].fa, COLOUR_NAMES[colour].en)}</span><span className="hm-legend-unknown"><i className="hm-swatch" style={{ backgroundColor: UNKNOWN_FILL }} /><span className="fa">{L('بدون داده', 'No data')}</span></span></div>
      <div className="hm-bar" aria-hidden>{steps.map((c, i) => <i key={i} style={{ background: hex(c) }} />)}</div>
      {/* In the reader's direction, like the bar: in Persian the losses sit on the reading edge. */}
      <div className="hm-legend-row hm-legend-ends"><span className="num" dir="ltr">{tileFigure(-scale, colour)}</span><span className="num">0</span><span className="num" dir="ltr">{tileFigure(scale, colour)}</span></div>
    </div>
  );
}

function Strip<K extends string>({ value, options, onChange, name }: { value: K; options: { id: K; label: string }[]; onChange(k: K): void; name: string }) {
  return (
    <div className="m2-chips" role="radiogroup" aria-label={name}>
      {options.map((o) => <button key={o.id} type="button" role="radio" aria-checked={value === o.id} data-opt={o.id} className={`chip${value === o.id ? ' on' : ''}`} onClick={() => onChange(o.id)}><span className="fa">{o.label}</span></button>)}
    </div>
  );
}

/**
 * The six choices that decide what the map means. Each strip says when nothing can answer it
 * rather than sitting there changing nothing; the scale is in the header unconditionally.
 */
function SettingsSheet({ options: o, scale, assets, onOptions, onClose }: { options: HeatmapOptions; scale: number; assets: HeatmapAsset[]; onOptions(o: HeatmapOptions): void; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const scaleText = scale.toLocaleString('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const note = (faText: string, enText: string) => <p className="m2-note fa">{L(faText, enText)}</p>;
  return (
    <Sheet title={L('تنظیمات نقشه', 'Map settings')} subtitle={fa ? `دامنه رنگ تا ±${scaleText}` : `Colour scale ±${scaleText}`} onClose={onClose} className="hm-settings-sheet">
      <div className="m2-section" data-section="colour"><span className="label fa">{L('رنگ بر پایه', 'Colour by')}</span>
        <Strip name="colour" value={o.colour} onChange={(colour) => onOptions({ ...o, colour })} options={(Object.keys(COLOUR_NAMES) as HeatmapColour[]).map((id) => ({ id, label: L(COLOUR_NAMES[id].fa, COLOUR_NAMES[id].en) }))} />
        {!anyValueFor(assets, o.colour) ? note('هنوز هیچ بازاری این عدد را ندارد؛ خانه‌ها هاشورخورده‌اند تا با «بدون تغییر» اشتباه نشوند.', 'No market can answer this yet. Tiles are hatched so they cannot be read as unchanged.') : null}
      </div>
      {o.colour === 'performance' ? (
        <div className="m2-section" data-section="period"><span className="label fa">{L('دوره بازدهی', 'Performance window')}</span>
          <Strip name="period" value={o.period} onChange={(period) => onOptions({ ...o, period })} options={(Object.keys(PERIOD_NAMES) as HeatmapPeriod[]).map((id) => ({ id, label: L(PERIOD_NAMES[id].fa, PERIOD_NAMES[id].en) }))} />
        </div>
      ) : null}
      <div className="m2-section" data-section="size"><span className="label fa">{L('اندازه خانه‌ها', 'Tile size')}</span>
        <Strip name="size" value={o.size} onChange={(size) => onOptions({ ...o, size })} options={[
          { id: 'liquidity', label: L('نقدشوندگی', 'Liquidity') }, { id: 'volume', label: L('حجم', 'Volume') }, { id: 'turnover', label: L('گردش', 'Turnover') }, { id: 'mono', label: L('یکسان', 'Equal') }]} />
        {!anyWeightFor(assets, o.size) ? note('این فید حجمی نمی‌فرستد، پس اندازه‌ها به رتبه‌بندی نقدشوندگی برمی‌گردند.', 'This feed reports no volume, so tiles fall back to the liquidity ranking.')
          : o.size === 'liquidity' ? note('اندازه بر پایه‌ی رتبه‌بندی نقدشوندگی است، نه ارزش بازار.', 'Sized by liquidity ranking, not market capitalisation.') : null}
      </div>
      <div className="m2-section" data-section="density"><span className="label fa">{L('چگالی نقشه', 'Map density')}</span>
        <Strip name="density" value={o.density} onChange={(density) => onOptions({ ...o, density })} options={[
          { id: 'focused', label: L('درشت', 'Large') }, { id: 'standard', label: L('متعادل', 'Balanced') }, { id: 'everything', label: L('همه', 'All') }]} />
        {note('نقشه پرحجم‌ترین بازارها را می‌کشد؛ بقیه با گروه‌بندی و ورود به گروه در دسترس‌اند.', 'The map draws the heaviest markets; the rest are reached by grouping and focusing.')}
      </div>
      <div className="m2-section" data-section="grouping"><span className="label fa">{L('گروه‌بندی', 'Grouping')}</span>
        <Strip name="grouping" value={o.grouping} onChange={(grouping) => onOptions({ ...o, grouping })} options={[
          { id: 'none', label: L('بدون گروه', 'None') }, { id: 'class', label: L('بر پایه دسته', 'By class') }, { id: 'quote', label: L('بر پایه ارز مظنه', 'By quote currency') }]} />
        {o.grouping !== 'none' ? note('روی نوار نام هر گروه بزنید تا فقط همان گروه را ببینید.', 'Tap a group’s name strip to see that group on its own.') : null}
      </div>
      <div className="m2-section" data-section="palette"><span className="label fa">{L('پالت رنگ', 'Palette')}</span>
        <Strip name="palette" value={o.palette} onChange={(palette) => onOptions({ ...o, palette })} options={[
          { id: 'classic', label: L('کلاسیک', 'Classic') }, { id: 'blind', label: L('مناسب کوررنگی', 'Colour-blind') }, { id: 'mono', label: L('تک‌رنگ', 'Monochrome') }]} />
        {o.palette === 'blind' ? note('پالت مناسب کوررنگی از محور سبز و قرمز استفاده نمی‌کند؛ رشد آبی و افت نارنجی است.', 'The colour-blind palette avoids the red-green axis: gains are blue, losses orange.') : null}
      </div>
    </Sheet>
  );
}

/** Every figure the map could have coloured by, at once; an absent one is a dash, and an unread market says so. */
function DetailSheet({ asset: a, period, onOpenChart, onClose }: { asset: HeatmapAsset; period: HeatmapPeriod; onOpenChart(): void; onClose(): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  const inst = instrumentOf(a.symbol, a.price);
  const tone = (v: number | null) => (v == null || !Number.isFinite(v) || v === 0 ? '' : v > 0 ? 'up' : 'down');
  const gap = valueOf(a, 'gap');
  const row = (label: string, value: string, cls = '', key?: string) => <div className="hm-figure" data-figure={key}><span className="muted fa">{label}</span><span className={`num ${cls}`} dir="ltr">{value}</span></div>;
  return (
    <Sheet title={prettyOf(a.symbol)} subtitle={fa ? inst.nameFa : inst.nameEn} onClose={onClose} className="hm-detail-sheet">
      {!a.resolved ? <p className="m2-note fa hm-unresolved">{L('کندل روزانه این بازار هنوز خوانده نشده است.', 'This market’s daily candles have not been read yet.')}</p> : null}
      <div className="hm-figures">
        {row(L('قیمت', 'Price'), fmtPrice(a.price), '', 'price')}
        {row(L('تغییر روز', 'Day change'), fmtPercent(a.changePercent), tone(a.changePercent), 'change')}
        {row(fa ? `بازدهی ${PERIOD_NAMES[period].fa}` : `${PERIOD_NAMES[period].en} performance`, fmtPercent(a.periodPercent), tone(a.periodPercent), 'period')}
        {row(L('شکاف بازگشایی', 'Opening gap'), fmtPercent(gap), tone(gap), 'gap')}
        {row(L('بازگشایی', 'Open'), fmtPrice(a.openPrice), '', 'open')}
        {row(L('بسته‌شدن پیشین', 'Previous close'), fmtPrice(a.previousClose), '', 'previous')}
        {row(L('سقف روز', 'Day high'), fmtPrice(a.dayHigh), '', 'high')}
        {row(L('کف روز', 'Day low'), fmtPrice(a.dayLow), '', 'low')}
        {row(L('جای قیمت در دامنه', 'Place in range'), tileFigure(valueOf(a, 'range'), 'range'), '', 'range')}
        {row(L('نوسان روز', 'Day range'), fmtPercent(a.volatilityPercent), '', 'volatility')}
        {row(L('نوسان معمول', 'Typical range'), fmtPercent(a.typicalVolatilityPercent), '', 'typical')}
        {row(L('حجم روز', 'Day volume'), fmtAmount(a.volume), '', 'volume')}
        {row(L('گردش روز', 'Day turnover'), fmtAmount(a.turnover), '', 'turnover')}
        {/* Only where the market has one: a «—» on every pair teaches the reader to stop reading. */}
        {a.fundingRatePercent != null ? row(L('نرخ فاندینگ', 'Funding rate'), fmtPercent(a.fundingRatePercent), tone(a.fundingRatePercent), 'funding') : null}
      </div>
      <button type="button" className="btn btn-primary hm-open" onClick={onOpenChart}><span className="fa">{L('نمایش چارت', 'Open chart')}</span></button>
    </Sheet>
  );
}

