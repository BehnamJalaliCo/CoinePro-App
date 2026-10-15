// بازارنما — کتابخانه‌ی اندیکاتورهای سمت کلاینت (سری‌محور، آرایه‌ها هم‌طول کندل‌ها).
// ورودی: closes/highs/lows/... آرایه‌ی عدد. خروجی: آرایه‌ی عدد یا null (دوره‌ی گرم‌شدن).

// افزونه‌های فصل ۵ (PRO_CHART_BUILD_SPEC) — ماژول‌های drop-in، در انتهای فایل با REGISTRY ادغام می‌شوند.
import { EXT_REGISTRY_A } from './indicators_ext_a.js';
import { EXT_REGISTRY_B } from './indicators_ext_b.js';

// فارکس/فلز/شاخص حجم ندارند. اندیکاتور حجمی روی چنین ابزاری باید null بدهد،
// نه عددی که شبیه نتیجه‌ی واقعی است — کاربر روی آن معامله می‌کند.
// پیش‌تر mfi ثابت ۱۰۰ (اشباع خرید همیشگی) و vwap بی‌صدا close برمی‌گرداند.
export const hasVolume = (vols) => {
  if (!Array.isArray(vols)) return false;
  for (let i = 0; i < vols.length; i++) { const v = vols[i]; if (v != null && v > 0) return true; }
  return false;
};
const nulls = (n) => new Array(n).fill(null);

export const sma = (src, p) => {
  const out = new Array(src.length).fill(null);
  let sum = 0;
  for (let i = 0; i < src.length; i++) {
    sum += src[i];
    if (i >= p) sum -= src[i - p];
    if (i >= p - 1) out[i] = sum / p;
  }
  return out;
};
// SMA آگاه به null — برای MA روی خروجی اندیکاتور دیگر که warmup null دارد (مثل RSI-based MA TV):
// فقط مقادیر متناهی پنجره را میانگین می‌گیرد و تا پُرشدن کامل پنجره null می‌دهد. #330
export const smaNull = (arr, p) => { const out = new Array(arr.length).fill(null); let sum = 0, cnt = 0; for (let i = 0; i < arr.length; i++) { const v = arr[i]; if (v != null && Number.isFinite(v)) { sum += v; cnt++; } const j = i - p; if (j >= 0) { const w = arr[j]; if (w != null && Number.isFinite(w)) { sum -= w; cnt--; } } if (cnt === p) out[i] = sum / p; } return out; };

export const ema = (src, p) => {
  const out = new Array(src.length).fill(null);
  const k = 2 / (p + 1);
  let prev = null;
  for (let i = 0; i < src.length; i++) {
    const v = src[i];
    if (v == null) continue;
    prev = prev == null ? v : v * k + prev * (1 - k);
    if (i >= p - 1) out[i] = prev;
  }
  return out;
};

export const wma = (src, p) => {
  const out = new Array(src.length).fill(null);
  const denom = (p * (p + 1)) / 2;
  for (let i = p - 1; i < src.length; i++) {
    let s = 0;
    for (let j = 0; j < p; j++) s += src[i - j] * (p - j);
    out[i] = s / denom;
  }
  return out;
};

// HMA = WMA(2*WMA(n/2) - WMA(n), sqrt(n))
export const hma = (src, p) => {
  const half = Math.max(1, Math.floor(p / 2));
  const sq = Math.max(1, Math.round(Math.sqrt(p)));
  const w1 = wma(src, half);
  const w2 = wma(src, p);
  const diff = src.map((_, i) => (w1[i] != null && w2[i] != null ? 2 * w1[i] - w2[i] : null));
  const filled = diff.map((v) => (v == null ? 0 : v));
  const h = wma(filled, sq);
  // فقط وقتی پنجره‌ی کامل sq از diff واقعی باشد خروجی بده — تا صفرهای جعلی warmup لبه‌ی چپ را خراب نکنند (~√p بار اول).
  return h.map((v, i) => { for (let j = 0; j < sq; j++) if (i - j < 0 || diff[i - j] == null) return null; return v; });
};

export const rsi = (closes, p = 14) => {
  const out = new Array(closes.length).fill(null);
  let ag = 0, al = 0;
  for (let i = 1; i < closes.length; i++) {
    const ch = closes[i] - closes[i - 1];
    const g = Math.max(ch, 0), l = Math.max(-ch, 0);
    if (i <= p) { ag += g; al += l; if (i === p) { ag /= p; al /= p; out[i] = 100 - 100 / (1 + ag / (al || 1e-9)); } }
    else { ag = (ag * (p - 1) + g) / p; al = (al * (p - 1) + l) / p; out[i] = 100 - 100 / (1 + ag / (al || 1e-9)); }
  }
  return out;
};

export const macd = (closes, fast = 12, slow = 26, sig = 9) => {
  const ef = ema(closes, fast), es = ema(closes, slow);
  const line = closes.map((_, i) => (ef[i] != null && es[i] != null ? ef[i] - es[i] : null));
  const signal = ema(line, sig).map((v, i) => (line[i] == null ? null : v));
  const hist = line.map((v, i) => (v != null && signal[i] != null ? v - signal[i] : null));
  return { macd: line, signal, hist };
};

export const bollinger = (closes, p = 20, mult = 2) => {
  const basis = sma(closes, p);
  const upper = new Array(closes.length).fill(null);
  const lower = new Array(closes.length).fill(null);
  for (let i = p - 1; i < closes.length; i++) {
    let s = 0; for (let j = 0; j < p; j++) { const d = closes[i - j] - basis[i]; s += d * d; }
    const sd = Math.sqrt(s / p);
    upper[i] = basis[i] + mult * sd; lower[i] = basis[i] - mult * sd;
  }
  return { basis, upper, lower };
};

export const atr = (highs, lows, closes, p = 14) => {
  const tr = new Array(closes.length).fill(null);
  for (let i = 0; i < closes.length; i++) {
    if (i === 0) { tr[i] = highs[i] - lows[i]; continue; }
    tr[i] = Math.max(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]));
  }
  const out = new Array(closes.length).fill(null);
  let prev = null;
  for (let i = 0; i < closes.length; i++) {
    if (i < p) { if (i === p - 1) { let s = 0; for (let j = 0; j < p; j++) s += tr[j]; prev = s / p; out[i] = prev; } continue; }
    prev = (prev * (p - 1) + tr[i]) / p; out[i] = prev;
  }
  return out;
};

export const stoch = (highs, lows, closes, p = 14, d = 3) => {
  const k = new Array(closes.length).fill(null);
  for (let i = p - 1; i < closes.length; i++) {
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, highs[i - j]); ll = Math.min(ll, lows[i - j]); }
    k[i] = hh === ll ? 50 : ((closes[i] - ll) / (hh - ll)) * 100;
  }
  const dd = smaNull(k, d);
  return { k, d: dd };
};

// VWAP — پیش‌فرض TradingView سشن‌محور است (هر سشن معاملاتی/روز ریست می‌شود).
// اگر آرایه‌ی times (یونیکس‌ثانیه) داده شود، با تغییر روز UTC انباشت ریست می‌گردد؛
// بدون times رفتار تجمعی قبلی حفظ می‌شود (سازگاری عقب‌رو).
export const vwap = (highs, lows, closes, vols, times) => {
  if (!hasVolume(vols)) return nulls(closes.length); // VWAP بدون حجم وجود ندارد (پیش‌تر بی‌صدا close می‌داد)
  const out = new Array(closes.length).fill(null);
  let pv = 0, vv = 0, prevDay = null;
  for (let i = 0; i < closes.length; i++) {
    if (times && times[i] != null) {
      const day = Math.floor(times[i] / 86400); // سطل روز UTC
      if (prevDay !== null && day !== prevDay) { pv = 0; vv = 0; } // سشن جدید → ریست
      prevDay = day;
    }
    const tp = (highs[i] + lows[i] + closes[i]) / 3;
    pv += tp * (vols[i] || 0); vv += vols[i] || 0;
    out[i] = vv ? pv / vv : closes[i];
  }
  return out;
};

