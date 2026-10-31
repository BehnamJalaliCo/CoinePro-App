import { describe, expect, it } from 'vitest';
import { PAGE_TEACHING, TEACHING, teachingFor, usableKey, useTeaching } from '../pages/teaching';
import {
  arenaDays, arenaStreak, challengeFor, feedState, homeMarkets, leanOf, marketMood, moodIsEmpty, sinceLastVisit, AWAY_HOURS, HOME_MARKET_ROWS,
} from '../pages/homeLoop';
import { contextSentence, parseRelevance, relatedTo, relevanceOf, symbolFor, type Story } from '../pages/newsContext';
import { drawable, foundingFact, kycState, membershipFact, planFact, recordFacts, verificationFact, winRate, winRateDetail } from '../pages/profileFacts';
import { decodeAvatar, encodeAvatar, readRing } from '../pages/Avatar';
import { authFailure, retryIn, UnreadableAnswer, AUTH_FAILURE_TEXT } from '../data/authFailure';
import { HttpError } from '../data/http';
import { boardFrom, isSafeLink, messagesFrom, nudge, versionCodeOf } from '../data/owner';
import { describe as describeError } from '../state/lastError';
import { PAGES } from '../pages/registry';
import type { Ticker } from '../data/types';

const L = (fa: string) => fa;
const E = (_fa: string, en: string) => en;
const d = (n: number) => String(n);

describe('home2: teaching cards', () => {
  it('home2: covers 22 pages, every key usable, every surface has a lead and a pitfall', () => {
    expect(Object.keys(PAGE_TEACHING)).toHaveLength(22);
    for (const id of Object.keys(PAGE_TEACHING)) expect(PAGES.some((p) => p.id === id)).toBe(true);
    for (const s of Object.values(TEACHING)) { expect(usableKey(s.key)).toBe(true); expect(s.lead[0]).toMatch(/[؀-ۿ]/); expect(s.lead[1]).not.toMatch(/[؀-ۿ]/); expect(s.pitfall).not.toBeNull(); }
    expect(Object.keys(TEACHING)).toHaveLength(23);
    expect(teachingFor('vision')?.key).toBe('ai');
    expect(teachingFor('profile')).toBeNull();
    expect(JSON.stringify(TEACHING)).not.toMatch(/نمودار|هٔ/);
  });
  it('home2: dismiss puts a card away and restore brings it back, persisted', () => {
    useTeaching.getState().dismiss('home');
    expect(useTeaching.getState().dismissed).toContain('home');
    expect(JSON.parse(localStorage.getItem('pc.teaching.v1') ?? '[]')).toContain('home');
    useTeaching.getState().dismiss('BAD KEY');
    expect(useTeaching.getState().dismissed).not.toContain('BAD KEY');
    useTeaching.getState().restore('home');
    expect(useTeaching.getState().dismissed).not.toContain('home');
  });
});

const tk = (symbol: string, changePct: number, volume?: number, last = 1): Ticker => ({ symbol, last, change: 0, changePct, volume, ts: 0 });

describe('home2: market mood', () => {
  it('home2: breadth counts only markets that moved, leans at 60/40', () => {
    const m = marketMood([tk('A', 1), tk('B', 2), tk('C', -1), tk('D', 0), tk('E', 3)]);
    expect(m.advancing).toBe(3); expect(m.declining).toBe(1); expect(m.breadth).toBe(75);
    expect(leanOf(75)).toBe('up'); expect(leanOf(40)).toBe('down'); expect(leanOf(55)).toBe('mixed'); expect(leanOf(null)).toBe('unknown');
    expect(m.movers.map((x) => x.symbol)).toEqual(['E', 'B', 'A']);
  });
  it('home2: busy and moving is the turnover top intersected with movement; uncovered symbols are left out', () => {
    const list = [tk('A', 5, 10), tk('B', -9, 1_000_000), tk('C', 1, 900_000), tk('D', 0.1, 5)];
    const m = marketMood(list, (s) => s !== 'A');
    expect(m.movers.map((x) => x.symbol)).toEqual(['B', 'C', 'D']);
    expect(m.unusual[0]!.symbol).toBe('B');
    expect(moodIsEmpty(marketMood([]))).toBe(true);
    expect(marketMood([tk('A', 0), tk('B', 0)]).breadth).toBeNull();
  });
});

