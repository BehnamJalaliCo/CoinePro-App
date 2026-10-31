// The heat map's arithmetic (OLD feature/heatmap: HeatmapOptions, HeatmapFacts, HeatmapAsset /
// HeatmapMetrics, HeatmapColours, HeatmapFormat, HeatmapPlan, Treemap, core/symbols SymbolRanking).
// Pure: what a missing figure falls back to, which quantity each colour mode plots, and how the
// blocks are cut are the product decisions, and they are tested without a screen.
import { instrumentOf, marketOf } from '../data/symbols';
import type { Candle, Market, Ticker } from '../data/types';

// ── Options ─────────────────────────────────────────────────────────────────────────────────────
/** Area: the offline liquidity ranking (the default — complete before a bar is read), base-asset volume, turnover, or equal. */
export type HeatmapSize = 'liquidity' | 'volume' | 'turnover' | 'mono';
/** Colour: day change, the period's performance, volatility against the market's own normal, place in range, opening gap. */
export type HeatmapColour = 'change' | 'performance' | 'volatility' | 'range' | 'gap';
export type HeatmapPeriod = 'week' | 'month' | 'quarter';
export type HeatmapPalette = 'classic' | 'blind' | 'mono';
export type HeatmapGrouping = 'none' | 'class' | 'quote';
export type HeatmapDensity = 'focused' | 'standard' | 'everything';
export const PERIOD_BARS: Record<HeatmapPeriod, number> = { week: 7, month: 30, quarter: 90 };
export const DENSITY_TILES: Record<HeatmapDensity, number | null> = { focused: 48, standard: 144, everything: null };

export interface HeatmapOptions { size: HeatmapSize; colour: HeatmapColour; period: HeatmapPeriod; palette: HeatmapPalette; grouping: HeatmapGrouping; density: HeatmapDensity }
export const DEFAULT_OPTIONS: HeatmapOptions = { size: 'liquidity', colour: 'change', period: 'month', palette: 'classic', grouping: 'class', density: 'standard' };

// ── Ranking (core/symbols SymbolRanking) ──────────────────────────────────────────────────────────
const FOREX_MAJORS = [
  'EURUSD', 'GBPUSD', 'USDJPY', 'XAUUSD', 'USDCHF', 'AUDUSD', 'USDCAD', 'NZDUSD',
  'EURJPY', 'GBPJPY', 'XAGUSD', 'EURGBP', 'US30', 'US500', 'US100', 'USOIL',
  'EURCHF', 'AUDJPY', 'EURAUD', 'EURCAD', 'GBPCHF', 'AUDNZD', 'NZDJPY', 'GBPAUD',
  'GBPCAD', 'USDSGD', 'USDHKD', 'USDTRY', 'USDMXN', 'USDZAR', 'EURNZD', 'CADJPY',
  'CHFJPY', 'AUDCAD', 'AUDCHF', 'CADCHF', 'NZDCAD', 'GBPNZD', 'XPTUSD', 'XPDUSD',
  'UKOIL', 'NATGAS', 'GER40', 'UK100', 'JPN225', 'FRA40', 'EU50', 'HK50', 'AUS200',
];
const CRYPTO_MAJORS = [
  'BTC', 'ETH', 'USDT', 'BNB', 'SOL', 'XRP', 'USDC', 'ADA', 'DOGE', 'TRX',
  'AVAX', 'SHIB', 'DOT', 'LINK', 'BCH', 'NEAR', 'POL', 'MATIC', 'LTC', 'UNI',
  'ICP', 'DAI', 'ETC', 'APT', 'XLM', 'RENDER', 'ATOM', 'XMR', 'OKB', 'FIL',
  'HBAR', 'ARB', 'VET', 'MKR', 'INJ', 'OP', 'IMX', 'GRT', 'AAVE', 'STX',
  'TAO', 'RUNE', 'FTM', 'SEI', 'THETA', 'FLOW', 'LDO', 'TIA', 'SUI', 'ALGO',
  'EGLD', 'QNT', 'BSV', 'GALA', 'JUP', 'FLR', 'KCS', 'PYTH', 'ORDI', 'WIF',
  'JASMY', 'BONK', 'PEPE', 'FLOKI', 'AXS', 'SAND', 'MANA', 'CHZ', 'EOS', 'XTZ',
  'NEO', 'KAVA', 'MINA', 'ROSE', 'IOTA', 'GMX', 'ENS', 'DYDX', 'CFX', 'ONDO',
  'WLD', 'ENA', 'STRK', 'ZK', 'W', 'ETHFI', 'BLUR', '1INCH', 'COMP', 'CRV',
  'SNX', 'SUSHI', 'YFI', 'ZEC', 'DASH', 'BAT', 'ZIL', 'ONE', 'CELO', 'ANKR',
];
// Aliases the feeds use for the same instrument (web spellings of the OLD canonical names).
const CANONICAL: Record<string, string> = { NAS100: 'US100', DE40: 'GER40', XTIUSD: 'USOIL', XBRUSD: 'UKOIL', XNGUSD: 'NATGAS' };
const CLASS_WEIGHTS: Record<Market, number> = { metals: 0, forex: 1, crypto: 2, indices: 3, energy: 4 };
const CLASS_COUNT = 6;
export const UNRANKED = Number.MAX_SAFE_INTEGER;
/** Place in the offline liquidity ranking, or UNRANKED. */
export function rankOf(symbol: string): number {
  const inst = instrumentOf(symbol);
  const position = inst.market === 'crypto' ? CRYPTO_MAJORS.indexOf(inst.base) : FOREX_MAJORS.indexOf(CANONICAL[symbol] ?? symbol);
  if (position < 0) return UNRANKED;
  return position * CLASS_COUNT + (CLASS_WEIGHTS[inst.market] ?? CLASS_COUNT);
}

