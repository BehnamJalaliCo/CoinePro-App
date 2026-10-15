// The reader's VIP state on both platforms, kept fresh for the banners, the status page and the
// funnel: read on sign-in, every few minutes while the tab is visible, and on demand. When the
// free tier is gained or lost the page says so to whoever decides premium features
// (`pc:entitlements-changed`), and a new VIP notice rings like an alert does.
import { create } from 'zustand';
import { useSession } from '../data/session';
import { pageVisible } from '../data/http';
import { readNotify, inQuietHours, chime } from '../state/notify';
import { forexState, vipState, type ForexState, type VipState } from './api';

interface VipStore {
  crypto: VipState | null;
  forex: ForexState | null;
  cryptoError: boolean;
  forexError: boolean;
  loadedAt: number;
  refresh(): Promise<void>;
  setCrypto(v: VipState): void;
  setForex(v: ForexState): void;
  clear(): void;
}

const SEEN = 'pc.vip.seen.v1';
const readSeen = (): string => { try { return localStorage.getItem(SEEN) ?? ''; } catch { return ''; } };
const writeSeen = (id: string) => { try { localStorage.setItem(SEEN, id); } catch { /* storage off */ } };

function announce(prev: VipState | null, next: VipState): void {
  if (prev && prev.entitled !== next.entitled) window.dispatchEvent(new CustomEvent('pc:entitlements-changed', { detail: { platform: 'tradeyar', vip: next.entitled, status: next.status } }));
  const newest = next.events[0];
  if (!newest) return;
  const seen = readSeen();
  if (seen === newest.id) return;
  writeSeen(newest.id);
  // The first read only records where the reader is; later notices ring.
  if (!prev || !seen) return;
  const local = readNotify();
  if (inQuietHours(new Date(), local)) return;
  if (local.sound) chime();
  try {
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      new Notification(newest.title, { body: newest.body, tag: `vip-${newest.id}`, icon: `${import.meta.env.BASE_URL}generated/app-icons/favicon-32.png` });
    }
  } catch { /* notifications unavailable */ }
}

export const useVip = create<VipStore>((set, get) => ({
  crypto: null,
  forex: null,
  cryptoError: false,
  forexError: false,
  loadedAt: 0,
  refresh: async () => {
    const s = useSession.getState().sessions;
    const jobs: Promise<void>[] = [];
    if (s.tradeyar) {
      jobs.push(vipState().then((v) => { announce(get().crypto, v); set({ crypto: v, cryptoError: false }); }).catch(() => set({ cryptoError: true })));
    } else set({ crypto: null });
    if (s.coineprofx) {
      jobs.push(forexState().then((v) => {
        const prev = get().forex;
        if (prev && prev.entitled !== v.entitled) window.dispatchEvent(new CustomEvent('pc:entitlements-changed', { detail: { platform: 'coineprofx', vip: v.entitled, status: v.status } }));
        set({ forex: v, forexError: false });
      }).catch(() => set({ forexError: true })));
    } else set({ forex: null });
    await Promise.all(jobs);
    set({ loadedAt: Date.now() });
  },
  setCrypto: (v) => { announce(get().crypto, v); set({ crypto: v }); },
  setForex: (v) => set({ forex: v }),
  clear: () => set({ crypto: null, forex: null, loadedAt: 0 }),
}));

let timer: ReturnType<typeof setInterval> | null = null;

/** Starts the poller once: on sign-in/out, every five minutes while visible, and on return to the tab. */
export function startVipPoller(everyMs = 5 * 60_000): () => void {
  if (timer) return () => undefined;
  const tick = () => { if (pageVisible()) void useVip.getState().refresh(); };
  let last = '';
  const unsub = useSession.subscribe((st) => {
    const key = `${Boolean(st.sessions.tradeyar)}${Boolean(st.sessions.coineprofx)}`;
    if (key === last) return;
    last = key;
    if (key === 'falsefalse') useVip.getState().clear(); else tick();
  });
  last = `${Boolean(useSession.getState().sessions.tradeyar)}${Boolean(useSession.getState().sessions.coineprofx)}`;
  if (last !== 'falsefalse') tick();
  timer = setInterval(tick, everyMs);
  const onVis = () => { if (pageVisible() && Date.now() - useVip.getState().loadedAt > 60_000) tick(); };
  document.addEventListener('visibilitychange', onVis);
  return () => { unsub(); if (timer) clearInterval(timer); timer = null; document.removeEventListener('visibilitychange', onVis); };
}
