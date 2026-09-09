package com.coinepro.core.script

/**
 * Best-effort translation of a pasted Pine Script (v5) into NamaScript.
 *
 * Not a compiler for Pine: the two languages have different execution models (Pine runs bar by
 * bar with state, NamaScript vectorises — `docs/namascript/SPEC.md` §10), and a faithful Pine
 * front end is v2's work. This is the paste helper the plan asks for: a reader who brings a
 * script from TradingView gets the lines that have a direct equivalent rewritten, the header
 * dropped, and a **list of what could not be carried over**, each with the line it was on, so
 * that what remains to be done by hand is named rather than discovered.
 *
 * What it maps (`RULES`, in order): the `//@version` header and the `indicator(...)` /
 * `strategy(...)` declaration (dropped); `input.int/float/bool(default, "title")` → NamaScript's
 * `input(...)` forms; `ta.rma` → `ta.smma`; `ta.stoch` → `ta.stoch_k`; `ta.highest/lowest` with
 * the argument order kept; `plotshape(cond, style=shape.x)` → `marker(cond, style="…")`;
 * `plot(x, "title", color=…)` → `plot(x, title="title", color=…)`; `color.new` as is;
 * `strategy.entry("id", strategy.long)` → a note; `var` and `:=` at statement start → `=`;
 * `na` → `nz(...)` where it can; `if`, `for`, `while`, `switch`, function definitions, arrays,
 * `request.security` and drawing objects → reported, the line kept as a comment.
 */
object PineTranslator {

    /** One thing the translator could not carry over, with the source line it was on. */
    data class Unsupported(val line: Int, val what: String, val source: String)

    data class Translation(
        val source: String,
        /** In source order. Empty means every line was mapped. */
        val unsupported: List<Unsupported>,
    ) {
        val complete: Boolean get() = unsupported.isEmpty()
    }

    fun translate(pine: String): Translation {
        val out = StringBuilder()
        val unsupported = mutableListOf<Unsupported>()
        pine.lines().forEachIndexed { index, raw ->
            val number = index + 1
            val line = raw.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> out.appendLine()
                trimmed.startsWith("//@version") -> out.appendLine("//@version=1 // was ${trimmed.removePrefix("//")}")
                trimmed.startsWith("//") -> out.appendLine(line)
                DECLARATION.containsMatchIn(trimmed) -> out.appendLine("// ${trimmed}")
                else -> {
                    val blocked = BLOCKED.firstOrNull { it.first.containsMatchIn(trimmed) }
                    if (blocked != null) {
                        unsupported += Unsupported(number, blocked.second, trimmed)
                        out.appendLine("// [نمااسکریپت: ${blocked.second}] $trimmed")
                    } else {
                        var text = trimmed
                        for ((pattern, replacement) in RULES) text = pattern.replace(text, replacement)
                        // A removed trailing argument leaves its comma behind.
                        text = TRAILING_COMMA.replace(text, ")")
                        val leftover = LEFTOVER.find(text)
                        if (leftover != null) {
                            unsupported += Unsupported(number, "function not in NamaScript: ${leftover.value}", trimmed)
                            out.appendLine("// [نمااسکریپت: ${leftover.value}] $text")
                        } else {
                            out.appendLine(text)
                        }
                    }
                }
            }
        }
        return Translation(out.toString().trimEnd() + "\n", unsupported)
    }

    private val DECLARATION = Regex("""^(indicator|strategy|library)\s*\(""")
    private val TRAILING_COMMA = Regex(""",\s*\)""")

    /** Constructs with no equivalent in the vectorised language, each named for the report. */
    private val BLOCKED: List<Pair<Regex, String>> = listOf(
        Regex("""^(if|else|for|while|switch)\b""") to "control flow is not in NamaScript v1.1 — rewrite as a condition or iff(...)",
        Regex("""^\w+\s*\([^)]*\)\s*=>""") to "user functions are not in NamaScript v1.1",
        Regex("""\b(array|matrix|map)\.\w+""") to "collections are not in NamaScript v1.1",
        Regex("""\brequest\.\w+""") to "request.* (other symbols and timeframes) is not in NamaScript v1.1",
        Regex("""\b(label|line|box|table|polyline|linefill)\.\w+""") to "drawing objects are not in NamaScript v1.1",
        Regex("""\bstrategy\.(entry|exit|close|order|close_all)\b""") to "strategy orders — use signal(buy, entry, stop, target) for one setup",
        Regex("""\b(fill|plotcandle|plotbar|barcolor|plotarrow)\s*\(""") to "this plot type is not in NamaScript v1.1",
        Regex("""\bvarip\b""") to "varip (per-tick state) has no meaning in a vectorised model",
        Regex("""\b(str|syminfo|timeframe|barstate|session)\.\w+""") to "this namespace is not in NamaScript v1.1",
    )

