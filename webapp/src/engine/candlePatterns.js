// ─────────────────────────────────────────────────────────────────────────
// تشخیص خودکار الگوهای کندل‌استیک (مثل اندیکاتور Candlestick Patterns تریدینگ‌ویو)
// تعریف‌های کلاسیک تحلیل تکنیکال (دامنه‌ی عمومی) — پیاده‌سازی اورجینال، بدون کپی کد.
// ورودی: آرایه‌ی کندل [{t,o,h,l,c,v}] — خروجی: [{t, dir:'bull'|'bear'|'neutral', label}]
// ─────────────────────────────────────────────────────────────────────────

const body = (c) => Math.abs(c.c - c.o);
const range = (c) => (c.h - c.l) || 1e-9;
const upperWick = (c) => c.h - Math.max(c.o, c.c);
const lowerWick = (c) => Math.min(c.o, c.c) - c.l;
const isBull = (c) => c.c >= c.o;
const isBear = (c) => c.c < c.o;

// میانگین اندازه‌ی بدنه روی n کندل قبل — برای سنجش «بزرگ/کوچک بودن»
const avgBody = (cs, i, n = 10) => {
  let s = 0, k = 0;
  for (let j = Math.max(0, i - n); j < i; j++) { s += body(cs[j]); k++; }
  return k ? s / k : body(cs[i]);
};

// روند کوتاه‌مدت پیش از الگو (برای الگوهای بازگشتی) — شیب close روی ~5 کندل
const priorTrend = (cs, i, n = 5) => {
  const a = cs[Math.max(0, i - n)], b = cs[Math.max(0, i - 1)];
  if (!a || !b) return 0;
  const d = b.c - a.c; const th = avgBody(cs, i) * 0.5;
  return d > th ? 1 : d < -th ? -1 : 0;
};