// ── One market (HeatmapAsset, HeatmapFacts) ───────────────────────────────────────────────────────
export interface HeatmapAsset {
  symbol: string;
  market: Market;
  quote: string;
  price: number;
  changePercent: number | null;
  periodPercent: number | null;
  volatilityPercent: number | null;
  typicalVolatilityPercent: number | null;
  openPrice: number | null;
  previousClose: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  volume: number | null;
  turnover: number | null;
  fundingRatePercent: number | null;
  /** Whether this market's own daily bars have been asked for and answered. */
  resolved: boolean;
}

const positive = (v: number | null | undefined): number | null => (v != null && Number.isFinite(v) && v > 0 ? v : null);
const finite = (v: number | null | undefined): number | null => (v != null && Number.isFinite(v) ? v : null);
const percentFrom = (reference: number, price: number): number | null => finite(((price - reference) / reference) * 100);
/** How many closed days a "normal" is taken over, and the fewest that make one. */
const TYPICAL_WINDOW = 30;
const MIN_TYPICAL_BARS = 10;

/**
 * One market's figures from its live ticker and its daily bars (oldest first), or null with no
 * price. The ticker wins every figure both can answer (the venue's own twenty-four hours are what
 * a reader holds the map against); the bars keep the period return, the median daily range, and —
 * because the feeds' tickers carry no session open — the open and the close before it. The live
 * price widens the day's range rather than sitting past the end of it, and with no range reported
 * at all the range is unknown, never zero.
 */
export function assetOf(symbol: string, ticker: Ticker | null, bars: Candle[] = [], period: HeatmapPeriod = 'month', resolved = bars.length > 0): HeatmapAsset | null {
  const last = bars[bars.length - 1];
  const price = positive(ticker?.last) ?? positive(last?.c);
  if (price == null) return null;
  const inst = instrumentOf(symbol, price);
  const barPreviousClose = positive(bars[bars.length - 2]?.c);
  const open = positive(last?.o);
  const previousClose = barPreviousClose ?? positive(ticker?.prevClose);
  const reference = previousClose ?? open;
  const reportedHigh = finite(ticker?.high ?? last?.h);
  const reportedLow = finite(ticker?.low ?? last?.l);
  const high = reportedHigh != null ? Math.max(reportedHigh, price) : null;
  const low = reportedLow != null ? Math.min(reportedLow, price) : null;
  // Zero is what the MT5 side sends with no volume to report: unknown, not «nothing traded».
  const volume = positive(ticker?.volume) ?? positive(last?.v);
  const turnover = volume != null ? (inst.market === 'crypto' ? volume * price : barTurnover(last, volume)) : null;
  return {
    symbol, market: inst.market, quote: inst.quote, price,
    changePercent: finite(ticker?.changePct) ?? (reference != null ? percentFrom(reference, price) : null),
    periodPercent: periodPercent(bars, price, period),
    volatilityPercent: dayRangePercent(high, low, price),
    typicalVolatilityPercent: typicalRangePercent(bars),
    openPrice: open, previousClose, dayHigh: high, dayLow: low, volume, turnover,
    fundingRatePercent: null,
    resolved,
  };
}

