package com.coinepro.core.script

/**
 * **Bring your own script** — what happens to whatever a reader pastes in (run Σ, S3 A).
 *
 * ### The premise
 *
 * People are going to ask an assistant for an indicator and paste the answer in. That is not a
 * fringe case to be tolerated; it is, from this version on, the *main* way a script arrives. Which
 * means the paste box has to deal with three genuinely different things, and the reader must not be
 * asked which one they have:
 *
 * * **NamaScript**, correct or nearly so. An assistant that was given the language's own reference
 *   writes this, and what comes back usually has one or two errors of a small, repeated kind.
 * * **Pine**, because that is what an assistant asked for «a TradingView indicator» writes, and
 *   because the reader may simply have copied one from somewhere.
 * * **Prose** — «buy when EMA 20 crosses EMA 50» — which is the reader telling the app what they
 *   want in the only language they are sure of.
 *
 * ### Why the fixes are a table and not a smarter compiler
 *
 * Because the mistakes are not random. An assistant writing NamaScript gets the same handful of
 * things wrong over and over: it writes Pine's `indicator(...)` header, it writes `&&` for `and`,
 * it calls `ta.rma`, it writes `close(1)` for `close[1]`. Every one of those is a textual
 * substitution that is *always* right, and a table of them is something a reader can see, approve
 * and undo — which a compiler that silently accepted both spellings would not be.
 *
 * The inverse mistake is the expensive one, and this table has made it: `var`, `varip`, `:=`,
 * `plotshape` and `hline` all *look* like Pine and are all this language's own, so a rule
 * «translating» them rewrote scripts that already ran. `ScriptPasteTest` runs the whole conformance
 * suite through [repair] and requires every file to come out byte-identical, which is what caught
 * them; nothing goes in this table until that test has been run against it.
 *
 * A rule earns its place here only if applying it can never change a correct script's meaning. That
 * is the whole admission test, and it is why there is no rule for «wrong argument order» or «missing
 * length»: those need judgement, and judgement belongs to the diagnostic, not to a one-tap button.
 *
 * Pure Kotlin. The paste sheet, the studio and the indicator sheet all call this; none of them
 * decides anything.
 */
object ScriptPaste {

    /** What a pasted block of text appears to be. */
    enum class Dialect {
        /** NamaScript, whether or not it compiles. */
        NAMA,

        /** Pine v5, or close enough that [PineTranslator] is the right next step. */
        PINE,

        /** A sentence. There is no code here to fix; there is an intention to match a template to. */
        PROSE,
    }

    /**
     * One textual repair, with the reason a reader sees before they accept it.
     *
     * [code] is the diagnostic this usually shows up as, or `""` where the mistake is caught before
     * the compiler gets a chance — a Pine header is not a NamaScript error, it is a line that should
     * not be there. The pairing lets the editor put the fix *under the error*, which is where
     * somebody looking at a red line is already reading.
     */
    data class Fix(
        val id: String,
        val code: String,
        /** What it does, one line, in the reader's language. */
        val what: String,
        val whatEn: String,
        /** The line numbers it touched, one-based, for the diff. */
        val lines: List<Int>,
    )

    /** A pasted script, read and repaired as far as it can be without judgement. */
    data class Paste(
        val dialect: Dialect,
        /** The text after every applicable fix. Equal to the input when nothing applied. */
        val fixed: String,
        /** What was changed, in the order it was applied. Empty means the paste was already clean. */
        val fixes: List<Fix>,
        /** What Pine carried that has no equivalent. Empty for a NamaScript paste. */
        val unsupported: List<PineTranslator.Unsupported> = emptyList(),
        /** The templates that match a prose paste, best first. Empty for code. */
        val templates: List<ScriptTemplate> = emptyList(),
    ) {
        /** Whether anything at all happened to the text. */
        val changed: Boolean get() = fixes.isNotEmpty() || unsupported.isNotEmpty()
    }

    /**
     * What [text] is.
     *
     * Pine first, and only on what is Pine's *alone* — `//@version=5`, a header call, `ta.rma`,
     * `shape.triangleup`, a typed `var float x`. Not on `var`, `:=`, `plotshape` or
     * `request.security`, which this language has too: sending one of those through the translator
     * would be translating a script that already ran. Prose last and by exclusion: a block with no
     * operator, no call and no assignment in it is not code in either language.
     */
    fun dialectOf(text: String): Dialect {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Dialect.PROSE
        if (PINE_MARKERS.any { it.containsMatchIn(trimmed) }) return Dialect.PINE
        val codeish = CODE_MARKERS.count { it.containsMatchIn(trimmed) }
        return if (codeish >= 1) Dialect.NAMA else Dialect.PROSE
    }

