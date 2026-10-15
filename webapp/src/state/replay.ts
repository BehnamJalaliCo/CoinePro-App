// Bar replay, as TradingView's: pick a starting bar on the chart (or a date, the first bar, a random
// one), then play the bars after it one at a time or step, at a chosen speed, optionally building
// each bar from a lower timeframe's bars (the update interval). The engine is the old Pro-Chart
// ReplayController. Practice trading runs inside the replay, with the old academy simulator's rules:
// market, limit and stop orders, size from the risk per trade, stop loss and take profit (by default
// 1.5× and 3× ATR) that close on their own as bars are revealed, a 50% partial close, breakeven,
// reverse, R-multiples, and the session kept across a refresh.
import { create } from 'zustand';
import { ReplayController, SPEED_LADDER, type ReplayState } from '../engine/ReplayController.js';
import type { Candle } from '../data/types';
import { feed } from '../data/feed';
import { intervalSeconds } from '../data/intervals';
import { usePrefs } from './prefs';
import { usePaper, fillPrice } from './paper';
import { useChartBus } from '../chart/chartBus';

export type PracticeOrder = 'market' | 'limit' | 'stop';
export type CloseHow = 'sl' | 'tp' | 'manual' | 'reverse' | 'flatten';

export interface ReplayTrade {
  id: string;
  side: 'long' | 'short';
  /** Fill price (an order's price while it waits). */
  entry: number;
  /** Bar time of the fill (of the order while it waits). */
  at: number;
  exit?: number;
  exitAt?: number;
  sl?: number;
  tp?: number;
  /** Units still open, and at the fill. */
  qty: number;
  qty0: number;
  /** Money lost at the first stop (for R); 0 without a stop. */
  risk: number;
  /** Money already taken by partial closes. */
  realized: number;
  pnl?: number;
  r?: number;
  how?: CloseHow;
  type: PracticeOrder;
  pending?: boolean;
}

export interface PracticeSettings {
  /** The practice account's starting balance. */
  balance: number;
  /** Risk per trade, percent of the balance (sizes the trade from its stop). */
  riskPct: number;
  /** Stop loss and take profit from the ATR on every new trade. */
  auto: boolean;
  slAtr: number;
  tpAtr: number;
  /** Market orders pay the paper account's spread and slippage. */
  spread: boolean;
  order: PracticeOrder;
  /** 'bar' or an interval id below the chart's: each bar is built from those bars. */
  update: string;
}

export const DEFAULT_PRACTICE: PracticeSettings = { balance: 10_000, riskPct: 1, auto: true, slAtr: 1.5, tpAtr: 3, spread: true, order: 'market', update: 'bar' };

/** A replayed trade's result in percent of its entry, at `price` while it is open. */
export const replayPnl = (t: ReplayTrade, price: number) => ((t.exit ?? price) - t.entry) / t.entry * 100 * (t.side === 'long' ? 1 : -1);

/** Money made by a trade at `price` (or its exit): what is still open plus what partial closes took. */
export const tradeMoney = (t: ReplayTrade, price: number) => ((t.exit ?? price) - t.entry) * (t.side === 'long' ? 1 : -1) * t.qty + t.realized;

/** Average true range over the last `n` bars (Wilder's plain mean here, as the old practice used). */
export function atrOf(bars: Candle[], n = 14): number {
  if (bars.length < 2) return 0;
  const from = Math.max(1, bars.length - n);
  let sum = 0;
  let k = 0;
  for (let i = from; i < bars.length; i++) {
    const b = bars[i]!; const p = bars[i - 1]!;
    sum += Math.max(b.h - b.l, Math.abs(b.h - p.c), Math.abs(b.l - p.c));
    k++;
  }
  return k ? sum / k : 0;
}

/** Stop distance for a new trade: 1.5×ATR by default, never under 0.15% of the price. */
export function stopDistance(bars: Candle[], price: number, mult: number): number {
  const atr = atrOf(bars.slice(-21), 14);
  const floor = price * 0.0015;
  return atr > 0 ? Math.max(atr * mult, floor) : price * 0.005 * (mult / 1.5);
}

const close = (t: ReplayTrade, price: number, at: number, how: CloseHow): ReplayTrade => {
  const done: ReplayTrade = { ...t, exit: price, exitAt: at, how };
  const pnl = tradeMoney(done, price);
  return { ...done, pnl, r: t.risk > 0 ? pnl / t.risk : undefined };
};

