// Price alerts. Every alert is watched here, in the browser, against the live prices (the phone's
// GuestAlertMarketSource does the same); a signed-in reader's alert is also created on the
// platform that owns the symbol, so it fires on the phone and in Telegram when this tab is closed.
//   TradeYar     POST/GET/DELETE /up/tradeyar/api/mobile/v1/alerts   (any *USDT)
//   CoinePro-FX  POST/GET/DELETE /up/coineprofx/api/user/mobile/alerts (the fixed 19)
import { create } from 'zustand';
import { useMarket } from './market';
import { authed } from '../data/auth';
import { backendOf, FX_SYMBOLS } from '../data/symbols';
import { useSession } from '../data/session';
import { uuid } from '../data/http';

export type AlertCondition = 'above' | 'below' | 'cross';

export interface PriceAlert {
  id: string;
  symbol: string;
  condition: AlertCondition;
  value: number;
  active: boolean;
  createdAt: number;
  triggeredAt?: number;
  serverId?: string;
}

const KEY = 'pc.alerts.v1';
const read = (): PriceAlert[] => { try { const v = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown; return Array.isArray(v) ? (v as PriceAlert[]) : []; } catch { return []; } };

interface AlertsState {
  list: PriceAlert[];
  fired: PriceAlert | null;
  add(symbol: string, condition: AlertCondition, value: number): void;
  remove(id: string): void;
  toggle(id: string): void;
  dismiss(): void;
}

function serverPath(symbol: string): string | null {
  const backend = backendOf(symbol);
  if (!useSession.getState().sessions[backend]) return null;
  if (backend === 'coineprofx' && !FX_SYMBOLS.includes(symbol)) return null;
  return backend === 'tradeyar' ? '/up/tradeyar/api/mobile/v1/alerts' : '/up/coineprofx/api/user/mobile/alerts';
}

export const useAlerts = create<AlertsState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().list)); } catch { /* ignore */ } };
  return {
    list: read(),
    fired: null,
    add: (symbol, condition, value) => {
      const a: PriceAlert = { id: uuid(), symbol, condition, value, active: true, createdAt: Date.now() };
      set({ list: [a, ...get().list] }); save();
      const path = serverPath(symbol);
      if (path) {
        const backend = backendOf(symbol);
        void authed<{ alert?: { id?: string | number } }>(backend, path, {
          body: { symbol, condition: condition === 'cross' ? 'cross' : condition, value, trigger: 'once' },
        }).then((r) => {
          const sid = r.alert?.id;
          if (sid != null) { set({ list: get().list.map((x) => (x.id === a.id ? { ...x, serverId: String(sid) } : x)) }); save(); }
        }).catch(() => undefined);
      }
    },
    remove: (id) => {
      const a = get().list.find((x) => x.id === id);
      set({ list: get().list.filter((x) => x.id !== id) }); save();
      const path = a && serverPath(a.symbol);
      if (a?.serverId && path) void authed(backendOf(a.symbol), `${path}/${encodeURIComponent(a.serverId)}`, { method: 'DELETE' }).catch(() => undefined);
    },
    toggle: (id) => { set({ list: get().list.map((x) => (x.id === id ? { ...x, active: !x.active, triggeredAt: x.active ? x.triggeredAt : undefined } : x)) }); save(); },
    dismiss: () => set({ fired: null }),
  };
});

// ── Watching ────────────────────────────────────────────────────────────────────────────────────
const lastSeen = new Map<string, number>();

function notify(a: PriceAlert, price: number) {
  try {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(`${a.symbol} ${a.condition === 'below' ? '≤' : a.condition === 'above' ? '≥' : '⇄'} ${a.value}`, { body: `${price}`, tag: a.id, icon: `${import.meta.env.BASE_URL}generated/app-icons/favicon-32.png` });
    }
  } catch { /* notifications unavailable */ }
}

let watching = false;
export function startAlertWatch(): void {
  if (watching) return;
  watching = true;
  useMarket.subscribe((m) => {
    const st = useAlerts.getState();
    const active = st.list.filter((a) => a.active);
    if (!active.length) return;
    let changed = false;
    const next = st.list.map((a) => {
      if (!a.active) return a;
      const tk = m.tickers[a.symbol];
      if (!tk) return a;
      const prev = lastSeen.get(a.id);
      lastSeen.set(a.id, tk.last);
      const hit = a.condition === 'above' ? tk.last >= a.value
        : a.condition === 'below' ? tk.last <= a.value
        : prev != null && ((prev < a.value && tk.last >= a.value) || (prev > a.value && tk.last <= a.value));
      if (!hit) return a;
      changed = true;
      const fired = { ...a, active: false, triggeredAt: Date.now() };
      notify(fired, tk.last);
      useAlerts.setState({ fired });
      return fired;
    });
    if (changed) {
      useAlerts.setState({ list: next });
      try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
    }
  });
}
