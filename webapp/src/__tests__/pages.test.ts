import { beforeEach, describe, expect, it } from 'vitest';
import { runBacktest, signals, DEFAULT_OPTIONS, DEFAULT_PARAMS } from '../pages/backtestEngine';
import { calc, pipSizeOf, contractOf } from '../pages/ToolsPage';
import { convertPine, unpack } from '../pages/ScriptPage';
import { PAGES, pageFromPath } from '../pages/registry';
import { parseNum } from '../pages/kit';
import { usePaper, fillPrice, DEFAULT_RULES } from '../state/paper';
import { useJournal, pnlFor } from '../state/journal';
import { inQuietHours } from '../state/notify';
import { readPost, listOf, communityKey } from '../data/community';
import { archive, restore } from '../pages/ProfilePage';
import type { Candle } from '../data/types';

const bars = (closes: number[]): Candle[] => closes.map((c, i) => ({ t: 1_700_000_000 + i * 3600, o: i ? closes[i - 1]! : c, h: Math.max(c, i ? closes[i - 1]! : c) + 0.5, l: Math.min(c, i ? closes[i - 1]! : c) - 0.5, c, v: 10 }));

describe('pages registry', () => {
  it('every page has a unique id and path', () => {
    expect(new Set(PAGES.map((p) => p.id)).size).toBe(PAGES.length);
    expect(new Set(PAGES.map((p) => p.path)).size).toBe(PAGES.length);
  });
  it('reads the longest path first, with a parameter', () => {
    expect(pageFromPath('academy/lesson/intro-1')).toEqual({ id: 'lesson', param: 'intro-1' });
    expect(pageFromPath('academy')).toEqual({ id: 'academy', param: null });
    expect(pageFromPath('ai/vision')).toEqual({ id: 'vision', param: null });
    expect(pageFromPath('ai')).toEqual({ id: 'ai', param: null });
    expect(pageFromPath('market/news')).toEqual({ id: 'news', param: null });
    expect(pageFromPath('signal/tradeyar-42')).toEqual({ id: 'signal', param: 'tradeyar-42' });
    expect(pageFromPath('s/abc123')).toEqual({ id: 'script', param: 'abc123' });
  });
  it('a chart address is not a page', () => {
    expect(pageFromPath('BTCUSDT/4h')).toBeNull();
    expect(pageFromPath('XAUUSD')).toBeNull();
  });
  it('refuses a parameter with odd characters', () => {
    expect(pageFromPath('community/post/<script>')).toBeNull();
  });
});

describe('numbers typed by a reader', () => {
  it('reads Persian digits and separators', () => {
    expect(parseNum('۱۲٬۳۴۵٫۶')).toBe(12345.6);
    expect(parseNum('1,234.5')).toBe(1234.5);
    expect(parseNum('')).toBeNull();
    expect(parseNum('abc')).toBeNull();
  });
});

describe('trader tools (TraderToolsCalculator parity)', () => {
  it('risk and position size', () => {
    expect(calc.risk(10_000, 1)).toEqual({ risk: 100, after: 9900 });
    expect(calc.position(100, 20, 10).lots).toBeCloseTo(0.5);
  });
  it('risk to reward checks the geometry', () => {
    expect(calc.rr(100, 95, 110, 'long')!.ratio).toBeCloseTo(2);
    expect(calc.rr(100, 105, 90, 'short')!.ratio).toBeCloseTo(2);
    expect(calc.rr(100, 105, 110, 'long')).toBeNull();
  });
  it('pips, profit, crypto, compound, drawdown', () => {
    const p = calc.pips(1.1, 1.102, 1, 0.0001, 10, 'long');
    expect(p.pips).toBeCloseTo(20); expect(p.pnl).toBeCloseTo(200);
    expect(calc.profit(2000, 2010, 1, 100, 'long').pnl).toBeCloseTo(1000);
    const c = calc.crypto(100, 110, 1, 0.1, 'long');
    expect(c.gross).toBeCloseTo(10); expect(c.fees).toBeCloseTo(0.21); expect(c.net).toBeCloseTo(9.79);
    expect(calc.compound(1000, 10, 2).end).toBeCloseTo(1210);
    const d = calc.drawdown(10_000, 10, 2);
    expect(d.end).toBeCloseTo(8100); expect(d.recovery).toBeCloseTo(23.4568, 3);
  });
  it('pip size and contract by instrument', () => {
    expect(pipSizeOf('EURUSD')).toBe(0.0001); expect(pipSizeOf('USDJPY')).toBe(0.01); expect(pipSizeOf('XAUUSD')).toBe(0.1);
    expect(contractOf('XAUUSD')).toBe(100); expect(contractOf('EURUSD')).toBe(100_000);
  });
});

