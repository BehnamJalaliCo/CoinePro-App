import { describe, expect, it } from 'vitest';
import {
  REACH, drawnAt, groupItems, laneCentre, laneGroups, laneReach, markImportance, markKind, nearest, newsBelongs, newsRelevance,
  notableBars, notableReading, ratioAt, symbolRelevance, IMPORTANCE_COLOR, type LaneItem, type LaneMark, type Placed,
} from '../chart/eventLane';
import { candleSource, sourceCaption, venueName } from '../data/venue';
import { DEFAULT_SETTINGS } from '../state/chartSettings';
import type { CalendarEvent, NewsItem } from '../data/intel';
import type { Candle } from '../data/types';

const quiet = (n: number, range = 1, at: Record<number, number> = {}): Candle[] =>
  Array.from({ length: n }, (_, i) => { const r = at[i] ?? range; return { t: 1000 + i * 60, o: 100, h: 100 + r / 2, l: 100 - r / 2, c: 100 + (i % 2 ? 0.1 : -0.1), v: 1 }; });
const ev = (t: number, impact: CalendarEvent['impact'] = 'high', title = 'CPI'): LaneItem => ({ kind: 'event', at: t, event: { title, currency: 'USD', time: t * 1000, impact, forecast: '', previous: '', actual: '' } });
const story = (t: number, title = 'Fed'): LaneItem => ({ kind: 'news', at: t, news: { id: String(t), market: 'forex', title, summary: '', source: 's', time: t * 1000, image: null, importance: 0 } });
const mark = (logical: number, items: LaneItem[]): LaneMark => ({ logical, barT: 1000 + logical * 60, items });
const news = (title: string, summary = '', market: NewsItem['market'] = 'forex') => ({ market, title, summary });

describe('notable bars (NotableBars.kt)', () => {
  it('marks a bar more than twice the 20 preceding bars, never at exactly twice', () => {
    expect(notableBars(quiet(60, 1, { 30: 2.5 }))).toEqual([30]);
    expect(notableBars(quiet(60, 1, { 30: 2.0 }))).toEqual([]);
    expect(notableBars(quiet(60, 1, { 30: 1.9 }))).toEqual([]);
  });
  it('marks nothing in the first window, nothing over a flat baseline, nothing on a short series', () => {
    expect(notableBars(quiet(60, 1, { 5: 30 }))).toEqual([]);
    expect(notableBars(quiet(60, 0, { 30: 5 }))).toEqual([]);
    expect(notableBars(quiet(20, 1, { 10: 9 }))).toEqual([]);
  });
  it('ratioAt agrees with the dot: the bar is never in its own baseline', () => {
    const b = quiet(60, 1, { 30: 3.1 });
    expect(ratioAt(b, 30)).toBeCloseTo(3.1, 5);
    expect(ratioAt(b, 10)).toBeNull();
    expect(ratioAt(quiet(60, 0), 30)).toBeNull();
  });
  it('the reading: ratio, open to close, and only what fell inside the bar, in order', () => {
    const b = quiet(60, 1, { 30: 3 });
    b[30] = { ...b[30]!, o: 100, c: 101.5 };
    const at = b[30]!.t;
    const r = notableReading(b, 30, [story(at + 59), ev(at + 10), ev(at + 60), ev(at - 1)])!;
    expect(r.ratio).toBeCloseTo(3, 5);
    expect(r.movePct).toBeCloseTo(1.5, 5);
    expect(r.up).toBe(true);
    expect(r.items.map((i) => i.at)).toEqual([at + 10, at + 59]);
    // The last bar is as wide as the gap before it.
    expect(notableReading(b, 59, [])!.to).toBe(b[59]!.t + 60);
  });
});

