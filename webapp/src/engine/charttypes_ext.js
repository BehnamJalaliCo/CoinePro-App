// بازارنما — افزونه‌ی «انواع چارت» (فصل ۲ از PRO_CHART_BUILD_SPEC).
// این فایل کاملاً additive است: هیچ فایل مشترکی را تغییر نمی‌دهد و با یک خط import + چند انشعاب
// کوچک در buildPriceSeries مرج می‌شود. تابع‌ها «خالص»‌اند (pure transform/spec) و دقیقاً مطابق
// شکل خروجی chartbuilders.js داده می‌سازند تا موتور lightweight-charts v5 آن‌ها را رندر کند.
//
// قرارداد داده: کندل خام {t,o,h,l,c,v}  —  t برحسب ثانیه‌ی یونیکس.
// آداپتورهای موتور (مطابق BazaarNama.jsx):
//   valSeries(cs) → [{ time:c.t, value:c.c }]
//   ohlc(cs)      → [{ time:c.t, open:c.o, high:c.h, low:c.l, close:c.c }]
//
// انواع پیاده‌سازی‌شده در این فایل:
//   ۴  — Volume Candles (کندل حجمی، رنگ بر پایه‌ی close/open + opacity ∝ حجم)
//   ۶  — Line with Markers (خط با نشانگر نقطه‌ای)
//   ۹  — HLC Area (ناحیه‌ی close + دو خط کم‌رنگ high/low) — تنها نوع چندسری
//   ۱۱ — Columns (هیستوگرام close با رنگ جهت)
//   ۱۲ — High-Low (تنها بازه‌ی high–low هر بار، بدون open/close)
//   به‌علاوه ارتقای رندر Kagi (ضخامت yang/yin) و P&F (ستون‌های X/O) طبق §2.13/§2.14
//
//   فاز۳ (تعمیق انواع غیرزمانی هم‌تراز TV — additive، انتهای فایل):
//     Heikin-Ashi (heikinAshi) · Hollow Candles (hollowCandles) · Renko رنگی+ATR/wick (renkoBricks)
//     · Range رنگی (rangeBarsColored) · Line-Break رنگی+n (lineBreakColored) + dispatcher buildExtNonStandard.

import { avgRange, kagi, rangeBars, lineBreak } from './chartbuilders.js';

// ────────────────────────────────────────────────────────────────────────────
//  ابزارهای کوچک
// ────────────────────────────────────────────────────────────────────────────

// تبدیل رنگ hex به rgba با آلفای دلخواه (برای تینت‌های کم‌رنگ و opacity حجمی)
function withAlpha(color, a) {
  if (!color) return `rgba(0,0,0,${a})`;
  if (color.startsWith('rgba')) return color.replace(/[\d.]+\)$/, `${a})`);
  if (color.startsWith('rgb('))  return color.replace('rgb(', 'rgba(').replace(')', `,${a})`);
  let h = color.replace('#', '');
  if (h.length === 3) h = h.split('').map((x) => x + x).join('');
  const r = parseInt(h.slice(0, 2), 16) || 0;
  const g = parseInt(h.slice(2, 4), 16) || 0;
  const b = parseInt(h.slice(4, 6), 16) || 0;
  return `rgba(${r},${g},${b},${a})`;
}
const clamp = (lo, hi, x) => Math.max(lo, Math.min(hi, x));

// رنگ‌های پیش‌فرض (اگر فراخواننده تم را پاس ندهد) — هم‌تراز با THEMES.dark در BazaarNama.jsx
const DEF = { up: '#089981', down: '#f23645', accent: '#2962FF' };

// ────────────────────────────────────────────────────────────────────────────
//  ۴ — Volume Candles  (نوع «کندل»)
//  خروجی: آرایه‌ی کندل سازگار با CandlestickSeries که هر نقطه «color/borderColor/wickColor»
//  اختصاصی دارد؛ شفافیت بدنه متناسب با حجم همان بار است (تقریب Equivolume بدون CustomSeries).
//  رنگ بدنه: up/down بر پایه‌ی close≥open. آلفا در بازه‌ی [0.25,1] بر پایه‌ی vol/maxVol.
//  مصرف: chart.addSeries(CandlestickSeries, {...}); s.setData(volumeCandles(cs, {up,down}));
// ────────────────────────────────────────────────────────────────────────────
export function volumeCandles(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  let maxV = 0;
  for (const c of cs) maxV = Math.max(maxV, c.v || 0);
  return cs.map((c) => {
    const bull = c.c >= c.o;
    const base = bull ? up : down;
    // آلفای بدنه ∝ حجم؛ کف 0.25 تا همیشه دیده شود
    const a = maxV > 0 ? clamp(0.25, 1, 0.25 + 0.75 * ((c.v || 0) / maxV)) : 0.85;
    return {
      time: c.t, open: c.o, high: c.h, low: c.l, close: c.c,
      color: withAlpha(base, a), borderColor: base, wickColor: base,
    };
  });
}

