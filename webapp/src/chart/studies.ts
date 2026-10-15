// Draws the engine's indicators (engine/indicators.js REGISTRY) and the NamaScript on the chart
// with lightweight-charts: overlays on the price pane, oscillators each in its own pane below, as
// TradingView stacks them, in the list's order. Every output shape the registry produces is mapped:
// line/signal/signal2, histograms (by sign, by palette, MACD's four shades), multi-line bands with
// their fill, Ichimoku's two-coloured cloud, guide levels with the shaded zone between them, a fixed
// range, markers and levels. Each study's style applies: per-plot colour, opacity, width, line
// style, plot type (line, step line, histogram, area, columns, circles, cross) and visibility, the
// fill, editable levels, precision, the scale it is pinned to, price-scale labels and status-line
// values; so do its Visibility (timeframes), its own timeframe (with or without waiting for that
// timeframe's bars to close) and a source taken from another study's output.
import {
  AreaSeries, BarSeries, CandlestickSeries, HistogramSeries, LineSeries, LineStyle, LineType, createSeriesMarkers,
  type IChartApi, type IPriceLine, type ISeriesApi, type ISeriesMarkersPluginApi, type ISeriesPrimitive, type SeriesMarker, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts';
import type { IndicatorColumns, IndicatorOutput, Values } from '../engine/indicators.js';
import { STUDIES as REGISTRY } from '../engine/indicators_ext_c.js';
import { BandFillPrimitive } from '../engine/bandFill.js';
import { patternName } from '../engine/candlePatterns.js';
import type { Candle } from '../data/types';
import { SCRIPT, visibleOn, type LevelStyle, type LineStyleName, type PlotStyle, type PlotType, type Study } from '../state/studies';
import { usePrefs } from '../state/prefs';
import { useScript, scriptTitle } from '../state/script';
import { intervalLabel, intervalSeconds } from '../data/intervals';
import { plotName } from './studyMeta';
import { CrossPaint, ScriptPaint, scriptIsOverlay, scriptMarkers } from './scriptRenderer';
import { ProfilePaint } from './profilePaint';
import { create } from 'zustand';
import { useStudies } from '../state/studies';
import { feed } from '../data/feed';

// TradingView's default colours for the studies people add first; the rest keep the engine's.
const TV_COLOR: Record<string, string> = {
  ma: '#2962FF', ema: '#2962FF', wma: '#2962FF', hma: '#2962FF', vwap: '#2962FF', rsi: '#7E57C2', stoch: '#2962FF',
  stochrsi: '#2962FF', macd: '#2962FF', atr: '#B71C1C', cci: '#2962FF', willr: '#7E57C2', mfi: '#7E57C2', adx: '#FF5252',
  obv: '#2962FF', cmf: '#43A047', aroon: '#FB8C00', mom: '#2962FF', roc: '#2962FF', supertrend: '#089981',
};
const SIGNAL = '#FF6D00';
export const GUIDE = '#787B86';
const UP = '#089981';
const DOWN = '#F23645';
const LS: Record<LineStyleName, LineStyle> = { solid: LineStyle.Solid, dashed: LineStyle.Dashed, dotted: LineStyle.Dotted };

export interface StudyLegendRow {
  uid: string; key: string; label: string; colors: string[]; values: (number | null)[]; pane: number; hidden: boolean;
  /** The short name and the inputs, apart (TradingView's status line can show either). */
  name: string; args: string;
  /** The names of the plots whose values are listed, in the same order as `values`. */
  plotNames: string[];
  /** Decimal places for the values. */
  precision: number;
  /** False when the reader turned the values off in the status line. */
  statusValues: boolean;
  /** Not shown on this timeframe (its Visibility settings). */
  tfOff: boolean;
  /** The NamaScript pseudo-study. */
  script: boolean;
}

/** One output of a study, as the settings dialog lists it. */
export interface PlotInfo { name: string; color: string; hist: boolean; dashed: boolean; width: number }

interface Item { values: Values; color: string; hist?: boolean; dashed?: boolean; width?: number; connect?: boolean; colorFor?: (v: number | null, k: number) => string; name: string }

interface Rendered {
  study: Study;
  pane: number;
  series: ISeriesApi<SeriesType>[];
  /** The series of each item (null when the plot is hidden). */
  plotSeries: (ISeriesApi<SeriesType> | null)[];
  items: Item[];
  priceLines: { series: ISeriesApi<SeriesType>; line: IPriceLine }[];
  prims: { series: ISeriesApi<SeriesType>; prim: ISeriesPrimitive<Time> }[];
  markers: SeriesMarker<Time>[];
  precision: number;
  tfOff: boolean;
  script: boolean;
  profile?: ProfilePaint;
}

export const columns = (bars: Candle[]): IndicatorColumns => ({
  open: bars.map((b) => b.o), high: bars.map((b) => b.h), low: bars.map((b) => b.l),
  close: bars.map((b) => b.c), volume: bars.map((b) => b.v), time: bars.map((b) => b.t),
});

/** #rrggbb or rgba() at an opacity (0–1), as a CSS colour. */
export function withAlpha(color: string, opacity: number | undefined): string {
  if (opacity == null || opacity >= 1) return color;
  const hex = /^#([0-9a-f]{6})([0-9a-f]{2})?$/i.exec(color);
  if (hex) { const n = parseInt(hex[1]!, 16); return `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, ${Math.max(0, opacity)})`; }
  const short = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(color);
  if (short) return withAlpha(`#${short[1]}${short[1]}${short[2]}${short[2]}${short[3]}${short[3]}`, opacity);
  const rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(color);
  if (rgba) return `rgba(${rgba[1]}, ${rgba[2]}, ${rgba[3]}, ${Number(rgba[4] ?? 1) * opacity})`;
  return color;
}

/** A CSS colour as #rrggbb plus its opacity (for the colour pickers). */
export function splitColor(color: string): { hex: string; opacity: number } {
  const hex = /^#([0-9a-f]{6})$/i.exec(color);
  if (hex) return { hex: color.toUpperCase(), opacity: 1 };
  const short = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(color);
  if (short) return { hex: `#${short[1]}${short[1]}${short[2]}${short[2]}${short[3]}${short[3]}`.toUpperCase(), opacity: 1 };
  const rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i.exec(color);
  if (rgba) {
    const h = (v: string) => Math.round(Number(v)).toString(16).padStart(2, '0');
    return { hex: `#${h(rgba[1]!)}${h(rgba[2]!)}${h(rgba[3]!)}`.toUpperCase(), opacity: Number(rgba[4] ?? 1) };
  }
  return { hex: '#2962FF', opacity: 1 };
}

// ── Timeframes of their own ──────────────────────────────────────────────────────────────────────
interface Buckets { cols: IndicatorColumns; map: number[]; last: boolean[]; closed: boolean[] }

function bucketKey(tf: string, t: number): number {
  if (tf === '1W') return Math.floor((t / 86400 + 3) / 7);
  if (tf === '1M') { const d = new Date(t * 1000); return d.getUTCFullYear() * 12 + d.getUTCMonth(); }
  const s = intervalSeconds(tf);
  return Math.floor(t / s);
}
function bucketEnd(tf: string, key: number): number {
  if (tf === '1W') return ((key + 1) * 7 - 3) * 86400;
  if (tf === '1M') return Date.UTC(Math.floor((key + 1) / 12), (key + 1) % 12, 1) / 1000;
  return (key + 1) * intervalSeconds(tf);
}

/** The chart's bars gathered into a higher timeframe's, with the way back. */
export function toTimeframe(c: IndicatorColumns, tf: string, chartSeconds: number): Buckets {
  const o: number[] = []; const h: number[] = []; const l: number[] = []; const cl: number[] = []; const v: number[] = []; const t: number[] = [];
  const map: number[] = []; const keys: number[] = [];
  for (let i = 0; i < c.close.length; i++) {
    const k = bucketKey(tf, c.time[i]!);
    if (keys[keys.length - 1] !== k) { keys.push(k); o.push(c.open[i]!); h.push(c.high[i]!); l.push(c.low[i]!); cl.push(c.close[i]!); v.push(c.volume[i] ?? 0); t.push(c.time[i]!); }
    else { const j = keys.length - 1; h[j] = Math.max(h[j]!, c.high[i]!); l[j] = Math.min(l[j]!, c.low[i]!); cl[j] = c.close[i]!; v[j] = v[j]! + (c.volume[i] ?? 0); }
    map.push(keys.length - 1);
  }
  const last = map.map((k, i) => i === map.length - 1 || map[i + 1] !== k);
  const closed = keys.map((k, j) => j < keys.length - 1 || (c.time[c.time.length - 1]! + chartSeconds >= bucketEnd(tf, k)));
  return { cols: { open: o, high: h, low: l, close: cl, volume: v, time: t }, map, last, closed };
}

/** A higher timeframe's values back on the chart's bars, without looking ahead. */
export function fromTimeframe(values: Values, b: Buckets, wait: boolean): Values {
  return b.map.map((k, i) => {
    if (wait) return b.last[i] && b.closed[k] ? values[k] ?? null : null;
    if (b.last[i]) return values[k] ?? null;
    return k > 0 ? values[k - 1] ?? null : null;
  });
}

function mapOutput(out: IndicatorOutput, f: (v: Values) => Values): IndicatorOutput {
  const m: IndicatorOutput = { ...out };
  for (const k of ['line', 'signal', 'signal2', 'hist', 'upper', 'basis', 'lower'] as const) if (out[k]) m[k] = f(out[k]!);
  if (out.hists) m.hists = out.hists.map((x) => ({ ...x, data: f(x.data) }));
  if (out.lines) m.lines = out.lines.map((x) => ({ ...x, data: f(x.data) }));
  return m;
}

/** A series with gaps filled from the nearest value (an indicator's warm-up), for use as a source. */
function filled(v: Values): number[] {
  const first = v.find((x) => x != null && Number.isFinite(x)) ?? 0;
  let prev = first as number;
  return v.map((x) => { if (x != null && Number.isFinite(x)) prev = x; return prev; });
}

/** The bars on screen (logical indices), for the studies that read them; the renderer keeps it. */
let visible: { from: number; to: number } | null = null;

/** Second symbols' bars (Correlation, Ratio, Spread), by symbol and interval. */
const others = new Map<string, Candle[] | 'loading' | 'failed'>();
function otherCloses(symbol: string, bars: Candle[], uid: string): (number | null)[] | null {
  const interval = usePrefs.getState().interval;
  const key = `${symbol.toUpperCase()}|${interval}`;
  const got = others.get(key);
  if (got === undefined) {
    others.set(key, 'loading');
    feed.history(symbol.toUpperCase(), interval).then(({ bars: b }) => { others.set(key, b); useStudies.getState().touch(uid); }).catch(() => others.set(key, 'failed'));
    return null;
  }
  if (!Array.isArray(got)) return null;
  const at = new Map(got.map((b) => [b.t, b.c]));
  let last: number | null = null;
  return bars.map((b) => { const v = at.get(b.t); if (v != null) last = v; return v ?? last; });
}

/** Tables the studies put over the chart (Performance, Seasonality), drawn by the studies host. */
export interface StudyTable { uid: string; title: string; rows: [string, string, (string | null)?][] }
export const useStudyTables = create<{ tables: StudyTable[] }>(() => ({ tables: [] }));

// ── Computing one study ──────────────────────────────────────────────────────────────────────
export function computeStudy(st: Study, bars: Candle[], cache: Map<string, Item[]>, all: Study[], seen: Set<string> = new Set()): { out: IndicatorOutput; items: Item[] } | null {
  const def = REGISTRY[st.key];
  if (!def || !bars.length) return null;
  seen.add(st.uid);
  const inputs: Record<string, unknown> = { ...def.inputs, ...st.inputs };
  let cols = columns(bars);
  let mask: boolean[] | null = null;
  // Indicator on indicator: the source is another study's output.
  const src = inputs.source;
  if (typeof src === 'string' && src.startsWith('study:')) {
    const [, uid, idx] = src.split(':');
    let items = cache.get(uid!);
    const other = all.find((x) => x.uid === uid);
    if (!items && other && !seen.has(other.uid)) items = computeStudy(other, bars, cache, all, seen)?.items;
    const values = items?.[Number(idx)]?.values;
    if (values) { cols = { ...cols, close: filled(values) }; mask = values.map((v) => v == null || !Number.isFinite(v)); }
    inputs.source = 'close';
  }
  if (def.visibleRange) { inputs.__from = visible?.from ?? 0; inputs.__to = visible?.to ?? bars.length - 1; }
  if (def.other) inputs.__other = otherCloses(String(inputs.symbol ?? ''), bars, st.uid) ?? undefined;
  let out: IndicatorOutput;
  const chartSec = intervalSeconds(usePrefs.getState().interval);
  const ownTf = Boolean(st.timeframe && intervalSeconds(st.timeframe) > chartSec);
  try {
    if (ownTf) {
      const b = toTimeframe(cols, st.timeframe!, chartSec);
      const raw = def.calc(b.cols, inputs);
      out = mapOutput(raw, (v) => fromTimeframe(v, b, st.waitClose !== false));
      if (raw.markers) out.markers = raw.markers.map((m) => { const k = b.cols.time.indexOf(m.time); const i = b.map.lastIndexOf(k); return { ...m, time: i >= 0 ? cols.time[i]! : m.time }; });
    } else out = def.calc(cols, inputs);
  } catch { return null; }
  if (mask) { const m = mask; out = mapOutput(out, (v) => v.map((x, i) => (m[i] ? null : x))); }
  const items = flatten(out, TV_COLOR[st.key] ?? def.color);
  if (ownTf && st.waitClose !== false) for (const it of items) it.connect = true;
  cache.set(st.uid, items);
  return { out, items };
}

function flatten(out: IndicatorOutput, main: string): Item[] {
  const list: Item[] = [];
  if (out.hist) {
    const h = out.hist;
    let colorFor: (v: number | null, k: number) => string;
    if (out.palette) colorFor = out.palette;
    else if (out.macd) colorFor = (v, k) => { const prev = k > 0 ? h[k - 1] : null; const rising = prev == null || (v ?? 0) >= prev; return (v ?? 0) >= 0 ? (rising ? '#26A69A' : '#B2DFDB') : (rising ? '#FFCDD2' : '#FF5252'); };
    else colorFor = (v) => ((v ?? 0) >= 0 ? UP : DOWN);
    list.push({ values: h, color: UP, hist: true, colorFor, name: 'Histogram' });
  }
  for (const x of out.hists ?? []) list.push({ values: x.data, color: x.color, hist: true, colorFor: () => x.color, name: 'Histogram' });
  if (out.upper && out.lower) {
    list.push({ values: out.upper, color: '#F23645', name: 'Upper' });
    if (out.basis) list.push({ values: out.basis, color: '#2962FF', name: 'Basis' });
    list.push({ values: out.lower, color: '#089981', name: 'Lower' });
  }
  if (out.line) list.push({ values: out.line, color: main, name: 'Plot' });
  if (out.signal) list.push({ values: out.signal, color: SIGNAL, name: 'Signal' });
  if (out.signal2) list.push({ values: out.signal2, color: out.signal2Color ?? '#787B86', name: 'Signal 2' });
  (out.lines ?? []).forEach((l, i) => list.push({ values: l.data, color: l.color, dashed: l.dashed, connect: l.connectNulls, name: l.name ?? `Plot ${i + 1}` }));
  return list;
}


/** The plots a study draws, for its Style tab (computed on the bars on screen). */
export function studyPlots(st: Study, bars: Candle[], all: Study[] = []): PlotInfo[] {
  if (st.key === SCRIPT) {
    const res = useScript.getState().result;
    return (res?.ok ? res.plots ?? [] : []).map((p) => ({ name: p.name, color: p.color, hist: false, dashed: p.style === 2, width: Math.max(1, Math.min(4, p.width || 1)) }));
  }
  const def = REGISTRY[st.key];
  if (!def) return [];
  const fa = usePrefs.getState().lang === 'fa';
  const r = computeStudy(st, bars, new Map(), all);
  const main = TV_COLOR[st.key] ?? def.color;
  return (r?.items ?? []).map((it, k) => ({ name: plotName(st.key, k, it.name, fa), color: it.color || main, hist: Boolean(it.hist), dashed: Boolean(it.dashed), width: it.width ?? 1 }));
}

/** The engine's guide levels and whether it shades between them, for the Style tab. */
export function studyGuides(st: Study, bars: Candle[]): { levels: number[]; zone: boolean; fill: string | null; fill2: string | null } {
  const def = REGISTRY[st.key];
  if (!def || !bars.length) return { levels: [], zone: false, fill: null, fill2: null };
  let out: IndicatorOutput;
  try { out = def.calc(columns(bars.slice(-300)), { ...def.inputs, ...st.inputs, source: 'close' }); } catch { return { levels: [], zone: false, fill: null, fill2: null }; }
  const band = out.upper && out.lower ? 'rgba(33, 150, 243, 0.08)' : out.cloud ? (st.key === 'ichimoku' ? 'rgba(67, 160, 71, 0.18)' : out.cloudColors?.[0] ?? 'rgba(33, 150, 243, 0.08)') : null;
  return { levels: out.guides ?? [], zone: Boolean(out.zone), fill: band, fill2: st.key === 'ichimoku' ? 'rgba(244, 67, 54, 0.18)' : null };
}


export class StudyRenderer {
  private rendered: Rendered[] = [];
  private markersApi: ISeriesMarkersPluginApi<Time> | null = null;
  private bars: Candle[] = [];
  private leftOn = false;
  private scriptOverlay = true;
  /** Each study's pane height (stretch factor), kept across rebuilds. */
  private paneFactor = new Map<string, number>();
  /** The first pane index no study uses (the chart view's first script runner used to go there). */
  freePane = 1;

  private rangeTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private chart: IChartApi, private price: () => ISeriesApi<SeriesType> | null, private digits: () => number) {
    // Studies that read the bars on screen (Visible Average Price, the visible-range profile) follow
    // the chart as it scrolls and zooms.
    chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (!range) return;
      visible = { from: Math.max(0, Math.ceil(range.from)), to: Math.floor(range.to) };
      if (!this.rendered.some((r) => REGISTRY[r.study.key]?.visibleRange)) return;
      if (this.rangeTimer) clearTimeout(this.rangeTimer);
      this.rangeTimer = setTimeout(() => { this.rangeTimer = null; this.refreshVisible(); }, 120);
    });
  }

  /** Recomputes the studies that read the bars on screen. */
  private refreshVisible(): void {
    const cache = new Map<string, Item[]>();
    const studies = this.rendered.map((r) => r.study);
    visible = visible && { from: Math.max(0, visible.from), to: Math.min(this.bars.length - 1, visible.to) };
    for (const r of this.rendered) {
      if (!REGISTRY[r.study.key]?.visibleRange || r.study.hidden || r.tfOff) continue;
      const res = computeStudy(r.study, this.bars, cache, studies);
      if (!res) continue;
      if (r.profile && res.out.profile) r.profile.setData(res.out.profile.segments);
      res.items.forEach((it, i) => { const sr = r.plotSeries[i]; if (sr && sr.seriesType() !== 'Histogram') sr.setData(this.lineData(this.bars, it.values, it.connect)); });
      r.items = res.items;
    }
  }

  /** Rebuilds every study's series from scratch (on add, remove, input change, symbol change). */
  rebuild(studies: Study[], bars: Candle[]): void {
    this.bars = bars;
    this.rememberPanes();
    this.dispose();
    this.tables = [];
    const cache = new Map<string, Item[]>();
    const interval = usePrefs.getState().interval;
    let nextPane = 1;
    let anyLeft = false;
    for (const st of studies) {
      const isScript = st.key === SCRIPT;
      const def = REGISTRY[st.key];
      if (!def && !isScript) continue;
      const tfOff = !visibleOn(st.visibility, interval);
      let overlay: boolean;
      let res = isScript ? useScript.getState().result : null;
      if (isScript) {
        if (res && !res.ok) res = null;
        if (res) this.scriptOverlay = scriptIsOverlay(res, bars);
        overlay = this.scriptOverlay;
      } else overlay = def!.pane === 'main';
      const pane = overlay ? 0 : nextPane++;
      if (pane > 0) this.ensurePane(pane, st.uid);
      const r: Rendered = { study: st, pane, series: [], plotSeries: [], items: [], priceLines: [], prims: [], markers: [], precision: 2, tfOff, script: isScript };
      r.precision = st.style?.precision ?? (overlay ? this.digits() : 2);
      this.rendered.push(r);
      if (isScript) {
        if (res) r.items = this.scriptItems(res, bars);
        if (!st.hidden && !tfOff && res) this.drawScript(r, res);
      } else {
        const items = computeStudy(st, bars, cache, studies);
        if (!items) continue;
        r.items = items.items;
        if (st.hidden || tfOff) continue;
        this.draw(r, items.out, TV_COLOR[st.key] ?? def!.color);
      }
      if (st.style?.scale === 'left') anyLeft = true;
    }
    this.freePane = nextPane;
    // Panes left over from studies that were removed go with them.
    const panes = this.chart.panes();
    for (let i = panes.length - 1; i >= nextPane; i--) {
      if (i > 0) { try { this.chart.removePane(i); } catch { /* pane still holds a series */ } }
    }
    this.restorePanes();
    if (anyLeft !== this.leftOn) { this.chart.applyOptions({ leftPriceScale: { visible: anyLeft, borderVisible: false } }); this.leftOn = anyLeft; }
    this.applyMarkers();
    useStudyTables.setState({ tables: this.tables });
  }

  private tables: StudyTable[] = [];

  /** Recomputes values for new or changed bars without recreating series. */
  refresh(bars: Candle[]): void {
    this.bars = bars;
    const cache = new Map<string, Item[]>();
    const studies = this.rendered.map((r) => r.study);
    for (const r of this.rendered) {
      if (r.script) continue;
      const items = computeStudy(r.study, bars, cache, studies);
      if (!items) continue;
      if (items.items.length !== r.items.length) continue;
      r.items = items.items;
      if (r.study.hidden || r.tfOff) continue;
      items.items.forEach((it, i) => {
        const s = r.plotSeries[i];
        if (!s) return;
        const ps = r.study.style?.plots?.[i];
        if (s.seriesType() === 'Histogram') s.setData(this.histData(bars, it.values, ps?.color ? () => withAlpha(ps.color!, ps.opacity) : it.colorFor ? (v, k) => withAlpha(it.colorFor!(v, k), ps?.opacity) : () => withAlpha(it.color, ps?.opacity)));
        else s.setData(this.lineData(bars, it.values, it.connect));
      });
    }
  }

  legend(index: number | null): StudyLegendRow[] {
    const fa = usePrefs.getState().lang === 'fa';
    return this.rendered.map((r) => {
      const i = index ?? this.bars.length - 1;
      const st = r.study;
      let name: string; let args: string;
      if (r.script) {
        const s = useScript.getState();
        name = scriptTitle(s.runCode) ?? s.tabs.find((t) => t.id === s.active)?.name ?? 'NamaScript';
        args = Object.values(s.inputs).filter((v) => typeof v === 'number' || typeof v === 'string').join(' ');
      } else {
        const def = REGISTRY[st.key]!;
        const all = { ...def.inputs, ...st.inputs };
        name = shortName(st.key, def.label);
        args = Object.entries(all).filter(([k, v]) => typeof v === 'number' || (k === 'source' && typeof v === 'string' && v !== 'close')).map(([, v]) => (String(v).startsWith('study:') ? '⧉' : String(v))).join(' ');
        if (st.timeframe) args = `${args}${args ? ' ' : ''}${intervalLabel(st.timeframe)}`;
      }
      const shown = r.items.map((it, k) => ({ it, k })).filter(({ k }) => st.style?.plots?.[k]?.visible !== false);
      const colors = shown.map(({ it, k }) => { const ps = st.style?.plots?.[k]; return withAlpha(ps?.color ?? it.color, ps?.opacity); });
      return {
        uid: st.uid, key: st.key, label: `${name}${args ? ` ${args}` : ''}`, name, args, colors,
        values: st.style?.statusValues === false ? [] : shown.map(({ it }) => it.values[i] ?? null),
        plotNames: shown.map(({ it, k }) => (r.script ? it.name : plotName(st.key, k, it.name, fa))),
        pane: r.pane, hidden: Boolean(st.hidden), precision: r.precision, statusValues: st.style?.statusValues !== false, tfOff: r.tfOff, script: r.script,
      };
    });
  }

  dispose(): void {
    for (const r of this.rendered) {
      for (const p of r.priceLines) { try { p.series.removePriceLine(p.line); } catch { /* gone */ } }
      for (const b of r.prims) { try { b.series.detachPrimitive(b.prim); } catch { /* gone */ } }
      for (const s of r.series) { try { this.chart.removeSeries(s); } catch { /* gone */ } }
    }
    this.rendered = [];
    this.markersApi?.setMarkers([]);
  }

  // ── Panes ────────────────────────────────────────────────────────────────────────────────────
  // A pane per oscillator, kept even while its study is hidden (TradingView keeps the empty pane),
  // so later panes keep their places and their legends; heights follow each study across rebuilds.
  private ensurePane(index: number, uid: string): void {
    let panes = this.chart.panes();
    while (panes.length <= index) {
      const p = this.chart.addPane(true);
      p.setStretchFactor(this.paneFactor.get(uid) ?? 0.36);
      panes = this.chart.panes();
    }
    panes[index]!.setPreserveEmptyPane(true);
  }
  private rememberPanes(): void {
    const panes = this.chart.panes();
    for (const r of this.rendered) if (r.pane > 0 && panes[r.pane]) this.paneFactor.set(r.study.uid, panes[r.pane]!.getStretchFactor());
    if (panes[0]) this.paneFactor.set('__main', panes[0].getStretchFactor());
  }
  private restorePanes(): void {
    const panes = this.chart.panes();
    for (const r of this.rendered) { const f = this.paneFactor.get(r.study.uid); if (r.pane > 0 && f != null && panes[r.pane]) panes[r.pane]!.setStretchFactor(f); }
    const m = this.paneFactor.get('__main');
    if (m != null && panes[0]) panes[0].setStretchFactor(m);
  }

  private lineData(bars: Candle[], v: Values, connect?: boolean) {
    const out: { time: UTCTimestamp; value?: number }[] = [];
    for (let i = 0; i < bars.length; i++) {
      const x = v[i];
      if (x == null || !Number.isFinite(x)) { if (!connect) out.push({ time: bars[i]!.t as UTCTimestamp }); }
      else out.push({ time: bars[i]!.t as UTCTimestamp, value: x });
    }
    return out;
  }

  private histData(bars: Candle[], v: Values, colorFor?: (v: number | null, k: number) => string) {
    return bars.map((b, i) => {
      const x = v[i];
      return x == null || !Number.isFinite(x)
        ? { time: b.t as UTCTimestamp }
        : { time: b.t as UTCTimestamp, value: x, color: colorFor ? colorFor(x, i) : UP };
    });
  }

  /** One plot as a series of the type the reader chose. */
  private plotSeries(r: Rendered, it: Item, k: number, base: Record<string, unknown>): ISeriesApi<SeriesType> {
    const bars = this.bars;
    const ps: PlotStyle = r.study.style?.plots?.[k] ?? {};
    const type: PlotType = ps.type ?? (it.hist ? 'histogram' : 'line');
    const color = withAlpha(ps.color ?? it.color, ps.opacity);
    const width = Math.max(1, Math.min(4, ps.width ?? it.width ?? 1)) as 1 | 2 | 3 | 4;
    const lineStyle = LS[ps.lineStyle ?? (it.dashed ? 'dashed' : 'solid')];
    if (type === 'histogram' || type === 'columns') {
      const s = this.chart.addSeries(HistogramSeries, { ...base, base: 0, priceLineVisible: false }, r.pane);
      const colorFor = ps.color ? () => color : it.colorFor ? (v: number | null, i: number) => withAlpha(it.colorFor!(v, i), ps.opacity) : () => color;
      s.setData(this.histData(bars, it.values, colorFor));
      return s;
    }
    if (type === 'area') {
      const s = this.chart.addSeries(AreaSeries, { ...base, lineColor: color, topColor: withAlpha(ps.color ?? it.color, (ps.opacity ?? 1) * 0.28), bottomColor: withAlpha(ps.color ?? it.color, 0), lineWidth: width, lineStyle, priceLineVisible: false, crosshairMarkerVisible: false }, r.pane);
      s.setData(this.lineData(bars, it.values, it.connect));
      return s;
    }
    const s = this.chart.addSeries(LineSeries, {
      ...base, color, lineWidth: width, lineStyle, priceLineVisible: false, crosshairMarkerVisible: false,
      lineType: type === 'step' ? LineType.WithSteps : LineType.Simple,
      lineVisible: type !== 'circles' && type !== 'cross',
      pointMarkersVisible: type === 'circles', pointMarkersRadius: type === 'circles' ? width + 1 : undefined,
    }, r.pane);
    const data = this.lineData(bars, it.values, it.connect);
    s.setData(data);
    if (type === 'cross') {
      const prim = new CrossPaint(data.filter((d): d is { time: UTCTimestamp; value: number } => d.value != null), color, width);
      s.attachPrimitive(prim as never);
      r.prims.push({ series: s, prim: prim as never });
    }
    return s;
  }

  private baseOptions(r: Rendered, autoscale?: () => { priceRange: { minValue: number; maxValue: number } }): Record<string, unknown> {
    const style = r.study.style ?? {};
    const scale = style.scale === 'left' ? 'left' : style.scale === 'none' ? `none-${r.study.uid}` : undefined;
    return {
      priceFormat: { type: 'price', precision: r.precision, minMove: 1 / 10 ** r.precision },
      lastValueVisible: style.priceLabels !== false,
      ...(scale ? { priceScaleId: scale } : {}),
      ...(autoscale ? { autoscaleInfoProvider: autoscale } : {}),
    };
  }

  private draw(r: Rendered, out: IndicatorOutput, mainColor: string): void {
    const bars = this.bars;
    const st = r.study;
    const style = st.style ?? {};
    const autoscale = out.range ? () => ({ priceRange: { minValue: out.range![0], maxValue: out.range![1] } }) : undefined;
    const base = this.baseOptions(r, autoscale);

    r.items.forEach((it, k) => {
      if (!it.color) it.color = mainColor;
      if (style.plots?.[k]?.visible === false) { r.plotSeries.push(null); return; }
      const s = this.plotSeries(r, it, k, base);
      r.series.push(s);
      r.plotSeries.push(s);
    });
    if (style.scale === 'none') {
      for (const s of r.series) { try { s.priceScale().applyOptions({ scaleMargins: { top: 0.1, bottom: 0.1 } }); } catch { /* fine */ } }
    }

    const first = r.series[0];
    const fill = style.fill;
    // Bands: Bollinger/Keltner/… (upper, lower) and the registry's `cloud` pairs (Ichimoku).
    if (first && out.upper && out.lower && fill?.visible !== false) {
      const data = bars.map((b, i) => ({ time: b.t, upper: out.upper![i] ?? null, lower: out.lower![i] ?? null }));
      const band = new BandFillPrimitive(data, fill?.color ? withAlpha(fill.color, fill.opacity ?? 0.08) : 'rgba(33, 150, 243, 0.08)');
      first.attachPrimitive(band as never);
      r.prims.push({ series: first, prim: band as never });
    }
    if (first && out.cloud && out.lines && fill?.visible !== false) {
      const [a, b] = out.cloud;
      const la = out.lines[a!]?.data;
      const lb = out.lines[b!]?.data;
      if (la && lb) {
        const ichimoku = st.key === 'ichimoku';
        const data = bars.map((bar, i) => ({ time: bar.t, upper: la[i] ?? null, lower: lb[i] ?? null }));
        const c1 = fill?.color ? withAlpha(fill.color, fill.opacity ?? 0.18) : ichimoku ? 'rgba(67, 160, 71, 0.18)' : out.cloudColors?.[0] ?? 'rgba(33, 150, 243, 0.08)';
        const band = ichimoku
          ? new BandFillPrimitive(data, c1, fill?.color2 ? withAlpha(fill.color2, fill.opacity ?? 0.18) : 'rgba(244, 67, 54, 0.18)')
          : new BandFillPrimitive(data, c1);
        first.attachPrimitive(band as never);
        r.prims.push({ series: first, prim: band as never });
      }
    }
    // Levels (RSI 70/30, CCI ±100, …), as the reader edited them, and the shaded zone between the outer ones.
    if (first) {
      const levels: LevelStyle[] = style.levels ?? (out.guides ?? []).map((g) => ({ value: g, color: GUIDE, lineStyle: 'dashed' as const, visible: true }));
      for (const g of levels) {
        if (!g.visible || !Number.isFinite(g.value)) continue;
        const line = first.createPriceLine({ price: g.value, color: g.color, lineWidth: (g.width ?? 1) as 1, lineStyle: LS[g.lineStyle], axisLabelVisible: false, title: '' });
        r.priceLines.push({ series: first, line });
      }
      const lf = style.levelFill;
      const shown = levels.filter((g) => g.visible && Number.isFinite(g.value)).map((g) => g.value);
      if ((out.zone ? lf?.visible !== false : lf?.visible === true) && shown.length >= 2) {
        const lo = Math.min(...shown); const hi = Math.max(...shown);
        const band = new BandFillPrimitive(bars.map((b) => ({ time: b.t, upper: hi, lower: lo })), lf?.color ? withAlpha(lf.color, lf.opacity ?? 0.1) : 'rgba(126, 87, 194, 0.1)');
        first.attachPrimitive(band as never);
        r.prims.push({ series: first, prim: band as never });
      }
    }
    // Levels (auto Fibonacci, support/resistance, supply/demand) on the price series.
    const price = this.price();
    if (price) {
      for (const l of out.levels ?? []) {
        const line = price.createPriceLine({ price: l.price, color: l.color, lineWidth: 1, lineStyle: LineStyle.Solid, axisLabelVisible: true, title: l.label ?? '' });
        r.priceLines.push({ series: price, line });
      }
    }
    if (out.markers?.length) r.markers = out.markers.map((m) => ({ ...m, time: m.time as UTCTimestamp }));
    // Re-coloured bars, volume profiles and tables.
    if (out.candles?.length) {
      const s = this.chart.addSeries(CandlestickSeries, { ...base, priceLineVisible: false, lastValueVisible: false }, r.pane);
      s.setData(out.candles.map((c) => ({ time: c.time as UTCTimestamp, open: c.open, high: c.high, low: c.low, close: c.close, color: c.color, wickColor: c.color, borderColor: c.color })));
      r.series.push(s);
    }
    if (out.profile && price) {
      const prim = new ProfilePaint(out.profile.segments, out.profile.place);
      price.attachPrimitive(prim as never);
      r.prims.push({ series: price, prim: prim as never });
      r.profile = prim;
    }
    if (out.table) this.tables.push({ uid: st.uid, title: out.table.title, rows: out.table.rows });
  }

  // ── The NamaScript pseudo-study ──────────────────────────────────────────────────────────────
  private scriptItems(res: NonNullable<ReturnType<typeof useScript.getState>['result']>, bars: Candle[]): Item[] {
    return (res.plots ?? []).map((p) => {
      const at = new Map(p.data.map((d) => [d.time, d.value]));
      return { values: bars.map((b) => at.get(b.t) ?? null), color: p.color, width: Math.max(1, Math.min(4, p.width || 1)), dashed: p.style === 2, name: p.name };
    });
  }

  private drawScript(r: Rendered, res: NonNullable<ReturnType<typeof useScript.getState>['result']>): void {
    const base = this.baseOptions(r);
    r.items.forEach((it, k) => {
      if (r.study.style?.plots?.[k]?.visible === false) { r.plotSeries.push(null); return; }
      const s = this.plotSeries(r, it, k, base);
      r.series.push(s); r.plotSeries.push(s);
    });
    // plotcandle / plotbar: candles of the script's own.
    for (const cp of res.candleplots ?? []) {
      const up = cp.color ?? UP; const dn = cp.color ?? DOWN;
      const s = cp.bars
        ? this.chart.addSeries(BarSeries, { upColor: up, downColor: dn, priceLineVisible: false, lastValueVisible: false }, r.pane)
        : this.chart.addSeries(CandlestickSeries, { upColor: up, downColor: dn, borderUpColor: up, borderDownColor: dn, wickUpColor: up, wickDownColor: dn, priceLineVisible: false, lastValueVisible: false }, r.pane);
      s.setData(cp.data.map((d) => ({ ...d, time: d.time as UTCTimestamp })));
      r.series.push(s);
    }
    const host = r.pane === 0 ? this.price() : r.series[0] ?? null;
    if (host) {
      for (const h of res.hlines ?? []) r.priceLines.push({ series: host, line: host.createPriceLine({ price: h.price, color: h.color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: h.title }) });
      const paint = { bgs: res.bgs, fills: res.fills, boxes: res.boxes, lines: res.lines, barcolors: res.barcolors, zones: res.zones };
      if (Object.values(paint).some((x) => x?.length)) {
        const prim = new ScriptPaint(paint);
        host.attachPrimitive(prim as never);
        r.prims.push({ series: host, prim: prim as never });
      }
    }
    r.markers = scriptMarkers(res);
    // The strategy's trades, as TradingView marks them: an arrow and a label at each entry and exit.
    const s = useScript.getState();
    if (s.backtest && s.props.showTrades && res.signals) {
      const fa = usePrefs.getState().lang === 'fa';
      for (const t of s.backtest.trades) {
        const long = t.dir === 1;
        r.markers.push({ time: t.entryT as UTCTimestamp, position: long ? 'belowBar' : 'aboveBar', shape: long ? 'arrowUp' : 'arrowDown', color: long ? '#2962FF' : '#F23645', text: long ? (fa ? 'خرید' : 'Long') : (fa ? 'فروش' : 'Short') });
        r.markers.push({ time: t.exitT as UTCTimestamp, position: long ? 'aboveBar' : 'belowBar', shape: long ? 'arrowDown' : 'arrowUp', color: '#9C27B0', text: fa ? 'خروج' : 'Close' });
      }
    }
  }

  private applyMarkers(): void {
    const price = this.price();
    if (!price) return;
    const en = usePrefs.getState().lang === 'en';
    const all = this.rendered
      .flatMap((r) => (r.study.hidden || r.tfOff ? [] : r.markers))
      .map((m) => (en && m.text ? { ...m, text: patternName(m.text, 'en') } : m))
      .sort((a, b) => (a.time as number) - (b.time as number));
    if (!this.markersApi) this.markersApi = createSeriesMarkers(price, all);
    else this.markersApi.setMarkers(all);
  }

  /** The price series was replaced (a chart-type change): markers and levels follow it. */
  resetPriceSeries(): void { this.markersApi = null; }
}

/** TradingView-style short name for the legend: the Latin abbreviation in the label, else the label. */
export function shortName(key: string, label: string): string {
  const m = /\(([A-Za-z0-9%± .\-]+)\)/.exec(label);
  if (m) return m[1]!.trim();
  if (/^[A-Za-z0-9 %]+$/.test(label)) return label;
  const known: Record<string, string> = { bb: 'BB', ichimoku: 'Ichimoku', supertrend: 'Supertrend', stoch: 'Stoch', willr: '%R', donchian: 'DC', keltner: 'KC', psar: 'SAR', alligator: 'Alligator', pivots: 'Pivots' };
  return known[key] ?? key.toUpperCase();
}
