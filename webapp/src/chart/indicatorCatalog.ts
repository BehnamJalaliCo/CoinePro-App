// Names and categories for the indicator dialog. The studies themselves are the old Pro-Chart
// registry (engine/indicators.js); this adds TradingView's English names, the Persian names
// written the way the app writes Persian (no kasra marks, «ه‌ی» for the ezafe), and categories.
import { REGISTRY } from '../engine/indicators.js';
import { shortName } from './studies';

const ABBR: Record<string, string> = { rsi: 'RSI', macd: 'MACD', ema: 'EMA', ma: 'MA', bb: 'BB', stoch: 'Stoch', atr: 'ATR', adx: 'ADX', obv: 'OBV', cci: 'CCI', mfi: 'MFI', vwap: 'VWAP', willr: '%R', dmi: 'DMI', psar: 'SAR', stochrsi: 'Stoch RSI', crsi: 'CRSI', mtfRsi: 'MTF RSI' };

const EN: Record<string, string> = {
  ma: 'Moving Average', ema: 'Moving Average Exponential', wma: 'Moving Average Weighted', hma: 'Hull Moving Average', vwap: 'VWAP', avwap: 'Anchored VWAP',
  smma: 'Smoothed Moving Average', zlema: 'Zero-Lag EMA', kama: 'Kaufman Adaptive MA', t3: 'Tillson T3', mcginley: 'McGinley Dynamic',
  linreg: 'Linear Regression Curve', lsma: 'Least Squares Moving Average', dema: 'Double EMA', tema: 'Triple EMA',
  vwma: 'Volume Weighted Moving Average', alma: 'Arnaud Legoux Moving Average', maRibbon: 'Moving Average Ribbon', gmma: 'Guppy Multiple Moving Average', maCross: 'MA Cross',
  envelopes: 'Envelopes', mtfEma: 'Multi-timeframe EMA', mtfRsi: 'Multi-timeframe RSI',
  bb: 'Bollinger Bands', bbpercent: 'Bollinger Bands %B', bbw: 'Bollinger BandWidth', bbWidth: 'Bollinger BandWidth (%)',
  keltner: 'Keltner Channels', donchian: 'Donchian Channels', stdErrBands: 'Standard Error Bands', linRegChannel: 'Linear Regression Channel',
  supertrend: 'Supertrend', psar: 'Parabolic SAR', ichimoku: 'Ichimoku Cloud', alligator: 'Williams Alligator',
  chandeKroll: 'Chande Kroll Stop', chandelier: 'Chandelier Exit', volatilityStop: 'Volatility Stop',
  pivots: 'Pivot Points Standard', pivotsMulti: 'Pivot Points (Multi)', pivotHL: 'Pivot Points High Low', fractals: 'Williams Fractals',
  candlePatterns: 'Candlestick Patterns', zigzag: 'Zig Zag', autoFib: 'Auto Fib Retracement', srLevels: 'Support and Resistance', supplyDemand: 'Supply and Demand Zones',
  median: 'Median Price', typicalPrice: 'Typical Price', weightedClose: 'Weighted Close',
  rsi: 'Relative Strength Index', macd: 'MACD', stoch: 'Stochastic', stochrsi: 'Stochastic RSI',
  cci: 'Commodity Channel Index', willr: 'Williams %R', adx: 'Average Directional Index', dmi: 'Directional Movement Index', aroon: 'Aroon', aroonOsc: 'Aroon Oscillator',
  mom: 'Momentum', roc: 'Rate Of Change', trix: 'TRIX', ppo: 'Price Oscillator', pvo: 'Percentage Volume Oscillator', ao: 'Awesome Oscillator', ac: 'Accelerator Oscillator', accelerator: 'Accelerator Oscillator (classic)',
  uo: 'Ultimate Oscillator', fisher: 'Fisher Transform', crsi: 'Connors RSI', smi: 'Stochastic Momentum Index', smiErgodic: 'SMI Ergodic Indicator',
  tsi: 'True Strength Index', cmo: 'Chande Momentum Oscillator', kst: 'Know Sure Thing', coppock: 'Coppock Curve', stc: 'Schaff Trend Cycle',
  rvi: 'Relative Vigor Index', rviVol: 'Relative Volatility Index', bop: 'Balance of Power', vortex: 'Vortex Indicator', dpo: 'Detrended Price Oscillator',
  choppiness: 'Choppiness Index', massIndex: 'Mass Index', elderRay: 'Elder Ray (Bull/Bear Power)', eom: 'Ease of Movement', woodiesCci: "Woodies CCI", pmo: 'Price Momentum Oscillator',
  atr: 'Average True Range', stddev: 'Standard Deviation', hv: 'Historical Volatility', chaikinVol: 'Chaikin Volatility', adr: 'Average Day Range', ulcer: 'Ulcer Index',
  obv: 'On Balance Volume', mfi: 'Money Flow Index', cmf: 'Chaikin Money Flow', netVolume: 'Net Volume', volume: 'Volume (with MA)',
  adline: 'Accumulation/Distribution', chaikinOsc: 'Chaikin Oscillator', forceIndex: 'Elder Force Index', klinger: 'Klinger Oscillator',
  pvt: 'Price Volume Trend', pvi: 'Positive Volume Index', nvi: 'Negative Volume Index', volumeOsc: 'Volume Oscillator',
};

