// Area P — records and reports: the portfolio's full report (curve with the deepest fall and best
// climb, the metric set, each symbol's share, the Solar Hijri month grid, CSV and a real .xlsx), the
// paper account's expected fill, fill log, record by symbol and month, «شروع دوباره» kept apart from
// «پاک کردن همه‌چیز», the journal's outcome and screenshot filters with profit factor and expectancy,
// the signal record's evidence and filters with the cancel of a queued execution, and the backtest's
// small-sample sentence and Excel file. Same helpers as run.mjs.
import { readFileSync } from 'node:fs';

export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const NOW = Math.floor(Date.now() / 1000);
  const iso = (offsetSec = 0) => new Date((NOW + offsetSec) * 1000).toISOString();
  const start = async (opts = {}) => { const env = await open('BTCUSDT/1h?e2e=1', opts); await ready(env.page); return env; };
  const page = (p, id, param) => pc(p, ([x, y]) => window.__pc.usePage.getState().open(x, y), [id, param]);
  const zipOk = (path) => { const b = readFileSync(path); return b[0] === 0x50 && b[1] === 0x4b && b.includes(Buffer.from('xl/worksheets/sheet1.xml')); };

  await test('records: the portfolio\'s full report — curve, spans, metrics, month grid, CSV and Excel', async () => {
    const day = 86400;
    const trades = [[-30, 40], [120, 30], [-80, 20], [-60, 12], [200, 5], [15, 1]].map(([pnl, ago], i) => ({ id: i + 1, symbol: i % 2 ? 'ETHUSDT' : 'BTCUSDT', side: 'buy', opened_at: NOW - ago * day - 7200, closed_at: NOW - ago * day, entry: 100, exit: 100 + pnl, quantity: 1, pnl, fee: 0.5, currency: 'USDT' }));
    const env = await start({ signed: true, mock: (u) => (u.pathname === '/up/tradeyar/api/mobile/v1/portfolio/history' ? json({ trades, total: trades.length }) : null) });
    const p = env.page;
    await page(p, 'portfolio');
    await p.click('.page-tabs [role=tab] >> text=گزارش کامل');
    await p.waitForSelector('.rp-report [data-part=metrics]');
    expect(await p.$('.rp-curve .rp-line'), 'the curve');
    expect(await p.$('.rp-curve .rp-band.down') && await p.$('.rp-curve .rp-band.up'), 'the fall and the climb are banded');
    const curve = await p.textContent('[data-part=curve]');
    expect(curve.includes('سود انباشته') && curve.includes('بیشترین افت') && curve.includes('بیشترین رشد پیوسته'), curve);
    expect(/از [۰-۹]+ \S+ تا [۰-۹]+ \S+/.test(curve), `Jalali span with Persian digits: ${curve}`);
    const metrics = await p.textContent('[data-part=metrics]');
    for (const k of ['نرخ برد', 'ضریب سود', 'شارپ', 'سورتینو', 'میانگین مدت نگهداری', 'بیشترین بردهای پیاپی', 'طولانی‌ترین دوره‌ی افت']) expect(metrics.includes(k), `metric ${k}`);
    // 3 wins of 6: 50.0%; gross 335 / 170.
    expect(metrics.includes('50.0%') && metrics.includes((335 / 170).toFixed(2)), metrics);
    expect(metrics.includes('۲ ساعت'), 'holding time in Persian prose');
    expect(!/[۰-۹]/.test(await p.textContent('[data-part=metrics] .rp-metric b >> nth=1')), 'figures stay Latin');
    const grid = await p.textContent('[data-part=months]');
    expect(grid.includes('کل سال') && /فروردین|اردیبهشت|مهر|آبان|آذر|دی/.test(grid), grid);
    expect((await p.$$('[data-part=attribution] .rp-attr')).length === 2, 'two symbols');
    const [csv] = await Promise.all([p.waitForEvent('download'), p.click('[data-part=export] [data-export=csv]')]);
    expect(/^coinepro-trades-\d{4}-\d{2}-\d{2}\.csv$/.test(csv.suggestedFilename()), csv.suggestedFilename());
    const text = readFileSync(await csv.path(), 'utf8');
    expect(text.startsWith('﻿"شناسه"') && text.includes('"تاریخ بسته شدن (شمسی)"') && /"14\d\d\/\d\d\/\d\d"/.test(text), text.slice(0, 300));
    const [xlsx] = await Promise.all([p.waitForEvent('download'), p.click('[data-part=export] [data-export=xlsx]')]);
    expect(xlsx.suggestedFilename().endsWith('.xlsx') && zipOk(await xlsx.path()), 'a real workbook');
    // On a phone the month grid scrolls inside its card; the page does not.
    await p.setViewportSize({ width: 390, height: 844 });
    await p.waitForTimeout(200);
    expect(!(await p.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)), 'no horizontal overflow on a phone');
    return env;
  });

  await test('records: paper — expected fill, fill log, record by symbol and month, start again keeps history, erase takes it', async () => {
    const env = await start();
    const p = env.page;
    await pc(p, () => {
      const b = window.__pc.usePaper.getState();
      const a = b.open('BTCUSDT', 'long', 0.01, 80000); b.close(a, 81000);
      const c = b.open('ETHUSDT', 'short', 1, 2400); b.close(c, 2450);
      b.place({ symbol: 'SOLUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 95 }, 100);
      b.mark('SOLUSDT', 97); b.mark('SOLUSDT', 94);
    });
    await page(p, 'paper');
    await p.waitForSelector('.paper-page .ticket');
    await p.fill('.paper-page .ticket input >> nth=0', '0.01');
    await p.waitForSelector('[data-part=fill-preview]');
    const prev = await p.textContent('[data-part=fill-preview]');
    expect(prev.includes('برآورد پر شدن') && prev.includes('هزینه‌ی اسپرد') && prev.includes('لغزش') && prev.includes('مارجین لازم') && prev.includes('اسپرد فرضی بود'), prev);
    await p.click('.paper-page .page-tabs [role=tab] >> text=تاریخچه');
    await p.waitForSelector('[data-part=fills] .pr-fill');
    const log = await p.textContent('[data-part=fills]');
    expect(log.includes('از بازار گرفته شد') && log.includes('در قیمت خودش پر شد') && log.includes('قیمت آخر در آن لحظه'), log.slice(0, 400));
    expect((await p.$$('[data-part=fills] .pr-fill')).length === 5, 'five fills');
    const [x] = await Promise.all([p.waitForEvent('download'), p.click('[data-part=export] [data-export=xlsx]')]);
    expect(zipOk(await x.path()), 'paper workbook');
    await p.click('.paper-page .page-tabs [role=tab] >> text=کارنامه');
    await p.waitForSelector('[data-part=record]');
    const rec = await p.textContent('[data-part=record]');
    expect(rec.includes('۲ معامله‌ی بسته‌شده') && rec.includes('امید ریاضی') && rec.includes('برد به باخت'), rec);
    expect((await p.$$('[data-part=by-symbol] .rp-metric')).length === 2 && await p.$('[data-part=by-month] .pr-month'), 'by symbol and by month');
    // Worst first.
    expect((await p.textContent('[data-part=by-symbol] .rp-metric >> nth=0')).includes('ETHUSDT'), 'worst first');
    await p.click('.paper-page .page-tabs [role=tab] >> text=قوانین حساب');
    await p.waitForSelector('[data-part=fill-rules]');
    p.once('dialog', (d) => d.accept());
    await p.click('[data-act=reset]');
    await p.waitForFunction(() => window.__pc.usePaper.getState().positions.length === 0);
    expect((await pc(p, () => window.__pc.usePaper.getState().history.length)) === 2, 'start again keeps the history');
    p.once('dialog', (d) => d.accept());
    await p.click('[data-act=wipe]');
    await p.waitForFunction(() => window.__pc.usePaper.getState().history.length === 0 && window.__pc.usePaper.getState().fills.length === 0);
    return env;
  });

  await test('records: journal outcome and screenshot filters, profit factor and expectancy over what is shown', async () => {
    const env = await start();
    const p = env.page;
    await pc(p, () => {
      const j = window.__pc.useJournal.getState();
      const add = (symbol, pnl, extra = {}) => j.upsert({ symbol, buy: true, entry: 1, exit: 2, size: 1, pnl, emotion: 'آرام', note: '', lesson: '', tags: [], ...extra });
      add('BTCUSDT', 40, { screenshot: 'data:image/png;base64,iVBORw0KGgo=' }); add('BTCUSDT', -10); add('ETHUSDT', 20); add('SOLUSDT', null, { note: 'قیمت ۶۴۰۰۰', exit: null });
    });
    await page(p, 'journal');
    await p.waitForSelector('[data-part=journal-stats] [data-stat=pf]');
    expect((await p.textContent('[data-part=journal-stats] [data-stat=pf] b')).trim() === '6.00', await p.textContent('[data-part=journal-stats]'));
    expect((await p.textContent('[data-part=journal-stats] [data-stat=expectancy] b')).includes('16.67'), 'expectancy 50/3');
    const cards = () => p.$$eval('.journal-card', (els) => els.length);
    await p.click('[data-outcome=wins]');
    expect((await cards()) === 2, 'wins');
    expect((await p.textContent('[data-part=journal-stats] .rp-metric >> nth=0')).includes('۲ از ۴'), 'shown of total');
    await p.click('[data-outcome=losses]');
    expect((await cards()) === 1, 'losses');
    await p.click('[data-outcome=ungraded]');
    expect((await cards()) === 1, 'no P&L');
    await p.click('[data-outcome=any]');
    await p.click('[data-shot=with]');
    expect((await cards()) === 1, 'with screenshot');
    await p.click('[data-shot=with]');
    await p.click('[data-shot=without]');
    expect((await cards()) === 3, 'without screenshot');
    await p.click('[data-shot=without]');
    await p.fill('[data-part=journal-filter] .scr-search', '64000');
    expect((await cards()) === 1, 'search folds Persian digits');
    return env;
  });

  await test('records: signal evidence — rates over their own samples, market / instrument / result filters, cancel a queued execution', async () => {
    const closed = [
      { id: 31, symbol: 'BTCUSDT', direction: 'BUY', timeframe: '1h', status: 'closed', result: { pnl_usd: 25 }, targets: [{ level: 1, hit: true }], close_reason: 'TP1', risk_reward_tp1: 2, closed_at: iso(-3600) },
      { id: 32, symbol: 'BTCUSDT', direction: 'SELL', timeframe: '4h', status: 'closed', result: { pnl_usd: -10 }, targets: [{ level: 1, hit: false }], close_reason: 'SL', risk_reward_tp1: 1, closed_at: iso(-7200) },
      { id: 33, symbol: 'ETHUSDT', direction: 'BUY', timeframe: '1h', status: 'closed', targets: [{ level: 1 }], closed_at: iso(-9000) },
    ];
    const closes = [];
    const env = await start({ signed: true, mock: (u, method) => {
      if (u.pathname === '/up/tradeyar/api/mobile/v1/signals' && u.searchParams.get('status') === 'closed') return json({ items: closed });
      if (u.pathname === '/up/coineprofx/api/public/signals/recent') return json({ items: [{ id: 41, symbol: 'XAUUSD', direction: 'BUY', timeframe: '1h', status: 'closed', market: 'forex', result: { pnl_usd: 0 }, targets: [], close_reason: 'manual' }] });
      if (u.pathname === '/up/tradeyar/api/mobile/v1/executions' && method === 'GET') return json({ items: [{ id: 'q1', signal_id: 31, venue: 'lbank', product: 'futures', status: 'queued', side: 'buy', quantity: '0.01', signal: { symbol: 'BTCUSDT', direction: 'BUY', timeframe: '1h' }, created_at: iso(-60) }, { id: 'o1', signal_id: 32, venue: 'lbank', product: 'futures', status: 'open', side: 'sell', quantity: '0.02', signal: { symbol: 'BTCUSDT', direction: 'SELL', timeframe: '4h' }, created_at: iso(-600) }] });
      if (/\/executions\/q1\/close$/.test(u.pathname)) { closes.push(u.pathname); return json({ execution: { id: 'q1', status: 'cancelled' } }); }
      return null;
    } });
    const p = env.page;
    await page(p, 'activity');
    await p.click('.page-tabs [role=tab] >> text=اجراهای سیگنال');
    await p.waitForSelector('[data-status=queued]');
    expect((await p.textContent('[data-status=queued]')).trim() === 'در صف', 'status in Persian');
    expect((await p.$$('[data-act=cancel]')).length === 1 && (await p.$$('[data-act=close]')).length === 0, 'cancel only on the queued one; LBank open has no close');
    expect((await p.textContent('[data-act=cancel]')).includes('لغو درخواستِ در صف'), 'cancel label');
    p.once('dialog', (d) => d.accept());
    await p.click('[data-act=cancel]');
    for (let i = 0; i < 20 && !closes.length; i++) await p.waitForTimeout(100);
    expect(closes.length === 1, 'the queued request was cancelled through the close route');
    await p.click('.page-tabs [role=tab] >> text=کارنامه‌ی سیگنال‌ها');
    await p.waitForSelector('[data-part=performance]');
    const metric = async (id) => (await p.textContent(`[data-metric=${id}] .bt-v`)).trim();
    expect(await metric('signals') === '4', `signals ${await metric('signals')}`);
    expect(await metric('win') === '33.3%', `win ${await metric('win')}`);
    expect(await metric('tp') === '50.0%' && await metric('sl') === '33.3%' && await metric('rr') === '1:1.50', `${await metric('tp')} ${await metric('sl')} ${await metric('rr')}`);
    expect((await p.textContent('[data-metric=win] small')).includes('۳ نمونه'), 'sample size in Persian digits');
    await p.click('[data-market=forex]');
    expect(await metric('signals') === '1', 'forex only');
    await p.click('[data-market=forex]');
    await p.fill('[data-part=activity-filters] .scr-search', 'btcusdt');
    expect(await metric('signals') === '2', 'instrument');
    await p.click('[data-result=loss]');
    expect(await metric('signals') === '1' && await metric('sl') === '100.0%', 'result loss');
    await p.click('[data-result=unknown]');
    await p.waitForSelector('.empty >> text=رکوردی با این فیلترها نیست');
    await p.click('.empty .link');
    expect(await metric('signals') === '4', 'filters cleared');
    return env;
  });

  await test('records: backtest — the small-sample sentence under thirty trades, CSV and a real Excel file', async () => {
    const env = await start();
    const p = env.page;
    await page(p, 'backtest');
    await p.waitForSelector('.backtest-page form');
    await p.selectOption('.backtest-page form select >> nth=1', 'breakout');
    await p.click('.backtest-page form .btn-primary');
    await p.waitForSelector('[data-part=bt-export]', { timeout: 20000 });
    const n = await p.$$eval('.backtest-page .grow tbody tr', (r) => r.length);
    const warn = await p.$('[data-part=thin-sample]');
    expect(n < 30 ? Boolean(warn) : !warn, `trades ${n}, warning ${Boolean(warn)}`);
    if (warn) expect(/فقط [۰-۹]+ معامله بسته شد\. زیر ۳۰ معامله/.test(await warn.textContent()), await warn.textContent());
    const [csv] = await Promise.all([p.waitForEvent('download'), p.click('[data-part=bt-export] [data-export=csv]')]);
    const text = readFileSync(await csv.path(), 'utf8');
    expect(csv.suggestedFilename() === 'coinepro-backtest-btcusdt.csv' && text.startsWith('﻿"گزارش بک‌تست","BTCUSDT"') && text.includes('"موجودی پس از معامله"'), text.slice(0, 200));
    const [x] = await Promise.all([p.waitForEvent('download'), p.click('[data-part=bt-export] [data-export=xlsx]')]);
    expect(x.suggestedFilename() === 'coinepro-backtest-btcusdt.xlsx' && zipOk(await x.path()), 'backtest workbook');
    return env;
  });
}
