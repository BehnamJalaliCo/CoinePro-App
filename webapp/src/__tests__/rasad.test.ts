import { describe, expect, it, beforeEach } from 'vitest';
import {
  FLAT, TRENDING, MINIMUM_BARS, biasLabel, chartReading, contradiction, decimalsFor, factsOfPaper, readChart, reviewTrade, strengthLabel,
  suggestAlert, supportResistance, trendLine, volatilityLabel,
} from '../chart/rasad';
import { briefOf } from '../state/pulse';
import { challengeFor, clockOf, markRevenge, recordResult, score, scoreReplay, streak, best, MINIMUM_CONTEXT_BARS, MINIMUM_FORWARD_BARS } from '../state/arena';
import { replayReport, exitOf, CONFIDENT_TRADES } from '../state/replayReport';
import { ratioSeries } from '../chart/compareRatio';
import { toJalali, jalaliDate, cell, candleCsv, csvFileName } from '../chart/candleCsv';
import { symbolNeighbours, symbolStep } from '../shell/symbolWheel';
import { forLayout, withGlobal, scopeFromId, noteGlobal, readGlobal } from '../state/drawingScope';
import { positionOrder } from '../chart/positionTrade';
import { isFoundingMember, foundingStamp } from '../data/founding';
import { useSession } from '../data/session';
import type { ReplayTrade } from '../state/replay';
import type { Candle } from '../data/types';

/** A deterministic walk: a rise, a range and a fall, with swings in it. */
function walk(n: number, drift = 0, seed = 7): Candle[] {
  let x = seed, p = 100;
  const out: Candle[] = [];
  for (let i = 0; i < n; i++) {
    x = (x * 9301 + 49297) % 233280;
    const r = x / 233280 - 0.5;
    const o = p, c = o * (1 + r * 0.02 + drift);
    out.push({ t: i * 3600, o, h: Math.max(o, c) * 1.003, l: Math.min(o, c) * 0.997, c, v: 10 });
    p = c;
  }
  return out;
}

