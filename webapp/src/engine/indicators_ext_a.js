// بازارنما — افزونهٔ اندیکاتورها، بستهٔ A: روند/میانگین‌متحرک (§5.1) + نوسان (§5.3).
// این فایل کاملاً مستقل و افزایشی است؛ هیچ فایلِ مشترکی را تغییر نمی‌دهد.
// EXT_REGISTRY_A را با یک خط در REGISTRY اصلی (indicators.js) ادغام کنید.
//
// قراردادها (دقیقاً مثل indicators.js):
//   - آرایه‌ها هم‌طولِ کندل‌ها؛ دورهٔ گرم‌شدن → null.
//   - c = { open, high, low, close, volume, time } هر کدام آرایهٔ عدد.
//   - i = ورودی‌های resolve‌شده (پیش‌فرض‌ها + override کاربر + i.source آرایه).
//   - شکل‌های خروجی: { line }, { upper, basis, lower, multi:true },
//     { line, signal, guides, range }, { lines:[{data,color,dashed?}] }.
//   - منبع: هر اندیکاتورِ تک‌خطی از i.source ?? c.close می‌خواند (سازگار با §5.7).

// ───────────────────────── کمک‌تابع‌های محلیِ مستقل ─────────────────────────
// (این‌ها در اینجا کپی/تعریف می‌شوند تا فایل drop-in بماند و به export‌های
//  indicators.js وابسته نباشد؛ نام‌ها prefix داخلی دارند تا تداخل نکنند.)

// میانگین متحرک ساده
const _sma = (src, p) => {
  const out = new Array(src.length).fill(null);
  let sum = 0, cnt = 0;
  for (let i = 0; i < src.length; i++) {
    const v = src[i];
    if (v == null) { // در صورت ورودیِ دارای null، پنجره را امن نگه می‌داریم
      out[i] = null;
      // بازسازیِ مجموع از صفر برای سادگی و ایمنی عددی
      sum = 0; cnt = 0;
      continue;
    }
    sum += v; cnt++;
    if (i >= p) { const old = src[i - p]; if (old != null) { sum -= old; cnt--; } }
    if (cnt >= p) out[i] = sum / p;
  }
  return out;
};

// میانگین متحرک نمایی (seed = اولین مقدارِ معتبر، خروجی از i>=p-1)
const _ema = (src, p) => {
  const out = new Array(src.length).fill(null);
  const k = 2 / (p + 1);
  let prev = null, seen = 0;
  for (let i = 0; i < src.length; i++) {
    const v = src[i];
    if (v == null) continue;
    prev = prev == null ? v : v * k + prev * (1 - k);
    seen++;
    if (seen >= p) out[i] = prev;
  }
  return out;
};

// میانگین متحرک وزنی خطی
const _wma = (src, p) => {
  const out = new Array(src.length).fill(null);
  const denom = (p * (p + 1)) / 2;
  for (let i = p - 1; i < src.length; i++) {
    let ok = true, s = 0;
    for (let j = 0; j < p; j++) { const v = src[i - j]; if (v == null) { ok = false; break; } s += v * (p - j); }
    out[i] = ok ? s / denom : null;
  }
  return out;
};

// SMMA / RMA (وایلدر): SMMA_t = (SMMA_{t-1}·(p-1)+src_t)/p ؛ seed = SMA(p)
const _smma = (src, p) => {
  const out = new Array(src.length).fill(null);
  let prev = null, sum = 0, cnt = 0;
  for (let i = 0; i < src.length; i++) {
    const v = src[i];
    if (v == null) continue;
    if (prev == null) {
      sum += v; cnt++;
      if (cnt === p) { prev = sum / p; out[i] = prev; }
    } else {
      prev = (prev * (p - 1) + v) / p; out[i] = prev;
    }
  }
  return out;
};

// انحراف معیارِ جمعیتی روی پنجرهٔ p
const _stdev = (src, p) => {
  const out = new Array(src.length).fill(null);
  for (let i = p - 1; i < src.length; i++) {
    let ok = true, mean = 0;
    for (let j = 0; j < p; j++) { const v = src[i - j]; if (v == null) { ok = false; break; } mean += v; }
    if (!ok) { out[i] = null; continue; }
    mean /= p;
    let s = 0;
    for (let j = 0; j < p; j++) { const d = src[i - j] - mean; s += d * d; }
    out[i] = Math.sqrt(s / p);
  }
  return out;
};