// نکته: برای رندر «تقریبی Route B» (کندل عادی + ساب‌پن حجم) از همین کندل‌ها استفاده کنید
// و یک HistogramSeries جدا برای حجم بسازید. این تابع، مسیر تک‌سری رنگ‌محور را می‌دهد.
// داده‌ی حجم برای ساب‌پن اختیاری:
export function volumeHistogram(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  return cs.map((c) => ({ time: c.t, value: c.v || 0, color: withAlpha(c.c >= c.o ? up : down, 0.5) }));
}

// ────────────────────────────────────────────────────────────────────────────
//  ۶ — Line with Markers  (نوع «خط مقدار»)
//  همان داده‌ی خطی (close)؛ رندر با LineSeries و گزینه‌ی نیتیو pointMarkersVisible:true.
//  این تابع داده را می‌دهد و lineMarkerOptions گزینه‌های سری را.
// ────────────────────────────────────────────────────────────────────────────
export function lineMarkersData(cs) {
  return cs.map((c) => ({ time: c.t, value: c.c }));
}
export function lineMarkerOptions(opts = {}) {
  return {
    color: opts.accent || DEF.accent,
    lineWidth: 2,
    pointMarkersVisible: true,
    pointMarkersRadius: opts.radius || 3,
  };
}

// ────────────────────────────────────────────────────────────────────────────
//  ۹ — HLC Area  (نوع «چندسری»: یک AreaSeries + دو LineSeries)
//  خروجی یک spec است: { area:{options,data}, aux:[{ctor:'line',options,data}, ...] }
//  «area» سری اصلی/anchor ترسیم است؛ «aux» سری‌های فرعی (high/low) که باید removeSeries شوند.
// ────────────────────────────────────────────────────────────────────────────
export function hlcAreaSpec(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down, accent = opts.accent || DEF.accent;
  return {
    area: {
      options: { lineColor: accent, topColor: withAlpha(accent, 0.35), bottomColor: withAlpha(accent, 0) },
      data: cs.map((c) => ({ time: c.t, value: c.c })),
    },
    aux: [
      { ctor: 'line', role: 'high', options: { color: withAlpha(up, 0.35), lineWidth: 1, lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false }, data: cs.map((c) => ({ time: c.t, value: c.h })) },
      { ctor: 'line', role: 'low',  options: { color: withAlpha(down, 0.35), lineWidth: 1, lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false }, data: cs.map((c) => ({ time: c.t, value: c.l })) },
    ],
  };
}

// ────────────────────────────────────────────────────────────────────────────
//  ۱۱ — Columns  (نوع «هیستوگرام»)
//  هیستوگرام close؛ رنگ هر ستون بر پایه‌ی جهت (close≥prevClose).
//  مصرف: chart.addSeries(HistogramSeries, columnsOptions()); s.setData(columnsData(cs, {up,down}));
//  هشدار live-update: HistogramSeries.update({time,value,color}) — انشعاب جدا لازم است.
// ────────────────────────────────────────────────────────────────────────────
export function columnsData(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  let prev = cs[0].c;
  return cs.map((c) => {
    const rising = c.c >= prev; prev = c.c;
    return { time: c.t, value: c.c, color: rising ? up : down };
  });
}
export function columnsOptions() {
  return { priceLineVisible: false, lastValueVisible: true };
}
// نقطه‌ی live برای Columns (محاسبه‌ی رنگ نسبت به کندل ماقبل آخر)
export function columnsLivePoint(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return null;
  const last = cs[cs.length - 1];
  const prev = cs.length > 1 ? cs[cs.length - 2].c : last.c;
  return { time: last.t, value: last.c, color: last.c >= prev ? up : down };
}

