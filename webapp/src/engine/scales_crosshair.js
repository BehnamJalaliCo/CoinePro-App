// بازارنما — فصل ۳: تایم‌فریم، محورها، مقیاسِ قیمت، کراس‌هیر و سشن‌ها.
// همهٔ صادرات‌ها «توابعِ خالص» یا «جدولِ ثابت» هستند تا به‌صورتِ drop-in در
// BazaarNama.jsx مصرف شوند. هیچ وابستگی به state یا به lightweight-charts ندارند؛
// خروجی‌ها payloadِ آمادهٔ applyOptions یا توصیف‌گرِ باندِ سشن هستند.
//
// شکلِ کندلِ سرتاسرِ پروژه: {t,o,h,l,c,v} با t = یونیکس‌ثانیه.
// مقادیرِ enum با اعدادِ ثابتِ lightweight-charts v5 هم‌خوان‌اند تا نیازی به
// import از کتابخانه نباشد (CrosshairMode: Normal=0, Magnet=1, Hidden=2 ؛
// PriceScaleMode: Normal=0, Logarithmic=1, Percentage=2, IndexedTo100=3).

/* ──────────────────────────────────────────────────────────────────────────
 * (الف) کراس‌هیر — حالت‌های Cross / Dot / Arrow / Hidden + Magnet
 * ────────────────────────────────────────────────────────────────────────── */

// مقادیرِ ثابتِ enumِ CrosshairMode در lightweight-charts v5
export const CROSSHAIR_MODE = { Normal: 0, Magnet: 1, Hidden: 2 };

// حالت‌های کاربرپسند. native enum فقط Normal/Magnet/Hidden را پوشش می‌دهد؛
// «Dot» و «Arrow» نمایش‌های نشانگر روی حالتِ Normal هستند که روی overlay رسم می‌شوند.
// glyph: نوعِ نشانه‌ای که میزبان باید روی نقطهٔ تقاطع بکشد ('none'|'dot'|'arrow').
// cursor: مقدارِ CSS cursor برای کانتینرِ چارت.
export const CROSSHAIR_MODES = [
  { id: 'cross', label: 'صلیبی', mode: CROSSHAIR_MODE.Normal, glyph: 'none', cursor: 'crosshair', linesVisible: true },
  { id: 'dot', label: 'نقطه‌ای', mode: CROSSHAIR_MODE.Normal, glyph: 'dot', cursor: 'crosshair', linesVisible: true },
  { id: 'arrow', label: 'پیکانی', mode: CROSSHAIR_MODE.Normal, glyph: 'arrow', cursor: 'default', linesVisible: true },
  { id: 'hidden', label: 'بدون', mode: CROSSHAIR_MODE.Hidden, glyph: 'none', cursor: 'default', linesVisible: false },
  // پاک‌کن (Eraserِ TV) — کلیک روی ترسیم آن را حذف می‌کند. کراس‌هیرِ عادی نمایش داده می‌شود تا هدف‌گیری دقیق باشد.
  { id: 'eraser', label: 'پاک‌کن', mode: CROSSHAIR_MODE.Normal, glyph: 'none', cursor: 'default', linesVisible: true, eraser: true },
];

export const crosshairModeById = (id) =>
  CROSSHAIR_MODES.find((m) => m.id === id) || CROSSHAIR_MODES[0];

// حالت‌های Magnet مثلِ TradingView. enum بومیِ lightweight-charts فقط یک Magnet دارد؛
// تفاوتِ weak/strong یک راهنمای میزبان است (شعاعِ snap برحسبِ پیکسل):
//  • weak  → فقط وقتی مکان‌نما نزدیکِ یک سطحِ OHLC است snap می‌کند (snapPx محدود).
//  • strong→ همیشه به نزدیک‌ترین سطحِ OHLC می‌چسبد (snapPx نامحدود).
// active: آیا حالتِ بومیِ Magnet روشن شود. snapPx راهنمای میزبان برای منطقِ چسبیدن است.
export const MAGNET_MODES = [
  { id: 'off', label: 'خاموش', active: false, snapPx: 0 },
  { id: 'weak', label: 'ضعیف', active: true, snapPx: 12 },
  { id: 'strong', label: 'قوی', active: true, snapPx: Infinity },
];

// نگاشتِ ورودیِ magnet به حالت. سازگاریِ عقب‌رو: boolean حفظ می‌شود
// (true → weak = روشنِ بومی؛ false/undefined → off).
export const magnetModeById = (id) => {
  if (id === true) return MAGNET_MODES[1];
  if (id === false || id == null) return MAGNET_MODES[0];
  return MAGNET_MODES.find((m) => m.id === id) || MAGNET_MODES[0];
};

// رنگ‌های دقیقِ کراس‌هیرِ TradingView (در هر دو تمِ روشن/تیره یکسان‌اند):
//  • خطِ نقطه‌چینِ خاکستریِ #9598a1 (رنگِ رسمیِ کراس‌هیرِ TV).
//  • پس‌زمینهٔ برچسبِ محورِ کراس‌هیر خاکستریِ خنثیِ #4c525e (نه رنگِ accent) — عینِ TV.
//  • style = LineStyle.Dashed(2): خط‌چینِ ریزِ TV (نه LargeDashed درشتِ قبلی).
export const TV_CROSSHAIR_COLOR = '#9598a1';
export const TV_CROSSHAIR_LABEL_BG = '#4c525e';
export const TV_CROSSHAIR_LABEL_TEXT = '#ffffff';
export const CROSSHAIR_LINE_STYLE = 2; // LineStyle.Dashed — خط‌چینِ ریزِ عینِ TradingView

/**
 * ساختِ payloadِ آمادهٔ chart.applyOptions({...}) برای کراس‌هیر.
 * @param {string} id            یکی از 'cross'|'dot'|'arrow'|'hidden'
 * @param {boolean|string} magnet  boolean (true=weak) یا یکی از 'off'|'weak'|'strong'
 * @param {object}  th           تمِ جاری (THEMES[theme]) برای رنگِ خط/برچسب
 * @returns {{crosshair:object, _ui:{glyph:string,cursor:string,magnet:string,snapPx:number}}}
 *   crosshair: مستقیماً به chart.applyOptions داده می‌شود.
 *   _ui: راهنمای میزبان برای نشانگر/نشانه + قدرتِ magnet (نباید به applyOptions داده شود).
 */
