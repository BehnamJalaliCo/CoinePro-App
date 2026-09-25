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
     * The prompt's own version, bumped whenever a change here could change an assistant's answer.
     *
     * That is the language's surface *and* the prompt's own shape — 2 is the split into a Persian
     * ask and an English specification, which changes what comes back even though the language did
     * not move. Not the app's version: the app ships many times without either changing, and a
     * number that was new every fortnight would teach a reader that it means nothing.
     */
    const val VERSION: String = "2"

    /** How many functions of each group the prompt lists before it says «and more». */
    private const val PER_GROUP = 6

    /**
     * Where the English specification starts inside a prompt.
     *
     * Public because the screen draws the two halves differently — the ask as prose in the reader's
     * own direction, the specification as a code block that does not wrap — and it must split on
     * the same marker the prompt is built with rather than on a guess about where English begins.
     */
    const val SPEC_MARK: String = "--- NamaScript specification"

    /**
     * A prompt split into the half the reader reads and the half the model reads.
     *
     * Second is null for anything that is not one of these prompts, so a caller handed arbitrary
     * text draws it as prose rather than as a block of code it is not.
     */
    fun parts(prompt: String): Pair<String, String?> {
        val at = prompt.indexOf(SPEC_MARK)
        return if (at < 0) prompt to null else prompt.substring(0, at).trimEnd() to prompt.substring(at)
    }

    /**
     * The whole prompt, ready to be copied into an assistant.
     *
     * [symbol] and [timeframe] are the reader's own chart, because an assistant that knows it is
     * writing for gold on the hourly writes different lengths than one writing for nothing in
     * particular — and because the reader should not have to say it twice.
     */
    fun prompt(symbol: String, timeframe: String, english: Boolean = false): String =
        if (english) promptEn(symbol, timeframe) else promptFa(symbol, timeframe)

    /**
     * The Persian prompt: a Persian ask, and the language's specification **in English**.
     *
     * ### Why the spec is English even in the Persian app
     *
     * Because the reader is not the one reading it. Every assistant a Persian reader reaches —
     * ChatGPT, Claude, the rest — was trained overwhelmingly on English technical text, and a
     * specification written in Persian is a specification the model half-understands: it returns
     * Pine with Persian titles, or invents a third language out of the two. The same six hundred
     * characters in English come back as code that runs.
     *
     * So the two halves are split by who reads them. The reader reads the top — what this prompt is,
     * what to paste back — in their own language. The model reads the specification, and it reads it
     * in the language the model is best at. The owner's review of 4.85.0 asked for exactly this
     * split, and it is the one part of the app where English is not the doorway but the tool.
     */
    private fun promptFa(symbol: String, timeframe: String): String = buildString {
        appendLine("یک اندیکاتور به زبان «نمااسکریپت» برای من بنویس. فقط کد بده، بدون توضیح.")
        appendLine("مشخصات زبان در بخش انگلیسی زیر آمده است؛ دقیقاً از همان پیروی کن.")
        appendLine("جواب را که گرفتی، در همان صفحه‌ی «پرسیدن از دستیار» دکمه‌ی «چسباندن جواب» را بزن.")
        appendLine()
        append(spec(symbol, timeframe))
        appendLine()
        appendLine("(نسخه‌ی راهنما: $VERSION)")
    }

    private fun promptEn(symbol: String, timeframe: String): String = buildString {
        appendLine("Write an indicator in NamaScript for me. Code only, no commentary.")
        appendLine("The language's specification is below; follow it exactly.")
        appendLine()
        append(spec(symbol, timeframe))
        appendLine()
        appendLine("(kit version: $VERSION)")
    }

    /**
     * The specification both prompts carry, in English, generated from the reference.
     *
     * One function and not two, because the thing being described is one language. A Persian copy
     * and an English copy would be two descriptions free to disagree, and the one nobody reads is
     * the one that would drift — which is the same argument that made this generated from
     * `ScriptReference` rather than written out beside it.
     */
    private fun spec(symbol: String, timeframe: String): String = buildString {
        appendLine("--- NamaScript specification (read this in English) ---")
        appendLine()
        appendLine("NamaScript is the Pro Chart app's indicator language. It is not Pine; it looks like it and differs:")
        appendLine("- No header. No //@version, no indicator(...), no strategy(...). Start with the code.")
        appendLine("- The logical operators are and, or, not − not &&, ||, !.")
        appendLine("- The previous bar is close[1], not close(1).")
        appendLine("- One statement per line. No if, for or while and no function definitions; use iff(condition, a, b) in place of if.")
        appendLine("- \u201Cvar x = \u2026\u201D and \u201Cx := \u2026\u201D exist but are almost never needed: every series is computed over all the bars at once.")
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
        appendLine("- plot(series, title = \"name\", color = color.gold) − a line. Where it goes is worked out for you; pane = \"own\" forces its own panel, pane = \"price\" the candles.")
        appendLine("- hline(60, title = \"ceiling\") − a fixed horizontal line")
        appendLine("- marker(condition, style = \"up\", title = \"name\") − a mark on every bar the condition holds; style is up, down or circle")
        appendLine("- input(20, title = \"length\", min = 2, max = 200) − a number the reader can change")
        appendLine("- The colours: " + ScriptReference.COLOUR_NAMES.joinToString(", "))
        appendLine()
        appendLine("And the important one:")
        appendLine("- signal(condition, text = \"what happened\") − this is what turns a script into a *signal*.")
        appendLine("  The app measures that condition's historical win rate and shows the reader your sentence.")
        appendLine("  Write at least one; write a second for the other direction.")
        appendLine("  Titles and signal text may be written in Persian − the reader reads them.")
        appendLine()
        appendLine("Three examples:")
        appendLine()
        for (example in EXAMPLES) {
            appendLine("// ${example.titleEn}")
            appendLine(example.source.trimIndent())
            appendLine()
        }
        appendLine("The reader's chart: $symbol on the $timeframe timeframe. Choose lengths that suit it.")
        appendLine()
        appendLine("--- end of specification ---")
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
