import { beforeEach, describe, expect, it } from 'vitest';
import {
  attribution, backtestPreamble, backtestRows, buildCsv, buildWorkbook, byMonth, canRequestClose, columnName, compactMoney, crc32, durationLabel, durationParts,
  filterJournal, isExplicitStopLossReason, jalaliLong, jalaliOf, jalaliSlash, journalStats, numberCell, paperAsClosed, paperRecord, readSignalRecord,
  sharpe, sortino, summarizeSignals, filterHistory, tradeFields, tradeMetrics, TRADE_HEADERS, isoInstant, type ClosedTrade,
} from '../pages/records';
import { usePaper, taking, resting, feeFor, DEFAULT_RULES, type PaperTrade } from '../state/paper';
import type { JournalEntry } from '../state/journal';
import type { BtResult } from '../pages/backtestEngine';

const trade = (net: number | null, closedAt: number, extra: Partial<ClosedTrade> = {}): ClosedTrade => ({
  id: String(closedAt), symbol: 'BTCUSDT', side: 'buy', volume: 1, entry: 100, exit: 101, openedAt: closedAt - 60, closedAt,
  gross: null, commission: null, swap: null, net, pips: null, reason: '', balanceAfter: null, liquidated: false, currency: 'USDT', ...extra,
});

describe('records: Solar Hijri dates (JalaliDate)', () => {
  it('converts the days the Kotlin tests name', () => {
    expect(jalaliOf(2026, 1, 1)).toEqual({ year: 1404, month: 10, day: 11 });
    expect(jalaliOf(2026, 3, 21)).toEqual({ year: 1405, month: 1, day: 1 });
    expect(jalaliOf(2025, 3, 20)).toEqual({ year: 1403, month: 12, day: 30 });
    expect(jalaliOf(2024, 9, 21)).toEqual({ year: 1403, month: 6, day: 31 });
    expect(jalaliOf(2024, 9, 22)).toEqual({ year: 1403, month: 7, day: 1 });
    expect(jalaliLong({ year: 1404, month: 10, day: 11 })).toBe('۱۱ دی ۱۴۰۴');
    expect(jalaliSlash({ year: 1404, month: 1, day: 5 })).toBe('1404/01/05');
  });
});