export function detectCandlePatterns(cs) {
  if (!cs || cs.length < 3) return [];
  const out = [];
  const push = (t, dir, label) => out.push({ t, dir, label });

  for (let i = 1; i < cs.length; i++) {
    const c = cs[i], p = cs[i - 1];
    const ab = avgBody(cs, i);
    const b = body(c), r = range(c), uw = upperWick(c), lw = lowerWick(c);
    const trend = priorTrend(cs, i);

    // ── تک‌کندلی ──
    // Doji — بدنه‌ی بسیار کوچک نسبت به دامنه
    if (b <= r * 0.1) {
      if (lw > r * 0.6 && uw < r * 0.15) push(c.t, 'bull', 'دراگون‌فلای دوجی');
      else if (uw > r * 0.6 && lw < r * 0.15) push(c.t, 'bear', 'گیوستون دوجی');
      else push(c.t, 'neutral', 'دوجی');
    }
    // Marubozu — بدنه‌ی بزرگ بدون سایه
    else if (b > ab * 1.3 && uw < r * 0.05 && lw < r * 0.05) {
      push(c.t, isBull(c) ? 'bull' : 'bear', isBull(c) ? 'ماروبوزوی صعودی' : 'ماروبوزوی نزولی');
    }
    // Hammer / Hanging Man — سایه‌ی پایینی بلند، بدنه‌ی کوچک بالا
    else if (lw >= b * 2 && uw <= b * 0.6 && b <= r * 0.4) {
      if (trend < 0) push(c.t, 'bull', 'چکش (Hammer)');
      else if (trend > 0) push(c.t, 'bear', 'مرد آویزان');
    }
    // Inverted Hammer / Shooting Star — سایه‌ی بالایی بلند
    else if (uw >= b * 2 && lw <= b * 0.6 && b <= r * 0.4) {
      if (trend < 0) push(c.t, 'bull', 'چکش معکوس');
      else if (trend > 0) push(c.t, 'bear', 'ستاره‌ی دنباله‌دار');
    }
    // Spinning Top — بدنه‌ی کوچک میانی با دو سایه‌ی بلند (بلاتکلیفی) — بزرگ‌تر از دوجی، هر دو سایه ≥ بدنه
    else if (b <= r * 0.35 && uw >= b && lw >= b && uw > r * 0.2 && lw > r * 0.2) {
      push(c.t, 'neutral', 'فرفره (Spinning Top)');
    }

    // ── دو‌کندلی ──
    // Engulfing — بدنه‌ی کندل فعلی، بدنه‌ی کندل قبل را کاملاً می‌پوشاند
    if (isBull(c) && isBear(p) && c.c >= p.o && c.o <= p.c && b > body(p)) push(c.t, 'bull', 'پوششی صعودی');
    if (isBear(c) && isBull(p) && c.o >= p.c && c.c <= p.o && b > body(p)) push(c.t, 'bear', 'پوششی نزولی');
    // Harami — بدنه‌ی کوچک فعلی داخل بدنه‌ی بزرگ قبل
    if (body(p) > ab && b < body(p) * 0.6 && Math.max(c.o, c.c) <= Math.max(p.o, p.c) && Math.min(c.o, c.c) >= Math.min(p.o, p.c)) {
      if (isBear(p) && isBull(c)) push(c.t, 'bull', 'هارامی صعودی');
      else if (isBull(p) && isBear(c)) push(c.t, 'bear', 'هارامی نزولی');
    }
    // Piercing / Dark Cloud — نفوذ به بیش از نیمه‌ی بدنه‌ی قبل
    if (isBear(p) && isBull(c) && c.o < p.l && c.c > (p.o + p.c) / 2 && c.c < p.o) push(c.t, 'bull', 'نفوذی (Piercing)');
    if (isBull(p) && isBear(c) && c.o > p.h && c.c < (p.o + p.c) / 2 && c.c > p.o) push(c.t, 'bear', 'ابر سیاه');
    // Tweezer — دو کندل متوالی با کف/سقف تقریباً یکسان (بازگشتی)؛ بدنه‌های معنادار و رنگ مخالف
    if (b > r * 0.2 && body(p) > range(p) * 0.2) {
      if (trend < 0 && isBear(p) && isBull(c) && Math.abs(c.l - p.l) <= r * 0.05) push(c.t, 'bull', 'انبرک کف');
      if (trend > 0 && isBull(p) && isBear(c) && Math.abs(c.h - p.h) <= r * 0.05) push(c.t, 'bear', 'انبرک سقف');
    }

    // ── سه‌کندلی ──
    if (i >= 2) {
      const q = cs[i - 2];
      // Morning / Evening Star — بدنه‌ی بزرگ، بدنه‌ی کوچک (گپ)، بدنه‌ی بزرگ مخالف
      if (isBear(q) && body(q) > ab && body(p) < body(q) * 0.5 && isBull(c) && c.c > (q.o + q.c) / 2) push(c.t, 'bull', 'ستاره‌ی صبح‌گاهی');
      if (isBull(q) && body(q) > ab && body(p) < body(q) * 0.5 && isBear(c) && c.c < (q.o + q.c) / 2) push(c.t, 'bear', 'ستاره‌ی شامگاهی');
      // Three White Soldiers / Three Black Crows
      if (isBull(q) && isBull(p) && isBull(c) && p.c > q.c && c.c > p.c && body(p) > ab * 0.6 && body(c) > ab * 0.6) push(c.t, 'bull', 'سه سرباز سفید');
      if (isBear(q) && isBear(p) && isBear(c) && p.c < q.c && c.c < p.c && body(p) > ab * 0.6 && body(c) > ab * 0.6) push(c.t, 'bear', 'سه کلاغ سیاه');
    }
  }

  // یک کندل ممکن است چند الگو داشته باشد؛ برچسب‌ها را در هر زمان ادغام می‌کنیم
  const byTime = new Map();
  out.forEach((o) => {
    const e = byTime.get(o.t);
    if (!e) byTime.set(o.t, { t: o.t, dir: o.dir, labels: [o.label] });
    else { e.labels.push(o.label); if (e.dir !== o.dir) e.dir = 'neutral'; }
  });
  return Array.from(byTime.values()).map((e) => ({ t: e.t, dir: e.dir, label: e.labels.join('، ') }));
}

