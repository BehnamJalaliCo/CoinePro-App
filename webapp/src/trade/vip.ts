// The TradeYar VIP channel on the web: the channel's signals (member-only: the list answers
// `membership_required` for everyone else), the membership state and the public join terms, the
// reader's copy trades on LBank (executions), and a watcher that announces a new signal while the
// app is open — a toast, and a browser notification outside the quiet hours.
import { create } from 'zustand';
import { authed } from '../data/auth';
import { request, uuid } from '../data/http';
import { useSession } from '../data/session';
import { readSignal, type Signal } from '../data/signals';
import { chime, inQuietHours, readNotify } from '../state/notify';
import { MEMBER_ROOT } from './venue';

const TY = MEMBER_ROOT.tradeyar;
type Row = Record<string, unknown>;
export type VipList = 'active' | 'recent' | 'closed';

export interface MembershipStatus { status: string; messageFa: string | null; nextStep: string | null; canResubmit: boolean; uid: string | null }
export interface JoinTerms { lbankUrl: string | null; ourbitUrl: string | null; minDeposit: number | null; noticeFa: string | null; copyTradeExchanges: string[] }
/** The channel's balance rules as TradeYar publishes them (never constants here). */
export interface VipRules { minDeposit: number | null; warnBelow: number | null; removeAt: number | null; linesFa: string[] }

const firstNum = (x: Row, ...keys: string[]): number | null => { for (const k of keys) { const n = Number(x[k]); if (x[k] != null && x[k] !== '' && Number.isFinite(n)) return n; } return null; };

/** Reads the public VIP rules route; any field it does not send stays null. */
export function readRules(x: Row): VipRules {
  const lines = Array.isArray(x.rules_fa) ? (x.rules_fa as unknown[]).map(String) : typeof x.rules_fa === 'string' ? [x.rules_fa] : typeof x.notice_fa === 'string' ? [x.notice_fa] : [];
  return {
    minDeposit: firstNum(x, 'min_deposit_usdt', 'min_deposit'),
    warnBelow: firstNum(x, 'warn_balance_usdt', 'warning_below_usdt', 'warn_below_usdt', 'warn_balance'),
    removeAt: firstNum(x, 'removal_balance_usdt', 'remove_below_usdt', 'critical_balance_usdt', 'remove_at_usdt'),
    linesFa: lines.filter(Boolean),
  };
}
export interface Execution {
  id: string; signalId: string; venue: string; side: string; product: string; quantity: string | null; status: string;
  error: string | null; canClose: boolean; createdAt: number | null; closedAt: number | null; symbol: string | null;
}

const ms = (v: unknown): number | null => { if (v == null || v === '') return null; const t = typeof v === 'number' ? (v < 1e12 ? v * 1000 : v) : Date.parse(String(v)); return Number.isFinite(t) ? t : null; };

export function readExecution(x: Row): Execution {
  const sig = (x.signal ?? null) as Row | null;
  return {
    id: String(x.id ?? ''), signalId: String(x.signal_id ?? ''), venue: String(x.venue ?? 'lbank'), side: String(x.side ?? ''), product: String(x.product ?? ''),
    quantity: x.quantity == null ? null : String(x.quantity), status: String(x.status ?? 'unknown').toLowerCase(),
    error: x.error_message ? String(x.error_message) : null, canClose: Boolean(x.can_request_close ?? x.status === 'open'),
    createdAt: ms(x.created_at), closedAt: ms(x.closed_at), symbol: sig?.symbol ? String(sig.symbol).toUpperCase() : x.symbol ? String(x.symbol).toUpperCase() : null,
  };
}

// ── Outcomes ────────────────────────────────────────────────────────────────────────────────────
export const isOpen = (s: Signal) => s.status.toUpperCase() === 'ACTIVE' || (!s.closedAt && !s.status);

export type Outcome = 'active' | 'win' | 'loss' | 'flat' | null;

/** How a signal ended, from what the platform recorded: the result first, then the status. */
export function outcomeOf(s: Signal): Outcome {
  if (isOpen(s)) return 'active';
  if (s.pnlUsd != null) return s.pnlUsd > 0 ? 'win' : s.pnlUsd < 0 ? 'loss' : 'flat';
  if (s.isWin != null) return s.isWin ? 'win' : 'loss';
  const st = s.status.toUpperCase();
  if (/TP\d?/.test(st) || s.hits?.some(Boolean)) return 'win';
  if (/STOP|SL/.test(st)) return 'loss';
  if (s.pctGain != null) return s.pctGain > 0 ? 'win' : s.pctGain < 0 ? 'loss' : 'flat';
  return null;
}

export function winRate(list: Signal[]): { wins: number; losses: number; rate: number | null } {
  let wins = 0; let losses = 0;
  for (const s of list) { const o = outcomeOf(s); if (o === 'win') wins++; else if (o === 'loss') losses++; }
  return { wins, losses, rate: wins + losses ? (wins / (wins + losses)) * 100 : null };
}

