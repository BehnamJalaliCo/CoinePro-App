// Paper trading: a practice account in this browser, run by the phone's rules (core/papertrade
// PaperBook.kt): market, limit, stop, stop-limit and trailing orders; taker and maker fees, slippage
// and an assumed spread on every fill; leverage with a margin check and a stop-out; stop loss and
// take profit closing on their own. Marked to the live tickers. Nothing is sent anywhere.
import { create } from 'zustand';
import { uuid } from '../data/http';
import { useMarket } from './market';

export type Side = 'long' | 'short';
export type OrderType = 'market' | 'limit' | 'stop' | 'stop_limit' | 'trailing';
export type CloseReason = 'manual' | 'sl' | 'tp' | 'liq';

export interface PaperPosition { id: string; symbol: string; side: Side; qty: number; entry: number; openedAt: number; sl?: number; tp?: number; leverage?: number; fees?: number }
export interface PaperTrade extends PaperPosition { exit: number; closedAt: number; pnl: number; reason?: CloseReason }
export interface PaperOrder {
  id: string; symbol: string; side: Side; type: OrderType; qty: number;
  limitPrice?: number; stopPrice?: number; trailPercent?: number; trailRef?: number;
  sl?: number; tp?: number; createdAt: number; triggered?: boolean;
}
export interface PaperRules { startingBalance: number; leverage: number; takerFeePercent: number; makerFeePercent: number; slippagePercent: number; spreadPercent: number; stopOutPercent: number }

export const DEFAULT_RULES: PaperRules = { startingBalance: 10_000, leverage: 1, takerFeePercent: 0.05, makerFeePercent: 0.02, slippagePercent: 0.02, spreadPercent: 0.04, stopOutPercent: 50 };

const KEY = 'pc.paper.v1';
interface Stored { balance: number; positions: PaperPosition[]; history: PaperTrade[]; orders: PaperOrder[]; rules: PaperRules }
const read = (): Stored => {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Stored> | null;
    if (v && typeof v.balance === 'number') return { balance: v.balance, positions: v.positions ?? [], history: v.history ?? [], orders: v.orders ?? [], rules: { ...DEFAULT_RULES, ...(v.rules ?? {}) } };
  } catch { /* fall through */ }
  return { balance: DEFAULT_RULES.startingBalance, positions: [], history: [], orders: [], rules: DEFAULT_RULES };
};

export const pnlOf = (p: PaperPosition, price: number) => (p.side === 'long' ? price - p.entry : p.entry - price) * p.qty;

/** The price a market order really gets: half the spread plus the slippage, against the trader. */
export function fillPrice(side: Side, price: number, rules: PaperRules, maker = false): number {
  if (maker) return price;
  const k = (rules.spreadPercent / 2 + rules.slippagePercent) / 100;
  return side === 'long' ? price * (1 + k) : price * (1 - k);
}

export const marginOf = (p: { qty: number; entry: number; leverage?: number }) => (p.qty * p.entry) / Math.max(1, p.leverage ?? 1);

interface PaperState extends Stored {
  /** A market order at the live price (kept for the bottom panel). */
  open(symbol: string, side: Side, qty: number, price: number, sl?: number, tp?: number): string | null;
  place(order: Omit<PaperOrder, 'id' | 'createdAt'>, price: number): string | null;
  cancel(id: string): void;
  close(id: string, price: number, reason?: CloseReason): void;
  protect(id: string, sl?: number, tp?: number): void;
  setRules(r: Partial<PaperRules>): void;
  reset(): void;
  /** Runs the book against a new price for a symbol: fills, triggers, protection, stop-out. */
  mark(symbol: string, price: number): void;
  lastError: string | null;
}

