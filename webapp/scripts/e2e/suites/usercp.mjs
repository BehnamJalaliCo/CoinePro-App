// «پنل کاربری» (/next/panel): every section, against a stateful fake of the panel server (the relay's
// /up/panel/* and the admin service behind it) and the shared backend fixtures.
const PNG_1PX = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
const NOW = Math.floor(Date.now() / 1000);
const KINDS = ['layouts', 'settings', 'drawings', 'templates', 'watchlist'];
const SECTIONS = ['overview', 'trading', 'copytrade', 'security', 'workspace', 'inbox', 'support', 'billing', 'connections', 'privacy'];

/** The panel server, in memory: what the relay and the admin service would answer this reader. */
function fakePanel({ down = false } = {}) {
  const s = {
    calls: [], events: [], signedOut: 0, erased: false, exported: 0,
    sessions: [
      { sid: 'here000000000001', created: NOW - 3600, last_seen: NOW, browser: 'Chrome', os: 'Windows', device: 'desktop', country: 'IR', accounts: ['tradeyar:7'], current: true },
      { sid: 'other00000000002', created: NOW - 86400, last_seen: NOW - 7200, browser: 'Safari', os: 'iOS', device: 'phone', country: 'DE', accounts: ['tradeyar:7'], current: false },
    ],
    workspace: { settings: { version: 2, data: { keys: { 'pc.chartsettings.v1': '{"e2e":"from-cloud"}' } }, history: [1, 2] } },
    inbox: [
      { id: 1, kind: 'offer', title: 'تخفیف ویژه‌ی پریمیوم', body: 'تا پایان هفته', link: '/next/panel/billing', ticket_id: null, created_at: NOW - 600, read: false, broadcast: true },
      { id: 2, kind: 'info', title: 'به پنل خوش آمدید', body: 'همه‌چیز در یک جا', link: '', ticket_id: null, created_at: NOW - 3600, read: false, broadcast: false },
    ],
    tickets: [{ id: 5, subject: 'سوال درباره‌ی VIP', category: 'billing', status: 'answered', created_at: NOW - 86400, updated_at: NOW - 3600, unread_user: true,
      messages: [{ id: 1, at: NOW - 86400, author: 'user', body: 'چطور VIP شوم؟', attachments: [] }, { id: 2, at: NOW - 3600, author: 'owner', body: 'از بخش پیوستن به VIP.', attachments: [] }] }],
  };
  const meta = () => KINDS.map((k) => ({ kind: k, version: s.workspace[k]?.version ?? 0, size: s.workspace[k] ? JSON.stringify(s.workspace[k].data).length : 0, updated_at: s.workspace[k] ? NOW - 60 : null, device: s.workspace[k] ? 'Firefox · Linux' : null, versions: s.workspace[k]?.history.length ?? 0, limit: k === 'drawings' ? 1048576 : 262144 }));
  const me = () => ({
    account: 'tradeyar:7', first_seen: NOW - 90 * 86400, last_seen: NOW, sign_ins: 41, vip: true, paid: false, plan: 'pro', premium_until: null,
    aliases: [{ alias: 'coineprofx:45', backend: 'coineprofx', email: 'e2e@example.com', name: 'E2E', vip: 0, paid: 0, plan: '', last_seen: NOW },
      { alias: 'tradeyar:7', backend: 'tradeyar', email: 'e2e@example.com', name: 'E2E', vip: 1, paid: 0, plan: 'pro', last_seen: NOW }],
    profile: s.profile ?? {}, prefs: s.prefs ?? {}, unread: s.inbox.filter((m) => !m.read).length, open_tickets: s.tickets.filter((t) => t.status !== 'closed').length,
    answered_tickets: s.tickets.filter((t) => t.unread_user).length, trades_30d: 2, workspace: meta(),
    previous_sign_in: { at: NOW - 86400, device: 'phone', browser: 'Safari', os: 'iOS', country: 'DE' },
    session: { sid: 'here000000000001', device: 'desktop', browser: 'Chrome', os: 'Windows', country: 'IR' },
  });
  const json = (body, status = 200, headers = {}) => ({ status, contentType: 'application/json', body: JSON.stringify(body), headers });
  const answer = (method, path, body) => {
    if (path === 'signout') { s.signedOut++; return json({ ok: true }); }
    if (down) return json({ error: 'panel_off' }, 503);
    if (path === 'me') return json(me());
    if (path === 'sessions') return json({ sessions: s.sessions });
    if (path === 'sessions/revoke') {
      const before = s.sessions.length;
      s.sessions = body.all ? [] : body.others ? s.sessions.filter((x) => x.current) : s.sessions.filter((x) => x.sid !== body.sid);
      return json({ revoked: before - s.sessions.length });
    }
    if (path === 'security') return json({ failed_30d: 1, devices: [{ browser: 'Chrome', os: 'Windows', device: 'desktop', last_seen: NOW, sign_ins: 30, country: 'IR' }, { browser: 'Safari', os: 'iOS', device: 'phone', last_seen: NOW - 7200, sign_ins: 11, country: 'DE' }],
      items: [{ id: 3, at: NOW - 600, kind: 'signin', name: 'google', source: 'relay', backend: 'tradeyar', device: 'desktop', browser: 'Chrome', os: 'Windows', country: 'IR', detail: null },
        { id: 2, at: NOW - 900, kind: 'signin_failed', name: 'password', source: 'relay', backend: 'tradeyar', device: 'phone', browser: 'Safari', os: 'iOS', country: 'TR', detail: null }] });
    if (path.startsWith('activity')) return json({ items: [{ id: 9, at: NOW - 120, kind: 'trade', name: 'order', source: 'relay', backend: 'tradeyar', device: 'desktop', browser: 'Chrome', os: 'Windows', country: 'IR', detail: { symbol: 'BTCUSDT', side: 'buy', status: 200 } }] });
    if (path.startsWith('trades')) return json({ total: 2, items: [
      { id: 2, at: NOW - 300, backend: 'tradeyar', method: 'POST', path: 'trade/orders', action: 'order', symbol: 'BTCUSDT', side: 'buy', quantity: 0.01, type: 'limit', price: 80000, venue: null, status: 200, ok: true, order_id: '991', state: 'submitted', detail: null, device: 'desktop' },
      { id: 1, at: NOW - 900, backend: 'tradeyar', method: 'POST', path: 'trade/positions/close', action: 'close', symbol: 'ETHUSDT', side: 'sell', quantity: 1, type: null, price: null, venue: null, status: 422, ok: false, order_id: null, state: null, detail: 'margin too low', device: 'phone' }] });
    if (path === 'event') { s.events.push(...body.events); return json({ stored: body.events.length }); }
    if (path === 'workspace') return json({ kinds: meta() });
    const ws = /^workspace\/([a-z]+)(\/versions(?:\/(\d+))?|\/restore)?$/.exec(path);
    if (ws) {
      const [, kind, tail, version] = ws;
      const part = s.workspace[kind];
      if (tail === '/versions') return json({ versions: (part?.history ?? []).map((v) => ({ version: v, size: 40, saved_at: NOW - (10 - v) * 600, device: 'Firefox · Linux', restored_from: null })).reverse() });
      if (tail === '/restore') { part.version += 1; part.history.push(part.version); s.restored = { kind, from: body.version }; return json({ version: part.version }); }
      if (tail && version) return json({ version: Number(version), data: part.data });
      if (method === 'PUT') {
        if ((part?.version ?? 0) !== body.base_version && !body.force) return json({ error: 'newer_version', version: part.version }, 409);
        const next = (part?.version ?? 0) + 1;
        s.workspace[kind] = { version: next, data: body.data, history: [...(part?.history ?? []), next] };
        return json({ kind, version: next });
      }
      return json(part ? { kind, version: part.version, data: part.data } : { kind, version: 0, data: null });
    }
    if (path === 'inbox') return json({ items: s.inbox, unread: s.inbox.filter((m) => !m.read).length });
    if (path === 'inbox/read-all') { s.inbox.forEach((m) => { m.read = true; }); return json({ marked: 2 }); }
    const msg = /^inbox\/(\d+)(\/read)?$/.exec(path);
    if (msg) { const m = s.inbox.find((x) => x.id === Number(msg[1])); if (msg[2]) m.read = true; else s.inbox = s.inbox.filter((x) => x !== m); return json({ ok: true }); }
    if (path === 'tickets' && method === 'POST') {
      const id = 6 + s.tickets.length;
      s.tickets.unshift({ id, subject: body.subject, category: body.category, status: 'open', created_at: NOW, updated_at: NOW, unread_user: false,
        messages: [{ id: 10, at: NOW, author: 'user', body: body.body, attachments: body.attachments.map((a, i) => ({ id: 100 + i, name: a.name, type: 'image/jpeg', size: a.data.length })) }] });
      s.lastAttachments = body.attachments;
      return json({ id }, 201);
    }
    if (path === 'tickets') return json({ items: s.tickets.map(({ messages, ...t }) => t) });
    const tk = /^tickets\/(\d+)(\/messages|\/close|\/attachments\/\d+)?$/.exec(path);
    if (tk) {
      const t = s.tickets.find((x) => x.id === Number(tk[1]));
      if (!t) return json({ error: 'ticket' }, 404);
      if (tk[2] === '/messages') { t.messages.push({ id: 99, at: NOW, author: 'user', body: body.body, attachments: [] }); t.status = 'open'; return json({ ok: true }, 201); }
      if (tk[2] === '/close') { t.status = 'closed'; return json({ ok: true }); }
      if (tk[2]?.startsWith('/attachments')) return { status: 200, contentType: 'image/png', body: Buffer.from(PNG_1PX, 'base64') };
      t.unread_user = false;
      return json({ ticket: { ...t, messages: undefined }, messages: t.messages });
    }
    if (path === 'profile') { s.profile = body; return json({ profile: body }); }
    if (path === 'prefs') { s.prefs = { ...(s.prefs ?? {}), ...body }; return json({ prefs: s.prefs }); }
    if (path === 'export') { s.exported++; return json({ account: 'tradeyar:7', workspace: [] }, 200, { 'Content-Disposition': 'attachment; filename="pro-chart-panel-data.json"' }); }
    if (path === 'data' && method === 'DELETE') { s.erased = body.confirm === true; return json({ erased: { web_events: 3 } }); }
    return json({ error: 'not_found' }, 404);
  };
  const setup = async (ctx) => {
    await ctx.route('http://pc.local/up/**', async (route) => {
      const req = route.request();
      const u = new URL(req.url());
      if (u.pathname.startsWith('/up/panel/')) {
        const path = u.pathname.slice('/up/panel/'.length) + (u.search || '');
        const clean = path.split('?')[0];
        let body = null;
        try { body = req.postData() ? JSON.parse(req.postData()) : null; } catch { body = null; }
        const auth = req.headers().authorization ?? '';
        s.calls.push({ method: req.method(), path: clean, auth });
        if (clean !== 'signout' && auth !== 'Bearer pch_e2e') return route.fulfill(json({ error: 'signed_out' }, 401));
        return route.fulfill(answer(req.method(), clean.startsWith('activity') || clean.startsWith('trades') ? path : clean, body));
      }
      // The platform routes the panel reads that the shared fixtures do not answer.
      if (u.pathname === '/up/coineprofx/api/user/mobile/entitlements') return route.fulfill(json({ premium: false, all: true }));
      if (u.pathname === '/up/tradeyar/api/mobile/v1/entitlements') return route.fulfill(json({ premium: true, plan: 'quarterly', ends_at: new Date(Date.now() + 40 * 86400e3).toISOString(), all: true }));
      return route.fallback();
    });
  };
  return { s, setup };
}