/** Live profit of an open signal, in percent, at `price`. */
export function livePct(s: Signal, price: number | null | undefined): number | null {
  if (s.entry == null || !price) return s.pctGain ?? null;
  return ((price - s.entry) / s.entry) * 100 * (s.direction === 'long' ? 1 : -1);
}

/** The status in the reader's words: active, a target reached, stopped, closed by hand. */
export function statusLabel(s: Signal): [string, string] {
  const st = s.status.toUpperCase();
  if (isOpen(s)) {
    const hit = (s.hits ?? []).lastIndexOf(true);
    return hit >= 0 ? [`فعال · TP${hit + 1} لمس شد`, `Active · TP${hit + 1} hit`] : ['فعال', 'Active'];
  }
  const tp = /TP(\d)/.exec(st);
  if (tp) return [`بسته با TP${tp[1]}`, `Closed at TP${tp[1]}`];
  if (/STOP|SL/.test(st)) return ['حد ضرر خورد', 'Stopped out'];
  if (/MANUAL/.test(st)) return ['بسته‌شده‌ی دستی', 'Closed by hand'];
  if (/EXPIRE/.test(st)) return ['منقضی', 'Expired'];
  return ['بسته‌شده', 'Closed'];
}

// ── Store ───────────────────────────────────────────────────────────────────────────────────────
const SEEN_KEY = 'pc.vip.seen.v1';
const PREFS_KEY = 'pc.vip.v1';
interface VipPrefs { onChart: boolean }
const readPrefs = (): VipPrefs => { try { return { onChart: true, ...(JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') as Partial<VipPrefs>) }; } catch { return { onChart: true }; } };
const readSeen = (): string[] => { try { const v = JSON.parse(localStorage.getItem(SEEN_KEY) ?? '[]'); return Array.isArray(v) ? v.map(String) : []; } catch { return []; } };

interface VipState {
  /** null until the platform has answered (or while signed out of TradeYar). */
  member: boolean | null;
  message: string | null;
  lists: Record<VipList, Signal[] | null>;
  errors: Partial<Record<VipList, string>>;
  status: MembershipStatus | null;
  terms: JoinTerms | null;
  rules: VipRules | null;
  executions: Execution[] | null;
  /** The signal the reader picked in the panel: drawn on the chart even once closed. */
  focus: Signal | null;
  onChart: boolean;
  /** New signals the reader has not looked at (the widget bar's badge). */
  fresh: string[];
  toast: Signal | null;
  load(list: VipList): Promise<void>;
  loadMembership(): Promise<void>;
  loadExecutions(): Promise<void>;
  setFocus(s: Signal | null): void;
  setOnChart(on: boolean): void;
  markSeen(): void;
  dismissToast(): void;
}

export const useVip = create<VipState>((set, get) => ({
  member: null,
  message: null,
  lists: { active: null, recent: null, closed: null },
  errors: {},
  status: null,
  terms: null,
  rules: null,
  executions: null,
  focus: null,
  onChart: readPrefs().onChart,
  fresh: [],
  toast: null,
  load: async (list) => {
    if (!useSession.getState().sessions.tradeyar) { set({ member: null, lists: { active: null, recent: null, closed: null } }); return; }
    try {
      const r = await authed<{ items?: Row[]; membership_required?: boolean; membership_message?: string | null }>('tradeyar', `${TY}/signals?status=${list}&limit=100`);
      const items = (r.items ?? []).map((x) => readSignal(x, 'tradeyar')).filter((s) => s.symbol);
      set((s) => ({ member: !r.membership_required, message: r.membership_message ?? null, lists: { ...s.lists, [list]: items }, errors: { ...s.errors, [list]: undefined } }));
    } catch (e) {
      set((s) => ({ errors: { ...s.errors, [list]: String((e as Error)?.message ?? e) } }));
      throw e;
    }
  },
  loadMembership: async () => {
    const [st, terms, rules] = await Promise.allSettled([
      useSession.getState().sessions.tradeyar ? authed<Row>('tradeyar', `${TY}/membership/status`) : Promise.reject(new Error('signed-out')),
      request<Row>('/up/tradeyar/api/v1/public/membership'),
      // The channel's balance rules, when TradeYar publishes them (area H's public route).
      request<Row>('/up/tradeyar/api/v1/public/vip/rules'),
    ]);
    set({
      status: st.status === 'fulfilled' ? {
        status: String(st.value.status ?? ''), messageFa: st.value.message_fa ? String(st.value.message_fa) : null,
        nextStep: st.value.next_step ? String(st.value.next_step) : null, canResubmit: Boolean(st.value.can_resubmit), uid: st.value.uid ? String(st.value.uid) : null,
      } : null,
      terms: terms.status === 'fulfilled' ? {
        lbankUrl: terms.value.lbank_referral_url ? String(terms.value.lbank_referral_url) : null,
        ourbitUrl: terms.value.ourbit_referral_url ? String(terms.value.ourbit_referral_url) : null,
        minDeposit: Number(terms.value.min_deposit_usdt) || null,
        noticeFa: terms.value.notice_fa ? String(terms.value.notice_fa) : null,
        copyTradeExchanges: Array.isArray(terms.value.copy_trade_exchanges) ? (terms.value.copy_trade_exchanges as unknown[]).map(String) : [],
      } : get().terms,
      rules: rules.status === 'fulfilled' && rules.value && typeof rules.value === 'object' ? readRules(rules.value) : get().rules,
    });
  },
  loadExecutions: async () => {
    const r = await authed<{ items?: Row[] }>('tradeyar', `${TY}/executions?limit=50`);
    set({ executions: (r.items ?? []).map(readExecution) });
  },
  setFocus: (focus) => set({ focus }),
  setOnChart: (onChart) => { set({ onChart }); try { localStorage.setItem(PREFS_KEY, JSON.stringify({ onChart })); } catch { /* storage off */ } },
  markSeen: () => set({ fresh: [] }),
  dismissToast: () => set({ toast: null }),
}));

/** Copies a signal to LBank: the server takes symbol, side and levels from the signal itself. */
export async function copySignal(signalId: string, quantity: number, clientRequestId = uuid()): Promise<Execution | null> {
  const r = await authed<{ execution?: Row }>('tradeyar', `${TY}/executions`, { body: { signal_id: Number(signalId), venue: 'lbank', quantity, client_request_id: clientRequestId, confirmed: true } });
  return r.execution ? readExecution(r.execution) : null;
}

export async function closeExecution(id: string): Promise<Execution | null> {
  const r = await authed<{ execution?: Row }>('tradeyar', `${TY}/executions/${encodeURIComponent(id)}/close`, { body: {} });
  return r.execution ? readExecution(r.execution) : null;
}

// ── New-signal watcher ──────────────────────────────────────────────────────────────────────────
/** Ids not seen before, given what was seen; the first look only records (no alarm for history). */
export function newIds(ids: string[], seen: string[], firstLook: boolean): string[] {
  if (firstLook) return [];
  const known = new Set(seen);
  return ids.filter((id) => !known.has(id));
}

function announce(s: Signal, fa: boolean): void {
  useVip.setState((x) => ({ toast: s, fresh: [...new Set([...x.fresh, s.id])] }));
  const local = readNotify();
  if (inQuietHours(new Date(), local)) return;
  if (local.sound) chime();
  try {
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      const side = s.direction === 'long' ? (fa ? 'خرید' : 'Long') : (fa ? 'فروش' : 'Short');
      const n = new Notification(fa ? `سیگنال تازه‌ی VIP · ${s.symbol}` : `New VIP signal · ${s.symbol}`, {
        body: `${side} ${s.timeframe}${s.entry != null ? ` @ ${s.entry}` : ''}`, tag: `vip-${s.id}`, lang: fa ? 'fa' : 'en', dir: fa ? 'rtl' : 'ltr',
      });
      n.onclick = () => { window.focus(); useVip.getState().setFocus(s); n.close(); };
    }
  } catch { /* notifications unavailable */ }
}

let watching = false;
let pollNow: () => void = () => undefined;
/** Asks the channel at once (a sign-in, the panel's refresh, the end-to-end suite). */
export const pollVip = () => pollNow();

/** Polls the channel's active signals every minute (two when the tab is hidden). */
export function startVipWatch(lang: () => 'fa' | 'en'): () => void {
  if (watching) return () => undefined;
  watching = true;
  let first = true;
  let last = 0;
  const tick = async () => {
    if (!useSession.getState().sessions.tradeyar) { first = true; return; }
    const every = document.visibilityState === 'hidden' ? 120_000 : 60_000;
    if (Date.now() - last < every) return;
    last = Date.now();
    try { await useVip.getState().load('active'); } catch { return; }
    const st = useVip.getState();
    if (!st.member) return;
    const list = st.lists.active ?? [];
    const seen = readSeen();
    const fresh = newIds(list.map((s) => s.id), seen, first);
    first = false;
    try { localStorage.setItem(SEEN_KEY, JSON.stringify([...new Set([...list.map((s) => s.id), ...seen])].slice(0, 400))); } catch { /* storage off */ }
    for (const id of fresh) { const s = list.find((x) => x.id === id); if (s) announce(s, lang() === 'fa'); }
  };
  const timer = setInterval(() => void tick(), 5_000);
  pollNow = () => { last = 0; void tick(); };
  void tick();
  let prev = useSession.getState().sessions.tradeyar?.access;
  const off = useSession.subscribe((s) => {
    if (s.sessions.tradeyar?.access !== prev) { prev = s.sessions.tradeyar?.access; last = 0; if (!s.sessions.tradeyar) useVip.setState({ member: null, lists: { active: null, recent: null, closed: null }, executions: null }); void tick(); }
  });
  return () => { clearInterval(timer); off(); watching = false; };
}