export const crosshairOptions = (id, magnet, th) => {
  const m = crosshairModeById(id);
  const mag = magnetModeById(magnet);
  // Magnet توگلِ متعامد است: اگر فعال باشد، حالتِ پایه (وقتی پنهان نیست) به Magnet می‌رود.
  const mode = m.mode === CROSSHAIR_MODE.Hidden
    ? CROSSHAIR_MODE.Hidden
    : (mag.active ? CROSSHAIR_MODE.Magnet : CROSSHAIR_MODE.Normal);
  const T = th || {};
  // خطِ کراس‌هیر همیشه خاکستریِ TV است (در هر دو تم)، مگر تم صریحاً T.crosshair بدهد.
  const lineColor = T.crosshair || TV_CROSSHAIR_COLOR;
  // پس‌زمینهٔ برچسبِ محورِ کراس‌هیر خاکستریِ خنثیِ TV است، مگر تم T.crosshairLabelBg بدهد.
  const labelBg = T.crosshairLabelBg || TV_CROSSHAIR_LABEL_BG;
  const lineCfg = {
    visible: m.linesVisible,
    color: lineColor,
    width: 1,
    style: CROSSHAIR_LINE_STYLE,
    labelVisible: m.linesVisible,
    labelBackgroundColor: labelBg,
  };
  return {
    crosshair: { mode, vertLine: { ...lineCfg }, horzLine: { ...lineCfg } },
    _ui: { glyph: m.glyph, cursor: m.cursor, magnet: mag.id, snapPx: mag.snapPx },
  };
};

/**
 * رسمِ glyphِ نقطه/پیکان روی بوم overlay در محلِ تقاطعِ کراس‌هیر.
 * میزبان آن را داخلِ هندلرِ subscribeCrosshairMove (همان‌جا که dl.render() صدا زده می‌شود)
 * صدا می‌زند: paintCrosshairGlyph(ctx, glyph, x, y, th).
 * @param {CanvasRenderingContext2D} ctx
 * @param {string} glyph  'dot' | 'arrow' | 'none'
 * @param {number} x      مختصاتِ پیکسلیِ افقی (از param.point.x)
 * @param {number} y      مختصاتِ پیکسلیِ عمودی (از param.point.y)
 */
export const paintCrosshairGlyph = (ctx, glyph, x, y, th) => {
  if (!ctx || glyph === 'none' || x == null || y == null) return;
  const color = (th && th.accent) || '#2962FF';
  ctx.save();
  if (glyph === 'dot') {
    ctx.beginPath();
    ctx.arc(x, y, 4, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = '#fff';
    ctx.stroke();
  } else if (glyph === 'arrow') {
    // پیکانِ کوچک که از بالا-چپ به نقطه اشاره می‌کند
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x - 12, y - 5);
    ctx.lineTo(x - 7, y - 5);
    ctx.lineTo(x - 5, y - 12);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
  }
  ctx.restore();
};

/* ──────────────────────────────────────────────────────────────────────────
 * (ب) مقیاسِ قیمت — حالت‌های Regular / Log / Percent / Indexed-to-100 + Invert/Lock
 * ────────────────────────────────────────────────────────────────────────── */

// مقادیرِ ثابتِ enumِ PriceScaleMode در lightweight-charts v5
export const PRICE_SCALE_MODE = { Normal: 0, Logarithmic: 1, Percentage: 2, IndexedTo100: 3 };

// جدولِ حالت‌های مقیاس برای پر کردنِ <select>. value مستقیماً به applyOptions({mode}) می‌رود.
// ترتیبِ نمایش هم‌ترازِ منوی مقیاسِ TradingView: عادی ← درصدی ← پایه۱۰۰ ← لگاریتمی
// (تحقیقِ لوپِ #۳۵۸ + ترتیبِ دکمه‌های گوشهٔ TV «٪» پیش از «log»: درصد قبل از لگاریتمی، لگاریتمی آخر).
// اصلاحِ #۳۳۶ که اشتباهاً لگاریتمی را دوم گذاشته بود. value مبناست پس بازچینش فقط نمایشی است.
export const PRICE_SCALE_MODES = [
  { value: PRICE_SCALE_MODE.Normal, label: 'عادی' },
  { value: PRICE_SCALE_MODE.Percentage, label: 'درصدی' },
  { value: PRICE_SCALE_MODE.IndexedTo100, label: 'پایه ۱۰۰' },
  { value: PRICE_SCALE_MODE.Logarithmic, label: 'لگاریتمی' },
];

// ظاهرِ TV-مانندِ محورِ قیمت (افزایشی؛ روی chart.priceScale('right').applyOptions اعمال می‌شود).
//  • ticksVisible   → خطِ تیکِ کوچک کنارِ هر برچسب مثلِ TradingView.
//  • entireTextOnly → فقط برچسب‌های کاملِ جا-شده (بدونِ نصفهٔ بریده در لبه‌ها).
//  • alignLabels    → چیدنِ برچسب‌ها بدونِ همپوشانی (فاصله‌گذاریِ تیکِ منظم مثلِ TV).
//  • minimumWidth   → پهنای پایدارِ محور تا اعدادِ .tnum با تغییرِ قیمت نپرند.
export const PRICE_SCALE_APPEARANCE = {
  ticksVisible: true,
  entireTextOnly: true,
  alignLabels: true,
  minimumWidth: 56,
};

// (۴۳) حاشیه‌های عمودیِ محورِ قیمت مثلِ TradingView: بالا ~۱۰٪، پایین ~۱۰٪ (فشرده‌تر
// از پیش‌فرضِ lightweight-charts که بالا ۲۰٪ است). اختیاری/opt-in تا چیدمانِ حجمِ
// موجود دست‌نخورده بماند: میزبان با priceScaleOptions({..., margins: TV_SCALE_MARGINS})
// یا مستقیماً chart.priceScale('right').applyOptions({ scaleMargins: TV_SCALE_MARGINS }).
// نکته: همپوشانیِ حجم محورِ جدا با حاشیهٔ خودش دارد؛ این فقط محورِ قیمتِ اصلی است.
export const TV_SCALE_MARGINS = { top: 0.1, bottom: 0.1 };

/**
 * payloadِ آمادهٔ chart.priceScale(id).applyOptions({...}) برای حالت/قفل/وارونگیِ مقیاس.
 * @param {object} opt
 * @param {number}  opt.mode        یکی از PRICE_SCALE_MODE (پیش‌فرض Normal)
 * @param {boolean} opt.locked      true → autoScale خاموش (قفلِ بازهٔ دستی)
 * @param {boolean} opt.invert      true → محور عمودی وارونه
 * @param {boolean} opt.appearance  true (پیش‌فرض) → ظاهرِ TV-مانند (PRICE_SCALE_APPEARANCE)
 *                                   را هم می‌چسباند؛ false → فقط حالت/قفل/وارونگی (رفتارِ قدیمی).
 * @param {object}  [opt.margins]   { top, bottom } → scaleMargins مثلِ TV (اختیاری؛ اگر
 *                                   داده نشود اصلاً scaleMargins نمی‌فرستد = رفتارِ قدیمی).
 * @returns {object} payloadِ applyOptions
 */
