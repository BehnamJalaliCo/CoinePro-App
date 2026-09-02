package com.coinepro.core.script

/**
 * One entry in the language reference.
 *
 * [signature] is written exactly as it would be typed, and the editor inserts it verbatim when the
 * entry is tapped. That is the reason the parameter names here are the real ones rather than
 * prettier placeholders: a reader who taps `ta.sma(close, 20)` gets something that runs.
 */
data class ScriptFunction(
    val signature: String,
    val summary: String,
    /** What it hands back, in a few words. */
    val returns: String,
)

data class ScriptReferenceGroup(val title: String, val functions: List<ScriptFunction>)

/**
 * Everything the language can do, in the order a reader meets it.
 *
 * Kept beside the interpreter rather than in the screen, because it documents *this* dispatch
 * table: an entry here that `Builtins.call` does not answer is a promise the language does not
 * keep, and `ScriptReferenceTest` fails the build over exactly that.
 */
object ScriptReference {

    /** The series every script starts with, before it computes anything. */
    val SERIES: List<ScriptFunction> = listOf(
        ScriptFunction("close", "قیمت بسته‌شدن هر کندل.", "سری عددی"),
        ScriptFunction("open", "قیمت باز شدن هر کندل.", "سری عددی"),
        ScriptFunction("high", "بالاترین قیمت هر کندل.", "سری عددی"),
        ScriptFunction("low", "پایین‌ترین قیمت هر کندل.", "سری عددی"),
        ScriptFunction("volume", "حجم هر کندل. روی نمادهایی که فید حجم ندارند خالی است.", "سری عددی"),
        ScriptFunction("hl2", "میانگین بالا و پایین: (high + low) ÷ 2.", "سری عددی"),
        ScriptFunction("hlc3", "قیمت معمول: (high + low + close) ÷ 3.", "سری عددی"),
        ScriptFunction("ohlc4", "میانگین هر چهار قیمت کندل.", "سری عددی"),
        ScriptFunction("time", "زمان هر کندل، به ثانیهٔ یونیکس.", "سری عددی"),
        ScriptFunction("bar_index", "شمارهٔ کندل، از صفر.", "سری عددی"),
        ScriptFunction("n", "تعداد کل کندل‌های نمودار.", "عدد"),
        ScriptFunction(
            "confirmed",
            "روی هر کندلی که بسته شده درست است و روی آخرین کندل نادرست. شرط سیگنال را با «and confirmed» ببندید تا نشانه روی کندلِ در حال شکل‌گیری ننشیند و بعداً جابه‌جا نشود.",
            "سری شرطی",
        ),
    )

