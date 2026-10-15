import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  EventQueue, KINDS, apply, collect, describeError, events, exportWorkspace, flushEvents, hashBlob, importWorkspace, kindOf, plan, readMemo,
  scrub, setTelemetry, syncWorkspace, telemetryOn, writeMemo, type WorkspaceBlob,
} from '../data/panel';
import { useSession } from '../data/session';
import { todayPnl, startOfToday, type Closed } from '../usercp/sources';

const signIn = () => useSession.getState().put('tradeyar', { access: 'pch_e2e', refresh: 'pch_r', refreshAt: Date.now() + 3_600_000, user: null });

beforeEach(() => { localStorage.clear(); useSession.getState().signOut(); events.clear(); });
afterEach(() => { vi.unstubAllGlobals(); });

describe('workspace parts', () => {
  it('every pc.* key belongs to at most one part, and the secrets to none', () => {
    expect(kindOf('pc.layouts.v1')).toBe('layouts');
    expect(kindOf('pc.grid.v1')).toBe('layouts');
    expect(kindOf('pc.chartsettings.v1')).toBe('settings');
    expect(kindOf('pc.drawings.v1.BTCUSDT')).toBe('drawings');
    expect(kindOf('pc.studies.v1')).toBe('templates');
    expect(kindOf('pc.namascript.v1')).toBe('templates');
    expect(kindOf('pc.watchlists.v2')).toBe('watchlist');
    for (const secret of ['pc.sessions.v1', 'pc.community.key.v1', 'pc.fx.guest.v1', 'pc.cloud.v1', 'pc.telemetry.v1', 'pc_client_id', 'install_id_web', 'pc.journal.v1']) {
      expect(kindOf(secret)).toBeNull();
    }
  });

  it('collects one part and applies a blob back, its own keys only', () => {
    localStorage.setItem('pc.drawings.v1.BTCUSDT', '[1]');
    localStorage.setItem('pc.drawings.v1.ETHUSDT', '[2]');
    localStorage.setItem('pc.layouts.v1', '{}');
    const blob = collect(localStorage, 'drawings');
    expect(Object.keys(blob.keys)).toEqual(['pc.drawings.v1.BTCUSDT', 'pc.drawings.v1.ETHUSDT']);
    const written = apply(localStorage, 'drawings', { keys: { 'pc.drawings.v1.SOLUSDT': '[3]', 'pc.sessions.v1': 'stolen', 'pc.layouts.v1': 'x', 'pc.drawings.v1.BAD': 5 } });
    expect(written).toBe(1);
    expect(localStorage.getItem('pc.drawings.v1.SOLUSDT')).toBe('[3]');
    // A drawing the cloud version does not have is gone; nothing outside the part was touched.
    expect(localStorage.getItem('pc.drawings.v1.BTCUSDT')).toBeNull();
    expect(localStorage.getItem('pc.sessions.v1')).not.toBe('stolen');
    expect(localStorage.getItem('pc.layouts.v1')).toBe('{}');
    expect(apply(localStorage, 'drawings', null)).toBe(0);
  });

  it('hashes a blob the same whatever the key order, and differently when a value changes', () => {
    const a: WorkspaceBlob = { keys: { 'pc.a': '1', 'pc.b': '2' } };
    const b: WorkspaceBlob = { keys: { 'pc.b': '2', 'pc.a': '1' } };
    expect(hashBlob(a)).toBe(hashBlob(b));
    expect(hashBlob(a)).not.toBe(hashBlob({ keys: { 'pc.a': '1', 'pc.b': '3' } }));
  });

  it('plans push, pull, conflict or nothing', () => {
    const local: WorkspaceBlob = { keys: { 'pc.layouts.v1': 'x' } };
    const empty: WorkspaceBlob = { keys: {} };
    const memo = { version: 3, hash: hashBlob(local), at: 0 };
    expect(plan(local, 3, memo)).toBe('none');
    expect(plan({ keys: { 'pc.layouts.v1': 'y' } }, 3, memo)).toBe('push');
    expect(plan(local, 4, memo)).toBe('pull');
    expect(plan({ keys: { 'pc.layouts.v1': 'y' } }, 4, memo)).toBe('conflict');
    expect(plan(local, 0, undefined)).toBe('push');
    expect(plan(empty, 0, undefined)).toBe('none');
    // An empty browser never overwrites a filled cloud: it takes it.
    expect(plan(empty, 2, undefined)).toBe('pull');
    expect(plan(local, 2, undefined)).toBe('conflict');
  });

  it('a workspace file goes out and comes back in, and anything else is refused', () => {
    localStorage.setItem('pc.layouts.v1', '{"layouts":[1]}');
    localStorage.setItem('pc.sessions.v1', 'secret');
    const file = exportWorkspace();
    expect(file).not.toContain('secret');
    localStorage.clear();
    expect(importWorkspace(file)).toBe(1);
    expect(localStorage.getItem('pc.layouts.v1')).toBe('{"layouts":[1]}');
    expect(() => importWorkspace('{"format":"other"}')).toThrow();
  });
});