// Anchored VWAP — VWAP از anchor N کندل پیش + باندهای انحراف معیار (±mult·σ) — نسخه‌ی مؤسساتی
export const avwap = (highs, lows, closes, vols, anchorBars = 100, mult = 1) => {
  if (!hasVolume(vols)) return { vwap: nulls(closes.length), upper: nulls(closes.length), lower: nulls(closes.length) };
  const n = closes.length;
  const out = new Array(n).fill(null), up = new Array(n).fill(null), dn = new Array(n).fill(null);
  const start = Math.max(0, n - (anchorBars || n));
  let pv = 0, vv = 0, pv2 = 0;
  for (let i = start; i < n; i++) {
    const tp = (highs[i] + lows[i] + closes[i]) / 3;
    const v = vols[i] || 0;
    pv += tp * v; vv += v; pv2 += tp * tp * v;
    const vw = vv ? pv / vv : closes[i];
    out[i] = vw;
    const variance = vv ? Math.max(0, pv2 / vv - vw * vw) : 0;
    const sd = Math.sqrt(variance);
    up[i] = vw + (mult || 1) * sd; dn[i] = vw - (mult || 1) * sd;
  }
  return { vwap: out, upper: up, lower: dn };
};

// ── اندیکاتورهای افزوده (پاریتی TradingView) ──
const _trueRange = (h, l, c) => { const n = c.length, tr = new Array(n).fill(null); for (let i = 0; i < n; i++) { if (i === 0) { tr[i] = h[i] - l[i]; continue; } tr[i] = Math.max(h[i] - l[i], Math.abs(h[i] - c[i - 1]), Math.abs(l[i] - c[i - 1])); } return tr; };
const _sma = (arr, p) => { const n = arr.length, out = new Array(n).fill(null); let s = 0; for (let i = 0; i < n; i++) { s += arr[i] || 0; if (i >= p) s -= arr[i - p] || 0; if (i >= p - 1) out[i] = s / p; } return out; };

// Choppiness Index (0..100): >61.8 رنج، <38.2 ترند
export const choppiness = (h, l, c, p = 14) => {
  const n = c.length, tr = _trueRange(h, l, c), out = new Array(n).fill(null);
  for (let i = p - 1; i < n; i++) {
    let atrSum = 0, hh = -Infinity, ll = Infinity;
    for (let j = i - p + 1; j <= i; j++) { atrSum += tr[j] || 0; hh = Math.max(hh, h[j]); ll = Math.min(ll, l[j]); }
    const rng = hh - ll;
    out[i] = rng > 0 ? 100 * Math.log10(atrSum / rng) / Math.log10(p) : null;
  }
  return out;
};
// Vortex Indicator → {plus, minus}
export const vortex = (h, l, c, p = 14) => {
  const n = c.length, tr = _trueRange(h, l, c), vmP = new Array(n).fill(0), vmM = new Array(n).fill(0);
  for (let i = 1; i < n; i++) { vmP[i] = Math.abs(h[i] - l[i - 1]); vmM[i] = Math.abs(l[i] - h[i - 1]); }
  const plus = new Array(n).fill(null), minus = new Array(n).fill(null);
  for (let i = p; i < n; i++) {
    let sTR = 0, sP = 0, sM = 0;
    for (let j = i - p + 1; j <= i; j++) { sTR += tr[j] || 0; sP += vmP[j]; sM += vmM[j]; }
    if (sTR > 0) { plus[i] = sP / sTR; minus[i] = sM / sTR; }
  }
  return { plus, minus };
};
// Detrended Price Oscillator
export const dpo = (c, p = 20) => { const sma = _sma(c, p), n = c.length, out = new Array(n).fill(null), k = Math.floor(p / 2) + 1; for (let i = 0; i < n; i++) { if (sma[i] != null && i - k >= 0) out[i] = c[i] - sma[i]; } return out; };
// Balance of Power
export const bop = (o, h, l, c) => c.map((cl, i) => { const rng = h[i] - l[i]; return rng > 0 ? (cl - o[i]) / rng : 0; });
// Ease of Movement
export const eom = (h, l, vol, p = 14) => {
  if (!hasVolume(vol)) return nulls(h.length);
  const n = h.length, raw = new Array(n).fill(null);
  for (let i = 1; i < n; i++) { const dm = (h[i] + l[i]) / 2 - (h[i - 1] + l[i - 1]) / 2; const br = (vol[i] || 1) / 100000000 / Math.max(h[i] - l[i], 1e-9); raw[i] = br > 0 ? dm / br : 0; }
  return _sma(raw.map((x) => x == null ? 0 : x), p);
};

const _ema2 = (arr, p) => { const n = arr.length, out = new Array(n).fill(null), k = 2 / (p + 1); let prev = null; for (let i = 0; i < n; i++) { const v = arr[i]; if (v == null) { out[i] = prev; continue; } prev = prev == null ? v : v * k + prev * (1 - k); out[i] = prev; } return out; };
const _wma2 = (arr, p) => { const n = arr.length, out = new Array(n).fill(null), dw = p * (p + 1) / 2; for (let i = p - 1; i < n; i++) { let s = 0, ok = true; for (let j = 0; j < p; j++) { const v = arr[i - j]; if (v == null) { ok = false; break; } s += v * (p - j); } if (ok) out[i] = s / dw; } return out; };
const _roc2 = (arr, p) => arr.map((v, i) => (i >= p && arr[i - p]) ? (v - arr[i - p]) / arr[i - p] * 100 : null);
const _smma2 = (arr, p) => { const n = arr.length, out = new Array(n).fill(null); let prev = null; for (let i = 0; i < n; i++) { const v = arr[i]; if (v == null) { out[i] = prev; continue; } if (prev == null) { if (i >= p - 1) { let s = 0; for (let j = 0; j < p; j++) s += arr[i - j]; prev = s / p; out[i] = prev; } } else { prev = (prev * (p - 1) + v) / p; out[i] = prev; } } return out; };