/**
 * One revealed bar (or part of one) against the trades: orders that the bar's range reaches are
 * filled at their price, then stops and targets close at their level, the stop first when one bar
 * reaches both (the worst case, as the old simulator did). Trades and orders newer than the bar are
 * left alone, so stepping back and forward again cannot fill an order before it was placed.
 */
export function settleBar(trades: ReplayTrade[], bar: Candle): ReplayTrade[] {
  let changed = false;
  const out = trades.map((t0) => {
    let t = t0;
    if (t.exit != null || t.at >= bar.t) return t;
    if (t.pending) {
      const long = t.side === 'long';
      const hit = t.type === 'limit' ? (long ? bar.l <= t.entry : bar.h >= t.entry) : (long ? bar.h >= t.entry : bar.l <= t.entry);
      if (!hit) return t;
      t = { ...t, pending: false, at: bar.t };
      changed = true;
    }
    const long = t.side === 'long';
    if (t.sl != null && (long ? bar.l <= t.sl : bar.h >= t.sl)) { changed = true; return close(t, t.sl, bar.t, 'sl'); }
    if (t.tp != null && (long ? bar.h >= t.tp : bar.l <= t.tp)) { changed = true; return close(t, t.tp, bar.t, 'tp'); }
    return t;
  });
  return changed ? out : trades;
}

export interface PracticeStats { trades: number; wins: number; winRate: number | null; net: number; netPct: number; netR: number; balance: number; equity: number; last: ReplayTrade | null; open: number; pending: number }

/** The session's figures: closed trades, wins, win rate, net money, percent and R, balance and equity. */
export function practiceStats(trades: ReplayTrade[], start: number, price: number): PracticeStats {
  const closed = trades.filter((t) => t.exit != null);
  const wins = closed.filter((t) => (t.pnl ?? 0) > 0).length;
  const net = closed.reduce((n, t) => n + (t.pnl ?? 0), 0);
  const open = trades.filter((t) => t.exit == null && !t.pending);
  const banked = open.reduce((n, t) => n + t.realized, 0);
  const floating = open.reduce((n, t) => n + tradeMoney(t, price) - t.realized, 0);
  const last = closed.reduce<ReplayTrade | null>((a, t) => (!a || (t.exitAt ?? 0) >= (a.exitAt ?? 0) ? t : a), null);
  return {
    trades: closed.length, wins, winRate: closed.length ? wins / closed.length * 100 : null,
    net, netPct: start ? net / start * 100 : 0, netR: closed.reduce((n, t) => n + (t.r ?? 0), 0),
    balance: start + net + banked, equity: start + net + banked + floating, last,
    open: open.length, pending: trades.filter((t) => t.pending).length,
  };
}

/** Lower-timeframe bars grouped under the chart bar they fall in: bar time → its bars, in order. */
export function intrabarMap(full: Candle[], subs: Candle[]): Map<number, Candle[]> {
  const map = new Map<number, Candle[]>();
  let k = 0;
  for (let i = 0; i < full.length; i++) {
    const bt = full[i]!.t;
    const nt = i + 1 < full.length ? full[i + 1]!.t : Infinity;
    const bucket: Candle[] = [];
    while (k < subs.length && subs[k]!.t < bt) k++;
    while (k < subs.length && subs[k]!.t < nt) bucket.push(subs[k++]!);
    if (bucket.length > 1) map.set(bt, bucket);
  }
  return map;
}

/** The bar as it stands after its first `j + 1` lower-timeframe bars. */
export function formingBar(bar: Candle, subs: Candle[], j: number): Candle {
  let h = -Infinity; let l = Infinity; let v = 0;
  for (let k = 0; k <= j; k++) { const s = subs[k]!; h = Math.max(h, s.h); l = Math.min(l, s.l); v += s.v; }
  return { t: bar.t, o: subs[0]!.o, h, l, c: subs[j]!.c, v };
}

/** Lower timeframes a chart interval can be replayed by (TradingView's update interval). */
export const UPDATE_STEPS: Record<string, string[]> = {
  '3': ['1'], '5': ['1'], '15': ['1', '5'], '30': ['1', '5', '15'], '60': ['1', '5', '15'], '120': ['5', '15', '60'], '240': ['15', '60'],
  '1D': ['60', '240'], '1W': ['240', '1D'], '1M': ['1D'],
};

interface Session { symbol: string; interval: string; start: number; at: number; trades: ReplayTrade[] }
interface Stored { settings: PracticeSettings; session: Session | null }
const KEY = 'pc.replay.v1';
const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v) return { settings: { ...DEFAULT_PRACTICE, ...(v.settings ?? {}) }, session: v.session && Array.isArray(v.session.trades) ? v.session : null };
  } catch { /* fall through */ }
  return { settings: DEFAULT_PRACTICE, session: null };
};
const write = (s: Stored) => { try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* full or blocked */ } };

