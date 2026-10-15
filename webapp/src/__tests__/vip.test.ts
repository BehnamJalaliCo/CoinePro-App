import { beforeEach, describe, expect, it } from 'vitest';
import { readCryptoRules, readForexRules, readForexState, readRegistration, readVip } from '../vip/api';
import { cleanUid, cryptoStep, gaugeAt, outcomeOf, readLocal, segments, severityOf, timeLeft, usdt, writeLocal, zoneOf } from '../vip/logic';
import { monthLabel, monthRows, outcomeWalk, signalOutcome, winRate } from '../vip/perf';
import { PAGES, pageFromPath } from '../pages/registry';

const RULES = {
  default_exchange: 'lbank',
  exchanges: [
    { id: 'lbank', label: 'LBank', referral_url: 'https://lbank.example/ref', uid_pattern: '^LBA[0-9A-Z]{5,10}$', uid_example: 'LBA1A23456', copy_trade: true },
    { id: 'ourbit', label: 'Ourbit', referral_url: 'https://ourbit.example/ref', uid_pattern: '^[0-9]{6,10}$', uid_example: '40429440', copy_trade: false },
  ],
  entry: { min_deposit: 50, registration_min_deposit: 50, invite_ttl_hours: 24 },
  stay: { warn_below: 30, critical_below: 10, grace_hours: 48, rejoin_at: 50, warn_repeat_hours: 24 },
  checks: { balance_every_seconds: 1800, pending_every_seconds: 60, pending_window_hours: 168, pending_reminder_hours: [24, 72], uid_recheck_every_seconds: 60, uid_recheck_hours: 24, exchange_lag_fa: '۳۰ دقیقه تا ۱ ساعت' },
  support_url: 'https://t.me/support',
  texts_html: { deal: 'کانال <b>رایگان</b> است', stay_rule: 'شرط ماندن' },
  registration_status_fa: { pending_deposit: 'موجودی کم است' },
  notices: { removed: { level: 'error', title: 'دسترسی VIP لغو شد' } },
};

describe('vip api readers', () => {
  it('reads the public rules with the gate’s numbers', () => {
    const r = readCryptoRules(RULES);
    expect(r.complete).toBe(true);
    expect(r.defaultExchange).toBe('lbank');
    expect(r.exchanges.map((x) => x.id)).toEqual(['lbank', 'ourbit']);
    expect([r.minDeposit, r.warnBelow, r.criticalBelow, r.graceHours, r.rejoinAt]).toEqual([50, 30, 10, 48, 50]);
    expect(r.pendingReminderHours).toEqual([24, 72]);
    expect(r.texts.deal).toContain('<b>');
    expect(r.notices.removed!.title).toBe('دسترسی VIP لغو شد');
  });
  it('falls back to the older camelCase public answer for the links and the minimum', () => {
    const r = readCryptoRules({}, { lbankReferralUrl: 'https://l/ref', ourbitReferralUrl: 'https://o/ref', minDepositUsdt: 50, noticeFa: 'با لینک ما' });
    expect(r.complete).toBe(false);
    expect(r.exchanges.map((x) => x.referralUrl)).toEqual(['https://l/ref', 'https://o/ref']);
    expect(r.minDeposit).toBe(50);
    expect(r.warnBelow).toBeNull();
    expect(r.noticeFa).toBe('با لینک ما');
  });
  it('reads the registration with its VIP side', () => {
    const r = readRegistration({ status: 'pending_deposit', message_fa: 'x', next_step: 'uid', can_resubmit: true, uid: 'LBA1', vip: { status: 'pending_deposit', entitled: false } });
    expect(r).toMatchObject({ status: 'pending_deposit', canResubmit: true, vipStatus: 'pending_deposit', vipEntitled: false });
  });
  it('reads the VIP state, its deadline and notices', () => {
    const v = readVip({ status: 'critical', entitled: true, balance: 6.2, removal_due_at: '2026-10-16T08:00:00+00:00', thresholds: { min_deposit: 50, warn: 30, critical: 10, grace_hours: 48 }, channel: { configured: true, telegram_known: false, invite_url: 'https://t.me/+x' }, events: [{ id: 9, event: 'critical', level: 'error', title: 't', body: 'b', at: '2026-10-14T08:00:00Z' }] });
    expect(v.removalDueAt).toBe(Date.parse('2026-10-16T08:00:00Z'));
    expect(v.events[0]).toMatchObject({ id: '9', event: 'critical', level: 'error' });
    expect(v.channel.inviteUrl).toBe('https://t.me/+x');
  });
  it('reads the forex rules and state (signals only)', () => {
    const r = readForexRules({ signals_only: true, broker: { name: 'OneRoyal', referral_url: 'https://or/ref' }, entry: { min_deposit: 500, currency: 'USD', verification: 'manual', lifetime: true }, alternatives: { trial_hours: 48, plans: [{ key: 'monthly', months: 1, price: 30 }] }, steps_fa: ['a', 'b', 'c'] });
    expect(r).toMatchObject({ signalsOnly: true, brokerName: 'OneRoyal', minDeposit: 500, lifetime: true, trialHours: 48 });
    expect(r.steps).toHaveLength(3);
    const s = readForexState({ status: 'rejected', entitled: false, claim: { id: 5, account_login: '•••567', email: 'e', status: 'rejected', reason: 'no deposit' }, can_submit: true });
    expect(s.claim!.reason).toBe('no deposit');
    expect(s.canSubmit).toBe(true);
  });
});

