// Offline numerical-correctness tests for hand-written indicator calc functions.
//
// WHY: Playwright "does it render" only catches crashes, NOT wrong numbers. Any hand-written
// math (indicators, spread/symbol-math, aggregations, precision) must be validated against an
// independent reference + invariants + exact hand-computed values. This suite is that gate.
//
// HOW TO RUN (the calc modules import extensionless './candlePatterns' which raw Node ESM can't
// resolve, so bundle first with the already-present esbuild, then run):
//   cd frontend/prochart
//   npx --no-install esbuild src/bazaarnama/indicators_ext_b.js --bundle --format=esm --outfile=test/.ext_b.bundle.mjs
//   node test/indicators.test.mjs
// (.ext_b.bundle.mjs is a generated artifact — safe to gitignore.)
//
// Add a new block per indicator: write an INDEPENDENT reference impl from the textbook formula,
// compare elementwise to the real calc, then assert invariants + a couple exact values.

import REG from '../../../src/engine/indicators_ext_b.js';

let pass = 0, fail = 0;
const near = (a, b, e = 1e-9) => Math.abs(a - b) <= e;
const ck = (name, cond, got, exp) => { if (cond) { pass++; console.log('PASS', name); } else { fail++; console.log('FAIL', name, 'got', JSON.stringify(got), 'exp', JSON.stringify(exp)); } };

// ───────── Ulcer Index ─────────
// Reference: R_i = 100*(C_i − max(C, last N))/max(C, last N); UI = sqrt(mean(R^2, last N)); na until full window.
const refUlcer = (close, p) => {
  const n = close.length, R = new Array(n).fill(null), out = new Array(n).fill(null);
  for (let k = p - 1; k < n; k++) { let m = -Infinity; for (let j = 0; j < p; j++) m = Math.max(m, close[k - j]); if (m !== 0) R[k] = 100 * (close[k] - m) / m; }
  for (let k = 0; k < n; k++) { let s = 0, ok = true; for (let j = 0; j < p; j++) { const v = R[k - j]; if (v == null) { ok = false; break; } s += v * v; } if (ok) out[k] = Math.sqrt(s / p); }
  return out;
};
{
  const ulcer = REG.ulcer.calc;
  const cl = []; let x = 100; const seed = [3, -2, 5, -7, 1, 4, -6, 2, -3, 8, -1, -4, 6, -2, 3, -9, 5, 1, -2, 7, -5, 2, 4, -8, 3, -1, 6, -3, 2, 5, -7, 4, -2, 1, -6, 3, 8, -4, 2, -5];
  for (const d of seed) { x += d; cl.push(x); }
  const got = ulcer({ close: cl }, { period: 14 }).line, ref = refUlcer(cl, 14);
  let match = true, bad = -1; for (let k = 0; k < cl.length; k++) { const a = got[k], b = ref[k]; if ((a == null) !== (b == null) || (a != null && !near(a, b))) { match = false; bad = k; break; } }
  ck('ulcer == independent reference (elementwise)', match, bad < 0 ? 'ok' : { k: bad, got: got[bad], ref: ref[bad] }, 'match');
  ck('ulcer >= 0', got.filter(v => v != null).every(v => v >= 0), 'ok', '>=0');
  const rise = Array.from({ length: 30 }, (_, k) => 100 + k);
  const u = ulcer({ close: rise }, { period: 14 }).line.filter(v => v != null);
  ck('ulcer = 0 on monotonic rise', u.length > 0 && u.every(v => near(v, 0)), u.slice(0, 3), 'all 0');
}