describe('records: the full report (PortfolioMetrics)', () => {
  const six = [trade(100, 1), trade(-50, 2), trade(0, 3), trade(200, 4), trade(-100, 5), trade(-100, 6)];
  it('adds up on paper: wins, scratches, ratios, streaks', () => {
    const m = tradeMetrics([...six].reverse());
    expect([m.trades, m.wins, m.losses, m.scratches, m.net]).toEqual([6, 2, 3, 1, 50]);
    expect(m.winRate).toBeCloseTo(40);
    expect(m.profitFactor).toBeCloseTo(1.2);
    expect(m.expectancy).toBeCloseTo(50 / 6);
    expect(m.averageWin).toBe(150); expect(m.averageLoss).toBeCloseTo(250 / 3);
    expect(m.winLossRatio).toBeCloseTo(1.8);
    expect([m.largestWin, m.largestLoss]).toEqual([200, -100]);
    expect([m.longestWinStreak, m.longestLossStreak]).toEqual([1, 2]);
    expect(m.equityIsBalance).toBe(false);
    expect(m.equity.map((p) => p.equity)).toEqual([100, 50, 50, 250, 150, 50]);
    expect(m.averageHoldingSeconds).toBe(60);
  });
  it('marks the deepest fall, the best climb and the longest stretch under water', () => {
    const m = tradeMetrics(six);
    expect(m.drawdown).toMatchObject({ peakIndex: 3, troughIndex: 5, depth: 200, depthPercent: null });
    expect(m.runUp).toMatchObject({ troughIndex: 1, peakIndex: 3, height: 200, heightPercent: null });
    expect(m.longestDrawdown).toMatchObject({ trades: 3, recovered: true });
    // On a real balance curve the percentages appear.
    const bal = tradeMetrics([trade(100, 1, { balanceAfter: 1100 }), trade(-220, 2, { balanceAfter: 880 }), trade(50, 3, { balanceAfter: 930 })]);
    expect(bal.equityIsBalance).toBe(true);
    expect(bal.drawdown!.depthPercent).toBeCloseTo(20);
    expect(bal.runUp!.heightPercent).toBeCloseTo((50 / 880) * 100);
    expect(bal.longestDrawdown).toMatchObject({ trades: 2, recovered: false });
  });
  it('Sharpe divides by every return; Sortino\'s downside mean is over every return too', () => {
    expect(sharpe([2, 0, -1, 3])).toBeCloseTo(1 / Math.sqrt(2.5));
    expect(sortino([2, 0, -1, 3])).toBeCloseTo(2);
    expect(sharpe([1])).toBeNull(); expect(sharpe([1, 1])).toBeNull(); expect(sortino([1, 2])).toBeNull();
  });
  it('attribution orders by the size of the move and shares each pool', () => {
    const rows = attribution([trade(300, 1, { symbol: 'A' }), trade(100, 2, { symbol: 'B' }), trade(-400, 3, { symbol: 'C' }), trade(0, 4, { symbol: 'D' })]);
    expect(rows.map((r) => r.symbol)).toEqual(['C', 'A', 'B', 'D']);
    expect(rows[0]!.share).toBeCloseTo(100); expect(rows[1]!.share).toBeCloseTo(75); expect(rows[2]!.share).toBeCloseTo(25); expect(rows[3]!.share).toBeNull();
  });
  it('months are Solar Hijri, quiet months kept as empty rows', () => {
    const at = (y: number, m: number, d: number) => Math.floor(new Date(y, m - 1, d, 12).getTime() / 1000);
    const rows = byMonth([trade(10, at(2026, 1, 1)), trade(-4, at(2026, 1, 25)), trade(7, at(2026, 3, 1))]);
    expect(rows.map((r) => [r.year, r.month, r.trades, r.net])).toEqual([[1404, 10, 1, 10], [1404, 11, 1, -4], [1404, 12, 1, 7]]);
    const gap = byMonth([trade(1, at(2026, 1, 1)), trade(1, at(2026, 3, 1))]);
    expect(gap.map((r) => r.trades)).toEqual([1, 0, 1]);
  });
  it('durations and month cells read as the phone writes them', () => {
    expect(durationParts(90061)).toEqual({ days: 1, hours: 1, minutes: 1, seconds: 1 });
    expect(durationParts(-5)).toEqual({ days: 0, hours: 0, minutes: 0, seconds: 0 });
    expect(durationLabel(90061, true)).toBe('۱ روز و ۱ ساعت');
    expect(durationLabel(3720, false)).toBe('1 h 2 m');
    expect(compactMoney(1240)).toBe('+1.2k'); expect(compactMoney(-35.4)).toBe('-35');
  });
});

