package com.coinepro.feature.script

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Syntax colour for the code field, and the underline on the failing token.
 *
 * A [VisualTransformation] that keeps the text and its offsets exactly (the mapping is the
 * identity) and only attaches spans: comments, strings, numbers, the three word-operators, the
 * namespaces (`ta.` `math.` `input.` `color.` `request.`) with the name after them, and the
 * built-in series. It is a tokenizer of its own rather than the language's `Lexer`, which is
 * internal to `:namascript` and stops at the first error — an editor has to colour a line that
 * is still being typed.
 *
 * The squiggle is the failure's line and column from `ScriptFailure`: the token that starts
 * there, or the rest of the line when the column is past the text, underlined and tinted in the
 * sell colour. Compose has no wavy underline; a straight one in red reads the same way.
 */
internal class NamaSyntaxTransformation(
    private val failureLine: Int?,
    private val failureColumn: Int?,
    private val squiggle: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val builder = AnnotatedString.Builder(source)
        for (span in NamaSyntax.spans(source)) {
            builder.addStyle(span.style, span.start, span.end)
        }
        squiggleRange(source)?.let { range ->
            builder.addStyle(
                SpanStyle(color = squiggle, textDecoration = TextDecoration.Underline, background = squiggle.copy(alpha = 0.14f)),
                range.start,
                range.end,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun squiggleRange(source: String): TextRange? {
        val line = failureLine ?: return null
        if (line < 1) return null
        var start = 0
        var current = 1
        while (current < line) {
            val next = source.indexOf('\n', start)
            if (next < 0) return null
            start = next + 1
            current++
        }
        val lineEnd = source.indexOf('\n', start).let { if (it < 0) source.length else it }
        if (lineEnd <= start) return null
        val column = ((failureColumn ?: 1) - 1).coerceIn(0, lineEnd - start)
        val from = start + column
        if (from >= lineEnd) return TextRange(start, lineEnd)
        var to = from + 1
        if (source[from].isLetterOrDigit() || source[from] == '_') {
            while (to < lineEnd && (source[to].isLetterOrDigit() || source[to] == '_' || source[to] == '.')) to++
        }
        return TextRange(from, to)
    }
}

internal object NamaSyntax {

    class Span(val start: Int, val end: Int, val style: SpanStyle)

    // The palette: the studio's terminal surface is near-black, so these are the lighter tones.
    private val COMMENT = SpanStyle(color = Color(0xFF7A8290))
    private val STRING = SpanStyle(color = Color(0xFFE6C07B))
    private val NUMBER = SpanStyle(color = Color(0xFFD19A66))
    private val KEYWORD = SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.SemiBold)
    private val NAMESPACE = SpanStyle(color = Color(0xFF56B6C2))
    private val FUNCTION = SpanStyle(color = Color(0xFF61AFEF))
    private val SERIES = SpanStyle(color = Color(0xFF98C379))

    private val KEYWORDS = setOf("and", "or", "not", "true", "false")
    private val SERIES_NAMES = setOf("open", "high", "low", "close", "volume", "hl2", "hlc3", "ohlc4", "time", "bar_index", "n", "confirmed")
    private val NAMESPACES = setOf("ta", "math", "input", "color", "request")

    fun spans(source: String): List<Span> {
        val out = ArrayList<Span>()
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) n else it }
                    out += Span(i, end, COMMENT)
                    i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && source[j] != c && source[j] != '\n') j++
                    val end = if (j < n && source[j] == c) j + 1 else j
                    out += Span(i, end, STRING)
                    i = end
                }
                c.isDigit() || (c == '.' && i + 1 < n && source[i + 1].isDigit()) -> {
                    var j = i
                    while (j < n && (source[j].isDigit() || source[j] == '.')) j++
                    out += Span(i, j, NUMBER)
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (source[j].isLetterOrDigit() || source[j] == '_')) j++
                    val word = source.substring(i, j)
                    when {
                        word in KEYWORDS -> out += Span(i, j, KEYWORD)
                        word in NAMESPACES && j < n && source[j] == '.' -> {
                            var k = j + 1
                            while (k < n && (source[k].isLetterOrDigit() || source[k] == '_')) k++
                            out += Span(i, j + 1, NAMESPACE)
                            if (k > j + 1) out += Span(j + 1, k, FUNCTION)
                            j = k
                        }
                        word in SERIES_NAMES -> out += Span(i, j, SERIES)
                        j < n && source[j] == '(' -> out += Span(i, j, FUNCTION)
                    }
                    i = j
                }
                else -> i++
            }
        }
        return out
    }
}
