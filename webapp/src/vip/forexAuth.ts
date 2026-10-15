// A CoinePro-FX session for a reader who signed in on TradeYar. Sign-in tries TradeYar first and
// only falls back to CoinePro-FX on a refusal (data/auth.ts), so most readers hold no forex session
// at all — and the forex VIP lives on CoinePro-FX. This signs in (or registers, with the e-mailed
// code) on CoinePro-FX alone, through the same relay, keeping the TradeYar session as it is.
import { HttpError, request } from '../data/http';
import { useSession, type Session } from '../data/session';

const FX = '/up/coineprofx/api/user/auth';

interface TokenResponse { access_token?: string; token?: string; refresh_token?: string; expires_in?: number; user?: Record<string, unknown>; profile?: Record<string, unknown> }

export function fxSession(r: TokenResponse): Session {
  const access = r.access_token ?? r.token;
  if (!access) throw new HttpError(500, { detail: 'no token' }, null);
  const life = (r.expires_in ?? 3600) * 1000;
  const u = r.user ?? r.profile ?? null;
  const pick = (k: string) => (u && u[k] != null && u[k] !== '' ? String(u[k]) : undefined);
  return {
    access,
    refresh: r.refresh_token ?? null,
    refreshAt: Date.now() + life - (life > 600_000 ? 60_000 : life / 10),
    user: u ? { id: pick('id') ?? pick('user_id'), name: pick('full_name') ?? pick('name') ?? pick('first_name'), email: pick('email') } : null,
  };
}

export async function forexSignIn(email: string, password: string): Promise<void> {
  const r = await request<TokenResponse>(`${FX}/login`, { body: { email: email.trim(), password } });
  useSession.getState().put('coineprofx', fxSession(r));
}

export async function forexRegisterStart(email: string, password: string, fullName: string): Promise<{ token: string; cooldown: number }> {
  const r = await request<{ registration_token: string; cooldown_seconds?: number }>(`${FX}/register/start`, { body: { email: email.trim(), password, full_name: fullName.trim() } });
  return { token: r.registration_token, cooldown: r.cooldown_seconds ?? 60 };
}

export async function forexRegisterVerify(token: string, otp: string): Promise<void> {
  const r = await request<TokenResponse>(`${FX}/register/verify`, { body: { registration_token: token, otp: otp.trim() } });
  useSession.getState().put('coineprofx', fxSession(r));
}
