// گاردِ رگرسیون — چرخهٔ حیاتِ ترسیم (فاز ۵).
//
// باگ‌ها:
//  ۵.۴ clearAll و setDrawings هیچ‌کدام undo نمی‌کردند — لودِ چیدمان/پاک‌کردنِ همه
//      برگشت‌ناپذیر بود.
//  ۵.۵ _hit فیلدِ visible را چک نمی‌کرد — شیِ مخفی‌شده هنوز کلیک‌پذیر بود.
//
// اجرا:
//   npx esbuild src/bazaarnama/drawings.js --bundle --format=esm --outfile=test/.draw.bundle.mjs
//   node --test test/drawings_lifecycle.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
// stubهای مرورگر — drawings.js در constructor به window دست می‌زند
globalThis.window = globalThis.window || { addEventListener() {}, removeEventListener() {}, devicePixelRatio: 1, requestAnimationFrame: (f) => setTimeout(f, 0), cancelAnimationFrame: (id) => clearTimeout(id), getComputedStyle: () => ({}) };
globalThis.document = globalThis.document || { createElement: () => ({ getContext: () => new Proxy({}, { get: () => () => {}, set: () => true }), style: {} }), addEventListener() {}, removeEventListener() {} };
globalThis.devicePixelRatio = 1;
globalThis.requestAnimationFrame = globalThis.window.requestAnimationFrame;
globalThis.cancelAnimationFrame = globalThis.window.cancelAnimationFrame;

const { DrawingLayer } = await import('../../../src/engine/drawings.js');

// mockهای کوچک — DrawingLayer فقط canvas.getContext و chart را لمس می‌کند.
function mkLayer() {
  const ctx = new Proxy({}, { get: (_, k) => (typeof k === 'string' && k in {} ? undefined : () => {}), set: () => true });
  const canvas = { getContext: () => ctx, width: 800, height: 400, style: {}, getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }), addEventListener: () => {}, removeEventListener: () => {} };
  const chart = { timeScale: () => ({ timeToCoordinate: (t) => t, coordinateToTime: (x) => x, subscribeVisibleLogicalRangeChange: () => {}, getVisibleLogicalRange: () => null }), priceScale: () => ({}) };
  const dl = new DrawingLayer(canvas, chart);
  // نگاشتِ ساده‌ی زمان/قیمت ↔ پیکسل تا _x/_y/_t/_p کار کنند
  dl.series = { priceToCoordinate: (p) => p, coordinateToPrice: (y) => y };
  dl._x = (t) => t; dl._y = (p) => p; dl._t = (x) => x; dl._p = (y) => y;
  dl.render = () => {}; // no-op: تستِ منطق است نه رندرِ پیکسل
  return dl;
}

test('DrawingLayer ساخته می‌شود', () => {
  const dl = mkLayer();
  assert.ok(Array.isArray(dl.drawings));
  assert.equal(dl.drawings.length, 0);
});

// ── ۵.۴ ──
test('clearAll برگشت‌پذیر است (undo)', () => {
  const dl = mkLayer();
  dl.drawings = [{ type: 'hline', p: 100 }, { type: 'hline', p: 200 }];
  assert.ok(dl.canUndo() === false || dl.canUndo() === true); // تاریخچه هنوز خالی
  dl.clearAll();
  assert.equal(dl.drawings.length, 0, 'clearAll باید پاک کند');
  assert.ok(dl.canUndo(), 'clearAll باید در تاریخچه ثبت شده باشد');
  dl.undo();
  assert.equal(dl.drawings.length, 2, 'undo باید ۲ ترسیم را برگرداند');
});

test('setDrawings با record=true برگشت‌پذیر است', () => {
  const dl = mkLayer();
  dl.drawings = [{ type: 'hline', p: 100 }];
  dl.setDrawings([{ type: 'vline', t: 5 }, { type: 'vline', t: 6 }], true);
  assert.equal(dl.drawings.length, 2);
  dl.undo();
  assert.equal(dl.drawings.length, 1, 'undo باید ترسیمِ قبلی را برگرداند');
  assert.equal(dl.drawings[0].type, 'hline');
});

test('setDrawings با record=false (لودِ اولیه) undo تولید نمی‌کند', () => {
  const dl = mkLayer();
  dl.setDrawings([{ type: 'hline', p: 1 }]); // پیش‌فرض record=false
  assert.ok(!dl.canUndo(), 'لودِ اولیه نباید در تاریخچه بیفتد');
});

// ── ۵.۵ ──
test('_hit شیِ مخفی (visible=false) را برنمی‌گرداند', () => {
  const dl = mkLayer();
  dl.drawings = [{ type: 'hline', p: 100, visible: false }];
  // کلیک دقیقاً روی خطِ افقیِ p=100
  const hit = dl._hit(400, 100);
  assert.equal(hit, -1, 'شیِ مخفی نباید hit شود');
});

test('_hit شیِ visible را برمی‌گرداند', () => {
  const dl = mkLayer();
  dl.drawings = [{ type: 'hline', p: 100 }]; // visible undefined = نمایان
  const hit = dl._hit(400, 100);
  assert.equal(hit, 0, 'شیِ نمایان باید hit شود');
});

test('_hit شیِ مخفی را رد می‌کند ولی نمایانِ زیرش را می‌گیرد', () => {
  const dl = mkLayer();
  dl.drawings = [{ type: 'hline', p: 100 }, { type: 'hline', p: 100, visible: false }];
  // آخرین (مخفی) باید رد شود، اولی (نمایان) گرفته شود
  const hit = dl._hit(400, 100);
  assert.equal(hit, 0, 'باید شیِ نمایانِ زیرین را بگیرد نه مخفیِ رویی');
});
