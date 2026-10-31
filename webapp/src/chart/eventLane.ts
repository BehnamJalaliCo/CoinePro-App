// The event lane's arithmetic, kept pure so the canvas, the click and the tests agree (OLD
// CoineProChart.kt drawEventMarks / eventGroups / groupedMark, chart/core ChartEvents.kt
// EventGlyphs, NotableBars.kt, NotableBarReading.kt, core/chartevents ChartEventFeed.kt).
import type { Candle } from '../data/types';
import type { CalendarEvent, NewsItem } from '../data/intel';
import { backendOf } from '../data/symbols';

// ── Geometry (EventGlyphs) ────────────────────────────────────────────────────────────────────────
/** A glyph's full width and height. */
export const GLYPH = 12;
/** The gap between the glyph and the top of the time axis: the lane sits above the dates. */
export const AXIS_GAP = 4;
/** The space two marks need between them to be drawn as two. */
export const CLUSTER_GAP = 2;
/** Marks closer than this fold into one glyph group. */
export const REACH = GLYPH + CLUSTER_GAP;
/** How far a cluster's outline sits behind the glyph in front of it. */
export const STACK = 2;
/** The notable-bar dot's diameter. */
export const DOT = 5;
/** A touch reaches a glyph this far from its centre (a 48-point target); a mouse needs less. */
export const TOUCH_RADIUS = 24;
export const MOUSE_RADIUS = 8;

/** Where the lane's glyphs are centred, for a pane `h` tall. */
export const laneCentre = (h: number) => h - AXIS_GAP - GLYPH / 2;
/** How far above the axis a click still reaches the lane: the glyph, its gap, the bolt above it. */
export const laneReach = () => AXIS_GAP + GLYPH * 2 + CLUSTER_GAP * 3;

// ── Colours ───────────────────────────────────────────────────────────────────────────────────────
export type Importance = CalendarEvent['impact'];
/** High red, medium a true orange (Material orange 600), low the analysis blue — never green, which reads as «price up». */
export const IMPORTANCE_COLOR: Record<Importance, string> = { high: '#F23645', medium: '#FB8C00', low: '#2962FF', holiday: '#787B86' };
/** TradingView's palette purple: the news bolt. */
export const NEWS_PURPLE = '#9C27B0';
const RANK: Importance[] = ['holiday', 'low', 'medium', 'high'];
export const rankOf = (i: Importance) => RANK.indexOf(i);

// ── Marks and their groups ──────────────────────────────────────────────────────────────────────
/** One thing on the lane: a calendar release or a headline. `at` in unix seconds. */
export type LaneItem =
  | { kind: 'event'; at: number; event: CalendarEvent }
  | { kind: 'news'; at: number; news: NewsItem };

/** Everything on one bar (or one slot ahead of the bars), earliest first. */
export interface LaneMark {
  /** The bar's logical index (fractional slots right of the last bar for upcoming releases). */
  logical: number;
  /** The bar's open time, or null for a slot ahead of the bars. */
  barT: number | null;
  items: LaneItem[];
}

/** A release mark when the bar holds any release, else a news mark. */
export const markKind = (m: LaneMark): 'event' | 'news' => (m.items.some((i) => i.kind === 'event') ? 'event' : 'news');
/** The strongest release in the mark; what its disc is coloured by. */
export function markImportance(m: LaneMark): Importance {
  let best: Importance = 'low';
  let seen = false;
  for (const i of m.items) if (i.kind === 'event' && (!seen || rankOf(i.event.impact) > rankOf(best))) { best = i.event.impact; seen = true; }
  return best;
}
export const hasNews = (m: LaneMark) => m.items.some((i) => i.kind === 'news');
/** More than one event behind one glyph: it opens a list, and is drawn as a stack. */
export const isCluster = (m: LaneMark) => m.items.length > 1;

export interface Placed<T> { mark: T; x: number }

/**
 * The marks folded into glyph groups, left to right: a mark joins the group before it when it
 * stands closer than `reach` to the group's first mark *or* to where the group's glyph is drawn
 * (its first release mark), so no two glyphs ever touch. Marks off the plot are dropped.
 */
export function laneGroups<T>(placed: Placed<T>[], isNews: (m: T) => boolean, plotWidth: number, reach = REACH): Placed<T>[][] {
  const list = placed.filter((p) => p.x >= 0 && p.x <= plotWidth).sort((a, b) => a.x - b.x);
  const groups: Placed<T>[][] = [];
  for (const entry of list) {
    const g = groups[groups.length - 1];
    if (g && (entry.x - g[0]!.x < reach || entry.x - drawnAt(g, isNews).x < reach)) g.push(entry);
    else groups.push([entry]);
  }
  return groups;
}

/** Where a group's glyph stands: its first release mark, else its first. */
export function drawnAt<T>(group: Placed<T>[], isNews: (m: T) => boolean): Placed<T> {
  return group.find((p) => !isNews(p.mark)) ?? group[0]!;
}

/** Every event of a group, earliest first, and how many bars it spans. */
export function groupItems(group: Placed<LaneMark>[]): { items: LaneItem[]; bars: number } {
  const items = [...new Set(group.flatMap((p) => p.mark.items))].sort((a, b) => a.at - b.at);
  return { items, bars: new Set(group.map((p) => Math.round(p.mark.logical))).size };
}

/** The candidate nearest to `x` within `radius`, or null (ChartEvents.markAt / notableAt). */
export function nearest<T>(list: T[], xOf: (t: T) => number | null, x: number, radius: number): T | null {
  let best: T | null = null;
  let bestD = Infinity;
  for (const t of list) {
    const px = xOf(t);
    if (px == null) continue;
    const d = Math.abs(px - x);
    if (d <= radius && d < bestD) { best = t; bestD = d; }
  }
  return best;
}

