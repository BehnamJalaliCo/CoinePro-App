// End-to-end suite for the built web app (dist/), in a real Chromium.
//
//   node scripts/e2e/run.mjs            backends mocked from fixtures (deterministic, offline)
//   E2E_UPSTREAM=https://pro-chart.com node scripts/e2e/run.mjs   the live relay
//
// The page is served from dist at http://pc.local/next/…; /up/* and /api/* are answered by the
// fixtures or fetched from the upstream through the browser context. Every test fails on any
// uncaught page error. Playwright is a dev dependency; CI installs its Chromium first.
import { createRequire } from 'node:module';
import { existsSync, readFileSync, statSync, mkdirSync } from 'node:fs';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const here = dirname(fileURLToPath(import.meta.url));
const DIST = join(here, '../../dist');
const SHOTS = process.env.E2E_SHOTS ?? join(here, '../../.e2e-shots');
const UPSTREAM = process.env.E2E_UPSTREAM ?? '';
const ONLY = process.env.E2E_ONLY ?? '';
const { chromium } = require(process.env.PLAYWRIGHT_MODULE ?? 'playwright');

if (!existsSync(join(DIST, 'index.html'))) { console.error('dist/ is missing: run npm run build first'); process.exit(2); }
mkdirSync(SHOTS, { recursive: true });

// ── Fixtures ──────────────────────────────────────────────────────────────────────────────────────
const NOW = Math.floor(Date.now() / 1000);
function series(seed, base, step, n) {
  let p = base, x = seed;
  const rnd = () => { x = (x * 9301 + 49297) % 233280; return x / 233280; };
  const out = [];
  const start = Math.floor(NOW / step) * step - (n - 1) * step;
  for (let i = 0; i < n; i++) {
    const o = p; const c = o * (1 + (rnd() - 0.5) * 0.02);
    out.push({ t: start + i * step, o, h: Math.max(o, c) * (1 + rnd() * 0.005), l: Math.min(o, c) * (1 - rnd() * 0.005), c, v: 100 + rnd() * 900, closed: i < n - 1 });
    p = c;
  }
  return out;
}
const TF = { M1: 60, M5: 300, M15: 900, M30: 1800, H1: 3600, H4: 14400, D1: 86400, W1: 604800 };
const tfOf = (q) => ({ '1m': 'M1', '5m': 'M5', '15m': 'M15', '30m': 'M30', '1h': 'H1', '4h': 'H4', '1d': 'D1', '1w': 'W1' }[q?.toLowerCase()] ?? q?.toUpperCase());
const CRYPTO = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT', 'TRXUSDT', 'LTCUSDT', 'SUIUSDT'];
const PRICE = { BTCUSDT: 81000, ETHUSDT: 2400, SOLUSDT: 103, BNBUSDT: 720, XRPUSDT: 1.31, ADAUSDT: 0.22, DOGEUSDT: 0.08, TRXUSDT: 0.3, LTCUSDT: 90, SUIUSDT: 1.0, XAUUSD: 4140, XAGUSD: 56, EURUSD: 1.12, GBPUSD: 1.32, USDJPY: 157.7, AUDUSD: 0.695, USDCHF: 0.83 };
const FX = ['XAUUSD', 'XAGUSD', 'EURUSD', 'GBPUSD', 'USDJPY', 'AUDUSD', 'USDCHF'];
const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });

