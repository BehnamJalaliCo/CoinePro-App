// The strategy tester's engine: built-in strategies (the app's backtest presets) and NamaScript
// strategies (their entry and exit signals) over a series of bars, with TradingView's Properties:
// initial capital, order size (percent of equity, contracts or cash), pyramiding, commission (percent
// and per order), slippage, fill on the bar's close or the next bar's open, a date range, a stop and
// a target. Signals are read on a bar's close and, by default, filled on the next bar's open, so no
// bar sees its future. The report carries TradingView's Overview, Performance and List of trades
// figures, the equity, drawdown and buy-and-hold curves; an optimizer and a walk-forward test sit
// on top.
import { atr, bollinger, ema, macd, rsi, sma } from '../engine/indicators.js';
import type { Candle } from '../data/types';

export type StrategyId = 'ma_cross' | 'rsi' | 'macd' | 'bollinger' | 'breakout' | 'supertrend';
export interface StrategyParams { fast: number; slow: number; maType: 'sma' | 'ema'; rsiLen: number; rsiLow: number; rsiHigh: number; bbLen: number; bbMult: number; donchian: number; stLen: number; stMult: number }
export const DEFAULT_PARAMS: StrategyParams = { fast: 9, slow: 21, maType: 'ema', rsiLen: 14, rsiLow: 30, rsiHigh: 70, bbLen: 20, bbMult: 2, donchian: 20, stLen: 10, stMult: 3 };

export interface BacktestOptions {
  direction: 'long' | 'short' | 'both'; stopPct: number; targetPct: number; feePct: number; capital: number; riskShare: number;
  /** How each order is sized: a share of equity (riskShare %), a number of contracts, or an amount of cash. */
  sizeMode?: 'percent' | 'qty' | 'cash';
  qty?: number;
  cash?: number;
  /** Entries allowed in the same direction (TradingView's pyramiding); 1 means one position. */
  pyramiding?: number;
  /** Slippage against the trader on every fill, in percent of the price. */
  slippagePct?: number;
  /** Commission per order in cash, on top of feePct. */
  feeCash?: number;
  /** Fill on the signal bar's close instead of the next bar's open. */
  fillOnClose?: boolean;
  /** Only trades inside this range of bar times (seconds). */
  from?: number | null;
  to?: number | null;
}
export const DEFAULT_OPTIONS: BacktestOptions = { direction: 'both', stopPct: 0, targetPct: 0, feePct: 0.05, capital: 10_000, riskShare: 100 };

export type ExitReason = 'signal' | 'stop' | 'target' | 'end' | 'exit';
export interface BtTrade {
  dir: 1 | -1; entryT: number; entry: number; exitT: number; exit: number; pnl: number; pct: number; reason: ExitReason;
  qty?: number; bars?: number; runUp?: number; drawdown?: number; cum?: number; fee?: number; entryIdx?: number; exitIdx?: number;
}
export interface SideStats { trades: number; wins: number; net: number; win: number; pf: number; grossProfit: number; grossLoss: number }
export interface BtResult {
  trades: BtTrade[]; equity: { t: number; e: number }[]; net: number; netPct: number; win: number; pf: number; dd: number; ddPct: number; avg: number; sharpe: number; buyHold: number; exposure: number;
  grossProfit?: number; grossLoss?: number; fees?: number; avgWin?: number; avgLoss?: number; ratio?: number; largestWin?: number; largestLoss?: number;
  expectancy?: number; winStreak?: number; lossStreak?: number; maxRunUp?: number; maxRunUpPct?: number; sortino?: number;
  avgBars?: number; avgBarsWin?: number; avgBarsLoss?: number; wins?: number; losses?: number; long?: SideStats; short?: SideStats;
  drawdown?: { t: number; dd: number }[]; buyHoldCurve?: { t: number; e: number }[]; openPnl?: number; capital?: number;
}

