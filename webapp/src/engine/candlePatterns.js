// ─────────────────────────────────────────────────────────────────────────
// تشخیصِ خودکارِ الگوهای کندل‌استیک (مثلِ اندیکاتورِ Candlestick Patterns تریدینگ‌ویو)
// تعریف‌های کلاسیکِ تحلیلِ تکنیکال (دامنهٔ عمومی) — پیاده‌سازیِ اورجینال، بدون کپیِ کد.
// ورودی: آرایهٔ کندل [{t,o,h,l,c,v}] — خروجی: [{t, dir:'bull'|'bear'|'neutral', label}]
// ─────────────────────────────────────────────────────────────────────────

const body = (c) => Math.abs(c.c - c.o);
const range = (c) => (c.h - c.l) || 1e-9;
const upperWick = (c) => c.h - Math.max(c.o, c.c);
const lowerWick = (c) => Math.min(c.o, c.c) - c.l;
const isBull = (c) => c.c >= c.o;
const isBear = (c) => c.c < c.o;

// میانگینِ اندازهٔ بدنه روی n کندلِ قبل — برای سنجشِ «بزرگ/کوچک بودن»
const avgBody = (cs, i, n = 10) => {
  let s = 0, k = 0;
  for (let j = Math.max(0, i - n); j < i; j++) { s += body(cs[j]); k++; }
  return k ? s / k : body(cs[i]);
};

// روندِ کوتاه‌مدتِ پیش از الگو (برای الگوهای بازگشتی) — شیبِ close روی ~5 کندل
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
    // Doji — بدنهٔ بسیار کوچک نسبت به دامنه
    if (b <= r * 0.1) {
      if (lw > r * 0.6 && uw < r * 0.15) push(c.t, 'bull', 'دراگون‌فلای دوجی');
      else if (uw > r * 0.6 && lw < r * 0.15) push(c.t, 'bear', 'گِیوستون دوجی');
      else push(c.t, 'neutral', 'دوجی');
    }
    // Marubozu — بدنهٔ بزرگ بدونِ سایه
    else if (b > ab * 1.3 && uw < r * 0.05 && lw < r * 0.05) {
      push(c.t, isBull(c) ? 'bull' : 'bear', isBull(c) ? 'ماروبوزوی صعودی' : 'ماروبوزوی نزولی');
    }
    // Hammer / Hanging Man — سایهٔ پایینیِ بلند، بدنهٔ کوچکِ بالا
    else if (lw >= b * 2 && uw <= b * 0.6 && b <= r * 0.4) {
      if (trend < 0) push(c.t, 'bull', 'چکش (Hammer)');
      else if (trend > 0) push(c.t, 'bear', 'مرد آویزان');
    }
    // Inverted Hammer / Shooting Star — سایهٔ بالاییِ بلند
    else if (uw >= b * 2 && lw <= b * 0.6 && b <= r * 0.4) {
      if (trend < 0) push(c.t, 'bull', 'چکشِ معکوس');
      else if (trend > 0) push(c.t, 'bear', 'ستارهٔ دنباله‌دار');
    }
    // Spinning Top — بدنهٔ کوچکِ میانی با دو سایهٔ بلند (بلاتکلیفی) — بزرگ‌تر از دوجی، هر دو سایه ≥ بدنه
    else if (b <= r * 0.35 && uw >= b && lw >= b && uw > r * 0.2 && lw > r * 0.2) {
      push(c.t, 'neutral', 'فرفره (Spinning Top)');
    }

    // ── دو‌کندلی ──
    // Engulfing — بدنهٔ کندلِ فعلی، بدنهٔ کندلِ قبل را کاملاً می‌پوشاند
    if (isBull(c) && isBear(p) && c.c >= p.o && c.o <= p.c && b > body(p)) push(c.t, 'bull', 'پوششیِ صعودی');
    if (isBear(c) && isBull(p) && c.o >= p.c && c.c <= p.o && b > body(p)) push(c.t, 'bear', 'پوششیِ نزولی');
    // Harami — بدنهٔ کوچکِ فعلی داخلِ بدنهٔ بزرگِ قبل
    if (body(p) > ab && b < body(p) * 0.6 && Math.max(c.o, c.c) <= Math.max(p.o, p.c) && Math.min(c.o, c.c) >= Math.min(p.o, p.c)) {
      if (isBear(p) && isBull(c)) push(c.t, 'bull', 'هارامیِ صعودی');
      else if (isBull(p) && isBear(c)) push(c.t, 'bear', 'هارامیِ نزولی');
    }
    // Piercing / Dark Cloud — نفوذ به بیش از نیمهٔ بدنهٔ قبل
    if (isBear(p) && isBull(c) && c.o < p.l && c.c > (p.o + p.c) / 2 && c.c < p.o) push(c.t, 'bull', 'نفوذی (Piercing)');
    if (isBull(p) && isBear(c) && c.o > p.h && c.c < (p.o + p.c) / 2 && c.c > p.o) push(c.t, 'bear', 'ابرِ سیاه');
    // Tweezer — دو کندلِ متوالی با کف/سقفِ تقریباً یکسان (بازگشتی)؛ بدنه‌های معنادار و رنگِ مخالف
    if (b > r * 0.2 && body(p) > range(p) * 0.2) {
      if (trend < 0 && isBear(p) && isBull(c) && Math.abs(c.l - p.l) <= r * 0.05) push(c.t, 'bull', 'انبرکِ کف');
      if (trend > 0 && isBull(p) && isBear(c) && Math.abs(c.h - p.h) <= r * 0.05) push(c.t, 'bear', 'انبرکِ سقف');
    }

    // ── سه‌کندلی ──
    if (i >= 2) {
      const q = cs[i - 2];
      // Morning / Evening Star — بدنهٔ بزرگ، بدنهٔ کوچک (گپ)، بدنهٔ بزرگِ مخالف
      if (isBear(q) && body(q) > ab && body(p) < body(q) * 0.5 && isBull(c) && c.c > (q.o + q.c) / 2) push(c.t, 'bull', 'ستارهٔ صبح‌گاهی');
      if (isBull(q) && body(q) > ab && body(p) < body(q) * 0.5 && isBear(c) && c.c < (q.o + q.c) / 2) push(c.t, 'bear', 'ستارهٔ شامگاهی');
      // Three White Soldiers / Three Black Crows
      if (isBull(q) && isBull(p) && isBull(c) && p.c > q.c && c.c > p.c && body(p) > ab * 0.6 && body(c) > ab * 0.6) push(c.t, 'bull', 'سه سربازِ سفید');
      if (isBear(q) && isBear(p) && isBear(c) && p.c < q.c && c.c < p.c && body(p) > ab * 0.6 && body(c) > ab * 0.6) push(c.t, 'bear', 'سه کلاغِ سیاه');
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

export const PATTERN_COUNT = 19; // تعدادِ تقریبیِ الگوهای پوشش‌داده‌شده (برای نمایشِ UI)