export default async function ({ test, open, ready, pc, expect }) {
  const toPanel = async (env, section = null) => {
    await pc(env.page, (x) => window.__pc.usePage.getState().open('panel', x), section);
    await env.page.waitForSelector('.ucp-section h2', { timeout: 15000 });
  };

  await test('usercp: the account menu opens the panel; overview shows both accounts, VIP and the day', async () => {
    const fake = fakePanel();
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, setup: fake.setup });
    await ready(env.page);
    await env.page.click('.account .avatar');
    await env.page.click('.menu-item.panel-item');
    await env.page.waitForURL(/\/next\/panel$/);
    await env.page.waitForSelector('.ucp-stat');
    expect(await env.page.locator('.ucp-stat').count() === 4, 'four figure tiles');
    await env.page.waitForFunction(() => document.querySelector('.ucp-stat .ucp-stat-value')?.textContent === '1', null, { timeout: 10000 });
    const text = await env.page.textContent('.ucp-main');
    expect(text.includes('tradeyar:7') && text.includes('coineprofx:45'), 'both accounts listed');
    expect(text.includes('سه‌ماهه'), 'TradeYar premium plan shown');
    expect((await env.page.textContent('.ucp-nav .ucp-badge')) === '2', 'unread badge on the inbox');
    expect(fake.s.calls.every((c) => c.path === 'signout' || c.auth === 'Bearer pch_e2e'), 'every panel call carries the handle');
    return env;
  });

  for (const [lang, scheme, width, height] of [['fa', 'light', 1440, 900], ['en', 'dark', 1440, 900], ['fa', 'dark', 390, 844], ['en', 'light', 390, 844]]) {
    await test(`usercp: every section renders (${lang}, ${scheme}, ${width}px) without errors or sideways scroll`, async () => {
      const fake = fakePanel();
      const env = await open('BTCUSDT/1h?e2e=1', { signed: true, setup: fake.setup, scheme, width, height });
      await ready(env.page);
      await pc(env.page, (l) => window.__pc.usePrefs.getState().setLang(l), lang);
      for (const id of SECTIONS) {
        await toPanel(env, id === 'overview' ? null : id);
        await env.page.waitForFunction((x) => document.querySelector('.ucp-main')?.dataset.section === x, id);
        await env.page.waitForTimeout(250);
        const dir = await env.page.getAttribute('.page', 'dir');
        expect(dir === (lang === 'fa' ? 'rtl' : 'ltr'), `${id}: page direction ${dir}`);
        const wide = await env.page.evaluate(() => { const b = document.querySelector('.page-body'); return b.scrollWidth - b.clientWidth; });
        expect(wide <= 1, `${id}: ${wide}px sideways scroll at ${width}px`);
        // USERCP_SHOTS=<dir>: keep a picture of each state, for looking at by eye.
        if (process.env.USERCP_SHOTS) await env.page.screenshot({ path: `${process.env.USERCP_SHOTS}/${id}-${lang}-${scheme}-${width}.jpg`, type: 'jpeg', quality: 60 });
        const heading = await env.page.textContent('.ucp-section h2');
        expect(lang === 'fa' ? /[آ-ی]/.test(heading) : /^[A-Za-z]/.test(heading), `${id}: heading «${heading}» in ${lang}`);
      }
      return env;
    });
  }

  await test('usercp: security closes one session, then signs out everywhere', async () => {
    const fake = fakePanel();
    const env = await open('panel/security?e2e=1', { signed: true, setup: fake.setup });
    env.page.on('dialog', (d) => { d.accept().catch(() => {}); });
    await env.page.waitForSelector('.ucp-sessions li');
    expect(await env.page.locator('.ucp-sessions li').count() === 2, 'two live sessions');
    expect((await env.page.textContent('.ucp-main')).includes('Safari'), 'the other browser is named');
    await env.page.click('.ucp-sessions li:not(.current) button');
    await env.page.waitForFunction(() => document.querySelectorAll('.ucp-sessions li').length === 1);
    expect(fake.s.sessions.length === 1 && fake.s.sessions[0].current, 'only this browser is left');
    await env.page.click('.ucp-card-head .btn.down');
    await env.page.waitForFunction(() => JSON.parse(localStorage.getItem('pc.sessions.v1') ?? '{}').tradeyar === undefined);
    await env.page.waitForFunction(() => document.querySelector('.gate, .ucp-gate'), null, { timeout: 5000 });
    for (let i = 0; i < 20 && !fake.s.signedOut; i++) await env.page.waitForTimeout(100);
    expect(fake.s.signedOut >= 1, 'the relay was told to forget this browser');
    return env;
  });

  await test('usercp: workspace sync sends this browser, takes the cloud, restores a version', async () => {
    const fake = fakePanel();
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, setup: fake.setup });
    await ready(env.page);
    await env.page.evaluate(() => localStorage.setItem('pc.layouts.v1', JSON.stringify({ layouts: [{ name: 'e2e' }], templates: [], current: null })));
    await toPanel(env, 'workspace');
    await env.page.waitForSelector('.ucp-part');
    expect(await env.page.locator('.ucp-part').count() === 5, 'five parts');
    await env.page.click('.ucp-section-head .btn-primary');
    // The app has written its own settings here, and the cloud has another device's: a conflict.
    await env.page.waitForSelector('.ucp-conflicts li');
    expect((await env.page.textContent('.ucp-conflicts')).length > 0, 'the conflict is named');
    await env.page.locator('.ucp-conflicts li').first().locator('.btn').nth(1).click();
    await env.page.waitForSelector('.ucp-note.t-gold');
    expect(fake.s.workspace.layouts?.version === 1, 'layouts pushed');
    expect(JSON.stringify(fake.s.workspace.layouts.data).includes('e2e'), 'the pushed blob holds the layout');
    expect(!JSON.stringify(fake.s.workspace).includes('pch_e2e'), 'no sign-in handle in the cloud');
    const pulled = await env.page.evaluate(() => localStorage.getItem('pc.chartsettings.v1'));
    expect(pulled === '{"e2e":"from-cloud"}', `settings pulled: ${pulled}`);
    // Auto-sync on, then a restore of an older settings version.
    await env.page.click('.ucp-switch');
    await env.page.waitForFunction(() => JSON.parse(localStorage.getItem('pc.cloud.v1')).auto === true);
    const settings = env.page.locator('.ucp-part').nth(1);
    env.page.on('dialog', (d) => { d.accept().catch(() => {}); });
    await settings.locator('.ucp-actions .btn').click();
    await env.page.waitForSelector('.ucp-versions li button');
    await env.page.locator('.ucp-versions li button').first().click();
    for (let i = 0; i < 30 && !fake.s.restored; i++) await env.page.waitForTimeout(100);
    expect(fake.s.restored?.kind === 'settings' && fake.s.restored.from === 1, `restored ${JSON.stringify(fake.s.restored)}`);
    return env;
  });

  await test('usercp: the workspace lists layouts and watchlists one by one and opens them on the chart', async () => {
    const fake = fakePanel();
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, setup: fake.setup });
    await ready(env.page);
    await pc(env.page, () => window.__pc.useLayouts.getState().save('چیدمان آزمایشی'));
    await toPanel(env, 'workspace');
    await env.page.waitForSelector('.ucp-library');
    expect((await env.page.textContent('.ucp-library')).includes('چیدمان آزمایشی'), 'the saved layout is listed');
    await env.page.click('.ucp-symbols button >> text=ETHUSDT');
    await env.page.waitForURL(/\/next\/ETHUSDT\/1h$/);
    return env;
  });

  await test('usercp: inbox reads and dismisses; support opens a ticket with a picture and answers', async () => {
    const fake = fakePanel();
    const env = await open('panel/inbox?e2e=1', { signed: true, setup: fake.setup });
    await env.page.waitForSelector('.ucp-inbox li.unread');
    await env.page.click('.ucp-inbox li:first-child .ucp-inbox-head');
    await env.page.waitForFunction(() => document.querySelectorAll('.ucp-inbox li.unread').length === 1);
    await env.page.click('.ucp-section-head .btn');
    await env.page.waitForFunction(() => !document.querySelector('.ucp-inbox li.unread'));
    await env.page.click('.ucp-inbox-body .btn:last-child');
    await env.page.waitForFunction(() => document.querySelectorAll('.ucp-inbox li').length === 1);

    await pc(env.page, () => window.__pc.usePage.getState().open('panel', 'support'));
    await env.page.waitForSelector('.ucp-tickets li');
    expect((await env.page.textContent('.ucp-tickets')).includes('سوال درباره‌ی VIP'), 'the old ticket is listed');
    await env.page.click('.ucp-section-head .btn-primary');
    await env.page.fill('.ucp-form input[maxlength="120"]', 'چارت روی گوشی خالی است');
    await env.page.selectOption('.ucp-form select', 'bug');
    await env.page.fill('.ucp-form textarea', 'از دیروز چارت طلا روی کروم اندروید خالی می‌ماند.');
    const [chooser] = await Promise.all([env.page.waitForEvent('filechooser'), env.page.click('.ucp-attach .btn')]);
    await chooser.setFiles({ name: 'shot.png', mimeType: 'image/png', buffer: Buffer.from(PNG_1PX, 'base64') });
    await env.page.waitForSelector('.ucp-attach .ucp-thumb img');
    await env.page.click('.ucp-form .btn-primary');
    await env.page.waitForSelector('.ucp-thread .ucp-bubble');
    expect(fake.s.lastAttachments?.length === 1 && fake.s.lastAttachments[0].data.startsWith('data:image/jpeg;base64,'), 'the picture went as a small JPEG');
    await env.page.waitForSelector('.ucp-thread .ucp-thumb img');
    await env.page.fill('.ucp-thread ~ .ucp-form textarea', 'یک نکته‌ی دیگر');
    await env.page.click('.ucp-thread ~ .ucp-form .btn-primary');
    await env.page.waitForFunction(() => document.querySelectorAll('.ucp-thread .ucp-bubble').length === 2);
    await env.page.click('.ucp-thread ~ .ucp-form .ucp-actions .btn-quiet');
    await env.page.waitForFunction(() => /بسته|Closed/.test(document.querySelector('.ucp-card-head .ucp-pill')?.textContent ?? ''));
    return env;
  });

  await test('usercp: trading, copy trading, billing and connections read both platforms', async () => {
    const fake = fakePanel();
    const env = await open('panel/trading?e2e=1', { signed: true, setup: fake.setup });
    await env.page.waitForSelector('.ucp-table tbody tr');
    expect((await env.page.textContent('.ucp-main')).includes('BTCUSDT'), 'the TradeYar position is listed');
    await env.page.click('.page-tabs .chip:nth-child(2)');
    await env.page.waitForFunction(() => /XAUUSD/.test(document.querySelector('.ucp-main')?.textContent ?? ''));
    await env.page.click('.page-tabs .chip:nth-child(3)');
    await env.page.waitForFunction(() => /991/.test(document.querySelector('.ucp-main')?.textContent ?? ''));
    expect((await env.page.textContent('.ucp-main')).includes('margin too low'), 'a refused call keeps its reason');
    for (const [id, word] of [['copytrade', 'lbank'], ['billing', 'سه‌ماهه'], ['connections', 'abcd…wxyz']]) {
      await pc(env.page, (x) => window.__pc.usePage.getState().open('panel', x), id);
      await env.page.waitForFunction((w) => (document.querySelector('.ucp-main')?.textContent ?? '').includes(w), word, { timeout: 10000 });
    }
    return env;
  });

  await test('usercp: data and privacy exports, turns reports off, and erases', async () => {
    const fake = fakePanel();
    const env = await open('panel/privacy?e2e=1', { signed: true, setup: fake.setup });
    await env.page.waitForSelector('.ucp-danger');
    const [download] = await Promise.all([env.page.waitForEvent('download'), env.page.locator('.ucp-card').nth(1).locator('.btn').first().click()]);
    expect(/pro-chart-panel-data/.test(download.suggestedFilename()), download.suggestedFilename());
    expect(fake.s.exported === 1, 'export asked once');
    await env.page.click('.ucp-switch');
    expect(await env.page.evaluate(() => localStorage.getItem('pc.telemetry.v1')) === 'off', 'reports switched off');
    const erase = env.page.locator('.ucp-danger .btn').first();
    expect(await erase.isDisabled(), 'erase waits for the typed word');
    await env.page.fill('.ucp-danger input', 'پاک کن');
    await erase.click();
    await env.page.waitForSelector('.ucp-note.t-good');
    expect(fake.s.erased, 'erase confirmed to the server');
    return env;
  });

  await test('usercp: the page reports its errors and feature use, scrubbed, only when signed in', async () => {
    const fake = fakePanel();
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, setup: fake.setup });
    await ready(env.page);
    const key = await pc(env.page, () => window.__pc.indicators.find((k) => /rsi/i.test(k)) ?? window.__pc.indicators[0]);
    await pc(env.page, (k) => window.__pc.useStudies.getState().add(k), key);
    await env.page.evaluate(() => { setTimeout(() => { throw new Error('e2e boom for sara@example.com pch_secret'); }, 0); });
    await env.page.waitForTimeout(200);
    env.errors.length = 0; // the error above is this test's own
    await pc(env.page, () => window.__pc.panel.flush());
    const names = fake.s.events.map((e) => e.name);
    expect(names.some((n) => n.startsWith('indicator.add.')), `feature event: ${names.join(', ')}`);
    const boom = fake.s.events.find((e) => e.kind === 'error');
    expect(boom && boom.name.includes('e2e boom') && !/sara@|pch_secret/.test(JSON.stringify(boom)), `error event scrubbed: ${JSON.stringify(boom)}`);
    // Signed out, nothing is sent.
    await env.page.evaluate(() => { localStorage.setItem('pc.sessions.v1', '{}'); });
    const before = fake.s.events.length;
    await env.page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1', { waitUntil: 'domcontentloaded' });
    await ready(env.page);
    await pc(env.page, () => window.__pc.useStudies.getState().add(window.__pc.indicators[1]));
    await pc(env.page, () => window.__pc.panel.flush());
    expect(fake.s.events.length === before, 'a guest sends no events');
    return env;
  });

  await test('usercp: a guest meets the sign-in gate; a panel server that is down leaves the platforms working', async () => {
    const guest = await open('panel?e2e=1', { setup: fakePanel().setup });
    await guest.page.waitForSelector('.gate');
    await guest.ctx.close();
    const fake = fakePanel({ down: true });
    const env = await open('panel?e2e=1', { signed: true, setup: fake.setup });
    await env.page.waitForSelector('.ucp-note.t-warn');
    await env.page.waitForFunction(() => document.querySelector('.ucp-stat .ucp-stat-value')?.textContent === '1', null, { timeout: 10000 });
    await pc(env.page, () => window.__pc.usePage.getState().open('panel', 'billing'));
    await env.page.waitForFunction(() => /سه‌ماهه/.test(document.querySelector('.ucp-main')?.textContent ?? ''));
    return env;
  });
}