// ── Notable bars (NotableBars.kt) ─────────────────────────────────────────────────────────────────
/** How many bars behind a candle its baseline is taken over. */
export const NOTABLE_WINDOW = 20;
/** How many times the baseline a range must exceed. */
export const NOTABLE_MULTIPLE = 2.0;

const rangeOf = (b: Candle | undefined): number => (b && Number.isFinite(b.h) && Number.isFinite(b.l) && b.h >= b.l ? b.h - b.l : NaN);

/**
 * The bars whose range is more than twice the average range of the 20 bars before them (the bar
 * itself never in its own baseline); none in the first 20 bars and none over a zero baseline.
 */
export function notableBars(bars: Candle[]): number[] {
  const n = bars.length;
  if (n <= NOTABLE_WINDOW) return [];
  const ranges = bars.map(rangeOf);
  const out: number[] = [];
  let sum = 0; let counted = 0;
  for (let i = 0; i < NOTABLE_WINDOW; i++) if (Number.isFinite(ranges[i]!)) { sum += ranges[i]!; counted++; }
  for (let i = NOTABLE_WINDOW; i < n; i++) {
    const r = ranges[i]!;
    if (Number.isFinite(r) && counted > 0) {
      const avg = sum / counted;
      if (avg > 0 && r > NOTABLE_MULTIPLE * avg) out.push(i);
    }
    if (Number.isFinite(r)) { sum += r; counted++; }
    const leaving = ranges[i - NOTABLE_WINDOW]!;
    if (Number.isFinite(leaving)) { sum -= leaving; counted--; }
  }
  return out;
}

/** One bar's range as a multiple of its own baseline, or null where there is none. */
export function ratioAt(bars: Candle[], i: number): number | null {
  if (i < NOTABLE_WINDOW || i >= bars.length) return null;
  const r = rangeOf(bars[i]);
  if (!Number.isFinite(r)) return null;
  let sum = 0; let counted = 0;
  for (let k = i - NOTABLE_WINDOW; k < i; k++) { const x = rangeOf(bars[k]); if (Number.isFinite(x)) { sum += x; counted++; } }
  if (!counted) return null;
  const avg = sum / counted;
  return avg > 0 ? r / avg : null;
}

export interface NotableReading {
  index: number;
  /** The bar's open, and the end of its own window (exclusive), unix seconds. */
  from: number;
  to: number;
  ratio: number | null;
  movePct: number | null;
  up: boolean;
  /** What was on the calendar and the wire inside the bar, in order. */
  items: LaneItem[];
}

/** What the «چرا این حرکت؟» sheet says about one bar (NotableBarReadings.of). */
export function notableReading(bars: Candle[], i: number, items: LaneItem[]): NotableReading | null {
  const b = bars[i];
  if (!b) return null;
  const from = b.t;
  const to = i + 1 < bars.length ? bars[i + 1]!.t : from + (bars.length >= 2 ? Math.max(1, b.t - bars[i - 1]!.t) : 1);
  return {
    index: i, from, to,
    ratio: ratioAt(bars, i),
    movePct: Number.isFinite(b.o) && Number.isFinite(b.c) && b.o !== 0 ? ((b.c - b.o) / b.o) * 100 : null,
    up: b.c >= b.o,
    items: items.filter((x) => x.at >= from && x.at < to).sort((a, c) => a.at - c.at),
  };
}

// ── Which news belongs to which chart (ChartEventFeed.chartEventsFor, AcademyPublicNews) ──────────
export type Relevance = 'gold' | 'silver' | 'crypto';

const SILVER_WORDS = ['نقره', 'XAG', 'Silver', 'silver'];
const GOLD_WORDS = ['طلا', 'XAU', 'Gold', 'gold'];
function classify(text: string): Relevance[] | null {
  const silver = SILVER_WORDS.some((w) => text.includes(w));
  const gold = GOLD_WORDS.some((w) => text.includes(w));
  if (silver && !gold) return ['silver'];
  if (gold && !silver) return ['gold'];
  return null;
}

/**
 * The markets a story is about. A crypto story is crypto's; the forex newsroom's carry no tag, so
 * the title decides, then the summary, and a story naming neither metal — a rate decision, the
 * dollar — is the newsroom's «macro» and tagged with both.
 */
export function newsRelevance(item: Pick<NewsItem, 'market' | 'title' | 'summary'>): Relevance[] {
  if (item.market === 'crypto') return ['crypto'];
  return classify(item.title) ?? classify(item.summary ?? '') ?? ['gold', 'silver'];
}

/** The market tag a chart answers to, or null for a pair or an index (which has none). */
export function symbolRelevance(symbol: string): Relevance | null {
  const s = symbol.toUpperCase();
  if (s === 'XAUUSD') return 'gold';
  if (s === 'XAGUSD') return 'silver';
  return backendOf(s) === 'tradeyar' ? 'crypto' : null;
}

/**
 * Whether a story goes on a chart: an untagged story everywhere; a tagged one on its market; a
 * pair or an index takes the macro stories (tagged with both metals), never a gold or a silver
 * analysis alone, never a coin's story.
 */
export function newsBelongs(symbol: string, item: Pick<NewsItem, 'market' | 'title' | 'summary'>): boolean {
  const tags = newsRelevance(item);
  const own = symbolRelevance(symbol);
  if (!tags.length) return true;
  if (own) return tags.includes(own);
  return tags.includes('gold') && tags.includes('silver');
}
