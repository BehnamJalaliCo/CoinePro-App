// بازارنما — هستهٔ تولیدِ تصویرِ چارت (#5). همهٔ گزینه‌ها (دانلود/کپی/لینک) از یک مسیرِ واحد.
// منطقِ فعلیِ composite کردنِ overlay (سشن‌ها/ترسیم‌ها) حفظ شده؛ واترمارکِ برند داخلِ
// خودِ فایلِ خروجی رسم می‌شود (نه‌فقط روی DOM)، + نوارِ caption اختیاری + رزولوشنِ رتینا.

let _wmImg = null; // کشِ لوگو برای واترمارک
function loadLogo(src) {
  if (_wmImg && _wmImg.__src === src) return Promise.resolve(_wmImg);
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => { img.__src = src; _wmImg = img; resolve(img); };
    img.onerror = () => resolve(null);
    img.src = src; // import محلی → بدونِ CORS taint
  });
}

// نقطهٔ شروعِ واترمارک/نوار بر اساسِ موقعیت
function wmPos(position, W, H, w, h, pad) {
  switch (position) {
    case 'bottom-right': return [W - w - pad, H - h - pad];
    case 'top-left': return [pad, pad];
    case 'top-right': return [W - w - pad, pad];
    case 'center': return [(W - w) / 2, (H - h) / 2];
    case 'bottom-left':
    default: return [pad, H - h - pad];
  }
}

/**
 * captureChart — یک canvasِ نهایی (آف‌اسکرین) می‌سازد.
 * @param {object} o
 *   chart        instanceِ lightweight-charts (takeScreenshot)
 *   overlay      canvasِ overlay (سشن‌ها/ترسیم‌ها) — اختیاری
 *   bg           رنگِ پس‌زمینه (برای flatten در JPG)
 *   scale        1|2 (پیش‌فرض 2)
 *   format       'png'|'jpg'
 *   watermark    { enabled, src, position, opacity, size, text } | null
 *   caption      { symbol, tf, price, bg, text, sub } | null
 * @returns {Promise<HTMLCanvasElement|null>}
 */