describe('records: files (core/export)', () => {
  it('number cells and CSV follow Numbers.cell and Csv.build', () => {
    expect(numberCell(12.5)).toBe('12.5'); expect(numberCell(1e-9)).toBe('0'); expect(numberCell(1234567.123456789)).toBe('1234567.12345679');
    expect(numberCell(null)).toBe(''); expect(numberCell(NaN)).toBe('');
    expect(buildCsv(['a', 'b'], [['1', 'x"y']], [['t', 'v']])).toBe('﻿"t","v"\r\n"a","b"\r\n"1","x""y"\r\n');
    expect(columnName(0)).toBe('A'); expect(columnName(25)).toBe('Z'); expect(columnName(26)).toBe('AA');
  });
  it('trade rows carry ISO and Solar Hijri dates; absent numbers are empty, not zero', () => {
    const t = trade(-12.5, Math.floor(Date.UTC(2026, 0, 1, 9, 30) / 1000), { openedAt: null, commission: -0.4 });
    const f = tradeFields(t);
    expect(f).toHaveLength(TRADE_HEADERS.length);
    expect(f[8]).toBe('2026-01-01T09:30:00Z');
    expect(f[9]).toBe('1404/10/11');
    expect([f[6], f[7], f[10], f[11], f[12], f[14]]).toEqual(['', '', '', '', '-0.4', '-12.5']);
    expect(isoInstant(null)).toBe('');
  });
  it('builds a real .xlsx: a stored zip whose parts check out, numbers typed, right to left', () => {
    const bytes = buildWorkbook({ sheetName: 'معاملات', header: ['نماد', 'سود'], rows: [['BTC<USDT', '12.5'], ['ETH', '']], numericColumns: [1] });
    const dv = new DataView(bytes.buffer);
    expect(dv.getUint32(0, true)).toBe(0x04034b50);
    const parts: Record<string, string> = {};
    let at = 0;
    while (dv.getUint32(at, true) === 0x04034b50) {
      const crc = dv.getUint32(at + 14, true); const size = dv.getUint32(at + 18, true); const nl = dv.getUint16(at + 26, true);
      const name = new TextDecoder().decode(bytes.slice(at + 30, at + 30 + nl));
      const data = bytes.slice(at + 30 + nl, at + 30 + nl + size);
      expect(crc32(data)).toBe(crc);
      parts[name] = new TextDecoder().decode(data);
      at += 30 + nl + size;
    }
    expect(Object.keys(parts)).toEqual(['[Content_Types].xml', '_rels/.rels', 'xl/workbook.xml', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml', 'xl/worksheets/sheet1.xml']);
    const sheet = parts['xl/worksheets/sheet1.xml']!;
    expect(sheet).toContain('rightToLeft="1"');
    expect(sheet).toContain('<c r="B2"><v>12.5</v></c>');
    expect(sheet).toContain('BTC&lt;USDT');
    expect(sheet).not.toContain('r="B3"');
    expect(parts['xl/workbook.xml']).toContain('name="معاملات"');
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926);
    // The end-of-directory record names six entries.
    expect(dv.getUint16(bytes.length - 22 + 10, true)).toBe(6);
  });
  it('the backtest file opens with the run and lists every trade with the running balance', () => {
    const result = { trades: [{ dir: 1, entryT: 1767259800, entry: 100, exitT: 1767263400, exit: 110, pnl: 9.8, pct: 9.8, reason: 'signal', qty: 1, fee: 0.2, entryIdx: 3, exitIdx: 4 }], equity: [], net: 9.8, netPct: 0.098, win: 100, pf: Infinity, dd: 0, ddPct: 0, avg: 9.8, sharpe: 0, buyHold: 5, exposure: 10 } as unknown as BtResult;
    const run = { symbol: 'BTCUSDT', strategy: 'تقاطع میانگین متحرک', direction: 'long' as const, feePct: 0.05, capital: 1000, bars: 500, from: 1767225600, to: 1767312000, result };
    const pre = backtestPreamble(run);
    expect(pre[0]).toEqual(['گزارش بک‌تست', 'BTCUSDT']);
    expect(pre.find((r) => r[0] === 'جهت')![1]).toBe('فقط خرید');
    expect(pre.find((r) => r[0] === 'ضریب سود')![1]).toBe('');
    const rows = backtestRows(run);
    expect(rows[0]![0]).toBe('1'); expect(rows[0]![1]).toBe('خرید'); expect(rows[0]![12]).toBe('10'); expect(rows[0]![18]).toBe('1009.8');
  });
});

describe('records: paper', () => {
  beforeEach(() => { localStorage.clear(); usePaper.setState({ balance: 10_000, positions: [], history: [], orders: [], fills: [], rules: DEFAULT_RULES, lastError: null }); });
  it('taking the market: the assumed spread around last, then the slippage, a cap for a limit (PaperFills.taking)', () => {
    const p = taking('long', 100, DEFAULT_RULES);
    expect(p.spreadPerUnit).toBeCloseTo(0.02);
    expect(p.price).toBeCloseTo(100.02 * 1.0002);
    expect(p.slippagePerUnit).toBeCloseTo(100.02 * 0.0002);
    expect(taking('short', 100, DEFAULT_RULES).price).toBeCloseTo(99.98 * 0.9998);
    expect(taking('long', 100, DEFAULT_RULES, 100.01).price).toBe(100.01);
    expect(feeFor(resting(95), 2, DEFAULT_RULES)).toBeCloseTo(95 * 2 * 0.0002);
  });
  it('every fill is logged with its costs, and whether the cross was watched', () => {
    usePaper.getState().open('BTCUSDT', 'long', 1, 100);
    let f = usePaper.getState().fills[0]!;
    expect(f).toMatchObject({ basis: 'taken', watched: true, assumedSpread: true, reference: 100 });
    expect(f.spreadCost).toBeCloseTo(0.02);
    usePaper.getState().place({ symbol: 'ETHUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 95 }, 100);
    usePaper.getState().mark('ETHUSDT', 96);
    usePaper.getState().mark('ETHUSDT', 94);
    f = usePaper.getState().fills[0]!;
    expect(f).toMatchObject({ symbol: 'ETHUSDT', basis: 'rested', price: 95, watched: true, spreadCost: 0, slippage: 0 });
    // A level already crossed at the first price this session saw: unwatched.
    usePaper.getState().place({ symbol: 'SOLUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 50 }, 100);
    usePaper.getState().mark('SOLUSDT', 40);
    expect(usePaper.getState().fills[0]).toMatchObject({ symbol: 'SOLUSDT', watched: false });
  });
  it('starting again keeps the history; erasing takes it (PaperEngine.reset / wipe)', () => {
    const id = usePaper.getState().open('BTCUSDT', 'long', 1, 100)!;
    usePaper.getState().close(id, 110);
    usePaper.getState().open('BTCUSDT', 'long', 1, 100);
    expect(usePaper.getState().history[0]!.balanceAfter).toBeCloseTo(usePaper.getState().balance + usePaper.getState().positions[0]!.fees!);
    usePaper.getState().reset();
    expect(usePaper.getState().positions).toHaveLength(0);
    expect(usePaper.getState().history).toHaveLength(1);
    expect(usePaper.getState().balance).toBe(DEFAULT_RULES.startingBalance);
    usePaper.getState().wipe();
    expect(usePaper.getState().history).toHaveLength(0);
    expect(usePaper.getState().fills).toHaveLength(0);
  });
  it('the record: expectancy, win to loss, by symbol worst first, costs', () => {
    const t = (pnl: number, symbol: string, closedAt: number, balanceAfter?: number): PaperTrade => ({ id: String(closedAt), symbol, side: 'long', qty: 1, entry: 100, exit: 100 + pnl, openedAt: closedAt - 1000, closedAt, pnl, fees: 1, balanceAfter });
    const r = paperRecord([t(30, 'A', 3000, 10_030), t(-10, 'B', 2000, 10_000), t(-20, 'B', 1000, 10_010)]);
    expect([r.trades, r.wins, r.losses, r.net]).toEqual([3, 1, 2, 0]);
    expect(r.expectancy).toBe(0);
    expect(r.payoff).toBeCloseTo(30 / 15);
    expect(r.profitFactor).toBeCloseTo(1);
    expect(r.bySymbol.map((s) => s.symbol)).toEqual(['B', 'A']);
    expect(r.costs).toBe(3);
    expect(r.equityIsBalance).toBe(true);
    expect(r.maxDrawdown).toBe(10);
    expect(paperAsClosed(t(5, 'A', 5000)).gross).toBe(6);
  });
});

describe('records: journal (JournalFilter, JournalStats)', () => {
  const e = (id: string, pnl: number | null, extra: Partial<JournalEntry> = {}): JournalEntry => ({ id, symbol: 'BTCUSDT', buy: true, entry: 1, exit: null, size: 1, pnl, emotion: 'آرام', note: '', lesson: '', tags: [], createdAt: 0, ...extra });
  const all = [e('a', 50, { screenshot: 'data:' }), e('b', -20), e('c', null, { note: 'قیمت ۶۴۰۰۰' }), e('d', 0, { tags: ['ریتست'] }), e('f', 30, { symbol: 'ETHUSDT' })];
  it('filters by outcome, screenshot, tag and text (digits folded)', () => {
    const ids = (q: Partial<Parameters<typeof filterJournal>[1]>) => filterJournal(all, { outcome: 'any', shot: 'any', tags: [], query: '', ...q }).map((x) => x.id);
    expect(ids({ outcome: 'wins' })).toEqual(['a', 'f']);
    expect(ids({ outcome: 'losses' })).toEqual(['b']);
    expect(ids({ outcome: 'ungraded' })).toEqual(['c']);
    expect(ids({ shot: 'with' })).toEqual(['a']);
    expect(ids({ shot: 'without', outcome: 'wins' })).toEqual(['f']);
    expect(ids({ tags: ['ریتست'] })).toEqual(['d']);
    expect(ids({ query: '64000' })).toEqual(['c']);
    expect(ids({ query: 'eth' })).toEqual(['f']);
  });
  it('figures over graded rows only: profit factor and expectancy', () => {
    const s = journalStats(all);
    expect([s.graded, s.wins, s.losses, s.net]).toEqual([4, 2, 1, 60]);
    expect(s.profitFactor).toBeCloseTo(4);
    expect(s.expectancy).toBe(15);
    expect(s.winRate).toBe(50);
    expect(journalStats([e('x', 10)]).profitFactor).toBeNull();
  });
});

describe('records: signal record (SignalPerformance)', () => {
  const raw = [
    { id: 1, symbol: 'btcusdt', result: { pnl_usd: 12 }, targets: [{ hit: true }, { hit: null }], close_reason: 'TP1', risk_reward_tp1: 2 },
    { id: 2, symbol: 'BTCUSDT', result: { pnl_usd: -5 }, targets: [{ hit: false }], close_reason: 'stop-loss', risk_reward_tp1: 1 },
    { id: 3, symbol: 'ETHUSDT', result: { pnl_usd: 0 }, targets: [{}], close_reason: ' ' },
    { id: 4, symbol: 'XAUUSD', market: 'forex', targets: [] },
  ].map((x) => readSignalRecord(x, 'crypto'));
  it('each rate over its own evidence; missing is not zero', () => {
    const s = summarizeSignals(raw);
    expect([s.total, s.wins, s.losses, s.breakeven, s.unknownPnl]).toEqual([4, 1, 1, 1, 1]);
    expect(s.winRate).toMatchObject({ hits: 1, denominator: 3 });
    expect(s.winRate.percent).toBeCloseTo(100 / 3);
    expect(s.tpHitRate).toEqual({ hits: 1, denominator: 2, percent: 50 });
    expect(s.stopLossRate).toEqual({ hits: 1, denominator: 2, percent: 50 });
    expect(s.averageRiskReward).toBe(1.5);
    expect(summarizeSignals([]).winRate.percent).toBeNull();
  });
  it('filters by market, instrument and result', () => {
    expect(filterHistory(raw, { market: 'forex' }).map((x) => x.id)).toEqual(['4']);
    expect(filterHistory(raw, { symbol: ' btcusdt ' }).map((x) => x.id)).toEqual(['1', '2']);
    expect(filterHistory(raw, { result: 'unknown' }).map((x) => x.id)).toEqual(['4']);
    expect(filterHistory(raw, { result: 'breakeven' }).map((x) => x.id)).toEqual(['3']);
    expect(isExplicitStopLossReason('Stop Loss')).toBe(true); expect(isExplicitStopLossReason('TP')).toBe(false);
  });
  it('a queued execution may be cancelled; MT5 closes what it sent; LBank only while queued', () => {
    expect(canRequestClose('queued', 'lbank')).toBe(true);
    expect(canRequestClose('open', 'lbank')).toBe(false);
    expect(canRequestClose('open', 'mt5')).toBe(true);
    expect(canRequestClose('submitted', 'mt5')).toBe(true);
    expect(canRequestClose('close_requested', 'mt5')).toBe(false);
  });
});