describe('رصد — the coach', () => {
  it('says nothing under thirty bars and reads no trend under sixty', () => {
    expect(readChart(walk(MINIMUM_BARS - 1))).toEqual([]);
    expect(chartReading(walk(59))).toBeNull();
    const s = readChart(walk(45));
    expect(s.length).toBe(1);
    expect(s[0]).toMatch(/^روی /);
  });

  it('reads the trend, the levels and (only where known) the studies, in that order', () => {
    const bars = walk(300, 0.002);
    const fa = readChart(bars);
    expect(fa.length).toBe(2);
    expect(fa[0]).toMatch(/نوسان/);
    expect(fa[1]).toMatch(/^روی /);
    const en = readChart(bars, [{ state: 'bull' }, { state: 'bull' }, { state: 'neutral' }], null, true);
    expect(en[2]).toBe('Of 3 studies on the chart, 2 agree and all of them read Bullish.');
    const split = readChart(bars, [{ state: 'bull' }, { state: 'bear' }], null, false);
    expect(split[2]).toBe('از ۲ ابزار روی چارت، ۱ صعودی و ۱ نزولی می‌خوانند − با هم موافق نیستند.');
    expect(readChart(bars, [], null, true)[2]).toBe('Nothing on this chart has an opinion about direction right now.');
  });

  it('never writes a sentence that is flat and trending at once, in either language', () => {
    for (const en of [false, true]) {
      const dirs = en ? ['Bullish', 'Bearish', null] : ['صعودی', 'نزولی', null];
      const strengths = en ? ['Strong', 'Moderate', 'No trend'] : ['قوی', 'متوسط', 'بدون روند'];
      const swings = en ? ['High', 'Medium', 'Low'] : ['زیاد', 'متوسط', 'کم'];
      for (const trending of [true, false]) for (const d of dirs) for (const st of strengths) for (const sw of swings) {
        if (trending && st === strengths[2]) continue;
        expect(contradiction(trendLine(trending, d, st, sw, en))).toBeNull();
      }
    }
    expect(contradiction('بازار خنثی است و روند قوی خوانده می‌شود')).toEqual(['خنثی', 'روند قوی']);
    expect(FLAT.length && TRENDING.length).toBeTruthy();
    expect(trendLine(true, 'Bullish', 'Strong', 'Low', true)).toBe('The market is bullish and the trend reads strong, with low swing.');
  });

  it('labels its thresholds in both languages', () => {
    const r = { strength: 34, volatility: 0.8, bias: -0.002 };
    expect([strengthLabel(r), volatilityLabel(r), biasLabel(r)]).toEqual(['قوی', 'زیاد', 'نزولی']);
    expect([strengthLabel({ ...r, strength: 10 }, true), volatilityLabel({ ...r, volatility: 0.4 }, true), biasLabel({ ...r, bias: 0.0005 }, true)]).toEqual(['No trend', 'Medium', 'Neutral']);
    expect([decimalsFor(81000), decimalsFor(1.12), decimalsFor(0.08), decimalsFor(0.0001)]).toEqual([1, 2, 4, 6]);
  });

  it('clusters swings into levels and drops a level touched once', () => {
    const bars = walk(400);
    for (const l of supportResistance(bars)) expect(l.count).toBeGreaterThanOrEqual(2);
  });

  it('suggests an alert at a level 0.2–6% away, with the sentence on its side', () => {
    const bars = walk(400);
    const s = suggestAlert(bars);
    const last = bars[bars.length - 1]!.c;
    if (s) {
      const d = Math.abs(s.price - last) / last;
      expect(d).toBeGreaterThanOrEqual(0.002);
      expect(d).toBeLessThanOrEqual(0.06);
      expect(s.why).toMatch(s.above ? /برگشته/ : /نگه داشته/);
    }
    expect(suggestAlert(walk(10))).toBeNull();
  });

  it('reviews a trade: risk first, the plan, then the result — and never invents a missing stop', () => {
    expect(reviewTrade({ entry: 100, exit: 99, stop: 99, closedByStop: true, rMultiple: -1 }, true)).toEqual([
      'You entered at 100.00 risking 1.00 to the stop.',
      'The stop did its job and closed it − that is the plan working, not the plan failing.',
      'The result was -1.0 times what you risked.',
    ]);
    expect(reviewTrade({ entry: 100, exit: 103 }, true)[0]).toBe('You entered at 100.00 and it closed at 103.00.');
    const none = reviewTrade(factsOfPaper({ side: 'long', entry: 100, exit: 98, reason: 'manual' }));
    expect(none[0]).toBe('این یکی بدون حد ضرر باز شد، پس قیمتی نبود که ضرر را در آن تمام کند.');
    expect(none[1]).toMatch(/^تصمیم‌گرفتن/);
    const tp = factsOfPaper({ side: 'short', entry: 100, exit: 96, sl: 102, reason: 'tp' });
    expect(tp.rMultiple).toBeCloseTo(2);
    expect(reviewTrade(tp)[1]).toBe('به هدفی که گذاشته بودید رسید.');
    expect(reviewTrade(factsOfPaper({ side: 'long', entry: 100, exit: 90, sl: 95, reason: 'liq' })).length).toBe(2);
  });

  it('adds رصد’s first sentence about the mover to the daily brief, and not to a quiet one', () => {
    const bars = walk(200, 0.002);
    const b = briefOf([{ symbol: 'BTCUSDT', changePercent: 2.4 }], false, bars)!;
    expect(b.lines[0]).toBe('BTCUSDT 2.40٪ بالا رفت.');
    expect(b.lines[1]).toBe(readChart(bars)[0]);
    expect(briefOf([{ symbol: 'BTCUSDT', changePercent: 0.05 }], true, bars)!.lines.length).toBe(1);
    expect(briefOf([{ symbol: 'BTCUSDT', changePercent: 2.4 }], false, null)!.lines.length).toBe(1);
  });
});

