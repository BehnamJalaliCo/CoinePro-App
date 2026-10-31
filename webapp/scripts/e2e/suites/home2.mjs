// Area H, wave 2: the pages' «این صفحه چیست؟» card, Home's return loop and mood strip, the profile's
// standing, record, avatar and midnight theme, the news article's context and guest announcements,
// the Premium BEP20 warning and the forex-only reader, the sign-in's failure sentences and countdown,
// the deletion lists, the academy's phone form, the last-error card and the owner's banners.
export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const settle = async (page) => {
    await page.waitForSelector('.page-body', { timeout: 10000 });
    await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…|Loading…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(200);
  };
  const seed = (values) => async (ctx) => {
    await ctx.addInitScript((v) => { for (const [k, x] of Object.entries(v)) { try { if (localStorage.getItem(`seeded:${k}`) == null) { localStorage.setItem(k, x); localStorage.setItem(`seeded:${k}`, '1'); } } catch { /* ignore */ } } }, values);
  };
  const noOverflow = async (page, what) => {
    const over = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1 || [...document.querySelectorAll('.page-body')].some((b) => b.scrollWidth > b.clientWidth + 1));
    expect(!over, `${what}: horizontal overflow`);
  };

  await test('home2: «این صفحه چیست؟» — lead and pitfall, dismissed for good, back from the header, never over a control', async () => {
    const env = await open('markets?e2e=1');
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.teach-strip[data-teach="markets"]', { timeout: 10000 });
    const text = await page.textContent('.teach-strip');
    expect(text.includes('قیمت لحظه‌ای و تغییر ۲۴ ساعته‌ی همه‌ی نمادها') && text.includes('نه تغییر از اول امروز'), `card: ${text}`);
    // In the flow: nothing in the page sits under it.
    const box = await page.locator('.teach-strip').boundingBox();
    const covered = await page.evaluate(({ y, h }) => [...document.querySelectorAll('.page-inner button, .page-inner input, .page-inner a')].some((el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.top < y + h && r.bottom > y; }), { y: box.y, h: box.height });
    expect(!covered, 'a control sits under the card');
    await page.click('.teach-strip .teach-close');
    await page.waitForSelector('.teach-strip', { state: 'detached', timeout: 3000 });
    await page.goto('http://pc.local/next/markets?e2e=1', { waitUntil: 'domcontentloaded' });
    await settle(page);
    expect(!(await page.isVisible('.teach-strip')), 'dismissal did not persist');
    await page.click('.page-head .teach-what');
    await page.waitForSelector('.teach-strip[data-teach="markets"]', { timeout: 3000 });
    // A page with no surface has no card and no «؟».
    await pc(page, () => window.__pc.usePage.getState().open('profile'));
    await settle(page);
    expect(!(await page.isVisible('.teach-strip')) && !(await page.isVisible('.page-head .teach-what')), 'profile has a card');
    // English.
    await pc(page, () => { window.__pc.usePrefs.getState().setLang('en'); window.__pc.usePage.getState().open('alerts'); });
    await settle(page);
    expect((await page.textContent('.teach-strip')).includes('An alert only tells you'), 'english card');
    return env;
  });

  await test('home2: home — while you were away, today\'s challenge and streak, balance, the board, my list, open signals, feed', async () => {
    const day = (ms) => { const d = new Date(ms); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); };
    const today = day(Date.now());
    const env = await open('home?e2e=1', {
      signed: true,
      setup: seed({
        'pc.lastVisit.v1': String(Date.now() - 30 * 3600e3),
        'pc.arena.v1': JSON.stringify([{ epochDay: today - 2 }, { epochDay: today - 1 }, { epochDay: today }]),
        'pc.watchlist.v1': JSON.stringify(['ETHUSDT', 'SOLUSDT']),
      }),
    });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.home-challenge', { timeout: 10000 });
    const ch = await page.textContent('.home-challenge');
    expect(ch.includes('امروز') && ch.includes('۳ روز پشت سر هم') && ch.includes('انجامش بدهید'), `challenge: ${ch}`);
    await page.waitForSelector('.home-mrow', { timeout: 15000 });
    await page.waitForSelector('.mood .mood-bar', { timeout: 10000 });
    const mood = await page.textContent('.mood');
    expect(mood.includes('بازار امروز') && /بازار بالا، .* پایین/.test(mood) && mood.includes('بیشترین حرکت'), `mood: ${mood}`);
    await page.waitForSelector('.home-since', { timeout: 10000 });
    const since = await page.textContent('.home-since');
    expect(since.includes('وقتی نبودید') && since.includes('۱ روز'), `since: ${since}`);
    const hero = await page.textContent('.home-hero');
    expect(hero.includes('دارایی کل') && hero.includes('12,345.67') && hero.includes('۱ دارایی · پرتفوی'), `hero: ${hero}`);
    await page.waitForSelector('.feed-row[data-feed]', { timeout: 5000 });
    await page.click('.home-hero .ib');
    expect((await page.textContent('.home-hero')).includes('••••'), 'balance not hidden');
    expect(JSON.parse(await page.evaluate(() => localStorage.getItem('pc.profile.v1'))).hideBalance === true, 'hide not kept');
    await page.waitForSelector('.home-signals .home-signal', { timeout: 10000 });
    expect((await page.textContent('.home-signals')).includes('ورود'), 'signal levels');
    await page.click('.home-challenge .btn');
    await page.waitForTimeout(300);
    const after = await page.evaluate(() => location.pathname);
    expect(!after.endsWith('/home'), `challenge went nowhere: ${after}`);
    return env;
  });

  await test('home2: profile — where you stand, your record, the avatar composer and the midnight theme', async () => {
    const env = await open('profile?e2e=1', { signed: true, mock: (u) => (u.pathname === '/up/tradeyar/api/mobile/v1/entitlements' ? json({ premium: false, all: true, founding_member: true }) : null), setup: seed({ 'pc.journal.v1': JSON.stringify([{ id: 'j1', symbol: 'BTCUSDT', buy: true, entry: 1, exit: 2, size: 1, pnl: 1, emotion: '', note: '', lesson: '', tags: [], createdAt: Date.now() }]) }) });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.facts.standing .fact-row[data-fact="membership"]', { timeout: 10000 });
    const st = await page.textContent('.facts.standing');
    expect(st.includes('جایگاه شما') && st.includes('در انتظار شناسه‌ی کاربری') && st.includes('انجام نشده') && st.includes('عضو بنیان‌گذار'), `standing: ${st}`);
    expect(st.includes('نامشخص'), 'unknown plan is not drawn as unknown');
    const rec = await page.textContent('.facts.record');
    expect(rec.includes('کارنامه‌ی شما') && rec.includes('۱ معامله') && rec.includes('۳ از ۴۰ درس'), `record: ${rec}`);
    await page.click('.profile-hero .avatar-btn');
    await page.waitForSelector('.av-composer', { timeout: 3000 });
    await page.click('.av-composer .page-tabs >> text=نشان‌ها');
    await page.click('.av-cell[aria-label="موشک"]');
    await page.click('.av-ring[data-ring="buy"]');
    await page.click('.av-composer >> text=ذخیره');
    const saved = JSON.parse(await page.evaluate(() => localStorage.getItem('pc.profile.v1')));
    expect(saved.avatar === 'mark:rocket' && saved.ring === 'buy', `avatar ${JSON.stringify(saved)}`);
    await page.waitForSelector('.profile-hero .avatar-v[data-ring="buy"] .av-mark', { timeout: 3000 });
    await page.click('.page-tabs >> text=نیمه‌شب');
    expect((await page.evaluate(() => document.documentElement.getAttribute('data-theme'))) === 'midnight', 'midnight attribute');
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(/rgb\(0, 0, 0\)/.test(bg) || (await page.evaluate(() => getComputedStyle(document.querySelector('.page')).backgroundColor)) === 'rgb(0, 0, 0)', `ground ${bg}`);
    expect((await page.textContent('.profile')).includes('تاریک روی سیاه کامل'), 'midnight note');
    await noOverflow(page, 'profile');
    return env;
  });

  await test('home2: midnight puts the chart on true black', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await pc(env.page, () => window.__pc.usePrefs.getState().setTheme('midnight'));
    await env.page.waitForTimeout(400);
    const bg = await pc(env.page, () => window.__pc.useChartBus.getState().api.options().layout.background);
    expect(JSON.stringify(bg).includes('#000000'), `chart ground ${JSON.stringify(bg)}`);
    await pc(env.page, () => window.__pc.usePrefs.getState().setTheme('dark'));
    await env.page.waitForTimeout(300);
    const dark = await pc(env.page, () => window.__pc.useChartBus.getState().api.options().layout.background);
    expect(JSON.stringify(dark).includes('#0F0F0F'), `dark ground ${JSON.stringify(dark)}`);
    return env;
  });

  await test('home2: news — what the story bears on, the chart, more from the same market; announcements open to guests', async () => {
    const now = Date.now();
    const env = await open('market/news?e2e=1', {
      mock: (u) => {
        if (u.pathname === '/api/news') return json({ data: [1, 2, 3].map((i) => ({ id: i, slug: `s${i}`, source: 'e2e', titleFa: `خبر بیت‌کوین ${i}`, summaryFa: 'خلاصه', publishedAt: new Date(now - i * 3600e3).toISOString(), importance: 5 })) });
        if (u.pathname === '/up/tradeyar/api/mobile/v1/announcements') return json({ announcements: [{ id: 'x1', title: 'قطعی برنامه‌ریزی‌شده', summary: 'یکشنبه', published_at: new Date(now - 7200e3).toISOString(), url: 'http://insecure.example' }, { id: '', title: 'بی‌شناسه', published_at: new Date().toISOString() }] });
        return null;
      },
    });
    const { page } = env;
    await settle(page);
    await page.click('.news-card .news-text >> nth=0');
    await page.waitForSelector('.news-context', { timeout: 5000 });
    const ctx = await page.textContent('.news-context');
    expect(ctx.includes('این خبر به چه مربوط است') && ctx.includes('این خبر روی کریپتو اثر دارد.') && ctx.includes('نمایش روی چارت'), `context: ${ctx}`);
    expect((await page.textContent('.news-related')).includes('بیشتر از همین بازار') && (await page.locator('.news-related-row').count()) === 2, 'related');
    await page.click('.news-chart');
    await page.waitForFunction(() => /\/next\/BTCUSDT\//.test(location.pathname), null, { timeout: 5000 });
    await ready(page);
    // Back to the news, as a guest, on the announcements.
    await pc(page, () => window.__pc.usePage.getState().open('news'));
    await settle(page);
    await page.click('.page-tabs >> text=اطلاعیه‌ها');
    await page.waitForSelector('.news-card >> text=قطعی برنامه‌ریزی‌شده', { timeout: 5000 });
    expect(!(await page.isVisible('text=بی‌شناسه')), 'an announcement without an id was shown');
    expect((await page.textContent('.page-body')).includes('۱ اطلاعیه'), 'count');
    return env;
  });

  await test('home2: announcements — the empty list says so plainly, with no retry', async () => {
    const env = await open('market/news?e2e=1', { mock: (u) => (u.pathname === '/up/tradeyar/api/mobile/v1/announcements' ? json({ announcements: [] }) : null) });
    const { page } = env;
    await settle(page);
    await page.click('.page-tabs >> text=اطلاعیه‌ها');
    await page.waitForSelector('.ann-empty', { timeout: 5000 });
    const t = await page.textContent('.ann-empty');
    expect(t.includes('تا امروز اطلاعیه‌ای منتشر نشده.') && t.includes('خالی بودنش یعنی چیزی گفته نشده'), t);
    expect((await page.locator('.ann-empty button').count()) === 0, 'retry button on an empty list');
    return env;
  });

  await test('home2: premium — BEP20 warning; a forex-only reader is told where Premium lives and linked', async () => {
    const seen = [];
    const env = await open('pro?e2e=1', {
      setup: seed({ 'pc.sessions.v1': JSON.stringify({ coineprofx: { access: 'pch_fx', refresh: 'pch_fxr', refreshAt: Date.now() + 36e5, user: { name: 'FX' } } }) }),
      mock: (u, method) => {
        if (u.pathname === '/up/tradeyar/api/mobile/v1/auth/link/coinepro') { seen.push(method); return json({ access_token: 'pch_ty', refresh_token: 'pch_tyr', expires_in: 3600 }); }
        return null;
      },
    });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.pro-link-note', { timeout: 5000 });
    expect((await page.textContent('.pro-link-note')).includes('پرمیوم روی حساب کریپتو ثبت می‌شود'), 'link note');
    await page.click('.plan >> nth=0');
    await page.waitForSelector('.usdt-warning', { timeout: 5000 });
    expect((await page.textContent('.usdt-warning')).includes('فقط تتر روی شبکه‌ی BEP20 و دست‌کم همین مبلغ'), 'warning');
    await page.click('.pay .btn-primary >> text=خرید');
    await page.waitForSelector('.pay input.tx', { timeout: 5000 });
    expect(seen.length === 1, `link calls ${seen.length}`);
    return env;
  });

  await test('home2: sign-in — a server fault, an unreadable answer and too many are three sentences, with a countdown', async () => {
    let mode = 'fault';
    const reply = () => {
      if (mode === 'fault') return { status: 503, contentType: 'text/html', body: '<html>down</html>' };
      if (mode === 'unreadable') return { status: 200, contentType: 'text/html', body: '<html>captive portal</html>' };
      return { status: 429, contentType: 'application/json', headers: { 'Retry-After': '30' }, body: '{}' };
    };
    const env = await open('BTCUSDT/1h?e2e=1', { mock: (u) => (u.pathname === '/up/tradeyar/api/mobile/v1/auth/login' || u.pathname === '/up/coineprofx/api/user/auth/login' ? reply() : null) });
    const { page } = env;
    await ready(page);
    await page.click('.avatar');
    await page.click('.account-menu .menu-item >> text=ورود');
    await page.waitForSelector('.dialog.auth', { timeout: 5000 });
    await page.fill('.dialog.auth input[type=email]', 'a@example.com');
    await page.fill('.dialog.auth input[type=password]', 'secret-pass');
    await page.click('.dialog.auth button[type=submit]');
    await page.waitForSelector('.dialog.auth .error', { timeout: 10000 });
    const fault = await page.textContent('.dialog.auth .error');
    expect(fault.includes('سرور پاسخ داد ولی خطای خودش را برگرداند'), `fault: ${fault}`);
    mode = 'unreadable';
    await page.click('.dialog.auth button[type=submit]');
    await page.waitForFunction(() => document.querySelector('.dialog.auth .error')?.textContent.includes('نتوانست پاسخ را بخواند'), null, { timeout: 10000 });
    mode = 'many';
    await page.click('.dialog.auth button[type=submit]');
    await page.waitForFunction(() => document.querySelector('.dialog.auth .error')?.textContent.includes('تلاش‌های زیادی انجام شد'), null, { timeout: 10000 });
    const label = await page.textContent('.dialog.auth button[type=submit]');
    expect(/تلاش دوباره تا [۰-۹]+ ثانیه‌ی دیگر/.test(label) && await page.isDisabled('.dialog.auth button[type=submit]'), `countdown: ${label}`);
    return env;
  });

  await test('home2: delete account — what is deleted, kept, not deleted and stays here', async () => {
    const env = await open('account/delete?e2e=1', { signed: true });
    const { page } = env;
    await settle(page);
    const t = await page.textContent('.delete-account');
    for (const w of ['چه چیزی حذف می‌شود', 'چه چیزی نگه داشته می‌شود', 'چه چیزی حذف نمی‌شود', 'چه چیزی روی این مرورگر می‌ماند', 'حساب آکادمی و پیشرفت درس‌ها', 'لاگ امنیتی و ضدتقلب، حداکثر ۱۲ ماه', 'بند ۵']) expect(t.includes(w), `missing ${w}`);
    expect(await page.isDisabled('.delete-account .btn.sell'), 'armed before the word');
    await page.fill('.delete-account input', 'حذف');
    expect(!(await page.isDisabled('.delete-account .btn.sell')), 'not armed after the word');
    return env;
  });

  await test('home2: academy — a reader without a phone adds one and the pro levels unlock', async () => {
    let phone = null;
    const posted = [];
    const env = await open('academy?e2e=1', {
      signed: true,
      mock: (u, method, req) => {
        if (u.pathname === '/up/coineprofx/api/academy/me') return json({ username: 'e2e', tier: 'free', completed: 3, total_lessons: 40, progress_pct: 7.5, xp: 1, phone_number: phone, phone_required: !phone });
        if (u.pathname === '/up/coineprofx/api/academy/profile' && method === 'POST') { const b = JSON.parse(req.postData() ?? '{}'); posted.push(b); if (!/^09\d{9}$/.test(b.phone)) return json({ detail: 'شمارهٔ موبایلِ معتبر وارد کن.' }, 400); phone = b.phone; return json({ ok: true, phone_number: phone, phone_required: false }); }
        return null;
      },
    });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.academy-phone', { timeout: 10000 });
    expect((await page.textContent('.academy-phone')).includes('شماره‌ی موبایل لازم است'), 'phone card');
    await page.fill('.academy-phone input', '0912');
    await page.click('.academy-phone button');
    await page.waitForSelector('.academy-phone [role=alert]', { timeout: 5000 });
    await page.fill('.academy-phone input', '۰۹۱۲۳۴۵۶۷۸۹');
    await page.click('.academy-phone button');
    await page.waitForSelector('.academy-phone', { state: 'detached', timeout: 5000 });
    expect(posted.at(-1).phone === '09123456789', `posted ${JSON.stringify(posted)}`);
    return env;
  });

  await test('home2: safety — the last error report, copied, and cleared', async () => {
    const env = await open('launch-readiness?e2e=1', { setup: seed({ 'pc.lastError.v1': JSON.stringify({ at: Date.now() - 60e3, summary: 'TypeError: e2e is undefined', culprit: 'f (index.js:1:2)', trace: 'TypeError: e2e is undefined\n at f (index.js:1:2)' }) }) });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.crash-card', { timeout: 5000 });
    const t = await page.textContent('.crash-card');
    expect(t.includes('آخرین خطای برنامه') && t.includes('TypeError: e2e is undefined') && t.includes('ارسال گزارش') && t.includes('کپی گزارش'), t);
    await env.ctx.grantPermissions(['clipboard-read', 'clipboard-write']).catch(() => {});
    await page.click('.crash-card >> text=کپی گزارش');
    await page.waitForSelector('.crash-card [role=status]', { timeout: 3000 });
    await page.click('.crash-card >> text=پاک کردن');
    await page.waitForSelector('.crash-card', { state: 'detached', timeout: 3000 });
    expect((await page.evaluate(() => localStorage.getItem('pc.lastError.v1'))) === null, 'not cleared');
    return env;
  });

  await test('home2: owner — maintenance and update banners and a campaign card from the check-in; dismissed for good', async () => {
    const events = [];
    let hellos = 0;
    const env = await open('BTCUSDT/1h?e2e=1', {
      mock: (u, method, req) => {
        if (u.pathname === '/api/app/hello' && method === 'POST') {
          hellos++;
          const b = JSON.parse(req.postData() ?? '{}');
          if (b.platform !== 'web' || !b.install_id) return json({ detail: 'bad' }, 400);
          return json({ server_time: 0, messages: [{ id: 41, kind: 'offer', priority: 'high', title: 'تخفیف پاییز', body: 'پرمیوم با تخفیف', cta: 'دیدن', link: 'coinepro://pro', coupon: 'PC-FALL', notify: true, ends_at: null }], config: { maintenance: { on: true, message_fa: '', message_en: '' }, update_nudge: { on: true, message_fa: 'نسخه‌ی تازه' }, min_version_code: 2_000_000_000, hello_every_hours: 6 } });
        }
        if (u.pathname === '/api/app/event') { events.push(JSON.parse(req.postData() ?? '{}')); return json({ ok: true }); }
        return null;
      },
    });
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.owner-banners .owner-card[data-owner="41"]', { timeout: 10000 });
    const t = await page.textContent('.owner-banners');
    expect(t.includes('سرورها در حال به‌روزرسانی‌اند') && t.includes('نسخه‌ی تازه') && t.includes('به‌روزرسانی') && t.includes('کد PC-FALL') && t.includes('تخفیف پاییز'), t);
    await page.waitForTimeout(300);
    expect(events.some((e) => e.message_id === 41 && e.kind === 'delivered'), `events ${JSON.stringify(events)}`);
    await page.click('.owner-card button[aria-label="بستن"]');
    await page.waitForSelector('.owner-card', { state: 'detached', timeout: 3000 });
    await page.waitForTimeout(300);
    expect(events.some((e) => e.message_id === 41 && e.kind === 'dismissed'), 'dismiss event');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await ready(page);
    await page.waitForSelector('.owner-bar.warn', { timeout: 5000 });
    expect(!(await page.isVisible('.owner-card')), 'dismissed card came back');
    expect(hellos === 1, `checked in ${hellos} times`);
    // The chart still has its room: the banners take their own rows above it.
    const chart = await page.locator('.chart-host').first().boundingBox();
    const bar = await page.locator('.owner-banners').boundingBox();
    expect(chart.y >= bar.y + bar.height - 1, 'banners cover the chart');
    return env;
  });
}
