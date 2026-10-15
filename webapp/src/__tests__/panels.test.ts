import { beforeEach, describe, expect, it, vi } from 'vitest';
import { exportList, normalizeList, parseList, parseSymbols, useWatchlist } from '../state/watchlist';
import { listDoc, listsOf, marksOf } from '../data/watchlistSync';
import { conditionHolds, describeAlert, fillMessage, lineLevel, startAlertWatch, useAlerts } from '../state/alerts';
import { useMarket } from '../state/market';
import { countdown, nextSessionChange, sessionText } from '../data/sessionClock';
import { dailyStats } from '../data/symbolStats';
import { technicals, indicatorSeries } from '../data/technicals';
import { categoriesOf, importanceOf, tickersIn } from '../shell/NewsPanel';
import { currenciesOf, surprise } from '../shell/CalendarPanel';
import { signalState } from '../shell/SignalsPanel';
import { readingState } from '../shell/AiPanel';
import { hotlist } from '../shell/Hotlists';
import { rowValues } from '../shell/Watchlist';
import { submitOrder } from '../shell/orderTicket';
import { usePaper } from '../state/paper';
import type { Candle, Ticker } from '../data/types';
import type { NewsItem, CalendarEvent } from '../data/intel';

const tick = (symbol: string, last: number) => useMarket.getState().put([{ symbol, last, change: 0, changePct: 0, ts: Date.now() }]);
const bars = (n: number, start = 100, step = 3600, slope = 0.5): Candle[] => Array.from({ length: n }, (_, i) => {
  const c = start + i * slope + Math.sin(i / 3) * 2;
  return { t: 1_700_000_000 + i * step, o: c - 0.3, h: c + 1, l: c - 1, c, v: 100 + i };
});

describe('watchlist sections', () => {
  beforeEach(() => { localStorage.clear(); useWatchlist.setState({ lists: [{ id: 'a', name: 'A', symbols: ['BTCUSDT', 'ETHUSDT', 'EURUSD', 'XAUUSD'] }], active: 'a', symbols: ['BTCUSDT', 'ETHUSDT', 'EURUSD', 'XAUUSD'], flags: {}, notes: {} }); });

  it('reads and writes TradingView ###sections', () => {
    const p = parseList('BINANCE:BTCUSDT,###FOREX,FX:EURUSD,OANDA:GBPUSD,###Metals,XAUUSD');
    expect(p.symbols).toEqual(['BTCUSDT', 'EURUSD', 'GBPUSD', 'XAUUSD']);
    expect(p.sections).toEqual([{ name: 'FOREX', symbols: ['EURUSD', 'GBPUSD'] }, { name: 'Metals', symbols: ['XAUUSD'] }]);
    expect(parseSymbols('btc/usdt eth-usdt')).toEqual(['BTCUSDT', 'ETHUSDT']);
    const l = normalizeList({ id: 'x', name: 'x', symbols: p.symbols, sections: p.sections.map((s, i) => ({ id: `s${i}`, ...s })) });
    expect(exportList(l)).toBe('BTCUSDT,###FOREX,EURUSD,GBPUSD,###Metals,XAUUSD');
  });

  it('keeps every symbol in one section, the list in display order', () => {
    const l = normalizeList({ id: 'x', name: 'x', symbols: ['A1', 'B1', 'C1', 'A1'], sections: [{ id: 's', name: 'S', symbols: ['A1', 'ZZ'] }, { id: 't', name: 'T', symbols: ['A1', 'C1'] }] });
    expect(l.symbols).toEqual(['B1', 'A1', 'C1']);
    expect(l.sections!.map((s) => s.symbols)).toEqual([['A1'], ['C1']]);
  });

  it('adds, moves, folds, renames and removes sections', () => {
    const w = useWatchlist.getState();
    const id = w.addSection('FX', ['EURUSD']);
    expect(useWatchlist.getState().symbols).toEqual(['BTCUSDT', 'ETHUSDT', 'XAUUSD', 'EURUSD']);
    useWatchlist.getState().move(['XAUUSD'], null, id);
    expect(useWatchlist.getState().lists[0]!.sections![0]!.symbols).toEqual(['EURUSD', 'XAUUSD']);
    useWatchlist.getState().move(['XAUUSD'], 'EURUSD', null);
    expect(useWatchlist.getState().lists[0]!.sections![0]!.symbols).toEqual(['XAUUSD', 'EURUSD']);
    useWatchlist.getState().move(['ETHUSDT'], 'BTCUSDT', null);
    expect(useWatchlist.getState().symbols.slice(0, 2)).toEqual(['ETHUSDT', 'BTCUSDT']);
    useWatchlist.getState().toggleSection(id);
    expect(useWatchlist.getState().lists[0]!.sections![0]!.collapsed).toBe(true);
    useWatchlist.getState().renameSection(id, 'Majors');
    expect(useWatchlist.getState().lists[0]!.sections![0]!.name).toBe('Majors');
    useWatchlist.getState().deleteSection(id);
    expect(useWatchlist.getState().lists[0]!.sections).toBeUndefined();
    expect(useWatchlist.getState().symbols).toHaveLength(4);
  });

  it('flags many, unflags all, removes many', () => {
    const w = useWatchlist.getState();
    w.flagMany(['BTCUSDT', 'ETHUSDT'], 3);
    expect(useWatchlist.getState().flags).toEqual({ BTCUSDT: 3, ETHUSDT: 3 });
    useWatchlist.getState().unflagAll();
    expect(useWatchlist.getState().flags).toEqual({});
    useWatchlist.getState().removeMany(['BTCUSDT', 'XAUUSD']);
    expect(useWatchlist.getState().symbols).toEqual(['ETHUSDT', 'EURUSD']);
  });

  it('change from the previous close and zero stays neutral', () => {
    const tk: Ticker = { symbol: 'BTCUSDT', last: 110, change: 5, changePct: 4.76, high: 112, low: 100, volume: 10, ts: 0 };
    const v = rowValues('BTCUSDT', tk, 100);
    expect(v.change).toBeCloseTo(10); expect(v.changePct).toBeCloseTo(10); expect(v.volume).toBe(1100); expect(v.range).toBeCloseTo(12);
    expect(rowValues('BTCUSDT', tk).change).toBe(5);
  });
});

