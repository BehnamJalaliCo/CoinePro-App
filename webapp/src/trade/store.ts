// What the reader holds on each platform, kept fresh while the page is visible: the account every
// 15 s, positions and working orders every 5 s once trading is live there. Neither platform pushes
// account events over a socket (TradeYar's only socket is prices), so this polls. The account
// call also answers whether trading is possible at all: signed out, no exchange keys, switched off
// on the server, a platform without the trade routes, or live.
import { create } from 'zustand';
import { HttpError, pageVisible } from '../data/http';
import { useSession } from '../data/session';
import { getAccount, getOrders, getPositions, getVenueLink } from './api';
import { LIVE_VENUES, type Venue } from './venue';
import { backendText, isWorking, type Account, type Order, type Position, type PosSide } from './wire';

export type VenueStatus = 'signed-out' | 'checking' | 'live' | 'no-keys' | 'off' | 'unavailable' | 'error';

export interface Book {
  status: VenueStatus;
  /** The platform's own sentence for the status, when it gave one. */
  message: string | null;
  account: Account | null;
  positions: Position[];
  orders: Order[];
  accountAt: number;
  bookAt: number;
}

const empty = (status: VenueStatus): Book => ({ status, message: null, account: null, positions: [], orders: [], accountAt: 0, bookAt: 0 });

export interface Protection { sl: number | null; tp: number | null; at: number }
const PROTECT_KEY = 'pc.trade.protect.v1';
const LEV_KEY = 'pc.trade.lev.v1';
const MODE_KEY = 'pc.trade.mode.v1';

function readJson<T>(key: string, fallback: T): T {
  try { const v = JSON.parse(localStorage.getItem(key) ?? 'null') as T | null; return v ?? fallback; } catch { return fallback; }
}
function writeJson(key: string, v: unknown): void { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* storage off */ } }

export const protectKey = (v: Venue, symbol: string, side: PosSide) => `${v}|${symbol}|${side}`;

interface TradingState {
  books: Record<Venue, Book>;
  /** Stop and target this browser last set on a position (the trade routes do not report them). */
  protections: Record<string, Protection>;
  /** The leverage last set per symbol. */
  leverage: Record<string, number>;
  /** Paper or live, as the reader last chose in the ticket. */
  mode: 'paper' | 'live' | null;
  refresh(v: Venue, what?: 'account' | 'book' | 'all'): Promise<void>;
  rememberProtection(v: Venue, symbol: string, side: PosSide, p: { sl: number | null; tp: number | null }): void;
  rememberLeverage(symbol: string, lev: number): void;
  setMode(m: 'paper' | 'live'): void;
}

const signedIn = (v: Venue) => Boolean(useSession.getState().sessions[v]);
const inflight: Partial<Record<string, Promise<void>>> = {};

function statusOf(e: unknown): VenueStatus {
  if (e instanceof HttpError) {
    if (e.status === 401) return 'signed-out';
    if (e.status === 400 || e.status === 409 || e.status === 422) return 'no-keys';
    if (e.status === 503) return 'off';
    if (e.status === 404 || e.status === 405) return 'unavailable';
  }
  if (e instanceof Error && e.message === 'signed-out') return 'signed-out';
  return 'error';
}