export const priceScaleOptions = ({ mode = PRICE_SCALE_MODE.Normal, locked = false, invert = false, appearance = true, margins } = {}) => ({
  mode,
  autoScale: !locked,
  invertScale: !!invert,
  ...(appearance ? PRICE_SCALE_APPEARANCE : {}),
  ...(margins ? { scaleMargins: margins } : {}),
});

// payloadِ «بازنشانیِ مقیاس» (دابل‌کلیکِ روی محور) — autoScale را دوباره روشن می‌کند.
// میزبان پس از این باید chart.timeScale().fitContent() را هم صدا بزند.
export const resetPriceScaleOptions = () => ({ autoScale: true, invertScale: false });

// payloadِ «برازش/Auto» — فقط autoScale را دوباره روشن می‌کند و وارونگی/حالت را دست‌نخورده
// نگه می‌دارد (برخلافِ reset). معادلِ دکمهٔ «Fit» / «Auto» در TradingView.
// میزبان برای برازشِ افقی هم chart.timeScale().fitContent() را صدا می‌زند.
export const fitPriceScaleOptions = () => ({ autoScale: true });

// رنگ‌های دقیقِ مرز/گریدِ محورهای TradingView (روشن/تیره).
//  • borderِ محور: خطِ نازکِ جداکنندهٔ محور از ناحیهٔ چارت.
//  • gridِ محو: خطوطِ افقی/عمودیِ بسیار کم‌رنگ عینِ TV.
export const TV_AXIS_BORDER_DARK = '#2a2e39';
export const TV_AXIS_BORDER_LIGHT = '#e0e3eb';
export const TV_GRID_DARK = '#1e222d';
export const TV_GRID_LIGHT = '#eef0f4';

// (۴۵) رنگِ کم‌رنگِ متنِ برچسب‌های محورِ TradingView (روشن/تیره). در lightweight-charts
// رنگِ برچسبِ محور از layout.textColor سراسری می‌آید؛ این ثابت‌ها مقدارِ دقیقِ TV را می‌دهند.
export const TV_AXIS_TEXT_DARK = '#b2b5be';
export const TV_AXIS_TEXT_LIGHT = '#787b86';

/**
 * payloadِ آمادهٔ chart.applyOptions({...}) برای ظاهرِ دقیقِ مرز و گریدِ محورها مثلِ TV.
 * افزایشی و اختیاری؛ میزبان یک‌بار پس از ساختِ چارت اعمال می‌کند.
 * تیک‌های تمیز: borderِ نازکِ محور + گریدِ محوِ کم‌کنتراست تا اعداد خوانا و شارپ بمانند.
 * @param {object} [opt]
 * @param {boolean} [opt.light]        true → پالتِ روشن؛ پیش‌فرض تیره (مثلِ TV dark).
 * @param {string}  [opt.borderColor]  override رنگِ مرزِ محور (وگرنه از تم TV).
 * @param {string}  [opt.gridColor]    override رنگِ گرید (وگرنه از تم TV).
 * @returns {{rightPriceScale:object, timeScale:object, grid:object}}
 */
export const axisAppearanceOptions = ({ light = false, borderColor, gridColor } = {}) => {
  const border = borderColor || (light ? TV_AXIS_BORDER_LIGHT : TV_AXIS_BORDER_DARK);
  const grid = gridColor || (light ? TV_GRID_LIGHT : TV_GRID_DARK);
  return {
    rightPriceScale: { borderVisible: true, borderColor: border },
    timeScale: { borderVisible: true, borderColor: border },
    grid: {
      vertLines: { visible: true, color: grid, style: 0 },
      horzLines: { visible: true, color: grid, style: 0 },
    },
  };
};

/**
 * (۴۵) payloadِ اختیاریِ رنگِ کم‌رنگِ متنِ محور مثلِ TV. جدا و opt-in است چون
 * layout.textColor بر تمامِ متنِ چارت اثر می‌گذارد؛ میزبان در صورتِ تمایل اعمال می‌کند.
 * @param {object} [opt] { light?:boolean, textColor?:string }
 * @returns {{layout:{textColor:string}}}
 */
export const axisTextColorOptions = ({ light = false, textColor } = {}) => ({
  layout: { textColor: textColor || (light ? TV_AXIS_TEXT_LIGHT : TV_AXIS_TEXT_DARK) },
});

// اندازهٔ فونتِ برچسب‌های محور (قیمت + زمان) مثلِ TradingView.
export const PRICE_AXIS_FONT_PX = 11;

/**
 * payloadِ آمادهٔ chart.applyOptions({ layout }) برای فونتِ ۱۱px محورها (مثلِ TV).
 * افزایشی و اختیاری؛ میزبان یک‌بار پس از ساختِ چارت اعمال می‌کند.
 * @param {string} [fontFamily] در صورتِ نیاز فونتِ خانوادهٔ سفارشی (پیش‌فرض: دست‌نخورده)
 * @returns {{layout:{fontSize:number, fontFamily?:string}}}
 */
export const priceAxisLayoutOptions = (fontFamily) => ({
  layout: { fontSize: PRICE_AXIS_FONT_PX, ...(fontFamily ? { fontFamily } : {}) },
});

// رنگ‌های پیش‌فرضِ سبز/قرمزِ برچسبِ قیمت (fallback وقتی تم رنگ نمی‌دهد).
const LIVE_UP = '#089981';
const LIVE_DOWN = '#f23645';
const LIVE_FLAT = '#2962FF';

/**
 * گزینه‌های خطِ قیمتِ زندهٔ «LIVE» با برچسبِ محورِ رنگیِ up/down (مثلِ TradingView).
 * افزایشی: میزبان می‌تواند این را جایگزینِ آبجکتِ ثابتِ createPriceLine کند تا
 * برچسبِ آخرین قیمت روی محور، پس‌زمینهٔ سبز/قرمزِ جهت‌دار و متنِ سفید بگیرد.
 * سازگاریِ عقب‌رو: بدونِ dir/up/down همان رنگِ آبیِ قبلی ('#2962FF') برمی‌گردد.
 * @param {object} opt
 * @param {number}  [opt.price]  قیمتِ خط (اگر داده شود در خروجی می‌آید)
 * @param {number}  [opt.dir]    جهت: >0 صعودی، <0 نزولی، 0/undefined خنثی
 * @param {string}  [opt.up]     رنگِ صعودی (معمولاً TH.up)
 * @param {string}  [opt.down]   رنگِ نزولی (معمولاً TH.down)
 * @param {string}  [opt.accent] رنگِ حالتِ خنثی (پیش‌فرض آبیِ TV)
 * @param {string}  [opt.title]  متنِ تگ (پیش‌فرض 'LIVE')
 * @returns {object} payloadِ آمادهٔ series.createPriceLine / priceLine.applyOptions
 */