    val GROUPS: List<ScriptReferenceGroup> = listOf(
        ScriptReferenceGroup(
            "میانگین‌ها",
            listOf(
                ScriptFunction("ta.sma(close, 20)", "میانگین متحرک ساده روی طول داده‌شده.", "سری عددی"),
                ScriptFunction("ta.ema(close, 20)", "میانگین متحرک نمایی؛ به کندل‌های تازه وزن بیشتری می‌دهد.", "سری عددی"),
                ScriptFunction("ta.wma(close, 20)", "میانگین متحرک وزنی خطی.", "سری عددی"),
                ScriptFunction("ta.hma(close, 21)", "میانگین متحرک هال؛ کم‌تأخیرتر از نمایی، اما دورهٔ گرم شدن بلندتری دارد.", "سری عددی"),
                ScriptFunction("ta.smma(close, 14)", "میانگین متحرک هموار (RMA)؛ همان میانگینی که داخل RSI و ATR است.", "سری عددی"),
                ScriptFunction("ta.zlema(close, 20)", "میانگین نمایی بی‌تأخیر.", "سری عددی"),
                ScriptFunction("ta.kama(close, 10, 2, 30)", "میانگین تطبیقی کافمن؛ در روند تند و در رنج کند می‌شود.", "سری عددی"),
                ScriptFunction("ta.mcginley(close, 14)", "پویای مک‌گینلی؛ میانگینی که با سرعت بازار تنظیم می‌شود.", "سری عددی"),
                ScriptFunction("ta.linreg(close, 20)", "خط رگرسیون خطی روی پنجره — مقدارِ انتهای خط در هر کندل.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "نوسان‌نماها",
            listOf(
                ScriptFunction("ta.rsi(close, 14)", "شاخص قدرت نسبی، بین صفر و صد.", "سری عددی"),
                ScriptFunction("ta.cci(20)", "شاخص کانال کالا. از high و low و close خودِ نمودار می‌خواند.", "سری عددی"),
                ScriptFunction("ta.atr(14)", "میانگین دامنهٔ واقعی — اندازهٔ نوسان، نه جهت آن.", "سری عددی"),
                ScriptFunction("ta.macd(close, 12, 26, 9)", "خط مکدی: تفاضل دو میانگین نمایی.", "سری عددی"),
                ScriptFunction("ta.macd_signal(close, 12, 26, 9)", "خط سیگنال مکدی.", "سری عددی"),
                ScriptFunction("ta.macd_hist(close, 12, 26, 9)", "هیستوگرام مکدی: خط منهای سیگنال.", "سری عددی"),
                ScriptFunction("ta.momentum(close, 10)", "تفاضل قیمت با چند کندل قبل.", "سری عددی"),
                ScriptFunction("ta.williams_r(14)", "ویلیامز %R، بین صفر و منهای صد. از high و low و close نمودار می‌خواند.", "سری عددی"),
                ScriptFunction("ta.ultimate(7, 14, 28)", "نوسان‌نمای نهایی لری ویلیامز، سه‌دوره‌ای.", "سری عددی"),
                ScriptFunction("ta.trix(close, 18, 9)", "TRIX: نرخ تغییرِ میانگین نمایی سه‌گانه.", "سری عددی"),
                ScriptFunction("ta.trix_signal(close, 18, 9)", "خط سیگنال TRIX.", "سری عددی"),
                ScriptFunction("ta.fisher(9)", "تبدیل فیشر روی میانهٔ کندل؛ برگشت‌ها را تیز می‌کند.", "سری عددی"),
                ScriptFunction("ta.fisher_signal(9)", "خط سیگنال فیشر (یک کندل تأخیر).", "سری عددی"),
                ScriptFunction("ta.crsi(close, 3, 2, 100)", "RSI کانرز: میانگین RSI کوتاه، RSI رشته و رتبهٔ درصدی.", "سری عددی"),
                ScriptFunction("ta.smi(close, 20, 5, 5)", "SMI ارگودیک (TSI)؛ بین منفی صد و صد.", "سری عددی"),
                ScriptFunction("ta.smi_signal(close, 20, 5, 5)", "خط سیگنال SMI.", "سری عددی"),
                ScriptFunction("ta.chop(14)", "شاخص آشفتگی؛ بالای ۶۱ رنج، زیر ۳۸ روند.", "سری عددی"),
                ScriptFunction("ta.bop(1)", "توازن قدرت: (close − open) ÷ (high − low)، با هموارسازی اختیاری.", "سری عددی"),
                ScriptFunction("ta.vortex_plus(14)", "خط مثبت ورتکس.", "سری عددی"),
                ScriptFunction("ta.vortex_minus(14)", "خط منفی ورتکس.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "نوسان (تلاطم)",
            listOf(
                ScriptFunction("ta.tr()", "دامنهٔ واقعی هر کندل، بدون میانگین.", "سری عددی"),
                ScriptFunction("ta.hv(close, 10)", "تلاطم تاریخی: انحراف معیارِ بازده لگاریتمی، سالانه‌شده.", "سری عددی"),
                ScriptFunction("ta.chaikin_vol(10, 10)", "تلاطم چایکین: نرخ تغییرِ میانگینِ دامنهٔ کندل.", "سری عددی"),
                ScriptFunction("ta.bb_percent(close, 20, 2)", "%B: جای قیمت بین دو لبهٔ باند بولینگر، صفر تا یک.", "سری عددی"),
                ScriptFunction("ta.bb_width(close, 20, 2)", "پهنای باند بولینگر نسبت به میانه.", "سری عددی"),
                ScriptFunction("ta.keltner_upper(20, 2)", "لبهٔ بالای کانال کلتنر (EMA ± ضریب × ATR).", "سری عددی"),
                ScriptFunction("ta.keltner_lower(20, 2)", "لبهٔ پایین کانال کلتنر.", "سری عددی"),
                ScriptFunction("ta.keltner_basis(20, 2)", "میانهٔ کانال کلتنر.", "سری عددی"),
                ScriptFunction("ta.env_upper(close, 20, 1)", "لبهٔ بالای پاکت: میانگین ساده به‌اضافهٔ درصد.", "سری عددی"),
                ScriptFunction("ta.env_lower(close, 20, 1)", "لبهٔ پایین پاکت.", "سری عددی"),
                ScriptFunction("ta.env_basis(close, 20, 1)", "میانهٔ پاکت.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "باندها",
            listOf(
                ScriptFunction("ta.bb_basis(close, 20, 2)", "میانهٔ باند بولینگر.", "سری عددی"),
                ScriptFunction("ta.bb_upper(close, 20, 2)", "لبهٔ بالای باند بولینگر.", "سری عددی"),
                ScriptFunction("ta.bb_lower(close, 20, 2)", "لبهٔ پایین باند بولینگر.", "سری عددی"),
                ScriptFunction("ta.donchian_upper(20)", "سقف کانال دانچیان. برای شکست، یک کندل عقب بخوانیدش: ta.donchian_upper(20)[1].", "سری عددی"),
                ScriptFunction("ta.donchian_lower(20)", "کف کانال دانچیان.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "روند و جهت",
            listOf(
                ScriptFunction("ta.supertrend(10, 3)", "خط سوپرترند: باندی به اندازهٔ ATR که سمتش را با قیمت عوض می‌کند.", "سری عددی"),
                ScriptFunction("ta.supertrend_trend(10, 3)", "جهت سوپرترند: ۱ صعودی، ۱− نزولی. تغییر جهت، همان برگشت است.", "سری عددی"),
                ScriptFunction("ta.adx(14)", "قدرت روند بدون جهت. زیر آستانه یعنی روندی در کار نیست.", "سری عددی"),
                ScriptFunction("ta.di_plus(14)", "شاخص جهت‌دار صعودی.", "سری عددی"),
                ScriptFunction("ta.di_minus(14)", "شاخص جهت‌دار نزولی.", "سری عددی"),
                ScriptFunction("ta.stoch_k(14, 3)", "خط ‎%K استوکاستیک.", "سری عددی"),
                ScriptFunction("ta.stoch_d(14, 3)", "خط ‎%D استوکاستیک؛ میانگین ‎%K.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "ایچیموکو",
            listOf(
                ScriptFunction("ta.ichimoku_conversion(9, 26)", "تنکان‌سن.", "سری عددی"),
                ScriptFunction("ta.ichimoku_base(9, 26)", "کیجون‌سن.", "سری عددی"),
                ScriptFunction(
                    "ta.ichimoku_span_a(9, 26)",
                    "سنکو A، بدون جابه‌جایی. ابری که نمودار روی این کندل می‌کشد، ۲۶ کندل قبل محاسبه شده: ta.ichimoku_span_a(9, 26)[26].",
                    "سری عددی",
                ),
                ScriptFunction("ta.ichimoku_span_b(9, 26, 52)", "سنکو B، بدون جابه‌جایی.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "حجم",
            listOf(
                ScriptFunction(
                    "ta.vwap()",
                    "میانگین وزنیِ حجمِ قیمت، از اولین کندل بارگذاری‌شده. روی فیدی که حجم نمی‌دهد بی‌مقدار است، نه صفر.",
                    "سری عددی",
                ),
                ScriptFunction("ta.obv()", "حجم تعادلی. مثل vwap، بدون حجم بی‌مقدار است.", "سری عددی"),
                ScriptFunction("ta.ad()", "خط انباشت/توزیع.", "سری عددی"),
                ScriptFunction("ta.pvt()", "روند قیمت‌حجم.", "سری عددی"),
                ScriptFunction("ta.force(13)", "شاخص نیروی الدر، هموارشده با میانگین نمایی.", "سری عددی"),
                ScriptFunction("ta.chaikin_osc(3, 10)", "نوسان‌نمای چایکین: تفاضل دو میانگین از خط انباشت/توزیع.", "سری عددی"),
                ScriptFunction("ta.eom(14)", "سهولت حرکت.", "سری عددی"),
                ScriptFunction("ta.klinger(34, 55, 13)", "نوسان‌نمای حجم کلینگر.", "سری عددی"),
                ScriptFunction("ta.klinger_signal(34, 55, 13)", "خط سیگنال کلینگر.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "آمار",
            listOf(
                ScriptFunction("ta.highest(high, 20)", "بیشترین مقدار در پنجرهٔ داده‌شده.", "سری عددی"),
                ScriptFunction("ta.lowest(low, 20)", "کمترین مقدار در پنجرهٔ داده‌شده.", "سری عددی"),
                ScriptFunction("ta.sum(volume, 20)", "جمع پنجره.", "سری عددی"),
                ScriptFunction("ta.stdev(close, 20)", "انحراف معیار پنجره.", "سری عددی"),
                ScriptFunction("ta.change(close, 1)", "تفاوت با چند کندل قبل.", "سری عددی"),
                ScriptFunction("ta.roc(close, 12)", "درصد تغییر نسبت به چند کندل قبل.", "سری عددی"),
                ScriptFunction("ta.cum(volume)", "جمع تجمعی از اولین کندل.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "منطق کندل‌ها",
            listOf(
                ScriptFunction("ta.rising(close, 3)", "درست وقتی مقدار در هر یک از n کندل اخیر بالا رفته باشد.", "سری شرطی"),
                ScriptFunction("ta.falling(close, 3)", "درست وقتی مقدار در هر یک از n کندل اخیر پایین آمده باشد.", "سری شرطی"),
                ScriptFunction("ta.barssince(cond)", "چند کندل از آخرین باری که شرط درست بود گذشته؛ پیش از اولین بار بی‌مقدار.", "سری عددی"),
                ScriptFunction("ta.valuewhen(cond, close, 0)", "مقدار سری در آخرین کندلی که شرط درست بود؛ عدد سوم یعنی چندمین بار قبل‌تر.", "سری عددی"),
                ScriptFunction("ta.pivothigh(5, 5)", "سقف محلی: بالاتر از n کندل قبل و n کندل بعد. روی کندلی که تأیید می‌شود می‌نشیند و بعداً جابه‌جا نمی‌شود.", "سری عددی"),
                ScriptFunction("ta.pivotlow(5, 5)", "کف محلی، به همان قاعده.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "تقاطع‌ها",
            listOf(
                ScriptFunction("ta.crossover(a, b)", "کندلی که در آن a از پایین به بالای b رفت.", "سری شرطی"),
                ScriptFunction("ta.crossunder(a, b)", "کندلی که در آن a از بالا به پایین b رفت.", "سری شرطی"),
            ),
        ),
        ScriptReferenceGroup(
            "ریاضی",
            listOf(
                ScriptFunction("math.abs(x)", "قدر مطلق.", "عدد یا سری"),
                ScriptFunction("math.max(a, b)", "بزرگ‌تر از دو مقدار.", "عدد یا سری"),
                ScriptFunction("math.min(a, b)", "کوچک‌تر از دو مقدار.", "عدد یا سری"),
                ScriptFunction("math.round(x)", "گرد کردن به نزدیک‌ترین عدد صحیح.", "عدد یا سری"),
                ScriptFunction("math.floor(x)", "گرد کردن به پایین.", "عدد یا سری"),
                ScriptFunction("math.ceil(x)", "گرد کردن به بالا.", "عدد یا سری"),
                ScriptFunction("math.sqrt(x)", "جذر.", "عدد یا سری"),
                ScriptFunction("math.log(x)", "لگاریتم طبیعی.", "عدد یا سری"),
                ScriptFunction("math.sign(x)", "علامت: ۱−، ۰ یا ۱.", "عدد یا سری"),
                ScriptFunction("math.pow(a, b)", "توان.", "عدد یا سری"),
            ),
        ),
        ScriptReferenceGroup(
            "شرط و جای‌گزینی",
            listOf(
                ScriptFunction("iff(condition, a, b)", "روی هر کندل، اگر شرط برقرار بود a وگرنه b.", "سری عددی"),
                ScriptFunction("nz(series, 0)", "جای هر کندل بی‌مقدار، عدد داده‌شده را می‌گذارد.", "سری عددی"),
            ),
        ),
        ScriptReferenceGroup(
            "ورودی کاربر",
            listOf(
                ScriptFunction(
                    "input(14, title = \"طول\", min = 2, max = 200)",
                    "یک عدد که خواننده بدون دست‌زدن به کد از پنل تغییرش می‌دهد.",
                    "عدد",
                ),
            ),
        ),
        ScriptReferenceGroup(
            "خروجی روی نمودار",
            listOf(
                ScriptFunction(
                    "plot(series, title = \"نام\", color = color.gold, width = 1.4, dashed = false, pane = \"auto\")",
                    "یک خط روی نمودار. جای خط — روی قیمت یا در پنل جدا — خودکار تعیین می‌شود مگر pane را بدهید.",
                    "همان سری",
                ),
                ScriptFunction(
                    "hline(70, title = \"اشباع خرید\", color = color.grey)",
                    "یک خط افقی ثابت.",
                    "همان عدد",
                ),
                ScriptFunction(
                    "marker(condition, title = \"ورود\", style = \"up\", color = color.green)",
                    "روی هر کندلی که شرط برقرار است یک نشانه می‌گذارد. style یکی از up، down یا circle.",
                    "همان شرط",
                ),
                ScriptFunction(
                    "signal(condition, entry, stop, target = target, buy = true)",
                    "از آخرین کندلی که شرط در آن برقرار شد یک ستاپ می‌سازد: ورود، حد ضرر، هدف و نسبت ریسک به بازده.",
                    "درست/نادرست",
                ),
                ScriptFunction("log(\"متن\")", "یک سطر در پنل خروجی می‌نویسد.", "درست"),
            ),
        ),
    )

    /** Every function name the reference claims exists, for the test that checks it against the interpreter. */
    val NAMES: List<String> = GROUPS.flatMap { group ->
        group.functions.map { it.signature.substringBefore('(') }
    }

    /**
     * The colours a script can name, read from the interpreter's own table.
     *
     * Derived rather than listed, so the palette the editor offers and the palette the language
     * accepts cannot drift apart. A reference that offers a colour the interpreter refuses is a
     * reference that teaches an error.
     */
    val COLOUR_NAMES: List<String> = Interpreter.COLOURS.keys.sorted()
}
