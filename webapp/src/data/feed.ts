// The chart's feed: history from candles.ts, the forming bar built from live prices and corrected
// against the backend's own last bars every so often (the phone's live edge, CandleBudget-aware).
// A spread or formula (BTCUSDT/ETHUSDT, XAUUSD*2, (EURUSD+GBPUSD)/2) is built here from its legs,
// bar by bar on their common times, and stays live as any leg moves.
import type { ChartFeed } from '../chart/ChartView';
import type { Candle } from './types';
import { bucketOf, loadEdge, loadHistory, type Fold } from './candles';
import { intervalSeconds, parseIntervalId } from './intervals';
import { onPrice, setFocus } from './prices';
import { backendOf, exprLegs, instrumentOf } from './symbols';
import { pageVisible } from './http';
import { computeSpread, evalAst, parseSymbolExpr, type ExprNode } from '../engine/symbolExpr.js';
import { useMarket } from '../state/market';
import { useAlerts } from '../state/alerts';
import type { Ticker } from './types';

const longBars = (iv: string) => intervalSeconds(iv) >= 7 * 86400;

/** How a live tick finds its bar: by the clock for minutes and hours, by the calendar beyond. */
export function liveFold(interval: string): Fold {
  const u = parseIntervalId(interval)?.unit ?? 'm';
  return u === 'D' ? 'days' : u === 'W' ? 'week' : u === 'M' ? 'month' : 'minutes';
}

/** The legs' last bars, kept from the latest history so a spread's live bar starts from them. */
const legTails = new Map<string, Record<string, Candle>>();

async function spreadHistory(expr: string, legs: string[], interval: string, before?: number) {
  const ast = parseSymbolExpr(expr)!.ast as ExprNode;
  const pages = await Promise.all(legs.map((s) => loadHistory(s, interval, before, before ? 'bulk' : 'reader')));
  const bySym: Record<string, Candle[]> = {};
  legs.forEach((s, i) => { bySym[s] = pages[i]!.bars; });
  const bars = computeSpread(bySym, ast) as Candle[];
  if (!before) {
    const tails: Record<string, Candle> = {};
    legs.forEach((s) => { const b = bySym[s]!; if (b.length) tails[s] = b[b.length - 1]!; });
    legTails.set(`${expr}|${interval}`, tails);
    const last = bars[bars.length - 1];
    if (last) useMarket.getState().putInstruments([instrumentOf(expr, last.c)]);
    watchExpression(expr);
  }
  return { bars, more: pages.every((p) => p.more) && bars.length > 0 };
}

// ── A spread's own ticker ───────────────────────────────────────────────────────────────────────
// Evaluated from its legs' tickers whenever one moves, so the tab title, the price precision and
// the alerts set on a spread work as they do on a plain symbol. A spread has no artwork, so its
// ticker never reaches a list.
const watched = new Set<string>();

function exprTicker(expr: string, tickers: Record<string, Ticker>): Ticker | null {
  const parsed = parseSymbolExpr(expr);
  const legs = exprLegs(expr);
  if (!parsed || !legs) return null;
  const last: Record<string, number> = {};
  const prev: Record<string, number> = {};
  let ts = 0;
  for (const s of legs) {
    const tk = tickers[s];
    if (!tk) return null;
    last[s] = tk.last;
    prev[s] = tk.prevClose ?? tk.last - tk.change;
    ts = Math.max(ts, tk.ts);
  }
  const now = evalAst(parsed.ast as ExprNode, last);
  const before = evalAst(parsed.ast as ExprNode, prev);
  if (now == null) return null;
  const change = before != null ? now - before : 0;
  return { symbol: expr, last: now, change, changePct: before ? (change / Math.abs(before)) * 100 : 0, prevClose: before ?? undefined, ts };
}

function refreshExpressions(tickers: Record<string, Ticker>): void {
  const exprs = new Set(watched);
  for (const a of useAlerts.getState().list) if (a.active && exprLegs(a.symbol)) exprs.add(a.symbol);
  const out: Ticker[] = [];
  for (const e of exprs) {
    const tk = exprTicker(e, tickers);
    if (tk && tickers[e]?.last !== tk.last) out.push(tk);
  }
  if (out.length) useMarket.getState().put(out);
}

export function watchExpression(expr: string): void {
  watched.add(expr);
  refreshExpressions(useMarket.getState().tickers);
}
// Spreads with alerts on them are priced from the first tick, chart open or not.
useMarket.subscribe((m, p) => { if (m.tickers !== p.tickers) refreshExpressions(m.tickers); });

function plainLive(symbol: string, interval: string, from: Candle | undefined, onBar: (bar: Candle) => void): () => void {
  let bar: Candle | null = from ? { ...from } : null;
  let alive = true;
  const fold = liveFold(interval);

  const seed = (bars: Candle[]) => {
    const tail = bars[bars.length - 1];
    if (!tail) return;
    if (!bar || tail.t >= bar.t) { bar = { ...tail }; onBar(bar); }
  };

  const offPrice = onPrice(symbol, (price, tsMs) => {
    if (!bar) return;
    const t = bucketOf(Math.floor(tsMs / 1000), interval, fold);
    if (t > bar.t) bar = { t, o: bar.c, h: price, l: price, c: price, v: 0 };
    else { bar = { ...bar, h: Math.max(bar.h, price), l: Math.min(bar.l, price), c: price }; }
    onBar(bar);
  });

  // The edge: crypto every interval/12 (3–30 s); forex at most every 30 s to stay inside the
  // guest budget's background share.
  const crypto = backendOf(symbol) === 'tradeyar';
  const every = longBars(interval) ? 60_000 : Math.min(30_000, Math.max(crypto ? 3000 : 30_000, (intervalSeconds(interval) * 1000) / 12));
  const tick = async () => {
    if (!alive) return;
    if (pageVisible()) {
      try { seed(await loadEdge(symbol, interval)); } catch { /* the next tick tries again */ }
    }
    if (alive) timer = setTimeout(tick, every);
  };
  let timer = setTimeout(tick, every);

  return () => { alive = false; clearTimeout(timer); offPrice(); };
}

