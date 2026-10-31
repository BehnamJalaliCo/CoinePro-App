// The chart engine and its settings (area A2): columns, period separators, prior-period levels,
// the YTD range, the market state, the tall-plot fill, the larger layouts, the MA offset and the
// six built-in indicator templates.
import { beforeEach, describe, expect, it } from 'vitest';
import { STUDIES, autoAnchor } from '../engine/indicators_ext_c.js';
import { columnsRange, buildSeries, COLUMN_ALPHA } from '../chart/chartTypes';
import { periodStarts, separatorPeriod } from '../chart/SessionBreaks';
import { rangeInterval, RANGES } from '../shell/TimeBar';
import { marketState } from '../data/sessions';
import { tallMargins, MIN_TALL_FILL } from '../chart/tallFill';
import { PRESETS, CELL_AREAS, MAX_CELLS, presetOf } from '../state/grid';
import { breakAtGaps, computeStudy, plotShift, shiftValues, columns } from '../chart/studies';
import { BUILT_IN_TEMPLATES, applyBuiltInTemplate, templateSummary } from '../chart/builtinTemplates';
import { useStudies, VOLUME } from '../state/studies';
import { menuFor } from '../chart/studyMeta';
import { DEFAULT_SETTINGS } from '../state/chartSettings';
import { entryOf } from '../chart/indicatorCatalog';
import type { Candle } from '../data/types';

const DAY = 86400;
const bars = (n: number, step = 3600, start = Date.UTC(2026, 0, 5) / 1000): Candle[] => Array.from({ length: n }, (_, i) => {
  const c = 100 + Math.sin(i / 5) * 5 + i * 0.1;
  return { t: start + i * step, o: c - 0.5, h: c + 1, l: c - 1, c, v: 100 + i };
});

describe('columns', () => {
  it('fits the scale to the visible closes, never down to zero', () => {
    const b = bars(10);
    const r = columnsRange(b, 2, 6)!;
    expect(r.minValue).toBe(Math.min(...b.slice(2, 7).map((x) => x.c)));
    expect(r.maxValue).toBe(Math.max(...b.slice(2, 7).map((x) => x.c)));
    expect(r.minValue).toBeGreaterThan(50);
    expect(columnsRange([], 0, 5)).toBeNull();
  });
  it('draws the body at 55 % of the colour', () => {
    const style = { up: '#089981', down: '#F23645', line: '#2962FF', bg: '#FFFFFF' } as never;
    const d = buildSeries('columns', bars(3), style, {} as never).data as { color: string }[];
    expect(COLUMN_ALPHA).toBe(0.55);
    expect(d.every((x) => /rgba\(.*0\.55\)/.test(x.color))).toBe(true);
  });
});

describe('period separators', () => {
  it('day on intraday, month on daily, year on weekly and longer', () => {
    expect(separatorPeriod(60)).toBe('day');
    expect(separatorPeriod(4 * 3600)).toBe('day');
    expect(separatorPeriod(DAY)).toBe('month');
    expect(separatorPeriod(7 * DAY)).toBe('year');
    expect(separatorPeriod(30 * DAY)).toBe('year');
    expect(separatorPeriod(0)).toBeNull();
  });
  it('a rule at the first bar of each new month on daily bars', () => {
    const t = bars(70, DAY, Date.UTC(2026, 0, 1) / 1000).map((b) => b.t);
    const starts = periodStarts(t, 'month', () => '');
    expect(starts).toEqual([Date.UTC(2026, 1, 1) / 1000, Date.UTC(2026, 2, 1) / 1000]);
    expect(periodStarts(t, 'year', () => '')).toEqual([]);
  });
});

describe('prior-period levels', () => {
  it('auto anchor: day on intraday, week on daily, month on weekly+', () => {
    expect(autoAnchor(bars(60, 3600).map((b) => b.t))).toBe('D');
    expect(autoAnchor(bars(60, DAY).map((b) => b.t))).toBe('W');
    expect(autoAnchor(bars(60, 7 * DAY).map((b) => b.t))).toBe('M');
    expect(STUDIES.prevHLC!.inputs.anchor).toBe('auto');
    expect(menuFor('prevHLC', 'anchor')!.map((o) => o.value)).toEqual(['auto', 'D', 'W', 'M']);
    expect(menuFor('cvd', 'anchor')!.map((o) => o.value)).toEqual(['D', 'W', 'M']);
  });
  it('yesterday’s high, low and close and today’s open, broken at each boundary, labelled PH PL PC O', () => {
    const b = bars(72, 3600); // three UTC days of hourly bars
    const out = STUDIES.prevHLC!.calc(columns(b), { anchor: 'auto' });
    const [H, L, C, O] = out.lines!;
    expect(out.lines!.map((l) => l.title)).toEqual(['PH', 'PL', 'PC', 'O']);
    // Day one has no previous day.
    expect(H!.data.slice(0, 24).every((v) => v == null)).toBe(true);
    // The first bar of a day is the gap; the rest of day two carries day one's levels.
    expect(H!.data[24]).toBeNull();
    const d1 = b.slice(0, 24);
    expect(H!.data[25]).toBe(Math.max(...d1.map((x) => x.h)));
    expect(L!.data[25]).toBe(Math.min(...d1.map((x) => x.l)));
    expect(C!.data[25]).toBe(d1[23]!.c);
    expect(O!.data[25]).toBe(b[24]!.o);
    expect(O!.data[50]).toBe(b[48]!.o);
  });
});