// ────────────────────────────────────────────────────────────────────────────
//  ۱۲ — High-Low  (نوع «کندل»، Strategy A از §2.8)
//  CandlestickSeries با open=low, close=high و بدون بوردر/ویک ⇒ میله‌ی توپُر high–low.
//  رنگ بر پایه‌ی close-vs-prevClose (جهت بسته‌شدن، چون open/close واقعی پنهان است).
//  مصرف: chart.addSeries(CandlestickSeries, highLowOptions()); s.setData(highLowData(cs, {up,down}));
// ────────────────────────────────────────────────────────────────────────────
export function highLowData(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  let prevC = cs[0].c;
  return cs.map((c) => {
    const rising = c.c >= prevC; prevC = c.c;
    const col = rising ? up : down;
    // open=low, close=high تا بدنه از کف تا سقف کشیده شود
    return { time: c.t, open: c.l, high: c.h, low: c.l, close: c.h, color: col, borderColor: col, wickColor: 'rgba(0,0,0,0)' };
  });
}
export function highLowOptions() {
  return { borderVisible: false, wickUpColor: 'rgba(0,0,0,0)', wickDownColor: 'rgba(0,0,0,0)' };
}

// ────────────────────────────────────────────────────────────────────────────
//  ارتقای Kagi — ضخامت yang/yin  (§2.13)
//  از kagi() موجود نقاط را می‌گیریم و آن را به «قطعات راست‌گوشه با ضخامت متغیر» می‌شکنیم.
//  چون LineSeries عرض یکنواخت دارد، خروجی را به‌صورت مجموعه‌ای از سری‌های خطی مجزا می‌دهیم:
//  هر «segment-run» با ضخامت ثابت (yang=۳، yin=۱) یک سری جدا با نقاط راست‌گوشه است.
//  این spec بدون CustomSeries، رفتار TradingView (شانه/کمر) را تقریب می‌زند.
//
//  خروجی: { segments: [ { thick:Boolean, points:[{time,value}, ...] }, ... ] }
//  راست‌گوشه‌سازی: بین دو رأس a→b یک نقطه‌ی میانی (b.time, a.value) درج می‌شود تا
//  پله‌ای (افقی سپس عمودی) رسم شود؛ ضخامت بر پایه‌ی عبور از shoulder/waist تعیین می‌گردد.
// ────────────────────────────────────────────────────────────────────────────
export function kagiSpec(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  const pts = kagi(cs, opts.reversal, opts.atrLen); // [{t,value}, ...]
  if (pts.length < 2) {
    return { segments: pts.length ? [{ thick: false, color: up, points: pts.map((p) => ({ time: p.t, value: p.value })) }] : [] };
  }
  // پاس تعیین ضخامت: خط Kagi وقتی از shoulder *پیشین* (قله‌ی قبلی) بالاتر رود yang (ضخیم/سبز)
  // و وقتی زیر waist پیشین (درّه‌ی قبلی) افت کند yin (نازک/قرمز) می‌شود. shoulder/waist در هر
  // برگشت به‌روز می‌شوند — نه بیشینه/کمینه‌ی سراسری. (باگ قبلی: با max/min سراسری، در روند
  // نزولی سقف اولیه دیگر شکسته نمی‌شد ⇒ خط همیشه نازک/قرمز می‌ماند.)
  let thick = false, lastShoulder = -Infinity, lastWaist = Infinity;
  const flags = []; // ضخامت هر قطعه‌ی a→b (به ازای i از 1..n-1)
  for (let i = 1; i < pts.length; i++) {
    const prev = pts[i - 1].value, cur = pts[i].value;
    if (cur > prev) {           // قطعه‌ی صعودی — prev یک درّه (waist) است
      if (lastShoulder !== -Infinity && cur > lastShoulder) thick = true;
      lastWaist = prev;
    } else if (cur < prev) {    // قطعه‌ی نزولی — prev یک قله (shoulder) است
      if (lastWaist !== Infinity && cur < lastWaist) thick = false;
      lastShoulder = prev;
    }
    flags.push(thick);
  }
  // نقاط راست‌گوشه + خرد کردن به run هایی با ضخامت یکسان
  const segments = [];
  let cur = null;
  for (let i = 1; i < pts.length; i++) {
    const a = pts[i - 1], b = pts[i];
    const t = flags[i - 1];
    const corner = { time: b.t - 1, value: a.value };  // پله: افقی تا لحظه‌ی قبل b، سپس عمودی. زمان منحصربه‌فرد (نه دوتایی b.t) تا سری خطی lightweight-charts خطای «Value is null»/ترتیب ندهد.
    if (!cur || cur.thick !== t) {
      if (cur) segments.push(cur);
      cur = { thick: t, color: t ? up : down, points: [{ time: a.t, value: a.value }] };
    }
    cur.points.push({ time: corner.time, value: corner.value });
    cur.points.push({ time: b.t, value: b.value });
  }
  if (cur) segments.push(cur);
  return { segments };
}
// گزینه‌های سری برای هر قطعه‌ی Kagi
export function kagiSegmentOptions(seg) {
  return { color: seg.color, lineWidth: seg.thick ? 3 : 1, lineJoin: 'miter', priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false };
}

