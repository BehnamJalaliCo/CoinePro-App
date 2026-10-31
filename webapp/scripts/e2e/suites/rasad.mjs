// Area R — «رصد», the Arena, the replay report and the chart's extras: the coach's line under the
// plot and its sheet (the readings, three sentences, «وقتی رسید خبرم کنید», the last paper trade's
// review); today's five-minute challenge with its clock, score, streak and picture; the replay
// session's report; «بازپخش از اینجا»; a drawn position opened through the order ticket; the ratio
// comparison; the candle CSV with its Solar Hijri column; the phone's symbol wheel; and how far a new
// drawing travels. Same helpers as run.mjs.
import { readFileSync } from 'node:fs';

export default async function ({ test, open, ready, pc, expect }) {
  const start = async (path = 'BTCUSDT/1h?e2e=1', opts = {}) => { const env = await open(path, opts); await ready(env.page); return env; };

  await test('rasad: the line under the plot opens the sheet — readings, three sentences, the alert and the review', async () => {
    const env = await start();
    const { page: p } = env;
    await p.waitForSelector('.rasad-line .rasad-first', { timeout: 15000 });
    const first = (await p.textContent('.rasad-line .rasad-first')).trim();
    expect(/نوسان/.test(first), `the trend sentence first: ${first}`);
    // The last paper trade on this market, closed at its stop.
    await pc(p, () => { const pp = window.__pc.usePaper.getState(); const id = pp.open('BTCUSDT', 'long', 0.01, 81000, 80000, 83000); window.__pc.usePaper.getState().close(id, 80000, 'sl'); });
    await p.click('.rasad-line');
    await p.waitForSelector('.rasad-sheet');
    expect((await p.$$('.rasad-sheet .rd-col')).length === 3, 'three readings');
    expect(/این بازار چه می‌کند/.test(await p.textContent('.rasad-sheet .rd-head')), 'the readings title');
    expect(/ADX \d+/.test(await p.textContent('.rasad-sheet [data-reading=strength] .rd-why')), 'the figure behind the word');
    const n = (await p.$$('.rasad-sheet .rasad-sentence')).length;
    expect(n === 2, `trend and levels (the studies' sentence waits for the signal layer): ${n}`);
    await pc(p, () => window.__pc.useRasadReads.getState().set([{ state: 'bull' }, { state: 'neutral' }]));
    await p.waitForFunction(() => document.querySelectorAll('.rasad-sheet .rasad-sentence').length === 3);
    expect(/از ۲ ابزار روی چارت، ۱ هم‌نظرند/.test(await p.textContent('.rasad-sheet .rasad-sentence[data-i="2"]')), await p.textContent('.rasad-sheet .rasad-sentence[data-i="2"]'));
    const review = await p.textContent('.rasad-sheet .rasad-review');
    expect(/آخرین معامله‌ی آزمایشی شما/.test(review) && /حد ضرر کارش را کرد/.test(review) && /-1\.0 برابر/.test(review), review);
    if (await p.$('.rasad-sheet .rasad-alert')) {
      const price = Number(await p.getAttribute('.rasad-sheet .rasad-alert', 'data-price'));
      await p.click('.rasad-sheet .rasad-alert');
      await p.waitForSelector('.alert-dlg', { timeout: 8000 });
      expect(!(await p.$('.rasad-sheet')), 'the sheet closes for the composer');
      expect(price > 0, `a level: ${price}`);
    }
    return env;
  });

  await test('rasad: the Arena — clock, start and finish, the two halves, the day recorded, the picture', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => window.__pc.useReplay.getState().select());
    await p.click('.replay-bar .rp-arena');
    await p.waitForSelector('.arena-bar', { timeout: 20000 });
    expect((await p.textContent('.arena-clock')).trim() === '5:00', await p.textContent('.arena-clock'));
    expect(await p.$('.replay-bar .rp-row'), 'the replay controls stay: they step the market');
    await p.click('.arena-go');
    await p.waitForFunction(() => document.querySelector('.arena-clock')?.textContent?.trim() === '4:59', null, { timeout: 5000 });
    // One practice trade with its automatic stop, closed by hand.
    await pc(p, () => { const r = window.__pc.useReplay.getState(); r.trade('long'); });
    await pc(p, () => { const r = window.__pc.useReplay.getState(); r.controller.step(); r.controller.step(); });
    await pc(p, () => { const r = window.__pc.useReplay.getState(); const t = r.trades.find((x) => x.exit == null); if (t) r.closeTrade(t.id); });
    await p.click('.arena-go');
    await p.waitForSelector('.arena-result');
    const halves = await p.textContent('.arena-result .arena-halves');
    expect(/\/60/.test(halves) && /\/40/.test(halves), halves);
    expect(/۱ از ۱ معامله حد ضرر داشت/.test(await p.textContent('.arena-result .arena-working')), await p.textContent('.arena-result .arena-working'));
    const rows = await p.evaluate(() => JSON.parse(localStorage.getItem('pc.arena.v1')));
    expect(rows.length === 1 && rows[0].discipline === 60, JSON.stringify(rows));
    await p.evaluate(() => { Object.defineProperty(navigator, 'canShare', { value: undefined, configurable: true }); });
    const dl = p.waitForEvent('download', { timeout: 8000 });
    await p.click('.arena-result .arena-share');
    expect((await dl).suggestedFilename() === 'arena.png', 'arena.png');
    return env;
  });

  await test('rasad: the Arena says «not scored» for five minutes without a trade', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => window.__pc.useReplay.getState().select());
    await p.click('.replay-bar .rp-arena');
    await p.waitForSelector('.arena-bar', { timeout: 20000 });
    await p.click('.arena-go');
    await p.click('.arena-go');
    await p.waitForSelector('.arena-result .arena-none');
    expect(!(await p.$('.arena-result .arena-total')), 'no zero printed');
    return env;
  });

  await test('rasad: the replay report counts exits and withholds the rates under thirty', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => { const bars = window.__pc.useChartBus.getState().bars; window.__pc.useReplay.getState().start(bars, bars[200].t, { keepView: false }); });
    await pc(p, () => { const r = window.__pc.useReplay.getState(); r.trade('long'); const t = window.__pc.useReplay.getState().trades[0]; window.__pc.useReplay.getState().closeTrade(t.id); });
    await pc(p, () => { const r = window.__pc.useReplay.getState(); r.controller.step(); window.__pc.useReplay.getState().trade('short'); window.__pc.useReplay.getState().flatten(); });
    await p.click('.replay-bar [aria-pressed]');
    await p.click('.rp-report-open');
    await p.waitForSelector('.rp-report');
    const v = async (k) => (await p.textContent(`.rp-report [data-row="${k}"] dd`)).trim();
    expect(await v('trades') === '2', await v('trades'));
    expect(await v('exit-manual') === '1' && await v('exit-session') === '1', `${await v('exit-manual')} ${await v('exit-session')}`);
    expect(await v('win-rate') === '—', await v('win-rate'));
    expect(/فقط ۲ معامله بسته شده/.test(await p.textContent('.rp-report .rp-sample')), 'the sample warning');
    expect(await p.$('.rp-report .rp-discipline'), 'the discipline line');
    return env;
  });

  await test('rasad: «بازپخش از اینجا» in the chart menu starts the replay at that bar', async () => {
    const env = await start();
    const { page: p } = env;
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.4, { button: 'right' });
    await p.click('[data-act=replay-here]');
    await p.waitForFunction(() => window.__pc.useReplay.getState().state?.on);
    const r = await pc(p, () => { const s = window.__pc.useReplay.getState(); return { at: s.startedAt, last: s.controller.full.at(-1).t, n: s.slice.length, all: s.controller.full.length }; });
    expect(r.at < r.last && r.n < r.all, JSON.stringify(r));
    return env;
  });

  await test('rasad: a drawn long position opens through the order ticket with both its lines', async () => {
    const env = await start();
    const { page: p } = env;
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.click(box.x + box.width * 0.5, box.y + box.height * 0.4, { button: 'right' });
    await p.click('[data-act=long]');
    const i = await pc(p, () => window.__pc.useChartBus.getState().drawingLayer.getDrawings().findIndex((d) => d.type === 'longshort'));
    expect(i >= 0, 'a long position drawn');
    await p.evaluate((index) => window.dispatchEvent(new CustomEvent('pc:drawing-menu', { detail: { clientX: 400, clientY: 300, index } })), i);
    await p.click('.dctx [data-act=paper-trade]');
    const req = await pc(p, () => window.__pc.useTicket.getState().req);
    const d = await pc(p, (k) => window.__pc.useChartBus.getState().drawingLayer.positionStats(window.__pc.useChartBus.getState().drawingLayer.getDrawings()[k]), i);
    expect(req && req.side === 'buy' && Math.abs(req.stopLoss - d.stop) < 1e-6 && Math.abs(req.takeProfit - d.target) < 1e-6 && req.quantity > 0, JSON.stringify(req));
    return env;
  });

  await test('rasad: a comparison drawn as «نسبت» is the compared close over this one', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => { window.__pc.useCompare.getState().add('ETHUSDT'); window.__pc.useChartSettings.getState().set({ compareMode: 'ratio' }); });
    await p.waitForFunction(() => {
      const api = window.__pc.useChartBus.getState().api;
      const lines = api.panes()[0].getSeries().filter((s) => s.seriesType() === 'Line' && s.data().length);
      return lines.some((s) => { const v = s.data().at(-1).value; return v > 0 && v < 0.2; });
    }, null, { timeout: 15000 });
    await pc(p, () => window.__pc.useChartSettings.getState().set({ compareMode: 'own' }));
    await p.waitForFunction(() => window.__pc.useChartBus.getState().api.panes()[0].getSeries().some((s) => s.seriesType() === 'Line' && s.data().length && s.data().at(-1).value > 1000), null, { timeout: 10000 });
    return env;
  });

  await test('rasad: the candle CSV carries the Solar Hijri date, the clock, provenance and the BOM', async () => {
    const env = await start();
    const { page: p } = env;
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.click(box.x + box.width * 0.4, box.y + box.height * 0.4, { button: 'right' });
    await p.hover('[data-act=more-tools]');
    await p.click('[data-act=more-tools]').catch(() => {});
    const dl = p.waitForEvent('download', { timeout: 8000 });
    await p.click('[data-act=csv]');
    const file = await dl;
    expect(/^BTCUSDT-1h-14\d\d-\d\d-\d\d\.csv$/.test(file.suggestedFilename()), file.suggestedFilename());
    const text = readFileSync(await file.path(), 'utf8');
    const rows = text.slice(1).split('\r\n');
    expect(text.charCodeAt(0) === 0xFEFF, 'BOM');
    expect(rows[0].startsWith('"# BTCUSDT · 1h · '), rows[0]);
    expect(rows[1].includes('"تاریخ (شمسی)"'), rows[1]);
    expect(/^"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ","14\d\d\/\d\d\/\d\d","\d\d:\d\d",/.test(rows[2]), rows[2]);
    return env;
  });

  await test('rasad: on a phone, dragging the symbol’s name turns to the next symbol of the list', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { width: 390, height: 844 });
    const { page: p } = env;
    await pc(p, () => window.__pc.useWatchlist.getState().setLists([{ id: 'w', name: 'W', symbols: ['BTCUSDT', 'ETHUSDT', 'SOLUSDT'] }]));
    await p.waitForTimeout(300);
    const b = await p.locator('.topbar-symbol').boundingBox();
    const ev = (type, y) => p.dispatchEvent('.topbar-symbol', type, { pointerType: 'touch', pointerId: 7, clientX: b.x + 10, clientY: y, bubbles: true, isPrimary: true });
    await ev('pointerdown', b.y + 20);
    await ev('pointermove', b.y - 10);
    await ev('pointerup', b.y - 10);
    await p.waitForFunction(() => window.__pc.usePrefs.getState().symbol === 'ETHUSDT', null, { timeout: 5000 });
    expect(!(await p.$('.sym-search, .search-dialog')), 'the turn did not open the search');
    await ev('pointerdown', b.y);
    await ev('pointermove', b.y + 30);
    await ev('pointerup', b.y + 30);
    await p.waitForFunction(() => window.__pc.usePrefs.getState().symbol === 'BTCUSDT', null, { timeout: 5000 });
    return env;
  });

  await test('rasad: a new drawing is seen just here, with this layout, or on every layout', async () => {
    const env = await start();
    const { page: p } = env;
    await p.click('.layout-name');
    await p.click('.drawing-scope [data-scope=none]');
    expect(/با ذخیره‌ی چیدمان همراهش نمی‌رود/.test(await p.textContent('.drawing-scope .ds-note')), 'the note follows the choice');
    await p.keyboard.press('Escape');
    const box = await p.locator('.chart-host').boundingBox();
    const hline = async (y) => { await p.mouse.click(box.x + box.width * 0.5, box.y + box.height * y, { button: 'right' }); await p.click('[data-act=hline]'); };
    const draw = () => pc(p, () => { const l = window.__pc.useChartBus.getState().drawingLayer; const bars = window.__pc.useChartBus.getState().bars; l.addDrawing({ type: 'trend', p0: { t: bars.at(-20).t, p: bars.at(-20).c }, p1: { t: bars.at(-5).t, p: bars.at(-5).c } }); });
    await draw();
    await pc(p, () => window.__pc.useDrawingScope.getState().set('global'));
    await draw();
    await hline(0.3);
    const st = await pc(p, () => ({
      scopes: window.__pc.useChartBus.getState().drawingLayer.getDrawings().map((d) => d.scope ?? null),
      global: JSON.parse(localStorage.getItem('pc.drawings.global.v1.BTCUSDT') ?? '[]').length,
    }));
    // The line from the menu is a new drawing too, and takes the reach set at the time.
    expect(st.scopes.join() === 'none,global,global', JSON.stringify(st));
    expect(st.global === 2, JSON.stringify(st));
    // A saved layout keeps neither the scratch nor the every-layout drawing.
    const saved = await pc(p, () => { window.__pc.useLayouts.getState().save('R'); return window.__pc.useLayouts.getState().layouts.find((l) => l.name === 'R'); });
    const kept = (saved.drawings?.BTCUSDT ?? []).map((d) => d.scope ?? null);
    expect(!kept.includes('none') && !kept.includes('global'), JSON.stringify(kept));
    return env;
  });
}
