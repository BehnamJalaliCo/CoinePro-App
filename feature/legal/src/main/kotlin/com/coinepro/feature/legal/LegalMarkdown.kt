package com.coinepro.feature.legal

/**
 * The Markdown the two legal documents are written in, and nothing else.
 *
 * ### Why this exists instead of a dependency
 *
 * `docs/legal/TERMS.md` and `docs/legal/PRIVACY_POLICY.md` use a closed set of constructs —
 * headings, paragraphs, rules, blockquotes, bullets, numbered items, tables, bold, italic, inline
 * code, links and autolinks. That is a few hundred lines of parser. A CommonMark library is a few
 * hundred kilobytes of one, plus an HTML or AnnotatedString bridge, to render two files that never
 * change shape. `scripts/site/build-site.py` made the same call for the published website and this
 * is deliberately the same subset, so a construct that renders on the site renders in the app.
 *
 * ### What happens to anything outside the set
 *
 * The rule is that **no delimiter ever reaches the screen**. A construct the parser recognises but
 * has no style for keeps its text and loses its syntax: an image becomes its alt text, strikethrough
 * becomes the words, a level-4-to-6 heading is a heading, a fenced block is a block of lines, and an
 * HTML comment is dropped the way a comment should be. A construct it does not recognise at all —
 * a reference-style link, a footnote — reaches the screen as the characters that were typed, which
 * is text and not markup, and is the failure mode worth having: readable, obviously wrong to
 * whoever wrote it, and never a `**` in front of a reader.
 *
 * There are no Android types in this file. That is what makes the whole subset testable on a plain
 * JVM, which is why every construct below has a test.
 */

/** A run of text inside a block, carrying whatever the source said about it. */
data class LegalSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    /**
     * Where a link points, or null.
     *
     * Kept in the model and, for a named link, never shown: the screen renders link *text*. The one
     * exception is [autolink], where the source wrote the address itself and there is no other text
     * to show.
     */
    val link: String? = null,
    /** `<https://example.test>` — the address is the text, so it is also what a reader sees. */
    val autolink: Boolean = false,
)

/** A paragraph-level thing. */
sealed interface LegalBlock {

    data class Heading(val level: Int, val spans: List<LegalSpan>) : LegalBlock

    data class Paragraph(val spans: List<LegalSpan>) : LegalBlock

    /** A `>` note. Split at its own blank lines rather than run together into one lump. */
    data class Quote(val paragraphs: List<List<LegalSpan>>) : LegalBlock

    /**
     * Bullets or numbered items.
     *
     * The marker is carried rather than derived, because the numbered lists in these documents are
     * numbered in Persian digits — «۱.» — and re-deriving them in Latin would renumber a legal
     * document's own clauses on screen.
     */
    data class Listing(val items: List<Item>) : LegalBlock {
        data class Item(val marker: String, val spans: List<LegalSpan>)
    }

    data class Table(
        val header: List<List<LegalSpan>>,
        val rows: List<List<List<LegalSpan>>>,
    ) : LegalBlock

    /** A fenced block. Neither document has one; it is here so a fence never prints as `` ``` ``. */
    data class Code(val lines: List<String>) : LegalBlock

    object Rule : LegalBlock
}

/**
 * A parsed document, with the two things a reader needs above the text lifted out of it.
 *
 * Both documents open with a level-one title and then a bold-led one-liner carrying the revision
 * date. That is structure, not decoration, so the screen puts them in its header rather than
 * printing a title twice.
 */
data class LegalReading(
    val title: String,
    /** The «آخرین بازنگری» line, where the document carried one. */
    val revision: List<LegalSpan>?,
    val blocks: List<LegalBlock>,
    /** Which way the document reads. Taken from the text, so a translated file needs no flag. */
    val rightToLeft: Boolean,
)

/** The text a reader would see, with every style dropped. */
fun List<LegalSpan>.plainText(): String = joinToString(separator = "") { it.text }

object LegalMarkdown {

    fun read(source: String): LegalReading {
        val blocks = parse(source).toMutableList()

        val heading = blocks.firstOrNull() as? LegalBlock.Heading
        val title = if (heading != null && heading.level == 1) {
            blocks.removeAt(0)
            heading.spans.plainText()
        } else {
            ""
        }

        // Only a *bold-led* opening line is a revision stamp. The English half of the privacy
        // policy opens with an ordinary sentence under its title, and lifting that into the header
        // would put a paragraph of content where a date belongs.
        val opening = blocks.firstOrNull()
        val revision = if (opening is LegalBlock.Paragraph && opening.spans.firstOrNull()?.bold == true) {
            blocks.removeAt(0)
            opening.spans
        } else {
            null
        }

        return LegalReading(title, revision, blocks, rightToLeft(source))
    }

