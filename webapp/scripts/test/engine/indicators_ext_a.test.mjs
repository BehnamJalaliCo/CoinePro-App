// Offline numerical regression guards for the subtle-math indicators in indicators_ext_a.js.
// These were audited correct (loop #61) but had no permanent test. KAMA and McGinley Dynamic
// are self-contained recursive MAs where a future edit could silently break the adaptive
// smoothing; each is compared elementwise to an INDEPENDENT textbook reference.
//
// HOW TO RUN:
//   cd frontend/prochart
//   npx --no-install esbuild src/bazaarnama/indicators_ext_a.js --bundle --format=esm --outfile=test/.ext_a.bundle.mjs
//   node test/indicators_ext_a.test.mjs
// (.ext_a.bundle.mjs is a generated artifact — safe to gitignore.)

import { EXT_REGISTRY_A as R } from '../../../src/engine/indicators_ext_a.js';

let pass = 0, fail = 0;
const near = (a, b, e = 1e-9) => Math.abs(a - b) <= e;
const ck = (name, cond, got, exp) => { if (cond) { pass++; console.log('PASS', name); } else { fail++; console.log('FAIL', name, 'got', JSON.stringify(got), 'exp', JSON.stringify(exp)); } };
const diffs = (a, b) => { let d = 0, t = 0; for (let i = 0; i < b.length; i++) { if (b[i] == null) continue; t++; if (a[i] == null || !near(a[i], b[i])) d++; } return d + '/' + t; };

const C = Array.from({ length: 60 }, (_, i) => 100 + i * 0.4 + Math.sin(i / 5) * 5);
const bars = { close: C, high: C, low: C, open: C, volume: C.map(() => 1) };

// ───────── KAMA (Kaufman Adaptive MA) ─────────
// ER = |price - price[p]| / sum(|price - price[1]|, p); SC = (ER*(fastSC-slowSC)+slowSC)^2;
// kama = prev + SC*(price - prev); seed = price at bar p-1.
{
  const kamaRef = (src, p = 10, fastN = 2, slowN = 30) => { const n = src.length, out = new Array(n).fill(null), fast = 2 / (fastN + 1), slow = 2 / (slowN + 1); let prev = null; for (let i = 0; i < n; i++) { if (i < p) { if (i === p - 1) { prev = src[i]; out[i] = prev; } continue; } const ch = Math.abs(src[i] - src[i - p]); let vol = 0; for (let j = 0; j < p; j++) vol += Math.abs(src[i - j] - src[i - j - 1]); const er = vol ? ch / vol : 0; const sc = Math.pow(er * (fast - slow) + slow, 2); prev = prev + sc * (src[i] - prev); out[i] = prev; } return out; };
  const got = R.kama.calc(bars, R.kama.inputs).line;
  ck('kama == textbook adaptive-MA reference', diffs(got, kamaRef(C, 10, 2, 30)) === '0/51', diffs(got, kamaRef(C, 10, 2, 30)), '0/51');
}

// ───────── McGinley Dynamic ─────────
// md += (src - md) / (p * (src/md)^4); seed = SMA(p).
{
  const mcgRef = (src, p = 14) => { const n = src.length, out = new Array(n).fill(null); let md = null, sum = 0, cnt = 0; for (let i = 0; i < n; i++) { const v = src[i]; if (md == null) { sum += v; cnt++; if (cnt === p) { md = sum / p; out[i] = md; } } else { md = md + (v - md) / (p * Math.pow(v / md, 4) || 1e-9); out[i] = md; } } return out; };
  const got = R.mcginley.calc(bars, R.mcginley.inputs).line;
  ck('mcginley == textbook self-adjusting-MA reference', diffs(got, mcgRef(C, 14)) === '0/47', diffs(got, mcgRef(C, 14)), '0/47');
}

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
