// بازارنما — لایه‌ی ترسیم روی چارت (Canvas overlay، مختصات chart-space).
// ابزارها: trend, ray, hline, vline, rect, fib, longshort, text.
// نقاط بر حسب {t (unix), p (price)} ذخیره می‌شوند تا با zoom/pan ثابت بمانند.

// افزونه‌ی فصل ۴ (PRO_CHART_BUILD_SPEC): ۳۹ ابزار ترسیم جدید، drop-in.
import { EXT_NEED, EXT_FREEHAND, EXT_LABELS, EXT_ONE, EXT_POLY, isExt, extDraw, extHit, extHandles, extInit, extMove, extScreen, extMaxPts, normLevels, dirOf, drawTextBlock, hitTextBlock, drawLineText } from './drawtools_ext.js';
import { DrawingHistory } from './drawing_history.js';

// The keys of a drawing that are its look rather than its place: what «Save as default», the
// templates and the settings dialog carry from one drawing to another.
export const STYLE_KEYS = ['color', 'width', 'lineStyle', 'dashed', 'fill', 'fillOpacity', 'fontSize', 'bold', 'italic', 'bg', 'bgOpacity', 'border', 'textColor', 'align', 'valign', 'wrap', 'wrapWidth', 'extendLeft', 'extendRight', 'middle', 'priceLabels', 'stats', 'levels', 'fibBg', 'bands', 'rows', 'va', 'widthPct', 'mode', 'header', 'account', 'risk', 'riskUnit', 'lot', 'upColor', 'downColor', 'dir'];
// Text-bearing drawings: an editor opens when one is placed and on a double click.
export const TEXT_TYPES = ['text', 'anchoredtext', 'note', 'pricenote', 'pin', 'comment', 'callout', 'pricelabel', 'signpost', 'flag', 'table'];