export const livePriceLineOptions = ({ price, dir = 0, up, down, accent = LIVE_FLAT, title = 'LIVE' } = {}) => {
  const color = dir > 0 ? (up || LIVE_UP) : dir < 0 ? (down || LIVE_DOWN) : accent;
  const o = {
    color,
    lineWidth: 1,
    lineStyle: 1,          // LineStyle.Dotted
    axisLabelVisible: true,
    title,
    axisLabelColor: color,   // پس‌زمینهٔ برچسبِ محور = رنگِ جهت
    axisLabelTextColor: '#ffffff',
  };
  if (price != null) o.price = price;
  return o;
};

/**
 * گزینه‌های سریِ قیمتِ اصلی برای «برچسبِ آخرین قیمت» روی محور (مثلِ TV).
 * افزایشی و اختیاری. توجه: در کندلِ استاندارد، پس‌زمینهٔ برچسبِ آخرین مقدار به‌طورِ
 * خودکار رنگِ up/downِ همان کندل را می‌گیرد (کارِ خودِ lightweight-charts)؛ این helper
 * فقط دیده‌شدنِ برچسب + خطِ قیمت را روشن می‌کند تا آن رنگِ خودکار ظاهر شود.
 * برای سری‌های value-محور (line/area) می‌توان priceLineColor داد.
 * @param {object} [opt] { priceLineColor?:string }
 * @returns {{lastValueVisible:boolean, priceLineVisible:boolean, priceLineColor?:string}}
 */
export const lastPriceLabelOptions = ({ priceLineColor } = {}) => ({
  lastValueVisible: true,
  priceLineVisible: true,
  ...(priceLineColor ? { priceLineColor } : {}),
});

/* ──────────────────────────────────────────────────────────────────────────
 * (پ) سشن‌ها — باندهای London / NY / Tokyo (TZ-aware با Intl، DST خودکار)
 * ────────────────────────────────────────────────────────────────────────── */

// تعریفِ سشن‌ها برحسبِ ساعتِ UTC (تقریبیِ هماهنگ با backend session-coverage).
// startUtc/endUtc ساعتِ شناورِ UTC. اگر end < start یعنی سشن از نیمه‌شبِ UTC عبور می‌کند.
export const SESSIONS = [
  // سیدنی — چهارمین سشنِ اصلیِ فارکس (از نیمه‌شبِ UTC عبور می‌کند؛ sessionBands این را پشتیبانی می‌کند).
  { id: 'sydney', label: 'سیدنی', startUtc: 21, endUtc: 6, color: 'rgba(236,72,153,0.10)', edge: 'rgba(236,72,153,0.45)' },
  { id: 'tokyo', label: 'توکیو', startUtc: 0, endUtc: 9, color: 'rgba(99,102,241,0.10)', edge: 'rgba(99,102,241,0.45)' },
  { id: 'london', label: 'لندن', startUtc: 8, endUtc: 17, color: 'rgba(34,197,94,0.10)', edge: 'rgba(34,197,94,0.45)' },
  { id: 'newyork', label: 'نیویورک', startUtc: 13, endUtc: 22, color: 'rgba(234,179,8,0.10)', edge: 'rgba(234,179,8,0.45)' },
];

// رنگِ همپوشانیِ لندن↔نیویورک (پررنگ‌تر) برای تأکید.
export const SESSION_OVERLAP_COLOR = 'rgba(239,68,68,0.12)';

// تبدیلِ یک یونیکس‌ثانیه به اجزای تاریخِ تقویمیِ wall-clock در منطقهٔ زمانیِ داده‌شده.
// از Intl استفاده می‌کند تا DST خودکار اعمال شود (هیچ آفستِ ثابتی).
const _fmtCache = {};
const _zoneFmt = (tz) => {
  if (!_fmtCache[tz]) {
    _fmtCache[tz] = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    });
  }
  return _fmtCache[tz];
};

// آفستِ منطقهٔ زمانی (ثانیه) نسبت به UTC در لحظهٔ unixSec داده‌شده — DST-aware.
// روش: اختلافِ «UTC تفسیرشده به‌عنوانِ wall-clockِ آن منطقه» با «UTC واقعی».
export const tzOffsetSec = (unixSec, tz) => {
  try {
    const d = new Date(unixSec * 1000);
    const parts = _zoneFmt(tz).formatToParts(d);
    const get = (t) => Number(parts.find((p) => p.type === t)?.value);
    let hour = get('hour');
    if (hour === 24) hour = 0; // برخی محیط‌ها نیمه‌شب را 24 می‌دهند
    // دقیقه را از خودِ منطقه بگیر (نه از UTC) وگرنه آفستِ نیم‌ساعته‌ها (تهران/هند)
    // اشتباه محاسبه می‌شود. ثانیه در همهٔ مناطق با UTC یکسان است (آفست‌ها مضربِ دقیقه‌اند).
    const min = get('minute');
    const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), hour,
      min, new Date(unixSec * 1000).getUTCSeconds());
    return Math.round((asUtc - unixSec * 1000) / 1000);
  } catch (e) {
    return 0;
  }
};

/**
 * تولیدِ باندهای سشن برای بازهٔ زمانیِ مرئی. هر باند یک بازهٔ زمانی است (یونیکس‌ثانیه)
 * که میزبان می‌تواند با timeScale().timeToCoordinate() به پیکسل تبدیل کرده و مستطیلِ
 * نیمه‌شفاف بکشد (مثلِ aiZonesRef/drawZone).
 *
 * @param {number} fromSec  ابتدای بازهٔ مرئی (یونیکس‌ثانیه) — معمولاً اولین کندلِ مرئی.
 * @param {number} toSec    انتهای بازهٔ مرئی (یونیکس‌ثانیه) — معمولاً آخرین کندل + کمی آینده.
 * @param {string} tz       منطقهٔ زمانیِ نمایش (مثلِ 'Asia/Tehran').
 * @param {object} [opt]    { sessions?: string[] فقطِ idهای انتخابی، overlap?: boolean }
 * @returns {Array<{id,label,from,to,color,edge}>}
 *   from/to یونیکس‌ثانیه؛ color رنگِ پر؛ edge رنگِ لبه. باندهای overlap با id='overlap'.
 *
 * نکته: مرزها در منطقهٔ زمانیِ نمایش با DST محاسبه می‌شوند؛ از این رو با تغییرِ tz
 * این تابع دوباره صدا زده می‌شود تا باندها جابه‌جا شوند.
 */