/** Quantity at the bar's typical price: the closest a candle gets to a traded value. */
function barTurnover(last: Candle | undefined, volume: number): number | null {
  if (!last) return null;
  const typical = (last.h + last.l + last.c) / 3;
  return Number.isFinite(typical) && typical > 0 ? typical * volume : null;
}
/** Close-to-price over the window; null when the series is shorter than the window. */
function periodPercent(bars: Candle[], price: number, period: HeatmapPeriod): number | null {
  const index = bars.length - 1 - PERIOD_BARS[period];
  if (index < 0) return null;
  const reference = positive(bars[index]!.c);
  return reference == null ? null : percentFrom(reference, price);
}
function dayRangePercent(high: number | null, low: number | null, price: number): number | null {
  if (high == null || low == null || price <= 0) return null;
  const v = ((high - low) / price) * 100;
  return Number.isFinite(v) && v >= 0 ? v : null;
}
/** The median daily range of the recent closed bars (the bar in progress excluded). */
function typicalRangePercent(bars: Candle[]): number | null {
  if (bars.length < MIN_TYPICAL_BARS + 1) return null;
  const closed = bars.slice(Math.max(0, bars.length - 1 - TYPICAL_WINDOW), bars.length - 1);
  const ranges = closed.flatMap((b) => {
    const close = positive(b.c);
    if (close == null) return [];
    const r = ((b.h - b.l) / close) * 100;
    return Number.isFinite(r) && r >= 0 ? [r] : [];
  }).sort((a, b) => a - b);
  if (ranges.length < MIN_TYPICAL_BARS) return null;
  const median = ranges[Math.floor(ranges.length / 2)]!;
  return median > 0 ? median : null;
}

// ── Metrics ───────────────────────────────────────────────────────────────────────────────────────
const UNRANKED_PLACE = 1024;
function rankWeight(a: HeatmapAsset): number {
  const rank = rankOf(a.symbol);
  return 1 / (1 + (rank === UNRANKED ? UNRANKED_PLACE : rank));
}
/** The area a market claims; a missing figure falls back to the ranking, never to zero. */
export function weightOf(a: HeatmapAsset, size: HeatmapSize): number {
  if (size === 'mono') return 1;
  const reported = size === 'volume' ? a.volume : size === 'turnover' ? a.turnover ?? (a.volume != null ? a.volume * a.price : null) : null;
  return positive(reported) ?? rankWeight(a);
}
/** The quantity the ramp plots, or null — and null draws as unknown (hatched), not as flat. */
export function valueOf(a: HeatmapAsset, colour: HeatmapColour): number | null {
  let v: number | null;
  switch (colour) {
    case 'change': v = a.changePercent; break;
    case 'performance': v = a.periodPercent; break;
    case 'volatility': {
      const actual = a.volatilityPercent != null && Number.isFinite(a.volatilityPercent) && a.volatilityPercent >= 0 ? a.volatilityPercent : null;
      const typical = positive(a.typicalVolatilityPercent);
      v = actual != null && typical != null ? actual - typical : null; break;
    }
    case 'range': {
      const high = finite(a.dayHigh); const low = finite(a.dayLow);
      v = high != null && low != null && high - low > 0 ? ((a.price - low) / (high - low) * 2 - 1) * 100 : null; break;
    }
    case 'gap': {
      const open = finite(a.openPrice); const close = positive(a.previousClose);
      v = open != null && close != null ? ((open - close) / close) * 100 : null; break;
    }
  }
  return finite(v);
}
export const anyValueFor = (assets: HeatmapAsset[], colour: HeatmapColour) => assets.some((a) => valueOf(a, colour) != null);
export function anyWeightFor(assets: HeatmapAsset[], size: HeatmapSize): boolean {
  if (size === 'liquidity' || size === 'mono') return true;
  if (size === 'volume') return assets.some((a) => positive(a.volume) != null);
  return assets.some((a) => positive(a.turnover ?? (a.volume != null ? a.volume * a.price : null)) != null);
}
/** Each metric's band for the scale: a quarter's move and a day's are not the same size. */
export function scaleBoundsOf(colour: HeatmapColour): [number, number] {
  switch (colour) {
    case 'change': return [0.5, 25];
    case 'performance': return [1, 60];
    case 'volatility': return [0.2, 10];
    case 'range': return [100, 100];
    case 'gap': return [0.2, 10];
  }
}
/** The 90th percentile of the magnitudes (truncated index), held inside the band. */
export function scaleOfValues(values: number[], floor = 0.5, ceiling = 25): number {
  const m = values.filter(Number.isFinite).map(Math.abs).sort((a, b) => a - b);
  if (!m.length) return floor;
  const index = Math.min(m.length - 1, Math.max(0, Math.trunc(0.9 * (m.length - 1))));
  return Math.min(ceiling, Math.max(floor, m[index]!));
}
export function scaleFor(values: number[], colour: HeatmapColour): number {
  const [floor, ceiling] = scaleBoundsOf(colour);
  return scaleOfValues(values, floor, ceiling);
}