    /**
     * Reads [text], repairs what can be repaired without judgement, and says what is left.
     *
     * Nothing here runs the script. The reader presses «اجرا» when they are ready, and a paste that
     * quietly ran and drew would be a paste that quietly ran somebody else's code.
     */
    fun read(text: String): Paste = when (dialectOf(text)) {
        Dialect.PROSE -> Paste(
            dialect = Dialect.PROSE,
            fixed = text,
            fixes = emptyList(),
            templates = ScriptTemplates.matching(text),
        )
        Dialect.PINE -> {
            val translated = PineTranslator.translate(text)
            val repaired = repair(translated.source)
            Paste(
                dialect = Dialect.PINE,
                fixed = repaired.first,
                fixes = repaired.second,
                unsupported = translated.unsupported,
            )
        }
        Dialect.NAMA -> {
            val repaired = repair(text)
            Paste(dialect = Dialect.NAMA, fixed = repaired.first, fixes = repaired.second)
        }
    }

    /**
     * Applies every rule that matches, in table order, and reports what it did.
     *
     * In order and not to a fixed point: each rule is written so that applying it twice is the same
     * as applying it once, and a loop over a table somebody will extend is a loop somebody will
     * eventually make oscillate.
     */
    fun repair(source: String): Pair<String, List<Fix>> {
        var text = source
        val applied = mutableListOf<Fix>()
        for (rule in RULES) {
            val hit = rule.linesTouched(text)
            if (hit.isEmpty()) continue
            text = rule.apply(text)
            applied += Fix(rule.id, rule.code, rule.what, rule.whatEn, hit)
        }
        return text to applied
    }

    /** Every repair this build knows, for the reference sheet and for the test that walks them. */
    val FIXES: List<Rule> get() = RULES

    /**
     * One rule: what it matches, what it does about it, and why.
     *
     * [apply] takes and returns the whole source rather than a line, because two of the rules —
     * the missing version header and the missing `plot` — are about the script as a whole.
     */
    class Rule internal constructor(
        val id: String,
        val code: String,
        val what: String,
        val whatEn: String,
        private val matches: (String) -> List<Int>,
        private val rewrite: (String) -> String,
    ) {
        fun linesTouched(source: String): List<Int> = matches(source)
        fun apply(source: String): String = rewrite(source)
    }

    // ── the table ────────────────────────────────────────────────────────────────────────────
    //
    // Twenty rules, collected from what assistants actually produce when asked for a NamaScript
    // indicator. Each one is a substitution that cannot change a correct script's meaning, which is
    // the admission test; anything needing judgement stays a diagnostic with a hint.

    private fun lineRule(
        id: String,
        code: String,
        what: String,
        whatEn: String,
        pattern: Regex,
        /**
         * Whether a `//` comment is invisible to this rule.
         *
         * True for everything that is about code, false for the two rules whose whole subject is a
         * comment line: `//@version=5` is a header, not a remark.
         */
        blindToComments: Boolean = true,
        replacement: (MatchResult) -> String,
    ) = Rule(
        id = id,
        code = code,
        what = what,
        whatEn = whatEn,
        matches = { source ->
            source.lines().mapIndexedNotNull { index, line ->
                (index + 1).takeIf { pattern.containsMatchIn(masked(line, blindToComments)) }
            }
        },
        rewrite = { source ->
            source.lines().joinToString("\n") { line -> rewriteOutsideText(line, pattern, replacement, blindToComments) }
        },
    )