describe('watchlist sync', () => {
  it('writes flags in the phone’s ids and sections and notes as extra fields', () => {
    const d = listDoc({ id: 'a', symbols: ['X'], name: 'A', columns: ['last'], updated_at_ms: 1 }, { id: 'a', name: 'A', symbols: ['BTCUSDT', 'EURUSD'], sections: [{ id: 's', name: 'FX', symbols: ['EURUSD'] }] }, { BTCUSDT: 0, EURUSD: 5, ETHUSDT: 1 }, { EURUSD: 'note' }, 99);
    expect(d.flags).toEqual({ BTCUSDT: 'red', EURUSD: 'cyan' });
    expect(d.columns).toEqual(['last']);
    expect(d.web_sections).toEqual([{ id: 's', name: 'FX', symbols: ['EURUSD'] }]);
    expect(d.web_notes).toEqual({ EURUSD: 'note' });
    expect(d.updated_at_ms).toBe(99);
    const same = listDoc(d, { id: 'a', name: 'A', symbols: ['BTCUSDT', 'EURUSD'], sections: [{ id: 's', name: 'FX', symbols: ['EURUSD'] }] }, { BTCUSDT: 0, EURUSD: 5 }, { EURUSD: 'note' }, 200);
    expect(same.updated_at_ms).toBe(99);
  });
  it('reads the document back', () => {
    const payload = { lists: [{ id: 'a', name: 'A', symbols: ['BTCUSDT'], flags: { BTCUSDT: 'green', ETHUSDT: 'grey' }, web_notes: { BTCUSDT: 'hi' }, web_sections: [{ id: 's', name: 'S', symbols: ['BTCUSDT'] }] }, { id: 'b', symbols: ['X'] }], deleted: { b: 1 } };
    expect(listsOf(payload)).toEqual([{ id: 'a', name: 'A', symbols: ['BTCUSDT'], sections: [{ id: 's', name: 'S', symbols: ['BTCUSDT'] }] }]);
    expect(marksOf(payload)).toEqual({ flags: { BTCUSDT: 2 }, notes: { BTCUSDT: 'hi' } });
  });
});

