package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reference documentation is generated from the table the app shows, in both languages, and
 * committed; this test renders it again and fails when the committed copy is stale. Run with
 * `-Dnamascript.reference.write=true` to rewrite `docs/namascript/reference/{fa,en}.md`.
 *
 * Also the test that every Persian entry has an English line, and that every function the
 * interpreter answers to is in the reference — a built-in nobody documented is a built-in nobody
 * can find.
 */
class ReferenceDocsTest {

    private val writing = System.getProperty("namascript.reference.write") == "true"

    @Test
    fun `every Persian entry has an English line`() {
        val missing = (ScriptReference.SERIES + ScriptReference.ALL_GROUPS.flatMap { it.functions })
            .filter { ScriptReferenceEn.summaryFor(it) == null }
            .map { it.signature }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every built-in the interpreter answers to is in the reference`() {
        val source = File("src/commonMain/kotlin/com/coinepro/core/script/Builtins.kt").readText()
        val bound = Regex("""^\s+"([a-z_.]+)"(?:, "([a-z_.]+)")? ->""", RegexOption.MULTILINE)
            .findAll(source).flatMap { listOfNotNull(it.groupValues[1], it.groupValues[2].takeIf(String::isNotEmpty)) }.toSet()
        val documented = ScriptReference.ALL_GROUPS.flatMap { it.functions }.map { ScriptReferenceEn.nameOf(it.signature) }.toSet() +
            ScriptReference.SERIES.map { ScriptReferenceEn.nameOf(it.signature) }.toSet()
        val undocumented = (bound - documented - UNDOCUMENTED_ON_PURPOSE).sorted()
        assertEquals("built-ins with no reference entry", emptyList<String>(), undocumented)
    }

    @Test
    fun `the committed reference is what the table renders`() {
        val root = File("../docs/namascript/reference")
        root.mkdirs()
        val fa = File(root, "fa.md")
        val en = File(root, "en.md")
        if (writing) {
            fa.writeText(render(english = false))
            en.writeText(render(english = true))
        }
        assertTrue("run with -Dnamascript.reference.write=true to write ${fa.path}", fa.exists() && en.exists())
        assertEquals("docs/namascript/reference/fa.md is stale; rewrite it with -Dnamascript.reference.write=true", render(english = false), fa.readText())
        assertEquals("docs/namascript/reference/en.md is stale; rewrite it with -Dnamascript.reference.write=true", render(english = true), en.readText())
    }

    private fun render(english: Boolean): String = buildString {
        if (english) {
            appendLine("# NamaScript reference")
            appendLine()
            appendLine("Generated from `ScriptReference` and `ScriptReferenceEn` by `ReferenceDocsTest`; do not edit by hand. The language itself is described in `../SPEC.md`.")
        } else {
            appendLine("# مرجع نمااسکریپت")
            appendLine()
            appendLine("از جدول `ScriptReference` توسط `ReferenceDocsTest` ساخته می‌شود؛ دستی ویرایش نکنید. خودِ زبان در `../SPEC.md` شرح داده شده است.")
        }
        appendLine()
        appendLine(if (english) "## Built-in series" else "## سری‌های آماده")
        appendLine()
        table(this, ScriptReference.SERIES, english)
        for (group in ScriptReference.ALL_GROUPS) {
            appendLine()
            appendLine("## ${if (english) englishTitle(group.title) else group.title}")
            appendLine()
            table(this, group.functions, english)
        }
        appendLine()
        appendLine(if (english) "## Colours" else "## رنگ‌ها")
        appendLine()
        appendLine(ScriptReference.COLOUR_NAMES.joinToString(", ") { "`$it`" })
    }

    private fun table(out: StringBuilder, functions: List<ScriptFunction>, english: Boolean) {
        out.appendLine(if (english) "| Call | What it gives | Returns |" else "| فراخوانی | چه می‌دهد | خروجی |")
        out.appendLine("| --- | --- | --- |")
        for (function in functions) {
            val summary = if (english) ScriptReferenceEn.summaryFor(function) ?: "" else function.summary
            val returns = if (english) ScriptReferenceEn.RETURNS[function.returns] ?: function.returns else function.returns
            out.appendLine("| `${function.signature.replace("|", "\\|")}` | ${summary.replace("|", "\\|")} | $returns |")
        }
    }

    private fun englishTitle(persian: String): String = GROUP_TITLES[persian] ?: persian

    private companion object {
        val GROUP_TITLES = mapOf(
            "میانگین‌ها" to "Averages",
            "نوسان‌نماها" to "Oscillators",
            "باندها و کانال‌ها" to "Bands and channels",
            "روند" to "Trend",
            "حجم" to "Volume",
            "پنجره‌ها و منطق" to "Windows and logic",
            "ریاضی" to "Math",
            "کمکی" to "Helpers",
            "ورودی و خروجی" to "Input and output",
            "میانگین‌ها و روند (۴٫۵۰)" to "Averages and trend (4.50)",
            "نوسان‌نماها (۴٫۵۰)" to "Oscillators (4.50)",
            "حجم (۴٫۵۰)" to "Volume (4.50)",
            "ریاضی (۴٫۵۰)" to "Math (4.50)",
            "ورودی، خروجی، هشدار (۴٫۵۰)" to "Input, output, alerts (4.50)",
        )

        /** Bound names that are aliases of a documented one, or internal. */
        val UNDOCUMENTED_ON_PURPOSE = setOf("up", "down")
    }
}
