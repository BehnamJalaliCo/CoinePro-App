// Names, categories and tabs for the indicator dialog. The studies themselves are the old Pro-Chart
// registry (engine/indicators.js) plus the ones added for TradingView parity (indicators_ext_c.js);
// this adds TradingView's English names, the Persian names written the way the app writes Persian
// (no kasra marks, «ه‌ی» for the ezafe), the categories, TradingView's tabs (Indicators, Profiles,
// Patterns; Strategies are NamaScript examples), NEW/BETA badges and the curated lists.
import { STUDIES as REGISTRY } from '../engine/indicators_ext_c.js';
import { TV_PATTERNS } from '../engine/candlePatterns.js';
import { shortName } from './studies';

const ABBR: Record<string, string> = { rsi: 'RSI', macd: 'MACD', ema: 'EMA', ma: 'MA', bb: 'BB', stoch: 'Stoch', atr: 'ATR', adx: 'ADX', obv: 'OBV', cci: 'CCI', mfi: 'MFI', vwap: 'VWAP', willr: '%R', dmi: 'DMI', psar: 'SAR', stochrsi: 'Stoch RSI', crsi: 'CRSI', mtfRsi: 'MTF RSI', cvd: 'CVD', vpVisible: 'VPVR', vpSession: 'SVP', vpPeriodic: 'PVP', vpFixed: 'FRVP', vpAnchored: 'AVP', twap: 'TWAP', rci: 'RCI', bbTrend: 'BBTrend', rollingVwap: 'Rolling VWAP' };

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
  candlePatterns: 'All Candlestick Patterns', zigzag: 'Zig Zag', autoFib: 'Auto Fib Retracement', srLevels: 'Support and Resistance', supplyDemand: 'Supply and Demand Zones',
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
  // Added for TradingView parity.
  chopZone: 'Chop Zone', correlation: 'Correlation Coefficient', ratio: 'Ratio', spread: 'Spread',
  volumeDelta: 'Volume Delta', cvd: 'Cumulative Volume Delta', upDownVolume: 'Up/Down Volume', rollingVwap: 'Rolling VWAP', twap: 'Time Weighted Average Price',
  visibleAvgPrice: 'Visible Average Price', relVolAtTime: 'Relative Volume at Time', rci: 'Rank Correlation Index', trendStrength: 'Trend Strength Index',
  bbTrend: 'BBTrend', bollingerBars: 'Bollinger Bars', smiErgodicOsc: 'SMI Ergodic Oscillator', bullBearPower: 'Bull Bear Power', vol24h: '24-hour Volume',
  techRatings: 'Technical Ratings', autoFibExt: 'Auto Fib Extension', autoTrendlines: 'Auto Trendlines', autoPitchfork: 'Auto Pitchfork', autoChartPatterns: 'Auto Chart Patterns',
  prevHLC: 'Previous High, Low and Close', performance: 'Performance', seasonality: 'Seasonality', moonPhases: 'Moon Phases',
  vpVisible: 'Visible Range Volume Profile', vpSession: 'Session Volume Profile', vpPeriodic: 'Periodic Volume Profile', vpFixed: 'Fixed Range Volume Profile', vpAnchored: 'Anchored Volume Profile',
  ...Object.fromEntries(TV_PATTERNS.map((p) => [`cp_${p.id}`, p.en])),
};

export type Category = 'trend' | 'bands' | 'oscillators' | 'volatility' | 'volume' | 'levels' | 'patterns' | 'profiles';
export type DialogTab = 'indicators' | 'strategies' | 'profiles' | 'patterns';