/** +1 enter long / −1 enter short / 0 nothing, per bar, read at that bar's close. */
export function signals(bars: Candle[], id: StrategyId, p: StrategyParams): number[] {
  const c = bars.map((b) => b.c); const h = bars.map((b) => b.h); const l = bars.map((b) => b.l);
  const out = new Array<number>(bars.length).fill(0);
  const cross = (a: (number | null)[], b: (number | null)[], i: number) => {
    const a0 = a[i - 1]; const b0 = b[i - 1]; const a1 = a[i]; const b1 = b[i];
    if (a0 == null || b0 == null || a1 == null || b1 == null) return 0;
    return a0 <= b0 && a1 > b1 ? 1 : a0 >= b0 && a1 < b1 ? -1 : 0;
  };
  if (id === 'ma_cross') {
    const f = p.maType === 'ema' ? ema(c, p.fast) : sma(c, p.fast); const s = p.maType === 'ema' ? ema(c, p.slow) : sma(c, p.slow);
    for (let i = 1; i < bars.length; i++) out[i] = cross(f, s, i);
  } else if (id === 'rsi') {
    const r = rsi(c, p.rsiLen);
    for (let i = 1; i < bars.length; i++) {
      const a = r[i - 1]; const b = r[i];
      if (a == null || b == null) continue;
      if (a < p.rsiLow && b >= p.rsiLow) out[i] = 1; else if (a > p.rsiHigh && b <= p.rsiHigh) out[i] = -1;
    }
  } else if (id === 'macd') {
    const m = macd(c, 12, 26, 9);
    for (let i = 1; i < bars.length; i++) out[i] = cross(m.macd, m.signal, i);
  } else if (id === 'bollinger') {
    const bb = bollinger(c, p.bbLen, p.bbMult);
    for (let i = 1; i < bars.length; i++) {
      const lo = bb.lower[i]; const up = bb.upper[i]; const lo0 = bb.lower[i - 1]; const up0 = bb.upper[i - 1];
      if (lo == null || up == null || lo0 == null || up0 == null) continue;
      if (c[i - 1]! < lo0 && c[i]! >= lo) out[i] = 1; else if (c[i - 1]! > up0 && c[i]! <= up) out[i] = -1;
    }
  } else if (id === 'breakout') {
    for (let i = p.donchian + 1; i < bars.length; i++) {
      let hi = -Infinity; let lo = Infinity;
      for (let k = i - p.donchian; k < i; k++) { hi = Math.max(hi, h[k]!); lo = Math.min(lo, l[k]!); }
      out[i] = c[i]! > hi ? 1 : c[i]! < lo ? -1 : 0;
    }
  } else if (id === 'supertrend') {
    const a = atr(h, l, c, p.stLen);
    let up = 0; let dn = 0; let trend = 1;
    for (let i = 0; i < bars.length; i++) {
      const av = a[i];
      if (av == null) continue;
      const mid = (h[i]! + l[i]!) / 2;
      const bu = mid - p.stMult * av; const bd = mid + p.stMult * av;
      up = i > 0 && c[i - 1]! > up ? Math.max(bu, up) : bu;
      dn = i > 0 && c[i - 1]! < dn ? Math.min(bd, dn) : bd;
      const prev = trend;
      if (c[i]! > dn) trend = 1; else if (c[i]! < up) trend = -1;
      if (trend !== prev) out[i] = trend;
    }
  }
  return out;
}

/** Entry and exit signals per bar: a script's `strategy.entry/exit/close`, or two alert conditions. */
export interface SignalSet { long: boolean[]; short: boolean[]; exitL?: boolean[]; exitS?: boolean[] }

/** A +1/−1 signal array as a signal set (an opposite entry closes and reverses). */
export function fromSigns(sig: number[]): SignalSet {
  return { long: sig.map((v) => v === 1), short: sig.map((v) => v === -1) };
}

/** Signal indices (a script result's `signals`) as a signal set over n bars. */
export function fromIndices(n: number, s: { long?: number[]; short?: number[]; exitL?: number[]; exitS?: number[] }): SignalSet {
  const arr = (ix?: number[]) => { const a = new Array<boolean>(n).fill(false); for (const i of ix ?? []) if (i >= 0 && i < n) a[i] = true; return a; };
  return { long: arr(s.long), short: arr(s.short), exitL: arr(s.exitL), exitS: arr(s.exitS) };
}

/** Two alert conditions as a long-only strategy: the first enters, the second exits (the old app's rule). */
export function fromAlerts(bars: Candle[], buy: number[], sell: number[]): SignalSet {
  const b = new Set(buy); const s = new Set(sell);
  return { long: bars.map((x) => b.has(x.t)), short: bars.map(() => false), exitL: bars.map((x) => s.has(x.t)), exitS: bars.map(() => false) };
}

export function runBacktest(bars: Candle[], sig: number[] | SignalSet, o: BacktestOptions): BtResult {
  return simulate(bars, Array.isArray(sig) ? fromSigns(sig) : sig, o);
}

interface Lot { dir: 1 | -1; entry: number; entryT: number; entryIdx: number; qty: number; fee: number; hi: number; lo: number }

