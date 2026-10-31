// The day's line per symbol for the watchlist's «روند» column (OLD core/marketdata SparklineStore):
// the last 48 half-hour closes. Only rows on screen ask; each symbol is fetched once per visit (a
// failing one is not retried on every render); four at a time, so a fast scroll queues.
import { create } from 'zustand';
import { barsFor } from './symbolStats';

export const SPARK_INTERVAL = '30';
export const SPARK_BARS = 48;
const CONCURRENCY = 4;

interface SparkState { lines: Record<string, number[]>; pending: Record<string, true> }
export const useSparklines = create<SparkState>(() => ({ lines: {}, pending: {} }));

const asked = new Set<string>();
const queue: string[] = [];
let running = 0;

function pump(): void {
  while (running < CONCURRENCY && queue.length) {
    const symbol = queue.shift()!;
    running++;
    void barsFor(symbol, SPARK_INTERVAL, 10 * 60_000)
      .then((bars) => bars.slice(-SPARK_BARS).map((b) => b.c).filter(Number.isFinite))
      .catch(() => [] as number[])
      .then((line) => {
        const { lines, pending } = useSparklines.getState();
        const { [symbol]: _done, ...rest } = pending;
        useSparklines.setState({ lines: { ...lines, [symbol]: line }, pending: rest });
      })
      .finally(() => { running--; pump(); });
  }
}

/** Asks for a symbol's line (once). */
export function requestSparkline(symbol: string): void {
  if (asked.has(symbol)) return;
  asked.add(symbol);
  queue.push(symbol);
  useSparklines.setState((s) => ({ pending: { ...s.pending, [symbol]: true } }));
  pump();
}

/** The polyline points of a line in a W×H box, or null under two points. */
export function sparkPoints(values: number[], w: number, h: number): string | null {
  if (values.length < 2) return null;
  const min = Math.min(...values); const max = Math.max(...values);
  return values.map((c, i) => `${((i / (values.length - 1)) * w).toFixed(1)},${(h - 1 - ((c - min) / (max - min || 1)) * (h - 2)).toFixed(1)}`).join(' ');
}
