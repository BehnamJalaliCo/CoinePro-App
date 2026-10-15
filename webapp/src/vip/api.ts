// The VIP gate of both platforms, as the website reads it. Every number and sentence here comes
// from the servers: TradeYar's public rules (app/vip/service.py, the values its monitor applies),
// its membership routes for the reader's own state, and CoinePro-FX's forex-VIP routes. Nothing
// about the deal is written into the page.
import { request } from '../data/http';
import { ty, fx, num, str } from '../data/member';

// ── TradeYar (crypto) rules ──────────────────────────────────────────────────────────────────────
export interface Exchange { id: string; label: string; referralUrl: string; uidPattern: string | null; uidExample: string | null; copyTrade: boolean }
export interface CryptoRules {
  exchanges: Exchange[];
  defaultExchange: string;
  minDeposit: number | null;
  registrationMinDeposit: number | null;
  inviteTtlHours: number | null;
  warnBelow: number | null;
  criticalBelow: number | null;
  graceHours: number | null;
  rejoinAt: number | null;
  warnRepeatHours: number | null;
  balanceEverySeconds: number | null;
  pendingEverySeconds: number | null;
  pendingWindowHours: number | null;
  pendingReminderHours: number[];
  uidRecheckEverySeconds: number | null;
  uidRecheckHours: number | null;
  exchangeLagFa: string;
  supportUrl: string | null;
  texts: Record<string, string>;
  registrationStatusFa: Record<string, string>;
  notices: Record<string, { level: string; title: string }>;
  noticeFa: string;
  /** False when only the older public answer was available (links and minimum only). */
  complete: boolean;
}

const RULES = '/up/tradeyar/api/v1/public/vip/rules';
const TERMS = '/up/tradeyar/api/v1/public/membership';

export function readCryptoRules(r: Record<string, unknown>, terms: Record<string, unknown> | null = null): CryptoRules {
  const g = (o: unknown, k: string): unknown => (o && typeof o === 'object' ? (o as Record<string, unknown>)[k] : undefined);
  const entry = g(r, 'entry'), stay = g(r, 'stay'), checks = g(r, 'checks');
  const exchanges = (Array.isArray(r.exchanges) ? (r.exchanges as Record<string, unknown>[]) : []).map((x) => ({
    id: str(x.id), label: str(x.label) || str(x.id), referralUrl: str(x.referral_url), uidPattern: x.uid_pattern ? str(x.uid_pattern) : null,
    uidExample: x.uid_example ? str(x.uid_example) : null, copyTrade: Boolean(x.copy_trade),
  })).filter((x) => x.id && x.referralUrl);
  // The older public answer (camelCase or snake_case) carries the links and the minimum.
  if (!exchanges.length && terms) {
    const pick = (...k: string[]) => k.map((x) => terms[x]).find((v) => typeof v === 'string' && v) as string | undefined;
    const lb = pick('lbankReferralUrl', 'lbank_referral_url');
    const ob = pick('ourbitReferralUrl', 'ourbit_referral_url');
    if (lb) exchanges.push({ id: 'lbank', label: 'LBank', referralUrl: lb, uidPattern: null, uidExample: null, copyTrade: true });
    if (ob) exchanges.push({ id: 'ourbit', label: 'Ourbit', referralUrl: ob, uidPattern: null, uidExample: null, copyTrade: false });
  }
  const texts: Record<string, string> = {};
  const th = g(r, 'texts_html');
  if (th && typeof th === 'object') for (const [k, v] of Object.entries(th)) if (typeof v === 'string') texts[k] = v;
  const statuses: Record<string, string> = {};
  const rs = g(r, 'registration_status_fa');
  if (rs && typeof rs === 'object') for (const [k, v] of Object.entries(rs)) if (typeof v === 'string') statuses[k] = v;
  const notices: Record<string, { level: string; title: string }> = {};
  const ns = g(r, 'notices');
  if (ns && typeof ns === 'object') for (const [k, v] of Object.entries(ns as Record<string, Record<string, unknown>>)) notices[k] = { level: str(v?.level), title: str(v?.title) };
  const termMin = terms ? num(terms.minDepositUsdt ?? terms.min_deposit_usdt) : null;
  return {
    exchanges,
    defaultExchange: str(r.default_exchange) || exchanges[0]?.id || 'lbank',
    minDeposit: num(g(entry, 'min_deposit')) ?? termMin,
    registrationMinDeposit: num(g(entry, 'registration_min_deposit')) ?? termMin,
    inviteTtlHours: num(g(entry, 'invite_ttl_hours')),
    warnBelow: num(g(stay, 'warn_below')),
    criticalBelow: num(g(stay, 'critical_below')),
    graceHours: num(g(stay, 'grace_hours')),
    rejoinAt: num(g(stay, 'rejoin_at')),
    warnRepeatHours: num(g(stay, 'warn_repeat_hours')),
    balanceEverySeconds: num(g(checks, 'balance_every_seconds')),
    pendingEverySeconds: num(g(checks, 'pending_every_seconds')),
    pendingWindowHours: num(g(checks, 'pending_window_hours')),
    pendingReminderHours: (Array.isArray(g(checks, 'pending_reminder_hours')) ? (g(checks, 'pending_reminder_hours') as unknown[]) : []).map(num).filter((x): x is number => x != null),
    uidRecheckEverySeconds: num(g(checks, 'uid_recheck_every_seconds')),
    uidRecheckHours: num(g(checks, 'uid_recheck_hours')),
    exchangeLagFa: str(g(checks, 'exchange_lag_fa')),
    supportUrl: r.support_url ? str(r.support_url) : null,
    texts, registrationStatusFa: statuses, notices,
    noticeFa: terms ? str(terms.noticeFa ?? terms.notice_fa) : '',
    complete: Boolean(entry && stay),
  };
}

