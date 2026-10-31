// The in-app NamaScript course (the phone's ScriptLessons.kt), in the order it has to be read: the two
// hardest ideas — everything is a whole series at once, and an absent value is not zero — come third
// and fourth, before anything that quietly depends on them. Every example runs: the lesson's button
// puts it in the editor, on the chart the reader already has open. Written for this engine, whose
// series are combined with functions (gt, and, add, ref) rather than with operators.

export interface ScriptLesson {
  id: string;
  title: string;
  titleEn: string;
  body: string[];
  bodyEn: string[];
  example?: string;
  takeaway: string;
  takeawayEn: string;
}

export const LESSONS: ScriptLesson[] = [
  {
    id: 'what', title: 'نمااسکریپت چیست', titleEn: 'What NamaScript is',
    body: [
      'نمااسکریپت زبان کوچکی است برای نوشتن اندیکاتور خودتان. چیزی که می‌نویسید مستقیم روی همان چارتی که باز است اجرا می‌شود و همان‌جا کشیده می‌شود − نه در یک محیط جدا، نه روی داده‌ای دیگر.',
      'یک اسکریپت چند سطر است. هر سطر یا چیزی را حساب می‌کند و اسم می‌گذارد، یا چیزی روی چارت می‌کشد. همین.',
      'کد شما در همین مرورگر اجرا می‌شود. جایی فرستاده نمی‌شود و چیزی از حساب یا سفارش‌های شما نمی‌بیند: نمااسکریپت فقط کندل‌ها را می‌خواند و فقط روی چارت می‌کشد. اسکریپت نمی‌تواند معامله باز کند.',
    ],
    bodyEn: [
      'NamaScript is a small language for writing your own indicator. What you write runs on the chart that is open and is drawn right there − not in a separate place, not on other data.',
      'A script is a few lines. Each line either works something out and names it, or draws something on the chart. That is all.',
      'Your code runs in this browser. It is sent nowhere and sees nothing of your account or orders: NamaScript only reads candles and only draws on the chart. A script cannot open a trade.',
    ],
    example: 'plot(close, "قیمت بسته", color.orange)',
    takeaway: 'هر اسکریپت روی کندل‌های همان چارت اجرا می‌شود و فقط رسم می‌کند.', takeawayEn: 'Every script runs on the candles of that chart and only draws.',
  },
  {
    id: 'values', title: 'مقدارها و نام‌ها', titleEn: 'Values and names',
    body: [
      'با «=» به یک محاسبه اسم می‌دهید. بعد از آن هر جا اسم را بنویسید، همان محاسبه است.',
      'اسم‌ها با حرف انگلیسی یا زیرخط شروع می‌شوند. اگر لازم شد مقدار یک اسم را عوض کنید، از «:=» استفاده کنید.',
      'چهار جور مقدار وجود دارد: عدد، متن، درست/نادرست، و رنگ. کنار اینها «سری» هست که درس بعدی است.',
    ],
    bodyEn: [
      'You give a calculation a name with “=”. After that, wherever you write the name, it is that calculation.',
      'Names start with an English letter or an underscore. When you need to change a name’s value, use “:=”.',
      'There are four kinds of value: number, text, true/false, and colour. Beside them is the “series”, which is the next lesson.',
    ],
    example: 'length = 20\naverage = ta.sma(close, length)\nplot(average, "میانگین", color.orange)',
    takeaway: '«=» تعریف می‌کند، «:=» مقدار را عوض می‌کند.', takeawayEn: '“=” defines, “:=” changes the value.',
  },
  {
    id: 'series', title: 'همه‌چیز یک سری است', titleEn: 'Everything is a series',
    body: [
      'این مهم‌ترین ایده‌ی زبان است. «close» یک عدد نیست؛ قیمت بسته‌شدن همه‌ی کندل‌ها با هم است. وقتی می‌نویسید «mul(close, 2)»، این محاسبه روی تک‌تک کندل‌ها انجام می‌شود و نتیجه‌اش دوباره یک سری است.',
      'پس در نمااسکریپت حلقه (for) لازم نیست: هر عبارتی که می‌نویسید یک‌بار برای کل چارت نوشته می‌شود و روی همه‌ی کندل‌ها اجرا می‌شود.',
      'برای همین سری‌ها را با تابع ترکیب می‌کنید، نه با علامت: «add»، «sub»، «mul» و «div». اگر یک طرف عدد ثابت باشد، همان عدد برای همه‌ی کندل‌ها تکرار می‌شود. «sub(close, 10)» یعنی از قیمت هر کندل ده واحد کم کن.',
    ],
    bodyEn: [
      'This is the most important idea in the language. “close” is not a number; it is the closing price of every candle at once. When you write “mul(close, 2)”, the calculation is done on each candle and its result is again a series.',
      'So NamaScript needs no loop (for): each expression is written once for the whole chart and runs on every candle.',
      'That is why series are combined with functions, not symbols: “add”, “sub”, “mul” and “div”. When one side is a plain number, that number repeats for every candle. “sub(close, 10)” means take ten off every candle’s price.',
    ],
    example: 'spread = sub(high, low)\nplot(spread, "دامنه‌ی کندل", color.blue)',
    takeaway: 'یک‌بار می‌نویسید، برای همه‌ی کندل‌ها حساب می‌شود.', takeawayEn: 'Written once, worked out for every candle.',
  },
  {
    id: 'absent', title: 'کندل‌های بی‌مقدار', titleEn: 'Candles with no value',
    body: [
      'میانگین بیست‌کندلی روی کندل پنجم مقداری ندارد − هنوز بیست کندل نگذشته است. زبان آنجا صفر نمی‌گذارد؛ آن کندل را بی‌مقدار می‌گذارد و رسم از جایی شروع می‌شود که واقعاً مقدار وجود دارد.',
      'این تفاوت ظاهری نیست. اگر صفر گذاشته می‌شد، میانگینِ میانگین‌ها خراب می‌شد و چارت یک افت جعلی نشان می‌داد که هرگز اتفاق نیفتاده بود.',
      'تقسیم بر صفر هم بی‌مقدار است، نه بی‌نهایت. «close[1]» روی اولین کندل چارت هم بی‌مقدار است، چون کندلی قبل از آن وجود ندارد.',
      'اگر جایی لازم شد بی‌مقدارها را با عدد پر کنید − و مطمئنید که درست است − «nz» این کار را می‌کند.',
    ],
    bodyEn: [
      'A twenty-bar average has no value on the fifth candle − twenty candles have not passed yet. The language does not put a zero there; it leaves that candle without a value and the drawing starts where there really is one.',
      'This is not cosmetic. Had a zero been put there, an average of averages would be ruined and the chart would show a fake drop that never happened.',
      'Division by zero has no value too, not infinity. “close[1]” on the chart’s first candle has no value, because there is no candle before it.',
      'When you need to fill the gaps with a number − and you are sure that is right − “nz” does it.',
    ],
    example: 'change = ta.change(close, 1)\nplot(nz(change, 0), "تغییر", color.gray)',
    takeaway: 'بی‌مقدار یعنی «نمی‌دانم»، نه صفر.', takeawayEn: 'No value means “I don’t know”, not zero.',
  },
  {
    id: 'history', title: 'نگاه به کندل‌های قبل', titleEn: 'Looking at earlier candles',
    body: [
      '«[1]» یعنی یک کندل قبل، «[5]» یعنی پنج کندل قبل. این کار روی هر سری‌ای جواب می‌دهد، نه فقط روی قیمت.',
      '«gt(close, close[1])» یعنی این کندل بالاتر از کندل قبلی بسته شده − و مثل هر عبارت دیگری، برای همه‌ی کندل‌ها حساب می‌شود.',
      'روی کندل‌های ابتدای چارت که آن‌قدر گذشته وجود ندارد، نتیجه بی‌مقدار است؛ زبان به اولین کندل نمی‌چسبد و عدد جعلی نمی‌سازد.',
    ],
    bodyEn: [
      '“[1]” means one candle back, “[5]” five candles back. It works on any series, not only on price.',
      '“gt(close, close[1])” means this candle closed above the one before − and like any other expression, it is worked out for every candle.',
      'On the first candles of the chart, where there is not that much past, the result has no value; the language does not stick to the first candle or make up a number.',
    ],
    example: 'rising = and(gt(close, close[1]), gt(close[1], close[2]))\nplotshape(rising, "سه کندل صعودی", shape.up, color.green)',
    takeaway: '«[n]» گذشته را می‌خواند و پیش از شروع چارت، بی‌مقدار است.', takeawayEn: '“[n]” reads the past, and before the chart begins it has no value.',
  },
  {
    id: 'conditions', title: 'شرط‌ها', titleEn: 'Conditions',
    body: [
      'مقایسه‌ها − «gt» (بزرگ‌تر)، «lt» (کوچک‌تر)، «ge» و «le» − یک سری درست/نادرست می‌سازند: برای هر کندل یک جواب.',
      'با «and» و «or» شرط‌ها را ترکیب می‌کنید و «iff(شرط، false، true)» یک شرط را برعکس می‌کند. اگر یک طرف روی کندلی بی‌مقدار باشد، جواب آن کندل هم بی‌مقدار است، نه نادرست.',
      'برای انتخاب بین دو مقدار روی هر کندل، از «iff(شرط، الف، ب)» استفاده کنید.',
    ],
    bodyEn: [
      'Comparisons − “gt” (greater), “lt” (less), “ge” and “le” − make a true/false series: one answer per candle.',
      'You combine conditions with “and” and “or”, and “iff(condition, false, true)” turns one around. When one side has no value on a candle, that candle’s answer has none either, rather than false.',
      'To choose between two values on each candle, use “iff(condition, a, b)”.',
    ],
    example: 'trend = ta.sma(close, 50)\nabove = gt(close, trend)\nplot(iff(above, high, low), "لبه‌ی همسو", color.teal)',
    takeaway: 'شرط هم یک سری است: برای هر کندل یک جواب.', takeawayEn: 'A condition is a series too: one answer per candle.',
  },
  {
    id: 'indicators', title: 'اندیکاتورهای آماده', titleEn: 'Ready-made indicators',
    body: [
      'همه‌ی اندیکاتورها زیر «ta.» هستند: «ta.sma»، «ta.ema»، «ta.rsi»، «ta.atr»، «ta.macd» و بقیه. فهرست کاملشان در «راهنمای توابع» ویرایشگر است.',
      'اینها همان فرمول‌های استاندارد این اندیکاتورها هستند؛ چیزی که با آنها می‌سازید با تعریف‌های آشنای Pine یکی است.',
      'ورودی اول معمولاً سری است و دوم طول دوره. بعضی‌ها − مثل «ta.atr» و «ta.cci» − خودشان high و low و close را از چارت برمی‌دارند و فقط طول می‌خواهند.',
    ],
    bodyEn: [
      'Every indicator lives under “ta.”: “ta.sma”, “ta.ema”, “ta.rsi”, “ta.atr”, “ta.macd” and the rest. The full list is in the editor’s reference.',
      'These are the standard formulas of those indicators; what you build with them agrees with Pine’s familiar definitions.',
      'The first argument is usually a series and the second a length. Some − like “ta.atr” and “ta.cci” − take high, low and close from the chart themselves and only want a length.',
    ],
    example: 'plot(ta.ema(close, 20), "EMA 20", color.orange)\nplot(ta.ema(close, 50), "EMA 50", color.blue)',
    takeaway: 'هر اندیکاتور آماده‌ای زیر «ta.» است.', takeawayEn: 'Every ready-made indicator is under “ta.”.',
  },
  {
    id: 'crosses', title: 'تقاطع‌ها', titleEn: 'Crossings',
    body: [
      '«ta.crossover(a, b)» فقط روی همان کندلی درست است که a از پایین به بالای b رفته − نه روی همه‌ی کندل‌هایی که a بالاتر است.',
      'برابر بودن، تقاطع نیست. دو خط که به هم می‌رسند و بی‌آنکه از هم رد شوند برمی‌گردند، هیچ تقاطعی نساخته‌اند؛ اگر این‌طور نبود، دو خط صاف روی هر کندل سیگنال می‌دادند.',
      '«ta.crossunder» همان است در جهت مخالف.',
    ],
    bodyEn: [
      '“ta.crossover(a, b)” is true only on the candle where a went from below b to above it − not on every candle where a is higher.',
      'Touching is not crossing. Two lines that meet and turn back without passing each other have made no crossing; otherwise two flat lines would signal on every candle.',
      '“ta.crossunder” is the same the other way.',
    ],
    example: 'fast = ta.ema(close, 9)\nslow = ta.ema(close, 21)\nplot(fast, "تند", color.orange)\nplot(slow, "کند", color.blue)\nplotshape(ta.crossover(fast, slow), "تقاطع", shape.up, color.green)',
    takeaway: 'تقاطع یک لحظه است، نه یک وضعیت.', takeawayEn: 'A crossing is a moment, not a state.',
  },
  {
    id: 'drawing', title: 'کشیدن روی چارت', titleEn: 'Drawing on the chart',
    body: [
      '«plot» خط می‌کشد، «hline» خط افقی ثابت، و «plotshape» روی کندل‌هایی که شرط برقرار است نشانه می‌گذارد.',
      'جای خط‌ها با «اندازه‌گیری» تعیین می‌شود نه از روی اسم: اسکریپتی که عددهایش نزدیک قیمت می‌ماند روی قیمت کشیده می‌شود و اسکریپتی که نمی‌ماند، در پنل جدا. اگر نظر دیگری دارید، در «این اسکریپت مال من است» گزینه‌ی «در پنل جدا» را روشن کنید.',
      'همه‌ی خطوطی که پنل جدا می‌خواهند در یک پنل جمع می‌شوند و یک مقیاس مشترک دارند − که دقیقاً همان چیزی است که مقایسه‌شان را ممکن می‌کند.',
    ],
    bodyEn: [
      '“plot” draws a line, “hline” a fixed horizontal line, and “plotshape” marks the candles where a condition holds.',
      'Where the lines go is decided by measuring, not by name: a script whose numbers stay near the price is drawn on the price, and one whose numbers do not, in its own panel. If you think otherwise, turn on “In its own panel” under “This script is mine”.',
      'All the lines that want their own panel share one panel and one scale − which is exactly what makes them comparable.',
    ],
    example: 'r = ta.rsi(close, 14)\nplot(r, "RSI", color.orange)\nhline(70, "اشباع خرید", color.red)\nhline(30, "اشباع فروش", color.green)',
    takeaway: 'جای خط با اندازه‌گیری تعیین می‌شود، نه با نامش.', takeawayEn: 'A line’s place is measured, not named.',
  },
  {
    id: 'inputs', title: 'ورودی برای خواننده', titleEn: 'Inputs for the reader',
    body: [
      '«input.int» و «input.float» عددی می‌سازند که از تنظیمات اسکریپت روی چارت قابل تغییر است − بی‌آنکه کسی به کد دست بزند.',
      'دومین آرگومان اسم آن ورودی در تنظیمات است. سومی و چهارمی کمینه و بیشینه‌اش را می‌بندند، و همین بستن است که جلوی «دوره‌ی صفر» یا «دوره‌ی منفی» را می‌گیرد.',
      'مقداری که خواننده انتخاب کرده ذخیره می‌شود و دفعه‌ی بعد هم همان است.',
    ],
    bodyEn: [
      '“input.int” and “input.float” make a number that can be changed from the script’s settings on the chart − without anybody touching the code.',
      'The second argument is that input’s name in the settings. The third and fourth close its range, and that closing is what stops a “zero length” or a “negative length”.',
      'The value the reader chose is kept and is the same next time.',
    ],
    example: 'length = input.int(14, "دوره‌ی میانگین", 2, 200)\nplot(ta.ema(close, length), "میانگین", color.orange)',
    takeaway: 'هر عددی که ممکن است عوض شود، باید input باشد.', takeawayEn: 'Any number that might change should be an input.',
  },
  {
    id: 'setups', title: 'ساختن یک ستاپ', titleEn: 'Building a setup',
    body: [
      '«riskreward(ورود، حد ضرر، هدف)» از سه سری، یک ستاپ کامل می‌کشد: ورود، حد ضرر و هدف − همان ناحیه‌های رنگی‌ای که سیگنال‌های خود برنامه روی چارت می‌کشند.',
      'ستاپ از «آخرین» کندلی گرفته می‌شود که هر سه عدد را دارد. پس با «iff(شرط، close، null)» ورود را فقط روی کندل‌های شرط بگذارید: چیزی که ممکن است حالا به آن عمل کنید تازه‌ترین آن است؛ ستاپی از سه هفته پیش، ستاپ نیست.',
      '«alertcondition» همان شرط را به هشدار و به آزمون استراتژی می‌برد: دو شرط اول، ورود و خروج‌اند.',
    ],
    bodyEn: [
      '“riskreward(entry, stop, target)” draws a whole setup from three series: entry, stop and target − the same coloured zones the app’s own signals draw on the chart.',
      'The setup is taken from the “last” candle that has all three numbers. So put the entry only on the condition’s candles with “iff(condition, close, null)”: what you might act on now is the latest one; a setup from three weeks ago is not a setup.',
      '“alertcondition” takes the same condition to alerts and to the strategy tester: the first two conditions are an entry and its exit.',
    ],
    example: 'a = ta.atr(14)\nroof = ta.highest(high, 20)\nbroke = ta.crossover(close, ref(roof, 1))\n\nentry = iff(broke, close, null)\nstop = sub(entry, mul(a, 2))\nriskreward(entry, stop, add(entry, mul(a, 4)))\nalertcondition(broke, "سقف بیست کندل شکست")',
    takeaway: 'یک ستاپ سه عدد است: ورود، حد ضرر، هدف.', takeawayEn: 'A setup is three numbers: entry, stop, target.',
  },
  {
    id: 'limits', title: 'مرزهای زبان', titleEn: 'The language’s limits',
    body: [
      'حلقه و تابعِ کاربر در کار نیست. زبان عمداً این‌طور طراحی شده: هر عبارت یک‌بار روی کل سری اجرا می‌شود، و همین است که اجرای اسکریپت را سریع و قابل پیش‌بینی نگه می‌دارد.',
      'چیزی که با حلقه می‌نوشتید، تقریباً همیشه با «iff» و «[n]» و توابع پنجره‌ای مثل «ta.highest» نوشتنی است.',
      'اسکریپت به شبکه، فایل و حساب شما دسترسی ندارد و نمی‌تواند سفارش بگذارد. هر اجرا هم سقف زمانی دارد، پس یک اسکریپت اشتباه، برنامه را قفل نمی‌کند.',
      'اجرای اسکریپت روی همان کندل‌هایی است که چارت نشان می‌دهد. در حالت بازپخش، اسکریپت هم فقط تا همان کندل را می‌بیند − آینده نه به شما نشان داده می‌شود و نه به اسکریپت شما.',
    ],
    bodyEn: [
      'There are no loops and no user functions. The language is made that way on purpose: each expression runs once over the whole series, which is what keeps a script fast and predictable.',
      'What you would write with a loop can nearly always be written with “iff”, “[n]” and window functions such as “ta.highest”.',
      'A script has no access to the network, files or your account and cannot place an order. Every run also has a time limit, so a wrong script does not lock the app.',
      'A script runs on the candles the chart shows. In replay, the script too sees only up to that candle − the future is shown neither to you nor to your script.',
    ],
    takeaway: 'بدون حلقه، بدون شبکه، بدون سفارش − عمداً.', takeawayEn: 'No loops, no network, no orders − on purpose.',
  },
];
