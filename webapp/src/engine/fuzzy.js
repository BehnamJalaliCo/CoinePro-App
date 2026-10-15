// بازارنما — fuzzy matcher سبک (بدونِ وابستگی). خروجی: امتیاز (هرچه بیشتر بهتر) یا -1.
// رتبه‌بندی: تطبیقِ دقیق > startsWith > زیررشتهٔ پیوسته > subsequence (حروفِ پراکنده با ترتیب).
function scoreOne(hay, needle) {
  if (!needle) return 0;
  const H = hay.toLowerCase(), N = needle.toLowerCase();
  if (H === N) return 1000;
  if (H.startsWith(N)) return 800 - (H.length - N.length); // پیشوند، کوتاه‌تر بهتر
  const idx = H.indexOf(N);
  if (idx >= 0) return 600 - idx - (H.length - N.length) * 0.2; // زیررشتهٔ پیوسته
  // subsequence: همهٔ حروفِ N به ترتیب داخلِ H باشند
  let i = 0, j = 0, gaps = 0, last = -1;
  while (i < H.length && j < N.length) {
    if (H[i] === N[j]) { if (last >= 0) gaps += i - last - 1; last = i; j++; }
    i++;
  }
  if (j === N.length) return 300 - gaps; // تطبیقِ پراکنده، فاصلهٔ کمتر بهتر
  return -1;
}

// روی نماد و توضیح (فارسی)؛ بهترین امتیاز را برمی‌گرداند.
export function matchSymbol(meta, q) {
  if (!q) return 0;
  const a = scoreOne(meta.symbol, q);
  const b = scoreOne(meta.desc || '', q);
  const c = meta.base ? scoreOne(meta.base, q) : -1;
  let s = Math.max(a, b, c);
  if (s < 0) return -1;
  if (meta.popular) s += 25; // بوستِ محبوب‌ها
  return s;
}

// موقعیتِ حروفِ منطبقِ کوئری درونِ متن — برای های‌لایتِ TradingView-style.
// همان رتبه‌بندیِ scoreOne: زیررشتهٔ پیوسته (exact/prefix شاملِ آن) وگرنه subsequence.
// خروجی: آرایهٔ ایندکس‌های منطبق در `text` (خالی اگر تطبیق نبود).
export function highlightPositions(text, q) {
  const H = String(text || ''), N = String(q || '').trim();
  if (!N) return [];
  const hl = H.toLowerCase(), nl = N.toLowerCase();
  const idx = hl.indexOf(nl);
  if (idx >= 0) { const out = []; for (let k = 0; k < nl.length; k++) out.push(idx + k); return out; }
  const out = [];
  let j = 0;
  for (let i = 0; i < hl.length && j < nl.length; i++) {
    if (hl[i] === nl[j]) { out.push(i); j++; }
  }
  return j === nl.length ? out : [];
}

export function searchSymbols(metaList, q, cat) {
  const filtered = cat && cat !== 'all' ? metaList.filter((m) => m.cat === cat) : metaList;
  if (!q) {
    // #۷ بدونِ کوئری: محبوب‌ها اول، سپس ترتیبِ ورودی حفظ می‌شود (کریپتو از بک‌اند بر اساسِ ارزشِ بازار
    // مرتب می‌آید؛ sortِ پایدارِ JS آن را نگه می‌دارد). tiebreakِ الفبایی حذف شد چون ترتیبِ mcap را می‌شکست.
    return [...filtered].sort((x, y) => (Number(y.popular) - Number(x.popular)));
  }
  const scored = [];
  for (const m of filtered) { const s = matchSymbol(m, q); if (s >= 0) scored.push([s, m]); }
  scored.sort((p, n) => n[0] - p[0] || p[1].symbol.localeCompare(n[1].symbol));
  return scored.map((x) => x[1]);
}