// رگرسیون خطی: نقطهٔ انتهای پنجره (a + b·(p-1+offset))
const _linreg = (src, p, offset = 0) => {
  const out = new Array(src.length).fill(null);
  // مجموع‌های x ثابت‌اند (x = 0..p-1)
  const sumX = (p - 1) * p / 2;
  const sumX2 = (p - 1) * p * (2 * p - 1) / 6;
  const denom = p * sumX2 - sumX * sumX;
  for (let i = p - 1; i < src.length; i++) {
    let ok = true, sumY = 0, sumXY = 0;
    for (let j = 0; j < p; j++) {
      const x = j;                 // قدیمی‌ترین = 0، جدیدترین = p-1
      const y = src[i - (p - 1) + j];
      if (y == null) { ok = false; break; }
      sumY += y; sumXY += x * y;
    }
    if (!ok || !denom) { out[i] = null; continue; }
    const b = (p * sumXY - sumX * sumY) / denom;   // شیب
    const a = (sumY - b * sumX) / p;               // عرض از مبدأ
    out[i] = a + b * (p - 1 + offset);             // مقدار در نقطهٔ انتها (+ آفست)
  }
  return out;
};

// True Range
const _atr = (highs, lows, closes, p) => {
  const n = closes.length, tr = new Array(n).fill(null);
  for (let i = 0; i < n; i++) {
    if (i === 0) { tr[i] = highs[i] - lows[i]; continue; }
    tr[i] = Math.max(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]));
  }
  const out = new Array(n).fill(null);
  let prev = null;
  for (let i = 0; i < n; i++) {
    if (i < p) { if (i === p - 1) { let s = 0; for (let j = 0; j < p; j++) s += tr[j]; prev = s / p; out[i] = prev; } continue; }
    prev = (prev * (p - 1) + tr[i]) / p; out[i] = prev;
  }
  return out;
};

// بیشینه/کمینهٔ پنجره
const _highest = (src, p) => { const o = new Array(src.length).fill(null); for (let i = p - 1; i < src.length; i++) { let h = -Infinity; for (let j = 0; j < p; j++) h = Math.max(h, src[i - j]); o[i] = h; } return o; };
const _lowest = (src, p) => { const o = new Array(src.length).fill(null); for (let i = p - 1; i < src.length; i++) { let l = Infinity; for (let j = 0; j < p; j++) l = Math.min(l, src[i - j]); o[i] = l; } return o; };

// ───────────────────────── فرمول‌های اختصاصی ─────────────────────────

// ZLEMA — میانگین نمایی با تأخیرِ صفر: EMA(src + (src - src_{t-lag}))، lag=(p-1)/2
const _zlema = (src, p) => {
  const lag = Math.floor((p - 1) / 2);
  const de = src.map((v, i) => (v == null || src[i - lag] == null || i < lag ? null : v + (v - src[i - lag])));
  // EMA روی سری‌ای که ابتدایش null دارد؛ نقاط null نادیده گرفته می‌شوند
  return _ema(de, p);
};

// KAMA (Kaufman) — میانگینِ تطبیقی
//   ER = |Δ(p)| / Σ|Δ(1)| ؛ SC = (ER·(fast-slow)+slow)^2 با fast=2/(2+1), slow=2/(30+1)
const _kama = (src, p = 10, fastN = 2, slowN = 30) => {
  const out = new Array(src.length).fill(null);
  const fast = 2 / (fastN + 1), slow = 2 / (slowN + 1);
  let prev = null;
  for (let i = 0; i < src.length; i++) {
    if (i < p) { if (i === p - 1) { prev = src[i]; out[i] = prev; } continue; }
    const change = Math.abs(src[i] - src[i - p]);
    let vol = 0; for (let j = 0; j < p; j++) vol += Math.abs(src[i - j] - src[i - j - 1]);
    const er = vol ? change / vol : 0;
    const sc = Math.pow(er * (fast - slow) + slow, 2);
    prev = prev + sc * (src[i] - prev);
    out[i] = prev;
  }
  return out;
};