function mock(url) {
  const u = new URL(url);
  const p = u.pathname;
  const q = u.searchParams;
  if (p === '/api/crypto/prices') return json({ ts: Date.now(), data: CRYPTO.map((s) => ({ s, p: PRICE[s] * (1 + (Math.random() - 0.5) * 0.001), t: NOW, c: -2.1, h: PRICE[s] * 1.03, l: PRICE[s] * 0.97, v: 123456 })) });
  if (p === '/api/crypto/candles') { const tf = tfOf(q.get('tf')); return json({ symbol: q.get('symbol'), tf, candles: series(q.get('symbol').length * 7, PRICE[q.get('symbol')] ?? 10, TF[tf] ?? 3600, Math.min(500, Number(q.get('limit') ?? 500))) }); }
  if (p === '/up/coineprofx/api/user/auth/guest') return json({ token: 'pch_e2eguest', expires_in: 7200, scope: 'guest' });
  const chart = /^\/up\/coineprofx\/api\/academy\/chart\/([A-Z0-9]+)$/.exec(p);
  if (chart) { const tf = q.get('tf'); return json({ symbol: chart[1], timeframe: tf, candles: series(chart[1].length * 11, PRICE[chart[1]] ?? 1, TF[tf] ?? 3600, Math.min(1500, Number(q.get('limit') ?? 1500))).map(({ closed, ...b }) => b) }); }
  if (p === '/up/coineprofx/api/ws/snapshot') return json({ prices: Object.fromEntries((q.get('symbols') ?? '').split(',').filter(Boolean).map((s) => [s, { symbol: s, price: PRICE[s] ?? 1, ts: Date.now() }])) });
  if (p === '/up/coineprofx/api/user/mobile/market/tickers') return json({ tickers: FX.map((s) => ({ symbol: s, last: PRICE[s], open_24h: PRICE[s] * 1.004, high_24h: PRICE[s] * 1.01, low_24h: PRICE[s] * 0.99, change_percent_24h: -0.4, volume_24h: 1000, ts: Date.now() })), cache_ttl_ms: 5000 });
  if (p === '/api/news') return json({ data: [{ id: 1, slug: 'e2e-story', source: 'e2e', titleFa: 'خبر آزمایشی بیت‌کوین', summaryFa: 'خلاصه‌ی آزمایشی', publishedAt: new Date().toISOString(), importance: 8 }] });
  if (p === '/up/tradeyar/api/v1/news/e2e-story') return json({ data: { bodyFa: 'متن کامل خبر آزمایشی.' } });
  if (p === '/up/coineprofx/api/academy/bn/news') return json({ items: [{ id: '9', title: 'خبر آزمایشی طلا', summary: 'خلاصه', published_at: new Date().toISOString(), source: 'e2e' }] });
  if (p === '/up/tradeyar/api/v1/public/calendar/week') return json([{ title: 'CPI m/m', country: 'USD', date: new Date(Date.now() + 3600e3).toISOString(), impact: 'High', forecast: '0.3%', previous: '0.2%' }]);
  if (p === '/api/track-record') return json({ signals: [{ id: 1, symbol: 'BTCUSDT', timeframe: '1h', direction: 'LONG', entry_price: 80000, sl_price: 79000, tp1_price: 82000, is_win: true, pct_gain: 5, risk_reward: 2, created_at: new Date(Date.now() - 86400e3).toISOString(), closed_at: new Date().toISOString() }] });
  if (p === '/up/tradeyar/api/mobile/v1/auth/methods') return json({ email_password: true, google: true, google_client_id: 'e2e.apps.googleusercontent.com' });
  if (p === '/up/tradeyar/api/mobile/v1/auth/login') return json({ detail: 'ایمیل یا رمز عبور درست نیست.' }, 401);
  if (p === '/up/coineprofx/api/user/auth/login') return json({ detail: { code: 'bad', message: 'ایمیل یا رمز عبور درست نیست.' } }, 401);
  if (p === '/up/tradeyar/api/mobile/v1/payments/plans') return json({ plans: [{ id: 'monthly', days: 30, price_toman: 1, price_usdt: '15' }], usdt: { network: 'BEP20', wallet: '0x0000000000000000000000000000000000000000' } });
  return json({ detail: 'not in fixtures' }, 404);
}

// ── Harness ───────────────────────────────────────────────────────────────────────────────────────
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf', '.map': 'application/json', '.txt': 'text/plain' };
const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'], ...(UPSTREAM && process.env.HTTPS_PROXY ? { proxy: { server: process.env.HTTPS_PROXY } } : {}) });

