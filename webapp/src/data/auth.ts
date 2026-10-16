// Sign-in, as the phone does it (core/auth: FederatedEmailAuthGateway, SessionController):
//   • register on TradeYar only (start → emailed code → verify);
//   • sign in and Google try TradeYar first, CoinePro-FX only on 401/403; a CoinePro-FX reader then
//     gets a TradeYar session too through auth/link/coinepro;
//   • refresh 60 s before expiry and on any 401; the session ends only when refresh itself is refused;
//   • signing out is local: the handles are forgotten (the relay never sees a logout call, as today).
import { HttpError, request, type RequestOptions } from './http';
import { useSession, type AuthUser, type Session } from './session';
import type { Backend } from './symbols';

const TY = '/up/tradeyar/api/mobile/v1/auth';
const FX = '/up/coineprofx/api/user/auth';

interface TokenResponse {
  access_token?: string; accessToken?: string; token?: string;
  refresh_token?: string; refreshToken?: string;
  expires_in?: number; expiresIn?: number;
  user?: Record<string, unknown>; profile?: Record<string, unknown>;
}

function readUser(u: Record<string, unknown> | undefined): AuthUser | null {
  if (!u) return null;
  const pick = (...keys: string[]) => keys.map((k) => u[k]).find((v) => v !== undefined && v !== null && v !== '');
  return {
    id: pick('telegram_id', 'user_id', 'userId', 'id') as string | number | undefined,
    name: pick('name', 'full_name', 'display_name', 'username') as string | undefined,
    email: pick('email') as string | undefined,
    plan: pick('plan') as string | undefined,
    planFa: pick('plan_fa') as string | undefined,
    vip: Boolean(pick('is_vip', 'vip', 'is_paid', 'paid')),
  };
}

function toSession(r: TokenResponse, prior?: Session): Session {
  const access = r.access_token ?? r.accessToken ?? r.token;
  if (!access) throw new HttpError(500, { detail: 'no token' }, null);
  const life = (r.expires_in ?? r.expiresIn ?? 3600) * 1000;
  const early = life > 600_000 ? 60_000 : life / 10;
  return {
    access,
    refresh: r.refresh_token ?? r.refreshToken ?? prior?.refresh ?? null,
    refreshAt: Date.now() + life - early,
    user: readUser(r.user ?? r.profile) ?? prior?.user ?? null,
  };
}

const refused = (e: unknown) => e instanceof HttpError && (e.status === 401 || e.status === 403);
// A refresh the platform cannot read (400/422: the relay sends an ended session's handle on as
// nothing) is a session that is over too, not a reason to keep retrying as a signed-in reader.
const ended = (e: unknown) => refused(e) || (e instanceof HttpError && (e.status === 400 || e.status === 422));

async function linkTradeYar(fxAccess: string): Promise<void> {
  try {
    const r = await request<TokenResponse>(`${TY}/link/coinepro`, { body: { coinepro_token: fxAccess } });
    useSession.getState().put('tradeyar', toSession(r));
  } catch { /* the crypto side stays a guest; forex works on its own */ }
}

// TradeYar down (5xx, no answer) is not a reason to keep a CoinePro-FX reader out (server report,
// 2026-10-16: a 502 from TradeYar left the sign-in failed without asking CoinePro-FX).
const unavailable = (e: unknown) => !(e instanceof HttpError) || e.status >= 500 || e.status === 0;

async function federated(tyPath: string, fxPath: string, body: unknown): Promise<void> {
  let tyDown: unknown = null;
  try {
    const r = await request<TokenResponse>(`${TY}/${tyPath}`, { body });
    useSession.getState().put('tradeyar', toSession(r));
    return;
  } catch (e) {
    if (unavailable(e)) tyDown = e;
    else if (!refused(e)) throw e;
  }
  let r: TokenResponse;
  try { r = await request<TokenResponse>(`${FX}/${fxPath}`, { body }); }
  catch (e) {
    // CoinePro-FX does not know the reader and TradeYar could not be asked: say TradeYar is out,
    // not «wrong password» for an account that may live there.
    throw tyDown && refused(e) ? tyDown : e;
  }
  const fx = toSession(r);
  useSession.getState().put('coineprofx', fx);
  await linkTradeYar(fx.access);
}

export const signIn = (email: string, password: string) => federated('login', 'login', { email: email.trim(), password });
export const signInWithGoogle = (idToken: string) => federated('google', 'google', { id_token: idToken });

export async function registerStart(email: string, password: string, fullName: string): Promise<{ token: string; cooldown: number }> {
  const r = await request<{ registration_token: string; cooldown_seconds?: number }>(`${TY}/register/start`, { body: { email: email.trim(), password, full_name: fullName.trim() } });
  return { token: r.registration_token, cooldown: r.cooldown_seconds ?? 60 };
}