// ────────────────────────────────────────────────────────────────────────────
//  ارتقای Point & Figure — ستون‌های X/O  (§2.14)
//  مدل ستون جعبه‌ای: هر ستون یک جهت (+1=X صعودی / -1=O نزولی) و بازه‌ی from..to (در واحد box) دارد.
//  چون CustomSeries نداریم، گلیف‌های X/O را به‌صورت «نشانگر نقطه‌ای روی یک سری خطی نامرئی»
//  رندر می‌کنیم: برای هر سلول جعبه یک نقطه با marker متناظر می‌سازیم.
//  هر ستون یک «زمان» یکتا (ایندکس ستون) می‌گیرد تا ستون‌ها کنار هم بنشینند.
//
//  خروجی: {
//    cells:    [{ time, value }, ...]      ← همه‌ی سلول‌ها (داده‌ی یک LineSeries نامرئی برای autoscale)
//    markers:  [{ time, position:'inBar', color, shape:'circle'|'square', text:'X'|'O' }, ...]
//    box:      اندازه‌ی جعبه (برای نمایش/تنظیمات)
//  }
//  نشانگرها از طریق createSeriesMarkers(series, markers) به سری نامرئی وصل می‌شوند.
// ────────────────────────────────────────────────────────────────────────────
export function pnfColumns(cs, opts = {}) {
  if (!cs.length) return { cells: [], markers: [], columns: [], box: 1 };
  const box = opts.box || avgRange(cs, opts.atrLen || 14) || 1;
  const reversal = opts.reversal || 3;
  const q = (p) => Math.round(p / box) * box;          // کوانتایز قیمت به مضرب box
  const columns = [];                                   // { dir:+1/-1, from, to }
  let dir = 0, col = null;
  for (const c of cs) {
    const bp = q(opts.source === 'hl' ? (dir >= 0 ? c.h : c.l) : c.c);
    if (dir === 0) {
      // ستون آغازین — جهت را با اولین حرکت ≥box تعیین می‌کنیم
      if (!col) { col = { dir: 1, from: bp, to: bp }; }
      if (bp >= col.to + box) { dir = 1; col.dir = 1; col.to = bp; }
      else if (bp <= col.from - box) { dir = -1; col.dir = -1; col.from = bp; const tmp = col.from; col.from = col.to; col.to = tmp; }
      continue;
    }
    if (dir === 1) {
      if (bp >= col.to + box) { col.to = bp; }                       // ادامه‌ی ستون X رو به بالا
      else if (bp <= col.to - reversal * box) {                       // برگشت → ستون O جدید
        columns.push(col);
        col = { dir: -1, from: col.to - box, to: bp }; dir = -1;
      }
    } else { // dir === -1
      if (bp <= col.to - box) { col.to = bp; }                        // ادامه‌ی ستون O رو به پایین
      else if (bp >= col.to + reversal * box) {                       // برگشت → ستون X جدید
        columns.push(col);
        col = { dir: 1, from: col.to + box, to: bp }; dir = 1;
      }
    }
  }
  if (col) columns.push(col);

  // رندر: هر ستون یک «time» یکتا — ستون‌ها به‌طور یکنواخت روی کل بازه‌ی زمانی کندل‌ها پخش می‌شوند
  // تا P&F مثل TV در سراسر عرض چارت دیده شود (نه فشرده در یک ثانیه لبه‌ی چپ).
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  const t0 = cs[0].t, tEnd = cs[cs.length - 1].t;
  const step = columns.length > 1 ? Math.max(2, (tEnd - t0) / (columns.length - 1)) : 2;
  const cells = [];
  const markers = [];
  // هر گلیف X/O یک «time» یکتاست — lightweight-charts نشانگرها را فقط با زمان‌های
  // صعودی یکتا رندر می‌کند؛ زمان تکراری ⇒ هیچ نشانگری کشیده نمی‌شود (علت خالی‌بودن P&F).
  // پس درون هر ستون هر سطح را با آفست ۱ثانیه‌ای یکتا می‌گذاریم؛ در زووم عادی این آفست‌ها
  // به یک پیکسل جمع می‌شوند ⇒ ستون عمودی X/O مثل TV. یکتایی هم خطای «Value is null» را می‌بندد.
  // segments: هر ستون یک خط نزدیک‌به‌عمودی رنگی (lo→hi با دو زمان t,t+1 که در زووم عادی
  // به یک پیکسل جمع می‌شوند ⇒ ستون عمودی). سبز X برای صعودی، قرمز O برای نزولی. این روش
  // مطمئن رندر می‌شود (برخلاف markers که روی خط نامرئی در این نسخه کشیده نمی‌شوند).
  const segments = [];
  let lastT = t0 - 1;
  columns.forEach((cl, idx) => {
    const baseTime = Math.round(t0 + idx * step);
    const lo = Math.min(cl.from, cl.to), hi = Math.max(cl.from, cl.to);
    // خط ستون نزدیک‌عمودی — hi را حداقل نیم‌box بالاتر از lo نگه می‌داریم تا حتی ستون تک‌جعبه هم دیده شود.
    const top = Math.max(hi, lo + box * 0.5);
    segments.push({ dir: cl.dir, color: cl.dir > 0 ? up : down, points: [{ time: baseTime, value: lo }, { time: baseTime + 1, value: top }] });
    // cells/markers هم نگه داشته می‌شوند (autoscale + سازگاری) — زمان‌های یکتای صعودی.
    let t = Math.max(baseTime, lastT + 1);
    for (let lvl = lo; lvl <= hi + 1e-9; lvl += box) {
      cells.push({ time: t, value: lvl });
      markers.push({ time: t, position: 'inBar', color: cl.dir > 0 ? up : down, shape: cl.dir > 0 ? 'square' : 'circle' });
      lastT = t;
      t += 1;
    }
  });
  return { cells, markers, columns, segments, box };
}
// گزینه‌های سری نامرئی میزبان گلیف‌های P&F
export function pnfHostOptions() {
  return { color: 'rgba(0,0,0,0)', lineWidth: 1, pointMarkersVisible: false, lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false };
}
// گزینه‌های خط ضخیم هر ستون P&F (نمایش عمودی رنگی، بی‌خط/برچسب قیمت)
export function pnfSegmentOptions(seg) {
  return { color: seg.color, lineWidth: 6, lineJoin: 'round', priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false };
}

