package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries

/**
 * NamaScript, from the outside.
 *
 * One entry point, and it never throws. A script is written by the reader, and the reader is
 * entitled to a message rather than a crash — so every failure, from a stray bracket to a division
 * that produced no finite value anywhere, comes back as [ScriptResult.error] with a line and a
 * column for the editor to put a caret at.
 */
object NamaScript {

    /** Longer than this is refused before it is even tokenised. */
    const val MAX_SOURCE_LENGTH = 20_000

    /**
     * Compiles and runs [source] over [series].
     *
     * @param overrides values a reader set for `input(...)` declarations, keyed by their title.
     *  A stored value from an older revision of the script that no longer declares that input is
     *  ignored rather than being an error — a renamed input should not stop a script running.
     */
    fun run(
        source: String,
        series: CandleSeries,
        overrides: Map<String, Double> = emptyMap(),
    ): ScriptResult = try {
        if (source.length > MAX_SOURCE_LENGTH) {
            throw ScriptError("اسکریپت از حد مجاز بلندتر است")
        }
        if (series.bars.isEmpty()) {
            throw ScriptError("برای اجرای اسکریپت، نمودار باید کندل داشته باشد")
        }
        Interpreter(series, overrides).run(Parser(Lexer(source).scan()).parse())
    } catch (error: ScriptError) {
        ScriptResult(error = ScriptFailure(error.bare, error.line, error.column))
    } catch (error: StackOverflowError) {
        // A deeply nested expression can exhaust the stack before the node budget notices. Caught
        // by name rather than as Throwable, so a genuine bug in this package still surfaces as one.
        ScriptResult(error = ScriptFailure("اسکریپت بیش از حد تودرتو است", 0, 0))
    }

    /**
     * Checks a script without running it.
     *
     * For the editor, which wants to underline a syntax error as it is typed and cannot afford to
     * evaluate a whole series on every keystroke.
     */
    fun check(source: String): ScriptFailure? = try {
        if (source.length > MAX_SOURCE_LENGTH) {
            ScriptFailure("اسکریپت از حد مجاز بلندتر است", 0, 0)
        } else {
            Parser(Lexer(source).scan()).parse()
            null
        }
    } catch (error: ScriptError) {
        ScriptFailure(error.bare, error.line, error.column)
    }
}