describe('alert conditions', () => {
  it('holds by its definition', () => {
    expect(conditionHolds({ condition: 'cross_up', value: 10 }, 9, 10)).toBe(true);
    expect(conditionHolds({ condition: 'cross_up', value: 10 }, 11, 9)).toBe(false);
    expect(conditionHolds({ condition: 'cross_down', value: 10 }, 11, 9)).toBe(true);
    expect(conditionHolds({ condition: 'enter_channel', value: 10, high: 20 }, 9, 15)).toBe(true);
    expect(conditionHolds({ condition: 'enter_channel', value: 10, high: 20 }, 12, 15)).toBe(false);
    expect(conditionHolds({ condition: 'exit_channel', value: 10, high: 20 }, 15, 21)).toBe(true);
    expect(conditionHolds({ condition: 'inside_channel', value: 20, high: 10 }, null, 15)).toBe(true);
    expect(conditionHolds({ condition: 'outside_channel', value: 10, high: 20 }, null, 25)).toBe(true);
    expect(conditionHolds({ condition: 'move_up_pct', value: 2 }, null, 102.5, 100)).toBe(true);
    expect(conditionHolds({ condition: 'move_down_pct', value: 2 }, null, 99, 100)).toBe(false);
    expect(conditionHolds({ condition: 'move_up', value: 5 }, null, 106, 100)).toBe(true);
    expect(conditionHolds({ condition: 'move_down', value: 5 }, null, 94, 100)).toBe(true);
  });
  it('slides a trend line with time, fills placeholders, names itself', () => {
    expect(lineLevel({ t1: 0, p1: 100, t2: 10, p2: 110 }, 20)).toBe(120);
    expect(fillMessage('{{ticker}} at {{close}} on {{interval}} {{nope}}', { symbol: 'BTCUSDT', price: 5, interval: '60' })).toBe('BTCUSDT at 5 on 60 {{nope}}');
    expect(describeAlert({ symbol: 'ETHUSDT', condition: 'enter_channel', value: 1, high: 2 }, false)).toBe('ETHUSDT Entering channel 1 – 2');
    expect(describeAlert({ symbol: 'ETHUSDT', condition: 'move_up_pct', value: 3, bars: 4 }, true)).toContain('کندل');
  });
});

describe('alert watcher and list actions', () => {
  beforeEach(() => { useAlerts.setState({ list: [], fired: null, log: [], unseen: 0 }); localStorage.clear(); startAlertWatch(); });
  it('every-time alerts respect the cool-down and log each firing with its message', () => {
    const id = useAlerts.getState().create({ symbol: 'SOLUSDT', condition: 'above', value: 10, freq: 'every', cooldownMs: 60_000, message: '{{ticker}} {{close}}' });
    tick('SOLUSDT', 11);
    tick('SOLUSDT', 12);
    const a = useAlerts.getState().list.find((x) => x.id === id)!;
    expect(a.active).toBe(true);
    expect(a.fires).toBe(1);
    expect(useAlerts.getState().log[0]!.message).toBe('SOLUSDT 11');
    expect(useAlerts.getState().unseen).toBe(1);
  });
  it('an expired alert stops without firing', () => {
    useAlerts.getState().create({ symbol: 'ADAUSDT', condition: 'above', value: 1, expiresAt: Date.now() - 1 });
    tick('ADAUSDT', 2);
    const a = useAlerts.getState().list[0]!;
    expect(a.active).toBe(false); expect(a.expired).toBe(true); expect(useAlerts.getState().fired).toBeNull();
  });
  it('a second AND condition must hold too', () => {
    useAlerts.getState().create({ symbol: 'DOGEUSDT', condition: 'above', value: 1, and: { condition: 'below', value: 2 } });
    tick('DOGEUSDT', 3);
    expect(useAlerts.getState().fired).toBeNull();
    tick('DOGEUSDT', 1.5);
    expect(useAlerts.getState().fired?.symbol).toBe('DOGEUSDT');
  });
  it('a trend-line alert crosses the line where it is now', () => {
    const now = Date.now() / 1000;
    useAlerts.getState().create({ symbol: 'LTCUSDT', condition: 'cross_up', value: 0, source: 'line', line: { t1: now - 100, p1: 50, t2: now, p2: 60 } });
    tick('LTCUSDT', 55);
    expect(useAlerts.getState().fired).toBeNull();
    tick('LTCUSDT', 61);
    expect(useAlerts.getState().fired?.symbol).toBe('LTCUSDT');
  });
  it('edits, clones, stops all, restarts and deletes the inactive', () => {
    const s = useAlerts.getState();
    const id = s.create({ symbol: 'XRPUSDT', condition: 'above', value: 5, name: 'mine' });
    useAlerts.getState().update(id, { value: 6 });
    expect(useAlerts.getState().list[0]!.value).toBe(6);
    const c = useAlerts.getState().clone(id);
    expect(useAlerts.getState().list.find((x) => x.id === c)!.name).toBe('mine (2)');
    useAlerts.getState().stopAll();
    expect(useAlerts.getState().list.every((a) => !a.active)).toBe(true);
    useAlerts.getState().restartInactive();
    expect(useAlerts.getState().list.every((a) => a.active)).toBe(true);
    useAlerts.getState().toggle(id);
    useAlerts.getState().deleteInactive();
    expect(useAlerts.getState().list.map((a) => a.id)).toEqual([c]);
  });
});