// ── Colours (HeatmapColours) ──────────────────────────────────────────────────────────────────────
const NEUTRAL = 0x2b3139, MONO_NEUTRAL = 0x6e7681, UNKNOWN = 0x0e1116, UNKNOWN_HATCH = 0x2c333c;
const INK_LIGHT = 0xf0f1f2, INK_DARK = 0x0b0e11;
const STOPS: Record<string, [number, number]> = {
  classicUp: [0x0f7a45, 0x00c46a], classicDown: [0x8c2331, 0xf6465d],
  blindUp: [0x1b4f8a, 0x2f86d6], blindDown: [0x8a4a1e, 0xe07b18],
  monoUp: [0xacb3bc, 0xedeff2], monoDown: [0x3a4048, 0x14171c],
};
const ch = (rgb: number, shift: number) => (rgb >> shift) & 0xff;
function blend(from: number, to: number, amount: number): number {
  const t = Math.min(1, Math.max(0, amount));
  const mix = (s: number) => Math.min(255, Math.max(0, Math.round(ch(from, s) + (ch(to, s) - ch(from, s)) * t)));
  return (mix(16) << 16) | (mix(8) << 8) | mix(0);
}
const ramp = (m: number, neutral: number, mid: number, strong: number) => (m <= 0.5 ? blend(neutral, mid, m / 0.5) : blend(mid, strong, (m - 0.5) / 0.5));
export const neutralOf = (p: HeatmapPalette) => (p === 'mono' ? MONO_NEUTRAL : NEUTRAL);
export const hex = (rgb: number) => `#${rgb.toString(16).padStart(6, '0')}`;
export const UNKNOWN_FILL = hex(UNKNOWN);
export const HATCH_INK = hex(UNKNOWN_HATCH);
/** A value on the diverging ramp; `risingIsGreen` false swaps the warm and cool ends (not on mono). */
export function colourFor(value: number, scale: number, palette: HeatmapPalette, risingIsGreen = true): number {
  if (!Number.isFinite(value) || !Number.isFinite(scale) || scale <= 0) return neutralOf(palette);
  const t = Math.min(1, Math.max(-1, value / scale));
  if (t === 0) return neutralOf(palette);
  const warmIsUp = !risingIsGreen && palette !== 'mono';
  const rising = t > 0;
  const name = palette === 'mono' ? (rising ? 'monoUp' : 'monoDown') : `${palette === 'blind' ? 'blind' : 'classic'}${rising !== warmIsUp ? 'Up' : 'Down'}`;
  const [mid, strong] = STOPS[name]!;
  return ramp(Math.abs(t), neutralOf(palette), mid, strong);
}
const linear = (c: number) => { const v = c / 255; return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
export const luminanceOf = (rgb: number) => 0.2126 * linear(ch(rgb, 16)) + 0.7152 * linear(ch(rgb, 8)) + 0.0722 * linear(ch(rgb, 0));
/** Dark ink on a light tile, light ink on a dark one. */
export const labelInkFor = (rgb: number) => hex(luminanceOf(rgb) > 0.25 ? INK_DARK : INK_LIGHT);

// ── Format (HeatmapFormat) ────────────────────────────────────────────────────────────────────────
export const ABSENT = '—';
const MINUS = '−';
export function fmtPercent(v: number | null): string {
  if (v == null || !Number.isFinite(v)) return ABSENT;
  return `${v > 0 ? '+' : v < 0 ? MINUS : ''}${Math.abs(v).toFixed(2)}%`;
}
/** A price at the decimals its size needs (OLD MarketNumberFormatter.priceAuto). */
export function fmtPrice(v: number | null): string {
  if (v == null || !Number.isFinite(v)) return ABSENT;
  const m = Math.abs(v);
  const d = m === 0 || m >= 1 ? 2 : m >= 0.01 ? 4 : m >= 0.0001 ? 6 : 8;
  const s = m.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  return v < 0 ? MINUS + s : s;
}
export function fmtAmount(v: number | null): string {
  if (v == null || !Number.isFinite(v)) return ABSENT;
  const m = Math.abs(v);
  const [scaled, suffix] = m >= 1e12 ? [v / 1e12, 'T'] : m >= 1e9 ? [v / 1e9, 'B'] : m >= 1e6 ? [v / 1e6, 'M'] : m >= 1e3 ? [v / 1e3, 'K'] : [v, ''];
  const decimals = Math.abs(scaled) >= 100 ? 0 : Math.abs(scaled) >= 10 ? 1 : 2;
  const s = Math.abs(scaled).toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
  return (scaled < 0 ? MINUS : '') + s + suffix;
}
/** A tile's figure: the place in range as a whole number (−100…100), the rest as signed percentages. */
export function tileFigure(v: number | null, colour: HeatmapColour): string {
  if (v == null || !Number.isFinite(v)) return ABSENT;
  if (colour === 'range') { const r = Math.round(v); return r < 0 ? MINUS + Math.abs(r) : String(r); }
  return fmtPercent(v);
}

// ── Selection and the plan (HeatmapSelection, HeatmapPlanner, Treemap) ────────────────────────────
export type Bucket = { kind: 'class'; market: Market } | { kind: 'quote'; currency: string } | { kind: 'none' };
export const bucketKey = (b: Bucket) => (b.kind === 'class' ? `c:${b.market}` : b.kind === 'quote' ? `q:${b.currency}` : 'none');
export function bucketOf(a: HeatmapAsset, grouping: HeatmapGrouping): Bucket {
  if (grouping === 'class') return { kind: 'class', market: a.market };
  if (grouping === 'quote') return a.quote.trim() ? { kind: 'quote', currency: a.quote.toUpperCase() } : { kind: 'none' };
  return { kind: 'none' };
}
/** The heaviest markets by the current sizing, capped by the density, inside the focused block. */
export function selectAssets(assets: HeatmapAsset[], o: HeatmapOptions, focus: Bucket | null = null): HeatmapAsset[] {
  const scoped = focus == null ? assets : assets.filter((a) => bucketKey(bucketOf(a, o.grouping)) === bucketKey(focus));
  const ordered = scoped.map((a, i) => ({ a, i, w: weightOf(a, o.size) })).sort((x, y) => y.w - x.w || x.i - y.i).map((x) => x.a);
  const cap = DENSITY_TILES[o.density];
  return cap == null || ordered.length <= cap ? ordered : ordered.slice(0, cap);
}

export interface Rect { x: number; y: number; w: number; h: number }
const ZERO: Rect = { x: 0, y: 0, w: 0, h: 0 };
/** Squarified treemap; answers in input order, heaviest placed first. */
export function treemap(weights: number[], width: number, height: number): Rect[] {
  if (!weights.length) return [];
  const out: Rect[] = weights.map(() => ZERO);
  if (width <= 0 || height <= 0) return out;
  const order = weights.map((w, i) => ({ w, i })).filter((x) => Number.isFinite(x.w) && x.w > 0).sort((a, b) => b.w - a.w || a.i - b.i).map((x) => x.i);
  if (!order.length) return out;
  const total = order.reduce((n, i) => n + weights[i]!, 0);
  const scale = (width * height) / total;
  const areas = order.map((i) => weights[i]! * scale);
  const placed = tile(areas, { x: 0, y: 0, w: width, h: height });
  placed.forEach((r, k) => { out[order[k]!] = r; });
  return out;
}
function worst(lo: number, hi: number, sum: number, side: number): number {
  if (sum <= 0 || side <= 0 || lo <= 0) return Number.MAX_VALUE;
  return Math.max((side * side * hi) / (sum * sum), (sum * sum) / (side * side * lo));
}
function tile(areas: number[], container: Rect): Rect[] {
  const out: Rect[] = areas.map(() => ZERO);
  let rect = container;
  let index = 0;
  while (index < areas.length) {
    if (rect.w <= 0 || rect.h <= 0) { while (index < areas.length) out[index++] = { x: rect.x, y: rect.y, w: 0, h: 0 }; return out; }
    const side = Math.min(rect.w, rect.h);
    let sum = 0, lo = Number.MAX_VALUE, hi = 0, best = Number.MAX_VALUE, end = index;
    while (end < areas.length) {
      const area = areas[end]!;
      const nLo = Math.min(lo, area), nHi = Math.max(hi, area), nSum = sum + area;
      const ratio = worst(nLo, nHi, nSum, side);
      if (end > index && ratio > best) break;
      lo = nLo; hi = nHi; sum = nSum; best = ratio; end++;
    }
    rect = placeRow(areas, index, end, sum, rect, end >= areas.length, out);
    index = end;
  }
  return out;
}
const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));
function placeRow(areas: number[], from: number, to: number, sum: number, rect: Rect, lastRow: boolean, out: Rect[]): Rect {
  const horizontal = rect.w <= rect.h;
  const along = horizontal ? rect.w : rect.h;
  const cross = horizontal ? rect.h : rect.w;
  const crossNear = horizontal ? rect.y : rect.x;
  const crossFarEdge = horizontal ? rect.y + rect.h : rect.x + rect.w;
  const crossFar = lastRow ? crossFarEdge : clamp(crossNear + Math.min(sum / along, cross), crossNear, crossFarEdge);
  const thickness = crossFar - crossNear;
  const alongNear = horizontal ? rect.x : rect.y;
  const alongFarEdge = horizontal ? rect.x + rect.w : rect.y + rect.h;
  let offset = 0;
  for (let i = from; i < to; i++) {
    const near = clamp(alongNear + offset, alongNear, alongFarEdge);
    offset += thickness > 0 ? areas[i]! / thickness : 0;
    const far = i === to - 1 ? alongFarEdge : clamp(alongNear + offset, near, alongFarEdge);
    out[i] = horizontal ? { x: near, y: crossNear, w: far - near, h: crossFar - crossNear } : { x: crossNear, y: near, w: crossFar - crossNear, h: far - near };
  }
  return horizontal ? { x: rect.x, y: crossFar, w: rect.w, h: rect.y + rect.h - crossFar } : { x: crossFar, y: rect.y, w: rect.x + rect.w - crossFar, h: rect.h };
}