// ────────────────────────────────────────────────────────────────────────────
//  فهرست انواع جدید برای مرج با CHART_TYPES (برچسب فارسی).
//  Icon را عمداً نمی‌گذارم تا import آیکون اضافه لازم نشود؛ فراخواننده می‌تواند Icon اضافه کند.
//  (BazaarNama.jsx آیکون را اختیاری مصرف می‌کند.)
// ────────────────────────────────────────────────────────────────────────────
export const EXT_CHART_TYPES = [
  { id: 'volcandles', label: 'کندل حجمی' },
  { id: 'lwm',        label: 'خطی با نشانگر' },
  { id: 'hlcarea',    label: 'ناحیه‌ی HLC' },
  { id: 'columns',    label: 'ستونی' },
  { id: 'highlow',    label: 'سقف‑کف' },
];

// شناسه‌های نوع جدید که خانواده‌ی «مقدار» محسوب می‌شوند (برای guardهای live-update/replay).
// volcandles/highlow «کندل»‌اند؛ columns «هیستوگرام»؛ hlcarea «area/مقدار»؛ lwm «خط/مقدار».
export const EXT_VALUE_TYPES = ['lwm', 'hlcarea']; // به آرایه‌های ['line','area','baseline','step'] افزوده شوند
export const EXT_HISTOGRAM_TYPES = ['columns'];
export const EXT_CANDLE_TYPES = ['volcandles', 'highlow'];
// همه‌ی انواع جدید این فایل (برای انشعاب buildPriceSeries)
export const EXT_TYPES = ['volcandles', 'lwm', 'hlcarea', 'columns', 'highlow'];

