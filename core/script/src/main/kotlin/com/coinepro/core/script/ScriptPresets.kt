package com.coinepro.core.script

/**
 * A script that ships with the app.
 *
 * [id] is repository-owned identity and must stay stable: a reader who opened a preset and edited
 * it has a saved copy that remembers where it came from, and renaming the id orphans it.
 *
 * These are not demos. Every one of them is a study somebody actually trades, written the way it
 * would be written by hand — which is the only way a preset teaches anything. A library of toys
 * teaches that the language is a toy.
 */
data class ScriptPreset(
    val id: String,
    val title: String,
    val summary: String,
    /** What a reader learns by reading it, one line. Shown under the title in the library. */
    val teaches: String,
    val source: String,
)

object ScriptPresets {

    /**
     * The starting point for a blank script.
     *
     * Not an empty string. An empty editor is the hardest screen in any programming product, and a
     * reader who has never seen the language needs a first line they can change rather than a
     * cursor blinking at nothing.
     */
    val BLANK = """
        // اسکریپت تازه
        // یک خط بکشید و تغییرش بدهید:
        plot(ta.ema(close, 20), title = "میانگین ۲۰", color = color.gold)
    """.trimIndent()

    val ALL: List<ScriptPreset> = listOf(
        ScriptPreset(
            id = "ema-cross",
            title = "تقاطع دو میانگین",
            summary = "دو میانگین نمایی و نشانه روی هر تقاطع.",
            teaches = "متغیر، ta.ema، ta.crossover و marker",
            source = """
                // دو میانگین نمایی؛ تقاطع رو به بالا نشانهٔ سبز و رو به پایین نشانهٔ قرمز می‌گیرد.
                fastLength = input(9, title = "دورهٔ تند", min = 2, max = 200)
                slowLength = input(21, title = "دورهٔ کند", min = 3, max = 400)

                fast = ta.ema(close, fastLength)
                slow = ta.ema(close, slowLength)

                plot(fast, title = "تند", color = color.gold)
                plot(slow, title = "کند", color = color.blue)

                marker(ta.crossover(fast, slow), title = "تقاطع صعودی", style = "up")
                marker(ta.crossunder(fast, slow), title = "تقاطع نزولی", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "rsi-zones",
            title = "RSI با نواحی اشباع",
            summary = "شاخص قدرت نسبی در پنل خودش، با خطوط ۳۰ و ۷۰.",
            teaches = "پنل جدا، hline و اینکه چرا RSI روی قیمت کشیده نمی‌شود",
            source = """
                // RSI در پنل خودش می‌نشیند: مقیاسش صفر تا صد است و روی قیمت، محور قیمت را
                // به یک خط صاف تبدیل می‌کند.
                length = input(14, title = "دورهٔ RSI", min = 2, max = 100)
                rsi = ta.rsi(close, length)

                plot(rsi, title = "RSI", color = color.gold)
                hline(70, title = "اشباع خرید", color = color.sell)
                hline(50, color = color.grey)
                hline(30, title = "اشباع فروش", color = color.buy)

                marker(ta.crossunder(rsi, 30), title = "بازگشت از کف", style = "up")
                marker(ta.crossover(rsi, 70), title = "بازگشت از سقف", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "bollinger-squeeze",
            title = "فشردگی باند بولینگر",
            summary = "باند بولینگر، و نشانه روی کندل‌هایی که باند در تنگ‌ترین حالت خودش است.",
            teaches = "باندها، ta.lowest و مقایسهٔ یک سری با گذشتهٔ خودش",
            source = """
                // پهنای باند نسبت به میانه؛ وقتی به کمترین مقدار صد کندل اخیر می‌رسد، بازار
                // جمع شده است. این نشانهٔ جهت نیست — نشانهٔ آماده شدن است.
                length = input(20, title = "دورهٔ باند", min = 5, max = 200)
                lookback = input(100, title = "پنجرهٔ مقایسه", min = 20, max = 500)

                upper = ta.bb_upper(close, length, 2)
                lower = ta.bb_lower(close, length, 2)
                basis = ta.bb_basis(close, length, 2)

                plot(upper, title = "لبهٔ بالا", color = color.grey)
                plot(basis, title = "میانه", color = color.gold, dashed = true)
                plot(lower, title = "لبهٔ پایین", color = color.grey)

                width = (upper - lower) / basis * 100
                tightest = ta.lowest(width, lookback)
                marker(width <= tightest, title = "فشردگی", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "atr-stop",
            title = "حد ضرر بر پایهٔ ATR",
            summary = "فاصلهٔ حد ضرر را از نوسان واقعی بازار می‌گیرد، نه از یک درصد ثابت.",
            teaches = "ta.atr و اینکه چرا یک درصد ثابت روی طلا و روی بیت‌کوین یک چیز نیست",
            source = """
                // یک درصد ثابت روی هر نمادی معنای دیگری دارد. ATR فاصله را از نوسان خودِ همان
                // نماد می‌گیرد، پس همان اسکریپت روی طلا و روی بیت‌کوین درست کار می‌کند.
                multiplier = input(2, title = "ضریب ATR", min = 0.5, max = 6)
                atr = ta.atr(14)

                longStop = close - atr * multiplier
                shortStop = close + atr * multiplier

                plot(longStop, title = "حد ضرر خرید", color = color.buy, dashed = true)
                plot(shortStop, title = "حد ضرر فروش", color = color.sell, dashed = true)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "breakout-setup",
            title = "شکست سقف بیست کندل",
            summary = "یک ستاپ کامل: ورود روی شکست، حد ضرر زیر کف، هدف در دو برابر ریسک.",
            teaches = "signal و اینکه یک ستاپ سه عدد است، نه یک فلش",
            source = """
                // ستاپ کامل. signal آخرین کندلی را می‌گیرد که شرط در آن برقرار شده — نه اولی —
                // چون چیزی که ممکن است حالا به آن عمل کنید، تازه‌ترین آن است.
                length = input(20, title = "پنجرهٔ سقف و کف", min = 5, max = 200)

                roof = ta.highest(high, length)
                floor = ta.lowest(low, length)
                plot(roof, title = "سقف", color = color.grey, dashed = true)
                plot(floor, title = "کف", color = color.grey, dashed = true)

                broke = ta.crossover(close, roof[1])
                marker(broke, title = "شکست", style = "up")

                entry = close
                stop = floor
                risk = entry - stop
                signal(broke, entry, stop, target = entry + risk * 2, buy = true)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "macd-histogram",
            title = "مکدی و هیستوگرام",
            summary = "خط مکدی، خط سیگنال و تفاضلشان در یک پنل.",
            teaches = "چند خروجی از یک اندیکاتور، و اینکه چند plot در یک پنل جمع می‌شوند",
            source = """
                // هر سه از یک محاسبه می‌آیند و باید در یک پنل و روی یک مقیاس خوانده شوند.
                fast = input(12, title = "تند", min = 2, max = 100)
                slow = input(26, title = "کند", min = 3, max = 200)
                smooth = input(9, title = "سیگنال", min = 2, max = 100)

                macd = ta.macd(close, fast, slow, smooth)
                signalLine = ta.macd_signal(close, fast, slow, smooth)

                plot(macd, title = "مکدی", color = color.blue)
                plot(signalLine, title = "سیگنال", color = color.gold)
                hline(0, color = color.grey)

                marker(ta.crossover(macd, signalLine), title = "تقاطع صعودی", style = "up")
                marker(ta.crossunder(macd, signalLine), title = "تقاطع نزولی", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "trend-filter",
            title = "فیلتر روند",
            summary = "همان تقاطع، اما فقط در جهت روند بلندمدت.",
            teaches = "and، iff و رنگ‌آمیزی یک خط بر اساس شرط",
            source = """
                // یک تقاطع در خلاف جهت روند بزرگ‌تر، همان تقاطع نیست. and دو شرط را روی هر
                // کندل با هم می‌سنجد.
                trendLength = input(200, title = "دورهٔ روند", min = 20, max = 500)
                fastLength = input(10, title = "تند", min = 2, max = 100)
                slowLength = input(30, title = "کند", min = 3, max = 200)

                trend = ta.sma(close, trendLength)
                fast = ta.ema(close, fastLength)
                slow = ta.ema(close, slowLength)

                up = close > trend
                plot(trend, title = "روند", color = color.grey, width = 2)
                plot(fast, title = "تند", color = color.gold)
                plot(slow, title = "کند", color = color.blue)

                marker(ta.crossover(fast, slow) and up, title = "خرید همسو با روند", style = "up")
                marker(ta.crossunder(fast, slow) and not up, title = "فروش همسو با روند", style = "down")
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "volume-spike",
            title = "جهش حجم",
            summary = "کندل‌هایی که حجمشان چند برابر میانگین است.",
            teaches = "volume، ta.sma روی حجم، و نسبت‌گرفتن دو سری",
            source = """
                // حجم را با میانگین خودش می‌سنجیم، نه با یک عدد ثابت: «حجم بالا» روی هر نماد
                // عدد دیگری است.
                length = input(20, title = "دورهٔ میانگین حجم", min = 5, max = 200)
                threshold = input(2.5, title = "چند برابر میانگین", min = 1.2, max = 10)

                average = ta.sma(volume, length)
                ratio = volume / average

                plot(ratio, title = "نسبت حجم", color = color.orange, pane = "separate")
                hline(threshold, title = "آستانه", color = color.grey)
                marker(ratio > threshold, title = "جهش حجم", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "inside-bar",
            title = "کندل درونی",
            summary = "کندلی که کاملاً داخل کندل قبلی خودش جا شده است.",
            teaches = "دسترسی به کندل قبل با [1]",
            source = """
                // [1] یعنی «یک کندل قبل». روی اولین کندل نمودار مقداری وجود ندارد، و زبان
                // به‌جای صفر گذاشتن، آن کندل را بی‌مقدار می‌گذارد.
                inside = high < high[1] and low > low[1]
                marker(inside, title = "کندل درونی", style = "circle", color = color.gold)

                narrow = (high - low) < (high[1] - low[1]) * 0.5
                marker(inside and narrow, title = "درونی و تنگ", style = "circle", color = color.orange)
            """.trimIndent(),
        ),
        ScriptPreset(
            id = "session-range",
            title = "دامنهٔ روز",
            summary = "سقف و کف بیست‌وچهار ساعت گذشته، به‌صورت دو خط.",
            teaches = "ta.highest و ta.lowest برای ساختن یک ناحیه",
            source = """
                // روی تایم‌فریم یک‌ساعته، بیست‌وچهار کندل یعنی یک شبانه‌روز. اگر تایم‌فریم را
                // عوض کنید، این عدد را هم عوض کنید — زبان تایم‌فریم را نمی‌داند.
                bars = input(24, title = "تعداد کندل", min = 2, max = 500)

                roof = ta.highest(high, bars)
                floor = ta.lowest(low, bars)
                middle = (roof + floor) / 2

                plot(roof, title = "سقف دوره", color = color.sell)
                plot(middle, title = "میانه", color = color.grey, dashed = true)
                plot(floor, title = "کف دوره", color = color.buy)

                log("دامنهٔ فعلی رسم شد")
            """.trimIndent(),
        ),
    )

    fun byId(id: String): ScriptPreset? = ALL.firstOrNull { it.id == id }
}