export class DrawingLayer {
  constructor(canvas, chart) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.chart = chart;
    this.series = null;
    this.tool = 'cursor';
    this.color = '#2962FF'; // آبی اشباع/پررنگ برند Pro-Chart (قبلاً #3b82f6 کم‌رنگ‌تر بود)
    this.toolDefaults = {}; // سبک پیش‌فرض هر نوع ابزار (Save as default style TV) — خالی ⇒ no-op؛ فقط با ذخیره‌ی کاربر پر می‌شود
    this.digits = 5; // دقت اعشار قیمت نماد فعال (برای برچسب ابزار اندازه‌گیری) — با setDigits به‌روز می‌شود
    this.drawings = [];
    this.tmp = null;      // در حال ترسیم
    this.dragging = false;
    this.onChange = null;
    this.profile = null;  // Volume Profile buckets
    this.script = null;   // گرافیک نمااسکریپت (fill/bgcolor/barcolor/line/box)
    this.selected = -1;   // ایندکس ترسیم انتخاب‌شده (اصلی)
    this.multiSel = new Set(); // چند-انتخاب (Ctrl+کلیک، سبک TV) — خالی ⇒ رفتار تک‌انتخابی قبلی بدون تغییر
    this.marquee = null;  // مستطیل انتخاب کششی (Marquee TV) — {x0,y0,x1,y1} حین کشیدن
    this.candles = null;  // برای مگنت (snap به OHLC)
    this.magnet = false;
    this.magnetMode = 'strong'; // 'strong' = همیشه snap، 'weak' = فقط اگر نزدیک OHLC باشد (سبک TV)
    this.pending = null;  // ابزار چندنقطه‌ای در حال ساخت
    this.order = null;    // {side, entry, sl, tp} — خطوط سفارش قابل‌درگ
    this.onOrder = null;  // callback هنگام درگ خطوط سفارش
    this.dragOrder = null;
    // ── ویرایش آبجکت (فاز ۳) ──
    this.dragHandle = null;   // {idx, hi} — درگ یک handle
    this.dragMove = null;     // {idx, last:{t,p}} — جابه‌جایی کل آبجکت
    this.hover = -1;          // آبجکت زیر ماوس (برای cursor)
    this.onSelect = null;     // callback هنگام تغییر انتخاب
    this.stayInMode = false;  // ماندن در حالت ترسیم پس از کشیدن
    this.lang = 'fa';         // labels drawn on the canvas follow the reader's language
    this.emoji = '🙂';        // the glyph the Icons tool places next
    this.eraserCursor = 'pointer';
    this.zoomBox = null;      // the Zoom-in tool's rectangle while it is dragged
    this.onZoomBox = null;    // (box) the rectangle the reader zoomed to
    this.onCreated = null;    // (index) a drawing was just placed
    this.tfN = null;          // the interval's size within its class (15 for 15 minutes)
    this._history = new DrawingHistory(100);
    this._undo = this._history.past; // alias تشخیصی سازگار با نسخه‌ی قبل
    this._redo = this._history.future;
    this._bind();
  }

  // برچسب فارسی نوع ترسیم (برای Object Tree)
  static label(type) {
    return ({ trend: 'خط روند', ray: 'پرتو', hline: 'خط افقی', vline: 'خط عمودی', rect: 'مستطیل', fib: 'فیبوناچی', fibext: 'فیبوی گسترشی', longshort: 'موقعیت لانگ', short: 'موقعیت شورت', text: 'متن', channel: 'کانال', pitchfork: 'چنگال' }[type] || EXT_LABELS[type] || type);
  }
  // snapshot عمیق برای undo/redo
  _snapshot() { return JSON.parse(JSON.stringify(this.drawings)); }
  _pushUndo() { this._history.record(this.drawings); }
  undo() { const next = this._history.undo(this.drawings); if (!next) return; this.drawings = next; this.selected = -1; this.multiSel = new Set(); this._changed(); this.onSelect && this.onSelect(-1); }
  redo() { const next = this._history.redo(this.drawings); if (!next) return; this.drawings = next; this.selected = -1; this.multiSel = new Set(); this._changed(); this.onSelect && this.onSelect(-1); }
  canUndo() { return this._history.canUndo(); }
  canRedo() { return this._history.canRedo(); }
  // A new symbol starts a new history: undo never brings back another symbol's drawings.
  resetHistory() { this._history = new DrawingHistory(100); this._undo = this._history.past; this._redo = this._history.future; }

  // ── Public surface for the host (hit-test for the right-click menu, selection, clipboard) ──
  /** The drawing under (x, y) in canvas pixels, topmost first, or -1. */
  hitTest(x, y) { return this._hit(x, y); }
  /** Every selected drawing, in stacking order. */
  selectedIndices() {
    const s = new Set(this.multiSel); if (this.selected >= 0) s.add(this.selected);
    return [...s].filter((i) => i >= 0 && i < this.drawings.length).sort((a, b) => a - b);
  }
  /** Adds a drawing to the selection or takes it out (Ctrl+click in the object tree). */
  toggleSelect(i) {
    if (i < 0 || i >= this.drawings.length) return;
    const s = new Set(this.selectedIndices());
    if (s.has(i)) s.delete(i); else s.add(i);
    const list = [...s];
    if (list.length <= 1) { this.multiSel = new Set(); this.selected = list.length ? list[0] : -1; }
    else { this.multiSel = s; this.selected = s.has(i) ? i : list[0]; }
    this.render(); this.onSelect && this.onSelect(this.selected);
  }
  /** Deep copies of the selected drawings (Ctrl+C). */
  copySelected() { return this.selectedIndices().map((i) => JSON.parse(JSON.stringify(this.drawings[i]))); }
  /** Pastes copies a little down and to the right of the originals, selected; one undo step. */
  pasteMany(list) {
    const src = (Array.isArray(list) ? list : [list]).filter((o) => o && o.type);
    if (!src.length) return [];
    this._pushUndo();
    const made = [];
    for (const obj of src) {
      const c = JSON.parse(JSON.stringify(obj)); c.locked = false; c.visible = true;
      this._offset(c, 14);
      this.drawings.push(c); made.push(this.drawings.length - 1);
    }
    this.multiSel = made.length > 1 ? new Set(made) : new Set();
    this.selected = made[made.length - 1];
    this._changed(); this.onSelect && this.onSelect(this.selected);
    return made;
  }
  // Shifts a copy by `px` pixels down and right (in time and price, or on screen for anchored text).
  _offset(c, px) {
    if (extScreen(c.type)) { const W = this.canvas.width || 1, H = this.canvas.height || 1; c.ax = Math.min(0.98, (c.ax || 0.5) + px / W); c.ay = Math.min(0.98, (c.ay || 0.5) + px / H); return; }
    const h = this._handlePoints(c).find((p) => p.x != null && p.y != null);
    if (h) { const ot = this._t(h.x), op = this._p(h.y), nt = this._t(h.x + px), np = this._p(h.y + px); if (ot != null && op != null && nt != null && np != null) this._moveBy(c, nt - ot, np - op); }
  }
  /** The same style on several drawings at once, one undo step (a group's lock or visibility). */
  setStyleMany(idxs, st) {
    const list = (idxs || []).filter((i) => this.drawings[i]);
    if (!list.length) return;
    this._pushUndo();
    list.forEach((i) => Object.assign(this.drawings[i], st));
    this._changed();
  }
  /**
   * Removes a drawing that was placed a moment ago and left empty (a text whose editor was
   * cancelled) together with the history step its placing recorded, so undo has nothing to bring back.
   */
  abandon(i) {
    if (i < 0 || i >= this.drawings.length) return;
    this._history.past.pop();
    this.drawings.splice(i, 1);
    this.multiSel = new Set();
    if (this.selected === i) this.selected = -1; else if (this.selected > i) this.selected--;
    this._changed(); this.onSelect && this.onSelect(this.selected);
  }
  /** Completes a drawing placed a moment ago (its text) without a history step of its own. */
  amend(i, st) { const d = this.drawings[i]; if (!d) return; Object.assign(d, st); this._changed(); }
  // ── Live edits (the settings dialog): changes show at once, OK records one undo step, Cancel restores ──
  beginEdit() { return this._snapshot(); }
  liveStyle(i, st) { const d = this.drawings[i]; if (!d) return; Object.assign(d, st); this.render(); }
  endEdit(before, commit) {
    if (!before) return;
    if (commit) { if (JSON.stringify(before) !== JSON.stringify(this.drawings)) { this._history.record(before); } this._changed(); }
    else { this.drawings = before; this._changed(); }
    this.onSelect && this.onSelect(this.selected);
  }
  // A drawing has just been placed: the tool goes back to the cursor (unless the reader stays in
  // drawing mode), the new drawing is selected, and the host hears of it (a text opens its editor).
  _created() {
    const idx = this.drawings.length - 1;
    this._reset();
    if (!this.stayInMode && idx >= 0) { this.selected = idx; this.multiSel = new Set(); this.render(); this.onSelect && this.onSelect(idx); }
    if (idx >= 0) this.onCreated && this.onCreated(idx);
  }

  // ── API برای Object Tree / نوار استایل ──
  selectAt(i) { this.selected = (i >= 0 && i < this.drawings.length) ? i : -1; this.render(); this.onSelect && this.onSelect(this.selected); }
  getSelected() { return this.selected >= 0 ? this.drawings[this.selected] : null; }
  // فرمان‌های صفحه‌کلید فقط از dispatcher سراسری BazaarNama وارد می‌شوند. این دو
  // متد رفتارهای داخلی Canvas را بدون نصب keydown دوم در اختیار آن dispatcher می‌گذارند.
  cancelInteraction() {
    this.selected = -1; this.multiSel = new Set(); this.marquee = null;
    this.pending = null; this.tmp = null; this.twoClick = false;
    this.dragging = false; this.measuring = false;
    this.render(); this.onSelect && this.onSelect(-1);
  }
  // Arrow keys move the selected drawings 1px (8px with Shift). Left and right step a whole bar
  // when a pixel is less than one, so the drawing always moves. False when nothing could move.
  nudgeSelected(key, large = false) {
    const idxs = this.selectedIndices().filter((i) => this.drawings[i] && !this.drawings[i].locked);
    if (!idxs.length) return false;
    const step = large ? 8 : 1;
    const sx = key === 'ArrowLeft' ? -step : key === 'ArrowRight' ? step : 0;
    const sy = key === 'ArrowUp' ? -step : key === 'ArrowDown' ? step : 0;
    if (!sx && !sy) return false;
    const ref = idxs.map((i) => this._handlePoints(this.drawings[i]).find((p) => p.x != null && p.y != null)).find(Boolean);
    let dt = 0, dp = 0;
    if (ref) {
      if (sy) { const a = this._p(ref.y), b = this._p(ref.y + sy); if (a != null && b != null) dp = b - a; }
      if (sx) { const a = this._t(ref.x), b = this._t(ref.x + sx); if (a != null && b != null) dt = b - a; if (!dt) { const iv = this._barInterval(); if (iv) dt = Math.sign(sx) * iv; } }
    }
    const screenOnly = idxs.every((i) => extScreen(this.drawings[i].type));
    if (!screenOnly && (!Number.isFinite(dt) || !Number.isFinite(dp) || (!dt && !dp))) return false;
    this._pushUndo();
    const W = this.canvas.width || 1, H = this.canvas.height || 1;
    idxs.forEach((i) => { const d = this.drawings[i]; if (extScreen(d.type)) { d.ax = (d.ax || 0) + sx / W; d.ay = (d.ay || 0) + sy / H; } else this._moveBy(d, dt, dp); });
    this._changed();
    return true;
  }
  // انتخاب همه‌ی ترسیم‌های قفل‌نشده (Ctrl+A TV) — به multiSel می‌ریزد تا move/delete/style گروهی کار کند.
  selectAll() {
    this.multiSel = new Set();
    for (let i = 0; i < this.drawings.length; i++) { const d = this.drawings[i]; if (d && !d.locked && d.visible !== false) this.multiSel.add(i); }
    if (this.multiSel.size <= 1) { this.selected = this.multiSel.size ? [...this.multiSel][0] : -1; this.multiSel = new Set(); }
    else this.selected = [...this.multiSel][0];
    this.render(); this.onSelect && this.onSelect(this.selected);
  }
  removeAt(i) { if (i < 0 || i >= this.drawings.length) return; this._pushUndo(); this.multiSel = new Set(); this.drawings.splice(i, 1); if (this.selected === i) this.selected = -1; else if (this.selected > i) this.selected--; this._changed(); this.onSelect && this.onSelect(this.selected); }
  // حذف گروهی چند-انتخاب (Ctrl+کلیک) — splice نزولی تا ایندکس‌ها معتبر بمانند.
  removeSelected() {
    const idxs = [...this.multiSel]; if (this.selected >= 0) idxs.push(this.selected);
    const uniq = [...new Set(idxs)].filter((i) => i >= 0 && i < this.drawings.length).sort((a, b) => b - a);
    if (!uniq.length) return;
    this._pushUndo();
    uniq.forEach((i) => this.drawings.splice(i, 1));
    this.multiSel = new Set(); this.selected = -1;
    this._changed(); this.onSelect && this.onSelect(-1);
  }
  toggleVisible(i) { const d = this.drawings[i]; if (!d) return; this._pushUndo(); d.visible = d.visible === false; this._changed(); }
  toggleLock(i) { const d = this.drawings[i]; if (!d) return; this._pushUndo(); d.locked = !d.locked; this._changed(); }
  setStyle(i, st) { const d = this.drawings[i]; if (!d) return; this._pushUndo();
    // استایل گروهی (TV): اگر چند-انتخاب فعال است و i عضو آن، تغییر سبک روی همه‌ی اعضا اعمال شود.
    // استثنا: تغییر «text» (ویرایش محتوای متن) فقط روی همان درای — نه کل گروه.
    const grp = this.multiSel.size > 1 && this.multiSel.has(i) && !('text' in st);
    const targets = grp ? [...this.multiSel] : [i];
    targets.forEach((k) => { const dd = this.drawings[k]; if (dd) Object.assign(dd, st); });
    this._changed(); }
  setStayInMode(on) { this.stayInMode = !!on; }
  // «نمایش روی تایم‌فریم‌ها» (Visibility on intervals TV) — کلاس tf جاری (minutes/hours/days/weeks/months)
  //   از BazaarNama ست می‌شود؛ ترسیم دارای tfVis[class]===false در این کلاس رندر/انتخاب نمی‌شود. #271
  //   A class may also carry a range (TradingView's Visibility tab): {on, min, max} in that class's
  //   units, against the interval's size n (15 for 15 minutes, 4 for 4 hours).
  setTfClass(cls, n) { if (this.tfClass === cls && this.tfN === (n ?? null)) return; this.tfClass = cls; this.tfN = n ?? null; this.render(); }
  _tfHidden(d) {
    if (!d || !d.tfVis || !this.tfClass) return false;
    const v = d.tfVis[this.tfClass];
    if (v === false) return true;
    if (v && typeof v === 'object') {
      if (v.on === false) return true;
      const n = this.tfN;
      if (n != null && ((v.min != null && n < v.min) || (v.max != null && n > v.max))) return true;
    }
    return false;
  }
  // فرمتر زمان (tz-aware، از BazaarNama) — برای برچسب تاریخ خط عمودی روی محور زمان (مثل TV). #281
  setTimeFmt(fn) { this.timeFmt = (typeof fn === 'function') ? fn : null; }
  lockAll(on, record = true) { if (record) this._pushUndo(); this.drawings.forEach((d) => { d.locked = on; }); this._changed(); }
  hideAll(on) { this._pushUndo(); this.drawings.forEach((d) => { d.visible = !on; }); this._changed(); }
  // آیا همه‌ی ترسیم‌ها پنهان‌اند؟ (برای تیک منوی «پنهان‌کردن ترسیم‌ها»)
  allHidden() { return this.drawings.length > 0 && this.drawings.every((d) => d.visible === false); }
  // افزودن خط افقی در قیمت دلخواه (منوی راست‌کلیک — «افزودن خط افقی در …»)؛ ایندکس آن را برمی‌گرداند.
  addHLine(price, color) { if (price == null || !Number.isFinite(price)) return -1; this._pushUndo(); this.drawings.push(this._applyDef({ type: 'hline', p: price, color: color || this.color, width: 2 })); this._changed(); return this.drawings.length - 1; }

  // #کلون: کپی عمیق آبجکت با آفست کوچک (≈۱۴px) تا کپی دیده شود؛ انتخاب روی کپی تازه
  clone(i) {
    const d = this.drawings[i]; if (!d) return -1;
    this._pushUndo();
    const c = JSON.parse(JSON.stringify(d)); c.locked = false;
    this._offset(c, 14);
    this.drawings.push(c); this.selected = this.drawings.length - 1; this._changed(); this.onSelect && this.onSelect(this.selected);
    return this.selected;
  }

  // #کپی/چسباندن (Ctrl+C/Ctrl+V مثل TV) — copy یک کپی عمیق آبجکت انتخابی را برمی‌گرداند؛
  // paste یک نسخه‌ی آفست‌دار (≈۱۴px) از آبجکت کلیپ‌بورد اضافه و انتخاب می‌کند (مثل clone ولی از کلیپ‌بورد).
  copy(i) { const d = this.drawings[i]; return d ? JSON.parse(JSON.stringify(d)) : null; }
  paste(obj) {
    if (!obj || !obj.type) return -1;
    this._pushUndo();
    const c = JSON.parse(JSON.stringify(obj)); c.locked = false; c.visible = true;
    this._offset(c, 14);
    this.drawings.push(c); this.selected = this.drawings.length - 1; this._changed(); this.onSelect && this.onSelect(this.selected);
    return this.selected;
  }

  // ── ترتیب Z (آرایه به ترتیب رسم است؛ انتهای آرایه = روی همه) ──
  bringToFront(i) { if (i < 0 || i >= this.drawings.length) return; this._pushUndo(); const [d] = this.drawings.splice(i, 1); this.drawings.push(d); this.selected = this.drawings.length - 1; this._changed(); this.onSelect && this.onSelect(this.selected); }
  sendToBack(i) { if (i < 0 || i >= this.drawings.length) return; this._pushUndo(); const [d] = this.drawings.splice(i, 1); this.drawings.unshift(d); this.selected = 0; this._changed(); this.onSelect && this.onSelect(this.selected); }
  bringForward(i) { if (i < 0 || i >= this.drawings.length - 1) return; this._pushUndo(); const d = this.drawings[i]; this.drawings[i] = this.drawings[i + 1]; this.drawings[i + 1] = d; if (this.selected === i) this.selected = i + 1; else if (this.selected === i + 1) this.selected = i; this._changed(); this.onSelect && this.onSelect(this.selected); }
  // جابه‌جایی آزاد ترتیب لایه (z-order) با کشیدن‌ورهاکردن در درخت آبجکت — سبک TV. from را برداشته و پیش to می‌گذارد.
  move(from, to) { const n = this.drawings.length; if (from === to || from < 0 || from >= n || to < 0 || to >= n) return; this._pushUndo(); const [d] = this.drawings.splice(from, 1); this.drawings.splice(to, 0, d); this.selected = to; this._changed(); this.onSelect && this.onSelect(this.selected); }
  sendBackward(i) { if (i <= 0 || i >= this.drawings.length) return; this._pushUndo(); const d = this.drawings[i]; this.drawings[i] = this.drawings[i - 1]; this.drawings[i - 1] = d; if (this.selected === i) this.selected = i - 1; else if (this.selected === i - 1) this.selected = i; this._changed(); this.onSelect && this.onSelect(this.selected); }

  setOrder(o) { this.order = o; this.render(); }

  setProfile(buckets) { this.profile = buckets; this.render(); }
  setScriptPaint(s) { this.script = s; this.render(); }
  setCandles(cs) { this.candles = cs; }
  setDigits(d) { if (Number.isFinite(d)) this.digits = d; }
  // A price as the axis writes it (the host may hand its own formatter, with thousands separators).
  _fmtP(v) { try { if (typeof this.priceFmt === 'function') return String(this.priceFmt(v)); } catch (e) { /* fall back */ } return Number(v).toFixed(this.digits); } // دقت اعشار نماد فعال (برچسب ابزار اندازه‌گیری)
  setMagnet(on, mode) { this.magnet = on; if (mode) this.magnetMode = mode; }

  _snap(pt) {
    if (!this.magnet || !this.candles || !this.candles.length || pt.t == null) return pt;
    // نزدیک‌ترین کندل به زمان، snap قیمت به نزدیک‌ترین O/H/L/C
    let best = null, bd = Infinity;
    for (const c of this.candles) { const dd = Math.abs(c.t - pt.t); if (dd < bd) { bd = dd; best = c; } }
    if (!best) return pt;
    const cand = [best.o, best.h, best.l, best.c];
    let bp = pt.p, bpd = Infinity;
    cand.forEach((v) => { const dd = Math.abs(v - pt.p); if (dd < bpd) { bpd = dd; bp = v; } });
    // حالت ضعیف (سبک TV): فقط وقتی نشانگر به‌قدر کافی نزدیک OHLC است snap کن؛ وگرنه نقطه‌ی آزاد.
    if (this.magnetMode === 'weak' && pt.p && bpd / Math.abs(pt.p) > 0.0015) return pt;
    return { t: best.t, p: bp };
  }

  setSeries(s) { this.series = s; this.render(); }
  // سبک پیش‌فرض per-tool TV: نگاشت { type: {color,width,lineStyle,fill,fillOpacity,fontSize,bold,italic} }.
  setToolDefaults(m) { this.toolDefaults = (m && typeof m === 'object') ? m : {}; }
  // اعمال سبک پیش‌فرض نوع روی یک ترسیم نوساخته — فقط کلیدهای سبک را می‌ریزد (هندسه/متن دست‌نخورده)؛ اگر پیش‌فرضی نباشد no-op.
  _applyDef(d) {
    const def = d && d.type ? this.toolDefaults[d.type] : null;
    if (!def) return d;
    const out = { ...d };
    STYLE_KEYS.forEach((k) => { if (def[k] !== undefined) out[k] = JSON.parse(JSON.stringify(def[k])); });
    return out;
  }
  // 'cursor' and 'eraser' leave the chart its own pointer (the layer takes it only over a drawing);
  // every other tool takes the pointer, and touch-action:none so a finger draws instead of scrolling.
  setTool(t, color) {
    this.tool = t; if (color) this.color = color; this.pending = null; this.tmp = null; this.twoClick = false; this.dragging = false; this.multiSel = new Set(); this.hover = -1; this.zoomBox = null;
    const passive = t === 'cursor' || t === 'eraser';
    this.canvas.style.pointerEvents = passive ? 'none' : 'auto';
    this.canvas.style.touchAction = passive ? '' : 'none';
    this.canvas.style.cursor = (t === 'cursor' || t === 'select') ? 'default' : t === 'eraser' ? this.eraserCursor : t === 'zoom' ? 'zoom-in' : 'crosshair';
    if (t !== 'select') { this.selected = -1; this.onSelect && this.onSelect(-1); }
    this.render();
  }
  // record=true وقتی کاربر صریحاً چیدمانی را لود می‌کند (باید undo شود)؛
  // پیش‌فرض false برای لود اولیه/بازیابی نماد که نباید در تاریخچه بیفتد.
  setDrawings(arr, record = false) { if (record && this.drawings.length) this._pushUndo(); this.drawings = arr || []; this.selected = -1; this.render(); }
  getDrawings() { return this.drawings; }
  clearLast() { this.drawings.pop(); this._changed(); }

  // #۹ افزودن برنامه‌ای یک ترسیم (مثل جعبه‌ی لانگ/شورت از کلیک‌راست) + ثبت در undo/persist
  addDrawing(d) { if (!d || typeof d !== 'object') return; this._pushUndo(); this.drawings.push(d); this.selected = this.drawings.length - 1; this._changed(); this.onSelect && this.onSelect(this.selected); }
  clearAll() { if (this.drawings.length) this._pushUndo(); this.drawings = []; this.selected = -1; this.multiSel = new Set(); this._changed(); }
  _changed() { this.render(); this.onChange && this.onChange(this.drawings); }

  // فاصله‌ی زمانی یک بار (ثانیه) — میانه‌ی چند فاصله‌ی آخر (مقاوم به گپ‌ها) برای اکستراپولیشن در فضای خالی.
  _barInterval() {
    const cs = this.candles;
    if (cs && cs.length >= 2) {
      const n = cs.length, diffs = [];
      for (let i = Math.max(1, n - 8); i < n; i++) { const d = cs[i].t - cs[i - 1].t; if (d > 0) diffs.push(d); }
      if (diffs.length) { diffs.sort((a, b) => a - b); return diffs[Math.floor(diffs.length / 2)]; }
    }
    return null;
  }
  _x(t) {
    try {
      const ts = this.chart.timeScale();
      const x = ts.timeToCoordinate(t);
      if (x != null) return x; // داخل بازه: رفتار قبلی بدون تغییر
      // زمان فراتر از آخرین/قبل از اولین کندل (فضای خالی): به مختصات منطقی نگاشت کن تا نقطه‌ی رسم در whitespace رندر شود (سبک TV).
      const cs = this.candles, iv = this._barInterval();
      if (!cs || !cs.length || !iv || !ts.logicalToCoordinate) return null;
      const lastIdx = cs.length - 1;
      let L;
      if (t > cs[lastIdx].t) L = lastIdx + (t - cs[lastIdx].t) / iv;
      else if (t < cs[0].t) L = 0 - (cs[0].t - t) / iv;
      else return null; // داخل بازه ولی گپ — دست نزن
      const xx = ts.logicalToCoordinate(L);
      return (xx != null && Number.isFinite(xx)) ? xx : null;
    } catch (e) { return null; }
  } // دفاعی: زمان نامعتبر نباید حلقه‌ی رندر را بشکند
  _y(p) { return this.series ? this.series.priceToCoordinate(p) : null; }
  // نقاط لنگر یک ترسیم در مختصات صفحه — برای تست داخل مستطیل Marquee (هر نوع: pts / p0,p1 / hline p / vline t / متن t,p).
  _screenPts(d) {
    const out = []; const add = (t, p) => { const x = (t != null) ? this._x(t) : null; const y = (p != null) ? this._y(p) : null; if (x != null || y != null) out.push({ x, y }); };
    if (extScreen(d.type)) { out.push({ x: (d.ax || 0) * this.canvas.width, y: (d.ay || 0) * this.canvas.height }); return out; }
    if (Array.isArray(d.pts)) d.pts.forEach((q) => add(q.t, q.p));
    if (d.p0) add(d.p0.t, d.p0.p);
    if (d.p1) add(d.p1.t, d.p1.p);
    if (d.p != null && d.t == null) add(null, d.p);         // hline: فقط y
    if (d.t != null && d.p == null) add(d.t, null);         // vline: فقط x
    if (d.t != null && d.p != null && !d.p0 && !Array.isArray(d.pts)) add(d.t, d.p); // متن/نشانه
    return out;
  }
  // آیا لنگر ترسیم داخل مستطیل [rx0,ry0..rx1,ry1] است؟ (hline فقط y، vline فقط x را می‌سنجد)
  _inRect(d, rx0, ry0, rx1, ry1) {
    const xmin = Math.min(rx0, rx1), xmax = Math.max(rx0, rx1), ymin = Math.min(ry0, ry1), ymax = Math.max(ry0, ry1);
    return this._screenPts(d).some((q) => {
      const xok = (q.x == null) || (q.x >= xmin && q.x <= xmax);
      const yok = (q.y == null) || (q.y >= ymin && q.y <= ymax);
      return xok && yok;
    });
  }
  _t(x) {
    const ts = this.chart.timeScale();
    const t = ts.coordinateToTime(x);
    if (t != null) return t; // داخل بازه: رفتار قبلی بدون تغییر
    // فضای خالی (فراتر از آخرین یا قبل از اولین کندل): زمان را از مختصات منطقی اکستراپوله کن تا بتوان در whitespace نقطه گذاشت (سبک TV).
    try {
      const cs = this.candles, iv = this._barInterval();
      if (!cs || !cs.length || !iv || !ts.coordinateToLogical) return t;
      const L = ts.coordinateToLogical(x);
      if (L == null || !Number.isFinite(L)) return t;
      const lastIdx = cs.length - 1;
      return cs[lastIdx].t + Math.round(L - lastIdx) * iv;
    } catch (e) { return t; }
  }
  _p(y) { return this.series ? this.series.coordinateToPrice(y) : null; }

  // نقاط قابل‌درگ هر آبجکت → [{x,y,set(t,p)}]
  _handlePoints(d) {
    if (isExt(d.type)) { const h = extHandles(d, this); if (h) return h; }
    if (d.type === 'hline') { const y = this._y(d.p); return [{ x: 14, y, set: (t, p) => { d.p = p; } }]; }
    if (d.type === 'vline') { const x = this._x(d.t); return [{ x, y: 14, set: (t) => { d.t = t; } }]; }
    if (d.type === 'text') { return [{ x: this._x(d.t), y: this._y(d.p), set: (t, p) => { d.t = t; d.p = p; } }]; }
    // Long and short positions: entry, stop and target on the left edge, the width on the right.
    if ((d.type === 'longshort' || d.type === 'short') && d.p0 && d.p1) {
      const g = this._posGeom(d); if (!g) return [];
      return [
        { x: g.xa, y: g.yE, set: (t, p) => { d.p0 = { t, p }; } },
        { x: g.xa, y: g.yS, set: (t, p) => { d.p1 = { t: d.p1.t, p }; } },
        { x: g.xa, y: g.yT, set: (t, p) => { d.tp = p; } },
        { x: g.xb, y: g.yE, set: (t) => { d.p1 = { t, p: d.p1.p }; } },
      ];
    }
    if (d.pts) return d.pts.map((pp, k) => ({ x: this._x(pp.t), y: this._y(pp.p), set: (t, p) => { d.pts[k] = { t, p }; } }));
    const out = [];
    if (d.p0) out.push({ x: this._x(d.p0.t), y: this._y(d.p0.p), set: (t, p) => { d.p0 = { t, p }; } });
    if (d.p1) out.push({ x: this._x(d.p1.t), y: this._y(d.p1.p), set: (t, p) => { d.p1 = { t, p }; } });
    return out;
  }
  _hitHandle(d, x, y) { const hs = this._handlePoints(d); for (let i = 0; i < hs.length; i++) { const h = hs[i]; if (h.x != null && h.y != null && Math.hypot(x - h.x, y - h.y) < 9) return i; } return -1; }
  _moveBy(d, dt, dp) {
    const mv = (pt) => ({ t: pt.t + dt, p: pt.p + dp });
    if (isExt(d.type) && extMove(d, dt, dp, this)) return;
    if (d.tp != null && Number.isFinite(d.tp)) d.tp += dp;
    if (d.type === 'hline') { d.p += dp; return; }
    if (d.type === 'vline') { d.t += dt; return; }
    if (d.type === 'text') { d.t += dt; d.p += dp; return; }
    if (d.pts) { d.pts = d.pts.map(mv); return; }
    if (d.p0) d.p0 = mv(d.p0); if (d.p1) d.p1 = mv(d.p1);
  }
  // ابزارهای دونقطه‌ای خطی که با Shift به زاویه‌ی ۰/۴۵/۹۰ می‌چسبند (سبک TV).
  _isAngleTool(type) { return type === 'trend' || type === 'ray' || type === 'extline' || type === 'infoline' || type === 'arrow'; }
  // نقطه‌ی دوم p1 را در فضای پیکسل به نزدیک‌ترین مضرب ۴۵° نسبت به p0 می‌چسباند و به زمان/قیمت برمی‌گرداند.
  _constrainAngle(p0, p1) {
    const x0 = this._x(p0.t), y0 = this._y(p0.p), x1 = this._x(p1.t), y1 = this._y(p1.p);
    if (x0 == null || y0 == null || x1 == null || y1 == null) return p1;
    const dx = x1 - x0, dy = y1 - y0, dist = Math.hypot(dx, dy);
    if (dist < 1) return p1;
    const a = Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) * (Math.PI / 4);
    const nt = this._t(x0 + dist * Math.cos(a)), np = this._p(y0 + dist * Math.sin(a));
    return { t: nt != null ? nt : p1.t, p: np != null ? np : p1.p };
  }
  // مربع/دایره: نقطه‌ی دوم را چنان می‌چسباند که |عرض|=|ارتفاع| در پیکسل شود (مستطیل→مربع، بیضی→دایره) — سبک TV با Shift.
  _isSquareTool(type) { return type === 'rect' || type === 'rotrect' || type === 'ellipse'; }
  _constrainSquare(p0, p1) {
    const x0 = this._x(p0.t), y0 = this._y(p0.p), x1 = this._x(p1.t), y1 = this._y(p1.p);
    if (x0 == null || y0 == null || x1 == null || y1 == null) return p1;
    const dx = x1 - x0, dy = y1 - y0, s = Math.max(Math.abs(dx), Math.abs(dy));
    if (s < 1) return p1;
    const nt = this._t(x0 + (dx < 0 ? -s : s)), np = this._p(y0 + (dy < 0 ? -s : s));
    return { t: nt != null ? nt : p1.t, p: np != null ? np : p1.p };
  }
  // دیسپچر constrain Shift: خطوط ⇒ زاویه؛ اشکال جعبه‌ای ⇒ مربع/دایره؛ بقیه بدون تغییر.
  _constrainDraw(type, p0, p1) {
    if (this._isAngleTool(type)) return this._constrainAngle(p0, p1);
    if (this._isSquareTool(type)) return this._constrainSquare(p0, p1);
    return p1;
  }

  _bind() {
    const cv = this.canvas;
    const down = (e) => {
      if (!this.series) return;
      if (e.button != null && e.button !== 0) return; // only the primary button (or a finger) draws; a right click is the menu's
      const r = cv.getBoundingClientRect();
      const x = e.clientX - r.left, y = e.clientY - r.top;
      // A finger has no double click: two taps close together finish a polyline or edit a drawing.
      if (e.pointerType && e.pointerType !== 'mouse') {
        const now = Date.now(), lt = this._lastTap; this._lastTap = { t: now, x, y };
        if (lt && now - lt.t < 320 && Math.hypot(x - lt.x, y - lt.y) < 24) {
          this._lastTap = null;
          if (this.pending && EXT_POLY.includes(this.pending.type)) { finishPoly(); return; }
          if (this.tool === 'cursor' || this.tool === 'select') { const idx = this._hit(x, y); if (idx >= 0) { this.selected = idx; this.render(); this.onSelect && this.onSelect(idx); this.onDblEdit && this.onDblEdit(idx); return; } }
        }
      }
      // Eraser: a click on a drawing removes it (locked drawings stay).
      if (this.tool === 'eraser') { const hit = this._hit(x, y); if (hit >= 0 && this.drawings[hit] && !this.drawings[hit].locked) this.removeAt(hit); return; }
      // Zoom in: drag a rectangle; the host zooms the chart to it on release.
      if (this.tool === 'zoom') { this.zoomBox = { x0: x, y0: y, x1: x, y1: y }; this.render(); return; }
      if (this.tool === 'cursor' || this.tool === 'select') {
        // Shift+درگ ⇒ ابزار اندازه‌گیری گذرا (سبک TV): جعبه‌ی Δقیمت/٪/بار/ساعت که با رهاکردن پاک می‌شود.
        if (e.shiftKey) { const mp = this._snap({ t: this._t(x), p: this._p(y) }); if (mp.t != null && mp.p != null) { this.tmp = { type: 'dprange', p0: mp, p1: mp, color: '#089981', width: 2 }; this.dragging = true; this.didDrag = false; this.downXY = { x, y }; this.measuring = true; return; } }
        // درگ خطوط سفارش (entry/sl/tp)
        if (this.order) { for (const k of ['entry', 'sl', 'tp']) { const yy = this._y(this.order[k]); if (yy != null && Math.abs(yy - y) < 7) { this.dragOrder = k; return; } } }
        // ۱) اگر آبجکتی انتخاب شده، روی handleهایش کلیک شده؟ → درگ handle
        if (this.selected >= 0) {
          const sd = this.drawings[this.selected];
          if (sd && !sd.locked) { const hi = this._hitHandle(sd, x, y); if (hi >= 0) { this.dragHandle = { idx: this.selected, hi, screen: extScreen(sd.type), snap: this._snapshot() }; return; } }
        }
        // ۲) انتخاب آبجکت زیر کلیک
        const hit = this._hit(x, y);
        // Marquee TV: در حالت «select»، کشیدن روی فضای خالی ⇒ مستطیل انتخاب (چند-انتخاب). فقط select،
        //   چون در حالت cursor کشیدن فضای خالی برای pan/زوم چارت رزرو است.
        if (this.tool === 'select' && hit < 0 && !e.ctrlKey && !e.metaKey && !e.shiftKey && !e.altKey) {
          this.marquee = { x0: x, y0: y, x1: x, y1: y }; this.selected = -1; this.multiSel = new Set(); this.onSelect && this.onSelect(-1); this.render(); return;
        }
        // Ctrl/Cmd+کلیک ⇒ چند-انتخاب (toggle) سبک TV — بدون شروع درگ.
        if ((e.ctrlKey || e.metaKey) && !e.altKey) {
          if (hit >= 0) {
            if (!this.multiSel.size && this.selected >= 0 && this.selected !== hit) this.multiSel.add(this.selected);
            if (this.multiSel.has(hit)) this.multiSel.delete(hit); else this.multiSel.add(hit);
            if (this.multiSel.size <= 1) { this.selected = this.multiSel.size ? [...this.multiSel][0] : -1; this.multiSel = new Set(); }
            else this.selected = hit;
            this.onSelect && this.onSelect(this.selected); this.render();
          }
          return;
        }
        // کلیک ساده روی عضوی از گروه چند-انتخاب ⇒ گروه را نگه‌دار (برای درگ گروهی)؛ وگرنه تک‌انتخاب.
        const keepGroup = hit >= 0 && this.multiSel.size > 1 && this.multiSel.has(hit);
        if (!keepGroup) this.multiSel = new Set();
        this.selected = hit; this.onSelect && this.onSelect(hit); this.render();
        // ۳) اگر روی بدنه‌ی آبجکت قفل‌نشده کلیک شد → جابه‌جایی کل آبجکت (یا گروه چند-انتخاب).
        //    گارد this.drawings[hit]: در حالت «پاک‌کن» onSelect همان آبجکت را حذف می‌کند و آرایه کوچک می‌شود ⇒ بدون گارد، خواندن .locked کرش می‌داد.
        if (hit >= 0 && this.drawings[hit] && !this.drawings[hit].locked) { const sp = this._snap({ t: this._t(x), p: this._p(y) }); if ((sp.t != null && sp.p != null) || extScreen(this.drawings[hit].type)) {
          // A plain click selects without a history step; the step is recorded when the drawing first moves.
          const snap = this._snapshot();
          // Alt+درگ ⇒ کلون درجا و درگ کپی (اصل سر جایش می‌مانَد) — ژست رایج TV. بدون Alt = جابه‌جایی خود آبجکت.
          let mi = hit;
          if (e.altKey) { this._history.record(snap); const dup = JSON.parse(JSON.stringify(this.drawings[hit])); dup.locked = false; this.drawings.push(dup); mi = this.drawings.length - 1; this.onSelect && this.onSelect(mi); this.multiSel = new Set(); }
          this.selected = mi; this.dragMove = { idx: mi, last: sp, xy: { x, y }, snap: e.altKey ? null : snap }; } }
        return;
      }
      let pt = this._snap({ t: this._t(x), p: this._p(y) });
      if (pt.t == null || pt.p == null) return;
      // ابزارهای freehand (brush/highlighter): با درگ نمونه‌برداری می‌شوند، نه با کلیک.
      // پیش‌تر EXT_FREEHAND ساخته می‌شد ولی **هرگز import نمی‌شد**، و EXT_NEED فقط
      // points>=3 را می‌گیرد — پس points:-1 در هیچ شاخه‌ای نمی‌افتاد و این دو دکمه
      // برای همیشه هیچ‌چیز نمی‌کشیدند.
      if (EXT_FREEHAND.includes(this.tool)) {
        this.freehand = { type: this.tool, pts: [pt], color: this.color };
        this.render();
        return;
      }
      // One-click tools (a note, a flag, an anchored VWAP, an icon…) are placed where the click lands.
      if (EXT_ONE.includes(this.tool)) {
        const clr = this.tool === 'arrowup' ? '#089981' : this.tool === 'arrowdown' ? '#f23645' : this.color;
        const nd = this._applyDef({ type: this.tool, p0: pt, p1: pt, color: clr, width: 2 });
        extInit(nd, this, x, y);
        this._pushUndo(); this.drawings.push(nd); this._created();
        return;
      }
      const NEED = { channel: 3, pitchfork: 3, ...EXT_NEED };
      if (NEED[this.tool]) {
        if (!this.pending || this.pending.type !== this.tool) this.pending = { type: this.tool, pts: [pt], color: this.color };
        else this.pending.pts.push(pt);
        if (this.pending.pts.length >= NEED[this.tool]) { this._pushUndo(); this.drawings.push(this._applyDef({ type: this.tool, pts: this.pending.pts.slice(), color: this.color, width: 2 })); this.pending = null; this._created(); }
        else this.render();
        return;
      }
      // خط چندتکه (polyline): هر کلیک یک رأس اضافه می‌کند؛ پایان با دابل‌کلیک یا Enter (بی‌پایان ثابت).
      if (EXT_POLY.includes(this.tool)) {
        const tt = this.tool;
        if (!this.pending || this.pending.type !== tt) this.pending = { type: tt, pts: [pt], color: this.color };
        else this.pending.pts.push(pt);
        const max = extMaxPts(tt);
        if (max && this.pending.pts.length >= max) { finishPoly(); return; }
        this.render();
        return;
      }
      if (this.tool === 'hline') { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'hline', p: pt.p, color: this.color, width: 2 })); this._created(); return; }
      if (this.tool === 'vline') { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'vline', t: pt.t, color: this.color, width: 2 })); this._created(); return; }
      // The host's own text editor when it has one (onTextRequest: the new drawing's index, then
      // setStyle(i, {text})); window.prompt only as the fallback.
      if (this.tool === 'text') {
        if (this.onCreated) { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'text', t: pt.t, p: pt.p, text: '', color: this.color })); this._created(); return; }
        if (this.onTextRequest) { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'text', t: pt.t, p: pt.p, text: '', color: this.color })); const idx = this.drawings.length - 1; this._reset(); this.onTextRequest(idx); return; }
        const txt = window.prompt('متن (برای خط جدید «\\n» بنویسید):'); if (txt) { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'text', t: pt.t, p: pt.p, text: txt, color: this.color })); } this._reset(); return;
      }
      // ابزارهای دونقطه‌ای: پشتیبانی از هر دو حالت «کلیک-کلیک» (مثل TradingView) و «کلیک-کشیدن».
      // اگر منتظر کلیک دوم هستیم، این کلیک نقطه‌ی پایانی است و ترسیم ثبت می‌شود.
      if (this.twoClick && this.tmp) {
        this.tmp.p1 = e.shiftKey ? this._constrainDraw(this.tmp.type, this.tmp.p0, pt) : pt; this._pushUndo(); const nd = this._applyDef(this.tmp); extInit(nd, this, x, y); this.drawings.push(nd);
        this.tmp = null; this.twoClick = false; this.dragging = false; this.didDrag = false; this._created(); return;
      }
      // نشانگر فلش بالا/پایین = سیگنال خرید/فروش ⇒ رنگ پیش‌فرض معنایی سبز/قرمز (مثل TV؛ کاربر می‌تواند بعداً تغییر دهد).
      const _clr = this.tool === 'arrowup' ? '#089981' : this.tool === 'arrowdown' ? '#f23645' : this.color;
      this.tmp = { type: this.tool, p0: pt, p1: pt, color: _clr, width: 2 };
      this.dragging = true; this.didDrag = false; this.downXY = { x, y };
    };
    // اسکرول خودکار لبه هنگام ترسیم (سبک TradingView): اگر حین کشیدن/ویرایش یک ابزار،
    // نشانگر به لبه‌ی چپ/راست چارت برسد، پنجره‌ی زمانی همان‌سو پن می‌شود تا کاربر بتواند ترسیم را
    // تا هر کندل دلخواه (بیرون از صفحه‌ی فعلی) ادامه دهد. با هر فریم نقطه‌ی فعال هم به‌روز می‌شود.
    let edgeRAF = null, edgeDir = 0, edgeX = 0, edgeY = 0;
    const edgeTick = () => {
      if (!edgeDir) { edgeRAF = null; return; }
      let ts; try { ts = this.chart.timeScale(); } catch (e) { edgeRAF = null; return; }
      const lr = ts.getVisibleLogicalRange && ts.getVisibleLogicalRange();
      if (lr) {
        const span = Math.max(1, lr.to - lr.from);
        const step = Math.max(0.4, span * 0.010) * edgeDir; // ~۱٪ عرض دید در هر فریم
        try { ts.setVisibleLogicalRange({ from: lr.from + step, to: lr.to + step }); } catch (e) { /* noop */ }
      }
      const sp = this._snap({ t: this._t(edgeX), p: this._p(edgeY) });
      if (sp.t != null && sp.p != null) {
        if (this.dragging && this.tmp) this.tmp.p1 = sp;
        else if (this.twoClick && this.tmp) this.tmp.p1 = sp;
        else if (this.pending) this.pending.preview = sp;
      }
      this.render();
      edgeRAF = requestAnimationFrame(edgeTick);
    };
    const setEdgePan = (dir, x, y) => {
      edgeDir = dir; edgeX = x; edgeY = y;
      if (dir && !edgeRAF) edgeRAF = requestAnimationFrame(edgeTick);
      else if (!dir && edgeRAF) { cancelAnimationFrame(edgeRAF); edgeRAF = null; }
    };
    const stopEdgePan = () => setEdgePan(0, 0, 0);
    this._stopEdgePan = stopEdgePan;
    const move = (e) => {
      const r = cv.getBoundingClientRect();
      const x = e.clientX - r.left, y = e.clientY - r.top;
      // تشخیص لبه هنگام یک عملیات ترسیم/ویرایش فعال ⇒ فعال‌سازی پن خودکار.
      const _drawActive = (this.dragging && this.tmp) || (this.twoClick && this.tmp) || !!this.pending || !!this.dragHandle || !!this.dragMove;
      let _edir = 0; const EDGE = 40;
      if (_drawActive) { if (x < EDGE) _edir = -1; else if (x > r.width - EDGE) _edir = 1; }
      setEdgePan(_edir, x, y);
      // نمونه‌برداری freehand — نقطه‌ی نو فقط وقتی که به‌اندازه‌ی کافی از قبلی دور شده،
      // وگرنه صدها نقطه‌ی هم‌پوشان ذخیره می‌شود و render کند می‌شود.
      if (this.freehand) {
        const sp = { t: this._t(x), p: this._p(y) };
        if (sp.t != null && sp.p != null) {
          const last = this.freehand.pts[this.freehand.pts.length - 1];
          const lp = last ? { x: this._x(last.t), y: this._y(last.p) } : null;
          if (!lp || Math.hypot(x - lp.x, y - lp.y) >= 3) { this.freehand.pts.push(sp); this.render(); }
        }
        return;
      }
      if (this.marquee) { this.marquee.x1 = x; this.marquee.y1 = y; this.render(); return; }
      if (this.zoomBox) { this.zoomBox.x1 = x; this.zoomBox.y1 = y; this.render(); return; }
      if (this.dragOrder && this.order) { const pr = this._p(y); if (pr != null) { this.order[this.dragOrder] = pr; this.render(); this.onOrder && this.onOrder({ ...this.order }); } return; }
      // درگ handle
      if (this.dragHandle) {
        const d = this.drawings[this.dragHandle.idx];
        if (this.dragHandle.snap) { this._history.record(this.dragHandle.snap); this.dragHandle.snap = null; }
        if (d && this.dragHandle.screen) { d.ax = Math.max(0, Math.min(1, x / (cv.width || 1))); d.ay = Math.max(0, Math.min(1, y / (cv.height || 1))); this.render(); return; }
        const sp = this._snap({ t: this._t(x), p: this._p(y) }); if (d && sp.t != null && sp.p != null) { const h = this._handlePoints(d)[this.dragHandle.hi]; if (h) h.set(sp.t, sp.p); this.render(); } return;
      }
      // جابه‌جایی کل آبجکت
      if (this.dragMove) {
        const d = this.drawings[this.dragMove.idx]; const sp = this._snap({ t: this._t(x), p: this._p(y) });
        const sx = x - this.dragMove.xy.x, sy = y - this.dragMove.xy.y; this.dragMove.xy = { x, y };
        if (this.dragMove.snap && (sx || sy)) { this._history.record(this.dragMove.snap); this.dragMove.snap = null; }
        const moveOne = (dd, dt, dp) => { if (extScreen(dd.type)) { dd.ax = (dd.ax || 0) + sx / (cv.width || 1); dd.ay = (dd.ay || 0) + sy / (cv.height || 1); } else if (dt != null) this._moveBy(dd, dt, dp); };
        const ok = sp.t != null && sp.p != null && this.dragMove.last && this.dragMove.last.t != null && this.dragMove.last.p != null;
        const dt = ok ? sp.t - this.dragMove.last.t : null, dp = ok ? sp.p - this.dragMove.last.p : null;
        if (d) { if (this.multiSel.size > 1 && this.multiSel.has(this.dragMove.idx)) { this.multiSel.forEach((i) => { const dd = this.drawings[i]; if (dd && !dd.locked) moveOne(dd, dt, dp); }); } else moveOne(d, dt, dp); }
        if (ok) this.dragMove.last = sp; else if (sp.t != null && sp.p != null) this.dragMove.last = sp;
        this.render(); return;
      }
      // hover-highlight (سبک TV): ترسیم زیر نشانگر را با دستگیره‌های کم‌رنگ برجسته کن. hit-test از قبل روی هر
      // mousemove اجرا می‌شود، پس render فقط روی «تغییر» hover اجرا می‌شود (ارزان). فقط نشانگر/انتخاب و نه حین رسم.
      if ((this.tool === 'cursor' || this.tool === 'select') && !this.pending) {
        const hv = this._hit(x, y);
        if (hv !== this.hover) { this.hover = hv; this.render(); }
      }
      const sp = this._snap({ t: this._t(x), p: this._p(y) });
      // #۱۱ در حالت cursor هم ترسیم‌ها قابل ویرایش‌اند: وقتی کرسر روی یک ترسیم/دستگیره/خط سفارش
      // است، لایه‌ی ترسیم را موقتاً فعال می‌کنیم تا کلیک=انتخاب/ویرایش؛ در فضای خالی غیرفعال تا چارت
      // اسکرول/زوم کند. (move روی window است، پس حتی با pointerEvents=none هم اجرا می‌شود.)
      if ((this.tool === 'cursor' || this.tool === 'eraser') && !this.dragging && !this.dragMove && !this.dragHandle && !this.dragOrder) {
        const over = this._interactiveAt(x, y);
        const want = (over || (e.shiftKey && this.tool === 'cursor')) ? 'auto' : 'none'; // Shift ⇒ کانوس رویدادها را بگیرد تا Shift+درگ اندازه‌گیری کار کند
        if (cv.style.pointerEvents !== want) cv.style.pointerEvents = want;
        cv.style.cursor = this.tool === 'eraser' ? this.eraserCursor : over ? 'pointer' : 'default';
        if (this.tool === 'eraser') { const hv = over ? this._hit(x, y) : -1; if (hv !== this.hover) { this.hover = hv; this.render(); } }
      }
      // hover برای cursor (تغییر نشانگر)
      if (this.tool === 'select' && !this.dragging) { const sd = this.selected >= 0 ? this.drawings[this.selected] : null; const onH = sd && !sd.locked && this._hitHandle(sd, x, y) >= 0; const h = this._hit(x, y); cv.style.cursor = onH ? 'crosshair' : (h >= 0 ? 'move' : 'default'); this.hover = h; }
      if (this.pending && sp.t != null && sp.p != null) { this.pending.preview = sp; this.render(); return; }
      // پیش‌نمایش حالت کلیک-کلیک: خط موقت از نقطه‌ی اول تا مکان‌نما دنبال می‌شود.
      if (this.twoClick && this.tmp) { if (sp.t != null && sp.p != null) { this.tmp.p1 = e.shiftKey ? this._constrainDraw(this.tmp.type, this.tmp.p0, sp) : sp; this.render(); } return; }
      if (!this.dragging || !this.tmp) return;
      if (sp.t != null && sp.p != null) {
        this.tmp.p1 = e.shiftKey ? this._constrainDraw(this.tmp.type, this.tmp.p0, sp) : sp;
        if (this.measuring) this.tmp.color = sp.p >= this.tmp.p0.p ? '#089981' : '#f23645'; // سبز صعودی / قرمز نزولی مثل اندازه‌گیری TV
        if (this.downXY && Math.hypot(x - this.downXY.x, y - this.downXY.y) > 4) this.didDrag = true; // حرکت محسوس ⇒ کشیدن
        this.render();
      }
    };
    const up = () => {
      stopEdgePan(); // پایان کشیدن ⇒ توقف اسکرول خودکار لبه
      // پایان freehand: زیر ۲ نقطه یعنی کلیک ساده — چیزی ثبت نمی‌شود.
      if (this.freehand) {
        const fh = this.freehand; this.freehand = null;
        if (fh.pts.length >= 2) {
          this._pushUndo();
          this.drawings.push(this._applyDef({ type: fh.type, pts: fh.pts, color: fh.color }));
          this.selected = this.drawings.length - 1;
          this._changed();
        }
        this.render();
        return;
      }
      // پایان Marquee: هر ترسیمی که لنگرش داخل مستطیل است انتخاب شود (چند-انتخاب).
      if (this.marquee) {
        const m = this.marquee; this.marquee = null;
        if (Math.abs(m.x1 - m.x0) > 4 || Math.abs(m.y1 - m.y0) > 4) {
          const sel = new Set();
          this.drawings.forEach((d, i) => { if (d && !d.locked && this._inRect(d, m.x0, m.y0, m.x1, m.y1)) sel.add(i); });
          if (sel.size <= 1) { this.selected = sel.size ? [...sel][0] : -1; this.multiSel = new Set(); }
          else { this.multiSel = sel; this.selected = [...sel][0]; }
          this.onSelect && this.onSelect(this.selected);
        }
        this.render(); return;
      }
      if (this.zoomBox) {
        const z = this.zoomBox; this.zoomBox = null; this.render();
        if (Math.abs(z.x1 - z.x0) > 6 && Math.abs(z.y1 - z.y0) > 6 && this.onZoomBox) this.onZoomBox({ x0: Math.min(z.x0, z.x1), y0: Math.min(z.y0, z.y1), x1: Math.max(z.x0, z.x1), y1: Math.max(z.y0, z.y1) });
        if (Math.abs(z.x1 - z.x0) > 6 && Math.abs(z.y1 - z.y0) > 6) this._reset();
        return;
      }
      if (this.dragOrder) { this.dragOrder = null; return; }
      if (this.dragHandle) { const moved = !this.dragHandle.snap; this.dragHandle = null; if (moved) this._changed(); else this.render(); return; }
      if (this.dragMove) { const moved = !this.dragMove.snap; this.dragMove = null; if (moved) this._changed(); else this.render(); return; }
      // اندازه‌گیری گذرا (Shift+درگ سبک TV): با رهاکردن پاک می‌شود (ثبت نمی‌شود).
      if (this.measuring) { this.measuring = false; this.dragging = false; this.tmp = null; this.didDrag = false; this.render(); return; }
      if (this.tmp && this.dragging) {
        if (this.didDrag) { this._pushUndo(); const nd = this._applyDef(this.tmp); extInit(nd, this); this.drawings.push(nd); this.tmp = null; this.dragging = false; this.didDrag = false; this._created(); } // کشیده شد ⇒ ثبت
        else { this.dragging = false; this.twoClick = true; this.render(); } // فقط کلیک شد ⇒ منتظر کلیک دوم (مثل TV)
      }
    };
    const key = (e) => {
      const tag = e.target && e.target.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target && e.target.isContentEditable)) return;
      if (e.key === 'Enter' && this.pending && EXT_POLY.includes(this.pending.type)) { e.preventDefault(); this._finishPoly && this._finishPoly(); return; }
    };
    // پایان‌دادن خط چندتکه (دابل‌کلیک یا Enter). دابل‌کلیک دو mousedown می‌زند ⇒ رأس تکراری انتهایی حذف می‌شود.
    const finishPoly = () => {
      if (!this.pending || !EXT_POLY.includes(this.pending.type)) return;
      const t = this.pending.type;
      const pts = this.pending.pts.slice();
      const L = pts.length;
      if (L >= 2 && pts[L - 1].t === pts[L - 2].t && pts[L - 1].p === pts[L - 2].p) pts.pop();
      this.pending = null;
      if (pts.length >= 2) { this._pushUndo(); this.drawings.push(this._applyDef({ type: t, pts, color: this.color, width: 2 })); this._created(); }
      else this._reset();
    };
    const dbl = (e) => {
      // اگر خط چندتکه/مسیر در حال ترسیم است ⇒ پایان بده (رفتار قبلی).
      if (this.pending && EXT_POLY.includes(this.pending.type)) { finishPoly(); return; }
      // دابل‌کلیک روی یک ترسیم (در حالت نشانگر/انتخاب) ⇒ انتخاب + ویرایش (متن: ویرایش محتوا) — سبک TV.
      if (this.tool !== 'cursor' && this.tool !== 'select') return;
      try {
        const r = cv.getBoundingClientRect(); const x = e.clientX - r.left, y = e.clientY - r.top;
        const idx = this._hit(x, y);
        if (idx >= 0) { this.selected = idx; this.render(); this.onSelect && this.onSelect(idx); this.onDblEdit && this.onDblEdit(idx); }
      } catch (err) { /* noop */ }
    };
    // چرخ ماوس حین ترسیم: چون در حالت ابزار ترسیم canvas پوینتراونت‌ها را می‌گیرد (pointerEvents=auto)،
    // اسکرول/زوم چارت زیرین بلاک می‌شد و کاربر نمی‌توانست حین رسم به کندل‌های دیگر برود.
    // این هندلر چرخ را به timeScale چارت فوروارد می‌کند (افقی=پن، عمودی=زوم حول نشانگر) تا مثل TV بتوان
    // حین رسم چارت را جابه‌جا/زوم کرد و ابزار را تا هر کندل دلخواه برد. در حالت نشانگر/انتخاب کاری نمی‌کند
    // (آنجا pointerEvents=none است و چارت خودش چرخ را می‌گیرد).
    const wheel = (e) => {
      if (this.tool === 'cursor' || this.tool === 'select') return;
      let ts; try { ts = this.chart.timeScale(); } catch (err) { return; }
      const lr = ts.getVisibleLogicalRange && ts.getVisibleLogicalRange();
      if (!lr) return;
      e.preventDefault(); e.stopPropagation();
      const span = Math.max(1, lr.to - lr.from);
      const horizontal = e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY);
      try {
        if (horizontal) {
          // پن افقی: پنجره‌ی منطقی را چپ/راست ببر (مثل اسکرول افقی TV) — اجازه‌ی رفتن به هر کندل لود‌شده.
          const raw = e.deltaX || e.deltaY;
          const shift = Math.sign(raw) * Math.max(1, span * 0.12);
          ts.setVisibleLogicalRange({ from: lr.from + shift, to: lr.to + shift });
        } else {
          // زوم حول نشانگر (مثل چرخ عمودی چارت).
          const rect = cv.getBoundingClientRect();
          const x = e.clientX - rect.left;
          const L = ts.coordinateToLogical ? ts.coordinateToLogical(x) : (lr.from + lr.to) / 2;
          const pivot = (L == null || !Number.isFinite(L)) ? (lr.from + lr.to) / 2 : L;
          const zoom = e.deltaY > 0 ? 1.1 : 0.9; // پایین=دورشدن، بالا=نزدیک‌شدن
          const from = pivot - (pivot - lr.from) * zoom;
          const to = pivot + (lr.to - pivot) * zoom;
          if (to - from >= 2) ts.setVisibleLogicalRange({ from, to });
        }
        this.render();
      } catch (err) { /* noop */ }
    };
    this._finishPoly = finishPoly;
    // Pointer events, so a mouse, a pen and a finger all draw. The host may hand a press on the
    // chart to the layer itself (a finger on a drawing in cursor mode) through _down.
    this._down = down;
    cv.addEventListener('pointerdown', down);
    cv.addEventListener('dblclick', dbl);
    cv.addEventListener('wheel', wheel, { passive: false });
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
    window.addEventListener('keydown', key);
    this._cleanup = () => { stopEdgePan(); cv.removeEventListener('pointerdown', down); cv.removeEventListener('dblclick', dbl); cv.removeEventListener('wheel', wheel); window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up); window.removeEventListener('keydown', key); };
  }

  // #۱۱ آیا نقطه‌ی (x,y) روی چیزی قابل‌تعامل است؟ (خط سفارش، دستگیره‌ی آبجکت انتخاب‌شده، یا بدنه‌ی یک ترسیم)
  _interactiveAt(x, y) {
    if (this.order) { for (const k of ['entry', 'sl', 'tp']) { const yy = this._y(this.order[k]); if (yy != null && Math.abs(yy - y) < 7) return true; } }
    if (this.selected >= 0) { const sd = this.drawings[this.selected]; if (sd && !sd.locked && this._hitHandle(sd, x, y) >= 0) return true; }
    return this._hit(x, y) >= 0;
  }

  // hit-test: نزدیک‌ترین ترسیم به نقطه‌ی کلیک (یا -1)
  _hit(x, y) {
    const near = 7;
    const pb = this._paneBox; if (pb && pb.w && (x > pb.w || y > pb.h)) return -1; // the axes belong to the chart
    for (let i = this.drawings.length - 1; i >= 0; i--) {
      const d = this.drawings[i];
      if (!d || d.visible === false) continue; // شی مخفی (toggleVisible) نباید کلیک‌پذیر باشد
      if (this._tfHidden(d)) continue; // ترسیم پنهان‌شده در این کلاس تایم‌فریم قابل انتخاب نیست (#271)
      if (isExt(d.type)) { if (extHit(d, x, y, this)) return i; continue; }
      if (d.type === 'hline') { const yy = this._y(d.p); if (yy != null && Math.abs(yy - y) < near) return i; continue; }
      if (d.type === 'vline') { const xx = this._x(d.t); if (xx != null && Math.abs(xx - x) < near) return i; continue; }
      // متن: جعبه‌ی تقریبی حول لنگر (t,p) — قبلاً هیچ شاخه‌ای نداشت و انتخاب/راست‌کلیک متن کار نمی‌کرد.
      if (d.type === 'text') { if (hitTextBlock(d, x, y)) return i; const tx = this._x(d.t), ty = this._y(d.p); if (tx == null || ty == null) continue; const fs = Math.max(8, Math.min(64, d.fontSize || 12)); const tlines = String(d.text || '').replace(/\\n/g, '\n').split('\n'); const maxLen = tlines.reduce((a, s) => Math.max(a, s.length), 0); const lh = Math.round(fs * 1.3); const hw = Math.max(24, maxLen * fs * 0.4); if (x >= tx - hw && x <= tx + hw && y >= ty - fs - 4 && y <= ty + 5 + (tlines.length - 1) * lh) return i; continue; }
      // ابزارهای پایه‌ی چندنقطه‌ای (channel/pitchfork) که از pts استفاده می‌کنند نه p0/p1 — قبلاً در _hit می‌افتادند (مثل باگ متن) و انتخاب/راست‌کلیک نمی‌شدند.
      if (Array.isArray(d.pts) && d.pts.length) {
        const P = d.pts.map((pt) => ({ x: this._x(pt.t), y: this._y(pt.p) })).filter((q) => q.x != null && q.y != null);
        const segHit = (a, bp) => { const A = x - a.x, B = y - a.y, C = bp.x - a.x, D = bp.y - a.y; const dot = A * C + B * D, len = C * C + D * D; const tt = len ? Math.max(0, Math.min(1, dot / len)) : 0; const px = a.x + tt * C, py = a.y + tt * D; return Math.hypot(x - px, y - py) < near; };
        let hitPts = false;
        for (let k = 0; k < P.length && !hitPts; k++) {
          if (Math.hypot(x - P[k].x, y - P[k].y) < near + 3) hitPts = true;
          else if (k > 0 && segHit(P[k - 1], P[k])) hitPts = true;
        }
        // خط میانه‌ی چنگال (pitchfork): از نقطه‌ی ۰ تا میانه‌ی نقاط ۱و۲ — خط نمایشی اصلی که پاره‌خط کنترل نیست.
        if (!hitPts && d.type === 'pitchfork' && P.length >= 3) { const mid = { x: (P[1].x + P[2].x) / 2, y: (P[1].y + P[2].y) / 2 }; if (segHit(P[0], mid)) hitPts = true; }
        // خط موازی کانال (channel): از نقطه‌ی ۲ موازی با p0→p1 — خط نمایشی دوم که پاره‌خط کنترل نیست.
        if (!hitPts && d.type === 'channel' && P.length >= 3) { const q2 = { x: P[2].x + (P[1].x - P[0].x), y: P[2].y + (P[1].y - P[0].y) }; if (segHit(P[2], q2)) hitPts = true; }
        if (hitPts) return i; continue;
      }
      if (d.type === 'longshort' || d.type === 'short') {
        const g = this._posGeom(d); if (!g) continue;
        const ysP = [g.yE, g.yS, g.yT].filter((v) => v != null); if (!ysP.length) continue;
        if (x >= g.xa - near && x <= g.xb + near && y >= Math.min(...ysP) - near && y <= Math.max(...ysP) + near) return i;
        continue;
      }
      const x0 = this._x(d.p0 && d.p0.t), y0 = this._y(d.p0 && d.p0.p), x1 = this._x(d.p1 && d.p1.t), y1 = this._y(d.p1 && d.p1.p);
      if (x0 == null || x1 == null) continue;
      if ((d.type === 'fib' || d.type === 'rect') && (d.extendLeft || d.extendRight)) {
        const W = this.canvas.width, xl = d.extendLeft ? 0 : Math.min(x0, x1), xr = d.extendRight ? W : Math.max(x0, x1);
        if (x >= xl - near && x <= xr + near && y >= Math.min(y0, y1) - near && y <= Math.max(y0, y1) + near) return i;
        continue;
      }
      if (d.type === 'rect' || d.type === 'fib') {
        if (x >= Math.min(x0, x1) - near && x <= Math.max(x0, x1) + near && y >= Math.min(y0, y1) - near && y <= Math.max(y0, y1) + near) return i;
      } else { // خط: فاصله تا پاره‌خط
        const A = x - x0, B = y - y0, C = x1 - x0, D = y1 - y0; const dot = A * C + B * D, len = C * C + D * D;
        // ray از p0 در جهت p1 تا امتداد (رندر ۴۰۰۰×) کشیده می‌شود؛ پس سقف t را clamp نکن تا دُم امتدادیافته هم قابل‌انتخاب باشد (مثل TV). بقیه‌ی خطوط پاره‌خط محدود [۰,۱].
        const fwd = d.type === 'ray' || d.extendRight, back = !!d.extendLeft;
        const t = len ? Math.max(back ? -Infinity : 0, Math.min(fwd ? Infinity : 1, dot / len)) : 0;
        const px = x0 + t * C, py = y0 + t * D; if (Math.hypot(x - px, y - py) < near) return i;
      }
    }
    return -1;
  }

  _reset() { this.twoClick = false; this.didDrag = false; if (!this.stayInMode) { this.tool = 'cursor'; this.canvas.style.pointerEvents = 'none'; this.canvas.style.touchAction = ''; this.canvas.style.cursor = 'default'; this.onToolReset && this.onToolReset(); } this._changed(); }
  destroy() { this._cleanup && this._cleanup(); }

  resize(w, h) { this.canvas.width = w; this.canvas.height = h; this.render(); }

  _barWidth() { try { const o = this.chart.timeScale().options(); if (o && o.barSpacing) return Math.max(2, o.barSpacing); } catch (e) { /* */ } return 6; }

  // رندر گرافیک نمااسکریپت روی کانواس (با مختصات زنده‌ی چارت؛ خودکار با پن/زوم به‌روز می‌شود)
  _paintScript(ctx, W, H) {
    const s = this.script; if (!s) return;
    const bw = this._barWidth();
    // bgcolor — نوار عمودی تمام‌ارتفاع روی کندل‌های شرط
    (s.bgs || []).forEach((g) => { ctx.fillStyle = g.color || 'rgba(59,130,246,.10)'; (g.bars || []).forEach((t) => { const x = this._x(t); if (x == null) return; ctx.fillRect(x - bw / 2, 0, Math.max(1, bw), H); }); });
    // fill — ناحیه‌ی پُرشده بین دو سری
    (s.fills || []).forEach((f) => {
      const A = f.a || [], B = f.b || []; if (A.length < 2 || B.length < 2) return;
      ctx.beginPath(); let started = false;
      for (let i = 0; i < A.length; i++) { const x = this._x(A[i].time), y = this._y(A[i].value); if (x == null || y == null) continue; if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y); }
      for (let i = B.length - 1; i >= 0; i--) { const x = this._x(B[i].time), y = this._y(B[i].value); if (x == null || y == null) continue; ctx.lineTo(x, y); }
      ctx.closePath(); ctx.fillStyle = f.color || 'rgba(59,130,246,.12)'; ctx.fill();
    });
    // box.new
    (s.boxes || []).forEach((b) => { const x1 = this._x(b.left), x2 = this._x(b.right), y1 = this._y(b.top), y2 = this._y(b.bottom); if (x1 == null || x2 == null || y1 == null || y2 == null) return; const X = Math.min(x1, x2), Y = Math.min(y1, y2), w = Math.abs(x2 - x1), h = Math.abs(y2 - y1); ctx.fillStyle = b.bg || 'rgba(59,130,246,.08)'; ctx.fillRect(X, Y, w, h); ctx.strokeStyle = b.color || '#3b82f6'; ctx.lineWidth = 1; ctx.strokeRect(X, Y, w, h); });
    // line.new
    (s.lines || []).forEach((l) => { const x1 = this._x(l.x1), x2 = this._x(l.x2), y1 = this._y(l.y1), y2 = this._y(l.y2); if (x1 == null || x2 == null || y1 == null || y2 == null) return; ctx.strokeStyle = l.color || '#3b82f6'; ctx.lineWidth = l.width || 1; if (l.style === 2) ctx.setLineDash([5, 3]); ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke(); ctx.setLineDash([]); });
    // barcolor — نقطه‌ی رنگی کوچک زیر کندل‌های شرط
    (s.barcolors || []).forEach((bc) => { if (bc.all) return; ctx.fillStyle = bc.color || '#f59e0b'; (bc.bars || []).forEach((t) => { const x = this._x(t); if (x == null) return; ctx.beginPath(); ctx.arc(x, H - 8, 2.5, 0, 7); ctx.fill(); }); });
  }

  // The main pane's area: the canvas less the price axis on the right, the time axis at the bottom
  // and any indicator panes under the price (TradingView keeps drawings inside their pane).
  _pane() {
    const W = this.canvas.width, H = this.canvas.height;
    let pw = 0, th = 0, ph = null;
    try { pw = this.chart.priceScale('right').width() || 0; } catch (e) { pw = 0; }
    try { th = this.chart.timeScale().height() || 0; } catch (e) { th = 0; }
    try { const ps = this.chart.panes && this.chart.panes(); if (ps && ps.length > 1) ph = ps[0].getHeight(); } catch (e) { ph = null; }
    const h = ph != null ? Math.min(ph, H - th) : H - th;
    return { w: Math.max(0, W - pw), h: Math.max(0, h), pw, th, W, H };
  }

  render() {
    const ctx = this.ctx; if (!ctx) return;
    const W = this.canvas.width, H = this.canvas.height;
    ctx.clearRect(0, 0, W, H);
    const pane = this._pane();
    this._paneBox = pane;
    ctx.save(); ctx.beginPath(); ctx.rect(0, 0, pane.w, pane.h); ctx.clip();
    // Volume Profile (نوارهای افقی سمت راست)
    if (this.profile && this.profile.length) {
      const maxV = Math.max(...this.profile.map((b) => b.vol)) || 1;
      const maxW = Math.min(160, W * 0.18);
      this.profile.forEach((b) => {
        const y = this._y((b.lo + b.hi) / 2); if (y == null) return;
        const yh = this._y(b.hi), yl = this._y(b.lo); const h = (yh != null && yl != null) ? Math.max(2, Math.abs(yl - yh) - 1) : 4;
        const w = (b.vol / maxV) * maxW;
        ctx.fillStyle = b.poc ? 'rgba(245,158,11,.55)' : (b.va ? 'rgba(59,130,246,.42)' : 'rgba(59,130,246,.18)');
        ctx.fillRect(pane.w - w, y - h / 2, w, h);
      });
    }
    if (this.script) this._paintScript(ctx, W, H);
    // freehand حین کشیدن مثل tmp پیش‌نمایش می‌شود — وگرنه کاربر تا رهاکردن ماوس
    // هیچ‌چیز نمی‌بیند و فکر می‌کند ابزار خراب است.
    const _live = this.tmp || this.freehand || null;
    const all = _live ? [...this.drawings, _live] : this.drawings;
    all.forEach((d, i) => { if (d !== _live && this._tfHidden(d)) return; this._draw(ctx, d, i === this.selected || this.multiSel.has(i)); if (i === this.selected || this.multiSel.has(i)) this._drawHandles(ctx, d); else if (d !== this.tmp && i === this.hover && d.visible !== false) this._drawHandles(ctx, d, 0.4); });
    // مستطیل Marquee (حین کشیدن) — کادر خط‌چین آبی با پُر نیمه‌شفاف، سبک TV.
    if (this.marquee) { const m = this.marquee; const rx = Math.min(m.x0, m.x1), ry = Math.min(m.y0, m.y1), rw = Math.abs(m.x1 - m.x0), rh = Math.abs(m.y1 - m.y0); ctx.save(); ctx.fillStyle = 'rgba(59,130,246,.10)'; ctx.strokeStyle = 'rgba(59,130,246,.9)'; ctx.lineWidth = 1; ctx.setLineDash([4, 3]); ctx.fillRect(rx, ry, rw, rh); ctx.strokeRect(rx, ry, rw, rh); ctx.restore(); }
    if (this.pending) {
      const pts = this.pending.preview ? [...this.pending.pts, this.pending.preview] : this.pending.pts;
      this._draw(ctx, { type: this.pending.type, pts, color: this.pending.color });
      ctx.fillStyle = this.pending.color;
      this.pending.pts.forEach((p) => { const x = this._x(p.t), y = this._y(p.p); if (x != null && y != null) { ctx.beginPath(); ctx.arc(x, y, 3, 0, 7); ctx.fill(); } });
    }
    if (this.order) this._drawOrder(ctx);
    ctx.restore();
    this._axisTags(ctx, pane);
    // The Zoom-in tool's rectangle while it is dragged.
    if (this.zoomBox) { const z = this.zoomBox; ctx.save(); ctx.fillStyle = 'rgba(41,98,255,0.10)'; ctx.strokeStyle = 'rgba(41,98,255,0.9)'; ctx.lineWidth = 1; ctx.setLineDash([4, 3]); ctx.fillRect(Math.min(z.x0, z.x1), Math.min(z.y0, z.y1), Math.abs(z.x1 - z.x0), Math.abs(z.y1 - z.y0)); ctx.strokeRect(Math.min(z.x0, z.x1) + 0.5, Math.min(z.y0, z.y1) + 0.5, Math.abs(z.x1 - z.x0), Math.abs(z.y1 - z.y0)); ctx.restore(); }
    this._drawAxisLabels(ctx, W, H);
  }

  // The anchors of a drawing as {t?, p?}: a horizontal line has only a price, a vertical one only a time.
  _anchors(d) {
    if (!d) return [];
    if (extScreen(d.type)) return [];
    if (d.type === 'hline') return [{ p: d.p }];
    if (d.type === 'vline') return [{ t: d.t }];
    if (d.type === 'text') return [{ t: d.t, p: d.p }];
    if ((d.type === 'longshort' || d.type === 'short') && d.p0 && d.p1) { const g = this._posPrices(d); return [{ t: d.p0.t, p: g.entry }, { p: g.stop }, { p: g.target }, { t: d.p1.t }]; }
    if (d.type === 'barspattern' && d.dst) return [{ t: d.dst.t, p: d.dst.p }];
    if (Array.isArray(d.pts)) return d.pts.map((q) => ({ t: q.t, p: q.p }));
    if (EXT_ONE.includes(d.type)) return d.p0 ? [d.type === 'pricelabel' ? { p: d.p0.p } : { t: d.p0.t, p: d.p0.p }] : [];
    const out = [];
    if (d.p0) out.push({ t: d.p0.t, p: d.p0.p });
    if (d.p1) out.push({ t: d.p1.t, p: d.p1.p });
    return out;
  }

  // Every horizontal line's price on the price axis and every vertical line's date on the time axis
  // (a cross line has both), in the drawing's colour — what TradingView keeps on its axes.
  _axisTags(ctx, pane) {
    const { W, H, pw, th } = pane;
    ctx.save(); ctx.setLineDash([]); ctx.font = '11px IRANYekanX, sans-serif'; ctx.textBaseline = 'middle';
    const priceTag = (y, txt, bg) => {
      if (y == null || y < 0 || y > pane.h) return;
      const tw = ctx.measureText(txt).width, h = 18;
      const x = pw ? W - pw : W - tw - 10, w = pw || tw + 8;
      ctx.fillStyle = bg; ctx.fillRect(x, y - h / 2, w, h);
      ctx.fillStyle = '#fff'; ctx.textAlign = 'left'; ctx.fillText(txt, x + 6, y);
    };
    const timeTag = (x, txt, bg) => {
      if (x == null || !txt || x < 0 || x > pane.w) return;
      const w = ctx.measureText(txt).width + 12, h = th ? Math.min(th - 1, 22) : 16;
      const left = Math.max(0, Math.min(pane.w - w, x - w / 2)), top = th ? H - th + 1 : H - h - 1;
      ctx.fillStyle = bg; ctx.fillRect(left, top, w, h);
      ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.fillText(txt, left + w / 2, top + h / 2);
    };
    const tf = (t) => { try { return this.timeFmt ? String(this.timeFmt(t) || '') : ''; } catch (e) { return ''; } };
    for (const d of this.drawings) {
      if (!d || d.visible === false || this._tfHidden(d)) continue;
      const col = d.color || '#2962FF';
      if (d.type === 'hline' && d.p != null) priceTag(this._y(d.p), this._fmtP(d.p), col);
      else if (d.type === 'vline' && d.t != null) timeTag(this._x(d.t), tf(d.t), col);
      else if (d.type === 'crossline' && d.p0) { priceTag(this._y(d.p0.p), this._fmtP(d.p0.p), col); timeTag(this._x(d.p0.t), tf(d.p0.t), col); }
    }
    ctx.restore();
  }

  // TradingView's axis labels for the selected drawing: each anchor's price on the price axis and
  // its time on the time axis, with the span between them shaded.
  _drawAxisLabels(ctx, W, H) {
    if (this.selected < 0 || this.multiSel.size > 1) return;
    const d = this.drawings[this.selected];
    if (!d || d.visible === false || this._tfHidden(d)) return;
    let pw = 0, th = 0;
    try { pw = this.chart.priceScale('right').width(); th = this.chart.timeScale().height(); } catch (e) { return; }
    if (!pw && !th) return;
    const an = this._anchors(d);
    if (!an.length) return;
    const ys = [], xs = [];
    ctx.save();
    ctx.font = '11px IRANYekanX, sans-serif'; ctx.textBaseline = 'middle'; ctx.setLineDash([]);
    const pane = this._paneBox ? this._paneBox.h : H - th;
    for (const a of an) {
      if (a.p != null) { const y = this._y(a.p); if (y != null && y >= 0 && y <= pane) ys.push({ y, p: a.p }); }
      if (a.t != null) { const x = this._x(a.t); if (x != null && x >= 0 && x <= W - pw) xs.push({ x, t: a.t }); }
    }
    const blue = '#2962FF';
    if (pw && ys.length > 1) { const a = Math.min(...ys.map((q) => q.y)), b = Math.max(...ys.map((q) => q.y)); ctx.fillStyle = 'rgba(41,98,255,0.18)'; ctx.fillRect(W - pw, a, pw, b - a); }
    if (th && xs.length > 1) { const a = Math.min(...xs.map((q) => q.x)), b = Math.max(...xs.map((q) => q.x)); ctx.fillStyle = 'rgba(41,98,255,0.18)'; ctx.fillRect(a, pane, b - a, th); }
    if (pw) for (const q of ys) {
      const txt = this._fmtP(q.p); const h = 18;
      ctx.fillStyle = blue; ctx.fillRect(W - pw, q.y - h / 2, pw, h);
      ctx.fillStyle = '#fff'; ctx.textAlign = 'left'; ctx.fillText(txt, W - pw + 6, q.y);
    }
    if (th && this.timeFmt) for (const q of xs) {
      let txt = ''; try { txt = String(this.timeFmt(q.t) || ''); } catch (e) { txt = ''; }
      if (!txt) continue;
      const w = ctx.measureText(txt).width + 12, h = Math.min(th, 22);
      const x = Math.max(0, Math.min(W - pw - w, q.x - w / 2));
      ctx.fillStyle = blue; ctx.fillRect(x, pane + 1, w, h);
      ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.fillText(txt, x + w / 2, pane + 1 + h / 2);
    }
    ctx.restore();
  }

  _drawOrder(ctx) {
    const W = this.canvas.width;
    const o = this.order;
    const yE = this._y(o.entry), yS = this._y(o.sl), yT = this._y(o.tp);
    ctx.font = '11px IRANYekanX, sans-serif'; ctx.lineWidth = 1;
    const risk = Math.abs(o.entry - o.sl), reward = Math.abs(o.tp - o.entry);
    const rr = risk ? (reward / risk).toFixed(2) : '—';
    // نواحی
    if (yE != null && yT != null) { ctx.fillStyle = 'rgba(34,197,94,.10)'; ctx.fillRect(0, Math.min(yE, yT), W, Math.abs(yE - yT)); }
    if (yE != null && yS != null) { ctx.fillStyle = 'rgba(239,68,68,.10)'; ctx.fillRect(0, Math.min(yE, yS), W, Math.abs(yE - yS)); }
    const line = (y, col, label) => { if (y == null) return; ctx.strokeStyle = col; ctx.setLineDash([5, 3]); ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke(); ctx.setLineDash([]); ctx.fillStyle = col; ctx.fillRect(4, y - 7, 14, 14); ctx.fillStyle = '#fff'; ctx.fillText('⇕', 6, y + 4); ctx.fillStyle = col; ctx.fillText(label, 22, y - 3); };
    line(yT, '#22c55e', `هدف ${o.tp.toFixed(this.digits)}`);
    line(yE, '#3b82f6', `${o.side === 'buy' ? 'خرید' : 'فروش'} ${o.entry.toFixed(this.digits)}  R:R ${rr}`);
    line(yS, '#ef4444', `حد ضرر ${o.sl.toFixed(this.digits)}`);
  }

  _drawHandles(ctx, d, alpha) {
    const hs = this._handlePoints(d);
    if (alpha != null) { ctx.save(); ctx.globalAlpha = alpha; }
    hs.forEach((h) => { if (h.x == null || h.y == null) return; ctx.fillStyle = '#fff'; ctx.strokeStyle = d.locked ? '#94a3b8' : '#2962FF'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(h.x, h.y, alpha != null ? 4 : 5, 0, 7); ctx.fill(); ctx.stroke(); });
    if (alpha != null) ctx.restore();
  }

  _draw(ctx, d, sel = false) {
    if (d.visible === false) return;
    if (isExt(d.type)) { extDraw(ctx, d, this); return; }
    const W = this.canvas.width, H = this.canvas.height;
    ctx.lineWidth = d.width || 2; ctx.strokeStyle = d.color; ctx.fillStyle = d.color; ctx.font = '12px IRANYekanX, sans-serif';
    // سبک خط: solid/dashed/dotted (سبک TV) — سازگار عقب‌رو با فلگ قدیمی d.dashed.
    ctx.setLineDash(d.lineStyle === 'dotted' ? [2, 3] : ((d.lineStyle === 'dashed' || d.dashed) ? [6, 4] : []));
    // ابزارهای چندنقطه‌ای
    if (d.type === 'channel' && d.pts) {
      const P = d.pts.map((p) => ({ x: this._x(p.t), y: this._y(p.p) }));
      if (P.some((p) => p.x == null || p.y == null) || P.length < 2) return;
      ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.stroke();
      if (P.length >= 3) { const dx = P[1].x - P[0].x, dy = P[1].y - P[0].y; const ox = P[2].x - P[0].x, oy = P[2].y - P[0].y;
        ctx.beginPath(); ctx.moveTo(P[0].x + ox, P[0].y + oy); ctx.lineTo(P[1].x + ox, P[1].y + oy); ctx.stroke();
        ctx.globalAlpha = 0.15; ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[1].x, P[1].y); ctx.lineTo(P[1].x + ox, P[1].y + oy); ctx.lineTo(P[0].x + ox, P[0].y + oy); ctx.closePath(); ctx.fill(); ctx.globalAlpha = 1; }
      return;
    }
    if (d.type === 'pitchfork' && d.pts) {
      const P = d.pts.map((p) => ({ x: this._x(p.t), y: this._y(p.p) }));
      if (P.some((p) => p.x == null || p.y == null) || P.length < 2) return;
      if (P.length >= 3) { const mx = (P[1].x + P[2].x) / 2, my = (P[1].y + P[2].y) / 2;
        const dx = mx - P[0].x, dy = my - P[0].y; const k = 3;
        ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[0].x + dx * k, P[0].y + dy * k); ctx.stroke(); // مدین
        [P[1], P[2]].forEach((pp) => { ctx.globalAlpha = 0.9; ctx.beginPath(); ctx.moveTo(pp.x, pp.y); ctx.lineTo(pp.x + dx * k, pp.y + dy * k); ctx.stroke(); ctx.globalAlpha = 1; });
      } else { ctx.beginPath(); ctx.moveTo(P[0].x, P[0].y); ctx.lineTo(P[P.length - 1].x, P[P.length - 1].y); ctx.stroke(); }
      return;
    }
    if (d.type === 'fibext') {
      const xx0 = this._x(d.p0 && d.p0.t), xx1 = this._x(d.p1 && d.p1.t);
      if (xx0 == null || xx1 == null) return;
      const base = d.p0.p, diff = d.p1.p - d.p0.p;
      const xa = d.extendLeft ? 0 : Math.min(xx0, xx1), xb = d.extendRight ? W : Math.max(xx0, xx1) + 30;
      normLevels(d, 'fibext').filter((l) => l.on).forEach((l) => { const pr = base + diff * l.v; const y = this._y(pr); if (y == null) return; if (d.levels) { ctx.strokeStyle = l.color; ctx.fillStyle = l.color; } ctx.globalAlpha = 0.85; ctx.beginPath(); ctx.moveTo(xa, y); ctx.lineTo(xb, y); ctx.stroke(); ctx.fillText(`${String(+l.v.toFixed(3))}  ${pr.toFixed(this.digits)}`, Math.max(xa, Math.min(xx0, xx1)) + 4, y - 2); ctx.globalAlpha = 1; });
      return;
    }
    if (d.type === 'hline') { const y = this._y(d.p); if (y == null) return; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
      drawLineText(ctx, d, 0, y, (this._paneBox ? this._paneBox.w : W), y);
      return; } // its price tag sits on the price axis (_axisTags), as on TradingView #287
    if (d.type === 'vline') { const x = this._x(d.t); if (x == null) return; ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
      drawLineText(ctx, d, x, (this._paneBox ? this._paneBox.h : H), x, 0);
      return; } // its date tag sits on the time axis (_axisTags), in the reader's time zone #281→#288
    const x0 = this._x(d.p0?.t), y0 = this._y(d.p0?.p), x1 = this._x(d.p1?.t), y1 = this._y(d.p1?.p);
    // Text: several lines, bold/italic, aligned and wrapped, on an optional background and border. #295
    if (d.type === 'text') { const x = this._x(d.t), y = this._y(d.p); if (x == null || y == null) return; drawTextBlock(ctx, d, x, y); return; }
    if (d.type === 'longshort' || d.type === 'short') { this._drawPosition(ctx, d); return; }
    if (x0 == null || y0 == null || x1 == null || y1 == null) return;
    if (d.type === 'trend' || d.type === 'ray') {
      const L = this._lineEnds(d, x0, y0, x1, y1, W, H);
      ctx.beginPath(); ctx.moveTo(L.ax, L.ay); ctx.lineTo(L.bx, L.by); ctx.stroke();
      if (d.middle) { ctx.save(); ctx.setLineDash([]); ctx.fillStyle = '#fff'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.arc((x0 + x1) / 2, (y0 + y1) / 2, 3.5, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); ctx.restore(); }
      if (d.priceLabels && d.p0 && d.p1) { for (const [px, py, pr] of [[x0, y0, d.p0.p], [x1, y1, d.p1.p]]) this._tag(ctx, px, py + (py <= Math.min(y0, y1) ? -10 : 10), this._fmtP(pr), d.color, 'center'); }
      drawLineText(ctx, d, x0, y0, x1, y1);
      // Stats (TradingView's trend-line stats): Δprice, %, bars, span — while selected, always, or never.
      const showStats = d.stats === 'always' || (sel && d.stats !== 'hidden');
      if (showStats && d.type === 'trend' && d.p0 && d.p1) {
        const dp = d.p1.p - d.p0.p, pct = d.p0.p ? (dp / d.p0.p * 100) : 0;
        const iv = this._barInterval(), bars = iv ? Math.round((d.p1.t - d.p0.t) / iv) : 0;
        const sg = dp >= 0 ? '+' : '−';
        ctx.save(); ctx.setLineDash([]); ctx.font = '11px IRANYekanX, sans-serif';
        ctx.fillText(`${sg}${Math.abs(dp).toFixed(this.digits)} (${sg}${Math.abs(pct).toFixed(2)}%) · ${Math.abs(bars)} bars`, x1 + 8, y1 - 2); // market figures: Latin, as the axis
        ctx.restore();
      }
    }
    else if (d.type === 'rect') {
      const rx = Math.min(x0, x1), ry = Math.min(y0, y1), rw = Math.abs(x1 - x0), rh = Math.abs(y1 - y0);
      const ex0 = d.extendLeft ? 0 : rx, ex1 = d.extendRight ? W : rx + rw;
      ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.18); ctx.fillStyle = d.fill || d.color; ctx.fillRect(ex0, ry, ex1 - ex0, rh); ctx.fillStyle = d.color; ctx.globalAlpha = 1; ctx.strokeRect(rx, ry, rw, rh);
      if (d.middle) { ctx.save(); ctx.globalAlpha = 0.6; ctx.setLineDash([4, 3]); ctx.beginPath(); ctx.moveTo(ex0, ry + rh / 2); ctx.lineTo(ex1, ry + rh / 2); ctx.stroke(); ctx.restore(); }
      if (d.text) { const vy = d.valign === 'top' ? ry : d.valign === 'bottom' ? ry + rh : ry + rh / 2; const vd = { ...d, valign: d.valign === 'top' ? 'bottom' : d.valign === 'bottom' ? 'top' : 'middle' }; drawLineText(ctx, vd, rx, vy, rx + rw, vy); }
    }
    else if (d.type === 'fib') {
      const top = Math.max(d.p0.p, d.p1.p), bot = Math.min(d.p0.p, d.p1.p), rng = top - bot;
      const xa = d.extendLeft ? 0 : Math.min(x0, x1), xb = d.extendRight ? W : Math.max(x0, x1);
      const lvls = normLevels(d, 'fib').filter((l) => l.on).sort((a, b) => a.v - b.v);
      // سطوح با رنگ اختصاصی هر سطح (سبک TV) + باند پس‌زمینه‌ی کم‌رنگ بین سطوح متوالی.
      const ys = lvls.map((l) => this._y(top - rng * l.v));
      lvls.forEach((l, i) => {
        const y = ys[i]; if (y == null) return; const col = l.color || d.color;
        // باند پس‌زمینه بین این سطح و سطح قبلی (رنگ همین سطح، شفافیت کم مثل Background فیب TV).
        if (d.fibBg !== false && i > 0 && ys[i - 1] != null) { ctx.globalAlpha = 0.08; ctx.fillStyle = col; ctx.fillRect(xa, Math.min(y, ys[i - 1]), xb - xa, Math.abs(y - ys[i - 1])); ctx.globalAlpha = 1; }
        const price = top - rng * l.v;
        ctx.strokeStyle = col; ctx.fillStyle = col; ctx.globalAlpha = 0.85;
        ctx.beginPath(); ctx.moveTo(xa, y); ctx.lineTo(xb, y); ctx.stroke();
        // برچسب سطح/قیمت را با شفافیت کامل بکش (خط فیب کم‌رنگ است ولی متن مثل TV واضح) — #289
        ctx.globalAlpha = 1;
        ctx.fillText(`${String(+l.v.toFixed(3))}  ${price.toFixed(this.digits)}`, Math.min(x0, x1) + 4, y - 2);
      });
    }
  }

  _tr(fa, en) { return this.lang === 'en' ? en : fa; }

  // A line from p0 to p1 extended to the edge on the sides asked for (a ray always to the right).
  _lineEnds(d, x0, y0, x1, y1, W, H) {
    const dx = x1 - x0, dy = y1 - y0, len = Math.hypot(dx, dy) || 1, K = (W + H) * 4;
    const right = d.type === 'ray' || d.extendRight, left = d.extendLeft;
    return { ax: left ? x0 - dx / len * K : x0, ay: left ? y0 - dy / len * K : y0, bx: right ? x1 + dx / len * K : x1, by: right ? y1 + dy / len * K : y1 };
  }

  // A small filled label with white text, centred on x, above / below / on y.
  _tag(ctx, x, y, text, bg, place) {
    ctx.save(); ctx.setLineDash([]); ctx.font = '11px IRANYekanX, sans-serif';
    const lines = String(text).split('\n'); let w = 0; lines.forEach((l) => { w = Math.max(w, ctx.measureText(l).width); });
    const bw = w + 12, lh = 15, bh = lines.length * lh + 6;
    const by = place === 'above' ? y - bh - 3 : place === 'below' ? y + 3 : y - bh / 2;
    ctx.fillStyle = bg; ctx.globalAlpha = 0.95;
    if (ctx.roundRect) { ctx.beginPath(); ctx.roundRect(x - bw / 2, by, bw, bh, 3); ctx.fill(); } else ctx.fillRect(x - bw / 2, by, bw, bh);
    ctx.globalAlpha = 1; ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    lines.forEach((l, i) => { ctx.direction = dirOf(l); ctx.fillText(l, x, by + 3 + lh / 2 + i * lh); });
    ctx.restore();
  }

  // Entry, stop and target of a long or short position. The target is the reader's (d.tp) once
  // dragged or typed; until then two times the risk, as before.
  _posPrices(d) {
    const entry = d.p0.p, stop = d.p1.p, long = d.type === 'longshort';
    const target = (d.tp != null && Number.isFinite(d.tp)) ? d.tp : (long ? entry + 2 * (entry - stop) : entry - 2 * (stop - entry));
    return { entry, stop, target, long };
  }
  _posGeom(d) {
    if (!d.p0 || !d.p1) return null;
    const x0 = this._x(d.p0.t), x1 = this._x(d.p1.t); if (x0 == null || x1 == null) return null;
    const g = this._posPrices(d); const xa = Math.min(x0, x1), xb = Math.max(Math.max(x0, x1), xa + 40);
    return { xa, xb, yE: this._y(g.entry), yS: this._y(g.stop), yT: this._y(g.target), ...g };
  }
  /** The money side of a position: account, risk, quantity, reward and the reward/risk ratio. */
  positionStats(d) {
    const g = this._posPrices(d);
    const account = Number.isFinite(d.account) ? d.account : 1000, lot = Number.isFinite(d.lot) && d.lot > 0 ? d.lot : 1;
    const risk = Number.isFinite(d.risk) ? d.risk : 1;
    const riskAmt = d.riskUnit === 'amount' ? risk : account * risk / 100;
    const per = Math.abs(g.entry - g.stop);
    const qty = per ? riskAmt / per / lot : 0;
    const reward = qty * lot * Math.abs(g.target - g.entry);
    return { ...g, account, lot, riskAmt, qty, reward, rr: per ? Math.abs(g.target - g.entry) / per : 0, tpPct: g.entry ? (g.target - g.entry) / g.entry * 100 : 0, slPct: g.entry ? (g.stop - g.entry) / g.entry * 100 : 0 };
  }
  // TradingView's long and short position: the profit zone in green, the risk zone in red, the
  // target, entry and stop lines, and the labels with prices, percentages, amounts, the quantity
  // and the reward/risk ratio.
  _drawPosition(ctx, d) {
    const g = this._posGeom(d); if (!g) return;
    const { xa, xb, yE, yS, yT } = g; const st = this.positionStats(d);
    ctx.save(); ctx.setLineDash([]); ctx.lineWidth = 1;
    if (yT != null && yE != null) { ctx.fillStyle = 'rgba(8,153,129,0.2)'; ctx.fillRect(xa, Math.min(yT, yE), xb - xa, Math.abs(yE - yT)); }
    if (yS != null && yE != null) { ctx.fillStyle = 'rgba(242,54,69,0.2)'; ctx.fillRect(xa, Math.min(yS, yE), xb - xa, Math.abs(yE - yS)); }
    const line = (y, col) => { if (y == null) return; ctx.strokeStyle = col; ctx.beginPath(); ctx.moveTo(xa, y); ctx.lineTo(xb, y); ctx.stroke(); };
    line(yT, '#089981'); line(yE, '#787b86'); line(yS, '#f23645');
    // Figures are isolated left to right so a Persian label keeps «(+3.00%)» as written.
    const cx = (xa + xb) / 2, iso = (v) => `\u2066${v}\u2069`, f = (v) => iso(this._fmtP(v));
    const q = st.qty >= 100 ? st.qty.toFixed(0) : st.qty >= 1 ? st.qty.toFixed(2) : Number(st.qty.toPrecision(3)).toString();
    const sgn = (v) => iso(`(${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(2)}%)`);
    if (yT != null) this._tag(ctx, cx, yT, `${this._tr('هدف', 'Target')}: ${f(st.target)} ${sgn(st.tpPct)}  ${this._tr('مبلغ', 'Amount')}: ${iso(st.reward.toFixed(2))}`, '#089981', yT <= (yE ?? yT) ? 'above' : 'below');
    if (yE != null) this._tag(ctx, cx, yE, `${this._tr('نسبت سود به ریسک', 'Risk/reward ratio')}: ${iso(st.rr.toFixed(2))}\n${this._tr('حجم', 'Qty')}: ${iso(q)}`, '#5d606b', 'center');
    if (yS != null) this._tag(ctx, cx, yS, `${this._tr('حد ضرر', 'Stop')}: ${f(st.stop)} ${sgn(st.slPct)}  ${this._tr('مبلغ', 'Amount')}: ${iso(st.riskAmt.toFixed(2))}`, '#f23645', yS <= (yE ?? yS) ? 'above' : 'below');
    ctx.restore();
  }
}