// T3 (Tillson) — شش‌بار EMA با ضریب حجمِ a
const _t3 = (src, p = 10, a = 0.7) => {
  const e1 = _ema(src, p);
  const e2 = _ema(e1.map((v) => (v == null ? null : v)), p);
  const e3 = _ema(e2.map((v) => (v == null ? null : v)), p);
  const e4 = _ema(e3.map((v) => (v == null ? null : v)), p);
  const e5 = _ema(e4.map((v) => (v == null ? null : v)), p);
  const e6 = _ema(e5.map((v) => (v == null ? null : v)), p);
  const c1 = -a * a * a;
  const c2 = 3 * a * a + 3 * a * a * a;
  const c3 = -6 * a * a - 3 * a - 3 * a * a * a;
  const c4 = 1 + 3 * a + a * a * a + 3 * a * a;
  return src.map((_, i) =>
    e6[i] != null && e5[i] != null && e4[i] != null && e3[i] != null
      ? c1 * e6[i] + c2 * e5[i] + c3 * e4[i] + c4 * e3[i]
      : null
  );
};

// McGinley Dynamic — MA خودتنظیم: md += (src - md)/(N·(src/md)^4)
const _mcginley = (src, p = 14) => {
  const out = new Array(src.length).fill(null);
  let md = null, sum = 0, cnt = 0;
  for (let i = 0; i < src.length; i++) {
    const v = src[i];
    if (v == null) continue;
    if (md == null) {
      sum += v; cnt++;
      if (cnt === p) { md = sum / p; out[i] = md; }   // seed = SMA(p)
    } else {
      const ratio = md ? v / md : 1;
      const denom = p * Math.pow(ratio, 4);
      md = md + (v - md) / (denom || 1e-9);
      out[i] = md;
    }
  }
  return out;
};

// TRIX — نرخ تغییرِ سه‌بار EMA (×10000، درصدِ ممیزی)
const _trix = (src, p = 18, sig = 9) => {
  const e1 = _ema(src, p);
  const e2 = _ema(e1, p);
  const e3 = _ema(e2, p);
  const line = e3.map((v, i) => (v != null && e3[i - 1] != null && e3[i - 1] ? 10000 * (v - e3[i - 1]) / e3[i - 1] : null));
  const signal = _ema(line, sig);
  return { line, signal };
};

// Bollinger خام (basis/upper/lower) برای %B و BBW و انحراف
const _bb = (src, p, mult) => {
  const basis = _sma(src, p);
  const sd = _stdev(src, p);
  const upper = src.map((_, i) => (basis[i] != null && sd[i] != null ? basis[i] + mult * sd[i] : null));
  const lower = src.map((_, i) => (basis[i] != null && sd[i] != null ? basis[i] - mult * sd[i] : null));
  return { basis, upper, lower, sd };
};

// نوسانِ تاریخی (Historical Volatility) — ٪ سالانه‌شدهٔ انحرافِ لگاریتمِ بازده
const _hv = (closes, p = 10, annual = 365) => {
  const n = closes.length, lr = new Array(n).fill(null);
  for (let i = 1; i < n; i++) lr[i] = closes[i] > 0 && closes[i - 1] > 0 ? Math.log(closes[i] / closes[i - 1]) : null;
  const sd = _stdev(lr, p);
  return sd.map((v) => (v == null ? null : 100 * v * Math.sqrt(annual)));
};

// نوسانِ چایکین — درصدِ تغییرِ EMA(H−L) نسبت به roc دوره قبل
const _chaikinVol = (highs, lows, p = 10, roc = 10) => {
  const hl = highs.map((h, i) => h - lows[i]);
  const e = _ema(hl, p);
  return e.map((v, i) => (v != null && e[i - roc] != null && e[i - roc] ? 100 * (v - e[i - roc]) / e[i - roc] : null));
};

// PPO — نوسان‌سازِ درصدیِ قیمت: 100·(EMA_fast−EMA_slow)/EMA_slow ؛ سیگنال = EMA(PPO, sig)
const _ppo = (src, fast = 12, slow = 26, sig = 9) => {
  const ef = _ema(src, fast), es = _ema(src, slow);
  const line = src.map((_, i) => (ef[i] != null && es[i] != null && es[i] ? 100 * (ef[i] - es[i]) / es[i] : null));
  const signal = _ema(line, sig);
  const hist = line.map((v, i) => (v != null && signal[i] != null ? v - signal[i] : null));
  return { line, signal, hist };
};