describe('sync with the cloud', () => {
  function server(initial: Record<string, { version: number; data: WorkspaceBlob | null }>) {
    const state = { ...initial };
    const calls: { method: string; url: string; body: unknown; auth: string | null }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      const body = init.body ? JSON.parse(String(init.body)) : null;
      calls.push({ method: init.method ?? 'GET', url, body, auth: new Headers(init.headers).get('Authorization') });
      const kind = url.split('/workspace/')[1];
      let answer: unknown;
      if (url.endsWith('/up/panel/workspace')) {
        answer = { kinds: KINDS.map((k) => ({ kind: k, version: state[k]?.version ?? 0, size: 0, updated_at: null, device: null, versions: 0, limit: 262144 })) };
      } else if (init.method === 'PUT') {
        const current = state[kind!]?.version ?? 0;
        if (body.base_version !== current && !body.force) return new Response(JSON.stringify({ error: 'newer_version', version: current }), { status: 409 });
        state[kind!] = { version: current + 1, data: body.data };
        answer = { version: current + 1 };
      } else {
        answer = { version: state[kind!]?.version ?? 0, data: state[kind!]?.data ?? null };
      }
      return new Response(JSON.stringify(answer), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }));
    return { state, calls };
  }

  it('pushes this browser, pulls another device, and reports a conflict until it is settled', async () => {
    signIn();
    localStorage.setItem('pc.layouts.v1', 'mine');
    const { state, calls } = server({ settings: { version: 2, data: { keys: { 'pc.prefs.v1': 'theirs' } } } });
    const first = await syncWorkspace();
    expect(Object.fromEntries(first.results.map((r) => [r.kind, r.action]))).toMatchObject({ layouts: 'push', settings: 'pull', drawings: 'none' });
    expect(first.pulled).toBe(true);
    expect(localStorage.getItem('pc.prefs.v1')).toBe('theirs');
    expect(state.layouts!.version).toBe(1);
    expect(calls.every((c) => c.url.startsWith('/up/panel/') && c.auth === 'Bearer pch_e2e')).toBe(true);
    expect(readMemo().kinds.layouts!.version).toBe(1);

    // Another device saves layouts while this browser changes them too.
    state.layouts = { version: 2, data: { keys: { 'pc.layouts.v1': 'other device' } } };
    localStorage.setItem('pc.layouts.v1', 'mine, changed');
    const second = await syncWorkspace();
    expect(second.results.find((r) => r.kind === 'layouts')!.action).toBe('conflict');
    const kept = await syncWorkspace({ resolve: { layouts: 'local' } });
    expect(kept.results.find((r) => r.kind === 'layouts')).toMatchObject({ action: 'push', version: 3 });
    expect(state.layouts.data!.keys['pc.layouts.v1']).toBe('mine, changed');
  });

  it('the automatic run only ever sends, never replaces what is here', async () => {
    signIn();
    localStorage.setItem('pc.watchlists.v2', 'local');
    server({ settings: { version: 5, data: { keys: { 'pc.prefs.v1': 'cloud' } } } });
    const r = await syncWorkspace({ pushOnly: true });
    expect(r.pulled).toBe(false);
    expect(localStorage.getItem('pc.prefs.v1')).toBeNull();
    expect(r.results.find((x) => x.kind === 'watchlist')!.action).toBe('push');
  });

  it('remembers what it agreed on per part', () => {
    writeMemo({ auto: true, kinds: { layouts: { version: 4, hash: 'h', at: 1 } } });
    expect(readMemo()).toEqual({ auto: true, kinds: { layouts: { version: 4, hash: 'h', at: 1 } } });
    localStorage.setItem('pc.cloud.v1', 'not json');
    expect(readMemo()).toEqual({ kinds: {} });
  });
});

