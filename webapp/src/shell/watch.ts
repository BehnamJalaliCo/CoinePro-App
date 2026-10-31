// What a chart looks like in a window the size of a stamp (chart/core ChartWatch.kt, app/brief
// BriefSparklineShape.kt): four facts and a line — which market, what it costs, which way it has
// gone across the visible window, how long this candle has left — and the rules that keep that
// window from working for nothing. Pure, for the tests; the window is shell/KeepWatching.tsx.

export interface WatchSnapshot {
  symbol: string;
  /** The bar length as the interval menu labels it («1h»), drawn as-is. */
  interval: string;
  price: number;
  /** Change across the visible window, per cent; null where it cannot be read. */
  changePct: number | null;
  /** The tail of the series, oldest first, at most LINE_POINTS. */
  closes: number[];
  /** When the forming bar closes, unix seconds; null on a series with no open bar. */
  closesAt: number | null;
}

/** The window is redrawn at most once a second. */
export const MIN_PUBLISH_MS = 1000;
/** How much of the tail the line draws; beyond this the points are narrower than a pixel. */
export const LINE_POINTS = 60;
/** Past a week the number is a date, not a countdown. */
const MAX_COUNTDOWN_S = 7 * 24 * 60 * 60;

export function sameSnapshot(a: WatchSnapshot, b: WatchSnapshot): boolean {
  return a.symbol === b.symbol && a.interval === b.interval && a.price === b.price && a.changePct === b.changePct && a.closesAt === b.closesAt
    && a.closes.length === b.closes.length && a.closes.every((c, i) => c === b.closes[i]);
}

/**
 * Whether a new snapshot is worth drawing: the first always (a window that waited would open
 * empty), never an equal one, otherwise not more often than MIN_PUBLISH_MS.
 */
export function shouldPublish(prev: WatchSnapshot | null, next: WatchSnapshot, lastAt: number | null, now: number): boolean {
  if (!prev || lastAt == null) return true;
  if (sameSnapshot(prev, next)) return false;
  return now - lastAt >= MIN_PUBLISH_MS;
}

/** Seconds until the forming bar closes, or null: past the close (no stuck «0:00») or more than a week away. */
export function countdownSeconds(closesAt: number | null, nowSec: number): number | null {
  if (closesAt == null) return null;
  const left = closesAt - nowSec;
  return left >= 1 && left <= MAX_COUNTDOWN_S ? Math.floor(left) : null;
}

/** «0:45», «12:05», «3:04:09»: Latin digits, a figure read against a clock. */
export function countdownText(s: number): string {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const x = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${x}` : `${m}:${x}`;
}

export interface WatchShape { points: { x: number; y: number }[]; rising: boolean }

/**
 * The closes normalised into the unit box (y flipped, 0 at the top), or null under two points.
 * Non-finite closes are dropped; a flat tail is centred, not divided by zero. Rising is first
 * against last — a tail that fell all the way and bounced on the final close is a tail that fell.
 */
export function watchShape(values: number[]): WatchShape | null {
  const closes = values.filter((v) => Number.isFinite(v));
  if (closes.length < 2) return null;
  const low = Math.min(...closes);
  const span = Math.max(...closes) - low;
  const step = 1 / (closes.length - 1);
  return {
    points: closes.map((c, i) => ({ x: i * step, y: span > 0 ? 1 - (c - low) / span : 0.5 })),
    rising: closes[closes.length - 1]! >= closes[0]!,
  };
}

/** Change from the first to the last visible close, per cent, or null. */
export function visibleChange(closes: number[], from: number | null, to: number | null): number | null {
  if (!closes.length) return null;
  const a = Math.max(0, Math.min(closes.length - 1, Math.ceil(from ?? 0)));
  const b = Math.max(0, Math.min(closes.length - 1, Math.floor(to ?? closes.length - 1)));
  const first = closes[a]!;
  const last = closes[b]!;
  if (!(b > a) || !Number.isFinite(first) || !Number.isFinite(last) || first === 0) return null;
  return ((last - first) / Math.abs(first)) * 100;
}