export async function captureChart(o) {
  const { chart, overlay, bg = '#131722', scale = 2, watermark = null, caption = null } = o || {};
  if (!chart || !chart.takeScreenshot) return null;
  const base = chart.takeScreenshot();
  if (!base) return null;
  const bw = base.width, bh = base.height;
  const capH = caption ? 40 : 0;

  const cv = document.createElement('canvas');
  cv.width = Math.round(bw * scale);
  cv.height = Math.round((bh + capH) * scale);
  const ctx = cv.getContext('2d');
  ctx.scale(scale, scale);
  // پس‌زمینه (برای JPG که شفافیت ندارد)
  ctx.fillStyle = bg; ctx.fillRect(0, 0, bw, bh + capH);
  // ۱) چارت  ۲) overlay (سشن‌ها/ترسیم‌ها) — همان منطقِ فعلی
  try { ctx.drawImage(base, 0, 0); } catch (e) { /* */ }
  if (overlay) { try { ctx.drawImage(overlay, 0, 0); } catch (e) { /* */ } }

  // ۳) نوارِ caption (نماد/تایم‌فریم/قیمت/تاریخ)
  if (caption) {
    const y0 = bh;
    ctx.fillStyle = caption.bg || bg; ctx.fillRect(0, y0, bw, capH);
    ctx.fillStyle = caption.text || '#d1d4dc';
    ctx.font = '600 13px IRANYekanX, Ravagh, AnjomanMax, Vazirmatn, sans-serif';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    const left = `${caption.symbol || ''}  ·  ${caption.tf || ''}` + (caption.price ? `  ·  ${caption.price}` : '');
    ctx.fillText(left, 12, y0 + capH / 2);
    ctx.textAlign = 'right';
    ctx.fillStyle = caption.sub || '#787b86';
    ctx.fillText(caption.subText || 'bazaarnama', bw - 12, y0 + capH / 2);
  }

  // ۴) واترمارکِ برند (آخر از همه تا رو باشد)
  if (watermark && watermark.enabled !== false && watermark.src) {
    const img = await loadLogo(watermark.src);
    const pad = 14;
    const op = watermark.opacity != null ? watermark.opacity : 0.5;
    const wantText = watermark.kind === 'text' || watermark.kind === 'logo+text' || (!img && watermark.text);
    if (img) {
      const h = watermark.size || 40;
      const w = h * (img.width / img.height || 1);
      const [x, y] = wmPos(watermark.position || 'bottom-left', bw, bh, w, h, pad);
      ctx.save();
      ctx.globalAlpha = op;
      // در تمِ روشن لوگو معکوس شود تا خوانا بماند (هم‌تراز با Watermark فعلیِ DOM)
      if (watermark.invert) { ctx.filter = 'invert(1)'; }
      try { ctx.drawImage(img, x, y, w, h); } catch (e) { /* */ }
      ctx.restore();
      if (wantText && watermark.text) {
        ctx.save(); ctx.globalAlpha = Math.min(1, op + 0.15);
        ctx.fillStyle = watermark.invert ? '#000' : '#fff';
        ctx.font = '700 13px IRANYekanX, Ravagh, Vazirmatn, sans-serif'; ctx.textBaseline = 'top'; ctx.textAlign = 'left';
        ctx.fillText(watermark.text, x, y + h + 2);
        ctx.restore();
      }
    } else if (wantText && watermark.text) {
      const [x, y] = wmPos(watermark.position || 'bottom-left', bw, bh, 120, 18, pad);
      ctx.save(); ctx.globalAlpha = op;
      ctx.fillStyle = watermark.invert ? '#000' : '#fff';
      ctx.font = '700 15px IRANYekanX, Ravagh, Vazirmatn, sans-serif'; ctx.textBaseline = 'top'; ctx.textAlign = 'left';
      ctx.fillText(watermark.text, x, y);
      ctx.restore();
    }
  }
  return cv;
}

// canvas → Blob
export function canvasToBlob(cv, format = 'png', quality = 0.92) {
  return new Promise((resolve) => {
    const mime = format === 'jpg' ? 'image/jpeg' : 'image/png';
    try { cv.toBlob((b) => resolve(b), mime, format === 'jpg' ? quality : undefined); }
    catch (e) { resolve(null); }
  });
}

// دانلودِ یک blob
export function downloadBlob(blob, filename) {
  if (!blob) return;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

// کپیِ تصویر به کلیپ‌بورد (ClipboardItem). برمی‌گرداند true/false.
export async function copyBlobToClipboard(blob) {
  if (!blob) return false;
  try {
    if (navigator.clipboard && typeof window.ClipboardItem !== 'undefined') {
      await navigator.clipboard.write([new window.ClipboardItem({ [blob.type || 'image/png']: blob })]);
      return true;
    }
  } catch (e) { /* */ }
  return false;
}

// کپیِ متن (لینک) به کلیپ‌بورد
export async function copyText(text) {
  try { if (navigator.clipboard && navigator.clipboard.writeText) { await navigator.clipboard.writeText(text); return true; } } catch (e) { /* */ }
  try {
    const ta = document.createElement('textarea'); ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta); return true;
  } catch (e) { return false; }
}

const SCREENSHOT_OPTS_KEY = 'bn_screenshot_opts';
export function loadScreenshotOpts() {
  try { return JSON.parse(localStorage.getItem(SCREENSHOT_OPTS_KEY) || '{}') || {}; } catch (e) { return {}; }
}
export function saveScreenshotOpts(patch) {
  try { localStorage.setItem(SCREENSHOT_OPTS_KEY, JSON.stringify({ ...loadScreenshotOpts(), ...patch })); } catch (e) { /* */ }
}
