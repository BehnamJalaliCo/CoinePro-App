package com.coinepro.core.common

/**
 * Keeps Latin-scripted values readable inside right-to-left copy.
 *
 * Prices, symbols and identifiers stay Latin so they remain comparable with broker and exchange
 * terminals, which means every one of them is a left-to-right run embedded in Persian text. Without
 * an isolate the surrounding paragraph direction reorders neighbouring punctuation and adjacent
 * runs merge into each other, so `2,412.85` next to `-0.31%` can render with the signs on the wrong
 * values.
 *
 * The isolate characters are formatting-only: they carry no width and never affect string equality
 * checks on the numeric text itself, but they do change [String.length]. Apply this at the moment
 * of display, never before parsing or comparing.
 */
object BidiText {
    /**
     * U+2066 LEFT-TO-RIGHT ISOLATE.
     *
     * Public because a caller rebuilding a string around its own offset table — see [numericRuns] —
     * has to place the marks itself, and must place the same two characters this object does.
     */
    const val LRI = '⁦'

    /** U+2069 POP DIRECTIONAL ISOLATE. Public for the same reason as [LRI]. */
    const val PDI = '⁩'

    /** Wraps [value] so it renders left-to-right regardless of the surrounding paragraph. */
    fun isolateLtr(value: String): String = if (value.isEmpty()) value else "$LRI$value$PDI"

    /** Removes any isolates previously added by [isolateLtr]. */
    fun strip(value: String): String = value.replace(LRI.toString(), "").replace(PDI.toString(), "")

    /**
     * A percentage: the figure, its sign, and an isolate around both.
     *
     * ### Two separate things were wrong
     *
     * **The sign was in the resource string.** `%1$s٪` puts a neutral character after a Latin
     * left-to-right run, and the surrounding Persian paragraph then claims it: the guest home read
     * «نرخ برد ٪66.7», the sign in front of the number it belongs to, with the sentence's own full
     * stop in front of that. Passing the figure and its sign as one isolated argument gives the
     * paragraph nothing to claim.
     *
     * **And the sign was «٪».** U+066A ARABIC PERCENT SIGN does not lay out beside Persian digits
     * the way the Latin `%` does — the same trouble U+066C causes inside a grouped number, and the
     * isolate alone does not settle it. The Latin sign does, and it is what the rest of the app has
     * always printed: every percent pill, every change column, «+2.14%». A percentage is a market
     * figure, so it is Latin-digit and Latin-signed for the same reason a price is — a reader
     * compares it against MetaTrader. The «٪» in a handful of resource strings was the exception,
     * not the rule.
     */
    fun percent(figure: String): String = isolateLtr(strip(figure) + "%")

    /**
     * Whether a whole sentence is Latin, and therefore a paragraph rather than a run.
     *
     * ### Why this is a different question from [isolateLtr]
     *
     * An isolate is for a *run* — a price, a ticker, a percentage — sitting inside Persian copy.
     * It keeps the run's own characters in order and the surrounding paragraph stays right-to-left,
     * which is correct, because the paragraph really is Persian.
     *
     * A whole English sentence is not a run. Laid out in a right-to-left paragraph it comes back
     * with its **final full stop at the beginning**: «.lifting precious metals». That is what a news
     * card looked like the moment its stories started arriving from an English wire — the words in
     * order, the sentence-ending punctuation on the wrong end of the line, on every row.
     *
     * So a caller asks this and sets the paragraph's own direction, rather than isolating a
     * sentence that has nothing around it to be isolated from.
     *
     * ### Where the line is drawn
     *
     * On the letters only, ignoring digits, spaces and punctuation — those are direction-neutral
     * and counting them would call «۱۲ BTC» Latin. A string with no letters at all is not Latin: a
     * bare figure is a run and belongs to whatever paragraph it sits in.
     */
    /**
     * Isolates the compound numbers inside a right-to-left paragraph.
     *
     * ### The failure
     *
     * An academy lesson said «با اهرم ۱:۱۰۰» — a hundred to one — and the screen said «۱۰۰:۱». A
     * colon is direction-neutral, so a right-to-left paragraph puts the two digit runs on the
     * wrong sides of it. Everything about the string was correct; the sentence simply meant the
     * opposite once it was laid out, on a figure a reader sizes a position from.
     *
     * The same reordering waits inside every ratio, time and range in server-written prose:
     * «۱:۲»، «09:14»، «۲۰۲۴-۲۰۲۵». It is not a property of the copy, so no author can avoid it.
     *
     * ### What is wrapped, and what deliberately is not
     *
     * Only a run that runs **digit → connector → digit**: two numbers with something neutral
     * between them, which is the only shape that can come apart. A lone number is left alone —
     * digits carry their own strong direction and a bare «۱۰۰» is never reordered, so isolating it
     * would add two invisible characters to most of the words in the app for nothing.
     *
     * Persian and Latin digits both count, and so do the thousands separators, because the run has
     * to survive «۱۰٬۰۰۰:۱» in one piece.
     */
    fun isolateNumericRuns(value: String): String {
        val runs = numericRuns(value)
        if (runs.isEmpty()) return value
        val out = StringBuilder(value.length + runs.size * 2)
        var index = 0
        for (run in runs) {
            out.append(value, index, run.first)
            out.append(LRI).append(latinSeparators(value.substring(run.first, run.last + 1))).append(PDI)
            index = run.last + 1
        }
        out.append(value, index, value.length)
        return out.toString()
    }

