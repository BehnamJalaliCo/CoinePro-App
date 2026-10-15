// تست آفلاین chartbuilders (avgRange/renko/rangeBars/lineBreak) — node test/chartbuilders.test.mjs
import { avgRange, renko, rangeBars, lineBreak, kagi, pnf } from '../../../src/engine/chartbuilders.js';

let fails = 0;
const ok = (n, c) => { if (!c) { fails++; console.log('FAIL ' + n); } else console.log('PASS ' + n); };

// ── avgRange: باید ATR p بار آخر باشد (نه اولین‌ها) ──
// ۲۰ کندل: close=100، h=100+i، l=100 ⇒ TR[i>=1]=i. آخرین ۱۴ = i=6..19 ⇒ میانگین=12.5
const cs = []; for (let i = 0; i < 20; i++) cs.push({ o: 100, h: 100 + i, l: 100, c: 100 });
const ar = avgRange(cs, 14);
ok(`avgRange recent-14 == 12.5 (got ${ar})`, Math.abs(ar - 12.5) < 1e-9);
ok('avgRange(<p bars) works', Math.abs(avgRange(cs.slice(0, 5), 14) - ((1+2+3+4)/4)) < 1e-9); // TR i=1..4 = 1,2,3,4

// ── renko: آجرها دقیقاً brick بلند، جهت درست ──
const up = renko([{o:100,h:100,l:100,c:100},{o:0,h:0,l:0,c:101},{o:0,h:0,l:0,c:102},{o:0,h:0,l:0,c:103}], 1);
ok(`renko up: 3 bricks (got ${up.length})`, up.length === 3);
ok('renko up: each brick exactly 1 tall & up', up.every((b) => Math.abs((b.c - b.o) - 1) < 1e-9 && b.c > b.o && Math.abs(b.h - b.c) < 1e-9 && Math.abs(b.l - b.o) < 1e-9));
const dn = renko([{o:100,h:100,l:100,c:100},{o:0,h:0,l:0,c:98}], 1);
ok(`renko down: 2 bricks (got ${dn.length})`, dn.length === 2);
ok('renko down: each 1 tall & down', dn.every((b) => Math.abs((b.o - b.c) - 1) < 1e-9 && b.c < b.o));
ok('renko: times strictly increasing', up.every((b, i) => i === 0 || b.t > up[i-1].t));

// ── rangeBars: بار وقتی بازه >= rng بسته می‌شود ──
const rb = rangeBars([{o:100,h:100,l:100,c:100},{o:100,h:103,l:100,c:103},{o:103,h:103,l:100,c:100}], 2);
ok('rangeBars: produced bars', rb.length >= 1);
ok('rangeBars: all h>=l', rb.every((b) => b.h >= b.l));
ok('rangeBars: each bar range >= rng (approx)', rb.every((b) => (b.h - b.l) >= 2 - 1e-9));

// ── lineBreak: h>=l، زمان صعودی ──
const lb = lineBreak([{o:100,c:100,h:100,l:100},{o:0,c:102,h:0,l:0},{o:0,c:104,h:0,l:0},{o:0,c:101,h:0,l:0},{o:0,c:98,h:0,l:0}], 3);
ok('lineBreak: produced lines', lb.length >= 1);
ok('lineBreak: all h>=l', lb.every((b) => b.h >= b.l));
ok('lineBreak: times strictly increasing', lb.every((b, i) => i === 0 || b.t > lb[i-1].t));

// ── kagi: پولبک کوچک نادیده، برگشت روی حرکت >=reversal ──
const kg = kagi([{t:1,c:100,h:100,l:100},{t:2,c:105,h:105,l:105},{t:3,c:103,h:103,l:103},{t:4,c:98,h:98,l:98}], 3);
ok(`kagi: 2 points (up-extreme then reversal) got ${kg.length}`, kg.length === 2);
ok('kagi: up-extreme=105 (small pullback 103 ignored)', Math.abs(kg[0].value - 105) < 1e-9);
ok('kagi: reversal point=98 (moved >=reversal down)', Math.abs(kg[1].value - 98) < 1e-9);
ok('kagi: times strictly increasing', kg.every((q,i)=> i===0 || q.t > kg[i-1].t));

// ── pnf (Point & Figure): ستون‌های X/O روی گرید box، برگشت با reversal*box (رگرسیون + ناوردا) ──
{
  const mk = (arr) => arr.map((c, i) => ({ o: c, h: c, l: c, c, t: i }));
  // box=1, reversal=3: 10→13 (سه X box-گرید)، افت ۳ باکس تا 10 ⇒ برگشت، سپس 9.
  const pf = pnf(mk([10, 11, 12, 13, 10, 9]), 1, 3);
  ok(`pnf values == [11,12,13,10,9] (got ${pf.map((p) => p.value)})`, JSON.stringify(pf.map((p) => p.value)) === JSON.stringify([11, 12, 13, 10, 9]));
  ok('pnf: no consecutive-equal values', pf.every((p, i) => i === 0 || p.value !== pf[i - 1].value));
  ok('pnf: up-column strictly rising before reversal', pf[0].value < pf[1].value && pf[1].value < pf[2].value);
  ok('pnf: reversal at exactly reversal*box drop (13→10)', pf[3].value === 10);
  // box خودکار (box=0 ⇒ avgRange) نباید بیندازد و باید روی روند قوی نقطه بدهد
  const auto = pnf(mk([100, 101, 103, 106, 110, 115]), 0, 3, 5);
  ok(`pnf: auto-box produces points on strong trend (got ${auto.length})`, Array.isArray(auto) && auto.length >= 1);
  ok('pnf: empty=[]', pnf([], 1, 3).length === 0);
}

// خالی
ok('avgRange empty=1', avgRange([]) === 1);
ok('renko empty=[]', renko([], 1).length === 0);

console.log(`\n=== ${fails === 0 ? 'ALL PASS' : fails + ' FAILED'} ===`);
process.exit(fails === 0 ? 0 : 1);
