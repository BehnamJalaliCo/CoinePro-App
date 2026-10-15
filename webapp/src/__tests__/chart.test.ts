import { describe, expect, it, beforeEach } from 'vitest';
import { buildSeries, pnfBuild, renkoBuild, shareVolume, type SeriesStyle, type TypeParams } from '../chart/chartTypes';
import { kagiRuns } from '../chart/primitives';
import { barTimeText, clockText, dateText } from '../chart/time';
import { DEFAULT_SETTINGS, migrate, useChartSettings } from '../state/chartSettings';
import { withLabel, withLine } from '../chart/PriceScaleMenu';
import { visibleStats } from '../chart/PriceLines';
import { barIndexAt, currenciesOf, importanceOk } from '../chart/EventMarks';
import { useStudies, VOLUME } from '../state/studies';
import { useCompare } from '../state/compare';
import { indexAt } from '../chart/ChartView';
import { computeProfile } from '../engine/volumeProfile.js';
import type { Candle } from '../data/types';

const bar = (t: number, o: number, h: number, l: number, c: number, v = 10): Candle => ({ t, o, h, l, c, v });
const style: SeriesStyle = {
  up: '#089981', down: '#F23645', line: '#2962FF', bg: '#FFFFFF', bodyVisible: true, borderVisible: true, borderUp: '#089981', borderDown: '#F23645',
  wickVisible: true, wickUp: '#089981', wickDown: '#F23645', colorByPrevClose: false, thinBars: false, hollow: false, lineWidth: 2, areaTop: '', areaBottom: '',
};
const params: TypeParams = {
  renkoMethod: 'traditional', renkoAtr: 14, renkoBox: 1, renkoWicks: false, rangeMethod: 'traditional', rangeSize: 2, lineBreakCount: 3,
  kagiMethod: 'traditional', kagiAtr: 14, kagiReversal: 2, pnfMethod: 'traditional', pnfAtr: 14, pnfBox: 1, pnfReversal: 3, pnfSource: 'close',
};
const rising = Array.from({ length: 10 }, (_, i) => bar(1000 + i * 60, 100 + i, 101 + i, 99 + i, 100.5 + i));

describe('chart types', () => {
  it('High-low draws a body from the low to the high, coloured by the previous close, with no close tick', () => {
    const b = [bar(0, 10, 12, 9, 11), bar(60, 11, 13, 10, 10.5)];
    const d = buildSeries('highlow', b, style, params).data as { open: number; close: number; color: string; wickColor: string }[];
    expect(d[0]).toMatchObject({ open: 9, close: 12 });
    expect(d[1]!.color).toBe(style.down);
    expect(d[1]!.wickColor).toBe('rgba(0,0,0,0)');
  });
  it('colours bars by the previous close when asked', () => {
    const b = [bar(0, 10, 12, 9, 11), bar(60, 9, 12, 8, 11.5)];
    const d = buildSeries('candles', b, { ...style, colorByPrevClose: true }, params).data as { color: string }[];
    expect(d[1]!.color).toBe(style.up);
  });
  it('Renko needs two boxes to reverse and gives each brick a share of its bar\'s volume', () => {
    const b = [bar(0, 100, 100, 100, 100), bar(60, 100, 103, 100, 103, 30), bar(120, 103, 103, 101.5, 101.5), bar(180, 101.5, 101.5, 100, 100, 20)];
    const bricks = renkoBuild(b, 1, false);
    expect(bricks.filter((x) => x.c > x.o).length).toBe(3);
    // 103 → 101.5 is not two boxes: no reversal; 103 → 100 is: two falling bricks, from 102.
    const falling = bricks.filter((x) => x.c < x.o);
    expect(falling.length).toBe(2);
    expect(falling[0]!.o).toBe(102);
    expect(falling[0]!.t).toBe(b[3]!.t);
    const vols = shareVolume(bricks, b);
    expect(vols.slice(0, 3).every((x) => x.v === 10)).toBe(true);
    expect(new Set(bricks.map((x) => x.t)).size).toBe(bricks.length);
  });
  it('Point & figure builds X and O columns dated by the bar that began them', () => {
    const closes = [100, 101, 102, 103, 104, 100.5, 99, 98, 101.5, 103];
    const b = closes.map((c, i) => bar(1000 + i * 60, c, c, c, c));
    const cols = pnfBuild(b, 1, 3, 'close');
    expect(cols.map((c) => c.dir)).toEqual([1, -1, 1]);
    expect(cols[0]).toMatchObject({ lo: 100, hi: 104 });
    expect(cols[1]!.time).toBe(b[5]!.t);
    expect(cols.every((c, i) => i === 0 || c.time > cols[i - 1]!.time)).toBe(true);
  });
  it('Kagi turns thick above the last shoulder and thin below the last waist, at that price', () => {
    const pts = [{ time: 1, value: 10 }, { time: 2, value: 14 }, { time: 3, value: 11 }, { time: 4, value: 16 }, { time: 5, value: 9 }];
    const runs = kagiRuns(pts);
    expect(runs[0]!.thick).toBe(true);
    expect(runs.some((r) => !r.thick)).toBe(true);
    // The flip to thin happens at the waist (11), part-way down the last column.
    const thin = runs.find((r) => !r.thick)!;
    expect(thin.path[0]!.v).toBe(11);
  });
  it('indicators and volume run on the bricks of Renko', () => {
    const built = buildSeries('renko', rising, style, params);
    expect(built.candles.length).toBeGreaterThan(5);
    expect(built.candles.every((c) => c.v >= 0)).toBe(true);
  });
  it('finds a bar by its time', () => {
    expect(indexAt(rising, rising[4]!.t)).toBe(4);
    expect(indexAt(rising, 5)).toBe(-1);
    expect(barIndexAt(rising, rising[4]!.t + 30)).toBe(4);
  });
});