const CATEGORY_KEYS: Record<Category, string[]> = {
  trend: ['ma', 'ema', 'wma', 'hma', 'smma', 'zlema', 'kama', 't3', 'mcginley', 'linreg', 'lsma', 'dema', 'tema', 'vwma', 'alma', 'maRibbon', 'gmma', 'maCross', 'mtfEma', 'vwap', 'avwap', 'supertrend', 'psar', 'ichimoku', 'alligator', 'chandeKroll', 'chandelier', 'volatilityStop', 'median', 'typicalPrice', 'weightedClose', 'rollingVwap', 'twap', 'visibleAvgPrice', 'trendStrength', 'autoTrendlines', 'ratio', 'spread'],
  bands: ['bb', 'keltner', 'donchian', 'envelopes', 'stdErrBands', 'linRegChannel', 'bollingerBars', 'autoPitchfork'],
  oscillators: ['rsi', 'macd', 'stoch', 'stochrsi', 'cci', 'willr', 'adx', 'dmi', 'aroon', 'aroonOsc', 'mom', 'roc', 'trix', 'ppo', 'ao', 'ac', 'accelerator', 'uo', 'fisher', 'crsi', 'smi', 'smiErgodic', 'tsi', 'cmo', 'kst', 'coppock', 'stc', 'rvi', 'bop', 'vortex', 'dpo', 'choppiness', 'massIndex', 'elderRay', 'eom', 'woodiesCci', 'pmo', 'mtfRsi', 'bbpercent', 'chopZone', 'correlation', 'rci', 'bbTrend', 'smiErgodicOsc', 'bullBearPower', 'techRatings'],
  volatility: ['atr', 'stddev', 'hv', 'chaikinVol', 'adr', 'ulcer', 'bbw', 'bbWidth', 'rviVol'],
  volume: ['obv', 'mfi', 'cmf', 'netVolume', 'volume', 'adline', 'chaikinOsc', 'forceIndex', 'klinger', 'pvt', 'pvi', 'nvi', 'volumeOsc', 'pvo', 'volumeDelta', 'cvd', 'upDownVolume', 'relVolAtTime', 'vol24h'],
  levels: ['pivots', 'pivotsMulti', 'pivotHL', 'fractals', 'zigzag', 'autoFib', 'autoFibExt', 'srLevels', 'supplyDemand', 'prevHLC', 'performance', 'seasonality', 'moonPhases'],
  patterns: ['autoChartPatterns', 'candlePatterns', ...TV_PATTERNS.map((p) => `cp_${p.id}`)],
  profiles: ['vpVisible', 'vpSession', 'vpPeriodic', 'vpFixed', 'vpAnchored'],
};

export const CATEGORIES: { id: Category; fa: string; en: string; icon: string }[] = [
  { id: 'trend', fa: 'روند و میانگین‌ها', en: 'Trend & averages', icon: 'ph-trend-up' },
  { id: 'bands', fa: 'باندها و کانال‌ها', en: 'Bands & channels', icon: 'tool_channel' },
  { id: 'oscillators', fa: 'نوسان‌گرها', en: 'Oscillators', icon: 'ph-chart-line-up' },
  { id: 'volatility', fa: 'نوسان', en: 'Volatility', icon: 'ph-lightning' },
  { id: 'volume', fa: 'حجم', en: 'Volume', icon: 'ph-chart-bar' },
  { id: 'levels', fa: 'سطوح خودکار', en: 'Levels & auto tools', icon: 'tool_fib' },
];

/** Persian the way the app writes it: no kasra marks, «ه‌ی» for the ezafe the old labels wrote with a hamza. */
// Built from code points so this file never carries the marks it removes.
const KASRA = new RegExp(String.fromCharCode(0x0650), 'g');
const HAMZA_EZAFE = new RegExp(`${String.fromCharCode(0x0647, 0x0654)}|${String.fromCharCode(0x06c0)}`, 'g');

export function cleanFa(s: string): string {
  return s.replace(KASRA, '').replace(HAMZA_EZAFE, 'ه‌ی').replace(/\s+/g, ' ').trim();
}

