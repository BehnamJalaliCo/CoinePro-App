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
    )

    val GROUPS: List<ScriptReferenceGroup> = listOf(
        ScriptReferenceGroup(
            "میانگین‌ها",
            listOf(
                ScriptFunction("ta.sma(close, 20)", "میانگین متحرک ساده روی طول داده‌شده.", "سری عددی"),
                ScriptFunction("ta.ema(close, 20)", "میانگین متحرک نمایی؛ به کندل‌های تازه وزن بیشتری می‌دهد.", "سری عددی"),
                ScriptFunction("ta.wma(close, 20)", "میانگین متحرک وزنی خطی.", "سری عددی"),
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
            ),
        ),
        ScriptReferenceGroup(
            "باندها",
            listOf(
                ScriptFunction("ta.bb_basis(close, 20, 2)", "میانهٔ باند بولینگر.", "سری عددی"),
                ScriptFunction("ta.bb_upper(close, 20, 2)", "لبهٔ بالای باند بولینگر.", "سری عددی"),
                ScriptFunction("ta.bb_lower(close, 20, 2)", "لبهٔ پایین باند بولینگر.", "سری عددی"),
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