describe('home2: return loop', () => {
  const now = Date.UTC(2026, 9, 31, 12);
  it('home2: nothing under four hours away, nothing when nothing moved two per cent', () => {
    expect(sinceLastVisit(0, now, [{ symbol: 'X', change: 0.1 }])).toBeNull();
    expect(sinceLastVisit(now - (AWAY_HOURS * 3600e3 - 1), now, [{ symbol: 'X', change: 0.1 }])).toBeNull();
    expect(sinceLastVisit(now - 5 * 3600e3, now, [{ symbol: 'X', change: 0.01 }])).toBeNull();
    const s = sinceLastVisit(now - 30 * 3600e3, now, [{ symbol: 'A', change: 0.03 }, { symbol: 'B', change: -0.08 }, { symbol: 'C', change: 0.02 }, { symbol: 'D', change: 0.05 }], 2, 1)!;
    expect(s.movers.map((m) => m.symbol)).toEqual(['B', 'D', 'A']);
    expect(s.signals).toBe(2); expect(s.alerts).toBe(1);
    expect(sinceLastVisit(now - 5 * 3600e3, now, [], 0, 1)).not.toBeNull();
  });
  it('home2: the challenge is decided by the day, walked with stride seven', () => {
    expect(challengeFor(0).id).toBe('read-a-signal');
    expect(challengeFor(1).id).toBe('journal-a-trade');
    expect(challengeFor(1, true).title).toBe('Write one trade in the journal');
    expect(new Set(Array.from({ length: 12 }, (_, i) => challengeFor(i).id)).size).toBe(12);
    expect(challengeFor(-1).id).toBe(challengeFor(11).id);
  });
  it('home2: the arena streak survives a day of grace and no more', () => {
    expect(arenaStreak([], 100)).toBe(0);
    expect(arenaStreak([98, 99, 100], 100)).toBe(3);
    expect(arenaStreak([98, 99], 100)).toBe(2);
    expect(arenaStreak([97, 98], 100)).toBe(0);
    expect(arenaDays([{ epochDay: 5, total: 70 }, { day: 6 }, 'x', null])).toEqual([5, 6]);
    expect(arenaDays({ results: [{ epochDay: 9 }] })).toEqual([9]);
  });
  it('home2: the home card leads with the reader\'s own list and counts the rest', () => {
    const all = ['ETHUSDT', 'BTCUSDT', 'XAUUSD', 'A', 'B', 'C', 'D', 'E'];
    const r = homeMarkets(all, ['D', 'NOPE', 'C']);
    expect(r.rows.slice(0, 2)).toEqual(['D', 'C']);
    expect(r.rows[2]).toBe('BTCUSDT');
    expect(r.rows).toHaveLength(HOME_MARKET_ROWS);
    expect(r.more).toBe(2);
  });
  it('home2: the feed state in words', () => {
    expect(feedState({ liveAt: 1000, savedAt: null, online: true, now: 2000 })).toBe('live');
    expect(feedState({ liveAt: 0, savedAt: 5, online: false, now: 2000 })).toBe('cached');
    expect(feedState({ liveAt: 0, savedAt: 5, online: true, now: 2000 })).toBe('cachedRefreshing');
    expect(feedState({ liveAt: 0, savedAt: null, online: true, now: 2000 })).toBe('connecting');
    expect(feedState({ liveAt: 0, savedAt: null, online: false, now: 2000 })).toBe('offline');
    expect(feedState({ liveAt: 1000, savedAt: null, online: true, now: 60_000 })).toBe('degraded');
  });
});

const story = (id: string, market: 'crypto' | 'forex', title: string, time: number, relevance?: Story['relevance']): Story => ({ id, market, title, summary: '', source: 's', time, image: null, importance: 0, relevance });

