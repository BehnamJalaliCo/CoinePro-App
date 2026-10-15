// The free VIP (area H): the sign-up funnel for crypto (TradeYar: LBank/Ourbit) and forex
// (CoinePro-FX: OneRoyal, signals only) in every state the servers answer, the VIP rules applied to
// a web account (warning, final warning, removal, recovery) with the banners and the status page,
// and the user site's screens ported here (performance, affiliate, copy trading).
export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json, vipMember, forexMember } = fixtures;
  const TYM = '/up/tradeyar/api/mobile/v1/';
  const settle = async (page) => {
    await page.waitForSelector('.page-body', { timeout: 10000 });
    await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…|Loading…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(200);
  };
  const noOverflow = async (page, what) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1 || [...document.querySelectorAll('.page-body')].some((b) => b.scrollWidth > b.clientWidth + 1));
    expect(!over, `${what}: horizontal overflow`);
  };
  /** A mock whose VIP answers follow mutable state; every request it saw is kept. */
  const world = (init = {}) => {
    const w = { reg: 'awaiting_uid', vip: 'none', forex: 'none', seen: [], ...init };
    w.mock = (u, method, req) => {
      const p = u.pathname;
      if (p.startsWith('/up/')) w.seen.push({ p, method, body: req.postData() });
      if (p === `${TYM}membership/status`) return json({ status: w.reg, message_fa: w.reg === 'rejected_referral' ? 'هنوز به‌عنوان زیرمجموعه‌ی تریدیار شناسایی نشدی.' : w.reg === 'pending_deposit' ? 'حساب شما زیرمجموعه‌ی تریدیار تأیید شد، اما موجودی به حد لازم نرسیده است.' : w.reg === 'error' ? 'بررسی خودکار انجام نشد. لطفاً چند دقیقه دیگر دوباره تلاش کنید.' : 'هنوز UID صرافی را ثبت نکرده‌اید.', next_step: w.reg === 'approved' ? null : 'uid', can_resubmit: w.reg !== 'approved', uid: w.reg === 'awaiting_uid' ? null : 'LBA1A23456', exchange: null, vip: { status: w.vip, entitled: ['active', 'low', 'critical'].includes(w.vip) } });
      if (p === `${TYM}membership/uid` && method === 'POST') { w.reg = w.afterUid ?? 'pending_deposit'; if (w.reg === 'pending_deposit') w.vip = 'pending_deposit'; if (w.reg === 'rejected_referral') w.vip = 'awaiting_referral'; return json({ status: w.reg, message_fa: 'ok', can_resubmit: true, uid: 'LBA1A23456', exchange: 'lbank' }); }
      if (p === `${TYM}membership/vip`) return json(vipMember(w.vip));
      if (p === `${TYM}membership/vip/recheck`) { if (w.recheckTo) w.vip = w.recheckTo; return json({ ...vipMember(w.vip), recheck: { outcome: w.recheckTo ? 'granted' : 'pending_deposit', granted: Boolean(w.recheckTo), message_fa: w.recheckTo ? 'دسترسی VIP دوباره فعال شد.' : 'هنوز به حد لازم نرسیده است.' } }); }
      if (p === '/up/coineprofx/api/user/mobile/forex-vip') return json(forexMember(w.forex));
      if (p === '/up/coineprofx/api/user/mobile/forex-vip/claim') { w.forex = 'submitted'; return json(forexMember('submitted')); }
      if (p === '/up/coineprofx/api/user/auth/login') return json({ access_token: 'pch_fx2', refresh_token: 'pch_fx2r', expires_in: 3600, user: { email: 'fx@example.com', full_name: 'FX' } });
      return null;
    };
    return w;
  };

  await test('vip: a guest sees the server’s rules and is asked to sign in first', async () => {
    const env = await open('vip?e2e=1');
    await settle(env.page);
    await env.page.waitForSelector('.vip-rules', { timeout: 10000 });
    const rules = await env.page.textContent('.vip-rules');
    expect(/50/.test(rules) && /30/.test(rules) && /10/.test(rules), `rules: ${rules}`);
    expect(/۴۸/.test(rules), 'grace hours in Persian digits');
    expect(await env.page.isVisible('.vip-rail-step.now >> text=حساب پرو چارت'), 'account step is current');
    await env.page.click('.vip-main >> text=ورود یا ثبت‌نام');
    await env.page.waitForSelector('.dialog.auth', { timeout: 5000 });
    return env;
  });

  await test('vip: crypto funnel — link, deposit, UID check, then waiting for the deposit', async () => {
    const w = world();
    const env = await open('vip?e2e=1', { signed: true, mock: w.mock });
    await settle(env.page);
    await env.page.waitForSelector('.vip-exchange.on >> text=LBank', { timeout: 10000 });
    const link = await env.page.getAttribute('.vip-partner >> nth=0', 'href');
    const rel = await env.page.getAttribute('.vip-partner >> nth=0', 'rel');
    expect(link === 'https://lbank.com/ref/E2E' && /sponsored/.test(rel) && /noreferrer/.test(rel), `${link} ${rel}`);
    await env.page.click('.vip-exchange >> text=Ourbit');
    expect((await env.page.getAttribute('.vip-partner >> nth=0', 'href')).includes('ourbit'), 'Ourbit link');
    await env.page.click('.vip-exchange >> text=LBank');
    await env.page.click('text=حساب را ساختم');
    await env.page.waitForSelector('text=حساب را شارژ کنید');
    await env.page.click('text=شارژ کردم');
    await env.page.waitForSelector('.vip-form input.num');
    await env.page.fill('.vip-form input.num', '09121234567');
    await env.page.click('.vip-form button.btn-primary');
    await env.page.waitForFunction(() => /موبایل/.test(document.querySelector('.vip-form [role=alert]')?.textContent ?? ''), null, { timeout: 10000 });
    await env.page.fill('.vip-form input.num', ' lba1a23456 ');
    await env.page.click('.vip-form button.btn-primary');
    await env.page.waitForSelector('[data-outcome=deposit]', { timeout: 10000 });
    const post = w.seen.find((x) => x.p.endsWith('membership/uid'));
    expect(post && JSON.parse(post.body).uid === 'LBA1A23456' && JSON.parse(post.body).exchange === 'lbank', `uid post ${post?.body}`);
    expect(/12/.test(await env.page.textContent('[data-outcome=deposit]')), 'current balance shown');
    await env.page.click('text=واریز کردم — همین الان بررسی کن');
    await env.page.waitForSelector('.vip-result .vip-note', { timeout: 5000 });
    expect(w.seen.some((x) => x.p.endsWith('membership/vip/recheck') && x.method === 'POST'), 'recheck posted');
    // Re-read on its 8 s timer and after each step, never in a loop (an unstable hook dependency once
    // made every answer trigger the next request).
    const reads = w.seen.filter((x) => x.p.endsWith('membership/status')).length;
    await env.page.waitForTimeout(1500);
    const later = w.seen.filter((x) => x.p.endsWith('membership/status')).length;
    expect(reads <= 8 && later - reads <= 1, `membership/status read ${reads} times, then ${later - reads} more in 1.5 s`);
    return env;
  });

  for (const c of [
    { reg: 'rejected_referral', vip: 'awaiting_referral', outcome: 'not_indexed', text: 'شناسایی نشده' },
    { reg: 'error', vip: 'none', outcome: 'error', text: 'بررسی انجام نشد' },
    { reg: 'approved', vip: 'active', outcome: 'vip', text: 'فعال است' },
    { reg: 'verifying', vip: 'none', outcome: 'verifying', text: 'در حال بررسی' },
    { reg: 'approved', vip: 'none', outcome: 'approved', text: 'در حال صدور' },
  ]) {
    await test(`vip: funnel verdict ${c.outcome}`, async () => {
      const w = world({ reg: c.reg, vip: c.vip });
      const env = await open('vip?e2e=1', { signed: true, mock: w.mock });
      await settle(env.page);
      await env.page.waitForSelector(`[data-outcome=${c.outcome}]`, { timeout: 15000 });
      expect((await env.page.textContent('.vip-result')).includes(c.text), `${c.outcome}: ${await env.page.textContent('.vip-result')}`);
      if (c.outcome === 'not_indexed') {
        await env.page.click('.vip-result button:has-text("UID دیگری بفرستم")');
        await env.page.waitForSelector('.vip-form input.num', { timeout: 10000 });
      }
      if (c.outcome === 'vip') {
        await env.page.click('.vip-result button:has-text("ورود به کانال VIP تلگرام")');
        await env.page.waitForSelector('.vip-result a.link', { timeout: 10000 });
        expect(w.seen.some((x) => x.p.endsWith('membership/vip/invite') && x.method === 'POST'), 'invite minted');
        expect((await env.page.getAttribute('.vip-result a.link', 'href')) === 'https://t.me/+e2eFresh', 'fresh link shown');
      }
      return env;
    });
  }

  await test('vip: forex is OneRoyal, verification by support, signals only', async () => {
    const w = world();
    const env = await open('vip?e2e=1', { signed: true, mock: w.mock });
    await settle(env.page);
    await env.page.click('.page-tabs >> text=فارکس · OneRoyal');
    await env.page.waitForSelector('.vip-steps li >> nth=2', { timeout: 10000 });
    const main = await env.page.textContent('.vip-funnel');
    expect(/500/.test(main) && /OneRoyal/.test(main), 'broker and minimum from the server');
    expect(/فقط سیگنال/.test(main), 'signals only said');
    expect(!/متاتریدر|اتصال حساب MT5|کپی‌ترید فارکس/.test(main), 'no forex trading offered');
    expect((await env.page.getAttribute('.vip-partner >> nth=0', 'href')).includes('oneroyal'), 'OneRoyal referral');
    await env.page.fill('[data-forex-status=none] input.num', '51234567');
    await env.page.fill('[data-forex-status=none] input[type=email]', 'fx@example.com');
    await env.page.click('[data-forex-status=none] button.btn-primary');
    await env.page.waitForSelector('[data-forex-status=submitted]', { timeout: 5000 });
    const claim = w.seen.find((x) => x.p.endsWith('forex-vip/claim'));
    expect(claim && JSON.parse(claim.body).account_login === '51234567', `claim ${claim?.body}`);
    return env;
  });

  await test('vip: a TradeYar-only reader signs in to CoinePro-FX for forex', async () => {
    const w = world();
    const env = await open('vip?e2e=1', { mock: w.mock });
    await env.page.evaluate(() => {
      const s = { access: 'pch_e2e', refresh: 'pch_e2e_r', refreshAt: Date.now() + 36e5, user: { name: 'E2E', email: 'e2e@example.com' } };
      localStorage.setItem('pc.sessions.v1', JSON.stringify({ tradeyar: s }));
      localStorage.setItem('pc.vip.v1', JSON.stringify({ market: 'forex' }));
    });
    await env.page.reload();
    await settle(env.page);
    await env.page.waitForSelector('.vip-fx-account', { timeout: 10000 });
    expect((await env.page.inputValue('.vip-fx-account input[type=email]')) === 'e2e@example.com', 'e-mail carried over');
    await env.page.fill('.vip-fx-account input[type=password]', 'barane-paeezi-42');
    await env.page.click('.vip-fx-account button.btn-primary');
    await env.page.waitForSelector('[data-forex-status=none]', { timeout: 10000 });
    expect(await pc(env.page, () => Boolean(window.__pc.useSession.getState().sessions.coineprofx)), 'forex session kept');
    return env;
  });

  for (const sev of ['low', 'critical', 'removed']) {
    await test(`vip: ${sev} balance shows its banner over the chart`, async () => {
      const w = world({ reg: 'approved', vip: sev });
      const env = await open('BTCUSDT/1h?e2e=1', { signed: true, mock: w.mock });
      await ready(env.page);
      await env.page.waitForSelector(`[data-vip-banner=${sev}]`, { timeout: 15000 });
      const text = await env.page.textContent('.vip-banner');
      if (sev === 'low') expect(/21\.4/.test(text) && /30/.test(text), text);
      if (sev === 'critical') expect(/4\.2/.test(text) && /ساعت/.test(text), text);
      if (sev === 'removed') expect(/50/.test(text), text);
      await env.page.click('.vip-banner-x');
      await env.page.waitForSelector('.vip-banner', { state: 'detached', timeout: 10000 });
      await env.page.reload();
      await ready(env.page);
      await env.page.waitForTimeout(1500);
      expect(!(await env.page.isVisible('.vip-banner')), 'dismissal kept');
      return env;
    });
  }

  await test('vip: removal switches the free tier off and the recheck restores it', async () => {
    const w = world({ reg: 'approved', vip: 'active', recheckTo: 'active' });
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, mock: w.mock });
    await ready(env.page);
    await env.page.waitForFunction(() => window.__pc.useVipMember.getState().crypto?.status === 'active', null, { timeout: 15000 });
    await env.page.evaluate(() => { window.__vipEvents = []; window.addEventListener('pc:entitlements-changed', (e) => window.__vipEvents.push(e.detail)); });
    w.vip = 'removed';
    await pc(env.page, () => window.__pc.useVipMember.getState().refresh());
    await env.page.waitForSelector('[data-vip-banner=removed]', { timeout: 5000 });
    let events = await env.page.evaluate(() => window.__vipEvents);
    expect(events.length === 1 && events[0].vip === false && events[0].status === 'removed', JSON.stringify(events));
    await env.page.click('.vip-banner-cta');
    await settle(env.page);
    await env.page.waitForSelector('[data-vip-status=removed]', { timeout: 10000 });
    const todo = await env.page.textContent('.vip-todo');
    expect(/50/.test(todo) && /خودکار/.test(todo), `recovery path: ${todo}`);
    await env.page.click('text=شارژ کردم — همین الان بررسی کن');
    await env.page.waitForSelector('[data-vip-status=active]', { timeout: 5000 });
    events = await env.page.evaluate(() => window.__vipEvents);
    expect(events.length === 2 && events[1].vip === true, JSON.stringify(events));
    await pc(env.page, () => window.__pc.usePage.getState().close());
    await env.page.waitForTimeout(300);
    expect(!(await env.page.isVisible('.vip-banner')), 'banner gone after recovery');
    return env;
  });

  await test('vip: status page — gauge, deadline, notices, checklist, forex card', async () => {
    const w = world({ reg: 'approved', vip: 'critical', forex: 'approved' });
    const env = await open('vip/status?e2e=1', { signed: true, mock: w.mock });
    await settle(env.page);
    await env.page.waitForSelector('[data-vip-status=critical] .vip-gauge.critical', { timeout: 10000 });
    const card = await env.page.textContent('[data-vip-status=critical]');
    expect(/4\.2/.test(card) && /ساعت/.test(card) && /LBA1A23456/.test(card), card);
    expect(await env.page.$$eval('.vip-event', (els) => els.length) === 2, 'two notices');
    expect(await env.page.$$eval('.vip-check li', (els) => els.length) >= 5, 'checklist');
    expect(/فعال/.test(await env.page.textContent('.vip-forex-card')), 'forex VIP active');
    expect(await env.page.isVisible('.vip-forex-card >> text=اتصال تلگرام'), 'telegram link offered');
    return env;
  });

  await test('vip: Telegram links through the coineprofx.com window, and only that window is heard', async () => {
    // @CoineProFxBot's widget domain is coineprofx.com, so the widget runs in a window there
    // (CoineProFx frontend/website/public/tg-link.html); here a stand-in posts what the widget gives.
    const w = world({ reg: 'approved', vip: 'active', forex: 'approved' });
    let linked = null;
    const mock = (u, method, req) => {
      if (u.pathname === '/up/coineprofx/api/user/auth/link/telegram' && method === 'POST') { linked = JSON.parse(req.postData() ?? '{}'); return json({ merged: false }); }
      return w.mock(u, method, req);
    };
    const now = Math.floor(Date.now() / 1000);
    const env = await open('vip/status?e2e=1', { signed: true, mock, setup: async (ctx) => {
      await ctx.route('https://coineprofx.com/tg-link.html', (r) => r.fulfill({ status: 200, contentType: 'text/html', body: `<!doctype html><script>
        setTimeout(() => {
          window.opener.postMessage({ type: 'pc:telegram-auth', user: { id: 'x' } }, '*');
          window.opener.postMessage({ type: 'pc:telegram-auth', user: { id: 777, first_name: 'E2E', auth_date: ${now}, hash: 'abc' } }, '*');
        }, 400);
      </script>` }));
    } });
    await settle(env.page);
    await env.page.waitForSelector('.vip-tg button', { timeout: 10000 });
    const [pop] = await Promise.all([env.page.waitForEvent('popup'), env.page.click('.vip-tg button')]);
    expect(new URL(pop.url()).origin === 'https://coineprofx.com', `popup at ${pop.url()}`);
    // This page posting to itself is not the coineprofx.com window: ignored.
    await env.page.evaluate(() => window.postMessage({ type: 'pc:telegram-auth', user: { id: 1, auth_date: 1, hash: 'forged' } }, '*'));
    await env.page.waitForSelector('.vip-forex-card >> text=تلگرام به حساب فارکس وصل شد.', { timeout: 10000 });
    expect(linked && linked.id === 777 && linked.hash === 'abc' && linked.first_name === 'E2E', `linked ${JSON.stringify(linked)}`);
    return env;
  });

  await test('vip: performance — account record and public track record', async () => {
    const env = await open('performance?e2e=1', { signed: true });
    await settle(env.page);
    await env.page.waitForSelector('.vip-ring', { timeout: 10000 });
    expect(/68\.2%/.test(await env.page.textContent('.vip-ring')), 'win rate');
    expect(/۲۲/.test(await env.page.textContent('.vip-ring-meta')), 'denominator in Persian prose');
    await env.page.waitForSelector('.vip-walk path.vip-walk-line');
    expect(await env.page.$$eval('.vip-month', (els) => els.length) === 3, 'three months');
    await env.page.click('.page-tabs >> text=کارنامه‌ی سیگنال‌ها');
    await env.page.waitForSelector('.vip-ring', { timeout: 10000 });
    const guest = await open('performance?e2e=1');
    await settle(guest.page);
    await guest.page.waitForSelector('.vip-ring', { timeout: 10000 });
    expect((await guest.page.textContent('.vip-ring-meta')).includes('عمومی'), 'guest sees the public record');
    await guest.ctx.close();
    return env;
  });

  await test('vip: affiliate — link, code, referrals', async () => {
    const env = await open('affiliate?e2e=1', { signed: true });
    await settle(env.page);
    await env.page.waitForSelector('.vip-copy code >> text=E2ECODE', { timeout: 10000 });
    const text = await env.page.textContent('.page-body');
    expect(/E2ECODE/.test(text) && /LBA9X11111/.test(text) && /2 \/ 4/.test(text), text.slice(0, 300));
    return env;
  });

  await test('vip: copy trading — dashboard, positions, settings saved and tested', async () => {
    const seen = [];
    const env = await open('copytrade?e2e=1', { signed: true, mock: (u, method, req) => { if (u.pathname.includes('/api/user/v1/')) seen.push({ p: u.pathname, method, body: req.postData() }); return null; } });
    await settle(env.page);
    await env.page.waitForSelector('.vip-equity .big', { timeout: 10000 });
    expect(/1,250\.40/.test(await env.page.textContent('.vip-equity')), 'equity');
    await env.page.waitForSelector('[data-blocker=auto_entry_off]');
    await env.page.click('.page-tabs >> text=پوزیشن‌ها');
    for (const tab of ['روی صرافی', 'سفارش‌ها', 'تاریخچه']) {
      await env.page.click(`.page-tabs >> nth=1 >> text=${tab}`);
      await env.page.waitForSelector('.table tbody tr', { timeout: 5000 });
    }
    await env.page.click('.page-tabs >> nth=0 >> text=تنظیمات');
    await env.page.waitForSelector('.vip-settings', { timeout: 10000 });
    await env.page.selectOption('.vip-settings select >> nth=0', '10');
    await env.page.click('text=ذخیره‌ی تنظیمات');
    await env.page.waitForSelector('.vip-save [role=status].up', { timeout: 5000 });
    const put = seen.find((x) => x.method === 'PUT');
    expect(put && JSON.parse(put.body).leverageValue === 10 && Object.keys(JSON.parse(put.body)).length === 1, `put ${put?.body}`);
    await env.page.click('text=تست اتصال');
    await env.page.waitForSelector('#api [role=status] >> text=اتصال برقرار است', { timeout: 5000 });
    return env;
  });

  await test('vip: pages in English, dark, and on a phone', async () => {
    const w = world({ reg: 'pending_deposit', vip: 'low', forex: 'submitted' });
    const env = await open('vip?e2e=1', { signed: true, mock: w.mock, width: 390, height: 844, scheme: 'dark' });
    await settle(env.page);
    for (const id of ['vip-join', 'vip-status', 'performance', 'affiliate', 'copytrade']) {
      await pc(env.page, (x) => window.__pc.usePage.getState().open(x), id);
      await settle(env.page);
      await env.page.waitForTimeout(400);
      await noOverflow(env.page, `${id} fa`);
    }
    await pc(env.page, () => window.__pc.usePrefs.getState().setLang('en'));
    for (const id of ['vip-join', 'performance', 'vip-status']) {
      await pc(env.page, (x) => window.__pc.usePage.getState().open(x), id);
      await settle(env.page);
      await noOverflow(env.page, `${id} en`);
    }
    await env.page.waitForSelector('.vip-gauge', { timeout: 10000 });
    const en = await env.page.textContent('.page-body');
    expect(/Exchange balance/.test(en) && /What to do/.test(en), `English copy: ${en.slice(0, 200)}`);
    await pc(env.page, () => window.__pc.usePage.getState().close());
    await env.page.waitForSelector('[data-vip-banner=low]', { timeout: 10000 });
    await noOverflow(env.page, 'banner on a phone');
    return env;
  });

  await screens({ test, open, ready, fixtures });
}