/** A spread's forming bar: every leg live, the expression evaluated on their latest bars. */
function spreadLive(expr: string, legs: string[], interval: string, from: Candle | undefined, onBar: (bar: Candle) => void): () => void {
  const ast = parseSymbolExpr(expr)!.ast as ExprNode;
  const tails: Record<string, Candle> = { ...(legTails.get(`${expr}|${interval}`) ?? {}) };
  let lastT = from?.t ?? 0;
  const emit = () => {
    if (!legs.every((s) => tails[s])) return;
    const t = Math.max(...legs.map((s) => tails[s]!.t));
    if (t < lastT) return;
    // A leg that has not ticked in the new bar yet stands at its last close.
    const bySym: Record<string, Candle[]> = {};
    for (const s of legs) {
      const b = tails[s]!;
      bySym[s] = [b.t === t ? b : { t, o: b.c, h: b.c, l: b.c, c: b.c, v: 0 }];
    }
    const out = computeSpread(bySym, ast)[0];
    if (!out) return;
    lastT = t;
    onBar({ t: out.t, o: out.o, h: out.h, l: out.l, c: out.c, v: 0 });
  };
  const offs = legs.map((s) => plainLive(s, interval, tails[s], (bar) => { tails[s] = bar; emit(); }));
  setFocus(legs[0]!);
  return () => offs.forEach((f) => f());
}

export const feed: ChartFeed = {
  async history(symbol, interval, before) {
    const legs = exprLegs(symbol);
    if (legs) return spreadHistory(symbol, legs, interval, before);
    if (!before) setFocus(symbol);
    return loadHistory(symbol, interval, before, before ? 'bulk' : 'reader');
  },

  live(symbol, interval, from, onBar) {
    const legs = exprLegs(symbol);
    if (legs) return spreadLive(symbol, legs, interval, from, onBar);
    return plainLive(symbol, interval, from, onBar);
  },
};

// ── Regular trading hours (TradingView's RTH / ETH) ─────────────────────────────────────────────
// A cash index's own session, in its exchange's time; the CFD trades nearly round the clock, and
// "Regular" keeps only the bars of the cash session on intraday intervals.
interface Session { tz: string; open: number; close: number }
const SESSIONS: Record<string, Session> = {
  US30: { tz: 'America/New_York', open: 9 * 60 + 30, close: 16 * 60 },
  US500: { tz: 'America/New_York', open: 9 * 60 + 30, close: 16 * 60 },
  NAS100: { tz: 'America/New_York', open: 9 * 60 + 30, close: 16 * 60 },
  US100: { tz: 'America/New_York', open: 9 * 60 + 30, close: 16 * 60 },
  DE40: { tz: 'Europe/Berlin', open: 9 * 60, close: 17 * 60 + 30 },
  GER40: { tz: 'Europe/Berlin', open: 9 * 60, close: 17 * 60 + 30 },
  UK100: { tz: 'Europe/London', open: 8 * 60, close: 16 * 60 + 30 },
  JPN225: { tz: 'Asia/Tokyo', open: 9 * 60, close: 15 * 60 },
};

/** The symbol's regular session, when it has one apart from the extended hours. */
export function regularSession(symbol: string): Session | null {
  return SESSIONS[symbol] ?? null;
}

const minuteOfDay = new Map<string, Intl.DateTimeFormat>();
function minutesIn(tz: string, t: number): number {
  let f = minuteOfDay.get(tz);
  if (!f) { f = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false }); minuteOfDay.set(tz, f); }
  const [h, m] = f.format(new Date(t * 1000)).split(':').map(Number);
  return ((h ?? 0) % 24) * 60 + (m ?? 0);
}

/** Whether a bar opening at `t` lies inside the regular session (always true where none applies). */
export function inRegularHours(symbol: string, interval: string, t: number): boolean {
  const s = regularSession(symbol);
  if (!s || intervalSeconds(interval) >= 86400) return true;
  const m = minutesIn(s.tz, t);
  return m >= s.open && m < s.close;
}

const regularFeed: ChartFeed = {
  async history(symbol, interval, before) {
    const page = await feed.history(symbol, interval, before);
    if (!regularSession(symbol) || intervalSeconds(interval) >= 86400) return page;
    return { ...page, bars: page.bars.filter((b) => inRegularHours(symbol, interval, b.t)) };
  },
  live(symbol, interval, from, onBar) {
    return feed.live(symbol, interval, from, (bar) => { if (inRegularHours(symbol, interval, bar.t)) onBar(bar); });
  },
};

/** The feed for the chosen session: extended hours (everything) or regular hours only. */
export function feedFor(session: 'regular' | 'extended'): ChartFeed {
  return session === 'regular' ? regularFeed : feed;
}