export function simulate(bars: Candle[], s: SignalSet, o: BacktestOptions): BtResult {
  const trades: BtTrade[] = [];
  const equity: { t: number; e: number }[] = [];
  let cash = o.capital;
  let lots: Lot[] = [];
  let barsIn = 0;
  const allowed = (d: number) => (o.direction === 'both' ? true : o.direction === 'long' ? d === 1 : d === -1);
  const fee = o.feePct / 100;
  const feeCash = o.feeCash ?? 0;
  const slip = (o.slippagePct ?? 0) / 100;
  const pyramid = Math.max(1, Math.round(o.pyramiding ?? 1));
  const inRange = (t: number) => (o.from == null || t >= o.from) && (o.to == null || t <= o.to);
  const fillPx = (px: number, dir: 1 | -1, entering: boolean) => px * (1 + (entering ? dir : -dir) * slip);

  const closeLot = (lot: Lot, raw: number, t: number, idx: number, reason: ExitReason) => {
    const price = reason === 'stop' || reason === 'target' ? raw : fillPx(raw, lot.dir, false);
    const gross = (price - lot.entry) * lot.dir * lot.qty;
    const exitFee = price * lot.qty * fee + feeCash;
    const pnl = gross - lot.fee - exitFee;
    cash += gross - exitFee;
    trades.push({
      dir: lot.dir, entryT: lot.entryT, entry: lot.entry, exitT: t, exit: price, pnl, pct: (pnl / (lot.entry * lot.qty)) * 100, reason,
      qty: lot.qty, bars: idx - lot.entryIdx, fee: lot.fee + exitFee, entryIdx: lot.entryIdx, exitIdx: idx,
      runUp: Math.max(0, (lot.dir === 1 ? lot.hi - lot.entry : lot.entry - lot.lo) * lot.qty),
      drawdown: Math.max(0, (lot.dir === 1 ? lot.entry - lot.lo : lot.hi - lot.entry) * lot.qty),
    });
  };
  const closeAll = (dir: 1 | -1 | 0, px: number, t: number, idx: number, reason: ExitReason) => {
    const keep: Lot[] = [];
    for (const l of lots) { if (dir === 0 || l.dir === dir) closeLot(l, px, t, idx, reason); else keep.push(l); }
    lots = keep;
  };
  const open = (dir: 1 | -1, raw: number, t: number, idx: number) => {
    if (!allowed(dir) || !inRange(t)) return;
    if (lots.length >= pyramid) return;
    const price = fillPx(raw, dir, true);
    const mode = o.sizeMode ?? 'percent';
    const equityNow = cash + lots.reduce((n, l) => n + (raw - l.entry) * l.dir * l.qty, 0);
    const qty = mode === 'qty' ? (o.qty ?? 1) : mode === 'cash' ? (o.cash ?? o.capital) / price : (Math.max(0, equityNow) * (o.riskShare / 100)) / price;
    if (!(qty > 0)) return;
    const entryFee = price * qty * fee + feeCash;
    cash -= entryFee;
    lots.push({ dir, entry: price, entryT: t, entryIdx: idx, qty, fee: entryFee, hi: price, lo: price });
  };
  // One bar's orders at a price: exits first, then a reversal or a new entry.
  const act = (i: number, px: number, t: number, idx: number) => {
    const L = s.long[i]; const S = s.short[i];
    const xL = s.exitL?.[i]; const xS = s.exitS?.[i];
    const dir = lots[0]?.dir ?? 0;
    if (dir === 1 && (S || xL)) closeAll(1, px, t, idx, S ? 'signal' : 'exit');
    else if (dir === -1 && (L || xS)) closeAll(-1, px, t, idx, L ? 'signal' : 'exit');
    const now = lots[0]?.dir ?? 0;
    if (L && now !== -1 && (now === 1 ? lots.length < pyramid : true)) open(1, px, t, idx);
    else if (S && now !== 1 && (now === -1 ? lots.length < pyramid : true)) open(-1, px, t, idx);
  };

  for (let i = 1; i < bars.length; i++) {
    const b = bars[i]!;
    // The previous bar's signal acts at this bar's open (or, filling on close, this bar's at its close).
    if (!o.fillOnClose) act(i - 1, b.o, b.t, i);
    if (lots.length) {
      barsIn++;
      for (const l of [...lots]) {
        l.hi = Math.max(l.hi, b.h); l.lo = Math.min(l.lo, b.l);
        const stop = o.stopPct > 0 ? l.entry * (1 - l.dir * o.stopPct / 100) : null;
        const target = o.targetPct > 0 ? l.entry * (1 + l.dir * o.targetPct / 100) : null;
        // Within a bar the stop is assumed to come first: the cautious reading.
        let reason: ExitReason | null = null; let at = 0;
        if (stop != null && (l.dir === 1 ? b.l <= stop : b.h >= stop)) { reason = 'stop'; at = stop; }
        else if (target != null && (l.dir === 1 ? b.h >= target : b.l <= target)) { reason = 'target'; at = target; }
        if (reason) { closeLot(l, at, b.t, i, reason); lots = lots.filter((x) => x !== l); }
      }
    }
    if (o.fillOnClose) act(i, b.c, b.t, i);
    if (lots.length && o.to != null && b.t > o.to) closeAll(0, b.c, b.t, i, 'end');
    const openPnl = lots.reduce((n, l) => n + (b.c - l.entry) * l.dir * l.qty, 0);
    equity.push({ t: b.t, e: cash + openPnl });
  }
  const last = bars[bars.length - 1];
  const openPnl = last ? lots.reduce((n, l) => n + (last.c - l.entry) * l.dir * l.qty, 0) : 0;
  if (lots.length && last) closeAll(0, last.c, last.t, bars.length - 1, 'end');
  if (equity.length) equity[equity.length - 1]!.e = cash;
  return report(bars, trades, equity, cash, barsIn, o, openPnl);
}