describe('backtest engine', () => {
  const series = bars([...Array.from({ length: 40 }, (_, i) => 100 - i * 0.5), ...Array.from({ length: 60 }, (_, i) => 80 + i), ...Array.from({ length: 40 }, (_, i) => 140 - i)]);
  it('a moving-average cross trades the swing and fills on the next open', () => {
    const sig = signals(series, 'ma_cross', { ...DEFAULT_PARAMS, fast: 3, slow: 8, maType: 'sma' });
    const r = runBacktest(series, sig, { ...DEFAULT_OPTIONS, feePct: 0 });
    expect(r.trades.length).toBeGreaterThan(0);
    const first = r.trades[0]!;
    const at = series.findIndex((b) => b.t === first.entryT);
    expect(sig[at - 1]).not.toBe(0);
    expect(first.entry).toBe(series[at]!.o);
    expect(r.equity.length).toBe(series.length - 1);
  });
  it('long-only takes no short and a stop caps the loss', () => {
    const sig = signals(series, 'breakout', { ...DEFAULT_PARAMS, donchian: 5 });
    const r = runBacktest(series, sig, { ...DEFAULT_OPTIONS, direction: 'long', stopPct: 1, feePct: 0 });
    expect(r.trades.every((t) => t.dir === 1)).toBe(true);
    for (const t of r.trades.filter((x) => x.reason === 'stop')) expect(t.pct).toBeGreaterThanOrEqual(-1.0001);
  });
  it('every strategy runs without throwing', () => {
    for (const id of ['ma_cross', 'rsi', 'macd', 'bollinger', 'breakout', 'supertrend'] as const) {
      const s = signals(series, id, DEFAULT_PARAMS);
      expect(s.length).toBe(series.length);
      expect(() => runBacktest(series, s, DEFAULT_OPTIONS)).not.toThrow();
    }
  });
});