let seq = 0;
const tid = () => `r${Date.now().toString(36)}${(seq++).toString(36)}`;

interface ReplayStore {
  /** Waiting for the reader to click the starting bar. */
  selecting: boolean;
  state: ReplayState | null;
  /** The bars on screen while replay is on (null when off); the last one may be forming. */
  slice: Candle[] | null;
  controller: ReplayController;
  /** Where the replay started (bar time), for "back to the start" and resuming. */
  startedAt: number | null;
  /** The view the chart had when the bar was picked, kept so the chart does not jump. */
  keepView: { from: number; to: number } | null;
  select(): void;
  start(bars: Candle[], startTime: number, opts?: { keepView?: boolean; trades?: ReplayTrade[] }): void;
  exit(): void;
  /** Practice trades inside the replay. */
  trades: ReplayTrade[];
  settings: PracticeSettings;
  /** The saved session (symbol, interval, start, where it was, trades), to resume after a refresh. */
  session: Session | null;
  /** The update interval's state: off, loading, on (with how many bars it covers) or none found. */
  intrabar: { state: 'off' | 'loading' | 'on' | 'none'; bars: number };
  setSettings(p: Partial<PracticeSettings>): void;
  /** Buy or sell at the replayed close; against an open trade it closes that one (one-position netting). */
  trade(side: 'long' | 'short'): void;
  /** A limit or stop order at `price`. */
  order(side: 'long' | 'short', type: PracticeOrder, price: number): void;
  flatten(): void;
  closeTrade(id: string, share?: number): void;
  reverse(): void;
  breakeven(id: string): void;
  modify(id: string, p: { sl?: number | null; tp?: number | null; entry?: number }): void;
  cancel(id: string): void;
  resetPractice(): void;
  resume(bars: Candle[]): boolean;
  setUpdate(interval: string): void;
  /** The replayed close (the forming bar's while a bar is being built). */
  price(): number;
}

