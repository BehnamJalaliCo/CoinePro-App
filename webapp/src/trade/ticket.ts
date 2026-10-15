// The order-ticket contract (BRIEF, areas B and C): the chart legend's Buy/Sell boxes, the price
// axis «+», the chart menu and the order panel dispatch
//   new CustomEvent('pc:order-ticket', { cancelable: true, detail: { symbol, side, price?, type?,
//     quantity?, stopLoss?, takeProfit?, source } })
// and fall back to paper trading unless someone calls preventDefault(). This listens.
//
// • An order from the chart always opens the ticket, as TradingView's legend does: in Live mode
//   when the symbol's platform can trade right now (or is still being asked), else in Paper mode
//   with the Live switch explaining what is missing.
// • An order from the order panel is already a full ticket: it is taken only to go live (the
//   ticket opens on its confirmation); otherwise the panel's own paper order stands.
// • The reader's last choice of Paper keeps orders on paper until they switch back.
// • Forex, metals and indices are signals only on Pro Chart (CoinePro-FX's executor is off): never
//   live; from the chart the ticket opens on Paper with a note saying so, from the panel the panel's
//   own paper order stands.
import { create } from 'zustand';
import { useTrading } from './store';
import { liveVenueOf } from './venue';

export interface TicketDetail {
  symbol: string;
  side: 'buy' | 'sell';
  price?: number;
  type?: 'market' | 'limit' | 'stop';
  quantity?: number;
  stopLoss?: number;
  takeProfit?: number;
  source?: string;
}

export interface TicketRequest extends TicketDetail { mode: 'live' | 'paper'; at: number }

interface TicketState {
  req: TicketRequest | null;
  open(r: Omit<TicketRequest, 'at'>): void;
  close(): void;
}

export const useTicket = create<TicketState>((set) => ({
  req: null,
  open: (r) => set({ req: { ...r, at: Date.now() } }),
  close: () => set({ req: null }),
}));

const finite = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);

/** Cleans a detail from another area: only the fields the contract names, of the right types. */
export function cleanDetail(d: unknown): TicketDetail | null {
  if (!d || typeof d !== 'object') return null;
  const x = d as Record<string, unknown>;
  const symbol = typeof x.symbol === 'string' ? x.symbol.trim().toUpperCase() : '';
  if (!symbol || (x.side !== 'buy' && x.side !== 'sell')) return null;
  const type = x.type === 'market' || x.type === 'limit' || x.type === 'stop' ? x.type : undefined;
  const pos = (v: unknown) => (finite(v) && v > 0 ? v : undefined);
  return {
    symbol, side: x.side, type,
    price: pos(x.price), quantity: pos(x.quantity), stopLoss: pos(x.stopLoss), takeProfit: pos(x.takeProfit),
    source: typeof x.source === 'string' ? x.source : undefined,
  };
}

/** Live or paper for this order, and whether to take it from the dispatcher. */
export function routeTicket(d: TicketDetail): { take: boolean; mode: 'live' | 'paper' } {
  const v = liveVenueOf(d.symbol);
  const st = useTrading.getState();
  const status = v ? st.books[v].status : null;
  const live = (status === 'live' || status === 'checking') && st.mode !== 'paper';
  if (d.source === 'panel') return { take: live, mode: 'live' };
  return { take: true, mode: live ? 'live' : 'paper' };
}

export function onOrderTicket(e: Event): void {
  if (e.defaultPrevented) return;
  const d = cleanDetail((e as CustomEvent).detail);
  if (!d) return;
  const r = routeTicket(d);
  if (!r.take) return;
  e.preventDefault();
  useTicket.getState().open({ ...d, mode: r.mode });
}

/** Listens for the contract's event; returns the unsubscribe. */
export function listenForTickets(): () => void {
  window.addEventListener('pc:order-ticket', onOrderTicket);
  return () => window.removeEventListener('pc:order-ticket', onOrderTicket);
}