export async function registerVerify(token: string, otp: string): Promise<void> {
  const r = await request<TokenResponse>(`${TY}/register/verify`, { body: { registration_token: token, otp: otp.trim() } });
  useSession.getState().put('tradeyar', toSession(r));
}

export async function forgotPassword(email: string): Promise<void> {
  const body = { email: email.trim() };
  const results = await Promise.allSettled([request(`${TY}/password/forgot`, { body }), request(`${FX}/password/forgot`, { body })]);
  if (results.every((x) => x.status === 'rejected')) throw (results[0] as PromiseRejectedResult).reason;
}

export async function resetPassword(token: string, password: string): Promise<void> {
  const body = { reset_token: token.replace(/\s+/g, '').toUpperCase(), new_password: password };
  try { await request(`${TY}/password/reset`, { body }); }
  catch { await request(`${FX}/password/reset`, { body }); }
}

export function signOut(): void { useSession.getState().signOut(); }

// ── Methods (what sign-in offers) ────────────────────────────────────────────────────────────────
export interface AuthMethods { google: boolean; googleClientId: string | null; emailPassword: boolean }
let methods: Promise<AuthMethods> | null = null;
export function authMethods(): Promise<AuthMethods> {
  if (!methods) {
    methods = request<Record<string, unknown>>(`${TY}/methods`)
      .then((m) => ({
        google: Boolean(m.google ?? true),
        googleClientId: (m.google_client_id ?? m.googleClientId ?? null) as string | null,
        emailPassword: Boolean(m.email_password ?? m.emailPassword ?? true),
      }))
      .catch(() => { methods = null; return { google: false, googleClientId: null, emailPassword: true }; });
  }
  return methods;
}

// ── Refresh ─────────────────────────────────────────────────────────────────────────────────────
const refreshing: Partial<Record<Backend, Promise<string | null>>> = {};

async function refresh(backend: Backend): Promise<string | null> {
  const s = useSession.getState().sessions[backend];
  if (!s?.refresh) { useSession.getState().put(backend, null); return null; }
  const path = backend === 'tradeyar' ? `${TY}/refresh` : `${FX}/refresh`;
  try {
    const r = await request<TokenResponse>(path, { body: { refresh_token: s.refresh } });
    const next = toSession(r, s);
    useSession.getState().put(backend, next);
    return next.access;
  } catch (e) {
    if (ended(e)) useSession.getState().put(backend, null);
    return ended(e) ? null : s.access;
  }
}

/** A fresh access handle for the backend, refreshing first when it is due. */
export async function accessFor(backend: Backend): Promise<string | null> {
  const s = useSession.getState().sessions[backend];
  if (!s) return null;
  if (s.refreshAt > Date.now()) return s.access;
  refreshing[backend] ??= refresh(backend).finally(() => { delete refreshing[backend]; });
  return refreshing[backend]!;
}

/** A member request: the session's bearer, one refresh-and-retry on 401. */
export async function authed<T>(backend: Backend, path: string, opts: RequestOptions = {}): Promise<T> {
  const token = await accessFor(backend);
  if (!token) throw new HttpError(401, null, null);
  try {
    return await request<T>(path, { ...opts, token });
  } catch (e) {
    if (!(e instanceof HttpError) || e.status !== 401) throw e;
    refreshing[backend] ??= refresh(backend).finally(() => { delete refreshing[backend]; });
    const again = await refreshing[backend]!;
    if (!again) throw e;
    return request<T>(path, { ...opts, token: again });
  }
}

// ── Google ──────────────────────────────────────────────────────────────────────────────────────
// A popup to Google's account chooser, returning to the terminal's callback page on this origin,
// which posts the ID token back (web/src/wasmJsMain/resources/google-callback.html).
export function googleIdToken(clientId: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const nonce = Math.random().toString(36).slice(2) + Date.now().toString(36);
    const redirect = new URL('/terminal/google-callback.html', window.location.origin).href;
    const url = 'https://accounts.google.com/o/oauth2/v2/auth?response_type=id_token&prompt=select_account'
      + `&scope=${encodeURIComponent('openid email profile')}&client_id=${encodeURIComponent(clientId)}`
      + `&redirect_uri=${encodeURIComponent(redirect)}&nonce=${nonce}`;
    const w = 480, h = 640;
    const popup = window.open(url, 'google-signin', `width=${w},height=${h},left=${(screen.width - w) / 2},top=${(screen.height - h) / 2}`);
    if (!popup) { reject(new Error('popup-blocked')); return; }
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== window.location.origin || (e.data as { kind?: string })?.kind !== 'google-id-token') return;
      cleanup();
      const d = e.data as { idToken?: string; error?: string };
      if (d.idToken) resolve(d.idToken); else reject(new Error(d.error ?? 'cancelled'));
    };
    const closed = setInterval(() => { if (popup.closed) { cleanup(); reject(new Error('cancelled')); } }, 600);
    const cleanup = () => { window.removeEventListener('message', onMessage); clearInterval(closed); };
    window.addEventListener('message', onMessage);
  });
}
