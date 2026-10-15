// بازارنما — لایه‌ی ترسیم روی چارت (Canvas overlay، مختصات chart-space).
// ابزارها: trend, ray, hline, vline, rect, fib, longshort, text.
// نقاط بر حسب {t (unix), p (price)} ذخیره می‌شوند تا با zoom/pan ثابت بمانند.

// افزونه‌ی فصل ۴ (PRO_CHART_BUILD_SPEC): ۳۹ ابزار ترسیم جدید، drop-in.
import { EXT_NEED, EXT_FREEHAND, EXT_LABELS, isExt, extDraw, extHit, extHandles } from './drawtools_ext.js';
import { DrawingHistory } from './drawing_history.js';

const FIB = [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1];
// رنگ هر سطح فیبوناچی — هم‌تراز پالت پیش‌فرض فیب TradingView (خاکستری/قرمز/سبزروشن/سبز/سبزآبی/آبی/خاکستری).
const FIB_COLORS = ['#787b86', '#f23645', '#81c784', '#4caf50', '#089981', '#64b5f6', '#787b86'];

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
  nudgeSelected(key, large = false) {
    if (this.selected < 0) return false;
    const d = this.drawings[this.selected]; if (!d || d.locked) return false;
    const h = this._handlePoints(d).find((p) => p.x != null && p.y != null); if (!h) return false;
    const step = large ? 8 : 1;
    let dt = 0, dp = 0;
    if (key === 'ArrowUp') dp = this._p(h.y - step) - this._p(h.y);
    else if (key === 'ArrowDown') dp = this._p(h.y + step) - this._p(h.y);
    else if (key === 'ArrowLeft') dt = this._t(h.x - step) - this._t(h.x);
    else if (key === 'ArrowRight') dt = this._t(h.x + step) - this._t(h.x);
    else return false;
    if (!Number.isFinite(dt) || !Number.isFinite(dp) || (!dt && !dp)) return false;
    this._pushUndo(); this._moveBy(d, dt, dp); this._changed();
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
  setTfClass(cls) { if (this.tfClass === cls) return; this.tfClass = cls; this.render(); }
  _tfHidden(d) { return !!(d && d.tfVis && this.tfClass && d.tfVis[this.tfClass] === false); }
  // فرمتر زمان (tz-aware، از BazaarNama) — برای برچسب تاریخ خط عمودی روی محور زمان (مثل TV). #281
  setTimeFmt(fn) { this.timeFmt = (typeof fn === 'function') ? fn : null; }
  lockAll(on) { this._pushUndo(); this.drawings.forEach((d) => { d.locked = on; }); this._changed(); }
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
    const h = this._handlePoints(d).find((p) => p.x != null && p.y != null);
    if (h) { const ot = this._t(h.x), op = this._p(h.y), nt = this._t(h.x + 14), np = this._p(h.y + 14); if (ot != null && op != null && nt != null && np != null) this._moveBy(c, nt - ot, np - op); }
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
    const h = this._handlePoints(c).find((p) => p.x != null && p.y != null);
    if (h) { const ot = this._t(h.x), op = this._p(h.y), nt = this._t(h.x + 14), np = this._p(h.y + 14); if (ot != null && op != null && nt != null && np != null) this._moveBy(c, nt - ot, np - op); }
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
  setDigits(d) { if (Number.isFinite(d)) this.digits = d; } // دقت اعشار نماد فعال (برچسب ابزار اندازه‌گیری)
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
    ['color', 'width', 'lineStyle', 'fill', 'fillOpacity', 'fontSize', 'bold', 'italic', 'bg', 'bgOpacity', 'border'].forEach((k) => { if (def[k] !== undefined) out[k] = def[k]; });
    return out;
  }
  setTool(t, color) { this.tool = t; if (color) this.color = color; this.pending = null; this.tmp = null; this.twoClick = false; this.dragging = false; this.multiSel = new Set(); this.hover = -1; this.canvas.style.pointerEvents = t === 'cursor' ? 'none' : 'auto'; this.canvas.style.cursor = (t === 'cursor' || t === 'select') ? 'default' : 'crosshair'; if (t !== 'select') { this.selected = -1; this.onSelect && this.onSelect(-1); } }
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
    if (d.pts) return d.pts.map((pp, k) => ({ x: this._x(pp.t), y: this._y(pp.p), set: (t, p) => { d.pts[k] = { t, p }; } }));
    const out = [];
    if (d.p0) out.push({ x: this._x(d.p0.t), y: this._y(d.p0.p), set: (t, p) => { d.p0 = { t, p }; } });
    if (d.p1) out.push({ x: this._x(d.p1.t), y: this._y(d.p1.p), set: (t, p) => { d.p1 = { t, p }; } });
    return out;
  }
  _hitHandle(d, x, y) { const hs = this._handlePoints(d); for (let i = 0; i < hs.length; i++) { const h = hs[i]; if (h.x != null && h.y != null && Math.hypot(x - h.x, y - h.y) < 9) return i; } return -1; }
  _moveBy(d, dt, dp) {
    const mv = (pt) => ({ t: pt.t + dt, p: pt.p + dp });
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
      const r = cv.getBoundingClientRect();
      const x = e.clientX - r.left, y = e.clientY - r.top;
      if (this.tool === 'cursor' || this.tool === 'select') {
        // Shift+درگ ⇒ ابزار اندازه‌گیری گذرا (سبک TV): جعبه‌ی Δقیمت/٪/بار/ساعت که با رهاکردن پاک می‌شود.
        if (e.shiftKey) { const mp = this._snap({ t: this._t(x), p: this._p(y) }); if (mp.t != null && mp.p != null) { this.tmp = { type: 'dprange', p0: mp, p1: mp, color: '#089981', width: 2 }; this.dragging = true; this.didDrag = false; this.downXY = { x, y }; this.measuring = true; return; } }
        // درگ خطوط سفارش (entry/sl/tp)
        if (this.order) { for (const k of ['entry', 'sl', 'tp']) { const yy = this._y(this.order[k]); if (yy != null && Math.abs(yy - y) < 7) { this.dragOrder = k; return; } } }
        // ۱) اگر آبجکتی انتخاب شده، روی handleهایش کلیک شده؟ → درگ handle
        if (this.selected >= 0) {
          const sd = this.drawings[this.selected];
          if (sd && !sd.locked) { const hi = this._hitHandle(sd, x, y); if (hi >= 0) { this._pushUndo(); this.dragHandle = { idx: this.selected, hi }; return; } }
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
        if (hit >= 0 && this.drawings[hit] && !this.drawings[hit].locked) { const sp = this._snap({ t: this._t(x), p: this._p(y) }); if (sp.t != null && sp.p != null) { this._pushUndo();
          // Alt+درگ ⇒ کلون درجا و درگ کپی (اصل سر جایش می‌مانَد) — ژست رایج TV. بدون Alt = جابه‌جایی خود آبجکت.
          let mi = hit;
          if (e.altKey) { const dup = JSON.parse(JSON.stringify(this.drawings[hit])); dup.locked = false; this.drawings.push(dup); mi = this.drawings.length - 1; this.onSelect && this.onSelect(mi); this.multiSel = new Set(); }
          this.selected = mi; this.dragMove = { idx: mi, last: sp }; } }
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
      const NEED = { channel: 3, pitchfork: 3, ...EXT_NEED };
      if (NEED[this.tool]) {
        if (!this.pending || this.pending.type !== this.tool) this.pending = { type: this.tool, pts: [pt], color: this.color };
        else this.pending.pts.push(pt);
        if (this.pending.pts.length >= NEED[this.tool]) { this._pushUndo(); this.drawings.push(this._applyDef({ type: this.tool, pts: this.pending.pts.slice(), color: this.color, width: 2 })); this.pending = null; this._reset(); }
        else this.render();
        return;
      }
      // خط چندتکه (polyline): هر کلیک یک رأس اضافه می‌کند؛ پایان با دابل‌کلیک یا Enter (بی‌پایان ثابت).
      if (this.tool === 'polyline' || this.tool === 'path') {
        const tt = this.tool;
        if (!this.pending || this.pending.type !== tt) this.pending = { type: tt, pts: [pt], color: this.color };
        else this.pending.pts.push(pt);
        this.render();
        return;
      }
      if (this.tool === 'hline') { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'hline', p: pt.p, color: this.color, width: 2 })); this._reset(); return; }
      if (this.tool === 'vline') { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'vline', t: pt.t, color: this.color, width: 2 })); this._reset(); return; }
      // The host's own text editor when it has one (onTextRequest: the new drawing's index, then
      // setStyle(i, {text})); window.prompt only as the fallback.
      if (this.tool === 'text') {
        if (this.onTextRequest) { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'text', t: pt.t, p: pt.p, text: '', color: this.color })); const idx = this.drawings.length - 1; this._reset(); this.onTextRequest(idx); return; }
        const txt = window.prompt('متن (برای خط جدید «\\n» بنویسید):'); if (txt) { this._pushUndo(); this.drawings.push(this._applyDef({ type: 'text', t: pt.t, p: pt.p, text: txt, color: this.color })); } this._reset(); return;
      }
      // ابزارهای دونقطه‌ای: پشتیبانی از هر دو حالت «کلیک-کلیک» (مثل TradingView) و «کلیک-کشیدن».
      // اگر منتظر کلیک دوم هستیم، این کلیک نقطه‌ی پایانی است و ترسیم ثبت می‌شود.
      if (this.twoClick && this.tmp) {
        this.tmp.p1 = e.shiftKey ? this._constrainDraw(this.tmp.type, this.tmp.p0, pt) : pt; this._pushUndo(); this.drawings.push(this._applyDef(this.tmp));
        this.tmp = null; this.twoClick = false; this.dragging = false; this.didDrag = false; this._reset(); return;
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
      if (this.dragOrder && this.order) { const pr = this._p(y); if (pr != null) { this.order[this.dragOrder] = pr; this.render(); this.onOrder && this.onOrder({ ...this.order }); } return; }
      // درگ handle
      if (this.dragHandle) { const d = this.drawings[this.dragHandle.idx]; const sp = this._snap({ t: this._t(x), p: this._p(y) }); if (d && sp.t != null && sp.p != null) { this._handlePoints(d)[this.dragHandle.hi].set(sp.t, sp.p); this.render(); } return; }
      // جابه‌جایی کل آبجکت
      if (this.dragMove) { const d = this.drawings[this.dragMove.idx]; const sp = this._snap({ t: this._t(x), p: this._p(y) }); if (d && sp.t != null && sp.p != null) { const dt = sp.t - this.dragMove.last.t, dp = sp.p - this.dragMove.last.p; if (this.multiSel.size > 1 && this.multiSel.has(this.dragMove.idx)) { this.multiSel.forEach((i) => { const dd = this.drawings[i]; if (dd && !dd.locked) this._moveBy(dd, dt, dp); }); } else { this._moveBy(d, dt, dp); } this.dragMove.last = sp; this.render(); } return; }
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
      if (this.tool === 'cursor' && !this.dragging && !this.dragMove && !this.dragHandle && !this.dragOrder) {
        const over = this._interactiveAt(x, y);
        const want = (over || e.shiftKey) ? 'auto' : 'none'; // Shift ⇒ کانوس رویدادها را بگیرد تا Shift+درگ اندازه‌گیری کار کند
        if (cv.style.pointerEvents !== want) cv.style.pointerEvents = want;
        cv.style.cursor = over ? 'pointer' : 'default';
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
      if (this.dragOrder) { this.dragOrder = null; return; }
      if (this.dragHandle) { this.dragHandle = null; this._changed(); return; }
      if (this.dragMove) { this.dragMove = null; this._changed(); return; }
      // اندازه‌گیری گذرا (Shift+درگ سبک TV): با رهاکردن پاک می‌شود (ثبت نمی‌شود).
      if (this.measuring) { this.measuring = false; this.dragging = false; this.tmp = null; this.didDrag = false; this.render(); return; }
      if (this.tmp && this.dragging) {
        if (this.didDrag) { this._pushUndo(); this.drawings.push(this._applyDef(this.tmp)); this.tmp = null; this.dragging = false; this.didDrag = false; this._reset(); } // کشیده شد ⇒ ثبت
        else { this.dragging = false; this.twoClick = true; this.render(); } // فقط کلیک شد ⇒ منتظر کلیک دوم (مثل TV)
      }
    };
    const key = (e) => {
      const tag = e.target && e.target.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target && e.target.isContentEditable)) return;
      if (e.key === 'Enter' && this.pending && (this.pending.type === 'polyline' || this.pending.type === 'path')) { e.preventDefault(); this._finishPoly && this._finishPoly(); return; }
    };
    // پایان‌دادن خط چندتکه (دابل‌کلیک یا Enter). دابل‌کلیک دو mousedown می‌زند ⇒ رأس تکراری انتهایی حذف می‌شود.
    const finishPoly = () => {
      if (!this.pending || (this.pending.type !== 'polyline' && this.pending.type !== 'path')) return;
      const t = this.pending.type;
      const pts = this.pending.pts.slice();
      const L = pts.length;
      if (L >= 2 && pts[L - 1].t === pts[L - 2].t && pts[L - 1].p === pts[L - 2].p) pts.pop();
      if (pts.length >= 2) { this._pushUndo(); this.drawings.push(this._applyDef({ type: t, pts, color: this.pending.color, width: 2 })); }
      this.pending = null; this._reset();
    };
    const dbl = (e) => {
      // اگر خط چندتکه/مسیر در حال ترسیم است ⇒ پایان بده (رفتار قبلی).
      if (this.pending && (this.pending.type === 'polyline' || this.pending.type === 'path')) { finishPoly(); return; }
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
    cv.addEventListener('mousedown', down);
    cv.addEventListener('dblclick', dbl);
    cv.addEventListener('wheel', wheel, { passive: false });
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
    window.addEventListener('keydown', key);
    this._cleanup = () => { stopEdgePan(); cv.removeEventListener('mousedown', down); cv.removeEventListener('dblclick', dbl); cv.removeEventListener('wheel', wheel); window.removeEventListener('mousemove', move); window.removeEventListener('mouseup', up); window.removeEventListener('keydown', key); };
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
    for (let i = this.drawings.length - 1; i >= 0; i--) {
      const d = this.drawings[i];
      if (!d || d.visible === false) continue; // شی مخفی (toggleVisible) نباید کلیک‌پذیر باشد
      if (this._tfHidden(d)) continue; // ترسیم پنهان‌شده در این کلاس تایم‌فریم قابل انتخاب نیست (#271)
      if (isExt(d.type)) { if (extHit(d, x, y, this)) return i; continue; }
      if (d.type === 'hline') { const yy = this._y(d.p); if (yy != null && Math.abs(yy - y) < near) return i; continue; }
      if (d.type === 'vline') { const xx = this._x(d.t); if (xx != null && Math.abs(xx - x) < near) return i; continue; }
      // متن: جعبه‌ی تقریبی حول لنگر (t,p) — قبلاً هیچ شاخه‌ای نداشت و انتخاب/راست‌کلیک متن کار نمی‌کرد.
      if (d.type === 'text') { const tx = this._x(d.t), ty = this._y(d.p); if (tx == null || ty == null) continue; const fs = Math.max(8, Math.min(64, d.fontSize || 12)); const tlines = String(d.text || '').replace(/\\n/g, '\n').split('\n'); const maxLen = tlines.reduce((a, s) => Math.max(a, s.length), 0); const lh = Math.round(fs * 1.3); const hw = Math.max(24, maxLen * fs * 0.4); if (x >= tx - hw && x <= tx + hw && y >= ty - fs - 4 && y <= ty + 5 + (tlines.length - 1) * lh) return i; continue; }
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
      const x0 = this._x(d.p0 && d.p0.t), y0 = this._y(d.p0 && d.p0.p), x1 = this._x(d.p1 && d.p1.t), y1 = this._y(d.p1 && d.p1.p);
      if (x0 == null || x1 == null) continue;
      if (d.type === 'rect' || d.type === 'fib' || d.type === 'longshort' || d.type === 'short') {
        if (x >= Math.min(x0, x1) - near && x <= Math.max(x0, x1) + near && y >= Math.min(y0, y1) - near && y <= Math.max(y0, y1) + near) return i;
      } else { // خط: فاصله تا پاره‌خط
        const A = x - x0, B = y - y0, C = x1 - x0, D = y1 - y0; const dot = A * C + B * D, len = C * C + D * D;
        // ray از p0 در جهت p1 تا امتداد (رندر ۴۰۰۰×) کشیده می‌شود؛ پس سقف t را clamp نکن تا دُم امتدادیافته هم قابل‌انتخاب باشد (مثل TV). بقیه‌ی خطوط پاره‌خط محدود [۰,۱].
        const t = len ? (d.type === 'ray' ? Math.max(0, dot / len) : Math.max(0, Math.min(1, dot / len))) : 0;
        const px = x0 + t * C, py = y0 + t * D; if (Math.hypot(x - px, y - py) < near) return i;
      }
    }
    return -1;
  }

  _reset() { this.twoClick = false; this.didDrag = false; if (!this.stayInMode) { this.tool = 'cursor'; this.canvas.style.pointerEvents = 'none'; this.canvas.style.cursor = 'default'; this.onToolReset && this.onToolReset(); } this._changed(); }
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

  render() {
    const ctx = this.ctx; if (!ctx) return;
    const W = this.canvas.width, H = this.canvas.height;
    ctx.clearRect(0, 0, W, H);
    // Volume Profile (نوارهای افقی سمت راست)
    if (this.profile && this.profile.length) {
      const maxV = Math.max(...this.profile.map((b) => b.vol)) || 1;
      const maxW = Math.min(160, W * 0.18);
      this.profile.forEach((b) => {
        const y = this._y((b.lo + b.hi) / 2); if (y == null) return;
        const yh = this._y(b.hi), yl = this._y(b.lo); const h = (yh != null && yl != null) ? Math.max(2, Math.abs(yl - yh) - 1) : 4;
        const w = (b.vol / maxV) * maxW;
        ctx.fillStyle = b.poc ? 'rgba(245,158,11,.55)' : (b.va ? 'rgba(59,130,246,.42)' : 'rgba(59,130,246,.18)');
        ctx.fillRect(W - w, y - h / 2, w, h);
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
      const lv = [0, 0.618, 1, 1.272, 1.618, 2.618]; const base = d.p0.p, diff = d.p1.p - d.p0.p;
      const xa = Math.min(xx0, xx1), xb = Math.max(xx0, xx1) + 30;
      lv.forEach((l) => { const pr = base + diff * l; const y = this._y(pr); if (y == null) return; ctx.globalAlpha = 0.85; ctx.beginPath(); ctx.moveTo(xa, y); ctx.lineTo(xb, y); ctx.stroke(); ctx.fillText(`${String(+l.toFixed(3))}  ${pr.toFixed(this.digits)}`, xa + 4, y - 2); ctx.globalAlpha = 1; });
      return;
    }
    if (d.type === 'hline') { const y = this._y(d.p); if (y == null) return; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
      // برچسب قیمت به‌صورت tag رنگی لبه‌ی راست (نزدیک محور قیمت) — سبک TV؛ قبلاً متن ساده‌ی لبه‌ی چپ بود. #287
      const lbl = d.p.toFixed(this.digits); ctx.save(); ctx.font = '11px sans-serif'; const tw = ctx.measureText(lbl).width; const pad = 4, tagW = tw + pad * 2, tagH = 15; const tx = W - tagW - 2; ctx.fillStyle = d.color; ctx.globalAlpha = 0.95; ctx.fillRect(tx, y - tagH / 2, tagW, tagH); ctx.globalAlpha = 1; ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left'; ctx.fillText(lbl, tx + pad, y); ctx.restore(); return; }
    if (d.type === 'vline') { const x = this._x(d.t); if (x == null) return; ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
      // برچسب تاریخ خط عمودی به‌صورت tag رنگی محور زمان (پایین، مرکز خط)، tz-درست — سبک TV؛ هم‌سبک با price-tag خط افقی #287. (#281→#288)
      if (this.timeFmt) { try { const lbl = this.timeFmt(d.t); if (lbl) { const s = String(lbl); ctx.save(); ctx.font = '11px sans-serif'; const tw = ctx.measureText(s).width; const pad = 4, tagW = tw + pad * 2, tagH = 15; let tx = x - tagW / 2; tx = Math.max(2, Math.min(tx, this.canvas.width - tagW - 2)); const ty = H - tagH - 1; ctx.fillStyle = d.color; ctx.globalAlpha = 0.95; ctx.fillRect(tx, ty, tagW, tagH); ctx.globalAlpha = 1; ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left'; ctx.fillText(s, tx + pad, ty + tagH / 2); ctx.restore(); } } catch (e) { /* noop */ } }
      return; }
    const x0 = this._x(d.p0?.t), y0 = this._y(d.p0?.p), x1 = this._x(d.p1?.t), y1 = this._y(d.p1?.p);
    if (d.type === 'text') { const x = this._x(d.t), y = this._y(d.p); if (x == null || y == null) return; const fs = Math.max(8, Math.min(64, d.fontSize || 12)); const italic = d.italic ? 'italic ' : ''; const bold = d.bold ? 'bold ' : ''; ctx.font = `${italic}${bold}${fs}px IRANYekanX, sans-serif`;
      // متن چندخطی مثل ابزار Text TV: هم نیولاین واقعی (paste) هم «\n» تایپ‌شده در prompt را می‌شکند؛ هر خط با line-height. تک‌خطی دقیقاً مثل قبل. #295
      const lines = String(d.text || '').replace(/\\n/g, '\n').split('\n'); const lh = Math.round(fs * 1.3);
      // پس‌زمینه (#284) + حاشیه‌ی (Border، #285) متن — کادر مشترک که همه‌ی خطوط را دربر می‌گیرد.
      if (d.bg || d.border) { let tw = 0; for (let li = 0; li < lines.length; li++) { const w = ctx.measureText(lines[li]).width; if (w > tw) tw = w; } const pad = 3; const rx = x - pad, ry = y - fs - pad + 2, rw = tw + pad * 2, rh = (lines.length - 1) * lh + fs + pad * 2;
        if (d.bg) { ctx.save(); ctx.fillStyle = d.bg; ctx.globalAlpha = (d.bgOpacity != null ? d.bgOpacity : 0.9); ctx.fillRect(rx, ry, rw, rh); ctx.restore(); }
        if (d.border) { ctx.save(); ctx.strokeStyle = d.border; ctx.lineWidth = 1; ctx.strokeRect(rx, ry, rw, rh); ctx.restore(); } }
      for (let li = 0; li < lines.length; li++) ctx.fillText(lines[li], x, y + li * lh); return; }
    if (x0 == null || y0 == null || x1 == null || y1 == null) return;
    if (d.type === 'trend') { ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke();
      // برچسب اطلاعاتی Δقیمت/٪/بار هنگام انتخاب (مثل خط روند TV) — متن رنگی ساده، هم‌سبک برچسب‌های دیگر ترسیم.
      if (sel && d.p0 && d.p1) {
        const dp = d.p1.p - d.p0.p, pct = d.p0.p ? (dp / d.p0.p * 100) : 0;
        const iv = this._barInterval(), bars = iv ? Math.round((d.p1.t - d.p0.t) / iv) : 0;
        const sg = dp >= 0 ? '+' : '−';
        ctx.fillText(`${sg}${Math.abs(dp).toFixed(this.digits)} (${sg}${Math.abs(pct).toFixed(2)}%) · ${Math.abs(bars)} بار`, x1 + 8, y1 - 2);
      }
    }
    else if (d.type === 'ray') { const dx = x1 - x0, dy = y1 - y0; const k = 4000; ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x0 + dx * k, y0 + dy * k); ctx.stroke(); }
    else if (d.type === 'rect') { ctx.globalAlpha = (d.fillOpacity != null ? d.fillOpacity : 0.18); ctx.fillStyle = d.fill || d.color; ctx.fillRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0)); ctx.fillStyle = d.color; ctx.globalAlpha = 1; ctx.strokeRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0)); }
    else if (d.type === 'fib') {
      const top = Math.max(d.p0.p, d.p1.p), bot = Math.min(d.p0.p, d.p1.p), rng = top - bot;
      const xa = Math.min(x0, x1), xb = Math.max(x0, x1);
      // سطوح با رنگ اختصاصی هر سطح (سبک TV) + باند پس‌زمینه‌ی کم‌رنگ بین سطوح متوالی.
      const ys = FIB.map((lv) => this._y(top - rng * lv));
      FIB.forEach((lv, i) => {
        const y = ys[i]; if (y == null) return; const col = FIB_COLORS[i] || d.color;
        // باند پس‌زمینه بین این سطح و سطح قبلی (رنگ همین سطح، شفافیت کم مثل Background فیب TV).
        if (i > 0 && ys[i - 1] != null) { ctx.globalAlpha = 0.08; ctx.fillStyle = col; ctx.fillRect(xa, Math.min(y, ys[i - 1]), xb - xa, Math.abs(y - ys[i - 1])); ctx.globalAlpha = 1; }
        const price = top - rng * lv;
        ctx.strokeStyle = col; ctx.fillStyle = col; ctx.globalAlpha = 0.85;
        ctx.beginPath(); ctx.moveTo(xa, y); ctx.lineTo(xb, y); ctx.stroke();
        // برچسب سطح/قیمت را با شفافیت کامل بکش (خط فیب کم‌رنگ است ولی متن مثل TV واضح) — #289
        ctx.globalAlpha = 1;
        ctx.fillText(`${String(+lv.toFixed(3))}  ${price.toFixed(this.digits)}`, xa + 4, y - 2);
      });
    }
    else if (d.type === 'longshort') {
      const entry = d.p0.p, stop = d.p1.p, risk = entry - stop, target = entry + 2 * risk;
      const yE = this._y(entry), yS = this._y(stop), yT = this._y(target);
      const xa = Math.min(x0, x1), xb = Math.max(x0, x1) + 40;
      // درصد حرکت تا هدف/حد ضرر — کمیّت R:R مثل ابزار Long Position/Short Position TV.
      const rwdPct = entry ? (Math.abs(target - entry) / Math.abs(entry) * 100) : 0;
      const rskPct = entry ? (Math.abs(stop - entry) / Math.abs(entry) * 100) : 0;
      if (yT != null && yE != null) { ctx.fillStyle = 'rgba(34,197,94,.15)'; ctx.fillRect(xa, Math.min(yT, yE), xb - xa, Math.abs(yE - yT)); }
      if (yS != null && yE != null) { ctx.fillStyle = 'rgba(239,68,68,.15)'; ctx.fillRect(xa, Math.min(yS, yE), xb - xa, Math.abs(yE - yS)); }
      ctx.fillStyle = d.color; ctx.strokeStyle = '#22c55e'; if (yT != null) { ctx.beginPath(); ctx.moveTo(xa, yT); ctx.lineTo(xb, yT); ctx.stroke(); ctx.fillText(`هدف 2R  ${target.toFixed(this.digits)} (${rwdPct.toFixed(2)}%)`, xa + 4, yT - 2); }
      ctx.strokeStyle = '#94a3b8'; if (yE != null) { ctx.beginPath(); ctx.moveTo(xa, yE); ctx.lineTo(xb, yE); ctx.stroke(); ctx.fillText(`ورود  ${entry.toFixed(this.digits)}`, xa + 4, yE - 2); }
      ctx.strokeStyle = '#ef4444'; if (yS != null) { ctx.beginPath(); ctx.moveTo(xa, yS); ctx.lineTo(xb, yS); ctx.stroke(); ctx.fillText(`حد ضرر  ${stop.toFixed(this.digits)} (${rskPct.toFixed(2)}%)`, xa + 4, yS - 2); }
    }
    else if (d.type === 'short') {
      // موقعیت شورت (ابزار Short Position TV): برعکس لانگ — حد ضرر بالای ورود، هدف پایین (entry − 2R).
      //   جعبه‌ی سبز سود پایین ورود، جعبه‌ی قرمز ریسک بالای ورود. p0=ورود، p1=حد ضرر (که بالا کشیده می‌شود).
      const entry = d.p0.p, stop = d.p1.p, risk = stop - entry, target = entry - 2 * risk;
      const yE = this._y(entry), yS = this._y(stop), yT = this._y(target);
      const xa = Math.min(x0, x1), xb = Math.max(x0, x1) + 40;
      const rwdPct = entry ? (Math.abs(target - entry) / Math.abs(entry) * 100) : 0;
      const rskPct = entry ? (Math.abs(stop - entry) / Math.abs(entry) * 100) : 0;
      if (yT != null && yE != null) { ctx.fillStyle = 'rgba(34,197,94,.15)'; ctx.fillRect(xa, Math.min(yT, yE), xb - xa, Math.abs(yE - yT)); }
      if (yS != null && yE != null) { ctx.fillStyle = 'rgba(239,68,68,.15)'; ctx.fillRect(xa, Math.min(yS, yE), xb - xa, Math.abs(yE - yS)); }
      ctx.fillStyle = d.color; ctx.strokeStyle = '#22c55e'; if (yT != null) { ctx.beginPath(); ctx.moveTo(xa, yT); ctx.lineTo(xb, yT); ctx.stroke(); ctx.fillText(`هدف 2R  ${target.toFixed(this.digits)} (${rwdPct.toFixed(2)}%)`, xa + 4, yT - 2); }
      ctx.strokeStyle = '#94a3b8'; if (yE != null) { ctx.beginPath(); ctx.moveTo(xa, yE); ctx.lineTo(xb, yE); ctx.stroke(); ctx.fillText(`ورود  ${entry.toFixed(this.digits)}`, xa + 4, yE - 2); }
      ctx.strokeStyle = '#ef4444'; if (yS != null) { ctx.beginPath(); ctx.moveTo(xa, yS); ctx.lineTo(xb, yS); ctx.stroke(); ctx.fillText(`حد ضرر  ${stop.toFixed(this.digits)} (${rskPct.toFixed(2)}%)`, xa + 4, yS - 2); }
    }
  }
}
