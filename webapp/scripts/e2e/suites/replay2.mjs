// Replay and drawings, from a reader's report: a drawing reaching past the replay's last bar must
// stay drawn while the replay steps forward and back, a long/short position is placed with one click
// at a default size, and a plain click on the chart never moves the replay (Alt+click does; the
// studies suite checks that part with the scrubber).
export default async function ({ test, open, ready, pc, expect }) {
  const layer = (page, fn, arg) => page.evaluate(([src, a]) => (0, eval)(src)(window.__pc.useChartBus.getState().drawingLayer, a), [String(fn), arg]);
  const box = (page) => page.locator('.chart-host').boundingBox();

  await test('replay2: a drawing past the replay’s last bar stays drawn as the replay steps forward and back', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => { const r = window.__pc.useReplay.getState(); const bars = window.__pc.useChartBus.getState().bars; r.select(); window.__pc.useReplay.getState().start(bars, bars[200].t, { keepView: false }); });
    await page.waitForFunction(() => window.__pc.useReplay.getState().slice?.length > 0, null, { timeout: 5000 });
    await page.waitForTimeout(200);
    // A fib from the last revealed bar to ten bars into the space the replay has not revealed yet.
    await layer(page, (l) => {
      const s = window.__pc.useReplay.getState().slice; const last = s.at(-1); const iv = last.t - s.at(-2).t;
      l.addDrawing({ type: 'fib', p0: { t: s.at(-12).t, p: s.at(-12).l }, p1: { t: last.t + 10 * iv, p: last.h }, color: '#2962FF' });
    });
    const drawn = () => layer(page, (l) => { const d = l.getDrawings().at(-1); return [l._x(d.p0.t), l._x(d.p1.t)].every((x) => x != null && Number.isFinite(x)); });
    expect(await drawn(), 'the fib is not drawn past the last revealed bar');
    await pc(page, () => window.__pc.useReplay.getState().controller.stepBy(5));
    await page.waitForTimeout(200);
    expect(await drawn(), 'the fib was cut when the replay stepped forward');
    await pc(page, () => window.__pc.useReplay.getState().controller.seek({ index: 150 }));
    await page.waitForTimeout(200);
    expect(await drawn(), 'the fib disappeared when the replay went back');
    // A position placed past the last bar survives the same round trip.
    await layer(page, (l) => {
      const s = window.__pc.useReplay.getState().slice; const last = s.at(-1); const iv = last.t - s.at(-2).t;
      l.addDrawing({ type: 'longshort', p0: { t: last.t + 2 * iv, p: last.c }, p1: { t: last.t + 20 * iv, p: last.c * 0.98 }, color: '#2962FF' });
    });
    await pc(page, () => window.__pc.useReplay.getState().controller.seek({ index: 120 }));
    await page.waitForTimeout(200);
    expect(await layer(page, (l) => l._posGeom(l.getDrawings().at(-1)) != null), 'the position disappeared when the replay went back');
    return env;
  });

  await test('replay2: after a big jump the drawings are drawn on the new price scale without moving the mouse', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => { const r = window.__pc.useReplay.getState(); const bars = window.__pc.useChartBus.getState().bars; r.select(); window.__pc.useReplay.getState().start(bars, bars[60].t, { keepView: false }); });
    await page.waitForFunction(() => window.__pc.useReplay.getState().slice?.length > 0, null, { timeout: 5000 });
    await layer(page, (l) => { const s = window.__pc.useReplay.getState().slice; l.addDrawing({ type: 'fib', p0: { t: s.at(-20).t, p: s.at(-20).l }, p1: { t: s.at(-2).t, p: s.at(-2).h }, color: '#2962FF' }); });
    // Note the price scale every time the layer draws.
    await layer(page, (l) => { const r = l.render.bind(l); window.__drawnAt = null; l.render = (...a) => { window.__drawnAt = l.series.coordinateToPrice(50); return r(...a); }; });
    await page.mouse.move(5, 5);
    await pc(page, () => window.__pc.useReplay.getState().controller.seek({ index: 480 }));
    await page.waitForTimeout(600);
    const s = await layer(page, (l) => ({ drawn: window.__drawnAt, now: l.series.coordinateToPrice(50) }));
    expect(s.drawn != null && Math.abs(s.drawn - s.now) < 1e-6, `the drawings were left on the old scale ${JSON.stringify(s)}`);
    return env;
  });

  await test('replay2: one click places a long position at a default size; a short opens the other way', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    const b = await box(page);
    for (const [tool, fx] of [['longshort', 0.45], ['short', 0.6]]) {
      await pc(page, (t) => window.__pc.useDrawing.getState().setTool(t, 'forecasting'), tool);
      const before = await layer(page, (l) => l.getDrawings().length);
      await page.mouse.click(b.x + b.width * fx, b.y + b.height * 0.5);
      await page.waitForTimeout(150);
      const d = await layer(page, (l) => { const x = l.getDrawings().at(-1); return x ? { type: x.type, n: l.getDrawings().length, p0: x.p0, p1: x.p1, g: l._posPrices(x) } : null; });
      expect(d && d.n === before + 1 && d.type === tool, `one click did not place a ${tool}: ${JSON.stringify(d)}`);
      expect(d.p1.t > d.p0.t, `${tool}: no width`);
      expect(tool === 'longshort' ? d.g.stop < d.g.entry && d.g.target > d.g.entry : d.g.stop > d.g.entry && d.g.target < d.g.entry, `${tool}: stop and target on the wrong sides ${JSON.stringify(d.g)}`);
      expect(Math.abs(d.g.target - d.g.entry) / Math.abs(d.g.entry - d.g.stop) > 1.9, `${tool}: the target is not two times the risk`);
    }
    // A drag still sizes it by hand.
    await pc(page, () => window.__pc.useDrawing.getState().setTool('longshort', 'forecasting'));
    const n0 = await layer(page, (l) => l.getDrawings().length);
    await page.mouse.move(b.x + b.width * 0.3, b.y + b.height * 0.4);
    await page.mouse.down();
    await page.mouse.move(b.x + b.width * 0.35, b.y + b.height * 0.55, { steps: 6 });
    await page.mouse.move(b.x + b.width * 0.4, b.y + b.height * 0.7, { steps: 6 });
    await page.mouse.up();
    await page.waitForTimeout(150);
    expect(await layer(page, (l) => l.getDrawings().length) === n0 + 1, 'a drag no longer places a position');
    return env;
  });

  await test('replay2: while replaying, a click or a double click on the chart leaves the replay where it is', async () => {
    const env = await open('BTCUSDT/1h?e2e=1');
    const { page } = env;
    await ready(page);
    await pc(page, () => { const r = window.__pc.useReplay.getState(); const bars = window.__pc.useChartBus.getState().bars; r.select(); window.__pc.useReplay.getState().start(bars, bars[150].t, { keepView: false }); });
    await page.waitForFunction(() => window.__pc.useReplay.getState().slice?.length > 0, null, { timeout: 5000 });
    await pc(page, () => window.__pc.useReplay.getState().controller.stepBy(40));
    await page.waitForTimeout(200);
    const idx = () => pc(page, () => window.__pc.useReplay.getState().state.idx);
    const i0 = await idx();
    const b = await box(page);
    await page.mouse.click(b.x + b.width * 0.3, b.y + b.height * 0.5);
    await page.mouse.dblclick(b.x + b.width * 0.35, b.y + b.height * 0.45);
    await page.waitForTimeout(200);
    expect(await idx() === i0, `the replay moved on a plain click: ${i0} → ${await idx()}`);
    return env;
  });
}
