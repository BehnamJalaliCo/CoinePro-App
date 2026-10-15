// The chart's own settings: TradingView's «Settings» dialog, tab for tab (Symbol, Status line,
// Scales and lines, Canvas, Trading, Alerts, Events), plus the legend's own switches and the
// old Pro-Chart's overlays. Kept in this browser; templates are named copies of the whole set.
import { create } from 'zustand';

/** How a price line shows: its label on the axis, the line across the pane, both, or nothing. */
export type LineVis = 'value-line' | 'value' | 'line' | 'hidden';
/** When a button or a label shows: on mouse-over, always, or never. */
export type ShowWhen = 'hover' | 'always' | 'hidden';
export type TitleMode = 'description' | 'ticker' | 'both';
export type IndicatorLabels = 'value' | 'name-value' | 'hidden';
export type CrossStyle = 'solid' | 'dotted' | 'dashed' | 'large-dashed' | 'sparse-dotted';
export type SessionId = 'sydney' | 'tokyo' | 'london' | 'newyork';
export const SESSION_IDS: SessionId[] = ['sydney', 'tokyo', 'london', 'newyork'];

export interface ChartSettings {
  // ── Symbol ──
  /** Bar body colours (the candles' bodies, the bars, columns, the rising/falling line ends). */
  up: string;
  down: string;
  bodyVisible: boolean;
  borderVisible: boolean;
  borderUp: string;
  borderDown: string;
  wickVisible: boolean;
  wickUp: string;
  wickDown: string;
  /** Colour each bar by its close against the previous close, not against its own open. */
  colorByPrevClose: boolean;
  thinBars: boolean;
  /** Rising candle bodies drawn hollow (the old Pro-Chart's switch; Candles only). */
  hollow: boolean;
  /** Line, step, line-with-markers, area and the baseline's line. */
  lineColor: string;
  lineWidth: number;
  areaTop: string;
  areaBottom: string;
  /** Baseline: where the base sits, in percent of the visible price range (TradingView: 50). */
  baselinePct: number;
  /** Decimal places on the price axis and the legend; −1 is the instrument's own. */
  precision: number;
  // Non-time chart types (TradingView's inputs for each).
  renkoMethod: 'atr' | 'traditional';
  renkoAtr: number;
  renkoBox: number;
  renkoWicks: boolean;
  rangeMethod: 'atr' | 'traditional';
  rangeSize: number;
  lineBreakCount: number;
  kagiMethod: 'atr' | 'traditional';
  kagiAtr: number;
  kagiReversal: number;
  pnfMethod: 'atr' | 'traditional';
  pnfAtr: number;
  pnfBox: number;
  pnfReversal: number;
  pnfSource: 'close' | 'hl';

  // ── Status line ──
  showLogo: boolean;
  showTitle: boolean;
  titleMode: TitleMode;
  showMarketStatus: boolean;
  showOhlc: boolean;
  showChange: boolean;
  showVolume: boolean;
  showLastDay: boolean;
  tradeButtons: boolean;
  indTitles: boolean;
  indInputs: boolean;
  indValues: boolean;
  legendBg: boolean;
  /** Opacity of the legend's background, 0–100. */
  legendBgOpacity: number;
  legendCollapsed: boolean;
  legendCompact: boolean;

  // ── Scales and lines ──
  currencyLabel: ShowWhen;
  lockRatio: boolean;
  /** Price units per bar width while the ratio is locked. */
  lockRatioValue: number;
  /** Auto-scale fits the price alone, not the indicators and compared symbols over it. */
  scalePriceOnly: boolean;
  scalePlacement: 'auto' | 'left' | 'right';
  noOverlap: boolean;
  plusButton: boolean;
  countdown: boolean;
  lastPrice: LineVis;
  prevDayClose: LineVis;
  indicatorLabels: IndicatorLabels;
  highLow: LineVis;
  bidAsk: LineVis;
  avgClose: LineVis;
  /** The previous day's high and low. */
  pdhl: LineVis;
  /** The highest high and the lowest low on screen, marked on their bars. */
  hhll: boolean;
  symbolNameLabel: boolean;
  dayOfWeek: boolean;
  dateFormat: 'dd MMM \'yy' | 'MMM dd, yyyy' | 'yyyy-MM-dd' | 'dd/MM/yyyy' | 'MM/dd/yyyy';
  hour12: boolean;
  /** Compared symbols: each on its own hidden scale (unless the axis is in %), or all in percent. */
  compareMode: 'own' | 'percent';

