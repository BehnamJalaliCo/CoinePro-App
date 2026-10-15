import { beforeEach, describe, expect, it } from 'vitest';
import { STUDIES } from '../engine/indicators_ext_c.js';
import { INPUT_LABELS, MENU_INPUTS } from '../chart/studyMeta';
import { useStudies, visibleOn, intervalClass, VOLUME } from '../state/studies';
import { computeStudy, fromTimeframe, splitColor, toTimeframe, withAlpha, columns } from '../chart/studies';
import type { Candle } from '../data/types';

const bars = (n: number, step = 3600): Candle[] => Array.from({ length: n }, (_, i) => {
  const c = 100 + Math.sin(i / 7) * 5 + i * 0.05;
  return { t: 1_700_000_000 - (1_700_000_000 % 86400) + i * step, o: c - 0.3, h: c + 1, l: c - 1, c, v: 100 + (i % 9) * 10 };
});

describe('indicator inputs', () => {
  it('every input key of every indicator has a Persian and an English label', () => {
    const keys = new Set(Object.values(STUDIES).flatMap((d) => Object.keys(d.inputs)));
    for (const k of keys) {
      expect(INPUT_LABELS[k], k).toBeTruthy();
      expect(INPUT_LABELS[k]![0].length, k).toBeGreaterThan(0);
      expect(INPUT_LABELS[k]![1].length, k).toBeGreaterThan(0);
    }
  });
  it('pivot type is a menu with every type the engine knows', () => {
    expect(MENU_INPUTS.pivotType!.map((o) => o.value)).toEqual(['Classic', 'Fibonacci', 'Woodie', 'Camarilla', 'DM']);
  });
});

describe('visibility by timeframe', () => {
  it('reads interval classes', () => {
    expect(intervalClass('15')).toEqual({ cls: 'minutes', n: 15 });
    expect(intervalClass('240')).toEqual({ cls: 'hours', n: 4 });
    expect(intervalClass('1D')).toEqual({ cls: 'days', n: 1 });
    expect(intervalClass('1W')).toEqual({ cls: 'weeks', n: 1 });
  });
  it('hides a study outside its ranges', () => {
    expect(visibleOn(undefined, '15')).toBe(true);
    expect(visibleOn({ minutes: { on: false, from: 1, to: 59 } }, '15')).toBe(false);
    expect(visibleOn({ minutes: { on: true, from: 30, to: 59 } }, '15')).toBe(false);
    expect(visibleOn({ minutes: { on: true, from: 5, to: 30 } }, '15')).toBe(true);
    expect(visibleOn({ minutes: { on: false, from: 1, to: 59 } }, '60')).toBe(true);
  });
});

describe('colours', () => {
  it('applies and reads opacity', () => {
    expect(withAlpha('#2962FF', 0.5)).toBe('rgba(41, 98, 255, 0.5)');
    expect(withAlpha('#2962FF', 1)).toBe('#2962FF');
    expect(splitColor('rgba(126, 87, 194, 0.1)')).toEqual({ hex: '#7E57C2', opacity: 0.1 });
    expect(splitColor('#fff')).toEqual({ hex: '#FFFFFF', opacity: 1 });
  });
});

describe('an indicator on a higher timeframe', () => {
  it('never shows a value before its bar closed when waiting for closes', () => {
    const b = bars(48, 3600);
    const k = toTimeframe(columns(b), '240', 3600);
    expect(k.cols.close.length).toBe(12);
    const htf = k.cols.close.map((c) => c);
    const waited = fromTimeframe(htf, k, true);
    for (let i = 0; i < b.length; i++) {
      if (waited[i] == null) continue;
      // The value equals the close of the four-hour bar that ends at this bar.
      expect(waited[i]).toBe(b[i]!.c);
      expect(k.last[i]).toBe(true);
    }
    const stepped = fromTimeframe(htf, k, false);
    // Inside a bucket, the previous bucket's value (no look-ahead).
    expect(stepped[5]).toBe(htf[0]);
  });
});

describe('studies store', () => {
  beforeEach(() => { localStorage.clear(); useStudies.setState({ list: [{ uid: 'vol', key: VOLUME, inputs: {} }], recent: [], defaults: {} }); });
  it('adds with saved defaults, duplicates, moves and remembers recent', () => {
    const s = useStudies.getState();
    const a = s.add('rsi')!;
    useStudies.getState().patch(a, { inputs: { ...STUDIES.rsi!.inputs, period: 21 }, style: { precision: 3 } });
    useStudies.getState().saveDefault(a);
    const b = useStudies.getState().add('rsi')!;
    const added = useStudies.getState().list.find((x) => x.uid === b)!;
    expect(added.inputs.period).toBe(21);
    expect(added.style?.precision).toBe(3);
    const c = useStudies.getState().duplicate(a)!;
    const list = useStudies.getState().list.map((x) => x.uid);
    expect(list.indexOf(c)).toBe(list.indexOf(a) + 1);
    useStudies.getState().move(c, -1);
    expect(useStudies.getState().list.map((x) => x.uid).indexOf(c)).toBe(list.indexOf(a));
    expect(useStudies.getState().recent[0]).toBe('rsi');
    useStudies.getState().resetDefault('rsi');
    const d = useStudies.getState().add('rsi');
    expect(useStudies.getState().list.find((x) => x.uid === d)!.inputs.period).toBe(14);
  });
});

describe('an indicator on an indicator', () => {
  it('an SMA of the RSI follows the RSI, with its warm-up left empty', () => {
    const b = bars(200);
    const rsi = { uid: 'r1', key: 'rsi', inputs: { ...STUDIES.rsi!.inputs } };
    const ma = { uid: 'm1', key: 'ma', inputs: { period: 5, source: 'study:r1:0' } };
    const out = computeStudy(ma, b, new Map(), [rsi, ma])!;
    const r = computeStudy(rsi, b, new Map(), [rsi])!.items[0]!.values;
    const m = out.items[0]!.values;
    expect(m[10]).toBeNull();
    const i = 150;
    const want = (r[i - 4]! + r[i - 3]! + r[i - 2]! + r[i - 1]! + r[i]!) / 5;
    expect(m[i]).toBeCloseTo(want, 6);
  });
});
