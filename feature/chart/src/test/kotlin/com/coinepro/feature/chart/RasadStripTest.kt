package com.coinepro.feature.chart

import com.coinepro.core.common.BidiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the coach's one line on the chart page is allowed to be (run Ω-FIX item 5).
 *
 * The device showed «اینجا روندی نیست — بازار داخل یک محدوده می‌چرخد، با ن…»: the strip was one
 * line with an ellipsis, and a sentence cut at «با ن» is not a shorter sentence, it is a riddle.
 *
 * Two things had to change. The row wraps, which is what this file pins; and no sentence the coach
 * can write is long enough to need cutting even then, which is pinned where the templates are —
 * `RasadCoachTest.every trend sentence fits the two lines the strip gives it`, in `:chart-core`,
 * because that is the module that can see them.
 */
class RasadStripTest {

    @Test
    fun `the strip wraps rather than cutting`() {
        assertTrue("a one-line strip is what produced «با ن…»", RASAD_LINES >= 2)
    }

    @Test
    fun `the strip's numbers are isolated before they are drawn`() {
        // What `RasadLine` and `RasadSheetBody` both do to every sentence on their way to a `Text`.
        val sentence = BidiText.isolateNumbers("روی 91,263.03 است، بین حمایت 90,000.00 و مقاومت 92,000.00.")
        assertEquals("one isolate per price", 3, sentence.count { it == BidiText.FSI })
        assertTrue("the sentence lost its own stop", sentence.endsWith("."))
    }
}