export const useTrading = create<TradingState>((set, get) => {
  const patch = (v: Venue, p: Partial<Book>) => set((s) => ({ books: { ...s.books, [v]: { ...s.books[v], ...p } } }));

  const loadAccount = async (v: Venue) => {
    if (!LIVE_VENUES.includes(v)) return;
    if (!signedIn(v)) { patch(v, empty('signed-out')); return; }
    // TradeYar answers the account 400 TYR-016 until an LBank key is saved; its key link says so
    // without an error (server report, 2026-10-15), so the account is asked only once there is a key.
    if (v === 'tradeyar') {
      try {
        const link = await getVenueLink();
        if (!link.configured && !link.connected) { patch(v, { status: 'no-keys', message: null, account: null, accountAt: Date.now() }); return; }
      } catch { /* the account call below says what is wrong */ }
    }
    try {
      const account = await getAccount(v);
      patch(v, { account, status: account.tradingEnabled ? 'live' : 'off', message: null, accountAt: Date.now() });
    } catch (e) {
      const status = statusOf(e);
      patch(v, { status, message: backendText(e), accountAt: Date.now(), ...(status === 'live' ? {} : { account: status === 'error' ? get().books[v].account : null }) });
    }
  };

  const loadBook = async (v: Venue) => {
    if (get().books[v].status !== 'live') return;
    const [p, o] = await Promise.allSettled([getPositions(v), getOrders(v)]);
    patch(v, {
      ...(p.status === 'fulfilled' ? { positions: p.value } : {}),
      ...(o.status === 'fulfilled' ? { orders: o.value } : {}),
      bookAt: Date.now(),
    });
  };

  return {
    // CoinePro-FX is signals only on Pro Chart: its book is never asked for.
    books: { tradeyar: empty(signedIn('tradeyar') ? 'checking' : 'signed-out'), coineprofx: empty('unavailable') },
    protections: readJson<Record<string, Protection>>(PROTECT_KEY, {}),
    leverage: readJson<Record<string, number>>(LEV_KEY, {}),
    mode: ((m) => (m === 'paper' || m === 'live' ? m : null))(readJson<string | null>(MODE_KEY, null)),
    refresh: (v, what = 'all') => {
      const key = `${v}:${what}`;
      inflight[key] ??= (async () => {
        if (what !== 'book') await loadAccount(v);
        if (what !== 'account') await loadBook(v);
      })().finally(() => { delete inflight[key]; });
      return inflight[key]!;
    },
    rememberProtection: (v, symbol, side, p) => {
      const next = { ...get().protections, [protectKey(v, symbol, side)]: { sl: p.sl, tp: p.tp, at: Date.now() } };
      set({ protections: next }); writeJson(PROTECT_KEY, next);
    },
    rememberLeverage: (symbol, lev) => { const next = { ...get().leverage, [symbol]: lev }; set({ leverage: next }); writeJson(LEV_KEY, next); },
    setMode: (mode) => { set({ mode }); writeJson(MODE_KEY, mode); },
  };
});

/** Whether a ticket for this venue can go to the exchange right now. */
export const canTrade = (v: Venue | null): boolean => Boolean(v && useTrading.getState().books[v].status === 'live');

/**
 * The stop and target on an open position: the platform's own when it reports them, else what this
 * browser last set, else the bracket of the order that opened it (TradeYar arms it after the fill).
 */
export function protectionOf(v: Venue, p: Position, orders: Order[], memory: Record<string, Protection>): { sl: number | null; tp: number | null } {
  if (p.stopLoss != null || p.takeProfit != null) return { sl: p.stopLoss, tp: p.takeProfit };
  const mem = memory[protectKey(v, p.symbol, p.side)];
  const opener = orders
    .filter((o) => o.symbol === p.symbol && !o.reduceOnly && (o.side === 'buy') === (p.side === 'long') && (o.stopLoss != null || o.takeProfit != null) && (o.protection === 'armed' || o.protection === 'pending'))
    .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))[0];
  if (mem && (!opener || mem.at >= (opener.createdAt ?? 0))) return { sl: mem.sl, tp: mem.tp };
  return opener ? { sl: opener.stopLoss, tp: opener.takeProfit } : { sl: null, tp: null };
}

export const workingOrders = (b: Book, symbol?: string) => b.orders.filter((o) => isWorking(o) && (!symbol || o.symbol === symbol));

let watching = false;
/** Keeps both platforms' books fresh: started once by the trade host. */
export function startTradingWatch(): () => void {
  if (watching) return () => undefined;
  watching = true;
  const st = () => useTrading.getState();
  const tick = () => {
    for (const v of LIVE_VENUES) {
      const b = st().books[v];
      if (!signedIn(v)) { if (b.status !== 'signed-out') useTrading.setState((s) => ({ books: { ...s.books, [v]: empty('signed-out') } })); continue; }
      const now = Date.now();
      const visible = pageVisible();
      const accountEvery = b.status === 'live' ? 15_000 : b.status === 'unavailable' ? 600_000 : 60_000;
      if ((b.status === 'checking' && !b.accountAt) || (visible && now - b.accountAt > accountEvery)) void st().refresh(v, 'account');
      else if (visible && b.status === 'live' && now - b.bookAt > 5_000) void st().refresh(v, 'book');
    }
  };
  const timer = setInterval(tick, 1000);
  tick();
  // A new session (or a sign-out) re-asks at once.
  let prev = useSession.getState().sessions;
  const off = useSession.subscribe((s) => {
    for (const v of LIVE_VENUES) {
      if (Boolean(s.sessions[v]) !== Boolean(prev[v]) || s.sessions[v]?.access !== prev[v]?.access) {
        useTrading.setState((x) => ({ books: { ...x.books, [v]: empty(s.sessions[v] ? 'checking' : 'signed-out') } }));
      }
    }
    prev = s.sessions;
  });
  const onFocus = () => { for (const v of LIVE_VENUES) if (signedIn(v)) void st().refresh(v, 'all'); };
  window.addEventListener('focus', onFocus);
  return () => { clearInterval(timer); off(); window.removeEventListener('focus', onFocus); watching = false; };
}
