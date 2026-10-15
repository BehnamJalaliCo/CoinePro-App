// ─────────────────────────────────────────────────────────────────────────
// تشخیصِ خودکارِ الگوهای هارمونیک (Gartley / Bat / Butterfly / Crab)
// بر پایهٔ نسبت‌های فیبوناچیِ کلاسیک روی نقاطِ چرخشِ XABCD (دامنهٔ عمومی).
// ورودی: [{t,o,h,l,c}] — خروجی: [{t, dir:'bull'|'bear', label}]
// ─────────────────────────────────────────────────────────────────────────

// نقاطِ چرخشِ متناوب (zigzag ساده بر پایهٔ pivotهای تأییدشده)
function swings(cs, L = 3, R = 3) {
  const piv = [];
  for (let i = L; i < cs.length - R; i++) {
    let isHigh = true, isLow = true;
    for (let j = i - L; j <= i + R; j++) { if (j === i) continue; if (cs[j].h >= cs[i].h) isHigh = false; if (cs[j].l <= cs[i].l) isLow = false; }
    if (isHigh) piv.push({ i, t: cs[i].t, price: cs[i].h, type: 'H' });
    if (isLow) piv.push({ i, t: cs[i].t, price: cs[i].l, type: 'L' });
  }
  piv.sort((a, b) => a.i - b.i);
  // متناوب‌سازی: بینِ دو چرخشِ هم‌نوعِ پشتِ‌سرهم، فقط افراطی‌تر را نگه دار
  const out = [];
  for (const p of piv) {
    const last = out[out.length - 1];
    if (!last || last.type !== p.type) { out.push(p); continue; }
    if ((p.type === 'H' && p.price > last.price) || (p.type === 'L' && p.price < last.price)) out[out.length - 1] = p;
  }
  return out;
}

const inRange = (v, lo, hi) => v >= lo && v <= hi;

// تعریفِ نسبت‌های هر الگو: [AB/XA, BC/AB, CD/BC, AD/XA] با تلورانس
const PATTERNS = [
  { name: 'گارتلی', ab: [0.55, 0.68], bc: [0.38, 0.90], cd: [1.10, 1.70], ad: [0.72, 0.84] },
  { name: 'خفاش (Bat)', ab: [0.35, 0.55], bc: [0.38, 0.90], cd: [1.55, 2.70], ad: [0.84, 0.92] },
  { name: 'پروانه (Butterfly)', ab: [0.74, 0.82], bc: [0.38, 0.90], cd: [1.55, 2.30], ad: [1.24, 1.65] },
  { name: 'خرچنگ (Crab)', ab: [0.35, 0.65], bc: [0.38, 0.90], cd: [2.10, 3.70], ad: [1.55, 1.70] },
];

export function detectHarmonics(cs) {
  if (!cs || cs.length < 40) return [];
  const sw = swings(cs);
  if (sw.length < 5) return [];
  const out = [];
  // روی چند دنبالهٔ ۵-نقطه‌ایِ آخر جست‌وجو کن (نه فقط آخری) تا الگوهای تازه دیده شوند
  for (let s = Math.max(0, sw.length - 8); s + 4 < sw.length; s++) {
    const [X, A, B, C, D] = sw.slice(s, s + 5);
    // باید متناوب باشند (H,L,H,L,H یا L,H,L,H,L)
    if (X.type === A.type || A.type === B.type || B.type === C.type || C.type === D.type) continue;
    const XA = Math.abs(A.price - X.price), AB = Math.abs(B.price - A.price), BC = Math.abs(C.price - B.price), CD = Math.abs(D.price - C.price), AD = Math.abs(D.price - A.price);
    if (XA === 0 || AB === 0 || BC === 0) continue;
    const rAB = AB / XA, rBC = BC / AB, rCD = CD / BC, rAD = AD / XA;
    for (const p of PATTERNS) {
      if (inRange(rAB, ...p.ab) && inRange(rBC, ...p.bc) && inRange(rCD, ...p.cd) && inRange(rAD, ...p.ad)) {
        // نقطهٔ D کف باشد → صعودی (خرید در تکمیل)، سقف باشد → نزولی
        const dir = D.type === 'L' ? 'bull' : 'bear';
        out.push({ t: D.t, dir, label: `${p.name} ${dir === 'bull' ? '▲' : '▼'}`, points: [X, A, B, C, D].map((q) => ({ t: q.t, price: q.price })) });
        break;
      }
    }
  }
  // ادغام بر اساسِ زمان
  const byT = new Map();
  out.forEach((o) => { if (!byT.has(o.t)) byT.set(o.t, o); });
  return Array.from(byT.values()).sort((a, b) => a.t - b.t);
}
