// The studies area: indicator settings, the indicators dialog, the catalogue, NamaScript, the strategy
// tester, replay, the bottom dock, paper trading and help. Same helpers as run.mjs.
export default async function ({ test, open, ready, pc, expect }) {
  const addStudy = (page, key) => pc(page, (k) => window.__pc.useStudies.getState().add(k), key);
  const study = (page, uid) => pc(page, (u) => window.__pc.useStudies.getState().list.find((s) => s.uid === u), uid);
  const openSettings = async (page, uid) => {
    await pc(page, (u) => window.dispatchEvent(new CustomEvent('pc:study-settings', { detail: { uid: u } })), uid);
    await page.waitForSelector('.st-dialog', { timeout: 5000 });
  };

  await test('studies: settings show changes live, Cancel puts them back, Ok keeps them', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const uid = await addStudy(env.page, 'rsi');
    await openSettings(env.page, uid);
    const n = env.page.locator('.st-dialog .st-num').first();
    await n.fill('21');
    expect((await study(env.page, uid)).inputs.period === 21, 'period not applied live');
    await env.page.click('.st-foot >> text=انصراف');
    expect((await study(env.page, uid)).inputs.period === 14, 'cancel did not restore');
    await openSettings(env.page, uid);
    await env.page.locator('.st-dialog .st-num').first().fill('9');
    await env.page.click('.st-foot .btn-primary');
    expect((await study(env.page, uid)).inputs.period === 9, 'ok did not keep');
    // Labels are Persian in Persian, and the source menu is labelled.
    await openSettings(env.page, uid);
    const text = await env.page.textContent('.st-body');
    expect(text.includes('طول') && text.includes('منبع') && text.includes('بسته شدن'), `labels: ${text.slice(0, 120)}`);
    await env.page.keyboard.press('Escape');
    return env;
  });

  await test('studies: style tab — plot colour, visibility, type, levels, precision, scale', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const uid = await addStudy(env.page, 'rsi');
    await openSettings(env.page, uid);
    await env.page.click('.st-tab >> nth=1');
    await env.page.click('.st-swatch >> nth=0');
    await env.page.click('.st-color .st-chip >> nth=10');
    await env.page.keyboard.press('Escape');
    let s = await study(env.page, uid);
    expect(s.style.plots[0].color === '#F23645', `colour ${JSON.stringify(s.style)}`);
    await env.page.locator('.st-plot input[type=checkbox]').nth(1).uncheck();
    await env.page.locator('.st-plot select').first().selectOption('step');
    await env.page.click('.st-add');
    await env.page.locator('.st-dialog select').filter({ hasText: 'پیش‌فرض' }).first().selectOption('4');
    await env.page.click('.st-foot .btn-primary');
    s = await study(env.page, uid);
    expect(s.style.plots[1].visible === false && s.style.plots[0].type === 'step' && s.style.levels.length === 3 && s.style.precision === 4, JSON.stringify(s.style));
    return env;
  });

  await test('studies: visibility by timeframe, saved defaults, duplicate and move', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const uid = await addStudy(env.page, 'macd');
    await openSettings(env.page, uid);
    await env.page.click('.st-tab >> nth=2');
    await env.page.locator('.st-vis input[type=checkbox]').nth(2).uncheck();
    await env.page.click('.st-foot .btn-primary');
    expect((await study(env.page, uid)).visibility.hours.on === false, 'hours visibility');
    await openSettings(env.page, uid);
    await env.page.locator('.st-dialog .st-num').first().fill('8');
    await env.page.click('.st-defaults');
    await env.page.click('.st-pop >> text=ذخیره به‌عنوان پیش‌فرض');
    await env.page.click('.st-foot .btn-primary');
    const u2 = await addStudy(env.page, 'macd');
    expect((await study(env.page, u2)).inputs.fast === 8, 'saved default not used');
    const u3 = await pc(env.page, (u) => window.__pc.useStudies.getState().duplicate(u), u2);
    await pc(env.page, (u) => window.__pc.useStudies.getState().move(u, -1), u3);
    const order = await pc(env.page, () => window.__pc.useStudies.getState().list.map((s) => s.uid));
    expect(order.indexOf(u3) < order.indexOf(u2), 'move up');
    await pc(env.page, () => window.__pc.useStudies.getState().resetDefault('macd'));
    return env;
  });

  await test('studies: a hidden oscillator keeps its pane, later legends stay in place', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const a = await addStudy(env.page, 'rsi');
    await addStudy(env.page, 'macd');
    await addStudy(env.page, 'stoch');
    await env.page.waitForTimeout(500);
    await pc(env.page, (u) => window.__pc.useStudies.getState().toggle(u), a);
    await env.page.waitForTimeout(700);
    const tops = await env.page.$$eval('.pane-legend', (els) => els.map((e) => e.getBoundingClientRect().top));
    expect(tops.length === 3, `pane legends ${tops.length}`);
    expect(tops[0] < tops[1] && tops[1] < tops[2], `tops ${tops.join(',')}`);
    return env;
  });

  await test('studies: an indicator on an indicator, and on a higher timeframe', async () => {
    const env = await open('BTCUSDT/15m?e2e=1');
    await ready(env.page);
    const r = await addStudy(env.page, 'rsi');
    const m = await addStudy(env.page, 'ma');
    await pc(env.page, ([mu, ru]) => { const s = window.__pc.useStudies.getState(); const st = s.list.find((x) => x.uid === mu); s.setInputs(mu, { ...st.inputs, source: `study:${ru}:0`, period: 5 }); }, [m, r]);
    await env.page.waitForTimeout(500);
    const legend = await env.page.textContent('.pane-legend');
    expect(/MA|RSI/.test(legend ?? ''), `legend ${legend}`);
    await openSettings(env.page, m);
    const opts = await env.page.locator('.st-dialog select').first().locator('option').allTextContents();
    expect(opts.some((o) => o.includes('RSI')), `source options ${opts.join('|')}`);
    await env.page.locator('.st-dialog select').filter({ hasText: 'چارت' }).selectOption('240');
    await env.page.click('.st-foot .btn-primary');
    expect((await study(env.page, m)).timeframe === '240', 'timeframe');
    return env;
  });

  await test('studies: every added indicator, profile and pattern computes and draws', async () => {
    const env = await open('ETHUSDT/1h?e2e=1');
    await ready(env.page);
    const all = await pc(env.page, () => window.__pc.studyKeys);
    const old = new Set(await pc(env.page, () => window.__pc.indicators));
    const keys = all.filter((k) => !old.has(k));
    expect(keys.length >= 70, `only ${keys.length} new indicators`);
    for (let i = 0; i < keys.length; i += 8) {
      const batch = keys.slice(i, i + 8);
      await pc(env.page, (ks) => { const s = window.__pc.useStudies.getState(); for (const k of ks) s.add(k); }, batch);
      await env.page.waitForTimeout(300);
      const rows = await env.page.$$eval('.legend-study', (els) => els.length);
      expect(rows >= batch.length, `legend rows ${rows} for ${batch.join(',')}`);
      await pc(env.page, () => window.__pc.useStudies.getState().clear());
    }
    // A table and a profile appear over the chart.
    await pc(env.page, () => { const s = window.__pc.useStudies.getState(); s.add('performance'); s.add('vpVisible'); });
    await env.page.waitForSelector('.st-tables .ns-table', { timeout: 5000 });
    return env;
  });

  await test('studies: indicators dialog — tabs, keys, Arabic letters, help, strategies, recent', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await env.page.keyboard.press('/');
    await env.page.waitForSelector('.tvd');
    await env.page.keyboard.press('End');
    const last = await env.page.$eval('.tvd .ind-row.on', (el) => { const r = el.getBoundingClientRect(); const l = el.closest('.tvd-list').getBoundingClientRect(); return r.bottom <= l.bottom + 1 && r.top >= l.top - 1; });
    expect(last, 'the last row is not scrolled into view');
    await env.page.keyboard.press('Home');
    await env.page.keyboard.press('PageDown');
    const idx = await env.page.$$eval('.tvd .ind-row', (els) => els.findIndex((e) => e.classList.contains('on')));
    expect(idx === 10, `PageDown moved to ${idx}`);
    // Arabic yeh and kaf, a zero-width non-joiner and a diacritic still find the Persian name.
    await env.page.fill('.tvd-search input', 'ميانگين متحرك');
    await env.page.waitForTimeout(150);
    expect((await env.page.$$eval('.tvd .ind-row', (els) => els.length)) > 0, 'Arabic letters found nothing');
    await env.page.fill('.tvd-search input', 'zzzzqq');
    expect((await env.page.textContent('.tvd-list')).includes('پیدا نشد'), 'empty text');
    await env.page.fill('.tvd-search input', '');
    for (const [n, want] of [[2, 'vpVisible'], [3, 'cp_hammer']]) {
      await env.page.click(`.tvd-tabs .chip >> nth=${n}`);
      const ids = await env.page.$$eval('.tvd .ind-name', (els) => els.map((e) => e.textContent));
      expect(ids.length > 3, `tab ${n} empty`);
      void want;
    }
    await env.page.click('.tvd-tabs .chip >> nth=2');
    await env.page.click('.tvd .ind-row >> nth=0');
    const recent = await pc(env.page, () => window.__pc.useStudies.getState().recent);
    expect(recent.length === 1, `recent ${recent}`);
    await env.page.click('.tvd-tabs .chip >> nth=1');
    await env.page.click('.tvd .ind-row >> nth=0');
    await env.page.waitForFunction(() => window.__pc.useScript.getState().on, null, { timeout: 5000 });
    // Help from a row opens that indicator's entry.
    await env.page.click('.tvd-tabs .chip >> nth=0');
    await env.page.hover('.tvd .ind-row >> nth=1');
    await env.page.click('.tvd .ind-row.on .ind-help');
    await env.page.waitForSelector('.help-entry h2', { timeout: 8000 });
    return env;
  });

  await test('studies: the chart volume, once removed, comes back from the indicators dialog', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await pc(env.page, () => window.__pc.useStudies.getState().setVolume(false));
    await env.page.keyboard.press('/');
    await env.page.waitForSelector('.tvd');
    await env.page.fill('.tvd-search input', 'حجم');
    await env.page.click('.tvd .ind-row >> text=حجم (روی چارت)');
    expect(await pc(env.page, () => window.__pc.useStudies.getState().list.some((s) => s.key === '__volume' && !s.hidden)), 'volume not back');
    return env;
  });

  await test('studies: help resolves every id, shows diagrams and sections, adds to the chart', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    for (const id of ['regchannel', 'cp_hammer', 'pivotsMulti', 'flag', 'chandeKroll', 'vpSession']) {
      await pc(env.page, (x) => window.__pc.usePage.getState().open('help', x), id);
      await env.page.waitForSelector('.help-entry h2', { timeout: 8000 });
      const h = await env.page.textContent('.help-entry h2');
      expect((h ?? '').length > 2, `${id}: no title`);
      await pc(env.page, () => window.__pc.usePage.getState().close());
    }
    await pc(env.page, () => window.__pc.usePage.getState().open('help', 'ma'));
    await env.page.waitForSelector('.help-entry svg.help-diagram, .help-entry .help-sections', { timeout: 8000 });
    expect(await env.page.$('.help-sections .help-section'), 'no sections in Persian');
    await env.page.click('.help-entry-bar .btn-primary');
    await env.page.waitForFunction(() => window.__pc.useStudies.getState().list.some((s) => s.key === 'ma'), null, { timeout: 5000 });
    return env;
  });

  const bottom = (page, tab) => pc(page, (t) => { const p = window.__pc.usePanels.getState(); if (p.bottom !== t) p.setBottom(t); }, tab);

  await test('studies: NamaScript editor — highlight, autocomplete, comment, brackets, find, error line', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await bottom(env.page, 'namascript');
    await pc(env.page, () => window.__pc.useScript.getState().setSource('x = ta.sma(close, 20)\nplot(x, "SMA", color.blue)'));
    await env.page.waitForSelector('.ce-pre .tk-kw');
    expect(await env.page.$('.ce-pre .tk-num') && await env.page.$('.ce-pre .tk-str'), 'numbers and strings not highlighted');
    await env.page.click('.ce-ta');
    await env.page.keyboard.press('Control+End');
    await env.page.keyboard.type('\ny = ta.em');
    await env.page.waitForSelector('.ce-ac .ce-ac-row', { timeout: 3000 });
    await env.page.keyboard.press('Enter');
    let src = await pc(env.page, () => window.__pc.useScript.getState().source);
    expect(src.includes('y = ta.ema()'), `autocomplete: ${src}`);
    await env.page.keyboard.type('close, 9');
    await env.page.waitForSelector('.ce-hint', { timeout: 3000 });
    await env.page.keyboard.press('Control+/');
    src = await pc(env.page, () => window.__pc.useScript.getState().source);
    expect(src.split('\n')[2].startsWith('// y = ta.ema(close, 9)'), `comment: ${src.split('\n')[2]}`);
    await env.page.keyboard.press('Control+f');
    await env.page.waitForSelector('.ce-find input');
    // Fill the box itself: on a slow runner the keys can land before the box takes the focus.
    await env.page.fill('.ce-find input', 'close');
    await env.page.waitForFunction(() => /\/2/.test(document.querySelector('.ce-count')?.textContent ?? ''), null, { timeout: 3000 }).catch(() => {});
    expect(/\/2/.test(await env.page.textContent('.ce-count')), 'find count');
    await env.page.keyboard.press('Escape');
    expect((await env.page.textContent('.ce-status')).includes('Line') || (await env.page.textContent('.ce-status')).includes('خط'), 'status line');
    // A syntax error points at its line.
    await pc(env.page, () => { const s = window.__pc.useScript.getState(); s.setSource('a = 1\nplot(close)\nb = (]'); s.apply(); });
    await env.page.waitForFunction(() => { const r = window.__pc.useScript.getState().result; return r && !r.ok; }, null, { timeout: 10000 });
    const line = await pc(env.page, () => window.__pc.useScript.getState().result.errorLine);
    expect(line === 3, `error line ${line}`);
    await env.page.waitForSelector('.ce-errline');
    return env;
  });

  await test('studies: NamaScript tabs, save and versions, bar mode, inputs, console, tables', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await bottom(env.page, 'namascript');
    await env.page.click('.ns-newtab');
    expect((await env.page.$$('.ns-tab')).length === 2, 'tabs');
    await pc(env.page, () => window.__pc.useScript.getState().setSource('indicator("T")\nlen = input.int(14, "Length")\nplot(ta.rsi(close, len), "RSI", color.purple)'));
    await env.page.click('.ns-btn >> nth=1');
    await pc(env.page, () => window.__pc.useScript.getState().setSource('indicator("T")\nlen = input.int(10, "Length")\nplot(ta.rsi(close, len), "RSI", color.purple)'));
    await env.page.keyboard.press('Control+s');
    // Outside the editor Ctrl+S is the layout's (it asks for a name on a new layout); close that.
    if (await env.page.waitForSelector('.scrim', { timeout: 1500 }).catch(() => null)) { await env.page.keyboard.press('Escape'); await env.page.waitForSelector('.scrim', { state: 'detached', timeout: 3000 }); }
    await env.page.click('.ce-ta');
    await env.page.keyboard.press('Control+s');
    const lib = await pc(env.page, () => window.__pc.useScript.getState().lib);
    expect(lib.length === 1 && lib[0].versions.length === 1, `library ${JSON.stringify(lib.map((x) => x.versions?.length))}`);
    await env.page.click('.ns-btn.primary');
    await env.page.waitForSelector('.ns-log.ok', { timeout: 10000 });
    await env.page.click('.ns-btn >> text=/Inputs|ورودی/');
    await env.page.waitForSelector('.st-dialog .st-num');
    await env.page.locator('.st-dialog .st-num').first().fill('21');
    await env.page.click('.st-foot .btn-primary');
    await env.page.waitForFunction(() => window.__pc.useScript.getState().inputs.Length === 21, null, { timeout: 5000 });
    await env.page.click('.ns-toggle input');
    await env.page.waitForFunction(() => { const s = window.__pc.useScript.getState(); return s.barMode && !s.running && s.result?.ok; }, null, { timeout: 10000 });
    const i = await pc(env.page, () => window.__pc.exampleCode.findIndex((c) => c.includes('table.new')));
    await pc(env.page, (k) => { const s = window.__pc.useScript.getState(); s.setBarMode(false); s.newTab(window.__pc.exampleCode[k], 'table'); s.apply(); }, i);
    await env.page.waitForSelector('.ns-tables .ns-table td', { timeout: 10000 });
    return env;
  });

  await test('studies: strategy tester — performance, trades to the chart, properties, optimizer, saved, alerts', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const k = await pc(env.page, () => window.__pc.exampleCode.findIndex((c) => c.includes('strategy.entry') && c.includes('input.int')));
    await pc(env.page, (x) => { const s = window.__pc.useScript.getState(); s.newTab(window.__pc.exampleCode[x], 'S'); s.apply(); }, k);
    await bottom(env.page, 'backtest');
    await env.page.waitForSelector('.bt2 .bt-stats', { timeout: 15000 });
    await env.page.click('.bt-tab >> nth=1');
    expect((await env.page.$$('.bt-table tbody tr')).length > 20, 'performance rows');
    await env.page.click('.bt-tab >> nth=2');
    await env.page.waitForSelector('.bt-trade');
    const before = await pc(env.page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    await env.page.click('.bt-trade >> nth=-1');
    await env.page.waitForTimeout(200);
    const after = await pc(env.page, () => window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange());
    expect(Math.abs(after.from - before.from) > 1, 'the chart did not move to the trade');
    const net0 = await pc(env.page, () => window.__pc.useScript.getState().backtest.net);
    await env.page.click('.bt-tab >> nth=3');
    await env.page.locator('.bt-props .st-num').nth(4).fill('0.5');
    await env.page.waitForFunction((n) => window.__pc.useScript.getState().backtest && window.__pc.useScript.getState().backtest.net !== n, net0, { timeout: 10000 });
    await env.page.click('.bt-tab >> nth=4');
    await env.page.click('.bt-opt-side .btn-primary');
    await env.page.waitForSelector('.bt-opt .bt-table tbody tr', { timeout: 30000 });
    await pc(env.page, () => window.__pc.useScript.getState().saveStrategy('E2E'));
    expect((await pc(env.page, () => window.__pc.useScript.getState().strategies.length)) === 1, 'saved strategy');
    // No strategy: two alert conditions are tested as entry and exit.
    const a = await pc(env.page, () => window.__pc.exampleCode.findIndex((c) => (c.match(/alertcondition/g) ?? []).length >= 2 && !c.includes('strategy.')));
    await pc(env.page, (x) => { const s = window.__pc.useScript.getState(); s.newTab(window.__pc.exampleCode[x], 'A'); s.apply(); }, a);
    await env.page.waitForSelector('.bt-badge', { timeout: 10000 });
    return env;
  });

  await test('studies: bottom dock resizes, maximises and keeps notes', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    await bottom(env.page, 'notes');
    await env.page.fill('.notes-area', 'buy the dip at 80k');
    await env.page.waitForFunction(() => JSON.parse(localStorage.getItem('pc.notes.v1') ?? '{}').text === 'buy the dip at 80k', null, { timeout: 3000 });
    const h0 = await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height);
    const g = await env.page.locator('.dock-grip').boundingBox();
    await env.page.mouse.move(g.x + 200, g.y + 4); await env.page.mouse.down(); await env.page.mouse.move(g.x + 200, g.y - 96, { steps: 5 }); await env.page.mouse.up();
    const h1 = await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height);
    expect(h1 > h0 + 60, `resize ${h0} → ${h1}`);
    await env.page.dblclick('.dock-grip');
    expect(Math.abs((await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height)) - 300) < 2, 'double-click reset');
    await env.page.click('.ws-bottom-tabs [aria-label="Maximize panel"], .ws-bottom-tabs [aria-label="بزرگ‌ترین اندازه"]');
    expect((await env.page.$eval('.ws-bottom-body', (e) => e.getBoundingClientRect().height)) > 600, 'maximise');
    return env;
  });

  await test('studies: replay — first/last, scrubber, date, click a bar, view follows, go to date', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const R = (fn, a) => pc(env.page, fn, a);
    const idx = () => R(() => window.__pc.useReplay.getState().state.idx);
    await R(() => window.__pc.useReplay.getState().select());
    await env.page.click('.replay-bar >> text=Random bar');
    await env.page.waitForSelector('.replay-bar.rp2');
    expect(/\d\d:\d\d/.test(await env.page.textContent('.rp-date')), 'bar date');
    await env.page.waitForTimeout(150);
    let view = await R(() => { const r = window.__pc.useReplay.getState(); return { lr: window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange(), last: r.slice.length - 1 }; });
    expect(view.lr.to > view.last + 10 && view.lr.from < view.last, `start view ${JSON.stringify(view)}`);
    await R(() => window.__pc.useReplay.getState().controller.stepBy(80));
    await env.page.waitForTimeout(150);
    view = await R(() => { const r = window.__pc.useReplay.getState(); return { lr: window.__pc.useChartBus.getState().api.timeScale().getVisibleLogicalRange(), last: r.slice.length - 1 }; });
    expect(view.last <= view.lr.to && view.last > view.lr.from, `view did not follow ${JSON.stringify(view)}`);
    // The scrubber, first and last bar.
    await env.page.$eval('.rp-scrub', (el) => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, '120'); el.dispatchEvent(new Event('input', { bubbles: true })); });
    expect(await idx() === 120, 'scrubber');
    await env.page.click('[title="پرش به اولین کندل"]');
    expect(await idx() === 29, 'first bar');
    await env.page.click('[title="پرش به آخرین کندل"]');
    const n = await R(() => window.__pc.useReplay.getState().state.length);
    expect(await idx() === n - 1, 'last bar');
    await env.page.$eval('.rp-scrub', (el) => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, '300'); el.dispatchEvent(new Event('input', { bubbles: true })); });
    // A click on a revealed bar goes back to it.
    await env.page.waitForTimeout(150);
    const at = await R(() => { const api = window.__pc.useChartBus.getState().api; const r = api.chartElement().getBoundingClientRect(); return { x: r.left + api.timeScale().logicalToCoordinate(280), y: r.top + 150 }; });
    await env.page.mouse.click(at.x, at.y);
    expect(Math.abs((await idx()) - 280) <= 1, `click to seek: ${await idx()}`);
    // Go to date (Alt+G) moves the replay.
    const local = await R(() => { const t = window.__pc.useReplay.getState().controller.full[200].t; return new Date(t * 1000 - new Date(t * 1000).getTimezoneOffset() * 60000).toISOString().slice(0, 16); });
    await env.page.keyboard.press('Alt+g');
    await env.page.fill('.goto-pop input', local);
    await env.page.click('.goto-pop .btn-primary');
    await env.page.waitForFunction(() => Math.abs(window.__pc.useReplay.getState().state.idx - 200) <= 1, null, { timeout: 5000 });
    return env;
  });

  await test('studies: replay practice — Shift+B, ATR lines, dragging, auto close, orders, partial, breakeven, reverse, resume', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const R = (fn, a) => pc(env.page, fn, a);
    await R(() => window.__pc.useReplay.getState().select());
    await env.page.click('.replay-bar >> text=Random bar');
    await env.page.waitForSelector('.replay-bar.rp2');
    await R(() => { const r = window.__pc.useReplay.getState(); r.controller.seek({ index: 250 }); });
    const paper0 = await R(() => window.__pc.usePaper.getState().positions.length);
    await env.page.keyboard.press('Shift+KeyB');
    let tr = await R(() => window.__pc.useReplay.getState().trades);
    expect(tr.length === 1 && tr[0].side === 'long' && tr[0].sl < tr[0].entry && tr[0].tp > tr[0].entry && tr[0].risk > 0, `practice trade ${JSON.stringify(tr)}`);
    expect(await R(() => window.__pc.usePaper.getState().positions.length) === paper0, 'Shift+B made a paper trade while replaying');
    await env.page.waitForFunction(() => document.querySelectorAll('.tl-tag').length === 3, null, { timeout: 3000 });
    // Drag the stop loss down by its tag.
    const tag = await env.page.locator('.tl-tag', { hasText: 'SL' }).boundingBox();
    await env.page.mouse.move(tag.x + 8, tag.y + 10); await env.page.mouse.down(); await env.page.mouse.move(tag.x + 8, tag.y + 50, { steps: 4 }); await env.page.mouse.up();
    const sl1 = await R(() => window.__pc.useReplay.getState().trades[0].sl);
    expect(sl1 < tr[0].sl, `drag ${tr[0].sl} → ${sl1}`);
    // Partial close and breakeven from the panel.
    await env.page.click('[title="پنل معامله‌ی تمرینی"]');
    await env.page.waitForSelector('.rp-panel');
    await env.page.click('.rp-acts >> text=50%');
    tr = await R(() => window.__pc.useReplay.getState().trades);
    expect(Math.abs(tr[0].qty - tr[0].qty0 / 2) / tr[0].qty0 < 0.01 && tr[0].realized !== 0, `partial ${JSON.stringify(tr[0])}`);
    await env.page.click('.rp-acts >> text=BE');
    tr = await R(() => window.__pc.useReplay.getState().trades);
    expect(tr[0].sl === tr[0].entry, 'breakeven');
    // Reverse: the long closes, a short opens.
    await env.page.click('.rp-acts [title="معکوس کردن"]');
    tr = await R(() => window.__pc.useReplay.getState().trades);
    expect(tr.length === 2 && tr[0].how === 'reverse' && tr[1].side === 'short' && tr[1].exit == null, `reverse ${JSON.stringify(tr.map((t) => [t.side, t.how]))}`);
    // The target closes the trade on its own as bars come.
    await R(() => { const r = window.__pc.useReplay.getState(); const t = r.trades[1]; r.modify(t.id, { tp: t.entry * 0.9999, sl: t.entry * 1.5 }); });
    await R(() => window.__pc.useReplay.getState().controller.stepBy(40));
    tr = await R(() => window.__pc.useReplay.getState().trades);
    expect(tr[1].exit != null && tr[1].how === 'tp' && typeof tr[1].r === 'number', `auto close ${JSON.stringify(tr[1])}`);
    // A limit order waits, shows its line, and is cancelled.
    await env.page.click('.rp-ticket .seg >> text=لیمیت');
    const px = await R(() => window.__pc.useReplay.getState().price());
    await env.page.fill('.rp-ticket .rp-in', String(Math.round(px * 0.5)));
    await env.page.click('.rp-ticket .btn.buy');
    expect(await R(() => window.__pc.useReplay.getState().trades.some((t) => t.pending && t.type === 'limit')), 'limit order');
    await env.page.click('.rp-acts >> text=لغو');
    expect(!(await R(() => window.__pc.useReplay.getState().trades.some((t) => t.pending))), 'cancel order');
    expect((await env.page.textContent('.rp-stats')).includes('نرخ برد'), 'statistics');
    // The session survives leaving: resume brings the trades back.
    await env.page.click('.replay-bar [title="exit"]');
    await R(() => window.__pc.useReplay.getState().select());
    await env.page.click('.replay-bar >> text=ادامه‌ی جلسه‌ی قبل');
    await env.page.waitForSelector('.replay-bar.rp2');
    expect((await R(() => window.__pc.useReplay.getState().trades.length)) === 2, 'resume');
    await env.page.click('.replay-bar [title="exit"]');
    return env;
  });

  await test('studies: replay update interval builds each bar from lower-timeframe bars', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const R = (fn, a) => pc(env.page, fn, a);
    await R(() => window.__pc.useReplay.getState().select());
    await env.page.click('.replay-bar >> text=Random bar');
    await env.page.waitForSelector('.replay-bar.rp2');
    await R(() => { const r = window.__pc.useReplay.getState(); r.controller.seek({ index: r.state.length - 12 }); });
    await env.page.click('.rp-speed');
    await env.page.click('.rp-upd[data-step="15"]');
    await env.page.waitForFunction(() => window.__pc.useReplay.getState().intrabar.state !== 'loading', null, { timeout: 10000 });
    expect(await R(() => window.__pc.useReplay.getState().intrabar.state) === 'on', 'no lower bars');
    const before = await R(() => window.__pc.useReplay.getState().slice.length);
    await R(() => window.__pc.useReplay.getState().controller.stepIntrabar());
    const s = await R(() => { const r = window.__pc.useReplay.getState(); const f = r.slice[r.slice.length - 1]; const full = r.controller.full[r.state.idx + 1]; return { len: r.slice.length, t: f.t, ft: full.t, idx: r.state.idx }; });
    expect(s.len === before + 1 && s.t === s.ft, `forming bar ${JSON.stringify(s)}`);
    await env.page.click('.rp-speed');
    await env.page.click('.rp-upd[data-step="bar"]');
    expect(await R(() => window.__pc.useReplay.getState().intrabar.state) === 'off', 'update interval off');
    await env.page.click('.rp-speed');
    await env.page.click('.rp-speed-menu >> text=3x');
    expect(await R(() => window.__pc.useReplay.getState().state.speed) === 3, 'speed');
    return env;
  });

  await test('studies: trading panel — risk size, stop and target, lines dragged, partial close, orders, journal, statistics', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    await ready(env.page);
    const R = (fn, a) => pc(env.page, fn, a);
    await bottom(env.page, 'paper');
    await env.page.waitForFunction(() => !document.querySelector('.paper-ticket .buy').disabled, null, { timeout: 10000 });
    const last = await R(() => window.__pc.useMarket.getState().tickers.BTCUSDT.last);
    const ins = env.page.locator('.pp-ticket input');
    await ins.nth(1).fill(String(Math.round(last * 0.98)));
    await ins.nth(2).fill(String(Math.round(last * 1.01)));
    await env.page.click('.pp-risk .btn');
    const qty = Number(await ins.nth(0).inputValue());
    expect(qty > 0 && Math.abs(qty * last * 0.02 - 100) < 5, `risk size ${qty}`);
    await env.page.click('.paper-ticket .buy');
    await env.page.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 1, null, { timeout: 5000 });
    const p0 = await R(() => window.__pc.usePaper.getState().positions[0]);
    expect(p0.sl > 0 && p0.tp > p0.entry, `protected ${JSON.stringify(p0)}`);
    await env.page.waitForFunction(() => document.querySelectorAll('.tl-tag').length === 3, null, { timeout: 3000 });
    const tag = await env.page.locator('.tl-tag', { hasText: 'TP' }).boundingBox();
    await env.page.mouse.move(tag.x + 8, tag.y + 10); await env.page.mouse.down(); await env.page.mouse.move(tag.x + 8, tag.y - 30, { steps: 4 }); await env.page.mouse.up();
    const tp1 = await R(() => window.__pc.usePaper.getState().positions[0].tp);
    expect(tp1 > p0.tp, `drag tp ${p0.tp} → ${tp1}`);
    await env.page.click('.paper-lists .pp-part >> text=50%');
    const after = await R(() => ({ q: window.__pc.usePaper.getState().positions[0].qty, h: window.__pc.usePaper.getState().history.length }));
    expect(Math.abs(after.q - p0.qty / 2) / p0.qty < 0.01 && after.h === 1, `partial ${JSON.stringify(after)}`);
    // A limit order: its badge, its line (dragged), cancelled.
    await env.page.selectOption('.pp-ticket select', 'limit');
    await ins.nth(1).fill(String(Math.round(last * 0.995)));
    await env.page.click('.paper-ticket .buy');
    await env.page.waitForFunction(() => window.__pc.usePaper.getState().orders.length === 1, null, { timeout: 5000 });
    await env.page.waitForSelector('.pp-tab.on >> text=سفارش‌ها');
    expect(await env.page.isVisible('.pp-tab .pp-badge.pending'), 'orders badge');
    const ot = await env.page.locator('.tl-tag', { hasText: 'لیمیت' }).boundingBox();
    const lp0 = await R(() => window.__pc.usePaper.getState().orders[0].limitPrice);
    if (ot && ot.y > 40) {
      await env.page.mouse.move(ot.x + 8, ot.y + 10); await env.page.mouse.down(); await env.page.mouse.move(ot.x + 8, ot.y - 20, { steps: 4 }); await env.page.mouse.up();
      expect((await R(() => window.__pc.usePaper.getState().orders[0].limitPrice)) !== lp0, 'order drag');
    }
    await env.page.click('.paper-lists .bt-row:not(.bt-head) .lt');
    expect((await R(() => window.__pc.usePaper.getState().orders.length)) === 0, 'cancel');
    await env.page.click('.pp-tab >> text=موقعیت‌ها');
    await env.page.click('.paper-lists .bt-row:not(.bt-head) .lt');
    await env.page.waitForFunction(() => window.__pc.usePaper.getState().history.length === 2, null, { timeout: 5000 });
    await env.page.click('.pp-tab >> text=تاریخچه');
    await env.page.click('.paper-lists .bt-row:not(.bt-head) .pp-part >> nth=0');
    expect((await R(() => window.__pc.useJournal.getState().entries.length)) === 1, 'logged to the journal');
    await env.page.click('.pp-tab >> text=آمار');
    await env.page.waitForSelector('.pp-curve polyline');
    expect((await env.page.textContent('.pp-statsbox')).includes('بیشترین برد پیاپی'), 'streaks');
    await env.page.click('.pp-tab >> text=ژورنال');
    await env.page.waitForSelector('.pp-jr:not(.bt-head)');
    return env;
  });
}
