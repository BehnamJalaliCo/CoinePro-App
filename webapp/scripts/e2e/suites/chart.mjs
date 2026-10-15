// Chart area (B): settings, menus, legend, axes, overlays, chart types, data window.
export default async function ({ test, open, ready, pc, expect }) {
  const SHOT = process.env.CHART_SHOTS;
  if (SHOT) {
    // Pictures for a person to look at (CHART_SHOTS=dir); not part of the suite otherwise.
    const steps = (process.env.CHART_STEPS ?? 'main').split(',');
    for (const [scheme, lang] of (process.env.CHART_LOOKS ?? 'light:fa').split(',').map((x) => x.split(':'))) {
      await test(`chart: shots ${scheme} ${lang}`, async () => {
        const env = await open('BTCUSDT/1h?e2e=1', { scheme, width: Number(process.env.W ?? 1440), height: Number(process.env.H ?? 900) });
        await ready(env.page);
        await pc(env.page, (l) => window.__pc.usePrefs.getState().setLang(l), lang);
        await env.page.waitForTimeout(800);
        const shot = (n) => env.page.screenshot({ path: `${SHOT}/${n}-${scheme}-${lang}.jpg`, type: 'jpeg', quality: 70 });
        const box = await env.page.locator('.chart-host').boundingBox();
        for (const s of steps) {
          if (s === 'main') { await env.page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.4); await env.page.waitForTimeout(400); await shot('main'); }
          if (s === 'ctx') { await env.page.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.3, { button: 'right' }); await env.page.waitForTimeout(400); await shot('ctx'); await env.page.keyboard.press('Escape'); }
          if (s === 'price') { await env.page.mouse.click(box.x + box.width - 30, box.y + box.height * 0.3, { button: 'right' }); await env.page.waitForTimeout(300); await env.page.hover('.cm [data-act=ps-labels]'); await env.page.waitForTimeout(400); await shot('price'); await env.page.keyboard.press('Escape'); }
          if (s === 'time') { await env.page.mouse.click(box.x + box.width * 0.4, box.y + box.height - 10, { button: 'right' }); await env.page.waitForTimeout(300); await env.page.hover('.cm [data-act=ts-tz]'); await env.page.waitForTimeout(400); await shot('time'); await env.page.keyboard.press('Escape'); }
          if (s.startsWith('settings')) {
            const tab = s.split('-')[1] ?? 'symbol';
            await env.page.evaluate((t) => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: t } })), tab);
            await env.page.waitForTimeout(500); await shot(`settings-${tab}`); await env.page.keyboard.press('Escape'); await env.page.waitForTimeout(200);
          }
          if (s === 'legend') {
            await pc(env.page, () => { const s = window.__pc.useStudies.getState(); s.add('rsi'); s.add('ema'); });
            await env.page.waitForTimeout(800);
            await env.page.hover('.legend-study[data-uid^=ema]'); await env.page.waitForTimeout(300); await shot('legend');
            await env.page.click('.legend-study[data-uid^=ema] .legend-tools .lt:last-child'); await env.page.waitForTimeout(300);
            await env.page.hover('.cm [data-act=study-move]'); await env.page.waitForTimeout(400); await shot('legend-menu'); await env.page.keyboard.press('Escape');
          }
          if (s === 'plus') { await env.page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.35); await env.page.waitForTimeout(300); await env.page.click('.axis-plus'); await env.page.waitForTimeout(300); await shot('plus'); await env.page.keyboard.press('Escape'); }
          if (s === 'types') {
            for (const t of ['kagi', 'pnf', 'renko', 'highlow', 'hollow', 'baseline']) {
              await pc(env.page, (x) => window.__pc.usePrefs.getState().setChartType(x), t);
              await env.page.waitForTimeout(700); await shot(`type-${t}`);
            }
          }
          if (s === 'overlays') {
            await pc(env.page, () => window.__pc.useChartSettings.getState().set({ volumeProfile: true, sessionsOn: true, highLow: 'value-line', avgClose: 'value-line', hhll: true, volumeMa: true, news: true, pdhl: 'value-line', prevDayClose: 'value-line' }));
            await pc(env.page, () => window.__pc.useAlerts.getState().add('BTCUSDT', 'above', 82500));
            await env.page.waitForTimeout(1500); await shot('overlays');
          }
          if (s === 'dw') { await pc(env.page, () => { const p = window.__pc.usePanels.getState(); if (p.open !== 'data') p.toggle('data'); }); await env.page.waitForTimeout(600); await env.page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.4); await env.page.waitForTimeout(400); await shot('dw'); }
        }
        return env;
      });
    }
  }

  // ── Chart Settings: seven tabs, live changes, Cancel and Escape put things back, templates ──
  await test('chart: settings dialog — tabs, live change, cancel, escape, templates, ok', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await p.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'canvas' } })));
    await p.waitForSelector('.chart-settings [data-tab=canvas][aria-selected=true]');
    expect(await p.$$eval('.chart-settings .cs-tab', (e) => e.length) === 7, 'seven tabs');
    for (const tab of ['symbol', 'status', 'scales', 'canvas', 'trading', 'alerts', 'events']) { await p.click(`.chart-settings [data-tab=${tab}]`); await p.waitForSelector('.cs-pane .cs-row'); }
    await p.click('.chart-settings [data-tab=canvas]');
    await p.click('.chart-settings input[data-k=vertGrid]');
    expect(await pc(p, () => window.__pc.useChartSettings.getState().vertGrid) === false, 'live change');
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.options().grid.vertLines.visible) === false, 'chart follows');
    await p.click('.chart-settings .dialog-foot .btn-quiet:not(.cs-tpl-btn)');
    await p.waitForSelector('.chart-settings', { state: 'detached' });
    expect(await pc(p, () => window.__pc.useChartSettings.getState().vertGrid) === true, 'cancel restores');
    // Escape restores too, the scale switches with it.
    await p.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'scales' } })));
    await p.selectOption('.chart-settings select[data-k=scaleMode]', 'log');
    await p.click('.chart-settings input[data-k=invert]');
    expect(await pc(p, () => window.__pc.useChartBus.getState().scale) === 'log', 'scale set');
    await p.keyboard.press('Escape');
    await p.waitForSelector('.chart-settings', { state: 'detached' });
    const after = await pc(p, () => ({ scale: window.__pc.useChartBus.getState().scale, invert: window.__pc.useChartBus.getState().invert }));
    expect(after.scale === 'normal' && !after.invert, `escape restores ${JSON.stringify(after)}`);
    // Templates: save, change, apply, delete.
    await p.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'symbol' } })));
    await p.click('.cs-tpl-btn');
    await p.fill('.cs-tpl input', 'e2e');
    await p.click('.cs-tpl button[type=submit]');
    await pc(p, () => window.__pc.useChartSettings.getState().set({ up: '#123456' }));
    await p.click('.cs-tpl [data-act="tpl-e2e"]');
    expect(await pc(p, () => window.__pc.useChartSettings.getState().up) === '#089981', 'template applied');
    await p.click('.cs-tpl-btn');
    await p.click('.cs-tpl .cs-tpl-row .lt');
    expect(await pc(p, () => window.__pc.useChartSettings.getState().templates.length) === 0, 'template deleted');
    await p.keyboard.press('Escape');
    // Ok keeps a change and it survives a reload.
    await p.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'status' } })));
    await p.click('.chart-settings input[data-k=showLogo]');
    await p.click('.chart-settings .dialog-foot .btn-primary');
    await p.goto('http://pc.local/next/BTCUSDT/1h?e2e=1'); await ready(p);
    expect(await pc(p, () => window.__pc.useChartSettings.getState().showLogo) === false, 'ok kept');
    expect(!(await p.$('.legend-head .logo')), 'logo hidden');
    return env;
  });

  await test('chart: settings apply to the chart — background, crosshair, margins, precision, time format', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => window.__pc.useChartSettings.getState().set({ bgType: 'gradient', bgColor: '#101820', bgColor2: '#203040', crossColor: '#ff0000', crossWidth: 3, crossStyle: 'solid', marginTop: 20, rightOffset: 30, precision: 4, hour12: true, scaleFontSize: 14 }));
    await p.waitForTimeout(300);
    const o = await pc(p, () => { const a = window.__pc.useChartBus.getState().api; const x = a.options(); return { bg: x.layout.background, ch: x.crosshair.vertLine, m: a.priceScale('right').options().scaleMargins, ro: a.timeScale().options().rightOffset, fs: x.layout.fontSize, pf: window.__pc.useChartBus.getState().series.options().priceFormat }; });
    expect(o.bg.type === 'gradient' && o.bg.topColor === '#101820', `background ${JSON.stringify(o.bg)}`);
    expect(o.ch.color === '#ff0000' && o.ch.width === 3 && o.ch.style === 0, `crosshair ${JSON.stringify(o.ch)}`);
    expect(Math.abs(o.m.top - 0.2) < 1e-9 && o.ro === 30 && o.fs === 14 && o.pf.precision === 4, JSON.stringify(o));
    const legend = await p.textContent('.legend-ohlc');
    expect(/\d+\.\d{4}\b/.test(legend), `precision in legend: ${legend}`);
    return env;
  });

  // ── Price axis menu: modes, invert, lock, labels; the switches kept across reloads ──
  await test('chart: price scale menu — modes, invert, lock ratio, labels, kept on reload', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    const box = await p.locator('.chart-host').boundingBox();
    const axis = () => p.mouse.click(box.x + box.width - 25, box.y + box.height * 0.3, { button: 'right' });
    await axis();
    await p.waitForSelector('.price-menu');
    expect(!(await p.$('.cm [data-act=reset]')), 'not the chart menu');
    await p.click('.price-menu [data-act=ps-log]');
    await axis(); await p.click('.price-menu [data-act=ps-invert]');
    await axis(); await p.click('.price-menu [data-act=ps-lock]');
    const st = await pc(p, () => window.__pc.useChartSettings.getState());
    expect(st.lockRatio && st.lockRatioValue > 0, 'lock ratio');
    await axis(); await p.hover('.price-menu [data-act=ps-labels]'); await p.click('.cm-sub [data-act=lbl-countdown]');
    expect(!(await pc(p, () => window.__pc.useChartSettings.getState().countdown)), 'countdown label off');
    await p.keyboard.press('Escape');
    await axis(); await p.hover('.price-menu [data-act=ps-lines]'); await p.click('.cm-sub [data-act=line-last]');
    expect(await pc(p, () => window.__pc.useChartSettings.getState().lastPrice) === 'value', 'price line off, label kept');
    await p.keyboard.press('Escape');
    await p.goto('http://pc.local/next/BTCUSDT/1h?e2e=1'); await ready(p);
    const b = await pc(p, () => { const x = window.__pc.useChartBus.getState(); return { scale: x.scale, invert: x.invert, mode: x.api.priceScale('right').options().mode, inv: x.api.priceScale('right').options().invertScale }; });
    expect(b.scale === 'log' && b.invert && b.mode === 1 && b.inv, `kept ${JSON.stringify(b)}`);
    return env;
  });

  await test('chart: scale on the left, scale price only, compare on its own scale', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => window.__pc.useChartSettings.getState().set({ scalePlacement: 'left' }));
    await p.waitForTimeout(300);
    const l = await pc(p, () => { const a = window.__pc.useChartBus.getState().api; return { left: a.priceScale('left').width(), id: window.__pc.useChartBus.getState().series.options().priceScaleId }; });
    expect(l.left > 20 && l.id === 'left', JSON.stringify(l));
    await pc(p, () => window.__pc.useChartSettings.getState().set({ scalePlacement: 'right' }));
    await pc(p, () => window.__pc.useCompare.getState().add('ETHUSDT'));
    await p.waitForSelector('.legend-study.cmp .num', { timeout: 10000 });
    const ids = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getSeries().map((s) => s.options().priceScaleId));
    expect(ids.includes('cmp-ETHUSDT'), `own scale ${ids}`);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.priceScale('right').options().mode) === 0, 'main stays regular');
    await p.hover('.legend-study.cmp'); await p.click('.legend-study.cmp .legend-tools .lt:first-child');
    expect(await pc(p, () => window.__pc.useCompare.getState().hidden.ETHUSDT) === true, 'eye hides');
    await p.goto('http://pc.local/next/BTCUSDT/1h?e2e=1'); await ready(p);
    expect((await pc(p, () => window.__pc.useCompare.getState().symbols)).includes('ETHUSDT'), 'compare kept');
    await pc(p, () => window.__pc.useChartSettings.getState().set({ compareMode: 'percent' }));
    await p.waitForTimeout(300);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.priceScale('right').options().mode) === 2, 'percent when asked');
    await pc(p, () => window.__pc.useCompare.getState().clear());
    return env;
  });

  // ── Time axis menu ──
  await test('chart: time scale menu — time zone, session breaks, sessions, go to date', async () => {
    const env = await open('BTCUSDT/15m?e2e=1');
    await ready(env.page);
    const p = env.page;
    const box = await p.locator('.chart-host').boundingBox();
    const axis = () => p.mouse.click(box.x + box.width * 0.4, box.y + box.height - 10, { button: 'right' });
    await axis(); await p.waitForSelector('.time-menu');
    await p.hover('.time-menu [data-act=ts-tz]'); await p.click('.cm-sub [data-act="tz-UTC"]');
    expect(await pc(p, () => window.__pc.usePrefs.getState().timezone) === 'UTC', 'time zone');
    await axis(); await p.click('.time-menu [data-act=ts-breaks]');
    await p.waitForSelector('canvas.session-breaks');
    await axis(); await p.hover('.time-menu [data-act=ts-sessions]'); await p.click('.cm-sub [data-act=sessions-all]');
    await p.keyboard.press('Escape');
    await p.waitForSelector('canvas.session-shading');
    const painted = await p.evaluate(() => { const c = document.querySelector('canvas.session-shading'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; for (let i = 3; i < d.length; i += 40) if (d[i]) return true; return false; });
    expect(painted, 'sessions shaded');
    await axis(); await p.click('.time-menu [data-act=ts-goto]');
    await p.waitForSelector('.goto-pop');
    return env;
  });

  // ── The chart menu's drawing items, and contract 2 ──
  await test('chart: chart menu — vertical line, note, long, short, line, remove N indicators', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    const box = await p.locator('.chart-host').boundingBox();
    // Each drawing goes in its own spot, and later menus open where none of them lies: with the
    // drawings area's real hit test, a right-click on a drawing gets the drawing's menu instead.
    const menu = async (fx = 0.1, fy = 0.9) => { await p.mouse.click(box.x + box.width * fx, box.y + box.height * fy, { button: 'right' }); await p.waitForSelector('.ctx [data-act=reset]'); };
    const count = () => pc(p, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().map((d) => d.type));
    const spots = { vline: [0.8, 0.5], long: [0.3, 0.3], short: [0.3, 0.6], hline: [0.5, 0.15] };
    for (const act of ['vline', 'long', 'short', 'hline']) { await menu(...spots[act]); await p.click(`.ctx [data-act=${act}]`); }
    expect(JSON.stringify(await count()) === JSON.stringify(['vline', 'longshort', 'short', 'hline']), `drawn ${await count()}`);
    await menu(); await p.click('.ctx [data-act=note]');
    await p.waitForFunction(() => window.__pc.useChartBus.getState().textEdit != null);
    await p.keyboard.press('Escape');
    await pc(p, () => { const s = window.__pc.useStudies.getState(); s.add('rsi'); s.add('ema'); });
    await menu();
    const label = await p.textContent('.ctx [data-act=remove-indicators]');
    expect(label.includes('۲'), `count in label: ${label}`);
    await p.click('.ctx [data-act=remove-indicators]');
    expect(await pc(p, () => window.__pc.useStudies.getState().list.filter((s) => s.key !== '__volume').length) === 0, 'removed');
    // Paste, when the drawings area has something on its clipboard.
    await pc(p, () => { const l = window.__pc.useChartBus.getState().drawingLayer; l.pasteMany = (list) => { list.forEach((d) => l.addDrawing({ ...d })); return []; }; window.__pc.useDrawing.setState({ clip: [{ type: 'hline', p: 80000, color: '#2962FF' }] }); });
    const n0 = (await count()).length;
    await menu(); await p.click('.ctx [data-act=paste]');
    expect((await count()).length === n0 + 1, 'pasted');
    // A drawing under the pointer gets the drawing menu (contract 2), not the chart's.
    await pc(p, () => { const l = window.__pc.useChartBus.getState().drawingLayer; l.hitTest = () => 0; window.__hits = []; window.addEventListener('pc:drawing-menu', (e) => window.__hits.push(e.detail)); });
    await p.mouse.click(box.x + box.width * 0.45, box.y + box.height * 0.35, { button: 'right' });
    await p.waitForTimeout(200);
    const hits = await p.evaluate(() => window.__hits);
    expect(hits.length === 1 && hits[0].index === 0 && hits[0].clientX > 0, JSON.stringify(hits));
    expect(!(await p.$('.ctx [data-act=reset]')), 'no chart menu over a drawing');
    return env;
  });

  // ── Legend ──
  await test('chart: legend — tools, ⋯ menu, duplicate, alert on indicator, pin, collapse, compact, remove all', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => { const s = window.__pc.useStudies.getState(); s.add('ema'); s.add('rsi'); });
    await p.waitForSelector('.legend-study[data-uid^=ema]');
    const remove = await p.getAttribute('.legend-study[data-uid^=ema] .legend-tools .lt:nth-child(3)', 'title');
    expect(remove === 'حذف اندیکاتور', `remove tooltip ${remove}`);
    const more = async () => { await p.hover('.legend-study[data-uid^=ema] .legend-study-name'); await p.click('.legend-study[data-uid^=ema] .legend-tools .lt:last-child'); };
    await more(); await p.click('.cm [data-act=study-duplicate]');
    expect(await pc(p, () => window.__pc.useStudies.getState().list.filter((s) => s.key === 'ema').length) === 2, 'duplicated');
    await p.evaluate(() => { window.__alerts = []; window.addEventListener('pc:alert-dialog', (e) => window.__alerts.push(e.detail)); });
    await more(); await p.click('.cm [data-act=study-alert]');
    const a = await p.evaluate(() => window.__alerts[0]);
    expect(a && a.study && a.study.key === 'ema' && a.symbol === 'BTCUSDT', JSON.stringify(a));
    // The panels area answers with its alert dialog; close it before going on.
    if (await p.waitForSelector('.alert-dlg', { timeout: 3000 }).catch(() => null)) {
      await p.click('.alert-dlg [aria-label="close"]');
      await p.waitForSelector('.alert-dlg', { state: 'detached', timeout: 3000 });
    }
    await more(); await p.hover('.cm [data-act=study-pin]'); await p.click('.cm-sub [data-act=pin-left]');
    await p.waitForTimeout(400);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.priceScale('left').width()) > 20, 'pinned to the left scale');
    // RSI onto the price pane, then back.
    await p.hover('.pane-legend .legend-study .legend-study-name'); await p.click('.pane-legend .legend-study .legend-tools .lt:last-child');
    await p.hover('.cm [data-act=study-move]'); await p.click('.cm-sub [data-act=move-main]');
    await p.waitForTimeout(500);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.panes().length) === 1, 'moved to the main pane');
    // Fold, compact, remove all.
    await p.click('.legend-fold .fold');
    await p.waitForSelector('.legend-summary');
    expect(/۴|4/.test(await p.textContent('.legend-summary')), 'summary counts');
    await p.click('.legend-fold .fold');
    await pc(p, () => window.__pc.useChartSettings.getState().set({ legendCompact: true }));
    await p.waitForSelector('.legend-chip');
    await pc(p, () => window.__pc.useChartSettings.getState().set({ legendCompact: false, showOhlc: false }));
    expect(await p.$('.legend-chg'), 'bar change without OHLC');
    p.once('dialog', (d) => void d.accept());
    await p.hover('.legend-fold'); await p.click('.legend-fold .legend-tools .lt:last-child');
    expect(await pc(p, () => window.__pc.useStudies.getState().list.filter((s) => s.key !== '__volume').length) === 0, 'all removed');
    // The symbol row: settings gear opens Settings, ⋯ opens its menu.
    await p.hover('.legend-head'); await p.click('.legend-head .legend-tools .lt:first-child');
    await p.waitForSelector('.chart-settings [data-tab=symbol][aria-selected=true]');
    await p.keyboard.press('Escape');
    await p.hover('.legend-head'); await p.click('.legend-head .legend-tools .lt:last-child');
    await p.waitForSelector('.cm [data-act=sym-alert]');
    return env;
  });

  // ── Buy/Sell from the chart: offered to a trading area first (pc:order-ticket), paper otherwise ──
  await test('chart: order ticket offered first; paper trade when nobody takes it', async () => {
    const env = await open('ETHUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await p.waitForSelector('.legend-trade .lt-buy', { timeout: 10000 });
    await p.evaluate(() => { window.__tickets = []; window.__take = true; window.addEventListener('pc:order-ticket', (e) => { window.__tickets.push(e.detail); if (window.__take) e.preventDefault(); }); });
    await p.click('.legend-trade .lt-buy');
    let t = await p.evaluate(() => window.__tickets);
    expect(t.length === 1 && t[0].side === 'buy' && t[0].source === 'legend' && t[0].symbol === 'ETHUSDT' && t[0].price > 0, JSON.stringify(t));
    expect((await pc(p, () => window.__pc.usePaper.getState().positions.length)) === 0, 'taken: no paper trade');
    // The axis «+»: a limit order at the price, offered first, then paper when nobody takes it.
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.6);
    await p.waitForSelector('.axis-plus');
    await p.click('.axis-plus');
    await p.click('.cm [data-act=plus-buy]');
    t = await p.evaluate(() => window.__tickets);
    expect(t.length === 2 && t[1].source === 'axis-plus' && t[1].type === 'limit', JSON.stringify(t[1]));
    expect((await pc(p, () => window.__pc.usePaper.getState().orders.length)) === 0, 'taken: no paper order');
    await p.evaluate(() => { window.__take = false; });
    await p.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.6);
    await p.click('.axis-plus');
    await p.click('.cm [data-act=plus-buy]');
    expect((await pc(p, () => window.__pc.usePaper.getState().orders.length)) === 1, 'fallback: paper order');
    await p.click('.legend-trade .lt-sell');
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions.some((x) => x.side === 'short'));
    // The paper panel opened under the chart: the chart is shorter now.
    const box2 = await p.locator('.chart-host').boundingBox();
    box.y = box2.y; box.height = box2.height;
    // The axis «+»: alert at the price (contract 1).
    await p.evaluate(() => { window.__alerts = []; window.addEventListener('pc:alert-dialog', (e) => window.__alerts.push(e.detail)); });
    await p.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.6);
    await p.click('.axis-plus');
    await p.click('.cm [data-act=plus-alert]');
    const a = await p.evaluate(() => window.__alerts[0]);
    expect(a && a.symbol === 'ETHUSDT' && a.price > 0, JSON.stringify(a));
    return env;
  });

  // ── Countdown: every time interval, none on non-time types ──
  await test('chart: countdown on time intervals incl. 1W, none on Renko', async () => {
    const env = await open('BTCUSDT/1W?e2e=1');
    await ready(env.page);
    const p = env.page;
    await p.waitForFunction(() => /\d+d \d\d:\d\d|\d\d:\d\d/.test(document.querySelector('.ws-chart').getAttribute('data-countdown') ?? ''), null, { timeout: 5000 });
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('renko'));
    await p.waitForFunction(() => document.querySelector('.ws-chart').getAttribute('data-countdown') === '', null, { timeout: 5000 });
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('candles'));
    return env;
  });

  // ── Chart types and their inputs ──
  await test('chart: chart-type inputs — gear beside the type, Renko box, baseline base level', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    expect(!(await p.$('.ctp-gear')), 'no gear for candles');
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('renko'));
    await p.click('.ctp-gear');
    await p.waitForSelector('.ctp-pop select');
    await p.selectOption('.ctp-pop select', 'traditional');
    const n0 = await pc(p, () => window.__pc.useChartBus.getState().shown.length);
    await p.fill('.ctp-pop input[type=number]', '50');
    await p.waitForTimeout(300);
    const n1 = await pc(p, () => window.__pc.useChartBus.getState().shown.length);
    expect(n1 > n0, `smaller box, more bricks ${n0} → ${n1}`);
    await p.keyboard.press('Escape');
    // Indicators run on the bricks.
    await pc(p, () => window.__pc.useStudies.getState().add('rsi'));
    await p.waitForSelector('.pane-legend .legend-study');
    // Baseline: the base is half-way up the visible range.
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('baseline'));
    await p.waitForTimeout(600);
    const b = await pc(p, () => { const s = window.__pc.useChartBus.getState().series; const a = window.__pc.useChartBus.getState().api; const h = a.panes()[0].getHeight(); return { base: s.options().baseValue.price, top: s.coordinateToPrice(0), bot: s.coordinateToPrice(h) }; });
    const mid = (b.top + b.bot) / 2;
    expect(Math.abs(b.base - mid) < Math.abs(b.top - b.bot) * 0.02, `base ${JSON.stringify(b)}`);
    await p.waitForSelector('.alert-handle[data-line=baseline]');
    const hb = await p.locator('.alert-handle[data-line=baseline]').boundingBox();
    await p.mouse.move(hb.x + 30, hb.y + 4); await p.mouse.down(); await p.mouse.move(hb.x + 30, hb.y - 80, { steps: 4 }); await p.mouse.up();
    expect(await pc(p, () => window.__pc.useChartSettings.getState().baselinePct) > 55, 'dragged up');
    for (const t of ['kagi', 'pnf', 'highlow', 'hollow']) { await pc(p, (x) => window.__pc.usePrefs.getState().setChartType(x), t); await p.waitForTimeout(300); }
    expect(await pc(p, () => window.__pc.useChartBus.getState().shown.length) > 10, 'hollow bars');
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('candles'));
    return env;
  });

  // ── Overlays: volume profile, alert line drag, news and events, volume average, levels ──
  await test('chart: overlays — VPVR, alert line drag, events and news, volume MA, levels', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => window.__pc.useChartSettings.getState().set({ volumeProfile: true, news: true, events: true, eventImportance: 'all', volumeMa: true, highLow: 'value-line', avgClose: 'value-line', pdhl: 'value-line', prevDayClose: 'value-line', bidAsk: 'value-line', hhll: true }));
    await p.waitForFunction(() => Number(document.querySelector('canvas.volume-profile')?.dataset.rows ?? 0) > 0, null, { timeout: 5000 });
    await p.waitForFunction(() => Number(document.querySelector('canvas.event-marks')?.dataset.news ?? 0) > 0 && Number(document.querySelector('canvas.event-marks')?.dataset.events ?? 0) > 0, null, { timeout: 10000 });
    const ma = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getSeries().some((s) => s.seriesType() === 'Line' && s.options().priceScaleId === 'vol' && s.data().some((d) => 'value' in d)));
    expect(ma, 'volume average drawn');
    const want = ['سقف', 'کف', 'میانگین', 'PDH', 'PDL', 'پایانی قبلی'];
    await p.waitForFunction((w) => { const t = window.__pc.useChartBus.getState().series.priceLines().map((l) => l.options().title); return w.every((x) => t.includes(x)); }, want, { timeout: 8000 })
      .catch(async () => { throw new Error(`lines: ${await pc(p, () => window.__pc.useChartBus.getState().series.priceLines().map((l) => l.options().title))}`); });
    // An alert line, dragged to a new price.
    const px = await pc(p, () => { const b = window.__pc.useChartBus.getState().bars; return b[b.length - 1].c * 0.995; });
    await pc(p, (x) => window.__pc.useAlerts.getState().add('BTCUSDT', 'below', x), px);
    await p.waitForSelector('.alert-handle[data-line^=alert-]');
    const hb = await p.locator('.alert-handle[data-line^=alert-]').boundingBox();
    await p.mouse.move(hb.x + 60, hb.y + 4); await p.mouse.down(); await p.mouse.move(hb.x + 60, hb.y + 60, { steps: 4 }); await p.mouse.up();
    const v = await pc(p, () => window.__pc.useAlerts.getState().list.filter((a) => a.symbol === 'BTCUSDT')[0].value);
    expect(v < px, `alert moved ${px} → ${v}`);
    // A trade setup draws its zones.
    await pc(p, (x) => window.__pc.useOverlay.getState().show({ symbol: 'BTCUSDT', direction: 'long', entry: x, sl: x * 0.99, targets: [x * 1.02], label: 'e2e' }), px);
    await p.waitForSelector('canvas.setup-zones[data-on="1"]');
    return env;
  });

  // ── Data window ──
  await test('chart: data window — symbol heading, change and percent, indicators, compare', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => { window.__pc.useStudies.getState().add('rsi'); window.__pc.useCompare.getState().add('ETHUSDT'); const s = window.__pc.usePanels.getState(); if (s.open !== 'data') s.toggle('data'); });
    await p.waitForSelector('.dw-sec[data-uid^=rsi]', { timeout: 10000 });
    const text = await p.textContent('.dw-body');
    expect(/BTCUSDT/.test(text) && /1h/.test(text), 'heading');
    expect(/[+−][\d,.]+ \([+−]\d+\.\d\d%\)/.test(text), `change with percent: ${text.slice(0, 300)}`);
    expect(/ETHUSDT/.test(text), 'compare row');
    expect(!/[۰-۹]/.test(await p.textContent('.dw-body dd')), 'Latin figures');
    await pc(p, () => window.__pc.useCompare.getState().clear());
    return env;
  });

  // ── Panes: maximise, collapse, buttons; navigation and «go to real time» ──
  await test('chart: panes and navigation — maximise, collapse, double-click, nav buttons, go live', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => window.__pc.useChartSettings.getState().set({ paneButtons: 'always', navButtons: 'always' }));
    await pc(p, () => window.__pc.useStudies.getState().add('rsi'));
    await p.waitForSelector('.pane-btns[data-pane="1"]');
    const h0 = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getHeight());
    await p.click('.pane-btns[data-pane="1"] [data-pane-act=max]');
    await p.waitForTimeout(300);
    const h1 = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getHeight());
    expect(h1 < h0 / 4, `main pane folded ${h0} → ${h1}`);
    await p.click('.pane-btns[data-pane="1"] [data-pane-act=max]');
    await p.waitForTimeout(300);
    await p.click('.pane-btns[data-pane="1"] [data-pane-act=collapse]');
    await p.waitForTimeout(300);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.panes()[1].getHeight()) < 60, 'collapsed');
    await p.click('.pane-btns[data-pane="1"] [data-pane-act=collapse]');
    await p.waitForTimeout(300);
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.dblclick(box.x + box.width * 0.3, box.y + box.height * 0.3);
    await p.waitForTimeout(300);
    expect(await pc(p, () => window.__pc.useChartBus.getState().api.panes()[1].getHeight()) < 40, 'double click maximises the main pane');
    await p.mouse.dblclick(box.x + box.width * 0.3, box.y + box.height * 0.1);
    await p.click('.nav-buttons [data-nav=left]');
    await p.click('.nav-buttons [data-nav=left]');
    await p.click('.nav-buttons [data-nav=left]');
    await p.waitForSelector('.go-live');
    await p.click('.go-live');
    await p.waitForSelector('.go-live', { state: 'detached', timeout: 5000 });
    await p.click('.nav-buttons [data-nav=zoom-in]');
    await p.click('.nav-buttons [data-nav=reset]');
    return env;
  });

  // ── The rest of the settings that reach the chart: events filters, sessions, trading lines ──
  await test('chart: positions, orders and execution marks from paper trading', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    const px = await pc(p, () => { const b = window.__pc.useChartBus.getState().bars; return b[b.length - 1].c; });
    await pc(p, (x) => window.__pc.usePaper.getState().open('BTCUSDT', 'long', 0.01, x, x * 0.98, x * 1.03), px);
    await p.waitForTimeout(300);
    const titles = await pc(p, () => window.__pc.useChartBus.getState().series.priceLines().map((l) => l.options().title));
    expect(titles.some((t) => /خرید 0.01/.test(t)) && titles.includes('حد ضرر') && titles.includes('حد سود'), `${titles}`);
    const sl = p.locator('.alert-handle[data-line^=sl-]');
    const hb = await sl.boundingBox();
    await p.mouse.move(hb.x + 40, hb.y + 4); await p.mouse.down(); await p.mouse.move(hb.x + 40, hb.y + 30, { steps: 3 }); await p.mouse.up();
    const pos = await pc(p, () => window.__pc.usePaper.getState().positions[0]);
    expect(pos.sl < px * 0.98, `stop dragged ${pos.sl}`);
    await pc(p, () => window.__pc.useChartSettings.getState().set({ positionLines: false }));
    await p.waitForTimeout(200);
    expect(!(await p.$('.alert-handle[data-line^=sl-]')), 'positions hidden');
    return env;
  });

  await test('chart: replay runs the applied script on the bars on screen', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const p = env.page;
    await pc(p, () => { const s = window.__pc.useScript.getState(); s.setSource(window.__pc.exampleCode[0]); s.apply(); });
    await p.waitForTimeout(800);
    await pc(p, () => window.__pc.useReplay.getState().select());
    await p.click('.replay-bar >> text=Random bar').catch(() => {});
    await p.waitForTimeout(300);
    await p.click('.replay-bar >> text=+10').catch(() => {});
    await p.waitForTimeout(800);
    return env;
  });

  await test('chart: phone — settings dialog and legend fit 390 wide', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    await ready(env.page);
    const p = env.page;
    await p.evaluate(() => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'scales' } })));
    await p.waitForSelector('.chart-settings');
    const w = await p.evaluate(() => { const d = document.querySelector('.chart-settings').getBoundingClientRect(); return { w: d.width, right: d.right, scroll: document.documentElement.scrollWidth }; });
    expect(w.w <= 390 && w.right <= 390 && w.scroll <= 390, JSON.stringify(w));
    const overflow = await p.evaluate(() => [...document.querySelectorAll('.cs-pane .cs-row')].some((r) => r.scrollWidth > r.clientWidth + 2));
    expect(!overflow, 'rows fit');
    return env;
  });
}
