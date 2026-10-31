// The signal layer as one chart holds it (OLD feature/chart ChartSignals.kt ChartSignalLayer and
// ChartSignalEngine.evaluate): every switched-on study and the reader's script, each read by the
// rules in signals.ts, each with its base rate, plus the one Setup score — a single value the Now
// strip, the Explain sheet and the marks on the candles all read, so they cannot disagree. Also the
// reader's choices that belong to it: the horizon, each study's marks, the sheet that is open, and
// «روی تایم‌فریم‌های دیگر» (the same study read on H1, H4 and D1, fetched on a tap).
import { create } from 'zustand';
import type { Candle } from '../data/types';
import { REGISTRY } from '../engine/indicators.js';
import { STUDIES as ALL_STUDIES } from '../engine/indicators_ext_c.js';
import type { ScriptResult } from '../engine/namascript.js';
import { runScript } from '../engine/namascript.js';
import { SCRIPT, VOLUME, type Study } from '../state/studies';
import { scriptTitle, useScript } from '../state/script';
import { computeStudy, shortName } from './studies';
import { entryOf } from './indicatorCatalog';
import { scriptIsOverlay } from './scriptRenderer';
import { feed } from '../data/feed';
import { intervalSeconds } from '../data/intervals';
import {
  DEFAULT_HORIZON, emptyReport, isSilent, makeSeries, measureRead, noteText, readLine, readStudy, readVerdicts, setupScore, snapHorizon,
  type ConfidenceReport, type Exclusion, type MarkerStyle, type MarketState, type Series, type SetupScore, type SignalRead,
} from './signals';

export interface SignalLayer {
  reads: SignalRead[];
  confidence: Record<string, ConfidenceReport>;
  setup: SetupScore;
  horizon: number;
  /** What a pill calls each study, by uid: RSI, MACD — or the name the reader gave their script. */
  names: Record<string, string>;
  /** Each read's study key (rsi, macd, __script), by uid. */
  keys: Record<string, string>;
  /** Which language the sentences were written in. */
  english: boolean;
  /** The bars it was read on (the marks and the replay start read them back). */
  bars: Candle[];
}

export const EMPTY_LAYER: SignalLayer = { reads: [], confidence: {}, setup: { score: 0, side: 'neutral', signals: 0, studies: 0, winRate: null }, horizon: DEFAULT_HORIZON, names: {}, keys: {}, english: false, bars: [] };

/** One study on another bar length: the wire name printed as it is, its state and its sentence. */
export interface TimeframeRead { timeframe: string; state: MarketState; sentence: string }

/** The other bar lengths a study is read on, and how many bars each fetch asks for. */
export const MULTI_TIMEFRAMES: { id: string; wire: string }[] = [{ id: '60', wire: 'H1' }, { id: '240', wire: 'H4' }, { id: '1D', wire: 'D1' }];
export const MULTI_TIMEFRAME_BARS = 200;

const STRUCTURE_CATEGORIES = new Set(['levels', 'patterns', 'profiles']);
/** Studies drawn but with nothing to say as a direction: a second symbol's ratio, a bar colouring. */
const SILENT_KEYS = new Set(['volume', 'chopZone', 'ratio', 'spread', 'bollingerBars', 'techRatings']);

/** A study's short name for a sentence: the Latin abbreviation the legend already prints. */
export function signalName(st: Study): string {
  if (st.key === SCRIPT) return scriptTitle(useScript.getState().runCode) ?? 'NamaScript';
  const entry = entryOf(st.key);
  if (entry && /^[\x20-\x7e]+$/.test(entry.abbr)) return entry.abbr;
  const def = ALL_STUDIES[st.key];
  return def ? shortName(st.key, def.label) : st.key.toUpperCase();
}

/** One study read on `bars` (its own inputs, timeframe and source, as the chart draws it). */
export function readOne(st: Study, bars: Candle[], s: Series, english: boolean, all: Study[], script: ScriptResult | null): SignalRead | null {
  if (st.key === VOLUME) return null;
  const name = signalName(st);
  if (st.key === SCRIPT) {
    if (!script?.ok) return null;
    if (script.signals && (script.signals.long.length || script.signals.short.length)) return readVerdicts(st.uid, name, script.signals.long, script.signals.short, s);
    const plot = script.plots?.find((p) => p.data.length);
    if (!plot) return null;
    const at = new Map(bars.map((b, i) => [b.t, i]));
    const line: (number | null)[] = new Array(bars.length).fill(null);
    for (const pt of plot.data) { const i = at.get(pt.time); if (i != null && Number.isFinite(pt.value)) line[i] = pt.value; }
    return readLine(st.uid, name, line, s, scriptIsOverlay(script, bars), english);
  }
  const def = ALL_STUDIES[st.key];
  if (!def) return null;
  const entry = entryOf(st.key);
  const structure = STRUCTURE_CATEGORIES.has(entry?.category ?? '') || SILENT_KEYS.has(st.key) || Boolean(def.other) || Boolean(def.visibleRange);
  const out = structure ? null : computeStudy(st, bars, new Map(), all)?.out ?? null;
  const inputs = { ...def.inputs, ...st.inputs } as Record<string, unknown>;
  const period = Number(inputs.period ?? inputs.length ?? 20);
  return readStudy({ id: st.uid, key: st.key, name, overlay: def.pane === 'main', structure, period }, out, s, english);
}

