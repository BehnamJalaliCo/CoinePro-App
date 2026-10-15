// What the settings dialog and the legend call each input and each plot, in both languages, and
// the choices of the inputs that are menus. Every input key the registry uses has a label here
// (a unit test keeps it that way); the Persian is written the house way.
import { SOURCE_OPTS } from '../engine/indicators.js';

type Bi = [fa: string, en: string];

/** Labels for every input key in the registry, as TradingView names them. */
export const INPUT_LABELS: Record<string, Bi> = {
  period: ['طول', 'Length'],
  source: ['منبع', 'Source'],
  anchorBars: ['لنگر (کندل‌های قبل)', 'Anchor (bars back)'],
  mult: ['ضریب', 'Multiplier'],
  smooth: ['هموارسازی', 'Smoothing'],
  scale: ['مقیاس حجم', 'Volume divisor'],
  p: ['طول ATR اول', 'P (ATR length)'],
  x: ['ضریب ATR', 'X (ATR multiplier)'],
  q: ['طول توقف', 'Q (stop length)'],
  sum: ['طول جمع', 'Sum length'],
  fast: ['طول سریع', 'Fast length'],
  slow: ['طول کند', 'Slow length'],
  cycle: ['طول چرخه', 'Cycle length'],
  roc: ['طول نرخ تغییر', 'ROC length'],
  factor: ['ضریب تایم‌فریم', 'Timeframe multiplier'],
  maLength: ['طول میانگین متحرک', 'MA length'],
  sig: ['هموارسازی سیگنال', 'Signal smoothing'],
  d: ['هموارسازی %D', '%D smoothing'],
  tenkan: ['طول خط تبدیل', 'Conversion line length'],
  kijun: ['طول خط پایه', 'Base line length'],
  span: ['طول دهانه‌ی B', 'Leading span B length'],
  start: ['شروع', 'Start'],
  inc: ['افزایش', 'Increment'],
  max: ['بیشینه', 'Maximum value'],
  k: ['هموارسازی %K', '%K smoothing'],
  short: ['طول کوتاه', 'Short length'],
  long: ['طول بلند', 'Long length'],
  offset: ['جابه‌جایی', 'Offset'],
  sigma: ['سیگما', 'Sigma'],
  base: ['طول پایه', 'Base length'],
  step: ['گام', 'Step'],
  count: ['تعداد', 'Count'],
  volume: ['ضریب حجم', 'Volume factor'],
  pct: ['درصد', 'Percent'],
  annual: ['روزهای سال', 'Annual days'],
  len1: ['طول اول', 'First smoothing length'],
  len2: ['طول دوم', 'Second smoothing length'],
  length: ['طول', 'Length'],
  stdevLen: ['طول انحراف معیار', 'StdDev length'],
  mid: ['طول میانی', 'Middle length'],
  rsiLen: ['طول RSI', 'RSI length'],
  streakLen: ['طول رشته‌ی UpDown', 'UpDown length'],
  rankLen: ['طول رتبه‌ی ROC', 'ROC length'],
  smoothK: ['هموارسازی %K', '%K smoothing'],
  smoothD: ['هموارسازی %D', '%D smoothing'],
  maLen: ['طول میانگین حجم', 'Volume MA length'],
  shortLen: ['طول کوتاه', 'Short length'],
  longLen: ['طول بلند', 'Long length'],
  pivotType: ['نوع', 'Type'],
  left: ['کندل‌های چپ', 'Left bars'],
  right: ['کندل‌های راست', 'Right bars'],
  n: ['کندل‌های هر طرف', 'Periods'],
  dev: ['انحراف (درصد)', 'Deviation (%)'],
  lookback: ['کندل‌های بازبینی', 'Lookback'],
  tol: ['تلورانس (درصد)', 'Tolerance (%)'],
  impulse: ['ضریب حرکت', 'Impulse multiplier'],
  atrLen: ['طول ATR', 'ATR length'],
  maxZones: ['بیشینه‌ی ناحیه‌ها', 'Max zones'],
  // Keys the indicators added for TradingView parity use.
  trend: ['تشخیص روند بر اساس', 'Detect trend based on'],
  len: ['طول', 'Length'],
  symbol: ['نماد', 'Symbol'],
  anchor: ['دوره‌ی لنگر', 'Anchor period'],
  window: ['پنجره (کندل)', 'Window (bars)'],
  rows: ['تعداد ردیف', 'Row size'],
  valueArea: ['ناحیه‌ی ارزش (درصد)', 'Value area volume (%)'],
  period2: ['دوره', 'Period'],
  pivots: ['تعداد پیووت', 'Pivots'],
  depth: ['عمق', 'Depth'],
  levels: ['سطوح', 'Levels'],
  bars: ['کندل‌ها', 'Bars'],
  basis: ['نوع میانگین', 'Basis MA type'],
  signal: ['طول سیگنال', 'Signal length'],
  session: ['جلسه', 'Session'],
  show: ['نمایش', 'Show'],
  end: ['پایان (کندل‌های قبل)', 'End (bars back)'],
};

export function inputLabel(key: string, fa: boolean): string {
  const l = INPUT_LABELS[key];
  if (l) return fa ? l[0] : l[1];
  return key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
}