    /**
     * [line] with everything inside a string literal or after a `//` blanked out, length for length.
     *
     * Every rule below is a search-and-replace, and a search-and-replace that can see into a reader's
     * own text is a rule that will eventually corrupt it: «رقم‌های فارسی» would rewrite the numerals
     * in `text = "۳ کندل"`, and «&& becomes and» would rewrite a label that happens to say `a && b`.
     * The blanking keeps the offsets so a match found here points at the same place in the real line.
     *
     * A comment is blanked for the same reason: `// buy when close = open` is prose, not a condition.
     */
    internal fun masked(line: String, blindToComments: Boolean = true): String {
        if ('"' !in line && !(blindToComments && "//" in line)) return line
        val out = StringBuilder(line.length)
        var inText = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' -> {
                    inText = !inText
                    out.append(character)
                }
                inText -> out.append(MASK)
                blindToComments && character == '/' && index + 1 < line.length && line[index + 1] == '/' -> {
                    while (index < line.length) {
                        out.append(MASK)
                        index++
                    }
                    return out.toString()
                }
                else -> out.append(character)
            }
            index++
        }
        return out.toString()
    }

    /** The whole source, blanked line by line, for the rules that read more than one line. */
    private fun maskedSource(source: String): String = source.lines().joinToString("\n", transform = ::masked)

    /**
     * [pattern] replaced in [line], matched against the blanked view and written into the real text.
     *
     * The match is made again on the real characters before [replacement] sees it, because a rule
     * like «the second argument of plot is its title» has to *keep* the title it found, and the
     * blanked view no longer has it. Where that second match cannot be made — a pattern that only
     * holds with its surroundings — the blanked groups are used, which is safe: they only reach the
     * output for rules whose replacement ignores them.
     */
    private fun rewriteOutsideText(
        line: String,
        pattern: Regex,
        replacement: (MatchResult) -> String,
        blindToComments: Boolean,
    ): String {
        val view = masked(line, blindToComments)
        if (view === line) return pattern.replace(line) { replacement(it) }
        val allowed = pattern.findAll(view).map { it.range.first }.toSet()
        if (allowed.isEmpty()) return line
        // Matched on the blanked view, replaced on the real one. Blanking preserves length, so a
        // match on the real line that starts at an approved offset is the same match — and it is
        // the real one, which is what [replacement] needs: the rule that names plot's title has to
        // keep the title, and the blanked view no longer has it.
        val out = StringBuilder(line.length)
        var cursor = 0
        for (match in pattern.findAll(line)) {
            if (match.range.first < cursor || match.range.first !in allowed) continue
            out.append(line, cursor, match.range.first)
            out.append(replacement(match))
            cursor = match.range.last + 1
        }
        out.append(line, cursor, line.length)
        return out.toString()
    }

    private val RULES: List<Rule> = listOf(
        lineRule(
            id = "drop-indicator-header",
            code = "",
            what = "سربرگ indicator(...) پاین به توضیح تبدیل شد؛ نمااسکریپت سربرگ ندارد.",
            whatEn = "Pine's indicator(...) header became a comment; NamaScript has no header.",
            pattern = Regex("""^\s*(indicator|study|strategy)\s*\(.*\)\s*$"""),
            blindToComments = false,
            replacement = { "// ${it.value.trim()}" },
        ),
        lineRule(
            id = "input-bounds",
            code = "E208",
            what = "«minval» و «maxval» به «min» و «max» تبدیل شدند.",
            whatEn = "“minval” and “maxval” became “min” and “max”.",
            // Pine's names for input's bounds. This language reads min and max, and ignores an
            // argument it does not know — so the unfixed form silently loses the reader's limits
            // rather than failing, which is the worst of the three outcomes.
            pattern = Regex("""\b(min|max)val\s*="""),
            replacement = { "${it.groupValues[1]} =" },
        ),
        lineRule(
            id = "rma-to-smma",
            code = "E304",
            what = "ta.rma به ta.smma تغییر کرد — همان میانگین است با نام این زبان.",
            whatEn = "ta.rma became ta.smma — the same average under this language's name.",
            pattern = Regex("""\bta\.rma\b"""),
            replacement = { "ta.smma" },
        ),
        lineRule(
            id = "stoch-to-stoch-k",
            code = "E304",
            what = "ta.stoch به ta.stoch_k تغییر کرد.",
            whatEn = "ta.stoch became ta.stoch_k.",
            pattern = Regex("""\bta\.stoch\b(?!_)"""),
            replacement = { "ta.stoch_k" },
        ),
        lineRule(
            id = "cross-over",
            code = "E304",
            what = "ta.cross به ta.crossover تغییر کرد؛ برای جهت مخالف ta.crossunder هست.",
            whatEn = "ta.cross became ta.crossover; the other direction is ta.crossunder.",
            pattern = Regex("""\bta\.cross\b(?!over|under)"""),
            replacement = { "ta.crossover" },
        ),
        lineRule(
            id = "bare-ta-names",
            code = "E304",
            what = "نام‌های بدون پیشوند (sma، ema، rsi، …) با «ta.» نوشته شدند.",
            whatEn = "Bare names (sma, ema, rsi, …) were given their “ta.” prefix.",
            pattern = Regex("""(?<![\w.])(sma|ema|wma|rsi|atr|macd|stdev|highest|lowest|crossover|crossunder)\s*\("""),
            replacement = { "ta.${it.groupValues[1]}(" },
        ),
        lineRule(
            id = "na-comparison",
            code = "E203",
            what = "«== na» به na(...) تبدیل شد؛ مقایسه با غیبت هیچ‌وقت تصمیم نمی‌گیرد.",
            whatEn = "“== na” became na(...); a comparison with absence never decides.",
            // Pine's own idiom, and the one an assistant reaches for. It is not an error the compiler
            // can catch — `x == na` parses and runs, and is false on every bar including the ones the
            // reader wanted — so the rewrite is the only place it gets caught.
            pattern = Regex("""([A-Za-z_]\w*(?:\[\d+\])?)\s*(==|!=)\s*na\b"""),
            replacement = {
                val test = "na(${it.groupValues[1]})"
                if (it.groupValues[2] == "==") test else "not $test"
            },
        ),
        lineRule(
            id = "equality",
            code = "E107",
            what = "«=» در شرط به «==» تبدیل شد.",
            whatEn = "Turned “=” into “==” inside a condition.",
            // Only a bare name directly after and/or/not, which cannot be an assignment: there is no
            // assignment inside an expression in this language. The wider reading — anything after an
            // «and» up to the next «=» — would rewrite `plot(x, title = "a and b", color = …)`, which
            // is a correct script, and a rule that can break a correct script does not belong here.
            pattern = Regex("""\b(and|or|not)\s+([A-Za-z_]\w*(?:\[\d+\])?)\s*=(?![=])"""),
            replacement = { "${it.groupValues[1]} ${it.groupValues[2]} ==" },
        ),
        lineRule(
            id = "and-or-words",
            code = "E107",
            what = "«&&» و «||» به «and» و «or» تبدیل شدند.",
            whatEn = "Turned “&&” and “||” into “and” and “or”.",
            pattern = Regex("""&&|\|\|"""),
            replacement = { if (it.value == "&&") "and" else "or" },
        ),
        lineRule(
            id = "not-word",
            code = "E107",
            what = "«!» پیش از شرط به «not» تبدیل شد.",
            whatEn = "Turned “!” before a condition into “not”.",
            pattern = Regex("""(?<![=!<>])!(?=[A-Za-z_(])"""),
            replacement = { "not " },
        ),
        lineRule(
            id = "title-argument",
            code = "E208",
            what = "عنوان دوم plot به «title = …» تبدیل شد.",
            whatEn = "plot's second positional title became “title = …”.",
            pattern = Regex("""\bplot\s*\(\s*([^,()]+(?:\([^()]*\))?[^,()]*)\s*,\s*("[^"]*")\s*(?=[,)])"""),
            replacement = { "plot(${it.groupValues[1]}, title = ${it.groupValues[2]}" },
        ),
        lineRule(
            id = "colour-spelling",
            code = "E209",
            what = "«colour» به «color» تبدیل شد؛ نام آرگومان در این زبان آمریکایی است.",
            whatEn = "“colour” became “color”; the argument's name is American here.",
            pattern = Regex("""\bcolour\b"""),
            replacement = { "color" },
        ),
        lineRule(
            id = "close-index",
            code = "E202",
            what = "close(1) به close[1] تبدیل شد.",
            whatEn = "close(1) became close[1].",
            pattern = Regex("""\b(open|high|low|close|volume|hlc3|ohlc4|hl2)\s*\(\s*(\d+)\s*\)"""),
            replacement = { "${it.groupValues[1]}[${it.groupValues[2]}]" },
        ),
        lineRule(
            id = "semicolons",
            code = "E101",
            what = "«;» پایان خط حذف شد.",
            whatEn = "Dropped the trailing “;”.",
            pattern = Regex(""";\s*$"""),
            replacement = { "" },
        ),
        lineRule(
            id = "persian-digits",
            code = "E105",
            what = "رقم‌های فارسی داخل کد به لاتین تبدیل شدند.",
            whatEn = "Persian digits inside the code became Latin.",
            pattern = Regex("""[۰-۹]+"""),
            replacement = { match -> match.value.map { PERSIAN_DIGITS.indexOf(it).digitToChar() }.joinToString("") },
        ),
        lineRule(
            id = "pine-shape-constants",
            code = "E208",
            what = "ثابت‌های shape.* پاین به متن تبدیل شدند: style = \"triangleup\".",
            whatEn = "Pine's shape.* constants became text: style = \"triangleup\".",
            // `shape.triangleup` is a name this language does not bind, so it can only ever be a
            // paste from Pine; the same call with the name quoted is the form marker already reads.
            pattern = Regex("""\b(?:shape|location|size|xloc|plot)\.([a-z_]+)\b"""),
            replacement = { "\"${it.groupValues[1]}\"" },
        ),
        lineRule(
            id = "linewidth-argument",
            code = "E208",
            what = "«linewidth» به «width» تبدیل شد.",
            whatEn = "“linewidth” became “width”.",
            pattern = Regex("""\blinewidth\s*="""),
            replacement = { "width =" },
        ),
        lineRule(
            id = "drop-overlay-argument",
            code = "E208",
            what = "آرگومان overlay حذف شد؛ جای هر خط خودکار تعیین می‌شود یا با pane.",
            whatEn = "Dropped the overlay argument; a line's panel is worked out, or set with pane.",
            pattern = Regex("""\s*,\s*overlay\s*=\s*(true|false)"""),
            replacement = { "" },
        ),
        lineRule(
            id = "smart-quotes",
            code = "E106",
            what = "گیومه‌های تایپوگرافیک به گیومه‌ی ساده تبدیل شدند.",
            whatEn = "Typographic quotation marks became plain ones.",
            // What a chat window returns when it has prettified the answer, and what a reader gets
            // when they paste through a word processor. Never valid, so never ambiguous.
            pattern = Regex("""[\u201c\u201d\u00ab\u00bb]"""),
            replacement = { "\"" },
        ),
        Rule(
            id = "add-plot",
            code = "",
            what = "یک plot اضافه شد؛ اسکریپتی که چیزی نمی‌کشد روی چارت دیده نمی‌شود.",
            whatEn = "Added a plot; a script that draws nothing is invisible on the chart.",
            matches = { source ->
                // Only where the script computes something and draws nothing at all. A script whose
                // whole job is `signal(...)` is a legitimate shape and is left alone.
                val view = maskedSource(source)
                val draws = DRAW_CALLS.any { it.containsMatchIn(view) }
                val names = ASSIGNMENT.findAll(view).toList()
                if (draws || names.isEmpty()) emptyList() else listOf(source.lines().size)
            },
            rewrite = { source ->
                val last = ASSIGNMENT.findAll(maskedSource(source)).last().groupValues[2]
                source.trimEnd() + "\nplot($last)\n"
            },
        ),
    )

    /** What says «this is Pine» rather than «this is NamaScript». */
    private val PINE_MARKERS = listOf(
        Regex("""^\s*//@version\s*=""", RegexOption.MULTILINE),
        Regex("""^\s*(indicator|study|strategy)\s*\(""", RegexOption.MULTILINE),
        // Names Pine has and this language does not. `var`, `varip`, `:=`, `plotshape`,
        // `request.security` and `strategy.entry` are deliberately absent from this list: they are
        // all NamaScript's own, and a script using them is not a translation candidate.
        Regex("""\bta\.rma\s*\("""),
        Regex("""\b(shape|xloc|location|size)\.[a-z_]+"""),
        Regex("""\b(minval|maxval|linewidth|overlay)\s*="""),
        // A typed declaration: this language's `var` takes a name and no type.
        Regex("""^\s*(var|varip)\s+(float|int|bool|string|color)\s+\w+""", RegexOption.MULTILINE),
    )

    /** What says «this is code at all» rather than a sentence. */
    private val CODE_MARKERS = listOf(
        Regex("""^\s*\w+\s*=\s*\S""", RegexOption.MULTILINE),
        Regex("""\b(plot|plotshape|plotchar|marker|hline|signal|fill|bgcolor|alertcondition|strategy\.\w+)\s*\("""),
        Regex("""\bta\.\w+\s*\("""),
        Regex("""\binput\s*\("""),
    )

    private val DRAW_CALLS = listOf(
        Regex("""\bplot\s*\("""),
        Regex("""\bmarker\s*\("""),
        Regex("""\blevel\s*\("""),
        Regex("""\bsignal\s*\("""),
        Regex("""\bbgcolor\s*\("""),
        Regex("""\bfill\s*\("""),
        Regex("""\bplotshape\s*\("""),
        Regex("""\bhline\s*\("""),
    )

    private val ASSIGNMENT = Regex("""^(\s*)([A-Za-z_]\w*)\s*=\s*\S""", RegexOption.MULTILINE)

    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** What a blanked-out character becomes; never a character a script can contain. */
    private const val MASK = ''
}