export const PATTERN_COUNT = 19; // تعداد تقریبی الگوهای پوشش‌داده‌شده (برای نمایش UI)

// Names of the detector's patterns in English, for the English interface (the labels stay Persian
// in the engine's output, which the tests read).
export const PATTERN_EN = {
  // Chart patterns (Auto Chart Patterns).
  'سر و شانه': 'Head and Shoulders', 'سر و شانه‌ی معکوس': 'Inverse Head and Shoulders', 'سقف دوقلو': 'Double Top', 'کف دوقلو': 'Double Bottom',
  'سقف سه‌قلو': 'Triple Top', 'کف سه‌قلو': 'Triple Bottom', 'مثلث افزایشی': 'Ascending Triangle', 'مثلث کاهشی': 'Descending Triangle',
  'مثلث متقارن': 'Symmetrical Triangle', 'گوه‌ی صعودی': 'Rising Wedge', 'گوه‌ی نزولی': 'Falling Wedge', 'پرچم صعودی': 'Bull Flag', 'پرچم نزولی': 'Bear Flag',
  'دراگون‌فلای دوجی': 'Dragonfly Doji', 'گیوستون دوجی': 'Gravestone Doji', 'دوجی': 'Doji',
  'ماروبوزوی صعودی': 'Marubozu White', 'ماروبوزوی نزولی': 'Marubozu Black',
  'چکش (Hammer)': 'Hammer', 'مرد آویزان': 'Hanging Man', 'چکش معکوس': 'Inverted Hammer', 'ستاره‌ی دنباله‌دار': 'Shooting Star',
  'فرفره (Spinning Top)': 'Spinning Top', 'پوششی صعودی': 'Bullish Engulfing', 'پوششی نزولی': 'Bearish Engulfing',
  'هارامی صعودی': 'Bullish Harami', 'هارامی نزولی': 'Bearish Harami', 'نفوذی (Piercing)': 'Piercing', 'ابر سیاه': 'Dark Cloud Cover',
  'انبرک کف': 'Tweezer Bottom', 'انبرک سقف': 'Tweezer Top', 'ستاره‌ی صبح‌گاهی': 'Morning Star', 'ستاره‌ی شامگاهی': 'Evening Star',
  'سه سرباز سفید': 'Three White Soldiers', 'سه کلاغ سیاه': 'Three Black Crows',
};

/** A pattern label (or several, joined with «، ») in the given language. */
export function patternName(label, lang) {
  if (lang !== 'en' || !label) return label;
  return String(label).split('، ').map((x) => PATTERN_EN[x] ?? x).join(', ');
}

// ─────────────────────────────────────────────────────────────────────────
// The single candlestick patterns of TradingView's Patterns tab, one indicator each, by the
// published definitions of TradingView's built-in pattern scripts: body average = EMA(14) of the
// body, a trend filter on SMA(50) (or SMA(50) with SMA(200), or none), and each pattern's rule.
// ─────────────────────────────────────────────────────────────────────────
const smaArr = (v, p) => { const o = new Array(v.length).fill(null); let s = 0; for (let i = 0; i < v.length; i++) { s += v[i]; if (i >= p) s -= v[i - p]; if (i >= p - 1) o[i] = s / p; } return o; };
const emaArr = (v, p) => { const o = new Array(v.length).fill(null); const k = 2 / (p + 1); let e = null; for (let i = 0; i < v.length; i++) { e = e == null ? v[i] : v[i] * k + e * (1 - k); o[i] = e; } return o; };

