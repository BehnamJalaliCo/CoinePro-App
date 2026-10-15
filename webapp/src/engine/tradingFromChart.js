// tradingFromChart.js — کمک‌تابع‌های «ترید از روی چارت» (فصل ۹.۳ اسپک)
// ─────────────────────────────────────────────────────────────────────────────
// این ماژول کاملاً additive و drop-in است؛ هیچ فایلِ مشترکی را تغییر نمی‌دهد.
// دو بخش دارد:
//   ۱) توابعِ خالص (pure) برای محاسبهٔ R:R، سایزِ پوزیشن، پیپ، و ساختِ سفارشِ پیش‌فرض.
//   ۲) یک کنترلرِ سبک (PriceLineOrder) که خطوطِ Entry/SL/TP را با priceLine APIِ
//      سری در lightweight-charts v5 رسم/به‌روز/حذف می‌کند — مکمّلِ لایهٔ canvasِ
//      موجود (drawRef.setOrder)، نه جایگزینِ آن. اگر سری/priceLine نباشد بی‌صدا
//      degrade می‌کند (graceful) و کرش نمی‌کند.
//
// شکلِ سفارش در سراسرِ پروژه: { side:'buy'|'sell', entry, sl, tp }
// رنگ‌ها مطابقِ BazaarNama: ورود #3b82f6 (یا accent)، هدف #22c55e، حد ضرر #ef4444.
// ─────────────────────────────────────────────────────────────────────────────

// رنگ‌های پیش‌فرضِ خطوط (با TH.accent/up/down هم‌خوان؛ قابلِ override از بیرون)
export const ORDER_COLORS = { entry: '#3b82f6', tp: '#22c55e', sl: '#ef4444' };

// ── توابعِ خالص ──────────────────────────────────────────────────────────────

// نسبتِ ریسک به ریوارد. اگر ریسک صفر باشد null برمی‌گرداند (تقسیم‌بر‌صفر).
export function computeRR(order) {
  if (!order) return null;
  const risk = Math.abs(order.entry - order.sl);
  const reward = Math.abs(order.tp - order.entry);
  if (!risk || !isFinite(risk)) return null;
  return reward / risk;
}

// فرمتِ R:R برای نمایش (مثلِ "2.00" یا "—")
export function formatRR(order) {
  const rr = computeRR(order);
  return rr == null || !isFinite(rr) ? '—' : rr.toFixed(2);
}

// اعتبارسنجیِ هندسهٔ سفارش: برای خرید باید sl < entry < tp و برای فروش برعکس.
export function isValidOrder(order) {
  if (!order || !isFinite(order.entry) || !isFinite(order.sl) || !isFinite(order.tp)) return false;
  if (order.side === 'buy') return order.sl < order.entry && order.tp > order.entry;
  if (order.side === 'sell') return order.sl > order.entry && order.tp < order.entry;
  return false;
}

// اندازهٔ یک «پیپ» را تخمین می‌زند. JPY و طلا/شاخص اعشارِ کمتری دارند.
// برای فارکسِ ۵‌رقمی پیپ = 0.0001، برای جفت‌های JPY = 0.01.
export function pipSize(symbol, price) {
  const s = (symbol || '').toUpperCase();
  if (s.includes('JPY')) return 0.01;
  if (s.includes('XAU') || s.includes('GOLD')) return 0.1;
  if (s.includes('XAG') || s.includes('SILVER')) return 0.01;
  // پیش‌فرضِ فارکس؛ برای قیمت‌های بزرگ (شاخص/کریپتو) به مقیاسِ قیمت پناه می‌بریم
  if (price && price > 1000) return 1;
  return 0.0001;
}

// فاصلهٔ ورود تا حد ضرر برحسبِ پیپ.
export function stopPips(order, symbol) {
  if (!order) return 0;
  const ps = pipSize(symbol, order.entry);
  return ps ? Math.abs(order.entry - order.sl) / ps : 0;
}

// محاسبهٔ سایزِ پوزیشن (حجم برحسبِ واحدِ پایه) از روی ریسکِ دلاری.
// risk_usd = پولی که حاضریم تا حد ضرر از دست بدهیم.
// size = risk_usd / (|entry - sl| × valuePerUnit) — به‌صورتِ ساده valuePerUnit=1.
// خروجی به‌صورتِ { units, lots } که lots = units / contractSize.
export function positionSize(order, opts = {}) {
  const { riskUsd = 0, contractSize = 100000, valuePerUnit = 1 } = opts;
  if (!order || !riskUsd) return { units: 0, lots: 0, riskUsd: 0 };
  const dist = Math.abs(order.entry - order.sl);
  if (!dist || !isFinite(dist)) return { units: 0, lots: 0, riskUsd };
  const units = riskUsd / (dist * valuePerUnit);
  const lots = contractSize ? units / contractSize : 0;
  return { units: Math.max(0, units), lots: Math.max(0, lots), riskUsd };
}

// سود/زیانِ شناورِ یک سفارش/پوزیشن نسبت به قیمتِ زندهٔ فعلی (برحسبِ پیپ و دلار).
export function unrealizedPnl(order, livePrice, opts = {}) {
  if (!order || !isFinite(livePrice)) return { pips: 0, usd: 0 };
  const dir = order.side === 'buy' ? 1 : -1;
  const diff = (livePrice - order.entry) * dir;
  const ps = pipSize(opts.symbol, order.entry) || 0.0001;
  const pips = diff / ps;
  const { units = 0, valuePerUnit = 1 } = opts;
  const usd = diff * units * valuePerUnit;
  return { pips, usd };
}