describe('vip funnel logic', () => {
  const reg = (status: string) => readRegistration({ status });
  it('walks the steps', () => {
    expect(cryptoStep(false, null, { registered: true, deposited: true })).toBe('account');
    expect(cryptoStep(true, reg('awaiting_uid'), { registered: false, deposited: false })).toBe('exchange');
    expect(cryptoStep(true, reg('awaiting_uid'), { registered: true, deposited: false })).toBe('deposit');
    expect(cryptoStep(true, reg('awaiting_uid'), { registered: true, deposited: true })).toBe('uid');
    expect(cryptoStep(true, reg('pending_deposit'), { registered: false, deposited: false })).toBe('result');
  });
  it('lets the VIP gate speak ahead of the registration', () => {
    const vip = (status: string, entitled = false) => readVip({ status, entitled });
    expect(outcomeOf(reg('pending_deposit'), vip('active', true))).toBe('vip');
    expect(outcomeOf(reg('rejected_referral'), vip('pending_deposit'))).toBe('deposit');
    expect(outcomeOf(reg('rejected_referral'), null)).toBe('not_indexed');
    expect(outcomeOf(reg('approved'), vip('none'))).toBe('approved');
    expect(outcomeOf(reg('error'), null)).toBe('error');
  });
  it('rates the severity and the balance zone', () => {
    expect(severityOf(readVip({ status: 'low' }))).toBe('low');
    expect(severityOf(readVip({ status: 'removed' }))).toBe('removed');
    expect(severityOf(null)).toBe('none');
    expect(zoneOf(45, 30, 10)).toBe('healthy');
    expect(zoneOf(20, 30, 10)).toBe('warn');
    expect(zoneOf(9.99, 30, 10)).toBe('critical');
    expect(zoneOf(null, 30, 10)).toBe('unknown');
    expect(gaugeAt(50, 50)).toBeCloseTo(0.8);
    expect(gaugeAt(999, 50)).toBe(1);
  });
  it('reads a UID the way the bot does', () => {
    const r = readCryptoRules(RULES);
    expect(cleanUid(' lba1a23456 ', 'lbank', r)).toEqual({ uid: 'LBA1A23456', error: null });
    expect(cleanUid('UID: ۴۰۴۲۹۴۴۰', 'ourbit', r)).toEqual({ uid: '40429440', error: null });
    expect(cleanUid('09121234567', 'ourbit', r).error).toBe('phone');
    expect(cleanUid('me@example.com', 'lbank', r).error).toBe('email');
    expect(cleanUid('12', 'ourbit', r).error).toBe('shape');
    expect(cleanUid('', 'lbank', r).error).toBe('empty');
    expect(cleanUid('LBA1A23456', 'lbank', null).error).toBeNull();
  });
  it('counts down and formats', () => {
    expect(timeLeft(10 * 3_600_000 + 5 * 60_000, 0)).toEqual({ h: 10, m: 5, over: false });
    expect(timeLeft(0, 5)).toEqual({ h: 0, m: 0, over: true });
    expect(usdt(12.5)).toBe('12.5');
    expect(usdt(null)).toBe('—');
  });
  it('renders the bot’s HTML as text runs, never markup', () => {
    expect(segments('کانال <b>رایگان</b> است <i>x</i> &lt;script&gt;')).toEqual([
      { text: 'کانال ', bold: false }, { text: 'رایگان', bold: true }, { text: ' است ', bold: false }, { text: 'x', bold: false }, { text: ' <script>', bold: false },
    ]);
    expect(segments('<a href="x">link</a>')).toEqual([{ text: 'link', bold: false }]);
  });
  beforeEach(() => { try { localStorage.clear(); } catch { /* none */ } });
  it('keeps the reader’s ticks per browser', () => {
    expect(readLocal().market).toBe('crypto');
    writeLocal({ ...readLocal(), market: 'forex', registered: true });
    expect(readLocal()).toMatchObject({ market: 'forex', registered: true, deposited: false });
  });
});

