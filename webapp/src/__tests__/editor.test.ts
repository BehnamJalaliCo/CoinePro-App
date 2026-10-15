import { describe, expect, it } from 'vitest';
import { activeCall, highlight, toggleComment } from '../shell/CodeEditor';
import { simulate, fromIndices, grid, runBacktest, DEFAULT_OPTIONS } from '../pages/backtestEngine';
import type { Candle } from '../data/types';

describe('code editor helpers', () => {
  it('highlights comments, strings, numbers, keywords and calls', () => {
    const h = highlight('// note\nx = ta.sma(close, 20) // c\nfoo("a")');
    expect(h).toContain('<span class="tk-cm">// note</span>');
    expect(h).toContain('<span class="tk-kw">ta.sma</span>');
    expect(h).toContain('<span class="tk-num">20</span>');
    expect(h).toContain('<span class="tk-fn">foo</span>');
    expect(h).toContain('<span class="tk-str">"a"</span>');
    expect(highlight('a < b')).toContain('&lt;');
  });
  it('finds the call under the caret', () => {
    expect(activeCall('x = ta.ema(close, ')?.name).toBe('ta.ema');
    expect(activeCall('x = ta.ema(close, 9) + 1')).toBeNull();
    expect(activeCall('plot(ta.rsi(close, 14), ')?.name).toBe('plot');
  });
  it('comments and uncomments the selected lines', () => {
    const a = toggleComment('a = 1\n  b = 2\nc = 3', 0, 12);
    expect(a.text).toBe('// a = 1\n  // b = 2\nc = 3');
    expect(toggleComment(a.text, 0, a.end).text).toBe('a = 1\n  b = 2\nc = 3');
  });
});

describe('strategy tester engine', () => {
  const bars: Candle[] = Array.from({ length: 120 }, (_, i) => { const c = 100 + Math.sin(i / 6) * 10; return { t: 1_700_000_000 + i * 3600, o: c - 0.2, h: c + 1, l: c - 1, c, v: 1 }; });
  const sig = { long: [10, 50, 90], short: [], exitL: [30, 70, 110], exitS: [] };
  it('keeps entry and exit, size, run-up, drawdown and the running total', () => {
    const r = simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, feePct: 0 });
    expect(r.trades.length).toBe(3);
    const t = r.trades[0]!;
    expect(t.entry).toBe(bars[11]!.o);
    expect(t.exit).toBe(bars[31]!.o);
    expect(t.qty).toBeGreaterThan(0);
    expect(t.runUp!).toBeGreaterThanOrEqual(0);
    expect(r.trades[2]!.cum).toBeCloseTo(r.trades.reduce((n, x) => n + x.pnl, 0), 6);
    expect(r.equity.length).toBe(bars.length - 1);
    expect(r.long!.trades).toBe(3);
  });
  it('commission, slippage and filling on close change the result', () => {
    const base = simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, feePct: 0 });
    const costly = simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, feePct: 0.1, slippagePct: 0.1 });
    expect(costly.net).toBeLessThan(base.net);
    const onClose = simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, feePct: 0, fillOnClose: true });
    expect(onClose.trades[0]!.entry).toBe(bars[10]!.c);
  });
  it('pyramids up to the limit and respects the date range', () => {
    const s2 = { long: [10, 12, 14], short: [], exitL: [40], exitS: [] };
    expect(simulate(bars, fromIndices(bars.length, s2), { ...DEFAULT_OPTIONS, pyramiding: 2 }).trades.length).toBe(2);
    expect(simulate(bars, fromIndices(bars.length, s2), { ...DEFAULT_OPTIONS }).trades.length).toBe(1);
    expect(simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, from: bars[45]!.t }).trades.length).toBe(2);
  });
  it('sizes by contracts or cash', () => {
    expect(simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, sizeMode: 'qty', qty: 3 }).trades[0]!.qty).toBe(3);
    const c = simulate(bars, fromIndices(bars.length, sig), { ...DEFAULT_OPTIONS, sizeMode: 'cash', cash: 500 }).trades[0]!;
    expect(c.qty! * c.entry).toBeCloseTo(500, 6);
  });
  it('the legacy signal array still works and the grid enumerates combinations', () => {
    expect(runBacktest(bars, bars.map((_, i) => (i === 10 ? 1 : i === 40 ? -1 : 0)), DEFAULT_OPTIONS).trades.length).toBeGreaterThan(0);
    expect(grid([{ key: 'a', from: 1, to: 3, step: 1 }, { key: 'b', from: 10, to: 20, step: 5 }]).length).toBe(9);
  });
});