  // ── Canvas ──
  bgType: 'solid' | 'gradient';
  /** '' follows the theme. */
  bgColor: string;
  bgColor2: string;
  vertGrid: boolean;
  vertGridColor: string;
  horzGrid: boolean;
  horzGridColor: string;
  crossColor: string;
  crossWidth: number;
  crossStyle: CrossStyle;
  watermark: boolean;
  watermarkColor: string;
  watermarkOpacity: number;
  scaleTextColor: string;
  scaleFontSize: number;
  scaleLineColor: string;
  navButtons: ShowWhen;
  paneButtons: ShowWhen;
  marginTop: number;
  marginBottom: number;
  rightOffset: number;
  scrollScale: boolean;
  /** Crosshair snapping (the cursor mode itself is the drawing rail's). */
  crosshair: 'normal' | 'magnet';

  // ── Trading ──
  instantOrders: boolean;
  positionLines: boolean;
  orderLines: boolean;
  executionMarks: boolean;

  // ── Alerts ──
  alertLines: boolean;
  alertLinesActiveOnly: boolean;
  alertLineColor: string;

  // ── Events ──
  events: boolean;
  eventImportance: 'high' | 'medium' | 'all';
  eventsFutureOnly: boolean;
  news: boolean;
  sessionBreaks: boolean;
  sessionBreakColor: string;
  sessionBreakStyle: 'solid' | 'dotted' | 'dashed';
  sessionBreakWidth: number;
  /** Trading-session shading (Sydney, Tokyo, London, New York and their overlaps). */
  sessionsOn: boolean;
  sessions: SessionId[];
  sessionOverlaps: boolean;

  // ── Overlays ──
  volumeMa: boolean;
  volumeMaLength: number;
  volumeProfile: boolean;
  vpRows: number;
  vpValueArea: number;
  autoDivergence: boolean;
  autoHarmonic: boolean;
  autoGaps: boolean;
}

export const DEFAULT_SETTINGS: ChartSettings = {
  up: '#089981', down: '#F23645', bodyVisible: true,
  borderVisible: true, borderUp: '#089981', borderDown: '#F23645',
  wickVisible: true, wickUp: '#089981', wickDown: '#F23645',
  colorByPrevClose: false, thinBars: false, hollow: false,
  lineColor: '#2962FF', lineWidth: 2, areaTop: 'rgba(41,98,255,0.28)', areaBottom: 'rgba(41,98,255,0.02)', baselinePct: 50,
  precision: -1,
  renkoMethod: 'atr', renkoAtr: 14, renkoBox: 0, renkoWicks: false,
  rangeMethod: 'atr', rangeSize: 0,
  lineBreakCount: 3,
  kagiMethod: 'atr', kagiAtr: 14, kagiReversal: 0,
  pnfMethod: 'atr', pnfAtr: 14, pnfBox: 0, pnfReversal: 3, pnfSource: 'close',

  showLogo: true, showTitle: true, titleMode: 'both', showMarketStatus: true, showOhlc: true, showChange: true, showVolume: false,
  showLastDay: false, tradeButtons: true, indTitles: true, indInputs: true, indValues: true, legendBg: false, legendBgOpacity: 50,
  legendCollapsed: false, legendCompact: false,

  currencyLabel: 'hover', lockRatio: false, lockRatioValue: 0, scalePriceOnly: false, scalePlacement: 'auto', noOverlap: true, plusButton: true, countdown: true,
  lastPrice: 'value-line', prevDayClose: 'hidden', indicatorLabels: 'value', highLow: 'hidden', bidAsk: 'hidden', avgClose: 'hidden',
  pdhl: 'hidden', hhll: false, symbolNameLabel: false, dayOfWeek: true, dateFormat: 'dd MMM \'yy', hour12: false, compareMode: 'own',

  bgType: 'solid', bgColor: '', bgColor2: '', vertGrid: true, vertGridColor: '', horzGrid: true, horzGridColor: '',
  crossColor: '', crossWidth: 1, crossStyle: 'dashed', watermark: false, watermarkColor: '', watermarkOpacity: 7,
  scaleTextColor: '', scaleFontSize: 12, scaleLineColor: '', navButtons: 'hover', paneButtons: 'hover',
  marginTop: 10, marginBottom: 8, rightOffset: 10, scrollScale: true, crosshair: 'normal',

  instantOrders: true, positionLines: true, orderLines: true, executionMarks: true,

  alertLines: true, alertLinesActiveOnly: true, alertLineColor: '#F7A600',

  events: true, eventImportance: 'high', eventsFutureOnly: false, news: false,
  sessionBreaks: false, sessionBreakColor: '', sessionBreakStyle: 'dashed', sessionBreakWidth: 1,
  sessionsOn: false, sessions: ['sydney', 'tokyo', 'london', 'newyork'], sessionOverlaps: true,

  volumeMa: false, volumeMaLength: 20, volumeProfile: false, vpRows: 24, vpValueArea: 70,
  autoDivergence: false, autoHarmonic: false, autoGaps: false,
};

