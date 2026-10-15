// بازارنما — بسته‌ی افزودنی ابزارهای ترسیم (Chapter 4 / فصل ۴).
// ─────────────────────────────────────────────────────────────────────────────
// این فایل کاملاً additive است؛ هیچ‌چیزی در drawings.js را بازنویسی نمی‌کند.
// هر ابزار سه تابع خالص دارد که فقط از یک «api» مختصاتی استفاده می‌کنند:
//   api = { x(t), y(p), t(x), p(y), barWidth(), W, H, candles, color(d) }
// که دقیقاً متناظر متدهای داخلی DrawingLayer است:
//   x≡_x  y≡_y  t≡_t  p≡_p  barWidth≡_barWidth  W,H≡canvas.width/height
// به این ترتیب با یک خط، رجیستری زیر را داخل _draw/_handlePoints/_hit/NEED وصل می‌کنیم.
//
// قراردادها (مطابق drawings.js):
//   • نقاط در chart-space ذخیره می‌شوند: {t (unix ثانیه), p (price)} تا با pan/zoom ثابت بمانند.
//   • شکل کندل در همه‌جا: {t,o,h,l,c,v}.
//   • ابزارهای دو نقطه‌ای (drag) از d.p0/d.p1 و ابزارهای N نقطه‌ای از d.pts استفاده می‌کنند.
//   • استایل: d.color, d.width(||1.5), d.dashed → ctx.setLineDash([6,4]).
// هیچ کد یا دارایی اختصاصی TradingView استفاده نشده؛ همه‌ی فرمول‌ها بازپیاده‌سازی شده‌اند.
// ─────────────────────────────────────────────────────────────────────────────

// شمارش نوار فیبوناچی زمانی
const FIB_SEQ = [0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233];
// شیب‌های بادبزن گان (به‌صورت ضریب 1×1)
const GANN_FAN = [
  { k: 8, lbl: '8×1' }, { k: 4, lbl: '4×1' }, { k: 3, lbl: '3×1' }, { k: 2, lbl: '2×1' },
  { k: 1, lbl: '1×1' }, { k: 1 / 2, lbl: '1×2' }, { k: 1 / 3, lbl: '1×3' }, { k: 1 / 4, lbl: '1×4' }, { k: 1 / 8, lbl: '1×8' },
];

// ── کمکی‌های مشترک ──────────────────────────────────────────────────────────
const dash = (ctx, on) => ctx.setLineDash(on ? [6, 4] : []);
// The line style a drawing asks for: solid, dashed or dotted (d.lineStyle); the old d.dashed flag
// still reads as dashed. Every tool, built-in or from this file, strokes with the same pattern.
export const dashOf = (d) => (d && d.lineStyle === 'dotted' ? [2, 3] : (d && (d.lineStyle === 'dashed' || d.dashed)) ? [6, 4] : []);
function style(ctx, d) {
  ctx.lineWidth = d.width || 1.5;
  ctx.strokeStyle = d.color || '#2962FF';
  ctx.fillStyle = d.color || '#2962FF';
  ctx.font = '12px IRANYekanX, sans-serif';
  ctx.setLineDash(dashOf(d));
}
// A label in the reader's language (the host sets layer.lang); Persian when it is not known.
const tr = (api, fa, en) => ((api && typeof api.lang === 'function' && api.lang() === 'en') ? en : fa);
// A line's direction from its first strong letter: Persian lines are laid out right to left, so
// «42 بار» reads as written whatever the canvas's own direction.
export const dirOf = (s) => { const m = /[A-Za-z\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]/.exec(String(s)); return m && /[^A-Za-z]/.test(m[0]) ? 'rtl' : 'ltr'; };
// A time span as days, hours or minutes (Latin digits: a market figure).
const spanText = (api, dt) => (dt >= 86400 ? `${Math.floor(dt / 86400)}${tr(api, ' روز', 'd')}` : dt >= 3600 ? `${Math.floor(dt / 3600)}${tr(api, ' ساعت', 'h')}` : `${Math.max(0, Math.floor(dt / 60))}${tr(api, ' دقیقه', 'm')}`);

// ── Levels (Fibonacci and Gann) ─────────────────────────────────────────────
// Each drawing may carry its own level table, d.levels: plain ratios (older drawings) or
// {v, color, on}. A drawing without one uses the defaults below, TradingView's.
export const LEVEL_COLORS = ['#787b86', '#f23645', '#81c784', '#4caf50', '#089981', '#64b5f6', '#787b86', '#2962ff', '#f23645', '#9c27b0', '#e91e63', '#ff9800', '#00bcd4', '#673ab7'];
const lv = (v, on = true, color) => ({ v, on, color });
export const LEVEL_DEFAULTS = {
  fib: [lv(0), lv(0.236), lv(0.382), lv(0.5), lv(0.618), lv(0.786), lv(1), lv(1.618, false, '#2962ff'), lv(2.618, false, '#f23645'), lv(3.618, false, '#9c27b0'), lv(4.236, false, '#e91e63')],
  fibext: [lv(0), lv(0.618), lv(1), lv(1.272), lv(1.618), lv(2.618)],
  fib3: [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1, 1.272, 1.414, 1.618, 2, 2.618, 3.618, 4.236].map((v) => lv(v)),
  fibchannel: [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1, 1.618].map((v) => lv(v)),
  fibfan: [0.236, 0.382, 0.5, 0.618, 0.786, 1].map((v) => lv(v)),
  fibcircles: [0.236, 0.382, 0.5, 0.618, 1, 1.618, 2.618].map((v) => lv(v)),
  fibarcs: [0.382, 0.5, 0.618, 1].map((v) => lv(v)),
  fibwedge: [0.236, 0.382, 0.5, 0.618, 0.786, 1].map((v) => lv(v)),
  pitchfan: [0.25, 0.382, 0.5, 0.618, 0.75, 1].map((v) => lv(v)),
  fibtimeext: [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1, 1.272, 1.414, 1.618, 2, 2.618, 3.618, 4.236].map((v) => lv(v)),
  gannbox: [0.25, 0.382, 0.5, 0.618, 0.75].map((v) => lv(v)),
};
/** The full level table of a drawing: {v, color, on} for every row, the drawing's own or the defaults. */
export function normLevels(d, type) {
  const def = LEVEL_DEFAULTS[type || (d && d.type)] || [];
  const src = d && Array.isArray(d.levels) && d.levels.length ? d.levels : def;
  return src.map((e, i) => {
    const o = (e && typeof e === 'object') ? e : { v: Number(e) };
    return { v: Number(o.v), on: o.on !== false, color: o.color || (def[i] && def[i].color) || LEVEL_COLORS[i % LEVEL_COLORS.length] };
  }).filter((l) => Number.isFinite(l.v));
}
/** The ratios a drawing shows (its own table, or the defaults), switched-off rows left out. */
export function levelValues(d, type) { return normLevels(d, type).filter((l) => l.on).map((l) => l.v); }

// Screen boxes of the labels drawn in the last frame, for hit-tests that need the measured size.
const boxes = new WeakMap();
const inBox = (d, x, y, pad = 4) => { const b = boxes.get(d); return !!b && x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad; };

/** The font of a text drawing: its size, bold and italic, in the one typeface. */
export const textFont = (d, size) => `${d && d.italic ? 'italic ' : ''}${d && d.bold ? 'bold ' : ''}${Math.max(8, Math.min(72, (d && d.fontSize) || size || 12))}px IRANYekanX, sans-serif`;

// Breaks a text into lines: typed «\n», real newlines, and (when wrap is on) words that pass maxW.
export function textLines(ctx, text, wrapW) {
  const raw = String(text == null ? '' : text).replace(/\\n/g, '\n').split('\n');
  if (!wrapW || wrapW < 20) return raw;
  const out = [];
  for (const line of raw) {
    const words = line.split(/(\s+)/);
    let cur = '';
    for (const w of words) {
      const next = cur + w;
      if (cur && ctx.measureText(next).width > wrapW) { out.push(cur.trimEnd()); cur = w.trimStart(); } else cur = next;
    }
    out.push(cur);
  }
  return out;
}

/**
 * A block of text at (x, y): the first line's baseline at y, aligned left / centre / right of x,
 * wrapped when d.wrap is on, with an optional background (d.bg, d.bgOpacity) and border (d.border).
 * Returns the box it covered. Shared by the text tool and the anchored text.
 */
export function drawTextBlock(ctx, d, x, y, fallback) {
  const fs = Math.max(8, Math.min(72, d.fontSize || 12));
  ctx.font = textFont(d, 12);
  const lines = textLines(ctx, d.text || fallback || '', d.wrap ? (d.wrapWidth || 240) : 0);
  const lh = Math.round(fs * 1.3);
  let tw = 0; for (const ln of lines) tw = Math.max(tw, ctx.measureText(ln).width);
  const align = d.align === 'center' || d.align === 'right' ? d.align : 'left';
  const left = align === 'center' ? x - tw / 2 : align === 'right' ? x - tw : x;
  const pad = 3, box = { x: left - pad, y: y - fs - pad + 2, w: tw + pad * 2, h: (lines.length - 1) * lh + fs + pad * 2 };
  if (d.bg) { ctx.save(); ctx.fillStyle = d.bg; ctx.globalAlpha = (d.bgOpacity != null ? d.bgOpacity : 0.9); ctx.fillRect(box.x, box.y, box.w, box.h); ctx.restore(); }
  if (d.border) { ctx.save(); ctx.strokeStyle = d.border; ctx.lineWidth = 1; ctx.setLineDash([]); ctx.strokeRect(box.x, box.y, box.w, box.h); ctx.restore(); }
  ctx.fillStyle = d.textColor || d.color || '#2962FF';
  ctx.textAlign = align; ctx.textBaseline = 'alphabetic';
  for (let i = 0; i < lines.length; i++) { ctx.direction = dirOf(lines[i]); ctx.fillText(lines[i], x, y + i * lh); }
  ctx.direction = 'ltr'; ctx.textAlign = 'left';
  boxes.set(d, box);
  return box;
}
/** Whether (x, y) falls on the text block drawn for d in the last frame. */
export function hitTextBlock(d, x, y) { return inBox(d, x, y, 2); }

/**
 * A short text along a line from (x0, y0) to (x1, y1) — TradingView's Text tab on a trend line or
 * a horizontal line: above, on or under the line (d.valign), at its start, middle or end (d.align).
 */
