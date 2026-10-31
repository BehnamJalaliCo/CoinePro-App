import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('../ui/SymbolLogo', () => ({ hasLogo: (s: string) => !s.startsWith('NOLOGO'), SymbolLogo: () => null }));
import { KINDS, BULLISH, LOOKBACK, MIN_BARS, chartStudiesOf, entrants, matches, reliabilityOf, scanOf, setupStudiesOf, tagsOf, type Scan } from '../pages/growthScan';
import { barMagnifier, firstTouch, growthHeld, simulate, strategySet, DEFAULT_OPTIONS, DEFAULT_PARAMS } from '../pages/backtestEngine';
import { BITCOIN, DAILY_CAP, WINDOW_MS, briefOf, canSpend, digestDue, moveText, moversOf, observe, quiet, shouldOffer, spanText, thresholdFor, post, sentToday, resetPulseRecord } from '../state/pulse';
import { closedOnly, scanWatch, watchBody, type ScanWatch } from '../state/scanWatch';
import { deadTile, isChartable } from '../data/chartable';
import type { Candle, Ticker } from '../data/types';

const H = 3600;
/** A series: flat-ish then whatever `step` says, with volume. */
function bars(n: number, price: (i: number) => number, vol: (i: number) => number = () => 100): Candle[] {
  return Array.from({ length: n }, (_, i) => { const c = price(i); const o = price(Math.max(0, i - 1)); return { t: 1_700_000_000 + i * H, o, h: Math.max(o, c) * 1.002, l: Math.min(o, c) * 0.998, c, v: vol(i) }; });
}

describe('growth scan (OLD GrowthScan)', () => {
  it('has the eleven kinds in the OLD order, ten bullish and the warning', () => {
    expect(KINDS.map((k) => k.id)).toEqual(['scan_trend', 'scan_breakout', 'scan_momentum', 'scan_golden', 'scan_squeeze', 'scan_volume', 'scan_divergence', 'scan_pullback', 'scan_structure', 'scan_harmonic', 'scan_trend_end']);
    expect(BULLISH).toHaveLength(10);
    expect(KINDS.find((k) => k.id === 'scan_trend_end')!.fa).toBe('پایان روند صعودی');
  });
  it('needs sixty bars', () => {
    expect(scanOf(bars(MIN_BARS - 1, (i) => 100 + i))).toBeNull();
    expect(scanOf(bars(MIN_BARS, (i) => 100 + i))).not.toBeNull();
  });
  it('a steady climb scores high, a steady fall low, both within 0–100', () => {
    const up = scanOf(bars(260, (i) => 100 * 1.004 ** i))!;
    const down = scanOf(bars(260, (i) => 100 * 0.996 ** i))!;
    expect(up.growth).toBeGreaterThan(down.growth);
    expect(up.growth).toBeGreaterThanOrEqual(30);
    expect(up.growth).toBeLessThanOrEqual(100);
    expect(down.growth).toBeGreaterThanOrEqual(0);
  });
  it('reads a breakout on volume on the newest bar', () => {
    const b = bars(120, (i) => (i < 119 ? 100 + Math.sin(i) * 0.5 : 104), (i) => (i === 119 ? 1000 : 100));
    const s = scanOf(b)!;
    expect(s.barsAgo.scan_breakout).toBe(0);
    expect(s.barsAgo.scan_volume).toBe(0);
  });
  it('bars ago never exceed the lookback', () => {
    const s = scanOf(bars(300, (i) => 100 + Math.sin(i / 7) * 10 + i * 0.05))!;
    for (const v of Object.values(s.barsAgo)) expect(v!).toBeLessThanOrEqual(LOOKBACK);
  });
  it('the hit rate is null under five completed signals', () => {
    const s = scanOf(bars(80, (i) => 100 + i))!;
    expect(s.samples).toBeLessThan(5);
    expect(s.reliability).toBeNull();
    const r = reliabilityOf(bars(400, (i) => 100 + Math.sin(i / 6) * 8));
    expect(r.samples).toBeGreaterThan(5);
    expect(r.winRate).toBeGreaterThanOrEqual(0);
  });
  it('matches, tags, studies and entrants follow the screen’s rule', () => {
    const s: Scan = { barsAgo: { scan_breakout: 2, scan_trend_end: 1, scan_golden: 12 }, growth: 70, reliability: null, samples: 0 };
    expect(matches(s, [], 5, null)).toBe(true);
    expect(matches(s, [], 5, 80)).toBe(false);
    expect(matches(s, ['scan_golden'], 5, null)).toBe(false);
    expect(matches(s, ['scan_golden', 'scan_breakout'], 5, 65)).toBe(true);
    const tags = tagsOf(s, 5);
    expect(tags.map((t) => t.kind.id)).toEqual(['scan_trend_end', 'scan_breakout']);
    expect(setupStudiesOf(tags)).toEqual(['supertrend', 'donchian']);
    expect(setupStudiesOf([])).toEqual(['supertrend', 'ema', 'adx']);
    expect(chartStudiesOf(['sma', 'bollinger', 'rsi', 'divergence', 'harmonics', 'sr'])).toEqual({ keys: ['ma', 'bb', 'rsi', 'srLevels'], divergence: true, harmonic: true });
    expect(entrants(['SOLUSDT', 'BTCUSDT', 'ETHUSDT'], ['ETHUSDT'])).toEqual(['BTCUSDT', 'SOLUSDT']);
  });
});

