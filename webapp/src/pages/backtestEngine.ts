// The strategy tester's engine: built-in strategies (the app's backtest presets) over a series of
// bars, traded one position at a time with a stop, a target, fees and a share of equity per trade.
// Signals are read on a bar's close and filled on the next bar's open, so no bar sees its future.
import { atr, bollinger, ema, macd, rsi, sma } from '../engine/indicators.js';
import type { Candle } from '../data/types';

export type StrategyId = 'ma_cross' | 'rsi' | 'macd' | 'bollinger' | 'breakout' | 'supertrend';
export interface StrategyParams { fast: number; slow: number; maType: 'sma' | 'ema'; rsiLen: number; rsiLow: number; rsiHigh: number; bbLen: number; bbMult: number; donchian: number; stLen: number; stMult: number }
export const DEFAULT_PARAMS: StrategyParams = { fast: 9, slow: 21, maType: 'ema', rsiLen: 14, rsiLow: 30, rsiHigh: 70, bbLen: 20, bbMult: 2, donchian: 20, stLen: 10, stMult: 3 };

export interface BacktestOptions { direction: 'long' | 'short' | 'both'; stopPct: number; targetPct: number; feePct: number; capital: number; riskShare: number }
export const DEFAULT_OPTIONS: BacktestOptions = { direction: 'both', stopPct: 0, targetPct: 0, feePct: 0.05, capital: 10_000, riskShare: 100 };

export interface BtTrade { dir: 1 | -1; entryT: number; entry: number; exitT: number; exit: number; pnl: number; pct: number; reason: 'signal' | 'stop' | 'target' | 'end' }
export interface BtResult { trades: BtTrade[]; equity: { t: number; e: number }[]; net: number; netPct: number; win: number; pf: number; dd: number; ddPct: number; avg: number; sharpe: number; buyHold: number; exposure: number }

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

export function runBacktest(bars: Candle[], sig: number[], o: BacktestOptions): BtResult {
  const trades: BtTrade[] = [];
  const equity: { t: number; e: number }[] = [];
  let cash = o.capital; let pos: { dir: 1 | -1; entry: number; entryT: number; qty: number } | null = null;
  let barsIn = 0;
  const allowed = (d: number) => (o.direction === 'both' ? true : o.direction === 'long' ? d === 1 : d === -1);
  const fee = o.feePct / 100;
  const closeAt = (price: number, t: number, reason: BtTrade['reason']) => {
    if (!pos) return;
    const gross = (price - pos.entry) * pos.dir * pos.qty;
    const cost = (pos.entry + price) * pos.qty * fee;
    const pnl = gross - cost;
    cash += pnl;
    trades.push({ dir: pos.dir, entryT: pos.entryT, entry: pos.entry, exitT: t, exit: price, pnl, pct: (pnl / (pos.entry * pos.qty)) * 100, reason });
    pos = null;
  };
  for (let i = 1; i < bars.length; i++) {
    const b = bars[i]!; const want = sig[i - 1]!;
    // The previous bar's signal acts at this bar's open.
    if (want && pos && pos.dir !== want) closeAt(b.o, b.t, 'signal');
    if (want && !pos && allowed(want)) {
      const qty = (cash * (o.riskShare / 100)) / b.o;
      if (qty > 0) pos = { dir: want as 1 | -1, entry: b.o, entryT: b.t, qty };
    }
    if (pos) {
      barsIn++;
      const stop = o.stopPct > 0 ? pos.entry * (1 - pos.dir * o.stopPct / 100) : null;
      const target = o.targetPct > 0 ? pos.entry * (1 + pos.dir * o.targetPct / 100) : null;
      // Within a bar the stop is assumed to come first: the cautious reading.
      if (stop != null && (pos.dir === 1 ? b.l <= stop : b.h >= stop)) closeAt(stop, b.t, 'stop');
      else if (target != null && (pos.dir === 1 ? b.h >= target : b.l <= target)) closeAt(target, b.t, 'target');
    }
    const open = pos ? (b.c - pos.entry) * pos.dir * pos.qty : 0;
    equity.push({ t: b.t, e: cash + open });
  }
  const last = bars[bars.length - 1];
  if (pos && last) closeAt(last.c, last.t, 'end');
  if (equity.length) equity[equity.length - 1]!.e = cash;
  const wins = trades.filter((t) => t.pnl > 0); const losses = trades.filter((t) => t.pnl <= 0);
  const gw = wins.reduce((n, t) => n + t.pnl, 0); const gl = Math.abs(losses.reduce((n, t) => n + t.pnl, 0));
  let peak = o.capital; let dd = 0; let ddPct = 0;
  for (const p of equity) { peak = Math.max(peak, p.e); dd = Math.max(dd, peak - p.e); ddPct = Math.max(ddPct, peak ? ((peak - p.e) / peak) * 100 : 0); }
  const rets = trades.map((t) => t.pct);
  const mean = rets.length ? rets.reduce((n, x) => n + x, 0) / rets.length : 0;
  const sd = rets.length > 1 ? Math.sqrt(rets.reduce((n, x) => n + (x - mean) ** 2, 0) / (rets.length - 1)) : 0;
  const first = bars[0]?.c ?? 0;
  return {
    trades, equity, net: cash - o.capital, netPct: ((cash - o.capital) / o.capital) * 100,
    win: trades.length ? (wins.length / trades.length) * 100 : 0, pf: gl ? gw / gl : gw ? Infinity : 0,
    dd, ddPct, avg: trades.length ? (cash - o.capital) / trades.length : 0, sharpe: sd ? (mean / sd) * Math.sqrt(rets.length) : 0,
    buyHold: first && last ? ((last.c - first) / first) * 100 : 0, exposure: bars.length > 1 ? (barsIn / (bars.length - 1)) * 100 : 0,
  };
}