describe('میدان — the daily challenge', () => {
  it('picks the same challenge from the date, with the Kotlin Long arithmetic', () => {
    expect(challengeFor(20_000, ['A', 'B', 'C', 'D'], 500)).toEqual({ symbol: 'C', startBar: 289, epochDay: 20_000 });
    expect(challengeFor(20_000, ['A'], 100)).toBeNull();
    expect(challengeFor(20_000, [], 500)).toBeNull();
    expect(challengeFor(20_000, ['A', 'B'], MINIMUM_CONTEXT_BARS + MINIMUM_FORWARD_BARS)!.startBar).toBe(MINIMUM_CONTEXT_BARS);
    expect(challengeFor(20_000, ['A', 'B', 'C', 'D'], 1e15)!.symbol).toBe('C');
  });

  it('scores discipline (60) and profit (40) apart, with a flat revenge penalty and a capped profit', () => {
    expect(score([]).trades).toBe(0);
    const s = score([{ hadStop: true, rMultiple: 1 }, { hadStop: false, rMultiple: null }]);
    expect([s.discipline, s.profit, s.withStop]).toEqual([30, 13, 1]);
    expect(score([{ hadStop: true, rMultiple: 10 }]).profit).toBe(40);
    expect(score([{ hadStop: true, rMultiple: 1, revenge: true }, { hadStop: true, rMultiple: 1 }]).discipline).toBe(45);
    expect(score([{ hadStop: true, rMultiple: -5 }]).profit).toBe(0);
  });

  it('marks a trade opened within 90 replay seconds of a losing close as revenge', () => {
    const t = [{ hadStop: true, rMultiple: -1 }, { hadStop: true, rMultiple: 1 }, { hadStop: true, rMultiple: 1 }];
    const m = markRevenge(t, [0, 100, 500], [100, 300, 600], [-1, 1, 1]);
    expect(m.map((x) => x.revenge)).toEqual([false, true, false]);
    expect(markRevenge(t, [0], [1], [1])).toBe(t);
  });

  it('reads the replay’s practice trades', () => {
    const tr = (p: Partial<ReplayTrade>): ReplayTrade => ({ id: 'x', side: 'long', entry: 100, at: 0, exit: 101, exitAt: 3600, qty: 1, qty0: 1, risk: 0, realized: 0, type: 'market', pnl: 1, ...p });
    const s = scoreReplay([tr({ risk: 2, sl: 98, r: 0.5 }), tr({ id: 'y', at: 3600, exitAt: 7200 }), tr({ id: 'z', exit: undefined })]);
    expect([s.trades, s.withStop, s.earnedR]).toEqual([2, 1, 0.5]);
  });

  it('keeps one row a day, counts the streak from today or yesterday, and finds the best', () => {
    let rows = recordResult([], { epochDay: 10, symbol: 'A', total: 40, discipline: 30, profit: 10 });
    rows = recordResult(rows, { epochDay: 11, symbol: 'A', total: 70, discipline: 60, profit: 10 });
    rows = recordResult(rows, { epochDay: 11, symbol: 'A', total: 50, discipline: 40, profit: 10 });
    expect(rows.length).toBe(2);
    expect(streak(rows, 11)).toBe(2);
    expect(streak(rows, 12)).toBe(2);
    expect(streak(rows, 13)).toBe(0);
    expect(best(rows)!.total).toBe(50);
    expect([clockOf(300), clockOf(59), clockOf(-3)]).toEqual(['5:00', '0:59', '0:00']);
  });
});

describe('replay report', () => {
  const tr = (how: ReplayTrade['how'], pnl: number): ReplayTrade => ({ id: String(Math.random()), side: 'long', entry: 100, at: 0, exit: 100 + pnl, exitAt: 1, qty: 1, qty0: 1, risk: 1, realized: 0, type: 'market', pnl, how });
  it('counts exits by stop, target, hand and session, and withholds the rates under thirty', () => {
    const r = replayReport([tr('sl', -1), tr('tp', 2), tr('manual', 1), tr('reverse', -1), tr('flatten', 0.5)], 100, 80);
    expect(r.exits).toEqual({ stop: 1, target: 1, manual: 2, session: 1 });
    expect([r.trades, r.wins, r.losses, r.bars]).toEqual([5, 3, 2, 80]);
    expect(r.winRate).toBeNull();
    expect(r.net).toBeCloseTo(1.5);
    const many = replayReport(Array.from({ length: CONFIDENT_TRADES }, (_, i) => tr('tp', i % 3 ? 1 : -1)), 100, 10);
    expect(many.winRate).toBeCloseTo(66.67, 1);
    expect(many.profitFactor).toBeCloseTo(2);
    expect(exitOf(tr(undefined, 0))).toBe('manual');
  });
});

