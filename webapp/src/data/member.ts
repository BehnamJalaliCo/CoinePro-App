// The member routes of both platforms, as the app calls them (core/account, core/notifications,
// core/execution, core/membership…). TradeYar's prefix is api/mobile/v1, CoinePro-FX's user/mobile;
// the reader's active platform picks one, falling back to whichever session exists.
import { authed } from './auth';
import { request, type RequestOptions } from './http';
import { useSession } from './session';
import type { Backend } from './symbols';
import { create } from 'zustand';

const KEY = 'pc.platform.v1';
interface PlatformState { platform: Backend; set(p: Backend): void }
export const usePlatform = create<PlatformState>((set) => ({
  platform: ((): Backend => { try { return localStorage.getItem(KEY) === 'coineprofx' ? 'coineprofx' : 'tradeyar'; } catch { return 'tradeyar'; } })(),
  set: (platform) => { set({ platform }); try { localStorage.setItem(KEY, platform); } catch { /* ignore */ } },
}));

/** The platform to ask: the chosen one when signed in there, else the one with a session. */
export function activeBackend(): Backend | null {
  const s = useSession.getState().sessions;
  const want = usePlatform.getState().platform;
  if (s[want]) return want;
  if (s.tradeyar) return 'tradeyar';
  if (s.coineprofx) return 'coineprofx';
  return null;
}

export const ROOT: Record<Backend, string> = { tradeyar: '/up/tradeyar/api/mobile/v1', coineprofx: '/up/coineprofx/api/user/mobile' };

/** A member call on the active platform: `path` is relative to its prefix. */
export async function member<T>(path: string, opts: RequestOptions = {}, backend: Backend | null = activeBackend()): Promise<T> {
  if (!backend) throw new Error('signed-out');
  return authed<T>(backend, `${ROOT[backend]}/${path}`, opts);
}

export const ty = <T>(path: string, opts: RequestOptions = {}) => authed<T>('tradeyar', `/up/tradeyar/${path}`, opts);
export const fx = <T>(path: string, opts: RequestOptions = {}) => authed<T>('coineprofx', `/up/coineprofx/api/${path}`, opts);
export const pub = <T>(path: string, opts: RequestOptions = {}) => request<T>(path, opts);

export const num = (v: unknown): number | null => { const n = Number(v); return v == null || v === '' || !Number.isFinite(n) ? null : n; };
export const str = (v: unknown): string => (v == null ? '' : String(v));
export const ms = (v: unknown): number | null => { if (v == null || v === '') return null; const t = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v)); return Number.isFinite(t) ? t : null; };