export const useReplay = create<ReplayStore>((set, get) => {
  /** The last bar index the trades have been settled through. */
  let settled = -1;
  /** The bar being built from lower-timeframe bars: its time and how far it got. */
  let intra: { t: number; j: number } | null = null;

  const settle = (bar: Candle) => {
    const next = settleBar(get().trades, bar);
    if (next !== get().trades) set({ trades: next });
  };
  /** Bars from the last settled one up to `idx` (forward moves only: steps, jumps, the slider). */
  const settleTo = (idx: number) => {
    const full = controller.full as Candle[];
    if (idx > settled) for (let i = Math.max(settled + 1, 0); i <= idx; i++) if (full[i]) settle(full[i]!);
    settled = idx;
  };

  const controller = new ReplayController({
    onSlice: (slice) => { intra = null; set({ slice: slice as Candle[] }); settleTo(controller.idx); },
    onStep: (bar, slice, idx) => {
      const b = bar as Candle;
      if (intra && intra.t === b.t) {
        // The rest of a bar that was being built: what its remaining lower bars reach.
        const subs = intrabarFor(b.t);
        if (subs) for (let k = intra.j + 1; k < subs.length; k++) settle(subs[k]!);
        settled = idx;
      } else settleTo(idx);
      intra = null;
      set({ slice: slice as Candle[] });
    },
    onChange: (state) => set({ state }),
    onEnd: () => undefined,
  });
  let subMap: Map<number, Candle[]> | null = null;
  const intrabarFor = (t: number) => subMap?.get(t) ?? null;
  controller.onIntrabar = (sub: unknown, bar: unknown, j: number, slice: unknown[]) => {
    const b = bar as Candle;
    const subs = intrabarFor(b.t);
    if (!subs) return;
    intra = { t: b.t, j };
    settle(sub as Candle);
    set({ slice: [...(slice as Candle[]), formingBar(b, subs, j)] });
  };

  const persist = () => {
    const { settings, trades, state, startedAt } = get();
    const { symbol, interval } = usePrefs.getState();
    const full = controller.full as Candle[];
    const at = state?.on ? full[controller.idx]?.t : undefined;
    const session = state?.on && startedAt != null && at != null ? { symbol, interval, start: startedAt, at, trades } : get().session;
    write({ settings, session });
    if (session !== get().session) set({ session });
  };

  const priceNow = () => { const s = get().slice; return s?.[s.length - 1]?.c ?? 0; };
  const barTime = () => { const s = get().slice; return s?.[s.length - 1]?.t ?? 0; };
  const equity = () => practiceStats(get().trades, get().settings.balance, priceNow()).equity;

  /** A new trade's stop, target and size at `price`, from the settings and the bars so far. */
  const build = (side: 'long' | 'short', price: number, type: PracticeOrder): ReplayTrade => {
    const st = get().settings;
    const dir = side === 'long' ? 1 : -1;
    const d = stopDistance(get().slice ?? [], price, st.slAtr);
    const sl = st.auto ? price - dir * d : undefined;
    const tp = st.auto ? price + dir * d * (st.tpAtr / st.slAtr) : undefined;
    const risk = sl != null ? equity() * st.riskPct / 100 : 0;
    const qty = sl != null ? risk / Math.abs(price - sl) : equity() / price;
    const q = Number(qty.toPrecision(4));
    return { id: tid(), side, entry: price, at: barTime(), sl, tp, qty: q, qty0: q, risk: sl != null ? Math.abs(price - sl) * q : 0, realized: 0, type, pending: type !== 'market' };
  };
  const marketPrice = (side: 'long' | 'short') => {
    const px = priceNow();
    return get().settings.spread ? fillPrice(side, px, usePaper.getState().rules) : px;
  };
  const exitPrice = (t: ReplayTrade) => marketPrice(t.side === 'long' ? 'short' : 'long');

  return {
    selecting: false,
    state: null,
    slice: null,
    controller,
    startedAt: null,
    keepView: null,
    ...(() => { const s = read(); return { settings: s.settings, session: s.session }; })(),
    intrabar: { state: 'off', bars: 0 },
    select: () => { if (get().state?.on) get().exit(); set({ selecting: true }); },
    start: (bars, startTime, opts) => {
      const api = useChartBus.getState().api;
      const keep = opts?.keepView !== false ? api?.timeScale().getVisibleLogicalRange() ?? null : null;
      settled = Number.MAX_SAFE_INTEGER; // the starting cut settles nothing
      set({ selecting: false, keepView: keep ? { from: keep.from, to: keep.to } : null, trades: opts?.trades ?? [] });
      if (!controller.enter(bars, { startTime })) { settled = -1; return; }
      settled = controller.idx;
      set({ startedAt: (controller.full as Candle[])[controller.idx]?.t ?? startTime });
      if (get().settings.update !== 'bar') get().setUpdate(get().settings.update);
      persist();
    },
    exit: () => {
      persist();
      controller.exit();
      controller.disableIntrabar();
      subMap = null; intra = null; settled = -1;
      set({ selecting: false, state: null, slice: null, trades: [], startedAt: null, keepView: null, intrabar: { state: 'off', bars: 0 } });
    },
    trades: [],
    setSettings: (p) => {
      const next = { ...get().settings, ...p };
      next.riskPct = Math.min(100, Math.max(0.01, next.riskPct));
      next.slAtr = Math.min(20, Math.max(0.1, next.slAtr));
      next.tpAtr = Math.min(50, Math.max(0.1, next.tpAtr));
      next.balance = Math.min(10_000_000, Math.max(100, next.balance));
      set({ settings: next });
      persist();
    },
    price: priceNow,
    trade: (side) => {
      if (!get().slice?.length) return;
      // An opposite trade closes the open one first, as a one-position practice account does.
      const open = get().trades.find((t) => t.exit == null && !t.pending);
      if (open && open.side !== side) { get().closeTrade(open.id); return; }
      if (open) return;
      const t = build(side, marketPrice(side), 'market');
      set({ trades: [...get().trades, t] });
      persist();
    },
    order: (side, type, price) => {
      if (!get().slice?.length || !(price > 0)) return;
      if (type === 'market') { get().trade(side); return; }
      set({ trades: [...get().trades, build(side, price, type)] });
      persist();
    },
    flatten: () => {
      if (!get().slice?.length) return;
      const at = barTime();
      set({ trades: get().trades.filter((t) => !t.pending).map((t) => (t.exit == null ? close(t, exitPrice(t), at, 'flatten') : t)) });
      persist();
    },
    closeTrade: (id, share = 1) => {
      const at = barTime();
      set({
        trades: get().trades.map((t) => {
          if (t.id !== id || t.exit != null || t.pending) return t;
          const px = exitPrice(t);
          if (share >= 1) return close(t, px, at, 'manual');
          const part = Number((t.qty * share).toPrecision(4));
          return { ...t, qty: Number((t.qty - part).toPrecision(6)), realized: t.realized + (px - t.entry) * (t.side === 'long' ? 1 : -1) * part };
        }),
      });
      persist();
    },
    reverse: () => {
      const open = get().trades.find((t) => t.exit == null && !t.pending);
      if (!open) return;
      const at = barTime();
      const closed = close(open, exitPrice(open), at, 'reverse');
      const side = open.side === 'long' ? 'short' : 'long';
      set({ trades: get().trades.map((t) => (t.id === open.id ? closed : t)) });
      set({ trades: [...get().trades, build(side, marketPrice(side), 'market')] });
      persist();
    },
    breakeven: (id) => { set({ trades: get().trades.map((t) => (t.id === id && t.exit == null ? { ...t, sl: t.entry } : t)) }); persist(); },
    modify: (id, p) => {
      set({
        trades: get().trades.map((t) => {
          if (t.id !== id || t.exit != null) return t;
          const n = { ...t };
          if (p.sl !== undefined) n.sl = p.sl ?? undefined;
          if (p.tp !== undefined) n.tp = p.tp ?? undefined;
          if (p.entry !== undefined && t.pending) {
            // Moving an order moves its stop and target with it.
            const d = p.entry - t.entry;
            n.entry = p.entry;
            if (n.sl != null) n.sl += d;
            if (n.tp != null) n.tp += d;
          }
          if (t.pending && n.sl != null) { n.risk = Math.abs(n.entry - n.sl) * n.qty0; }
          return n;
        }),
      });
      persist();
    },
    cancel: (id) => { set({ trades: get().trades.filter((t) => !(t.id === id && t.pending)) }); persist(); },
    resetPractice: () => { set({ trades: [] }); persist(); },
    resume: (bars) => {
      const s = get().session;
      const { symbol, interval } = usePrefs.getState();
      if (!s || s.symbol !== symbol || s.interval !== interval || !bars.length || bars[0]!.t > s.at || bars[bars.length - 1]!.t < s.at) return false;
      get().start(bars, s.at, { keepView: false, trades: s.trades });
      set({ startedAt: s.start });
      persist();
      return true;
    },
    setUpdate: (iv) => {
      set({ settings: { ...get().settings, update: iv } });
      persist();
      if (iv === 'bar' || !get().state?.on) { controller.disableIntrabar(); subMap = null; set({ intrabar: { state: 'off', bars: 0 } }); return; }
      const { symbol, interval } = usePrefs.getState();
      if (intervalSeconds(iv) >= intervalSeconds(interval)) return;
      set({ intrabar: { state: 'loading', bars: 0 } });
      const full = controller.full as Candle[];
      const from = full[controller.idx]?.t ?? 0;
      void (async () => {
        let subs: Candle[] = [];
        try {
          let page = await feed.history(symbol, iv);
          subs = page.bars;
          // Older pages until the lower bars reach back to the replayed bar (six pages at most).
          for (let k = 0; k < 6 && page.more && subs.length && subs[0]!.t > from; k++) {
            page = await feed.history(symbol, iv, subs[0]!.t);
            if (!page.bars.length) break;
            subs = [...page.bars.filter((b) => b.t < subs[0]!.t), ...subs];
          }
        } catch { /* none: the replay steps whole bars */ }
        if (controller.full !== full || get().settings.update !== iv) return;
        subMap = intrabarMap(full, subs);
        if (subMap.size) { controller.setIntrabarData(subMap); controller.enableIntrabar(true); }
        set({ intrabar: { state: subMap.size ? 'on' : 'none', bars: subMap.size } });
      })();
    },
  };
});

// The session follows the replay as it moves (at most once a second), so a refresh can resume it.
let saveTimer: ReturnType<typeof setTimeout> | null = null;
useReplay.subscribe((s, p) => {
  if (s.state?.idx === p.state?.idx || !s.state?.on) return;
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    const r = useReplay.getState();
    if (!r.state?.on || r.startedAt == null) return;
    const { symbol, interval } = usePrefs.getState();
    const at = (r.controller.full as Candle[])[r.controller.idx]?.t;
    if (at == null) return;
    const session = { symbol, interval, start: r.startedAt, at, trades: r.trades };
    write({ settings: r.settings, session });
    useReplay.setState({ session });
  }, 1000);
});

/** Go to `t` (seconds) in the replay: seeks while replaying, starts there while picking a bar. */
export function replayGoTo(t: number, bars: Candle[]): boolean {
  const r = useReplay.getState();
  if (r.state?.on) return r.controller.seek({ time: t });
  if (r.selecting && bars.length && bars[0]!.t <= t) { r.start(bars, t, { keepView: false }); return true; }
  return false;
}

export { SPEED_LADDER };
