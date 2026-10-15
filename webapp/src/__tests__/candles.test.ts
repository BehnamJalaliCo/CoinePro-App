import { describe, expect, it } from 'vitest';
import { bucketOf, foldBars, intervalsFor } from '../data/candles';
import type { Candle } from '../data/types';

const bar = (t: number, o: number, h: number, l: number, c: number, v = 1): Candle => ({ t, o, h, l, c, v });

describe('foldBars', () => {
  it('folds 1m bars into 3m buckets on clock boundaries, not from the first bar', () => {
    const start = 1_700_000_040; // 00:54 of some hour → bucket 00:54 (3m aligned at 54)
    const bars = [0, 1, 2, 3, 4].map((i) => bar(start + i * 60, 10 + i, 11 + i, 9 + i, 10.5 + i));
    const out = foldBars(bars, '3', 'minutes');
    for (const b of out) expect(b.t % 180).toBe(0);
    expect(out.reduce((n, b) => n + b.v, 0)).toBe(5);
    const first = out[0]!;
    expect(first.o).toBe(bars.find((b) => b.t >= first.t)!.o);
  });

  it('keeps high/low/close/volume exact', () => {
    const t0 = 1_699_999_200; // divisible by 7200
    const out = foldBars([bar(t0, 1, 5, 0.5, 2, 3), bar(t0 + 3600, 2, 7, 1.5, 6, 4)], '120', 'minutes');
    expect(out).toEqual([{ t: t0, o: 1, h: 7, l: 0.5, c: 6, v: 7 }]);
  });

  it('weeks open on Monday 00:00 UTC', () => {
    const wed = Date.UTC(2026, 9, 14) / 1000; // Wednesday 14 Oct 2026
    const monday = Date.UTC(2026, 9, 12) / 1000;
    expect(bucketOf(wed, '1W', 'week')).toBe(monday);
    expect(bucketOf(monday, '1W', 'week')).toBe(monday);
    const sunday = Date.UTC(2026, 9, 18, 23) / 1000;
    expect(bucketOf(sunday, '1W', 'week')).toBe(monday);
  });

  it('months open on the 1st', () => {
    const t = Date.UTC(2026, 1, 27, 13) / 1000;
    expect(bucketOf(t, '1M', 'month')).toBe(Date.UTC(2026, 1, 1) / 1000);
    const days = Array.from({ length: 70 }, (_, i) => bar(Date.UTC(2026, 0, 1) / 1000 + i * 86400, 1, 2, 0.5, 1.5));
    const months = foldBars(days, '1M', 'month');
    expect(months.map((m) => new Date(m.t * 1000).getUTCMonth())).toEqual([0, 1, 2]);
  });

  it('passes bars through when nothing is folded', () => {
    const bars = [bar(60, 1, 1, 1, 1)];
    expect(foldBars(bars, '1', 'none')).toBe(bars);
  });
});

describe('intervalsFor', () => {
  it('crypto draws every interval', () => {
    expect(intervalsFor('BTCUSDT')).toEqual(expect.arrayContaining(['1', '3', '5', '15', '30', '60', '120', '240', '1D', '1W', '1M']));
  });
  it('forex has no 1m or 3m (the backend has no M1)', () => {
    const iv = intervalsFor('EURUSD');
    expect(iv).not.toContain('1');
    expect(iv).not.toContain('3');
    expect(iv).toContain('5');
    expect(iv).toContain('1W');
  });
});
