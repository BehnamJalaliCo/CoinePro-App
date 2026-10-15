// گارد رگرسیون — اندیکاتورهای چند-تایم‌فریمی نباید آینده را ببینند.
//
// باگ: `_expandA` مقدار کندل HTF را — که از close آخرین کندل سطل می‌آمد — به
// **همه‌ی** کندل‌های آن سطل نسبت می‌داد. یعنی در کندل i*f، اندیکاتور کلوزی را
// می‌دانست که f-1 کندل بعد رخ می‌دهد. این بک‌تست را غلط سودده نشان می‌دهد و
// خطرناک‌ترین کلاس باگ در یک پلتفرم معاملاتی است.
//
// روش تست (استاندارد طلایی): سری را روی کل داده حساب کن، بعد روی یک **پیشوند**.
// اگر lookahead نباشد، مقادیر پیشوند باید بایت‌به‌بایت با همان بازه در سری کامل
// یکی باشند — چون کندل t فقط از ۰..t ساخته شده. اگر باشد، واگرا می‌شوند.
//
// اجرا:
//   npx esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node --test test/mtf_lookahead.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { REGISTRY } from '../../../src/engine/indicators.js';

const N = 300;
const mkCandles = (n) => {
  const c = { open: [], high: [], low: [], close: [], volume: [], time: [] };
  let p = 100;
  for (let k = 0; k < n; k++) {
    // حرکت قطعی ولی ناهموار — تا کلوز هر کندل با کندل‌های قبلش قابل حدس نباشد
    p += Math.sin(k / 3) * 3 + Math.cos(k / 7) * 2;
    c.open.push(p); c.close.push(p + Math.sin(k) * 1.5);
    c.high.push(p + 4); c.low.push(p - 4);
    c.volume.push(1000 + (k % 13) * 50);
    c.time.push(1700000000 + k * 60);
  }
  return c;
};
const FULL = mkCandles(N);
const slice = (c, n) => ({
  open: c.open.slice(0, n), high: c.high.slice(0, n), low: c.low.slice(0, n),
  close: c.close.slice(0, n), volume: c.volume.slice(0, n), time: c.time.slice(0, n),
});

const MTF_KEYS = ['mtfEma', 'mtfRsi'];

const lineOf = (res) => res.line ?? res.lines?.[0]?.data ?? Object.values(res).find(Array.isArray);

test('اندیکاتورهای MTF در رجیستری هستند', () => {
  for (const k of MTF_KEYS) assert.ok(REGISTRY[k], `${k} غایب است`);
});

// ── تست اصلی ──
test('MTF آینده نمی‌بیند: پیشوند باید با سری کامل یکی باشد', () => {
  const violations = [];
  for (const key of MTF_KEYS) {
    const def = REGISTRY[key];
    const full = lineOf(def.calc(FULL, { ...def.inputs }));

    // در چند نقطه‌ی برش تست کن — از جمله وسط یک سطل HTF
    for (const cut of [137, 200, 251]) {
      const partial = lineOf(def.calc(slice(FULL, cut), { ...def.inputs }));
      for (let t = 0; t < cut; t++) {
        const a = full[t], b = partial[t];
        if (a == null && b == null) continue;
        if (a == null || b == null || Math.abs(a - b) > 1e-9) {
          violations.push(`${key}: کندل ${t} با برش ${cut} → کامل=${a} پیشوند=${b}`);
          break; // یک نمونه از هر ترکیب کافی است
        }
      }
    }
  }
  assert.deepEqual(violations, [], `سوگیری lookahead — مقدار با دیدن آینده عوض می‌شود:\n${violations.join('\n')}`);
});

test('کندل‌های اول MTF تا بسته‌شدن اولین کندل HTF null‌اند', () => {
  for (const key of MTF_KEYS) {
    const def = REGISTRY[key];
    const out = lineOf(def.calc(FULL, { ...def.inputs }));
    assert.equal(out[0], null, `${key}: کندل ۰ نباید مقدار داشته باشد — هیچ کندل HTFای هنوز بسته نشده`);
  }
});

test('MTF هنوز داده‌ی واقعی تولید می‌کند — گارد بیش از حد سخت‌گیر نیست', () => {
  for (const key of MTF_KEYS) {
    const def = REGISTRY[key];
    const { period, factor } = def.inputs;
    const out = lineOf(def.calc(FULL, { ...def.inputs }));
    const real = out.filter((v) => v != null);

    // گرم‌شدن مورد انتظار: period کندل HTF لازم است، هر کدام factor کندل پایه،
    // به‌علاوه‌ی یک کندل HTF تأخیر برای بسته‌شدن. mtfEma با period=50 و factor=4
    // یعنی ۲۰۰ کندل پایه — پس دقیقاً ۱۰۰ مقدار از ۳۰۰. این ذات اندیکاتور است، نه گارد.
    const expectedWarmup = period * factor;
    assert.ok(real.length >= N - expectedWarmup - factor,
      `${key}: ${real.length} مقدار — کمتر از حد انتظار ${N - expectedWarmup - factor}`);
    assert.ok(real.length > 20, `${key}: فقط ${real.length} مقدار — عملاً بی‌فایده`);
    assert.ok(new Set(real).size > 1, `${key}: خروجی ثابت است`);
  }
});

test('تأخیر گسترش دقیقاً یک کندل HTF است — نه بیشتر، نه کمتر', () => {
  // اگر تأخیر خیلی زیاد شود اندیکاتور بی‌جهت کند می‌شود؛ اگر کم شود lookahead برمی‌گردد.
  const def = REGISTRY.mtfRsi;
  const { period, factor } = def.inputs;
  const out = lineOf(def.calc(FULL, { ...def.inputs }));
  const first = out.findIndex((v) => v != null);
  // RSI(period) اولین مقدارش در کندل HTF شماره‌ی period است → کندل پایه‌ی (period+1)*factor
  assert.equal(first, (period + 1) * factor,
    `اولین مقدار در ${first} است، انتظار ${(period + 1) * factor}`);
});
