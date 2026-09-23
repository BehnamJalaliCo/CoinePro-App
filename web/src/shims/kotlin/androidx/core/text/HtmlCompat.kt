@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.core.text

import android.graphics.Typeface
import android.text.Spanned
import android.text.SpannedString
import android.text.style.BulletSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

private fun parseHtmlJs(html: String): JsAny = js("new DOMParser().parseFromString(html, 'text/html').body")
private fun kidsJs(n: JsAny): Int = js("n.childNodes.length")
private fun kidJs(n: JsAny, i: Int): JsAny = js("n.childNodes[i]")
private fun typeJs(n: JsAny): Int = js("n.nodeType")
private fun tagJs(n: JsAny): String = js("(n.tagName || '').toLowerCase()")
private fun dataJs(n: JsAny): String = js("n.nodeValue || ''")
private fun hrefJs(n: JsAny): String = js("n.getAttribute('href') || ''")

/**
 * `Html.fromHtml`, over the browser's own HTML parser: the same text and the same spans the phone's
 * lesson renderer reads — bold, italic, underline, strike-through, links, bullets — with blocks
 * separated the way `FROM_HTML_MODE_COMPACT` separates them, by one line break.
 */
object HtmlCompat {
    const val FROM_HTML_MODE_LEGACY = 0
    const val FROM_HTML_MODE_COMPACT = 63

    fun fromHtml(source: String, flags: Int): Spanned {
        val out = StringBuilder()
        val spans = ArrayList<Triple<Any, Int, Int>>()
        fun breakLine() { if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
        fun walk(node: JsAny) {
            when (typeJs(node)) {
                3 -> out.append(dataJs(node).replace(Regex("\\s+"), " ").let { if (out.isEmpty() || out.last() == '\n') it.trimStart() else it })
                1 -> {
                    val tag = tagJs(node)
                    val block = tag in BLOCKS
                    if (block) breakLine()
                    if (tag == "br") { out.append('\n'); return }
                    val start = out.length
                    for (i in 0 until kidsJs(node)) walk(kidJs(node, i))
                    val end = out.length
                    when (tag) {
                        "b", "strong" -> spans += Triple(StyleSpan(Typeface.BOLD), start, end)
                        "i", "em", "cite", "dfn" -> spans += Triple(StyleSpan(Typeface.ITALIC), start, end)
                        "u", "ins" -> spans += Triple(UnderlineSpan(), start, end)
                        "s", "strike", "del" -> spans += Triple(StrikethroughSpan(), start, end)
                        "a" -> spans += Triple(URLSpan(hrefJs(node)), start, end)
                        "li" -> spans += Triple(BulletSpan(), start, end)
                        "h1", "h2", "h3", "h4", "h5", "h6" -> spans += Triple(StyleSpan(Typeface.BOLD), start, end)
                    }
                    if (block) breakLine()
                }
                else -> {}
            }
        }
        walk(parseHtmlJs(source))
        val text = out.toString().trimEnd('\n')
        return SpannedString(text, spans.map { (s, a, b) -> Triple(s, a.coerceAtMost(text.length), b.coerceAtMost(text.length)) })
    }

    fun toHtml(text: Spanned, option: Int): String = text.toString()

    private val BLOCKS = setOf("p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "section", "article", "tr", "pre")
}

fun String.parseAsHtml(flags: Int = HtmlCompat.FROM_HTML_MODE_LEGACY): Spanned = HtmlCompat.fromHtml(this, flags)
