import { describe, expect, it, beforeEach } from 'vitest';
import { MINIMUM_TRADES, myWeek, weekStart, weekTrades } from '../state/week';
import { roundFor, judge, record, rightPercent, localEpochDay, CONTEXT_BARS, FORWARD_BARS } from '../state/duel';
import { halfQuantity } from '../trade/math';
import { impactOfScore, parseImpact, parseSentiment, type CalendarEvent } from '../data/intel';
import { migrateNotes, useWatchlist } from '../state/watchlist';
import { listNotesOf } from '../data/watchlistSync';
import { mergeJournal } from '../pages/ProfilePage';
import { calendarMatches, icsFor } from '../data/calendarExtras';
import { intervalGroup, intervalLabel, intervalLongLabel, intervalSeconds, isSubMinute, parseIntervalId, parseIntervalInput } from '../data/intervals';
import { canDraw, planFor } from '../data/candles';
import { foldTicks } from '../data/ticks';
import { barColorData } from '../chart/scriptRenderer';
import { usePaper } from '../state/paper';
import { sparkPoints } from '../data/sparklines';
import type { Candle } from '../data/types';

const bar = (t: number, c: number): Candle => ({ t, o: c, h: c, l: c, c, v: 1 });

describe('«هفته‌ی من»', () => {
  it('opens on Saturday in Tehran and on Monday elsewhere, at midnight', () => {
    const wed = new Date(2026, 9, 28, 15, 30).getTime(); // a Wednesday
    const sat = new Date(weekStart(wed, 'Asia/Tehran'));
    expect(sat.getDay()).toBe(6);
    expect([sat.getHours(), sat.getMinutes()]).toEqual([0, 0]);
    expect(sat.getDate()).toBe(24);
    expect(new Date(weekStart(wed, 'Europe/Berlin')).getDay()).toBe(1);
    // Saturday itself is the first day of its own week.
    const satNoon = new Date(2026, 9, 31, 12).getTime();
    expect(new Date(weekStart(satNoon, 'Asia/Tehran')).getDate()).toBe(31);
  });

  it('withholds the win rate under five closed trades and counts only the week', () => {
    const now = new Date(2026, 9, 28, 12).getTime();
    const day = 86_400_000;
    const four = Array.from({ length: 4 }, (_, i) => ({ closedAt: now - i * 3600e3, won: i % 2 === 0 }));
    const w = myWeek({ now, zone: 'Asia/Tehran', trades: [...four, { closedAt: now - 10 * day, won: true }], alertFiredAt: [now, now - 20 * day], alertsArmed: 3, practiceFinishedAt: [now - 3600e3] });
    expect(w.trades).toBe(4);
    expect(w.won).toBe(2);
    expect(w.winPercent).toBeNull();
    expect(w.alertsFired).toBe(1);
    expect(w.alertsArmed).toBe(3);
    expect(w.practiceSessions).toBe(1);
    const five = myWeek({ now, zone: 'Asia/Tehran', trades: [...four, { closedAt: now, won: true }] });
    expect(MINIMUM_TRADES).toBe(5);
    expect(five.winPercent).toBe(60);
    expect(myWeek({ now, zone: 'Asia/Tehran', alertsArmed: 2 }).empty).toBe(true);
  });

  it('counts a paper trade copied into the journal once, and skips open or break-even rows', () => {
    const t = weekTrades(
      [{ id: 'paper-p1', pnl: 5, createdAt: 1 }, { id: 'j1', pnl: null, createdAt: 2 }, { id: 'j2', pnl: 0, createdAt: 3 }, { id: 'j3', pnl: -2, createdAt: 4 }],
      [{ id: 'p1', pnl: 5, closedAt: 1 }, { id: 'p2', pnl: 3, closedAt: 5 }],
    );
    expect(t).toEqual([{ closedAt: 1, won: true }, { closedAt: 4, won: false }, { closedAt: 5, won: true }]);
  });
});

