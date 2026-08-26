package com.coinepro.feature.academy

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lesson bodies come out of a rich-text editor, so the interesting cases are the ones an editor
 * produces: entities, block elements that imply line breaks, and nesting nobody typed on purpose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LessonHtmlTest {

    @Test
    fun `plain paragraphs keep their text and lose their tags`() {
        val out = htmlToAnnotated("<p>یک لات استاندارد ۱۰۰٬۰۰۰ واحد است.</p>")
        assertEquals("یک لات استاندارد ۱۰۰٬۰۰۰ واحد است.", out.text)
    }

    @Test
    fun `bold and italic become styles rather than markup`() {
        val out = htmlToAnnotated("<p>یک <b>لات</b> و یک <i>پیپ</i></p>")
        assertFalse("no tags survive", out.text.contains("<"))

        val bold = out.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        val italic = out.spanStyles.firstOrNull { it.item.fontStyle == FontStyle.Italic }
        assertTrue("bold not carried", bold != null)
        assertTrue("italic not carried", italic != null)
        assertEquals("لات", out.text.substring(bold!!.start, bold.end))
        assertEquals("پیپ", out.text.substring(italic!!.start, italic.end))
    }

    @Test
    fun `entities are decoded`() {
        // A stripper written with a regex gets this wrong and shows "&nbsp;" to the reader.
        val out = htmlToAnnotated("<p>۱&nbsp;لات &amp; ۱۰&nbsp;مینی&#8209;لات</p>")
        assertFalse(out.text.contains("&nbsp;"))
        assertFalse(out.text.contains("&amp;"))
        assertTrue(out.text.contains("&"))
    }

    @Test
    fun `paragraphs are separated and the trailing gap is trimmed`() {
        // Block elements imply breaks. Without them the two paragraphs run into one sentence; with
        // the parser's trailing newlines left in, the lesson ends with blank space under it.
        val out = htmlToAnnotated("<p>اول</p><p>دوم</p>")
        assertTrue("paragraphs must not run together", out.text.contains("\n"))
        assertFalse("no trailing gap", out.text.endsWith("\n"))
        assertTrue(out.text.startsWith("اول"))
        assertTrue(out.text.endsWith("دوم"))
    }

    @Test
    fun `list items get a bullet written into the text`() {
        // Written rather than painted: BulletSpan draws at a fixed left offset, which in a
        // right-to-left paragraph puts the dot at the end of the line. As a character it is placed
        // by the bidi algorithm, so it lands at the start whichever way the line runs — and without
        // it a three-item list renders as three short paragraphs, which is what it looked like.
        val out = htmlToAnnotated("<ul><li>الف</li><li>ب</li><li>پ</li></ul>")
        val lines = out.text.split("\n").filter { it.isNotBlank() }
        assertEquals(listOf("• الف", "• ب", "• پ"), lines)
    }

    @Test
    fun `a style inside a list item still covers the right words`() {
        // The bullets shift every offset after them. A span applied at its unshifted index would
        // bold the wrong characters — and by the third item it would be off by six.
        val out = htmlToAnnotated("<ul><li>الف <b>ب</b></li><li>پ <b>ت</b></li></ul>")
        val bolds = out.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, bolds.size)
        assertEquals(setOf("ب", "ت"), bolds.map { out.text.substring(it.start, it.end) }.toSet())
    }

    @Test
    fun `malformed nesting does not throw`() {
        // Real editor output. The platform parser recovers; a hand-rolled one usually does not.
        val out = htmlToAnnotated("<p>الف <b>ب <i>پ</b> ت</i></p>")
        assertTrue(out.text.contains("الف"))
        assertTrue(out.text.contains("ت"))
    }

    @Test
    fun `empty content is empty rather than a crash`() {
        assertEquals("", htmlToAnnotated("").text)
        assertEquals("", htmlToAnnotated("   ").text)
    }

    @Test
    fun `a span running past the trimmed text does not go out of bounds`() {
        // The trailing newlines are cut after the spans were measured against the untrimmed string,
        // so any span touching the end has to be clamped. Getting this wrong throws on the exact
        // lessons that end in bold — a heading-shaped ending, which is common.
        val out = htmlToAnnotated("<p><b>پایان</b></p>")
        for (span in out.spanStyles) {
            assertTrue("span ${span.start}..${span.end} past ${out.text.length}", span.end <= out.text.length)
        }
    }
}
