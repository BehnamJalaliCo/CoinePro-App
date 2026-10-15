// The reader's sessions on the two platforms. The relay hands the page `pch_…` handles in place of
// real tokens (web/relay/relay.py); a handle only works together with this browser's HttpOnly
// `pc_session` cookie, so keeping handles in localStorage exposes nothing.
import { create } from 'zustand';
import type { Backend } from './symbols';

export interface AuthUser {
  id?: string | number;
  name?: string;
  email?: string;
  plan?: string;
  planFa?: string;
  vip?: boolean;
}

export interface Session {
  access: string;
  refresh: string | null;
  /** ms epoch when the access handle should be refreshed. */
  refreshAt: number;
  user: AuthUser | null;
}

const KEY = 'pc.sessions.v1';
type Sessions = Partial<Record<Backend, Session>>;

function read(): Sessions {
  try { return (JSON.parse(localStorage.getItem(KEY) ?? '{}') as Sessions) ?? {}; } catch { return {}; }
}

interface SessionState {
  sessions: Sessions;
  put(backend: Backend, s: Session | null): void;
  signOut(): void;
}

export const useSession = create<SessionState>((set, get) => {
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(get().sessions)); } catch { /* ignore */ } };
  return {
    sessions: read(),
    put: (backend, s) => {
      const next = { ...get().sessions };
      if (s) next[backend] = s; else delete next[backend];
      set({ sessions: next }); save();
    },
    signOut: () => { set({ sessions: {} }); save(); },
  };
});

export function bearer(backend: Backend): string | null {
  return useSession.getState().sessions[backend]?.access ?? null;
}

export function signedIn(): boolean {
  const s = useSession.getState().sessions;
  return Boolean(s.tradeyar || s.coineprofx);
}
