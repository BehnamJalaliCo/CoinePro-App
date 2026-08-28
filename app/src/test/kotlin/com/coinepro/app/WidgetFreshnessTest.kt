package com.coinepro.app

import com.coinepro.app.widget.WidgetFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * How old the widget says its prices are.
 *
 * A widget with no time on it shows yesterday's price exactly as confidently as this second's, and
 * the reader cannot tell the difference. On a trading app that is not a cosmetic problem, so the
 * cases that matter here are the dishonest ones: never fetched, and a clock that moved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fa-rIR")
class WidgetFreshnessTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val now = 1_735_000_000_000L

    private fun describe(capturedAt: Long, at: Long = now, stale: Boolean = false) =
        WidgetFreshness.describe(context, capturedAt, at, stale)

    @Test
    fun `nothing fetched is never said to be now`() {
        // The one lie this whole file exists to prevent.
        val never = context.getString(R.string.widget_never)
        assertEquals(never, describe(capturedAt = 0L))
        assertEquals(never, describe(capturedAt = -1L))
    }

    @Test
    fun `a clock that moved backwards admits it does not know`() {
        // The reader changed the time, or the network corrected it. A negative age is not an
        // answer, and "now" would be a confident wrong one.
        assertEquals(context.getString(R.string.widget_never), describe(capturedAt = now + 60_000))
    }

    @Test
    fun `fresh is now, and the buckets climb`() {
        assertEquals(context.getString(R.string.widget_now), describe(capturedAt = now - 5_000))
        assertEquals(context.getString(R.string.widget_now), describe(capturedAt = now - 59_000))

        val fiveMinutes = describe(capturedAt = now - 5 * 60_000)
        val twoHours = describe(capturedAt = now - 2 * 3_600_000)
        val old = describe(capturedAt = now - 3 * 86_400_000L)
        // Four distinct answers, each true for as long as it is shown.
        assertEquals(4, setOf(context.getString(R.string.widget_now), fiveMinutes, twoHours, old).size)
        assertEquals(context.getString(R.string.widget_old), old)
    }

    @Test
    fun `a count in prose is in Persian digits`() {
        // The app's rule: prose counts are Persian, market figures are Latin. The prices in the
        // rows beside this line are Latin, and the difference is the point — one is language and
        // the other is data.
        val text = describe(capturedAt = now - 5 * 60_000)
        assertTrue("'$text' should carry a Persian five", text.contains("\u06F5"))
    }

    @Test
    fun `a failed refresh is said out loud, not hidden behind a climbing age`() {
        // "An hour old" may be fine. "An hour old and we tried and could not" is a reason to open
        // the app, and the two are different facts.
        val quiet = describe(capturedAt = now - 3_600_000, stale = false)
        val loud = describe(capturedAt = now - 3_600_000, stale = true)
        assertTrue(loud.contains(quiet))
        assertTrue(loud.length > quiet.length)
    }
}
