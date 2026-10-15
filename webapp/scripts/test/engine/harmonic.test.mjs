// Offline guard for harmonic pattern detection (detectHarmonics).
// The AD ratio uses AD = |D - A| (retracement-from-A), which loop #47 proved CORRECT
// via brute-force search (see memory prochart-harmonic-ratio-convention). This test pins
// that convention: a crafted bullish Gartley must be detected. If someone "fixes" AD to
// |D - X|, rAD becomes 0.214 (not in Gartley's 0.72–0.84) and detection breaks → test fails.
//
// RUN: cd frontend/prochart && node test/harmonic.test.mjs   (harmonic.js has no imports)

import { detectHarmonics } from '../../../src/engine/harmonic.js';

let pass = 0, fail = 0;
const ok = (n, c, extra) => { if (c) { pass++; console.log('PASS', n); } else { fail++; console.log('FAIL', n, extra != null ? JSON.stringify(extra) : ''); } };

// Build candles from waypoints [idx, price] with linear interpolation; flat candles (h=l=c=o=price).
function build(waypoints, n) {
  const cs = [];
  for (let i = 0; i < n; i++) {
    // find surrounding waypoints
    let a = waypoints[0], b = waypoints[waypoints.length - 1];
    for (let k = 0; k < waypoints.length - 1; k++) { if (i >= waypoints[k][0] && i <= waypoints[k + 1][0]) { a = waypoints[k]; b = waypoints[k + 1]; break; } }
    const span = b[0] - a[0] || 1;
    const p = a[1] + (b[1] - a[1]) * ((i - a[0]) / span);
    cs.push({ t: i, o: p, h: p, l: p, c: p });
  }
  return cs;
}

// Bullish Gartley: X=100(L) A=110(H) B=103.82(L) C=106.91(H) D=102.14(L)
//   AB/XA=0.618, BC/AB=0.5, CD/BC≈1.54, AD/XA=0.786 → matches گارتلی only; D is a Low → 'bull'.
const gartley = build([[0, 107], [4, 100], [11, 110], [18, 103.82], [25, 106.91], [32, 102.14], [39, 108]], 44);
const res = detectHarmonics(gartley);

ok('detectHarmonics returns at least one pattern on a crafted Gartley', res.length >= 1, res.map((r) => r.label));
const g = res.find((r) => r.label.includes('گارتلی'));
ok('the crafted Gartley is detected as گارتلی (guards AD = |D-A| convention #47)', !!g, res.map((r) => r.label));
ok('detected Gartley direction is bull (D is a low)', g ? g.dir === 'bull' : false, g && g.dir);
ok('detected pattern has 5 XABCD points', g ? (g.points && g.points.length === 5) : false, g && g.points && g.points.length);

// Empty / short input → no crash, empty result.
ok('empty input → []', detectHarmonics([]).length === 0);
ok('short input (<40 bars) → []', detectHarmonics(build([[0, 100], [10, 110]], 20)).length === 0);

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