describe('backtest: growth_scan and the bar magnifier', () => {
  it('growth_scan is long only: in on a turn up, out on a turn down', () => {
    const b = bars(300, (i) => 100 + Math.sin(i / 15) * 15);
    const held = growthHeld(b, 20);
    expect(held.some(Boolean)).toBe(true);
    const set = strategySet(b, 'growth_scan', DEFAULT_PARAMS);
    expect(set.short.some(Boolean)).toBe(false);
    expect(set.long.filter(Boolean).length).toBeGreaterThan(0);
    const r = simulate(b, set, { ...DEFAULT_OPTIONS, direction: 'long' });
    expect(r.trades.every((t) => t.dir === 1)).toBe(true);
  });
  it('firstTouch walks open → nearer extreme → farther extreme, and gaps fill at the open', () => {
    // Opens near the high: up first → the target is touched before the stop.
    expect(firstTouch([{ t: 1, o: 100, h: 101, l: 95, c: 98, v: 0 }], true, 96, 100.5)).toEqual([1, 100.5]);
    // Opens near the low: down first → the stop.
    expect(firstTouch([{ t: 1, o: 100, h: 105, l: 99, c: 104, v: 0 }], true, 99.5, 104)).toEqual([1, 99.5]);
    expect(firstTouch([{ t: 2, o: 90, h: 91, l: 89, c: 90, v: 0 }], true, 95, 110)).toEqual([2, 90]);
    expect(firstTouch([{ t: 3, o: 100, h: 100.2, l: 99.8, c: 100, v: 0 }], true, 95, 110)).toBeNull();
  });
  it('the magnifier buckets finer bars by time and decides a bar where both levels were touched', () => {
    const coarse: Candle[] = [{ t: 0, o: 100, h: 100, l: 100, c: 100, v: 1 }, { t: 3600, o: 100, h: 112, l: 94, c: 100, v: 1 }, { t: 7200, o: 100, h: 100, l: 100, c: 100, v: 1 }];
    const fine: Candle[] = [{ t: 3600, o: 100, h: 112, l: 99, c: 111, v: 1 }, { t: 5400, o: 111, h: 111, l: 94, c: 95, v: 1 }];
    const mag = barMagnifier(coarse, fine);
    expect(mag(1)).toHaveLength(2);
    expect(mag(0)).toHaveLength(0);
    const set = { long: [true, false, false], short: [false, false, false] };
    const o = { ...DEFAULT_OPTIONS, direction: 'long' as const, stopPct: 5, targetPct: 10, feePct: 0 };
    expect(simulate(coarse, set, o).trades[0]!.reason).toBe('stop');
    expect(simulate(coarse, set, { ...o, magnifier: mag }).trades[0]!.reason).toBe('target');
  });
});

