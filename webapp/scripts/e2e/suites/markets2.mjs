// Markets, heat map and order book (area M, wave 2): the market preview from the markets list, the
// watchlist and the screener; the heat map's settings, blocks, focus, coverage and tile figures;
// the markets list's «داغ» lens, filter sheet, pulse, headlines, open signals and top stories; the
// bulk candle pacer; and the order book's aggregation, cumulative figures, bid share, depth curve
// and «هشدار در این قیمت».
export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const settle = async (page) => {
    await page.waitForSelector('.page-body', { timeout: 10000 });
    await page.waitForFunction(() => { const b = document.querySelector('.page-body'); return b && !/در حال بارگذاری…|Loading…/.test(b.textContent ?? ''); }, null, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(250);
  };
  // Crypto prices with moves that differ, so «داغ» has a median to cut at.
  const MOVES = { BTCUSDT: [81000, 0.5, 9000], ETHUSDT: [2400, -6, 50000], SOLUSDT: [103, 3, 200000], BNBUSDT: [720, 1, 3000], XRPUSDT: [1.31, -2.5, 4e6], ADAUSDT: [0.22, 7, 1e7], DOGEUSDT: [0.08, -0.2, 2e7], TRXUSDT: [0.3, 0.1, 1e6], LTCUSDT: [90, 4, 10000], SUIUSDT: [1.0, -9, 1e6] };
  const prices = (u) => (u.pathname === '/api/crypto/prices'
    ? json({ ts: Date.now(), data: Object.entries(MOVES).map(([s, [p, c, v]]) => ({ s, p, t: Math.floor(Date.now() / 1000), c, h: p * 1.05, l: p * 0.93, v })) })
    : null);
  // The same, with no forex tickers: the lens and the filter over a known board.
  const cryptoOnly = (u) => prices(u) ?? (u.pathname === '/up/coineprofx/api/user/mobile/market/tickers' ? json({ tickers: [], cache_ttl_ms: 60000 }) : null);

  await test('markets2: a held row opens the preview — spans, the reading, star, milestone alert, chart', async () => {
    const env = await open('markets?e2e=1', { mock: prices });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.mk-table tr[data-symbol="BTCUSDT"]', { timeout: 15000 });
    // A right click is a hold.
    await page.click('.mk-table tr[data-symbol="BTCUSDT"]', { button: 'right' });
    await page.waitForSelector('.market-preview[data-symbol="BTCUSDT"]', { timeout: 5000 });
    expect(await pc(page, () => window.__pc.usePage.getState().page) === 'markets', 'the hold did not open the chart');
    const title = await page.textContent('.market-preview .mp-title .sym');
    expect(title === 'BTC/USDT', `title ${title}`);
    const price = await page.textContent('.market-preview .mp-price');
    expect(/^[\d,.]+$/.test(price) && !/[۰-۹]/.test(price), `price ${price}`);
    expect(/^\+0\.50%$/.test(await page.textContent('.market-preview .mp-pill')), 'move pill');
    const ranges = await page.$$eval('.mp-range', (bs) => bs.map((b) => b.textContent));
    expect(ranges.join(',') === '1D,1W,1M,3M,1Y,همه', `ranges ${ranges}`);
    expect(!(await page.$('.mp-range.on')), 'no span lit at first');
    // A span asks once, at its own bar length.
    const asked = [];
    page.on('request', (r) => { const u = new URL(r.url()); if (u.pathname.endsWith('/candles') && u.searchParams.get('symbol') === 'BTCUSDT') asked.push(u.searchParams.get('tf')); });
    await page.click('.mp-range[data-range="week"]');
    await page.waitForSelector('.mp-reading', { timeout: 10000 });
    expect(asked.includes('H1'), `asked ${asked}`);
    expect(/ · قدرت .+ · نوسان /.test(await page.textContent('.mp-reading')), 'the reading');
    await page.click('.mp-range[data-range="month"]');
    await page.click('.mp-range[data-range="week"]');
    expect(asked.filter((x) => x === 'H1').length === 1, `week asked again: ${asked}`);
    // Pointing along the line reads the bar under it, in Latin figures.
    const plot = await page.locator('.mp-plot').boundingBox();
    await page.mouse.move(plot.x + plot.width * 0.5, plot.y + plot.height / 2);
    await page.waitForFunction(() => /\d/.test(document.querySelector('.mp-readout b')?.textContent ?? ''), null, { timeout: 3000 });
    expect(await page.$('.mp-cross'), 'crosshair');
    // Star toggles the watchlist.
    const before = await pc(page, () => window.__pc.useWatchlist.getState().symbols.includes('BTCUSDT'));
    await page.click('.mp-action >> nth=0');
    expect((await pc(page, () => window.__pc.useWatchlist.getState().symbols.includes('BTCUSDT'))) === !before, 'star');
    // «اگر امروز این‌قدر حرکت کرد خبرم کنید»: one alert, one direction, today's move.
    expect(/اگر امروز این‌قدر حرکت کرد خبرم کنید/.test(await page.textContent('.mp-milestones')), 'milestone label');
    expect((await page.$$('.mp-chip')).length === 6, 'six chips');
    await page.click('.mp-chip[data-milestone="+5"]');
    await page.waitForSelector('.pc-toast >> text=هشدار ساخته شد', { timeout: 3000 });
    const a = await pc(page, () => window.__pc.useAlerts.getState().list.find((x) => x.symbol === 'BTCUSDT'));
    expect(a && a.condition === 'move_up_pct' && a.value === 5 && a.interval === '1D' && a.bars === 1 && a.freq === 'once', JSON.stringify(a));
    expect(!(await page.$('.market-preview')), 'the sheet closed');
    // The eye opens it too, and «چارت» opens the chart.
    await page.click('.mk-table tr[data-symbol="ETHUSDT"] .preview-btn');
    await page.waitForSelector('.market-preview[data-symbol="ETHUSDT"]');
    await page.click('.mp-open');
    await page.waitForFunction(() => !window.__pc.usePage.getState().page && window.__pc.usePrefs.getState().symbol === 'ETHUSDT', null, { timeout: 5000 });
    return env;
  });

  await test('markets2: the preview opens from the watchlist menu and a held screener row; Escape closes it', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.usePanels.getState().show('watchlist'));
    await page.waitForSelector('.wl-row[data-symbol="ETHUSDT"]', { timeout: 10000 });
    await page.click('.wl-row[data-symbol="ETHUSDT"]', { button: 'right' });
    await page.click('.wl-preview');
    await page.waitForSelector('.market-preview[data-symbol="ETHUSDT"]');
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('.market-preview'), null, { timeout: 3000 });
    await pc(page, () => window.__pc.usePanels.getState().show('screener'));
    await page.waitForSelector('.scr-row[data-symbol="SOLUSDT"]', { timeout: 10000 });
    const row = await page.locator('.scr-row[data-symbol="SOLUSDT"]').boundingBox();
    await page.mouse.move(row.x + 60, row.y + row.height / 2);
    await page.mouse.down();
    await page.waitForSelector('.market-preview[data-symbol="SOLUSDT"]', { timeout: 3000 });
    await page.mouse.up();
    await page.waitForTimeout(200);
    expect(await pc(page, () => window.__pc.usePrefs.getState().symbol) === 'BTCUSDT', 'the hold did not also open SOLUSDT');
    return env;
  });

  await test('markets2: «داغ», the filter sheet, the pulse with its reasons, headlines, signals and top stories', async () => {
    const env = await open('markets?e2e=1', { mock: cryptoOnly, signed: true });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.mk-table tr[data-symbol="BTCUSDT"]', { timeout: 15000 });
    // The pulse: volume figured, the other three a dash with a reason.
    const cells = await page.$$eval('.mk-cell b', (bs) => bs.map((b) => b.textContent));
    expect(cells[0] === '—' && /^\$[\d.,]+[KMBT]?$/.test(cells[1]) && cells[2] === '—' && cells[3] === '—', `pulse ${cells}`);
    await page.click('.mk-cell[data-cell="mood"]');
    await page.waitForSelector('.mk-pulse-sheet >> text=هنوز در دسترس نیست');
    expect(/بازار صعودی است/.test(await page.textContent('.mk-pulse-sheet')), 'breadth under the mood');
    await page.keyboard.press('Escape');
    // «داغ»: moved at least the median, by turnover. |moves| median (upper) = 3 → SOL, LTC, ETH, ADA, SUI, by turnover.
    await page.click('.market-filters .page-tabs >> text=داغ');
    const hot = await page.$$eval('.mk-table tbody tr', (rs) => rs.map((r) => r.getAttribute('data-symbol')));
    expect(hot.join(',') === 'ETHUSDT,SOLUSDT,ADAUSDT,SUIUSDT,LTCUSDT', `hot ${hot}`);
    // The filter sheet: a change band, its badge, a second tap clears; the clear-all appears only then.
    await page.click('.market-filters .page-tabs >> text=همه‌ی بازارها');
    await page.click('.mk-filter-btn');
    await page.waitForSelector('.mk-filter-sheet');
    expect(!(await page.$('.mk-clear')), 'no clear on an untouched sheet');
    await page.click('.mk-filter-sheet [data-band="down5"]');
    await page.click('.mk-filter-sheet [data-floor="m1"]');
    await page.waitForSelector('.mk-clear');
    await page.keyboard.press('Escape');
    expect((await page.textContent('.mk-filter-btn .badge')) === '2', 'badge');
    const down = await page.$$eval('.mk-table tbody tr', (rs) => rs.map((r) => r.getAttribute('data-symbol')));
    expect(down.sort().join(',') === 'ETHUSDT,SUIUSDT', `filtered ${down}`);
    await page.click('.mk-filter-btn');
    await page.click('.mk-clear');
    await page.keyboard.press('Escape');
    expect(!(await page.$('.mk-filter-btn .badge')), 'cleared');
    // Headlines, the open signals (a strip and a count per row), top stories.
    await page.waitForSelector('.mk-headline', { timeout: 10000 });
    await page.waitForSelector('.mk-strip', { timeout: 10000 });
    expect(/۳ سیگنال باز/.test(await page.textContent('.mk-strip')), await page.textContent('.mk-strip'));
    expect(/(BTCUSDT|ETHUSDT|XAUUSD) خرید/.test(await page.textContent('.mk-strip')), 'summary');
    expect((await page.textContent('.mk-table tr[data-symbol="BTCUSDT"] .mk-sig')).trim() === '1', 'row count');
    expect(/سرخط خبرها/.test(await page.textContent('.mk-stories')), 'top stories');
    await page.waitForSelector('.mk-story');
    await page.click('.mk-story >> nth=0');
    await page.waitForFunction(() => window.__pc.usePage.getState().page === 'news', null, { timeout: 5000 });
    return env;
  });

  await test('markets2: the heat map — blocks, focus, settings with notes, coverage, hatched unknowns, tile figures', async () => {
    const env = await open('heatmap?e2e=1', { mock: prices });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.heat-box .tile', { timeout: 15000 });
    // Grouped by class by default, with name strips; a strip focuses its block.
    await page.waitForSelector('.hm-group[data-bucket="c:crypto"]', { timeout: 5000 });
    await page.click('.hm-group[data-bucket="c:crypto"]');
    expect((await page.textContent('.hm-top h2')).trim() === 'کریپتو', 'focus title');
    const syms = await page.$$eval('.heat-box .tile', (ts) => ts.map((t) => t.getAttribute('data-symbol')));
    expect(syms.length > 0 && syms.every((s) => s.endsWith('USDT')), `focused ${syms}`);
    expect(/^[۰-۹]+ از [۰-۹]+ بازار$/.test((await page.textContent('.hm-subtitle')).trim()), 'Persian counts');
    await page.click('.hm-unfocus');
    // Settings: performance shows its window; gap is unanswerable until the bars land (or says so).
    await page.click('.hm-settings');
    await page.waitForSelector('.hm-settings-sheet');
    expect(/دامنه رنگ تا ±[\d.]+/.test(await page.textContent('.hm-settings-sheet')), 'scale in the header');
    expect(!(await page.$('[data-section="period"]')), 'no window under day change');
    await page.click('[data-section="colour"] [data-opt="performance"]');
    await page.waitForSelector('[data-section="period"]');
    await page.click('[data-section="period"] [data-opt="week"]');
    await page.click('[data-section="size"] [data-opt="liquidity"]');
    expect(/نه ارزش بازار/.test(await page.textContent('[data-section="size"]')), 'liquidity note');
    await page.click('[data-section="palette"] [data-opt="blind"]');
    expect(/رشد آبی و افت نارنجی/.test(await page.textContent('[data-section="palette"]')), 'palette note');
    await page.click('[data-section="grouping"] [data-opt="none"]');
    await page.click('[data-section="colour"] [data-opt="change"]');
    await page.keyboard.press('Escape');
    expect(!(await page.$('.hm-group')), 'no strips ungrouped');
    const kept = await pc(page, () => JSON.parse(localStorage.getItem('pc.heatmap.options.v2')));
    expect(kept.palette === 'blind' && kept.grouping === 'none' && kept.period === 'week', JSON.stringify(kept));
    // The legend: the ramp's ends written as tiles are, and the hatch labelled.
    expect(/بدون داده/.test(await page.textContent('.hm-legend')), 'unknown in the legend');
    // A hold (right click) opens the tile's figures; a funding row only where there is one.
    await page.click('.heat-box .tile[data-symbol="BTCUSDT"]', { button: 'right' });
    await page.waitForSelector('.hm-detail-sheet');
    expect((await page.textContent('.hm-detail-sheet h2')).trim() === 'BTC/USDT', 'detail title');
    for (const f of ['price', 'change', 'period', 'gap', 'open', 'previous', 'high', 'low', 'range', 'volatility', 'typical', 'volume', 'turnover']) expect(await page.$(`.hm-figure[data-figure="${f}"]`), `figure ${f}`);
    expect(!(await page.$('.hm-figure[data-figure="funding"]')), 'no funding row');
    expect((await page.textContent('.hm-figure[data-figure="change"] .num')).trim() === '+0.50%', 'day change');
    // The bars arrive: the period and the gap get figures.
    await page.waitForFunction(() => /\d/.test(document.querySelector('.hm-figure[data-figure="gap"] .num')?.textContent ?? ''), null, { timeout: 15000 });
    await page.click('.hm-open');
    await page.waitForFunction(() => !window.__pc.usePage.getState().page && window.__pc.usePrefs.getState().symbol === 'BTCUSDT', null, { timeout: 5000 });
    return env;
  });

  await test('markets2: the heat map hatches what it cannot answer and counts it', async () => {
    // Daily bars refused: «شکاف بازگشایی» has nothing to read, so every tile is hatched and the line says how many have a figure.
    const env = await open('heatmap?e2e=1', { mock: (u) => prices(u) ?? (/candles|academy\/chart/.test(u.pathname) ? json({ candles: [] }) : null) });
    const { page } = env;
    await settle(page);
    await page.waitForSelector('.heat-box .tile', { timeout: 15000 });
    await page.click('.hm-settings');
    await page.click('[data-section="colour"] [data-opt="gap"]');
    await page.waitForSelector('[data-section="colour"] >> text=هنوز هیچ بازاری این عدد را ندارد', { timeout: 15000 });
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => document.querySelectorAll('.heat-box .tile').length === document.querySelectorAll('.heat-box .tile.unknown').length, null, { timeout: 15000 });
    await page.waitForFunction(() => /^۰ از [۰-۹]+ خانه عدد دارد$/.test(document.querySelector('.hm-coverage')?.textContent?.trim() ?? ''), null, { timeout: 15000 });
    return env;
  });

  await test('markets2: bulk candle reads are paced at one start per interval per server', async () => {
    const env = await open('heatmap?e2e=1', { mock: prices, setup: async (ctx) => { await ctx.addInitScript(() => { try { localStorage.setItem('pc.e2e.pace', '400'); } catch { /* ignore */ } }); } });
    const { page } = env;
    const starts = [];
    page.on('request', (r) => { const u = new URL(r.url()); if (u.pathname === '/api/crypto/candles' && u.searchParams.get('tf') === 'D1') starts.push(Date.now()); });
    await settle(page);
    await page.waitForFunction(() => document.querySelectorAll('.heat-box .tile').length > 0, null, { timeout: 15000 });
    await page.waitForTimeout(2600);
    expect(starts.length >= 4, `only ${starts.length} reads`);
    // Each start is booked one interval after the last; a request's own set-up can shift one emission
    // a little, so the span is what is held to the pace: never more than one start per interval.
    const gaps = starts.slice(1).map((t, i) => t - starts[i]);
    expect(starts.at(-1) - starts[0] >= (starts.length - 1) * 350, `gaps ${gaps}`);
    expect(gaps.every((g) => g >= 100), `two starts together: ${gaps}`);
    return env;
  });

  await test('markets2: the order book — grouping steps, cumulative figures, bid share, depth curve, alert at a price', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.usePanels.getState().show('dom'));
    await page.waitForSelector('.dom-lvl.bid .dom-cell.size', { timeout: 10000 });
    expect(/^\d+%$/.test((await page.textContent('.bid-share b')).trim()), 'bid share');
    expect(/۲۰ سطح نزدیک اسپرد/.test(await page.textContent('.bid-share')), 'the band in the note');
    expect(await page.$('.depth-curve svg .depth-line.bid'), 'curve');
    const steps = await page.$$eval('.depth-steps [data-step]', (bs) => bs.map((b) => Number(b.getAttribute('data-step'))));
    expect(steps.length >= 2, `steps ${steps}`);
    const raw = await page.$$eval('.dom-lvl.bid .dom-price', (ps) => ps.length);
    await page.click(`.depth-steps [data-step="${steps[steps.length - 1]}"]`);
    await page.waitForFunction((n) => document.querySelectorAll('.dom-lvl.bid .dom-price').length < n, raw, { timeout: 5000 });
    expect(await page.$('text=سمت خرید به پایینِ گام'), 'step note');
    // Cumulative: the bid totals only grow away from the touch.
    await page.click('.depth-figure >> text=تجمعی');
    expect(/خرید تجمعی/.test(await page.textContent('.dom-head')), 'column names the figure');
    const totals = await page.$$eval('.dom-lvl.bid .dom-cell.size span', (s) => s.map((x) => Number(x.textContent)));
    expect(totals.every((v, i) => i === 0 || v >= totals[i - 1]), `totals ${totals}`);
    const kept = await pc(page, () => JSON.parse(localStorage.getItem('pc.dom.ladder.v1')).BTCUSDT);
    expect(kept.figure === 'cumulative' && kept.step === steps[steps.length - 1], JSON.stringify(kept));
    // «هشدار در این قیمت»: the alert dialog at that level's price.
    const opened = await pc(page, () => new Promise((resolve) => { window.addEventListener('pc:alert-dialog', (e) => resolve(e.detail), { once: true }); document.querySelector('.dom-lvl.ask .dom-alert').click(); }));
    expect(opened.symbol === 'BTCUSDT' && opened.price > 81000, JSON.stringify(opened));
    return env;
  });

  await test('markets2: the trade page book has the same tools', async () => {
    const env = await open('trade?e2e=1', { signed: true });
    const { page } = env;
    await settle(page);
    await page.click('.page-tabs >> text=دفتر سفارش');
    await page.waitForSelector('.dom-row.bid', { timeout: 10000 });
    await page.waitForSelector('.dom .bid-share');
    await page.waitForSelector('.dom .depth-curve');
    await page.waitForSelector('.dom .depth-steps [data-step]');
    const opened = await pc(page, () => new Promise((resolve) => { window.addEventListener('pc:alert-dialog', (e) => resolve(e.detail), { once: true }); document.querySelector('.dom-row.bid .dom-alert').click(); }));
    expect(opened.symbol === 'BTCUSDT' && opened.price < 81000, JSON.stringify(opened));
    return env;
  });
}
