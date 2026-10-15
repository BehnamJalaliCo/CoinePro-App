// The main chart: lightweight-charts (TradingView's own open-source renderer) set to TradingView's
// desktop measurements — 6px bars, 10 bars of breathing room on the right, dotted grid, dashed
// crosshair — with the legend, volume, every chart type, the indicators in their panes, the drawing
// layer, the live last bar, and older history fetched as the reader scrolls back.
import { useEffect, useRef, useState } from 'react';
import {
  ColorType, CrosshairMode, HistogramSeries, LineStyle, PriceScaleMode, createChart,
  type IChartApi, type IPriceLine, type ISeriesApi, type MouseEventParams, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { Candle } from '../data/types';
import { formatPrice, formatVolume } from './format';
import { useT, usePrefs, isDark } from '../state/prefs';
import { intervalLabel } from '../data/intervals';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { showSpan, useChartBus } from './chartBus';
import { attachDrawings, type DrawingHost } from './drawingHost';
import { createPriceSeries, livePoint, seriesData, DERIVED, type Palette as TypePalette } from './chartTypes';
import { StudyRenderer, type StudyLegendRow } from './studies';
import { useStudies, VOLUME } from '../state/studies';
import { useChartSettings } from '../state/chartSettings';
import { intervalSeconds } from '../data/intervals';
import { timeOptions } from './time';
import { COMPARE_COLORS, useCompare } from '../state/compare';
import { LineSeries, createSeriesMarkers, createTextWatermark, type SeriesMarker } from 'lightweight-charts';
import { useMarket } from '../state/market';
import { calendarWeek, type CalendarEvent } from '../data/intel';
import { DrawingStyleBar } from './DrawingStyleBar';
import { SessionBreaks } from './SessionBreaks';
import { AutoOverlay } from './AutoOverlay';
import { useQuote } from '../data/quote';
import { BandFillPrimitive } from '../engine/bandFill.js';
import { usePaper } from '../state/paper';
import { usePanels } from '../state/panels';
import { marketOf } from '../data/symbols';
import { onPrice } from '../data/prices';
import { ScriptRenderer } from './scriptRenderer';
import { useScript } from '../state/script';
import { runScript } from '../engine/namascript.js';
import { useReplay } from '../state/replay';
import { useOverlay } from '../state/overlay';
import { ReplayBar } from '../shell/ReplayBar';
import { ChartContextMenu } from './ChartContextMenu';
import { TextEditor } from './TextEditor';

export interface HistoryResult { bars: Candle[]; more: boolean }

export interface ChartFeed {
  /** Bars ending now (or before `before`, a bar time in seconds), oldest first, and whether older exist. */
  history(symbol: string, interval: string, before?: number): Promise<HistoryResult>;
  /** Live updates of the forming bar, starting from the last bar of history; returns the unsubscribe. */
  live(symbol: string, interval: string, from: Candle | undefined, onBar: (bar: Candle) => void): () => void;
}

interface Palette extends TypePalette { text: string; grid: string; cross: string; crossLabel: string; lineSep: string }

function palette(dark: boolean): Palette {
  const { up, down } = useChartSettings.getState();
  return { ...basePalette(dark), up, down };
}

function basePalette(dark: boolean): Palette {
  return dark
    ? { bg: '#0F0F0F', text: '#B8B8B8', lineSep: '#2E2E2E', grid: 'rgba(242,242,242,0.06)', cross: '#9C9C9C', crossLabel: '#4A4A4A', up: '#089981', down: '#F23645', line: '#2962FF' }
    : { bg: '#FFFFFF', text: '#0F0F0F', lineSep: '#EBEBEB', grid: 'rgba(46,46,46,0.06)', cross: '#9C9C9C', crossLabel: '#131722', up: '#089981', down: '#F23645', line: '#2962FF' };
}

const volColor = (b: Candle, p: Palette, dark: boolean) => (b.c >= b.o ? p.up : p.down) + (dark ? '80' : '66');

export function ChartView({ feed, digits, displayName, onStudySettings, onAlertAt }: { feed: ChartFeed; digits: number; displayName: string; onStudySettings?(uid: string): void; onAlertAt?(price: number | null): void }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const theme = usePrefs((s) => s.theme);
  const chartType = usePrefs((s) => s.chartType);
  const studies = useStudies((s) => s.list);
  const toggleStudy = useStudies((s) => s.toggle);
  const removeStudy = useStudies((s) => s.remove);
  const host = useRef<HTMLDivElement>(null);
  const drawCanvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef<DrawingHost | null>(null);
  const chart = useRef<IChartApi | null>(null);
  const price = useRef<ISeriesApi<SeriesType> | null>(null);
  const volume = useRef<ISeriesApi<'Histogram'> | null>(null);
  const renderer = useRef<StudyRenderer | null>(null);
  const script = useRef<ScriptRenderer | null>(null);
  const bars = useRef<Candle[]>([]);
  const more = useRef(false);
  const loadingOlder = useRef(false);
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);
  const [last, setLast] = useState<Candle | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [legendRows, setLegendRows] = useState<StudyLegendRow[]>([]);
  const [paneTops, setPaneTops] = useState<number[]>([]);
  const [menuAt, setMenuAt] = useState<{ x: number; y: number; price: number | null } | null>(null);
  const dark = isDark(theme);
  const darkRef = useRef(dark);
  darkRef.current = dark;
  const digitsRef = useRef(digits);
  digitsRef.current = digits;
  const typeRef = useRef(chartType);
  typeRef.current = chartType;
  const studiesRef = useRef(studies);
  studiesRef.current = studies;

  const fmt = () => ({ precision: digitsRef.current, minMove: 1 / 10 ** digitsRef.current });
  // Renko, Kagi, Line break, Point & figure and Range drop time: their bricks are evenly spaced, so
  // nothing else may add time points to the scale (volume and studies stay off, as TradingView does).
  const timeless = () => ['renko', 'linebreak', 'kagi', 'pnf', 'range'].includes(typeRef.current);
  const showVolume = () => !timeless() && studiesRef.current.some((s) => s.key === VOLUME && !s.hidden);

  /** The bars on screen: the replay's slice while replaying, otherwise everything loaded. */
  const view = () => useReplay.getState().slice ?? bars.current;

  function paintPrice() {
    const p = palette(darkRef.current);
    price.current?.setData(seriesData(typeRef.current, view(), p) as never);
    paintHlc(p);
  }
  // HLC area: the high–close band in the rising colour and the close–low band in the falling one.
  const hlc = useRef<{ series: ISeriesApi<SeriesType>; up: BandFillPrimitive; down: BandFillPrimitive } | null>(null);
  function paintHlc(p: Palette) {
    const series = price.current;
    if (!series || typeRef.current !== 'hlcarea') { hlc.current = null; return; }
    const v = view();
    const up = v.map((b) => ({ time: b.t, upper: b.h, lower: b.c }));
    const down = v.map((b) => ({ time: b.t, upper: b.c, lower: b.l }));
    if (!hlc.current || hlc.current.series !== series) {
      const a = new BandFillPrimitive(up, tint(p.up, 0.28));
      const b = new BandFillPrimitive(down, tint(p.down, 0.28));
      series.attachPrimitive(a as never); series.attachPrimitive(b as never);
      hlc.current = { series, up: a, down: b };
    } else { hlc.current.up.setData(up); hlc.current.down.setData(down); }
  }
  function paintVolume() {
    const p = palette(darkRef.current);
    volume.current?.setData(showVolume() ? view().map((b) => ({ time: b.t as UTCTimestamp, value: b.v, color: volColor(b, p, darkRef.current) })) : []);
  }
  function rebuildStudies() {
    renderer.current?.rebuild(timeless() ? [] : studiesRef.current.filter((s) => s.key !== VOLUME), view());
    script.current?.draw(useScript.getState().applied ? useScript.getState().result : null, view());
    setLegendRows(renderer.current?.legend(null) ?? []);
    measurePanes();
  }
  // Where each pane starts, for its legend: read from the pane's own element once laid out.
  function measurePanes() {
    requestAnimationFrame(() => {
      const c = chart.current;
      const origin = host.current?.getBoundingClientRect();
      if (!c || !origin) return;
      const tops = c.panes().map((pane) => {
        const el = pane.getHTMLElement();
        return el ? el.getBoundingClientRect().top - origin.top : 0;
      });
      setPaneTops((prev) => (prev.length === tops.length && prev.every((v, i) => Math.abs(v - tops[i]!) < 0.5) ? prev : tops));
    });
  }

  // Create once.
  useEffect(() => {
    if (!host.current) return;
    const p = palette(dark);
    const c = createChart(host.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: p.bg }, textColor: p.text, fontSize: 12,
        fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false,
        panes: { separatorColor: p.lineSep, separatorHoverColor: 'rgba(212,166,74,0.4)', enableResize: true },
      },
      grid: { vertLines: { color: p.grid, style: LineStyle.Dotted }, horzLines: { color: p.grid, style: LineStyle.Dotted } },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: p.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: p.crossLabel },
        horzLine: { color: p.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: p.crossLabel },
      },
      rightPriceScale: { borderColor: p.lineSep, scaleMargins: { top: 0.1, bottom: 0.08 } },
      timeScale: { borderColor: p.lineSep, barSpacing: 6, rightOffset: 10, timeVisible: true, secondsVisible: false, minBarSpacing: 0.5 },
      // Latin digits and English month names on the axes, whatever the browser's locale: market
      // figures are always Latin (owner's rule), and TradingView writes dates the same way.
      localization: { locale: 'en-US', dateFormat: "dd MMM ''yy", priceFormatter: (v: number) => formatPrice(v, digitsRef.current) },
    });
    chart.current = c;
    price.current = createPriceSeries(c, typeRef.current, p, fmt());
    useChartBus.getState().setSeries(price.current);
    volume.current = c.addSeries(HistogramSeries, { priceScaleId: 'vol', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false });
    c.priceScale('vol').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    renderer.current = new StudyRenderer(c, () => price.current, () => digitsRef.current);
    script.current = new ScriptRenderer(c, () => price.current, () => drawing.current?.layer ?? null, () => renderer.current?.freePane ?? 1);

    c.subscribeCrosshairMove((param: MouseEventParams<Time>) => {
      if (!param.time) { setHoverIdx(null); useChartBus.getState().setHover(null); return; }
      const v = view();
      const i = v.findIndex((b) => b.t === param.time);
      setHoverIdx(i >= 0 ? i : null);
      useChartBus.getState().setHover(i >= 0 ? v[i]! : null);
    });
    // Replay: the click that picks the starting bar.
    c.subscribeClick((param) => {
      const r = useReplay.getState();
      if (r.selecting && typeof param.time === 'number') r.start(bars.current, param.time);
    });
    // Older history when the reader scrolls near the left edge.
    c.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (range && range.from < 30 && more.current && !loadingOlder.current) void loadOlder();
    });
    useChartBus.getState().setApi(c);
    if (drawCanvas.current) {
      drawing.current = attachDrawings(drawCanvas.current, c);
      drawing.current.setSeries(price.current);
    }
    const ro = new ResizeObserver(() => measurePanes());
    ro.observe(host.current);
    return () => {
      ro.disconnect();
      drawing.current?.dispose(); drawing.current = null;
      script.current?.clear(); script.current = null;
      renderer.current?.dispose(); renderer.current = null;
      useChartBus.getState().setApi(null); useChartBus.getState().setSeries(null); c.remove(); chart.current = null; price.current = null; volume.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Theme changes recolour in place; nothing is refetched.
  useEffect(() => {
    const p = palette(dark);
    chart.current?.applyOptions({
      layout: { background: { type: ColorType.Solid, color: p.bg }, textColor: p.text, panes: { separatorColor: p.lineSep } },
      grid: { vertLines: { color: p.grid }, horzLines: { color: p.grid } },
      crosshair: { vertLine: { labelBackgroundColor: p.crossLabel }, horzLine: { labelBackgroundColor: p.crossLabel } },
      rightPriceScale: { borderColor: p.lineSep }, timeScale: { borderColor: p.lineSep },
    });
    paintVolume();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dark]);

  // Chart type (and the candle colours, the price line): a new price series in place of the old
  // one; drawings, markers and levels follow it.
  const up = useChartSettings((x) => x.up);
  const down = useChartSettings((x) => x.down);
  const priceLine = useChartSettings((x) => x.priceLine);
  const gridMode = useChartSettings((x) => x.grid);
  const crosshairMode = useChartSettings((x) => x.crosshair);
  const countdown = useChartSettings((x) => x.countdown);
  const showOhlc = useChartSettings((x) => x.showOhlc);
  const showChange = useChartSettings((x) => x.showChange);
  useEffect(() => {
    const c = chart.current;
    if (!c || !price.current) return;
    c.removeSeries(price.current);
    price.current = createPriceSeries(c, chartType, palette(darkRef.current), fmt());
    useChartBus.getState().setSeries(price.current);
    price.current.applyOptions({ priceLineVisible: priceLine });
    renderer.current?.resetPriceSeries();
    drawing.current?.setSeries(price.current);
    paintPrice();
    paintVolume();
    rebuildStudies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartType, up, down, priceLine]);

  useEffect(() => {
    const p = palette(darkRef.current);
    chart.current?.applyOptions({
      grid: { vertLines: { visible: gridMode === 'both' || gridMode === 'vertical', color: p.grid }, horzLines: { visible: gridMode === 'both' || gridMode === 'horizontal', color: p.grid } },
      crosshair: { mode: crosshairMode === 'magnet' ? CrosshairMode.Magnet : CrosshairMode.Normal },
    });
  }, [gridMode, crosshairMode, dark]);

  // Time left in the forming bar, under the last price on the axis, as TradingView shows it.
  useEffect(() => {
    if (!countdown || ['1W', '1M'].includes(interval)) { price.current?.applyOptions({ title: '' }); return; }
    const secs = intervalSeconds(interval);
    const tick = () => {
      const now = Math.floor(Date.now() / 1000);
      const left = secs - (now % secs);
      const h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), x = left % 60;
      const txt = h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}`;
      price.current?.applyOptions({ title: txt });
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [countdown, interval, chartType]);

  // Price precision follows the instrument.
  useEffect(() => { price.current?.applyOptions({ priceFormat: { type: 'price', ...fmt() } }); }, [digits]);

  // Indicators: rebuilt when the list or an input changes.
  useEffect(() => {
    paintVolume();
    if (bars.current.length) rebuildStudies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studies]);

  // NamaScript: run on the chart's bars when applied, re-run when asked or when the bars change.
  const scriptApplied = useScript((s) => s.applied);
  const scriptRun = useScript((s) => s.runSeq);
  const [barsVersion, setBarsVersion] = useState(0);
  useEffect(() => {
    if (!scriptApplied) { script.current?.clear(); measurePanes(); return; }
    if (!bars.current.length) return;
    let alive = true;
    const st = useScript.getState();
    st.setRunning(true);
    void runScript(st.source, bars.current, st.inputs).then((r) => {
      if (!alive) return;
      useScript.getState().setResult(r);
      useScript.getState().setRunning(false);
      script.current?.draw(r.ok ? r : null, bars.current);
      measurePanes();
    });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scriptApplied, scriptRun, barsVersion]);

  // A trade setup (a signal, the AI's reading) as price lines: entry, stop, targets.
  const setup = useOverlay((o) => o.setup);
  useEffect(() => {
    const series = price.current;
    if (!series || !setup || setup.symbol !== symbol) return;
    const lines: IPriceLine[] = [];
    const add = (p: number | null, color: string, title: string, style = LineStyle.Solid) => {
      if (p == null) return;
      lines.push(series.createPriceLine({ price: p, color, lineWidth: 2, lineStyle: style, axisLabelVisible: true, title }));
    };
    add(setup.entry, '#2962FF', `${setup.label} · Entry`);
    add(setup.sl, '#F23645', 'SL', LineStyle.Dashed);
    setup.targets.forEach((tp, i) => add(tp, '#089981', `TP${i + 1}`, LineStyle.Dashed));
    return () => { lines.forEach((l) => { try { series.removePriceLine(l); } catch { /* series replaced */ } }); };
  }, [setup, symbol, chartType]);
  useEffect(() => { if (setup && setup.symbol !== symbol) useOverlay.getState().clear(); }, [symbol, setup]);

  // The previous session's close as a dotted line (TradingView's "Previous day close price line").
  const prevCloseOn = useChartSettings((x) => x.prevClose);
  const prevClose = useMarket((m) => { const tk = m.tickers[symbol]; if (!tk) return null; const v = tk.prevClose ?? tk.last - tk.change; return Number.isFinite(v) && v > 0 ? Number(v.toPrecision(10)) : null; });
  useEffect(() => {
    const series = price.current;
    if (!series || !prevCloseOn || prevClose == null) return;
    const line = series.createPriceLine({ price: prevClose, color: '#787B86', lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: 'Prev close' });
    return () => { try { series.removePriceLine(line); } catch { /* series replaced */ } };
  }, [prevCloseOn, prevClose, chartType, up, down, priceLine, symbol]);

  // The symbol and interval behind the bars, faint, as TradingView's watermark.
  const watermark = useChartSettings((x) => x.watermark);
  useEffect(() => {
    const c = chart.current;
    if (!c || !watermark) return;
    const wm = createTextWatermark(c.panes()[0]!, { horzAlign: 'center', vertAlign: 'center', lines: [{ text: `${symbol}, ${intervalLabel(interval)}`, color: dark ? 'rgba(255,255,255,0.07)' : 'rgba(19,23,34,0.07)', fontSize: 64, fontFamily: "'IRANYekanX', sans-serif", fontStyle: '' }] });
    return () => { try { wm.detach(); } catch { /* chart gone */ } };
  }, [watermark, symbol, interval, dark]);

  // The week's high-impact economic events that touch this symbol, on the bars they fall in.
  const eventsOn = useChartSettings((x) => x.events);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [eventsAt, setEventsAt] = useState<Map<number, CalendarEvent[]>>(new Map());
  useEffect(() => {
    if (!eventsOn) return;
    let alive = true;
    void loadEvents().then((e) => { if (alive) setEvents(e); });
    return () => { alive = false; };
  }, [eventsOn]);
  useEffect(() => {
    const series = price.current;
    const list = bars.current;
    if (!series || !eventsOn || !events.length || !list.length || timeless()) return;
    const ccy = marketOf(symbol) === 'crypto' ? ['USD'] : [symbol.slice(0, 3), symbol.slice(3, 6)].map((c) => (c === 'XAU' || c === 'XAG' ? 'USD' : c));
    const first = list[0]!.t; const last = list[list.length - 1]!.t + intervalSeconds(interval);
    const markers: SeriesMarker<Time>[] = [];
    const byBar = new Map<number, CalendarEvent[]>();
    for (const e of events) {
      const t = Math.floor(e.time / 1000);
      if (e.impact !== 'high' || t < first || t >= last || !ccy.includes(e.currency.toUpperCase().slice(0, 3))) continue;
      let lo = 0; let hi = list.length - 1;
      while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (list[mid]!.t <= t) lo = mid; else hi = mid - 1; }
      // A dot under the bar, as TradingView marks events; the names are in the legend on hover.
      markers.push({ time: list[lo]!.t as Time, position: 'belowBar', color: '#F57C00', shape: 'circle', size: 0.6 });
      const k = list[lo]!.t;
      byBar.set(k, [...(byBar.get(k) ?? []), e]);
    }
    setEventsAt(byBar);
    if (!markers.length) return;
    markers.sort((a, b) => (a.time as number) - (b.time as number));
    const plugin = createSeriesMarkers(series, markers);
    return () => { try { plugin.detach(); } catch { /* series replaced */ } setEventsAt(new Map()); };
  }, [eventsOn, events, barsVersion, chartType, up, down, priceLine, symbol, interval]);

  // Replay: repaint from the slice as it grows; back to everything on exit.
  const replaySlice = useReplay((r) => r.slice);
  useEffect(() => {
    if (!chart.current) return;
    paintPrice(); paintVolume();
    renderer.current?.refresh(view());
    setLegendRows(renderer.current?.legend(null) ?? []);
    if (!replaySlice) chart.current.timeScale().scrollToRealTime();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [replaySlice]);

  // Time zone of the axis and the crosshair label. Daily and longer bars keep their UTC date.
  const timezone = usePrefs((s) => s.timezone);
  useEffect(() => { chart.current?.applyOptions(timeOptions(timezone, interval)); }, [timezone, interval]);

  // The time bar's scale switches; comparing forces percent, as on TradingView.
  const scale = useChartBus((b) => b.scale);
  const autoScale = useChartBus((b) => b.autoScale);
  const compare = useCompare((s) => s.symbols);
  const removeCompare = useCompare((s) => s.remove);
  const invert = useChartBus((b) => b.invert);
  useEffect(() => {
    const pct = scale === 'percent' || compare.length > 0;
    const mode = pct ? PriceScaleMode.Percentage : scale === 'log' ? PriceScaleMode.Logarithmic : scale === 'indexed' ? PriceScaleMode.IndexedTo100 : PriceScaleMode.Normal;
    chart.current?.priceScale('right').applyOptions({ mode, autoScale, invertScale: invert });
  }, [scale, autoScale, compare.length, invert]);

  // Compared symbols: a line each, same interval, live from the price feed.
  const [compareLast, setCompareLast] = useState<Record<string, number>>({});
  useEffect(() => {
    const c = chart.current;
    if (!c) return;
    const made: ISeriesApi<'Line'>[] = [];
    const offs: (() => void)[] = [];
    let alive = true;
    compare.forEach((sym, i) => {
      const s = c.addSeries(LineSeries, { color: COMPARE_COLORS[i % COMPARE_COLORS.length], lineWidth: 2, priceLineVisible: false, lastValueVisible: true, crosshairMarkerVisible: false });
      made.push(s);
      void feed.history(sym, interval).then(({ bars: data }) => {
        if (!alive) return;
        s.setData(data.map((b) => ({ time: b.t as UTCTimestamp, value: b.c })));
        const tail = data[data.length - 1];
        if (tail) setCompareLast((m) => ({ ...m, [sym]: tail.c }));
        offs.push(onPrice(sym, (px) => {
          const last = data[data.length - 1];
          if (!last) return;
          s.update({ time: last.t as UTCTimestamp, value: px });
          setCompareLast((m) => ({ ...m, [sym]: px }));
        }));
      }).catch(() => undefined);
    });
    return () => { alive = false; offs.forEach((f) => f()); made.forEach((s) => { try { c.removeSeries(s); } catch { /* gone */ } }); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compare, interval, symbol]);

  async function loadOlder() {
    const first = bars.current[0];
    if (!first) return;
    loadingOlder.current = true;
    const key = `${symbol}|${interval}`;
    try {
      const r = await feed.history(symbol, interval, first.t);
      if (`${usePrefs.getState().symbol}|${usePrefs.getState().interval}` !== key) return;
      const older = r.bars.filter((b) => b.t < first.t);
      more.current = r.more && older.length > 0;
      if (!older.length) return;
      const ts = chart.current?.timeScale();
      const range = ts?.getVisibleLogicalRange();
      bars.current = [...older, ...bars.current];
      paintPrice(); paintVolume();
      renderer.current?.refresh(bars.current);
      if (ts && range) ts.setVisibleLogicalRange({ from: range.from + older.length, to: range.to + older.length });
      useChartBus.getState().setBars(bars.current);
      drawing.current?.layer.setCandles(bars.current);
    } catch {
      more.current = false;
    } finally {
      loadingOlder.current = false;
    }
  }

  // Load on symbol or interval change; live updates after.
  useEffect(() => {
    let alive = true;
    let stop: (() => void) | null = null;
    let studyTimer: ReturnType<typeof setTimeout> | null = null;
    setState('loading'); setHoverIdx(null);
    more.current = false;
    feed.history(symbol, interval).then(({ bars: data, more: hasMore }) => {
      if (!alive) return;
      bars.current = data;
      more.current = hasMore;
      paintPrice(); paintVolume();
      const c = chart.current;
      c?.timeScale().applyOptions({ barSpacing: 6, rightOffset: 10 });
      c?.timeScale().scrollToRealTime();
      const bus = useChartBus.getState();
      bus.setBars(data);
      drawing.current?.layer.setCandles(data);
      drawing.current?.layer.setDigits(digitsRef.current);
      drawing.current?.setSymbol(symbol);
      rebuildStudies();
      setBarsVersion((v) => v + 1);
      if (bus.pendingSpan && c) { showSpan(c, data, bus.pendingSpan); bus.setPendingSpan(null); }
      setLast(data[data.length - 1] ?? null);
      setState(data.length ? 'ready' : 'error');
      stop = feed.live(symbol, interval, data[data.length - 1], (bar) => {
        const list = bars.current;
        const tail = list[list.length - 1];
        if (tail && bar.t < tail.t) return;
        if (tail && bar.t === tail.t) list[list.length - 1] = bar; else list.push(bar);
        if (useReplay.getState().slice) return;
        const p = palette(darkRef.current);
        const point = livePoint(typeRef.current, bar, list[list.length - 2], p);
        if (point) price.current?.update(point as never); else if (DERIVED.includes(typeRef.current)) paintPrice();
        if (showVolume()) volume.current?.update({ time: bar.t as UTCTimestamp, value: bar.v, color: volColor(bar, p, darkRef.current) });
        setLast({ ...bar });
        // Indicators follow the forming bar at most once a second.
        if (!studyTimer) studyTimer = setTimeout(() => { studyTimer = null; renderer.current?.refresh(bars.current); setLegendRows(renderer.current?.legend(null) ?? []); }, 1000);
      });
    }).catch(() => { if (alive) setState('error'); });
    return () => { alive = false; stop?.(); if (studyTimer) clearTimeout(studyTimer); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, interval, attempt, feed]);

  // Legend values under the crosshair; pane edges move when a separator is dragged.
  useEffect(() => {
    if (renderer.current) setLegendRows(renderer.current.legend(hoverIdx));
    measurePanes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hoverIdx]);

  const onScreen = replaySlice ?? bars.current;
  const shown = hoverIdx != null ? onScreen[hoverIdx] ?? last : replaySlice ? onScreen[onScreen.length - 1] ?? null : last;
  const shownIdx = hoverIdx ?? onScreen.length - 1;
  const prev = shownIdx > 0 ? onScreen[shownIdx - 1] : undefined;
  const chg = shown ? shown.c - (prev?.c ?? shown.o) : 0;
  const chgPct = shown && (prev?.c ?? shown.o) ? (chg / (prev?.c ?? shown.o)) * 100 : 0;
  const tone = shown && shown.c >= shown.o ? 'up' : 'down';
  const volStudy = studies.find((s) => s.key === VOLUME);

  const studyRow = (r: StudyLegendRow) => (
    <div key={r.uid} className={`legend-study${r.hidden ? ' off' : ''}`}>
      <span className="legend-study-name num">{r.label}</span>
      {!r.hidden ? r.values.map((v, i) => (
        <span key={i} className="num" style={{ color: r.colors[i] }}>{v == null ? '∅' : formatPrice(v, r.pane === 0 ? digits : Math.abs(v) >= 1000 ? 0 : 2)}</span>
      )) : null}
      <span className="legend-tools">
        <button className="lt" title="hide" onClick={() => toggleStudy(r.uid)}><Icon name={r.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
        <button className="lt" title={t('settings')} onClick={() => onStudySettings?.(r.uid)}><Icon name="ph-gear" size="sm" /></button>
        <button className="lt" title={t('removeDrawings')} onClick={() => removeStudy(r.uid)}><Icon name="ph-x" size="sm" /></button>
      </span>
    </div>
  );

  // TradingView's Buy/Sell boxes under the title: the live top of the book; a click is a paper trade.
  const quote = useQuote(symbol);
  const replayOn = useReplay((r) => Boolean(r.state?.on));
  const sessionBreaks = useChartSettings((x) => x.sessionBreaks);
  const autoDiv = useChartSettings((x) => x.autoDivergence);
  const autoHarm = useChartSettings((x) => x.autoHarmonic);
  const autoGaps = useChartSettings((x) => x.autoGaps);
  const paperTrade = (side: 'long' | 'short', px: number) => {
    usePaper.getState().open(symbol, side, Number((1000 / px).toPrecision(2)), px);
    if (usePanels.getState().bottom !== 'paper') usePanels.getState().setBottom('paper');
  };

  return (
    <div className="ws-chart" onContextMenu={(e) => {
      if ((e.target as HTMLElement).closest('.legend, .replay-bar')) return;
      e.preventDefault();
      const r = e.currentTarget.getBoundingClientRect();
      const y = e.clientY - r.top;
      const px = price.current?.coordinateToPrice(y);
      setMenuAt({ x: e.clientX, y: e.clientY, price: typeof px === 'number' ? px : null });
    }}>
      <div ref={host} className="chart-host" />
      {sessionBreaks ? <SessionBreaks api={chart.current} bars={bars.current} intraday={intervalSeconds(interval) < 86400} timezone={timezone} version={barsVersion} color={dark ? 'rgba(255,255,255,0.18)' : 'rgba(19,23,34,0.18)'} /> : null}
      {(autoDiv || autoHarm || autoGaps) && !timeless() ? <AutoOverlay api={chart.current} series={price.current} bars={view()} version={barsVersion + (replaySlice?.length ?? 0)} flags={{ divergence: autoDiv, harmonic: autoHarm, gaps: autoGaps }} fa={usePrefs.getState().lang === 'fa'} /> : null}
      <canvas ref={drawCanvas} className="draw-layer" />
      <div className="legend">
        <div className="legend-row">
          <SymbolLogo symbol={symbol} size={18} />
          <span className="legend-title fa">{displayName}</span>
          <span className="legend-dot">·</span>
          <span className="num">{intervalLabel(interval)}</span>
          {shown && showOhlc ? (
            <span className={`legend-ohlc num ${tone}`}>
              <i>O</i>{formatPrice(shown.o, digits)} <i>H</i>{formatPrice(shown.h, digits)} <i>L</i>{formatPrice(shown.l, digits)} <i>C</i>{formatPrice(shown.c, digits)}
              {showChange ? <>{' '}{chg >= 0 ? '+' : '−'}{formatPrice(Math.abs(chg), digits)} ({chg >= 0 ? '+' : '−'}{Math.abs(chgPct).toFixed(2)}%)</> : null}
            </span>
          ) : null}
        </div>
        {hoverIdx != null && shown && eventsAt.get(shown.t)?.length ? (
          <div className="legend-events">
            {eventsAt.get(shown.t)!.map((e, i) => <span key={i} className="num"><i className="ev-dot" />{e.currency} · {e.title}{e.actual ? ` · ${e.actual}` : e.forecast ? ` · f ${e.forecast}` : ''}</span>)}
          </div>
        ) : null}
        {quote && !replayOn ? (
          <div className="legend-trade" role="group" aria-label="trade">
            <button className="lt-sell" title="Sell (paper)" onClick={() => paperTrade('short', (quote.bid + quote.ask) / 2)}><b className="num">{formatPrice(quote.bid, digits)}</b><span>SELL</span></button>
            <span className="lt-spread num">{formatPrice(quote.spread, digits)}</span>
            <button className="lt-buy" title="Buy (paper)" onClick={() => paperTrade('long', (quote.bid + quote.ask) / 2)}><b className="num">{formatPrice(quote.ask, digits)}</b><span>BUY</span></button>
          </div>
        ) : null}
        {volStudy && shown ? (
          <div className={`legend-study${volStudy.hidden ? ' off' : ''}`}>
            <span className="legend-study-name">Vol</span>
            {!volStudy.hidden ? <span className={`num ${tone}`}>{formatVolume(shown.v)}</span> : null}
            <span className="legend-tools">
              <button className="lt" onClick={() => toggleStudy(volStudy.uid)}><Icon name={volStudy.hidden ? 'eye_off' : 'ph-eye'} size="sm" /></button>
              <button className="lt" onClick={() => removeStudy(volStudy.uid)}><Icon name="ph-x" size="sm" /></button>
            </span>
          </div>
        ) : null}
        {compare.map((sym, i) => (
          <div key={sym} className="legend-study">
            <span className="legend-study-name sym" style={{ color: COMPARE_COLORS[i % COMPARE_COLORS.length] }}>{sym}</span>
            {compareLast[sym] != null ? <span className="num">{formatPrice(compareLast[sym]!, Math.min(8, Math.max(2, digits)))}</span> : null}
            <span className="legend-tools"><button className="lt" onClick={() => removeCompare(sym)}><Icon name="ph-x" size="sm" /></button></span>
          </div>
        ))}
        {legendRows.filter((r) => r.pane === 0).map(studyRow)}
      </div>
      {legendRows.filter((r) => r.pane > 0).map((r) => (
        <div key={r.uid} className="legend pane-legend" style={{ top: (paneTops[r.pane] ?? 0) + 4 }}>{studyRow(r)}</div>
      ))}
      <ReplayBar />
      <DrawingStyleBar />
      <TextEditor />
      {menuAt ? <ChartContextMenu at={menuAt} onClose={() => setMenuAt(null)} onAlertAt={onAlertAt} /> : null}
      {state === 'loading' ? <div className="chart-state fa">{t('loading')}…</div> : null}
      {state === 'error' ? (
        <div className="chart-state fa">{t('chartFailed')} <button className="link" onClick={() => setAttempt((a) => a + 1)}>{t('retry')}</button></div>
      ) : null}
    </div>
  );
}

// The week's calendar, fetched once per visit for the chart's event markers.
let eventsCache: Promise<CalendarEvent[]> | null = null;
function loadEvents(): Promise<CalendarEvent[]> {
  eventsCache ??= calendarWeek().catch(() => { eventsCache = null; return []; });
  return eventsCache;
}

/** A #rrggbb colour at an opacity. */
function tint(hex: string, a: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const n = parseInt(m[1]!, 16);
  return `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
}