describe('market notifications (OLD MarketPulse)', () => {
  beforeEach(() => { localStorage.clear(); resetPulseRecord(); });
  it('thresholds: bitcoin 3, gold 1.5, majors 0.7, indices 1.5, other crypto 5', () => {
    expect(thresholdFor(BITCOIN)).toBe(3);
    expect(thresholdFor('XAUUSD')).toBe(1.5);
    expect(thresholdFor('EURUSD')).toBe(0.7);
    expect(thresholdFor('US30')).toBe(1.5);
    expect(thresholdFor('SOLUSDT')).toBe(5);
  });
  it('a rise past the threshold inside four hours is a move, and the window starts again', () => {
    const t0 = 1_000_000_000_000;
    let [s, m] = observe(BITCOIN, [], t0, 100, 3);
    expect(m).toBeNull();
    [s, m] = observe(BITCOIN, s, t0 + 3600e3, 102, 3);
    expect(m).toBeNull();
    [s, m] = observe(BITCOIN, s, t0 + 7200e3, 103.2, 3);
    expect(m?.percent).toBeCloseTo(3.2, 5);
    expect(m?.windowMs).toBe(7200e3);
    expect(s).toHaveLength(1);
    // A low older than the window does not count.
    const [, late] = observe(BITCOIN, [{ at: t0, price: 90 }], t0 + WINDOW_MS + 1, 100, 3);
    expect(late).toBeNull();
    const [, fall] = observe('EURUSD', [{ at: t0, price: 1.1 }], t0 + 60e3, 1.09, 0.7);
    expect(fall!.percent).toBeLessThan(0);
  });
  it('quiet from 23:00 to 08:00; the summary from 09:00 to 12:00, once a day; the offer once a day until on', () => {
    expect(quiet(23 * 60)).toBe(true);
    expect(quiet(7 * 60 + 59)).toBe(true);
    expect(quiet(8 * 60)).toBe(false);
    expect(digestDue(9 * 60, 'd', null)).toBe(true);
    expect(digestDue(12 * 60, 'd', null)).toBe(false);
    expect(digestDue(10 * 60, 'd', 'd')).toBe(false);
    expect(shouldOffer(false, false, null, 'd')).toBe(true);
    expect(shouldOffer(false, false, 'd', 'd')).toBe(false);
    expect(shouldOffer(true, false, null, 'd')).toBe(false);
    expect(shouldOffer(false, true, null, 'd')).toBe(false);
  });
  it('wording: Persian prose counts, Latin figures', () => {
    expect(spanText(2 * 3600e3, false)).toBe('در ۲ ساعت گذشته');
    expect(spanText(5 * 60e3, true)).toBe('over the last 15 minutes');
    const t = moveText({ symbol: BITCOIN, percent: 3.24, from: 62000, to: 64210, windowMs: 2 * 3600e3 }, false);
    expect(t.title).toMatch(/\+3\.24% بالا رفت$/);
    expect(t.body).toMatch(/^اکنون 64,210(\.\d+)?، در ۲ ساعت گذشته$/);
  });
  it('the summary’s risers: the most traded coins with a mark, the top three that rose', () => {
    const tk = (symbol: string, changePct: number, volume: number): Ticker => ({ symbol, last: 1, change: 0, changePct, volume, ts: 0 });
    const out = moversOf([tk('AUSDT', 9, 10), tk('BUSDT', 4, 10), tk('CUSDT', 2, 10), tk('DUSDT', 1, 10), tk('NOLOGOUSDT', 50, 99), tk('XAUUSD', 30, 99), tk('EUSDT', -3, 10)]);
    expect(out.map((m) => m.symbol)).toEqual(['AUSDT', 'BUSDT', 'CUSDT']);
  });
  it('the brief: counts in Persian, the mover in Latin, a quiet night said plainly, nothing about nothing', () => {
    expect(briefOf([{ symbol: 'BTCUSDT', changePercent: null }], false)).toBeNull();
    const b = briefOf([{ symbol: 'BTCUSDT', changePercent: 2.4 }, { symbol: 'ETHUSDT', changePercent: -1 }, { symbol: 'XAUUSD', changePercent: 0.5 }], false)!;
    expect(b.headline).toBe('۲ بالا، ۱ پایین');
    expect(b.lines[0]).toBe('BTCUSDT 2.40٪ بالا رفت.');
    expect(b.mover).toBe('BTCUSDT');
    const q = briefOf([{ symbol: 'BTCUSDT', changePercent: 0.05 }, { symbol: 'ETHUSDT', changePercent: -0.02 }], true)!;
    expect(q.headline).toBe('A quiet night across your 2 markets');
    expect(q.mover).toBeNull();
  });
  it('never more than six a day, and nothing at all without permission', () => {
    const noon = new Date(2026, 9, 31, 12, 0).getTime();
    expect(post('market_move', 'x', 'y', { now: noon })).toBe(false);
    const sent: string[] = [];
    class FakeNotification { static permission = 'granted'; onclick: (() => void) | null = null; constructor(title: string) { sent.push(title); } close() { /* */ } }
    vi.stubGlobal('Notification', FakeNotification);
    try {
      for (let i = 0; i < 10 && canSpend(noon); i++) post('market_move', `m${i}`, 'b', { now: noon });
      expect(sent).toHaveLength(DAILY_CAP);
      expect(sentToday(noon)).toBe(DAILY_CAP);
      expect(canSpend(noon)).toBe(false);
      expect(canSpend(new Date(2026, 10, 1, 12, 0).getTime())).toBe(true);
      expect(canSpend(new Date(2026, 10, 1, 23, 30).getTime())).toBe(false);
    } finally { vi.unstubAllGlobals(); }
  });
});

