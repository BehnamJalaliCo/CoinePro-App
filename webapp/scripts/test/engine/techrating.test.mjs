// تست آفلاین technicalRating — اجرا پس از bundle (چون از ./indicators می‌آید):
//   npx --no-install esbuild src/bazaarnama/techRating.js --bundle --format=esm --outfile=test/.techrating.bundle.mjs
//   node test/techrating.test.mjs
import { technicalRating, ratingLabel, momentumSignal, adxSignal, bullBearSignal } from '../../../src/engine/techRating.js';

let fails = 0;
const ok = (n, c) => { if (!c) { fails++; console.log('FAIL ' + n); } else console.log('PASS ' + n); };

// سری صعودی نویزدار (تا نوسان‌گرها مقدار متناهی بدهند)
const mk = (n, f) => { const cs = []; for (let i = 0; i < n; i++) { const base = f(i); cs.push({ o: base, h: base + 0.5, l: base - 0.5, c: base }); } return cs; };
const up = mk(250, (i) => 100 + i * 0.5 + Math.sin(i / 5) * 1.2);   // روند صعودی
const down = mk(250, (i) => 300 - i * 0.5 + Math.sin(i / 5) * 1.2); // روند نزولی

const R = technicalRating(up);
ok('returns object', R && typeof R === 'object');
ok('has all fields', R && ['overall','ma','osc','label','maBuy','maSell','maNeutral','oscBuy','oscSell','oscNeutral'].every((k) => k in R));
// maTot = 12 (6×2) + Hull + Ichimoku = 14 روی سری ۲۵۰تایی
const maTot = R.maBuy + R.maSell + R.maNeutral;
ok(`maTot == 14 without volume (VWMA skipped) (got ${maTot})`, maTot === 14);
// ── VWMA(20): پانزدهمین MA؛ با حجم معتبر maTot=15، بدون حجم 14 (بی‌رگرسیون) ── #264
const upV = up.map((c, i) => ({ ...c, v: 1000 + i }));
const RV = technicalRating(upV);
const maTotV = RV.maBuy + RV.maSell + RV.maNeutral;
ok(`maTot == 15 with volume (VWMA counted) (got ${maTotV})`, maTotV === 15);
ok(`uptrend+volume: VWMA rated Buy so maBuy 14->15 (${R.maBuy}->${RV.maBuy})`, RV.maBuy === R.maBuy + 1);
// همه‌حجم‌صفر ⇒ VWMA=null ⇒ رد می‌شود ⇒ maTot=14 (اثبات گارد null)
const upV0 = up.map((c) => ({ ...c, v: 0 }));
const R0 = technicalRating(upV0);
ok(`all-zero volume: maTot == 14 (VWMA null-skipped) (got ${R0.maBuy + R0.maSell + R0.maNeutral})`, (R0.maBuy + R0.maSell + R0.maNeutral) === 14);
const oscTot = R.oscBuy + R.oscSell + R.oscNeutral;
ok(`oscTot == 11 (got ${oscTot})`, oscTot === 11); // 9 + ADX + BullBearPower = 11 مثل TV (#265)

// ── adxSignal — قاعده‌ی crossover-محور TV ── #265
ok('adx: +DI crosses ABOVE -DI, ADX>20 -> +1', adxSignal(25, 30, 20, 18, 22) === 1);
ok('adx: +DI crosses BELOW -DI, ADX>20 -> -1', adxSignal(25, 20, 30, 22, 18) === -1);
ok('adx: +DI already above (no cross) -> 0', adxSignal(25, 30, 20, 28, 22) === 0);
ok('adx: crossover but ADX<=20 -> 0', adxSignal(15, 30, 20, 18, 22) === 0);
ok('adx: non-finite -> 0', adxSignal(NaN, 30, 20, 18, 22) === 0);
// ── bullBearSignal ── #265
ok('bbp: bear<0 rising & bull rising -> +1', bullBearSignal(2, 1, -1, -2) === 1);
ok('bbp: bull>0 falling & bear falling -> -1', bullBearSignal(1, 2, -2, -1) === -1);
ok('bbp: flat (no rise/fall) -> 0', bullBearSignal(1, 1, -1, -1) === 0);
ok('bbp: bear<0 rising but bull NOT rising -> 0', bullBearSignal(1, 2, -1, -2) === 0);
ok('bbp: non-finite -> 0', bullBearSignal(NaN, 1, -1, -2) === 0);

