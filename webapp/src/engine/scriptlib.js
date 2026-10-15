// نمااسکریپت — کتابخانهٔ نمونه‌ها + مرجعِ توابع (اتوکامپلیت/مستندات/هایلایت).

export const EXAMPLES = [
  {
    name: '⭐ قالبِ ستاپِ شخصی (TP/SLِ خودت)', kind: 'strategy',
    code: `// قالبِ آماده برای ساختِ ستاپِ شخصی — ورودی‌ها را تغییر بده
// تا شرطِ ورود، حد ضرر و هدفِ خودت را تعریف کنی.
emaFast = input.int(20, "EMA سریع")
emaSlow = input.int(50, "EMA کند")
slMult  = input.float(1.5, "ضریبِ حد ضرر (ATR)")
tpMult  = input.float(3, "ضریبِ هدف (ATR)")
ef = ta.ema(close, emaFast)
es = ta.ema(close, emaSlow)
a  = ta.atr(14)
long  = crossover(ef, es)
short = crossunder(ef, es)
plot(ef, "EMA سریع", color.aqua)
plot(es, "EMA کند", color.orange)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
// حد ضرر و سه هدف بر پایهٔ ATR (نسبت‌ها را با ورودی‌ها تنظیم کن)
sl  = sub(close, mul(a, slMult))
tp1 = add(close, mul(a, tpMult))
tp2 = add(close, mul(a, mul(tpMult, 2)))
tp3 = add(close, mul(a, mul(tpMult, 3)))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها (TP1/TP2/TP3)، جعبهٔ قرمزِ کم‌رنگ = حد ضرر
riskreward(close, sl, tp1, tp2, tp3)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'کراسِ HMA (خرید/فروش)', kind: 'indicator',
    code: `// کراسِ میانگینِ هال — سیگنالِ خرید/فروش
fastLen = input.int(9, "دورهٔ سریع")
slowLen = input.int(21, "دورهٔ کند")
fast = ta.hma(close, fastLen)
slow = ta.hma(close, slowLen)
plot(fast, "HMA سریع", color.aqua)
plot(slow, "HMA کند", color.orange)
buy  = crossover(fast, slow)
sell = crossunder(fast, slow)
plotshape(buy,  "خرید", shape.up,   color.green)
plotshape(sell, "فروش", shape.down, color.red)
alertcondition(buy,  "سیگنالِ خرید HMA")
alertcondition(sell, "سیگنالِ فروش HMA")`,
  },
  {
    name: 'استراتژیِ سوپرترند', kind: 'strategy',
    code: `// استراتژیِ سوپرترند — ورود با تغییرِ روند
atrLen = input.int(10, "دورهٔ ATR")
mult   = input.float(3, "ضریب")
st = ta.supertrend(atrLen, mult)
plot(st.line, "سوپرترند", color.teal)
long  = crossover(close, st.line)
short = crossunder(close, st.line)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'واگراییِ RSI', kind: 'indicator',
    code: `// RSI با نواحیِ اشباع
len = input.int(14, "دورهٔ RSI")
r = ta.rsi(close, len)
plot(r, "RSI", color.purple)
hline(70, "اشباعِ خرید", color.red)
hline(30, "اشباعِ فروش", color.green)
plotshape(crossunder(r, 70), "برگشت", shape.down, color.red)
plotshape(crossover(r, 30),  "برگشت", shape.up,   color.green)`,
  },
  {
    name: 'شکستِ باندِ بولینگر', kind: 'indicator',
    code: `// شکستِ باندِ بولینگر
len = input.int(20, "دوره")
m   = input.float(2, "ضریبِ انحراف")
b = ta.bb(close, len, m)
plot(b.upper, "بالا", color.gray)
plot(b.mid,   "میانه", color.blue)
plot(b.lower, "پایین", color.gray)
plotshape(crossover(close, b.upper),  "شکستِ بالا", shape.up,   color.green)
plotshape(crossunder(close, b.lower), "شکستِ پایین", shape.down, color.red)`,
  },
  {
    name: 'کراسِ MACD', kind: 'indicator',
    code: `// هیستوگرام و کراسِ MACD
m = ta.macd(close, 12, 26, 9)
plot(m.macd,   "MACD",   color.blue)
plot(m.signal, "سیگنال", color.orange)
buy  = crossover(m.macd, m.signal)
sell = crossunder(m.macd, m.signal)
plotshape(buy,  "خرید", shape.up,   color.green)
plotshape(sell, "فروش", shape.down, color.red)
alertcondition(buy, "کراسِ صعودیِ MACD")`,
  },
  {
    name: 'EMA دوگانه + فیلترِ ATR', kind: 'strategy',
    code: `// تقاطعِ EMA با فیلترِ نوسانِ ATR
e1 = ta.ema(close, 20)
e2 = ta.ema(close, 50)
vol = ta.atr(14)
strong = gt(vol, ta.sma(vol, 50))
long  = and(crossover(e1, e2), strong)
short = and(crossunder(e1, e2), strong)
plot(e1, "EMA20", color.aqua)
plot(e2, "EMA50", color.orange)
strategy.entry("long",  long)
strategy.entry("short", short)
plotshape(long, "خرید", shape.up, color.green)`,
  },
  {
    name: '⭐ ستاپِ همگراییِ سه‌گانه (روند+مومنتوم+نوسان)', kind: 'strategy',
    code: `// ستاپِ کامل: سه تأییدیه با هم تصمیم می‌گیرند
emaFast = ta.ema(close, 21)
emaSlow = ta.ema(close, 55)
r = ta.rsi(close, 14)
vol = ta.atr(14)
volOK = gt(vol, ta.sma(vol, 50))
upTrend = gt(emaFast, emaSlow)
dnTrend = lt(emaFast, emaSlow)
long  = and(and(upTrend, crossover(r, 50)), volOK)
short = and(and(dnTrend, crossunder(r, 50)), volOK)
plot(emaFast, "EMA21", color.aqua)
plot(emaSlow, "EMA55", color.orange)
plotshape(long,  "خرید قوی", shape.up,   color.green)
plotshape(short, "فروش قوی", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)
alertcondition(long,  "ستاپِ خریدِ همگرا")
alertcondition(short, "ستاپِ فروشِ همگرا")`,
  },
  {
    name: 'سوپرترند + MACD (هم‌جهت)', kind: 'strategy',
    code: `// دو موتورِ روند که باید هم‌جهت باشند
st = ta.supertrend(10, 3)
m = ta.macd(close, 12, 26, 9)
long  = and(crossover(close, st.line), gt(m.macd, m.signal))
short = and(crossunder(close, st.line), lt(m.macd, m.signal))
plot(st.line, "سوپرترند", color.teal)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'پولبک در روند (EMA200 + Stochastic)', kind: 'strategy',
    code: `// در روندِ اصلی، ورود روی پولبک
ema200 = ta.ema(close, 200)
s = ta.stoch(14, 3)
bull = gt(close, ema200)
bear = lt(close, ema200)
long  = and(bull, crossover(s.k, 20))
short = and(bear, crossunder(s.k, 80))
plot(ema200, "EMA200", color.yellow)
plotshape(long,  "خرید پولبک", shape.up,   color.green)
plotshape(short, "فروش پولبک", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'بریک‌اوتِ دانچیان + فیلترِ روند', kind: 'strategy',
    code: `// شکستِ سقف/کفِ ۲۰ کندلی، هم‌جهت با EMA50
hh = ta.highest(high, 20)
ll = ta.lowest(low, 20)
ema50 = ta.ema(close, 50)
long  = and(crossover(close, hh), gt(close, ema50))
short = and(crossunder(close, ll), lt(close, ema50))
plot(hh, "سقفِ کانال", color.gray)
plot(ll, "کفِ کانال", color.gray)
plotshape(long,  "شکستِ صعودی", shape.up,   color.green)
plotshape(short, "شکستِ نزولی", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'بازگشت از باند بولینگر + RSI', kind: 'strategy',
    code: `// واکنش به باندِ بیرونی با تأییدِ RSI
b = ta.bb(close, 20, 2)
r = ta.rsi(close, 14)
long  = and(crossover(close, b.lower), lt(r, 35))
short = and(crossunder(close, b.upper), gt(r, 65))
plot(b.upper, "بالا", color.gray)
plot(b.lower, "پایین", color.gray)
plotshape(long,  "بازگشت خرید", shape.up,   color.green)
plotshape(short, "بازگشت فروش", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'امتیازدهیِ همگرایی (۴ سیگنال)', kind: 'indicator',
    code: `// هر تأییدیه ۱ امتیاز؛ ۳+ = ستاپِ قوی
s1 = gt(ta.ema(close,20), ta.ema(close,50))
s2 = gt(ta.rsi(close,14), 50)
s3 = gt(close, ta.vwap())
s4 = gt(ta.macd(close,12,26,9).hist, 0)
score = add(add(s1, s2), add(s3, s4))
plot(score, "امتیازِ صعودی (۰-۴)", color.aqua)
hline(3, "ستاپِ قوی", color.green)
buy = crossover(score, 3)
plotshape(buy, "ستاپِ خرید", shape.up, color.green)
alertcondition(buy, "امتیازِ همگرایی به ۳+ رسید")`,
  },
  {
    name: 'همگراییِ سه‌گانه: روند+سوپرترند+RSI (حد ضرر/هدف)', kind: 'strategy',
    code: `// سه تأیید: روندِ EMA، سوپرترند، و مومنتومِ RSI
e = ta.ema(close, 50)
st = ta.supertrend(10, 3)
r = ta.rsi(close, 14)
a = ta.atr(14)
long  = and(and(gt(close, e), gt(st.dir, 0)), gt(r, 52))
short = and(and(lt(close, e), lt(st.dir, 0)), lt(r, 48))
plot(e, "EMA50", color.orange)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
// حد ضرر (قرمز) و هدف (سبز) بر پایهٔ ATR — نسبتِ ریسک‌به‌ریوارد ۱:۲
slLong = sub(close, mul(a, 1.5))
tpLong = add(close, mul(a, 3))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر (R = فاصلهٔ ورود تا حد ضرر)
riskreward(close, slLong, tpLong, add(tpLong, sub(close, slLong)), add(tpLong, mul(sub(close, slLong), 2)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'همگراییِ سه‌گانه: شکستِ کانال+CCI+حجمِ مومنتوم', kind: 'strategy',
    code: `// شکستِ بیشینهٔ ۲۰ کندل + تأییدِ CCI + شتابِ قیمت
hi = ta.highest(high, 20)
lo = ta.lowest(low, 20)
c = ta.cci(20)
m = ta.roc(close, 10)
a = ta.atr(14)
long  = and(and(crossover(close, hi), gt(c, 100)), gt(m, 0))
short = and(and(crossunder(close, lo), lt(c, -100)), lt(m, 0))
plot(hi, "سقفِ کانال", color.gray)
plot(lo, "کفِ کانال", color.gray)
plotshape(long,  "شکستِ خرید", shape.up,   color.green)
plotshape(short, "شکستِ فروش", shape.down, color.red)
slLong = sub(close, mul(a, 2))
tpLong = add(close, mul(a, 4))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر (R = فاصلهٔ ورود تا حد ضرر)
riskreward(close, slLong, tpLong, add(tpLong, sub(close, slLong)), add(tpLong, mul(sub(close, slLong), 2)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'همگراییِ سه‌گانه: پولبکِ EMA200+استوکاستیک+کندل', kind: 'strategy',
    code: `// پولبک به روندِ بلندمدت + اشباعِ استوکاستیک + بازگشتِ قیمت
e = ta.ema(close, 200)
k = ta.stoch(14, 3)
a = ta.atr(14)
up = gt(close, e)
long  = and(and(up, crossover(k.k, k.d)), lt(k.k, 30))
short = and(and(lt(close, e), crossunder(k.k, k.d)), gt(k.k, 70))
plot(e, "EMA200", color.aqua)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
slLong = sub(low, mul(a, 1))
tpLong = add(close, mul(a, 2.5))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر (R = فاصلهٔ ورود تا حد ضرر)
riskreward(close, slLong, tpLong, add(tpLong, sub(close, slLong)), add(tpLong, mul(sub(close, slLong), 2)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'همگراییِ سه‌گانه: HMA+MACD+ROC', kind: 'strategy',
    code: `// میانگینِ سریعِ هال + تقاطعِ MACD + شتابِ ROC
h = ta.hma(close, 21)
md = ta.macd(close, 12, 26, 9)
ro = ta.roc(close, 12)
a = ta.atr(14)
long  = and(and(gt(close, h), crossover(md.macd, md.signal)), gt(ro, 0))
short = and(and(lt(close, h), crossunder(md.macd, md.signal)), lt(ro, 0))
plot(h, "HMA21", color.purple)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
slLong = sub(close, mul(a, 1.8))
tpLong = add(close, mul(a, 3.6))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر (R = فاصلهٔ ورود تا حد ضرر)
riskreward(close, slLong, tpLong, add(tpLong, sub(close, slLong)), add(tpLong, mul(sub(close, slLong), 2)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'همگراییِ سه‌گانه: VWAP+بولینگر+RSI', kind: 'strategy',
    code: `// قیمت بالای VWAP + فشردگیِ بولینگر + مومنتومِ RSI
v = ta.vwap()
b = ta.bb(close, 20, 2)
r = ta.rsi(close, 14)
a = ta.atr(14)
long  = and(and(gt(close, v), crossover(close, b.mid)), gt(r, 50))
short = and(and(lt(close, v), crossunder(close, b.mid)), lt(r, 50))
plot(v, "VWAP", color.yellow)
plot(b.mid, "میانهٔ بولینگر", color.gray)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
slLong = sub(close, mul(a, 1.5))
tpLong = add(close, mul(a, 3))
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر (R = فاصلهٔ ورود تا حد ضرر)
riskreward(close, slLong, tpLong, add(tpLong, sub(close, slLong)), add(tpLong, mul(sub(close, slLong), 2)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: '🌥 ابرِ ایچیموکو (Ichimoku) + پُرکردنِ ابر', kind: 'indicator',
    code: `// ابرِ ایچیموکو کامل با ناحیهٔ پرشده بینِ اسپن‌ها
ich = ta.ichimoku(9, 26, 52)
plot(ich.conversion, "تنکان", color.blue)
plot(ich.base, "کیجون", color.red)
plot(ich.spanA, "اسپن A", color.green)
plot(ich.spanB, "اسپن B", color.orange)
// ابر: ناحیهٔ بینِ اسپن A و B
fill(ich.spanA, ich.spanB, color.new(color.green, 88))
// سیگنال: تقاطعِ تنکان/کیجون بالای ابر
bull = and(crossover(ich.conversion, ich.base), gt(close, ich.spanB))
plotshape(bull, "خرید", shape.up, color.green)`,
  },
  {
    name: '💪 قدرتِ روند (ADX + DI) با رنگِ پس‌زمینه', kind: 'indicator',
    code: `// شاخصِ جهت‌دار — قدرت و جهتِ روند
d = ta.dmi(14, 14)
plot(d.adx, "ADX", color.purple, 2)
plot(d.plus, "+DI", color.green)
plot(d.minus, "-DI", color.red)
hline(25, "آستانهٔ روند", color.gray)
// پس‌زمینهٔ سبز وقتی روندِ قویِ صعودی، قرمز وقتی نزولی
strong = gt(d.adx, 25)
bgcolor(and(strong, gt(d.plus, d.minus)), color.new(color.green, 90))
bgcolor(and(strong, gt(d.minus, d.plus)), color.new(color.red, 90))`,
  },
  {
    name: '📊 داشبوردِ چنداسیلاتوری (جدول روی چارت)', kind: 'indicator',
    code: `// خلاصهٔ زندهٔ اندیکاتورها در یک جدولِ روی چارت
r = ta.rsi(close, 14)
m = ta.mfi(14)
w = ta.wpr(14)
a = ta.dmi(14,14).adx
t = table.new()
table.cell(t, 0, 0, "اندیکاتور"); table.cell(t, 1, 0, "مقدار")
table.cell(t, 0, 1, "RSI");  table.cell(t, 1, 1, r)
table.cell(t, 0, 2, "MFI");  table.cell(t, 1, 2, m)
table.cell(t, 0, 3, "W%R");  table.cell(t, 1, 3, w)
table.cell(t, 0, 4, "ADX");  table.cell(t, 1, 4, a)
plot(r, "RSI", color.aqua)
// رنگِ کندل وقتی اشباعِ خرید/فروشِ هم‌زمان
barcolor(color.orange, and(gt(r, 70), gt(m, 80)))`,
  },
  {
    name: '🎯 پارابولیک SAR + کلتنر (دنبالِ روند)', kind: 'strategy',
    code: `// SAR برای حد ضررِ متحرک + کانالِ کلتنر برای نوسان
ps = ta.sar(0.02, 0.02, 0.2)
k = ta.kc(close, 20, 2)
plot(ps, "SAR", color.purple, 1, 2)
plot(k.upper, "کلتنر بالا", color.gray)
plot(k.lower, "کلتنر پایین", color.gray)
fill(k.upper, k.lower, color.new(color.blue, 92))
long  = crossover(close, ps)
short = crossunder(close, ps)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
a = ta.atr(14)
riskreward(close, sub(close, mul(a,1.5)), add(close, mul(a,1.5)), add(close, mul(a,3)), add(close, mul(a,4.5)))
strategy.entry("long", long)
strategy.entry("short", short)`,
  },
  {
    name: '⏮ تاریخچه و متغیرِ پایدار (سبکِ Pine: close[1], var)', kind: 'indicator',
    code: `// نحوِ سازگار با Pine: close[1] = کندلِ قبل، var = متغیرِ پایدار
// تغییرِ قیمت نسبت به کندلِ قبل و دو کندل قبل
chg = sub(close, close[1])
mom2 = sub(close[1], close[2])
plot(chg, "تغییر", color.aqua)
// کندلِ صعودی/نزولی نسبت به قبل
up = gt(close, close[1])
dn = lt(close, close[1])
barcolor(color.new(color.green, 40), up)
barcolor(color.new(color.red, 40), dn)
// فلش وقتی شتاب مثبت شد
plotarrow(sub(chg, mom2), "شتاب")
// جدولِ خلاصه (آخرین مقادیر)
t = table.new()
table.cell(t, 0, 0, "قیمتِ فعلی"); table.cell(t, 1, 0, close)
table.cell(t, 0, 1, "کندلِ قبل");  table.cell(t, 1, 1, close[1])
table.cell(t, 0, 2, "تغییر");      table.cell(t, 1, 2, chg)`,
  },
  {
    name: '🔭 مولتی‌تایم‌فریم (روندِ تایم‌فریمِ بالاتر)', kind: 'indicator',
    code: `// روندِ تایم‌فریمِ بالاتر (۴ برابرِ فعلی) روی همین چارت
htfClose = security(4, close)
htfEma = ta.ema(htfClose, 20)
plot(ta.ema(close, 20), "EMA فعلی", color.blue)
plot(htfEma, "EMA تایم‌فریمِ بالا", color.orange, 2)
// فیلتر: فقط وقتی قیمت بالای روندِ HTF است خرید
bull = and(crossover(close, ta.ema(close, 20)), gt(close, htfEma))
plotshape(bull, "خرید همسو با HTF", shape.up, color.green)
bgcolor(gt(close, htfEma), color.new(color.green, 92))
bgcolor(lt(close, htfEma), color.new(color.red, 92))`,

  },
  {
    name: 'آرون (قدرت و جهتِ روند)', kind: 'indicator',
    code: `// آرون — بر پایهٔ فاصله تا سقف/کفِ اخیر؛ عبورِ خطوط = تغییرِ روند
len = input.int(14, "دورهٔ آرون")
ar = ta.aroon(len)
plot(ar.up,   "آرونِ صعودی", color.green, 2)
plot(ar.down, "آرونِ نزولی", color.red, 2)
hline(70, "قوی", color.gray)
hline(30, "ضعیف", color.gray)
buy  = crossover(ar.up, ar.down)
sell = crossunder(ar.up, ar.down)
plotshape(buy,  "خرید", shape.up,   color.green)
plotshape(sell, "فروش", shape.down, color.red)
alertcondition(buy, "روندِ صعودیِ آرون")`,
  },
  {
    name: 'استوکاستیک RSI (اشباعِ حساس)', kind: 'indicator',
    code: `// استوکاستیک RSI — اسیلاتورِ سریع برای نواحیِ اشباع
len = input.int(14, "دوره")
sr = ta.stochrsi(close, len, 3, 3)
plot(sr.k, "K", color.aqua)
plot(sr.d, "D", color.orange)
hline(80, "اشباعِ خرید", color.red)
hline(20, "اشباعِ فروش", color.green)
buy  = and(crossover(sr.k, sr.d), lt(sr.k, 20))
sell = and(crossunder(sr.k, sr.d), gt(sr.k, 80))
plotshape(buy,  "خرید", shape.up,   color.green)
plotshape(sell, "فروش", shape.down, color.red)`,
  },
  {
    name: 'کراسِ DEMA/TEMA (کم‌تأخیر)', kind: 'strategy',
    code: `// میانگین‌های دوگانه/سه‌گانهٔ نمایی — ورودِ سریع با تأخیرِ کم
fast = ta.dema(close, input.int(10, "DEMA سریع"))
slow = ta.tema(close, input.int(30, "TEMA کند"))
plot(fast, "DEMA10", color.aqua)
plot(slow, "TEMA30", color.orange)
long  = crossover(fast, slow)
short = crossunder(fast, slow)
plotshape(long,  "خرید", shape.up,   color.green)
plotshape(short, "فروش", shape.down, color.red)
a = ta.atr(14)
// حد ضرر/هدف بر پایهٔ ATR (جعبهٔ سبز = هدف تا TP3، قرمز = حد ضرر)
riskreward(close, sub(close, mul(a, 1.5)), add(close, mul(a, 3)), add(close, mul(a, 4.5)), add(close, mul(a, 6)))
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: 'شاخصِ قدرتِ واقعی (TSI) + سیگنال', kind: 'indicator',
    code: `// TSI — مومنتومِ دوبار هموارشده با خطِ سیگنال
t = ta.tsi(close, 13, 25)
sig = ta.ema(t, 13)
plot(t,   "TSI", color.blue, 2)
plot(sig, "سیگنال", color.orange)
hline(0, "صفر", color.gray)
buy  = crossover(t, sig)
sell = crossunder(t, sig)
plotshape(buy,  "خرید", shape.up,   color.green)
plotshape(sell, "فروش", shape.down, color.red)
bgcolor(gt(t, 0), color.new(color.green, 92))
bgcolor(lt(t, 0), color.new(color.red, 92))`,
  },
  {
    name: 'جریانِ پول: OBV + CMF + واگراییِ مخفی', kind: 'indicator',
    code: `// حجمِ هوشمند: OBV (تجمعی) و CMF (فشارِ خرید/فروش)
o = ta.obv()
c = ta.cmf(20)
plot(o, "OBV", color.aqua, 2)
plot(mul(c, 100000), "CMF×", color.purple)
// واگراییِ مخفی: قیمتِ پایین‌تر ولی OBV بالاتر = فشارِ پنهانِ خرید
hidBull = and(lt(close, close[5]), gt(o, o[5]))
plotshape(hidBull, "واگراییِ مخفی", shape.up, color.green)
alertcondition(and(gt(c, 0.1), gt(o, o[1])), "ورودِ پولِ هوشمند")`,
  },
  {
    name: 'فشردگیِ کلتنر/بولینگر (Squeeze)', kind: 'strategy',
    code: `// وقتی بولینگر داخلِ کلتنر می‌رود بازار فشرده است؛ خروج = انفجار
b = ta.bb(close, 20, 2)
k = ta.kc(close, 20, 1.5)
squeeze = and(gt(b.lower, k.lower), lt(b.upper, k.upper))
fired = and(squeeze[1], and(gt(b.upper, k.upper), gt(close, b.mid)))
plot(b.upper, "بولینگر بالا", color.blue)
plot(b.lower, "بولینگر پایین", color.blue)
plot(k.upper, "کلتنر بالا", color.gray)
plot(k.lower, "کلتنر پایین", color.gray)
// پس‌زمینهٔ زرد = بازارِ فشرده (آمادهٔ حرکت)
bgcolor(squeeze, color.new(color.yellow, 85))
long = and(fired, gt(close, k.upper))
plotshape(long, "شکستِ فشردگی", shape.up, color.green)
strategy.entry("long", long)
alertcondition(fired, "خروج از فشردگی (Squeeze)")`,
  },
  {
    name: 'پیووتِ سقف/کف (ساختارِ بازار)', kind: 'indicator',
    code: `// نقاطِ چرخشِ بازار + حمایت/مقاومتِ پویا با valuewhen
ph = ta.pivothigh(5, 5)
pl = ta.pivotlow(5, 5)
plot(close, "قیمت", color.gray)
plotshape(ph, "سقفِ چرخش", shape.down, color.red)
plotshape(pl, "کفِ چرخش", shape.up,   color.green)
// آخرین سقف/کفِ معتبر = خطوطِ مقاومت/حمایت
res = ta.valuewhen(ph, high, 0)
sup = ta.valuewhen(pl, low, 0)
plot(res, "مقاومت", color.red, 1, 2)
plot(sup, "حمایت", color.green, 1, 2)`,
  },
  {
    name: 'اسیلاتورِ شگفت‌انگیز (AO)', kind: 'indicator',
    code: `// AO — تفاضلِ دو میانگینِ میانهٔ قیمت؛ شتابِ مومنتوم
a = ta.ao()
plot(a, "AO", color.blue, 2)
hline(0, "صفر", color.gray)
// رنگِ کندل: سبز اگر AO صعودی، قرمز اگر نزولی
barcolor(color.green, gt(a, a[1]))
barcolor(color.red,   lt(a, a[1]))
plotshape(crossover(a, 0),  "مثبت شد", shape.up,   color.green)
plotshape(crossunder(a, 0), "منفی شد", shape.down, color.red)`,
  },
  {
    name: 'کانالِ رگرسیونِ خطی (LinReg)', kind: 'strategy',
    code: `// خطِ روندِ رگرسیون + باندِ انحرافِ معیار برای بازگشت به میانگین
len = input.int(50, "دورهٔ رگرسیون")
mid = ta.linreg(close, len, 0)
dev = ta.stdev(close, len)
upper = add(mid, mul(dev, 2))
lower = sub(mid, mul(dev, 2))
plot(mid,   "خطِ رگرسیون", color.orange, 2)
plot(upper, "بالا", color.gray)
plot(lower, "پایین", color.gray)
fill(upper, lower, color.new(color.blue, 92))
long  = crossover(close, lower)
short = crossunder(close, upper)
plotshape(long,  "بازگشت خرید", shape.up,   color.green)
plotshape(short, "بازگشت فروش", shape.down, color.red)
strategy.entry("long",  long)
strategy.entry("short", short)`,
  },
  {
    name: '📚 کتابخانه: توابعِ کمکیِ روند', kind: 'library',
    code: `// کتابخانه — این توابع را کپی کن و در اسکریپت‌های خود استفاده کن.
// هر تابع یک سری برمی‌گرداند تا مستقیم در شرط‌ها به‌کار رود.
function isUpTrend(fast, slow){ return gt(ta.ema(close, fast), ta.ema(close, slow)); }
function momentumOk(len){ return gt(ta.rsi(close, len), 50); }
function volatile(len){ return gt(ta.atr(len), ta.sma(ta.atr(len), 50)); }
// نمونهٔ استفاده: ترکیبِ سه تابعِ کتابخانه در یک ستاپ
setup = and(and(isUpTrend(21, 55), momentumOk(14)), volatile(14))
plot(ta.ema(close, 21), "EMA21", color.aqua)
plotshape(setup, "ستاپِ کتابخانه", shape.up, color.green)
alertcondition(setup, "ستاپِ کتابخانه فعال شد")`,
  },
  {
    name: '📚 کتابخانه: مدیریتِ ریسکِ ATR', kind: 'library',
    code: `// کتابخانه — توابعِ آمادهٔ حد ضرر/هدف بر پایهٔ ATR (قابلِ استفادهٔ مجدد).
function slLong(mult){ return sub(close, mul(ta.atr(14), mult)); }
function tpAt(r){ return add(close, mul(ta.atr(14), r)); }
// نمونهٔ استفاده: ورودِ ساده با نسبتِ ریسک‌به‌ریوارد ۱:۲:۳
long = crossover(ta.ema(close, 20), ta.ema(close, 50))
plot(ta.ema(close, 20), "EMA20", color.aqua)
plot(ta.ema(close, 50), "EMA50", color.orange)
plotshape(long, "خرید", shape.up, color.green)
// جعبهٔ سبزِ کم‌رنگ = هدف‌ها تا TP3، جعبهٔ قرمزِ کم‌رنگ = حد ضرر
riskreward(close, slLong(1.5), tpAt(3), tpAt(4.5), tpAt(6))
strategy.entry("long", long)`,
  },
];

// مرجعِ توابع برای اتوکامپلیت + پنلِ مستندات
export const REFERENCE = [
  { g: 'میانگین', items: [
    ['ta.sma(src, p)', 'میانگینِ متحرکِ ساده'],
    ['ta.ema(src, p)', 'میانگینِ متحرکِ نمایی'],
    ['ta.wma(src, p)', 'میانگینِ وزنی'],
    ['ta.hma(src, p)', 'میانگینِ هال (روان)'],
    ['ta.rma(src, p)', 'میانگینِ وایلدر'],
    ['ta.vwma(src, p)', 'میانگینِ وزنیِ حجم'],
    ['ta.vwap()', 'قیمتِ میانگینِ وزنیِ حجم'],
  ]},
  { g: 'نوسان‌گرها', items: [
    ['ta.rsi(src, p)', 'شاخصِ قدرتِ نسبی'],
    ['ta.macd(src, f, sl, sg)', '{macd, signal, hist}'],
    ['ta.stoch(p, d)', '{k, d} استوکاستیک'],
    ['ta.cci(p)', 'شاخصِ کانالِ کالا'],
    ['ta.roc(src, n)', 'نرخِ تغییر٪'],
    ['ta.mom(src, n)', 'مومنتوم'],
  ]},
  { g: 'نوسان/روند', items: [
    ['ta.atr(p)', 'میانگینِ بازهٔ واقعی'],
    ['ta.stdev(src, p)', 'انحرافِ معیار'],
    ['ta.bb(src, p, m)', '{mid, upper, lower}'],
    ['ta.supertrend(p, m)', '{line, dir}'],
    ['ta.highest(src, p)', 'بیشینهٔ دوره'],
    ['ta.lowest(src, p)', 'کمینهٔ دوره'],
  ]},
  { g: 'سیگنال', items: [
    ['crossover(a, b)', 'a از پایین b را قطع کرد'],
    ['crossunder(a, b)', 'a از بالا b را قطع کرد'],
    ['cross(a, b)', 'هر تقاطع'],
    ['rising(src, n)', 'n بارِ صعودی'],
    ['falling(src, n)', 'n بارِ نزولی'],
    ['barssince(cond)', 'تعدادِ بار از آخرین شرط'],
  ]},
  { g: 'عملگر/منطق', items: [
    ['add(a,b) sub(a,b) mul(a,b) div(a,b)', 'جمع/تفریق/ضرب/تقسیمِ سری'],
    ['gt(a,b) lt(a,b) ge(a,b) le(a,b)', 'مقایسه'],
    ['and(a,b) or(a,b)', 'منطقی'],
    ['iff(cond, a, b)', 'شرطی'],
    ['nz(src, r)', 'جایگزینیِ null'],
  ]},
  { g: 'ورودی', items: [
    ['input.int(def, "عنوان")', 'ورودیِ عددِ صحیح'],
    ['input.float(def, "عنوان")', 'ورودیِ اعشاری'],
    ['input.bool(def, "عنوان")', 'ورودیِ بله/خیر'],
  ]},
  { g: 'رسم/هشدار', items: [
    ['plot(src, "نام", color.blue)', 'رسمِ خط'],
    ['plotshape(cond, "نام", shape.up, color.green)', 'علامتِ بای/سل'],
    ['hline(price, "نام", color.gray)', 'خطِ افقی'],
    ['riskreward(entry, sl, tp1, tp2, tp3)', 'جعبهٔ سبزِ هدف (تا TP3) + قرمزِ حد ضرر، رو به جلو'],
    ['bgcolor(cond, color)', 'رنگِ پس‌زمینه'],
    ['label.new(cond, src, "متن", color)', 'برچسب'],
    ['alertcondition(cond, "پیام")', 'شرطِ آلارم'],
  ]},
  { g: 'استراتژی', items: [
    ['strategy.entry("long", cond)', 'ورودِ خرید'],
    ['strategy.entry("short", cond)', 'ورودِ فروش'],
    ['strategy.close(cond)', 'بستنِ پوزیشن'],
  ]},
  { g: 'داده', items: [
    ['open high low close volume', 'سری‌های قیمت'],
    ['hl2 hlc3 ohlc4 hlcc4', 'قیمت‌های ترکیبی'],
    ['bar_index last_bar_index', 'شمارهٔ کندل'],
    ['barstate.islast / isfirst / isconfirmed', 'وضعیتِ کندل (سری بولین)'],
    ['ref(src, n)', 'مقدارِ n کندلِ قبل (معادلِ close[n])'],
    ['na(x) / nz(src, r)', 'چکِ تهی / جایگزینی'],
    ['int(x) float(x) bool(x)', 'تبدیلِ نوع'],
    ['color.* shape.*', 'رنگ‌ها و اشکال'],
  ]},
  { g: 'میانگین‌های پیشرفته', items: [
    ['ta.dema(src,p) ta.tema(src,p)', 'میانگینِ دوگانه/سه‌گانهٔ نمایی'],
    ['ta.alma(src,p,off,sig)', 'میانگینِ آرنو لگو'],
    ['ta.swma(src)', 'میانگینِ وزنیِ متقارن'],
    ['ta.linreg(src,p,off)', 'رگرسیونِ خطی'],
    ['ta.trix(src,p)', 'TRIX'],
    ['ta.vwma(src,p) ta.vwap()', 'وزنیِ حجمی / VWAP'],
  ]},
  { g: 'اسیلاتورهای پیشرفته', items: [
    ['ta.wpr(p)', 'ویلیامز %R'],
    ['ta.mfi(p)', 'شاخصِ جریانِ نقدینگی'],
    ['ta.cmo(src,p)', 'مومنتومِ چاند'],
    ['ta.tsi(src,short,long)', 'شاخصِ قدرتِ واقعی'],
    ['ta.ao()', 'اسیلاتورِ شگفت‌انگیز'],
    ['ta.stochrsi(src,p,k,d)', '{k, d} استوک‌RSI'],
  ]},
  { g: 'روند/جهت/ابر', items: [
    ['ta.sar(start,inc,max)', 'پارابولیک SAR'],
    ['ta.dmi(p,smooth)', '{plus, minus, adx}'],
    ['ta.adx(p,smooth)', 'شاخصِ جهت‌دارِ میانگین'],
    ['ta.aroon(p)', '{up, down}'],
    ['ta.ichimoku(c,b,sp)', '{conversion, base, spanA, spanB}'],
    ['ta.kc(src,p,m) ta.donchian(p)', 'کلتنر / دانچیان'],
    ['ta.bbw(src,p,m) ta.kcw(...)', 'پهنای باند'],
  ]},
  { g: 'حجم/ساختار', items: [
    ['ta.obv() ta.ad() ta.cmf(p)', 'OBV / A/D / جریانِ پولِ چایکین'],
    ['ta.pivothigh(l,r) ta.pivotlow(l,r)', 'نقاطِ پیووت'],
    ['ta.highestbars(s,p) ta.lowestbars(s,p)', 'فاصله تا سقف/کف'],
    ['ta.correlation(a,b,p)', 'همبستگی'],
    ['ta.cum(src) ta.sum(src,p)', 'تجمعی / مجموعِ پنجره'],
    ['ta.median(src,p) ta.variance(src,p)', 'میانه / واریانس'],
  ]},
  { g: 'ریاضی', items: [
    ['math.floor/ceil/round/sign/abs', 'گردکردن/علامت'],
    ['math.exp/log/log10/sqrt/pow', 'توابعِ نمایی'],
    ['math.sin/cos/tan/atan', 'مثلثاتی'],
    ['math.pi math.e math.phi', 'ثابت‌ها'],
    ['math.sum(src,p) math.avg(a,b)', 'مجموع/میانگین'],
  ]},
  { g: 'رسمِ پیشرفته', items: [
    ['fill(plotA, plotB, color)', 'پُرکردنِ ناحیهٔ بینِ دو سری'],
    ['line.new(x1,y1,x2,y2,color,w)', 'خطِ برنامه‌نویسی‌شده'],
    ['box.new(left,top,right,bottom,color)', 'جعبه'],
    ['table.new() + table.cell(t,c,r,txt)', 'جدولِ روی چارت'],
    ['barcolor(color, cond)', 'رنگِ کندل'],
    ['alert("پیام") / alert(cond, "پیام")', 'هشدارِ پویا'],
  ]},
  { g: 'تاریخچه و رشته (سازگاریِ Pine)', items: [
    ['close[1]  high[2]  src[n]', 'مقدارِ n کندلِ قبل (= ref(src, n))'],
    ['security(mult, src)', 'تایم‌فریمِ بالاتر — مثلِ security(4, close)'],
    ['security(mult, high, "max")', 'تجمیعِ max/min/sum/last در HTF'],
    ['var x = 0    x := x + 1', 'متغیرِ پایدار / بازتخصیص (سازگاریِ نحوی)'],
    ['str.tostring(v)  str.format("{0}", v)', 'تبدیل/قالبِ رشته'],
    ['str.contains/split/upper/lower/replace_all', 'توابعِ رشته'],
    ['color.new(color.red, 80)', 'رنگ با شفافیتِ ۸۰٪'],
    ['color.rgb(255, 0, 0, 50)', 'رنگِ RGB با شفافیت'],
  ]},
  { g: 'رسمِ کندل/فلش', items: [
    ['plotarrow(series)', 'فلشِ بالا(مثبت)/پایین(منفی)'],
    ['plotcandle(o, h, l, c)', 'رسمِ کندلِ سفارشی (هایکین/رنکو…)'],
  ]},
  { g: 'ورودیِ پیشرفته', items: [
    ['input.int(def,"عنوان",min,max,step)', 'عدد با بازه/گام'],
    ['input.float(def,"عنوان",min,max,step)', 'اعشاری با بازه'],
    ['input.string(def,"عنوان",["a","b"])', 'انتخاب از فهرست'],
    ['input.color(def,"عنوان")', 'انتخابِ رنگ'],
    ['input.source(close,"منبع")', 'انتخابِ منبع'],
  ]},
];

// لیستِ کلماتِ کلیدی برای اتوکامپلیت
export const COMPLETIONS = (() => {
  const out = ['ta.sma', 'ta.ema', 'ta.wma', 'ta.hma', 'ta.rma', 'ta.smma', 'ta.vwma', 'ta.vwap', 'ta.dema', 'ta.tema', 'ta.alma', 'ta.swma', 'ta.linreg', 'ta.trix', 'ta.rsi', 'ta.macd', 'ta.stoch', 'ta.stochrsi', 'ta.cci', 'ta.roc', 'ta.mom', 'ta.wpr', 'ta.mfi', 'ta.cmo', 'ta.tsi', 'ta.ao', 'ta.atr', 'ta.tr', 'ta.stdev', 'ta.variance', 'ta.bb', 'ta.bbw', 'ta.kc', 'ta.kcw', 'ta.donchian', 'ta.supertrend', 'ta.sar', 'ta.dmi', 'ta.adx', 'ta.aroon', 'ta.ichimoku', 'ta.obv', 'ta.ad', 'ta.cmf', 'ta.highest', 'ta.lowest', 'ta.highestbars', 'ta.lowestbars', 'ta.median', 'ta.correlation', 'ta.cum', 'ta.sum', 'ta.change', 'ta.barssince', 'ta.valuewhen', 'ta.pivothigh', 'ta.pivotlow', 'math.abs', 'math.max', 'math.min', 'math.round', 'math.floor', 'math.ceil', 'math.sign', 'math.sqrt', 'math.pow', 'math.exp', 'math.log', 'math.log10', 'math.sin', 'math.cos', 'math.tan', 'math.atan', 'math.avg', 'math.sum', 'math.pi', 'math.e', 'math.phi', 'crossover', 'crossunder', 'cross', 'rising', 'falling', 'ref', 'security', 'request.security', 'na', 'nz', 'int', 'float', 'bool', 'add', 'sub', 'mul', 'div', 'gt', 'lt', 'ge', 'le', 'and', 'or', 'iff', 'input.int', 'input.float', 'input.bool', 'input.string', 'input.color', 'input.source', 'str.tostring', 'str.format', 'str.contains', 'str.split', 'str.replace_all', 'str.upper', 'str.lower', 'plot', 'plotshape', 'plotchar', 'plotarrow', 'plotcandle', 'hline', 'fill', 'riskreward', 'bgcolor', 'barcolor', 'line.new', 'box.new', 'table.new', 'table.cell', 'label.new', 'alert', 'alertcondition', 'color.rgb', 'strategy.entry', 'strategy.exit', 'strategy.close', 'color.blue', 'color.red', 'color.green', 'color.orange', 'color.purple', 'color.aqua', 'color.teal', 'color.gray', 'color.yellow', 'color.white', 'color.new', 'shape.up', 'shape.down', 'shape.circle', 'open', 'high', 'low', 'close', 'volume', 'hl2', 'hlc3', 'ohlc4', 'hlcc4', 'bar_index', 'last_bar_index', 'barstate.islast', 'barstate.isfirst', 'barstate.isconfirmed'];
  return out;
})();

export const KEYWORDS = ['ta', 'math', 'str', 'input', 'plot', 'plotshape', 'plotchar', 'plotarrow', 'plotcandle', 'hline', 'riskreward', 'bgcolor', 'barcolor', 'fill', 'line', 'box', 'table', 'label', 'alert', 'alertcondition', 'strategy', 'barstate', 'security', 'request', 'crossover', 'crossunder', 'cross', 'rising', 'falling', 'ref', 'na', 'nz', 'iff', 'int', 'float', 'bool', 'add', 'sub', 'mul', 'div', 'gt', 'lt', 'ge', 'le', 'and', 'or', 'color', 'shape', 'open', 'high', 'low', 'close', 'volume', 'time', 'hl2', 'hlc3', 'ohlc4', 'hlcc4', 'bar_index', 'last_bar_index', 'var', 'varip', 'if', 'for', 'function', 'return', 'true', 'false'];