export type Category = 'trend' | 'bands' | 'oscillators' | 'volatility' | 'volume' | 'levels';

const CATEGORY_KEYS: Record<Category, string[]> = {
  trend: ['ma', 'ema', 'wma', 'hma', 'smma', 'zlema', 'kama', 't3', 'mcginley', 'linreg', 'lsma', 'dema', 'tema', 'vwma', 'alma', 'maRibbon', 'gmma', 'maCross', 'mtfEma', 'vwap', 'avwap', 'supertrend', 'psar', 'ichimoku', 'alligator', 'chandeKroll', 'chandelier', 'volatilityStop', 'median', 'typicalPrice', 'weightedClose'],
  bands: ['bb', 'keltner', 'donchian', 'envelopes', 'stdErrBands', 'linRegChannel'],
  oscillators: ['rsi', 'macd', 'stoch', 'stochrsi', 'cci', 'willr', 'adx', 'dmi', 'aroon', 'aroonOsc', 'mom', 'roc', 'trix', 'ppo', 'ao', 'ac', 'accelerator', 'uo', 'fisher', 'crsi', 'smi', 'smiErgodic', 'tsi', 'cmo', 'kst', 'coppock', 'stc', 'rvi', 'bop', 'vortex', 'dpo', 'choppiness', 'massIndex', 'elderRay', 'eom', 'woodiesCci', 'pmo', 'mtfRsi', 'bbpercent'],
  volatility: ['atr', 'stddev', 'hv', 'chaikinVol', 'adr', 'ulcer', 'bbw', 'bbWidth', 'rviVol'],
  volume: ['obv', 'mfi', 'cmf', 'netVolume', 'volume', 'adline', 'chaikinOsc', 'forceIndex', 'klinger', 'pvt', 'pvi', 'nvi', 'volumeOsc', 'pvo'],
  levels: ['pivots', 'pivotsMulti', 'pivotHL', 'fractals', 'candlePatterns', 'zigzag', 'autoFib', 'srLevels', 'supplyDemand'],
};

export const CATEGORIES: { id: Category; fa: string; en: string }[] = [
  { id: 'trend', fa: 'روند و میانگین متحرک', en: 'Trend & moving averages' },
  { id: 'bands', fa: 'باندها و کانال‌ها', en: 'Bands & channels' },
  { id: 'oscillators', fa: 'نوسان‌گرها', en: 'Oscillators' },
  { id: 'volatility', fa: 'نوسان', en: 'Volatility' },
  { id: 'volume', fa: 'حجم', en: 'Volume' },
  { id: 'levels', fa: 'سطوح و الگوها', en: 'Levels & patterns' },
];

/** Persian the way the app writes it: no kasra marks, «ه‌ی» for the ezafe the old labels wrote with a hamza. */
// Built from code points so this file never carries the marks it removes.
const KASRA = new RegExp(String.fromCharCode(0x0650), 'g');
const HAMZA_EZAFE = new RegExp(`${String.fromCharCode(0x0647, 0x0654)}|${String.fromCharCode(0x06c0)}`, 'g');

export function cleanFa(s: string): string {
  return s.replace(KASRA, '').replace(HAMZA_EZAFE, 'ه‌ی').replace(/\s+/g, ' ').trim();
}

export interface CatalogEntry { key: string; fa: string; en: string; abbr: string; category: Category; overlay: boolean }

export const CATALOG: CatalogEntry[] = Object.entries(REGISTRY).map(([key, def]) => {
  const category = (Object.keys(CATEGORY_KEYS) as Category[]).find((c) => CATEGORY_KEYS[c].includes(key)) ?? (def.pane === 'main' ? 'trend' : 'oscillators');
  const en = EN[key] ?? key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
  return { key, fa: cleanFa(def.label), en, abbr: ABBR[key] ?? shortName(key, def.label), category, overlay: def.pane === 'main' };
}).sort((a, b) => a.en.localeCompare(b.en));

export const POPULAR = ['rsi', 'macd', 'ema', 'ma', 'bb', 'vwap', 'stoch', 'supertrend', 'ichimoku', 'atr', 'adx', 'obv'];

export const entryOf = (key: string) => CATALOG.find((c) => c.key === key);