// ── momentumSignal — قاعده‌ی صعود/نزول MOM (نه علامت MOM) مثل نمونه‌ی زنده‌ی TV ──
// سری شتاب‌گیرنده (تفاوت‌ها بزرگ‌تر می‌شوند) ⇒ MOM صعودی ⇒ +۱
const accel = []; { let x = 100; for (let i = 0; i < 30; i++) { x += 1 + i * 0.1; accel.push(x); } }
ok('momentum rising -> +1', momentumSignal(accel, 10) === 1);
// سری کندشونده (تفاوت‌ها کوچک‌تر) ⇒ MOM نزولی ⇒ −۱
const decel = []; { let x = 100; for (let i = 0; i < 30; i++) { x += Math.max(0.1, 5 - i * 0.15); decel.push(x); } }
ok('momentum falling -> -1', momentumSignal(decel, 10) === -1);
// خطی کامل: MOM ثابت ⇒ MOM==MOM_prev ⇒ 0
const linr = Array.from({ length: 30 }, (_, i) => 100 + i * 2);
ok('momentum flat (linear) -> 0', momentumSignal(linr, 10) === 0);
// نمونه‌ی کلیدی TV: MOM می‌تواند منفی ولی صعودی باشد ⇒ +۱ (اثبات «رفتار صعود/نزول» نه علامت)
// افت با شیب کاهنده: close نزولی ولی افت ۱۰کندلی کوچک‌تر می‌شود ⇒ MOM (منفی) صعودی ⇒ +۱
const negRise = []; { let x = 300; for (let i = 0; i < 30; i++) { x -= Math.max(0.2, 5 - i * 0.15); negRise.push(x); } }
{ const n = negRise.length; const mNow = negRise[n-1]-negRise[n-11], mPrev = negRise[n-2]-negRise[n-12];
  ok(`neg-but-rising MOM -> +1 (MOM=${mNow.toFixed(3)}<0, rising vs ${mPrev.toFixed(3)})`, mNow < 0 && mNow > mPrev && momentumSignal(negRise, 10) === 1); }
// داده‌ی ناکافی ⇒ 0 (بدون throw)
ok('momentum short data -> 0', momentumSignal([1,2,3], 10) === 0);
// روند صعودی خالص: قیمت بالای همه‌ی MAها ⇒ maBuy == maTot، maScore == 1
ok(`uptrend: maBuy==maTot (${R.maBuy}/${maTot}), maScore=${R.ma.toFixed(2)}`, R.maBuy === maTot && Math.abs(R.ma - 1) < 1e-9);
ok('overall = (ma+osc)/2', Math.abs(R.overall - (R.ma + R.osc) / 2) < 1e-9);
ok('label = ratingLabel(overall)', R.label === ratingLabel(R.overall));
ok('all counts non-negative', [R.maBuy,R.maSell,R.maNeutral,R.oscBuy,R.oscSell,R.oscNeutral].every((x) => x >= 0));
ok('all scores finite', [R.overall,R.ma,R.osc].every((x) => Number.isFinite(x)));

// روند نزولی: قیمت زیر همه‌ی MAها ⇒ maSell==maTot، maScore==-1
const D = technicalRating(down);
const dMaTot = D.maBuy + D.maSell + D.maNeutral;
ok(`downtrend: maScore strongly negative (${D.ma.toFixed(2)}), maSell>=tot-1 (${D.maSell}/${dMaTot})`, D.ma < -0.7 && D.maSell >= dMaTot - 1);

// ratingLabel آستانه‌ها
ok('ratingLabel thresholds', ratingLabel(0.6)==='strongBuy' && ratingLabel(0.2)==='buy' && ratingLabel(0)==='neutral' && ratingLabel(-0.2)==='sell' && ratingLabel(-0.6)==='strongSell');
// کمتر از ۶۰ کندل ⇒ null
ok('null for <60 candles', technicalRating(mk(30, (i)=>100+i)) === null);

console.log(`\n=== ${fails === 0 ? 'ALL PASS' : fails + ' FAILED'} ===`);
process.exit(fails === 0 ? 0 : 1);
