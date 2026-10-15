// The right-side widgets (area C): the widget bar, watchlist and details, alerts, news, calendar,
// screener, signals, AI, hotlists, the DOM ladder, notes, the notification log and the order
// panel. PANELS_SHOTS=<dir> also saves a picture of every state, in both languages and themes.
import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

export default async function ({ test, open, ready, pc, expect }) {
  const start = async (path = 'BTCUSDT/1h?e2e=1', opts = {}) => { const env = await open(path, opts); await ready(env.page); return env; };
  const show = (page, id) => pc(page, (x) => window.__pc.usePanels.getState().show(x), id);
  const state = (page, fn, arg) => pc(page, fn, arg);

  await test('panels: widget bar opens every widget; alert badge; notifications and help', async () => {
    const env = await start();
    const { page } = env;
    for (const [id, sel] of [['hotlists', '.hotlists .hot-cols'], ['dom', '.dom-lvl'], ['notes', '.notes-area'], ['log', '.notif-log'], ['order', '.op-go']]) {
      await page.click(`.ws-wbar [data-panel="${id}"]`);
      await page.waitForSelector(sel, { timeout: 10000 });
    }
    await state(page, () => { const a = window.__pc.useAlerts.getState(); a.create({ symbol: 'ETHUSDT', condition: 'above', value: 999999 }); a.create({ symbol: 'SOLUSDT', condition: 'below', value: 0.0001 }); });
    await page.waitForSelector('.ws-wbar [data-panel="alerts"] .wbar-badge', { timeout: 3000 });
    expect((await page.textContent('.ws-wbar [data-panel="alerts"] .wbar-badge')) === '۲', 'badge count');
    await page.click('.ws-wbar .wbar-btn >> nth=-2');
    expect(await state(page, () => window.__pc.usePage.getState().page) === 'notifications', 'notifications page');
    await state(page, () => window.__pc.usePage.getState().close());
    await page.click('.ws-wbar .wbar-btn >> nth=-1');
    expect(await state(page, () => window.__pc.usePage.getState().page) === 'help', 'help page');
    return env;
  });

  await test('panels: the split between watchlist and details drags, persists and folds', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'watchlist');
    const box = await page.locator('.wl-split').boundingBox();
    const before = await state(page, () => window.__pc.usePanels.getState().split);
    await page.mouse.move(box.x + 50, box.y + 2);
    await page.mouse.down();
    await page.mouse.move(box.x + 50, box.y + 120, { steps: 6 });
    await page.mouse.up();
    const after = await state(page, () => window.__pc.usePanels.getState().split);
    expect(after > before + 0.05, `split ${before} → ${after}`);
    await page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1'); await ready(page);
    expect(Math.abs((await state(page, () => window.__pc.usePanels.getState().split)) - after) < 0.001, 'split kept');
    await page.click('.details-head .lt[aria-expanded="true"]');
    await page.waitForSelector('.details.folded');
    await page.click('.details-head .lt[aria-expanded="false"]');
    await page.waitForSelector('.details:not(.folded) .details-price');
    return env;
  });

  await test('panels: watchlist sections — add, move a symbol in, fold with a count, export ###', async () => {
    const env = await start();
    const { page } = env;
    page.on('dialog', (d) => void d.accept('MAJORS').catch(() => {}));
    await page.click('.wl-title');
    await page.click('.wl-lists >> text=/افزودن بخش|Add section/');
    await page.waitForSelector('.wl-section-title >> text=MAJORS');
    await page.click('.wl-row[data-symbol="EURUSD"]', { button: 'right' });
    await page.click('.wl-ctx >> text=MAJORS');
    const sec = await state(page, () => window.__pc.useWatchlist.getState().lists[0].sections[0]);
    expect(sec.name === 'MAJORS' && sec.symbols.join() === 'EURUSD', JSON.stringify(sec));
    await page.click('.wl-section-title >> text=MAJORS');
    await page.waitForSelector('.wl-section-title.closed .wl-sec-count');
    expect((await page.textContent('.wl-section-title.closed .wl-sec-count')) === '۱', 'count on a folded section');
    expect(!(await page.$('.wl-row[data-symbol="EURUSD"]')), 'folded section still shows its rows');
    await page.click('.wl-head button[aria-haspopup] >> nth=1');
    const [dl] = await Promise.all([page.waitForEvent('download'), page.click('.wl-more >> text=/\\.txt/')]);
    const text = readFileSync(await dl.path(), 'utf8');
    expect(text.includes('###MAJORS,EURUSD'), text);
    await state(page, () => { const w = window.__pc.useWatchlist.getState(); for (const n of ['Gold', 'Alts', 'Majors FX', 'Indices', 'Energy']) w.createList(n, ['BTCUSDT']); });
    await page.click('.wl-title');
    await page.fill('.wl-lists .menu-form input', 'alt');
    await page.waitForFunction(() => [...document.querySelectorAll('.wl-lists .menu-item .grow')].filter((e) => /Alts|Gold|Indices|Energy|Majors FX/.test(e.textContent)).length === 1);
    return env;
  });

  await test('panels: watchlist columns, list view, logos, group-by and a kept sort', async () => {
    const env = await start();
    const { page } = env;
    await page.click('.wl-head button[aria-haspopup] >> nth=1');
    await page.click('.wl-more label.check >> nth=3');
    await page.keyboard.press('Escape');
    expect((await page.$$('.wl-cols button')).length === 5, 'volume column');
    await page.click('.wl-cols button >> nth=3');
    expect(await state(page, () => window.__pc.useWatchView.getState().sort?.key) === 'changePct', 'sort by chg%');
    await state(page, () => window.__pc.useWatchView.getState().setView({ groupBy: 'none', logos: false, description: true, dot: true }));
    expect(!(await page.$('.wl-section-title')), 'no group titles');
    await page.waitForSelector('.wl-row .wl-dot');
    expect(!(await page.$('.wl-row .logo')), 'logos still shown');
    await page.click('.wl-head .ib[aria-pressed] >> nth=1');
    await page.waitForSelector('.wl.listview .wl-list-figs');
    await page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1'); await ready(page);
    const v = await state(page, () => { const s = window.__pc.useWatchView.getState(); return { sort: s.sort, table: s.table, cols: s.columns, group: s.groupBy }; });
    expect(v.sort?.key === 'changePct' && !v.table && v.cols.includes('volume') && v.group === 'none', JSON.stringify(v));
    return env;
  });

  await test('panels: watchlist row menu, multi-select, keyboard walk, drag, in-panel add', async () => {
    const env = await start();
    const { page } = env;
    await page.click('.wl-row[data-symbol="ETHUSDT"]', { button: 'right' });
    await page.click('.wl-ctx >> text=/به مقایسه|to compare/');
    expect((await state(page, () => window.__pc.useCompare.getState().symbols)).includes('ETHUSDT'), 'compare');
    await page.click('.wl-row[data-symbol="SOLUSDT"]', { button: 'right' });
    await page.click('.wl-ctx >> text=/هشدار روی|Add alert on/');
    await page.waitForSelector('.alert-dlg .alert-dlg-title >> text=SOLUSDT');
    await page.keyboard.press('Escape');
    await page.click('.wl-row[data-symbol="XRPUSDT"]', { button: 'right' });
    await page.click('.wl-ctx >> text=/معامله‌ی|Trade/');
    expect(await state(page, () => window.__pc.usePanels.getState().open) === 'order', 'trade opens the order panel');
    expect(await state(page, () => window.__pc.usePrefs.getState().symbol) === 'XRPUSDT', 'trade sets the symbol');
    await show(page, 'watchlist');
    await state(page, () => window.__pc.usePrefs.getState().setSymbol('BTCUSDT'));
    await page.focus('.wl-body');
    await page.keyboard.press('ArrowDown');
    expect(await state(page, () => window.__pc.usePrefs.getState().symbol) === 'ETHUSDT', 'arrow down walks the list');
    await state(page, () => window.__pc.useWatchView.getState().setView({ groupBy: 'sections', sort: null }));
    await page.dragAndDrop('.wl-row[data-symbol="SOLUSDT"]', '.wl-row[data-symbol="BTCUSDT"]');
    const order = await state(page, () => window.__pc.useWatchlist.getState().symbols.slice(0, 2).join());
    expect(order === 'SOLUSDT,BTCUSDT', `drag: ${order}`);
    await page.click('.wl-row[data-symbol="BNBUSDT"]', { modifiers: ['Control'] });
    await page.click('.wl-row[data-symbol="ADAUSDT"]', { modifiers: ['Control'] });
    await page.waitForSelector('.wl-selbar');
    await page.click('.wl-selbar .lt >> nth=1');
    const left = await state(page, () => window.__pc.useWatchlist.getState().symbols);
    expect(!left.includes('BNBUSDT') && !left.includes('ADAUSDT'), 'bulk remove');
    await page.click('.wl-head > .ib:not(.wl-title) >> nth=0');
    await page.fill('.wl-add input', 'SUI');
    await page.click('.wl-add-row >> nth=0');
    expect((await state(page, () => window.__pc.useWatchlist.getState().symbols)).includes('SUIUSDT'), 'in-panel add');
    return env;
  });

  await test('panels: alert on the whole list; open in screener and heat map; unflag all; CSV', async () => {
    const env = await start();
    const { page } = env;
    await state(page, () => window.__pc.useWatchlist.getState().flagMany(['BTCUSDT', 'ETHUSDT'], 1));
    await page.click('.wl-title');
    await page.click('.wl-lists >> text=/هشدار روی کل فهرست|Add alert on the list/');
    await page.waitForSelector('.alert-dlg');
    await page.click('.alert-dlg .btn-primary');
    const n = await state(page, () => { const l = window.__pc.useAlerts.getState().list; return { n: l.length, groups: new Set(l.map((a) => a.group)).size, cond: l[0]?.condition }; });
    const want = await state(page, () => window.__pc.useWatchlist.getState().symbols.length);
    expect(n.n === want && n.groups === 1 && n.cond === 'move_up_pct', JSON.stringify(n));
    await page.click('.wl-head button[aria-haspopup] >> nth=1');
    const [dl] = await Promise.all([page.waitForEvent('download'), page.click('.wl-more >> text=/\\.csv/')]);
    expect(readFileSync(await dl.path(), 'utf8').includes('BTCUSDT'), 'csv');
    await page.click('.wl-head button[aria-haspopup] >> nth=1');
    await page.click('.wl-more >> text=/پرچم‌ها|Unflag all/');
    expect(Object.keys(await state(page, () => window.__pc.useWatchlist.getState().flags)).length === 0, 'unflag all');
    await page.click('.wl-head button[aria-haspopup] >> nth=1');
    await page.click('.wl-more >> text=/اسکرینر|screener/');
    const pg = await state(page, () => ({ page: window.__pc.usePage.getState().page, param: window.__pc.usePage.getState().param }));
    expect(pg.page === 'screener' && pg.param === 'wl:default', JSON.stringify(pg));
    await page.waitForSelector('.wl-filter.on', { timeout: 10000 });
    await state(page, () => window.__pc.usePage.getState().open('heatmap', 'wl:default'));
    await page.waitForSelector('.heat .wl-filter.on', { timeout: 10000 });
    return env;
  });

  await test('panels: prices flash on a tick', async () => {
    const env = await start();
    await env.page.waitForSelector('.wl [data-flash]', { timeout: 15000 });
    await state(env.page, () => window.__pc.usePanels.getState().show('screener'));
    await env.page.waitForSelector('.screener [data-flash]', { timeout: 15000 });
    return env;
  });

  await test('panels: symbol details — actions, 1D, Technicals table, About, countdown, community', async () => {
    const env = await start();
    const { page } = env;
    await page.evaluate(() => { window.__settingsTab = null; window.addEventListener('pc:settings', (e) => { window.__settingsTab = e.detail.tab; }); });
    await page.click('.details-head .lt >> nth=2');
    expect(await page.evaluate(() => window.__settingsTab) === 'symbol', 'settings on the symbol tab');
    await page.waitForSelector('.details .perf-grid .perf', { timeout: 15000 });
    expect((await page.textContent('.details .perf-grid .perf >> nth=0')).includes('1D'), '1D tile');
    await page.waitForSelector('.details-more', { timeout: 15000 });
    await page.click('.details-more');
    expect((await page.$$('.tech-table tbody tr')).length > 15, 'indicator table');
    const text = await page.textContent('.details');
    expect(/اندازه‌ی قرارداد|Contract size/.test(text) && /کمترین تیک|Minimum tick/.test(text), 'about card');
    await page.waitForSelector('.details-post', { timeout: 10000 });
    await state(page, () => window.__pc.usePrefs.getState().setSymbol('XAUUSD'));
    await page.waitForSelector('.details-count', { timeout: 10000 });
    await page.waitForSelector('.details-type');
    return env;
  });

  await test('panels: alert dialog from pc:alert-dialog — price, channel, trend line, indicator; edit, clone, log', async () => {
    const env = await start('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await state(page, () => window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { symbol: 'ETHUSDT', price: 2500 } })));
    await page.waitForSelector('.alert-dlg .alert-dlg-title >> text=ETHUSDT');
    expect(await page.inputValue('.alert-dlg .al-field input[type=number] >> nth=0') === '2500.0', 'price prefilled');
    await page.selectOption('.alert-dlg select >> nth=2', 'enter_channel');
    await page.fill('.alert-dlg .al-field input[type=number] >> nth=1', '2600');
    await page.click('.alert-dlg .chips-row .chip >> nth=1');
    await page.click('.alert-tabs [role=tab] >> nth=1');
    await page.fill('.al-col input', 'my channel');
    await page.click('.alert-dlg .btn-primary');
    let a = await state(page, () => window.__pc.useAlerts.getState().list[0]);
    expect(a.symbol === 'ETHUSDT' && a.condition === 'enter_channel' && a.value === 2500 && a.high === 2600 && a.freq === 'per_bar' && a.name === 'my channel', JSON.stringify(a));
    await state(page, () => { const t = Date.now() / 1000; window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { symbol: 'BTCUSDT', line: { t1: t - 3600, p1: 80000, t2: t, p2: 81000 } } })); });
    await page.waitForSelector('.alert-dlg .al-static');
    await page.click('.alert-dlg .btn-primary');
    a = await state(page, () => window.__pc.useAlerts.getState().list[0]);
    expect(a.source === 'line' && a.line && a.line.p2 === 81000, JSON.stringify(a));
    const uid = await state(page, () => { window.__pc.useStudies.getState().add('rsi'); const l = window.__pc.useStudies.getState().list; return l[l.length - 1].uid; });
    await state(page, (u) => window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { study: { uid: u, key: 'rsi', plot: 0 } } })), uid);
    await page.waitForFunction(() => document.querySelectorAll('.alert-dlg select')[3]?.options.length > 0, null, { timeout: 15000 });
    await page.click('.alert-dlg .btn-primary');
    a = await state(page, () => window.__pc.useAlerts.getState().list[0]);
    expect(a.source === 'indicator' && a.indicator.key === 'rsi' && a.indicator.output, JSON.stringify(a));
    await show(page, 'alerts');
    await page.waitForSelector('.alert-row');
    await page.hover('.alert-row >> nth=0');
    await page.click('.alert-row >> nth=0 >> .alert-tools .lt >> nth=0');
    await page.waitForSelector('.alert-dlg');
    await page.fill('.alert-dlg .al-field input[type=number] >> nth=0', '55');
    await page.click('.alert-dlg .btn-primary');
    expect(await state(page, () => window.__pc.useAlerts.getState().list[0].value) === 55, 'edited');
    const before = await state(page, () => window.__pc.useAlerts.getState().list.length);
    await page.hover('.alert-row >> nth=0');
    await page.click('.alert-row >> nth=0 >> .alert-tools .lt >> nth=2');
    expect(await state(page, () => window.__pc.useAlerts.getState().list.length) === before + 1, 'cloned');
    await page.click('.alerts-tools .ib >> nth=-1');
    await page.click('.wl-more >> text=/توقف همه|Stop all/');
    expect(await state(page, () => window.__pc.useAlerts.getState().list.every((x) => !x.active)), 'stop all');
    await page.click('.alerts-tools .ib >> nth=-1');
    await page.click('.wl-more >> text=/راه‌اندازی دوباره|Restart all/');
    expect(await state(page, () => window.__pc.useAlerts.getState().list.every((x) => x.active)), 'restart all');
    await state(page, () => { window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 1, name: 'fires now' }); window.__pc.useMarket.getState().put([{ symbol: 'BTCUSDT', last: 81234, change: 0, changePct: 0, ts: Date.now() }]); });
    await page.waitForSelector('.toast');
    await page.click('.alerts-head [role=tab] >> nth=1');
    await page.waitForSelector('.alert-log >> text=fires now');
    return env;
  });

  await test('panels: news — scope, categories, ticker chips, reader keys', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'news');
    await page.click('.news .panel-chips .chip >> nth=2');
    await page.waitForFunction(() => document.querySelectorAll('.news-row').length === 2, null, { timeout: 10000 });
    expect(await page.$('.news-row.hot'), 'an important story is marked');
    await page.click('.news .panel-chips.cats .chip >> text=/فلزات|Metals/');
    await page.waitForFunction(() => document.querySelectorAll('.news-row').length === 1);
    await page.click('.news .panel-chips.cats .chip.on');
    await page.click('.news-row >> nth=0');
    await page.waitForSelector('.article');
    const t1 = await page.textContent('.article h3');
    await page.keyboard.press('ArrowDown');
    await page.waitForFunction((x) => document.querySelector('.article h3')?.textContent !== x, t1);
    await page.keyboard.press('Escape');
    await page.waitForSelector('.news-row');
    return env;
  });

  await test('panels: calendar — columns, impact filter, reminder, opens the pair', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'calendar');
    await page.waitForSelector('.cal-row.x', { timeout: 10000 });
    expect(await page.$('.cal-cols') && await page.$('.cal-week .cal-wday'), 'column titles and week strip');
    expect(await page.$('.cal-row.x.next .cal-soon'), 'countdown on the next event');
    await page.click('.cal-imp >> nth=0');
    await page.waitForFunction(() => !document.querySelector('.cal-row.x'));
    await page.click('.cal-imp >> nth=0');
    await page.waitForSelector('.cal-row.x');
    await page.hover('.cal-row.x');
    await page.click('.cal-row.x .cal-bell');
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem('pc.cal.reminders.v1') || '[]').length) === 1, 'reminder kept');
    await page.click('.cal-row.x .cal-title');
    expect(await state(page, () => window.__pc.usePrefs.getState().symbol) === 'XAUUSD', 'USD opens gold');
    return env;
  });

  await test('panels: screener — columns, rating, gainers/losers, star, count', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'screener');
    await page.waitForSelector('.screener .scr-row:not(.scr-head)');
    await page.click('.scr-filters .lt >> nth=2');
    await page.click('.wl-more label.check >> text=/امتیاز فنی|Rating/');
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => [...document.querySelectorAll('.scr-rating')].some((e) => e.textContent && e.textContent !== '…'), null, { timeout: 20000 });
    await page.click('.scr-filters .seg button >> nth=1');
    await page.waitForFunction(() => !document.querySelector('.screener .scr-row:not(.scr-head)'));
    await page.click('.scr-filters .seg button >> nth=0');
    const row = page.locator('.screener .scr-row:not(.scr-head)', { hasText: 'LTCUSDT' });
    await row.locator('.scr-star').click();
    expect((await state(page, () => window.__pc.useWatchlist.getState().symbols)).includes('LTCUSDT'), 'star adds');
    expect(/\d|[۰-۹]/.test(await page.textContent('.scr-foot')), 'count footer');
    return env;
  });

  await test('panels: signals — status, TPs, confidence, this symbol, clear from chart', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page } = env;
    await show(page, 'signals');
    await page.waitForSelector('.sig-row .sig-state', { timeout: 15000 });
    expect(await page.$('.sig-row .sig-conf') && /TP2/.test(await page.textContent('.signals .panel-body')), 'confidence and TP2');
    await page.click('.signals .panel-chips .chip >> nth=2');
    await page.waitForFunction(() => [...document.querySelectorAll('.sig-row .sym')].every((e) => e.textContent === 'BTCUSDT'));
    await page.click('.sig-row .sig-levels');
    expect(await state(page, () => Boolean(window.__pc.useOverlay.getState().setup)), 'drawn on the chart');
    await page.click('.signals .panel-head .btn');
    expect(await state(page, () => window.__pc.useOverlay.getState().setup) === null, 'cleared');
    return env;
  });

  await test('panels: AI — quota, readings with live state, on chart and off', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true });
    await env.page.addInitScript(() => localStorage.setItem('pc.ai.history.v1', JSON.stringify([{ id: 'r1', symbol: 'BTCUSDT', interval: '60', at: Date.now() - 60000, r: { assessment: 'ok', direction: 'long', confidence: 74, trendBias: null, structure: null, setup: null, entryLow: 80000, entryHigh: 80200, sl: 78000, targets: [80500, 90000], reasoning: null, risk: null } }])));
    await env.page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const { page } = env;
    await show(page, 'ai');
    await page.waitForSelector('.ai-quota', { timeout: 10000 });
    await page.waitForSelector('.ai-hrow .sig-state');
    await page.click('.ai-hmain');
    expect(await state(page, () => window.__pc.useOverlay.getState().setup?.label) === 'AI', 'on chart');
    await page.waitForSelector('.ai-hrow.on');
    await page.click('.ai-hrow .lt');
    expect(!(await page.$('.ai-hrow')) && await state(page, () => window.__pc.useOverlay.getState().setup) === null, 'removed');
    return env;
  });

  await test('panels: hotlists, DOM click → paper limit, notes saved, log of fired alerts', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'hotlists');
    await page.selectOption('.hot-bar select', 'losers');
    await page.waitForSelector('.hot-row');
    await show(page, 'dom');
    await page.waitForSelector('.dom-lvl.bid .dom-cell.size', { timeout: 10000 });
    await page.click('.dom-lvl.bid .dom-cell.size >> nth=0');
    const o = await state(page, () => window.__pc.usePaper.getState().orders[0]);
    expect(o && o.type === 'limit' && o.side === 'long' && o.symbol === 'BTCUSDT', JSON.stringify(o));
    await show(page, 'notes');
    await page.fill('.notes-area', 'hello **world**');
    await page.waitForFunction(() => window.__pc.useWatchlist.getState().notes.BTCUSDT === 'hello **world**', null, { timeout: 3000 });
    await page.click('.notes-tools .seg button >> nth=1');
    expect(await page.textContent('.notes-view strong') === 'world', 'markdown');
    await state(page, () => { window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'above', value: 1, name: 'logged' }); window.__pc.useMarket.getState().put([{ symbol: 'BTCUSDT', last: 81111, change: 0, changePct: 0, ts: Date.now() }]); });
    await page.waitForSelector('.ws-wbar [data-panel="log"] .wbar-badge');
    await show(page, 'log');
    await page.waitForSelector('.notif-row.alert >> text=logged');
    return env;
  });

  await test('panels: order panel — market fill, limit with bracket, validation, leverage, live hand-off', async () => {
    const env = await start();
    const { page } = env;
    await show(page, 'order');
    await page.waitForSelector('.op-go');
    expect(await page.isDisabled('.op-go'), 'no quantity, no order');
    await page.fill('.op-box input.c', '0.01');
    await page.click('.op-go');
    await page.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 1, null, { timeout: 5000 }).catch(() => {});
    expect((await state(page, () => window.__pc.usePaper.getState().positions.length)) === 1, 'market filled');
    await page.click('.op-levs button >> nth=2');
    await page.waitForFunction(() => window.__pc.usePaper.getState().rules.leverage === 10, null, { timeout: 5000 }).catch(() => {});
    expect(await state(page, () => window.__pc.usePaper.getState().rules.leverage) === 10, 'leverage');
    await page.click('.op-types button >> nth=1');
    const px = Number(await page.inputValue('.op-field >> nth=0 >> input'));
    const tpS = String(Math.round(px * 1.05)), slS = String(Math.round(px * 0.95));
    await page.fill('.op-bracket input >> nth=0', tpS);
    await page.fill('.op-bracket input >> nth=1', slS);
    // Under load a re-render can land between the fills and the click; wait until both stand.
    await page.waitForFunction(([a, b]) => { const i = [...document.querySelectorAll('.op-bracket input')]; return i[0]?.value === a && i[1]?.value === b; }, [tpS, slS], { timeout: 5000 });
    await page.click('.op-go');
    await page.waitForFunction(() => window.__pc.usePaper.getState().orders.length > 0, null, { timeout: 5000 });
    const o = await state(page, () => window.__pc.usePaper.getState().orders[0]);
    expect(o && o.type === 'limit' && o.tp > o.sl, JSON.stringify(o));
    await page.fill('.op-bracket input >> nth=0', String(Math.round(px * 0.9)));
    await page.waitForSelector('.op-msgs.err');
    expect(await page.isDisabled('.op-go'), 'a take profit below a buy blocks it');
    await page.fill('.op-bracket input >> nth=0', String(Math.round(px * 1.05)));
    await page.click('.op-pcts button >> nth=0');
    expect(Number(await page.inputValue('.op-box input.c')) > 0, '25% sets a quantity');
    await page.evaluate(() => window.addEventListener('pc:order-ticket', (e) => { window.__ticket = e.detail; e.preventDefault(); }));
    const n = await state(page, () => window.__pc.usePaper.getState().orders.length);
    await page.click('.op-go');
    const t = await page.evaluate(() => window.__ticket);
    expect(t && t.side === 'buy' && t.type === 'limit' && t.source === 'panel', JSON.stringify(t));
    expect((await state(page, () => window.__pc.usePaper.getState().orders.length)) === n, 'taken live, not on paper');
    return env;
  });

  await test('panels: phone — the widget bar scrolls and a panel fits', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page } = env;
    expect(await page.evaluate(() => { const w = document.querySelector('.ws-wbar'); return w.scrollWidth > w.clientWidth; }), 'widget bar scrolls');
    for (const id of ['order', 'alerts', 'watchlist', 'calendar']) {
      await show(page, id);
      await page.waitForTimeout(500);
      expect(!(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)), `${id} overflows the phone`);
    }
    return env;
  });

  const SHOTS = process.env.PANELS_SHOTS;
  if (SHOTS) {
    mkdirSync(SHOTS, { recursive: true });
    for (const [lang, scheme] of [['fa', 'light'], ['en', 'light'], ['fa', 'dark'], ['en', 'dark']]) {
      await test(`panels: pictures ${lang} ${scheme}`, async () => {
        const env = await open('BTCUSDT/1h?e2e=1', { scheme, signed: true });
        await env.page.addInitScript((l) => { try { const p = JSON.parse(localStorage.getItem('pc.prefs.v1') || '{}'); localStorage.setItem('pc.prefs.v1', JSON.stringify({ ...p, lang: l })); } catch { /* ignore */ } }, lang);
        await env.page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1');
        await ready(env.page);
        await env.page.waitForTimeout(2500);
        const shot = async (name) => { await env.page.waitForTimeout(700); await env.page.screenshot({ path: join(SHOTS, `${lang}-${scheme}-${name}.png`) }); };
        const panel = async (id) => { await show(env.page, id); await env.page.waitForTimeout(1500); };
        await panel('watchlist'); await shot('watchlist');
        await env.page.click('.wl-row >> nth=2', { button: 'right' }); await shot('wl-row-menu'); await env.page.keyboard.press('Escape');
        await env.page.click('.wl-head button[aria-haspopup] >> nth=1'); await shot('wl-more'); await env.page.keyboard.press('Escape');
        await env.page.click('.wl-title'); await shot('wl-title'); await env.page.keyboard.press('Escape');
        await env.page.evaluate(() => { const d = document.querySelector('.details'); if (d) d.scrollTop = 500; }); await shot('details-2');
        await env.page.evaluate(() => { const d = document.querySelector('.details'); if (d) d.scrollTop = 1100; }); await shot('details-3');
        for (const id of ['alerts', 'news', 'calendar', 'screener', 'signals', 'ai', 'hotlists', 'dom', 'notes', 'log', 'order']) { await panel(id); await shot(id); }
        await pc(env.page, () => window.dispatchEvent(new CustomEvent('pc:alert-dialog', { detail: { symbol: 'ETHUSDT', price: 2500 } })));
        await shot('alert-dialog');
        return env;
      });
    }
    await test('panels: pictures phone fa', async () => {
      const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
      await ready(env.page);
      for (const id of ['watchlist', 'alerts', 'order', 'calendar', 'dom']) {
        await show(env.page, id); await env.page.waitForTimeout(1500);
        await env.page.screenshot({ path: join(SHOTS, `phone-${id}.png`) });
      }
      return env;
    });
  }
}
