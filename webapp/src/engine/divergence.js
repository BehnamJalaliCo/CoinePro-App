// ─────────────────────────────────────────────────────────────────────────
// تشخیصِ خودکارِ واگراییِ RSI (Regular + Hidden) — مثلِ اندیکاتورِ Divergence تریدینگ‌ویو
// الگوریتمِ استانداردِ تحلیلِ تکنیکال (دامنهٔ عمومی)، پیاده‌سازیِ اورجینال.
// ورودی: [{t,o,h,l,c,v}] — خروجی: [{t, dir:'bull'|'bear', label}]
// ─────────────────────────────────────────────────────────────────────────

// RSI وایلدر
function rsi(closes, period = 14) {
  const out = new Array(closes.length).fill(null);
  if (closes.length <= period) return out;
  let gain = 0, loss = 0;
  for (let i = 1; i <= period; i++) { const d = closes[i] - closes[i - 1]; if (d >= 0) gain += d; else loss -= d; }
  gain /= period; loss /= period;
  out[period] = loss === 0 ? 100 : 100 - 100 / (1 + gain / loss);
  for (let i = period + 1; i < closes.length; i++) {
    const d = closes[i] - closes[i - 1];
    const g = d > 0 ? d : 0, l = d < 0 ? -d : 0;
    gain = (gain * (period - 1) + g) / period;
    loss = (loss * (period - 1) + l) / period;
    out[i] = loss === 0 ? 100 : 100 - 100 / (1 + gain / loss);
  }
  return out;
}

// نقاطِ چرخشِ تأییدشده (pivot) با پنجرهٔ چپ/راست
function pivots(cs, rsiArr, L, R) {
  const lows = [], highs = [];
  for (let i = L; i < cs.length - R; i++) {
    if (rsiArr[i] == null) continue;
    let isLow = true, isHigh = true;
    for (let j = i - L; j <= i + R; j++) {
      if (j === i) continue;
      if (cs[j].l <= cs[i].l) isLow = false;
      if (cs[j].h >= cs[i].h) isHigh = false;
    }
    if (isLow) lows.push(i);
    if (isHigh) highs.push(i);
  }
  return { lows, highs };
}

export function detectDivergences(cs, { period = 14, left = 5, right = 5 } = {}) {
  if (!cs || cs.length < period + left + right + 2) return [];
  const closes = cs.map((c) => c.c);
  const r = rsi(closes, period);
  const { lows, highs } = pivots(cs, r, left, right);
  const raw = [];

  // واگرایی روی کف‌ها → سیگنالِ صعودی
  for (let k = 1; k < lows.length; k++) {
    const a = lows[k - 1], b = lows[k];
    if (r[a] == null || r[b] == null) continue;
    const pts = [{ t: cs[a].t, price: cs[a].l }, { t: cs[b].t, price: cs[b].l }];
    if (cs[b].l < cs[a].l && r[b] > r[a]) raw.push({ t: cs[b].t, dir: 'bull', label: 'واگراییِ صعودی', points: pts });
    else if (cs[b].l > cs[a].l && r[b] < r[a]) raw.push({ t: cs[b].t, dir: 'bull', label: 'واگراییِ صعودیِ مخفی', points: pts });
  }
  // واگرایی روی سقف‌ها → سیگنالِ نزولی
  for (let k = 1; k < highs.length; k++) {
    const a = highs[k - 1], b = highs[k];
    if (r[a] == null || r[b] == null) continue;
    const pts = [{ t: cs[a].t, price: cs[a].h }, { t: cs[b].t, price: cs[b].h }];
    if (cs[b].h > cs[a].h && r[b] < r[a]) raw.push({ t: cs[b].t, dir: 'bear', label: 'واگراییِ نزولی', points: pts });
    else if (cs[b].h < cs[a].h && r[b] > r[a]) raw.push({ t: cs[b].t, dir: 'bear', label: 'واگراییِ نزولیِ مخفی', points: pts });
  }

  // ادغام بر اساسِ زمان
  const byTime = new Map();
  raw.forEach((o) => {
    const e = byTime.get(o.t);
    if (!e) byTime.set(o.t, { t: o.t, dir: o.dir, labels: [o.label], points: o.points });
    else { e.labels.push(o.label); if (e.dir !== o.dir) e.dir = 'neutral'; }
  });
  return Array.from(byTime.values()).map((e) => ({ t: e.t, dir: e.dir, label: e.labels.join('، '), points: e.points }));
}
