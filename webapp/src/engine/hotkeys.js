// ─────────────────────────────────────────────────────────────────────────────
// hotkeys.js — لایهٔ میان‌برهای صفحه‌کلید برای Pro-Chart (فصل ۱۰.۷ از اسپک)
//
// یک رجیستریِ اعلانی (declarative) از میان‌بُرهای سبکِ TradingView به‌علاوهٔ
// attachHotkeys(handlers) که یک شنوندهٔ سراسریِ keydown نصب می‌کند و هر کلید را
// به callback متناظرش وصل می‌کند. خروجی، یک تابعِ detach برای پاک‌سازی است.
//
// طراحی:
//  • هیچ وابستگی به React یا DOMِ خاص — صرفاً یک شنوندهٔ keydown روی یک root.
//  • وقتی فوکوس روی input/textarea/select یا contentEditable باشد، میان‌بُرها
//    خاموش می‌شوند (به‌جز چند کلیدِ ترکیبیِ امن مثلِ Ctrl+S که باید همه‌جا کار کنند).
//  • preventDefault فقط برای کلیدهایی که خودمان مالکِ آن‌ها هستیم.
//  • SHORTCUTS برای ساختِ دیالوگِ راهنما صادر می‌شود (گروه‌بندی‌شده، فارسی).
// ─────────────────────────────────────────────────────────────────────────────

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent || '');

// آیا فوکوس روی یک فیلدِ متنی است؟ (برای خاموش‌کردنِ میان‌بُرهای تک‌حرفی)
function isTypingTarget(el) {
  if (!el) return false;
  const tag = (el.tagName || '').toUpperCase();
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (el.isContentEditable) return true;
  // کدادیتورِ نمااسکریپت یا هر ناحیهٔ ویرایش که خودش را علامت‌گذاری کرده
  if (el.closest && el.closest('[data-bn-editor], .cm-editor, [contenteditable="true"]')) return true;
  return false;
}

