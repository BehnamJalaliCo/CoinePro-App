// The Kotlin features ported in wave three: the chart's one undo (indicators, interval and range,
// chart type and drawings in one stack), the paper position's «برعکس کردن», the paper ticket's
// «فقط کاهش موقعیت», and the watchlist's «تحلیل» opening the list side by side.
export default async function ({ test, open, ready, pc, expect }) {
  const start = async (path = 'BTCUSDT/1h?e2e=1', opts = {}) => { const env = await open(path, opts); await ready(env.page); return env; };
  const layer = (page, fn, arg) => page.evaluate(([src, a]) => (0, eval)(src)(window.__pc.useChartBus.getState().drawingLayer, a), [String(fn), arg]);
  const count = (page) => layer(page, (l) => l.getDrawings().length);

  await test('gaps3: undo and redo walk indicators, interval, chart type and drawings in order; Ctrl+Z and the top bar agree', async () => {
    const env = await start();
    const { page: p } = env;
    const studies = () => pc(p, () => window.__pc.useStudies.getState().list.length);
    const n0 = await studies();
    // An interval, a drawing, a chart type, an indicator — in that order.
    await pc(p, () => window.__pc.usePrefs.getState().setInterval('15'));
    await ready(p);
    const close = await pc(p, () => window.__pc.useChartBus.getState().bars.at(-1).c);
    await layer(p, (l, price) => l.addHLine(price), close);
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('line'));
    await pc(p, () => window.__pc.useStudies.getState().add('rsi'));
    expect(JSON.stringify(await pc(p, () => window.__pc.chartHistory.kinds())) === JSON.stringify(['chart', 'drawing', 'chart', 'chart']), `stack ${JSON.stringify(await pc(p, () => window.__pc.chartHistory.kinds()))}`);
    const undo = p.locator('.ws-top [data-act="undo"]');
    expect(await undo.isEnabled(), 'top bar undo enabled');
    expect(/آخرین تغییرِ اندیکاتور، بازه، نوعِ چارت یا ترسیم/.test(await undo.getAttribute('title')), 'the undo says what it takes back');
    // The indicator goes first, by the top bar.
    await undo.click();
    expect(await studies() === n0, 'undo took the indicator off');
    // Then the chart type, by Ctrl+Z on the chart.
    await p.keyboard.press('Control+z');
    expect(await pc(p, () => window.__pc.usePrefs.getState().chartType) === 'candles', 'Ctrl+Z took the chart type back');
    // Then the drawing — the layer's own step.
    await p.keyboard.press('Control+z');
    expect(await count(p) === 0, 'the drawing came off');
    // Then the interval.
    await p.keyboard.press('Control+z');
    expect(await pc(p, () => window.__pc.usePrefs.getState().interval) === '60', 'the interval came back');
    expect(!(await undo.isEnabled()), 'nothing left to undo');
    // Redo puts all four back, in order.
    await p.keyboard.press('Control+y');
    expect(await pc(p, () => window.__pc.usePrefs.getState().interval) === '15', 'redo interval');
    await p.locator('.ws-top [data-act="redo"]').click();
    expect(await count(p) === 1, 'redo drawing');
    await p.keyboard.press('Control+Shift+z');
    expect(await pc(p, () => window.__pc.usePrefs.getState().chartType) === 'line', 'redo chart type');
    await p.keyboard.press('Control+y');
    expect(await studies() === n0 + 1, 'redo indicator');
    // A new change clears redo; a new symbol clears everything.
    await p.keyboard.press('Control+z');
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('area'));
    expect(!(await pc(p, () => window.__pc.useChartBus.getState().canRedo)), 'a new change dropped redo');
    await pc(p, () => window.__pc.usePrefs.getState().setSymbol('ETHUSDT'));
    await ready(p);
    expect(!(await pc(p, () => window.__pc.useChartBus.getState().canUndo)), 'a new symbol starts a new history');
    return env;
  });

  await test('gaps3: a time-bar range is one undo step that brings the old interval and span back', async () => {
    const env = await start();
    const { page: p } = env;
    await p.click('.ws-timebar .range >> text=1D');
    await p.waitForFunction(() => window.__pc.chartHistory.store.getState().range === '1D');
    expect(JSON.stringify(await pc(p, () => window.__pc.chartHistory.kinds())) === '["chart"]', 'one step for a range');
    const iv = await pc(p, () => window.__pc.usePrefs.getState().interval);
    expect(iv !== '60', `the range moved the interval (${iv})`);
    await pc(p, () => window.__pc.chartHistory.undo());
    expect(await pc(p, () => window.__pc.usePrefs.getState().interval) === '60', 'undo brought the interval back');
    expect(await pc(p, () => window.__pc.chartHistory.store.getState().range) === null, 'and unmarked the range');
    await pc(p, () => window.__pc.chartHistory.redo());
    await p.waitForSelector('.ws-timebar .range.on >> text=1D');
    return env;
  });

  await test('gaps3: the phone sheet and rail undo the chart, not only drawings', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page: p } = env;
    await pc(p, () => window.__pc.usePrefs.getState().setChartType('line'));
    await p.click('.ws-top .more-btn');
    const tile = p.locator('.sheet .undo-tile');
    expect(/آخرین تغییرِ/.test(await tile.getAttribute('title')), 'the sheet tile carries the note');
    await tile.click();
    expect(await pc(p, () => window.__pc.usePrefs.getState().chartType) === 'candles', 'the sheet undo took the type back');
    return env;
  });

  const paperReady = async (p) => {
    await pc(p, () => { const s = window.__pc.usePaper.getState(); s.wipe(); s.setRules({ leverage: 1 }); window.__pc.usePanels.getState().setBottom('paper'); });
    await p.waitForSelector('.paper-ticket .buy');
    await p.waitForFunction(() => !document.querySelector('.paper-ticket .buy').disabled, null, { timeout: 10000 });
  };

  await test('gaps3: «برعکس کردن» closes a paper position and opens the same size on the other side', async () => {
    const env = await start();
    const { page: p } = env;
    await paperReady(p);
    await p.fill('.pp-ticket .pp-f:has-text("مقدار") input', '0.01');
    await p.click('.paper-ticket .buy');
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 1);
    const rev = p.locator('.pp-pos [data-act="reverse"]');
    expect(await rev.getAttribute('aria-label') === 'برعکس کردن', 'the reverse button is named');
    await rev.click();
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions[0]?.side === 'short');
    const st = await pc(p, () => { const s = window.__pc.usePaper.getState(); return { n: s.positions.length, qty: s.positions[0].qty, reason: s.history[0]?.reason, fills: s.fills.length }; });
    expect(st.n === 1 && st.qty === 0.01 && st.reason === 'rev' && st.fills === 3, JSON.stringify(st));
    await p.click('.pp-tab >> text=تاریخچه');
    await p.waitForSelector('.pp-hist >> text=برعکس شد');
    return env;
  });

  await test('gaps3: «فقط کاهش موقعیت» refuses with nothing to reduce, then only reduces', async () => {
    const env = await start();
    const { page: p } = env;
    await paperReady(p);
    const box = p.locator('.pp-ticket [data-act="reduce-only"]');
    expect((await p.locator('.pp-ticket .pp-check').textContent()).includes('فقط کاهش موقعیت'), 'the switch is named');
    await box.check();
    await p.fill('.pp-ticket .pp-f:has-text("مقدار") input', '0.01');
    await p.click('.paper-ticket .sell');
    await p.waitForSelector('.pp-err >> text=موقعیتی برای کاهش نبود');
    expect(await pc(p, () => window.__pc.usePaper.getState().positions.length) === 0, 'nothing opened');
    await box.uncheck();
    await p.fill('.pp-ticket .pp-f:has-text("مقدار") input', '0.02');
    await p.click('.paper-ticket .buy');
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 1);
    await box.check();
    await p.fill('.pp-ticket .pp-f:has-text("مقدار") input', '0.05');
    await p.click('.paper-ticket .sell');
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 0);
    const h = await pc(p, () => window.__pc.usePaper.getState().history.length);
    expect(h === 1, `closed the long and opened no short (${h})`);
    return env;
  });

  await test('gaps3: the watchlist «تحلیل» opens the list side by side in the multi-chart layout', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { setup: (ctx) => ctx.addInitScript(() => {
      if (!sessionStorage.getItem('seeded')) {
        sessionStorage.setItem('seeded', '1');
        localStorage.setItem('pc.watchlists.v2', JSON.stringify({ lists: [{ id: 'a', name: 'A', symbols: ['ETHUSDT', 'SOLUSDT', 'XAUUSD'] }, { id: 'b', name: 'B', symbols: ['ETHUSDT'] }], active: 'a', flags: {}, notes: {} }));
      }
    }) });
    const { page: p } = env;
    await pc(p, () => window.__pc.usePanels.getState().show('watchlist'));
    const btn = p.locator('.wl-head [data-act="compare"]');
    await btn.waitFor({ timeout: 10000 });
    expect(await btn.getAttribute('aria-label') === 'تحلیل', 'named «تحلیل»');
    await btn.click();
    await p.waitForFunction(() => window.__pc.useGrid.getState().grid === '3c');
    const st = await pc(p, () => ({ main: window.__pc.usePrefs.getState().symbol, cells: window.__pc.useGrid.getState().cells.slice(0, 2).map((c) => c.symbol) }));
    expect(st.main === 'ETHUSDT' && st.cells.join() === 'SOLUSDT,XAUUSD', JSON.stringify(st));
    // One market is not a comparison: the action goes.
    await pc(p, () => window.__pc.useWatchlist.getState().setActive('b'));
    await p.waitForFunction(() => !document.querySelector('.wl-head [data-act="compare"]'));
    await pc(p, () => window.__pc.useGrid.getState().setGrid('1'));
    return env;
  });
}
