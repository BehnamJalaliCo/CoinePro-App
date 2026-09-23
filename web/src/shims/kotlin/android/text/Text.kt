@file:Suppress("unused", "UNUSED_PARAMETER")

package android.text

import android.view.View
import java.util.Locale

object TextUtils {
    fun getLayoutDirectionFromLocale(locale: Locale?): Int =
        if (locale?.language in setOf("fa", "ar", "he", "ur")) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()
    fun join(delimiter: CharSequence, tokens: Iterable<*>): String = tokens.joinToString(delimiter)
    fun htmlEncode(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** Text with styled ranges, as `Html.fromHtml` returns it. */
interface Spanned : CharSequence {
    fun <T : Any> getSpans(start: Int, end: Int, type: kotlin.reflect.KClass<T>): Array<T>
    fun getSpanStart(span: Any): Int
    fun getSpanEnd(span: Any): Int
    fun getSpanFlags(span: Any): Int = 0
}

class SpannedString internal constructor(private val text: String, private val spans: List<Triple<Any, Int, Int>>) : Spanned {
    override val length: Int get() = text.length
    override fun get(index: Int): Char = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSpans(start: Int, end: Int, type: kotlin.reflect.KClass<T>): Array<T> {
        val hits = spans.filter { (span, s, e) -> type.isInstance(span) && s <= end && e >= start }.map { it.first }
        return arrayOfNulls<Any>(hits.size).also { a -> hits.forEachIndexed { i, x -> a[i] = x } } as Array<T>
    }
    override fun getSpanStart(span: Any): Int = spans.firstOrNull { it.first === span }?.second ?: -1
    override fun getSpanEnd(span: Any): Int = spans.firstOrNull { it.first === span }?.third ?: -1
}
