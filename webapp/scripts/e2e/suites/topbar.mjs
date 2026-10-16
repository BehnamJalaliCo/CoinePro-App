// The top bar and what hangs from it: intervals (stars, custom, derived, the typed box), spreads,
// the symbol search, quick search, layouts, multi-chart (presets, active chart, sync, maximise),
// the time bar (ranges, zones, session, go to, gear), the snapshot, the badge, trade, publish,
// the phone's "⋯" sheet and the main menu's switches.
export default async function ({ test, open, ready, pc, expect }) {
  const bars = (page) => pc(page, () => window.__pc.useChartBus.getState().bars.map((b) => b.t));
  const aligned = (ts, step) => ts.length > 5 && ts.every((t) => t % step === 0) && ts.every((t, i) => i === 0 || t > ts[i - 1]);
  const waitUrl = (page, re) => page.waitForFunction((src) => new RegExp(src).test(location.pathname), re.source, { timeout: 10000 }).catch(() => { throw new Error(`url ${page.url()} is not ${re}`); });

  await test('topbar: interval stars, custom interval, derived intervals load on the clock', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.iv-caret');
    await page.waitForSelector('.iv-menu');
    const rows = await page.$$eval('.iv-row', (els) => els.length);
    expect(rows >= 20, `interval rows ${rows}`);
    // Star "2 minutes": a button in the bar.
    await page.locator('.iv-row', { hasText: /^۲ دقیقه|2 minutes/ }).first().hover();
    await page.locator('.iv-row', { hasText: /^۲ دقیقه|2 minutes/ }).first().locator('.iv-star').click();
    expect((await pc(page, () => window.__pc.usePrefs.getState().favIntervals)).includes('2'), 'star did not add 2m');
    await page.keyboard.press('Escape');
    await page.click('.ws-top .iv >> text=2m');
    await waitUrl(page, /\/BTCUSDT\/2m$/);
    await ready(page);
    expect(aligned(await bars(page), 120), '2m bars not on the clock');
    // A custom interval from the dialog.
    await page.click('.iv-caret');
    await page.click('.iv-add');
    await page.fill('.tv-small input', '7');
    await page.click('.tv-small button[type=submit]');
    await waitUrl(page, /\/BTCUSDT\/7m$/);
    await ready(page);
    const t7 = await bars(page);
    expect(t7.length > 5 && t7.every((t) => (t % 86400) % 420 === 0), '7m bars not aligned to the day');
    expect((await pc(page, () => window.__pc.usePrefs.getState().customIntervals)).includes('7'), 'custom not kept');
    await page.click('.iv-caret');
    expect(await page.isVisible('.iv-row .iv-x'), 'no remove on the custom row');
    await page.click('.iv-row .iv-x');
    expect(!(await pc(page, () => window.__pc.usePrefs.getState().customIntervals)).includes('7'), 'custom not removed');
    await page.keyboard.press('Escape');
    // The menu's own search: a filter, and Enter on a typed interval.
    await page.click('.iv-caret');
    await page.fill('.iv-search input', 'hour');
    expect((await page.$$eval('.iv-row', (els) => els.length)) === 7, 'search did not filter to the hours');
    await page.fill('.iv-search input', '5h');
    await page.keyboard.press('Enter');
    await waitUrl(page, /\/BTCUSDT\/5h$/);
    // 3 months on forex, folded from days onto the quarter.
    await pc(page, () => window.__pc.usePrefs.getState().setSymbol('XAUUSD'));
    await pc(page, () => window.__pc.usePrefs.getState().setInterval('3M'));
    await ready(page);
    const q = await bars(page);
    expect(q.length >= 2 && q.every((t) => { const d = new Date(t * 1000); return d.getUTCDate() === 1 && d.getUTCMonth() % 3 === 0; }), '3M bars not on quarters');
    return env;
  });

  await test('topbar: a digit typed on the chart opens the interval box', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const box = await page.locator('.chart-host').boundingBox();
    await page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.5);
    await page.keyboard.type('45', { delay: 60 });
    await page.waitForSelector('.iv-box');
    expect(/45|۴۵/.test(await page.textContent('.iv-box-hint')), 'hint does not name 45 minutes');
    await page.keyboard.press('Enter');
    await waitUrl(page, /\/BTCUSDT\/45m$/);
    await ready(page);
    expect(aligned(await bars(page), 2700), '45m bars not on the clock');
    await page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.5);
    await page.keyboard.type('4h', { delay: 60 });
    await page.keyboard.press('Enter');
    await waitUrl(page, /\/BTCUSDT\/4h$/);
    // Nonsense says so and changes nothing.
    await page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.5);
    await page.keyboard.type('9x');
    expect(Boolean(await page.waitForSelector('.iv-box-hint.bad', { timeout: 3000 }).catch(() => null)), 'no warning for 9x');
    await page.keyboard.press('Escape');
    expect(await pc(page, () => window.__pc.usePrefs.getState().interval) === '240', 'interval changed by nonsense');
    return env;
  });

  await test('topbar: derived interval pages back whole and stays live', async () => {
    const env = await open('XAUUSD/45m?e2e=1');
    const { page } = env;
    // A chart route that honours `before`, as the real one does.
    await page.route('**/up/coineprofx/api/academy/chart/**', (r) => {
      const u = new URL(r.request().url());
      const step = { M5: 300, M15: 900, H1: 3600, H4: 14400, D1: 86400 }[u.searchParams.get('tf')] ?? 900;
      const before = Number(u.searchParams.get('before') ?? Math.floor(Date.now() / 1000) + step);
      const n = Math.min(3000, Number(u.searchParams.get('limit') ?? 1500));
      const end = Math.floor((before - 1) / step) * step;
      const candles = Array.from({ length: n }, (_, i) => { const t = end - (n - 1 - i) * step; const o = 4000 + Math.sin(t / 7200) * 20; return { t, o, h: o + 3, l: o - 3, c: o + Math.cos(t / 3600), v: 10 }; });
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ candles }) });
    });
    await page.reload();
    await ready(page);
    const before = await bars(page);
    expect(aligned(before, 2700), 'XAUUSD 45m not aligned');
    await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().setVisibleLogicalRange({ from: -5, to: 60 }));
    await page.waitForFunction((n) => window.__pc.useChartBus.getState().bars.length > n, before.length, { timeout: 10000 });
    const after = await bars(page);
    expect(aligned(after, 2700), 'paged bars not aligned or duplicated');
    // Crypto 3m from 1m: the forming bar follows the price feed.
    await pc(page, () => { const p = window.__pc.usePrefs.getState(); p.setSymbol('ETHUSDT'); p.setInterval('3'); });
    await ready(page);
    const last0 = await pc(page, () => window.__pc.useChartBus.getState().bars.at(-1));
    expect(last0.t % 180 === 0, 'forming bar not aligned');
    return env;
  });

  await test('topbar: spreads and formulas open, draw and stay out of lists', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.topbar-symbol');
    await page.waitForSelector('.dialog.ss');
    await page.keyboard.type('btcusdt/ethusdt');
    await page.waitForSelector('.expr-row');
    await page.keyboard.press('Enter');
    await waitUrl(page, /\/next\/BTCUSDT%2FETHUSDT\/1h$/);
    await ready(page);
    const c = await pc(page, () => window.__pc.useChartBus.getState().bars.at(-1).c);
    expect(c > 5 && c < 200, `ratio ${c}`);
    expect((await page.textContent('.ws-top .topbar-symbol')).includes('BTCUSDT/ETHUSDT'), 'top bar does not name the spread');
    expect(await page.isVisible('.ws-top .topbar-symbol .logo-expr'), 'no formula glyph');
    const listed = await page.$$eval('.wl-row', (els) => els.some((e) => e.textContent.includes('/')));
    expect(!listed, 'a spread reached the watchlist');
    // A link opens a formula straight away.
    await page.goto('http://pc.local/next/XAUUSD*2/4h?e2e=1');
    await ready(page);
    const g = await pc(page, () => window.__pc.useChartBus.getState().bars.at(-1).c);
    const s = await pc(page, () => window.__pc.usePrefs.getState().symbol);
    expect(s === 'XAUUSD*2' && g > 1000, `formula ${s} ${g}`);
    return env;
  });

  await test('topbar: symbol search tabs, highlight, recent, compare mode', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.topbar-symbol');
    const value = await page.inputValue('.ss-field input');
    expect(value === 'BTCUSDT', `field starts with ${value}`);
    await page.keyboard.type('eth');
    await page.waitForSelector('.search-row.on .hl');
    expect((await page.textContent('.search-row.on .hl')).toUpperCase() === 'ETH', 'match not marked');
    expect((await page.textContent('.search-row.on .venue')) === 'LBank', 'no source column');
    await page.keyboard.press('Enter');
    await waitUrl(page, /ETHUSDT/);
    await page.click('.topbar-symbol');
    await page.waitForSelector('.ss-section');
    expect((await page.textContent('.search-list')).includes('ETHUSDT'), 'no recent');
    await page.click('.ss-tabs .chip >> nth=2');
    const forex = await page.$$eval('.search-row .sym', (els) => els.map((e) => e.textContent));
    expect(forex.length > 0 && forex.every((s) => !s.endsWith('USDT')), `forex tab: ${forex.slice(0, 4)}`);
    await page.keyboard.press('Escape');
    // Compare: added and removed in place.
    await page.click('.compare-btn');
    await page.waitForSelector('.dialog.ss.compare');
    await page.fill('.ss-field input', 'SOL');
    await page.click('.search-row >> nth=0');
    expect((await pc(page, () => window.__pc.useCompare.getState().symbols)).includes('SOLUSDT'), 'compare not added');
    expect(await page.isVisible('.dialog.ss.compare'), 'compare dialog closed');
    await page.click('.search-row >> nth=0');
    expect(!(await pc(page, () => window.__pc.useCompare.getState().symbols)).includes('SOLUSDT'), 'compare not removed');
    return env;
  });

  await test('topbar: quick search runs actions, flips switches, opens pages', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.keyboard.press('Control+k');
    await page.waitForSelector('.dialog.quick');
    await page.keyboard.type('Relative Strength');
    await page.waitForSelector('.qs-row.on');
    await page.keyboard.press('Enter');
    await page.waitForFunction(() => window.__pc.useStudies.getState().list.some((s) => s.key === 'rsi'), null, { timeout: 5000 });
    await page.click('.qs-btn');
    await page.keyboard.type('watermark');
    await page.waitForSelector('.qs-row.on .switch');
    await page.keyboard.press('Enter');
    expect(await pc(page, () => window.__pc.useChartSettings.getState().watermark), 'switch not flipped');
    expect(await page.isVisible('.dialog.quick'), 'a switch closed the palette');
    await page.fill('.dialog.quick input', 'trend line');
    await page.waitForSelector('.qs-row.on >> text=/خط روند|Trend line/');
    await page.keyboard.press('Enter');
    await page.waitForSelector('.dialog.quick', { state: 'detached' });
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'trend', 'tool not armed');
    await page.keyboard.press('Control+k');
    await page.waitForSelector('.dialog.quick input:focus');
    await page.keyboard.type('heat');
    await page.waitForSelector('.qs-row.on >> text=/هیت‌مپ|Heat map/');
    await page.keyboard.press('Enter');
    await page.waitForURL(/\/next\/heatmap$/, { timeout: 5000 });
    return env;
  });

  await test('topbar: layouts save, rename, copy, open, delete, autosave, drawings kept', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    page.on('dialog', (d) => void d.accept().catch(() => {}));
    await ready(page);
    await pc(page, () => { const b = window.__pc.useChartBus.getState(); b.drawingLayer.addHLine(b.bars.at(-1).c, '#2962FF'); });
    await page.click('.save-btn');
    await page.waitForSelector('.tv-small input');
    await page.fill('.tv-small input', 'Desk');
    await page.click('.tv-small button[type=submit]');
    expect((await page.textContent('.layout-name')).includes('Desk'), 'name not on the button');
    // The grid and drawings go with it.
    await page.click('.grid-btn');
    await page.click('[data-grid="2v"]');
    await page.keyboard.press('Escape');
    await page.keyboard.press('Control+s');
    const saved = await pc(page, () => window.__pc.useLayouts.getState().layouts.find((l) => l.name === 'Desk'));
    expect(saved.grid === '2v' && saved.drawings.BTCUSDT?.length === 1, `saved ${JSON.stringify({ g: saved.grid, d: Object.keys(saved.drawings ?? {}) })}`);
    await pc(page, () => window.__pc.useChartBus.getState().drawingLayer.clearAll());
    await pc(page, () => window.__pc.useLayouts.getState().load('Desk'));
    await page.waitForTimeout(400);
    expect(await pc(page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length) === 1, 'drawings not restored');
    // Rename and copy.
    await page.click('.layout-name');
    await page.click('.layout-menu .menu-item >> nth=2');
    await page.fill('.tv-small input', 'Desk 2');
    await page.click('.tv-small button[type=submit]');
    await page.click('.layout-name');
    await page.click('.layout-menu .menu-item >> nth=3');
    await page.click('.tv-small button[type=submit]');
    const names = await pc(page, () => window.__pc.useLayouts.getState().layouts.map((l) => l.name).sort());
    expect(names.length === 2 && names[0] === 'Desk 2', `names ${names}`);
    // Open layout: search, sort, delete.
    await page.click('.layout-name');
    await page.click('.layout-menu .menu-item >> nth=6');
    await page.waitForSelector('.open-layouts .ol-row');
    expect(await page.$$eval('.ol-row', (els) => els.length) === 2, 'open dialog rows');
    await page.fill('.open-layouts input', '(');
    expect(await page.$$eval('.ol-row', (els) => els.length) === 1, 'search');
    await page.fill('.open-layouts input', '');
    await page.click('.ol-row >> nth=1 >> .lt');
    expect((await pc(page, () => window.__pc.useLayouts.getState().layouts.length)) === 1, 'delete');
    await page.click('.ol-row >> nth=0');
    // Autosave.
    await page.click('.layout-name');
    await page.click('.layout-menu .toggle-row');
    await page.keyboard.press('Escape');
    await pc(page, () => window.__pc.usePrefs.getState().setInterval('240'));
    await page.waitForFunction(() => { const s = window.__pc.useLayouts.getState(); return s.layouts.find((l) => l.name === s.current)?.interval === '240'; }, null, { timeout: 8000 });
    return env;
  });

  await test('topbar: multi-chart presets, active chart, sync, maximise', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const presets = await pc(page, () => window.__pc.gridPresets);
    expect(presets.length >= 25 && Math.max(...presets.map((p) => p.cells)) === 8, `presets ${presets.length}`);
    await page.click('.grid-btn');
    for (const id of ['3c', '4l', '6', '8', '4']) {
      await page.click(`[data-grid="${id}"]`);
      await page.waitForTimeout(500);
      const n = await page.$$eval('.lite', (els) => els.length);
      expect(n === presets.find((p) => p.id === id).cells - 1, `${id}: ${n} other charts`);
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(1500);
    // The second chart becomes active; the top bar now drives it.
    const lite = await page.locator('.lite').first().boundingBox();
    await page.mouse.click(lite.x + lite.width / 2, lite.y + lite.height / 2);
    expect(await pc(page, () => window.__pc.useGrid.getState().active) === 1, 'cell not active');
    expect((await page.textContent('.topbar-symbol')).includes('ETHUSDT'), 'top bar not on the active chart');
    await page.click('.ws-top .iv >> text=4h');
    expect(await pc(page, () => window.__pc.useGrid.getState().cells[0].interval) === '240', 'interval not on the cell');
    expect(await pc(page, () => window.__pc.usePrefs.getState().interval) === '60', 'main chart changed');
    // Crosshair: moving over a cell shares its time.
    await page.mouse.move(lite.x + lite.width / 2, lite.y + lite.height / 2);
    await page.mouse.move(lite.x + lite.width / 3, lite.y + lite.height / 2, { steps: 4 });
    expect((await pc(page, () => window.__pc.useGrid.getState().hover))?.src === 1, 'crosshair not shared');
    // Symbol sync.
    await page.click('.grid-btn');
    await page.click('.grid-menu .toggle-row >> nth=0');
    await page.keyboard.press('Escape');
    await pc(page, () => window.__pc.useGrid.getState().setActive(0));
    await page.click('.topbar-symbol');
    await page.keyboard.type('SOL');
    await page.keyboard.press('Enter');
    await page.waitForFunction(() => window.__pc.useGrid.getState().cells.slice(0, 3).every((c) => c.symbol === 'SOLUSDT'), null, { timeout: 5000 });
    // Time sync: zooming the main chart moves the others.
    await pc(page, () => window.__pc.useGrid.getState().setSync({ symbol: false, time: true }));
    const main = await page.locator('.cell-main .chart-host').boundingBox();
    await page.mouse.move(main.x + main.width / 2, main.y + main.height / 2);
    await page.mouse.wheel(0, -600);
    await page.waitForFunction(() => window.__pc.useGrid.getState().range?.src === 0, null, { timeout: 5000 });
    // Alt+Enter maximises the active chart and back.
    await page.keyboard.press('Alt+Enter');
    await page.waitForSelector('.chart-grid.maxed');
    expect((await page.$$eval('.lite', (els) => els.filter((e) => e.offsetParent).length)) === 0, 'others still shown');
    await page.keyboard.press('Alt+Enter');
    await page.waitForSelector('.chart-grid:not(.maxed)');
    // Mirrored indicators.
    await pc(page, () => { window.__pc.useGrid.getState().setMirror({ studies: true }); window.__pc.useStudies.getState().add('rsi'); });
    await page.waitForTimeout(800);
    return env;
  });

  await test('topbar: a phone shows one chart and every desktop button in the sheet', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useGrid.getState().setGrid('4'));
    await page.waitForTimeout(800);
    expect((await page.$$eval('.cell-slot', (els) => els.filter((e) => e.offsetParent).length)) === 0, 'cells shown on a phone');
    await page.click('.more-btn');
    await page.waitForSelector('.sheet');
    const tiles = await page.$$eval('.sheet-tile', (els) => els.length);
    expect(tiles >= 15, `sheet tiles ${tiles}`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(!overflow, 'horizontal overflow');
    await page.click('.sheet-tile:has-text("جستجوی سریع")');
    await page.waitForSelector('.dialog.quick');
    return env;
  });

  await test('topbar: a narrow desktop folds buttons away instead of squeezing them', async () => {
    for (const [w, lang] of [[1440, 'fa'], [1280, 'en'], [1100, 'fa'], [900, 'en']]) {
      const env = await open('BTCUSDT/1h?e2e=1', { width: w, height: 800 });
      const { page } = env;
      await ready(page);
      if (lang === 'en') { await pc(page, () => window.__pc.usePrefs.getState().setLang('en')); await page.waitForTimeout(200); }
      const fit = await page.evaluate(() => { const t = document.querySelector('.ws-top'); return t.scrollWidth <= t.clientWidth; });
      expect(fit, `top bar overflows at ${w} (${lang})`);
      if (w <= 1100) {
        await page.click('.more-btn');
        await page.click('.sheet-tile.grid-tile');
        await page.click('[data-grid="2v"]');
        await page.waitForSelector('.lite');
      }
      await env.ctx.close();
      if (env.errors.length) throw new Error(env.errors.join(' | '));
    }
    return null;
  });

  await test('topbar: time bar ranges, time zones, session, go to a range, gear', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.ws-timebar .range >> text=5D');
    await waitUrl(page, /\/BTCUSDT\/5m$/);
    await ready(page);
    expect((await page.textContent('.ws-timebar .range.on')) === '5D', 'range not marked');
    const main = await page.locator('.cell-main .chart-host').boundingBox();
    await page.mouse.move(main.x + main.width / 2, main.y + main.height / 2);
    await page.mouse.wheel(0, -300);
    await page.waitForTimeout(200);
    expect(!(await page.$('.ws-timebar .range.on')), 'mark kept after zoom');
    // Zones.
    await page.click('.timebar-clock');
    const zones = await page.$$eval('.tz-menu .tz-row', (els) => els.length);
    expect(zones > 50, `zones ${zones}`);
    await page.click('.tz-menu .tz-row >> nth=1');
    expect(await pc(page, () => window.__pc.usePrefs.getState().tzExchange), 'exchange not chosen');
    expect(await pc(page, () => window.__pc.usePrefs.getState().timezone) === 'UTC', 'crypto exchange zone');
    await pc(page, () => window.__pc.usePrefs.getState().setSymbol('XAUUSD'));
    await page.waitForFunction(() => window.__pc.usePrefs.getState().timezone === 'America/New_York', null, { timeout: 5000 });
    // Session shading from its menu, painted over the main pane.
    await page.click('.shading-btn');
    await page.click('.shading-menu .shade-all .btn >> nth=0');
    await page.keyboard.press('Escape');
    await page.waitForSelector('canvas.session-shading', { timeout: 5000 });
    await page.waitForFunction(() => { const c = document.querySelector('canvas.session-shading'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; for (let i = 3; i < d.length; i += 40) if (d[i]) return true; return false; }, null, { timeout: 5000 });
    // Gear.
    await page.evaluate(() => { window.__gear = null; window.addEventListener('pc:settings', (e) => { window.__gear = e.detail?.tab; }); });
    await page.click('.timebar-gear');
    expect(await page.evaluate(() => window.__gear) === 'scales', 'gear did not ask for Scales');
    // Custom range.
    await ready(page);
    await page.keyboard.press('Escape');
    await page.keyboard.press('Alt+g');
    await page.click('.goto-tabs button >> nth=1');
    const span = await pc(page, () => { const b = window.__pc.useChartBus.getState().bars; const p2 = (n) => String(n).padStart(2, '0'); const f = (t) => { const d = new Date(t * 1000); return `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}T${p2(d.getHours())}:${p2(d.getMinutes())}`; }; return [f(b[200].t), f(b[260].t)]; });
    await page.fill('.goto-range input >> nth=0', span[0]);
    await page.fill('.goto-range input >> nth=1', span[1]);
    await page.click('.goto-range .btn');
    await page.waitForSelector('.goto-dialog', { state: 'detached', timeout: 8000 });
    const r = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    expect(r.from >= 190 && r.from <= 210 && r.to <= 270, `range ${JSON.stringify(r)}`);
    // Session: a cash index has regular hours.
    await pc(page, () => { const p = window.__pc.usePrefs.getState(); p.setSymbol('US500'); p.setInterval('15'); });
    await page.waitForSelector('.session-btn');
    await page.click('.session-btn');
    await page.click('.session-menu .tz-row >> nth=0');
    await page.waitForFunction(() => window.__pc.usePrefs.getState().session === 'regular');
    await page.waitForTimeout(1500);
    const outside = await pc(page, () => window.__pc.useChartBus.getState().bars.filter((b) => { const h = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'America/New_York', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(b.t * 1000)).replace(':', '')); return h < 930 || h >= 1600; }).length);
    expect(outside === 0, `${outside} bars outside the cash session`);
    await pc(page, () => window.__pc.usePrefs.getState().setSession('extended'));
    return env;
  });

  await test('topbar: snapshot menu, every canvas and the legend in the picture', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page, ctx } = env;
    await ready(page);
    await pc(page, () => window.__pc.useChartSettings.getState().set({ sessionBreaks: true }));
    const a = await pc(page, () => window.__pc.snapshotProbe());
    const host = await page.locator('.cell-main').boundingBox();
    expect(Math.abs(a.width - Math.round(host.width)) <= 1 && a.height === Math.round(host.height) + 32, `size ${a.width}×${a.height}`);
    expect(a.ink > 2000, `picture nearly empty (${a.ink})`);
    const b = await pc(page, () => window.__pc.snapshotProbe({ caption: false, hiRes: true }));
    expect(Math.abs(b.width - a.width * 2) <= 2 && Math.abs(b.height - (a.height - 32) * 2) <= 2, `2× ${b.width}×${b.height}`);
    // The menu: JPG download, link, new tab.
    await page.click('.snap-btn');
    await page.click('.snap-format .seg button >> nth=1');
    const dl = page.waitForEvent('download', { timeout: 8000 });
    await page.click('.snap-menu .menu-item >> nth=0');
    expect(/\.jpg$/.test((await dl).suggestedFilename()), 'not a JPG');
    // Plain http has no clipboard API: the link goes through the copy command, caught here.
    await page.evaluate(() => { window.__copied = null; const orig = document.execCommand.bind(document); document.execCommand = (c, ...a) => { if (c === 'copy') { window.__copied = document.activeElement?.value ?? null; return true; } return orig(c, ...a); }; });
    await page.click('.snap-btn');
    await page.click('.snap-menu .menu-item >> nth=2');
    await page.waitForSelector('.pc-toast');
    const link = await page.evaluate(async () => (navigator.clipboard?.readText ? navigator.clipboard.readText().catch(() => window.__copied) : window.__copied));
    expect(/\/next\/BTCUSDT\/1h$/.test(link), `link ${link}`);
    await page.click('.snap-btn');
    const tab = ctx.waitForEvent('page', { timeout: 8000 });
    await page.click('.snap-menu .menu-item >> nth=3');
    await tab;
    // Two charts: both in one picture.
    await pc(page, () => window.__pc.useGrid.getState().setGrid('2v'));
    await page.waitForTimeout(2500);
    const c = await pc(page, () => window.__pc.snapshotProbe({ caption: false }));
    const grid = await page.locator('.chart-grid').boundingBox();
    expect(Math.abs(c.width - Math.round(grid.width)) <= 1, `layout picture ${c.width} vs ${grid.width}`);
    // The other chart maximised: the picture is of it alone.
    await pc(page, () => { const g = window.__pc.useGrid.getState(); g.setActive(1); g.setMaximized(true); });
    await page.waitForTimeout(800);
    const m = await pc(page, () => window.__pc.snapshotProbe({ caption: false }));
    expect(m && Math.abs(m.width - Math.round(grid.width)) <= 1 && m.ink > 1000, `maximised picture ${JSON.stringify(m)}`);
    return env;
  });

  await test('topbar: alert badge, trade, publish, main menu switches', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useAlerts.getState().add('BTCUSDT', 'above', 99999));
    await page.waitForSelector('.alert-btn .badge');
    expect((await page.textContent('.alert-btn .badge')) === '۱', 'badge not in Persian digits');
    await page.click('.trade-btn');
    await page.waitForURL(/\/next\/paper-trade$/, { timeout: 5000 });
    await pc(page, () => window.__pc.usePage.getState().close());
    await ready(page);
    await page.click('.publish-btn');
    await page.waitForURL(/\/next\/community\/compose$/, { timeout: 8000 });
    await page.waitForSelector('.community textarea', { timeout: 10000 }).catch(() => {});
    await pc(page, () => window.__pc.usePage.getState().close());
    await page.click('.menu-btn');
    await page.waitForSelector('.drawer-tv');
    await page.click('.drawer-row[role=switch]');
    expect(await pc(page, () => window.__pc.usePrefs.getState().theme) === 'dark', 'theme switch');
    await page.click('.lang-row .seg button >> nth=1');
    expect(await pc(page, () => window.__pc.usePrefs.getState().lang) === 'en', 'language');
    await page.click('.drawer-row >> nth=2');
    await page.waitForURL(/\/next\/tutorials\/shortcuts$/, { timeout: 5000 });
    return env;
  });

  await test('topbar: a session revoked elsewhere returns the page to a guest, not a stuck signed-in reader', async () => {
    let refreshes = 0;
    const env = await open('BTCUSDT/1h?e2e=1', { signed: true, mock: (u) => {
      const p = u.pathname;
      if (/\/auth\/refresh$/.test(p)) { refreshes++; return { status: 422, contentType: 'application/json', body: JSON.stringify({ detail: [{ msg: 'refresh_token required' }] }) }; }
      if (p.startsWith('/up/tradeyar/api/mobile/v1/') || p.startsWith('/up/coineprofx/api/user/')) {
        if (/\/auth\/guest$/.test(p) || /\/public\//.test(p)) return null;
        return { status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'TYR-004', detail: 'Auth Token Missing' }) };
      }
      return null;
    } });
    await ready(env.page);
    // Ask as the panel would; the 401 and the unreadable refresh end the session on that platform.
    await pc(env.page, () => Promise.allSettled([window.__pc.authed('tradeyar', '/up/tradeyar/api/mobile/v1/me'), window.__pc.authed('coineprofx', '/up/coineprofx/api/user/me')]));
    await env.page.waitForFunction(() => { const s = window.__pc.useSession.getState().sessions; return !s.tradeyar && !s.coineprofx; }, null, { timeout: 15000 });
    const settled = refreshes;
    await env.page.waitForTimeout(3000);
    expect(refreshes - settled <= 0, `refresh kept being retried: ${refreshes}`);
    return env;
  });

  await test('topbar: on a dark system theme the menu dark switch reads on, and one click makes it light', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { scheme: 'dark' });
    const { page } = env;
    await ready(page);
    expect(await pc(page, () => window.__pc.usePrefs.getState().theme) === 'system', 'starts on the system theme');
    await page.click('.menu-btn');
    await page.waitForSelector('.drawer-tv');
    const sw = '.drawer-tv .drawer-row[role=switch] >> nth=0';
    expect(await page.getAttribute(sw, 'aria-checked') === 'true', `switch reads ${await page.getAttribute(sw, 'aria-checked')} on a dark page`);
    await page.click(sw);
    expect(await pc(page, () => window.__pc.usePrefs.getState().theme) === 'light', 'one click: light');
    expect(await page.getAttribute(sw, 'aria-checked') === 'false', 'then reads off');
    return env;
  });
}
