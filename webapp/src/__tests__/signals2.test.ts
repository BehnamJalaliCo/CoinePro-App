// The signal layer's arithmetic (OLD SignalSpec.kt, ConfidenceEngine.kt, SignalMarkers.kt,
// ChartSignals.kt markersFor, RepaintClaim.kt, ChartProvenance.kt), number for number.
import { describe, expect, it } from 'vitest';
import type { Candle } from '../data/types';
import { STUDIES } from '../engine/indicators_ext_c.js';
import {
  CONFIDENCE_CAP, DEFAULT_HORIZON, HORIZONS, MARKERS_PER_STUDY, THIN, bounded, breakout, chartExclusions, defaultStop, detailFor, exclusionsLine, figure,
  flipping, formatR, isSilent, isolateNumbers, makeSeries, markersFor, measureRead, noteText, oscillatorState, percentOf, readStudy, readVerdicts,
  reference, referenceState, repaintLabel, repaintOf, setupScore, sizeFor, snapHorizon, stateLabel, thinMarks, trustworthy, wilderAtr, zeroCross,
  type ConfidenceReport, type SignalRead,
} from '../chart/signals';

const bar = (i: number, c: number, spread = 1): Candle => ({ t: 1_700_000_000 + i * 3600, o: c, h: c + spread, l: c - spread, c, v: 100 });
const flat = (n: number, c = 100) => Array.from({ length: n }, (_, i) => bar(i, c));
const report = (id: string, samples: number, winRate: number): ConfidenceReport => ({ id, samples, winRate, averageR: 0, recent: [], horizon: 10 });
const read = (id: string, state: SignalRead['state'], events = 0): SignalRead => ({ id, state, events: Array.from({ length: events }, (_, i) => ({ bar: i, side: 'buy', strength: 1 })), note: { shape: 'quiet', values: [] }, stop: null });

