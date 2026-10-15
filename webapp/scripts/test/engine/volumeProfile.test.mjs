// تستِ آفلاینِ computeProfile — اجرا: node test/volumeProfile.test.mjs
import { computeProfile } from '../../../src/engine/volumeProfile.js';

let fails = 0;
const ok = (n, c) => { if (!c) { fails++; console.log('FAIL ' + n); } else console.log('PASS ' + n); };

// دو کندل: A پهن (کلِ بازه)، B باریک (وسط) — با B=10 باکت، lo=100 hi=110 step=1
const cs = [
  { l: 100, h: 110, v: 10 }, // کلِ ۱۰ باکت
  { l: 104, h: 106, v: 20 }, // باکت‌های 4,5,6
];
const bk = computeProfile(cs, 10);
ok('returns 10 buckets', bk && bk.length === 10);
const total = bk.reduce((s, b) => s + b.vol, 0);
ok(`total volume preserved == 30 (got ${total.toFixed(3)})`, Math.abs(total - 30) < 1e-6);
// توزیع (نه فقط میانه): باکتِ لبه‌ایِ ۰ باید سهمِ کندلِ A را بگیرد (میانه‌محور صفر می‌داد)
ok(`bucket[0] distributed >0 (got ${bk[0].vol.toFixed(3)})`, bk[0].vol > 0);
ok('bucket[0] == 1 (A spread evenly: 10/10)', Math.abs(bk[0].vol - 1) < 1e-6);
// POC در ناحیهٔ هم‌پوشانی (۴/۵/۶ که B هم آن‌جاست)
ok(`POC in overlap region 4..6 (poc idx=${bk.findIndex(b=>b.poc)})`, [4,5,6].includes(bk.findIndex(b=>b.poc)));
// دقیقاً یک POC
ok('exactly one POC', bk.filter(b=>b.poc).length === 1);
// ناحیهٔ ارزش پیوسته و شاملِ POC
const vaIdx = bk.map((b,i)=>b.va?i:-1).filter(i=>i>=0);
ok('VA non-empty', vaIdx.length > 0);
ok('VA contiguous', vaIdx.every((v,i)=> i===0 || v === vaIdx[i-1]+1));
ok('VA includes POC', bk[bk.findIndex(b=>b.poc)].va === true);
// VA پوششِ >=70٪
const vaVol = vaIdx.reduce((s,i)=>s+bk[i].vol,0);
ok(`VA covers >=70% (got ${(vaVol/total*100).toFixed(1)}%)`, vaVol >= total*0.7 - 1e-9);
// فارکسِ بی‌حجم: w=1 هر کندل ⇒ total == تعدادِ کندل
const noVol = computeProfile([{l:1,h:2},{l:1.5,h:2.5},{l:1,h:3}], 8);
const nvTotal = noVol.reduce((s,b)=>s+b.vol,0);
ok(`no-volume: total == candle count 3 (got ${nvTotal.toFixed(2)})`, Math.abs(nvTotal - 3) < 1e-6);
// حالتِ خالی/تک
ok('empty → null', computeProfile([], 10) === null);
ok('null → null', computeProfile(null, 10) === null);

console.log(`\n=== ${fails === 0 ? 'ALL PASS' : fails + ' FAILED'} ===`);
process.exit(fails === 0 ? 0 : 1);