export const sessionBands = (fromSec, toSec, tz = 'UTC', opt = {}) => {
  if (!(toSec > fromSec)) return [];
  const wantOverlap = opt.overlap !== false;
  const pick = opt.sessions && opt.sessions.length
    ? SESSIONS.filter((s) => opt.sessions.includes(s.id))
    : SESSIONS;
  const bands = [];
  const DAY = 86400;
  // از یک روزِ UTC پیش از from تا یک روزِ پس از to پیمایش می‌کنیم تا باندهای لبه‌ای جا نمانند.
  const startDay = Math.floor(fromSec / DAY) * DAY - DAY;
  const endDay = Math.floor(toSec / DAY) * DAY + DAY;
  for (let day = startDay; day <= endDay; day += DAY) {
    for (const s of pick) {
      // ساعتِ UTC را به یونیکس‌ثانیه تبدیل کن؛ عبور از نیمه‌شب را با +DAY مدیریت کن.
      const bStart = day + s.startUtc * 3600;
      const bEnd = day + (s.endUtc > s.startUtc ? s.endUtc : s.endUtc + 24) * 3600;
      const from = Math.max(bStart, fromSec);
      const to = Math.min(bEnd, toSec);
      if (to > from) bands.push({ id: s.id, label: s.label, from, to, color: s.color, edge: s.edge });
    }
  }
  // همپوشانیِ لندن↔نیویورک: اشتراکِ بازهٔ این دو سشن در هر روز.
  if (wantOverlap && pick.find((s) => s.id === 'london') && pick.find((s) => s.id === 'newyork')) {
    const L = SESSIONS.find((s) => s.id === 'london');
    const N = SESSIONS.find((s) => s.id === 'newyork');
    for (let day = startDay; day <= endDay; day += DAY) {
      const oFrom = Math.max(day + L.startUtc * 3600, day + N.startUtc * 3600, fromSec);
      const oTo = Math.min(day + L.endUtc * 3600, day + N.endUtc * 3600, toSec);
      if (oTo > oFrom) bands.push({ id: 'overlap', label: 'همپوشانی', from: oFrom, to: oTo, color: SESSION_OVERLAP_COLOR, edge: 'rgba(239,68,68,0.4)' });
    }
  }
  // همپوشانیِ سیدنی↔توکیو: هر روز از شروعِ توکیو (۰h UTC) تا پایانِ سیدنی (۶h UTC).
  if (wantOverlap && pick.find((s) => s.id === 'sydney') && pick.find((s) => s.id === 'tokyo')) {
    const T = SESSIONS.find((s) => s.id === 'tokyo');
    const Sy = SESSIONS.find((s) => s.id === 'sydney');
    for (let day = startDay; day <= endDay; day += DAY) {
      const oFrom = Math.max(day + T.startUtc * 3600, fromSec);
      const oTo = Math.min(day + Sy.endUtc * 3600, toSec);
      if (oTo > oFrom) bands.push({ id: 'overlap', label: 'همپوشانی', from: oFrom, to: oTo, color: SESSION_OVERLAP_COLOR, edge: 'rgba(239,68,68,0.4)' });
    }
  }
  return bands;
};

/**
 * رسمِ باندهای سشن روی بوم overlay. میزبان آن را در همان pass که dl.render()
 * صدا زده می‌شود اجرا می‌کند: paintSessions(ctx, chart, bands, h).
 * مختصاتِ x از timeScale().timeToCoordinate(unixSec) گرفته می‌شود.
 * @param {CanvasRenderingContext2D} ctx
 * @param {object} chart  نمونهٔ چارت (برای timeScale().timeToCoordinate)
 * @param {Array}  bands  خروجیِ sessionBands
 * @param {number} height ارتفاعِ بوم (برای کشیدنِ باندِ تمام‌قد)
 */
export const paintSessions = (ctx, chart, bands, height) => {
  if (!ctx || !chart || !bands || !bands.length) return;
  let ts;
  try { ts = chart.timeScale(); } catch (e) { return; }
  ctx.save();
  for (const b of bands) {
    let x1 = ts.timeToCoordinate(b.from);
    let x2 = ts.timeToCoordinate(b.to);
    if (x1 == null || x2 == null) continue;
    if (x2 < x1) { const t = x1; x1 = x2; x2 = t; }
    const w = Math.max(1, x2 - x1);
    ctx.fillStyle = b.color;
    ctx.fillRect(x1, 0, w, height);
    // لبه‌های نازک
    ctx.strokeStyle = b.edge;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x1 + 0.5, 0); ctx.lineTo(x1 + 0.5, height);
    ctx.moveTo(x2 - 0.5, 0); ctx.lineTo(x2 - 0.5, height);
    ctx.stroke();
  }
  ctx.restore();
};

/* ──────────────────────────────────────────────────────────────────────────
 * (ت) شمارشِ معکوسِ بسته‌شدنِ کندل — calendar-aware
 * ────────────────────────────────────────────────────────────────────────── */

// جدولِ ثانیهٔ هر تایم‌فریمِ fixed-width (هم‌خوان با TF_SEC در BazaarNama.jsx).
// تایم‌فریم‌های تقویمی (W1/MN) جداگانه و درست محاسبه می‌شوند، نه با مدولوِ ثابت.
export const TF_SECONDS = {
  S1: 1, S5: 5, S10: 10, S15: 15, S30: 30,
  M1: 60, M3: 180, M5: 300, M15: 900, M30: 1800, M45: 2700,
  H1: 3600, H2: 7200, H3: 10800, H4: 14400,
  D1: 86400, W1: 604800, MN: 2592000,
};

// آیا این تایم‌فریم تقویمی است (مرزش روی مضربِ epoch نمی‌افتد)؟
const CALENDAR_TF = { W1: 'isoWeek', MN: 'month' };

/**
 * ثانیه‌های باقی‌مانده تا بسته‌شدنِ کندلِ جاری.
 * برای تایم‌فریم‌های fixed-width: sec - (now % sec) (مثلِ کدِ فعلی).
 * برای W1/MN: مرزِ تقویمیِ بعدی را در منطقهٔ زمانیِ نمایش محاسبه می‌کند (DST-aware).
 * @param {string} tf       شناسهٔ تایم‌فریم ('M5','H1','W1','MN',...)
 * @param {number} [nowSec] اکنون (یونیکس‌ثانیه)؛ پیش‌فرض Date.now()
 * @param {string} [tz]     منطقهٔ زمانیِ نمایش برای تایم‌فریم‌های تقویمی
 * @returns {number} ثانیهٔ باقی‌مانده (>=1)
 */
