// گاردِ رگرسیون — دراپ‌داونِ «منبع» باید واقعاً اثر کند.
//
// باگ: رجیستری `source: 'close'` را به‌صورتِ **رشته** می‌فرستد، ولی `_src` در
// indicators_ext_a.js فقط آرایه را می‌پذیرفت (`Array.isArray('high') === false`).
// نتیجه: هر ۱۳ اندیکاتورِ آن فایل بی‌صدا روی close می‌افتادند. دراپ‌داون در رابط
// بود، کاربر عوضش می‌کرد، و هیچ اتفاقی نمی‌افتاد.
//
// اجرا:
//   npx esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node --test test/source_input.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { REGISTRY } from '../../../src/engine/indicators.js';

// ۲۵۰ کندل — بلندتر از بزرگ‌ترین دورهٔ پیش‌فرض (linreg = ۱۰۰)، وگرنه همه null
// می‌شوند و تست مثبتِ کاذب می‌دهد.
const N = 250;
const C = { open: [], high: [], low: [], close: [], volume: [], time: [] };
{
  let p = 100;
  for (let k = 0; k < N; k++) {
    p += Math.sin(k / 5) * 2;
    C.open.push(p); C.close.push(p + 0.5);
    C.high.push(p + 9); C.low.push(p - 9);   // فاصلهٔ زیادِ high/low تا اثر آشکار شود
    C.volume.push(1000 + k); C.time.push(1700000000 + k * 60);
  }
}

// اندیکاتورهایی که ورودیِ source دارند (هر دو افزونه)
const SOURCE_KEYS = [
  // ext_a — این‌ها همگی خراب بودند
  'smma', 'zlema', 'kama', 't3', 'mcginley', 'linreg', 'lsma', 'trix',
  'bbpercent', 'bbw', 'stddev', 'envelopes', 'ppo',
];

const run = (key, source) => {
  const def = REGISTRY[key];
  assert.ok(def, `${key} در رجیستری نیست`);
  return JSON.stringify(def.calc(C, { ...def.inputs, source }));
};

test('هر اندیکاتورِ دارای source به تغییرِ منبع واکنش می‌دهد', () => {
  const dead = [];
  for (const key of SOURCE_KEYS) {
    if (run(key, 'close') === run(key, 'high')) dead.push(key);
  }
  assert.deepEqual(dead, [], `دراپ‌داونِ منبع روی این‌ها no-op است: ${dead.join(', ')}`);
});

test('منابعِ ترکیبی (hl2/hlc3/ohlc4/hlcc4) هم resolve می‌شوند', () => {
  const bad = [];
  for (const src of ['hl2', 'hlc3', 'ohlc4', 'hlcc4', 'open', 'low']) {
    // smma نماینده است — همان _src را صدا می‌زند که بقیه
    if (run('smma', src) === run('smma', 'close')) bad.push(src);
  }
  assert.deepEqual(bad, [], `این منابع اثر ندارند: ${bad.join(', ')}`);
});

test('منبعِ نامعتبر بی‌صدا به close می‌افتد — نه کرش', () => {
  assert.equal(run('smma', 'nonsense-source'), run('smma', 'close'));
  assert.equal(run('smma', undefined), run('smma', 'close'));
});

test('سازگاریِ عقب‌رو: آرایهٔ از پیش resolve‌شده هنوز کار می‌کند', () => {
  const def = REGISTRY.smma;
  const asArray = JSON.stringify(def.calc(C, { ...def.inputs, source: C.high }));
  const asName = run('smma', 'high');
  assert.equal(asArray, asName, 'آرایهٔ خام و نامِ رشته‌ای باید یک نتیجه بدهند');
});