// ───────── Chandelier Exit ─────────
{
  const chan = REG.chandelier.calc;
  const n = 40, close = [], high = [], low = [];
  for (let k = 0; k < n; k++) { const b = 100 + k; close.push(b); high.push(b + 0.5); low.push(b - 0.5); }
  const p = 5, mult = 3, tr = [];
  for (let k = 0; k < n; k++) tr.push(k === 0 ? high[k] - low[k] : Math.max(high[k] - low[k], Math.abs(high[k] - close[k - 1]), Math.abs(low[k] - close[k - 1])));
  const rma = (a, pp) => { const o = new Array(a.length).fill(null); let s = 0; for (let k = 0; k < a.length; k++) { if (k < pp) { s += a[k]; if (k === pp - 1) o[k] = s / pp; } else o[k] = (o[k - 1] * (pp - 1) + a[k]) / pp; } return o; };
  const atr = rma(tr, p);
  const hh = new Array(n).fill(null); for (let k = p - 1; k < n; k++) { let h = -Infinity; for (let j = 0; j < p; j++) h = Math.max(h, close[k - j]); hh[k] = h; }
  const r = chan({ high, low, close }, { period: p, mult }), last = n - 1;
  ck('chandelier long = HH(close) - mult*ATR', near(r.lines[0].data[last], hh[last] - mult * atr[last]), r.lines[0].data[last], hh[last] - mult * atr[last]);
  ck('chandelier green below close (uptrend)', r.lines[0].data[last] < close[last], r.lines[0].data[last], '<' + close[last]);
  ck('chandelier red null (uptrend)', r.lines[1].data[last] == null, r.lines[1].data[last], null);
  ck('chandelier finite', r.lines[0].data.concat(r.lines[1].data).every(v => v == null || Number.isFinite(v)), 'ok', 'finite');
  // crash → flip to short (red) above price
  const c2 = [], h2 = [], l2 = [];
  for (let k = 0; k < 25; k++) { const b = 100 + k; c2.push(b); h2.push(b + .5); l2.push(b - .5); }
  for (let k = 0; k < 5; k++) { const b = 124 - 8 * (k + 1); c2.push(b); h2.push(b + .5); l2.push(b - .5); }
  const r2 = chan({ high: h2, low: l2, close: c2 }, { period: 5, mult: 3 }), lz = c2.length - 1;
  ck('chandelier flips to red-above on crash', r2.lines[1].data[lz] != null && r2.lines[1].data[lz] > c2[lz], r2.lines[1].data[lz], '>' + c2[lz]);
}

// ═════════ اندیکاتورهای غایب TradingView — batch ۱ ═════════
// reference helperهای مستقل (منطبق با کنوانسیون ext_b: _ema از اولین مقدار seed، خروجی از p-1).
const _refEma = (src, p) => { const out = new Array(src.length).fill(null); const k = 2 / (p + 1); let prev = null; for (let i = 0; i < src.length; i++) { const v = src[i]; if (v == null) { out[i] = prev; continue; } prev = prev == null ? v : v * k + prev * (1 - k); if (i >= p - 1) out[i] = prev; } return out; };
const _refSma = (src, p) => { const out = new Array(src.length).fill(null); let s = 0; for (let i = 0; i < src.length; i++) { const v = src[i] == null ? 0 : src[i]; s += v; if (i >= p) s -= (src[i - p] == null ? 0 : src[i - p]); if (i >= p - 1) out[i] = s / p; } return out; };
const elem = (name, got, ref) => { let ok = true, bad = -1; for (let k = 0; k < ref.length; k++) { const a = got[k], b = ref[k]; if ((a == null) !== (b == null) || (a != null && !near(a, b))) { ok = false; bad = k; break; } } ck(name, ok, bad < 0 ? 'ok' : { k: bad, got: got[bad], ref: ref[bad] }, 'match'); };

// ───────── Aroon Oscillator ─────────
{
  const osc = REG.aroonOsc.calc;
  const high = [], low = []; const hs = [5, 7, 6, 9, 8, 11, 10, 13, 12, 15, 14, 17, 16, 19, 18, 21, 20, 23, 22, 25];
  for (const h of hs) { high.push(h); low.push(h - 2); }
  const p = 14;
  const ref = (() => { const up = new Array(high.length).fill(null), dn = new Array(high.length).fill(null); for (let i = p; i < high.length; i++) { let hi = -Infinity, lo = Infinity, hb = 0, lb = 0; for (let j = 0; j <= p; j++) { if (high[i - j] > hi) { hi = high[i - j]; hb = j; } if (low[i - j] < lo) { lo = low[i - j]; lb = j; } } up[i] = 100 * (p - hb) / p; dn[i] = 100 * (p - lb) / p; } return up.map((u, k) => u == null ? null : u - dn[k]); })();
  const got = osc({ high, low }, { period: p }).line;
  elem('aroonOsc == independent reference', got, ref);
  ck('aroonOsc within [-100,100]', got.filter(v => v != null).every(v => v >= -100 && v <= 100), 'ok', 'bounded');
  // strictly-increasing ⇒ highest = current bar (up=100), lowest = oldest bar (down=0) ⇒ osc=100
  const hi2 = Array.from({ length: 20 }, (_, k) => 100 + k), lo2 = hi2.map(h => h - 1);
  const g2 = osc({ high: hi2, low: lo2 }, { period: 14 }).line;
  ck('aroonOsc = +100 on strictly rising', near(g2[g2.length - 1], 100), g2[g2.length - 1], 100);
}

