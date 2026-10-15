// The other charts of a multi-chart layout: their own symbol, interval and chart type, volume, the
// main chart's indicators and horizontal lines when mirrored, live bars, and TradingView's sync —
// the crosshair both ways, the visible time, the date range. The top bar drives whichever chart is
// active; a click on a chart makes it the active one.
import { useEffect, useRef, useState } from 'react';
import {
  ColorType, CrosshairMode, HistogramSeries, LineStyle, createChart,
  type IChartApi, type IPriceLine, type ISeriesApi, type MouseEventParams, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { ChartFeed } from './ChartView';
import type { Candle } from '../data/types';
import { isDark, usePrefs } from '../state/prefs';
import { useChartBus } from './chartBus';
import { useChartSettings } from '../state/chartSettings';
import { digitsOf } from '../state/market';
import { formatPrice, formatVolume } from './format';
import { intervalLabel, intervalSeconds } from '../data/intervals';
import { canDraw, intervalsFor } from '../data/candles';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { timeOptions } from './time';
import { buildSeries, createPriceSeries, livePoint, DERIVED, type ChartType, type Palette, type SeriesStyle, type TypeParams } from './chartTypes';
import { StudyRenderer } from './studies';
import { useStudies, VOLUME } from '../state/studies';
import { useGrid, type Cell } from '../state/grid';

// A cell draws its series with the main chart's look (Chart Settings) and its own colours.
function cellStyle(p: Palette): SeriesStyle {
  const s = useChartSettings.getState();
  return {
    up: p.up, down: p.down, line: p.line, bg: p.bg,
    bodyVisible: s.bodyVisible, borderVisible: s.borderVisible, borderUp: s.borderUp, borderDown: s.borderDown,
    wickVisible: s.wickVisible, wickUp: s.wickUp, wickDown: s.wickDown, colorByPrevClose: s.colorByPrevClose,
    thinBars: s.thinBars, hollow: s.hollow, lineWidth: s.lineWidth, areaTop: s.areaTop, areaBottom: s.areaBottom,
  };
}
function cellParams(): TypeParams {
  const s = useChartSettings.getState();
  return {
    renkoMethod: s.renkoMethod, renkoAtr: s.renkoAtr, renkoBox: s.renkoBox, renkoWicks: s.renkoWicks, rangeMethod: s.rangeMethod,
    rangeSize: s.rangeSize, lineBreakCount: s.lineBreakCount, kagiMethod: s.kagiMethod, kagiAtr: s.kagiAtr, kagiReversal: s.kagiReversal,
    pnfMethod: s.pnfMethod, pnfAtr: s.pnfAtr, pnfBox: s.pnfBox, pnfReversal: s.pnfReversal, pnfSource: s.pnfSource,
  };
}
import { isExpression } from '../data/symbols';

// ── The charts of the layout, for the snapshot and the sync bridge ────────────────────────────────
const registry = new Map<number, { api: IChartApi; el: HTMLElement }>();
/** Every extra chart on screen, by its number in the layout (1…7). */
export function liteCharts(): Map<number, { api: IChartApi; el: HTMLElement }> { return registry; }

/** A logical bar index → its time; beyond the last bar, extrapolated by the interval. */
export function logicalToTime(bars: Candle[], logical: number, step: number): number {
  if (!bars.length) return 0;
  const i = Math.round(logical);
  if (i < 0) return bars[0]!.t + i * step;
  if (i >= bars.length) return bars[bars.length - 1]!.t + (i - bars.length + 1) * step;
  return bars[i]!.t;
}

/** A time → the logical index of the bar it falls in; beyond the last bar, by the interval. */
export function timeToLogical(bars: Candle[], t: number, step: number): number {
  if (!bars.length) return 0;
  const last = bars[bars.length - 1]!;
  if (t > last.t) return bars.length - 1 + (t - last.t) / step;
  if (t < bars[0]!.t) return (t - bars[0]!.t) / step;
  let lo = 0; let hi = bars.length - 1;
  while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (bars[mid]!.t <= t) lo = mid; else hi = mid - 1; }
  return lo;
}

const barAt = (bars: Candle[], t: number): Candle | undefined => {
  let lo = 0; let hi = bars.length - 1; let best: Candle | undefined;
  while (lo <= hi) { const mid = (lo + hi) >> 1; if (bars[mid]!.t <= t) { best = bars[mid]; lo = mid + 1; } else hi = mid - 1; }
  return best;
};

