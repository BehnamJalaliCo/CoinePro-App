// Offline numerical regression guards for the CORE chart indicators in indicators.js
// (the ones every user plots). Focus: the correctness fixes made in loops #59/#60 —
// DEMA/TEMA nested-EMA warmup (the map(null->0) fake-zero-seed anti-pattern) and the
// SuperTrend final-band ratchet. Each is compared elementwise to an INDEPENDENT textbook
// reference, so a regression (re-introducing the fake-zero seed or inverted ratchet) fails here.
//
// HOW TO RUN (indicators.js imports nothing exotic; bundle then run):
//   cd frontend/prochart
//   npx --no-install esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node test/indicators_main.test.mjs
// (.ind.bundle.mjs is a generated artifact — safe to gitignore.)

import { dema, tema, supertrend, ema } from '../../../src/engine/indicators.js';

let pass = 0, fail = 0;
const near = (a, b, e = 1e-9) => Math.abs(a - b) <= e;
const ck = (name, cond, got, exp) => { if (cond) { pass++; console.log('PASS', name); } else { fail++; console.log('FAIL', name, 'got', JSON.stringify(got), 'exp', JSON.stringify(exp)); } };

// synthetic trending+wavy closes (long enough to expose warmup contamination)
const N = 140, C = Array.from({ length: N }, (_, i) => 100 + i * 0.5 + Math.sin(i / 7) * 6);
const H = C.map((v) => v + 2), L = C.map((v) => v - 2);

// independent textbook Wilder ATR (matches indicators.js atr)
const atrRef = (h, l, c, p) => { const n = c.length, tr = new Array(n); for (let i = 0; i < n; i++) tr[i] = i === 0 ? (h[i] - l[i]) : Math.max(h[i] - l[i], Math.abs(h[i] - c[i - 1]), Math.abs(l[i] - c[i - 1])); const o = new Array(n).fill(null); let s = 0; for (let i = 0; i < n; i++) { if (i < p) { s += tr[i]; if (i === p - 1) o[i] = s / p; } else o[i] = (o[i - 1] * (p - 1) + tr[i]) / p; } return o; };

// count elementwise diffs vs reference (over reference's non-null bars)
const diffs = (a, b) => { let d = 0, t = 0; for (let i = 0; i < b.length; i++) { if (b[i] == null) continue; t++; if (a[i] == null || !near(a[i], b[i])) d++; } return d + '/' + t; };

// ───────── DEMA / TEMA (loop #60): nested EMA must seed on first real value ─────────
// Reference uses the module's own ema (null-skipping, seed-on-first-real) with NO fake-zero map.
{
  const e1 = ema(C, 20), e2 = ema(e1, 20), e3 = ema(e2, 20);
  const demaRef = C.map((_, i) => (e1[i] != null && e2[i] != null ? 2 * e1[i] - e2[i] : null));
  const temaRef = C.map((_, i) => (e1[i] != null && e2[i] != null && e3[i] != null ? 3 * e1[i] - 3 * e2[i] + e3[i] : null));
  ck('dema == textbook nested-EMA (no fake-zero warmup)', diffs(dema(C, 20), demaRef) === '0/121', diffs(dema(C, 20), demaRef), '0/121');
  ck('tema == textbook nested-EMA (no fake-zero warmup)', diffs(tema(C, 20), temaRef) === '0/121', diffs(tema(C, 20), temaRef), '0/121');
  // guard against regression: first emitted DEMA must be near price, not dragged toward 0
  const d = dema(C, 20), fd = d.findIndex((v) => v != null);
  ck('dema first value near price (not zero-dragged)', d[fd] > C[fd] - 20 && d[fd] < C[fd] + 20, d[fd], '≈' + C[fd]);
}

// ───────── SuperTrend (loop #59): final-band ratchet ─────────
{
  const stRef = (h, l, c, p, m) => { const n = c.length, a = atrRef(h, l, c, p), st = new Array(n).fill(null), dir = new Array(n).fill(null); let pU = null, pL = null, pST = null; for (let i = 0; i < n; i++) { if (a[i] == null) continue; const mid = (h[i] + l[i]) / 2, bU = mid + m * a[i], bL = mid - m * a[i]; const fu = (pU == null) ? bU : (((bU < pU) || (c[i - 1] > pU)) ? bU : pU); const fl = (pL == null) ? bL : (((bL > pL) || (c[i - 1] < pL)) ? bL : pL); let d, stv; if (pST == null) { d = 1; stv = fl; } else if (pST === pU) { if (c[i] <= fu) { d = -1; stv = fu; } else { d = 1; stv = fl; } } else { if (c[i] >= fl) { d = 1; stv = fl; } else { d = -1; stv = fu; } } st[i] = stv; dir[i] = d; pU = fu; pL = fl; pST = stv; } return { line: st, dir }; };
  // whipsaw series to exercise the ratchet + flips
  const M = 60, wh = [], wl = [], wc = []; let x = 100; const seed = [3, 5, -4, -6, 2, 7, -3, -8, 4, 1, -5, 6, -2, 9, -7, 3, -4, 5, -9, 2, 6, -3, -5, 8, -2, 4, -6, 1, 7, -4];
  for (let i = 0; i < M; i++) { x += seed[i % seed.length] * 0.7; wc.push(x); wh.push(x + 2); wl.push(x - 2); }
  const got = supertrend(wh, wl, wc, 10, 3), ref = stRef(wh, wl, wc, 10, 3);
  ck('supertrend line == textbook (ratchet correct)', diffs(got.line, ref.line) === '0/51', diffs(got.line, ref.line), '0/51');
  let dd = 0, t = 0; for (let i = 0; i < M; i++) { if (ref.dir[i] == null) continue; t++; if (got.trend[i] !== ref.dir[i]) dd++; }
  ck('supertrend direction == textbook', dd === 0, dd + '/' + t, '0');
}

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
