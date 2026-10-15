// بازارنما — سازندهٔ انواعِ چارتِ «غیراستاندارد» که داده را از OHLC بازنویسی می‌کنند.
// خروجی: آرایهٔ کندل {t,o,h,l,c} (برای Renko/Range/LineBreak) یا نقاطِ خط {t,value} (Kagi/PnF).
// زمان‌ها به‌صورتِ صعودیِ یکتا تخصیص می‌یابند (این چارت‌ها مستقل از زمان‌اند).

// میانگینِ بازهٔ واقعی برای اندازهٔ پیش‌فرضِ آجر/بازه
export function avgRange(cs, p = 14) {
  if (!cs.length) return 1;
  // ATRِ اخیر (میانگینِ True Rangeِ p بارِ آخر) — مثلِ TV؛ نه p بارِ قدیمی‌ترین.
  //   قبلاً از ایندکسِ ۰ می‌شمرد و زود break می‌زد ⇒ اندازهٔ خودکارِ آجرِ Renko/Range/Kagi
  //   بر پایهٔ نوسانِ ~۸۰۰ کندلِ پیش بود، نه نوسانِ فعلی. #481
  const start = Math.max(1, cs.length - p);
  let sum = 0, n = 0;
  for (let i = start; i < cs.length; i++) {
    const tr = Math.max(cs[i].h - cs[i].l, Math.abs(cs[i].h - cs[i - 1].c), Math.abs(cs[i].l - cs[i - 1].c));
    sum += tr; n++;
  }
  return n ? sum / n : (cs[cs.length - 1].h - cs[cs.length - 1].l) || 1;
}

// تخصیصِ زمانِ صعودیِ یکتا
function timer() { let last = 0; return (t) => { const v = Math.max(t || 0, last + 1); last = v; return v; }; }

// ── Renko ── (آجرها بر پایهٔ close)
export function renko(cs, brick, atrLen) {
  if (!cs.length) return [];
  brick = brick || avgRange(cs, atrLen || 14) || 1;
  const out = []; const nt = timer();
  // دو دیوارِ top/bot: ادامهٔ روند فقط ۱ آجر می‌خواهد ولی بازگشت باید ۲ آجر حرکت کند (قانونِ استانداردِ Renko/TV).
  // قبلاً یک baseِ تکی بود که بعد از فقط ۱ آجرِ برگشت آجرِ معکوس می‌زد ⇒ بازگشت‌های جعلیِ اضافی (نمای «نازک/پرنویز»).
  let top = cs[0].c, bot = cs[0].c;
  for (const c of cs) {
    const price = c.c;
    while (price >= top + brick) { const o = top, cl = top + brick; out.push({ t: nt(c.t), o, h: cl, l: o, c: cl }); top += brick; bot = top - brick; }
    while (price <= bot - brick) { const o = bot, cl = bot - brick; out.push({ t: nt(c.t), o, h: o, l: cl, c: cl }); bot -= brick; top = bot + brick; }
  }
  return out;
}

// ── Range bars ── (هر بار وقتی قیمت به اندازهٔ rng حرکت کند)
export function rangeBars(cs, rng) {
  if (!cs.length) return [];
  rng = rng || avgRange(cs) || 1;
  const out = []; const nt = timer();
  let o = cs[0].o, hi = cs[0].o, lo = cs[0].o;
  for (const c of cs) {
    [c.o, c.h, c.l, c.c].forEach((px) => {
      hi = Math.max(hi, px); lo = Math.min(lo, px);
      if (hi - lo >= rng) { out.push({ t: nt(c.t), o, h: hi, l: lo, c: px }); o = px; hi = px; lo = px; }
    });
  }
  return out;
}

