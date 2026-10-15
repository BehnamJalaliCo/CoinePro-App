// تستِ عددیِ آفلاینِ resampleCandles — اجرا: node test/resample.test.mjs
// (ماژولِ خالص است، بدونِ bundle مستقیماً import می‌شود.)
import { resampleCandles } from '../../../src/engine/resample.js';

let fails = 0;
const ok = (name, cond) => { if (!cond) { fails++; console.log('FAIL ' + name); } else console.log('PASS ' + name); };
const eq = (name, a, b) => ok(name + ` (${a} == ${b})`, a === b);

// ── H1 → H2 (factor 2)، شروع از ساعتِ فردِ ۱۵:۰۰ UTC — باید به مرزِ زوجِ ۲ساعته هم‌تراز شود ──
const H = 3600;
const base15 = Math.floor(Date.UTC(2026, 0, 1, 15, 0, 0) / 1000); // 15:00 UTC
const h1 = [];
for (let i = 0; i < 8; i++) h1.push({ t: base15 + i * H, o: 10 + i, h: 20 + i, l: 5 + i, c: 15 + i, v: i + 1 });
// ساعت‌ها: 15,16,17,18,19,20,21,22
const h2 = resampleCandles(h1, 2);
ok('H2 all aligned to even 2h boundary (t%7200==0)', h2.every((c) => c.t % 7200 === 0));
// باکت‌ها: [14:00]=(15), [16:00]=(16,17), [18:00]=(18,19), [20:00]=(20,21), [22:00]=(22)
eq('H2 count', h2.length, 5);
// باکتِ 16:00 = کندل‌های index 1,2 (16:00,17:00)
const b16 = h2.find((c) => new Date(c.t * 1000).getUTCHours() === 16);
ok('H2 16:00 exists', !!b16);
eq('H2 16:00 open = h1[1].o', b16.o, h1[1].o);
eq('H2 16:00 close = h1[2].c', b16.c, h1[2].c);
eq('H2 16:00 high = max(h1[1..2].h)', b16.h, Math.max(h1[1].h, h1[2].h));
eq('H2 16:00 low = min(h1[1..2].l)', b16.l, Math.min(h1[1].l, h1[2].l));
eq('H2 16:00 vol = sum(h1[1..2].v)', b16.v, h1[1].v + h1[2].v);
// لبهٔ آغازین: باکتِ 14:00 فقط کندلِ 15:00 را دارد (partial leading — مثلِ TV)
const b14 = h2.find((c) => new Date(c.t * 1000).getUTCHours() === 14);
eq('H2 14:00 (partial) open = h1[0].o', b14.o, h1[0].o);
eq('H2 14:00 (partial) close = h1[0].c', b14.c, h1[0].c);

// ── M15 → M30 (factor 2) هم‌ترازی ──
const M = 900; const bm = Math.floor(Date.UTC(2026, 0, 1, 9, 15, 0) / 1000); // 09:15
const m15 = []; for (let i = 0; i < 6; i++) m15.push({ t: bm + i * M, o: 1, h: 2, l: 0, c: 1, v: 1 });
const m30 = resampleCandles(m15, 2);
ok('M30 aligned to 30-min boundary (t%1800==0)', m30.every((c) => c.t % 1800 === 0));

// ── D1 → W1 (factor 5): شمارشی (نه هم‌ترازِ زمانی) با شکافِ آخرهفته ──
const D = 86400; const bd = Math.floor(Date.UTC(2026, 0, 5, 0, 0, 0) / 1000); // دوشنبه
// ۱۰ روزِ معاملاتی با شکافِ آخرهفته (بعد از ۵ روز، ۳ روز بپر)
const d1 = []; let t = bd;
for (let i = 0; i < 10; i++) { d1.push({ t, o: i, h: i + 5, l: i - 5, c: i + 1, v: 100 }); t += (i % 5 === 4) ? 3 * D : D; }
const w1 = resampleCandles(d1, 5);
eq('W1 count (10 daily / 5) = 2', w1.length, 2);
eq('W1[0] open = d1[0].o (count-based first)', w1[0].o, d1[0].o);
eq('W1[0] close = d1[4].c (count-based 5th)', w1[0].c, d1[4].c);
eq('W1[0] high = max(d1[0..4].h)', w1[0].h, Math.max(...d1.slice(0, 5).map((c) => c.h)));
eq('W1[1] open = d1[5].o', w1[1].o, d1[5].o);

// ── invariants: h>=l, close continuity across all ──
ok('all resampled h>=l', [...h2, ...m30, ...w1].every((c) => c.h >= c.l));
// factor<=1 → بدونِ تغییر
ok('factor 1 = passthrough', resampleCandles(h1, 1) === h1);

console.log(`\n=== ${fails === 0 ? 'ALL PASS' : fails + ' FAILED'} ===`);
process.exit(fails === 0 ? 0 : 1);
