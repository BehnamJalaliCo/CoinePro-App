import { describe, expect, it } from 'vitest';
import {
  INTERVALS, intervalGroup, intervalLabel, intervalLongLabel, intervalSeconds, isStandardInterval, parseIntervalId, parseIntervalInput,
} from '../data/intervals';
import { bucketOf, canDraw, foldBars, intervalsFor, planFor } from '../data/candles';
import { liveFold, inRegularHours, regularSession } from '../data/feed';
import type { Candle } from '../data/types';

const bar = (t: number, o: number, h: number, l: number, c: number, v = 1): Candle => ({ t, o, h, l, c, v });

describe('interval ids', () => {
  it('parses TradingView notation', () => {
    expect(parseIntervalId('45')).toEqual({ n: 45, unit: 'm' });
    expect(parseIntervalId('3D')).toEqual({ n: 3, unit: 'D' });
    expect(parseIntervalId('2W')).toEqual({ n: 2, unit: 'W' });
    expect(parseIntervalId('12M')).toEqual({ n: 12, unit: 'M' });
    expect(parseIntervalId('0')).toBeNull();
    expect(parseIntervalId('4h')).toBeNull();
  });
  it('labels as the top bar writes them', () => {
    expect(['1', '45', '60', '90', '240', '720', '1D', '3D', '1W', '1M', '12M'].map(intervalLabel)).toEqual(['1m', '45m', '1h', '90m', '4h', '12h', '1D', '3D', '1W', '1M', '12M']);
  });
  it('names them in both languages, Persian digits in Persian', () => {
    expect(intervalLongLabel('45', 'en')).toBe('45 minutes');
    expect(intervalLongLabel('60', 'en')).toBe('1 hour');
    expect(intervalLongLabel('3M', 'en')).toBe('3 months');
    expect(intervalLongLabel('1D', 'en')).toBe('1 day');
    expect(intervalLongLabel('240', 'fa')).toBe('۴ ساعت');
    expect(intervalLongLabel('2W', 'fa')).toBe('۲ هفته');
  });
  it('knows each length in seconds', () => {
    expect(intervalSeconds('45')).toBe(2700);
    expect(intervalSeconds('3D')).toBe(3 * 86400);
    expect(intervalSeconds('2W')).toBe(2 * 604800);
  });
  it('groups the menu as TradingView does', () => {
    expect(intervalGroup('45')).toBe('minutes');
    expect(intervalGroup('180')).toBe('hours');
    expect(intervalGroup('90')).toBe('minutes');
    expect(intervalGroup('1W')).toBe('days');
    expect(INTERVALS.map((i) => i.id)).toEqual(expect.arrayContaining(['2', '10', '45', '180', '360', '480', '720', '3D', '3M', '6M', '12M']));
    expect(isStandardInterval('7')).toBe(false);
  });
  it.each([
    ['45', '45'], ['45m', '45'], ['M45', '45'], ['3h', '180'], ['H3', '180'], ['6H', '360'], ['3d', '3D'], ['D', '1D'],
    ['2w', '2W'], ['W', '1W'], ['3M', '3M'], ['MN', '1M'], ['MN3', '3M'], ['1mo', '1M'], ['1440', '1D'], ['10080', '1W'], ['1m', '1'],
  ])('reads what a reader types: %s → %s', (raw, id) => expect(parseIntervalInput(raw)).toBe(id));
  it('refuses nonsense', () => {
    for (const raw of ['', 'x', '4x', '0m', '-5', 'h']) expect(parseIntervalInput(raw)).toBeNull();
  });
});

