// گارد رگرسیون — ایچیموکو باید جابه‌جایی درست داشته باشد.
//
// باگ: Senkou A/B بدون جابه‌جایی روی همان کندل رسم می‌شدند و Chikou اصلاً نبود.
// ابر روی قیمت می‌نشست به‌جای اینکه ۲۶ کندل جلوتر برجسته شود — وارونه‌ی کارکردش.
//
// اجرا:
//   npx esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node --test test/ichimoku.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { ichimoku, REGISTRY } from '../../../src/engine/indicators.js';

const N = 160;
const H = [], L = [], C = [];
for (let k = 0; k < N; k++) {
  const p = 100 + Math.sin(k / 6) * 10 + Math.cos(k / 11) * 4;
  H.push(p + 3); L.push(p - 3); C.push(p + Math.sin(k) * 0.7);
}
const T = 9, K = 26, B = 52;
const R = ichimoku(H, L, C, T, K, B);

test('Tenkan و Kijun روی کندل جاری‌اند (بدون جابه‌جایی)', () => {
  const midAt = (i, p) => {
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, H[i - j]); ll = Math.min(ll, L[i - j]); }
    return (hh + ll) / 2;
  };
  assert.ok(Math.abs(R.tenkan[100] - midAt(100, T)) < 1e-9);
  assert.ok(Math.abs(R.kijun[100] - midAt(100, K)) < 1e-9);
});

test('Senkou A دقیقاً ۲۶ کندل جلو جابه‌جا شده', () => {
  for (const i of [60, 90, 120, 150]) {
    const src = i - K;
    const expected = (R.tenkan[src] + R.kijun[src]) / 2;
    assert.ok(Math.abs(R.spanA[i] - expected) < 1e-9,
      `spanA[${i}] باید از کندل ${src} بیاید — گرفتیم ${R.spanA[i]}، انتظار ${expected}`);
  }
});

test('Senkou B دقیقاً ۲۶ کندل جلو جابه‌جا شده', () => {
  const midAt = (i, p) => {
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, H[i - j]); ll = Math.min(ll, L[i - j]); }
    return (hh + ll) / 2;
  };
  for (const i of [90, 120, 150]) {
    assert.ok(Math.abs(R.spanB[i] - midAt(i - K, B)) < 1e-9, `spanB[${i}] جابه‌جا نشده`);
  }
});

test('۲۶ کندل اول ابر null است — منبعش هنوز وجود ندارد', () => {
  assert.ok(R.spanA.slice(0, K).every((v) => v === null), 'spanA در ۲۶ کندل اول باید null باشد');
  assert.ok(R.spanB.slice(0, K).every((v) => v === null), 'spanB در ۲۶ کندل اول باید null باشد');
});

test('ابر روی قیمت نمی‌نشیند — با نسخه‌ی بدون جابه‌جایی فرق دارد', () => {
  // بازتولید باگ قدیمی: spanA بدون جابه‌جایی
  const naive = R.tenkan.map((v, i) => (v != null && R.kijun[i] != null ? (v + R.kijun[i]) / 2 : null));
  let differs = 0;
  for (let i = K; i < N; i++) if (naive[i] != null && R.spanA[i] != null && Math.abs(naive[i] - R.spanA[i]) > 1e-6) differs++;
  assert.ok(differs > N / 4, `فقط ${differs} کندل با نسخه‌ی بدون جابه‌جایی فرق دارد — جابه‌جایی اعمال نشده`);
});

test('Chikou کلوز را ۲۶ کندل عقب رسم می‌کند', () => {
  for (const i of [0, 40, 80, 130]) assert.equal(R.chikou[i], C[i + K], `chikou[${i}] باید close[${i + K}] باشد`);
  assert.ok(R.chikou.slice(N - K).every((v) => v === null), '۲۶ کندل آخر chikou باید null باشد — کلوز آینده وجود ندارد');
});

test('رجیستری هر ۵ خط را می‌دهد و ابر به index های ۲/۳ اشاره می‌کند', () => {
  const def = REGISTRY.ichimoku;
  const res = def.calc({ open: C, high: H, low: L, close: C, volume: C.map(() => 0), time: C.map((_, i) => i) }, { ...def.inputs });
  assert.equal(res.lines.length, 5, 'Tenkan · Kijun · Senkou A · Senkou B · Chikou');
  assert.deepEqual(res.cloud, [2, 3], 'ابر باید بین Senkou A و B باشد');
  assert.ok(res.lines[4].data.some((v) => v != null), 'Chikou داده دارد');
});
