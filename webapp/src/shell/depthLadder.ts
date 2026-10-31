// The order book's arithmetic (OLD core/orderbook OrderBook.kt, PriceAggregation.kt; feature/dom
// DepthLadderRows.kt, DepthCurve.kt): price aggregation steps, the cumulative columns, the bid
// share near the touch, and the depth curve. Pure, so the DOM panel and the trade page share it.

export interface Level { price: number; quantity: number; orders: number | null }
export interface Book { bids: Level[]; asks: Level[] }
export type LadderFigure = 'amount' | 'cumulative';

/** Rows per side the OLD ladder draws, the band the bid share is measured over, and the depth fetched. */
export const VISIBLE_LEVELS = 8;
export const IMBALANCE_LEVELS = 20;
export const DEFAULT_DEPTH = 100;
const MAX_STEPS = 4;
/** A count on a bar only from this many orders up: nine rungs in ten hold one order. */
export const STACKED_ORDERS_THRESHOLD = 2;

/** Rows as the feeds send them ([price, quantity, orders?]), valid levels only, one per price, best first. */
export function bookOf(raw: { bids?: unknown[][]; asks?: unknown[][] }): Book {
  const side = (rows: unknown[][] | undefined, desc: boolean): Level[] => {
    const by = new Map<number, Level>();
    for (const r of rows ?? []) {
      const price = Number(r[0]), quantity = Number(r[1]);
      if (!(Number.isFinite(price) && price > 0 && Number.isFinite(quantity) && quantity > 0)) continue;
      const o = r[2] == null ? null : Number(r[2]);
      const orders = o != null && Number.isFinite(o) ? o : null;
      const have = by.get(price);
      if (have) by.set(price, { price, quantity: have.quantity + quantity, orders: have.orders == null && orders == null ? null : (have.orders ?? 0) + (orders ?? 0) });
      else by.set(price, { price, quantity, orders });
    }
    return [...by.values()].sort((a, b) => (desc ? b.price - a.price : a.price - b.price));
  };
  return { bids: side(raw.bids, true), asks: side(raw.asks, false) };
}

export const bestBid = (b: Book) => b.bids[0]?.price ?? null;
export const bestAsk = (b: Book) => b.asks[0]?.price ?? null;
export const crossed = (b: Book) => { const bid = bestBid(b), ask = bestAsk(b); return bid != null && ask != null && bid >= ask; };
export const spreadOf = (b: Book) => { const bid = bestBid(b), ask = bestAsk(b); return bid != null && ask != null ? ask - bid : null; };
export const midOf = (b: Book) => { const bid = bestBid(b), ask = bestAsk(b); return bid != null && ask != null ? (bid + ask) / 2 : null; };

/** The bid's share of the size resting on the first `levels` of each side, or null on an empty book. */
export function imbalance(b: Book, levels = IMBALANCE_LEVELS): number | null {
  const bid = b.bids.slice(0, levels).reduce((n, l) => n + l.quantity, 0);
  const ask = b.asks.slice(0, levels).reduce((n, l) => n + l.quantity, 0);
  return bid + ask <= 0 ? null : bid / (bid + ask);
}

/** Each level with everything between it and the touch. */
export function cumulative(levels: Level[]): (Level & { total: number })[] {
  let running = 0;
  return levels.map((l) => { running += l.quantity; return { ...l, total: running }; });
}
const volume = (levels: Level[]) => levels.reduce((n, l) => n + l.quantity, 0);
export const largestCumulative = (b: Book) => Math.max(volume(b.bids), volume(b.asks));

// ── Aggregation ──────────────────────────────────────────────────────────────────────────────────
/** The decimals a step needs: 0.05 → 2, 10 → 0 (clamped 0…8). */
export function aggregationDecimals(step: number): number {
  if (!Number.isFinite(step) || step <= 0) return 0;
  for (let d = 0; d <= 8; d++) { if (Math.abs(Number(step.toFixed(d)) - step) < step * 1e-9) return d; }
  return 8;
}
const snap = (price: number, step: number, up: boolean) => {
  const q = price / step;
  const n = up ? Math.ceil(q - 1e-9) : Math.floor(q + 1e-9);
  return Number((n * step).toFixed(aggregationDecimals(step)));
};
/**
 * The book bucketed to `step`: bids rounded down to it and asks up, so the spread can only widen,
 * never tighten. A step coarser than the deepest bid is not applied.
 */
export function aggregated(b: Book, step: number | null): Book {
  if (step == null || !Number.isFinite(step) || step <= 0) return b;
  if (b.bids.length && step > b.bids[b.bids.length - 1]!.price) return b;
  const merge = (levels: Level[], up: boolean) => bookOf(up
    ? { asks: levels.map((l) => [snap(l.price, step, true), l.quantity, l.orders]) }
    : { bids: levels.map((l) => [snap(l.price, step, false), l.quantity, l.orders]) });
  return { bids: merge(b.bids, false).bids, asks: merge(b.asks, true).asks };
}

/** The smallest gap between neighbouring levels on either side: the venue's tick, as far as the book shows. */
export function inferredTick(b: Book): number | null {
  const gaps: number[] = [];
  for (let i = 1; i < b.bids.length; i++) gaps.push(b.bids[i - 1]!.price - b.bids[i]!.price);
  for (let i = 1; i < b.asks.length; i++) gaps.push(b.asks[i]!.price - b.asks[i - 1]!.price);
  const ok = gaps.filter((g) => Number.isFinite(g) && g > 0);
  return ok.length ? Math.min(...ok) : null;
}

/** The 1-or-5 decade at or below the tick (six significant figures first, so 0.0999999 reads as 0.1). */
export function ladderBase(value: number): number {
  const rounded = Number(value.toPrecision(6));
  const exponent = Math.floor(Math.log10(rounded) + 1e-12);
  const decade = 10 ** exponent;
  return rounded / decade >= 5 - 1e-9 ? Number((decade * 5).toPrecision(12)) : Number(decade.toPrecision(12));
}