async function open(path, { width = 1440, height = 900, scheme = 'light', locale = 'fa-IR' } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height }, colorScheme: scheme, locale, timezoneId: 'Asia/Tehran' });
  const net = { calls: 0, failed: [] };
  await ctx.route('http://pc.local/**', async (r) => {
    const u = new URL(r.request().url());
    if (u.pathname.startsWith('/up/') || u.pathname.startsWith('/api/')) {
      net.calls++;
      if (UPSTREAM) {
        try { const resp = await r.fetch({ url: UPSTREAM + u.pathname + u.search }); if (resp.status() >= 500) net.failed.push(`${resp.status()} ${u.pathname}`); return r.fulfill({ response: resp }); }
        catch { net.failed.push(`ERR ${u.pathname}`); return r.abort(); }
      }
      return r.fulfill(mock(u.href));
    }
    const rel = u.pathname.replace(/^\/next\//, '');
    let file = join(DIST, rel);
    if (!rel || !existsSync(file) || statSync(file).isDirectory()) file = join(DIST, 'index.html');
    return r.fulfill({ status: 200, body: readFileSync(file), headers: { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' } });
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message} @ ${(e.stack ?? '').split('\n').slice(1, 3).join(' ').slice(0, 300)}`));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(`console: ${m.text().slice(0, 200)}`); });
  const t0 = Date.now();
  await page.goto(`http://pc.local/next/${path}`, { waitUntil: 'domcontentloaded' });
  return { ctx, page, errors, net, t0 };
}

const results = [];
async function test(name, fn) {
  if (ONLY && !name.includes(ONLY)) return;
  const t = Date.now();
  let env;
  try {
    env = await fn();
    if (env?.errors?.length) throw new Error(env.errors.slice(0, 3).join(' | '));
    results.push({ name, ok: true, ms: Date.now() - t });
    console.log(`  ✓ ${name} (${Date.now() - t} ms)`);
  } catch (e) {
    results.push({ name, ok: false, ms: Date.now() - t, error: String(e.message ?? e).slice(0, 400) });
    console.log(`  ✗ ${name}\n      ${String(e.message ?? e).slice(0, 400)}`);
    if (env?.page) await env.page.screenshot({ path: join(SHOTS, `${name.replace(/\W+/g, '_')}.png`) }).catch(() => {});
  } finally {
    await env?.ctx?.close().catch(() => {});
  }
}
const expect = (cond, msg) => { if (!cond) throw new Error(msg); };
const ready = async (page) => {
  await page.waitForSelector('.chart-host canvas', { timeout: 20000 });
  await page.waitForFunction(() => !document.querySelector('.chart-state'), null, { timeout: 25000 });
};
const pc = (page, fn, arg) => page.evaluate(fn, arg);

console.log(`e2e against ${UPSTREAM || 'fixtures'}`);

await test('first paint and live chart (BTCUSDT 1h)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const ms = Date.now() - env.t0;
  expect(ms < 15000, `chart took ${ms} ms`);
  const legend = await env.page.textContent('.legend-ohlc');
  expect(/O.*H.*L.*C/.test(legend ?? ''), 'legend OHLC missing');
  expect(!/[۰-۹]/.test(legend ?? ''), 'legend figures must be Latin');
  await env.page.waitForFunction(() => /BTCUSDT [\d,.]+/.test(document.title), null, { timeout: 15000 });
  return env;
});

await test('forex chart (XAUUSD 4h) with guest token', async () => {
  const env = await open('XAUUSD/4h?e2e=1');
  await ready(env.page);
  const title = await env.page.textContent('.legend-title');
  expect(title && title.length > 0, 'no display name');
  return env;
});

await test('watchlist click, interval buttons and the address', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.wl-row >> text=ETHUSDT');
  await env.page.waitForURL(/\/next\/ETHUSDT\/1h$/);
  await env.page.click('.ws-top .iv >> text=4h');
  await env.page.waitForURL(/\/next\/ETHUSDT\/4h$/);
  await ready(env.page);
  return env;
});

await test('symbol search by typing on the chart', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.keyboard.type('sol');
  await env.page.waitForSelector('.search-row');
  await env.page.keyboard.press('Enter');
  await env.page.waitForURL(/SOLUSDT/);
  return env;
});

await test('every chart type renders', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const types = await pc(env.page, () => window.__pc.chartTypes);
  for (const t of types) {
    await pc(env.page, (x) => window.__pc.usePrefs.getState().setChartType(x), t);
    await env.page.waitForTimeout(250);
  }
  await pc(env.page, () => window.__pc.usePrefs.getState().setChartType('candles'));
  expect(types.length === 16, `expected 16 chart types, got ${types.length}`);
  return env;
});