describe('details data', () => {
  it('counts down to the next session change', () => {
    const sat = Date.UTC(2026, 9, 17, 12, 0);
    const n = nextSessionChange('forex', sat)!;
    expect(n.open).toBe(false);
    expect(new Date(n.at).getUTCDay()).toBe(0);
    expect(new Date(n.at).getUTCHours()).toBe(22);
    expect(nextSessionChange('crypto')).toBeNull();
    expect(countdown(3_723_000)).toBe('01:02:03');
    expect(countdown(90_000_000)).toBe('1d 01:00');
    expect(sessionText('metals', false)).toContain('break');
  });
  it('the previous close and today’s range come from the daily bars', () => {
    const day = 86400;
    const now = Date.UTC(2026, 9, 15, 12) ;
    const today = Math.floor(now / 1000 / day) * day;
    const daily: Candle[] = [{ t: today - 2 * day, o: 1, h: 2, l: 0.5, c: 1.5, v: 1 }, { t: today - day, o: 1.5, h: 3, l: 1, c: 2, v: 1 }, { t: today, o: 2, h: 2.5, l: 1.8, c: 2.2, v: 1 }];
    const s = dailyStats(daily, 2.6, now);
    expect(s.prevClose).toBe(2); expect(s.open).toBe(2); expect(s.dayHigh).toBe(2.6); expect(s.dayLow).toBe(1.8);
    expect(s.perf[0]).toEqual({ id: '1D', pct: expect.closeTo(30, 5) });
  });
  it('lists every indicator of TradingView’s Technicals with a vote', () => {
    const t = technicals(bars(260))!;
    expect(t.oscillators.length).toBeGreaterThanOrEqual(9);
    expect(t.averages.length).toBeGreaterThanOrEqual(14);
    expect(t.averages.find((r) => r.id === 'sma200')!.action).toBe(1);
    expect(indicatorSeries('macd', bars(100)).map((o) => o.id)).toEqual(expect.arrayContaining(['line', 'signal']));
  });
});