/** The price sources, labelled. */
export const SOURCE_LABELS: Record<string, Bi> = {
  close: ['بسته شدن', 'close'], open: ['باز شدن', 'open'], high: ['سقف', 'high'], low: ['کف', 'low'],
  hl2: ['(سقف+کف)/۲', 'hl2'], hlc3: ['(سقف+کف+بسته)/۳', 'hlc3'], ohlc4: ['(باز+سقف+کف+بسته)/۴', 'ohlc4'], hlcc4: ['(سقف+کف+۲×بسته)/۴', 'hlcc4'],
};
export const SOURCES = SOURCE_OPTS;

/** Inputs that are menus: their choices, with labels. */
export const MENU_INPUTS: Record<string, { value: string; fa: string; en: string }[]> = {
  pivotType: [
    { value: 'Classic', fa: 'کلاسیک', en: 'Traditional' }, { value: 'Fibonacci', fa: 'فیبوناچی', en: 'Fibonacci' },
    { value: 'Woodie', fa: 'وودی', en: 'Woodie' }, { value: 'Camarilla', fa: 'کاماریلا', en: 'Camarilla' }, { value: 'DM', fa: 'دی‌مارک', en: 'DM' },
  ],
  trend: [
    { value: 'SMA50', fa: 'SMA50', en: 'SMA50' }, { value: 'SMA50,SMA200', fa: 'SMA50 و SMA200', en: 'SMA50, SMA200' }, { value: 'none', fa: 'بدون تشخیص', en: 'No detection' },
  ],
  anchor: [
    { value: 'D', fa: 'روز', en: 'Day' }, { value: 'W', fa: 'هفته', en: 'Week' }, { value: 'M', fa: 'ماه', en: 'Month' },
  ],
  basis: [
    { value: 'sma', fa: 'SMA', en: 'SMA' }, { value: 'ema', fa: 'EMA', en: 'EMA' },
  ],
  session: [
    { value: 'D', fa: 'روزانه', en: 'Daily' }, { value: 'W', fa: 'هفتگی', en: 'Weekly' }, { value: 'M', fa: 'ماهانه', en: 'Monthly' },
  ],
};

/** Names of the plots, in the renderer's flatten order, where TradingView names them. */
export const PLOT_NAMES: Record<string, Bi[]> = {
  rsi: [['RSI', 'RSI'], ['میانگین RSI', 'RSI-based MA']],
  macd: [['هیستوگرام', 'Histogram'], ['MACD', 'MACD'], ['سیگنال', 'Signal']],
  stoch: [['%K', '%K'], ['%D', '%D']],
  stochrsi: [['%K', '%K'], ['%D', '%D']],
  bb: [['باند بالا', 'Upper'], ['پایه', 'Basis'], ['باند پایین', 'Lower']],
  ichimoku: [['خط تبدیل', 'Conversion Line'], ['خط پایه', 'Base Line'], ['دهانه‌ی A', 'Leading Span A'], ['دهانه‌ی B', 'Leading Span B'], ['خط تأخیری', 'Lagging Span']],
  alligator: [['فک', 'Jaw'], ['دندان', 'Teeth'], ['لب', 'Lips']],
  keltner: [['باند بالا', 'Upper'], ['پایه', 'Basis'], ['باند پایین', 'Lower']],
  donchian: [['باند بالا', 'Upper'], ['پایه', 'Basis'], ['باند پایین', 'Lower']],
  supertrend: [['روند صعودی', 'Up Trend'], ['روند نزولی', 'Down Trend']],
  aroon: [['آرون بالا', 'Aroon Up'], ['آرون پایین', 'Aroon Down']],
  vortex: [['VI +', 'VI +'], ['VI -', 'VI -']],
  psar: [['زیر قیمت', 'Below'], ['بالای قیمت', 'Above']],
  elderRay: [['قدرت گاو', 'Bull Power'], ['قدرت خرس', 'Bear Power']],
  chandeKroll: [['توقف خرید', 'Stop Long'], ['توقف فروش', 'Stop Short']],
  maCross: [['میانگین کوتاه', 'Short'], ['میانگین بلند', 'Long']],
  avwap: [['VWAP', 'VWAP'], ['باند بالا', 'Upper Band'], ['باند پایین', 'Lower Band']],
};

export function plotName(key: string, index: number, fallback: string, fa: boolean): string {
  const n = PLOT_NAMES[key]?.[index];
  if (n) return fa ? n[0] : n[1];
  return fallback;
}

/** Interval choices for an indicator's own timeframe. */
export const TIMEFRAMES: { id: string; fa: string; en: string }[] = [
  { id: '', fa: 'چارت', en: 'Chart' },
  { id: '5', fa: '۵ دقیقه', en: '5 minutes' }, { id: '15', fa: '۱۵ دقیقه', en: '15 minutes' }, { id: '30', fa: '۳۰ دقیقه', en: '30 minutes' },
  { id: '60', fa: '۱ ساعت', en: '1 hour' }, { id: '120', fa: '۲ ساعت', en: '2 hours' }, { id: '240', fa: '۴ ساعت', en: '4 hours' },
  { id: '1D', fa: '۱ روز', en: '1 day' }, { id: '1W', fa: '۱ هفته', en: '1 week' }, { id: '1M', fa: '۱ ماه', en: '1 month' },
];