export async function cryptoRules(): Promise<CryptoRules> {
  const [rules, terms] = await Promise.allSettled([request<Record<string, unknown>>(RULES), request<Record<string, unknown>>(TERMS)]);
  const t = terms.status === 'fulfilled' ? terms.value : null;
  if (rules.status === 'fulfilled') return readCryptoRules(rules.value, t);
  if (t) return readCryptoRules({}, t);
  throw rules.reason;
}

// ── TradeYar: the reader's registration and VIP state ───────────────────────────────────────────
export type RegStatus = 'awaiting_uid' | 'verifying' | 'approved' | 'pending_deposit' | 'rejected_referral' | 'error' | 'pending';
export interface Registration { status: RegStatus; messageFa: string; nextStep: string | null; canResubmit: boolean; uid: string | null; exchange: string | null; vipStatus: string | null; vipEntitled: boolean }

export function readRegistration(r: Record<string, unknown>): Registration {
  const vip = (r.vip ?? null) as Record<string, unknown> | null;
  return {
    status: (str(r.status) || 'awaiting_uid') as RegStatus,
    messageFa: str(r.message_fa),
    nextStep: r.next_step == null ? null : str(r.next_step),
    canResubmit: Boolean(r.can_resubmit),
    uid: r.uid ? str(r.uid) : null,
    exchange: r.exchange ? str(r.exchange) : null,
    vipStatus: vip ? str(vip.status) : null,
    vipEntitled: Boolean(vip?.entitled),
  };
}

export const registration = () => ty<Record<string, unknown>>('api/mobile/v1/membership/status').then(readRegistration);
export const submitUid = (exchange: string, uid: string) => ty<Record<string, unknown>>('api/mobile/v1/membership/uid', { body: { exchange, uid } }).then(readRegistration);

export type VipStatus = 'none' | 'awaiting_referral' | 'pending_deposit' | 'active' | 'low' | 'critical' | 'removed' | 'expired';
export interface VipEvent { id: string; event: string; level: string; title: string; body: string; balance: number | null; at: number | null }
export interface VipState {
  status: VipStatus;
  entitled: boolean;
  messageFa: string;
  ruleFa: string;
  exchange: string | null;
  uid: string | null;
  balance: number | null;
  checkedAt: number | null;
  joinedAt: number | null;
  removedAt: number | null;
  criticalSince: number | null;
  removalDueAt: number | null;
  pendingSince: number | null;
  pendingUntil: number | null;
  thresholds: { minDeposit: number | null; warn: number | null; critical: number | null; graceHours: number | null; pendingWindowHours: number | null };
  channel: { configured: boolean; telegramKnown: boolean; inviteUrl: string | null };
  supportUrl: string | null;
  events: VipEvent[];
  recheck?: { outcome: string; granted: boolean; messageFa: string };
}

const t = (v: unknown): number | null => { if (v == null || v === '') return null; const n = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v)); return Number.isFinite(n) ? n : null; };

export function readVip(r: Record<string, unknown>): VipState {
  const th = (r.thresholds ?? {}) as Record<string, unknown>;
  const ch = (r.channel ?? {}) as Record<string, unknown>;
  const rc = r.recheck as Record<string, unknown> | undefined;
  return {
    status: (str(r.status) || 'none') as VipStatus,
    entitled: Boolean(r.entitled),
    messageFa: str(r.message_fa), ruleFa: str(r.rule_fa),
    exchange: r.exchange ? str(r.exchange) : null, uid: r.uid ? str(r.uid) : null,
    balance: num(r.balance), checkedAt: t(r.checked_at), joinedAt: t(r.joined_at), removedAt: t(r.removed_at),
    criticalSince: t(r.critical_since), removalDueAt: t(r.removal_due_at), pendingSince: t(r.pending_since), pendingUntil: t(r.pending_until),
    thresholds: { minDeposit: num(th.min_deposit), warn: num(th.warn), critical: num(th.critical), graceHours: num(th.grace_hours), pendingWindowHours: num(th.pending_window_hours) },
    channel: { configured: Boolean(ch.configured), telegramKnown: Boolean(ch.telegram_known), inviteUrl: ch.invite_url ? str(ch.invite_url) : null },
    supportUrl: r.support_url ? str(r.support_url) : null,
    events: (Array.isArray(r.events) ? (r.events as Record<string, unknown>[]) : []).map((e) => ({
      id: str(e.id), event: str(e.event), level: str(e.level), title: str(e.title), body: str(e.body), balance: num(e.balance), at: t(e.at),
    })),
    recheck: rc ? { outcome: str(rc.outcome), granted: Boolean(rc.granted), messageFa: str(rc.message_fa) } : undefined,
  };
}

