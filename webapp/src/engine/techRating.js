// امتیاز تکنیکال (Technical Rating سبک TradingView) — از میانگین‌های متحرک + نوسان‌گرها
// یک امتیاز خرید/فروش کلی می‌سازد و به «خرید قوی … فروش قوی» نگاشت می‌کند.
// همه‌چیز از همان helperهای اندیکاتور موجود محاسبه می‌شود؛ هیچ داده/سرویس جدیدی لازم نیست.
import { sma, ema, rsi, macd, stoch, cci, williamsR, ao, stochRsi, hma, vwma, adxDI, elderRay } from './indicators.js';
import { ultimateOsc } from './indicators_ext_b.js';

const lastN = (a) => { if (!Array.isArray(a)) return null; for (let i = a.length - 1; i >= 0; i--) { const v = a[i]; if (v != null && Number.isFinite(v)) return v; } return null; };

// امتیاز [-1..1] → کلید. آستانه‌ها هم‌تراز Technical Rating TV.
export const ratingLabel = (s) => (s > 0.5 ? 'strongBuy' : s > 0.1 ? 'buy' : s < -0.5 ? 'strongSell' : s < -0.1 ? 'sell' : 'neutral');

// سیگنال Momentum(len) به سبک Technical Rating TV: MOM=close−close[len]؛
//   خرید(+۱) اگر MOM صعودی (MOM>MOM_prev)، فروش(−۱) اگر نزولی، خنثی(۰) اگر برابر یا داده ناکافی.
//   نمونه‌ی زنده‌ی TV: MOM=−0.00375 ولی Action=Buy ⇒ ملاک «صعود/نزول» است نه علامت خود MOM. #263
export function momentumSignal(closes, len = 10) {
  if (!Array.isArray(closes) || closes.length < len + 2) return 0;
  const n = closes.length;
  const momNow = closes[n - 1] - closes[n - 1 - len];
  const momPrev = closes[n - 2] - closes[n - 2 - len];
  if (!Number.isFinite(momNow) || !Number.isFinite(momPrev)) return 0;
  return momNow > momPrev ? 1 : momNow < momPrev ? -1 : 0;
}

// سیگنال ADX به سبک Technical Rating TV — crossover-محور (نه صرفاً ADX>20):
//   خرید فقط وقتی +DI از زیر −DI بالا بزند و ADX>20؛ فروش وقتی +DI زیر −DI برود و ADX>20؛ وگرنه خنثی.
//   همین توضیح نمونه‌ی زنده‌ی TV است که با ADX=30.6 هم Neutral بود (کراسی رخ نداده بود). #265
export function adxSignal(adx, plusNow, minusNow, plusPrev, minusPrev) {
  if (![adx, plusNow, minusNow, plusPrev, minusPrev].every(Number.isFinite)) return 0;
  if (adx > 20 && plusPrev < minusPrev && plusNow > minusNow) return 1;
  if (adx > 20 && plusPrev > minusPrev && plusNow < minusNow) return -1;
  return 0;
}

// سیگنال Bull Bear Power (Elder Ray، EMA13) به سبک TV:
//   خرید: bear<0 و bear صعودی (bear>bear_prev) و bull صعودی؛ فروش: bull>0 و bull نزولی و bear نزولی؛ وگرنه خنثی.
export function bullBearSignal(bullNow, bullPrev, bearNow, bearPrev) {
  if (![bullNow, bullPrev, bearNow, bearPrev].every(Number.isFinite)) return 0;
  if (bearNow < 0 && bearNow > bearPrev && bullNow > bullPrev) return 1;
  if (bullNow > 0 && bullNow < bullPrev && bearNow < bearPrev) return -1;
  return 0;
}

// برچسب فارسی + رنگ برای هر کلید.
export const RATING_FA = {
  strongBuy:  { label: 'خرید قوی',  color: '#089981' },
  buy:        { label: 'خرید',       color: '#22c55e' },
  neutral:    { label: 'خنثی',       color: '#787b86' },
  sell:       { label: 'فروش',       color: '#f0616d' },
  strongSell: { label: 'فروش قوی',  color: '#f23645' },
};

/**
 * technicalRating(candles) → { overall, ma, osc, label, maLabel, oscLabel, buy, sell, neutral } یا null.
 * candles: [{ o,h,l,c }] با c/h/l معتبر. حداقل ~۶۰ کندل لازم است.
 */
