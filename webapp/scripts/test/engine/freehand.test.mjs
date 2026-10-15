// گاردِ رگرسیون — ابزارهای freehand (قلم‌مو/های‌لایتر) باید در چرخهٔ ترسیم وصل باشند.
//
// باگ: `EXT_FREEHAND` در drawtools_ext.js ساخته می‌شد ولی **هرگز import نمی‌شد**.
// و `EXT_NEED` فقط ابزارهای points>=3 را جمع می‌کند، پس brush/highlighter با
// points:-1 در هیچ شاخه‌ای از منطقِ mousedown نمی‌افتادند. نتیجه: دو دکمه در ریل
// که تا ابد هیچ‌چیز نمی‌کشیدند.
//
// اجرا:
//   npx esbuild src/bazaarnama/drawtools_ext.js --bundle --format=esm --outfile=test/.ext.bundle.mjs
//   node --test test/freehand.test.mjs

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { EXT_FREEHAND, EXT_NEED, EXT_REGISTRY, EXT_TOOLS } from '../../../src/engine/drawtools_ext.js';

test('EXT_FREEHAND هر دو ابزارِ freehand را می‌شناسد', () => {
  assert.ok(EXT_FREEHAND.includes('brush'), 'قلم‌مو غایب است');
  assert.ok(EXT_FREEHAND.includes('highlighter'), 'های‌لایتر غایب است');
});

test('ابزارهای freehand points:-1 دارند و در EXT_NEED نیستند', () => {
  // این دقیقاً چرا باگ رخ داد: EXT_NEED فقط points>=3 را می‌گیرد
  for (const id of EXT_FREEHAND) {
    assert.equal(EXT_REGISTRY[id].points, -1, `${id}: points باید -1 باشد`);
    assert.ok(!(id in EXT_NEED), `${id} در EXT_NEED است — منطقِ کلیک-شمار آن را می‌بلعد`);
  }
});

test('هر ابزارِ freehand draw و hit واقعی دارد', () => {
  for (const id of EXT_FREEHAND) {
    const d = EXT_REGISTRY[id];
    assert.equal(typeof d.draw, 'function', `${id}.draw`);
    assert.equal(typeof d.hit, 'function', `${id}.hit`);
    assert.ok(d.label, `${id}.label`);
  }
});

// ── گاردِ اصلی: drawings.js باید واقعاً EXT_FREEHAND را import و استفاده کند ──
const DRAWINGS = readFileSync(new URL('../../../src/engine/drawings.js', import.meta.url), 'utf8');

test('drawings.js عملاً EXT_FREEHAND را import می‌کند', () => {
  assert.match(DRAWINGS, /import\s*\{[^}]*EXT_FREEHAND[^}]*\}\s*from\s*'\.\/drawtools_ext(\.js)?'/,
    'EXT_FREEHAND import نشده — همان باگی که ابزارها را مرده کرده بود');
});

test('drawings.js چرخهٔ کاملِ freehand را دارد: شروع، نمونه‌برداری، ثبت', () => {
  assert.match(DRAWINGS, /EXT_FREEHAND\.includes\(this\.tool\)/, 'mousedown شروعِ freehand را تشخیص نمی‌دهد');
  assert.match(DRAWINGS, /this\.freehand\s*=\s*\{[^}]*pts:\s*\[pt\]/, 'شروع نقطهٔ اول را ذخیره نمی‌کند');
  assert.match(DRAWINGS, /this\.freehand\.pts\.push/, 'mousemove نمونه‌برداری نمی‌کند');
  assert.match(DRAWINGS, /fh\.pts\.length\s*>=\s*2/, 'mouseup ترسیم را ثبت نمی‌کند');
});

test('freehand حین کشیدن پیش‌نمایش می‌شود', () => {
  // بدونِ این، کاربر تا رهاکردنِ ماوس هیچ‌چیز نمی‌بیند و فکر می‌کند ابزار خراب است
  assert.match(DRAWINGS, /this\.tmp\s*\|\|\s*this\.freehand/, 'render() ترسیمِ در حالِ کشیدن را نشان نمی‌دهد');
});

test('نمونه‌برداری آستانهٔ فاصله دارد', () => {
  // بدونِ آستانه، هر پیکسلِ حرکت یک نقطه می‌شود → صدها نقطهٔ هم‌پوشان و رندرِ کند
  assert.match(DRAWINGS, /Math\.hypot\([^)]*\)\s*>=\s*\d/, 'نمونه‌برداری بدونِ آستانهٔ فاصله');
});

test('ابزارهای freehand در ریل ثبت شده‌اند', () => {
  // The new web app's rail lists its tools in src/chart/tools.ts.
  const rail = readFileSync(new URL('../../../src/chart/tools.ts', import.meta.url), 'utf8');
  for (const id of EXT_FREEHAND) {
    assert.ok(rail.includes(`'${id}'`), `${id} در ToolRail نیست — دکمه‌ای برای انتخابش وجود ندارد`);
  }
});

test('draw روی مسیرِ نمونه‌برداری‌شده throw نمی‌کند', () => {
  const calls = [];
  const ctx = new Proxy({}, {
    get: (_, k) => (k === 'lineJoin' || k === 'lineCap' || k === 'strokeStyle' || k === 'lineWidth' || k === 'globalAlpha' || k === 'setLineDash')
      ? (k === 'setLineDash' ? () => {} : undefined)
      : (...a) => { calls.push(k); return undefined; },
    set: () => true,
  });
  const api = { x: (t) => t * 2, y: (p) => p * 3, W: 800, H: 400, candles: [], color: () => '#000' };
  const pts = Array.from({ length: 12 }, (_, i) => ({ t: 1700000000 + i * 60, p: 100 + Math.sin(i) }));
  for (const id of EXT_FREEHAND) {
    assert.doesNotThrow(() => EXT_REGISTRY[id].draw(ctx, { type: id, pts, color: '#f00' }, api), `${id}.draw throw کرد`);
  }
});