export function drawLineText(ctx, d, x0, y0, x1, y1) {
  if (!d.text) return;
  ctx.save();
  ctx.font = textFont(d, 12);
  ctx.fillStyle = d.textColor || d.color || '#2962FF';
  const align = d.align === 'left' || d.align === 'right' ? d.align : 'center';
  const k = align === 'left' ? 0 : align === 'right' ? 1 : 0.5;
  let ang = Math.atan2(y1 - y0, x1 - x0);
  if (ang > Math.PI / 2 || ang < -Math.PI / 2) ang += Math.PI;
  const x = x0 + (x1 - x0) * k, y = y0 + (y1 - y0) * k;
  ctx.translate(x, y); ctx.rotate(ang);
  ctx.textAlign = align === 'left' ? 'left' : align === 'right' ? 'right' : 'center';
  ctx.textBaseline = d.valign === 'bottom' ? 'top' : d.valign === 'middle' ? 'middle' : 'bottom';
  const off = d.valign === 'bottom' ? 4 : d.valign === 'middle' ? 0 : -4;
  const lines = textLines(ctx, d.text, 0);
  const fs = Math.max(8, Math.min(72, d.fontSize || 12)), lh = Math.round(fs * 1.3);
  lines.forEach((ln, i) => { ctx.direction = dirOf(ln); ctx.fillText(ln, align === 'left' ? 6 : align === 'right' ? -6 : 0, off + (d.valign === 'bottom' ? i * lh : -(lines.length - 1 - i) * lh)); });
  ctx.restore();
}
// پاره‌خط
function seg(ctx, x0, y0, x1, y1) { ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke(); }
// شعاع: از p0 از دل p1 تا لبه‌ی فریم (با بزرگ‌نمایی ساده)
function rayTo(ctx, x0, y0, x1, y1, W, H, both) {
  const dx = x1 - x0, dy = y1 - y0, k = Math.max(W, H) * 4 || 8000;
  ctx.beginPath();
  if (both) ctx.moveTo(x0 - dx / Math.hypot(dx, dy || 1) * k, y0 - dy / Math.hypot(dx, dy || 1) * k);
  else ctx.moveTo(x0, y0);
  const L = Math.hypot(dx, dy) || 1;
  ctx.lineTo(x0 + dx / L * k, y0 + dy / L * k);
  ctx.stroke();
}
// نقطه‌ی کلیک تا پاره‌خط (برای hit-test)
function distSeg(x, y, x0, y0, x1, y1) {
  const A = x - x0, B = y - y0, C = x1 - x0, D = y1 - y0;
  const len = C * C + D * D, dot = A * C + B * D;
  const t = len ? Math.max(0, Math.min(1, dot / len)) : 0;
  return Math.hypot(x - (x0 + t * C), y - (y0 + t * D));
}
// نقطه‌ی کلیک تا خط نامتناهی (بدون clamp؛ برای extline)
function distLine(x, y, x0, y0, x1, y1) {
  const C = x1 - x0, D = y1 - y0, len = Math.hypot(C, D) || 1;
  return Math.abs((y - y0) * C - (x - x0) * D) / len;
}
// رسم سر پیکان در (x1,y1) به سمت زاویه‌ی خط
function arrowHead(ctx, x0, y0, x1, y1, size) {
  const a = Math.atan2(y1 - y0, x1 - x0), s = size || 10;
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x1 - s * Math.cos(a - Math.PI / 7), y1 - s * Math.sin(a - Math.PI / 7));
  ctx.lineTo(x1 - s * Math.cos(a + Math.PI / 7), y1 - s * Math.sin(a + Math.PI / 7));
  ctx.closePath(); ctx.fill();
}
// برچسب جعبه‌ای کوچک (پس‌زمینه + متن) — استفاده‌ی مشترک callout/note/pricelabel/measure
function labelBox(ctx, x, y, text, opts) {
  opts = opts || {};
  // چندخطی مثل ابزار Text TV: هم نیولاین واقعی (paste) هم «\n» تایپ‌شده در prompt — هم‌راستا با هسته‌ی text (#295/#296). #298
  const lines = String(text).replace(/\\n/g, '\n').split('\n');
  ctx.font = (opts.font || '11px IRANYekanX, sans-serif');
  let w = 0; lines.forEach((ln) => { w = Math.max(w, ctx.measureText(ln).width); });
  const fsm = /(\d+)px/.exec(ctx.font), lh = Math.round((fsm ? Number(fsm[1]) : 11) * 1.3);
  const padX = 6, padY = 4, bw = w + padX * 2, bh = lines.length * lh + padY * 2;
  let bx = x, by = y;
  if (opts.anchor === 'center') { bx = x - bw / 2; by = y - bh / 2; }
  if (opts.anchor === 'above') { bx = x - bw / 2; by = y - bh - 6; }
  ctx.fillStyle = opts.bg || 'rgba(20,24,33,.92)';
  if (ctx.roundRect) { ctx.beginPath(); ctx.roundRect(bx, by, bw, bh, 4); ctx.fill(); }
  else ctx.fillRect(bx, by, bw, bh);
  if (opts.border) { ctx.strokeStyle = opts.border; ctx.lineWidth = 1; dash(ctx, false); if (ctx.roundRect) ctx.stroke(); else ctx.strokeRect(bx, by, bw, bh); }
  ctx.fillStyle = opts.fg || '#fff';
  ctx.textAlign = 'left'; ctx.textBaseline = 'top';
  lines.forEach((ln, i) => { const rtl = dirOf(ln) === 'rtl'; ctx.direction = rtl ? 'rtl' : 'ltr'; ctx.textAlign = rtl ? 'right' : 'left'; ctx.fillText(ln, rtl ? bx + bw - padX : bx + padX, by + padY + i * lh); });
  ctx.direction = 'ltr'; ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
  if (opts.of) boxes.set(opts.of, { x: bx, y: by, w: bw, h: bh });
  return { bx, by, bw, bh };
}
// hit-test منحنی بزیه با نمونه‌برداری: P = نقاط صفحه‌ای، order=2 (درجه۲) یا 3 (درجه۳).
function bezierHit(P, x, y, order) {
  if (!P || P.some((q) => !q || q.x == null || q.y == null)) return false;
  const N = 26; let prev = null;
  for (let i = 0; i <= N; i++) {
    const t = i / N, u = 1 - t; let q;
    if (order === 2) q = { x: u * u * P[0].x + 2 * u * t * P[1].x + t * t * P[2].x, y: u * u * P[0].y + 2 * u * t * P[1].y + t * t * P[2].y };
    else q = { x: u * u * u * P[0].x + 3 * u * u * t * P[1].x + 3 * u * t * t * P[2].x + t * t * t * P[3].x, y: u * u * u * P[0].y + 3 * u * u * t * P[1].y + 3 * u * t * t * P[2].y + t * t * t * P[3].y };
    if (prev) { const C = q.x - prev.x, D = q.y - prev.y, len = C * C + D * D; const tt = len ? Math.max(0, Math.min(1, ((x - prev.x) * C + (y - prev.y) * D) / len)) : 0; if (Math.hypot(x - (prev.x + tt * C), y - (prev.y + tt * D)) < 6) return true; }
    prev = q;
  }
  return false;
}
// نقاط نمونه‌شده‌ی کمان دایره‌ای گذرنده از سه نقطه‌ی صفحه‌ای A→B→C (برای ابزار Arc). اگر هم‌خط بودند ⇒ خط راست.
function arcPoints(A, B, C, N) {
  const d = 2 * (A.x * (B.y - C.y) + B.x * (C.y - A.y) + C.x * (A.y - B.y));
  if (Math.abs(d) < 1e-3) return [A, C];
  const A2 = A.x * A.x + A.y * A.y, B2 = B.x * B.x + B.y * B.y, C2 = C.x * C.x + C.y * C.y;
  const ux = (A2 * (B.y - C.y) + B2 * (C.y - A.y) + C2 * (A.y - B.y)) / d;
  const uy = (A2 * (C.x - B.x) + B2 * (A.x - C.x) + C2 * (B.x - A.x)) / d;
  const r = Math.hypot(A.x - ux, A.y - uy);
  const un = (a, ref) => { while (a - ref > Math.PI) a -= 2 * Math.PI; while (a - ref < -Math.PI) a += 2 * Math.PI; return a; };
  const a0 = Math.atan2(A.y - uy, A.x - ux);
  const aB = un(Math.atan2(B.y - uy, B.x - ux), a0);
  const a1 = un(Math.atan2(C.y - uy, C.x - ux), aB);
  const out = []; for (let i = 0; i <= N; i++) { const a = a0 + (a1 - a0) * i / N; out.push({ x: ux + r * Math.cos(a), y: uy + r * Math.sin(a) }); }
  return out;
}
// تبدیل لیست نسبت به سطح: [{ratio, price}]
function fibLevels(d, base, diff) {
  return normLevels(d).filter((l) => l.on).map((l) => ({ ratio: l.v, price: base + diff * l.v, color: l.color }));
}
// مختصات صفحه‌ای یک نقطه‌ی {t,p}
const px = (api, pt) => ({ x: api.x(pt.t), y: api.y(pt.p) });
const ok = (P) => P && P.x != null && P.y != null;