// Review screenshots (not a test of behaviour): VIP_SHOTS=<dir> E2E_ONLY="vip: screens" node scripts/e2e/run.mjs
export async function screens({ test, open, ready, fixtures }) {
  const dir = process.env.VIP_SHOTS;
  if (!dir) return;
  const { json, vipMember, forexMember } = fixtures;
  const TYM = '/up/tradeyar/api/mobile/v1/';
  const mockFor = (reg, vip, forex) => (u) => {
    const p = u.pathname;
    if (p === `${TYM}membership/status`) return json({ status: reg, message_fa: reg === 'pending_deposit' ? 'حساب شما زیرمجموعه‌ی تریدیار تأیید شد، اما موجودی به حد لازم نرسیده است.' : 'هنوز UID صرافی را ثبت نکرده‌اید.', can_resubmit: true, uid: reg === 'awaiting_uid' ? null : 'LBA1A23456', vip: { status: vip, entitled: ['active', 'low', 'critical'].includes(vip) } });
    if (p === `${TYM}membership/vip`) return json(vipMember(vip));
    if (p === '/up/coineprofx/api/user/mobile/forex-vip') return json(forexMember(forex));
    return null;
  };
  const shot = async (name, path, o) => {
    const env = await open(path, { signed: true, mock: mockFor(o.reg, o.vip, o.forex ?? 'none'), width: o.phone ? 390 : 1440, height: o.phone ? 844 : 900, scheme: o.dark ? 'dark' : 'light' });
    await env.page.evaluate((x) => { try { localStorage.setItem('pc.vip.v1', JSON.stringify({ market: x.market ?? 'crypto', registered: x.step >= 1, deposited: x.step >= 2 })); } catch { /* */ } }, o);
    if (o.lang === 'en') { await env.page.waitForFunction(() => window.__pc, null, { timeout: 15000 }); await env.page.evaluate(() => window.__pc.usePrefs.getState().setLang('en')); }
    await env.page.reload();
    if (o.chart) await ready(env.page); else await env.page.waitForSelector('.page-body', { timeout: 10000 });
    await env.page.waitForTimeout(1800);
    await env.page.screenshot({ path: `${dir}/${name}.jpg`, type: 'jpeg', quality: 55 });
    await env.ctx.close();
  };
  await test('vip: screens', async () => {
    await shot('join-uid-fa-light', 'vip?e2e=1', { reg: 'awaiting_uid', vip: 'none', step: 2 });
    await shot('join-deposit-fa-dark', 'vip?e2e=1', { reg: 'pending_deposit', vip: 'pending_deposit', dark: true });
    await shot('join-forex-en-light', 'vip?e2e=1', { reg: 'awaiting_uid', vip: 'none', forex: 'submitted', market: 'forex', lang: 'en' });
    await shot('join-phone-fa-light', 'vip?e2e=1', { reg: 'awaiting_uid', vip: 'none', step: 0, phone: true });
    await shot('status-critical-fa-light', 'vip/status?e2e=1', { reg: 'approved', vip: 'critical', forex: 'approved' });
    await shot('status-removed-en-dark', 'vip/status?e2e=1', { reg: 'approved', vip: 'removed', lang: 'en', dark: true });
    await shot('status-low-phone-fa-dark', 'vip/status?e2e=1', { reg: 'approved', vip: 'low', phone: true, dark: true });
    await shot('banner-critical-fa-light', 'BTCUSDT/1h?e2e=1', { reg: 'approved', vip: 'critical', chart: true });
    await shot('banner-removed-phone-fa-dark', 'BTCUSDT/1h?e2e=1', { reg: 'approved', vip: 'removed', chart: true, phone: true, dark: true });
    await shot('performance-fa-light', 'performance?e2e=1', { reg: 'approved', vip: 'active' });
    await shot('copytrade-fa-dark', 'copytrade?e2e=1', { reg: 'approved', vip: 'active', dark: true });
    await shot('affiliate-en-light', 'affiliate?e2e=1', { reg: 'approved', vip: 'active', lang: 'en' });
    await shot('home-fa-light', 'home?e2e=1', { reg: 'approved', vip: 'low' });
    return null;
  });
}