    /**
     * Where [isolateNumericRuns] would put its marks, as ranges into [value].
     *
     * Separate from the wrapper because a caller that is rebuilding a string with its own offset
     * table — the academy's HTML converter, which carries bold and bullet spans across the
     * rebuild — cannot have two invisible characters appear in the middle of it without being told
     * where. One rule, two ways of applying it.
     */
    fun numericRuns(value: String): List<IntRange> {
        if (value.isEmpty()) return emptyList()
        val runs = mutableListOf<IntRange>()
        var index = 0
        while (index < value.length) {
            if (!value[index].isNumeral()) {
                index++
                continue
            }
            var end = index
            var last = index
            var connected = false
            while (end < value.length) {
                val character = value[end]
                when {
                    character.isNumeral() -> {
                        // A connector only counts once a digit has followed it.
                        if (last < end - 1) connected = true
                        last = end
                        end++
                    }
                    character in CONNECTORS -> end++
                    else -> break
                }
            }
            if (connected) runs += index..last
            index = last + 1
        }
        return runs
    }

    /**
     * The Arabic separators, swapped for the Latin ones with the same shape.
     *
     * The isolate alone is not enough for «۱۰٬۰۰۰», and this is why. U+066C ARABIC THOUSANDS
     * SEPARATOR carries the bidi class of an *Arabic number*, so it does not merge with the Persian
     * digits either side of it the way a plain comma does — it stays a separate run and lands on
     * the wrong side of them. The academy's lesson said ten thousand and drew «۰۰۰٬۱۰» inside an
     * isolate that had already straightened out the ratio in the same sentence.
     *
     * In IRANYekanX the two characters are drawn as the same low comma, so this changes the order
     * and nothing else. It applies only inside a run that is already all digits — never to prose,
     * where «٬» is punctuation and belongs to the writer.
     */
    private fun latinSeparators(run: String): String =
        run.map(::numericSeparator).joinToString("")

    /**
     * One character of a numeric run, as it has to be laid out. See [latinSeparators].
     *
     * Public and per-character because [numericRuns]' other caller rebuilds its string one
     * character at a time against an offset table it also has to keep. The swap is one-for-one, so
     * that table is unaffected.
     */
    fun numericSeparator(character: Char): Char = when (character) {
        '٬' -> ','
        '٫' -> '.'
        else -> character
    }

    /** Latin `0-9` and Persian `۰-۹`. Arabic-Indic `٠-٩` too, which some feeds send. */
    private fun Char.isNumeral(): Boolean =
        this in '0'..'9' || this in '۰'..'۹' || this in '٠'..'٩'

    /** What can sit between two numbers and be reordered: a ratio, a time, a range, a fraction. */
    private const val CONNECTORS = ":/-–.,٫٬−"

    fun isLatinSentence(value: String): Boolean {
        var latin = 0
        var other = 0
        for (character in value) {
            if (!character.isLetter()) continue
            if (character.code < 0x0250) latin++ else other++
        }
        return latin > 0 && latin > other
    }
}
