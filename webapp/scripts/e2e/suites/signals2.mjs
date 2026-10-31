// The signal layer — «a chart that explains itself» (OLD feature/chart ChartSignalStrip.kt,
// ExplainSheet.kt, ChartSignals.kt, chart/core SignalSpec.kt, ConfidenceEngine.kt, SignalMarkers.kt,
// RepaintClaim.kt, ChartProvenance.kt): the Now strip under the plot, the Explain sheet and its
// actions, the marks on the candles and their setting, the repaint badge, the provenance line
// (beside the venue caption in the legend, the web's own provenance line).
export default async function ({ test, open, ready, pc, expect, fixtures }) {
  const { json } = fixtures;
  const STEP = { M1: 60, M5: 300, M15: 900, M30: 1800, H1: 3600, H4: 14400, D1: 86400, W1: 604800 };
  /** A swinging series (RSI and MACD fire often), `n` bars of `step` ending at the forming bar. */
  const wave = (n, step, volume = true) => {
    const end = Math.floor(Date.now() / 1000 / step) * step;
    return Array.from({ length: n }, (_, i) => {
      const c = 81000 * (1 + Math.sin(i / 6) * 0.03 + Math.sin(i / 17) * 0.02);
      const o = 81000 * (1 + Math.sin((i - 1) / 6) * 0.03 + Math.sin((i - 1) / 17) * 0.02);
      return { t: end - (n - 1 - i) * step, o, h: Math.max(o, c) * 1.002, l: Math.min(o, c) * 0.998, c, v: volume ? 100 + (i % 7) * 10 : 0, closed: i < n - 1 };
    });
  };
  const waveMock = (volume = true) => (u) => {
    if (u.pathname === '/api/crypto/candles') {
      const tf = (u.searchParams.get('tf') ?? 'H1').toUpperCase();
      return json({ symbol: u.searchParams.get('symbol'), tf, candles: wave(Math.min(400, Number(u.searchParams.get('limit') ?? 400)), STEP[tf] ?? 3600, volume) });
    }
    return null;
  };
  const add = (p, key) => pc(p, (k) => window.__pc.useStudies.getState().add(k), key);
  const layer = (p) => pc(p, () => {
    const l = window.__pc.useSignals.getState().layer;
    return { reads: l.reads.map((r) => ({ id: r.id, state: r.state, events: r.events.length, stop: r.stop, shape: r.note.shape })), confidence: l.confidence, setup: l.setup, names: l.names, keys: l.keys };
  });

  await test('signals2: the Now strip under the plot — the Setup score and a pill per study with its hit rate', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    expect(!(await p.$('.now-strip')), 'no strip before a study is on');
    const rsi = await add(p, 'rsi');
    const macd = await add(p, 'macd');
    await p.waitForFunction(() => document.querySelectorAll('.now-strip .sig-pill').length === 2, null, { timeout: 10000 });
    const l = await layer(p);
    expect(l.names[rsi] === 'RSI' && l.names[macd] === 'MACD', `names ${JSON.stringify(l.names)}`);
    for (const id of [rsi, macd]) {
      const rep = l.confidence[id];
      const text = await p.textContent(`.sig-pill[data-id="${id}"]`);
      const rate = await p.textContent(`.sig-pill[data-id="${id}"] .sig-rate`).catch(() => null);
      expect(text.includes(l.names[id]), `pill ${text}`);
      if (rep.samples >= 8) expect(rate.replace(/[⁦⁩]/g, '') === `${Math.round(rep.winRate * 100)}% از ${rep.samples}`, `pill figure ${rate} vs ${JSON.stringify(rep)}`);
      else if (rep.samples > 0) expect(rate === 'کم‌داده', `thin pill ${rate}`);
      expect(rep.horizon === 10, `default horizon ${rep.horizon}`);
    }
    expect(l.confidence[rsi].samples >= 8, `the wave gives RSI a record: ${JSON.stringify(l.confidence[rsi])}`);
    // The Setup score: Latin figure, grey→gold, the side beside it.
    const chip = await p.textContent('.setup-chip');
    expect(chip.includes('وضعیت') && chip.includes(String(l.setup.score)), `setup chip ${chip}`);
    expect(!/[۰-۹]/.test(chip), 'the score is a Latin figure');
    expect(l.setup.score <= 100 && l.setup.score >= 0, `score ${l.setup.score}`);
    // Under the plot: the chart gave up exactly the strip's height.
    const geo = await pc(p, () => {
      const host = document.querySelector('.chart-host').getBoundingClientRect();
      const foot = document.querySelector('.sig-foot').getBoundingClientRect();
      return { hostBottom: host.bottom, footTop: foot.top, footBottom: foot.bottom, wrapBottom: document.querySelector('.ws-chart').getBoundingClientRect().bottom };
    });
    expect(Math.abs(geo.hostBottom - geo.footTop) < 1.5 && Math.abs(geo.footBottom - geo.wrapBottom) < 1.5, `strip not under the plot ${JSON.stringify(geo)}`);
    // English: the same strip in the other language.
    await pc(p, () => window.__pc.usePrefs.getState().setLang('en'));
    await p.waitForFunction(() => /Setup/.test(document.querySelector('.setup-chip')?.textContent ?? ''), null, { timeout: 5000 });
    const enRate = (await p.textContent(`.sig-pill[data-id="${rsi}"] .sig-rate`)).replace(/[⁦⁩]/g, '');
    expect(/^\d+% of \d+$/.test(enRate) || enRate === 'low data', `english pill ${enRate}`);
    return env;
  });

  await test('signals2: the Explain sheet — sentence, hit rate with N and horizon, last five, stop, horizon chips, other timeframes', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    const rsi = await add(p, 'rsi');
    await p.waitForSelector(`.sig-pill[data-id="${rsi}"]`);
    await p.click(`.sig-pill[data-id="${rsi}"]`);
    await p.waitForSelector('.explain-sheet');
    const body = await p.textContent('.explain-sheet');
    expect(body.includes('چارت چه می‌گوید') && body.includes('اینجا چطور کار کرده') && body.includes('خوانشی از گذشته است، نه توصیه.'), `sheet ${body.slice(0, 300)}`);
    expect(body.includes('شاخص قدرت نسبی') || body.includes('RSI'), 'what the study is');
    const l = await layer(p);
    const rep = l.confidence[rsi];
    const line = (await p.textContent('.xp-conf-line')).replace(/[⁦⁩]/g, '');
    const fa = (n) => String(n).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
    expect(line === `در ${Math.round(rep.winRate * 100)}% از ${fa(rep.samples)} بار درست بوده، با اندازه‌گیری ${fa(10)} کندل بعد.`, `confidence line ${line}`);
    expect((await p.textContent('.xp-conf-r')).includes('برابر ریسک'), 'average R');
    const dots = await p.$$eval('.xp-dots .sig-dot', (d) => d.length);
    expect(dots === Math.min(5, rep.samples), `last five dots ${dots}`);
    const sentence = await p.textContent('.xp-sentence');
    expect(sentence.includes('RSI'), `sentence ${sentence}`);
    const stop = await p.textContent('.xp-stop');
    expect(/حد ضرر پیشنهادی: (زیر|بالای) [\d,.]+/.test(stop.replace(/[⁦⁩]/g, '')), `stop ${stop}`);
    // The horizon: 20 bars re-measures the base rate.
    await p.click('.xp-chip[data-horizon="20"]');
    await p.waitForFunction(() => document.querySelector('.xp-conf')?.dataset.horizon === '20', null, { timeout: 5000 });
    const l20 = await layer(p);
    expect(l20.confidence[rsi].horizon === 20, 'horizon 20 in the layer');
    expect((await p.textContent('.xp-conf-line')).includes('۲۰ کندل بعد'), 'the sentence names the new horizon');
    // The same study on the other bar lengths (the chart is on H1: H4 and D1 answer).
    await p.click('[data-act="timeframes"]');
    await p.waitForSelector('.xp-tf-row', { timeout: 10000 });
    const tfs = await p.$$eval('.xp-tf-row', (r) => r.map((x) => x.dataset.tf));
    expect(JSON.stringify(tfs) === JSON.stringify(['H4', 'D1']), `timeframes ${tfs}`);
    expect((await p.textContent('.xp-tf')).includes('RSI'), 'each row says what it says');
    return env;
  });

  await test('signals2: the sheet\'s alert opens the alert dialog and «practise» starts replay on the signal', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    const rsi = await add(p, 'rsi');
    await p.waitForSelector(`.sig-pill[data-id="${rsi}"]`);
    await p.click(`.sig-pill[data-id="${rsi}"]`);
    await p.waitForSelector('[data-act="alert"]');
    expect((await p.textContent('[data-act="alert"]')).includes('روی سیگنال بعدی هشدار بسازید'), 'alert label');
    await p.click('[data-act="alert"]');
    await p.waitForSelector('.alert-dlg', { timeout: 5000 });
    expect(!(await p.$('.explain-sheet')), 'the sheet closes behind the dialog');
    await p.keyboard.press('Escape');
    await p.waitForFunction(() => !document.querySelector('.alert-dlg'), null, { timeout: 5000 });
    await p.click(`.sig-pill[data-id="${rsi}"]`);
    await p.waitForSelector('[data-act="practise"]');
    expect((await p.textContent('[data-act="practise"]')).includes('در ریپلی تمرین کنید'), 'practise label');
    const want = await pc(p, (id) => { const l = window.__pc.useSignals.getState().layer; const r = l.reads.find((x) => x.id === id); return l.bars[r.events[r.events.length - 1].bar].t; }, rsi);
    await p.click('[data-act="practise"]');
    await p.waitForFunction(() => window.__pc.useReplay.getState().slice != null, null, { timeout: 5000 });
    const slice = await pc(p, () => { const s = window.__pc.useReplay.getState().slice; return s[s.length - 1].t; });
    expect(slice === want, `replay starts on the signal: ${slice} vs ${want}`);
    // Replay hides the future, and the provenance line says so.
    await p.waitForFunction(() => (document.querySelector('.prov-line')?.dataset.kinds ?? '').includes('replay'), null, { timeout: 5000 });
    expect((await p.textContent('.prov-line')).includes('بازپخش روشن است.'), 'replay provenance');
    return env;
  });

  await test('signals2: tapping the Setup score lists every study; a row moves the sheet onto it', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    await add(p, 'rsi');
    const ema = await add(p, 'ema');
    await p.waitForFunction(() => document.querySelectorAll('.now-strip .sig-pill').length === 2, null, { timeout: 10000 });
    await p.click('.setup-chip');
    await p.waitForSelector('.explain-sheet .xp-score');
    const from = (await p.textContent('.xp-from')).replace(/[⁦⁩]/g, '');
    expect(/[۰-۹]+ اندیکاتور · [۰-۹]+ سیگنال/.test(from), `setup from ${from}`);
    expect(await p.$$eval('.xp-row', (r) => r.length) === 2, 'a row per study');
    await p.click(`.xp-row[data-id="${ema}"]`);
    await p.waitForFunction((id) => document.querySelector('.explain-sheet')?.dataset.id === id, ema, { timeout: 5000 });
    expect((await p.textContent('.xp-sentence')).includes('EMA'), 'the EMA sentence');
    return env;
  });

  await test('signals2: marks on the candles — labels, triangles only, off', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    const rsi = await add(p, 'rsi');
    await p.waitForFunction(() => Number(document.querySelector('canvas.signal-marks')?.dataset.marks ?? 0) > 0, null, { timeout: 10000 });
    const marks = () => pc(p, () => { const d = document.querySelector('canvas.signal-marks').dataset; return { marks: Number(d.marks), labelled: Number(d.labelled), drawn: Number(d.drawn), labels: Number(d.labels), detail: d.detail }; });
    let m = await marks();
    expect(m.marks <= 6 && m.labelled === m.marks, `six newest, labelled: ${JSON.stringify(m)}`);
    // Close in: the words go under the triangles; far out: one mark per ten bars.
    await pc(p, () => window.__pc.useChartBus.getState().api.timeScale().applyOptions({ barSpacing: 16 }));
    await p.waitForFunction(() => document.querySelector('canvas.signal-marks')?.dataset.detail === 'label', null, { timeout: 5000 });
    m = await marks();
    expect(m.drawn === 0 || m.labels > 0, `labels when zoomed in ${JSON.stringify(m)}`);
    await pc(p, () => window.__pc.useChartBus.getState().api.timeScale().applyOptions({ barSpacing: 3 }));
    await p.waitForFunction(() => document.querySelector('canvas.signal-marks')?.dataset.detail === 'thinned', null, { timeout: 5000 });
    expect((await marks()).labels === 0, 'no words when thinned');
    // The setting, per study, in the Explain sheet.
    await p.click(`.sig-pill[data-id="${rsi}"]`);
    await p.click('.xp-chip[data-style="triangles"]');
    await p.waitForFunction(() => document.querySelector('canvas.signal-marks')?.dataset.labelled === '0', null, { timeout: 5000 });
    expect(Number((await marks()).marks) > 0, 'triangles stay');
    await p.click('.xp-chip[data-style="off"]');
    await p.waitForFunction(() => document.querySelector('canvas.signal-marks')?.dataset.marks === '0', null, { timeout: 5000 });
    const saved = await pc(p, () => JSON.parse(localStorage.getItem('pc.signals.markers.v1') ?? '{}'));
    expect(saved.rsi === 'off', `kept ${JSON.stringify(saved)}`);
    // The pill, the score and the sheet stay with a silenced study.
    expect(await p.$(`.sig-pill[data-id="${rsi}"]`), 'pill kept');
    await p.click('.xp-chip[data-style="labels"]');
    await p.waitForFunction(() => Number(document.querySelector('canvas.signal-marks')?.dataset.labelled ?? 0) > 0, null, { timeout: 5000 });
    return env;
  });

  await test('signals2: the repaint badge in the legend and the study settings, and the rewrite in the provenance line', async () => {
    const env = await open('BTCUSDT/1h?e2e=1', { mock: waveMock() });
    const p = env.page;
    await ready(p);
    const pivots = await add(p, 'pivots');
    const zig = await add(p, 'zigzag');
    const rsi = await add(p, 'rsi');
    await p.waitForSelector(`.legend-study[data-uid="${zig}"] .repaint-badge`, { timeout: 10000 });
    const badge = (uid) => p.$eval(`.legend-study[data-uid="${uid}"] .repaint-badge`, (e) => ({ claim: e.dataset.claim, text: e.textContent, title: e.title })).catch(() => null);
    const z = await badge(zig);
    expect(z.claim === 'repaints' && z.text === 'عقب‌تر دوباره نوشته می‌شود' && z.title.includes('برای بک‌تست به آن تکیه نکنید'), `zigzag ${JSON.stringify(z)}`);
    const pv = await badge(pivots);
    expect(pv.claim === 'settled' && pv.text === 'repaint نمی‌کند', `pivots ${JSON.stringify(pv)}`);
    expect((await badge(rsi)) === null, 'no guessed mark on a study this build has not read');
    await p.waitForFunction(() => (document.querySelector('.prov-line')?.dataset.kinds ?? '').includes('repainting'), null, { timeout: 5000 });
    expect((await p.textContent('.prov-line')).includes('با آمدن کندل‌های تازه'), 'zigzag named under the chart');
    // In the study's settings: the badge and its sentence.
    await p.click(`.legend-study[data-uid="${pivots}"] .legend-swatch`);
    await p.waitForSelector('.st-dialog .st-repaint');
    const st = await p.textContent('.st-dialog .st-repaint');
    expect(st.includes('repaint نمی‌کند') && st.includes('فقط از کندل‌های بسته حساب می‌شود'), `settings ${st}`);
    // A study on its own higher timeframe that does not wait for the close rewrites.
    await pc(p, (u) => window.__pc.useStudies.getState().patch(u, { timeframe: '240', waitClose: false }), rsi);
    await p.keyboard.press('Escape');
    await p.waitForFunction((u) => document.querySelector(`.legend-study[data-uid="${u}"] .repaint-badge`)?.dataset.claim === 'repaints', rsi, { timeout: 5000 });
    return env;
  });

  await test('signals2: the provenance line — candles built on this device and a feed with no volume', async () => {
    const env = await open('BTCUSDT/2h?e2e=1', { mock: waveMock(false) });
    const p = env.page;
    await ready(p);
    await p.waitForSelector('.prov-line', { timeout: 10000 });
    const kinds = await p.$eval('.prov-line', (e) => e.dataset.kinds);
    // Beside the venue caption in the legend; the plot keeps its whole height for it.
    expect(await p.$('.legend .legend-source + .prov-line'), 'the line follows the venue caption');
    expect(await pc(p, () => getComputedStyle(document.querySelector('.ws-chart')).getPropertyValue('--sigfoot-h').trim() || '0px') === '0px', 'no strip, no room taken');
    expect(kinds === 'folded noVolume', `kinds ${kinds}`);
    const text = (await p.textContent('.prov-line')).replace(/[⁦⁩]/g, '');
    expect(text.startsWith('آنچه در این تصویر نیست — ') && text.includes('از کندل‌های H1 ساخته می‌شوند') && text.includes('این فید حجم نمی‌فرستد.'), `line ${text}`);
    // A native bar length with volume says nothing.
    await pc(p, () => window.__pc.usePrefs.getState().setInterval('60'));
    await p.waitForFunction(() => !(document.querySelector('.prov-line')?.dataset.kinds ?? '').includes('folded'), null, { timeout: 10000 });
    return env;
  });
}
