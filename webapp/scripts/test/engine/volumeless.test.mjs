// گاردِ رگرسیون — اندیکاتورهای حجمی روی ابزارِ بدونِ حجم (فارکس/فلز/شاخص).
//
// باگی که این تست جلویش را می‌گیرد: پیش‌تر mfi روی فارکس **ثابت ۱۰۰** برمی‌گرداند
// (اشباعِ خریدِ همیشگی) و vwap بی‌صدا به close تنزل می‌کرد و همچنان «VWAP» برچسب
// می‌خورد. هر دو عددِ با‌اطمینان‌غلط تولید می‌کردند که یک تریدر روی آن معامله می‌کند.
// قرارداد: بدونِ حجم → null، نه عدد.
//
// اجرا:
//   npx esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node --test test/volumeless.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { REGISTRY, mfi, vwap, obv, cmf, avwap, hasVolume } from '../../../src/engine/indicators.js';

const N = 120;
const mk = (withVol) => {
  const close = [], high = [], low = [], open = [], volume = [], time = [];
  let p = 1.1000;
  for (let i = 0; i < N; i++) {
    p += Math.sin(i / 7) * 0.0012;
    open.push(p); close.push(p + 0.0004); high.push(p + 0.0009); low.push(p - 0.0009);
    volume.push(withVol ? 1000 + i * 7 : 0);
    time.push(1700000000 + i * 3600);
  }
  return { open, high, low, close, volume, time };
};

const FX = mk(false);   // فارکس — حجم صفر
const CRYPTO = mk(true);

test('hasVolume تشخیص می‌دهد', () => {
  assert.equal(hasVolume(FX.volume), false);
  assert.equal(hasVolume(CRYPTO.volume), true);
  assert.equal(hasVolume(undefined), false);
  assert.equal(hasVolume([]), false);
  assert.equal(hasVolume([0, 0, 0]), false);
  assert.equal(hasVolume([0, 0, 5]), true);
});

test('mfi روی فارکس null است — نه ثابتِ ۱۰۰', () => {
  const out = mfi(FX.high, FX.low, FX.close, FX.volume, 14);
  assert.equal(out.length, N);
  assert.ok(out.every((v) => v === null), 'همهٔ مقادیر باید null باشند');
  // گاردِ صریحِ باگِ اصلی:
  assert.ok(!out.some((v) => v === 100), 'mfi هرگز نباید روی ابزارِ بدونِ حجم ۱۰۰ بدهد');
});

test('mfi روی کریپتو هنوز کار می‌کند', () => {
  const out = mfi(CRYPTO.high, CRYPTO.low, CRYPTO.close, CRYPTO.volume, 14);
  const real = out.filter((v) => v != null);
  assert.ok(real.length > 0, 'باید مقدارِ واقعی بدهد');
  assert.ok(real.every((v) => v >= 0 && v <= 100), 'در بازهٔ ۰..۱۰۰');
  assert.ok(new Set(real).size > 1, 'نباید ثابت باشد');
});

test('vwap روی فارکس null است — نه close', () => {
  const out = vwap(FX.high, FX.low, FX.close, FX.volume, FX.time);
  assert.ok(out.every((v) => v === null), 'همهٔ مقادیر باید null باشند');
  // گاردِ صریح: نباید بی‌صدا close را برگرداند
  assert.ok(!out.some((v, i) => v === FX.close[i]), 'vwap هرگز نباید به close تنزل کند');
});

test('vwap روی کریپتو هنوز کار می‌کند', () => {
  const out = vwap(CRYPTO.high, CRYPTO.low, CRYPTO.close, CRYPTO.volume, CRYPTO.time);
  assert.ok(out.every((v) => v != null), 'VWAP دورهٔ گرم‌شدن ندارد');
  assert.ok(new Set(out).size > 1);
});

test('obv / cmf روی فارکس null‌اند', () => {
  assert.ok(obv(FX.close, FX.volume).every((v) => v === null));
  assert.ok(cmf(FX.high, FX.low, FX.close, FX.volume, 20).every((v) => v === null));
});

test('avwap روی فارکس شکلِ درست با null برمی‌گرداند', () => {
  const r = avwap(FX.high, FX.low, FX.close, FX.volume, 100, 1);
  // رجیستری r.vwap / r.upper / r.lower می‌خواند — شکل باید حفظ شود وگرنه چارت کرش می‌کند
  assert.ok(Array.isArray(r.vwap) && Array.isArray(r.upper) && Array.isArray(r.lower));
  assert.ok(r.vwap.every((v) => v === null));
});

// ── قراردادِ رجیستری: هیچ اندیکاتورِ حجمی نباید روی فارکس عددِ غیرnull بدهد ──
const VOLUME_KEYS = [
  'mfi', 'cmf', 'obv', 'vwap', 'avwap', 'netVolume', 'eom',
  'volume', 'adline', 'chaikinOsc', 'forceIndex', 'klinger', 'pvt', 'volumeOsc', 'pvo', 'pvi', 'nvi',
];

test('کلِ رجیستریِ حجمی روی فارکس عددِ جعلی نمی‌دهد', () => {
  const failures = [];
  for (const key of VOLUME_KEYS) {
    const def = REGISTRY[key];
    if (!def) { failures.push(`${key}: در رجیستری نیست`); continue; }
    let res;
    try { res = def.calc(FX, { ...def.inputs }); } catch (e) { failures.push(`${key}: throw — ${e.message}`); continue; }
    for (const [field, val] of Object.entries(res || {})) {
      if (!Array.isArray(val)) continue;
      // فیلدهای سبک/پیکربندی — داده نیستند
      if (['guides', 'range', 'zone', 'cloud', 'cloudColors', 'histMode', 'colors'].includes(field)) continue;
      if (field === 'lines') {
        for (const l of val) if (Array.isArray(l?.data) && l.data.some((v) => v != null)) failures.push(`${key}.lines: مقدارِ غیرnull`);
        continue;
      }
      if (val.some((v) => v != null)) failures.push(`${key}.${field}: مقدارِ غیرnull روی ابزارِ بدونِ حجم`);
    }
  }
  assert.deepEqual(failures, [], `اندیکاتورهای حجمی روی فارکس باید null بدهند:\n${failures.join('\n')}`);
});

test('همان رجیستری روی کریپتو دادهٔ واقعی می‌دهد', () => {
  const dead = [];
  for (const key of VOLUME_KEYS) {
    const def = REGISTRY[key];
    if (!def) continue;
    const res = def.calc(CRYPTO, { ...def.inputs });
    const any = Object.entries(res || {}).some(([f, v]) => {
      if (!Array.isArray(v) || ['guides', 'range', 'zone', 'cloud'].includes(f)) return false;
      if (f === 'lines') return v.some((l) => Array.isArray(l?.data) && l.data.some((x) => x != null));
      return v.some((x) => x != null);
    });
    if (!any) dead.push(key);
  }
  assert.deepEqual(dead, [], `این‌ها روی کریپتو هم null دادند — گارد زیادی سخت‌گیر است: ${dead.join(', ')}`);
});
