// The event lane (OLD CoineProChart.kt drawEventMarks, core/chartevents): the news bolt and the
// calendar disc, a group drawn as a stack with the bolt above the release, a click opening the
// group's events earliest first, the notable-bar dot and its «چرا این حرکت؟» sheet, replay hiding
// future events, a pair's news filtered to macro stories, and the price-source caption.
export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const H = 3600;
  const base = () => Math.floor(Date.now() / 1000 / H) * H;
  /** The open time of the bar `k` bars before the forming one. */
  const T = (k) => base() - k * H;
  const iso = (s) => new Date(s * 1000).toISOString();

  /** A quiet BTC series with two spikes, `n` bars ending at the forming bar. */
  const quietBars = (n, spikes) => Array.from({ length: n }, (_, i) => {
    const k = n - 1 - i;
    const r = spikes.includes(k) ? 6 : 1;
    const o = 81000; const c = o + (i % 2 ? 20 : -20);
    return { t: T(k), o, h: o + (r * 81) / 2, l: o - (r * 81) / 2, c, v: 100, closed: k > 0 };
  });

  const laneCalendar = () => [
    { title: 'CPI m/m', country: 'USD', date: iso(T(5) + 600), impact: 'High', forecast: '0.3%', previous: '0.2%' },
    { title: 'PPI m/m', country: 'USD', date: iso(T(5) + 1200), impact: 'High', forecast: '0.1%', previous: '0.4%' },
    { title: 'Retail Sales', country: 'USD', date: iso(T(6) + 300), impact: 'Medium', forecast: '', previous: '' },
  ];
  const laneMock = (u) => {
    if (u.pathname === '/up/tradeyar/api/v1/public/calendar/week') return json(laneCalendar());
    if (u.pathname === '/api/news') return json({ data: [{ id: 7, slug: 'lane', source: 'e2e', titleFa: 'خبر کنار انتشار', summaryFa: 'خلاصه', publishedAt: iso(T(5) + 900), importance: 5 }] });
    return null;
  };
  /** The glyphs the lane drew: kind, x, y, importance, stacked. */
  const glyphs = (p) => pc(p, () => (document.querySelector('canvas.event-marks')?.dataset.glyphs ?? '').split(' ').filter(Boolean)
    .map((g) => { const [kind, x, y, imp, st] = g.split(':'); return { kind, x: Number(x), y: Number(y), imp, stacked: st === 'stack' }; }));
  const canvasBox = (p) => p.locator('canvas.event-marks').first().boundingBox();

  await test('events: bolt above the release, stack outline, a click lists the group earliest first', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: laneMock });
    const p = env.page;
    await ready(p);
    await pc(p, () => window.__pc.useChartSettings.getState().set({ events: true, news: true, eventImportance: 'all' }));
    await p.waitForFunction(() => (document.querySelector('canvas.event-marks')?.dataset.glyphs ?? '').includes('event:'), null, { timeout: 10000 });
    const g = await glyphs(p);
    const rel = g.find((x) => x.kind === 'event' && x.stacked);
    expect(rel, `no stacked release in ${JSON.stringify(g)}`);
    expect(rel.imp === 'medium', `the glyph stands on the group's first release (medium), got ${rel.imp}`);
    const bolt = g.find((x) => x.kind === 'news' && Math.abs(x.x - rel.x) < 0.5);
    expect(bolt && Math.abs(rel.y - bolt.y - 14) < 0.5, `bolt not one glyph above the release: ${JSON.stringify(g)}`);
    // No two glyphs on the centre line touch.
    const line = g.filter((x) => Math.abs(x.y - rel.y) < 0.5).map((x) => x.x).sort((a, b) => a - b);
    for (let i = 1; i < line.length; i++) expect(line[i] - line[i - 1] >= 14, `glyphs touch: ${line}`);
    // The lane is above the dates, inside the price pane.
    const paneH = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getHeight());
    expect(Math.abs(rel.y - (paneH - 10)) < 0.5, `lane centre ${rel.y} vs pane ${paneH}`);
    const box = await canvasBox(p);
    await p.mouse.move(box.x + rel.x, box.y + rel.y);
    await p.waitForSelector('.event-tip');
    await p.mouse.click(box.x + rel.x, box.y + rel.y);
    await p.waitForSelector('.ev-sheet.ev-group');
    const sub = await p.textContent('.ev-sheet-sub');
    expect(sub.includes('۴ رویداد در ۲ کندل کنار هم'), `subtitle ${sub}`);
    const titles = await p.$$eval('.ev-sheet .ev-title', (els) => els.map((e) => e.textContent));
    expect(JSON.stringify(titles) === JSON.stringify(['Retail Sales', 'CPI m/m', 'خبر کنار انتشار', 'PPI m/m']), `order ${titles}`);
    const head = await p.textContent('.ev-sheet h2');
    expect(head === 'رویدادهای این نقطه از چارت', `title ${head}`);
    const pills = await p.$$eval('.ev-sheet .ev-pill', (els) => els.map((e) => e.textContent));
    expect(pills.includes('اثر زیاد') && pills.includes('اثر متوسط') && pills.includes('خبر'), `pills ${pills}`);
    await p.keyboard.press('Escape');
    await p.waitForSelector('.ev-sheet', { state: 'detached' });
    return env;
  });

  await test('events: replay shows no future events; leaving it brings them back', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: laneMock });
    const p = env.page;
    await ready(p);
    await pc(p, () => window.__pc.useChartSettings.getState().set({ events: true, news: true, eventImportance: 'all' }));
    await p.waitForFunction(() => Number(document.querySelector('canvas.event-marks')?.dataset.events ?? 0) >= 2, null, { timeout: 10000 });
    await pc(p, (t) => window.__pc.useReplay.getState().start(window.__pc.useChartBus.getState().bars, t), T(8));
    await p.waitForFunction(() => window.__pc.useReplay.getState().slice != null, null, { timeout: 5000 });
    await p.waitForFunction(() => document.querySelector('canvas.event-marks')?.dataset.marks === '0', null, { timeout: 5000 })
      .catch(async () => { throw new Error(`marks in replay: ${await pc(p, () => document.querySelector('canvas.event-marks')?.dataset.glyphs)}`); });
    await pc(p, () => window.__pc.useReplay.getState().exit());
    await p.waitForFunction(() => Number(document.querySelector('canvas.event-marks')?.dataset.events ?? 0) >= 2, null, { timeout: 5000 });
    return env;
  });

  await test('events: notable-bar dot opens «چرا این حرکت؟»; no dot under a glyph', async () => {
    const n = 300;
    const mock = (u) => {
      if (u.pathname === '/api/crypto/candles') return json({ symbol: 'BTCUSDT', tf: 'H1', candles: quietBars(n, [9, 20]) });
      if (u.pathname === '/up/tradeyar/api/v1/public/calendar/week') return json([{ title: 'FOMC Statement', country: 'USD', date: iso(T(9) + 120), impact: 'High', forecast: '', previous: '' }]);
      if (u.pathname === '/api/news') return json({ data: [] });
      return null;
    };
    const env = await open('BTCUSDT/1h?e2e=1', { mock });
    const p = env.page;
    await ready(p);
    // The switches off: no glyph, so both spikes carry a dot.
    await pc(p, () => window.__pc.useChartSettings.getState().set({ events: false, news: false }));
    const dots = async () => pc(p, () => (document.querySelector('canvas.event-marks')?.dataset.dots ?? '').split(' ').filter(Boolean).map((d) => { const [i, x, y] = d.split(':').map(Number); return { i, x, y }; }));
    const idx = async (t) => pc(p, (tt) => window.__pc.useChartBus.getState().bars.findIndex((b) => b.t === tt), t);
    const i9 = await idx(T(9)); const i20 = await idx(T(20));
    await p.waitForFunction(([a, b]) => { const d = document.querySelector('canvas.event-marks')?.dataset.dots ?? ''; return d.split(' ').some((x) => x.startsWith(`${a}:`)) && d.split(' ').some((x) => x.startsWith(`${b}:`)); }, [i9, i20], { timeout: 10000 })
      .catch(async () => { throw new Error(`dots ${JSON.stringify(await dots())} want ${i9}, ${i20}`); });
    const box = await canvasBox(p);
    // The quiet spike: the honest empty answer.
    let d = (await dots()).find((x) => x.i === i20);
    await p.mouse.click(box.x + d.x, box.y + d.y);
    await p.waitForSelector('.ev-sheet.ev-notable');
    expect((await p.textContent('.ev-sheet h2')) === 'چرا این حرکت؟', 'title');
    const ratio = await p.textContent('.ev-ratio');
    expect(/^\d+\.\d برابر$/.test(ratio) && Number(ratio.split(' ')[0]) > 2, `ratio ${ratio}`);
    expect(/^\d+\.\d\d% (بالا|پایین)$/.test(await p.textContent('.ev-move')), 'open to close');
    expect((await p.textContent('.ev-nothing')).startsWith('این کندل به‌طور غیرعادی بزرگ است'), 'nothing sentence');
    await p.keyboard.press('Escape');
    await p.waitForSelector('.ev-sheet', { state: 'detached' });
    // The spike with a release inside: listed even with the calendar switched off.
    d = (await dots()).find((x) => x.i === i9);
    await p.mouse.click(box.x + d.x, box.y + d.y);
    await p.waitForSelector('.ev-sheet.ev-notable .ev-row', { timeout: 8000 });
    expect((await p.textContent('.ev-within')).includes('داخل همین کندل'), 'within label');
    expect((await p.textContent('.ev-sheet .ev-title')) === 'FOMC Statement', 'the release inside the bar');
    await p.keyboard.press('Escape');
    // The calendar on: the release's glyph takes the bar and its dot goes.
    await pc(p, () => window.__pc.useChartSettings.getState().set({ events: true, eventImportance: 'all' }));
    await p.waitForFunction((a) => { const c = document.querySelector('canvas.event-marks'); return (c?.dataset.glyphs ?? '').includes('event:') && !(c?.dataset.dots ?? '').split(' ').some((x) => x.startsWith(`${a}:`)); }, i9, { timeout: 8000 });
    expect((await dots()).some((x) => x.i === i20), 'the quiet spike keeps its dot');
    return env;
  });

  await test('events: a pair takes macro news only; gold keeps its own analysis', async () => {
    const mock = (u) => {
      if (u.pathname === '/up/coineprofx/api/academy/bn/news') {
        return json({ items: [
          { id: '1', title: 'تحلیل روزانه طلا', summary: 'سطوح مهم', published_at: iso(T(3) + 60), source: 'e2e' },
          { id: '2', title: 'تصمیم فدرال رزرو', summary: 'نرخ بهره ثابت ماند', published_at: iso(T(8) + 60), source: 'e2e' },
          { id: '3', title: 'تحلیل نقره', summary: 'در کنار طلا', published_at: iso(T(14) + 60), source: 'e2e' },
        ] });
      }
      if (u.pathname === '/up/tradeyar/api/v1/public/calendar/week') return json([]);
      return null;
    };
    const env = await open('EURUSD/1h?e2e=1', { mock });
    const p = env.page;
    await ready(p);
    // News marks are on by default, as on the phone.
    expect(await pc(p, () => window.__pc.useChartSettings.getState().news), 'news on by default');
    await p.waitForFunction(() => document.querySelector('canvas.event-marks')?.dataset.news === '1', null, { timeout: 10000 })
      .catch(async () => { throw new Error(`EURUSD news marks ${await pc(p, () => document.querySelector('canvas.event-marks')?.dataset.news)}`); });
    await pc(p, () => window.__pc.usePrefs.getState().setSymbol('XAUUSD'));
    await p.waitForFunction(() => window.__pc.usePrefs.getState().symbol === 'XAUUSD', null, { timeout: 5000 });
    await ready(p);
    await p.waitForFunction(() => document.querySelector('canvas.event-marks')?.dataset.news === '2', null, { timeout: 10000 })
      .catch(async () => { throw new Error(`XAUUSD news marks ${await pc(p, () => document.querySelector('canvas.event-marks')?.dataset.news)}`); });
    return env;
  });

  await test('events: price-source caption — one line when the venues agree, two when they differ', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const p = env.page;
    await ready(p);
    await p.waitForSelector('.legend-source');
    const crypto = await p.textContent('.legend-source');
    expect(crypto.replace(/[⁦-⁩]/g, '') === 'منبع کندل‌ها: LBank', `crypto caption ${crypto}`);
    await env.ctx.close();

    const fx = await open('XAUUSD/1h?e2e=1', {
      mock: (u) => (u.pathname === '/up/coineprofx/api/ws/snapshot'
        ? json({ prices: { XAUUSD: { symbol: 'XAUUSD', price: 4141, ts: Date.now(), source: 'finnhub' } } })
        : null),
    });
    await ready(fx.page);
    await fx.page.waitForFunction(() => (document.querySelector('.legend-source')?.textContent ?? '').includes('Finnhub'), null, { timeout: 10000 })
      .catch(async () => { throw new Error(`fx caption ${await fx.page.textContent('.legend-source').catch(() => 'none')}`); });
    const text = (await fx.page.textContent('.legend-source')).replace(/[⁦-⁩]/g, '');
    expect(text === 'منبع کندل‌ها: MetaTrader 5  ·  آخرین قیمت از Finnhub', `fx caption ${text}`);
    await pc(fx.page, () => window.__pc.usePrefs.getState().setLang('en'));
    await fx.page.waitForFunction(() => (document.querySelector('.legend-source')?.textContent ?? '').startsWith('Candles from MetaTrader 5'), null, { timeout: 5000 });
    // The price model carries the venue, and the symbol's details read it instead of a fixed name.
    const venues = await pc(fx.page, () => window.__pc.useMarket.getState().tickers.XAUUSD?.source);
    expect(venues === 'Finnhub', `ticker source ${venues}`);
    await fx.page.waitForFunction(() => (document.querySelector('.details dd[data-source]')?.textContent ?? '') === 'Candles from MetaTrader 5  ·  last price from Finnhub', null, { timeout: 5000 })
      .catch(async () => { throw new Error(`details source ${await fx.page.textContent('.details dd[data-source]').catch(() => 'none')}`); });
    return fx;
  });
}