export const secondsToClose = (tf, nowSec, tz = 'UTC') => {
  const now = nowSec != null ? nowSec : Math.floor(Date.now() / 1000);
  const cal = CALENDAR_TF[tf];
  if (!cal) {
    const sec = TF_SECONDS[tf] || 3600;
    return sec - (now % sec) || sec;
  }
  // تقویمی: مرزِ بعدی را در منطقهٔ زمانیِ نمایش بیاب.
  const off = tzOffsetSec(now, tz);
  const local = new Date((now + off) * 1000); // UTC-fields = wall-clock محلی
  let next;
  if (cal === 'isoWeek') {
    // ابتدای هفتهٔ ISO بعدی = دوشنبهٔ 00:00 محلی
    const dow = (local.getUTCDay() + 6) % 7; // 0=دوشنبه
    const daysToNextMon = 7 - dow;
    next = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + daysToNextMon, 0, 0, 0);
  } else { // month
    next = Date.UTC(local.getUTCFullYear(), local.getUTCMonth() + 1, 1, 0, 0, 0);
  }
  // next بر حسبِ wall-clockِ محلی است؛ به یونیکسِ واقعی برگردان.
  const rem = Math.round(next / 1000) - off - now;
  return rem > 0 ? rem : 1;
};

/**
 * قالب‌بندیِ ثانیهٔ باقی‌مانده به رشته: H:MM:SS یا M:SS (مثلِ کدِ فعلی).
 * @param {number} rem ثانیه
 * @returns {string}
 */
export const formatCountdown = (rem) => {
  const h = Math.floor(rem / 3600), m = Math.floor((rem % 3600) / 60), s = rem % 60;
  return h
    ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    : `${m}:${String(s).padStart(2, '0')}`;
};

/**
 * رنگِ هشدارِ نزدیکِ بسته‌شدن: < 10s قرمز، < 60s کهربایی، وگرنه null (پیش‌فرض).
 * میزبان از آن برای tint کردنِ نشانگرِ شمارش استفاده می‌کند.
 * @param {number} rem ثانیه
 * @returns {string|null}
 */
export const countdownTint = (rem) => {
  if (rem <= 10) return '#ef4444';
  if (rem <= 60) return '#f59e0b';
  return null;
};

/* ──────────────────────────────────────────────────────────────────────────
 * (ث) قالب‌بندیِ محورِ زمان — TZ-aware (tickMarkFormatter / timeFormatter)
 * ────────────────────────────────────────────────────────────────────────── */

// منطقه‌های آمادهٔ انتخاب در picker (پیش‌فرضِ مخاطبِ ما: تهران).
// مراکزِ مالیِ اصلیِ جهان، مرتب بر اساسِ آفستِ UTC (غرب→شرق) مثلِ فهرستِ منطقهٔ زمانیِ TradingView.
// فهرستِ منتخب (نه همهٔ ۴۰+ زونِ TV) تا decluttered بماند؛ انتخاب همیشه با id است پس ترتیب فقط نمایشی است.
export const TIMEZONES = [
  { id: 'America/Los_Angeles', label: 'لس‌آنجلس' },   // UTC−8
  { id: 'America/Chicago', label: 'شیکاگو' },          // UTC−6
  { id: 'America/New_York', label: 'نیویورک' },        // UTC−5
  { id: 'America/Sao_Paulo', label: 'سائوپائولو' },    // UTC−3
  { id: 'UTC', label: 'UTC' },                          // UTC±0
  { id: 'Europe/London', label: 'لندن' },              // UTC±0
  { id: 'Europe/Berlin', label: 'فرانکفورت' },         // UTC+1
  { id: 'Europe/Zurich', label: 'زوریخ' },             // UTC+1
  { id: 'Europe/Moscow', label: 'مسکو' },              // UTC+3
  { id: 'Asia/Tehran', label: 'تهران' },               // UTC+3:30
  { id: 'Asia/Dubai', label: 'دبی' },                  // UTC+4
  { id: 'Asia/Kolkata', label: 'بمبئی' },              // UTC+5:30
  { id: 'Asia/Singapore', label: 'سنگاپور' },          // UTC+8
  { id: 'Asia/Hong_Kong', label: 'هنگ‌کنگ' },          // UTC+8
  { id: 'Asia/Shanghai', label: 'شانگهای' },           // UTC+8
  { id: 'Asia/Tokyo', label: 'توکیو' },                // UTC+9
  { id: 'Australia/Sydney', label: 'سیدنی' },          // UTC+10
];

/**
 * ساختِ payloadِ chart.applyOptions({ timeScale, localization }) برای قالب‌بندیِ
 * محورِ زمان و برچسبِ کراس‌هیر در منطقهٔ زمانیِ انتخابی.
 * @param {string} tz منطقهٔ زمانیِ نمایش
 * @returns {{timeScale:{tickMarkFormatter:Function}, localization:{timeFormatter:Function}}}
 */
export const timeZoneOptions = (tz = 'UTC', hour12 = false, dowOnLabels = false) => {
  const dateFmt = new Intl.DateTimeFormat('fa-IR', { timeZone: tz, month: 'short', day: 'numeric' });
  // «روزِ هفته روی برچسب‌ها» (Day of week on labels — تبِ Scalesِ TV): فقط برچسبِ روزِ ماه، آپشنال.
  const dayFmt = dowOnLabels
    ? new Intl.DateTimeFormat('fa-IR', { timeZone: tz, weekday: 'short', month: 'short', day: 'numeric' })
    : dateFmt;
  // قالبِ ساعت ۱۲/۲۴ (هم‌ترازِ «Time hours format»ِ تبِ Scalesِ TV) — روی محورِ زمان + تگِ کراس‌هیر + پنجرهٔ داده اعمال می‌شود.
  const timeFmt = new Intl.DateTimeFormat('fa-IR', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: !!hour12 });
  // tickType: 0=Year 1=Month 2=DayOfMonth 3=Time 4=TimeWithSeconds
  const tickMarkFormatter = (time, tickType) => {
    const d = new Date((typeof time === 'number' ? time : 0) * 1000);
    if (tickType >= 3) return timeFmt.format(d);
    if (tickType === 2) return dayFmt.format(d); // روزِ ماه — با روزِ هفته اگر فعال باشد
    return dateFmt.format(d); // سال/ماه
  };
  // تگِ زمانِ کراس‌هیر: تاریخ و ساعت را جدا فرمت و با جداکنندهٔ صریحِ « · » می‌چسبانیم — چون قالبِ ترکیبیِ Intlِ fa-IR
  // بین موتورهای ICU متفاوت است و در مرورگر تاریخ و ساعت را بدونِ جداکننده به‌هم می‌چسباند («تیر ۴۱۳:۳۵» به‌جای «تیر ۴ · ۱۳:۳۵»).
  const timeFormatter = (time) => { const d = new Date((typeof time === 'number' ? time : 0) * 1000); return `${dateFmt.format(d)} · ${timeFmt.format(d)}`; };
  return { timeScale: { tickMarkFormatter }, localization: { timeFormatter } };
};