describe('«دوئل با گذشته»', () => {
  const symbols = ['BTCUSDT', 'ETHUSDT', 'XAUUSD'];
  it('picks the same round as the Kotlin arithmetic, the same all day', () => {
    // Worked by hand in 64-bit wrapping arithmetic (Duel.roundFor): day 20000, 1000 bars.
    expect(roundFor(20_000, symbols, 1_000)).toEqual({ symbol: 'ETHUSDT', atBar: 438, resolveBar: 458, epochDay: 20_000 });
    expect(roundFor(20_000, symbols, 1_000)).toEqual(roundFor(20_000, symbols, 1_000));
    const days = new Set(Array.from({ length: 40 }, (_, i) => JSON.stringify(roundFor(20_000 + i, symbols, 1_000))));
    expect(days.size).toBeGreaterThan(1);
  });
  it('refuses without symbols or without room for 120 bars behind and 20 in front', () => {
    expect(roundFor(20_000, [], 1_000)).toBeNull();
    expect(roundFor(20_000, symbols, CONTEXT_BARS + FORWARD_BARS)).toBeNull();
    for (let n = CONTEXT_BARS + FORWARD_BARS + 1; n < CONTEXT_BARS + FORWARD_BARS + 40; n++) {
      for (let d = 20_000; d < 20_020; d++) { const r = roundFor(d, symbols, n)!; expect(r.resolveBar).toBeLessThan(n); expect(r.atBar).toBeGreaterThanOrEqual(CONTEXT_BARS); }
    }
  });
  it('judges a move under half a percent as too close, and keeps no rate under five judged rounds', () => {
    const bars = Array.from({ length: 30 }, (_, i) => bar(i, 100));
    const round = { symbol: 'X', atBar: 5, resolveBar: 25, epochDay: 1 };
    bars[25] = bar(25, 100.4);
    expect(judge(bars, round, 'up')?.verdict).toBe('too_close');
    bars[25] = bar(25, 101);
    expect(judge(bars, round, 'up')?.verdict).toBe('right');
    expect(judge(bars, round, 'down')?.verdict).toBe('wrong');
    expect(judge(bars.slice(0, 20), round, 'up')).toBeNull();
    let r = { played: 0, right: 0, tooClose: 0 };
    for (let i = 0; i < 5; i++) r = record(r, { call: 'up', verdict: i === 0 ? 'too_close' : 'right', movePercent: 1 });
    expect(rightPercent(r)).toBeNull();
    r = record(r, { call: 'up', verdict: 'wrong', movePercent: -1 });
    expect(rightPercent(r)).toBe(80);
    expect(localEpochDay(new Date(1970, 0, 2, 23).getTime())).toBe(1);
  });
});

describe('close half a live position', () => {
  it('halves and rounds down to the venue step', () => {
    expect(halfQuantity(0.01, 80_000, 'tradeyar')).toBe(0.005);
    expect(halfQuantity(0.015, 80_000, 'tradeyar')).toBe(0.0075);
    expect(halfQuantity(3, 2.5, 'tradeyar')).toBe(1); // whole units at this price
    expect(halfQuantity(0.01, 4_100, 'coineprofx')).toBeNull();
    expect(halfQuantity(0.3, 4_100, 'coineprofx')).toBe(0.15);
  });
});

describe('news classification', () => {
  it('reads the feed’s words and TradeYar’s score, never guesses', () => {
    expect(parseImpact('high')).toBe('high');
    expect(parseImpact('متوسط')).toBe('medium');
    expect(parseImpact('?')).toBeUndefined();
    expect(parseSentiment('positive')).toBe('bullish');
    expect(parseSentiment(null)).toBeUndefined();
    expect(impactOfScore(8)).toBe('high');
    expect(impactOfScore(4)).toBe('medium');
    expect(impactOfScore(1)).toBe('low');
    expect(impactOfScore(undefined)).toBeUndefined();
  });
});

describe('watchlist notes per list', () => {
  beforeEach(() => localStorage.clear());
  it('gives old notes to every list holding the symbol', () => {
    const lists = [{ id: 'a', name: 'A', symbols: ['BTCUSDT'] }, { id: 'b', name: 'B', symbols: ['BTCUSDT', 'EURUSD'] }];
    expect(migrateNotes(lists, 'a', { BTCUSDT: 'x', XAUUSD: 'y', EURUSD: 'z' })).toEqual({ a: { BTCUSDT: 'x', XAUUSD: 'y' }, b: { BTCUSDT: 'x', EURUSD: 'z' } });
  });
  it('keeps a note in its own list only', () => {
    const st = useWatchlist.getState();
    st.setLists([{ id: 'a', name: 'A', symbols: ['BTCUSDT'] }, { id: 'b', name: 'B', symbols: ['BTCUSDT'] }]);
    st.setActive('a');
    useWatchlist.getState().note('BTCUSDT', 'hold');
    expect(useWatchlist.getState().notes.BTCUSDT).toBe('hold');
    useWatchlist.getState().setActive('b');
    expect(useWatchlist.getState().notes.BTCUSDT).toBeUndefined();
    expect(useWatchlist.getState().listNotes.a?.BTCUSDT).toBe('hold');
    expect(listNotesOf({ lists: [{ id: 'a', symbols: [], web_notes: { X: 'n' } }, { id: 'c', symbols: [] }] } as never)).toEqual({ a: { X: 'n' } });
  });
});

describe('archive restore', () => {
  it('merges the journal by id instead of overwriting it', () => {
    const mine = JSON.stringify([{ id: '1', symbol: 'A', createdAt: 2 }]);
    const theirs = JSON.stringify([{ id: '1', symbol: 'OLD', createdAt: 2 }, { id: '2', symbol: 'B', createdAt: 3 }]);
    expect(JSON.parse(mergeJournal(mine, theirs)).map((e: { id: string; symbol: string }) => `${e.id}${e.symbol}`)).toEqual(['2B', '1A']);
    expect(JSON.parse(mergeJournal(null, theirs))).toHaveLength(2);
  });
});

