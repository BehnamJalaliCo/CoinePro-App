// What the reader may reach, from both platforms: Premium is unlocked when either one says so
// (`premium`), and a member of the TradeYar VIP channel gets everything as plan «vip» with no end
// date (TradeYar payments.py, 2026-10-15). This store is the one place the web app decides it;
// the premium dialog, the Pro page and the account menu read it.
import { create } from 'zustand';
import { authed } from '../data/auth';
import { useSession } from '../data/session';
import { VENUES, type Venue } from './venue';

export interface Entitlement { all: boolean; premium: boolean; plan: string | null; endsAt: string | null }

interface EntitlementState {
  byVenue: Partial<Record<Venue, Entitlement>>;
  loadedAt: number;
  load(): Promise<void>;
}

const PATH: Record<Venue, string> = {
  tradeyar: '/up/tradeyar/api/mobile/v1/entitlements',
  coineprofx: '/up/coineprofx/api/user/mobile/entitlements',
};

export function readEntitlement(x: Record<string, unknown>): Entitlement {
  const plan = x.plan == null || x.plan === '' ? null : String(x.plan).toLowerCase();
  return { all: x.all !== false, premium: x.premium === true || plan === 'vip', plan, endsAt: x.ends_at ? String(x.ends_at) : null };
}

/** Premium, the plan that gives it (VIP first), and when it ends, across both platforms. */
export function combine(list: (Entitlement | undefined)[]): { premium: boolean; vip: boolean; plan: string | null; endsAt: string | null } {
  const got = list.filter((x): x is Entitlement => Boolean(x));
  const vip = got.find((x) => x.plan === 'vip');
  if (vip) return { premium: true, vip: true, plan: 'vip', endsAt: null };
  const paid = got.filter((x) => x.premium).sort((a, b) => (b.endsAt ?? '').localeCompare(a.endsAt ?? ''))[0];
  return paid ? { premium: true, vip: false, plan: paid.plan, endsAt: paid.endsAt } : { premium: false, vip: false, plan: null, endsAt: null };
}

let inflight: Promise<void> | null = null;

export const useEntitlements = create<EntitlementState>((set) => ({
  byVenue: {},
  loadedAt: 0,
  load: () => {
    inflight ??= (async () => {
      const sessions = useSession.getState().sessions;
      const next: Partial<Record<Venue, Entitlement>> = {};
      await Promise.all(VENUES.filter((v) => sessions[v]).map(async (v) => {
        try { next[v] = readEntitlement(await authed<Record<string, unknown>>(v, PATH[v])); } catch { /* that platform says nothing */ }
      }));
      set({ byVenue: next, loadedAt: Date.now() });
    })().finally(() => { inflight = null; });
    return inflight;
  },
}));

/** The reader's premium state; re-read by the components that show it. */
export function usePremium(): { premium: boolean; vip: boolean; plan: string | null; endsAt: string | null } {
  const by = useEntitlements((s) => s.byVenue);
  return combine([by.tradeyar, by.coineprofx]);
}

/** Outside React: is premium unlocked right now? */
export function isPremium(): boolean {
  const by = useEntitlements.getState().byVenue;
  return combine([by.tradeyar, by.coineprofx]).premium;
}

let watching = false;
/** Loads once per session change and every ten minutes. */
export function startEntitlementWatch(): () => void {
  if (watching) return () => undefined;
  watching = true;
  void useEntitlements.getState().load();
  let prev = useSession.getState().sessions;
  const off = useSession.subscribe((s) => {
    if (VENUES.some((v) => s.sessions[v]?.access !== prev[v]?.access)) void useEntitlements.getState().load();
    prev = s.sessions;
  });
  const timer = setInterval(() => void useEntitlements.getState().load(), 600_000);
  // The VIP pages notice a membership granted or removed (the balance rules) before the next poll.
  const changed = () => void useEntitlements.getState().load();
  window.addEventListener('pc:entitlements-changed', changed);
  return () => { off(); clearInterval(timer); window.removeEventListener('pc:entitlements-changed', changed); watching = false; };
}
