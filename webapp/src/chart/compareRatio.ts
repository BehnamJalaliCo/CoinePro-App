// «نسبت» — a compared symbol divided by this chart's, bar for bar (OLD chart/core Comparison.kt
// ComparisonBasis.RATIO): not «which rose more since the left edge» but «is it gaining on this one
// right now», with no dependence on where the reader started. A bar the base has no close for, or a
// zero close, has no ratio and is left out rather than drawn as nothing.
import type { Candle } from '../data/types';

export function ratioSeries(compared: { t: number; v: number }[], base: Candle[]): { t: number; v: number }[] {
  const under = new Map<number, number>();
  for (const b of base) under.set(b.t, b.c);
  const out: { t: number; v: number }[] = [];
  for (const p of compared) {
    const u = under.get(p.t);
    if (u == null || !Number.isFinite(u) || u === 0 || !Number.isFinite(p.v)) continue;
    out.push({ t: p.t, v: p.v / u });
  }
  return out;
}