describe('event lane groups (CoineProChart.kt eventGroups)', () => {
  const isNews = (m: LaneMark) => markKind(m) === 'news';
  it('folds marks closer than a glyph plus 2px, measured from the first mark and from the drawn glyph', () => {
    const a = mark(0, [story(1000)]);
    const b = mark(1, [ev(1060)]);
    const c = mark(2, [ev(1120)]);
    const d = mark(3, [ev(1180)]);
    const placed: Placed<LaneMark>[] = [{ mark: a, x: 10 }, { mark: b, x: 20 }, { mark: c, x: 33 }, { mark: d, x: 60 }];
    const groups = laneGroups(placed, isNews, 500);
    // c is 23px from the first mark but 13px from where the glyph is drawn (b), so it joins.
    expect(groups.map((g) => g.map((p) => p.x))).toEqual([[10, 20, 33], [60]]);
    expect(drawnAt(groups[0]!, isNews).x).toBe(20);
    expect(REACH).toBe(14);
  });
  it('never leaves two drawn glyphs closer than the reach', () => {
    const placed = Array.from({ length: 40 }, (_, i) => ({ mark: mark(i, [i % 3 ? ev(1000 + i * 60, 'low') : story(1000 + i * 60)]), x: i * 5.5 }));
    const groups = laneGroups(placed, isNews, 1000);
    const xs = groups.map((g) => drawnAt(g, isNews).x);
    for (let i = 1; i < xs.length; i++) expect(xs[i]! - xs[i - 1]!).toBeGreaterThanOrEqual(REACH);
  });
  it('drops marks off the plot', () => {
    expect(laneGroups([{ mark: mark(0, [ev(1)]), x: -3 }, { mark: mark(1, [ev(2)]), x: 900 }], isNews, 800)).toEqual([]);
  });
  it('a group opens every event earliest first and counts its bars', () => {
    const g: Placed<LaneMark>[] = [{ mark: mark(4, [ev(1300), story(1290)]), x: 10 }, { mark: mark(5, [ev(1250, 'medium')]), x: 16 }];
    const { items, bars } = groupItems(g);
    expect(items.map((i) => i.at)).toEqual([1250, 1290, 1300]);
    expect(bars).toBe(2);
  });
  it('a bar with a release is a release mark coloured by its strongest release', () => {
    const m = mark(1, [story(1), ev(2, 'low'), ev(3, 'medium')]);
    expect(markKind(m)).toBe('event');
    expect(markImportance(m)).toBe('medium');
    expect(markKind(mark(1, [story(1)]))).toBe('news');
    expect(IMPORTANCE_COLOR.medium).toBe('#FB8C00');
    expect(IMPORTANCE_COLOR.low).toBe('#2962FF');
  });
  it('the lane sits above the dates and a click reaches the bolt above a release', () => {
    expect(laneCentre(400)).toBe(390);
    expect(laneReach()).toBeGreaterThanOrEqual(10 + REACH + 6);
  });
  it('nearest picks the closest within the radius', () => {
    expect(nearest([10, 30, 45], (x) => x, 40, 8)).toBe(45);
    expect(nearest([10, 30], (x) => x, 20, 8)).toBeNull();
  });
});

describe('news per symbol (ChartEventFeed tagging)', () => {
  it('reads the metal off the title first, then the summary; neither is macro', () => {
    expect(newsRelevance(news('تحلیل روزانه نقره', 'در مقایسه با طلا'))).toEqual(['silver']);
    expect(newsRelevance(news('Fed holds rates', 'gold rallies'))).toEqual(['gold']);
    expect(newsRelevance(news('تصمیم فدرال رزرو'))).toEqual(['gold', 'silver']);
    expect(newsRelevance(news('بیت‌کوین', '', 'crypto'))).toEqual(['crypto']);
  });
  it('a pair takes macro stories; a metal keeps its own analysis; a coin story stays on crypto', () => {
    expect(symbolRelevance('EURUSD')).toBeNull();
    expect(newsBelongs('EURUSD', news('تصمیم فدرال رزرو'))).toBe(true);
    expect(newsBelongs('EURUSD', news('تحلیل طلا'))).toBe(false);
    expect(newsBelongs('XAUUSD', news('تحلیل طلا'))).toBe(true);
    expect(newsBelongs('XAGUSD', news('تحلیل طلا'))).toBe(false);
    expect(newsBelongs('XAGUSD', news('تصمیم فدرال رزرو'))).toBe(true);
    expect(newsBelongs('BTCUSDT', news('بیت‌کوین', '', 'crypto'))).toBe(true);
    expect(newsBelongs('EURUSD', news('بیت‌کوین', '', 'crypto'))).toBe(false);
  });
  it('news marks are on by default, as on the phone', () => {
    expect(DEFAULT_SETTINGS.news).toBe(true);
  });
});

describe('price-source caption (ChartScreen chart_source)', () => {
  it('names the candles, adds the price only when it differs, and says nothing when unknown', () => {
    expect(sourceCaption('MetaTrader 5', 'Finnhub', true)).toBe('منبع کندل‌ها: ⁦MetaTrader 5⁩  ·  آخرین قیمت از ⁦Finnhub⁩');
    expect(sourceCaption('MetaTrader 5', 'Finnhub', false)).toBe('Candles from MetaTrader 5  ·  last price from Finnhub');
    expect(sourceCaption('LBank', 'LBank', false)).toBe('Candles from LBank');
    expect(sourceCaption('LBank', '', false)).toBe('Candles from LBank');
    expect(sourceCaption('', 'Finnhub', false)).toBe('');
  });
  it('venues: the feed spelling, and each backend’s candles', () => {
    expect(venueName('finnhub')).toBe('Finnhub');
    expect(venueName('LBANK-ws')).toBe('LBank');
    expect(venueName('mt5-bridge')).toBe('');
    expect(venueName(undefined)).toBe('');
    expect(candleSource('BTCUSDT')).toBe('LBank');
    expect(candleSource('XAUUSD')).toBe('MetaTrader 5');
    expect(candleSource('BTCUSDT/ETHUSDT')).toBe('LBank');
    expect(candleSource('BTCUSDT/XAUUSD')).toBe('');
  });
});