export const vipState = () => ty<Record<string, unknown>>('api/mobile/v1/membership/vip').then(readVip);
export const vipRecheck = () => ty<Record<string, unknown>>('api/mobile/v1/membership/vip/recheck', { body: {} }).then(readVip);
export const vipInvite = () => ty<{ invite_url?: string; expires_at?: string }>('api/mobile/v1/membership/vip/invite', { body: {} })
  .then((r) => ({ url: str(r.invite_url), expiresAt: t(r.expires_at) }));

// ── CoinePro-FX (forex, signals only) ───────────────────────────────────────────────────────────
export interface ForexRules {
  signalsOnly: boolean;
  brokerName: string;
  referralUrl: string;
  minDeposit: number | null;
  currency: string;
  verification: string;
  lifetime: boolean;
  trialHours: number | null;
  plans: { key: string; months: number; price: number }[];
  planCurrency: string;
  botUrl: string | null;
  supportUrl: string | null;
  steps: string[];
  texts: Record<string, string>;
}

export function readForexRules(r: Record<string, unknown>): ForexRules {
  const b = (r.broker ?? {}) as Record<string, unknown>;
  const e = (r.entry ?? {}) as Record<string, unknown>;
  const a = (r.alternatives ?? {}) as Record<string, unknown>;
  const texts: Record<string, string> = {};
  for (const [k, v] of Object.entries((r.texts_html ?? {}) as Record<string, unknown>)) if (typeof v === 'string') texts[k] = v;
  return {
    signalsOnly: r.signals_only !== false,
    brokerName: str(b.name) || 'OneRoyal',
    referralUrl: str(b.referral_url),
    minDeposit: num(e.min_deposit),
    currency: str(e.currency) || 'USD',
    verification: str(e.verification) || 'manual',
    lifetime: Boolean(e.lifetime),
    trialHours: num(a.trial_hours),
    plans: (Array.isArray(a.plans) ? (a.plans as Record<string, unknown>[]) : []).map((p) => ({ key: str(p.key), months: num(p.months) ?? 0, price: num(p.price) ?? 0 })),
    planCurrency: str(a.currency) || 'USDT',
    botUrl: r.bot_url ? str(r.bot_url) : null,
    supportUrl: r.support_url ? str(r.support_url) : null,
    steps: (Array.isArray(r.steps_fa) ? (r.steps_fa as unknown[]) : []).map(str).filter(Boolean),
    texts,
  };
}

export const forexRules = () => request<Record<string, unknown>>('/up/coineprofx/api/public/forex-vip/rules').then(readForexRules);

export type ForexStatus = 'none' | 'submitted' | 'approved' | 'rejected';
export interface ForexState {
  status: ForexStatus;
  entitled: boolean;
  messageFa: string;
  vip: { plan: string; expiresAt: number | null; lifetime: boolean } | null;
  claim: { id: string; accountLogin: string; email: string; status: string; reason: string | null; submittedAt: number | null; reviewedAt: number | null } | null;
  telegramLinked: boolean;
  channelInvite: string | null;
  canSubmit: boolean;
}

export function readForexState(r: Record<string, unknown>): ForexState {
  const v = r.vip as Record<string, unknown> | null;
  const c = r.claim as Record<string, unknown> | null;
  return {
    status: (str(r.status) || 'none') as ForexStatus,
    entitled: Boolean(r.entitled),
    messageFa: str(r.message_fa),
    vip: v ? { plan: str(v.plan), expiresAt: t(v.expires_at), lifetime: Boolean(v.lifetime) } : null,
    claim: c ? { id: str(c.id), accountLogin: str(c.account_login), email: str(c.email), status: str(c.status), reason: c.reason ? str(c.reason) : null, submittedAt: t(c.submitted_at), reviewedAt: t(c.reviewed_at) } : null,
    telegramLinked: Boolean(r.telegram_linked),
    channelInvite: r.channel_invite ? str(r.channel_invite) : null,
    canSubmit: r.can_submit !== false,
  };
}

export const forexState = () => fx<Record<string, unknown>>('user/mobile/forex-vip').then(readForexState);
export const forexClaim = (accountLogin: string, email: string) => fx<Record<string, unknown>>('user/mobile/forex-vip/claim', { body: { account_login: accountLogin, email } }).then(readForexState);