function patternContext(cs, trend = 'SMA50') {
  const n = cs.length;
  const close = cs.map((c) => c.c);
  const hi = cs.map((c) => Math.max(c.o, c.c)); const lo = cs.map((c) => Math.min(c.o, c.c));
  const body = cs.map((c, i) => hi[i] - lo[i]);
  const avg = emaArr(body, 14);
  const s50 = smaArr(close, 50); const s200 = smaArr(close, 200);
  const up = new Array(n).fill(false); const dn = new Array(n).fill(false);
  for (let i = 0; i < n; i++) {
    if (trend === 'none') { up[i] = true; dn[i] = true; continue; }
    if (s50[i] == null) continue;
    if (trend === 'SMA50,SMA200') { if (s200[i] == null) continue; up[i] = close[i] > s50[i] && s50[i] > s200[i]; dn[i] = close[i] < s50[i] && s50[i] < s200[i]; }
    else { up[i] = close[i] > s50[i]; dn[i] = close[i] < s50[i]; }
  }
  const c = (i) => cs[i];
  const B = {
    hi, lo, body, avg, up, dn,
    rng: (i) => c(i).h - c(i).l,
    upS: (i) => c(i).h - hi[i], dnS: (i) => lo[i] - c(i).l,
    white: (i) => c(i).o < c(i).c, black: (i) => c(i).o > c(i).c,
    small: (i) => body[i] < avg[i], long: (i) => body[i] > avg[i],
    mid: (i) => body[i] / 2 + lo[i],
    hasUp: (i) => c(i).h - hi[i] > 0.05 * body[i], hasDn: (i) => lo[i] - c(i).l > 0.05 * body[i],
    dojiBody: (i) => (c(i).h - c(i).l) > 0 && body[i] <= (c(i).h - c(i).l) * 0.05,
  };
  B.shadowEq = (i) => { const u = B.upS(i), d = B.dnS(i); return u === d || ((d ? Math.abs(u - d) / d * 100 : Infinity) < 100 && (u ? Math.abs(d - u) / u * 100 : Infinity) < 100); };
  B.doji = (i) => B.dojiBody(i) && B.shadowEq(i);
  B.maru = (i) => B.long(i) && B.upS(i) <= 0.05 * body[i] && B.dnS(i) <= 0.05 * body[i];
  return B;
}

