import { describe, expect, it } from 'vitest';
import { RequestPacer, BULK_INTERVAL_MS, pacerFor, cryptoPacer, forexPacer } from '../data/pacer';
import { CHANGE_BANDS, NO_FILTER, applyFilter, bandHolds, compactFigure, filterCount, headlinesOf, hottest, keeps, pulseOf, pulseValue, signalCounts, signalSummary, typesOf, venuesOf } from '../pages/marketsLib';
import {
  DEFAULT_OPTIONS, assetOf, anyValueFor, anyWeightFor, colourFor, coverageText, fmtAmount, fmtPercent, labelInkFor, labelOf, neutralOf, plan, prettyOf, rankOf,
  scaleOfValues, selectAssets, tileFigure, treemap, UNRANKED, valueOf, weightOf,
} from '../pages/heatmapLib';
import { PREVIEW_RANGES, MILESTONES, linePoints, loadSpan, heldSpan, forgetSpans, readingOf, readingText, momentOf } from '../pages/previewLib';
import { aggregated, aggregationDecimals, aggregationSteps, bookOf, depthCurve, imbalance, inferredTick, ladderBase, ladderRows, readLadderPref, saveLadderPref } from '../shell/depthLadder';
import type { Candle, Ticker } from '../data/types';

const tk = (symbol: string, last: number, changePct: number, volume?: number, extra: Partial<Ticker> = {}): Ticker => ({ symbol, last, change: 0, changePct, volume, ts: 0, ...extra });
const bar = (t: number, o: number, h: number, l: number, c: number, v = 10): Candle => ({ t, o, h, l, c, v });

describe('markets2: the bulk pacer (OLD RequestPacer)', () => {
  it('spaces starts one interval apart, shared by every caller of the pacer', () => {
    let now = 1000;
    const p = new RequestPacer(() => 1333, () => now);
    expect([p.reserve(), p.reserve(), p.reserve()]).toEqual([0, 1333, 2666]);
    now += 5000;
    expect(p.reserve()).toBe(0);
  });
  it('45 starts a minute, one pacer per server', () => {
    expect(BULK_INTERVAL_MS).toBe(1333);
    expect(pacerFor('BTCUSDT')).toBe(cryptoPacer);
    expect(pacerFor('ETHUSDT')).toBe(cryptoPacer);
    expect(pacerFor('XAUUSD')).toBe(forexPacer);
    expect(pacerFor('EURUSD')).toBe(forexPacer);
  });
});

describe('markets2: the «داغ» lens, the filter and the pulse', () => {
  const rows = [tk('AUSDT', 1, 1, 1e6), tk('BUSDT', 1, -8, 2e6), tk('CUSDT', 1, 3, 5e6), tk('DUSDT', 1, 0.2, 9e6), tk('EUSDT', 1, NaN, 9e7)];
  it('hot: moved at least the board median, ordered by turnover, unknowns left out', () => {
    // |moves| 0.2, 1, 3, 8 → the upper middle is 3; survivors C (5e6) and B (2e6).
    expect(hottest(rows).map((x) => x.symbol)).toEqual(['CUSDT', 'BUSDT']);
    expect(hottest([])).toEqual([]);
  });
  it('a change band keeps unknown moves; a turnover floor drops unknown turnover', () => {
    expect(bandHolds('up5', 5)).toBe(true);
    expect(bandHolds('down', 0.1)).toBe(false);
    expect(CHANGE_BANDS.map((b) => b.id)).toEqual(['up5', 'up', 'down', 'down5']);
    const f = { ...NO_FILTER, change: 'down5' as const };
    expect(applyFilter(rows, f).map((x) => x.symbol)).toEqual(['BUSDT', 'EUSDT']);
    expect(keeps({ ...NO_FILTER, turnover: 'm10' }, tk('XUSDT', 1, 1))).toBe(false);
    expect(keeps({ ...NO_FILTER, turnover: 'm1' }, tk('XUSDT', 2, 1, 6e5))).toBe(true);
    expect(filterCount({ types: ['crypto'], venues: ['LBank', 'Finnhub'], change: 'up', turnover: null })).toBe(4);
  });
  it('types and venues as present; a venue filter needs the venue', () => {
    const mixed = [tk('BTCUSDT', 1, 1, 1, { source: 'LBank' }), tk('EURUSD', 1, 1, 1, { source: 'Finnhub' }), tk('XAUUSD', 1, 1)];
    expect(typesOf(mixed)).toEqual(['crypto', 'forex', 'metals']);
    expect(venuesOf(mixed)).toEqual(['LBank', 'Finnhub']);
    expect(applyFilter(mixed, { ...NO_FILTER, venues: ['Finnhub'] }).map((x) => x.symbol)).toEqual(['EURUSD']);
    expect(applyFilter(mixed, { ...NO_FILTER, types: ['metals'] }).map((x) => x.symbol)).toEqual(['XAUUSD']);
  });
  it('pulse: volume and breadth from the table; cap, dominance and mood stay dashes', () => {
    const p = pulseOf([tk('AUSDT', 2, 1, 1e6), tk('BUSDT', 1, -1, 5e5), tk('CUSDT', 1, 2, 0)]);
    expect(p.turnover24h).toBe(2.5e6);
    expect(p.breadth).toBe(66);
    expect(pulseValue('volume', p)).toBe('$2.5M');
    expect(pulseValue('cap', p)).toBeNull();
    expect(pulseValue('dominance', p)).toBeNull();
    expect(pulseValue('mood', p)).toBeNull();
    expect(compactFigure(340_200_000)).toBe('$340M');
    expect(compactFigure(1_400_000_000)).toBe('$1.4B');
  });
  it('headlines: six, important when the feed says high; signals summarised and counted', () => {
    const news = Array.from({ length: 9 }, (_, i) => ({ id: String(i), slug: `s${i}`, market: 'crypto' as const, title: `t${i}`, summary: '', source: 'x', time: 0, image: null, importance: 0, newsImpact: i === 0 ? 'high' as const : undefined }));
    const h = headlinesOf(news);
    expect(h).toHaveLength(6);
    expect(h[0]).toEqual({ id: 's0', title: 't0', important: true });
    const sig = [{ symbol: 'BTCUSDT', direction: 'long' as const }, { symbol: 'ETHUSDT', direction: 'short' as const }, { symbol: 'BTCUSDT', direction: 'short' as const }];
    expect(signalSummary(sig, false)).toBe('BTCUSDT خرید · ETHUSDT فروش');
    expect(signalCounts(sig)).toEqual({ BTCUSDT: 2, ETHUSDT: 1 });
  });
});