describe('chart extras', () => {
  it('draws a comparison as a ratio bar for bar, skipping bars without a base', () => {
    const base = [{ t: 1, o: 1, h: 1, l: 1, c: 2, v: 0 }, { t: 2, o: 1, h: 1, l: 1, c: 0, v: 0 }, { t: 3, o: 1, h: 1, l: 1, c: 4, v: 0 }];
    expect(ratioSeries([{ t: 1, v: 1 }, { t: 2, v: 1 }, { t: 3, v: 2 }, { t: 9, v: 1 }], base)).toEqual([{ t: 1, v: 0.5 }, { t: 3, v: 0.5 }]);
  });

  it('writes the Solar Hijri date in Latin digits, zero-padded', () => {
    expect(toJalali(2024, 3, 20)).toEqual({ jy: 1403, jm: 1, jd: 1 });
    expect(toJalali(2025, 3, 21)).toEqual({ jy: 1404, jm: 1, jd: 1 });
    expect(toJalali(2026, 10, 31)).toEqual({ jy: 1405, jm: 8, jd: 9 });
    expect(toJalali(2025, 3, 20)).toEqual({ jy: 1403, jm: 12, jd: 30 });
    expect(jalaliDate(1_700_000_000, 'Asia/Tehran')).toBe('1402/08/24');
    expect([cell(1.5), cell(81000), cell(0.000123456789), cell(NaN), cell(null)]).toEqual(['1.5', '81000', '0.00012346', '', '']);
  });

  it('exports the bars with provenance, two date columns, blank volume on a feed with none, and a BOM', () => {
    const csv = candleCsv([{ t: 1_700_003_600, o: 2, h: 3, l: 1, c: 2.5, v: 0 }, { t: 1_700_000_000, o: 1, h: 2, l: 0.5, c: 1.5, v: 0 }], { symbol: 'XAUUSD', interval: '1h', source: '', timeZone: 'Asia/Tehran', en: false });
    const rows = csv.slice(1).split('\r\n');
    expect(csv.startsWith('﻿')).toBe(true);
    expect(rows[0]).toBe('"# XAUUSD · 1h · بدون منبع"');
    expect(rows[1]).toBe('"زمان (ISO)","تاریخ (شمسی)","ساعت","باز","بیشترین","کمترین","بسته","حجم"');
    expect(rows[2]).toBe('"2023-11-14T22:13:20Z","1402/08/24","01:43","1","2","0.5","1.5",""');
    expect(csvFileName('BTC/USDT', '4h', 'UTC', Date.UTC(2026, 9, 31))).toBe('BTC-USDT-4h-1405-08-09.csv');
  });

  it('turns the symbol wheel on a ring of symbols with artwork', () => {
    const lists = (s: string) => s !== 'NOLOGO';
    expect(symbolNeighbours(['A', 'B', 'C'], 'B', lists)).toEqual({ previous: 'A', next: 'C', position: 2, total: 3 });
    expect(symbolNeighbours(['A', 'B'], 'A', lists)).toEqual({ previous: null, next: 'B', position: 1, total: 2 });
    expect(symbolStep(['A', 'NOLOGO', 'B', 'C'], 'C', 1, lists)).toBe('A');
    expect(symbolStep(['A', 'B', 'C'], 'A', -1, lists)).toBe('C');
    expect(symbolStep(['A'], 'A', 1, lists)).toBeNull();
    expect(symbolStep(['A', 'B'], 'X', -1, lists)).toBe('B');
  });

  it('keeps drawing scopes apart: layouts save their own, every-layout drawings join every layout', () => {
    localStorage.clear();
    const here = { type: 'hline', id: 'h', scope: 'none' } as never;
    const mine = { type: 'trend', id: 't', scope: 'layout' } as never;
    const old = { type: 'trend', id: 'o' } as never;
    const all = { type: 'hline', id: 'g', scope: 'global' } as never;
    expect(forLayout([here, mine, old, all])).toEqual([mine, old]);
    noteGlobal('XAUUSD', [here, mine, all]);
    expect(readGlobal('XAUUSD')).toEqual([all]);
    expect(withGlobal([mine, here, all], readGlobal('XAUUSD'))).toEqual([mine, all]);
    expect([scopeFromId('global'), scopeFromId('x'), scopeFromId(null)]).toEqual(['global', 'layout', 'layout']);
  });

  it('turns a drawn position into an order only when its lines are on the right sides', () => {
    const st = { entry: 100, stop: 98, target: 104, long: true, account: 1000, lot: 1, riskAmt: 10, qty: 5, reward: 20, rr: 2, tpPct: 4, slPct: -2 };
    expect(positionOrder(st)).toEqual({ side: 'buy', price: 100, quantity: 5, stopLoss: 98, takeProfit: 104 });
    expect(positionOrder({ ...st, stop: 102 })).toBeNull();
    expect(positionOrder({ ...st, long: false, stop: 102, target: 95 })!.side).toBe('sell');
  });
});

describe('founding member', () => {
  beforeEach(() => useSession.setState({ sessions: {} } as never));
  it('stamps only where the account data says so', () => {
    expect(isFoundingMember()).toBe(false);
    expect(foundingStamp(false)).toBeNull();
    useSession.setState({ sessions: { tradeyar: { access: 'x', refresh: null, refreshAt: 0, user: { founding: true } } } } as never);
    expect(foundingStamp(false)).toBe('عضو بنیان‌گذار');
    expect(foundingStamp(true)).toBe('Founding member');
  });
});
