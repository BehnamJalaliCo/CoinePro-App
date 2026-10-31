// Area T (wave 2): NamaScript's «bring your own script» (paste sheet, prompt kit, lessons, «this script is
// mine», sharing on the board), scripts in community posts, the community's copy / founding / name
// change, the AI's evidence and sources, and «معامله با کارگزار» behind the top bar's Trade.
// The test origin is plain http, where browsers give no clipboard: an in-memory one stands in for it.
const clip = async (ctx) => {
  await ctx.addInitScript(() => {
    let text = '';
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async (t) => { text = String(t); }, readText: async () => text } });
  });
};
const settle = async (page) => {
  await page.waitForSelector('.page-body', { timeout: 15000 });
  await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(250);
};
const setClip = (page, text) => page.evaluate((t) => navigator.clipboard.writeText(t), text);
const getClip = (page) => page.evaluate(() => navigator.clipboard.readText());
const NAMA = '// nama 1\n// id: e2escript23\n// name: Golden cross\n// description: Two averages\n// colour: FF22C55E\n// pane: price\n// ---\nfast = ta.ema(close, 9)\nslow = ta.ema(close, 21)\nplot(fast, "fast")\nplot(slow, "slow", color.red)';
const SHARED = `یک اسکریپت از من: Golden cross\nTwo averages\n\n${NAMA}\n`;