await test('all 108 indicators add, compute and remove', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  const keys = await pc(env.page, () => window.__pc.indicators);
  expect(keys.length === 108, `expected 108 indicators, got ${keys.length}`);
  for (let i = 0; i < keys.length; i += 6) {
    const batch = keys.slice(i, i + 6);
    await pc(env.page, (ks) => { const s = window.__pc.useStudies.getState(); for (const k of ks) s.add(k); }, batch);
    await env.page.waitForTimeout(350);
    const rows = await env.page.$$eval('.legend-study', (els) => els.length);
    expect(rows >= batch.length, `legend rows ${rows} for ${batch.join(',')}`);
    await pc(env.page, () => window.__pc.useStudies.getState().clear());
  }
  return env;
});

await test('indicator dialog: search, add RSI and MACD in their own panes', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.keyboard.press('/');
  await env.page.waitForSelector('.dialog.indicators');
  await env.page.keyboard.type('RSI');
  await env.page.waitForTimeout(200);
  const first = await env.page.textContent('.ind-row.on .ind-name');
  expect(first === 'Relative Strength Index', `RSI search ranked "${first}" first`);
  await env.page.keyboard.press('Enter');
  await env.page.fill('.dialog.indicators input', 'MACD');
  await env.page.keyboard.press('Enter');
  await env.page.keyboard.press('Escape');
  await env.page.waitForTimeout(600);
  const panes = await env.page.$$eval('.pane-legend', (els) => els.length);
  expect(panes === 2, `expected 2 pane legends, got ${panes}`);
  return env;
});

await test('every drawing tool draws', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  env.page.on('dialog', (d) => { d.accept('e2e').catch(() => {}); });
  const tools = await pc(env.page, () => window.__pc.tools.filter((t) => !['cursor', 'select'].includes(t.id)));
  const box = await env.page.locator('.chart-host').boundingBox();
  const at = (fx, fy) => [box.x + box.width * fx, box.y + box.height * fy];
  let drawn = 0;
  const missed = [];
  for (const t of tools) {
    await pc(env.page, (x) => window.__pc.useDrawing.getState().setTool(x.id, x.group), t);
    const before = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
    if (['brush', 'highlighter'].includes(t.id)) {
      const [x0, y0] = at(0.3, 0.4); const [x1, y1] = at(0.5, 0.5);
      await env.page.mouse.move(x0, y0); await env.page.mouse.down(); await env.page.mouse.move(x1, y1, { steps: 8 }); await env.page.mouse.up();
    } else if (['polyline', 'path'].includes(t.id)) {
      for (const [x, y] of [at(0.3, 0.5), at(0.45, 0.35), at(0.6, 0.55)]) await env.page.mouse.click(x, y);
      await env.page.keyboard.press('Enter');
    } else {
      const pts = [at(0.2, 0.6), at(0.3, 0.35), at(0.4, 0.55), at(0.5, 0.3), at(0.6, 0.5), at(0.7, 0.4), at(0.8, 0.45), at(0.85, 0.3)];
      for (const [x, y] of pts) {
        await env.page.mouse.click(x, y);
        const n = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
        if (n > before) break;
      }
    }
    await env.page.waitForTimeout(80);
    const after = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
    if (after > before) drawn++; else missed.push(t.id);
    await env.page.keyboard.press('Escape');
    await pc(env.page, () => window.__pc.useDrawing.getState().setTool('cursor', 'cursors'));
  }
  expect(drawn === tools.length, `only ${drawn} of ${tools.length} tools drew; missed: ${missed.join(', ')}`);
  // Undo/redo and persistence per symbol.
  const n = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
  await env.page.keyboard.press('Control+z');
  const m = await pc(env.page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length);
  expect(m === n - 1, 'undo did not remove one drawing');
  const saved = await env.page.evaluate(() => JSON.parse(localStorage.getItem('pc.drawings.v1.BTCUSDT') ?? '[]').length);
  expect(saved === m, `saved ${saved} drawings, layer has ${m}`);
  console.log(`      ${drawn}/${tools.length} tools drew`);
  return env;
});