export const elderRay = (h, l, c, p = 13) => { const e = _ema2(c, p); return { bull: h.map((x, i) => e[i] == null ? null : x - e[i]), bear: l.map((x, i) => e[i] == null ? null : x - e[i]) }; };
export const chandeKroll = (h, l, c, p = 10, x = 1, q = 9) => { const atrv = _sma(_trueRange(h, l, c), p); const n = c.length, hs = new Array(n).fill(null), ls = new Array(n).fill(null); for (let i = p - 1; i < n; i++) { let hh = -Infinity, ll = Infinity; for (let j = i - p + 1; j <= i; j++) { hh = Math.max(hh, h[j]); ll = Math.min(ll, l[j]); } hs[i] = hh - x * (atrv[i] || 0); ls[i] = ll + x * (atrv[i] || 0); } const hf = new Array(n).fill(null), lf = new Array(n).fill(null); for (let i = q - 1; i < n; i++) { let hh = -Infinity, ll = Infinity; for (let j = i - q + 1; j <= i; j++) { if (hs[j] != null) hh = Math.max(hh, hs[j]); if (ls[j] != null) ll = Math.min(ll, ls[j]); } hf[i] = hh === -Infinity ? null : hh; lf[i] = ll === Infinity ? null : ll; } return { high: hf, low: lf }; };
export const massIndex = (h, l, p = 9, sum = 25) => { const n = h.length, range = h.map((x, i) => x - l[i]); const e1 = _ema2(range, p), e2 = _ema2(e1, p); const ratio = e1.map((x, i) => (x != null && e2[i]) ? x / e2[i] : null); const out = new Array(n).fill(null); for (let i = sum - 1; i < n; i++) { let s = 0, ok = true; for (let j = 0; j < sum; j++) { const v = ratio[i - j]; if (v == null) { ok = false; break; } s += v; } if (ok) out[i] = s; } return out; };
export const coppock = (c, a = 14, b = 11, w = 10) => { const r = _roc2(c, a), r2 = _roc2(c, b); const sum = r.map((x, i) => (x != null && r2[i] != null) ? x + r2[i] : null); return _wma2(sum, w); };
export const kst = (c) => { const r1 = _sma(_roc2(c, 10), 10), r2 = _sma(_roc2(c, 15), 10), r3 = _sma(_roc2(c, 20), 10), r4 = _sma(_roc2(c, 30), 15); const n = c.length, line = new Array(n).fill(null); for (let i = 0; i < n; i++) { if (r1[i] != null && r2[i] != null && r3[i] != null && r4[i] != null) line[i] = r1[i] + 2 * r2[i] + 3 * r3[i] + 4 * r4[i]; } return { line, signal: _sma(line.map((x) => x == null ? 0 : x), 9) }; };
export const alligator = (h, l) => { const med = h.map((x, i) => (x + l[i]) / 2); return { jaw: _smma2(med, 13), teeth: _smma2(med, 8), lips: _smma2(med, 5) }; };
export const rvi = (o, h, l, c) => { const n = c.length, co = c.map((x, i) => x - o[i]), hl = h.map((x, i) => x - l[i]); const swma = (a) => { const out = new Array(n).fill(null); for (let i = 3; i < n; i++) { if ([a[i], a[i - 1], a[i - 2], a[i - 3]].some((v) => v == null)) continue; out[i] = (a[i] + 2 * a[i - 1] + 2 * a[i - 2] + a[i - 3]) / 6; } return out; }; const num = swma(co), den = swma(hl); const line = num.map((x, i) => (x != null && den[i]) ? x / den[i] : null); const sig = new Array(n).fill(null); for (let i = 3; i < n; i++) { if ([line[i], line[i - 1], line[i - 2], line[i - 3]].some((v) => v == null)) continue; sig[i] = (line[i] + 2 * line[i - 1] + 2 * line[i - 2] + line[i - 3]) / 6; } return { line, signal: sig }; };
export const bbWidth = (c, p = 20, mult = 2) => { const ma = _sma(c, p), n = c.length, out = new Array(n).fill(null); for (let i = p - 1; i < n; i++) { let s = 0; for (let j = 0; j < p; j++) { const d = c[i - j] - ma[i]; s += d * d; } const sd = Math.sqrt(s / p); out[i] = ma[i] ? (2 * mult * sd) / ma[i] * 100 : null; } return out; };
export const stc = (c, fast = 23, slow = 50, cycle = 10) => { const ef = _ema2(c, fast), es = _ema2(c, slow); const macd = ef.map((x, i) => (x != null && es[i] != null) ? x - es[i] : null); const n = c.length; const stoch = (src, p) => { const out = new Array(n).fill(null); for (let i = p - 1; i < n; i++) { let hh = -Infinity, ll = Infinity, ok = true; for (let j = 0; j < p; j++) { const v = src[i - j]; if (v == null) { ok = false; break; } hh = Math.max(hh, v); ll = Math.min(ll, v); } if (ok) out[i] = hh > ll ? (src[i] - ll) / (hh - ll) * 100 : 0; } return out; }; const d1 = _ema2(stoch(macd, cycle), 3); const d2 = _ema2(stoch(d1, cycle), 3); return d2; };
export const netVolume = (o, c, v) => (hasVolume(v) ? c.map((cl, i) => (v[i] || 0) * (cl >= o[i] ? 1 : -1)) : nulls(c.length));
export const stdErrBands = (c, p = 21, mult = 2) => { const n = c.length, mid = new Array(n).fill(null), up = new Array(n).fill(null), dn = new Array(n).fill(null); for (let i = p - 1; i < n; i++) { let sx = 0, sy = 0, sxx = 0, sxy = 0; for (let j = 0; j < p; j++) { const x = j, y = c[i - p + 1 + j]; sx += x; sy += y; sxx += x * x; sxy += x * y; } const b = (p * sxy - sx * sy) / (p * sxx - sx * sx || 1); const a = (sy - b * sx) / p; const yhat = a + b * (p - 1); let se = 0; for (let j = 0; j < p; j++) { const x = j, y = c[i - p + 1 + j]; const e = y - (a + b * x); se += e * e; } const stderr = Math.sqrt(se / Math.max(1, p - 2)); mid[i] = yhat; up[i] = yhat + mult * stderr; dn[i] = yhat - mult * stderr; } return { mid, up, dn }; };
// Accelerator Oscillator = AO − SMA(AO, 5)، که AO = SMA(median,5) − SMA(median,34).
//
// پیش‌تر O(n²) بود: `_sma(med, 34)` و `_sma(ao, 5)` **داخل** `.map()` صدا زده می‌شدند،
// یعنی به‌ازای هر کندل کل سری از نو محاسبه می‌شد. ۵۰۰۰ کندل ≈ ۷۰۰ms و با
// اسکرول بی‌کران تاریخچه، فریز حتمی. حالا هر SMA یک‌بار حساب می‌شود → O(n).
export const accelerator = (h, l) => {
  const med = h.map((x, i) => (x + l[i]) / 2);
  const s5 = _sma(med, 5), s34 = _sma(med, 34);
  const ao = s5.map((v, i) => (v != null && s34[i] != null ? v - s34[i] : null));
  const aoSig = _sma(ao.map((x) => (x == null ? 0 : x)), 5);
  return ao.map((v, i) => (v != null && aoSig[i] != null ? v - aoSig[i] : null));
};
export const chaikinVol = (h, l, p = 10) => { const hl = h.map((x, i) => x - l[i]); const e = _ema2(hl, p); return e.map((v, i) => (v != null && e[i - p] != null && e[i - p] !== 0) ? (v - e[i - p]) / e[i - p] * 100 : null); };
// MTF: resample کندل‌های موجود به تایم‌فریم بالاتر (factor)، محاسبه، و گسترش مقادیر به طول اصلی
const _resampleC = (c, f) => { f = Math.max(1, f | 0); const o = [], h = [], l = [], cl = [], v = []; for (let i = 0; i < c.close.length; i += f) { const end = Math.min(i + f, c.close.length); let mh = -Infinity, ml = Infinity, sv = 0; for (let j = i; j < end; j++) { if (c.high[j] > mh) mh = c.high[j]; if (c.low[j] < ml) ml = c.low[j]; sv += c.volume[j] || 0; } o.push(c.open[i]); h.push(mh); l.push(ml); cl.push(c.close[end - 1]); v.push(sv); } return { open: o, high: h, low: l, close: cl, volume: v }; };
// گسترش سری HTF به طول تایم‌فریم پایه — **بدون سوگیری lookahead**.
//
// باگ پیشین: سطل i (کندل‌های پایه‌ی i*f .. i*f+f-1) مقدارش از close[end-1] یعنی
// **آخرین** کندل سطل می‌آمد، ولی به **همه‌ی** کندل‌های سطل نسبت داده می‌شد. یعنی در
// کندل i*f، اندیکاتور کلوزی را می‌دانست که f-1 کندل بعد اتفاق می‌افتد. بک‌تست را
// غلط سودده نشان می‌داد.
//
// درست (مثل request.security با lookahead=off در TradingView): کندل HTF شماره‌ی i
// در انتهای کندل پایه‌ی i*f+f-1 بسته می‌شود، پس فقط از کندل (i+1)*f به بعد
// در دسترس است. تا آن موقع کندل HTF در حال شکل‌گیری است و مقدار قبلی دیده می‌شود.
// نتیجه: f کندل اول خروجی null‌اند — چون هنوز هیچ کندل HTFای بسته نشده.
const _expandA = (arr, f, len) => {
  f = Math.max(1, f | 0);
  const out = new Array(len).fill(null);
  for (let i = 0; i < arr.length; i++) {
    const from = (i + 1) * f;                    // اولین کندل پایه پس از بسته‌شدن سطل i
    const to = Math.min(from + f, len);
    for (let idx = from; idx < to; idx++) out[idx] = arr[i];
  }
  return out;
};

// SuperTrend → { trend:[-1/1], line:[price] }
export const supertrend = (highs, lows, closes, p = 10, mult = 3) => {
  const a = atr(highs, lows, closes, p);
  const line = new Array(closes.length).fill(null);
  const trend = new Array(closes.length).fill(null);
  let up = null, dn = null, dir = 1;
  for (let i = 0; i < closes.length; i++) {
    if (a[i] == null) continue;
    const mid = (highs[i] + lows[i]) / 2;
    let ub = mid + mult * a[i], lb = mid - mult * a[i];
    // رَچت باند نهایی استاندارد SuperTrend: باند تا وقتی قیمت داخلش است سفت می‌ماند، فقط با شکست ریست می‌شود.
    // (قبلاً منطق معکوس بود ⇒ باند هر بار دوباره پهن می‌شد و خط SuperTrend به‌شدت غلط بود — هم‌کلاس باگ نمااسکریپت #۵۸.)
    if (up != null) ub = (ub < up || closes[i - 1] > up) ? ub : up;
    if (dn != null) lb = (lb > dn || closes[i - 1] < dn) ? lb : dn;
    if (dir === 1 && closes[i] < (dn ?? lb)) dir = -1;
    else if (dir === -1 && closes[i] > (up ?? ub)) dir = 1;
    up = ub; dn = lb;
    trend[i] = dir; line[i] = dir === 1 ? lb : ub;
  }
  return { trend, line };
};

