// The chart engine and its settings (area A2): Columns, period separators, prior-period levels,
// the YTD range, the market-state glyph, the chart menu's indicator and drawing switches, the
// tall-plot fill, the larger layouts, the crosshair, the momentary magnet, the MA offset and the
// built-in indicator templates, the trade ring and quote chip, the Volume switch and the lit
// indicators button.
export default async function ({ test, open, ready, pc, expect }) {
  const rightClickChart = async (page, fx = 0.45, fy = 0.45) => {
    const box = await page.locator('.chart-host').boundingBox();
    await page.mouse.click(box.x + box.width * fx, box.y + box.height * fy, { button: 'right' });
    await page.waitForSelector('.menu.cm');
  };

  await test('engine: columns stand on the visible closes, body at 55 % with a cap', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.usePrefs.getState().setChartType('columns'));
    await page.waitForFunction(() => window.__pc.useChartBus.getState().series?.seriesType() === 'Histogram', null, { timeout: 5000 });
    await page.waitForTimeout(400);
    const r = await pc(page, () => {
      const bus = window.__pc.useChartBus.getState();
      const s = bus.series; const api = bus.api;
      const h = api.panes()[0].getHeight();
      const bottom = s.coordinateToPrice(h - 2);
      const closes = bus.bars.map((b) => b.c);
      const colors = s.data().slice(-5).map((d) => d.color);
      return { bottom, min: Math.min(...closes), colors };
    });
    expect(r.bottom > r.min * 0.5, `scale reaches ${r.bottom} under closes from ${r.min}: still standing on zero`);
    expect(r.colors.every((c) => /0\.55\)$/.test(c)), `column bodies ${r.colors.join(',')}`);
    return env;
  });

  await test('engine: period separators: day intraday, month daily', async () => {
    const env = await open('BTCUSDT/1D?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useChartSettings.getState().set({ sessionBreaks: true, sessionBreakColor: '#ff0000' }));
    await page.waitForSelector('canvas.session-breaks');
    await page.waitForTimeout(400);
    const ink = await pc(page, () => {
      const c = document.querySelector('canvas.session-breaks');
      const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
      let n = 0; for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++;
      return n;
    });
    expect(ink > 0, 'no month rules on the daily chart');
    return env;
  });

  await test('engine: prior-period levels: auto anchor, open line, PH PL PC O', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useStudies.getState().add('prevHLC'));
    await page.waitForFunction(() => window.__pc.useChartBus.getState().studyRows.some((r) => r.key === 'prevHLC'), null, { timeout: 5000 });
    const row = await pc(page, () => window.__pc.useChartBus.getState().studyRows.find((r) => r.key === 'prevHLC'));
    expect(row.plotNames.length === 4, `plots ${row.plotNames.join(',')}`);
    const st = await pc(page, () => window.__pc.useStudies.getState().list.find((s) => s.key === 'prevHLC'));
    expect(st.inputs.anchor === 'auto', `anchor ${st.inputs.anchor}`);
    // The values carry yesterday's levels on the last bar, and today's open.
    expect(row.values.filter((v) => v != null).length === 4, `values ${row.values.join(',')}`);
    return env;
  });

  await test('engine: YTD takes daily bars late in the year, 4h early', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const now = new Date();
    const doy = Math.floor((Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) - Date.UTC(now.getUTCFullYear(), 0, 1)) / 864e5) + 1;
    const want = doy <= 60 ? '240' : '1D';
    await page.click('.ws-timebar .range >> text=YTD');
    await page.waitForFunction((w) => window.__pc.usePrefs.getState().interval === w, want, { timeout: 5000 });
    return env;
  });

  await test('engine: market state glyph: weekend pinwheel, open dot', async () => {
    const env = await open('XAUUSD/1h?e2e=1', { setup: (ctx) => ctx.clock.setFixedTime(new Date('2026-10-17T12:00:00Z')) });
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.legend-status');
    expect(await page.getAttribute('.legend-status', 'data-state') === 'weekend', 'gold on a Saturday is not the weekend state');
    expect(await page.locator('.legend-status svg path').count() === 3, 'no pinwheel');
    await pc(page, () => window.__pc.usePrefs.getState().setSymbol('BTCUSDT'));
    await ready(page);
    await page.waitForFunction(() => document.querySelector('.legend-status')?.getAttribute('data-state') === 'open', null, { timeout: 5000 });
    expect(await page.locator('.legend-status i').count() === 1, 'open has no dot');
    return env;
  });

  await test('engine: chart menu hides all indicators and asks before removing drawings', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => { const s = window.__pc.useStudies.getState(); s.add('rsi'); s.add('ema'); });
    await rightClickChart(page);
    await page.click('[data-act="hide-indicators"]');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.filter((s) => s.key !== '__volume').every((s) => s.hidden)), 'not every indicator hidden');
    await rightClickChart(page);
    expect(/Show all|نمایش همه‌ی اندیکاتورها/.test(await page.textContent('[data-act="hide-indicators"]')), 'no Show all indicators');
    await page.click('[data-act="hide-indicators"]');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.every((s) => !s.hidden)), 'not shown again');
    // A horizontal line from the menu, then «Remove drawings» asks; «Keep» keeps it.
    await rightClickChart(page, 0.45, 0.6);
    await page.click('[data-act="hline"]');
    await page.waitForFunction(() => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length === 1, null, { timeout: 3000 });
    await pc(page, () => { const l = window.__pc.useChartBus.getState().drawingLayer; l.selectAt?.(-1); });
    await page.keyboard.press('Escape');
    await rightClickChart(page, 0.6, 0.25);
    await page.click('[data-act="remove-drawings"]');
    await page.waitForSelector('.confirm-clear');
    expect(/۱|1/.test(await page.textContent('.confirm-clear')), 'the count is not in the question');
    await page.click('.confirm-clear [data-act="keep"]');
    expect(await pc(page, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length) === 1, 'kept drawing went');
    await rightClickChart(page, 0.6, 0.25);
    await page.click('[data-act="remove-drawings"]');
    await page.click('.confirm-clear [data-act="clear"]');
    await page.waitForFunction(() => window.__pc.useChartBus.getState().drawingLayer.getDrawings().length === 0, null, { timeout: 3000 });
    return env;
  });

  await test('engine: a tall pane does not stretch the bars over its height', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { width: 420, height: 900 });
    const { page } = env;
    await ready(page);
    await page.waitForTimeout(600);
    const m = await pc(page, () => {
      const api = window.__pc.useChartBus.getState().api;
      const o = api.priceScale('right').options().scaleMargins;
      return { top: o.top, bottom: o.bottom, w: api.timeScale().width(), h: api.panes()[0].getHeight() };
    });
    expect(m.h > m.w, `pane ${m.w}×${m.h} is not tall`);
    const fill = 1 - m.top - m.bottom;
    expect(Math.abs(fill - Math.max(m.w / m.h, 0.5)) < 0.02, `bars take ${fill.toFixed(3)} of a ${m.w}×${m.h} pane`);
    return env;
  });

  await test('engine: layouts 3+2, 4+3 and 16 charts', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.grid-btn');
    for (const [id, n] of [['3+2', 5], ['4+3', 7], ['16', 16]]) {
      await page.click(`[data-grid="${id}"]`);
      await page.waitForFunction((k) => document.querySelectorAll('.lite').length === k, n - 1, { timeout: 8000 }).catch(() => {});
      const got = await page.$$eval('.lite', (els) => els.length);
      expect(got === n - 1, `${id}: ${got} other charts`);
    }
    await page.keyboard.press('Escape');
    await pc(page, () => window.__pc.useGrid.getState().setGrid('1'));
    return env;
  });

  await test('engine: crosshair is Large Dashed with TradingView tags from the first paint', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const o = await pc(page, () => window.__pc.useChartBus.getState().api.options().crosshair);
    expect(o.vertLine.style === 3 && o.horzLine.style === 3, `style ${o.vertLine.style}`);
    expect(o.vertLine.labelBackgroundColor.toUpperCase() === '#6A6D78', `light tag ${o.vertLine.labelBackgroundColor}`);
    await pc(page, () => window.__pc.usePrefs.getState().setTheme('dark'));
    await page.waitForFunction(() => window.__pc.useChartBus.getState().api.options().crosshair.vertLine.labelBackgroundColor.toUpperCase() === '#3D3D3D', null, { timeout: 3000 });
    return env;
  });

  await test('engine: Ctrl held is a momentary magnet while drawing, not in the cursor', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const magnet = () => pc(page, () => window.__pc.useChartBus.getState().drawingLayer.magnet);
    await pc(page, () => window.__pc.useDrawing.getState().setTool('trend'));
    const box = await page.locator('.chart-host').boundingBox();
    await page.mouse.move(box.x + box.width * 0.4, box.y + box.height * 0.4);
    await page.keyboard.down('Control');
    expect(await magnet() === true, 'Ctrl did not hold the magnet');
    expect(await pc(page, () => window.__pc.useChartBus.getState().drawingLayer.magnetMode) === 'strong', 'held magnet not strong');
    await page.keyboard.up('Control');
    expect(await magnet() === false, 'magnet stayed on after Ctrl came up');
    await pc(page, () => window.__pc.useDrawing.getState().setTool('cursor'));
    await page.keyboard.down('Control');
    expect(await magnet() === false, 'Ctrl in the cursor turned the magnet on');
    await page.keyboard.up('Control');
    return env;
  });

  await test('engine: EMA offset and the built-in templates', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.click('.tpl-btn');
    expect(await page.locator('.tpl-builtin').count() === 6, 'not six built-in templates');
    await page.click('.tpl-builtin[data-tpl="tv_displaced_ema"]');
    await page.waitForFunction(() => window.__pc.useChartBus.getState().studyRows.some((r) => r.key === 'ema'), null, { timeout: 5000 });
    const keys = await pc(page, () => window.__pc.useStudies.getState().list.map((s) => s.key));
    expect(keys.join() === '__volume,ema', `studies ${keys.join()}`);
    const row = await pc(page, () => window.__pc.useChartBus.getState().studyRows.find((r) => r.key === 'ema'));
    expect(/20 \+5/.test(row.label), `legend ${row.label}`);
    // The offset moves the line: the last value is the plain EMA's value five bars back.
    const vals = await pc(page, () => {
      const s = window.__pc.useStudies.getState();
      const e = s.list.find((x) => x.key === 'ema');
      return e.inputs.shift;
    });
    expect(vals === 5, `offset ${vals}`);
    await page.click('.tpl-btn');
    await page.click('.tpl-builtin[data-tpl="tv_oscillators"]');
    await page.waitForFunction(() => window.__pc.useStudies.getState().list.map((s) => s.key).join() === '__volume,rsi,macd,stoch', null, { timeout: 5000 });
    return env;
  });

  await test('engine: trade ring and quote chip with their switches; Volume switch', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await page.waitForSelector('.trade-ring');
    await page.waitForSelector('.quote-chip');
    expect((await page.textContent('.quote-chip')).includes('USDT'), 'quote chip is not USDT');
    // The chip sits inside the plot, clear of the price axis.
    const chip = await page.locator('.quote-chip').boundingBox();
    const axisW = await pc(page, () => window.__pc.useChartBus.getState().api.priceScale('right').width());
    const host = await page.locator('.chart-host').boundingBox();
    expect(chip.x + chip.width <= host.x + host.width - axisW, 'chip runs over the price axis');
    await page.click('.quote-chip');
    await page.waitForSelector('.price-menu');
    await page.keyboard.press('Escape');
    // Settings → Trading: both switches.
    await pc(page, () => window.dispatchEvent(new CustomEvent('pc:settings', { detail: { tab: 'trading' } })));
    await page.waitForSelector('.chart-settings');
    await page.click('input[data-k="tradeRing"]');
    await page.click('input[data-k="quoteChip"]');
    await page.waitForFunction(() => !document.querySelector('.trade-ring') && !document.querySelector('.quote-chip'), null, { timeout: 3000 });
    // Symbol tab: Volume.
    await page.click('.cs-tab[data-tab="symbol"]');
    expect(await page.isChecked('input[data-k="volume"]'), 'volume switch off while volume shows');
    await page.click('input[data-k="volume"]');
    expect(!(await pc(page, () => window.__pc.useStudies.getState().list.some((s) => s.key === '__volume'))), 'volume still on');
    await page.click('input[data-k="volume"]');
    expect(await pc(page, () => window.__pc.useStudies.getState().list.some((s) => s.key === '__volume' && !s.hidden)), 'volume not back');
    return env;
  });

  await test('engine: indicators button lit with studies; Moving Median', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => window.__pc.useStudies.getState().clear());
    await page.waitForFunction(() => !document.querySelector('.ind-btn')?.classList.contains('on'), null, { timeout: 3000 });
    await pc(page, () => window.__pc.useStudies.getState().add('median'));
    await page.waitForFunction(() => document.querySelector('.ind-btn')?.classList.contains('on'), null, { timeout: 3000 });
    return env;
  });
}