describe('home2: news context', () => {
  it('home2: relevance, the chart it opens, and the sentence', () => {
    expect(relevanceOf(story('1', 'crypto', 'x', 0))).toEqual(['crypto']);
    expect(relevanceOf(story('2', 'forex', 'قیمت طلا امروز', 0))).toEqual(['gold']);
    expect(relevanceOf(story('3', 'forex', 'Silver vs gold', 0))).toEqual(['gold', 'silver']);
    expect(relevanceOf(story('4', 'forex', 'نقره', 0))).toEqual(['silver']);
    expect(parseRelevance(['XAUUSD', 'digital_assets', 'oil'])).toEqual(['gold', 'crypto']);
    expect(symbolFor(['silver', 'gold'])).toBe('XAUUSD');
    expect(symbolFor(['crypto'])).toBe('BTCUSDT');
    expect(symbolFor([])).toBeNull();
    expect(contextSentence(['gold', 'silver'], true)).toBe('این خبر روی طلا و نقره اثر دارد.');
    expect(contextSentence([], false)).toBe('This story is not tied to one market.');
  });
  it('home2: related stories are the same market, newest first, four at most', () => {
    const feed = [story('a', 'crypto', 'a', 1), story('b', 'crypto', 'b', 5), story('g', 'forex', 'طلا', 9), ...[1, 2, 3, 4].map((i) => story(`c${i}`, 'crypto', 'c', 10 + i))];
    const r = relatedTo(feed[0]!, feed);
    expect(r.map((x) => x.id)).toEqual(['c4', 'c3', 'c2', 'c1']);
    expect(r.some((x) => x.id === 'a' || x.id === 'g')).toBe(false);
    expect(relatedTo(story('u', 'crypto', 'u', 0, []), feed)).toHaveLength(4);
  });
});

describe('home2: profile facts', () => {
  it('home2: an unknown answer is drawn as unknown, never as a verdict', () => {
    const m = membershipFact(null, null, L);
    expect(m.value).toBeNull(); expect(m.tone).toBe('neutral');
    expect(membershipFact('approved', 'خوش آمدید', L)).toMatchObject({ value: 'تأیید شده', tone: 'settled', detail: 'خوش آمدید' });
    expect(membershipFact('something_new', null, L).value).toBeNull();
    expect(kycState('weird')).toBeNull(); expect(kycState('verified')).toBe('approved');
    expect(verificationFact(null, L, d).value).toBeNull();
    expect(verificationFact({ state: 'rejected', reason: 'تصویر ناخوانا' }, L, d)).toMatchObject({ value: 'رد شد', tone: 'refused', detail: 'تصویر ناخوانا' });
    expect(verificationFact({ state: 'approved', level: 2 }, E, d).detail).toBe('Level 2');
    expect(planFact({ label: null, expires: null, daysLeft: null, endingSoon: false }, L, d).value).toBeNull();
    expect(planFact({ label: 'ماهانه', expires: '۱۰ آذر', daysLeft: 3, endingSoon: true }, L, d)).toMatchObject({ tone: 'waiting', detail: 'تا ۱۰ آذر، 3 روز مانده' });
    expect(foundingFact(false, L)).toBeNull();
    expect(foundingFact(true, L)?.value).toBe('عضو بنیان‌گذار');
    expect(drawable([{ id: 'x', label: 'x', value: null, tone: 'neutral', detail: null }])).toHaveLength(0);
  });
  it('home2: the record counts and win rates', () => {
    expect(winRate([10, -5, null, 0, 3])).toBeCloseTo(66.666, 2);
    expect(winRate([null])).toBeNull();
    expect(winRateDetail(50, E)).toBe('Win rate ⁦50.0%⁩');
    const r = recordFacts({ journal: { count: 3, win: null }, paper: { count: 0, win: null }, academy: { done: 4, total: 140, streak: 3 } }, L, d);
    expect(r.map((x) => x.id)).toEqual(['journal', 'paper', 'academy']);
    expect(r[2]!.detail).toBe('3 روز پیاپی یادگیری');
  });
  it('home2: the avatar round-trips and a bad value falls back', () => {
    for (const raw of ['', 'sym:BTC', 'sym:EURUSD', 'mark:rocket', 'data:image/jpeg;base64,AAA', '🦁']) expect(encodeAvatar(decodeAvatar(raw))).toBe(raw);
    expect(decodeAvatar('mark:dragon').kind).toBe('initial');
    expect(readRing('analysis')).toBe('analysis'); expect(readRing(undefined)).toBe('gold');
  });
});

