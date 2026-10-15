import { describe, expect, it } from 'vitest';
import { atrOf, formingBar, intrabarMap, practiceStats, settleBar, stopDistance, tradeMoney, type ReplayTrade } from '../state/replay';
import { hitLine, roundTo } from '../chart/tradeLines';
import { barDate } from '../shell/ReplayBar';

const bar = (t: number, o: number, h: number, l: number, c: number) => ({ t, o, h, l, c, v: 1 });
const long = (p: Partial<ReplayTrade> = {}): ReplayTrade => ({ id: 'a', side: 'long', entry: 100, at: 0, sl: 95, tp: 110, qty: 2, qty0: 2, risk: 10, realized: 0, type: 'market', ...p });

describe('replay practice', () => {
  it('measures the ATR and keeps a floor under the stop distance', () => {
    const bars = Array.from({ length: 30 }, (_, i) => bar(i, 100, 101, 99, 100));
    expect(atrOf(bars, 14)).toBeCloseTo(2);
    expect(stopDistance(bars, 100, 1.5)).toBeCloseTo(3);
    const flat = Array.from({ length: 30 }, (_, i) => bar(i, 100, 100, 100, 100));
    expect(stopDistance(flat, 100, 1.5)).toBeCloseTo(0.5);
  });

  it('closes at the stop or the target, the stop first when a bar reaches both', () => {
    expect(settleBar([long()], bar(1, 100, 104, 96, 101))[0]!.exit).toBeUndefined();
    const sl = settleBar([long()], bar(1, 100, 101, 94, 95))[0]!;
    expect([sl.exit, sl.how, sl.pnl, sl.r]).toEqual([95, 'sl', -10, -1]);
    const tp = settleBar([long()], bar(1, 100, 111, 99, 110))[0]!;
    expect([tp.exit, tp.how, tp.pnl, tp.r]).toEqual([110, 'tp', 20, 2]);
    expect(settleBar([long()], bar(1, 100, 112, 90, 100))[0]!.how).toBe('sl');
    const short = settleBar([long({ side: 'short', sl: 105, tp: 90 })], bar(1, 100, 106, 99, 104))[0]!;
    expect([short.how, short.pnl]).toEqual(['sl', -10]);
  });

  it('leaves trades newer than the bar alone and fills orders the bar reaches', () => {
    const t = [long({ at: 5 })];
    expect(settleBar(t, bar(5, 100, 120, 80, 100))).toBe(t);
    const order = long({ type: 'limit', pending: true, entry: 98, sl: 93, tp: 108 });
    expect(settleBar([order], bar(1, 100, 101, 99, 100))[0]!.pending).toBe(true);
    const filled = settleBar([order], bar(1, 100, 101, 97, 99))[0]!;
    expect([filled.pending, filled.at, filled.exit]).toEqual([false, 1, undefined]);
    const stop = long({ type: 'stop', pending: true, entry: 102 });
    expect(settleBar([stop], bar(1, 100, 103, 99, 102))[0]!.pending).toBe(false);
  });

  it('counts partial closes in the money and adds up the session', () => {
    const half = long({ qty: 1, realized: 5 });
    expect(tradeMoney(half, 104)).toBe(9);
    const closed = [long({ exit: 110, pnl: 20, r: 2, exitAt: 2 }), long({ id: 'b', exit: 95, pnl: -10, r: -1, exitAt: 3 }), long({ id: 'c', at: 3 })];
    const s = practiceStats(closed, 1000, 101);
    expect([s.trades, s.wins, s.winRate, s.net, s.netR, s.open, s.balance, s.equity]).toEqual([2, 1, 50, 10, 1, 1, 1010, 1012]);
    expect(s.last?.id).toBe('b');
  });

  it('builds each bar from its lower-timeframe bars', () => {
    const full = [bar(0, 1, 1, 1, 1), bar(3600, 1, 1, 1, 1)];
    const subs = [0, 900, 1800, 2700, 3600, 4500].map((t, i) => bar(t, 10 + i, 12 + i, 9 + i, 11 + i));
    const map = intrabarMap(full, subs);
    expect(map.get(0)?.length).toBe(4);
    expect(map.get(3600)?.length).toBe(2);
    expect(formingBar(full[0]!, map.get(0)!, 1)).toEqual({ t: 0, o: 10, h: 13, l: 9, c: 12, v: 2 });
  });
});

describe('trading lines', () => {
  it('finds the draggable line under the pointer and rounds the price', () => {
    const ys = [{ y: 100, drag: false }, { y: 103, drag: true }, { y: 140, drag: true }];
    expect(hitLine(ys, 101)).toBe(1);
    expect(hitLine(ys, 120)).toBe(-1);
    expect(hitLine([{ y: null, drag: true }], 0)).toBe(-1);
    expect(roundTo(1.234567, 2)).toBe(1.23);
  });

  it('writes the replayed bar as the chart does', () => {
    expect(barDate(Date.UTC(2026, 8, 30, 0, 30) / 1000, 'UTC', '60')).toBe("30 Sep '26  00:30");
    expect(barDate(Date.UTC(2026, 0, 2) / 1000, 'Asia/Tehran', '1D')).toBe("02 Jan '26");
  });
});

describe('auto chart patterns', () => {
  it('finds head and shoulders, a double bottom and an ascending triangle among the swings', async () => {
    const { findChartPatterns } = await import('../engine/indicators_ext_c.js');
    const sw = (xs: Array<[number, number]>, firstHigh: boolean) => xs.map(([i, price], k) => ({ i, price, high: k % 2 === 0 ? firstHigh : !firstHigh }));
    const hs = findChartPatterns(sw([[0, 110], [5, 100], [10, 120], [15, 100], [20, 110]], true), 2, 3);
    expect(hs[0]?.kind).toBe('hs');
    const db = findChartPatterns(sw([[0, 90], [5, 100], [10, 90.5]], false), 1, 3);
    expect(db[0]?.kind).toBe('db');
    const at = findChartPatterns(sw([[0, 110], [5, 100], [10, 110.5], [15, 105], [20, 110]], true), 1, 3);
    expect(at[0]?.kind).toBe('at');
    expect(findChartPatterns(sw([[0, 110], [5, 100], [10, 130], [15, 90], [20, 150]], true), 1, 3)).toEqual([]);
  });
});