// CCI
// هسته‌ی CCI روی هر سری منبع (پیش‌فرض TV: hlc3) — انحراف نسبت به SMA تقسیم بر ۰٫۰۱۵×میانگین‌انحراف.
const _cciCore = (src, p) => {
  const m = sma(src, p);
  const out = new Array(src.length).fill(null);
  for (let i = p - 1; i < src.length; i++) {
    let md = 0; for (let j = 0; j < p; j++) md += Math.abs(src[i - j] - m[i]);
    md /= p; out[i] = md ? (src[i] - m[i]) / (0.015 * md) : 0;
  }
  return out;
};
export const cci = (highs, lows, closes, p = 20) => _cciCore(closes.map((_, i) => (highs[i] + lows[i] + closes[i]) / 3), p);

// Williams %R
export const williamsR = (highs, lows, closes, p = 14) => {
  const out = new Array(closes.length).fill(null);
  for (let i = p - 1; i < closes.length; i++) {
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, highs[i - j]); ll = Math.min(ll, lows[i - j]); }
    out[i] = hh === ll ? -50 : ((hh - closes[i]) / (hh - ll)) * -100;
  }
  return out;
};

// OBV
export const obv = (closes, vols) => {
  if (!hasVolume(vols)) return nulls(closes.length);
  const out = new Array(closes.length).fill(null);
  let v = 0; out[0] = 0;
  for (let i = 1; i < closes.length; i++) { v += closes[i] > closes[i - 1] ? (vols[i] || 0) : closes[i] < closes[i - 1] ? -(vols[i] || 0) : 0; out[i] = v; }
  return out;
};

// Ichimoku → چند خط
// ایچیموکو کینکو هیو — با جابه‌جایی درست.
//
// باگ پیشین: Senkou A/B بدون جابه‌جایی و روی همان کندل رسم می‌شدند، و Chikou اصلاً
// وجود نداشت. یعنی ابر روی قیمت می‌نشست به‌جای اینکه جلوتر از آن برجسته شود — دقیقاً
// وارونه‌ی کارکردش. ابر (کومو) باید **۲۶ کندل جلوتر** باشد؛ کل فلسفه‌ی ایچیموکو
// پیش‌بینی حمایت/مقاومت آینده است.
//
// تعریف استاندارد:
//   Tenkan  = (بیشینه‌ی ۹ سقف + کمینه‌ی ۹ کف) / 2      — روی کندل جاری
//   Kijun   = (بیشینه‌ی ۲۶ سقف + کمینه‌ی ۲۶ کف) / 2     — روی کندل جاری
//   Senkou A = (Tenkan + Kijun) / 2                  — **۲۶ کندل جلوتر**
//   Senkou B = (بیشینه‌ی ۵۲ سقف + کمینه‌ی ۵۲ کف) / 2    — **۲۶ کندل جلوتر**
//   Chikou   = close                                 — **۲۶ کندل عقب‌تر**
//
// محدودیت شناخته‌شده: چارت فعلاً کندل آینده ندارد، پس آن ۲۶ کندل برجسته‌ی فراتر از
// آخرین کندل رسم نمی‌شوند. همه‌ی کندل‌های تاریخی درست هم‌ترازند. برجستگی لبه‌ی راست
// نیازمند گسترش محور زمان است (کار سطح چارت، نه اندیکاتور).
//
// ⚠️ Chikou ذاتاً کلوز آینده را در موقعیت گذشته نشان می‌دهد (استاندارد است و TV هم
// همین می‌کند). برای **نمایش** درست است؛ یک استراتژی هرگز نباید از آن سیگنال بگیرد.
export const ichimoku = (highs, lows, closes, t = 9, k = 26, b = 52) => {
  const n = highs.length;
  const mid = (p) => highs.map((_, i) => {
    if (i < p - 1) return null;
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, highs[i - j]); ll = Math.min(ll, lows[i - j]); }
    return (hh + ll) / 2;
  });
  const tenkan = mid(t), kijun = mid(k), b52 = mid(b);

  // Senkou A/B: مقدار محاسبه‌شده در کندل i، در کندل i+k ظاهر می‌شود
  const spanA = new Array(n).fill(null);
  const spanB = new Array(n).fill(null);
  for (let i = 0; i < n; i++) {
    const src = i - k;
    if (src < 0) continue;
    if (tenkan[src] != null && kijun[src] != null) spanA[i] = (tenkan[src] + kijun[src]) / 2;
    if (b52[src] != null) spanB[i] = b52[src];
  }

  // Chikou: کلوز کندل i+k در موقعیت i — یعنی ۲۶ کندل آخر خالی می‌مانند
  const chikou = new Array(n).fill(null);
  for (let i = 0; i < n - k; i++) chikou[i] = closes[i + k];

  return { tenkan, kijun, spanA, spanB, chikou };
};

// Donchian channel
export const donchian = (highs, lows, p = 20) => {
  const up = new Array(highs.length).fill(null), lo = new Array(highs.length).fill(null), mid = new Array(highs.length).fill(null);
  for (let i = p - 1; i < highs.length; i++) {
    let hh = -Infinity, ll = Infinity;
    for (let j = 0; j < p; j++) { hh = Math.max(hh, highs[i - j]); ll = Math.min(ll, lows[i - j]); }
    up[i] = hh; lo[i] = ll; mid[i] = (hh + ll) / 2;
  }
  return { upper: up, basis: mid, lower: lo };
};

// Keltner channel
export const keltner = (highs, lows, closes, p = 20, mult = 2) => {
  const basis = ema(closes, p), a = atr(highs, lows, closes, 10);
  return { basis, upper: basis.map((v, i) => (v != null && a[i] != null ? v + mult * a[i] : null)), lower: basis.map((v, i) => (v != null && a[i] != null ? v - mult * a[i] : null)) };
};

// ADX
export const adx = (highs, lows, closes, p = 14) => {
  const n = closes.length, tr = new Array(n).fill(0), pdm = new Array(n).fill(0), ndm = new Array(n).fill(0);
  for (let i = 1; i < n; i++) {
    tr[i] = Math.max(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]));
    const up = highs[i] - highs[i - 1], dn = lows[i - 1] - lows[i];
    pdm[i] = up > dn && up > 0 ? up : 0; ndm[i] = dn > up && dn > 0 ? dn : 0;
  }
  const sm = (a) => { const o = new Array(n).fill(null); let s = 0; for (let i = 1; i <= p; i++) s += a[i]; o[p] = s; for (let i = p + 1; i < n; i++) { s = s - s / p + a[i]; o[i] = s; } return o; };
  const str = sm(tr), spd = sm(pdm), snd = sm(ndm), out = new Array(n).fill(null);
  let adxPrev = null, dxs = [];
  for (let i = p; i < n; i++) {
    if (str[i] == null || !str[i]) continue;
    const pdi = 100 * spd[i] / str[i], ndi = 100 * snd[i] / str[i];
    const dx = (pdi + ndi) ? 100 * Math.abs(pdi - ndi) / (pdi + ndi) : 0;
    dxs.push(dx);
    if (dxs.length === p) { adxPrev = dxs.reduce((x, y) => x + y, 0) / p; out[i] = adxPrev; }
    else if (adxPrev != null) { adxPrev = (adxPrev * (p - 1) + dx) / p; out[i] = adxPrev; }
  }
  return out;
};

// DEMA / TEMA
// نسخه‌ی ADX که +DI و −DI را هم برمی‌گرداند (برای قاعده‌ی crossover-محور Technical Rating). #265
// دقیقاً همان محاسبه‌ی adx بالا؛ فقط pdi/ndi را هم ذخیره می‌کند (تست elementwise تضمین یکسانی adx می‌دهد).
export const adxDI = (highs, lows, closes, p = 14) => {
  const n = closes.length;
  if (n < p + 2) return null;
  const tr = new Array(n).fill(0), pdm = new Array(n).fill(0), ndm = new Array(n).fill(0);
  for (let i = 1; i < n; i++) {
    tr[i] = Math.max(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]));
    const up = highs[i] - highs[i - 1], dn = lows[i - 1] - lows[i];
    pdm[i] = up > dn && up > 0 ? up : 0; ndm[i] = dn > up && dn > 0 ? dn : 0;
  }
  const sm = (a) => { const o = new Array(n).fill(null); let s = 0; for (let i = 1; i <= p; i++) s += a[i]; o[p] = s; for (let i = p + 1; i < n; i++) { s = s - s / p + a[i]; o[i] = s; } return o; };
  const str = sm(tr), spd = sm(pdm), snd = sm(ndm);
  const plusDI = new Array(n).fill(null), minusDI = new Array(n).fill(null), adxArr = new Array(n).fill(null);
  let adxPrev = null, dxs = [];
  for (let i = p; i < n; i++) {
    if (str[i] == null || !str[i]) continue;
    const pdi = 100 * spd[i] / str[i], ndi = 100 * snd[i] / str[i];
    plusDI[i] = pdi; minusDI[i] = ndi;
    const dx = (pdi + ndi) ? 100 * Math.abs(pdi - ndi) / (pdi + ndi) : 0;
    dxs.push(dx);
    if (dxs.length === p) { adxPrev = dxs.reduce((x, y) => x + y, 0) / p; adxArr[i] = adxPrev; }
    else if (adxPrev != null) { adxPrev = (adxPrev * (p - 1) + dx) / p; adxArr[i] = adxPrev; }
  }
  return { adx: adxArr, plusDI, minusDI };
};