describe('news, calendar, signals, hotlists', () => {
  const item = (title: string, extra: Partial<NewsItem> = {}): NewsItem => ({ id: title, market: 'forex', title, summary: '', source: 's', time: 0, image: null, importance: 0, ...extra });
  it('marks forex news by its words and files it under categories', () => {
    expect(importanceOf(item('Fed holds rates; CPI hotter than expected'))).toBeGreaterThanOrEqual(7);
    expect(importanceOf(item('Quiet session in Asia'))).toBe(0);
    expect(categoriesOf(item('طلا در بالاترین سطح'))).toContain('metals');
    expect(categoriesOf(item('خبر بیت‌کوین', { market: 'crypto' }))).toEqual(['crypto']);
  });
  it('finds the tickers a story names', () => {
    const inst = [{ symbol: 'BTCUSDT', market: 'crypto' as const, base: 'BTC', quote: 'USDT', nameFa: 'بیت‌کوین/تتر', digits: 2 }, { symbol: 'XAUUSD', market: 'metals' as const, base: 'XAU', quote: 'USD', digits: 2 }, { symbol: 'ONEUSDT', market: 'crypto' as const, base: 'ONE', quote: 'USDT', digits: 4 }];
    expect(tickersIn(item('بیت‌کوین و طلا هم‌زمان بالا رفتند'), inst)).toEqual(['BTCUSDT', 'XAUUSD']);
    expect(tickersIn(item('One more day of gains'), inst)).toEqual([]);
  });
  it('colours an actual against the forecast', () => {
    const e = (title: string, actual: string, forecast: string): CalendarEvent => ({ title, currency: 'USD', time: 0, impact: 'high', actual, forecast, previous: '' });
    expect(surprise(e('CPI m/m', '0.4%', '0.3%'))).toBe(1);
    expect(surprise(e('Unemployment Rate', '4.2%', '4.0%'))).toBe(-1);
    expect(surprise(e('CPI', '', '0.3%'))).toBe(0);
    expect(currenciesOf('EURUSD')).toEqual(['EUR', 'USD']);
    expect(currenciesOf('BTCUSDT')).toEqual(['USD']);
  });
  it('works out a signal’s and a reading’s state from the price', () => {
    const s = { id: '1', symbol: 'X', direction: 'long' as const, timeframe: '1h', status: 'active', entry: 100, sl: 95, targets: [105, 110, 120], createdAt: 0, closedAt: null, isWin: null, pctGain: null, rr: null, confidence: 80, rationale: null };
    expect(signalState(s, 111)).toEqual({ key: 'tp', hits: 2 });
    expect(signalState(s, 94).key).toBe('sl');
    expect(signalState({ ...s, isWin: true }, 100).key).toBe('win');
    const r = { assessment: '', direction: 'short' as const, confidence: 70, trendBias: null, structure: null, setup: null, entryLow: 100, entryHigh: 100, sl: 105, targets: [95, 90], reasoning: null, risk: null };
    expect(readingState(r, 94)).toEqual({ key: 'tp', hits: 1 });
    expect(readingState(r, 106).key).toBe('sl');
  });
  it('ranks the hotlists', () => {
    const t = (symbol: string, changePct: number, volume: number, last = 1): Ticker => ({ symbol, last, change: 0, changePct, volume, high: last * 1.1, low: last * 0.9, ts: 0 });
    const list = [t('A', 5, 10), t('B', -3, 1000), t('C', 1, 5)];
    expect(hotlist(list, 'gainers').map((x) => x.symbol)).toEqual(['A', 'C']);
    expect(hotlist(list, 'losers').map((x) => x.symbol)).toEqual(['B']);
    expect(hotlist(list, 'active')[0]!.symbol).toBe('B');
  });
});

describe('order tickets', () => {
  beforeEach(() => { localStorage.clear(); usePaper.getState().reset(); });
  it('offers the order to live trading first, else fills on paper', () => {
    const seen = vi.fn((e: Event) => e.preventDefault());
    window.addEventListener('pc:order-ticket', seen);
    expect(submitOrder({ symbol: 'BTCUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 90, last: 100 })).toEqual({ live: true });
    expect((seen.mock.calls[0]![0] as CustomEvent).detail).toMatchObject({ symbol: 'BTCUSDT', side: 'buy', type: 'limit', price: 90, quantity: 1, source: 'panel' });
    expect(usePaper.getState().orders).toHaveLength(0);
    window.removeEventListener('pc:order-ticket', seen);
    const r = submitOrder({ symbol: 'BTCUSDT', side: 'short', type: 'market', qty: 0.01, last: 100, sl: 110 });
    expect(r.live).toBe(false);
    expect(usePaper.getState().positions[0]).toMatchObject({ symbol: 'BTCUSDT', side: 'short', sl: 110 });
  });
});