describe('lines broken at their gaps', () => {
  it('clears only the segment that would bridge a gap', () => {
    const d = [{ time: 1, value: 1 }, { time: 2, value: 2 }, { time: 3 }, { time: 4, value: 4 }, { time: 5, value: 5 }, { time: 6 }] as never[];
    const out = breakAtGaps(d) as { color?: string }[];
    expect(out.map((x) => x.color ?? '')).toEqual(['', 'rgba(0,0,0,0)', '', '', '', '']);
  });
});

describe('YTD range', () => {
  const ytd = RANGES.find((r) => r.id === 'YTD')!;
  it('4-hour bars through day 60 of the year, daily after', () => {
    expect(rangeInterval(ytd, Date.UTC(2026, 0, 10) / 1000)).toBe('240');
    expect(rangeInterval(ytd, Date.UTC(2026, 2, 1) / 1000)).toBe('240'); // day 60
    expect(rangeInterval(ytd, Date.UTC(2026, 2, 2) / 1000)).toBe('1D'); // day 61
    expect(rangeInterval(ytd, Date.UTC(2026, 9, 31) / 1000)).toBe('1D');
    expect(rangeInterval(RANGES.find((r) => r.id === '1Y')!, Date.UTC(2026, 0, 10) / 1000)).toBe('1D');
  });
});

describe('market state', () => {
  it('open, closed for the rollover hour, weekend', () => {
    expect(marketState('crypto', new Date(Date.UTC(2026, 9, 17, 12)))).toBe('open');
    expect(marketState('forex', new Date(Date.UTC(2026, 9, 17, 12)))).toBe('weekend'); // Saturday
    expect(marketState('forex', new Date(Date.UTC(2026, 9, 16, 22, 30)))).toBe('weekend'); // Friday after close
    expect(marketState('metals', new Date(Date.UTC(2026, 9, 14, 21, 15)))).toBe('closed');
    expect(marketState('forex', new Date(Date.UTC(2026, 9, 14, 12)))).toBe('open');
  });
});

describe('tall-plot fill', () => {
  it('leaves a wide pane alone', () => {
    expect(tallMargins(0.1, 0.08, 1200, 600)).toEqual({ top: 0.1, bottom: 0.08 });
  });
  it('caps the bars at width/height of a tall pane, never under half, air in the margins’ proportion', () => {
    const m = tallMargins(0.1, 0.08, 400, 600);
    expect(1 - m.top - m.bottom).toBeCloseTo(400 / 600, 6);
    expect(m.top / m.bottom).toBeCloseTo(0.1 / 0.08, 6);
    const thin = tallMargins(0.1, 0.08, 100, 1000);
    expect(1 - thin.top - thin.bottom).toBeCloseTo(MIN_TALL_FILL, 6);
  });
});

describe('layouts', () => {
  it('has 3+2, 4+2, 4+3 and 9, 10, 12, 14 and 16 charts', () => {
    const want: Record<string, number> = { '3+2': 5, '4+2': 6, '4+3': 7, '9': 9, '10': 10, '12': 12, '14': 14, '16': 16 };
    for (const [id, n] of Object.entries(want)) expect(presetOf(id).cells, id).toBe(n);
    expect(MAX_CELLS).toBe(16);
    expect(CELL_AREAS.length).toBe(16);
    // Three over two: the top row holds three charts, the bottom two.
    const rows = presetOf('3+2').areas.map((r) => new Set(r.split(' ')).size);
    expect(rows).toEqual([3, 2]);
    expect(presetOf('4+3').areas.map((r) => new Set(r.split(' ')).size)).toEqual([4, 3]);
    expect(PRESETS.filter((p) => p.cells === 16).length).toBe(1);
  });
});