/** Every switched-on study's reading, its base rate and the chart's Setup score. */
export function evaluateLayer(studies: Study[], bars: Candle[], opts: { english: boolean; horizon: number; script: ScriptResult | null }): SignalLayer {
  if (!bars.length) return { ...EMPTY_LAYER, horizon: opts.horizon, english: opts.english };
  const s = makeSeries(bars);
  const reads: SignalRead[] = [];
  const names: Record<string, string> = {};
  const keys: Record<string, string> = {};
  for (const st of studies) {
    let read: SignalRead | null = null;
    try { read = readOne(st, bars, s, opts.english, studies, opts.script); } catch { read = null; }
    if (!read || isSilent(read)) continue;
    reads.push(read);
    names[st.uid] = signalName(st);
    keys[st.uid] = st.key;
  }
  const confidence: Record<string, ConfidenceReport> = {};
  for (const r of reads) confidence[r.id] = measureRead(r, s, opts.horizon);
  return { reads, confidence, setup: setupScore(reads, confidence), horizon: opts.horizon, names, keys, english: opts.english, bars };
}

/** Whether the study is computed on a higher timeframe of its own. */
export const ownTimeframe = (st: Study, interval: string) => Boolean(st.timeframe && intervalSeconds(st.timeframe) > intervalSeconds(interval));

// ── The store ───────────────────────────────────────────────────────────────────────────────────

const STYLE_KEY = 'pc.signals.markers.v1';
const HORIZON_KEY = 'pc.signals.horizon.v1';
function read<T>(key: string, fallback: T): T { try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; return v ?? fallback; } catch { return fallback; } }
function write(key: string, v: unknown) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* ignore */ } }

interface SignalsState {
  layer: SignalLayer;
  /** How many bars ahead each base rate is measured over: 5, 10 or 20. */
  horizon: number;
  /** What each study draws on the candles, by study key; absent is labels. */
  markerStyles: Record<string, MarkerStyle>;
  /** The Explain sheet: a study's uid, null for the chart's own score, or closed. */
  explaining: { id: string | null } | null;
  /** «روی تایم‌فریم‌های دیگر»: absent is not asked, 'loading' is on its way, a list (maybe empty) is the answer. */
  across: Record<string, TimeframeRead[] | 'loading'>;
  /** What the picture is not showing (the provenance line under the venue caption). */
  exclusions: Exclusion[];
  setExclusions(list: Exclusion[]): void;
  setLayer(layer: SignalLayer): void;
  setHorizon(bars: number): void;
  setMarkerStyle(key: string, style: MarkerStyle): void;
  openExplain(id: string | null): void;
  closeExplain(): void;
  setAcross(id: string, rows: TimeframeRead[] | 'loading' | null): void;
}

export const useSignals = create<SignalsState>((set, get) => ({
  layer: EMPTY_LAYER,
  horizon: snapHorizon(read<number>(HORIZON_KEY, DEFAULT_HORIZON)),
  markerStyles: read<Record<string, MarkerStyle>>(STYLE_KEY, {}),
  explaining: null,
  across: {},
  exclusions: [],
  setExclusions: (exclusions) => { if (JSON.stringify(exclusions) !== JSON.stringify(get().exclusions)) set({ exclusions }); },
  setLayer: (layer) => set({ layer }),
  setHorizon: (bars) => { const horizon = snapHorizon(bars); if (horizon === get().horizon) return; write(HORIZON_KEY, horizon); set({ horizon }); },
  setMarkerStyle: (key, style) => {
    const next = { ...get().markerStyles };
    if (style === 'labels') delete next[key]; else next[key] = style;
    write(STYLE_KEY, next);
    set({ markerStyles: next });
  },
  openExplain: (id) => set({ explaining: { id } }),
  closeExplain: () => set({ explaining: null }),
  setAcross: (id, rows) => { const next = { ...get().across }; if (rows == null) delete next[id]; else next[id] = rows; set({ across: next }); },
}));

/** The report a pill reads for a study (an empty one when it has none). */
export const reportOf = (layer: SignalLayer, id: string): ConfidenceReport => layer.confidence[id] ?? emptyReport(id, layer.horizon);

/**
 * Read one study on the three other bar lengths (ChartController.readAcrossTimeframes): a request
 * per row, on a tap, never on every open; the one the reader is already looking at is not
 * «another timeframe», and a bar length the venue will not serve is simply absent.
 */
export async function readAcross(st: Study, symbol: string, interval: string, english: boolean, all: Study[]): Promise<TimeframeRead[]> {
  const rows: TimeframeRead[] = [];
  for (const tf of MULTI_TIMEFRAMES.filter((x) => x.id !== interval)) {
    try {
      const page = await feed.history(symbol, tf.id);
      const bars = page.bars.slice(-MULTI_TIMEFRAME_BARS);
      if (!bars.length) continue;
      const s = makeSeries(bars);
      const plain: Study = { ...st, timeframe: undefined };
      let script: ScriptResult | null = null;
      if (st.key === SCRIPT) {
        const sc = useScript.getState();
        script = await runScript(sc.runCode, bars, sc.inputs);
      }
      const r = readOne(plain, bars, s, english, all, script);
      if (!r) continue;
      rows.push({ timeframe: tf.wire, state: r.state, sentence: noteText(r.note, english) });
    } catch { /* not served: the row is absent */ }
  }
  return rows;
}

/** The registry's colour for a study, when the legend has none to hand. */
export const registryColour = (key: string) => REGISTRY[key]?.color ?? ALL_STUDIES[key]?.color ?? null;