export interface HeatmapTile { asset: HeatmapAsset; rect: Rect; fill: number | null; value: number | null }
export interface HeatmapGroup { bucket: Bucket | null; rect: Rect; header: Rect | null; tiles: HeatmapTile[] }
export interface HeatmapPlan { groups: HeatmapGroup[]; scale: number; known: number; size: number }
/** A block's name strip is taken only where what is left under it is still a map. */
const MIN_HEADER_RATIO = 4;

export function plan(assets: HeatmapAsset[], o: HeatmapOptions, width: number, height: number, opts: { risingIsGreen?: boolean; mirrored?: boolean; headerHeight?: number; focus?: Bucket | null } = {}): HeatmapPlan {
  const { risingIsGreen = true, mirrored = false, headerHeight = 0, focus = null } = opts;
  const drawn = selectAssets(assets, o, focus);
  const values = drawn.map((a) => valueOf(a, o.colour)).filter((v): v is number => v != null);
  const scale = scaleFor(values, o.colour);
  if (!drawn.length || width <= 0 || height <= 0) return { groups: [], scale, known: 0, size: 0 };
  const block = (bucket: Bucket | null, members: HeatmapAsset[], body: Rect, header: Rect | null): HeatmapGroup => {
    const rects = treemap(members.map((a) => weightOf(a, o.size)), body.w, body.h);
    const tiles = members.map((asset, i) => {
      const value = valueOf(asset, o.colour);
      const r = rects[i]!;
      return { asset, rect: { x: r.x + body.x, y: r.y + body.y, w: r.w, h: r.h }, value, fill: value == null ? null : colourFor(value, scale, o.palette, risingIsGreen) };
    });
    const rect = header == null ? body : { x: header.x, y: header.y, w: body.w, h: body.h + header.h };
    return { bucket, rect, header, tiles };
  };
  const container = { x: 0, y: 0, w: width, h: height };
  // A drilled-into map is already one bucket; a strip naming it would repeat the title.
  const cut: HeatmapGrouping = focus != null ? 'none' : o.grouping;
  let groups: HeatmapGroup[];
  if (cut === 'none') groups = [block(null, drawn, container, null)];
  else {
    const weight = new Map(drawn.map((a) => [a, weightOf(a, o.size)] as const));
    const byKey = new Map<string, { bucket: Bucket; members: HeatmapAsset[] }>();
    for (const a of drawn) { const b = bucketOf(a, cut); const k = bucketKey(b); if (!byKey.has(k)) byKey.set(k, { bucket: b, members: [] }); byKey.get(k)!.members.push(a); }
    const buckets = [...byKey.values()].map((g, i) => ({ ...g, i, w: g.members.reduce((n, a) => n + weight.get(a)!, 0) })).sort((x, y) => y.w - x.w || x.i - y.i);
    const rects = treemap(buckets.map((b) => b.w), width, height);
    groups = buckets.map((b, i) => {
      const r = rects[i]!;
      const header = headerHeight > 0 && r.h >= headerHeight * MIN_HEADER_RATIO ? { x: r.x, y: r.y, w: r.w, h: headerHeight } : null;
      const body = header ? { x: r.x, y: r.y + headerHeight, w: r.w, h: r.h - headerHeight } : r;
      return block(b.bucket, b.members, body, header);
    });
  }
  if (mirrored) {
    const m = (r: Rect): Rect => ({ ...r, x: width - r.x - r.w });
    groups = groups.map((g) => ({ ...g, rect: m(g.rect), header: g.header ? m(g.header) : null, tiles: g.tiles.map((t) => ({ ...t, rect: m(t.rect) })) }));
  }
  const tiles = groups.flatMap((g) => g.tiles);
  return { groups, scale, known: tiles.filter((t) => t.value != null).length, size: tiles.length };
}

/** What the tile is labelled with: the base alone for a coin, the pair for everything else. */
export const labelOf = (symbol: string) => (marketOf(symbol) === 'crypto' ? instrumentOf(symbol).base : symbol);
/** The pair as a terminal writes it: `BTC/USDT`, `EUR/USD`, an index as itself. */
export function prettyOf(symbol: string): string {
  const inst = instrumentOf(symbol);
  return inst.quote && inst.base !== symbol ? `${inst.base}/${inst.quote}` : symbol;
}

/** The coverage sentence: how much of the map is real yet. Null when every tile has a figure. */
export function coverageText(known: number, total: number, resolving: boolean, canResolve: boolean, fa: boolean, num: (n: number) => string): string | null {
  if (!canResolve && known === 0) return fa ? 'کندلی در دسترس این صفحه نیست، پس رنگی هم نیست. همه خانه‌ها هاشورخورده‌اند.' : 'No candles are available to this screen, so there is no colour. Every tile is hatched.';
  if (resolving && known < total) return fa ? 'در حال خواندن کندل روزانه…' : 'Reading daily candles…';
  if (known >= total) return null;
  return fa ? `${num(known)} از ${num(total)} خانه عدد دارد` : `${known} of ${total} tiles have a figure`;
}