// Space روی کنترلِ فوکوس‌پذیر متعلق به همان کنترل است (فعال‌سازی native)، نه
// میان‌بُرِ پخش بازپخش. target ممکن است SVG/Span داخل دکمه باشد، پس closest لازم است.
function ownsNativeSpaceActivation(el, event) {
  if (!el || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return false;
  if (!(event.key === ' ' || event.code === 'Space')) return false;
  return Boolean(el.closest && el.closest('button, [role="button"], summary, a[href]'));
}

// نمایشِ خوانا برای دیالوگِ راهنما (مثلِ «Alt+T» یا «⌥T»)
function fmtKey(combo) {
  if (isMac) {
    return combo
      .replace(/Ctrl\+/g, '⌘')
      .replace(/Alt\+/g, '⌥')
      .replace(/Shift\+/g, '⇧')
      .replace(/Meta\+/g, '⌘');
  }
  return combo;
}

// ─────────────────────────────────────────────────────────────────────────────
// جدولِ میان‌بُرها. هر آیتم:
//   id        — کلیدِ یکتا (همان نامِ handler)
//   combo     — رشتهٔ نمایشی (برای راهنما)
//   group     — گروه‌بندیِ فارسی برای دیالوگ
//   label     — توضیحِ فارسی
//   match     — تابعِ تطبیق با رویدادِ keydown
//   prevent   — آیا باید preventDefault شود (پیش‌فرض true)
//   allowInInput — آیا حتی در فیلدِ متنی هم فعال باشد (مثلِ Ctrl+S)
// ─────────────────────────────────────────────────────────────────────────────

// کمک‌کننده‌های تطبیق
const k = (e) => (e.key || '').toLowerCase();
const noMods = (e) => !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey;
const altOnly = (e) => e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey;
const ctrlOnly = (e) => (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey;
const ctrlAlt = (e) => (e.ctrlKey || e.metaKey) && e.altKey && !e.shiftKey;
const shiftOnly = (e) => e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey;

export const SHORTCUTS = [
  // ── ابزارهای ترسیم ──
  { id: 'cursor',   group: 'ابزارها', combo: 'Esc',     label: 'نشانگر (لغوِ ترسیم)', allowInInput: true, match: (e) => k(e) === 'escape', prevent: false },
  { id: 'trend',    group: 'ابزارها', combo: 'Alt+T',   label: 'خطِ روند',            match: (e) => altOnly(e) && k(e) === 't' },
  { id: 'hline',    group: 'ابزارها', combo: 'Alt+H',   label: 'خطِ افقی',            match: (e) => altOnly(e) && k(e) === 'h' },
  { id: 'vline',    group: 'ابزارها', combo: 'Alt+V',   label: 'خطِ عمودی',           match: (e) => altOnly(e) && k(e) === 'v' },
  { id: 'ray',      group: 'ابزارها', combo: 'Alt+R',   label: 'پرتو (Ray)',          match: (e) => altOnly(e) && k(e) === 'r' },
  { id: 'rect',     group: 'ابزارها', combo: 'Alt+E',   label: 'مستطیل',              match: (e) => altOnly(e) && k(e) === 'e' },
  { id: 'fib',      group: 'ابزارها', combo: 'Alt+F',   label: 'فیبوناچی',            match: (e) => altOnly(e) && k(e) === 'f' },
  { id: 'text',     group: 'ابزارها', combo: 'Alt+X',   label: 'متن',                 match: (e) => altOnly(e) && k(e) === 'x' },
  // Alt+P/Alt+L طبقِ TradingView به «مقیاسِ درصدی/لگاریتمی» اختصاص دارند (نه ابزارِ ترسیم) — کانال/لانگ‌شورت از ریلِ ابزار در دسترس‌اند. (#۴۴۴)
  { id: 'magnet',   group: 'ابزارها', combo: 'Ctrl+Alt+M', label: 'آهنربا (Magnet)',   match: (e) => ctrlAlt(e) && k(e) === 'm' },
  { id: 'stayDraw', group: 'ابزارها', combo: 'Ctrl+Alt+D', label: 'ماندن در حالتِ ترسیم', match: (e) => ctrlAlt(e) && k(e) === 'd' },

  // ── ویرایش ──
  { id: 'undo',      group: 'ویرایش', combo: 'Ctrl+Z', label: 'واگرد',  allowInInput: false, match: (e) => ctrlOnly(e) && k(e) === 'z' },
  { id: 'redo',      group: 'ویرایش', combo: 'Ctrl+Y', label: 'ازنو',   allowInInput: false, match: (e) => (ctrlOnly(e) && k(e) === 'y') || ((e.ctrlKey || e.metaKey) && e.shiftKey && !e.altKey && k(e) === 'z') },
  { id: 'deleteSel', group: 'ویرایش', combo: 'Delete', label: 'حذفِ آبجکتِ انتخابی', match: (e) => noMods(e) && (k(e) === 'delete' || k(e) === 'backspace') },
  { id: 'cloneSel',  group: 'ویرایش', combo: 'Ctrl+D', label: 'تکثیرِ آبجکتِ انتخابی', match: (e) => ctrlOnly(e) && k(e) === 'd' },
  { id: 'copySel',   group: 'ویرایش', combo: 'Ctrl+C', label: 'کپیِ آبجکتِ انتخابی', match: (e) => ctrlOnly(e) && k(e) === 'c' },
  { id: 'pasteSel',  group: 'ویرایش', combo: 'Ctrl+V', label: 'چسباندنِ آبجکت', match: (e) => ctrlOnly(e) && k(e) === 'v' },
  { id: 'selectAll', group: 'ویرایش', combo: 'Ctrl+A', label: 'انتخابِ همهٔ ترسیم‌ها', match: (e) => ctrlOnly(e) && k(e) === 'a' },
  { id: 'removeAll', group: 'ویرایش', combo: 'Ctrl+Alt+Backspace', label: 'حذفِ همهٔ ترسیم‌ها', match: (e) => ctrlAlt(e) && k(e) === 'backspace' },
  { id: 'clearChart',group: 'ویرایش', combo: 'Ctrl+Alt+R', label: 'پاکِ صفحه (خروجی‌ها)', match: (e) => ctrlAlt(e) && k(e) === 'r' },
  { id: 'lockSel',   group: 'ویرایش', combo: 'Ctrl+L', label: 'قفل/بازکردنِ انتخابی', match: (e) => ctrlOnly(e) && k(e) === 'l' },
  { id: 'hideAll',   group: 'ویرایش', combo: 'Ctrl+Alt+H', label: 'نمایش/پنهانِ همهٔ ترسیم‌ها', match: (e) => ctrlAlt(e) && k(e) === 'h' },

  // ── تایم‌فریم ──
  { id: 'tfNext', group: 'تایم‌فریم', combo: ',', label: 'تایم‌فریمِ بعدی', match: (e) => noMods(e) && (e.key === ',' || k(e) === 'comma') },
  { id: 'tfPrev', group: 'تایم‌فریم', combo: '.', label: 'تایم‌فریمِ قبلی', match: (e) => noMods(e) && (e.key === '.' || k(e) === 'period') },
  { id: 'tf1',    group: 'تایم‌فریم', combo: 'Shift+1', label: '۵ دقیقه (M5)',  match: (e) => shiftOnly(e) && (e.key === '!' || e.code === 'Digit1') },
  { id: 'tf2',    group: 'تایم‌فریم', combo: 'Shift+2', label: '۱۵ دقیقه (M15)', match: (e) => shiftOnly(e) && (e.key === '@' || e.code === 'Digit2') },
  { id: 'tf3',    group: 'تایم‌فریم', combo: 'Shift+3', label: '۱ ساعت (H1)',   match: (e) => shiftOnly(e) && (e.key === '#' || e.code === 'Digit3') },
  { id: 'tf4',    group: 'تایم‌فریم', combo: 'Shift+4', label: '۴ ساعت (H4)',   match: (e) => shiftOnly(e) && (e.key === '$' || e.code === 'Digit4') },
  { id: 'tf5',    group: 'تایم‌فریم', combo: 'Shift+5', label: 'روزانه (D1)',   match: (e) => shiftOnly(e) && (e.key === '%' || e.code === 'Digit5') },

  // ── نوعِ چارت ──
  { id: 'typeCandles', group: 'نوعِ چارت', combo: 'Alt+1', label: 'کندل',        match: (e) => altOnly(e) && (e.code === 'Digit1' || e.key === '1') },
  { id: 'typeBars',    group: 'نوعِ چارت', combo: 'Alt+2', label: 'میله (OHLC)', match: (e) => altOnly(e) && (e.code === 'Digit2' || e.key === '2') },
  { id: 'typeLine',    group: 'نوعِ چارت', combo: 'Alt+3', label: 'خطی',         match: (e) => altOnly(e) && (e.code === 'Digit3' || e.key === '3') },
  { id: 'typeArea',    group: 'نوعِ چارت', combo: 'Alt+4', label: 'ناحیه',       match: (e) => altOnly(e) && (e.code === 'Digit4' || e.key === '4') },
  { id: 'typeHeikin',  group: 'نوعِ چارت', combo: 'Alt+5', label: 'هایکین‌آشی',  match: (e) => altOnly(e) && (e.code === 'Digit5' || e.key === '5') },

  // ── ناوبری / زوم ──
  { id: 'scrollLeft',  group: 'ناوبری', combo: '←', label: 'حرکت ترسیم / اسکرول به چپ',   match: (e) => noMods(e) && k(e) === 'arrowleft' },
  { id: 'scrollRight', group: 'ناوبری', combo: '→', label: 'حرکت ترسیم / اسکرول به راست', match: (e) => noMods(e) && k(e) === 'arrowright' },
  { id: 'zoomIn',  group: 'ناوبری', combo: '↑ / +', label: 'حرکت ترسیم / بزرگ‌نمایی', match: (e) => noMods(e) && (k(e) === 'arrowup' || e.key === '+' || e.key === '=') },
  { id: 'zoomOut', group: 'ناوبری', combo: '↓ / −', label: 'حرکت ترسیم / کوچک‌نمایی', match: (e) => noMods(e) && (k(e) === 'arrowdown' || e.key === '-' || e.key === '_') },
  { id: 'nudgeUpFast', group: 'ناوبری', combo: 'Shift+↑', label: 'حرکت سریع ترسیم به بالا', match: (e) => shiftOnly(e) && k(e) === 'arrowup' },
  { id: 'nudgeDownFast', group: 'ناوبری', combo: 'Shift+↓', label: 'حرکت سریع ترسیم به پایین', match: (e) => shiftOnly(e) && k(e) === 'arrowdown' },
  { id: 'scrollEnd',  group: 'ناوبری', combo: 'End',  label: 'پرش به جدیدترین', match: (e) => noMods(e) && k(e) === 'end' },
  { id: 'scrollHome', group: 'ناوبری', combo: 'Home', label: 'پرش به قدیمی‌ترین', match: (e) => noMods(e) && k(e) === 'home' },
  { id: 'gotoDate',   group: 'ناوبری', combo: 'Alt+G', label: 'پرش به تاریخ', match: (e) => altOnly(e) && k(e) === 'g' },
  { id: 'fit',        group: 'ناوبری', combo: 'Ctrl+Alt+0', label: 'هم‌اندازه‌سازیِ چارت', match: (e) => ctrlAlt(e) && (e.code === 'Digit0' || e.key === '0') },
  { id: 'resetScale', group: 'ناوبری', combo: 'Ctrl+Alt+S', label: 'بازنشانیِ مقیاسِ قیمت', match: (e) => ctrlAlt(e) && k(e) === 's' },
  { id: 'invertScale', group: 'ناوبری', combo: 'Alt+I', label: 'وارونه‌سازیِ محورِ قیمت', match: (e) => altOnly(e) && k(e) === 'i' },
  { id: 'percentScale', group: 'ناوبری', combo: 'Alt+P', label: 'مقیاسِ درصدی', match: (e) => altOnly(e) && k(e) === 'p' },
  { id: 'logScale', group: 'ناوبری', combo: 'Alt+L', label: 'مقیاسِ لگاریتمی', match: (e) => altOnly(e) && k(e) === 'l' },

  // ── پنل‌ها / نما ──
  { id: 'toggleRight', group: 'نما', combo: 'Ctrl+R', label: 'نمایش/پنهانِ نوارِ کناری', match: (e) => ctrlOnly(e) && k(e) === 'r' },
  // «تمام‌صفحه» با میان‌بُرِ ترکیبی تا با «تایپِ حرف → جستجوی نماد»ِ TV تداخل نکند (بارهِ F آزاد شد).
  { id: 'fullscreen',  group: 'نما', combo: 'Ctrl+Alt+F', label: 'تمام‌صفحه', match: (e) => ctrlAlt(e) && k(e) === 'f' },
  { id: 'toggleTheme', group: 'نما', combo: 'Ctrl+Alt+T', label: 'تمِ تیره/روشن', match: (e) => ctrlAlt(e) && k(e) === 't' },
  { id: 'indicators',  group: 'نما', combo: '/', label: 'بازکردنِ اندیکاتورها', match: (e) => noMods(e) && e.key === '/' },
  { id: 'symbolSearch',group: 'نما', combo: 'Ctrl+K', label: 'جست‌وجوی نماد', allowInInput: false, match: (e) => ctrlOnly(e) && k(e) === 'k' },
  { id: 'settings',    group: 'نما', combo: 'Ctrl+,', label: 'تنظیماتِ چارت', match: (e) => ctrlOnly(e) && (e.key === ',' || k(e) === 'comma') },
  { id: 'help',        group: 'نما', combo: '?', label: 'راهنمای میان‌بُرها', match: (e) => e.shiftKey && e.key === '?' },

  // ── چیدمان / چند-چارت ──
  { id: 'saveLayout', group: 'چیدمان', combo: 'Ctrl+S', label: 'ذخیرهٔ چیدمان', allowInInput: true, match: (e) => ctrlOnly(e) && k(e) === 's' },
  { id: 'cycleGrid',  group: 'چیدمان', combo: 'Ctrl+Alt+G', label: 'تعویضِ چیدمانِ شبکه', match: (e) => ctrlAlt(e) && k(e) === 'g' },
  // «سلولِ بعدی (Tab)» حذف شد: handler نداشت (attachHotkeys skipش می‌کرد) و در راهنما میان‌بُرِ کاذب بود؛ Tab رزروِ مرورگر است و TV هم برای سلول‌ها کلیک می‌گیرد نه Tab. #321

  // ── ترید / آلارم ──
  { id: 'newAlert', group: 'ترید', combo: 'Alt+A', label: 'آلارمِ جدید روی قیمتِ نشانگر', match: (e) => altOnly(e) && k(e) === 'a' },
  { id: 'buy',  group: 'ترید', combo: 'Shift+B', label: 'خریدِ سریع', match: (e) => shiftOnly(e) && k(e) === 'b' },
  { id: 'sell', group: 'ترید', combo: 'Shift+S', label: 'فروشِ سریع', match: (e) => shiftOnly(e) && k(e) === 's' },

  // ── بازپخش (Bar Replay) ──
  { id: 'replayToggle', group: 'بازپخش', combo: 'Ctrl+Alt+P', label: 'ورود/خروجِ بازپخش', match: (e) => ctrlAlt(e) && k(e) === 'p' },
  { id: 'replayPlay',   group: 'بازپخش', combo: 'Space', label: 'پخش/مکث', match: (e) => noMods(e) && (e.key === ' ' || e.code === 'Space') },
  { id: 'replayStep',    group: 'بازپخش', combo: 'Shift+→', label: 'حرکت سریع ترسیم / گام بعدی بازپخش', match: (e) => shiftOnly(e) && k(e) === 'arrowright' },
  { id: 'replayStepBack', group: 'بازپخش', combo: 'Shift+←', label: 'حرکت سریع ترسیم / گام قبلی بازپخش', match: (e) => shiftOnly(e) && k(e) === 'arrowleft' },

  // ── اسکریپت ──
  // «اسکرین‌شات» با میان‌بُرِ ترکیبی تا با «تایپِ حرف → جستجوی نماد»ِ TV تداخل نکند (بارهِ S آزاد شد؛ دکمهٔ دوربین + منوی راست‌کلیک هم دارد).
  { id: 'screenshot', group: 'دیگر', combo: 'Alt+S', label: 'اسکرین‌شاتِ چارت', match: (e) => altOnly(e) && k(e) === 's' },
  { id: 'saveScript', group: 'دیگر', combo: 'Ctrl+Shift+S', label: 'ذخیرهٔ اسکریپت', allowInInput: true, match: (e) => (e.ctrlKey || e.metaKey) && e.shiftKey && !e.altKey && k(e) === 's' },
].map((s) => ({ prevent: true, allowInInput: false, ...s, combo: fmtKey(s.combo) }));

// گروه‌بندیِ آماده برای دیالوگِ راهنما: [{ group, items: [...] }]
export const SHORTCUT_GROUPS = (() => {
  const order = [];
  const map = {};
  for (const s of SHORTCUTS) {
    if (!map[s.group]) { map[s.group] = []; order.push(s.group); }
    map[s.group].push(s);
  }
  return order.map((g) => ({ group: g, items: map[g] }));
})();

// ─────────────────────────────────────────────────────────────────────────────
// attachHotkeys(handlers, opts) — نصبِ شنوندهٔ سراسری.
//
//   handlers — شیئی از { id: () => void }. هر idِ موجود در SHORTCUTS که handler
//              داشته باشد، اجرا می‌شود؛ بقیه نادیده گرفته می‌شوند.
//   opts.target — عنصرِ هدف برای addEventListener (پیش‌فرض window). برای
//              محدودکردن به ناحیهٔ چارت، rootRef.current را بدهید.
//   opts.enabled — تابع/مقدارِ بولی؛ اگر false شود میان‌بُرها موقتاً خاموش‌اند.
//
// خروجی: تابعِ detach() برای حذفِ شنونده.
// ─────────────────────────────────────────────────────────────────────────────
export function attachHotkeys(handlers, opts = {}) {
  if (typeof window === 'undefined') return () => {};
  const target = opts.target || window;
  const isEnabled = () => {
    const en = opts.enabled;
    if (en == null) return true;
    return typeof en === 'function' ? !!en() : !!en;
  };

  const onKeyDown = (e) => {
    if (!isEnabled()) return;
    const eventTarget = e.target || document.activeElement;
    if (ownsNativeSpaceActivation(eventTarget, e)) return;
    const typing = isTypingTarget(eventTarget);
    for (const s of SHORTCUTS) {
      const fn = handlers[s.id];
      if (!fn) continue;
      // در فیلدِ متنی فقط میان‌بُرهای allowInInput اجرا می‌شوند
      if (typing && !s.allowInInput) continue;
      let hit = false;
      try { hit = s.match(e); } catch (err) { hit = false; }
      if (!hit) continue;
      if (s.prevent !== false) { e.preventDefault(); e.stopPropagation(); }
      try { fn(e); } catch (err) { /* هندلر نباید لیسنر را بشکند */ }
      return; // اولین تطبیق برنده است
    }
  };

  target.addEventListener('keydown', onKeyDown, { capture: false });
  return () => target.removeEventListener('keydown', onKeyDown);
}

export default attachHotkeys;