describe('markets2: heat map facts, metrics, colours and the plan', () => {
  const days = (n: number, start = 100): Candle[] => Array.from({ length: n }, (_, i) => bar(i * 86400, start + i, start + i + 2, start + i - 2, start + i + 1));
  it('the ticker wins; the bars give the period, the median range, the open and the previous close', () => {
    const bars = days(40);
    const a = assetOf('BTCUSDT', tk('BTCUSDT', 150, 2.5, 10, { high: 160, low: 140 }), bars, 'month')!;
    expect(a.changePercent).toBe(2.5);
    expect(a.dayHigh).toBe(160);
    expect(a.openPrice).toBe(139);
    expect(a.previousClose).toBe(139);
    expect(a.periodPercent).toBeCloseTo(((150 - bars[9]!.c) / bars[9]!.c) * 100, 6);
    expect(a.typicalVolatilityPercent).not.toBeNull();
    expect(a.turnover).toBe(1500);
    expect(a.resolved).toBe(true);
  });
  it('no range reported is unknown, never zero; a period longer than the history is null', () => {
    const a = assetOf('EURUSD', tk('EURUSD', 1.1, 0.1), [], 'quarter')!;
    expect(a.dayHigh).toBeNull();
    expect(valueOf(a, 'range')).toBeNull();
    expect(valueOf(a, 'performance')).toBeNull();
    expect(valueOf(a, 'gap')).toBeNull();
    expect(a.resolved).toBe(false);
    expect(assetOf('EURUSD', null, [])).toBeNull();
  });
  it('the live price widens the day and range reads −100…100', () => {
    const a = assetOf('ETHUSDT', tk('ETHUSDT', 120, 1, 1, { high: 110, low: 90 }))!;
    expect(a.dayHigh).toBe(120);
    expect(valueOf(a, 'range')).toBe(100);
    expect(tileFigure(-42.4, 'range')).toBe('−42');
    expect(tileFigure(null, 'change')).toBe('—');
    expect(fmtPercent(-1.234)).toBe('−1.23%');
    expect(fmtAmount(1_240_000_000)).toBe('1.24B');
  });
  it('weights fall back to the liquidity ranking and are never zero', () => {
    expect(rankOf('BTCUSDT')).toBeLessThan(rankOf('ETHUSDT'));
    expect(rankOf('EURUSD')).toBeLessThan(rankOf('GBPUSD'));
    expect(rankOf('ZZZUSDT')).toBe(UNRANKED);
    const a = assetOf('ZZZUSDT', tk('ZZZUSDT', 1, 1))!;
    expect(weightOf(a, 'volume')).toBeGreaterThan(0);
    expect(weightOf(a, 'mono')).toBe(1);
    expect(anyWeightFor([a], 'volume')).toBe(false);
    expect(anyWeightFor([a], 'liquidity')).toBe(true);
    expect(anyValueFor([a], 'gap')).toBe(false);
  });
  it('the scale is the 90th percentile inside the band; the ramp is neutral at zero and the colour-blind ramp is blue up', () => {
    expect(scaleOfValues([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])).toBe(9);
    expect(scaleOfValues([], 0.5, 25)).toBe(0.5);
    expect(scaleOfValues([100], 0.5, 25)).toBe(25);
    expect(colourFor(0, 5, 'classic')).toBe(neutralOf('classic'));
    expect(colourFor(5, 5, 'classic')).toBe(0x00c46a);
    expect(colourFor(-5, 5, 'classic')).toBe(0xf6465d);
    expect(colourFor(5, 5, 'blind')).toBe(0x2f86d6);
    expect(colourFor(5, 5, 'classic', false)).toBe(0xf6465d);
    expect(labelInkFor(0xedeff2)).toBe('#0b0e11');
    expect(labelInkFor(0x14171c)).toBe('#f0f1f2');
  });
  it('a treemap fills the box in input order; the plan groups by class with name strips, caps by density, and mirrors', () => {
    const r = treemap([1, 3, 0, 2], 300, 200);
    expect(r[2]).toEqual({ x: 0, y: 0, w: 0, h: 0 });
    const area = r.reduce((n, x) => n + x.w * x.h, 0);
    expect(area).toBeCloseTo(60000, 3);
    const assets = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'EURUSD', 'GBPUSD', 'XAUUSD'].map((s) => assetOf(s, tk(s, 10, 1, 5))!);
    const p = plan(assets, DEFAULT_OPTIONS, 800, 600, { headerHeight: 32 });
    expect(p.groups.map((g) => g.bucket && g.bucket.kind === 'class' ? g.bucket.market : null).sort()).toEqual(['crypto', 'forex', 'metals']);
    expect(p.size).toBe(6);
    expect(p.known).toBe(6);
    const crypto = p.groups.find((g) => g.bucket?.kind === 'class' && g.bucket.market === 'crypto')!;
    expect(crypto.header).not.toBeNull();
    const focused = plan(assets, DEFAULT_OPTIONS, 800, 600, { focus: { kind: 'class', market: 'forex' } });
    expect(focused.size).toBe(2);
    expect(focused.groups[0]!.header).toBeNull();
    const many = Array.from({ length: 60 }, (_, i) => assetOf(`A${i}USDT`, tk(`A${i}USDT`, 1, 1, 1))!);
    expect(selectAssets(many, { ...DEFAULT_OPTIONS, density: 'focused' })).toHaveLength(48);
    const flat = plan(assets, { ...DEFAULT_OPTIONS, grouping: 'none' }, 800, 600);
    const mirrored = plan(assets, { ...DEFAULT_OPTIONS, grouping: 'none' }, 800, 600, { mirrored: true });
    const t0 = flat.groups[0]!.tiles[0]!.rect, m0 = mirrored.groups[0]!.tiles[0]!.rect;
    expect(m0.x).toBeCloseTo(800 - t0.x - t0.w, 6);
    expect(labelOf('BTCUSDT')).toBe('BTC');
    expect(labelOf('EURUSD')).toBe('EURUSD');
    expect(prettyOf('BTCUSDT')).toBe('BTC/USDT');
    expect(prettyOf('US30')).toBe('US30');
  });
  it('the coverage line', () => {
    expect(coverageText(3, 3, false, true, true, String)).toBeNull();
    expect(coverageText(1, 3, true, true, true, String)).toBe('در حال خواندن کندل روزانه…');
    expect(coverageText(1, 3, false, true, false, String)).toBe('1 of 3 tiles have a figure');
    expect(coverageText(0, 3, false, false, true, String)).toMatch(/هاشورخورده/);
  });
});

