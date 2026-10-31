import { beforeEach, describe, expect, it } from 'vitest';
import {
  bodyOf, contentTypeOf, defaultBody, deliver, draftOf, failed, looksLikeJson, newDraft, signatureOf, timeframeName, useWebhooks, validateUrl,
  attemptSummary, hmacSha256, sha256, type WebhookTarget,
} from '../state/webhooks';
import { changePhrase, entryNote, historyOf, saveLine, useAlertAudit, type AuditView } from '../state/alertAudit';
import { DEFAULT_REPEAT_MINUTES, MAX_CONDITIONS, auditView, describeAlert, fire, scriptConditionHolds, startAlertWatch, useAlerts, type PriceAlert } from '../state/alerts';
import { highImpactWarningsFor, relevanceForSymbol } from '../pages/eventWindow';
import { parseRelevance, type CalendarEvent } from '../data/intel';
import { useMarket } from '../state/market';

const target = (over: Partial<WebhookTarget> = {}): WebhookTarget => ({ id: 't1', name: 'Bot', url: 'https://hooks.example.com/in', enabled: true, createdAt: 1, ...over });
const event = { alertId: 'a1', symbol: 'BTCUSDT', firedAt: 1_700_000_000_000, price: 68_500, timeframe: 'H1' };
const res = (status: number, type: ResponseType = 'cors') => ({ ok: status >= 200 && status < 300, status, type } as Response);

describe('alerts2: webhook addresses (WebhookUrl.validate)', () => {
  it('refuses with the five reasons, in order, and accepts an HTTPS domain on 80 or 443', () => {
    expect(validateUrl('')).toBe('empty');
    expect(validateUrl('   ')).toBe('empty');
    expect(validateUrl('hooks example.com')).toBe('malformed');
    expect(validateUrl('https://a b.com/x')).toBe('malformed');
    expect(validateUrl('http://hooks.example.com/x')).toBe('not_https');
    expect(validateUrl('ftp://hooks.example.com/x')).toBe('not_https');
    expect(validateUrl('https:///path')).toBe('no_host');
    expect(validateUrl('https://192.168.1.4/hook')).toBe('not_a_domain');
    expect(validateUrl('https://[::1]/hook')).toBe('not_a_domain');
    expect(validateUrl('https://localhost/hook')).toBe('not_a_domain');
    expect(validateUrl('https://intranet/hook')).toBe('not_a_domain');
    expect(validateUrl('https://hooks.example.com:8443/x')).toBe('port');
    expect(validateUrl('https://hooks.example.com:443/x')).toBeNull();
    expect(validateUrl('https://hooks.example.com:80/x')).toBeNull();
    expect(validateUrl(' https://hooks.example.com/in?x=1 ')).toBeNull();
  });
});

