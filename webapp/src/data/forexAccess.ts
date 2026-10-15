// The token CoinePro-FX's chart route needs. A guest gets a two-hour guest token (POST
// user/auth/guest, wrapped by the relay into a `pch_…` handle tied to this browser's cookie); a
// signed-in forex reader gets an academy token (POST user/academy-token, ~12 h, not wrapped).
// The rules are the phone's (GuestTokenStore.kt, AcademyTokenStore.kt, CandleBudget.kt).
import { HttpError, request } from './http';

interface Held { token: string; expiresAt: number }

const GUEST_KEY = 'pc.fx.guest.v1';
let guest: Held | null = readGuest();
let guestMint: Promise<string> | null = null;
let guestBackoffMs = 15_000;
let guestBlockedUntil = 0;

function readGuest(): Held | null {
  try {
    const v = JSON.parse(sessionStorage.getItem(GUEST_KEY) ?? 'null') as Held | null;
    return v && typeof v.token === 'string' && v.expiresAt > Date.now() ? v : null;
  } catch { return null; }
}

function keepGuest(h: Held | null) {
  guest = h;
  try { if (h) sessionStorage.setItem(GUEST_KEY, JSON.stringify(h)); else sessionStorage.removeItem(GUEST_KEY); } catch { /* ignore */ }
}

async function mintGuest(): Promise<string> {
  if (Date.now() < guestBlockedUntil) throw new HttpError(429, null, guestBlockedUntil - Date.now());
  try {
    const r = await request<{ token: string; expires_in?: number; expiresIn?: number }>('/up/coineprofx/api/user/auth/guest', { method: 'POST' });
    const life = (r.expires_in ?? r.expiresIn ?? 7200) * 1000;
    keepGuest({ token: r.token, expiresAt: Date.now() + life - 120_000 });
    guestBackoffMs = 15_000;
    return r.token;
  } catch (e) {
    const wait = e instanceof HttpError && e.retryAfterMs ? e.retryAfterMs : guestBackoffMs;
    guestBlockedUntil = Date.now() + wait;
    guestBackoffMs = Math.min(guestBackoffMs * 2, 300_000);
    throw e;
  }
}

/** A guest token good for at least two more minutes; one mint in flight at a time. */
export function guestToken(): Promise<string> {
  if (guest && guest.expiresAt > Date.now()) return Promise.resolve(guest.token);
  if (!guestMint) guestMint = mintGuest().finally(() => { guestMint = null; });
  return guestMint;
}

/** Called after a 401 on the chart route: the handle died (relay restart, cookie cleared). */
export function dropGuestToken(): void { keepGuest(null); }

// ── Academy token (signed-in forex readers) ──────────────────────────────────────────────────────
let academy: Held | null = null;
let academyFailedAt = 0;
let academyMint: Promise<string | null> | null = null;

/** The academy token for a signed-in forex session, or null (use the guest token). */
export function academyToken(sessionBearer: string | null): Promise<string | null> {
  if (!sessionBearer) return Promise.resolve(null);
  if (academy && academy.expiresAt > Date.now()) return Promise.resolve(academy.token);
  if (Date.now() - academyFailedAt < 60_000) return Promise.resolve(null);
  if (!academyMint) {
    academyMint = request<{ token: string; expires_in?: number; expiresIn?: number }>('/up/coineprofx/api/user/academy-token', { method: 'POST', token: sessionBearer })
      .then((r) => {
        academy = { token: r.token, expiresAt: Date.now() + (r.expires_in ?? r.expiresIn ?? 43_200) * 1000 - 300_000 };
        return academy.token;
      })
      .catch(() => { academyFailedAt = Date.now(); return null; })
      .finally(() => { academyMint = null; });
  }
  return academyMint;
}

export function dropAcademyToken(): void { academy = null; }

// ── Guest candle budget ──────────────────────────────────────────────────────────────────────────
// CoinePro-FX lets a guest address make 120 chart requests per 10 minutes. The chart being opened
// may use 110 of them; the live edge 70; bulk work (screener, history fill) 20.
export type Urgency = 'reader' | 'background' | 'bulk';
const SHARE: Record<Urgency, number> = { reader: 110, background: 70, bulk: 20 };
const WINDOW = 600_000;
const spent: number[] = [];

/** True and counted when a guest request of this urgency fits in the window. */
export function spendGuestBudget(urgency: Urgency): boolean {
  const now = Date.now();
  while (spent.length && spent[0]! < now - WINDOW) spent.shift();
  if (spent.length >= SHARE[urgency]) return false;
  spent.push(now);
  return true;
}
