import { describe, expect, it } from 'vitest';
import { EXT_REGISTRY_C, profileRows, periodKey } from '../engine/indicators_ext_c.js';
import { TV_PATTERNS, detectPattern, patternName } from '../engine/candlePatterns.js';
import { CATALOG, fold, rank } from '../chart/indicatorCatalog';

const K = (t: number, o: number, h: number, l: number, c: number) => ({ t, o, h, l, c });
/** A falling series of 60 small bars, then the bars under test (the trend filter reads SMA 50). */
const downtrend = () => Array.from({ length: 60 }, (_, i) => { const c = 200 - i; return K(i, c + 0.4, c + 0.6, c - 0.1, c); });

describe('single candlestick patterns', () => {
  it('there are TradingView’s 44, each with Persian and English names', () => {
    expect(TV_PATTERNS.length).toBe(44);
    for (const p of TV_PATTERNS) { expect(p.fa.length).toBeGreaterThan(2); expect(p.en.length).toBeGreaterThan(2); }
  });
  it('finds a hammer after a fall', () => {
    const cs = [...downtrend(), K(60, 140.6, 140.7, 137, 140.9)];
    expect(detectPattern('hammer', cs)).toContain(60);
  });
  it('finds a bullish engulfing after a fall', () => {
    const cs = [...downtrend(), K(60, 140.5, 140.7, 140.1, 140.2), K(61, 139.8, 143.5, 139.7, 143.2)];
    expect(detectPattern('engulfingBull', cs)).toContain(61);
  });
  it('finds a rising window in a rise', () => {
    const up = Array.from({ length: 60 }, (_, i) => { const c = 100 + i; return K(i, c - 0.4, c + 0.1, c - 0.6, c); });
    const cs = [...up, K(60, 162, 163, 161.5, 162.8)];
    expect(detectPattern('risingWindow', cs)).toContain(60);
  });
  it('labels in English for the English interface', () => {
    expect(patternName('پوششی صعودی، دوجی', 'en')).toBe('Bullish Engulfing, Doji');
    expect(patternName('دوجی', 'fa')).toBe('دوجی');
  });
});

describe('added indicators', () => {
  const N = 400;
  const c = { open: [] as number[], high: [] as number[], low: [] as number[], close: [] as number[], volume: [] as number[], time: [] as number[] };
  let p = 100;
  for (let k = 0; k < N; k++) { const o = p; p += Math.sin(k / 5) * 2; c.open.push(o); c.close.push(p); c.high.push(Math.max(o, p) + 1); c.low.push(Math.min(o, p) - 1); c.volume.push(500 + k); c.time.push(1_700_006_400 + k * 3600); }
  it('every one computes on bars with volume', () => {
    for (const [k, d] of Object.entries(EXT_REGISTRY_C)) expect(() => d.calc(c, { ...d.inputs, __other: c.close, __from: 300, __to: 399 }), k).not.toThrow();
  });
  it('a volume profile keeps every unit of volume and marks one point of control', () => {
    const r = profileRows(c, 0, 99, 20, 70)!;
    const total = r.rows.reduce((s, x) => s + x.up + x.down, 0);
    const want = c.volume.slice(0, 100).reduce((s, v) => s + v, 0);
    expect(total).toBeCloseTo(want, 6);
    expect(r.rows.filter((x) => x.poc).length).toBe(1);
    expect(r.vah).toBeGreaterThan(r.val);
  });
  it('periods key by day, week and month', () => {
    const mon = Date.UTC(2026, 9, 12) / 1000; // a Monday
    expect(periodKey(mon, 'W')).toBe(periodKey(mon + 6 * 86400, 'W'));
    expect(periodKey(mon, 'W')).not.toBe(periodKey(mon - 1, 'W'));
    expect(periodKey(Date.UTC(2026, 9, 31) / 1000, 'M')).not.toBe(periodKey(Date.UTC(2026, 10, 1) / 1000, 'M'));
  });
  it('the correlation of a series with itself is one', () => {
    const out = EXT_REGISTRY_C.correlation!.calc(c, { length: 20, __other: c.close });
    expect(out.line![200]).toBeCloseTo(1, 6);
  });
  it('moon phases come about every 14.8 days', () => {
    const t = Array.from({ length: 24 * 120 }, (_, k) => 1_760_000_000 + k * 3600);
    const cols = { open: t.map(() => 1), high: t.map(() => 1), low: t.map(() => 1), close: t.map(() => 1), volume: t.map(() => 0), time: t };
    const m = EXT_REGISTRY_C.moonPhases!.calc(cols, {}).markers!;
    expect(m.length).toBeGreaterThanOrEqual(7);
    expect(m.length).toBeLessThanOrEqual(9);
  });
});

describe('dialog search', () => {
  it('folds Arabic letters, the zero-width non-joiner and diacritics', () => {
    expect(fold('ميانگين متحرك')).toBe(fold('میانگین متحرک'));
    expect(fold('نوسان‌ساز')).toBe('نوسان ساز');
  });
  it('ranks the abbreviation first', () => {
    const rsi = CATALOG.find((e) => e.key === 'rsi')!;
    const crsi = CATALOG.find((e) => e.key === 'crsi')!;
    expect(rank(rsi, 'rsi')!).toBeLessThan(rank(crsi, 'rsi')!);
    expect(rank(rsi, 'zzz')).toBeNull();
  });
  it('profiles and patterns have their own tabs', () => {
    expect(CATALOG.filter((e) => e.tab === 'profiles').length).toBe(5);
    expect(CATALOG.filter((e) => e.tab === 'patterns').length).toBe(46);
  });
});