/* ──────────────────────────────────────────────────────────────────────────
 * (ج) UIِ محورِ TV — منوی راست‌کلیکِ اسکیل، دکمه‌های گوشهٔ محور، ساعتِ UTC،
 *      برچسبِ DOMِ آخرین قیمتِ جهت‌دار + فلَش. همه توابعِ خالص/توصیف‌گر (بدونِ DOM/state).
 *      (مواردِ ۴۵–۵۳ ممیزیِ TV: منوی محور، ساعتِ UTC، برچسبِ رنگی، فونتِ tabular.)
 * ────────────────────────────────────────────────────────────────────────── */

// (۴۵) خانوادهٔ فونتِ محور با ارقامِ tabular. فونتِ اصلیِ سایت IRANYekanX است؛
// چون canvasِ lightweight-charts از font-feature-settings/font-variant-numeric
// پشتیبانی نمی‌کند، برای ثباتِ عرضِ ارقام روی محور (نلرزیدنِ اعداد با تغییرِ قیمت)
// یک fallbackِ ارقامِ عرض‌ثابت پس از فونتِ اصلی می‌گذاریم.
export const AXIS_TABULAR_FONT =
  "'IRANYekanX', ui-monospace, 'SF Mono', Menlo, 'Roboto Mono', monospace";

// راحتی: payloadِ layout با فونتِ ۱۱px + خانوادهٔ tabular (item 45). افزایشی؛
// معادلِ priceAxisLayoutOptions(AXIS_TABULAR_FONT).
export const tabularAxisLayoutOptions = () => priceAxisLayoutOptions(AXIS_TABULAR_FONT);

/**
 * (۴۷/۴۸/۴۹) منوی راست‌کلیکِ محورِ قیمت مثلِ TradingView.
 * state جاری را می‌گیرد تا رادیوها/چک‌باکس‌ها علامت بخورند. آیتم‌ها:
 *   { id, label, type:'radio'|'checkbox'|'action'|'separator', checked?, value? }
 * میزبان با کلیک، id را به applyPriceScaleMenu(id, state) می‌دهد تا payloadِ
 * applyOptions ساخته شود؛ اکشن‌های 'addAlert'/'settings' را خودش جدا هندل می‌کند.
 * @param {object} [state] { mode, locked, invert }
 * @returns {Array}
 */
export const priceScaleMenu = ({ mode = PRICE_SCALE_MODE.Normal, locked = false, invert = false } = {}) => [
  { id: 'regular', label: 'عادی', type: 'radio', checked: mode === PRICE_SCALE_MODE.Normal, value: PRICE_SCALE_MODE.Normal },
  { id: 'percent', label: 'درصدی', type: 'radio', checked: mode === PRICE_SCALE_MODE.Percentage, value: PRICE_SCALE_MODE.Percentage },
  { id: 'indexed', label: 'پایه ۱۰۰', type: 'radio', checked: mode === PRICE_SCALE_MODE.IndexedTo100, value: PRICE_SCALE_MODE.IndexedTo100 },
  { id: 'log', label: 'لگاریتمی', type: 'radio', checked: mode === PRICE_SCALE_MODE.Logarithmic, value: PRICE_SCALE_MODE.Logarithmic },
  { id: 'sep1', type: 'separator' },
  { id: 'auto', label: 'مقیاسِ خودکار', type: 'checkbox', checked: !locked },
  { id: 'invert', label: 'وارونه‌کردنِ مقیاس', type: 'checkbox', checked: !!invert },
  { id: 'sep2', type: 'separator' },
  { id: 'fit', label: 'برازشِ داده‌ها', type: 'action' },
  { id: 'reset', label: 'بازنشانیِ مقیاس', type: 'action' },
  { id: 'sep3', type: 'separator' },
  { id: 'addAlert', label: 'افزودنِ هشدار…', type: 'action' },
  { id: 'settings', label: 'تنظیماتِ مقیاس…', type: 'action' },
];

/**
 * نگاشتِ کلیکِ آیتمِ منوی محور → state بعدی + payloadِ applyOptions.
 * @param {string} id   یکی از idهای priceScaleMenu (اکشن‌های محض null می‌دهند)
 * @param {object} state وضعیتِ جاری { mode, locked, invert }
 * @returns {{patch:object, apply:object, fitContent:boolean}|null}
 *   patch: وضعیتِ ذخیره‌ایِ بعدی (برای CH3_DEFAULTS-مانند). apply: payloadِ
 *   chart.priceScale().applyOptions. fitContent: آیا میزبان timeScale().fitContent() هم بزند.
 *   برای اکشن‌های 'addAlert'/'settings' مقدارِ null برمی‌گردد (میزبان جدا هندل کند).
 */
export const applyPriceScaleMenu = (id, state = {}) => {
  const cur = { mode: PRICE_SCALE_MODE.Normal, locked: false, invert: false, ...state };
  const next = { ...cur };
  let fitContent = false;
  switch (id) {
    case 'regular': next.mode = PRICE_SCALE_MODE.Normal; break;
    case 'log': next.mode = PRICE_SCALE_MODE.Logarithmic; break;
    case 'percent': next.mode = PRICE_SCALE_MODE.Percentage; break;
    case 'indexed': next.mode = PRICE_SCALE_MODE.IndexedTo100; break;
    case 'auto': next.locked = !cur.locked; if (!next.locked) fitContent = true; break;
    case 'invert': next.invert = !cur.invert; break;
    case 'fit': next.locked = false; fitContent = true; break;
    case 'reset': next.mode = PRICE_SCALE_MODE.Normal; next.locked = false; next.invert = false; fitContent = true; break;
    default: return null;
  }
  return { patch: next, apply: priceScaleOptions(next), fitContent };
};

/**
 * (۴۷/۵۲/۵۳) دکمه‌های ریزِ گوشهٔ پایینِ محورِ قیمت مثلِ TV: ٪ / log / A(uto).
 * توگل‌اند (برخلافِ رادیوهای منو): کلیکِ log وقتی log فعال است → عادی.
 * @param {object} [state] { mode, locked }
 * @returns {Array<{id,label,title,active}>}
 */
export const priceScaleCornerButtons = ({ mode = PRICE_SCALE_MODE.Normal, locked = false } = {}) => [
  { id: 'percent', label: '٪', title: 'مقیاسِ درصدی', active: mode === PRICE_SCALE_MODE.Percentage },
  { id: 'log', label: 'log', title: 'مقیاسِ لگاریتمی', active: mode === PRICE_SCALE_MODE.Logarithmic },
  { id: 'auto', label: 'auto', title: 'مقیاسِ خودکار (Fit)', active: !locked },
];

/**
 * نگاشتِ کلیکِ دکمهٔ گوشهٔ محور → state بعدی + payload (سمانتیکِ توگل).
 * @param {string} id 'percent'|'log'|'auto'
 * @param {object} state { mode, locked, invert }
 * @returns {{patch:object, apply:object, fitContent:boolean}|null}
 */