    /** Line rewrites, applied in order. */
    private val RULES: List<Pair<Regex, String>> = listOf(
        // `var x = …` → `x = …`; `x := …` at the start of a line → `x = …` (no per-bar state).
        Regex("""^var\s+(?:(?:float|int|bool|color|string)\s+)?(\w+)\s*=""") to "$1 =",
        Regex("""^(\w+)\s*:=""") to "$1 =",
        // Typed declarations: `float x = …`, `int n = …`, `bool b = …`.
        Regex("""^(float|int|bool|color|string)\s+(\w+)\s*=""") to "$2 =",
        // Inputs: input.int(14, "Length", minval=1, maxval=100) → input.int(14, title="Length", min=1, max=100).
        Regex("""input\.(int|float|bool)\(\s*([^,()]+?)\s*,\s*("[^"]*"|'[^']*')""") to "input.$1($2, title=$3",
        Regex("""input\(\s*([^,()]+?)\s*,\s*("[^"]*"|'[^']*')""") to "input($1, title=$2",
        Regex("""\bminval\s*=""") to "min=",
        Regex("""\bmaxval\s*=""") to "max=",
        Regex("""\binput\.source\([^)]*\)""") to "close",
        Regex("""\binput\.(string|timeframe|symbol|session|time|price)\s*\(""") to "input(",
        // ta.* spellings that differ.
        Regex("""\bta\.rma\(""") to "ta.smma(",
        Regex("""\bta\.stoch\(""") to "ta.stoch_k(",
        Regex("""\bta\.wpr\(""") to "ta.williams_r(",
        Regex("""\bta\.mom\(""") to "ta.momentum(",
        Regex("""\bta\.sar\(""") to "ta.psar(",
        Regex("""\bta\.dev\(""") to "ta.stdev(",
        Regex("""\bta\.bbw\(""") to "ta.bb_width(",
        Regex("""\bta\.tr\b(?!\()""") to "ta.tr()",
        // plotshape(cond, style=shape.triangleup, color=…) → marker(cond, style="triangleup", color=…).
        Regex("""\bplotshape\(""") to "marker(",
        Regex("""\bplotchar\(""") to "marker(",
        Regex("""style\s*=\s*shape\.(\w+)""") to "style=\"$1\"",
        Regex("""location\s*=\s*location\.\w+\s*,?\s*""") to "",
        Regex("""\bsize\s*=\s*size\.\w+\s*,?\s*""") to "",
        // plot(x, "title", color=…) → plot(x, title="title", color=…).
        Regex("""\bplot\(([^,()]+(?:\([^()]*\))?[^,()]*)\s*,\s*("[^"]*"|'[^']*')""") to "plot($1, title=$2",
        Regex("""\bhline\(([^,()]+)\s*,\s*("[^"]*"|'[^']*')""") to "hline($1, title=$2",
        Regex("""\blinewidth\s*=""") to "width=",
        Regex("""\bplot\.style_\w+\s*,?\s*""") to "",
        // Colours Pine names that NamaScript spells differently.
        Regex("""\bcolor\.(lime|green)\b""") to "color.green",
        Regex("""\bcolor\.(maroon|red|fuchsia)\b""") to "color.red",
        Regex("""\bcolor\.(yellow|olive)\b""") to "color.gold",
        Regex("""\bcolor\.(aqua|navy|blue)\b""") to "color.blue",
        Regex("""\bcolor\.(gray|black)\b""") to "color.grey",
        Regex("""\bcolor\.rgb\([^)]*\)""") to "color.gold",
        // `na(x) ? 0 : x` and `nz(x)` → `nz(x, 0)`.
        Regex("""\bnz\((\w+)\)""") to "nz($1, 0)",
        Regex("""\bna\((\w+)\)\s*\?\s*([^:]+?)\s*:\s*\1\b""") to "nz($1, $2)",
        // Pine's crossover family is the same; `math.` the same; booleans the same.
    )

    /** A call the language does not have, after the rules ran: reported rather than pasted. */
    private val LEFTOVER = Regex("""\b(shape|location|size|alert|strategy|barstate|syminfo|timeframe|request|str|array|matrix|map|label|line|box|table)\.\w+""")
}
