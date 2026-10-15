// What the VIP pages decide on their own: which step of the funnel the reader is on, how serious
// the VIP state is, which zone a balance sits in, and whether a typed UID can be sent. Pure, so the
// unit tests pin it; the rules themselves (thresholds, patterns) always come from the servers.
import type { CryptoRules, Registration, VipState } from './api';

export type Market = 'crypto' | 'forex';
export type CryptoStep = 'account' | 'exchange' | 'deposit' | 'uid' | 'result';

/** The funnel's current step. Steps 2 and 3 are the reader's own (ticked locally) until a UID is sent. */
export function cryptoStep(signed: boolean, reg: Registration | null, local: { registered: boolean; deposited: boolean }): CryptoStep {
  if (!signed) return 'account';
  if (reg && reg.status !== 'awaiting_uid') return 'result';
  if (!local.registered) return 'exchange';
  if (!local.deposited) return 'deposit';
  return 'uid';
}

export type Outcome = 'verifying' | 'vip' | 'approved' | 'deposit' | 'not_indexed' | 'error' | 'pending';

/** What the result step shows, merging the registration with the VIP gate (which may be ahead of it). */
export function outcomeOf(reg: Registration | null, vip: VipState | null): Outcome {
  if (vip?.entitled) return 'vip';
  if (vip?.status === 'pending_deposit') return 'deposit';
  if (vip?.status === 'awaiting_referral') return 'not_indexed';
  switch (reg?.status) {
    case 'approved': return 'approved';
    case 'pending_deposit': return 'deposit';
    case 'rejected_referral': return 'not_indexed';
    case 'error': return 'error';
    case 'verifying': return 'verifying';
    default: return 'pending';
  }
}

export type Severity = 'ok' | 'low' | 'critical' | 'removed' | 'pending' | 'none';

export function severityOf(vip: VipState | null): Severity {
  switch (vip?.status) {
    case 'active': return 'ok';
    case 'low': return 'low';
    case 'critical': return 'critical';
    case 'removed': return 'removed';
    case 'pending_deposit': case 'awaiting_referral': return 'pending';
    default: return 'none';
  }
}

export type Zone = 'healthy' | 'warn' | 'critical' | 'unknown';

/** Where a balance sits against the server's thresholds. */
export function zoneOf(balance: number | null, warn: number | null, critical: number | null): Zone {
  if (balance == null || warn == null || critical == null) return 'unknown';
  if (balance >= warn) return 'healthy';
  if (balance >= critical) return 'warn';
  return 'critical';
}

/** Position (0…1) of a value on a gauge that ends a little past the entry threshold. */
export function gaugeAt(value: number | null, top: number | null): number {
  if (value == null || top == null || top <= 0) return 0;
  return Math.max(0, Math.min(1, value / (top * 1.25)));
}

const FOLD: Record<string, string> = Object.fromEntries([...'۰۱۲۳۴۵۶۷۸۹'].map((d, i) => [d, String(i)]).concat([...'٠١٢٣٤٥٦٧٨٩'].map((d, i) => [d, String(i)])));

export type UidError = 'empty' | 'phone' | 'email' | 'shape';

/** The UID as the server expects it, or why it is not one (the bot's own reading, uid_intake.classify_uid). */
export function cleanUid(raw: string, exchange: string, rules: CryptoRules | null): { uid: string; error: UidError | null } {
  let s = [...(raw ?? '')].map((c) => FOLD[c] ?? c).join('');
  s = s.replace(/^(uid|id)\s*[:=：-]?\s*/i, '').replace(/[\s‌‍‎‏`'"«»]/g, '');
  if (!s) return { uid: '', error: 'empty' };
  if (s.includes('@') && s.includes('.')) return { uid: s, error: 'email' };
  const up = s.toUpperCase();
  if (/^(\+?98|0098|0)?9\d{9}$/.test(s.replace(/-/g, '')) && s.replace(/\D/g, '').length > 10) return { uid: s, error: 'phone' };
  const pattern = rules?.exchanges.find((x) => x.id === exchange)?.uidPattern;
  const uid = up.startsWith('LBA') ? up : s.replace(/-/g, '');
  if (pattern) {
    try { if (!new RegExp(pattern).test(uid)) return { uid, error: 'shape' }; } catch { /* a pattern we cannot read: let the server decide */ }
  } else if (!/^[\x21-\x7e]{4,64}$/.test(uid)) return { uid, error: 'shape' };
  return { uid, error: null };
}

/** Hours and minutes until a moment (never negative). */
export function timeLeft(at: number | null, now = Date.now()): { h: number; m: number; over: boolean } | null {
  if (at == null) return null;
  const d = at - now;
  if (d <= 0) return { h: 0, m: 0, over: true };
  return { h: Math.floor(d / 3_600_000), m: Math.floor((d % 3_600_000) / 60_000), over: false };
}

export const usdt = (v: number | null | undefined, digits = 2) =>
  v == null || !Number.isFinite(v) ? '—' : v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: digits });

/**
 * The bot's Telegram HTML (only <b>, <i>, <code> and line breaks) as plain segments the page can
 * render without injecting markup: [{ text, bold }].
 */
export function segments(html: string): { text: string; bold: boolean }[] {
  const out: { text: string; bold: boolean }[] = [];
  const decode = (s: string) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&');
  let bold = false;
  for (const part of (html ?? '').split(/(<\/?(?:b|strong|i|em|code)>)/i)) {
    if (/^<b>|^<strong>/i.test(part)) { bold = true; continue; }
    if (/^<\/b>|^<\/strong>/i.test(part)) { bold = false; continue; }
    if (/^<\/?(?:i|em|code)>$/i.test(part)) continue;
    const text = decode(part.replace(/<[^>]+>/g, ''));
    if (text) out.push({ text, bold });
  }
  return out;
}

/** Ticks the reader set on the funnel's own steps (per browser). */
const KEY = 'pc.vip.v1';
export interface VipLocal { market: Market; exchange: string; registered: boolean; deposited: boolean; dismissed: Record<string, number> }
export const VIP_LOCAL_DEFAULT: VipLocal = { market: 'crypto', exchange: '', registered: false, deposited: false, dismissed: {} };
export function readLocal(): VipLocal {
  try { return { ...VIP_LOCAL_DEFAULT, ...(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<VipLocal>) }; } catch { return { ...VIP_LOCAL_DEFAULT }; }
}
export function writeLocal(v: VipLocal): void { try { localStorage.setItem(KEY, JSON.stringify(v)); } catch { /* storage off */ } }