function palette(dark: boolean): Palette & { text: string; grid: string; lineSep: string; crossLabel: string } {
  const { up, down } = useChartSettings.getState();
  return dark
    ? { bg: '#0F0F0F', text: '#B8B8B8', lineSep: '#2E2E2E', grid: 'rgba(242,242,242,0.06)', crossLabel: '#4A4A4A', up, down, line: '#2962FF' }
    : { bg: '#FFFFFF', text: '#0F0F0F', lineSep: '#EBEBEB', grid: 'rgba(46,46,46,0.06)', crossLabel: '#131722', up, down, line: '#2962FF' };
}

export function LiteChart({ feed, index, cell, active, onSymbol, onActivate, onMain }: {
  feed: ChartFeed; index: number; cell: Cell; active: boolean;
  onSymbol(): void; onActivate(): void; onMain(): void;
}) {
  const { symbol } = cell;
  const chartType: ChartType = cell.chartType ?? 'candles';
  const box = useRef<HTMLDivElement>(null);
  const host = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const price = useRef<ISeriesApi<SeriesType> | null>(null);
  const vol = useRef<ISeriesApi<'Histogram'> | null>(null);
  const studies = useRef<StudyRenderer | null>(null);
  const bars = useRef<Candle[]>([]);
  const userInput = useRef(0);
  const applying = useRef(false);
  const [last, setLast] = useState<Candle | null>(null);
  const [hover, setHoverBar] = useState<Candle | null>(null);
  const [version, setVersion] = useState(0);
  const theme = usePrefs((s) => s.theme);
  const fa = usePrefs((s) => s.lang) === 'fa';
  const dark = isDark(theme);
  const { up, down } = useChartSettings();
  const digits = digitsOf(symbol);
  const ok = intervalsFor(symbol);
  const interval = canDraw(symbol, cell.interval) ? cell.interval : ok.includes('60') ? '60' : ok[0]!;
  const sync = useGrid((g) => g.sync);
  const mirror = useGrid((g) => g.mirror);
  const mainStudies = useStudies((s) => s.list);
  const showVolume = !['renko', 'linebreak', 'kagi', 'pnf', 'range'].includes(chartType)
    && (!mirror.studies || mainStudies.some((s) => s.key === VOLUME && !s.hidden));
  const showVolumeRef = useRef(showVolume);
  showVolumeRef.current = showVolume;
  const typeRef = useRef(chartType);
  typeRef.current = chartType;
  const darkRef = useRef(dark);
  darkRef.current = dark;

  // The chart, once per theme.
  useEffect(() => {
    if (!host.current) return;
    const p = palette(dark);
    const c = createChart(host.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: p.bg }, textColor: p.text, fontSize: 11, fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false, panes: { separatorColor: p.lineSep } },
      grid: { vertLines: { color: p.grid, style: LineStyle.Dotted }, horzLines: { color: p.grid, style: LineStyle.Dotted } },
      crosshair: { mode: CrosshairMode.Normal, vertLine: { labelBackgroundColor: p.crossLabel, style: LineStyle.Dashed }, horzLine: { labelBackgroundColor: p.crossLabel, style: LineStyle.Dashed } },
      rightPriceScale: { borderColor: p.lineSep, scaleMargins: { top: 0.12, bottom: 0.08 } },
      timeScale: { borderColor: p.lineSep, barSpacing: 6, rightOffset: 10, timeVisible: true },
      localization: { locale: 'en-US', priceFormatter: (v: number) => formatPrice(v, digitsOf(symbol)) },
    });
    vol.current = c.addSeries(HistogramSeries, { priceScaleId: 'v', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false });
    c.priceScale('v').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    chart.current = c;
    studies.current = new StudyRenderer(c, () => price.current, () => digitsOf(symbol));
    if (box.current) registry.set(index + 1, { api: c, el: box.current });

    // Crosshair: this chart's, shared when the reader moves over it.
    c.subscribeCrosshairMove((param: MouseEventParams<Time>) => {
      const t = typeof param.time === 'number' ? param.time : null;
      setHoverBar(t != null ? barAt(bars.current, t) ?? null : null);
      if (!param.sourceEvent && t != null) return;
      if (useGrid.getState().sync.crosshair) useGrid.getState().setHover(t != null ? { t, src: index + 1 } : null);
    });
    // Time: this chart's visible range, shared when the reader scrolls or zooms it.
    c.timeScale().subscribeVisibleLogicalRangeChange((r) => {
      if (!r || applying.current || Date.now() - userInput.current > 400 || !useGrid.getState().sync.time) return;
      const step = intervalSeconds(intervalRef.current);
      useGrid.getState().setRange({ from: logicalToTime(bars.current, r.from, step), to: logicalToTime(bars.current, r.to, step), src: index + 1 });
    });
    return () => {
      registry.delete(index + 1);
      studies.current?.dispose(); studies.current = null;
      c.remove(); chart.current = null; price.current = null; vol.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dark, index]);
  const intervalRef = useRef(interval);
  intervalRef.current = interval;

  const paint = () => {
    const p = palette(darkRef.current);
    price.current?.setData(buildSeries(typeRef.current, bars.current, cellStyle(p), cellParams()).data as never);
    const vc = (b: Candle) => (b.c >= b.o ? p.up : p.down) + (darkRef.current ? '80' : '66');
    vol.current?.setData(showVolumeRef.current ? bars.current.map((b) => ({ time: b.t as UTCTimestamp, value: b.v, color: vc(b) })) : []);
  };

  // The price series for the cell's chart type and colours.
  useEffect(() => {
    const c = chart.current;
    if (!c) return;
    if (price.current) { try { c.removeSeries(price.current); } catch { /* gone */ } }
    const d = digitsOf(symbol);
    price.current = createPriceSeries(c, chartType, cellStyle(palette(dark)), { precision: d, minMove: 1 / 10 ** d });
    studies.current?.resetPriceSeries();
    if (bars.current.length) { paint(); setVersion((v) => v + 1); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartType, dark, up, down, symbol]);

  // Bars: history, then live.
  useEffect(() => {
    let alive = true;
    let stop: (() => void) | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;
    feed.history(symbol, interval).then(({ bars: data }) => {
      if (!alive || !price.current) return;
      bars.current = data;
      paint();
      const req = pendingSpan.current;
      pendingSpan.current = null;
      if (req != null && data.length) showSpanOn(chart.current, data, req);
      else chart.current?.timeScale().scrollToRealTime();
      setLast(data[data.length - 1] ?? null);
      setVersion((v) => v + 1);
      stop = feed.live(symbol, interval, data[data.length - 1], (bar) => {
        const list = bars.current;
        const tail = list[list.length - 1];
        if (tail && bar.t < tail.t) return;
        if (tail && bar.t === tail.t) list[list.length - 1] = bar; else list.push(bar);
        const p = palette(darkRef.current);
        const point = livePoint(typeRef.current, bar, list[list.length - 2], cellStyle(p));
        if (point) price.current?.update(point as never); else if (DERIVED.includes(typeRef.current) || typeRef.current === 'hlcarea') paint();
        if (showVolumeRef.current) vol.current?.update({ time: bar.t as UTCTimestamp, value: bar.v, color: (bar.c >= bar.o ? p.up : p.down) + (darkRef.current ? '80' : '66') });
        setLast({ ...bar });
        if (!timer) timer = setTimeout(() => { timer = null; studies.current?.refresh(bars.current); }, 1000);
      });
    }).catch(() => undefined);
    return () => { alive = false; stop?.(); if (timer) clearTimeout(timer); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [feed, symbol, interval]);

  // Volume follows the main chart's volume study when indicators are mirrored.
  useEffect(() => { if (bars.current.length) paint(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [showVolume]);

  // The main chart's indicators, mirrored.
  useEffect(() => {
    const list = mirror.studies && !['renko', 'linebreak', 'kagi', 'pnf', 'range'].includes(chartType) ? mainStudies.filter((s) => s.key !== VOLUME) : [];
    studies.current?.rebuild(list, bars.current);
  }, [mirror.studies, mainStudies, version, chartType]);

  // Horizontal lines drawn on this symbol (on the main chart or saved for it), mirrored.
  const drawingVersion = useChartBus((b) => b.drawingVersion);
  const mainSymbol = usePrefs((s) => s.symbol);
  useEffect(() => {
    const series = price.current;
    if (!series || !mirror.hlines) return;
    const lines: IPriceLine[] = [];
    let list: { type: string; p?: number; color?: string; width?: number; lineStyle?: string; visible?: boolean }[] = [];
    const layer = useChartBus.getState().drawingLayer;
    if (symbol === mainSymbol && layer) list = layer.getDrawings() as typeof list;
    else { try { const v = JSON.parse(localStorage.getItem(`pc.drawings.v1.${symbol}`) ?? '[]') as unknown; if (Array.isArray(v)) list = v as typeof list; } catch { /* none */ } }
    for (const d of list) {
      if (d.type !== 'hline' || typeof d.p !== 'number' || d.visible === false) continue;
      lines.push(series.createPriceLine({
        price: d.p, color: d.color ?? '#2962FF', lineWidth: Math.max(1, Math.min(4, Number(d.width ?? 1))) as 1 | 2 | 3 | 4,
        lineStyle: d.lineStyle === 'dashed' ? LineStyle.Dashed : d.lineStyle === 'dotted' ? LineStyle.Dotted : LineStyle.Solid, axisLabelVisible: true, title: '',
      }));
    }
    return () => { lines.forEach((l) => { try { series.removePriceLine(l); } catch { /* replaced */ } }); };
  }, [mirror.hlines, symbol, mainSymbol, drawingVersion, version, chartType, dark, up, down]);

  // Axis time zone and price format.
  const timezone = usePrefs((s) => s.timezone);
  useEffect(() => {
    const c = chart.current;
    if (c) { c.applyOptions(timeOptions(timezone, interval)); c.applyOptions({ localization: { locale: 'en-US', priceFormatter: (v: number) => formatPrice(v, digitsOf(symbol)) } }); }
  }, [timezone, interval, symbol, dark, up, down]);

  // The crosshair of the main chart and of the other charts, by time.
  const mainHover = useChartBus((b) => b.hover);
  const shared = useGrid((g) => g.hover);
  useEffect(() => {
    const c = chart.current;
    const s = price.current;
    if (!c || !s || !sync.crosshair) return;
    const t = shared && shared.src !== index + 1 ? shared.t : shared ? null : mainHover?.t ?? null;
    if (shared?.src === index + 1) return;
    if (t == null) { c.clearCrosshairPosition(); setHoverBar(null); return; }
    const b = barAt(bars.current, t);
    if (b) { try { c.setCrosshairPosition(b.c, b.t as UTCTimestamp, s); setHoverBar(b); } catch { /* not drawn yet */ } }
  }, [mainHover, shared, sync.crosshair, index]);

  // The visible time of whichever chart the reader scrolls, when time is synced.
  const range = useGrid((g) => g.range);
  useEffect(() => {
    const c = chart.current;
    if (!c || !range || range.src === index + 1 || !sync.time || !bars.current.length) return;
    const step = intervalSeconds(interval);
    applying.current = true;
    c.timeScale().setVisibleLogicalRange({ from: timeToLogical(bars.current, range.from, step), to: timeToLogical(bars.current, range.to, step) });
    requestAnimationFrame(() => { applying.current = false; });
  }, [range, sync.time, index, interval, version]);

  // A date range from the time bar, for this chart.
  const pendingSpan = useRef<number | null>(null);
  const rangeReq = useGrid((g) => g.rangeReq);
  useEffect(() => {
    if (!rangeReq || !rangeReq.targets.includes(index + 1)) return;
    if (rangeReq.interval !== cell.interval && canDraw(symbol, rangeReq.interval)) {
      pendingSpan.current = rangeReq.span;
      useGrid.getState().setCell(index, { interval: rangeReq.interval });
    } else if (chart.current) showSpanOn(chart.current, bars.current, rangeReq.span);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rangeReq]);

  const shown = hover ?? last;
  const prev = shown ? bars.current[bars.current.indexOf(shown) - 1] : undefined;
  const chg = shown ? shown.c - (prev?.c ?? shown.o) : 0;
  const pct = shown && (prev?.c ?? shown.o) ? (chg / (prev?.c ?? shown.o)) * 100 : 0;
  const tone = shown && shown.c >= shown.o ? 'up' : 'down';
  const mark = () => { userInput.current = Date.now(); };
  return (
    <div ref={box} className={`lite cell${active ? ' active' : ''}`} data-cell={index + 1}
      onMouseDownCapture={() => { mark(); onActivate(); }} onWheelCapture={mark} onTouchStartCapture={mark} onPointerMoveCapture={(e) => { if (e.buttons) mark(); }}>
      <div ref={host} className="lite-host" />
      <div className="lite-legend">
        <button className="lite-sym" onClick={onSymbol} title={symbol}>
          {isExpression(symbol) ? <Icon name="ph-function" size="sm" className="expr-glyph" /> : <SymbolLogo symbol={symbol} size={16} />}
          <span className="sym">{symbol}</span>
        </button>
        <span className="legend-dot">·</span>
        <span className="num">{intervalLabel(interval)}</span>
        {shown ? (
          <span className={`lite-ohlc num ${tone}`}>
            <span className="lite-o"><i>O</i>{formatPrice(shown.o, digits)} <i>H</i>{formatPrice(shown.h, digits)} <i>L</i>{formatPrice(shown.l, digits)} </span><i>C</i>{formatPrice(shown.c, digits)}
            <span className="lite-chg">{' '}{chg >= 0 ? '+' : '−'}{formatPrice(Math.abs(chg), digits)} ({chg >= 0 ? '+' : '−'}{Math.abs(pct).toFixed(2)}%)</span>
          </span>
        ) : null}
        {shown && showVolume ? <span className="lite-vol num"><i>Vol</i> {formatVolume(shown.v)}</span> : null}
        <span className="lite-tools">
          <button className="lt" title={fa ? 'چارت اصلی شود' : 'Make it the main chart'} aria-label="main" onClick={(e) => { e.stopPropagation(); onMain(); }}><Icon name="ph-arrows-out" size="sm" /></button>
        </span>
      </div>
    </div>
  );
}

function showSpanOn(c: IChartApi | null, data: Candle[], seconds: number | null): void {
  if (!c || !data.length) return;
  if (seconds == null) { c.timeScale().fitContent(); return; }
  const lastBar = data[data.length - 1]!;
  const first = data.findIndex((b) => b.t >= lastBar.t - seconds);
  if (first < 0) return;
  c.timeScale().setVisibleLogicalRange({ from: first, to: data.length - 1 + 10 });
}

/**
 * The main chart's side of the sync: its crosshair and visible time shared with the other charts,
 * and theirs shown on it. Mounted once while the layout has more than one chart.
 */
export function GridSync() {
  const api = useChartBus((b) => b.api);
  const series = useChartBus((b) => b.series);
  useEffect(() => {
    if (!api) return;
    let applying = false;
    let userInput = 0;
    const el = api.chartElement();
    const mark = () => { userInput = Date.now(); };
    const drag = (e: PointerEvent) => { if (e.buttons) mark(); };
    el.addEventListener('wheel', mark, { capture: true, passive: true });
    el.addEventListener('pointerdown', mark, { capture: true });
    el.addEventListener('pointermove', drag, { capture: true });
    const onKey = () => mark();
    window.addEventListener('keydown', onKey, true);
    const onCross = (param: MouseEventParams<Time>) => {
      if (!param.sourceEvent && param.time != null) return;
      const g = useGrid.getState();
      if (!g.sync.crosshair) return;
      g.setHover(typeof param.time === 'number' ? { t: param.time, src: 0 } : null);
    };
    const onRange = (r: { from: number; to: number } | null) => {
      const g = useGrid.getState();
      if (!r || applying || !g.sync.time || Date.now() - userInput > 400) return;
      const { bars } = useChartBus.getState();
      const step = intervalSeconds(usePrefs.getState().interval);
      g.setRange({ from: logicalToTime(bars, r.from, step), to: logicalToTime(bars, r.to, step), src: 0 });
    };
    api.subscribeCrosshairMove(onCross);
    api.timeScale().subscribeVisibleLogicalRangeChange(onRange);
    // Theirs on the main chart.
    const unsub = useGrid.subscribe((g, prev) => {
      if (g.hover !== prev.hover && g.sync.crosshair) {
        const h = g.hover;
        const s = useChartBus.getState().series;
        if (!h || h.src === 0) { if (!h) api.clearCrosshairPosition(); }
        else if (s) {
          const b = barAt(useChartBus.getState().bars, h.t);
          if (b) { try { api.setCrosshairPosition(b.c, b.t as UTCTimestamp, s); } catch { /* not drawn yet */ } }
        }
      }
      if (g.range !== prev.range && g.range && g.range.src !== 0 && g.sync.time) {
        const { bars } = useChartBus.getState();
        if (!bars.length) return;
        const step = intervalSeconds(usePrefs.getState().interval);
        applying = true;
        api.timeScale().setVisibleLogicalRange({ from: timeToLogical(bars, g.range.from, step), to: timeToLogical(bars, g.range.to, step) });
        requestAnimationFrame(() => { applying = false; });
      }
    });
    return () => {
      unsub();
      window.removeEventListener('keydown', onKey, true);
      el.removeEventListener('wheel', mark, { capture: true });
      el.removeEventListener('pointerdown', mark, { capture: true });
      el.removeEventListener('pointermove', drag, { capture: true });
      try { api.unsubscribeCrosshairMove(onCross); api.timeScale().unsubscribeVisibleLogicalRangeChange(onRange); } catch { /* chart gone */ }
    };
  }, [api, series]);
  return null;
}
