// Area C2 — the smaller features: «هفته‌ی من» on the journal, «دوئل با گذشته» on the replay, closing
// half a live position (always behind its confirm step), server alerts on any CoinePro-FX symbol with
// a timeframe, news impact and sentiment chips, following authors in the community, NamaScript
// barcolor and table slots, paper time in force, watchlist notes per list and the trend column, the
// archive restore that merges the journal, calendar search by country with an .ics file, and seconds
// and tick bars from CoinePro-FX's trade history. Same helpers as run.mjs.
import { readFileSync } from 'node:fs';

export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const start = async (path = 'BTCUSDT/1h?e2e=1', opts = {}) => { const env = await open(path, opts); await ready(env.page); return env; };
  const record = (page) => {
    const sent = [];
    page.on('request', (r) => {
      const u = new URL(r.url());
      if (r.method() !== 'GET' && u.pathname.startsWith('/up/')) {
        let body = null;
        try { body = r.postDataJSON(); } catch { body = r.postData(); }
        sent.push({ method: r.method(), path: u.pathname, body });
      }
    });
    return sent;
  };
  const page = (p, id, param) => pc(p, ([x, y]) => window.__pc.usePage.getState().open(x, y), [id, param]);

  await test('extras: «هفته‌ی من» counts the week, withholds the win rate under five trades, shares a picture', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => {
      const j = window.__pc.useJournal.getState();
      for (const pnl of [5, -2, 3]) j.upsert({ symbol: 'BTCUSDT', buy: true, entry: 1, exit: 2, size: 1, pnl, emotion: 'آرام', note: '', lesson: '', tags: [] });
      j.upsert({ symbol: 'ETHUSDT', buy: true, entry: 1, exit: null, size: 1, pnl: null, emotion: 'آرام', note: '', lesson: '', tags: [] });
    });
    await page(p, 'journal');
    await p.waitForSelector('.my-week .mw-row[data-row=trades]');
    expect((await p.textContent('.my-week .mw-row[data-row=trades] dd')).trim() === '۳', `trades ${await p.textContent('.my-week .mw-row[data-row=trades] dd')}`);
    const wins = (await p.textContent('.my-week .mw-row[data-row=wins] dd')).trim();
    expect(wins === '۲ از ۳' && !(await p.textContent('.my-week')).includes('%'), `under five: ${wins}`);
    expect(await p.$('.my-week .mw-floor'), 'the floor sentence');
    await pc(p, () => { const j = window.__pc.useJournal.getState(); j.upsert({ symbol: 'X', buy: true, entry: 1, exit: 2, size: 1, pnl: 1, emotion: 'آرام', note: '', lesson: '', tags: [] }); j.upsert({ symbol: 'Y', buy: true, entry: 1, exit: 2, size: 1, pnl: 1, emotion: 'آرام', note: '', lesson: '', tags: [] }); });
    await p.waitForFunction(() => document.querySelector('.my-week .mw-row[data-row=wins] dd')?.textContent?.trim() === '80%');
    expect(!(await p.$('.my-week .mw-floor')), 'no floor sentence at five');
    await p.evaluate(() => { Object.defineProperty(navigator, 'canShare', { value: undefined, configurable: true }); });
    const dl = p.waitForEvent('download', { timeout: 8000 });
    await p.click('.my-week .mw-share');
    expect((await dl).suggestedFilename() === 'week.png', 'week.png');
    // The journal's statistics: no win rate under five trades either.
    await p.evaluate(() => { const j = window.__pc.useJournal.getState(); for (const e of j.entries.slice(0, 2)) j.remove(e.id); });
    await p.click('.page-tabs >> text=آمار');
    await p.waitForSelector('.pulse [data-stat=win]');
    expect((await p.textContent('.pulse [data-stat=win] b')).trim() === '۲ از ۳', await p.textContent('.pulse [data-stat=win]'));
    return env;
  });

  await test('extras: «دوئل با گذشته» hides the next 20 bars, judges the call once a day, keeps no rate under five', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => window.__pc.useWatchlist.getState().setLists([{ id: 'd', name: 'D', symbols: ['BTCUSDT'] }]));
    await pc(p, () => window.__pc.useReplay.getState().select());
    await p.click('.replay-bar .rp-duel');
    await p.waitForSelector('.duel-bar [data-call=up]', { timeout: 10000 });
    const n0 = await pc(p, () => window.__pc.useReplay.getState().slice.length);
    expect(n0 === 121, `120 bars behind the moment and the moment: ${n0}`);
    expect(!(await p.$('.replay-bar .rp-row')), 'no replay controls during the round');
    const box = await p.locator('.chart-host').boundingBox();
    await p.mouse.click(box.x + box.width * 0.3, box.y + box.height * 0.5);
    expect(await pc(p, () => window.__pc.useReplay.getState().slice.length) === 121, 'a click on a revealed bar does not move the round');
    await p.keyboard.press('Shift+ArrowRight');
    const n1 = await pc(p, () => window.__pc.useReplay.getState().slice.length);
    expect(n1 === 121, `stepping is blocked: ${n1}`);
    await p.click('.duel-bar [data-call=up]');
    await p.waitForSelector('.duel-bar .duel-verdict');
    expect(await pc(p, () => window.__pc.useReplay.getState().slice.length) === 141, 'the 20 bars revealed');
    const t1 = await pc(p, () => window.__pc.useDuel.getState().tally);
    expect(t1.played === 1, JSON.stringify(t1));
    expect(/از ۵ دور/.test(await p.textContent('.duel-bar .duel-record')), await p.textContent('.duel-bar .duel-record'));
    expect(!(await p.$('.duel-bar [data-call]')), 'no second call');
    await p.click('.duel-bar .duel-finish');
    await p.waitForFunction(() => !window.__pc.useReplay.getState().state?.on);
    // Again the same day: played, not recorded, and said so.
    await pc(p, () => window.__pc.useReplay.getState().select());
    await p.click('.replay-bar .rp-duel');
    await p.click('.duel-bar [data-call=down]', { timeout: 10000 });
    await p.waitForSelector('.duel-bar .duel-verdict');
    expect(/قبلاً/.test(await p.textContent('.duel-bar .duel-record')), 'already answered today');
    expect(await pc(p, () => window.__pc.useDuel.getState().tally.played) === 1, 'not recorded twice');
    return env;
  });

  await test('extras: close half a live position asks first, then sends half rounded to the step', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { signed: true });
    const { page: p } = env;
    const sent = record(p);
    await p.waitForFunction(() => window.__pc.useTrading.getState().books.tradeyar.status === 'live', null, { timeout: 10000 });
    await pc(p, () => window.__pc.usePanels.getState().setBottom('account'));
    await p.waitForSelector('.acct-table tbody tr .acct-half');
    await p.click('.acct-table tbody tr .acct-half');
    await p.waitForSelector('.acct-ask-half');
    await p.waitForTimeout(400);
    expect(!sent.some((x) => x.path.endsWith('/trade/positions/close')), 'nothing sent before the confirm');
    await p.click('.acct-ask-half .btn.sell');
    await p.waitForFunction(() => true);
    for (let i = 0; i < 30 && !sent.some((x) => x.path.endsWith('/trade/positions/close')); i++) await p.waitForTimeout(100);
    const close = sent.find((x) => x.path.endsWith('/trade/positions/close'));
    expect(close && close.body.quantity === 0.005 && close.body.side === 'long', JSON.stringify(close));
    // On the chart: ½ on the position's line, the same confirm.
    const c = await pc(p, () => { const b = window.__pc.useChartBus.getState().bars; return b[b.length - 1].c; });
    await p.route('**/up/tradeyar/api/mobile/v1/trade/positions*', (r) => r.fulfill(json({ items: [{ symbol: 'BTCUSDT', side: 'long', quantity: 0.02, entry_price: Number((c * 0.999).toFixed(1)), mark_price: c, unrealized_pnl: 1, leverage: 5 }] })));
    await pc(p, () => window.__pc.useTrading.getState().refresh('tradeyar', 'all'));
    const half = await p.waitForSelector('.ltl[data-key="pos:long"] .ltl-half', { state: 'visible', timeout: 8000 });
    const before = sent.length;
    await half.click();
    await p.waitForSelector('.ltl[data-key="pos:long"] .ltl-ask');
    expect(/0\.01/.test(await p.textContent('.ltl[data-key="pos:long"] .ltl-ask')), await p.textContent('.ltl[data-key="pos:long"] .ltl-ask'));
    await p.waitForTimeout(300);
    expect(sent.length === before, 'nothing sent before ✓');
    await p.click('.ltl[data-key="pos:long"] .ltl-ok');
    for (let i = 0; i < 30 && sent.length === before; i++) await p.waitForTimeout(100);
    const second = sent.slice(before).find((x) => x.path.endsWith('/trade/positions/close'));
    expect(second && second.body.quantity === 0.01, JSON.stringify(second));
    return env;
  });

  await test('extras: a server alert on any CoinePro-FX symbol, with the move’s timeframe', async () => {
    const env = await start('UK100/1h?e2e=1', { signed: true });
    const { page: p } = env;
    const sent = record(p);
    await page(p, 'alerts');
    await p.waitForSelector('.page-inner .table >> text=XAUUSD', { timeout: 10000 });
    await p.click('.page-tabs >> text=هشدار تازه');
    await p.waitForSelector('.page-inner form.form', { timeout: 5000 });
    await p.click('.page-tabs >> text=حرکت درصدی', { timeout: 8000 });
    await p.selectOption('select.alert-tf', 'H4', { timeout: 5000 }).catch(async (e) => { throw new Error(`${e.message.slice(0, 80)} ${(await p.textContent('.page-inner')).slice(0, 300)}`); });
    await p.click('.form .btn-primary');
    for (let i = 0; i < 40 && !sent.some((x) => x.path === '/up/coineprofx/api/user/mobile/alerts'); i++) await p.waitForTimeout(100);
    const post = sent.find((x) => x.path === '/up/coineprofx/api/user/mobile/alerts');
    expect(post && post.body.symbol === 'UK100' && post.body.spec?.kind === 'move' && post.body.spec.tf === 'H4', JSON.stringify(post));
    return env;
  });

  await test('extras: news shows the feed’s impact and sentiment, and filters by them', async () => {
    const iso = new Date().toISOString();
    const env = await start('BTCUSDT/1h?e2e=1', { mock: (u) => {
      if (u.pathname === '/api/news') return json({ data: [
        { id: 1, slug: 'a', source: 'e2e', titleFa: 'خبر مهم بیت‌کوین', summaryFa: 'خلاصه', publishedAt: iso, importance: 8, sentiment: 'bullish' },
        { id: 2, slug: 'b', source: 'e2e', titleFa: 'خبر معمولی اتریوم', summaryFa: 'خلاصه', publishedAt: iso, importance: 2, sentiment: 'bearish' },
      ] });
      return null;
    } });
    const { page: p } = env;
    await pc(p, () => { const s = window.__pc.usePanels.getState(); if (s.open !== 'news') s.toggle('news'); });
    await p.waitForSelector('.news-row .news-chip[data-impact=high]', { timeout: 10000 });
    expect(await p.$('.news-row .news-chip[data-sentiment=bearish]'), 'sentiment chip');
    await p.click('.news-filters [data-filter=high]');
    await p.waitForFunction(() => document.querySelectorAll('.news-row').length === 1);
    await p.click('.news-filters [data-filter=bearish]');
    await p.waitForSelector('.news-none');
    await p.click('.news-none .link-btn');
    await p.waitForFunction(() => document.querySelectorAll('.news-row').length === 2);
    return env;
  });

  await test('extras: community — follow an author, the Following chip narrows the board', async () => {
    const env = await start();
    const { page: p } = env;
    await page(p, 'community');
    await p.waitForSelector('.post .follow-btn[data-author=ali]', { timeout: 10000 });
    await p.click('.following-chip');
    await p.waitForSelector('.following-empty');
    await p.click('.following-empty .btn');
    await p.click('.post .follow-btn[data-author=ali]');
    expect(await pc(p, () => JSON.parse(localStorage.getItem('pc.community.follows.v1'))[0]) === 'ali', 'kept');
    await p.click('.following-chip');
    await p.waitForSelector('.post .follow-btn[aria-pressed=true]');
    expect(!(await p.$('.following-empty')), 'the followed author’s post shows');
    return env;
  });

  await test('extras: NamaScript barcolor recolours the bars and table.new takes Pine’s slot', async () => {
    const env = await start();
    const { page: p } = env;
    const series0 = await pc(p, () => window.__pc.useChartBus.getState().api.panes()[0].getSeries().length);
    const code = 'barcolor(color.orange, gt(close, open))\nt = table.new(position.bottom_left)\ntable.cell(t, 0, 0, "hi")\nplot(close)';
    await pc(p, (c) => { const s = window.__pc.useScript.getState(); s.setSource(c); s.apply(); }, code);
    await p.waitForSelector('.ns-slot[data-slot=bottom_left] .ns-table td >> text=hi', { timeout: 15000 });
    const r = await pc(p, () => window.__pc.useScript.getState().result);
    expect(r.ok && r.barcolors.length === 1 && r.barcolors[0].bars.length > 10 && r.tables[0].position === 'bottom_left', JSON.stringify({ ok: r.ok, bc: r.barcolors?.length, pos: r.tables?.[0]?.position }));
    await p.waitForFunction((n) => window.__pc.useChartBus.getState().api.panes()[0].getSeries().length > n, series0 + 1, { timeout: 8000 });
    // Bar by bar: a colour per bar, the last call on a bar winning.
    await pc(p, () => { const s = window.__pc.useScript.getState(); s.setBarMode(true); s.setSource('barcolor(color.red)\nif (close > open) barcolor(color.green)\nplot(close)'); s.apply(); });
    await p.waitForFunction(() => { const s = window.__pc.useScript.getState(); return s.barMode && !s.running && s.result?.ok && s.result.barcolors?.length === 2; }, null, { timeout: 15000 }).catch(async (e) => { throw new Error(`${e.message} ${JSON.stringify(await pc(p, () => { const s = window.__pc.useScript.getState(); return { bm: s.barMode, run: s.running, ok: s.result?.ok, err: s.result?.error, bc: s.result?.barcolors }; }))}`.slice(0, 600)); });
    const groups = await pc(p, () => window.__pc.useScript.getState().result.barcolors.map((g) => g.bars.length));
    const n = await pc(p, () => window.__pc.useChartBus.getState().bars.length);
    expect(groups[0] + groups[1] === n, `${groups} of ${n}`);
    await pc(p, () => window.__pc.useScript.getState().setBarMode(false));
    return env;
  });

  await test('extras: paper limit time in force — IOC away from the market is cancelled, GTC rests', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => window.__pc.usePanels.getState().setBottom('paper'));
    await p.waitForSelector('.paper-ticket .buy');
    await p.waitForFunction(() => !document.querySelector('.paper-ticket .buy').disabled, null, { timeout: 10000 });
    await p.selectOption('.pp-ticket select', 'limit');
    const last = await pc(p, () => window.__pc.useMarket.getState().tickers.BTCUSDT.last);
    await p.fill('.pp-ticket .pp-f:has-text("لیمیت") input', String(Math.round(last * 0.9)));
    await p.click('.pp-tif [data-tif=ioc]');
    await p.click('.paper-ticket .buy');
    await p.waitForSelector('.pp-err >> text=بی‌درنگ پر نشد و لغو شد');
    expect(await pc(p, () => window.__pc.usePaper.getState().orders.length) === 0, 'no order left');
    await p.click('.pp-tif [data-tif=gtc]');
    await p.click('.paper-ticket .buy');
    await p.waitForFunction(() => window.__pc.usePaper.getState().orders[0]?.tif === 'gtc');
    await p.waitForSelector('.pp-tif-tag >> text=GTC');
    return env;
  });

  await test('extras: watchlist notes per list (old notes kept) and the trend column', async () => {
    const env = await start('BTCUSDT/1h?e2e=1', { setup: (ctx) => ctx.addInitScript(() => {
      if (!sessionStorage.getItem('seeded')) {
        sessionStorage.setItem('seeded', '1');
        localStorage.setItem('pc.watchlists.v2', JSON.stringify({ lists: [{ id: 'a', name: 'A', symbols: ['BTCUSDT', 'ETHUSDT'] }, { id: 'b', name: 'B', symbols: ['BTCUSDT'] }], active: 'a', flags: {}, notes: { BTCUSDT: 'old note' } }));
      }
    }) });
    const { page: p } = env;
    const st = await pc(p, () => window.__pc.useWatchlist.getState().listNotes);
    expect(st.a?.BTCUSDT === 'old note' && st.b?.BTCUSDT === 'old note', JSON.stringify(st));
    await pc(p, () => window.__pc.useWatchlist.getState().note('BTCUSDT', 'only in A'));
    await pc(p, () => window.__pc.useWatchlist.getState().setActive('b'));
    expect(await pc(p, () => window.__pc.useWatchlist.getState().notes.BTCUSDT) === 'old note', 'list B keeps its own');
    await pc(p, () => { const s = window.__pc.usePanels.getState(); if (s.open !== 'watchlist') s.toggle('watchlist'); window.__pc.useWatchView.getState().setView({ table: true }); window.__pc.useWatchView.getState().toggleColumn('spark'); });
    await p.waitForSelector('.wl-row[data-symbol=BTCUSDT] .wl-spark[data-spark]:not([data-spark=none])', { timeout: 15000 });
    expect(Number(await p.getAttribute('.wl-row[data-symbol=BTCUSDT] .wl-spark', 'data-spark')) >= 2, 'a line');
    return env;
  });

  await test('extras: archive restore merges the journal instead of overwriting it', async () => {
    const env = await start();
    const { page: p } = env;
    await pc(p, () => window.__pc.useJournal.getState().upsert({ id: 'mine', symbol: 'BTCUSDT', buy: true, entry: 1, exit: 2, size: 1, pnl: 1, emotion: 'آرام', note: '', lesson: '', tags: [] }));
    await page(p, 'profile');
    await p.waitForSelector('button >> text=بازگردانی');
    const archive = { 'pc.journal.v1': JSON.stringify([{ id: 'theirs', symbol: 'ETHUSDT', buy: true, entry: 1, exit: 2, size: 1, pnl: 2, emotion: 'آرام', note: '', lesson: '', tags: [], createdAt: 1 }]) };
    const chooser = p.waitForEvent('filechooser');
    await p.click('button >> text=بازگردانی');
    await (await chooser).setFiles({ name: 'archive.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(archive)) });
    await p.waitForFunction(() => JSON.parse(localStorage.getItem('pc.journal.v1') ?? '[]').length === 2, null, { timeout: 5000 });
    const ids = await pc(p, () => JSON.parse(localStorage.getItem('pc.journal.v1')).map((e) => e.id).sort());
    expect(ids.join() === 'mine,theirs', ids.join());
    await p.waitForTimeout(1200); // the page reloads after a restore
    return env;
  });

  await test('extras: calendar search finds a release by country; «افزودن به تقویم» saves an .ics', async () => {
    const env = await start();
    const { page: p } = env;
    await page(p, 'calendar');
    await p.waitForSelector('.cal-row.wide');
    await p.fill('.scr-search', 'آمریکا');
    await p.waitForSelector('.cal-row.wide >> text=CPI m/m');
    await p.fill('.scr-search', 'japan');
    await p.waitForFunction(() => !document.querySelector('.cal-row.wide'));
    await p.fill('.scr-search', '');
    const dl = p.waitForEvent('download', { timeout: 5000 });
    await p.click('.cal-row.wide .cal-ics');
    const file = await dl;
    expect(file.suggestedFilename() === 'event.ics', file.suggestedFilename());
    const text = readFileSync(await file.path(), 'utf8');
    expect(/BEGIN:VEVENT[\s\S]*SUMMARY:CPI m\/m/.test(text), text.slice(0, 200));
    return env;
  });

  await test('extras: seconds and tick bars from CoinePro-FX’s trade history', async () => {
    const seen = [];
    const now = Math.floor(Date.now() / 1000);
    const env = await start('BTCUSDT/10S?e2e=1', { mock: (u) => {
      if (u.pathname === '/up/coineprofx/api/public/market/seconds') {
        seen.push(`seconds:${u.searchParams.get('seconds')}`);
        const s = Number(u.searchParams.get('seconds'));
        const top = Math.floor(now / s) * s;
        return json({ symbol: 'BTCUSDT', seconds: s, source: 'binance', candles: Array.from({ length: 300 }, (_, i) => { const p = 81000 + Math.sin(i / 7) * 50; return { t: top - (299 - i) * s, o: p, h: p + 5, l: p - 5, c: p + 1, v: 1 }; }) });
      }
      if (u.pathname === '/up/coineprofx/api/public/market/ticks') {
        seen.push('ticks');
        return json({ symbol: 'BTCUSDT', source: 'binance', ticks: Array.from({ length: 3000 }, (_, i) => ({ t: (now - 3000 + i) * 1000, p: 81000 + (i % 50), v: 0.01, s: 'buy' })) });
      }
      return null;
    } });
    const { page: p } = env;
    expect(seen.includes('seconds:10'), seen.join());
    const n = await pc(p, () => window.__pc.useChartBus.getState().bars.length);
    expect(n >= 290, `seconds bars ${n}`);
    await pc(p, () => window.__pc.usePrefs.getState().setInterval('100T'));
    await p.waitForURL(/\/next\/BTCUSDT\/100T/);
    await p.waitForFunction(() => window.__pc.useChartBus.getState().bars.length === 30, null, { timeout: 10000 });
    expect(seen.includes('ticks'), seen.join());
    await p.click('.iv-caret');
    await p.waitForSelector('.iv-menu .iv-head >> text=تیک');
    await p.waitForSelector('.iv-menu .iv-row >> text=۱۰۰ تیک');
    return env;
  });
}
