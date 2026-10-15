// The main chart: lightweight-charts (TradingView's own open-source renderer) set to TradingView's
// desktop measurements and to every choice in Chart Settings — the legend, volume, every chart
// type, the indicators in their panes, the drawing layer, the live last bar, the price axis's
// labels and lines, the menus of the chart and of both axes, and older history fetched as the
// reader scrolls back.
import { useEffect, useRef, useState } from 'react';
import {
  ColorType, CrosshairMode, HistogramSeries, LineSeries, LineStyle, PriceScaleMode, createChart, createTextWatermark,
  type AutoscaleInfo, type IChartApi, type ISeriesApi, type MouseEventParams, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { Candle } from '../data/types';
import { formatPrice } from './format';
import { useT, usePrefs, isDark } from '../state/prefs';
import { intervalLabel, intervalSeconds } from '../data/intervals';
import { showSpan, useChartBus, type CompareRow } from './chartBus';
import { attachDrawings, type DrawingHost } from './drawingHost';
import { BRICKS, DERIVED, TIMELESS, buildSeries, createPriceSeries, livePoint, tint, type SeriesStyle, type TypeParams } from './chartTypes';
import { StudyRenderer, type StudyLegendRow } from './studies';
import { useStudies, VOLUME } from '../state/studies';
import { useChartSettings, showsLine, showsValue, type ChartSettings, type CrossStyle } from '../state/chartSettings';
import { timeOptions, zoneOf } from './time';
import { compareColor, useCompare } from '../state/compare';
import { DrawingStyleBar } from './DrawingStyleBar';
import { SessionBreaks } from './SessionBreaks';
import { AutoOverlay } from './AutoOverlay';
import { BandFillPrimitive } from '../engine/bandFill.js';
import { onPrice } from '../data/prices';
import { ScriptRenderer } from './scriptRenderer';
import { useScript } from '../state/script';
import { runScript } from '../engine/namascript.js';
import { useReplay } from '../state/replay';
import { useOverlay } from '../state/overlay';
import { ReplayBar } from '../shell/ReplayBar';
import { ChartContextMenu } from './ChartContextMenu';
import { PriceScaleMenu } from './PriceScaleMenu';
import { TimeScaleMenu } from './TimeScaleMenu';
import { TextEditor } from './TextEditor';
import { CountdownPrimitive, KagiPrimitive, PnfPrimitive } from './primitives';
import { secondsToClose, countdownTint } from '../engine/scales_crosshair.js';
import { Legend, PaneLegend } from './Legend';
import { PriceLines } from './PriceLines';
import { EventMarks } from './EventMarks';
import { SessionShading } from './SessionShading';
import { VolumeProfile } from './VolumeProfile';
import { SetupZones } from './SetupZones';
import { AxisPlus, AxisChips } from './AxisPlus';
import { NavButtons, PaneControls } from './NavButtons';
import { usePlacement } from './studyPlacement';
import { SettingsHost } from '../shell/SettingsDialog';
import { themeColours } from '../shell/SettingsDialog';
import './chart-x.css';

export interface HistoryResult { bars: Candle[]; more: boolean }

export interface ChartFeed {
  /** Bars ending now (or before `before`, a bar time in seconds), oldest first, and whether older exist. */
  history(symbol: string, interval: string, before?: number): Promise<HistoryResult>;
  /** Live updates of the forming bar, starting from the last bar of history; returns the unsubscribe. */
  live(symbol: string, interval: string, from: Candle | undefined, onBar: (bar: Candle) => void): () => void;
}

/** TradingView's crosshair line styles, by lightweight-charts' numbers. */
const CROSS_STYLE: Record<CrossStyle, LineStyle> = { solid: LineStyle.Solid, dotted: LineStyle.Dotted, dashed: LineStyle.Dashed, 'large-dashed': LineStyle.LargeDashed, 'sparse-dotted': LineStyle.SparseDotted };

interface Look { bg: string; bg2: string; gradient: boolean; text: string; lineSep: string; gridV: string; gridH: string; cross: string; crossLabel: string; watermark: string }

function lookOf(dark: boolean, s: ChartSettings): Look {
  const t = themeColours(dark);
  return {
    bg: s.bgColor || t.bg, bg2: s.bgColor2 || s.bgColor || t.bg, gradient: s.bgType === 'gradient',
    text: s.scaleTextColor || t.text, lineSep: s.scaleLineColor || t.line,
    gridV: s.vertGridColor || (dark ? 'rgba(242,242,242,0.06)' : 'rgba(46,46,46,0.06)'),
    gridH: s.horzGridColor || (dark ? 'rgba(242,242,242,0.06)' : 'rgba(46,46,46,0.06)'),
    cross: s.crossColor || t.cross, crossLabel: dark ? '#4A4A4A' : '#131722', watermark: s.watermarkColor || t.watermark,
  };
}

function styleOf(dark: boolean, s: ChartSettings): SeriesStyle {
  return {
    up: s.up, down: s.down, line: s.lineColor, bg: s.bgColor || themeColours(dark).bg,
    bodyVisible: s.bodyVisible, borderVisible: s.borderVisible, borderUp: s.borderUp, borderDown: s.borderDown,
    wickVisible: s.wickVisible, wickUp: s.wickUp, wickDown: s.wickDown, colorByPrevClose: s.colorByPrevClose,
    thinBars: s.thinBars, hollow: s.hollow, lineWidth: s.lineWidth, areaTop: s.areaTop, areaBottom: s.areaBottom,
  };
}

const paramsOf = (s: ChartSettings): TypeParams => ({
  renkoMethod: s.renkoMethod, renkoAtr: s.renkoAtr, renkoBox: s.renkoBox, renkoWicks: s.renkoWicks, rangeMethod: s.rangeMethod, rangeSize: s.rangeSize,
  lineBreakCount: s.lineBreakCount, kagiMethod: s.kagiMethod, kagiAtr: s.kagiAtr, kagiReversal: s.kagiReversal,
  pnfMethod: s.pnfMethod, pnfAtr: s.pnfAtr, pnfBox: s.pnfBox, pnfReversal: s.pnfReversal, pnfSource: s.pnfSource,
});

const volColor = (b: Candle, s: ChartSettings, dark: boolean, prev?: Candle) => tint((s.colorByPrevClose ? b.c >= (prev?.c ?? b.o) : b.c >= b.o) ? s.up : s.down, dark ? 0.5 : 0.4);

/** The series of one indicator (lightweight-charts' own objects), read from the renderer. */
export function studySeries(renderer: StudyRenderer | null, uid: string): ISeriesApi<SeriesType>[] {
  if (!renderer) return [];
  const own = (renderer as unknown as { seriesOf?(uid: string): ISeriesApi<SeriesType>[] }).seriesOf;
  if (typeof own === 'function') return own.call(renderer, uid);
  const list = (renderer as unknown as { rendered?: { study: { uid: string }; series: ISeriesApi<SeriesType>[] }[] }).rendered ?? [];
  return list.find((r) => r.study.uid === uid)?.series ?? [];
}

/** The index of the bar at a time (bars are in time order), or −1. */
export function indexAt(list: Candle[], t: number): number {
  let lo = 0; let hi = list.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1; const v = list[mid]!.t;
    if (v === t) return mid;
    if (v < t) lo = mid + 1; else hi = mid - 1;
  }
  return -1;
}

