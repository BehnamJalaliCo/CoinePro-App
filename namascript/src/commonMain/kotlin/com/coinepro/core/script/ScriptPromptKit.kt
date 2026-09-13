package com.coinepro.core.script

/**
 * **«یک اسکریپت از هوش مصنوعی بگیر»** — the prompt a reader hands to an assistant (run Σ, S3 B).
 *
 * ### Why this is a feature and not a help page
 *
 * Because a reader is going to do it anyway, and what they will paste into the assistant is «write
 * me a NamaScript indicator» — which produces Pine, or a language the model invented, roughly every
 * time. The difference between that and something that runs on the first try is about six hundred
 * characters of context: the types, the function names, the plot family, and three examples. Nobody
 * is going to type those; the app has them, so the app hands them over.
 *
 * The prompt is **generated from the reference the interpreter itself documents** — not copied
 * beside it. A hand-written prompt would be a third place the language is described, and the third
 * place is always the one that goes stale. `ScriptPromptKitTest` holds that every name the prompt
 * teaches is a name `Builtins.call` answers.
 *
 * ### Why it is versioned
 *
 * Because the reader may paste the result back a week later, and because an assistant's answer is
 * only as good as the version of the language it was told about. [VERSION] rides in the prompt's
 * own text, so a script that came back wrong can be traced to the prompt that asked for it.
 */
object ScriptPromptKit {

    /**
     * The prompt's own version, bumped whenever the language's surface changes.
     *
     * Not the app's version: the app ships many times without the language moving, and a prompt
     * that claimed to be new every fortnight would teach a reader that the number means nothing.
     */
    const val VERSION: String = "1"

    /** How many functions of each group the prompt lists before it says «and more». */
    private const val PER_GROUP = 6

    /**
     * The whole prompt, ready to be copied into an assistant.
     *
     * [symbol] and [timeframe] are the reader's own chart, because an assistant that knows it is
     * writing for gold on the hourly writes different lengths than one writing for nothing in
     * particular — and because the reader should not have to say it twice.
     */
    fun prompt(symbol: String, timeframe: String, english: Boolean = false): String =
        if (english) promptEn(symbol, timeframe) else promptFa(symbol, timeframe)

    private fun promptFa(symbol: String, timeframe: String): String = buildString {
        appendLine("یک اندیکاتور به زبان «نمااسکریپت» بنویس. فقط کد بده، بدون توضیح.")
        appendLine()
        appendLine("نمااسکریپت زبان اندیکاتورنویسی اپلیکیشن پرو چارت است. پاین (Pine) نیست؛ شبیه آن است و فرق‌هایی دارد:")
        appendLine("- سربرگ ندارد. نه //@version، نه indicator(...)، نه strategy(...). مستقیم کد را شروع کن.")
        appendLine("- عملگرهای منطقی and و or و not هستند، نه && و || و !.")
        appendLine("- کندل قبلی close[1] است، نه close(1).")
        appendLine("- هر خط یک دستور. if و for و while و تعریف تابع نداریم؛ به جای if از iff(شرط، الف، ب) استفاده کن.")
        appendLine("- «var x = …» و «x := …» هستند، اما تقریباً هیچ‌وقت لازم نمی‌شوند: هر سری یک‌جا روی همه‌ی کندل‌ها حساب می‌شود.")
        appendLine("- ta.rma وجود ندارد؛ نامش ta.smma است.")
        appendLine()
        appendLine("چه چیزی در دسترس است:")
        appendLine("- سری‌ها: " + ScriptReference.SERIES.joinToString(", ") { it.signature.substringBefore("(") })
        for (group in ScriptReference.GROUPS) {
            val names = group.functions.take(PER_GROUP).joinToString("، ") { ScriptReferenceEn.nameOf(it.signature) }
            val more = if (group.functions.size > PER_GROUP) " و چند تای دیگر" else ""
            appendLine("- ${group.title}: $names$more")
        }
        appendLine()
        appendLine("چه چیزی می‌کشد:")
        appendLine("- plot(series, title = \"نام\", color = color.gold) — یک خط. جایش خودکار تعیین می‌شود؛ pane = \"own\" پنل جدا و pane = \"price\" روی قیمت.")
        appendLine("- hline(60, title = \"سقف\") — یک خط افقی ثابت")
        appendLine("- marker(condition, style = \"up\", title = \"نام\") — نشانه روی کندل‌هایی که شرط درست است؛ style یکی از up، down یا circle")
        appendLine("- input(20, title = \"دوره\", min = 2, max = 200) — عددی که کاربر می‌تواند عوض کند")
        appendLine("- رنگ‌ها: " + ScriptReference.COLOUR_NAMES.joinToString("، "))
        appendLine()
        appendLine("و مهم‌تر از همه:")
        appendLine("- signal(condition, text = \"چه اتفاقی افتاد\") — این چیزی است که اسکریپت را به یک «سیگنال» تبدیل می‌کند.")
        appendLine("  اپلیکیشن روی همین شرط نرخ برد تاریخی حساب می‌کند و همان جمله را به کاربر نشان می‌دهد.")
        appendLine("  دست‌کم یک signal بنویس؛ برای جهت مخالف یکی دیگر.")
        appendLine()
        appendLine("سه نمونه:")
        appendLine()
        for (example in EXAMPLES) {
            appendLine("// ${example.title}")
            appendLine(example.source.trimIndent())
            appendLine()
        }
        appendLine("چارت کاربر: $symbol روی تایم‌فریم $timeframe.")
        appendLine()
        appendLine("حالا این را بنویس: ")
        appendLine()
        appendLine("(نسخه‌ی راهنما: $VERSION)")
    }

