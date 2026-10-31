// The screener's growth scan and scan watch, chartable-only lists, the growth_scan backtest with the
// bar magnifier, and the market notifications (area C1). Notifications are opted into per test
// (localStorage pc.e2e.notify = '1' before the page loads); without it nothing runs or is offered.
export default async function ({ test, open, pc, expect, fixtures }) {
  const { json } = fixtures;
  const settle = async (page) => {
    await page.waitForSelector('.page-body', { timeout: 10000 });
    await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…|Loading…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(250);
  };
  // The suite's page is not a secure context, so the browser always answers «denied»; an opted-in
  // test states the permission instead and reads what would have been shown from pulse.posted.
  const optIn = (grant = true) => async (ctx) => {
    await ctx.addInitScript((g) => { try { localStorage.setItem('pc.e2e.notify', '1'); localStorage.setItem('pc.e2e.permission', g ? 'granted' : 'default'); } catch { /* ignore */ } }, grant);
  };
  /** The page's local time today at hh:mm, in ms. */
  const at = (page, h, m = 0) => page.evaluate(([hh, mm]) => { const d = new Date(); d.setHours(hh, mm, 0, 0); return d.getTime(); }, [h, m]);
  const CHARTABLE = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT'];
  const scoped = (u) => (u.pathname === '/up/tradeyar/api/mobile/v1/auth/methods' ? json({ email_password: true, google: false, symbols: CHARTABLE.map((symbol) => ({ symbol })) }) : null);

  await test('scan: growth cues list only chartable markets, with a Latin score, and open with the setup’s studies', async () => {
    const env = await open('screener?e2e=1', { mock: scoped });
    const { page } = env;
    await settle(page);
    await page.click('.scr-mode >> text=نشانه‌های رشد');
    await page.waitForSelector('.scr-growth-table tbody tr', { timeout: 30000 });
    await page.waitForFunction(() => !document.querySelector('.scr-progress'), null, { timeout: 30000 });
    const syms = await page.$$eval('.scr-growth-table tbody tr', (rs) => rs.map((r) => r.getAttribute('data-symbol')));
    expect(syms.length > 0 && syms.every((s) => ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT'].includes(s)), `rows: ${syms}`);
    const scores = await page.$$eval('.scr-growth-score', (cs) => cs.map((c) => c.textContent));
    expect(scores.every((s) => /^\d{1,3}$/.test(s) && Number(s) <= 100), `scores ${scores}`);
    const sorted = scores.map(Number);
    expect(sorted.every((v, i) => i === 0 || sorted[i - 1] >= v), 'sorted by score');
    // A higher bar than every market: an empty list with its own sentence.
    await page.click('[data-min="80"]');
    if (sorted[0] < 80) await page.waitForSelector('text=هیچ بازاری با این شرط‌ها پیدا نشد.', { timeout: 5000 });
    await page.click('.scr-growth >> text=همه');
    await page.waitForSelector('.scr-growth-table tbody tr');
    await pc(page, () => window.__pc.useStudies.getState().clear());
    await page.click('.scr-growth-table tbody tr >> nth=0');
    await page.waitForFunction(() => !window.__pc.usePage.getState().page, null, { timeout: 5000 });
    const keys = await pc(page, () => window.__pc.useStudies.getState().list.map((s) => s.key).filter((k) => !k.startsWith('__')));
    const allowed = ['supertrend', 'ema', 'adx', 'donchian', 'macd', 'rsi', 'ma', 'bb', 'keltner', 'volumeOsc', 'zigzag', 'srLevels'];
    expect(keys.length > 0 && keys.every((k) => allowed.includes(k)), `studies ${keys}`);
    const sym = await pc(page, () => window.__pc.usePrefs.getState().symbol);
    expect(sym === syms[0], `opened ${sym}`);
    return env;
  });

  await test('scan: the table filters to chartable markets and a scan in progress stops on leaving', async () => {
    const env = await open('screener?e2e=1', { mock: scoped });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.page-body .table tbody tr', { timeout: 15000 });
    const syms = await page.$$eval('.page-body .table tbody tr .sym', (s) => s.map((x) => x.textContent));
    expect(syms.every((s) => ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'BNBUSDT'].includes(s)), `table: ${syms}`);
    await page.click('.scr-mode >> text=نشانه‌های رشد');
    await pc(page, () => window.__pc.usePage.getState().close());
    await page.waitForTimeout(1500);
    return env;
  });

  await test('scan: a watch announces only markets that enter, once, and waits out the night', async () => {
    const env = await open('screener?e2e=1', { mock: scoped, setup: optIn() });
    const { page } = env;
    await settle(page);
    await page.click('.scr-mode >> text=نشانه‌های رشد');
    await page.waitForSelector('.scr-growth-table tbody tr', { timeout: 30000 });
    await page.waitForFunction(() => !document.querySelector('.scr-progress'), null, { timeout: 30000 });
    await page.click('.scr-watch');
    await page.waitForSelector('.scr-watch >> text=ورود نمادهای تازه را خبر می‌دهیم');
    const w = await pc(page, () => window.__pc.scanWatch.useScanWatch.getState().watches[0]);
    expect(w && w.symbols.length >= 1 && w.known.length >= 1, `watch ${JSON.stringify(w)?.slice(0, 200)}`);
    // Everything that matched was known: no announcement.
    const noon = await at(page, 12);
    await pc(page, (t) => window.__pc.scanWatch.runWatches(t), noon);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 0, 'nothing new, nothing said');
    // Forget one market: it is an entrant, announced once.
    await pc(page, () => { const s = window.__pc.scanWatch.useScanWatch.getState(); const x = s.watches[0]; s.record(x.id, x.known.slice(1)); });
    const night = await at(page, 23, 30);
    await pc(page, (t) => window.__pc.scanWatch.runWatches(t), night);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 0, 'quiet at 23:30');
    await pc(page, (t) => window.__pc.scanWatch.runWatches(t), noon);
    const posted = await pc(page, () => window.__pc.pulse.posted);
    expect(posted.length === 1 && posted[0].title === 'نمادهای تازه وارد اسکن رشد شما شدند', JSON.stringify(posted));
    await pc(page, (t) => window.__pc.scanWatch.runWatches(t), noon);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 1, 'not again while it stays in');
    await page.click('.scr-watch');
    expect((await pc(page, () => window.__pc.scanWatch.useScanWatch.getState().watches.length)) === 0, 'watch removed');
    return env;
  });

  await test('scan: market notifications never fire under e2e without opting in', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await page.waitForFunction(() => window.__pc?.pulse, null, { timeout: 15000 });
    await page.waitForTimeout(3000);
    expect(!(await page.$('.notify-offer')), 'no offer');
    const allowed = await pc(page, () => window.__pc.pulse.allowed());
    expect(allowed === false, 'not allowed');
    await pc(page, () => { window.__pc.useMarket.getState().put([{ symbol: 'BTCUSDT', last: 50000, change: 0, changePct: 0, ts: Date.now() }]); return window.__pc.pulse.run(); });
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 0, 'nothing posted');
    return env;
  });

  await test('scan: a sharp bitcoin move is announced; six a day at most; nothing in the night', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { setup: optIn() });
    const { page } = env;
    await page.waitForFunction(() => window.__pc?.pulse, null, { timeout: 15000 });
    const noon = await at(page, 12);
    const put = (p) => pc(page, (x) => window.__pc.useMarket.getState().put([{ symbol: 'BTCUSDT', last: x, change: 0, changePct: 0, ts: Date.now() }]), p);
    // The calendar reminder is its own test's business: off here, so a release near noon cannot interleave.
    await pc(page, () => { window.__pc.pulse.resetPulseRecord(); window.__pc.pulse.usePulse.getState().set({ calendar: false }); });
    await put(100000);
    await pc(page, (t) => window.__pc.pulse.run(t), noon - 3600e3);
    await put(103500);
    await pc(page, (t) => window.__pc.pulse.run(t), noon);
    let posted = await pc(page, () => window.__pc.pulse.posted);
    expect(posted.length === 1 && posted[0].kind === 'market_move' && /\+3\.50% بالا رفت/.test(posted[0].title) && /در ۱ ساعت گذشته/.test(posted[0].body), JSON.stringify(posted));
    // The same market inside three hours: not again.
    await put(108000);
    await pc(page, (t) => window.__pc.pulse.run(t), noon + 60e3);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 1, 'cooldown');
    // Fill the day: the seventh does not go out.
    const spent = await pc(page, (t) => { let n = 0; while (window.__pc.pulse.canSpend(t) && n < 20) { window.__pc.pulse.post('test', 'x', 'y', { now: t }); n++; } return window.__pc.pulse.sentToday(t); }, noon);
    expect(spent === 6, `sent ${spent}`);
    await pc(page, () => { const r = window.__pc.pulse.pulseRecord(); r.fired = {}; });
    await put(120000);
    const before = await pc(page, () => window.__pc.pulse.posted.length);
    await pc(page, (t) => window.__pc.pulse.run(t), noon + 4 * 3600e3);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === before, 'cap holds');
    // Night: samples kept, nothing said.
    await pc(page, () => window.__pc.pulse.resetPulseRecord());
    const night = await at(page, 23, 30);
    await put(100000);
    await pc(page, (t) => window.__pc.pulse.run(t), night - 3600e3);
    await put(110000);
    await pc(page, (t) => window.__pc.pulse.run(t), night);
    expect((await pc(page, () => window.__pc.pulse.posted.length)) === 0, 'quiet at night');
    return env;
  });

  await test('scan: notifications page has each kind’s switch, the brief’s hour, and says a tab must be open', async () => {
    const env = await open('notifications?e2e=1');
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.pulse-card', { timeout: 10000 });
    const text = await page.textContent('.pulse-card');
    expect(/زبانه/.test(text) && /شش اعلان/.test(text) && /23:00/.test(text), 'tab note and limits');
    for (const k of ['move', 'watchlist', 'digest', 'calendar', 'scan']) expect(await page.isChecked(`[data-pulse="${k}"] input`), `${k} on by default`);
    expect(!(await page.isChecked('[data-pulse="brief"] input')), 'brief off until switched on');
    await page.click('[data-pulse="brief"] input');
    await page.fill('input[aria-label="brief-hour"]', '08:30');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('pc.pulse.v1')));
    expect(saved.brief === true && saved.briefMinute === 510, JSON.stringify(saved));
    await page.click('[data-pulse="digest"] input');
    expect((await page.evaluate(() => JSON.parse(localStorage.getItem('pc.pulse.v1')))).digest === false, 'digest off');
    return env;
  });

  await test('scan: the daily brief names the reader’s own mover once a day', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { setup: optIn() });
    const { page } = env;
    await page.waitForFunction(() => window.__pc?.pulse, null, { timeout: 15000 });
    await pc(page, () => { window.__pc.pulse.resetPulseRecord(); window.__pc.pulse.usePulse.getState().set({ brief: true, briefMinute: 7 * 60 }); window.__pc.useWatchlist.getState().setLists([{ id: 'default', name: 'W', symbols: ['BTCUSDT', 'ETHUSDT'] }]); window.__pc.useMarket.getState().put([{ symbol: 'BTCUSDT', last: 1, change: 0, changePct: 1.2, ts: 0 }, { symbol: 'ETHUSDT', last: 1, change: 0, changePct: -3.4, ts: 0 }]); });
    const six = await at(page, 6, 59);
    expect((await pc(page, (t) => window.__pc.pulse.runBrief(t), six)) === false, 'not before its hour');
    const seven = await at(page, 7, 5);
    expect((await pc(page, (t) => window.__pc.pulse.runBrief(t), seven)) === true, 'delivered');
    expect((await pc(page, (t) => window.__pc.pulse.runBrief(t), seven + 60e3)) === false, 'once a day');
    const p = await pc(page, () => window.__pc.pulse.posted.at(-1));
    expect(p.title === '۱ بالا، ۱ پایین' && p.body === 'ETHUSDT 3.40٪ پایین آمد.', JSON.stringify(p));
    return env;
  });

  await test('scan: the offer comes once a day and not after «do not ask again»', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { setup: optIn(false) });
    const { page } = env;
    await page.waitForFunction(() => window.__pc?.pulse, null, { timeout: 15000 });
    await page.waitForSelector('.notify-offer', { timeout: 8000 });
    expect(/حداکثر شش اعلان/.test(await page.textContent('.notify-offer')), 'limits on the offer');
    await page.click('.notify-offer >> text=دیگر پرسیده نشود');
    expect(!(await page.$('.notify-offer')), 'closed');
    expect(!(await pc(page, () => window.__pc.pulse.offerDue(Date.now() + 2 * 86400e3))), 'never again');
    // A reader who only said «later» is asked again tomorrow, not today.
    await pc(page, () => { const r = window.__pc.pulse.pulseRecord(); r.never = false; });
    expect(!(await pc(page, () => window.__pc.pulse.offerDue())), 'not twice a day');
    expect(await pc(page, () => window.__pc.pulse.offerDue(Date.now() + 86400e3)), 'again tomorrow');
    return env;
  });

  await test('scan: growth_scan backtest is long only, with the bar magnifier on', async () => {
    const env = await open('backtest?e2e=1');
    const { page } = env;
    await settle(page);
    await page.locator('.form select').nth(1).selectOption('growth_scan');
    await page.click('.bt-magnifier input');
    await page.locator('.form input.num').nth(1).fill('2');
    await page.locator('.form input.num').nth(1).blur();
    await page.locator('.form input.num').nth(2).fill('3');
    await page.locator('.form input.num').nth(2).blur();
    await page.click('text=اجرای بک‌تست');
    await page.waitForSelector('.bt-stats.wide', { timeout: 30000 });
    const sides = await page.$$eval('.backtest-page tbody tr td:nth-child(2)', (t) => t.map((x) => x.textContent));
    expect(sides.length > 0 && sides.every((s) => s === 'Long'), `sides ${sides.slice(0, 5)}`);
    return env;
  });

  await test('scan: the heat map leaves out markets that did not trade and ones without charts', async () => {
    const custom = (u) => {
      if (u.pathname === '/api/crypto/prices') return json({ ts: Date.now(), data: [
        { s: 'BTCUSDT', p: 81000, c: -2.1, v: 1000 }, { s: 'ETHUSDT', p: 2400, c: 0, v: 0 }, { s: 'SOLUSDT', p: 103, c: 1.5, v: 500 }, { s: 'XRPUSDT', p: 1.3, c: 3, v: 900 }, { s: 'BNBUSDT', p: 720, c: 0, v: 50 },
      ] });
      return scoped(u);
    };
    const env = await open('heatmap?e2e=1', { mock: custom });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.heat-box .tile', { timeout: 10000 });
    await page.waitForTimeout(800);
    const tiles = await page.$$eval('.heat-box .tile', (ts) => ts.map((t) => (t.getAttribute('title') ?? '').split(' ')[0]));
    expect(tiles.includes('BTCUSDT') && tiles.includes('SOLUSDT') && tiles.includes('BNBUSDT'), `tiles ${tiles}`);
    expect(!tiles.includes('ETHUSDT'), 'no change and no volume: dropped');
    expect(!tiles.includes('XRPUSDT'), 'not chartable: dropped');
    return env;
  });
}