/** A simple moving average of the volumes. */
function sma(values: number[], n: number): (number | null)[] {
  const out: (number | null)[] = [];
  let sum = 0;
  for (let i = 0; i < values.length; i++) {
    sum += values[i]!;
    if (i >= n) sum -= values[i - n]!;
    out.push(i >= n - 1 ? sum / n : null);
  }
  return out;
}

export type MenuAt =
  | { kind: 'chart'; x: number; y: number; price: number | null; time: number | null }
  | { kind: 'price'; x: number; y: number }
  | { kind: 'time'; x: number; y: number };

export function ChartView({ feed, digits: instDigits, displayName, onStudySettings, onAlertAt }: { feed: ChartFeed; digits: number; displayName: string; onStudySettings?(uid: string): void; onAlertAt?(price: number | null): void }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const interval = usePrefs((s) => s.interval);
  const theme = usePrefs((s) => s.theme);
  const chartType = usePrefs((s) => s.chartType);
  const lang = usePrefs((s) => s.lang);
  const studies = useStudies((s) => s.list);
  const cs = useChartSettings();
  const digits = cs.precision >= 0 ? cs.precision : instDigits;
  const host = useRef<HTMLDivElement>(null);
  const wrap = useRef<HTMLDivElement>(null);
  const drawCanvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef<DrawingHost | null>(null);
  const chart = useRef<IChartApi | null>(null);
  const price = useRef<ISeriesApi<SeriesType> | null>(null);
  const volume = useRef<ISeriesApi<'Histogram'> | null>(null);
  const volumeMa = useRef<ISeriesApi<'Line'> | null>(null);
  const renderer = useRef<StudyRenderer | null>(null);
  const script = useRef<ScriptRenderer | null>(null);
  const bars = useRef<Candle[]>([]);
  const shownRef = useRef<Candle[]>([]);
  const more = useRef(false);
  const loadingOlder = useRef(false);
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);
  const [last, setLast] = useState<Candle | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [legendRows, setLegendRows] = useState<StudyLegendRow[]>([]);
  const [panes, setPanes] = useState<{ top: number; height: number }[]>([]);
  const [menuAt, setMenuAt] = useState<MenuAt | null>(null);
  const [seriesVer, setSeriesVer] = useState(0);
  const [paneMode, setPaneMode] = useState<{ max: number | null; collapsed: number[] }>({ max: null, collapsed: [] });
  const dark = isDark(theme);
  const darkRef = useRef(dark);
  darkRef.current = dark;
  const digitsRef = useRef(digits);
  digitsRef.current = digits;
  const typeRef = useRef(chartType);
  typeRef.current = chartType;
  const studiesRef = useRef(studies);
  studiesRef.current = studies;
  const csRef = useRef(cs);
  csRef.current = cs;
  const kagiPrim = useRef<KagiPrimitive | null>(null);
  const pnfPrim = useRef<PnfPrimitive | null>(null);
  const countdownPrim = useRef<CountdownPrimitive | null>(null);

  const fmt = () => ({ precision: digitsRef.current, minMove: 1 / 10 ** digitsRef.current });
  const timeless = () => TIMELESS.includes(typeRef.current);
  /** The bars indicators and volume are computed on: the time bars, or the bricks of Renko, Range, Line break. */
  const studyBars = () => (BRICKS.includes(typeRef.current) ? shownRef.current : timeless() ? [] : view());
  const showVolume = () => (!timeless() || BRICKS.includes(typeRef.current)) && studiesRef.current.some((s) => s.key === VOLUME && !s.hidden);

  /** The bars on screen: the replay's slice while replaying, otherwise everything loaded. */
  const view = () => useReplay.getState().slice ?? bars.current;

  function paintPrice() {
    const s = csRef.current;
    const built = buildSeries(typeRef.current, view(), styleOf(darkRef.current, s), paramsOf(s));
    shownRef.current = built.candles;
    useChartBus.getState().setShown(built.candles);
    price.current?.setData(built.data as never);
    if (built.kagi) kagiPrim.current?.setData(built.kagi, { yang: s.up, yin: s.down, yangWidth: Math.max(2, s.lineWidth + 1), yinWidth: 1 });
    if (built.pnf) pnfPrim.current?.setData(built.pnf.cols, built.pnf.box, s.up, s.down);
    paintHlc();
  }
  // HLC area: the high–close band in the rising colour and the close–low band in the falling one.
  const hlc = useRef<{ series: ISeriesApi<SeriesType>; up: BandFillPrimitive; down: BandFillPrimitive } | null>(null);
  function paintHlc() {
    const series = price.current;
    if (!series || typeRef.current !== 'hlcarea') { hlc.current = null; return; }
    const s = csRef.current;
    const v = view();
    const up = v.map((b) => ({ time: b.t, upper: b.h, lower: b.c }));
    const down = v.map((b) => ({ time: b.t, upper: b.c, lower: b.l }));
    if (!hlc.current || hlc.current.series !== series) {
      const a = new BandFillPrimitive(up, tint(s.up, 0.28));
      const b = new BandFillPrimitive(down, tint(s.down, 0.28));
      series.attachPrimitive(a as never); series.attachPrimitive(b as never);
      hlc.current = { series, up: a, down: b };
    } else { hlc.current.up.setData(up); hlc.current.down.setData(down); }
  }
  function paintVolume() {
    const s = csRef.current;
    const on = showVolume();
    const list = on ? studyBars() : [];
    volume.current?.setData(list.map((b, i) => ({ time: b.t as UTCTimestamp, value: b.v, color: volColor(b, s, darkRef.current, list[i - 1]) })));
    const ma = on && s.volumeMa ? sma(list.map((b) => b.v), Math.max(2, s.volumeMaLength)) : [];
    volumeMa.current?.setData(on && s.volumeMa ? list.map((b, i) => (ma[i] == null ? { time: b.t as UTCTimestamp } : { time: b.t as UTCTimestamp, value: ma[i]! })) : []);
  }
  function rebuildStudies() {
    const r = renderer.current;
    const sb = studyBars();
    r?.rebuild(sb.length ? studiesRef.current.filter((s) => s.key !== VOLUME) : [], sb);
    script.current?.draw(useScript.getState().applied ? useScript.getState().result : null, view());
    applyPlacement();
    publishLegend(null);
    measurePanes();
  }
  /** TradingView's «Move to» and «Pin to scale», and the indicators' axis labels, after every rebuild. */
  function applyPlacement() {
    const c = chart.current; const r = renderer.current;
    if (!c || !r) return;
    const place = usePlacement.getState().byUid;
    const labels = csRef.current.indicatorLabels;
    const main = mainScale();
    let left = false; let right = false;
    const list = useStudies.getState().list;
    for (const row of r.legend(null)) {
      // The indicators area may keep the scale in the study's own style; this chart's choice comes first.
      const own = (list.find((x) => x.uid === row.uid) as { style?: { scale?: string } } | undefined)?.style?.scale;
      const p = { ...(own === 'left' || own === 'right' || own === 'none' ? { scale: own as 'left' | 'right' | 'none' } : {}), ...(place[row.uid] ?? {}) };
      studySeries(r, row.uid).forEach((series, k) => {
        try {
          const cur = series.getPane().paneIndex();
          if (p.pane === 'main' && row.pane > 0 && cur !== 0) series.moveToPane(0);
          else if (p.pane === 'new' && row.pane === 0 && cur === 0) series.moveToPane(k === 0 ? c.panes().length : studySeries(r, row.uid)[0]!.getPane().paneIndex());
          // An oscillator brought onto the price pane keeps its own (hidden) scale unless pinned.
          const scaleId = p.scale === 'left' || p.scale === 'right' ? p.scale
            : p.scale === 'none' || (p.pane === 'main' && row.pane > 0) ? `ovl-${row.uid}` : main;
          if (scaleId === 'left') left = true;
          if (scaleId === 'right') right = true;
          series.applyOptions({
            priceScaleId: scaleId,
            lastValueVisible: labels !== 'hidden' && series.seriesType() !== 'Histogram',
            title: labels === 'name-value' && k === 0 ? row.label.split(' ')[0] : '',
          });
          // «Scale price chart only»: an indicator over the price does not stretch the price scale.
          if (scaleId === main && series.getPane().paneIndex() === 0 && !wrapped.has(series)) {
            wrapped.add(series);
            const own = series.options().autoscaleInfoProvider as ((b: () => AutoscaleInfo | null) => AutoscaleInfo | null) | undefined;
            series.applyOptions({ autoscaleInfoProvider: (b: () => AutoscaleInfo | null) => (csRef.current.scalePriceOnly ? null : own ? own(b) : b()) });
          }
        } catch { /* series gone */ }
      });
    }
    // Panes a move left empty go away.
    const ps = c.panes();
    for (let i = ps.length - 1; i > 0; i--) { if (!ps[i]!.getSeries().length) { try { c.removePane(i); } catch { /* kept */ } } }
    scalesUsed.current = { left, right };
    applyScales();
  }
  const scalesUsed = useRef({ left: false, right: false });
  const wrapped = useRef(new WeakSet<object>()).current;
  const mainScale = () => (csRef.current.scalePlacement === 'left' ? 'left' : 'right');

  /** The legend rows with each row's actual pane (an indicator may have been moved). */
  function publishLegend(index: number | null) {
    const r = renderer.current;
    if (!r) { setLegendRows([]); return; }
    const rows = r.legend(index).map((row) => {
      const first = studySeries(r, row.uid)[0];
      let pane = row.pane;
      try { if (first) pane = first.getPane().paneIndex(); } catch { /* removed */ }
      return { ...row, pane };
    });
    setLegendRows(rows);
    useChartBus.getState().setStudyRows(rows);
  }

  // Where each pane starts and how tall it is, for its legend and its buttons.
  function measurePanes() {
    requestAnimationFrame(() => {
      const c = chart.current;
      const origin = host.current?.getBoundingClientRect();
      if (!c || !origin) return;
      const next = c.panes().map((pane) => {
        const el = pane.getHTMLElement();
        const r = el?.getBoundingClientRect();
        return { top: r ? r.top - origin.top : 0, height: r ? r.height : pane.getHeight() };
      });
      setPanes((prev) => (prev.length === next.length && prev.every((v, i) => Math.abs(v.top - next[i]!.top) < 0.5 && Math.abs(v.height - next[i]!.height) < 0.5) ? prev : next));
    });
  }

  function applyScales() {
    const c = chart.current;
    if (!c) return;
    const s = csRef.current;
    const b = useChartBus.getState();
    const left = s.scalePlacement === 'left';
    const cmp = useCompare.getState().symbols.length > 0 && s.compareMode === 'percent';
    const pct = b.scale === 'percent' || cmp;
    const mode = pct ? PriceScaleMode.Percentage : b.scale === 'log' ? PriceScaleMode.Logarithmic : b.scale === 'indexed' ? PriceScaleMode.IndexedTo100 : PriceScaleMode.Normal;
    const look = lookOf(darkRef.current, s);
    const common = { borderColor: look.lineSep, alignLabels: s.noOverlap, scaleMargins: { top: s.marginTop / 100, bottom: s.marginBottom / 100 }, textColor: look.text };
    const main = { ...common, mode, autoScale: b.autoScale || s.lockRatio, invertScale: b.invert, visible: true };
    const other = (used: boolean) => ({ ...common, visible: used, mode: PriceScaleMode.Normal, autoScale: true, invertScale: false });
    c.applyOptions({
      rightPriceScale: left ? other(scalesUsed.current.right) : main,
      leftPriceScale: left ? main : other(scalesUsed.current.left),
    });
    price.current?.applyOptions({ priceScaleId: left ? 'left' : 'right' });
  }

  // Create once.
  useEffect(() => {
    if (!host.current) return;
    const s = csRef.current;
    const look = lookOf(dark, s);
    const c = createChart(host.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: look.bg }, textColor: look.text, fontSize: s.scaleFontSize,
        fontFamily: "'IRANYekanX', system-ui, sans-serif", attributionLogo: false,
        panes: { separatorColor: look.lineSep, separatorHoverColor: 'rgba(212,166,74,0.4)', enableResize: true },
      },
      grid: { vertLines: { color: look.gridV, style: LineStyle.Dotted }, horzLines: { color: look.gridH, style: LineStyle.Dotted } },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: look.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: look.crossLabel },
        horzLine: { color: look.cross, style: LineStyle.Dashed, width: 1, labelBackgroundColor: look.crossLabel },
      },
      rightPriceScale: { borderColor: look.lineSep, scaleMargins: { top: s.marginTop / 100, bottom: s.marginBottom / 100 } },
      leftPriceScale: { borderColor: look.lineSep, visible: false },
      timeScale: { borderColor: look.lineSep, barSpacing: 6, rightOffset: s.rightOffset, timeVisible: true, secondsVisible: false, minBarSpacing: 0.5 },
      // Latin digits and English month names on the axes, whatever the browser's locale: market
      // figures are always Latin (owner's rule), and TradingView writes dates the same way.
      localization: { locale: 'en-US', dateFormat: "dd MMM ''yy", priceFormatter: (v: number) => formatPrice(v, digitsRef.current) },
    });
    chart.current = c;
    price.current = makePriceSeries(c);
    volume.current = c.addSeries(HistogramSeries, { priceScaleId: 'vol', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false });
    volumeMa.current = c.addSeries(LineSeries, { priceScaleId: 'vol', color: '#2962FF', lineWidth: 1, lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false });
    c.priceScale('vol').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    renderer.current = new StudyRenderer(c, () => price.current, () => digitsRef.current);
    script.current = new ScriptRenderer(c, () => price.current, () => drawing.current?.layer ?? null, () => renderer.current?.freePane ?? 1);

    c.subscribeCrosshairMove((param: MouseEventParams<Time>) => {
      if (!param.time) { setHoverIdx(null); useChartBus.getState().setHover(null); publishCompare(null); return; }
      const v = shownRef.current;
      const i = indexAt(v, param.time as number);
      setHoverIdx(i >= 0 ? i : null);
      useChartBus.getState().setHover(i >= 0 ? v[i]! : null);
      publishCompare(param.time as number);
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
      useChartBus.getState().setApi(null); useChartBus.getState().setSeries(null); c.remove(); chart.current = null; price.current = null; volume.current = null; volumeMa.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** A price series of the current type and look, with the primitives that type needs. */
  function makePriceSeries(c: IChartApi): ISeriesApi<SeriesType> {
    const s = csRef.current;
    const series = createPriceSeries(c, typeRef.current, styleOf(darkRef.current, s), fmt());
    kagiPrim.current = null; pnfPrim.current = null;
    if (typeRef.current === 'kagi') { kagiPrim.current = new KagiPrimitive({ yang: s.up, yin: s.down, yangWidth: 3, yinWidth: 1 }); series.attachPrimitive(kagiPrim.current); }
    if (typeRef.current === 'pnf') { pnfPrim.current = new PnfPrimitive(s.up, s.down); series.attachPrimitive(pnfPrim.current); }
    countdownPrim.current = new CountdownPrimitive();
    series.attachPrimitive(countdownPrim.current);
    // Auto-scale: Point & figure by its columns; «Lock price to bar ratio» holds price per bar fixed.
    series.applyOptions({
      autoscaleInfoProvider: (base: () => AutoscaleInfo | null) => {
        let info = base();
        const pnf = pnfPrim.current;
        if (pnf) {
          const r = c.timeScale().getVisibleLogicalRange();
          const range = r ? pnf.range(r.from, r.to) : null;
          if (range) info = { priceRange: range };
        }
        const st = csRef.current;
        if (!st.lockRatio || !info?.priceRange || !(st.lockRatioValue > 0)) return info;
        const h = c.panes()[0]?.getHeight() ?? 0;
        const spacing = c.timeScale().options().barSpacing;
        if (!h || !spacing) return info;
        const mid = (info.priceRange.minValue + info.priceRange.maxValue) / 2;
        const half = (h * st.lockRatioValue) / spacing / 2;
        return { ...info, priceRange: { minValue: mid - half, maxValue: mid + half } };
      },
      priceLineVisible: showsLine(s.lastPrice), lastValueVisible: showsValue(s.lastPrice),
      priceScaleId: s.scalePlacement === 'left' ? 'left' : 'right',
      title: s.symbolNameLabel ? usePrefs.getState().symbol : '',
    });
    useChartBus.getState().setSeries(series);
    return series;
  }

  // The canvas: background, grid, crosshair, axis text and lines, margins, scrolling — and the theme.
  const lookKey = [cs.bgType, cs.bgColor, cs.bgColor2, cs.vertGrid, cs.vertGridColor, cs.horzGrid, cs.horzGridColor, cs.crossColor, cs.crossWidth, cs.crossStyle, cs.crosshair, cs.scaleTextColor, cs.scaleFontSize, cs.scaleLineColor, cs.marginTop, cs.marginBottom, cs.rightOffset, cs.scrollScale, cs.noOverlap].join('|');
  useEffect(() => {
    const c = chart.current;
    if (!c) return;
    const s = csRef.current;
    const look = lookOf(dark, s);
    const cross = { color: look.cross, style: CROSS_STYLE[s.crossStyle] ?? LineStyle.Dashed, width: s.crossWidth as 1 | 2 | 3 | 4, labelBackgroundColor: look.crossLabel };
    c.applyOptions({
      layout: {
        background: look.gradient ? { type: ColorType.VerticalGradient, topColor: look.bg, bottomColor: look.bg2 } : { type: ColorType.Solid, color: look.bg },
        textColor: look.text, fontSize: s.scaleFontSize, panes: { separatorColor: look.lineSep },
      },
      grid: { vertLines: { visible: s.vertGrid, color: look.gridV }, horzLines: { visible: s.horzGrid, color: look.gridH } },
      crosshair: { mode: s.crosshair === 'magnet' ? CrosshairMode.Magnet : CrosshairMode.Normal, vertLine: cross, horzLine: cross },
      timeScale: { borderColor: look.lineSep, rightOffset: s.rightOffset },
      handleScroll: s.scrollScale, handleScale: s.scrollScale,
    });
    applyScales();
    paintVolume();
    measurePanes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dark, lookKey]);

  // Chart type and the series' look: a new price series in place of the old one; drawings,
  // markers and levels follow it.
  const styleKey = [chartType, cs.up, cs.down, cs.bodyVisible, cs.borderVisible, cs.borderUp, cs.borderDown, cs.wickVisible, cs.wickUp, cs.wickDown, cs.colorByPrevClose, cs.thinBars, cs.hollow, cs.lineColor, cs.lineWidth, cs.areaTop, cs.areaBottom].join('|');
  const first = useRef(true);
  useEffect(() => {
    const c = chart.current;
    if (!c || !price.current) return;
    if (first.current) { first.current = false; return; }
    c.removeSeries(price.current);
    hlc.current = null;
    // Point & figure's columns need room for their X and O; the other types TradingView's 6px.
    const spacing = c.timeScale().options().barSpacing;
    if (typeRef.current === 'pnf' && spacing < 12) c.timeScale().applyOptions({ barSpacing: 14 });
    else if (typeRef.current !== 'pnf' && spacing === 14) c.timeScale().applyOptions({ barSpacing: 6 });
    price.current = makePriceSeries(c);
    renderer.current?.resetPriceSeries();
    drawing.current?.setSeries(price.current);
    paintPrice();
    paintVolume();
    rebuildStudies();
    applyScales();
    setSeriesVer((v) => v + 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [styleKey]);

  // The inputs of the types built from the bars: a repaint, and the studies on the new bricks.
  const paramKey = JSON.stringify(paramsOf(cs));
  useEffect(() => {
    if (!bars.current.length || !DERIVED.includes(typeRef.current)) return;
    paintPrice(); paintVolume();
    if (BRICKS.includes(typeRef.current)) rebuildStudies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramKey]);

  // The last price's label and line, the symbol name beside it, and the placement of the scale.
  useEffect(() => {
    price.current?.applyOptions({ priceLineVisible: showsLine(cs.lastPrice), title: cs.symbolNameLabel ? symbol : '' });
  }, [cs.lastPrice, cs.symbolNameLabel, symbol, seriesVer]);
  const scale = useChartBus((b) => b.scale);
  const autoScale = useChartBus((b) => b.autoScale);
  const invert = useChartBus((b) => b.invert);
  const compare = useCompare((s) => s.symbols);
  useEffect(() => { applyScales(); applyPlacement(); measurePanes(); },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [scale, autoScale, invert, compare.length, cs.compareMode, cs.scalePlacement, cs.lockRatio, cs.lockRatioValue, cs.indicatorLabels, cs.scalePriceOnly, seriesVer]);

  // Volume and its moving average follow their switches.
  useEffect(() => { paintVolume(); },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cs.volumeMa, cs.volumeMaLength]);

  // Time left in the forming bar, under the last price on the axis, as TradingView shows it: on
  // every time-based interval (weeks and months by the calendar), tinted as the close nears. A
  // series whose own label would be invisible (hollow or hidden bodies) gets its label drawn here.
  const lastLabel = useRef<() => void>(() => undefined);
  useEffect(() => {
    const prim = countdownPrim.current;
    const series = price.current;
    if (!prim || !series) return;
    const mark = (text: string) => wrap.current?.setAttribute('data-countdown', text);
    const type = typeRef.current;
    const candleLike = ['candles', 'heikin', 'renko', 'range', 'linebreak', 'volcandles'].includes(type);
    const own = showsValue(cs.lastPrice) && (type === 'hollow' || (candleLike && (!cs.bodyVisible || cs.hollow)));
    series.applyOptions({ lastValueVisible: showsValue(cs.lastPrice) && !own });
    const counting = cs.countdown && !timeless() && showsValue(cs.lastPrice);
    const tf = ({ '1W': 'W1', '1M': 'MN' } as Record<string, string>)[interval];
    const secs = intervalSeconds(interval);
    const tick = () => {
      const list = view();
      const lastBar = list[list.length - 1];
      if (!lastBar || (!counting && !own)) { prim.set({ text: '', price: null, back: '' }); mark(''); return; }
      const s = csRef.current;
      const prev = list[list.length - 2];
      const byPrev = s.colorByPrevClose || type === 'hollow' || ['line', 'area', 'baseline', 'step', 'linemarkers', 'hlcarea', 'columns'].includes(type);
      const rising = byPrev ? lastBar.c >= (prev?.c ?? lastBar.o) : lastBar.c >= lastBar.o;
      const lineType = ['line', 'area', 'step', 'linemarkers'].includes(type);
      const colour = lineType ? s.lineColor : rising ? s.up : s.down;
      if (own) series.applyOptions({ priceLineColor: colour });
      let text = '';
      let warn: string | null = null;
      if (counting && !useReplay.getState().slice) {
        const now = Math.floor(Date.now() / 1000);
        const left = tf ? secondsToClose(tf, now, zoneOf(usePrefs.getState().timezone)) : secs - (now % secs) || secs;
        const d = Math.floor(left / 86400); const h = Math.floor((left % 86400) / 3600); const m = Math.floor((left % 3600) / 60); const x = left % 60;
        const pad = (n: number) => String(n).padStart(2, '0');
        text = d ? `${d}d ${pad(h)}:${pad(m)}` : h ? `${pad(h)}:${pad(m)}:${pad(x)}` : `${pad(m)}:${pad(x)}`;
        warn = countdownTint(left);
      }
      const lastC = shownRef.current[shownRef.current.length - 1]?.c ?? lastBar.c;
      prim.set({ text, price: lastC, back: warn ?? colour, labelBack: colour, fontSize: s.scaleFontSize, label: own ? formatPrice(lastC, digitsRef.current) : '' });
      mark(text);
    };
    lastLabel.current = own ? tick : () => undefined;
    tick();
    const id = setInterval(tick, 1000);
    return () => { clearInterval(id); lastLabel.current = () => undefined; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cs.countdown, cs.lastPrice, cs.bodyVisible, cs.hollow, interval, chartType, seriesVer, digits]);

  // Price precision follows the instrument, or the settings' override.
  useEffect(() => {
    price.current?.applyOptions({ priceFormat: { type: 'price', ...fmt() } });
    chart.current?.applyOptions({ localization: { priceFormatter: (v: number) => formatPrice(v, digitsRef.current) } });
    drawing.current?.layer.setDigits(digits);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [digits]);

  // Indicators: rebuilt when the list or an input changes, or when one is moved or pinned.
  const placement = usePlacement((p) => p.byUid);
  useEffect(() => {
    paintVolume();
    if (bars.current.length) rebuildStudies();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studies, placement]);

  // NamaScript: run on the bars on screen (the replay's slice while replaying) when applied,
  // re-run when asked or when the bars change.
  const scriptApplied = useScript((s) => s.applied);
  const scriptRun = useScript((s) => s.runSeq);
  const [barsVersion, setBarsVersion] = useState(0);
  const replaySlice = useReplay((r) => r.slice);
  const replayStep = replaySlice?.length ?? 0;
  useEffect(() => {
    if (!scriptApplied) { script.current?.clear(); measurePanes(); return; }
    const list = view();
    if (!list.length) return;
    let alive = true;
    const st = useScript.getState();
    st.setRunning(true);
    void runScript(st.source, list, st.inputs).then((r) => {
      if (!alive) return;
      useScript.getState().setResult(r);
      useScript.getState().setRunning(false);
      script.current?.draw(r.ok ? r : null, list);
      measurePanes();
    });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scriptApplied, scriptRun, barsVersion, replayStep]);

  // The symbol and interval behind the bars, faint, as TradingView's watermark.
  useEffect(() => {
    const c = chart.current;
    if (!c || !cs.watermark) return;
    const look = lookOf(dark, cs);
    const color = tint(look.watermark.startsWith('#') ? look.watermark : '#787B86', Math.max(0.02, Math.min(1, cs.watermarkOpacity / 100)));
    const wm = createTextWatermark(c.panes()[0]!, { horzAlign: 'center', vertAlign: 'center', lines: [{ text: `${symbol}, ${intervalLabel(interval)}`, color, fontSize: 64, fontFamily: "'IRANYekanX', sans-serif", fontStyle: '' }] });
    return () => { try { wm.detach(); } catch { /* chart gone */ } };
  }, [cs.watermark, cs.watermarkOpacity, cs.watermarkColor, symbol, interval, dark, cs]);

  // Replay: repaint from the slice as it grows; back to everything on exit.
  useEffect(() => {
    if (!chart.current) return;
    paintPrice(); paintVolume();
    renderer.current?.refresh(studyBars());
    publishLegend(null);
    if (!replaySlice) chart.current.timeScale().scrollToRealTime();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [replaySlice]);

  // Time zone and the time scale's look (day of week, date format, 12/24 hours).
  const timezone = usePrefs((s) => s.timezone);
  useEffect(() => { chart.current?.applyOptions(timeOptions(timezone, interval, { hour12: cs.hour12, dayOfWeek: cs.dayOfWeek, dateFormat: cs.dateFormat })); }, [timezone, interval, cs.hour12, cs.dayOfWeek, cs.dateFormat]);

  // Compared symbols: a line each, same interval, live from the price feed — on its own hidden
  // scale, or in percent with the price when the axis (or the settings) say so.
  const cmpHidden = useCompare((s) => s.hidden);
  const cmpColors = useCompare((s) => s.colors);
  const cmpSeries = useRef<Map<string, { series: ISeriesApi<'Line'>; data: { t: number; v: number }[] }>>(new Map());
  const [cmpVer, setCmpVer] = useState(0);
  useEffect(() => {
    const c = chart.current;
    if (!c) return;
    const made = new Map<string, { series: ISeriesApi<'Line'>; data: { t: number; v: number }[] }>();
    const offs: (() => void)[] = [];
    let alive = true;
    compare.forEach((sym) => {
      const st = useCompare.getState();
      const s = c.addSeries(LineSeries, { color: compareColor(st, sym), lineWidth: 2, priceLineVisible: false, lastValueVisible: true, crosshairMarkerVisible: false, visible: !st.hidden[sym] });
      const entry = { series: s, data: [] as { t: number; v: number }[] };
      made.set(sym, entry);
      void feed.history(sym, interval).then(({ bars: data }) => {
        if (!alive) return;
        entry.data = data.map((b) => ({ t: b.t, v: b.c }));
        s.setData(data.map((b) => ({ time: b.t as UTCTimestamp, value: b.c })));
        setCmpVer((v) => v + 1);
        offs.push(onPrice(sym, (px) => {
          const tail = entry.data[entry.data.length - 1];
          if (!tail) return;
          tail.v = px;
          s.update({ time: tail.t as UTCTimestamp, value: px });
          if (useChartBus.getState().hover == null) publishCompare(null);
        }));
      }).catch(() => undefined);
    });
    cmpSeries.current = made;
    return () => { alive = false; offs.forEach((f) => f()); made.forEach(({ series }) => { try { c.removeSeries(series); } catch { /* gone */ } }); cmpSeries.current = new Map(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compare, interval, symbol]);
  useEffect(() => {
    const pct = scale === 'percent' || cs.compareMode === 'percent';
    cmpSeries.current.forEach(({ series }, sym) => {
      series.applyOptions({
        priceScaleId: pct ? mainScale() : `cmp-${sym}`, visible: !cmpHidden[sym], color: compareColor(useCompare.getState(), sym),
        autoscaleInfoProvider: (b: () => AutoscaleInfo | null) => (pct && csRef.current.scalePriceOnly ? null : b()),
      });
      if (!pct) { try { chart.current?.priceScale(`cmp-${sym}`).applyOptions({ scaleMargins: { top: cs.marginTop / 100, bottom: cs.marginBottom / 100 } }); } catch { /* not yet */ } }
    });
    publishCompare(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compare, cmpHidden, cmpColors, scale, cs.compareMode, cs.scalePlacement, cs.scalePriceOnly, cmpVer]);
  const [compareRows, setCompareRows] = useState<CompareRow[]>([]);
  function publishCompare(time: number | null) {
    const st = useCompare.getState();
    const rows: CompareRow[] = st.symbols.map((sym) => {
      const e = cmpSeries.current.get(sym);
      const d = e?.data ?? [];
      let i = d.length - 1;
      if (time != null) { i = -1; for (let k = d.length - 1; k >= 0; k--) { if (d[k]!.t <= time) { i = k; break; } } }
      const v = i >= 0 ? d[i]!.v : null;
      const p = i > 0 ? d[i - 1]!.v : null;
      return { symbol: sym, color: compareColor(st, sym), value: v, change: v != null && p ? ((v - p) / p) * 100 : null, hidden: Boolean(st.hidden[sym]) };
    });
    setCompareRows(rows);
    useChartBus.getState().setCompareRows(rows);
  }

  async function loadOlder() {
    const firstBar = bars.current[0];
    if (!firstBar) return;
    loadingOlder.current = true;
    const key = `${symbol}|${interval}`;
    try {
      const r = await feed.history(symbol, interval, firstBar.t);
      if (`${usePrefs.getState().symbol}|${usePrefs.getState().interval}` !== key) return;
      const older = r.bars.filter((b) => b.t < firstBar.t);
      // TradingView keeps scrolling back; the old Pro-Chart stopped at 6,000 bars to stay quick.
      more.current = r.more && older.length > 0 && bars.current.length + older.length < 6000;
      if (!older.length) return;
      const ts = chart.current?.timeScale();
      const range = ts?.getVisibleLogicalRange();
      const before = shownRef.current.length;
      bars.current = [...older, ...bars.current];
      paintPrice(); paintVolume();
      renderer.current?.refresh(studyBars());
      const added = shownRef.current.length - before;
      if (ts && range) ts.setVisibleLogicalRange({ from: range.from + added, to: range.to + added });
      useChartBus.getState().setBars(bars.current);
      drawing.current?.layer.setCandles(bars.current);
      if (BRICKS.includes(typeRef.current)) rebuildStudies();
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
    let derivedTimer: ReturnType<typeof setTimeout> | null = null;
    setState('loading'); setHoverIdx(null);
    more.current = false;
    feed.history(symbol, interval).then(({ bars: data, more: hasMore }) => {
      if (!alive) return;
      bars.current = data;
      more.current = hasMore;
      paintPrice(); paintVolume();
      const c = chart.current;
      c?.timeScale().applyOptions({ barSpacing: typeRef.current === 'pnf' ? 14 : 6, rightOffset: csRef.current.rightOffset });
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
      publishCompare(null);
      stop = feed.live(symbol, interval, data[data.length - 1], (bar) => {
        const list = bars.current;
        const tail = list[list.length - 1];
        if (tail && bar.t < tail.t) return;
        const fresh = !tail || bar.t !== tail.t;
        if (tail && bar.t === tail.t) list[list.length - 1] = bar; else list.push(bar);
        if (useReplay.getState().slice) return;
        const s = csRef.current;
        const point = livePoint(typeRef.current, bar, list[list.length - 2], styleOf(darkRef.current, s));
        if (point) price.current?.update(point as never);
        else if (!derivedTimer) derivedTimer = setTimeout(() => { derivedTimer = null; paintPrice(); if (BRICKS.includes(typeRef.current)) paintVolume(); }, fresh ? 0 : 250);
        if (showVolume() && !BRICKS.includes(typeRef.current)) volume.current?.update({ time: bar.t as UTCTimestamp, value: bar.v, color: volColor(bar, s, darkRef.current, list[list.length - 2]) });
        if (fresh && s.volumeMa) paintVolume();
        setLast({ ...bar });
        lastLabel.current();
        // Indicators follow the forming bar at most once a second.
        if (!studyTimer) studyTimer = setTimeout(() => { studyTimer = null; renderer.current?.refresh(studyBars()); if (useChartBus.getState().hover == null) publishLegend(null); }, 1000);
      });
    }).catch(() => { if (alive) setState('error'); });
    return () => { alive = false; stop?.(); if (studyTimer) clearTimeout(studyTimer); if (derivedTimer) clearTimeout(derivedTimer); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, interval, attempt, feed]);

  // Legend values under the crosshair; pane edges move when a separator is dragged.
  useEffect(() => {
    publishLegend(hoverIdx);
    measurePanes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hoverIdx]);

  // Panes: one maximised (the others folded away), or some collapsed to their legend.
  useEffect(() => {
    const c = chart.current;
    if (!c) return;
    const ps = c.panes();
    const max = paneMode.max != null && paneMode.max < ps.length ? paneMode.max : null;
    ps.forEach((p, i) => {
      const base = i === 0 ? 3 : 1;
      const f = max != null ? (i === max ? 1 : 0.001) : paneMode.collapsed.includes(i) ? 0.04 : base;
      try { p.setStretchFactor(f); } catch { /* older pane */ }
    });
    measurePanes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paneMode, legendRows.length]);
  useEffect(() => { setPaneMode({ max: null, collapsed: [] }); }, [studies.length]);
  // The legend's «⋯ → Maximize / Collapse pane».
  useEffect(() => {
    const on = (e: Event) => {
      const d = (e as CustomEvent<{ op: 'max' | 'collapse'; pane: number }>).detail;
      if (!d) return;
      setPaneMode((m) => (d.op === 'max'
        ? { max: m.max === d.pane ? null : d.pane, collapsed: [] }
        : { max: null, collapsed: m.collapsed.includes(d.pane) ? m.collapsed.filter((x) => x !== d.pane) : [...m.collapsed, d.pane] }));
    };
    window.addEventListener('pc:pane', on);
    return () => window.removeEventListener('pc:pane', on);
  }, []);
  // A trade setup belongs to its symbol.
  const setup = useOverlay((o) => o.setup);
  useEffect(() => { if (setup && setup.symbol !== symbol) useOverlay.getState().clear(); }, [symbol, setup]);

  const onScreen = shownRef.current;
  const shown = hoverIdx != null ? onScreen[hoverIdx] ?? last : onScreen[onScreen.length - 1] ?? last;
  const shownIdx = hoverIdx ?? onScreen.length - 1;
  const prev = shownIdx > 0 ? onScreen[shownIdx - 1] : undefined;

  // Right-click: a drawing gets its own menu (the drawing layer's), the price axis and the time
  // axis theirs, the chart the rest.
  const onContextMenu = (e: React.MouseEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest('.legend, .replay-bar, .nav-buttons, .pane-btns, .axis-plus, .alert-handle')) return;
    e.preventDefault();
    const c = chart.current;
    const r = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - r.left; const y = e.clientY - r.top;
    const layer = useChartBus.getState().drawingLayer;
    const index = (layer as unknown as { hitTest?(x: number, y: number): number } | null)?.hitTest?.(x, y) ?? -1;
    if (index >= 0) { window.dispatchEvent(new CustomEvent('pc:drawing-menu', { detail: { clientX: e.clientX, clientY: e.clientY, index } })); return; }
    if (c) {
      const rightW = c.priceScale('right').width();
      const leftW = c.priceScale('left').width();
      const timeH = c.timeScale().height();
      if (x > r.width - rightW || (leftW > 0 && x < leftW)) { setMenuAt({ kind: 'price', x: e.clientX, y: e.clientY }); return; }
      if (y > r.height - timeH) { setMenuAt({ kind: 'time', x: e.clientX, y: e.clientY }); return; }
    }
    const px = price.current?.coordinateToPrice(y);
    const tm = c?.timeScale().coordinateToTime(x);
    setMenuAt({ kind: 'chart', x: e.clientX, y: e.clientY, price: typeof px === 'number' ? px : null, time: typeof tm === 'number' ? tm : null });
  };

  // A double click on a pane maximises it (and back), as on TradingView.
  const onDoubleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const c = chart.current;
    if (!c || c.panes().length < 2 || (e.target as HTMLElement).closest('.legend, .replay-bar, .nav-buttons, .pane-btns, .axis-plus')) return;
    const r = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - r.left; const y = e.clientY - r.top;
    if (x > r.width - c.priceScale('right').width() || y > r.height - c.timeScale().height()) return;
    const layer = useChartBus.getState().drawingLayer;
    if (((layer as unknown as { hitTest?(x: number, y: number): number } | null)?.hitTest?.(x, y) ?? -1) >= 0) return;
    const i = panes.findIndex((p) => y >= p.top && y < p.top + p.height);
    if (i < 0) return;
    setPaneMode((m) => ({ max: m.max === i ? null : i, collapsed: [] }));
  };

  const sessionBreaks = cs.sessionBreaks;
  const autoOn = (cs.autoDivergence || cs.autoHarmonic || cs.autoGaps) && !timeless();
  const intraday = intervalSeconds(interval) < 86400;
  const breakColor = cs.sessionBreakColor || (dark ? 'rgba(255,255,255,0.18)' : 'rgba(19,23,34,0.18)');
  const api = chart.current;
  const series = price.current;

  return (
    <div ref={wrap} className={`ws-chart nav-${cs.navButtons} panebtn-${cs.paneButtons}`} onContextMenu={onContextMenu} onDoubleClick={onDoubleClick} data-type={chartType}>
      <div ref={host} className="chart-host" />
      {/* The overlay canvases sit beside the chart's own (a snapshot lays every canvas of the cell over the chart's picture). */}
      {sessionBreaks ? <SessionBreaks api={api} bars={bars.current} intraday={intraday} timezone={timezone} version={barsVersion + seriesVer} color={breakColor} width={cs.sessionBreakWidth} style={cs.sessionBreakStyle} /> : null}
      {cs.sessionsOn && intraday && !timeless() ? <SessionShading api={api} bars={bars.current} sessions={cs.sessions} overlaps={cs.sessionOverlaps} version={barsVersion + seriesVer} fa={lang === 'fa'} dark={dark} /> : null}
      {autoOn ? <AutoOverlay api={api} series={series} bars={view()} version={barsVersion + (replaySlice?.length ?? 0) + seriesVer} flags={{ divergence: cs.autoDivergence, harmonic: cs.autoHarmonic, gaps: cs.autoGaps }} fa={lang === 'fa'} /> : null}
      {cs.volumeProfile && !TIMELESS.includes(chartType) ? <VolumeProfile api={api} series={series} bars={view()} version={barsVersion + (replaySlice?.length ?? 0) + seriesVer} rows={cs.vpRows} valueArea={cs.vpValueArea} up={cs.up} down={cs.down} dark={dark} /> : null}
      <SetupZones api={api} series={series} version={seriesVer} bars={bars.current} />
      <EventMarks api={api} series={series} bars={bars.current} version={barsVersion + seriesVer} />
      <canvas ref={drawCanvas} className="draw-layer" />
      <PriceLines series={series} api={api} version={seriesVer} bars={view()} barsVersion={barsVersion} digits={digits} />
      <Legend symbol={symbol} displayName={displayName} interval={interval} chartType={chartType} digits={digits} shown={shown ?? null} prev={prev}
        rows={legendRows.filter((r) => r.pane === 0)} compareRows={compareRows} onStudySettings={onStudySettings} hovering={hoverIdx != null} />
      {legendRows.filter((r) => r.pane > 0).map((r) => (
        <PaneLegend key={r.uid} row={r} top={(panes[r.pane]?.top ?? 0) + 4} digits={digits} onStudySettings={onStudySettings} paneCount={panes.length}
          maximised={paneMode.max === r.pane} collapsed={paneMode.collapsed.includes(r.pane)} />
      ))}
      <PaneControls panes={panes} mode={paneMode} rows={legendRows}
        onMaximise={(i) => setPaneMode((m) => ({ max: m.max === i ? null : i, collapsed: [] }))}
        onCollapse={(i) => setPaneMode((m) => ({ max: null, collapsed: m.collapsed.includes(i) ? m.collapsed.filter((x) => x !== i) : [...m.collapsed, i] }))} />
      <NavButtons api={api} paneHeight={panes[0]?.height ?? 0} />
      <AxisPlus api={api} series={series} digits={digits} onAlertAt={onAlertAt} />
      <AxisChips api={api} symbol={symbol} paneHeight={panes[0]?.height ?? 0} />
      <ReplayBar />
      <DrawingStyleBar />
      <TextEditor />
      <SettingsHost />
      {menuAt?.kind === 'chart' ? <ChartContextMenu at={menuAt} onClose={() => setMenuAt(null)} onAlertAt={onAlertAt} /> : null}
      {menuAt?.kind === 'price' ? <PriceScaleMenu at={menuAt} onClose={() => setMenuAt(null)} /> : null}
      {menuAt?.kind === 'time' ? <TimeScaleMenu at={menuAt} onClose={() => setMenuAt(null)} /> : null}
      {state === 'loading' ? <div className="chart-state fa">{t('loading')}…</div> : null}
      {state === 'error' ? (
        <div className="chart-state fa">{t('chartFailed')} <button className="link" onClick={() => setAttempt((a) => a + 1)}>{t('retry')}</button></div>
      ) : null}
    </div>
  );
}