    private fun promptEn(symbol: String, timeframe: String): String = buildString {
        appendLine("Write an indicator in NamaScript. Code only, no commentary.")
        appendLine()
        appendLine("NamaScript is the Pro Chart app's indicator language. It is not Pine; it looks like it and differs:")
        appendLine("- No header. No //@version, no indicator(...), no strategy(...). Start with the code.")
        appendLine("- The logical operators are and, or, not — not &&, ||, !.")
        appendLine("- The previous bar is close[1], not close(1).")
        appendLine("- One statement per line. No if, for or while and no function definitions; use iff(condition, a, b) in place of if.")
        appendLine("- “var x = …” and “x := …” exist but are almost never needed: every series is computed over all the bars at once.")
        appendLine("- There is no ta.rma; it is called ta.smma.")
        appendLine()
        appendLine("What is available:")
        appendLine("- Series: " + ScriptReference.SERIES.joinToString(", ") { it.signature.substringBefore("(") })
        for (group in ScriptReferenceEn.GROUPS) {
            val names = group.functions.take(PER_GROUP).joinToString(", ") { ScriptReferenceEn.nameOf(it.signature) }
            val more = if (group.functions.size > PER_GROUP) " and a few more" else ""
            appendLine("- ${group.title}: $names$more")
        }
        appendLine()
        appendLine("What draws:")
        appendLine("- plot(series, title = \"name\", color = color.gold) — a line. Where it goes is worked out for you; pane = \"own\" forces its own panel, pane = \"price\" the candles.")
        appendLine("- hline(60, title = \"ceiling\") — a fixed horizontal line")
        appendLine("- marker(condition, style = \"up\", title = \"name\") — a mark on every bar the condition holds; style is up, down or circle")
        appendLine("- input(20, title = \"length\", min = 2, max = 200) — a number the reader can change")
        appendLine("- The colours: " + ScriptReference.COLOUR_NAMES.joinToString(", "))
        appendLine()
        appendLine("And the important one:")
        appendLine("- signal(condition, text = \"what happened\") — this is what turns a script into a *signal*.")
        appendLine("  The app measures that condition's historical win rate and shows the reader your sentence.")
        appendLine("  Write at least one; write a second for the other direction.")
        appendLine()
        appendLine("Three examples:")
        appendLine()
        for (example in EXAMPLES) {
            appendLine("// ${example.titleEn}")
            appendLine(example.source.trimIndent())
            appendLine()
        }
        appendLine("The reader's chart: $symbol on the $timeframe timeframe.")
        appendLine()
        appendLine("Now write: ")
        appendLine()
        appendLine("(kit version: $VERSION)")
    }

    /**
     * The three examples the prompt carries.
     *
     * Three, and these three, because between them they show every shape an assistant needs: a line
     * on the price, a study in its own panel with levels, and a verdict with a sentence. A fourth
     * would be a fourth thing to get subtly wrong; two would leave `signal(...)` looking optional.
     *
     * Each one is a real script and `ScriptPromptKitTest` compiles all three, because an example
     * that does not run is worse than no example: it teaches the mistake.
     */
    data class Example(val title: String, val titleEn: String, val source: String)

    val EXAMPLES: List<Example> = listOf(
        Example(
            title = "یک میانگین روی قیمت",
            titleEn = "One average on the price",
            source = """
                length = input(20, title = "دوره", min = 2, max = 200)
                average = ta.ema(close, length)
                plot(average, title = "میانگین", color = color.gold)
            """,
        ),
        Example(
            title = "یک نوسان‌گر در پنل خودش، با سطح",
            titleEn = "An oscillator in its own panel, with levels",
            source = """
                r = ta.rsi(close, 14)
                plot(r, title = "RSI", pane = "own")
                hline(30, title = "کف")
                hline(70, title = "سقف")
            """,
        ),
        Example(
            title = "یک سیگنال با جمله‌ی خودش",
            titleEn = "A signal with its own sentence",
            source = """
                fast = ta.ema(close, 9)
                slow = ta.ema(close, 21)
                plot(fast, title = "تند")
                plot(slow, title = "کند")
                signal(ta.crossover(fast, slow), text = "میانگین تند از کند رد شد")
                signal(ta.crossunder(fast, slow), text = "میانگین تند زیر کند رفت")
            """,
        ),
    )
}