describe('signals2: what a study says', () => {
  it('signals2: the sixteen shapes read in both languages', () => {
    expect(noteText({ shape: 'leftFloor', values: ['RSI', '30.0'] }, false)).toBe('RSI از 30.0 برگشت');
    expect(noteText({ shape: 'leftFloor', values: ['RSI', '30.0'] }, true)).toBe('RSI came back over 30.0');
    expect(noteText({ shape: 'crossedAbove', values: ['قیمت', 'EMA'] }, false)).toBe('قیمت از بالای EMA رد شد');
    expect(noteText({ shape: 'brokeHigh', values: ['20'] }, false)).toBe('قیمت سقف 20 کندل را شکست');
    expect(noteText({ shape: 'quiet', values: [] }, true)).toBe('No signal on this bar');
    expect(stateLabel('bull', false)).toBe('صعودی');
    expect(stateLabel('neutral', true)).toBe('Neutral');
    expect(figure(30)).toBe('30.0');
    expect(figure(0.5)).toBe('0.50');
    expect(figure(91263.03)).toBe('91263');
  });

  it('signals2: the dead band round an oscillator\'s middle and the touch zone round a line', () => {
    // RSI 48.6 used to be called bearish; on 30..70 the dead band is 40–60.
    expect(oscillatorState(48.6, [30, 70])).toBe('neutral');
    expect(oscillatorState(60.5, [30, 70])).toBe('bull');
    expect(oscillatorState(39.9, [30, 70])).toBe('bear');
    expect(oscillatorState(null, [30, 70])).toBe('neutral');
    expect(referenceState(100.1, 100, 1)).toBe('neutral'); // within 0.15 ATR
    expect(referenceState(100.2, 100, 1)).toBe('bull');
    expect(referenceState(99.8, 100, 1)).toBe('bear');
    expect(referenceState(100.0001, 100, 0)).toBe('bull'); // no range: the strict comparison
  });

  it('signals2: leaving the floor is a buy, leaving the ceiling a sell, with the sentence on the newest bar', () => {
    const s = makeSeries(flat(30));
    const line = Array.from({ length: 30 }, (_, i) => (i < 28 ? 50 : i === 28 ? 25 : 35));
    const r = bounded('u1', 'RSI', line, [30, 70], s);
    expect(r.events).toEqual([{ bar: 29, side: 'buy', strength: (30 - 25) / 40 }]);
    expect(r.note).toEqual({ shape: 'leftFloor', values: ['RSI', '30.0'] });
    expect(r.state).toBe('bear'); // 35 is under the 40–60 band
    const down = bounded('u1', 'RSI', Array.from({ length: 30 }, (_, i) => (i < 29 ? 75 : 65)), [30, 70], s);
    expect(down.events[0]?.side).toBe('sell');
    expect(noteText(down.note, false)).toBe('RSI از 70.0 پایین آمد');
    const at = bounded('u1', 'RSI', Array.from({ length: 30 }, () => 20), [30, 70], s);
    expect(noteText(at.note, true)).toBe('RSI is under 30.0');
  });

  it('signals2: a close crossing a line on the price, and the line is its stop', () => {
    const bars = [...flat(25, 100), bar(25, 103)];
    const s = makeSeries(bars);
    const r = reference('u2', 'EMA', bars.map(() => 101), s, false);
    expect(r.events).toEqual([{ bar: 25, side: 'buy', strength: 1 }]);
    expect(noteText(r.note, false)).toBe('قیمت از بالای EMA رد شد');
    expect(r.stop).toBe(101);
  });

  it('signals2: zero, or the study\'s own signal line', () => {
    const s = makeSeries(flat(25));
    const line = Array.from({ length: 25 }, (_, i) => (i < 24 ? -1 : 1));
    expect(noteText(zeroCross('u', 'MOM', line, s, null, true).note, true)).toBe('MOM crossed zero upward');
    const sig = Array.from({ length: 25 }, () => 0.5);
    expect(noteText(zeroCross('u', 'MACD', line, s, sig, false).note, false)).toBe('MACD از بالای خط سیگنالش رد شد');
    const steady = Array.from({ length: 25 }, () => 2);
    expect(noteText(zeroCross('u', 'MOM', steady, s, null, false).note, false)).toBe('MOM بالای 0 است');
  });

  it('signals2: a flip, a Donchian breakout, and a script\'s own verdicts', () => {
    const bars = [...flat(24, 100), bar(24, 110)];
    const s = makeSeries(bars);
    const f = flipping('u', 'Supertrend', bars.map(() => 105), s, true);
    expect(f.note).toEqual({ shape: 'turnedUp', values: ['Supertrend'] });
    expect(f.stop).toBe(105);
    const d = breakout('u', s, 20, false);
    expect(noteText(d.note, false)).toBe('قیمت سقف 20 کندل را شکست');
    expect(d.stop).toBe(99); // the channel's floor under a buy
    const v = readVerdicts('sc', 'My script', [3, 12], [8], s);
    expect(v.state).toBe('bull');
    expect(v.events.map((e) => e.bar)).toEqual([3, 8, 12]);
  });

  it('signals2: the stop is the swing behind the signal with a quarter-ATR of air', () => {
    const bars = flat(30, 100);
    bars[20] = { ...bars[20]!, l: 95 };
    const s = makeSeries(bars);
    const atr = wilderAtr(s.high, s.low, s.close, 14)[29]!;
    expect(atr).toBeCloseTo(s.atr[29]!, 12);
    expect(defaultStop(s, { bar: 25, side: 'buy', strength: 1 })).toBeCloseTo(95 - s.atr[29]! * 0.25, 9);
    expect(defaultStop(s, { bar: 25, side: 'sell', strength: 1 })).toBeCloseTo(101 + s.atr[29]! * 0.25, 9);
  });

  it('signals2: real studies through the four rules, and below twenty bars a study is quiet', () => {
    const bars: Candle[] = [];
    let p = 100;
    for (let i = 0; i < 300; i++) { p *= 1 + Math.sin(i / 7) * 0.012; bars.push({ t: 1_700_000_000 + i * 3600, o: p, h: p * 1.004, l: p * 0.996, c: p, v: 10 }); }
    const s = makeSeries(bars);
    const cols = { open: bars.map((b) => b.o), high: bars.map((b) => b.h), low: bars.map((b) => b.l), close: bars.map((b) => b.c), volume: bars.map((b) => b.v), time: bars.map((b) => b.t) };
    const out = (k: string) => STUDIES[k]!.calc(cols, { ...STUDIES[k]!.inputs });
    const rsi = readStudy({ id: 'r', key: 'rsi', name: 'RSI', overlay: false }, out('rsi'), s, false);
    expect(rsi.events.length).toBeGreaterThan(3);
    expect(rsi.events.every((e) => e.bar > 0)).toBe(true);
    expect(rsi.stop).not.toBeNull();
    const macd = readStudy({ id: 'm', key: 'macd', name: 'MACD', overlay: false }, out('macd'), s, true);
    expect(macd.events.length).toBeGreaterThan(3);
    const atr = readStudy({ id: 'a', key: 'atr', name: 'ATR', overlay: false }, out('atr'), s, true);
    expect(atr.events).toEqual([]);
    expect(atr.note.shape).toBe('reads');
    const st = readStudy({ id: 's', key: 'supertrend', name: 'Supertrend', overlay: true }, out('supertrend'), s, false);
    expect(st.stop).not.toBeNull();
    expect(readStudy({ id: 'r', key: 'rsi', name: 'RSI', overlay: false }, out('rsi'), makeSeries(bars.slice(0, 19)), false).note.shape).toBe('quiet');
    expect(isSilent(readStudy({ id: 'p', key: 'pivots', name: 'Pivots', overlay: true, structure: true }, out('pivots'), s, false))).toBe(true);
  });

  it('signals2: figures are isolated inside the prose', () => {
    expect(isolateNumbers('در 61% از 18 بار')).toBe('در ⁦61%⁩ از ⁦18⁩ بار');
  });
});