// ────────────────────────────────────────────────────────────────────────────
//  Dispatcher کمکی — یک‌جا برای buildPriceSeries.
//  داده/گزینه‌های لازم را بر پایه‌ی نوع برمی‌گرداند تا فراخواننده فقط addSeries کند.
//  خروجی: { kind: 'candle'|'histogram'|'line-markers'|'hlc-area', ... }
//  (فراخواننده با همان ctorهای import‌شده در BazaarNama.jsx سری را می‌سازد.)
// ────────────────────────────────────────────────────────────────────────────
export function buildExtType(type, cs, opts = {}) {
  switch (type) {
    case 'volcandles':
      return { kind: 'candle', options: {}, data: volumeCandles(cs, opts) };
    case 'highlow':
      return { kind: 'candle', options: highLowOptions(), data: highLowData(cs, opts) };
    case 'columns':
      return { kind: 'histogram', options: columnsOptions(), data: columnsData(cs, opts) };
    case 'lwm':
      return { kind: 'line-markers', options: lineMarkerOptions(opts), data: lineMarkersData(cs) };
    case 'hlcarea':
      return { kind: 'hlc-area', spec: hlcAreaSpec(cs, opts) };
    default:
      return null;
  }
}

// ════════════════════════════════════════════════════════════════════════════
//  فاز ۳ — تعمیق انواع چارت «غیرزمانی» هم‌تراز TradingView (§۲ مرجع).
//  همه additive: توابع خالص جدید که رنگ/گزینه/بازنویسی داده را غنی‌تر می‌کنند و
//  در کنار سازنده‌های پایه‌ی chartbuilders.js (renko/rangeBars/lineBreak/kagi/pnf)
//  می‌نشینند. هیچ export یا امضای موجودی تغییر نمی‌کند.
// ════════════════════════════════════════════════════════════════════════════

// تخصیص زمان صعودی یکتا (این چارت‌ها مستقل از زمان‌اند) — کپی محلی الگوی chartbuilders.
function extTimer() { let last = 0; return (t) => { const v = Math.max(t || 0, last + 1); last = v; return v; }; }

// ────────────────────────────────────────────────────────────────────────────
//  Heikin-Ashi  (§2.13 / نوع «کندل هموارشده») — تأیید/تعمیق
//  HA_close = (o+h+l+c)/4 ؛ HA_open = میانگین HA_open/HA_close قبلی (بار اول (o+c)/2) ؛
//  HA_high = max(h, HAo, HAc) ؛ HA_low = min(l, HAo, HAc). رنگ بر پایه‌ی HAc≥HAo.
//  خروجی سازگار با CandlestickSeries (هر نقطه رنگ اختصاصی دارد ⇒ رنگ در همان setData).
//  مصرف: chart.addSeries(CandlestickSeries, heikinAshiOptions()); s.setData(heikinAshi(cs, {up,down}));
// ────────────────────────────────────────────────────────────────────────────
export function heikinAshi(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  const out = [];
  let prevO = (cs[0].o + cs[0].c) / 2, prevC = (cs[0].o + cs[0].h + cs[0].l + cs[0].c) / 4;
  for (let i = 0; i < cs.length; i++) {
    const c = cs[i];
    const haC = (c.o + c.h + c.l + c.c) / 4;
    const haO = i === 0 ? (c.o + c.c) / 2 : (prevO + prevC) / 2;
    const haH = Math.max(c.h, haO, haC);
    const haL = Math.min(c.l, haO, haC);
    const col = haC >= haO ? up : down;
    out.push({ time: c.t, open: haO, high: haH, low: haL, close: haC, color: col, borderColor: col, wickColor: col });
    prevO = haO; prevC = haC;
  }
  return out;
}
export function heikinAshiOptions() {
  return { borderVisible: true, priceLineVisible: false };
}
// آخرین نقطه‌ی HA برای live-update (نیازمند HA قبلی؛ فراخواننده باید حالت را نگه دارد یا کل سری را باز-سازد).
export function heikinAshiLivePoint(cs, opts = {}) {
  const arr = heikinAshi(cs, opts);
  return arr.length ? arr[arr.length - 1] : null;
}