function sideStats(list: BtTrade[]): SideStats {
  const w = list.filter((t) => t.pnl > 0);
  const gp = w.reduce((n, t) => n + t.pnl, 0);
  const gl = Math.abs(list.filter((t) => t.pnl <= 0).reduce((n, t) => n + t.pnl, 0));
  const net = gp - gl;
  return { trades: list.length, wins: w.length, net, win: list.length ? (w.length / list.length) * 100 : 0, pf: gl ? gp / gl : gp ? Infinity : 0, grossProfit: gp, grossLoss: gl };
}

function report(bars: Candle[], trades: BtTrade[], equity: { t: number; e: number }[], cash: number, barsIn: number, o: BacktestOptions, openPnl: number): BtResult {
  // Trades in time order of their exit, each with the running total.
  let cum = 0;
  for (const t of trades) { cum += t.pnl; t.cum = cum; }
  const wins = trades.filter((t) => t.pnl > 0); const losses = trades.filter((t) => t.pnl <= 0);
  const gw = wins.reduce((n, t) => n + t.pnl, 0); const gl = Math.abs(losses.reduce((n, t) => n + t.pnl, 0));
  let peak = o.capital; let dd = 0; let ddPct = 0; let trough = o.capital; let runUp = 0; let runUpPct = 0;
  const drawdown: { t: number; dd: number }[] = [];
  for (const p of equity) {
    peak = Math.max(peak, p.e); dd = Math.max(dd, peak - p.e); ddPct = Math.max(ddPct, peak ? ((peak - p.e) / peak) * 100 : 0);
    trough = Math.min(trough, p.e); runUp = Math.max(runUp, p.e - trough); runUpPct = Math.max(runUpPct, trough ? ((p.e - trough) / trough) * 100 : 0);
    drawdown.push({ t: p.t, dd: peak ? -((peak - p.e) / peak) * 100 : 0 });
  }
  const rets = trades.map((t) => t.pct);
  const mean = rets.length ? rets.reduce((n, x) => n + x, 0) / rets.length : 0;
  const sd = rets.length > 1 ? Math.sqrt(rets.reduce((n, x) => n + (x - mean) ** 2, 0) / (rets.length - 1)) : 0;
  const down = rets.filter((x) => x < 0);
  const dsd = down.length ? Math.sqrt(down.reduce((n, x) => n + x * x, 0) / rets.length) : 0;
  let ws = 0; let ls = 0; let wsMax = 0; let lsMax = 0;
  for (const t of trades) { if (t.pnl > 0) { ws++; ls = 0; wsMax = Math.max(wsMax, ws); } else { ls++; ws = 0; lsMax = Math.max(lsMax, ls); } }
  const first = bars[0]?.c ?? 0;
  const last = bars[bars.length - 1];
  const avgWin = wins.length ? gw / wins.length : 0; const avgLoss = losses.length ? -gl / losses.length : 0;
  const barsOf = (l: BtTrade[]) => (l.length ? l.reduce((n, t) => n + (t.bars ?? 0), 0) / l.length : 0);
  const buyHoldCurve = first ? bars.slice(1).map((b) => ({ t: b.t, e: o.capital * (b.c / first) })) : [];
  return {
    trades, equity, net: cash - o.capital, netPct: ((cash - o.capital) / o.capital) * 100,
    win: trades.length ? (wins.length / trades.length) * 100 : 0, pf: gl ? gw / gl : gw ? Infinity : 0,
    dd, ddPct, avg: trades.length ? (cash - o.capital) / trades.length : 0, sharpe: sd ? (mean / sd) * Math.sqrt(rets.length) : 0,
    buyHold: first && last ? ((last.c - first) / first) * 100 : 0, exposure: bars.length > 1 ? (barsIn / (bars.length - 1)) * 100 : 0,
    grossProfit: gw, grossLoss: gl, fees: trades.reduce((n, t) => n + (t.fee ?? 0), 0), avgWin, avgLoss, ratio: avgLoss ? avgWin / Math.abs(avgLoss) : 0,
    largestWin: wins.reduce((m, t) => Math.max(m, t.pnl), 0), largestLoss: losses.reduce((m, t) => Math.min(m, t.pnl), 0),
    expectancy: trades.length ? (wins.length / trades.length) * avgWin + (losses.length / trades.length) * avgLoss : 0,
    winStreak: wsMax, lossStreak: lsMax, maxRunUp: runUp, maxRunUpPct: runUpPct, sortino: dsd ? (mean / dsd) * Math.sqrt(rets.length) : 0,
    avgBars: barsOf(trades), avgBarsWin: barsOf(wins), avgBarsLoss: barsOf(losses), wins: wins.length, losses: losses.length,
    long: sideStats(trades.filter((t) => t.dir === 1)), short: sideStats(trades.filter((t) => t.dir === -1)),
    drawdown, buyHoldCurve, openPnl, capital: o.capital,
  };
}