export const dema = (closes, p = 20) => { const e = ema(closes, p), e2 = ema(e, p); return closes.map((_, i) => (e[i] != null && e2[i] != null ? 2 * e[i] - e2[i] : null)); }; // ema خودش null-skip/seed-on-first-real دارد؛ map(null→0) قبلی EMA تودرتو را با صفر جعلی seed می‌کرد ⇒ ~۹۰ بار اول خروجی به‌شدت غلط.
export const tema = (closes, p = 20) => { const e1 = ema(closes, p), e2 = ema(e1, p), e3 = ema(e2, p); return closes.map((_, i) => (e1[i] != null && e2[i] != null && e3[i] != null ? 3 * e1[i] - 3 * e2[i] + e3[i] : null)); }; // مثل dema: EMA تودرتو بدون map(null→0) تا روی اولین مقدار واقعی seed شود.

// VWMA
export const vwma = (closes, vols, p = 20) => { const pv = closes.map((c, i) => c * (vols[i] || 0)); const spv = sma(pv, p), sv = sma(vols.map((v) => v || 0), p); return closes.map((_, i) => (spv[i] != null && sv[i] ? spv[i] / sv[i] : null)); };

// Parabolic SAR
export const psar = (highs, lows, start = 0.02, inc = 0.02, max = 0.2) => {
  const n = highs.length, out = new Array(n).fill(null);
  let trend = 1, af = start, ep = highs[0], sar = lows[0];
  for (let i = 1; i < n; i++) {
    sar = sar + af * (ep - sar);
    if (trend > 0) { if (lows[i] < sar) { trend = -1; sar = ep; ep = lows[i]; af = start; } else if (highs[i] > ep) { ep = highs[i]; af = Math.min(max, af + inc); } }
    else { if (highs[i] > sar) { trend = 1; sar = ep; ep = highs[i]; af = start; } else if (lows[i] < ep) { ep = lows[i]; af = Math.min(max, af + inc); } }
    out[i] = sar;
  }
  return out;
};

// Aroon → { up, down }
export const aroon = (highs, lows, p = 14) => {
  const up = new Array(highs.length).fill(null), down = new Array(highs.length).fill(null);
  for (let i = p; i < highs.length; i++) {
    let hi = -Infinity, lo = Infinity, hb = 0, lb = 0;
    for (let j = 0; j <= p; j++) { if (highs[i - j] > hi) { hi = highs[i - j]; hb = j; } if (lows[i - j] < lo) { lo = lows[i - j]; lb = j; } }
    up[i] = 100 * (p - hb) / p; down[i] = 100 * (p - lb) / p;
  }
  return { up, down };
};

// MFI
export const mfi = (highs, lows, closes, vols, p = 14) => {
  if (!hasVolume(vols)) return nulls(closes.length); // بدون حجم MFI بی‌معناست (پیش‌تر ثابت ۱۰۰ می‌داد)
  const n = closes.length, tp = new Array(n), pos = new Array(n).fill(0), neg = new Array(n).fill(0);
  for (let i = 0; i < n; i++) { tp[i] = (highs[i] + lows[i] + closes[i]) / 3; const rmf = tp[i] * (vols[i] || 0); if (i > 0) { if (tp[i] > tp[i - 1]) pos[i] = rmf; else if (tp[i] < tp[i - 1]) neg[i] = rmf; } }
  const sp = sma(pos, p).map((v) => (v == null ? null : v * p)), sn = sma(neg, p).map((v) => (v == null ? null : v * p));
  const out = new Array(n).fill(null);
  // clamp: MFI طبق تعریف ۰..۱۰۰ است؛ بدون این، خطای ممیز شناور ۱۰۰.۰۰۰۰۰۰۰۰۰۰۰۰۰۱ می‌داد
  for (let i = 0; i < n; i++) if (sp[i] != null) out[i] = Math.min(100, Math.max(0, sn[i] ? 100 - 100 / (1 + sp[i] / sn[i]) : 100));
  return out;
};

// CMF
export const cmf = (highs, lows, closes, vols, p = 20) => {
  if (!hasVolume(vols)) return nulls(closes.length);
  const n = closes.length, mfv = new Array(n);
  for (let i = 0; i < n; i++) { const rng = highs[i] - lows[i]; mfv[i] = (rng ? ((closes[i] - lows[i]) - (highs[i] - closes[i])) / rng : 0) * (vols[i] || 0); }
  const sm = sma(mfv, p).map((v) => (v == null ? null : v * p)), sv = sma(vols.map((v) => v || 0), p).map((v) => (v == null ? null : v * p));
  return closes.map((_, i) => (sm[i] != null && sv[i] ? sm[i] / sv[i] : null));
};

// Stochastic RSI → { k, d }
export const stochRsi = (closes, p = 14, k = 3, d = 3) => {
  const r = rsi(closes, p), n = closes.length, raw = new Array(n).fill(null);
  for (let i = p - 1; i < n; i++) { let hh = -Infinity, ll = Infinity, ok = 0; for (let j = 0; j < p; j++) { const v = r[i - j]; if (v == null) continue; ok = 1; hh = Math.max(hh, v); ll = Math.min(ll, v); } if (ok && r[i] != null) raw[i] = hh === ll ? 0 : 100 * (r[i] - ll) / (hh - ll); }
  const ks = smaNull(raw, k);
  const ds = smaNull(ks, d);
  return { k: ks, d: ds };
};

// Awesome Oscillator
export const ao = (highs, lows) => { const mp = highs.map((_, i) => (highs[i] + lows[i]) / 2); const f = sma(mp, 5), s = sma(mp, 34); return mp.map((_, i) => (f[i] != null && s[i] != null ? f[i] - s[i] : null)); };

// TSI
export const tsi = (closes, short = 13, long = 25) => {
  const n = closes.length, m = new Array(n).fill(0), am = new Array(n).fill(0);
  for (let i = 1; i < n; i++) { m[i] = closes[i] - closes[i - 1]; am[i] = Math.abs(m[i]); }
  const dbl = ema(ema(m, long), short), adbl = ema(ema(am, long), short);
  return closes.map((_, i) => (dbl[i] != null && adbl[i] ? 100 * dbl[i] / adbl[i] : null));
};

// CMO
export const cmo = (closes, p = 9) => {
  const n = closes.length, up = new Array(n).fill(0), dn = new Array(n).fill(0);
  for (let i = 1; i < n; i++) { const ch = closes[i] - closes[i - 1]; if (ch > 0) up[i] = ch; else dn[i] = -ch; }
  const su = sma(up, p).map((v) => (v == null ? null : v * p)), sd = sma(dn, p).map((v) => (v == null ? null : v * p));
  return closes.map((_, i) => { if (su[i] == null) return null; const t = su[i] + sd[i]; return t ? 100 * (su[i] - sd[i]) / t : 0; });
};