// ───────── PVO (Percentage Volume Oscillator) ─────────
{
  const pvo = REG.pvo.calc;
  const close = [], volume = []; const vseed = [100, 120, 90, 140, 160, 110, 130, 170, 150, 180, 200, 160, 140, 190, 210, 175, 220, 205, 230, 195, 240, 260, 210, 250, 270, 230, 280, 255, 300, 275];
  for (let k = 0; k < vseed.length; k++) { close.push(50 + k); volume.push(vseed[k]); }
  const inp = { fast: 12, slow: 26, sig: 9 };
  const fast = _refEma(volume, inp.fast), slow = _refEma(volume, inp.slow);
  const refLine = fast.map((f, k) => (f == null || slow[k] == null || !slow[k] ? null : (f - slow[k]) / slow[k] * 100));
  const refSig = _refEma(refLine, inp.sig);
  const refHist = refLine.map((v, k) => (v == null || refSig[k] == null ? null : v - refSig[k]));
  const r = pvo({ close, volume }, inp);
  elem('pvo line == independent reference', r.line, refLine);
  elem('pvo signal == reference', r.signal, refSig);
  ck('pvo hist == line - signal (invariant)', r.hist.every((v, k) => (v == null && refHist[k] == null) || near(v, refHist[k])), 'ok', 'match');
  ck('pvo finite', r.line.concat(r.signal, r.hist).every(v => v == null || Number.isFinite(v)), 'ok', 'finite');
}

// ───────── ADR (Average Daily Range) ─────────
{
  const adr = REG.adr.calc;
  const high = [], low = []; for (let k = 0; k < 20; k++) { const b = 100 + k; high.push(b + 3); low.push(b - 1); } // range ثابت = ۴
  const ref = _refSma(high.map((h, k) => h - low[k]), 14);
  const got = adr({ high, low }, { period: 14 }).line;
  elem('adr == SMA(high-low) reference', got, ref);
  ck('adr = 4 on constant range', near(got[got.length - 1], 4), got[got.length - 1], 4);
  ck('adr >= 0', got.filter(v => v != null).every(v => v >= 0), 'ok', '>=0');
}

// ───────── Median price ─────────
{
  const med = REG.median.calc;
  const close = [7, 3, 5, 9, 1, 8, 2, 6, 4, 10];
  const p = 3;
  const ref = new Array(close.length).fill(null);
  for (let k = p - 1; k < close.length; k++) { const w = [close[k], close[k - 1], close[k - 2]].sort((a, b) => a - b); ref[k] = w[1]; }
  const got = med({ close, open: close, high: close, low: close, volume: close }, { period: p, source: 'close' }).line;
  elem('median(3) == independent reference', got, ref);
  ck('median[2] of [7,3,5]=5', near(got[2], 5), got[2], 5);
  ck('median[4] of [5,9,1]=5', near(got[4], 5), got[4], 5);
  const g4 = med({ close: [4, 8, 2, 6], open: [], high: [], low: [], volume: [] }, { period: 4, source: 'close' }).line;
  ck('median(4) of [4,8,2,6] = (4+6)/2 = 5', near(g4[3], 5), g4[3], 5);
}