// DMI — حرکتِ جهت‌دارِ وایلدر: +DI / −DI (+ ADX)؛ مکملِ مدخلِ adx (§5.1)
const _dmi = (highs, lows, closes, p = 14) => {
  const n = closes.length, tr = new Array(n).fill(0), pdm = new Array(n).fill(0), ndm = new Array(n).fill(0);
  for (let i = 1; i < n; i++) {
    tr[i] = Math.max(highs[i] - lows[i], Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1]));
    const up = highs[i] - highs[i - 1], dn = lows[i - 1] - lows[i];
    pdm[i] = up > dn && up > 0 ? up : 0; ndm[i] = dn > up && dn > 0 ? dn : 0;
  }
  const sm = (a) => { const o = new Array(n).fill(null); let s = 0; for (let i = 1; i <= p; i++) s += a[i]; o[p] = s; for (let i = p + 1; i < n; i++) { s = s - s / p + a[i]; o[i] = s; } return o; };
  const str = sm(tr), spd = sm(pdm), snd = sm(ndm);
  const plus = new Array(n).fill(null), minus = new Array(n).fill(null), adxOut = new Array(n).fill(null);
  let adxPrev = null; const dxs = [];
  for (let i = p; i < n; i++) {
    if (str[i] == null || !str[i]) continue;
    const pdi = 100 * spd[i] / str[i], ndi = 100 * snd[i] / str[i];
    plus[i] = pdi; minus[i] = ndi;
    const dx = (pdi + ndi) ? 100 * Math.abs(pdi - ndi) / (pdi + ndi) : 0;
    dxs.push(dx);
    if (dxs.length === p) { adxPrev = dxs.reduce((x, y) => x + y, 0) / p; adxOut[i] = adxPrev; }
    else if (adxPrev != null) { adxPrev = (adxPrev * (p - 1) + dx) / p; adxOut[i] = adxPrev; }
  }
  return { plus, minus, adx: adxOut };
};

// منبع (§5.7). رجیستری `source: 'close'` را به‌صورتِ **رشته** می‌فرستد، ولی این تابع
// پیش‌تر فقط آرایه را می‌پذیرفت — یعنی `Array.isArray('high') === false` و هر ۱۳
// اندیکاتورِ این فایل بی‌صدا روی close می‌افتادند. دراپ‌داونِ منبع در رابط بود و
// کار نمی‌کرد. حالا مثلِ `_srcOf` در indicators_ext_b.js نامِ رشته‌ای را هم resolve می‌کند.
const _hl2 = (c) => c.close.map((_, k) => (c.high[k] + c.low[k]) / 2);
const _hlc3 = (c) => c.close.map((v, k) => (c.high[k] + c.low[k] + v) / 3);
const _ohlc4 = (c) => c.close.map((v, k) => (c.open[k] + c.high[k] + c.low[k] + v) / 4);
const _hlcc4 = (c) => c.close.map((v, k) => (c.high[k] + c.low[k] + v + v) / 4);
const _src = (c, i) => {
  const s = i && i.source;
  if (Array.isArray(s)) return s;                 // سازگاریِ عقب‌رو: آرایهٔ از پیش resolve‌شده
  switch (s) {
    case 'open': return c.open;
    case 'high': return c.high;
    case 'low': return c.low;
    case 'hl2': return _hl2(c);
    case 'hlc3': return _hlc3(c);
    case 'ohlc4': return _ohlc4(c);
    case 'hlcc4': return _hlcc4(c);
    default: return c.close;
  }
};

