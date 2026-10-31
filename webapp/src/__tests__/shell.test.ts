import { beforeEach, describe, expect, it } from 'vitest';
import { oldestSaved, readSnapshot, saveSnapshot, savedAgeOf, savedAgeSentence, savedAgo, useSaved, withSaved } from '../data/saved';
import { uncheckedIds } from '../state/alerts';
import { addressStep } from '../route';
import { retryDelay, RETRY_STEPS } from '../chart/ChartStatus';
import { countdownSeconds, countdownText, shouldPublish, visibleChange, watchShape, type WatchSnapshot } from '../shell/watch';
import { PAGES } from '../pages/registry';
import SW_CODE from '../../public/sw.js?raw';

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

describe('saved age (SavedAge.kt)', () => {
  const now = 1_700_000_000_000;
  it('is null for no time, a zero time and a time ahead of the clock', () => {
    expect(savedAgeOf(null, now)).toBeNull();
    expect(savedAgeOf(undefined, now)).toBeNull();
    expect(savedAgeOf(0, now)).toBeNull();
    expect(savedAgeOf(now + 5000, now)).toBeNull();
  });
  it('floors into moments, minutes, hours, days', () => {
    expect(savedAgeOf(now - 59_000, now)).toEqual({ unit: 'moments', count: 0 });
    expect(savedAgeOf(now - MIN, now)).toEqual({ unit: 'minutes', count: 1 });
    expect(savedAgeOf(now - 59 * MIN - 59_000, now)).toEqual({ unit: 'minutes', count: 59 });
    expect(savedAgeOf(now - 2 * HOUR - 30 * MIN, now)).toEqual({ unit: 'hours', count: 2 });
    expect(savedAgeOf(now - 23 * HOUR - 59 * MIN, now)).toEqual({ unit: 'hours', count: 23 });
    expect(savedAgeOf(now - 3 * DAY, now)).toEqual({ unit: 'days', count: 3 });
  });
  it('says it in both languages, Persian digits for the Persian count', () => {
    expect(savedAgo({ unit: 'hours', count: 2 }, 'fa')).toBe('ذخیره‌شده · ۲ ساعت پیش');
    expect(savedAgo({ unit: 'moments', count: 0 }, 'fa')).toBe('ذخیره‌شده · همین حالا');
    expect(savedAgeSentence({ unit: 'minutes', count: 15 }, 'fa')).toBe('۱۵ دقیقه پیش');
    expect(savedAgeSentence({ unit: 'days', count: 1 }, 'fa')).toBe('۱ روز پیش');
    expect(savedAgo({ unit: 'hours', count: 1 }, 'en')).toBe('Saved · 1 hour ago');
    expect(savedAgeSentence({ unit: 'minutes', count: 2 }, 'en')).toBe('2 minutes ago');
    expect(savedAgeSentence({ unit: 'moments', count: 0 }, 'en')).toBe('just now');
  });
});

describe('saved pictures', () => {
  beforeEach(() => { localStorage.clear(); useSaved.setState({ at: {} }); });
  it('keeps the last good answer and gives it back, with its time, when the next fails', async () => {
    const a = await withSaved('news:crypto', async () => [1, 2]);
    expect(a).toEqual({ data: [1, 2], savedAt: null });
    expect(useSaved.getState().at['news:crypto']).toBeUndefined();
    const b = await withSaved<number[]>('news:crypto', async () => { throw new Error('offline'); });
    expect(b.data).toEqual([1, 2]);
    expect(typeof b.savedAt).toBe('number');
    expect(useSaved.getState().at['news:crypto']).toBe(b.savedAt);
    // A fresh answer clears the mark.
    await withSaved('news:crypto', async () => [3]);
    expect(useSaved.getState().at['news:crypto']).toBeUndefined();
  });
  it('fails as before when nothing was ever saved, and does not save an empty answer', async () => {
    await expect(withSaved('calendar', async () => { throw new Error('x'); })).rejects.toThrow('x');
    await withSaved('calendar', async () => [] as number[], (d) => d.length > 0);
    expect(readSnapshot('calendar')).toBeNull();
  });
  it('keeps at most twelve bar pictures, dropping the oldest', () => {
    for (let i = 0; i < 15; i++) saveSnapshot(`bars:S${i}|60`, { bars: [i] }, 1000 + i);
    expect(readSnapshot('bars:S0|60')).toBeNull();
    expect(readSnapshot('bars:S2|60')).toBeNull();
    expect(readSnapshot('bars:S3|60')?.data).toEqual({ bars: [3] });
    expect(readSnapshot('bars:S14|60')?.at).toBe(1014);
  });
  it('the oldest saved time among several feeds', () => {
    expect(oldestSaved({ a: 5, b: 3 }, ['a', 'b', 'c'])).toBe(3);
    expect(oldestSaved({ a: 5 }, ['b'])).toBeNull();
  });
});