describe('alerts2: webhook bodies and signature', () => {
  it('reads JSON objects and arrays as JSON, everything else as text', () => {
    expect(looksLikeJson('{"side":"buy","symbol":"BTCUSDT"}')).toBe(true);
    expect(looksLikeJson(' [1, 2.5, -3e2, true, null, "x\\u00e9"] ')).toBe(true);
    expect(looksLikeJson('{}')).toBe(true);
    expect(looksLikeJson('طلا به ۲۶۰۰ رسید')).toBe(false);
    expect(looksLikeJson('12')).toBe(false);
    expect(looksLikeJson('"hello"')).toBe(false);
    expect(looksLikeJson('{bad}')).toBe(false);
    expect(looksLikeJson('{"a":1,}')).toBe(false);
    expect(looksLikeJson('{"a":"line\nbreak"}')).toBe(false);
    expect(looksLikeJson('['.repeat(33) + ']'.repeat(33))).toBe(false);
    expect(looksLikeJson('['.repeat(32) + ']'.repeat(32))).toBe(true);
    expect(contentTypeOf('{"a":1}')).toBe('application/json');
    expect(contentTypeOf('{ a sentence')).toBe('text/plain');
  });
  it('sends the reader’s message as typed, or the envelope with firedAt inside the signed body', () => {
    expect(defaultBody(event)).toBe('{"alertId":"a1","symbol":"BTCUSDT","firedAt":1700000000000,"price":68500,"timeframe":"H1"}');
    expect(defaultBody({ alertId: 'a"1', symbol: 'X', firedAt: 5, price: NaN })).toBe('{"alertId":"a\\"1","symbol":"X","firedAt":5}');
    expect(bodyOf({ ...event, message: '  hello  ' })).toBe('hello');
    expect(bodyOf({ ...event, message: '  ' })).toBe(defaultBody(event));
    expect(timeframeName('60')).toBe('H1');
    expect(timeframeName('1D')).toBe('D1');
  });
  it('signs with HMAC-SHA256 as sha256=<hex>, and signs nothing without a key', async () => {
    expect(await signatureOf('The quick brown fox jumps over the lazy dog', 'key')).toBe('sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8');
    expect(await signatureOf('x', '')).toBeNull();
    expect(await signatureOf('x', '   ')).toBeNull();
    expect(await signatureOf('x', null)).toBeNull();
  });
  it('signs the same in script where WebCrypto is missing (a page over plain HTTP)', async () => {
    const enc = new TextEncoder();
    const hex = (b: Uint8Array) => Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
    expect(hex(sha256(enc.encode('abc')))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
    expect(hex(sha256(enc.encode('')))).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    const long = 'x'.repeat(200), key = 'k'.repeat(100);
    expect(`sha256=${hex(hmacSha256(enc.encode(key), enc.encode(long)))}`).toBe(await signatureOf(long, key));
    expect(`sha256=${hex(hmacSha256(enc.encode('key'), enc.encode('The quick brown fox jumps over the lazy dog')))}`).toBe('sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8');
    const body = '{"alertId":"a","symbol":"طلا"}';
    expect(`sha256=${hex(hmacSha256(enc.encode('کلید'), enc.encode(body)))}`).toBe(await signatureOf(body, 'کلید'));
  });
});

describe('alerts2: delivering one webhook', () => {
  it('delivers on 2xx with the signature and timestamp headers, never the key, no redirects, no cookies', async () => {
    let seen: RequestInit | null = null;
    const r = await deliver(target(), event, 'top-secret-key', { fetch: async (_u, i) => { seen = i; return res(200); } });
    expect(r.attempt.outcome).toBe('delivered');
    expect(r.attempt.status).toBe(200);
    expect(r.mode).toBe('cors');
    const h = (seen as unknown as RequestInit).headers as Record<string, string>;
    expect(h['X-CoinePro-Signature']).toBe(await signatureOf(defaultBody(event), 'top-secret-key'));
    expect(h['Content-Type']).toBe('application/json');
    expect(Number(h['X-CoinePro-Timestamp'])).toBeGreaterThan(0);
    expect((seen as unknown as RequestInit).redirect).toBe('manual');
    expect((seen as unknown as RequestInit).credentials).toBe('omit');
    expect(JSON.stringify(seen)).not.toContain('top-secret-key');
    expect(JSON.stringify(r.attempt)).not.toContain('top-secret-key');
  });
  it('sends no signature header without a key', async () => {
    let h: Record<string, string> = {};
    await deliver(target(), event, null, { fetch: async (_u, i) => { h = i.headers as Record<string, string>; return res(204); } });
    expect('X-CoinePro-Signature' in h).toBe(false);
  });
  it('records a refusal, a redirect, a timeout and a dead host in its own words', async () => {
    expect((await deliver(target(), event, null, { fetch: async () => res(404) })).attempt).toMatchObject({ outcome: 'rejected', status: 404, reason: 'rejected' });
    expect((await deliver(target(), event, null, { fetch: async () => res(0, 'opaqueredirect') })).attempt.outcome).toBe('rejected');
    const abort = async () => { throw Object.assign(new Error('aborted'), { name: 'AbortError' }); };
    expect((await deliver(target(), event, null, { fetch: abort })).attempt).toMatchObject({ outcome: 'timeout', reason: 'timeout' });
    const dead = async () => { throw new TypeError('Failed to fetch'); };
    expect((await deliver(target(), event, null, { fetch: dead })).attempt).toMatchObject({ outcome: 'unreachable', reason: 'unreachable' });
    const blocked = await deliver(target({ url: 'http://x.com' }), event, null, { fetch: async () => res(200) });
    expect(blocked.attempt).toMatchObject({ outcome: 'blocked', reason: 'not_https' });
  });
  it('falls back to no-cors where CORS is refused, learns it, and says the answer is unreadable', async () => {
    const modes: string[] = [];
    const go = async (_u: string, i: RequestInit) => { modes.push(String(i.mode)); if (i.mode === 'cors') throw new TypeError('CORS'); return res(0, 'opaque'); };
    const r = await deliver(target(), event, 'k', { fetch: go });
    expect(modes).toEqual(['cors', 'no-cors']);
    expect(r.attempt.outcome).toBe('opaque');
    expect(r.mode).toBe('no-cors');
    expect(failed(r.attempt)).toBe(false);
    // Once learned, a firing leaves once, as plain text with no custom header.
    modes.length = 0;
    let h: Record<string, string> = {};
    const r2 = await deliver(target({ mode: 'no-cors' }), event, 'k', { fetch: async (u, i) => { h = i.headers as Record<string, string>; return go(u, i); } });
    expect(modes).toEqual(['no-cors']);
    expect(h).toEqual({ 'Content-Type': 'text/plain' });
    expect(r2.attempt.outcome).toBe('opaque');
    expect(attemptSummary(r2.attempt, true)).toContain('پاسخ خوانده نشد');
  });
  it('summarises an attempt with Latin figures', () => {
    const s = attemptSummary({ targetId: 't', targetName: 'n', alertId: 'a', at: 1, outcome: 'rejected', status: 500, latencyMs: 143, reason: 'rejected' }, true);
    expect(s).toContain('گیرنده نپذیرفت');
    expect(s).toContain('500');
    expect(s).toContain('143ms');
    expect(s).toContain('گیرنده درخواست را نپذیرفت');
  });
});

describe('alerts2: the webhook store keeps keys out of every pc. key', () => {
  beforeEach(() => { localStorage.clear(); useWebhooks.setState({ targets: [], log: [] }); });
  it('saves, keeps the key on an untouched edit, clears it on a touched empty one, forgets it on delete', () => {
    const st = useWebhooks.getState();
    expect(st.save({ ...newDraft(), name: '', url: 'https://hooks.example.com' })).toBeNull();
    expect(st.save({ ...newDraft(), name: 'Bot', url: 'http://hooks.example.com' })).toBeNull();
    const id = st.save({ ...newDraft(), name: 'Bot', url: 'https://hooks.example.com/in', secret: 'k-123-secret', secretTouched: true })!;
    expect(id).toBeTruthy();
    const pcValues = () => Object.keys(localStorage).filter((k) => k.startsWith('pc.')).map((k) => localStorage.getItem(k)).join('|');
    expect(pcValues()).not.toContain('k-123-secret');
    expect(JSON.stringify(useWebhooks.getState())).not.toContain('k-123-secret');
    const t = useWebhooks.getState().targets[0]!;
    // Opening for edit never loads the key.
    expect(draftOf(t).secret).toBe('');
    useWebhooks.getState().save({ ...draftOf(t), name: 'Bot 2' });
    expect(Object.values(localStorage).join('|')).toContain('k-123-secret');
    useWebhooks.getState().save({ ...draftOf(useWebhooks.getState().targets[0]!), secret: '', secretTouched: true });
    expect(Object.values(localStorage).join('|')).not.toContain('k-123-secret');
    useWebhooks.getState().save({ ...draftOf(useWebhooks.getState().targets[0]!), secret: 'again', secretTouched: true });
    useWebhooks.getState().remove(id);
    expect(Object.values(localStorage).join('|')).not.toContain('again');
    expect(useWebhooks.getState().targets).toHaveLength(0);
  });
  it('posts a firing only to the enabled webhooks, or to the alert’s chosen ones', async () => {
    useWebhooks.setState({ targets: [target({ id: 'a', url: 'https://a.example.com' }), target({ id: 'b', url: 'https://b.example.com', enabled: false }), target({ id: 'c', url: 'https://c.example.com' })] });
    const hits: string[] = [];
    const real = globalThis.fetch;
    globalThis.fetch = (async (u: string) => { hits.push(new URL(u).host); return res(200); }) as typeof fetch;
    try {
      const all = await useWebhooks.getState().dispatch(event);
      expect(hits.sort()).toEqual(['a.example.com', 'c.example.com']);
      expect(all.every((x) => x.outcome === 'delivered')).toBe(true);
      hits.length = 0;
      await useWebhooks.getState().dispatch(event, ['c']);
      expect(hits).toEqual(['c.example.com']);
      expect(useWebhooks.getState().log).toHaveLength(3);
      expect(useWebhooks.getState().log[0]!.targetId).toBe('c');
    } finally { globalThis.fetch = real; }
  });
});

describe('alerts2: an alert’s own history (AlertAuditTrail)', () => {
  const view = (over: Partial<AuditView> = {}): AuditView => ({ symbol: 'BTCUSDT', scope: '', condition: 'c', frequency: 'once', channels: 'true', loudness: 'chime', message: '', active: true, spent: false, ...over });
  it('names what changed in one clause, singular verb, no count', () => {
    expect(changePhrase(['condition'], true)).toBe('شرط تغییر کرد');
    expect(changePhrase(['condition', 'frequency'], true)).toBe('شرط و تکرار تغییر کرد');
    expect(changePhrase(['symbol', 'condition', 'message'], true)).toBe('نماد، شرط و پیام تغییر کرد');
    expect(changePhrase(['condition', 'frequency'], false)).toBe('Condition and repeat changed');
  });
  it('writes created, edited, armed or nothing', () => {
    expect(saveLine(null, view())).toEqual({ event: 'created' });
    expect(saveLine(view(), view())).toBeNull();
    expect(saveLine(view(), view({ message: '  ' }))).toBeNull();
    expect(saveLine(view(), view({ condition: 'd', loudness: 'bell' }))).toEqual({ event: 'edited', changed: ['condition', 'loudness'] });
    expect(saveLine(view({ active: false }), view())).toEqual({ event: 'armed' });
    expect(saveLine(view({ spent: true }), view({ spent: false }))).toEqual({ event: 'armed' });
  });
  it('records the reader’s acts and the firing with its delivery, oldest first', () => {
    localStorage.clear();
    useAlertAudit.setState({ entries: [] });
    useAlerts.setState({ list: [], fired: null });
    startAlertWatch();
    const id = useAlerts.getState().create({ symbol: 'LTCUSDT', condition: 'above', value: 100, freq: 'every', cooldownMs: 0 });
    useAlerts.getState().update(id, { value: 100 });
    useAlerts.getState().update(id, { value: 101 });
    useMarket.getState().put([{ symbol: 'LTCUSDT', last: 99, change: 0, changePct: 0, ts: Date.now() }]);
    useMarket.getState().put([{ symbol: 'LTCUSDT', last: 102, change: 0, changePct: 0, ts: Date.now() }]);
    useAlerts.getState().toggle(id);
    useAlerts.getState().remove(id);
    const h = historyOf(useAlertAudit.getState().entries, id);
    expect(h.map((e) => e.event)).toEqual(['created', 'edited', 'fired', 'delivered', 'snoozed', 'deleted']);
    expect(entryNote(h[1]!, true)).toBe('شرط تغییر کرد');
    expect(h[2]!.price).toBe(102);
    expect(h[0]!.price).toBeUndefined();
  });
});

describe('alerts2: conditions, repeats, the 24-hour change and scripts', () => {
  beforeEach(() => { localStorage.clear(); useAlerts.setState({ list: [], fired: null }); useAlertAudit.setState({ entries: [] }); });
  const tick = (symbol: string, last: number, changePct = 0) => useMarket.getState().put([{ symbol, last, change: 0, changePct, ts: Date.now() }]);
  it('holds up to five conditions and fires only when every one holds', () => {
    expect(MAX_CONDITIONS).toBe(5);
    startAlertWatch();
    useAlerts.getState().create({ symbol: 'ADAUSDT', condition: 'above', value: 1, ands: [{ condition: 'below', value: 2 }, { condition: 'above', value: 1.2 }, { condition: 'below', value: 1.8 }, { condition: 'above', value: 1.5 }] });
    tick('ADAUSDT', 1.1);
    tick('ADAUSDT', 1.3);
    expect(useAlerts.getState().fired).toBeNull();
    tick('ADAUSDT', 1.9);
    expect(useAlerts.getState().fired).toBeNull();
    tick('ADAUSDT', 1.6);
    expect(useAlerts.getState().fired?.symbol).toBe('ADAUSDT');
  });
  it('reads an older alert’s single `and` as its second condition', () => {
    startAlertWatch();
    useAlerts.getState().create({ symbol: 'TRXUSDT', condition: 'above', value: 1, and: { condition: 'below', value: 2 } });
    tick('TRXUSDT', 3);
    expect(useAlerts.getState().fired).toBeNull();
    tick('TRXUSDT', 1.5);
    expect(useAlerts.getState().fired?.symbol).toBe('TRXUSDT');
  });
  it('fires on the 24-hour change over or under a per cent, never on a missing figure', () => {
    startAlertWatch();
    useAlerts.getState().create({ symbol: 'SUIUSDT', condition: 'change24_under', value: 5 });
    tick('SUIUSDT', 1, -4.9);
    expect(useAlerts.getState().fired).toBeNull();
    tick('SUIUSDT', 1, NaN);
    expect(useAlerts.getState().fired).toBeNull();
    tick('SUIUSDT', 1, -5);
    expect(useAlerts.getState().fired?.condition).toBe('change24_under');
    expect(describeAlert({ symbol: 'SUIUSDT', condition: 'change24_over', value: 5 }, true)).toBe('تغییر ۲۴ ساعته‌ی SUIUSDT بیشتر از 5%');
    expect(describeAlert({ symbol: 'SUIUSDT', condition: 'change24_under', value: 5 }, true)).toBe('تغییر ۲۴ ساعته‌ی SUIUSDT کمتر از منفی 5%');
  });
  it('once a day: at most once in twenty-four hours', () => {
    const a = { id: 'x', symbol: 'X', condition: 'above', value: 1, active: true, createdAt: 0, freq: 'daily' } as PriceAlert;
    const t0 = 1_000_000_000_000;
    const f1 = fire(a, t0)!;
    expect(f1.active).toBe(true);
    expect(fire(f1, t0 + 23 * 3600e3)).toBeNull();
    expect(fire(f1, t0 + 24 * 3600e3)).not.toBeNull();
  });
  it('until I see it: stays on, repeats every N minutes, stops when seen', () => {
    const a = { id: 'y', symbol: 'Y', condition: 'above', value: 1, active: true, createdAt: 0, freq: 'once', untilSeen: true, repeatMinutes: 5 } as PriceAlert;
    const t0 = 1_000_000_000_000;
    const f1 = fire(a, t0)!;
    expect(f1.active).toBe(true);
    expect(fire(f1, t0 + 4 * 60e3)).toBeNull();
    expect(fire(f1, t0 + 5 * 60e3)).not.toBeNull();
    expect(fire({ ...f1, seenAt: t0 + 1 }, t0 + 60 * 60e3)).toBeNull();
    expect(fire({ ...f1, repeatMinutes: undefined }, t0 + (DEFAULT_REPEAT_MINUTES - 1) * 60e3)).toBeNull();
    // «دیدم» in the store: stamped and switched off.
    const id = useAlerts.getState().create({ symbol: 'Y', condition: 'above', value: 1, untilSeen: true, repeatMinutes: 5 });
    useAlerts.getState().update(id, { lastFiredAt: t0 }, { silent: true });
    useAlerts.getState().acknowledge(id);
    const after = useAlerts.getState().list.find((x) => x.id === id)!;
    expect(after.active).toBe(false);
    expect(after.seenAt).toBeGreaterThan(0);
  });
  it('a script condition holds when the named alertcondition fired on the bar asked about', () => {
    const alerts = [{ msg: 'Buy', bars: [100, 200] }, { msg: 'Sell', bars: [150] }, { msg: 'Buy', bars: [300], dynamic: true }];
    expect(scriptConditionHolds(alerts, 'Buy', 200)).toBe(true);
    expect(scriptConditionHolds(alerts, 'Buy', 300)).toBe(false);
    expect(scriptConditionHolds(alerts, 'Sell', 200)).toBe(false);
    expect(scriptConditionHolds(undefined, 'Buy', 200)).toBe(false);
    expect(describeAlert({ symbol: 'BTCUSDT', condition: 'above', value: 1, source: 'script', script: { source: 'x', condition: 'Buy', name: 'HMA' } }, true)).toBe('اسکریپت «HMA»: Buy');
  });
  it('the history sees an until-seen change as a change of repeat', () => {
    const base = { id: 'z', symbol: 'Z', condition: 'above', value: 1, active: true, createdAt: 0 } as PriceAlert;
    expect(saveLine(auditView(base), auditView({ ...base, untilSeen: true }))).toEqual({ event: 'edited', changed: ['frequency'] });
    expect(saveLine(auditView(base), auditView({ ...base, webhooks: ['a'] }))).toEqual({ event: 'edited', changed: ['channels'] });
  });
});

describe('alerts2: the high-impact event window on a signal', () => {
  const now = Date.UTC(2026, 9, 31, 12);
  const ev = (over: Partial<CalendarEvent>): CalendarEvent => ({ title: 'CPI', currency: 'USD', time: now + 3600e3, impact: 'high', forecast: '', previous: '', actual: '', relevance: ['gold', 'silver'], ...over });
  it('takes high-impact events that move the market, an hour back to six ahead, soonest first', () => {
    expect(relevanceForSymbol('XAUUSD')).toBe('gold');
    expect(relevanceForSymbol('BTCUSDT')).toBe('crypto');
    expect(relevanceForSymbol('EURUSD')).toBeNull();
    const list = [ev({ title: 'late', time: now + 5 * 3600e3 }), ev({ title: 'soon', time: now + 600e3 }), ev({ title: 'past', time: now - 2 * 3600e3 }), ev({ title: 'far', time: now + 7 * 3600e3 }), ev({ title: 'medium', impact: 'medium' }), ev({ title: 'recent', time: now - 1800e3 })];
    expect(highImpactWarningsFor(list, 'XAUUSD', now).map((e) => e.title)).toEqual(['recent', 'soon', 'late']);
    expect(highImpactWarningsFor(list, 'BTCUSDT', now)).toEqual([]);
    expect(highImpactWarningsFor([ev({ relevance: ['crypto'] })], 'BTCUSDT', now)).toHaveLength(1);
    expect(highImpactWarningsFor(list, 'EURUSD', now)).toEqual([]);
    expect(highImpactWarningsFor([ev({ relevance: undefined })], 'XAGUSD', now)).toHaveLength(1);
  });
  it('parses the feed’s market tags', () => {
    expect(parseRelevance({ tags: ['Gold', 'xag', 'other'] })).toEqual(['gold', 'silver']);
    expect(parseRelevance({ markets: 'crypto,digital_assets' })).toEqual(['crypto']);
    expect(parseRelevance({})).toBeNull();
  });
});