await test('every NamaScript example runs', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const codes = await pc(env.page, () => window.__pc.exampleCode);
  const failures = [];
  for (let i = 0; i < codes.length; i++) {
    await pc(env.page, (c) => { const s = window.__pc.useScript.getState(); s.setSource(c); s.apply(); }, codes[i]);
    await env.page.waitForFunction(() => { const s = window.__pc.useScript.getState(); return !s.running && s.result; }, null, { timeout: 15000 });
    const r = await pc(env.page, () => { const s = window.__pc.useScript.getState().result; return { ok: s.ok, error: s.error }; });
    if (!r.ok) failures.push(`#${i}: ${r.error}`);
    await pc(env.page, () => window.__pc.useScript.getState().remove());
  }
  expect(!failures.length, `examples failed: ${failures.join(' ; ')}`);
  console.log(`      ${codes.length} examples ran`);
  return env;
});

await test('backtest shows the strategy results', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const idx = await pc(env.page, () => window.__pc.exampleCode.findIndex((c) => c.includes('strategy.entry')));
  await pc(env.page, (i) => { const s = window.__pc.useScript.getState(); s.setSource(window.__pc.exampleCode[i]); s.apply(); window.__pc.usePanels.getState().setBottom('backtest'); }, idx);
  await env.page.waitForSelector('.bt-stats', { timeout: 15000 });
  return env;
});

await test('replay: select, step, play, exit', async () => {
  const env = await open('BTCUSDT/15m?e2e=1');
  await ready(env.page);
  await env.page.click('[title="بازپخش"]');
  const box = await env.page.locator('.chart-host').boundingBox();
  await env.page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.5);
  await env.page.waitForSelector('.replay-bar:not(.selecting)');
  const i0 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  await env.page.keyboard.press('Shift+ArrowRight');
  const i1 = await pc(env.page, () => window.__pc.useReplay.getState().state.idx);
  expect(i1 === i0 + 1, 'replay step');
  await env.page.click('.replay-bar [title="exit"]');
  expect(!(await pc(env.page, () => window.__pc.useReplay.getState().state?.on)), 'replay did not exit');
  return env;
});

await test('alert from the chart context menu', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const box = await env.page.locator('.chart-host').boundingBox();
  await env.page.mouse.click(box.x + box.width * 0.5, box.y + box.height * 0.3, { button: 'right' });
  await env.page.click('.ctx >> text=هشدار در این قیمت');
  await env.page.click('.dialog .btn-primary');
  const n = await pc(env.page, () => window.__pc.useAlerts.getState().list.length);
  expect(n === 1, 'alert not created');
  return env;
});

await test('every side panel opens', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const id of ['watchlist', 'alerts', 'signals', 'ai', 'objects', 'data', 'news', 'calendar', 'screener']) {
    await pc(env.page, (x) => { const p = window.__pc.usePanels.getState(); if (p.open !== x) p.toggle(x); }, id);
    await env.page.waitForTimeout(600);
    expect(await env.page.$('.ws-panel:not([hidden])'), `panel ${id} not shown`);
  }
  await pc(env.page, () => window.__pc.usePanels.getState().toggle('news'));
  await env.page.waitForSelector('.news-row', { timeout: 10000 });
  await env.page.click('.news-row');
  await env.page.waitForSelector('.article', { timeout: 10000 });
  return env;
});

await test('every bottom tab opens', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const tab of ['screener', 'namascript', 'backtest', 'paper']) {
    await pc(env.page, (x) => { const p = window.__pc.usePanels.getState(); if (p.bottom !== x) p.setBottom(x); }, tab);
    await env.page.waitForTimeout(400);
    expect(await env.page.$('.ws-bottom-body'), `bottom ${tab}`);
  }
  return env;
});

await test('paper trading opens and closes a position', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.usePanels.getState().setBottom('paper'));
  await env.page.waitForSelector('.paper-ticket .buy');
  await env.page.waitForFunction(() => !document.querySelector('.paper-ticket .buy').disabled, null, { timeout: 10000 });
  await env.page.click('.paper-ticket .buy');
  await env.page.waitForSelector('.paper-lists .bt-row:not(.bt-head) .lt');
  await env.page.click('.paper-lists .bt-row:not(.bt-head) .lt');
  return env;
});