describe('derived intervals', () => {
  it('folds from the largest native timeframe that divides the interval', () => {
    expect(planFor('BTCUSDT', '45')).toMatchObject({ tf: 'M15', fold: 'minutes', factor: 3 });
    expect(planFor('BTCUSDT', '10')).toMatchObject({ tf: 'M5', fold: 'minutes', factor: 2 });
    expect(planFor('BTCUSDT', '480')).toMatchObject({ tf: 'H4', fold: 'minutes', factor: 2 });
    expect(planFor('BTCUSDT', '360')).toMatchObject({ tf: 'H1', fold: 'minutes', factor: 6 });
    expect(planFor('BTCUSDT', '7')).toMatchObject({ tf: 'M1', fold: 'minutes', factor: 7 });
    expect(planFor('BTCUSDT', '3D')).toMatchObject({ tf: 'D1', fold: 'days', factor: 3 });
    expect(planFor('BTCUSDT', '2W')).toMatchObject({ tf: 'W1', fold: 'week' });
    expect(planFor('EURUSD', '2W')).toMatchObject({ tf: 'D1', fold: 'week' });
    expect(planFor('EURUSD', '3M')).toMatchObject({ tf: 'D1', fold: 'month' });
    expect(planFor('EURUSD', '240')).toMatchObject({ tf: 'H4', fold: 'none' });
  });
  it('a forex symbol has nothing under five minutes', () => {
    expect(canDraw('EURUSD', '2')).toBe(false);
    expect(canDraw('EURUSD', '10')).toBe(true);
    expect(canDraw('EURUSD', '7')).toBe(false);
    expect(intervalsFor('EURUSD')).toContain('45');
  });
  it('builds 45-minute bars on the clock from 15-minute ones', () => {
    const t0 = 1_700_001_000 - (1_700_001_000 % 2700);
    const bars = Array.from({ length: 7 }, (_, i) => bar(t0 + i * 900, 10 + i, 12 + i, 9 + i, 11 + i));
    const out = foldBars(bars, '45', 'minutes');
    expect(out.map((b) => b.t)).toEqual([t0, t0 + 2700, t0 + 5400]);
    expect(out[0]).toEqual({ t: t0, o: 10, h: 14, l: 9, c: 13, v: 3 });
  });
  it('an interval that does not divide a day starts over at midnight', () => {
    const day = Date.UTC(2026, 9, 14) / 1000;
    expect(bucketOf(day + 3600 * 23, '420', 'minutes')).toBe(day + 7 * 3600 * 3);
    expect(bucketOf(day + 86400 + 60, '420', 'minutes')).toBe(day + 86400);
  });
  it('quarters open on January, April, July and October', () => {
    const t = Date.UTC(2026, 4, 17) / 1000;
    expect(bucketOf(t, '3M', 'month')).toBe(Date.UTC(2026, 3, 1) / 1000);
    const days = Array.from({ length: 200 }, (_, i) => bar(Date.UTC(2026, 0, 1) / 1000 + i * 86400, 1, 2, 0.5, 1.5));
    expect(foldBars(days, '3M', 'month').map((m) => new Date(m.t * 1000).getUTCMonth())).toEqual([0, 3, 6]);
  });
  it('two weeks open on a Monday', () => {
    const t = Date.UTC(2026, 9, 21) / 1000;
    const b = bucketOf(t, '2W', 'week');
    expect(new Date(b * 1000).getUTCDay()).toBe(1);
    expect(t - b).toBeLessThan(14 * 86400);
  });
  it('live ticks find their bar by clock or calendar', () => {
    expect(liveFold('45')).toBe('minutes');
    expect(liveFold('3D')).toBe('days');
    expect(liveFold('1W')).toBe('week');
    expect(liveFold('3M')).toBe('month');
  });
});

describe('regular trading hours', () => {
  it('only the cash indices have them', () => {
    expect(regularSession('US500')?.tz).toBe('America/New_York');
    expect(regularSession('BTCUSDT')).toBeNull();
    expect(regularSession('EURUSD')).toBeNull();
  });
  it('keeps the New York cash session on intraday bars', () => {
    const open = Date.UTC(2026, 9, 14, 13, 30) / 1000; // 09:30 New York (EDT)
    expect(inRegularHours('US500', '15', open)).toBe(true);
    expect(inRegularHours('US500', '15', open - 900)).toBe(false);
    expect(inRegularHours('US500', '15', open + 6.5 * 3600)).toBe(false);
    expect(inRegularHours('US500', '1D', open - 900)).toBe(true);
    expect(inRegularHours('EURUSD', '15', open - 900)).toBe(true);
  });
});
