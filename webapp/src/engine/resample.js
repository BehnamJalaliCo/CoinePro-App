// resample — تجمیعِ کندل‌های تایم‌فریمِ مشتق (M2/M3/H2/H3/W1/MN) از تایم‌فریمِ پایه.
//   خالص و بدونِ importِ خارجی ⇒ مستقیماً در Node قابلِ تست (test/resample.test.mjs).
//
// درون‌روزی (base < 1 روز): باکت‌بندیِ هم‌تراز با مرزِ زمانی (مثلِ TradingView) — نه شمارشی از ایندکسِ ۰.
//   باگِ قبلی: H2/H3/M2/M3 به اولین کندلِ واکشی‌شده لنگر می‌شدند ⇒ ساعتِ فرد و جابه‌جایی با اسکرول.
// پایهٔ روزانه (W1=D1×5، MN=D1×22): شمارشی می‌مانَد تا «۵ روزِ معاملاتی = هفته» با شکافِ آخرهفته درست بماند.
export const resampleCandles = (cs, factor) => {
  if (factor <= 1 || !cs || !cs.length) return cs;
  let base = Infinity;
  for (let i = 1; i < cs.length; i++) { const d = cs[i].t - cs[i - 1].t; if (d > 0 && d < base) base = d; }
  if (Number.isFinite(base) && base > 0 && base < 86400) {
    const bucket = base * factor;
    const out = []; let cur = null, key = null;
    for (const c of cs) {
      const k = Math.floor(c.t / bucket) * bucket;
      if (k !== key) { if (cur) out.push(cur); key = k; cur = { t: k, o: c.o, h: c.h, l: c.l, c: c.c, v: c.v || 0 }; }
      else { cur.h = Math.max(cur.h, c.h); cur.l = Math.min(cur.l, c.l); cur.c = c.c; cur.v += (c.v || 0); }
    }
    if (cur) out.push(cur);
    return out;
  }
  const out = [];
  for (let i = 0; i < cs.length; i += factor) {
    const g = cs.slice(i, i + factor); if (!g.length) break;
    out.push({ t: g[0].t, o: g[0].o, h: Math.max(...g.map((c) => c.h)), l: Math.min(...g.map((c) => c.l)), c: g[g.length - 1].c, v: g.reduce((s, c) => s + (c.v || 0), 0) });
  }
  return out;
};

export default resampleCandles;