// ───────────────────────── رجیستریِ افزونه ─────────────────────────
export const EXT_REGISTRY_A = {
  // ===== §5.1 روند / میانگین‌های متحرک =====
  smma: {
    label: 'SMMA / RMA (وایلدر)', pane: 'main', inputs: { source: 'close', period: 14 }, color: '#84cc16',
    calc: (c, i) => ({ line: _smma(_src(c, i), i.period) }),
  },
  zlema: {
    label: 'ZLEMA (تأخیرِ صفر)', pane: 'main', inputs: { source: 'close', period: 21 }, color: '#2dd4bf',
    calc: (c, i) => ({ line: _zlema(_src(c, i), i.period) }),
  },
  kama: {
    label: 'KAMA (تطبیقی کافمن)', pane: 'main', inputs: { source: 'close', period: 10, fast: 2, slow: 30 }, color: '#fb923c',
    calc: (c, i) => ({ line: _kama(_src(c, i), i.period, i.fast, i.slow) }),
  },
  t3: {
    label: 'T3 (تیلسون)', pane: 'main', inputs: { source: 'close', period: 10, volume: 0.7 }, color: '#e879f9',
    calc: (c, i) => ({ line: _t3(_src(c, i), i.period, i.volume) }),
  },
  mcginley: {
    label: 'مک‌گینلی داینامیک', pane: 'main', inputs: { source: 'close', period: 14 }, color: '#facc15',
    calc: (c, i) => ({ line: _mcginley(_src(c, i), i.period) }),
  },
  linreg: {
    label: 'منحنیِ رگرسیون خطی', pane: 'main', inputs: { source: 'close', period: 100 }, color: '#38bdf8',
    calc: (c, i) => ({ line: _linreg(_src(c, i), i.period, 0) }),
  },
  lsma: {
    label: 'LSMA (کمترین‌مربعات)', pane: 'main', inputs: { source: 'close', period: 25, offset: 0 }, color: '#a78bfa',
    calc: (c, i) => ({ line: _linreg(_src(c, i), i.period, i.offset) }),
  },
  trix: {
    label: 'TRIX', pane: 'sub', inputs: { source: 'close', period: 18, sig: 9 }, color: '#f472b6',
    calc: (c, i) => { const t = _trix(_src(c, i), i.period, i.sig); return { line: t.line, signal: t.signal, guides: [0] }; },
  },

  // ===== §5.3 نوسان =====
  bbpercent: {
    label: 'باند بولینگر ٪B', pane: 'sub', inputs: { source: 'close', period: 20, mult: 2 }, color: '#22d3ee',
    calc: (c, i) => {
      const s = _src(c, i), b = _bb(s, i.period, i.mult);
      const line = b.upper.map((u, k) => (u != null && b.lower[k] != null && u - b.lower[k] ? (s[k] - b.lower[k]) / (u - b.lower[k]) : null));
      return { line, guides: [0, 0.5, 1], range: [-0.5, 1.5] };
    },
  },
  bbw: {
    label: 'پهنای باند بولینگر', pane: 'sub', inputs: { source: 'close', period: 20, mult: 2 }, color: '#94a3b8',
    calc: (c, i) => {
      const s = _src(c, i), b = _bb(s, i.period, i.mult);
      const line = b.upper.map((u, k) => (u != null && b.basis[k] ? (u - b.lower[k]) / b.basis[k] : null));
      return { line };
    },
  },
  stddev: {
    label: 'انحراف معیار', pane: 'sub', inputs: { source: 'close', period: 20 }, color: '#fb7185',
    calc: (c, i) => ({ line: _stdev(_src(c, i), i.period) }),
  },
  envelopes: {
    label: 'پاکت‌ها (Envelopes)', pane: 'main', inputs: { source: 'close', period: 20, pct: 1 }, color: '#a78bfa',
    calc: (c, i) => {
      const b = _sma(_src(c, i), i.period);
      return {
        upper: b.map((v) => (v == null ? null : v * (1 + i.pct / 100))),
        basis: b,
        lower: b.map((v) => (v == null ? null : v * (1 - i.pct / 100))),
        multi: true,
      };
    },
  },
  hv: {
    label: 'نوسانِ تاریخی (HV)', pane: 'sub', inputs: { period: 10, annual: 365 }, color: '#f59e0b',
    calc: (c, i) => ({ line: _hv(c.close, i.period, i.annual) }),
  },
  chaikinVol: {
    label: 'نوسانِ چایکین', pane: 'sub', inputs: { period: 10, roc: 10 }, color: '#0ea5e9',
    calc: (c, i) => ({ line: _chaikinVol(c.high, c.low, i.period, i.roc), guides: [0] }),
  },

  // ===== §5.2 مومنتوم / اسیلاتور =====
  dmi: {
    label: 'DMI (جهت‌دارِ +DI/−DI)', pane: 'sub', inputs: { period: 14 }, color: '#22c55e',
    calc: (c, i) => { const r = _dmi(c.high, c.low, c.close, i.period); return { line: r.plus, signal: r.minus, signal2: r.adx, signal2Color: '#64748b', guides: [25], range: [0, 100] }; }, // +DI سبز / −DI نارنجی / ADX خاکستریِ متوسط (روی هر دو تمِ روشن/تیره دیده شود) — مثلِ DMIِ TradingView
  },
  ppo: {
    label: 'PPO (درصدیِ قیمت)', pane: 'sub', inputs: { source: 'close', fast: 12, slow: 26, sig: 9 }, color: '#60a5fa',
    calc: (c, i) => { const p = _ppo(_src(c, i), i.fast, i.slow, i.sig); return { line: p.line, signal: p.signal, hist: p.hist, macd: true }; },
  },
};

export default EXT_REGISTRY_A;
