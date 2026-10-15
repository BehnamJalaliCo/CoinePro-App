// Area F — trading from the chart and the TradeYar VIP channel: the order ticket behind
// `pc:order-ticket` (live for a member, paper for a guest, never live for forex), the orders it
// sends, positions / orders / stop and target on the chart with drag and close, the account panel,
// the VIP signals on the chart and in their panel, copy trade, the new-signal toast, and
// VIP ⇒ premium. Same helpers as run.mjs.
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

export default async function ({ test, open, ready, pc, expect }) {
  const TY = '/up/tradeyar/api/mobile/v1/';
  const start = async (path = 'BTCUSDT/1h?e2e=1', opts = {}) => { const env = await open(path, opts); await ready(env.page); return env; };
  const record = (page) => {
    const sent = [];
    page.on('request', (r) => {
      const u = new URL(r.url());
      if (r.method() !== 'GET' && u.pathname.startsWith('/up/')) {
        let body = null;
        try { body = r.postDataJSON(); } catch { body = r.postData(); }
        sent.push({ method: r.method(), path: u.pathname, body });
      }
    });
    return sent;
  };
  const sentTo = async (sent, method, path, timeout = 5000, from = 0) => {
    const t0 = Date.now();
    for (;;) {
      const hit = sent.slice(from).filter((x) => x.method === method && x.path === path);
      if (hit.length) return hit[hit.length - 1];
      if (Date.now() - t0 > timeout) throw new Error(`no ${method} ${path}; sent: ${sent.map((x) => `${x.method} ${x.path}`).join(', ')}`);
      await new Promise((r) => setTimeout(r, 100));
    }
  };
  const live = (page) => page.waitForFunction(() => window.__pc.useTrading.getState().books.tradeyar.status === 'live', null, { timeout: 10000 });
  const offer = (page, detail) => page.evaluate((d) => { const e = new CustomEvent('pc:order-ticket', { cancelable: true, detail: d }); window.dispatchEvent(e); return e.defaultPrevented; }, detail);
  const json = (body, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });
  const lastClose = (page) => pc(page, () => { const b = window.__pc.useChartBus.getState().bars; return b[b.length - 1].c; });
  // Positions and orders around the chart's own price, so their lines are on screen.
  const nearBook = async (page, { order = true } = {}) => {
    const c = await lastClose(page);
    const entry = Number((c * 0.999).toFixed(1));
    const limit = Number((c * 0.996).toFixed(1));
    await page.route('**/up/tradeyar/api/mobile/v1/trade/positions*', (r) => r.fulfill(json({ items: [{ symbol: 'BTCUSDT', side: 'long', quantity: 0.01, entry_price: entry, mark_price: c, unrealized_pnl: 1, leverage: 5, liquidation_price: entry * 0.8 }] })));
    await page.route('**/up/tradeyar/api/mobile/v1/trade/orders?*', (r) => r.fulfill(json({ items: order ? [{ id: 7, symbol: 'BTCUSDT', side: 'buy', type: 'limit', quantity: 0.02, price: limit, status: 'open', can_cancel: true, can_amend: true, protection: 'none' }] : [] })));
    await pc(page, () => window.__pc.useTrading.getState().refresh('tradeyar', 'all'));
    return { c, entry, limit };
  };
  const drag = async (page, selector, dy) => {
    const box = await (await page.waitForSelector(selector, { state: 'visible', timeout: 8000 })).boundingBox();
    const x = box.x + box.width / 2; const y = box.y + box.height / 2;
    await page.mouse.move(x, y);
    await page.mouse.down();
    for (let i = 1; i <= 6; i++) await page.mouse.move(x, y + (dy * i) / 6);
    await page.mouse.up();
  };

  await test('trade: without an LBank key the chart asks the key link, never the account (no TYR-016)', async () => {
    const seen = [];
    const answer = (body) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, mock: (u) => {
      seen.push(u.pathname);
      if (u.pathname === '/up/tradeyar/api/mobile/v1/venues/lbank') return answer({ configured: false, connected: false, status: 'none', permission: '' });
      if (u.pathname === '/up/tradeyar/api/mobile/v1/trade/account') return { status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'TYR-016' }) };
      return null;
    } });
    await ready(env.page);
    await env.page.waitForFunction(() => window.__pc.useTrading.getState().books.tradeyar.status === 'no-keys', null, { timeout: 8000 });
    await env.page.waitForTimeout(1500);
    expect(seen.some((p) => p.endsWith('/venues/lbank')), 'the key link was asked');
    expect(!seen.some((p) => p.endsWith('/trade/account') || p.endsWith('/trade/positions') || p.includes('/trade/orders')), `asked without a key: ${seen.filter((p) => p.includes('/trade/')).join(', ')}`);
    return env;
  });

  await test('trade: a member’s chart order opens the live ticket and sends one confirmed order', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    const sent = record(page);
    await live(page);
    expect(await offer(page, { symbol: 'BTCUSDT', side: 'buy', price: 81000, source: 'legend' }), 'the live listener takes the order');
    await page.waitForSelector('.ot .ot-mode button.live.on');
    expect(!(await page.$('.ot .tr-needs')), 'no requirements for a live member');
    await page.fill('.ot input[aria-label="quantity"]', '0.01');
    await page.click('.ot .ot-levs button >> text=10x');
    await page.click('.ot .ot-level.sl .ot-check input');
    await page.fill('.ot .ot-level.sl input[aria-label="sl"]', '79000');
    expect(/0\.01 BTCUSDT/.test(await page.textContent('.ot .ot-submit')), 'the button names the order');
    await page.click('.ot .ot-submit');
    await page.waitForSelector('.ot .ot-review');
    // First attempt: the exchange does not answer; the retry must carry the same request id.
    let calls = 0;
    await page.route('**/up/tradeyar/api/mobile/v1/trade/orders', (r) => {
      calls++;
      if (calls === 1) return r.fulfill(json({ detail: 'ارتباط با صرافی برقرار نشد. وضعیت سفارش نامشخص است.', code: 'TYR-E' }, 502));
      return r.fulfill(json({ order: { id: 5, symbol: 'BTCUSDT', side: 'buy', type: 'market', quantity: 0.01, status: 'filled', avg_price: 81000 } }));
    });
    await page.click('.ot .ot-actions .ot-submit');
    await page.waitForSelector('.ot .ot-result.err');
    expect((await page.textContent('.ot .ot-error')).includes('ارتباط با صرافی برقرار نشد'), 'the platform’s words, verbatim');
    await page.click('.ot .ot-result.err .ot-submit');
    await page.waitForSelector('.ot .ot-result.ok');
    const orders = sent.filter((x) => x.method === 'POST' && x.path === `${TY}trade/orders`);
    expect(orders.length === 2, `two attempts, got ${orders.length}`);
    const [a, b] = orders.map((x) => x.body);
    expect(a.client_request_id && a.client_request_id === b.client_request_id && a.client_request_id.length >= 8, 'one client_request_id across the retry');
    expect(a.symbol === 'BTCUSDT' && a.side === 'buy' && a.type === 'market' && a.quantity === 0.01 && a.stop_loss === 79000 && a.take_profit === null && a.reduce_only === false && a.price === null, JSON.stringify(a));
    const lev = await sentTo(sent, 'POST', `${TY}trade/leverage`);
    expect(lev.body.symbol === 'BTCUSDT' && lev.body.leverage === 10, JSON.stringify(lev.body));
    return env;
  });

  await test('trade: positions, orders, stop and target on the chart — drag, confirm, cancel, close', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    const sent = record(page);
    await live(page);
    const { entry, limit } = await nearBook(page);
    await page.waitForSelector('.ltl[data-key="pos:long"] .ltl-label', { state: 'visible' });
    expect(/0\.01/.test(await page.textContent('.ltl[data-key="pos:long"] .ltl-tag')), 'size on the position line');
    await page.waitForSelector('.ltl[data-key="ord:7"] .ltl-label', { state: 'visible' });
    // A stop loss pulled out of the position's SL button.
    await drag(page, '.ltl[data-key="pos:long"] .ltl-spawn.sl', 40);
    await page.click('.ltl[data-key="ghost"] .ltl-ok');
    const p1 = await sentTo(sent, 'POST', `${TY}trade/positions/protection`);
    expect(p1.body.symbol === 'BTCUSDT' && p1.body.side === 'long' && p1.body.stop_loss < entry && p1.body.take_profit === null, JSON.stringify(p1.body));
    // The stop line is there now; drag it further and confirm.
    await page.waitForSelector('.ltl[data-key="sl:long"] .ltl-label', { state: 'visible' });
    const before = sent.length;
    await drag(page, '.ltl[data-key="sl:long"] .ltl-label', 25);
    await page.click('.ltl[data-key="sl:long"] .ltl-ok');
    const p2 = await sentTo(sent, 'POST', `${TY}trade/positions/protection`, 5000, before);
    expect(p2.body.stop_loss < p1.body.stop_loss, `moved lower: ${JSON.stringify(p2.body)}`);
    // The working order moves by drag (PATCH) and cancels with ×.
    await drag(page, '.ltl[data-key="ord:7"] .ltl-label', -20);
    await page.click('.ltl[data-key="ord:7"] .ltl-ok');
    const amend = await sentTo(sent, 'PATCH', `${TY}trade/orders/7`);
    expect(amend.body.price > limit, JSON.stringify(amend.body));
    await page.click('.ltl[data-key="ord:7"] .ltl-x');
    await sentTo(sent, 'DELETE', `${TY}trade/orders/7`);
    // Close the position: × asks once more on the line.
    await page.click('.ltl[data-key="pos:long"] .ltl-x');
    await page.click('.ltl[data-key="pos:long"] .ltl-ok');
    const close = await sentTo(sent, 'POST', `${TY}trade/positions/close`);
    expect(close.body.symbol === 'BTCUSDT' && close.body.side === 'long' && close.body.client_request_id && close.body.quantity === null, JSON.stringify(close.body));
    return env;
  });

  await test('trade: account panel — equity, positions with SL/TP, orders, history', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    await live(page);
    await pc(page, () => window.__pc.usePanels.getState().setBottom('account'));
    await page.waitForSelector('.acct .acct-strip');
    expect(/1,050\.00/.test(await page.textContent('.acct .acct-strip')), 'equity in the strip');
    await page.waitForSelector('.acct-table tbody tr');
    expect(/BTCUSDT/.test(await page.textContent('.acct-table')), 'position row');
    await page.click('.acct-tabs button >> nth=1');
    await page.waitForSelector('.acct-table >> text=ETHUSDT');
    await page.click('.acct-tabs button >> nth=2');
    await page.waitForSelector('.acct-table tfoot');
    expect((await page.$$('.acct-table tbody tr')).length === 2, 'two closed trades');
    return env;
  });

  await test('trade: a guest gets the paper ticket with the way to live; the panel keeps its paper order', async () => {
    const env = await start('ETHUSDT/1h?e2e=1');
    const { page } = env;
    expect(!(await offer(page, { symbol: 'ETHUSDT', side: 'buy', quantity: 0.5, source: 'panel' })), 'a guest’s panel order falls back to paper');
    expect(await offer(page, { symbol: 'ETHUSDT', side: 'buy', price: 2400, source: 'legend' }), 'a chart order opens the ticket');
    await page.waitForSelector('.ot .ot-mode button.on >> text=آزمایشی');
    await page.click('.ot .ot-mode button.live');
    await page.waitForSelector('.ot .tr-needs .tr-steps li');
    expect(/ورود به تریدیار/.test(await page.textContent('.ot .tr-needs')), 'sign in to TradeYar is the first step');
    await page.click('.ot .tr-needs .link');
    await page.waitForSelector('.ot .ot-submit');
    await page.click('.ot .ot-submit');
    await page.waitForFunction(() => window.__pc.usePaper.getState().positions.some((p) => p.symbol === 'ETHUSDT' && p.side === 'long'), null, { timeout: 5000 });
    expect(await pc(page, () => window.__pc.usePanels.getState().bottom) === 'paper', 'the paper tab opens');
    expect(!(await page.$('.ot')), 'the ticket closes');
    return env;
  });

  await test('trade: forex is signals only — paper ticket with the note, no live', async () => {
    const env = await start('XAUUSD/4h?e2e=1', { signed: true });
    const { page } = env;
    expect(!(await offer(page, { symbol: 'XAUUSD', side: 'buy', quantity: 0.1, source: 'panel' })), 'the panel keeps forex on paper');
    expect(await offer(page, { symbol: 'XAUUSD', side: 'sell', price: 4140, source: 'context-menu', type: 'limit' }), 'the chart opens the ticket');
    await page.waitForSelector('.ot .ot-note');
    expect(await page.isDisabled('.ot .ot-mode button.live'), 'no live switch for forex');
    expect(/سیگنال/.test(await page.textContent('.ot .ot-note')), 'signal-only note');
    expect(await page.getAttribute('.ot .ot-types button.on', 'aria-selected') === 'true' && /محدود/.test(await page.textContent('.ot .ot-types button.on')), 'limit from the menu');
    return env;
  });

  await test('trade: VIP signal on the chart and in its panel, copy trade confirmed', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    const sent = record(page);
    await page.waitForFunction(() => window.__pc.useVip.getState().member === true, null, { timeout: 10000 });
    await page.waitForSelector('.vip-ov .vip-tag', { state: 'visible', timeout: 10000 });
    expect(/VIP/.test(await page.textContent('.vip-ov .vip-tag')) && /LONG/.test(await page.textContent('.vip-ov .vip-tag')), 'the tag');
    expect(await page.$eval('.vip-ov .vip-zone', (e) => e.style.display !== 'none' && e.offsetHeight > 0), 'the entry zone box');
    expect(await page.$eval('.vip-ov .vip-mark', (e) => e.style.display !== 'none'), 'the posted-at marker');
    await page.click('.ws-wbar .vip-wbtn');
    await page.waitForSelector('.vip-panel .vip-row');
    expect((await page.$$('.vip-panel .vip-row')).length === 2, 'two active signals');
    await page.click('.vip-panel .vip-row >> nth=1 >> .vip-main');
    await page.waitForURL(/ETHUSDT/);
    await page.waitForSelector('.vip-ov .vip-sig.focus', { state: 'attached' });
    await page.click('.vip-panel .panel-chips .chip >> nth=2');
    await page.waitForSelector('.vip-panel .vip-rate');
    await page.click('.vip-panel .panel-chips .chip >> nth=0');
    await page.click('.vip-panel .vip-row >> nth=0 >> .vip-copy');
    await page.waitForSelector('.vip-dialog input[aria-label="quantity"]');
    await page.fill('.vip-dialog input[aria-label="quantity"]', '100');
    await page.click('.vip-dialog .ot-actions .ot-submit');
    await page.waitForSelector('.vip-dialog .vip-confirm');
    await page.click('.vip-dialog .ot-actions .ot-submit');
    const ex = await sentTo(sent, 'POST', `${TY}executions`);
    expect(ex.body.signal_id === 11 && ex.body.venue === 'lbank' && ex.body.confirmed === true && ex.body.quantity > 0 && ex.body.client_request_id, JSON.stringify(ex.body));
    await page.waitForSelector('.vip-panel .vip-exec');
    await page.click('.vip-panel .vip-exec .btn >> text=بستن');
    await page.click('.vip-panel .vip-exec .vip-ask .btn');
    await sentTo(sent, 'POST', `${TY}executions/e1/close`);
    return env;
  });

  await test('trade: a new VIP signal while the app is open — toast and badge', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    await page.waitForFunction(() => window.__pc.useVip.getState().member === true, null, { timeout: 10000 });
    const now = new Date().toISOString();
    await page.route('**/up/tradeyar/api/mobile/v1/signals?status=active*', (r) => r.fulfill(json({ items: [
      { id: 11, symbol: 'BTCUSDT', direction: 'buy', status: 'ACTIVE', timeframe: '1h', entry: 81000, stop_loss: 79000, targets: [{ level: 1, price: 83000, hit: false }], created_at: now },
      { id: 99, symbol: 'SOLUSDT', direction: 'sell', status: 'ACTIVE', timeframe: '15m', entry: 103, stop_loss: 106, targets: [{ level: 1, price: 99, hit: false }], created_at: now },
    ], membership_required: false })));
    await pc(page, () => window.__pc.pollVip());
    await page.waitForSelector('.vip-toast >> text=SOLUSDT', { timeout: 8000 });
    await page.waitForSelector('.vip-wbtn .wbar-badge');
    await page.click('.vip-toast .btn');
    await page.waitForURL(/SOLUSDT\/15m/);
    return env;
  });

  await test('trade: outside the channel — the pitch with TradeYar’s join terms', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    await page.route('**/up/tradeyar/api/mobile/v1/signals?*', (r) => r.fulfill(json({ items: [], membership_required: true, membership_message: 'دیدن سیگنال‌ها نیاز به اشتراک فعال دارد.' })));
    await ready(page);
    await page.click('.ws-wbar .vip-wbtn');
    await page.waitForSelector('.vip-pitch .tr-steps');
    const text = await page.textContent('.vip-pitch');
    expect(/نیاز به اشتراک فعال/.test(text) && /50 USDT/.test(text), 'the server’s message and the minimum deposit');
    expect(await page.getAttribute('.vip-pitch a[href*="lbank"]', 'href') === 'https://lbank.com/ref/e2e', 'the referral link');
    await page.click('.vip-pitch .vip-join');
    await page.waitForFunction(() => ['vip-join', 'membership'].includes(window.__pc.usePage.getState().page));
    expect(!(await page.$('.vip-ov .vip-tag')), 'nothing on the chart for a non-member');
    return env;
  });

  await test('trade: VIP entitlements unlock premium and name it VIP', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    await page.route('**/up/tradeyar/api/mobile/v1/entitlements', (r) => r.fulfill(json({ all: true, premium: true, paywall: false, plan: 'vip', ends_at: null })));
    await ready(page);
    await page.waitForFunction(() => window.__pc.useEntitlements.getState().byVenue.tradeyar?.plan === 'vip', null, { timeout: 8000 });
    await page.click('.account .avatar');
    await page.waitForSelector('.account-menu .plan-badge.vip');
    expect((await page.textContent('.account-menu .premium-item')).includes('VIP'), 'the menu item says VIP');
    await page.click('.account-menu .premium-item');
    await page.waitForSelector('.dialog.premium .vip-card');
    expect((await page.textContent('.dialog.premium h2')).trim() === 'VIP', 'the dialog is VIP');
    expect(!(await page.$('.dialog.premium .plans')), 'nothing to buy');
    return env;
  });

  // Pictures for the eye (TRADE_SHOTS=<dir>): the ticket, the lines, the panels, fa/en, light/dark,
  // desktop and phone.
  const SHOTS = process.env.TRADE_SHOTS;
  if (SHOTS) {
    mkdirSync(SHOTS, { recursive: true });
    for (const [lang, scheme, width, height] of [['fa', 'light', 1440, 900], ['en', 'dark', 1440, 900], ['fa', 'dark', 390, 844], ['en', 'light', 390, 844]]) {
      await test(`trade: pictures ${lang} ${scheme} ${width}`, async () => {
        const env = await open('BTCUSDT/1h?e2e=1', { scheme, signed: true, width, height });
        const { page } = env;
        await page.addInitScript((l) => { try { const p = JSON.parse(localStorage.getItem('pc.prefs.v1') || '{}'); localStorage.setItem('pc.prefs.v1', JSON.stringify({ ...p, lang: l })); } catch { /* ignore */ } }, lang);
        await page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1');
        await ready(page);
        await live(page);
        await nearBook(page);
        await page.waitForSelector('.ltl[data-key="pos:long"] .ltl-label', { state: 'visible' });
        await page.waitForTimeout(1200);
        const shot = (name) => page.screenshot({ path: join(SHOTS, `${lang}-${scheme}-${width}-${name}.png`) });
        await shot('lines');
        await offer(page, { symbol: 'BTCUSDT', side: 'buy', source: 'legend' });
        await page.fill('.ot input[aria-label="quantity"]', '0.01');
        await page.click('.ot .ot-level.tp .ot-check input');
        await page.click('.ot .ot-level.tp .ot-modes button >> nth=2');
        await page.fill('.ot .ot-level.tp input[aria-label="tp"]', '2');
        await shot('ticket');
        await page.click('.ot .ot-submit');
        await shot('confirm');
        await page.click('.ot .ot-head .lt');
        await page.click('.ws-wbar .vip-wbtn');
        await page.waitForSelector('.vip-panel .vip-row');
        await shot('vip');
        await page.click('.ws-wbar .vip-wbtn');
        await pc(page, () => window.__pc.usePanels.getState().setBottom('account'));
        await page.waitForSelector('.acct-table tbody tr');
        await shot('account');
        return env;
      });
    }
  }
}