    fun parse(source: String): List<LegalBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<LegalBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index].trim()

            if (line.isEmpty()) {
                index++
                continue
            }

            // A note to whoever edits the file. The generated copies under
            // feature/legal/src/main/assets carry one saying where the real document lives, and a
            // reader of the terms has no business seeing it.
            if (line.startsWith("<!--")) {
                while (index < lines.size && !lines[index].contains("-->")) index++
                index++
                continue
            }

            if (line.startsWith("```")) {
                index++
                val body = ArrayList<String>()
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    body += lines[index]
                    index++
                }
                index++
                blocks += LegalBlock.Code(body)
                continue
            }

            if (RULE.matchEntire(line) != null) {
                blocks += LegalBlock.Rule
                index++
                continue
            }

            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                blocks += LegalBlock.Heading(
                    level = heading.groupValues[1].length,
                    spans = inline(heading.groupValues[2].trim()),
                )
                index++
                continue
            }

            if (line.startsWith(">")) {
                val paragraphs = ArrayList<List<LegalSpan>>()
                val current = ArrayList<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    val body = lines[index].trim().removePrefix(">").trim()
                    if (body.isEmpty()) {
                        if (current.isNotEmpty()) {
                            paragraphs += inline(current.joinToString(separator = " "))
                            current.clear()
                        }
                    } else {
                        current += body
                    }
                    index++
                }
                if (current.isNotEmpty()) paragraphs += inline(current.joinToString(separator = " "))
                if (paragraphs.isNotEmpty()) blocks += LegalBlock.Quote(paragraphs)
                continue
            }

            if (line.startsWith("|")) {
                val rows = ArrayList<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    rows += lines[index].trim().trim('|').split('|').map { it.trim() }
                    index++
                }
                // Row two of a Markdown table is the alignment rule, not data. Filtered by shape
                // rather than by position, so a table missing it still renders its first row as a
                // header instead of eating a row of the document.
                val data = rows.filterNot { it.isAlignmentRule() }
                if (data.isNotEmpty()) {
                    blocks += LegalBlock.Table(
                        header = data.first().map(::inline),
                        rows = data.drop(1).map { row -> row.map(::inline) },
                    )
                }
                continue
            }

            val numbered = ORDERED.matchEntire(line) != null
            if (numbered || BULLET.matchEntire(line) != null) {
                val markers = ArrayList<String>()
                val texts = ArrayList<StringBuilder>()
                while (index < lines.size) {
                    val raw = lines[index]
                    val current = raw.trim()
                    val item = if (numbered) ORDERED.matchEntire(current) else BULLET.matchEntire(current)
                    if (item != null) {
                        markers += if (numbered) item.groupValues[1] + "." else BULLET_MARK
                        texts += StringBuilder(item.groupValues[if (numbered) 2 else 1])
                        index++
                    } else if (current.isNotEmpty() && texts.isNotEmpty() && raw.first().isWhitespace()) {
                        // A wrapped continuation line belongs to the item above it.
                        texts.last().append(' ').append(current)
                        index++
                    } else {
                        break
                    }
                }
                blocks += LegalBlock.Listing(
                    markers.indices.map { LegalBlock.Listing.Item(markers[it], inline(texts[it].toString())) },
                )
                continue
            }

            // A paragraph. The first line is taken unconditionally so that a line no branch above
            // claimed — `#hashtag`, say — becomes text rather than spinning this loop forever.
            val paragraph = ArrayList<String>()
            paragraph += line
            index++
            while (index < lines.size) {
                val current = lines[index].trim()
                if (current.isEmpty() || current.opensAnotherBlock()) break
                paragraph += current
                index++
            }
            blocks += LegalBlock.Paragraph(inline(paragraph.joinToString(separator = " ")))
        }

        return blocks
    }
}

// ── blocks ───────────────────────────────────────────────────────────────────────────────────────

private const val BULLET_MARK = "•"

private val HEADING = Regex("(#{1,6})\\s+(.*)")
private val BULLET = Regex("[*+-]\\s+(.*)")

/**
 * A numbered item.
 *
 * Both Latin and Persian digits, because the terms number their own clauses «۱.» «۲.» and a parser
 * that only knew `1.` would render four numbered obligations as four loose paragraphs.
 */
private val ORDERED = Regex("([0-9۰-۹٠-٩]+)[.)]\\s+(.*)")
private val RULE = Regex("-{3,}|\\*{3,}|_{3,}")

private fun String.opensAnotherBlock(): Boolean =
    RULE.matchEntire(this) != null ||
        startsWith("|") ||
        startsWith(">") ||
        startsWith("#") ||
        startsWith("```") ||
        startsWith("<!--") ||
        BULLET.matchEntire(this) != null ||
        ORDERED.matchEntire(this) != null

private fun List<String>.isAlignmentRule(): Boolean =
    isNotEmpty() && all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } }

/**
 * Which way the document reads, counted rather than declared.
 *
 * The privacy policy ships as two files — Persian and English — and the terms only in Persian, so
 * the direction is a property of the file and not of the reader's locale. Counting letters means a
 * document added later is right without anybody remembering to set a flag, and a Persian document
 * full of Latin product names still counts as Persian.
 */
