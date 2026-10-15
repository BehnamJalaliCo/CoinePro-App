// Drawings (area A): the rail and its menus, cursors, magnet and switches, text tools, keyboard,
// zoom, the floating style bar, the drawing menu and settings dialog, the object tree, touch, and
// TradingView's newer tools. Every feature the drawings area added has a check here.
export default async function ({ test, open, ready, pc, expect }) {
  const layer = (page, fn, arg) => page.evaluate(([src, a]) => (0, eval)(src)(window.__pc.useChartBus.getState().drawingLayer, a), [String(fn), arg]);
  const count = (page) => layer(page, (l) => l.getDrawings().length);
  const box = (page) => page.locator('.chart-host').boundingBox();
  const at = (b, fx, fy) => [b.x + b.width * fx, b.y + b.height * fy];
  const lastClose = (page) => pc(page, () => window.__pc.useChartBus.getState().bars.at(-1).c);
  // A trend line between two points of the chart, by mouse; returns its two screen points.
  const drawTrend = async (page, a = [0.35, 0.6], z = [0.6, 0.35]) => {
    const b = await box(page);
    await pc(page, () => window.__pc.useDrawing.getState().setTool('trend', 'lines'));
    const p0 = at(b, ...a), p1 = at(b, ...z);
    await page.mouse.click(...p0); await page.mouse.click(...p1);
    await page.waitForTimeout(120);
    return [p0, p1];
  };

  await test('drawings: rail menus open beside the rail, unclipped; hover opens; star, help, tooltip', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('[data-group="lines"]');
    await page.waitForSelector('.dfly[data-menu="lines"]');
    const row = page.locator('.dfly [data-tool="hray"]');
    const r = await row.boundingBox();
    const hit = await page.evaluate(([x, y]) => Boolean(document.elementFromPoint(x, y)?.closest('[data-tool="hray"]')), [r.x + r.width / 2, r.y + r.height / 2]);
    expect(hit, 'a menu row is covered or clipped');
    expect(await page.locator('.dfly .dfly-sec').count() >= 3, 'trend tools have their LINES / CHANNELS / PITCHFORKS sections');
    expect((await page.locator('.dfly [data-tool="hray"] kbd').textContent()).includes('Alt+J'), 'Alt+J shown for the horizontal ray');
    await row.click();
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'hray', 'picking a row arms the tool');
    // Tooltips: name · key.
    const title = await page.getAttribute('.ws-rail [data-tool="hray"]', 'title');
    expect(/·\s*Alt\+J/.test(title), `tooltip ${title}`);
    // Hover opens after a pause.
    await page.hover('.ws-rail .rail-group:has([data-group="fib"]) .tool');
    await page.waitForSelector('.dfly[data-menu="fib"]', { timeout: 3000 });
    // Star a tool: pinned on the rail, kept.
    await page.hover('.dfly [data-tool="fib3"]');
    await page.click('.dfly [data-tool="fib3"] .dfly-star');
    await page.waitForSelector('.ws-rail .rail-fav');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('pc.drawing.v1')).favorites);
    expect(saved.includes('fib3'), 'favourite not kept');
    // The floating favourites toolbar.
    await page.keyboard.press('Escape');
    await page.click('[data-tool="favbar"]');
    await page.waitForSelector('.dfav [aria-label]');
    await page.click('.dfav-close');
    // Help for a tool.
    await page.click('[data-group="lines"]');
    await page.hover('.dfly [data-tool="trend"]');
    await page.click('.dfly [data-tool="trend"] .dfly-help');
    const pg = await pc(page, () => ({ page: window.__pc.usePage.getState().page, param: window.__pc.usePage.getState().param }));
    expect(pg.page === 'help' && pg.param === 'trend', `help ${JSON.stringify(pg)}`);
    return env;
  });

  await test('drawings: cursors (dot, arrow, demonstration, eraser), kept; crosshair hidden where it should be', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('[data-group="cursors"]');
    await page.click('.dfly [data-tool="dot"]');
    expect(await page.getAttribute('.ws-chart', 'data-cursor') === 'dot', 'dot cursor');
    const mode = () => pc(page, () => window.__pc.useChartBus.getState().api.options().crosshair.mode);
    await page.waitForTimeout(80);
    expect(await mode() === 0, 'dot keeps the crosshair');
    await pc(page, () => window.__pc.useDrawing.getState().setTool('arrowcursor', 'cursors'));
    await page.waitForTimeout(80);
    expect(await mode() === 2, 'arrow hides the crosshair');
    await pc(page, () => window.__pc.useDrawing.getState().setTool('demonstration', 'cursors'));
    await page.waitForSelector('canvas.draw-demo', { state: 'attached' });
    const b = await box(page);
    await page.mouse.move(...at(b, 0.3, 0.3)); await page.mouse.move(...at(b, 0.5, 0.4), { steps: 6 });
    // Eraser: a click on a drawing removes it.
    await pc(page, () => window.__pc.useDrawing.getState().setTool('eraser', 'cursors'));
    const price = await lastClose(page);
    await layer(page, (l, p) => l.addHLine(p, '#2962FF'), price);
    const y = await pc(page, (p) => window.__pc.useChartBus.getState().series.priceToCoordinate(p), price);
    await page.mouse.move(b.x + b.width * 0.4, b.y + y + 3);
    await page.mouse.click(b.x + b.width * 0.4, b.y + y);
    expect(await count(page) === 0, 'eraser did not remove the line');
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem('pc.drawing.v1')).cursor) === 'eraser', 'cursor not kept');
    const again = await env.ctx.newPage();
    await again.goto('http://pc.local/next/BTCUSDT/1h?e2e=1', { waitUntil: 'domcontentloaded' });
    await ready(again);
    expect(await pc(again, () => window.__pc.useDrawing.getState().tool) === 'eraser', 'cursor not restored');
    return env;
  });

  await test('drawings: magnet weak/strong snaps the crosshair; stay in drawing; lock and hide kept across symbols', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('[data-tool="magnet"]');
    await page.waitForTimeout(100);
    expect(await pc(page, () => window.__pc.useChartBus.getState().api.options().crosshair.mode) === 3, 'magnet: crosshair on OHLC');
    await page.click('[data-group="magnet"]');
    await page.click('.dfly[data-menu="magnet"] .dfly-row >> nth=0');
    expect(await pc(page, () => window.__pc.useDrawing.getState().magnetMode) === 'weak', 'weak magnet');
    await page.click('[data-tool="stay"]');
    await drawTrend(page);
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'trend', 'stay in drawing: the tool stays armed');
    await page.keyboard.press('Control+Alt+d');
    expect(!(await pc(page, () => window.__pc.useDrawing.getState().stay)), 'Ctrl+Alt+D toggles stay');
    await page.keyboard.press('Escape');
    // Lock all, then a new symbol: its drawings come up locked.
    await page.evaluate(() => localStorage.setItem('pc.drawings.v1.ETHUSDT', JSON.stringify([{ type: 'hline', p: 2400, color: '#2962FF', width: 2 }])));
    await page.click('[data-tool="lock"]');
    await page.click('[data-tool="hide"]');
    await pc(page, () => window.__pc.usePrefs.getState().setSymbol('ETHUSDT'));
    await page.waitForTimeout(300); await ready(page);
    expect(await layer(page, (l) => l.getDrawings().every((d) => d.locked)), 'lock not re-applied after a symbol switch');
    expect(await page.evaluate(() => document.querySelector('canvas.draw-layer').style.visibility) === 'hidden', 'hide not kept');
    const kept = await page.evaluate(() => JSON.parse(localStorage.getItem('pc.drawing.v1')));
    expect(kept.magnet && kept.magnetMode === 'weak' && kept.locked && kept.hidden, `not kept: ${JSON.stringify(kept)}`);
    return env;
  });

  await test('drawings: text-bearing tools ask for text on creation and on double click; cancel leaves no undo step', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const b = await box(page);
    const ids = ['note', 'callout', 'signpost', 'flag', 'pricelabel', 'comment', 'pin', 'anchoredtext', 'pricenote'];
    for (const [k, id] of ids.entries()) {
      await pc(page, (x) => window.__pc.useDrawing.getState().setTool(x), id);
      await page.mouse.click(...at(b, 0.12 + k * 0.08, 0.45 + k * 0.04));
      if (!(await page.locator('.dtext').count())) await page.mouse.click(...at(b, 0.16 + k * 0.08, 0.35));
      await page.waitForSelector('.dtext textarea', { timeout: 3000 });
      await page.fill('.dtext textarea', `e2e ${id}`);
      await page.click('.dtext [data-act="ok"]');
      const t = await layer(page, (l) => l.getDrawings().at(-1).text);
      expect(t === `e2e ${id}`, `${id}: text ${t}`);
      await page.keyboard.press('Escape');
    }
    // Double click on the note opens its text again.
    const n = await layer(page, (l) => l.getDrawings().findIndex((d) => d.type === 'note'));
    const pt = await layer(page, (l, i) => { const h = l._handlePoints(l.getDrawings()[i])[0]; return [h.x, h.y]; }, n);
    await page.mouse.move(b.x + pt[0], b.y + pt[1]);
    await page.mouse.dblclick(b.x + pt[0], b.y + pt[1]);
    await page.waitForSelector('.dtext textarea', { timeout: 3000 });
    expect(await page.inputValue('.dtext textarea') === 'e2e note', 'double click did not open the note');
    await page.click('.dtext [data-act="cancel"]');
    // A new text cancelled: gone, and undo does not bring it back.
    const before = await count(page);
    await pc(page, () => window.__pc.useDrawing.getState().setTool('text', 'annotations'));
    await page.mouse.click(...at(b, 0.3, 0.3));
    await page.waitForSelector('.dtext textarea');
    await page.keyboard.press('Escape');
    expect(await count(page) === before, 'cancelled text stayed');
    await page.keyboard.press('Control+z');
    expect(await count(page) === before - 1, 'undo after a cancelled text did not undo the previous drawing');
    return env;
  });

  await test('drawings: keyboard — Alt+J/C, Alt+R resets, Ctrl+C/V, Ctrl+A, arrows nudge, Delete all, Esc, Ctrl+Alt+R', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.mouse.click(...at(await box(page), 0.5, 0.5));
    await page.keyboard.press('Alt+j');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'hray', 'Alt+J');
    await page.keyboard.press('Alt+c');
    expect(await pc(page, () => window.__pc.useDrawing.getState().tool) === 'crossline', 'Alt+C');
    await page.keyboard.press('Escape');
    // Alt+R: back to the default view, not the ray.
    await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().setVisibleLogicalRange({ from: 10, to: 60 }));
    await page.keyboard.press('Alt+r');
    await page.waitForTimeout(150);
    const s = await pc(page, () => ({ tool: window.__pc.useDrawing.getState().tool, r: window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange(), n: window.__pc.useChartBus.getState().bars.length }));
    expect(s.tool !== 'ray' && s.r.to > s.n - 2, `Alt+R ${JSON.stringify(s)}`);
    const price = await lastClose(page);
    await layer(page, (l, p) => { l.addHLine(p); l.addHLine(p * 1.01); l.selectAt(0); }, price);
    await page.keyboard.press('Control+c');
    await page.keyboard.press('Control+v');
    expect(await count(page) === 3, 'Ctrl+C / Ctrl+V');
    await page.keyboard.press('Control+a');
    expect((await layer(page, (l) => l.selectedIndices().length)) === 3, 'Ctrl+A');
    const p0 = await layer(page, (l) => l.getDrawings()[0].p);
    const range = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Shift+ArrowUp');
    expect((await layer(page, (l) => l.getDrawings()[0].p)) > p0, 'arrows did not move the selection');
    const range2 = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    expect(range.from === range2.from && range.to === range2.to, 'arrows zoomed the chart while a drawing was selected');
    await page.keyboard.press('Delete');
    expect(await count(page) === 0, 'Delete removed only one of the selection');
    await layer(page, (l, p) => { l.addHLine(p); l.selectAt(0); }, price);
    await page.keyboard.press('Escape');
    expect((await layer(page, (l) => l.selected)) === -1, 'Esc keeps the selection');
    await pc(page, () => window.__pc.useStudies.getState().add('rsi'));
    await page.keyboard.press('Control+Alt+r');
    await page.waitForTimeout(100);
    expect(await count(page) === 0 && (await pc(page, () => window.__pc.useStudies.getState().list.filter((x) => x.key !== '__volume').length)) === 0, 'Ctrl+Alt+R clears the chart');
    return env;
  });

  await test('drawings: zoom in by box, zoom out steps back; undo history is per symbol; vertical line date in the time zone', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const b = await box(page);
    const r0 = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    await page.click('[data-tool="zoom"]');
    await page.mouse.move(...at(b, 0.3, 0.3)); await page.mouse.down(); await page.mouse.move(...at(b, 0.5, 0.6), { steps: 5 }); await page.mouse.up();
    await page.waitForTimeout(150);
    const r1 = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    expect(r1.to - r1.from < (r0.to - r0.from) * 0.5, `zoom box ${JSON.stringify([r0, r1])}`);
    expect(!(await pc(page, () => window.__pc.useChartBus.getState().api.priceScale('right').options().autoScale)), 'price range not zoomed');
    await page.click('[data-tool="zoomout"]');
    await page.waitForTimeout(100);
    const r2 = await pc(page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    expect(Math.abs(r2.from - r0.from) < 1 && Math.abs(r2.to - r0.to) < 1, 'zoom out did not go back');
    // History per symbol.
    await layer(page, (l, p) => l.addHLine(p), await lastClose(page));
    await pc(page, () => window.__pc.usePrefs.getState().setSymbol('ETHUSDT'));
    await page.waitForTimeout(300); await ready(page);
    await page.keyboard.press('Control+z');
    expect(await count(page) === 0 && !(await pc(page, () => window.__pc.useChartBus.getState().canUndo)), 'undo crossed symbols');
    // Vertical line label: Tehran is 3:30 ahead of UTC.
    const label = await layer(page, (l) => l.timeFmt(Date.UTC(2026, 9, 13, 15, 0) / 1000));
    expect(label === "Tue 13 Oct '26  18:30", `vertical line label ${label}`);
    return env;
  });

  await test('drawings: floating bar — name, stats, exact price, fill, text style, order, default, templates, alert, settings; beside the selection', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.usePrefs.getState().setLang('en'));
    const [p0, p1] = await drawTrend(page);
    await page.waitForSelector('.draw-style-bar .dsb-stats');
    expect((await page.textContent('.draw-style-bar .dsb-name')) === 'Trend line', 'tool name not in the UI language');
    const bar = await page.locator('.draw-style-bar').boundingBox();
    const covers = [p0, p1].some(([x, y]) => x >= bar.x && x <= bar.x + bar.width && y >= bar.y && y <= bar.y + bar.height);
    expect(!covers, 'the bar covers the drawing');
    // Alert from the trend line (contract 1).
    await page.evaluate(() => { window.__alert = null; window.addEventListener('pc:alert-dialog', (e) => { window.__alert = e.detail; }); });
    await page.click('.draw-style-bar [data-act="alert"]');
    const al = await page.evaluate(() => window.__alert);
    expect(al && al.line && al.symbol === 'BTCUSDT', `alert ${JSON.stringify(al)}`);
    // The alert dialog (panels area) answers the event; close it to go on with the bar.
    const dlg = await page.waitForSelector('.alert-dlg', { timeout: 3000 }).catch(() => null);
    if (dlg) { await page.click('.alert-dlg [aria-label="close"]'); await page.waitForSelector('.alert-dlg', { state: 'detached', timeout: 3000 }); }
    // Templates: save, apply, delete.
    await page.click('.draw-style-bar .dsb-tpl > button');
    await page.fill('.dsb-form input', 'Mine');
    await page.click('.dsb-form button[type=submit]');
    expect(await pc(page, () => window.__pc.useDrawing.getState().templates.trend?.[0]?.name === 'Mine'), 'template not saved');
    // Visual order and the drag grip.
    await layer(page, (l, p) => { l.addHLine(p); l.selectAt(0); }, await lastClose(page));
    await page.click('.draw-style-bar [data-act="order"]');
    await page.click('.dsb-pop .dfly-row >> nth=0');
    expect((await layer(page, (l) => l.getDrawings()[1].type)) === 'trend', 'bring to front');
    // Exact price for the horizontal line.
    await layer(page, (l) => l.selectAt(l.getDrawings().findIndex((d) => d.type === 'hline')));
    await page.fill('.dsb-price', '12345.5');
    await page.keyboard.press('Enter');
    expect((await layer(page, (l) => l.getDrawings().find((d) => d.type === 'hline').p)) === 12345.5, 'exact price');
    // Save as default: the next horizontal line takes the colour.
    await page.click('.draw-style-bar .dsb-group >> nth=0 >> button');
    await page.click('.dsb-pop .swatch >> nth=2');
    await page.click('.draw-style-bar [data-act="more"]');
    await page.click('.dsb-pop .dfly-row:has-text("Save as default")');
    await pc(page, () => window.__pc.useDrawing.getState().setTool('hline', 'lines'));
    const b = await box(page);
    await page.mouse.click(...at(b, 0.5, 0.7));
    expect((await layer(page, (l) => l.getDrawings().at(-1).color)) === '#089981', 'default not applied');
    // Fill and opacity for a rectangle; bold for a text.
    await layer(page, (l) => { l.addDrawing({ type: 'rect', p0: { t: l.candles.at(-40).t, p: l.candles.at(-40).c }, p1: { t: l.candles.at(-20).t, p: l.candles.at(-20).c * 1.01 }, color: '#2962FF' }); });
    await page.click('.draw-style-bar .dsb-fill > button');
    await page.click('.dsb-pop .swatch >> nth=1');
    expect((await layer(page, (l) => l.getDrawings().at(-1).fill)) === '#F23645', 'fill');
    await layer(page, (l) => { l.addDrawing({ type: 'text', t: l.candles.at(-30).t, p: l.candles.at(-30).c, text: 'hi', color: '#2962FF' }); });
    await page.click('.draw-style-bar .txt-b');
    expect(await layer(page, (l) => l.getDrawings().at(-1).bold === true), 'bold');
    // Two selected, delete removes both.
    await layer(page, (l) => { l.selectAt(0); l.toggleSelect(1); });
    await page.click('.draw-style-bar [data-act="remove"]');
    expect(await count(page) >= 1 && (await layer(page, (l) => l.selectedIndices().length)) === 0, 'delete selection');
    // Settings from the bar; drag the bar.
    await layer(page, (l) => l.selectAt(0));
    await page.click('.draw-style-bar [data-act="settings"]');
    await page.waitForSelector('.dsd');
    await page.click('.dsd [data-act="cancel"]');
    const g = await page.locator('.dsb-grip').boundingBox();
    await page.mouse.move(g.x + 4, g.y + 8); await page.mouse.down(); await page.mouse.move(g.x + 80, g.y + 120, { steps: 5 }); await page.mouse.up();
    expect(await pc(page, () => Boolean(window.__pc.useDrawing.getState().barPos)), 'bar not draggable');
    return env;
  });

  await test('drawings: right-click menu (contract 2) and the settings dialog tabs', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const [p0, p1] = await drawTrend(page);
    await page.keyboard.press('Escape');
    const mid = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
    expect((await layer(page, (l, xy) => { const r = document.querySelector('canvas.draw-layer').getBoundingClientRect(); return l.hitTest(xy[0] - r.left, xy[1] - r.top); }, mid)) === 0, 'hitTest');
    await page.mouse.move(...mid);
    await page.mouse.click(...mid, { button: 'right' });
    await page.waitForSelector('.dctx .dctx-head');
    expect(!(await page.locator('.ctx').count()), 'the chart menu opened over a drawing');
    await page.hover('.dctx [data-act="order"]');
    await page.waitForSelector('.dctx-subm [data-act="front"]');
    await page.click('.dctx [data-act="clone"]');
    expect(await count(page) === 2, 'clone from the menu');
    // The event alone opens it too.
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('pc:drawing-menu', { detail: { clientX: 400, clientY: 300, index: 1 } })));
    await page.waitForSelector('.dctx');
    await page.hover('.dctx [data-act="visibility"]');
    await page.click('.dctx-subm [data-tf="hours"]');
    expect(await layer(page, (l) => l.getDrawings()[1].tfVis.hours === false), 'visibility switch');
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('pc:drawing-menu', { detail: { clientX: 400, clientY: 300, index: 0 } })));
    await page.click('.dctx [data-act="settings"]');
    await page.waitForSelector('.dsd');
    // Style: extend right and middle point; Coordinates: price of point 1; Visibility: minutes off.
    await page.click('.dsd-row:has-text("امتداد به راست") input');
    await page.click('.dsd-tab[data-tab="coords"]');
    await page.fill('.dsd-coord >> nth=0 >> input[aria-label="قیمت"]', '70000');
    await page.click('.dsd-tab[data-tab="visibility"]');
    await page.click('.dsd-vis[data-tf="minutes"] input[type=checkbox]');
    await page.fill('.dsd-vis[data-tf="hours"] input >> nth=2', '4');
    const live = await layer(page, (l) => l.getDrawings()[0]);
    expect(live.extendRight && live.p0.p === 70000 && live.tfVis.minutes.on === false && live.tfVis.hours.max === 4, `live edit ${JSON.stringify(live)}`);
    await page.click('.dsd [data-act="cancel"]');
    expect(!(await layer(page, (l) => l.getDrawings()[0].extendRight)), 'cancel did not restore');
    // OK keeps, as one undo step.
    await page.evaluate(() => window.__pc.useDrawing.getState().openSettings(0));
    await page.click('.dsd-row:has-text("نقطه‌ی میانی") input');
    await page.click('.dsd [data-act="ok"]');
    expect(await layer(page, (l) => l.getDrawings()[0].middle === true), 'ok');
    await page.keyboard.press('Control+z');
    expect(!(await layer(page, (l) => l.getDrawings()[0].middle)), 'one undo step');
    // Fibonacci levels; visibility hides on this interval (1h is hours 1, out of a 2–24 range).
    await layer(page, (l) => { l.addDrawing({ type: 'fib', p0: { t: l.candles.at(-60).t, p: l.candles.at(-60).h }, p1: { t: l.candles.at(-10).t, p: l.candles.at(-10).l }, color: '#2962FF' }); });
    const fi = await count(page) - 1;
    await page.evaluate((i) => window.__pc.useDrawing.getState().openSettings(i), fi);
    await page.click('.dsd-lv-row >> nth=1 >> input[type=checkbox]');
    await page.click('.dsd-tab[data-tab="visibility"]');
    await page.fill('.dsd-vis[data-tf="hours"] input >> nth=1', '2');
    await page.click('.dsd [data-act="ok"]');
    const f = await layer(page, (l, i) => ({ d: l.getDrawings()[i], hidden: l._tfHidden(l.getDrawings()[i]) }), fi);
    expect(f.d.levels[1].on === false && f.hidden, `fib ${JSON.stringify(f.d.levels?.[1])} hidden=${f.hidden}`);
    return env;
  });

  await test('drawings: long position — target handle, inputs (account, risk, quantity)', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await layer(page, (l) => { const c = l.candles; l.addDrawing({ type: 'longshort', p0: { t: c.at(-40).t, p: c.at(-40).c }, p1: { t: c.at(-20).t, p: c.at(-40).c * 0.98 }, color: '#2962FF' }); });
    const hs = await layer(page, (l) => l._handlePoints(l.getDrawings()[0]).map((h) => [h.x, h.y]));
    expect(hs.length === 4, 'four handles');
    const b = await box(page);
    const [tx, ty] = hs[2];
    await page.mouse.move(b.x + tx, b.y + ty); await page.mouse.down(); await page.mouse.move(b.x + tx, b.y + ty - 40, { steps: 4 }); await page.mouse.up();
    const d = await layer(page, (l) => l.getDrawings()[0]);
    expect(typeof d.tp === 'number' && d.tp > d.p0.p * 1.04, `target ${d.tp}`);
    await page.evaluate(() => window.__pc.useDrawing.getState().openSettings(0));
    await page.click('.dsd-tab[data-tab="inputs"]');
    await page.fill('.dsd-row:has-text("اندازه‌ی حساب") input', '5000');
    const st = await layer(page, (l) => l.positionStats(l.getDrawings()[0]));
    expect(st.account === 5000 && Math.abs(st.riskAmt - 50) < 1e-6 && st.qty > 0, `inputs ${JSON.stringify(st)}`);
    await page.click('.dsd [data-act="ok"]');
    return env;
  });

  await test('drawings: object tree — select, header, rename, order by drag, groups, indicator gear', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const price = await lastClose(page);
    await layer(page, (l, p) => { l.addHLine(p); l.addHLine(p * 1.01); l.addDrawing({ type: 'text', t: l.candles.at(-5).t, p, text: 'hello', color: '#F23645' }); l.selectAt(-1); }, price);
    await pc(page, () => { if (window.__pc.usePanels.getState().open !== 'objects') window.__pc.usePanels.getState().toggle('objects'); });
    await page.waitForSelector('.objects .obj-row[data-index]');
    expect((await page.locator('.objects .obj-row[data-index]').count()) === 3, 'rows');
    expect((await page.textContent('.objects .obj-row[data-index="2"]')).includes('hello'), 'text label');
    await page.click('.objects .obj-row[data-index="0"] .obj-name');
    await page.waitForSelector('.draw-style-bar');
    // Rename.
    await page.dblclick('.objects .obj-row[data-index="0"] .obj-name');
    await page.fill('.objects .obj-rename', 'Support');
    await page.keyboard.press('Enter');
    expect(await layer(page, (l) => l.getDrawings()[0].name === 'Support'), 'rename');
    // Drag the bottom drawing onto the top one.
    await page.locator('.objects .obj-row[data-index="0"]').dragTo(page.locator('.objects .obj-row[data-index="2"]'));
    expect((await layer(page, (l) => l.getDrawings()[2].name)) === 'Support', 'drag reorder');
    // A group of two, hidden together.
    await layer(page, (l) => { l.selectAt(0); l.toggleSelect(1); });
    await page.click('.objects [aria-label="گروه تازه"]');
    await page.waitForSelector('.objects .obj-row.grp');
    await page.click('.objects .obj-row.grp .obj-tools .lt >> nth=1');
    expect(await layer(page, (l) => l.getDrawings().filter((d) => d.group).every((d) => d.visible === false)), 'group hide');
    // Header: hide all and remove all.
    await page.click('.objects .otree-tools [aria-label="پنهان‌کردن همه"]');
    expect(await layer(page, (l) => l.getDrawings().every((d) => d.visible === false)), 'hide all');
    // An indicator's gear opens its settings.
    await pc(page, () => window.__pc.useStudies.getState().add('rsi'));
    await page.click('.objects [data-act="study-settings"]');
    await page.waitForSelector('.dialog.settings[role=dialog]');
    await page.click('.dialog.settings[role=dialog] .dialog-head .ib[aria-label="بستن"]');
    await page.click('.objects .otree-tools [aria-label="حذف همه"]');
    expect(await count(page) === 0, 'remove all');
    return env;
  });

  await test('drawings: remove and hide menus count drawings and indicators; the rail hides and comes back', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useStudies.getState().add('rsi'));
    await layer(page, (l, p) => { l.addHLine(p); l.addHLine(p * 1.01); }, await lastClose(page));
    await layer(page, (l) => l.toggleLock(0));
    await page.click('[data-group="hide"]');
    await page.click('.dfly[data-menu="hide"] .dfly-row >> nth=1');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.every((s) => s.hidden)), 'hide indicators');
    await page.click('[data-group="hide"]');
    await page.click('.dfly[data-menu="hide"] .dfly-row >> nth=1');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.every((s) => !s.hidden)), 'show indicators again');
    await page.click('[data-group="remove"]');
    expect((await page.textContent('.dfly[data-menu="remove"] .dfly-row >> nth=0')).includes('۱'), 'one unlocked drawing counted (Persian digits)');
    await page.click('.dfly[data-menu="remove"] .dfly-row >> nth=0');
    expect(await count(page) === 1, 'locked drawing kept');
    await page.click('[data-group="remove"]');
    await page.click('.dfly[data-menu="remove"] .dfly-row >> nth=1');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.every((s) => s.key === '__volume')), 'remove indicators');
    // Default colour.
    await page.click('[data-tool="color"]');
    await page.click('.dfly[data-menu="color"] .swatch >> nth=1');
    await pc(page, () => window.__pc.useDrawing.getState().setTool('hline', 'lines'));
    await page.mouse.click(...at(await box(page), 0.5, 0.3));
    expect((await layer(page, (l) => l.getDrawings().at(-1).color)) === '#F23645', 'default colour');
    // Hide the rail (contract 8) and bring it back.
    await page.click('[data-tool="hiderail"]');
    expect(await page.evaluate(() => document.body.classList.contains('rail-hidden')), 'rail hidden');
    expect(await pc(page, () => window.__pc.useDrawing.getState().railHidden), 'railHidden');
    await page.click('.rail-show');
    expect(!(await page.evaluate(() => document.body.classList.contains('rail-hidden'))), 'rail back');
    return env;
  });

  await test('drawings: touch draws (pointer events), touch-action none while drawing; phone rail has undo and redo', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useDrawing.getState().setTool('trend', 'lines'));
    expect(await page.evaluate(() => document.querySelector('canvas.draw-layer').style.touchAction) === 'none', 'touch-action');
    await page.evaluate(() => {
      const cv = document.querySelector('canvas.draw-layer'); const r = cv.getBoundingClientRect();
      const ev = (type, x, y, target) => target.dispatchEvent(new PointerEvent(type, { pointerType: 'touch', isPrimary: true, button: 0, buttons: 1, clientX: r.left + x, clientY: r.top + y, bubbles: true }));
      ev('pointerdown', 80, 300, cv); ev('pointermove', 150, 250, window); ev('pointermove', 220, 200, window); ev('pointerup', 220, 200, window);
    });
    expect(await count(page) === 1, 'a finger did not draw');
    await page.evaluate(() => document.body.classList.add('rail-open'));
    await page.waitForTimeout(300);
    const undo = page.locator('.ws-rail .phone-only[aria-label="واگرد"]');
    expect(await undo.isVisible(), 'undo on the phone rail');
    await undo.click();
    expect(await count(page) === 0, 'phone undo');
    await page.click('.ws-rail .phone-only[aria-label="ازنو"]');
    expect(await count(page) === 1, 'phone redo');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(!overflow, 'horizontal overflow');
    return env;
  });

  await test('drawings: axis labels for the selected drawing\'s points; new tools draw with their data', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await drawTrend(page);
    const probe = () => page.evaluate(() => {
      const l = window.__pc.useChartBus.getState().drawingLayer, api = window.__pc.useChartBus.getState().api;
      const d = l.getDrawings()[0]; const cv = document.querySelector('canvas.draw-layer');
      const dpr = window.devicePixelRatio || 1, W = cv.width / dpr, pw = api.priceScale('right').width();
      const y = l._y(d.p0.p); const px = cv.getContext('2d').getImageData(Math.round((W - pw + 3) * dpr), Math.round(y * dpr), 1, 1).data;
      return [px[0], px[1], px[2], px[3]];
    });
    const sel = await probe();
    expect(sel[3] > 200 && sel[2] > 200 && sel[0] < 90, `no price label on the axis: ${sel}`);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(60);
    const off = await probe();
    expect(off[3] < 50, `axis label stayed after deselecting: ${off}`);
    // VWAP and volume profile compute from the bars.
    await layer(page, (l) => { const c = l.candles; l.addDrawing({ type: 'avwap', p0: { t: c.at(-80).t, p: c.at(-80).c }, p1: { t: c.at(-80).t, p: c.at(-80).c } }); l.addDrawing({ type: 'fixedvp', p0: { t: c.at(-120).t, p: c.at(-120).c }, p1: { t: c.at(-20).t, p: c.at(-20).c } }); l.selectAt(-1); });
    const b = await box(page);
    const vw = await layer(page, (l) => { const c = l.candles; let sv = 0, spv = 0; for (const x of c.slice(-80)) { const v = x.v || 1; sv += v; spv += (x.h + x.l + x.c) / 3 * v; } return { t: c.at(-1).t, v: spv / sv }; });
    const hitVw = await layer(page, (l, q) => l.hitTest(l._x(q.t), l._y(q.v)), vw);
    expect(hitVw === 1 || hitVw === 2, `VWAP line not where the VWAP is (${hitVw})`);
    void b;
    return env;
  });
}