await test('multi-chart layouts 2/3/4', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  for (const g of ['2v', '2h', '3', '4']) {
    await pc(env.page, (x) => window.__pc.useGrid.getState().setGrid(x), g);
    await env.page.waitForTimeout(1500);
    const n = await env.page.$$eval('.lite', (els) => els.length);
    expect(n === ({ '2v': 1, '2h': 1, '3': 2, '4': 3 })[g], `grid ${g}: ${n} lite charts`);
  }
  await pc(env.page, () => window.__pc.useGrid.getState().setGrid('1'));
  return env;
});

await test('settings, theme and language', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => { const p = window.__pc.usePrefs.getState(); p.setTheme('dark'); });
  await env.page.waitForTimeout(300);
  const bg = await env.page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(bg === 'rgb(15, 15, 15)', `dark background ${bg}`);
  await pc(env.page, () => window.__pc.usePrefs.getState().setLang('en'));
  await env.page.waitForTimeout(300);
  expect((await env.page.textContent('.ws-top')).includes('Indicators'), 'English top bar');
  await env.page.screenshot({ path: join(SHOTS, 'dark-en.png') });
  return env;
});

await test('sign-in dialog shows the backend error', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.avatar');
  await env.page.click('.account-menu .menu-item >> text=ورود');
  await env.page.fill('input[type=email]', 'nobody@example.com');
  await env.page.fill('input[type=password]', 'wrong-password');
  await env.page.keyboard.press('Enter');
  await env.page.waitForSelector('.dialog.auth .error', { timeout: 15000 });
  return env;
});

await test('premium dialog lists plans', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  await env.page.click('.cta');
  await env.page.waitForSelector('.plan', { timeout: 10000 });
  return env;
});

await test('signals track record opens a setup on the chart', async () => {
  const env = await open('ETHUSDT/1h?e2e=1');
  await ready(env.page);
  await pc(env.page, () => window.__pc.usePanels.getState().toggle('signals'));
  await env.page.waitForSelector('.sig-row', { timeout: 15000 });
  await env.page.click('.sig-row');
  await env.page.waitForTimeout(2500);
  return env;
});

await test('phone layout (390×844)', async () => {
  const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
  await ready(env.page);
  expect(!(await env.page.isVisible('.ws-panel:not([hidden])')), 'a panel covers the chart on a phone');
  const overflow = await env.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(!overflow, 'horizontal overflow on a phone');
  await env.page.click('.phone-only');
  await env.page.waitForTimeout(300);
  expect(await env.page.evaluate(() => document.body.classList.contains('rail-open')), 'rail did not open');
  await env.page.screenshot({ path: join(SHOTS, 'phone.png') });
  return env;
});

await test('tablet layout (820×1180)', async () => {
  const env = await open('XAUUSD/1h?e2e=1', { width: 820, height: 1180 });
  await ready(env.page);
  const overflow = await env.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(!overflow, 'horizontal overflow on a tablet');
  await env.page.screenshot({ path: join(SHOTS, 'tablet.png') });
  return env;
});

await test('stress: 60 symbol and interval switches', async () => {
  const env = await open('BTCUSDT/1h?e2e=1');
  await ready(env.page);
  const syms = ['BTCUSDT', 'ETHUSDT', 'XAUUSD', 'EURUSD', 'SOLUSDT', 'GBPUSD'];
  const ivs = ['5', '15', '60', '240', '1D'];
  for (let i = 0; i < 60; i++) {
    await pc(env.page, ([s, iv]) => { const p = window.__pc.usePrefs.getState(); p.setSymbol(s); p.setInterval(iv); }, [syms[i % syms.length], ivs[i % ivs.length]]);
    await env.page.waitForTimeout(40);
  }
  await ready(env.page);
  const heap = await env.page.evaluate(() => performance.memory?.usedJSHeapSize ?? 0);
  expect(heap === 0 || heap < 400e6, `heap ${Math.round(heap / 1e6)} MB`);
  console.log(`      heap ${Math.round(heap / 1e6)} MB`);
  return env;
});

await browser.close();
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