describe('home2: sign-in failures', () => {
  it('home2: a server fault, a non-answer and an unreadable answer are different sentences', () => {
    expect(authFailure(new HttpError(502, '<html>', null)).reason).toBe('serverFault');
    expect(authFailure(new HttpError(-1, 'TypeError', null)).reason).toBe('unreachable');
    expect(authFailure(new UnreadableAnswer()).reason).toBe('unreadable');
    expect(authFailure(new SyntaxError('x')).reason).toBe('unreadable');
    expect(authFailure(new HttpError(401, { detail: 'رمز اشتباه است' }, null))).toMatchObject({ reason: 'rejected', message: 'رمز اشتباه است' });
    expect(authFailure(new HttpError(422, null, null)).reason).toBe('invalid');
    expect(new Set(Object.values(AUTH_FAILURE_TEXT).map((x) => x[0])).size).toBe(6);
  });
  it('home2: too many carries the wait, counted down on the button', () => {
    expect(authFailure(new HttpError(429, null, 42_000))).toMatchObject({ reason: 'rateLimited', retryAfterSeconds: 42 });
    expect(retryIn(42, true, (n) => String(n).replace(/\d/g, (x) => '۰۱۲۳۴۵۶۷۸۹'[Number(x)]!))).toBe('تلاش دوباره تا ۴۲ ثانیه‌ی دیگر');
    expect(retryIn(5, false, String)).toBe('You can try again in 5 seconds');
  });
});

describe('home2: owner channel', () => {
  const reply = {
    messages: [
      { id: 7, kind: 'offer', priority: 'high', title: 'تخفیف', body: 'b', cta: 'خرید', link: 'https://pro-chart.com/pro', coupon: 'PC-ABC', notify: true, ends_at: null },
      { id: 8, kind: 'announcement', title: 't', link: 'javascript:alert(1)', coupon: 'bad code', ends_at: 1 },
      { id: 0, title: 'no id' },
    ],
    config: { maintenance: { on: true, message_fa: 'در حال تعمیر', message_en: '' }, update_nudge: { on: true, message_fa: '' }, min_version_code: 99_000_000 },
  };
  it('home2: campaigns are read safely; ended, settled and unsafe ones are dropped', () => {
    const m = messagesFrom(reply);
    expect(m.map((x) => x.id)).toEqual([7, 8]);
    expect(m[1]!.link).toBe(''); expect(m[1]!.coupon).toBe('');
    const b = boardFrom(reply, 5_000, new Set(), true, versionCodeOf('5.36.0'));
    expect(b.cards.map((x) => x.id)).toEqual([7]);
    expect(b.maintenance).toBe('در حال تعمیر');
    expect(b.updateNudge).toBe('');
    expect(boardFrom(reply, 5_000, new Set([7]), false, 0).cards).toHaveLength(0);
    expect(boardFrom(null, 0, new Set(), false, 0)).toEqual({ maintenance: null, updateNudge: null, cards: [] });
  });
  it('home2: the version code and the update nudge', () => {
    expect(versionCodeOf('5.36.0')).toBe(53_600_000);
    expect(versionCodeOf('x')).toBe(0);
    expect(nudge(true, 'm', 60_000_000, 53_600_000)).toBe('m');
    expect(nudge(true, 'm', 0, 1)).toBeNull();
    expect(nudge(false, 'm', 60_000_000, 1)).toBeNull();
    expect(isSafeLink('coinepro://signal/1')).toBe(true); expect(isSafeLink('http://x')).toBe(false); expect(isSafeLink('https://a b')).toBe(false);
  });
});

describe('home2: last error', () => {
  it('home2: the report has the line, the first own frame and the trace', () => {
    const e = new TypeError('x is undefined');
    e.stack = 'TypeError: x is undefined\n    at f (http://pc.local/next/assets/index-1.js:10:5)\n    at g (http://cdn/x.js:1:1)';
    const r = describeError(e, '', 1000, 'http://pc.local');
    expect(r.summary).toBe('TypeError: x is undefined');
    expect(r.culprit).toContain('index-1.js:10:5');
    expect(r.trace).toContain('at g');
    expect(describeError('boom', 'promise', 1).summary).toBe('Error: boom');
  });
});