const KEY = 'pc.chartsettings.v1';
const TPL_KEY = 'pc.chartsettings.tpl.v1';

/** Settings saved by an older build: the single price-line switch, the grid mode, the prev-close switch. */
export function migrate(raw: Record<string, unknown>): Partial<ChartSettings> {
  const out: Record<string, unknown> = { ...raw };
  if (typeof raw.priceLine === 'boolean' && raw.lastPrice === undefined) out.lastPrice = raw.priceLine ? 'value-line' : 'value';
  if (typeof raw.prevClose === 'boolean' && raw.prevDayClose === undefined) out.prevDayClose = raw.prevClose ? 'value-line' : 'hidden';
  if (typeof raw.grid === 'string' && raw.vertGrid === undefined) {
    out.vertGrid = raw.grid === 'both' || raw.grid === 'vertical';
    out.horzGrid = raw.grid === 'both' || raw.grid === 'horizontal';
  }
  delete out.priceLine; delete out.prevClose; delete out.grid;
  // Only known keys, each of the default's type: a hand-edited or stale store never breaks the chart.
  const clean: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(out)) {
    const def = (DEFAULT_SETTINGS as unknown as Record<string, unknown>)[k];
    if (def === undefined) continue;
    if (Array.isArray(def) ? Array.isArray(v) : typeof v === typeof def) clean[k] = v;
  }
  return clean as Partial<ChartSettings>;
}

const read = (): ChartSettings => {
  try { return { ...DEFAULT_SETTINGS, ...migrate(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, unknown>) }; } catch { return DEFAULT_SETTINGS; }
};

export const pickSettings = (s: ChartSettings): ChartSettings => {
  const out = {} as Record<string, unknown>;
  for (const k of Object.keys(DEFAULT_SETTINGS)) out[k] = (s as unknown as Record<string, unknown>)[k];
  return out as unknown as ChartSettings;
};

export interface SettingsTemplate { name: string; settings: Partial<ChartSettings> }

const readTemplates = (): SettingsTemplate[] => {
  try { const v = JSON.parse(localStorage.getItem(TPL_KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as SettingsTemplate[]).filter((t) => t && typeof t.name === 'string') : []; } catch { return []; }
};

interface State extends ChartSettings {
  templates: SettingsTemplate[];
  set(p: Partial<ChartSettings>): void;
  reset(): void;
  saveTemplate(name: string): void;
  applyTemplate(name: string): void;
  deleteTemplate(name: string): void;
}

export const useChartSettings = create<State>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(pickSettings(get()))); } catch { /* storage blocked: kept for this visit */ } };
  const saveTpl = () => { try { localStorage.setItem(TPL_KEY, JSON.stringify(get().templates)); } catch { /* ignore */ } };
  return {
    ...read(),
    templates: readTemplates(),
    set: (p) => { set(p); save(); },
    reset: () => { set(DEFAULT_SETTINGS); try { localStorage.removeItem(KEY); } catch { /* ignore */ } },
    saveTemplate: (name) => {
      const n = name.trim();
      if (!n) return;
      set({ templates: [...get().templates.filter((t) => t.name !== n), { name: n, settings: pickSettings(get()) }] });
      saveTpl();
    },
    applyTemplate: (name) => {
      const t = get().templates.find((x) => x.name === name);
      if (!t) return;
      set({ ...DEFAULT_SETTINGS, ...migrate(t.settings as Record<string, unknown>) });
      save();
    },
    deleteTemplate: (name) => { set({ templates: get().templates.filter((t) => t.name !== name) }); saveTpl(); },
  };
});

/** Whether a LineVis shows its axis label / its line. */
export const showsValue = (v: LineVis) => v === 'value-line' || v === 'value';
export const showsLine = (v: LineVis) => v === 'value-line' || v === 'line';
