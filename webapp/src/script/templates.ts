// The twelve shapes a strategy comes in, for a reader who pasted a sentence instead of code (the
// phone's ScriptTemplates.kt). «buy when EMA 20 crosses EMA 50» is not a program; it is one of about a
// dozen shapes, and the reader knows which the moment they see it named. The paste box matches
// keywords, offers the closest three with the sentence's own numbers already in place, and the
// reader picks. Every template draws, marks its buy and sell bars and declares the two alert
// conditions, which is what makes it something the tester, the alerts and the chart all read.
//
// Written in this engine's NamaScript (series functions rather than operators: gt, and, add, ref).

export interface ScriptTemplate {
  id: string;
  title: string;
  titleEn: string;
  summary: string;
  summaryEn: string;
  /** What the holes take when the sentence named no numbers. */
  defaults: number[];
  /** The source, with %1, %2… where the numbers go. */
  shape: string;
  keywords: string[];
}

const signals = (buy: string, sell: string, buyText: string, sellText: string) =>
  `plotshape(${buy}, "${buyText}", shape.up, color.green)\nplotshape(${sell}, "${sellText}", shape.down, color.red)\nalertcondition(${buy}, "${buyText}")\nalertcondition(${sell}, "${sellText}")`;

export const TEMPLATES: ScriptTemplate[] = [
  {
    id: 'ma-cross', title: 'تقاطع دو میانگین', titleEn: 'Two moving averages cross',
    summary: 'میانگین تند از کند رد می‌شود.', summaryEn: 'A fast average crosses a slow one.', defaults: [20, 50],
    shape: `fast = ta.ema(close, input.int(%1, "دوره‌ی تند", 2, 400))
slow = ta.ema(close, input.int(%2, "دوره‌ی کند", 2, 400))
plot(fast, "تند", color.orange)
plot(slow, "کند", color.blue)
${signals('ta.crossover(fast, slow)', 'ta.crossunder(fast, slow)', 'میانگین تند از کند رد شد', 'میانگین تند زیر کند رفت')}`,
    keywords: ['cross', 'تقاطع', 'میانگین', 'ema', 'sma', 'moving average', 'رد شد'],
  },
  {
    id: 'rsi-zones', title: 'بازگشت از اشباع', titleEn: 'Coming back from an extreme',
    summary: 'RSI از کف برمی‌گردد یا از سقف پایین می‌آید.', summaryEn: 'RSI comes back over its floor or under its ceiling.', defaults: [14, 30, 70],
    shape: `r = ta.rsi(close, input.int(%1, "دوره", 2, 200))
floorLevel = input.int(%2, "کف", 1, 49)
ceilingLevel = input.int(%3, "سقف", 51, 99)
plot(r, "RSI", color.purple)
hline(floorLevel, "کف")
hline(ceilingLevel, "سقف")
${signals('ta.crossover(r, floorLevel)', 'ta.crossunder(r, ceilingLevel)', 'RSI از کف برگشت', 'RSI از سقف پایین آمد')}`,
    keywords: ['rsi', 'اشباع', 'oversold', 'overbought', 'اشباع فروش', 'اشباع خرید'],
  },
  {
    id: 'breakout', title: 'شکست سقف و کف', titleEn: 'Breaking a high or a low',
    summary: 'قیمت از بالاترین یا پایین‌ترین n کندل رد می‌شود.', summaryEn: 'Price passes the highest or lowest of the last n bars.', defaults: [20],
    shape: `span = input.int(%1, "تعداد کندل", 2, 400)
top = ref(ta.highest(high, span), 1)
bottom = ref(ta.lowest(low, span), 1)
plot(top, "سقف", color.green)
plot(bottom, "کف", color.red)
${signals('gt(close, top)', 'lt(close, bottom)', 'سقف شکسته شد', 'کف شکسته شد')}`,
    keywords: ['breakout', 'شکست', 'سقف', 'کف', 'high', 'low', 'range'],
  },
  {
    id: 'band-reversion', title: 'برگشت به میانگین', titleEn: 'Back to the middle',
    summary: 'قیمت از باند برمی‌گردد.', summaryEn: 'Price comes back from a band.', defaults: [20, 2],
    shape: `span = input.int(%1, "دوره", 2, 200)
width = input.float(%2, "انحراف", 1, 5)
mid = ta.sma(close, span)
dev = mul(ta.stdev(close, span), width)
plot(mid, "میانه", color.gray)
plot(add(mid, dev), "بالا", color.red)
plot(sub(mid, dev), "پایین", color.green)
${signals('ta.crossover(close, sub(mid, dev))', 'ta.crossunder(close, add(mid, dev))', 'از باند پایین برگشت', 'از باند بالا برگشت')}`,
    keywords: ['bollinger', 'باند', 'برگشت', 'reversion', 'mean', 'میانگین‌گرایی'],
  },
  {
    id: 'macd-signal', title: 'تقاطع مکدی', titleEn: 'MACD crossing its signal',
    summary: 'مکدی از خط سیگنالش رد می‌شود.', summaryEn: 'MACD crosses its signal line.', defaults: [12, 26, 9],
    shape: `fastLen = input.int(%1, "تند", 2, 200)
slowLen = input.int(%2, "کند", 2, 400)
signalLen = input.int(%3, "سیگنال", 1, 100)
macdLine = sub(ta.ema(close, fastLen), ta.ema(close, slowLen))
sig = ta.ema(macdLine, signalLen)
plot(macdLine, "مکدی", color.blue)
plot(sig, "سیگنال", color.orange)
${signals('ta.crossover(macdLine, sig)', 'ta.crossunder(macdLine, sig)', 'مکدی از سیگنالش رد شد', 'مکدی زیر سیگنالش رفت')}`,
    keywords: ['macd', 'مکدی', 'همگرایی'],
  },
  {
    id: 'trend-pullback', title: 'پولبک در روند', titleEn: 'A pullback inside a trend',
    summary: 'بالای میانگین بلند، برگشت از میانگین کوتاه.', summaryEn: 'Above the slow average, coming back off the fast one.', defaults: [200, 20],
    shape: `trendLen = input.int(%1, "دوره‌ی روند", 10, 400)
pullLen = input.int(%2, "دوره‌ی پولبک", 2, 200)
trend = ta.ema(close, trendLen)
pull = ta.ema(close, pullLen)
plot(trend, "روند", color.blue)
plot(pull, "پولبک", color.orange)
rising = gt(close, trend)
${signals('and(rising, ta.crossover(close, pull))', 'and(lt(close, trend), ta.crossunder(close, pull))', 'پولبک در روند صعودی تمام شد', 'پولبک در روند نزولی تمام شد')}`,
    keywords: ['pullback', 'پولبک', 'اصلاح', 'trend', 'روند'],
  },
  {
    id: 'volume-spike', title: 'جهش حجم', titleEn: 'A jump in volume',
    summary: 'حجم چند برابر میانگینش می‌شود.', summaryEn: 'Volume goes to a multiple of its own average.', defaults: [20, 2],
    shape: `span = input.int(%1, "دوره", 2, 200)
times = input.float(%2, "چند برابر", 1, 10)
average = ta.sma(volume, span)
plot(volume, "حجم", color.gray)
plot(mul(average, times), "آستانه", color.orange)
jump = gt(volume, mul(average, times))
${signals('and(jump, gt(close, open))', 'and(jump, lt(close, open))', 'جهش حجم با کندل صعودی', 'جهش حجم با کندل نزولی')}`,
    keywords: ['volume', 'حجم', 'spike', 'جهش'],
  },
  {
    id: 'atr-stop', title: 'حد ضرر متحرک', titleEn: 'A trailing stop',
    summary: 'خطی که به اندازه‌ی چند ATR زیر قیمت می‌آید.', summaryEn: 'A line that follows the price a few ATRs below it.', defaults: [14, 3],
    shape: `span = input.int(%1, "دوره‌ی ATR", 2, 200)
times = input.float(%2, "ضریب", 1, 10)
band = mul(ta.atr(span), times)
stopLine = sub(ta.highest(close, span), band)
plot(stopLine, "حد ضرر", color.red)
plotshape(ta.crossunder(close, stopLine), "قیمت زیر حد ضرر متحرک رفت", shape.down, color.red)
alertcondition(ta.crossunder(close, stopLine), "قیمت زیر حد ضرر متحرک رفت")`,
    keywords: ['atr', 'حد ضرر', 'stop', 'trailing', 'متحرک'],
  },
  {
    id: 'inside-bar', title: 'کندل داخلی', titleEn: 'An inside bar',
    summary: 'کندلی که کاملاً داخل کندل قبلش است.', summaryEn: 'A bar wholly inside the one before it.', defaults: [],
    shape: `inside = and(lt(high, high[1]), gt(low, low[1]))
plotshape(inside, "کندل داخلی", shape.circle, color.gray)
${signals('and(inside, gt(close, open))', 'and(inside, lt(close, open))', 'کندل داخلی صعودی', 'کندل داخلی نزولی')}`,
    keywords: ['inside', 'کندل داخلی', 'الگو', 'pattern', 'engulf'],
  },
  {
    id: 'gap', title: 'شکاف قیمتی', titleEn: 'A price gap',
    summary: 'کندل با فاصله از کندل قبل باز می‌شود.', summaryEn: 'A bar opens away from the one before it.', defaults: [1],
    shape: `size = input.float(%1, "درصد", 0.1, 20)
gapUp = gt(open, mul(close[1], 1 + size / 100))
gapDown = lt(open, mul(close[1], 1 - size / 100))
${signals('gapUp', 'gapDown', 'شکاف صعودی', 'شکاف نزولی')}`,
    keywords: ['gap', 'شکاف', 'پرش'],
  },
  {
    id: 'adx-trend', title: 'روند قوی', titleEn: 'A trend worth trading',
    summary: 'ADX از آستانه رد می‌شود.', summaryEn: 'ADX passes a threshold.', defaults: [14, 25],
    shape: `span = input.int(%1, "دوره", 2, 200)
gate = input.int(%2, "آستانه", 5, 60)
strength = ta.adx(span, span)
plot(strength, "ADX", color.purple)
hline(gate, "آستانه")
woke = ta.crossover(strength, gate)
${signals('and(woke, gt(close, ta.ema(close, span)))', 'and(woke, lt(close, ta.ema(close, span)))', 'روند صعودی جان گرفت', 'روند نزولی جان گرفت')}`,
    keywords: ['adx', 'روند قوی', 'strength', 'قدرت روند'],
  },
  {
    id: 'session-open', title: 'شکست اولین کندل', titleEn: 'Breaking the first bar',
    summary: 'قیمت از سقف یا کف اولین کندل روز رد می‌شود.', summaryEn: "Price passes the first bar of the run's high or low.", defaults: [1],
    shape: `back = input.int(%1, "کندل مرجع", 1, 20)
top = ref(high, back)
bottom = ref(low, back)
plot(top, "سقف مرجع", color.green)
plot(bottom, "کف مرجع", color.red)
${signals('ta.crossover(close, top)', 'ta.crossunder(close, bottom)', 'سقف کندل مرجع شکست', 'کف کندل مرجع شکست')}`,
    keywords: ['session', 'باز شدن', 'اولین کندل', 'open', 'روز'],
  },
];