export function technicalRating(candles) {
  try {
    if (!Array.isArray(candles) || candles.length < 60) return null;
    const closes = candles.map((c) => c.c);
    const highs = candles.map((c) => c.h);
    const lows = candles.map((c) => c.l);
    const price = lastN(closes);
    if (price == null) return null;

    // ── میانگین‌های متحرک: خرید اگر قیمت بالای MA، فروش اگر پایین (سبک ۱۵-MA TV) ──
    let maBuy = 0, maSell = 0, maTot = 0;
    const rateMA = (v) => { if (v == null || !Number.isFinite(v)) return; maTot++; if (price > v) maBuy++; else if (price < v) maSell++; };
    [10, 20, 30, 50, 100, 200].forEach((p) => {
      if (candles.length <= p + 2) return;
      rateMA(lastN(sma(closes, p))); rateMA(lastN(ema(closes, p)));
    });
    // Hull MA(9) + خط پایه‌ی ایچیموکو(26) — دو MA دیگر مجموعه‌ی TV (بدون نیاز به حجم).
    rateMA(lastN(hma(closes, 9)));
    if (candles.length >= 26) {
      let hh = -Infinity, ll = Infinity;
      for (let i = candles.length - 26; i < candles.length; i++) { if (highs[i] > hh) hh = highs[i]; if (lows[i] < ll) ll = lows[i]; }
      if (Number.isFinite(hh) && Number.isFinite(ll)) rateMA((hh + ll) / 2);
    }
    // VWMA(20) — پانزدهمین MA TV. فقط وقتی حجم معتبر باشد سهم می‌گیرد؛ فارکس بی‌حجم ⇒ vwma=null ⇒
    //   rateMA ردش می‌کند (maTot=14، بی‌رگرسیون). با حجم معتبر ⇒ maTot=15 مثل TV. #264
    const vols = candles.map((c) => (c && c.v != null ? c.v : 0));
    if (candles.length > 22) rateMA(lastN(vwma(closes, vols, 20)));
    const maScore = maTot ? (maBuy - maSell) / maTot : 0;

    // ── نوسان‌گرها: هرکدام خرید(+۱)/فروش(-۱)/خنثی(۰) ──
    let oBuy = 0, oSell = 0, oTot = 0;
    const bump = (sig) => { oTot++; if (sig > 0) oBuy++; else if (sig < 0) oSell++; };
    const r = lastN(rsi(closes, 14)); if (r != null) bump(r < 30 ? 1 : r > 70 ? -1 : 0);
    const st = stoch(highs, lows, closes, 14, 3); const k = lastN(st && st.k); if (k != null) bump(k < 20 ? 1 : k > 80 ? -1 : 0);
    const cc = lastN(cci(highs, lows, closes, 20)); if (cc != null) bump(cc < -100 ? 1 : cc > 100 ? -1 : 0);
    const m = macd(closes, 12, 26, 9); const ml = lastN(m && m.macd), ms = lastN(m && m.signal); if (ml != null && ms != null) bump(ml > ms ? 1 : ml < ms ? -1 : 0);
    // نوسان‌گرهای بیشتر از مجموعه‌ی TV — آستانه‌های استاندارد (اشباع فروش=خرید، هم‌راستا با بالا).
    const wr = lastN(williamsR(highs, lows, closes, 14)); if (wr != null) bump(wr < -80 ? 1 : wr > -20 ? -1 : 0); // Williams %R (−100..0)
    const aoArr = ao(highs, lows); const av = lastN(aoArr); if (av != null) bump(av > 0 ? 1 : av < 0 ? -1 : 0); // Awesome Oscillator (خط صفر)
    const sr = stochRsi(closes, 14, 3, 3); const srk = lastN(sr && sr.k); if (srk != null) bump(srk < 20 ? 1 : srk > 80 ? -1 : 0); // Stoch RSI
    // Momentum(10) — سیگنال از helper testable (خرید=صعود MOM، فروش=نزول). #263
    if (closes.length > 12) bump(momentumSignal(closes, 10));
    // Ultimate Oscillator(7,14,28) — خرید >۷۰، فروش <۳۰ (نمونه‌ی TV: UO=36.7 ⇒ Neutral). #263
    const uo = ultimateOsc({ high: highs, low: lows, close: closes }, { short: 7, mid: 14, long: 28 });
    const uv = lastN(uo && uo.line); if (uv != null) bump(uv > 70 ? 1 : uv < 30 ? -1 : 0);
    // ADX(14) — قاعده‌ی crossover-محور TV روی +DI/−DI دو کندل آخر (معمولاً خنثی، مثل TV). #265
    const ax = adxDI(highs, lows, closes, 14);
    if (ax) { const A = ax.adx, P = ax.plusDI, M = ax.minusDI, na = A.length;
      bump(adxSignal(A[na - 1], P[na - 1], M[na - 1], P[na - 2], M[na - 2])); }
    // Bull Bear Power (Elder Ray EMA13) — قاعده‌ی صعود/نزول bull&bear TV. #265
    const er = elderRay(highs, lows, closes, 13);
    if (er && er.bull && er.bear) { const B = er.bull, Be = er.bear, nb = B.length;
      bump(bullBearSignal(B[nb - 1], B[nb - 2], Be[nb - 1], Be[nb - 2])); }
    const oscScore = oTot ? (oBuy - oSell) / oTot : 0;

    const overall = (maScore + oscScore) / 2;
    return {
      overall, ma: maScore, osc: oscScore,
      label: ratingLabel(overall), maLabel: ratingLabel(maScore), oscLabel: ratingLabel(oscScore),
      buy: maBuy + oBuy, sell: maSell + oSell, neutral: (maTot - maBuy - maSell) + (oTot - oBuy - oSell),
      // شمارش تفکیکی هر بخش (سبک Technicals TV که برای «Moving Averages» و «Oscillators» جداگانه Sell/Neutral/Buy می‌دهد)
      maBuy, maSell, maNeutral: maTot - maBuy - maSell,
      oscBuy: oBuy, oscSell: oSell, oscNeutral: oTot - oBuy - oSell,
    };
  } catch (e) { return null; }
}
