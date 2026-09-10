package com.coinepro.feature.script

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's colouring and its squiggle, on the JVM.
 *
 * The transformation keeps the text and the offsets exactly — a highlighter that shifted the
 * caret would be worse than none — and puts one underline where the failure said.
 */
class NamaSyntaxTest {

    @Test
    fun `tokens are classed`() {
        val source = "// note\nfast = ta.ema(close, 12) and true \"x\""
        val spans = NamaSyntax.spans(source)
        val texts = spans.map { source.substring(it.start, it.end) }
        assertTrue(texts.contains("// note"))
        assertTrue(texts.contains("ta."))
        assertTrue(texts.contains("ema"))
        assertTrue(texts.contains("close"))
        assertTrue(texts.contains("12"))
        assertTrue(texts.contains("and"))
        assertTrue(texts.contains("true"))
        assertTrue(texts.contains("\"x\""))
    }

    @Test
    fun `the text and the offsets are untouched`() {
        val source = "plot(ta.sma(close, 20))\nmarker(close > open)"
        val transformed = NamaSyntaxTransformation(null, null, Color.Red).filter(AnnotatedString(source))
        assertEquals(source, transformed.text.text)
        assertEquals(7, transformed.offsetMapping.originalToTransformed(7))
        assertEquals(30, transformed.offsetMapping.transformedToOriginal(30))
    }

    @Test
    fun `the failing token on the failing line is underlined`() {
        val source = "a = close\nb = ta.magic(close)\nplot(b)"
        val transformed = NamaSyntaxTransformation(2, 5, Color.Red).filter(AnnotatedString(source))
        val underlined = transformed.text.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(1, underlined.size)
        assertEquals("ta.magic", source.substring(underlined[0].start, underlined[0].end))
    }

    @Test
    fun `a column past the text underlines the rest of the line, and a bad line nothing`() {
        val source = "plot(close"
        val past = NamaSyntaxTransformation(1, 40, Color.Red).filter(AnnotatedString(source))
        assertEquals(1, past.text.spanStyles.count { it.item.textDecoration == TextDecoration.Underline })
        val none = NamaSyntaxTransformation(9, 1, Color.Red).filter(AnnotatedString(source))
        assertEquals(0, none.text.spanStyles.count { it.item.textDecoration == TextDecoration.Underline })
    }
}