// Pivot Points (کلاسیک، چرخشی روی کندل قبل) → P/R1/R2/S1/S2
// نقاط پیووت کلاسیک — سطوح افقی ثابت که از HLC *دوره‌ی قبل* (پیش‌فرض: روز قبل) محاسبه
// و در سراسر دوره‌ی جاری صاف نگه داشته می‌شوند (مثل TradingView). times=مُهر زمانی ثانیه‌ای هر بار.
// بدون times به رفتار قدیمی (بار قبل) برمی‌گردد.
export const pivots = (highs, lows, closes, times) => {
  const n = closes.length, p = new Array(n).fill(null), r1 = new Array(n).fill(null), r2 = new Array(n).fill(null), s1 = new Array(n).fill(null), s2 = new Array(n).fill(null);
  const setLevels = (i, pv) => { if (!pv) return; const pp = (pv.H + pv.L + pv.C) / 3; p[i] = pp; r1[i] = 2 * pp - pv.L; s1[i] = 2 * pp - pv.H; r2[i] = pp + (pv.H - pv.L); s2[i] = pp - (pv.H - pv.L); };
  if (!times || !times.length) {
    for (let i = 1; i < n; i++) setLevels(i, { H: highs[i - 1], L: lows[i - 1], C: closes[i - 1] });
    return { p, r1, r2, s1, s2 };
  }
  const dayOf = (t) => Math.floor((t || 0) / 86400); // مرز روز UTC
  let curDay = null, dH = -Infinity, dL = Infinity, dC = null, prev = null;
  for (let i = 0; i < n; i++) {
    const d = dayOf(times[i]);
    if (curDay === null) { curDay = d; dH = highs[i]; dL = lows[i]; dC = closes[i]; }
    else if (d !== curDay) { prev = { H: dH, L: dL, C: dC }; curDay = d; dH = highs[i]; dL = lows[i]; dC = closes[i]; }
    else { dH = Math.max(dH, highs[i]); dL = Math.min(dL, lows[i]); dC = closes[i]; }
    setLevels(i, prev); // سطوح دوره‌ی جاری از HLC دوره‌ی قبل ⇒ در طول دوره صاف می‌مانند
  }
  return { p, r1, r2, s1, s2 };
};

// ALMA — میانگین گاوسی آرنو لگو (offset 0..1 محل قله، sigma هموارسازی)
export const alma = (src, p = 9, offset = 0.85, sigma = 6) => {
  const n = src.length, out = new Array(n).fill(null);
  const m = offset * (p - 1), s = p / sigma || 1e-9;
  const w = new Array(p); let wsum = 0;
  for (let j = 0; j < p; j++) { w[j] = Math.exp(-((j - m) * (j - m)) / (2 * s * s)); wsum += w[j]; }
  for (let i = p - 1; i < n; i++) {
    let acc = 0, ok = true;
    for (let j = 0; j < p; j++) { const v = src[i - (p - 1) + j]; if (v == null) { ok = false; break; } acc += v * w[j]; }
    out[i] = ok && wsum ? acc / wsum : null;
  }
  return out;
};

// MA Ribbon — نوار چند میانگین متحرک (base، گام step، تعداد count؛ SMA یا EMA)
export const maRibbon = (src, base = 20, step = 10, count = 6, type = 'sma') => {
  const fn = type === 'ema' ? ema : sma;
  const out = [];
  for (let k = 0; k < count; k++) out.push(fn(src, Math.max(1, base + k * step)));
  return out;
};

// GMMA — گاپی چندمیانگین متحرک: گروه کوتاه‌مدت {3,5,8,10,12,15} + بلندمدت {30,35,40,45,50,60}
export const gmma = (src) => {
  const shortL = [3, 5, 8, 10, 12, 15], longL = [30, 35, 40, 45, 50, 60];
  return { short: shortL.map((p) => ema(src, p)), long: longL.map((p) => ema(src, p)) };
};

// منبع قیمت (هم‌تراز input.source TV): close/open/high/low + قیمت‌های ترکیبی.
// پیش‌فرض/نامعلوم ⇒ close ⇒ رفتار قبلی دست‌نخورده (بدون رگرسیون برای اندیکاتورهای ذخیره‌شده).
export const SOURCE_OPTS = ['close', 'open', 'high', 'low', 'hl2', 'hlc3', 'ohlc4', 'hlcc4'];
export const resolveSrc = (c, name) => {
  switch (name) {
    case 'open': return c.open;
    case 'high': return c.high;
    case 'low': return c.low;
    case 'hl2': return c.high.map((h, i) => (h + c.low[i]) / 2);
    case 'hlc3': return c.high.map((h, i) => (h + c.low[i] + c.close[i]) / 3);
    case 'ohlc4': return c.high.map((h, i) => (c.open[i] + h + c.low[i] + c.close[i]) / 4);
    // HLCC4 = (H + L + 2×C)/4 — منبع هشتم TV (وزن دوبرابر close). #262
    case 'hlcc4': return c.high.map((h, i) => (h + c.low[i] + 2 * c.close[i]) / 4);
    default: return c.close;
  }
};