// ── Optimizer and walk-forward ───────────────────────────────────────────────────────────────────
export interface Range { key: string; from: number; to: number; step: number }
export interface OptRow { values: Record<string, number>; net: number; netPct: number; pf: number; win: number; dd: number; trades: number }

/** Every combination of the ranges (at most `cap`), as input maps. */
export function grid(ranges: Range[], cap = 400): Record<string, number>[] {
  let out: Record<string, number>[] = [{}];
  for (const r of ranges) {
    const step = r.step > 0 ? r.step : 1;
    const vals: number[] = [];
    for (let v = r.from; v <= r.to + 1e-9 && vals.length < 200; v += step) vals.push(Number(v.toFixed(6)));
    const next: Record<string, number>[] = [];
    for (const base of out) for (const v of vals) { if (next.length >= cap) break; next.push({ ...base, [r.key]: v }); }
    out = next;
  }
  return out.filter((x) => Object.keys(x).length);
}

export const optRow = (values: Record<string, number>, r: BtResult): OptRow => ({ values, net: r.net, netPct: r.netPct, pf: r.pf, win: r.win, dd: r.ddPct, trades: r.trades.length });

/** Runs a strategy over every combination; the best (by net profit) first. */
export async function optimize(ranges: Range[], run: (values: Record<string, number>) => Promise<BtResult | null>, onProgress?: (done: number, total: number) => void): Promise<OptRow[]> {
  const combos = grid(ranges);
  const rows: OptRow[] = [];
  for (let i = 0; i < combos.length; i++) {
    const r = await run(combos[i]!);
    if (r) rows.push(optRow(combos[i]!, r));
    onProgress?.(i + 1, combos.length);
  }
  return rows.sort((a, b) => b.net - a.net);
}

export interface WalkWindow { from: number; to: number; best: Record<string, number> | null; inSample: OptRow | null; outSample: OptRow | null }

/**
 * Walk-forward: the bars split into `windows` consecutive windows; in each, the first `inShare`
 * finds the best combination and the rest tests it unseen.
 */
export async function walkForward(bars: Candle[], windows: number, inShare: number, ranges: Range[], run: (b: Candle[], values: Record<string, number>) => Promise<BtResult | null>): Promise<WalkWindow[]> {
  const out: WalkWindow[] = [];
  const size = Math.floor(bars.length / Math.max(1, windows));
  for (let w = 0; w < windows; w++) {
    const part = bars.slice(w * size, w === windows - 1 ? bars.length : (w + 1) * size);
    const cut = Math.max(2, Math.floor(part.length * inShare));
    const ins = part.slice(0, cut); const oos = part.slice(cut);
    const rows = await optimize(ranges, (v) => run(ins, v));
    const best = rows[0] ?? null;
    const test = best && oos.length > 2 ? await run(oos, best.values) : null;
    out.push({ from: part[0]?.t ?? 0, to: part[part.length - 1]?.t ?? 0, best: best?.values ?? null, inSample: best, outSample: test && best ? optRow(best.values, test) : null });
  }
  return out;
}
