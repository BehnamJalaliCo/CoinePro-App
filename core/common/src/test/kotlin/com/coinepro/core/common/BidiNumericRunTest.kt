package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compound numbers inside right-to-left prose.
 *
 * The academy lesson that said «با اهرم ۱:۱۰۰» rendered «۱۰۰:۱» — the opposite leverage, on the
 * figure a reader sizes a position from.
 */
class BidiNumericRunTest {

    private val lri = '⁦'
    private val pdi = '⁩'

    @Test
    fun `a ratio is held together`() {
        assertEquals("با اهرم ${lri}۱:۱۰۰${pdi}، ", BidiText.isolateNumericRuns("با اهرم ۱:۱۰۰، "))
    }

    @Test
    fun `a lone number is left alone`() {
        // Digits carry their own direction; two invisible characters around every number in the app
        // would be cost with no effect.
        assertEquals("۱۰۰ دلار", BidiText.isolateNumericRuns("۱۰۰ دلار"))
    }

    @Test
    fun `a grouped number counts as compound, because its separator is neutral too`() {
        // «۱۲٬۵۰۰» is two digit runs around a mark with no direction of its own — the same shape as
        // a ratio, and at the same risk.
        // And its separator comes back as a plain comma: U+066C does not merge with the digits
        // either side of it, so inside the isolate it still landed on the wrong side of them.
        assertEquals("سود ${lri}۱۲,۵۰۰${pdi} بود", BidiText.isolateNumericRuns("سود ۱۲٬۵۰۰ بود"))
    }

    @Test
    fun `a time and a range survive too`() {
        assertTrue(lri + "09:14" + pdi in BidiText.isolateNumericRuns("ساعت 09:14 منتشر شد"))
        assertTrue(lri + "۲۰۲۴-۲۰۲۵" + pdi in BidiText.isolateNumericRuns("دوره‌ی ۲۰۲۴-۲۰۲۵"))
    }

    @Test
    fun `a trailing connector is not swallowed`() {
        // «۱۰۰.» ends a sentence; the full stop belongs to the paragraph, not to the number.
        assertEquals("مقدار ۱۰۰.", BidiText.isolateNumericRuns("مقدار ۱۰۰."))
    }

    @Test
    fun `a percentage keeps its sign beside its figure`() {
        // The guest home read «نرخ برد ٪66.7» — the sign in front of the number, and the sentence's
        // full stop in front of that.
        // And the sign is the Latin one, which is what the app's own percent pills have always
        // printed: «٪» does not lay out beside the digits even inside the isolate.
        assertEquals("${lri}66.7%$pdi", BidiText.percent("66.7"))
    }

    @Test
    fun `a figure that arrives already isolated is not isolated twice`() {
        // The formatters hand back an isolated run, and an isolate inside an isolate puts the sign
        // outside the inner one — back where this started.
        assertEquals("${lri}66.7%$pdi", BidiText.percent(BidiText.isolateLtr("66.7")))
    }

    @Test
    fun `text with no numbers comes back untouched`() {
        assertEquals("اهرم سود را بزرگ می‌کند", BidiText.isolateNumericRuns("اهرم سود را بزرگ می‌کند"))
        assertEquals("", BidiText.isolateNumericRuns(""))
    }
}