// ── Line Break ── (شکستِ n خط)
export function lineBreak(cs, n = 3) {
  if (!cs.length) return [];
  const out = []; const nt = timer();
  const lines = []; // {o,c}
  for (const c of cs) {
    const price = c.c;
    if (!lines.length) { if (cs[0] && Math.abs(price - cs[0].o) > 1e-9) { lines.push({ o: cs[0].o, c: price }); out.push({ t: nt(c.t), o: cs[0].o, h: Math.max(cs[0].o, price), l: Math.min(cs[0].o, price), c: price }); } continue; }
    const last = lines[lines.length - 1];
    const ref = lines.slice(-n);
    const hi = Math.max(...ref.map((l) => Math.max(l.o, l.c)));
    const lo = Math.min(...ref.map((l) => Math.min(l.o, l.c)));
    const up = last.c >= last.o;
    if (up && price > last.c) { lines.push({ o: last.c, c: price }); out.push({ t: nt(c.t), o: last.c, h: price, l: last.c, c: price }); }
    else if (!up && price < last.c) { lines.push({ o: last.c, c: price }); out.push({ t: nt(c.t), o: last.c, h: last.c, l: price, c: price }); }
    else if (up && price < lo) { lines.push({ o: lo, c: price }); out.push({ t: nt(c.t), o: lo, h: lo, l: price, c: price }); }
    else if (!up && price > hi) { lines.push({ o: hi, c: price }); out.push({ t: nt(c.t), o: hi, h: price, l: hi, c: price }); }
  }
  return out;
}

// ── Kagi ── (خطِ پیوسته که با برگشتِ ≥reversal جهت عوض می‌کند) → نقاطِ خط
export function kagi(cs, reversal, atrLen) {
  if (!cs.length) return [];
  reversal = reversal || avgRange(cs, atrLen || 14) || 1;
  const nt = timer();
  const pts = [{ t: nt(cs[0].t), value: cs[0].c }];
  let dir = 0, ext = cs[0].c;
  for (const c of cs) {
    const p = c.c;
    if (dir >= 0 && p > ext) { ext = p; pts[pts.length - 1] = { t: nt(c.t), value: p }; dir = 1; }
    else if (dir <= 0 && p < ext) { ext = p; pts[pts.length - 1] = { t: nt(c.t), value: p }; dir = -1; }
    else if (dir === 1 && p <= ext - reversal) { pts.push({ t: nt(c.t), value: p }); ext = p; dir = -1; }
    else if (dir === -1 && p >= ext + reversal) { pts.push({ t: nt(c.t), value: p }); ext = p; dir = 1; }
  }
  return pts;
}

// ── Point & Figure ── (سادهٔ ستونی) → نقاطِ خطِ سقف/کفِ ستون‌ها
export function pnf(cs, box, reversal = 3, atrLen) {
  if (!cs.length) return [];
  box = box || avgRange(cs, atrLen || 14) || 1;
  const nt = timer();
  const pts = []; let dir = 0, ext = cs[0].c;
  for (const c of cs) {
    const p = c.c;
    if (dir >= 0 && p >= ext + box) { ext = ext + Math.floor((p - ext) / box) * box; pts.push({ t: nt(c.t), value: ext }); dir = 1; }
    else if (dir <= 0 && p <= ext - box) { ext = ext - Math.floor((ext - p) / box) * box; pts.push({ t: nt(c.t), value: ext }); dir = -1; }
    else if (dir === 1 && p <= ext - box * reversal) { ext = p; pts.push({ t: nt(c.t), value: ext }); dir = -1; }
    else if (dir === -1 && p >= ext + box * reversal) { ext = p; pts.push({ t: nt(c.t), value: ext }); dir = 1; }
  }
  return pts;
}

