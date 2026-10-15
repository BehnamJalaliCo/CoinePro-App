// Custom lightweight-charts v5 primitive: پُرشدگیِ نیمه‌شفاف بینِ دو خط (باندِ بولینگر/کلتنر/ابرِ ایچیموکو).
// همه‌چیز درونِ try/catch است تا در بدترین حالت فقط پُرشدگی رندر نشود (نه کرش).
// دادهٔ باند: [{ time, upper, lower }]. اگر colorDown داده شود، هر بخش بر اساسِ upper≥lower رنگِ up/down می‌گیرد (ابرِ ایچیموکو).

class BandFillRenderer {
  constructor(src) { this._src = src; }
  draw(target) {
    try {
      const src = this._src;
      const chart = src._chart, series = src._series, data = src._data;
      if (!chart || !series || !data || data.length < 2) return;
      const ts = chart.timeScale();
      target.useMediaCoordinateSpace(({ context: ctx }) => {
        const pts = [];
        for (const d of data) {
          if (d == null || d.upper == null || d.lower == null) { pts.push(null); continue; }
          const x = ts.timeToCoordinate(d.time);
          const yU = series.priceToCoordinate(d.upper);
          const yL = series.priceToCoordinate(d.lower);
          pts.push((x == null || yU == null || yL == null) ? null : { x, yU, yL, up: d.upper >= d.lower });
        }
        // بخش‌های پیوسته را جدا پر می‌کنیم (null = گسست).
        let seg = [];
        const flush = () => {
          if (seg.length < 2) { seg = []; return; }
          if (src._colorDown) {
            // ابرِ ایچیموکو: هر زیرْبخش بر اساسِ جهت رنگ می‌گیرد
            let sub = [seg[0]];
            for (let i = 1; i < seg.length; i++) {
              if (seg[i].up === sub[sub.length - 1].up) { sub.push(seg[i]); }
              else { paint(ctx, sub, sub[0].up ? src._color : src._colorDown); sub = [seg[i - 1], seg[i]]; }
            }
            paint(ctx, sub, sub[0].up ? src._color : src._colorDown);
          } else {
            paint(ctx, seg, src._color);
          }
          seg = [];
        };
        for (const p of pts) { if (p == null) flush(); else seg.push(p); }
        flush();
      });
    } catch (e) { /* noop — بدونِ پُرشدگی، بدونِ کرش */ }
  }
}

function paint(ctx, seg, color) {
  ctx.beginPath();
  ctx.moveTo(seg[0].x, seg[0].yU);
  for (let i = 0; i < seg.length; i++) ctx.lineTo(seg[i].x, seg[i].yU);
  for (let i = seg.length - 1; i >= 0; i--) ctx.lineTo(seg[i].x, seg[i].yL);
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();
}

class BandFillPaneView {
  constructor(src) { this._renderer = new BandFillRenderer(src); }
  zOrder() { return 'bottom'; } // زیرِ خطوط و کندل نباشد؛ 'bottom' = زیرِ سریِ خودش
  renderer() { return this._renderer; }
}

export class BandFillPrimitive {
  // color: پُرشدگیِ اصلی (یا up در حالتِ ابر)؛ colorDown: پُرشدگیِ نزولی (اختیاری، برای ابرِ ایچیموکو)
  constructor(data, color, colorDown = null) {
    this._data = data || [];
    this._color = color;
    this._colorDown = colorDown;
    this._chart = null; this._series = null; this._requestUpdate = null;
    this._paneViews = [new BandFillPaneView(this)];
  }
  attached(param) { try { this._chart = param.chart; this._series = param.series; this._requestUpdate = param.requestUpdate; } catch (e) {} }
  detached() { this._chart = null; this._series = null; this._requestUpdate = null; }
  updateAllViews() { /* دادهٔ ثابت پس از ساخت؛ نیازی به به‌روزرسانیِ view نیست */ }
  paneViews() { return this._paneViews; }
  setData(data) { this._data = data || []; if (this._requestUpdate) try { this._requestUpdate(); } catch (e) {} }
}