describe('paper book (PaperBook rules)', () => {
  beforeEach(() => { localStorage.clear(); usePaper.setState({ balance: 10_000, positions: [], history: [], orders: [], rules: DEFAULT_RULES, lastError: null }); });
  it('a market fill pays the spread, slippage and fee', () => {
    const px = fillPrice('long', 100, DEFAULT_RULES);
    expect(px).toBeCloseTo(100 * (1 + (0.02 + 0.02) / 100));
    usePaper.getState().open('BTCUSDT', 'long', 1, 100);
    const p = usePaper.getState().positions[0]!;
    expect(p.entry).toBeCloseTo(px);
    expect(usePaper.getState().balance).toBeCloseTo(10_000 - px * 0.0005);
  });
  it('refuses an order beyond the margin', () => {
    expect(usePaper.getState().open('BTCUSDT', 'long', 1000, 100)).toBeNull();
    expect(usePaper.getState().lastError).toBe('margin');
  });
  it('a limit order rests, then fills at its price as maker', () => {
    usePaper.getState().place({ symbol: 'ETHUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 95 }, 100);
    expect(usePaper.getState().orders).toHaveLength(1);
    usePaper.getState().mark('ETHUSDT', 96);
    expect(usePaper.getState().positions).toHaveLength(0);
    usePaper.getState().mark('ETHUSDT', 94.5);
    expect(usePaper.getState().orders).toHaveLength(0);
    expect(usePaper.getState().positions[0]!.entry).toBe(95);
  });
  it('a stop loss closes the position by itself', () => {
    usePaper.getState().open('SOLUSDT', 'long', 1, 100, 95);
    usePaper.getState().mark('SOLUSDT', 94);
    expect(usePaper.getState().positions).toHaveLength(0);
    expect(usePaper.getState().history[0]!.reason).toBe('sl');
    expect(usePaper.getState().history[0]!.pnl).toBeLessThan(0);
  });
  it('a trailing stop follows the price and triggers on the pullback', () => {
    usePaper.getState().place({ symbol: 'XRPUSDT', side: 'short', type: 'trailing', qty: 1, trailPercent: 2 }, 100);
    usePaper.getState().mark('XRPUSDT', 110);
    usePaper.getState().mark('XRPUSDT', 108.5);
    expect(usePaper.getState().positions).toHaveLength(0);
    usePaper.getState().mark('XRPUSDT', 107.7);
    expect(usePaper.getState().positions).toHaveLength(1);
  });
  it('rules are clamped', () => {
    usePaper.getState().setRules({ leverage: 9999, takerFeePercent: -1 });
    expect(usePaper.getState().rules.leverage).toBe(500);
    expect(usePaper.getState().rules.takerFeePercent).toBe(0);
  });
});

describe('journal', () => {
  beforeEach(() => { localStorage.clear(); useJournal.setState({ entries: [], error: null }); });
  it('computes the result from the prices when not typed', () => {
    expect(pnlFor({ buy: true, entry: 100, exit: 110, size: 2 })).toBe(20);
    expect(pnlFor({ buy: false, entry: 100, exit: 110, size: 2 })).toBe(-20);
    expect(pnlFor({ buy: true, entry: 100, exit: null, size: 2 })).toBeNull();
  });
  it('imports only well-formed entries and keeps ids unique', () => {
    useJournal.getState().upsert({ symbol: 'BTCUSDT', buy: true, entry: 1, exit: 2, size: 1, pnl: null, emotion: 'آرام', note: '', lesson: '', tags: [] });
    const n = useJournal.getState().importAll([{ symbol: 'ETHUSDT', entry: 5, buy: true, exit: null, size: 1, tags: ['ترند'] }, { nope: true }, ...useJournal.getState().entries]);
    expect(n).toBe(2);
    expect(useJournal.getState().entries).toHaveLength(2);
  });
});

describe('script sharing and Pine paste', () => {
  it('rewrites Pine operators outside strings only', () => {
    const r = convertPine('//@version=5\nplot(close > open and volume > 0 ? 1 : 0, title="Long and short")');
    expect(r.code).toContain('&&');
    expect(r.code).toContain('"Long and short"');
    expect(r.code).not.toContain('@version');
    expect(r.fixes.length).toBeGreaterThan(0);
  });
  it('an unpacked raw link gives the script back', async () => {
    const raw = 'r' + btoa('plot(close)').replace(/=+$/, '');
    expect(await unpack(raw)).toBe('plot(close)');
    expect(await unpack('z!!!')).toBeNull();
  });
});

describe('notifications quiet hours', () => {
  it('spans midnight', () => {
    const l = { sound: true, quiet: true, quietFrom: '23:00', quietTo: '07:00' };
    expect(inQuietHours(new Date(2026, 0, 1, 23, 30), l)).toBe(true);
    expect(inQuietHours(new Date(2026, 0, 1, 6, 59), l)).toBe(true);
    expect(inQuietHours(new Date(2026, 0, 1, 12, 0), l)).toBe(false);
    expect(inQuietHours(new Date(2026, 0, 1, 23, 30), { ...l, quiet: false })).toBe(false);
  });
});

describe('community wire', () => {
  it('reads a post leniently and finds the list under any key', () => {
    const p = readPost({ id: 7, body: 'سلام دنیا', username: 'ali', like_count: 3, status: 'pending', reactions: { '🔥': 2 } });
    expect(p).toMatchObject({ id: '7', content: 'سلام دنیا', author: 'ali', likes: 3, pending: true, category: 'عمومی' });
    expect(listOf({ results: [{ id: 1 }] })).toHaveLength(1);
    expect(listOf({ nothing: 1 })).toEqual([]);
  });
  it('the install key is 64 hex and stable', () => {
    localStorage.clear();
    const k = communityKey();
    expect(k).toMatch(/^[0-9a-f]{64}$/);
    expect(communityKey()).toBe(k);
  });
});

describe('browser archive', () => {
  it('exports pc.* keys except the sign-in, and restores them', () => {
    localStorage.clear();
    localStorage.setItem('pc.watchlist.v1', '["BTCUSDT"]');
    localStorage.setItem('pc.sessions.v1', '{"secret":1}');
    localStorage.setItem('other', 'x');
    const a = archive();
    expect(Object.keys(a)).toEqual(['pc.watchlist.v1']);
    localStorage.clear();
    expect(restore({ ...a, 'pc.sessions.v1': 'evil', bad: 'x' })).toBe(1);
    expect(localStorage.getItem('pc.sessions.v1')).toBeNull();
    expect(localStorage.getItem('pc.watchlist.v1')).toBe('["BTCUSDT"]');
  });
});