describe('time scale look', () => {
  it('writes dates in each format, with the day of week when asked', () => {
    const t = Date.UTC(1997, 8, 29, 13, 5) / 1000;
    expect(dateText(t, 'UTC', { dateFormat: 'yyyy-MM-dd', dayOfWeek: false })).toBe('1997-09-29');
    expect(dateText(t, 'UTC', { dateFormat: 'dd/MM/yyyy', dayOfWeek: false })).toBe('29/09/1997');
    expect(dateText(t, 'UTC', { dayOfWeek: true })).toBe("Mon 29 Sep '97");
    expect(clockText(t, 'UTC')).toBe('13:05');
    expect(clockText(t, 'UTC', true)).toMatch(/01:05\s?PM/);
    expect(barTimeText(t, 'Asia/Tehran', '60', { dayOfWeek: false })).toContain('16:35');
    expect(barTimeText(t, 'exchange', '1D', { dayOfWeek: false })).toBe("29 Sep '97");
  });
});

describe('chart settings', () => {
  beforeEach(() => { localStorage.clear(); useChartSettings.setState({ ...DEFAULT_SETTINGS, templates: [] }); });
  it('reads an older build\'s price line, previous close and grid', () => {
    const m = migrate({ priceLine: false, prevClose: true, grid: 'horizontal', unknown: 1, up: 5 });
    expect(m).toMatchObject({ lastPrice: 'value', prevDayClose: 'value-line', vertGrid: false, horzGrid: true });
    expect('unknown' in m).toBe(false);
    expect('up' in m).toBe(false);
  });
  it('saves, applies and deletes templates', () => {
    const s = useChartSettings.getState();
    s.set({ up: '#123456' });
    s.saveTemplate('mine');
    s.set({ up: '#654321' });
    useChartSettings.getState().applyTemplate('mine');
    expect(useChartSettings.getState().up).toBe('#123456');
    useChartSettings.getState().deleteTemplate('mine');
    expect(useChartSettings.getState().templates).toEqual([]);
    expect(JSON.parse(localStorage.getItem('pc.chartsettings.v1')!).up).toBe('#123456');
  });
  it('turns a line\'s label and its line on and off separately', () => {
    expect(withLabel('value-line', false)).toBe('line');
    expect(withLabel('hidden', true)).toBe('value');
    expect(withLine('value', true)).toBe('value-line');
    expect(withLine('line', false)).toBe('hidden');
  });
});

describe('overlays', () => {
  it('works out the visible range\'s high, low and average close', () => {
    const s = visibleStats(rising, 2, 4)!;
    expect(s.high).toBe(105);
    expect(s.low).toBe(101);
    expect(s.avg).toBeCloseTo(103.5);
    expect(s.hiT).toBe(rising[4]!.t);
  });
  it('filters events by importance and currency', () => {
    expect(importanceOk('medium', 'high')).toBe(false);
    expect(importanceOk('medium', 'medium')).toBe(true);
    expect(importanceOk('low', 'all')).toBe(true);
    expect(currenciesOf('EURJPY')).toEqual(['EUR', 'JPY']);
    expect(currenciesOf('XAUUSD')).toEqual(['USD', 'USD']);
    expect(currenciesOf('BTCUSDT')).toEqual(['USD']);
  });
  it('splits the volume profile into buying and selling volume, with the value area asked for', () => {
    const p = computeProfile([{ o: 1, c: 2, l: 1, h: 2, v: 10 }, { o: 2, c: 1, l: 1, h: 2, v: 30 }], 4, 0.5)!;
    const up = p.reduce((n, b) => n + b.up, 0); const down = p.reduce((n, b) => n + b.down, 0);
    expect(up).toBeCloseTo(10);
    expect(down).toBeCloseTo(30);
  });
});

describe('studies and compares', () => {
  beforeEach(() => { localStorage.clear(); });
  it('duplicates a study and moves a pane past the overlays', () => {
    useStudies.setState({ list: [{ uid: 'vol', key: VOLUME, inputs: {} }, { uid: 'r', key: 'rsi', inputs: { len: 14 } }, { uid: 'e', key: 'ema', inputs: {} }, { uid: 'm', key: 'macd', inputs: {} }] });
    useStudies.getState().duplicate('r');
    const list = useStudies.getState().list;
    expect(list.length).toBe(5);
    expect(list[2]!.key).toBe('rsi');
    expect(list[2]!.uid).not.toBe('r');
    useStudies.getState().move('m', -1);
    const keys = useStudies.getState().list.map((s) => s.uid);
    expect(keys.indexOf('m')).toBeLessThan(keys.indexOf(list[2]!.uid));
  });
  it('keeps compared symbols, their colours and their eyes', () => {
    useCompare.setState({ symbols: [], hidden: {}, colors: {} });
    useCompare.getState().add('ETHUSDT');
    useCompare.getState().toggle('ETHUSDT');
    useCompare.getState().setColor('ETHUSDT', '#00ff00');
    const saved = JSON.parse(localStorage.getItem('pc.compare.v1')!);
    expect(saved).toEqual({ symbols: ['ETHUSDT'], hidden: { ETHUSDT: true }, colors: { ETHUSDT: '#00ff00' } });
    useCompare.getState().remove('ETHUSDT');
    expect(useCompare.getState().colors).toEqual({});
  });
});