/** The script, with the sentence's numbers in its holes and the defaults in the rest. */
export function templateSource(t: ScriptTemplate, numbers: number[] = []): string {
  let text = t.shape;
  t.defaults.forEach((fallback, i) => { text = text.split(`%${i + 1}`).join(String(numbers[i] ?? fallback)); });
  return `${text.trim()}\n`;
}

/** How well a template answers a sentence: the number of its keywords the sentence carries. */
export function templateScore(t: ScriptTemplate, text: string): number {
  const lower = text.toLowerCase();
  return t.keywords.filter((k) => lower.includes(k)).length;
}

/** The templates that answer a sentence, best first; the first three when nothing matched. */
export function matchingTemplates(text: string, limit = 3): ScriptTemplate[] {
  const ranked = TEMPLATES.map((t) => [t, templateScore(t, text)] as const).filter(([, s]) => s > 0);
  if (!ranked.length) return TEMPLATES.slice(0, limit);
  return [...ranked].sort((a, b) => b[1] - a[1]).slice(0, limit).map(([t]) => t);
}

/** Every whole number in a sentence, Persian digits too, in order. */
export function numbersIn(text: string): number[] {
  const latin = text.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0));
  return (latin.match(/\d+/g) ?? []).map(Number).filter(Number.isFinite);
}
