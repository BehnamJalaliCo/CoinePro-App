// گارد رگرسیون — ذره‌بین بار در replay (فاز ۶.۳).
//
// باگ: setIntrabarData/enableIntrabar/onIntrabar کاملاً نوشته بودند ولی هرگز از
// BazaarNama صدا زده نمی‌شدند (صفر call site). حالا وصل شده‌اند: هنگام replay اگر
// toggle روشن باشد، ریزکندل‌های TF پایین‌تر گرفته و map می‌شوند و کاربر داخل هر
// بار ریزگام می‌بیند.
//
// اجرا:
//   npx esbuild src/bazaarnama/ReplayController.js --bundle --format=esm --outfile=test/.replay.bundle.mjs
//   node --test test/replay_intrabar.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { ReplayController } from '../../../src/engine/ReplayController.js';

const mkBars = (n) => Array.from({ length: n }, (_, i) => ({ t: 1000 + i * 60, o: 100 + i, h: 101 + i, l: 99 + i, c: 100 + i, v: 10 }));

test('API ذره‌بین موجود است', () => {
  const c = new ReplayController({});
  assert.equal(typeof c.setIntrabarData, 'function');
  assert.equal(typeof c.enableIntrabar, 'function');
  assert.equal(c.hasIntrabar, false, 'پیش‌فرض خاموش');
});

test('setIntrabarData + enableIntrabar وضعیت را روشن می‌کند', () => {
  const c = new ReplayController({});
  c.setIntrabarData(new Map([[1060, [{ t: 1000, o: 1, h: 2, l: 0, c: 1 }]]]));
  c.enableIntrabar(true);
  assert.equal(c.hasIntrabar, true);
});

test('onIntrabar در حال پخش، ریزکندل‌های داخل بار را emit می‌کند', () => {
  const full = mkBars(40);
  const intrabarCalls = [];
  const stepCalls = [];
  const c = new ReplayController({
    onStep: (bar) => stepCalls.push(bar.t),
    onIntrabar: (sub, bar, j) => intrabarCalls.push({ subT: sub.t, barT: bar.t, j }),
  });
  // برای بار index=2 (t=1120) سه ریزکندل تعریف کن
  const map = new Map([[1120, [
    { t: 1120, o: 102, h: 102.3, l: 101.8, c: 102.1 },
    { t: 1140, o: 102.1, h: 102.6, l: 102.0, c: 102.4 },
    { t: 1160, o: 102.4, h: 103.0, l: 102.3, c: 103.0 },
  ]]]);
  c.setIntrabarData(map);
  c.enableIntrabar(true);
  c.enter(full, { startIndex: 1 }); // idx=1، بار بعدی index=2 دارد ریزکندل

  // چند تیک بزن تا از میان ریزکندل‌های بار ۲ رد شود
  c._tick(); // ریزکندل ۰
  c._tick(); // ریزکندل ۱
  c._tick(); // ریزکندل ۲
  c._tick(); // آخرین ریزکندل → step() بار را commit می‌کند

  assert.ok(intrabarCalls.length >= 3, `باید ≥۳ ریزگام emit شود، شد ${intrabarCalls.length}`);
  assert.equal(intrabarCalls[0].barT, 1120, 'ریزگام‌ها باید مربوط به بار ۱۱۲۰ باشند');
  assert.deepEqual(intrabarCalls.slice(0, 3).map((x) => x.j), [0, 1, 2], 'اندیس‌های ریزکندل به ترتیب');
  // high/low ریزکندل‌ها متفاوت‌اند (نه ثابت) — یعنی واقعاً داخل بار حرکت می‌کند
  assert.ok(new Set(intrabarCalls.map((x) => x.subT)).size >= 3, 'ریزکندل‌های متمایز');
});

test('بدون داده برای یک بار، به step معمولی سقوط می‌کند (بدون کرش)', () => {
  const full = mkBars(40);
  const steps = [];
  const c = new ReplayController({ onStep: (bar) => steps.push(bar.t), onIntrabar: () => {} });
  c.setIntrabarData(new Map()); // خالی — هیچ باری ریزکندل ندارد
  c.enableIntrabar(true);
  c.enter(full, { startIndex: 1 });
  assert.doesNotThrow(() => { c._tick(); c._tick(); });
  assert.ok(steps.length >= 1, 'باید به step معمولی افتاده باشد');
});

test('خاموش‌بودن ذره‌بین، مسیر عادی step را عوض نمی‌کند', () => {
  const full = mkBars(40);
  const steps = [];
  const intr = [];
  const c = new ReplayController({ onStep: (bar) => steps.push(bar.t), onIntrabar: () => intr.push(1) });
  // enableIntrabar صدا نمی‌زنیم
  c.enter(full, { startIndex: 1 });
  c._tick(); c._tick();
  assert.equal(intr.length, 0, 'ذره‌بین خاموش نباید onIntrabar بزند');
  assert.ok(steps.length >= 2, 'step عادی باید کار کند');
});