export const usePaper = create<PaperState>((set, get) => {
  const save = () => { const { balance, positions, history, orders, rules } = get(); try { localStorage.setItem(KEY, JSON.stringify({ balance, positions, history, orders, rules })); } catch { /* ignore */ } };

  const fill = (symbol: string, side: Side, qty: number, price: number, maker: boolean, sl?: number, tp?: number): string | null => {
    const { rules, balance, positions } = get();
    const px = fillPrice(side, price, rules, maker);
    const fee = qty * px * ((maker ? rules.makerFeePercent : rules.takerFeePercent) / 100);
    const used = positions.reduce((n, p) => n + marginOf(p), 0);
    const need = (qty * px) / Math.max(1, rules.leverage);
    if (need + fee > balance - used) { set({ lastError: 'margin' }); return null; }
    const id = uuid();
    set({ positions: [{ id, symbol, side, qty, entry: px, openedAt: Date.now(), sl, tp, leverage: rules.leverage, fees: fee }, ...positions], balance: balance - fee, lastError: null });
    save();
    return id;
  };

  return {
    ...read(),
    lastError: null,
    open: (symbol, side, qty, price, sl, tp) => (qty > 0 && price > 0 ? fill(symbol, side, qty, price, false, sl, tp) : null),
    place: (o, price) => {
      if (!(o.qty > 0) || !(price > 0)) { set({ lastError: 'invalid' }); return null; }
      if (o.type === 'market') return fill(o.symbol, o.side, o.qty, price, false, o.sl, o.tp);
      if ((o.type === 'limit' || o.type === 'stop_limit') && !(o.limitPrice! > 0)) { set({ lastError: 'price' }); return null; }
      if ((o.type === 'stop' || o.type === 'stop_limit') && !(o.stopPrice! > 0)) { set({ lastError: 'price' }); return null; }
      if (o.type === 'trailing' && !(o.trailPercent! > 0)) { set({ lastError: 'price' }); return null; }
      const id = uuid();
      set({ orders: [{ ...o, id, createdAt: Date.now(), trailRef: o.type === 'trailing' ? price : undefined }, ...get().orders], lastError: null });
      save();
      return id;
    },
    cancel: (id) => { set({ orders: get().orders.filter((o) => o.id !== id) }); save(); },
    close: (id, price, reason = 'manual') => {
      const { positions, rules } = get();
      const p = positions.find((x) => x.id === id);
      if (!p) return;
      const exit = reason === 'manual' || reason === 'liq' ? fillPrice(p.side === 'long' ? 'short' : 'long', price, rules) : price;
      const fee = p.qty * exit * (rules.takerFeePercent / 100);
      const pnl = pnlOf(p, exit) - fee;
      set({
        positions: positions.filter((x) => x.id !== id),
        history: [{ ...p, exit, closedAt: Date.now(), pnl: pnl - (p.fees ?? 0), reason, fees: (p.fees ?? 0) + fee }, ...get().history].slice(0, 500),
        balance: get().balance + pnl,
      });
      save();
    },
    protect: (id, sl, tp) => { set({ positions: get().positions.map((p) => (p.id === id ? { ...p, sl, tp } : p)) }); save(); },
    setRules: (r) => {
      const next = { ...get().rules, ...r };
      next.leverage = Math.min(500, Math.max(1, Math.round(next.leverage)));
      next.startingBalance = Math.min(10_000_000, Math.max(100, next.startingBalance));
      for (const k of ['takerFeePercent', 'makerFeePercent', 'slippagePercent', 'spreadPercent'] as const) next[k] = Math.min(5, Math.max(0, next[k]));
      set({ rules: next }); save();
    },
    reset: () => { set({ balance: get().rules.startingBalance, positions: [], history: [], orders: [], lastError: null }); save(); },
    mark: (symbol, price) => {
      const st = get();
      // Pending orders.
      for (const o of st.orders.filter((x) => x.symbol === symbol)) {
        let go = false; let maker = false; let at = price;
        if (o.type === 'limit') { go = o.side === 'long' ? price <= o.limitPrice! : price >= o.limitPrice!; maker = true; at = o.limitPrice!; }
        else if (o.type === 'stop') go = o.side === 'long' ? price >= o.stopPrice! : price <= o.stopPrice!;
        else if (o.type === 'stop_limit') {
          if (!o.triggered && (o.side === 'long' ? price >= o.stopPrice! : price <= o.stopPrice!)) { set({ orders: get().orders.map((x) => (x.id === o.id ? { ...x, triggered: true } : x)) }); }
          const armed = get().orders.find((x) => x.id === o.id)?.triggered;
          if (armed) { go = o.side === 'long' ? price <= o.limitPrice! : price >= o.limitPrice!; maker = true; at = o.limitPrice!; }
        } else if (o.type === 'trailing') {
          const ref = o.trailRef ?? price;
          const nextRef = o.side === 'long' ? Math.min(ref, price) : Math.max(ref, price);
          const trigger = o.side === 'long' ? nextRef * (1 + o.trailPercent! / 100) : nextRef * (1 - o.trailPercent! / 100);
          go = o.side === 'long' ? price >= trigger : price <= trigger;
          if (!go && nextRef !== ref) set({ orders: get().orders.map((x) => (x.id === o.id ? { ...x, trailRef: nextRef } : x)) });
        }
        if (go) { get().cancel(o.id); fill(o.symbol, o.side, o.qty, at, maker, o.sl, o.tp); }
      }
      // Protection.
      for (const p of get().positions.filter((x) => x.symbol === symbol)) {
        if (p.sl != null && (p.side === 'long' ? price <= p.sl : price >= p.sl)) get().close(p.id, p.sl, 'sl');
        else if (p.tp != null && (p.side === 'long' ? price >= p.tp : price <= p.tp)) get().close(p.id, p.tp, 'tp');
      }
      // Stop-out: equity under the stop-out share of the margin in use closes everything.
      const now = get();
      if (!now.positions.length) return;
      const tickers = useMarket.getState().tickers;
      const unreal = now.positions.reduce((n, p) => n + pnlOf(p, p.symbol === symbol ? price : tickers[p.symbol]?.last ?? p.entry), 0);
      const used = now.positions.reduce((n, p) => n + marginOf(p), 0);
      if (used > 0 && (now.balance + unreal) < used * (now.rules.stopOutPercent / 100) && now.rules.leverage > 1) {
        for (const p of now.positions) get().close(p.id, p.symbol === symbol ? price : tickers[p.symbol]?.last ?? p.entry, 'liq');
      }
    },
  };
});

let watching = false;
/** Runs the paper book against every price the market feed brings. */
export function startPaperWatch(): void {
  if (watching) return;
  watching = true;
  const seen = new Map<string, number>();
  useMarket.subscribe((m) => {
    const st = usePaper.getState();
    if (!st.orders.length && !st.positions.some((p) => p.sl != null || p.tp != null) && st.rules.leverage <= 1) return;
    const symbols = new Set([...st.orders.map((o) => o.symbol), ...st.positions.map((p) => p.symbol)]);
    for (const s of symbols) {
      const px = m.tickers[s]?.last;
      if (!px || seen.get(s) === px) continue;
      seen.set(s, px);
      st.mark(s, px);
    }
  });
}
