// Offline guard for RSI divergence detection (detectDivergences).
// Audited-correct in loop #47 (Wilder RSI, regular/hidden pivots) but was untested.
// Craft a regular BULLISH divergence: price makes a LOWER low while RSI makes a HIGHER low
// (steep first decline → low RSI; gentler decline to a lower price → higher RSI).
// RUN: cd frontend/prochart && node test/divergence.test.mjs  (divergence.js has no imports)

import { detectDivergences } from '../../../src/engine/divergence.js';

let pass = 0, fail = 0;
const ok = (n, c, extra) => { if (c) { pass++; console.log('PASS', n); } else { fail++; console.log('FAIL', n, extra != null ? JSON.stringify(extra) : ''); } };
const build = (closes) => closes.map((c, i) => ({ t: i, o: i ? closes[i - 1] : c, h: c + 0.5, l: c - 0.5, c }));

// L1(~90 @ idx16) reached by a STEEP drop → low RSI; L2(~87 @ idx34, lower price) reached
// by a GENTLE decline → higher RSI ⇒ price lower-low + RSI higher-low = regular bull divergence.
const closes = [
  115, 113, 111, 110, 112, 111, 113, 112, 114, 113,
  112, 108, 104, 100, 96, 92, 90, 93, 97, 101,
  104, 106, 104, 102, 100, 98, 96, 95, 93, 91,
  90, 89, 88, 88, 87, 89, 92, 95, 98, 100,
  101, 102, 101, 103,
];
const res = detectDivergences(build(closes));
const bull = res.find((r) => r.dir === 'bull' && r.label.includes('واگراییِ صعودی'));

ok('regular bullish divergence detected (lower price low + higher RSI low)', !!bull, res.map((r) => r.label));
ok('divergence dir=bull', bull ? bull.dir === 'bull' : false, bull && bull.dir);
ok('divergence anchored at the 2nd (lower) low (t=34)', bull ? bull.t === 34 : false, bull && bull.t);

// A steadily-rising series must NOT produce a bullish divergence (no false positive).
const rising = build(Array.from({ length: 44 }, (_, i) => 100 + i));
ok('no divergence on a monotonic uptrend', detectDivergences(rising).every((r) => !r.label.includes('واگراییِ صعودی')), detectDivergences(rising).map((r) => r.label));

ok('empty/short input → []', detectDivergences([]).length === 0 && detectDivergences(build([1, 2, 3])).length === 0);

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