// ── Volume (هیستوگرامِ حجم، سبک TradingView) ──
// overlay روی مقیاسِ قیمتِ مستقل ('' → جدا از مقیاسِ اصلی) و چسبیده به کفِ چارت،
// نیمه‌شفاف، رنگِ هر میله سبز/قرمز بر پایهٔ جهتِ کندل (close≥open). پیش‌فرض روشن.
export const VOL_UP = 'rgba(8,153,129,.5)';   // سبزِ up مثلِ پالتِ فعلیِ TV (#089981، نیمه‌شفاف) — هم‌تراز با کندل‌ها
export const VOL_DOWN = 'rgba(242,54,69,.5)';  // قرمزِ down مثلِ پالتِ فعلیِ TV (#f23645، نیمه‌شفاف) — هم‌تراز با کندل‌ها

// دادهٔ هیستوگرامِ حجم: هر نقطه {time,value,color} — رنگ بر پایهٔ up/down.
export function volumeData(cs, opts = {}) {
  if (!cs || !cs.length) return [];
  const up = opts.up || VOL_UP, down = opts.down || VOL_DOWN;
  return cs.map((c) => ({ time: c.t, value: c.v || 0, color: (c.c >= c.o) ? up : down }));
}

// یک نقطهٔ حجم برای live-update (HistogramSeries.update).
export function volumePoint(c, opts = {}) {
  const up = opts.up || VOL_UP, down = opts.down || VOL_DOWN;
  return { time: c.t, value: (c && c.v) || 0, color: ((c && c.c >= c.o) ? up : down) };
}

// گزینه‌های سریِ هیستوگرامِ حجم (priceScaleId جدا، فرمتِ حجم، بدونِ خط/برچسبِ قیمت).
export function volumeOptions(opts = {}) {
  return {
    priceScaleId: opts.priceScaleId != null ? opts.priceScaleId : '',
    priceFormat: { type: 'volume' },
    lastValueVisible: false,
    priceLineVisible: false,
  };
}

// حاشیهٔ مقیاس تا حجم فقط ~۱۵٪ پایینِ چارت را بگیرد و مقیاسِ اصلی را فشرده نکند.
// top بزرگ‌تر ⇒ نوارِ حجمِ کوتاه‌تر (0.85 ≈ ۱۵٪ پایین).
export function volumeScaleMargins(opts = {}) {
  return { top: opts.top != null ? opts.top : 0.85, bottom: opts.bottom != null ? opts.bottom : 0 };
}

// spec کامل برای فراخواننده: chart.addSeries(HistogramSeries, spec.options)،
// سپس priceScale().applyOptions({ scaleMargins: spec.scaleMargins })، و s.setData(spec.data).
export function buildVolume(cs, opts = {}) {
  return {
    kind: 'histogram',
    options: volumeOptions(opts),
    scaleMargins: volumeScaleMargins(opts),
    data: volumeData(cs, opts),
    on: true, // پیش‌فرض روشن (مثلِ TradingView)
  };
}

export const NONSTANDARD = ['renko', 'range', 'linebreak', 'kagi', 'pnf'];
// عددِ مثبتِ معتبر یا undefined (تا سازنده به پیش‌فرضِ avgRange برگردد). ورودیِ خالی/۰/نامعتبر ⇒ undefined.
function posOr(v) { const n = Number(v); return (v != null && v !== '' && Number.isFinite(n) && n > 0) ? n : undefined; }
export function buildNonStandard(type, cs, p = {}) {
  if (type === 'renko') return { kind: 'candle', data: renko(cs, posOr(p.renkoBrick), posOr(p.atrLen)) };
  if (type === 'range') return { kind: 'candle', data: rangeBars(cs, posOr(p.rangeSize)) };
  if (type === 'linebreak') return { kind: 'candle', data: lineBreak(cs, posOr(p.lineBreakLines) || 3) };
  if (type === 'kagi') return { kind: 'line', data: kagi(cs, posOr(p.kagiReversal), posOr(p.atrLen)) };
  if (type === 'pnf') return { kind: 'line', data: pnf(cs, posOr(p.pnfBox), posOr(p.pnfReversal) || 3, posOr(p.atrLen)) };
  return null;
}