describe('signals2: how it has done here', () => {
  it('signals2: the stop is checked bar by bar before the horizon; an unfinished signal is no outcome', () => {
    // A buy at bar 25 (close 100); bar 27's low reaches the stop → a loss of one R.
    const bars = flat(60, 100);
    const r: SignalRead = { id: 'x', state: 'bull', events: [{ bar: 25, side: 'buy', strength: 1 }, { bar: 30, side: 'buy', strength: 1 }, { bar: 55, side: 'buy', strength: 1 }], note: { shape: 'quiet', values: [] }, stop: null };
    const stops: Record<number, number> = { 25: 98, 30: 95 };
    bars[27] = { ...bars[27]!, l: 97.5 };
    bars[40] = { ...bars[40]!, c: 110 };
    const rep = measureRead(r, makeSeries(bars), 10, (e) => stops[e.bar] ?? null);
    expect(rep.samples).toBe(2); // bar 55 + 10 is past the end
    expect(rep.winRate).toBe(0.5);
    expect(rep.recent).toEqual([false, true]);
    expect(rep.averageR).toBeCloseTo((-1 + (110 - 100) / 5) / 2, 9);
    expect(rep.horizon).toBe(10);
  });

  it('signals2: under eight samples there is no percentage, and the horizons are 5, 10 and 20', () => {
    expect(THIN).toBe(8);
    expect([...HORIZONS]).toEqual([5, 10, 20]);
    expect(DEFAULT_HORIZON).toBe(10);
    expect(trustworthy(report('a', 7, 0.71))).toBe(false);
    expect(percentOf(report('a', 7, 0.71))).toBe(0);
    expect(percentOf(report('a', 18, 0.611))).toBe(61);
    expect(snapHorizon(12)).toBe(10);
    expect(snapHorizon(30)).toBe(20);
    expect(formatR(0.4249)).toBe('+0.42');
    expect(formatR(-1)).toBe('-1.00');
  });

  it('signals2: the Setup score is confidence, not consensus', () => {
    // Four studies agreeing at about 40% each: 40, not 100.
    const reads = ['a', 'b', 'c', 'd'].map((id) => read(id, 'bull', 2));
    const reports = { a: report('a', 30, 0.43), b: report('b', 30, 0.4), c: report('c', 30, 0.39), d: report('d', 30, 0.4) };
    const sc = setupScore(reads, reports);
    expect(sc.score).toBe(41);
    expect(sc.side).toBe('bull');
    expect(sc.studies).toBe(4);
    expect(sc.signals).toBe(8);
    expect(sc.winRate).toBeCloseTo(0.405, 9);
    expect(sc.score).toBeLessThanOrEqual(CONFIDENCE_CAP);
    // Disagreement cancels; a thin record counts for less; no record, no vote.
    const mixed = setupScore([read('a', 'bull'), read('b', 'bear')], { a: report('a', 30, 0.6), b: report('b', 6, 0.6) });
    expect(mixed.score).toBe(Math.round(((0.6 - 0.6 * 0.2) / 1.2) * 100));
    const none = setupScore([read('a', 'bull'), read('b', 'bull'), read('c', 'bear')], {});
    expect(none).toEqual({ score: 0, side: 'bull', signals: 0, studies: 3, winRate: null });
    expect(setupScore([read('a', 'neutral')], {}).studies).toBe(0);
  });
});