/** Persian name without the Latin in brackets the engine's labels carry. */
const faName = (label: string) => cleanFa(label.replace(/\s*\([A-Za-z0-9%±/ .,\-&']+\)\s*/g, ' ')) || cleanFa(label);

// Badges, as TradingView marks the newest built-ins and the ones still settling.
const BADGES: Record<string, 'NEW' | 'BETA'> = {
  chopZone: 'NEW', volumeDelta: 'NEW', cvd: 'NEW', upDownVolume: 'NEW', rollingVwap: 'NEW', visibleAvgPrice: 'NEW', relVolAtTime: 'NEW',
  rci: 'NEW', trendStrength: 'NEW', bbTrend: 'NEW', bollingerBars: 'NEW', smiErgodicOsc: 'NEW', autoFibExt: 'NEW', autoTrendlines: 'BETA', autoPitchfork: 'BETA', autoChartPatterns: 'BETA',
  prevHLC: 'NEW', performance: 'NEW', seasonality: 'BETA', moonPhases: 'NEW', vpVisible: 'NEW', vpSession: 'NEW', vpPeriodic: 'NEW', vpFixed: 'NEW', vpAnchored: 'NEW',
  techRatings: 'NEW', correlation: 'NEW', supplyDemand: 'BETA', srLevels: 'BETA',
};

export interface CatalogEntry { key: string; fa: string; en: string; abbr: string; category: Category; overlay: boolean; tab: DialogTab; badge?: 'NEW' | 'BETA'; /** A real abbreviation (RSI, VWAP), shown beside the name; absent for the rest. */ short?: string }

export const CATALOG: CatalogEntry[] = Object.entries(REGISTRY).map(([key, def]) => {
  const category = (Object.keys(CATEGORY_KEYS) as Category[]).find((c) => CATEGORY_KEYS[c].includes(key)) ?? (def.pane === 'main' ? 'trend' : 'oscillators');
  const en = EN[key] ?? key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase());
  const tab: DialogTab = category === 'patterns' ? 'patterns' : category === 'profiles' ? 'profiles' : 'indicators';
  const bracket = /\(([A-Za-z0-9%± .\-]+)\)/.exec(def.label)?.[1]?.trim();
  const short = ABBR[key] ?? (bracket && bracket !== en && bracket.length <= 12 ? bracket : undefined);
  return { short, key, fa: key.startsWith('cp_') ? cleanFa(TV_PATTERNS.find((p) => `cp_${p.id}` === key)?.fa ?? def.label) : faName(def.label), en, abbr: ABBR[key] ?? shortName(key, def.label), category, overlay: def.pane === 'main', tab, badge: BADGES[key] };
}).sort((a, b) => a.en.localeCompare(b.en));

export const POPULAR = ['rsi', 'macd', 'ema', 'ma', 'bb', 'vwap', 'stoch', 'supertrend', 'ichimoku', 'atr', 'adx', 'obv'];

/** TradingView's Community views, made from this catalogue (no feed behind them): in order, as ranked. */
export const CURATED: Record<'editors' | 'top' | 'trending', string[]> = {
  editors: ['ichimoku', 'supertrend', 'vwap', 'vpVisible', 'bb', 'macd', 'rsi', 'cvd', 'techRatings', 'autoTrendlines', 'pivots', 'alligator', 'chopZone', 'bbTrend'],
  top: ['rsi', 'macd', 'ma', 'ema', 'bb', 'stoch', 'adx', 'vwap', 'obv', 'atr', 'supertrend', 'ichimoku', 'stochrsi', 'cci', 'mfi'],
  trending: ['cvd', 'volumeDelta', 'vpSession', 'rollingVwap', 'supertrend', 'avwap', 'bbTrend', 'rci', 'trendStrength', 'relVolAtTime', 'stc', 'maRibbon', 'autoFibExt', 'prevHLC'],
};

export const entryOf = (key: string) => CATALOG.find((c) => c.key === key);

/** Search text, folded: Arabic ي/ك to Persian ی/ک, the zero-width non-joiner to a space, no diacritics, lower case. */
export function fold(s: string): string {
  return (s ?? '').toString().toLowerCase()
    .replace(/ي/g, 'ی').replace(/ك/g, 'ک').replace(/[ىئ]/g, 'ی').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه')
    .replace(/[‌‍]/g, ' ').replace(/[ً-ٰٟ]/g, '').replace(/\s+/g, ' ').trim();
}

/** Ranks a catalogue entry for a search (lower is better), or null when it does not match. */
export function rank(c: CatalogEntry, query: string): number | null {
  const q = fold(query);
  if (!q) return 0;
  const en = c.en.toLowerCase(); const abbr = c.abbr.toLowerCase(); const fa = fold(c.fa); const key = c.key.toLowerCase();
  if (!(en.includes(q) || abbr.includes(q) || fa.includes(q) || key.includes(q))) return null;
  // TradingView's order: the abbreviation itself, then names starting with the query, then words.
  if (abbr === q || key === q) return 0;
  if (en.startsWith(q) || fa.startsWith(q)) return 1;
  if (en.split(/[\s/(-]+/).some((w) => w.startsWith(q)) || fa.split(' ').some((w) => w.startsWith(q))) return 2;
  return 3;
}
