// Offline numerical test for the indicator price-SOURCE resolver (resolveSrc in indicators.js).
// WHY: source math (hl2/hlc3/ohlc4/hlcc4) is hand-written; a typo silently feeds wrong data
// into every indicator that uses it. Verify each source against an independent textbook formula.
//
// HOW TO RUN:
//   cd frontend/prochart
//   npx --no-install esbuild src/bazaarnama/indicators.js --bundle --format=esm --outfile=test/.ind.bundle.mjs
//   node test/source.test.mjs
// (.ind.bundle.mjs is a generated artifact — safe to gitignore.)

import { resolveSrc, SOURCE_OPTS, adx, adxDI } from '../../../src/engine/indicators.js';

let pass = 0, fail = 0;
const near = (a, b, e = 1e-12) => Math.abs(a - b) <= e;
const ck = (name, cond, got, exp) => { if (cond) { pass++; console.log('PASS', name); } else { fail++; console.log('FAIL', name, 'got', JSON.stringify(got), 'exp', JSON.stringify(exp)); } };

// deterministic OHLC (no Math.random) — mixed shapes incl. down bars so weighted sources differ
const c = {
  open:  [10.00, 11.20, 10.50, 12.30, 9.80],
  high:  [10.80, 11.50, 11.00, 12.60, 10.10],
  low:   [ 9.60, 10.90, 10.10, 11.80, 9.40],
  close: [10.40, 11.10, 10.90, 12.00, 9.55],
};
const n = c.close.length;

// independent textbook references
const ref = {
  close: c.close,
  open: c.open,
  high: c.high,
  low: c.low,
  hl2:   c.high.map((h, i) => (h + c.low[i]) / 2),
  hlc3:  c.high.map((h, i) => (h + c.low[i] + c.close[i]) / 3),
  ohlc4: c.high.map((h, i) => (c.open[i] + h + c.low[i] + c.close[i]) / 4),
  hlcc4: c.high.map((h, i) => (h + c.low[i] + c.close[i] + c.close[i]) / 4), // (H+L+2C)/4
};

// every advertised source must resolve elementwise to its reference
for (const name of SOURCE_OPTS) {
  const got = resolveSrc(c, name);
  let ok = got.length === n;
  for (let i = 0; ok && i < n; i++) ok = near(got[i], ref[name][i]);
  ck(`resolveSrc('${name}') == reference`, ok, got, ref[name]);
}

// HLCC4 exact hand-computed value on bar 0: (10.80 + 9.60 + 2*10.40)/4 = 41.2/4 = 10.30
ck('hlcc4[0] exact = 10.30', near(resolveSrc(c, 'hlcc4')[0], 10.30), resolveSrc(c, 'hlcc4')[0], 10.30);
// HLCC4 weights close double vs ohlc4 (which weights open): on a strong-close bar hlcc4 > ohlc4 when close>open-avg
ck('hlcc4 != ohlc4 when open!=close', !near(resolveSrc(c, 'hlcc4')[0], resolveSrc(c, 'ohlc4')[0]), 'differ', 'differ');
// unknown source falls back to close (no regression for saved indicators)
{ const got = resolveSrc(c, 'nonsense'); let ok = true; for (let i = 0; i < n; i++) ok = ok && near(got[i], c.close[i]); ck('unknown source -> close fallback', ok, got, c.close); }
// SOURCE_OPTS includes hlcc4 (dropdown parity with TV)
ck('SOURCE_OPTS has hlcc4', SOURCE_OPTS.includes('hlcc4'), SOURCE_OPTS, 'includes hlcc4');

// ── adxDI must reproduce adx() exactly (its .adx array) + expose directional +DI/−DI ── #265
{
  const N = 80, H = [], L = [], C = [];
  for (let i = 0; i < N; i++) { const base = 100 + i * 0.7 + Math.sin(i / 4) * 2; H.push(base + 1); L.push(base - 1); C.push(base + 0.3); }
  const legacy = adx(H, L, C, 14);         // existing ADX-only
  const full = adxDI(H, L, C, 14);          // new: {adx, plusDI, minusDI}
  let same = full && full.adx.length === legacy.length;
  for (let i = 0; same && i < N; i++) { const a = full.adx[i], b = legacy[i]; if ((a == null) !== (b == null) || (a != null && !near(a, b, 1e-9))) same = false; }
  ck('adxDI.adx == adx() elementwise', same, 'match', 'match');
  // strong uptrend ⇒ at the last valid bar +DI should exceed −DI (directional sanity)
  const nn = N - 1;
  ck('adxDI: +DI > -DI on uptrend end', full.plusDI[nn] != null && full.minusDI[nn] != null && full.plusDI[nn] > full.minusDI[nn], { p: full.plusDI[nn], m: full.minusDI[nn] }, '+DI>-DI');
  // short input ⇒ null (no crash)
  ck('adxDI short input -> null', adxDI([1,2], [0,1], [1,1], 14) === null, 'null', 'null');
}

console.log(`\n${pass} passed, ${fail} failed`);
if (fail) process.exit(1);