export const applyCornerButton = (id, state = {}) => {
  const cur = { mode: PRICE_SCALE_MODE.Normal, locked: false, invert: false, ...state };
  const next = { ...cur };
  let fitContent = false;
  if (id === 'log') next.mode = cur.mode === PRICE_SCALE_MODE.Logarithmic ? PRICE_SCALE_MODE.Normal : PRICE_SCALE_MODE.Logarithmic;
  else if (id === 'percent') next.mode = cur.mode === PRICE_SCALE_MODE.Percentage ? PRICE_SCALE_MODE.Normal : PRICE_SCALE_MODE.Percentage;
  else if (id === 'auto') { next.locked = !cur.locked; if (!next.locked) fitContent = true; }
  else return null;
  return { patch: next, apply: priceScaleOptions(next), fitContent };
};

/**
 * (۵۲/۵۳) کنترل‌های گوشهٔ پایین-راستِ محور مثلِ TradingView: دکمهٔ ADJ (تعدیلِ سودِ سهام)
 * و چرخ‌دندهٔ تنظیماتِ محور. توصیف‌گرِ خالص؛ میزبان رندر و کلیک را هندل می‌کند.
 * ADJ فقط برای سهام معنا دارد و پیش‌فرض (فارکس) حذف است؛ با adj=true افزوده می‌شود.
 * @param {object} [opt] { adj?:boolean, adjActive?:boolean }
 * @returns {Array<{id,label,title,active,action}>}
 *   action=true یعنی اکشنِ باز کردنِ دیالوگ (چرخ‌دنده)، false یعنی توگل (ADJ).
 */
export const axisBottomControls = ({ adj = false, adjActive = false } = {}) => {
  const items = [];
  if (adj) items.push({ id: 'adj', label: 'ADJ', title: 'تعدیلِ سودِ سهام', active: !!adjActive, action: false });
  items.push({ id: 'settings', label: '⚙', title: 'تنظیماتِ محور', active: false, action: true });
  return items;
};

// فرمترهای کش‌شدهٔ ساعت برای axisClock (بر اساسِ tz).
const _clockCache = {};
const _clockFmt = (tz) => {
  if (!_clockCache[tz]) {
    _clockCache[tz] = new Intl.DateTimeFormat('fa-IR', {
      timeZone: tz, hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  }
  return _clockCache[tz];
};

/**
 * (۵۱) ساعتِ زندهٔ گوشهٔ پایینِ محورِ زمان + برچسبِ آفستِ منطقه مثلِ TV.
 * میزبان آن را هر ثانیه صدا می‌زند و رشته‌ها را کنارِ محورِ زمان نشان می‌دهد.
 * ارقام فارسیِ .tnum و dir=ltr. آفست DST-aware (از tzOffsetSec).
 * @param {number} [nowSec] یونیکس‌ثانیه (پیش‌فرض Date.now)
 * @param {string} [tz]     منطقهٔ نمایش
 * @returns {{time:string, offsetLabel:string, tz:string}}
 *   time: 'HH:MM:SS' در منطقه. offsetLabel: 'UTC' یا 'UTC+3:30' / 'UTC-5'.
 */
export const axisClock = (nowSec, tz = 'UTC') => {
  const now = nowSec != null ? nowSec : Math.floor(Date.now() / 1000);
  let time = '';
  try { time = _clockFmt(tz).format(new Date(now * 1000)); } catch (e) { time = ''; }
  const off = tzOffsetSec(now, tz);
  let offsetLabel;
  if (off === 0) {
    offsetLabel = 'UTC';
  } else {
    const sign = off < 0 ? '-' : '+';
    const a = Math.abs(off);
    const oh = Math.floor(a / 3600);
    const om = Math.floor((a % 3600) / 60);
    offsetLabel = om ? `UTC${sign}${oh}:${String(om).padStart(2, '0')}` : `UTC${sign}${oh}`;
  }
  return { time, offsetLabel, tz };
};

/**
 * کلاسِ فلَشِ جهت‌دار برای رقم‌های تغییرکرده (.flash-up/.flash-down).
 * @param {number} dir >0 صعودی، <0 نزولی، 0 خنثی
 * @returns {string} 'flash-up' | 'flash-down' | ''
 */
export const directionFlashClass = (dir) => (dir > 0 ? 'flash-up' : dir < 0 ? 'flash-down' : '');

/**
 * (۴۶) توصیف‌گرِ pillِ «آخرین قیمت» روی لبهٔ محور (DOM/overlay) — مکمّلِ
 * livePriceLineOptions که برای series.createPriceLine در lightweight-charts است.
 * پس‌زمینهٔ جهت‌دار سبز/قرمز، متنِ سفید، تگِ LIVE و کلاسِ فلَش برای تیک.
 * dir از مقایسهٔ price با prev استخراج می‌شود اگر مستقیم داده نشود.
 * @param {object} opt
 * @param {number} [opt.price]  قیمتِ جاری
 * @param {number} [opt.prev]   قیمتِ قبلی (برای استخراجِ جهت اگر dir داده نشود)
 * @param {number} [opt.dir]    جهتِ صریح (اولویت با این)
 * @param {string} [opt.up]     رنگِ صعودی (معمولاً TH.up)
 * @param {string} [opt.down]   رنگِ نزولی (معمولاً TH.down)
 * @param {string} [opt.accent] رنگِ خنثی (پیش‌فرض آبیِ TV)
 * @param {boolean}[opt.live]   نمایشِ تگِ LIVE (پیش‌فرض true)
 * @param {string} [opt.text]   متنِ آمادهٔ قیمت (وگرنه String(price))
 * @returns {{text:string, bg:string, color:string, dir:number, live:boolean, flashClass:string}}
 */
export const lastPriceLabel = ({ price, prev, dir, up, down, accent = LIVE_FLAT, live = true, text } = {}) => {
  const d = dir != null
    ? Math.sign(dir)
    : (prev != null && price != null ? Math.sign(price - prev) : 0);
  const bg = d > 0 ? (up || LIVE_UP) : d < 0 ? (down || LIVE_DOWN) : accent;
  return {
    text: text != null ? text : (price != null ? String(price) : ''),
    bg,
    color: '#ffffff',
    dir: d,
    live: !!live,
    flashClass: directionFlashClass(d),
  };
};

// پیش‌فرضِ کاملِ این فصل برای ذخیره/بازیابیِ میزِکار (saveWS/loadWS).
export const CH3_DEFAULTS = {
  scaleMode: PRICE_SCALE_MODE.Normal,
  scaleLocked: false,
  scaleInvert: false,
  crosshairId: 'cross',
  magnet: false,        // سازگاریِ عقب‌رو (boolean) — همچنان معتبر
  magnetMode: 'off',    // 'off'|'weak'|'strong' (ترجیح: اگر ست شد به crosshairOptions بده)
  tz: 'Asia/Tehran',
  sessionsOn: false,
};