describe('signals2: marks, repaint, provenance', () => {
  it('signals2: six newest marks a study, labels / triangles / off, hidden studies drop out', () => {
    const bars = flat(40);
    const r: SignalRead = { id: 'u', state: 'bull', events: Array.from({ length: 9 }, (_, i) => ({ bar: i * 4, side: i % 2 ? 'sell' : 'buy', strength: i / 10 })), note: { shape: 'quiet', values: [] }, stop: null };
    const marks = markersFor([r], bars, { english: false, colours: { u: '#7E57C2' } });
    expect(marks.length).toBe(MARKERS_PER_STUDY);
    expect(marks[0]!.bar).toBe(12);
    expect(marks.find((m) => m.side === 'buy')!.label).toBe('خرید');
    expect(marks.find((m) => m.side === 'sell')!.above).toBe(true);
    expect(marks.find((m) => m.side === 'buy')!.price).toBe(99);
    expect(markersFor([r], bars, { english: true, styles: { u: 'triangles' } }).every((m) => m.label === null)).toBe(true);
    expect(markersFor([r], bars, { english: true, styles: { u: 'off' } })).toEqual([]);
    expect(markersFor([r], bars, { english: true, hidden: new Set(['u']) })).toEqual([]);
    expect(detailFor(12)).toBe('label');
    expect(detailFor(12, 'triangles')).toBe('triangle');
    expect(detailFor(5)).toBe('thinned');
    expect(detailFor(20, 'off')).toBe('hidden');
    expect([sizeFor(0.1), sizeFor(0.5), sizeFor(0.9)]).toEqual([6, 8, 10]);
    const thin = thinMarks(markersFor([{ ...r, events: [{ bar: 1, side: 'buy', strength: 0.2 }, { bar: 3, side: 'buy', strength: 0.9 }, { bar: 4, side: 'sell', strength: 0.1 }] }], bars, { english: true }));
    expect(thin.map((m) => m.bar).sort()).toEqual([3, 4]);
  });

  it('signals2: the four repaint claims, and a study on its own unclosed timeframe rewrites', () => {
    expect(repaintOf('pivots')).toBe('settled');
    expect(repaintOf('fractals')).toBe('late');
    expect(repaintOf('chopZone')).toBe('liveBar');
    expect(repaintOf('zigzag')).toBe('repaints');
    expect(repaintOf('rsi')).toBeNull();
    expect(repaintOf('rsi', { ownTimeframe: true, waitClose: false })).toBe('repaints');
    expect(repaintOf('rsi', { ownTimeframe: true, waitClose: true })).toBeNull();
    expect(repaintLabel('settled', false)).toBe('repaint نمی‌کند');
    expect(repaintLabel('late', true)).toBe('Settles a few bars later');
    expect(repaintLabel('liveBar', false)).toBe('تا بسته‌شدن کندل تغییر می‌کند');
    expect(repaintLabel('repaints', false)).toBe('عقب‌تر دوباره نوشته می‌شود');
  });

  it('signals2: built on this device, no volume, replay hides the future, and what rewrites', () => {
    const noVol = flat(5).map((b) => ({ ...b, v: 0 }));
    const list = chartExclusions({ foldedFrom: 'H1', bars: noVol, replaying: true, repainting: ['Zig Zag'] });
    expect(list.map((x) => x.kind)).toEqual(['folded', 'noVolume', 'replay', 'repainting']);
    const fa = exclusionsLine(list, false);
    expect(fa.startsWith('آنچه در این تصویر نیست — ')).toBe(true);
    expect(fa).toContain('از کندل‌های H1 ساخته می‌شوند');
    expect(fa).toContain('این فید حجم نمی‌فرستد.');
    expect(fa).toContain('بازپخش روشن است.');
    expect(fa).toContain('Zig Zag: با آمدن کندل‌های تازه');
    expect(exclusionsLine(chartExclusions({ foldedFrom: null, bars: flat(5), replaying: false, repainting: [] }), true)).toBe('');
  });
});
