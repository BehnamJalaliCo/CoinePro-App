// Offline guards for candlestick-pattern detection (detectCandlePatterns) + gap detection (detectGaps).
// Both were untested. Craft canonical candles and assert the classic pattern is detected.
// RUN: cd frontend/prochart && node test/candlePatterns.test.mjs  (both files have no imports)

import { detectCandlePatterns } from '../../../src/engine/candlePatterns.js';
import { detectGaps } from '../../../src/engine/gaps.js';

let pass = 0, fail = 0;
const ok = (n, c, extra) => { if (c) { pass++; console.log('PASS', n); } else { fail++; console.log('FAIL', n, extra != null ? JSON.stringify(extra) : ''); } };
const K = (t, o, h, l, c) => ({ t, o, h, l, c, v: 1 });

// 0-3: tiny bodies (small avgBody). 4: bear. 5: bull engulfing. 6: doji. 7: bull marubozu.
const cs = [
  K(0, 100, 100.15, 99.95, 100.1),
  K(1, 100, 100.15, 99.95, 100.1),
  K(2, 100, 100.15, 99.95, 100.1),
  K(3, 100, 100.15, 99.95, 100.1),
  K(4, 105, 105.2, 99.8, 100),        // bear body 5
  K(5, 99, 106.5, 98.5, 106),         // bull engulfs #4 (o<=p.c, c>=p.o, body 7>5)
  K(6, 100, 101, 99, 100.05),         // doji (body 0.05 « range 2)
  K(7, 100, 112, 100, 112),           // bull marubozu (body 12, no wicks)
];
const res = detectCandlePatterns(cs);
const labels = res.map((r) => r.label);

ok('bullish engulfing detected (پوششیِ صعودی)', labels.includes('پوششیِ صعودی'), labels);
ok('doji detected (دوجی)', labels.some((l) => l.includes('دوجی')), labels);
ok('bullish marubozu detected (ماروبوزوی صعودی)', labels.includes('ماروبوزوی صعودی'), labels);
// engulfing must be bull-directional
const eng = res.find((r) => r.label === 'پوششیِ صعودی');
ok('engulfing dir=bull', eng ? eng.dir === 'bull' : false, eng && eng.dir);
ok('empty/short input → []', detectCandlePatterns([]).length === 0 && detectCandlePatterns([K(0, 1, 1, 1, 1)]).length === 0);

// ── gaps: a 6% up-gap (prev close 100 → next open 106) must be detected ──
const gapSeries = [K(0, 99, 101, 98, 100), K(1, 106, 108, 105, 107), K(2, 107, 108, 106, 107.5)];
const gaps = detectGaps(gapSeries, 0.1);
ok('up-gap detected (open 106 vs prev close 100)', Array.isArray(gaps) && gaps.length >= 1, gaps);
ok('no false gap on a continuous series', detectGaps([K(0, 100, 101, 99, 100), K(1, 100, 101, 99, 100.2)], 0.1).length === 0);

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