// ════════════════════════════════════════════════════════════════════════════
// رجیستری ابزارها. هر ورودی:
//   { label, points, draw(ctx,d,api), hit(d,x,y,api), handles(d,api), defaults }
// points: تعداد کلیک لازم (۲ = drag، ≥۳ = چندکلیک، ۱ = تک‌کلیک).
// draw : رسم با مختصات زنده.
// hit  : true اگر (x,y) روی آبجکت باشد (near≈7px).
// handles: آرایه‌ی نقاط قابل‌درگ → [{x,y,set(t,p)}]. اگر undefined، DrawingLayer مسیر
//          عمومی d.pts/p0/p1 را خودش می‌سازد (یعنی نیازی به override نیست).
// ════════════════════════════════════════════════════════════════════════════
export const EXT_REGISTRY = {

  // ───────── ۴.۲ خطوط ─────────
  // خط نامتناهی (هر دو جهت)
  extline: {
    label: 'خط امتداد‌یافته', points: 2,
    draw(ctx, d, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; style(ctx, d); rayTo(ctx, a.x, a.y, b.x, b.y, api.W, api.H, true); },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distLine(x, y, a.x, a.y, b.x, b.y) < 7; },
  },
  // خط اطلاعاتی: قطعه + برچسب Δقیمت/Δدرصد/Δبار/Δزمان/زاویه
  infoline: {
    label: 'خط اطلاعاتی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      style(ctx, d); seg(ctx, a.x, a.y, b.x, b.y);
      const dp = d.p1.p - d.p0.p, pct = d.p0.p ? dp / d.p0.p * 100 : 0;
      const bars = Math.round((b.x - a.x) / (api.barWidth() || 6));
      const ang = Math.atan2(-(b.y - a.y), (b.x - a.x)) * 180 / Math.PI;
      const txt = `Δ ${dp.toFixed(api.digits())}\n${pct.toFixed(2)}%  |  ${bars} ${tr(api, 'بار', 'bars')}\n${ang.toFixed(1)}°`;
      labelBox(ctx, (a.x + b.x) / 2, (a.y + b.y) / 2, txt, { anchor: 'center', border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distSeg(x, y, a.x, a.y, b.x, b.y) < 7; },
  },
  // زاویه‌ی روند: شعاع + قوس زاویه نسبت به افق
  angle: {
    label: 'زاویه‌ی روند', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      style(ctx, d); rayTo(ctx, a.x, a.y, b.x, b.y, api.W, api.H, false);
      dash(ctx, false); ctx.globalAlpha = 0.5; seg(ctx, a.x, a.y, a.x + 40, a.y); ctx.globalAlpha = 1;
      const ang = Math.atan2(-(b.y - a.y), (b.x - a.x)) * 180 / Math.PI;
      ctx.beginPath(); ctx.arc(a.x, a.y, 26, 0, -ang * Math.PI / 180, ang > 0); ctx.stroke();
      labelBox(ctx, a.x + 30, a.y - 8, `${ang.toFixed(1)}°`, { border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distLine(x, y, a.x, a.y, b.x, b.y) < 7; },
  },
  // پرتو افقی: از p0 تا راست
  hray: {
    label: 'پرتو افقی', points: 1,
    draw(ctx, d, api) { const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d); seg(ctx, a.x, a.y, api.W, a.y); ctx.fillText(d.p0.p.toFixed(api.digits()), a.x + 4, a.y - 3); drawLineText(ctx, d, a.x, a.y, api.W, a.y); },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.abs(y - a.y) < 7 && x >= a.x - 7; },
  },
  // خط صلیبی: افقی + عمودی از یک لنگر
  crossline: {
    label: 'خط صلیبی', points: 1,
    // Its price and date tags sit on the axes (DrawingLayer._axisTags), as on TradingView. (#282→#288)
    draw(ctx, d, api) { const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d); seg(ctx, 0, a.y, api.W, a.y); seg(ctx, a.x, 0, a.x, api.H); },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.abs(y - a.y) < 7 || Math.abs(x - a.x) < 7; },
  },

  // ───────── ۴.۳ کانال‌ها و چنگال‌ها ─────────
  // کانال رگرسیون: خط رگرسیون خطی close روی بازه + باند ±۲σ
  regchannel: {
    label: 'کانال رگرسیون', points: 2,
    draw(ctx, d, api) {
      const t0 = Math.min(d.p0.t, d.p1.t), t1 = Math.max(d.p0.t, d.p1.t);
      const cs = (api.candles || []).filter((c) => c.t >= t0 && c.t <= t1);
      style(ctx, d);
      if (cs.length < 2) { const a = px(api, d.p0), b = px(api, d.p1); if (ok(a) && ok(b)) seg(ctx, a.x, a.y, b.x, b.y); return; }
      const n = cs.length; let sx = 0, sy = 0, sxy = 0, sxx = 0;
      cs.forEach((c, i) => { sx += i; sy += c.c; sxy += i * c.c; sxx += i * i; });
      const m = (n * sxy - sx * sy) / (n * sxx - sx * sx || 1), b0 = (sy - m * sx) / n;
      let ss = 0; cs.forEach((c, i) => { const e = c.c - (m * i + b0); ss += e * e; }); const sd = Math.sqrt(ss / n);
      const pv = (i) => m * i + b0, x0 = api.x(cs[0].t), x1 = api.x(cs[n - 1].t);
      const yM0 = api.y(pv(0)), yM1 = api.y(pv(n - 1));
      const yT0 = api.y(pv(0) + 2 * sd), yT1 = api.y(pv(n - 1) + 2 * sd);
      const yB0 = api.y(pv(0) - 2 * sd), yB1 = api.y(pv(n - 1) - 2 * sd);
      ctx.globalAlpha = 0.06; ctx.beginPath(); ctx.moveTo(x0, yT0); ctx.lineTo(x1, yT1); ctx.lineTo(x1, yB1); ctx.lineTo(x0, yB0); ctx.closePath(); ctx.fill(); ctx.globalAlpha = 1;
      seg(ctx, x0, yM0, x1, yM1);
      ctx.globalAlpha = 0.75; dash(ctx, true); seg(ctx, x0, yT0, x1, yT1); seg(ctx, x0, yB0, x1, yB1); dash(ctx, false); ctx.globalAlpha = 1;
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return x >= Math.min(a.x, b.x) - 7 && x <= Math.max(a.x, b.x) + 7 && y >= Math.min(a.y, b.y) - 30 && y <= Math.max(a.y, b.y) + 30; },
  },
  // کانال سقف/کف صاف: خط روند (p0→p1) + خط افقی در p2
  flatchannel: {
    label: 'کانال سقف/کف صاف', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      style(ctx, d); const xL = Math.min(P[0].x, P[1].x), xR = Math.max(P[0].x, P[1].x);
      ctx.globalAlpha = 0.06; ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.lineTo(xR, P[2].y); ctx.lineTo(xL, P[2].y); ctx.closePath(); ctx.fill(); ctx.globalAlpha = 1;
      seg(ctx, P[0].x, P[0].y, P[1].x, P[1].y); seg(ctx, xL, P[2].y, xR, P[2].y);
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return false; return distSeg(x, y, P[0].x, P[0].y, P[1].x, P[1].y) < 7 || (Math.abs(y - P[2].y) < 7 && x >= Math.min(P[0].x, P[1].x) - 7 && x <= Math.max(P[0].x, P[1].x) + 7); },
  },
  // کانال ناپیوسته: دو خط مستقل p0→p1 و p2→p3
  disjointchannel: {
    label: 'کانال ناپیوسته', points: 4,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 4) return;
      style(ctx, d);
      ctx.globalAlpha = 0.06; ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.lineTo(P[3].x, P[3].y); ctx.lineTo(P[2].x, P[2].y); ctx.closePath(); ctx.fill(); ctx.globalAlpha = 1;
      seg(ctx, P[0].x, P[0].y, P[1].x, P[1].y); seg(ctx, P[2].x, P[2].y, P[3].x, P[3].y);
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 4) return false; return distSeg(x, y, P[0].x, P[0].y, P[1].x, P[1].y) < 7 || distSeg(x, y, P[2].x, P[2].y, P[3].x, P[3].y) < 7; },
  },
  // چنگال شیف (median از میان p0..mid عمودی)
  schiff: { label: 'چنگال شیف', points: 3, draw: drawFork('schiff'), hit: hitFork },
  // چنگال شیف اصلاح‌شده (median از میان p0..mid در هر دو محور)
  modschiff: { label: 'چنگال شیف اصلاح‌شده', points: 3, draw: drawFork('modschiff'), hit: hitFork },
  // چنگال داخلی (پرونگ‌ها از نیمه‌راه لنگرها)
  insidepitchfork: { label: 'چنگال داخلی', points: 3, draw: drawFork('inside'), hit: hitFork },
  // پیچ‌فن فیبوناچی: شعاع‌های موازی در نسبت‌های فیبوناچی بین دو پرونگ
  pitchfan: {
    label: 'پیچ‌فن فیبوناچی', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      const mid = { x: (P[1].x + P[2].x) / 2, y: (P[1].y + P[2].y) / 2 };
      const O = P[0], dx = mid.x - O.x, dy = mid.y - O.y; style(ctx, d);
      rayTo(ctx, O.x, O.y, mid.x, mid.y, api.W, api.H, false);
      levelValues(d).forEach((r) => {
        const bx = P[1].x + (P[2].x - P[1].x) * r, by = P[1].y + (P[2].y - P[1].y) * r;
        ctx.globalAlpha = 0.7; rayTo(ctx, bx, by, bx + dx, by + dy, api.W, api.H, false); ctx.globalAlpha = 1;
        ctx.fillText((r * 100).toFixed(1) + '%', bx + 3, by);
      });
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return false; const mid = { x: (P[1].x + P[2].x) / 2, y: (P[1].y + P[2].y) / 2 }; return distLine(x, y, P[0].x, P[0].y, mid.x, mid.y) < 7 || distSeg(x, y, P[1].x, P[1].y, P[2].x, P[2].y) < 7; },
  },

  // ───────── ۴.۴ فیبوناچی ─────────
  // فیبوی گسترشی سه‌نقطه‌ای (A→B→C) با تصویر صحیح از C
  fib3: {
    label: 'فیبوی گسترشی ۳نقطه', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p))) return;
      const A = d.pts[0].p, B = d.pts[1].p, C = d.pts[2].p, dir = (B - A) >= 0 ? 1 : -1, mag = Math.abs(B - A);
      const xa = api.x(d.pts[1].t), xb = api.x(d.pts[2].t) + 60;
      style(ctx, d); ctx.globalAlpha = 0.5; seg(ctx, P[0].x, P[0].y, P[1].x, P[1].y); seg(ctx, P[1].x, P[1].y, P[2].x, P[2].y); ctx.globalAlpha = 1;
      fibLevels(d, C, dir * mag).forEach((L) => { const y = api.y(L.price); if (y == null) return; dash(ctx, false); if (d.levels) { ctx.strokeStyle = L.color; ctx.fillStyle = L.color; } seg(ctx, xa, y, xb, y); ctx.fillText(`${(L.ratio * 100).toFixed(1)}%  ${L.price.toFixed(api.digits())}`, xa + 4, y - 2); });
    },
    hit(d, x, y, api) { const xa = api.x(d.pts[1].t), xb = api.x(d.pts[2].t) + 60; if (xa == null) return false; if (x < Math.min(xa, xb) - 7 || x > Math.max(xa, xb) + 7) return false; const A = d.pts[0].p, B = d.pts[1].p, C = d.pts[2].p, dir = (B - A) >= 0 ? 1 : -1, mag = Math.abs(B - A); return fibLevels(d, C, dir * mag).some((L) => { const yy = api.y(L.price); return yy != null && Math.abs(yy - y) < 7; }); },
  },
  // بادبزن فیبوناچی: شعاع از p0 از دل تقسیمات جعبه
  fibfan: {
    label: 'بادبزن فیبوناچی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      style(ctx, d); ctx.globalAlpha = 0.25; ctx.strokeRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y)); ctx.globalAlpha = 1;
      levelValues(d).forEach((r) => {
        const ty = a.y + (b.y - a.y) * r; dash(ctx, false); ctx.globalAlpha = 0.85; rayTo(ctx, a.x, a.y, b.x, ty, api.W, api.H, false); ctx.globalAlpha = 1;
        ctx.fillText((r * 100).toFixed(1) + '%', b.x + 3, ty);
      });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return levelValues(d).some((r) => { const ty = a.y + (b.y - a.y) * r; return distLine(x, y, a.x, a.y, b.x, ty) < 7 && x >= Math.min(a.x, b.x) - 7; }); },
  },
  // ناحیه‌های زمانی فیبوناچی: خطوط عمودی در شمارش فیبوناچی
  fibtime: {
    label: 'ناحیه‌ی زمانی فیبوناچی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; const unit = d.p1.t - d.p0.t; if (!unit) return;
      style(ctx, d);
      FIB_SEQ.forEach((n) => { const xx = api.x(d.p0.t + n * unit); if (xx == null) return; seg(ctx, xx, 0, xx, api.H); ctx.fillText(String(n), xx + 3, 14); });
    },
    hit(d, x, y, api) { const unit = d.p1.t - d.p0.t; if (!unit) return false; return FIB_SEQ.some((n) => { const xx = api.x(d.p0.t + n * unit); return xx != null && Math.abs(xx - x) < 7; }); },
  },
  // فیبوی زمانی روندی (۳نقطه): مضرب‌های فیبوناچی (t1-t0) از t2
  fibtimeext: {
    label: 'فیبوی زمانی روندی', points: 3,
    draw(ctx, d, api) {
      const unit = d.pts[1].t - d.pts[0].t; if (!unit) return; style(ctx, d);
      levelValues(d).forEach((r) => { const xx = api.x(d.pts[2].t + r * unit); if (xx == null) return; ctx.globalAlpha = 0.85; seg(ctx, xx, 0, xx, api.H); ctx.globalAlpha = 1; ctx.fillText((r).toFixed(3), xx + 3, 14); });
    },
    hit(d, x, y, api) { const unit = d.pts[1].t - d.pts[0].t; if (!unit) return false; return levelValues(d).some((r) => { const xx = api.x(d.pts[2].t + r * unit); return xx != null && Math.abs(xx - x) < 7; }); },
  },
  // کانال فیبوناچی (۳نقطه): کانال موازی با خطوط داخلی فیبوناچی
  fibchannel: {
    label: 'کانال فیبوناچی', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      const ox = P[2].x - P[0].x, oy = P[2].y - P[0].y; style(ctx, d);
      levelValues(d).forEach((r) => {
        const a = { x: P[0].x + ox * r, y: P[0].y + oy * r }, b = { x: P[1].x + ox * r, y: P[1].y + oy * r };
        ctx.globalAlpha = (r === 0 || r === 1) ? 1 : 0.7; seg(ctx, a.x, a.y, b.x, b.y); ctx.globalAlpha = 1; ctx.fillText((r * 100).toFixed(1) + '%', b.x + 3, b.y);
      });
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p))) return false; const ox = P[2].x - P[0].x, oy = P[2].y - P[0].y; return [0, 0.5, 1].some((r) => distSeg(x, y, P[0].x + ox * r, P[0].y + oy * r, P[1].x + ox * r, P[1].y + oy * r) < 7); },
  },
  // دایره‌های فیبوناچی (بیضی هم‌مرکز برای تصحیح مقیاس price/time)
  fibcircles: {
    label: 'دایره‌های فیبوناچی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const rx = Math.abs(b.x - a.x), ry = Math.abs(b.y - a.y); style(ctx, d);
      levelValues(d).forEach((r) => {
        ctx.globalAlpha = 0.7; ctx.beginPath(); ctx.ellipse(a.x, a.y, rx * r, ry * r, 0, 0, Math.PI * 2); ctx.stroke(); ctx.globalAlpha = 1;
      });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const rx = Math.abs(b.x - a.x) || 1, ry = Math.abs(b.y - a.y) || 1; return levelValues(d).some((r) => { const e = Math.hypot((x - a.x) / (rx * r || 1), (y - a.y) / (ry * r || 1)); return Math.abs(e - 1) < 0.08; }); },
  },
  // کمان‌های فیبوناچی (نیم‌بیضی پایین)
  fibarcs: {
    label: 'کمان‌های فیبوناچی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const rx = Math.abs(b.x - a.x), ry = Math.abs(b.y - a.y), down = b.y >= a.y; style(ctx, d);
      levelValues(d).forEach((r) => { ctx.globalAlpha = 0.8; ctx.beginPath(); ctx.ellipse(a.x, a.y, rx * r, ry * r, 0, down ? 0 : Math.PI, down ? Math.PI : 0); ctx.stroke(); ctx.globalAlpha = 1; });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const rx = Math.abs(b.x - a.x) || 1, ry = Math.abs(b.y - a.y) || 1; return levelValues(d).some((r) => Math.abs(Math.hypot((x - a.x) / (rx * r || 1), (y - a.y) / (ry * r || 1)) - 1) < 0.1); },
  },
  // مارپیچ فیبوناچی (لگاریتمی طلایی): p0 مرکز، p1 شعاع/زاویه‌ی شروع
  fibspiral: {
    label: 'مارپیچ فیبوناچی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const r0 = Math.hypot(b.x - a.x, b.y - a.y) || 1, a0 = Math.atan2(b.y - a.y, b.x - a.x);
      const growth = Math.log(1.6180339887) / (Math.PI / 2), TH = Math.PI * 6;
      style(ctx, d); ctx.beginPath(); let started = false;
      for (let th = 0; th <= TH; th += 0.1) { const r = r0 * Math.exp(-growth * (TH - th)); const x = a.x + r * Math.cos(a0 + th), y = a.y + r * Math.sin(a0 + th); if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y); }
      ctx.stroke();
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const r0 = Math.hypot(b.x - a.x, b.y - a.y) || 1, a0 = Math.atan2(b.y - a.y, b.x - a.x); const growth = Math.log(1.6180339887) / (Math.PI / 2), TH = Math.PI * 6; for (let th = 0; th <= TH; th += 0.1) { const r = r0 * Math.exp(-growth * (TH - th)); const xx = a.x + r * Math.cos(a0 + th), yy = a.y + r * Math.sin(a0 + th); if (Math.hypot(xx - x, yy - y) < 7) return true; } return false; },
  },
  // گُوه‌ی فیبوناچی (۳نقطه): قوس‌های فیبوناچی بین دو شعاع از رأس
  fibwedge: {
    label: 'گُوه‌ی فیبوناچی', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      const O = P[0], v1 = { x: P[1].x - O.x, y: P[1].y - O.y }, v2 = { x: P[2].x - O.x, y: P[2].y - O.y };
      style(ctx, d); ctx.globalAlpha = 0.7; seg(ctx, O.x, O.y, P[1].x, P[1].y); seg(ctx, O.x, O.y, P[2].x, P[2].y); ctx.globalAlpha = 1;
      levelValues(d).forEach((r) => {
        const a = { x: O.x + v1.x * r, y: O.y + v1.y * r }, b = { x: O.x + v2.x * r, y: O.y + v2.y * r };
        const cx = O.x + (v1.x + v2.x) / 2 * r * 1.15, cy = O.y + (v1.y + v2.y) / 2 * r * 1.15;
        ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.quadraticCurveTo(cx, cy, b.x, b.y); ctx.stroke();
        ctx.fillText((r * 100).toFixed(1) + '%', b.x + 3, b.y);
      });
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return false; return distSeg(x, y, P[0].x, P[0].y, P[1].x, P[1].y) < 7 || distSeg(x, y, P[0].x, P[0].y, P[2].x, P[2].y) < 7; },
  },

  // ───────── ۴.۵ گان ─────────
  // جعبه‌ی گان: شبکه‌ی قیمت/زمان + قطرها
  gannbox: {
    label: 'جعبه‌ی گان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const X = Math.min(a.x, b.x), Y = Math.min(a.y, b.y), w = Math.abs(b.x - a.x), h = Math.abs(b.y - a.y);
      style(ctx, d); ctx.strokeRect(X, Y, w, h);
      ctx.globalAlpha = 0.45;
      levelValues(d).forEach((r) => { if (r <= 0 || r >= 1) return; seg(ctx, X, Y + h * r, X + w, Y + h * r); seg(ctx, X + w * r, Y, X + w * r, Y + h); });
      ctx.globalAlpha = 0.8; seg(ctx, X, Y + h, X + w, Y); seg(ctx, X, Y, X + w, Y + h); ctx.globalAlpha = 1;
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return x >= Math.min(a.x, b.x) - 7 && x <= Math.max(a.x, b.x) + 7 && y >= Math.min(a.y, b.y) - 7 && y <= Math.max(a.y, b.y) + 7; },
  },
  // بادبزن گان: شعاع‌ها در زوایای 1×1،2×1،... از p0
  gannfan: {
    label: 'بادبزن گان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const ux = (b.x - a.x), uy = (b.y - a.y); style(ctx, d);
      GANN_FAN.forEach((g) => { ctx.globalAlpha = g.k === 1 ? 1 : 0.6; rayTo(ctx, a.x, a.y, a.x + ux, a.y + uy * g.k, api.W, api.H, false); ctx.globalAlpha = 1; });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const ux = (b.x - a.x), uy = (b.y - a.y); return GANN_FAN.some((g) => distLine(x, y, a.x, a.y, a.x + ux, a.y + uy * g.k) < 7 && (x - a.x) * ux >= 0); },
  },
  // مربع گان: جعبه + شبکه‌ی هشتم + قطرها + بیضی‌های هم‌مرکز از p0
  gannsquare: {
    label: 'مربع گان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const X = Math.min(a.x, b.x), Y = Math.min(a.y, b.y), w = Math.abs(b.x - a.x), h = Math.abs(b.y - a.y);
      style(ctx, d); ctx.strokeRect(X, Y, w, h);
      ctx.globalAlpha = 0.4; for (let i = 1; i < 8; i++) { seg(ctx, X + w * i / 8, Y, X + w * i / 8, Y + h); seg(ctx, X, Y + h * i / 8, X + w, Y + h * i / 8); }
      ctx.globalAlpha = 0.85; seg(ctx, a.x, a.y, b.x, b.y); seg(ctx, a.x, b.y, b.x, a.y);
      ctx.globalAlpha = 0.5; [0.25, 0.5, 0.75, 1].forEach((r) => { ctx.beginPath(); ctx.ellipse(a.x, a.y, w * r, h * r, 0, 0, Math.PI * 2); ctx.stroke(); }); ctx.globalAlpha = 1;
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return x >= Math.min(a.x, b.x) - 7 && x <= Math.max(a.x, b.x) + 7 && y >= Math.min(a.y, b.y) - 7 && y <= Math.max(a.y, b.y) + 7; },
  },
  // مربع ثابت گان: مربع واقعی (ضلع برابر) + شبکه‌ی هشتم + قطرهای ۴۵°
  gannfixed: {
    label: 'مربع ثابت گان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const s = Math.max(Math.abs(b.x - a.x), Math.abs(b.y - a.y)) || 40, sx = b.x >= a.x ? 1 : -1, sy = b.y >= a.y ? 1 : -1;
      const X = Math.min(a.x, a.x + sx * s), Y = Math.min(a.y, a.y + sy * s);
      style(ctx, d); ctx.strokeRect(X, Y, s, s);
      ctx.globalAlpha = 0.4; for (let i = 1; i < 8; i++) { seg(ctx, X + s * i / 8, Y, X + s * i / 8, Y + s); seg(ctx, X, Y + s * i / 8, X + s, Y + s * i / 8); }
      ctx.globalAlpha = 0.85; seg(ctx, X, Y, X + s, Y + s); seg(ctx, X, Y + s, X + s, Y); ctx.globalAlpha = 1;
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const s = Math.max(Math.abs(b.x - a.x), Math.abs(b.y - a.y)) || 40, sx = b.x >= a.x ? 1 : -1, sy = b.y >= a.y ? 1 : -1; const X = Math.min(a.x, a.x + sx * s), Y = Math.min(a.y, a.y + sy * s); return x >= X - 7 && x <= X + s + 7 && y >= Y - 7 && y <= Y + s + 7; },
  },

  // ───────── ۴.۶ الگوها ─────────
  // XABCD: پلی‌لاین ۵نقطه + نسبت‌های فیبوناچی لگ‌ها
  xabcd: { label: 'الگوی XABCD', points: 5, draw: drawPattern(['X', 'A', 'B', 'C', 'D'], true), hit: hitPoly },
  abcd: { label: 'الگوی ABCD', points: 4, draw: drawPattern(['A', 'B', 'C', 'D'], true), hit: hitPoly },
  cypher: { label: 'الگوی سایفر', points: 5, draw: drawPattern(['X', 'A', 'B', 'C', 'D'], true), hit: hitPoly },
  tripattern: {
    label: 'مثلث الگو', points: 3,
    draw(ctx, d, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return; style(ctx, d); ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.lineTo(P[2].x, P[2].y); ctx.closePath(); ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.1); ctx.fillStyle = d.fill || d.color || '#2962FF'; ctx.fill(); ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 1; ctx.stroke(); },
    hit: hitPoly,
  },
  hns: { label: 'سر و شانه', points: 5, draw: drawPattern(['LS', 'H', 'RS', '', ''], false), hit: hitPoly },
  ell_impulse: { label: 'موج ایمپالس الیوت', points: 6, draw: drawPattern(['0', '1', '2', '3', '4', '5'], false), hit: hitPoly },
  ell_abc: { label: 'موج اصلاحی الیوت', points: 4, draw: drawPattern(['0', 'A', 'B', 'C'], false), hit: hitPoly },
  // مثلث الیوت (ABCDE)
  ell_triangle: { label: 'مثلث الیوت', points: 6, draw: drawPattern(['0', 'A', 'B', 'C', 'D', 'E'], false), hit: hitPoly },
  // ترکیب دوگانه‌ی الیوت (WXY)
  ell_wxy: { label: 'ترکیب دوگانه‌ی الیوت', points: 4, draw: drawPattern(['0', 'W', 'X', 'Y'], false), hit: hitPoly },
  // ترکیب سه‌گانه‌ی الیوت (WXYXZ)
  ell_wxyxz: { label: 'ترکیب سه‌گانه‌ی الیوت', points: 6, draw: drawPattern(['0', 'W', 'X', 'Y', 'X', 'Z'], false), hit: hitPoly },
  // سه‌حرکت (Three Drives): درایوها + اصلاح‌ها با نسبت‌های فیبوناچی
  threedrives: { label: 'الگوی سه‌حرکت', points: 7, draw: drawPattern(['0', '1', 'A', '2', 'B', '3', 'C'], true), hit: hitPoly },
  // خطوط دوره‌ای / سیکل زمانی: عمودی‌های تکرارشونده با دوره‌ی (t1-t0)
  cyclic: {
    label: 'خطوط دوره‌ای', points: 2,
    draw(ctx, d, api) {
      const per = d.p1.t - d.p0.t; if (!per) return; style(ctx, d);
      for (let n = 0; n < 60; n++) { const xx = api.x(d.p0.t + n * per); if (xx == null) continue; if (xx > api.W + 4) break; seg(ctx, xx, 0, xx, api.H); }
    },
    hit(d, x, y, api) { const per = d.p1.t - d.p0.t; if (!per) return false; for (let n = 0; n < 60; n++) { const xx = api.x(d.p0.t + n * per); if (xx != null && Math.abs(xx - x) < 7) return true; } return false; },
  },
  // خط سینوسی: موج سینوس بین دو لنگر
  sine: {
    label: 'خط سینوسی', points: 2,
    draw(ctx, d, api) {
      const lam = d.p1.t - d.p0.t; if (!lam) return; const mid = (d.p0.p + d.p1.p) / 2, amp = Math.abs(d.p1.p - d.p0.p) / 2;
      style(ctx, d); ctx.beginPath(); let started = false;
      for (let i = 0; i <= 120; i++) { const tt = d.p0.t + lam * (i / 120) * 4; const pp = mid + amp * Math.sin(2 * Math.PI * (tt - d.p0.t) / lam); const xx = api.x(tt), yy = api.y(pp); if (xx == null || yy == null) continue; if (!started) { ctx.moveTo(xx, yy); started = true; } else ctx.lineTo(xx, yy); }
      ctx.stroke();
    },
    hit(d, x, y, api) { const lam = d.p1.t - d.p0.t; if (!lam) return false; const mid = (d.p0.p + d.p1.p) / 2, amp = Math.abs(d.p1.p - d.p0.p) / 2; for (let i = 0; i <= 120; i++) { const tt = d.p0.t + lam * (i / 120) * 4; const pp = mid + amp * Math.sin(2 * Math.PI * (tt - d.p0.t) / lam); const xx = api.x(tt), yy = api.y(pp); if (xx != null && yy != null && Math.hypot(xx - x, yy - y) < 7) return true; } return false; },
  },
  // چرخه‌های زمانی: نیم‌دایره‌های متوالی در کف فریم با دوره‌ی (t1-t0)
  timecycles: {
    label: 'چرخه‌های زمانی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const per = d.p1.t - d.p0.t; if (!per) return; const r0 = Math.abs(b.x - a.x) / 2 || (api.barWidth() || 6), base = api.H - 2;
      style(ctx, d);
      for (let n = 1; n <= 24; n++) {
        const cx = api.x(d.p0.t + (n - 0.5) * per); if (cx == null) continue; if (cx - r0 > api.W) break;
        ctx.globalAlpha = 0.8; ctx.beginPath(); ctx.arc(cx, base, r0, Math.PI, 0, false); ctx.stroke(); ctx.globalAlpha = 1;
        const xx = api.x(d.p0.t + n * per); if (xx != null) { ctx.globalAlpha = 0.25; seg(ctx, xx, 0, xx, api.H); ctx.globalAlpha = 1; }
      }
    },
    hit(d, x, y, api) { const per = d.p1.t - d.p0.t; if (!per) return false; for (let n = 0; n <= 24; n++) { const xx = api.x(d.p0.t + n * per); if (xx != null && Math.abs(xx - x) < 7) return true; } return false; },
  },

  // ───────── ۴.۷ پروجکشن و اندازه‌گیری ─────────
  // بازه‌ی قیمت: براکت عمودی + Δقیمت/Δدرصد
  pricerange: {
    label: 'بازه‌ی قیمت', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const cx = (a.x + b.x) / 2; style(ctx, d); seg(ctx, cx, a.y, cx, b.y); seg(ctx, cx - 8, a.y, cx + 8, a.y); seg(ctx, cx - 8, b.y, cx + 8, b.y);
      const dp = d.p1.p - d.p0.p, pct = d.p0.p ? dp / d.p0.p * 100 : 0;
      const col = dp >= 0 ? 'rgba(34,197,94,.12)' : 'rgba(239,68,68,.12)'; ctx.fillStyle = col; ctx.fillRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x) || 30, Math.abs(b.y - a.y));
      labelBox(ctx, cx, (a.y + b.y) / 2, `${dp.toFixed(api.digits())}\n${pct.toFixed(2)}%`, { anchor: 'center', border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const cx = (a.x + b.x) / 2; return Math.abs(x - cx) < 14 && y >= Math.min(a.y, b.y) - 7 && y <= Math.max(a.y, b.y) + 7; },
  },
  // بازه‌ی زمان: براکت افقی + Δبار/Δزمان
  daterange: {
    label: 'بازه‌ی زمان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const cy = (a.y + b.y) / 2; style(ctx, d); seg(ctx, a.x, cy, b.x, cy); seg(ctx, a.x, cy - 8, a.x, cy + 8); seg(ctx, b.x, cy - 8, b.x, cy + 8);
      const bars = Math.round((b.x - a.x) / (api.barWidth() || 6)), dt = Math.abs(d.p1.t - d.p0.t);
      const hh = Math.floor(dt / 3600), dd = Math.floor(dt / 86400);
      labelBox(ctx, (a.x + b.x) / 2, cy, `${Math.abs(bars)} ${tr(api, 'بار', 'bars')}\n${dd ? dd + tr(api, ' روز', 'd') : hh + tr(api, ' ساعت', 'h')}`, { anchor: 'center', border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const cy = (a.y + b.y) / 2; return Math.abs(y - cy) < 14 && x >= Math.min(a.x, b.x) - 7 && x <= Math.max(a.x, b.x) + 7; },
  },
  // بازه‌ی قیمت و زمان: جعبه + هر چهار Δ
  dprange: {
    label: 'بازه‌ی قیمت و زمان', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const X = Math.min(a.x, b.x), Y = Math.min(a.y, b.y), w = Math.abs(b.x - a.x), h = Math.abs(b.y - a.y);
      style(ctx, d); ctx.globalAlpha = 0.1; ctx.fillRect(X, Y, w, h); ctx.globalAlpha = 1; ctx.strokeRect(X, Y, w, h);
      const dp = d.p1.p - d.p0.p, pct = d.p0.p ? dp / d.p0.p * 100 : 0, bars = Math.round(w / (api.barWidth() || 6)), dt = Math.abs(d.p1.t - d.p0.t);
      // بازه‌ی زمانی تطبیقی (روز/ساعت/دقیقه) — یک‌دست با ابزار ruler و Measure TV؛ پیش‌تر همیشه «ساعت» بود (بازه‌ی کوتاه ⇒ «۰ ساعت»، بازه‌ی چندروزه ⇒ ساعت خام).
      const timeStr = spanText(api, dt);
      labelBox(ctx, X + w / 2, Y + h / 2, `${dp.toFixed(api.digits())}  (${pct.toFixed(2)}%)\n${Math.abs(bars)} ${tr(api, 'بار', 'bars')}  |  ${timeStr}`, { anchor: 'center', border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return x >= Math.min(a.x, b.x) - 7 && x <= Math.max(a.x, b.x) + 7 && y >= Math.min(a.y, b.y) - 7 && y <= Math.max(a.y, b.y) + 7; },
  },
  // پیش‌بینی: مخروط سایه‌دار از p0 به p1 + Δدرصد
  forecast: {
    label: 'پیش‌بینی', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      const spread = Math.abs(b.y - a.y) * 0.4 + 10; style(ctx, d);
      ctx.fillStyle = d.p1.p >= d.p0.p ? 'rgba(34,197,94,.10)' : 'rgba(239,68,68,.10)';
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y - spread); ctx.lineTo(b.x, b.y + spread); ctx.closePath(); ctx.fill();
      dash(ctx, true); seg(ctx, a.x, a.y, b.x, b.y); dash(ctx, false);
      const pct = d.p0.p ? (d.p1.p - d.p0.p) / d.p0.p * 100 : 0;
      labelBox(ctx, b.x + 4, b.y, `${pct.toFixed(2)}%`, { border: d.color });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distSeg(x, y, a.x, a.y, b.x, b.y) < 12; },
  },
  // پروجکشن (۳نقطه): لگ مرجع p0→p1 تصویر می‌شود از p2 (حرکت اندازه‌گیری‌شده) + Δدرصد
  projection: {
    label: 'پروجکشن', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      style(ctx, d); ctx.globalAlpha = 0.7; seg(ctx, P[0].x, P[0].y, P[1].x, P[1].y); ctx.globalAlpha = 1;
      const dx = P[1].x - P[0].x, dy = P[1].y - P[0].y, tip = { x: P[2].x + dx, y: P[2].y + dy };
      const up = (d.pts[1].p - d.pts[0].p) >= 0;
      ctx.fillStyle = up ? 'rgba(34,197,94,.10)' : 'rgba(239,68,68,.10)';
      ctx.beginPath(); ctx.moveTo(P[2].x, P[2].y); ctx.lineTo(tip.x, tip.y); ctx.lineTo(tip.x, P[2].y); ctx.closePath(); ctx.fill();
      dash(ctx, true); seg(ctx, P[2].x, P[2].y, tip.x, tip.y); dash(ctx, false);
      const pct = d.pts[0].p ? (d.pts[1].p - d.pts[0].p) / d.pts[0].p * 100 : 0;
      labelBox(ctx, tip.x + 4, tip.y, `${pct.toFixed(2)}%`, { border: d.color });
    },
    hit(d, x, y, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return false; const dx = P[1].x - P[0].x, dy = P[1].y - P[0].y; return distSeg(x, y, P[0].x, P[0].y, P[1].x, P[1].y) < 8 || distSeg(x, y, P[2].x, P[2].y, P[2].x + dx, P[2].y + dy) < 8; },
  },
  // خط روند قیمت‌ـزمان (ruler/measure سریع) — مثل بازه‌ی کامل اما با نمایش پیوسته
  ruler: {
    label: 'خط‌کش (اندازه‌گیری)', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return;
      style(ctx, d); dash(ctx, true); seg(ctx, a.x, a.y, b.x, b.y); dash(ctx, false);
      const dp = d.p1.p - d.p0.p, pct = d.p0.p ? dp / d.p0.p * 100 : 0, bars = Math.round((b.x - a.x) / (api.barWidth() || 6));
      // بازه‌ی زمانی (روز/ساعت/دقیقه) — مثل ابزار Measure TV که قیمت/٪/بار/زمان را نشان می‌دهد.
      const dt = Math.abs((d.p1.t || 0) - (d.p0.t || 0));
      const timeStr = spanText(api, dt);
      ctx.fillStyle = dp >= 0 ? 'rgba(34,197,94,.12)' : 'rgba(239,68,68,.12)'; ctx.fillRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y));
      labelBox(ctx, b.x, b.y, `${dp >= 0 ? '▲' : '▼'} ${Math.abs(dp).toFixed(api.digits())} (${pct.toFixed(2)}%)\n${Math.abs(bars)} ${tr(api, 'بار', 'bars')}  ·  ${timeStr}`, { anchor: 'above', bg: dp >= 0 ? 'rgba(22,101,52,.92)' : 'rgba(127,29,29,.92)' });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distSeg(x, y, a.x, a.y, b.x, b.y) < 8; },
  },

  // ───────── ۴.۸ اشکال ─────────
  // دایره (به‌صورت بیضی محاط مربع‌شده)
  circle: {
    label: 'دایره', points: 2,
    draw(ctx, d, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; const r = Math.hypot(b.x - a.x, b.y - a.y); style(ctx, d); ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.1); ctx.beginPath(); ctx.arc(a.x, a.y, r, 0, Math.PI * 2); ctx.fillStyle = d.fill || d.color || '#2962FF'; ctx.fill(); ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 1; ctx.stroke(); },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const r = Math.hypot(b.x - a.x, b.y - a.y); return Math.abs(Math.hypot(x - a.x, y - a.y) - r) < 8; },
  },
  // بیضی محاط جعبه
  ellipse: {
    label: 'بیضی', points: 2,
    draw(ctx, d, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; const cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2, rx = Math.abs(b.x - a.x) / 2, ry = Math.abs(b.y - a.y) / 2; style(ctx, d); ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.1); ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2); ctx.fillStyle = d.fill || d.color || '#2962FF'; ctx.fill(); ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 1; ctx.stroke(); },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; const cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2, rx = Math.abs(b.x - a.x) / 2 || 1, ry = Math.abs(b.y - a.y) / 2 || 1; const e = Math.hypot((x - cx) / rx, (y - cy) / ry); return Math.abs(e - 1) < 0.12; },
  },
  // مثلث (۳ رأس)
  triangle: {
    label: 'مثلث', points: 3,
    draw(ctx, d, api) { const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return; style(ctx, d); ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.lineTo(P[2].x, P[2].y); ctx.closePath(); ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.1); ctx.fillStyle = d.fill || d.color || '#2962FF'; ctx.fill(); ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 1; ctx.stroke(); },
    hit: hitPoly,
  },
  // مستطیل چرخیده (۳نقطه: جهت از p0→p1، عرض از p2)
  rotrect: {
    label: 'مستطیل چرخیده', points: 3,
    draw(ctx, d, api) {
      const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
      const ux = P[1].x - P[0].x, uy = P[1].y - P[0].y, L = Math.hypot(ux, uy) || 1; const nx = -uy / L, ny = ux / L;
      const w = ((P[2].x - P[0].x) * nx + (P[2].y - P[0].y) * ny);
      const c = [P[0], P[1], { x: P[1].x + nx * w, y: P[1].y + ny * w }, { x: P[0].x + nx * w, y: P[0].y + ny * w }];
      style(ctx, d); ctx.beginPath(); ctx.moveTo(c[0].x, c[0].y); c.slice(1).forEach((p) => ctx.lineTo(p.x, p.y)); ctx.closePath(); ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.1); ctx.fillStyle = d.fill || d.color || '#2962FF'; ctx.fill(); ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 1; ctx.stroke();
    },
    hit: hitPoly,
  },
  // پیکان (p0→p1 با سر پیکان)
  arrow: {
    label: 'پیکان', points: 2,
    draw(ctx, d, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; style(ctx, d); seg(ctx, a.x, a.y, b.x, b.y); arrowHead(ctx, a.x, a.y, b.x, b.y, 12); },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return distSeg(x, y, a.x, a.y, b.x, b.y) < 8; },
  },
  // قلم‌مو / آزاد: پلی‌لاین نرم نمونه‌برداری‌شده (d.pts با چند نقطه)
  brush: {
    label: 'قلم‌موی آزاد', points: -1, freehand: true,
    draw(ctx, d, api) { if (!d.pts || d.pts.length < 2) return; style(ctx, d); ctx.lineJoin = 'round'; ctx.lineCap = 'round'; ctx.beginPath(); let started = false; d.pts.forEach((pt) => { const P = px(api, pt); if (!ok(P)) return; if (!started) { ctx.moveTo(P.x, P.y); started = true; } else ctx.lineTo(P.x, P.y); }); ctx.stroke(); },
    hit(d, x, y, api) { if (!d.pts) return false; for (let i = 1; i < d.pts.length; i++) { const a = px(api, d.pts[i - 1]), b = px(api, d.pts[i]); if (ok(a) && ok(b) && distSeg(x, y, a.x, a.y, b.x, b.y) < 7) return true; } return false; },
  },
  // ماژیک / های‌لایتر: قلم‌موی ضخیم نیمه‌شفاف
  highlighter: {
    label: 'های‌لایتر', points: -1, freehand: true,
    draw(ctx, d, api) { if (!d.pts || d.pts.length < 2) return; ctx.strokeStyle = d.color || '#facc15'; ctx.lineWidth = d.width || 12; ctx.globalAlpha = 0.3; ctx.lineJoin = 'round'; ctx.lineCap = 'round'; dash(ctx, false); ctx.beginPath(); let started = false; d.pts.forEach((pt) => { const P = px(api, pt); if (!ok(P)) return; if (!started) { ctx.moveTo(P.x, P.y); started = true; } else ctx.lineTo(P.x, P.y); }); ctx.stroke(); ctx.globalAlpha = 1; },
    hit(d, x, y, api) { if (!d.pts) return false; for (let i = 1; i < d.pts.length; i++) { const a = px(api, d.pts[i - 1]), b = px(api, d.pts[i]); if (ok(a) && ok(b) && distSeg(x, y, a.x, a.y, b.x, b.y) < 8) return true; } return false; },
  },

  // ───────── ۴.۹ یادداشت‌ها ─────────
  // کال‌اوت: حباب متن + خط راهنما به نقطه‌ی هدف
  callout: {
    label: 'کال‌اوت', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; style(ctx, d);
      seg(ctx, a.x, a.y, b.x, b.y); ctx.beginPath(); ctx.arc(b.x, b.y, 3, 0, Math.PI * 2); ctx.fill();
      labelBox(ctx, a.x, a.y, d.text || tr(api, 'یادداشت', 'Callout'), { border: d.border || d.color, bg: d.bg || 'rgba(20,24,33,.95)', fg: d.textColor || undefined, font: textFont(d, 11), of: d });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a)) return false; return inBox(d, x, y) || Math.hypot(x - a.x, y - a.y) < 12 || (ok(b) && distSeg(x, y, a.x, a.y, b.x, b.y) < 6); },
  },
  // برچسب قیمت: تگ چسبیده به اسکیل راست
  pricelabel: {
    label: 'برچسب قیمت', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d); dash(ctx, true); seg(ctx, 0, a.y, api.W, a.y); dash(ctx, false);
      const txt = (d.text ? String(d.text).replace(/\\n|[\r\n]/g, ' ') + '  ' : '') + d.p0.p.toFixed(api.digits());
      ctx.font = textFont(d, 11); const w = ctx.measureText(txt).width + 12;
      ctx.fillStyle = d.color || '#2962FF'; ctx.fillRect(api.W - w, a.y - 9, w, 18);
      ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.fillText(txt, api.W - w + 6, a.y); ctx.textBaseline = 'alphabetic';
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.abs(y - a.y) < 7; },
  },
  // یادداشت/نوت: نشانگر کوچک که با hover متن را نشان می‌دهد (اینجا همیشه نمایش)
  note: {
    label: 'یادداشت', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d);
      ctx.beginPath(); ctx.arc(a.x, a.y, 7, 0, Math.PI * 2); ctx.fill(); ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText('i', a.x, a.y + 1); ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
      if (d.text) labelBox(ctx, a.x + 12, a.y - 8, d.text, { border: d.border || d.color, bg: d.bg || undefined, fg: d.textColor || undefined, font: textFont(d, 11), of: d });
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - a.y) < 10 || inBox(d, x, y); },
  },
  // پیکان جهت‌دار (گلیف تک‌لنگر): up/down/left/right
  arrowdir: {
    label: 'پیکان جهت‌دار', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d);
      const dir = d.dir || 'up', s = 14; const ends = { up: [0, -s], down: [0, s], left: [-s, 0], right: [s, 0] }[dir] || [0, -s];
      seg(ctx, a.x - ends[0], a.y - ends[1], a.x, a.y); arrowHead(ctx, a.x - ends[0], a.y - ends[1], a.x, a.y, 12);
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - a.y) < 16; },
  },
  // پرچم نشانه (گلیف تک‌لنگر) — هم‌تراز «Flag Mark» TV: میله + بادبان مثلثی رنگی روی نقطه‌ی لنگر.
  flag: {
    label: 'پرچم نشانه', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d);
      const H = 18, W = 11; const topY = a.y - H;
      // میله‌ی پرچم
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(a.x, topY); ctx.stroke();
      // بادبان مثلثی (پُرشده با رنگ ترسیم)
      ctx.beginPath(); ctx.moveTo(a.x, topY); ctx.lineTo(a.x + W, topY + 4); ctx.lineTo(a.x, topY + 8); ctx.closePath();
      ctx.fillStyle = d.color || '#2962FF'; ctx.fill();
      if (d.text) labelBox(ctx, a.x + W + 4, topY - 2, d.text, { border: d.border || d.color, bg: d.bg || undefined, fg: d.textColor || undefined, font: textFont(d, 11), of: d });
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - (a.y - 9)) < 14 || inBox(d, x, y); },
  },
  // تابلو راهنما (Signpost) — تک‌لنگر: میله + تابلو رنگی با نوک پیکانی و متن (هم‌تراز Signpost TV).
  signpost: {
    label: 'تابلو راهنما', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d);
      const H = 22, topY = a.y - H;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(a.x, topY); ctx.stroke();
      // تابلو راهنما تک‌خطی ثابت‌ارتفاع است؛ نیولاین (تایپ‌شده «\n» یا واقعی) به فاصله جمع می‌شود تا بک‌اسلش خام دیده نشود. #298
      const txt = (d.text || tr(api, 'راهنما', 'Signpost')).replace(/\\n|[\r\n]/g, ' ');
      ctx.font = textFont(d, 11);
      const w = ctx.measureText(txt).width + 18;
      ctx.fillStyle = d.color || '#2962FF';
      ctx.beginPath();
      ctx.moveTo(a.x + 7, topY - 9); ctx.lineTo(a.x + 7 + w, topY - 9); ctx.lineTo(a.x + 7 + w, topY + 9);
      ctx.lineTo(a.x + 7, topY + 9); ctx.lineTo(a.x, topY); ctx.closePath(); ctx.fill();
      ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
      ctx.direction = dirOf(txt); ctx.fillText(txt, a.x + 15, topY); ctx.direction = 'ltr'; ctx.textBaseline = 'alphabetic';
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return (Math.abs(x - a.x) < 7 && y <= a.y && y >= a.y - 22) || Math.hypot(x - (a.x + 20), y - (a.y - 22)) < 18; },
  },
  // نشانگر فلش رو‌به‌بالا (Arrow mark up) — تک‌لنگر: فلش پُر رو‌به‌بالا (سیگنال خرید). متمایز از arrowdir خطی.
  arrowup: {
    label: 'نشانگر فلش بالا', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; const s = 9; ctx.fillStyle = d.color || '#089981';
      ctx.beginPath(); ctx.moveTo(a.x, a.y - s); ctx.lineTo(a.x + s, a.y + 2); ctx.lineTo(a.x + 3, a.y + 2);
      ctx.lineTo(a.x + 3, a.y + s + 4); ctx.lineTo(a.x - 3, a.y + s + 4); ctx.lineTo(a.x - 3, a.y + 2); ctx.lineTo(a.x - s, a.y + 2); ctx.closePath(); ctx.fill();
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - a.y) < 14; },
  },
  // نشانگر فلش رو‌به‌پایین (Arrow mark down) — تک‌لنگر: فلش پُر رو‌به‌پایین (سیگنال فروش).
  arrowdown: {
    label: 'نشانگر فلش پایین', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; const s = 9; ctx.fillStyle = d.color || '#f23645';
      ctx.beginPath(); ctx.moveTo(a.x, a.y + s); ctx.lineTo(a.x + s, a.y - 2); ctx.lineTo(a.x + 3, a.y - 2);
      ctx.lineTo(a.x + 3, a.y - s - 4); ctx.lineTo(a.x - 3, a.y - s - 4); ctx.lineTo(a.x - 3, a.y - 2); ctx.lineTo(a.x - s, a.y - 2); ctx.closePath(); ctx.fill();
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - a.y) < 14; },
  },
  // خط چندتکه (Polyline) — چند-نقطه‌ای باز: کلیک برای هر رأس، دابل‌کلیک/Enter برای پایان (هم‌تراز Polyline TV).
  //   points:-1 و freehand نیست ⇒ در DrawingLayer.down شاخه‌ی اختصاصی نقاط را جمع می‌کند. handleها از مسیر عمومی pts می‌آیند.
  polyline: {
    label: 'خط چندتکه', points: -1, poly: true,
    draw(ctx, d, api) {
      const P = (d.pts || []).map((p) => px(api, p)).filter(ok);
      if (P.length < 2) return; style(ctx, d);
      ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); for (let i = 1; i < P.length; i++) ctx.lineTo(P[i].x, P[i].y); ctx.stroke();
    },
    hit(d, x, y, api) {
      const P = (d.pts || []).map((p) => px(api, p));
      for (let i = 0; i < P.length - 1; i++) {
        const a = P[i], bb = P[i + 1]; if (!ok(a) || !ok(bb)) continue;
        const C = bb.x - a.x, D = bb.y - a.y, len = C * C + D * D;
        const t = len ? Math.max(0, Math.min(1, ((x - a.x) * C + (y - a.y) * D) / len)) : 0;
        if (Math.hypot(x - (a.x + t * C), y - (a.y + t * D)) < 6) return true;
      }
      return false;
    },
  },
  // مسیر پیکان‌دار (Path) — مثل polyline ولی با سرپیکان در انتها. جمع نقاط در DrawingLayer.down با polyline مشترک است.
  path: {
    label: 'مسیر پیکان‌دار', points: -1, poly: true,
    draw(ctx, d, api) {
      const P = (d.pts || []).map((p) => px(api, p)).filter(ok);
      if (P.length < 2) return; style(ctx, d);
      ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); for (let i = 1; i < P.length; i++) ctx.lineTo(P[i].x, P[i].y); ctx.stroke();
      const a = P[P.length - 2], bb = P[P.length - 1]; arrowHead(ctx, a.x, a.y, bb.x, bb.y, 13);
    },
    hit(d, x, y, api) {
      const P = (d.pts || []).map((p) => px(api, p));
      for (let i = 0; i < P.length - 1; i++) {
        const a = P[i], bb = P[i + 1]; if (!ok(a) || !ok(bb)) continue;
        const C = bb.x - a.x, D = bb.y - a.y, len = C * C + D * D;
        const t = len ? Math.max(0, Math.min(1, ((x - a.x) * C + (y - a.y) * D) / len)) : 0;
        if (Math.hypot(x - (a.x + t * C), y - (a.y + t * D)) < 6) return true;
      }
      return false;
    },
  },
  // منحنی (Curve/quadratic bezier) — ۳ نقطه: آغاز · کنترل · پایان (points:3 ⇒ در EXT_NEED، جمع خودکار نقاط).
  curve: {
    label: 'منحنی', points: 3,
    draw(ctx, d, api) {
      const P = (d.pts || []).map((p) => px(api, p)); if (P.length < 3 || P.some((q) => !ok(q))) return; style(ctx, d);
      ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.quadraticCurveTo(P[1].x, P[1].y, P[2].x, P[2].y); ctx.stroke();
    },
    hit(d, x, y, api) { return bezierHit((d.pts || []).map((p) => px(api, p)), x, y, 2); },
  },
  // منحنی دوگانه (Double curve/cubic bezier) — ۴ نقطه: آغاز · کنترل۱ · کنترل۲ · پایان (points:4).
  doublecurve: {
    label: 'منحنی دوگانه', points: 4,
    draw(ctx, d, api) {
      const P = (d.pts || []).map((p) => px(api, p)); if (P.length < 4 || P.some((q) => !ok(q))) return; style(ctx, d);
      ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.bezierCurveTo(P[1].x, P[1].y, P[2].x, P[2].y, P[3].x, P[3].y); ctx.stroke();
    },
    hit(d, x, y, api) { return bezierHit((d.pts || []).map((p) => px(api, p)), x, y, 3); },
  },
  // کمان (Arc) — ۳ نقطه: آغاز · میانی (کنترل خمیدگی) · پایان؛ کمان دایره‌ای گذرنده از هر سه (points:3).
  arc: {
    label: 'کمان', points: 3,
    draw(ctx, d, api) {
      const P = (d.pts || []).map((p) => px(api, p)); if (P.length < 3 || P.some((q) => !ok(q))) return; style(ctx, d);
      const S = arcPoints(P[0], P[1], P[2], 30);
      ctx.beginPath(); ctx.moveTo(S[0].x, S[0].y); for (let i = 1; i < S.length; i++) ctx.lineTo(S[i].x, S[i].y); ctx.stroke();
    },
    hit(d, x, y, api) {
      const P = (d.pts || []).map((p) => px(api, p)); if (P.length < 3 || P.some((q) => !ok(q))) return false;
      const S = arcPoints(P[0], P[1], P[2], 30);
      for (let i = 0; i < S.length - 1; i++) {
        const a = S[i], bb = S[i + 1], C = bb.x - a.x, D = bb.y - a.y, len = C * C + D * D;
        const t = len ? Math.max(0, Math.min(1, ((x - a.x) * C + (y - a.y) * D) / len)) : 0;
        if (Math.hypot(x - (a.x + t * C), y - (a.y + t * D)) < 6) return true;
      }
      return false;
    },
  },
  // ───────── TradingView's tools that neither old app had ─────────
  // VWAP anchored to a bar: the volume-weighted average of the typical price from that bar to the
  // last one, with ±1σ bands when asked (d.bands).
  avwap: {
    label: 'VWAP لنگرشده', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); const path = vwapPath(api.candles, d.p0.t);
      style(ctx, d);
      const P = path.map((q) => ({ x: api.x(q.t), y: api.y(q.v), u: api.y(q.v + q.sd), l: api.y(q.v - q.sd) })).filter((q) => q.x != null && q.y != null);
      if (P.length) {
        ctx.beginPath(); P.forEach((q, i) => (i ? ctx.lineTo(q.x, q.y) : ctx.moveTo(q.x, q.y))); ctx.stroke();
        if (d.bands) { ctx.globalAlpha = 0.6; dash(ctx, true); for (const k of ['u', 'l']) { ctx.beginPath(); P.forEach((q, i) => (i ? ctx.lineTo(q.x, q[k]) : ctx.moveTo(q.x, q[k]))); ctx.stroke(); } dash(ctx, false); ctx.globalAlpha = 1; }
        const last = P[P.length - 1]; ctx.font = '11px IRANYekanX, sans-serif'; ctx.fillText('VWAP', last.x + 4, last.y - 4);
        ctx.beginPath(); ctx.arc(P[0].x, P[0].y, 3, 0, Math.PI * 2); ctx.fill();
      } else if (ok(a)) { ctx.beginPath(); ctx.arc(a.x, a.y, 3, 0, Math.PI * 2); ctx.fill(); }
    },
    hit(d, x, y, api) {
      const P = vwapPath(api.candles, d.p0.t).map((q) => ({ x: api.x(q.t), y: api.y(q.v) })).filter(ok);
      for (let i = 1; i < P.length; i++) if (distSeg(x, y, P[i - 1].x, P[i - 1].y, P[i].x, P[i].y) < 7) return true;
      const a = px(api, d.p0); return ok(a) && Math.hypot(x - a.x, y - a.y) < 8;
    },
  },
  // Fixed range volume profile: the volume traded at each price between two bars, rising bars'
  // volume and falling bars' apart, the point of control and the value area.
  fixedvp: {
    label: 'پروفایل حجم بازه‌ی ثابت', points: 2,
    draw(ctx, d, api) { drawProfile(ctx, d, api, d.p0.t, d.p1.t); },
    hit(d, x, y) { return inBox(d, x, y, 2); },
  },
  // Anchored volume profile: the same from one bar to the last.
  anchoredvp: {
    label: 'پروفایل حجم لنگرشده', points: 1,
    draw(ctx, d, api) { const cs = api.candles || []; const last = cs.length ? cs[cs.length - 1].t : d.p0.t; drawProfile(ctx, d, api, d.p0.t, last); },
    hit(d, x, y, api) { const a = px(api, d.p0); return inBox(d, x, y, 2) || (ok(a) && Math.hypot(x - a.x, y - a.y) < 8); },
  },
  // Anchored text: pinned to the screen (a share of the width and height), not to a bar and a
  // price, so it stays where it was put while the chart scrolls.
  anchoredtext: {
    label: 'متن لنگرشده', points: 1, screen: true,
    init(d, api, x, y) { d.ax = api.CW ? x / api.CW : 0.5; d.ay = api.CH ? y / api.CH : 0.5; },
    draw(ctx, d, api) { const x = (d.ax != null ? d.ax : 0.5) * api.CW, y = (d.ay != null ? d.ay : 0.5) * api.CH; drawTextBlock(ctx, d, x, y, tr(api, 'متن', 'Text')); },
    hit(d, x, y) { return inBox(d, x, y, 3); },
    handles(d, api) { return [{ x: (d.ax != null ? d.ax : 0.5) * api.CW, y: (d.ay != null ? d.ay : 0.5) * api.CH, screen: true, set: () => {} }]; },
  },
  // Price note: a label carrying the price of the point it is tied to.
  pricenote: {
    label: 'یادداشت قیمت', points: 2,
    draw(ctx, d, api) {
      const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return; style(ctx, d);
      seg(ctx, a.x, a.y, b.x, b.y); ctx.beginPath(); ctx.arc(a.x, a.y, 3, 0, Math.PI * 2); ctx.fill();
      const txt = (d.text ? `${d.text}\n` : '') + d.p0.p.toFixed(api.digits());
      labelBox(ctx, b.x, b.y, txt, { anchor: 'center', bg: d.bg || d.color || '#2962FF', fg: d.textColor || '#fff', border: d.border || undefined, font: textFont(d, 12), of: d });
    },
    hit(d, x, y, api) { const a = px(api, d.p0), b = px(api, d.p1); if (!ok(a) || !ok(b)) return false; return inBox(d, x, y) || distSeg(x, y, a.x, a.y, b.x, b.y) < 6; },
  },
  // Pin: a map pin on a bar, its text in a bubble above it.
  pin: {
    label: 'سنجاق', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d); dash(ctx, false);
      const r = 8, cy = a.y - 18;
      ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(a.x - r * 0.7, cy + r * 0.7); ctx.arc(a.x, cy, r, Math.PI * 0.75, Math.PI * 0.25); ctx.closePath(); ctx.fill();
      ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(a.x, cy, 3, 0, Math.PI * 2); ctx.fill();
      if (d.text) labelBox(ctx, a.x, cy - r - 4, d.text, { anchor: 'above', bg: d.bg || 'rgba(20,24,33,.92)', fg: d.textColor || '#fff', border: d.border || d.color, font: textFont(d, 12), of: d });
      else boxes.delete(d);
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return Math.hypot(x - a.x, y - (a.y - 16)) < 13 || inBox(d, x, y); },
  },
  // Comment: a speech bubble whose tail points at a bar.
  comment: {
    label: 'دیدگاه', points: 1,
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; style(ctx, d); dash(ctx, false);
      ctx.font = textFont(d, 13);
      const lines = textLines(ctx, d.text || tr(api, 'دیدگاه', 'Comment'), d.wrap ? (d.wrapWidth || 240) : 0);
      const fs = Math.max(8, Math.min(72, d.fontSize || 13)), lh = Math.round(fs * 1.35);
      let w = 0; lines.forEach((ln) => { w = Math.max(w, ctx.measureText(ln).width); });
      const bw = w + 20, bh = lines.length * lh + 14, bx = a.x - 10, by = a.y - 14 - bh;
      ctx.fillStyle = d.bg || d.color || '#2962FF';
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(bx, by, bw, bh, 8); else ctx.rect(bx, by, bw, bh);
      ctx.fill();
      ctx.beginPath(); ctx.moveTo(a.x - 2, by + bh - 1); ctx.lineTo(a.x, a.y); ctx.lineTo(a.x + 12, by + bh - 1); ctx.closePath(); ctx.fill();
      if (d.border) { ctx.strokeStyle = d.border; ctx.lineWidth = 1; ctx.beginPath(); if (ctx.roundRect) ctx.roundRect(bx, by, bw, bh, 8); else ctx.rect(bx, by, bw, bh); ctx.stroke(); }
      ctx.fillStyle = d.textColor || '#fff'; ctx.textBaseline = 'top'; ctx.textAlign = 'left';
      lines.forEach((ln, i) => { const rtl = dirOf(ln) === 'rtl'; ctx.direction = rtl ? 'rtl' : 'ltr'; ctx.textAlign = rtl ? 'right' : 'left'; ctx.fillText(ln, rtl ? bx + bw - 10 : bx + 10, by + 7 + i * lh); }); ctx.direction = 'ltr'; ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
      boxes.set(d, { x: bx, y: by, w: bw, h: bh });
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return inBox(d, x, y) || Math.hypot(x - a.x, y - a.y) < 8; },
  },
  // Table: a small grid of text tied to a bar and a price (its top-left corner).
  table: {
    label: 'جدول', points: 1,
    init(d) { if (!Array.isArray(d.cells)) d.cells = [['', ''], ['', '']]; },
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return;
      const cells = Array.isArray(d.cells) && d.cells.length ? d.cells : [['', '']];
      const cols = Math.max(...cells.map((r) => (Array.isArray(r) ? r.length : 0)), 1);
      const fs = Math.max(8, Math.min(48, d.fontSize || 12)), rh = Math.round(fs * 1.9);
      const widths = Array.from({ length: cols }, (_, c) => {
        let w = 36; cells.forEach((r, ri) => { ctx.font = (ri === 0 && d.header !== false) ? `bold ${fs}px IRANYekanX, sans-serif` : textFont(d, 12); w = Math.max(w, ctx.measureText(String((r && r[c]) ?? '')).width + 16); }); return w;
      });
      const tw = widths.reduce((s, w) => s + w, 0), th = rh * cells.length;
      ctx.save(); ctx.setLineDash([]);
      ctx.fillStyle = d.bg || 'rgba(41,98,255,0.08)'; ctx.globalAlpha = d.bg ? (d.bgOpacity != null ? d.bgOpacity : 0.9) : 1; ctx.fillRect(a.x, a.y, tw, th); ctx.globalAlpha = 1;
      if (d.header !== false && cells.length > 1) { ctx.fillStyle = d.color || '#2962FF'; ctx.globalAlpha = 0.14; ctx.fillRect(a.x, a.y, tw, rh); ctx.globalAlpha = 1; }
      ctx.strokeStyle = d.border || d.color || '#2962FF'; ctx.lineWidth = 1; ctx.strokeRect(a.x + 0.5, a.y + 0.5, tw, th);
      for (let r = 1; r < cells.length; r++) { ctx.beginPath(); ctx.moveTo(a.x, a.y + r * rh + 0.5); ctx.lineTo(a.x + tw, a.y + r * rh + 0.5); ctx.stroke(); }
      let cx = a.x; for (let c = 0; c < cols - 1; c++) { cx += widths[c]; ctx.beginPath(); ctx.moveTo(cx + 0.5, a.y); ctx.lineTo(cx + 0.5, a.y + th); ctx.stroke(); }
      ctx.fillStyle = d.textColor || d.color || '#2962FF'; ctx.textBaseline = 'middle';
      cells.forEach((row, ri) => { let x0 = a.x; for (let c = 0; c < cols; c++) { ctx.font = (ri === 0 && d.header !== false && cells.length > 1) ? `bold ${fs}px IRANYekanX, sans-serif` : textFont(d, 12); const tx = String((row && row[c]) ?? ''); const rtl = dirOf(tx) === 'rtl'; ctx.direction = rtl ? 'rtl' : 'ltr'; ctx.textAlign = rtl ? 'right' : 'left'; ctx.fillText(tx, rtl ? x0 + widths[c] - 8 : x0 + 8, a.y + ri * rh + rh / 2); x0 += widths[c]; } }); ctx.direction = 'ltr'; ctx.textAlign = 'left';
      ctx.restore();
      boxes.set(d, { x: a.x, y: a.y, w: tw, h: th });
    },
    hit(d, x, y, api) { const a = px(api, d.p0); if (!ok(a)) return false; return inBox(d, x, y, 2) || Math.hypot(x - a.x, y - a.y) < 8; },
  },
  // Bars pattern: the bars between two clicks, copied and laid down again where the copy is
  // dragged (d.dst), as candles, high-low bars or a close line, mirrored or flipped on request.
  barspattern: {
    label: 'الگوی کندل‌ها', points: 2,
    init(d, api) {
      const t0 = Math.min(d.p0.t, d.p1.t), t1 = Math.max(d.p0.t, d.p1.t);
      const cs = (api.candles || []).filter((c) => c.t >= t0 && c.t <= t1);
      d.src = cs.map((c) => [c.o, c.h, c.l, c.c]);
      const iv = api.barInterval() || 60;
      d.dst = { t: (cs.length ? cs[cs.length - 1].t : t1) + iv, p: cs.length ? cs[0].o : d.p0.p };
    },
    draw(ctx, d, api) {
      const src = Array.isArray(d.src) ? d.src : [];
      if (!src.length || !d.dst) { const a = px(api, d.p0), b = px(api, d.p1); if (ok(a) && ok(b)) { style(ctx, d); ctx.globalAlpha = 0.25; ctx.fillRect(Math.min(a.x, b.x), 0, Math.abs(b.x - a.x), api.H); ctx.globalAlpha = 1; } return; }
      const iv = api.barInterval() || 60, o0 = src[0][0], shift = d.dst.p - o0;
      const bw = Math.max(1, (api.barWidth() || 6) * 0.7);
      style(ctx, d); dash(ctx, false);
      let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
      const pts = [];
      for (let i = 0; i < src.length; i++) {
        const s = d.mirror ? src[src.length - 1 - i] : src[i];
        let [o, h, l, c] = s.map((v) => (d.flip ? 2 * o0 - v : v));
        if (d.mirror) { const t = o; o = c; c = t; }
        if (d.flip) { const t = h; h = l; l = t; }
        o += shift; h += shift; l += shift; c += shift;
        const x = api.x(d.dst.t + i * iv); const yo = api.y(o), yh = api.y(h), yl = api.y(l), yc = api.y(c);
        if (x == null || yo == null || yh == null || yl == null || yc == null) continue;
        x0 = Math.min(x0, x - bw); x1 = Math.max(x1, x + bw); y0 = Math.min(y0, yh); y1 = Math.max(y1, yl);
        if (d.mode === 'line') { pts.push({ x, y: yc }); continue; }
        ctx.globalAlpha = 0.75; ctx.beginPath(); ctx.moveTo(x, yh); ctx.lineTo(x, yl); ctx.stroke();
        if (d.mode === 'hl') { ctx.beginPath(); ctx.moveTo(x - bw / 2, yo); ctx.lineTo(x, yo); ctx.moveTo(x, yc); ctx.lineTo(x + bw / 2, yc); ctx.stroke(); }
        else { ctx.globalAlpha = c >= o ? 0.25 : 0.6; ctx.fillRect(x - bw / 2, Math.min(yo, yc), bw, Math.max(1, Math.abs(yc - yo))); ctx.globalAlpha = 0.9; ctx.strokeRect(x - bw / 2, Math.min(yo, yc), bw, Math.max(1, Math.abs(yc - yo))); }
      }
      if (pts.length) { ctx.globalAlpha = 0.9; ctx.beginPath(); pts.forEach((q, i) => (i ? ctx.lineTo(q.x, q.y) : ctx.moveTo(q.x, q.y))); ctx.stroke(); }
      ctx.globalAlpha = 1;
      if (Number.isFinite(x0)) boxes.set(d, { x: x0, y: y0, w: x1 - x0, h: y1 - y0 }); else boxes.delete(d);
    },
    hit(d, x, y) { return inBox(d, x, y, 3); },
    handles(d, api) { if (!d.dst) return null; return [{ x: api.x(d.dst.t), y: api.y(d.dst.p), set: (t, p) => { d.dst = { t, p }; } }]; },
    move(d, dt, dp) { if (d.dst) d.dst = { t: d.dst.t + dt, p: d.dst.p + dp }; return true; },
  },
  // Ghost feed: a path of clicks the price might take, drawn as made-up candles along it, about
  // as tall as the recent ones. Finish with a double click, Enter, or the eighth point.
  ghostfeed: {
    label: 'خوراک شبح', points: -1, poly: true, maxPts: 8,
    draw(ctx, d, api) {
      const P = d.pts || []; if (P.length < 2) return;
      const iv = api.barInterval() || 60, cs = api.candles || [];
      const recent = cs.slice(-50); const avg = recent.length ? recent.reduce((s, c) => s + (c.h - c.l), 0) / recent.length : Math.abs(P[0].p) * 0.002;
      const seed = Math.abs(Math.round(P[0].t / iv)) % 9973;
      const rnd = (i) => { const v = Math.sin((i + 1) * 12.9898 + seed * 78.233) * 43758.5453; return v - Math.floor(v) - 0.5; };
      const bw = Math.max(1, (api.barWidth() || 6) * 0.7);
      ctx.save(); ctx.setLineDash([]); ctx.lineWidth = 1;
      let k = 0;
      for (let s = 0; s + 1 < P.length; s++) {
        const A = P[s], B = P[s + 1]; if (!(B.t > A.t)) continue;
        const n = Math.max(1, Math.round((B.t - A.t) / iv));
        for (let j = 0; j < n; j++, k++) {
          const o = A.p + (B.p - A.p) * (j / n), c0 = A.p + (B.p - A.p) * ((j + 1) / n);
          const c = j === n - 1 ? B.p : c0 + rnd(k) * avg * 0.3;
          const h = Math.max(o, c) + Math.abs(rnd(k + 101)) * avg * 0.6, l = Math.min(o, c) - Math.abs(rnd(k + 202)) * avg * 0.6;
          const x = api.x(A.t + j * iv), yo = api.y(o), yc = api.y(c), yh = api.y(h), yl = api.y(l);
          if (x == null || yo == null || yc == null || yh == null || yl == null) continue;
          const col = c >= o ? (d.upColor || '#089981') : (d.downColor || '#f23645');
          ctx.strokeStyle = col; ctx.fillStyle = col; ctx.globalAlpha = 0.6;
          ctx.beginPath(); ctx.moveTo(x, yh); ctx.lineTo(x, yl); ctx.stroke();
          ctx.fillRect(x - bw / 2, Math.min(yo, yc), bw, Math.max(1, Math.abs(yc - yo)));
        }
      }
      ctx.restore();
    },
    hit(d, x, y, api) { const P = (d.pts || []).map((q) => px(api, q)); for (let i = 1; i < P.length; i++) { if (ok(P[i - 1]) && ok(P[i]) && distSeg(x, y, P[i - 1].x, P[i - 1].y, P[i].x, P[i].y) < 10) return true; } return false; },
  },
  // Icon: an emoji or a glyph placed on a bar (TradingView's Icons group).
  emoji: {
    label: 'آیکون', points: 1,
    init(d, api) { if (!d.text) d.text = (api.emoji && api.emoji()) || '🙂'; },
    draw(ctx, d, api) {
      const a = px(api, d.p0); if (!ok(a)) return; const fs = Math.max(12, Math.min(96, d.fontSize || 28));
      ctx.font = `${fs}px IRANYekanX, sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillStyle = d.color || '#2962FF';
      ctx.fillText(String(d.text || '🙂'), a.x, a.y); ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
      boxes.set(d, { x: a.x - fs / 2, y: a.y - fs / 2, w: fs, h: fs });
    },
    hit(d, x, y) { return inBox(d, x, y, 2); },
  },

};

// The anchored VWAP from bar time t0 on: [{t, v, sd}] for every bar from the anchor to the last.
export function vwapPath(candles, t0) {
  let sv = 0, spv = 0, spv2 = 0; const out = [];
  for (const c of candles || []) {
    if (c.t < t0) continue;
    const tp = (c.h + c.l + c.c) / 3, v = c.v || 1;
    sv += v; spv += tp * v; spv2 += tp * tp * v;
    const m = spv / sv; out.push({ t: c.t, v: m, sd: Math.sqrt(Math.max(0, spv2 / sv - m * m)) });
  }
  return out;
}
// Volume at price between two times: `rows` price rows from the lowest low to the highest high,
// each bar's volume spread evenly over the rows its range covers and kept apart for rising and
// falling bars; the point of control (the busiest row) and the value area (`va` of the volume,
// grown from the POC towards the busier neighbour, as TradingView does). Forex without volume
// counts each bar once.
export function volumeRows(candles, t0, t1, rows, va) {
  const cs = (candles || []).filter((c) => c.t >= t0 && c.t <= t1);
  if (!cs.length) return null;
  let lo = Infinity, hi = -Infinity;
  for (const c of cs) { lo = Math.min(lo, c.l, c.h); hi = Math.max(hi, c.l, c.h); }
  if (!(hi > lo)) hi = lo + (Math.abs(lo) * 1e-6 || 1e-9);
  const n = Math.max(4, Math.min(200, Math.round(rows || 24))), step = (hi - lo) / n;
  const out = Array.from({ length: n }, (_, i) => ({ lo: lo + i * step, hi: lo + (i + 1) * step, up: 0, down: 0, poc: false, va: false }));
  for (const c of cs) {
    const w = c.v || 1;
    const a = Math.max(0, Math.min(n - 1, Math.floor((Math.min(c.l, c.h) - lo) / step)));
    const b = Math.max(0, Math.min(n - 1, Math.floor((Math.max(c.l, c.h) - lo) / step)));
    const per = w / (b - a + 1), k = c.c >= c.o ? 'up' : 'down';
    for (let i = a; i <= b; i++) out[i][k] += per;
  }
  const vol = (i) => out[i].up + out[i].down;
  let p = 0; for (let i = 1; i < n; i++) if (vol(i) > vol(p)) p = i;
  out[p].poc = true;
  const tot = out.reduce((s, r) => s + r.up + r.down, 0);
  let a = p, b = p, acc = vol(p);
  while (acc < tot * (va || 0.7) && (a > 0 || b < n - 1)) {
    const below = a > 0 ? vol(a - 1) : -1, above = b < n - 1 ? vol(b + 1) : -1;
    if (above >= below) acc += vol(++b); else acc += vol(--a);
  }
  for (let i = a; i <= b; i++) out[i].va = true;
  return { rows: out, poc: p, vah: out[b].hi, val: out[a].lo, lo, hi, bars: cs.length };
}
// Draws a volume profile over [t0, t1]: the rows from the range's left edge, `widthPct` of its
// width at the busiest row, the value area stronger, the POC as a line across the range.
function drawProfile(ctx, d, api, t0, t1) {
  const ta = Math.min(t0, t1), tb = Math.max(t0, t1);
  const prof = volumeRows(api.candles, ta, tb, d.rows || 24, d.va || 0.7);
  const xa = api.x(ta), xb = api.x(tb);
  if (!prof || xa == null || xb == null) { boxes.delete(d); return; }
  const left = Math.min(xa, xb), right = Math.max(xa, xb, left + 30);
  const maxW = (right - left) * (d.widthPct || 0.3);
  const maxV = Math.max(...prof.rows.map((r) => r.up + r.down)) || 1;
  const up = d.upColor || '#089981', down = d.downColor || '#f23645';
  ctx.save(); ctx.setLineDash([]);
  for (const r of prof.rows) {
    const yT = api.y(r.hi), yB = api.y(r.lo); if (yT == null || yB == null) continue;
    const top = Math.min(yT, yB) + 0.5, h = Math.max(1, Math.abs(yB - yT) - 1);
    const wu = (r.up / maxV) * maxW, wd = (r.down / maxV) * maxW;
    ctx.globalAlpha = r.va ? 0.55 : 0.22;
    ctx.fillStyle = up; ctx.fillRect(left, top, wu, h);
    ctx.fillStyle = down; ctx.fillRect(left + wu, top, wd, h);
  }
  ctx.globalAlpha = 1;
  const pr = prof.rows[prof.poc], yP = api.y((pr.lo + pr.hi) / 2);
  if (yP != null) { ctx.strokeStyle = d.pocColor || '#f23645'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.moveTo(left, yP); ctx.lineTo(right, yP); ctx.stroke(); }
  const yHi = api.y(prof.hi), yLo = api.y(prof.lo);
  if (yHi != null && yLo != null) {
    ctx.strokeStyle = d.color || '#2962FF'; ctx.globalAlpha = 0.35; ctx.lineWidth = 1; ctx.strokeRect(left + 0.5, Math.min(yHi, yLo) + 0.5, right - left, Math.abs(yLo - yHi)); ctx.globalAlpha = 1;
    boxes.set(d, { x: left, y: Math.min(yHi, yLo), w: right - left, h: Math.abs(yLo - yHi) });
  }
  ctx.restore();
}

// ── سازنده‌ی تابع draw برای الگوهای پلی‌لاین برچسب‌دار ──────────────────────
function drawPattern(labels, ratios) {
  return function (ctx, d, api) {
    const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 2) return;
    style(ctx, d);
    ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); P.slice(1).forEach((p) => ctx.lineTo(p.x, p.y)); ctx.stroke();
    // سایه‌ی مثلث‌های متوالی
    ctx.globalAlpha = 0.06;
    for (let i = 0; i + 2 < P.length; i++) { ctx.beginPath(); ctx.moveTo(P[i].x, P[i].y); ctx.lineTo(P[i + 1].x, P[i + 1].y); ctx.lineTo(P[i + 2].x, P[i + 2].y); ctx.closePath(); ctx.fill(); }
    ctx.globalAlpha = 1;
    // برچسب رأس‌ها
    P.forEach((p, i) => { const lb = labels[i]; if (!lb) return; ctx.fillStyle = '#fff'; ctx.strokeStyle = d.color; ctx.beginPath(); ctx.arc(p.x, p.y, 8, 0, Math.PI * 2); ctx.fill(); ctx.lineWidth = 1.5; ctx.stroke(); ctx.fillStyle = d.color; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.font = 'bold 10px IRANYekanX, sans-serif'; ctx.fillText(lb, p.x, p.y + 1); ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic'; });
    // نسبت‌های فیبوناچی لگ‌ها (price-space)
    if (ratios) {
      for (let i = 1; i + 1 < d.pts.length; i++) {
        const leg1 = Math.abs(d.pts[i].p - d.pts[i - 1].p), leg2 = Math.abs(d.pts[i + 1].p - d.pts[i].p);
        const r = leg1 ? (leg2 / leg1) : 0;
        const mx = (P[i].x + P[i + 1].x) / 2, my = (P[i].y + P[i + 1].y) / 2;
        ctx.fillStyle = d.color; ctx.font = '10px IRANYekanX, sans-serif'; ctx.fillText(r.toFixed(3), mx + 4, my);
      }
    }
  };
}
// hit-test مشترک پلی‌لاین/چندضلعی
function hitPoly(d, x, y, api) {
  if (!d.pts) return false;
  for (let i = 1; i < d.pts.length; i++) { const a = px(api, d.pts[i - 1]), b = px(api, d.pts[i]); if (ok(a) && ok(b) && distSeg(x, y, a.x, a.y, b.x, b.y) < 7) return true; }
  return false;
}

// ── سازنده‌ی چنگال‌های اندروزی (Schiff/Modified/Inside) ─────────────────────
// p0 = دسته‌ی چنگال، p1/p2 = دو پرونگ. خط میانه از O به میانه‌ی p1p2؛ پرونگ‌ها موازی میانه.
function drawFork(mode) {
  return function (ctx, d, api) {
    const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return;
    const mid = { x: (P[1].x + P[2].x) / 2, y: (P[1].y + P[2].y) / 2 };
    let O = P[0], A1 = P[1], A2 = P[2];
    if (mode === 'schiff') O = { x: P[0].x, y: (P[0].y + mid.y) / 2 };
    else if (mode === 'modschiff') O = { x: (P[0].x + mid.x) / 2, y: (P[0].y + mid.y) / 2 };
    else if (mode === 'inside') { A1 = { x: (P[0].x + P[1].x) / 2, y: (P[0].y + P[1].y) / 2 }; A2 = { x: (P[0].x + P[2].x) / 2, y: (P[0].y + P[2].y) / 2 }; }
    const dx = mid.x - O.x, dy = mid.y - O.y;
    style(ctx, d);
    rayTo(ctx, O.x, O.y, mid.x, mid.y, api.W, api.H, false);
    ctx.globalAlpha = 0.85;
    rayTo(ctx, A1.x, A1.y, A1.x + dx, A1.y + dy, api.W, api.H, false);
    rayTo(ctx, A2.x, A2.y, A2.x + dx, A2.y + dy, api.W, api.H, false);
    ctx.globalAlpha = 0.6; seg(ctx, A1.x, A1.y, A2.x, A2.y); ctx.globalAlpha = 1;
  };
}
// hit-test مشترک چنگال‌ها
function hitFork(d, x, y, api) {
  const P = d.pts.map((p) => px(api, p)); if (P.some((p) => !ok(p)) || P.length < 3) return false;
  const mid = { x: (P[1].x + P[2].x) / 2, y: (P[1].y + P[2].y) / 2 }, dx = mid.x - P[0].x, dy = mid.y - P[0].y;
  return distLine(x, y, P[0].x, P[0].y, mid.x, mid.y) < 7
    || distLine(x, y, P[1].x, P[1].y, P[1].x + dx, P[1].y + dy) < 7
    || distLine(x, y, P[2].x, P[2].y, P[2].x + dx, P[2].y + dy) < 7;
}

// ── handles پیش‌فرض برای تک‌لنگرها (p0) ────────────────────────────────────
// ابزارهایی که فقط p0 دارند (hray/crossline/pricelabel/note/arrowdir...) یک handle می‌خواهند.
// بقیه (p0+p1 یا pts) را خود DrawingLayer از مسیر عمومی می‌سازد.
const SINGLE_ANCHOR = new Set(['hray', 'crossline', 'pricelabel', 'note', 'arrowdir', 'flag', 'signpost', 'arrowup', 'arrowdown', 'avwap', 'anchoredvp', 'pin', 'comment', 'table', 'emoji']);

// ════════════════════════════════════════════════════════════════════════════
// خروجی‌های آماده‌برای‌وصل‌شدن
// ════════════════════════════════════════════════════════════════════════════

// نقشه‌ی تعداد کلیک ابزارهای چندنقطه‌ای (≥۳) → برای ادغام با NEED در drawings.js.
// (ابزارهای ۲نقطه‌ای drag-based هستند و در NEED قرار نمی‌گیرند؛ ابزارهای freehand جداگانه.)
export const EXT_NEED = (() => { const m = {}; Object.entries(EXT_REGISTRY).forEach(([k, v]) => { if (v.points >= 3) m[k] = v.points; }); return m; })();

// ابزارهای ۲نقطه‌ای drag-based (مثل trend/rect) → باید مثل this.tmp رفتار کنند.
export const EXT_DRAG2 = Object.entries(EXT_REGISTRY).filter(([, v]) => v.points === 2).map(([k]) => k);

// ابزارهای freehand (brush/highlighter) که با sampling روی mousemove ساخته می‌شوند.
export const EXT_FREEHAND = Object.entries(EXT_REGISTRY).filter(([, v]) => v.freehand).map(([k]) => k);

// Tools placed with one click (one anchor), as TradingView places a note, a flag or an icon.
export const EXT_ONE = Object.entries(EXT_REGISTRY).filter(([, v]) => v.points === 1).map(([k]) => k);

// Tools built point by point until a double click, Enter, or their last point (maxPts).
export const EXT_POLY = Object.entries(EXT_REGISTRY).filter(([, v]) => v.poly).map(([k]) => k);
export const extMaxPts = (type) => (EXT_REGISTRY[type] && EXT_REGISTRY[type].maxPts) || 0;

// Tools pinned to the screen rather than to a bar and a price.
export const extScreen = (type) => Boolean(EXT_REGISTRY[type] && EXT_REGISTRY[type].screen);

/** A new drawing's own set-up (a table's cells, the bars a pattern copies), at the pixel it was placed. */
export function extInit(d, layer, x, y) { const e = EXT_REGISTRY[d.type]; if (e && e.init) { try { e.init(d, extApi(layer), x, y); } catch (err) { /* keep the bare drawing */ } } }

/** Moves a drawing the tool's own way (the copy of a bars pattern); false = the layer's generic move. */
export function extMove(d, dt, dp, layer) { const e = EXT_REGISTRY[d.type]; if (!e || !e.move) return false; try { return e.move(d, dt, dp, extApi(layer)) !== false; } catch (err) { return false; } }

// برچسب‌های فارسی → برای ادغام با DrawingLayer.static label.
export const EXT_LABELS = (() => { const m = {}; Object.entries(EXT_REGISTRY).forEach(([k, v]) => { m[k] = v.label; }); return m; })();

// آیا این type متعلق به این بسته است؟
export const isExt = (type) => Object.prototype.hasOwnProperty.call(EXT_REGISTRY, type);

// ساخت api مختصاتی از یک نمونه‌ی DrawingLayer (متدهای _x/_y/_t/_p/_barWidth).
export function extApi(layer) {
  return {
    x: (t) => layer._x(t),
    y: (p) => layer._y(p),
    t: (x) => layer._t(x),
    p: (y) => layer._p(y),
    barWidth: () => layer._barWidth(),
    digits: () => (layer && Number.isFinite(layer.digits)) ? layer.digits : 5, // دقت اعشار نماد فعال برای برچسب ابزار اندازه‌گیری (پیش‌فرض ۵ = رفتار قبلی)
    timeFmt: (t) => { try { return layer.timeFmt ? String(layer.timeFmt(t) || '') : ''; } catch (e) { return ''; } }, // فرمتر زمان tz-aware برای برچسب تاریخ خط صلیبی (#282)
    lang: () => (layer && layer.lang) || 'fa',
    barInterval: () => (layer && layer._barInterval ? layer._barInterval() : null),
    emoji: () => (layer && layer.emoji) || '',
    // The main pane (drawings stay inside it); CW/CH are the whole canvas.
    get W() { return layer._paneBox ? layer._paneBox.w : layer.canvas.width; },
    get H() { return layer._paneBox ? layer._paneBox.h : layer.canvas.height; },
    get CW() { return layer.canvas.width; },
    get CH() { return layer.canvas.height; },
    candles: layer.candles,
  };
}

// رسم یک آبجکت این بسته (فراخوانی از داخل _draw).
// A tool drawn while it is still being placed may have fewer points than it needs (fib3's preview
// after its second click); the half-built shape is skipped rather than throwing out of the frame.
export function extDraw(ctx, d, layer) { const e = EXT_REGISTRY[d.type]; if (!e) return false; if (d.visible === false) return true; try { ctx.save(); e.draw(ctx, d, extApi(layer)); } catch (err) { /* partial preview */ } finally { ctx.restore(); } return true; }

// hit-test یک آبجکت این بسته (فراخوانی از داخل _hit).
export function extHit(d, x, y, layer) { const e = EXT_REGISTRY[d.type]; if (!e || !e.hit) return false; try { return e.hit(d, x, y, extApi(layer)); } catch (err) { return false; } }

// handles یک آبجکت این بسته؛ null یعنی «از مسیر عمومی DrawingLayer استفاده کن».
export function extHandles(d, layer) {
  const api = extApi(layer);
  const e = EXT_REGISTRY[d.type];
  if (e && e.handles) { const h = e.handles(d, api); if (h) return h; }
  if (SINGLE_ANCHOR.has(d.type) && d.p0) return [{ x: api.x(d.p0.t), y: api.y(d.p0.p), set: (t, p) => { d.p0 = { t, p }; } }];
  return null; // pts یا p0/p1 → مسیر عمومی
}

// تعریف ابزارها برای نوارابزار React (TOOLS). icon = نام آیکون lucide-react.
// id با type یکی است تا setTool(id) مستقیم کار کند.
export const EXT_TOOLS = [
  // خطوط
  { id: 'extline', label: 'خط امتداد‌یافته', icon: 'Slash', points: 2 },
  { id: 'infoline', label: 'خط اطلاعاتی (Δقیمت/درصد/بار/زاویه)', icon: 'Ruler', points: 2 },
  { id: 'angle', label: 'زاویه‌ی روند', icon: 'TrendingUp', points: 2 },
  { id: 'hray', label: 'پرتو افقی', icon: 'ArrowUpRight', points: 2 },
  { id: 'crossline', label: 'خط صلیبی', icon: 'Crosshair', points: 2 },
  // کانال‌ها و چنگال‌ها
  { id: 'regchannel', label: 'کانال رگرسیون', icon: 'Move', points: 2 },
  { id: 'flatchannel', label: 'کانال سقف/کف صاف (۳نقطه)', icon: 'Move', points: 3 },
  { id: 'disjointchannel', label: 'کانال ناپیوسته (۴نقطه)', icon: 'Move', points: 4 },
  { id: 'schiff', label: 'چنگال شیف (۳نقطه)', icon: 'GitBranch', points: 3 },
  { id: 'modschiff', label: 'چنگال شیف اصلاح‌شده (۳نقطه)', icon: 'GitBranch', points: 3 },
  { id: 'insidepitchfork', label: 'چنگال داخلی (۳نقطه)', icon: 'GitBranch', points: 3 },
  { id: 'pitchfan', label: 'پیچ‌فن فیبوناچی (۳نقطه)', icon: 'GitBranch', points: 3 },
  // فیبوناچی
  { id: 'fib3', label: 'فیبوی گسترشی ۳نقطه (A→B→C)', icon: 'Spline', points: 3 },
  { id: 'fibfan', label: 'بادبزن فیبوناچی', icon: 'GitBranch', points: 2 },
  { id: 'fibtime', label: 'ناحیه‌ی زمانی فیبوناچی', icon: 'Slash', points: 2 },
  { id: 'fibtimeext', label: 'فیبوی زمانی روندی (۳نقطه)', icon: 'Slash', points: 3 },
  { id: 'fibchannel', label: 'کانال فیبوناچی (۳نقطه)', icon: 'Move', points: 3 },
  { id: 'fibcircles', label: 'دایره‌های فیبوناچی', icon: 'Crosshair', points: 2 },
  { id: 'fibarcs', label: 'کمان‌های فیبوناچی', icon: 'Spline', points: 2 },
  { id: 'fibspiral', label: 'مارپیچ فیبوناچی', icon: 'Spline', points: 2 },
  { id: 'fibwedge', label: 'گُوه‌ی فیبوناچی (۳نقطه)', icon: 'Spline', points: 3 },
  // گان
  { id: 'gannbox', label: 'جعبه‌ی گان', icon: 'Square', points: 2 },
  { id: 'gannfan', label: 'بادبزن گان', icon: 'GitBranch', points: 2 },
  { id: 'gannsquare', label: 'مربع گان', icon: 'Square', points: 2 },
  { id: 'gannfixed', label: 'مربع ثابت گان', icon: 'Square', points: 2 },
  // الگوها
  { id: 'xabcd', label: 'الگوی XABCD (۵نقطه)', icon: 'Spline', points: 5 },
  { id: 'abcd', label: 'الگوی ABCD (۴نقطه)', icon: 'Spline', points: 4 },
  { id: 'cypher', label: 'الگوی سایفر (۵نقطه)', icon: 'Spline', points: 5 },
  { id: 'tripattern', label: 'مثلث الگو (۳نقطه)', icon: 'TrendingUp', points: 3 },
  { id: 'hns', label: 'سر و شانه (۵نقطه)', icon: 'Activity', points: 5 },
  { id: 'ell_impulse', label: 'موج ایمپالس الیوت (۶نقطه)', icon: 'Activity', points: 6 },
  { id: 'ell_abc', label: 'موج اصلاحی الیوت (۴نقطه)', icon: 'Activity', points: 4 },
  { id: 'ell_triangle', label: 'مثلث الیوت (ABCDE، ۶نقطه)', icon: 'Activity', points: 6 },
  { id: 'ell_wxy', label: 'ترکیب دوگانه‌ی الیوت (WXY، ۴نقطه)', icon: 'Activity', points: 4 },
  { id: 'ell_wxyxz', label: 'ترکیب سه‌گانه‌ی الیوت (WXYXZ، ۶نقطه)', icon: 'Activity', points: 6 },
  { id: 'threedrives', label: 'الگوی سه‌حرکت (۷نقطه)', icon: 'Spline', points: 7 },
  { id: 'cyclic', label: 'خطوط دوره‌ای', icon: 'Slash', points: 2 },
  { id: 'timecycles', label: 'چرخه‌های زمانی', icon: 'Crosshair', points: 2 },
  { id: 'sine', label: 'خط سینوسی', icon: 'Activity', points: 2 },
  // پروجکشن و اندازه‌گیری
  { id: 'pricerange', label: 'بازه‌ی قیمت', icon: 'Ruler', points: 2 },
  { id: 'daterange', label: 'بازه‌ی زمان', icon: 'Ruler', points: 2 },
  { id: 'dprange', label: 'بازه‌ی قیمت و زمان', icon: 'Square', points: 2 },
  { id: 'forecast', label: 'پیش‌بینی', icon: 'TrendingUp', points: 2 },
  { id: 'projection', label: 'پروجکشن (۳نقطه)', icon: 'TrendingUp', points: 3 },
  { id: 'ruler', label: 'خط‌کش (اندازه‌گیری سریع)', icon: 'Ruler', points: 2 },
  // اشکال
  { id: 'circle', label: 'دایره', icon: 'Crosshair', points: 2 },
  { id: 'ellipse', label: 'بیضی', icon: 'Crosshair', points: 2 },
  { id: 'triangle', label: 'مثلث', icon: 'TrendingUp', points: 3 },
  { id: 'rotrect', label: 'مستطیل چرخیده (۳نقطه)', icon: 'Square', points: 3 },
  { id: 'arrow', label: 'پیکان', icon: 'ArrowUpRight', points: 2 },
  { id: 'brush', label: 'قلم‌موی آزاد', icon: 'Pencil', points: -1 },
  { id: 'highlighter', label: 'های‌لایتر', icon: 'Pencil', points: -1 },
  // یادداشت‌ها
  { id: 'callout', label: 'کال‌اوت (حباب + خط راهنما)', icon: 'Type', points: 2 },
  { id: 'pricelabel', label: 'برچسب قیمت', icon: 'Type', points: 2 },
  { id: 'note', label: 'یادداشت', icon: 'Type', points: 2 },
  { id: 'arrowdir', label: 'پیکان جهت‌دار', icon: 'ArrowUpRight', points: 2 },
];
