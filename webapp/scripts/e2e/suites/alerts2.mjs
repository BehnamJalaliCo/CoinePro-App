// Alerts, wave 2 (area L): webhooks (an address refused under the field, a signed test send, the
// delivery log, a fired alert posted, a no-CORS endpoint), an alert's own history, up to five
// conditions, «until I see it» and «once a day», the 24-hour change, a NamaScript alertcondition
// made into an alert, and the signal page's high-impact event window.
import { createHmac } from 'node:crypto';

export default async function ({ test, open, ready, pc, expect }) {
  const json = (body, status = 200, headers = {}) => ({ status, contentType: 'application/json', body: JSON.stringify(body), headers });
  const CORS = { 'access-control-allow-origin': '*', 'access-control-allow-methods': 'POST', 'access-control-allow-headers': 'content-type, x-coinepro-signature, x-coinepro-timestamp' };
  /** A fake receiver at https://<host>.e2e-hooks.com: records what arrives, answers `status`, with or without CORS. */
  const receiver = (host, { status = 200, cors = true } = {}) => {
    const got = [];
    const setup = async (ctx) => {
      await ctx.route(`https://${host}.e2e-hooks.com/**`, async (r) => {
        const q = r.request();
        if (q.method() === 'OPTIONS') { got.push({ method: 'OPTIONS', headers: q.headers() }); return r.fulfill({ status: 204, headers: cors ? CORS : {} }); }
        got.push({ method: q.method(), headers: q.headers(), body: q.postData() ?? '' });
        // Playwright does not enforce CORS on a fulfilled response, so a receiver without CORS is
        // played as the page sees one: a CORS-mode request fails, a no-cors one goes through.
        if (!cors && (q.headers()['sec-fetch-mode'] === 'cors' || 'x-coinepro-timestamp' in q.headers())) return r.abort('failed');
        return r.fulfill({ status, contentType: 'text/plain', body: 'ok', headers: cors ? CORS : {} });
      });
    };
    return { got, setup, url: `https://${host}.e2e-hooks.com/in` };
  };
  // A refusal by CORS is reported by the browser on the console; that is the case under test.
  const dropCors = (env) => { env.errors.splice(0, env.errors.length, ...env.errors.filter((e) => !/CORS|Access-Control|access control/i.test(e))); };
  const tick = (page, symbol, last, changePct = 0) => pc(page, ([s, p, c]) => window.__pc.useMarket.getState().put([{ symbol: s, last: p, change: 0, changePct: c, ts: Date.now() }]), [symbol, last, changePct]);
  const showAlerts = (page) => pc(page, () => window.__pc.usePanels.getState().show('alerts'));
  const openWebhooks = async (page) => {
    await showAlerts(page);
    await page.click('.alerts-tools button[title="بیشتر"]');
    await page.click('.wl-more [data-act="webhooks"]');
    await page.waitForSelector('.webhook-dlg');
  };

  await test('alerts2: webhooks — refused address under the field, signed test send, saved, a fired alert posted and logged', async () => {
    const hook = receiver('bot');
    const env = await open('BTCUSDT/1h?e2e=1', { setup: hook.setup });
    const { page } = env;
    await ready(page);
    await openWebhooks(page);
    expect((await page.textContent('.webhook-dlg')).includes('هنوز وب‌هوکی نساخته‌اید'), 'empty state');
    await page.click('.webhook-dlg [data-act="new"]');
    await page.fill('[data-field="name"]', 'ربات آزمایشی');
    await page.fill('[data-field="url"]', 'http://bot.e2e-hooks.com/in');
    expect((await page.textContent('[data-url-hint]')).includes('نشانی باید با https شروع شود'), 'http refused under the field');
    expect(await page.$('.webhook-dlg [data-act="test"]') === null, 'test offered for a refused address');
    expect(await page.$eval('.webhook-dlg [data-act="save"]', (b) => b.disabled), 'save allowed for a refused address');
    await page.fill('[data-field="url"]', 'https://192.168.1.4/in');
    expect((await page.textContent('[data-url-hint]')).includes('نام دامنه'), 'numeric address refused');
    await page.fill('[data-field="url"]', 'https://bot.e2e-hooks.com:8443/in');
    expect((await page.textContent('[data-url-hint]')).includes('۸۰ و ۴۴۳'), 'port refused');
    await page.fill('[data-field="url"]', hook.url);
    expect(await page.getAttribute('[data-field="secret"]', 'type') === 'password', 'the key field is not a password field');
    await page.fill('[data-field="secret"]', 'e2e-signing-key-123');
    await page.click('.webhook-dlg [data-act="test"]');
    await page.waitForSelector('[data-test-result]', { timeout: 8000 });
    expect(await page.getAttribute('[data-test-result]', 'data-test-result') === 'delivered', `test result ${await page.textContent('[data-test-result]')} ${JSON.stringify(hook.got.map((g) => [g.method, g.headers['content-type']]))} ${env.errors.join(' | ')}`);
    expect(hook.got.length === 1, `test sends ${hook.got.length}`);
    const sent = hook.got[0];
    const body = JSON.parse(sent.body);
    expect(body.alertId === 'webhook-test' && body.symbol === 'BTCUSDT' && body.timeframe === 'H1' && body.firedAt > 0, `test body ${sent.body}`);
    const sig = 'sha256=' + createHmac('sha256', 'e2e-signing-key-123').update(sent.body).digest('hex');
    expect(sent.headers['x-coinepro-signature'] === sig, `signature ${sent.headers['x-coinepro-signature']} != ${sig}`);
    expect(sent.headers['content-type'].startsWith('application/json'), `content type ${sent.headers['content-type']}`);
    expect(!JSON.stringify(sent).includes('e2e-signing-key-123'), 'the key travelled');
    await page.click('.webhook-dlg [data-act="save"]');
    await page.waitForSelector('.wh-row[data-webhook="ربات آزمایشی"]');
    // The key: only in its own non-pc key, never in anything exported or synced, never on screen.
    const store = await page.evaluate(() => Object.fromEntries(Object.keys(localStorage).map((k) => [k, localStorage.getItem(k)])));
    const leaks = Object.entries(store).filter(([k, v]) => k.startsWith('pc.') && v.includes('e2e-signing-key-123')).map(([k]) => k);
    expect(!leaks.length, `key inside ${leaks}`);
    expect((store['coinepro.webhook.keys.v1'] ?? '').includes('e2e-signing-key-123'), 'key not kept');
    await page.click('.wh-row .wh-main');
    expect(await page.inputValue('[data-field="secret"]') === '', 'the stored key was loaded into the field');
    expect(!(await page.textContent('.webhook-dlg')).includes('e2e-signing-key-123'), 'the key is on screen');
    await page.click('.webhook-dlg [data-act="back"]');
    expect((await page.textContent('.webhook-dlg [data-log]')).includes('تحویل شد'), 'delivery log');
    await page.keyboard.press('Escape');
    // A fired alert is posted to it.
    await pc(page, () => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 1000, message: '{"side":"buy","t":"{{ticker}}"}' }));
    await tick(page, 'BTCUSDT', 999);
    await tick(page, 'BTCUSDT', 1001);
    for (let i = 0; i < 40 && hook.got.length < 2; i++) await page.waitForTimeout(100);
    expect(hook.got.length === 2, `fired posts ${hook.got.length}`);
    expect(hook.got[1].body === '{"side":"buy","t":"BTCUSDT"}' && hook.got[1].headers['content-type'].startsWith('application/json'), `fired body ${hook.got[1].body}`);
    expect(hook.got[1].headers['x-coinepro-signature'] === 'sha256=' + createHmac('sha256', 'e2e-signing-key-123').update(hook.got[1].body).digest('hex'), 'fired signature');
    return env;
  });

  await test('alerts2: a webhook without CORS is still posted, as plain text without the signature, and the log says the answer is unreadable', async () => {
    const hook = receiver('nocors', { cors: false });
    const env = await open('BTCUSDT/1h?e2e=1', { setup: hook.setup });
    const { page } = env;
    await ready(page);
    await openWebhooks(page);
    await page.click('.webhook-dlg [data-act="new"]');
    await page.fill('[data-field="name"]', 'Sheet');
    await page.fill('[data-field="url"]', hook.url);
    await page.fill('[data-field="secret"]', 'k-nocors');
    await page.click('.webhook-dlg [data-act="test"]');
    await page.waitForSelector('[data-test-result]', { timeout: 10000 });
    const outcome = await page.getAttribute('[data-test-result]', 'data-test-result');
    expect(outcome === 'opaque', `outcome ${outcome}`);
    expect((await page.textContent('[data-test-result]')).includes('پاسخ خوانده نشد'), 'the log claims more than it knows');
    const posts = hook.got.filter((g) => g.method === 'POST');
    const plain = posts.at(-1);
    expect(plain && plain.headers['content-type'].startsWith('text/plain') && !plain.headers['x-coinepro-signature'], `no-cors post ${JSON.stringify(plain?.headers)}`);
    // Learned once saved: a firing leaves once.
    await page.click('.webhook-dlg [data-act="save"]');
    await page.keyboard.press('Escape');
    const before = hook.got.filter((g) => g.method === 'POST').length;
    await pc(page, () => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 1000 }));
    await tick(page, 'BTCUSDT', 999);
    await tick(page, 'BTCUSDT', 1001);
    for (let i = 0; i < 40 && hook.got.filter((g) => g.method === 'POST').length <= before; i++) await page.waitForTimeout(100);
    await page.waitForTimeout(300);
    const after = hook.got.filter((g) => g.method === 'POST').length;
    expect(after === before + 1, `a firing posted ${after - before} times`);
    dropCors(env);
    return env;
  });

  await test('alerts2: an alert’s own history — made, edited, fired, delivered, and a webhook that refused, loudest', async () => {
    const hook = receiver('down', { status: 500 });
    const env = await open('BTCUSDT/1h?e2e=1', {
      setup: async (ctx) => {
        await hook.setup(ctx);
        await ctx.addInitScript((u) => { try { if (!localStorage.getItem('pc.webhooks.v1')) localStorage.setItem('pc.webhooks.v1', JSON.stringify([{ id: 'w1', name: 'Desk', url: u, enabled: true, createdAt: 1 }])); } catch { /* ignore */ } }, hook.url);
      },
    });
    const { page } = env;
    await ready(page);
    // Far above the fixtures' price, so only these ticks can fire it.
    const id = await pc(page, () => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 500000, freq: 'every', cooldownMs: 0 }));
    await pc(page, (i) => window.__pc.useAlerts.getState().update(i, { value: 500000 }), id);
    await pc(page, (i) => window.__pc.useAlerts.getState().update(i, { value: 500001, freq: 'per_minute' }), id);
    await tick(page, 'BTCUSDT', 499999);
    await tick(page, 'BTCUSDT', 500002);
    for (let i = 0; i < 40 && !hook.got.length; i++) await page.waitForTimeout(100);
    await page.waitForTimeout(300);
    await showAlerts(page);
    await page.click(`.alert-row [data-act="history"]`);
    await page.waitForSelector('.audit-dlg');
    const events = await page.$$eval('.audit-dlg [data-event]', (els) => els.map((e) => e.getAttribute('data-event')));
    expect(JSON.stringify(events) === JSON.stringify(['created', 'edited', 'fired', 'delivered', 'delivery_failed']), `history ${events}`);
    const text = await page.textContent('.audit-dlg');
    expect(text.includes('کارنامه‌ی هشدار') && text.includes('اعلان رسیده یا نرسیده'), 'title and lead');
    expect(text.includes('شرط و تکرار تغییر کرد'), 'what the edit changed');
    expect(text.includes('ارسال به وب‌هوک‌ها (۱ مورد)'), 'deliveries heading with a Persian count');
    const failed = await page.textContent('.audit-dlg .ev-delivery_failed');
    expect(failed.includes('اعلان نرسید') && failed.includes('Desk') && failed.includes('گیرنده نپذیرفت'), `failed line ${failed}`);
    expect(/500,?002/.test(await page.textContent('.audit-dlg .ev-fired')), 'the price at the firing, in Latin figures');
    expect(await page.$eval('.audit-dlg .ev-delivery_failed .audit-text', (e) => getComputedStyle(e).borderInlineStartWidth !== '0px'), 'the failure is not set apart');
    return env;
  });

  await test('alerts2: the dialog — up to five conditions, until I see it, once a day, and the 24-hour change', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { symbol: 'BTCUSDT', price: 90000 } })));
    await page.waitForSelector('.alert-dlg');
    for (let i = 0; i < 4; i++) await page.click('.alert-dlg [data-act="add-cond"]');
    expect((await page.$$('.alert-dlg [data-extra]')).length === 4, 'four extra conditions');
    expect(await page.$('.alert-dlg [data-act="add-cond"]') === null, 'a sixth condition offered');
    expect((await page.textContent('.alert-dlg [data-cap]')).includes('حداکثر ۵ شرط، و همه با هم باید برقرار باشند'), 'the cap line');
    await page.click('.alert-dlg [data-extra="3"] [data-act="remove-cond"]');
    expect(await page.$('.alert-dlg [data-act="add-cond"]'), 'the add button did not come back');
    await page.click('.alert-dlg [data-act="until-seen"]');
    expect((await page.textContent('.alert-dlg [data-until-note]')).includes('هر ۱۵ دقیقه برمی‌گردد'), 'until-seen note with the default minutes');
    await page.click('.alert-dlg [data-minutes] .chip >> nth=0');
    expect((await page.textContent('.alert-dlg [data-until-note]')).includes('هر ۵ دقیقه'), 'minutes chosen');
    await page.click('.alert-dlg .chips-row .chip:has-text("روزی یک بار")');
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    let a = (await pc(page, () => window.__pc.useAlerts.getState().list))[0];
    expect(a && a.ands?.length === 3 && a.untilSeen === true && a.repeatMinutes === 5 && a.freq === 'daily' && !a.serverId, `saved ${JSON.stringify(a)}`);
    // The 24-hour change.
    await pc(page, () => window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { symbol: 'ETHUSDT' } })));
    await page.waitForSelector('.alert-dlg');
    await page.selectOption('.alert-dlg select[aria-label="شرط"]', 'change24_over');
    expect((await page.textContent('.alert-dlg')).includes('درصد'), 'the value is a per cent');
    await page.fill('.alert-dlg input[aria-label="مقدار"]', '5');
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    a = (await pc(page, () => window.__pc.useAlerts.getState().list)).find((x) => x.symbol === 'ETHUSDT');
    expect(a && a.condition === 'change24_over' && a.value === 5, `24h alert ${JSON.stringify(a)}`);
    await tick(page, 'ETHUSDT', 2400, 4.9);
    expect((await pc(page, () => window.__pc.useAlerts.getState().list)).find((x) => x.symbol === 'ETHUSDT').active, 'fired under the per cent');
    await tick(page, 'ETHUSDT', 2401, 5.2);
    expect(!(await pc(page, () => window.__pc.useAlerts.getState().list)).find((x) => x.symbol === 'ETHUSDT').active, 'did not fire over the per cent');
    expect((await page.textContent('.toast')).includes('تغییر ۲۴ ساعته‌ی ETHUSDT بیشتر از 5%'), 'the toast sentence');
    return env;
  });

  await test('alerts2: until I see it — the toast says «دیدم», and seeing it stops the alert', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 1000, untilSeen: true, repeatMinutes: 5 }));
    await tick(page, 'BTCUSDT', 1001);
    await page.waitForSelector('.toast [data-act="seen"]');
    let a = (await pc(page, () => window.__pc.useAlerts.getState().list))[0];
    expect(a.active && a.lastFiredAt, 'an until-seen alert went quiet after one firing');
    await showAlerts(page);
    expect(await page.$('.alert-row [data-act="seen"]'), 'no «دیدم» on the row');
    await page.click('.toast [data-act="seen"]');
    a = (await pc(page, () => window.__pc.useAlerts.getState().list))[0];
    expect(!a.active && a.seenAt > 0, `seen ${JSON.stringify(a)}`);
    return env;
  });

  await test('alerts2: a NamaScript alertcondition says whether it holds and becomes an alert that fires', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const code = 'indicator("E2E alerts")\nplot(close, "C")\nalertcondition(gt(close, 0), "Always")\nalertcondition(lt(close, 0), "Never")\n';
    await pc(page, (c) => { const s = window.__pc.useScript.getState(); s.setSource(c); s.apply(); }, code);
    await pc(page, () => { const p = window.__pc.usePanels.getState(); if (p.bottom !== 'namascript') p.setBottom('namascript'); });
    await page.waitForSelector('[data-script-alert="Always"]', { timeout: 15000 });
    expect(await page.getAttribute('[data-script-alert="Always"] [data-firing]', 'data-firing') === 'true', `Always is not holding ${JSON.stringify(await pc(page, () => { const r = window.__pc.useScript.getState().result; const b = window.__pc.useChartBus.getState().bars; return { last: b.at(-1)?.t, n: b.length, al: r?.alerts?.map((a) => [a.msg, a.bars.length, a.bars.slice(-2)]) }; }))}`);
    expect(await page.getAttribute('[data-script-alert="Never"] [data-firing]', 'data-firing') === 'false', 'Never is holding');
    expect((await page.textContent('[data-script-alert="Always"]')).includes('روی کندل آخر برقرار است'), 'holding wording');
    await page.click('[data-script-alert="Always"] [data-act="script-alert"]');
    await page.waitForSelector('.alert-dlg [data-script-cond]');
    expect((await page.textContent('.alert-dlg [data-script-cond]')).includes('Always'), 'the condition in the dialog');
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    const a = (await pc(page, () => window.__pc.useAlerts.getState().list))[0];
    expect(a && a.source === 'script' && a.script.condition === 'Always' && a.script.source.includes('alertcondition'), `script alert ${JSON.stringify(a)?.slice(0, 200)}`);
    // The watcher runs the script every fifteen seconds.
    await page.waitForFunction(() => window.__pc.useAlerts.getState().list[0]?.fires >= 1, null, { timeout: 25000 });
    return env;
  });

  await test('alerts2: an active signal shows the high-impact event window, a closed one does not', async () => {
    const soon = new Date(Date.now() + 90 * 60e3).toISOString();
    const far = new Date(Date.now() + 9 * 3600e3).toISOString();
    let status = 'active';
    const env = await open('XAUUSD/1h?e2e=1', {
      signed: true,
      mock: (u) => {
        if (u.pathname === '/up/tradeyar/api/v1/public/calendar/week') return json([
          { title: 'Core CPI m/m', country: 'USD', date: soon, impact: 'High', forecast: '0.3%', previous: '0.2%' },
          { title: 'Far away', country: 'USD', date: far, impact: 'High' },
          { title: 'Quiet one', country: 'USD', date: soon, impact: 'Low' },
        ]);
        if (u.pathname === '/up/coineprofx/api/public/signals/detail/77') return json({ signal: { id: 77, symbol: 'XAUUSD', direction: 'buy', timeframe: 'H1', status, entry_price: 4100, stop_loss: 4080, tp1: 4150, created_at: new Date().toISOString() } });
        return null;
      },
    });
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.usePage.getState().open('signal', 'coineprofx-77'));
    await page.waitForSelector('[data-impact-window]', { timeout: 15000 });
    const text = await page.textContent('[data-impact-window]');
    expect(text.includes('بازه‌ی خبر پرتأثیر') && text.includes('این هشدار ریسک است، نه پیش‌بینی') && text.includes('Core CPI m/m'), `card ${text}`);
    expect(!text.includes('Far away') && !text.includes('Quiet one'), 'events outside the window or not high impact');
    status = 'closed';
    await pc(page, () => window.__pc.usePage.getState().open(null));
    await pc(page, () => window.__pc.usePage.getState().open('signal', 'coineprofx-77'));
    await page.waitForSelector('.page-inner .sym', { timeout: 15000 });
    await page.waitForTimeout(800);
    expect(await page.$('[data-impact-window]') === null, 'the window on a closed signal');
    return env;
  });
}
