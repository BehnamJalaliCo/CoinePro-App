// ─────────────────────────────────────────────────────────────────────────
// تشخیصِ گپِ قیمت — کندلی که بازشدنش از بستهٔ کندلِ قبل فاصلهٔ معنادار دارد
// (مثلِ گپِ آخرِهفتهٔ فارکس یا گپِ بازشدنِ بازار). ورودی: [{t,o,h,l,c}]
// خروجی: [{t, dir:'up'|'down', pct}]
// ─────────────────────────────────────────────────────────────────────────
export function detectGaps(cs, minPct = 0.1) {
  if (!cs || cs.length < 2) return [];
  const out = [];
  for (let i = 1; i < cs.length; i++) {
    const prevC = cs[i - 1].c, open = cs[i].o;
    if (prevC == null || open == null || !prevC) continue;
    const gap = open - prevC;
    const pct = (gap / prevC) * 100;
    if (Math.abs(pct) >= minPct) out.push({ t: cs[i].t, dir: gap > 0 ? 'up' : 'down', pct });
  }
  return out;
}