describe('the page\'s own events', () => {
  it('keeps names short, repeats few and the queue bounded', () => {
    const q = new EventQueue(10, 2);
    expect(q.push('error', 'x'.repeat(200))).toBe(true);
    expect(q.take()[0]!.name.length).toBe(80);
    expect(q.push('feature', 'tool.trend_line')).toBe(true);
    expect(q.push('feature', 'tool.trend_line')).toBe(true);
    expect(q.push('feature', 'tool.trend_line')).toBe(false);
    for (let i = 0; i < 20; i++) q.push('page', `page/${i}`);
    expect(q.size).toBe(10);
    const batch = q.take(4);
    expect(batch).toHaveLength(4);
    q.putBack(batch);
    expect(q.size).toBe(10);
    expect(q.push('page', '   ')).toBe(false);
  });

  it('never lets a handle, a bearer, an email or a query string out', () => {
    const text = scrub('GET /up/x?token=abc failed for sara@example.com with Bearer abc.def pch_secret123');
    expect(text).not.toMatch(/pch_secret|abc\.def|sara@|token=abc/);
    const q = new EventQueue();
    q.push('error', 'e', { stack: 'at https://pro-chart.com/next/assets/a.js?v=pch_x:1:2', message: 'x'.repeat(900) });
    const d = q.take()[0]!.detail!;
    expect(String(d.stack)).not.toContain('pch_x');
    expect(String(d.message).length).toBe(300);
    const e = describeError(new TypeError('cannot read sara@example.com'), 'a.js:1');
    expect(e.name).toBe('TypeError: cannot read …@…');
    expect(e.detail.source).toBe('a.js:1');
    expect(describeError('plain').name).toBe('Error: plain');
  });

  it('sends only for a signed-in reader who has not turned it off', async () => {
    const fetch = vi.fn(async () => new Response('{"stored":1}', { status: 200 }));
    vi.stubGlobal('fetch', fetch);
    events.push('feature', 'indicator.add.rsi');
    expect(await flushEvents()).toBe(0);
    expect(events.size).toBe(0);
    signIn();
    events.push('feature', 'indicator.add.macd');
    expect(await flushEvents()).toBe(1);
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/up/panel/event');
    expect(JSON.parse(String(init.body)).events[0].name).toBe('indicator.add.macd');
    setTelemetry(false);
    expect(telemetryOn()).toBe(false);
    events.push('feature', 'indicator.add.ema');
    expect(await flushEvents()).toBe(0);
    setTelemetry(true);
  });
});

describe('today\'s P&L', () => {
  it('adds up only what closed today, per currency', () => {
    const now = new Date(2026, 9, 15, 14, 0);
    const t = (closed: number, pnl: number, currency = 'USDT'): Closed => ({ backend: 'tradeyar', id: '1', symbol: 'BTCUSDT', side: 'buy', opened: null, closed, entry: null, exit: null, qty: null, pnl, fee: 0, currency });
    const today = startOfToday(now);
    expect(todayPnl([t(today + 1000, 20), t(today + 2000, -5), t(today - 1000, 99), t(today + 3000, 7, 'USD')], now)).toEqual({ USDT: 15, USD: 7 });
  });
});
