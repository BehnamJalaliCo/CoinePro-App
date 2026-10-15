// volumeProfile — نیم‌رخِ حجم (Volume Profile سبکِ TradingView): حجمِ هر کندل روی باکت‌های
//   قیمتیِ بازهٔ high-low توزیع می‌شود (نه فقط میانه)، سپس POC و ناحیهٔ ارزشِ ۷۰٪.
//   خالص و بدونِ importِ خارجی ⇒ مستقیماً در Node قابلِ تست (test/volumeProfile.test.mjs).
//   فارکسِ بدونِ حجم ⇒ w=1 (نیم‌رخِ تعدادِ کندل، معادلِ TPO).
export function computeProfile(candles, B = 48) {
  if (!candles || !candles.length) return null;
  let lo = Infinity, hi = -Infinity;
  for (const c of candles) { const l = Math.min(c.l, c.h), h = Math.max(c.l, c.h); if (l < lo) lo = l; if (h > hi) hi = h; }
  if (!Number.isFinite(lo) || !Number.isFinite(hi)) return null;
  const step = (hi - lo) / B || 1;
  const buckets = Array.from({ length: B }, (_, i) => ({ lo: lo + i * step, hi: lo + (i + 1) * step, vol: 0, poc: false, va: false }));
  // توزیعِ یکنواختِ حجمِ هر کندل روی باکت‌هایی که بازهٔ high-lowِ آن را می‌پوشانند (سبکِ TV، نه فقط میانه).
  for (const c of candles) {
    const w = (c.v || 1);
    const cl = Math.max(lo, Math.min(c.l, c.h)), ch = Math.min(hi, Math.max(c.l, c.h));
    const loB = Math.max(0, Math.min(B - 1, Math.floor((cl - lo) / step)));
    const hiB = Math.max(0, Math.min(B - 1, Math.floor((ch - lo) / step)));
    const nB = (hiB - loB + 1) || 1;
    const per = w / nB;
    for (let i = loB; i <= hiB; i++) buckets[i].vol += per;
  }
  // POC = باکتِ پرحجم‌ترین
  let pocI = 0; for (let i = 1; i < B; i++) if (buckets[i].vol > buckets[pocI].vol) pocI = i;
  buckets[pocI].poc = true;
  // ناحیهٔ ارزش: گسترشِ دوطرفه از POC تا پوششِ ۷۰٪ حجم (همسایهٔ پرحجم‌تر اول).
  const total = buckets.reduce((s, b) => s + b.vol, 0);
  let loI = pocI, hiI = pocI, acc = buckets[pocI].vol;
  while (acc < total * 0.7 && (loI > 0 || hiI < B - 1)) {
    const below = loI > 0 ? buckets[loI - 1].vol : -1;
    const above = hiI < B - 1 ? buckets[hiI + 1].vol : -1;
    if (above >= below) { hiI++; acc += buckets[hiI].vol; } else { loI--; acc += buckets[loI].vol; }
  }
  for (let i = loI; i <= hiI; i++) buckets[i].va = true;
  return buckets;
}

export default computeProfile;