// ────────────────────────────────────────────────────────────────────────────
//  Hollow Candles  (§2 ردیف ۳) — تأیید/تعمیق
//  رفتار TradingView: رنگ خط/ویک بر پایه‌ی close-vs-prevClose (روند)؛ بدنه «توخالی»
//  (fill شفاف) وقتی صعودی است (close≥open) و «توپُر» با رنگ روند وقتی نزولی (close<open).
//  ⇒ چهار حالت: up-hollow / up-filled / down-hollow / down-filled.
//  مصرف: chart.addSeries(CandlestickSeries, hollowOptions()); s.setData(hollowCandles(cs, {up,down}));
// ────────────────────────────────────────────────────────────────────────────
export function hollowCandles(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  let prevC = cs[0].c;
  return cs.map((c) => {
    const rising = c.c >= prevC; prevC = c.c;   // روند ⇒ رنگ خط/ویک
    const trend = rising ? up : down;
    const bull = c.c >= c.o;                     // شکل بدنه ⇒ توخالی/توپُر
    return {
      time: c.t, open: c.o, high: c.h, low: c.l, close: c.c,
      color: bull ? 'rgba(0,0,0,0)' : trend,     // بدنه‌ی صعودی توخالی، نزولی توپُر
      borderColor: trend, wickColor: trend,
    };
  });
}
export function hollowOptions() {
  return { borderVisible: true, priceLineVisible: false };
}

// ────────────────────────────────────────────────────────────────────────────
//  Renko — تعمیق: اندازه‌ی آجر ATR/Traditional + رنگ جهت + ویک اختیاری  (§2 ردیف ۱۴)
//  خروجی سازگار با CandlestickSeries؛ هر آجر رنگ/بوردر اختصاصی دارد.
//  opts: { brick, method:'atr'|'traditional', atrPeriod, wicks:Boolean, up, down }
//    - method='atr' (پیش‌فرض): brick = avgRange(cs, atrPeriod)
//    - method='traditional': brick = opts.brick (یا avgRange به‌عنوان fallback)
//    - wicks: سایه‌ی آجر تا اکسترم همان کندل سازنده کشیده می‌شود (تقریب رفتار TV).
// ────────────────────────────────────────────────────────────────────────────
export function renkoBrickSize(cs, opts = {}) {
  if (opts.brick && opts.brick > 0) return opts.brick;
  return avgRange(cs, opts.atrPeriod || 14) || 1;
}
export function renkoBricks(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  if (!cs.length) return [];
  const brick = renkoBrickSize(cs, opts);
  const wicks = !!opts.wicks;
  const nt = extTimer();
  const out = [];
  let base = cs[0].c;
  for (const c of cs) {
    const price = c.c;
    let firstUp = true, firstDown = true;
    while (price >= base + brick) {
      const o = base, cl = base + brick;
      const lo = wicks && firstUp ? Math.min(o, c.l) : o; firstUp = false;
      out.push({ time: nt(c.t), open: o, high: cl, low: lo, close: cl, color: up, borderColor: up, wickColor: up });
      base += brick;
    }
    while (price <= base - brick) {
      const o = base, cl = base - brick;
      const hi = wicks && firstDown ? Math.max(o, c.h) : o; firstDown = false;
      out.push({ time: nt(c.t), open: o, high: hi, low: cl, close: cl, color: down, borderColor: down, wickColor: down });
      base -= brick;
    }
  }
  return out;
}
export function renkoOptions(opts = {}) {
  // بدون ویک ⇒ ویک‌ها شفاف تا فقط بدنه‌ی آجر دیده شود؛ با ویک ⇒ رنگ آجر.
  if (opts.wicks) return { borderVisible: true, priceLineVisible: false };
  return { borderVisible: true, wickUpColor: 'rgba(0,0,0,0)', wickDownColor: 'rgba(0,0,0,0)', priceLineVisible: false };
}

