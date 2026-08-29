package com.coinepro.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repeat policy, at the two moments that decide whether somebody trusts it.
 *
 * A wick through a level that the bar then takes back is the thing "once per bar close" exists to
 * not tell anybody about. If it fires mid-bar it is not a quieter setting, it is the same setting
 * with a different name, and the reader who chose it has been misled rather than served.
 */
class AlertFrequencyTest {

    private val barStart = 1_700_000_000_000L
    private val midBar = barStart + 30_000L
    private val nextBarStart = barStart + 60_000L

    @Test
    fun `once per bar close does not fire mid-bar`() {
        assertFalse(
            AlertFrequency.ONCE_PER_BAR_CLOSE.shouldFire(
                now = midBar,
                lastFiredAt = null,
                barStart = barStart,
                barClosed = false,
            ),
        )
        assertTrue(
            AlertFrequency.ONCE_PER_BAR_CLOSE.shouldFire(
                now = nextBarStart,
                lastFiredAt = null,
                barStart = barStart,
                barClosed = true,
            ),
        )
    }

    @Test
    fun `once per bar does fire mid-bar, and that is the difference between the two`() {
        assertTrue(
            AlertFrequency.ONCE_PER_BAR.shouldFire(
                now = midBar,
                lastFiredAt = null,
                barStart = barStart,
                barClosed = false,
            ),
        )
    }

    @Test
    fun `once per bar allows one firing in a bar and no more`() {
        assertFalse(
            AlertFrequency.ONCE_PER_BAR.shouldFire(
                now = midBar,
                lastFiredAt = barStart,
                barStart = barStart,
                barClosed = false,
            ),
        )
        assertTrue(
            AlertFrequency.ONCE_PER_BAR.shouldFire(
                now = nextBarStart + 1_000L,
                lastFiredAt = midBar,
                barStart = nextBarStart,
                barClosed = false,
            ),
        )
    }

    @Test
    fun `once fires exactly once, whatever the bars do`() {
        assertTrue(
            AlertFrequency.ONCE.shouldFire(midBar, lastFiredAt = null, barStart = barStart, barClosed = true),
        )
        assertFalse(
            AlertFrequency.ONCE.shouldFire(
                now = nextBarStart,
                lastFiredAt = midBar,
                barStart = nextBarStart,
                barClosed = true,
            ),
        )
    }

    @Test
    fun `every time fires on every evaluation, closed bar or not`() {
        assertTrue(
            AlertFrequency.EVERY_TIME.shouldFire(midBar, lastFiredAt = midBar, barStart = barStart, barClosed = false),
        )
    }

    /**
     * A phone whose clock jumped back must not lock its alerts until the calendar catches up.
     *
     * That is indistinguishable, from the reader's chair, from an app that has stopped working.
     */
    @Test
    fun `a last firing in the future is treated as never having happened`() {
        assertTrue(
            AlertFrequency.ONCE.shouldFire(
                now = barStart,
                lastFiredAt = barStart + 1_000_000L,
                barStart = barStart,
                barClosed = true,
            ),
        )
    }

    @Test
    fun `every frequency survives a round trip through its id`() {
        AlertFrequency.entries.forEach { frequency ->
            assertTrue(frequency.id, AlertFrequency.fromId(frequency.id) == frequency)
        }
        assertTrue(AlertFrequency.fromId("something_else") == null)
        assertTrue(AlertFrequency.fromId(null) == null)
    }
}