describe('«هنوز بررسی نشده» (AlertReach.kt)', () => {
  const list = [
    { id: 'new', active: true, createdAt: 2000 },
    { id: 'old', active: true, createdAt: 500 },
    { id: 'paused', active: false, createdAt: 3000 },
    { id: 'legacy', active: true, createdAt: 0 },
  ];
  it('every active alert with a creation time when no pass ever read prices', () => {
    expect([...uncheckedIds(list, null)].sort()).toEqual(['new', 'old']);
  });
  it('only the alerts created after the last pass', () => {
    expect([...uncheckedIds(list, 1000)]).toEqual(['new']);
    expect(uncheckedIds(list, 5000).size).toBe(0);
  });
});

describe('browser Back (route.ts)', () => {
  it('the first address is written over, not stacked', () => {
    expect(addressStep(null, { page: 'markets' }, null)).toBe('replace');
  });
  it('opening a page, or another page over one, is a new entry', () => {
    expect(addressStep({ page: null }, { page: 'news' }, null)).toBe('push');
    expect(addressStep({ page: 'news' }, { page: 'alerts' }, { pcDepth: 1 })).toBe('push');
  });
  it('a markets-hub face, a page segment and the chart rewrite the entry', () => {
    expect(addressStep({ page: 'markets' }, { page: 'heatmap' }, { pcDepth: 1 })).toBe('replace');
    expect(addressStep({ page: 'help' }, { page: 'help' }, { pcDepth: 1 })).toBe('replace');
    expect(addressStep({ page: null }, { page: null }, null)).toBe('replace');
  });
  it('closing a page the app opened walks back over its entries; one opened by a link is rewritten', () => {
    expect(addressStep({ page: 'news' }, { page: null }, { pcDepth: 2 })).toBe('back');
    expect(addressStep({ page: 'news' }, { page: null }, null)).toBe('replace');
  });
});

describe('menu: one door per feature', () => {
  const listed = PAGES.filter((p) => p.group && p.directory !== false);
  it('lists each feature once, with the exchange membership', () => {
    const names = listed.map((p) => p.fa);
    expect(new Set(names).size).toBe(names.length);
    expect(listed.some((p) => p.id === 'membership' && p.group === 'account')).toBe(true);
  });
  it('the hub faces, Pro and the second Home are not menu rows', () => {
    for (const id of ['heatmap', 'screener', 'pro', 'home']) expect(listed.some((p) => p.id === id)).toBe(false);
    expect(listed.some((p) => p.id === 'markets')).toBe(true);
  });
});

describe('chart failure retry', () => {
  it('waits longer each time, then every minute', () => {
    expect(RETRY_STEPS.map((_, i) => retryDelay(i))).toEqual([5, 10, 20, 40, 60]);
    expect(retryDelay(12)).toBe(60);
  });
});

describe('keep watching (ChartWatch.kt)', () => {
  const snap = (over: Partial<WatchSnapshot> = {}): WatchSnapshot => ({ symbol: 'BTCUSDT', interval: '1h', price: 100, changePct: 1, closes: [1, 2], closesAt: 10, ...over });
  it('publishes the first snapshot at once, never an equal one, else at most once a second', () => {
    expect(shouldPublish(null, snap(), null, 0)).toBe(true);
    expect(shouldPublish(snap(), snap(), 0, 5000)).toBe(false);
    expect(shouldPublish(snap(), snap({ price: 101 }), 1000, 1500)).toBe(false);
    expect(shouldPublish(snap(), snap({ price: 101 }), 1000, 2000)).toBe(true);
  });
  it('counts down only to a close that is ahead and within a week', () => {
    expect(countdownSeconds(null, 0)).toBeNull();
    expect(countdownSeconds(100, 100)).toBeNull();
    expect(countdownSeconds(100, 150)).toBeNull();
    expect(countdownSeconds(145, 100)).toBe(45);
    expect(countdownSeconds(100 + 8 * 86400, 100)).toBeNull();
    expect(countdownText(45)).toBe('0:45');
    expect(countdownText(725)).toBe('12:05');
    expect(countdownText(3 * 3600 + 249)).toBe('3:04:09');
  });
  it('draws the line first against last, flat centred, nothing under two points', () => {
    expect(watchShape([5])).toBeNull();
    expect(watchShape([1, Number.NaN])).toBeNull();
    const fell = watchShape([10, 2, 3])!;
    expect(fell.rising).toBe(false);
    expect(fell.points[0]).toEqual({ x: 0, y: 0 });
    expect(fell.points[1]).toEqual({ x: 0.5, y: 1 });
    expect(watchShape([4, 4, 4])!.points.every((p) => p.y === 0.5)).toBe(true);
  });
  it('reads the change across the visible window', () => {
    expect(visibleChange([100, 110, 120, 90], 0.4, 2.2)).toBeCloseTo(9.0909, 3);
    expect(visibleChange([100], 0, 0)).toBeNull();
  });
});