// ────────────────────────────────────────────────────────────────────────────
//  Range bars — تعمیق: رنگ جهت روی سازنده‌ی پایه‌ی rangeBars()  (§2 ردیف ۱۸)
//  از rangeBars(chartbuilders) استفاده می‌کند (بازنویسی داده) و فقط رنگ up/down می‌افزاید.
//  opts: { range, up, down }
// ────────────────────────────────────────────────────────────────────────────
export function rangeBarsColored(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  const bars = rangeBars(cs, opts.range);
  return bars.map((b) => {
    const col = b.c >= b.o ? up : down;
    return { time: b.t, open: b.o, high: b.h, low: b.l, close: b.c, color: col, borderColor: col, wickColor: col };
  });
}

// ────────────────────────────────────────────────────────────────────────────
//  Line Break — تعمیق: تعداد خط تنظیم‌پذیر + رنگ جهت  (§2 ردیف ۱۵)
//  از lineBreak(chartbuilders) با n دلخواه (پیش‌فرض TV = ۳) استفاده و رنگ می‌افزاید.
//  opts: { lines, up, down }
// ────────────────────────────────────────────────────────────────────────────
export function lineBreakColored(cs, opts = {}) {
  const up = opts.up || DEF.up, down = opts.down || DEF.down;
  const n = opts.lines && opts.lines > 0 ? opts.lines : 3;
  const bars = lineBreak(cs, n);
  return bars.map((b) => {
    const col = b.c >= b.o ? up : down;
    return { time: b.t, open: b.o, high: b.h, low: b.l, close: b.c, color: col, borderColor: col, wickColor: col };
  });
}
// گزینه‌های مشترک سری کندلی آجر/بازه/شکست‌خط (بوردر واضح، بدون خط قیمت).
export function brickBarOptions() {
  return { borderVisible: true, priceLineVisible: false };
}

// ────────────────────────────────────────────────────────────────────────────
//  فهرست/متادیتای تعمیق فاز۳ (additive؛ جدا از EXT_CHART_TYPES تا مدخل منوی تکراری نسازد).
//  این‌ها انواعی‌اند که سازنده‌ی پایه‌شان از قبل ثبت شده؛ این‌جا فقط «نسخه‌ی رنگی/غنی» ارائه می‌شود.
// ────────────────────────────────────────────────────────────────────────────
export const EXT_NONSTANDARD_DEEP = ['heikinashi', 'hollow', 'renko', 'range', 'linebreak'];
// پیش‌فرض‌های تنظیمات هر نوع (هم‌تراز دیالوگ تنظیمات TV) — فراخواننده می‌تواند override کند.
export const EXT_NONSTANDARD_DEFAULTS = {
  renko:     { method: 'atr', atrPeriod: 14, wicks: false },
  range:     { }, // range از avgRange به‌عنوان پیش‌فرض استفاده می‌کند
  linebreak: { lines: 3 },
};

// ────────────────────────────────────────────────────────────────────────────
//  Dispatcher تکمیلی برای انواع غیرزمانی «رنگی/عمیق».
//  خروجی candle: { kind:'candle', options, data } — سازگار با همان مسیر buildExtType.
//  kagi/pnf از spec‌های موجود (kagiSpec/pnfColumns) استفاده می‌کنند.
//  فراخواننده می‌تواند این را به‌عنوان مسیر ارتقایافته‌ی buildNonStandard صدا بزند؛ اگر نوع
//  پوشش داده نشود null برمی‌گرداند تا مسیر پایه دست‌نخورده بماند.
// ────────────────────────────────────────────────────────────────────────────
export function buildExtNonStandard(type, cs, opts = {}) {
  const o = { ...(EXT_NONSTANDARD_DEFAULTS[type] || {}), ...opts };
  switch (type) {
    case 'heikinashi':
      return { kind: 'candle', options: heikinAshiOptions(), data: heikinAshi(cs, o) };
    case 'hollow':
      return { kind: 'candle', options: hollowOptions(), data: hollowCandles(cs, o) };
    case 'renko':
      return { kind: 'candle', options: renkoOptions(o), data: renkoBricks(cs, o) };
    case 'range':
      return { kind: 'candle', options: brickBarOptions(), data: rangeBarsColored(cs, o) };
    case 'linebreak':
      return { kind: 'candle', options: brickBarOptions(), data: lineBreakColored(cs, o) };
    case 'kagi':
      return { kind: 'kagi', spec: kagiSpec(cs, o) };
    case 'pnf':
      return { kind: 'pnf', spec: pnfColumns(cs, o) };
    default:
      return null;
  }
}