describe('crosshair and settings defaults', () => {
  it('Large Dashed by default; trade ring and quote chip on', () => {
    expect(DEFAULT_SETTINGS.crossStyle).toBe('large-dashed');
    expect(DEFAULT_SETTINGS.tradeRing).toBe(true);
    expect(DEFAULT_SETTINGS.quoteChip).toBe(true);
  });
  it('the median is the Moving Median', () => {
    expect(entryOf('median')?.en).toBe('Moving Median');
  });
});

describe('moving average offset', () => {
  it('every moving average has an Offset input at 0', () => {
    for (const k of ['ma', 'ema', 'wma', 'hma', 'dema', 'tema', 'vwma', 'smma']) expect(STUDIES[k]!.inputs.shift, k).toBe(0);
  });
  it('moves the plot right by whole bars; the tail past the last bar is not drawn', () => {
    expect(shiftValues([1, 2, 3, 4], 2)).toEqual([null, null, 1, 2]);
    expect(shiftValues([1, 2, 3, 4], -1)).toEqual([2, 3, 4, null]);
    expect(plotShift('5')).toBe(5);
    expect(plotShift(999)).toBe(200);
    expect(plotShift(undefined)).toBe(0);
    const b = bars(60);
    const plain = computeStudy({ uid: 'e', key: 'ema', inputs: { period: 20, source: 'close', shift: 0 } }, b, new Map(), [])!.items[0]!.values;
    const moved = computeStudy({ uid: 'e5', key: 'ema', inputs: { period: 20, source: 'close', shift: 5 } }, b, new Map(), [])!.items[0]!.values;
    expect(moved[40]).toBe(plain[35]);
    expect(moved.length).toBe(plain.length);
  });
});

describe('built-in indicator templates', () => {
  beforeEach(() => useStudies.setState({ list: [{ uid: 'vol', key: VOLUME, inputs: {} }, { uid: 'x', key: 'bb', inputs: {} }] }));
  it('TradingView’s six, with the old Pro-Chart’s exact contents', () => {
    expect(BUILT_IN_TEMPLATES.map((t) => t.id)).toEqual(['tv_bw3lines', 'tv_displaced_ema', 'tv_ma_exp_ribbon', 'tv_oscillators', 'tv_swing', 'tv_volume_based']);
    expect(BUILT_IN_TEMPLATES.map((t) => t.en)).toEqual(["Bill Williams' 3 Lines", 'Displaced EMA', 'MA Exp Ribbon', 'Oscillators', 'Swing Trading', 'Volume Based']);
    expect(BUILT_IN_TEMPLATES.map((t) => t.studies.map((s) => s.key))).toEqual([['alligator'], ['ema'], ['maRibbon'], ['rsi', 'macd', 'stoch'], ['ema', 'ma', 'rsi'], ['vwap', 'obv', 'mfi', 'cmf']]);
    for (const t of BUILT_IN_TEMPLATES) for (const s of t.studies) expect(STUDIES[s.key], s.key).toBeTruthy();
    expect(templateSummary(BUILT_IN_TEMPLATES[1]!)).toBe('EMA 20 +5');
    expect(templateSummary(BUILT_IN_TEMPLATES[4]!)).toBe('EMA 20 · SMA 50 · RSI');
    for (const t of BUILT_IN_TEMPLATES) expect(t.fa).not.toMatch(/نمودار|هٔ/);
  });
  it('applying one replaces the indicators and keeps the volume', () => {
    expect(applyBuiltInTemplate('tv_displaced_ema')).toBe(true);
    const list = useStudies.getState().list;
    expect(list.map((s) => s.key)).toEqual([VOLUME, 'ema']);
    expect(list[1]!.inputs.period).toBe(20);
    expect(list[1]!.inputs.shift).toBe(5);
    applyBuiltInTemplate('tv_swing');
    expect(useStudies.getState().list.map((s) => `${s.key}:${s.inputs.period ?? ''}`)).toEqual([`${VOLUME}:`, 'ema:20', 'ma:50', 'rsi:14']);
  });
  it('hide all indicators keeps the volume shown', () => {
    useStudies.getState().setAllHidden(true);
    expect(useStudies.getState().list.find((s) => s.key === 'bb')!.hidden).toBe(true);
    expect(useStudies.getState().list.find((s) => s.key === VOLUME)!.hidden).toBeFalsy();
    useStudies.getState().setAllHidden(false);
    expect(useStudies.getState().list.find((s) => s.key === 'bb')!.hidden).toBe(false);
  });
});