/** Up to four steps above the tick, ×5 then ×2 from its decade (1 → 5, 10, 50, 100), capped at 100 ticks; the kept step stays offered. */
export function aggregationSteps(b: Book, keep: number | null = null): number[] {
  const kept = keep != null && Number.isFinite(keep) && keep > 0 ? keep : null;
  const tick = inferredTick(b);
  if (tick == null) return kept != null ? [kept] : [];
  let rung = ladderBase(tick);
  const cap = rung * DEFAULT_DEPTH;
  const steps: number[] = [];
  let byFive = true;
  while (steps.length < MAX_STEPS) {
    rung = Number((rung * (byFive ? 5 : 2)).toPrecision(12));
    byFive = !byFive;
    if (rung > cap * (1 + 1e-9)) break;
    if (rung > tick) steps.push(rung);
  }
  return [...new Set([...steps, ...(kept != null ? [kept] : [])])].sort((a, c) => a - c);
}
export const stepLabel = (step: number) => step.toFixed(aggregationDecimals(step));

// ── The ladder ───────────────────────────────────────────────────────────────────────────────────
export interface LadderRow { price: number; quantity: number; total: number; side: 'bid' | 'ask'; barFraction: number; cumulativeBarFraction: number; curveFraction: number; orders: number | null }
const fraction = (v: number, largest: number) => (largest <= 0 ? 0 : Math.min(1, Math.max(0, v / largest)));
/**
 * The visible rows of each side (asks best last, so they read down to the spread). A row's bar is
 * its size against the largest visible size on *either* side (so the heavier side looks heavier),
 * its cumulative bar against the larger visible side total, its faint curve against the whole book.
 */
export function ladderRows(b: Book, levels = VISIBLE_LEVELS): { asks: LadderRow[]; bids: LadderRow[] } {
  const vb = b.bids.slice(0, levels), va = b.asks.slice(0, levels);
  const largestQuantity = Math.max(0, ...vb.map((l) => l.quantity), ...va.map((l) => l.quantity));
  const largestVisible = Math.max(volume(vb), volume(va));
  const largestBook = largestCumulative(b);
  const rows = (ls: Level[], side: 'bid' | 'ask') => cumulative(ls).map((l) => ({
    price: l.price, quantity: l.quantity, total: l.total, side, orders: l.orders,
    barFraction: fraction(l.quantity, largestQuantity),
    cumulativeBarFraction: fraction(l.total, largestVisible),
    curveFraction: fraction(l.total, largestBook),
  }));
  return { asks: rows(va, 'ask').reverse(), bids: rows(vb, 'bid') };
}
export const ladderFigure = (r: LadderRow, f: LadderFigure) => (f === 'amount' ? r.quantity : r.total);
export const ladderBar = (r: LadderRow, f: LadderFigure) => (f === 'amount' ? r.barFraction : r.cumulativeBarFraction);
/** Decimals for a size column from its largest figure. */
export const quantityDecimalsFor = (largest: number) => (largest >= 1000 ? 0 : largest >= 100 ? 1 : largest >= 1 ? 3 : 5);

// ── The depth curve ──────────────────────────────────────────────────────────────────────────────
export interface CurvePoint { price: number; total: number; x: number; y: number }
export interface DepthCurve { bids: CurvePoint[]; asks: CurvePoint[]; mid: number; lowPrice: number; highPrice: number; peakTotal: number }
/** Both sides' cumulative depth on one price axis centred on the mid; null on a crossed or empty book. */
export function depthCurve(b: Book): DepthCurve | null {
  if (crossed(b)) return null;
  const mid = midOf(b);
  const deepestBid = b.bids[b.bids.length - 1]?.price, deepestAsk = b.asks[b.asks.length - 1]?.price;
  if (mid == null || deepestBid == null || deepestAsk == null) return null;
  const half = Math.max(mid - deepestBid, deepestAsk - mid);
  if (!Number.isFinite(half) || half <= 0) return null;
  const peak = largestCumulative(b);
  if (!Number.isFinite(peak) || peak <= 0) return null;
  const low = mid - half, span = half * 2;
  const points = (ls: Level[]) => cumulative(ls).map((l) => ({ price: l.price, total: l.total, x: Math.min(1, Math.max(0, (l.price - low) / span)), y: Math.min(1, Math.max(0, l.total / peak)) }));
  return { bids: points(b.bids), asks: points(b.asks), mid, lowPrice: low, highPrice: mid + half, peakTotal: peak };
}

// ── The reader's choice per symbol (DepthLadderPreferences) ───────────────────────────────────────
const PREF_KEY = 'pc.dom.ladder.v1';
export function readLadderPref(symbol: string): { step: number | null; figure: LadderFigure } {
  try {
    const all = JSON.parse(localStorage.getItem(PREF_KEY) ?? '{}') as Record<string, { step: number | null; figure: LadderFigure }>;
    const p = all[symbol.toUpperCase()];
    if (p) return { step: typeof p.step === 'number' && p.step > 0 ? p.step : null, figure: p.figure === 'cumulative' ? 'cumulative' : 'amount' };
  } catch { /* defaults */ }
  return { step: null, figure: 'amount' };
}
export function saveLadderPref(symbol: string, pref: { step: number | null; figure: LadderFigure }): void {
  try {
    const all = JSON.parse(localStorage.getItem(PREF_KEY) ?? '{}') as Record<string, unknown>;
    all[symbol.toUpperCase()] = pref;
    localStorage.setItem(PREF_KEY, JSON.stringify(all));
  } catch { /* not kept */ }
}
