// The coach, the first run and the chrome's tooltips (area B1). Every other suite opens its pages with
// ?e2e=1 and must never meet a tip or a welcome screen; these tests ask for them with &coach=1 and
// &firstrun=1.
export default async function ({ test, open, ready, expect }) {
  // A touch screen for this context: `(pointer: coarse)` answers true.
  const touchScreen = async (ctx) => {
    await ctx.addInitScript(() => {
      const real = window.matchMedia.bind(window);
      window.matchMedia = (q) => (/pointer:\s*coarse/.test(q) ? { matches: true, media: q, onchange: null, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; } } : real(q));
    });
  };
  const stored = (page) => page.evaluate(() => JSON.parse(localStorage.getItem('pc.coach.v1') ?? '{}'));
  const painted = (page, sel) => page.evaluate((s) => {
    const c = document.querySelector(s);
    if (!c || !c.width) return 0;
    const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
    const seen = new Set();
    for (let i = 0; i < d.length; i += 40) seen.add(`${d[i] >> 4},${d[i + 1] >> 4},${d[i + 2] >> 4}`);
    return seen.size;
  }, sel);

  // The film fades in from its first frame, so a slow runner can sample it still empty: wait for colour.
  const colourIn = (page) => page.waitForFunction(() => {
    const c = document.querySelector('.coach-film');
    if (!c || !c.width) return false;
    const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
    const seen = new Set();
    for (let i = 0; i < d.length; i += 40) seen.add(`${d[i] >> 4},${d[i + 1] >> 4},${d[i + 2] >> 4}`);
    return seen.size > 8;
  }, null, { timeout: 5000 }).catch(() => undefined);

  await test('coach: never shown to a page opened for another suite (?e2e=1)', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await env.page.waitForTimeout(2600);
    expect(!(await env.page.$('.coach-layer')), 'a coach tip appeared under ?e2e=1');
    expect(!(await env.page.$('.first-run')), 'the first run appeared under ?e2e=1');
    return env;
  });

  await test('coach: a mouse gets one quiet card with a ring, and clicks still reach the page', async () => {
    const env = await open('BTCUSDT/1h?e2e=1&coach=1');
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.coach-layer.quiet .coach-card', { timeout: 8000 });
    expect(!(await page.$('.coach-scrim')), 'a mouse tip dimmed the page');
    expect(!(await page.$('.coach-card .coach-film')), 'the quiet card carries the film');
    const id = await page.getAttribute('.coach-layer', 'data-coach-tip');
    expect(id === 'draw_toolbar', `first desktop tip ${id}`);
    const title = await page.textContent('.coach-card .coach-head b');
    expect(title === 'نوار ابزار ترسیم', `mouse title ${title}`);
    // The card sits in the bottom-left corner, past the rail (measured once its rise has settled).
    await page.waitForTimeout(900);
    const box = await page.locator('.coach-card').boundingBox();
    expect(box.x >= 16 && box.x <= 70 && box.y + box.height > 900 - 40, `card at ${JSON.stringify(box)}`);
    // Clicks outside the card reach the page: the menu opens under a tip.
    await page.click('.ws-top .menu-btn');
    await page.waitForSelector('.drawer', { timeout: 3000 });
    // …and a dialog over the page puts the tip away.
    await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    expect((await stored(page)).dismissed?.includes('draw_toolbar'), 'shown tip not put away');
    await page.keyboard.press('Escape');
    // One a visit with a mouse: nothing else comes.
    await page.waitForTimeout(2600);
    expect(!(await page.$('.coach-layer')), 'a second tip in the same visit');
    // A new visit: the next tip, the wheel zoom on the plot. The address bar has dropped the query by
    // now (the app writes its own path), so the visit opens the test address again rather than reload.
    await page.goto('http://pc.local/next/BTCUSDT/1h?e2e=1&coach=1', { waitUntil: 'domcontentloaded' });
    await ready(page);
    await page.waitForSelector('.coach-layer.quiet .coach-card', { timeout: 8000 });
    expect((await page.getAttribute('.coach-layer', 'data-coach-tip')) === 'chart_pinch', 'second visit tip');
    expect((await page.textContent('.coach-card .coach-head b')) === 'زوم با چرخ موس', 'wheel wording for a mouse');
    await page.click('.coach-ok');
    await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    return env;
  });

  await test('coach: a touch screen gets the dimmed spotlight, the film and touch wording', async () => {
    const env = await open('BTCUSDT/1h?e2e=1&coach=1', { width: 390, height: 844, setup: touchScreen });
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.coach-layer:not(.quiet) .coach-card', { timeout: 8000 });
    expect(await page.$('canvas.coach-scrim'), 'no scrim on a touch screen');
    const id = await page.getAttribute('.coach-layer', 'data-coach-tip');
    expect(id === 'chart_pinch', `first phone tip ${id}`);
    expect((await page.textContent('.coach-card .coach-head b')) === 'زوم با دو انگشت', 'touch title');
    await page.waitForFunction(() => document.querySelector('.coach-film')?.dataset.painted === '1', null, { timeout: 3000 });
    await colourIn(page);
    expect((await painted(page, '.coach-film')) > 8, 'film is blank');
    // The scrim is dark where the page is, light through the hole over the plot.
    const dim = await page.evaluate(() => {
      const c = document.querySelector('canvas.coach-scrim'); const ctx = c.getContext('2d'); const dpr = c.width / innerWidth;
      const a = (x, y) => ctx.getImageData(Math.round(x * dpr), Math.round(y * dpr), 1, 1).data[3];
      const plot = document.querySelector('.chart-host table tr td:nth-child(2)').getBoundingClientRect();
      return { corner: a(2, 2), hole: a(plot.left + plot.width / 2, plot.top + plot.height / 2) };
    });
    await page.waitForTimeout(1500);
    const settled = await page.evaluate(() => {
      const c = document.querySelector('canvas.coach-scrim'); const ctx = c.getContext('2d'); const dpr = c.width / innerWidth;
      const plot = document.querySelector('.chart-host table tr td:nth-child(2)').getBoundingClientRect();
      return { corner: ctx.getImageData(2, 2, 1, 1).data[3], hole: ctx.getImageData(Math.round((plot.left + plot.width / 2) * dpr), Math.round((plot.top + plot.height / 2) * dpr), 1, 1).data[3] };
    });
    expect(settled.corner > 150 && settled.hole < 10, `scrim ${JSON.stringify(dim)} → ${JSON.stringify(settled)}`);
    // A tap anywhere on the glass is «فهمیدم»; the next one waits out the cooldown.
    await page.mouse.click(5, 5);
    await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    await page.waitForTimeout(2500);
    expect(!(await page.$('.coach-layer')), 'the next tip came inside the 25 s cooldown');
    expect((await stored(page)).dismissed?.includes('chart_pinch'), 'tip not put away');
    return env;
  });

  await test('coach: never over an open dialog; the tip waits until it closes', async () => {
    const env = await open('BTCUSDT/1h?e2e=1&coach=1');
    const { page } = env;
    await ready(page);
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'symbol' } })));
    await page.waitForSelector('[role=dialog]', { timeout: 5000 });
    await page.waitForTimeout(3000);
    expect(!(await page.$('.coach-layer')), 'a tip over the settings dialog');
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('.scrim, [role=dialog]'), null, { timeout: 5000 });
    await page.waitForSelector('.coach-layer .coach-card', { timeout: 6000 });
    return env;
  });

  await test('coach: «بلدم» puts the whole coach away', async () => {
    const env = await open('BTCUSDT/1h?e2e=1&coach=1');
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.coach-never', { timeout: 8000 });
    await page.click('.coach-never');
    await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    expect((await stored(page)).off === true, 'off not stored');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await ready(page);
    await page.waitForTimeout(2600);
    expect(!(await page.$('.coach-layer')), 'a tip after «بلدم»');
    return env;
  });

  await test('coach: the library plays every tip and brings them all back', async () => {
    const env = await open('tutorials/tips?e2e=1', {
      setup: (ctx) => ctx.addInitScript(() => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.setItem('pc.coach.v1', JSON.stringify({ dismissed: ['chart_pinch', 'legend'], off: true })); } }),
    });
    const { page } = env;
    await page.waitForSelector('.coach-row', { timeout: 10000 });
    const rows = await page.$$eval('.coach-row', (els) => els.map((e) => e.getAttribute('data-tip')));
    expect(rows.length === 16 && rows.includes('draw_cursor') && rows.includes('row_reorder'), `library rows ${rows}`);
    expect((await page.$$('.coach-group')).length === 3, 'three groups');
    // An animated tip: the card in the middle of the glass, the film running, no «بلدم».
    await page.click('.coach-row[data-tip="chart_axis"]');
    await page.waitForSelector('.coach-layer.preview .coach-card', { timeout: 3000 });
    expect(!(await page.$('.coach-never')), 'a preview offers «بلدم»');
    await page.waitForFunction(() => document.querySelector('.coach-film')?.dataset.painted === '1', null, { timeout: 3000 });
    await colourIn(page);
    const a = await painted(page, '.coach-film');
    await page.waitForTimeout(400);
    const frames = await page.evaluate(() => document.querySelector('.coach-film').toDataURL());
    await page.waitForTimeout(400);
    const moved = frames !== (await page.evaluate(() => document.querySelector('.coach-film').toDataURL()));
    expect(a > 8 && moved, `film blank (${a}) or still (${moved})`);
    const box = await page.locator('.coach-card').boundingBox();
    expect(Math.abs(box.x + box.width / 2 - 720) < 4, 'preview card not centred');
    await page.click('.coach-ok');
    await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    // Every row plays.
    for (const id of rows) {
      await page.click(`.coach-row[data-tip="${id}"]`);
      await page.waitForSelector(`.coach-layer[data-coach-tip="${id}"] .coach-card`, { timeout: 3000 });
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => !document.querySelector('.coach-layer'), null, { timeout: 3000 });
    }
    await page.click('[data-act="coach-replay-all"]');
    await page.waitForSelector('.coach-replay.done', { timeout: 2000 });
    const s = await stored(page);
    expect(s.off === false && s.dismissed.length === 0, `replay all left ${JSON.stringify(s)}`);
    // The library is a door of the main menu.
    await page.keyboard.press('Escape');
    await page.click('.ws-top .menu-btn');
    await page.click('.drawer-item:has-text("نکته‌های آموزشی")');
    await page.waitForSelector('.coach-row', { timeout: 5000 });
    return env;
  });

  await test('coach: first run — welcome, starter preferences, reader question; Simple hides chrome', async () => {
    const env = await open('BTCUSDT/1h?e2e=1&firstrun=1');
    const { page } = env;
    await page.waitForSelector('.first-run.welcome', { timeout: 10000 });
    await page.waitForSelector('.wl-slide[data-slide="chart"]', { timeout: 3000 });
    await page.waitForSelector('.wl-slide[data-slide="tools"]', { timeout: 6000 });
    await page.click('.wl-dots button >> nth=4');
    await page.waitForSelector('.wl-slide[data-slide="free"]', { timeout: 2000 });
    await page.waitForFunction(() => document.querySelector('.wl-art')?.dataset.done === '1', null, { timeout: 3000 });
    await page.click('.wl-primary');
    await page.waitForSelector('.first-run.starter', { timeout: 3000 });
    await page.click('.fr-pill:has-text("تیره")');
    await page.click('.fr-pill:has-text("English")');
    await page.waitForSelector('.first-run.starter h2:has-text("A few things, once")', { timeout: 2000 });
    await page.click('.fr-pill:has-text("Red")');
    await page.click('.fr-done');
    await page.waitForSelector('.first-run.question', { timeout: 3000 });
    expect((await page.textContent('.question h2')) === 'What should we put on your chart?', 'question in English');
    await page.click('.fr-mode[data-mode="simple"]');
    await page.waitForFunction(() => !document.querySelector('.first-run'), null, { timeout: 3000 });
    await ready(page);
    const st = await page.evaluate(() => ({
      theme: document.documentElement.getAttribute('data-theme'), lang: document.documentElement.lang,
      reader: document.documentElement.getAttribute('data-reader'), rise: document.documentElement.getAttribute('data-rise'),
      up: getComputedStyle(document.documentElement).getPropertyValue('--up').trim(),
      stored: JSON.parse(localStorage.getItem('pc.firstrun.v1')), chartUp: JSON.parse(localStorage.getItem('pc.chartsettings.v1') ?? '{}').up,
      replayShown: [...document.querySelectorAll('.ws-top .ib')].some((b) => b.querySelector('use[href="#ph-rewind"]') && b.offsetParent),
      gridShown: Boolean(document.querySelector('.ws-top .grid-btn')?.offsetParent),
    }));
    expect(st.theme === 'dark' && st.lang === 'en' && st.reader === 'simple' && st.rise === 'red', `applied ${JSON.stringify(st)}`);
    expect(st.up.toUpperCase() === '#F23645' && st.chartUp === '#F23645', `red rise ${st.up} ${st.chartUp}`);
    expect(st.stored.welcome && st.stored.starter && st.stored.reader === 'simple', `stored ${JSON.stringify(st.stored)}`);
    expect(!st.replayShown && !st.gridShown, 'Simple still shows replay or multi-chart');
    // The menu switches the mode back in one tap.
    await page.click('.ws-top .menu-btn');
    await page.click('.reader-row button[data-mode="pro"]');
    expect(!(await page.evaluate(() => document.documentElement.getAttribute('data-reader'))), 'Pro still simple');
    await page.keyboard.press('Escape');
    // Shown once.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await ready(page);
    expect(!(await page.$('.first-run')), 'first run shown twice');
    return env;
  });

  await test('coach: first run is skipped for a returning reader and «بعداً» is Trader', async () => {
    const back = await open('BTCUSDT/1h?e2e=1&firstrun=1', { setup: (ctx) => ctx.addInitScript(() => { if (!localStorage.getItem('pc.prefs.v1')) localStorage.setItem('pc.prefs.v1', JSON.stringify({ lang: 'fa' })); }) });
    await ready(back.page);
    await back.page.waitForTimeout(800);
    expect(!(await back.page.$('.first-run')), 'a returning reader was shown the first run');
    await back.ctx.close();
    const env = await open('BTCUSDT/1h?e2e=1&firstrun=1', { width: 390, height: 844 });
    const { page } = env;
    await page.waitForSelector('.first-run.welcome', { timeout: 10000 });
    // A coach tip never comes over the first run.
    await page.click('.wl-primary');
    await page.click('.fr-done');
    await page.click('.fr-skip');
    await page.waitForFunction(() => !document.querySelector('.first-run'), null, { timeout: 3000 });
    const s = await page.evaluate(() => JSON.parse(localStorage.getItem('pc.firstrun.v1')));
    expect(s.reader === 'trader', `skip stored ${s.reader}`);
    expect(!(await page.evaluate(() => document.documentElement.getAttribute('data-reader'))), 'Trader hides chrome');
    return env;
  });

  await test('coach: rail and top-bar tooltips after 500 ms, follow the pointer, not on focus', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const btn = page.locator('.ws-top .menu-btn');
    const label = await btn.getAttribute('title');
    const b = await btn.boundingBox();
    await page.mouse.move(b.x + 4, b.y + 4);
    await page.waitForTimeout(250);
    expect(!(await page.isVisible('.chrome-tip')), 'tooltip before 500 ms');
    expect(!(await btn.getAttribute('title')), 'native title left on while hovered');
    await page.waitForTimeout(450);
    expect(await page.isVisible('.chrome-tip'), 'no tooltip after 500 ms');
    expect((await page.textContent('.chrome-tip')) === label, 'tooltip text');
    const p1 = await page.locator('.chrome-tip').boundingBox();
    await page.mouse.move(b.x + b.width - 4, b.y + b.height - 4);
    const p2 = await page.locator('.chrome-tip').boundingBox();
    expect(p2.x > p1.x && p2.y > p1.y, `tooltip did not follow ${JSON.stringify(p1)} → ${JSON.stringify(p2)}`);
    await page.mouse.move(700, 450);
    await page.waitForTimeout(100);
    expect(!(await page.isVisible('.chrome-tip')), 'tooltip stayed after leaving');
    expect((await btn.getAttribute('title')) === label, 'title not put back');
    // A rail tool names itself too.
    const tool = page.locator('.ws-rail .rail-group .tool').first();
    const tb = await tool.boundingBox();
    await page.mouse.move(tb.x + tb.width / 2, tb.y + tb.height / 2);
    await page.waitForTimeout(700);
    const shown = await page.isVisible('.chrome-tip');
    const flyout = await page.$('.dfly');
    expect(shown || flyout, 'rail tool: no tooltip and no flyout');
    await page.mouse.move(700, 450);
    // Keyboard focus never shows it.
    await btn.focus();
    await page.waitForTimeout(700);
    expect(!(await page.isVisible('.chrome-tip')), 'tooltip on focus');
    return env;
  });
}