// ── The service worker (public/sw.js), run against a fake worker scope ──────────────────────────
type Req = { method: string; url: string; mode?: string };
function loadWorker(net: (req: Req) => Promise<Response>) {
  const code = SW_CODE;
  const listeners: Record<string, (e: unknown) => void> = {};
  const store = new Map<string, Map<string, Response>>();
  const ORIGIN = 'https://pro-chart.com';
  const keyOf = (r: Req | string) => new URL(typeof r === 'string' ? r : r.url, ORIGIN).href;
  const caches = {
    open: async (name: string) => {
      if (!store.has(name)) store.set(name, new Map());
      const m = store.get(name)!;
      return {
        put: async (r: Req | string, res: Response) => { m.set(keyOf(r), res); },
        add: async (u: string) => { m.set(keyOf(u), await net({ method: 'GET', url: keyOf(u) })); },
      };
    },
    keys: async () => [...store.keys()],
    delete: async (n: string) => store.delete(n),
    match: async (r: Req | string, opts: { ignoreSearch?: boolean; cacheName?: string } = {}) => {
      const want = new URL(keyOf(r));
      if (opts.ignoreSearch) want.search = '';
      for (const [name, m] of store) {
        if (opts.cacheName && name !== opts.cacheName) continue;
        for (const [k, v] of m) { const u = new URL(k); if (opts.ignoreSearch) u.search = ''; if (u.href === want.href) return v.clone(); }
      }
      return undefined;
    },
  };
  const self = {
    location: new URL(`${ORIGIN}/next/sw.js`),
    addEventListener: (t: string, fn: (e: unknown) => void) => { listeners[t] = fn; },
    skipWaiting: async () => undefined,
    clients: { claim: async () => undefined },
  };
  new Function('self', 'caches', 'fetch', code)(self, caches, net);
  const fetchEvent = async (req: Req): Promise<Response | null> => {
    let answer: Promise<Response> | null = null;
    listeners.fetch!({ request: req, respondWith: (p: Promise<Response>) => { answer = p; } });
    return answer ? await answer : null;
  };
  const lifecycle = async (t: 'install' | 'activate') => { let w: Promise<unknown> = Promise.resolve(); listeners[t]!({ waitUntil: (p: Promise<unknown>) => { w = p; } }); await w; };
  return { fetchEvent, lifecycle, store, ORIGIN };
}

describe('service worker (sw.js)', () => {
  let online = true;
  const net = async (req: Req) => {
    if (!online) throw new TypeError('Failed to fetch');
    return new Response(`body of ${new URL(req.url).pathname}`, { status: 200 });
  };
  const flush = () => new Promise((r) => setTimeout(r, 0));

  it('leaves data, other origins, other folders and non-GETs to the network', async () => {
    online = true;
    const w = loadWorker(net);
    for (const req of [
      { method: 'GET', url: `${w.ORIGIN}/api/crypto/prices` },
      { method: 'GET', url: `${w.ORIGIN}/up/coineprofx/api/x` },
      { method: 'GET', url: `${w.ORIGIN}/next/api/x` },
      { method: 'GET', url: `${w.ORIGIN}/next/up/x` },
      { method: 'GET', url: `${w.ORIGIN}/terminal/app.js` },
      { method: 'GET', url: 'https://cdn.example.com/next/x.js' },
      { method: 'POST', url: `${w.ORIGIN}/next/assets/a.js` },
      { method: 'GET', url: `${w.ORIGIN}/next/sw.js` },
    ]) expect(await w.fetchEvent(req), req.url).toBeNull();
  });

  it('is network first, and answers from the last good copy only when the network does not', async () => {
    online = true;
    const w = loadWorker(net);
    const js = { method: 'GET', url: `${w.ORIGIN}/next/assets/index-abc.js` };
    expect(await (await w.fetchEvent(js))!.text()).toBe('body of /next/assets/index-abc.js');
    await flush();
    online = false;
    expect(await (await w.fetchEvent(js))!.text()).toBe('body of /next/assets/index-abc.js');
    const missing = await w.fetchEvent({ method: 'GET', url: `${w.ORIGIN}/next/assets/never.js` });
    expect(missing!.status).toBe(504);
  });

  it('opens a deep link offline with the cached shell', async () => {
    online = true;
    const w = loadWorker(net);
    await w.fetchEvent({ method: 'GET', url: `${w.ORIGIN}/next/BTCUSDT/1h?e2e=1`, mode: 'navigate' });
    await flush();
    online = false;
    const page = await w.fetchEvent({ method: 'GET', url: `${w.ORIGIN}/next/markets`, mode: 'navigate' });
    expect(page!.status).toBe(200);
    expect(await page!.text()).toBe('body of /next/BTCUSDT/1h');
  });

  it('caches the shell on install and drops only its own older caches on activate', async () => {
    online = true;
    const w = loadWorker(net);
    w.store.set('pro-chart-shell:/next/:v0', new Map());
    w.store.set('pro-chart-shell-v1', new Map()); // the old terminal's, on the same origin
    await w.lifecycle('install');
    await w.lifecycle('activate');
    expect([...w.store.keys()].sort()).toEqual(['pro-chart-shell-v1', 'pro-chart-shell:/next/:v1']);
    expect(w.store.get('pro-chart-shell:/next/:v1')!.has(`${w.ORIGIN}/next/`)).toBe(true);
  });
});