describe('scan watch and chartable lists', () => {
  it('the body lists five and counts the rest in prose', () => {
    expect(watchBody(['A', 'B'], false)).toBe('A، B');
    expect(watchBody(['A', 'B', 'C', 'D', 'E', 'F', 'G'], false)).toBe('A، B، C، D، E و ۲ نماد دیگر');
    expect(watchBody(['A', 'B', 'C', 'D', 'E', 'F', 'G'], true)).toBe('A، B، C، D، E and 2 more');
  });
  it('only closed bars are scanned', () => {
    const b = bars(3, (i) => 100 + i);
    expect(closedOnly(b, '60', b[2]!.t + 10)).toHaveLength(2);
  });
  it('a market that could not be read keeps last pass’s standing', async () => {
    const w: ScanWatch = { id: 'w', scanIds: [], within: 5, minGrowth: null, tf: '60', symbols: ['AUSDT', 'BUSDT', 'CUSDT'], known: ['BUSDT'] };
    const climb = bars(120, (i) => 100 * 1.003 ** i).map((x) => ({ ...x, t: x.t - 10 * 86400 * 365 }));
    const matched = await scanWatch(w, async (s) => (s === 'AUSDT' ? climb : s === 'BUSDT' ? null : bars(10, () => 1)));
    expect(matched).toEqual(['AUSDT', 'BUSDT']);
  });
  it('chartable: an unanswered platform filters nothing; dead heat-map tiles are dropped', () => {
    expect(isChartable('BTCUSDT', null)).toBe(true);
    expect(isChartable('PEPEUSDT', { crypto: new Set(['BTCUSDT']), forex: null })).toBe(false);
    expect(isChartable('XAUUSD', { crypto: new Set(['BTCUSDT']), forex: null })).toBe(true);
    expect(deadTile({ changePct: 0, volume: 0 })).toBe(true);
    expect(deadTile({ changePct: 0, volume: undefined })).toBe(true);
    expect(deadTile({ changePct: 0.1, volume: 0 })).toBe(false);
    expect(deadTile({ changePct: 0, volume: 5 })).toBe(false);
  });
});