describe('markets2: the preview', () => {
  it('six spans with the OLD bar lengths and three milestones', () => {
    expect(PREVIEW_RANGES.map((r) => `${r.id}:${r.interval}:${r.bars}`)).toEqual(['day:15:96', 'week:60:168', 'month:240:180', 'quarter:1D:90', 'year:1D:365', 'all:1W:540']);
    expect(PREVIEW_RANGES.find((r) => r.id === 'all')!.fa).toBe('همه');
    expect(MILESTONES).toEqual([3, 5, 10]);
  });
  it('a span is asked once and held; a failure is not held', async () => {
    forgetSpans();
    let calls = 0;
    const ok = (async () => { calls++; return { bars: Array.from({ length: 200 }, (_, i) => bar(i, 1, 1, 1, i)), more: false }; }) as never;
    const r = PREVIEW_RANGES[0]!;
    expect((await loadSpan('BTCUSDT', r, ok)).length).toBe(96);
    await loadSpan('btcusdt', r, ok);
    expect(calls).toBe(1);
    expect(heldSpan('BTCUSDT', r)).toHaveLength(96);
    const bad = (async () => { throw new Error('x'); }) as never;
    expect(await loadSpan('ETHUSDT', r, bad)).toEqual([]);
    expect(heldSpan('ETHUSDT', r)).toBeNull();
  });
  it('the reading needs sixty bars and names a rise', () => {
    expect(readingOf(Array.from({ length: 30 }, (_, i) => bar(i, 1, 2, 0.5, 1)))).toBeNull();
    const up = Array.from({ length: 120 }, (_, i) => bar(i, 100 + i, 101 + i + (i % 3), 99 + i, 100.5 + i));
    const r = readingOf(up)!;
    expect(r).not.toBeNull();
    expect(readingText(r, false)).toMatch(/^صعودی · قدرت (قوی|متوسط|بدون روند) · نوسان (زیاد|متوسط|کم)$/);
    expect(readingText(r, true)).toMatch(/^Bullish · strength /);
  });
  it('a daily bar drops the clock; an intraday one keeps it in Latin digits', () => {
    const t = Date.UTC(2026, 9, 1, 12, 30) / 1000;
    expect(momentOf(t, PREVIEW_RANGES[0]!, false)).toMatch(/ · \d\d:\d\d$/);
    expect(momentOf(t, PREVIEW_RANGES[3]!, false)).not.toMatch(/:/);
    expect(momentOf(t, PREVIEW_RANGES[5]!, true)).toMatch(/2026/);
    expect(linePoints([1], 10, 10)).toEqual([]);
    expect(linePoints([1, 3], 100, 20)).toEqual([{ x: 0, y: 16 }, { x: 100, y: 4 }]);
  });
});