// رجیستری اندیکاتورها (برای UI). pane: 'main' (اورلی) یا 'sub'.
export const REGISTRY = {
  ma:   { label: 'میانگین متحرک (MA)', pane: 'main', inputs: { period: 20, source: 'close' }, color: '#60a5fa', calc: (c, i) => ({ line: sma(resolveSrc(c, i.source), i.period) }) },
  ema:  { label: 'EMA', pane: 'main', inputs: { period: 20, source: 'close' }, color: '#f59e0b', calc: (c, i) => ({ line: ema(resolveSrc(c, i.source), i.period) }) },
  wma:  { label: 'WMA', pane: 'main', inputs: { period: 20, source: 'close' }, color: '#a78bfa', calc: (c, i) => ({ line: wma(resolveSrc(c, i.source), i.period) }) },
  hma:  { label: 'HMA (هال)', pane: 'main', inputs: { period: 21, source: 'close' }, color: '#22d3ee', calc: (c, i) => ({ line: hma(resolveSrc(c, i.source), i.period) }) },
  vwap: { label: 'VWAP (سشن‌محور)', pane: 'main', inputs: {}, color: '#e879f9', calc: (c) => ({ line: vwap(c.high, c.low, c.close, c.volume, c.time) }) },
  avwap: { label: 'VWAP لنگرانداخته (±σ)', pane: 'main', inputs: { anchorBars: 100, mult: 1 }, color: '#e879f9', calc: (c, i) => { const r = avwap(c.high, c.low, c.close, c.volume, i.anchorBars, i.mult); return { lines: [{ data: r.vwap, color: '#e879f9' }, { data: r.upper, color: '#a855f7' }, { data: r.lower, color: '#a855f7' }], cloud: [1, 2], cloudColors: ['rgba(168,85,247,0.09)'] }; } }, // باندهای ±σ توپر + پُرشدگی بنفش کم‌رنگ بینشان
  choppiness: { label: 'شاخص چاپینس', pane: 'sub', inputs: { period: 14 }, color: '#94a3b8', calc: (c, i) => ({ line: choppiness(c.high, c.low, c.close, i.period), guides: [61.8, 38.2] }) },
  vortex: { label: 'وُرتکس (VI±)', pane: 'sub', inputs: { period: 14 }, color: '#22c55e', calc: (c, i) => { const r = vortex(c.high, c.low, c.close, i.period); return { line: r.plus, signal: r.minus }; } },
  dpo: { label: 'DPO', pane: 'sub', inputs: { period: 20, source: 'close' }, color: '#f59e0b', calc: (c, i) => ({ line: dpo(resolveSrc(c, i.source), i.period), guides: [0] }) },
  bop: { label: 'موازنه‌ی قدرت (BOP)', pane: 'sub', inputs: {}, color: '#8b5cf6', calc: (c) => ({ line: bop(c.open, c.high, c.low, c.close), guides: [0] }) },
  eom: { label: 'سهولت حرکت (EOM)', pane: 'sub', inputs: { period: 14 }, color: '#06b6d4', calc: (c, i) => ({ line: eom(c.high, c.low, c.volume, i.period), guides: [0] }) },
  elderRay: { label: 'الدر ری (قدرت گاو/خرس)', pane: 'sub', inputs: { period: 13 }, color: '#22c55e', calc: (c, i) => { const r = elderRay(c.high, c.low, c.close, i.period); return { hists: [{ data: r.bull, color: '#22c55e' }, { data: r.bear, color: '#ef4444' }], guides: [0] }; } }, // دو هیستوگرام: قدرت گاو سبز + قدرت خرس قرمز مثل TV
  chandeKroll: { label: 'استاپ چاند کرول', pane: 'main', inputs: { p: 10, x: 1, q: 9 }, color: '#ef4444', calc: (c, i) => { const r = chandeKroll(c.high, c.low, c.close, i.p, i.x, i.q); return { lines: [{ data: r.high, color: '#ef4444' }, { data: r.low, color: '#22c55e' }] }; } },
  massIndex: { label: 'شاخص توده', pane: 'sub', inputs: { period: 9, sum: 25 }, color: '#f59e0b', calc: (c, i) => ({ line: massIndex(c.high, c.low, i.period, i.sum), guides: [27, 26.5] }) },
  coppock: { label: 'منحنی کاپاک', pane: 'sub', inputs: { source: 'close' }, color: '#8b5cf6', calc: (c, i) => ({ line: coppock(resolveSrc(c, i.source)), guides: [0] }) },
  kst: { label: 'KST', pane: 'sub', inputs: { source: 'close' }, color: '#06b6d4', calc: (c, i) => { const r = kst(resolveSrc(c, i.source)); return { line: r.line, signal: r.signal, guides: [0] }; } },
  alligator: { label: 'تمساح ویلیامز', pane: 'main', inputs: {}, color: '#3b82f6', calc: (c) => { const r = alligator(c.high, c.low); return { lines: [{ data: r.jaw, color: '#3b82f6' }, { data: r.teeth, color: '#ef4444' }, { data: r.lips, color: '#22c55e' }] }; } },
  rvi: { label: 'سرزندگی نسبی (RVI)', pane: 'sub', inputs: {}, color: '#22c55e', calc: (c) => { const r = rvi(c.open, c.high, c.low, c.close); return { line: r.line, signal: r.signal, guides: [0] }; } },
  bbWidth: { label: 'پهنای باند بولینگر', pane: 'sub', inputs: { period: 20, mult: 2, source: 'close' }, color: '#f59e0b', calc: (c, i) => ({ line: bbWidth(resolveSrc(c, i.source), i.period, i.mult) }) },
  stc: { label: 'چرخه‌ی روند شاف (STC)', pane: 'sub', inputs: { fast: 23, slow: 50, cycle: 10, source: 'close' }, color: '#8b5cf6', calc: (c, i) => ({ line: stc(resolveSrc(c, i.source), i.fast, i.slow, i.cycle), guides: [75, 25] }) },
  netVolume: { label: 'حجم خالص', pane: 'sub', inputs: {}, color: '#089981', calc: (c) => ({ hist: netVolume(c.open, c.close, c.volume), histMode: 'sign', guides: [0] }) }, // هیستوگرام سبز/قرمز بر اساس علامت مثل TV
  stdErrBands: { label: 'باندهای خطای معیار', pane: 'main', inputs: { period: 21, mult: 2 }, color: '#3b82f6', calc: (c, i) => { const r = stdErrBands(c.close, i.period, i.mult); return { lines: [{ data: r.mid, color: '#3b82f6' }, { data: r.up, color: '#60a5fa' }, { data: r.dn, color: '#60a5fa' }], cloud: [1, 2], cloudColors: ['rgba(96,165,250,0.07)'] }; } }, // باندهای توپر + پُرشدگی آبی کم‌رنگ مثل TV
  accelerator: { label: 'نوسان‌ساز شتاب (AC)', pane: 'sub', inputs: {}, color: '#089981', calc: (c) => ({ hist: accelerator(c.high, c.low), guides: [0] }) }, // هیستوگرام دورنگ مومنتوم مثل TV
  chaikinVol: { label: 'نوسان چایکین', pane: 'sub', inputs: { period: 10 }, color: '#f59e0b', calc: (c, i) => ({ line: chaikinVol(c.high, c.low, i.period), guides: [0] }) },
  mtfEma: { label: 'EMA چندتایم‌فریمی (×factor)', pane: 'main', inputs: { period: 50, factor: 4 }, color: '#22d3ee', calc: (c, i) => ({ line: _expandA(ema(_resampleC(c, i.factor).close, i.period), i.factor, c.close.length) }) },
  mtfRsi: { label: 'RSI چندتایم‌فریمی (×factor)', pane: 'sub', inputs: { period: 14, factor: 4 }, color: '#f59e0b', calc: (c, i) => ({ line: _expandA(rsi(_resampleC(c, i.factor).close, i.period), i.factor, c.close.length), guides: [70, 30] }) },
  bb:   { label: 'باند بولینگر', pane: 'main', inputs: { period: 20, mult: 2, source: 'close' }, color: '#94a3b8', calc: (c, i) => { const b = bollinger(resolveSrc(c, i.source), i.period, i.mult); return { upper: b.upper, basis: b.basis, lower: b.lower, multi: true }; } },
  supertrend: { label: 'سوپرترند', pane: 'main', inputs: { period: 10, mult: 3 }, color: '#10b981', calc: (c, i) => { const r = supertrend(c.high, c.low, c.close, i.period, i.mult); const up = r.line.map((v, k) => (r.trend[k] === 1 ? v : null)); const dn = r.line.map((v, k) => (r.trend[k] === -1 ? v : null)); return { lines: [{ data: up, color: '#10b981', gaps: true }, { data: dn, color: '#ef4444', gaps: true }] }; } }, // خط دورنگ سوپرترند: سبز صعودی / قرمز نزولی (مثل TV)
  rsi:  { label: 'RSI', pane: 'sub', inputs: { period: 14, source: 'close', maLength: 14 }, color: '#a78bfa', calc: (c, i) => { const r = rsi(resolveSrc(c, i.source), i.period); return { line: r, signal: (i.maLength > 0 ? smaNull(r, i.maLength) : null), guides: [30, 70], range: [0, 100], zone: [30, 70] }; } }, // «RSI-based MA» پیش‌فرض TV (خط دوم نارنجی؛ maLength=0 خاموشش می‌کند). #330
  macd: { label: 'MACD', pane: 'sub', inputs: { fast: 12, slow: 26, sig: 9, source: 'close' }, color: '#60a5fa', calc: (c, i) => { const m = macd(resolveSrc(c, i.source), i.fast, i.slow, i.sig); return { line: m.macd, signal: m.signal, hist: m.hist, macd: true }; } },
  stoch:{ label: 'استوکاستیک', pane: 'sub', inputs: { period: 14, d: 3 }, color: '#34d399', calc: (c, i) => { const s = stoch(c.high, c.low, c.close, i.period, i.d); return { line: s.k, signal: s.d, guides: [20, 80], range: [0, 100], zone: [20, 80] }; } },
  atr:  { label: 'ATR', pane: 'sub', inputs: { period: 14 }, color: '#fb7185', calc: (c, i) => ({ line: atr(c.high, c.low, c.close, i.period) }) },
  cci:  { label: 'CCI', pane: 'sub', inputs: { period: 20, source: 'hlc3' }, color: '#f472b6', calc: (c, i) => ({ line: _cciCore(resolveSrc(c, i.source), i.period), guides: [-100, 100], zone: [-100, 100] }) },
  willr:{ label: 'ویلیامز %R', pane: 'sub', inputs: { period: 14 }, color: '#facc15', calc: (c, i) => ({ line: williamsR(c.high, c.low, c.close, i.period), guides: [-20, -80], range: [-100, 0], zone: [-80, -20] }) },
  obv:  { label: 'OBV', pane: 'sub', inputs: {}, color: '#38bdf8', calc: (c) => ({ line: obv(c.close, c.volume) }) },
  adx:  { label: 'ADX', pane: 'sub', inputs: { period: 14 }, color: '#f97316', calc: (c, i) => ({ line: adx(c.high, c.low, c.close, i.period), guides: [25] }) },
  donchian: { label: 'کانال دونچیان', pane: 'main', inputs: { period: 20 }, color: '#94a3b8', calc: (c, i) => { const d = donchian(c.high, c.low, i.period); return { lines: [{ data: d.upper, color: '#60a5fa' }, { data: d.basis, color: '#94a3b8' }, { data: d.lower, color: '#60a5fa' }], cloud: [0, 2], cloudColors: ['rgba(96,165,250,0.07)'] }; } }, // باندهای توپر + پُرشدگی آبی کم‌رنگ مثل دونچیان TV
  keltner: { label: 'کانال کلتنر', pane: 'main', inputs: { period: 20, mult: 2 }, color: '#f472b6', calc: (c, i) => { const k = keltner(c.high, c.low, c.close, i.period, i.mult); return { lines: [{ data: k.upper, color: '#f472b6' }, { data: k.basis, color: '#f472b6' }, { data: k.lower, color: '#f472b6' }], cloud: [0, 2], cloudColors: ['rgba(244,114,182,0.08)'] }; } }, // باندهای توپر + پُرشدگی صورتی کم‌رنگ بین بالا/پایین مثل کلتنر TV
  ichimoku: { label: 'ایچیموکو', pane: 'main', inputs: { tenkan: 9, kijun: 26, span: 52 }, color: '#22d3ee', calc: (c, i) => { const k = ichimoku(c.high, c.low, c.close, i.tenkan, i.kijun, i.span); return { lines: [{ data: k.tenkan, color: '#3b82f6' }, { data: k.kijun, color: '#ef4444' }, { data: k.spanA, color: '#22c55e' }, { data: k.spanB, color: '#f59e0b' }, { data: k.chikou, color: '#a855f7', dashed: true }], cloud: [2, 3] }; } }, // Tenkan/Kijun روی کندل جاری · Senkou A/B با جابه‌جایی +۲۶ و ابر بینشان (indexهای 2/3) · Chikou با جابه‌جایی −۲۶ (خط‌چین)
  dema: { label: 'DEMA (نمایی دوگانه)', pane: 'main', inputs: { period: 20, source: 'close' }, color: '#38bdf8', calc: (c, i) => ({ line: dema(resolveSrc(c, i.source), i.period) }) },
  tema: { label: 'TEMA (نمایی سه‌گانه)', pane: 'main', inputs: { period: 20, source: 'close' }, color: '#fb923c', calc: (c, i) => ({ line: tema(resolveSrc(c, i.source), i.period) }) },
  vwma: { label: 'VWMA (وزنی حجمی)', pane: 'main', inputs: { period: 20 }, color: '#c084fc', calc: (c, i) => ({ line: vwma(c.close, c.volume, i.period) }) },
  psar: { label: 'پارابولیک SAR', pane: 'main', inputs: { start: 0.02, inc: 0.02, max: 0.2 }, color: '#a855f7', calc: (c, i) => { const s = psar(c.high, c.low, i.start, i.inc, i.max); const below = s.map((v, k) => (v == null ? null : (v <= c.close[k] ? v : null))); const above = s.map((v, k) => (v == null ? null : (v > c.close[k] ? v : null))); return { lines: [{ data: below, color: '#a855f7', dashed: true, gaps: true }, { data: above, color: '#a855f7', dashed: true, gaps: true }] }; } }, // شکست خط در چرخش SAR (بدون خط اتصال اریب از روی قیمت) مثل TV
  pivots: { label: 'نقاط پیووت (کلاسیک)', pane: 'main', inputs: {}, color: '#94a3b8', calc: (c) => { const v = pivots(c.high, c.low, c.close, c.time); return { lines: [{ data: v.r2, color: '#ef4444', dashed: true, gaps: true }, { data: v.r1, color: '#f87171', dashed: true, gaps: true }, { data: v.p, color: '#94a3b8', gaps: true }, { data: v.s1, color: '#4ade80', dashed: true, gaps: true }, { data: v.s2, color: '#22c55e', dashed: true, gaps: true }] }; } },
  aroon: { label: 'آرون (Aroon)', pane: 'sub', inputs: { period: 14 }, color: '#22c55e', calc: (c, i) => { const a = aroon(c.high, c.low, i.period); return { line: a.up, signal: a.down, guides: [30, 70], range: [0, 100] }; } },
  mfi: { label: 'MFI (جریان نقدینگی)', pane: 'sub', inputs: { period: 14 }, color: '#10b981', calc: (c, i) => ({ line: mfi(c.high, c.low, c.close, c.volume, i.period), guides: [20, 80], range: [0, 100], zone: [20, 80] }) },
  cmf: { label: 'CMF (پول چایکین)', pane: 'sub', inputs: { period: 20 }, color: '#0ea5e9', calc: (c, i) => ({ line: cmf(c.high, c.low, c.close, c.volume, i.period), guides: [0] }) },
  stochrsi: { label: 'استوکاستیک RSI', pane: 'sub', inputs: { period: 14, k: 3, d: 3, source: 'close' }, color: '#34d399', calc: (c, i) => { const s = stochRsi(resolveSrc(c, i.source), i.period, i.k, i.d); return { line: s.k, signal: s.d, guides: [20, 80], range: [0, 100], zone: [20, 80] }; } },
  ao: { label: 'اسیلاتور شگفت‌انگیز (AO)', pane: 'sub', inputs: {}, color: '#60a5fa', calc: (c) => ({ hist: ao(c.high, c.low), guides: [0] }) }, // هیستوگرام دورنگ مومنتوم مثل TV
  tsi: { label: 'TSI (قدرت واقعی)', pane: 'sub', inputs: { short: 13, long: 25, source: 'close' }, color: '#f472b6', calc: (c, i) => ({ line: tsi(resolveSrc(c, i.source), i.short, i.long), guides: [0] }) },
  cmo: { label: 'CMO (مومنتوم چاند)', pane: 'sub', inputs: { period: 9, source: 'close' }, color: '#fbbf24', calc: (c, i) => ({ line: cmo(resolveSrc(c, i.source), i.period), guides: [-50, 50], zone: [-50, 50] }) },
  alma: { label: 'ALMA (آرنو لگو)', pane: 'main', inputs: { period: 9, offset: 0.85, sigma: 6, source: 'close' }, color: '#2dd4bf', calc: (c, i) => ({ line: alma(resolveSrc(c, i.source), i.period, i.offset, i.sigma) }) },
  maRibbon: { label: 'نوار میانگین‌ها (MA Ribbon)', pane: 'main', inputs: { base: 20, step: 10, count: 6 }, color: '#60a5fa', calc: (c, i) => { const rs = maRibbon(c.close, i.base, i.step, i.count, 'sma'); const pal = ['#60a5fa', '#38bdf8', '#22d3ee', '#2dd4bf', '#34d399', '#4ade80', '#a3e635', '#facc15']; return { lines: rs.map((data, k) => ({ data, color: pal[k % pal.length] })) }; } },
  gmma: { label: 'گاپی (GMMA)', pane: 'main', inputs: {}, color: '#3b82f6', calc: (c) => { const g = gmma(c.close); return { lines: [...g.short.map((data) => ({ data, color: '#3b82f6' })), ...g.long.map((data) => ({ data, color: '#ef4444' }))] }; } },
  maCross: { label: 'تقاطع میانگین‌ها (MA Cross)', pane: 'main', inputs: { fast: 10, slow: 30 }, color: '#22c55e', calc: (c, i) => ({ lines: [{ data: sma(c.close, i.fast), color: '#22c55e' }, { data: sma(c.close, i.slow), color: '#ef4444' }] }) },
};