// ساختِ سفارشِ پیش‌فرض از یک قیمتِ مبنا (مثلِ startTrade در BazaarNama).
// stopPct = درصدِ فاصلهٔ حد ضرر، rr = نسبتِ هدف به ریسک.
export function buildDefaultOrder(side, basePrice, opts = {}) {
  const { stopPct = 0.005, rr = 2 } = opts;
  if (!basePrice || !isFinite(basePrice)) return null;
  const d = basePrice * stopPct;
  const sign = side === 'buy' ? 1 : -1;
  return {
    side,
    entry: basePrice,
    sl: basePrice - sign * d,
    tp: basePrice + sign * rr * d,
  };
}

// با حفظِ هندسه، فقط حد ضرر را به نقطهٔ سربه‌سر (entry) منتقل می‌کند.
export function moveToBreakeven(order) {
  if (!order) return order;
  return { ...order, sl: order.entry };
}

// نگاشتِ سفارشِ چارت به نوعِ سفارشِ بروکر:
//   اگر entry ≈ قیمتِ فعلی → market، وگرنه limit/stop بسته به سمت و سمتِ بازار.
// (مطابقِ پلانِ ۹.۳.C اسپک) — صرفاً محاسبه‌ای؛ هیچ سفارشی ارسال نمی‌کند.
export function classifyOrderType(order, livePrice, tolPct = 0.0002) {
  if (!order || !isFinite(livePrice)) return 'market';
  const tol = livePrice * tolPct;
  if (Math.abs(order.entry - livePrice) <= tol) return 'market';
  if (order.side === 'buy') return order.entry < livePrice ? 'buy_limit' : 'buy_stop';
  return order.entry > livePrice ? 'sell_limit' : 'sell_stop';
}

// ── کنترلرِ خطوطِ قیمتِ سفارش روی سری (priceLine API) ─────────────────────────
// با سریِ lightweight-charts (CandlestickSeries/LineSeries/…) کار می‌کند که
// createPriceLine({ price, color, ... }) و line.applyOptions(...) / removePriceLine
// دارد. به‌جای حذف/ساختِ مکرر، خطوط را با applyOptions به‌روز می‌کند (روان، بدونِ churn).
//
// استفاده:
//   const ctl = new PriceLineOrder(series, { colors: ORDER_COLORS, symbol });
//   ctl.set(order);              // ساخت/به‌روزرسانیِ سه خط
//   ctl.update({ entry: 1.234 });// به‌روزرسانیِ جزئی
//   ctl.clear();                 // حذفِ همهٔ خطوط
export class PriceLineOrder {
  constructor(series, opts = {}) {
    this.series = series || null;
    this.colors = { ...ORDER_COLORS, ...(opts.colors || {}) };
    this.symbol = opts.symbol || '';
    this.lines = { entry: null, sl: null, tp: null };
    this.order = null;
  }

  // تعویضِ سری (مثلاً وقتی نوعِ چارت عوض می‌شود) — خطوطِ قبلی پاک و دوباره ساخته می‌شوند.
  setSeries(series) {
    const had = this.order;
    this.clear();
    this.series = series || null;
    if (had) this.set(had);
  }

  _title(key, order) {
    const rr = formatRR(order);
    if (key === 'tp') return `هدف ${num(order.tp)}`;
    if (key === 'sl') return `حد ضرر ${num(order.sl)}`;
    return `${order.side === 'buy' ? 'خرید' : 'فروش'} ${num(order.entry)}  R:R ${rr}`;
  }

  _ensure(key, order) {
    if (!this.series) return;
    const price = order[key];
    if (!isFinite(price)) return;
    const color = this.colors[key];
    const title = this._title(key, order);
    if (this.lines[key]) {
      try { this.lines[key].applyOptions({ price, color, title }); } catch (e) { /* graceful */ }
    } else {
      try {
        this.lines[key] = this.series.createPriceLine({
          price, color, lineWidth: 1, lineStyle: 2,
          axisLabelVisible: true, title,
        });
      } catch (e) { this.lines[key] = null; }
    }
  }

  // ساخت/به‌روزرسانیِ کاملِ سفارش (هر سه خط). اگر null بدهی، پاک می‌کند.
  set(order) {
    if (!order) { this.clear(); return; }
    this.order = { ...order };
    ['tp', 'entry', 'sl'].forEach((k) => this._ensure(k, this.order));
  }

  // به‌روزرسانیِ جزئی (مثلِ درگِ یک خط) — فقط کلیدهای داده‌شده + بازمحاسبهٔ عنوانِ R:R.
  update(patch) {
    if (!this.order) { if (patch) this.set(patch); return; }
    this.order = { ...this.order, ...patch };
    // چون عنوانِ خطِ entry حاوی R:R است، با هر تغییری همهٔ خطوط تازه می‌شوند.
    ['tp', 'entry', 'sl'].forEach((k) => this._ensure(k, this.order));
  }

  clear() {
    if (this.series) {
      for (const k of ['entry', 'sl', 'tp']) {
        if (this.lines[k]) { try { this.series.removePriceLine(this.lines[k]); } catch (e) { /* */ } }
        this.lines[k] = null;
      }
    } else {
      this.lines = { entry: null, sl: null, tp: null };
    }
    this.order = null;
  }
}

// عددِ قیمت را تمیز فرمت می‌کند (۵ رقم برای فارکس، کمتر برای قیمت‌های بزرگ).
function num(v) {
  if (v == null || !isFinite(v)) return '—';
  if (Math.abs(v) >= 1000) return v.toFixed(2);
  if (Math.abs(v) >= 100) return v.toFixed(3);
  return v.toFixed(5);
}

export default {
  ORDER_COLORS, computeRR, formatRR, isValidOrder, pipSize, stopPips,
  positionSize, unrealizedPnl, buildDefaultOrder, moveToBreakeven,
  classifyOrderType, PriceLineOrder,
};