describe('markets2: the order book', () => {
  const raw = { bids: [[100, 1, 2], [99.5, 2, 1], [99, 3], ['x', 1], [98.5, 0]], asks: [[100.5, 1], [101, 2, 4], [101.5, 3]] };
  it('reads levels, merges equal prices, drops bad ones; the tick, the base and the steps', () => {
    const b = bookOf(raw);
    expect(b.bids.map((l) => l.price)).toEqual([100, 99.5, 99]);
    expect(b.bids[0]!.orders).toBe(2);
    expect(b.bids[2]!.orders).toBeNull();
    expect(inferredTick(b)).toBe(0.5);
    expect(ladderBase(0.5)).toBe(0.5);
    expect(ladderBase(40.5)).toBe(10);
    expect(ladderBase(0.0099999999)).toBe(0.01);
    expect(aggregationSteps(b)).toEqual([2.5, 5, 25, 50]);
    expect(aggregationSteps(b, 2)).toEqual([2, 2.5, 5, 25, 50]);
    expect(aggregationDecimals(0.05)).toBe(2);
    expect(aggregationDecimals(10)).toBe(0);
  });
  it('aggregation rounds bids down and asks up, summing what lands together', () => {
    const b = aggregated(bookOf(raw), 1);
    expect(b.bids).toEqual([{ price: 100, quantity: 1, orders: 2 }, { price: 99, quantity: 5, orders: 1 }]);
    expect(b.asks.map((l) => [l.price, l.quantity])).toEqual([[101, 3], [102, 3]]);
    expect(aggregated(bookOf(raw), 1000)).toEqual(bookOf(raw));
  });
  it('bid share, cumulative bars and the curve', () => {
    const b = bookOf(raw);
    expect(imbalance(b)).toBeCloseTo(6 / 12, 9);
    const l = ladderRows(b, 2);
    expect(l.asks.map((r) => r.price)).toEqual([101, 100.5]);
    expect(l.bids[1]!.total).toBe(3);
    expect(l.bids[1]!.cumulativeBarFraction).toBe(1);
    expect(l.bids[1]!.barFraction).toBe(1);
    const c = depthCurve(b)!;
    expect(c.mid).toBe(100.25);
    expect(c.lowPrice).toBe(99);
    expect(c.highPrice).toBe(101.5);
    expect(c.bids[2]!.y).toBe(1);
    expect(depthCurve(bookOf({ bids: [[101, 1]], asks: [[100, 1]] }))).toBeNull();
  });
  it('the step and the figure are kept per symbol', () => {
    saveLadderPref('btcusdt', { step: 5, figure: 'cumulative' });
    expect(readLadderPref('BTCUSDT')).toEqual({ step: 5, figure: 'cumulative' });
    expect(readLadderPref('ETHUSDT')).toEqual({ step: null, figure: 'amount' });
  });
});