const P = (id, en, fa, abbr, dir, bars, rule) => ({ id, en, fa, abbr, dir, bars, rule });
const RULES = [
  P('abandonedBabyBear', 'Abandoned Baby - Bearish', 'بچه‌ی رهاشده‌ی نزولی', 'AB', 'bear', 3, (b, cs, i) => b.up[i - 2] && b.white(i - 2) && b.dojiBody(i - 1) && cs[i - 2].h < cs[i - 1].l && b.black(i) && cs[i - 1].l > cs[i].h),
  P('abandonedBabyBull', 'Abandoned Baby - Bullish', 'بچه‌ی رهاشده‌ی صعودی', 'AB', 'bull', 3, (b, cs, i) => b.dn[i - 2] && b.black(i - 2) && b.dojiBody(i - 1) && cs[i - 2].l > cs[i - 1].h && b.white(i) && cs[i - 1].h < cs[i].l),
  P('darkCloudCover', 'Dark Cloud Cover - Bearish', 'ابر سیاه پوشاننده‌ی نزولی', 'DCC', 'bear', 2, (b, cs, i) => b.up[i - 1] && b.white(i - 1) && b.long(i - 1) && b.black(i) && cs[i].o >= cs[i - 1].h && cs[i].c < b.mid(i - 1) && cs[i].c > cs[i - 1].o),
  P('doji', 'Doji', 'دوجی', 'D', 'neutral', 1, (b, cs, i) => b.doji(i) && !(b.dojiBody(i) && b.upS(i) <= b.body[i]) && !(b.dojiBody(i) && b.dnS(i) <= b.body[i])),
  P('dojiStarBear', 'Doji Star - Bearish', 'ستاره‌ی دوجی نزولی', 'DS', 'bear', 2, (b, cs, i) => b.up[i] && b.white(i - 1) && b.long(i - 1) && b.dojiBody(i) && b.lo[i] > b.hi[i - 1]),
  P('dojiStarBull', 'Doji Star - Bullish', 'ستاره‌ی دوجی صعودی', 'DS', 'bull', 2, (b, cs, i) => b.dn[i] && b.black(i - 1) && b.long(i - 1) && b.dojiBody(i) && b.hi[i] < b.lo[i - 1]),
  P('downsideTasukiGap', 'Downside Tasuki Gap - Bearish', 'شکاف تاسوکی نزولی', 'DTG', 'bear', 3, (b, cs, i) => b.long(i - 2) && b.small(i - 1) && b.dn[i] && b.black(i - 2) && b.hi[i - 1] < b.lo[i - 2] && b.black(i - 1) && b.white(i) && b.hi[i] <= b.lo[i - 2] && b.hi[i] >= b.hi[i - 1]),
  P('dragonflyDoji', 'Dragonfly Doji - Bullish', 'دوجی سنجاقک صعودی', 'DD', 'bull', 1, (b, cs, i) => b.dojiBody(i) && b.upS(i) <= b.body[i]),
  P('engulfingBear', 'Engulfing - Bearish', 'پوشا (اینگالفینگ) نزولی', 'BE', 'bear', 2, (b, cs, i) => b.up[i] && b.black(i) && b.long(i) && b.white(i - 1) && b.small(i - 1) && cs[i].c <= cs[i - 1].o && cs[i].o >= cs[i - 1].c && (cs[i].c < cs[i - 1].o || cs[i].o > cs[i - 1].c)),
  P('engulfingBull', 'Engulfing - Bullish', 'پوشا (اینگالفینگ) صعودی', 'BE', 'bull', 2, (b, cs, i) => b.dn[i] && b.white(i) && b.long(i) && b.black(i - 1) && b.small(i - 1) && cs[i].c >= cs[i - 1].o && cs[i].o <= cs[i - 1].c && (cs[i].c > cs[i - 1].o || cs[i].o < cs[i - 1].c)),
  P('eveningDojiStar', 'Evening Doji Star - Bearish', 'ستاره‌ی دوجی عصرگاهی', 'EDS', 'bear', 3, (b, cs, i) => b.long(i - 2) && b.dojiBody(i - 1) && b.long(i) && b.up[i] && b.white(i - 2) && b.lo[i - 1] > b.hi[i - 2] && b.black(i) && b.lo[i] <= b.mid(i - 2) && b.lo[i] > b.lo[i - 2] && b.lo[i - 1] > b.hi[i]),
  P('eveningStar', 'Evening Star - Bearish', 'ستاره‌ی عصرگاهی', 'ES', 'bear', 3, (b, cs, i) => b.long(i - 2) && b.small(i - 1) && b.long(i) && b.up[i] && b.white(i - 2) && b.lo[i - 1] > b.hi[i - 2] && b.black(i) && b.lo[i] <= b.mid(i - 2) && b.lo[i] > b.lo[i - 2] && b.lo[i - 1] > b.hi[i]),
  P('fallingThreeMethods', 'Falling Three Methods - Bearish', 'سه روش نزولی', 'F3M', 'bear', 5, (b, cs, i) => b.dn[i - 4] && b.long(i - 4) && b.black(i - 4) && [3, 2, 1].every((k) => b.small(i - k) && b.white(i - k) && cs[i - k].o > cs[i - 4].l && cs[i - k].c < cs[i - 4].h) && b.long(i) && b.black(i) && cs[i].c < cs[i - 4].c),
  P('fallingWindow', 'Falling Window - Bearish', 'پنجره‌ی نزولی', 'FW', 'bear', 2, (b, cs, i) => b.dn[i - 1] && b.rng(i) !== 0 && b.rng(i - 1) !== 0 && cs[i].h < cs[i - 1].l),
  P('gravestoneDoji', 'Gravestone Doji - Bearish', 'دوجی سنگ قبر نزولی', 'GD', 'bear', 1, (b, cs, i) => b.dojiBody(i) && b.dnS(i) <= b.body[i]),
  P('hammer', 'Hammer - Bullish', 'چکش صعودی', 'H', 'bull', 1, (b, cs, i) => b.small(i) && b.body[i] > 0 && b.lo[i] > (cs[i].h + cs[i].l) / 2 && b.dnS(i) >= 2 * b.body[i] && !b.hasUp(i) && b.dn[i]),
  P('hangingMan', 'Hanging Man - Bearish', 'مرد آویزان نزولی', 'HM', 'bear', 1, (b, cs, i) => b.small(i) && b.body[i] > 0 && b.lo[i] > (cs[i].h + cs[i].l) / 2 && b.dnS(i) >= 2 * b.body[i] && !b.hasUp(i) && b.up[i]),
  P('haramiBear', 'Harami - Bearish', 'هارامی نزولی', 'BH', 'bear', 2, (b, cs, i) => b.long(i - 1) && b.white(i - 1) && b.up[i - 1] && b.black(i) && b.small(i) && cs[i].h <= b.hi[i - 1] && cs[i].l >= b.lo[i - 1]),
  P('haramiBull', 'Harami - Bullish', 'هارامی صعودی', 'BH', 'bull', 2, (b, cs, i) => b.long(i - 1) && b.black(i - 1) && b.dn[i - 1] && b.white(i) && b.small(i) && cs[i].h <= b.hi[i - 1] && cs[i].l >= b.lo[i - 1]),
  P('haramiCrossBear', 'Harami Cross - Bearish', 'هارامی صلیبی نزولی', 'HC', 'bear', 2, (b, cs, i) => b.long(i - 1) && b.white(i - 1) && b.up[i - 1] && b.dojiBody(i) && cs[i].h <= b.hi[i - 1] && cs[i].l >= b.lo[i - 1]),
  P('haramiCrossBull', 'Harami Cross - Bullish', 'هارامی صلیبی صعودی', 'HC', 'bull', 2, (b, cs, i) => b.long(i - 1) && b.black(i - 1) && b.dn[i - 1] && b.dojiBody(i) && cs[i].h <= b.hi[i - 1] && cs[i].l >= b.lo[i - 1]),
  P('invertedHammer', 'Inverted Hammer - Bullish', 'چکش وارونه‌ی صعودی', 'IH', 'bull', 1, (b, cs, i) => b.small(i) && b.body[i] > 0 && b.hi[i] < (cs[i].h + cs[i].l) / 2 && b.upS(i) >= 2 * b.body[i] && !b.hasDn(i) && b.dn[i]),
  P('kickingBear', 'Kicking - Bearish', 'لگد نزولی', 'K', 'bear', 2, (b, cs, i) => b.maru(i - 1) && b.white(i - 1) && b.maru(i) && b.black(i) && cs[i - 1].l > cs[i].h),
  P('kickingBull', 'Kicking - Bullish', 'لگد صعودی', 'K', 'bull', 2, (b, cs, i) => b.maru(i - 1) && b.black(i - 1) && b.maru(i) && b.white(i) && cs[i - 1].h < cs[i].l),
  P('longLowerShadow', 'Long Lower Shadow - Bullish', 'سایه‌ی پایینی بلند', 'LLS', 'bull', 1, (b, cs, i) => b.dnS(i) > b.rng(i) * 0.75),
  P('longUpperShadow', 'Long Upper Shadow - Bearish', 'سایه‌ی بالایی بلند', 'LUS', 'bear', 1, (b, cs, i) => b.upS(i) > b.rng(i) * 0.75),
  P('marubozuBlack', 'Marubozu Black - Bearish', 'ماروبوزوی سیاه', 'MB', 'bear', 1, (b, cs, i) => b.black(i) && b.long(i) && b.upS(i) <= 0.05 * b.body[i] && b.dnS(i) <= 0.05 * b.body[i]),
  P('marubozuWhite', 'Marubozu White - Bullish', 'ماروبوزوی سفید', 'MW', 'bull', 1, (b, cs, i) => b.white(i) && b.long(i) && b.upS(i) <= 0.05 * b.body[i] && b.dnS(i) <= 0.05 * b.body[i]),
  P('morningDojiStar', 'Morning Doji Star - Bullish', 'ستاره‌ی دوجی صبح‌گاهی', 'MDS', 'bull', 3, (b, cs, i) => b.long(i - 2) && b.dojiBody(i - 1) && b.long(i) && b.dn[i] && b.black(i - 2) && b.hi[i - 1] < b.lo[i - 2] && b.white(i) && b.hi[i] >= b.mid(i - 2) && b.hi[i] < b.hi[i - 2] && b.hi[i - 1] < b.lo[i]),
  P('morningStar', 'Morning Star - Bullish', 'ستاره‌ی صبح‌گاهی', 'MS', 'bull', 3, (b, cs, i) => b.long(i - 2) && b.small(i - 1) && b.long(i) && b.dn[i] && b.black(i - 2) && b.hi[i - 1] < b.lo[i - 2] && b.white(i) && b.hi[i] >= b.mid(i - 2) && b.hi[i] < b.hi[i - 2] && b.hi[i - 1] < b.lo[i]),
  P('onNeck', 'On Neck - Bearish', 'روی گردن نزولی', 'ON', 'bear', 2, (b, cs, i) => b.dn[i] && b.black(i - 1) && b.long(i - 1) && b.white(i) && cs[i].o < cs[i - 1].c && b.small(i) && b.rng(i) !== 0 && Math.abs(cs[i].c - cs[i - 1].l) <= b.avg[i] * 0.05),
  P('piercing', 'Piercing - Bullish', 'نفوذی صعودی', 'P', 'bull', 2, (b, cs, i) => b.dn[i - 1] && b.black(i - 1) && b.long(i - 1) && b.white(i) && cs[i].o <= cs[i - 1].l && cs[i].c > b.mid(i - 1) && cs[i].c < cs[i - 1].o),
  P('risingThreeMethods', 'Rising Three Methods - Bullish', 'سه روش صعودی', 'R3M', 'bull', 5, (b, cs, i) => b.up[i - 4] && b.long(i - 4) && b.white(i - 4) && [3, 2, 1].every((k) => b.small(i - k) && b.black(i - k) && cs[i - k].o < cs[i - 4].h && cs[i - k].c > cs[i - 4].l) && b.long(i) && b.white(i) && cs[i].c > cs[i - 4].c),
  P('risingWindow', 'Rising Window - Bullish', 'پنجره‌ی صعودی', 'RW', 'bull', 2, (b, cs, i) => b.up[i - 1] && b.rng(i) !== 0 && b.rng(i - 1) !== 0 && cs[i].l > cs[i - 1].h),
  P('shootingStar', 'Shooting Star - Bearish', 'ستاره‌ی دنباله‌دار نزولی', 'SS', 'bear', 1, (b, cs, i) => b.small(i) && b.body[i] > 0 && b.hi[i] < (cs[i].h + cs[i].l) / 2 && b.upS(i) >= 2 * b.body[i] && !b.hasDn(i) && b.up[i]),
  P('spinningTopBlack', 'Spinning Top Black', 'فرفره‌ی سیاه', 'STB', 'neutral', 1, (b, cs, i) => b.dnS(i) >= b.rng(i) * 0.34 && b.upS(i) >= b.rng(i) * 0.34 && !b.dojiBody(i) && b.black(i)),
  P('spinningTopWhite', 'Spinning Top White', 'فرفره‌ی سفید', 'STW', 'neutral', 1, (b, cs, i) => b.dnS(i) >= b.rng(i) * 0.34 && b.upS(i) >= b.rng(i) * 0.34 && !b.dojiBody(i) && b.white(i)),
  P('threeBlackCrows', 'Three Black Crows - Bearish', 'سه کلاغ سیاه', '3BC', 'bear', 3, (b, cs, i) => [0, 1, 2].every((k) => b.black(i - k) && b.long(i - k) && b.rng(i - k) * 0.05 > b.dnS(i - k)) && cs[i].c < cs[i - 1].c && cs[i - 1].c < cs[i - 2].c && cs[i].o > cs[i - 1].c && cs[i].o < cs[i - 1].o && cs[i - 1].o > cs[i - 2].c && cs[i - 1].o < cs[i - 2].o),
  P('threeWhiteSoldiers', 'Three White Soldiers - Bullish', 'سه سرباز سفید', '3WS', 'bull', 3, (b, cs, i) => [0, 1, 2].every((k) => b.white(i - k) && b.long(i - k) && b.rng(i - k) * 0.05 > b.upS(i - k)) && cs[i].c > cs[i - 1].c && cs[i - 1].c > cs[i - 2].c && cs[i].o < cs[i - 1].c && cs[i].o > cs[i - 1].o && cs[i - 1].o < cs[i - 2].c && cs[i - 1].o > cs[i - 2].o),
  P('triStarBear', 'Tri-Star - Bearish', 'سه‌ستاره‌ی نزولی', '3S', 'bear', 3, (b, cs, i) => b.up[i - 2] && b.doji(i - 2) && b.doji(i - 1) && b.doji(i) && b.hi[i - 2] < b.lo[i - 1] && b.lo[i - 1] > b.hi[i]),
  P('triStarBull', 'Tri-Star - Bullish', 'سه‌ستاره‌ی صعودی', '3S', 'bull', 3, (b, cs, i) => b.dn[i - 2] && b.doji(i - 2) && b.doji(i - 1) && b.doji(i) && b.lo[i - 2] > b.hi[i - 1] && b.hi[i - 1] < b.lo[i]),
  P('tweezerBottom', 'Tweezer Bottom - Bullish', 'انبرک کف صعودی', 'TB', 'bull', 2, (b, cs, i) => b.dn[i - 1] && (!b.dojiBody(i) || (b.hasUp(i) && b.hasDn(i))) && Math.abs(cs[i].l - cs[i - 1].l) <= b.avg[i] * 0.05 && b.black(i - 1) && b.white(i) && b.long(i - 1)),
  P('tweezerTop', 'Tweezer Top - Bearish', 'انبرک سقف نزولی', 'TT', 'bear', 2, (b, cs, i) => b.up[i - 1] && (!b.dojiBody(i) || (b.hasUp(i) && b.hasDn(i))) && Math.abs(cs[i].h - cs[i - 1].h) <= b.avg[i] * 0.05 && b.white(i - 1) && b.black(i) && b.long(i - 1)),
  P('upsideTasukiGap', 'Upside Tasuki Gap - Bullish', 'شکاف تاسوکی صعودی', 'UTG', 'bull', 3, (b, cs, i) => b.long(i - 2) && b.small(i - 1) && b.up[i] && b.white(i - 2) && b.lo[i - 1] > b.hi[i - 2] && b.white(i - 1) && b.black(i) && b.lo[i] >= b.hi[i - 2] && b.lo[i] <= b.lo[i - 1]),
];

export const TV_PATTERNS = RULES.map(({ rule, ...p }) => p);

/** Bar indices where a pattern completes. */
export function detectPattern(id, cs, trend = 'SMA50') {
  const r = RULES.find((x) => x.id === id);
  if (!r || !cs || cs.length < r.bars) return [];
  const b = patternContext(cs, trend);
  const out = [];
  for (let i = Math.max(r.bars - 1, 1); i < cs.length; i++) {
    if (b.avg[i] == null) continue;
    try { if (r.rule(b, cs, i)) out.push(i); } catch { /* an edge bar */ }
  }
  return out;
}
