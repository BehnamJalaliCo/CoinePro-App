package com.coinepro.feature.academy

import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

/**
 * The bullet written into the text, rather than painted beside it.
 *
 * `BulletSpan` draws a dot at a fixed *left* offset, which in a right-to-left paragraph puts it on
 * the wrong side of the line — and Compose has no span to map it to anyway. Written into the string
 * it is an ordinary character, so the bidi algorithm places it at the start of the line whichever
 * direction the line runs.
 */
private const val BULLET = "• "

/**
 * Lesson bodies, which arrive as HTML from the academy's own editor.
 *
 * Rendered rather than shown raw, and rendered rather than put in a WebView. A WebView here would
 * be a second rendering engine inside a Compose screen: its own fonts, its own text sizing, its own
 * scroll container fighting the one around it, and no way to make a paragraph inside it match a
 * paragraph outside it. For prose with bold, italics and lists, converting to an `AnnotatedString`
 * costs a few dozen lines and keeps one typographic system.
 *
 * The conversion goes through the platform's own parser rather than a regex. `HtmlCompat` handles
 * entities, malformed nesting and the block-level line breaks that a naive tag-stripper gets wrong
 * — and the content here is authored in a rich-text editor, so malformed nesting is not a
 * hypothetical.
 */
internal fun htmlToAnnotated(html: String): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")
    val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    // The parser leaves the trailing newlines that block elements imply. Left in the middle, where
    // they are paragraph breaks; trimmed at the ends, where they are just a gap under the text.
    val source = spanned.toString().trimEnd('\n')

    // Where the bullets go, in source coordinates. Sorted, because the spans come back in whatever
    // order the parser stored them and the rebuild below walks forwards.
    val bulletStarts = spanned.getSpans(0, spanned.length, BulletSpan::class.java)
        .map { spanned.getSpanStart(it) }
        .filter { it in 0..source.length }
        .distinct()
        .sorted()

    val text = StringBuilder(source.length + bulletStarts.size * BULLET.length)
    // Source index → index in the rebuilt string. One entry per source position plus the end, so
    // a span ending at the last character still has somewhere to map to.
    val shifted = IntArray(source.length + 1)
    var cursor = 0
    for (index in 0..source.length) {
        if (index in bulletStarts) {
            text.append(BULLET)
            cursor += BULLET.length
        }
        shifted[index] = cursor
        if (index < source.length) {
            text.append(source[index])
            cursor++
        }
    }
    val out = text.toString()

    return buildAnnotatedString {
        append(out)
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            // Clamped before the shift, because the trailing newlines were cut after the spans were
            // measured — a lesson ending in bold would otherwise index past the end.
            val start = spanned.getSpanStart(span).coerceIn(0, source.length)
            val end = spanned.getSpanEnd(span).coerceIn(0, source.length)
            if (end <= start) continue
            val from = shifted[start]
            val to = shifted[end]
            when (span) {
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, to)
                    android.graphics.Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), from, to)
                    android.graphics.Typeface.BOLD_ITALIC ->
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            from,
                            to,
                        )
                }
                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), from, to)
                is StrikethroughSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), from, to)
            }
        }
    }
}