private fun rightToLeft(source: String): Boolean {
    var rtl = 0
    var ltr = 0
    for (char in source) {
        when (char) {
            // Arabic, Arabic Supplement and Extended-A — which is where every Persian letter lives
            // — plus the two presentation-forms blocks, in case a document arrives normalised.
            in '\u0600'..'\u06FF', in '\u0750'..'\u077F',
            in '\uFB50'..'\uFDFF', in '\uFE70'..'\uFEFF',
            -> rtl++
            in 'A'..'Z', in 'a'..'z' -> ltr++
        }
    }
    return rtl >= ltr
}

// ── inline ───────────────────────────────────────────────────────────────────────────────────────

private class Taken(val spans: List<LegalSpan>, val end: Int)

private class Run(val text: String, val end: Int)

private class Anchor(val text: String, val href: String, val end: Int)

private val EMAIL = Regex("[^\\s@<>]+@[^\\s@<>]+\\.[A-Za-z]{2,}")

private fun inline(source: String): List<LegalSpan> {
    val spans = ArrayList<LegalSpan>()
    val literal = StringBuilder()
    var index = 0

    fun flush() {
        if (literal.isNotEmpty()) {
            spans += LegalSpan(literal.toString())
            literal.setLength(0)
        }
    }

    while (index < source.length) {
        val taken = takeMarkup(source, index)
        if (taken == null) {
            literal.append(source[index])
            index++
        } else {
            flush()
            spans += taken.spans
            index = taken.end
        }
    }
    flush()
    return spans
}

private fun takeMarkup(source: String, at: Int): Taken? {
    // An image. Neither document has one, and if one arrives the reader gets the alt text — which
    // is the sentence somebody wrote for exactly this case — rather than `![…](…)` across a page.
    if (source.startsWith("![", at)) {
        val anchor = matchAnchor(source, at + 1) ?: return null
        return Taken(inline(anchor.text), anchor.end)
    }
    if (source[at] == '[') {
        val anchor = matchAnchor(source, at) ?: return null
        return Taken(inline(anchor.text).map { it.copy(link = anchor.href) }, anchor.end)
    }
    if (source[at] == '<') {
        val close = source.indexOf('>', at + 1)
        if (close <= at + 1) return null
        val body = source.substring(at + 1, close)
        val href = autolinkHref(body) ?: return null
        return Taken(listOf(LegalSpan(body, link = href, autolink = true)), close + 1)
    }
    if (source.startsWith("**", at)) {
        val run = delimited(source, at, "**") ?: return null
        return Taken(inline(run.text).map { it.copy(bold = true) }, run.end)
    }
    // Recognised, and deliberately unstyled: a struck-through clause in a legal document is a
    // clause somebody meant to delete. Showing the words without the tildes is the safe reading,
    // and `~~` in front of a reader is not.
    if (source.startsWith("~~", at)) {
        val run = delimited(source, at, "~~") ?: return null
        return Taken(inline(run.text), run.end)
    }
    if (source[at] == '*') {
        val run = delimited(source, at, "*") ?: return null
        return Taken(inline(run.text).map { it.copy(italic = true) }, run.end)
    }
    if (source[at] == '`') {
        val run = delimited(source, at, "`") ?: return null
        return Taken(listOf(LegalSpan(run.text, code = true)), run.end)
    }
    return null
}

/**
 * The text between a pair of delimiters, or null where there is no pair.
 *
 * Emphasis does not open or close on a space, which is what keeps a lone asterisk in a sentence
 * from swallowing the rest of the paragraph and italicising it.
 */
private fun delimited(source: String, at: Int, marker: String): Run? {
    val from = at + marker.length
    val close = source.indexOf(marker, from)
    if (close < 0) return null
    val text = source.substring(from, close)
    if (text.isEmpty() || text.first().isWhitespace() || text.last().isWhitespace()) return null
    return Run(text, close + marker.length)
}

private fun matchAnchor(source: String, at: Int): Anchor? {
    if (source.getOrNull(at) != '[') return null
    val closeText = source.indexOf(']', at + 1)
    if (closeText < 0 || source.getOrNull(closeText + 1) != '(') return null
    val closeHref = source.indexOf(')', closeText + 2)
    if (closeHref < 0) return null
    val href = source.substring(closeText + 2, closeHref).trim()
    if (href.isEmpty()) return null
    return Anchor(source.substring(at + 1, closeText), href, closeHref + 1)
}

/**
 * `<https://…>` and `<name@example.test>`, which are the only two autolinks Markdown has.
 *
 * Anything else in angle brackets is not a link and is left as the characters it is — an HTML tag
 * dropped into one of these documents would print as a tag, which is visible and fixable, rather
 * than disappearing into a renderer that decided to interpret it.
 */
private fun autolinkHref(body: String): String? = when {
    body.startsWith("https://") || body.startsWith("http://") -> body
    EMAIL.matchEntire(body) != null -> "mailto:$body"
    else -> null
}