export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;

  await test('script2: a paste is read, repaired into its own tab, and undone in one tap', async () => {
    const env = await open('script?e2e=1', { setup: clip });
    const { page } = env;
    await settle(page);
    const raw = 'len = input.int(۲۰, “طول”)\navg = sma(close, len)\nplot(avg, "میانگین", color.grey)';
    await setClip(page, raw);
    await page.click('.script-paste');
    await page.waitForSelector('.sx-paste .sx-fix', { timeout: 5000 });
    const head = await page.textContent('.sx-paste .dialog-head');
    expect(head.includes('چسباندن اسکریپت') && head.includes('به عنوان نمااسکریپت خوانده شد'), `head: ${head}`);
    expect((await page.textContent('.sx-paste h3')).includes('مورد اصلاح شد'), 'the fixes are counted');
    expect((await page.$$('.sx-fix')).length >= 4, 'each fix listed');
    expect((await page.textContent('.sx-fix[data-fix="persian-digits"]')).includes('خط ۱'), 'the line, in Persian digits');
    const fixed = await pc(page, () => window.__pc.useScript.getState().source);
    expect(fixed.includes('ta.sma(close, len)') && fixed.includes('input.int(20, "طول")') && fixed.includes('color.gray'), `fixed: ${fixed}`);
    await page.click('.sx-undo');
    await page.waitForSelector('.sx-paste', { state: 'detached', timeout: 3000 });
    expect(await pc(page, () => window.__pc.useScript.getState().source) === raw, 'the undo puts the paste back as it was');
    return env;
  });

  await test('script2: a sentence becomes a template picker with its numbers in place', async () => {
    const env = await open('script?e2e=1', { setup: clip });
    const { page } = env;
    await settle(page);
    await setClip(page, 'وقتی میانگین ۹ از ۲۱ رد شد بخر');
    await page.click('.script-paste');
    await page.waitForSelector('.sx-template', { timeout: 5000 });
    expect((await page.textContent('.sx-paste')).includes('کدام‌یک را می‌خواستید؟'), 'the picker asks');
    await page.click('.sx-template[data-template="ma-cross"] button');
    const code = await pc(page, () => window.__pc.useScript.getState().source);
    expect(code.includes('input.int(9, "دوره‌ی تند"') && code.includes('input.int(21, "دوره‌ی کند"') && code.includes('alertcondition'), `template: ${code}`);
    await setClip(page, '');
    await page.click('.script-paste');
    await page.waitForSelector('text=چیزی در حافظه‌ی موقت نیست.', { timeout: 3000 });
    return env;
  });

  await test('script2: the prompt kit shows the whole prompt, copies it raw, and takes the answer back', async () => {
    const env = await open('script?e2e=1', { setup: clip });
    const { page } = env;
    await settle(page);
    await page.click('.script-prompt-kit');
    await page.waitForSelector('.sx-prompt', { timeout: 5000 });
    expect((await page.textContent('.sx-prompt .dialog-head')).includes('نسخه‌ی متن ۲'), 'the version in Persian digits');
    const spec = await page.textContent('.sx-prompt .sx-code');
    expect(spec.startsWith('--- NamaScript specification') && spec.includes('BTCUSDT on the'), 'the spec names the chart');
    expect((await page.$$('.sx-prompt .btn')).length >= 4, 'copy, ChatGPT, Claude, paste');
    await page.click('.sx-copy');
    await page.waitForSelector('text=کپی شد. در دستیار بچسبانید.', { timeout: 3000 });
    const copied = await getClip(page);
    expect(copied.startsWith('یک اندیکاتور به زبان «نمااسکریپت»') && copied.includes('(نسخه‌ی راهنما: 2)'), 'the raw prompt is copied');
    await setClip(page, 'r = ta.rsi(close, 14)\nplot(r, "RSI")');
    await page.click('.sx-paste-result');
    await page.waitForSelector('.sx-clean', { timeout: 5000 });
    expect((await page.textContent('.sx-clean')) === 'چیزی برای اصلاح نبود.', 'a clean answer says so');
    return env;
  });

  await test('script2: where the browser gives no clipboard, the paste lands in a box', async () => {
    const env = await open('script?e2e=1');
    const { page } = env;
    await settle(page);
    await page.click('.script-paste');
    await page.waitForSelector('.sx-paste-box textarea', { timeout: 5000 });
    expect((await page.textContent('.sx-paste-box')).includes('هر چیزی را اینجا بچسبانید'), 'the hint');
    await page.fill('.sx-paste-box textarea', '//@version=5\nindicator("x")\nbull = close > open and volume > 0\nplot(close)');
    await page.click('.sx-paste-box .btn-primary');
    await page.waitForSelector('.sx-paste .sx-unsupported', { timeout: 5000 });
    const t = await page.textContent('.sx-paste');
    expect(t.includes('به عنوان اسکریپت تریدینگ‌ویو خوانده و ترجمه شد') && t.includes('به صورت توضیح باقی ماندند'), `pine: ${t}`);
    return env;
  });

  await test('script2: the lessons open in order and an example goes to the editor', async () => {
    const env = await open('script?e2e=1');
    const { page } = env;
    await settle(page);
    await page.click('.page-tabs >> text=آموزش');
    await page.waitForSelector('.sx-lesson.on[data-lesson="what"]', { timeout: 5000 });
    expect((await page.$$('.sx-lesson')).length === 12, 'twelve lessons');
    expect((await page.textContent('.sx-lesson[data-lesson="series"] .sx-lesson-head')).includes('۳.'), 'numbered in Persian');
    await page.click('.sx-lesson[data-lesson="crosses"] .sx-lesson-head');
    await page.click('.sx-lesson[data-lesson="crosses"] .sx-try');
    await page.waitForSelector('.script-editor', { timeout: 5000 });
    expect((await pc(page, () => window.__pc.useScript.getState().source)).includes('ta.crossover(fast, slow)'), 'the example is in the editor');
    return env;
  });

  await test('script2: «this script is mine» colours and panels the script, exports it, and shares it on the board', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { setup: clip });
    const { page } = env;
    await ready(page);
    await pc(page, () => { const s = window.__pc.useScript.getState(); s.newTab('fast = ta.ema(close, 9)\nplot(fast, "fast")', 'Mine'); s.apply(); window.__pc.usePage.getState().open('script'); });
    await settle(page);
    await page.click('.sx-mine-head');
    await page.fill('.sx-description', 'Nine-bar average');
    await page.fill('.sx-tags', 'trend، ema');
    await page.click('.sx-swatch[data-colour="red"]');
    await page.click('.sx-switch input');
    await page.waitForFunction(() => window.__pc.useScript.getState().result?.plots?.[0]?.color === '#ef4444', null, { timeout: 10000 });
    const st = await pc(page, () => { const s = window.__pc.useScript.getState(); return { meta: s.runMeta, tab: s.tabs.find((t) => t.id === s.active).meta }; });
    expect(st.meta.ownPane === true && st.tab.tags.join('|') === 'trend|ema' && st.tab.description === 'Nine-bar average', `meta: ${JSON.stringify(st)}`);
    expect((await page.textContent('.sx-mine')).includes('هنوز نسخه‌ی قبلی نیست'), 'no versions yet');
    await page.click('.sx-export');
    await page.waitForSelector('text=کپی شد. هر جا بخواهید بچسبانید و نگه دارید.', { timeout: 3000 });
    const file = await getClip(page);
    expect(file.startsWith('// nama 1') && file.includes('// description: Nine-bar average') && file.includes('// colour: FFEF4444') && file.includes('// pane: own'), `file: ${file}`);
    // «ورودی گرفتن» of a whole file opens it with its name and fields.
    await setClip(page, NAMA);
    await page.click('.sx-import');
    await page.waitForFunction(() => window.__pc.useScript.getState().tabs.some((t) => t.name === 'Golden cross' && t.meta?.colour === '#22C55E'), null, { timeout: 3000 });
    await pc(page, () => { const s = window.__pc.useScript.getState(); s.switchTab(s.tabs.find((t) => t.name === 'Mine').id); });
    await page.click('.sx-share-board');
    await page.waitForURL(/\/next\/community\/compose$/, { timeout: 8000 });
    await page.waitForSelector('.community textarea', { timeout: 10000 });
    const body = await page.inputValue('.community textarea');
    expect(body.startsWith('یک اسکریپت از من: Mine\nNine-bar average') && body.includes('// nama 1'), `post: ${body.slice(0, 120)}`);
    return env;
  });

  const board = (u) => {
    const p = u.pathname;
    const CM = '/up/tradeyar/api/v1/public/app-community/';
    if (p === `${CM}posts`) return json({ posts: [{ id: 1, content: SHARED, author: 'e2e', category: 'تحلیل', likes: 0, replies_count: 0, created_at: new Date().toISOString(), status: 'published' }] });
    if (p === `${CM}posts/1`) return json({ post: { id: 1, content: SHARED, author: 'ali', category: 'تحلیل', likes: 0, created_at: new Date().toISOString() }, replies: [] });
    return null;
  };

  await test('script2: a post carrying a script shows its code, installs on the chart in one tap, and says so after', async () => {
    const env = await open('community?e2e=1', { mock: board, setup: async (ctx) => { await clip(ctx); await ctx.addInitScript(() => { try { localStorage.setItem('pc.founding.v1', '1'); } catch { /* ignore */ } }); } });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.post .post-code.feed', { timeout: 10000 });
    expect((await page.textContent('.post .post-code')).split('\n').length === 4, 'four lines in the feed');
    expect((await page.textContent('.post .post-body')).startsWith('یک اسکریپت از من'), 'the prose half');
    // The reader's own post carries the founding mark.
    await page.waitForSelector('.post .founding >> text=عضو بنیان‌گذار', { timeout: 5000 });
    await page.click('.post .post-menu-btn');
    await page.click('.post-copy');
    await page.waitForSelector('text=متن رونوشت شد.', { timeout: 3000 });
    expect((await getClip(page)) === SHARED, 'the whole text copied');
    await pc(page, () => window.__pc.usePage.getState().open('thread', '1'));
    await settle(page);
    await page.waitForSelector('.add-script', { timeout: 10000 });
    expect((await page.textContent('.add-script')) === '«Golden cross» را به چارت من اضافه کنید', 'the add button names the script');
    expect((await page.textContent('.post-code')).includes('plot(slow, "slow", color.red)'), 'whole in the thread');
    expect(!(await page.$('.script-installed')), 'not installed yet');
    await page.click('.add-script');
    await ready(page);
    const s = await pc(page, () => { const x = window.__pc.useScript.getState(); return { on: x.on, name: x.tabs.find((t) => t.id === x.active).name, colour: x.runMeta?.colour }; });
    expect(s.on && s.name === 'Golden cross' && s.colour === '#22C55E', `installed: ${JSON.stringify(s)}`);
    await pc(page, () => window.__pc.usePage.getState().open('thread', '1'));
    await settle(page);
    await page.waitForSelector('.script-installed >> text=شما این را اضافه کرده‌اید', { timeout: 5000 });
    await page.click('.open-script');
    await page.waitForURL(/\/next\/script$/, { timeout: 5000 });
    return env;
  });

  await test('script2: the composer names the reader and changes the name', async () => {
    const calls = [];
    const env = await open('community/compose?e2e=1', { mock: (u, method, req) => { if (u.pathname.endsWith('/app-community/me') && method === 'POST') { calls.push(req.postDataJSON()); return json({ registered: true, display_name: 'e2e2' }); } return null; } });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('text=شما: e2e', { timeout: 10000 });
    await page.click('.change-name');
    await page.waitForSelector('.name-card', { timeout: 3000 });
    expect((await page.textContent('.name-card')).includes('بین ۲ تا ۲۴ نویسه'), 'the rule in Persian digits');
    expect(await page.isDisabled('.name-card .btn-primary'), 'the same name cannot be saved');
    await page.fill('.name-card input', 'e2e2');
    await page.click('.name-card .btn-primary');
    await page.waitForFunction(() => !document.querySelector('.name-card'), null, { timeout: 5000 });
    expect(calls.length === 1 && calls[0].display_name === 'e2e2', `calls: ${JSON.stringify(calls)}`);
    return env;
  });

  await test('script2: the AI setup shows the technical evidence it was given', async () => {
    const env = await open('ai?e2e=1', { signed: true, mock: (u) => (u.pathname.endsWith('/result/job1') ? json({ id: 'job1', status: 'done', result: { symbol: 'BTCUSDT', direction: 'long', timeframe: '1h', entry: 81000, sl: 79800, tp1: 82500, confidence: 0.74, rr: 1.25, rationale: 'ساختار صعودی.', price_now: 81000, snapshot: { ema_20: 80500, ema_50: 79900, rsi_14: 58.34, swing_high_20: 82000, swing_low_20: 78000, change_pct_20: 2.5 } } }) : null) });
    const { page } = env;
    await settle(page);
    await page.click('text=ساخت ستاپ');
    await page.waitForSelector('.ai-evidence', { timeout: 20000 });
    const t = await page.textContent('.ai-evidence');
    expect(t.includes('شواهد تکنیکال') && t.includes('80,500.00') && t.includes('58.3') && t.includes('+2.50%'), `evidence: ${t}`);
    expect(t.includes('—'), 'a missing reading is a dash');
    expect(await page.getAttribute('.ev-range', 'aria-valuenow') === '75', 'price at three quarters of the range');
    return env;
  });

  await test('script2: the assistant lists its sources, or says it has none', async () => {
    let withSources = false;
    const env = await open('ai/assistant?e2e=1', { signed: true, mock: (u) => (u.pathname === '/up/coineprofx/api/user/ai/chat' && withSources ? json({ answer: 'بله.', remaining: 8, context: [{ kind: 'active_signal', title: 'XAUUSD خرید', summary: 'ورود 4100', source: 'signals', as_of: '10:00', freshness: 'stale', signal_id: 11 }] }) : null) });
    const { page } = env;
    await settle(page);
    await page.fill('.ai-ask textarea', 'روند؟');
    await page.click('.ai-ask button');
    await page.waitForSelector('.ai-no-context', { timeout: 10000 });
    expect((await page.textContent('.ai-no-context')) === 'هیچ منبع ساختاریافته‌ای به این پاسخ پیوست نشده است.', 'no sources said plainly');
    withSources = true;
    await page.fill('.ai-ask textarea', 'سیگنال؟');
    await page.click('.ai-ask button');
    await page.waitForSelector('.ai-source[data-kind="active_signal"]', { timeout: 10000 });
    const t = await page.textContent('.ai-sources');
    expect(t.includes('منابعِ استفاده‌شده') && t.includes('سیگنال فعال') && t.includes('قدیمی') && t.includes('signals · 10:00'), `sources: ${t}`);
    await page.click('.ai-source >> text=باز کردن سیگنال');
    await page.waitForURL(/\/next\/signal\/11$/, { timeout: 5000 });
    return env;
  });

  await test('script2: Trade opens the partners sheet — this app first, then OneRoyal, LBank and Ourbit', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.trade-btn');
    await page.waitForSelector('.partners-sheet .partner-card', { timeout: 5000 });
    const ids = await page.$$eval('.partner-card', (els) => els.map((e) => e.getAttribute('data-partner')));
    expect(ids.join(',') === 'oneroyal,lbank,ourbit', `order: ${ids}`);
    await page.waitForFunction(() => document.querySelector('.partner-card[data-partner="lbank"] a')?.getAttribute('href') === 'https://lbank.com/ref/E2E', null, { timeout: 5000 });
    const hrefs = await page.$$eval('.partner-open', (els) => els.map((e) => [e.getAttribute('href'), e.getAttribute('target'), e.getAttribute('rel')]));
    expect(hrefs[0][0] === 'https://vc.cabinet.oneroyal.com/links/go/e2e', `the forex VIP's broker link: ${hrefs[0][0]}`);
    expect(hrefs.every(([h, t, r]) => h.startsWith('https://') && t === '_blank' && r.includes('noopener')), `links: ${JSON.stringify(hrefs)}`);
    expect((await page.textContent('.partners-sheet')).includes('این‌ها معرفی هستند، نه توصیه.'), 'the disclaimer');
    expect(await page.$$eval('.partner-logo path', (p) => p.length) > 3, 'the marks are drawn');
    await page.click('.partners-sheet .trade-here');
    await page.waitForURL(/\/next\/paper-trade$/, { timeout: 5000 });
    return env;
  });
}
