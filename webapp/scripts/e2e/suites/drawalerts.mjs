// Drawings ↔ alerts, and drawing on a phone (area A3): an alert made from a drawing follows it,
// deleting a drawing that carries alerts asks, the alert lists mark a gone drawing and sketch a live
// one, shape alerts (rectangle, channel), and the phone's drawing mode (placement cursor, floating
// toolbar, drawing band).
export default async function ({ test, open, ready, pc, expect }) {
  const layer = (page, fn, arg) => page.evaluate(([src, a]) => (0, eval)(src)(window.__pc.useChartBus.getState().drawingLayer, a), [String(fn), arg]);
  const count = (page) => layer(page, (l) => l.getDrawings().length);
  const alerts = (page) => pc(page, () => window.__pc.useAlerts.getState().list);
  const last = (page) => pc(page, () => window.__pc.useChartBus.getState().bars.at(-1).c);
  const tick = (page, price) => pc(page, (p) => {
    const m = window.__pc.useMarket.getState();
    m.put([{ symbol: 'BTCUSDT', last: p, change: 0, changePct: 0, ts: Date.now() }]);
  }, price);

  await test('drawalerts: an alert on a line follows it; deleting the line asks (keep / both / line only); the lists mark a gone drawing', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const c = await last(page);
    const l1 = Math.round(c * 1.2), l2 = Math.round(c * 1.4);
    const i = await layer(page, (l, p) => l.addHLine(p), l1);
    const id = await layer(page, (l, k) => l.getDrawings()[k].id, i);
    expect(typeof id === 'string' && id.length > 3, `no stable id ${id}`);
    // The id is kept with the drawing.
    expect(await page.evaluate((d) => JSON.parse(localStorage.getItem('pc.drawings.v1.BTCUSDT')).some((x) => x.id === d), id), 'id not saved');
    // An alert from the drawing's style bar.
    await layer(page, (l, k) => l.selectAt(k), i);
    await page.$eval('[data-act="alert"]', (e) => e.click());
    await page.waitForSelector('.alert-dlg');
    expect((await page.textContent('.alert-dlg')).includes('با ترسیم جابه‌جا می‌شود'), 'the dialog does not say the alert follows the drawing');
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    let list = await alerts(page);
    expect(list.length === 1 && list[0].drawingId === id && list[0].drawingType === 'hline', `alert ${JSON.stringify(list)}`);
    // Move the line: the alert takes the new level.
    await layer(page, (l, a) => l.setStyle(a.k, { p: a.p }), { k: i, p: l2 });
    await page.waitForTimeout(500);
    list = await alerts(page);
    expect(Math.abs(list[0].value - l2) < 1e-6, `value ${list[0].value} != ${l2}`);
    // Crossing the old level does nothing; crossing the new one fires.
    await tick(page, l1 + 5);
    expect((await alerts(page))[0].active, 'fired on the old level');
    await tick(page, l2 + 5);
    expect(!(await alerts(page))[0].active, 'did not fire on the moved line');
    // Re-arm, then delete the line: the question.
    await pc(page, () => window.__pc.useAlerts.getState().restartInactive());
    await page.mouse.move(5, 5);
    await layer(page, (l, k) => l.selectAt(k), i);
    await page.keyboard.press('Delete');
    await page.waitForSelector('.dialog.dad');
    const msg = await page.textContent('.dialog.dad');
    expect(msg.includes('روی این ترسیم هشدار هست') && msg.includes('۱ هشدار این خط را می‌پاید'), `dialog text ${msg}`);
    await page.click('.dad [data-act="cancel"]');
    expect(await count(page) === 1, 'cancel removed the line');
    // Line only: the alert stays and is marked as having lost its drawing.
    await layer(page, (l) => l.selectAt(0));
    await page.keyboard.press('Delete');
    await page.click('.dad [data-act="keep"]');
    expect(await count(page) === 0, 'keep did not remove the line');
    expect((await alerts(page)).length === 1, 'keep removed the alert');
    await pc(page, () => window.__pc.usePanels.getState().show('alerts'));
    await page.waitForSelector('.alert-row [data-orphan]');
    expect((await page.textContent('.alert-row [data-orphan]')).includes('ترسیمش نیست'), 'orphan pill text');
    const red = await page.$eval('.alert-row [data-orphan]', (e) => getComputedStyle(e).color);
    expect(red.replace(/\s/g, '') === 'rgb(242,54,69)', `orphan colour ${red}`);
    // The alert centre says the same.
    await pc(page, () => window.__pc.usePage.getState().open('alerts'));
    await page.click('.tabs button:has-text("هشدارهای این مرورگر"), [role="tab"]:has-text("هشدارهای این مرورگر")').catch(() => undefined);
    await page.waitForSelector('.page-inner [data-orphan]', { timeout: 5000 });
    await pc(page, () => window.__pc.usePage.getState().open(null));
    // Undo brings the line back: a sketch instead of the pill.
    await page.mouse.move(5, 5);
    await layer(page, (l) => l.undo());
    await page.waitForSelector('.alert-row [data-sketch]');
    expect(await page.locator('.alert-row [data-orphan]').count() === 0, 'orphan pill after undo');
    // Both: the line and the alert go.
    await layer(page, (l) => { l.selectAt(0); l.removeSelected(); });
    await page.click('.dad [data-act="both"]');
    expect(await count(page) === 0 && (await alerts(page)).length === 0, 'both did not remove both');
    // English wording.
    await pc(page, () => window.__pc.usePrefs.getState().setLang('en'));
    const j = await layer(page, (l, p) => l.addHLine(p), l1);
    const jid = await layer(page, (l, k) => l.getDrawings()[k].id, j);
    await pc(page, (d) => window.__pc.useAlerts.getState().create({ symbol: 'BTCUSDT', condition: 'cross', value: 1, drawingId: d, drawingType: 'hline' }), jid);
    await layer(page, (l, k) => l.removeAt(k), j);
    await page.waitForSelector('.dialog.dad');
    const en = await page.textContent('.dialog.dad');
    expect(en.includes('This drawing carries alerts') && en.includes('1 alerts watch this line') && en.includes('Delete the drawing, keep the alerts'), `english ${en}`);
    await page.keyboard.press('Escape');
    expect(await count(page) === 1, 'Escape deleted');
    return env;
  });

  await test('drawalerts: a rectangle alert is its top and bottom (enter / exit); a channel is its two rails', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const c = await last(page);
    const t = await pc(page, () => { const b = window.__pc.useChartBus.getState().bars; return [b.at(-30).t, b.at(-5).t]; });
    const lo = Math.round(c * 1.3), hi = Math.round(c * 1.35);
    await layer(page, (l, a) => l.addDrawing({ type: 'rect', p0: { t: a.t[0], p: a.hi }, p1: { t: a.t[1], p: a.lo }, color: '#2962FF', width: 2 }), { t, lo, hi });
    await page.$eval('[data-act="alert"]', (e) => e.click());
    await page.waitForSelector('.alert-dlg [data-band]');
    const opts = await page.$$eval('.alert-dlg select[aria-label="شرط"] option', (os) => os.map((o) => o.value));
    expect(opts[0] === 'enter_channel' && opts.includes('exit_channel') && opts.includes('cross'), `conditions ${opts}`);
    const src = await page.$eval('.alert-dlg select[aria-label="منبع"]', (s) => s.selectedOptions[0].textContent);
    expect(src.includes('مستطیل'), `source ${src}`);
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    const a = (await alerts(page))[0];
    expect(a.condition === 'enter_channel' && a.value === lo && a.high === hi && a.source === 'line' && a.drawingType === 'rect', `rect alert ${JSON.stringify(a)}`);
    // The row names the drawing and sketches it.
    await pc(page, () => window.__pc.usePanels.getState().show('alerts'));
    await page.waitForSelector('.alert-row [data-sketch]');
    expect((await page.textContent('.alert-row .alert-cond')).includes('مستطیل'), 'row does not name the rectangle');
    // Entering the rectangle fires.
    await tick(page, lo - 50);
    expect((await alerts(page))[0].active, 'fired outside');
    await tick(page, Math.round((lo + hi) / 2));
    expect(!(await alerts(page))[0].active, 'entering the rectangle did not fire');
    // A channel's two rails.
    await pc(page, () => window.__pc.useAlerts.setState({ list: [] }));
    const band = await layer(page, (l, a) => {
      l.addDrawing({ type: 'channel', pts: [{ t: a.t[0], p: a.lo }, { t: a.t[1], p: a.lo }, { t: a.t[0], p: a.hi }], color: '#2962FF', width: 2 });
      return null;
    }, { t, lo, hi });
    void band;
    await page.$eval('[data-act="alert"]', (e) => e.click());
    await page.waitForSelector('.alert-dlg [data-band]');
    const txt = await page.textContent('.alert-dlg [data-band]');
    expect(txt.replace(/,/g, '').includes(String(lo)) && txt.replace(/,/g, '').includes(String(hi)), `channel band ${txt}`);
    await page.selectOption('.alert-dlg select[aria-label="شرط"]', 'exit_channel');
    await page.click('.alert-dlg .dialog-foot .btn-primary');
    const ch = (await alerts(page))[0];
    expect(ch.condition === 'exit_channel' && ch.drawingType === 'channel' && ch.value === lo && ch.high === hi, `channel alert ${JSON.stringify(ch)}`);
    return env;
  });

  // A finger on the chart: pointer events of type touch on the drawing canvas (moves and ups on window).
  const finger = (page, steps) => page.evaluate((list) => {
    const cv = document.querySelector('.cell-main canvas.draw-layer') ?? document.querySelector('canvas.draw-layer');
    const r = cv.getBoundingClientRect();
    for (const [type, x, y] of list) {
      const target = type === 'pointerdown' ? cv : window;
      target.dispatchEvent(new PointerEvent(type, { pointerType: 'touch', pointerId: 7, isPrimary: true, button: 0, buttons: type === 'pointerup' ? 0 : 1, clientX: r.left + x, clientY: r.top + y, bubbles: true, cancelable: true }));
    }
  }, steps);
  const dot = (page) => page.$eval('.place-cursor', (e) => ({ x: parseFloat(e.style.getPropertyValue('--px')), y: parseFloat(e.style.getPropertyValue('--py')), hidden: e.hidden }));
  const banner = (page) => page.$eval('.draw-banner', (e) => ({ text: e.querySelector('.db-text').textContent, count: e.querySelector('.db-count').hidden ? '' : e.querySelector('.db-count').textContent, hidden: e.hidden }));

  await test('drawalerts: phone — the pencil enters drawing mode; the placement cursor moves like a trackpad and a tap places the point at the dot', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page } = env;
    await ready(page);
    await page.click('[data-act="draw-mode"]');
    await page.waitForSelector('.dft');
    await page.waitForSelector('.draw-band');
    expect(!(await page.locator('.ws-timebar').isVisible()), 'the time bar still shows in drawing mode');
    // A tap on the lines glyph arms the group's tool.
    await page.click('.dft [data-group="lines"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'trend', 'lines glyph did not arm the trend line');
    await page.waitForSelector('.place-cursor:not([hidden])');
    let b = await banner(page);
    expect(b.text === 'نشانگر را روی نقطه‌ی شروع ببرید' && b.count === '۱ از ۴', `banner ${JSON.stringify(b)}`);
    const d0 = await dot(page);
    // Drag: the dot moves by the finger's travel, not to the finger.
    await finger(page, [['pointerdown', 40, 500], ['pointermove', 60, 480], ['pointermove', 10, 520], ['pointerup', 10, 520]]);
    const d1 = await dot(page);
    expect(Math.abs(d1.x - (d0.x - 30)) < 1.5 && Math.abs(d1.y - (d0.y + 20)) < 1.5, `trackpad move ${JSON.stringify([d0, d1])}`);
    b = await banner(page);
    expect(b.text === 'بزنید تا نقطه‌ی اول ثبت شود' && b.count === '۲ از ۴', `banner after move ${JSON.stringify(b)}`);
    // A tap far from the dot places the point at the dot.
    await finger(page, [['pointerdown', 300, 650], ['pointerup', 300, 650]]);
    b = await banner(page);
    expect(b.text === 'نشانگر را روی نقطه‌ی بعد ببرید' && b.count === '۳ از ۴', `banner after first point ${JSON.stringify(b)}`);
    await finger(page, [['pointerdown', 100, 400], ['pointermove', 160, 360], ['pointermove', 200, 340], ['pointerup', 200, 340]]);
    const d2 = await dot(page);
    b = await banner(page);
    expect(b.text === 'بزنید تا ترسیم تمام شود' && b.count === '۴ از ۴', `banner before finishing ${JSON.stringify(b)}`);
    // The shape stretches to the dot before it is committed.
    const tmp = await layer(page, (l) => (l.tmp ? { x: l._x(l.tmp.p1.t), y: l._y(l.tmp.p1.p) } : null));
    expect(tmp && Math.abs(tmp.y - d2.y) < 2, `preview not stretched to the dot ${JSON.stringify([tmp, d2])}`);
    await finger(page, [['pointerdown', 20, 700], ['pointerup', 20, 700]]);
    expect(await count(page) === 1, 'two taps did not place a trend line');
    const ends = await layer(page, (l) => { const d = l.getDrawings()[0]; return [{ x: l._x(d.p0.t), y: l._y(d.p0.p) }, { x: l._x(d.p1.t), y: l._y(d.p1.p) }]; });
    expect(Math.abs(ends[0].y - d1.y) < 2 && Math.abs(ends[1].y - d2.y) < 2, `points not at the dot ${JSON.stringify([ends, d1, d2])}`);
    // Placed: the tool goes back to the cursor and the pointer goes.
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'cursor', 'tool not reset');
    expect((await dot(page)).hidden && (await banner(page)).hidden, 'cursor still shown');
    // The bin drops the point in progress, then leaves the tool.
    await page.click('.dft [data-group="lines"]');
    await finger(page, [['pointerdown', 40, 500], ['pointermove', 70, 470], ['pointerup', 70, 470], ['pointerdown', 40, 500], ['pointerup', 40, 500]]);
    expect(await layer(page, (l) => Boolean(l.twoClick && l.tmp)), 'first point not pending');
    await page.click('.draw-banner .db-cancel');
    expect(await layer(page, (l) => !l.tmp), 'bin did not drop the point');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'trend', 'bin with a point should keep the tool');
    await page.click('.draw-banner .db-cancel');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'cursor', 'second bin did not leave the tool');
    // A horizontal line is one tap.
    await pc(page, () => window.__pc.useDrawing.getState().setTool('hline', 'lines'));
    b = await banner(page);
    expect(b.count === '۱ از ۲', `hline count ${b.count}`);
    await finger(page, [['pointerdown', 40, 500], ['pointermove', 70, 470], ['pointerup', 70, 470], ['pointerdown', 40, 500], ['pointerup', 40, 500]]);
    expect(await count(page) === 2, 'hline not placed');
    // A mouse draws directly (no cursor).
    await pc(page, () => { window.__pc.usePrefs.getState().setLang('en'); window.__pc.useDrawing.getState().setTool('trend', 'lines'); });
    b = await banner(page);
    expect(b.text === 'Move cursor to start point' && b.count === '1 of 4', `english banner ${JSON.stringify(b)}`);
    const box = await page.locator('.cell-main .chart-host, .chart-host').first().boundingBox();
    await page.mouse.move(box.x + 100, box.y + 300);
    expect((await dot(page)).hidden, 'cursor shown under a mouse');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(!overflow, 'horizontal overflow');
    return env;
  });

  await test('drawalerts: phone — the floating toolbar drags, folds, arms on tap and opens a group on a long press; the band has its switches', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page } = env;
    await ready(page);
    await page.click('[data-act="draw-mode"]');
    await page.waitForSelector('.dft');
    const groups = await page.$$eval('.dft [data-group]', (els) => els.map((e) => e.dataset.group));
    expect(groups.length >= 6 && !groups.includes('cursors'), `groups ${groups}`);
    // Toolbar inside the plot, left side, below the legend.
    const tb = await page.locator('.dft').boundingBox();
    const grid = await page.locator('.chart-grid').boundingBox();
    expect(tb.x >= grid.x && tb.x < grid.x + 30 && tb.y >= grid.y + 60 && tb.y + 24 <= grid.y + grid.height, `toolbar ${JSON.stringify([tb, grid])}`);
    // Long press opens the group's tools; a row arms one.
    await page.$eval('.dft [data-group="fib"]', (e) => e.dispatchEvent(new PointerEvent('pointerdown', { pointerType: 'touch', pointerId: 3, bubbles: true })));
    await page.waitForTimeout(650);
    await page.$eval('.dft [data-group="fib"]', (e) => e.dispatchEvent(new PointerEvent('pointerup', { pointerType: 'touch', pointerId: 3, bubbles: true })));
    await page.waitForSelector('.dft-fly[data-fly="fib"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) !== 'fib', 'a long press armed the tool');
    await page.click('.dft-fly [data-tool="fibext"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'fibext', 'flyout row did not arm');
    expect(await page.getAttribute('.dft [data-group="fib"]', 'data-tool') === 'fibext', 'the glyph shows the armed tool');
    // A tap on the armed glyph disarms; a tap again arms the group's last-used tool.
    await page.click('.dft [data-group="fib"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'cursor', 'tap on armed glyph did not disarm');
    await page.click('.dft [data-group="fib"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'fibext', 'tap did not arm the last-used tool');
    // The grip folds to the armed tool, and back.
    await page.click('.dft-grip');
    await page.waitForSelector('.dft.folded');
    expect(await page.locator('.dft .dft-tool').count() === 1, 'folded shows more than the armed tool');
    await page.click('.dft-grip');
    await page.waitForSelector('.dft:not(.folded)');
    // The grip drags the toolbar.
    const g = await page.locator('.dft-grip').boundingBox();
    await page.$eval('.dft-grip', (e, p) => {
      const ev = (type, x, y) => e.dispatchEvent(new PointerEvent(type, { pointerType: 'touch', pointerId: 4, clientX: x, clientY: y, bubbles: true }));
      ev('pointerdown', p.x, p.y); ev('pointermove', p.x + 60, p.y + 80); ev('pointermove', p.x + 120, p.y + 160); ev('pointerup', p.x + 120, p.y + 160);
    }, { x: g.x + g.width / 2, y: g.y + g.height / 2 });
    const tb2 = await page.locator('.dft').boundingBox();
    expect(tb2.x - tb.x > 80 && tb2.y - tb.y > 100, `drag ${JSON.stringify([tb, tb2])}`);
    expect(!(await page.locator('.dft.folded').count()), 'a drag folded the toolbar');
    // The band: magnet, lock, eye, object tree, the palette.
    await page.click('.draw-band [data-act="magnet"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().magnet), 'magnet');
    await page.click('.draw-band [data-act="lock"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().locked), 'lock');
    await page.click('.draw-band [data-act="eye"]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().hidden), 'eye');
    await page.click('.draw-band [data-act="eye"]');
    await page.click('.draw-band [data-act="objects"]');
    expect(await pc(page, () => window.__pc.usePanels.getState().open) === 'objects', 'object tree');
    await pc(page, () => window.__pc.usePanels.getState().toggle('objects'));
    await page.click('.draw-band [data-act="tools"]');
    expect(await page.evaluate(() => document.body.classList.contains('rail-open')), 'palette');
    const labels = await page.$$eval('.draw-band [data-act]', (els) => els.map((e) => e.getAttribute('aria-label')));
    expect(labels[0] === 'خروج از حالت ترسیم' && labels.includes('تمام‌صفحه') && labels.includes('درخت ترسیم‌ها'), `band labels ${labels}`);
    // Leaving drawing mode.
    await page.click('.draw-band [data-act="exit"]');
    await page.waitForSelector('.dft', { state: 'detached' });
    expect(await page.locator('.draw-band').count() === 0 && await page.locator('.ws-timebar').isVisible(), 'band or time bar after leaving');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'cursor' && !(await page.evaluate(() => document.body.classList.contains('rail-open'))), 'leaving did not reset');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(!overflow, 'horizontal overflow');
    return env;
  });
}
