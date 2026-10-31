// The shell (area B2): the offline banner, the saved pictures and their age, the alert pill before
// the first price check, browser Back and Forward, one menu door per feature with the Pro card, the
// markets hub switch, the chart's failure screen, the bottom dock's size and picture-in-picture.
// The service worker is skipped under automation (vip/InstallPrompt.tsx); its routing is unit-tested
// in src/__tests__/shell.test.ts.
export default async function ({ test, open, ready, pc, expect }) {
  /** A network the test can cut: while `down`, every /api and /up request fails as offline. */
  const network = () => {
    const n = { down: false };
    n.setup = async (ctx) => {
      await ctx.route('http://pc.local/**', (r) => {
        const p = new URL(r.request().url()).pathname;
        if (n.down && (p.startsWith('/api/') || p.startsWith('/up/'))) return r.abort('internetdisconnected');
        return r.fallback();
      });
    };
    return n;
  };
  const settle = async (page) => {
    await page.waitForSelector('.page-body', { timeout: 10000 });
    await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…|Loading…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
  };
  const path = (page) => page.evaluate(() => location.pathname);

  await test('shell: the offline banner comes and goes with the network', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    expect(!(await env.page.isVisible('.offline-bar')), 'banner while online');
    await env.ctx.setOffline(true);
    await env.page.waitForSelector('.offline-bar', { timeout: 5000 });
    const text = await env.page.textContent('.offline-bar');
    expect(text.includes('اینترنت این دستگاه قطع است') && text.includes('قیمت‌ها همان آخرین چیزی است که رسیده'), `banner: ${text}`);
    await env.ctx.setOffline(false);
    await env.page.waitForSelector('.offline-bar', { state: 'detached', timeout: 5000 });
    return env;
  });

  await test('shell: saved bars, tickers and news show «ذخیره‌شده · ۲ ساعت پیش» when the network fails', async () => {
    const net = network();
    const env = await open('BTCUSDT/1h?e2e=1', { setup: net.setup });
    await ready(env.page);
    // Everything that arrived is saved with its time.
    await env.page.waitForFunction(() => ['pc.saved.v1:bars:BTCUSDT|60', 'pc.saved.v1:tickers:crypto'].every((k) => localStorage.getItem(k)), null, { timeout: 10000 });
    await pc(env.page, () => window.__pc.usePage.getState().open('news'));
    await settle(env.page);
    await env.page.waitForFunction(() => localStorage.getItem('pc.saved.v1:news:crypto'), null, { timeout: 10000 });
    expect(!(await env.page.isVisible('.saved-line')), 'a saved line over live data');
    await pc(env.page, () => window.__pc.usePage.getState().close());
    // Two hours old, then the network goes and the page is opened again.
    await pc(env.page, () => {
      for (const k of Object.keys(localStorage).filter((x) => x.startsWith('pc.saved.v1:'))) {
        const v = JSON.parse(localStorage.getItem(k)); v.at = Date.now() - 2 * 3600e3 - 60e3; localStorage.setItem(k, JSON.stringify(v));
      }
    });
    net.down = true;
    // The address drops ?e2e=1 once read, so the page is opened again by its full address.
    await env.page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1', { waitUntil: 'domcontentloaded' });
    await ready(env.page);
    await env.page.waitForSelector('.saved-line.on-chart', { timeout: 10000 });
    const chartLine = await env.page.textContent('.saved-line.on-chart');
    expect(chartLine.includes('ذخیره‌شده · ۲ ساعت پیش'), `chart: ${chartLine}`);
    const bars = await pc(env.page, () => window.__pc.useChartBus.getState().bars.length);
    expect(bars >= 100, `saved bars drawn: ${bars}`);
    // The watchlist's prices are the saved ones, and say so.
    await env.page.waitForFunction(() => [...document.querySelectorAll('.saved-line')].some((e) => !e.classList.contains('on-chart') && e.textContent.includes('۲ ساعت پیش')), null, { timeout: 5000 });
    await pc(env.page, () => window.__pc.usePage.getState().open('news'));
    await settle(env.page);
    await env.page.waitForSelector('.page-body .saved-line', { timeout: 10000 });
    expect((await env.page.textContent('.page-body .saved-line')).includes('۲ ساعت پیش'), 'news age');
    expect((await env.page.textContent('.page-body')).includes('خبر آزمایشی بیت‌کوین'), 'saved story shown');
    await pc(env.page, () => window.__pc.usePage.getState().close());
    // The network is back: fresh pictures replace the saved ones and the lines leave by themselves.
    net.down = false;
    await env.page.evaluate(() => window.dispatchEvent(new Event('online')));
    await env.page.waitForSelector('.saved-line.on-chart', { state: 'detached', timeout: 15000 });
    await env.page.waitForFunction(() => !document.querySelector('.saved-line'), null, { timeout: 20000 });
    return env;
  });

  await test('shell: an alert says «هنوز بررسی نشده» until the first price check', async () => {
    const net = network();
    net.down = true;
    const env = await open('BTCUSDT/1h?e2e=1', { setup: net.setup });
    await env.page.waitForFunction(() => window.__pc, null, { timeout: 10000 });
    await pc(env.page, () => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 999999 }));
    await pc(env.page, () => window.__pc.usePage.getState().open('alerts'));
    await settle(env.page);
    await env.page.waitForSelector('.badge.unchecked', { timeout: 5000 });
    expect((await env.page.textContent('.badge.unchecked')).includes('هنوز بررسی نشده'), 'pill text');
    net.down = false;
    await env.page.waitForSelector('.badge.unchecked', { state: 'detached', timeout: 20000 });
    expect((await pc(env.page, () => window.__pc.useAlerts.getState().checkedAt)) > 0, 'checkedAt set');
    return env;
  });

  await test('shell: a chart that did not load offers «نماد دیگر» and retries by itself', async () => {
    const net = network();
    net.down = true;
    const env = await open('SOLUSDT/1h?e2e=1', { setup: net.setup });
    await env.page.waitForSelector('.chart-fail', { timeout: 20000 });
    const text = await env.page.textContent('.chart-fail');
    expect(/تلاش دوباره‌ی خودکار تا [۰-۹]+ ثانیه‌ی دیگر/.test(text), `auto retry: ${text}`);
    await env.page.click('.chart-fail >> text=نماد دیگر');
    await env.page.waitForSelector('.dialog.ss', { timeout: 5000 });
    await env.page.keyboard.press('Escape');
    await env.page.waitForSelector('.dialog.ss', { state: 'detached', timeout: 5000 });
    net.down = false;
    await env.page.waitForSelector('.chart-fail', { state: 'detached', timeout: 25000 });
    await ready(env.page);
    return env;
  });

  await test('shell: browser Back closes a page, Forward opens it again', async () => {
    const env = await open('ETHUSDT/4h?e2e=1');
    await ready(env.page);
    await env.page.click('.menu-btn');
    await env.page.waitForSelector('.drawer', { timeout: 5000 });
    await env.page.click('.drawer-item >> text=اخبار بازار');
    await settle(env.page);
    expect((await path(env.page)) === '/next/market/news', `news: ${await path(env.page)}`);
    await env.page.goBack();
    await env.page.waitForSelector('.page', { state: 'detached', timeout: 5000 });
    expect((await path(env.page)) === '/next/ETHUSDT/4h', `back: ${await path(env.page)}`);
    await env.page.goForward();
    await settle(env.page);
    expect((await path(env.page)) === '/next/market/news', `forward: ${await path(env.page)}`);
    // A hub face rewrites the entry: Back from the heat map is the chart, not the market list.
    await pc(env.page, () => window.__pc.usePage.getState().open('markets'));
    await settle(env.page);
    await env.page.click('.hub-switch >> text=هیت‌مپ');
    await env.page.waitForSelector('.heat-box', { timeout: 10000 });
    expect((await path(env.page)) === '/next/heatmap', `heatmap: ${await path(env.page)}`);
    await env.page.goBack();
    await settle(env.page);
    expect((await path(env.page)) === '/next/market/news', `back over the hub: ${await path(env.page)}`);
    // Closed from inside the app: the address walks back to the chart as it is now.
    await pc(env.page, () => window.__pc.usePrefs.getState().setSymbol('BTCUSDT'));
    await env.page.keyboard.press('Escape');
    await env.page.waitForSelector('.page', { state: 'detached', timeout: 5000 });
    await env.page.waitForFunction(() => location.pathname === '/next/BTCUSDT/4h', null, { timeout: 5000 });
    expect((await pc(env.page, () => window.__pc.usePrefs.getState().symbol)) === 'BTCUSDT', 'symbol kept');
    return env;
  });

  await test('shell: the menu has one door per feature, the Pro card and exchange membership', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await env.page.click('.menu-btn');
    await env.page.waitForSelector('.drawer', { timeout: 5000 });
    const rows = await env.page.$$eval('.drawer-row', (els) => els.map((e) => e.textContent.trim()));
    for (const dup of ['خانه‌ی بازار', 'مرکز راهنما', 'میانبرهای صفحه‌کلید']) expect(!rows.some((r) => r.includes(dup)), `duplicate door «${dup}»`);
    const items = await env.page.$$eval('.drawer-item', (els) => els.map((e) => e.textContent.trim()));
    expect(new Set(items).size === items.length, `repeated rows: ${items}`);
    expect(items.includes('عضویت صرافی'), 'exchange membership row');
    for (const face of ['هیت‌مپ بازار', 'اسکرینر', 'پرو چارت پرمیوم']) expect(!items.includes(face), `${face} listed`);
    const first = await env.page.$eval('.drawer', (d) => [...d.querySelectorAll('.drawer-pro, .drawer-search, .drawer-item')].map((e) => e.className.split(' ')[0])[0]);
    expect(first === 'drawer-pro', `Pro card not on top: ${first}`);
    expect((await env.page.textContent('.drawer-pro')).includes('تا ۱۶ چارت کنار هم'), 'Pro card line');
    // Search still finds a page the menu does not list.
    await env.page.fill('.drawer-search input', 'اسکرینر');
    expect((await env.page.locator('.drawer-item').count()) >= 1, 'search finds the screener');
    await env.page.fill('.drawer-search input', '');
    await env.page.click('.drawer-pro');
    await settle(env.page);
    expect((await path(env.page)) === '/next/pro', `pro: ${await path(env.page)}`);
    return env;
  });

  await test('shell: the help page links the academy', async () => {
    const env = await open('tutorials?e2e=1');
    await settle(env.page);
    await env.page.click('.help-academy');
    await settle(env.page);
    expect((await path(env.page)) === '/next/academy', `academy: ${await path(env.page)}`);
    return env;
  });

  await test('shell: markets, heat map and screener share one switch', async () => {
    const env = await open('markets?e2e=1');
    await settle(env.page);
    const tabs = await env.page.$$eval('.hub-switch [role=tab]', (els) => els.map((e) => [e.textContent.trim(), e.getAttribute('aria-selected')]));
    expect(JSON.stringify(tabs) === JSON.stringify([['بازارها', 'true'], ['هیت‌مپ', 'false'], ['اسکرینر', 'false']]), `tabs: ${JSON.stringify(tabs)}`);
    await env.page.click('.hub-switch >> text=هیت‌مپ');
    await env.page.waitForSelector('.heat-box', { timeout: 10000 });
    await env.page.click('.hub-switch >> text=اسکرینر');
    await settle(env.page);
    expect((await path(env.page)) === '/next/screener', `screener: ${await path(env.page)}`);
    expect((await env.page.getAttribute('.hub-switch [role=tab]:nth-child(3)', 'aria-selected')) === 'true', 'screener selected');
    await env.page.click('.hub-switch >> text=بازارها');
    await env.page.waitForSelector('.pulse', { timeout: 10000 });
    return env;
  });

  await test('shell: the bottom dock opens at 320 and never drags below 200', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await env.page.click('.ws-bottom-tabs >> text=یادداشت‌ها');
    await env.page.waitForSelector('.ws-bottom-body', { timeout: 5000 });
    const h0 = await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height);
    expect(Math.abs(h0 - 320) < 2, `default ${h0}`);
    const g = await env.page.locator('.dock-grip').boundingBox();
    await env.page.mouse.move(g.x + 200, g.y + 4); await env.page.mouse.down(); await env.page.mouse.move(g.x + 200, g.y + 400, { steps: 6 }); await env.page.mouse.up();
    const h1 = await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height);
    expect(Math.abs(h1 - 200) < 2, `floor ${h1}`);
    return env;
  });

  await test('shell: «تماشای پیوسته» opens a stamp-sized window; no button where the browser has none', async () => {
    const plain = await open('BTCUSDT/1h?e2e=1');
    await ready(plain.page);
    expect((await plain.page.locator('.keep-watching').count()) === 0, 'button shown without Document Picture-in-Picture');
    await plain.ctx.close();
    // A stand-in for the browser's Document Picture-in-Picture: a real second window.
    const env = await open('BTCUSDT/1h?e2e=1', { setup: (ctx) => ctx.addInitScript(() => {
      window.documentPictureInPicture = { window: null, requestWindow(o) { const w = window.open('', 'pcpip', `width=${o?.width ?? 300},height=${o?.height ?? 170}`); this.window = w; return Promise.resolve(w); } };
    }) });
    await ready(env.page);
    expect((await env.page.getAttribute('.keep-watching', 'title')) === 'تماشای پیوسته', 'button title');
    const popup = env.page.waitForEvent('popup');
    await env.page.click('.keep-watching');
    const win = await popup;
    await win.waitForSelector('.pip-price', { timeout: 5000 });
    expect((await win.textContent('.pip-head')).includes('BTCUSDT'), 'ticker');
    expect((await win.textContent('.pip-head')).includes('1H'), 'interval');
    expect(/^[\d,.]+$/.test((await win.textContent('.pip-price')).trim()), 'price in Latin digits');
    expect(/^[+−]\d+\.\d{2}%$/.test((await win.textContent('.pip-chg')).trim()), 'change');
    expect((await win.locator('.pip-line path').count()) === 1, 'line');
    await win.waitForSelector('.pip-cd', { timeout: 3000 });
    expect(/^\d+:\d{2}(:\d{2})?$/.test((await win.textContent('.pip-cd')).trim()), 'countdown');
    expect((await env.page.getAttribute('.keep-watching', 'aria-pressed')) === 'true', 'button pressed');
    await env.page.click('.keep-watching');
    await env.page.waitForFunction(() => document.querySelector('.keep-watching')?.getAttribute('aria-pressed') === 'false', null, { timeout: 3000 });
    return env;
  });
}
