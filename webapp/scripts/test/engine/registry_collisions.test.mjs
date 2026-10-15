// گاردِ رگرسیون — تصادمِ کلیدهای رجیستری باید صریح باشد، نه «آخرین نویسنده برنده».
//
// باگ: `Object.assign(REGISTRY, EXT_A, EXT_B)` بی‌صدا کلیدهای تکراری را بازنویسی
// می‌کرد. کامنتش می‌گفت «۳ کلیدِ مشترک» ولی واقعاً ۶ تا بود — و در موردِ trix
// **برندهٔ غلط** انتخاب می‌شد:
//
//   `_ema` در indicators_ext_b.js روی null مقدارِ قبلی را جلو می‌برد و warmup را
//   ایندکس‌محور می‌سنجد. برای TRIX که سه EMAِ تودرتوست، یعنی TRIX(18) از کندلِ ۱۸
//   مقدار می‌داد در حالی که ~۵۴ کندل لازم دارد → ۳۴ کندلِ ابتدایی از مقادیرِ جعلی.
//   نسخهٔ extA nullها را رد می‌کند و مقادیرِ واقعی را می‌شمارد.
//
// اجرا:
//   npx esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node --test test/registry_collisions.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { REGISTRY } from '../../../src/engine/indicators.js';

const N = 300;
const C = { open: [], high: [], low: [], close: [], volume: [], time: [] };
{
  let p = 100;
  for (let k = 0; k < N; k++) {
    p += Math.sin(k / 5) * 2 + Math.cos(k / 9);
    C.open.push(p); C.close.push(p + 0.4);
    C.high.push(p + 3); C.low.push(p - 3);
    C.volume.push(1000 + k * 3); C.time.push(1700000000 + k * 60);
  }
}
const lineOf = (r) => r.line ?? r.hist ?? r.lines?.[0]?.data ?? Object.values(r).find(Array.isArray);

test('رجیستریِ ادغام‌شده ۱۰۸ کلیدِ متمایز دارد', () => {
  assert.equal(Object.keys(REGISTRY).length, 108);
});

test('هر کلیدِ تصادمی دقیقاً یک تعریفِ زنده دارد', () => {
  // اگر گارد بشکند، یکی از این‌ها undefined یا شکل‌خراب می‌شود
  for (const k of ['trix', 'bbpercent', 'bbw', 'chaikinVol', 'bop', 'eom']) {
    const d = REGISTRY[k];
    assert.ok(d, `${k} از رجیستری افتاد`);
    assert.equal(typeof d.calc, 'function', `${k}.calc تابع نیست`);
    assert.ok(d.label, `${k} برچسب ندارد`);
  }
});

test('trix نسخهٔ extA است — warmupِ درست، نه ایندکس‌محور', () => {
  const d = REGISTRY.trix;
  const line = lineOf(d.calc(C, { ...d.inputs }));
  const first = line.findIndex((v) => v != null);

  // TRIX = سه EMAِ تودرتوی period → حداقل ~۳×period کندلِ واقعی لازم است.
  // نسخهٔ غلط (extB) از کندلِ period-1 = ۱۷/۱۸ مقدار می‌داد.
  assert.ok(first > 40, `trix از کندلِ ${first} شروع می‌شود — نسخهٔ ایندکس‌محورِ غلط برگشته (باید >۴۰ باشد)`);
  assert.ok(first < 70, `trix از کندلِ ${first} شروع می‌شود — بیش از حد دیر`);
});

test('trix هنوز دادهٔ واقعی می‌دهد', () => {
  const d = REGISTRY.trix;
  const line = lineOf(d.calc(C, { ...d.inputs })).filter((v) => v != null);
  assert.ok(line.length > N / 2, `فقط ${line.length} مقدار`);
  assert.ok(new Set(line).size > 1, 'خروجی ثابت است');
});

test('هر تصادم به تغییرِ منبع واکنش می‌دهد (source زنده است)', () => {
  for (const k of ['trix', 'bbpercent', 'bbw']) {
    const d = REGISTRY[k];
    if (!('source' in (d.inputs || {}))) continue;
    const a = JSON.stringify(d.calc(C, { ...d.inputs, source: 'close' }));
    const b = JSON.stringify(d.calc(C, { ...d.inputs, source: 'high' }));
    assert.notEqual(a, b, `${k}: دراپ‌داونِ منبع no-op است`);
  }
});

test('هیچ کلیدِ رجیستری calc یا label گم‌شده ندارد', () => {
  const bad = [];
  for (const [k, d] of Object.entries(REGISTRY)) {
    if (typeof d?.calc !== 'function') bad.push(`${k}: calc نیست`);
    if (!d?.label) bad.push(`${k}: label نیست`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});