// ── ادغام افزونه‌های فصل ۵ ────────────────────────────────────────────────
//
// پیش‌تر یک `Object.assign(REGISTRY, A, B)` ساده بود و کامنتش می‌گفت «۳ کلید مشترک».
// واقعاً **۶** تاست، و «آخرین نویسنده بی‌صدا برنده» یک باگ درست‌نمایی بود:
//
//   • trix        — extB برنده می‌شد و **غلط** بود. `_ema` در extB روی null مقدار
//                   قبلی را جلو می‌برد و warmup را ایندکس‌محور می‌سنجد، پس TRIX(18)
//                   از کندل ۱۸ مقدار می‌داد در حالی که سه EMA تودرتو ~۵۴ کندل لازم
//                   دارند — ۳۴ کندل ابتدایی از مقادیر جعلی ساخته می‌شد. همان کلاس
//                   باگی که dema/tema داشتند (نگاه: کامنت dema/tema بالاتر).
//                   → نسخه‌ی extA (شمارشی، nullها را رد می‌کند) برنده شد.
//   • bbpercent   — هر دو ریاضی یکسان می‌دهند؛ تکرار بی‌ضرر. extB نگه داشته شد.
//   • bbw         — همان.
//   • chaikinVol  — extA بر core.
//   • bop, eom    — extB بر core.
//
// حالا مالکیت **صریح** است و گارد جلوی تصادم ناخواسته‌ی آینده را می‌گیرد.
const REGISTRY_OWNER = {
  trix: 'A',        // extA — warmup درست
  bbpercent: 'B',
  bbw: 'B',
  chaikinVol: 'A',
  bop: 'B',
  eom: 'B',
};

const _mergeExt = (target, ext, tag) => {
  for (const [key, def] of Object.entries(ext)) {
    if (key in target) {
      const owner = REGISTRY_OWNER[key];
      if (owner === undefined) {
        // تصادم جدید و ثبت‌نشده — کسی کلیدی را دوباره تعریف کرده بدون اینکه بداند
        const msg = `[indicators] تصادم ثبت‌نشده‌ی رجیستری: "${key}" (منبع: ${tag}). ` +
          'یکی را حذف کنید یا مالکش را در REGISTRY_OWNER اعلام کنید.';
        if (import.meta.env?.DEV) throw new Error(msg);
        console.warn(msg);
      } else if (owner !== tag) {
        continue; // مالک اعلام‌شده این نیست → نادیده بگیر
      }
    }
    target[key] = def;
  }
  return target;
};

_mergeExt(REGISTRY, EXT_REGISTRY_A, 'A');
_mergeExt(REGISTRY, EXT_REGISTRY_B, 'B');