describe('vip performance arithmetic', () => {
  const T = (o: 'WIN' | 'LOSS' | 'BREAKEVEN' | 'UNKNOWN', d: string) => ({ outcome: o, at: Date.parse(d) });
  const trades = [T('WIN', '2026-08-02'), T('LOSS', '2026-08-05'), T('WIN', '2026-09-01'), T('BREAKEVEN', '2026-09-03'), T('UNKNOWN', '2026-09-04'), T('WIN', '2026-07-30')];
  it('walks wins up and losses down in time order', () => {
    expect(outcomeWalk(trades)).toEqual([0, 1, 2, 1, 2]);
    expect(outcomeWalk([])).toEqual([]);
  });
  it('rates over graded trades only, never 0% for nothing', () => {
    expect(winRate(trades)).toEqual({ rate: 75, wins: 3, losses: 1, graded: 4 });
    expect(winRate([T('UNKNOWN', '2026-09-01')]).rate).toBeNull();
  });
  it('buckets months oldest first', () => {
    const m = monthRows(trades, 'utc');
    expect(m.map((x) => x.month)).toEqual(['2026-07', '2026-08', '2026-09']);
    expect(m[1]).toMatchObject({ wins: 1, losses: 1, graded: 2, winRate: 50 });
    expect(m[2]).toMatchObject({ wins: 1, losses: 0, total: 3, winRate: 100 });
    expect(monthLabel('2026-09', 'fa')).toBe('سپتامبر 2026');
    expect(monthLabel('2026-09', 'en')).toBe('Sep 2026');
  });
  it('grades a published signal', () => {
    expect(signalOutcome({ isWin: true, pctGain: 3, closedAt: 1 })).toBe('WIN');
    expect(signalOutcome({ isWin: null, pctGain: -1, closedAt: 1 })).toBe('LOSS');
    expect(signalOutcome({ isWin: null, pctGain: null, closedAt: null })).toBe('OPEN');
  });
});

describe('vip pages are registered', () => {
  it('has the ids other areas link to', () => {
    for (const id of ['vip-join', 'vip-status', 'affiliate', 'performance', 'copytrade']) expect(PAGES.some((p) => p.id === id)).toBe(true);
    expect(pageFromPath('vip')).toEqual({ id: 'vip-join', param: null });
    expect(pageFromPath('vip/status')).toEqual({ id: 'vip-status', param: null });
    expect(PAGES.find((p) => p.id === 'vip-join')!.member).toBeFalsy();
    expect(PAGES.find((p) => p.id === 'vip-status')!.member).toBe(true);
  });
});