// ═════════ اندیکاتورهای غایب TradingView — batch ۲ ═════════
const _refEmaAlpha = (src, period) => { const out = new Array(src.length).fill(null), a = 2 / period; let prev = null; for (let k = 0; k < src.length; k++) { const v = src[k]; if (v == null) { out[k] = prev; continue; } prev = prev == null ? v : prev + a * (v - prev); out[k] = prev; } return out; };
const _refStdev = (src, p) => { const out = new Array(src.length).fill(null); for (let i = p - 1; i < src.length; i++) { let m = 0; for (let j = 0; j < p; j++) m += src[i - j]; m /= p; let s = 0; for (let j = 0; j < p; j++) { const d = src[i - j] - m; s += d * d; } out[i] = Math.sqrt(s / p); } return out; };
const _refCci = (high, low, close, p) => { const n = close.length, tp = new Array(n); for (let k = 0; k < n; k++) tp[k] = (high[k] + low[k] + close[k]) / 3; const ma = _refSma(tp, p), out = new Array(n).fill(null); for (let k = p - 1; k < n; k++) { let md = 0; for (let j = 0; j < p; j++) md += Math.abs(tp[k - j] - ma[k]); md /= p; out[k] = md ? (tp[k] - ma[k]) / (0.015 * md) : 0; } return out; };

// بارهای مصنوعی مشترک
const _bars = (n) => { const high = [], low = [], close = [], volume = []; let x = 100; const seed = [3, -2, 5, -7, 1, 4, -6, 2, -3, 8, -1, -4, 6, -2, 3, -9, 5, 1, -2, 7, -5, 2, 4, -8, 3, -1, 6, -3, 2, 5, -7, 4, -2, 1, -6, 3, 8, -4, 2, -5]; for (let k = 0; k < n; k++) { x += seed[k % seed.length]; close.push(x); high.push(x + 2); low.push(x - 2); volume.push(100 + ((k * 37) % 90)); } return { high, low, close, volume }; };

// ───────── Woodies CCI ─────────
{
  const w = REG.woodiesCci.calc, b = _bars(40);
  const r = w(b, { slow: 14, fast: 6 });
  elem('woodiesCci line == CCI(14) reference', r.line, _refCci(b.high, b.low, b.close, 14));
  elem('woodiesCci signal == CCI(6) reference', r.signal, _refCci(b.high, b.low, b.close, 6));
  ck('woodiesCci finite', r.line.concat(r.signal).every(v => v == null || Number.isFinite(v)), 'ok', 'finite');
}

// ───────── PMO ─────────
{
  const pmo = REG.pmo.calc, b = _bars(60), cl = b.close, n = cl.length;
  const roc = new Array(n).fill(null); for (let k = 1; k < n; k++) if (cl[k - 1]) roc[k] = (cl[k] / cl[k - 1] - 1) * 100 * 10;
  const refLine = _refEmaAlpha(_refEmaAlpha(roc, 35), 20), refSig = _refEma(refLine, 10);
  const r = pmo(b, { len1: 35, len2: 20, sig: 10 });
  elem('pmo line == independent reference', r.line, refLine);
  elem('pmo signal == EMA(pmo,10) reference', r.signal, refSig);
}

// ───────── PVI / NVI ─────────
{
  const pvi = REG.pvi.calc, nvi = REG.nvi.calc;
  const close = [100, 102, 101, 105, 103, 108], volume = [10, 20, 15, 25, 12, 30]; // up/down volume pattern
  // reference PVI: changes only when volume[k] > volume[k-1]
  const refPVI = [1000]; for (let k = 1; k < close.length; k++) { let v = refPVI[k - 1]; if (volume[k] > volume[k - 1]) v += (close[k] - close[k - 1]) / close[k - 1] * v; refPVI.push(v); }
  const refNVI = [1000]; for (let k = 1; k < close.length; k++) { let v = refNVI[k - 1]; if (volume[k] < volume[k - 1]) v += (close[k] - close[k - 1]) / close[k - 1] * v; refNVI.push(v); }
  elem('pvi == cumulative reference (up-volume days)', pvi({ close, volume }).line, refPVI);
  elem('nvi == cumulative reference (down-volume days)', nvi({ close, volume }).line, refNVI);
  ck('pvi seed = 1000', near(pvi({ close, volume }).line[0], 1000), pvi({ close, volume }).line[0], 1000);
}