describe('calendar', () => {
  const e: CalendarEvent = { title: 'CPI m/m', currency: 'USD', country: 'US', time: Date.UTC(2026, 10, 3, 12, 30), impact: 'high', forecast: '', previous: '', actual: '' };
  it('finds a release by its country', () => {
    expect(calendarMatches(e, 'آمریکا')).toBe(true);
    expect(calendarMatches(e, 'united states')).toBe(true);
    expect(calendarMatches(e, 'usd')).toBe(true);
    expect(calendarMatches(e, 'japan')).toBe(false);
  });
  it('writes a half-hour iCalendar event', () => {
    const ics = icsFor(e, Date.UTC(2026, 10, 1));
    expect(ics).toContain('DTSTART:20261103T123000Z');
    expect(ics).toContain('DTEND:20261103T130000Z');
    expect(ics).toContain('SUMMARY:CPI m/m');
    expect(ics.startsWith('BEGIN:VCALENDAR')).toBe(true);
  });
});

describe('seconds and tick intervals', () => {
  it('parses, labels and groups them', () => {
    expect(parseIntervalId('10S')).toEqual({ n: 10, unit: 'S' });
    expect(parseIntervalId('7S')).toBeNull();
    expect(parseIntervalInput('100t')).toBe('100T');
    expect(parseIntervalInput('5s')).toBe('5S');
    expect(intervalLabel('100T')).toBe('100T');
    expect(intervalSeconds('15S')).toBe(15);
    expect(intervalGroup('1T')).toBe('ticks');
    expect(intervalGroup('30S')).toBe('seconds');
    expect(intervalLongLabel('10S', 'fa')).toBe('۱۰ ثانیه');
    expect(intervalLongLabel('100T', 'en')).toBe('100 ticks');
    expect(isSubMinute('45')).toBe(false);
    expect(planFor('BTCUSDT', '10S')).toBeNull();
    expect(canDraw('XAUUSD', '100T')).toBe(true);
    expect(canDraw('BTCUSDT/ETHUSDT', '10S')).toBe(false);
  });
  it('folds trades into bars of N, in order even inside one second', () => {
    const ticks = [1000, 1100, 1200, 1300, 1400].map((tMs, i) => ({ tMs, price: 10 + i, volume: 1 }));
    const { bars, filled } = foldTicks(ticks, 2);
    expect(bars.map((b) => [b.t, b.o, b.h, b.l, b.c, b.v])).toEqual([[1, 10, 11, 10, 11, 2], [2, 12, 13, 12, 13, 2], [3, 14, 14, 14, 14, 1]]);
    expect(filled).toBe(1);
  });
});

describe('NamaScript barcolor', () => {
  it('recolours the coloured bars, the last colour winning', () => {
    const bars = [bar(1, 1), bar(2, 2), bar(3, 3)];
    const d = barColorData(bars, [{ color: 'red', bars: [1, 2] }, { color: 'blue', bars: [2] }]);
    expect(d.map((x) => [x.time, x.color])).toEqual([[1, 'red'], [2, 'blue']]);
    expect(barColorData(bars, [{ color: 'green', all: true }])).toHaveLength(3);
  });
});

describe('paper time in force', () => {
  beforeEach(() => usePaper.getState().reset());
  it('keeps GTC resting, cancels an IOC the market is not at, fills a marketable FOK at no worse than its price', () => {
    const p = usePaper.getState();
    expect(p.place({ symbol: 'BTCUSDT', side: 'long', type: 'limit', qty: 0.01, limitPrice: 90_000, tif: 'gtc' }, 100_000)).toBeTruthy();
    expect(usePaper.getState().orders[0]?.tif).toBe('gtc');
    expect(usePaper.getState().place({ symbol: 'BTCUSDT', side: 'long', type: 'limit', qty: 0.01, limitPrice: 90_000, tif: 'ioc' }, 100_000)).toBeNull();
    expect(usePaper.getState().lastError).toBe('immediate');
    expect(usePaper.getState().orders).toHaveLength(1);
    expect(usePaper.getState().place({ symbol: 'BTCUSDT', side: 'long', type: 'limit', qty: 0.01, limitPrice: 100_010, tif: 'fok' }, 100_000)).toBeTruthy();
    const pos = usePaper.getState().positions[0]!;
    expect(pos.entry).toBeLessThanOrEqual(100_010);
  });
});

describe('watchlist trend column', () => {
  it('draws a line only from two points', () => {
    expect(sparkPoints([1], 10, 10)).toBeNull();
    expect(sparkPoints([1, 2], 10, 10)).toBe('0.0,9.0 10.0,1.0');
  });
});
