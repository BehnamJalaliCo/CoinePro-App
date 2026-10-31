// «جایگاه شما» and «کارنامه‌ی شما» — what the server says about the account, and what the reader has
// done (OLD feature/profile ProfileStanding.kt, ProfileFacts.kt). Rule one: nothing the server did
// not say is claimed. A fact whose answer is missing is drawn as «نامشخص» in the muted ink, never
// as «تأیید نشده»; its tone is dropped with it. A row with neither a value nor a destination is
// not drawn, and a card of such rows is not drawn at all.
export type FactTone = 'neutral' | 'settled' | 'waiting' | 'refused';
export interface Fact { id: string; label: string; value: string | null; tone: FactTone; detail: string | null; open?: string }

type Pick = (fa: string, en: string) => string;

export const drawable = (facts: Fact[]) => facts.filter((f) => f.value != null || f.open != null);

/** The membership words feature:membership prints; an unknown status has none. */
const MEMBERSHIP: Record<string, [string, string, FactTone]> = {
  awaiting_uid: ['در انتظار شناسه‌ی کاربری', 'Waiting for your UID', 'waiting'],
  verifying: ['در حال بررسی', 'Being checked', 'waiting'],
  approved: ['تأیید شده', 'Approved', 'settled'],
  pending_deposit: ['در انتظار واریز', 'Waiting for funding', 'waiting'],
  rejected_referral: ['حساب متصل نیست', 'Account not linked', 'refused'],
  error: ['بررسی ناتمام ماند', 'The check did not finish', 'waiting'],
  pending: ['در انتظار', 'Pending', 'waiting'],
};

/** `status` is null when nothing was asked, the read failed, or it is still loading. */
export function membershipFact(status: string | null, serverMessage: string | null, L: Pick): Fact {
  const known = status ? MEMBERSHIP[status] : undefined;
  return { id: 'membership', label: L('عضویت', 'Membership'), value: known ? L(known[0], known[1]) : null, tone: known ? known[2] : 'neutral', detail: serverMessage || null, open: 'vip-join' };
}

const KYC: Record<string, [string, string, FactTone]> = {
  not_started: ['انجام نشده', 'Not started', 'neutral'],
  pending: ['در انتظار بررسی', 'Under review', 'waiting'],
  approved: ['تأیید شد', 'Approved', 'settled'],
  rejected: ['رد شد', 'Rejected', 'refused'],
};
/** The KYC states the server sends, folded onto four; anything else is unknown, not «not started». */
export function kycState(raw: unknown): string | null {
  const s = String(raw ?? '').toLowerCase();
  if (s === 'approved' || s === 'verified') return 'approved';
  if (s === 'rejected' || s === 'declined') return 'rejected';
  if (s === 'pending' || s === 'submitted' || s === 'in_review' || s === 'under_review' || s === 'reviewing') return 'pending';
  if (s === 'not_started' || s === 'none' || s === 'unverified') return 'not_started';
  return null;
}

/**
 * Verification: the server's own detail only — the reviewer's reason on a rejection, the day a
 * pending submission arrived, the level granted.
 */
export function verificationFact(k: { state: string | null; reason?: string | null; submittedDay?: string | null; level?: number | null } | null, L: Pick, digits: (n: number) => string): Fact {
  const known = k?.state ? KYC[k.state] : undefined;
  let detail: string | null = null;
  if (k?.state === 'rejected') detail = k.reason || null;
  else if (k?.state === 'pending' && k.submittedDay) detail = L(`ارسال‌شده در ${k.submittedDay}`, `Submitted ${k.submittedDay}`);
  else if (k?.state === 'approved' && k.level && k.level > 0) detail = L(`سطح ${digits(k.level)}`, `Level ${k.level}`);
  return { id: 'verification', label: L('احراز هویت', 'Verification'), value: known ? L(known[0], known[1]) : null, tone: known ? known[2] : 'neutral', detail, open: 'verify' };
}

/** The plan as the server named it; null is unknown, not «free». */
export function planFact(p: { label: string | null; expires: string | null; daysLeft: number | null; endingSoon: boolean }, L: Pick, digits: (n: number) => string): Fact {
  let detail: string | null = null;
  if (p.expires && p.daysLeft != null) detail = L(`تا ${p.expires}، ${digits(p.daysLeft)} روز مانده`, `Until ${p.expires}, ${p.daysLeft} days left`);
  else if (p.expires) detail = L(`تا ${p.expires}`, `Until ${p.expires}`);
  return { id: 'plan', label: L('اشتراک', 'Plan'), value: p.label, tone: p.label == null ? 'neutral' : p.endingSoon ? 'waiting' : 'settled', detail, open: 'pro' };
}

/** «عضو بنیان‌گذار — از روز اول با ما», only for a reader who has it; never a row saying they do not. */
export function foundingFact(founding: boolean, L: Pick): Fact | null {
  return founding ? { id: 'founding', label: L('نشان عضویت', 'Member badge'), value: L('عضو بنیان‌گذار', 'Founding member'), tone: 'settled', detail: L('از روز اول با ما', 'Here from day one') } : null;
}

/** A win rate for a detail line: a market figure, Latin, one decimal; none over no decided trades. */
export function winRateDetail(percent: number | null, L: Pick): string | null {
  if (percent == null || !Number.isFinite(percent)) return null;
  const f = `⁦${percent.toFixed(1)}%⁩`;
  return L(`نرخ برد ${f}`, `Win rate ${f}`);
}

export interface Record_ { journal: { count: number; win: number | null }; paper: { count: number; win: number | null }; academy: { done: number | null; total: number | null; streak: number | null } | null }

/** What the reader has done: the journal, the practice account, the academy; each goes to its screen. */
export function recordFacts(r: Record_, L: Pick, digits: (n: number) => string): Fact[] {
  const out: Fact[] = [
    { id: 'journal', label: L('دفترچه‌ی معاملات', 'Trading journal'), value: L(`${digits(r.journal.count)} معامله`, `${r.journal.count} trades`), tone: 'neutral', detail: winRateDetail(r.journal.win, L), open: 'journal' },
    { id: 'paper', label: L('معامله‌ی آزمایشی', 'Paper trading'), value: L(`${digits(r.paper.count)} معامله‌ی بسته`, `${r.paper.count} closed`), tone: 'neutral', detail: winRateDetail(r.paper.win, L), open: 'paper' },
  ];
  if (r.academy) {
    const a = r.academy;
    out.push({
      id: 'academy', label: L('آکادمی', 'Academy'),
      value: a.done != null && a.total != null ? L(`${digits(a.done)} از ${digits(a.total)} درس`, `${a.done} of ${a.total} lessons`) : null,
      tone: 'neutral', detail: a.streak && a.streak > 0 ? L(`${digits(a.streak)} روز پیاپی یادگیری`, `${a.streak}-day learning streak`) : null, open: 'academy',
    });
  }
  return out;
}

/** Win rate over the decided trades (a trade with no result is not a loss); null under one. */
export function winRate(pnls: (number | null)[]): number | null {
  const decided = pnls.filter((x): x is number => x != null && x !== 0);
  return decided.length ? (decided.filter((x) => x > 0).length / decided.length) * 100 : null;
}