// ───────── RVI (Relative Volatility Index) ─────────
{
  const rvi = REG.rviVol.calc, b = _bars(40), src = b.close, n = src.length;
  const sd = _refStdev(src, 10), up = new Array(n).fill(null), dn = new Array(n).fill(null);
  for (let k = 1; k < n; k++) { if (sd[k] == null) continue; if (src[k] > src[k - 1]) { up[k] = sd[k]; dn[k] = 0; } else if (src[k] < src[k - 1]) { up[k] = 0; dn[k] = sd[k]; } else { up[k] = 0; dn[k] = 0; } }
  const ua = _refEma(up, 14), da = _refEma(dn, 14), ref = new Array(n).fill(null);
  for (let k = 0; k < n; k++) if (ua[k] != null && da[k] != null) { const s = ua[k] + da[k]; ref[k] = s ? 100 * ua[k] / s : 0; }
  const got = rvi(b, { length: 14, stdevLen: 10, source: 'close' }).line;
  elem('rviVol == independent reference', got, ref);
  ck('rviVol within [0,100]', got.filter(v => v != null).every(v => v >= 0 && v <= 100), 'ok', 'bounded');
}

// ───────── Typical Price (HLC3) & Weighted Close (HLCC4) — Loop #44 ─────────
{
  const high = [12, 15, 20], low = [8, 9, 10], close = [11, 12, 18];
  const tp = REG.typicalPrice.calc({ high, low, close }).line;
  const wc = REG.weightedClose.calc({ high, low, close }).line;
  ck('typicalPrice == (H+L+C)/3', tp.every((v, k) => near(v, (high[k] + low[k] + close[k]) / 3)), tp, 'HLC3');
  ck('weightedClose == (H+L+2C)/4', wc.every((v, k) => near(v, (high[k] + low[k] + 2 * close[k]) / 4)), wc, 'HLCC4');
}

// ───────── Volume Oscillator — Loop #40 ─────────
{
  const b = _bars(40), vol = b.volume;
  const short = _refEma(vol, 5), long = _refEma(vol, 10);
  const ref = short.map((s, k) => (s == null || long[k] == null || !long[k] ? null : (s - long[k]) / long[k] * 100));
  elem('volumeOsc == (shortEMA-longEMA)/longEMA*100 reference', REG.volumeOsc.calc(b, { shortLen: 5, longLen: 10 }).line, ref);
  const nz = REG.volumeOsc.calc({ high: [1, 2, 3], low: [1, 2, 3], close: [1, 2, 3], volume: [0, 0, 0] }, { shortLen: 5, longLen: 10 }).line;
  ck('volumeOsc null without volume', nz.every(v => v == null), nz, 'all null');
}

// ───────── Linear Regression Channel — Loop #39 ─────────
{
  const n = 120, close = Array.from({ length: n }, (_, k) => 2 * k + 10), high = close.map(v => v + 0.1), low = close.map(v => v - 0.1);
  const r = REG.linRegChannel.calc({ high, low, close }, { length: 100, mult: 2, source: 'close' });
  ck('linRegChannel warmup null (<length-1)', r.basis.slice(0, 99).every(v => v == null), 'ok', 'null');
  ck('linRegChannel basis==src on perfect linear', near(r.basis[119], close[119], 1e-6), r.basis[119], close[119]);
  ck('linRegChannel sd~0 on linear => upper==basis', near(r.upper[119], r.basis[119], 1e-6), r.upper[119], r.basis[119]);
  ck('linRegChannel bands symmetric', near(r.upper[119] - r.basis[119], r.basis[119] - r.lower[119]), 'ok', 'symmetric');
}

// ───────── Volatility Stop — Loop #41 ─────────
{
  const n = 40, close = Array.from({ length: n }, (_, k) => 100 + 2 * k), high = close.map(v => v + 1), low = close.map(v => v - 1);
  const r = REG.volatilityStop.calc({ high, low, close }, { period: 14, mult: 2 }), up = r.lines[0].data, dn = r.lines[1].data, last = n - 1;
  ck('volatilityStop green stop below price (uptrend)', up[last] != null && up[last] < close[last], up[last], '<' + close[last]);
  ck('volatilityStop red null in uptrend', dn[last] == null, dn[last], null);
  ck('volatilityStop green stop non-decreasing (uptrend)', up.filter(v => v != null).every((v, i, a) => i === 0 || v >= a[i - 1] - 1e-9), 'ok', 'monotone');
}

// ───────── warmup-contamination fix: EMA signals seed on first real value — Loop #45 ─────────
// (regression guard: the map(null->0) anti-pattern used to drag the first ~p signal outputs toward 0)
{
  const b = _bars(120);
  for (const key of ['smiErgodic', 'trix', 'klinger']) {
    const r = REG[key].calc(b, REG[key].inputs), line = r.line, sig = r.signal;
    const fl = line.findIndex(v => v != null), fs = sig.findIndex(v => v != null);
    ck(`${key} signal seeds on first real line value (no null->0 drag)`, fs >= fl && near(sig[fs], line[fs], 1e-9), { line: line[fs], sig: sig[fs] }, 'equal');
  }
}

// ───────── ROC / Momentum / BOP — added coverage (Loop #49) ─────────
{
  const b = _bars(40), close = b.close;
  elem('roc == (c-c[p])/c[p]*100 reference', REG.roc.calc(b, { period: 9, source: 'close' }).line,
    close.map((_, k) => (k >= 9 ? (close[k] - close[k - 9]) / close[k - 9] * 100 : null)));
  elem('mom == c-c[p] reference', REG.mom.calc(b, { period: 10, source: 'close' }).line,
    close.map((_, k) => (k >= 10 ? close[k] - close[k - 10] : null)));
  // BOP needs open; smooth=1 => raw (c-o)/(h-l), zero-range guard -> 0
  const n = 20, open = [], high = [], low = [], cl = [];
  for (let k = 0; k < n; k++) { const x = 100 + Math.sin(k) * 5; open.push(x); high.push(x + 2); low.push(x - 2); cl.push(x + Math.cos(k)); }
  elem('bop(smooth=1) == (c-o)/(h-l) reference', REG.bop.calc({ open, high, low, close: cl }, { smooth: 1 }).line,
    cl.map((c, k) => { const r = high[k] - low[k]; return r ? (c - open[k]) / r : 0; }));
}

// ───────── Volume family: Force Index / PVT / A-D line / Chaikin Osc — added coverage (Loop #53) ─────────
{
  const b = _bars(40), { high, low, close, volume } = b, n = close.length;
  // Force Index: raw=(c-c[-1])*vol, EMA(period), null@0
  const raw = new Array(n).fill(null); for (let k = 1; k < n; k++) raw[k] = (close[k] - close[k - 1]) * volume[k];
  elem('forceIndex == EMA((c-c[-1])*vol) reference', REG.forceIndex.calc(b, { period: 13 }).line, _refEma(raw, 13).map((v, k) => (k < 1 ? null : v)));
  // PVT: cumulative ((c-c[-1])/c[-1])*vol, seed 0
  const pvt = new Array(n).fill(0); for (let k = 1; k < n; k++) pvt[k] = pvt[k - 1] + ((close[k] - close[k - 1]) / close[k - 1]) * volume[k];
  elem('pvt == cumulative price-volume-trend reference', REG.pvt.calc(b, {}).line, pvt);
  // A/D line: cumulative MFM*vol, MFM=((c-l)-(h-c))/(h-l)
  const adl = new Array(n).fill(null); let acc = 0;
  for (let k = 0; k < n; k++) { const r = high[k] - low[k]; const mfm = r ? ((close[k] - low[k]) - (high[k] - close[k])) / r : 0; acc += mfm * volume[k]; adl[k] = acc; }
  elem('adline == cumulative MFM*vol reference', REG.adline.calc(b, {}).line, adl);
  // Chaikin Oscillator: EMA(ADL,fast) - EMA(ADL,slow)
  const ef = _refEma(adl, 3), es = _refEma(adl, 10);
  elem('chaikinOsc == EMA(ADL,3)-EMA(ADL,10) reference', REG.chaikinOsc.calc(b, { fast: 3, slow: 10 }).line, adl.map((_, k) => (ef[k] != null && es[k] != null ? ef[k] - es[k] : null)));
}

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail) process.exit(1);
